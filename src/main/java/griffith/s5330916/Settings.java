package griffith.s5330916;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.binding.Bindings;

public class Settings {

    // Defining location of settings JSON file
    private static final Path SETTINGS_FILE =
            Paths.get("src", "main", "resources", "settings.json");

    public static void show(Stage stage, Runnable onBack) {

        // Reading current settings from JSON file
        String json = readSettingsFile();

        int fieldLength = getInt(json, "fieldLength", 20);
        int fieldWidth = getInt(json, "fieldWidth", 10);
        int level = getInt(json, "level", 1);

        boolean music = getBoolean(json, "music", true);
        boolean soundEffects = getBoolean(json, "soundEffects", true);
        boolean aiPlayer = getBoolean(json, "aiPlayer", false);
        boolean extendedMode = getBoolean(json, "extendedMode", false);

        // Creating title for Settings Screen
        Label titleLabel = new Label("Settings");
        titleLabel.setStyle(
                "-fx-font-size: 30px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: yellow;"
        );

        // Creating sliders with constraints for each numerical setting
        Slider widthSlider = createSlider(5, 15, fieldWidth);
        Slider lengthSlider = createSlider(15, 30, fieldLength);
        Slider levelSlider = createSlider(1, 10, level);

        // Creating labels which display the currently selected slider values
        Label widthValue = createLabel(String.valueOf(fieldWidth));
        Label lengthValue = createLabel(String.valueOf(fieldLength));
        Label levelValue = createLabel(String.valueOf(level));

        // Updating displayed values when sliders are changed
        widthValue.textProperty().bind(
                Bindings.format("%.0f", widthSlider.valueProperty())
        );

        lengthValue.textProperty().bind(
                Bindings.format("%.0f", lengthSlider.valueProperty())
        );

        levelValue.textProperty().bind(
                Bindings.format("%.0f", levelSlider.valueProperty())
        );

        // Creating CheckBox Objects for On/Off settings
        CheckBox musicCheckBox = createCheckBox(music);
        CheckBox soundEffectsCheckBox = createCheckBox(soundEffects);
        CheckBox aiPlayerCheckBox = createCheckBox(aiPlayer);
        CheckBox extendedModeCheckBox = createCheckBox(extendedMode);

        // Creating labels to display On or Off beside each CheckBox
        Label musicValue = createLabel(music ? "On" : "Off");
        Label soundEffectsValue = createLabel(soundEffects ? "On" : "Off");
        Label aiPlayerValue = createLabel(aiPlayer ? "On" : "Off");
        Label extendedModeValue = createLabel(extendedMode ? "On" : "Off");

        // Updating On/Off labels when CheckBoxes are changed
        musicValue.textProperty().bind(
                Bindings.when(musicCheckBox.selectedProperty())
                        .then("On")
                        .otherwise("Off")
        );

        soundEffectsValue.textProperty().bind(
                Bindings.when(soundEffectsCheckBox.selectedProperty())
                        .then("On")
                        .otherwise("Off")
        );

        aiPlayerValue.textProperty().bind(
                Bindings.when(aiPlayerCheckBox.selectedProperty())
                        .then("On")
                        .otherwise("Off")
        );

        extendedModeValue.textProperty().bind(
                Bindings.when(extendedModeCheckBox.selectedProperty())
                        .then("On")
                        .otherwise("Off")
        );

        // GridPane allows each setting to remain aligned into three columns
        GridPane settingsGrid = new GridPane();
        settingsGrid.setAlignment(Pos.CENTER);
        settingsGrid.setHgap(25);
        settingsGrid.setVgap(30);
        settingsGrid.setPadding(new Insets(20));

        // First column contains the setting names
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setPrefWidth(220);
        labelColumn.setHalignment(HPos.RIGHT);

        // Second column contains sliders and CheckBoxes
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setPrefWidth(350);
        controlColumn.setHalignment(HPos.LEFT);

        // Third column contains the current setting value
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPrefWidth(70);
        valueColumn.setHalignment(HPos.LEFT);

        settingsGrid.getColumnConstraints().addAll(
                labelColumn,
                controlColumn,
                valueColumn
        );

        // Adding Field Width controls to first row
        settingsGrid.add(createLabel("Field Width (No. of cells):"), 0, 0);
        settingsGrid.add(widthSlider, 1, 0);
        settingsGrid.add(widthValue, 2, 0);

        // Adding Field Height controls to second row
        settingsGrid.add(createLabel("Field Height (No. of cells):"), 0, 1);
        settingsGrid.add(lengthSlider, 1, 1);
        settingsGrid.add(lengthValue, 2, 1);

        // Adding Game Level controls to third row
        settingsGrid.add(createLabel("Game Level:"), 0, 2);
        settingsGrid.add(levelSlider, 1, 2);
        settingsGrid.add(levelValue, 2, 2);

        // Adding Music controls
        settingsGrid.add(createLabel("Music:"), 0, 3);
        settingsGrid.add(musicCheckBox, 1, 3);
        settingsGrid.add(musicValue, 2, 3);

        // Adding Sound Effects controls
        settingsGrid.add(createLabel("Sound Effects:"), 0, 4);
        settingsGrid.add(soundEffectsCheckBox, 1, 4);
        settingsGrid.add(soundEffectsValue, 2, 4);

        // Adding AI Player controls
        settingsGrid.add(createLabel("AI Player:"), 0, 5);
        settingsGrid.add(aiPlayerCheckBox, 1, 5);
        settingsGrid.add(aiPlayerValue, 2, 5);

        // Adding Extended Mode controls
        settingsGrid.add(createLabel("Extended Mode:"), 0, 6);
        settingsGrid.add(extendedModeCheckBox, 1, 6);
        settingsGrid.add(extendedModeValue, 2, 6);

        // Creating Button Objects for Settings Screen
        Button saveButton = new Button("Save");
        Button backButton = new Button("Back");

        // Defining same button style used by Main Menu
        String menuButtonStyle =
                "-fx-font-size: 20px;" +
                        "-fx-background-color: #555;" +
                        "-fx-text-fill: yellow;";

        saveButton.setStyle(menuButtonStyle);
        backButton.setStyle(menuButtonStyle);

        saveButton.setPrefWidth(150);
        backButton.setPrefWidth(150);

        // Label is empty until settings have successfully been saved
        Label saveMessage = createLabel("");

        // Updating JSON file with currently selected settings
        saveButton.setOnAction(ignored -> {

            int newFieldWidth =
                    (int) Math.round(widthSlider.getValue());

            int newFieldLength =
                    (int) Math.round(lengthSlider.getValue());

            int newLevel =
                    (int) Math.round(levelSlider.getValue());

            saveSettings(
                    newFieldLength,
                    newFieldWidth,
                    newLevel,
                    musicCheckBox.isSelected(),
                    soundEffectsCheckBox.isSelected(),
                    aiPlayerCheckBox.isSelected(),
                    extendedModeCheckBox.isSelected()
            );

            saveMessage.setText("Settings Saved");
        });

        // Returning user back to Main Menu
        backButton.setOnAction(ignored -> onBack.run());

        // Putting buttons beside each other
        HBox buttonLayout = new HBox(20);
        buttonLayout.setAlignment(Pos.CENTER);
        buttonLayout.getChildren().addAll(saveButton, backButton);

        // VBox holds the Settings Screen vertically
        VBox settingsLayout = new VBox(20);
        settingsLayout.setAlignment(Pos.CENTER);
        settingsLayout.setPadding(new Insets(20));
        settingsLayout.setStyle("-fx-background-color: black;");

        settingsLayout.getChildren().addAll(
                titleLabel,
                settingsGrid,
                buttonLayout,
                saveMessage
        );

        // Creating Scene and rendering it onto the existing Stage
        Scene settingsScene = new Scene(settingsLayout, 800, 600);

        stage.setScene(settingsScene);
    }

    // Creating slider with integer values and visible constraints
    private static Slider createSlider(
            int minimum,
            int maximum,
            int startingValue) {

        Slider slider = new Slider(
                minimum,
                maximum,
                startingValue
        );

        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);

        // Each tick represents one selectable integer
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setBlockIncrement(1);
        slider.setSnapToTicks(true);

        slider.setPrefWidth(350);

        return slider;
    }

    // Creating labels with same text colour used by Main Menu
    private static Label createLabel(String text) {

        Label label = new Label(text);

        label.setStyle(
                "-fx-text-fill: yellow;" +
                        "-fx-font-size: 14px;"
        );

        return label;
    }

    // Creating CheckBox with initial value from JSON file
    private static CheckBox createCheckBox(boolean selected) {

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(selected);

        return checkBox;
    }

    // Reading contents of settings JSON file
    private static String readSettingsFile() {

        try {
            return Files.readString(SETTINGS_FILE);

        } catch (IOException e) {
            System.err.println("Could not read settings.json");
            return "";
        }
    }

    // Saving selected settings back into JSON file
    private static void saveSettings(
            int fieldLength,
            int fieldWidth,
            int level,
            boolean music,
            boolean soundEffects,
            boolean aiPlayer,
            boolean extendedMode) {

        String json =
                "{\n" +
                        "  \"fieldLength\": " + fieldLength + ",\n" +
                        "  \"fieldWidth\": " + fieldWidth + ",\n" +
                        "  \"level\": " + level + ",\n" +
                        "  \"music\": " + music + ",\n" +
                        "  \"soundEffects\": " + soundEffects + ",\n" +
                        "  \"aiPlayer\": " + aiPlayer + ",\n" +
                        "  \"extendedMode\": " + extendedMode + "\n" +
                        "}";

        try {
            Files.writeString(SETTINGS_FILE, json);

        } catch (IOException e) {
            System.err.println(
                    "Could not save settings.json: " + e.getMessage()
            );
        }
    }

    // Finding integer setting from JSON and using default if it cannot be found
    private static int getInt(
            String json,
            String key,
            int defaultValue) {

        Pattern pattern = Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(\\d+)"
        );

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return defaultValue;
    }

    // Finding boolean setting from JSON and using default if it cannot be found
    private static boolean getBoolean(
            String json,
            String key,
            boolean defaultValue) {

        Pattern pattern = Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(true|false)"
        );

        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }

        return defaultValue;
    }
}