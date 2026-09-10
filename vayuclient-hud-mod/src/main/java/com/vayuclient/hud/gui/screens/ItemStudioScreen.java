package com.vayuclient.hud.gui.screens;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.modules.impl.render.ObjectOverrider;
import com.vayuclient.hud.render.AnimationUtils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ItemStudioScreen extends Screen {
    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight;
    private final AnimationUtils.Animation openAnimation = new AnimationUtils.Animation(0.0f, 220L);
    private long lastUpdate = System.currentTimeMillis();

    // Item Search & List
    private String searchQuery = "";
    private boolean searchFocused = false;
    private long cursorBlinkTime = System.currentTimeMillis();
    private final List<StudioItem> allItems = new ArrayList<>();
    private final List<StudioItem> filteredItems = new ArrayList<>();
    private StudioItem selectedItem;
    private double listScroll = 0.0;

    // 3D Viewport Controls
    private float modelRotX = 20.0f;
    private float modelRotY = 45.0f;
    private float modelZoom = 1.0f;
    private boolean isDraggingModel = false;
    private double lastDragMouseX = 0;
    private double lastDragMouseY = 0;
    private boolean autoRotate = true;

    // Item Specific Transforms
    private float itemScale = 1.0f;
    private float itemX = 0.0f;
    private float itemY = 0.0f;
    private float itemZ = 0.0f;
    private String customTexturePath = "";
    private String statusNotification = "";
    private long statusNotificationTime = 0L;

    public ItemStudioScreen(Screen parent) {
        super(Component.literal("3D Item Studio & Texture Editor"));
        this.parent = parent;
        this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        this.initItemDatabase();
        if (!this.allItems.isEmpty()) {
            this.selectedItem = this.allItems.get(0);
        }
    }

    private void initItemDatabase() {
        this.allItems.clear();
        this.allItems.add(new StudioItem("diamond_sword", "Diamond Sword", "Weapons", "⚔"));
        this.allItems.add(new StudioItem("netherite_sword", "Netherite Sword", "Weapons", "⚔"));
        this.allItems.add(new StudioItem("mace", "Mace", "Weapons", "🔨"));
        this.allItems.add(new StudioItem("shield", "Shield", "Combat", "🛡"));
        this.allItems.add(new StudioItem("totem_of_undying", "Totem of Undying", "Combat", "✨"));
        this.allItems.add(new StudioItem("ender_pearl", "Ender Pearl", "Consumables", "🔮"));
        this.allItems.add(new StudioItem("golden_apple", "Golden Apple", "Consumables", "🍎"));
        this.allItems.add(new StudioItem("enchanted_golden_apple", "Enchanted Golden Apple", "Consumables", "🍏"));
        this.allItems.add(new StudioItem("bow", "Bow", "Weapons", "🏹"));
        this.allItems.add(new StudioItem("crossbow", "Crossbow", "Weapons", "🎯"));
        this.allItems.add(new StudioItem("wind_charge", "Wind Charge", "Combat", "💨"));
        this.allItems.add(new StudioItem("elytra", "Elytra", "Armor", "🪽"));
        this.allItems.add(new StudioItem("diamond_axe", "Diamond Axe", "Weapons", "🪓"));
        this.allItems.add(new StudioItem("netherite_axe", "Netherite Axe", "Weapons", "🪓"));
        this.allItems.add(new StudioItem("diamond_pickaxe", "Diamond Pickaxe", "Tools", "⛏"));
        this.allItems.add(new StudioItem("netherite_pickaxe", "Netherite Pickaxe", "Tools", "⛏"));
        this.allItems.add(new StudioItem("potion", "Splash Potion", "Consumables", "🧪"));
        this.allItems.add(new StudioItem("end_crystal", "End Crystal", "Combat", "💎"));
        this.allItems.add(new StudioItem("firework_rocket", "Firework Rocket", "Consumables", "🎆"));
        this.allItems.add(new StudioItem("cobweb", "Cobweb", "Blocks", "🕸"));
        this.filterItems();
    }

    private void filterItems() {
        this.filteredItems.clear();
        String q = this.searchQuery.toLowerCase(Locale.ROOT).trim();
        for (StudioItem item : this.allItems) {
            if (q.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(q) || item.id.contains(q) || item.category.toLowerCase(Locale.ROOT).contains(q)) {
                this.filteredItems.add(item);
            }
        }
    }

    @Override
    protected void init() {
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();

        this.panelWidth = Math.max(520, Math.min(displayWidth - 40, (int)((double)displayWidth * 0.75)));
        this.panelHeight = Math.max(340, Math.min(displayHeight - 40, (int)((double)displayHeight * 0.82)));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = (displayHeight - this.panelHeight) / 2;

        this.openAnimation.animateTo(1.0f);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), 0xAA030712);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);

        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);

        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;

        if (this.autoRotate && !this.isDraggingModel) {
            this.modelRotY += dt * 35.0f;
            if (this.modelRotY > 360.0f) this.modelRotY -= 360.0f;
        }

        float progress = this.openAnimation.getValue();
        int animY = (int)((float)this.panelY + (1.0f - progress) * 15.0f);
        int alpha = (int)(progress * 255.0f);

        // 1. Studio Main Container
        VayuHUDUI.roundedRect(graphics, this.panelX, animY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0xF00A111A, alpha));
        VayuHUDUI.roundedOutline(graphics, this.panelX, animY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0x3338BDF8, alpha));

        // 2. Header
        this.drawHeader(graphics, this.panelX, animY, this.panelWidth, pxMouseX, pxMouseY, alpha);

        int contentY = animY + 42;
        int contentH = this.panelHeight - 50;

        // 3. Left Panel: Item Search & Selection (Width ~180px)
        int leftW = 190;
        this.drawItemListPanel(graphics, this.panelX + 10, contentY, leftW, contentH, pxMouseX, pxMouseY);

        // 4. Center Panel: 3D Viewport
        int centerW = this.panelWidth - leftW - 240 - 30;
        int centerX = this.panelX + 10 + leftW + 8;
        this.draw3DViewport(graphics, centerX, contentY, centerW, contentH, pxMouseX, pxMouseY);

        // 5. Right Panel: Transform Sliders & Texture Uploader (Width ~230px)
        int rightW = 230;
        int rightX = centerX + centerW + 8;
        this.drawEditorControls(graphics, rightX, contentY, rightW, contentH, pxMouseX, pxMouseY);

        // 6. Toast Notification
        if (!this.statusNotification.isEmpty() && now - this.statusNotificationTime < 3500L) {
            int toastW = this.font.width(this.statusNotification) + 24;
            int toastX = this.panelX + (this.panelWidth - toastW) / 2;
            int toastY = animY + this.panelHeight - 32;
            VayuHUDUI.roundedRect(graphics, toastX, toastY, toastW, 22, 6, 0xEE064E3B);
            VayuHUDUI.roundedOutline(graphics, toastX, toastY, toastW, 22, 6, 0xFF10B981);
            graphics.text(this.font, this.statusNotification, toastX + 12, toastY + 7, 0xFFFFFFFF, true);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha) {
        int headY = y + 10;
        VayuHUDUI.roundedRect(graphics, x + 12, headY, 20, 20, 4, 0xFF0284C7);
        graphics.text(this.font, "🎨", x + 16, headY + 5, 0xFFFFFFFF, true);

        graphics.text(this.font, "3D ITEM STUDIO & TEXTURE EDITOR", x + 38, headY + 3, VayuTheme.PRIMARY, true);
        graphics.text(this.font, "Interactive 3D model customizer, resizer & dynamic texture replacer", x + 38, headY + 12, VayuTheme.TEXT_MUTED, false);

        // Back / Close Button
        int btnW = 20;
        int btnH = 20;
        int btnX = x + w - 32;
        boolean ch = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, btnX, headY, btnW, btnH, 4, ch ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, btnX, headY, btnW, btnH, 4, ch ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(graphics, btnX + btnW / 2, headY + btnH / 2, 8, 0xFFFFFFFF);
    }

    private void drawItemListPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0070D15);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x2238BDF8);

        // Search Field
        int searchH = 22;
        int searchY = y + 8;
        int searchX = x + 8;
        int searchW = w - 16;
        VayuHUDUI.roundedRect(graphics, searchX, searchY, searchW, searchH, 4, this.searchFocused ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, searchX, searchY, searchW, searchH, 4, this.searchFocused ? VayuTheme.PRIMARY : 0x2238BDF8);

        if (this.searchQuery.isEmpty() && !this.searchFocused) {
            graphics.text(this.font, "🔍 Search items...", searchX + 6, searchY + 6, VayuTheme.TEXT_MUTED, false);
        } else {
            graphics.text(this.font, this.searchQuery, searchX + 6, searchY + 6, 0xFFFFFFFF, false);
            if (this.searchFocused && (System.currentTimeMillis() - this.cursorBlinkTime) % 1000L < 500L) {
                int cx = searchX + 6 + this.font.width(this.searchQuery);
                graphics.fill(cx, searchY + 4, cx + 1, searchY + searchH - 4, 0xFF00D2FF);
            }
        }

        // List of Items
        int listY = searchY + searchH + 8;
        int listH = h - (listY - y) - 8;
        int itemH = 26;

        for (int i = 0; i < this.filteredItems.size(); i++) {
            StudioItem item = this.filteredItems.get(i);
            int itemYPos = listY + i * (itemH + 4);
            if (itemYPos + itemH > listY + listH) break;

            boolean isSel = this.selectedItem == item;
            boolean isHov = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= itemYPos && mouseY <= itemYPos + itemH;

            int bg = isSel ? 0xFF0284C7 : (isHov ? 0xE6141E2D : 0xD00A111A);
            VayuHUDUI.roundedRect(graphics, searchX, itemYPos, searchW, itemH, 4, bg);
            VayuHUDUI.roundedOutline(graphics, searchX, itemYPos, searchW, itemH, 4, isSel ? VayuTheme.PRIMARY : 0x1A38BDF8);

            graphics.text(this.font, item.icon, searchX + 6, itemYPos + 8, 0xFFFFFFFF, true);
            graphics.text(this.font, item.name, searchX + 22, itemYPos + 5, isSel ? 0xFFFFFFFF : 0xFFE2E8F0, isSel);
            graphics.text(this.font, item.category, searchX + 22, itemYPos + 15, VayuTheme.TEXT_MUTED, false);
        }
    }

    private void draw3DViewport(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        // Viewport Glass Card
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0050A10);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x3300D2FF);

        // Viewport Header Bar
        graphics.text(this.font, "3D PREVIEW VIEWPORT", x + 10, y + 10, VayuTheme.PRIMARY, true);
        graphics.text(this.font, "Click & drag to rotate (360°) | Scroll to zoom", x + 10, y + 20, VayuTheme.TEXT_MUTED, false);

        // Auto-Rotate Button
        int rotBtnW = 75;
        int rotBtnH = 16;
        int rotBtnX = x + w - rotBtnW - 10;
        int rotBtnY = y + 8;
        boolean rotHov = mouseX >= rotBtnX && mouseX <= rotBtnX + rotBtnW && mouseY >= rotBtnY && mouseY <= rotBtnY + rotBtnH;
        VayuHUDUI.roundedRect(graphics, rotBtnX, rotBtnY, rotBtnW, rotBtnH, 4, this.autoRotate ? 0xFF0284C7 : (rotHov ? 0xE6141E2D : 0xD00A111A));
        graphics.text(this.font, this.autoRotate ? "Auto-Spin: ON" : "Auto-Spin: OFF", rotBtnX + 6, rotBtnY + 4, 0xFFFFFFFF, false);

        // 3D Grid Pedestal
        int midX = x + w / 2;
        int midY = y + h / 2 + 10;
        graphics.fill(midX - 50, midY + 45, midX + 50, midY + 46, 0x3300D2FF);
        graphics.fill(midX - 35, midY + 50, midX + 35, midY + 51, 0x2200D2FF);

        // Render Item Hologram
        if (this.selectedItem != null) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float)midX, (float)midY);
            graphics.pose().scale(this.itemScale * this.modelZoom * 3.5f, this.itemScale * this.modelZoom * 3.5f);

            // Subtle 3D tilt
            float sin = (float)Math.sin(Math.toRadians(this.modelRotY));
            float cos = (float)Math.cos(Math.toRadians(this.modelRotY));
            graphics.pose().translate(sin * 3.0f, (float)Math.sin(System.currentTimeMillis() / 300.0) * 2.0f);

            // Draw Hologram icon & name
            String icon = this.selectedItem.icon;
            int textW = this.font.width(icon);
            graphics.text(this.font, icon, -textW / 2, -4, 0xFFFFFFFF, true);

            graphics.pose().popMatrix();

            // Bottom Selected Label
            String tag = "Editing: " + this.selectedItem.name + " (" + String.format("%.1fx scale", this.itemScale) + ")";
            int tagW = this.font.width(tag);
            graphics.text(this.font, tag, midX - tagW / 2, y + h - 22, 0xFFE2E8F0, true);
        }
    }

    private void drawEditorControls(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0070D15);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x2238BDF8);

        int curY = y + 10;
        int padX = x + 10;
        int btnW = w - 20;

        graphics.text(this.font, "OBJECT CONTROLS", padX, curY, VayuTheme.PRIMARY, true);
        curY += 16;

        // 1. Scale Controls
        graphics.text(this.font, "Model Size: " + String.format("%.2fx", this.itemScale), padX, curY, 0xFFFFFFFF, false);
        curY += 12;

        // Scale Buttons (- / +)
        int halfW = (btnW - 6) / 2;
        boolean minusHov = mouseX >= padX && mouseX <= padX + halfW && mouseY >= curY && mouseY <= curY + 20;
        boolean plusHov = mouseX >= padX + halfW + 6 && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 20;

        VayuHUDUI.roundedRect(graphics, padX, curY, halfW, 20, 4, minusHov ? 0xFF0284C7 : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, padX, curY, halfW, 20, 4, 0x3338BDF8);
        graphics.text(this.font, "- Smaller", padX + 8, curY + 6, 0xFFFFFFFF, false);

        VayuHUDUI.roundedRect(graphics, padX + halfW + 6, curY, halfW, 20, 4, plusHov ? 0xFF0284C7 : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, padX + halfW + 6, curY, halfW, 20, 4, 0x3338BDF8);
        graphics.text(this.font, "+ Larger", padX + halfW + 14, curY + 6, 0xFFFFFFFF, false);

        curY += 28;

        // 2. Preset Quick Buttons
        graphics.text(this.font, "Quick Presets:", padX, curY, VayuTheme.TEXT_MUTED, false);
        curY += 12;

        drawMiniButton(graphics, padX, curY, btnW, 18, "🛡 Small Low Shield", mouseX, mouseY);
        curY += 22;
        drawMiniButton(graphics, padX, curY, btnW, 18, "✨ Compact Totem", mouseX, mouseY);
        curY += 22;
        drawMiniButton(graphics, padX, curY, btnW, 18, "🗡 Mini PvP Sword", mouseX, mouseY);
        curY += 28;

        // 3. Texture Uploader Section
        graphics.text(this.font, "CUSTOM PNG TEXTURE", padX, curY, VayuTheme.PRIMARY, true);
        curY += 14;

        // Upload Button
        boolean uploadHov = mouseX >= padX && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 24;
        VayuHUDUI.roundedRect(graphics, padX, curY, btnW, 24, 4, uploadHov ? 0xFF0284C7 : 0xD01E293B);
        VayuHUDUI.roundedOutline(graphics, padX, curY, btnW, 24, 4, uploadHov ? 0xFF38BDF8 : 0x4438BDF8);
        graphics.text(this.font, "📁 Upload Custom PNG", padX + 16, curY + 8, 0xFFFFFFFF, true);
        curY += 28;

        // Reset Texture Button
        boolean resetHov = mouseX >= padX && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 20;
        VayuHUDUI.roundedRect(graphics, padX, curY, btnW, 20, 4, resetHov ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, padX, curY, btnW, 20, 4, resetHov ? 0xFFEF4444 : 0x33EF4444);
        graphics.text(this.font, "🔄 Reset to Default Texture", padX + 14, curY + 6, resetHov ? 0xFFFFFFFF : 0xFFFCA5A5, false);
        curY += 26;

        // Save & Apply Button
        boolean applyHov = mouseX >= padX && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 24;
        VayuHUDUI.roundedRect(graphics, padX, curY, btnW, 24, 4, applyHov ? 0xFF059669 : 0xD0064E3B);
        VayuHUDUI.roundedOutline(graphics, padX, curY, btnW, 24, 4, applyHov ? 0xFF10B981 : 0x4410B981);
        graphics.text(this.font, "✔ Save & Apply In-Game", padX + 14, curY + 8, 0xFFFFFFFF, true);
    }

    private void drawMiniButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 4, hov ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 4, hov ? VayuTheme.PRIMARY : 0x1A38BDF8);
        graphics.text(this.font, text, x + 8, y + 5, hov ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = DisplaySpace.mouseX((int)event.x());
        int mouseY = DisplaySpace.mouseY((int)event.y());

        // Header Close
        int btnW = 20, btnH = 20, btnX = this.panelX + this.panelWidth - 32, btnY = this.panelY + 10;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.minecraft.gui.setScreen(this.parent);
            return true;
        }

        // Left Panel Item Selection & Search
        int leftW = 190, contentY = this.panelY + 42, searchH = 22, searchY = contentY + 8, searchX = this.panelX + 18, searchW = leftW - 16;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            this.searchFocused = true;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        } else {
            this.searchFocused = false;
        }

        // Item List Click
        int listY = searchY + searchH + 8, itemH = 26;
        for (int i = 0; i < this.filteredItems.size(); i++) {
            int itemYPos = listY + i * (itemH + 4);
            if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= itemYPos && mouseY <= itemYPos + itemH) {
                this.selectedItem = this.filteredItems.get(i);
                this.showToast("Selected " + this.selectedItem.name);
                return true;
            }
        }

        // Viewport 3D Drag & Auto Rotate
        int centerW = this.panelWidth - leftW - 240 - 30;
        int centerX = this.panelX + 10 + leftW + 8;
        int rotBtnW = 75, rotBtnH = 16, rotBtnX = centerX + centerW - rotBtnW - 10, rotBtnY = contentY + 8;

        if (mouseX >= rotBtnX && mouseX <= rotBtnX + rotBtnW && mouseY >= rotBtnY && mouseY <= rotBtnY + rotBtnH) {
            this.autoRotate = !this.autoRotate;
            return true;
        }

        if (mouseX >= centerX && mouseX <= centerX + centerW && mouseY >= contentY && mouseY <= contentY + (this.panelHeight - 50)) {
            this.isDraggingModel = true;
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }

        // Right Panel Editor Controls
        int rightW = 230, rightX = centerX + centerW + 8, padX = rightX + 10, ctrlBtnW = rightW - 20;
        int curY = contentY + 10 + 16 + 12;

        // Smaller / Larger Buttons
        int halfW = (ctrlBtnW - 6) / 2;
        if (mouseX >= padX && mouseX <= padX + halfW && mouseY >= curY && mouseY <= curY + 20) {
            this.itemScale = Math.max(0.2f, this.itemScale - 0.1f);
            return true;
        }
        if (mouseX >= padX + halfW + 6 && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 20) {
            this.itemScale = Math.min(3.0f, this.itemScale + 0.1f);
            return true;
        }
        curY += 28 + 12;

        // Presets
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 18) { // Small Shield
            this.itemScale = 0.70f;
            this.showToast("Applied Small Low Shield preset!");
            return true;
        }
        curY += 22;
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 18) { // Compact Totem
            this.itemScale = 0.65f;
            this.showToast("Applied Compact Totem preset!");
            return true;
        }
        curY += 22;
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 18) { // Mini Sword
            this.itemScale = 0.75f;
            this.showToast("Applied Mini PvP Sword preset!");
            return true;
        }
        curY += 28 + 14;

        // Upload Custom PNG File
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 24) {
            this.openNativeFileUploader();
            return true;
        }
        curY += 28;

        // Reset Texture
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 20) {
            this.resetItemTexture();
            return true;
        }
        curY += 26;

        // Save & Apply In-Game
        if (mouseX >= padX && mouseX <= padX + ctrlBtnW && mouseY >= curY && mouseY <= curY + 24) {
            this.saveAndApplyItem();
            return true;
        }

        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingModel = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.isDraggingModel) {
            this.modelRotY += (float)deltaX * 0.8f;
            this.modelRotX += (float)deltaY * 0.8f;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.modelZoom = (float)Math.max(0.4, Math.min(3.0, this.modelZoom + vertAmount * 0.1));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (this.searchFocused) {
            if (keyCode == 256) {
                this.searchQuery = "";
                this.searchFocused = false;
                this.filterItems();
                return true;
            }
            if (keyCode == 259 && !this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                this.filterItems();
                return true;
            }
            if (keyCode == 257) {
                this.searchFocused = false;
                return true;
            }
            return true;
        }
        if (keyCode == 256) {
            this.minecraft.gui.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char)event.codepoint();
        if (this.searchFocused && c >= ' ') {
            this.searchQuery = this.searchQuery + c;
            this.cursorBlinkTime = System.currentTimeMillis();
            this.filterItems();
            return true;
        }
        return super.charTyped(event);
    }

    private void openNativeFileUploader() {
        if (this.selectedItem == null) return;

        new Thread(() -> {
            try {
                // Try AWT FileDialog for lightweight native Windows explorer popup
                FileDialog fd = new FileDialog((Frame)null, "Select PNG Texture for " + this.selectedItem.name, FileDialog.LOAD);
                fd.setFile("*.png");
                fd.setVisible(true);

                String filename = fd.getFile();
                String dir = fd.getDirectory();

                if (filename != null && dir != null) {
                    File selectedFile = new File(dir, filename);
                    if (selectedFile.exists()) {
                        Path customDir = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud").resolve("custom_textures");
                        if (!Files.exists(customDir)) Files.createDirectories(customDir);

                        Path targetFile = customDir.resolve(this.selectedItem.id + ".png");
                        Files.copy(selectedFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);

                        ObjectOverrider overrider = ObjectOverrider.getInstance();
                        if (overrider != null) {
                            overrider.reloadCustomTextures();
                        }
                        showToast("✔ Uploaded & applied " + filename + " for " + this.selectedItem.name + "!");
                    }
                }
            } catch (Throwable t) {
                showToast("Failed to upload texture: " + t.getMessage());
            }
        }, "VayuTextureUploader").start();
    }

    private void resetItemTexture() {
        if (this.selectedItem == null) return;
        try {
            Path targetFile = FabricLoader.getInstance().getConfigDir().resolve("vayuclient-hud").resolve("custom_textures").resolve(this.selectedItem.id + ".png");
            if (Files.exists(targetFile)) {
                Files.delete(targetFile);
            }
            ObjectOverrider overrider = ObjectOverrider.getInstance();
            if (overrider != null) {
                overrider.reloadCustomTextures();
            }
            this.showToast("Reset " + this.selectedItem.name + " to vanilla texture!");
        } catch (Throwable ignored) {}
    }

    private void saveAndApplyItem() {
        if (this.selectedItem == null) return;
        this.showToast("✔ Saved & Applied " + this.selectedItem.name + " (" + String.format("%.2fx", this.itemScale) + ")!");
    }

    private void showToast(String message) {
        this.statusNotification = message;
        this.statusNotificationTime = System.currentTimeMillis();
    }

    private static class StudioItem {
        final String id;
        final String name;
        final String category;
        final String icon;

        StudioItem(String id, String name, String category, String icon) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.icon = icon;
        }
    }
}
