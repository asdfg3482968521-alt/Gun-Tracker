from pathlib import Path

p = Path("src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java")
s = p.read_text(encoding="utf-8")
old = "    private void saveAndClose() {\n\n    private void saveAndClose() {"
if old not in s:
    raise SystemExit("expected duplicate saveAndClose marker not found")
p.write_text(s.replace(old, "    private void saveAndClose() {", 1), encoding="utf-8")
print("task4 syntax repaired")
