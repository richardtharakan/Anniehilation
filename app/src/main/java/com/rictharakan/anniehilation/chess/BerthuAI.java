package com.rictharakan.anniehilation.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A lightweight native chess opponent for Anniehilation.
 * Berthu plays Black. Search runs on a private copy of the live position,
 * so the UI thread and the displayed game are never mutated by analysis.
 */
public final class BerthuAI {
    public static final int CHILL = 1;
    public static final int MISCHIEF = 2;
    public static final int ROYAL_MENACE = 3;

    private static final int MATE_SCORE = 100000;
    private static final int INFINITY = 1_000_000;
    private static final int NODE_LIMIT = 12000;

    private static final int[][] CENTER = {
            {0, 1, 2, 3, 3, 2, 1, 0},
            {1, 2, 3, 4, 4, 3, 2, 1},
            {2, 3, 4, 5, 5, 4, 3, 2},
            {3, 4, 5, 6, 6, 5, 4, 3},
            {3, 4, 5, 6, 6, 5, 4, 3},
            {2, 3, 4, 5, 5, 4, 3, 2},
            {1, 2, 3, 4, 4, 3, 2, 1},
            {0, 1, 2, 3, 3, 2, 1, 0}
    };

    private BerthuAI() { }

    /** Returns a legal Black move, or null if the position has no legal moves. */
    public static ChessEngine.Move chooseMove(ChessEngine livePosition, int difficulty) {
        if (livePosition == null || livePosition.isWhiteToMove()) return null;

        final ChessEngine search = new ChessEngine(livePosition);
        List<ChessEngine.Move> rootMoves = orderedMoves(search);
        if (rootMoves.isEmpty()) return null;

        final int depth = Math.max(CHILL, Math.min(ROYAL_MENACE, difficulty));
        final SearchBudget budget = new SearchBudget(NODE_LIMIT);
        final ArrayList<ScoredMove> scored = new ArrayList<ScoredMove>();
        int best = -INFINITY;
        int alpha = -INFINITY;

        for (ChessEngine.Move move : rootMoves) {
            if (budget.exhausted()) break;
            if (!search.makeMove(move)) continue;
            int score = alphaBeta(search, depth - 1, alpha, INFINITY, 1, budget);
            search.undo();
            scored.add(new ScoredMove(move, score));
            if (score > best) best = score;
            if (score > alpha) alpha = score;
        }

        if (scored.isEmpty()) return rootMoves.get(0);
        Collections.sort(scored, (a, b) -> Integer.compare(b.score, a.score));

        // Berthu is competent, but not perfectly predictable: choose among
        // near-equal top moves so the opponent feels a little mischievous.
        int tolerance = depth == CHILL ? 115 : (depth == MISCHIEF ? 45 : 18);
        int optionCount = 1;
        while (optionCount < scored.size()
                && scored.get(0).score - scored.get(optionCount).score <= tolerance) {
            optionCount++;
        }
        int pick = optionCount <= 1 ? 0 : new Random().nextInt(Math.min(optionCount, 3));
        return scored.get(pick).move;
    }

    private static int alphaBeta(ChessEngine engine, int depth, int alpha, int beta,
                                 int ply, SearchBudget budget) {
        budget.visit();
        List<ChessEngine.Move> moves = orderedMoves(engine);

        if (moves.isEmpty()) {
            if (engine.isInCheck()) {
                return engine.isWhiteToMove() ? MATE_SCORE - ply : -MATE_SCORE + ply;
            }
            return 0;
        }
        if (engine.isInsufficientMaterial() || engine.isDrawByFiftyMoveRule()) return 0;
        if (depth <= 0 || budget.exhausted()) return evaluate(engine, moves);

        boolean blackToMove = !engine.isWhiteToMove();
        if (blackToMove) {
            int value = -INFINITY;
            for (ChessEngine.Move move : moves) {
                if (!engine.makeMove(move)) continue;
                value = Math.max(value, alphaBeta(engine, depth - 1, alpha, beta, ply + 1, budget));
                engine.undo();
                alpha = Math.max(alpha, value);
                if (alpha >= beta || budget.exhausted()) break;
            }
            return value;
        } else {
            int value = INFINITY;
            for (ChessEngine.Move move : moves) {
                if (!engine.makeMove(move)) continue;
                value = Math.min(value, alphaBeta(engine, depth - 1, alpha, beta, ply + 1, budget));
                engine.undo();
                beta = Math.min(beta, value);
                if (alpha >= beta || budget.exhausted()) break;
            }
            return value;
        }
    }

    /** Static score from Berthu/Black's point of view. */
    private static int evaluate(ChessEngine engine, List<ChessEngine.Move> availableMoves) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            char piece = engine.getPieceAt(square);
            if (piece == ChessEngine.EMPTY) continue;
            boolean black = Character.isLowerCase(piece);
            char kind = Character.toLowerCase(piece);
            int value;
            switch (kind) {
                case 'p': value = 100; break;
                case 'n': value = 320; break;
                case 'b': value = 335; break;
                case 'r': value = 500; break;
                case 'q': value = 900; break;
                case 'k': value = 20000; break;
                default: value = 0;
            }

            int row = ChessEngine.row(square);
            int col = ChessEngine.col(square);
            int positional = 0;
            if (kind == 'n' || kind == 'b' || kind == 'q') {
                positional += CENTER[row][col] * (kind == 'n' ? 5 : 3);
            }
            if (kind == 'p') {
                int advance = black ? row - 1 : 6 - row;
                positional += Math.max(0, advance) * 7;
                if (col >= 2 && col <= 5) positional += 5;
            }
            if (kind == 'r' && (col == 3 || col == 4)) positional += 7;
            int signed = value + positional;
            score += black ? signed : -signed;
        }

        int mobility = availableMoves.size() * 2;
        score += engine.isWhiteToMove() ? -mobility : mobility;
        if (engine.isInCheck()) score += engine.isWhiteToMove() ? -28 : 28;
        return score;
    }

    private static List<ChessEngine.Move> orderedMoves(final ChessEngine engine) {
        ArrayList<ChessEngine.Move> moves = new ArrayList<ChessEngine.Move>(engine.getLegalMoves());
        Collections.sort(moves, new Comparator<ChessEngine.Move>() {
            @Override public int compare(ChessEngine.Move a, ChessEngine.Move b) {
                return Integer.compare(orderScore(engine, b), orderScore(engine, a));
            }
        });
        return moves;
    }

    private static int orderScore(ChessEngine engine, ChessEngine.Move move) {
        char attacker = engine.getPieceAt(move.from);
        char victim = engine.getPieceAt(move.to);
        int score = 0;
        if (victim != ChessEngine.EMPTY) {
            score += 1000 + pieceValue(victim) * 10 - pieceValue(attacker);
        }
        if (move.promotion != 0) score += 800 + pieceValue(move.promotion);
        // En-passant captures land on an empty target square. A diagonal pawn
        // move into an empty square is sufficient to recognise it here.
        if (Character.toLowerCase(attacker) == 'p'
                && ChessEngine.col(move.from) != ChessEngine.col(move.to)
                && victim == ChessEngine.EMPTY) score += 950;
        return score;
    }

    private static int pieceValue(char piece) {
        switch (Character.toLowerCase(piece)) {
            case 'p': return 100;
            case 'n': return 320;
            case 'b': return 335;
            case 'r': return 500;
            case 'q': return 900;
            case 'k': return 20000;
            default: return 0;
        }
    }

    private static final class SearchBudget {
        final int limit;
        int visited;
        SearchBudget(int limit) { this.limit = limit; }
        void visit() { visited++; }
        boolean exhausted() { return visited >= limit; }
    }

    private static final class ScoredMove {
        final ChessEngine.Move move;
        final int score;
        ScoredMove(ChessEngine.Move move, int score) {
            this.move = move;
            this.score = score;
        }
    }
}
