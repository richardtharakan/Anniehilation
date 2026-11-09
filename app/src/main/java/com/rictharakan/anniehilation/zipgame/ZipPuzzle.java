package com.rictharakan.anniehilation.zipgame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure Java model for a Zip-style Hamiltonian path puzzle.
 *
 * The board stores numbered checkpoints and blocked edges. A valid solution is a
 * single orthogonal path that visits every cell once, collects checkpoints in
 * ascending order, and ends on the final checkpoint.
 */
public final class ZipPuzzle {

    public enum MoveResult {
        STARTED,
        ADDED,
        RETRACTED,
        WON,
        WRONG_START,
        NON_ADJACENT,
        WALL_BLOCKED,
        ALREADY_VISITED,
        WRONG_NUMBER,
        PREMATURE_END
    }

    private final int rows;
    private final int cols;
    private final int level;
    private final int[] checkpointAt;
    private final boolean[][] horizontalWalls;
    private final boolean[][] verticalWalls;
    private final int[] solutionPath;
    private final int checkpointCount;
    private final ArrayList<Integer> path = new ArrayList<Integer>();
    private final boolean[] visited;
    private int expectedNext = 1;

    ZipPuzzle(
            int rows,
            int cols,
            int level,
            int[] checkpointAt,
            boolean[][] horizontalWalls,
            boolean[][] verticalWalls,
            int[] solutionPath) {
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("A puzzle must be at least 2x2.");
        }
        if (checkpointAt == null || checkpointAt.length != rows * cols) {
            throw new IllegalArgumentException("Checkpoint array does not match board size.");
        }
        if (solutionPath == null || solutionPath.length != rows * cols) {
            throw new IllegalArgumentException("Solution path must visit every cell.");
        }
        this.rows = rows;
        this.cols = cols;
        this.level = level;
        this.checkpointAt = Arrays.copyOf(checkpointAt, checkpointAt.length);
        this.horizontalWalls = copy(horizontalWalls);
        this.verticalWalls = copy(verticalWalls);
        this.solutionPath = Arrays.copyOf(solutionPath, solutionPath.length);
        this.visited = new boolean[rows * cols];
        int count = 0;
        for (int value : this.checkpointAt) {
            if (value > 0) count++;
        }
        this.checkpointCount = count;
        validateSolution();
    }

    private static boolean[][] copy(boolean[][] source) {
        if (source == null) return new boolean[0][0];
        boolean[][] result = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? new boolean[0] : Arrays.copyOf(source[i], source[i].length);
        }
        return result;
    }

    private void validateSolution() {
        boolean[] seen = new boolean[rows * cols];
        int nextNumber = 1;
        for (int i = 0; i < solutionPath.length; i++) {
            int cell = solutionPath[i];
            if (cell < 0 || cell >= seen.length || seen[cell]) {
                throw new IllegalArgumentException("Solution path contains an invalid or repeated cell.");
            }
            seen[cell] = true;
            int number = checkpointAt[cell];
            if (number > 0) {
                if (number != nextNumber) {
                    throw new IllegalArgumentException("Solution path does not visit checkpoints in order.");
                }
                nextNumber++;
            }
            if (i > 0) {
                int previous = solutionPath[i - 1];
                if (!isAdjacent(previous, cell) || isWallBetween(previous, cell)) {
                    throw new IllegalArgumentException("Solution path crosses a non-adjacent cell or wall.");
                }
            }
        }
        if (checkpointAt[solutionPath[0]] != 1 || checkpointAt[solutionPath[solutionPath.length - 1]] != checkpointCount) {
            throw new IllegalArgumentException("Solution must start at 1 and end at the final checkpoint.");
        }
        if (nextNumber != checkpointCount + 1) {
            throw new IllegalArgumentException("Solution path did not collect every checkpoint.");
        }
    }

    public MoveResult enterCell(int targetCell) {
        if (targetCell < 0 || targetCell >= rows * cols) return MoveResult.NON_ADJACENT;

        if (path.isEmpty()) {
            if (checkpointAt[targetCell] != 1) return MoveResult.WRONG_START;
            path.add(targetCell);
            visited[targetCell] = true;
            expectedNext = 2;
            return MoveResult.STARTED;
        }

        int existingIndex = path.indexOf(targetCell);
        if (existingIndex >= 0) {
            if (existingIndex == path.size() - 1) return MoveResult.ALREADY_VISITED;
            truncatePath(existingIndex + 1);
            return MoveResult.RETRACTED;
        }

        int currentCell = path.get(path.size() - 1);
        if (!isAdjacent(currentCell, targetCell)) return MoveResult.NON_ADJACENT;
        if (isWallBetween(currentCell, targetCell)) return MoveResult.WALL_BLOCKED;
        if (visited[targetCell]) return MoveResult.ALREADY_VISITED;

        int number = checkpointAt[targetCell];
        if (number > 0 && number != expectedNext) return MoveResult.WRONG_NUMBER;
        if (number == checkpointCount && path.size() + 1 < rows * cols) {
            return MoveResult.PREMATURE_END;
        }

        path.add(targetCell);
        visited[targetCell] = true;
        if (number == expectedNext) expectedNext++;

        if (path.size() == rows * cols && expectedNext == checkpointCount + 1
                && checkpointAt[targetCell] == checkpointCount) {
            return MoveResult.WON;
        }
        return MoveResult.ADDED;
    }

    public boolean undo() {
        if (path.isEmpty()) return false;
        truncatePath(path.size() - 1);
        return true;
    }

    public void clearPath() {
        path.clear();
        Arrays.fill(visited, false);
        expectedNext = 1;
    }

    /**
     * Reveals the next move from the generated solution. If the player has
     * diverged, the path is rewound to its common correct prefix first.
     */
    public boolean applyHint() {
        int common = 0;
        int limit = Math.min(path.size(), solutionPath.length);
        while (common < limit && path.get(common).intValue() == solutionPath[common]) {
            common++;
        }
        if (common < path.size()) truncatePath(common);
        if (common == 0) {
            path.add(solutionPath[0]);
            visited[solutionPath[0]] = true;
            expectedNext = 2;
            common = 1;
        }
        if (common < solutionPath.length) {
            int next = solutionPath[common];
            path.add(next);
            visited[next] = true;
            int number = checkpointAt[next];
            if (number == expectedNext) expectedNext++;
        }
        return isSolved();
    }

    private void truncatePath(int keepCount) {
        while (path.size() > keepCount) {
            int removed = path.remove(path.size() - 1);
            visited[removed] = false;
        }
        recomputeExpectedNumber();
    }

    private void recomputeExpectedNumber() {
        expectedNext = 1;
        for (Integer cell : path) {
            int number = checkpointAt[cell.intValue()];
            if (number == expectedNext) expectedNext++;
        }
    }

    public boolean isSolved() {
        return path.size() == rows * cols
                && expectedNext == checkpointCount + 1
                && !path.isEmpty()
                && checkpointAt[path.get(path.size() - 1)] == checkpointCount;
    }

    public boolean isAdjacent(int first, int second) {
        int r1 = first / cols;
        int c1 = first % cols;
        int r2 = second / cols;
        int c2 = second % cols;
        return Math.abs(r1 - r2) + Math.abs(c1 - c2) == 1;
    }

    public boolean isWallBetween(int first, int second) {
        if (!isAdjacent(first, second)) return true;
        int r1 = first / cols;
        int c1 = first % cols;
        int r2 = second / cols;
        int c2 = second % cols;
        if (r1 != r2) {
            int top = Math.min(r1, r2);
            return top < horizontalWalls.length && c1 < horizontalWalls[top].length
                    && horizontalWalls[top][c1];
        }
        int left = Math.min(c1, c2);
        return r1 < verticalWalls.length && left < verticalWalls[r1].length
                && verticalWalls[r1][left];
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getLevel() { return level; }
    public int getCellCount() { return rows * cols; }
    public int getCheckpointCount() { return checkpointCount; }

    /** Cell occupied by Berthu's starting marker (checkpoint 1). */
    public int getStartCell() {
        for (int cell = 0; cell < checkpointAt.length; cell++) {
            if (checkpointAt[cell] == 1) return cell;
        }
        return -1;
    }

    /** Cell occupied by Annie's destination marker (the final checkpoint). */
    public int getEndCell() {
        for (int cell = 0; cell < checkpointAt.length; cell++) {
            if (checkpointAt[cell] == checkpointCount) return cell;
        }
        return -1;
    }
    public int getExpectedNext() { return expectedNext; }
    public int getPathLength() { return path.size(); }
    public int getCheckpointAt(int cell) { return checkpointAt[cell]; }
    public boolean isVisited(int cell) { return cell >= 0 && cell < visited.length && visited[cell]; }

    public int[] getPathCopy() {
        int[] result = new int[path.size()];
        for (int i = 0; i < path.size(); i++) result[i] = path.get(i);
        return result;
    }

    public int[] getSolutionPathCopy() {
        return Arrays.copyOf(solutionPath, solutionPath.length);
    }

    public boolean hasHorizontalWall(int row, int col) {
        return row >= 0 && row < horizontalWalls.length
                && col >= 0 && col < horizontalWalls[row].length
                && horizontalWalls[row][col];
    }

    public boolean hasVerticalWall(int row, int col) {
        return row >= 0 && row < verticalWalls.length
                && col >= 0 && col < verticalWalls[row].length
                && verticalWalls[row][col];
    }
}
