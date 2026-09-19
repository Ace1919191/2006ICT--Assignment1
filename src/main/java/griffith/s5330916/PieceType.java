package griffith.s5330916;

/**
 * Defines the available Tetris piece types.
 *
 * <p>Each piece stores its original block offsets, display colour,
 * and whether it can be rotated.</p>
 */
public enum PieceType {
    I(new int[][]{
            {0, -1},
            {0, 0},
            {0, 1},
            {0, 2}
    }, "cyan", true, 2),

    O(new int[][]{
            {0, 0},
            {0, 1},
            {1, 0},
            {1, 1}
    }, "yellow", false, 1),

    T(new int[][]{
            {0, -1},
            {0, 0},
            {0, 1},
            {1, 0}
    }, "purple", true,4),

    L(new int[][]{
            {-1, 1},
            {0, -1},
            {0, 0},
            {0, 1}
    }, "orange", true, 4),

    J(new int[][]{
            {-1, -1},
            {0, -1},
            {0, 0},
            {0, 1}
    }, "blue", true, 4),

    S(new int[][]{
            {0, 0},
            {0, 1},
            {1, -1},
            {1, 0}
    }, "green", true, 2),

    Z(new int[][]{
            {0, -1},
            {0, 0},
            {1, 0},
            {1, 1}
    }, "red", true, 2);

    private final int[][] shape;
    private final String colour;
    private final boolean rotatable;
    private final int rotationCount;

    PieceType(int[][] shape, String colour, boolean rotatable, int rotationCount) {
        this.shape = shape;
        this.colour = colour;
        this.rotatable = rotatable;
        this.rotationCount = rotationCount;
    }

    public int[][] createShape() {
        int[][] copiedShape = new int[shape.length][2];

        for (int i = 0; i < shape.length; i++) {
            copiedShape[i][0] = shape[i][0];
            copiedShape[i][1] = shape[i][1];
        }

        return copiedShape;
    }

    public String getColour() {
        return colour;
    }

    public boolean isRotatable() {
        return rotatable;
    }
    public int getRotationCount() {return rotationCount; }
}
