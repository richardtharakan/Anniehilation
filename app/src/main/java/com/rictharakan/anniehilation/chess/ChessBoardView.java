package com.rictharakan.anniehilation.chess;

import android.content.Context;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Native, touch-operated chessboard. White's pieces are always at the bottom. */
public final class ChessBoardView extends View {

    public interface Listener {
        void onMoveSelected(ChessEngine.Move move);
        void onPromotionRequired(List<ChessEngine.Move> choices);
        void onInvalidMove();
    }

    private static final int LIGHT = 0xFFF0E1C4;
    private static final int DARK = 0xFF476B70;
    private static final int GOLD = 0xFFFFC65C;
    private static final int BOARD_EDGE = 0xFF17283A;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF boardRect = new RectF();
    private final RectF pieceRect = new RectF();
    private final Path crownPath = new Path();
    private final Path crossPath = new Path();

    private ChessEngine engine;
    private Listener listener;
    private Bitmap annieQueenBitmap;
    private float cellSize;
    private float boardLeft;
    private float boardTop;
    private float density;
    private int selectedSquare = -1;
    private final ArrayList<Integer> targets = new ArrayList<Integer>();
    private int lastFrom = -1;
    private int lastTo = -1;
    private boolean showCoordinates = true;
    private boolean inputEnabled = true;
    private boolean animatingPiece;
    private int animatedFrom = -1;
    private int animatedTo = -1;
    private char animatedPiece = ChessEngine.EMPTY;
    private float moveProgress = 1f;
    private ValueAnimator moveAnimator;

    public ChessBoardView(Context context) {
        super(context);
        initialize(context);
    }

    public ChessBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    private void initialize(Context context) {
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_NONE, null);
        setClickable(true);
        setFocusable(true);
        setHapticFeedbackEnabled(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        annieQueenBitmap = loadAnniePortrait();
    }

    public void setEngine(ChessEngine engine) {
        cancelMoveAnimation();
        this.engine = engine;
        selectedSquare = -1;
        targets.clear();
        lastFrom = -1;
        lastTo = -1;
        postInvalidateOnAnimation();
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
        if (!enabled) clearSelection();
    }

    public void refreshPosition() { refreshPosition(true); }

    public void refreshPosition(boolean animate) {
        if (engine != null) {
            ChessEngine.Move last = engine.getLastMove();
            lastFrom = last == null ? -1 : last.from;
            lastTo = last == null ? -1 : last.to;
            if (animate && last != null && getWidth() > 0 && getHeight() > 0) {
                startMoveAnimation(last);
            } else {
                cancelMoveAnimation();
            }
        }
        selectedSquare = -1;
        targets.clear();
        postInvalidateOnAnimation();
    }

    private void startMoveAnimation(ChessEngine.Move last) {
        cancelMoveAnimation();
        animatedFrom = last.from;
        animatedTo = last.to;
        animatedPiece = engine.getPieceAt(last.to);
        if (animatedPiece == ChessEngine.EMPTY) return;
        moveProgress = 0f;
        animatingPiece = true;
        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(190L);
        moveAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        moveAnimator.addUpdateListener(animation -> {
            moveProgress = (Float) animation.getAnimatedValue();
            postInvalidateOnAnimation();
        });
        moveAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                animatingPiece = false;
                animatedPiece = ChessEngine.EMPTY;
                moveAnimator = null;
                postInvalidateOnAnimation();
            }
        });
        moveAnimator.start();
    }

    private void cancelMoveAnimation() {
        if (moveAnimator != null) {
            moveAnimator.cancel();
            moveAnimator = null;
        }
        animatingPiece = false;
        animatedPiece = ChessEngine.EMPTY;
        animatedFrom = -1;
        animatedTo = -1;
        moveProgress = 1f;
    }

    public void clearSelection() {
        selectedSquare = -1;
        targets.clear();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xFF0A1423);
        if (engine == null) return;

        float margin = dp(12f);
        float available = Math.max(0f, Math.min(getWidth() - margin * 2f, getHeight() - margin * 2f));
        cellSize = available / 8f;
        if (cellSize <= 0f) return;
        float boardSize = cellSize * 8f;
        boardLeft = (getWidth() - boardSize) / 2f;
        boardTop = (getHeight() - boardSize) / 2f;

        boardRect.set(boardLeft - dp(8f), boardTop - dp(8f),
                boardLeft + boardSize + dp(8f), boardTop + boardSize + dp(8f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF050B12);
        canvas.drawRoundRect(boardRect, dp(15f), dp(15f), paint);
        RectF raised = new RectF(boardRect.left + dp(2f), boardRect.top + dp(2f),
                boardRect.right - dp(2f), boardRect.bottom - dp(2f));
        paint.setColor(BOARD_EDGE);
        canvas.drawRoundRect(raised, dp(13f), dp(13f), paint);
        outlinePaint.setStrokeWidth(dp(1.4f));
        outlinePaint.setColor(0xFFB88D4A);
        canvas.drawRoundRect(boardRect, dp(15f), dp(15f), outlinePaint);
        outlinePaint.setStrokeWidth(dp(0.8f));
        outlinePaint.setColor(0xFF60788A);
        canvas.drawRoundRect(raised, dp(13f), dp(13f), outlinePaint);

        drawSquares(canvas);
        drawMoveHighlights(canvas);
        drawPieces(canvas);
        drawAnimatedPiece(canvas);
        if (showCoordinates) drawCoordinates(canvas);
    }

    private void drawSquares(Canvas canvas) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int square = r * 8 + c;
                float left = boardLeft + c * cellSize;
                float top = boardTop + r * cellSize;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(((r + c) & 1) == 0 ? LIGHT : DARK);
                canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);

                if (square == lastFrom || square == lastTo) {
                    paint.setColor(0x4DFFD27A);
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
                }
                outlinePaint.setColor(0x1F12202A);
                outlinePaint.setStrokeWidth(dp(0.45f));
                canvas.drawRect(left, top, left + cellSize, top + cellSize, outlinePaint);
            }
        }
    }

    private void drawMoveHighlights(Canvas canvas) {
        if (selectedSquare >= 0) {
            float left = boardLeft + ChessEngine.col(selectedSquare) * cellSize;
            float top = boardTop + ChessEngine.row(selectedSquare) * cellSize;
            paint.setColor(0x66FFD05B);
            canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
            outlinePaint.setColor(0xFFFFD27A);
            outlinePaint.setStrokeWidth(Math.max(dp(2f), cellSize * 0.035f));
            canvas.drawRoundRect(left + dp(2), top + dp(2), left + cellSize - dp(2),
                    top + cellSize - dp(2), dp(5), dp(5), outlinePaint);
        }
        for (Integer target : targets) {
            int square = target.intValue();
            float cx = centerX(square);
            float cy = centerY(square);
            char occupant = engine.getPieceAt(square);
            if (occupant == ChessEngine.EMPTY) {
                paint.setColor(0x99304745);
                canvas.drawCircle(cx, cy, cellSize * 0.105f, paint);
                outlinePaint.setColor(0xAAFFF0CE);
                outlinePaint.setStrokeWidth(dp(1f));
                canvas.drawCircle(cx, cy, cellSize * 0.105f, outlinePaint);
            } else {
                outlinePaint.setColor(0xCCFFCB73);
                outlinePaint.setStrokeWidth(Math.max(dp(2f), cellSize * 0.045f));
                canvas.drawCircle(cx, cy, cellSize * 0.43f, outlinePaint);
            }
        }

        if (engine.isInCheck()) {
            boolean whiteKing = engine.isWhiteToMove();
            char king = whiteKing ? 'K' : 'k';
            for (int square = 0; square < 64; square++) {
                if (engine.getPieceAt(square) == king) {
                    paint.setColor(0x66FF3B45);
                    canvas.drawCircle(centerX(square), centerY(square), cellSize * 0.47f, paint);
                    break;
                }
            }
        }
    }

    private void drawPieces(Canvas canvas) {
        for (int square = 0; square < 64; square++) {
            if (animatingPiece && square == animatedTo) continue;
            char piece = engine.getPieceAt(square);
            if (piece == ChessEngine.EMPTY) continue;
            float cx = centerX(square);
            float cy = centerY(square);
            if (piece == 'Q') drawAnnieQueen(canvas, cx, cy);
            else if (piece == 'K') drawRoyalKing(canvas, cx, cy);
            else drawStandardPiece(canvas, piece, cx, cy);
        }
    }

    private void drawAnimatedPiece(Canvas canvas) {
        if (!animatingPiece || animatedPiece == ChessEngine.EMPTY) return;
        float fromX = centerX(animatedFrom);
        float fromY = centerY(animatedFrom);
        float toX = centerX(animatedTo);
        float toY = centerY(animatedTo);
        float x = fromX + (toX - fromX) * moveProgress;
        float y = fromY + (toY - fromY) * moveProgress;
        if (animatedPiece == 'Q') drawAnnieQueen(canvas, x, y);
        else if (animatedPiece == 'K') drawRoyalKing(canvas, x, y);
        else drawStandardPiece(canvas, animatedPiece, x, y);
    }

    /** Annie's Golden Academy badge replaces only White's queen; White's king remains a king. */
    private void drawAnnieQueen(Canvas canvas, float cx, float cy) {
        float radius = cellSize * 0.36f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF17263B);
        canvas.drawCircle(cx, cy, radius + dp(2f), paint);
        outlinePaint.setColor(GOLD);
        outlinePaint.setStrokeWidth(Math.max(dp(2f), cellSize * 0.045f));
        canvas.drawCircle(cx, cy, radius + dp(1f), outlinePaint);
        if (annieQueenBitmap != null && !annieQueenBitmap.isRecycled()) {
            pieceRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            Path clip = new Path();
            clip.addCircle(cx, cy, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            canvas.drawBitmap(annieQueenBitmap, null, pieceRect, paint);
            canvas.restoreToCount(save);
        } else {
            drawStandardPiece(canvas, 'Q', cx, cy);
        }
    }

    /** White's king is a gold royal crown topped by a small cross. */
    private void drawRoyalKing(Canvas canvas, float cx, float cy) {
        float w = cellSize * 0.62f;
        float baseY = cy + cellSize * 0.28f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF23364C);
        canvas.drawCircle(cx, cy, cellSize * 0.37f, paint);
        outlinePaint.setColor(0xFFFFD36D);
        outlinePaint.setStrokeWidth(Math.max(dp(1.5f), cellSize * 0.035f));
        canvas.drawCircle(cx, cy, cellSize * 0.37f, outlinePaint);

        crownPath.reset();
        crownPath.moveTo(cx - w * 0.48f, baseY - cellSize * 0.38f);
        crownPath.lineTo(cx - w * 0.50f, baseY - cellSize * 0.10f);
        crownPath.lineTo(cx + w * 0.50f, baseY - cellSize * 0.10f);
        crownPath.lineTo(cx + w * 0.48f, baseY - cellSize * 0.38f);
        crownPath.lineTo(cx + w * 0.18f, baseY - cellSize * 0.20f);
        crownPath.lineTo(cx, baseY - cellSize * 0.50f);
        crownPath.lineTo(cx - w * 0.18f, baseY - cellSize * 0.20f);
        crownPath.close();
        paint.setColor(0xFFFFC34F);
        canvas.drawPath(crownPath, paint);
        outlinePaint.setColor(0xFF80531B);
        outlinePaint.setStrokeWidth(dp(1.2f));
        canvas.drawPath(crownPath, outlinePaint);

        paint.setColor(0xFFFFE09A);
        canvas.drawRect(cx - w * 0.48f, baseY - cellSize * 0.12f,
                cx + w * 0.48f, baseY + cellSize * 0.02f, paint);
        paint.setColor(0xFF9D6725);
        canvas.drawRect(cx - w * 0.50f, baseY + cellSize * 0.02f,
                cx + w * 0.50f, baseY + cellSize * 0.09f, paint);

        // Cross above the crown: vertical stem and short horizontal arms.
        crossPath.reset();
        crossPath.moveTo(cx, cy - cellSize * 0.59f);
        crossPath.lineTo(cx, cy - cellSize * 0.42f);
        crossPath.moveTo(cx - cellSize * 0.09f, cy - cellSize * 0.53f);
        crossPath.lineTo(cx + cellSize * 0.09f, cy - cellSize * 0.53f);
        outlinePaint.setColor(0xFFFFE8B2);
        outlinePaint.setStrokeWidth(Math.max(dp(2f), cellSize * 0.055f));
        canvas.drawPath(crossPath, outlinePaint);
        paint.setColor(0xFFFFF3D1);
        canvas.drawCircle(cx, cy - cellSize * 0.59f, cellSize * 0.045f, paint);
    }

    private void drawStandardPiece(Canvas canvas, char piece, float cx, float cy) {
        String glyph = glyphFor(piece);
        boolean white = Character.isUpperCase(piece);
        textPaint.setTextSize(cellSize * 0.77f);
        textPaint.setColor(white ? 0xFFFFFCF4 : 0xFF17202C);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD));
        textPaint.setStrokeWidth(0f);
        textPaint.setShadowLayer(dp(1.3f), 0f, dp(1f), white ? 0xAA17202C : 0x66FFFFFF);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(glyph, cx, baseline, textPaint);
        textPaint.clearShadowLayer();
    }

    private String glyphFor(char piece) {
        switch (piece) {
            case 'P': return "♙";
            case 'N': return "♘";
            case 'B': return "♗";
            case 'R': return "♖";
            case 'p': return "♟";
            case 'n': return "♞";
            case 'b': return "♝";
            case 'r': return "♜";
            case 'q': return "♛";
            case 'k': return "♚";
            case 'Q': return "♕";
            case 'K': return "♔";
            default: return "?";
        }
    }

    private void drawCoordinates(Canvas canvas) {
        textPaint.setTextSize(Math.max(dp(8f), cellSize * 0.12f));
        textPaint.setShadowLayer(0f, 0f, 0f, 0);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int color = ((r + c) & 1) == 0 ? 0xFF71869A : 0xFFE8D9C1;
                textPaint.setColor(color);
                if (c == 0) canvas.drawText(String.valueOf(8 - r), boardLeft + dp(5f),
                        boardTop + r * cellSize + dp(11f), textPaint);
                if (r == 7) canvas.drawText("abcdefgh".substring(c, c + 1),
                        boardLeft + (c + 1) * cellSize - dp(6f),
                        boardTop + 8 * cellSize - dp(3f), textPaint);
            }
        }
    }

    private Bitmap loadAnniePortrait() {
        // Use the exact Golden Academy portrait from the gallery for White's queen only.
        int resourceId = getResources().getIdentifier(
                "royal_chess_annie_badge", "drawable", getContext().getPackageName());
        if (resourceId == 0) resourceId = getResources().getIdentifier(
                "annienvasion_annie_badge", "drawable", getContext().getPackageName());
        if (resourceId == 0) resourceId = getResources().getIdentifier(
                "ic_launcher_round", "mipmap", getContext().getPackageName());
        if (resourceId == 0) resourceId = getResources().getIdentifier(
                "anniehilation_logo", "drawable", getContext().getPackageName());
        if (resourceId == 0) return null;
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
            if (bitmap != null) bitmap.setHasMipMap(true);
            return bitmap;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (engine == null || !inputEnabled || animatingPiece) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            int square = squareAt(event.getX(), event.getY());
            if (square >= 0) handleSquareTap(square);
            performClick();
            return true;
        }
        return true;
    }

    private void handleSquareTap(int square) {
        if (selectedSquare < 0) {
            selectIfCurrentPlayer(square);
            return;
        }
        if (square == selectedSquare) {
            clearSelection();
            return;
        }

        ArrayList<ChessEngine.Move> choices = new ArrayList<ChessEngine.Move>();
        for (ChessEngine.Move move : engine.getLegalMovesFrom(selectedSquare)) {
            if (move.to == square) choices.add(move);
        }
        if (choices.size() == 1) {
            if (listener != null) listener.onMoveSelected(choices.get(0));
            return;
        }
        if (choices.size() > 1) {
            if (listener != null) listener.onPromotionRequired(choices);
            return;
        }

        char tapped = engine.getPieceAt(square);
        if (tapped != ChessEngine.EMPTY && Character.isUpperCase(tapped) == engine.isWhiteToMove()) {
            selectIfCurrentPlayer(square);
        } else {
            if (listener != null) listener.onInvalidMove();
            clearSelection();
        }
    }

    private void selectIfCurrentPlayer(int square) {
        char piece = engine.getPieceAt(square);
        if (piece == ChessEngine.EMPTY || Character.isUpperCase(piece) != engine.isWhiteToMove()) {
            clearSelection();
            return;
        }
        List<ChessEngine.Move> moves = engine.getLegalMovesFrom(square);
        if (moves.isEmpty()) {
            clearSelection();
            if (listener != null) listener.onInvalidMove();
            return;
        }
        selectedSquare = square;
        targets.clear();
        for (ChessEngine.Move move : moves) {
            if (!targets.contains(Integer.valueOf(move.to))) targets.add(Integer.valueOf(move.to));
        }
        postInvalidateOnAnimation();
    }

    private int squareAt(float x, float y) {
        if (cellSize <= 0f || x < boardLeft || y < boardTop
                || x >= boardLeft + cellSize * 8f || y >= boardTop + cellSize * 8f) return -1;
        int col = (int) ((x - boardLeft) / cellSize);
        int row = (int) ((y - boardTop) / cellSize);
        return ChessEngine.square(row, col);
    }

    private float centerX(int square) { return boardLeft + (ChessEngine.col(square) + 0.5f) * cellSize; }
    private float centerY(int square) { return boardTop + (ChessEngine.row(square) + 0.5f) * cellSize; }
    private float dp(float value) { return value * density; }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
