# Gun Tracker Stable Lock, Target Filters, and HUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make target lock stable and non-jittery, make the three aim modes visibly distinct, add combinable target categories plus anti-bot filtering, and add a draggable always-on status HUD with a master-toggle key.

**Architecture:** Keep TACZ ballistic solving intact. Move target-switch policy and view smoothing into small testable helpers, let `TargetSelector` own lock/candidate state, let `AimHandler` own mode-specific camera state, and keep UI/HUD persistence in Forge CLIENT config.

**Tech Stack:** Java 17, Minecraft Forge 1.20.1 / Forge 47.3.22, Gradle 8.8, Parchment mappings, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-14-stable-lock-target-filter-hud-design.md`

## Global Constraints

- Use the user-uploaded baseline currently on `main`; do not restore the old strict-singleplayer restriction.
- Preserve TACZ ballistic prediction/calibration and the four existing trigger slots.
- Preserve maximum target distance range `4..300` blocks.
- Target categories are independent booleans: players default ON, hostile monsters default ON, other living entities default OFF.
- Armor stands are always rejected.
- Anti-bot modes are OFF / STANDARD / STRICT, default STANDARD.
- Stickiness is `0..100`, default `85`; switch confirmation default `12` ticks; candidate scan interval default `3` ticks; invisible tolerance default `10` ticks; unlock FOV default `110°`.
- HUD status box defaults visible and remains visible when master aim is off.
- Master-toggle key defaults unbound and supports keyboard/mouse rebinding with Backspace clear / Escape cancel.
- Every user-facing new string must exist in both `zh_cn.json` and `en_us.json`.
- Final verification command is `gradle clean build --stacktrace --no-daemon`, followed by a successful GitHub Actions build and artifact upload.

---

### Task 1: CI branch coverage and pure lock/view logic

**Files:**
- Modify: `.github/workflows/build.yml`
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/TargetLockPolicy.java`
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/AimViewMath.java`
- Create: `src/test/java/com/crispywafer/crispywaferguntracker/StableAimLogicTestMain.java`
- Modify: `build.gradle`

**Interfaces:**
- Produces: `TargetLockPolicy.switchMargin(int): double`
- Produces: `TargetLockPolicy.shouldChallenge(double currentScore, double candidateScore, int stickiness): boolean`
- Produces: `TargetLockPolicy.advanceConfirmation(boolean sameCandidate, boolean stillBetter, int previousTicks, int elapsedTicks): int`
- Produces: `TargetLockPolicy.confirmed(int confirmedTicks, int requiredTicks): boolean`
- Produces: `AimViewMath.filterAngle(double previous, double sample, int stability): double`
- Produces: `AimViewMath.stepAngle(double current, double target, double gain, double maxDegrees): double`
- Produces: `AimViewMath.stepLinear(double current, double target, double gain, double maxDegrees): double`

- [ ] **Step 1: Enable Actions for the feature branch and make all logic mains run under `check`.**

Add `feature/stable-lock-target-filter-hud` to the workflow branch list. Replace the single JavaExec coverage with three JavaExec tasks (`activationLogicTest`, `aimMathLogicTest`, `stableAimLogicTest`) and make `check` depend on all three.

- [ ] **Step 2: Write `StableAimLogicTestMain` before implementation.**

It must assert at least:

```java
assertClose(42.8, TargetLockPolicy.switchMargin(85), 1.0e-9, "85 stickiness margin");
assertFalse(TargetLockPolicy.shouldChallenge(100.0, 60.0, 85), "40-point lead is not enough at 85");
assertTrue(TargetLockPolicy.shouldChallenge(100.0, 50.0, 85), "50-point lead is enough at 85");
assertEquals(6, TargetLockPolicy.advanceConfirmation(true, true, 3, 3), "same candidate accumulates");
assertEquals(0, TargetLockPolicy.advanceConfirmation(true, false, 9, 3), "lost advantage resets");
assertTrue(TargetLockPolicy.confirmed(12, 12), "12 ticks confirms");
assertClose(-179.0, AimViewMath.filterAngle(179.0, -177.0, 70), 1.0, "yaw filter uses wrapped angle");
assertClose(10.0, AimViewMath.stepAngle(0.0, 90.0, 1.0, 10.0), 1.0e-9, "turn cap applies");
```

- [ ] **Step 3: Push tests and verify RED in GitHub Actions.**

Expected failure: compilation errors for missing `TargetLockPolicy` / `AimViewMath`.

- [ ] **Step 4: Implement the minimal pure helpers.**

`TargetLockPolicy.switchMargin` is exactly:

```java
return 2.0D + 0.48D * Math.max(0, Math.min(100, stickiness));
```

`shouldChallenge` is `candidateScore + switchMargin(stickiness) < currentScore`.

`advanceConfirmation` returns `0` if `!sameCandidate || !stillBetter`; otherwise `previousTicks + Math.max(1, elapsedTicks)`.

`AimViewMath.filterAngle` uses `AimMath.normalizeDegrees(sample - previous)`, with `alpha = 0.65 - 0.50 * clamp(stability / 100.0)`, then returns `previous + delta * alpha`.

`stepAngle` uses wrapped delta, multiplies by gain, clamps magnitude to `maxDegrees`, and returns `current + step`. `stepLinear` does the same without angle wrapping.

- [ ] **Step 5: Run/push until all three logic mains and full Forge build are GREEN.**

- [ ] **Step 6: Commit.**

Commit message: `test: add stable lock and view math contracts`.

---

### Task 2: Config model and sticky target selector

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Config.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/TargetSelector.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/AimMath.java`
- Modify: `src/test/java/com/crispywafer/crispywaferguntracker/StableAimLogicTestMain.java`

**Interfaces:**
- Produces config booleans: `TARGET_PLAYERS`, `TARGET_HOSTILES`, `TARGET_OTHERS`
- Produces enum/config: `AntiBotMode { OFF, STANDARD, STRICT }`, `ANTI_BOT_MODE`
- Produces config values: `STICKINESS`, `SWITCH_CONFIRM_TICKS`, `CANDIDATE_SCAN_INTERVAL`, `INVISIBLE_TOLERANCE_TICKS`, `UNLOCK_FOV_DEGREES`
- `TargetSelector.selectTarget(Player)` remains the public update entry point.
- `TargetSelector.getTarget()` and `getTargetAimPos(Player)` remain compatible.

- [ ] **Step 1: Extend failing config-contract tests.**

Assert defaults/ranges via constants: stickiness `0..100`, confirm `0..60`, scan `1..10`, invisible `0..60`, unlock FOV `5..180`, default anti-bot enum `STANDARD` through the config declaration contract where practical.

- [ ] **Step 2: Implement config declarations while keeping old `TARGET_MODE`, `STICKY_TARGET`, and `SWITCH_HYSTERESIS` only as compatibility keys.**

New UI/runtime logic must use the new fields, not the old enum/boolean.

- [ ] **Step 3: Refactor target eligibility into acquisition vs retention.**

Add methods with these responsibilities:

```java
private boolean isAcquisitionTarget(Player player, LivingEntity entity)
private boolean isRetainedTarget(Player player, LivingEntity entity)
private boolean matchesEnabledCategory(LivingEntity entity)
private boolean passesAntiBot(Player player, LivingEntity entity)
```

Rules:
- reject self, dead, removed, and `ArmorStand` always;
- players require `TARGET_PLAYERS`; monsters require `TARGET_HOSTILES`; remaining `LivingEntity` require `TARGET_OTHERS`;
- acquisition uses search FOV and immediate visibility requirement;
- retention uses unlock FOV and invisible-tolerance state;
- distance always uses `MAX_DISTANCE`.

- [ ] **Step 4: Implement STANDARD/STRICT player filtering.**

STANDARD: for `Player`, require `Minecraft.getInstance().getConnection()` and a `PlayerInfo` found by UUID.

STRICT: STANDARD plus profile consistency. Reject immediately for nil/empty UUID or a definite UUID mismatch. Count soft anomalies (blank/invalid name, profile-name mismatch, missing expected profile information) and reject when at least two soft anomalies are present. Do not use AFK/movement behavior.

- [ ] **Step 5: Replace periodic immediate switching with sticky candidate confirmation.**

State in `TargetSelector`:

```java
@Nullable private LivingEntity candidateTarget;
private int candidateTicks;
private int invisibleTicks;
private int lastCandidateScanTick = Integer.MIN_VALUE;
```

Behavior:
- no current target: scan/acquire normally;
- current invalid/dead/out-of-range/outside unlock FOV/category/anti-bot: clear and reacquire on next scan;
- current temporarily invisible: increment `invisibleTicks`, retain until `INVISIBLE_TOLERANCE_TICKS` exceeded;
- current valid: only scan replacements every `CANDIDATE_SCAN_INTERVAL` ticks;
- replacement must pass acquisition filters and `TargetLockPolicy.shouldChallenge`;
- the same candidate accumulates confirmation by scan interval; different/lost candidate resets;
- switch only after `SWITCH_CONFIRM_TICKS`.

- [ ] **Step 6: Remove the old current-target score bonus from runtime scoring.**

Keep `AimMath.targetScore` compatibility if useful, but `TargetSelector` must call it with no sticky bonus so stickiness is only controlled by the new policy.

- [ ] **Step 7: Push and require full build GREEN.**

Commit message: `feat: add sticky lock target filtering`.

---

### Task 3: Distinct aim modes and master-toggle key

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Config.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Keybindings.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/AimHandler.java`
- Modify: `src/test/java/com/crispywafer/crispywaferguntracker/StableAimLogicTestMain.java`

**Interfaces:**
- Produces `Keybindings.masterToggleKey` as an unbound `KeyMapping`.
- Produces aim config values: `VIEW_STABILITY`, `SMOOTH_FOLLOW_GAIN`, `SMOOTH_MAX_TURN`, `SNAP_FOLLOW_GAIN`, `SNAP_MAX_TURN`, `FLICK_INITIAL_GAIN`, `FLICK_INITIAL_MAX_TURN`, `FLICK_TRACK_GAIN`, `FLICK_TRACK_MAX_TURN`, `RETURN_GAIN`, `RETURN_MAX_TURN`.

- [ ] **Step 1: Add failing tests for view-math defaults/contracts and mode transition helpers.**

Add pure assertions that `stepAngle` wraps across ±180 correctly, max-turn caps work, and linear pitch stepping caps correctly.

- [ ] **Step 2: Add config values with exact defaults from the spec.**

Defaults:
- stability `70`
- smooth gain `0.28`, max turn `10`
- snap follow gain `0.55`, max turn `18`
- flick initial gain `0.75`, max turn `35`
- flick tracking gain `0.40`, max turn `15`
- return gain `0.35`, max turn `30`

- [ ] **Step 3: Register `masterToggleKey`.**

Use `GLFW_KEY_UNKNOWN`, register it with the other mappings, and keep the four trigger slots unchanged.

- [ ] **Step 4: Make `AimHandler` consume the master-toggle key before trigger activation.**

When pressed with no screen open:

```java
boolean next = !Config.MASTER_ENABLED.get();
Config.MASTER_ENABLED.set(next);
Config.CLIENT_SPEC.save();
if (!next) resetAll();
```

Opening config still resets active aim state. Re-enabling master does not reactivate stale toggle/hold state.

- [ ] **Step 5: Replace single-tick substep aiming with persistent filtered target angles.**

Maintain filtered yaw/pitch state and reset it when the actual target reference changes. Convert predicted position to raw target angles, pass through `AimViewMath.filterAngle`, then apply the selected mode.

- [ ] **Step 6: Implement visibly distinct modes.**

SMOOTH: every tick `stepAngle/stepLinear` using smooth gain/max turn.

SNAP: when target reference changes from null/other target, set yaw/pitch directly once; subsequent ticks use snap follow gain/max turn.

FLICK_RETURN: on activation edge save original yaw/pitch. During acquisition/large error use flick initial gain/max turn; once total error <= `1.0°`, use tracking gain/max turn. On release stop target selection and step back to original yaw/pitch using return gain/max turn; finish under `0.25°` by writing exact original angles.

- [ ] **Step 7: Push and require logic tests + full Forge build GREEN.**

Commit message: `feat: differentiate aim modes and add master toggle`.

---

### Task 4: Rework in-game settings and translations

**Files:**
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json`

**Interfaces:**
- Consumes all new config/key fields from Tasks 2-3.
- Existing Backspace clear and Escape cancel behavior remains for trigger bindings and is extended to the master key binding.

- [ ] **Step 1: Target page uses independent toggles.**

Rows in order: distance, search FOV, unlock FOV, visible-only, players, hostiles, other living, all-targets toggle button, anti-bot mode, stickiness, switch-confirm ticks, invisible tolerance, candidate scan interval, aim point, custom height.

“All targets” button sets all three category booleans ON if any are off; otherwise sets all OFF.

- [ ] **Step 2: Aim page shows parameters that actually affect the selected mode.**

Always show stability. Then:
- SMOOTH: smooth gain + max turn;
- SNAP: snap follow gain + max turn;
- FLICK_RETURN: initial gain/max turn + tracking gain/max turn + return gain/max turn.

Do not present `AIM_SUBSTEPS` as an active tuning control for the new view system.

- [ ] **Step 3: Keys page adds the master-toggle binding above the four trigger slots.**

Use the same keyboard/mouse capture mechanism. Backspace clears it; Escape leaves the old binding unchanged.

- [ ] **Step 4: Add Chinese-primary and English-fallback strings.**

Required Chinese concepts include: 粘滞强度, 切换确认时间, 脱锁 FOV, 不可见容忍, 候选扫描间隔, 锁定玩家, 锁定敌对怪物, 锁定其他生物, 全体可锁定实体, 防假人关闭/标准/严格, 视角稳定度, 平滑最大转向, 瞬间锁定后续跟随, 甩枪快速阶段, 持续跟随, 回正, 切换自瞄总开关.

- [ ] **Step 5: Push and require full Forge build GREEN.**

Commit message: `feat: expose stable lock settings in game`.

---

### Task 5: Draggable persistent HUD

**Files:**
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/HudPositionMath.java`
- Create: `src/main/java/com/crispywafer/crispywaferguntracker/HudPositionScreen.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/Config.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/AimHud.java`
- Modify: `src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json`
- Modify: `src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json`
- Modify: `src/test/java/com/crispywafer/crispywaferguntracker/StableAimLogicTestMain.java`

**Interfaces:**
- Produces `HudPositionMath.clampNormalized(double): double`.
- Produces configs: `HUD_SHOW_MASTER`, `HUD_SHOW_MODE`, `HUD_SHOW_TARGET_NAME`, `HUD_SHOW_TARGET_DISTANCE`, `HUD_X_NORMALIZED`, `HUD_Y_NORMALIZED`.

- [ ] **Step 1: Write failing coordinate tests.**

```java
assertClose(0.0, HudPositionMath.clampNormalized(-0.5), 0.0, "hud x min");
assertClose(1.0, HudPositionMath.clampNormalized(1.5), 0.0, "hud x max");
assertClose(0.37, HudPositionMath.clampNormalized(0.37), 0.0, "hud unchanged");
```

- [ ] **Step 2: Add HUD config fields.**

Keep `SHOW_HUD` as the status-box master visibility switch. New line toggles default ON. Normalized X/Y default near center-bottom (for example `0.50`, `0.62`) and range `0..1`.

- [ ] **Step 3: Rebuild `AimHud` status box rendering.**

When `SHOW_HUD` is true, render a small semi-transparent rectangle at normalized coordinates. It must still render when `MASTER_ENABLED` is false. Compose only enabled lines:
- master state;
- mode;
- target name when locked;
- target distance when locked.

Keep FOV ring and ballistic HUD as independent options.

- [ ] **Step 4: Implement `HudPositionScreen`.**

It previews the same status box, records old normalized coordinates on entry, drags with left mouse, updates normalized values while dragging, saves on mouse release / Done, restores old coordinates on Escape, and has a reset-default button.

- [ ] **Step 5: Add HUD-page toggles and “Adjust HUD position” / “Reset position” controls.**

- [ ] **Step 6: Push and require full Forge build GREEN.**

Commit message: `feat: add draggable persistent aim hud`.

---

### Task 6: Regression review, final build, and main delivery

**Files:**
- Modify: `.github/workflows/build.yml` (remove temporary feature-branch trigger; leave `main` only unless an intentional permanent branch is desired)
- Potentially modify only files needed to fix verified regressions.

**Interfaces:**
- No new public interfaces; this task verifies the integrated system.

- [ ] **Step 1: Run fresh feature-branch verification.**

Require GitHub Actions to execute `gradle clean build --stacktrace --no-daemon` with all logic mains passing and artifact upload succeeding.

- [ ] **Step 2: Review the branch diff against the spec.**

Verify explicitly:
- sticky lock does not switch on one scan;
- 12-tick candidate confirmation works;
- three categories are combinable;
- armor stands are rejected;
- STANDARD/STRICT anti-bot paths exist;
- smooth/snap/flick-return have different defaults and state transitions;
- master toggle key is separate from four trigger keys;
- HUD remains visible when master is off and position is persistent;
- 300-block max remains unchanged;
- TACZ files/solver behavior were not removed.

- [ ] **Step 3: Restore workflow to minimal permanent branch triggers and run another clean feature build if the workflow edit itself changes behavior.**

- [ ] **Step 4: Fast-forward `main` to the verified feature tip.**

No force push. Then wait for the `main` push workflow.

- [ ] **Step 5: Verify the `main` Actions run is `completed/success`, with both Build and Upload mod JAR successful.**

- [ ] **Step 6: Download the exact `main` artifact, inspect ZIP/JAR contents, compute SHA-256, and deliver the installable JAR plus raw artifact ZIP.**

Commit message for workflow cleanup: `ci: restore main-only build trigger`.
