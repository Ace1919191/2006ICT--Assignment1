package griffith.s5330916;

/**
 * Move calculated by TetrisServer.
 *
 * opX is the target anchor column.
 * opRotate is the number of clockwise rotations.
 *
 * The local game currently receives this only for server testing/visualisation.
 * It does not use the move to control either human player.
 */
public record OpMove(int opX, int opRotate) { }
