package griffith.s5330916;

public class PieceController {
    private final GameBoard gameBoard;
    private ActivePiece currentPiece;

    public PieceController(GameBoard gameBoard) {
        this.gameBoard = gameBoard;
    }

    public void setCurrentPiece(ActivePiece currentPiece) {
        this.currentPiece = currentPiece;
    }

    public ActivePiece getCurrentPiece() {
        return currentPiece;
    }

    // Moving current piece down one grid space
    public boolean movePieceDown() {
        int nextRow = currentPiece.getAnchorRow() + 1;
        // Moving piece if next position is available
        if (gameBoard.canPlacePiece(currentPiece, nextRow, currentPiece.getAnchorColumn())) {
            currentPiece.setAnchorRow(nextRow);
            return true;
        }
        return false;
    }

    // Moving current piece left or right
    public boolean movePieceHorizontal(int direction) {
        int nextColumn = currentPiece.getAnchorColumn() + direction;
        // Moving piece if new horizontal position is available
        if (gameBoard.canPlacePiece(currentPiece, currentPiece.getAnchorRow(), nextColumn)) {
            currentPiece.setAnchorColumn(nextColumn);
            return true;
        }
        return false;
    }

    // Rotating current piece 90 degrees clockwise around anchor block
    public boolean rotatePiece() {
        // Square piece does not need to be rotated
        if (!currentPiece.getCurrentPieceType().isRotatable()) {
            return false;
        }

        int[][] currentPieceShape = currentPiece.getCurrentPieceShape();
        int[][] rotatedShape = new int[currentPieceShape.length][2];
        for (int i = 0; i < currentPieceShape.length; i++) {
            int rowOffset = currentPieceShape[i][0];
            int columnOffset = currentPieceShape[i][1];

            // Rotating row and column offsets 90 degrees clockwise
            rotatedShape[i][0] = columnOffset;
            rotatedShape[i][1] = -rowOffset;
        }

        // Only applying rotation if rotated piece fits on grid
        if (gameBoard.canPlacePiece(currentPiece.getAnchorRow(), currentPiece.getAnchorColumn(), rotatedShape)) {
            currentPiece.setCurrentPieceShape(rotatedShape);
            return true;
        }
        return false;
    }
}
