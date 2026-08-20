package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;
import java.util.Collections;
import java.util.Map;

public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }

    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() {
        return Collections.emptyMap();
    }
}
