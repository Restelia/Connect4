/*
Name: Ali Azam, Andy Tran
NetID: aazam7, atran59
Date: 4/26/2025
Class: CS342
Description: This project is a JavaFX-based networked Connect 4 game with user authentication, online matchmaking, live chat, friend requests, and a dynamic leaderboard system.
 */

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

	Map<String, ObjectOutputStream> clientOutputs = new HashMap<>(); // Map clients to their output streams
	Queue<Integer> waitingPlayers = new LinkedList<>(); // Created Game and waiting for someone to join
	Map<Integer, Connect4Game> activeGames = new HashMap<>(); // Track active games by gameId
	Map<Integer, Integer> rematchRequests = new HashMap<>(); // Track which players have requested a match
	Map<Integer, Integer> playerToGameId = new HashMap<>(); // Map player IDs to the game IDs
	Map<Integer, Integer> waitingPlayerTimers = new HashMap<>(); // Map waiting player IDs
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
			}
				catch(Exception e) {
					callback.accept(new Message(MessageType.DISCONNECT,"Server socket did not launch", null));
				}
			}
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
						// Check if message is for everyone or specific client
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
					// Find client with the matching clientID
					if (t.clientID.equals(recipient)) {
						try {
							// Send the message to the matched client
							t.out.writeObject(message);
							break;
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}

			public void handleTurnTimeout(Connect4Game game, int nextPlayer, int gameId) {
				System.out.println("Player " + nextPlayer + " timed out.");

				// Increment the number of consecutive timeouts
				game.incrementTimeouts();

				// If both players have timed out consecutively, end the game as a draw
				if (game.getConsecutiveTimeouts() >= 2) {
					System.out.println("Both players timed out. Ending game as a draw.");

					// Create a message to announce the draw
					Message drawMsg = new Message(MessageType.GAME_OVER, "Game ended in a draw due to inactivity.", null);

					// Get output streams for both players
					ObjectOutputStream p1Out = clientOutputs.get("client" + game.getCurrentPlayer());
					ObjectOutputStream p2Out = clientOutputs.get("client" + game.getOtherPlayer(game.getCurrentPlayer()));

					try {
						if (p1Out != null) p1Out.writeObject(drawMsg);
						if (p2Out != null) p2Out.writeObject(drawMsg);
					} catch (IOException e) {
						e.printStackTrace();
					}

					// Remove the game from active games
					activeGames.remove(gameId);
					return;
				}

				// Otherwise, switch the turn to the other player
				game.switchTurn();
				int newTurnPlayer = game.getCurrentPlayer();

				// Create messages to update the board and notify whose turn it is
				String updatedBoard = game.getBoardString();
				Message updateMsg = new Message(MessageType.BOARD_UPDATE, updatedBoard, null);
				Message newTurnMsg = new Message(MessageType.TURN, "client" + newTurnPlayer, null);

				// Get output streams for the players
				ObjectOutputStream timedOutOut = clientOutputs.get("client" + nextPlayer);
				ObjectOutputStream newPlayerOut = clientOutputs.get("client" + newTurnPlayer);
				ObjectOutputStream otherPlayerOut = clientOutputs.get("client" + game.getOtherPlayer(newTurnPlayer));

				try {
					// Inform the timed-out player that they missed their turn
					if (timedOutOut != null) {
						timedOutOut.writeObject(new Message(MessageType.TEXT, "You ran out of time. Turn skipped.", null));
						timedOutOut.writeObject(newTurnMsg);
					}
					// Send the updated board and new turn to the new current player
					if (newPlayerOut != null) {
						newPlayerOut.writeObject(updateMsg);
						newPlayerOut.writeObject(newTurnMsg);
					}
					// Also send board and turn info to the opponent if needed
					if (otherPlayerOut != null && otherPlayerOut != newPlayerOut) {
						otherPlayerOut.writeObject(updateMsg);
						otherPlayerOut.writeObject(newTurnMsg);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				// Start the turn timer for the next player
				int finalNewTurnPlayer = newTurnPlayer;
				game.startTurnTimer(() -> handleTurnTimeout(game, finalNewTurnPlayer, gameId));
			}

			public void saveUser(String username, String password) {
				// Notify that a new username and password were received
				callback.accept(new Message(MessageType.TEXT, "Received new username and password.", null));

				// Check if the username already exists in "users.txt"
				try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
					String line;
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts.length > 0 && parts[0].equals(username)) {
							// Username already exists, do not add it again
							return;
						}
					}
				} catch (IOException e) {
					// If file doesn't exist or another I/O error happens, just proceed to create a new file
					return;
				}

				// Prepare new user data to be saved (format: username,password,0,0,0)
				String data = username + "," + password + ",0,0,0\n";

				// Notify that we're checking the file
				callback.accept(new Message(MessageType.TEXT, "Checking file", null));

				// Notify that we're creating or appending to the file
				callback.accept(new Message(MessageType.TEXT, "Creating new file", null));

				// Write the new user information to "users.txt"
				try (FileWriter writer = new FileWriter("users.txt", true)) {
					writer.write(data);
					// Notify that writing to the file was successful
					callback.accept(new Message(MessageType.TEXT, "Successfully appended", null));

					// Notify client that the user was successfully added
					out.writeObject(new Message(MessageType.ADDING_USER, null, null));
				} catch (IOException e) {
					// Print error message if saving user fails
					System.err.println("Error saving user: " + e.getMessage());
				}
			}


			public void checkCredentials(String username, String password) throws IOException {
				// Open the "users.txt" file for reading
				try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
					String line;

					// Notify that username and password checking has started
					callback.accept(new Message(MessageType.TEXT, "Checking username and password in the database", null));

					// Read through each line in the file
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");

						// Check if line has at least username and password and matches the input
						if (parts.length >= 2 && parts[0].equals(username) && parts[1].equals(password)) {

							// Synchronize access to the loggedInUsers set
							synchronized (Server.loggedInUsers) {

								// Check if user is already logged in
								if (Server.loggedInUsers.contains(username)) {
									// Inform client that the user is already logged in
									out.writeObject(new Message(MessageType.ALREADY_LOGGED_IN, "ALREADY_LOGGED_IN", null));
									return;
								}

								// Mark user as logged in
								Server.loggedInUsers.add(username);
								this.clientUsername = username;

								// Inform client that login was successful
								out.writeObject(new Message(MessageType.LOGIN, String.valueOf(true), null));
							}
							return;
						}
					}

					// If credentials didn't match any record, notify client of invalid credentials
					out.writeObject(new Message(MessageType.LOGIN, "INVALID_CREDENTIALS", null));
				} catch (IOException e) {
					// Handle any file read errors and notify client of an error
					e.printStackTrace();
					out.writeObject(new Message(MessageType.LOGIN, "ERROR", null));
				}
			}

			public boolean isValidUser(String username) {
				// Try to open and read the "users.txt" file
				try (BufferedReader reader = new BufferedReader(new FileReader("users.txt"))) {
					String line;

					// Read through each line of the file
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");

						// Check if the line contains at least a username and if it matches the input
						if (parts.length > 0 && parts[0].equals(username)) {
							return true; // Username found in file
						}
					}
				} catch (IOException e) {
					// Print stack trace if there's an error reading the file
					e.printStackTrace();
				}

				// Username not found or an error occurred
				return false;
			}

			public void addFriend(String username, String friendUsername) {
				// Create or open the "friends.txt" file
				File file = new File("friends.txt");
				Map<String, Set<String>> friendMap = new HashMap<>();

				// Ensure the file exists, or create a new one if it doesn't
				try {
					if (!file.exists()) {
						file.createNewFile();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Read the current friend data from the file
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					String line;

					// Parse each line and map each user to their list of friends
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");
						if (parts.length >= 1) {
							String user = parts[0].trim();
							Set<String> friends = new HashSet<>();

							// Add each friend to the user's set of friends
							for (int i = 1; i < parts.length; i++) {
								friends.add(parts[i].trim());
							}
							// Store the user and their friends in the map
							friendMap.put(user, friends);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// If the username doesn't exist in the map, create an empty set for them
				friendMap.putIfAbsent(username, new HashSet<>());

				// Add the friend to the user's list of friends
				friendMap.get(username).add(friendUsername);

				// Write the updated data back to the file
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
					// Iterate through each user and their friends, writing them to the file
					for (Map.Entry<String, Set<String>> entry : friendMap.entrySet()) {
						String user = entry.getKey();
						String friends = String.join(",", entry.getValue());

						// Write the user and their friends, separated by commas
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
				// Open the "friends.txt" file for reading
				try (BufferedReader reader = new BufferedReader(new FileReader("friends.txt"))) {
					String line;

					// Read each line in the file
					while ((line = reader.readLine()) != null) {
						String[] parts = line.split(",");

						// If the first part matches the username and there are friends listed, return the list of friends
						if (parts.length > 1 && parts[0].equals(username)) {
							return String.join(",", Arrays.copyOfRange(parts, 1, parts.length)); // Return friends as a comma-separated string
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				// If the username was not found or has no friends, return an empty string
				return "";
			}

			public int makeEasyMove(Connect4Game game) {
				List<Integer> validColumns = new ArrayList<>();

				// Check all columns (0 to 6) to see which are valid moves
				for (int col = 0; col < 7; col++) {
					if (game.isValidMove(col)) { // Check if the move is valid in the current column
						validColumns.add(col); // Add valid columns to the list
					}
				}

				// If there are valid moves, pick a random one from the list
				if (!validColumns.isEmpty()) {
					return validColumns.get((int)(Math.random() * validColumns.size())); // Randomly select a valid move
				}

				// Return -1 if there are no valid moves
				return -1;
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
						// Read the incoming message from the client
						Message message = (Message) in.readObject();

						// Handle the message based on its type
						switch (message.getType()) {
							case TEXT:
								// Handle regular text message
								callback.accept(new Message(MessageType.TEXT, clientUsername + " sent: " + message.getMessage(), null));

								// If no recipient is specified, broadcast the message to all clients
								if (message.getRecipient() == null) {
									updateClients(new Message(MessageType.TEXT, clientUsername + " said: " + message.getMessage(), null));
								} else {
									// If there's a recipient, send a private message to the specified recipient
									sendToRecipient(message.getRecipient(), new Message(MessageType.TEXT, "[Private] client #" + count + ": " + message.getMessage(), null));
								}
								break;

							case CREATE_GAME:
								// Handle the creation of a new game
								callback.accept(new Message(MessageType.TEXT, "Client #" + count + " created a game.", null));

								// Extract game details (such as the timer duration) from the message
								String[] parts = message.getMessage().split(",");
								int turnTime = Integer.parseInt(parts[1]); // Get the turn time from the message

								// Add this client to the waiting list for a game
								waitingPlayers.add(count);
								waitingPlayerTimers.put(count, turnTime); // Store the turn time for this player

								// Notify the client that the game was created and they're waiting for an opponent
								out.writeObject(new Message(MessageType.TEXT, "Game created. Waiting for opponent...", null));
								break;

							case REQUEST_GAMES:
								// Handle a request for the list of available games
								List<String> gameEntries = new ArrayList<>();

								// Loop through all the waiting players and generate game entries with their usernames
								for (Integer playerId : waitingPlayers) {
									// Find the ClientThread for this playerId using their count
									String username = clients.stream()
											.filter(c -> c.count == playerId)
											.findFirst()
											.map(c -> c.clientUsername)
											.orElse("Unknown Player");

									// Add the game entry to the list
									gameEntries.add("Game by " + username);
								}

								// Join all the game entries into a single comma-separated string
								String gameList = String.join(",", gameEntries);

								// Send the list of available games back to the client
								out.writeObject(new Message(MessageType.GAMELIST, gameList, null));
								break;

							case JOIN_GAME:
								// Notify callback that the client wants to join a game
								callback.accept(new Message(MessageType.TEXT, "Client #" + count + " wants to join a game.", null));

								// Check if there are any waiting players (i.e., available games to join)
								if (!waitingPlayers.isEmpty()) {
									// Get the host (first player in the waiting list)
									int hostId = waitingPlayers.poll(); // Remove the host from the waiting queue
									int turnDuration = waitingPlayerTimers.getOrDefault(hostId, 30); // Get the host's turn duration, default to 30 if not set
									waitingPlayerTimers.remove(hostId); // Clean up the host's entry from the waitingPlayerTimers map

									// Create a new game and assign the turn duration
									Connect4Game game = new Connect4Game();
									game.setTurnDuration(turnDuration);

									// Generate a new game ID
									int gameId = gameIdCounter++;

									// Store the game in active games and map the players to the game
									activeGames.put(gameId, game);
									playerToGameId.put(hostId, gameId);
									playerToGameId.put(count, gameId);

									// Assign players (host and joiner) to the game
									game.setPlayers(hostId, count);

									// Get output streams for the host and the joiner
									ObjectOutputStream hostOut = clientOutputs.get("client" + hostId);
									ObjectOutputStream joinerOut = clientOutputs.get("client" + count);

									// Notify the host and the joiner that the game has started
									if (hostOut != null) {
										hostOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + count, null));
									}
									if (joinerOut != null) {
										joinerOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + hostId, null));
									}

									// Notify the host that it's their turn
									ObjectOutputStream startingPlayerOut = clientOutputs.get("client" + hostId);
									if (startingPlayerOut != null) {
										startingPlayerOut.writeObject(new Message(MessageType.TURN, "client" + hostId, null));
										// Optionally, the timer update could be sent here (commented out):
										// startingPlayerOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
									}
								} else {
									// Inform the client if there are no games available to join
									out.writeObject(new Message(MessageType.TEXT, "No games available to join right now.", null));
								}
								break;

							case MOVE:
								// Log the move received from the client
								System.out.println("Server received move for column: " + message.getMessage());

								// Notify all players about the move made by the client
								callback.accept(new Message(MessageType.TEXT, "Client #" + count + " made a move: " + message.getMessage(), null));

								// Check if the player is in a game
								if (!playerToGameId.containsKey(count)) {
									callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
									break;
								}

								// Retrieve the game ID and game object for the player
								int gameId = playerToGameId.get(count);
								Connect4Game game = activeGames.get(gameId);
								int column = Integer.parseInt(message.getMessage()); // Parse the column number

								// Ensure it's the player's turn
								if (game.getCurrentPlayer() != count) {
									callback.accept(new Message(MessageType.TEXT, "It's not your turn.", null));
									break;
								}

								// Attempt to make the move in the game
								boolean valid = game.makeMove(column, count);
								game.cancelTurnTimer(); // Cancel the timer for the current turn

								// If the move is invalid, inform the player
								if (!valid) {
									callback.accept(new Message(MessageType.TEXT, "Invalid move. Try again.", null));
									break;
								}

								// Reset the timeout count after a valid move
								game.resetTimeouts();

								// Send the updated board state to the player(s)
								String boardString = game.getBoardString();
								Message boardUpdateMessage = new Message(MessageType.BOARD_UPDATE, boardString, null);
								out.writeObject(boardUpdateMessage);

								// Determine if the game is against a bot (player2 ID is negative)
								boolean isBotGame = game.getPlayer2() < 0;
								int opponent = isBotGame ? -1 : game.getOtherPlayer(count);
								ObjectOutputStream opponentOut = isBotGame ? null : clientOutputs.get("client" + opponent);
								ObjectOutputStream currentOut = clientOutputs.get("client" + count);

								if (!isBotGame) {
									// Human vs Human - update both players' timers and send board updates
									if (game.getCurrentPlayer() == count) {
										if (opponentOut != null) {
											opponentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
										}
									} else {
										if (currentOut != null) {
											currentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
										}
									}

									// Send the updated board to both players
									if (opponentOut != null) opponentOut.writeObject(boardUpdateMessage);
									if (currentOut != null) currentOut.writeObject(boardUpdateMessage);
								} else {
									// Bot game - only update the human player
									if (currentOut != null) {
										currentOut.writeObject(boardUpdateMessage);
										// No need to update the timer for the bot's turn
									}
								}

								// Check if there's a winner or draw
								if (game.checkWinner()) {
									String resultMessage = isBotGame ?
											(game.getWinner() == 1 ? "You win!" : "You lose!") : // Bot game result
											(game.getCurrentPlayer() == count ? "You win!" : "You lose!"); // Human vs Human result

									// Always send game over message to the current player
									out.writeObject(new Message(MessageType.GAME_OVER, resultMessage, null));

									if (!isBotGame) {
										// Send game over message to opponent in Human vs Human games
										if (opponentOut != null) {
											opponentOut.writeObject(new Message(MessageType.GAME_OVER,
													game.getCurrentPlayer() == opponent ? "You win!" : "You lose!", null));
										}

										// Update stats for human vs human games
										if (clientUsername != null) {
											updateUserStats(clientUsername,
													resultMessage.contains("win") ? "win" :
															resultMessage.contains("lose") ? "loss" : "draw");
										}
									}
									break;
								} else if (game.checkDraw()) {
									// If the game is a draw, send draw message to both players
									out.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));

									if (!isBotGame) {
										// Send draw message to opponent in Human vs Human games
										if (opponentOut != null) {
											opponentOut.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
										}

										// Update stats for human vs human games
										if (clientUsername != null) {
											updateUserStats(clientUsername, "draw");
										}
									}
									break;
								}

								// Switch the turn to the next player
								game.switchTurn();
								int nextPlayer = game.getCurrentPlayer();

								if (isBotGame) {
									// If it's a bot game, make the bot move after a small delay
									int botDifficulty = -game.getPlayer2(); // Get difficulty level from negative player ID
									new Thread(() -> {
										try {
											Thread.sleep(1000); // Small delay for a better UX

											// Make the bot's move based on its difficulty level
											int botColumn;
											if (botDifficulty == 1) { // Easy bot
												botColumn = makeEasyMove(game);
											} else { // Hard bot
												botColumn = makeHardMove(game);
											}

											if (botColumn != -1) { // If the bot makes a valid move
												game.makeMove(botColumn, game.getPlayer2());

												// Send updated board to the human player
												String updatedBoard = game.getBoardString();
												out.writeObject(new Message(MessageType.BOARD_UPDATE, updatedBoard, null));

												// Check for game over after bot's move
												if (game.checkWinner()) {
													out.writeObject(new Message(MessageType.GAME_OVER,
															game.getWinner() == 1 ? "You win!" : "You lose!", null));
												}
												else if (game.checkDraw()) {
													out.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
												}
												else {
													// Switch back to human player's turn
													game.switchTurn();
													out.writeObject(new Message(MessageType.TURN, "client" + count, null));

													// Start timer only for human player's turn
													game.startTurnTimer(() -> handleTurnTimeout(game, count, gameId));
												}
											}
										} catch (Exception e) {
											e.printStackTrace();
										}
									}).start();
								} else {
									// For human vs human, send turn update to both players
									ObjectOutputStream nextOut = clientOutputs.get("client" + nextPlayer);
									ObjectOutputStream otherOut = clientOutputs.get("client" + game.getOtherPlayer(nextPlayer));

									Message turnMessage = new Message(MessageType.TURN, "client" + nextPlayer, null);
									if (nextOut != null) nextOut.writeObject(turnMessage);
									if (otherOut != null && otherOut != nextOut) otherOut.writeObject(turnMessage);

									// Start the turn timer for the next player
									game.startTurnTimer(() -> {
										System.out.println("⏰ Player " + nextPlayer + " timed out.");

										// Increment timeout count and check if both players timed out
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

										// Switch turn to the opponent
										game.switchTurn();
										int newTurnPlayer = game.getCurrentPlayer();

										// Update the timers for both players
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

										// Send updated board and new turn message
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

										// Restart the timer for the new player
										int finalNextPlayer = nextPlayer; // because lambdas need final vars
										game.startTurnTimer(() -> handleTurnTimeout(game, finalNextPlayer, gameId));
									});
								}
								break;


							case RETURN_TO_LOBBY:
								// This is where the game is marked as finished manually when the user clicks "Return to Lobby"
								System.out.println("Client #" + count + " requested to return to lobby.");

								// Check if the player is currently in a game
								if (!playerToGameId.containsKey(count)) {
									// If the player is not in a game, send a message indicating they aren't in one
									callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
									break; // Exit the case since the player isn't in a game
								}

								// Retrieve the game ID the player is part of
								int returnGameId = playerToGameId.get(count);
								Connect4Game returnGame = activeGames.get(returnGameId);

								// Check if the game exists, if not send an error message
								if (returnGame == null) {
									callback.accept(new Message(MessageType.TEXT, "Game session expired or not found.", null));
									break; // Exit the case as the game isn't found
								}

								// Mark the game as finished (so it won't continue)
								returnGame.setGameFinished(true);
								callback.accept(new Message(MessageType.TEXT, "Returning to lobby. Game finished.", null));

								// Optionally, notify the opponent that the current player has returned to the lobby
								int opponentId = returnGame.getOtherPlayer(count);
								ObjectOutputStream opponentOutt = clientOutputs.get("client" + opponentId);
								if (opponentOutt != null) {
									opponentOutt.writeObject(new Message(MessageType.TEXT, "Your opponent returned to the lobby.", null));
								}

								// Clean up the game session (remove from active games and player-to-game mapping)
								activeGames.remove(returnGameId);  // Remove the game from active games
								playerToGameId.remove(count);      // Remove the player from the player-to-game mapping
								playerToGameId.remove(opponentId); // Remove the opponent from the player-to-game mapping

								break;  // Exit the case after handling the return to lobby action

							case REMATCH:
								System.out.println("Client #" + count + " requested a rematch.");

								// Check if the player is currently in a game
								if (!playerToGameId.containsKey(count)) {
									// If the player isn't in a game, send a message indicating they aren't in one
									callback.accept(new Message(MessageType.TEXT, "You're not in a game.", null));
									break; // Exit the case since the player isn't in a game
								}

								// Retrieve the last game ID and the game object
								int lastGameId = playerToGameId.get(count);
								Connect4Game lastGame = activeGames.get(lastGameId);

								System.out.println("Last game ID for client " + count + ": " + lastGameId);

								// Check if the game has ended or doesn't exist anymore
								if (lastGame == null || lastGame.isGameFinished()) {
									callback.accept(new Message(MessageType.TEXT, "Game session expired or finished.", null));
									break; // Exit the case since the game is finished or expired
								}

								// Get the opponent's ID
								int opponentT = lastGame.getOtherPlayer(count);

								if (opponentT < 0) {
									// The opponent is a bot (indicated by a negative ID)
									System.out.println("Client " + count + " requested a bot rematch.");

									// Create a new game for the bot
									Connect4Game newBotGame = new Connect4Game();
									int newBotGameId = gameIdCounter++; // Increment the game ID counter

									int difficulty = -opponentT; // The difficulty level is the negative of the opponent's ID
									newBotGame.setPlayers(count, opponentT); // Set the player and bot
									newBotGame.setTurnDuration(lastGame.getTurnDuration()); // Use the same turn duration as the previous game

									activeGames.put(newBotGameId, newBotGame); // Add the new game to the active games map
									playerToGameId.put(count, newBotGameId); // Update the player's game ID

									// Send the game start message to the player
									ObjectOutputStream playerOut = clientOutputs.get("client" + count);
									if (playerOut != null) {
										playerOut.writeObject(new Message(MessageType.GAME_STARTED, "BOT," + newBotGame.getTurnDuration(), null));
										playerOut.writeObject(new Message(MessageType.TURN, "client" + count, null)); // Start the player's turn
									}
									break; // End the case for bot rematch
								}

								// If the opponent is human, handle the rematch request
								rematchRequests.put(count, lastGameId); // Store the rematch request

								System.out.println("Rematch Requests: " + rematchRequests);

								// Check if the opponent also wants a rematch
								if (rematchRequests.containsKey(opponentT) && rematchRequests.get(opponentT) == lastGameId) {
									// Both players requested a rematch
									System.out.println("Starting rematch between " + count + " and " + opponentT);

									// Create a new game for the rematch
									Connect4Game newGame = new Connect4Game();
									int newGameId = gameIdCounter++; // Increment the game ID counter

									activeGames.put(newGameId, newGame); // Add the new game to active games
									playerToGameId.put(count, newGameId); // Assign the new game ID to both players
									playerToGameId.put(opponentT, newGameId);

									rematchRequests.remove(count); // Clear the rematch requests
									rematchRequests.remove(opponentT);

									// Notify both players that the rematch is starting
									ObjectOutputStream hostOut = clientOutputs.get("client" + count);
									ObjectOutputStream joinerOut = clientOutputs.get("client" + opponentT);

									if (hostOut != null) {
										hostOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + count, null)); // Host starts the game
									}
									if (joinerOut != null) {
										joinerOut.writeObject(new Message(MessageType.GAME_STARTED, "client" + opponentT, null)); // Joiner starts the game
									}

									newGame.setPlayers(count, opponentT); // Set the players for the new game

									// Determine who starts the game and notify them
									int startingPlayer = newGame.getCurrentPlayer();
									ObjectOutputStream startingOut = clientOutputs.get("client" + startingPlayer);
									if (startingOut != null) {
										startingOut.writeObject(new Message(MessageType.TURN, "client" + startingPlayer, null)); // Start the turn for the starting player
									}

								} else {
									// Only one player has requested a rematch so far
									callback.accept(new Message(MessageType.TEXT, "Waiting for opponent to accept rematch...", null));
								}
								break; // Exit the case after handling the rematch

							case USERNPASS:
								System.out.println("Creating username");

								// Split the message into parts (assuming the message format is "username,password")
								String[] partss = message.getMessage().split(",");

								// Send feedback about the received username and password to the client
								callback.accept(new Message(MessageType.TEXT, "Received username: " + partss[0].trim(), null));
								callback.accept(new Message(MessageType.TEXT, "Received password: " + partss[1].trim(), null));

								// Call the saveUser function to store the username and password
								saveUser(partss[0], partss[1]);

								// Send a confirmation message to the client that the username and password have been saved
								callback.accept(new Message(MessageType.TEXT, "Saved username and password", null));
								break;

							case CANCEL_GAME_CREATION:
								System.out.println("Client #" + count + " canceled game creation.");

								// Remove from the waiting queue if the client is currently in it
								if (waitingPlayers.remove(count)) {
									// Notify the client that the game creation was canceled
									out.writeObject(new Message(MessageType.TEXT, "Game creation canceled.", null));
								} else {
									// Notify the client that they are not in the game creation queue
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
											// Extract username, wins, losses, and draws (assumes the correct data order)
											String username = part[0];
											int wins = Integer.parseInt(part[2]);
											int losses = Integer.parseInt(part[3]);
											int draws = Integer.parseInt(part[4]); // Assumes draws is the 5th field
											// Add the stats to the userStats list
											userStats.add(new String[]{username, String.valueOf(wins), String.valueOf(losses), String.valueOf(draws)});
										}
									}

									// Sort the user stats by number of wins (descending)
									userStats.sort((a, b) -> Integer.compare(Integer.parseInt(b[1]), Integer.parseInt(a[1])));

									// Convert the user stats into a comma-separated format for the leaderboard
									List<String> formatted = new ArrayList<>();
									for (String[] user : userStats) {
										formatted.add(String.join(",", user));
									}

									// Join all the formatted strings with semicolons and send the leaderboard message to the client
									String leaderboardMessage = String.join(";", formatted);
									out.writeObject(new Message(MessageType.LEADERBOARD, leaderboardMessage, null));

								} catch (IOException e) {
									// Handle errors in reading the file
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

							case GET_CHAT_RECIPIENTS:
								String recipientList = String.join(",", Server.loggedInUsers);
								out.writeObject(new Message(MessageType.CHAT_RECIPIENTS, recipientList, null));
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



	

	
