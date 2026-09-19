package griffith.s5330916;

public final class BoardEvaluator {
    private final int holePenalty;

    public BoardEvaluator() { this(5); }

    public BoardEvaluator(int holePenalty) {
        if (holePenalty < 0) { throw new IllegalArgumentException("Use a non-negative penalty"); }
        this.holePenalty = holePenalty;
    }


    public int columnHeight(GameBoard board, int col) {

        for (int row = 0; row < board.height(); row++) {
            if (board.cell(row,col) != 0) {return board.height() - row;}
        }
        return 0;
    }


    public int maximumHeight(GameBoard board) {

        int tallest = 0;
        for (int col = 0; col < board.width(); col++) {
            tallest = Math.max(tallest, columnHeight(board, col));
        }
        return tallest;
    }


    public int holes(GameBoard board) {

        int count = 0;
        for (int col = 0; col < board.width(); col++) {
            boolean blockAbove = false;
            for (int row = 0; row < board.height(); row++) {
                if (board.cell(row, col) != 0) {
                    blockAbove = true;
                }   else if (blockAbove) {
                    count++;
                }

            }
        }
        return count;
    }


    public int bumpiness(GameBoard board) {

        int total = 0;

        for (int col = 0; col< board.width() - 1; col++) {
            int left = columnHeight(board, col);
            int right = columnHeight(board, col+1);
            total += Math.abs(left-right);

        }
        return total;
    }

    public int evaluate(GameBoard after, int linesCleared) {

        return 3 * linesCleared
                - 4 * maximumHeight(after)
                - 2 * bumpiness(after)
                - holePenalty * holes(after);

    }
}
