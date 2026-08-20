/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.appearance.PlayerAppearanceService;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;

public final class VayuSkinsModule
extends Module {
    private static VayuSkinsModule instance;

    public VayuSkinsModule() {
        super("VayuClientSkins", "VayuClient Skins & Capes", "Show VayuClient skins and capes on VayuClient players", Category.RENDER);
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

