package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;

public class CustomGlint extends Module {
    private final ModeSetting colorMode = this.register(new ModeSetting("color_mode", "Glint style", "custom", new String[]{"custom", "chroma", "gold", "cyan", "red", "neon_pink"}));
    private final ColorSetting customColor = this.register(new ColorSetting("custom_color", "Custom enchantment color", 0, 210, 255));
    private final NumberSetting glintSpeed = this.register(new NumberSetting("glint_speed", "Animation speed multiplier", 1.0, 0.2, 3.0, 0.1));
    private final NumberSetting glintIntensity = this.register(new NumberSetting("glint_intensity", "Glint brightness", 1.0, 0.2, 2.0, 0.1));
    private final BooleanSetting applyToArmor = this.register(new BooleanSetting("apply_to_armor", "Apply to worn armor", true));
    private final BooleanSetting applyToItems = this.register(new BooleanSetting("apply_to_items", "Apply to held items & tools", true));

    public CustomGlint() {
        super("CustomGlint", "Custom enchantment glint colors, speed, and brightness", Category.RENDER);
    }

    public int getGlintColor() {
        if (!this.isEnabled()) return 0xFF8040CC; // Default purple

        if (this.colorMode.is("chroma")) return getRainbowColor(2.0f);
        if (this.colorMode.is("gold")) return 0xFFFFD700;
        if (this.colorMode.is("cyan")) return 0xFF00E5FF;
        if (this.colorMode.is("red")) return 0xFFFF2244;
        if (this.colorMode.is("neon_pink")) return 0xFFFF1493;

        return this.customColor.getRGB();
    }

    public float getGlintSpeed() {
        return this.glintSpeed.getValue().floatValue();
    }

    public float getGlintIntensity() {
        return this.glintIntensity.getValue().floatValue();
    }

    public boolean shouldApplyToArmor() {
        return this.isEnabled() && this.applyToArmor.isEnabled();
    }

    public boolean shouldApplyToItems() {
        return this.isEnabled() && this.applyToItems.isEnabled();
    }

    private static int getRainbowColor(float speed) {
        float hue = (System.currentTimeMillis() % (int)(3000 / speed)) / (3000.0f / speed);
        return java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f);
    }
}
