/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.utility;

import java.util.Locale;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.NumberSetting;
import com.vayuclient.hud.modules.settings.TextSetting;

public class AutoGG
extends Module {
    private final TextSetting message = this.register(new TextSetting("message", "Message to send", "gg"));
    private final NumberSetting delay = this.register(new NumberSetting("delay", "Delay before sending (seconds)", 1.0, 0.5, 5.0, 0.5));
    private static final String[] TRIGGERS = new String[]{"has won", "winner", "winners", "victory", "game over", "game ended", "1st place", "you win", "you lost", "you died", "top survivors", "won the game"};
    private static final int COOLDOWN_TICKS = 200;
    private int sendDelayTicks = -1;
    private int cooldownTicks = 0;

    public AutoGG() {
        super("AutoGG", "Automatically send GG when a game ends", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        this.sendDelayTicks = -1;
        this.cooldownTicks = 0;
    }

    public void onChatReceived(String text) {
        if (this.cooldownTicks > 0 || this.sendDelayTicks >= 0) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String trigger : TRIGGERS) {
            if (!lower.contains(trigger)) continue;
            this.sendDelayTicks = (int)((Double)this.delay.getValue() * 20.0);
            return;
        }
    }

    @Override
    public void onTick() {
        if (!this.isInGame()) {
            return;
        }
        if (this.cooldownTicks > 0) {
            --this.cooldownTicks;
        }
        if (this.sendDelayTicks >= 0) {
            --this.sendDelayTicks;
            if (this.sendDelayTicks < 0) {
                this.sendGG();
            }
        }
    }

    private void sendGG() {
        String text = (String)this.message.getValue();
        if (text == null || text.isEmpty() || mc.getConnection() == null) {
            return;
        }
        if (text.startsWith("/")) {
            mc.getConnection().sendCommand(text.substring(1));
        } else {
            mc.getConnection().sendChat(text);
        }
        this.cooldownTicks = 200;
    }
}

