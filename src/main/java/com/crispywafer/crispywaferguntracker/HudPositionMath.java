package com.crispywafer.crispywaferguntracker;

public final class HudPositionMath {
    private HudPositionMath() {}

    public static double clampNormalized(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
