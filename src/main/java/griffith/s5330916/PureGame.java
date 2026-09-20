package griffith.s5330916;

/**
 * Snapshot sent from a local Tetris board to TetrisServer.
 *
 * Piece shapes use this project's native format:
 * each entry is {rowOffset, columnOffset} from the piece anchor.
 */
public class PureGame {
    private String source;
    private int width;
    private int height;
    private int[][] cells;
    private int[][] currentShape;
    private int[][] nextShape;
    private int anchorRow;
    private int anchorColumn;

    // Required by Jackson when TetrisServer deserialises the request
    public PureGame() { }

    public PureGame(String source, int width, int height, int[][] cells, int[][] currentShape,
                    int[][] nextShape, int anchorRow, int anchorColumn) {
        this.source = source;
        this.width = width;
        this.height = height;
        this.cells = cells;
        this.currentShape = currentShape;
        this.nextShape = nextShape;
        this.anchorRow = anchorRow;
        this.anchorColumn = anchorColumn;
    }

    public String getSource() { return source; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int[][] getCells() { return cells; }
    public int[][] getCurrentShape() { return currentShape; }
    public int[][] getNextShape() { return nextShape; }
    public int getAnchorRow() { return anchorRow; }
    public int getAnchorColumn() { return anchorColumn; }

    public void setSource(String source) { this.source = source; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setCells(int[][] cells) { this.cells = cells; }
    public void setCurrentShape(int[][] currentShape) { this.currentShape = currentShape; }
    public void setNextShape(int[][] nextShape) { this.nextShape = nextShape; }
    public void setAnchorRow(int anchorRow) { this.anchorRow = anchorRow; }
    public void setAnchorColumn(int anchorColumn) { this.anchorColumn = anchorColumn; }
}
