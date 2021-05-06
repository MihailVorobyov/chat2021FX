package server;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuthService {
	private static final Logger LOGGER = LogManager.getLogger(AuthService.class);
	private static Connection connection;
	private static Statement statement;

	public static void connect() {
		try {
			LOGGER.debug("Try to connect to DB...");
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:main.db");
			statement = connection.createStatement();
			LOGGER.debug("Connecting successful");
		} catch (ClassNotFoundException | SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static int addUser(String login, String pass, String nickname) {
		try {
			LOGGER.debug(String.format("Try to add to DB: User = %s, passHash = %s, nickname = %s", login,
					pass.hashCode(),
					nickname));
			String query = "INSERT INTO users (login, password, nickname) VALUES (?, ?, ?);";
			PreparedStatement ps = connection.prepareStatement(query);
			ps.setString(1, login);
			ps.setInt(2, pass.hashCode());
			ps.setString(3, nickname);
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
		return 0;
	}

	public static String getNicknameByLoginAndPass(String login, String pass) {
		String query = String.format("select nickname, password from users where login='%s'", login);
		try {
			LOGGER.debug("Try to get nickname by login and pass");
			ResultSet rs = statement.executeQuery(query); // возвращает выборку через select
			// изменим пароли в ДБ на хеш от строки pass1
			int myHash = pass.hashCode();

			if (rs.next()) {
				String nick = rs.getString(1);
				int dbHash = rs.getInt(2);
				if (myHash == dbHash) {
					LOGGER.debug("Nickname got successful");
					return nick;
				}
			}
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
		return null;
	}

	public static void disconnect() {
		try {
			LOGGER.debug("Try to disconnect from DB...");
			connection.close();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
		LOGGER.debug("Disconnected from DB");
	}

	/*
	* Сохраняет чёрный список в БД
	*/
	public static int addUserToBlacklist(String owner, String blackClient) {
		PreparedStatement ps = null;
		try {
			LOGGER.debug("Try to add user to blacklist");
			ps = connection.prepareStatement("INSERT INTO blacklist (owner, black) VALUES (?, ?)");
			ps.setString(1, owner);
			ps.setString(2, blackClient);
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		} finally {
			if (ps != null) {
				statementClose(ps);
			}
		}

		return 0;
	}

	public static int deleteUserFromBlacklist(String owner, String blackClient) {
		PreparedStatement ps = null;
		try {
			LOGGER.debug("Try to delete user to blacklist");
			ps = connection.prepareStatement("DELETE FROM blacklist WHERE owner = ? AND black = ?");
			ps.setString(1, owner);
			ps.setString(2, blackClient);
			return ps.executeUpdate();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		} finally {
			statementClose(ps);
		}
		return 0;
	}

	public static List<String> getBlackListByNickname (String nickname) {
		LOGGER.debug("Try to get blacklist by nickname");
		List<String> blacklist = new ArrayList<>();
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			ps = connection.prepareStatement("SELECT * FROM blacklist WHERE owner = ?");
			ps.setString(1, nickname);
			rs = ps.executeQuery();

			while (rs.next()) {
				blacklist.add(rs.getString(2));
			}
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		} finally {
			resultSetClose(rs);
			statementClose(ps);
		}
		return blacklist;
	}

	private static void statementClose(PreparedStatement ps) {
		try {
			ps.close();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	private static void resultSetClose(ResultSet rs) {
		try {
			rs.close();
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	public static void saveHistory (String nickname, String message) {
		try {
			LOGGER.debug("Try to save message to DB...");
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date date = new Date();

			PreparedStatement ps = connection.prepareStatement("INSERT INTO history (date_time, recipient, message) " +
					"VALUES (?, ?, ?);");
			ps.setString(1, formatter.format(date));
			ps.setString(2, nickname);
			ps.setString(3, message);
			if (ps.executeUpdate() > 0) {
				LOGGER.debug("Successfully save massage to DB");
			}
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	/**
	 * Выбирает из базы данных 100 последних сообщений из окна чата данного пользователя.
	 * @param nickname
	 * @return возвращает строку для отображения в окне чата
	 */
	public static String getHistory(String nickname) {
		StringBuilder builder = new StringBuilder("/history ");
		PreparedStatement ps = null;
		ResultSet rs = null;

		try {
			LOGGER.debug("Try to load 100 last messages from DB...");
			ps = connection.prepareStatement("SELECT message_id, message FROM history WHERE recipient = ? ORDER" +
					" BY message_id ASC LIMIT 100, (SELECT count(*) FROM history) - 100");
			ps.setString(1, nickname);
			rs = ps.executeQuery();

			while (rs.next()) {
				builder.append(rs.getString("message") + "\n");
			}
			LOGGER.debug("Successfully load 100 last messages");
		} catch (SQLException e) {
			LOGGER.error(e.getMessage(), e);
		} finally {
			resultSetClose(rs);
			statementClose(ps);
		}
		return builder.toString();
	}
}
