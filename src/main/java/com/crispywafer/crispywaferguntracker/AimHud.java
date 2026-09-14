package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT)
public final class AimHud {
    private AimHud() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!Config.SHOW_HUD.get() && !Config.SHOW_FOV_RING.get() && !Config.SHOW_BALLISTICS_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !AimHandler.isStrictSingleplayer(mc)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;

        LivingEntity target = AimHandler.getCurrentTarget();
        boolean active = AimHandler.isAimActive();

        if (Config.SHOW_FOV_RING.get()) {
            drawFovRing(graphics, mc, centerX, centerY, width, height, target != null);
        }

        int ballisticY = centerY + 12;
        if (Config.SHOW_HUD.get()) {
            Component status;
            if (!active) {
                status = Component.translatable("hud.crispywaferguntrackermod.idle");
            } else if (target == null) {
                status = Component.translatable("hud.crispywaferguntrackermod.searching");
            } else {
                double distance = mc.player.distanceTo(target);
                status = Component.translatable(
                        "hud.crispywaferguntrackermod.locked",
                        target.getDisplayName(),
                        String.format(Locale.ROOT, "%.1f", distance)
                );
            }

            int textWidth = mc.font.width(status);
            graphics.drawString(mc.font, status, centerX - textWidth / 2, centerY + 12, 0xFFFFFFFF, true);
            ballisticY = centerY + 24;
        }

        if (Config.SHOW_BALLISTICS_HUD.get() && active && target != null) {
            BallisticProfile profile = TargetSelector.INSTANCE.getLastProfile();
            BallisticsMath.Solution solution = TargetSelector.INSTANCE.getLastSolution();
            if (profile != null) {
                String sourceKey = switch (profile.source()) {
                    case TACZ_LIVE -> "hud.crispywaferguntrackermod.source.tacz_live";
                    case TACZ_DATA -> "hud.crispywaferguntrackermod.source.tacz_data";
                    case MANUAL -> "hud.crispywaferguntrackermod.source.manual";
                };
                String tof = solution.valid()
                        ? String.format(Locale.ROOT, "%.2f", solution.timeTicks())
                        : "--";
                Component ballistic = Component.translatable(
                        "hud.crispywaferguntrackermod.ballistics",
                        Component.translatable(sourceKey),
                        String.format(Locale.ROOT, "%.2f", profile.speedBlocksPerTick()),
                        String.format(Locale.ROOT, "%.3f", profile.gravityPerTick()),
                        String.format(Locale.ROOT, "%.3f", profile.frictionPerTick()),
                        tof
                );
                int ballisticWidth = mc.font.width(ballistic);
                graphics.drawString(mc.font, ballistic, centerX - ballisticWidth / 2, ballisticY, 0xFFE0E0E0, true);
            }
        }
    }

    private static void drawFovRing(
            GuiGraphics graphics,
            Minecraft mc,
            int centerX,
            int centerY,
            int width,
            int height,
            boolean hasTarget
    ) {
        double cameraFov = Math.max(30.0D, mc.options.fov().get());
        double aimFov = Config.AIM_FOV_DEGREES.get();
        double ratio = Math.min(1.0D, aimFov / cameraFov);
        int radius = (int) Math.round(Math.min(width, height) * 0.46D * ratio);
        radius = Math.max(8, radius);

        int color = hasTarget ? 0xFF55FF55 : 0xAAFFFFFF;
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            double angle = (Math.PI * 2.0D * i) / segments;
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int y = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
}
