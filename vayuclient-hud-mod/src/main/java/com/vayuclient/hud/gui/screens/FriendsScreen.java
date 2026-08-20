package com.vayuclient.hud.gui.screens;

import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.render.AnimationUtils;
import com.vayuclient.hud.social.VayuSocialManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class FriendsScreen extends Screen {
    private final Screen parent;
    private final VayuSocialManager social = VayuSocialManager.getInstance();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private int selectedTab = 0; // 0 = FRIENDS, 1 = REQUESTS, 2 = PLAYERS
    private static final String[] TABS = {"FRIENDS", "REQUESTS", "ONLINE PLAYERS"};

    private String addFriendInput = "";
    private boolean inputFocused = false;
    private long cursorBlinkTime = 0L;
    private String statusMessage = "";
    private long statusMessageTime = 0L;

    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private AnimationUtils.Animation openAnimation;
    private long lastUpdate = System.currentTimeMillis();

    public FriendsScreen(Screen parent) {
        super(Component.literal("VayuClient Friends & Social"));
        this.parent = parent;
        this.openAnimation = new AnimationUtils.Animation(0.0f, 250);
        this.openAnimation.animateTo(1.0f);
    }

    public FriendsScreen() {
        this(null);
    }

    @Override
    protected void init() {
        this.social.pollSocialData();
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 250);
            this.openAnimation.animateTo(1.0f);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), 0xCC050A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.extractBackground(g, mouseX, mouseY, delta);

        long now = System.currentTimeMillis();
        this.lastUpdate = now;

        int screenW = DisplaySpace.width();
        int screenH = DisplaySpace.height();
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);

        // Panel size
        panelWidth = Math.min(520, Math.max(340, (int) (screenW * 0.72f)));
        panelHeight = Math.min(380, Math.max(260, (int) (screenH * 0.80f)));
        panelX = (screenW - panelWidth) / 2;
        panelY = (screenH - panelHeight) / 2;

        float anim = this.openAnimation != null ? this.openAnimation.getValue() : 1.0f;
        int animatedY = (int) (panelY + (1.0f - anim) * 16);
        int alpha = (int) (anim * 255.0f);

        // Main glassmorphic background
        VayuHUDUI.roundedRect(g, panelX, animatedY, panelWidth, panelHeight, 10, VayuHUDUI.withAlpha(0xF00A111A, alpha));
        VayuHUDUI.roundedOutline(g, panelX, animatedY, panelWidth, panelHeight, 10, VayuHUDUI.withAlpha(0x4438BDF8, alpha));

        // Header
        renderHeader(g, animatedY, pxMouseX, pxMouseY);

        // Tabs
        renderTabs(g, animatedY, pxMouseX, pxMouseY);

        // Add Friend Input Bar
        renderAddFriendBar(g, animatedY, pxMouseX, pxMouseY);

        // Content List Area
        int contentY = animatedY + 98;
        int contentH = panelHeight - 110;
        DisplaySpace.enableScissor(g, panelX + 8, contentY, panelX + panelWidth - 8, contentY + contentH);

        if (selectedTab == 0) {
            renderFriendsList(g, contentY, contentH, pxMouseX, pxMouseY);
        } else if (selectedTab == 1) {
            renderRequestsList(g, contentY, contentH, pxMouseX, pxMouseY);
        } else {
            renderOnlinePlayersList(g, contentY, contentH, pxMouseX, pxMouseY);
        }

        DisplaySpace.disableScissor(g);

        // Status Toast Banner at bottom
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 4000) {
            int msgW = this.font.width(statusMessage) + 24;
            int msgX = panelX + (panelWidth - msgW) / 2;
            int msgY = animatedY + panelHeight - 28;
            VayuHUDUI.roundedRect(g, msgX, msgY, msgW, 20, 6, 0xEE0284C7);
            VayuHUDUI.roundedOutline(g, msgX, msgY, msgW, 20, 6, 0xFF38BDF8);
            g.text(this.font, statusMessage, msgX + 12, msgY + 6, 0xFFFFFFFF, true);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor g, int curPanelY, int mouseX, int mouseY) {
        int titleX = panelX + 16;
        int titleY = curPanelY + 14;

        g.text(this.font, "VAYU FRIENDS & SOCIAL", titleX, titleY, VayuTheme.PRIMARY, true);
        g.text(this.font, "Real-Time Multi-Server Network", titleX, titleY + 12, VayuTheme.TEXT_MUTED, false);

        // Online Count Badge
        int activeCount = social.getActivePlayers().size();
        String onlineBadge = activeCount + " Vayu Players Online";
        int badgeW = this.font.width(onlineBadge) + 16;
        int badgeX = panelX + panelWidth - 44 - badgeW;
        int badgeY = curPanelY + 14;
        VayuHUDUI.roundedRect(g, badgeX, badgeY, badgeW, 18, 5, 0x3338BDF8);
        VayuHUDUI.roundedOutline(g, badgeX, badgeY, badgeW, 18, 5, 0x5538BDF8);
        g.text(this.font, onlineBadge, badgeX + 8, badgeY + 5, 0xFF38BDF8, true);

        // Close Button (X)
        int closeX = panelX + panelWidth - 32;
        int closeY = curPanelY + 14;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18;
        VayuHUDUI.roundedRect(g, closeX, closeY, 18, 18, 4, closeHover ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.drawCloseVector(g, closeX + 9, closeY + 9, 8, 0xFFFFFFFF);
    }

    private void renderTabs(GuiGraphicsExtractor g, int curPanelY, int mouseX, int mouseY) {
        int tabX = panelX + 16;
        int tabY = curPanelY + 40;
        int tabH = 22;

        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i];
            if (i == 0) label += " (" + social.getFriends().size() + ")";
            else if (i == 1) {
                int reqCount = social.getIncomingRequests().size();
                label += " (" + reqCount + ")";
            } else {
                label += " (" + social.getActivePlayers().size() + ")";
            }

            int tabW = this.font.width(label) + 16;
            boolean active = (selectedTab == i);
            boolean hover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = active ? VayuTheme.HERO_HOVER_START : (hover ? 0xE6141E2D : 0xD00F1722);
            int border = active ? VayuTheme.PRIMARY : (hover ? VayuTheme.PRIMARY : 0x2A38BDF8);

            VayuHUDUI.roundedRect(g, tabX, tabY, tabW, tabH, 5, bg);
            VayuHUDUI.roundedOutline(g, tabX, tabY, tabW, tabH, 5, border);

            // Highlight request tab if incoming requests > 0
            if (i == 1 && social.getIncomingRequests().size() > 0 && !active) {
                VayuHUDUI.roundedRect(g, tabX + tabW - 8, tabY + 3, 5, 5, 2, 0xFFEF4444);
            }

            g.text(this.font, label, tabX + 8, tabY + 7, active ? 0xFFFFFFFF : (hover ? 0xFFCBD5E1 : VayuTheme.TEXT_SECONDARY), active);

            tabX += tabW + 6;
        }
    }

    private void renderAddFriendBar(GuiGraphicsExtractor g, int curPanelY, int mouseX, int mouseY) {
        int barX = panelX + 16;
        int barY = curPanelY + 68;
        int barH = 22;
        boolean isInGame = Minecraft.getInstance().player != null;
        int btnW = 90;
        int coordsBtnW = isInGame ? 100 : 0;
        int spacing = 6;
        int inputW = panelWidth - 32 - btnW - (isInGame ? coordsBtnW + spacing : 0) - spacing;

        // Input Box
        boolean inputHover = mouseX >= barX && mouseX <= barX + inputW && mouseY >= barY && mouseY <= barY + barH;
        int bg = inputFocused ? 0xE6141E2D : (inputHover ? 0xD0101824 : 0xD0080E16);
        int border = inputFocused ? VayuTheme.PRIMARY : 0x3338BDF8;

        VayuHUDUI.roundedRect(g, barX, barY, inputW, barH, 5, bg);
        VayuHUDUI.roundedOutline(g, barX, barY, inputW, barH, 5, border);

        String displayText = addFriendInput.isEmpty() && !inputFocused ? "Enter Minecraft username to add..." : addFriendInput;
        int textColor = addFriendInput.isEmpty() && !inputFocused ? VayuTheme.TEXT_MUTED : 0xFFFFFFFF;

        if (inputFocused && (System.currentTimeMillis() - cursorBlinkTime) % 1000 < 500) {
            displayText += "_";
        }

        g.text(this.font, displayText, barX + 8, barY + 7, textColor, false);

        // Add Button
        int btnX = barX + inputW + spacing;
        boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= barY && mouseY <= barY + barH;
        int btnBg = btnHover ? VayuTheme.HERO_HOVER_START : VayuTheme.HERO_GRADIENT_START;
        VayuHUDUI.roundedRect(g, btnX, barY, btnW, barH, 5, btnBg);
        VayuHUDUI.roundedOutline(g, btnX, barY, btnW, barH, 5, VayuTheme.PRIMARY);
        g.text(this.font, "+ Add Friend", btnX + 10, barY + 7, 0xFF050A10, true);

        // Broadcast Coords Button (Amber)
        if (isInGame) {
            int cBtnX = btnX + btnW + spacing;
            boolean cHover = mouseX >= cBtnX && mouseX <= cBtnX + coordsBtnW && mouseY >= barY && mouseY <= barY + barH;
            int cBg = cHover ? 0xFFF59E0B : 0xFFD97706;
            VayuHUDUI.roundedRect(g, cBtnX, barY, coordsBtnW, barH, 5, cBg);
            VayuHUDUI.roundedOutline(g, cBtnX, barY, coordsBtnW, barH, 5, 0xFFFBBF24);
            g.text(this.font, "📍 Share All", cBtnX + 12, barY + 7, 0xFF050A10, true);
        }
    }

    private void renderFriendsList(GuiGraphicsExtractor g, int contentY, int contentH, int mouseX, int mouseY) {
        List<VayuSocialManager.FriendInfo> friends = social.getFriends();
        if (friends.isEmpty()) {
            g.text(this.font, "No friends added yet. Add players by username above!", panelX + 24, contentY + 24, VayuTheme.TEXT_MUTED, false);
            return;
        }

        boolean isInGame = Minecraft.getInstance().player != null;
        int itemH = 46;
        int totalH = friends.size() * (itemH + 6);
        maxScroll = Math.max(0, totalH - contentH);

        int curY = (int) (contentY + 6 - scrollOffset);

        for (VayuSocialManager.FriendInfo f : friends) {
            if (curY + itemH >= contentY && curY <= contentY + contentH) {
                int itemX = panelX + 16;
                int itemW = panelWidth - 32;

                boolean hover = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= curY && mouseY <= curY + itemH;
                int bg = hover ? 0xE6141E2D : 0xD00A111A;
                VayuHUDUI.roundedRect(g, itemX, curY, itemW, itemH, 6, bg);
                VayuHUDUI.roundedOutline(g, itemX, curY, itemW, itemH, 6, hover ? 0x6638BDF8 : 0x2238BDF8);

                // Avatar Box / Status Dot
                int dotColor = f.isOnline() ? 0xFF22C55E : 0xFF64748B;
                VayuHUDUI.roundedRect(g, itemX + 12, curY + 12, 22, 22, 4, 0xFF0F1722);
                VayuHUDUI.roundedRect(g, itemX + 28, curY + 28, 6, 6, 3, dotColor);
                g.text(this.font, f.username().substring(0, Math.min(1, f.username().length())).toUpperCase(Locale.ROOT),
                    itemX + 19, curY + 18, 0xFF38BDF8, true);

                // Friend Name & Status
                g.text(this.font, f.username(), itemX + 42, curY + 11, 0xFFFFFFFF, true);

                String statusTxt = f.isOnline()
                    ? (f.currentServerAddress() != null && !f.currentServerAddress().isEmpty()
                        ? "Playing on " + f.currentServerAddress()
                        : "Online in Menu")
                    : "Offline";
                int statusColor = f.isOnline() ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED;
                g.text(this.font, statusTxt, itemX + 42, curY + 24, statusColor, false);

                // Action Buttons on Right
                int btnW = 56;
                int btnH = 22;
                int btnY = curY + 12;
                int btnX = itemX + itemW - 12;

                // 1. Remove Button (Red)
                btnX -= 24;
                boolean rmHover = mouseX >= btnX && mouseX <= btnX + 24 && mouseY >= btnY && mouseY <= btnY + btnH;
                VayuHUDUI.roundedRect(g, btnX, btnY, 24, btnH, 4, rmHover ? 0xFFDC2626 : 0xD01F1212);
                VayuHUDUI.drawCloseVector(g, btnX + 12, btnY + 11, 8, 0xFFFFFFFF);

                // 2. Join Server Button (Green, if online with valid multiplayer server)
                if (f.isOnline() && f.currentServerAddress() != null && !f.currentServerAddress().isBlank() &&
                    !f.currentServerAddress().equalsIgnoreCase("Singleplayer")) {
                    btnX -= (btnW + 4);
                    boolean jnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                    VayuHUDUI.roundedRect(g, btnX, btnY, btnW, btnH, 4, jnHover ? 0xFF16A34A : 0xFF15803D);
                    g.text(this.font, "Join", btnX + 16, btnY + 7, 0xFFFFFFFF, true);
                }

                // 3. Share Coords Button (Amber, if in-game and friend is online)
                if (isInGame && f.isOnline()) {
                    btnX -= (btnW + 4);
                    boolean cHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                    VayuHUDUI.roundedRect(g, btnX, btnY, btnW, btnH, 4, cHover ? 0xFFF59E0B : 0xFFD97706);
                    g.text(this.font, "📍 Coords", btnX + 4, btnY + 7, 0xFF050A10, true);
                }

                // 4. Invite Button (Cyan)
                if (f.isOnline()) {
                    btnX -= (btnW + 4);
                    boolean invHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                    VayuHUDUI.roundedRect(g, btnX, btnY, btnW, btnH, 4, invHover ? 0xE60284C7 : 0xD00369A1);
                    g.text(this.font, "Invite", btnX + 12, btnY + 7, 0xFFFFFFFF, false);
                }
            }
            curY += itemH + 6;
        }
    }

    private void renderRequestsList(GuiGraphicsExtractor g, int contentY, int contentH, int mouseX, int mouseY) {
        List<VayuSocialManager.FriendRequestInfo> incoming = social.getIncomingRequests();
        List<VayuSocialManager.FriendRequestInfo> outgoing = social.getOutgoingRequests();

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            g.text(this.font, "No pending friend requests.", panelX + 24, contentY + 24, VayuTheme.TEXT_MUTED, false);
            return;
        }

        int itemH = 40;
        int totalH = (incoming.size() + outgoing.size() + 2) * (itemH + 6);
        maxScroll = Math.max(0, totalH - contentH);

        int curY = (int) (contentY + 6 - scrollOffset);

        if (!incoming.isEmpty()) {
            g.text(this.font, "INCOMING REQUESTS (" + incoming.size() + ")", panelX + 18, curY, VayuTheme.PRIMARY, true);
            curY += 16;

            for (VayuSocialManager.FriendRequestInfo req : incoming) {
                if (curY + itemH >= contentY && curY <= contentY + contentH) {
                    int itemX = panelX + 16;
                    int itemW = panelWidth - 32;

                    VayuHUDUI.roundedRect(g, itemX, curY, itemW, itemH, 6, 0xD00A111A);
                    VayuHUDUI.roundedOutline(g, itemX, curY, itemW, itemH, 6, 0x3338BDF8);

                    g.text(this.font, req.fromUsername(), itemX + 14, curY + 10, 0xFFFFFFFF, true);
                    g.text(this.font, "Wants to be friends", itemX + 14, curY + 22, VayuTheme.TEXT_MUTED, false);

                    // Accept Button (Green)
                    int accX = itemX + itemW - 140;
                    int accY = curY + 9;
                    int accW = 60;
                    int accH = 22;
                    boolean accHover = mouseX >= accX && mouseX <= accX + accW && mouseY >= accY && mouseY <= accY + accH;
                    VayuHUDUI.roundedRect(g, accX, accY, accW, accH, 4, accHover ? 0xFF22C55E : 0xFF16A34A);
                    g.text(this.font, "ACCEPT", accX + 10, accY + 7, 0xFFFFFFFF, true);

                    // Deny Button (Red)
                    int denX = itemX + itemW - 70;
                    int denW = 58;
                    boolean denHover = mouseX >= denX && mouseX <= denX + denW && mouseY >= accY && mouseY <= accY + accH;
                    VayuHUDUI.roundedRect(g, denX, accY, denW, accH, 4, denHover ? 0xFFEF4444 : 0xFFDC2626);
                    g.text(this.font, "DENY", denX + 14, accY + 7, 0xFFFFFFFF, true);
                }
                curY += itemH + 6;
            }
        }

        if (!outgoing.isEmpty()) {
            curY += 10;
            g.text(this.font, "OUTGOING REQUESTS (" + outgoing.size() + ")", panelX + 18, curY, VayuTheme.TEXT_MUTED, true);
            curY += 16;

            for (VayuSocialManager.FriendRequestInfo req : outgoing) {
                if (curY + itemH >= contentY && curY <= contentY + contentH) {
                    int itemX = panelX + 16;
                    int itemW = panelWidth - 32;

                    VayuHUDUI.roundedRect(g, itemX, curY, itemW, itemH, 6, 0xD0080E16);
                    VayuHUDUI.roundedOutline(g, itemX, curY, itemW, itemH, 6, 0x2238BDF8);

                    g.text(this.font, req.toUsername(), itemX + 14, curY + 10, VayuTheme.TEXT_PRIMARY, true);
                    g.text(this.font, "Pending response...", itemX + 14, curY + 22, VayuTheme.TEXT_MUTED, false);
                }
                curY += itemH + 6;
            }
        }
    }

    private void renderOnlinePlayersList(GuiGraphicsExtractor g, int contentY, int contentH, int mouseX, int mouseY) {
        List<String> players = social.getActivePlayers();
        if (players.isEmpty()) {
            g.text(this.font, "No other VayuClient players online right now.", panelX + 24, contentY + 24, VayuTheme.TEXT_MUTED, false);
            return;
        }

        int itemH = 38;
        int totalH = players.size() * (itemH + 6);
        maxScroll = Math.max(0, totalH - contentH);

        int curY = (int) (contentY + 6 - scrollOffset);

        for (String player : players) {
            if (curY + itemH >= contentY && curY <= contentY + contentH) {
                int itemX = panelX + 16;
                int itemW = panelWidth - 32;

                boolean hover = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= curY && mouseY <= curY + itemH;
                int bg = hover ? 0xE6141E2D : 0xD00A111A;
                VayuHUDUI.roundedRect(g, itemX, curY, itemW, itemH, 6, bg);
                VayuHUDUI.roundedOutline(g, itemX, curY, itemW, itemH, 6, hover ? 0x6638BDF8 : 0x2238BDF8);

                // Online indicator + Player Name
                VayuHUDUI.roundedRect(g, itemX + 14, curY + 16, 6, 6, 3, 0xFF22C55E);
                g.text(this.font, player, itemX + 26, curY + 14, 0xFFFFFFFF, true);

                // Add Friend button
                int btnW = 86;
                int btnH = 22;
                int btnX = itemX + itemW - btnW - 12;
                int btnY = curY + 8;

                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                VayuHUDUI.roundedRect(g, btnX, btnY, btnW, btnH, 4, btnHover ? VayuTheme.HERO_HOVER_START : VayuTheme.HERO_GRADIENT_START);
                VayuHUDUI.roundedOutline(g, btnX, btnY, btnW, btnH, 4, VayuTheme.PRIMARY);
                g.text(this.font, "+ Add Friend", btnX + 8, btnY + 7, 0xFF050A10, true);
            }
            curY += itemH + 6;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        int mouseX = DisplaySpace.mouseX(event.x());
        int mouseY = DisplaySpace.mouseY(event.y());

        // Close Button
        int closeX = panelX + panelWidth - 32;
        int closeY = panelY + 14;
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18) {
            onClose();
            return true;
        }

        // Tabs Click
        int tabX = panelX + 16;
        int tabY = panelY + 40;
        int tabH = 22;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i];
            if (i == 0) label += " (" + social.getFriends().size() + ")";
            else if (i == 1) label += " (" + social.getIncomingRequests().size() + ")";
            else label += " (" + social.getActivePlayers().size() + ")";

            int tabW = this.font.width(label) + 16;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                selectedTab = i;
                scrollOffset = 0.0;
                return true;
            }
            tabX += tabW + 6;
        }

        // Add Friend Input Bar Focus & Click
        int barX = panelX + 16;
        int barY = panelY + 68;
        int barH = 22;
        boolean isInGame = Minecraft.getInstance().player != null;
        int addBtnW = 90;
        int coordsBtnW = isInGame ? 100 : 0;
        int spacing = 6;
        int inputW = panelWidth - 32 - addBtnW - (isInGame ? coordsBtnW + spacing : 0) - spacing;

        if (mouseX >= barX && mouseX <= barX + inputW && mouseY >= barY && mouseY <= barY + barH) {
            inputFocused = true;
            cursorBlinkTime = System.currentTimeMillis();
            return true;
        } else {
            inputFocused = false;
        }

        // Add Button Click
        int addBtnX = barX + inputW + spacing;
        if (mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= barY && mouseY <= barY + barH) {
            if (!addFriendInput.trim().isEmpty()) {
                String target = addFriendInput.trim();
                social.sendFriendRequest(target, msg -> showFeedback(msg));
                addFriendInput = "";
            }
            return true;
        }

        // Share All Coords Button Click
        if (isInGame) {
            int cBtnX = addBtnX + addBtnW + spacing;
            if (mouseX >= cBtnX && mouseX <= cBtnX + coordsBtnW && mouseY >= barY && mouseY <= barY + barH) {
                social.shareCoordinates(null, msg -> showFeedback(msg));
                return true;
            }
        }

        // Content Area Actions
        int contentY = panelY + 98;
        int contentH = panelHeight - 110;

        if (mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= contentY && mouseY <= contentY + contentH) {
            if (selectedTab == 0) {
                // Friends Tab Actions
                List<VayuSocialManager.FriendInfo> friends = social.getFriends();
                int itemH = 46;
                int curY = (int) (contentY + 6 - scrollOffset);

                for (VayuSocialManager.FriendInfo f : friends) {
                    if (mouseY >= curY && mouseY <= curY + itemH) {
                        int itemX = panelX + 16;
                        int itemW = panelWidth - 32;
                        int actBtnW = 56;
                        int actBtnH = 22;
                        int btnY = curY + 12;
                        int btnX = itemX + itemW - 12;

                        // 1. Remove Button
                        btnX -= 24;
                        if (mouseX >= btnX && mouseX <= btnX + 24 && mouseY >= btnY && mouseY <= btnY + actBtnH) {
                            social.removeFriend(f.username(), msg -> showFeedback(msg));
                            return true;
                        }

                        // 2. Join Server Button
                        if (f.isOnline() && f.currentServerAddress() != null && !f.currentServerAddress().isBlank() &&
                            !f.currentServerAddress().equalsIgnoreCase("Singleplayer")) {
                            btnX -= (actBtnW + 4);
                            if (mouseX >= btnX && mouseX <= btnX + actBtnW && mouseY >= btnY && mouseY <= btnY + actBtnH) {
                                VayuSocialManager.joinServer(this, f.currentServerAddress());
                                return true;
                            }
                        }

                        // 3. Share Coords Button
                        if (isInGame && f.isOnline()) {
                            btnX -= (actBtnW + 4);
                            if (mouseX >= btnX && mouseX <= btnX + actBtnW && mouseY >= btnY && mouseY <= btnY + actBtnH) {
                                social.shareCoordinates(f.username(), msg -> showFeedback(msg));
                                return true;
                            }
                        }

                        // 4. Invite Button
                        if (f.isOnline()) {
                            btnX -= (actBtnW + 4);
                            if (mouseX >= btnX && mouseX <= btnX + actBtnW && mouseY >= btnY && mouseY <= btnY + actBtnH) {
                                social.sendServerInvite(f.username(), msg -> showFeedback(msg));
                                return true;
                            }
                        }
                    }
                    curY += itemH + 6;
                }
            } else if (selectedTab == 1) {
                // Requests Tab Actions (Accept / Deny)
                List<VayuSocialManager.FriendRequestInfo> incoming = social.getIncomingRequests();
                int itemH = 40;
                int curY = (int) (contentY + 6 - scrollOffset);

                if (!incoming.isEmpty()) {
                    curY += 16; // Skip header
                    for (VayuSocialManager.FriendRequestInfo req : incoming) {
                        if (mouseY >= curY && mouseY <= curY + itemH) {
                            int itemX = panelX + 16;
                            int itemW = panelWidth - 32;
                            int accX = itemX + itemW - 140;
                            int accY = curY + 9;
                            int accW = 60;
                            int accH = 22;

                            // Accept
                            if (mouseX >= accX && mouseX <= accX + accW && mouseY >= accY && mouseY <= accY + accH) {
                                social.respondFriendRequest(req.fromUsername(), true, msg -> showFeedback(msg));
                                return true;
                            }

                            // Deny
                            int denX = itemX + itemW - 70;
                            int denW = 58;
                            if (mouseX >= denX && mouseX <= denX + denW && mouseY >= accY && mouseY <= accY + accH) {
                                social.respondFriendRequest(req.fromUsername(), false, msg -> showFeedback(msg));
                                return true;
                            }
                        }
                        curY += itemH + 6;
                    }
                }
            } else if (selectedTab == 2) {
                // Online Players Tab Action
                List<String> players = social.getActivePlayers();
                int itemH = 38;
                int curY = (int) (contentY + 6 - scrollOffset);

                for (String player : players) {
                    if (mouseY >= curY && mouseY <= curY + itemH) {
                        int itemX = panelX + 16;
                        int itemW = panelWidth - 32;
                        int plBtnW = 86;
                        int plBtnH = 22;
                        int btnX = itemX + itemW - plBtnW - 12;
                        int btnY = curY + 8;

                        if (mouseX >= btnX && mouseX <= btnX + plBtnW && mouseY >= btnY && mouseY <= btnY + plBtnH) {
                            social.sendFriendRequest(player, msg -> showFeedback(msg));
                            return true;
                        }
                    }
                    curY += itemH + 6;
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (inputFocused) {
            if (event.key() == 259) { // Backspace
                if (!addFriendInput.isEmpty()) {
                    addFriendInput = addFriendInput.substring(0, addFriendInput.length() - 1);
                }
                return true;
            }
            if (event.key() == 257) { // Enter
                if (!addFriendInput.trim().isEmpty()) {
                    social.sendFriendRequest(addFriendInput.trim(), msg -> showFeedback(msg));
                    addFriendInput = "";
                }
                return true;
            }
            if (event.key() == 256) { // Escape
                inputFocused = false;
                return true;
            }
        }
        if (event.key() == 256) { // Escape
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputFocused) {
            char c = (char) event.codepoint();
            if (c >= 32 && c != 127) {
                addFriendInput += c;
                return true;
            }
        }
        return super.charTyped(event);
    }

    private void showFeedback(String msg) {
        this.statusMessage = msg;
        this.statusMessageTime = System.currentTimeMillis();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
