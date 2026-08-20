package com.vayuclient.hud.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.vayuclient.hud.gui.screens.ClickGUIScreen;
import net.minecraft.client.gui.screens.Screen;

public class VayuModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (ConfigScreenFactory<Screen>) parent -> new ClickGUIScreen();
    }
}
