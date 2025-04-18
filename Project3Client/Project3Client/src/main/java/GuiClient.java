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

import java.util.*;

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
	Label timerLabel;
	int turnSeconds;
	private Timer currentTimer;

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
						createGameGui();
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
					case REMATCH_ACCEPTED:
						System.out.println("Rematch accepted!");
						if (!sceneMap.containsKey("game")) {
							createGameGui();
						}
						resetBoard();
						isMyTurn = false;
						turnLabel.setText("Waiting for opponent...");
						mainStage.setScene(sceneMap.get("game"));
						break;
					case TIMER_UPDATE:
						turnSeconds = Integer.parseInt(msg.getMessage());
						updateTimerLabel(turnSeconds);
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
			showGameSettings();
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

	public void showGameSettings() {
		createGameBtn = new Button("Create Game");
		Button backBtn = new Button("Back to Lobby");

		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("lobby"));
		});

		Slider slider = new Slider(5, 50, 25); // Min 5, Max 50, default 25
		slider.setShowTickLabels(true);
		slider.setShowTickMarks(true);
		slider.setMajorTickUnit(5);
		slider.setMinorTickCount(4);
		slider.setBlockIncrement(1);

		Label timeLabel = new Label("Selected time: 25 seconds");

		// Update the label whenever the slider is moved
		slider.valueProperty().addListener((obs, oldVal, newVal) -> {
			int roundedVal = (int) Math.round(newVal.doubleValue());
			timeLabel.setText("Selected time: " + roundedVal + " seconds");
		});

		VBox timeBox = new VBox(10, new Label("Pick time per turn:"), slider, timeLabel);
		timeBox.setAlignment(Pos.CENTER);

		// Optional AI section — not functional yet
		HBox aiBox = new HBox(10, new Button("Yes"), new Button("No")); // Placeholder
		VBox aiSection = new VBox(10, new Label("Enable AI?"), aiBox);
		aiSection.setAlignment(Pos.CENTER);

		VBox layout = new VBox(20, timeBox, aiSection, createGameBtn, backBtn);
		layout.setAlignment(Pos.CENTER);
		layout.setPadding(new Insets(20));

		sceneMap.put("settings", new Scene(layout, 400, 300));
		mainStage.setScene(sceneMap.get("settings"));

		createGameBtn.setOnAction(e -> {
			int selectedTime = (int) Math.round(slider.getValue());
			// ✅ Send selected time in seconds as message to server
			clientConnection.send(new Message(MessageType.CREATE_GAME, Integer.toString(selectedTime), null));
			showWaitingScreen();
		});
	}

	public void showWaitingScreen() {
		VBox box = new VBox(10);
		Label label = new Label("Waiting for opponent...");
		Button backBtn = new Button("Back to Lobby");

		backBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.CANCEL_GAME_CREATION, "", null));
			mainStage.setScene(sceneMap.get("lobby"));
		});

		box.getChildren().addAll(label, backBtn);
		box.setAlignment(Pos.CENTER);
		sceneMap.put("waiting", new Scene(box, 400, 300));
		mainStage.setScene(sceneMap.get("waiting"));
	}

	public void showGameListScreen() {
		gameListView = new ListView<>();
		Button joinSelectedBtn = new Button("Join Selected Game");
		Button backBtn = new Button("Back to Lobby");

		joinSelectedBtn.setOnAction(e -> {
			String selected = gameListView.getSelectionModel().getSelectedItem();
			if (selected != null) {
				clientConnection.send(new Message(MessageType.JOIN_GAME, selected, null));
			}
		});

		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("lobby"));
		});

		VBox box = new VBox(10, new Label("Available Games:"), gameListView, joinSelectedBtn, backBtn);
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

		timerLabel = new Label("Time Left: 0");
		timerLabel.setStyle("-fx-font-size: 16px;");

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
						System.out.println("MOVE: Column " + finalCol);
						clientConnection.send(new Message(MessageType.MOVE, Integer.toString(finalCol), null));
						currentTimer.cancel();
						turnLabel.setText("Opponent's Turn");
						timerLabel.setText("Time Left: 0");
						isMyTurn = false;
					}
				});
				GridPane.setColumnIndex(cell, col);
				GridPane.setRowIndex(cell, row);
				gameBoard.add(cell, col, row);
			}
		}

		VBox root = new VBox(10, turnLabel, timerLabel, new Label("Connect 4 Game"), gameBoard);
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

	public void updateTimerLabel(int seconds) {
		if (currentTimer != null) {
			currentTimer.cancel();
		}

		currentTimer = new Timer();
		final int[] remaining = {seconds};

		currentTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (remaining[0] <= 0) {
					Platform.runLater(() -> timerLabel.setText("Time Left: 0"));
					currentTimer.cancel();
				} else {
					Platform.runLater(() -> timerLabel.setText("Time Left: " + remaining[0]));
					remaining[0]--;
				}
			}
		}, 0, 1000);
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

	public void resetBoard() {
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				Button cell = (Button) getNodeFromGridPane(gameBoard, col, row);
				if (cell != null) {
					cell.setStyle("-fx-background-color: lightgray;");
				}
			}
		}
	}

	public void showGameOver(String winner) {
		if (currentTimer != null) {
			currentTimer.cancel();
		}

		Button returnBtn = new Button("Return to Lobby");
		Button rematchBtn = new Button("Rematch");

		returnBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.RETURN_TO_LOBBY, "", null));
			mainStage.setScene(sceneMap.get("lobby"));
		});

		rematchBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.REMATCH, "", null));
			turnLabel.setText("Waiting for opponent to accept rematch...");
			mainStage.setScene(sceneMap.get("game"));
			resetBoard();
			isMyTurn = false;
		});

		VBox box = new VBox(10,
				new Label("Game Over! " + winner),
				rematchBtn,
				returnBtn
		);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));
		sceneMap.put("gameOver", new Scene(box, 400, 300));
		mainStage.setScene(sceneMap.get("gameOver"));
	}
}
