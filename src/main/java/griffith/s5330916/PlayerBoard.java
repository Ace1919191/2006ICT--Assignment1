package griffith.s5330916;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Encapsulates one player's independent Tetris board: its own grid,
 * falling piece, score, and fall timer. Two of these are created side
 * by side in {@link Game} to run a local two-player match.
 */
public class PlayerBoard {

    private static final String EMPTY_CELL_STYLE =
            "-fx-background-color: black;" +
                    "-fx-border-color: #555;" +
                    "-fx-border-width: 0.5;";

    private final String playerName;
    private final int fieldHeight;
    private final int fieldWidth;

    private boolean aiPlayer = false;
    private Move pendingAIMove;
    private boolean aiRotationComplete = false;

    // Called once when this player's board tops out
    private final Runnable onGameOver;

    // Shared with the other player's board (when there is one) so that both
    // players receive the exact same sequence of pieces
    private final PieceSequence pieceSequence;

    // Counts how many pieces this board has spawned so far; used as the
    // index into pieceSequence so "piece number N" matches for every board
    private int pieceIndex = 0;

    private GameBoard gameBoard;
    private PieceController pieceController;

    private double cellSize;

    // Each Pane represents one visible space in this player's grid
    private Pane[][] gridCells;

    // Separate visual layer for currently falling piece
    private Pane pieceOverlay;
    private Group fallingPieceGroup = new Group();

    // Animations used when moving the falling piece
    private TranslateTransition horizontalAnimation;
    private TranslateTransition verticalAnimation;

    // Timer controls how often this player's piece falls
    private Timeline fallTimer;

    private boolean paused = false;
    private boolean softDropActive = false;

    // Preventing the game-over callback from firing more than once
    private boolean gameOverTriggered = false;

    private int score = 0;
    private Label scoreLabel;
    private Label statusLabel;

    private VBox view;

    public PlayerBoard(String playerName, int fieldHeight, int fieldWidth, Runnable onGameOver,
                       PieceSequence pieceSequence) {
        this.playerName = playerName;
        this.fieldHeight = fieldHeight;
        this.fieldWidth = fieldWidth;
        this.onGameOver = onGameOver;
        this.pieceSequence = pieceSequence;

        buildView();
    }

    // Building all visual elements for this player's board
    private void buildView() {
        gridCells = new Pane[fieldHeight][fieldWidth];
        gameBoard = new GameBoard(fieldHeight, fieldWidth);
        pieceController = new PieceController(gameBoard);

        // Creating name label so each board can be told apart
        Label nameLabel = new Label(playerName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        // Creating GridPane which will contain all Tetris cells for this player
        GridPane gameGrid = new GridPane();
        gameGrid.setAlignment(Pos.CENTER);

        // Calculating cell size so two boards fit comfortably side by side
        cellSize = Math.min(420.0 / fieldHeight, 380.0 / fieldWidth);
        cellSize = Math.min(cellSize, 26);

        // Creating each individual cell in this player's grid
        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                Pane cell = new Pane();

                cell.setMinSize(cellSize, cellSize);
                cell.setPrefSize(cellSize, cellSize);
                cell.setMaxSize(cellSize, cellSize);
                cell.setStyle(EMPTY_CELL_STYLE);

                gridCells[row][column] = cell;
                gameGrid.add(cell, column, row);
            }
        }

        // Creating separate layer for smoothly moving falling piece
        pieceOverlay = new Pane();

        double boardWidth = fieldWidth * cellSize;
        double boardHeight = fieldHeight * cellSize;

        pieceOverlay.setMinSize(boardWidth, boardHeight);
        pieceOverlay.setPrefSize(boardWidth, boardHeight);
        pieceOverlay.setMaxSize(boardWidth, boardHeight);
        pieceOverlay.setMouseTransparent(true);
        pieceOverlay.getChildren().add(fallingPieceGroup);

        // Placing falling piece layer directly over this player's grid
        StackPane boardStack = new StackPane(gameGrid, pieceOverlay);
        boardStack.setAlignment(Pos.CENTER);
        boardStack.setMinSize(boardWidth, boardHeight);
        boardStack.setPrefSize(boardWidth, boardHeight);
        boardStack.setMaxSize(boardWidth, boardHeight);

        // Creating status label for displaying Paused / Game Over
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 14px;");

        // Creating score label for this player
        scoreLabel = new Label("Score: 0");
        scoreLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 14px; -fx-font-weight: bold;");

        view = new VBox(10, nameLabel, boardStack, statusLabel, scoreLabel);
        view.setAlignment(Pos.CENTER);

        // Creating timer which moves this player's piece down automatically
        fallTimer = new Timeline(new KeyFrame(Duration.millis(500), ignored -> movePieceDown()));
        fallTimer.setCycleCount(Animation.INDEFINITE);
    }

    public void setAiPlayer(boolean aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    private void applyAIMove() {
        ActivePiece piece = pieceController.getCurrentPiece();

        if (!aiRotationComplete) {
            for (int i = 0; i < pendingAIMove.rotations(); i++) {
                pieceController.rotatePiece();
            }
            updateFallingPieceShape();
            aiRotationComplete = true;
        }
        int targetCol = pendingAIMove.column();
        int currentCol = piece.getAnchorColumn();

        if (currentCol < targetCol) {
            pieceController.movePieceHorizontal(1);
        } else if (currentCol > targetCol) {
            pieceController.movePieceHorizontal(-1);
        }

        fallingPieceGroup.setTranslateX(piece.getAnchorColumn() * cellSize);
        fallingPieceGroup.setTranslateY(piece.getAnchorRow() * cellSize);

        animateHorizontalMovement();

        //fast drop
        if (piece.getAnchorColumn() == targetCol) {
            fallTimer.setRate(10);
        }

    }


    // Returning the assembled view so Game.java can place it in the layout
    public VBox getView() {
        return view;
    }

    // Starting this player's fall timer and spawning their first piece
    public void start() {
        spawnPiece();
        fallTimer.play();
    }

    // Pausing this player's timer and animations
    public void pause() {
        // A board that has already topped out has nothing left to pause -
        // leave its "Game Over" label alone
        if (gameOverTriggered) {
            return;
        }
        paused = true;
        fallTimer.pause();
        pausePieceAnimations();
        statusLabel.setText("Paused");
    }

    // Resuming this player's timer and animations
    public void resume() {
        if (gameOverTriggered) {
            return;
        }
        paused = false;
        statusLabel.setText("");
        resumePieceAnimations();
        fallTimer.play();
    }

    // Stopping this player's board completely, e.g. when leaving the game
    public void stop() {
        fallTimer.stop();
        stopPieceAnimations();
        softDropActive = false;
    }

    public int getScore() {
        return score;
    }

    // Moving current piece left
    public void moveLeft() {
        movePieceHorizontal(-1);
    }

    // Moving current piece right
    public void moveRight() {
        movePieceHorizontal(1);
    }

    // Rotating current piece 90 degrees clockwise around anchor block
    public void rotate() {
        if (paused || gameOverTriggered) {
            return;
        }
        if (pieceController.rotatePiece()) {
            updateFallingPieceShape();
        }
    }

    // Turning soft drop on or off for this player
    public void setSoftDrop(boolean active) {
        if (paused || gameOverTriggered) {
            return;
        }

        if (active) {
            // Preventing auto-repeat on key hold
            if (!softDropActive) {
                softDropActive = true;
                fallTimer.setRate(5);
                movePieceDown();
            }
        } else {
            softDropActive = false;
            fallTimer.setRate(1);
        }
    }

    // Spawning new piece at top centre of this player's grid
    private void spawnPiece() {
        stopPieceAnimations();

        // Removing visual object belonging to previous piece
        pieceOverlay.getChildren().remove(fallingPieceGroup);

        // Creating completely new visual object for spawned piece
        fallingPieceGroup = new Group();
        pieceOverlay.getChildren().add(fallingPieceGroup);

        int anchorRow = 1;
        int anchorColumn = fieldWidth / 2;

        // Pulling the next piece from the shared sequence so that both
        // players (in Two Player Mode) receive identical piece order
        PieceType currentPieceType = pieceSequence.getPiece(pieceIndex);
        pieceIndex++;

        ActivePiece currentPiece = new ActivePiece(currentPieceType, anchorRow, anchorColumn);
        pieceController.setCurrentPiece(currentPiece);

        if (aiPlayer) {
            TetrisAI ai = new TetrisAI(new BoardEvaluator());
            pendingAIMove= ai.findBestMove(gameBoard, currentPiece);
            aiRotationComplete = false;
        }



        // Ending this player's game if new piece cannot fit onto grid
        if (!gameBoard.canPlacePiece(currentPiece, anchorRow, anchorColumn)) {
            fallTimer.stop();
            stopPieceAnimations();
            fallingPieceGroup.getChildren().clear();
            statusLabel.setText("Game Over");

            if (!gameOverTriggered) {
                gameOverTriggered = true;
                onGameOver.run();
            }
            return;
        }

        // Resetting animations and drawing new falling piece at spawn position
        stopPieceAnimations();
        updateFallingPieceShape();
        fallingPieceGroup.setTranslateX(anchorColumn * cellSize);
        fallingPieceGroup.setTranslateY(anchorRow * cellSize);

        renderGrid();



    }

    // Moving current piece down one grid space
    private void movePieceDown() {
        // Preventing movement while paused
        if (paused) {
            return;
        }

        if (aiPlayer && pendingAIMove != null) {
            applyAIMove();
        }

        // Moving piece if next position is available
        if (pieceController.movePieceDown()) {
            animateVerticalMovement();
        } else {
            // Locking piece into grid once it can no longer move down
            lockPiece();

            // Checking for completed rows and adding score
            int linesCleared = gameBoard.clearFullRows();
            addScore(linesCleared);

            // Spawning another random piece
            spawnPiece();
        }

    }

    // Moving current piece left or right
    private void movePieceHorizontal(int direction) {
        // Preventing movement while paused, or once this board has topped out
        if (paused || gameOverTriggered) {
            return;
        }
        // Moving piece if new horizontal position is available
        if (pieceController.movePieceHorizontal(direction)) {
            animateHorizontalMovement();
        }
    }

    // Converting falling piece into locked blocks
    private void lockPiece() {
        stopPieceAnimations();

        gameBoard.lockPiece(pieceController.getCurrentPiece());

        // Removing separate falling visual because piece is now part of grid
        fallingPieceGroup.getChildren().clear();

        renderGrid();
    }

    // Adding score depending on number of lines cleared at once
    private void addScore(int linesCleared) {
        switch (linesCleared) {
            case 1:
                score += 100;
                break;
            case 2:
                score += 300;
                break;
            case 3:
                score += 500;
                break;
            case 4:
                score += 800;
                break;
            default:
                break;
        }
        // Updating displayed score
        scoreLabel.setText("Score: " + score);
    }

    // Updating appearance of this player's entire grid
    private void renderGrid() {
        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                PieceType lockedPiece = gameBoard.getLockedBlock(row, column);
                if (lockedPiece != null) {
                    gridCells[row][column].setStyle(createPieceStyle(lockedPiece.getColour()));
                } else {
                    gridCells[row][column].setStyle(EMPTY_CELL_STYLE);
                }
            }
        }
    }

    // Creating visual blocks for this player's currently falling piece
    private void updateFallingPieceShape() {
        fallingPieceGroup.getChildren().clear();

        ActivePiece currentPiece = pieceController.getCurrentPiece();
        for (int[] block : currentPiece.getCurrentPieceShape()) {
            Rectangle rectangle = new Rectangle(cellSize, cellSize);

            // Positioning block relative to anchor block
            rectangle.setX(block[1] * cellSize);
            rectangle.setY(block[0] * cellSize);

            // Applying piece colour and border to individual block
            rectangle.setStyle("-fx-fill: " + currentPiece.getCurrentPieceType().getColour() + ";" +
                    "-fx-stroke: black; -fx-stroke-width: 2;");

            fallingPieceGroup.getChildren().add(rectangle);
        }
    }

    // Smoothly moving falling piece left or right
    private void animateHorizontalMovement() {
        if (horizontalAnimation != null) {
            horizontalAnimation.stop();
        }

        horizontalAnimation = new TranslateTransition(Duration.millis(80), fallingPieceGroup);
        horizontalAnimation.setToX(pieceController.getCurrentPiece().getAnchorColumn() * cellSize);
        horizontalAnimation.setInterpolator(Interpolator.EASE_BOTH);
        horizontalAnimation.play();
    }

    // Smoothly moving falling piece down towards its next grid position
    private void animateVerticalMovement() {
        if (verticalAnimation != null) {
            verticalAnimation.stop();
        }

        double animationTime = 480;

        // Shortening animation while soft drop is accelerating the piece
        if (fallTimer.getRate() > 1) {
            animationTime = 90;
        }

        verticalAnimation = new TranslateTransition(Duration.millis(animationTime), fallingPieceGroup);
        verticalAnimation.setToY(pieceController.getCurrentPiece().getAnchorRow() * cellSize);
        verticalAnimation.setInterpolator(Interpolator.LINEAR);
        verticalAnimation.play();
    }

    // Pausing visual movement of this player's falling piece
    private void pausePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.pause(); }
        if (verticalAnimation != null) { verticalAnimation.pause(); }
    }

    // Resuming visual movement of this player's falling piece
    private void resumePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.play(); }
        if (verticalAnimation != null) { verticalAnimation.play(); }
    }

    // Stopping current movement animations before locking or replacing piece
    private void stopPieceAnimations() {
        if (horizontalAnimation != null) {
            horizontalAnimation.stop();
            horizontalAnimation.setNode(null);
            horizontalAnimation = null;
        }

        if (verticalAnimation != null) {
            verticalAnimation.stop();
            verticalAnimation.setNode(null);
            verticalAnimation = null;
        }
    }

    // Creating coloured block with border so individual cells remain visible
    private static String createPieceStyle(String colour) {
        return "-fx-background-color: " + colour + ";" +
                "-fx-border-color: black; -fx-border-width: 2;";
    }
}