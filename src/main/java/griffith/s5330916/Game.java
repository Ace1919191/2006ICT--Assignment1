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

    private PlayerBoard playerOne;
    private PlayerBoard playerTwo;

    private boolean paused = false;
    private boolean roundEnded = false;
    private boolean playerOneEnded = false;
    private boolean playerTwoEnded = false;

    private Label overallStatusLabel;

    public Game(Stage stage, Runnable onBack) {
        this.stage = stage;
        this.onBack = onBack;
    }

    public static void show(Stage stage, Runnable onBack) {
        Game game = new Game(stage, onBack);
        game.showGame();
    }

    private void showGame() {
        GameSettings settings = GameSettings.load();
        int fieldHeight = settings.getFieldHeight();
        int fieldWidth = settings.getFieldWidth();
        twoPlayerMode = settings.isTwoPlayerMode();

        Label titleLabel = new Label(twoPlayerMode ? "Tetris - 2 Player" : "Tetris - 1 Player");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        PieceSequence pieceSequence = new PieceSequence();

        String playerOneLabel = twoPlayerMode ? "Player 1 (WASD)" : "Player 1";
        playerOne = new PlayerBoard(playerOneLabel, fieldHeight, fieldWidth,
                () -> handlePlayerGameOver(true), pieceSequence);

        HBox boardsLayout;
        if (twoPlayerMode) {
            playerTwo = new PlayerBoard("Player 2 (Arrow Keys)", fieldHeight, fieldWidth,
                    () -> handlePlayerGameOver(false), pieceSequence);
            boardsLayout = new HBox(60, playerOne.getView(), playerTwo.getView());
        } else {
            playerTwo = null;
            boardsLayout = new HBox(playerOne.getView());
        }
        boardsLayout.setAlignment(Pos.CENTER);

        overallStatusLabel = new Label("");
        overallStatusLabel.setStyle("-fx-text-fill: yellow; -fx-font-size: 18px; -fx-font-weight: bold;");

        Button backButton = new Button("Back");
        String menuButtonStyle = "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";
        backButton.setStyle(menuButtonStyle);

        backButton.setOnAction(ignored -> {
            if (roundEnded) {
                stopAll();
                onBack.run();
                return;
            }

            boolean wasPaused = paused;
            pauseAll();
            overallStatusLabel.setText("Paused");

            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to return to the Main Menu?", ButtonType.YES, ButtonType.NO);

            confirmation.setTitle("Return to Main Menu");
            confirmation.setHeaderText("Exit Current Game?");
            confirmation.initOwner(stage);

            ButtonType result = confirmation.showAndWait().orElse(ButtonType.NO);

            if (result == ButtonType.YES) {
                stopAll();
                onBack.run();
            } else if (!wasPaused) {
                resumeAll();
                overallStatusLabel.setText("");
            }
        });

        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        HBox bottomLayout = new HBox(20, overallStatusLabel, bottomSpacer, backButton);
        bottomLayout.setAlignment(Pos.CENTER);
        bottomLayout.setPadding(new Insets(0, 20, 0, 20));
        bottomLayout.setMaxWidth(Double.MAX_VALUE);

        VBox gameLayout = new VBox(20);
        gameLayout.setAlignment(Pos.CENTER);
        gameLayout.setPadding(new Insets(20));
        gameLayout.setStyle("-fx-background-color: black;");
        gameLayout.getChildren().addAll(titleLabel, boardsLayout, bottomLayout);

        double sceneWidth = twoPlayerMode ? 1000 : 550;
        Scene gameScene = new Scene(gameLayout, sceneWidth, 700);

        gameScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
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
                case P:
                    togglePauseAll();
                    break;
                default:
                    break;
            }
            event.consume();
        });

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

        stage.setScene(gameScene);

        playerOne.start();
        if (twoPlayerMode) {
            playerTwo.start();
        }
    }

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

    private void handlePlayerGameOver(boolean isPlayerOne) {
        if (roundEnded) {
            return;
        }

        if (isPlayerOne) {
            if (playerOneEnded) {
                return;
            }
            playerOneEnded = true;
        } else {
            if (playerTwoEnded) {
                return;
            }
            playerTwoEnded = true;
        }

        if (!twoPlayerMode) {
            roundEnded = true;
            stopAll();
            overallStatusLabel.setText("Game Over - Score: " + playerOne.getScore());
            return;
        }

        if (playerOneEnded && playerTwoEnded) {
            roundEnded = true;
            stopAll();

            int scoreOne = playerOne.getScore();
            int scoreTwo = playerTwo.getScore();

            if (scoreOne > scoreTwo) {
                overallStatusLabel.setText("Game Over - Player 1 Wins!");
            } else if (scoreTwo > scoreOne) {
                overallStatusLabel.setText("Game Over - Player 2 Wins!");
            } else {
                overallStatusLabel.setText("Game Over - It's a Tie!");
            }
        } else {
            String toppedOutName = isPlayerOne ? "Player 1" : "Player 2";
            overallStatusLabel.setText(toppedOutName + " topped out - game continues");
        }
    }
}
