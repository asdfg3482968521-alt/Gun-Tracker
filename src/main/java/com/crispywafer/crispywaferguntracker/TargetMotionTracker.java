package com.crispywafer.crispywaferguntracker;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Maintains a tiny EWMA motion history per entity. Position deltas are blended with
 * Minecraft's delta movement so one-tick knockback/step jitter does not throw the
 * predicted intercept far away from the target.
 */
final class TargetMotionTracker {
    record Estimate(Vec3 velocity, Vec3 acceleration) {}

    private static final int PRUNE_AFTER_TICKS = 120;
    private final Map<Integer, State> states = new HashMap<>();
    private int lastPruneTick;

    Estimate sample(LivingEntity entity) {
        int tick = entity.tickCount;
        Vec3 position = entity.position();
        Vec3 reportedVelocity = entity.getDeltaMovement();
        State state = states.get(entity.getId());

        if (state == null || tick <= state.lastTick || tick - state.lastTick > 20) {
            Vec3 initialVelocity = clamp(reportedVelocity, Config.MAX_TRACKED_TARGET_SPEED.get());
            State fresh = new State(position, initialVelocity, Vec3.ZERO, tick, tick);
            states.put(entity.getId(), fresh);
            prune(tick);
            return new Estimate(initialVelocity, Vec3.ZERO);
        }

        int dt = Math.max(1, tick - state.lastTick);
        Vec3 positionalVelocity = position.subtract(state.lastPosition).scale(1.0D / dt);
        Vec3 rawVelocity = positionalVelocity.scale(0.80D).add(reportedVelocity.scale(0.20D));
        rawVelocity = clamp(rawVelocity, Config.MAX_TRACKED_TARGET_SPEED.get());

        double velocityAlpha = Config.TARGET_VELOCITY_SMOOTHING.get();
        Vec3 smoothedVelocity = new Vec3(
                MotionMath.ema(state.velocity.x, rawVelocity.x, velocityAlpha),
                MotionMath.ema(state.velocity.y, rawVelocity.y, velocityAlpha),
                MotionMath.ema(state.velocity.z, rawVelocity.z, velocityAlpha)
        );

        Vec3 rawAcceleration = smoothedVelocity.subtract(state.velocity).scale(1.0D / dt);
        rawAcceleration = clamp(rawAcceleration, Config.MAX_TARGET_ACCELERATION.get());
        double accelerationAlpha = Config.TARGET_ACCELERATION_SMOOTHING.get();
        Vec3 smoothedAcceleration = new Vec3(
                MotionMath.ema(state.acceleration.x, rawAcceleration.x, accelerationAlpha),
                MotionMath.ema(state.acceleration.y, rawAcceleration.y, accelerationAlpha),
                MotionMath.ema(state.acceleration.z, rawAcceleration.z, accelerationAlpha)
        );

        state.lastPosition = position;
        state.velocity = smoothedVelocity;
        state.acceleration = smoothedAcceleration;
        state.lastTick = tick;
        state.lastSeenTick = tick;
        prune(tick);
        return new Estimate(smoothedVelocity, smoothedAcceleration);
    }

    void clear() {
        states.clear();
    }

    private void prune(int tick) {
        if (tick - lastPruneTick < 40 && states.size() < 96) return;
        lastPruneTick = tick;
        Iterator<Map.Entry<Integer, State>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            if (tick - state.lastSeenTick > PRUNE_AFTER_TICKS) iterator.remove();
        }
    }

    private static Vec3 clamp(Vec3 value, double maxMagnitude) {
        double[] xyz = MotionMath.clampMagnitude(value.x, value.y, value.z, maxMagnitude);
        return new Vec3(xyz[0], xyz[1], xyz[2]);
    }

    private static final class State {
        private Vec3 lastPosition;
        private Vec3 velocity;
        private Vec3 acceleration;
        private int lastTick;
        private int lastSeenTick;

        private State(Vec3 lastPosition, Vec3 velocity, Vec3 acceleration, int lastTick, int lastSeenTick) {
            this.lastPosition = lastPosition;
            this.velocity = velocity;
            this.acceleration = acceleration;
            this.lastTick = lastTick;
            this.lastSeenTick = lastSeenTick;
        }
    }
}
