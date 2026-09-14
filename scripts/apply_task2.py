from pathlib import Path

path = Path("src/main/java/com/crispywafer/crispywaferguntracker/Config.java")
s = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, got {count}: {old[:80]!r}")
    s = s.replace(old, new, 1)

replace_once(
    "public final class Config {\n    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();\n",
    """public final class Config {\n    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();\n\n    public static final double MAX_DISTANCE_MIN = 4.0D;\n    public static final double MAX_DISTANCE_MAX = 300.0D;\n    public static final int AIM_SUBSTEPS_MIN = 1;\n    public static final int AIM_SUBSTEPS_MAX = 32;\n    public static final double MAX_LEAD_TICKS_MIN = 1.0D;\n    public static final double MAX_LEAD_TICKS_MAX = 200.0D;\n    public static final double PROJECTILE_SPEED_MIN = 0.1D;\n    public static final double PROJECTILE_SPEED_MAX = 200.0D;\n    public static final double MAX_TRACKED_TARGET_SPEED_MIN = 0.5D;\n    public static final double MAX_TRACKED_TARGET_SPEED_MAX = 50.0D;\n    public static final double MAX_TARGET_ACCELERATION_MIN = 0.0D;\n    public static final double MAX_TARGET_ACCELERATION_MAX = 10.0D;\n    public static final int LONG_PRESS_MS_MIN = 50;\n    public static final int LONG_PRESS_MS_MAX = 2000;\n""",
)

replace_once(
    "    public enum TargetMode {\n",
    """    public enum AimBehavior {\n        SMOOTH_TRACK,\n        SNAP,\n        FLICK_RETURN;\n\n        public AimBehavior next() {\n            AimBehavior[] values = values();\n            return values[(ordinal() + 1) % values.length];\n        }\n    }\n\n    public enum TargetMode {\n""",
)

replace_once(
    "    public static final ForgeConfigSpec.DoubleValue CONTINUOUS_SPEED;\n",
    """    public static final ForgeConfigSpec.BooleanValue MASTER_ENABLED;\n    public static final ForgeConfigSpec.EnumValue<AimBehavior> AIM_BEHAVIOR;\n    public static final ForgeConfigSpec.IntValue LONG_PRESS_MS;\n    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_1;\n    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_2;\n    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_3;\n    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_4;\n    public static final ForgeConfigSpec.DoubleValue CONTINUOUS_SPEED;\n""",
)

replace_once(
    '        BUILDER.push("aim_settings");\n',
    """        BUILDER.push("aim_settings");\n        MASTER_ENABLED = BUILDER.comment("Global master switch for all aim activation.")\n                .define("master_enabled", true);\n        AIM_BEHAVIOR = BUILDER.comment("Aim behavior shared by all trigger slots.")\n                .defineEnum("aim_behavior", AimBehavior.SMOOTH_TRACK);\n        LONG_PRESS_MS = BUILDER.comment("Long-hold activation threshold in milliseconds.")\n                .defineInRange("long_press_ms", 200, LONG_PRESS_MS_MIN, LONG_PRESS_MS_MAX);\n        TRIGGER_MODE_1 = BUILDER.comment("Activation mode for trigger slot 1.")\n                .defineEnum("trigger_mode_1", AimActivationController.TriggerMode.HOLD);\n        TRIGGER_MODE_2 = BUILDER.comment("Activation mode for trigger slot 2.")\n                .defineEnum("trigger_mode_2", AimActivationController.TriggerMode.TOGGLE);\n        TRIGGER_MODE_3 = BUILDER.comment("Activation mode for trigger slot 3.")\n                .defineEnum("trigger_mode_3", AimActivationController.TriggerMode.HOLD);\n        TRIGGER_MODE_4 = BUILDER.comment("Activation mode for trigger slot 4.")\n                .defineEnum("trigger_mode_4", AimActivationController.TriggerMode.HOLD);\n""",
)

for old, new in {
    '.defineInRange("aim_substeps", 4, 1, 8);': '.defineInRange("aim_substeps", 4, AIM_SUBSTEPS_MIN, AIM_SUBSTEPS_MAX);',
    '.defineInRange("max_distance", 96.0D, 4.0D, 128.0D);': '.defineInRange("max_distance", 96.0D, MAX_DISTANCE_MIN, MAX_DISTANCE_MAX);',
    '.defineInRange("projectile_speed", 8.0D, 0.1D, 40.0D);': '.defineInRange("projectile_speed", 8.0D, PROJECTILE_SPEED_MIN, PROJECTILE_SPEED_MAX);',
    '.defineInRange("max_lead_ticks", 30.0D, 1.0D, 40.0D);': '.defineInRange("max_lead_ticks", 30.0D, MAX_LEAD_TICKS_MIN, MAX_LEAD_TICKS_MAX);',
    '.defineInRange("max_target_acceleration", 0.35D, 0.0D, 2.0D);': '.defineInRange("max_target_acceleration", 0.35D, MAX_TARGET_ACCELERATION_MIN, MAX_TARGET_ACCELERATION_MAX);',
    '.defineInRange("max_tracked_target_speed", 4.0D, 0.5D, 20.0D);': '.defineInRange("max_tracked_target_speed", 4.0D, MAX_TRACKED_TARGET_SPEED_MIN, MAX_TRACKED_TARGET_SPEED_MAX);',
}.items():
    replace_once(old, new)

path.write_text(s, encoding="utf-8")
Path(__file__).unlink()
print("Config range/mode patch applied")
