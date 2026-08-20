/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;

public class NametagIconModule
extends Module {
    private static NametagIconModule instance;
    private final BooleanSetting showAboveHead = this.register(new BooleanSetting("show_above_head", "Show icon above players' heads", true));
    private final BooleanSetting showInTabList = this.register(new BooleanSetting("show_in_tab_list", "Show icon in the TAB player list", true));

    public NametagIconModule() {
        super("NametagIcon", "Show VayuClient logo next to VayuClient players", Category.RENDER);
        instance = this;
        this.setEnabled(true);
    }

    public static boolean shouldShowAboveHead() {
        return instance != null && instance.isEnabled() && NametagIconModule.instance.showAboveHead.isEnabled();
    }

    public static boolean shouldShowInTabList() {
        return instance != null && instance.isEnabled() && NametagIconModule.instance.showInTabList.isEnabled();
    }
}

