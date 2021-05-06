package server;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuthService {
	private static Connection connection;
	private static Statement statement;

	public static void connect() {
		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:main.db");
			statement = connection.createStatement();
		} catch (ClassNotFoundException | SQLException e) {
			e.printStackTrace();
		}
	}

	public static int addUser(String login, String pass, String nickname) {
		try {
			String query = "INSERT INTO users (login, password, nickname) VALUES (?, ?, ?);";
			PreparedStatement ps = connection.prepareStatement(query);
			ps.setString(1, login);
			ps.setInt(2, pass.hashCode());
			ps.setString(3, nickname);
			return ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}

	public static String getNicknameByLoginAndPass(String login, String pass) {
		String query = String.format("select nickname, password from users where login='%s'", login);
		try {
			ResultSet rs = statement.executeQuery(query); // возвращает выборку через select
			int myHash = pass.hashCode();
			// кеш числа 12345
			// изменим пароли в ДБ на хеш от строки pass1

			if (rs.next()) {
				String nick = rs.getString(1);
				int dbHash = rs.getInt(2);
				if (myHash == dbHash) {
					return nick;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void disconnect() {
		try {
			connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/*
	* Сохраняет чёрный список в БД
	*/
	public static int addUserToBlacklist(String owner, String blackClient) {
		PreparedStatement ps = null;
		try {
			ps = connection.prepareStatement("INSERT INTO blacklist (owner, black) VALUES (?, ?)");
			ps.setString(1, owner);
			ps.setString(2, blackClient);
			return ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
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
			ps = connection.prepareStatement("DELETE FROM blacklist WHERE owner = ? AND black = ?");
			ps.setString(1, owner);
			ps.setString(2, blackClient);
			return ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			statementClose(ps);
		}
		return 0;
	}

	public static List<String> getBlackListByNickname (String nickname) {
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
			e.printStackTrace();
		} finally {
			resultSetClose(rs);
			statementClose(ps);
		}
		return blacklist;
	}

	private static void statementClose(PreparedStatement ps) {
		try {
			ps.close();
		} catch (SQLException throwables) {
			throwables.printStackTrace();
		}
	}

	private static void resultSetClose(ResultSet rs) {
		try {
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void saveHistory (String nickname, String message) {
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Date date = new Date();

			PreparedStatement ps = connection.prepareStatement("INSERT INTO history (date_time, recipient, message) " +
					"VALUES (?, ?, ?);");
			ps.setString(1, formatter.format(date));
			ps.setString(2, nickname);
			ps.setString(3, message);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
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
			ps = connection.prepareStatement("SELECT message_id, message FROM history WHERE recipient = ? ORDER" +
					" BY message_id ASC LIMIT 100, (SELECT count(*) FROM history) - 100");
			ps.setString(1, nickname);
			rs = ps.executeQuery();

			while (rs.next()) {
				builder.append(rs.getString("message") + "\n");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			resultSetClose(rs);
			statementClose(ps);
		}
		return builder.toString();
	}
}
