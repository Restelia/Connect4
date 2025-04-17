import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class GuiClient extends Application {

	TextField c1;
	TextField recipientField;
	Button b1, createGameBtn, joinGameBtn;
	HashMap<String, Scene> sceneMap;
	VBox clientBox, lobbyBox;
	Client clientConnection;
	ListView<String> listItems2, gameListView;
	Stage mainStage;
	GridPane gameBoard;
	String currentPlayerId = "";
	boolean isMyTurn;
	Label turnLabel; // Turn indicator label

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		mainStage = primaryStage;
		sceneMap = new HashMap<>();

		clientConnection = new Client(data -> {
			Platform.runLater(() -> {
				Message msg = (Message) data;
				switch (msg.getType()) {
					case GAMELIST:
						gameListView.getItems().setAll(msg.getMessage().split(","));
						break;
					case GAME_STARTED:
						System.out.println("GAME_STARTED received! Switching to game scene.");
						if (!sceneMap.containsKey("game")) {
							createGameGui();
						}
						showGameScene();
						break;
					case BOARD_UPDATE:
						System.out.println("Updating board...");
						updateBoard(msg.getMessage());
						break;
					case TURN:
						handleTurnChange(msg.getMessage());
						break;
					case PLAYER_ID:
						currentPlayerId = msg.getMessage();
						break;
					case GAME_OVER:
						showGameOver(msg.getMessage()); // Game over message
						break;
					default:
						listItems2.getItems().add(msg.toString());
						break;
				}
			});
		});

		clientConnection.start();
		createClientGui();
		createLobbyGui();
		createGameGui();

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(sceneMap.get("lobby"));
		primaryStage.setTitle("Client" + currentPlayerId);
		primaryStage.show();
	}

	public void createClientGui() {
		listItems2 = new ListView<>();

		c1 = new TextField();
		c1.setPromptText("Enter Message");
		recipientField = new TextField();
		recipientField.setPromptText("Enter recipient ");
		b1 = new Button("Send");

		b1.setOnAction(e -> {
			String recipient = recipientField.getText();
			if (Objects.equals(recipient, "")) {
				recipient = null;
			}
			Message msg = new Message(MessageType.TEXT, c1.getText(), recipient);
			clientConnection.send(msg);
			c1.clear();
			recipientField.clear();
		});

		clientBox = new VBox(10, c1, recipientField, b1, listItems2);
		clientBox.setStyle("-fx-background-color: blue;" + "-fx-font-family: 'serif';");
		sceneMap.put("client", new Scene(clientBox, 400, 300));
	}

	public void createLobbyGui() {
		createGameBtn = new Button("Create Game");
		joinGameBtn = new Button("Join Game");

		createGameBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.CREATE_GAME, "", null));
			showWaitingScreen();
		});

		joinGameBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.REQUEST_GAMES, "", null));
			showGameListScreen();
		});

		lobbyBox = new VBox(15, createGameBtn, joinGameBtn);
		lobbyBox.setAlignment(Pos.CENTER);
		lobbyBox.setPadding(new Insets(20));
		sceneMap.put("lobby", new Scene(lobbyBox, 400, 300));
	}

	public void showWaitingScreen() {
		VBox box = new VBox(10, new Label("Waiting for opponent..."));
		box.setAlignment(Pos.CENTER);
		sceneMap.put("waiting", new Scene(box, 400, 300));
		mainStage.setScene(sceneMap.get("waiting"));
	}

	public void showGameListScreen() {
		gameListView = new ListView<>();
		Button joinSelectedBtn = new Button("Join Selected Game");

		joinSelectedBtn.setOnAction(e -> {
			String selected = gameListView.getSelectionModel().getSelectedItem();
			if (selected != null) {
				clientConnection.send(new Message(MessageType.JOIN_GAME, selected, null));
			}
		});

		VBox box = new VBox(10, new Label("Available Games:"), gameListView, joinSelectedBtn);
		box.setPadding(new Insets(10));
		sceneMap.put("gameList", new Scene(box, 400, 300));
		mainStage.setScene(sceneMap.get("gameList"));
	}

	public void showGameScene() {
		mainStage.setScene(sceneMap.get("game"));
	}

	public void createGameGui() {
		turnLabel = new Label("Opponent's Turn");
		turnLabel.setStyle("-fx-font-size: 16px;");

		gameBoard = new GridPane();
		gameBoard.setPadding(new Insets(10));
		gameBoard.setHgap(5);
		gameBoard.setVgap(5);

		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				Button cell = new Button();
				cell.setPrefSize(50, 50);
				cell.setStyle("-fx-background-color: lightgray;");
				int finalCol = col;
				cell.setOnAction(e -> {
					if (isMyTurn) {
						cell.setStyle("-fx-background-color: black;");
						System.out.println("MOVE: Column " + finalCol);
						clientConnection.send(new Message(MessageType.MOVE, Integer.toString(finalCol), null));
						turnLabel.setText("Opponent's Turn");
						isMyTurn = false;
					}
				});
				GridPane.setColumnIndex(cell, col);
				GridPane.setRowIndex(cell, row);
				gameBoard.add(cell, col, row);
			}
		}

		VBox root = new VBox(10, turnLabel, new Label("Connect 4 Game"), gameBoard);
		root.setPadding(new Insets(10));
		sceneMap.put("game", new Scene(root, 400, 400));
	}

	public void updateBoard(String boardString) {
		String[] rows = boardString.split(",");
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				Button cell = (Button) getNodeFromGridPane(gameBoard, col, row);
				char value = rows[row].charAt(col);
				if (value == '1') {
					cell.setStyle("-fx-background-color: red;");
				} else if (value == '2') {
					cell.setStyle("-fx-background-color: yellow;");
				} else {
					cell.setStyle("-fx-background-color: lightgray;");
				}
			}
		}
	}

	private javafx.scene.Node getNodeFromGridPane(GridPane gridPane, int col, int row) {
		for (javafx.scene.Node node : gridPane.getChildren()) {
			Integer columnIndex = GridPane.getColumnIndex(node);
			Integer rowIndex = GridPane.getRowIndex(node);
			if (columnIndex != null && rowIndex != null && columnIndex == col && rowIndex == row) {
				return node;
			}
		}
		return null;
	}

	public void handleTurnChange(String playerId) {
		if (playerId.equals(currentPlayerId)) {
			turnLabel.setText("Your Turn");
			isMyTurn = true;
		} else {
			turnLabel.setText("Opponent's Turn");
			isMyTurn = false;
		}
	}

	public void showGameOver(String winner) {
		VBox box = new VBox(10, new Label("Game Over! Winner: " + winner), new Button("Return to Lobby"));
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));
		sceneMap.put("gameOver", new Scene(box, 400, 300));
		mainStage.setScene(sceneMap.get("gameOver"));
	}
}
