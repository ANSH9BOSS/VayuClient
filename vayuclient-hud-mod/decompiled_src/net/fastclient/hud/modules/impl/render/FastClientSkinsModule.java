/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.appearance.PlayerAppearanceService;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;

public final class FastClientSkinsModule
extends Module {
    private static FastClientSkinsModule instance;

    public FastClientSkinsModule() {
        super("FastClientSkins", "FastClient Skins & Capes", "Show FastClient skins and capes on FastClient players", Category.RENDER);
        instance = this;
        this.setEnabled(true);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    protected void onEnable() {
        PlayerAppearanceService.getInstance().setEnabled(true);
    }

    @Override
    protected void onDisable() {
        PlayerAppearanceService.getInstance().setEnabled(false);
    }
}

