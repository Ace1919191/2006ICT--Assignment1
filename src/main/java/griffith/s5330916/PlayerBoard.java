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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerBoard {
    private static final String EMPTY_CELL_STYLE =
            "-fx-background-color: black;" +
                    "-fx-border-color: #555;" +
                    "-fx-border-width: 0.5;";

    private final String playerName;
    private final int fieldHeight;
    private final int fieldWidth;
    private final Runnable onGameOver;
    private final PieceSequence pieceSequence;

    // Single background worker keeps server updates in order for this board.
    private final ExecutorService serverExecutor;
    private final AtomicBoolean serverRequestActive = new AtomicBoolean(false);
    private volatile PureGame pendingServerSnapshot;

    private int pieceIndex = 0;
    private PieceType nextPieceType;

    private GameBoard gameBoard;
    private PieceController pieceController;

    private double cellSize;
    private Pane[][] gridCells;
    private Pane pieceOverlay;
    private Group fallingPieceGroup = new Group();

    private TranslateTransition horizontalAnimation;
    private TranslateTransition verticalAnimation;
    private Timeline fallTimer;

    private boolean paused = false;
    private boolean softDropActive = false;
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

        serverExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "TetrisServerSync-" + playerName);
            thread.setDaemon(true);
            return thread;
        });

        buildView();
    }

    private void buildView() {
        gridCells = new Pane[fieldHeight][fieldWidth];
        gameBoard = new GameBoard(fieldHeight, fieldWidth);
        pieceController = new PieceController(gameBoard);

        Label nameLabel = new Label(playerName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        GridPane gameGrid = new GridPane();
        gameGrid.setAlignment(Pos.CENTER);

        cellSize = Math.min(420.0 / fieldHeight, 380.0 / fieldWidth);
        cellSize = Math.min(cellSize, 26);

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

        pieceOverlay = new Pane();

        double boardWidth = fieldWidth * cellSize;
        double boardHeight = fieldHeight * cellSize;

        pieceOverlay.setMinSize(boardWidth, boardHeight);
        pieceOverlay.setPrefSize(boardWidth, boardHeight);
        pieceOverlay.setMaxSize(boardWidth, boardHeight);
        pieceOverlay.setMouseTransparent(true);
        pieceOverlay.getChildren().add(fallingPieceGroup);

        StackPane boardStack = new StackPane(gameGrid, pieceOverlay);
        boardStack.setAlignment(Pos.CENTER);
        boardStack.setMinSize(boardWidth, boardHeight);
        boardStack.setPrefSize(boardWidth, boardHeight);
        boardStack.setMaxSize(boardWidth, boardHeight);

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 14px;");

        scoreLabel = new Label("Score: 0");
        scoreLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 14px; -fx-font-weight: bold;");

        view = new VBox(10, nameLabel, boardStack, statusLabel, scoreLabel);
        view.setAlignment(Pos.CENTER);

        fallTimer = new Timeline(new KeyFrame(Duration.millis(500), ignored -> movePieceDown()));
        fallTimer.setCycleCount(Animation.INDEFINITE);
    }

    public VBox getView() {
        return view;
    }

    public void start() {
        spawnPiece();
        fallTimer.play();
    }

    public void pause() {
        if (gameOverTriggered) {
            return;
        }

        paused = true;
        fallTimer.pause();
        pausePieceAnimations();
        statusLabel.setText("Paused");
    }

    public void resume() {
        if (gameOverTriggered) {
            return;
        }

        paused = false;
        statusLabel.setText("");
        resumePieceAnimations();
        fallTimer.play();
    }

    public void stop() {
        fallTimer.stop();
        stopPieceAnimations();
        softDropActive = false;
        pendingServerSnapshot = null;
        serverExecutor.shutdownNow();
    }

    public int getScore() {
        return score;
    }

    public void moveLeft() {
        movePieceHorizontal(-1);
    }

    public void moveRight() {
        movePieceHorizontal(1);
    }

    public void rotate() {
        if (paused || gameOverTriggered) {
            return;
        }

        if (pieceController.rotatePiece()) {
            updateFallingPieceShape();
            sendCurrentStateToServer();
        }
    }

    public void setSoftDrop(boolean active) {
        if (paused || gameOverTriggered) {
            return;
        }

        if (active) {
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

    private void spawnPiece() {
        stopPieceAnimations();

        pieceOverlay.getChildren().remove(fallingPieceGroup);
        fallingPieceGroup = new Group();
        pieceOverlay.getChildren().add(fallingPieceGroup);

        int anchorRow = 1;
        int anchorColumn = fieldWidth / 2;

        PieceType currentPieceType = pieceSequence.getPiece(pieceIndex);
        pieceIndex++;
        nextPieceType = pieceSequence.getPiece(pieceIndex);

        ActivePiece currentPiece = new ActivePiece(currentPieceType, anchorRow, anchorColumn);
        pieceController.setCurrentPiece(currentPiece);

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

        updateFallingPieceShape();
        fallingPieceGroup.setTranslateX(anchorColumn * cellSize);
        fallingPieceGroup.setTranslateY(anchorRow * cellSize);
        renderGrid();

        sendCurrentStateToServer();
    }

    private void movePieceDown() {
        if (paused || gameOverTriggered) {
            return;
        }

        if (pieceController.movePieceDown()) {
            animateVerticalMovement();
            sendCurrentStateToServer();
        } else {
            lockPiece();

            int linesCleared = gameBoard.clearFullRows();
            addScore(linesCleared);

            spawnPiece();
        }
    }

    private void movePieceHorizontal(int direction) {
        if (paused || gameOverTriggered) {
            return;
        }

        if (pieceController.movePieceHorizontal(direction)) {
            animateHorizontalMovement();
            sendCurrentStateToServer();
        }
    }

    private void sendCurrentStateToServer() {
        if (gameOverTriggered || nextPieceType == null || serverExecutor.isShutdown()) {
            return;
        }

        ActivePiece currentPiece = pieceController.getCurrentPiece();

        if (currentPiece == null) {
            return;
        }

        // Replace any older unsent snapshot with the newest state. This keeps the server mirror current
        // even during soft drop or rapid key presses.
        pendingServerSnapshot = new PureGame(
                playerName,
                fieldWidth,
                fieldHeight,
                gameBoard.getServerCells(),
                copyShape(currentPiece.getCurrentPieceShape()),
                nextPieceType.createShape(),
                currentPiece.getAnchorRow(),
                currentPiece.getAnchorColumn()
        );

        startServerWorkerIfNeeded();
    }

    private void startServerWorkerIfNeeded() {
        if (serverRequestActive.compareAndSet(false, true)) {
            serverExecutor.execute(this::processServerSnapshots);
        }
    }

    private void processServerSnapshots() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                PureGame snapshot = pendingServerSnapshot;
                pendingServerSnapshot = null;

                if (snapshot == null) {
                    break;
                }

                try {
                    // The returned move is intentionally ignored for gameplay. AI is not implemented yet.
                    TetrisClient.requestMove(snapshot);
                } catch (IOException e) {
                    System.err.println("[CLIENT] " + playerName + " -> TetrisServer unavailable: " + e.getMessage());
                    pendingServerSnapshot = null;
                    break;
                }
            }
        } finally {
            serverRequestActive.set(false);

            if (pendingServerSnapshot != null && !serverExecutor.isShutdown()) {
                startServerWorkerIfNeeded();
            }
        }
    }

    private static int[][] copyShape(int[][] shape) {
        int[][] copy = new int[shape.length][2];

        for (int i = 0; i < shape.length; i++) {
            copy[i][0] = shape[i][0];
            copy[i][1] = shape[i][1];
        }

        return copy;
    }

    private void lockPiece() {
        stopPieceAnimations();
        gameBoard.lockPiece(pieceController.getCurrentPiece());
        fallingPieceGroup.getChildren().clear();
        renderGrid();
    }

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

        scoreLabel.setText("Score: " + score);
    }

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

    private void updateFallingPieceShape() {
        fallingPieceGroup.getChildren().clear();

        ActivePiece currentPiece = pieceController.getCurrentPiece();

        for (int[] block : currentPiece.getCurrentPieceShape()) {
            Rectangle rectangle = new Rectangle(cellSize, cellSize);

            rectangle.setX(block[1] * cellSize);
            rectangle.setY(block[0] * cellSize);
            rectangle.setStyle("-fx-fill: " + currentPiece.getCurrentPieceType().getColour() + ";"
                    + "-fx-stroke: black; -fx-stroke-width: 2;");

            fallingPieceGroup.getChildren().add(rectangle);
        }
    }

    private void animateHorizontalMovement() {
        if (horizontalAnimation != null) {
            horizontalAnimation.stop();
        }

        horizontalAnimation = new TranslateTransition(Duration.millis(80), fallingPieceGroup);
        horizontalAnimation.setToX(pieceController.getCurrentPiece().getAnchorColumn() * cellSize);
        horizontalAnimation.setInterpolator(Interpolator.EASE_BOTH);
        horizontalAnimation.play();
    }

    private void animateVerticalMovement() {
        if (verticalAnimation != null) {
            verticalAnimation.stop();
        }

        double animationTime = 480;

        if (fallTimer.getRate() > 1) {
            animationTime = 90;
        }

        verticalAnimation = new TranslateTransition(Duration.millis(animationTime), fallingPieceGroup);
        verticalAnimation.setToY(pieceController.getCurrentPiece().getAnchorRow() * cellSize);
        verticalAnimation.setInterpolator(Interpolator.LINEAR);
        verticalAnimation.play();
    }

    private void pausePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.pause(); }
        if (verticalAnimation != null) { verticalAnimation.pause(); }
    }

    private void resumePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.play(); }
        if (verticalAnimation != null) { verticalAnimation.play(); }
    }

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

    private static String createPieceStyle(String colour) {
        return "-fx-background-color: " + colour + ";"
                + "-fx-border-color: black; -fx-border-width: 2;";
    }
}
