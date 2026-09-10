package griffith.s5330916;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Game {

    private final Stage stage;
    private final Runnable onBack;

    // Whether this match is being played with one board or two boards side by side
    private boolean twoPlayerMode;

    // Each player runs on their own independent board.
    // playerTwo stays null for the entire match when running in single player mode.
    private PlayerBoard playerOne;
    private PlayerBoard playerTwo;

    private boolean paused = false;

    // Preventing the win/tie message from being overwritten once decided
    private boolean roundEnded = false;

    private Label overallStatusLabel;

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
        // Reading current grid size and player count from settings.json
        GameSettings settings = GameSettings.load();
        int fieldHeight = settings.getFieldHeight();
        int fieldWidth = settings.getFieldWidth();
        twoPlayerMode = settings.isTwoPlayerMode();

        // Creating title for Game Screen, reflecting the selected player count
        Label titleLabel = new Label(twoPlayerMode ? "Tetris - 2 Player" : "Tetris - 1 Player");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        // Shared piece generator so that, in Two Player Mode, both boards
        // receive the exact same sequence of pieces
        PieceSequence pieceSequence = new PieceSequence();

        // Creating player one's board. In single player mode this board accepts
        // both WASD and Arrow Key controls so either control scheme works.
        String playerOneLabel = twoPlayerMode ? "Player 1 (WASD)" : "Player 1";
        playerOne = new PlayerBoard(playerOneLabel, fieldHeight, fieldWidth, this::handleGameOver, pieceSequence);

        // Only creating a second board when Two Player Mode is enabled
        HBox boardsLayout;
        if (twoPlayerMode) {
            playerTwo = new PlayerBoard("Player 2 (Arrow Keys)", fieldHeight, fieldWidth, this::handleGameOver,
                    pieceSequence);
            boardsLayout = new HBox(60, playerOne.getView(), playerTwo.getView());
        } else {
            playerTwo = null;
            boardsLayout = new HBox(playerOne.getView());
        }
        boardsLayout.setAlignment(Pos.CENTER);

        // Label used to display Paused or the eventual match result
        overallStatusLabel = new Label("");
        overallStatusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 18px; -fx-font-weight: bold;");

        // Creating Back Button for Game Screen
        Button backButton = new Button("Back");
        String menuButtonStyle = "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";
        backButton.setStyle(menuButtonStyle);

        // Pausing the board(s) and asking user to confirm returning to Main Menu
        backButton.setOnAction(ignored -> {
            // Remembering whether the game was already paused before Back was pressed
            boolean wasPaused = paused;

            pauseAll();
            overallStatusLabel.setText("Paused");

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
                // Stopping the board(s) completely before returning to Main Menu
                stopAll();
                onBack.run();
            } else if (!wasPaused) {
                // Resuming the board(s) if the game was running before Back was pressed
                resumeAll();
                overallStatusLabel.setText("");
            }
        });

        // Creating empty space between status and Back Button
        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        // Creating bottom section of Game Screen
        HBox bottomLayout = new HBox(20, overallStatusLabel, bottomSpacer, backButton);
        bottomLayout.setAlignment(Pos.CENTER);
        bottomLayout.setPadding(new Insets(0, 20, 0, 20));
        bottomLayout.setMaxWidth(Double.MAX_VALUE);

        // VBox holds the Game Screen vertically
        VBox gameLayout = new VBox(20);
        gameLayout.setAlignment(Pos.CENTER);
        gameLayout.setPadding(new Insets(20));
        gameLayout.setStyle("-fx-background-color: black;");
        gameLayout.getChildren().addAll(titleLabel, boardsLayout, bottomLayout);

        // Creating Scene for Game Screen. A single board needs less width than two.
        double sceneWidth = twoPlayerMode ? 1000 : 550;
        Scene gameScene = new Scene(gameLayout, sceneWidth, 700);

        // Routing keyboard controls.
        // Two Player Mode: Player 1 uses WASD, Player 2 uses Arrow Keys.
        // Single Player Mode: Player 1 responds to both WASD and Arrow Keys.
        gameScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                // Player 1 controls (always active)
                case A:
                    playerOne.moveLeft();
                    break;
                case D:
                    playerOne.moveRight();
                    break;
                case W:
                    playerOne.rotate();
                    break;
                case S:
                    playerOne.setSoftDrop(true);
                    break;

                // Player 2 controls in Two Player Mode; fall back to controlling
                // Player 1 in Single Player Mode so Arrow Keys also work.
                case LEFT:
                    (twoPlayerMode ? playerTwo : playerOne).moveLeft();
                    break;
                case RIGHT:
                    (twoPlayerMode ? playerTwo : playerOne).moveRight();
                    break;
                case UP:
                    (twoPlayerMode ? playerTwo : playerOne).rotate();
                    break;
                case DOWN:
                    (twoPlayerMode ? playerTwo : playerOne).setSoftDrop(true);
                    break;

                // Pausing or resuming the board(s) together
                case P:
                    togglePauseAll();
                    break;

                default:
                    break;
            }
            event.consume();
        });

        // Detecting when a soft drop key is released
        gameScene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            switch (event.getCode()) {
                case S:
                    playerOne.setSoftDrop(false);
                    break;
                case DOWN:
                    (twoPlayerMode ? playerTwo : playerOne).setSoftDrop(false);
                    break;
                default:
                    break;
            }
            event.consume();
        });

        // Rendering Game Scene onto existing Stage
        stage.setScene(gameScene);

        // Starting the board(s) at the same time
        playerOne.start();
        if (twoPlayerMode) {
            playerTwo.start();
        }
    }

    // Pausing or resuming the board(s) together
    private void togglePauseAll() {
        if (roundEnded) {
            return;
        }

        if (paused) {
            resumeAll();
            overallStatusLabel.setText("");
        } else {
            pauseAll();
            overallStatusLabel.setText("Paused");
        }
    }

    private void pauseAll() {
        paused = true;
        playerOne.pause();
        if (twoPlayerMode) {
            playerTwo.pause();
        }
    }

    private void resumeAll() {
        paused = false;
        playerOne.resume();
        if (twoPlayerMode) {
            playerTwo.resume();
        }
    }

    private void stopAll() {
        playerOne.stop();
        if (twoPlayerMode) {
            playerTwo.stop();
        }
    }

    // Called when a board tops out; ends the match
    private void handleGameOver() {
        if (roundEnded) {
            return;
        }
        roundEnded = true;

        // Stopping every board as soon as one player tops out
        stopAll();

        if (!twoPlayerMode) {
            overallStatusLabel.setText("Game Over - Score: " + playerOne.getScore());
            return;
        }

        int scoreOne = playerOne.getScore();
        int scoreTwo = playerTwo.getScore();

        if (scoreOne > scoreTwo) {
            overallStatusLabel.setText("Game Over - Player 1 Wins!");
        } else if (scoreTwo > scoreOne) {
            overallStatusLabel.setText("Game Over - Player 2 Wins!");
        } else {
            overallStatusLabel.setText("Game Over - It's a Tie!");
        }
    }
}