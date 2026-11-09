package com.rictharakan.anniehilation.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small dependency-free chess rules engine for Anniehilation.
 * Board orientation: index 0 is a8; index 63 is h1. White is at the bottom.
 * White pieces are uppercase. Black pieces are lowercase.
 */
public final class ChessEngine {

    public static final char EMPTY = '.';

    public static final class Move {
        public final int from;
        public final int to;
        public final char promotion;
        final boolean enPassant;
        final boolean castle;

        Move(int from, int to, char promotion, boolean enPassant, boolean castle) {
            this.from = from;
            this.to = to;
            this.promotion = promotion;
            this.enPassant = enPassant;
            this.castle = castle;
        }

        @Override public String toString() {
            return squareName(from) + "-" + squareName(to)
                    + (promotion == 0 ? "" : "=" + Character.toUpperCase(promotion));
        }
    }

    private static final class Position {
        final char[] board;
        boolean whiteToMove;
        boolean whiteKingSide;
        boolean whiteQueenSide;
        boolean blackKingSide;
        boolean blackQueenSide;
        int enPassantSquare;
        int halfmoveClock;
        int fullmoveNumber;
        Move lastMove;

        Position() {
            board = new char[64];
            whiteToMove = true;
            whiteKingSide = whiteQueenSide = blackKingSide = blackQueenSide = true;
            enPassantSquare = -1;
            fullmoveNumber = 1;
            String[] ranks = {
                    "rnbqkbnr",
                    "pppppppp",
                    "........",
                    "........",
                    "........",
                    "........",
                    "PPPPPPPP",
                    "RNBQKBNR"
            };
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) board[r * 8 + c] = ranks[r].charAt(c);
            }
        }

        Position(Position source) {
            board = source.board.clone();
            whiteToMove = source.whiteToMove;
            whiteKingSide = source.whiteKingSide;
            whiteQueenSide = source.whiteQueenSide;
            blackKingSide = source.blackKingSide;
            blackQueenSide = source.blackQueenSide;
            enPassantSquare = source.enPassantSquare;
            halfmoveClock = source.halfmoveClock;
            fullmoveNumber = source.fullmoveNumber;
            lastMove = source.lastMove;
        }
    }

    private final ArrayList<Position> history = new ArrayList<Position>();
    private Position position;

    public ChessEngine() {
        reset();
    }

    /** Creates an independent search copy, including the full undo history. */
    public ChessEngine(ChessEngine source) {
        if (source == null) {
            reset();
            return;
        }
        history.clear();
        for (Position oldPosition : source.history) {
            history.add(new Position(oldPosition));
        }
        position = history.isEmpty()
                ? new Position()
                : history.get(history.size() - 1);
        if (history.isEmpty()) history.add(position);
    }

    public void reset() {
        position = new Position();
        history.clear();
        history.add(position);
    }

    public boolean isWhiteToMove() { return position.whiteToMove; }
    public char getPieceAt(int square) { return square >= 0 && square < 64 ? position.board[square] : EMPTY; }
    public Move getLastMove() { return position.lastMove; }
    public int getHalfmoveClock() { return position.halfmoveClock; }
    public int getFullmoveNumber() { return position.fullmoveNumber; }
    public int getMoveCount() { return Math.max(0, history.size() - 1); }
    public boolean canUndo() { return history.size() > 1; }

    public boolean undo() {
        if (!canUndo()) return false;
        history.remove(history.size() - 1);
        position = history.get(history.size() - 1);
        return true;
    }

    public List<Move> getLegalMoves() {
        return Collections.unmodifiableList(generateLegalMoves(position));
    }

    public List<Move> getLegalMovesFrom(int square) {
        ArrayList<Move> result = new ArrayList<Move>();
        for (Move move : generateLegalMoves(position)) {
            if (move.from == square) result.add(move);
        }
        return result;
    }

    public boolean makeMove(Move requested) {
        if (requested == null) return false;
        for (Move legal : generateLegalMoves(position)) {
            if (legal.from == requested.from && legal.to == requested.to
                    && Character.toLowerCase(legal.promotion) == Character.toLowerCase(requested.promotion)) {
                position = apply(position, legal);
                history.add(position);
                return true;
            }
        }
        return false;
    }

    public boolean isInCheck() {
        return isInCheck(position, position.whiteToMove);
    }

    public boolean isCheckmate() {
        return isInCheck() && generateLegalMoves(position).isEmpty();
    }

    public boolean isStalemate() {
        return !isInCheck() && generateLegalMoves(position).isEmpty();
    }

    public boolean isDrawByFiftyMoveRule() {
        return position.halfmoveClock >= 100;
    }

    public boolean isInsufficientMaterial() {
        int bishops = 0;
        int knights = 0;
        int other = 0;
        int bishopColor = -1;
        boolean bishopsSameColor = true;
        for (int sq = 0; sq < 64; sq++) {
            char p = position.board[sq];
            if (p == EMPTY || p == 'K' || p == 'k') continue;
            char kind = Character.toLowerCase(p);
            if (kind == 'b') {
                bishops++;
                int color = (row(sq) + col(sq)) & 1;
                if (bishopColor < 0) bishopColor = color;
                else if (bishopColor != color) bishopsSameColor = false;
            } else if (kind == 'n') knights++;
            else other++;
        }
        if (other > 0) return false;
        if (bishops == 0 && knights == 0) return true;
        if (bishops == 1 && knights == 0) return true;
        if (bishops == 0 && knights == 1) return true;
        return knights == 0 && bishops > 0 && bishopsSameColor;
    }

    public String getStatusText() {
        if (isCheckmate()) return "CHECKMATE — " + (position.whiteToMove ? "BLACK" : "WHITE") + " WINS";
        if (isStalemate()) return "DRAW — STALEMATE";
        if (isInsufficientMaterial()) return "DRAW — INSUFFICIENT MATERIAL";
        if (isDrawByFiftyMoveRule()) return "DRAW AVAILABLE — 50-MOVE RULE";
        return (position.whiteToMove ? "WHITE" : "BLACK") + " TO MOVE" + (isInCheck() ? " · CHECK" : "");
    }

    public static int square(int row, int col) { return row * 8 + col; }
    public static int row(int square) { return square / 8; }
    public static int col(int square) { return square % 8; }

    public static String squareName(int square) {
        if (square < 0 || square >= 64) return "--";
        return "abcdefgh".substring(col(square), col(square) + 1) + (8 - row(square));
    }

    private static boolean isWhitePiece(char piece) {
        return piece >= 'A' && piece <= 'Z';
    }

    private static boolean isBlackPiece(char piece) {
        return piece >= 'a' && piece <= 'z';
    }

    private static boolean isEnemy(char piece, boolean white) {
        return white ? isBlackPiece(piece) : isWhitePiece(piece);
    }

    private static boolean inside(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private static ArrayList<Move> generateLegalMoves(Position source) {
        ArrayList<Move> legal = new ArrayList<Move>();
        ArrayList<Move> pseudo = generatePseudoMoves(source);
        boolean movingWhite = source.whiteToMove;
        for (Move move : pseudo) {
            Position next = apply(source, move);
            if (!isInCheck(next, movingWhite)) legal.add(move);
        }
        return legal;
    }

    private static ArrayList<Move> generatePseudoMoves(Position p) {
        ArrayList<Move> moves = new ArrayList<Move>();
        boolean white = p.whiteToMove;
        for (int from = 0; from < 64; from++) {
            char piece = p.board[from];
            if (piece == EMPTY || isWhitePiece(piece) != white) continue;
            char kind = Character.toLowerCase(piece);
            int r = row(from), c = col(from);
            if (kind == 'p') addPawnMoves(p, moves, from, white);
            else if (kind == 'n') {
                int[][] steps = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
                for (int[] d : steps) addStep(p, moves, from, r + d[0], c + d[1]);
            } else if (kind == 'b') addSlidingMoves(p, moves, from, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
            else if (kind == 'r') addSlidingMoves(p, moves, from, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
            else if (kind == 'q') addSlidingMoves(p, moves, from, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},{-1,0},{1,0},{0,-1},{0,1}});
            else if (kind == 'k') {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr != 0 || dc != 0) addStep(p, moves, from, r + dr, c + dc);
                    }
                }
                addCastlingMoves(p, moves, from, white);
            }
        }
        return moves;
    }

    private static void addPawnMoves(Position p, ArrayList<Move> moves, int from, boolean white) {
        int r = row(from), c = col(from);
        int direction = white ? -1 : 1;
        int nextRow = r + direction;
        if (!inside(nextRow, c)) return;
        int one = square(nextRow, c);
        if (p.board[one] == EMPTY) {
            addPawnMoveOrPromotions(moves, from, one, white, false);
            int startRow = white ? 6 : 1;
            int twoRow = r + direction * 2;
            if (r == startRow && p.board[square(twoRow, c)] == EMPTY) {
                moves.add(new Move(from, square(twoRow, c), (char) 0, false, false));
            }
        }
        for (int dc : new int[]{-1, 1}) {
            int targetCol = c + dc;
            if (!inside(nextRow, targetCol)) continue;
            int target = square(nextRow, targetCol);
            if (isEnemy(p.board[target], white)) {
                addPawnMoveOrPromotions(moves, from, target, white, false);
            } else if (target == p.enPassantSquare && p.board[target] == EMPTY) {
                moves.add(new Move(from, target, (char) 0, true, false));
            }
        }
    }

    private static void addPawnMoveOrPromotions(ArrayList<Move> moves, int from, int to,
                                                 boolean white, boolean enPassant) {
        int promotionRow = white ? 0 : 7;
        if (row(to) == promotionRow) {
            moves.add(new Move(from, to, 'q', enPassant, false));
            moves.add(new Move(from, to, 'r', enPassant, false));
            moves.add(new Move(from, to, 'b', enPassant, false));
            moves.add(new Move(from, to, 'n', enPassant, false));
        } else {
            moves.add(new Move(from, to, (char) 0, enPassant, false));
        }
    }

    private static void addStep(Position p, ArrayList<Move> moves, int from, int r, int c) {
        if (!inside(r, c)) return;
        char target = p.board[square(r, c)];
        if (target == EMPTY || isEnemy(target, p.whiteToMove)) {
            if (Character.toLowerCase(target) != 'k') {
                moves.add(new Move(from, square(r, c), (char) 0, false, false));
            }
        }
    }

    private static void addSlidingMoves(Position p, ArrayList<Move> moves, int from, int[][] directions) {
        int r = row(from), c = col(from);
        for (int[] d : directions) {
            int nr = r + d[0], nc = c + d[1];
            while (inside(nr, nc)) {
                int to = square(nr, nc);
                char target = p.board[to];
                if (target == EMPTY) {
                    moves.add(new Move(from, to, (char) 0, false, false));
                } else {
                    if (isEnemy(target, p.whiteToMove) && Character.toLowerCase(target) != 'k') {
                        moves.add(new Move(from, to, (char) 0, false, false));
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private static void addCastlingMoves(Position p, ArrayList<Move> moves, int from, boolean white) {
        int home = white ? square(7, 4) : square(0, 4);
        if (from != home || isSquareAttacked(p, home, !white)) return;
        char rook = white ? 'R' : 'r';
        if (white ? p.whiteKingSide : p.blackKingSide) {
            int rookSquare = white ? square(7, 7) : square(0, 7);
            if (p.board[rookSquare] == rook && p.board[home + 1] == EMPTY && p.board[home + 2] == EMPTY
                    && !isSquareAttacked(p, home + 1, !white)
                    && !isSquareAttacked(p, home + 2, !white)) {
                moves.add(new Move(from, home + 2, (char) 0, false, true));
            }
        }
        if (white ? p.whiteQueenSide : p.blackQueenSide) {
            int rookSquare = white ? square(7, 0) : square(0, 0);
            if (p.board[rookSquare] == rook && p.board[home - 1] == EMPTY
                    && p.board[home - 2] == EMPTY && p.board[home - 3] == EMPTY
                    && !isSquareAttacked(p, home - 1, !white)
                    && !isSquareAttacked(p, home - 2, !white)) {
                moves.add(new Move(from, home - 2, (char) 0, false, true));
            }
        }
    }

    private static Position apply(Position source, Move move) {
        Position next = new Position(source);
        char moving = next.board[move.from];
        char captured = next.board[move.to];
        boolean white = source.whiteToMove;
        next.board[move.from] = EMPTY;
        next.board[move.to] = move.promotion == 0
                ? moving
                : (white ? Character.toUpperCase(move.promotion) : Character.toLowerCase(move.promotion));

        if (move.enPassant) {
            int capturedPawn = move.to + (white ? 8 : -8);
            captured = next.board[capturedPawn];
            next.board[capturedPawn] = EMPTY;
        }

        if (move.castle) {
            if (move.to > move.from) {
                int rookFrom = move.from + 3;
                int rookTo = move.from + 1;
                next.board[rookTo] = next.board[rookFrom];
                next.board[rookFrom] = EMPTY;
            } else {
                int rookFrom = move.from - 4;
                int rookTo = move.from - 1;
                next.board[rookTo] = next.board[rookFrom];
                next.board[rookFrom] = EMPTY;
            }
        }

        if (moving == 'K') { next.whiteKingSide = false; next.whiteQueenSide = false; }
        if (moving == 'k') { next.blackKingSide = false; next.blackQueenSide = false; }
        updateRookRights(next, moving, move.from);
        updateRookRights(next, captured, move.to);

        next.enPassantSquare = -1;
        if (Character.toLowerCase(moving) == 'p' && Math.abs(move.to - move.from) == 16) {
            next.enPassantSquare = (move.from + move.to) / 2;
        }
        if (Character.toLowerCase(moving) == 'p' || captured != EMPTY) next.halfmoveClock = 0;
        else next.halfmoveClock++;
        if (!white) next.fullmoveNumber++;
        next.whiteToMove = !white;
        next.lastMove = move;
        return next;
    }

    private static void updateRookRights(Position p, char piece, int square) {
        if (piece == 'R') {
            if (square == 63) p.whiteKingSide = false;
            if (square == 56) p.whiteQueenSide = false;
        } else if (piece == 'r') {
            if (square == 7) p.blackKingSide = false;
            if (square == 0) p.blackQueenSide = false;
        }
    }

    private static boolean isInCheck(Position p, boolean whiteKing) {
        char king = whiteKing ? 'K' : 'k';
        int kingSquare = -1;
        for (int i = 0; i < 64; i++) {
            if (p.board[i] == king) { kingSquare = i; break; }
        }
        return kingSquare < 0 || isSquareAttacked(p, kingSquare, !whiteKing);
    }

    private static boolean isSquareAttacked(Position p, int target, boolean byWhite) {
        int tr = row(target), tc = col(target);
        for (int from = 0; from < 64; from++) {
            char piece = p.board[from];
            if (piece == EMPTY || isWhitePiece(piece) != byWhite) continue;
            char kind = Character.toLowerCase(piece);
            int fr = row(from), fc = col(from);
            int dr = tr - fr, dc = tc - fc;
            if (kind == 'p') {
                int attackRow = byWhite ? -1 : 1;
                if (dr == attackRow && Math.abs(dc) == 1) return true;
            } else if (kind == 'n') {
                if ((Math.abs(dr) == 2 && Math.abs(dc) == 1)
                        || (Math.abs(dr) == 1 && Math.abs(dc) == 2)) return true;
            } else if (kind == 'k') {
                if (Math.max(Math.abs(dr), Math.abs(dc)) == 1) return true;
            } else {
                boolean diagonal = Math.abs(dr) == Math.abs(dc) && dr != 0;
                boolean straight = (dr == 0) != (dc == 0);
                boolean attacks = kind == 'q' ? (diagonal || straight)
                        : kind == 'b' ? diagonal : straight;
                if (!attacks) continue;
                int stepR = Integer.compare(dr, 0);
                int stepC = Integer.compare(dc, 0);
                int rr = fr + stepR;
                int cc = fc + stepC;
                boolean blocked = false;
                while (rr != tr || cc != tc) {
                    if (p.board[square(rr, cc)] != EMPTY) { blocked = true; break; }
                    rr += stepR;
                    cc += stepC;
                }
                if (!blocked) return true;
            }
        }
        return false;
    }
}
