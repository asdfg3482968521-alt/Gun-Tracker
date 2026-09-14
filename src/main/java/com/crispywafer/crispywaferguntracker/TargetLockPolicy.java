package com.crispywafer.crispywaferguntracker;

/** Pure target-switch policy helpers so sticky lock behavior is easy to test. */
public final class TargetLockPolicy {
    private TargetLockPolicy() {}

    public static double switchMargin(int stickiness) {
        int clamped = Math.max(0, Math.min(100, stickiness));
        return 2.0D + 0.48D * clamped;
    }

    public static boolean shouldChallenge(double currentScore, double candidateScore, int stickiness) {
        return candidateScore + switchMargin(stickiness) < currentScore;
    }

    public static int advanceConfirmation(
            boolean sameCandidate,
            boolean stillBetter,
            int previousTicks,
            int elapsedTicks
    ) {
        if (!sameCandidate || !stillBetter) return 0;
        return Math.max(0, previousTicks) + Math.max(1, elapsedTicks);
    }

    public static boolean confirmed(int confirmedTicks, int requiredTicks) {
        return confirmedTicks >= Math.max(0, requiredTicks);
    }
}
