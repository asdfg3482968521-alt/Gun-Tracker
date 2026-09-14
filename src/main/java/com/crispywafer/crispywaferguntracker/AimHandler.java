package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT)
public final class AimHandler {
    private static final AimActivationController ACTIVATION = new AimActivationController();

    private static boolean previousActive;
    private static boolean returning;
    private static boolean filteredAnglesReady;
    private static boolean flickFastPhase;
    private static float originalYaw;
    private static float originalPitch;
    private static double filteredYaw;
    private static double filteredPitch;

    @Nullable
    private static LivingEntity currentTarget;
    @Nullable
    private static LivingEntity filteredTarget;

    private AimHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            resetAll();
            return;
        }

        if (Keybindings.openConfigKey != null
                && Keybindings.openConfigKey.consumeClick()
                && mc.screen == null) {
            resetAll();
            mc.setScreen(new GunTrackerConfigScreen(null));
            return;
        }

        if (mc.screen != null) {
            resetAll();
            return;
        }

        if (Keybindings.masterToggleKey != null
                && !Keybindings.masterToggleKey.isUnbound()
                && Keybindings.masterToggleKey.consumeClick()) {
            boolean next = !Config.MASTER_ENABLED.get();
            Config.MASTER_ENABLED.set(next);
            Config.CLIENT_SPEC.save();
            resetAll();
            return;
        }

        AimActivationController.SlotInput[] slots = collectTriggerInputs();

        if (!Config.MASTER_ENABLED.get()) {
            resetAll();
            return;
        }

        boolean active = ACTIVATION.update(
                true,
                false,
                System.currentTimeMillis(),
                Config.LONG_PRESS_MS.get(),
                slots
        );
        Config.AimBehavior behavior = Config.AIM_BEHAVIOR.get();

        if (active && !previousActive) {
            returning = false;
            if (behavior == Config.AimBehavior.FLICK_RETURN) {
                originalYaw = player.getYRot();
                originalPitch = player.getXRot();
                flickFastPhase = true;
            }
        }

        if (active) {
            TaczBallistics.INSTANCE.tick(player);
            LivingEntity before = currentTarget;
            updateTarget(player);
            boolean targetChanged = currentTarget != before;
            aimAtCurrentTarget(player, behavior, targetChanged);
        }

        if (!active && previousActive) {
            if (shouldReturnAfterRelease(behavior)) {
                beginReturn();
            } else {
                clearTarget();
            }
        }

        previousActive = active;

        if (returning) {
            updateReturn(player);
        }
    }

    private static AimActivationController.SlotInput[] collectTriggerInputs() {
        AimActivationController.SlotInput[] slots = new AimActivationController.SlotInput[Keybindings.TRIGGER_SLOT_COUNT];
        for (int i = 0; i < Keybindings.TRIGGER_SLOT_COUNT; i++) {
            KeyMapping mapping = Keybindings.triggerKey(i);
            boolean down = mapping != null && !mapping.isUnbound() && mapping.isDown();
            boolean clicked = mapping != null && !mapping.isUnbound() && mapping.consumeClick();
            if (clicked && duplicatesEarlierBinding(i, mapping)) clicked = false;
            slots[i] = new AimActivationController.SlotInput(down, clicked, triggerMode(i));
        }
        return slots;
    }

    private static boolean duplicatesEarlierBinding(int slot, KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        for (int i = 0; i < slot; i++) {
            KeyMapping earlier = Keybindings.triggerKey(i);
            if (earlier != null && !earlier.isUnbound() && mapping.getKey().equals(earlier.getKey())) return true;
        }
        return false;
    }

    private static AimActivationController.TriggerMode triggerMode(int slot) {
        return switch (slot) {
            case 0 -> Config.TRIGGER_MODE_1.get();
            case 1 -> Config.TRIGGER_MODE_2.get();
            case 2 -> Config.TRIGGER_MODE_3.get();
            case 3 -> Config.TRIGGER_MODE_4.get();
            default -> throw new IndexOutOfBoundsException("trigger slot " + slot);
        };
    }

    static boolean shouldReturnAfterRelease(Config.AimBehavior behavior) {
        return behavior == Config.AimBehavior.FLICK_RETURN;
    }

    private static void beginReturn() {
        returning = true;
        currentTarget = null;
        TargetSelector.INSTANCE.clearTarget();
        resetAngleFilter();
    }

    private static void updateReturn(LocalPlayer player) {
        double yawError = Math.abs(AimMath.normalizeDegrees(originalYaw - player.getYRot()));
        double pitchError = Math.abs(originalPitch - player.getXRot());
        if (yawError <= 0.25D && pitchError <= 0.25D) {
            player.setYRot(originalYaw);
            player.setXRot(originalPitch);
            returning = false;
            return;
        }
        player.setYRot((float) AimViewMath.stepAngle(
                player.getYRot(), originalYaw, Config.RETURN_GAIN.get(), Config.RETURN_MAX_TURN.get()));
        player.setXRot((float) AimViewMath.stepLinear(
                player.getXRot(), originalPitch, Config.RETURN_GAIN.get(), Config.RETURN_MAX_TURN.get()));
    }

    private static void updateTarget(LocalPlayer player) {
        TargetSelector.INSTANCE.selectTarget(player);
        currentTarget = TargetSelector.INSTANCE.getTarget();
    }

    private static void aimAtCurrentTarget(LocalPlayer player, Config.AimBehavior behavior, boolean targetChanged) {
        Vec3 targetPos = TargetSelector.INSTANCE.getTargetAimPos(player);
        if (currentTarget == null || targetPos == null) {
            resetAngleFilter();
            return;
        }

        Vec2 raw = calculateNeededAngle(player, targetPos);
        boolean newFilterTarget = !filteredAnglesReady || filteredTarget != currentTarget || targetChanged;
        if (newFilterTarget) {
            filteredTarget = currentTarget;
            filteredYaw = raw.x;
            filteredPitch = raw.y;
            filteredAnglesReady = true;
            if (behavior == Config.AimBehavior.FLICK_RETURN) flickFastPhase = true;
        } else {
            filteredYaw = AimViewMath.filterAngle(filteredYaw, raw.x, Config.VIEW_STABILITY.get());
            filteredPitch = AimViewMath.filterAngle(filteredPitch, raw.y, Config.VIEW_STABILITY.get());
        }

        switch (behavior) {
            case SMOOTH_TRACK -> applyStep(player, Config.SMOOTH_FOLLOW_GAIN.get(), Config.SMOOTH_MAX_TURN.get());
            case SNAP -> {
                if (newFilterTarget) {
                    player.setYRot((float) filteredYaw);
                    player.setXRot((float) filteredPitch);
                } else {
                    applyStep(player, Config.SNAP_FOLLOW_GAIN.get(), Config.SNAP_MAX_TURN.get());
                }
            }
            case FLICK_RETURN -> {
                double error = Math.abs(AimMath.normalizeDegrees(filteredYaw - player.getYRot()))
                        + Math.abs(filteredPitch - player.getXRot());
                if (flickFastPhase && error <= 1.0D) flickFastPhase = false;
                if (flickFastPhase) {
                    applyStep(player, Config.FLICK_INITIAL_GAIN.get(), Config.FLICK_INITIAL_MAX_TURN.get());
                } else {
                    applyStep(player, Config.FLICK_TRACK_GAIN.get(), Config.FLICK_TRACK_MAX_TURN.get());
                }
            }
        }
    }

    private static void applyStep(LocalPlayer player, double gain, double maxTurn) {
        player.setYRot((float) AimViewMath.stepAngle(player.getYRot(), filteredYaw, gain, maxTurn));
        player.setXRot((float) AimViewMath.stepLinear(player.getXRot(), filteredPitch, gain, maxTurn));
    }

    private static Vec2 calculateNeededAngle(LocalPlayer player, Vec3 targetPos) {
        Vec3 eye = player.getEyePosition();
        double dx = targetPos.x - eye.x;
        double dy = targetPos.y - eye.y;
        double dz = targetPos.z - eye.z;
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distanceXZ));
        return new Vec2(yaw, pitch);
    }

    private static void resetAngleFilter() {
        filteredAnglesReady = false;
        filteredTarget = null;
        filteredYaw = 0.0D;
        filteredPitch = 0.0D;
        flickFastPhase = false;
    }

    static boolean isAimActive() {
        return ACTIVATION.isActive();
    }

    @Nullable
    static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private static void clearTarget() {
        currentTarget = null;
        TargetSelector.INSTANCE.clearTarget();
        resetAngleFilter();
    }

    private static void resetAll() {
        ACTIVATION.reset();
        previousActive = false;
        returning = false;
        currentTarget = null;
        TargetSelector.INSTANCE.resetTracking();
        resetAngleFilter();
    }
}
