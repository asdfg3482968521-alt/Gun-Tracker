from pathlib import Path

path = Path("src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java")
s = path.read_text(encoding="utf-8")
old = "    private void rebuildWidgets() {\n"
new = "    @Override\n    protected void rebuildWidgets() {\n"
if s.count(old) != 1:
    raise SystemExit(f"expected exactly one rebuildWidgets match, got {s.count(old)}")
path.write_text(s.replace(old, new, 1), encoding="utf-8")
Path(__file__).unlink()
print("Patched rebuildWidgets override")
