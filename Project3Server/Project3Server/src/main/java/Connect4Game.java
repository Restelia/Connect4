import java.util.Timer;
import java.util.TimerTask;

public class Connect4Game {
    private int player1;
    private int player2;
    private int currentPlayer;
    private int initialStartingPlayer; // Used to alternate starting player if desired
    private int turnDurationSeconds = 30;
    private Timer turnTimer;
    private Runnable onTurnTimeout;
    private boolean gameFinished = false;
    private int consecutiveTimeouts = 0;

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private int[][] board = new int[ROWS][COLS];
    private boolean gameInProgress = true;

    public Connect4Game() {
        clearBoard();
    }

    public void setTurnDuration(int seconds) {
        this.turnDurationSeconds = seconds;
    }

    public void setPlayers(int p1, int p2) {
        this.player1 = p1;
        this.player2 = p2;
        this.initialStartingPlayer = p1;
        this.currentPlayer = p1;
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
        cancelTurnTimer();
        this.onTurnTimeout = timeoutCallback;

        turnTimer = new Timer();
        final int[] remaining = {turnDurationSeconds};

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

    public boolean makeMove(int col, int playerId) {
        if (col < 0 || col >= COLS || gameFinished) return false;
        for (int row = ROWS - 1; row >= 0; row--) {
            if (board[row][col] == 0) {
                board[row][col] = (playerId == player1) ? 1 : 2;
                return true;
            }
        }
        return false; // column full
    }

    public boolean checkWinner() {
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
        cancelTurnTimer();     // Stop any ongoing timers
        clearBoard();          // Clear the board
        this.gameInProgress = true;
        this.gameFinished = false;
        this.consecutiveTimeouts = 0;
        this.currentPlayer = initialStartingPlayer; // Keep same starter or rotate if desired
    }

    private void clearBoard() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;
            }
        }
    }

    private boolean checkDirection(int row, int col) {
        int player = board[row][col];

        // Horizontal
        if (col + 3 < COLS &&
                player == board[row][col + 1] &&
                player == board[row][col + 2] &&
                player == board[row][col + 3])
            return true;

        // Vertical
        if (row + 3 < ROWS &&
                player == board[row + 1][col] &&
                player == board[row + 2][col] &&
                player == board[row + 3][col])
            return true;

        // Diagonal \
        if (row + 3 < ROWS && col + 3 < COLS &&
                player == board[row + 1][col + 1] &&
                player == board[row + 2][col + 2] &&
                player == board[row + 3][col + 3])
            return true;

        // Diagonal /
        if (row - 3 >= 0 && col + 3 < COLS &&
                player == board[row - 1][col + 1] &&
                player == board[row - 2][col + 2] &&
                player == board[row - 3][col + 3])
            return true;

        return false;
    }

    public String getBoardString() {
        StringBuilder boardString = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                boardString.append(board[row][col]);
            }
            if (row < ROWS - 1) {
                boardString.append(",");
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

    public int getTurnDurationSeconds() {
        return this.turnDurationSeconds;
    }
}
