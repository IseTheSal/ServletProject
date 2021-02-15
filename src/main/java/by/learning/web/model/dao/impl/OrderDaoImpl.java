package by.learning.web.model.dao.impl;


import by.learning.web.exception.ConnectionPoolException;
import by.learning.web.exception.DaoException;
import by.learning.web.model.dao.OrderDao;
import by.learning.web.model.entity.ClientOrder;
import by.learning.web.model.entity.Coupon;
import by.learning.web.model.pool.ConnectionPool;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OrderDaoImpl implements OrderDao {
    private static final Logger logger = LogManager.getLogger();

    private static final OrderDaoImpl INSTANCE = new OrderDaoImpl();

    public static OrderDaoImpl getInstance() {
        return INSTANCE;
    }

    private static final ConnectionPool CONNECTION_POOL = ConnectionPool.INSTANCE;

    private static final String FIND_AVAILABLE_COUPON_DISCOUNT = "SELECT coupon_id, discount " +
            "FROM coupons " +
            "WHERE code = ? AND amount > 0";
    private static final String INSERT_ORDER_WITH_COUPON = "INSERT INTO orders(order_id, user_id, total_price, coupon_id) " +
            "VALUES (default, ?, ?, ?)";
    private static final String INSERT_ORDER_WITHOUT_COUPON = "INSERT INTO orders(order_id, user_id, total_price, coupon_id) " +
            "VALUES (default, ?, ?, null)";
    private static final String INSERT_GAME_ORDER = "INSERT INTO game_order(game_id, order_id) " +
            "VALUES (?, ?)";
    private static final String FIND_AVAILABLE_GAME_CODE_AMOUNT = "SELECT count(game_code) AS amount " +
            "FROM codes " +
            "WHERE sold IS FALSE AND game_id = ?";
    private static final String FIND_AVAILABLE_COUPONS_AMOUNT = "SELECT amount " +
            "FROM coupons " +
            "WHERE code = ? AND amount > 0 ";
    private static final String FIND_COUPON_DISCOUNT = "SELECT discount " +
            "FROM coupons " +
            "WHERE code = ?;";
    private static final String FIND_LIMITED_GAME_CODE = "SELECT game_code " +
            "from codes " +
            "where sold IS FALSE " +
            "  AND game_id = ? " +
            "LIMIT ?";
    private static final String SET_CODE_SOLD_TRUE = "UPDATE codes " +
            "SET sold = TRUE " +
            "WHERE game_code = ?";
    private static final String INCREASE_COUPON_AMOUNT = "UPDATE coupons " +
            "SET amount = amount + ? " +
            "WHERE code = ?";
    private static final String DECREASE_COUPON_AMOUNT = "UPDATE coupons " +
            "SET amount = amount - ? " +
            "WHERE code = ?";


    public Optional<Coupon> findAvailableCouponByCode(String codeName) throws DaoException {
        Optional<Coupon> result = Optional.empty();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            preparedStatement = connection.prepareStatement(FIND_AVAILABLE_COUPON_DISCOUNT);
            preparedStatement.setString(1, codeName);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int couponId = resultSet.getInt(1);
                short discount = resultSet.getShort(2);
                Coupon coupon = new Coupon(couponId, discount, codeName);
                result = Optional.of(coupon);
                logger.log(Level.DEBUG, "dis {}", discount);
            }
        } catch (ConnectionPoolException | SQLException e) {
            throw new DaoException(e);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        return result;
    }

    public int findAvailableCouponAmountByName(String codeName) throws DaoException {
        int amount = 0;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            preparedStatement = connection.prepareStatement(FIND_AVAILABLE_COUPONS_AMOUNT);
            preparedStatement.setString(1, codeName);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                amount = resultSet.getInt(1);
            }
        } catch (ConnectionPoolException | SQLException e) {
            throw new DaoException(e);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        logger.log(Level.INFO, "available amount {}", amount);
        return amount;
    }

    @Override
    public short findCouponDiscountByName(String codeName) throws DaoException {
        short discount = 0;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            preparedStatement = connection.prepareStatement(FIND_COUPON_DISCOUNT);
            preparedStatement.setString(1, codeName);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                discount = resultSet.getShort(1);
            }
        } catch (ConnectionPoolException | SQLException e) {
            throw new DaoException(e);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        logger.log(Level.INFO, "{} discount {}", codeName, discount);
        return discount;
    }

    public boolean changeCouponAmountByName(String codeName, int amount, boolean increase) throws DaoException {
        boolean changed = false;
        PreparedStatement preparedStatement = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            if (increase) {
                preparedStatement = connection.prepareStatement(INCREASE_COUPON_AMOUNT);
            } else {
                preparedStatement = connection.prepareStatement(DECREASE_COUPON_AMOUNT);
            }
            preparedStatement.setInt(1, amount);
            preparedStatement.setString(2, codeName);
            int executeUpdate = preparedStatement.executeUpdate();
            if (executeUpdate > 0) {
                changed = true;
            }
        } catch (ConnectionPoolException | SQLException e) {
            throw new DaoException(e);
        } finally {
            close(preparedStatement);
        }
        return changed;
    }

    public int findAvailableGameAmountById(int gameId) throws DaoException {
        int amount = 0;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            preparedStatement = connection.prepareStatement(FIND_AVAILABLE_GAME_CODE_AMOUNT);
            preparedStatement.setInt(1, gameId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                amount = resultSet.getInt(1);
            }
        } catch (ConnectionPoolException | SQLException e) {
            throw new DaoException(e);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        logger.log(Level.INFO, "available amount {}", amount);
        return amount;
    }

    @Override
    public boolean createOrder(ClientOrder order) throws DaoException {
        boolean isCreated = false;
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet generatedKeys = null;
        try {
            connection = CONNECTION_POOL.getConnection();
            connection.setAutoCommit(false);
            if (order.getCoupon() == null) {
                preparedStatement = connection.prepareStatement(INSERT_ORDER_WITHOUT_COUPON, Statement.RETURN_GENERATED_KEYS);
            } else {
                preparedStatement = connection.prepareStatement(INSERT_ORDER_WITH_COUPON, Statement.RETURN_GENERATED_KEYS);
                preparedStatement.setInt(3, order.getCoupon().getId());
            }
            preparedStatement.setInt(1, order.getUserId());
            preparedStatement.setBigDecimal(2, order.getPrice());
            int executeUpdate = preparedStatement.executeUpdate();
            if (executeUpdate > 0) {
                generatedKeys = preparedStatement.getGeneratedKeys();
                generatedKeys.next();
                int orderId = generatedKeys.getInt(1);
                boolean executed = true;
                int[] gameIdArray = order.getGameIdSet().stream().mapToInt(Integer::intValue).toArray();
                int i = 0;
                while (i < gameIdArray.length && executed) {
                    executed = createGameOrderRelation(connection, gameIdArray[i], orderId);
                    i++;
                }
                isCreated = executed;
            }
        } catch (ConnectionPoolException | SQLException e) {
            rollback(connection);
            throw new DaoException(e);
        } finally {
            setAutoCommitTrue(connection);
            close(generatedKeys);
            close(preparedStatement);
            close(connection);
        }
        return isCreated;
    }

    private boolean createGameOrderRelation(Connection connection, int gameId, int orderId) throws DaoException {
        boolean result = false;
        try (PreparedStatement preparedStatement = connection.prepareStatement(INSERT_GAME_ORDER)) {
            preparedStatement.setInt(1, gameId);
            preparedStatement.setInt(2, orderId);
            int executeUpdate = preparedStatement.executeUpdate();
            if (executeUpdate > 0) {
                result = true;
            }
        } catch (SQLException exception) {
            logger.log(Level.ERROR, exception);
            throw new DaoException(exception);
        }
        return result;
    }

    public List<String> findLimitedGameCodes(int gameId, int amount) throws DaoException {
        List<String> result = new ArrayList<>();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            preparedStatement = connection.prepareStatement(FIND_LIMITED_GAME_CODE);
            preparedStatement.setInt(1, gameId);
            preparedStatement.setInt(2, amount);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                String code = resultSet.getString(1);
                result.add(code);
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        return result;
    }

    public void putSoldGameCodeList(List<String> codeList) throws DaoException {
        try (Connection connection = CONNECTION_POOL.getConnection()) {
            for (String codeName : codeList) {
                putSoldOnGameCode(connection, codeName);
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        }
    }

    private void putSoldOnGameCode(Connection connection, String codeName) throws DaoException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(SET_CODE_SOLD_TRUE)) {
            preparedStatement.setString(1, codeName);
            preparedStatement.executeUpdate();
        } catch (SQLException ex) {
            throw new DaoException(ex);
        }
    }
}