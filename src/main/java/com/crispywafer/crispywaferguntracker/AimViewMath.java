package com.crispywafer.crispywaferguntracker;

/** Pure camera-angle filtering and capped stepping helpers. */
public final class AimViewMath {
    private AimViewMath() {}

    public static double filterAngle(double previous, double sample, int stability) {
        double normalized = Math.max(0.0D, Math.min(1.0D, stability / 100.0D));
        double alpha = 0.65D - 0.50D * normalized;
        double delta = AimMath.normalizeDegrees(sample - previous);
        return previous + delta * alpha;
    }

    public static double stepAngle(double current, double target, double gain, double maxDegrees) {
        double delta = AimMath.normalizeDegrees(target - current);
        double step = clamp(delta * Math.max(0.0D, gain), Math.max(0.0D, maxDegrees));
        return current + step;
    }

    public static double stepLinear(double current, double target, double gain, double maxDegrees) {
        double delta = target - current;
        double step = clamp(delta * Math.max(0.0D, gain), Math.max(0.0D, maxDegrees));
        return current + step;
    }

    private static double clamp(double value, double maxMagnitude) {
        if (value > maxMagnitude) return maxMagnitude;
        if (value < -maxMagnitude) return -maxMagnitude;
        return value;
    }
}
