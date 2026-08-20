/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.MouseButtonEvent
 */
package com.vayuclient.hud.gui.widgets;

import java.util.function.Consumer;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class CategoryButton {
    private final Category category;
    private int x;
    private int y;
    private final int width;
    private final int height;
    private boolean selected;
    private final Consumer<Category> onClick;
    private float hoverProgress;
    private float selectProgress;
    private long lastUpdate = System.currentTimeMillis();

    public CategoryButton(Category category, int x, int y, int width, int height, Consumer<Category> onClick) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onClick = onClick;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        long now = System.currentTimeMillis();
        float delta = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.hoverProgress = AnimationUtils.smoothDelta(this.hoverProgress, hovered ? 1.0f : 0.0f, 0.3f, delta * 60.0f);
        this.selectProgress = AnimationUtils.smoothDelta(this.selectProgress, this.selected ? 1.0f : 0.0f, 0.25f, delta * 60.0f);
        int bgColor = this.selected ? -16723201 : VayuHUDUI.blend(-435153640, -266722777, this.hoverProgress);
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, bgColor);
        String icon = this.category.getIcon();
        int iconX = this.x + 8;
        int iconY = this.y + (this.height - 8) / 2;
        int iconColor = this.selected ? -723724 : -7303024;
        graphics.text(mc.font, icon, iconX, iconY, iconColor, false);
        String name = this.category.getDisplayName();
        int textX = this.x + 24;
        int textY = this.y + (this.height - 8) / 2;
        int textColor = this.selected ? -723724 : VayuHUDUI.blend(-7303024, -723724, this.hoverProgress);
        graphics.text(mc.font, name, textX, textY, textColor, this.selected);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0 && mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height)) {
            if (this.onClick != null) {
                this.onClick.accept(this.category);
            }
            return true;
        }
        return false;
    }

    public Category getCategory() {
        return this.category;
    }

    public boolean isSelected() {
        return this.selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }
}

