package griffith.s5330916;

public class ScoreManager {

    private int score;
    private int linesErased;
    private int level;

    public ScoreManager() {
        reset();
    }

    public void linesCleared(int lines) {
        switch (lines) {
            case 1:
                score += 100;
                break;
            case 2:
                score += 300;
                break;
            case 3:
                score += 600;
                break;
            case 4:
                score += 1000;
                break;
        }

        linesErased += lines;

        // Level increases every 10 erased lines
        level = (linesErased / 10) + 1;
    }

    public int getScore() {
        return score;
    }

    public int getLinesErased() {
        return linesErased;
    }

    public int getLevel() {
        return level;
    }

    public void reset() {
        score = 0;
        linesErased = 0;
        level = 1;
    }
}