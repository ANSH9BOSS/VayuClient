package com.vayuclient.hud.launcher;

import java.util.LinkedHashMap;
import java.util.Map;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.launcher.LauncherSkinPreference;
import com.vayuclient.hud.launcher.OptionalMenuIntegrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class LauncherRenderer {
    static final String DOMAIN = "vayuclient-hud";
    private static final Identifier LOGO_ID = DisplaySpace.texture(Identifier.fromNamespaceAndPath(DOMAIN, "textures/gui/title-vayuclient-logo.png"));
    private static final Map<String, int[]> regions = new LinkedHashMap<>();
    private static long screenOpenTime = 0L;

    private LauncherRenderer() {}

    public static void onScreenOpen() {
        screenOpenTime = System.currentTimeMillis();
    }

    public static String getClickedButton(int mx, int my) {
        for (Map.Entry<String, int[]> e : regions.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    public static boolean isSkinToggleClicked(int screenW, int screenH, int mouseX, int mouseY) {
        int[] bounds = skinToggleBounds(screenW, screenH);
        return isHovered(bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    public static boolean isDiscordClicked(int screenW, int screenH, int mouseX, int mouseY) {
        int[] bounds = discordBounds(screenW, screenH);
        return isHovered(bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. VAYUCLIENT MAIN TITLE / HOME SCREEN (RESPONSIVE CYBER-AERO)
    // ═══════════════════════════════════════════════════════════════

    public static void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        regions.clear();
        if (screenOpenTime == 0L) {
            screenOpenTime = System.currentTimeMillis();
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - screenOpenTime);
        float animT = Math.min(1.0f, (float) elapsed / 350.0f);
        float easeT = VayuTheme.easeOutCubic(animT);

        // Dark vignette background
        g.fill(0, 0, screenW, screenH, 0x88050A10);

        int centerX = screenW / 2;
        int centerY = screenH / 2;

        // Top Navigation Bar
        renderTopBar(g, font, screenW, mouseX, mouseY, easeT);

        // Header Title / Logo
        int logoW = Math.min(260, Math.max(160, (int) (screenW * 0.38f)));
        int logoH = (int) (logoW * (161.0f / 510.0f));
        int logoY = Math.max(28, centerY - 110);
        float logoScale = VayuTheme.lerp(0.96f, 1.0f, easeT);

        g.pose().pushMatrix();
        g.pose().translate((float) centerX, (float) (logoY + logoH / 2));
        g.pose().scale(logoScale, logoScale);
        g.pose().translate((float) (-centerX), (float) (-logoY - logoH / 2));
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO_ID, centerX - logoW / 2, logoY, 0.0f, 0.0f, logoW, logoH, 510, 161, 510, 161);
        g.pose().popMatrix();

        // Subtitle badge
        String sub = "v1.9.0 - FABRIC 26.2";
        int subW = font.width(sub);
        int badgePad = 8;
        int badgeX = centerX - (subW + badgePad * 2) / 2;
        int badgeY = logoY + logoH + 4;
        VayuHUDUI.roundedRect(g, badgeX, badgeY, subW + badgePad * 2, 16, 4, 0xD00A111A);
        VayuHUDUI.roundedOutline(g, badgeX, badgeY, subW + badgePad * 2, 16, 4, 0x3338BDF8);
        g.text(font, sub, badgeX + badgePad, badgeY + 4, VayuTheme.PRIMARY, true);

        // ═══════════════════════════════════════════════════════════
        // 2x2 MODULAR GAME MODE CARDS
        // ═══════════════════════════════════════════════════════════
        int cardGridW = Math.min(480, Math.max(280, screenW - 32));
        int cardGap = 10;
        int cardW = (cardGridW - cardGap) / 2;
        int cardH = Math.min(54, Math.max(42, (int) (screenH * 0.11f)));
        int startX = centerX - cardGridW / 2;
        int startY = badgeY + 24;

        int row1Y = startY;
        int row2Y = startY + cardH + cardGap;
        int col1X = startX;
        int col2X = startX + cardW + cardGap;

        // Card 1: Singleplayer
        reg("singleplayer", col1X, row1Y, cardW, cardH);
        renderGameModeCard(g, font, col1X, row1Y, cardW, cardH, "SINGLEPLAYER", "Offline worlds & maps", 1, mouseX, mouseY);

        // Card 2: Multiplayer
        reg("multiplayer", col2X, row1Y, cardW, cardH);
        renderGameModeCard(g, font, col2X, row1Y, cardW, cardH, "MULTIPLAYER", "Servers, Arenas & LAN", 2, mouseX, mouseY);

        // Card 3: Vayu HUD Mods
        reg("vayu_hud_menu", col1X, row2Y, cardW, cardH);
        renderGameModeCard(g, font, col1X, row2Y, cardW, cardH, "VAYU HUD MODS", "Modules & Canvas Editor", 3, mouseX, mouseY);

        // Card 4: Waypoints & Navigation
        reg("waypoints", col2X, row2Y, cardW, cardH);
        renderGameModeCard(g, font, col2X, row2Y, cardW, cardH, "WAYPOINTS", "Saved Markers & Radar", 4, mouseX, mouseY);

        // ═══════════════════════════════════════════════════════════
        // BOTTOM DOCK (OPTIONS, FRIENDS, PACKS, MODS, DISCORD, QUIT)
        // ═══════════════════════════════════════════════════════════
        renderBottomDock(g, font, screenW, screenH, mouseX, mouseY);
    }

    private static void renderGameModeCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, String desc, int type, int mouseX, int mouseY) {
        boolean hover = isHovered(x, y, w, h, mouseX, mouseY);
        int bg = hover ? 0xE6141E2D : 0xD00A111A;
        int border = hover ? VayuTheme.PRIMARY : 0x2A38BDF8;

        VayuHUDUI.roundedRect(g, x, y, w, h, 6, bg);
        VayuHUDUI.roundedOutline(g, x, y, w, h, 6, border);

        // Left Icon Box
        int iconBoxSize = h - 14;
        int iconBoxX = x + 7;
        int iconBoxY = y + 7;
        int iconBg = hover ? 0xFF0284C7 : 0xFF0F1722;
        VayuHUDUI.roundedRect(g, iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, 4, iconBg);
        VayuHUDUI.roundedOutline(g, iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, 4, hover ? VayuTheme.PRIMARY : 0x3338BDF8);

        int iconColor = hover ? 0xFFFFFFFF : VayuTheme.PRIMARY;
        int cx = iconBoxX + iconBoxSize / 2;
        int cy = iconBoxY + iconBoxSize / 2;

        if (type == 1) {
            VayuHUDUI.drawPlayVector(g, cx, cy, 14, iconColor);
        } else if (type == 2) {
            VayuHUDUI.drawMultiplayerVector(g, cx, cy, 14, iconColor);
        } else if (type == 3) {
            VayuHUDUI.drawModsVector(g, cx, cy, 14, iconColor);
        } else {
            VayuHUDUI.drawSettingsVector(g, cx, cy, 14, iconColor);
        }

        // Title & Description
        int textX = iconBoxX + iconBoxSize + 10;
        int textY = y + (h - 22) / 2;
        g.text(font, title, textX, textY, hover ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);
        g.text(font, desc, textX, textY + 12, hover ? 0xFFCBD5E1 : VayuTheme.TEXT_MUTED, false);
    }

    private static void renderBottomDock(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        int dockH = 28;
        int dockY = screenH - dockH - 12;
        int btnW = Math.min(80, Math.max(54, (screenW - 140) / 6));
        int gap = 6;
        int quitW = 32;
        int totalW = btnW * 5 + quitW + gap * 5;
        int startX = (screenW - totalW) / 2;

        int curX = startX;

        // 1. Settings
        reg("settings", curX, dockY, btnW, dockH);
        renderDockButton(g, font, curX, dockY, btnW, dockH, "Settings", 1, mouseX, mouseY);
        curX += btnW + gap;

        // 2. Friends & Social (Highlighted with subtle cyan border)
        reg("friends", curX, dockY, btnW, dockH);
        renderDockButton(g, font, curX, dockY, btnW, dockH, "Friends", 2, mouseX, mouseY);
        curX += btnW + gap;

        // 3. Resource Packs
        reg("box", curX, dockY, btnW, dockH);
        renderDockButton(g, font, curX, dockY, btnW, dockH, "Packs", 3, mouseX, mouseY);
        curX += btnW + gap;

        // 4. Mod Menu / Replays
        if (OptionalMenuIntegrations.isFlashbackReplaysAvailable()) {
            reg("flashback_replays", curX, dockY, btnW, dockH);
            renderDockButton(g, font, curX, dockY, btnW, dockH, "Replays", 4, mouseX, mouseY);
            curX += btnW + gap;
        } else {
            reg("modmenu", curX, dockY, btnW, dockH);
            renderDockButton(g, font, curX, dockY, btnW, dockH, "Mods", 4, mouseX, mouseY);
            curX += btnW + gap;
        }

        // 5. Discord Community
        reg("discord", curX, dockY, btnW, dockH);
        renderDockButton(g, font, curX, dockY, btnW, dockH, "Discord", 5, mouseX, mouseY);
        curX += btnW + gap;

        // 6. Quit Button (Red Accent)
        reg("quit", curX, dockY, quitW, dockH);
        boolean qh = isHovered(curX, dockY, quitW, dockH, mouseX, mouseY);
        VayuHUDUI.roundedRect(g, curX, dockY, quitW, dockH, 6, qh ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(g, curX, dockY, quitW, dockH, 6, qh ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(g, curX + quitW / 2, dockY + dockH / 2, 10, 0xFFFFFFFF);
    }

    private static void renderDockButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, int iconType, int mouseX, int mouseY) {
        boolean hover = isHovered(x, y, w, h, mouseX, mouseY);
        int bg = hover ? 0xE6141E2D : 0xD00A111A;
        int border = hover ? VayuTheme.PRIMARY : (iconType == 2 ? 0x6638BDF8 : 0x2A38BDF8);

        VayuHUDUI.roundedRect(g, x, y, w, h, 6, bg);
        VayuHUDUI.roundedOutline(g, x, y, w, h, 6, border);

        int iconX = x + 8;
        int cy = y + h / 2;
        int iconColor = hover ? VayuTheme.PRIMARY : (iconType == 2 ? 0xFF38BDF8 : 0xFFCBD5E1);

        if (iconType == 1) {
            VayuHUDUI.drawSettingsVector(g, iconX, cy, 10, iconColor);
        } else if (iconType == 2) {
            // Friends / Social Icon
            VayuHUDUI.drawMultiplayerVector(g, iconX, cy, 10, iconColor);
        } else if (iconType == 3) {
            VayuHUDUI.drawModsVector(g, iconX, cy, 10, iconColor);
        } else if (iconType == 4) {
            VayuHUDUI.drawModsVector(g, iconX, cy, 10, iconColor);
        } else {
            VayuHUDUI.drawPlayVector(g, iconX, cy, 10, iconColor);
        }

        g.text(font, label, iconX + 10, y + (h - 8) / 2, hover ? 0xFFFFFFFF : VayuTheme.TEXT_SECONDARY, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. VAYUCLIENT PAUSE / ESCAPE MENU (PROPORTIONAL & HIGH-FPS)
    // ═══════════════════════════════════════════════════════════════

    public static void renderPause(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        regions.clear();

        // Obsidian frosted background
        g.fill(0, 0, screenW, screenH, 0xD8050A10);

        int centerX = screenW / 2;
        int centerY = screenH / 2;

        // Telemetry Dashboard Box (Proportional to GUI view)
        int panelW = Math.min(320, Math.max(220, (int) (screenW * 0.5f)));
        int panelH = Math.min(276, Math.max(210, (int) (screenH * 0.85f)));
        int panelX = centerX - panelW / 2;
        int panelY = centerY - panelH / 2;

        VayuHUDUI.roundedRect(g, panelX, panelY, panelW, panelH, 10, 0xE60A111A);
        VayuHUDUI.roundedOutline(g, panelX, panelY, panelW, panelH, 10, 0x4438BDF8);

        // Header Title in Box
        g.text(font, "GAME PAUSED", panelX + 16, panelY + 14, VayuTheme.PRIMARY, true);
        g.text(font, "VayuClient v1.9.0 - Fabric 26.2", panelX + 16, panelY + 26, VayuTheme.TEXT_MUTED, false);

        // World Telemetry Readout Box
        int statBoxW = panelW - 32;
        int statBoxH = 34;
        int statBoxX = panelX + 16;
        int statBoxY = panelY + 40;
        VayuHUDUI.roundedRect(g, statBoxX, statBoxY, statBoxW, statBoxH, 6, 0xFF050A10);
        VayuHUDUI.roundedOutline(g, statBoxX, statBoxY, statBoxW, statBoxH, 6, 0x2238BDF8);

        Minecraft mc = Minecraft.getInstance();
        String fps = mc.getFps() + " FPS";
        String server = mc.hasSingleplayerServer() ? "Singleplayer" : (mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Multiplayer");
        String pos = mc.player != null ? String.format("X:%d Y:%d Z:%d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()) : "X:0 Y:0 Z:0";

        g.text(font, server + " | " + fps, statBoxX + 10, statBoxY + 6, 0xFFFFFFFF, true);
        g.text(font, pos, statBoxX + 10, statBoxY + 18, VayuTheme.PRIMARY, false);

        // Pause Action Buttons
        int btnW = panelW - 32;
        int btnH = 26;
        int btnGap = 6;
        int curBtnY = statBoxY + statBoxH + 10;

        // 1. Resume Game (Primary Cyan)
        reg("pause_backtogame", statBoxX, curBtnY, btnW, btnH);
        renderPauseActionButton(g, font, statBoxX, curBtnY, btnW, btnH, "RESUME GAME", 1, true, mouseX, mouseY);
        curBtnY += btnH + btnGap;

        // 2. Vayu HUD Mods
        reg("pause_vayuclient_settings", statBoxX, curBtnY, btnW, btnH);
        renderPauseActionButton(g, font, statBoxX, curBtnY, btnW, btnH, "VAYU HUD MODS", 2, false, mouseX, mouseY);
        curBtnY += btnH + btnGap;

        // 3. Friends & Social
        reg("pause_friends", statBoxX, curBtnY, btnW, btnH);
        renderPauseActionButton(g, font, statBoxX, curBtnY, btnW, btnH, "FRIENDS & SOCIAL", 2, false, mouseX, mouseY);
        curBtnY += btnH + btnGap;

        // 4. Settings & Options
        reg("pause_options", statBoxX, curBtnY, btnW, btnH);
        renderPauseActionButton(g, font, statBoxX, curBtnY, btnW, btnH, "OPTIONS & SETTINGS", 3, false, mouseX, mouseY);
        curBtnY += btnH + btnGap;

        // 5. Disconnect / Title
        reg("pause_disconnect", statBoxX, curBtnY, btnW, btnH);
        renderPauseActionButton(g, font, statBoxX, curBtnY, btnW, btnH, "DISCONNECT & SAVE", 4, false, mouseX, mouseY);
        curBtnY += btnH + btnGap;
    }

    private static void renderPauseActionButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, int type, boolean primary, int mouseX, int mouseY) {
        boolean hover = isHovered(x, y, w, h, mouseX, mouseY);
        int bg = primary 
            ? (hover ? VayuTheme.HERO_HOVER_START : VayuTheme.HERO_GRADIENT_START)
            : (hover ? 0xE6141E2D : 0xD00F1722);
        int border = primary ? VayuTheme.PRIMARY : (hover ? VayuTheme.PRIMARY : 0x2A38BDF8);

        VayuHUDUI.roundedRect(g, x, y, w, h, 6, bg);
        VayuHUDUI.roundedOutline(g, x, y, w, h, 6, border);

        int iconX = x + 14;
        int cy = y + h / 2;
        int iconColor = primary ? 0xFF050A10 : (hover ? VayuTheme.PRIMARY : 0xFFCBD5E1);

        if (type == 1) {
            VayuHUDUI.drawPlayVector(g, iconX, cy, 10, iconColor);
        } else if (type == 2) {
            VayuHUDUI.drawModsVector(g, iconX, cy, 10, iconColor);
        } else if (type == 3) {
            VayuHUDUI.drawSettingsVector(g, iconX, cy, 10, iconColor);
        } else {
            VayuHUDUI.drawCloseVector(g, iconX, cy, 10, hover ? 0xFFEF4444 : iconColor);
        }

        int textColor = primary ? 0xFF050A10 : (hover ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY);
        g.text(font, text, iconX + 12, y + (h - 8) / 2, textColor, !primary);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. TOP BAR & PROFILE AVATAR
    // ═══════════════════════════════════════════════════════════════

    private static void renderTopBar(GuiGraphicsExtractor g, Font font, int screenW, int mouseX, int mouseY, float easeT) {
        int y = 12;
        int h = 26;
        int pad = 16;

        // Left Brand Badge
        int leftX = pad;
        int badgeW = 124;
        VayuHUDUI.roundedRect(g, leftX, y, badgeW, h, 6, 0xD00A111A);
        VayuHUDUI.roundedOutline(g, leftX, y, badgeW, h, 6, 0x3338BDF8);
        g.text(font, "VAYUCLIENT", leftX + 10, y + 9, VayuTheme.PRIMARY, true);
        g.text(font, "1.9.0", leftX + badgeW - 36, y + 9, VayuTheme.TEXT_MUTED, false);

        // Right Profile Pill
        String user = getUserName();
        int userW = font.width(user);
        int profW = 34 + userW + 12;
        int profX = screenW - pad - profW;

        reg("profile", profX, y, profW, h);
        boolean ph = isHovered(profX, y, profW, h, mouseX, mouseY);
        VayuHUDUI.roundedRect(g, profX, y, profW, h, 6, ph ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(g, profX, y, profW, h, 6, ph ? VayuTheme.PRIMARY : 0x3338BDF8);

        // Profile Avatar Indicator
        int avSz = 16;
        int avX = profX + 6;
        int avY = y + (h - avSz) / 2;
        VayuHUDUI.roundedRect(g, avX, avY, avSz, avSz, 3, 0xFF0284C7);
        VayuHUDUI.drawPlayVector(g, avX + avSz / 2, avY + avSz / 2, 8, 0xFFFFFFFF);

        g.text(font, user, avX + avSz + 6, y + 9, ph ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS & REGISTRATION
    // ═══════════════════════════════════════════════════════════════

    public static void renderVanillaOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        g.text(font, "VayuClient v1.9.0", 12, screenH - 18, 0x8838BDF8, true);
    }

    public static int[] skinToggleBounds(int screenW, int screenH) {
        return new int[]{screenW - 36, 12, 24, 24};
    }

    public static int[] discordBounds(int screenW, int screenH) {
        return new int[]{16, screenH - 36, 80, 24};
    }

    private static String getUserName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null && mc.getUser().getName() != null) {
            return mc.getUser().getName();
        }
        return "Player";
    }

    private static boolean isHovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static void reg(String id, int x, int y, int w, int h) {
        regions.put(id, new int[]{x, y, w, h});
    }
}
