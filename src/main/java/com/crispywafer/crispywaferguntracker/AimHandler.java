package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
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
    private static float originalYaw;
    private static float originalPitch;
    private static float returnStartYaw;
    private static float returnStartPitch;
    private static double returnProgress;

    @Nullable
    private static LivingEntity currentTarget;

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

        AimActivationController.SlotInput[] slots = collectTriggerInputs();

        if (!isStrictSingleplayer(mc)) {
            if (anyClicked(slots)) {
                player.displayClientMessage(
                        Component.translatable("message.crispywaferguntrackermod.singleplayer_only"),
                        true
                );
            }
            resetAll();
            return;
        }

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
            returnProgress = 0.0D;
            if (behavior == Config.AimBehavior.FLICK_RETURN) {
                originalYaw = player.getYRot();
                originalPitch = player.getXRot();
            }
        }

        if (active) {
            TaczBallistics.INSTANCE.tick(player);
            updateTarget(player);
            switch (behavior) {
                case SMOOTH_TRACK -> aimAtCurrentTarget(
                        player,
                        Config.CONTINUOUS_SPEED.get(),
                        false
                );
                case SNAP -> aimAtCurrentTarget(player, 1.0D, true);
                case FLICK_RETURN -> aimAtCurrentTarget(
                        player,
                        Config.FLICK_SPEED.get(),
                        Config.FLICK_SPEED.get() >= 0.999D
                );
            }
        }

        if (!active && previousActive) {
            if (shouldReturnAfterRelease(behavior)) {
                beginReturn(player);
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
            if (clicked && duplicatesEarlierBinding(i, mapping)) {
                clicked = false;
            }
            slots[i] = new AimActivationController.SlotInput(down, clicked, triggerMode(i));
        }
        return slots;
    }

    private static boolean duplicatesEarlierBinding(int slot, KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        for (int i = 0; i < slot; i++) {
            KeyMapping earlier = Keybindings.triggerKey(i);
            if (earlier != null && !earlier.isUnbound() && mapping.getKey().equals(earlier.getKey())) {
                return true;
            }
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

    private static boolean anyClicked(AimActivationController.SlotInput[] slots) {
        for (AimActivationController.SlotInput slot : slots) {
            if (slot != null && slot.clicked()) return true;
        }
        return false;
    }

    static boolean shouldReturnAfterRelease(Config.AimBehavior behavior) {
        return behavior == Config.AimBehavior.FLICK_RETURN;
    }

    private static void beginReturn(LocalPlayer player) {
        returnStartYaw = player.getYRot();
        returnStartPitch = player.getXRot();
        returnProgress = 0.0D;
        returning = true;
    }

    private static void updateReturn(LocalPlayer player) {
        double speed = Config.FLICK_RETURN_SPEED.get();
        returnProgress = Math.min(1.0D, returnProgress + speed);
        float newYaw = lerpAngle(returnStartYaw, originalYaw, (float) returnProgress);
        float newPitch = (float) (returnStartPitch + (originalPitch - returnStartPitch) * returnProgress);
        player.setYRot(newYaw);
        player.setXRot(newPitch);

        if (returnProgress >= 1.0D) {
            returning = false;
            clearTarget();
        }
    }

    /**
     * Hard safety boundary: integrated server must exist and only one player may be present.
     * LAN worlds with a second player therefore disable the feature too.
     */
    static boolean isStrictSingleplayer(Minecraft mc) {
        if (!mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) return false;
        return mc.getSingleplayerServer().getPlayerList().getPlayerCount() <= 1;
    }

    private static void updateTarget(LocalPlayer player) {
        TargetSelector.INSTANCE.selectTarget(player);
        currentTarget = TargetSelector.INSTANCE.getTarget();
    }

    private static void aimAtCurrentTarget(LocalPlayer player, double speed, boolean instant) {
        Vec3 targetPos = TargetSelector.INSTANCE.getTargetAimPos(player);
        if (currentTarget == null || targetPos == null) return;

        Vec2 needed = calculateNeededAngle(player, targetPos);
        if (instant) {
            player.setYRot(needed.x);
            player.setXRot(needed.y);
            return;
        }

        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();
        float deltaYaw = (float) AimMath.normalizeDegrees(needed.x - currentYaw);
        float deltaPitch = needed.y - currentPitch;

        int substeps = Math.max(1, Config.AIM_SUBSTEPS.get());
        float finalYaw = currentYaw + deltaYaw * (float) speed;
        float finalPitch = currentPitch + deltaPitch * (float) speed;
        float stepYaw = (finalYaw - currentYaw) / substeps;
        float stepPitch = (finalPitch - currentPitch) / substeps;

        for (int i = 0; i < substeps; i++) {
            currentYaw += stepYaw;
            currentPitch += stepPitch;
            player.setYRot(currentYaw);
            player.setXRot(currentPitch);
        }
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

    private static float lerpAngle(float from, float to, float progress) {
        float delta = (float) AimMath.normalizeDegrees(to - from);
        return from + delta * progress;
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
    }

    private static void resetAll() {
        ACTIVATION.reset();
        previousActive = false;
        returning = false;
        returnProgress = 0.0D;
        currentTarget = null;
        TargetSelector.INSTANCE.resetTracking();
    }
}
