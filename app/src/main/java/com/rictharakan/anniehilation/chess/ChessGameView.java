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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Native Canvas chessboard. White starts at the bottom; h1 is the light square. */
public final class ChessGameView extends View {
    public interface Listener {
        void onMoveChosen(ChessGameEngine.Move move);
        void onPromotionRequired(List<ChessGameEngine.Move> choices);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF squareRect = new RectF();
    private ChessGameEngine game;
    private Listener listener;
    private int selectedSquare = -1;
    private List<ChessGameEngine.Move> selectedMoves = new ArrayList<>();
    private boolean flipped;
    private float boardLeft;
    private float boardTop;
    private float cellSize;
    private float boardSize;

    private final int lightSquare = 0xFFE8D8BC;
    private final int darkSquare = 0xFF80624B;
    private final int boardBorder = 0xFF392B25;
    private final int selectedColor = 0x88FACC15;
    private final int lastMoveColor = 0x6659BFA8;
    private final int targetColor = 0xAA176B46;

    public ChessGameView(Context context) {
        super(context);
        setFocusable(true);
        setContentDescription("Chess board. White pieces at the bottom. Tap a piece, then a highlighted destination.");
        textPaint.setTypeface(Typeface.create("serif", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(ChessGameEngine game) {
        this.game = game;
        clearSelection();
        invalidate();
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isFlipped() { return flipped; }
    public void flipBoard() { flipped = !flipped; clearSelection(); invalidate(); }
    public void clearSelection() { selectedSquare = -1; selectedMoves.clear(); invalidate(); }
    public void refreshBoard() { clearSelection(); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF101827);
        if (game == null) return;

        boardSize = Math.min(getWidth() - dp(12), getHeight() - dp(12));
        boardSize = Math.max(0f, boardSize);
        cellSize = boardSize / 8f;
        boardLeft = (getWidth() - boardSize) / 2f;
        boardTop = (getHeight() - boardSize) / 2f;
        if (boardSize <= 0f) return;

        // Outer frame.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boardBorder);
        canvas.drawRoundRect(boardLeft - dp(4), boardTop - dp(4),
                boardLeft + boardSize + dp(4), boardTop + boardSize + dp(4), dp(7), dp(7), paint);

        int lastFrom = game.getLastMoveFrom();
        int lastTo = game.getLastMoveTo();
        List<ChessGameEngine.Move> legal = selectedMoves;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int square = squareForCell(col, row);
                float left = boardLeft + col * cellSize;
                float top = boardTop + row * cellSize;
                squareRect.set(left, top, left + cellSize, top + cellSize);
                boolean light = ((ChessGameEngine.fileOf(square) + ChessGameEngine.rankOf(square)) & 1) == 1;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(light ? lightSquare : darkSquare);
                canvas.drawRect(squareRect, paint);

                if (square == lastFrom || square == lastTo) {
                    paint.setColor(lastMoveColor);
                    canvas.drawRect(squareRect, paint);
                }
                if (square == selectedSquare) {
                    paint.setColor(selectedColor);
                    canvas.drawRect(squareRect, paint);
                }

                if (isLegalTarget(legal, square)) {
                    char occupant = game.getPieceAt(square);
                    paint.setStyle(occupant == '.' ? Paint.Style.FILL : Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(dp(2), cellSize * 0.055f));
                    paint.setColor(targetColor);
                    float radius = cellSize * (occupant == '.' ? 0.12f : 0.42f);
                    canvas.drawCircle(left + cellSize / 2f, top + cellSize / 2f, radius, paint);
                }

                drawPiece(canvas, game.getPieceAt(square), left, top);
                drawCoordinates(canvas, square, row, col, left, top);
            }
        }
    }

    private void drawPiece(Canvas canvas, char piece, float left, float top) {
        if (piece == '.') return;
        String glyph = ChessGameEngine.getPieceGlyph(piece);
        float cx = left + cellSize / 2f;
        float cy = top + cellSize / 2f;
        textPaint.setTextSize(cellSize * 0.76f);
        textPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setStrokeWidth(Math.max(dp(1), cellSize * 0.025f));
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setColor(Character.isUpperCase(piece) ? 0xFF28313C : 0xFFF7EBDD);
        float baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(glyph, cx, baseline, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Character.isUpperCase(piece) ? 0xFFFFFBF1 : 0xFF171B22);
        canvas.drawText(glyph, cx, baseline, textPaint);
    }

    private void drawCoordinates(Canvas canvas, int square, int row, int col, float left, float top) {
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setStrokeWidth(0f);
        textPaint.setTextSize(Math.max(dp(8), cellSize * 0.13f));
        boolean light = ((ChessGameEngine.fileOf(square) + ChessGameEngine.rankOf(square)) & 1) == 1;
        textPaint.setColor(light ? 0xFF735944 : 0xFFF1DFC5);
        if (col == 0) {
            String rank = String.valueOf(ChessGameEngine.rankOf(square) + 1);
            canvas.drawText(rank, left + cellSize * 0.12f, top + cellSize * 0.17f, textPaint);
        }
        if (row == 7) {
            String file = String.valueOf((char) ('a' + ChessGameEngine.fileOf(square)));
            canvas.drawText(file, left + cellSize * 0.88f, top + cellSize * 0.95f, textPaint);
        }
    }

    private boolean isLegalTarget(List<ChessGameEngine.Move> moves, int square) {
        for (ChessGameEngine.Move move : moves) if (move.to == square) return true;
        return false;
    }

    private int squareForCell(int col, int row) {
        int file = flipped ? 7 - col : col;
        int rank = flipped ? row : 7 - row;
        return ChessGameEngine.square(file, rank);
    }

    private void handleTap(float x, float y) {
        if (game == null || game.isGameOver() || cellSize <= 0f) return;
        if (x < boardLeft || y < boardTop || x >= boardLeft + boardSize || y >= boardTop + boardSize) return;
        int col = Math.min(7, (int) ((x - boardLeft) / cellSize));
        int row = Math.min(7, (int) ((y - boardTop) / cellSize));
        int tapped = squareForCell(col, row);
        char piece = game.getPieceAt(tapped);
        int moverSide = game.getSideToMove();

        if (selectedSquare >= 0) {
            List<ChessGameEngine.Move> destinations = new ArrayList<>();
            for (ChessGameEngine.Move move : selectedMoves) if (move.to == tapped) destinations.add(move);
            if (!destinations.isEmpty()) {
                clearSelection();
                if (destinations.size() > 1) {
                    if (listener != null) listener.onPromotionRequired(destinations);
                } else if (listener != null) {
                    listener.onMoveChosen(destinations.get(0));
                }
                return;
            }
        }

        if (piece != '.' && ((Character.isUpperCase(piece) && moverSide == 1)
                || (Character.isLowerCase(piece) && moverSide == -1))) {
            selectedSquare = tapped;
            selectedMoves = new ArrayList<>(game.legalMovesFrom(tapped));
            invalidate();
        } else {
            clearSelection();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            handleTap(event.getX(), event.getY());
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
