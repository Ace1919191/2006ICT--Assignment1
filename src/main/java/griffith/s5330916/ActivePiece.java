package griffith.s5330916;

public class ActivePiece {
    private final PieceType currentPieceType;
    private int[][] currentPieceShape;

    // Defining current position of the anchor block
    private int anchorRow;
    private int anchorColumn;

    public ActivePiece(PieceType currentPieceType, int anchorRow, int anchorColumn) {
        this.currentPieceType = currentPieceType;
        // Creating copy of selected shape so it can be rotated
        this.currentPieceShape = currentPieceType.createShape();
        this.anchorRow = anchorRow;
        this.anchorColumn = anchorColumn;
    }

    public PieceType getCurrentPieceType() {
        return currentPieceType;
    }

    public int[][] getCurrentPieceShape() {
        return currentPieceShape;
    }

    public void setCurrentPieceShape(int[][] currentPieceShape) {
        this.currentPieceShape = currentPieceShape;
    }

    public int getAnchorRow() {
        return anchorRow;
    }

    public void setAnchorRow(int anchorRow) {
        this.anchorRow = anchorRow;
    }

    public int getAnchorColumn() {
        return anchorColumn;
    }

    public void setAnchorColumn(int anchorColumn) {
        this.anchorColumn = anchorColumn;
    }

    public int rotationCount() {return currentPieceType.getRotationCount();}

}
