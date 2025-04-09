import javax.swing.*;
import javax.swing.Timer;  // Import the Swing Timer
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class OthelloGame extends JFrame {
    // Board constants
    private static final int BOARD_SIZE = 8;
    private static final char EMPTY = '.';
    private static final char BLACK = 'B';
    private static final char WHITE = 'W';

    // All eight possible directions for move evaluation
    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            { 0, -1},          { 0, 1},
            { 1, -1}, { 1, 0}, { 1, 1}
    };

    // Predefined score matrix for each position on the board.
    // Higher scores are better positions.
    private static final int[][] SCORE_MATRIX = {
            { 100, -20,  10,   5,   5,  10, -20, 100 },
            { -20, -50,  -10,  -2,  -2,  -10, -50, -20 },
            {  10,  -10,   9,   0,   0,   9,  -10,  10 },
            {   5,  -2,   0,   0,   0,   0,  -2,   5 },
            {   5,  -2,   0,   0,   0,   0,  -2,   5 },
            {  10,  -10,   9,   0,   0,   9,  -10,  10 },
            { -20, -50,  -10,  -2,  -2,  -10, -50, -20 },
            { 100, -20,  10,   5,   5,  10, -20, 100 }
    };

    // Game state: board and whose turn it is.
    private char[][] board = new char[BOARD_SIZE][BOARD_SIZE];
    private boolean humanTurn = true; // Human is Black, AI is White

    // GUI components
    private JPanel boardPanel;
    private JLabel messageLabel;
    private CellButton[][] cellButtons = new CellButton[BOARD_SIZE][BOARD_SIZE];

    // A helper class for each board cell as a JButton
    private class CellButton extends JButton {
        int row, col;
        public CellButton(int row, int col) {
            this.row = row;
            this.col = col;
            setFont(new Font("Arial", Font.BOLD, 24));
        }
    }

    public OthelloGame() {
        setTitle("Othello Game with Lookahead Weighted AI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Message at top
        messageLabel = new JLabel("Your turn (Black).");
        add(messageLabel, BorderLayout.NORTH);

        // Board panel setup using GridLayout
        boardPanel = new JPanel();
        boardPanel.setLayout(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                CellButton btn = new CellButton(i, j);
                btn.setFocusPainted(false);
                btn.addActionListener(new ButtonListener());
                cellButtons[i][j] = btn;
                boardPanel.add(btn);
            }
        }
        add(boardPanel, BorderLayout.CENTER);

        // Initialize board and update display
        initializeBoard();
        updateBoard();

        setSize(500, 550);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Initialize board to the standard starting position
    private void initializeBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            Arrays.fill(board[i], EMPTY);
        }
        // Starting pieces in center
        board[3][3] = WHITE;
        board[3][4] = BLACK;
        board[4][3] = BLACK;
        board[4][4] = WHITE;
    }

    // Update the GUI to match the board state
    private void updateBoard() {
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                CellButton btn = cellButtons[i][j];
                char cell = board[i][j];
                if (cell == BLACK) {
                    btn.setText("●");
                    btn.setForeground(Color.BLACK);
                } else if (cell == WHITE) {
                    btn.setText("○");
                    btn.setForeground(Color.WHITE);
                } else {
                    btn.setText("");
                }
                // Highlight legal moves for the human player if it's their turn.
                if (humanTurn && board[i][j] == EMPTY && isValidMove(board, i, j, BLACK)) {
                    btn.setBackground(new Color(200, 255, 200));  // light green highlight
                } else {
                    btn.setBackground(new Color(125, 125, 125));
                }
            }
        }
    }

    // Check if a given cell (row, col) is on board.
    private boolean onBoard(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    // Returns list of positions to flip if the move is valid; otherwise an empty list.
    private List<Point> getFlips(char[][] state, int row, int col, char player) {
        List<Point> flips = new ArrayList<>();
        if (state[row][col] != EMPTY) {
            return flips;
        }
        char opponent = (player == BLACK) ? WHITE : BLACK;
        // Check all eight directions for valid flips.
        for (int[] dir : DIRECTIONS) {
            int r = row + dir[0], c = col + dir[1];
            List<Point> candidates = new ArrayList<>();
            while (onBoard(r, c) && state[r][c] == opponent) {
                candidates.add(new Point(r, c));
                r += dir[0];
                c += dir[1];
            }
            if (onBoard(r, c) && state[r][c] == player && !candidates.isEmpty()) {
                flips.addAll(candidates);
            }
        }
        return flips;
    }

    // Check if the cell at row, col is a valid move for the player.
    private boolean isValidMove(char[][] state, int row, int col, char player) {
        return !getFlips(state, row, col, player).isEmpty();
    }

    // Get all valid moves for a player mapped to list of flips.
    private Map<Point, List<Point>> getAllValidMoves(char[][] state, char player) {
        Map<Point, List<Point>> moves = new HashMap<>();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (state[i][j] == EMPTY) {
                    List<Point> flips = getFlips(state, i, j, player);
                    if (!flips.isEmpty()) {
                        moves.put(new Point(i, j), flips);
                    }
                }
            }
        }
        return moves;
    }

    // Make a move on the board, flipping appropriate pieces.
    private void makeMove(char[][] state, int row, int col, char player, List<Point> flips) {
        state[row][col] = player;
        for (Point p : flips) {
            state[p.x][p.y] = player;
        }
    }

    /**
     * Decision method for the AI move.
     * For each valid move:
     *   1. Compute A: The weighted score for the AI move (cell score + sum of scores for each flip).
     *   2. Simulate the move on a copy of the board.
     *   3. Compute B: For the resulting board, determine all valid moves for the opponent.
     *      For each opponent move, compute its weighted score (cell score + flips).
     *      Let max(B) be the maximum score among these moves (or 0 if opponent has no moves).
     *   4. The evaluation is A + max(B).
     * The AI selects the move with the highest evaluation.
     */
    private Point decisionAIMove(char[][] state, char player) {
        Map<Point, List<Point>> moves = getAllValidMoves(state, player);
        if (moves.isEmpty()) {
            return null;
        }
        Point bestMove = null;
        int bestEvaluation = Integer.MIN_VALUE;
        for (Map.Entry<Point, List<Point>> entry : moves.entrySet()) {
            Point move = entry.getKey();
            List<Point> flips = entry.getValue();
            // Compute A, the immediate weighted score for this move.
            int A = SCORE_MATRIX[move.x][move.y];
            // 发现根本没必要算flip
//            for (Point p : flips) {
//                A += Math.max(SCORE_MATRIX[p.x][p.y], 0);
//            }

            // Simulate the move on a temporary board.
            char[][] simBoard = deepCopyBoard(state);
            makeMove(simBoard, move.x, move.y, player, flips);

            // Now determine the potential for the opponent.
            char opp = (player == BLACK) ? WHITE : BLACK;
            Map<Point, List<Point>> oppMoves = getAllValidMoves(simBoard, opp);
            int maxB = -100;
            if (!oppMoves.isEmpty()) {
                for (Map.Entry<Point, List<Point>> oppEntry : oppMoves.entrySet()) {
                    Point oppMove = oppEntry.getKey();
                    List<Point> oppFlips = oppEntry.getValue();
                    int B = SCORE_MATRIX[oppMove.x][oppMove.y];
//                    for (Point p : oppFlips) {
//                        B += SCORE_MATRIX[p.x][p.y];
//                    }
                    if (B > maxB) {
                        maxB = B;
                    }
                }
            }
            int evaluation = A - maxB;
            // For debugging, you might print out the move and its evaluation.
            // System.out.printf("Move (%d,%d): A = %d, max(B) = %d, eval = %d%n", move.x, move.y, A, maxB, evaluation);
            if (evaluation > bestEvaluation) {
                bestEvaluation = evaluation;
                bestMove = move;
            }
        }
        return bestMove;
    }

    // Utility method to deep copy the board
    private char[][] deepCopyBoard(char[][] original) {
        char[][] copy = new char[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            copy[i] = Arrays.copyOf(original[i], BOARD_SIZE);
        }
        return copy;
    }

    // Check if there are any valid moves available for a player.
    private boolean hasValidMove(char[][] state, char player) {
        return !getAllValidMoves(state, player).isEmpty();
    }

    // Count score for board.
    private Map<Character, Integer> scoreBoard(char[][] state) {
        int black = 0, white = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (state[i][j] == BLACK) {
                    black++;
                } else if (state[i][j] == WHITE) {
                    white++;
                }
            }
        }
        Map<Character, Integer> score = new HashMap<>();
        score.put(BLACK, black);
        score.put(WHITE, white);
        return score;
    }

    // Called after each move to check if the game is over.
    private boolean gameOver() {
        return !hasValidMove(board, BLACK) && !hasValidMove(board, WHITE);
    }

    // Handle the AI move (White) using the new decision rule.
    private void processAIMove() {
        // Check if AI has any valid moves. If not, skip AI's turn immediately.
        if (!hasValidMove(board, WHITE)) {
            messageLabel.setText("AI has no valid moves. Your turn (Black).");
            humanTurn = true;
            return;
        }

        // Delay AI move to simulate thinking using a Swing Timer.
        javax.swing.Timer timer = new javax.swing.Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Point move = decisionAIMove(board, WHITE);
                if (move != null) {
                    List<Point> flips = getFlips(board, move.x, move.y, WHITE);
                    makeMove(board, move.x, move.y, WHITE, flips);
                    messageLabel.setText("Your turn (Black).");
                }
                humanTurn = true;
                updateBoard();

                if (gameOver()) {
                    endGame();
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
    // Display final score and game result.
    private void endGame() {
        Map<Character, Integer> scores = scoreBoard(board);
        String result;
        if (scores.get(BLACK) > scores.get(WHITE)) {
            result = "You win!";
        } else if (scores.get(BLACK) < scores.get(WHITE)) {
            result = "AI wins!";
        } else {
            result = "It's a tie!";
        }
        messageLabel.setText("Game Over! " + result + " (Black: " + scores.get(BLACK)
                + " White: " + scores.get(WHITE) + ")");
    }

    // Listener for board cell clicks for human moves.
    private class ButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!hasValidMove(board, BLACK)) {
                // No valid move for the human player; skip their turn.
                JOptionPane.showMessageDialog(OthelloGame.this, "No valid moves available for you. Skipping your turn.");
                humanTurn = false;
                // Directly process the AI move.
                processAIMove();
                return;
            }

            if (!humanTurn) {
                return; // Ignore clicks when it's not human turn.
            }
            CellButton btn = (CellButton) e.getSource();
            int row = btn.row, col = btn.col;

            if (!isValidMove(board, row, col, BLACK)) {
                JOptionPane.showMessageDialog(OthelloGame.this, "Invalid move. Please select a valid cell.");
                return;
            }
            // Make the human move.
            List<Point> flips = getFlips(board, row, col, BLACK);
            makeMove(board, row, col, BLACK, flips);
            humanTurn = false;
            updateBoard();

            // If game is over, end; otherwise let AI move if possible.
            if (gameOver()) {
                endGame();
                return;
            }
            // Check if AI has moves; if not, return turn to human.
            if (!hasValidMove(board, WHITE)) {
                messageLabel.setText("AI has no valid moves. Your turn (Black).");
                humanTurn = true;
            } else {
                messageLabel.setText("AI is thinking...");
                processAIMove();
            }
        }
    }

    public static void main(String[] args) {
        // Run GUI in the Event Dispatch Thread for thread safety.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new OthelloGame();
            }
        });
    }
}
