package by.learning.web.model.dao.impl;

import by.learning.web.exception.ConnectionPoolException;
import by.learning.web.exception.DaoException;
import by.learning.web.model.dao.UserDao;
import by.learning.web.model.entity.User;
import by.learning.web.model.pool.ConnectionPool;
import by.learning.web.util.CryptEncoder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class UserDaoImpl implements UserDao {
    private static final Logger logger = LogManager.getLogger();

    private static final UserDaoImpl INSTANCE = new UserDaoImpl();

    public static UserDaoImpl getInstance() {
        return INSTANCE;
    }

    private static final ConnectionPool CONNECTION_POOL = ConnectionPool.INSTANCE;

    private final static String FIND_USER = "SELECT user_id, password, firstname, lastname, email, role " +
            "FROM users WHERE users.login = ?";
    private static final String CONTAIN_LOGIN = "SELECT COUNT(*) FROM users WHERE users.login = ?";
    private static final String CONTAIN_EMAIL = "SELECT COUNT(*) FROM users WHERE users.email = ?";
    private static final String ADD_USER = "INSERT INTO users (login, password, firstname, lastname, email, role) " +
            "VALUES(?,?,?,?,?,?)";
    private static final String UPDATE_EMAIL = "UPDATE users SET email = ? WHERE users.user_id = ?";
    private static final String UPDATE_PASSWORD = "UPDATE users SET password = ? WHERE users.user_id = ?";
    private static final String FIND_USER_PASSWORD = "SELECT password FROM users WHERE users.user_id = ?";
    private static final String FIND_ALL_CLIENTS = "SELECT user_id, login, email, firstname, lastname, users.role FROM users WHERE users.role = 'CLIENT'";
    private static final String FIND_ALL_USERS = "SELECT user_id, login, email, firstname, lastname, users.role FROM users";

    @Override
    public Optional<User> findUser(String login, String password) throws DaoException {
        Optional<User> result = Optional.empty();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.takeConnection()) {
            preparedStatement = connection.prepareStatement(FIND_USER);
            preparedStatement.setString(1, login);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String cryptPassword = resultSet.getString(2);
                boolean isMatch = CryptEncoder.check(password, cryptPassword);
                if (isMatch) {
                    int id = Integer.parseInt(resultSet.getString(1));
                    String firstname = resultSet.getString(3);
                    String lastname = resultSet.getString(4);
                    String email = resultSet.getString(5);
                    String roleString = resultSet.getString(6);
                    User.Role role = User.Role.valueOf(roleString);
                    result = Optional.of(new User(id, login, firstname, lastname, email, role));
                }
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        return result;
    }

    private boolean existLogin(String login) throws DaoException {
        boolean result = exist(login, CONTAIN_LOGIN);
        logger.log(Level.DEBUG, "Is login exist: " + result);
        return result;
    }

    private boolean existEmail(String email) throws DaoException {
        boolean result = exist(email, CONTAIN_EMAIL);
        logger.log(Level.DEBUG, "Is email exist: " + result);
        return result;
    }

    private boolean exist(String value, String sqlRequest) throws DaoException {
        boolean result;
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            connection = CONNECTION_POOL.takeConnection();
            preparedStatement = connection.prepareStatement(sqlRequest);
            preparedStatement.setString(1, value);
            resultSet = preparedStatement.executeQuery();
            int counter = 0;
            if (resultSet.next()) {
                counter = resultSet.getInt(1);
            }
            result = (counter > 0);
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        } finally {
            close(resultSet);
            close(preparedStatement);
            close(connection);
        }
        return result;
    }

    @Override
    public boolean addUser(User user, String cryptPassword) throws DaoException {
        boolean result = false;
        String login = user.getLogin();
        String email = user.getEmail();
        if (!existEmail(email) && !existLogin(login)) {
            PreparedStatement preparedStatement = null;
            logger.log(Level.DEBUG, "Login and email are available");
            try (Connection connection = CONNECTION_POOL.takeConnection()) {
                preparedStatement = connection.prepareStatement(ADD_USER);
                preparedStatement.setString(1, login);
                preparedStatement.setString(2, cryptPassword);
                preparedStatement.setString(3, user.getFirstname());
                preparedStatement.setString(4, user.getLastname());
                preparedStatement.setString(5, email);
                String role = user.getRole().toString();
                preparedStatement.setString(6, role);
                preparedStatement.executeUpdate();
                result = true;
                logger.log(Level.DEBUG, "New user register successfully");
            } catch (SQLException | ConnectionPoolException ex) {
                throw new DaoException(ex);
            } finally {
                close(preparedStatement);
            }
        } else {
            logger.log(Level.DEBUG, "Fail during registration. Login or email exist");
        }
        return result;
    }

    @Override
    public boolean changeUserEmail(int userId, String email) throws DaoException {
        boolean isChanged = false;
        boolean isExist = existEmail(email);
        if (!isExist) {
            PreparedStatement preparedStatement = null;
            try (Connection connection = CONNECTION_POOL.takeConnection()) {
                preparedStatement = connection.prepareStatement(UPDATE_EMAIL);
                preparedStatement.setString(1, email);
                preparedStatement.setInt(2, userId);
                int executeUpdate = preparedStatement.executeUpdate();
                if (executeUpdate > 0) {
                    isChanged = true;
                }
            } catch (ConnectionPoolException | SQLException ex) {
                throw new DaoException(ex);
            } finally {
                close(preparedStatement);
            }
        }
        return isChanged;
    }

    @Override
    public Optional<String> findUserPassword(int userId) throws DaoException {
        Optional<String> password = Optional.empty();
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try (Connection connection = CONNECTION_POOL.takeConnection()) {
            preparedStatement = connection.prepareStatement(FIND_USER_PASSWORD);
            preparedStatement.setInt(1, userId);
            resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                password = Optional.of(resultSet.getString(1));
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        } finally {
            close(resultSet);
            close(preparedStatement);
        }
        return password;
    }

    @Override
    public boolean changeUserPassword(int userId, String cryptPassword) throws DaoException {
        boolean isChanged = false;
        PreparedStatement preparedStatement = null;
        try (Connection connection = CONNECTION_POOL.takeConnection()) {
            preparedStatement = connection.prepareStatement(UPDATE_PASSWORD);
            preparedStatement.setString(1, cryptPassword);
            preparedStatement.setInt(2, userId);
            int executeUpdate = preparedStatement.executeUpdate();
            if (executeUpdate > 0) {
                isChanged = true;
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        } finally {
            close(preparedStatement);
        }
        return isChanged;
    }

    @Override
    public Set<User> findAllClients() throws DaoException {
        return findUsers(FIND_ALL_CLIENTS);
    }

    @Override
    public Set<User> findAllUsers() throws DaoException {
        return findUsers(FIND_ALL_USERS);
    }

    private Set<User> findUsers(String sqlQuery) throws DaoException {
        Set<User> result = new HashSet<>();
        try (Connection connection = CONNECTION_POOL.takeConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                int userId = resultSet.getInt(1);
                String login = resultSet.getString(2);
                String email = resultSet.getString(3);
                String firstname = resultSet.getString(4);
                String lastname = resultSet.getString(5);
                User.Role role = User.Role.valueOf(resultSet.getString(6));
                User user = new User(userId, login, firstname, lastname, email, role);
                result.add(user);
            }
        } catch (SQLException | ConnectionPoolException ex) {
            throw new DaoException(ex);
        }
        return result;
    }
}
