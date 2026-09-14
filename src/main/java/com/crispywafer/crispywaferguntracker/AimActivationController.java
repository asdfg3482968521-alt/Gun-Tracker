package com.crispywafer.crispywaferguntracker;

import java.util.Arrays;

/**
 * Pure input state machine that combines four configurable trigger slots into
 * one shared aim activation state.
 */
public final class AimActivationController {
    public enum TriggerMode {
        HOLD,
        TOGGLE,
        LONG_HOLD;

        public TriggerMode next() {
            TriggerMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public record SlotInput(boolean down, boolean clicked, TriggerMode mode) {
        public SlotInput {
            if (mode == null) mode = TriggerMode.HOLD;
        }
    }

    private final long[] downSince = {-1L, -1L, -1L, -1L};
    private boolean toggleActive;
    private boolean active;

    public boolean update(
            boolean masterEnabled,
            boolean screenOpen,
            long nowMillis,
            long longPressMillis,
            SlotInput[] slots
    ) {
        if (!masterEnabled || screenOpen) {
            reset();
            return false;
        }
        if (slots == null || slots.length < 4) {
            throw new IllegalArgumentException("Exactly four trigger slots are required");
        }

        boolean anyHold = false;
        boolean anyToggleClick = false;
        long threshold = Math.max(0L, longPressMillis);

        for (int i = 0; i < 4; i++) {
            SlotInput slot = slots[i];
            if (slot == null) {
                downSince[i] = -1L;
                continue;
            }

            switch (slot.mode()) {
                case HOLD -> {
                    downSince[i] = -1L;
                    anyHold |= slot.down();
                }
                case TOGGLE -> {
                    downSince[i] = -1L;
                    anyToggleClick |= slot.clicked();
                }
                case LONG_HOLD -> {
                    if (slot.down()) {
                        if (downSince[i] < 0L) {
                            downSince[i] = nowMillis;
                        }
                        anyHold |= nowMillis - downSince[i] >= threshold;
                    } else {
                        downSince[i] = -1L;
                    }
                }
            }
        }

        if (anyToggleClick) {
            toggleActive = !toggleActive;
        }
        active = toggleActive || anyHold;
        return active;
    }

    public boolean isActive() {
        return active;
    }

    public void reset() {
        toggleActive = false;
        active = false;
        Arrays.fill(downSince, -1L);
    }
}
