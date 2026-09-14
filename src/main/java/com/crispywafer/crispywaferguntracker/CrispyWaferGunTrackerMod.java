package com.crispywafer.crispywaferguntracker;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(CrispyWaferGunTrackerMod.MODID)
public final class CrispyWaferGunTrackerMod {
    public static final String MODID = "crispywaferguntrackermod";

    public CrispyWaferGunTrackerMod() {
        Config.register();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> Config.createConfigScreen(minecraft, screen)
                )
        );
    }
}
