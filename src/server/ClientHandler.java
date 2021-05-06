package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler {

	private DataOutputStream out;
	private DataInputStream in;
	private String nickname;

	// черный список у пользователя, а не у сервера
	List<String> blackList;

	public ClientHandler(ConsoleServer server, Socket socket) {
		try {
			this.in = new DataInputStream(socket.getInputStream());
			this.out = new DataOutputStream(socket.getOutputStream());
			this.blackList = new ArrayList<>();
			this.nickname = null;

			server.getExecutorService().execute(() -> {
				boolean isExit = false;
				try {
					// отключение неавторизованных пользователей по таймауту
					// (120 сек. ждём после подключения клиента, и если он не авторизовался за это время, закрываем соединение).
					socket.setSoTimeout(120000);
					while (true) {
						String str = in.readUTF();
						if (str.startsWith("/auth")){
							String[] tokens = str.split(" ");
							String nick = AuthService.getNicknameByLoginAndPass(tokens[1], tokens[2]);
							if (nick != null) {
								if (!server.isNickBusy(nick)) {
									sendMsg("/auth-OK");
									setNickname(nick);
									sendMsg("/nick " + nickname);
									socket.setSoTimeout(0);
									server.subscribe(ClientHandler.this);
									// загружаем чёрный список из БД
									blackList.addAll(AuthService.getBlackListByNickname(nickname));
									// загружаем историю сообщений из БД
									loadHistory();
									break;
								} else {
									sendMsg("Учетная запись уже используется");
								}
							} else {
								sendMsg("Неверный логин/пароль");
							}
						}
						// регистрация
						if (str.startsWith("/signup ")) {
							String[] tokens = str.split(" ");
							int result = AuthService.addUser(tokens[1], tokens[2], tokens[3]);
							if (result > 0) {
								sendMsg("Successful registration");
							} else {
								sendMsg("Registration failed");
							}
						}
						// выход
						if ("/end".equals(str)) {
							isExit = true;
							break;
						}
					}

					if (!isExit) {
						while (true) {
							String str = in.readUTF();
							// для всех служебных команд и личных сообщений
							if (str.startsWith("/") || str.startsWith("@")) {
								if ("/end".equalsIgnoreCase(str)){
									// для оповещения клиента, т.к. без сервера клиент работать не должен
									out.writeUTF("/serverClosed");
									System.out.println("Client (" + socket.getInetAddress() + ") exited");
									break;
								}
								// вторая часть ДЗ. выполнение
								if (str.startsWith("@")) {
									String[] tokens = str.split(" ", 2);
									server.sendPrivateMsg(this, tokens[0].substring(1), tokens[1]);
								}

								// черный список для пользователя.
								// сохраняем чёрный список в БД
								if (str.startsWith("/blacklist ")) {
									String[] tokens = str.split(" ");
									if (AuthService.getBlackListByNickname(nickname).contains(tokens[1])) {
										if(AuthService.deleteUserFromBlacklist(nickname, tokens[1]) == 1) {
											blackList.remove(tokens[1]);
											sendMsg("You exclude " + tokens[1] + " from blacklist");
										} else {
											sendMsg("Something wrong. Can't exclude");
										}

									} else {
										if (AuthService.addUserToBlacklist(nickname, tokens[1]) == 1) {
											blackList.add(tokens[1]);
											sendMsg("You added " + tokens[1] + " to blacklist");
										} else {
											sendMsg("Something wrong. Can't add");
										}
									}
								}
							} else {
								server.broadcastMessage(this, nickname + ": " + str);
							}
							System.out.println("Client (" + socket.getInetAddress() + "): " + str);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					if (server.isNickBusy(nickname)) {
						server.unsubscribe(this);
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendMsg(String msg) {

		// Сохранене сообщений в БД
		if (nickname != null && !msg.startsWith("/")) {
			AuthService.saveHistory(nickname, msg);
		}

		try {
			out.writeUTF(msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public boolean checkBlackList(String nickname) {
		return blackList.contains(nickname);
	}

	// 2. После загрузки клиента показывать ему последние 100 строк чата из БД
	private void loadHistory() {
		try {
			out.writeUTF(AuthService.getHistory(nickname));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
