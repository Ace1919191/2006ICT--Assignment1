package griffith.s5330916;

/**
 * Move calculated by TetrisServer.
 *
 * opX is the target anchor column.
 * opRotate is the number of clockwise rotations.
 *
 * AI-controlled PlayerBoard instances use this response to execute the
 * server-selected move. Human boards ignore it for gameplay.
 */
public record OpMove(int opX, int opRotate) { }
