# Gun-Tracker In-Game Config Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a transparent Simplified-Chinese in-game configuration panel opened with Left Alt, a master aimbot switch, four keyboard/mouse trigger slots with per-slot activation mode, expanded numeric ranges including a 300-block maximum activation distance, and integrate the new activation model with the existing singleplayer-only aim logic.

**Architecture:** Keep one shared aimbot configuration and separate input activation from aim behavior. `AimActivationController` converts four `KeyMapping` states into one unified active/inactive signal, while `AimHandler` only executes the selected aim behavior. Move the large UI out of `Config.java` into `GunTrackerConfigScreen.java`; keep Forge CLIENT config as the source of truth for numeric/enum settings and Minecraft `KeyMapping` as the source of truth for physical keyboard/mouse bindings.

**Tech Stack:** Minecraft Forge 1.20.1, Forge 47.3.22, Java 17, Gradle 8.8, ForgeConfigSpec, Minecraft `Screen`/widgets, GLFW input codes, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-14-in-game-config-panel-design.md`

## Global Constraints

- Minecraft: 1.20.1.
- Forge: 47.3.22.
- Java: 17.
- Gradle: 8.8.
- Preserve the current strict singleplayer boundary: integrated server must exist and player count must be at most 1.
- Left Alt opens/closes the config panel and no longer toggles aim directly.
- Provide exactly four user trigger slots in this iteration.
- Trigger slots may bind keyboard keys or mouse buttons.
- Backspace clears a binding while the config panel is capturing input; Escape cancels capture without changing the old binding.
- Each trigger slot independently selects `按住生效`, `单击切换`, or `长按生效`; all slots trigger the same shared aim configuration.
- A master switch disables every aim trigger without blocking access to the config panel.
- Maximum activation/search distance is 300 blocks; all logic and UI must read the same `Config.MAX_DISTANCE` value.
- UI copy, mode labels, help text, binding state text, and HUD additions must have Simplified Chinese translations.
- No external GUI library.
- No multiplayer enablement, macros, click automation, or per-trigger aim presets.

---

## File Structure

**Create**
- `src/main/java/com/crispywafer/crispywaferguntracker/AimActivationController.java` — four-slot input state machine; no target selection or camera movement.
- `src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java` — transparent paged/scrollable config UI, key/mouse capture, Backspace clear, Escape cancel.
- `src/test/java/com/crispywafer/crispywaferguntracker/AimActivationControllerTestMain.java` — plain-Java state-machine tests runnable by Gradle without JUnit.

**Modify**
- `src/main/java/com/crispywafer/crispywaferguntracker/Config.java` — config-only enums/values/range constants and config-screen factory; remove the nested giant screen implementation.
- `src/main/java/com/crispywafer/crispywaferguntracker/Keybindings.java` — one panel binding plus four trigger `KeyMapping`s; helper accessors for slots.
- `src/main/java/com/crispywafer/crispywaferguntracker/AimHandler.java` — consume unified activation and execute selected behavior; preserve strict singleplayer reset semantics.
- `src/main/java/com/crispywafer/crispywaferguntracker/AimHud.java` — show master-switch/activation/behavior status with Chinese translations.
- `src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json` — full Chinese copy for pages, modes, key capture, status, numeric units.
- `src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json` — matching fallback keys.
- `build.gradle` — add a no-dependency `logicTest` JavaExec task and wire it into `check`.

---

### Task 1: Add testable activation-state logic

**Files:**
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/AimActivationController.java`
- Create: `src/test/java/com/crispywafer/crispywaferguntracker/AimActivationControllerTestMain.java`
- Modify: `build.gradle`

**Interfaces:**
- Produces enum `AimActivationController.TriggerMode { HOLD, TOGGLE, LONG_HOLD }`.
- Produces record `AimActivationController.SlotInput(boolean down, boolean clicked, TriggerMode mode)`.
- Produces `boolean update(boolean masterEnabled, boolean screenOpen, long nowMillis, long longPressMillis, SlotInput[] slots)`.
- Produces `void reset()` and `boolean isActive()`.
- Later tasks feed four `KeyMapping` snapshots into this controller.

- [ ] **Step 1: Write failing state-machine tests**

Create a plain-main test class with these exact cases:

```java
public final class AimActivationControllerTestMain {
    public static void main(String[] args) {
        testHoldUsesLogicalOr();
        testToggleFlipsOncePerClick();
        testLongHoldWaitsForThreshold();
        testMasterSwitchForcesOff();
        testScreenOpenForcesOffAndClearsTransientState();
        testDuplicateToggleClicksCountOnce();
        System.out.println("AimActivationController tests passed");
    }

    private static AimActivationController.SlotInput slot(
            boolean down,
            boolean clicked,
            AimActivationController.TriggerMode mode
    ) {
        return new AimActivationController.SlotInput(down, clicked, mode);
    }

    private static AimActivationController.SlotInput[] emptySlots() {
        return new AimActivationController.SlotInput[] {
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD),
                slot(false, false, AimActivationController.TriggerMode.HOLD)
        };
    }

    private static void testHoldUsesLogicalOr() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1000, 200, slots), "slot 1 hold should activate");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.HOLD);
        slots[1] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1010, 200, slots), "slot 2 hold should keep activation on");
    }

    private static void testToggleFlipsOncePerClick() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "first click toggles on");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1010, 200, slots), "toggle remains on");
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertFalse(c.update(true, false, 1020, 200, slots), "second click toggles off");
    }

    private static void testLongHoldWaitsForThreshold() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.LONG_HOLD);
        assertFalse(c.update(true, false, 1000, 200, slots), "long hold must not activate immediately");
        assertFalse(c.update(true, false, 1199, 200, slots), "long hold must wait for threshold");
        assertTrue(c.update(true, false, 1200, 200, slots), "long hold activates at threshold");
        slots[0] = slot(false, false, AimActivationController.TriggerMode.LONG_HOLD);
        assertFalse(c.update(true, false, 1210, 200, slots), "release ends long hold");
    }

    private static void testMasterSwitchForcesOff() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "toggle on first");
        assertFalse(c.update(false, false, 1010, 200, slots), "master off must force inactive");
        assertFalse(c.update(true, false, 1020, 200, emptySlots()), "master off must clear toggle state");
    }

    private static void testScreenOpenForcesOffAndClearsTransientState() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(true, false, AimActivationController.TriggerMode.HOLD);
        assertTrue(c.update(true, false, 1000, 200, slots), "hold on first");
        assertFalse(c.update(true, true, 1010, 200, slots), "screen open must suppress aim");
        assertFalse(c.update(true, false, 1020, 200, emptySlots()), "screen close must not restore stale hold");
    }

    private static void testDuplicateToggleClicksCountOnce() {
        AimActivationController c = new AimActivationController();
        AimActivationController.SlotInput[] slots = emptySlots();
        slots[0] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        slots[1] = slot(false, true, AimActivationController.TriggerMode.TOGGLE);
        assertTrue(c.update(true, false, 1000, 200, slots), "same physical click represented twice must toggle once");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
```

- [ ] **Step 2: Add a Gradle task and verify RED**

Add:

```groovy
tasks.register('logicTest', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'com.crispywafer.crispywaferguntracker.AimActivationControllerTestMain'
}

tasks.named('check').configure {
    dependsOn 'logicTest'
}
```

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: FAIL because `AimActivationController` does not exist yet.

- [ ] **Step 3: Implement the minimal controller**

Use four fixed slots internally for long-hold timestamps and a single shared toggle latch. Required behavior:

```java
public final class AimActivationController {
    public enum TriggerMode { HOLD, TOGGLE, LONG_HOLD }
    public record SlotInput(boolean down, boolean clicked, TriggerMode mode) {}

    private final long[] downSince = {-1L, -1L, -1L, -1L};
    private boolean toggleActive;
    private boolean active;

    public boolean update(
            boolean masterEnabled,
            boolean screenOpen,
            long nowMillis,
            long longPressMillis,
            SlotInput[] slots
    ) {
        if (!masterEnabled || screenOpen) {
            reset();
            return false;
        }

        boolean anyHold = false;
        boolean anyToggleClick = false;
        for (int i = 0; i < 4; i++) {
            SlotInput slot = slots[i];
            switch (slot.mode()) {
                case HOLD -> anyHold |= slot.down();
                case TOGGLE -> anyToggleClick |= slot.clicked();
                case LONG_HOLD -> {
                    if (slot.down()) {
                        if (downSince[i] < 0L) downSince[i] = nowMillis;
                        anyHold |= nowMillis - downSince[i] >= longPressMillis;
                    } else {
                        downSince[i] = -1L;
                    }
                }
            }
        }
        if (anyToggleClick) toggleActive = !toggleActive;
        active = toggleActive || anyHold;
        return active;
    }

    public boolean isActive() { return active; }

    public void reset() {
        toggleActive = false;
        active = false;
        java.util.Arrays.fill(downSince, -1L);
    }
}
```

- [ ] **Step 4: Run GREEN tests**

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: PASS and output contains `AimActivationController tests passed`.

- [ ] **Step 5: Commit**

Commit message: `feat: add multi-trigger activation state machine`

---

### Task 2: Define shared config model and expanded ranges

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Config.java`
- Modify: `src/test/java/com/crispywafer/crispywaferguntracker/AimActivationControllerTestMain.java`

**Interfaces:**
- Produces `Config.AimBehavior { SMOOTH_TRACK, SNAP, FLICK_RETURN }`.
- Produces `Config.MASTER_ENABLED`.
- Produces `Config.AIM_BEHAVIOR`.
- Produces `Config.TRIGGER_MODE_1` through `Config.TRIGGER_MODE_4`, typed as `AimActivationController.TriggerMode`.
- Produces `Config.LONG_PRESS_MS`.
- Produces public range constants used by both ForgeConfigSpec and UI.

- [ ] **Step 1: Extend test main with range contract assertions**

Add assertions that constants equal these values:

```java
assertEquals(300.0D, Config.MAX_DISTANCE_MAX, "distance max");
assertEquals(32, Config.AIM_SUBSTEPS_MAX, "substeps max");
assertEquals(200.0D, Config.MAX_LEAD_TICKS_MAX, "lead max");
assertEquals(200.0D, Config.PROJECTILE_SPEED_MAX, "projectile speed max");
assertEquals(50.0D, Config.MAX_TRACKED_TARGET_SPEED_MAX, "tracked speed max");
assertEquals(10.0D, Config.MAX_TARGET_ACCELERATION_MAX, "acceleration max");
```

- [ ] **Step 2: Run test to verify RED**

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: FAIL because range constants do not exist.

- [ ] **Step 3: Refactor `Config.java` into config-only responsibility**

Add constants:

```java
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
```

Add:

```java
public enum AimBehavior {
    SMOOTH_TRACK, SNAP, FLICK_RETURN;
    public AimBehavior next() {
        AimBehavior[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
```

Forge config defaults:

```java
MASTER_ENABLED = BUILDER.define("master_enabled", true);
AIM_BEHAVIOR = BUILDER.defineEnum("aim_behavior", AimBehavior.SMOOTH_TRACK);
LONG_PRESS_MS = BUILDER.defineInRange("long_press_ms", 200, LONG_PRESS_MS_MIN, LONG_PRESS_MS_MAX);
TRIGGER_MODE_1 = BUILDER.defineEnum("trigger_mode_1", AimActivationController.TriggerMode.HOLD);
TRIGGER_MODE_2 = BUILDER.defineEnum("trigger_mode_2", AimActivationController.TriggerMode.TOGGLE);
TRIGGER_MODE_3 = BUILDER.defineEnum("trigger_mode_3", AimActivationController.TriggerMode.HOLD);
TRIGGER_MODE_4 = BUILDER.defineEnum("trigger_mode_4", AimActivationController.TriggerMode.HOLD);
```

Change existing bounds to shared constants, especially:

```java
MAX_DISTANCE = BUILDER.defineInRange("max_distance", 96.0D, MAX_DISTANCE_MIN, MAX_DISTANCE_MAX);
AIM_SUBSTEPS = BUILDER.defineInRange("aim_substeps", 4, AIM_SUBSTEPS_MIN, AIM_SUBSTEPS_MAX);
PROJECTILE_SPEED = BUILDER.defineInRange("projectile_speed", 8.0D, PROJECTILE_SPEED_MIN, PROJECTILE_SPEED_MAX);
MAX_LEAD_TICKS = BUILDER.defineInRange("max_lead_ticks", 30.0D, MAX_LEAD_TICKS_MIN, MAX_LEAD_TICKS_MAX);
MAX_TARGET_ACCELERATION = BUILDER.defineInRange("max_target_acceleration", 0.35D, MAX_TARGET_ACCELERATION_MIN, MAX_TARGET_ACCELERATION_MAX);
MAX_TRACKED_TARGET_SPEED = BUILDER.defineInRange("max_tracked_target_speed", 4.0D, MAX_TRACKED_TARGET_SPEED_MIN, MAX_TRACKED_TARGET_SPEED_MAX);
```

Remove the nested `ConfigScreen` implementation and change factory to:

```java
public static Screen createConfigScreen(Minecraft minecraft, Screen parent) {
    return new GunTrackerConfigScreen(parent);
}
```

Keep old config keys that are still read by behavior code until Task 5 removes/replaces their use; do not delete user settings prematurely.

- [ ] **Step 4: Run logic tests**

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `refactor: centralize aim config ranges and modes`

---

### Task 3: Register panel key and four trigger slots

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Keybindings.java`

**Interfaces:**
- Produces `KeyMapping openConfigKey` default Left Alt.
- Produces `KeyMapping triggerKey1` … `triggerKey4`.
- Produces `static KeyMapping triggerKey(int index)` for indices 0..3.
- Physical trigger defaults: all unbound except slot 1 defaults Mouse Button 5 to preserve a usable out-of-box trigger.

- [ ] **Step 1: Add compile-time references in the activation test source**

Add a helper that verifies the slot-count contract without creating Minecraft runtime objects:

```java
assertEquals(4, Keybindings.TRIGGER_SLOT_COUNT, "trigger slot count");
```

- [ ] **Step 2: Run `gradle testClasses` to verify RED**

Expected: FAIL because `TRIGGER_SLOT_COUNT` does not exist.

- [ ] **Step 3: Implement key mappings**

Required declarations:

```java
public static final int TRIGGER_SLOT_COUNT = 4;
public static KeyMapping openConfigKey;
public static KeyMapping triggerKey1;
public static KeyMapping triggerKey2;
public static KeyMapping triggerKey3;
public static KeyMapping triggerKey4;
```

Register:

```java
openConfigKey = new KeyMapping(
        "key.crispywaferguntrackermod.open_config",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.crispywaferguntrackermod"
);
triggerKey1 = new KeyMapping(
        "key.crispywaferguntrackermod.trigger_1",
        InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_5,
        "key.categories.crispywaferguntrackermod"
);
triggerKey2 = new KeyMapping("key.crispywaferguntrackermod.trigger_2", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), "key.categories.crispywaferguntrackermod");
triggerKey3 = new KeyMapping("key.crispywaferguntrackermod.trigger_3", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), "key.categories.crispywaferguntrackermod");
triggerKey4 = new KeyMapping("key.crispywaferguntrackermod.trigger_4", InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), "key.categories.crispywaferguntrackermod");
```

If Forge/Minecraft rejects constructing unbound mappings using `UNKNOWN.getType()`, use `InputConstants.Type.KEYSYM` with `InputConstants.UNKNOWN.getValue()`; verify by cloud compile rather than guessing.

Add:

```java
public static KeyMapping triggerKey(int index) {
    return switch (index) {
        case 0 -> triggerKey1;
        case 1 -> triggerKey2;
        case 2 -> triggerKey3;
        case 3 -> triggerKey4;
        default -> throw new IndexOutOfBoundsException("trigger index " + index);
    };
}
```

- [ ] **Step 4: Run `gradle testClasses`**

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: register configurable aim trigger slots`

---

### Task 4: Build the transparent Chinese config screen and input capture

**Files:**
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json`

**Interfaces:**
- Constructor: `GunTrackerConfigScreen(Screen parent)`.
- Public/static helper allowed for tests: `static double sliderToValue(double normalized, double min, double max)` and `static double valueToSlider(double value, double min, double max)`.
- Capturing state stores `int capturingSlot = -1` and the previous binding is untouched until a valid key/mouse input is received.

- [ ] **Step 1: Add failing pure slider-conversion tests**

Append to the plain-main test class:

```java
assertClose(4.0D, GunTrackerConfigScreen.sliderToValue(0.0D, 4.0D, 300.0D), 1.0e-9, "slider min");
assertClose(300.0D, GunTrackerConfigScreen.sliderToValue(1.0D, 4.0D, 300.0D), 1.0e-9, "slider max");
assertClose(1.0D, GunTrackerConfigScreen.valueToSlider(300.0D, 4.0D, 300.0D), 1.0e-9, "value max");
```

- [ ] **Step 2: Run RED test**

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: FAIL because `GunTrackerConfigScreen` does not exist.

- [ ] **Step 3: Implement screen shell and transparent rendering**

Use a page enum:

```java
private enum Page { GENERAL, AIM, TARGET, BALLISTICS, KEYS, HUD }
```

Render a translucent background and panel, not the default opaque dirt background:

```java
@Override
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    graphics.fill(0, 0, width, height, 0x55000000);
    graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB0101010);
    graphics.fill(panelLeft, panelTop, panelRight, panelTop + 24, 0xC0202020);
    super.render(graphics, mouseX, mouseY, partialTick);
}
```

Do not call `renderBackground()` if it produces an opaque background in this Minecraft version.

- [ ] **Step 4: Implement six pages with shared config controls**

Pages and minimum controls:

`总体`
- 自瞄总开关
- 自瞄方式
- 长按触发时间（毫秒）

`自瞄`
- 追踪速度
- 甩枪速度
- 松开回正速度
- 平滑子步数
- FOV

`目标`
- 最大触发距离（方块），range 4..300
- 仅可见目标
- 目标粘滞
- 切换迟滞
- 目标类型
- 瞄准点
- 自定义瞄准高度

`弹道`
- 提前量
- 手动弹速
- 阻力
- 最大预测时间
- 重力补偿
- 重力
- 高级弹道
- TACZ 自动弹道
- TACZ 实弹校准
- 目标加速度预测
- 速度/加速度平滑
- 目标最大速度/加速度
- 继承自身速度
- 锁定后重扫间隔

`快捷键`
- four rows: `快捷键 N：[绑定按钮]  触发模式：[模式按钮]`
- Backspace clear help text
- Escape cancel help text

`HUD`
- HUD 总显示
- FOV 圆环
- 弹道 HUD

Use a scroll offset for pages whose controls exceed the visible panel height. Mouse wheel changes offset by one row and clamps to content bounds.

- [ ] **Step 5: Implement keyboard/mouse binding capture**

Required behavior:

```java
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
```

`setTriggerBinding` must update the `KeyMapping` via Minecraft's mapping API and persist through the normal options save path. Use the API available in 1.20.1 (`setKey(...)` and `KeyMapping.resetMapping()`); verify exact method names by compilation.

- [ ] **Step 6: Save config values on Done/Close**

On explicit Done and `onClose()`, write edited local values back to `ForgeConfigSpec` values and call the config save path if available through the loaded client config. If direct save API is unavailable, rely on Forge value mutation and confirm persistence in a manual client run later; do not invent an unsupported API.

- [ ] **Step 7: Add complete translations**

At minimum add Chinese keys for:
- page names
- master switch
- aim behavior labels
- trigger mode labels
- `请设置快捷键`
- `按任意键或鼠标按钮...`
- `Backspace 清除，Esc 取消`
- `未绑定`
- `最大触发距离（方块）`
- `长按触发时间（毫秒）`
- `平滑追踪`, `瞬间锁定`, `甩枪并回正`
- `按住生效`, `单击切换`, `长按生效`
- duplicate-binding warning text
- all existing config labels retained in Chinese

Mirror the same keys in `en_us.json` with concise English fallback text.

- [ ] **Step 8: Run tests and compile**

Run: `gradle logicTest compileJava processResources --stacktrace --no-daemon`

Expected: PASS.

- [ ] **Step 9: Commit**

Commit message: `feat: add transparent in-game config panel`

---

### Task 5: Integrate unified activation with aim behaviors

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/AimHandler.java`

**Interfaces:**
- Own one `private static final AimActivationController ACTIVATION = new AimActivationController();`.
- `isAimActive()` reports the controller/behavior execution state.
- `resetAll()` resets controller and target tracking.

- [ ] **Step 1: Add behavior-selection testable helper contract**

Add a pure helper to `AimHandler`:

```java
static boolean shouldReturnAfterRelease(Config.AimBehavior behavior) {
    return behavior == Config.AimBehavior.FLICK_RETURN;
}
```

Add plain-main assertions for all three enum values.

- [ ] **Step 2: Run RED test**

Run: `gradle logicTest --stacktrace --no-daemon`

Expected: FAIL because helper/enum integration is not complete.

- [ ] **Step 3: Open config panel from Alt before aim processing**

In `onClientTick`, when `Keybindings.openConfigKey.consumeClick()` is true and no other screen is open:

```java
mc.setScreen(Config.createConfigScreen(mc, null));
ACTIVATION.reset();
clearAimState(false);
return;
```

If the config screen is already open, `onClose()` handles closing; do not let Alt simultaneously trigger any aim slot.

- [ ] **Step 4: Convert four KeyMappings to controller input**

For each slot, feed:
- `isDown()` for hold/long-hold state.
- `consumeClick()` for toggle edge.
- matching `Config.TRIGGER_MODE_N.get()`.

Deduplicate repeated physical bindings before passing `clicked=true`: compare each slot mapping's current key against earlier slots and only allow the first occurrence to contribute the click edge. This implements the spec rule that the same physical button bound twice toggles only once.

Call:

```java
boolean active = ACTIVATION.update(
        Config.MASTER_ENABLED.get(),
        mc.screen instanceof GunTrackerConfigScreen,
        System.currentTimeMillis(),
        Config.LONG_PRESS_MS.get(),
        slots
);
```

- [ ] **Step 5: Map `AimBehavior` to existing mechanics**

`SMOOTH_TRACK`
- While active: select/update target and call `aimAtCurrentTarget(player, Config.CONTINUOUS_SPEED.get(), false)`.
- On release: stop without view restoration.

`SNAP`
- While active: select/update target and call `aimAtCurrentTarget(player, 1.0D, true)`.
- On release: stop without view restoration.

`FLICK_RETURN`
- On transition inactive -> active: record original yaw/pitch once.
- While active: select/update target and call `aimAtCurrentTarget(player, Config.FLICK_SPEED.get(), Config.FLICK_SPEED.get() >= 0.999D)`.
- On transition active -> inactive: start existing return interpolation from current view to original view using `Config.FLICK_RETURN_SPEED`.
- Do not start return when forced reset is caused by leaving strict singleplayer or opening config; those paths clear state immediately.

Delete/retire the old direct `toggleAimKey`/`flickKey` state machine after equivalent behavior is covered by the new controller.

- [ ] **Step 6: Preserve strict singleplayer safety behavior**

Before executing activation, retain:

```java
if (!isStrictSingleplayer(mc)) {
    resetAll();
    return;
}
```

If panel key is pressed outside strict singleplayer, still allow the configuration screen to open because editing settings is harmless; only aim execution remains disabled.

- [ ] **Step 7: Run logic tests and compile**

Run: `gradle logicTest compileJava --stacktrace --no-daemon`

Expected: PASS.

- [ ] **Step 8: Commit**

Commit message: `feat: integrate configurable aim activation modes`

---

### Task 6: Update HUD status and complete Chinese UX

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/AimHud.java`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json`

**Interfaces:**
- HUD reads `Config.MASTER_ENABLED`, `Config.AIM_BEHAVIOR`, and `AimHandler.isAimActive()`.

- [ ] **Step 1: Add HUD state translations**

Chinese strings must distinguish:
- `总开关：关`
- `待机`
- `搜索目标`
- `锁定：<名称>  距离：<数字> 方块`
- `模式：平滑追踪/瞬间锁定/甩枪并回正`

- [ ] **Step 2: Update HUD rendering**

If master is disabled and HUD is enabled, show master-off state rather than `待机`. When active, append/stack the current behavior label below lock status. Keep ballistic HUD placement from overlapping by incrementing `ballisticY`.

- [ ] **Step 3: Keep distance unit consistent**

Continue using `mc.player.distanceTo(target)` and display the unit as `方块`, matching the 4..300 config.

- [ ] **Step 4: Run resource and Java compilation**

Run: `gradle compileJava processResources --stacktrace --no-daemon`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: localize aim status and mode hud`

---

### Task 7: Full build verification and cloud artifact

**Files:**
- Modify only if failures identify a concrete root cause.

**Interfaces:**
- GitHub Actions workflow remains `.github/workflows/build.yml` and runs `gradle clean build --stacktrace --no-daemon`.

- [ ] **Step 1: Run full local-capable Gradle verification**

Run: `gradle clean build --stacktrace --no-daemon`

Expected: exit code 0, including `logicTest` through `check`.

If local Gradle is unavailable, do not claim local success; proceed to Actions as the authoritative build environment.

- [ ] **Step 2: Push the final implementation commits to `main`**

A normal contents/commit update must trigger the workflow.

- [ ] **Step 3: Observe the GitHub Actions run**

Required successful steps:
- Checkout source
- Set up Java 17
- Set up Gradle 8.8
- Build
- Upload mod JAR

- [ ] **Step 4: If build fails, debug from logs before editing**

Read the failed job log, identify the exact first root-cause error, make one minimal fix, and trigger a new run. Do not batch speculative fixes.

- [ ] **Step 5: Verify artifact**

Confirm the successful run exposes a non-expired artifact named `gun-tracker-forge-1.20.1` containing `build/libs/*.jar`.

- [ ] **Step 6: Final requirement checklist**

Verify against the spec:
- Alt opens transparent settings panel.
- master switch exists.
- 4 trigger slots exist.
- keyboard and mouse binding capture exists.
- Backspace clears and Escape cancels capture.
- each slot has hold/toggle/long-hold mode.
- duplicate physical bindings do not double-toggle.
- aim behavior is independent of trigger slot.
- maximum activation distance reaches exactly 300 blocks.
- all new UI strings have Chinese translations.
- strict singleplayer restriction remains intact.
- GitHub Actions build is green.
- JAR artifact exists.

- [ ] **Step 7: Final commit only if verification changes were required**

Use a message that names the actual verified fix; do not create a no-op commit.
