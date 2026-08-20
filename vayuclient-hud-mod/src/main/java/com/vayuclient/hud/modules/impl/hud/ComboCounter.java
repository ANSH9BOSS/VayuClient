/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 */
package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ComboCounter
extends Module {
    private final NumberSetting timeout = this.register(new NumberSetting("timeout", "Seconds before combo resets", 2.0, 1.0, 5.0, 0.5));
    private final BooleanSetting resetOnHit = this.register(new BooleanSetting("reset_on_damage", "Reset combo when taking damage", true));
    private final BooleanSetting showLabel = this.register(new BooleanSetting("show_label", "Show 'Combo' text", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final ColorSetting lowColor = this.register(new ColorSetting("low_color", "Color for low combos (1-4)", 255, 255, 255));
    private final ColorSetting midColor = this.register(new ColorSetting("mid_color", "Color for medium combos (5-9)", 255, 255, 0));
    private final ColorSetting highColor = this.register(new ColorSetting("high_color", "Color for high combos (10+)", 255, 170, 0));
    private int combo = 0;
    private long lastHitTime = 0L;
    private float lastPlayerHealth = 20.0f;
    private float animatedCombo = 0.0f;
    private static ComboCounter instance;

    public ComboCounter() {
        super("ComboCounter", "Displays consecutive hit combo", Category.HUD);
        instance = this;
    }

    public static ComboCounter getInstance() {
        return instance;
    }

    @Override
    public void onTick() {
        if (!this.isInGame() || ComboCounter.mc.player == null) {
            return;
        }
        long timeoutMs = (long)((Double)this.timeout.getValue() * 1000.0);
        if (System.currentTimeMillis() - this.lastHitTime > timeoutMs) {
            this.combo = 0;
        }
        if (this.resetOnHit.isEnabled()) {
            float currentHealth = ComboCounter.mc.player.getHealth();
            if (currentHealth < this.lastPlayerHealth) {
                this.combo = 0;
            }
            this.lastPlayerHealth = currentHealth;
        }
        this.animatedCombo += ((float)this.combo - this.animatedCombo) * 0.3f;
    }

    public void onHit() {
        if (!this.isEnabled() || !this.isInGame()) {
            return;
        }
        ++this.combo;
        this.lastHitTime = System.currentTimeMillis();
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || this.combo == 0) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        Object text = this.showLabel.isEnabled() ? this.combo + " Combo" : String.valueOf(this.combo);
        int textWidth = ComboCounter.mc.font.width((String)text);
        int color = this.getComboColor();
        if (this.background.isEnabled()) {
            int bgWidth = textWidth + 10;
            int bgHeight = 17;
            VayuHUDUI.hudPanel(graphics, x - 5, y - 4, bgWidth, bgHeight);
            if (this.combo >= 10) {
                graphics.fill(x - 4, y - 3, x + bgWidth - 1, y - 1, color);
            }
        }
        long timeSinceHit = System.currentTimeMillis() - this.lastHitTime;
        int displayColor = color;
        if (timeSinceHit < 150L) {
            int r = Math.min(255, (color >> 16 & 0xFF) + 50);
            int g = Math.min(255, (color >> 8 & 0xFF) + 50);
            int b = Math.min(255, (color & 0xFF) + 50);
            displayColor = 0xFF000000 | r << 16 | g << 8 | b;
        }
        graphics.text(ComboCounter.mc.font, (String)text, x, y, displayColor, true);
        graphics.pose().popMatrix();
    }

    private int getComboColor() {
        if (this.combo >= 10) {
            return this.highColor.getRGB() | 0xFF000000;
        }
        if (this.combo >= 5) {
            return this.midColor.getRGB() | 0xFF000000;
        }
        return this.lowColor.getRGB() | 0xFF000000;
    }

    public int getCombo() {
        return this.combo;
    }

    public void resetCombo() {
        this.combo = 0;
    }

    @Override
    public int getHudWidth() {
        if (this.combo == 0) {
            return 60;
        }
        Object text = this.showLabel.isEnabled() ? this.combo + " Combo" : String.valueOf(this.combo);
        return ComboCounter.mc.font.width((String)text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

