/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;

public class ScoreboardMod
extends Module {
    private final BooleanSetting hideScoreboard = this.register(new BooleanSetting("hide_scoreboard", "Hide scoreboard completely", false));
    private final BooleanSetting hideNumbers = this.register(new BooleanSetting("hide_numbers", "Hide score numbers", true));
    private final BooleanSetting customBackground = this.register(new BooleanSetting("custom_background", "Use custom background color", false));
    private final ColorSetting backgroundColor = this.register(new ColorSetting("background_color", "Background color", 0, 0, 0));
    private final NumberSetting backgroundOpacity = this.register(new NumberSetting("background_opacity", "Background opacity", 64.0, 0.0, 255.0, 1.0));

    public ScoreboardMod() {
        super("ScoreboardMod", "Customize the scoreboard appearance", Category.RENDER);
        this.hideNumbers.visibleWhen(() -> !this.hideScoreboard.isEnabled());
        this.customBackground.visibleWhen(() -> !this.hideScoreboard.isEnabled());
        this.backgroundColor.visibleWhen(() -> this.customBackground.isEnabled() && !this.hideScoreboard.isEnabled());
        this.backgroundOpacity.visibleWhen(() -> this.customBackground.isEnabled() && !this.hideScoreboard.isEnabled());
    }

    public boolean shouldHideScoreboard() {
        return this.hideScoreboard.isEnabled();
    }

    public boolean shouldHideNumbers() {
        return this.hideNumbers.isEnabled();
    }

    public boolean hasCustomBackground() {
        return this.customBackground.isEnabled();
    }

    public int getBackgroundColor() {
        int alpha = this.backgroundOpacity.getIntValue();
        return alpha << 24 | this.backgroundColor.getRGB() & 0xFFFFFF;
    }
}

