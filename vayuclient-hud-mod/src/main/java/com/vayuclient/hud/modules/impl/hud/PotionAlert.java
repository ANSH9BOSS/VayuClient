package com.vayuclient.hud.modules.impl.hud;

import java.util.ArrayList;
import java.util.List;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public class PotionAlert extends Module {
    private final NumberSetting alertSeconds = this.register(new NumberSetting("alert_seconds", "Seconds remaining to trigger alert", 10.0, 3.0, 30.0, 1.0));
    private final BooleanSetting pulseAnimation = this.register(new BooleanSetting("pulse_animation", "Pulsing alert flash", true));
    private final BooleanSetting soundAlert = this.register(new BooleanSetting("sound_alert", "Play sound warning", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Glass backdrop", true));
    private final ColorSetting alertColor = this.register(new ColorSetting("alert_color", "Warning text color", 255, 68, 68));
    
    private long lastSoundTime = 0L;
    private final List<AlertEntry> expiringEffects = new ArrayList<>();

    public PotionAlert() {
        super("PotionAlert", "Alerts when critical buffs are about to expire", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || mc.player == null) return;

        this.updateExpiringEffects();
        if (this.expiringEffects.isEmpty()) return;

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        int lineY = y;
        int maxWidth = 120;

        for (AlertEntry e : this.expiringEffects) {
            int textW = mc.font.width(e.text) + 16;
            if (textW > maxWidth) maxWidth = textW;
        }

        if (this.background.isEnabled()) {
            int height = this.expiringEffects.size() * 14 + 6;
            VayuHUDUI.hudPanelStrong(graphics, x - 4, y - 3, maxWidth + 8, height);
        }

        float pulse = this.pulseAnimation.isEnabled() 
            ? (float)(0.7 + 0.3 * Math.sin(System.currentTimeMillis() / 150.0))
            : 1.0f;

        for (AlertEntry e : this.expiringEffects) {
            int baseColor = this.alertColor.getRGB();
            int r = (int)(((baseColor >> 16) & 0xFF) * pulse);
            int g = (int)(((baseColor >> 8) & 0xFF) * pulse);
            int b = (int)((baseColor & 0xFF) * pulse);
            int color = 0xFF000000 | (r << 16) | (g << 8) | b;

            graphics.text(mc.font, "⚠ " + e.text, x, lineY, color, true);
            lineY += 14;
        }

        graphics.pose().popMatrix();
    }

    private void updateExpiringEffects() {
        this.expiringEffects.clear();
        if (mc.player == null) return;

        int thresholdTicks = (int)(this.alertSeconds.getValue().doubleValue() * 20.0);
        boolean hasExpiring = false;

        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            int duration = effect.getDuration();
            if (duration > 0 && duration <= thresholdTicks) {
                int secs = duration / 20;
                Holder<MobEffect> effectHolder = effect.getEffect();
                String name = ((MobEffect)effectHolder.value()).getDescriptionId().replace("effect.minecraft.", "");
                name = capitalize(name.replace('_', ' '));
                int amp = effect.getAmplifier() + 1;
                String ampStr = amp > 1 ? " " + toRoman(amp) : "";
                String timeStr = String.format("%d:%02d", secs / 60, secs % 60);

                this.expiringEffects.add(new AlertEntry(name + ampStr + " (" + timeStr + ")"));
                hasExpiring = true;
            }
        }

        if (hasExpiring && this.soundAlert.isEnabled()) {
            long now = System.currentTimeMillis();
            if (now - this.lastSoundTime > 3000L) {
                this.lastSoundTime = now;
                try {
                    mc.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
                } catch (Throwable ignored) {}
            }
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        char[] chars = str.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        for (int i = 1; i < chars.length; i++) {
            if (chars[i - 1] == ' ') chars[i] = Character.toUpperCase(chars[i]);
        }
        return new String(chars);
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static class AlertEntry {
        final String text;
        AlertEntry(String text) { this.text = text; }
    }
}
