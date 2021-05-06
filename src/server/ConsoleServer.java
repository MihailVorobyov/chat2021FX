package server;



import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsoleServer {
	// 1. Добавить на серверную сторону чата логирование, с выводом информации о действиях на сервере (запущен, произошла ошибка, клиент подключился, клиент прислал сообщение/команду).
	private static final Logger LOGGER = LogManager.getLogger(ConsoleServer.class);

	private Vector<ClientHandler> users;
	private ExecutorService executorService;

	public ConsoleServer() {
		LOGGER.info("Try to start server");
		users = new Vector<>();
		ServerSocket server = null; // наша сторона
		Socket socket = null; // удаленная (remote) сторона

		/* Уровень 3, ДЗ №4.
		 * 2. На серверной стороне сетевого чата реализовать управление потоками через ExecutorService.
		 * Продолжение кода в классе ClientHandler.
		 */
		this.executorService = Executors.newFixedThreadPool(30);

		try {
			AuthService.connect();
			server = new ServerSocket(6001);
			LOGGER.info("Server started");

			while (true) {
				socket = server.accept();
				LOGGER.info(String.format("Client [%s] try to connect", socket.getInetAddress()));
				new ClientHandler(this, socket);
			}

		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		} finally {

			if (socket != null) {
				try {
					LOGGER.info(String.format("Client [%s] disconnected", socket.getInetAddress()));
					socket.close();
				} catch (IOException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}

			if (server != null) {
				try {
					LOGGER.info("Try to stop server...");
					server.close();
				} catch (IOException e) {
					LOGGER.error(e.getMessage(), e);
				}
			}

			AuthService.disconnect();
			executorService.shutdown();
			LOGGER.info("Server is stopped");
		}
	}

	public void subscribe(ClientHandler client) {
		users.add(client);
		LOGGER.info(String.format("User [%s] connected", client.getNickname()));
		broadcastClientsList();
	}

	public void unsubscribe(ClientHandler client) {
		users.remove(client);
		LOGGER.info(String.format("User [%s] disconnected", client.getNickname()));
		broadcastClientsList();
	}

	public void broadcastMessage(ClientHandler from, String str) {
		for (ClientHandler c : users) {
			if (!c.checkBlackList(from.getNickname())) {
				c.sendMsg(str);
				LOGGER.info(str);
			}
		}
	}

	public boolean isNickBusy(String nick) {
		for (ClientHandler c : users) {
			if (c.getNickname().equals(nick)) {
				return true;
			}
		}
		return false;
	}

	public void sendPrivateMsg(ClientHandler nickFrom, String nickTo, String msg) {
		for (ClientHandler c : users) {
			if (c.getNickname().equals(nickTo)) {   // выбираем нужного получателя
				if (!nickFrom.getNickname().equals(nickTo)) {   // не отправлять сообщение самому себе
					if (!c.checkBlackList(nickFrom.getNickname())) {    // если чёрный список получателя НЕ
						// содержит ник отправителя, то отправляем ему сообщение
						c.sendMsg(nickFrom.getNickname() + ": [Send for " + nickTo + "] " + msg);
						LOGGER.info(String.format("%s: [Send for %s] %s", nickFrom.getNickname(), nickTo, msg));
					}
					nickFrom.sendMsg(nickFrom.getNickname() + ": [Send for " + nickTo + "] " + msg);
				}
			}
		}
	}

	private void broadcastClientsList() {
		StringBuilder sb = new StringBuilder();
		sb.append("/clientList ");
		for (ClientHandler c : users) {
			sb.append(c.getNickname());
			sb.append(" ");
		}

		String out = sb.toString();
		for (ClientHandler c : users) {
			c.sendMsg(out);
		}
		LOGGER.info("sendMsg(" + out + ")");
	}

	public ExecutorService getExecutorService() {
		return executorService;
	}
}
