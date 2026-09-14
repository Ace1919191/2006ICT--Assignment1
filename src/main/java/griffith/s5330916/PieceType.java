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
    }, "cyan", true),

    O(new int[][]{
            {0, 0},
            {0, 1},
            {1, 0},
            {1, 1}
    }, "yellow", false),

    T(new int[][]{
            {0, -1},
            {0, 0},
            {0, 1},
            {1, 0}
    }, "purple", true),

    L(new int[][]{
            {-1, 1},
            {0, -1},
            {0, 0},
            {0, 1}
    }, "orange", true),

    J(new int[][]{
            {-1, -1},
            {0, -1},
            {0, 0},
            {0, 1}
    }, "blue", true),

    S(new int[][]{
            {0, 0},
            {0, 1},
            {1, -1},
            {1, 0}
    }, "green", true),

    Z(new int[][]{
            {0, -1},
            {0, 0},
            {1, 0},
            {1, 1}
    }, "red", true);

    private final int[][] shape;
    private final String colour;
    private final boolean rotatable;

    PieceType(int[][] shape, String colour, boolean rotatable) {
        this.shape = shape;
        this.colour = colour;
        this.rotatable = rotatable;
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
}
