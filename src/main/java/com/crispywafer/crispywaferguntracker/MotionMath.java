package com.crispywafer.crispywaferguntracker;

/** Pure motion-filter helpers used by the target tracker. */
public final class MotionMath {
    private MotionMath() {}

    public static double ema(double previous, double sample, double alpha) {
        double a = Math.max(0.0D, Math.min(1.0D, alpha));
        return previous + (sample - previous) * a;
    }

    public static double[] clampMagnitude(double x, double y, double z, double maxMagnitude) {
        double max = Math.max(0.0D, maxMagnitude);
        double lengthSq = x * x + y * y + z * z;
        if (lengthSq <= max * max || lengthSq <= 1.0e-18D) {
            return new double[]{x, y, z};
        }
        double scale = max / Math.sqrt(lengthSq);
        return new double[]{x * scale, y * scale, z * scale};
    }
}
