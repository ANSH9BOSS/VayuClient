/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.settings;

import java.awt.Color;
import net.fastclient.hud.modules.settings.Setting;

public class ColorSetting
extends Setting<Color> {
    public ColorSetting(String name, String description, int r, int g, int b) {
        super(name, description, new Color(r, g, b));
    }

    public ColorSetting(String name, String description, int r, int g, int b, int a) {
        super(name, description, new Color(r, g, b, a));
    }

    public int getRed() {
        return ((Color)this.getValue()).getRed();
    }

    public int getGreen() {
        return ((Color)this.getValue()).getGreen();
    }

    public int getBlue() {
        return ((Color)this.getValue()).getBlue();
    }

    public int getAlpha() {
        return ((Color)this.getValue()).getAlpha();
    }

    public int getRGB() {
        return ((Color)this.getValue()).getRGB();
    }

    public void setRGB(int r, int g, int b) {
        this.setValue(new Color(r, g, b, this.getAlpha()));
    }

    public void setRGBA(int r, int g, int b, int a) {
        this.setValue(new Color(r, g, b, a));
    }
}

