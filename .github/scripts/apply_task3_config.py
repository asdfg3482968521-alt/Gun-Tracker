from pathlib import Path
import os, subprocess

root = Path(__file__).resolve().parents[2]
path = root / "src/main/java/com/crispywafer/crispywaferguntracker/Config.java"
s = path.read_text(encoding="utf-8")

old = """    public static final double UNLOCK_FOV_MIN = 5.0D;\n    public static final double UNLOCK_FOV_MAX = 180.0D;\n"""
new = old + """    public static final int VIEW_STABILITY_MIN = 0;\n    public static final int VIEW_STABILITY_MAX = 100;\n    public static final double AIM_GAIN_MIN = 0.05D;\n    public static final double AIM_GAIN_MAX = 1.0D;\n    public static final double AIM_MAX_TURN_MIN = 1.0D;\n    public static final double AIM_MAX_TURN_MAX = 90.0D;\n"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_4;\n    public static final ForgeConfigSpec.DoubleValue CONTINUOUS_SPEED;\n"""
new = """    public static final ForgeConfigSpec.EnumValue<AimActivationController.TriggerMode> TRIGGER_MODE_4;\n    public static final ForgeConfigSpec.IntValue VIEW_STABILITY;\n    public static final ForgeConfigSpec.DoubleValue SMOOTH_FOLLOW_GAIN;\n    public static final ForgeConfigSpec.DoubleValue SMOOTH_MAX_TURN;\n    public static final ForgeConfigSpec.DoubleValue SNAP_FOLLOW_GAIN;\n    public static final ForgeConfigSpec.DoubleValue SNAP_MAX_TURN;\n    public static final ForgeConfigSpec.DoubleValue FLICK_INITIAL_GAIN;\n    public static final ForgeConfigSpec.DoubleValue FLICK_INITIAL_MAX_TURN;\n    public static final ForgeConfigSpec.DoubleValue FLICK_TRACK_GAIN;\n    public static final ForgeConfigSpec.DoubleValue FLICK_TRACK_MAX_TURN;\n    public static final ForgeConfigSpec.DoubleValue RETURN_GAIN;\n    public static final ForgeConfigSpec.DoubleValue RETURN_MAX_TURN;\n    public static final ForgeConfigSpec.DoubleValue CONTINUOUS_SPEED;\n"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        TRIGGER_MODE_4 = BUILDER.comment(\"Activation mode for trigger slot 4.\")\n                .defineEnum(\"trigger_mode_4\", AimActivationController.TriggerMode.HOLD);\n        CONTINUOUS_SPEED = BUILDER.comment(\"Continuous tracking speed. 1.0 is maximum.\")\n                .defineInRange(\"continuous_speed\", 0.90D, 0.05D, 1.0D);\n"""
new = """        TRIGGER_MODE_4 = BUILDER.comment(\"Activation mode for trigger slot 4.\")\n                .defineEnum(\"trigger_mode_4\", AimActivationController.TriggerMode.HOLD);\n        VIEW_STABILITY = BUILDER.comment(\"Cross-tick target angle stability from 0 to 100.\")\n                .defineInRange(\"view_stability\", 70, VIEW_STABILITY_MIN, VIEW_STABILITY_MAX);\n        SMOOTH_FOLLOW_GAIN = BUILDER.comment(\"Smooth-track follow gain per tick.\")\n                .defineInRange(\"smooth_follow_gain\", 0.28D, AIM_GAIN_MIN, AIM_GAIN_MAX);\n        SMOOTH_MAX_TURN = BUILDER.comment(\"Smooth-track maximum turn in degrees per tick.\")\n                .defineInRange(\"smooth_max_turn\", 10.0D, AIM_MAX_TURN_MIN, AIM_MAX_TURN_MAX);\n        SNAP_FOLLOW_GAIN = BUILDER.comment(\"Follow gain after SNAP's one-time acquisition snap.\")\n                .defineInRange(\"snap_follow_gain\", 0.55D, AIM_GAIN_MIN, AIM_GAIN_MAX);\n        SNAP_MAX_TURN = BUILDER.comment(\"Maximum follow turn after SNAP acquisition.\")\n                .defineInRange(\"snap_max_turn\", 18.0D, AIM_MAX_TURN_MIN, AIM_MAX_TURN_MAX);\n        FLICK_INITIAL_GAIN = BUILDER.comment(\"Fast-stage gain for FLICK_RETURN.\")\n                .defineInRange(\"flick_initial_gain\", 0.75D, AIM_GAIN_MIN, AIM_GAIN_MAX);\n        FLICK_INITIAL_MAX_TURN = BUILDER.comment(\"Fast-stage maximum turn for FLICK_RETURN.\")\n                .defineInRange(\"flick_initial_max_turn\", 35.0D, AIM_MAX_TURN_MIN, AIM_MAX_TURN_MAX);\n        FLICK_TRACK_GAIN = BUILDER.comment(\"Tracking gain after the flick reaches the target.\")\n                .defineInRange(\"flick_track_gain\", 0.40D, AIM_GAIN_MIN, AIM_GAIN_MAX);\n        FLICK_TRACK_MAX_TURN = BUILDER.comment(\"Tracking maximum turn after the flick reaches the target.\")\n                .defineInRange(\"flick_track_max_turn\", 15.0D, AIM_MAX_TURN_MIN, AIM_MAX_TURN_MAX);\n        RETURN_GAIN = BUILDER.comment(\"Return-to-original-view gain for FLICK_RETURN.\")\n                .defineInRange(\"return_gain\", 0.35D, AIM_GAIN_MIN, AIM_GAIN_MAX);\n        RETURN_MAX_TURN = BUILDER.comment(\"Return-to-original-view maximum turn per tick.\")\n                .defineInRange(\"return_max_turn\", 30.0D, AIM_MAX_TURN_MIN, AIM_MAX_TURN_MAX);\n        CONTINUOUS_SPEED = BUILDER.comment(\"Legacy continuous tracking speed kept for config compatibility.\")\n                .defineInRange(\"continuous_speed\", 0.90D, 0.05D, 1.0D);\n"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
path.write_text(s, encoding="utf-8")
Path(__file__).unlink()
subprocess.run(["git", "config", "user.name", "github-actions[bot]"], check=True)
subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], check=True)
subprocess.run(["git", "add", str(path.relative_to(root)), ".github/scripts/apply_task3_config.py"], check=True)
subprocess.run(["git", "commit", "-m", "feat: add distinct aim mode config"], check=True)
subprocess.run(["git", "push", "origin", f"HEAD:{os.environ['GITHUB_REF_NAME']}"], check=True)
