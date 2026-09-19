package griffith.s5330916;

import java.util.ArrayList;
import java.util.List;

public final class TetrisAI {

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

}
public List<Move> legalMoves(GameBoard board, piece); {

}