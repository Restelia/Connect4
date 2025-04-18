import java.util.Timer;
import java.util.TimerTask;

public class Connect4Game {
    private int player1;
    private int player2;
    private int currentPlayer;
    private int turnDurationSeconds = 30; // default
    private Timer turnTimer;
    private Runnable onTurnTimeout;
    private boolean gameFinished = false;
    private int consecutiveTimeouts = 0;

    public void setTurnDuration(int seconds) {
        this.turnDurationSeconds = seconds;
    }

    public void resetTimeouts() {
        consecutiveTimeouts = 0;
    }

    public void incrementTimeouts() {
        consecutiveTimeouts++;
    }

    public int getConsecutiveTimeouts() {
        return consecutiveTimeouts;
    }

    public void startTurnTimer(Runnable timeoutCallback) {
        cancelTurnTimer(); // Clear any previous timer
        this.onTurnTimeout = timeoutCallback;

        turnTimer = new Timer();

        final int[] remaining = {turnDurationSeconds};

        // Logging countdown every second
        turnTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (remaining[0] <= 0) {
                    System.out.println("[Timer] Time's up!");
                    cancel();
                    if (onTurnTimeout != null) {
                        onTurnTimeout.run();
                    }
                } else {
                    System.out.println("[Timer] " + remaining[0] + "s remaining...");
                    remaining[0]--;
                }
            }
        }, 0, 1000);
    }

    public void cancelTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer.purge();
            turnTimer = null;
        }
    }


    public void setPlayers(int p1, int p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.currentPlayer = p1; // player1 starts
    }

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private int[][] board = new int[ROWS][COLS];  // 0 = empty, 1 = player 1, 2 = player 2
    private boolean gameInProgress = true;

    public Connect4Game() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;  // Initialize the board as empty
            }
        }
    }

    public boolean makeMove(int col, int playerId) {
        if (col < 0 || col >= 7) return false;
        for (int row = 5; row >= 0; row--) {
            if (board[row][col] == 0) {
                board[row][col] = (playerId == player1) ? 1 : 2;
                return true;
            }
        }
        return false; // column full
    }

    public boolean checkWinner() {
        // Check for a winner
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (board[row][col] != 0 && checkDirection(row, col)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void reset() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;
            }
        }
        this.gameInProgress = true;
        this.currentPlayer = player1; // Or randomize if you want
    }

    private boolean checkDirection(int row, int col) {
        int player = board[row][col];

        // Check horizontal
        for (int i = 0; i < 4; i++) {
            if (col + i < COLS && board[row][col + i] == player) {
                if (i == 3) return true;
            } else {
                break;
            }
        }

        // Check vertical
        for (int i = 0; i < 4; i++) {
            if (row + i < ROWS && board[row + i][col] == player) {
                if (i == 3) return true;
            } else {
                break;
            }
        }

        // Check diagonal \
        for (int i = 0; i < 4; i++) {
            if (row + i < ROWS && col + i < COLS && board[row + i][col + i] == player) {
                if (i == 3) return true;
            } else {
                break;
            }
        }

        // Check diagonal /
        for (int i = 0; i < 4; i++) {
            if (row - i >= 0 && col + i < COLS && board[row - i][col + i] == player) {
                if (i == 3) return true;
            } else {
                break;
            }
        }

        return false;
    }

    public String getBoardString() {
        StringBuilder boardString = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                boardString.append(board[row][col]);
            }
            if (row < ROWS - 1) {
                boardString.append(","); // Separate rows with commas
            }
        }
        return boardString.toString();
    }

    public void switchTurn() {
        currentPlayer = (currentPlayer == player1) ? player2 : player1;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getOtherPlayer(int playerId) {
        return (playerId == player1) ? player2 : player1;
    }

    public void setGameFinished(boolean finished) {
        this.gameFinished = finished;
    }

    public boolean isGameFinished() {
        return gameFinished;
    }
}
