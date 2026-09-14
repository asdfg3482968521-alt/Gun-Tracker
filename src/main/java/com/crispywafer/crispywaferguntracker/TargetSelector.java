package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Selects and predicts a stable target inside the configured crosshair FOV. */
public final class TargetSelector {
    public static final TargetSelector INSTANCE = new TargetSelector();

    private final TargetMotionTracker motionTracker = new TargetMotionTracker();

    @Nullable
    private LivingEntity target;
    @Nullable
    private LivingEntity candidateTarget;
    private int candidateTicks;
    private int invisibleTicks;
    private int lastCandidateScanTick = Integer.MIN_VALUE;
    private BallisticProfile lastProfile;
    private BallisticsMath.Solution lastSolution = BallisticsMath.Solution.invalid();

    private TargetSelector() {}

    public void selectTarget(Player player) {
        if (player == null) {
            clearTarget();
            return;
        }

        if (target == null) {
            acquireBestTarget(player);
            return;
        }

        if (!isRetainedTarget(player, target)) {
            clearTarget();
            acquireBestTarget(player);
            return;
        }

        int interval = Math.max(Config.CANDIDATE_SCAN_INTERVAL_MIN, Config.CANDIDATE_SCAN_INTERVAL.get());
        if (lastCandidateScanTick != Integer.MIN_VALUE
                && player.tickCount - lastCandidateScanTick < interval) {
            return;
        }

        int elapsed = lastCandidateScanTick == Integer.MIN_VALUE
                ? interval
                : Math.max(1, player.tickCount - lastCandidateScanTick);
        lastCandidateScanTick = player.tickCount;

        LivingEntity best = findBestAcquisitionTarget(player, target);
        if (best == null) {
            resetCandidate();
            return;
        }

        double currentScore = scoreTarget(player, target);
        double candidateScore = scoreTarget(player, best);
        boolean better = TargetLockPolicy.shouldChallenge(
                currentScore, candidateScore, Config.STICKINESS.get());
        if (!better) {
            resetCandidate();
            return;
        }

        if (candidateTarget != best) {
            candidateTarget = best;
            candidateTicks = Math.max(1, elapsed);
        } else {
            candidateTicks = TargetLockPolicy.advanceConfirmation(
                    true, true, candidateTicks, elapsed);
        }

        if (TargetLockPolicy.confirmed(candidateTicks, Config.SWITCH_CONFIRM_TICKS.get())) {
            target = candidateTarget;
            resetCandidate();
            invisibleTicks = 0;
            lastSolution = BallisticsMath.Solution.invalid();
        }
    }

    private void acquireBestTarget(Player player) {
        int interval = Math.max(Config.CANDIDATE_SCAN_INTERVAL_MIN, Config.CANDIDATE_SCAN_INTERVAL.get());
        if (lastCandidateScanTick != Integer.MIN_VALUE
                && player.tickCount - lastCandidateScanTick < interval) {
            return;
        }
        lastCandidateScanTick = player.tickCount;
        LivingEntity best = findBestAcquisitionTarget(player, null);
        if (best != null) {
            target = best;
            invisibleTicks = 0;
            resetCandidate();
            lastSolution = BallisticsMath.Solution.invalid();
        }
    }

    @Nullable
    private LivingEntity findBestAcquisitionTarget(Player player, @Nullable LivingEntity excluded) {
        double maxDistance = Config.MAX_DISTANCE.get();
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(maxDistance));

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (LivingEntity entity : entities) {
            if (entity == excluded || !isAcquisitionTarget(player, entity)) continue;
            double score = scoreTarget(player, entity);
            if (score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    public boolean isValidTarget(Player player, @Nullable LivingEntity entity) {
        return isAcquisitionTarget(player, entity);
    }

    private boolean isAcquisitionTarget(Player player, @Nullable LivingEntity entity) {
        if (!isBaseTarget(player, entity)) return false;
        double angle = angularErrorDegrees(player, entity);
        if (!AimMath.isWithinFov(angle, Config.AIM_FOV_DEGREES.get())) return false;
        return !Config.VISIBLE_ONLY.get() || player.hasLineOfSight(entity);
    }

    private boolean isRetainedTarget(Player player, @Nullable LivingEntity entity) {
        if (!isBaseTarget(player, entity)) return false;
        double unlockFov = Math.max(Config.AIM_FOV_DEGREES.get(), Config.UNLOCK_FOV_DEGREES.get());
        if (!AimMath.isWithinFov(angularErrorDegrees(player, entity), unlockFov)) return false;

        if (!Config.VISIBLE_ONLY.get()) {
            invisibleTicks = 0;
            return true;
        }
        if (player.hasLineOfSight(entity)) {
            invisibleTicks = 0;
            return true;
        }
        invisibleTicks++;
        return invisibleTicks <= Config.INVISIBLE_TOLERANCE_TICKS.get();
    }

    private boolean isBaseTarget(Player player, @Nullable LivingEntity entity) {
        if (player == null || entity == null || entity == player) return false;
        if (entity instanceof ArmorStand) return false;
        if (!entity.isAlive() || entity.isRemoved()) return false;
        double maxDistance = Config.MAX_DISTANCE.get();
        if (player.distanceToSqr(entity) > maxDistance * maxDistance) return false;
        return matchesEnabledCategory(entity) && passesAntiBot(entity);
    }

    private boolean matchesEnabledCategory(LivingEntity entity) {
        if (entity instanceof Player) return Config.TARGET_PLAYERS.get();
        if (entity instanceof Monster) return Config.TARGET_HOSTILES.get();
        return Config.TARGET_OTHERS.get();
    }

    private boolean passesAntiBot(LivingEntity entity) {
        if (!(entity instanceof Player playerEntity)) return true;
        Config.AntiBotMode mode = Config.ANTI_BOT_MODE.get();
        if (mode == Config.AntiBotMode.OFF) return true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return false;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerEntity.getUUID());
        if (info == null) return false;
        if (mode == Config.AntiBotMode.STANDARD) return true;

        UUID uuid = playerEntity.getUUID();
        if (uuid == null || (uuid.getMostSignificantBits() == 0L && uuid.getLeastSignificantBits() == 0L)) {
            return false;
        }
        if (info.getProfile() == null || info.getProfile().getId() == null
                || !uuid.equals(info.getProfile().getId())) {
            return false;
        }

        int softAnomalies = 0;
        String entityName = playerEntity.getGameProfile().getName();
        String infoName = info.getProfile().getName();
        if (entityName == null || !entityName.matches("[A-Za-z0-9_]{1,16}")) softAnomalies++;
        if (infoName == null || !infoName.equals(entityName)) softAnomalies++;
        if (playerEntity.getGameProfile().getId() == null
                || !uuid.equals(playerEntity.getGameProfile().getId())) softAnomalies++;
        return softAnomalies < 2;
    }

    private double scoreTarget(Player player, LivingEntity entity) {
        double angle = angularErrorDegrees(player, entity);
        double distance = Math.sqrt(player.distanceToSqr(entity));
        boolean approaching = isApproachingPlayer(player, entity);
        boolean recentlyHurt = entity.hurtTime > 0;
        return AimMath.targetScore(angle, distance, approaching, recentlyHurt, false);
    }

    private boolean isApproachingPlayer(Player player, LivingEntity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0e-6) return false;
        Vec3 towardPlayer = player.position().subtract(entity.position());
        return towardPlayer.lengthSqr() > 1.0e-6 && velocity.dot(towardPlayer) > 0.0;
    }

    private void resetCandidate() {
        candidateTarget = null;
        candidateTicks = 0;
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
        candidateTarget = null;
        candidateTicks = 0;
        invisibleTicks = 0;
        lastCandidateScanTick = Integer.MIN_VALUE;
        lastSolution = BallisticsMath.Solution.invalid();
    }

    public void resetTracking() {
        clearTarget();
        motionTracker.clear();
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
