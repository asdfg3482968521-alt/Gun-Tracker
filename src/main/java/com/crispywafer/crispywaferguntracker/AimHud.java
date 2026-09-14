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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT)
public final class AimHud {
    static final int PADDING = 5;
    static final int LINE_HEIGHT = 11;

    record HudBounds(int left, int top, int right, int bottom) {
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private AimHud() {}

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!Config.SHOW_HUD.get() && !Config.SHOW_FOV_RING.get() && !Config.SHOW_BALLISTICS_HUD.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        LivingEntity target = AimHandler.getCurrentTarget();
        boolean masterEnabled = Config.MASTER_ENABLED.get();

        if (Config.SHOW_FOV_RING.get() && masterEnabled) {
            drawFovRing(graphics, mc, centerX, centerY, width, height, target != null);
        }

        if (Config.SHOW_HUD.get()) {
            int hudX = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_X_NORMALIZED.get()) * width);
            int hudY = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_Y_NORMALIZED.get()) * height);
            renderStatusBox(graphics, mc, hudX, hudY, false);
        }

        if (Config.SHOW_BALLISTICS_HUD.get() && masterEnabled && AimHandler.isAimActive() && target != null) {
            drawBallistics(graphics, mc, centerX, centerY + 28);
        }
    }

    static HudBounds renderStatusBox(GuiGraphics graphics, Minecraft mc, int centerX, int centerY, boolean preview) {
        List<Component> lines = statusLines(mc, preview);
        if (lines.isEmpty()) return new HudBounds(centerX, centerY, centerX, centerY);

        int contentWidth = 0;
        for (Component line : lines) contentWidth = Math.max(contentWidth, mc.font.width(line));
        int boxWidth = contentWidth + PADDING * 2;
        int boxHeight = lines.size() * LINE_HEIGHT + PADDING * 2;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int left = Math.max(2, Math.min(screenWidth - boxWidth - 2, centerX - boxWidth / 2));
        int top = Math.max(2, Math.min(screenHeight - boxHeight - 2, centerY - boxHeight / 2));
        int right = left + boxWidth;
        int bottom = top + boxHeight;

        graphics.fill(left, top, right, bottom, 0xA0101010);
        graphics.fill(left, top, right, top + 1, 0xCCB0B0B0);
        graphics.fill(left, bottom - 1, right, bottom, 0xCC606060);

        int y = top + PADDING;
        for (Component line : lines) {
            graphics.drawString(mc.font, line, left + PADDING, y, 0xFFFFFFFF, true);
            y += LINE_HEIGHT;
        }
        return new HudBounds(left, top, right, bottom);
    }

    private static List<Component> statusLines(Minecraft mc, boolean preview) {
        List<Component> lines = new ArrayList<>();
        boolean masterEnabled = Config.MASTER_ENABLED.get();
        LivingEntity target = AimHandler.getCurrentTarget();

        if (Config.HUD_SHOW_MASTER.get()) {
            lines.add(Component.translatable(
                    "hud.invisiblekeybinding.master_state",
                    Component.translatable(masterEnabled
                            ? "invisiblekeybinding.config.on"
                            : "invisiblekeybinding.config.off")
            ));
        }
        if (Config.HUD_SHOW_MODE.get()) {
            lines.add(Component.translatable(
                    "hud.invisiblekeybinding.mode",
                    Component.translatable(modeTranslationKey(Config.AIM_BEHAVIOR.get()))
            ));
        }
        if (Config.HUD_SHOW_TARGET_NAME.get()) {
            Component name = target != null
                    ? target.getDisplayName()
                    : (preview ? Component.literal("Steve") : Component.translatable("hud.invisiblekeybinding.no_target"));
            lines.add(Component.translatable("hud.invisiblekeybinding.target", name));
        }
        if (Config.HUD_SHOW_TARGET_DISTANCE.get()) {
            String distance = target != null && mc.player != null
                    ? String.format(Locale.ROOT, "%.1f", mc.player.distanceTo(target))
                    : (preview ? "23.6" : "--");
            lines.add(Component.translatable("hud.invisiblekeybinding.distance", distance));
        }
        return lines;
    }

    private static void drawBallistics(GuiGraphics graphics, Minecraft mc, int centerX, int y) {
        BallisticProfile profile = TargetSelector.INSTANCE.getLastProfile();
        BallisticsMath.Solution solution = TargetSelector.INSTANCE.getLastSolution();
        if (profile == null) return;

        String sourceKey = switch (profile.source()) {
            case TACZ_LIVE -> "hud.invisiblekeybinding.source.tacz_live";
            case TACZ_DATA -> "hud.invisiblekeybinding.source.tacz_data";
            case MANUAL -> "hud.invisiblekeybinding.source.manual";
        };
        String tof = solution.valid() ? String.format(Locale.ROOT, "%.2f", solution.timeTicks()) : "--";
        Component ballistic = Component.translatable(
                "hud.invisiblekeybinding.ballistics",
                Component.translatable(sourceKey),
                String.format(Locale.ROOT, "%.2f", profile.speedBlocksPerTick()),
                String.format(Locale.ROOT, "%.3f", profile.gravityPerTick()),
                String.format(Locale.ROOT, "%.3f", profile.frictionPerTick()),
                tof
        );
        int ballisticWidth = mc.font.width(ballistic);
        graphics.drawString(mc.font, ballistic, centerX - ballisticWidth / 2, y, 0xFFE0E0E0, true);
    }

    static String modeTranslationKey(Config.AimBehavior behavior) {
        return switch (behavior) {
            case SMOOTH_TRACK -> "hud.invisiblekeybinding.mode.smooth_track";
            case SNAP -> "hud.invisiblekeybinding.mode.snap";
            case FLICK_RETURN -> "hud.invisiblekeybinding.mode.flick_return";
        };
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
        int radius = Math.max(8, (int) Math.round(Math.min(width, height) * 0.46D * ratio));
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
