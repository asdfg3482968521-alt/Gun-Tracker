from pathlib import Path
import shutil

ROOT = Path.cwd()
OLD_NS = "crispywaferguntrackermod"
NEW_NS = "invisiblekeybinding"

# Apply the namespace/resource rename everywhere the uploaded package changed it.
# Keep docs untouched because the uploaded ZIP keeps the historical design docs as-is.
for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    rel = path.relative_to(ROOT).as_posix()
    if rel.startswith(".git/") or rel.startswith("docs/") or rel.startswith(".github/scripts/") or rel == ".github/workflows/build.yml":
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    updated = text.replace(OLD_NS, NEW_NS)
    if updated != text:
        path.write_text(updated, encoding="utf-8")

old_assets = ROOT / "src/main/resources/assets" / OLD_NS
new_assets = ROOT / "src/main/resources/assets" / NEW_NS
if old_assets.exists():
    if new_assets.exists():
        shutil.rmtree(new_assets)
    old_assets.rename(new_assets)

# Exact uploaded metadata/build identity changes.
build = ROOT / "build.gradle"
text = build.read_text(encoding="utf-8")
text = text.replace("archivesName = 'gun-tracker-singleplayer-enhanced'", "archivesName = 'invisiblekeybinding'")
build.write_text(text, encoding="utf-8")

props = ROOT / "gradle.properties"
values = {
    "mod_id": "invisiblekeybinding",
    "mod_name": "InvisibleKeybinding",
    "mod_license": "All-Rights-Reserved",
    "mod_version": "1.0.0",
    "mod_group_id": "com.crispywafer.invisiblekeybinding",
    "mod_authors": "Tapio, TuYw",
    "mod_description": "Client-side utility tweaks.",
}
lines = []
for line in props.read_text(encoding="utf-8").splitlines():
    key = line.split("=", 1)[0] if "=" in line else None
    if key in values:
        line = f"{key}={values[key]}"
    lines.append(line)
props.write_text("\n".join(lines) + "\n", encoding="utf-8")

mods = ROOT / "src/main/resources/META-INF/mods.toml"
text = mods.read_text(encoding="utf-8")
text = text.replace('license="MIT"', 'license="All-Rights-Reserved"')
text = text.replace('displayName="CrispyWafer Gun Tracker"', 'displayName="InvisibleKeybinding"')
text = text.replace(
    "Client-side ballistic aim tracker for Minecraft Forge 1.20.1.\n"
    "Adds TACZ-aware live ballistic calibration, iterative drag/gravity interception, motion filtering, FOV targeting, stable target switching and Simplified Chinese UI.",
    "Client-side utility tweaks."
)
text = text.replace('authors="Sodium_CrispyWafer"', 'authors="Tapio, TuYw"')
text = text.replace('displayTest="IGNORE_SERVER_VERSION"\n', '')
mods.write_text(text, encoding="utf-8")

keys = ROOT / "src/main/java/com/crispywafer/crispywaferguntracker/Keybindings.java"
text = keys.read_text(encoding="utf-8")
text = text.replace("GLFW.GLFW_KEY_G,", "GLFW.GLFW_KEY_K,")
text = text.replace("GLFW.GLFW_KEY_Z,", "GLFW.GLFW_KEY_L,")
keys.write_text(text, encoding="utf-8")

# The uploaded ZIP still contains the same Java exhaustiveness bug as the previous upload.
# Keep the already-verified minimal fix so this source can compile.
screen = ROOT / "src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java"
text = screen.read_text(encoding="utf-8")
needle = "            case HUD -> 9;\n        };"
if "case WEAPON -> 5;" not in text:
    if needle not in text:
        raise SystemExit("Could not locate totalRows() switch for WEAPON compile fix")
    text = text.replace(needle, "            case HUD -> 9;\n            case WEAPON -> 5;\n        };", 1)
screen.write_text(text, encoding="utf-8")

# Restore the uploaded package's normal read-only workflow after this one-time import run.
workflow = ROOT / ".github/workflows/build.yml"
workflow.write_text("""name: Build Forge Mod

on:
  push:
    branches:
      - main
      - feature/stable-lock-target-filter-hud
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - name: Checkout source
        uses: actions/checkout@v6

      - name: Set up Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle 8.8
        uses: gradle/actions/setup-gradle@v6
        with:
          gradle-version: '8.8'

      - name: Build
        run: gradle clean build --stacktrace --no-daemon

      - name: Upload mod JAR
        uses: actions/upload-artifact@v4
        with:
          name: gun-tracker-forge-1.20.1
          path: build/libs/*.jar
          if-no-files-found: error
          retention-days: 30
""", encoding="utf-8")

# Remove this temporary importer so the final branch is a clean source tree.
Path(__file__).unlink()
print("Applied uploaded source package v2 with verified WEAPON row-count compile fix.")
