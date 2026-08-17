package com.vayuclient.ui.platform;

import net.minecraft.client.gui.screens.Screen;

public interface IScreenNavigationAdapter {
    void openSingleplayer(Screen parent);
    void openMultiplayer(Screen parent);
    void openOptions(Screen parent);
    void openModsConfig(Screen parent);
    void disconnectToTitle();
}
