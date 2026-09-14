package com.crispywafer.crispywaferguntracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = CrispyWaferGunTrackerMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Keybindings {
    public static final int TRIGGER_SLOT_COUNT = 4;

    public static KeyMapping openConfigKey;
    public static KeyMapping masterToggleKey;
    public static KeyMapping triggerKey1;
    public static KeyMapping triggerKey2;
    public static KeyMapping triggerKey3;
    public static KeyMapping triggerKey4;
    /** 强制移动物品（Shift+左键的替代） */
    public static KeyMapping forceMoveKey;
    /** 一键整理：把同种可堆叠物品合并到一起 */
    public static KeyMapping mergeKey;

    /** Compatibility aliases until AimHandler is migrated to the new activation controller. */
    @Deprecated public static KeyMapping toggleAimKey;
    @Deprecated public static KeyMapping flickKey;

    private Keybindings() {}

    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        openConfigKey = new KeyMapping(
                "key.invisiblekeybinding.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_ALT,
                "key.categories.invisiblekeybinding"
        );
        masterToggleKey = new KeyMapping(
                "key.invisiblekeybinding.master_toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.invisiblekeybinding"
        );
        triggerKey1 = new KeyMapping(
                "key.invisiblekeybinding.trigger_1",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                "key.categories.invisiblekeybinding"
        );
        triggerKey2 = unbound("key.invisiblekeybinding.trigger_2");
        triggerKey3 = unbound("key.invisiblekeybinding.trigger_3");
        triggerKey4 = unbound("key.invisiblekeybinding.trigger_4");
        forceMoveKey = new KeyMapping(
                "key.invisiblekeybinding.force_move",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.invisiblekeybinding"
        );

        event.register(openConfigKey);
        event.register(masterToggleKey);
        event.register(triggerKey1);
        event.register(triggerKey2);
        event.register(triggerKey3);
        event.register(triggerKey4);
        mergeKey = new KeyMapping(
                "key.invisiblekeybinding.merge_stacks",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "key.categories.invisiblekeybinding"
        );

        event.register(forceMoveKey);
        event.register(mergeKey);

        toggleAimKey = openConfigKey;
        flickKey = triggerKey1;
    }

    private static KeyMapping unbound(String translationKey) {
        return new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.invisiblekeybinding"
        );
    }

    public static KeyMapping triggerKey(int index) {
        return switch (index) {
            case 0 -> triggerKey1;
            case 1 -> triggerKey2;
            case 2 -> triggerKey3;
            case 3 -> triggerKey4;
            default -> throw new IndexOutOfBoundsException("trigger index " + index);
        };
    }
}
