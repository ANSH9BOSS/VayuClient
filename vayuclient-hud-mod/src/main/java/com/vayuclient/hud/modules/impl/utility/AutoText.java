/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.InputConstants
 *  com.mojang.blaze3d.platform.Window
 */
package com.vayuclient.hud.modules.impl.utility;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.KeybindSetting;
import com.vayuclient.hud.modules.settings.TextSetting;

public class AutoText
extends Module {
    private final TextSetting text1 = this.register(new TextSetting("message_1", "First quick message", "GG!"));
    private final TextSetting text2 = this.register(new TextSetting("message_2", "Second quick message", "/home"));
    private final TextSetting text3 = this.register(new TextSetting("message_3", "Third quick message", "Nice shot!"));
    private final KeybindSetting key1 = this.register(new KeybindSetting("key_1", "Key for message 1", 321));
    private final KeybindSetting key2 = this.register(new KeybindSetting("key_2", "Key for message 2", 322));
    private final KeybindSetting key3 = this.register(new KeybindSetting("key_3", "Key for message 3", 323));
    private boolean[] keyWasPressed = new boolean[3];

    public AutoText() {
        super("AutoText", "Send predefined messages quickly with keybinds (Numpad 1-3 by default)", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        this.keyWasPressed = new boolean[3];
    }

    @Override
    public void onTick() {
        boolean pressed;
        if (!this.isInGame() || AutoText.mc.gui.screen() != null) {
            return;
        }
        Window window = mc.getWindow();
        if ((Integer)this.key1.getValue() != 0) {
            pressed = InputConstants.isKeyDown((Window)window, (int)((Integer)this.key1.getValue()));
            if (pressed && !this.keyWasPressed[0]) {
                this.sendText((String)this.text1.getValue());
            }
            this.keyWasPressed[0] = pressed;
        }
        if ((Integer)this.key2.getValue() != 0) {
            pressed = InputConstants.isKeyDown((Window)window, (int)((Integer)this.key2.getValue()));
            if (pressed && !this.keyWasPressed[1]) {
                this.sendText((String)this.text2.getValue());
            }
            this.keyWasPressed[1] = pressed;
        }
        if ((Integer)this.key3.getValue() != 0) {
            pressed = InputConstants.isKeyDown((Window)window, (int)((Integer)this.key3.getValue()));
            if (pressed && !this.keyWasPressed[2]) {
                this.sendText((String)this.text3.getValue());
            }
            this.keyWasPressed[2] = pressed;
        }
    }

    private void sendText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (mc.getConnection() != null) {
            if (text.startsWith("/")) {
                mc.getConnection().sendCommand(text.substring(1));
            } else {
                mc.getConnection().sendChat(text);
            }
        }
    }
}

