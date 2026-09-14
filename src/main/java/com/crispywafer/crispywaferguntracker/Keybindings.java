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
    public static KeyMapping toggleAimKey;
    public static KeyMapping flickKey;

    private Keybindings() {}

    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        toggleAimKey = new KeyMapping(
                "key.crispywaferguntrackermod.toggle_aim",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.crispywaferguntrackermod"
        );
        flickKey = new KeyMapping(
                "key.crispywaferguntrackermod.flick",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_5,
                "key.categories.crispywaferguntrackermod"
        );
        event.register(toggleAimKey);
        event.register(flickKey);
    }
}
