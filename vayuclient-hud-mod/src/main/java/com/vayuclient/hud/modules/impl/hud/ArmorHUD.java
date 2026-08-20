/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.item.ItemStack
 */
package com.vayuclient.hud.modules.impl.hud;

import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ArmorHUD
extends Module {
    private final BooleanSetting showDurability = this.register(new BooleanSetting("show_durability", "Show durability values", true));
    private final ModeSetting displayMode = this.register(new ModeSetting("display_mode", "Durability display format", "value", new String[]{"value", "percentage", "both"}));
    private final BooleanSetting horizontal = this.register(new BooleanSetting("horizontal", "Horizontal layout", false));
    private final BooleanSetting background = this.register(new BooleanSetting("background", "Show background", false));
    private final NumberSetting bgOpacity = this.register(new NumberSetting("opacity", "Background opacity", 0.0, 0.0, 255.0, 5.0));

    public ArmorHUD() {
        super("ArmorHUD", "Shows equipped armor with durability", Category.HUD);
        this.bgOpacity.visibleWhen(this.background::isEnabled);
        this.displayMode.visibleWhen(this.showDurability::isEnabled);
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
        ItemStack helmet = ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack boots = ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.FEET);
        ItemStack[] armor = new ItemStack[]{helmet, chest, legs, boots};
        int textWidth = 0;
        if (((Boolean)this.showDurability.getValue()).booleanValue()) {
            for (ItemStack stack : armor) {
                String text;
                int w;
                if (stack.isEmpty() || !stack.isDamageableItem() || (w = ArmorHUD.mc.font.width(text = this.getDurabilityText(stack))) <= textWidth) continue;
                textWidth = w;
            }
        }
        if (((Boolean)this.background.getValue()).booleanValue()) {
            if (((Boolean)this.horizontal.getValue()).booleanValue()) {
                VayuHUDUI.hudPanel(graphics, x - 5, y - 4, armor.length * 20 + 10, 20 + ((Boolean)this.showDurability.getValue() != false ? 11 : 0) + 6, this.bgOpacity.getIntValue());
            } else {
                VayuHUDUI.hudPanel(graphics, x - 5, y - 4, 20 + ((Boolean)this.showDurability.getValue() != false ? textWidth : 0) + 10, armor.length * 20 + 6, this.bgOpacity.getIntValue());
            }
        }
        int offsetX = 0;
        int offsetY = 0;
        for (ItemStack stack : armor) {
            if (!stack.isEmpty()) {
                graphics.item(stack, x + offsetX, y + offsetY);
                if (((Boolean)this.showDurability.getValue()).booleanValue() && stack.isDamageableItem()) {
                    int currentDamage = stack.getDamageValue();
                    int maxDurability = stack.getMaxDamage();
                    int durability = maxDurability - currentDamage;
                    float percent = (float)durability / (float)maxDurability;
                    int color = percent > 0.5f ? -11141291 : (percent > 0.25f ? -171 : -43691);
                    String durText = this.getDurabilityText(stack);
                    if (((Boolean)this.horizontal.getValue()).booleanValue()) {
                        graphics.text(ArmorHUD.mc.font, durText, x + offsetX + 8 - ArmorHUD.mc.font.width(durText) / 2, y + offsetY + 17, color, true);
                    } else {
                        graphics.text(ArmorHUD.mc.font, durText, x + 20, y + offsetY + 4, color, true);
                    }
                }
            }
            if (((Boolean)this.horizontal.getValue()).booleanValue()) {
                offsetX += 20;
                continue;
            }
            offsetY += 20;
        }
        graphics.pose().popMatrix();
    }

    private String getDurabilityText(ItemStack stack) {
        int currentDamage = stack.getDamageValue();
        int maxDurability = stack.getMaxDamage();
        int durability = maxDurability - currentDamage;
        float percent = (float)durability / (float)maxDurability * 100.0f;
        String mode = (String)this.displayMode.getValue();
        if (mode.equals("percentage")) {
            return String.format("%.0f%%", Float.valueOf(percent));
        }
        if (mode.equals("both")) {
            return String.format("%d (%.0f%%)", durability, Float.valueOf(percent));
        }
        return String.valueOf(durability);
    }

    @Override
    public int getHudWidth() {
        int textWidth = 0;
        if (ArmorHUD.mc.player != null && ((Boolean)this.showDurability.getValue()).booleanValue()) {
            ItemStack[] armor;
            for (ItemStack stack : armor = new ItemStack[]{ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.HEAD), ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.CHEST), ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.LEGS), ArmorHUD.mc.player.getItemBySlot(EquipmentSlot.FEET)}) {
                if (stack.isEmpty() || !stack.isDamageableItem()) continue;
                textWidth = Math.max(textWidth, ArmorHUD.mc.font.width(this.getDurabilityText(stack)));
            }
        }
        if (((Boolean)this.horizontal.getValue()).booleanValue()) {
            return 90;
        }
        return 20 + ((Boolean)this.showDurability.getValue() != false ? textWidth : 0) + 10;
    }

    @Override
    public int getHudHeight() {
        if (((Boolean)this.horizontal.getValue()).booleanValue()) {
            return 20 + ((Boolean)this.showDurability.getValue() != false ? 11 : 0) + 6;
        }
        return 86;
    }
}

