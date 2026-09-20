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
        for (int row = completedRow; row > 0; row--) {
            System.arraycopy(lockedBlocks[row - 1], 0, lockedBlocks[row], 0, fieldWidth);
        }

        for (int column = 0; column < fieldWidth; column++) {
            lockedBlocks[0][column] = null;
        }
    }

    public boolean canPlacePiece(ActivePiece currentPiece, int testAnchorRow, int testAnchorColumn) {
        return canPlacePiece(testAnchorRow, testAnchorColumn, currentPiece.getCurrentPieceShape());
    }

    public boolean canPlacePiece(int testAnchorRow, int testAnchorColumn, int[][] pieceShape) {
        for (int[] block : pieceShape) {
            int row = testAnchorRow + block[0];
            int column = testAnchorColumn + block[1];

            if (row < 0 || row >= fieldHeight || column < 0 || column >= fieldWidth) {
                return false;
            }

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
            lockedBlocks[row][column] = currentPiece.getCurrentPieceType();
        }
    }

    public PieceType getLockedBlock(int row, int column) {
        return lockedBlocks[row][column];
    }

    // Converting local PieceType grid into the server's 0 = empty, 1 = occupied grid
    public int[][] getServerCells() {
        int[][] cells = new int[fieldHeight][fieldWidth];

        for (int row = 0; row < fieldHeight; row++) {
            for (int column = 0; column < fieldWidth; column++) {
                cells[row][column] = lockedBlocks[row][column] == null ? 0 : 1;
            }
        }
        return cells;
    }
}
