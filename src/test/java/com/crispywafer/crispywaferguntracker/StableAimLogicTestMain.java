package com.crispywafer.crispywaferguntracker;

public final class StableAimLogicTestMain {
    public static void main(String[] args) {
        testStickinessMargin();
        testCandidateConfirmation();
        testWrappedAngleFilter();
        testTurnCaps();
        testStableTargetConfigContracts();
        testAimModeConfigContracts();
        System.out.println("StableAimLogic tests passed");
    }

    private static void testStickinessMargin() {
        assertClose(42.8D, TargetLockPolicy.switchMargin(85), 1.0e-9, "85 stickiness margin");
        assertFalse(TargetLockPolicy.shouldChallenge(100.0D, 60.0D, 85),
                "40-point lead is not enough at 85");
        assertTrue(TargetLockPolicy.shouldChallenge(100.0D, 50.0D, 85),
                "50-point lead is enough at 85");
        assertClose(2.0D, TargetLockPolicy.switchMargin(0), 1.0e-9, "zero stickiness margin");
        assertClose(50.0D, TargetLockPolicy.switchMargin(100), 1.0e-9, "max stickiness margin");
    }

    private static void testCandidateConfirmation() {
        assertEquals(6, TargetLockPolicy.advanceConfirmation(true, true, 3, 3),
                "same candidate accumulates elapsed ticks");
        assertEquals(0, TargetLockPolicy.advanceConfirmation(true, false, 9, 3),
                "lost advantage resets");
        assertEquals(0, TargetLockPolicy.advanceConfirmation(false, true, 9, 3),
                "different candidate resets before accumulating");
        assertFalse(TargetLockPolicy.confirmed(11, 12), "11 ticks is not confirmed");
        assertTrue(TargetLockPolicy.confirmed(12, 12), "12 ticks confirms");
        assertTrue(TargetLockPolicy.confirmed(0, 0), "zero confirmation threshold confirms immediately");
    }

    private static void testWrappedAngleFilter() {
        double filtered = AimViewMath.filterAngle(179.0D, -177.0D, 70);
        assertClose(-179.8D, AimMath.normalizeDegrees(filtered), 0.25D,
                "yaw filter must cross the short way around the wrap boundary");
    }

    private static void testTurnCaps() {
        assertClose(10.0D, AimViewMath.stepAngle(0.0D, 90.0D, 1.0D, 10.0D), 1.0e-9,
                "angle cap applies");
        assertClose(-175.0D, AimMath.normalizeDegrees(AimViewMath.stepAngle(179.0D, -170.0D, 1.0D, 6.0D)),
                1.0e-9, "wrapped turn uses short direction and cap");
        assertClose(-8.0D, AimViewMath.stepLinear(0.0D, -30.0D, 1.0D, 8.0D), 1.0e-9,
                "pitch cap applies");
    }

    private static void testStableTargetConfigContracts() {
        assertEquals(0, Config.STICKINESS_MIN, "stickiness min");
        assertEquals(100, Config.STICKINESS_MAX, "stickiness max");
        assertEquals(60, Config.SWITCH_CONFIRM_TICKS_MAX, "switch confirm max");
        assertEquals(10, Config.CANDIDATE_SCAN_INTERVAL_MAX, "candidate scan max");
        assertEquals(60, Config.INVISIBLE_TOLERANCE_TICKS_MAX, "invisible tolerance max");
        assertClose(180.0D, Config.UNLOCK_FOV_MAX, 0.0D, "unlock fov max");
        assertTrue(Config.TARGET_PLAYERS != null, "player target toggle exists");
        assertTrue(Config.TARGET_HOSTILES != null, "hostile target toggle exists");
        assertTrue(Config.TARGET_OTHERS != null, "other target toggle exists");
        assertTrue(Config.ANTI_BOT_MODE != null, "anti-bot config exists");
        assertTrue(Config.AntiBotMode.STANDARD != null, "standard anti-bot mode exists");
        assertTrue(Config.AntiBotMode.STRICT != null, "strict anti-bot mode exists");
    }

    private static void testAimModeConfigContracts() {
        assertEquals(100, Config.VIEW_STABILITY_MAX, "view stability max");
        assertTrue(Config.VIEW_STABILITY != null, "view stability config exists");
        assertTrue(Config.SMOOTH_FOLLOW_GAIN != null, "smooth follow gain exists");
        assertTrue(Config.SMOOTH_MAX_TURN != null, "smooth max turn exists");
        assertTrue(Config.SNAP_FOLLOW_GAIN != null, "snap follow gain exists");
        assertTrue(Config.SNAP_MAX_TURN != null, "snap max turn exists");
        assertTrue(Config.FLICK_INITIAL_GAIN != null, "flick initial gain exists");
        assertTrue(Config.FLICK_INITIAL_MAX_TURN != null, "flick initial max turn exists");
        assertTrue(Config.FLICK_TRACK_GAIN != null, "flick tracking gain exists");
        assertTrue(Config.FLICK_TRACK_MAX_TURN != null, "flick tracking max turn exists");
        assertTrue(Config.RETURN_GAIN != null, "return gain exists");
        assertTrue(Config.RETURN_MAX_TURN != null, "return max turn exists");
        assertTrue(Keybindings.masterToggleKey == null, "master toggle key is unregistered during pure tests");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertClose(double expected, double actual, double eps, String message) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
