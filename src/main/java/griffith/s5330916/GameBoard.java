package griffith.s5330916;

public class GameBoard {
    private final int fieldHeight;
    private final int fieldWidth;
    private final PieceType[][] lockedBlocks;

    public GameBoard(int fieldHeight, int fieldWidth) {
        this.fieldHeight = fieldHeight;
        this.fieldWidth = fieldWidth;
        lockedBlocks = new PieceType[fieldHeight][fieldWidth];
    }

    // Checking entire grid for completed rows
    public int clearFullRows() {
        int linesCleared = 0;
        // Starting from bottom because rows above will move down
        for (int row = fieldHeight - 1; row >= 0; row--) {
            if (isRowFull(row)) {
                removeRow(row);
                linesCleared++;
                // Checking same row again because another row has moved into it
                row++;
            }
        }
        return linesCleared;
    }

    // Checking if every grid space in a row contains a block
    private boolean isRowFull(int row) {
        for (int column = 0; column < fieldWidth; column++) {
            if (lockedBlocks[row][column] == null) {
                return false;
            }
        }
        return true;
    }

    // Removing completed row and moving all rows above down by one
    private void removeRow(int completedRow) {
        // Moving every row above the completed row down one position
        for (int row = completedRow; row > 0; row--) {
            if (fieldWidth >= 0) System.arraycopy(lockedBlocks[row - 1], 0, lockedBlocks[row], 0, fieldWidth);
        }
        // Clearing the new top row
        for (int column = 0; column < fieldWidth; column++) {
            lockedBlocks[0][column] = null;
        }
    }

    //Made public so it can be used in BoardEvaluator
    public int width() { return fieldWidth; }
    public int height() { return fieldHeight; }
    public int cell(int row, int col) {
        return lockedBlocks[row][col] == null ? 0 : 1;
    }


    // Checking if piece can exist at specified anchor position
    // Checking if current piece can exist at specified anchor position
    public boolean canPlacePiece(
            ActivePiece currentPiece,
            int testAnchorRow,
            int testAnchorColumn) {
        return canPlacePiece(testAnchorRow, testAnchorColumn, currentPiece.getCurrentPieceShape());
    }

    // Checking if specified piece shape can exist at anchor position
    public boolean canPlacePiece(int testAnchorRow, int testAnchorColumn, int[][] pieceShape) {
        for (int[] block : pieceShape) {
            int row = testAnchorRow + block[0];
            int column = testAnchorColumn + block[1];

            // Checking if block would leave the grid
            if (row < 0 || row >= fieldHeight || column < 0 || column >= fieldWidth) {
                return false;
            }

            // Checking if block would collide with landed piece
            if (lockedBlocks[row][column] != null) {
                return false;
            }
        }
        return true;
    }

    // Converting falling piece into locked blocks
    public void lockPiece(ActivePiece currentPiece) {
        for (int[] block : currentPiece.getCurrentPieceShape()) {
            int row = currentPiece.getAnchorRow() + block[0];
            int column = currentPiece.getAnchorColumn() + block[1];

            // Saving Piece Type so placed block retains its colour
            lockedBlocks[row][column] = currentPiece.getCurrentPieceType();
        }
    }

    public PieceType getLockedBlock(int row, int column) {
        return lockedBlocks[row][column];
    }
}
