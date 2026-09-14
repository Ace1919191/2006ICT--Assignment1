package griffith.s5330916;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PieceSequence {
    private final List<PieceType> generatedPieces = new ArrayList<>();
    private final Random random = new Random();
    private final PieceType[] availablePieces = PieceType.values();

    // Returning the piece at the given index, generating new ones as needed
    public PieceType getPiece(int index) {
        while (generatedPieces.size() <= index) {
            generatedPieces.add(availablePieces[random.nextInt(availablePieces.length)]);
        }
        return generatedPieces.get(index);
    }
}