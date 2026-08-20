/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  org.lwjgl.glfw.GLFW
 */
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
import com.vayuclient.hud.gui.screens.ModuleConfigScreen;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen
extends Screen {
    private final List<DraggableModule> draggables = new ArrayList<DraggableModule>();
    private DraggableModule dragging = null;
    private DraggableModule selected = null;
    private DraggableModule configTarget = null;
    private int dragOffsetX;
    private int dragOffsetY;
    private DraggableModule resizing = null;
    private int resizeStartX;
    private int resizeStartY;
    private float resizeStartScale;
    private boolean gridEnabled = true;
    private boolean snapToGrid = false;
    private boolean showGuides = true;
    private int gridSize = 10;
    private final Set<Integer> activeGuidesX = new HashSet<Integer>();
    private final Set<Integer> activeGuidesY = new HashSet<Integer>();
    private boolean configPanelOpen = false;
    private AnimationUtils.Animation configPanelAnimation;
    private static final int CONFIG_PANEL_WIDTH = 160;
    private boolean toolbarExpanded = true;
    private AnimationUtils.Animation toolbarAnimation;
    private static final int TOOLBAR_HEIGHT_COLLAPSED = 28;
    private static final int TOOLBAR_HEIGHT_EXPANDED = 66;
    private static final int SNAP_THRESHOLD = 8;
    private static final int NUDGE_AMOUNT = 1;
    private static final int NUDGE_AMOUNT_FAST = 10;
    private static final float SCALE_STEP = 0.1f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 10.0f;

    public HudEditorScreen() {
        super((Component)Component.literal((String)"HUD Editor"));
    }

    private int screenWidth() {
        return DisplaySpace.width();
    }

    private int screenHeight() {
        return DisplaySpace.height();
    }

    private boolean isShiftKeyDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey((long)handle, (int)340) == 1 || GLFW.glfwGetKey((long)handle, (int)344) == 1;
    }

    protected void init() {
        this.draggables.clear();
        if (this.toolbarAnimation == null) {
            this.toolbarAnimation = new AnimationUtils.Animation(1.0f, 150L);
        }
        if (this.configPanelAnimation == null) {
            this.configPanelAnimation = new AnimationUtils.Animation(0.0f, 150L);
        }
        for (Module module : VayuHUDClient.getInstance().getModuleManager().getModules()) {
            if (!module.isEnabled() || !module.isHudVisible()) continue;
            this.draggables.add(new DraggableModule(module, module.getHudX(), module.getHudY()));
        }
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.screenWidth(), this.screenHeight(), VayuHUDUI.withAlpha(-16777216, 80));
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        if (this.dragging == null) {
            this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
            if (this.gridEnabled) {
                this.renderGrid(graphics);
            }
        }
        this.renderCenterGuides(graphics);
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
            this.renderToolbar(graphics, pxMouseX, pxMouseY);
            if (this.configPanelOpen && this.configTarget != null) {
                this.renderConfigPanel(graphics, pxMouseX, pxMouseY);
            }
            this.renderFooter(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void renderCenterGuides(GuiGraphicsExtractor graphics) {
        int centerColor = VayuHUDUI.withAlpha(-16723201, 45);
        int centerX = this.screenWidth() / 2;
        int centerY = this.screenHeight() / 2;
        graphics.fill(centerX, 0, centerX + 1, this.screenHeight(), centerColor);
        graphics.fill(0, centerY, this.screenWidth(), centerY + 1, centerColor);
    }

    private void renderGrid(GuiGraphicsExtractor graphics) {
        int lineColor = VayuHUDUI.withAlpha(1154997472, 28);
        for (int x = 0; x <= this.screenWidth(); x += this.gridSize) {
            graphics.fill(x, 0, x + 1, this.screenHeight(), lineColor);
        }
        for (int y = 0; y <= this.screenHeight(); y += this.gridSize) {
            graphics.fill(0, y, this.screenWidth(), y + 1, lineColor);
        }
    }

    private void renderAlignmentGuides(GuiGraphicsExtractor graphics) {
        int guideColor = VayuTheme.PRIMARY;
        for (int x : this.activeGuidesX) {
            graphics.fill(x, 0, x + 1, this.screenHeight(), guideColor);
        }
        for (int y : this.activeGuidesY) {
            graphics.fill(0, y, this.screenWidth(), y + 1, guideColor);
        }
    }

    private void renderDraggableModule(GuiGraphicsExtractor graphics, DraggableModule dm, int mouseX, int mouseY) {
        int borderWidth;
        int borderColor;
        boolean isHovered = this.isHovered(dm, mouseX, mouseY);
        boolean isSelected = dm == this.selected;
        boolean isDragging = dm == this.dragging;
        boolean isResizing = dm == this.resizing;
        boolean isResizeHandleHovered = this.isResizeHandleHovered(dm, mouseX, mouseY);
        if (isDragging) {
            int bracketLen = 8;
            int bracketColor = VayuTheme.PRIMARY;
            boolean hasOffset = !dm.module.getName().equals("Block Overlay");
            int pad = hasOffset ? (int)(2.0f * dm.scale) : 0;
            int bx = dm.x - pad;
            int by = dm.y - pad;
            int bw = dm.width;
            int bh = dm.height;
            graphics.fill(bx - 1, by - 1, bx + bracketLen, by + 1, bracketColor);
            graphics.fill(bx - 1, by - 1, bx + 1, by + bracketLen, bracketColor);
            graphics.fill(bx + bw - bracketLen, by - 1, bx + bw + 1, by + 1, bracketColor);
            graphics.fill(bx + bw - 1, by - 1, bx + bw + 1, by + bracketLen, bracketColor);
            graphics.fill(bx - 1, by + bh - 1, bx + bracketLen, by + bh + 1, bracketColor);
            graphics.fill(bx - 1, by + bh - bracketLen, bx + 1, by + bh + 1, bracketColor);
            graphics.fill(bx + bw - bracketLen, by + bh - 1, bx + bw + 1, by + bh + 1, bracketColor);
            graphics.fill(bx + bw - 1, by + bh - bracketLen, bx + bw + 1, by + bh + 1, bracketColor);
            return;
        }
        if (isResizing) {
            borderColor = VayuTheme.PRIMARY;
            borderWidth = 2;
        } else if (isSelected) {
            borderColor = VayuTheme.PRIMARY;
            borderWidth = 2;
        } else if (isHovered) {
            borderColor = VayuTheme.SECONDARY;
            borderWidth = 2;
        } else {
            borderColor = VayuTheme.BORDER_SUBTLE;
            borderWidth = 1;
        }
        boolean hasOffset = !dm.module.getName().equals("Block Overlay");
        int pad = hasOffset ? (int)(2.0f * dm.scale) : 0;
        int bx = dm.x - pad;
        int by = dm.y - pad;
        int bw = dm.width;
        int bh = dm.height;
        graphics.fill(bx, by, bx + bw, by + borderWidth, borderColor);
        graphics.fill(bx, by + bh - borderWidth, bx + bw, by + bh, borderColor);
        graphics.fill(bx, by, bx + borderWidth, by + bh, borderColor);
        graphics.fill(bx + bw - borderWidth, by, bx + bw, by + bh, borderColor);
        if (isHovered || isSelected || isResizing) {
            int handleSize = 12;
            int handleX = bx + bw - handleSize;
            int handleY = by + bh - handleSize;
            int handleColor = isResizeHandleHovered || isResizing ? VayuTheme.PRIMARY : VayuTheme.SECONDARY;
            graphics.fill(handleX + 2, handleY + handleSize - 2, handleX + handleSize, handleY + handleSize, handleColor);
            graphics.fill(handleX + handleSize - 2, handleY + 2, handleX + handleSize, handleY + handleSize, handleColor);
            graphics.fill(handleX + 5, handleY + handleSize - 4, handleX + handleSize - 2, handleY + handleSize - 2, handleColor);
            graphics.fill(handleX + handleSize - 4, handleY + 5, handleX + handleSize - 2, handleY + handleSize - 2, handleColor);
        }
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float animVal = this.toolbarAnimation.getValue();
        int toolbarWidth = 340;
        int toolbarX = (this.screenWidth() - toolbarWidth) / 2;
        int toolbarY = 8;
        int currentHeight = (int)(30.0f + 38.0f * animVal);
        VayuHUDUI.roundedRect(graphics, toolbarX, toolbarY, toolbarWidth, currentHeight, 6, VayuTheme.GLASS_PANEL);
        VayuHUDUI.outline(graphics, toolbarX, toolbarY, toolbarWidth, currentHeight, VayuTheme.BORDER_SUBTLE);
        graphics.fill(toolbarX, toolbarY, toolbarX + toolbarWidth, toolbarY + 2, VayuTheme.PRIMARY);
        graphics.text(this.font, "VAYU HUD CANVAS", toolbarX + 10, toolbarY + 8, VayuTheme.TEXT_PRIMARY, true);
        String gridInfo = this.gridSize + "px";
        graphics.text(this.font, gridInfo, toolbarX + toolbarWidth - this.font.width(gridInfo) - 30, toolbarY + 8, VayuTheme.TEXT_MUTED, false);
        String collapseIcon = this.toolbarExpanded ? "^" : "v";
        graphics.text(this.font, collapseIcon, toolbarX + toolbarWidth - 16, toolbarY + 8, VayuTheme.TEXT_MUTED, false);
        if (animVal > 0.5f) {
            int btnX;
            int btnY = toolbarY + 26;
            int btnHeight = 22;
            int btnSpacing = 8;
            int gridStartX = btnX = toolbarX + 12;
            String gridText = this.gridEnabled ? "[#] Grid" : "[ ] Grid";
            int gridBtnWidth = this.font.width(gridText) + 14;
            int gridBtnColor = this.gridEnabled ? VayuTheme.PRIMARY : VayuTheme.GLASS_PANEL;
            VayuHUDUI.roundedRect(graphics, btnX, btnY, gridBtnWidth, btnHeight, 4, gridBtnColor);
            graphics.text(this.font, gridText, btnX + 7, btnY + 7, this.gridEnabled ? 0xFF050A10 : VayuTheme.TEXT_MUTED, true);
            int snapStartX = btnX += gridBtnWidth + btnSpacing;
            String snapText = this.snapToGrid ? "[*] Snap" : "[ ] Snap";
            int snapBtnWidth = this.font.width(snapText) + 14;
            int snapBtnColor = this.snapToGrid ? VayuTheme.PRIMARY : VayuTheme.GLASS_PANEL;
            VayuHUDUI.roundedRect(graphics, btnX, btnY, snapBtnWidth, btnHeight, 4, snapBtnColor);
            graphics.text(this.font, snapText, btnX + 7, btnY + 7, this.snapToGrid ? 0xFF050A10 : VayuTheme.TEXT_MUTED, true);
            int guidesStartX = btnX += snapBtnWidth + btnSpacing;
            String guidesText = this.showGuides ? "[*] Guides" : "[ ] Guides";
            int guidesBtnWidth = this.font.width(guidesText) + 14;
            int guidesBtnColor = this.showGuides ? VayuTheme.PRIMARY : VayuTheme.GLASS_PANEL;
            VayuHUDUI.roundedRect(graphics, btnX, btnY, guidesBtnWidth, btnHeight, 4, guidesBtnColor);
            graphics.text(this.font, guidesText, btnX + 7, btnY + 7, this.showGuides ? 0xFF050A10 : VayuTheme.TEXT_MUTED, true);
            int infoY = btnY + btnHeight + 4;
            int infoOn = VayuTheme.TEXT_SECONDARY;
            int infoOff = VayuTheme.TEXT_MUTED;
            graphics.text(this.font, "G - overlay", gridStartX + 2, infoY, this.gridEnabled ? infoOn : infoOff, false);
            graphics.text(this.font, "S - snap", snapStartX + 2, infoY, this.snapToGrid ? infoOn : infoOff, false);
            graphics.text(this.font, "A - align", guidesStartX + 2, infoY, this.showGuides ? infoOn : infoOff, false);
        }
    }

    private void renderConfigPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        float panelAnim = this.configPanelAnimation.getValue();
        if (panelAnim < 0.01f) {
            return;
        }
        int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 160 - 10);
        int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 200));
        int panelHeight = 150;
        int currentWidth = (int)(160.0f * panelAnim);
        VayuHUDUI.roundedRect(graphics, panelX, panelY, currentWidth, panelHeight, 5, -234156528);
        VayuHUDUI.outline(graphics, panelX, panelY, currentWidth, panelHeight, VayuTheme.BORDER_SUBTLE);
        graphics.fill(panelX, panelY, panelX + currentWidth, panelY + 2, VayuTheme.PRIMARY);
        if (panelAnim > 0.5f) {
            Component title = VayuFonts.moduleName(this.configTarget.module.getDisplayName());
            graphics.text(this.font, title, panelX + 10, panelY + 10, VayuTheme.TEXT_PRIMARY, false);
            graphics.text(this.font, "Quick Actions", panelX + 10, panelY + 26, VayuTheme.TEXT_MUTED, false);
            int actionY = panelY + 45;
            graphics.text(this.font, "[R] Reset Position", panelX + 10, actionY, VayuTheme.TEXT_SECONDARY, false);
            graphics.text(this.font, "[X] Center Horizontal", panelX + 10, actionY += 22, VayuTheme.TEXT_SECONDARY, false);
            graphics.text(this.font, "[Y] Center Vertical", panelX + 10, actionY += 22, VayuTheme.TEXT_SECONDARY, false);
            graphics.text(this.font, "[*] Full Settings...", panelX + 10, actionY += 28, VayuTheme.PRIMARY, false);
        }
    }

    private void renderPositionInfo(GuiGraphicsExtractor graphics, DraggableModule dm) {
        String posText = this.resizing != null && dm == this.resizing ? String.format("%d, %d  %.0f%%", dm.x, dm.y, Float.valueOf(dm.scale * 100.0f)) : String.format("%d, %d", dm.x, dm.y);
        int posWidth = this.font.width(posText);
        int infoX = dm.x + dm.width / 2 - posWidth / 2;
        int infoY = dm.y + dm.height + 4;
        infoX = Math.max(4, Math.min(this.screenWidth() - posWidth - 4, infoX));
        if (infoY + 10 > this.screenHeight() - 20) {
            infoY = dm.y - 12;
        }
        VayuHUDUI.roundedRect(graphics, infoX - 5, infoY - 3, posWidth + 10, 14, 3, -435153640);
        graphics.text(this.font, posText, infoX, infoY, VayuHUDUI.withAlpha(-7303024, 210), false);
    }

    private void renderFooter(GuiGraphicsExtractor graphics) {
        String hints = "Drag \u2022 Right-click options \u2022 ESC to save";
        int hintsWidth = this.font.width(hints);
        graphics.text(this.font, hints, (this.screenWidth() - hintsWidth) / 2, this.screenHeight() - 12, VayuHUDUI.withAlpha(-7303024, 120), false);
    }

    private boolean isHovered(DraggableModule dm, int mouseX, int mouseY) {
        int padding = 6;
        return mouseX >= dm.x - padding && mouseX <= dm.x + dm.width + padding && mouseY >= dm.y - padding && mouseY <= dm.y + dm.height + padding;
    }

    private boolean isResizeHandleHovered(DraggableModule dm, int mouseX, int mouseY) {
        int handleSize = 16;
        int handleX = dm.x + dm.width - handleSize;
        int handleY = dm.y + dm.height - handleSize;
        return mouseX >= handleX && mouseX <= dm.x + dm.width + 4 && mouseY >= handleY && mouseY <= dm.y + dm.height + 4;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();
        if (this.configPanelOpen && this.configTarget != null && this.configPanelAnimation.getValue() > 0.5f) {
            int panelX = Math.min(this.configTarget.x + this.configTarget.width + 10, this.screenWidth() - 160 - 10);
            int panelY = Math.max(10, Math.min(this.configTarget.y, this.screenHeight() - 200));
            int panelHeight = 150;
            int currentWidth = (int)(160.0f * this.configPanelAnimation.getValue());
            if (mouseX >= (double)panelX && mouseX <= (double)(panelX + currentWidth) && mouseY >= (double)panelY && mouseY <= (double)(panelY + panelHeight)) {
                return this.handleConfigPanelClick((int)mouseX, (int)mouseY, panelX, panelY);
            }
            this.configPanelAnimation.animateTo(0.0f);
            this.configPanelOpen = false;
            return true;
        }
        if (button == 1) {
            for (DraggableModule dm : this.draggables) {
                if (!this.isHovered(dm, (int)mouseX, (int)mouseY)) continue;
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
            for (DraggableModule dm : this.draggables) {
                if (!this.isResizeHandleHovered(dm, (int)mouseX, (int)mouseY)) continue;
                this.resizing = dm;
                this.selected = dm;
                this.resizeStartX = (int)mouseX;
                this.resizeStartY = (int)mouseY;
                this.resizeStartScale = dm.scale;
                return true;
            }
            for (DraggableModule dm : this.draggables) {
                if (!this.isHovered(dm, (int)mouseX, (int)mouseY)) continue;
                this.dragging = dm;
                this.selected = dm;
                this.dragOffsetX = (int)mouseX - dm.x;
                this.dragOffsetY = (int)mouseY - dm.y;
                return true;
            }
        }
        if (this.toolbarExpanded && this.toolbarAnimation.getValue() > 0.5f) {
            int toolbarWidth = 360;
            int toolbarX = (this.screenWidth() - toolbarWidth) / 2;
            int toolbarY = 10;
            int currentHeight = (int)(66.0f * this.toolbarAnimation.getValue());
            if (mouseX >= (double)toolbarX && mouseX <= (double)(toolbarX + toolbarWidth) && mouseY >= (double)toolbarY && mouseY <= (double)(toolbarY + currentHeight)) {
                return this.handleToolbarClick((int)mouseX, (int)mouseY, toolbarX, toolbarY);
            }
        }
        if (button == 0) {
            this.selected = null;
        }
        return super.mouseClicked(event, bl);
    }

    private boolean handleToolbarClick(int mouseX, int mouseY, int toolbarX, int toolbarY) {
        int btnY = toolbarY + 22;
        int btnHeight = 18;
        int btnSpacing = 8;
        int btnX = toolbarX + 12;
        String gridText = this.gridEnabled ? "\u229e Grid" : "\u229f Grid";
        int gridBtnWidth = this.font.width(gridText) + 12;
        if (mouseX >= btnX && mouseX <= btnX + gridBtnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight) {
            this.gridEnabled = !this.gridEnabled;
            return true;
        }
        String snapText = this.snapToGrid ? "\u25c9 Snap" : "\u25cb Snap";
        int snapBtnWidth = this.font.width(snapText) + 12;
        if (mouseX >= (btnX += gridBtnWidth + btnSpacing) && mouseX <= btnX + snapBtnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight) {
            this.snapToGrid = !this.snapToGrid;
            return true;
        }
        String guidesText = this.showGuides ? "\u25c8 Guides" : "\u25c7 Guides";
        int guidesBtnWidth = this.font.width(guidesText) + 12;
        if (mouseX >= (btnX += snapBtnWidth + btnSpacing) && mouseX <= btnX + guidesBtnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight) {
            this.showGuides = !this.showGuides;
            return true;
        }
        return true;
    }

    private boolean handleConfigPanelClick(int mouseX, int mouseY, int panelX, int panelY) {
        if (this.configTarget == null) {
            return false;
        }
        int actionY = panelY + 45;
        int actionHeight = 16;
        if (mouseY >= actionY && mouseY <= actionY + actionHeight) {
            this.configTarget.x = 10;
            this.configTarget.y = 10;
            this.configTarget.module.setHudPosition(10, 10);
            return true;
        }
        if (mouseY >= (actionY += 22) && mouseY <= actionY + actionHeight) {
            this.configTarget.x = (this.screenWidth() - this.configTarget.width) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 22) && mouseY <= actionY + actionHeight) {
            this.configTarget.y = (this.screenHeight() - this.configTarget.height) / 2;
            this.configTarget.module.setHudPosition(this.configTarget.x, this.configTarget.y);
            return true;
        }
        if (mouseY >= (actionY += 28) && mouseY <= actionY + actionHeight) {
            Minecraft.getInstance().gui.setScreen((Screen)new ModuleConfigScreen(this.configTarget.module, this));
            return true;
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == 0) {
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

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        if (this.resizing != null) {
            int deltaFromStart = (int)mouseX - this.resizeStartX + (int)mouseY - this.resizeStartY;
            float scaleDelta = (float)deltaFromStart / 100.0f;
            this.resizing.setScale(this.resizeStartScale + scaleDelta);
            this.resizing.module.setHudScale(this.resizing.scale);
            return true;
        }
        if (this.dragging != null) {
            int newX = (int)mouseX - this.dragOffsetX;
            int newY = (int)mouseY - this.dragOffsetY;
            this.activeGuidesX.clear();
            this.activeGuidesY.clear();
            if (this.snapToGrid) {
                newX = Math.round((float)newX / (float)this.gridSize) * this.gridSize;
                newY = Math.round((float)newY / (float)this.gridSize) * this.gridSize;
            }
            if (this.showGuides && this.snapToGrid) {
                int draggingCenterX = newX + this.dragging.width / 2;
                int draggingCenterY = newY + this.dragging.height / 2;
                int draggingRight = newX + this.dragging.width;
                int draggingBottom = newY + this.dragging.height;
                int screenCenterX = this.screenWidth() / 2;
                int screenCenterY = this.screenHeight() / 2;
                if (Math.abs(draggingCenterX - screenCenterX) < 8) {
                    newX = screenCenterX - this.dragging.width / 2;
                    this.activeGuidesX.add(screenCenterX);
                }
                if (Math.abs(draggingCenterY - screenCenterY) < 8) {
                    newY = screenCenterY - this.dragging.height / 2;
                    this.activeGuidesY.add(screenCenterY);
                }
                if (Math.abs(newX) < 8) {
                    newX = 0;
                    this.activeGuidesX.add(0);
                }
                if (Math.abs(draggingRight - this.screenWidth()) < 8) {
                    newX = this.screenWidth() - this.dragging.width;
                    this.activeGuidesX.add(this.screenWidth());
                }
                if (Math.abs(newY) < 8) {
                    newY = 0;
                    this.activeGuidesY.add(0);
                }
                if (Math.abs(draggingBottom - this.screenHeight()) < 8) {
                    newY = this.screenHeight() - this.dragging.height;
                    this.activeGuidesY.add(this.screenHeight());
                }
                for (DraggableModule other : this.draggables) {
                    if (other == this.dragging) continue;
                    int otherCenterX = other.x + other.width / 2;
                    int otherCenterY = other.y + other.height / 2;
                    int otherRight = other.x + other.width;
                    int otherBottom = other.y + other.height;
                    if (Math.abs(newX - other.x) < 8) {
                        newX = other.x;
                        this.activeGuidesX.add(other.x);
                    }
                    if (Math.abs(draggingRight - otherRight) < 8) {
                        newX = otherRight - this.dragging.width;
                        this.activeGuidesX.add(otherRight);
                    }
                    if (Math.abs(newY - other.y) < 8) {
                        newY = other.y;
                        this.activeGuidesY.add(other.y);
                    }
                    if (Math.abs(draggingBottom - otherBottom) < 8) {
                        newY = otherBottom - this.dragging.height;
                        this.activeGuidesY.add(otherBottom);
                    }
                    if (Math.abs(draggingCenterX - otherCenterX) < 8) {
                        newX = otherCenterX - this.dragging.width / 2;
                        this.activeGuidesX.add(otherCenterX);
                    }
                    if (Math.abs(draggingCenterY - otherCenterY) >= 8) continue;
                    newY = otherCenterY - this.dragging.height / 2;
                    this.activeGuidesY.add(otherCenterY);
                }
            }
            this.dragging.x = Math.max(0, Math.min(this.screenWidth() - this.dragging.width, newX));
            this.dragging.y = Math.max(0, Math.min(this.screenHeight() - this.dragging.height, newY));
            this.dragging.module.setHudPosition(this.dragging.x, this.dragging.y);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        double pxMouseX = DisplaySpace.mouseX(mouseX);
        double pxMouseY = DisplaySpace.mouseY(mouseY);
        if (this.toolbarExpanded && this.toolbarAnimation.getValue() > 0.5f) {
            int toolbarWidth = 360;
            int toolbarX = (this.screenWidth() - toolbarWidth) / 2;
            int toolbarY = 10;
            int currentHeight = (int)(66.0f * this.toolbarAnimation.getValue());
            if (pxMouseX >= (double)toolbarX && pxMouseX <= (double)(toolbarX + toolbarWidth) && pxMouseY >= (double)toolbarY && pxMouseY <= (double)(toolbarY + currentHeight)) {
                this.gridSize = vertAmount > 0.0 ? Math.min(50, this.gridSize + 5) : Math.max(5, this.gridSize - 5);
                return true;
            }
        }
        if (this.isShiftKeyDown()) {
            this.gridSize = vertAmount > 0.0 ? Math.min(50, this.gridSize + 5) : Math.max(5, this.gridSize - 5);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizAmount, vertAmount);
    }

    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 258) {
            this.toolbarExpanded = !this.toolbarExpanded;
            this.toolbarAnimation.animateTo(this.toolbarExpanded ? 1.0f : 0.0f);
            return true;
        }
        if (keyCode == 71) {
            this.gridEnabled = !this.gridEnabled;
            return true;
        }
        if (keyCode == 83) {
            this.snapToGrid = !this.snapToGrid;
            return true;
        }
        if (keyCode == 65) {
            this.showGuides = !this.showGuides;
            return true;
        }
        if ((keyCode == 61 || keyCode == 334) && this.selected != null) {
            float step = this.isShiftKeyDown() ? 0.5f : 0.1f;
            this.selected.setScale(this.selected.scale + step);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if ((keyCode == 45 || keyCode == 333) && this.selected != null) {
            float step = this.isShiftKeyDown() ? 0.5f : 0.1f;
            this.selected.setScale(this.selected.scale - step);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if ((keyCode == 48 || keyCode == 320) && this.selected != null) {
            this.selected.setScale(2.0f);
            this.selected.module.setHudScale(this.selected.scale);
            return true;
        }
        if (this.selected != null) {
            int nudge = this.isShiftKeyDown() ? 10 : 1;
            boolean moved = false;
            switch (keyCode) {
                case 262: {
                    this.selected.x = Math.min(this.screenWidth() - this.selected.width, this.selected.x + nudge);
                    moved = true;
                    break;
                }
                case 263: {
                    this.selected.x = Math.max(0, this.selected.x - nudge);
                    moved = true;
                    break;
                }
                case 264: {
                    this.selected.y = Math.min(this.screenHeight() - this.selected.height, this.selected.y + nudge);
                    moved = true;
                    break;
                }
                case 265: {
                    this.selected.y = Math.max(0, this.selected.y - nudge);
                    moved = true;
                }
            }
            if (moved) {
                if (this.snapToGrid) {
                    this.selected.x = Math.round((float)this.selected.x / (float)this.gridSize) * this.gridSize;
                    this.selected.y = Math.round((float)this.selected.y / (float)this.gridSize) * this.gridSize;
                }
                this.selected.module.setHudPosition(this.selected.x, this.selected.y);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    public void onClose() {
        for (DraggableModule dm : this.draggables) {
            dm.module.setHudPosition(dm.x, dm.y);
            dm.module.setHudScale(dm.scale);
        }
        VayuHUDClient.getInstance().getModuleManager().saveConfig();
        super.onClose();
    }

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
            this.width = (int)((float)this.module.getHudWidth() * this.scale);
            this.height = (int)((float)this.module.getHudHeight() * this.scale);
        }

        void setScale(float newScale) {
            this.scale = Math.max(0.5f, Math.min(10.0f, newScale));
            this.updateDimensions();
        }
    }
}

