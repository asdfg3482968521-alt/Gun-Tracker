package com.crispywafer.crispywaferguntracker;

public final class AimActivationControllerTestMain {
    public static void main(String[] args) {
        testHoldUsesLogicalOr();
        testToggleFlipsOncePerClick();
        testLongHoldWaitsForThreshold();
        testMasterSwitchForcesOff();
        testScreenOpenForcesOffAndClearsTransientState();
        testDuplicateToggleClicksCountOnce();
        testConfigRangeContracts();
        System.out.println("AimActivationController tests passed");
    }

    private static AimActivationController.SlotInput slot(
            boolean down,
            boolean clicked,
            AimActivationController.TriggerMode mode
    ) {
        return new AimActivationController.SlotInput(down, clicked, mode);
    }

    private static AimActivationController.SlotInput[] emptySlots() {
        return new AimActivationController.SlotInput[] {
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD)
        };
    }

    private static void testHoldUsesLogicalOr() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1000, 200, slots), "slot 1 hold should activate");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.HOLD);
        slots[1] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1010, 200, slots), "slot 2 hold should keep activation on");
    }

    private static void testToggleFlipsOncePerClick() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "first click toggles on");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1010, 200, slots), "toggle remains on");
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertFalse(c.update(true, false, 1020, 200, slots), "second click toggles off");
    }

    private static void testLongHoldWaitsForThreshold() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.LONG_HOLD);
        assertFalse(c.update(true, false, 1000, 200, slots), "long hold must not activate immediately");
        assertFalse(c.update(true, false, 1199, 200, slots), "long hold must wait for threshold");
        assertTrue(c.update(true, false, 1200, 200, slots), "long hold activates at threshold");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.LONG_HOLD);
        assertFalse(c.update(true, false, 1210, 200, slots), "release ends long hold");
    }

    private static void testMasterSwitchForcesOff() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "toggle on first");
        assertFalse(c.update(false, false, 1010, 200, slots), "master off must force inactive");
        assertFalse(c.update(true, false, 1020, 200, emptySlots()), "master off must clear toggle state");
    }

    private static void testScreenOpenForcesOffAndClearsTransientState() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1000, 200, slots), "hold on first");
        assertFalse(c.update(true, true, 1010, 200, slots), "screen open must suppress aim");
        assertFalse(c.update(true, false, 1020, 200, emptySlots()), "screen close must not restore stale hold");
    }

    private static void testDuplicateToggleClicksCountOnce() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        slots[1] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "duplicate click signals must toggle only once");
    }

    private static void testConfigRangeContracts() {
        assertClose(300.0D, Config.MAX_DISTANCE_MAX, 0.0D, "distance max");
        assertEquals(32, Config.AIM_SUBSTEPS_MAX, "substeps max");
        assertClose(200.0D, Config.MAX_LEAD_TICKS_MAX, 0.0D, "lead max");
        assertClose(200.0D, Config.PROJECTILE_SPEED_MAX, 0.0D, "projectile speed max");
        assertClose(50.0D, Config.MAX_TRACKED_TARGET_SPEED_MAX, 0.0D, "tracked speed max");
        assertClose(10.0D, Config.MAX_TARGET_ACCELERATION_MAX, 0.0D, "acceleration max");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void assertClose(double expected, double actual, double eps, String message) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }
}
