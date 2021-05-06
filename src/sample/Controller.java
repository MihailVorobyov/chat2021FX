package sample;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {
	@FXML
	TextArea chatArea;

	@FXML
	TextField textField;

	@FXML
	HBox bottomPanel;

	@FXML
	HBox upperPanel;

	@FXML
	TextField loginField;

	@FXML
	PasswordField passwordField;

	@FXML
	ListView<String> clientList;

	@FXML
	Label userName;

	Socket socket;
	DataInputStream in;
	DataOutputStream out;

	File file;

	static final String ADDRESS = "localhost";
	static final int PORT = 6001;

	private boolean isAuthorized;

//	private String nickname;

	List<TextArea> textAreas;

	public void setAuthorized(boolean isAuthorized) {
		this.isAuthorized = isAuthorized;
		if (!isAuthorized) {
			upperPanel.setVisible(true);
			upperPanel.setManaged(true);

			bottomPanel.setVisible(false);
			bottomPanel.setManaged(false);

			clientList.setVisible(false);
			clientList.setManaged(false);
		} else {
			upperPanel.setVisible(false);
			upperPanel.setManaged(false);

			bottomPanel.setVisible(true);
			bottomPanel.setManaged(true);

			clientList.setVisible(true);
			clientList.setManaged(true);
		}
	}

	@FXML
	void sendMsg() {
		try {
			out.writeUTF(textField.getText());
			textField.clear();
			textField.requestFocus();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void connect() {
		try {
			socket = new Socket(ADDRESS, PORT);

			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());

			setAuthorized(false);

			new Thread(() -> {
				try {
					while (true) {
						String str = in.readUTF();
						if ("/auth-OK".equals(str)) {
							setAuthorized(true);
							chatArea.clear();
							break;
						} else {
							for (TextArea ta : textAreas) {
								ta.appendText(str + "\n");
							}
						}
					}

//					{
//						String str = in.readUTF();
//						if (str.startsWith("/nick ")) {
//							nickname = str.replaceFirst("/nick ", "");
//						}
//					}

					// 2. После загрузки клиента показывать ему последние 100 строк чата.
//					file = new File("src/sample/history/" + nickname);
//					loadHistory(file, 100);

					while (true) {
						String str = in.readUTF();

						if ("/serverClosed".equals(str) || "/timeout".equals(str)) {
//							nickname = null;
							file = null;
							chatArea.clear();
							System.out.println(str);
							break;
						} else if (str.startsWith("/clientList ")) {
							String[] tokens = str.split(" ");
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									clientList.getItems().clear();
									for (int i = 1; i < tokens.length; i++) {
										clientList.getItems().add(tokens[i]);
									}
								}
							});
							 // загрузка истории из БД
						} else if (str.startsWith("/history ")) {
							chatArea.setText(str.replaceFirst("/history ", ""));
						} else if (!"".equals(str)){
//							// 1. Добавить в сетевой чат запись локальной истории в текстовый файл на клиенте.
//							saveToFile(file, str + "\n");
							chatArea.appendText(str + "\n");
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					setAuthorized(false);
				}
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
			chatArea.appendText("Connection refused)\nServer is not available");
		}
	}

	public void tryToAuth(ActionEvent actionEvent) {
		if (socket == null || socket.isClosed()) {
			connect();
		}
		try {
			out.writeUTF("/auth " + loginField.getText() + " " + passwordField.getText());
			loginField.clear();
			passwordField.clear();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void disconnect() {
		if (socket != null) {
			if (!socket.isClosed()) {
				try {
					out.writeUTF("/end");
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void selectClient(MouseEvent mouseEvent) {
		if (mouseEvent.getClickCount() == 2) {
			MiniStage ms = new MiniStage(clientList.getSelectionModel().getSelectedItem(), out, textAreas);
			ms.show();
		}
	}

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		setAuthorized(false);
		textAreas = new ArrayList<>();
		textAreas.add(chatArea);
	}

	public void logUp(ActionEvent actionEvent) {
		RegistrationStage rs = new RegistrationStage(out);
		rs.show();
	}

	// 1. Добавить в сетевой чат запись локальной истории в текстовый файл на клиенте.
	private void saveToFile (File userHistoryFile, String str) {
		if (!userHistoryFile.exists()) {
			try {
				userHistoryFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(userHistoryFile, true))) {
			dos.writeUTF(str);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void loadHistory(File aFile, int n) {
		String s;

		try {
			RandomAccessFile raf = new RandomAccessFile(aFile, "r");
			long length = aFile.length() - 1;
			int readLines = 0;
			StringBuilder sb = new StringBuilder();

			for(long i = length; i >= 0; i--) {
				raf.seek(i);
				char c = (char) raf.read();

				if (c == '\n') {
					readLines++;
					if (readLines == n) {
						break;
					}
				}
				sb.append(c);
			}
			sb.reverse();
			System.out.println(sb.toString());
			chatArea.appendText(sb.toString());
		} catch (FileNotFoundException e) {
			System.out.println("Файл истории не найден");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
