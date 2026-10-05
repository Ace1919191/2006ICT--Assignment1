package griffith.s5330916;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HighScores {
    // Defining location of scores JSON file
    private static final Path SCORES_FILE =
            Paths.get("src", "main", "resources", "scores.json");

    // Maximum number of scores retained/displayed
    private static final int MAX_SCORES = 10;

    // Same button style used by Main Menu, reused for all buttons on this screen
    private static final String MENU_BUTTON_STYLE =
            "-fx-font-size: 20px; -fx-background-color: #555; -fx-text-fill: yellow;";

    public static void show(Stage stage, Runnable onBack) {
        // Reading and sorting scores from JSON file
        List<PlayerScore> scores = readScores();

        // Creating title for High Scores Screen
        Label titleLabel = new Label("High Scores");
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: yellow;");

        // GridPane allows score information to remain aligned into columns
        GridPane scoresGrid = new GridPane();
        scoresGrid.setAlignment(Pos.CENTER);
        scoresGrid.setHgap(40);
        scoresGrid.setVgap(15);
        scoresGrid.setPadding(new Insets(20));

        // First column contains player position
        ColumnConstraints positionColumn = new ColumnConstraints();
        positionColumn.setPrefWidth(60);
        positionColumn.setHalignment(HPos.CENTER);

        // Second column contains player identifier
        ColumnConstraints playerColumn = new ColumnConstraints();
        playerColumn.setPrefWidth(120);
        playerColumn.setHalignment(HPos.CENTER);

        // Third column contains player score
        ColumnConstraints scoreColumn = new ColumnConstraints();
        scoreColumn.setPrefWidth(150);
        scoreColumn.setHalignment(HPos.CENTER);

        scoresGrid.getColumnConstraints().addAll(positionColumn, playerColumn, scoreColumn);

        // Creating column headings
        Label positionHeading = createLabel("Rank");
        Label playerHeading = createLabel("Player");
        Label scoreHeading = createLabel("Score");

        positionHeading.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px; -fx-font-weight: bold;");
        playerHeading.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px; -fx-font-weight: bold;");
        scoreHeading.setStyle("-fx-text-fill: yellow; -fx-font-size: 16px; -fx-font-weight: bold;");

        scoresGrid.add(positionHeading, 0, 0);
        scoresGrid.add(playerHeading, 1, 0);
        scoresGrid.add(scoreHeading, 2, 0);

        // Displaying maximum of ten highest scores
        int scoresToDisplay = Math.min(MAX_SCORES, scores.size());

        for (int i = 0; i < scoresToDisplay; i++) {
            PlayerScore playerScore = scores.get(i);

            scoresGrid.add(createLabel(String.valueOf(i + 1)), 0, i + 1);
            scoresGrid.add(createLabel(playerScore.player()), 1, i + 1);
            scoresGrid.add(createLabel(String.valueOf(playerScore.score())), 2, i + 1);
        }

        // Displaying message if scores JSON file contains no scores
        if (scores.isEmpty()) {
            Label noScoresLabel = createLabel("No Scores Available");
            scoresGrid.add(noScoresLabel, 0, 1, 3, 1);
            GridPane.setHalignment(noScoresLabel, HPos.CENTER);
        }

        // Creating Back Button for High Scores Screen
        Button backButton = new Button("Back");
        backButton.setStyle(MENU_BUTTON_STYLE);
        backButton.setPrefWidth(150);

        // Returning user back to Main Menu
        backButton.setOnAction(ignored -> onBack.run());

        HBox buttonRow = new HBox(20, backButton);
        buttonRow.setAlignment(Pos.CENTER);

        // VBox holds the High Scores Screen vertically
        VBox scoresLayout = new VBox(20);
        scoresLayout.setAlignment(Pos.CENTER);
        scoresLayout.setPadding(new Insets(20));
        scoresLayout.setStyle("-fx-background-color: black;");

        scoresLayout.getChildren().addAll(titleLabel, scoresGrid, buttonRow);

        // Creating Scene and rendering it onto the existing Stage
        Scene scoresScene = new Scene(scoresLayout, 800, 600);
        stage.setScene(scoresScene);
    }

    // Creating labels with same text colour used by Main Menu
    private static Label createLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: yellow; -fx-font-size: 14px;");
        return label;
    }

    /**
     * Checks whether the given score qualifies for the top-ten list.
     * Called from the game-over screen once a round ends.
     */
    public static boolean isHighScore(int score) {
        List<PlayerScore> scores = readScores();
        return scores.size() < MAX_SCORES || score > scores.get(scores.size() - 1).score();
    }

    /**
     * Adds a new score, re-sorts, truncates to the top MAX_SCORES entries,
     * and persists the result to scores.json.
     */
    public static void addScore(String player, int score) {
        List<PlayerScore> scores = readScores();
        scores.removeIf(existingScore -> existingScore.player().equalsIgnoreCase(player));
        scores.add(new PlayerScore(player, score));

        scores.sort(
                (firstScore, secondScore) ->
                        Integer.compare(secondScore.score(), firstScore.score())
        );

        if (scores.size() > MAX_SCORES) {
            scores = scores.subList(0, MAX_SCORES);
        }

        writeScores(scores);
    }

    // Reading player identifiers and scores from JSON file
    private static List<PlayerScore> readScores() {
        List<PlayerScore> scores = new ArrayList<>();

        try {
            String json = Files.readString(SCORES_FILE);

            // Finding three letter player identifiers and their score
            Pattern pattern = Pattern.compile("\"([A-Za-z]{3})\"\\s*:\\s*(\\d+)");

            Matcher matcher = pattern.matcher(json);

            while (matcher.find()) {
                String player = matcher.group(1).toUpperCase();
                int score = Integer.parseInt(matcher.group(2));
                scores.add(new PlayerScore(player, score));
            }

        } catch (IOException e) {System.err.println("Could not read scores.json: " + e.getMessage());}

        // Sorting scores from highest to lowest
        scores.sort(
                (firstScore, secondScore) ->
                        Integer.compare(secondScore.score(), firstScore.score())
        );
        return scores;
    }

    // Writing the current top scores list back out to the JSON file
    private static void writeScores(List<PlayerScore> scores) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        for (int i = 0; i < scores.size(); i++) {
            PlayerScore playerScore = scores.get(i);
            json.append("  \"").append(playerScore.player()).append("\": ").append(playerScore.score());
            if (i < scores.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("}\n");

        try {
            Files.createDirectories(SCORES_FILE.getParent());
            Files.writeString(SCORES_FILE, json.toString());
        } catch (IOException e) {
            System.err.println("Could not write scores.json: " + e.getMessage());
        }
    }

    // Storing player identifier and score together while displaying scores
    private record PlayerScore(String player, int score) { }
}
