package com.crispywafer.crispywaferguntracker;

/** Pure math helpers kept independent from Minecraft for easy testing. */
public final class AimMath {
    private static final double EPSILON = 1.0e-7;

    private AimMath() {}

    public static double normalizeDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped <= -180.0) wrapped += 360.0;
        return wrapped;
    }

    /**
     * aimFovDegrees is the angular radius from the crosshair, not the full cone width.
     */
    public static boolean isWithinFov(double angularErrorDegrees, double aimFovDegrees) {
        return angularErrorDegrees >= 0.0
                && angularErrorDegrees <= Math.max(0.0, aimFovDegrees) + EPSILON;
    }

    public static double leadTimeTicks(double distance, double projectileSpeed, double maxLeadTicks) {
        if (distance <= 0.0 || projectileSpeed <= EPSILON || maxLeadTicks <= 0.0) return 0.0;
        return Math.min(distance / projectileSpeed, maxLeadTicks);
    }

    public static double predictedAxisOffset(double velocityPerTick, double leadTimeTicks) {
        return velocityPerTick * Math.max(0.0, leadTimeTicks);
    }

    public static double gravityCompensation(double gravityPerTickSquared, double leadTimeTicks) {
        double t = Math.max(0.0, leadTimeTicks);
        return 0.5 * Math.max(0.0, gravityPerTickSquared) * t * t;
    }

    /** Lower score is better. Crosshair angle deliberately dominates distance. */
    public static double targetScore(
            double angularErrorDegrees,
            double distanceBlocks,
            boolean approachingPlayer,
            boolean recentlyHurt,
            boolean currentTarget
    ) {
        double score = Math.max(0.0, angularErrorDegrees) * 10.0
                + Math.max(0.0, distanceBlocks) * 0.10;
        if (approachingPlayer) score -= 4.0;
        if (recentlyHurt) score -= 2.0;
        if (currentTarget) score -= 8.0;
        return score;
    }

    /** Switch only when the new score beats the old one by at least the threshold. */
    public static boolean shouldSwitchTarget(double currentScore, double candidateScore, double hysteresisThreshold) {
        return candidateScore + Math.max(0.0, hysteresisThreshold) < currentScore - EPSILON;
    }

    /**
     * Crosshair-first ordering. Distance only breaks an angular tie.
     */
    public static boolean isBetterCandidate(
            double candidateAngle,
            double candidateDistance,
            double bestAngle,
            double bestDistance
    ) {
        if (candidateAngle < bestAngle - EPSILON) return true;
        if (candidateAngle > bestAngle + EPSILON) return false;
        return candidateDistance < bestDistance;
    }
}
