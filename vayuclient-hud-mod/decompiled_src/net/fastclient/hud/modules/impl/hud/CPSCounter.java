/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  org.lwjgl.glfw.GLFW
 */
package net.fastclient.hud.modules.impl.hud;

import java.util.LinkedList;
import java.util.Queue;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

public class CPSCounter
extends Module {
    private final Queue<Long> leftClicks = new LinkedList<Long>();
    private final Queue<Long> rightClicks = new LinkedList<Long>();
    private boolean wasLeftPressed = false;
    private boolean wasRightPressed = false;

    public CPSCounter() {
        super("CPSCounter", "Clicks per second counter", Category.HUD);
    }

    @Override
    public void onTick() {
        boolean rightPressed;
        if (!this.isInGame()) {
            return;
        }
        long now = System.currentTimeMillis();
        while (!this.leftClicks.isEmpty() && now - this.leftClicks.peek() > 1000L) {
            this.leftClicks.poll();
        }
        while (!this.rightClicks.isEmpty() && now - this.rightClicks.peek() > 1000L) {
            this.rightClicks.poll();
        }
        long windowHandle = mc.getWindow().handle();
        boolean leftPressed = GLFW.glfwGetMouseButton((long)windowHandle, (int)0) == 1;
        boolean bl = rightPressed = GLFW.glfwGetMouseButton((long)windowHandle, (int)1) == 1;
        if (leftPressed && !this.wasLeftPressed) {
            this.leftClicks.add(now);
        }
        if (rightPressed && !this.wasRightPressed) {
            this.rightClicks.add(now);
        }
        this.wasLeftPressed = leftPressed;
        this.wasRightPressed = rightPressed;
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
        String text = String.format("CPS: %d | %d", this.leftClicks.size(), this.rightClicks.size());
        FastClientUI.hudText(graphics, CPSCounter.mc.font, text, x, y, -1, true);
        graphics.pose().popMatrix();
    }

    @Override
    public int getHudWidth() {
        String text = String.format("CPS: %d | %d", this.leftClicks.size(), this.rightClicks.size());
        return CPSCounter.mc.font.width(text) + 10;
    }

    @Override
    public int getHudHeight() {
        return 17;
    }
}

