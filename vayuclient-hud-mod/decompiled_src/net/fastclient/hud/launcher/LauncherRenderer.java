/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FormattedText
 *  net.minecraft.resources.Identifier
 */
package net.fastclient.hud.launcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientFonts;
import net.fastclient.hud.launcher.LauncherSkinPreference;
import net.fastclient.hud.launcher.LauncherTheme;
import net.fastclient.hud.launcher.OptionalMenuIntegrations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;

public final class LauncherRenderer {
    static final String DOMAIN = "fastclient-hud";
    static final String TITLE_DIR = "textures/gui/title/";
    private static final int TOP_Y = 32;
    private static final int TOP_RIGHT = 42;
    private static final int ICON_SQ = 52;
    private static final int GAP = 14;
    private static final int AVATAR_SZ = 34;
    private static final int PILL_PAD_L = 14;
    private static final int PILL_PAD_R = 18;
    private static final int AVATAR_TEXT_GAP = 14;
    private static final Identifier FAST_SETTINGS_ICON = Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"textures/gui/fasticon_white.png");
    private static final int FAST_SETTINGS_ICON_TEXTURE_SIZE = 96;
    private static final String SKIN_TOGGLE_ID = "launcher_skin_toggle";
    private static final int FLASHBACK_ICON_TEXTURE_SIZE = 16;
    private static final Identifier FLASHBACK_REPLAYS_ICON = Identifier.fromNamespaceAndPath((String)"flashback", (String)"icon_pixelated.png");
    private static final Identifier FLASHBACK_START_ICON = Identifier.fromNamespaceAndPath((String)"flashback", (String)"icon_pixelated_start.png");
    private static final Identifier FLASHBACK_FINISH_ICON = Identifier.fromNamespaceAndPath((String)"flashback", (String)"icon_pixelated_finish.png");
    private static final Identifier FLASHBACK_PAUSE_ICON = Identifier.fromNamespaceAndPath((String)"flashback", (String)"icon_pixelated_pause.png");
    private static final Identifier FLASHBACK_CANCEL_ICON = Identifier.fromNamespaceAndPath((String)"flashback", (String)"icon_pixelated_cancel.png");
    static final int MAIN_BTN_W = 120;
    static final int MAIN_BTN_H = 23;
    private static final int MAIN_SPACING_TIGHT = 25;
    private static final int LOGO_W = 108;
    private static final int LOGO_H = 36;
    private static final int QUIT_W = 90;
    private static final int QUIT_H = 23;
    private static final float PRIMARY_UI_SCALE = 3.0f;
    private static final float CHROME_UI_SCALE = 1.25f;
    private static final float USERNAME_TEXT_SCALE = 2.0f;
    private static final boolean SHOW_COIN_CHECK_IN = false;
    private static final float LAUNCHER_TEXT_SCALE = 2.0f;
    private static final int BUTTON_BG = -15921133;
    private static final int BUTTON_BORDER = -14406863;
    private static final int BUTTON_HOVER_LEFT = -12958640;
    private static final int BUTTON_HOVER_RIGHT = -15591911;
    private static final int BUTTON_HOVER_BORDER = -13616829;
    private static final int BUTTON_TEXT = -460552;
    private static final int ACCENT_HOVER_LEFT = -39373;
    private static final int ACCENT_HOVER_RIGHT = -13954548;
    private static final int ACCENT_BUTTON_HOVER_LEFT = -34227;
    private static final int ACCENT_BUTTON_HOVER_RIGHT = -11263981;
    private static final int ACCENT_BUTTON_HOVER_BORDER = -30106;
    private static final int QUIT_BG = -1335219428;
    private static final int QUIT_HOVER_BG = -863750624;
    private static final int QUIT_BORDER = -2872779;
    private static final float MATERIAL_SYMBOL_VISUAL_Y_OFFSET = 2.0f;
    private static final float TOP_BAR_ICON_SCALE = 2.35f;
    private static final Map<String, String> topBarLabels = new LinkedHashMap<String, String>();
    private static final Map<String, String> topBarSymbols = new LinkedHashMap<String, String>();
    private static final Map<String, int[]> regions;
    private static final int[][] CORNER_INSETS;

    private LauncherRenderer() {
    }

    public static String getClickedButton(int mx, int my) {
        for (Map.Entry<String, int[]> e : regions.entrySet()) {
            int[] r = e.getValue();
            if (mx < r[0] || mx > r[0] + r[2] || my < r[1] || my > r[1] + r[3]) continue;
            return e.getKey();
        }
        return null;
    }

    public static boolean isSkinToggleClicked(int screenW, int screenH, int mouseX, int mouseY) {
        int[] bounds = LauncherRenderer.skinToggleBounds(screenW, screenH);
        return LauncherRenderer.isHovered(bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    public static boolean isDiscordClicked(int screenW, int screenH, int mouseX, int mouseY) {
        int[] bounds = LauncherRenderer.discordBounds(screenW, screenH);
        return LauncherRenderer.isHovered(bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    public static void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        regions.clear();
        float s = LauncherRenderer.launcherScale(screenW, screenH);
        float chrome = LauncherRenderer.chromeScale(s);
        g.fill(0, 0, screenW, screenH, 1376389644);
        int centerX = screenW / 2;
        int centerY = screenH / 2;
        LauncherRenderer.renderTopBar(g, font, screenW, chrome, mouseX, mouseY);
        int sideW = LauncherRenderer.sc(300, chrome);
        int sideCoinH = (int)((double)sideW * 374.0 / 1446.0);
        int sideDiscH = (int)((double)sideW * 967.0 / 1446.0);
        int sideGap = LauncherRenderer.sc(14, chrome);
        int sideXu = screenW - LauncherRenderer.sc(42, chrome) - sideW;
        int bottomY = screenH - LauncherRenderer.sc(42, chrome);
        int discY = bottomY - sideDiscH;
        int coinY = discY - sideGap - sideCoinH;
        LauncherRenderer.renderDiscordPanel(g, sideXu, discY, sideW, sideDiscH, mouseX, mouseY);
        Identifier logoId = DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)DOMAIN, (String)"textures/gui/title-fastclient-logo.png"));
        int logoW = LauncherRenderer.buttonDrawW(108, s);
        int logoH = LauncherRenderer.buttonDrawH(36, s);
        g.blit(RenderPipelines.GUI_TEXTURED, logoId, centerX - logoW / 2, centerY - LauncherRenderer.buttonDrawH(95, s), 0.0f, 0.0f, logoW, logoH, 510, 161, 510, 161);
        int mainBtnW = LauncherRenderer.buttonDrawW(120, s);
        int mainBtnH = LauncherRenderer.buttonDrawH(23, s);
        int mainSpacing = LauncherRenderer.buttonDrawH(25, s);
        int btnX = centerX - mainBtnW / 2;
        int startY = centerY - LauncherRenderer.buttonDrawH(40, s);
        LauncherRenderer.reg("singleplayer", btnX, startY, mainBtnW, mainBtnH);
        LauncherRenderer.renderLauncherTextButton(g, font, btnX, startY, mainBtnW, mainBtnH, "Singleplayer", "\uf0d3", false, mouseX, mouseY);
        LauncherRenderer.reg("multiplayer", btnX, startY + mainSpacing, mainBtnW, mainBtnH);
        LauncherRenderer.renderLauncherTextButton(g, font, btnX, startY + mainSpacing, mainBtnW, mainBtnH, "Multiplayer", "\uea21", false, mouseX, mouseY);
        LauncherRenderer.reg("skins", btnX, startY + mainSpacing * 2, mainBtnW, mainBtnH);
        LauncherRenderer.renderLauncherTextButton(g, font, btnX, startY + mainSpacing * 2, mainBtnW, mainBtnH, "Cosmetics", "\uf19e", false, mouseX, mouseY);
        int storeIndex = 3;
        if (OptionalMenuIntegrations.isModMenuAvailable()) {
            LauncherRenderer.reg("modmenu", btnX, startY + mainSpacing * 3, mainBtnW, mainBtnH);
            LauncherRenderer.renderLauncherTextButton(g, font, btnX, startY + mainSpacing * 3, mainBtnW, mainBtnH, "Mods", "\ue5c3", false, mouseX, mouseY);
            storeIndex = 4;
        }
        LauncherRenderer.reg("store", btnX, startY + mainSpacing * storeIndex, mainBtnW, mainBtnH);
        LauncherRenderer.renderLauncherTextButton(g, font, btnX, startY + mainSpacing * storeIndex, mainBtnW, mainBtnH, "Store", "\uea12", true, mouseX, mouseY);
        if (OptionalMenuIntegrations.isFlashbackReplaysAvailable()) {
            int replayX = btnX + mainBtnW + LauncherRenderer.buttonDrawW(4, s);
            int replayY = startY + mainSpacing * 2;
            LauncherRenderer.renderExternalIconButton(g, font, "flashback_replays", "Open Replays", FLASHBACK_REPLAYS_ICON, replayX, replayY, mainBtnH, mouseX, mouseY);
        }
        int quitW = LauncherRenderer.buttonDrawW(90, s);
        int quitH = LauncherRenderer.buttonDrawH(23, s);
        int quitX = centerX - quitW / 2;
        int quitY = startY + mainSpacing * (storeIndex + 2);
        LauncherRenderer.reg("quit", quitX, quitY, quitW, quitH);
        LauncherRenderer.renderQuitButton(g, font, quitX, quitY, quitW, quitH, mouseX, mouseY);
        String ver = "Fastclient 26.2 (release/ca786cd3)";
        int verX = quitX + quitW / 2;
        int verY = quitY + quitH + LauncherRenderer.buttonDrawH(6, s);
        int tw = font.width(ver);
        g.pose().pushMatrix();
        g.pose().translate((float)verX, (float)verY);
        g.pose().scale(1.3f, 1.3f);
        g.pose().translate((float)(-verX), (float)(-verY));
        g.text(font, ver, verX - tw / 2, verY, -7829368, false);
        g.pose().popMatrix();
    }

    public static void renderPause(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        regions.clear();
        float s = LauncherRenderer.launcherScale(screenW, screenH);
        float chrome = LauncherRenderer.chromeScale(s);
        g.fill(0, 0, screenW, screenH, -788529152);
        LauncherRenderer.renderTopBar(g, font, screenW, chrome, mouseX, mouseY);
        int artH = (int)((float)screenH * 0.72f);
        int artW = Math.round((float)artH * 0.8866213f);
        int artX = -Math.round((float)artW * 0.3f);
        int artY = screenH - Math.round((float)artH * 0.82f);
        g.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)DOMAIN, (String)"textures/gui/title/25.png")), artX, artY, 0.0f, 0.0f, artW, artH, 2737, 3087, 2737, 3087);
        int centerX = screenW / 2;
        int centerY = screenH / 2;
        Identifier logoId = DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)DOMAIN, (String)"textures/gui/title-fastclient-logo.png"));
        int logoW = LauncherRenderer.buttonDrawW(108, s);
        int logoH = LauncherRenderer.buttonDrawH(36, s);
        g.blit(RenderPipelines.GUI_TEXTURED, logoId, centerX - logoW / 2, centerY - LauncherRenderer.buttonDrawH(99, s), 0.0f, 0.0f, logoW, logoH, 510, 161, 510, 161);
        int mainBtnW = LauncherRenderer.buttonDrawW(120, s);
        int mainBtnH = LauncherRenderer.buttonDrawH(23, s);
        int mainSpacing = LauncherRenderer.buttonDrawH(25, s);
        int btnX = centerX - mainBtnW / 2;
        int startY = centerY - LauncherRenderer.buttonDrawH(58, s);
        LauncherRenderer.pauseTextBtn(g, font, "pause_backtogame", "Back to Game", "\ue5c4", false, btnX, startY, mainBtnW, mainBtnH, mouseX, mouseY);
        LauncherRenderer.pauseTextBtn(g, font, "pause_fastclient_settings", "Fast Settings", "\ue566", false, btnX, startY + mainSpacing, mainBtnW, mainBtnH, mouseX, mouseY);
        LauncherRenderer.pauseTextBtn(g, font, "pause_store", "Store", "\uea12", true, btnX, startY + mainSpacing * 2, mainBtnW, mainBtnH, mouseX, mouseY);
        LauncherRenderer.pauseTextBtn(g, font, "pause_options", "Options", "\ue8b8", false, btnX, startY + mainSpacing * 3, mainBtnW, mainBtnH, mouseX, mouseY);
        int nextButtonIndex = 4;
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            LauncherRenderer.pauseTextBtn(g, font, "pause_open_to_lan", "Open to LAN", "\uea21", false, btnX, startY + mainSpacing * nextButtonIndex++, mainBtnW, mainBtnH, mouseX, mouseY);
        }
        if (OptionalMenuIntegrations.isModMenuAvailable()) {
            LauncherRenderer.pauseTextBtn(g, font, "pause_modmenu", "Mods", "\ue5c3", false, btnX, startY + mainSpacing * nextButtonIndex++, mainBtnW, mainBtnH, mouseX, mouseY);
        }
        LauncherRenderer.pauseTextBtn(g, font, "pause_disconnect", "Disconnect", "\ue9ba", false, btnX, startY + mainSpacing * nextButtonIndex, mainBtnW, mainBtnH, mouseX, mouseY);
        LauncherRenderer.renderFlashbackPauseControls(g, font, btnX + mainBtnW + LauncherRenderer.buttonDrawW(4, s), startY + mainSpacing, mainBtnH, mouseX, mouseY);
        int brSq = LauncherRenderer.sc(52, s);
        int brGap = LauncherRenderer.sc(14, s);
        int brPad = LauncherRenderer.sc(42, s) * 2;
        int brRight = screenW - brPad;
        int brY = screenH - brPad - brSq;
        int brX = brRight;
        brX = LauncherRenderer.pauseBottomBtn(g, font, "pause_minecraftfolder", "Minecraft Folder", "\ue2c8", brX, brY, brSq, mouseX, mouseY) - brGap;
        brX = LauncherRenderer.pauseBottomBtn(g, font, "pause_player_reporting", "Player Reporting", "\ue160", brX, brY, brSq, mouseX, mouseY) - brGap;
        brX = LauncherRenderer.pauseBottomBtn(g, font, "pause_statistics", "Statistics", "\ue26b", brX, brY, brSq, mouseX, mouseY) - brGap;
        LauncherRenderer.pauseBottomBtn(g, font, "pause_advancements", "Advancements", "\uea23", brX, brY, brSq, mouseX, mouseY);
    }

    private static void renderTopBar(GuiGraphicsExtractor g, Font font, int screenW, float s, int mouseX, int mouseY) {
        int y = LauncherRenderer.sc(32, s);
        int sq = LauncherRenderer.sc(52, s);
        int gap = LauncherRenderer.sc(14, s);
        int rightEdge = screenW - LauncherRenderer.sc(42, s);
        int toggleX = rightEdge - sq;
        LauncherRenderer.renderSkinToggle(g, font, screenW, DisplaySpace.height(), mouseX, mouseY);
        int cx = toggleX - gap;
        cx = LauncherRenderer.iconBtn(g, "box", cx, y, sq, mouseX, mouseY) - gap;
        cx = LauncherRenderer.iconBtn(g, "diamond", cx, y, sq, mouseX, mouseY) - gap;
        cx = LauncherRenderer.iconBtn(g, "settings", cx, y, sq, mouseX, mouseY) - gap;
        cx = LauncherRenderer.iconBtn(g, "camera", cx, y, sq, mouseX, mouseY) - gap;
        cx = LauncherRenderer.iconBtn(g, "chat", cx, y, sq, mouseX, mouseY) - gap;
        cx = LauncherRenderer.iconBtn(g, "window", cx, y, sq, mouseX, mouseY) - gap;
        String user = LauncherRenderer.getUserName();
        int avatarSz = LauncherRenderer.sc(34, s);
        int padL = LauncherRenderer.sc(14, s);
        int padR = LauncherRenderer.sc(18, s);
        int textGap = LauncherRenderer.sc(14, s);
        int userTextW = Math.round((float)font.width(user) * 2.0f);
        Objects.requireNonNull(font);
        int userTextH = Math.round(9.0f * 2.0f);
        int profH = sq;
        int profW = padL + avatarSz + textGap + userTextW + padR;
        int profX = cx - profW;
        LauncherRenderer.reg("profile", profX, y, profW, profH);
        boolean ph = LauncherRenderer.isHovered(profX, y, profW, profH, mouseX, mouseY);
        LauncherRenderer.renderButtonSurface(g, profX, y, profW, profH, ph, false);
        int ax = profX + padL;
        int ay = y + (profH - avatarSz) / 2;
        LauncherRenderer.drawCenteredLauncherComponent(g, font, FastClientFonts.filledMaterialSymbol("\uf0d3"), ax + avatarSz / 2, ay + avatarSz / 2 + Math.round(4.4f), 2.2f, -460552, false);
        int tx = ax + avatarSz + textGap;
        int ty = y + (profH - userTextH) / 2 + 1;
        g.pose().pushMatrix();
        g.pose().translate((float)tx, (float)ty);
        g.pose().scale(2.0f, 2.0f);
        g.pose().translate((float)(-tx), (float)(-ty));
        g.text(font, user, tx, ty, -1, true);
        g.pose().popMatrix();
    }

    public static void renderSkinToggle(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        int[] bounds = LauncherRenderer.skinToggleBounds(screenW, screenH);
        int x = bounds[0];
        int y = bounds[1];
        int size = bounds[2];
        boolean fastClientEnabled = LauncherSkinPreference.isFastClientSkinEnabled();
        boolean hover = LauncherRenderer.isHovered(x, y, size, size, mouseX, mouseY);
        LauncherRenderer.reg(SKIN_TOGGLE_ID, x, y, size, size);
        LauncherRenderer.renderButtonSurface(g, x, y, size, size, hover, fastClientEnabled);
        int iconSize = Math.max(12, Math.round((float)size * 0.58f));
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(FAST_SETTINGS_ICON), iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 96, 96, 96, 96);
        if (hover) {
            String tooltip = fastClientEnabled ? "Use Minecraft UI" : "Use FastClient UI";
            LauncherRenderer.renderTopBarTooltip(g, font, tooltip, x, y + size + Math.max(5, size / 5));
        }
    }

    public static void renderVanillaOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        LauncherRenderer.renderSkinToggle(g, font, screenW, screenH, mouseX, mouseY);
        int[] bounds = LauncherRenderer.discordBounds(screenW, screenH);
        LauncherRenderer.renderDiscordPanel(g, bounds[0], bounds[1], bounds[2], bounds[3], mouseX, mouseY);
    }

    private static void renderDiscordPanel(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        LauncherRenderer.reg("joindiscord1", x, y, width, height);
        boolean hover = LauncherRenderer.isHovered(x, y, width, height, mouseX, mouseY);
        g.blit(RenderPipelines.GUI_TEXTURED, LauncherRenderer.titleId(hover ? "joindiscord1_hover" : "joindiscord1"), x, y, 0.0f, 0.0f, width, height, width, height, width, height);
    }

    private static void renderFlashbackPauseControls(GuiGraphicsExtractor g, Font font, int x, int y, int size, int mouseX, int mouseY) {
        OptionalMenuIntegrations.FlashbackRecordingState state = OptionalMenuIntegrations.getFlashbackRecordingState();
        if (state == OptionalMenuIntegrations.FlashbackRecordingState.HIDDEN) {
            return;
        }
        int gap = Math.max(4, size / 10);
        if (state == OptionalMenuIntegrations.FlashbackRecordingState.READY) {
            LauncherRenderer.renderExternalIconButton(g, font, "flashback_record_start", "Start Recording", FLASHBACK_START_ICON, x, y, size, mouseX, mouseY);
            return;
        }
        LauncherRenderer.renderExternalIconButton(g, font, "flashback_record_finish", "Finish Recording", FLASHBACK_FINISH_ICON, x, y, size, mouseX, mouseY);
        x += size + gap;
        if (state == OptionalMenuIntegrations.FlashbackRecordingState.PAUSED) {
            LauncherRenderer.renderExternalIconButton(g, font, "flashback_record_resume", "Resume Recording", FLASHBACK_START_ICON, x, y, size, mouseX, mouseY);
        } else {
            LauncherRenderer.renderExternalIconButton(g, font, "flashback_record_pause", "Pause Recording", FLASHBACK_PAUSE_ICON, x, y, size, mouseX, mouseY);
        }
        LauncherRenderer.renderExternalIconButton(g, font, "flashback_record_cancel", "Cancel Recording", FLASHBACK_CANCEL_ICON, x += size + gap, y, size, mouseX, mouseY);
    }

    private static void renderExternalIconButton(GuiGraphicsExtractor g, Font font, String id, String tooltip, Identifier icon, int x, int y, int size, int mouseX, int mouseY) {
        LauncherRenderer.reg(id, x, y, size, size);
        boolean hover = LauncherRenderer.isHovered(x, y, size, size, mouseX, mouseY);
        LauncherRenderer.renderButtonSurface(g, x, y, size, size, hover, false);
        int iconSize = Math.max(12, Math.round((float)size * 0.55f));
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 16, 16, 16, 16);
        if (hover) {
            LauncherRenderer.renderTopBarTooltip(g, font, tooltip, x + size / 2, y + size + Math.max(5, size / 5));
        }
    }

    private static int iconBtn(GuiGraphicsExtractor g, String name, int x, int y, int sq, int mouseX, int mouseY) {
        int bx = x - sq;
        LauncherRenderer.reg(name, bx, y, sq, sq);
        boolean hover = LauncherRenderer.isHovered(bx, y, sq, sq, mouseX, mouseY);
        LauncherRenderer.renderButtonSurface(g, bx, y, sq, sq, hover, false);
        String symbol = topBarSymbols.get(name);
        if (symbol != null) {
            LauncherRenderer.drawCenteredLauncherComponent(g, Minecraft.getInstance().font, FastClientFonts.filledMaterialSymbol(symbol), bx + sq / 2, y + sq / 2 + Math.round(4.7f), 2.35f, hover ? -460552 : -5592406, false);
        }
        if (hover) {
            LauncherRenderer.renderTopBarTooltip(g, Minecraft.getInstance().font, topBarLabels.get(name), bx + sq / 2, y + sq + 10);
        }
        return bx;
    }

    private static int[] computeInsets(int r) {
        if (r == 0) {
            return new int[0];
        }
        int[] ci = new int[r];
        for (int i = 0; i < r; ++i) {
            int d = r - i;
            int sqrt = (int)Math.ceil(Math.sqrt((double)r * (double)r - (double)d * (double)d));
            ci[i] = Math.max(0, r - sqrt);
        }
        return ci;
    }

    private static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        int i;
        if (r <= 0 || r >= 20 || h < r * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int[] ci = CORNER_INSETS[r];
        int rr = r;
        for (i = 0; i < rr; ++i) {
            g.fill(x + ci[i], y + i, x + w - ci[i], y + i + 1, color);
        }
        if (h > rr * 2) {
            g.fill(x, y + rr, x + w, y + h - rr, color);
        }
        for (i = 0; i < rr; ++i) {
            g.fill(x + ci[rr - 1 - i], y + h - rr + i, x + w - ci[rr - 1 - i], y + h - rr + i + 1, color);
        }
    }

    private static int sc(int val, float s) {
        return (int)((float)val * s);
    }

    private static float launcherScale(int screenW, int screenH) {
        return Math.min((float)screenW / 1920.0f, (float)screenH / 1080.0f);
    }

    private static float chromeScale(float s) {
        return s * 1.25f;
    }

    private static int[] skinToggleBounds(int screenW, int screenH) {
        float chrome = LauncherRenderer.chromeScale(LauncherRenderer.launcherScale(screenW, screenH));
        int size = LauncherRenderer.sc(52, chrome);
        int x = screenW - LauncherRenderer.sc(42, chrome) - size;
        int y = LauncherRenderer.sc(32, chrome);
        return new int[]{x, y, size, size};
    }

    private static int[] discordBounds(int screenW, int screenH) {
        float chrome = LauncherRenderer.chromeScale(LauncherRenderer.launcherScale(screenW, screenH));
        int width = LauncherRenderer.sc(300, chrome);
        int height = (int)((double)width * 967.0 / 1446.0);
        int x = screenW - LauncherRenderer.sc(42, chrome) - width;
        int y = screenH - LauncherRenderer.sc(42, chrome) - height;
        return new int[]{x, y, width, height};
    }

    private static int buttonDrawW(int val, float s) {
        return Math.round((float)val * s * 3.0f);
    }

    private static int buttonDrawH(int val, float s) {
        return Math.round((float)val * s * 3.0f);
    }

    private static void renderLauncherTextButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String labelText, String symbol, boolean accentButton, int mouseX, int mouseY) {
        float textScale;
        boolean hover = LauncherRenderer.isHovered(x, y, w, h, mouseX, mouseY);
        LauncherRenderer.renderButtonSurface(g, x, y, w, h, hover, accentButton);
        boolean useFastIcon = "\ue566".equals(symbol);
        Component icon = symbol == null || useFastIcon ? null : FastClientFonts.filledMaterialSymbol(symbol);
        Component label = FastClientFonts.strong(labelText);
        float iconScale = textScale = 2.0f;
        int iconWidth = useFastIcon ? Math.max(18, Math.round((float)h * 0.5f)) : (icon == null ? 0 : Math.max(1, Math.round((float)font.width((FormattedText)icon) * iconScale)));
        int labelWidth = Math.max(1, Math.round((float)font.width((FormattedText)label) * textScale));
        int gap = iconWidth == 0 ? 0 : Math.max(5, Math.round((float)h * 0.08f));
        int contentWidth = iconWidth + gap + labelWidth;
        int contentX = x + (w - contentWidth) / 2;
        int centerY = y + h / 2;
        if (useFastIcon) {
            int iconY = centerY - iconWidth / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(FAST_SETTINGS_ICON), contentX, iconY, 0.0f, 0.0f, iconWidth, iconWidth, 96, 96, 96, 96);
        } else if (icon != null) {
            int iconVisualCenterY = centerY + Math.round(2.0f * iconScale);
            LauncherRenderer.drawCenteredLauncherComponent(g, font, icon, contentX + iconWidth / 2, iconVisualCenterY, iconScale, -460552, false);
        }
        LauncherRenderer.drawCenteredLauncherComponent(g, font, label, contentX + iconWidth + gap + labelWidth / 2, centerY, textScale, -460552, true);
    }

    private static void renderButtonSurface(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean accentButton) {
        int radius = Math.max(3, Math.round((float)h * 0.09f));
        LauncherRenderer.fillRoundedRect(g, x, y + Math.max(1, h / 30), w, h, radius, 0x38000000);
        int border = accentButton ? (hover ? -30106 : -39373) : (hover ? -13616829 : -14406863);
        LauncherRenderer.fillRoundedRect(g, x, y, w, h, radius, border);
        int inset = Math.max(1, Math.round((float)h / 69.0f));
        int innerX = x + inset;
        int innerY = y + inset;
        int innerW = w - inset * 2;
        int innerH = h - inset * 2;
        int innerRadius = Math.max(2, radius - inset);
        if (accentButton) {
            LauncherRenderer.fillRoundedHorizontalGradient(g, innerX, innerY, innerW, innerH, innerRadius, hover ? -34227 : -39373, hover ? -11263981 : -13954548);
        } else if (hover) {
            LauncherRenderer.fillRoundedHorizontalGradient(g, innerX, innerY, innerW, innerH, innerRadius, -12958640, -15591911);
        } else {
            LauncherRenderer.fillRoundedRect(g, innerX, innerY, innerW, innerH, innerRadius, -15921133);
        }
    }

    private static void renderQuitButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hover = LauncherRenderer.isHovered(x, y, w, h, mouseX, mouseY);
        int radius = Math.max(7, h / 4);
        LauncherRenderer.fillRoundedRect(g, x, y + Math.max(2, h / 20), w, h, radius, 0x42000000);
        LauncherRenderer.fillRoundedRect(g, x, y, w, h, radius, -2872779);
        int inset = Math.max(1, Math.round((float)h / 55.0f));
        LauncherRenderer.fillRoundedRect(g, x + inset, y + inset, w - inset * 2, h - inset * 2, Math.max(2, radius - inset), hover ? -863750624 : -1335219428);
        Component label = FastClientFonts.strong("Quit Game");
        float textScale = 2.0f;
        LauncherRenderer.drawCenteredLauncherComponent(g, font, label, x + w / 2, y + h / 2, textScale, -39373, true);
    }

    private static void renderTopBarTooltip(GuiGraphicsExtractor g, Font font, String labelText, int centerX, int y) {
        if (labelText == null) {
            return;
        }
        Component label = FastClientFonts.strong(labelText);
        float scale = 2.0f;
        int textW = Math.round((float)font.width((FormattedText)label) * scale);
        Objects.requireNonNull(font);
        int textH = Math.round(9.0f * scale);
        int padX = Math.max(12, Math.round(scale * 4.0f));
        int padY = Math.max(6, Math.round(scale * 2.0f));
        int tooltipW = textW + padX * 2;
        int tooltipH = textH + padY * 2;
        int tooltipX = centerX - tooltipW / 2;
        LauncherRenderer.fillRoundedRect(g, tooltipX, y, tooltipW, tooltipH, Math.max(3, tooltipH / 8), -234024941);
        LauncherRenderer.drawRectOutline(g, tooltipX, y, tooltipW, tooltipH, -1606135718);
        LauncherRenderer.drawCenteredLauncherComponent(g, font, label, centerX, y + tooltipH / 2, scale, -460552, true);
    }

    private static void drawCenteredLauncherComponent(GuiGraphicsExtractor g, Font font, Component text, int centerX, int centerY, float scale, int color, boolean shadow) {
        int textX = Math.round((float)centerX - (float)font.width((FormattedText)text) * scale / 2.0f);
        float f = centerY;
        Objects.requireNonNull(font);
        int textY = Math.round(f - 9.0f * scale / 2.0f);
        g.pose().pushMatrix();
        g.pose().translate((float)textX, (float)textY);
        g.pose().scale(scale, scale);
        g.pose().translate((float)(-textX), (float)(-textY));
        g.text(font, text, textX, textY, color, shadow);
        g.pose().popMatrix();
    }

    private static void fillRoundedHorizontalGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int leftColor, int rightColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int column = 0; column < w; ++column) {
            float position = w <= 1 ? 1.0f : (float)column / (float)(w - 1);
            float blend = Math.max(0.0f, Math.min(1.0f, (position - 0.14f) / 0.6f));
            int verticalInset = LauncherRenderer.roundedColumnInset(column, w, r);
            g.fill(x + column, y + verticalInset, x + column + 1, y + h - verticalInset, LauncherTheme.blend(leftColor, rightColor, blend));
        }
    }

    private static int roundedColumnInset(int column, int width, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int edgeDistance = Math.min(column, width - 1 - column);
        if (edgeDistance >= radius) {
            return 0;
        }
        double circleX = (double)(radius - edgeDistance) - 0.5;
        return Math.max(0, (int)Math.ceil((double)radius - Math.sqrt(Math.max(0.0, (double)(radius * radius) - circleX * circleX))));
    }

    private static void pauseTextBtn(GuiGraphicsExtractor g, Font font, String id, String label, String symbol, boolean accentButton, int x, int y, int w, int h, int mouseX, int mouseY) {
        LauncherRenderer.reg(id, x, y, w, h);
        LauncherRenderer.renderLauncherTextButton(g, font, x, y, w, h, label, symbol, accentButton, mouseX, mouseY);
    }

    private static int pauseBottomBtn(GuiGraphicsExtractor g, Font font, String name, String labelText, String symbol, int x, int y, int sqH, int mouseX, int mouseY) {
        Component label = FastClientFonts.strong(labelText);
        Component icon = FastClientFonts.filledMaterialSymbol(symbol);
        float scale = 2.0f;
        int textWidth = Math.round((float)font.width((FormattedText)label) * scale);
        int iconWidth = Math.round((float)font.width((FormattedText)icon) * scale);
        int gap = Math.max(7, Math.round(scale * 3.0f));
        int contentWidth = iconWidth + gap + textWidth;
        int btnW = Math.max(Math.round((float)sqH * 2.8347826f), contentWidth + Math.max(20, Math.round(scale * 8.0f)));
        int bx = x - btnW;
        LauncherRenderer.reg(name, bx, y, btnW, sqH);
        boolean hover = LauncherRenderer.isHovered(bx, y, btnW, sqH, mouseX, mouseY);
        LauncherRenderer.renderButtonSurface(g, bx, y, btnW, sqH, hover, false);
        int contentX = bx + (btnW - contentWidth) / 2;
        int color = hover ? -460552 : -5592406;
        LauncherRenderer.drawCenteredLauncherComponent(g, font, icon, contentX + iconWidth / 2, y + sqH / 2 + Math.round(2.0f * scale), scale, color, false);
        LauncherRenderer.drawCenteredLauncherComponent(g, font, label, contentX + iconWidth + gap + textWidth / 2, y + sqH / 2, scale, color, true);
        return bx;
    }

    private static void drawRectOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static Identifier titleId(String name) {
        return DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)DOMAIN, (String)(TITLE_DIR + name + ".png")));
    }

    private static void reg(String name, int x, int y, int w, int h) {
        regions.put(name, new int[]{x, y, w, h});
    }

    private static boolean isHovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String getUserName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            return mc.getUser().getName();
        }
        return "Player";
    }

    static {
        topBarLabels.put("window", "Fast Mods");
        topBarLabels.put("chat", "Social");
        topBarLabels.put("camera", "Screenshots");
        topBarLabels.put("settings", "Minecraft Settings");
        topBarLabels.put("diamond", "Minecraft Realms");
        topBarLabels.put("box", "Resource Packs");
        topBarSymbols.put("window", "\ue99b");
        topBarSymbols.put("chat", "\ue8af");
        topBarSymbols.put("camera", "\ue412");
        topBarSymbols.put("settings", "\ue8b8");
        topBarSymbols.put("diamond", "\uead5");
        topBarSymbols.put("box", "\ue2c8");
        regions = new LinkedHashMap<String, int[]>();
        CORNER_INSETS = new int[20][];
        for (int r = 0; r < 20; ++r) {
            LauncherRenderer.CORNER_INSETS[r] = LauncherRenderer.computeInsets(r);
        }
    }
}

