/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.FormattedText
 */
package net.fastclient.hud.gui.components;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.fastclient.hud.gui.FastClientFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.FormattedText;

public abstract class UIComponent {
    protected static final int CONTENT_INSET = 12;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean hovered = false;
    protected BooleanSupplier visibilityCheck = () -> true;

    public UIComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(GuiGraphicsExtractor var1, int var2, int var3, float var4);

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        return this.isHovered(event.x(), event.y());
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    protected boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width) && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height);
    }

    protected void drawUiText(GuiGraphicsExtractor graphics, Minecraft mc, String text, int textX, int textY, int color) {
        float scale = FastClientFonts.bodyScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)textX, (float)textY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-textX), (float)(-textY));
        graphics.text(mc.font, FastClientFonts.body(text), textX, textY, color, false);
        graphics.pose().popMatrix();
    }

    protected int uiTextWidth(Minecraft mc, String text) {
        return Math.round((float)mc.font.width((FormattedText)FastClientFonts.body(text)) * FastClientFonts.bodyScale());
    }

    protected int centeredTextY(Minecraft mc, int boxY, int boxHeight) {
        Objects.requireNonNull(mc.font);
        int lineHeight = Math.round(9.0f * FastClientFonts.bodyScale());
        return boxY + (boxHeight - lineHeight) / 2 + 1;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public boolean isVisible() {
        return this.visible && this.visibilityCheck.getAsBoolean();
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setVisibilityCheck(BooleanSupplier check) {
        this.visibilityCheck = check;
    }
}

