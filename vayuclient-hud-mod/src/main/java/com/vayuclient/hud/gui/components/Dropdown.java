/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 */
package com.vayuclient.hud.gui.components;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class Dropdown
extends UIComponent {
    private final String label;
    private final String[] options;
    private int selectedIndex;
    private final Consumer<String> onChange;
    private boolean expanded = false;
    private float expandProgress = 0.0f;
    private float hoverProgress = 0.0f;
    private int hoveredOption = -1;
    private long lastUpdate = System.currentTimeMillis();

    public Dropdown(int x, int y, int width, int height, String label, String[] options, String selected, Consumer<String> onChange) {
        super(x, y, width, height);
        this.label = label;
        this.options = options;
        this.onChange = onChange;
        this.selectedIndex = 0;
        for (int i = 0; i < options.length; ++i) {
            if (!options[i].equalsIgnoreCase(selected)) continue;
            this.selectedIndex = i;
            break;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hovered = this.isHovered(mouseX, mouseY);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.expandProgress = AnimationUtils.smoothDelta(this.expandProgress, this.expanded ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, this.hovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
        Minecraft mc = Minecraft.getInstance();
        if (this.hoverProgress > 0.01f && !this.expanded) {
            int bgAlpha = (int)(15.0f * this.hoverProgress);
            VayuHUDUI.roundedRect(graphics, this.x, this.y, this.width, this.height, 4, VayuHUDUI.withAlpha(-266722777, bgAlpha));
        }
        String displayLabel = VayuTheme.formatSettingName(this.label);
        int labelColor = VayuHUDUI.blend(-7303024, -723724, this.hoverProgress);
        this.drawUiText(graphics, mc, displayLabel, this.x + 12, this.centeredTextY(mc, this.y, this.height), labelColor);
        int dropWidth = 100;
        int dropX = this.x + this.width - dropWidth - 12;
        int dropY = this.y + 2;
        int dropHeight = this.height - 4;
        int btnColor = this.expanded ? -16723201 : (this.hovered ? -266722777 : -435153640);
        VayuHUDUI.roundedRect(graphics, dropX, dropY, dropWidth, dropHeight, 4, btnColor);
        int borderColor = this.expanded ? -13058312 : 1143616571;
        VayuHUDUI.outline(graphics, dropX, dropY, dropWidth, dropHeight, borderColor);
        String selectedText = this.options[this.selectedIndex];
        int textX = dropX + (dropWidth - this.uiTextWidth(mc, selectedText)) / 2 - 6;
        this.drawUiText(graphics, mc, selectedText, textX, this.centeredTextY(mc, dropY, dropHeight), -723724);
        String arrow = this.expanded ? "\u25b2" : "\u25bc";
        this.drawUiText(graphics, mc, arrow, dropX + dropWidth - 14, this.centeredTextY(mc, dropY, dropHeight), -7303024);
        if (this.expandProgress > 0.01f) {
            int optY;
            int i;
            int optionHeight = this.height;
            int totalOptionsHeight = (int)((float)(this.options.length * optionHeight) * this.expandProgress);
            int optionsY = this.y + this.height + 2;
            VayuHUDUI.roundedRect(graphics, dropX, optionsY, dropWidth, totalOptionsHeight, 4, -435219433);
            VayuHUDUI.outline(graphics, dropX, optionsY, dropWidth, totalOptionsHeight, 1143616571);
            this.hoveredOption = -1;
            if (this.expandProgress > 0.9f) {
                for (i = 0; i < this.options.length; ++i) {
                    optY = optionsY + i * optionHeight;
                    if (mouseX < dropX || mouseX > dropX + dropWidth || mouseY < optY || mouseY >= optY + optionHeight) continue;
                    this.hoveredOption = i;
                    break;
                }
            }
            for (i = 0; i < this.options.length; ++i) {
                optY = optionsY + i * optionHeight;
                if (optY + optionHeight <= optionsY || optY >= optionsY + totalOptionsHeight) continue;
                int optBgColor = i == this.selectedIndex ? VayuHUDUI.withAlpha(-16723201, 60) : (i == this.hoveredOption ? -266722777 : -435153640);
                graphics.fill(dropX + 1, optY, dropX + dropWidth - 1, optY + optionHeight, optBgColor);
                if (i == this.selectedIndex) {
                    this.drawUiText(graphics, mc, "\u2713", dropX + 6, this.centeredTextY(mc, optY, optionHeight), -16723201);
                }
                int optTextX = dropX + (i == this.selectedIndex ? 18 : 8);
                int optTextColor = i == this.selectedIndex ? -16723201 : -723724;
                this.drawUiText(graphics, mc, this.options[i], optTextX, this.centeredTextY(mc, optY, optionHeight), optTextColor);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != 0) {
            return false;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int dropWidth = 100;
        int dropX = this.x + this.width - dropWidth - 12;
        if (this.expanded && this.expandProgress > 0.9f) {
            int optionHeight = this.height;
            int optionsY = this.y + this.height + 2;
            for (int i = 0; i < this.options.length; ++i) {
                int optY = optionsY + i * optionHeight;
                if (!(mouseX >= (double)dropX) || !(mouseX <= (double)(dropX + dropWidth)) || !(mouseY >= (double)optY) || !(mouseY < (double)(optY + optionHeight))) continue;
                this.selectedIndex = i;
                if (this.onChange != null) {
                    this.onChange.accept(this.options[this.selectedIndex]);
                }
                this.expanded = false;
                return true;
            }
            this.expanded = false;
            return true;
        }
        if (mouseX >= (double)dropX && mouseX <= (double)(dropX + dropWidth) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height)) {
            this.expanded = !this.expanded;
            return true;
        }
        return false;
    }

    public String getSelected() {
        return this.options[this.selectedIndex];
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    @Override
    public int getHeight() {
        if (this.expanded && this.expandProgress > 0.5f) {
            return this.height + this.options.length * this.height + 4;
        }
        return this.height;
    }
}

