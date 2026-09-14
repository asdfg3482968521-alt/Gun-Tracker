package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Selects and predicts a stable target inside the configured crosshair FOV. */
public final class TargetSelector {
    public static final TargetSelector INSTANCE = new TargetSelector();

    private final TargetMotionTracker motionTracker = new TargetMotionTracker();

    @Nullable
    private LivingEntity target;
    private int lastFullScanTick = Integer.MIN_VALUE;
    private BallisticProfile lastProfile;
    private BallisticsMath.Solution lastSolution = BallisticsMath.Solution.invalid();

    private TargetSelector() {}

    public void selectTarget(Player player) {
        if (player == null) {
            clearTarget();
            return;
        }

        boolean currentValid = target != null && isValidTarget(player, target);
        if (currentValid && Config.STICKY_TARGET.get()) {
            int interval = Math.max(1, Config.LOCKED_RESCAN_INTERVAL.get());
            if (player.tickCount - lastFullScanTick < interval) {
                return;
            }
        }
        lastFullScanTick = player.tickCount;

        double maxDistance = Config.MAX_DISTANCE.get();
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(maxDistance)
        );

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LivingEntity entity : entities) {
            if (!isValidTarget(player, entity)) continue;
            double score = scoreTarget(player, entity, entity == target);
            if (score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }

        if (currentValid && Config.STICKY_TARGET.get()) {
            if (best == null || best == target) return;

            double currentScore = scoreTarget(player, target, true);
            if (!AimMath.shouldSwitchTarget(currentScore, bestScore, Config.SWITCH_HYSTERESIS.get())) {
                return;
            }
        }

        target = best;
        lastSolution = BallisticsMath.Solution.invalid();
    }

    public boolean isValidTarget(Player player, @Nullable LivingEntity entity) {
        if (player == null || entity == null || entity == player) return false;
        if (!entity.isAlive() || entity.isRemoved()) return false;

        double maxDistance = Config.MAX_DISTANCE.get();
        if (player.distanceToSqr(entity) > maxDistance * maxDistance) return false;
        if (!matchesTargetMode(entity)) return false;

        // FOV is cheap; do it before the relatively expensive ray trace.
        double angle = angularErrorDegrees(player, entity);
        if (!AimMath.isWithinFov(angle, Config.AIM_FOV_DEGREES.get())) return false;
        return !Config.VISIBLE_ONLY.get() || player.hasLineOfSight(entity);
    }

    private boolean matchesTargetMode(LivingEntity entity) {
        Config.TargetMode mode = Config.TARGET_MODE.get();
        return switch (mode) {
            case HOSTILE_ONLY -> entity instanceof Monster;
            case MOBS_ONLY -> !(entity instanceof Player);
            case ALL_LIVING -> true;
        };
    }

    private double scoreTarget(Player player, LivingEntity entity, boolean current) {
        double angle = angularErrorDegrees(player, entity);
        double distance = Math.sqrt(player.distanceToSqr(entity));
        boolean approaching = isApproachingPlayer(player, entity);
        boolean recentlyHurt = entity.hurtTime > 0;
        return AimMath.targetScore(angle, distance, approaching, recentlyHurt, current);
    }

    private boolean isApproachingPlayer(Player player, LivingEntity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0e-6) return false;
        Vec3 towardPlayer = player.position().subtract(entity.position());
        return towardPlayer.lengthSqr() > 1.0e-6 && velocity.dot(towardPlayer) > 0.0;
    }

    public double angularErrorDegrees(Player player, LivingEntity entity) {
        Vec3 eye = player.getEyePosition();
        Vec3 toTarget = calculateBaseAimPosition(entity).subtract(eye);
        if (toTarget.lengthSqr() < 1.0e-9) return 0.0;

        Vec3 look = player.getLookAngle().normalize();
        Vec3 direction = toTarget.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(direction)));
        return Math.toDegrees(Math.acos(dot));
    }

    private Vec3 calculateBaseAimPosition(LivingEntity entity) {
        Config.AimPoint point = Config.AIM_POINT.get();
        if (point == Config.AimPoint.HEAD) return entity.getEyePosition();

        double fraction = switch (point) {
            case CHEST -> 0.72D;
            case CENTER -> 0.50D;
            case CUSTOM -> Config.CUSTOM_AIM_HEIGHT.get();
            default -> 0.90D;
        };
        return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * fraction, entity.getZ());
    }

    private Vec3 calculatePredictedAimPosition(Player player, LivingEntity entity) {
        Vec3 base = calculateBaseAimPosition(entity);
        if (!Config.ADVANCED_BALLISTICS.get()) {
            return calculateLegacyPrediction(player, entity, base);
        }

        if (!(player instanceof LocalPlayer localPlayer)) return calculateLegacyPrediction(player, entity, base);
        BallisticProfile profile = TaczBallistics.INSTANCE.resolve(localPlayer);
        if (!profile.usable()) return calculateLegacyPrediction(player, entity, base);

        TargetMotionTracker.Estimate estimate = motionTracker.sample(entity);
        Vec3 velocity = Config.LEAD_ENABLED.get() ? estimate.velocity() : Vec3.ZERO;
        Vec3 acceleration = Vec3.ZERO;
        if (Config.LEAD_ENABLED.get() && Config.USE_TARGET_ACCELERATION.get()) {
            // Minecraft acceleration is highly transient; keep a conservative fraction
            // so jump/knockback spikes do not dominate a long-range lead solution.
            acceleration = estimate.acceleration().scale(0.35D);
        }

        Vec3 inherited = Vec3.ZERO;
        if (Config.INHERIT_SHOOTER_VELOCITY.get()) {
            Vec3 shooterVelocity = player.getDeltaMovement();
            inherited = new Vec3(shooterVelocity.x, player.onGround() ? 0.0D : shooterVelocity.y, shooterVelocity.z);
        }

        Vec3 origin = player.getEyePosition();
        Vec3 relative = base.subtract(origin);
        BallisticsMath.Solution solution = BallisticsMath.solveIntercept(
                relative.x, relative.y, relative.z,
                velocity.x, velocity.y, velocity.z,
                acceleration.x, acceleration.y, acceleration.z,
                inherited.x, inherited.y, inherited.z,
                profile.speedBlocksPerTick(),
                profile.frictionPerTick(),
                profile.gravityPerTick(),
                Config.MAX_LEAD_TICKS.get()
        );

        lastProfile = profile;
        lastSolution = solution;
        if (!solution.valid()) return calculateLegacyPrediction(player, entity, base);
        return origin.add(solution.aimX(), solution.aimY(), solution.aimZ());
    }

    private Vec3 calculateLegacyPrediction(Player player, LivingEntity entity, Vec3 base) {
        lastProfile = new BallisticProfile(
                "manual",
                Config.PROJECTILE_SPEED.get(),
                Config.PROJECTILE_FRICTION.get(),
                Config.GRAVITY_COMPENSATION.get() ? Config.PROJECTILE_GRAVITY.get() : 0.0D,
                BallisticProfile.Source.MANUAL,
                player.tickCount
        );
        lastSolution = BallisticsMath.Solution.invalid();
        if (!Config.LEAD_ENABLED.get()) return base;

        double distance = player.getEyePosition().distanceTo(base);
        double leadTicks = AimMath.leadTimeTicks(
                distance,
                Config.PROJECTILE_SPEED.get(),
                Config.MAX_LEAD_TICKS.get()
        );
        if (leadTicks <= 0.0D) return base;

        Vec3 velocity = motionTracker.sample(entity).velocity();
        double x = base.x + AimMath.predictedAxisOffset(velocity.x, leadTicks);
        double y = base.y + AimMath.predictedAxisOffset(velocity.y, leadTicks);
        double z = base.z + AimMath.predictedAxisOffset(velocity.z, leadTicks);

        if (Config.GRAVITY_COMPENSATION.get()) {
            y += AimMath.gravityCompensation(Config.PROJECTILE_GRAVITY.get(), leadTicks);
        }
        return new Vec3(x, y, z);
    }

    public void clearTarget() {
        target = null;
        lastSolution = BallisticsMath.Solution.invalid();
    }

    public void resetTracking() {
        clearTarget();
        motionTracker.clear();
        lastFullScanTick = Integer.MIN_VALUE;
        lastProfile = null;
    }

    @Nullable
    public LivingEntity getTarget() {
        return target;
    }

    @Nullable
    public Vec3 getTargetAimPos(Player player) {
        return target == null || player == null ? null : calculatePredictedAimPosition(player, target);
    }

    @Nullable
    BallisticProfile getLastProfile() {
        return lastProfile;
    }

    BallisticsMath.Solution getLastSolution() {
        return lastSolution;
    }
}
