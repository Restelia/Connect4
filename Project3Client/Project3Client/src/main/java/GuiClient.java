import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

public class GuiClient extends Application {

	TextField c1, recipientField, username, password;
	Button b1, createGameBtn, joinGameBtn, logInBtn, signUpBtn, leaderBoardBtn, usersOnlineBtn, friendsListBtn, logOutBtn;
	HashMap<String, Scene> sceneMap;
	VBox clientBox, lobbyBox, signUpBox, accountBox, userInputs, notificationArea;
	Client clientConnection;
	ListView<String> listItems2, gameListView;
	Stage mainStage;
	GridPane gameBoard;
	StackPane rootPane;;
	String currentPlayerId = "", currentUsername, localUsername;
	boolean isMyTurn;
	Label turnLabel, timerLabel, status, usernameLabel, welcomeLabel;
	int turnSeconds, width=700, height=500;
	Timer currentTimer;
	NotificationManager notificationManager;

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
					case USERNAME:
						currentUsername = msg.getMessage();
						usernameLabel.setText(currentUsername);
						localUsername = currentUsername;
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
					case LEADERBOARD:
						String leaderboardData = msg.getMessage();
						List<UserStats> players = Arrays.stream(leaderboardData.split(";"))
								.map(entry -> {
									String[] parts = entry.split(",");
									String name = parts[0];
									int wins = Integer.parseInt(parts[1]);
									int losses = Integer.parseInt(parts[2]);
									int draws = Integer.parseInt(parts[3]);
									return new UserStats(name, wins, losses, draws);
								})
								.collect(Collectors.toList());

						createLeaderBoardGui(players);
						showLeaderBoardScene();
						break;
					case LOGIN:
						String response = msg.getMessage();
						if (Boolean.parseBoolean(response)) {
							createLobbyGui();
							returnToLobby();
						} else {
							status.setText("INCORRECT USERNAME OR PASSWORD");
							status.setVisible(true);
						}
						break;
					case ADDING_USER:
						logInScreen();
						mainStage.setScene(sceneMap.get("login"));
						break;
					case ALREADY_LOGGED_IN:
						status.setText("USER ALREADY LOGGED IN");
						status.setVisible(true);
						break;
					case ONLINE_USERS:
						showOnlineUsers(msg.getMessage().split(","));
						break;
					case LOG_OUT:
						createWelcomeScreen();
						mainStage.setScene(sceneMap.get("welcome"));
						break;
					case FRIEND_REQUEST_NOTIFICATION:
						String requesterName = msg.getMessage();
						HBox friendRequestCard = new HBox(10);
						friendRequestCard.setAlignment(Pos.CENTER_LEFT);
						friendRequestCard.setStyle("-fx-background-color: rgba(40,120,200,0.9); -fx-background-radius: 5; -fx-padding: 10;");

						Label requestLabel = new Label(requesterName + " wants to be your friend!");
						requestLabel.setTextFill(Color.WHITE);

						Button acceptBtn = new Button("✓");
						acceptBtn.setStyle("-fx-background-color: rgba(0,200,0,0.7); -fx-text-fill: white;");
						acceptBtn.setOnAction(e -> {
							clientConnection.send(new Message(
									MessageType.FRIEND_REQUEST_RESPONSE,
									"accept",
									requesterName
							));
							notificationManager.showNotification("You are now friends with " + requesterName);
							notificationArea.getChildren().remove(friendRequestCard);
						});

						Button rejectBtn = new Button("✕");
						rejectBtn.setStyle("-fx-background-color: rgba(200,0,0,0.7); -fx-text-fill: white;");
						rejectBtn.setOnAction(e -> {
							clientConnection.send(new Message(
									MessageType.FRIEND_REQUEST_RESPONSE,
									"reject",
									requesterName
							));
							notificationArea.getChildren().remove(friendRequestCard);
						});

						friendRequestCard.getChildren().addAll(requestLabel, acceptBtn, rejectBtn);
						Platform.runLater(() -> {
							long friendRequestCount = notificationArea.getChildren().stream()
									.filter(node -> node instanceof HBox)
									.count();

							if (friendRequestCount >= 3) {
								// Optionally show a notification that limit has been reached
								notificationManager.showNotification("Friend request limit reached (3 max)");
								return;
							}

							notificationArea.getChildren().add(friendRequestCard);
							if (!notificationArea.isVisible()) {
								notificationArea.setVisible(true);
								TranslateTransition showTransition = new TranslateTransition(Duration.millis(300), notificationArea);
								showTransition.setToY(0);
								showTransition.play();
							}
						});

						PauseTransition delay = new PauseTransition(Duration.seconds(5));
						delay.setOnFinished(ev -> {
							notificationArea.getChildren().remove(friendRequestCard);
						});
						delay.play();
						break;

					case FRIENDS_LIST:
						String[] friends = msg.getMessage().split(",");
						Platform.runLater(() -> showFriendsList(friends));
						break;

					default:
						listItems2.getItems().add(msg.toString());
						break;
				}
			});
		});

		clientConnection.start();
		createWelcomeScreen();
		createGameGui();

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(sceneMap.get("welcome"));
		primaryStage.setTitle("Client" + currentPlayerId);
		primaryStage.show();
	}

	public void createWelcomeScreen(){
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(700);  // or use scene width binding
		backgroundView.setFitHeight(500); // or use scene height binding
		backgroundView.setPreserveRatio(false);

		// Apply blur effect
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		signUpBtn = new Button("Sign Up");
		logInBtn = new Button("Log in");

		signUpBtn.setId("welcome-button");
		logInBtn.setId("welcome-button");

		welcomeLabel = new Label("Welcome to Connect 4");
		welcomeLabel.setId("welcome-title");

		signUpBtn.setOnAction(e -> {
			signUpScreen();
			mainStage.setScene(sceneMap.get("signup"));
		});

		logInBtn.setOnAction(e -> {
			logInScreen();
			mainStage.setScene(sceneMap.get("login"));
		});

		VBox buttonBox = new VBox(15, signUpBtn, logInBtn);
		buttonBox.setAlignment(Pos.CENTER);

		accountBox = new VBox(30, welcomeLabel, buttonBox);
		accountBox.setAlignment(Pos.CENTER);
		accountBox.setPadding(new Insets(50));
		accountBox.setId("welcome-box");

		StackPane root = new StackPane(backgroundView, accountBox);

		Scene scene = createBaseScene(root);
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
		sceneMap.put("welcome", scene);
	}

	public void signUpScreen(){
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(700);  // or use scene width binding
		backgroundView.setFitHeight(500); // or use scene height binding
		backgroundView.setPreserveRatio(false);

		// Apply blur effect
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		username = new TextField();
		username.setPromptText("Input username here");
		username.setId("text-field");

		password = new TextField();
		password.setPromptText("Input password here");
		password.setId("text-field");

		Button signUpBtn2 = new Button ("Sign up");
		Button backBtn = new Button("⬅");

		Label status = new Label("USERNAME ALREADY TAKEN");
		status.setVisible(false);
		status.setTextFill(Color.RED);

		Label signUpLabel = new Label("Create Account");
		signUpLabel.setId("welcome-title");

		signUpBtn2.setId("welcome-button");
		backBtn.setId("back-button");

		signUpBtn2.setOnAction(e -> {
			String combined = username.getText() + "," + password.getText();
			if (username.getText().trim().isEmpty() || password.getText().trim().isEmpty()){
				status.setText("Username and password cannot be empty.");
				status.setVisible(true);
				return;
			}
			clientConnection.send(new Message(MessageType.USERNPASS, combined, null));
			status.setVisible(true);
		});

		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("welcome"));
		});

		HBox topLeftBox = new HBox(backBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		userInputs = new VBox(15, username, password);

		signUpBox = new VBox(15, signUpLabel, userInputs, signUpBtn2, status);
		signUpBox.setAlignment(Pos.CENTER);
		signUpBox.setPadding(new Insets(20));

		BorderPane root = new BorderPane();
		root.setTop(topLeftBox);       // back button in top-right
		root.setCenter(signUpBox);      // center content

		StackPane layered = new StackPane(backgroundView, root);

		Scene scene = createBaseScene(layered);
		sceneMap.put("signup", scene);
	}

	public void logInScreen(){
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(700);  // or use scene width binding
		backgroundView.setFitHeight(500); // or use scene height binding
		backgroundView.setPreserveRatio(false);

		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		username = new TextField();
		username.setPromptText("Input username here");
		username.setId("text-field");

		status = new Label();
		status.setVisible(false);
		status.setTextFill(Color.RED);

		password = new TextField();
		password.setPromptText("Input password here");
		password.setId("text-field");

		Label signUpLabel = new Label("Login to your account");
		signUpLabel.setId("welcome-title");

		Button logInBtn2 = new Button ("Log in");
		logInBtn2.setId("welcome-button");

		Button backBtn = new Button("⬅");
		backBtn.setId("back-button");

		logInBtn2.setOnAction(e -> {
			String combined = username.getText() + "," + password.getText();
			if (username.getText().trim().isEmpty() || password.getText().trim().isEmpty()){
				status.setText("Username and password cannot be empty.");
				status.setVisible(true);
				return;
			}
			clientConnection.send(new Message(MessageType.LOGIN, combined, null));
		});

		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("welcome"));
		});

		HBox topLeftBox = new HBox(backBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		userInputs = new VBox(15, username, password);
		signUpBox = new VBox(15, signUpLabel, userInputs, logInBtn2, status);
		signUpBox.setAlignment(Pos.CENTER);
		signUpBox.setPadding(new Insets(20));

		BorderPane root = new BorderPane();
		root.setTop(topLeftBox);        // back button on top left
		root.setCenter(signUpBox);      // form centered

		StackPane layered = new StackPane(backgroundView, root); // background + UI layered
		Scene scene = createBaseScene(layered);
		sceneMap.put("login", scene);
	}

	public Scene createBaseScene(Pane content) {
        // root for the whole app
		rootPane = new StackPane();
		rootPane.getChildren().add(content);

		// Create notification area (only once)
		if (notificationArea == null) {
			notificationArea = new VBox(5);
			notificationArea.setAlignment(Pos.BOTTOM_RIGHT);
			notificationArea.setPadding(new Insets(10));
			notificationArea.setTranslateY(100);
			notificationArea.setVisible(false);
			notificationArea.setPickOnBounds(false);

			notificationManager = new NotificationManager(notificationArea);
			notificationArea.getStyleClass().add("notification-area");
		}

		if (notificationArea.getParent() != null) {
			((Pane) notificationArea.getParent()).getChildren().remove(notificationArea);
		}

		// Ensure notification area is on top
		rootPane.getChildren().add(notificationArea);

		Scene scene = new Scene(rootPane, width, height);
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
		return scene;
	}

	public void createLobbyGui() {
		createGameBtn = new Button("Create Game");
		createGameBtn.setId("welcome-button");
		joinGameBtn = new Button("Join Game");
		leaderBoardBtn = new Button("Leaderboard");
		usersOnlineBtn = new Button("Users Online");
		friendsListBtn = new Button("Friends List");
		logOutBtn = new Button("Log Out");
		usernameLabel = new Label();

		createGameBtn.setOnAction(e -> {
			showGameSettings();
		});

		joinGameBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.REQUEST_GAMES, "", null));
			showGameListScreen();
		});

		leaderBoardBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.GET_LEADERBOARD, "", null));
		});

		usersOnlineBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.VIEW_ONLINE_USERS, "", null));
		});

		friendsListBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.VIEW_FRIENDS, currentUsername, null));
		});

		logOutBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.LOG_OUT, null, null));
		});

		Button testNotificationBtn = new Button("Test Notification");
		testNotificationBtn.setOnAction(e ->
				notificationManager.showNotification("This is a test notification!")
		);

		lobbyBox = new VBox(15, createGameBtn, joinGameBtn,leaderBoardBtn, usersOnlineBtn, friendsListBtn, usernameLabel, testNotificationBtn, logOutBtn);
		lobbyBox.setAlignment(Pos.CENTER);
		lobbyBox.setPadding(new Insets(20));

		Scene lobbyScene = createBaseScene(lobbyBox);
		sceneMap.put("lobby", lobbyScene);
	}

	public void returnToLobby() {
		// Get the stored lobby scene
		Scene lobbyScene = sceneMap.get("lobby");

		// Ensure notification area is properly attached to the lobby scene
		if (notificationArea != null && notificationArea.getParent() != null) {
			((Pane) notificationArea.getParent()).getChildren().remove(notificationArea);
		}

		// Get the root pane of the lobby scene
		StackPane lobbyRoot = (StackPane) lobbyScene.getRoot();

		// Add notification area to the lobby root
		if (notificationArea != null) {
			lobbyRoot.getChildren().add(notificationArea);
		}

		// Set the scene
		mainStage.setScene(lobbyScene);
	}

	public void showGameSettings() {
		createGameBtn = new Button("Create Game");
		Button backBtn = new Button("Back to Lobby");

		backBtn.setOnAction(e -> {
			returnToLobby();
		});

		Slider timerSlider = new Slider(5, 50, 25); // Min 5, Max 50, default 25
		timerSlider.setShowTickLabels(true);
		timerSlider.setShowTickMarks(true);
		timerSlider.setMajorTickUnit(5);
		timerSlider.setMinorTickCount(4);
		timerSlider.setBlockIncrement(1);

		Label timeLabel = new Label("Selected time: 25 seconds");

		// Update the label whenever the slider is moved
		timerSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
			int roundedVal = (int) Math.round(newVal.doubleValue());
			timeLabel.setText("Selected time: " + roundedVal + " seconds");
		});

		VBox timeBox = new VBox(10, new Label("Pick time per turn:"), timerSlider, timeLabel);
		timeBox.setAlignment(Pos.CENTER);


		// Optional AI section — not functional yet
		ToggleGroup aiToggleGroup = new ToggleGroup();
		RadioButton noAIRadio = new RadioButton("Human Opponent");
		RadioButton easyAIRadio = new RadioButton("Easy AI");
		RadioButton hardAIRadio = new RadioButton("Hard AI");

		noAIRadio.setToggleGroup(aiToggleGroup);
		easyAIRadio.setToggleGroup(aiToggleGroup);
		hardAIRadio.setToggleGroup(aiToggleGroup);
		noAIRadio.setSelected(true); // Default to human opponent

		VBox aiSelectionBox = new VBox(10, new Label("Select Opponent:"), noAIRadio, easyAIRadio, hardAIRadio);
		aiSelectionBox.setAlignment(Pos.CENTER_LEFT);
		aiSelectionBox.setPadding(new Insets(0, 0, 0, 20));

		VBox layout = new VBox(20, timeBox, aiSelectionBox, createGameBtn, backBtn);
		layout.setAlignment(Pos.CENTER);
		layout.setPadding(new Insets(20));

		sceneMap.put("settings", createBaseScene(layout));
		mainStage.setScene(sceneMap.get("settings"));

		createGameBtn.setOnAction(e -> {
			int selectedTime = (int) Math.round(timerSlider.getValue());
			// ✅ Send selected time in seconds as message to server
			String messageContent;

			// Determine which opponent was selected
			if (easyAIRadio.isSelected()) {
				messageContent = "1," + selectedTime; // 1 = Easy AI
			} else if (hardAIRadio.isSelected()) {
				messageContent = "2," + selectedTime; // 2 = Hard AI
			} else {
				messageContent = "0," + selectedTime; // 0 = Human opponent
			}

			// Send appropriate message type
			if (noAIRadio.isSelected()) {
				clientConnection.send(new Message(MessageType.CREATE_GAME, messageContent, null));
			} else {
				clientConnection.send(new Message(MessageType.CREATE_BOT_GAME, messageContent, null));
			}

			showWaitingScreen();
		});
	}

	public void showWaitingScreen() {
		VBox box = new VBox(10);
		Label label = new Label("Waiting for opponent...");
		Button backBtn = new Button("Back to Lobby");

		backBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.CANCEL_GAME_CREATION, "", null));
			returnToLobby();
		});

		box.getChildren().addAll(label, backBtn);
		box.setAlignment(Pos.CENTER);
		sceneMap.put("waiting", createBaseScene(box));
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
			returnToLobby();
		});

		VBox box = new VBox(10, new Label("Available Games:"), gameListView, joinSelectedBtn, backBtn);
		box.setPadding(new Insets(10));
		sceneMap.put("gameList", createBaseScene(box));
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

		VBox gameBox = new VBox(10, turnLabel, timerLabel, new Label("Connect 4 Game"), gameBoard);
		gameBox.setPadding(new Insets(10));

		HBox gameSceneHBox = new HBox(10, gameBox, clientBox);

		sceneMap.put("game", createBaseScene(gameSceneHBox));
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
			returnToLobby();
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
		sceneMap.put("gameOver", createBaseScene(box));
		mainStage.setScene(sceneMap.get("gameOver"));
	}

	public void createLeaderBoardGui(List<UserStats> players) {
		TableView<UserStats> table = new TableView<>();

		TableColumn<UserStats, String> usernameCol = new TableColumn<>("Username");
		usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
		usernameCol.setMinWidth(200);

		TableColumn<UserStats, Integer> winsCol = new TableColumn<>("Wins");
		winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));
		winsCol.setMinWidth(100);

		TableColumn<UserStats, Integer> lossesCol = new TableColumn<>("Losses");
		lossesCol.setCellValueFactory(new PropertyValueFactory<>("losses"));
		lossesCol.setMinWidth(100);

		TableColumn<UserStats, Integer> drawsCol = new TableColumn<>("Draws");
		drawsCol.setCellValueFactory(new PropertyValueFactory<>("draws"));
		drawsCol.setMinWidth(100);

		table.getColumns().addAll(usernameCol, winsCol, lossesCol, drawsCol);
		table.setItems(FXCollections.observableArrayList(players));
		table.getSortOrder().add(winsCol);  // Sort by wins descending
		winsCol.setSortType(TableColumn.SortType.DESCENDING);

		Button backButton = new Button("Back to Lobby");
		backButton.setOnAction(e -> returnToLobby());

		VBox layout = new VBox(10, new Label("🏆 Leaderboard"), table, backButton);
		layout.setAlignment(Pos.CENTER);
		layout.setPadding(new Insets(20));

		sceneMap.put("leaderboard", createBaseScene(layout));
	}

	public void showLeaderBoardScene() {
		mainStage.setScene(sceneMap.get("leaderboard"));
	}

	public void showOnlineUsers(String[] users) {
		ListView<String> userListView = new ListView<>();

		// Filter out the current user from the list
		List<String> filteredUsers = Arrays.stream(users)
				.filter(username -> !username.equals(currentUsername))
				.collect(Collectors.toList());

		userListView.getItems().setAll(filteredUsers);

		Button sendRequestBtn = new Button("Send Friend Request");
		Button closeBtn = new Button("Close");

		sendRequestBtn.setOnAction(e -> {
			String selectedUser = userListView.getSelectionModel().getSelectedItem();
			if (selectedUser != null) {
				// Create and send a friend request message
				Message friendRequest = new Message(
						MessageType.ADD_FRIEND,
						currentUsername,
						selectedUser  // Send to the selected user
				);
				clientConnection.send(friendRequest);

				// Show confirmation to the sender
				status.setText("Friend request sent to " + selectedUser);
				status.setVisible(true);
			}
		});

		closeBtn.setOnAction(e -> returnToLobby());

		// Update label to show count of online users (excluding yourself)
		Label titleLabel = new Label("Currently Online Users (" + filteredUsers.size() + "):");

		VBox box = new VBox(10,
				titleLabel,
				userListView,
				new HBox(10, sendRequestBtn, closeBtn) // Put buttons side by side
		);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));

		Scene onlineUsersScene = createBaseScene(box);
		mainStage.setScene(onlineUsersScene);
	}

	public void showFriendsList(String[] friends) {
		ListView<String> friendsListView = new ListView<>();
		friendsListView.getItems().setAll(friends);

		Button closeBtn = new Button("Close");
		closeBtn.setOnAction(e -> returnToLobby());

		Label titleLabel = new Label("Your Friends (" + friends.length + "):");

		VBox box = new VBox(10,
				titleLabel,
				friendsListView,
				new HBox(10, closeBtn)
		);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));

		Scene friendsScene = createBaseScene(box);
		mainStage.setScene(friendsScene);
	}
}
