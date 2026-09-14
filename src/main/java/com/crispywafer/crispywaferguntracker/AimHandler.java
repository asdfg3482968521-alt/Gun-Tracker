package com.crispywafer.crispywaferguntracker;

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
    private static boolean continuousAimActive;
    private static boolean flicking;
    private static boolean returning;
    private static boolean recordedOriginal;

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

        boolean togglePressed = Keybindings.toggleAimKey != null && Keybindings.toggleAimKey.consumeClick();
        if (!isStrictSingleplayer(mc)) {
            if (togglePressed) {
                player.displayClientMessage(
                        Component.translatable("message.crispywaferguntrackermod.singleplayer_only"),
                        true
                );
            }
            resetAll();
            return;
        }

        if (togglePressed) {
            continuousAimActive = !continuousAimActive;
            player.displayClientMessage(
                    Component.translatable(continuousAimActive
                            ? "message.crispywaferguntrackermod.aim_on"
                            : "message.crispywaferguntrackermod.aim_off"),
                    true
            );
            if (!continuousAimActive && !flicking && !returning) {
                TargetSelector.INSTANCE.clearTarget();
                currentTarget = null;
            }
        }

        boolean flickDown = Keybindings.flickKey != null && Keybindings.flickKey.isDown();
        if (continuousAimActive || flickDown || flicking) {
            TaczBallistics.INSTANCE.tick(player);
        }
        if (flickDown && !flicking && !returning) {
            flicking = true;
            recordedOriginal = false;
        }

        if (continuousAimActive && !flicking && !returning) {
            updateTarget(player);
            aimAtCurrentTarget(player, Config.CONTINUOUS_SPEED.get(), Config.INSTANT_CONTINUOUS.get());
        }

        if (flicking) {
            if (!recordedOriginal) {
                originalYaw = player.getYRot();
                originalPitch = player.getXRot();
                recordedOriginal = true;
            }
            updateTarget(player);
            aimAtCurrentTarget(player, Config.FLICK_SPEED.get(), Config.FLICK_SPEED.get() >= 0.999D);
        }

        if (!flickDown && flicking) {
            returnStartYaw = player.getYRot();
            returnStartPitch = player.getXRot();
            returnProgress = 0.0D;
            returning = true;
            flicking = false;
            recordedOriginal = false;
        }

        if (returning) {
            double speed = Config.FLICK_RETURN_SPEED.get();
            returnProgress = Math.min(1.0D, returnProgress + speed);
            float newYaw = lerpAngle(returnStartYaw, originalYaw, (float) returnProgress);
            float newPitch = (float) (returnStartPitch + (originalPitch - returnStartPitch) * returnProgress);
            player.setYRot(newYaw);
            player.setXRot(newPitch);

            if (returnProgress >= 1.0D) {
                returning = false;
                if (!continuousAimActive) {
                    TargetSelector.INSTANCE.clearTarget();
                    currentTarget = null;
                }
            }
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
        return continuousAimActive || flicking;
    }

    @Nullable
    static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private static void resetAll() {
        continuousAimActive = false;
        flicking = false;
        returning = false;
        recordedOriginal = false;
        returnProgress = 0.0D;
        currentTarget = null;
        TargetSelector.INSTANCE.resetTracking();
    }
}
