/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;

public class MotionBlurModule
extends Module {
    private final NumberSetting strength = this.register(new NumberSetting("strength", "Blur intensity (1.0 = natural)", 1.0, 0.1, 10.0, 0.1));
    private final BooleanSetting refreshRateScaling = this.register(new BooleanSetting("refresh_rate_scaling", "Scale blur based on FPS vs refresh rate", true));
    private final ModeSetting excludeEntities = this.register(new ModeSetting("exclude_entities", "Exclude entities from blur", "Third Person", new String[]{"Always", "Third Person", "Never"}));
    private final ModeSetting blurAlgorithm = this.register(new ModeSetting("blur_algorithm", "Blur direction algorithm", "Centered", new String[]{"Backwards", "Centered"}));

    public MotionBlurModule() {
        super("Motion Blur", "Velocity-based motion blur effect", Category.RENDER);
    }

    public float getStrength() {
        return this.strength.getFloatValue();
    }

    public boolean isRefreshRateScaling() {
        return this.refreshRateScaling.isEnabled();
    }

    public String getExcludeEntities() {
        return (String)this.excludeEntities.getValue();
    }

    public int getBlurAlgorithmOrdinal() {
        return this.blurAlgorithm.is("Backwards") ? 0 : 1;
    }
}

