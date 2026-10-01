package griffith.s5330916;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the Tetris board dimensions and player-count setting from settings.json.
 */

public final class GameSettings {
    private static final Path SETTINGS_FILE =
            Paths.get("src", "main", "resources", "settings.json");

    private static final int DEFAULT_FIELD_HEIGHT = 20;
    private static final int DEFAULT_FIELD_WIDTH = 10;
    private static final boolean DEFAULT_TWO_PLAYER_MODE = true;

    private final int fieldHeight;
    private final int fieldWidth;
    private final boolean twoPlayerMode;
    private final boolean music;
    private final boolean soundEffects;

    private GameSettings(int fieldHeight, int fieldWidth, boolean twoPlayerMode,
                         boolean music, boolean soundEffects) {
        this.fieldHeight = fieldHeight;
        this.fieldWidth = fieldWidth;
        this.twoPlayerMode = twoPlayerMode;
        this.music = music;
        this.soundEffects = soundEffects;
    }

    public static GameSettings load() {
        String json = readSettingsFile();
        boolean music = getBoolean(json, "music", true);
        boolean soundEffects = getBoolean(json, "soundEffects", true);

        int fieldHeight = getInt(json, "fieldLength", DEFAULT_FIELD_HEIGHT);
        int fieldWidth = getInt(json, "fieldWidth", DEFAULT_FIELD_WIDTH);
        boolean twoPlayerMode = getBoolean(json, "twoPlayerMode", DEFAULT_TWO_PLAYER_MODE);

        return new GameSettings(fieldHeight, fieldWidth, twoPlayerMode, music, soundEffects);
    }

    public int getFieldHeight() {
        return fieldHeight;
    }

    public int getFieldWidth() {
        return fieldWidth;
    }

    // Whether the game should be played as 2 players (side by side) or a single player
    public boolean isTwoPlayerMode() {
        return twoPlayerMode;
    }
    public boolean isMusicEnabled() {
        return music;
    }

    public boolean isSoundEffectsEnabled() {
        return soundEffects;
    }

    private static String readSettingsFile() {
        try {
            return Files.readString(SETTINGS_FILE);
        } catch (IOException e) {
            System.err.println("Could not read settings.json: " + e.getMessage());
            return "";
        }
    }

    private static int getInt(String json, String key, int defaultValue) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return defaultValue;
    }

    private static boolean getBoolean(String json, String key, boolean defaultValue) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }

        return defaultValue;
    }
}