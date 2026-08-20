/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.hud;

import java.text.SimpleDateFormat;
import java.util.Date;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;

public class ChatTimestamps
extends Module {
    private final ModeSetting format = this.register(new ModeSetting("format", "Timestamp format", "HH:mm", new String[]{"HH:mm", "HH:mm:ss", "hh:mm a", "hh:mm:ss a", "[HH:mm]", "[hh:mm a]"}));
    private final BooleanSetting brackets = this.register(new BooleanSetting("brackets", "Wrap timestamp in brackets", true));
    private final BooleanSetting useCustomColor = this.register(new BooleanSetting("custom_color", "Use custom timestamp color", true));
    private final ColorSetting timestampColor = this.register(new ColorSetting("color", "Timestamp text color", 170, 170, 170));
    private final BooleanSetting seconds = this.register(new BooleanSetting("show_seconds", "Show seconds in timestamp", false));
    private static ChatTimestamps instance;

    public ChatTimestamps() {
        super("ChatTimestamps", "Add timestamps to chat messages", Category.HUD);
        this.timestampColor.visibleWhen(this.useCustomColor::isEnabled);
        instance = this;
    }

    public static ChatTimestamps getInstance() {
        return instance;
    }

    public String getTimestamp() {
        String formatStr = (String)this.format.getValue();
        if (this.seconds.isEnabled() && !formatStr.contains(":ss")) {
            formatStr = formatStr.replace(":mm", ":mm:ss");
        }
        SimpleDateFormat sdf = new SimpleDateFormat(formatStr);
        Object time = sdf.format(new Date());
        if (this.brackets.isEnabled() && !formatStr.startsWith("[")) {
            time = "[" + (String)time + "]";
        }
        return (String)time + " ";
    }

    public String getColorPrefix() {
        if (!this.useCustomColor.isEnabled()) {
            return "\u00a77";
        }
        int r = this.timestampColor.getRed();
        int g = this.timestampColor.getGreen();
        int b = this.timestampColor.getBlue();
        float brightness = (float)(r + g + b) / 3.0f / 255.0f;
        if (r > 200 && g < 100 && b < 100) {
            return "\u00a7c";
        }
        if (r > 200 && g > 100 && g < 200 && b < 100) {
            return "\u00a76";
        }
        if (r > 200 && g > 200 && b < 100) {
            return "\u00a7e";
        }
        if (r < 100 && g > 200 && b < 100) {
            return "\u00a7a";
        }
        if (r < 100 && g > 200 && b > 200) {
            return "\u00a7b";
        }
        if (r < 100 && g < 200 && b > 200) {
            return "\u00a79";
        }
        if (r > 200 && g < 100 && b > 200) {
            return "\u00a7d";
        }
        if (r > 200 && g > 200 && b > 200) {
            return "\u00a7f";
        }
        if ((double)brightness < 0.3) {
            return "\u00a78";
        }
        if ((double)brightness < 0.6) {
            return "\u00a77";
        }
        return "\u00a77";
    }

    public String getFormattedTimestamp() {
        return this.getColorPrefix() + this.getTimestamp() + "\u00a7r";
    }

    public int getTimestampRGB() {
        return this.timestampColor.getRGB() | 0xFF000000;
    }

    public boolean useCustomColorRendering() {
        return this.useCustomColor.isEnabled();
    }

    @Override
    public boolean isHudVisible() {
        return false;
    }
}

