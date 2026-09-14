from pathlib import Path
import json

root = Path('.')
base = root / 'src/main/java/com/crispywafer/crispywaferguntracker'

# Pure HUD coordinate helper (kept Minecraft-independent for logic tests).
(base / 'HudPositionMath.java').write_text('''package com.crispywafer.crispywaferguntracker;\n\npublic final class HudPositionMath {\n    private HudPositionMath() {}\n\n    public static double clampNormalized(double value) {\n        return Math.max(0.0D, Math.min(1.0D, value));\n    }\n}\n''', encoding='utf-8')

# Add persistent HUD config fields.
config_path = base / 'Config.java'
s = config_path.read_text(encoding='utf-8')
needle = '    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;\n'
replacement = '''    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;\n    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_MASTER;\n    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_MODE;\n    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_TARGET_NAME;\n    public static final ForgeConfigSpec.BooleanValue HUD_SHOW_TARGET_DISTANCE;\n    public static final ForgeConfigSpec.DoubleValue HUD_X_NORMALIZED;\n    public static final ForgeConfigSpec.DoubleValue HUD_Y_NORMALIZED;\n'''
if needle not in s:
    raise SystemExit('Config SHOW_HUD declaration marker not found')
s = s.replace(needle, replacement, 1)
needle = '''        SHOW_HUD = BUILDER.comment("Show current lock status and target near the crosshair.")\n                .define("show_hud", true);\n'''
replacement = '''        SHOW_HUD = BUILDER.comment("Show the persistent movable aim status box.")\n                .define("show_hud", true);\n        HUD_SHOW_MASTER = BUILDER.comment("Show master aim enabled/disabled state in the status box.")\n                .define("hud_show_master", true);\n        HUD_SHOW_MODE = BUILDER.comment("Show current aim behavior in the status box.")\n                .define("hud_show_mode", true);\n        HUD_SHOW_TARGET_NAME = BUILDER.comment("Show locked target name in the status box.")\n                .define("hud_show_target_name", true);\n        HUD_SHOW_TARGET_DISTANCE = BUILDER.comment("Show locked target distance in the status box.")\n                .define("hud_show_target_distance", true);\n        HUD_X_NORMALIZED = BUILDER.comment("Normalized horizontal center position of the status box.")\n                .defineInRange("hud_x_normalized", 0.50D, 0.0D, 1.0D);\n        HUD_Y_NORMALIZED = BUILDER.comment("Normalized vertical center position of the status box.")\n                .defineInRange("hud_y_normalized", 0.62D, 0.0D, 1.0D);\n'''
if needle not in s:
    raise SystemExit('Config SHOW_HUD definition marker not found')
s = s.replace(needle, replacement, 1)
config_path.write_text(s, encoding='utf-8')

# Rebuild AimHud around a movable persistent status box.
(base / 'AimHud.java').write_text(r'''package com.crispywafer.crispywaferguntracker;

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
                    "hud.crispywaferguntrackermod.master_state",
                    Component.translatable(masterEnabled
                            ? "crispywaferguntrackermod.config.on"
                            : "crispywaferguntrackermod.config.off")
            ));
        }
        if (Config.HUD_SHOW_MODE.get()) {
            lines.add(Component.translatable(
                    "hud.crispywaferguntrackermod.mode",
                    Component.translatable(modeTranslationKey(Config.AIM_BEHAVIOR.get()))
            ));
        }
        if (Config.HUD_SHOW_TARGET_NAME.get()) {
            Component name = target != null
                    ? target.getDisplayName()
                    : (preview ? Component.literal("Steve") : Component.translatable("hud.crispywaferguntrackermod.no_target"));
            lines.add(Component.translatable("hud.crispywaferguntrackermod.target", name));
        }
        if (Config.HUD_SHOW_TARGET_DISTANCE.get()) {
            String distance = target != null && mc.player != null
                    ? String.format(Locale.ROOT, "%.1f", mc.player.distanceTo(target))
                    : (preview ? "23.6" : "--");
            lines.add(Component.translatable("hud.crispywaferguntrackermod.distance", distance));
        }
        return lines;
    }

    private static void drawBallistics(GuiGraphics graphics, Minecraft mc, int centerX, int y) {
        BallisticProfile profile = TargetSelector.INSTANCE.getLastProfile();
        BallisticsMath.Solution solution = TargetSelector.INSTANCE.getLastSolution();
        if (profile == null) return;

        String sourceKey = switch (profile.source()) {
            case TACZ_LIVE -> "hud.crispywaferguntrackermod.source.tacz_live";
            case TACZ_DATA -> "hud.crispywaferguntrackermod.source.tacz_data";
            case MANUAL -> "hud.crispywaferguntrackermod.source.manual";
        };
        String tof = solution.valid() ? String.format(Locale.ROOT, "%.2f", solution.timeTicks()) : "--";
        Component ballistic = Component.translatable(
                "hud.crispywaferguntrackermod.ballistics",
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
            case SMOOTH_TRACK -> "hud.crispywaferguntrackermod.mode.smooth_track";
            case SNAP -> "hud.crispywaferguntrackermod.mode.snap";
            case FLICK_RETURN -> "hud.crispywaferguntrackermod.mode.flick_return";
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
''', encoding='utf-8')

# Drag-and-drop HUD positioning screen.
(base / 'HudPositionScreen.java').write_text(r'''package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HudPositionScreen extends Screen {
    private final Screen parent;
    private final double originalX;
    private final double originalY;
    private boolean dragging;
    private boolean committed;
    private AimHud.HudBounds bounds = new AimHud.HudBounds(0, 0, 0, 0);

    public HudPositionScreen(Screen parent) {
        super(Component.translatable("crispywaferguntrackermod.hud_position.title"));
        this.parent = parent;
        this.originalX = Config.HUD_X_NORMALIZED.get();
        this.originalY = Config.HUD_Y_NORMALIZED.get();
    }

    @Override
    protected void init() {
        int y = height - 30;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> commitAndClose())
                .bounds(width / 2 + 4, y, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("crispywaferguntrackermod.config.hud_reset_position"), button -> {
            Config.HUD_X_NORMALIZED.set(0.50D);
            Config.HUD_Y_NORMALIZED.set(0.62D);
        }).bounds(width / 2 - 100, y, 96, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && bounds.contains(mouseX, mouseY)) {
            dragging = true;
            updatePosition(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            updatePosition(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            updatePosition(mouseX, mouseY);
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updatePosition(double mouseX, double mouseY) {
        Config.HUD_X_NORMALIZED.set(HudPositionMath.clampNormalized(mouseX / Math.max(1.0D, width)));
        Config.HUD_Y_NORMALIZED.set(HudPositionMath.clampNormalized(mouseY / Math.max(1.0D, height)));
    }

    private void commitAndClose() {
        committed = true;
        Config.CLIENT_SPEC.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        if (!committed) {
            Config.HUD_X_NORMALIZED.set(originalX);
            Config.HUD_Y_NORMALIZED.set(originalY);
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("crispywaferguntrackermod.hud_position.instruction"),
                width / 2, 26, 0xD0D0D0);

        Minecraft mc = Minecraft.getInstance();
        int x = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_X_NORMALIZED.get()) * width);
        int y = (int) Math.round(HudPositionMath.clampNormalized(Config.HUD_Y_NORMALIZED.get()) * height);
        bounds = AimHud.renderStatusBox(graphics, mc, x, y, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
''', encoding='utf-8')

# Expand HUD page in the in-game settings.
screen_path = base / 'GunTrackerConfigScreen.java'
s = screen_path.read_text(encoding='utf-8')
old = '''    private void buildHudPage() {\n        addToggleRow(0, "crispywaferguntrackermod.config.show_hud",\n                Config.SHOW_HUD::get, Config.SHOW_HUD::set);\n        addToggleRow(1, "crispywaferguntrackermod.config.show_fov_ring",\n                Config.SHOW_FOV_RING::get, Config.SHOW_FOV_RING::set);\n        addToggleRow(2, "crispywaferguntrackermod.config.show_ballistics_hud",\n                Config.SHOW_BALLISTICS_HUD::get, Config.SHOW_BALLISTICS_HUD::set);\n    }\n'''
new = '''    private void buildHudPage() {\n        addToggleRow(0, "crispywaferguntrackermod.config.show_hud",\n                Config.SHOW_HUD::get, Config.SHOW_HUD::set);\n        addToggleRow(1, "crispywaferguntrackermod.config.hud_show_master",\n                Config.HUD_SHOW_MASTER::get, Config.HUD_SHOW_MASTER::set);\n        addToggleRow(2, "crispywaferguntrackermod.config.hud_show_mode",\n                Config.HUD_SHOW_MODE::get, Config.HUD_SHOW_MODE::set);\n        addToggleRow(3, "crispywaferguntrackermod.config.hud_show_target_name",\n                Config.HUD_SHOW_TARGET_NAME::get, Config.HUD_SHOW_TARGET_NAME::set);\n        addToggleRow(4, "crispywaferguntrackermod.config.hud_show_target_distance",\n                Config.HUD_SHOW_TARGET_DISTANCE::get, Config.HUD_SHOW_TARGET_DISTANCE::set);\n        addToggleRow(5, "crispywaferguntrackermod.config.show_fov_ring",\n                Config.SHOW_FOV_RING::get, Config.SHOW_FOV_RING::set);\n        addToggleRow(6, "crispywaferguntrackermod.config.show_ballistics_hud",\n                Config.SHOW_BALLISTICS_HUD::get, Config.SHOW_BALLISTICS_HUD::set);\n        if (isRowVisible(7)) {\n            addRenderableWidget(Button.builder(Component.translatable("crispywaferguntrackermod.config.hud_adjust_position"), button -> {\n                Config.CLIENT_SPEC.save();\n                if (minecraft != null) minecraft.setScreen(new HudPositionScreen(this));\n            }).bounds(contentLeft, rowY(7), contentWidth(), 20).build());\n        }\n        if (isRowVisible(8)) {\n            addRenderableWidget(Button.builder(Component.translatable("crispywaferguntrackermod.config.hud_reset_position"), button -> {\n                Config.HUD_X_NORMALIZED.set(0.50D);\n                Config.HUD_Y_NORMALIZED.set(0.62D);\n                Config.CLIENT_SPEC.save();\n            }).bounds(contentLeft, rowY(8), contentWidth(), 20).build());\n        }\n    }\n'''
if old not in s:
    raise SystemExit('HUD page marker not found')
s = s.replace(old, new, 1)
if '            case HUD -> 3;\n' not in s:
    raise SystemExit('HUD row count marker not found')
s = s.replace('            case HUD -> 3;\n', '            case HUD -> 9;\n', 1)
screen_path.write_text(s, encoding='utf-8')

# Add config contract assertions to the already-red/green HUD test suite.
test_path = root / 'src/test/java/com/crispywafer/crispywaferguntracker/StableAimLogicTestMain.java'
s = test_path.read_text(encoding='utf-8')
needle = '        assertClose(0.37D, HudPositionMath.clampNormalized(0.37D), 0.0D, "hud unchanged");\n'
replacement = needle + '''        assertTrue(Config.HUD_SHOW_MASTER != null, "hud master-line toggle exists");\n        assertTrue(Config.HUD_SHOW_MODE != null, "hud mode-line toggle exists");\n        assertTrue(Config.HUD_SHOW_TARGET_NAME != null, "hud target-name toggle exists");\n        assertTrue(Config.HUD_SHOW_TARGET_DISTANCE != null, "hud distance toggle exists");\n        assertTrue(Config.HUD_X_NORMALIZED != null, "hud x position config exists");\n        assertTrue(Config.HUD_Y_NORMALIZED != null, "hud y position config exists");\n'''
if needle not in s:
    raise SystemExit('HUD test marker not found')
s = s.replace(needle, replacement, 1)
test_path.write_text(s, encoding='utf-8')

# Localizations.
for path, values in [
    (root / 'src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json', {
        'crispywaferguntrackermod.config.hud_show_master': '显示自瞄总开关状态',
        'crispywaferguntrackermod.config.hud_show_mode': '显示当前自瞄模式',
        'crispywaferguntrackermod.config.hud_show_target_name': '显示锁定目标名称',
        'crispywaferguntrackermod.config.hud_show_target_distance': '显示锁定目标距离',
        'crispywaferguntrackermod.config.hud_adjust_position': '调整 HUD 位置',
        'crispywaferguntrackermod.config.hud_reset_position': '恢复 HUD 默认位置',
        'crispywaferguntrackermod.hud_position.title': '调整自瞄状态框位置',
        'crispywaferguntrackermod.hud_position.instruction': '按住状态框并拖动；完成保存，Esc 取消',
        'hud.crispywaferguntrackermod.master_state': '自瞄：%s',
        'hud.crispywaferguntrackermod.target': '目标：%s',
        'hud.crispywaferguntrackermod.distance': '距离：%s 方块',
        'hud.crispywaferguntrackermod.no_target': '无'
    }),
    (root / 'src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json', {
        'crispywaferguntrackermod.config.hud_show_master': 'Show Aim Master State',
        'crispywaferguntrackermod.config.hud_show_mode': 'Show Current Aim Mode',
        'crispywaferguntrackermod.config.hud_show_target_name': 'Show Locked Target Name',
        'crispywaferguntrackermod.config.hud_show_target_distance': 'Show Locked Target Distance',
        'crispywaferguntrackermod.config.hud_adjust_position': 'Adjust HUD Position',
        'crispywaferguntrackermod.config.hud_reset_position': 'Reset HUD Position',
        'crispywaferguntrackermod.hud_position.title': 'Adjust Aim Status HUD',
        'crispywaferguntrackermod.hud_position.instruction': 'Drag the status box; Done saves, Esc cancels',
        'hud.crispywaferguntrackermod.master_state': 'Aim: %s',
        'hud.crispywaferguntrackermod.target': 'Target: %s',
        'hud.crispywaferguntrackermod.distance': 'Distance: %s blocks',
        'hud.crispywaferguntrackermod.no_target': 'None'
    })
]:
    data = json.loads(path.read_text(encoding='utf-8'))
    data.update(values)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

print('task5 draggable HUD patch applied')
