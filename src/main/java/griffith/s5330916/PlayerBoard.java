package griffith.s5330916;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import javafx.scene.media.AudioClip;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerBoard {

    private static final AudioClip ROTATE_SOUND = new AudioClip(
                PlayerBoard.class.getResource("/audio/rotatepiece.wav").toExternalForm()
        );
    private static final AudioClip LAND_SOUND = new AudioClip(
            PlayerBoard.class.getResource("/audio/piecelands.wav").toExternalForm()
    );
    private static final AudioClip LINE_CLEAR_SOUND = new AudioClip(
            PlayerBoard.class.getResource("/audio/linecleared.wav").toExternalForm()
    );
    private static final String EMPTY_CELL_STYLE =
            "-fx-background-color: black;" +
                    "-fx-border-color: #555;" +
                    "-fx-border-width: 0.5;";

    // Base time between automatic fall steps at normal speed (rate 1)
    private static final double FALL_INTERVAL_MS = 500;

    private final String playerName;
    private final String playerType;
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
    private boolean aiMoveApplied;
    private boolean serverWarningShown = false;
    private double cellSize;

    // Each Pane represents one visible space in this player's grid
    private Pane[][] gridCells;

    // Separate visual layer for currently falling piece
    private Pane pieceOverlay;
    private Group fallingPieceGroup = new Group();

    // Animations used when moving the falling piece
    private TranslateTransition horizontalAnimation;

    // Row the piece is visually sliding away from during the current fall step
    private int visualFromRow;

    // Updates the falling piece's vertical position every frame,
    // using the fall timer's own play head so movement and animation stay in sync
    private AnimationTimer verticalRenderer;

    // Timer controls how often this player's piece falls
    private Timeline fallTimer;

    private boolean paused = false;
    private boolean softDropActive = false;

    // Preventing the game-over callback from firing more than once
    private boolean gameOverTriggered = false;

    private final ScoreManager scoreManager = new ScoreManager();

    private Label scoreLabel;
    private Label levelLabel;
    private Label linesLabel;
    private Label statusLabel;

    private VBox view;

    private boolean humanPlayer = true;
    private boolean externalPlayer = false;

    // AI Variables
    private boolean aiPlayer = false;
    private volatile OpMove pendingAIMove;
    private boolean aiRotationComplete = false;
    private volatile boolean waitingForAIMove = false;


    public PlayerBoard(String playerName, String playerType, int fieldHeight, int fieldWidth, Runnable onGameOver,
                       PieceSequence pieceSequence) {
        this.playerName = playerName;
        this.playerType = playerType;
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

    // Building all visual elements for this player's board
    private void buildView() {
        gridCells = new Pane[fieldHeight][fieldWidth];
        gameBoard = new GameBoard(fieldHeight, fieldWidth);
        pieceController = new PieceController(gameBoard);

        // Creating name label so each board can be told apart
        Label nameLabel = new Label(playerName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        Label playerTypeLabel = new Label("Player Type: " + playerType);
        playerTypeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");

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

// Creating score information labels for this player
        scoreLabel = new Label("Score: 0");
        levelLabel = new Label("Level: 1");
        linesLabel = new Label("Lines Erased: 0");

        String infoStyle =
                "-fx-text-fill: yellow;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;";

        scoreLabel.setStyle(infoStyle);
        levelLabel.setStyle(infoStyle);
        linesLabel.setStyle(infoStyle);

        view = new VBox(
                10,
                nameLabel,
                playerTypeLabel,
                boardStack,
                statusLabel,
                levelLabel,
                scoreLabel,
                linesLabel
        );
        view.setAlignment(Pos.CENTER);

        // Creating timer which moves this player's piece down automatically
        fallTimer = new Timeline(new KeyFrame(Duration.millis(FALL_INTERVAL_MS), ignored -> movePieceDown()));
        fallTimer.setCycleCount(Animation.INDEFINITE);

        // Creating per-frame renderer which positions the falling piece vertically
        verticalRenderer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateVerticalPosition();
            }
        };
    }

    // Returning the assembled view so Game.java can place it in the layout
    public VBox getView() {
        return view;
    }

    // Starting this player's fall timer and spawning their first piece
    public void start() {
        spawnPiece();
        fallTimer.play();
        verticalRenderer.start();
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
        verticalRenderer.stop();
        stopPieceAnimations();
        softDropActive = false;
        pendingServerSnapshot = null;
        pendingAIMove = null;
        waitingForAIMove = false;
        serverExecutor.shutdownNow();
    }

    public int getScore() {
        return scoreManager.getScore();
    }

    public void setHumanPlayer(boolean human) { this.humanPlayer = human; }
    public void setExternalPlayer(boolean external) { this.externalPlayer = external; }
    public void setAiPlayer(boolean aiPlayer) {
        this.aiPlayer = aiPlayer;
    }

    private void applyAIMove() {
        if (pendingAIMove == null) {
            return;
        }

        ActivePiece piece = pieceController.getCurrentPiece();
        if (piece == null) {
            return;
        }

        if (!aiRotationComplete) {
            for (int i = 0; i < pendingAIMove.opRotate(); i++) {
                if (pieceController.rotatePiece()) {
                    playSound(ROTATE_SOUND);
                }
            }
            updateFallingPieceShape();
            aiRotationComplete = true;
        }

        int targetColumn = pendingAIMove.opX();
        int currentColumn = piece.getAnchorColumn();

        if (currentColumn < targetColumn) {
            pieceController.movePieceHorizontal(1);
        } else if (currentColumn > targetColumn) {
            pieceController.movePieceHorizontal(-1);
        }

        // Animating from the piece's current visual position instead of snapping
        // it into place, so AI pieces glide the same way human pieces do
        animateHorizontalMovement();

        if (aiRotationComplete && piece.getAnchorColumn() == targetColumn) {
            fallTimer.setRate(5);
        }

    }

    private OpMove evaluateLocalMove() {
        int[][] cells = gameBoard.getServerCells();
        ActivePiece piece = pieceController.getCurrentPiece();
        int[][] shape = copyShape(piece.getCurrentPieceShape());

        int bestColumn = piece.getAnchorColumn();
        int bestRotation = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int rotation = 0; rotation < 4; rotation++) {
            for (int column = 0; column < fieldWidth; column++) {
                int landingRow = findLandingRowLocal(cells, shape, column);

                if (landingRow < 0) continue;

                int[][] simulated = copyBoardLocal(cells);
                lockShapeLocal(simulated, shape, landingRow, column);

                int linesCleared = clearFullRowsLocal(simulated);
                double score = evaluateBoardLocal(simulated, linesCleared);

                if (score > bestScore) {
                    bestScore = score;
                    bestColumn = column;
                    bestRotation = rotation;
                }
            }

            shape = rotateShapeLocal(shape);
        }

        return new OpMove(bestColumn, bestRotation);
    }


    private void applyLocalAIMove() {
        if (!aiMoveApplied) {
            aiMoveApplied = true;
            pendingAIMove = evaluateLocalMove();
            aiRotationComplete = false;
        }
        if (pendingAIMove == null) {
            return;
        }
        applyAIMove();
        if (pieceController.getCurrentPiece().getAnchorColumn() == pendingAIMove.opX()) {
            pendingAIMove = null;
        }
    }


    // Moving current piece left
    public void moveLeft() {
        if (!humanPlayer && !aiPlayer) return;
        movePieceHorizontal(-1);
    }

    // Moving current piece right
    public void moveRight() {
        if (!humanPlayer && !aiPlayer) return;
        movePieceHorizontal(1);
    }

    // Rotating current piece 90 degrees clockwise around anchor block
    public void rotate() {
        if (paused || gameOverTriggered || (!humanPlayer && !aiPlayer)) {
            return;
        }

        if (pieceController.rotatePiece()) {
            updateFallingPieceShape();
            playSound(ROTATE_SOUND);
            if (externalPlayer) {
                sendCurrentStateToServer();
            }
        }
    }

    // Turning soft drop on or off for this player
    public void setSoftDrop(boolean active) {
        if (paused || gameOverTriggered || (!humanPlayer && !aiPlayer)) {
            return;
        }

        if (active) {
            // Preventing auto-repeat on key hold
            if (!softDropActive) {
                softDropActive = true;
                fallTimer.setRate(5);
                movePieceDown();
                // Restarting the cycle so the next automatic drop is a full
                // interval away and the visual slide starts from the beginning
                fallTimer.playFromStart();
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
        nextPieceType = pieceSequence.getPiece(pieceIndex);

        ActivePiece currentPiece = new ActivePiece(currentPieceType, anchorRow, anchorColumn);
        pieceController.setCurrentPiece(currentPiece);

        // Ending this player's game if new piece cannot fit onto grid
        if (!gameBoard.canPlacePiece(currentPiece, anchorRow, anchorColumn)) {
            fallTimer.stop();
            verticalRenderer.stop();
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

        // New piece starts stationary at its spawn row until the first fall step
        visualFromRow = anchorRow;
        fallingPieceGroup.setTranslateX(anchorColumn * cellSize);
        fallingPieceGroup.setTranslateY(anchorRow * cellSize);
        renderGrid();
        fallTimer.playFromStart();


        if (aiPlayer) {
            aiMoveApplied = false;
            pendingAIMove = evaluateLocalMove();
            aiRotationComplete = false;
            waitingForAIMove = false;
            fallTimer.setRate(1);
        }

        if (externalPlayer) {
            pendingAIMove = null;
            aiRotationComplete = false;
            waitingForAIMove = true;
            requestAIMove();
            fallTimer.setRate(1);
        }
    }

    private void movePieceDown() {
        if (paused || gameOverTriggered) {
            return;
        }

        if (aiPlayer) {
            applyLocalAIMove();
        }

        if (externalPlayer && !waitingForAIMove && pendingAIMove != null) {
            applyAIMove();
        }
        if (externalPlayer && waitingForAIMove && pendingAIMove == null) {
            if (!serverWarningShown) {
                serverWarningShown = true;

                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(
                            Alert.AlertType.ERROR,
                            "External Mode is enabled, but TetrisServer is not running." +
                                    "Pieces cannot move until the server is started."
                    );
                    alert.setTitle("TetrisServer Offline");
                    alert.setHeaderText("External Mode Warning");
                    alert.showAndWait();
                });
            }
        }

        int previousRow = pieceController.getCurrentPiece().getAnchorRow();

        if (pieceController.movePieceDown()) {
            // Sliding visually from the previous row to the new one over this fall step
            visualFromRow = previousRow;

            // Keep sending the current position so the server-side live mirror
            // follows both human and AI-controlled pieces.
            // For AI players, later returned OpMove values are ignored because
            // waitingForAIMove is already false after the spawn decision arrives.
            if (externalPlayer) {
                sendCurrentStateToServer();
            }
        } else {
            // Locking piece into grid once it can no longer move down
            lockPiece();
            playSound(LAND_SOUND);

            // Checking for completed rows and adding score
            int linesCleared = gameBoard.clearFullRows();

            if (linesCleared > 0) {
                playSound(LINE_CLEAR_SOUND);
                addScore(linesCleared);
            }

            fallTimer.playFromStart();
            // Spawning another random piece
            spawnPiece();
        }
    }
    private static boolean soundEffectsEnabled =
            GameSettings.load().isSoundEffectsEnabled();

    public static void setSoundEffectsEnabled(boolean enabled) {
        soundEffectsEnabled = enabled;
    }
    private void playSound(AudioClip sound) {
        if (soundEffectsEnabled) {
            sound.play();
        }
    }

    // Moving current piece left or right
    private void movePieceHorizontal(int direction) {
        // Preventing movement while paused, or once this board has topped out
        if (paused || gameOverTriggered) {
            return;
        }

        if (pieceController.movePieceHorizontal(direction)) {
            animateHorizontalMovement();
            if (externalPlayer) {
                sendCurrentStateToServer();
            }
        }
    }

    private void requestAIMove() {
        ActivePiece piece = pieceController.getCurrentPiece();
        if (piece == null) return;

        pendingServerSnapshot = new PureGame(
                playerName,
                fieldWidth,
                fieldHeight,
                gameBoard.getServerCells(),
                copyShape(piece.getCurrentPieceShape()),
                nextPieceType.createShape(),
                piece.getAnchorRow(),
                piece.getAnchorColumn()
        );
        waitingForAIMove = true;
        startServerWorkerIfNeeded();
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
                    OpMove returnedMove = TetrisClient.requestMove(snapshot);

                    System.out.println("[CLIENT] " + playerName + " received move -> target column="
                        + returnedMove.opX() + ", rotations=" + returnedMove.opRotate());

                    if (aiPlayer && waitingForAIMove) {
                        pendingAIMove = returnedMove;
                        waitingForAIMove = false;
                    }

                    if (externalPlayer && waitingForAIMove) {
                        pendingAIMove = returnedMove;
                        waitingForAIMove = false;
                    }
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

        // Removing separate falling visual because piece is now part of grid
        fallingPieceGroup.getChildren().clear();
        serverWarningShown = false;
        renderGrid();
    }

    // Updating score, level, and total lines whenever completed rows are cleared
    private void addScore(int linesCleared) {

        scoreManager.linesCleared(linesCleared);

        scoreLabel.setText(
                "Score: " + scoreManager.getScore()
        );

        levelLabel.setText(
                "Level: " + scoreManager.getLevel()
        );

        linesLabel.setText(
                "Lines Erased: " + scoreManager.getLinesErased()
        );
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
            rectangle.setStyle("-fx-fill: " + currentPiece.getCurrentPieceType().getColour() + ";"
                    + "-fx-stroke: black; -fx-stroke-width: 2;");

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

    // Positioning the falling piece between its previous and current row,
    // based on how far the fall timer is through its current step.
    // Because the position comes from the timer itself, soft drop, pausing
    // and restarting the timer are all reflected automatically.
    private void updateVerticalPosition() {
        ActivePiece piece = pieceController.getCurrentPiece();
        if (piece == null) {
            return;
        }

        double progress = fallTimer.getCurrentTime().toMillis() / FALL_INTERVAL_MS;
        progress = Math.max(0, Math.min(1, progress));

        double visualRow = visualFromRow + (piece.getAnchorRow() - visualFromRow) * progress;
        fallingPieceGroup.setTranslateY(visualRow * cellSize);
    }

    // Pausing visual movement of this player's falling piece
    private void pausePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.pause(); }
    }

    // Resuming visual movement of this player's falling piece
    private void resumePieceAnimations() {
        if (horizontalAnimation != null) { horizontalAnimation.play(); }
    }

    // Stopping current horizontal animation before locking or replacing piece
    private void stopPieceAnimations() {
        if (horizontalAnimation != null) {
            horizontalAnimation.stop();
            horizontalAnimation.setNode(null);
            horizontalAnimation = null;
        }
    }

    private static String createPieceStyle(String colour) {
        return "-fx-background-color: " + colour + ";"
                + "-fx-border-color: black; -fx-border-width: 2;";
    }

    private int findLandingRowLocal(int[][] cells, int[][] shape, int column) {
        int row = pieceController.getCurrentPiece().getAnchorRow();
        while (canPlaceLocal(cells, shape, row + 1, column)) {
            row++;
        }
        return canPlaceLocal(cells, shape, row, column) ? row : -1;
    }

    private boolean canPlaceLocal(int[][] cells, int[][] shape, int row, int column) {
        for (int[] block : shape) {
            int r = row + block[0];
            int c = column + block[1];

            if (r < 0 || r >= fieldHeight || c < 0 || c >= fieldWidth) return false;
            if (cells[r][c] != 0) return false;
        }
        return true;
    }

    private int[][] rotateShapeLocal(int[][] shape) {
        int[][] rotated = new int[shape.length][2];
        for (int i = 0; i < shape.length; i++) {
            rotated[i][0] = shape[i][1];
            rotated[i][1] = -shape[i][0];
        }
        return rotated;
    }

    private int[][] copyBoardLocal(int[][] board) {
        int[][] copy = new int[board.length][];

        for (int row = 0; row < board.length; row++) {
            copy[row] = board[row].clone();
        }

        return copy;
    }
    private void lockShapeLocal(int[][] board, int[][] shape, int row, int column) {
        for (int[] block : shape) {
            board[row + block[0]][column + block[1]] = 1;
        }
    }

    private int clearFullRowsLocal(int[][] board) {
        int height = board.length;
        int width = board[0].length;
        int linesCleared = 0;

        for (int row = height - 1; row >= 0; row--) {
            boolean full = true;

            for (int column = 0; column < width; column++) {
                if (board[row][column] == 0) {
                    full = false;
                    break;
                }
            }

            if (!full) {
                continue;
            }

            linesCleared++;

            for (int moveRow = row; moveRow > 0; moveRow--) {
                System.arraycopy(board[moveRow - 1], 0, board[moveRow], 0, width);
            }

            for (int column = 0; column < width; column++) {
                board[0][column] = 0;
            }

            row++;
        }

        return linesCleared;
    }


    private double evaluateBoardLocal(int[][] board, int cleared) {
        int holes = 0;
        int bump = 0;
        int[] colHeights = new int[fieldWidth];


        for (int x = 0; x < fieldWidth; x++) {
            boolean seenBlock = false;

            for (int y = 0; y < fieldHeight; y++) {
                if (board[y][x] != 0) {
                    seenBlock = true;
                    colHeights[x] = fieldHeight - y;
                } else if (seenBlock) {
                    holes++;
                }
            }
        }

        for (int x = 0; x < fieldWidth - 1; x++) {
            bump += Math.abs(colHeights[x] - colHeights[x + 1]);
        }

        int maxHeight = max(colHeights);

        return (120.0 * cleared)
                - (40.0 * maxHeight)
                - (15.0 * bump)
                - (80.0 * holes);
    }

    private int max(int[] arr) {
        int highest = 0;
        for (int v : arr) {
            if (v > highest) highest = v;
        }
        return highest;
    }

}

