package com.crispywafer.crispywaferguntracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.Locale;

public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final double MAX_DISTANCE_MIN = 4.0D;
    public static final double MAX_DISTANCE_MAX = 300.0D;
    public static final int AIM_SUBSTEPS_MIN = 1;
    public static final int AIM_SUBSTEPS_MAX = 32;
    public static final double MAX_LEAD_TICKS_MIN = 1.0D;
    public static final double MAX_LEAD_TICKS_MAX = 200.0D;
    public static final double PROJECTILE_SPEED_MIN = 0.1D;
    public static final double PROJECTILE_SPEED_MAX = 200.0D;
    public static final double MAX_TRACKED_TARGET_SPEED_MIN = 0.5D;
    public static final double MAX_TRACKED_TARGET_SPEED_MAX = 50.0D;
    public static final double MAX_TARGET_ACCELERATION_MIN = 0.0D;
    public static final double MAX_TARGET_ACCELERATION_MAX = 10.0D;
    public static final int LONG_PRESS_MS_MIN = 50;
    public static final int LONG_PRESS_MS_MAX = 2000;

    public enum AimBehavior {
        SMOOTH_TRACK,
        SNAP,
        FLICK_RETURN;

        public AimBehavior next() {
            AimBehavior[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum TargetMode {
        HOSTILE_ONLY,
        MOBS_ONLY,
        ALL_LIVING;

        public TargetMode next() {
            TargetMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum AimPoint {
        HEAD,
        CHEST,
        CENTER,
        CUSTOM;

        public AimPoint next() {
            AimPoint[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static final ForgeConfigSpec.BooleanValue MASTER_ENABLED;
    public static final ForgeConfigSpec.EnumValue<AimBehavior> AIM_BEHAVIOR;
    public static final ForgeConfigSpec.IntValue LONG_PRESS_MS;
    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_1;
    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_2;
    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_3;
    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_4;
    public static final ForgeConfigSpec.DoubleValue CONTINUOUS_SPEED;
    public static final ForgeConfigSpec.DoubleValue FLICK_SPEED;
    public static final ForgeConfigSpec.DoubleValue FLICK_RETURN_SPEED;
    public static final ForgeConfigSpec.IntValue AIM_SUBSTEPS;
    public static final ForgeConfigSpec.DoubleValue AIM_FOV_DEGREES;
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue VISIBLE_ONLY;
    public static final ForgeConfigSpec.BooleanValue INSTANT_CONTINUOUS;
    public static final ForgeConfigSpec.BooleanValue STICKY_TARGET;
    public static final ForgeConfigSpec.DoubleValue SWITCH_HYSTERESIS;
    public static final ForgeConfigSpec.EnumValue<TargetMode> TARGET_MODE;
    public static final ForgeConfigSpec.EnumValue<AimPoint> AIM_POINT;
    public static final ForgeConfigSpec.DoubleValue CUSTOM_AIM_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue LEAD_ENABLED;
    public static final ForgeConfigSpec.DoubleValue PROJECTILE_SPEED;
    public static final ForgeConfigSpec.DoubleValue PROJECTILE_FRICTION;
    public static final ForgeConfigSpec.DoubleValue MAX_LEAD_TICKS;
    public static final ForgeConfigSpec.BooleanValue GRAVITY_COMPENSATION;
    public static final ForgeConfigSpec.DoubleValue PROJECTILE_GRAVITY;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    public static final ForgeConfigSpec.BooleanValue SHOW_FOV_RING;
    public static final ForgeConfigSpec.BooleanValue ADVANCED_BALLISTICS;
    public static final ForgeConfigSpec.BooleanValue TACZ_AUTO_BALLISTICS;
    public static final ForgeConfigSpec.BooleanValue TACZ_LIVE_CALIBRATION;
    public static final ForgeConfigSpec.BooleanValue USE_TARGET_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue TARGET_VELOCITY_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue TARGET_ACCELERATION_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue MAX_TARGET_ACCELERATION;
    public static final ForgeConfigSpec.DoubleValue MAX_TRACKED_TARGET_SPEED;
    public static final ForgeConfigSpec.BooleanValue INHERIT_SHOOTER_VELOCITY;
    public static final ForgeConfigSpec.IntValue LOCKED_RESCAN_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue SHOW_BALLISTICS_HUD;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        BUILDER.push("aim_settings");
        MASTER_ENABLED = BUILDER.comment("Global master switch for all aim activation.")
                .define("master_enabled", true);
        AIM_BEHAVIOR = BUILDER.comment("Aim behavior shared by all trigger slots.")
                .defineEnum("aim_behavior", AimBehavior.SMOOTH_TRACK);
        LONG_PRESS_MS = BUILDER.comment("Long-hold activation threshold in milliseconds.")
                .defineInRange("long_press_ms", 200, LONG_PRESS_MS_MIN, LONG_PRESS_MS_MAX);
        TRIGGER_MODE_1 = BUILDER.comment("Activation mode for trigger slot 1.")
                .defineEnum("trigger_mode_1", AimActivationController.TriggerMode.HOLD);
        TRIGGER_MODE_2 = BUILDER.comment("Activation mode for trigger slot 2.")
                .defineEnum("trigger_mode_2", AimActivationController.TriggerMode.TOGGLE);
        TRIGGER_MODE_3 = BUILDER.comment("Activation mode for trigger slot 3.")
                .defineEnum("trigger_mode_3", AimActivationController.TriggerMode.HOLD);
        TRIGGER_MODE_4 = BUILDER.comment("Activation mode for trigger slot 4.")
                .defineEnum("trigger_mode_4", AimActivationController.TriggerMode.HOLD);
        CONTINUOUS_SPEED = BUILDER.comment("Continuous tracking speed. 1.0 is maximum.")
                .defineInRange("continuous_speed", 0.90D, 0.05D, 1.0D);
        FLICK_SPEED = BUILDER.comment("Flick speed. 1.0 snaps immediately.")
                .defineInRange("flick_speed", 1.0D, 0.05D, 1.0D);
        FLICK_RETURN_SPEED = BUILDER.comment("Return speed after releasing the flick key.")
                .defineInRange("flick_return_speed", 0.65D, 0.05D, 1.0D);
        AIM_SUBSTEPS = BUILDER.comment("Smoothing substeps for non-instant tracking.")
                .defineInRange("aim_substeps", 4, AIM_SUBSTEPS_MIN, AIM_SUBSTEPS_MAX);
        AIM_FOV_DEGREES = BUILDER.comment("Maximum angular error from the crosshair in degrees.")
                .defineInRange("aim_fov_degrees", 70.0D, 5.0D, 180.0D);
        MAX_DISTANCE = BUILDER.comment("Maximum target search distance in blocks.")
                .defineInRange("max_distance", 96.0D, MAX_DISTANCE_MIN, MAX_DISTANCE_MAX);
        VISIBLE_ONLY = BUILDER.comment("Only lock targets with direct line of sight.")
                .define("visible_only", true);
        INSTANT_CONTINUOUS = BUILDER.comment("Continuous aim snaps directly to the selected target.")
                .define("instant_continuous", true);
        STICKY_TARGET = BUILDER.comment("Keep current target unless a meaningfully better candidate appears.")
                .define("sticky_target", true);
        SWITCH_HYSTERESIS = BUILDER.comment("How much better a new target must score before switching.")
                .defineInRange("switch_hysteresis", 10.0D, 0.0D, 50.0D);
        TARGET_MODE = BUILDER.comment("Which living entities are valid targets.")
                .defineEnum("target_mode", TargetMode.HOSTILE_ONLY);
        AIM_POINT = BUILDER.comment("Vertical point to aim at.")
                .defineEnum("aim_point", AimPoint.HEAD);
        CUSTOM_AIM_HEIGHT = BUILDER.comment("Custom aim height as a fraction of entity height.")
                .defineInRange("custom_aim_height", 0.90D, 0.0D, 1.20D);
        LEAD_ENABLED = BUILDER.comment("Predict moving targets using configured projectile speed.")
                .define("lead_enabled", true);
        PROJECTILE_SPEED = BUILDER.comment("Manual fallback projectile speed in blocks per tick.")
                .defineInRange("projectile_speed", 8.0D, PROJECTILE_SPEED_MIN, PROJECTILE_SPEED_MAX);
        PROJECTILE_FRICTION = BUILDER.comment("Manual fallback TACZ-style friction per tick.")
                .defineInRange("projectile_friction", 0.01D, 0.0D, 0.30D);
        MAX_LEAD_TICKS = BUILDER.comment("Maximum prediction window in game ticks.")
                .defineInRange("max_lead_ticks", 30.0D, MAX_LEAD_TICKS_MIN, MAX_LEAD_TICKS_MAX);
        GRAVITY_COMPENSATION = BUILDER.comment("Raise aim point to compensate for projectile drop.")
                .define("gravity_compensation", false);
        PROJECTILE_GRAVITY = BUILDER.comment("Projectile downward acceleration in blocks/tick^2.")
                .defineInRange("projectile_gravity", 0.05D, 0.0D, 0.20D);
        SHOW_HUD = BUILDER.comment("Show current lock status and target near the crosshair.")
                .define("show_hud", true);
        SHOW_FOV_RING = BUILDER.comment("Draw an approximate FOV ring around the crosshair.")
                .define("show_fov_ring", true);
        ADVANCED_BALLISTICS = BUILDER.comment("Use iterative TACZ-style drag/gravity intercept solving.")
                .define("advanced_ballistics", true);
        TACZ_AUTO_BALLISTICS = BUILDER.comment("Automatically read held TACZ gun ballistic data when TACZ is installed.")
                .define("tacz_auto_ballistics", true);
        TACZ_LIVE_CALIBRATION = BUILDER.comment("Calibrate per-gun speed/gravity/friction from freshly fired TACZ bullets.")
                .define("tacz_live_calibration", true);
        USE_TARGET_ACCELERATION = BUILDER.comment("Use filtered target acceleration in the intercept prediction.")
                .define("use_target_acceleration", true);
        TARGET_VELOCITY_SMOOTHING = BUILDER.comment("EWMA strength for target velocity. Higher follows changes faster.")
                .defineInRange("target_velocity_smoothing", 0.55D, 0.05D, 1.0D);
        TARGET_ACCELERATION_SMOOTHING = BUILDER.comment("EWMA strength for target acceleration.")
                .defineInRange("target_acceleration_smoothing", 0.30D, 0.05D, 1.0D);
        MAX_TARGET_ACCELERATION = BUILDER.comment("Clamp for predicted target acceleration in blocks/tick^2.")
                .defineInRange("max_target_acceleration", 0.35D, MAX_TARGET_ACCELERATION_MIN, MAX_TARGET_ACCELERATION_MAX);
        MAX_TRACKED_TARGET_SPEED = BUILDER.comment("Clamp for tracked target speed to reject teleports/spikes.")
                .defineInRange("max_tracked_target_speed", 4.0D, MAX_TRACKED_TARGET_SPEED_MIN, MAX_TRACKED_TARGET_SPEED_MAX);
        INHERIT_SHOOTER_VELOCITY = BUILDER.comment("Model shooter velocity inherited by Minecraft projectiles.")
                .define("inherit_shooter_velocity", true);
        LOCKED_RESCAN_INTERVAL = BUILDER.comment("Ticks between full candidate scans while a sticky target is valid.")
                .defineInRange("locked_rescan_interval", 2, 1, 10);
        SHOW_BALLISTICS_HUD = BUILDER.comment("Show active ballistic profile and solved time-of-flight.")
                .define("show_ballistics_hud", true);
        BUILDER.pop();
        CLIENT_SPEC = BUILDER.build();
    }

    private Config() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    public static Screen createConfigScreen(Minecraft minecraft, Screen parent) {
        return new GunTrackerConfigScreen(parent);
    }

    private static final class ConfigScreen extends Screen {
        private final Screen parent;

        private double continuousSpeed;
        private double flickSpeed;
        private double returnSpeed;
        private int substeps;
        private double fov;
        private double maxDistance;
        private boolean visibleOnly;
        private boolean instantContinuous;
        private boolean stickyTarget;
        private double switchHysteresis;
        private TargetMode targetMode;
        private AimPoint aimPoint;
        private double customAimHeight;
        private boolean leadEnabled;
        private double projectileSpeed;
        private double projectileFriction;
        private double maxLeadTicks;
        private boolean gravityCompensation;
        private double projectileGravity;
        private boolean showHud;
        private boolean showFovRing;
        private boolean advancedBallistics;
        private boolean taczAutoBallistics;
        private boolean taczLiveCalibration;
        private boolean useTargetAcceleration;
        private double targetVelocitySmoothing;
        private double targetAccelerationSmoothing;
        private double maxTargetAcceleration;
        private double maxTrackedTargetSpeed;
        private boolean inheritShooterVelocity;
        private int lockedRescanInterval;
        private boolean showBallisticsHud;

        private ConfigScreen(Screen parent) {
            super(Component.translatable("crispywaferguntrackermod.config.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            continuousSpeed = CONTINUOUS_SPEED.get();
            flickSpeed = FLICK_SPEED.get();
            returnSpeed = FLICK_RETURN_SPEED.get();
            substeps = AIM_SUBSTEPS.get();
            fov = AIM_FOV_DEGREES.get();
            maxDistance = MAX_DISTANCE.get();
            visibleOnly = VISIBLE_ONLY.get();
            instantContinuous = INSTANT_CONTINUOUS.get();
            stickyTarget = STICKY_TARGET.get();
            switchHysteresis = SWITCH_HYSTERESIS.get();
            targetMode = TARGET_MODE.get();
            aimPoint = AIM_POINT.get();
            customAimHeight = CUSTOM_AIM_HEIGHT.get();
            leadEnabled = LEAD_ENABLED.get();
            projectileSpeed = PROJECTILE_SPEED.get();
            projectileFriction = PROJECTILE_FRICTION.get();
            maxLeadTicks = MAX_LEAD_TICKS.get();
            gravityCompensation = GRAVITY_COMPENSATION.get();
            projectileGravity = PROJECTILE_GRAVITY.get();
            showHud = SHOW_HUD.get();
            showFovRing = SHOW_FOV_RING.get();
            advancedBallistics = ADVANCED_BALLISTICS.get();
            taczAutoBallistics = TACZ_AUTO_BALLISTICS.get();
            taczLiveCalibration = TACZ_LIVE_CALIBRATION.get();
            useTargetAcceleration = USE_TARGET_ACCELERATION.get();
            targetVelocitySmoothing = TARGET_VELOCITY_SMOOTHING.get();
            targetAccelerationSmoothing = TARGET_ACCELERATION_SMOOTHING.get();
            maxTargetAcceleration = MAX_TARGET_ACCELERATION.get();
            maxTrackedTargetSpeed = MAX_TRACKED_TARGET_SPEED.get();
            inheritShooterVelocity = INHERIT_SHOOTER_VELOCITY.get();
            lockedRescanInterval = LOCKED_RESCAN_INTERVAL.get();
            showBallisticsHud = SHOW_BALLISTICS_HUD.get();

            int margin = 16;
            int gap = 8;
            int w = Math.max(150, (width - margin * 2 - gap * 2) / 3);
            int h = 20;
            int row = 24;
            int y0 = 38;
            int x1 = margin;
            int x2 = margin + w + gap;
            int x3 = margin + (w + gap) * 2;

            addRenderableWidget(new StringWidget(width / 2 - 160, 10, 320, 20, title, font));

            addRenderableWidget(doubleSlider(x1, y0, w, h, "crispywaferguntrackermod.config.continuous_speed", continuousSpeed, 0.05D, 1.0D, v -> continuousSpeed = v, 2));
            addRenderableWidget(doubleSlider(x1, y0 + row, w, h, "crispywaferguntrackermod.config.flick_speed", flickSpeed, 0.05D, 1.0D, v -> flickSpeed = v, 2));
            addRenderableWidget(doubleSlider(x1, y0 + row * 2, w, h, "crispywaferguntrackermod.config.flick_return_speed", returnSpeed, 0.05D, 1.0D, v -> returnSpeed = v, 2));
            addRenderableWidget(intSlider(x1, y0 + row * 3, w, h));
            addRenderableWidget(doubleSlider(x1, y0 + row * 4, w, h, "crispywaferguntrackermod.config.fov", fov, 5.0D, 180.0D, v -> fov = v, 0));
            addRenderableWidget(doubleSlider(x1, y0 + row * 5, w, h, "crispywaferguntrackermod.config.max_distance", maxDistance, 4.0D, 128.0D, v -> maxDistance = v, 0));
            addRenderableWidget(doubleSlider(x1, y0 + row * 6, w, h, "crispywaferguntrackermod.config.switch_hysteresis", switchHysteresis, 0.0D, 50.0D, v -> switchHysteresis = v, 0));

            addRenderableWidget(toggleButton(x2, y0, w, h, "crispywaferguntrackermod.config.instant_continuous", () -> instantContinuous, v -> instantContinuous = v));
            addRenderableWidget(toggleButton(x2, y0 + row, w, h, "crispywaferguntrackermod.config.visible_only", () -> visibleOnly, v -> visibleOnly = v));
            addRenderableWidget(toggleButton(x2, y0 + row * 2, w, h, "crispywaferguntrackermod.config.sticky_target", () -> stickyTarget, v -> stickyTarget = v));
            addRenderableWidget(Button.builder(targetModeMessage(), button -> {
                targetMode = targetMode.next();
                button.setMessage(targetModeMessage());
            }).bounds(x2, y0 + row * 3, w, h).build());
            addRenderableWidget(Button.builder(aimPointMessage(), button -> {
                aimPoint = aimPoint.next();
                button.setMessage(aimPointMessage());
            }).bounds(x2, y0 + row * 4, w, h).build());
            addRenderableWidget(doubleSlider(x2, y0 + row * 5, w, h, "crispywaferguntrackermod.config.custom_aim_height", customAimHeight, 0.0D, 1.20D, v -> customAimHeight = v, 2));
            addRenderableWidget(toggleButton(x2, y0 + row * 6, w, h, "crispywaferguntrackermod.config.lead_enabled", () -> leadEnabled, v -> leadEnabled = v));

            addRenderableWidget(toggleButton(x3, y0, w, h, "crispywaferguntrackermod.config.advanced_ballistics", () -> advancedBallistics, v -> advancedBallistics = v));
            addRenderableWidget(toggleButton(x3, y0 + row, w, h, "crispywaferguntrackermod.config.tacz_auto_ballistics", () -> taczAutoBallistics, v -> taczAutoBallistics = v));
            addRenderableWidget(toggleButton(x3, y0 + row * 2, w, h, "crispywaferguntrackermod.config.tacz_live_calibration", () -> taczLiveCalibration, v -> taczLiveCalibration = v));
            addRenderableWidget(doubleSlider(x3, y0 + row * 3, w, h, "crispywaferguntrackermod.config.projectile_speed", projectileSpeed, 0.1D, 40.0D, v -> projectileSpeed = v, 1));
            addRenderableWidget(doubleSlider(x3, y0 + row * 4, w, h, "crispywaferguntrackermod.config.projectile_friction", projectileFriction, 0.0D, 0.30D, v -> projectileFriction = v, 3));
            addRenderableWidget(doubleSlider(x3, y0 + row * 5, w, h, "crispywaferguntrackermod.config.projectile_gravity", projectileGravity, 0.0D, 0.20D, v -> projectileGravity = v, 3));
            addRenderableWidget(doubleSlider(x3, y0 + row * 6, w, h, "crispywaferguntrackermod.config.max_lead_ticks", maxLeadTicks, 1.0D, 40.0D, v -> maxLeadTicks = v, 0));
            addRenderableWidget(toggleButton(x3, y0 + row * 7, w, h, "crispywaferguntrackermod.config.use_target_acceleration", () -> useTargetAcceleration, v -> useTargetAcceleration = v));
            addRenderableWidget(toggleButton(x3, y0 + row * 8, w, h, "crispywaferguntrackermod.config.inherit_shooter_velocity", () -> inheritShooterVelocity, v -> inheritShooterVelocity = v));
            addRenderableWidget(toggleButton(x3, y0 + row * 9, w, h, "crispywaferguntrackermod.config.show_ballistics_hud", () -> showBallisticsHud, v -> showBallisticsHud = v));
            addRenderableWidget(toggleButton(x3, y0 + row * 10, w, h, "crispywaferguntrackermod.config.show_fov_ring", () -> showFovRing, v -> showFovRing = v));

            addRenderableWidget(doubleSlider(x2, y0 + row * 7, w, h, "crispywaferguntrackermod.config.velocity_smoothing", targetVelocitySmoothing, 0.05D, 1.0D, v -> targetVelocitySmoothing = v, 2));
            addRenderableWidget(doubleSlider(x2, y0 + row * 8, w, h, "crispywaferguntrackermod.config.acceleration_smoothing", targetAccelerationSmoothing, 0.05D, 1.0D, v -> targetAccelerationSmoothing = v, 2));
            addRenderableWidget(doubleSlider(x2, y0 + row * 9, w, h, "crispywaferguntrackermod.config.max_target_acceleration", maxTargetAcceleration, 0.0D, 2.0D, v -> maxTargetAcceleration = v, 2));
            addRenderableWidget(toggleButton(x2, y0 + row * 10, w, h, "crispywaferguntrackermod.config.show_hud", () -> showHud, v -> showHud = v));

            addRenderableWidget(toggleButton(x1, y0 + row * 7, w, h, "crispywaferguntrackermod.config.gravity_compensation", () -> gravityCompensation, v -> gravityCompensation = v));
            addRenderableWidget(doubleSlider(x1, y0 + row * 8, w, h, "crispywaferguntrackermod.config.max_tracked_target_speed", maxTrackedTargetSpeed, 0.5D, 20.0D, v -> maxTrackedTargetSpeed = v, 1));
            addRenderableWidget(intRangeSlider(x1, y0 + row * 9, w, h, "crispywaferguntrackermod.config.locked_rescan_interval", lockedRescanInterval, 1, 10, v -> lockedRescanInterval = v));

            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                    .bounds(width / 2 - 60, Math.min(height - 25, y0 + row * 11 + 6), 120, 20)
                    .build());
        }

        private AbstractSliderButton doubleSlider(int x, int y, int w, int h, String key, double current,
                                                   double min, double max, java.util.function.DoubleConsumer setter,
                                                   int decimals) {
            double normalized = (current - min) / (max - min);
            return new AbstractSliderButton(x, y, w, h, Component.empty(), normalized) {
                { updateMessage(); }

                @Override
                protected void updateMessage() {
                    double actual = min + value * (max - min);
                    setMessage(Component.translatable(key)
                            .append(": ")
                            .append(Component.literal(String.format(Locale.ROOT, "%." + decimals + "f", actual))));
                }

                @Override
                protected void applyValue() {
                    setter.accept(min + value * (max - min));
                }
            };
        }

        private AbstractSliderButton intSlider(int x, int y, int w, int h) {
            return new AbstractSliderButton(x, y, w, h, Component.empty(), (substeps - 1) / 7.0D) {
                { updateMessage(); }

                @Override
                protected void updateMessage() {
                    int actual = (int) Math.round(1 + value * 7);
                    setMessage(Component.translatable("crispywaferguntrackermod.config.aim_substeps")
                            .append(": ")
                            .append(Component.literal(Integer.toString(actual))));
                }

                @Override
                protected void applyValue() {
                    substeps = (int) Math.round(1 + value * 7);
                }
            };
        }

        private AbstractSliderButton intRangeSlider(int x, int y, int w, int h, String key, int current,
                                                          int min, int max, java.util.function.IntConsumer setter) {
            double normalized = (current - min) / (double) (max - min);
            return new AbstractSliderButton(x, y, w, h, Component.empty(), normalized) {
                { updateMessage(); }

                @Override
                protected void updateMessage() {
                    int actual = (int) Math.round(min + value * (max - min));
                    setMessage(Component.translatable(key).append(": ").append(Component.literal(Integer.toString(actual))));
                }

                @Override
                protected void applyValue() {
                    setter.accept((int) Math.round(min + value * (max - min)));
                }
            };
        }

        private Button toggleButton(int x, int y, int w, int h, String key, BooleanGetter getter, BooleanSetter setter) {
            return Button.builder(toggleMessage(key, getter.get()), button -> {
                boolean next = !getter.get();
                setter.set(next);
                button.setMessage(toggleMessage(key, next));
            }).bounds(x, y, w, h).build();
        }

        private Component toggleMessage(String key, boolean value) {
            return Component.translatable(key)
                    .append(": ")
                    .append(Component.translatable(value
                            ? "crispywaferguntrackermod.config.on"
                            : "crispywaferguntrackermod.config.off"));
        }

        private Component targetModeMessage() {
            return Component.translatable("crispywaferguntrackermod.config.target_mode")
                    .append(": ")
                    .append(Component.translatable("crispywaferguntrackermod.config.target_mode."
                            + targetMode.name().toLowerCase(Locale.ROOT)));
        }

        private Component aimPointMessage() {
            return Component.translatable("crispywaferguntrackermod.config.aim_point")
                    .append(": ")
                    .append(Component.translatable("crispywaferguntrackermod.config.aim_point."
                            + aimPoint.name().toLowerCase(Locale.ROOT)));
        }

        private void saveAndClose() {
            CONTINUOUS_SPEED.set(continuousSpeed);
            FLICK_SPEED.set(flickSpeed);
            FLICK_RETURN_SPEED.set(returnSpeed);
            AIM_SUBSTEPS.set(substeps);
            AIM_FOV_DEGREES.set(fov);
            MAX_DISTANCE.set(maxDistance);
            VISIBLE_ONLY.set(visibleOnly);
            INSTANT_CONTINUOUS.set(instantContinuous);
            STICKY_TARGET.set(stickyTarget);
            SWITCH_HYSTERESIS.set(switchHysteresis);
            TARGET_MODE.set(targetMode);
            AIM_POINT.set(aimPoint);
            CUSTOM_AIM_HEIGHT.set(customAimHeight);
            LEAD_ENABLED.set(leadEnabled);
            PROJECTILE_SPEED.set(projectileSpeed);
            PROJECTILE_FRICTION.set(projectileFriction);
            MAX_LEAD_TICKS.set(maxLeadTicks);
            GRAVITY_COMPENSATION.set(gravityCompensation);
            PROJECTILE_GRAVITY.set(projectileGravity);
            SHOW_HUD.set(showHud);
            SHOW_FOV_RING.set(showFovRing);
            ADVANCED_BALLISTICS.set(advancedBallistics);
            TACZ_AUTO_BALLISTICS.set(taczAutoBallistics);
            TACZ_LIVE_CALIBRATION.set(taczLiveCalibration);
            USE_TARGET_ACCELERATION.set(useTargetAcceleration);
            TARGET_VELOCITY_SMOOTHING.set(targetVelocitySmoothing);
            TARGET_ACCELERATION_SMOOTHING.set(targetAccelerationSmoothing);
            MAX_TARGET_ACCELERATION.set(maxTargetAcceleration);
            MAX_TRACKED_TARGET_SPEED.set(maxTrackedTargetSpeed);
            INHERIT_SHOOTER_VELOCITY.set(inheritShooterVelocity);
            LOCKED_RESCAN_INTERVAL.set(lockedRescanInterval);
            SHOW_BALLISTICS_HUD.set(showBallisticsHud);
            CLIENT_SPEC.save();
            minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }

    @FunctionalInterface
    private interface BooleanGetter { boolean get(); }

    @FunctionalInterface
    private interface BooleanSetter { void set(boolean value); }
}
