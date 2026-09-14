package com.crispywafer.crispywaferguntracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Transparent, in-game configuration panel for Gun Tracker. */
public final class GunTrackerConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int NAV_WIDTH = 106;

    private enum Page {
        GENERAL("crispywaferguntrackermod.config.page.general"),
        AIM("crispywaferguntrackermod.config.page.aim"),
        TARGET("crispywaferguntrackermod.config.page.target"),
        BALLISTICS("crispywaferguntrackermod.config.page.ballistics"),
        KEYS("crispywaferguntrackermod.config.page.keys"),
        HUD("crispywaferguntrackermod.config.page.hud");

        private final String translationKey;

        Page(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final Screen parent;
    private Page page = Page.GENERAL;
    private int scrollRows;
    private int capturingSlot = -1;

    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int visibleRows;

    public GunTrackerConfigScreen(Screen parent) {
        super(Component.translatable("crispywaferguntrackermod.config.title"));
        this.parent = parent;
    }

    static double sliderToValue(double normalized, double min, double max) {
        double clamped = Math.max(0.0D, Math.min(1.0D, normalized));
        return min + clamped * (max - min);
    }

    static double valueToSlider(double value, double min, double max) {
        if (max <= min) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
    }

    @Override
    protected void init() {
        computeLayout();
        rebuildWidgets();
    }

    private void computeLayout() {
        int margin = 14;
        panelLeft = Math.max(8, margin);
        panelTop = Math.max(8, margin);
        panelRight = Math.max(panelLeft + 280, width - margin);
        panelBottom = Math.max(panelTop + 210, height - margin);
        contentLeft = panelLeft + NAV_WIDTH + 16;
        contentRight = panelRight - 12;
        contentTop = panelTop + 38;
        contentBottom = panelBottom - 42;
        visibleRows = Math.max(3, (contentBottom - contentTop) / ROW_HEIGHT);
        clampScroll();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        computeLayout();

        int navX = panelLeft + 8;
        int navY = panelTop + 38;
        int navButtonWidth = NAV_WIDTH - 16;
        for (Page candidate : Page.values()) {
            addRenderableWidget(Button.builder(Component.translatable(candidate.translationKey), button -> {
                if (page != candidate) {
                    page = candidate;
                    scrollRows = 0;
                    capturingSlot = -1;
                    rebuildWidgets();
                }
            }).bounds(navX, navY, navButtonWidth, 20).build());
            navY += 23;
        }

        switch (page) {
            case GENERAL -> buildGeneralPage();
            case AIM -> buildAimPage();
            case TARGET -> buildTargetPage();
            case BALLISTICS -> buildBallisticsPage();
            case KEYS -> buildKeysPage();
            case HUD -> buildHudPage();
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(Math.max(contentLeft, panelRight - 112), panelBottom - 30, 100, 20)
                .build());
    }

    private void buildGeneralPage() {
        addToggleRow(0, "crispywaferguntrackermod.config.master_enabled",
                () -> Config.MASTER_ENABLED.get(), Config.MASTER_ENABLED::set);
        addCycleRow(1, "crispywaferguntrackermod.config.aim_behavior",
                this::aimBehaviorValue, () -> {
                    Config.AIM_BEHAVIOR.set(Config.AIM_BEHAVIOR.get().next());
                    rebuildWidgets();
                });
        addIntSliderRow(2, "crispywaferguntrackermod.config.long_press_ms",
                Config.LONG_PRESS_MS::get, Config.LONG_PRESS_MS::set,
                Config.LONG_PRESS_MS_MIN, Config.LONG_PRESS_MS_MAX,
                "crispywaferguntrackermod.unit.ms");
    }

    private void buildAimPage() {
        addDoubleSliderRow(0, "crispywaferguntrackermod.config.continuous_speed",
                Config.CONTINUOUS_SPEED::get, Config.CONTINUOUS_SPEED::set, 0.05D, 1.0D, 2, null);
        addDoubleSliderRow(1, "crispywaferguntrackermod.config.flick_speed",
                Config.FLICK_SPEED::get, Config.FLICK_SPEED::set, 0.05D, 1.0D, 2, null);
        addDoubleSliderRow(2, "crispywaferguntrackermod.config.flick_return_speed",
                Config.FLICK_RETURN_SPEED::get, Config.FLICK_RETURN_SPEED::set, 0.05D, 1.0D, 2, null);
        addIntSliderRow(3, "crispywaferguntrackermod.config.aim_substeps",
                Config.AIM_SUBSTEPS::get, Config.AIM_SUBSTEPS::set,
                Config.AIM_SUBSTEPS_MIN, Config.AIM_SUBSTEPS_MAX, null);
        addDoubleSliderRow(4, "crispywaferguntrackermod.config.fov",
                Config.AIM_FOV_DEGREES::get, Config.AIM_FOV_DEGREES::set, 5.0D, 180.0D, 0,
                "crispywaferguntrackermod.unit.degree");
    }

    private void buildTargetPage() {
        addDoubleSliderRow(0, "crispywaferguntrackermod.config.max_distance",
                Config.MAX_DISTANCE::get, Config.MAX_DISTANCE::set,
                Config.MAX_DISTANCE_MIN, Config.MAX_DISTANCE_MAX, 0,
                "crispywaferguntrackermod.unit.blocks");
        addToggleRow(1, "crispywaferguntrackermod.config.visible_only",
                Config.VISIBLE_ONLY::get, Config.VISIBLE_ONLY::set);
        addToggleRow(2, "crispywaferguntrackermod.config.sticky_target",
                Config.STICKY_TARGET::get, Config.STICKY_TARGET::set);
        addDoubleSliderRow(3, "crispywaferguntrackermod.config.switch_hysteresis",
                Config.SWITCH_HYSTERESIS::get, Config.SWITCH_HYSTERESIS::set, 0.0D, 50.0D, 0, null);
        addCycleRow(4, "crispywaferguntrackermod.config.target_mode", this::targetModeValue, () -> {
            Config.TARGET_MODE.set(Config.TARGET_MODE.get().next());
            rebuildWidgets();
        });
        addCycleRow(5, "crispywaferguntrackermod.config.aim_point", this::aimPointValue, () -> {
            Config.AIM_POINT.set(Config.AIM_POINT.get().next());
            rebuildWidgets();
        });
        addDoubleSliderRow(6, "crispywaferguntrackermod.config.custom_aim_height",
                Config.CUSTOM_AIM_HEIGHT::get, Config.CUSTOM_AIM_HEIGHT::set, 0.0D, 1.20D, 2, null);
    }

    private void buildBallisticsPage() {
        addToggleRow(0, "crispywaferguntrackermod.config.lead_enabled",
                Config.LEAD_ENABLED::get, Config.LEAD_ENABLED::set);
        addDoubleSliderRow(1, "crispywaferguntrackermod.config.projectile_speed",
                Config.PROJECTILE_SPEED::get, Config.PROJECTILE_SPEED::set,
                Config.PROJECTILE_SPEED_MIN, Config.PROJECTILE_SPEED_MAX, 1,
                "crispywaferguntrackermod.unit.blocks_per_tick");
        addDoubleSliderRow(2, "crispywaferguntrackermod.config.projectile_friction",
                Config.PROJECTILE_FRICTION::get, Config.PROJECTILE_FRICTION::set, 0.0D, 0.30D, 3, null);
        addDoubleSliderRow(3, "crispywaferguntrackermod.config.max_lead_ticks",
                Config.MAX_LEAD_TICKS::get, Config.MAX_LEAD_TICKS::set,
                Config.MAX_LEAD_TICKS_MIN, Config.MAX_LEAD_TICKS_MAX, 0,
                "crispywaferguntrackermod.unit.ticks");
        addToggleRow(4, "crispywaferguntrackermod.config.gravity_compensation",
                Config.GRAVITY_COMPENSATION::get, Config.GRAVITY_COMPENSATION::set);
        addDoubleSliderRow(5, "crispywaferguntrackermod.config.projectile_gravity",
                Config.PROJECTILE_GRAVITY::get, Config.PROJECTILE_GRAVITY::set, 0.0D, 0.20D, 3, null);
        addToggleRow(6, "crispywaferguntrackermod.config.advanced_ballistics",
                Config.ADVANCED_BALLISTICS::get, Config.ADVANCED_BALLISTICS::set);
        addToggleRow(7, "crispywaferguntrackermod.config.tacz_auto_ballistics",
                Config.TACZ_AUTO_BALLISTICS::get, Config.TACZ_AUTO_BALLISTICS::set);
        addToggleRow(8, "crispywaferguntrackermod.config.tacz_live_calibration",
                Config.TACZ_LIVE_CALIBRATION::get, Config.TACZ_LIVE_CALIBRATION::set);
        addToggleRow(9, "crispywaferguntrackermod.config.use_target_acceleration",
                Config.USE_TARGET_ACCELERATION::get, Config.USE_TARGET_ACCELERATION::set);
        addDoubleSliderRow(10, "crispywaferguntrackermod.config.velocity_smoothing",
                Config.TARGET_VELOCITY_SMOOTHING::get, Config.TARGET_VELOCITY_SMOOTHING::set,
                0.05D, 1.0D, 2, null);
        addDoubleSliderRow(11, "crispywaferguntrackermod.config.acceleration_smoothing",
                Config.TARGET_ACCELERATION_SMOOTHING::get, Config.TARGET_ACCELERATION_SMOOTHING::set,
                0.05D, 1.0D, 2, null);
        addDoubleSliderRow(12, "crispywaferguntrackermod.config.max_target_acceleration",
                Config.MAX_TARGET_ACCELERATION::get, Config.MAX_TARGET_ACCELERATION::set,
                Config.MAX_TARGET_ACCELERATION_MIN, Config.MAX_TARGET_ACCELERATION_MAX, 2, null);
        addDoubleSliderRow(13, "crispywaferguntrackermod.config.max_tracked_target_speed",
                Config.MAX_TRACKED_TARGET_SPEED::get, Config.MAX_TRACKED_TARGET_SPEED::set,
                Config.MAX_TRACKED_TARGET_SPEED_MIN, Config.MAX_TRACKED_TARGET_SPEED_MAX, 1,
                "crispywaferguntrackermod.unit.blocks_per_tick");
        addToggleRow(14, "crispywaferguntrackermod.config.inherit_shooter_velocity",
                Config.INHERIT_SHOOTER_VELOCITY::get, Config.INHERIT_SHOOTER_VELOCITY::set);
        addIntSliderRow(15, "crispywaferguntrackermod.config.locked_rescan_interval",
                Config.LOCKED_RESCAN_INTERVAL::get, Config.LOCKED_RESCAN_INTERVAL::set,
                1, 10, "crispywaferguntrackermod.unit.ticks");
    }

    private void buildKeysPage() {
        for (int slot = 0; slot < Keybindings.TRIGGER_SLOT_COUNT; slot++) {
            addTriggerRow(slot, slot);
        }
    }

    private void buildHudPage() {
        addToggleRow(0, "crispywaferguntrackermod.config.show_hud",
                Config.SHOW_HUD::get, Config.SHOW_HUD::set);
        addToggleRow(1, "crispywaferguntrackermod.config.show_fov_ring",
                Config.SHOW_FOV_RING::get, Config.SHOW_FOV_RING::set);
        addToggleRow(2, "crispywaferguntrackermod.config.show_ballistics_hud",
                Config.SHOW_BALLISTICS_HUD::get, Config.SHOW_BALLISTICS_HUD::set);
    }

    private void addToggleRow(int row, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        if (!isRowVisible(row)) return;
        addRenderableWidget(Button.builder(toggleMessage(labelKey, getter.getAsBoolean()), button -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            button.setMessage(toggleMessage(labelKey, next));
        }).bounds(contentLeft, rowY(row), contentWidth(), 20).build());
    }

    private void addCycleRow(int row, String labelKey, java.util.function.Supplier<Component> value, Runnable cycle) {
        if (!isRowVisible(row)) return;
        addRenderableWidget(Button.builder(labeledValue(labelKey, value.get()), button -> cycle.run())
                .bounds(contentLeft, rowY(row), contentWidth(), 20)
                .build());
    }

    private void addDoubleSliderRow(
            int row,
            String labelKey,
            DoubleSupplier getter,
            DoubleConsumer setter,
            double min,
            double max,
            int decimals,
            String suffixKey
    ) {
        if (!isRowVisible(row)) return;
        addRenderableWidget(new RangeSlider(contentLeft, rowY(row), contentWidth(), 20,
                labelKey, getter.getAsDouble(), min, max, decimals, suffixKey, setter));
    }

    private void addIntSliderRow(
            int row,
            String labelKey,
            IntSupplier getter,
            IntConsumer setter,
            int min,
            int max,
            String suffixKey
    ) {
        if (!isRowVisible(row)) return;
        addRenderableWidget(new IntRangeSlider(contentLeft, rowY(row), contentWidth(), 20,
                labelKey, getter.getAsInt(), min, max, suffixKey, setter));
    }

    private void addTriggerRow(int row, int slot) {
        if (!isRowVisible(row)) return;
        int gap = 6;
        int modeWidth = Math.max(106, contentWidth() / 3);
        int keyWidth = Math.max(120, contentWidth() - modeWidth - gap);
        int y = rowY(row);

        addRenderableWidget(Button.builder(triggerBindingMessage(slot), button -> {
            capturingSlot = slot;
            rebuildWidgets();
        }).bounds(contentLeft, y, keyWidth, 20).build());

        addRenderableWidget(Button.builder(triggerModeMessage(slot), button -> {
            setTriggerMode(slot, getTriggerMode(slot).next());
            rebuildWidgets();
        }).bounds(contentLeft + keyWidth + gap, y, modeWidth, 20).build());
    }

    private Component triggerBindingMessage(int slot) {
        Component name = Component.translatable("crispywaferguntrackermod.config.trigger_slot", slot + 1);
        if (capturingSlot == slot) {
            return name.copy().append(": ").append(Component.translatable("crispywaferguntrackermod.config.press_any_key"));
        }
        KeyMapping mapping = Keybindings.triggerKey(slot);
        Component bound = mapping == null || mapping.isUnbound()
                ? Component.translatable("crispywaferguntrackermod.config.unbound")
                : mapping.getTranslatedKeyMessage();
        Component result = name.copy().append(": ").append(bound);
        if (hasDuplicateBinding(slot)) {
            result = result.copy().append(" ⚠");
        }
        return result;
    }

    private Component triggerModeMessage(int slot) {
        return Component.translatable("crispywaferguntrackermod.config.trigger_mode")
                .append(": ")
                .append(triggerModeValue(getTriggerMode(slot)));
    }

    private boolean hasDuplicateBinding(int slot) {
        KeyMapping current = Keybindings.triggerKey(slot);
        if (current == null || current.isUnbound()) return false;
        for (int i = 0; i < Keybindings.TRIGGER_SLOT_COUNT; i++) {
            if (i == slot) continue;
            KeyMapping other = Keybindings.triggerKey(i);
            if (other != null && !other.isUnbound() && current.getKey().equals(other.getKey())) return true;
        }
        return false;
    }

    private AimActivationController.TriggerMode getTriggerMode(int slot) {
        return switch (slot) {
            case 0 -> Config.TRIGGER_MODE_1.get();
            case 1 -> Config.TRIGGER_MODE_2.get();
            case 2 -> Config.TRIGGER_MODE_3.get();
            case 3 -> Config.TRIGGER_MODE_4.get();
            default -> throw new IndexOutOfBoundsException("trigger slot " + slot);
        };
    }

    private void setTriggerMode(int slot, AimActivationController.TriggerMode mode) {
        switch (slot) {
            case 0 -> Config.TRIGGER_MODE_1.set(mode);
            case 1 -> Config.TRIGGER_MODE_2.set(mode);
            case 2 -> Config.TRIGGER_MODE_3.set(mode);
            case 3 -> Config.TRIGGER_MODE_4.set(mode);
            default -> throw new IndexOutOfBoundsException("trigger slot " + slot);
        }
    }

    private Component aimBehaviorValue() {
        String name = Config.AIM_BEHAVIOR.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.aim_behavior." + name);
    }

    private Component targetModeValue() {
        String name = Config.TARGET_MODE.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.target_mode." + name);
    }

    private Component aimPointValue() {
        String name = Config.AIM_POINT.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.aim_point." + name);
    }

    private Component triggerModeValue(AimActivationController.TriggerMode mode) {
        String name = mode.name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.trigger_mode." + name);
    }

    private Component toggleMessage(String labelKey, boolean value) {
        return Component.translatable(labelKey)
                .append(": ")
                .append(Component.translatable(value
                        ? "crispywaferguntrackermod.config.on"
                        : "crispywaferguntrackermod.config.off"));
    }

    private Component labeledValue(String labelKey, Component value) {
        return Component.translatable(labelKey).append(": ").append(value);
    }

    private boolean isRowVisible(int row) {
        return row >= scrollRows && row < scrollRows + visibleRows;
    }

    private int rowY(int row) {
        return contentTop + (row - scrollRows) * ROW_HEIGHT;
    }

    private int contentWidth() {
        return Math.max(120, contentRight - contentLeft);
    }

    private int totalRows() {
        return switch (page) {
            case GENERAL -> 3;
            case AIM -> 5;
            case TARGET -> 7;
            case BALLISTICS -> 16;
            case KEYS -> 4;
            case HUD -> 3;
        };
    }

    private void clampScroll() {
        int max = Math.max(0, totalRows() - visibleRows);
        scrollRows = Math.max(0, Math.min(max, scrollRows));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
            int old = scrollRows;
            scrollRows += delta < 0.0D ? 1 : -1;
            clampScroll();
            if (old != scrollRows) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (capturingSlot >= 0) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                setTriggerBinding(capturingSlot, InputConstants.UNKNOWN);
                capturingSlot = -1;
                rebuildWidgets();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingSlot = -1;
                rebuildWidgets();
                return true;
            }
            setTriggerBinding(capturingSlot, InputConstants.getKey(keyCode, scanCode));
            capturingSlot = -1;
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_ALT) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (capturingSlot >= 0) {
            setTriggerBinding(capturingSlot, InputConstants.Type.MOUSE.getOrCreate(button));
            capturingSlot = -1;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setTriggerBinding(int slot, InputConstants.Key key) {
        KeyMapping mapping = Keybindings.triggerKey(slot);
        if (mapping == null) return;
        mapping.setKey(key);
        KeyMapping.resetMapping();
        if (minecraft != null) minecraft.options.save();
    }

    private void saveAndClose() {
        Config.CLIENT_SPEC.save();
        if (minecraft != null) {
            minecraft.options.save();
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x44000000);
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB5101010);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 28, 0xD0202020);
        graphics.fill(panelLeft + NAV_WIDTH, panelTop + 30, panelLeft + NAV_WIDTH + 1, panelBottom - 8, 0x66888888);
        graphics.drawCenteredString(font, title, (panelLeft + panelRight) / 2, panelTop + 9, 0xFFFFFF);

        if (page == Page.KEYS) {
            graphics.drawString(font,
                    Component.translatable("crispywaferguntrackermod.config.key_capture_help"),
                    contentLeft, panelBottom - 39, 0xBFBFBF, false);
        }
        if (totalRows() > visibleRows) {
            graphics.drawString(font,
                    Component.translatable("crispywaferguntrackermod.config.scroll_hint"),
                    contentRight - 92, panelTop + 12, 0xBFBFBF, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static final class RangeSlider extends AbstractSliderButton {
        private final String labelKey;
        private final double min;
        private final double max;
        private final int decimals;
        private final String suffixKey;
        private final DoubleConsumer setter;

        private RangeSlider(int x, int y, int width, int height, String labelKey, double current,
                            double min, double max, int decimals, String suffixKey, DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), valueToSlider(current, min, max));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.suffixKey = suffixKey;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double actual = sliderToValue(value, min, max);
            String format = "%." + decimals + "f";
            Component message = Component.translatable(labelKey)
                    .append(": ")
                    .append(Component.literal(String.format(Locale.ROOT, format, actual)));
            if (suffixKey != null) message = message.copy().append(" ").append(Component.translatable(suffixKey));
            setMessage(message);
        }

        @Override
        protected void applyValue() {
            setter.accept(sliderToValue(value, min, max));
        }
    }

    private static final class IntRangeSlider extends AbstractSliderButton {
        private final String labelKey;
        private final int min;
        private final int max;
        private final String suffixKey;
        private final IntConsumer setter;

        private IntRangeSlider(int x, int y, int width, int height, String labelKey, int current,
                               int min, int max, String suffixKey, IntConsumer setter) {
            super(x, y, width, height, Component.empty(), valueToSlider(current, min, max));
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.suffixKey = suffixKey;
            this.setter = setter;
            updateMessage();
        }

        private int actual() {
            return (int) Math.round(sliderToValue(value, min, max));
        }

        @Override
        protected void updateMessage() {
            Component message = Component.translatable(labelKey)
                    .append(": ")
                    .append(Component.literal(Integer.toString(actual())));
            if (suffixKey != null) message = message.copy().append(" ").append(Component.translatable(suffixKey));
            setMessage(message);
        }

        @Override
        protected void applyValue() {
            setter.accept(actual());
        }
    }
}
