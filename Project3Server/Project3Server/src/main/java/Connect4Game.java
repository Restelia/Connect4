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
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private int[][] board = new int[ROWS][COLS];  // 0 = empty, 1 = player 1, 2 = player 2
    private int winner = 0;

    public Connect4Game() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                board[row][col] = 0;  // Initialize the board as empty
            }
        }
    }

    public void setTurnDuration(int seconds) {
        this.turnDurationSeconds = seconds;
    }

    public int getTurnDuration() {
        return this.turnDurationSeconds;
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

    public boolean makeMove(int col, int playerId) {
        if (col < 0 || col >= COLS) return false;
        for (int row = ROWS - 1; row >= 0; row--) {
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
                    this.winner = board[row][col];
                    return true;
                }
            }
        }
        return false;
    }

    public int getWinner() {
        return this.winner;
    }

    public boolean checkDraw() {
        for (int col = 0; col < COLS; col++) {
            if (board[0][col] == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean checkDirection(int row, int col) {
        int player = board[row][col];

        // Check horizontal
        if (col + 3 < COLS &&
                board[row][col] == player &&
                board[row][col+1] == player &&
                board[row][col+2] == player &&
                board[row][col+3] == player) {
            return true;
        }

        // Check vertical
        if (row + 3 < ROWS &&
                board[row][col] == player &&
                board[row+1][col] == player &&
                board[row+2][col] == player &&
                board[row+3][col] == player) {
            return true;
        }

        // Check diagonal \
        if (row + 3 < ROWS && col + 3 < COLS &&
                board[row][col] == player &&
                board[row+1][col+1] == player &&
                board[row+2][col+2] == player &&
                board[row+3][col+3] == player) {
            return true;
        }

        // Check diagonal /
        if (row - 3 >= 0 && col + 3 < COLS &&
                board[row][col] == player &&
                board[row-1][col+1] == player &&
                board[row-2][col+2] == player &&
                board[row-3][col+3] == player) {
            return true;
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

    public int getPlayer1() {
        return player1;
    }

    public int getPlayer2() {
        return player2;
    }

    public Connect4Game copy() {
        Connect4Game copy = new Connect4Game();
        copy.board = new int[ROWS][COLS];
        for (int i = 0; i < ROWS; i++) {
            System.arraycopy(this.board[i], 0, copy.board[i], 0, COLS);
        }
        copy.currentPlayer = this.currentPlayer;
        copy.player1 = this.player1;
        copy.player2 = this.player2;
        copy.gameFinished = this.gameFinished;
        copy.turnDurationSeconds = this.turnDurationSeconds;
        copy.winner = this.winner;
        return copy;
    }

    public boolean isValidMove(int column) {
        if (column < 0 || column >= COLS) return false;
        return board[0][column] == 0; // If top row is empty, column isn't full
    }

    public int[][] getBoard() {
        return board;
    }
}