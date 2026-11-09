package com.rictharakan.anniehilation.zipgame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

/** Creates self-contained, solvable boards without downloading daily LinkedIn puzzles. */
public final class ZipPuzzleGenerator {
    private ZipPuzzleGenerator() { }

    public static ZipPuzzle generate(int level, long seed) {
        int safeLevel = Math.max(1, level);
        int size = gridSizeForLevel(safeLevel);
        int rows = size;
        int cols = size;
        Random random = new Random(seed);
        int[] solution = createHamiltonianPath(rows, cols, random);

        int total = rows * cols;
        int checkpointCount = Math.min(10, Math.max(4, 4 + (safeLevel - 1) / 18));
        checkpointCount = Math.min(checkpointCount, total);
        int[] checkpointAt = new int[total];
        for (int number = 1; number <= checkpointCount; number++) {
            int pathIndex = (int) Math.round((number - 1) * (total - 1.0) / (checkpointCount - 1));
            checkpointAt[solution[pathIndex]] = number;
        }

        boolean[][] horizontalWalls = new boolean[rows - 1][cols];
        boolean[][] verticalWalls = new boolean[rows][cols - 1];
        addWalls(rows, cols, solution, safeLevel, random, horizontalWalls, verticalWalls);
        return new ZipPuzzle(rows, cols, safeLevel, checkpointAt,
                horizontalWalls, verticalWalls, solution);
    }

    public static int gridSizeForLevel(int level) {
        if (level < 9) return 4;
        if (level < 25) return 5;
        if (level < 51) return 6;
        if (level < 91) return 7;
        if (level < 141) return 8;
        if (level < 201) return 9;
        return 10;
    }

    private static int[] createHamiltonianPath(int rows, int cols, Random random) {
        int total = rows * cols;
        int[] path = new int[total];
        boolean[] used = new boolean[total];
        int[] nodeBudget = new int[] { 0 };

        for (int attempt = 0; attempt < 8; attempt++) {
            java.util.Arrays.fill(used, false);
            nodeBudget[0] = 0;
            int start = random.nextInt(total);
            path[0] = start;
            used[start] = true;
            if (dfs(start, 1, rows, cols, path, used, random, nodeBudget, 35000)) {
                return path;
            }
        }
        return snakePath(rows, cols, random.nextBoolean(), random.nextBoolean());
    }

    private static boolean dfs(int current, int depth, int rows, int cols,
                               int[] path, boolean[] used, Random random,
                               int[] nodeBudget, int maxNodes) {
        if (depth == path.length) return true;
        if (++nodeBudget[0] > maxNodes) return false;

        ArrayList<Integer> candidates = neighbors(current, rows, cols, used);
        // Warnsdorff-style ordering: visit cells with fewer onward choices first.
        Collections.shuffle(candidates, random);
        final boolean[] usedRef = used;
        Collections.sort(candidates, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                return Integer.compare(
                        onwardCount(a.intValue(), rows, cols, usedRef),
                        onwardCount(b.intValue(), rows, cols, usedRef));
            }
        });

        for (Integer candidate : candidates) {
            int next = candidate.intValue();
            if (used[next]) continue;
            used[next] = true;
            path[depth] = next;
            if (dfs(next, depth + 1, rows, cols, path, used, random, nodeBudget, maxNodes)) {
                return true;
            }
            used[next] = false;
            if (nodeBudget[0] > maxNodes) return false;
        }
        return false;
    }

    private static ArrayList<Integer> neighbors(int cell, int rows, int cols, boolean[] used) {
        ArrayList<Integer> result = new ArrayList<Integer>(4);
        int row = cell / cols;
        int col = cell % cols;
        if (row > 0 && !used[cell - cols]) result.add(cell - cols);
        if (row + 1 < rows && !used[cell + cols]) result.add(cell + cols);
        if (col > 0 && !used[cell - 1]) result.add(cell - 1);
        if (col + 1 < cols && !used[cell + 1]) result.add(cell + 1);
        return result;
    }

    private static int onwardCount(int cell, int rows, int cols, boolean[] used) {
        int count = 0;
        int row = cell / cols;
        int col = cell % cols;
        if (row > 0 && !used[cell - cols]) count++;
        if (row + 1 < rows && !used[cell + cols]) count++;
        if (col > 0 && !used[cell - 1]) count++;
        if (col + 1 < cols && !used[cell + 1]) count++;
        return count;
    }

    private static int[] snakePath(int rows, int cols, boolean verticalFirst, boolean reverse) {
        int[] path = new int[rows * cols];
        int index = 0;
        if (!verticalFirst) {
            for (int r = 0; r < rows; r++) {
                boolean leftToRight = (r % 2 == 0);
                for (int step = 0; step < cols; step++) {
                    int c = leftToRight ? step : cols - 1 - step;
                    path[index++] = r * cols + c;
                }
            }
        } else {
            for (int c = 0; c < cols; c++) {
                boolean topToBottom = (c % 2 == 0);
                for (int step = 0; step < rows; step++) {
                    int r = topToBottom ? step : rows - 1 - step;
                    path[index++] = r * cols + c;
                }
            }
        }
        if (reverse) {
            for (int i = 0; i < path.length / 2; i++) {
                int other = path.length - 1 - i;
                int temp = path[i];
                path[i] = path[other];
                path[other] = temp;
            }
        }
        return path;
    }

    private static void addWalls(int rows, int cols, int[] solution, int level,
                                 Random random, boolean[][] horizontalWalls,
                                 boolean[][] verticalWalls) {
        // Add a visible training barrier from the very first board.
        // Barriers are selected only from unused edges, so the generated
        // Hamiltonian solution remains guaranteed to be playable.
        boolean[][] usedHorizontal = new boolean[rows - 1][cols];
        boolean[][] usedVertical = new boolean[rows][cols - 1];
        for (int i = 1; i < solution.length; i++) {
            int first = solution[i - 1];
            int second = solution[i];
            int r1 = first / cols;
            int c1 = first % cols;
            int r2 = second / cols;
            int c2 = second % cols;
            if (r1 != r2) usedHorizontal[Math.min(r1, r2)][c1] = true;
            else usedVertical[r1][Math.min(c1, c2)] = true;
        }

        ArrayList<int[]> candidates = new ArrayList<int[]>();
        // Type 0 = horizontal boundary; type 1 = vertical boundary.
        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols; c++) {
                if (!usedHorizontal[r][c]) candidates.add(new int[] { 0, r, c });
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                if (!usedVertical[r][c]) candidates.add(new int[] { 1, r, c });
            }
        }
        Collections.shuffle(candidates, random);

        int progressiveWalls = Math.max(1, (level + 3) / 5);
        int wallCap = Math.max(1, (rows * cols) / 4);
        int targetWalls = Math.min(candidates.size(), Math.min(wallCap, progressiveWalls));
        for (int i = 0; i < targetWalls; i++) {
            int[] edge = candidates.get(i);
            if (edge[0] == 0) horizontalWalls[edge[1]][edge[2]] = true;
            else verticalWalls[edge[1]][edge[2]] = true;
        }
    }
}
