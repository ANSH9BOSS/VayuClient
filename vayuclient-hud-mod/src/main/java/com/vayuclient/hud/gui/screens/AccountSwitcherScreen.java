package com.vayuclient.hud.gui.screens;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vayuclient.hud.appearance.CustomSkinManager;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountSwitcherScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("VayuAccountSwitcher");
    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight;
    private final AnimationUtils.Animation openAnimation = new AnimationUtils.Animation(0.0f, 220L);
    private long lastUpdate = System.currentTimeMillis();

    // Accounts
    private final List<AccountEntry> accounts = new ArrayList<>();
    private String newAccountName = "";
    private boolean newAccountFocused = false;

    // Skin Setting
    private String premiumSkinInput = "";
    private boolean premiumSkinFocused = false;
    private boolean isSlimModel = false;
    private long cursorBlinkTime = System.currentTimeMillis();

    // Notification Toast
    private String toastMessage = "";
    private long toastTime = 0L;

    public AccountSwitcherScreen(Screen parent) {
        super(Component.literal("Account Switcher & Skin Studio"));
        this.parent = parent;
        this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        this.isSlimModel = CustomSkinManager.getInstance().isSlimModel();
        this.loadProfiles();
    }

    private void loadProfiles() {
        this.accounts.clear();

        // 1. Current active in-memory user
        String currentName = getCurrentUsername();
        AccountEntry currentEntry = new AccountEntry(UUID.randomUUID().toString(), currentName, "Offline", true);
        this.accounts.add(currentEntry);

        // 2. Read from %APPDATA%\VayuClient\profiles.json (Launcher Accounts)
        try {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                Path profilesPath = Paths.get(appData, "VayuClient", "profiles.json");
                if (Files.exists(profilesPath)) {
                    String json = Files.readString(profilesPath);
                    JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("Id") ? obj.get("Id").getAsString() : UUID.randomUUID().toString();
                        String name = obj.has("Username") ? obj.get("Username").getAsString() : "Player";
                        String type = obj.has("AccountType") ? (obj.get("AccountType").getAsInt() == 1 ? "Microsoft" : "Offline") : "Offline";
                        boolean isActive = name.equalsIgnoreCase(currentName);

                        // Avoid exact duplicate names
                        boolean exists = false;
                        for (AccountEntry a : this.accounts) {
                            if (a.username.equalsIgnoreCase(name)) {
                                exists = true;
                                a.accountType = type;
                                a.isActive = isActive;
                                break;
                            }
                        }
                        if (!exists) {
                            this.accounts.add(new AccountEntry(id, name, type, isActive));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed reading launcher profiles: {}", t.getMessage());
        }
    }

    @Override
    protected void init() {
        int dw = DisplaySpace.width();
        int dh = DisplaySpace.height();

        this.panelWidth = Math.max(520, Math.min(dw - 40, (int)((double)dw * 0.76)));
        this.panelHeight = Math.max(340, Math.min(dh - 40, (int)((double)dh * 0.82)));
        this.panelX = (dw - this.panelWidth) / 2;
        this.panelY = (dh - this.panelHeight) / 2;

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

        float progress = this.openAnimation.getValue();
        int animY = (int)((float)this.panelY + (1.0f - progress) * 15.0f);
        int alpha = (int)(progress * 255.0f);

        // Container
        VayuHUDUI.roundedRect(graphics, this.panelX, animY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0xF00A111A, alpha));
        VayuHUDUI.roundedOutline(graphics, this.panelX, animY, this.panelWidth, this.panelHeight, 10, VayuHUDUI.withAlpha(0x3338BDF8, alpha));

        // Header
        this.drawHeader(graphics, this.panelX, animY, this.panelWidth, pxMouseX, pxMouseY, alpha);

        int contentY = animY + 44;
        int contentH = this.panelHeight - 54;

        // Left Panel: Accounts List (Width ~48%)
        int leftW = (this.panelWidth - 30) / 2;
        this.drawAccountsPanel(graphics, this.panelX + 10, contentY, leftW, contentH, pxMouseX, pxMouseY);

        // Right Panel: Skin Studio (Width ~52%)
        int rightX = this.panelX + 10 + leftW + 10;
        int rightW = this.panelWidth - leftW - 30;
        this.drawSkinPanel(graphics, rightX, contentY, rightW, contentH, pxMouseX, pxMouseY);

        // Toast Notification
        if (!this.toastMessage.isEmpty() && now - this.toastTime < 3500L) {
            int toastW = this.font.width(this.toastMessage) + 24;
            int toastX = this.panelX + (this.panelWidth - toastW) / 2;
            int toastY = animY + this.panelHeight - 32;
            VayuHUDUI.roundedRect(graphics, toastX, toastY, toastW, 22, 6, 0xEE064E3B);
            VayuHUDUI.roundedOutline(graphics, toastX, toastY, toastW, 22, 6, 0xFF10B981);
            graphics.text(this.font, this.toastMessage, toastX + 12, toastY + 7, 0xFFFFFFFF, true);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha) {
        int headY = y + 10;
        VayuHUDUI.roundedRect(graphics, x + 12, headY, 20, 20, 4, 0xFF0284C7);
        graphics.text(this.font, "👤", x + 16, headY + 5, 0xFFFFFFFF, true);

        String activeUser = getCurrentUsername();
        graphics.text(this.font, "ACCOUNT SWITCHER & SKIN STUDIO", x + 38, headY + 3, VayuTheme.PRIMARY, true);
        graphics.text(this.font, "Logged In As: " + activeUser + " (Connected to Launcher)", x + 38, headY + 12, VayuTheme.TEXT_MUTED, false);

        // Close Button
        int btnW = 20, btnH = 20, btnX = x + w - 32;
        boolean ch = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= headY && mouseY <= headY + btnH;
        VayuHUDUI.roundedRect(graphics, btnX, headY, btnW, btnH, 4, ch ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, btnX, headY, btnW, btnH, 4, ch ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(graphics, btnX + btnW / 2, headY + btnH / 2, 8, 0xFFFFFFFF);
    }

    private void drawAccountsPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0070D15);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x2238BDF8);

        graphics.text(this.font, "SAVED ACCOUNTS", x + 10, y + 10, VayuTheme.PRIMARY, true);

        int curY = y + 26;
        int cardH = 30;
        int cardW = w - 20;

        for (AccountEntry acc : this.accounts) {
            if (curY + cardH > y + h - 60) break;

            boolean isHov = mouseX >= x + 10 && mouseX <= x + 10 + cardW && mouseY >= curY && mouseY <= curY + cardH;
            int bg = acc.isActive ? 0xFF0284C7 : (isHov ? 0xE6141E2D : 0xD00A111A);
            VayuHUDUI.roundedRect(graphics, x + 10, curY, cardW, cardH, 4, bg);
            VayuHUDUI.roundedOutline(graphics, x + 10, curY, cardW, cardH, 4, acc.isActive ? VayuTheme.PRIMARY : 0x1A38BDF8);

            // Avatar Head Icon
            VayuHUDUI.roundedRect(graphics, x + 14, curY + 4, 22, 22, 3, 0xFF0F1722);
            graphics.text(this.font, acc.accountType.equals("Microsoft") ? "🟢" : "👤", x + 17, curY + 10, 0xFFFFFFFF, true);

            // Username
            graphics.text(this.font, acc.username, x + 42, curY + 7, acc.isActive ? 0xFFFFFFFF : 0xFFE2E8F0, acc.isActive);
            
            // Tag
            String tag = acc.isActive ? "ACTIVE" : acc.accountType;
            int tagColor = acc.isActive ? 0xFF10B981 : 0xFF94A3B8;
            graphics.text(this.font, tag, x + 42, curY + 17, tagColor, false);

            curY += cardH + 5;
        }

        // Add Offline Account Input at Bottom
        int bottomY = y + h - 50;
        graphics.text(this.font, "Quick Switch Offline Name:", x + 10, bottomY, VayuTheme.TEXT_MUTED, false);

        int inputY = bottomY + 12;
        int inputW = w - 85;
        VayuHUDUI.roundedRect(graphics, x + 10, inputY, inputW, 22, 4, this.newAccountFocused ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, x + 10, inputY, inputW, 22, 4, this.newAccountFocused ? VayuTheme.PRIMARY : 0x2238BDF8);

        if (this.newAccountName.isEmpty() && !this.newAccountFocused) {
            graphics.text(this.font, "Enter name...", x + 16, inputY + 6, VayuTheme.TEXT_MUTED, false);
        } else {
            graphics.text(this.font, this.newAccountName, x + 16, inputY + 6, 0xFFFFFFFF, false);
            if (this.newAccountFocused && (System.currentTimeMillis() - this.cursorBlinkTime) % 1000L < 500L) {
                int cx = x + 16 + this.font.width(this.newAccountName);
                graphics.fill(cx, inputY + 4, cx + 1, inputY + 18, 0xFF00D2FF);
            }
        }

        // Switch Button
        int btnW = 60;
        int btnX = x + 10 + inputW + 5;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= inputY && mouseY <= inputY + 22;
        VayuHUDUI.roundedRect(graphics, btnX, inputY, btnW, 22, 4, btnHov ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, btnX, inputY, btnW, 22, 4, btnHov ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "Switch", btnX + 12, inputY + 6, 0xFFFFFFFF, true);
    }

    private void drawSkinPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0070D15);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x2238BDF8);

        graphics.text(this.font, "🎨 IN-GAME SKIN CHANGER", x + 10, y + 10, VayuTheme.PRIMARY, true);

        int curY = y + 26;
        int padX = x + 10;
        int btnW = w - 20;

        // 1. Set By Premium Username
        graphics.text(this.font, "👑 Set Skin by Premium Username:", padX, curY, 0xFFFFFFFF, false);
        curY += 12;

        int skinInputW = btnW - 65;
        VayuHUDUI.roundedRect(graphics, padX, curY, skinInputW, 22, 4, this.premiumSkinFocused ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, padX, curY, skinInputW, 22, 4, this.premiumSkinFocused ? VayuTheme.PRIMARY : 0x2238BDF8);

        if (this.premiumSkinInput.isEmpty() && !this.premiumSkinFocused) {
            graphics.text(this.font, "e.g. Technoblade, Dream...", padX + 6, curY + 6, VayuTheme.TEXT_MUTED, false);
        } else {
            graphics.text(this.font, this.premiumSkinInput, padX + 6, curY + 6, 0xFFFFFFFF, false);
            if (this.premiumSkinFocused && (System.currentTimeMillis() - this.cursorBlinkTime) % 1000L < 500L) {
                int cx = padX + 6 + this.font.width(this.premiumSkinInput);
                graphics.fill(cx, curY + 4, cx + 1, curY + 18, 0xFF00D2FF);
            }
        }

        // Apply Premium Skin Button
        int applyW = 58;
        int applyX = padX + skinInputW + 6;
        boolean appHov = mouseX >= applyX && mouseX <= applyX + applyW && mouseY >= curY && mouseY <= curY + 22;
        VayuHUDUI.roundedRect(graphics, applyX, curY, applyW, 22, 4, appHov ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, applyX, curY, applyW, 22, 4, appHov ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "Apply", applyX + 14, curY + 6, 0xFFFFFFFF, true);

        curY += 32;

        // 2. Upload Skin File
        graphics.text(this.font, "📁 Upload Custom Skin (.PNG):", padX, curY, 0xFFFFFFFF, false);
        curY += 12;

        boolean upHov = mouseX >= padX && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 24;
        VayuHUDUI.roundedRect(graphics, padX, curY, btnW, 24, 4, upHov ? 0xFF0284C7 : 0xD01E293B);
        VayuHUDUI.roundedOutline(graphics, padX, curY, btnW, 24, 4, upHov ? 0xFF38BDF8 : 0x4438BDF8);
        graphics.text(this.font, "📁 Choose PNG Skin File...", padX + 16, curY + 8, 0xFFFFFFFF, true);

        curY += 34;

        // 3. Model Style Selector (Classic Steve 4px vs Slim Alex 3px)
        graphics.text(this.font, "Skin Model Type:", padX, curY, VayuTheme.TEXT_MUTED, false);
        curY += 12;

        int halfW = (btnW - 6) / 2;
        boolean classicHov = mouseX >= padX && mouseX <= padX + halfW && mouseY >= curY && mouseY <= curY + 20;
        boolean slimHov = mouseX >= padX + halfW + 6 && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 20;

        VayuHUDUI.roundedRect(graphics, padX, curY, halfW, 20, 4, !this.isSlimModel ? 0xFF0284C7 : (classicHov ? 0xE6141E2D : 0xD00A111A));
        VayuHUDUI.roundedOutline(graphics, padX, curY, halfW, 20, 4, !this.isSlimModel ? VayuTheme.PRIMARY : 0x2238BDF8);
        graphics.text(this.font, "Classic (4px Steve)", padX + 6, curY + 6, !this.isSlimModel ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, !this.isSlimModel);

        VayuHUDUI.roundedRect(graphics, padX + halfW + 6, curY, halfW, 20, 4, this.isSlimModel ? 0xFF0284C7 : (slimHov ? 0xE6141E2D : 0xD00A111A));
        VayuHUDUI.roundedOutline(graphics, padX + halfW + 6, curY, halfW, 20, 4, this.isSlimModel ? VayuTheme.PRIMARY : 0x2238BDF8);
        graphics.text(this.font, "Slim (3px Alex)", padX + halfW + 12, curY + 6, this.isSlimModel ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, this.isSlimModel);

        curY += 30;

        // 4. Reset Skin Button
        boolean resHov = mouseX >= padX && mouseX <= padX + btnW && mouseY >= curY && mouseY <= curY + 20;
        VayuHUDUI.roundedRect(graphics, padX, curY, btnW, 20, 4, resHov ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, padX, curY, btnW, 20, 4, resHov ? 0xFFEF4444 : 0x33EF4444);
        graphics.text(this.font, "🔄 Reset to Default Vanilla Skin", padX + 16, curY + 6, resHov ? 0xFFFFFFFF : 0xFFFCA5A5, false);
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

        // Left Panel Clicks
        int contentY = this.panelY + 44, leftW = (this.panelWidth - 30) / 2, cardH = 30, cardW = leftW - 20;
        int curY = contentY + 26;

        for (AccountEntry acc : this.accounts) {
            if (mouseX >= this.panelX + 10 && mouseX <= this.panelX + 10 + cardW && mouseY >= curY && mouseY <= curY + cardH) {
                this.performSwitchAccount(acc.username);
                return true;
            }
            curY += cardH + 5;
        }

        // New Account Field Focus
        int bottomY = contentY + (this.panelHeight - 54) - 50, inputY = bottomY + 12, inputW = leftW - 85;
        if (mouseX >= this.panelX + 10 && mouseX <= this.panelX + 10 + inputW && mouseY >= inputY && mouseY <= inputY + 22) {
            this.newAccountFocused = true;
            this.premiumSkinFocused = false;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }

        // Switch Button Click
        int switchBtnW = 60, switchBtnX = this.panelX + 10 + inputW + 5;
        if (mouseX >= switchBtnX && mouseX <= switchBtnX + switchBtnW && mouseY >= inputY && mouseY <= inputY + 22) {
            if (!this.newAccountName.trim().isEmpty()) {
                this.performSwitchAccount(this.newAccountName.trim());
                this.newAccountName = "";
            }
            return true;
        }

        // Right Panel Clicks (Skin)
        int rightX = this.panelX + 10 + leftW + 10, rightW = this.panelWidth - leftW - 30, padX = rightX + 10, skinBtnW = rightW - 20;
        int skinCurY = contentY + 26 + 12;
        int skinInputW = skinBtnW - 65;

        // Premium Skin Input Focus
        if (mouseX >= padX && mouseX <= padX + skinInputW && mouseY >= skinCurY && mouseY <= skinCurY + 22) {
            this.premiumSkinFocused = true;
            this.newAccountFocused = false;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }

        // Apply Premium Skin
        int applyW = 58, applyX = padX + skinInputW + 6;
        if (mouseX >= applyX && mouseX <= applyX + applyW && mouseY >= skinCurY && mouseY <= skinCurY + 22) {
            if (!this.premiumSkinInput.trim().isEmpty()) {
                this.performApplyPremiumSkin(this.premiumSkinInput.trim());
            }
            return true;
        }
        skinCurY += 32 + 12;

        // Upload Skin File Click
        if (mouseX >= padX && mouseX <= padX + skinBtnW && mouseY >= skinCurY && mouseY <= skinCurY + 24) {
            this.openNativeSkinUploader();
            return true;
        }
        skinCurY += 34 + 12;

        // Model Type Toggle
        int halfW = (skinBtnW - 6) / 2;
        if (mouseX >= padX && mouseX <= padX + halfW && mouseY >= skinCurY && mouseY <= skinCurY + 20) {
            this.isSlimModel = false;
            CustomSkinManager.getInstance().setSlimModel(false);
            this.showToast("Set model type to Classic (4px)");
            return true;
        }
        if (mouseX >= padX + halfW + 6 && mouseX <= padX + skinBtnW && mouseY >= skinCurY && mouseY <= skinCurY + 20) {
            this.isSlimModel = true;
            CustomSkinManager.getInstance().setSlimModel(true);
            this.showToast("Set model type to Slim (3px)");
            return true;
        }
        skinCurY += 30;

        // Reset Skin Click
        if (mouseX >= padX && mouseX <= padX + skinBtnW && mouseY >= skinCurY && mouseY <= skinCurY + 20) {
            CustomSkinManager.getInstance().resetSkin();
            this.showToast("Reset skin to default vanilla!");
            return true;
        }

        this.newAccountFocused = false;
        this.premiumSkinFocused = false;
        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (this.newAccountFocused) {
            if (keyCode == 256) {
                this.newAccountFocused = false;
                return true;
            }
            if (keyCode == 259 && !this.newAccountName.isEmpty()) {
                this.newAccountName = this.newAccountName.substring(0, this.newAccountName.length() - 1);
                return true;
            }
            if (keyCode == 257) {
                if (!this.newAccountName.trim().isEmpty()) {
                    this.performSwitchAccount(this.newAccountName.trim());
                    this.newAccountName = "";
                }
                this.newAccountFocused = false;
                return true;
            }
            return true;
        }

        if (this.premiumSkinFocused) {
            if (keyCode == 256) {
                this.premiumSkinFocused = false;
                return true;
            }
            if (keyCode == 259 && !this.premiumSkinInput.isEmpty()) {
                this.premiumSkinInput = this.premiumSkinInput.substring(0, this.premiumSkinInput.length() - 1);
                return true;
            }
            if (keyCode == 257) {
                if (!this.premiumSkinInput.trim().isEmpty()) {
                    this.performApplyPremiumSkin(this.premiumSkinInput.trim());
                }
                this.premiumSkinFocused = false;
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
        if (this.newAccountFocused && c >= ' ' && c <= '~') {
            this.newAccountName += c;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }
        if (this.premiumSkinFocused && c >= ' ' && c <= '~') {
            this.premiumSkinInput += c;
            this.cursorBlinkTime = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    private void performSwitchAccount(String username) {
        switchUser(username, UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString(), "0", "legacy");
        this.loadProfiles();
        this.showToast("✔ Switched active account to " + username + "!");
    }

    private void performApplyPremiumSkin(String username) {
        new Thread(() -> {
            boolean success = CustomSkinManager.getInstance().applyPremiumSkin(username, this.isSlimModel);
            if (success) {
                showToast("✔ Downloaded & applied skin from " + username + "!");
            } else {
                showToast("Failed downloading skin from " + username);
            }
        }, "VayuSkinFetcher").start();
    }

    private void openNativeSkinUploader() {
        new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame)null, "Select Minecraft Skin (.PNG)", FileDialog.LOAD);
                fd.setFile("*.png");
                fd.setVisible(true);

                String filename = fd.getFile();
                String dir = fd.getDirectory();

                if (filename != null && dir != null) {
                    File file = new File(dir, filename);
                    if (file.exists()) {
                        boolean ok = CustomSkinManager.getInstance().applyCustomFileSkin(file, this.isSlimModel);
                        if (ok) {
                            showToast("✔ Applied custom skin " + filename + "!");
                        }
                    }
                }
            } catch (Throwable t) {
                showToast("Skin upload failed: " + t.getMessage());
            }
        }, "VayuSkinPicker").start();
    }

    private static void switchUser(String username, String uuid, String token, String type) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Constructor<?>[] ctors = User.class.getDeclaredConstructors();
            User newUser = null;
            for (Constructor<?> ctor : ctors) {
                ctor.setAccessible(true);
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 4) {
                    newUser = (User) ctor.newInstance(username, uuid, token, type);
                    break;
                } else if (pTypes.length == 6) {
                    UUID u = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
                    try { u = UUID.fromString(uuid); } catch (Throwable ignored) {}
                    Object userType = null;
                    for (Class<?> inner : User.class.getDeclaredClasses()) {
                        if (inner.isEnum()) {
                            Object[] constants = inner.getEnumConstants();
                            if (constants != null && constants.length > 0) {
                                userType = constants[0];
                            }
                        }
                    }
                    newUser = (User) ctor.newInstance(username, u, token, Optional.empty(), Optional.empty(), userType);
                    break;
                }
            }

            if (newUser != null) {
                for (Field f : Minecraft.class.getDeclaredFields()) {
                    if (f.getType() == User.class) {
                        f.setAccessible(true);
                        f.set(mc, newUser);
                        LOGGER.info("Switched Minecraft user in-memory to {}", username);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed switching user via reflection: {}", t.getMessage());
        }
    }

    private String getCurrentUsername() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && mc.getUser().getName() != null) {
            return mc.getUser().getName();
        }
        return "Player";
    }

    private void showToast(String msg) {
        this.toastMessage = msg;
        this.toastTime = System.currentTimeMillis();
    }

    private static class AccountEntry {
        final String id;
        final String username;
        String accountType;
        boolean isActive;

        AccountEntry(String id, String username, String accountType, boolean isActive) {
            this.id = id;
            this.username = username;
            this.accountType = accountType;
            this.isActive = isActive;
        }
    }
}
