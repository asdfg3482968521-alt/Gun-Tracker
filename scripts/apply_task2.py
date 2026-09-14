from pathlib import Path
path = Path("src/main/java/com/crispywafer/crispywaferguntracker/Config.java")
s = path.read_text(encoding="utf-8")
old = "        return new ConfigScreen(parent);\n"
new = "        return new GunTrackerConfigScreen(parent);\n"
if s.count(old) != 1:
    raise SystemExit(f"expected exactly one config-screen factory match, got {s.count(old)}")
path.write_text(s.replace(old, new, 1), encoding="utf-8")
Path(__file__).unlink()
print("Switched config factory to GunTrackerConfigScreen")
