package com.rictharakan.anniehilation;

import android.graphics.Paint;

public final class GameAssets {

    private GameAssets() {
        // Utility class; no instances needed.
    }

    // Night sky
    public static final int SKY_TOP = 0xFF111A35;
    public static final int SKY_BOTTOM = 0xFF6C4962;

    // Bengaluru skyline
    public static final int BUILDING_FAR = 0xFF252C49;
    public static final int BUILDING_NEAR = 0xFF182238;
    public static final int WINDOW = 0xFFFFC778;

    // Platforms and road
    public static final int GROUND = 0xFF252B35;
    public static final int CONCRETE = 0xFF888A91;

    // Annie's school uniform
    public static final int NAVY = 0xFF182A50;
    public static final int GOLD = 0xFFE3B64F;
    public static final int WHITE = 0xFFF2F0EA;

    // Character
    public static final int HAIR = 0xFF17151A;
    public static final int SKIN = 0xFFC98F72;

    // On-screen controls
    public static final int CONTROL = 0xAA101727;
    public static final int CONTROL_EDGE = 0xCCE4E8F2;

    // Creates a Paint object for drawing.
    public static Paint paint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return paint;
    }
}