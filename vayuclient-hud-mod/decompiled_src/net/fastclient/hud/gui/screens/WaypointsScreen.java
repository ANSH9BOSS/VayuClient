/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 */
package net.fastclient.hud.gui.screens;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.gui.components.ColorPicker;
import net.fastclient.hud.gui.components.TextInput;
import net.fastclient.hud.gui.components.UIComponent;
import net.fastclient.hud.modules.impl.render.WaypointsModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WaypointsScreen
extends Screen {
    private static final int ROW_HEIGHT = 58;
    private final WaypointsModule module;
    private final List<UIComponent> inputs = new ArrayList<UIComponent>();
    private String selectedId;
    private String name = "";
    private String x = "0";
    private String y = "0";
    private String z = "0";
    private String dimension = "overworld";
    private int color = -39373;
    private String error = "";
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int editorX;
    private int editorWidth;
    private int listX;
    private int listWidth;

    public WaypointsScreen(WaypointsModule module) {
        super((Component)Component.literal((String)"Waypoints"));
        this.module = module;
    }

    protected void init() {
        this.panelWidth = Math.min(DisplaySpace.width() - 48, 1180);
        this.panelHeight = Math.min(DisplaySpace.height() - 48, 720);
        this.panelX = (DisplaySpace.width() - this.panelWidth) / 2;
        this.panelY = (DisplaySpace.height() - this.panelHeight) / 2;
        this.editorX = this.panelX + 24;
        this.editorWidth = Math.max(310, (this.panelWidth - 76) * 43 / 100);
        this.listX = this.editorX + this.editorWidth + 28;
        this.listWidth = this.panelX + this.panelWidth - 24 - this.listX;
        if (this.selectedId == null) {
            this.useCurrentPosition();
        }
        this.rebuildInputs();
    }

    private void rebuildInputs() {
        this.inputs.clear();
        int top = this.panelY + 112;
        this.inputs.add(new TextInput(this.editorX, top, this.editorWidth, 30, "name", this.name, value -> {
            this.name = value;
        }));
        this.inputs.add(new TextInput(this.editorX, top + 42, this.editorWidth, 30, "x coordinate", this.x, value -> {
            this.x = value;
        }));
        this.inputs.add(new TextInput(this.editorX, top + 84, this.editorWidth, 30, "y coordinate", this.y, value -> {
            this.y = value;
        }));
        this.inputs.add(new TextInput(this.editorX, top + 126, this.editorWidth, 30, "z coordinate", this.z, value -> {
            this.z = value;
        }));
        this.inputs.add(new TextInput(this.editorX, top + 168, this.editorWidth, 30, "dimension", this.dimension, value -> {
            this.dimension = value;
        }));
        this.inputs.add(new ColorPicker(this.editorX, top + 210, this.editorWidth, 30, "marker color", new Color(this.color, true), value -> {
            this.color = value.getRGB() | 0xFF000000;
        }));
    }

    private void useCurrentPosition() {
        this.selectedId = null;
        this.name = "";
        if (this.minecraft != null && this.minecraft.player != null) {
            this.x = WaypointsScreen.formatCoordinate(this.minecraft.player.getX());
            this.y = WaypointsScreen.formatCoordinate(this.minecraft.player.getY());
            this.z = WaypointsScreen.formatCoordinate(this.minecraft.player.getZ());
            this.dimension = this.module.currentDimension();
        }
        this.color = -39373;
        this.error = "";
    }

    private static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        FastClientUI.roundedRect(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 7, -234156528);
        FastClientUI.outline(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 1143616571);
        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + 3, -39373);
        this.drawText(graphics, "WAYPOINTS", this.panelX + 24, this.panelY + 22, -723724, 1.35f);
        this.drawText(graphics, "Dimension-aware markers are stored locally and save automatically.", this.panelX + 24, this.panelY + 49, -7303024, 1.0f);
        this.drawButton(graphics, this.panelX + this.panelWidth - 54, this.panelY + 18, 30, 30, "X", pxMouseX, pxMouseY, false);
        graphics.fill(this.panelX + 20, this.panelY + 76, this.panelX + this.panelWidth - 20, this.panelY + 77, 1143616571);
        this.drawText(graphics, this.selectedId == null ? "CREATE WAYPOINT" : "EDIT WAYPOINT", this.editorX, this.panelY + 88, -723724, 1.0f);
        for (UIComponent input : this.inputs) {
            input.render(graphics, pxMouseX, pxMouseY, delta);
        }
        int actionY = this.panelY + 376;
        this.drawButton(graphics, this.editorX, actionY, this.editorWidth, 32, this.selectedId == null ? "ADD WAYPOINT" : "SAVE CHANGES", pxMouseX, pxMouseY, true);
        this.drawButton(graphics, this.editorX, actionY + 42, (this.editorWidth - 10) / 2, 30, "USE CURRENT", pxMouseX, pxMouseY, false);
        this.drawButton(graphics, this.editorX + (this.editorWidth + 10) / 2, actionY + 42, (this.editorWidth - 10) / 2, 30, "NEW / CLEAR", pxMouseX, pxMouseY, false);
        if (!this.error.isEmpty()) {
            this.drawText(graphics, this.error, this.editorX, actionY + 82, -34953, 0.9f);
        }
        this.drawText(graphics, "Tip: use full IDs like minecraft:the_nether for modded dimensions.", this.editorX, this.panelY + this.panelHeight - 38, -9934744, 0.85f);
        this.renderList(graphics, pxMouseX, pxMouseY);
        for (UIComponent input : this.inputs) {
            ColorPicker picker;
            if (!(input instanceof ColorPicker) || !(picker = (ColorPicker)input).isExpanded()) continue;
            input.render(graphics, pxMouseX, pxMouseY, delta);
        }
        DisplaySpace.pop(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<WaypointsModule.Waypoint> waypoints = this.module.getWaypoints();
        this.drawText(graphics, "SAVED  " + waypoints.size(), this.listX, this.panelY + 88, -723724, 1.0f);
        int listTop = this.panelY + 112;
        int listBottom = this.panelY + this.panelHeight - 24;
        DisplaySpace.enableScissor(graphics, this.listX, listTop, this.listX + this.listWidth, listBottom);
        if (waypoints.isEmpty()) {
            FastClientUI.roundedRect(graphics, this.listX, listTop, this.listWidth, 70, 5, -653586413);
            this.drawText(graphics, "No waypoints yet", this.listX + 16, listTop + 17, -723724, 1.0f);
            this.drawText(graphics, "Your first marker will appear here.", this.listX + 16, listTop + 40, -7303024, 0.9f);
        }
        for (int i = 0; i < waypoints.size(); ++i) {
            WaypointsModule.Waypoint waypoint = waypoints.get(i);
            int rowY = listTop + i * 58 - this.scroll;
            if (rowY + 58 < listTop || rowY > listBottom) continue;
            FastClientUI.roundedRect(graphics, this.listX, rowY, this.listWidth, 52, 5, waypoint.enabled ? -653586413 : -435219433);
            FastClientUI.outline(graphics, this.listX, rowY, this.listWidth, 52, this.selectedId != null && this.selectedId.equals(waypoint.id) ? -39373 : 1143616571);
            graphics.fill(this.listX, rowY, this.listX + 4, rowY + 58 - 6, waypoint.color | 0xFF000000);
            this.drawText(graphics, waypoint.name, this.listX + 13, rowY + 9, waypoint.enabled ? -723724 : -9934744, 1.0f);
            String detail = String.format(Locale.ROOT, "%.0f, %.0f, %.0f  -  %s", waypoint.x, waypoint.y, waypoint.z, waypoint.dimension);
            this.drawText(graphics, this.fit(detail, this.listWidth - 132), this.listX + 13, rowY + 31, -7303024, 0.82f);
            int buttonX = this.listX + this.listWidth - 102;
            this.drawButton(graphics, buttonX, rowY + 11, 28, 28, waypoint.enabled ? "ON" : "OFF", mouseX, mouseY, waypoint.enabled);
            this.drawButton(graphics, buttonX + 34, rowY + 11, 28, 28, "E", mouseX, mouseY, false);
            this.drawButton(graphics, buttonX + 68, rowY + 11, 28, 28, "X", mouseX, mouseY, false);
        }
        DisplaySpace.disableScissor(graphics);
    }

    private String fit(String value, int width) {
        String result = value;
        while (!result.isEmpty() && this.minecraft.font.width(result) > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result.equals(value) ? result : result + "...";
    }

    private void drawButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String label, int mouseX, int mouseY, boolean accent) {
        boolean hovered;
        boolean bl = hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        int background = accent ? (hovered ? -34995 : -39373) : (hovered ? -266722777 : -435153640);
        FastClientUI.roundedRect(graphics, x, y, width, height, 4, background);
        FastClientUI.outline(graphics, x, y, width, height, hovered ? -39373 : 1143616571);
        int textWidth = this.minecraft.font.width(label);
        graphics.text(this.minecraft.font, label, x + (width - textWidth) / 2, y + (height - 8) / 2, -1, false);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.text(this.minecraft.font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();
        if (button != 0) {
            return super.mouseClicked(event, bl);
        }
        if (WaypointsScreen.inside(mouseX, mouseY, this.panelX + this.panelWidth - 54, this.panelY + 18, 30, 30)) {
            this.onClose();
            return true;
        }
        for (UIComponent input : this.inputs) {
            if (!input.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl)) continue;
            return true;
        }
        int actionY = this.panelY + 376;
        if (WaypointsScreen.inside(mouseX, mouseY, this.editorX, actionY, this.editorWidth, 32)) {
            this.saveEditor();
            return true;
        }
        if (WaypointsScreen.inside(mouseX, mouseY, this.editorX, actionY + 42, (this.editorWidth - 10) / 2, 30)) {
            this.useCurrentPosition();
            this.rebuildInputs();
            return true;
        }
        if (WaypointsScreen.inside(mouseX, mouseY, this.editorX + (this.editorWidth + 10) / 2, actionY + 42, (this.editorWidth - 10) / 2, 30)) {
            this.useCurrentPosition();
            this.rebuildInputs();
            return true;
        }
        int listTop = this.panelY + 112;
        List<WaypointsModule.Waypoint> waypoints = this.module.getWaypoints();
        for (int i = 0; i < waypoints.size(); ++i) {
            int rowY = listTop + i * 58 - this.scroll;
            int buttonX = this.listX + this.listWidth - 102;
            WaypointsModule.Waypoint waypoint = waypoints.get(i);
            if (WaypointsScreen.inside(mouseX, mouseY, buttonX, rowY + 11, 28, 28)) {
                this.module.setWaypointEnabled(waypoint.id, !waypoint.enabled);
                return true;
            }
            if (WaypointsScreen.inside(mouseX, mouseY, buttonX + 34, rowY + 11, 28, 28)) {
                this.edit(waypoint);
                return true;
            }
            if (!WaypointsScreen.inside(mouseX, mouseY, buttonX + 68, rowY + 11, 28, 28)) continue;
            this.module.remove(waypoint.id);
            if (waypoint.id.equals(this.selectedId)) {
                this.useCurrentPosition();
                this.rebuildInputs();
            }
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    private void edit(WaypointsModule.Waypoint waypoint) {
        this.selectedId = waypoint.id;
        this.name = waypoint.name;
        this.x = Double.toString(waypoint.x);
        this.y = Double.toString(waypoint.y);
        this.z = Double.toString(waypoint.z);
        this.dimension = waypoint.dimension;
        this.color = waypoint.color;
        this.error = "";
        this.rebuildInputs();
    }

    private void saveEditor() {
        String cleanName = WaypointsModule.cleanName(this.name);
        String cleanDimension = WaypointsModule.normalizeDimension(this.dimension);
        if (cleanName.isEmpty()) {
            this.error = "Enter a waypoint name.";
            return;
        }
        if (cleanDimension.isEmpty()) {
            this.error = "Enter a dimension.";
            return;
        }
        try {
            double parsedX = Double.parseDouble(this.x.trim());
            double parsedY = Double.parseDouble(this.y.trim());
            double parsedZ = Double.parseDouble(this.z.trim());
            boolean enabled = true;
            if (this.selectedId != null) {
                enabled = this.module.getWaypoints().stream().filter(value -> value.id.equals(this.selectedId)).findFirst().map(value -> value.enabled).orElse(true);
            }
            WaypointsModule.Waypoint waypoint = this.selectedId == null ? this.module.create(cleanName, parsedX, parsedY, parsedZ, cleanDimension, this.color) : new WaypointsModule.Waypoint(this.selectedId, cleanName, parsedX, parsedY, parsedZ, cleanDimension, this.color, enabled);
            this.module.upsert(waypoint);
            this.useCurrentPosition();
            this.rebuildInputs();
        }
        catch (NumberFormatException exception) {
            this.error = "Coordinates must be valid numbers.";
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int visibleHeight = this.panelHeight - 136;
        int maxScroll = Math.max(0, this.module.getWaypoints().size() * 58 - visibleHeight);
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll - (int)(verticalAmount * 28.0)));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        for (UIComponent input : this.inputs) {
            if (!input.keyPressed(event)) continue;
            return true;
        }
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        for (UIComponent input : this.inputs) {
            if (!input.charTyped(event)) continue;
            return true;
        }
        return super.charTyped(event);
    }

    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= (double)x && mouseX <= (double)(x + width) && mouseY >= (double)y && mouseY <= (double)(y + height);
    }
}

