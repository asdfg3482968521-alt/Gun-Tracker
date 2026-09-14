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
        HUD("crispywaferguntrackermod.config.page.hud"),
        WEAPON("crispywaferguntrackermod.config.page.weapon");

        private final String translationKey;

        Page(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private final Screen parent;
    private Page page = Page.GENERAL;
    private static final int CAPTURE_NONE = -1;
    private static final int CAPTURE_MASTER = -2;

    private int scrollRows;
    private int capturingSlot = CAPTURE_NONE;

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
                    capturingSlot = CAPTURE_NONE;
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
            case WEAPON -> buildWeaponPage();
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
        addIntSliderRow(0, "crispywaferguntrackermod.config.view_stability",
                Config.VIEW_STABILITY::get, Config.VIEW_STABILITY::set,
                Config.VIEW_STABILITY_MIN, Config.VIEW_STABILITY_MAX, null);

        switch (Config.AIM_BEHAVIOR.get()) {
            case SMOOTH_TRACK -> {
                addDoubleSliderRow(1, "crispywaferguntrackermod.config.smooth_follow_gain",
                        Config.SMOOTH_FOLLOW_GAIN::get, Config.SMOOTH_FOLLOW_GAIN::set,
                        Config.AIM_GAIN_MIN, Config.AIM_GAIN_MAX, 2, null);
                addDoubleSliderRow(2, "crispywaferguntrackermod.config.smooth_max_turn",
                        Config.SMOOTH_MAX_TURN::get, Config.SMOOTH_MAX_TURN::set,
                        Config.AIM_MAX_TURN_MIN, Config.AIM_MAX_TURN_MAX, 1,
                        "crispywaferguntrackermod.unit.degree_per_tick");
            }
            case SNAP -> {
                addDoubleSliderRow(1, "crispywaferguntrackermod.config.snap_follow_gain",
                        Config.SNAP_FOLLOW_GAIN::get, Config.SNAP_FOLLOW_GAIN::set,
                        Config.AIM_GAIN_MIN, Config.AIM_GAIN_MAX, 2, null);
                addDoubleSliderRow(2, "crispywaferguntrackermod.config.snap_max_turn",
                        Config.SNAP_MAX_TURN::get, Config.SNAP_MAX_TURN::set,
                        Config.AIM_MAX_TURN_MIN, Config.AIM_MAX_TURN_MAX, 1,
                        "crispywaferguntrackermod.unit.degree_per_tick");
            }
            case FLICK_RETURN -> {
                addDoubleSliderRow(1, "crispywaferguntrackermod.config.flick_initial_gain",
                        Config.FLICK_INITIAL_GAIN::get, Config.FLICK_INITIAL_GAIN::set,
                        Config.AIM_GAIN_MIN, Config.AIM_GAIN_MAX, 2, null);
                addDoubleSliderRow(2, "crispywaferguntrackermod.config.flick_initial_max_turn",
                        Config.FLICK_INITIAL_MAX_TURN::get, Config.FLICK_INITIAL_MAX_TURN::set,
                        Config.AIM_MAX_TURN_MIN, Config.AIM_MAX_TURN_MAX, 1,
                        "crispywaferguntrackermod.unit.degree_per_tick");
                addDoubleSliderRow(3, "crispywaferguntrackermod.config.flick_track_gain",
                        Config.FLICK_TRACK_GAIN::get, Config.FLICK_TRACK_GAIN::set,
                        Config.AIM_GAIN_MIN, Config.AIM_GAIN_MAX, 2, null);
                addDoubleSliderRow(4, "crispywaferguntrackermod.config.flick_track_max_turn",
                        Config.FLICK_TRACK_MAX_TURN::get, Config.FLICK_TRACK_MAX_TURN::set,
                        Config.AIM_MAX_TURN_MIN, Config.AIM_MAX_TURN_MAX, 1,
                        "crispywaferguntrackermod.unit.degree_per_tick");
                addDoubleSliderRow(5, "crispywaferguntrackermod.config.return_gain",
                        Config.RETURN_GAIN::get, Config.RETURN_GAIN::set,
                        Config.AIM_GAIN_MIN, Config.AIM_GAIN_MAX, 2, null);
                addDoubleSliderRow(6, "crispywaferguntrackermod.config.return_max_turn",
                        Config.RETURN_MAX_TURN::get, Config.RETURN_MAX_TURN::set,
                        Config.AIM_MAX_TURN_MIN, Config.AIM_MAX_TURN_MAX, 1,
                        "crispywaferguntrackermod.unit.degree_per_tick");
            }
        }
    }

    private void buildTargetPage() {
        addDoubleSliderRow(0, "crispywaferguntrackermod.config.max_distance",
                Config.MAX_DISTANCE::get, Config.MAX_DISTANCE::set,
                Config.MAX_DISTANCE_MIN, Config.MAX_DISTANCE_MAX, 0,
                "crispywaferguntrackermod.unit.blocks");
        addDoubleSliderRow(1, "crispywaferguntrackermod.config.fov",
                Config.AIM_FOV_DEGREES::get, this::setSearchFov,
                5.0D, 180.0D, 0, "crispywaferguntrackermod.unit.degree");
        addDoubleSliderRow(2, "crispywaferguntrackermod.config.unlock_fov",
                Config.UNLOCK_FOV_DEGREES::get, this::setUnlockFov,
                Config.UNLOCK_FOV_MIN, Config.UNLOCK_FOV_MAX, 0,
                "crispywaferguntrackermod.unit.degree");
        addToggleRow(3, "crispywaferguntrackermod.config.visible_only",
                Config.VISIBLE_ONLY::get, Config.VISIBLE_ONLY::set);
        addToggleRow(4, "crispywaferguntrackermod.config.target_players",
                Config.TARGET_PLAYERS::get, Config.TARGET_PLAYERS::set);
        addToggleRow(5, "crispywaferguntrackermod.config.target_hostiles",
                Config.TARGET_HOSTILES::get, Config.TARGET_HOSTILES::set);
        addToggleRow(6, "crispywaferguntrackermod.config.target_others",
                Config.TARGET_OTHERS::get, Config.TARGET_OTHERS::set);
        addToggleRow(7, "crispywaferguntrackermod.config.all_targets",
                this::allTargetsEnabled, this::setAllTargets);
        addCycleRow(8, "crispywaferguntrackermod.config.anti_bot_mode", this::antiBotModeValue, () -> {
            Config.ANTI_BOT_MODE.set(Config.ANTI_BOT_MODE.get().next());
            rebuildWidgets();
        });
        addIntSliderRow(9, "crispywaferguntrackermod.config.stickiness",
                Config.STICKINESS::get, Config.STICKINESS::set,
                Config.STICKINESS_MIN, Config.STICKINESS_MAX, null);
        addIntSliderRow(10, "crispywaferguntrackermod.config.switch_confirm_ticks",
                Config.SWITCH_CONFIRM_TICKS::get, Config.SWITCH_CONFIRM_TICKS::set,
                Config.SWITCH_CONFIRM_TICKS_MIN, Config.SWITCH_CONFIRM_TICKS_MAX,
                "crispywaferguntrackermod.unit.ticks");
        addIntSliderRow(11, "crispywaferguntrackermod.config.invisible_tolerance_ticks",
                Config.INVISIBLE_TOLERANCE_TICKS::get, Config.INVISIBLE_TOLERANCE_TICKS::set,
                Config.INVISIBLE_TOLERANCE_TICKS_MIN, Config.INVISIBLE_TOLERANCE_TICKS_MAX,
                "crispywaferguntrackermod.unit.ticks");
        addIntSliderRow(12, "crispywaferguntrackermod.config.candidate_scan_interval",
                Config.CANDIDATE_SCAN_INTERVAL::get, Config.CANDIDATE_SCAN_INTERVAL::set,
                Config.CANDIDATE_SCAN_INTERVAL_MIN, Config.CANDIDATE_SCAN_INTERVAL_MAX,
                "crispywaferguntrackermod.unit.ticks");
        addCycleRow(13, "crispywaferguntrackermod.config.aim_point", this::aimPointValue, () -> {
            Config.AIM_POINT.set(Config.AIM_POINT.get().next());
            rebuildWidgets();
        });
        addDoubleSliderRow(14, "crispywaferguntrackermod.config.custom_aim_height",
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

    private void buildWeaponPage() {
        addToggleRow(0, "crispywaferguntrackermod.config.no_recoil",
                Config.NO_RECOIL::get, Config.NO_RECOIL::set);
        addDoubleSliderRow(1, "crispywaferguntrackermod.config.recoil_cancel_strength",
                Config.RECOIL_CANCEL_STRENGTH::get, Config.RECOIL_CANCEL_STRENGTH::set,
                Config.RECOIL_CANCEL_MIN, Config.RECOIL_CANCEL_MAX, 0, null);
        addToggleRow(2, "crispywaferguntrackermod.config.weapon_feel",
                Config.WEAPON_FEEL::get, Config.WEAPON_FEEL::set);
        addDoubleSliderRow(3, "crispywaferguntrackermod.config.weapon_stability",
                Config.WEAPON_STABILITY::get, Config.WEAPON_STABILITY::set,
                Config.WEAPON_STABILITY_MIN, Config.WEAPON_STABILITY_MAX, 0, null);
        addToggleRow(4, "crispywaferguntrackermod.config.keep_vanilla_bob",
                Config.KEEP_VANILLA_BOB::get, Config.KEEP_VANILLA_BOB::set);
    }

    private void buildKeysPage() {
        addMasterBindingRow(0);
        for (int slot = 0; slot < Keybindings.TRIGGER_SLOT_COUNT; slot++) {
            addTriggerRow(slot + 1, slot);
        }
    }

    private void buildHudPage() {
        addToggleRow(0, "crispywaferguntrackermod.config.show_hud",
                Config.SHOW_HUD::get, Config.SHOW_HUD::set);
        addToggleRow(1, "crispywaferguntrackermod.config.hud_show_master",
                Config.HUD_SHOW_MASTER::get, Config.HUD_SHOW_MASTER::set);
        addToggleRow(2, "crispywaferguntrackermod.config.hud_show_mode",
                Config.HUD_SHOW_MODE::get, Config.HUD_SHOW_MODE::set);
        addToggleRow(3, "crispywaferguntrackermod.config.hud_show_target_name",
                Config.HUD_SHOW_TARGET_NAME::get, Config.HUD_SHOW_TARGET_NAME::set);
        addToggleRow(4, "crispywaferguntrackermod.config.hud_show_target_distance",
                Config.HUD_SHOW_TARGET_DISTANCE::get, Config.HUD_SHOW_TARGET_DISTANCE::set);
        addToggleRow(5, "crispywaferguntrackermod.config.show_fov_ring",
                Config.SHOW_FOV_RING::get, Config.SHOW_FOV_RING::set);
        addToggleRow(6, "crispywaferguntrackermod.config.show_ballistics_hud",
                Config.SHOW_BALLISTICS_HUD::get, Config.SHOW_BALLISTICS_HUD::set);
        if (isRowVisible(7)) {
            addRenderableWidget(Button.builder(Component.translatable("crispywaferguntrackermod.config.hud_adjust_position"), button -> {
                Config.CLIENT_SPEC.save();
                if (minecraft != null) minecraft.setScreen(new HudPositionScreen(this));
            }).bounds(contentLeft, rowY(7), contentWidth(), 20).build());
        }
        if (isRowVisible(8)) {
            addRenderableWidget(Button.builder(Component.translatable("crispywaferguntrackermod.config.hud_reset_position"), button -> {
                Config.HUD_X_NORMALIZED.set(0.50D);
                Config.HUD_Y_NORMALIZED.set(0.62D);
                Config.CLIENT_SPEC.save();
            }).bounds(contentLeft, rowY(8), contentWidth(), 20).build());
        }
    }

    private void addMasterBindingRow(int row) {
        if (!isRowVisible(row)) return;
        addRenderableWidget(Button.builder(masterBindingMessage(), button -> {
            capturingSlot = CAPTURE_MASTER;
            rebuildWidgets();
        }).bounds(contentLeft, rowY(row), contentWidth(), 20).build());
    }

    private Component masterBindingMessage() {
        Component name = Component.translatable("crispywaferguntrackermod.config.master_toggle_binding");
        if (capturingSlot == CAPTURE_MASTER) {
            return name.copy().append(": ").append(Component.translatable("crispywaferguntrackermod.config.press_any_key"));
        }
        KeyMapping mapping = Keybindings.masterToggleKey;
        Component bound = mapping == null || mapping.isUnbound()
                ? Component.translatable("crispywaferguntrackermod.config.unbound")
                : mapping.getTranslatedKeyMessage();
        Component result = name.copy().append(": ").append(bound);
        if (hasMasterDuplicateBinding()) result = result.copy().append(" ⚠");
        return result;
    }

    private boolean hasMasterDuplicateBinding() {
        KeyMapping master = Keybindings.masterToggleKey;
        if (master == null || master.isUnbound()) return false;
        for (int i = 0; i < Keybindings.TRIGGER_SLOT_COUNT; i++) {
            KeyMapping trigger = Keybindings.triggerKey(i);
            if (trigger != null && !trigger.isUnbound() && master.getKey().equals(trigger.getKey())) return true;
        }
        return false;
    }

    private boolean allTargetsEnabled() {
        return Config.TARGET_PLAYERS.get() && Config.TARGET_HOSTILES.get() && Config.TARGET_OTHERS.get();
    }

    private void setAllTargets(boolean enabled) {
        Config.TARGET_PLAYERS.set(enabled);
        Config.TARGET_HOSTILES.set(enabled);
        Config.TARGET_OTHERS.set(enabled);
    }

    private void setSearchFov(double value) {
        Config.AIM_FOV_DEGREES.set(value);
        if (Config.UNLOCK_FOV_DEGREES.get() < value) Config.UNLOCK_FOV_DEGREES.set(value);
    }

    private void setUnlockFov(double value) {
        Config.UNLOCK_FOV_DEGREES.set(Math.max(value, Config.AIM_FOV_DEGREES.get()));
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
        KeyMapping master = Keybindings.masterToggleKey;
        if (master != null && !master.isUnbound() && current.getKey().equals(master.getKey())) return true;
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

    private Component antiBotModeValue() {
        String name = Config.ANTI_BOT_MODE.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.anti_bot_mode." + name);
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
            case AIM -> switch (Config.AIM_BEHAVIOR.get()) {
                case SMOOTH_TRACK, SNAP -> 3;
                case FLICK_RETURN -> 7;
            };
            case TARGET -> 15;
            case BALLISTICS -> 16;
            case KEYS -> 5;
            case HUD -> 9;
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
        if (capturingSlot != CAPTURE_NONE) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                setCapturedBinding(InputConstants.UNKNOWN);
                capturingSlot = CAPTURE_NONE;
                rebuildWidgets();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                capturingSlot = CAPTURE_NONE;
                rebuildWidgets();
                return true;
            }
            setCapturedBinding(InputConstants.getKey(keyCode, scanCode));
            capturingSlot = CAPTURE_NONE;
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (capturingSlot != CAPTURE_NONE) {
            setCapturedBinding(InputConstants.Type.MOUSE.getOrCreate(button));
            capturingSlot = CAPTURE_NONE;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setCapturedBinding(InputConstants.Key key) {
        if (capturingSlot == CAPTURE_MASTER) {
            setKeyMappingBinding(Keybindings.masterToggleKey, key);
        } else if (capturingSlot >= 0) {
            setTriggerBinding(capturingSlot, key);
        }
    }

    private void setTriggerBinding(int slot, InputConstants.Key key) {
        setKeyMappingBinding(Keybindings.triggerKey(slot), key);
    }

    private void setKeyMappingBinding(KeyMapping mapping, InputConstants.Key key) {
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
