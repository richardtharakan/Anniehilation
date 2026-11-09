package com.rictharakan.anniehilation.zipgame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Hardware-accelerated, touch-smoothed Canvas board for Zip Trail.
 * Walls are drawn as high-contrast raised barriers above the board grid.
 */
public class ZipGameView extends View {

    public interface Listener {
        void onMoveResult(ZipPuzzle.MoveResult result);
        void onPuzzleSolved();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint pathGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF markerRect = new RectF();
    private final Path reusablePath = new Path();
    private final Path markerClipPath = new Path();

    private Bitmap annieMarkerBitmap;
    private ZipPuzzle puzzle;
    private Listener listener;
    private float cellSize;
    private float boardLeft;
    private float boardTop;
    private float density;
    private int lastTouchedCell = -1;
    private float lastPointerX;
    private float lastPointerY;
    private boolean inputEnabled = true;

    private static final int COLOR_BACKGROUND = 0xFF0B1424;
    private static final int COLOR_PANEL = 0xFF101E32;
    private static final int COLOR_CELL_A = 0xFF223650;
    private static final int COLOR_CELL_B = 0xFF1B2D45;
    private static final int COLOR_GRID = 0xFF536B87;
    private static final int COLOR_PATH = 0xFF45D9CE;
    private static final int COLOR_PATH_HIGHLIGHT = 0xFFCFFFFA;
    private static final int COLOR_CHECKPOINT = 0xFFFFC857;
    private static final int COLOR_VISITED = 0xFF65E3C8;
    private static final int COLOR_WALL_SHADOW = 0xFF07101D;
    private static final int COLOR_WALL = 0xFFFF725B;
    private static final int COLOR_WALL_HIGHLIGHT = 0xFFFFD19A;

    public ZipGameView(Context context) {
        super(context);
        initialize(context);
    }

    public ZipGameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public ZipGameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        density = getResources().getDisplayMetrics().density;
        // Do not force a software layer. The default hardware-accelerated Canvas
        // is substantially smoother for the rounded paths and bitmap marker.
        setLayerType(View.LAYER_TYPE_NONE, null);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setHapticFeedbackEnabled(true);
        setContentDescription("Zip Trail puzzle board. Start at Berthu and trace a path to Annie.");

        pathGlowPaint.setStyle(Paint.Style.STROKE);
        pathGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        pathGlowPaint.setStrokeJoin(Paint.Join.ROUND);
        pathGlowPaint.setColor(0x6650F2DF);

        pathPaint.setStyle(Paint.Style.STROKE);
        pathPaint.setStrokeCap(Paint.Cap.ROUND);
        pathPaint.setStrokeJoin(Paint.Join.ROUND);
        pathPaint.setColor(COLOR_PATH);

        pathHighlightPaint.setStyle(Paint.Style.STROKE);
        pathHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
        pathHighlightPaint.setStrokeJoin(Paint.Join.ROUND);
        pathHighlightPaint.setColor(COLOR_PATH_HIGHLIGHT);

        wallShadowPaint.setStyle(Paint.Style.STROKE);
        wallShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        wallShadowPaint.setColor(COLOR_WALL_SHADOW);
        wallPaint.setStyle(Paint.Style.STROKE);
        wallPaint.setStrokeCap(Paint.Cap.ROUND);
        wallPaint.setColor(COLOR_WALL);
        wallHighlightPaint.setStyle(Paint.Style.STROKE);
        wallHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
        wallHighlightPaint.setColor(COLOR_WALL_HIGHLIGHT);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(COLOR_GRID);
        markerRingPaint.setStyle(Paint.Style.STROKE);
        markerRingPaint.setColor(0xFFFFD27D);
        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(COLOR_CHECKPOINT);
        badgeTextPaint.setColor(0xFF172338);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));

        annieMarkerBitmap = loadAnnieAppIcon();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPuzzle(ZipPuzzle puzzle) {
        this.puzzle = puzzle;
        this.lastTouchedCell = -1;
        postInvalidateOnAnimation();
    }

    public ZipPuzzle getPuzzle() {
        return puzzle;
    }

    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
    }

    public void undo() {
        if (puzzle != null && puzzle.undo()) postInvalidateOnAnimation();
    }

    public void clearPath() {
        if (puzzle != null) {
            puzzle.clearPath();
            postInvalidateOnAnimation();
        }
    }

    public void applyHint() {
        if (puzzle != null && !puzzle.isSolved()) {
            puzzle.applyHint();
            postInvalidateOnAnimation();
            if (puzzle.isSolved() && listener != null) listener.onPuzzleSolved();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(COLOR_BACKGROUND);
        if (puzzle == null) return;

        int rows = puzzle.getRows();
        int cols = puzzle.getCols();
        float availableWidth = Math.max(0f, getWidth() - dp(30));
        float availableHeight = Math.max(0f, getHeight() - dp(30));
        cellSize = Math.min(availableWidth / cols, availableHeight / rows);
        if (cellSize <= 0f) return;

        float boardWidth = cellSize * cols;
        float boardHeight = cellSize * rows;
        boardLeft = (getWidth() - boardWidth) / 2f;
        boardTop = (getHeight() - boardHeight) / 2f;

        // Recessed board plate and fine outline.
        rect.set(boardLeft - dp(8), boardTop - dp(8),
                boardLeft + boardWidth + dp(8), boardTop + boardHeight + dp(8));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_PANEL);
        canvas.drawRoundRect(rect, dp(18), dp(18), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xFF314861);
        canvas.drawRoundRect(rect, dp(18), dp(18), paint);

        drawCells(canvas, rows, cols);
        drawGrid(canvas, rows, cols);
        drawPath(canvas);
        drawWalls(canvas, rows, cols);
        drawCheckpoints(canvas, rows, cols);
        drawCharacterMarkers(canvas);
    }

    private void drawCells(Canvas canvas, int rows, int cols) {
        paint.setStyle(Paint.Style.FILL);
        float inset = Math.max(dp(1f), cellSize * 0.035f);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                rect.set(boardLeft + c * cellSize + inset,
                        boardTop + r * cellSize + inset,
                        boardLeft + (c + 1) * cellSize - inset,
                        boardTop + (r + 1) * cellSize - inset);
                paint.setColor(((r + c) & 1) == 0 ? COLOR_CELL_A : COLOR_CELL_B);
                canvas.drawRoundRect(rect, cellSize * 0.13f, cellSize * 0.13f, paint);
            }
        }
    }

    private void drawGrid(Canvas canvas, int rows, int cols) {
        gridPaint.setStrokeWidth(Math.max(dp(0.8f), cellSize * 0.012f));
        for (int r = 0; r <= rows; r++) {
            float y = boardTop + r * cellSize;
            canvas.drawLine(boardLeft, y, boardLeft + cols * cellSize, y, gridPaint);
        }
        for (int c = 0; c <= cols; c++) {
            float x = boardLeft + c * cellSize;
            canvas.drawLine(x, boardTop, x, boardTop + rows * cellSize, gridPaint);
        }
    }

    private void drawPath(Canvas canvas) {
        int[] pathCells = puzzle.getPathCopy();
        if (pathCells.length == 0) return;
        reusablePath.reset();
        for (int i = 0; i < pathCells.length; i++) {
            float x = centerX(pathCells[i]);
            float y = centerY(pathCells[i]);
            if (i == 0) reusablePath.moveTo(x, y);
            else reusablePath.lineTo(x, y);
        }
        pathGlowPaint.setStrokeWidth(Math.max(dp(13f), cellSize * 0.22f));
        pathPaint.setStrokeWidth(Math.max(dp(7f), cellSize * 0.145f));
        pathHighlightPaint.setStrokeWidth(Math.max(dp(1.6f), cellSize * 0.025f));
        canvas.drawPath(reusablePath, pathGlowPaint);
        canvas.drawPath(reusablePath, pathPaint);
        canvas.drawPath(reusablePath, pathHighlightPaint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE0FFFA);
        float dot = Math.max(dp(2f), cellSize * 0.045f);
        for (int i = 0; i < pathCells.length - 1; i++) {
            canvas.drawCircle(centerX(pathCells[i]), centerY(pathCells[i]), dot, paint);
        }
    }

    /** Draw bold raised barriers after the grid so no grid line can hide them. */
    private void drawWalls(Canvas canvas, int rows, int cols) {
        float shadowWidth = Math.max(dp(9f), cellSize * 0.23f);
        float bodyWidth = Math.max(dp(6f), cellSize * 0.16f);
        float highlightWidth = Math.max(dp(1.5f), cellSize * 0.035f);
        float reach = cellSize * 0.37f;
        wallShadowPaint.setStrokeWidth(shadowWidth);
        wallPaint.setStrokeWidth(bodyWidth);
        wallHighlightPaint.setStrokeWidth(highlightWidth);

        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols; c++) {
                if (puzzle.hasHorizontalWall(r, c)) {
                    float x = boardLeft + (c + 0.5f) * cellSize;
                    float y = boardTop + (r + 1) * cellSize;
                    drawWall(canvas, x - reach, y, x + reach, y);
                }
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                if (puzzle.hasVerticalWall(r, c)) {
                    float x = boardLeft + (c + 1) * cellSize;
                    float y = boardTop + (r + 0.5f) * cellSize;
                    drawWall(canvas, x, y - reach, x, y + reach);
                }
            }
        }
    }

    private void drawWall(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1 + dp(1f), y1 + dp(2f), x2 + dp(1f), y2 + dp(2f), wallShadowPaint);
        canvas.drawLine(x1, y1, x2, y2, wallPaint);
        canvas.drawLine(x1, y1 - dp(0.5f), x2, y2 - dp(0.5f), wallHighlightPaint);
        float bolt = Math.max(dp(1.6f), cellSize * 0.035f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_WALL_HIGHLIGHT);
        canvas.drawCircle(x1, y1, bolt, paint);
        canvas.drawCircle(x2, y2, bolt, paint);
    }

    private void drawCheckpoints(Canvas canvas, int rows, int cols) {
        int startCell = puzzle.getStartCell();
        int endCell = puzzle.getEndCell();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int cell = r * cols + c;
                int number = puzzle.getCheckpointAt(cell);
                if (number <= 0 || cell == startCell || cell == endCell) continue;
                float cx = centerX(cell);
                float cy = centerY(cell);
                float radius = cellSize * 0.285f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(puzzle.isVisited(cell) ? COLOR_VISITED : COLOR_CHECKPOINT);
                canvas.drawCircle(cx, cy, radius, paint);
                paint.setColor(0xFF172338);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(cellSize * (number >= 10 ? 0.24f : 0.28f));
                paint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
                Paint.FontMetrics fm = paint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(String.valueOf(number), cx, baseline, paint);
            }
        }
    }

    /** Berthu is the start; the app's own Annie launcher portrait is the goal. */
    private void drawCharacterMarkers(Canvas canvas) {
        drawCharacterMarker(canvas, puzzle.getStartCell(), true, 1, null);
        drawCharacterMarker(canvas, puzzle.getEndCell(), false, puzzle.getCheckpointCount(), annieMarkerBitmap);
    }

    private void drawCharacterMarker(Canvas canvas, int cell, boolean isBerthu,
                                     int checkpointNumber, Bitmap markerBitmap) {
        if (cell < 0) return;
        float cx = centerX(cell);
        float cy = centerY(cell);
        float radius = cellSize * 0.405f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isBerthu ? 0xFF5A351F : 0xFF192C45);
        canvas.drawCircle(cx, cy, radius, paint);

        markerRingPaint.setStrokeWidth(Math.max(dp(2f), cellSize * 0.04f));
        markerRingPaint.setColor(isBerthu ? 0xFFFFA64D : 0xFFFFD27D);
        canvas.drawCircle(cx, cy, radius, markerRingPaint);

        float imageRadius = cellSize * 0.325f;
        markerRect.set(cx - imageRadius, cy - imageRadius, cx + imageRadius, cy + imageRadius);
        if (!isBerthu && markerBitmap != null && !markerBitmap.isRecycled()) {
            int save = canvas.save();
            markerClipPath.reset();
            markerClipPath.addCircle(cx, cy, imageRadius, Path.Direction.CW);
            canvas.clipPath(markerClipPath);
            canvas.drawBitmap(markerBitmap, null, markerRect, paint);
            canvas.restoreToCount(save);
        } else if (isBerthu) {
            drawBerthuMiniature(canvas, cx, cy, imageRadius);
        } else {
            drawAnnieFallback(canvas, cx, cy, imageRadius);
        }

        // Gold checkpoint badge keeps the actual numeric sequence legible.
        float badgeX = cx + radius * 0.70f;
        float badgeY = cy + radius * 0.70f;
        float badgeRadius = Math.max(dp(6f), cellSize * 0.145f);
        canvas.drawCircle(badgeX, badgeY, badgeRadius, badgePaint);
        badgeTextPaint.setTextSize(Math.max(dp(8f), cellSize * (checkpointNumber >= 10 ? 0.16f : 0.19f)));
        Paint.FontMetrics fm = badgeTextPaint.getFontMetrics();
        float baseline = badgeY - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(String.valueOf(checkpointNumber), badgeX, baseline, badgeTextPaint);
    }

    private void drawBerthuMiniature(Canvas canvas, float cx, float cy, float half) {
        Path ears = new Path();
        ears.moveTo(cx - half * 0.86f, cy - half * 0.12f);
        ears.lineTo(cx - half * 0.70f, cy - half * 0.98f);
        ears.lineTo(cx - half * 0.18f, cy - half * 0.55f);
        ears.lineTo(cx + half * 0.22f, cy - half * 0.55f);
        ears.lineTo(cx + half * 0.72f, cy - half * 0.98f);
        ears.lineTo(cx + half * 0.88f, cy - half * 0.10f);
        ears.close();
        paint.setColor(0xFFFF963D);
        canvas.drawPath(ears, paint);
        paint.setColor(0xFFFFA94F);
        canvas.drawCircle(cx, cy + half * 0.02f, half * 0.76f, paint);
        paint.setColor(0xFFFFD3A4);
        canvas.drawCircle(cx - half * 0.24f, cy + half * 0.30f, half * 0.24f, paint);
        canvas.drawCircle(cx + half * 0.24f, cy + half * 0.30f, half * 0.24f, paint);
        paint.setColor(0xFF26313D);
        canvas.drawCircle(cx - half * 0.29f, cy - half * 0.02f, Math.max(dp(1.3f), half * 0.09f), paint);
        canvas.drawCircle(cx + half * 0.29f, cy - half * 0.02f, Math.max(dp(1.3f), half * 0.09f), paint);
        paint.setColor(0xFF9A4F36);
        canvas.drawCircle(cx, cy + half * 0.22f, half * 0.075f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(dp(0.8f), half * 0.035f));
        paint.setColor(0xFFFFF0D8);
        canvas.drawLine(cx - half * 0.20f, cy + half * 0.34f, cx - half * 0.95f, cy + half * 0.22f, paint);
        canvas.drawLine(cx - half * 0.20f, cy + half * 0.42f, cx - half * 0.93f, cy + half * 0.48f, paint);
        canvas.drawLine(cx + half * 0.20f, cy + half * 0.34f, cx + half * 0.95f, cy + half * 0.22f, paint);
        canvas.drawLine(cx + half * 0.20f, cy + half * 0.42f, cx + half * 0.93f, cy + half * 0.48f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawAnnieFallback(Canvas canvas, float cx, float cy, float half) {
        paint.setColor(0xFF263B5A);
        rect.set(cx - half * 0.78f, cy + half * 0.18f, cx + half * 0.78f, cy + half * 0.92f);
        canvas.drawRoundRect(rect, half * 0.25f, half * 0.25f, paint);
        paint.setColor(0xFFB96D43);
        canvas.drawCircle(cx, cy - half * 0.10f, half * 0.47f, paint);
        paint.setColor(0xFF3B2630);
        rect.set(cx - half * 0.52f, cy - half * 0.54f, cx + half * 0.52f, cy - half * 0.10f);
        canvas.drawRoundRect(rect, half * 0.20f, half * 0.20f, paint);
        canvas.drawCircle(cx - half * 0.40f, cy - half * 0.04f, half * 0.20f, paint);
        canvas.drawCircle(cx + half * 0.40f, cy - half * 0.04f, half * 0.20f, paint);
        paint.setColor(0xFFFFD8BA);
        canvas.drawCircle(cx, cy - half * 0.03f, half * 0.34f, paint);
        paint.setColor(0xFF392A32);
        canvas.drawCircle(cx - half * 0.13f, cy - half * 0.06f, Math.max(dp(1f), half * 0.045f), paint);
        canvas.drawCircle(cx + half * 0.13f, cy - half * 0.06f, Math.max(dp(1f), half * 0.045f), paint);
    }

    private Bitmap loadAnnieAppIcon() {
        // The project already has this exact portrait in its launcher resources.
        // Prefer the artwork-only foreground so the miniature stays crisp.
        int resourceId = getResources().getIdentifier(
                "ic_launcher_foreground", "mipmap", getContext().getPackageName());
        if (resourceId == 0) {
            resourceId = getResources().getIdentifier(
                    "ic_launcher_round", "mipmap", getContext().getPackageName());
        }
        if (resourceId == 0) {
            resourceId = getResources().getIdentifier(
                    "anniehilation_logo", "drawable", getContext().getPackageName());
        }
        if (resourceId == 0) return null;
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resourceId);
            if (bitmap != null) bitmap.setHasMipMap(true);
            return bitmap;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!inputEnabled || puzzle == null) return false;
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchedCell = -1;
            requestFocus();
            processTouchSample(event.getX(), event.getY());
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            // Process historical samples too: fast finger movement must not skip
            // cells between two delivered MotionEvents.
            int historySize = event.getHistorySize();
            for (int i = 0; i < historySize; i++) {
                processTouchSample(event.getHistoricalX(i), event.getHistoricalY(i));
            }
            processTouchSample(event.getX(), event.getY());
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            processTouchSample(event.getX(), event.getY());
            performClick();
            lastTouchedCell = -1;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            lastTouchedCell = -1;
            return true;
        }
        return true;
    }

    private void processTouchSample(float x, float y) {
        int cell = cellAt(x, y);
        lastPointerX = x;
        lastPointerY = y;
        if (cell < 0 || cell == lastTouchedCell) return;

        if (puzzle.getPathLength() == 0) {
            report(puzzle.enterCell(cell));
        } else {
            handleDragTo(cell, x, y);
        }
        lastTouchedCell = cell;
        postInvalidateOnAnimation();
    }

    /**
     * Fill all crossed cells on quick drags. For diagonal sampling jumps, choose
     * the orthogonal step nearest the finger's movement; no diagonal moves are
     * ever admitted into the puzzle model.
     */
    private void handleDragTo(int targetCell, float touchX, float touchY) {
        if (puzzle.isVisited(targetCell)) {
            report(puzzle.enterCell(targetCell));
            return;
        }

        int current = getCurrentPathCell();
        if (current < 0) {
            report(puzzle.enterCell(targetCell));
            return;
        }

        int guard = puzzle.getRows() + puzzle.getCols() + 2;
        while (current != targetCell && guard-- > 0) {
            int currentRow = current / puzzle.getCols();
            int currentCol = current % puzzle.getCols();
            int targetRow = targetCell / puzzle.getCols();
            int targetCol = targetCell % puzzle.getCols();
            int rowDelta = targetRow - currentRow;
            int colDelta = targetCol - currentCol;
            if (rowDelta == 0 && colDelta == 0) break;

            boolean horizontalFirst;
            if (rowDelta == 0) horizontalFirst = true;
            else if (colDelta == 0) horizontalFirst = false;
            else {
                float dx = Math.abs(touchX - centerX(current));
                float dy = Math.abs(touchY - centerY(current));
                horizontalFirst = dx >= dy;
            }

            int preferred = horizontalFirst
                    ? current + (colDelta > 0 ? 1 : -1)
                    : current + (rowDelta > 0 ? puzzle.getCols() : -puzzle.getCols());
            ZipPuzzle.MoveResult result = puzzle.enterCell(preferred);

            // A diagonal sample can approach a blocked edge. Try the other
            // orthogonal edge if it still moves closer to the sampled target.
            if (result == ZipPuzzle.MoveResult.WALL_BLOCKED && rowDelta != 0 && colDelta != 0) {
                int alternative = horizontalFirst
                        ? current + (rowDelta > 0 ? puzzle.getCols() : -puzzle.getCols())
                        : current + (colDelta > 0 ? 1 : -1);
                ZipPuzzle.MoveResult alternateResult = puzzle.enterCell(alternative);
                if (isProgress(alternateResult) || alternateResult == ZipPuzzle.MoveResult.WON) {
                    result = alternateResult;
                    preferred = alternative;
                }
            }

            report(result);
            if (!isProgress(result) && result != ZipPuzzle.MoveResult.WON) break;
            current = preferred;
            if (result == ZipPuzzle.MoveResult.WON) break;
        }
    }

    private int getCurrentPathCell() {
        int[] path = puzzle.getPathCopy();
        return path.length == 0 ? -1 : path[path.length - 1];
    }

    private boolean isProgress(ZipPuzzle.MoveResult result) {
        return result == ZipPuzzle.MoveResult.STARTED
                || result == ZipPuzzle.MoveResult.ADDED
                || result == ZipPuzzle.MoveResult.RETRACTED;
    }

    private void report(ZipPuzzle.MoveResult result) {
        if (listener != null && result != ZipPuzzle.MoveResult.ALREADY_VISITED) {
            listener.onMoveResult(result);
        }
        if (result == ZipPuzzle.MoveResult.WON && listener != null) listener.onPuzzleSolved();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int cellAt(float x, float y) {
        if (puzzle == null || cellSize <= 0f) return -1;
        float localX = x - boardLeft;
        float localY = y - boardTop;
        if (localX < 0f || localY < 0f
                || localX >= puzzle.getCols() * cellSize
                || localY >= puzzle.getRows() * cellSize) return -1;
        int col = (int) (localX / cellSize);
        int row = (int) (localY / cellSize);
        return row * puzzle.getCols() + col;
    }

    private float centerX(int cell) {
        int col = cell % puzzle.getCols();
        return boardLeft + (col + 0.5f) * cellSize;
    }

    private float centerY(int cell) {
        int row = cell / puzzle.getCols();
        return boardTop + (row + 0.5f) * cellSize;
    }

    private float dp(float value) {
        return value * density;
    }
}
