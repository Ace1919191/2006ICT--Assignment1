package griffith.s5330916;

import java.util.ArrayList;
import java.util.List;

public final class TetrisAI {

    private final BoardEvaluator evaluator;
    public TetrisAI(BoardEvaluator evaluator) {this.evaluator = evaluator; }

    public GameBoard copyBoard(GameBoard board) {
        GameBoard clone = new GameBoard(board.height(), board.width());

        for (int row = 0; row < board.height(); row++) {
            for (int col = 0; col < board.width(); col++) {
                PieceType block = board.getLockedBlock(row, col);
                if (block != null) {
                    ActivePiece temp = new ActivePiece(block, row, col);
                    temp.setCurrentPieceShape(new int[][]{{0, 0}});
                    clone.lockPiece(temp);
                }
            }
        }
        return clone;
    }

    private Placement simulateDrop(GameBoard board, ActivePiece piece) {

        GameBoard clone = copyBoard(board);
        ActivePiece falling = new ActivePiece(
                piece.getCurrentPieceType(),
                piece.getAnchorRow(),
                piece.getAnchorColumn()
        );
        falling.setCurrentPieceShape(piece.getCurrentPieceShape());

        while (clone.canPlacePiece(
                falling.getAnchorRow() + 1,
                falling.getAnchorColumn(),
                falling.getCurrentPieceShape()
        )) {
            falling.setAnchorRow(falling.getAnchorRow() + 1);
        }

        clone.lockPiece(falling);
        int cleared = clone.clearFullRows();
        return new Placement(clone, cleared);
    }

    private boolean rotateShape(GameBoard board, ActivePiece piece) {

        if (!piece.getCurrentPieceType().isRotatable()) {
            return false;
        }

        int[][] pieceShape = piece.getCurrentPieceShape();
        int[][] rotatedShape = new int[pieceShape.length][2];

        for (int i = 0; i < pieceShape.length; i++) {
            int rowOffset = pieceShape[i][0];
            int colOffset = pieceShape[i][1];
            rotatedShape[i][0] = colOffset;
            rotatedShape[i][1] = -rowOffset;
        }

        if (board.canPlacePiece(piece.getAnchorRow(), piece.getAnchorColumn(), rotatedShape)) {
            piece.setCurrentPieceShape(rotatedShape);
            return true;
        }
        return false;
    }


    public List<Move> legalMoves(GameBoard board, ActivePiece piece) {
        List<Move> moves = new ArrayList<>();
        for (int rotation = 0; rotation < piece.rotationCount(); rotation++) {
            ActivePiece rotated = new ActivePiece(
                piece.getCurrentPieceType(),
                piece.getAnchorRow(),
                piece.getAnchorColumn()
            );
            rotated.setCurrentPieceShape(piece.getCurrentPieceShape());

            for (int r = 0; r < rotation; r++) {
                rotateShape(board, rotated);
            }

            int minCol = Integer.MAX_VALUE;
            int maxCol = Integer.MIN_VALUE;
            for (int[] block : rotated.getCurrentPieceShape()) {
                minCol = Math.min(minCol, block[1]);
                maxCol = Math.max(maxCol, block[1]);
            }
            int shapeWidth = (maxCol - minCol) + 1;

            for (int leftCol = 0; leftCol <= board.width() - shapeWidth; leftCol++) {
                int anchorCol = leftCol - minCol;
                rotated.setAnchorColumn(anchorCol);

                if (!board.canPlacePiece(rotated.getAnchorRow(), anchorCol, rotated.getCurrentPieceShape())) {
                    continue; // starting spot itself isn't valid, skip it
                }

                Placement result = simulateDrop(board, rotated);
                int score = evaluator.evaluate(result.board(), result.linesCleared());
                moves.add(new Move(anchorCol, rotation, score));
            }

        }
        return moves;

    }


    public Move findBestMove(GameBoard board, ActivePiece piece) {
        Move best = null;
        for (Move candidate : legalMoves(board, piece)) {
            if (best == null || candidate.value() > best.value()) {
                best = candidate;

            }
        }

        return best;

    }
}


