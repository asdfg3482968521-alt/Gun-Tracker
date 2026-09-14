package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side weapon feel and recoil control for TACZ.
 *
 * <p>TACZ writes its camera recoil inside {@link ViewportEvent.ComputeCameraAngles}
 * from {@code com.tacz.guns.client.event.CameraSetupEvent#applyCameraRecoil} and
 * {@code #applyLevelCameraAnimation}. Nothing in the vanilla pipeline exposes how much
 * those handlers added, so this class captures the untouched camera angle at
 * {@link EventPriority#HIGHEST} and blends back towards it at
 * {@link EventPriority#LOWEST}, which runs after TACZ has written its offset.
 *
 * <p>All state here is client-only and never leaves the game process.
 */
@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT)
public final class WeaponControlHandler {
    private static float basePitch;
    private static float baseYaw;
    private static boolean baseCaptured;

    private WeaponControlHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void captureBaseAngles(ViewportEvent.ComputeCameraAngles event) {
        basePitch = event.getPitch();
        baseYaw = event.getYaw();
        baseCaptured = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void applyWeaponControl(ViewportEvent.ComputeCameraAngles event) {
        if (!baseCaptured) {
            return;
        }
        if (!Config.NO_RECOIL.get() && !Config.WEAPON_FEEL.get()) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        float pitch = event.getPitch();
        float yaw = event.getYaw();

        float anchorPitch = basePitch;
        float anchorYaw = baseYaw;
        if (!Config.KEEP_VANILLA_BOB.get()) {
            anchorPitch = player.getXRot();
            anchorYaw = player.getYRot();
        }

        if (Config.NO_RECOIL.get()) {
            double strength = Config.RECOIL_CANCEL_STRENGTH.get() / 100.0D;
            pitch = (float) (pitch - (pitch - anchorPitch) * strength);
        }

        if (Config.WEAPON_FEEL.get()) {
            double stability = Config.WEAPON_STABILITY.get() / 100.0D;
            pitch = (float) (pitch - (pitch - anchorPitch) * stability);
            yaw = (float) (yaw - (yaw - anchorYaw) * stability);
        }

        event.setPitch(pitch);
        event.setYaw(yaw);
    }
}
