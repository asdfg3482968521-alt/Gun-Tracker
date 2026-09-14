package com.crispywafer.crispywaferguntracker;

public final class AimMathTestMain {
    public static void main(String[] args) {
        testNormalizeDegreesWraps();
        testWithinFovUsesAngularRadius();
        testCrosshairPriorityBeatsCloserOffAxisTarget();
        testLeadTimeIsClampedAndUsesProjectileSpeed();
        testPredictedAxisOffsetUsesVelocityAndLeadTime();
        testGravityCompensationAimsAboveDrop();
        testTargetScoreRewardsThreatAndCurrentTarget();
        testHysteresisRequiresMeaningfulImprovement();
        testTacZDragDistanceMatchesDiscreteTicks();
        testTacZGravityDropMatchesTickOrder();
        testBallisticInterceptStationaryTarget();
        testBallisticInterceptLeadsLateralTarget();
        testBallisticInterceptCompensatesGravity();
        testDragIncreasesFlightTime();
        testMotionSmoothingRejectsOneTickSpike();
        testAccelerationClampLimitsPredictionNoise();
        System.out.println("AimMath tests passed");
    }

    private static void testNormalizeDegreesWraps() {
        assertClose(-170.0, AimMath.normalizeDegrees(190.0), 1.0e-9, "190 -> -170");
        assertClose(170.0, AimMath.normalizeDegrees(-190.0), 1.0e-9, "-190 -> 170");
    }

    private static void testWithinFovUsesAngularRadius() {
        assertTrue(AimMath.isWithinFov(25.0, 30.0), "25 degrees should be inside 30-degree aim radius");
        assertFalse(AimMath.isWithinFov(31.0, 30.0), "31 degrees should be outside 30-degree aim radius");
    }

    private static void testCrosshairPriorityBeatsCloserOffAxisTarget() {
        assertTrue(AimMath.isBetterCandidate(4.0, 30.0, 12.0, 5.0),
                "crosshair priority should prefer the smaller angular error before distance");
        assertTrue(AimMath.isBetterCandidate(4.0, 5.0, 4.0, 30.0),
                "distance should break ties when angular error is equal");
    }

    private static void testLeadTimeIsClampedAndUsesProjectileSpeed() {
        assertClose(5.0, AimMath.leadTimeTicks(50.0, 10.0, 20.0), 1.0e-9,
                "distance/speed should produce flight time");
        assertClose(20.0, AimMath.leadTimeTicks(500.0, 10.0, 20.0), 1.0e-9,
                "lead time should respect max prediction window");
        assertClose(0.0, AimMath.leadTimeTicks(50.0, 0.0, 20.0), 1.0e-9,
                "disabled projectile speed should disable lead");
    }

    private static void testPredictedAxisOffsetUsesVelocityAndLeadTime() {
        assertClose(6.0, AimMath.predictedAxisOffset(1.5, 4.0), 1.0e-9,
                "prediction should project target velocity over flight time");
    }

    private static void testGravityCompensationAimsAboveDrop() {
        assertClose(4.0, AimMath.gravityCompensation(0.5, 4.0), 1.0e-9,
                "aim point should be raised by half g t^2");
        assertClose(0.0, AimMath.gravityCompensation(0.0, 4.0), 1.0e-9,
                "zero gravity should need no compensation");
    }

    private static void testTargetScoreRewardsThreatAndCurrentTarget() {
        double plain = AimMath.targetScore(10.0, 20.0, false, false, false);
        double approaching = AimMath.targetScore(10.0, 20.0, true, false, false);
        double hurt = AimMath.targetScore(10.0, 20.0, false, true, false);
        double current = AimMath.targetScore(10.0, 20.0, false, false, true);
        assertTrue(approaching < plain, "approaching targets should receive a better score");
        assertTrue(hurt < plain, "recently hurt targets should receive a better score");
        assertTrue(current < plain, "current target should receive a stickiness bonus");
        assertTrue(AimMath.targetScore(4.0, 60.0, false, false, false)
                        < AimMath.targetScore(12.0, 5.0, false, false, false),
                "crosshair angle should remain the dominant score component");
    }

    private static void testHysteresisRequiresMeaningfulImprovement() {
        assertFalse(AimMath.shouldSwitchTarget(100.0, 95.0, 10.0),
                "small improvements should not cause target switching");
        assertTrue(AimMath.shouldSwitchTarget(100.0, 80.0, 10.0),
                "large improvements should allow target switching");
    }

    private static void testTacZDragDistanceMatchesDiscreteTicks() {
        assertClose(27.1, BallisticsMath.directionalTravel(10.0, 0.10, 3.0), 1.0e-9,
                "TACZ drag travel should use geometric per-tick decay");
    }

    private static void testTacZGravityDropMatchesTickOrder() {
        assertClose(-0.29, BallisticsMath.gravityDisplacementY(0.10, 0.10, 3.0), 1.0e-9,
                "TACZ applies gravity after movement and drag each tick");
    }

    private static void testBallisticInterceptStationaryTarget() {
        BallisticsMath.Solution s = BallisticsMath.solveIntercept(
                20.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                4.0, 0.0, 0.0, 20.0
        );
        assertTrue(s.valid(), "stationary target should have an intercept");
        assertClose(5.0, s.timeTicks(), 0.02, "20 blocks at 4 blocks/tick should take five ticks");
        assertClose(20.0, s.aimX(), 0.05, "stationary target aim X");
    }

    private static void testBallisticInterceptLeadsLateralTarget() {
        BallisticsMath.Solution s = BallisticsMath.solveIntercept(
                20.0, 0.0, 0.0,
                0.0, 0.0, 1.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                5.0, 0.0, 0.0, 20.0
        );
        assertTrue(s.valid(), "moving target should have an intercept");
        assertTrue(s.aimZ() > 3.5, "solver should aim ahead of lateral movement");
        assertClose(4.082, s.timeTicks(), 0.08, "lateral intercept time should be solved iteratively");
    }

    private static void testBallisticInterceptCompensatesGravity() {
        BallisticsMath.Solution s = BallisticsMath.solveIntercept(
                20.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                4.0, 0.0, 0.10, 20.0
        );
        assertTrue(s.valid(), "gravity trajectory should still solve");
        assertTrue(s.aimY() > 0.5, "gravity compensation should raise initial aim direction");
    }

    private static void testDragIncreasesFlightTime() {
        BallisticsMath.Solution noDrag = BallisticsMath.solveIntercept(
                20.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 20.0);
        BallisticsMath.Solution drag = BallisticsMath.solveIntercept(
                20.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 5.0, 0.08, 0.0, 20.0);
        assertTrue(noDrag.valid() && drag.valid(), "both trajectories should solve");
        assertTrue(drag.timeTicks() > noDrag.timeTicks(), "drag should increase flight time");
    }

    private static void testMotionSmoothingRejectsOneTickSpike() {
        double smoothed = MotionMath.ema(1.0, 4.0, 0.25);
        assertClose(1.75, smoothed, 1.0e-9, "EMA should damp a one-tick velocity spike");
    }

    private static void testAccelerationClampLimitsPredictionNoise() {
        double[] clamped = MotionMath.clampMagnitude(3.0, 4.0, 0.0, 2.5);
        assertClose(1.5, clamped[0], 1.0e-9, "clamped X");
        assertClose(2.0, clamped[1], 1.0e-9, "clamped Y");
        assertClose(0.0, clamped[2], 1.0e-9, "clamped Z");
    }

    private static void assertClose(double expected, double actual, double eps, String message) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
