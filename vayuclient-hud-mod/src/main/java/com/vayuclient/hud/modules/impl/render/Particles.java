/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;

public class Particles
extends Module {
    private final NumberSetting multiplier = this.register(new NumberSetting("multiplier", "Particle spawn multiplier (1 = normal)", 1.0, 0.0, 5.0, 0.5));
    private final BooleanSetting weather = this.register(new BooleanSetting("weather", "Show rain/snow weather effects", true));
    private final BooleanSetting criticals = this.register(new BooleanSetting("criticals", "Show critical hit particles", true));
    private final BooleanSetting enchanted = this.register(new BooleanSetting("enchanted", "Show enchanted hit particles", true));
    private final BooleanSetting explosion = this.register(new BooleanSetting("explosion", "Show explosion particles", true));
    private final BooleanSetting potion = this.register(new BooleanSetting("potion", "Show potion/effect particles", true));
    private final BooleanSetting firework = this.register(new BooleanSetting("firework", "Show firework particles", true));
    private final BooleanSetting totem = this.register(new BooleanSetting("totem", "Show totem of undying particles", true));
    private final BooleanSetting smoke = this.register(new BooleanSetting("smoke", "Show smoke particles", true));
    private final BooleanSetting flame = this.register(new BooleanSetting("flame", "Show flame particles", true));
    private final BooleanSetting waterSplash = this.register(new BooleanSetting("water_splash", "Show water splash particles", true));
    private final BooleanSetting useCustomColor = this.register(new BooleanSetting("custom_crit_color", "Use custom color for crit particles", false));
    private final ColorSetting critColor = this.register(new ColorSetting("crit_color", "Custom crit particle color", 255, 215, 0));
    private static Particles instance;

    public Particles() {
        super("Particles", "Control particle effects and weather", Category.RENDER);
        this.critColor.visibleWhen(this.useCustomColor::isEnabled);
        instance = this;
    }

    public static Particles getInstance() {
        return instance;
    }

    public float getMultiplier() {
        return this.multiplier.getFloatValue();
    }

    public boolean shouldShowWeather() {
        return this.weather.isEnabled();
    }

    public boolean shouldShowCriticals() {
        return this.criticals.isEnabled();
    }

    public boolean shouldShowEnchanted() {
        return this.enchanted.isEnabled();
    }

    public boolean shouldShowExplosion() {
        return this.explosion.isEnabled();
    }

    public boolean shouldShowPotion() {
        return this.potion.isEnabled();
    }

    public boolean shouldShowFirework() {
        return this.firework.isEnabled();
    }

    public boolean shouldShowTotem() {
        return this.totem.isEnabled();
    }

    public boolean shouldShowSmoke() {
        return this.smoke.isEnabled();
    }

    public boolean shouldShowFlame() {
        return this.flame.isEnabled();
    }

    public boolean shouldShowWaterSplash() {
        return this.waterSplash.isEnabled();
    }

    public boolean useCustomCritColor() {
        return this.useCustomColor.isEnabled();
    }

    public int getCritColor() {
        return this.critColor.getRGB() | 0xFF000000;
    }
}

