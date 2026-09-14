from pathlib import Path
import json

ROOT = Path('.')
java_path = ROOT / 'src/main/java/com/crispywafer/crispywaferguntracker/GunTrackerConfigScreen.java'
zh_path = ROOT / 'src/main/resources/assets/crispywaferguntrackermod/lang/zh_cn.json'
en_path = ROOT / 'src/main/resources/assets/crispywaferguntrackermod/lang/en_us.json'


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    a = text.index(start)
    b = text.index(end, a)
    return text[:a] + replacement.rstrip() + '\n\n' + text[b:]


text = java_path.read_text(encoding='utf-8')
text = text.replace(
    '    private int scrollRows;\n    private int capturingSlot = -1;\n',
    '    private static final int CAPTURE_NONE = -1;\n'
    '    private static final int CAPTURE_MASTER = -2;\n\n'
    '    private int scrollRows;\n'
    '    private int capturingSlot = CAPTURE_NONE;\n'
)
text = text.replace('                    capturingSlot = -1;\n', '                    capturingSlot = CAPTURE_NONE;\n')

text = replace_between(
    text,
    '    private void buildAimPage() {',
    '    private void buildTargetPage() {',
    '''    private void buildAimPage() {
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
    }'''
)

text = replace_between(
    text,
    '    private void buildTargetPage() {',
    '    private void buildBallisticsPage() {',
    '''    private void buildTargetPage() {
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
    }'''
)

text = replace_between(
    text,
    '    private void buildKeysPage() {',
    '    private void buildHudPage() {',
    '''    private void buildKeysPage() {
        addMasterBindingRow(0);
        for (int slot = 0; slot < Keybindings.TRIGGER_SLOT_COUNT; slot++) {
            addTriggerRow(slot + 1, slot);
        }
    }'''
)

insert_marker = '    private void addToggleRow(int row, String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {'
helpers = '''    private void addMasterBindingRow(int row) {
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

'''
text = text.replace(insert_marker, helpers + insert_marker)

text = text.replace(
    '        for (int i = 0; i < Keybindings.TRIGGER_SLOT_COUNT; i++) {\n            if (i == slot) continue;\n',
    '        KeyMapping master = Keybindings.masterToggleKey;\n'
    '        if (master != null && !master.isUnbound() && current.getKey().equals(master.getKey())) return true;\n'
    '        for (int i = 0; i < Keybindings.TRIGGER_SLOT_COUNT; i++) {\n'
    '            if (i == slot) continue;\n'
)

text = text.replace(
    '    private Component targetModeValue() {',
    '''    private Component antiBotModeValue() {
        String name = Config.ANTI_BOT_MODE.get().name().toLowerCase(Locale.ROOT);
        return Component.translatable("crispywaferguntrackermod.config.anti_bot_mode." + name);
    }

    private Component targetModeValue() {'''
)

text = replace_between(
    text,
    '    private int totalRows() {',
    '    private void clampScroll() {',
    '''    private int totalRows() {
        return switch (page) {
            case GENERAL -> 3;
            case AIM -> switch (Config.AIM_BEHAVIOR.get()) {
                case SMOOTH_TRACK, SNAP -> 3;
                case FLICK_RETURN -> 7;
            };
            case TARGET -> 15;
            case BALLISTICS -> 16;
            case KEYS -> 5;
            case HUD -> 3;
        };
    }'''
)

text = replace_between(
    text,
    '    @Override\n    public boolean keyPressed',
    '    private void saveAndClose() {',
    '''    @Override
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

    private void saveAndClose() {'''
)

java_path.write_text(text, encoding='utf-8')

zh = json.loads(zh_path.read_text(encoding='utf-8'))
zh.update({
    'key.crispywaferguntrackermod.master_toggle': '切换自瞄总开关',
    'crispywaferguntrackermod.config.master_toggle_binding': '自瞄总开关快捷键',
    'crispywaferguntrackermod.config.view_stability': '视角稳定度',
    'crispywaferguntrackermod.config.smooth_follow_gain': '平滑追踪跟随速度',
    'crispywaferguntrackermod.config.smooth_max_turn': '平滑追踪最大转向',
    'crispywaferguntrackermod.config.snap_follow_gain': '瞬间锁定后续跟随速度',
    'crispywaferguntrackermod.config.snap_max_turn': '瞬间锁定后续最大转向',
    'crispywaferguntrackermod.config.flick_initial_gain': '甩枪快速阶段速度',
    'crispywaferguntrackermod.config.flick_initial_max_turn': '甩枪快速阶段最大转向',
    'crispywaferguntrackermod.config.flick_track_gain': '甩枪持续跟随速度',
    'crispywaferguntrackermod.config.flick_track_max_turn': '甩枪持续跟随最大转向',
    'crispywaferguntrackermod.config.return_gain': '回正速度',
    'crispywaferguntrackermod.config.return_max_turn': '回正最大转向',
    'crispywaferguntrackermod.config.unlock_fov': '脱锁 FOV',
    'crispywaferguntrackermod.config.target_players': '锁定玩家',
    'crispywaferguntrackermod.config.target_hostiles': '锁定敌对怪物',
    'crispywaferguntrackermod.config.target_others': '锁定其他生物',
    'crispywaferguntrackermod.config.all_targets': '全体可锁定实体',
    'crispywaferguntrackermod.config.anti_bot_mode': '防假人模式',
    'crispywaferguntrackermod.config.anti_bot_mode.off': '关闭',
    'crispywaferguntrackermod.config.anti_bot_mode.standard': '标准过滤',
    'crispywaferguntrackermod.config.anti_bot_mode.strict': '严格过滤',
    'crispywaferguntrackermod.config.stickiness': '粘滞强度',
    'crispywaferguntrackermod.config.switch_confirm_ticks': '切换确认时间',
    'crispywaferguntrackermod.config.invisible_tolerance_ticks': '不可见容忍时间',
    'crispywaferguntrackermod.config.candidate_scan_interval': '候选扫描间隔',
    'crispywaferguntrackermod.unit.degree_per_tick': '度/刻'
})
zh_path.write_text(json.dumps(zh, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

en = json.loads(en_path.read_text(encoding='utf-8'))
en.update({
    'key.crispywaferguntrackermod.master_toggle': 'Toggle Aim Master Switch',
    'crispywaferguntrackermod.config.master_toggle_binding': 'Aim Master Toggle Hotkey',
    'crispywaferguntrackermod.config.view_stability': 'View Stability',
    'crispywaferguntrackermod.config.smooth_follow_gain': 'Smooth Follow Gain',
    'crispywaferguntrackermod.config.smooth_max_turn': 'Smooth Max Turn',
    'crispywaferguntrackermod.config.snap_follow_gain': 'Snap Follow Gain',
    'crispywaferguntrackermod.config.snap_max_turn': 'Snap Follow Max Turn',
    'crispywaferguntrackermod.config.flick_initial_gain': 'Flick Fast-Stage Gain',
    'crispywaferguntrackermod.config.flick_initial_max_turn': 'Flick Fast-Stage Max Turn',
    'crispywaferguntrackermod.config.flick_track_gain': 'Flick Tracking Gain',
    'crispywaferguntrackermod.config.flick_track_max_turn': 'Flick Tracking Max Turn',
    'crispywaferguntrackermod.config.return_gain': 'Return Gain',
    'crispywaferguntrackermod.config.return_max_turn': 'Return Max Turn',
    'crispywaferguntrackermod.config.unlock_fov': 'Unlock FOV',
    'crispywaferguntrackermod.config.target_players': 'Target Players',
    'crispywaferguntrackermod.config.target_hostiles': 'Target Hostile Mobs',
    'crispywaferguntrackermod.config.target_others': 'Target Other Living Entities',
    'crispywaferguntrackermod.config.all_targets': 'All Targetable Living Entities',
    'crispywaferguntrackermod.config.anti_bot_mode': 'Anti-Bot Mode',
    'crispywaferguntrackermod.config.anti_bot_mode.off': 'Off',
    'crispywaferguntrackermod.config.anti_bot_mode.standard': 'Standard Filter',
    'crispywaferguntrackermod.config.anti_bot_mode.strict': 'Strict Filter',
    'crispywaferguntrackermod.config.stickiness': 'Stickiness',
    'crispywaferguntrackermod.config.switch_confirm_ticks': 'Switch Confirmation Time',
    'crispywaferguntrackermod.config.invisible_tolerance_ticks': 'Invisible Tolerance',
    'crispywaferguntrackermod.config.candidate_scan_interval': 'Candidate Scan Interval',
    'crispywaferguntrackermod.unit.degree_per_tick': 'deg/tick'
})
en_path.write_text(json.dumps(en, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

print('task4 patch applied')
