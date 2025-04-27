/*
Name: Ali Azam, Andy Tran
NetID: aazam7, atran59
Date: 4/26/2025
Class: CS342
Description: This project is a JavaFX-based networked Connect 4 game with user authentication, online matchmaking, live chat, friend requests, and a dynamic leaderboard system.
 */

import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.shape.Circle;

import java.util.*;
import java.util.stream.Collectors;

public class GuiClient extends Application {

	TextField c1, recipientField, username, password;
	Button b1, createGameBtn, joinGameBtn, logInBtn, signUpBtn, leaderBoardBtn, usersOnlineBtn, friendsListBtn, logOutBtn;
	HashMap<String, Scene> sceneMap;
	VBox lobbyBox, signUpBox, accountBox, userInputs, notificationArea;
	Client clientConnection;
	ListView<String> listItems2, gameListView;
	Stage mainStage;
	GridPane gameBoard;
	StackPane rootPane;;
	String currentPlayerId = "", currentUsername, localUsername;
	ComboBox<String> recipientComboBox;
	ListView<String> userListView;
	boolean isMyTurn;
	Label turnLabel, timerLabel, status, welcomeLabel;
	int turnSeconds, width=900, height=700;
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
					case CHAT_RECIPIENTS:  // New case
						updateChatRecipients(msg.getMessage().split(","));
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
	// Creates the initial welcome screen with sign up and login options
	public void createWelcomeScreen(){
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Initialize Sign Up and Log In buttons
		signUpBtn = new Button("Sign Up");
		logInBtn = new Button("Log in");

		// Set button styles
		signUpBtn.setId("welcome-button");
		logInBtn.setId("welcome-button");

		// Create the welcome label
		welcomeLabel = new Label("Welcome to Connect 4");
		welcomeLabel.setId("welcome-title");

		// Set button actions to switch to respective scenes
		signUpBtn.setOnAction(e -> {
			signUpScreen();
			mainStage.setScene(sceneMap.get("signup"));
		});

		logInBtn.setOnAction(e -> {
			logInScreen();
			mainStage.setScene(sceneMap.get("login"));
		});

		// Layout for buttons
		VBox buttonBox = new VBox(15, signUpBtn, logInBtn);
		buttonBox.setAlignment(Pos.CENTER);

		// Layout for title and buttons together
		accountBox = new VBox(30, welcomeLabel, buttonBox);
		accountBox.setAlignment(Pos.CENTER);
		accountBox.setPadding(new Insets(50));
		accountBox.setId("welcome-box");

		// Stack background and main content
		StackPane root = new StackPane(backgroundView, accountBox);

		// Create scene and add CSS
		Scene scene = createBaseScene(root);
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

		// Store scene for later switching
		sceneMap.put("welcome", scene);
	}

	// Creates the sign-up screen where a new user can register an account
	public void signUpScreen(){
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Username input field
		username = new TextField();
		username.setPromptText("Input username here");
		username.setId("text-field");

		// Password input field
		password = new TextField();
		password.setPromptText("Input password here");
		password.setId("text-field");

		// Sign up button
		Button signUpBtn2 = new Button("Sign up");
		// Back button to return to welcome screen
		Button backBtn = new Button("⬅");

		// Status label to show errors (e.g., username taken)
		Label status = new Label("USERNAME ALREADY TAKEN");
		status.setVisible(false);
		status.setTextFill(Color.RED);

		// Title label for the screen
		Label signUpLabel = new Label("Create Account");
		signUpLabel.setId("welcome-title");

		// Apply styling IDs to buttons
		signUpBtn2.setId("welcome-button");
		backBtn.setId("back-button");

		// Handle sign-up button click
		signUpBtn2.setOnAction(e -> {
			String combined = username.getText() + "," + password.getText();
			// Check for empty fields
			if (username.getText().trim().isEmpty() || password.getText().trim().isEmpty()){
				status.setText("Username and password cannot be empty.");
				status.setVisible(true);
				return;
			}
			// Send username and password to server
			clientConnection.send(new Message(MessageType.USERNPASS, combined, null));
			status.setVisible(true);
		});

		// Handle back button click
		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("welcome"));
		});

		// Layout for back button at the top left
		HBox topLeftBox = new HBox(backBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		// Grouping user input fields
		userInputs = new VBox(15, username, password);

		// Grouping everything in the center
		signUpBox = new VBox(15, signUpLabel, userInputs, signUpBtn2, status);
		signUpBox.setAlignment(Pos.CENTER);
		signUpBox.setPadding(new Insets(20));

		// BorderPane to position back button at top and form at center
		BorderPane root = new BorderPane();
		root.setTop(topLeftBox);
		root.setCenter(signUpBox);

		// Layer UI elements over background
		StackPane layered = new StackPane(backgroundView, root);

		// Create and store scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("signup", scene);
	}

	// Creates the login screen where an existing user can log into their account
	public void logInScreen(){
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Username input field
		username = new TextField();
		username.setPromptText("Input username here");
		username.setId("text-field");

		// Password input field
		password = new TextField();
		password.setPromptText("Input password here");
		password.setId("text-field");

		// Status label for login errors
		status = new Label();
		status.setVisible(false);
		status.setTextFill(Color.RED);

		// Title label for login
		Label signUpLabel = new Label("Login to your account");
		signUpLabel.setId("welcome-title");

		// Login button
		Button logInBtn2 = new Button("Log in");
		logInBtn2.setId("welcome-button");

		// Back button to return to welcome screen
		Button backBtn = new Button("⬅");
		backBtn.setId("back-button");

		// Handle login button click
		logInBtn2.setOnAction(e -> {
			String combined = username.getText() + "," + password.getText();
			// Validate non-empty input
			if (username.getText().trim().isEmpty() || password.getText().trim().isEmpty()){
				status.setText("Username and password cannot be empty.");
				status.setVisible(true);
				return;
			}
			// Send login credentials to server
			clientConnection.send(new Message(MessageType.LOGIN, combined, null));
		});

		// Handle back button click
		backBtn.setOnAction(e -> {
			mainStage.setScene(sceneMap.get("welcome"));
		});

		// Layout for back button on top left
		HBox topLeftBox = new HBox(backBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		// Group user inputs
		userInputs = new VBox(15, username, password);

		// Group title, inputs, button, and status together
		signUpBox = new VBox(15, signUpLabel, userInputs, logInBtn2, status);
		signUpBox.setAlignment(Pos.CENTER);
		signUpBox.setPadding(new Insets(20));

		// BorderPane to position back button and form
		BorderPane root = new BorderPane();
		root.setTop(topLeftBox);
		root.setCenter(signUpBox);

		// Layer background and UI elements
		StackPane layered = new StackPane(backgroundView, root);

		// Create and store scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("login", scene);
	}

	// Creates a base scene structure with a notification area overlay
	public Scene createBaseScene(Pane content) {
		// Create the root pane
		rootPane = new StackPane();
		rootPane.getChildren().add(content); // Add the main content (like welcome screen, login screen, etc.)

		// Initialize notification area if it hasn't been created yet
		if (notificationArea == null) {
			notificationArea = new VBox(5);
			notificationArea.setAlignment(Pos.BOTTOM_RIGHT);
			notificationArea.setPadding(new Insets(10));
			notificationArea.setTranslateY(100); // Slide it slightly offscreen initially
			notificationArea.setVisible(false);
			notificationArea.setPickOnBounds(false); // Allows clicks to pass through when invisible

			notificationManager = new NotificationManager(notificationArea); // Manages showing notifications
			notificationArea.getStyleClass().add("notification-area"); // Style class from CSS
		}

		// If notification area was attached to another parent before, remove it
		if (notificationArea.getParent() != null) {
			((Pane) notificationArea.getParent()).getChildren().remove(notificationArea);
		}

		// Always add notification area on top of the root
		rootPane.getChildren().add(notificationArea);

		// Create the scene with specified width and height
		Scene scene = new Scene(rootPane, width, height);

		// Attach the global stylesheet
		scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

		// Return the created scene
		return scene;
	}

	// Creates the lobby GUI with options to create/join games, view leaderboard, friends, and log out
	public void createLobbyGui() {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create the title label
		Label title = new Label("Connect 4");
		title.setId("welcome-title"); // Styled using CSS
		title.setAlignment(Pos.CENTER);

		// Create the footer label
		Label footer = new Label("By: Andy & Ali");
		footer.setTextFill(Color.WHITE);
		footer.setPadding(new Insets(10));

		// Create lobby buttons
		createGameBtn = new Button("Create Game");
		joinGameBtn = new Button("Join Game");
		leaderBoardBtn = new Button("Leaderboard");
		usersOnlineBtn = new Button("Users Online");
		friendsListBtn = new Button("Friends List");
		logOutBtn = new Button("Log Out");

		// Apply styling IDs to buttons
		createGameBtn.setId("welcome-button");
		joinGameBtn.setId("welcome-button");
		leaderBoardBtn.setId("welcome-button");
		usersOnlineBtn.setId("welcome-button");
		friendsListBtn.setId("welcome-button");
		logOutBtn.setId("welcome-button");

		// Button actions
		createGameBtn.setOnAction(e -> showGameSettings());

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

		// Arrange buttons in a VBox
		lobbyBox = new VBox(15, createGameBtn, joinGameBtn, leaderBoardBtn, usersOnlineBtn, friendsListBtn, logOutBtn);
		lobbyBox.setAlignment(Pos.CENTER);
		lobbyBox.setPadding(new Insets(20));

		// Create an empty layer for coin animations
		Pane animationLayer = new Pane();
		animationLayer.setPickOnBounds(false); // So it doesn't block button clicks

		// Timeline animation to randomly spawn falling Connect 4 coins
		Timeline spawnCoins = new Timeline(new KeyFrame(Duration.seconds(0.5), e -> {
			Color color = Math.random() < 0.5 ? Color.web("#E4A14A") : Color.web("CF561F"); // Yellow or Red

			Circle coin = new Circle(20, color); // Coin radius = 20
			coin.setStroke(Color.BLACK);
			coin.setStrokeWidth(1);
			double startX = 120 + Math.random() * 460; // Random start x within center

			coin.setLayoutX(startX);
			coin.setLayoutY(-40); // Start slightly offscreen at top

			// Animate coin falling
			TranslateTransition fall = new TranslateTransition(Duration.seconds(3 + Math.random() * 2), coin);
			fall.setFromY(0);
			fall.setToY(700); // Fall below screen
			fall.setOnFinished(ev -> animationLayer.getChildren().remove(coin)); // Remove after fall

			animationLayer.getChildren().add(coin);
			fall.play();
		}));
		spawnCoins.setCycleCount(Animation.INDEFINITE);
		spawnCoins.play();

		// Set up the main layout
		BorderPane root = new BorderPane();
		root.setTop(title);
		BorderPane.setAlignment(title, Pos.TOP_CENTER);
		BorderPane.setMargin(title, new Insets(35, 0, 0, 0)); // Space from top
		root.setCenter(lobbyBox);
		root.setBottom(footer);
		BorderPane.setAlignment(footer, Pos.BOTTOM_LEFT);

		// Layer the background, animation layer, and UI
		StackPane layered = new StackPane(backgroundView, animationLayer, root);

		// Create and store the lobby scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("lobby", scene);
	}

	// Returns the user back to the lobby screen and ensures the notification area is correctly reattached
	public void returnToLobby() {
		// Get the stored lobby scene
		Scene lobbyScene = sceneMap.get("lobby");

		// If notification area is attached elsewhere, remove it
		if (notificationArea != null && notificationArea.getParent() != null) {
			((Pane) notificationArea.getParent()).getChildren().remove(notificationArea);
		}

		// Get the root StackPane of the lobby scene
		StackPane lobbyRoot = (StackPane) lobbyScene.getRoot();

		// Add the notification area on top of lobby root
		if (notificationArea != null) {
			lobbyRoot.getChildren().add(notificationArea);
		}

		// Switch the stage back to the lobby scene
		mainStage.setScene(lobbyScene);
	}

	// Displays the screen where the player can configure game settings like turn timer and opponent type
	public void showGameSettings() {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create a back button to return to lobby
		Button backBtn = new Button("⬅");
		backBtn.setId("back-button");
		backBtn.setOnAction(e -> returnToLobby());

		// Create labels for sections
		Label pickTimeLabel = new Label("Pick time per turn:");
		pickTimeLabel.setId("section-label");

		Label selectOpponentLabel = new Label("Select Opponent:");
		selectOpponentLabel.setId("section-label");

		// Create 'Create Game' button
		createGameBtn = new Button("Create Game");
		createGameBtn.setId("welcome-button");

		// Create title label
		Label titleLabel = new Label("Game Settings");
		titleLabel.setId("welcome-title");

		// Create timer slider for selecting turn time
		Slider timerSlider = new Slider(5, 50, 25); // Min 5s, Max 50s, Default 25s
		timerSlider.setShowTickLabels(true);
		timerSlider.setShowTickMarks(true);
		timerSlider.setMajorTickUnit(5);
		timerSlider.setMinorTickCount(4);
		timerSlider.setBlockIncrement(1);
		timerSlider.setId("game-timer-slider");

		// Label to display currently selected timer value
		Label timeLabel = new Label("Selected time: 25 seconds");
		timeLabel.setId("section-label");

		// Update time label dynamically as slider moves
		timerSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
			int roundedVal = (int) Math.round(newVal.doubleValue());
			timeLabel.setText("Selected time: " + roundedVal + " seconds");
		});

		// Group the timer label, slider, and live time label
		VBox timeBox = new VBox(10, pickTimeLabel, timerSlider, timeLabel);
		timeBox.setAlignment(Pos.CENTER);

		// Create AI selection (optional opponent type)
		ToggleGroup aiToggleGroup = new ToggleGroup();
		RadioButton noAIRadio = new RadioButton("Human Opponent");
		RadioButton easyAIRadio = new RadioButton("Easy AI");
		RadioButton hardAIRadio = new RadioButton("Hard AI");

		noAIRadio.setId("radio-human");
		easyAIRadio.setId("radio-easy");
		hardAIRadio.setId("radio-hard");

		noAIRadio.setToggleGroup(aiToggleGroup);
		easyAIRadio.setToggleGroup(aiToggleGroup);
		hardAIRadio.setToggleGroup(aiToggleGroup);

		noAIRadio.setSelected(true); // Default to human opponent

		// Group opponent selection buttons
		VBox aiSelectionBox = new VBox(10, selectOpponentLabel, noAIRadio, easyAIRadio, hardAIRadio);
		aiSelectionBox.setAlignment(Pos.CENTER_LEFT);
		aiSelectionBox.setPadding(new Insets(0, 0, 0, 20));

		// Central VBox containing all settings
		VBox centerBox = new VBox(20, titleLabel, timeBox, aiSelectionBox, createGameBtn);
		centerBox.setAlignment(Pos.CENTER);
		centerBox.setPadding(new Insets(10));

		// Top bar layout for back button
		HBox topBar = new HBox(backBtn);
		topBar.setAlignment(Pos.TOP_LEFT);
		topBar.setPadding(new Insets(10));

		// Layout structure using BorderPane
		BorderPane layout = new BorderPane();
		layout.setTop(topBar);
		layout.setCenter(centerBox);

		// Stack background and layout
		StackPane layered = new StackPane(backgroundView, layout);

		// Create and set scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("settings", scene);
		mainStage.setScene(scene);

		// Handle create game button press
		createGameBtn.setOnAction(e -> {
			int selectedTime = (int) Math.round(timerSlider.getValue());
			String messageContent;

			// Determine selected opponent type
			if (easyAIRadio.isSelected()) {
				messageContent = "1," + selectedTime; // 1 = Easy AI
			} else if (hardAIRadio.isSelected()) {
				messageContent = "2," + selectedTime; // 2 = Hard AI
			} else {
				messageContent = "0," + selectedTime; // 0 = Human opponent
			}

			// Send game creation request to server
			if (noAIRadio.isSelected()) {
				clientConnection.send(new Message(MessageType.CREATE_GAME, messageContent, null));
			} else {
				clientConnection.send(new Message(MessageType.CREATE_BOT_GAME, messageContent, null));
			}

			// Show waiting screen after creating the game
			showWaitingScreen();
		});
	}

	// Displays a waiting screen after creating a game while waiting for an opponent to join
	public void showWaitingScreen() {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create a VBox for center content
		VBox box = new VBox(10);

		// Waiting message label
		Label label = new Label("Waiting for opponent...");
		label.setId("welcome-title");

		// Back button to cancel and return to lobby
		Button backBtn = new Button("⬅");
		backBtn.setId("back-button");

		// Action when back button clicked (cancel game creation)
		backBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.CANCEL_GAME_CREATION, "", null));
			returnToLobby();
		});

		// Layout for back button on top left
		HBox topLeftBox = new HBox(backBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		// Center the label
		box.getChildren().addAll(label);
		box.setAlignment(Pos.CENTER);

		// Create BorderPane to organize layout
		BorderPane root = new BorderPane();
		root.setTop(topLeftBox); // Back button on top
		root.setCenter(box);     // Waiting message in center

		// Layer background and UI
		StackPane layered = new StackPane(backgroundView, root);

		// Create and store scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("waiting", scene);

		// Switch to waiting scene
		mainStage.setScene(sceneMap.get("waiting"));
	}

	// Displays the list of available games that the user can join
	public void showGameListScreen() {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create a ListView to display available games
		gameListView = new ListView<>();
		gameListView.setId("game-list");

		// Join selected game button
		Button joinSelectedBtn = new Button("Join Selected");
		joinSelectedBtn.setId("welcome-button");

		// Back button to return to lobby
		Button backBtn = new Button("⬅");
		backBtn.setId("back-button");

		// Title label
		Label availableGames = new Label("Available Games:");
		availableGames.setId("welcome-title");

		// Action when 'Join Selected' button clicked
		joinSelectedBtn.setOnAction(e -> {
			String selected = gameListView.getSelectionModel().getSelectedItem();
			if (selected != null) {
				clientConnection.send(new Message(MessageType.JOIN_GAME, selected, null));
			}
		});

		// Action when back button clicked
		backBtn.setOnAction(e -> {
			returnToLobby();
		});

		// Group title, list, and button vertically
		VBox centerBox = new VBox(10, availableGames, gameListView, joinSelectedBtn);
		centerBox.setAlignment(Pos.CENTER);
		centerBox.setPadding(new Insets(10));

		// Layout for back button at top left
		HBox topBar = new HBox(backBtn);
		topBar.setPadding(new Insets(10));
		topBar.setAlignment(Pos.TOP_LEFT);

		// Organize entire layout using BorderPane
		BorderPane layout = new BorderPane();
		layout.setTop(topBar);
		layout.setCenter(centerBox);

		// Layer background and layout
		StackPane layered = new StackPane(backgroundView, layout);

		// Create and store scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("gameList", scene);

		// Switch to the game list scene
		mainStage.setScene(sceneMap.get("gameList"));
	}

	// Switches the scene to the game scene where gameplay happens
	public void showGameScene() {
		// Set the main stage to the "game" scene
		mainStage.setScene(sceneMap.get("game"));
	}

	// Creates the Connect 4 gameplay GUI, including game board, chat system, and input handling
	public void createGameGui() {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Create the main root HBox to split game and chat areas
		HBox root = new HBox(20);
		root.getStyleClass().add("root"); // Styling class for root

		//----------------------------------------
		// GAME AREA SETUP
		//----------------------------------------

		VBox gameBox = new VBox(15);
		gameBox.getStyleClass().add("game-container");

		// Game title label
		Label gameTitle = new Label("CONNECT 4");
		gameTitle.getStyleClass().add("game-title");

		// Whose turn it is
		turnLabel = new Label("OPPONENT'S TURN");
		turnLabel.getStyleClass().add("turn-label");

		// Countdown timer label
		timerLabel = new Label("TIME LEFT: 0");
		timerLabel.getStyleClass().add("timer-label");

		// Create Connect 4 board grid (6 rows × 7 columns)
		gameBoard = new GridPane();
		gameBoard.getStyleClass().add("game-board");
		gameBoard.setHgap(5); // horizontal gap between cells
		gameBoard.setVgap(5); // vertical gap between cells

		// Populate grid with clickable buttons
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				Button cell = new Button();
				cell.getStyleClass().add("game-cell");

				int finalCol = col; // required because lambda needs final/effectively-final
				cell.setOnAction(e -> {
					if (isMyTurn) {
						System.out.println("MOVE: Column " + finalCol);
						clientConnection.send(new Message(MessageType.MOVE, Integer.toString(finalCol), null));
						if (currentTimer != null) {
							currentTimer.cancel();
						}
						turnLabel.setText("OPPONENT'S TURN");
						timerLabel.setText("TIME LEFT: 0");
						isMyTurn = false;
					}
				});

				gameBoard.add(cell, col, row); // Add button to grid
			}
		}

		// Add all game area elements to VBox
		gameBox.getChildren().addAll(gameTitle, turnLabel, timerLabel, gameBoard);

		//----------------------------------------
		// CHAT AREA SETUP
		//----------------------------------------

		VBox chatBox = new VBox(15);
		chatBox.getStyleClass().add("chat-container");

		// Chat title
		Label chatTitle = new Label("CHAT");
		chatTitle.getStyleClass().add("chat-title");

		// Chat messages list
		listItems2 = new ListView<>();
		listItems2.getStyleClass().add("chat-list");

		// Message input field
		c1 = new TextField();
		c1.setPromptText("Enter Message");
		c1.getStyleClass().add("chat-input");

		// ComboBox to select private message recipient
		recipientComboBox = new ComboBox<>();
		recipientComboBox.setPromptText("Select recipient");
		recipientComboBox.getStyleClass().add("chat-input");
		recipientComboBox.setEditable(true); // allow typing custom name

		// Refresh button to update list of available users
		Button refreshBtn = new Button("↻");
		refreshBtn.getStyleClass().add("refresh-button");
		refreshBtn.setOnAction(e ->
				clientConnection.send(new Message(MessageType.GET_CHAT_RECIPIENTS, "", null))
		);

		// Send button
		b1 = new Button("SEND");
		b1.getStyleClass().add("send-button");
		b1.setOnAction(e -> {
			String recipient = recipientComboBox.getValue();
			if (recipient == null || recipient.isEmpty() || recipient.equals("All")) {
				recipient = null; // null means send to everyone
			}
			Message msg = new Message(MessageType.TEXT, c1.getText(), recipient);
			clientConnection.send(msg);
			c1.clear();
			recipientComboBox.setValue(null); // reset recipient after send
		});

		// HBox to group recipient dropdown and refresh button
		HBox recipientBox = new HBox(5, recipientComboBox, refreshBtn);
		recipientBox.getStyleClass().add("recipient-box");
		recipientBox.setAlignment(Pos.CENTER_LEFT);

		// VBox to group all chat input elements
		VBox chatInputGroup = new VBox(10, recipientBox, c1, b1);
		chatInputGroup.setAlignment(Pos.CENTER_LEFT);

		// Add all chat elements into chatBox
		chatBox.getChildren().addAll(chatTitle, listItems2, chatInputGroup);

		//----------------------------------------
		// COMBINE GAME + CHAT AREA
		//----------------------------------------

		// Add game area and chat area side-by-side
		root.getChildren().addAll(gameBox, chatBox);

		// Stack background and root UI
		StackPane layeredPane = new StackPane();
		layeredPane.getChildren().addAll(backgroundView, root);

		// Create and store the game scene
		sceneMap.put("game", createBaseScene(layeredPane));
	}

	// Updates the visual Connect 4 board based on the server-provided board state string
	public void updateBoard(String boardString) {
		// Split the board string into rows
		String[] rows = boardString.split(",");

		// Loop through each cell (row, column)
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				// Find the Button at this (col, row) position
				Button cell = (Button) getNodeFromGridPane(gameBoard, col, row);
				// Get the value for this cell (e.g., '0' empty, '1' player 1, '2' player 2)
				char value = rows[row].charAt(col);

				// Clear previous styling
				cell.getStyleClass().removeAll("game-cell-player1", "game-cell-player2");

				// Apply style based on the value
				if (value == '1') {
					cell.getStyleClass().add("game-cell-player1"); // Red for player 1
				} else if (value == '2') {
					cell.getStyleClass().add("game-cell-player2"); // Yellow for player 2
				}
			}
		}
	}

	// Helper method to retrieve a specific node (button) from the GridPane based on column and row
	private javafx.scene.Node getNodeFromGridPane(GridPane gridPane, int col, int row) {
		// Loop through all children nodes in the GridPane
		for (javafx.scene.Node node : gridPane.getChildren()) {
			// Get the column and row index of the current node
			Integer columnIndex = GridPane.getColumnIndex(node);
			Integer rowIndex = GridPane.getRowIndex(node);

			// If column and row match the requested position, return this node
			if (columnIndex != null && rowIndex != null && columnIndex == col && rowIndex == row) {
				return node;
			}
		}
		// If no matching node found, return null
		return null;
	}

	// Starts or updates the turn countdown timer label on the game scene
	public void updateTimerLabel(int seconds) {
		// Cancel any previous timer if it exists
		if (currentTimer != null) {
			currentTimer.cancel();
		}

		// Create a new timer
		currentTimer = new Timer();
		final int[] remaining = {seconds}; // Use an array to modify inside inner class

		// Schedule a task to run every 1 second
		currentTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (remaining[0] <= 0) {
					// When time runs out, set label to 0 and cancel timer
					Platform.runLater(() -> timerLabel.setText("Time Left: 0"));
					currentTimer.cancel();
				} else {
					// Update the label each second with the remaining time
					Platform.runLater(() -> timerLabel.setText("Time Left: " + remaining[0]));
					remaining[0]--; // Decrease remaining seconds
				}
			}
		}, 0, 1000); // Start immediately, repeat every 1000ms (1 second)
	}

	// Handles switching turn display and enabling/disabling move inputs based on active player
	public void handleTurnChange(String playerId) {
		if (playerId.equals(currentPlayerId)) {
			// It's our turn
			turnLabel.setText("Your Turn");
			isMyTurn = true;
		} else {
			// It's opponent's turn
			turnLabel.setText("Opponent's Turn");
			isMyTurn = false;
		}
	}

	// Resets the Connect 4 board UI to its initial empty state
	public void resetBoard() {
		// Loop through each cell on the 6x7 grid
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 7; col++) {
				// Get the Button at (col, row)
				Button cell = (Button) getNodeFromGridPane(gameBoard, col, row);
				if (cell != null) {
					// Reset the button's background color to light gray (empty)
					cell.setStyle("-fx-background-color: lightgray;");
				}
			}
		}
	}

	// Displays the game over screen with winner information and options for rematch or return to lobby
	public void showGameOver(String winner) {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Cancel the turn timer if it's still running
		if (currentTimer != null) {
			currentTimer.cancel();
		}

		// Create "Return to Lobby" button
		Button returnBtn = new Button("⬅");
		returnBtn.setId("back-button");

		// Create "Rematch" button
		Button rematchBtn = new Button("Rematch");
		rematchBtn.setId("welcome-button");

		// Label displaying the winner
		Label winOrLoss = new Label("Game Over! " + winner);
		winOrLoss.setId("welcome-title");

		// Handle return button click
		returnBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.RETURN_TO_LOBBY, "", null));
			returnToLobby();
		});

		// Handle rematch button click
		rematchBtn.setOnAction(e -> {
			clientConnection.send(new Message(MessageType.REMATCH, "", null));
			turnLabel.setText("Waiting for opponent to accept rematch...");
			mainStage.setScene(sceneMap.get("game")); // Go back to game screen
			resetBoard(); // Clear the board for new match
			isMyTurn = false;
		});

		// VBox to group winner label and buttons vertically
		VBox box = new VBox(10, winOrLoss, rematchBtn, returnBtn);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(20));

		// Top-left bar for return button
		HBox topLeftBox = new HBox(returnBtn);
		topLeftBox.setAlignment(Pos.TOP_LEFT);
		topLeftBox.setPadding(new Insets(10));

		// Layout everything using BorderPane
		BorderPane root = new BorderPane();
		root.setTop(topLeftBox);
		root.setCenter(box);

		// Stack background and layout
		StackPane layered = new StackPane(backgroundView, root);

		// Create and store the "game over" scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("gameOver", scene);

		// Switch to the "game over" screen
		mainStage.setScene(sceneMap.get("gameOver"));
	}

	// Creates the leaderboard GUI using a TableView populated with player statistics
	public void createLeaderBoardGui(List<UserStats> players) {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create a TableView to show user stats
		TableView<UserStats> table = new TableView<>();
		table.setId("leaderboard-table");

		// Create Username column
		TableColumn<UserStats, String> usernameCol = new TableColumn<>("Username");
		usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
		usernameCol.setMinWidth(200);

		// Create Wins column
		TableColumn<UserStats, Integer> winsCol = new TableColumn<>("Wins");
		winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));
		winsCol.setMinWidth(100);

		// Create Losses column
		TableColumn<UserStats, Integer> lossesCol = new TableColumn<>("Losses");
		lossesCol.setCellValueFactory(new PropertyValueFactory<>("losses"));
		lossesCol.setMinWidth(100);

		// Create Draws column
		TableColumn<UserStats, Integer> drawsCol = new TableColumn<>("Draws");
		drawsCol.setCellValueFactory(new PropertyValueFactory<>("draws"));
		drawsCol.setMinWidth(100);

		// Add all columns to the table
		table.getColumns().addAll(usernameCol, winsCol, lossesCol, drawsCol);

		// Set data to the table
		table.setItems(FXCollections.observableArrayList(players));
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		// Sort leaderboard by wins (descending)
		table.getSortOrder().add(winsCol);
		winsCol.setSortType(TableColumn.SortType.DESCENDING);

		// Create a back button to return to lobby
		Button backButton = new Button("⬅");
		backButton.setId("back-button");
		backButton.setOnAction(e -> returnToLobby());

		// Top bar with back button
		HBox topBar = new HBox(backButton);
		topBar.setAlignment(Pos.TOP_LEFT);
		topBar.setPadding(new Insets(10));

		// Title label for leaderboard
		Label leaderBoardLabel = new Label("🏆 Leaderboard");
		leaderBoardLabel.setId("welcome-title");

		// Center VBox with title and table
		VBox centerBox = new VBox(10, leaderBoardLabel, table);
		centerBox.setAlignment(Pos.CENTER);
		centerBox.setPadding(new Insets(10));

		// Organize the full layout
		BorderPane layout = new BorderPane();
		layout.setTop(topBar);
		layout.setCenter(centerBox);

		// Stack background and layout
		StackPane layered = new StackPane(backgroundView, layout);

		// Create and store leaderboard scene
		Scene scene = createBaseScene(layered);
		sceneMap.put("leaderboard", scene);

		// Show leaderboard
		mainStage.setScene(scene);
	}

	// Switches the scene to the leaderboard screen
	public void showLeaderBoardScene() {
		// Set the main stage to the "leaderboard" scene
		mainStage.setScene(sceneMap.get("leaderboard"));
	}

	// Displays a list of currently online users and allows sending friend requests
	public void showOnlineUsers(String[] users) {
		// Create a new ListView to show online users
		ListView<String> userListView = new ListView<>();

		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Filter out the current user from the online users list
		List<String> filteredUsers = Arrays.stream(users)
				.filter(username -> !username.equals(currentUsername))
				.collect(Collectors.toList());

		// Populate the ListView
		userListView.setId("game-list");
		userListView.getItems().setAll(filteredUsers);

		// Create title label showing how many users are online
		Label titleLabel = new Label("Currently Online Users (" + filteredUsers.size() + "):");
		titleLabel.setId("welcome-title");

		// Create "Friend Request" button
		Button sendRequestBtn = new Button("Friend Request");
		sendRequestBtn.setId("welcome-button");

		// Create "Back" button to return to lobby
		Button closeBtn = new Button("⬅");
		closeBtn.setId("back-button");

		// Handle "Friend Request" button click
		sendRequestBtn.setOnAction(e -> {
			String selectedUser = userListView.getSelectionModel().getSelectedItem();
			if (selectedUser != null) {
				// Create and send friend request message
				Message friendRequest = new Message(
						MessageType.ADD_FRIEND,
						currentUsername,
						selectedUser // recipient username
				);
				clientConnection.send(friendRequest);

				// Show confirmation status (reuse status label)
				status.setText("Friend request sent to " + selectedUser);
				status.setVisible(true);
			}
		});

		// Handle "Back" button click
		closeBtn.setOnAction(e -> returnToLobby());

		// Layout center content (list and button)
		VBox centerBox = new VBox(10, titleLabel, userListView, sendRequestBtn);
		centerBox.setAlignment(Pos.CENTER);
		centerBox.setPadding(new Insets(10));

		// Layout for top bar (back button)
		HBox topBar = new HBox(closeBtn);
		topBar.setAlignment(Pos.TOP_LEFT);
		topBar.setPadding(new Insets(10));

		// Create the main layout
		BorderPane layout = new BorderPane();
		layout.setTop(topBar);
		layout.setCenter(centerBox);

		// Stack background and layout
		StackPane layered = new StackPane(backgroundView, layout);

		// Create scene and show it
		Scene scene = createBaseScene(layered);
		mainStage.setScene(scene);
	}

	// Updates the chat recipient dropdown list with online users, excluding the current user
	public void updateChatRecipients(String[] users) {
		Platform.runLater(() -> {
			// Filter out our own username from the list
			List<String> filteredUsers = Arrays.stream(users)
					.filter(username -> !username.equals(currentUsername))
					.collect(Collectors.toList());

			// Create an observable list of recipients
			ObservableList<String> recipients = FXCollections.observableArrayList(filteredUsers);

			// Add "All" at the top for public messaging
			recipients.add(0, "All");

			// Set the list into the recipient ComboBox
			recipientComboBox.setItems(recipients);
		});
	}

	// Displays the friend list of the currently logged-in user
	public void showFriendsList(String[] friends) {
		// Load background image
		Image backgroundImage = new Image(getClass().getResource("/background.png").toExternalForm());
		ImageView backgroundView = new ImageView(backgroundImage);
		backgroundView.setFitWidth(width);
		backgroundView.setFitHeight(height);
		backgroundView.setPreserveRatio(false);

		// Apply blur effect to background
		GaussianBlur blur = new GaussianBlur(20);
		backgroundView.setEffect(blur);

		// Create a ListView to display the friend list
		ListView<String> friendsListView = new ListView<>();
		friendsListView.setId("game-list");
		friendsListView.getItems().setAll(friends);

		// Create title label showing how many friends
		Label titleLabel = new Label("Your Friends (" + friends.length + "):");
		titleLabel.setId("welcome-title");

		// Create "Back" button to return to lobby
		Button closeBtn = new Button("⬅");
		closeBtn.setId("back-button");
		closeBtn.setOnAction(e -> returnToLobby());

		// Layout center content (title + friend list)
		VBox centerBox = new VBox(10, titleLabel, friendsListView);
		centerBox.setAlignment(Pos.CENTER);
		centerBox.setPadding(new Insets(10));

		// Layout for top bar (back button)
		HBox topBar = new HBox(closeBtn);
		topBar.setAlignment(Pos.TOP_LEFT);
		topBar.setPadding(new Insets(10));

		// Set up the full layout
		BorderPane layout = new BorderPane();
		layout.setTop(topBar);
		layout.setCenter(centerBox);

		// Stack background and layout
		StackPane layered = new StackPane(backgroundView, layout);

		// Create scene and show it
		Scene scene = createBaseScene(layered);
		mainStage.setScene(scene);
	}

}