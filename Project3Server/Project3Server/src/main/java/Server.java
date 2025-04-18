import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
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

	Map<String, ObjectOutputStream> clientOutputs = new HashMap<>();
	Queue<Integer> waitingPlayers = new LinkedList<>();
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
			int currentGameId = -1;
			int playerNumber;

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
									int turnTime = 30;

									try {
										turnTime = Integer.parseInt(message.getMessage());
									} catch (NumberFormatException ignored) {}

									waitingPlayers.add(count);
									waitingPlayerTimers.put(count, turnTime); // ✅ store timer
									out.writeObject(new Message(MessageType.TEXT, "Game created. Waiting for opponent...", null));
									break;

								case REQUEST_GAMES:
									// Send back dummy game list for now
									String gameList = waitingPlayers.stream()
											.map(id -> "Game by Client #" + id)
											.collect(Collectors.joining(","));
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

									// Send updated board to both players
									String boardString = game.getBoardString();
									Message boardUpdateMessage = new Message(MessageType.BOARD_UPDATE, boardString, null);

									int opponent = game.getOtherPlayer(count);
									ObjectOutputStream opponentOut = clientOutputs.get("client" + opponent);
									ObjectOutputStream currentOut = clientOutputs.get("client" + count);

									if (game.getCurrentPlayer() == count) {
										opponentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
									} else {
										currentOut.writeObject(new Message(MessageType.TIMER_UPDATE, String.valueOf(game.getTurnDuration()), null));
									}

									if (opponentOut != null) {opponentOut.writeObject(boardUpdateMessage);}
									if (currentOut != null) {currentOut.writeObject(boardUpdateMessage);}


									// Check for a win
									if (game.checkWinner()) {
										out.writeObject(new Message(MessageType.GAME_OVER, "You win!", null));
										if (opponentOut != null)
											opponentOut.writeObject(new Message(MessageType.GAME_OVER, "You lose!", null));
										break;
									}
									else if (game.checkDraw()) {
										out.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
										if (opponentOut != null)
											opponentOut.writeObject(new Message(MessageType.GAME_OVER, "Draw!", null));
										break;
									}

									// Switch turn and notify both players
									game.switchTurn();
									int nextPlayer = game.getCurrentPlayer();
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

										// Restart timer for next player
										int finalNextPlayer = nextPlayer; // because lambdas need final vars
										game.startTurnTimer(() -> handleTurnTimeout(game, finalNextPlayer, gameId));
									});

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

								case CANCEL_GAME_CREATION:
									System.out.println("Client #" + count + " canceled game creation.");

									// Remove from waiting queue if present
									if (waitingPlayers.remove(count)) {
										out.writeObject(new Message(MessageType.TEXT, "Game creation canceled.", null));
									} else {
										out.writeObject(new Message(MessageType.TEXT, "You are not in the game creation queue.", null));
									}

									break;
							}
						}
					    catch(Exception e) {
					    	callback.accept(new Message(MessageType.DISCONNECT,"OOOOPPs...Something wrong with the socket from client: " + count + "....closing down!", null));
					    	updateClients(new Message(MessageType.TEXT, "Client #"+count+" has left the server!", null));
					    	clients.remove(this);
					    	break;
					    }
					}
				}//end of run


		}//end of client thread
}



	

	
