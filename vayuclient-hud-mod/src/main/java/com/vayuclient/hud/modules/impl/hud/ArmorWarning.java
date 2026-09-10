package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorWarning extends Module {
    private final NumberSetting thresholdPercent = this.register(new NumberSetting("threshold_percent", "Durability % threshold to alert", 15.0, 5.0, 40.0, 1.0));
    private final BooleanSetting soundAlert = this.register(new BooleanSetting("sound_alert", "Play alert sound on critical armor", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Glass background panel", true));
    private final ColorSetting alertColor = this.register(new ColorSetting("alert_color", "Warning color", 255, 60, 60));

    private long lastSoundTime = 0L;
    private String lowestPieceName = "";
    private int lowestPercent = 100;
    private boolean isCritical = false;

    public ArmorWarning() {
        super("ArmorWarning", "Critical low durability alert for helmet, chestplate, leggings, and boots", Category.HUD);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || mc.player == null) return;

        this.checkArmor();
        if (!this.isCritical) return;

        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();

        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));

        String text = "🛡 LOW ARMOR: " + this.lowestPieceName + " (" + this.lowestPercent + "%)";
        int textW = mc.font.width(text);

        if (this.background.isEnabled()) {
            VayuHUDUI.hudPanelStrong(graphics, x - 4, y - 3, textW + 8, 14);
        }

        float pulse = (float)(0.7 + 0.3 * Math.sin(System.currentTimeMillis() / 120.0));
        int baseColor = this.alertColor.getRGB();
        int r = (int)(((baseColor >> 16) & 0xFF) * pulse);
        int g = (int)(((baseColor >> 8) & 0xFF) * pulse);
        int b = (int)((baseColor & 0xFF) * pulse);
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;

        graphics.text(mc.font, text, x, y, color, true);
        graphics.pose().popMatrix();
    }

    private void checkArmor() {
        if (mc.player == null) return;

        int minP = 100;
        String minName = "";
        boolean foundLow = false;
        double threshold = this.thresholdPercent.getValue().doubleValue();

        EquipmentSlot[] slots = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        for (EquipmentSlot slot : slots) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                int maxDamage = stack.getMaxDamage();
                int currentDamage = stack.getDamageValue();
                int durabilityRemaining = maxDamage - currentDamage;
                int percent = (int)((float)durabilityRemaining / (float)maxDamage * 100.0f);

                if (percent <= threshold && percent < minP) {
                    minP = percent;
                    minName = capitalize(slot.getName());
                    foundLow = true;
                }
            }
        }

        this.isCritical = foundLow;
        this.lowestPercent = minP;
        this.lowestPieceName = minName;

        if (this.isCritical && this.soundAlert.isEnabled()) {
            long now = System.currentTimeMillis();
            if (now - this.lastSoundTime > 3500L) {
                this.lastSoundTime = now;
                try {
                    mc.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.5f);
                } catch (Throwable ignored) {}
            }
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
