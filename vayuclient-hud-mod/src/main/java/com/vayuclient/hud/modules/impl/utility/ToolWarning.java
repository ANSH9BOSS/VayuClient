/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.sounds.SoundEvent
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.item.ItemStack
 */
package com.vayuclient.hud.modules.impl.utility;

import java.util.ArrayList;
import java.util.List;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ToolWarning
extends Module {
    private final ModeSetting thresholdMode = this.register(new ModeSetting("threshold_mode", "How to calculate low durability", "percentage", new String[]{"percentage", "absolute"}));
    private final NumberSetting percentThreshold = this.register(new NumberSetting("percent_threshold", "Durability percentage threshold", 10.0, 1.0, 50.0, 1.0));
    private final NumberSetting absoluteThreshold = this.register(new NumberSetting("absolute_threshold", "Absolute durability threshold", 10.0, 1.0, 100.0, 1.0));
    private final BooleanSetting showMainHand = this.register(new BooleanSetting("show_main_hand", "Warn for main hand item", true));
    private final BooleanSetting showOffHand = this.register(new BooleanSetting("show_off_hand", "Warn for off hand item", true));
    private final BooleanSetting showHelmet = this.register(new BooleanSetting("show_helmet", "Warn for helmet", true));
    private final BooleanSetting showChestplate = this.register(new BooleanSetting("show_chestplate", "Warn for chestplate", true));
    private final BooleanSetting showLeggings = this.register(new BooleanSetting("show_leggings", "Warn for leggings", true));
    private final BooleanSetting showBoots = this.register(new BooleanSetting("show_boots", "Warn for boots", true));
    private final BooleanSetting showIcon = this.register(new BooleanSetting("show_icon", "Show warning icon", true));
    private final BooleanSetting showItemName = this.register(new BooleanSetting("show_item_name", "Show item name", true));
    private final BooleanSetting showDurability = this.register(new BooleanSetting("show_durability", "Show remaining durability", true));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final ColorSetting warningColor = this.register(new ColorSetting("warning_color", "Warning text color", 255, 85, 85));
    private final ColorSetting criticalColor = this.register(new ColorSetting("critical_color", "Critical text color (< 5%)", 255, 0, 0));
    private final BooleanSetting soundAlert = this.register(new BooleanSetting("sound_alert", "Play sound alert", true));
    private final NumberSetting soundCooldown = this.register(new NumberSetting("sound_cooldown", "Seconds between sounds", 3.0, 1.0, 10.0, 0.5));
    private long lastSoundTime = 0L;
    private List<WarningEntry> cachedWarnings = new ArrayList<WarningEntry>();

    public ToolWarning() {
        super("ToolWarning", "Warns when tools/armor are low durability", Category.HUD);
        this.percentThreshold.visibleWhen(() -> this.thresholdMode.is("percentage"));
        this.absoluteThreshold.visibleWhen(() -> this.thresholdMode.is("absolute"));
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        long now;
        if (!this.isInGame() || ToolWarning.mc.player == null) {
            return;
        }
        this.updateWarnings();
        if (this.cachedWarnings.isEmpty()) {
            return;
        }
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        int lineY = y;
        int maxWidth = this.getContentWidth();
        if (this.background.isEnabled()) {
            int height = this.cachedWarnings.size() * 12 + 6;
            VayuHUDUI.hudPanelStrong(graphics, x - 5, y - 4, maxWidth + 10, height);
        }
        for (WarningEntry entry : this.cachedWarnings) {
            int color = entry.isCritical ? this.criticalColor.getRGB() : this.warningColor.getRGB();
            color |= 0xFF000000;
            StringBuilder text = new StringBuilder();
            if (this.showIcon.isEnabled()) {
                text.append("\u26a0 ");
            }
            if (this.showItemName.isEnabled()) {
                text.append(entry.name);
            }
            if (this.showDurability.isEnabled()) {
                if (this.showItemName.isEnabled()) {
                    text.append(": ");
                }
                text.append(entry.durability);
                if (this.thresholdMode.is("percentage")) {
                    text.append(" (").append(String.format("%.0f%%", Float.valueOf(entry.percent))).append(")");
                }
            }
            graphics.text(ToolWarning.mc.font, text.toString(), x, lineY, color, true);
            lineY += 12;
        }
        graphics.pose().popMatrix();
        if (this.soundAlert.isEnabled() && !this.cachedWarnings.isEmpty() && (double)((now = System.currentTimeMillis()) - this.lastSoundTime) > (Double)this.soundCooldown.getValue() * 1000.0) {
            ToolWarning.mc.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 0.5f);
            this.lastSoundTime = now;
        }
    }

    private void updateWarnings() {
        this.cachedWarnings.clear();
        if (ToolWarning.mc.player == null) {
            return;
        }
        if (this.showMainHand.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getMainHandItem());
        }
        if (this.showOffHand.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getOffhandItem());
        }
        if (this.showHelmet.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getItemBySlot(EquipmentSlot.HEAD));
        }
        if (this.showChestplate.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getItemBySlot(EquipmentSlot.CHEST));
        }
        if (this.showLeggings.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getItemBySlot(EquipmentSlot.LEGS));
        }
        if (this.showBoots.isEnabled()) {
            this.checkItem(ToolWarning.mc.player.getItemBySlot(EquipmentSlot.FEET));
        }
    }

    private void checkItem(ItemStack stack) {
        boolean isLow;
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        float percent = (float)remaining / (float)stack.getMaxDamage() * 100.0f;
        if (this.thresholdMode.is("percentage")) {
            isLow = (double)percent <= (Double)this.percentThreshold.getValue();
        } else {
            boolean bl = isLow = remaining <= this.absoluteThreshold.getIntValue();
        }
        if (isLow) {
            boolean isCritical = percent < 5.0f;
            this.cachedWarnings.add(new WarningEntry(stack.getHoverName().getString(), remaining, percent, isCritical));
        }
    }

    private int getContentWidth() {
        int maxWidth = 0;
        for (WarningEntry entry : this.cachedWarnings) {
            StringBuilder text = new StringBuilder();
            if (this.showIcon.isEnabled()) {
                text.append("\u26a0 ");
            }
            if (this.showItemName.isEnabled()) {
                text.append(entry.name);
            }
            if (this.showDurability.isEnabled()) {
                if (this.showItemName.isEnabled()) {
                    text.append(": ");
                }
                text.append(entry.durability);
                if (this.thresholdMode.is("percentage")) {
                    text.append(" (").append(String.format("%.0f%%", Float.valueOf(entry.percent))).append(")");
                }
            }
            maxWidth = Math.max(maxWidth, ToolWarning.mc.font.width(text.toString()));
        }
        return maxWidth;
    }

    @Override
    public int getHudWidth() {
        int width = this.getContentWidth();
        return width > 0 ? width + 10 : 100;
    }

    @Override
    public int getHudHeight() {
        int count = this.cachedWarnings.size();
        return count > 0 ? count * 12 + 6 : 18;
    }

    private static class WarningEntry {
        final String name;
        final int durability;
        final float percent;
        final boolean isCritical;

        WarningEntry(String name, int durability, float percent, boolean isCritical) {
            this.name = name;
            this.durability = durability;
            this.percent = percent;
            this.isCritical = isCritical;
        }
    }
}

