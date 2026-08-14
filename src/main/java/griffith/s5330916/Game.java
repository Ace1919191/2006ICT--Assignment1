package griffith.s5330916;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Game {
    // Defining location of settings JSON file
    private static final Path SETTINGS_FILE =
            Paths.get("src", "main", "resources", "settings.json");

    // Defining original Tetris piece using positions relative to the anchor block
    private static final int[][] PIECE_SHAPE = {
            {0, 0},
            {0, -1},
            {0, 1},
            {1, 0}
    };
    // Current shape is separate so the piece can be rotated
    private int[][] currentPieceShape;

    // Defining styles used by cells in the Tetris grid
    private static final String EMPTY_CELL_STYLE =
            "-fx-background-color: black;" +
                    "-fx-border-color: #555;" +
                    "-fx-border-width: 0.5;";

    private static final String FALLING_CELL_STYLE =
            "-fx-background-color: yellow;" +
                    "-fx-border-color: #555;" +
                    "-fx-border-width: 0.5;";

    private static final String LOCKED_CELL_STYLE = "-fx-background-color: #555;" +
                    "-fx-border-color: black;" +
                    "-fx-border-width: 0.5;";

    private final Stage stage;
    private final Runnable onBack;

    // Grid size is loaded from settings.json
    private int fieldHeight;
    private int fieldWidth;

    // Each Pane represents one visible space in the Tetris grid
    private Pane[][] gridCells;

    // Keeping track of blocks which have already landed
    private boolean[][] lockedBlocks;

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
        lockedBlocks = new boolean[fieldHeight][fieldWidth];

        // Creating title for Game Screen
        Label titleLabel = new Label("Tetris");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: yellow;"
        );

        // Creating GridPane which will contain all Tetris cells
        GridPane gameGrid = new GridPane();
        gameGrid.setAlignment(Pos.CENTER);

        // Calculating cell size so larger fields still fit inside the window
        double cellSize = Math.min(450.0 / fieldHeight, 600.0 / fieldWidth);

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
        // Creating status label for displaying Game Over later
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px;"
        );
        // Creating Back Button for Game Screen
        Button backButton = new Button("Back");
        String menuButtonStyle = "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";

        backButton.setStyle(menuButtonStyle);

        // Returning user back to Main Menu and stopping game timer
        backButton.setOnAction(ignored -> {
            fallTimer.stop();
            onBack.run();
        });
        // VBox holds the Game Screen vertically
        VBox gameLayout = new VBox(15);

        gameLayout.setAlignment(Pos.CENTER);
        gameLayout.setPadding(new Insets(20));
        gameLayout.setStyle("-fx-background-color: black;");
        gameLayout.getChildren().addAll(titleLabel, gameGrid, statusLabel, backButton);

        // Creating Scene for Game Screen
        Scene gameScene = new Scene(gameLayout, 800, 600
        );

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
                            movePieceDown();
                            fallTimer.setRate(5);
                            event.consume();
                            break;
                    }
                }
        );

        // Detecting when Down or S is released
        gameScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
                    switch (event.getCode()) {
                        case DOWN:
                        case S:
                            // Returning falling speed back to normal
                            fallTimer.setRate(1);
                            event.consume();
                            break;
                    }
                }
        );

        // Rendering Game Scene onto existing Stage
        stage.setScene(gameScene);

        // Spawning first Tetris piece
        spawnPiece();

        // Starting automatic falling
        fallTimer.play();
    }

    // Spawning new piece at top centre of Tetris grid
    private void spawnPiece() {
        anchorRow = 0;
        anchorColumn = fieldWidth / 2;

        // Resetting piece back to its original rotation
        currentPieceShape = copyShape(PIECE_SHAPE);

        // Ending game if new piece cannot fit onto grid
        if (!canPlacePiece(anchorRow, anchorColumn)) {
            fallTimer.stop();
            statusLabel.setText("Game Over");
            return;
        }
        renderGrid();
    }

    // Moving current piece down one grid space
    private void movePieceDown() {
        int nextRow = anchorRow + 1;
        // Moving piece if next position is available
        if (canPlacePiece(nextRow, anchorColumn)) {
            anchorRow = nextRow;
            renderGrid();
        } else {
            // Locking piece into grid once it can no longer move down
            lockPiece();
            // Spawning another piece after previous piece has landed
            spawnPiece();
        }
    }

    // Moving current piece left or right
    private void movePieceHorizontal(int direction) {
        int nextColumn = anchorColumn + direction;
        // Moving piece if new horizontal position is available
        if (canPlacePiece(anchorRow, nextColumn)) {
            anchorColumn = nextColumn;
            renderGrid();
        }
    }

    // Rotating current piece 90 degrees clockwise around anchor block
    private void rotatePiece() {
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

            renderGrid();
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
            if (lockedBlocks[row][column]) {
                return false;
            }
        }
        return true;
    }

    // Converting falling piece into locked blocks
    private void lockPiece() {
        for (int[] block : currentPieceShape) {
            int row = anchorRow + block[0];
            int column = anchorColumn + block[1];

            lockedBlocks[row][column] = true;
        }
        renderGrid();
    }

    // Updating appearance of entire Tetris grid
    private void renderGrid() {
        // Resetting cells to either empty or previously landed blocks
        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                if (lockedBlocks[row][column]) {
                    gridCells[row][column].setStyle(LOCKED_CELL_STYLE);
                } else {
                    gridCells[row][column].setStyle(EMPTY_CELL_STYLE);
                }
            }
        }

        // Drawing currently falling piece using anchor position
        for (int[] block : currentPieceShape) {
            int row = anchorRow + block[0];
            int column = anchorColumn + block[1];
            if (row >= 0 && row < fieldHeight && column >= 0 && column < fieldWidth) {
                gridCells[row][column].setStyle(
                        FALLING_CELL_STYLE
                );
            }
        }
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