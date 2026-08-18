package com.vayuclient.ui.adapter;

import com.vayuclient.ui.platform.IScreenNavigationAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

public class ModernScreenNavigationAdapter implements IScreenNavigationAdapter {

    @Override
    public void openSingleplayer(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreenAndShow(new SelectWorldScreen(parent));
        }
    }

    @Override
    public void openMultiplayer(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreenAndShow(new JoinMultiplayerScreen(parent));
        }
    }

    @Override
    public void openOptions(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreenAndShow(new OptionsScreen(parent, mc.options, false));
        }
    }

    @Override
    public void openModsConfig(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        try {
            Class<?> configCls = Class.forName("com.ansh9boss.lungehelper.client.gui.LungeConfigScreen");
            var ctor = configCls.getConstructor(Screen.class);
            var screen = (Screen) ctor.newInstance(parent);
            mc.setScreenAndShow(screen);
        } catch (Throwable t) {
            System.out.println("[VayuClient UI] Mods config screen fallback: " + t.getMessage());
            openOptions(parent);
        }
    }

    @Override
    public void disconnectToTitle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            try {
                Class<?> titleCls = Class.forName("com.vayuclient.ui.gui.VayuTitleScreen");
                var screen = (Screen) titleCls.getDeclaredConstructor().newInstance();
                mc.disconnect(screen, false);
            } catch (Throwable t) {
                mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
            }
        }
    }
}
