/*
 * Decompiled with CFR 0.152.
 */
package com.vayuclient.hud.api.input;

public interface InputHandler {
    public boolean mouseClicked(double var1, double var3, int var5);

    public boolean mouseReleased(double var1, double var3, int var5);

    public boolean mouseDragged(double var1, double var3, int var5, double var6, double var8);

    public boolean mouseScrolled(double var1, double var3, double var5, double var7);

    public boolean keyPressed(int var1, int var2, int var3);

    public boolean keyReleased(int var1, int var2, int var3);

    public boolean charTyped(char var1, int var2);
}

