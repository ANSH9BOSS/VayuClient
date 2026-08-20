/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.Hud
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.core.Holder
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.effect.MobEffect
 *  net.minecraft.world.effect.MobEffectInstance
 */
package net.fastclient.hud.modules.impl.hud;

import java.util.Collection;
import java.util.Objects;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionHUD
extends Module {
    private final BooleanSetting showIcon = this.register(new BooleanSetting("show_icon", "Show effect icon", true));
    private final BooleanSetting showDuration = this.register(new BooleanSetting("show_duration", "Show remaining duration", true));
    private final BooleanSetting showAmplifier = this.register(new BooleanSetting("show_amplifier", "Show effect level", true));
    private final BooleanSetting showName = this.register(new BooleanSetting("show_name", "Show effect name", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", true));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 80.0, 0.0, 255.0, 5.0));
    private static final int ICON_SIZE = 18;

    public PotionHUD() {
        super("PotionHUD", "Shows active potion effects", Category.HUD);
        this.bgOpacity.visibleWhen(this.background::isEnabled);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame()) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        int offsetY = 0;
        int iconOffset = (Boolean)this.showIcon.getValue() != false ? 20 : 0;
        int lineHeight = (Boolean)this.showIcon.getValue() != false ? 20 : 12;
        Collection effects = PotionHUD.mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            graphics.pose().popMatrix();
            return;
        }
        int maxWidth = 0;
        for (MobEffectInstance effect : effects) {
            String text = this.buildEffectText(effect);
            int width = iconOffset + PotionHUD.mc.font.width(text);
            if (width <= maxWidth) continue;
            maxWidth = width;
        }
        if (((Boolean)this.background.getValue()).booleanValue()) {
            FastClientUI.hudPanel(graphics, x - 5, y - 4, maxWidth + 10, effects.size() * lineHeight + 6, this.bgOpacity.getIntValue());
        }
        for (MobEffectInstance effect : effects) {
            int n;
            Holder effectType = effect.getEffect();
            MobEffect effectValue = (MobEffect)effectType.value();
            if (((Boolean)this.showIcon.getValue()).booleanValue()) {
                Identifier spriteId = Hud.getMobEffectSprite((Holder)effectType);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, x, y + offsetY, 18, 18);
            }
            String text = this.buildEffectText(effect);
            int color = effectValue.getColor();
            if (color == 0 || color == -1) {
                color = 0xFFFFFF;
            }
            color |= 0xFF000000;
            if (((Boolean)this.showIcon.getValue()).booleanValue()) {
                Objects.requireNonNull(PotionHUD.mc.font);
                n = (18 - 9) / 2;
            } else {
                n = 0;
            }
            int textY = y + offsetY + n;
            graphics.text(PotionHUD.mc.font, text, x + iconOffset, textY, color, true);
            offsetY += lineHeight;
        }
        graphics.pose().popMatrix();
    }

    private String buildEffectText(MobEffectInstance effect) {
        StringBuilder text = new StringBuilder();
        if (((Boolean)this.showName.getValue()).booleanValue()) {
            text.append(((MobEffect)effect.getEffect().value()).getDisplayName().getString());
        }
        if (((Boolean)this.showAmplifier.getValue()).booleanValue() && effect.getAmplifier() > 0) {
            if (text.length() > 0) {
                text.append(" ");
            }
            text.append(this.toRoman(effect.getAmplifier() + 1));
        }
        if (((Boolean)this.showDuration.getValue()).booleanValue()) {
            int duration = effect.getDuration() / 20;
            int minutes = duration / 60;
            int seconds = duration % 60;
            if (text.length() > 0) {
                text.append(" ");
            }
            text.append("\u00a77").append(String.format("%d:%02d", minutes, seconds));
        }
        return text.toString();
    }

    private String toRoman(int number) {
        String[] romanNumerals = new String[]{"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (number >= 1 && number <= 10) {
            return romanNumerals[number - 1];
        }
        return String.valueOf(number);
    }

    @Override
    public int getHudWidth() {
        if (PotionHUD.mc.player == null) {
            return 100;
        }
        Collection effects = PotionHUD.mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            return 80;
        }
        int iconOffset = (Boolean)this.showIcon.getValue() != false ? 20 : 0;
        int maxWidth = 0;
        for (MobEffectInstance effect : effects) {
            String text = this.buildEffectText(effect);
            int width = iconOffset + PotionHUD.mc.font.width(text.replaceAll("\u00a7.", ""));
            if (width <= maxWidth) continue;
            maxWidth = width;
        }
        return maxWidth + 10;
    }

    @Override
    public int getHudHeight() {
        if (PotionHUD.mc.player == null) {
            return 20;
        }
        Collection effects = PotionHUD.mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            return 20;
        }
        int lineHeight = (Boolean)this.showIcon.getValue() != false ? 20 : 12;
        return effects.size() * lineHeight + 6;
    }
}

