package com.vayuclient.hud.gui.screens;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.lwjgl.glfw.GLFW;

public class HudOverlayScreen extends Screen {
    private final List<DraggableModule> draggables = new ArrayList<>();
    private DraggableModule dragging = null;
    private DraggableModule selected = null;
    private DraggableModule configTarget = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private DraggableModule resizing = null;
    private int resizeStartX;
    private int resizeStartY;
    private float resizeStartScale;
    private boolean snapToGrid = false;
    private boolean showGuides = true;
    private int gridSize = 10;
    private final Set<Integer> activeGuidesX = new HashSet<>();
    private final Set<Integer> activeGuidesY = new HashSet<>();
    private boolean configPanelOpen = false;
    private AnimationUtils.Animation configPanelAnimation;
    private AnimationUtils.Animation openAnimation;

    public HudOverlayScreen() {
        super(Component.literal("VayuClient HUD Canvas"));
    }

    private int screenWidth() {
        return DisplaySpace.width();
    }

    private int screenHeight() {
        return DisplaySpace.height();
    }

    private boolean isShiftKeyDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, 340) == 1 || GLFW.glfwGetKey(handle, 344) == 1;
    }

    @Override
    protected void init() {
        this.draggables.clear();
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 200L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        if (this.configPanelAnimation == null) {
            this.configPanelAnimation = new AnimationUtils.Animation(0.0f, 150L);
        }
        for (Module module : VayuHUDClient.getInstance().getModuleManager().getModules()) {
            if (!module.isEnabled() || !module.isHudVisible()) continue;
            this.draggables.add(new DraggableModule(module, module.getHudX(), module.getHudY()));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.dragging == null) {
            graphics.fill(0, 0, this.screenWidth(), this.screenHeight(), 0x33000000);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);

        if (this.dragging == null) {
            this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        }

        float animProgress = this.openAnimation.getValue();

        if (this.dragging != null && this.showGuides) {
            this.renderAlignmentGuides(graphics);
        }

        for (DraggableModule dm : this.draggables) {
            this.renderDraggableModule(graphics, dm, pxMouseX, pxMouseY);
        }

        if (this.resizing != null) {
            this.renderPositionInfo(graphics, this.resizing);
        }

        if (this.dragging == null) {
            this.renderTopToolbar(graphics, pxMouseX, pxMouseY, animProgress);
            if (this.configPanelOpen && this.configTarget != null) {
                this.renderConfigPanel(graphics, pxMouseX, pxMouseY);
            }
            this.renderFooter(graphics, animProgress);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderTopToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float animProgress) {
        int barH = 26;
        int barY = 8;
        int barW = Math.min(480, this.screenWidth() - 32);
        int barX = (this.screenWidth() - barW) / 2;

        VayuHUDUI.roundedRect(graphics, barX, barY, barW, barH, 6, 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, barX, barY, barW, barH, 6, 0x3338BDF8);

        // Left: Emblem & Title
        int iconX = barX + 8;
        int iconY = barY + 3;
        VayuHUDUI.drawModsVector(graphics, iconX + 8, iconY + 10, 8, VayuTheme.PRIMARY);
        graphics.text(this.font, "HUD CANVAS", iconX + 20, barY + 9, VayuTheme.PRIMARY, true);

        // Center Actions: Snap & Guides
        int snapW = 60;
        int snapX = barX + 110;
        boolean snapH = mouseX >= snapX && mouseX <= snapX + snapW && mouseY >= barY + 3 && mouseY <= barY + barH - 3;
        VayuHUDUI.roundedRect(graphics, snapX, barY + 3, snapW, 20, 4, this.snapToGrid ? 0xFF0284C7 : (snapH ? 0xE6141E2D : 0xD00F1722));
        VayuHUDUI.roundedOutline(graphics, snapX, barY + 3, snapW, 20, 4, this.snapToGrid ? VayuTheme.PRIMARY : 0x2238BDF8);
        graphics.text(this.font, this.snapToGrid ? "Snap: ON" : "Snap: OFF", snapX + 6, barY + 9, this.snapToGrid ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);

        int guideW = 64;
        int guideX = snapX + snapW + 6;
        boolean guideH = mouseX >= guideX && mouseX <= guideX + guideW && mouseY >= barY + 3 && mouseY <= barY + barH - 3;
        VayuHUDUI.roundedRect(graphics, guideX, barY + 3, guideW, 20, 4, this.showGuides ? 0xFF0284C7 : (guideH ? 0xE6141E2D : 0xD00F1722));
        VayuHUDUI.roundedOutline(graphics, guideX, barY + 3, guideW, 20, 4, this.showGuides ? VayuTheme.PRIMARY : 0x2238BDF8);
        graphics.text(this.font, this.showGuides ? "Guides: ON" : "Guides: OFF", guideX + 6, barY + 9, this.showGuides ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);

        // Right Actions: Mods Menu & Done
        int doneW = 20;
        int doneX = barX + barW - doneW - 6;
        boolean doneH = mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= barY + 3 && mouseY <= barY + barH - 3;
        VayuHUDUI.roundedRect(graphics, doneX, barY + 3, doneW, 20, 4, doneH ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, doneX, barY + 3, doneW, 20, 4, doneH ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(graphics, doneX + doneW / 2, barY + barH / 2, 8, 0xFFFFFFFF);

        int modsW = 72;
        int modsX = doneX - modsW - 6;
        boolean modsH = mouseX >= modsX && mouseX <= modsX + modsW && mouseY >= barY + 3 && mouseY <= barY + barH - 3;
        VayuHUDUI.roundedRect(graphics, modsX, barY + 3, modsW, 20, 4, modsH ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, modsX, barY + 3, modsW, 20, 4, modsH ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "Mods Menu", modsX + 10, barY + 9, modsH ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);
    }

    private void renderAlignmentGuides(GuiGraphicsExtractor graphics) {
        int guideColor = 0x6638BDF8;
        for (int x : this.activeGuidesX) {
            graphics.fill(x, 0, x + 1, this.screenHeight(), guideColor);
        }
        for (int y : this.activeGuidesY) {
            graphics.fill(0, y, this.screenWidth(), y + 1, guideColor);
        }
    }

    private void renderDraggableModule(GuiGraphicsExtractor graphics, DraggableModule dm, int mouseX, int mouseY) {
        boolean isHovered = this.isHovered(dm, mouseX, mouseY);
        boolean isSelected = dm == this.selected;
        boolean isDragging = dm == this.dragging;
        boolean isResizing = dm == this.resizing;
        boolean isResizeHandleHovered = this.isResizeHandleHovered(dm, mouseX, mouseY);

        int[] bounds = this.getEditBounds(dm);
        int bx = bounds[0];
        int by = bounds[1];
        int bw = bounds[2];
        int bh = bounds[3];

        if (!isDragging) {
            Component moduleName = Component.literal(dm.module.getDisplayName());
            int nameW = this.font.width((FormattedText) moduleName);
            int nameX = bx + bw / 2 - nameW / 2;
            int nameY = by - 12;
            nameX = Math.max(2, Math.min(this.screenWidth() - nameW - 2, nameX));
            if (nameY < 2) {
                nameY = by + bh + 3;
            }
            int nameAlpha = isHovered || isSelected ? 0xD00A111A : 0x900A111A;
            VayuHUDUI.roundedRect(graphics, nameX - 4, nameY - 2, nameW + 8, 12, 3, nameAlpha);
            graphics.text(this.font, moduleName, nameX, nameY + 2, isHovered || isSelected ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);
        }

        if (isDragging) {
            int bracketColor = 0xAA38BDF8;
            graphics.fill(bx, by, bx + bw, by + 1, bracketColor);
            graphics.fill(bx, by + bh - 1, bx + bw, by + bh, bracketColor);
            graphics.fill(bx, by, bx + 1, by + bh, bracketColor);
            graphics.fill(bx + bw - 1, by, bx + bw, by + bh, bracketColor);
            return;
        }

        int borderColor = isResizing ? 0xFF00D9FF : (isSelected ? VayuTheme.PRIMARY : (isHovered ? 0xAA38BDF8 : 0x5538BDF8));
        VayuHUDUI.roundedRect(graphics, bx, by, bw, bh, 3, 0x220A111A);
        graphics.fill(bx, by, bx + bw, by + 1, borderColor);
        graphics.fill(bx, by + bh - 1, bx + bw, by + bh, borderColor);
        graphics.fill(bx, by, bx + 1, by + bh, borderColor);
        graphics.fill(bx + bw - 1, by, bx + bw, by + bh, borderColor);

        int chipSize = 14;
        int removeSize = 14;
        int handleSize = 10;
        int leftX = bx + 3;
        int chipY = by + bh - chipSize - 3;
        int rightX = bx + bw - handleSize - removeSize - 6;
        int removeY = by + bh - removeSize - 3;

        boolean hasSettings = this.hasSettings(dm);
        boolean settingsHovered = hasSettings && this.isSettingsActionHovered(dm, mouseX, mouseY);
        boolean disableHovered = this.isDisableActionHovered(dm, mouseX, mouseY);

        if (hasSettings) {
            VayuHUDUI.roundedRect(graphics, leftX, chipY, chipSize, chipSize, 3, settingsHovered ? 0xE6141E2D : 0xD00A111A);
            VayuHUDUI.drawSettingsVector(graphics, leftX + chipSize / 2, chipY + chipSize / 2, 8, settingsHovered ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED);
        }

        VayuHUDUI.roundedRect(graphics, rightX, removeY, removeSize, removeSize, 3, disableHovered ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.drawCloseVector(graphics, rightX + removeSize / 2, removeY + removeSize / 2, 6, disableHovered ? 0xFFFFFFFF : 0xFFEF4444);

        if (isHovered || isSelected || isResizing) {
            int handleX = bx + bw - handleSize;
            int handleY = by + bh - handleSize;
            int handleColor = isResizeHandleHovered || isResizing ? 0xFF00D9FF : 0xAA38BDF8;
            graphics.fill(handleX + 2, handleY + handleSize - 2, handleX + handleSize, handleY + handleSize, handleColor);
            graphics.fill(handleX + handleSize - 2, handleY + 2, handleX + handleSize, handleY + handleSize, handleColor);
        }
    }

    private void renderConfigPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float panelAnim = this.configPanelAnimation.getValue();
        if (panelAnim < 0.01f) {
            return;
        }
        int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 140);
        int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 120));
        int panelHeight = 110;
        int currentWidth = (int) (140.0f * panelAnim);

        VayuHUDUI.roundedRect(graphics, panelX, panelY, currentWidth, panelHeight, 6, 0xF0050A10);
        VayuHUDUI.roundedOutline(graphics, panelX, panelY, currentWidth, panelHeight, 6, 0x4438BDF8);

        if (panelAnim > 0.5f) {
            graphics.text(this.font, this.configTarget.module.getDisplayName(), panelX + 8, panelY + 8, VayuTheme.PRIMARY, true);
            int actionY = panelY + 26;
            graphics.text(this.font, "Reset Position", panelX + 8, actionY, VayuTheme.TEXT_MUTED, false);
            graphics.text(this.font, "Center Horizontally", panelX + 8, actionY += 18, VayuTheme.TEXT_MUTED, false);
            graphics.text(this.font, "Center Vertically", panelX + 8, actionY += 18, VayuTheme.TEXT_MUTED, false);
            graphics.text(this.font, "Full Settings...", panelX + 8, actionY += 20, VayuTheme.PRIMARY, true);
        }
    }

    private void renderPositionInfo(GuiGraphicsExtractor graphics, DraggableModule dm) {
        String posText = String.format("%.0f%%", dm.scale * 100.0f);
        int textWidth = this.font.width(posText);
        int infoX = dm.x + dm.width - textWidth - 4;
        int infoY = dm.y + dm.height + 4;
        VayuHUDUI.roundedRect(graphics, infoX - 4, infoY - 2, textWidth + 8, 12, 3, 0xD00A111A);
        graphics.text(this.font, posText, infoX, infoY + 2, VayuTheme.PRIMARY, false);
    }

    private void renderFooter(GuiGraphicsExtractor graphics, float animProgress) {
        String hints = "Drag modules to position | Right-click for options | Right Shift to close";
        int hintsWidth = this.font.width(hints);
        graphics.text(this.font, hints, (this.screenWidth() - hintsWidth) / 2, this.screenHeight() - 14, 0x88CBD5E1, false);
    }

    private boolean isHovered(DraggableModule dm, int mouseX, int mouseY) {
        int padding = 4;
        return mouseX >= dm.x - padding && mouseX <= dm.x + dm.width + padding && mouseY >= dm.y - padding && mouseY <= dm.y + dm.height + padding;
    }

    private boolean isResizeHandleHovered(DraggableModule dm, int mouseX, int mouseY) {
        int[] bounds = this.getEditBounds(dm);
        int bx = bounds[0];
        int by = bounds[1];
        int bw = bounds[2];
        int bh = bounds[3];
        return mouseX >= bx + bw - 12 && mouseX <= bx + bw + 4 && mouseY >= by + bh - 12 && mouseY <= by + bh + 4;
    }

    private boolean isSettingsActionHovered(DraggableModule dm, int mouseX, int mouseY) {
        if (!this.hasSettings(dm)) return false;
        int[] bounds = this.getEditBounds(dm);
        int size = 14;
        int x = bounds[0] + 3;
        int y = bounds[1] + bounds[3] - size - 3;
        return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
    }

    private boolean isDisableActionHovered(DraggableModule dm, int mouseX, int mouseY) {
        int[] bounds = this.getEditBounds(dm);
        int removeSize = 14;
        int x = bounds[0] + bounds[2] - 10 - removeSize - 6;
        int y = bounds[1] + bounds[3] - removeSize - 3;
        return mouseX >= x && mouseX <= x + removeSize && mouseY >= y && mouseY <= y + removeSize;
    }

    private int[] getEditBounds(DraggableModule dm) {
        return new int[]{dm.x, dm.y, dm.width, dm.height};
    }

    private boolean hasSettings(DraggableModule dm) {
        return !dm.module.getSettings().isEmpty();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();

        int barH = 26;
        int barY = 8;
        int barW = Math.min(480, this.screenWidth() - 32);
        int barX = (this.screenWidth() - barW) / 2;

        if (button == 0 && mouseY >= barY && mouseY <= barY + barH) {
            // Done Button
            int doneW = 20;
            int doneX = barX + barW - doneW - 6;
            if (mouseX >= doneX && mouseX <= doneX + doneW) {
                this.onClose();
                return true;
            }

            // Mods Menu Button
            int modsW = 72;
            int modsX = doneX - modsW - 6;
            if (mouseX >= modsX && mouseX <= modsX + modsW) {
                Minecraft.getInstance().gui.setScreen(ClickGUIScreen.getInstance());
                return true;
            }

            // Snap Button
            int snapW = 60;
            int snapX = barX + 110;
            if (mouseX >= snapX && mouseX <= snapX + snapW) {
                this.snapToGrid = !this.snapToGrid;
                return true;
            }

            // Guide Button
            int guideW = 64;
            int guideX = snapX + snapW + 6;
            if (mouseX >= guideX && mouseX <= guideX + guideW) {
                this.showGuides = !this.showGuides;
                return true;
            }
        }

        if (this.configPanelOpen && this.configTarget != null && this.configPanelAnimation.getValue() > 0.5f) {
            int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 140);
            int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 120));
            int panelHeight = 110;
            int currentWidth = (int) (140.0f * this.configPanelAnimation.getValue());
            if (mouseX >= panelX && mouseX <= panelX + currentWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
                return this.handleConfigPanelClick((int) mouseX, (int) mouseY, panelX, panelY);
            }
            this.configPanelAnimation.animateTo(0.0f);
            this.configPanelOpen = false;
            return true;
        }

        if (button == 1) { // Right Click
            for (DraggableModule dm : this.draggables) {
                if (!this.isHovered(dm, (int) mouseX, (int) mouseY)) continue;
                this.configTarget = dm;
                this.configPanelOpen = true;
                this.configPanelAnimation.animateTo(1.0f);
                return true;
            }
        }

        if (button == 0) {
            if (this.configPanelOpen) {
                this.configPanelAnimation.animateTo(0.0f);
                this.configPanelOpen = false;
            }
            for (DraggableModule dm : new ArrayList<>(this.draggables)) {
                if (this.hasSettings(dm) && this.isSettingsActionHovered(dm, (int) mouseX, (int) mouseY)) {
                    this.selected = dm;
                    Minecraft.getInstance().gui.setScreen(new ModuleConfigScreen(dm.module, this));
                    return true;
                }
                if (this.isDisableActionHovered(dm, (int) mouseX, (int) mouseY)) {
                    dm.module.setEnabled(false);
                    VayuHUDClient.getInstance().getModuleManager().saveConfig();
                    this.draggables.remove(dm);
                    this.selected = null;
                    return true;
                }
            }
            for (DraggableModule dm : this.draggables) {
                if (this.isResizeHandleHovered(dm, (int) mouseX, (int) mouseY)) {
                    this.resizing = dm;
                    this.selected = dm;
                    this.resizeStartX = (int) mouseX;
                    this.resizeStartY = (int) mouseY;
                    this.resizeStartScale = dm.scale;
                    return true;
                }
            }
            for (DraggableModule dm : this.draggables) {
                if (this.isHovered(dm, (int) mouseX, (int) mouseY)) {
                    this.dragging = dm;
                    this.selected = dm;
                    this.dragOffsetX = (int) mouseX - dm.x;
                    this.dragOffsetY = (int) mouseY - dm.y;
                    return true;
                }
            }
            this.selected = null;
        }
        return super.mouseClicked(event, bl);
    }

    private boolean handleConfigPanelClick(int mouseX, int mouseY, int panelX, int panelY) {
        if (this.configTarget == null) return false;
        int actionY = panelY + 26;
        int actionHeight = 14;
        if (mouseY >= actionY && mouseY <= actionY + actionHeight) {
            this.configTarget.x = 10;
            this.configTarget.y = 10;
            this.configTarget.module.setHudPosition(10, 10);
            return true;
        }
        if (mouseY >= (actionY += 18) && mouseY <= actionY + actionHeight) {
            this.configTarget.x = (this.screenWidth() - this.configTarget.width) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 18) && mouseY <= actionY + actionHeight) {
            this.configTarget.y = (this.screenHeight() - this.configTarget.height) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 20) && mouseY <= actionY + actionHeight) {
            Minecraft.getInstance().gui.setScreen(new ModuleConfigScreen(this.configTarget.module, this));
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (this.resizing != null) {
                this.resizing.module.setHudScale(this.resizing.scale);
                this.resizing = null;
                return true;
            }
            if (this.dragging != null) {
                this.dragging.module.setHudPosition(this.dragging.x, this.dragging.y);
                this.dragging = null;
                this.activeGuidesX.clear();
                this.activeGuidesY.clear();
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        if (this.resizing != null) {
            int deltaFromStart = (int) mouseX - this.resizeStartX + (int) mouseY - this.resizeStartY;
            float scaleDelta = (float) deltaFromStart / 100.0f;
            this.resizing.setScale(this.resizeStartScale + scaleDelta);
            this.resizing.module.setHudScale(this.resizing.scale);
            return true;
        }
        if (this.dragging != null) {
            int newX = (int) mouseX - this.dragOffsetX;
            int newY = (int) mouseY - this.dragOffsetY;
            this.activeGuidesX.clear();
            this.activeGuidesY.clear();
            if (this.snapToGrid) {
                newX = Math.round((float) newX / (float) this.gridSize) * this.gridSize;
                newY = Math.round((float) newY / (float) this.gridSize) * this.gridSize;
            }
            this.dragging.x = Math.max(0, Math.min(this.screenWidth() - this.dragging.width, newX));
            this.dragging.y = Math.max(0, Math.min(this.screenHeight() - this.dragging.height, newY));
            this.dragging.module.setHudPosition(this.dragging.x, this.dragging.y);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 344 || keyCode == 256) { // Right Shift or Escape
            this.onClose();
            return true;
        }
        if (keyCode == 83) { // S
            this.snapToGrid = !this.snapToGrid;
            return true;
        }
        if (keyCode == 71) { // G
            this.showGuides = !this.showGuides;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        for (DraggableModule dm : this.draggables) {
            dm.module.setHudPosition(dm.x, dm.y);
            dm.module.setHudScale(dm.scale);
        }
        VayuHUDClient.getInstance().getModuleManager().saveConfig();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class DraggableModule {
        Module module;
        int x;
        int y;
        int width;
        int height;
        float scale;

        DraggableModule(Module module, int x, int y) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.scale = module.getHudScale();
            this.updateDimensions();
        }

        void updateDimensions() {
            this.width = (int) ((float) this.module.getHudWidth() * this.scale);
            this.height = (int) ((float) this.module.getHudHeight() * this.scale);
        }

        void setScale(float newScale) {
            this.scale = Math.max(0.5f, Math.min(10.0f, newScale));
            this.updateDimensions();
        }
    }
}
