import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.scene.control.ListView;

public class Server{

	int count = 1;
	ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	TheServer server;
	private final Consumer<Message> callback;
	private static Set<String> loggedInUsers = Collections.synchronizedSet(new HashSet<>());

	Map<String, ObjectOutputStream> clientOutputs = new HashMap<>();
	Queue<Integer> waitingPlayers = new LinkedList<>(); // Created Game and waiting for someone to join
	Map<Integer, Connect4Game> activeGames = new HashMap<>();  // Track active games by gameId
	Map<Integer, Integer> rematchRequests = new HashMap<>();
	Map<Integer, Integer> playerToGameId = new HashMap<>();
	Map<Integer, Integer> waitingPlayerTimers = new HashMap<>();
	int gameIdCounter = 1;

	Server(Consumer<Message> call){
		callback = call;
		server = new TheServer();
		server.start();
	}

	public class TheServer extends Thread{

		public void run() {
			try(ServerSocket mysocket = new ServerSocket(5555);){
		    System.out.println("Server is waiting for a client!");

		    while(true) {

				ClientThread c = new ClientThread(mysocket.accept(), count);
				callback.accept(new Message(MessageType.NEWUSER, "client has connected to server: " + "client #" + count, null));
				clients.add(c);
				c.start();
				count++;

			    }
			}//end of try
				catch(Exception e) {
					callback.accept(new Message(MessageType.DISCONNECT,"Server socket did not launch", null));
				}
			}//end of while
		}

		class ClientThread extends Thread{

			String clientID;
			Socket connection;
			int count;
			ObjectInputStream in;
			ObjectOutputStream out;
			String clientUsername;

			ClientThread(Socket s, int count){
				this.connection = s;
				this.count = count;
				this.clientID = String.valueOf(count);
			}

			public void updateClients(Message message) {
				for (ClientThread t : clients) {
					try {
						if (message.getRecipient() == null || message.getRecipient().equals(t.clientID)) {
							t.out.writeObject(message);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			public void sendToRecipient(String recipient, Message message) {
				for (ClientThread t : clients) {
					if (t.clientID.equals(recipient)) {
						try {
							t.out.writeObject(message);
							break;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}

			private void handleTurnTimeout(Connect4Game game, int nextPlayer, int gameId) {
				System.out.println("⏰ Player " + nextPlayer + " timed out.");

				game.incrementTimeouts();

				if (game.getConsecutiveTimeouts() >= 2) {
					System.out.println("⚠️ Both players timed out. Ending game as a draw.");
					Message drawMsg = new Message(MessageType.GAME_OVER, "Game ended in a draw due to inactivity.", null);

					ObjectOutputStream p1Out = clientOutputs.get("client" + game.getCurrentPlayer());
					ObjectOutputStream p2Out = clientOutputs.get("client" + game.getOtherPlayer(game.getCurrentPlayer()));

					try {
						if (p1Out != null) p1Out.writeObject(drawMsg);
						if (p2Out != null) p2Out.writeObject(drawMsg);
					} catch (IOException e) {
						e.printStackTrace();
					}

					activeGames.remove(gameId);
					return;
				}

				game.switchTurn();
				int newTurnPlayer = game.getCurrentPlayer();

				String updatedBoard = game.getBoardString();
				Message updateMsg = new Message(MessageType.BOARD_UPDATE, updatedBoard, null);
				Message newTurnMsg = new Message(MessageType.TURN, "client" + newTurnPlayer, null);

				ObjectOutputStream timedOutOut = clientOutputs.get("client" + nextPlayer);
				ObjectOutputStream newPlayerOut = clientOutputs.get("client" + newTurnPlayer);
				ObjectOutputStream otherPlayerOut = clientOutputs.get("client" + game.getOtherPlayer(newTurnPlayer));

				try {
					if (timedOutOut != null) {
						timedOutOut.writeObject(new Message(MessageType.TEXT, "You ran out of time. Turn skipped.", null));
						timedOutOut.writeObject(newTurnMsg);
					}
					if (newPlayerOut != null) {
						newPlayerOut.writeObject(updateMsg);
						newPlayerOut.writeObject(newTurnMsg);
					}
					if (otherPlayerOut != null && otherPlayerOut != newPlayerOut) {
						otherPlayerOut.writeObject(updateMsg);
						otherPlayerOut.writeObject(newTurnMsg);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				// 👇 call the timer again for the next turn
				int finalNewTurnPlayer = newTurnPlayer;
				game.startTurnTimer(() -> handleTurnTimeout(game, finalNewTurnPlayer, gameId));
			}

			public void saveUser(String username, String password) {
				callback.accept(new Message(MessageType.TEXT, "Received new username and password.", null));

				try(BufferedReader reader = new BufferedReader(new FileReader("users.txt"))){
					String line;
					while ((line = reader.readLine()) != null){
						String[] parts = line.split(",");
						if (parts.length > 0 && parts[0].equals(username)){
							return;
						}
					}
				} catch (IOException e){
					return;
				}

				String data = username + "," + password + ",0,0,0\n";
				callback.accept(new Message(MessageType.TEXT, "Checking file", null));

				callback.accept(new Message(MessageType.TEXT, "Creating new file", null));
				try (FileWriter writer = new FileWriter("users.txt", true)) {
					writer.write(data);
					callback.accept(new Message(MessageType.TEXT, "Successfully appended", null));
					out.writeObject(new Message(MessageType.ADDING_USER, null, null));
				} catch (IOException e) {
					System.err.println("Error saving user: " + e.getMessage());
				}
			}

			public void checkCredentials(String username, String password) throws IOException {
				try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
					String line;
					callback.accept(new Message(MessageType.TEXT, "Checking username and password in the database", null));
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts.length >= 2 && parts[0].equals(username) && parts[1].equals(password)) {
							synchronized (Server.loggedInUsers) {
								if (Server.loggedInUsers.contains(username)) {
									// User already logged in
									out.writeObject(new Message(MessageType.ALREADY_LOGGED_IN, "ALREADY_LOGGED_IN", null));
									return;
								}
								// Mark user as logged in
								Server.loggedInUsers.add(username);
								this.clientUsername = username;
								out.writeObject(new Message(MessageType.LOGIN, String.valueOf(true), null));
							}
							return;
						}
					}
					// If we get here, credentials were invalid
					out.writeObject(new Message(MessageType.LOGIN, "INVALID_CREDENTIALS", null));
				} catch (IOException e) {
					e.printStackTrace();
					out.writeObject(new Message(MessageType.LOGIN, "ERROR", null));
				}
			}

			public boolean isValidUser(String username){
				try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))){
					String line;
					while ((line = reader.readLine()) != null){
						String[] parts = line.split(",");
						if (parts.length > 0 && parts[0].equals(username)){
							return true;
						}
					}
				} catch (IOException e){
					e.printStackTrace();
				}
				return false;
			}

			private void addFriend(String username, String friendUsername) {
				File file = new File("friends.txt");
				Map<String, Set<String>> friendMap = new HashMap<>();
				try {
					if (!file.exists()) {
						file.createNewFile();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// ✅ read current data using ',' separator
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts.length >= 1) {
							String user = parts[0].trim();
							Set<String> friends = new HashSet<>();
							for (int i = 1; i < parts.length; i++) {
								friends.add(parts[i].trim());
							}
							friendMap.put(user, friends);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				friendMap.putIfAbsent(username, new HashSet<>());
				friendMap.get(username).add(friendUsername);

				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
					for (Map.Entry<String, Set<String>> entry : friendMap.entrySet()) {
						String user = entry.getKey();
						String friends = String.join(",", entry.getValue());
						writer.write(user + (friends.isEmpty() ? "" : "," + friends));
						writer.newLine();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			public void updateUserStats(String username, String result) {
				File file = new File("users.txt");
				List<String> lines = new ArrayList<>();

				try {
					// Read the current file into memory
					BufferedReader reader = new BufferedReader(new FileReader(file));
					String line;
					while ((line = reader.readLine()) != null) {
						lines.add(line);
					}
					reader.close();

					// Find the line for the current user and update the stats
					for (int i = 0; i < lines.size(); i++) {
						String[] userData = lines.get(i).split(",");
						if (userData[0].equals(username)) {
							int wins = Integer.parseInt(userData[2]);
							int losses = Integer.parseInt(userData[3]);
							int draws = Integer.parseInt(userData[4]);

							switch (result) {
								case "win":
									wins++;
									break;
								case "loss":
									losses++;
									break;
								case "draw":
									draws++;
									break;
							}

							// Update the user's data
							lines.set(i, String.join(",", username, userData[1],
									String.valueOf(wins), String.valueOf(losses), String.valueOf(draws)));
							break;
						}
					}

					// Write the updated data back to the file
					BufferedWriter writer = new BufferedWriter(new FileWriter(file));
					for (String updatedLine : lines) {
						writer.write(updatedLine);
						writer.newLine();
					}
					writer.close();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			public String getFriendsList(String username) {
				try (BufferedReader reader = new BufferedReader(new FileReader("friends.txt"))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts.length > 1 && parts[0].equals(username)) {
							return String.join(",", Arrays.copyOfRange(parts, 1, parts.length));
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				return ""; // Return empty string if no friends or user not found
			}

			public int makeEasyMove(Connect4Game game) {
				List<Integer> validColumns = new ArrayList<>();
				for (int col = 0; col < 7; col++) {
					if (game.isValidMove(col)) {
						validColumns.add(col);
					}
				}
				if (!validColumns.isEmpty()) {
					return validColumns.get((int)(Math.random() * validColumns.size()));
				}
				return -1; // No valid move
			}

			public int makeHardMove(Connect4Game game) {
				// Simple heuristic-based AI for hard mode
				// First check if we can win immediately
				for (int col = 0; col < 7; col++) {
					if (game.isValidMove(col)) {
						Connect4Game testGame = game.copy();
						testGame.makeMove(col, 2); // Assuming bot is player 2
						if (testGame.checkWinner() && testGame.getWinner() == 2) {
							return col;
						}
					}
				}

				// Then check if opponent can win next move and block
				for (int col = 0; col < 7; col++) {
					if (game.isValidMove(col)) {
						Connect4Game testGame = game.copy();
						testGame.makeMove(col, 1); // Assuming human is player 1
						if (testGame.checkWinner() && testGame.getWinner() == 1) {
							return col;
						}
					}
				}

				// Otherwise make a strategic move (prefer center columns)
				int[] columnPriority = {3, 2, 4, 1, 5, 0, 6}; // Center columns first
				for (int col : columnPriority) {
					if (game.isValidMove(col)) {
						return col;
					}
				}

				// Fallback to random if all else fails
				return makeEasyMove(game);
			}

			public void run(){

				try {
					in = new ObjectInputStream(connection.getInputStream());
					out = new ObjectOutputStream(connection.getOutputStream());
					connection.setTcpNoDelay(true);

					out.writeObject(new Message(MessageType.PLAYER_ID, "client" + clientID, null));
					clientOutputs.put("client" + count, out);
				}
				catch(Exception e) {
					System.out.println("Streams not open");
				}

				updateClients(new Message(MessageType.NEWUSER, "new client on server: client #"+count, null));

				 while(true) {
					    try {
							Message message = (Message) in.readObject();
							switch (message.getType()) {
								case TEXT:
									callback.accept(new Message(MessageType.TEXT, "client #" + count + " sent: " + message.getMessage(), null));
									if (message.getRecipient() == null) {
										updateClients(new Message(MessageType.TEXT, "client #" + count + " said: " + message.getMessage(), null));
									} else {
										sendToRecipient(message.getRecipient(), new Message(MessageType.TEXT, "[Private] client #" + count + ": " + message.getMessage(), null));
									}
									break;

								case CREATE_GAME:
									callback.accept(new Message(MessageType.TEXT, "Client #" + count + " created a game.", null));
									String[] parts = message.getMessage().split(",");
									int turnTime = Integer.parseInt(parts[1]); // Get timer from message

									waitingPlayers.add(count);
									waitingPlayerTimers.put(count, turnTime);
									out.writeObject(new Message(MessageType.TEXT, "Game created. Waiting for opponent...", null));
									break;

								case REQUEST_GAMES:
									// Create a list of waiting games with usernames
									List<String> gameEntries = new ArrayList<>();
									for (Integer playerId : waitingPlayers) {
										// Find the ClientThread for this playerId
										String username = clients.stream()
												.filter(c -> c.count == playerId)
												.findFirst()
												.map(c -> c.clientUsername)
												.orElse("Unknown Player");

										gameEntries.add("Game by " + username);
									}

									String gameList = String.join(",", gameEntries);
									out.writeObject(new Message(MessageType.GAMELIST, gameList, null));
									break;

								case JOIN_GAME:
									callback.accept(new Message(MessageType.TEXT, "Client #" + count + " wants to join a game.", null));
									if (!waitingPlayers.isEmpty()) {
										int hostId = waitingPlayers.poll();
										int turnDuration = waitingPlayerTimers.getOrDefault(hostId, 30); // ✅ get timer
										waitingPlayerTimers.remove(hostId); // ✅ clean up

										Connect4Game game = new Connect4Game();
										game.setTurnDuration(turnDuration);
										int gameId = gameIdCounter++;
										activeGames.put(gameId, game);
										playerToGameId.put(hostId, gameId);
										playerToGameId.put(count, gameId);

										game.setPlayers(hostId, count);

										ObjectOutputStream hostOut = clientOutputs.get("client" + hostId);
										ObjectOutputStream joinerOut = clientOutputs.get("client" + clientID);

										if (hostOut != null) {
											hostOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + clientID, null));
										}
										if (joinerOut != null) {
											joinerOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + hostId, null));
										}

										ObjectOutputStream startingPlayerOut = clientOutputs.get("client" + hostId);
										if (startingPlayerOut != null) {
											startingPlayerOut.writeObject(new Message(MessageType.TURN, "client" + hostId, null));
											//startingPlayerOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
										}
									} else {
										out.writeObject(new Message(MessageType.TEXT, "No games available to join right now.", null));
									}
									break;

								case MOVE:
									System.out.println("Server received move for column: " + message.getMessage());
									callback.accept(new Message(MessageType.TEXT, "Client #" + count + " made a move: " + message.getMessage(), null));

									if (!playerToGameId.containsKey(count)) {
										callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
										break;
									}

									int gameId = playerToGameId.get(count);
									Connect4Game game = activeGames.get(gameId);
									int column = Integer.parseInt(message.getMessage());

									if (game.getCurrentPlayer() != count) {
										callback.accept(new Message(MessageType.TEXT, "It's not your turn.", null));
										break;
									}

									boolean valid = game.makeMove(column, count);
									game.cancelTurnTimer(); // Cancel timer for current turn

									if (!valid) {
										callback.accept(new Message(MessageType.TEXT, "Invalid move. Try again.", null));
										break;
									}

									// Reset timeout count after a valid move
									game.resetTimeouts();

									// Send updated board to player(s)
									String boardString = game.getBoardString();
									Message boardUpdateMessage = new Message(MessageType.BOARD_UPDATE, boardString, null);
									out.writeObject(boardUpdateMessage);

									// Check if this is a bot game (player2 is negative)
									boolean isBotGame = game.getPlayer2() < 0;
									int opponent = isBotGame ? -1 : game.getOtherPlayer(count);
									ObjectOutputStream opponentOut = isBotGame ? null : clientOutputs.get("client" + opponent);
									ObjectOutputStream currentOut = clientOutputs.get("client" + count);

									if (!isBotGame) {
										// Human vs Human - update both players' timers and boards
										if (game.getCurrentPlayer() == count) {
											if (opponentOut != null) {
												opponentOut.writeObject(new Message(MessageType.TIMER_UPDATE,
														String.valueOf(game.getTurnDuration()), null));
											}
										} else {
											if (currentOut != null) {
												currentOut.writeObject(new Message(MessageType.TIMER_UPDATE,
														String.valueOf(game.getTurnDuration()), null));
											}
										}

										// Send board updates to both players
										if (opponentOut != null) opponentOut.writeObject(boardUpdateMessage);
										if (currentOut != null) currentOut.writeObject(boardUpdateMessage);
									} else {
										// Bot game - only update human player
										if (currentOut != null) {
											currentOut.writeObject(boardUpdateMessage);
											// No timer update needed for bot's turn
										}
									}

									// Check for win/draw
									if (game.checkWinner()) {
										String resultMessage = isBotGame ?
												(game.getWinner() == 1 ? "You win!" : "You lose!") : // For bot games
												(game.getCurrentPlayer() == count ? "You win!" : "You lose!"); // For human games

										// Always show game over screen to current player
										out.writeObject(new Message(MessageType.GAME_OVER, resultMessage, null));

										if (!isBotGame) {
											// Only send opponent message in human vs human games
											if (opponentOut != null) {
												opponentOut.writeObject(new Message(MessageType.GAME_OVER,
														game.getCurrentPlayer() == opponent ? "You win!" : "You lose!", null));
											}

											// Only update stats for human vs human games
											if (clientUsername != null) {
												updateUserStats(clientUsername,
														resultMessage.contains("win") ? "win" :
																resultMessage.contains("lose") ? "loss" : "draw");
											}
										}
										break;
									}
									else if (game.checkDraw()) {
										// Always show draw screen to current player
										out.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));

										if (!isBotGame) {
											// Only send to opponent in human vs human games
											if (opponentOut != null) {
												opponentOut.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
											}

											// Only update stats for human vs human games
											if (clientUsername != null) {
												updateUserStats(clientUsername, "draw");
											}
										}
										break;
									}

									// Switch turn
									game.switchTurn();
									int nextPlayer = game.getCurrentPlayer();

									if (isBotGame) {
										// Bot's turn - make the move after a short delay
										int botDifficulty = -game.getPlayer2(); // Get difficulty from negative player ID
										new Thread(() -> {
											try {
												Thread.sleep(1000); // Small delay for better UX

												// Make bot move
												int botColumn;
												if (botDifficulty == 1) { // Easy bot
													botColumn = makeEasyMove(game);
												} else { // Hard bot
													botColumn = makeHardMove(game);
												}

												if (botColumn != -1) { // If valid move
													game.makeMove(botColumn, game.getPlayer2());

													// Send updated board to human player
													String updatedBoard = game.getBoardString();
													out.writeObject(new Message(MessageType.BOARD_UPDATE, updatedBoard, null));

													// Check for game over after bot move
													if (game.checkWinner()) {
														out.writeObject(new Message(MessageType.GAME_OVER,
																game.getWinner() == 1 ? "You win!" : "You lose!", null));
													}
													else if (game.checkDraw()) {
														out.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
													}
													else {
														// Switch back to human player
														game.switchTurn();
														out.writeObject(new Message(MessageType.TURN, "client" + count, null));

														// Start timer only for human's turn
														game.startTurnTimer(() -> handleTurnTimeout(game, count, gameId));
													}
												}
											} catch (Exception e) {
												e.printStackTrace();
											}
										}).start();
									}
									else {
										ObjectOutputStream nextOut = clientOutputs.get("client" + nextPlayer);
										ObjectOutputStream otherOut = clientOutputs.get("client" + game.getOtherPlayer(nextPlayer));

										Message turnMessage = new Message(MessageType.TURN, "client" + nextPlayer, null);
										if (nextOut != null) nextOut.writeObject(turnMessage);
										if (otherOut != null && otherOut != nextOut) otherOut.writeObject(turnMessage);

										// Start timer for next turn
										game.startTurnTimer(() -> {
											System.out.println("⏰ Player " + nextPlayer + " timed out.");

											game.incrementTimeouts();

											if (game.getConsecutiveTimeouts() >= 2) {
												System.out.println("⚠️ Both players timed out. Ending game as a draw.");
												Message drawMsg = new Message(MessageType.GAME_OVER, "Game ended in a draw due to inactivity.", null);

												ObjectOutputStream p1Out = clientOutputs.get("client" + game.getCurrentPlayer());
												ObjectOutputStream p2Out = clientOutputs.get("client" + game.getOtherPlayer(nextPlayer));

												try {
													if (p1Out != null) p1Out.writeObject(drawMsg);
													if (p2Out != null) p2Out.writeObject(drawMsg);
												} catch (IOException e) {
													e.printStackTrace();
												}

												activeGames.remove(gameId);
												return;
											}

											// Switch turn to opponent
											game.switchTurn();
											int newTurnPlayer = game.getCurrentPlayer();

											if (game.getCurrentPlayer() == count) {
												try {
													currentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
												} catch (IOException e) {
													throw new RuntimeException(e);
												}
											} else {
												try {
													opponentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
												} catch (IOException e) {
													throw new RuntimeException(e);
												}
											}

											// Send updated board and new turn
											String updatedBoard = game.getBoardString();
											Message updateMsg = new Message(MessageType.BOARD_UPDATE, updatedBoard, null);
											Message newTurnMsg = new Message(MessageType.TURN, "client" + newTurnPlayer, null);

											ObjectOutputStream timedOutOut = clientOutputs.get("client" + nextPlayer);
											ObjectOutputStream newPlayerOut = clientOutputs.get("client" + newTurnPlayer);
											ObjectOutputStream otherPlayerOut = clientOutputs.get("client" + game.getOtherPlayer(newTurnPlayer));

											try {
												if (timedOutOut != null) {
													timedOutOut.writeObject(new Message(MessageType.TEXT, "You ran out of time. Turn skipped.", null));
													timedOutOut.writeObject(newTurnMsg);
												}
												if (newPlayerOut != null) {
													newPlayerOut.writeObject(updateMsg);
													newPlayerOut.writeObject(newTurnMsg);
												}
												if (otherPlayerOut != null && otherPlayerOut != newPlayerOut) {
													otherPlayerOut.writeObject(updateMsg);
													otherPlayerOut.writeObject(newTurnMsg);
												}
											} catch (Exception ex) {
												ex.printStackTrace();
											}

											// 3. Restart timer for the new player
											int finalNextPlayer = nextPlayer; // because lambdas need final vars
											game.startTurnTimer(() -> handleTurnTimeout(game, finalNextPlayer, gameId));
										});
									}
									break;

								case RETURN_TO_LOBBY:
									// This is where the game is marked as finished manually when the user clicks "Return to Lobby"
									System.out.println("Client #" + count + " requested to return to lobby.");

									if (!playerToGameId.containsKey(count)) {
										callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
										break;
									}

									int returnGameId = playerToGameId.get(count);
									Connect4Game returnGame = activeGames.get(returnGameId);

									if (returnGame == null) {
										callback.accept(new Message(MessageType.TEXT, "Game session expired or not found.", null));
										break;
									}

									// Mark the game as finished
									returnGame.setGameFinished(true);
									callback.accept(new Message(MessageType.TEXT, "Returning to lobby. Game finished.", null));

									// Optionally, notify opponent of the lobby return
									int opponentId = returnGame.getOtherPlayer(count);
									ObjectOutputStream opponentOutt = clientOutputs.get("client" + opponentId);
									if (opponentOutt != null) {
										opponentOutt.writeObject(new Message(MessageType.TEXT, "Your opponent returned to the lobby.", null));
									}

									// Clean up game session (optional, depending on how you want to manage active games)
									activeGames.remove(returnGameId);
									playerToGameId.remove(count);
									playerToGameId.remove(opponentId);

									break;

								case REMATCH:
									System.out.println("Client #" + count + " requested a rematch.");

									if (!playerToGameId.containsKey(count)) {
										callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
										break;
									}

									int lastGameId = playerToGameId.get(count);
									Connect4Game lastGame = activeGames.get(lastGameId);

									System.out.println("Last game ID for client " + count + ": " + lastGameId);
									if (lastGame == null || lastGame.isGameFinished()) {
										callback.accept(new Message(MessageType.TEXT, "Game session expired or finished.", null));
										break;
									}

									int opponentT = lastGame.getOtherPlayer(count);
									rematchRequests.put(count, lastGameId);

									System.out.println("Rematch Requests: " + rematchRequests);

									// Use the same opponentId for both requests
									if (rematchRequests.containsKey(opponentT) && rematchRequests.get(opponentT) == lastGameId) {
										// Both players want a rematch
										System.out.println("Starting rematch between " + count + " and " + opponentT);

										// Create a new game instance for the rematch
										Connect4Game newGame = new Connect4Game();
										int newGameId = gameIdCounter++;

										// Store the new game
										activeGames.put(newGameId, newGame);
										playerToGameId.put(count, newGameId);
										playerToGameId.put(opponentT, newGameId);

										// Clear out the rematch requests
										rematchRequests.remove(count);
										rematchRequests.remove(opponentT);

										// Set the players for the new game
										ObjectOutputStream hostOut = clientOutputs.get("client" + count);
										ObjectOutputStream joinerOut = clientOutputs.get("client" + opponentT);

										// Notify both clients: game started
										if (hostOut != null) {
											hostOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + count, null));
										}
										if (joinerOut != null) {
											joinerOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + opponentT, null));
										}

										// Set players in the new game
										newGame.setPlayers(count, opponentT);

										// Start the game by notifying the first player it's their turn
										int startingPlayer = newGame.getCurrentPlayer();
										ObjectOutputStream startingOut = clientOutputs.get("client" + startingPlayer);
										if (startingOut != null) {
											startingOut.writeObject(new Message(MessageType.TURN, "client" + startingPlayer, null));
										}

									} else {
										// Only one player requested a rematch so far
										callback.accept(new Message(MessageType.TEXT, "Waiting for opponent to accept rematch...", null));
									}
									break;

								case USERNPASS:
									System.out.println("Creating username");
									String[] partss = message.getMessage().split(",");
									callback.accept(new Message(MessageType.TEXT, "Received username: " + partss[0].trim(), null));
									callback.accept(new Message(MessageType.TEXT, "Received password: " + partss[1].trim(), null));
									saveUser(partss[0], partss[1]);
									callback.accept(new Message(MessageType.TEXT, "Saved username and password", null));
									break;

								case CANCEL_GAME_CREATION:
									System.out.println("Client #" + count + " canceled game creation.");

									// Remove from waiting queue if present
									if (waitingPlayers.remove(count)) {
										out.writeObject(new Message(MessageType.TEXT, "Game creation canceled.", null));
									} else {
										out.writeObject(new Message(MessageType.TEXT, "You are not in the game creation queue.", null));
									}

									break;

								case GET_LEADERBOARD:
									System.out.println("Getting leaderboard...");
									List<String[]> userStats = new ArrayList<>();

									try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
										String line;
										while ((line = reader.readLine()) != null) {
											String[] part = line.split(",");
											if (part.length >= 5) {
												String username = part[0];
												int wins = Integer.parseInt(part[2]);
												int losses = Integer.parseInt(part[3]);
												int draws = Integer.parseInt(part[4]); // you originally had 0,0,0 (assumed draws is the 5th field)
												userStats.add(new String[]{username, String.valueOf(wins), String.valueOf(losses), String.valueOf(draws)});
											}
										}

										// Sort by number of wins (descending)
										userStats.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

										// Convert to string format: username,wins,losses,draws
										List<String> formatted = new ArrayList<>();
										for (String[] user : userStats) {
											formatted.add(String.join(",", user));
										}

										String leaderboardMessage = String.join(";", formatted);
										out.writeObject(new Message(MessageType.LEADERBOARD, leaderboardMessage, null));

									} catch (IOException e) {
										System.err.println("Error reading leaderboard: " + e.getMessage());
										callback.accept(new Message(MessageType.TEXT, "LEADERBOARD_ERROR", null));
									}

									break;

								case LOGIN:
									String[] information = message.getMessage().split(",");
									callback.accept(new Message(MessageType.TEXT, "Received username: " + information[0].trim(), null));
									callback.accept(new Message(MessageType.TEXT, "Received password: " + information[1].trim(), null));
									checkCredentials(information[0], information[1]);
									callback.accept(new Message(MessageType.TEXT, "Checking username and password", null));
									out.writeObject(new Message(MessageType.USERNAME, clientUsername, null));
									break;

								case VIEW_ONLINE_USERS:
									String loggedInUsersList = String.join(",", Server.loggedInUsers);
									out.writeObject(new Message(MessageType.ONLINE_USERS, loggedInUsersList, null));
									break;

								case FRIEND_REQUEST_RESPONSE:
									String requester = message.getRecipient();

									// Find the original requester's client
									for (ClientThread client : clients) {
										if (client.clientUsername != null && client.clientUsername.equals(requester)) {
											// Send the response back
											Message responseMsg = new Message(MessageType.FRIEND_REQUEST_RESULT, message.getMessage(),  // "accept" or "reject"
													message.getRecipient() // The original requester
											);
											client.out.writeObject(responseMsg);
											break;
										}
									}
									break;

								case ADD_FRIEND:
									String mainClient = message.getMessage();     // The user sending the request
									String targetFriend = message.getRecipient(); // The target user to be added

									// For logging/debugging
									callback.accept(new Message(MessageType.TEXT, mainClient + " wants to add " + targetFriend, null));

									// Proceed only if the target user exists
									if (isValidUser(targetFriend)) {
										// Add the friend in the database or file system
										addFriend(mainClient, targetFriend);

										// Now notify the target user, if they are online
										for (ClientThread client : clients) {
											if (client.clientUsername != null && client.clientUsername.equals(targetFriend)) {
												Message forwardMsg = new Message(
														MessageType.FRIEND_REQUEST_NOTIFICATION,
														mainClient,     // So target knows who sent the request
														targetFriend    // Recipient of the friend request
												);
												client.out.writeObject(forwardMsg);
												break;
											}
										}
									}
									break;

								case LOG_OUT:
									synchronized (Server.loggedInUsers){
										Server.loggedInUsers.remove(this.clientUsername);
										callback.accept(new Message(MessageType.TEXT, "Logging out: " + this.clientUsername, null));
										out.writeObject(new Message(MessageType.LOG_OUT, null, null));
									}
									break;

								case VIEW_FRIENDS:
									String username = message.getMessage(); // requesting user
									String friends = getFriendsList(username); // "jane,john,tom"
									Message response = new Message(MessageType.FRIENDS_LIST, friends, username);
									out.writeObject(response);
									break;

								case CREATE_BOT_GAME:
									callback.accept(new Message(MessageType.TEXT, "Client #" + count + " created a bot game.", null));
									String[] botParts = message.getMessage().split(",");
									int difficulty = Integer.parseInt(botParts[0]); // 1 = easy, 2 = hard
									int botTurnTime = Integer.parseInt(botParts[1]);

									Connect4Game gameBot = new Connect4Game();
									gameBot.setTurnDuration(botTurnTime);
									int gameBotId = gameIdCounter++;
									activeGames.put(gameBotId, gameBot);
									playerToGameId.put(count, gameBotId);

									// Set players - negative value indicates bot difficulty
									gameBot.setPlayers(count, -difficulty);

									// Notify client game has started against bot
									out.writeObject(new Message(MessageType.GAME_STARTED, "BOT," + botTurnTime, null));
									out.writeObject(new Message(MessageType.TURN, "client" + count, null));
									break;
							}
						}
						catch(Exception e) {
							callback.accept(new Message(MessageType.DISCONNECT,"OOOOPPs...Something wrong with the socket from client: " + count + "....closing down!", null));
							updateClients(new Message(MessageType.TEXT, "Client #"+count+" has left the server!", null));

							// Remove from logged-in users set if this client was logged in
							if (this.clientUsername != null) {
								synchronized (Server.loggedInUsers) {
									Server.loggedInUsers.remove(this.clientUsername);
								}
							}

							clients.remove(this);
							break;
						}
					}
				}//end of run


		}//end of client thread
}



	

	
