package com.crispywafer.crispywaferguntracker;

/** Immutable projectile parameters used by the intercept solver. */
public record BallisticProfile(
        String weaponKey,
        double speedBlocksPerTick,
        double frictionPerTick,
        double gravityPerTick,
        Source source,
        int calibratedTick
) {
    public enum Source {
        TACZ_LIVE,
        TACZ_DATA,
        MANUAL
    }

    public boolean usable() {
        return Double.isFinite(speedBlocksPerTick) && speedBlocksPerTick > 0.01D
                && Double.isFinite(frictionPerTick) && frictionPerTick >= 0.0D
                && Double.isFinite(gravityPerTick) && gravityPerTick >= 0.0D;
    }
}
