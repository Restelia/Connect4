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
	Map<Integer, Integer> playerToGameId = new HashMap<>();
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
									waitingPlayers.add(count);
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

										Connect4Game game = new Connect4Game();
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
										}
									} else {
										out.writeObject(new Message(MessageType.TEXT, "No games available to join right now.", null));
									}
									break;

								case MOVE:
									System.out.println("Server received move for column: " + message.getMessage());
									callback.accept(new Message(MessageType.TEXT, "Client #" + count + " made a move: " + message.getMessage(), null));
									if (!playerToGameId.containsKey(count)) {
										out.writeObject(new Message(MessageType.TEXT, "You're not in a game.", null));
										break;
									}

									int gameId = playerToGameId.get(count);
									Connect4Game game = activeGames.get(gameId);
									int column = Integer.parseInt(message.getMessage());

									if (game.getCurrentPlayer() != count) {
										out.writeObject(new Message(MessageType.TEXT, "It's not your turn.", null));
										break;
									}

									boolean valid = game.makeMove(column, count);
									if (!valid) {
										out.writeObject(new Message(MessageType.TEXT, "Invalid move. Try again.", null));
										break;
									}

									String boardString = game.getBoardString();
									// Send the BOARD_UPDATE message to both players
									Message boardUpdateMessage = new Message(MessageType.BOARD_UPDATE, boardString, null);

									int opponent = game.getOtherPlayer(count);
									ObjectOutputStream opponentOut = clientOutputs.get("client" + opponent);
									ObjectOutputStream currentOut = clientOutputs.get("client" + count);
									if (opponentOut != null) {opponentOut.writeObject(boardUpdateMessage);}
									if (currentOut != null) {currentOut.writeObject(boardUpdateMessage);}

									if (game.checkWinner()) {
										out.writeObject(new Message(MessageType.GAME_OVER, "You win!", null));
										if (opponentOut != null)
											opponentOut.writeObject(new Message(MessageType.GAME_OVER, "You lose!", null));
										activeGames.remove(gameId);
									} else {
										game.switchTurn();
										int nextPlayer = game.getCurrentPlayer();

										ObjectOutputStream nextOut = clientOutputs.get("client" + nextPlayer);
										if (nextOut != null) {
											nextOut.writeObject(new Message(MessageType.TURN, "client" + nextPlayer, null));
										}
									}
									break;

								default:
									callback.accept(new Message(MessageType.TEXT, "Unhandled message type from client #" + count, null));
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



	

	
