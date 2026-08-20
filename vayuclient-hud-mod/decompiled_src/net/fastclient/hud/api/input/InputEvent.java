/*
 * Decompiled with CFR 0.152.
 */
package net.fastclient.hud.api.input;

public class InputEvent {
    private final Type type;
    private final double mouseX;
    private final double mouseY;
    private final int button;
    private final int keyCode;
    private final int scanCode;
    private final int modifiers;
    private final char character;
    private final double deltaX;
    private final double deltaY;
    private final double scrollX;
    private final double scrollY;

    private InputEvent(Builder builder) {
        this.type = builder.type;
        this.mouseX = builder.mouseX;
        this.mouseY = builder.mouseY;
        this.button = builder.button;
        this.keyCode = builder.keyCode;
        this.scanCode = builder.scanCode;
        this.modifiers = builder.modifiers;
        this.character = builder.character;
        this.deltaX = builder.deltaX;
        this.deltaY = builder.deltaY;
        this.scrollX = builder.scrollX;
        this.scrollY = builder.scrollY;
    }

    public Type getType() {
        return this.type;
    }

    public double getMouseX() {
        return this.mouseX;
    }

    public double getMouseY() {
        return this.mouseY;
    }

    public int getButton() {
        return this.button;
    }

    public int getKeyCode() {
        return this.keyCode;
    }

    public int getScanCode() {
        return this.scanCode;
    }

    public int getModifiers() {
        return this.modifiers;
    }

    public char getCharacter() {
        return this.character;
    }

    public double getDeltaX() {
        return this.deltaX;
    }

    public double getDeltaY() {
        return this.deltaY;
    }

    public double getScrollX() {
        return this.scrollX;
    }

    public double getScrollY() {
        return this.scrollY;
    }

    public boolean isShiftDown() {
        return (this.modifiers & 1) != 0;
    }

    public boolean isCtrlDown() {
        return (this.modifiers & 2) != 0;
    }

    public boolean isAltDown() {
        return (this.modifiers & 4) != 0;
    }

    public static InputEvent mouseClick(double x, double y, int button) {
        return new Builder(Type.MOUSE_CLICK).mouse(x, y).button(button).build();
    }

    public static InputEvent mouseRelease(double x, double y, int button) {
        return new Builder(Type.MOUSE_RELEASE).mouse(x, y).button(button).build();
    }

    public static InputEvent mouseDrag(double x, double y, int button, double dx, double dy) {
        return new Builder(Type.MOUSE_DRAG).mouse(x, y).button(button).delta(dx, dy).build();
    }

    public static InputEvent mouseScroll(double x, double y, double scrollX, double scrollY) {
        return new Builder(Type.MOUSE_SCROLL).mouse(x, y).scroll(scrollX, scrollY).build();
    }

    public static InputEvent keyPress(int keyCode, int scanCode, int modifiers) {
        return new Builder(Type.KEY_PRESS).key(keyCode, scanCode, modifiers).build();
    }

    public static InputEvent keyRelease(int keyCode, int scanCode, int modifiers) {
        return new Builder(Type.KEY_RELEASE).key(keyCode, scanCode, modifiers).build();
    }

    public static InputEvent charTyped(char c, int modifiers) {
        return new Builder(Type.CHAR_TYPED).character(c).modifiers(modifiers).build();
    }

    public static class Builder {
        private final Type type;
        private double mouseX;
        private double mouseY;
        private int button;
        private int keyCode;
        private int scanCode;
        private int modifiers;
        private char character;
        private double deltaX;
        private double deltaY;
        private double scrollX;
        private double scrollY;

        public Builder(Type type) {
            this.type = type;
        }

        public Builder mouse(double x, double y) {
            this.mouseX = x;
            this.mouseY = y;
            return this;
        }

        public Builder button(int button) {
            this.button = button;
            return this;
        }

        public Builder key(int keyCode, int scanCode, int modifiers) {
            this.keyCode = keyCode;
            this.scanCode = scanCode;
            this.modifiers = modifiers;
            return this;
        }

        public Builder character(char c) {
            this.character = c;
            return this;
        }

        public Builder modifiers(int modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        public Builder delta(double dx, double dy) {
            this.deltaX = dx;
            this.deltaY = dy;
            return this;
        }

        public Builder scroll(double x, double y) {
            this.scrollX = x;
            this.scrollY = y;
            return this;
        }

        public InputEvent build() {
            return new InputEvent(this);
        }
    }

    public static enum Type {
        MOUSE_CLICK,
        MOUSE_RELEASE,
        MOUSE_DRAG,
        MOUSE_SCROLL,
        KEY_PRESS,
        KEY_RELEASE,
        CHAR_TYPED;

    }
}

