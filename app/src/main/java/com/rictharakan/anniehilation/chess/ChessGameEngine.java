/*
 * Copyright 2026 Anniehilation contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rictharakan.anniehilation.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Self-contained standard-chess rules engine for Anniehilation.
 *
 * Original Java implementation: 0x88 board, legal move generation, FEN,
 * SAN/PGN history, castling, en passant, promotion, check/mate/stalemate,
 * claimable and automatic draws, undo, and repetition tracking.
 * No third-party runtime dependency is required.
 */
public final class ChessGameEngine {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int WHITE = 1;
    private static final int BLACK = -1;
    private static final int WK = 1;
    private static final int WQ = 2;
    private static final int BK = 4;
    private static final int BQ = 8;

    private static final int[] KNIGHT_STEPS = {-33, -31, -18, -14, 14, 18, 31, 33};
    private static final int[] BISHOP_STEPS = {-17, -15, 15, 17};
    private static final int[] ROOK_STEPS = {-16, -1, 1, 16};
    private static final int[] KING_STEPS = {-17, -16, -15, -1, 1, 15, 16, 17};
    private static final char[] PROMOTIONS = {'q', 'r', 'b', 'n'};

    public enum GameResult {
        ACTIVE,
        CHECK,
        WHITE_WINS_CHECKMATE,
        BLACK_WINS_CHECKMATE,
        STALEMATE,
        DRAW_INSUFFICIENT_MATERIAL,
        DRAW_FIVEFOLD_REPETITION,
        DRAW_SEVENTY_FIVE_MOVE,
        DRAW_CLAIMED_REPETITION,
        DRAW_CLAIMED_FIFTY_MOVE,
        WHITE_WINS_RESIGNATION,
        BLACK_WINS_RESIGNATION
    }

    public static final class Move {
        public final int from;
        public final int to;
        public final char promotion;
        private final boolean enPassant;
        private final boolean castle;

        private Move(int from, int to, char promotion, boolean enPassant, boolean castle) {
            this.from = from;
            this.to = to;
            this.promotion = promotion;
            this.enPassant = enPassant;
            this.castle = castle;
        }

        public String toUci() {
            String result = squareName(from) + squareName(to);
            return promotion == '\0' ? result : result + Character.toLowerCase(promotion);
        }

        public String toString() { return toUci(); }
    }

    private static final class Position {
        final char[] board;
        int side;
        int castling;
        int enPassant;
        int halfmove;
        int fullmove;
        int whiteKing;
        int blackKing;
        int lastFrom = -1;
        int lastTo = -1;

        Position() {
            board = new char[128];
            for (int i = 0; i < 128; i++) if (onBoard(i)) board[i] = '.';
            side = WHITE;
            castling = WK | WQ | BK | BQ;
            enPassant = -1;
            halfmove = 0;
            fullmove = 1;
            whiteKing = 4;
            blackKing = 116;
        }

        Position(Position other) {
            board = other.board.clone();
            side = other.side;
            castling = other.castling;
            enPassant = other.enPassant;
            halfmove = other.halfmove;
            fullmove = other.fullmove;
            whiteKing = other.whiteKing;
            blackKing = other.blackKing;
            lastFrom = other.lastFrom;
            lastTo = other.lastTo;
        }
    }

    private static final class MoveRecord {
        final String uci;
        final String san;
        MoveRecord(String uci, String san) { this.uci = uci; this.san = san; }
    }

    private final List<Position> history = new ArrayList<>();
    private final List<MoveRecord> moves = new ArrayList<>();
    private GameResult claimedResult = null;
    private GameResult resignedResult = null;

    public ChessGameEngine() { this(INITIAL_FEN); }

    public ChessGameEngine(String fen) {
        history.add(parseFen(fen));
    }

    private Position current() { return history.get(history.size() - 1); }

    public void restart() {
        history.clear();
        moves.clear();
        claimedResult = null;
        resignedResult = null;
        history.add(parseFen(INITIAL_FEN));
    }

    public int getSideToMove() { return current().side; }
    public boolean isWhiteToMove() { return current().side == WHITE; }
    public char getPieceAt(int square) {
        if (!onBoard(square)) return '.';
        return current().board[square];
    }
    public int getLastMoveFrom() { return current().lastFrom; }
    public int getLastMoveTo() { return current().lastTo; }
    public int getPlyCount() { return moves.size(); }
    public boolean canUndo() { return history.size() > 1; }
    public boolean isInCheck() { return isKingAttacked(current(), current().side); }
    public String getFen() { return toFen(current()); }

    public List<Move> legalMoves() {
        return Collections.unmodifiableList(generateLegalMoves(current()));
    }

    public List<Move> legalMovesFrom(int from) {
        List<Move> result = new ArrayList<>();
        for (Move move : generateLegalMoves(current())) if (move.from == from) result.add(move);
        return Collections.unmodifiableList(result);
    }

    public boolean play(int from, int to, char promotion) {
        if (isGameOver()) return false;
        char promo = promotion == '\0' ? '\0' : Character.toLowerCase(promotion);
        for (Move move : generateLegalMoves(current())) {
            if (move.from == from && move.to == to && move.promotion == promo) {
                playLegalMove(move);
                return true;
            }
        }
        return false;
    }

    public boolean playUci(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) return false;
        int from = parseSquare(uci.substring(0, 2));
        int to = parseSquare(uci.substring(2, 4));
        if (from < 0 || to < 0) return false;
        char promotion = uci.length() == 5 ? Character.toLowerCase(uci.charAt(4)) : '\0';
        return play(from, to, promotion);
    }

    private void playLegalMove(Move move) {
        Position before = current();
        String san = toSan(before, move);
        Position after = apply(before, move);
        history.add(after);
        moves.add(new MoveRecord(move.toUci(), san));
        claimedResult = null;
        resignedResult = null;
    }

    public boolean undo() {
        if (history.size() <= 1) return false;
        history.remove(history.size() - 1);
        if (!moves.isEmpty()) moves.remove(moves.size() - 1);
        claimedResult = null;
        resignedResult = null;
        return true;
    }

    public boolean resign() {
        if (isGameOver()) return false;
        resignedResult = current().side == WHITE
                ? GameResult.BLACK_WINS_RESIGNATION
                : GameResult.WHITE_WINS_RESIGNATION;
        return true;
    }

    public boolean canClaimThreefoldRepetition() {
        if (isGameOver()) return false;
        return repetitionCount(current()) >= 3;
    }

    public boolean canClaimFiftyMoveDraw() {
        return !isGameOver() && current().halfmove >= 100;
    }

    public boolean canClaimDraw() {
        return canClaimThreefoldRepetition() || canClaimFiftyMoveDraw();
    }

    public boolean claimDraw() {
        if (isGameOver()) return false;
        if (canClaimThreefoldRepetition()) claimedResult = GameResult.DRAW_CLAIMED_REPETITION;
        else if (canClaimFiftyMoveDraw()) claimedResult = GameResult.DRAW_CLAIMED_FIFTY_MOVE;
        else return false;
        return true;
    }

    public GameResult getGameResult() {
        if (resignedResult != null) return resignedResult;
        if (claimedResult != null) return claimedResult;

        Position p = current();
        List<Move> legal = generateLegalMoves(p);
        boolean check = isKingAttacked(p, p.side);

        // Checkmate/stalemate takes precedence over automatic move-count/repetition draws.
        if (legal.isEmpty()) {
            if (check) return p.side == WHITE
                    ? GameResult.BLACK_WINS_CHECKMATE
                    : GameResult.WHITE_WINS_CHECKMATE;
            return GameResult.STALEMATE;
        }
        if (isInsufficientMaterial(p)) return GameResult.DRAW_INSUFFICIENT_MATERIAL;
        if (p.halfmove >= 150) return GameResult.DRAW_SEVENTY_FIVE_MOVE;
        if (repetitionCount(p) >= 5) return GameResult.DRAW_FIVEFOLD_REPETITION;
        return check ? GameResult.CHECK : GameResult.ACTIVE;
    }

    public boolean isGameOver() {
        GameResult result = getGameResult();
        return result != GameResult.ACTIVE && result != GameResult.CHECK;
    }

    public String getStatusText() {
        GameResult result = getGameResult();
        switch (result) {
            case CHECK: return (current().side == WHITE ? "White" : "Black") + " to move — Check";
            case ACTIVE: return (current().side == WHITE ? "White" : "Black") + " to move";
            case WHITE_WINS_CHECKMATE: return "Checkmate — White wins";
            case BLACK_WINS_CHECKMATE: return "Checkmate — Black wins";
            case STALEMATE: return "Draw — stalemate";
            case DRAW_INSUFFICIENT_MATERIAL: return "Draw — insufficient material";
            case DRAW_FIVEFOLD_REPETITION: return "Draw — fivefold repetition";
            case DRAW_SEVENTY_FIVE_MOVE: return "Draw — 75-move rule";
            case DRAW_CLAIMED_REPETITION: return "Draw claimed — threefold repetition";
            case DRAW_CLAIMED_FIFTY_MOVE: return "Draw claimed — 50-move rule";
            case WHITE_WINS_RESIGNATION: return "White wins — Black resigned";
            case BLACK_WINS_RESIGNATION: return "Black wins — White resigned";
            default: return "Game finished";
        }
    }

    public String getMoveHistoryText() {
        if (moves.isEmpty()) return "No moves yet";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if ((i & 1) == 0) {
                if (text.length() > 0) text.append('\n');
                text.append((i / 2) + 1).append(". ");
            } else {
                text.append(' ');
            }
            text.append(moves.get(i).san);
        }
        return text.toString();
    }

    public String getPgn() {
        String result = "*";
        GameResult state = getGameResult();
        if (state == GameResult.WHITE_WINS_CHECKMATE || state == GameResult.WHITE_WINS_RESIGNATION) result = "1-0";
        else if (state == GameResult.BLACK_WINS_CHECKMATE || state == GameResult.BLACK_WINS_RESIGNATION) result = "0-1";
        else if (state != GameResult.ACTIVE && state != GameResult.CHECK) result = "1/2-1/2";

        StringBuilder pgn = new StringBuilder();
        pgn.append("[Event \"Anniehilation Chess\"]\n");
        pgn.append("[Site \"Local\"]\n");
        pgn.append("[Date \"????.??.??\"]\n");
        pgn.append("[Round \"-\"]\n");
        pgn.append("[White \"White\"]\n");
        pgn.append("[Black \"Black\"]\n");
        pgn.append("[Result \"").append(result).append("\"]\n\n");
        for (int i = 0; i < moves.size(); i++) {
            if ((i & 1) == 0) pgn.append((i / 2) + 1).append(". ");
            else pgn.append(' ');
            pgn.append(moves.get(i).san);
            if (i == moves.size() - 1) pgn.append(' ');
        }
        pgn.append(result);
        return pgn.toString();
    }

    public String getUciHistory() {
        StringBuilder text = new StringBuilder();
        for (MoveRecord record : moves) {
            if (text.length() > 0) text.append(',');
            text.append(record.uci);
        }
        return text.toString();
    }

    /** Returns only user-declared terminal outcomes that are not derivable from the move list. */
    public String getPersistentResultToken() {
        if (claimedResult != null) return claimedResult.name();
        if (resignedResult != null) return resignedResult.name();
        return "";
    }

    /** Restores a persisted draw claim or resignation after replaying the move list. */
    public boolean restorePersistentResultToken(String token) {
        if (token == null || token.trim().isEmpty()) return true;
        if (isGameOver()) return false;
        switch (token) {
            case "DRAW_CLAIMED_REPETITION":
                if (!canClaimThreefoldRepetition()) return false;
                claimedResult = GameResult.DRAW_CLAIMED_REPETITION;
                return true;
            case "DRAW_CLAIMED_FIFTY_MOVE":
                if (!canClaimFiftyMoveDraw()) return false;
                claimedResult = GameResult.DRAW_CLAIMED_FIFTY_MOVE;
                return true;
            case "WHITE_WINS_RESIGNATION":
                if (current().side != BLACK) return false;
                resignedResult = GameResult.WHITE_WINS_RESIGNATION;
                return true;
            case "BLACK_WINS_RESIGNATION":
                if (current().side != WHITE) return false;
                resignedResult = GameResult.BLACK_WINS_RESIGNATION;
                return true;
            default:
                return false;
        }
    }

    public boolean restoreUciHistory(String value) {
        restart();
        if (value == null || value.trim().isEmpty()) return true;
        String[] items = value.split(",");
        for (String item : items) {
            if (!playUci(item.trim())) {
                restart();
                return false;
            }
        }
        return true;
    }

    public static int square(int file, int rank) {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return -1;
        return rank * 16 + file;
    }

    public static int fileOf(int square) { return square & 7; }
    public static int rankOf(int square) { return square >> 4; }

    public static String squareName(int square) {
        if (!onBoard(square)) return "--";
        return "abcdefgh".substring(fileOf(square), fileOf(square) + 1)
                + (rankOf(square) + 1);
    }

    private static int parseSquare(String value) {
        if (value == null || value.length() != 2) return -1;
        int file = value.charAt(0) - 'a';
        int rank = value.charAt(1) - '1';
        return square(file, rank);
    }

    private static boolean onBoard(int square) {
        return square >= 0 && square < 128 && (square & 0x88) == 0;
    }

    private static int colorOf(char piece) {
        if (piece == '.' || piece == '\0') return 0;
        return Character.isUpperCase(piece) ? WHITE : BLACK;
    }

    private static char pieceType(char piece) { return Character.toLowerCase(piece); }

    private static Position parseFen(String fen) {
        if (fen == null) throw new IllegalArgumentException("FEN is null");
        String[] fields = fen.trim().split("\\s+");
        if (fields.length < 4) throw new IllegalArgumentException("FEN must contain at least four fields");
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN must have eight ranks");

        Position p = new Position();
        p.whiteKing = -1;
        p.blackKing = -1;
        int whiteKings = 0;
        int blackKings = 0;
        for (int row = 0; row < 8; row++) {
            int file = 0;
            String value = ranks[row];
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                } else if ("pnbrqkPNBRQK".indexOf(c) >= 0) {
                    if (file >= 8) throw new IllegalArgumentException("Too many squares in FEN rank");
                    int rank = 7 - row;
                    int sq = square(file, rank);
                    p.board[sq] = c;
                    if (c == 'K') { p.whiteKing = sq; whiteKings++; }
                    if (c == 'k') { p.blackKing = sq; blackKings++; }
                    file++;
                } else {
                    throw new IllegalArgumentException("Invalid FEN piece: " + c);
                }
            }
            if (file != 8) throw new IllegalArgumentException("FEN rank does not contain eight squares");
        }
        if (whiteKings != 1 || blackKings != 1) throw new IllegalArgumentException("FEN needs exactly one king of each color");

        p.side = "w".equals(fields[1]) ? WHITE : "b".equals(fields[1]) ? BLACK : 0;
        if (p.side == 0) throw new IllegalArgumentException("Invalid FEN side to move");
        p.castling = 0;
        if (!"-".equals(fields[2])) {
            for (int i = 0; i < fields[2].length(); i++) {
                switch (fields[2].charAt(i)) {
                    case 'K': p.castling |= WK; break;
                    case 'Q': p.castling |= WQ; break;
                    case 'k': p.castling |= BK; break;
                    case 'q': p.castling |= BQ; break;
                    default: throw new IllegalArgumentException("Invalid FEN castling rights");
                }
            }
        }
        p.enPassant = "-".equals(fields[3]) ? -1 : parseSquare(fields[3]);
        if (!"-".equals(fields[3]) && (p.enPassant < 0 || (rankOf(p.enPassant) != 2 && rankOf(p.enPassant) != 5))) {
            throw new IllegalArgumentException("Invalid FEN en-passant square");
        }
        p.halfmove = fields.length > 4 ? parseNonNegative(fields[4], "halfmove clock") : 0;
        p.fullmove = fields.length > 5 ? parseNonNegative(fields[5], "fullmove number") : 1;
        if (p.fullmove < 1) throw new IllegalArgumentException("FEN fullmove number must be positive");
        return p;
    }

    private static int parseNonNegative(String value, String label) {
        try {
            int n = Integer.parseInt(value);
            if (n < 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid FEN " + label);
        }
    }

    private static String toFen(Position p) {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                char piece = p.board[square(file, rank)];
                if (piece == '.') empty++;
                else {
                    if (empty > 0) { fen.append(empty); empty = 0; }
                    fen.append(piece);
                }
            }
            if (empty > 0) fen.append(empty);
            if (rank > 0) fen.append('/');
        }
        fen.append(p.side == WHITE ? " w " : " b ");
        if (p.castling == 0) fen.append('-');
        else {
            if ((p.castling & WK) != 0) fen.append('K');
            if ((p.castling & WQ) != 0) fen.append('Q');
            if ((p.castling & BK) != 0) fen.append('k');
            if ((p.castling & BQ) != 0) fen.append('q');
        }
        fen.append(' ').append(p.enPassant < 0 ? "-" : squareName(p.enPassant));
        fen.append(' ').append(p.halfmove).append(' ').append(p.fullmove);
        return fen.toString();
    }

    private static List<Move> generateLegalMoves(Position p) {
        List<Move> legal = new ArrayList<>();
        for (Move move : generatePseudoMoves(p)) {
            Position next = apply(p, move);
            if (!isKingAttacked(next, p.side)) legal.add(move);
        }
        return legal;
    }

    private static List<Move> generatePseudoMoves(Position p) {
        List<Move> moves = new ArrayList<>();
        for (int from = 0; from < 128; from++) {
            if (!onBoard(from)) { from += 7; continue; }
            char piece = p.board[from];
            if (colorOf(piece) != p.side) continue;
            char type = pieceType(piece);
            if (type == 'p') addPawnMoves(p, from, moves);
            else if (type == 'n') addLeaperMoves(p, from, KNIGHT_STEPS, moves);
            else if (type == 'b') addSliderMoves(p, from, BISHOP_STEPS, moves);
            else if (type == 'r') addSliderMoves(p, from, ROOK_STEPS, moves);
            else if (type == 'q') addSliderMoves(p, from, KING_STEPS, moves);
            else if (type == 'k') {
                addLeaperMoves(p, from, KING_STEPS, moves);
                addCastlingMoves(p, from, moves);
            }
        }
        return moves;
    }

    private static void addPawnMoves(Position p, int from, List<Move> moves) {
        int dir = p.side == WHITE ? 16 : -16;
        int startRank = p.side == WHITE ? 1 : 6;
        int promotionRank = p.side == WHITE ? 7 : 0;
        int one = from + dir;
        if (onBoard(one) && p.board[one] == '.') {
            addPawnDestination(from, one, promotionRank, false, false, moves);
            int two = one + dir;
            if (rankOf(from) == startRank && onBoard(two) && p.board[two] == '.') {
                moves.add(new Move(from, two, '\0', false, false));
            }
        }
        int[] diagonals = {dir - 1, dir + 1};
        for (int delta : diagonals) {
            int to = from + delta;
            if (!onBoard(to)) continue;
            if (colorOf(p.board[to]) == -p.side && pieceType(p.board[to]) != 'k') {
                addPawnDestination(from, to, promotionRank, false, false, moves);
            } else if (to == p.enPassant && p.board[to] == '.') {
                int capturedSquare = to - dir;
                if (onBoard(capturedSquare)
                        && p.board[capturedSquare] == (p.side == WHITE ? 'p' : 'P')) {
                    moves.add(new Move(from, to, '\0', true, false));
                }
            }
        }
    }

    private static void addPawnDestination(int from, int to, int promotionRank,
                                          boolean enPassant, boolean castle, List<Move> moves) {
        if (rankOf(to) == promotionRank) {
            for (char promotion : PROMOTIONS) moves.add(new Move(from, to, promotion, enPassant, castle));
        } else {
            moves.add(new Move(from, to, '\0', enPassant, castle));
        }
    }

    private static void addLeaperMoves(Position p, int from, int[] steps, List<Move> moves) {
        for (int step : steps) {
            int to = from + step;
            if (!onBoard(to)) continue;
            int targetColor = colorOf(p.board[to]);
            if (targetColor == p.side) continue;
            if (pieceType(p.board[to]) == 'k') continue;
            moves.add(new Move(from, to, '\0', false, false));
        }
    }

    private static void addSliderMoves(Position p, int from, int[] steps, List<Move> moves) {
        for (int step : steps) {
            int to = from + step;
            while (onBoard(to)) {
                int targetColor = colorOf(p.board[to]);
                if (targetColor == p.side) break;
                if (pieceType(p.board[to]) != 'k') moves.add(new Move(from, to, '\0', false, false));
                if (targetColor != 0) break;
                to += step;
            }
        }
    }

    private static void addCastlingMoves(Position p, int from, List<Move> moves) {
        if (p.side == WHITE && from == 4 && !isSquareAttacked(p, 4, BLACK)) {
            if ((p.castling & WK) != 0 && p.board[7] == 'R' && p.board[5] == '.' && p.board[6] == '.'
                    && !isSquareAttacked(p, 5, BLACK) && !isSquareAttacked(p, 6, BLACK)) {
                moves.add(new Move(4, 6, '\0', false, true));
            }
            if ((p.castling & WQ) != 0 && p.board[0] == 'R' && p.board[1] == '.' && p.board[2] == '.' && p.board[3] == '.'
                    && !isSquareAttacked(p, 3, BLACK) && !isSquareAttacked(p, 2, BLACK)) {
                moves.add(new Move(4, 2, '\0', false, true));
            }
        } else if (p.side == BLACK && from == 116 && !isSquareAttacked(p, 116, WHITE)) {
            if ((p.castling & BK) != 0 && p.board[119] == 'r' && p.board[117] == '.' && p.board[118] == '.'
                    && !isSquareAttacked(p, 117, WHITE) && !isSquareAttacked(p, 118, WHITE)) {
                moves.add(new Move(116, 118, '\0', false, true));
            }
            if ((p.castling & BQ) != 0 && p.board[112] == 'r' && p.board[113] == '.' && p.board[114] == '.' && p.board[115] == '.'
                    && !isSquareAttacked(p, 115, WHITE) && !isSquareAttacked(p, 114, WHITE)) {
                moves.add(new Move(116, 114, '\0', false, true));
            }
        }
    }

    private static boolean isKingAttacked(Position p, int side) {
        int king = side == WHITE ? p.whiteKing : p.blackKing;
        return isSquareAttacked(p, king, -side);
    }

    private static boolean isSquareAttacked(Position p, int target, int bySide) {
        if (target < 0 || !onBoard(target)) return false;
        // Pawn source squares are one rank behind the attacked square.
        int pawnSourceRank = target + (bySide == WHITE ? -16 : 16);
        int leftPawn = pawnSourceRank - 1;
        int rightPawn = pawnSourceRank + 1;
        char pawn = bySide == WHITE ? 'P' : 'p';
        if (onBoard(leftPawn) && p.board[leftPawn] == pawn) return true;
        if (onBoard(rightPawn) && p.board[rightPawn] == pawn) return true;

        for (int step : KNIGHT_STEPS) {
            int from = target + step;
            if (onBoard(from) && colorOf(p.board[from]) == bySide && pieceType(p.board[from]) == 'n') return true;
        }
        for (int step : KING_STEPS) {
            int from = target + step;
            if (onBoard(from) && colorOf(p.board[from]) == bySide && pieceType(p.board[from]) == 'k') return true;
        }
        for (int step : KING_STEPS) {
            int from = target + step;
            int distance = 1;
            while (onBoard(from)) {
                char piece = p.board[from];
                if (piece != '.') {
                    if (colorOf(piece) == bySide) {
                        char type = pieceType(piece);
                        if (type == 'q'
                                || (distance == 1 && type == 'k')
                                || (isDiagonal(step) && type == 'b')
                                || (!isDiagonal(step) && type == 'r')) return true;
                    }
                    break;
                }
                from += step;
                distance++;
            }
        }
        return false;
    }

    private static boolean isDiagonal(int step) {
        return step == -17 || step == -15 || step == 15 || step == 17;
    }

    private static Position apply(Position p, Move move) {
        Position next = new Position(p);
        char moving = next.board[move.from];
        char captured = next.board[move.to];
        int side = p.side;
        next.board[move.from] = '.';
        next.board[move.to] = move.promotion == '\0'
                ? moving
                : side == WHITE ? Character.toUpperCase(move.promotion) : Character.toLowerCase(move.promotion);

        if (move.enPassant) {
            int capturedSquare = move.to - 16 * side;
            captured = next.board[capturedSquare];
            next.board[capturedSquare] = '.';
        }
        if (move.castle) {
            if (move.to > move.from) {
                int rookFrom = move.from + 3;
                int rookTo = move.from + 1;
                next.board[rookTo] = next.board[rookFrom];
                next.board[rookFrom] = '.';
            } else {
                int rookFrom = move.from - 4;
                int rookTo = move.from - 1;
                next.board[rookTo] = next.board[rookFrom];
                next.board[rookFrom] = '.';
            }
        }

        if (moving == 'K') {
            next.whiteKing = move.to;
            next.castling &= ~(WK | WQ);
        } else if (moving == 'k') {
            next.blackKing = move.to;
            next.castling &= ~(BK | BQ);
        }
        clearRookRight(next, move.from, moving);
        clearRookRight(next, move.to, captured);

        next.enPassant = Math.abs(move.to - move.from) == 32 && pieceType(moving) == 'p'
                ? (move.from + move.to) / 2 : -1;
        next.halfmove = pieceType(moving) == 'p' || captured != '.' ? 0 : p.halfmove + 1;
        next.fullmove = p.fullmove + (side == BLACK ? 1 : 0);
        next.side = -side;
        next.lastFrom = move.from;
        next.lastTo = move.to;
        return next;
    }

    private static void clearRookRight(Position p, int square, char rook) {
        if (rook == 'R') {
            if (square == 7) p.castling &= ~WK;
            if (square == 0) p.castling &= ~WQ;
        } else if (rook == 'r') {
            if (square == 119) p.castling &= ~BK;
            if (square == 112) p.castling &= ~BQ;
        }
    }

    private static String toSan(Position p, Move move) {
        char piece = p.board[move.from];
        char type = pieceType(piece);
        boolean capture = p.board[move.to] != '.' || move.enPassant;
        StringBuilder san = new StringBuilder();

        if (move.castle) {
            san.append(move.to > move.from ? "O-O" : "O-O-O");
        } else {
            if (type != 'p') {
                san.append(Character.toUpperCase(type));
                List<Move> alternatives = new ArrayList<>();
                for (Move other : generateLegalMoves(p)) {
                    if (other.from != move.from && other.to == move.to
                            && pieceType(p.board[other.from]) == type
                            && colorOf(p.board[other.from]) == colorOf(piece)) alternatives.add(other);
                }
                if (!alternatives.isEmpty()) {
                    boolean sameFile = false;
                    boolean sameRank = false;
                    for (Move other : alternatives) {
                        if (fileOf(other.from) == fileOf(move.from)) sameFile = true;
                        if (rankOf(other.from) == rankOf(move.from)) sameRank = true;
                    }
                    if (!sameFile) san.append((char) ('a' + fileOf(move.from)));
                    else if (!sameRank) san.append((char) ('1' + rankOf(move.from)));
                    else san.append(squareName(move.from));
                }
            } else if (capture) {
                san.append((char) ('a' + fileOf(move.from)));
            }
            if (capture) san.append('x');
            san.append(squareName(move.to));
            if (move.promotion != '\0') san.append('=').append(Character.toUpperCase(move.promotion));
        }

        Position after = apply(p, move);
        if (isKingAttacked(after, after.side)) {
            san.append(generateLegalMoves(after).isEmpty() ? '#' : '+');
        }
        return san.toString();
    }

    private static String positionKey(Position p) {
        StringBuilder key = new StringBuilder(140);
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) key.append(p.board[square(file, rank)]);
        }
        key.append('|').append(p.side).append('|').append(p.castling).append('|');
        boolean hasLegalEp = false;
        if (p.enPassant >= 0) {
            for (Move move : generateLegalMoves(p)) if (move.enPassant) { hasLegalEp = true; break; }
        }
        key.append(hasLegalEp ? p.enPassant : -1);
        return key.toString();
    }

    private static boolean isInsufficientMaterial(Position p) {
        int knights = 0;
        List<Integer> bishopColors = new ArrayList<>();
        int other = 0;
        for (int square = 0; square < 128; square++) {
            if (!onBoard(square)) { square += 7; continue; }
            char piece = p.board[square];
            if (piece == '.' || pieceType(piece) == 'k') continue;
            char type = pieceType(piece);
            if (type == 'n') knights++;
            else if (type == 'b') bishopColors.add((fileOf(square) + rankOf(square)) & 1);
            else other++;
        }
        if (other > 0) return false;
        int minors = knights + bishopColors.size();
        if (minors <= 1) return true;
        if (knights == 0 && !bishopColors.isEmpty()) {
            int first = bishopColors.get(0);
            for (int color : bishopColors) if (color != first) return false;
            return true;
        }
        return false;
    }

    // Test-support perft; uses the exact same legal move generator as gameplay.
    public static long perft(String fen, int depth) {
        if (depth < 0) throw new IllegalArgumentException("Depth cannot be negative");
        return perft(parseFen(fen), depth);
    }

    private static long perft(Position p, int depth) {
        if (depth == 0) return 1L;
        long nodes = 0L;
        for (Move move : generateLegalMoves(p)) nodes += perft(apply(p, move), depth - 1);
        return nodes;
    }

    public static String getInitialFen() { return INITIAL_FEN; }

    public static String getPieceGlyph(char piece) {
        switch (piece) {
            case 'K': return "♔"; case 'Q': return "♕"; case 'R': return "♖";
            case 'B': return "♗"; case 'N': return "♘"; case 'P': return "♙";
            case 'k': return "♚"; case 'q': return "♛"; case 'r': return "♜";
            case 'b': return "♝"; case 'n': return "♞"; case 'p': return "♟";
            default: return "";
        }
    }

    private int repetitionCount(Position position) {
        String wanted = positionKey(position);
        int count = 0;
        for (Position item : history) if (positionKey(item).equals(wanted)) count++;
        return count;
    }
}
