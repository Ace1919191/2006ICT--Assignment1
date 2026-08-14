package griffith.s5330916;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.net.URL;

// The Main Class inherits the Application Object type from JavaFX
public class Main extends Application {
    //Calling method decorator @Override tells the class not to inherit the start method from Application
    @Override
    public void start(Stage initialStage) {

        // Create splash screen Stage and parse to splash screen function
        Stage splashStage = new Stage(StageStyle.UNDECORATED);
        String xVariable = "Idk yet";
        showSplashScreen(splashStage, () -> showMainWindow(initialStage));
    }

    private void showSplashScreen(Stage stage, Runnable onFinished) {

        // Get JPG from resources
        URL imageURL = getClass().getResource("/assets/splash_screen.jpg");

        // If image could not be found, skip splash screen
        if (imageURL == null) {
            System.err.println("Splash Screen Not Found, opening main window.");
            onFinished.run();
            return;
        }

        // Load splash image
        Image image = new Image(imageURL.toExternalForm());
        ImageView imageView = new ImageView(image);

        // Keep image at its original size
        imageView.setPreserveRatio(true);

        // Create skip label
        Label skipLabel = new Label("Press 'ESC' to skip.");
        skipLabel.setStyle(
                "-fx-text-fill: yellow;" +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;"
        );
        skipLabel.setPadding(new Insets(0, 10, 20, 0));

        // Create splash screen layout
        StackPane splashLayout = new StackPane();
        splashLayout.getChildren().addAll(imageView, skipLabel);

        StackPane.setAlignment(skipLabel, Pos.BOTTOM_RIGHT);

        // Create scene matching image dimensions
        Scene splashScene = new Scene(
                splashLayout,
                image.getWidth(),
                image.getHeight()
        );

        stage.setScene(splashScene);

        // Transition to main application
        Runnable transitionToAction = () -> {
            stage.close();
            onFinished.run();
        };

        // Allow ESC to skip splash screen
        splashScene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                transitionToAction.run();
            }
        });

        // Show splash screen for 4 seconds
        PauseTransition splashDelay = new PauseTransition(Duration.seconds(4));

        splashDelay.setOnFinished(event -> transitionToAction.run());

        stage.show();
        splashDelay.play();
    }

    private void showMainWindow(Stage stage){
        stage.setTitle("Tetris");

        //VBox (Vertical Box) is I assume JavaFX window and we modify the menuLayout
        VBox menuLayout = new VBox(20);
        menuLayout.setAlignment(Pos.CENTER);
        //JavaFX prefix CSS can be used when adding the prefix -fx
        menuLayout.setStyle("-fx-background-color: black");

        // Creating Button Objects for the Menu Screen
        Button playButton = new Button("Play");
        Button scoresButton = new Button("High Scores");
        Button settingsButton = new Button("Settings");
        Button exitButton = new Button("Exit");

        // Defining default menu button styles and applying
        String menuButtonStyle = "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";
        playButton.setStyle(menuButtonStyle);
        scoresButton.setStyle(menuButtonStyle);
        settingsButton.setStyle(menuButtonStyle);
        exitButton.setStyle(menuButtonStyle);

        // Defining function calls on button press
        playButton.setOnAction(ignored -> Game.show(stage, () -> showMainWindow(stage)));
        scoresButton.setOnAction(ignored -> HighScores.show(stage, () -> showMainWindow(stage)));
        settingsButton.setOnAction(event -> Settings.show(stage, () -> showMainWindow(stage)));
        exitButton.setOnAction(event -> {stage.close(); System.exit(0);});

        menuLayout.getChildren().addAll(playButton, scoresButton, settingsButton, exitButton);

        // Canvas, the analogy didn't really click for me, is it the app window?
        Scene mainScene = new Scene(menuLayout, 800, 600);

        // Okay so the stage is the app window?
        // So the scene is probably what is rendered onto the stage / window? That is my understanding at least
        stage.setScene(mainScene);
        stage.show();
    }

    // Calling the static method means that the method belongs to the class rather than instanced by each object
    public static void main(String[] args) {
        launch(args);
    }
}