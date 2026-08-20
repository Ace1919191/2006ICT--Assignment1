package griffith.s5330916;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Game {
    private static final Path SETTINGS_FILE =
            Paths.get("src", "main", "resources", "settings.json");

    private int score = 0;
    private Label scoreLabel;

    private boolean paused = false;

    // Keeping track of whether player is currently accelerating the piece
    private boolean softDropActive = false;

    // Defining original Tetris piece using positions relative to the anchor block
    // Defining all Tetris piece shapes and their colours

    /*
    The way that storing a tetris piece's block data works is as follows:
    Within the piece data's array each value set represents a "block" of the piece,
    the numbers represents the offset of that block from the "anchor" piece,
    so for the I piece it translates to:
        1. One block to the left            {0, -1}
        2. *Anchor Block                    {0, 0}
        3. One block to the right           {0, 1}
        4.One block 2 spaces to the right   {0, 2}
        [][*][][]
     */

    private enum PieceType {
        I(new int[][]{
                {0, -1},
                {0, 0},
                {0, 1},
                {0, 2}
        }, "cyan", true),

        O(new int[][]{
                {0, 0},
                {0, 1},
                {1, 0},
                {1, 1}
        }, "yellow", false),

        T(new int[][]{
                {0, -1},
                {0, 0},
                {0, 1},
                {1, 0}
        }, "purple", true),

        L(new int[][]{
                {-1, 1},
                {0, -1},
                {0, 0},
                {0, 1}
        }, "orange", true),

        J(new int[][]{
                {-1, -1},
                {0, -1},
                {0, 0},
                {0, 1}
        }, "blue", true),

        S(new int[][]{
                {0, 0},
                {0, 1},
                {1, -1},
                {1, 0}
        }, "green", true),

        Z(new int[][]{
                {0, -1},
                {0, 0},
                {1, 0},
                {1, 1}
        }, "red", true);

        private final int[][] shape;
        private final String colour;
        private final boolean rotatable;

        PieceType(int[][] shape, String colour, boolean rotatable) {
            this.shape = shape;
            this.colour = colour;
            this.rotatable = rotatable;
        }
    }

    private final Random random = new Random();

    private PieceType currentPieceType;
    private int[][] currentPieceShape;

    private PieceType[][] lockedBlocks;

    private double cellSize;

    // Separate visual layer for currently falling piece
    private Pane pieceOverlay;
    private Group fallingPieceGroup = new Group();

    // Animations used when moving the falling piece
    private TranslateTransition horizontalAnimation;
    private TranslateTransition verticalAnimation;

    // Defining styles used by cells in the Tetris grid
    private static final String EMPTY_CELL_STYLE =
            "-fx-background-color: black;" +
            "-fx-border-color: #555;" +
            "-fx-border-width: 0.5;";

    private final Stage stage;
    private final Runnable onBack;

    // Grid size is loaded from settings.json
    private int fieldHeight;
    private int fieldWidth;

    // Each Pane represents one visible space in the Tetris grid
    private Pane[][] gridCells;

    // Defining current position of the anchor block
    private int anchorRow;
    private int anchorColumn;

    // Timer controls how often the piece falls
    private Timeline fallTimer;

    private Label statusLabel;

    public Game(Stage stage, Runnable onBack) {
        this.stage = stage;
        this.onBack = onBack;
    }

    public static void show(Stage stage, Runnable onBack) {
        // Creating Game Screen Object and displaying it
        Game game = new Game(stage, onBack);
        game.showGame();
    }

    private void showGame() {
        // Reading current grid size from settings.json
        String json = readSettingsFile();

        fieldHeight = getInt(json, "fieldLength", 20);
        fieldWidth = getInt(json, "fieldWidth", 10);

        // Creating arrays using selected field dimensions
        gridCells = new Pane[fieldHeight][fieldWidth];
        lockedBlocks = new PieceType[fieldHeight][fieldWidth];

        // Creating title for Game Screen
        Label titleLabel = new Label("Tetris");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: yellow;"
        );

        // Creating GridPane which will contain all Tetris cells
        GridPane gameGrid = new GridPane();
        gameGrid.setAlignment(Pos.CENTER);

        // Calculating cell size so larger fields still fit inside the window
        cellSize = Math.min(450.0 / fieldHeight, 600.0 / fieldWidth);

        // Preventing cells from becoming unnecessarily large
        cellSize = Math.min(cellSize, 30);

        // Creating each individual cell in the Tetris grid
        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                Pane cell = new Pane();

                cell.setMinSize(cellSize, cellSize);
                cell.setPrefSize(cellSize, cellSize);
                cell.setMaxSize(cellSize, cellSize);
                cell.setStyle(EMPTY_CELL_STYLE);

                gridCells[row][column] = cell;
                gameGrid.add(cell, column, row
                );
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

        // Placing falling piece layer directly over Tetris grid
        StackPane gameBoard = new StackPane(gameGrid, pieceOverlay);
        gameBoard.setAlignment(Pos.CENTER);
        gameBoard.setMinSize(boardWidth, boardHeight);
        gameBoard.setPrefSize(boardWidth, boardHeight);
        gameBoard.setMaxSize(boardWidth, boardHeight);

        // Creating status label for displaying Game Over later
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px;");

        // Creating score label for displaying current player score
        scoreLabel = new Label("Score: 0");
        scoreLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px; -fx-font-weight: bold;"
        );

        // Creating Back Button for Game Screen
        Button backButton = new Button("Back");
        String menuButtonStyle = "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";

        backButton.setStyle(menuButtonStyle);

        // Pausing game and asking user to confirm returning to Main Menu
        backButton.setOnAction(ignored -> {
            // Remembering whether game was already paused before Back was pressed
            boolean wasPaused = paused;

            // Pausing game while confirmation popup is displayed
            paused = true;
            fallTimer.pause();
            pausePieceAnimations();
            statusLabel.setText("Paused");

            // Creating confirmation popup
            Alert confirmation = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to return to the Main Menu?",
                    ButtonType.YES,
                    ButtonType.NO
            );
            confirmation.setTitle("Return to Main Menu");
            confirmation.setHeaderText("Exit Current Game?");
            confirmation.initOwner(stage);
            ButtonType result = confirmation.showAndWait().orElse(ButtonType.NO);
            if (result == ButtonType.YES) {
                // Stopping game completely before returning to Main Menu
                fallTimer.stop();
                stopPieceAnimations();
                softDropActive = false;
                onBack.run();
            } else if (!wasPaused) {
                // Resuming game if it was running before Back was pressed
                paused = false;
                statusLabel.setText("");
                resumePieceAnimations();
                fallTimer.play();
            }
        });

        // Creating empty space between Score and Back Button
        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        // Creating bottom section of Game Screen
        HBox bottomLayout = new HBox(20, scoreLabel, bottomSpacer, backButton);
        bottomLayout.setAlignment(Pos.CENTER);
        bottomLayout.setPadding(new Insets(0, 20, 0, 20));
        bottomLayout.setMaxWidth(Double.MAX_VALUE);

        // VBox holds the Game Screen vertically
        VBox gameLayout = new VBox(15);

        gameLayout.setAlignment(Pos.CENTER);
        gameLayout.setPadding(new Insets(20));
        gameLayout.setStyle("-fx-background-color: black;");
        gameLayout.getChildren().addAll(titleLabel, gameBoard, statusLabel, bottomLayout);

        // Creating Scene for Game Screen
        Scene gameScene = new Scene(gameLayout, 800, 600);

        // Creating timer which moves current piece down every half second
        fallTimer = new Timeline(new KeyFrame(Duration.millis(500), ignored -> movePieceDown()));
        fallTimer.setCycleCount(Animation.INDEFINITE);

        // Detecting keyboard controls while Game Screen is open
        gameScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                // Moving piece left
                case LEFT:
                case A:
                    movePieceHorizontal(-1);
                    event.consume();
                    break;

                // Moving piece right
                case RIGHT:
                case D:
                    movePieceHorizontal(1);
                    event.consume();
                    break;

                // Rotating piece clockwise
                case UP:
                case W:
                    rotatePiece();
                    event.consume();
                    break;

                // Accelerating piece downwards
                case DOWN:
                case S:
                    // Preventing auto-repeat on key hold
                    if (!softDropActive) {
                        softDropActive = true;
                        fallTimer.setRate(5);
                        movePieceDown();
                    }

                    event.consume();
                    break;

                // Pausing or resuming game
                case P:
                    togglePause();
                    event.consume();
                    break;
            }
        });

        // Detecting when Down or S is released
        gameScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            switch (event.getCode()) {
                case DOWN:
                case S:
                    // Returning falling speed back to normal
                    softDropActive = false;
                    fallTimer.setRate(1);
                    event.consume();
                    break;
            }
        });

        // Rendering Game Scene onto existing Stage
        stage.setScene(gameScene);

        // Spawning first Tetris piece
        spawnPiece();

        // Starting automatic falling
        fallTimer.play();
    }

    // Pausing or resuming the game
    private void togglePause() {
        paused = !paused;
        if (paused) {
            fallTimer.pause();
            statusLabel.setText("Paused");

        } else {
            fallTimer.play();
            statusLabel.setText("");
        }
    }

    // Checking entire grid for completed rows
    private int clearFullRows() {
        int linesCleared = 0;
        // Starting from bottom because rows above will move down
        for (int row = fieldHeight - 1; row >= 0; row--) {
            if (isRowFull(row)) {
                removeRow(row);
                linesCleared++;
                // Checking same row again because another row has moved into it
                row++;
            }
        }
        return linesCleared;
    }

    // Checking if every grid space in a row contains a block
    private boolean isRowFull(int row) {
        for (int column = 0; column < fieldWidth; column++) {
            if (lockedBlocks[row][column] == null) {
                return false;
            }
        }
        return true;
    }

    // Removing completed row and moving all rows above down by one
    private void removeRow(int completedRow) {
        // Moving every row above the completed row down one position
        for (int row = completedRow; row > 0; row--) {
            if (fieldWidth >= 0) System.arraycopy(lockedBlocks[row - 1], 0, lockedBlocks[row], 0, fieldWidth);
        }
        // Clearing the new top row
        for (int column = 0; column < fieldWidth; column++) {
            lockedBlocks[0][column] = null;
        }
    }

    // Spawning new piece at top centre of Tetris grid
    private void spawnPiece() {
        stopPieceAnimations();

        // Removing visual object belonging to previous piece
        pieceOverlay.getChildren().remove(fallingPieceGroup);

        // Creating completely new visual object for spawned piece
        fallingPieceGroup = new Group();

        pieceOverlay.getChildren().add(fallingPieceGroup);

        anchorRow = 1;
        anchorColumn = fieldWidth / 2;

        // Selecting random Tetris piece
        PieceType[] availablePieces = PieceType.values();
        currentPieceType = availablePieces[random.nextInt(availablePieces.length)];
        // Creating copy of selected shape so it can be rotated
        currentPieceShape = copyShape(currentPieceType.shape);

        // Ending game if new piece cannot fit onto grid
        if (!canPlacePiece(anchorRow, anchorColumn)) {
            fallTimer.stop();
            stopPieceAnimations();
            fallingPieceGroup.getChildren().clear();
            statusLabel.setText("Game Over");
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
        // Preventing movement while game is paused
        if (paused) {
            return;
        }
        int nextRow = anchorRow + 1;
        // Moving piece if next position is available
        if (canPlacePiece(nextRow, anchorColumn)) {
            anchorRow = nextRow;
            animateVerticalMovement();
        } else {
            // Locking piece into grid once it can no longer move down
            lockPiece();

            // Checking for completed rows and adding score
            int linesCleared = clearFullRows();
            addScore(linesCleared);

            // Spawning another random piece
            spawnPiece();
        }
    }

    // Moving current piece left or right
    private void movePieceHorizontal(int direction) {
        // Preventing movement while game is paused
        if (paused) {
            return;
        }
        int nextColumn = anchorColumn + direction;
        // Moving piece if new horizontal position is available
        if (canPlacePiece(anchorRow, nextColumn)) {
            anchorColumn = nextColumn;
            animateHorizontalMovement();
        }
    }

    // Rotating current piece 90 degrees clockwise around anchor block
    private void rotatePiece() {
        // Preventing movement while game is paused
        if (paused) {
            return;
        }

        // Square piece does not need to be rotated
        if (!currentPieceType.rotatable) {
            return;
        }

        int[][] rotatedShape = new int[currentPieceShape.length][2];
        for (int i = 0; i < currentPieceShape.length; i++) {
            int rowOffset = currentPieceShape[i][0];
            int columnOffset = currentPieceShape[i][1];

            // Rotating row and column offsets 90 degrees clockwise
            rotatedShape[i][0] = columnOffset;
            rotatedShape[i][1] = -rowOffset;
        }

        // Only applying rotation if rotated piece fits on grid
        if (canPlacePiece(anchorRow, anchorColumn, rotatedShape)) {
            currentPieceShape = rotatedShape;

            // Rebuilding falling piece using its new rotated shape
            updateFallingPieceShape();
        }
    }

    // Checking if piece can exist at specified anchor position
    // Checking if current piece can exist at specified anchor position
    private boolean canPlacePiece(
            int testAnchorRow,
            int testAnchorColumn) {
        return canPlacePiece(testAnchorRow, testAnchorColumn, currentPieceShape);
    }

    // Checking if specified piece shape can exist at anchor position
    private boolean canPlacePiece(int testAnchorRow, int testAnchorColumn, int[][] pieceShape) {
        for (int[] block : pieceShape) {
            int row = testAnchorRow + block[0];
            int column = testAnchorColumn + block[1];

            // Checking if block would leave the grid
            if (row < 0 || row >= fieldHeight || column < 0 || column >= fieldWidth) {
                return false;
            }

            // Checking if block would collide with landed piece
            if (lockedBlocks[row][column] != null) {
                return false;
            }
        }
        return true;
    }

    // Converting falling piece into locked blocks
    private void lockPiece() {
        // Stopping visual movement before converting piece into locked blocks
        stopPieceAnimations();

        for (int[] block : currentPieceShape) {
            int row = anchorRow + block[0];
            int column = anchorColumn + block[1];

            // Saving Piece Type so placed block retains its colour
            lockedBlocks[row][column] = currentPieceType;
        }

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
        }
        // Updating displayed score
        scoreLabel.setText("Score: " + score);
    }

    // Updating appearance of entire Tetris grid
    private void renderGrid() {
        // Resetting cells to either empty or previously landed blocks
        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                PieceType lockedPiece = lockedBlocks[row][column];
                if (lockedPiece != null) {
                    gridCells[row][column].setStyle(createPieceStyle(lockedPiece.colour));
                } else {
                    gridCells[row][column].setStyle(EMPTY_CELL_STYLE);
                }
            }
        }

    }

    // Creating visual blocks for currently falling Tetris piece
    private void updateFallingPieceShape() {
        fallingPieceGroup.getChildren().clear();

        for (int[] block : currentPieceShape) {
            Rectangle rectangle = new Rectangle(cellSize, cellSize);

            // Positioning block relative to anchor block
            rectangle.setX(block[1] * cellSize);
            rectangle.setY(block[0] * cellSize);

            // Applying piece colour and border to individual block
            rectangle.setStyle("-fx-fill: " + currentPieceType.colour + ";" +
                "-fx-stroke: black;  -fx-stroke-width: 2;");

            fallingPieceGroup.getChildren().add(rectangle);
        }
    }

    // Smoothly moving falling piece left or right
    private void animateHorizontalMovement() {
        if (horizontalAnimation != null) {
            horizontalAnimation.stop();
        }

        horizontalAnimation = new TranslateTransition(
                Duration.millis(80),
                fallingPieceGroup
        );

        horizontalAnimation.setToX(anchorColumn * cellSize);
        horizontalAnimation.setInterpolator(Interpolator.EASE_BOTH);
        horizontalAnimation.play();
    }

    // Smoothly moving falling piece down towards its next grid position
    private void animateVerticalMovement() {
        if (verticalAnimation != null) {
            verticalAnimation.stop();
        }

        double animationTime = 480;

        // Shortening animation while Down or S is accelerating the piece
        if (fallTimer.getRate() > 1) {animationTime = 90;}

        verticalAnimation = new TranslateTransition(Duration.millis(animationTime), fallingPieceGroup);

        verticalAnimation.setToY(anchorRow * cellSize);
        verticalAnimation.setInterpolator(Interpolator.LINEAR);
        verticalAnimation.play();
    }

    // Pausing visual movement of current falling piece
    private void pausePieceAnimations() {
        if (horizontalAnimation != null) {horizontalAnimation.pause();}
        if (verticalAnimation != null) {verticalAnimation.pause();}
    }

    // Resuming visual movement of current falling piece
    private void resumePieceAnimations() {
        if (horizontalAnimation != null) {horizontalAnimation.play();}
        if (verticalAnimation != null) {verticalAnimation.play();}
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

    // Creating separate copy of piece shape so it can be rotated
    private static int[][] copyShape(int[][] shape) {
        int[][] copiedShape = new int[shape.length][2];
        for (int i = 0; i < shape.length; i++) {
            copiedShape[i][0] = shape[i][0];
            copiedShape[i][1] = shape[i][1];
        }
        return copiedShape;
    }

    // Reading contents of settings JSON file
    private static String readSettingsFile() {
        try {
            return Files.readString(SETTINGS_FILE);
        } catch (IOException e) {
            System.err.println("Could not read settings.json: " + e.getMessage());
            return "";
        }
    }

    // Finding integer setting from JSON and using default if it cannot be found
    private static int getInt(String json, String key, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }
}