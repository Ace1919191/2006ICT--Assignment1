package griffith.s5330916;

import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TetrisServer {
    private static final int PORT = 3000;

    // TRUE = open the live server mirror, FALSE = console-only server
    private static final boolean SHOW_SERVER_WINDOW = true;

    // TRUE prints every received state. Leave FALSE during normal play to avoid console spam.
    private static final boolean VERBOSE_STATE_LOGGING = false;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static ServerGameWindow serverGameWindow;

    private TetrisServer() { }

    public static void main(String[] args) {
        log("Starting TetrisServer...");

        if (SHOW_SERVER_WINDOW) {
            startServerWindow();
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log("SERVER READY");
            log("Listening on localhost:" + PORT);
            log("Waiting for client connections...");

            while (true) {
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                } catch (IOException e) {
                    logError("Client connection failed", e);
                }
            }
        } catch (IOException e) {
            logError("Could not start TetrisServer on port " + PORT
                    + ". The port may already be in use.", e);
        }
    }

    private static void startServerWindow() {
        CountDownLatch started = new CountDownLatch(1);

        Runnable createWindow = () -> {
            try {
                Platform.setImplicitExit(false);
                serverGameWindow = new ServerGameWindow();
                serverGameWindow.show();
                log("Live server mirror opened.");
            } finally {
                started.countDown();
            }
        };

        try {
            Platform.startup(createWindow);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(createWindow);
        }

        try {
            if (!started.await(5, TimeUnit.SECONDS)) {
                log("Server window did not initialise within 5 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("Interrupted while starting the server window.");
        }
    }

    private static void handleClient(Socket client) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream()), true)) {

            String requestJson = in.readLine();

            if (requestJson == null || requestJson.isBlank()) {
                return;
            }

            PureGame game = MAPPER.readValue(requestJson, PureGame.class);
            String source = normaliseSource(game.getSource());

            if (VERBOSE_STATE_LOGGING) {
                log(source + " state -> row=" + game.getAnchorRow()
                        + ", column=" + game.getAnchorColumn());
            }

            OpMove move = findOptimalMove(game);

            if (SHOW_SERVER_WINDOW && serverGameWindow != null) {
                Platform.runLater(() -> serverGameWindow.updateGame(game, move));
            }

            out.println(MAPPER.writeValueAsString(move));
        }
    }

    private static String normaliseSource(String source) {
        if (source == null || source.isBlank()) {
            return "Player";
        }

        if (source.startsWith("Player 1")) {
            return "Player 1";
        }

        if (source.startsWith("Player 2")) {
            return "Player 2";
        }

        return source;
    }

    private static OpMove findOptimalMove(PureGame game) {
        int bestColumn = game.getAnchorColumn();
        int bestRotation = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        int[][] shape = copyShape(game.getCurrentShape());

        for (int rotation = 0; rotation < 4; rotation++) {
            for (int column = 0; column < game.getWidth(); column++) {
                int landingRow = findLandingRow(game, shape, column);

                if (landingRow < 0) {
                    continue;
                }

                int[][] simulated = copyBoard(game.getCells());
                lockShape(simulated, shape, landingRow, column);

                int linesCleared = clearFullRows(simulated);
                double score = evaluateBoard(simulated, linesCleared);

                if (score > bestScore) {
                    bestScore = score;
                    bestColumn = column;
                    bestRotation = rotation;
                }
            }

            shape = rotateShape(shape);
        }

        return new OpMove(bestColumn, bestRotation);
    }

    private static int findLandingRow(PureGame game, int[][] shape, int anchorColumn) {
        int row = game.getAnchorRow();

        if (!canPlace(game.getCells(), game.getHeight(), game.getWidth(),
                shape, row, anchorColumn)) {
            return -1;
        }

        while (canPlace(game.getCells(), game.getHeight(), game.getWidth(),
                shape, row + 1, anchorColumn)) {
            row++;
        }

        return row;
    }

    private static boolean canPlace(int[][] cells, int height, int width,
                                    int[][] shape, int anchorRow, int anchorColumn) {
        for (int[] block : shape) {
            int row = anchorRow + block[0];
            int column = anchorColumn + block[1];

            if (row < 0 || row >= height || column < 0 || column >= width) {
                return false;
            }

            if (cells[row][column] != 0) {
                return false;
            }
        }
        return true;
    }

    private static int[][] rotateShape(int[][] shape) {
        int[][] rotated = new int[shape.length][2];

        for (int i = 0; i < shape.length; i++) {
            int rowOffset = shape[i][0];
            int columnOffset = shape[i][1];

            rotated[i][0] = columnOffset;
            rotated[i][1] = -rowOffset;
        }

        return rotated;
    }

    private static void lockShape(int[][] board, int[][] shape, int anchorRow, int anchorColumn) {
        for (int[] block : shape) {
            int row = anchorRow + block[0];
            int column = anchorColumn + block[1];
            board[row][column] = 1;
        }
    }

    private static int clearFullRows(int[][] board) {
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

    private static double evaluateBoard(int[][] board, int linesCleared) {
        int[] heights = calculateColumnHeights(board);
        int aggregateHeight = 0;

        for (int height : heights) {
            aggregateHeight += height;
        }

        int holes = countHoles(board);
        int bumpiness = calculateBumpiness(heights);

        return (linesCleared * 1.0)
                - (aggregateHeight * 0.510066)
                - (holes * 0.760666)
                - (bumpiness * 0.184483);
    }

    private static int[] calculateColumnHeights(int[][] board) {
        int height = board.length;
        int width = board[0].length;
        int[] heights = new int[width];

        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                if (board[row][column] != 0) {
                    heights[column] = height - row;
                    break;
                }
            }
        }

        return heights;
    }

    private static int countHoles(int[][] board) {
        int height = board.length;
        int width = board[0].length;
        int holes = 0;

        for (int column = 0; column < width; column++) {
            boolean blockSeen = false;

            for (int row = 0; row < height; row++) {
                if (board[row][column] != 0) {
                    blockSeen = true;
                } else if (blockSeen) {
                    holes++;
                }
            }
        }

        return holes;
    }

    private static int calculateBumpiness(int[] heights) {
        int bumpiness = 0;

        for (int i = 0; i < heights.length - 1; i++) {
            bumpiness += Math.abs(heights[i] - heights[i + 1]);
        }

        return bumpiness;
    }

    private static int[][] copyBoard(int[][] board) {
        int[][] copy = new int[board.length][];

        for (int row = 0; row < board.length; row++) {
            copy[row] = board[row].clone();
        }

        return copy;
    }

    private static int[][] copyShape(int[][] shape) {
        int[][] copy = new int[shape.length][2];

        for (int i = 0; i < shape.length; i++) {
            copy[i][0] = shape[i][0];
            copy[i][1] = shape[i][1];
        }

        return copy;
    }

    private static void log(String message) {
        System.out.println("[" + LocalDateTime.now() + "] [SERVER] " + message);
    }

    private static void logError(String message, Exception error) {
        System.err.println("[" + LocalDateTime.now() + "] [SERVER ERROR] " + message);
        System.err.println(error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    private static final class ServerGameWindow {
        private final Stage stage;
        private final HBox boardsContainer = new HBox(30);
        private final Map<String, BoardView> boardViews = new LinkedHashMap<>();

        private ServerGameWindow() {
            stage = new Stage();
            stage.setTitle("TetrisServer");

            Label title = new Label("TetrisServer");
            title.setStyle("-fx-text-fill: yellow; -fx-font-size: 24px; -fx-font-weight: bold;");

            boardsContainer.setAlignment(Pos.CENTER);

            VBox root = new VBox(15, title, boardsContainer);
            root.setAlignment(Pos.TOP_CENTER);
            root.setPadding(new Insets(15));
            root.setStyle("-fx-background-color: black;");

            stage.setScene(new Scene(root, 920, 760));
        }

        private void show() {
            stage.show();
        }

        private void updateGame(PureGame game, OpMove move) {
            String source = normaliseSource(game.getSource());
            BoardView boardView = boardViews.get(source);

            if (boardView == null) {
                boardView = new BoardView(source);
                boardViews.put(source, boardView);
                boardsContainer.getChildren().add(boardView.getView());
            }

            boardView.update(game, move);
        }
    }

    private static final class BoardView {
        private static final double BOARD_WIDTH = 330;
        private static final double BOARD_HEIGHT = 560;
        private static final double NEXT_SIZE = 100;

        private final String source;
        private final VBox view;
        private final Canvas boardCanvas = new Canvas(BOARD_WIDTH, BOARD_HEIGHT);
        private final Canvas nextCanvas = new Canvas(NEXT_SIZE, NEXT_SIZE);
        private final Label stateLabel = new Label("Waiting for state...");

        private BoardView(String source) {
            this.source = source;

            Label title = new Label(source);
            title.setStyle("-fx-text-fill: yellow; -fx-font-size: 18px; -fx-font-weight: bold;");

            stateLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

            Label nextLabel = new Label("Next");
            nextLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");

            HBox nextBox = new HBox(10, nextLabel, nextCanvas);
            nextBox.setAlignment(Pos.CENTER);

            view = new VBox(8, title, stateLabel, boardCanvas, nextBox);
            view.setAlignment(Pos.TOP_CENTER);
        }

        private VBox getView() {
            return view;
        }

        private void update(PureGame game, OpMove move) {
            stateLabel.setText("row=" + game.getAnchorRow() + "  column=" + game.getAnchorColumn());

            drawBoard(game);
            drawNextShape(game.getNextShape());
        }

        private void drawBoard(PureGame game) {
            GraphicsContext graphics = boardCanvas.getGraphicsContext2D();

            graphics.setFill(Color.BLACK);
            graphics.fillRect(0, 0, boardCanvas.getWidth(), boardCanvas.getHeight());

            int width = game.getWidth();
            int height = game.getHeight();
            int[][] cells = game.getCells();

            if (width <= 0 || height <= 0 || cells == null) {
                return;
            }

            double cellSize = Math.min((boardCanvas.getWidth() - 12) / width,
                    (boardCanvas.getHeight() - 12) / height);

            double gridWidth = cellSize * width;
            double gridHeight = cellSize * height;
            double offsetX = (boardCanvas.getWidth() - gridWidth) / 2.0;
            double offsetY = (boardCanvas.getHeight() - gridHeight) / 2.0;

            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    double x = offsetX + column * cellSize;
                    double y = offsetY + row * cellSize;

                    if (cells[row][column] != 0) {
                        graphics.setFill(Color.DARKGRAY);
                        graphics.fillRect(x, y, cellSize, cellSize);
                    }

                    graphics.setStroke(Color.rgb(80, 80, 80));
                    graphics.strokeRect(x, y, cellSize, cellSize);
                }
            }

            int[][] shape = game.getCurrentShape();

            if (shape == null) {
                return;
            }

            for (int[] block : shape) {
                int row = game.getAnchorRow() + block[0];
                int column = game.getAnchorColumn() + block[1];

                if (row < 0 || row >= height || column < 0 || column >= width) {
                    continue;
                }

                double x = offsetX + column * cellSize;
                double y = offsetY + row * cellSize;

                graphics.setFill(Color.CYAN);
                graphics.fillRect(x, y, cellSize, cellSize);
                graphics.setStroke(Color.BLACK);
                graphics.strokeRect(x, y, cellSize, cellSize);
            }
        }

        private void drawNextShape(int[][] shape) {
            GraphicsContext graphics = nextCanvas.getGraphicsContext2D();

            graphics.setFill(Color.BLACK);
            graphics.fillRect(0, 0, nextCanvas.getWidth(), nextCanvas.getHeight());
            graphics.setStroke(Color.DARKGRAY);
            graphics.strokeRect(0, 0, nextCanvas.getWidth(), nextCanvas.getHeight());

            if (shape == null || shape.length == 0) {
                return;
            }

            int minRow = Integer.MAX_VALUE;
            int maxRow = Integer.MIN_VALUE;
            int minColumn = Integer.MAX_VALUE;
            int maxColumn = Integer.MIN_VALUE;

            for (int[] block : shape) {
                minRow = Math.min(minRow, block[0]);
                maxRow = Math.max(maxRow, block[0]);
                minColumn = Math.min(minColumn, block[1]);
                maxColumn = Math.max(maxColumn, block[1]);
            }

            int shapeHeight = maxRow - minRow + 1;
            int shapeWidth = maxColumn - minColumn + 1;
            double cellSize = Math.min(24, Math.min(80.0 / shapeWidth, 80.0 / shapeHeight));

            double totalWidth = shapeWidth * cellSize;
            double totalHeight = shapeHeight * cellSize;
            double offsetX = (nextCanvas.getWidth() - totalWidth) / 2.0;
            double offsetY = (nextCanvas.getHeight() - totalHeight) / 2.0;

            for (int[] block : shape) {
                double x = offsetX + (block[1] - minColumn) * cellSize;
                double y = offsetY + (block[0] - minRow) * cellSize;

                graphics.setFill(Color.ORANGE);
                graphics.fillRect(x, y, cellSize, cellSize);
                graphics.setStroke(Color.BLACK);
                graphics.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }
}
