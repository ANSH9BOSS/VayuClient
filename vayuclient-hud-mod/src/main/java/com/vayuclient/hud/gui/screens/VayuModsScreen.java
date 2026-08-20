package com.vayuclient.hud.gui.screens;

import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.mods.ModEntry;
import com.vayuclient.hud.mods.ModIntegrationManager;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VayuModsScreen extends Screen {
    private final Screen parent;
    private final ModIntegrationManager manager = ModIntegrationManager.getInstance();

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private String selectedCategory = "all"; // all, performance, visual, utility, library, user
    private String searchQuery = "";
    private boolean searchFocused = false;
    private long cursorBlinkTime = 0L;

    private final List<ModEntry> filteredMods = new ArrayList<>();
    private ModEntry selectedMod = null;

    private double scrollOffset = 0.0;
    private double maxScroll = 0.0;
    private AnimationUtils.Animation openAnimation;
    private long lastUpdate = System.currentTimeMillis();

    // Navigation tabs
    private static final String[] CATEGORIES = {"all", "performance", "visual", "utility", "library"};
    private static final String[] CATEGORY_NAMES = {"All", "Performance", "Visuals", "Utilities", "Libraries"};
    private final float[] tabHover = new float[CATEGORIES.length];

    public VayuModsScreen(Screen parent) {
        super(Component.literal("VayuClient Mod Manager"));
        this.parent = parent;
    }

    public VayuModsScreen() {
        this(null);
    }

    @Override
    protected void init() {
        manager.init();
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 220L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);

        rebuildLayout();
        filterMods();
    }

    private void rebuildLayout() {
        int dw = DisplaySpace.width();
        int dh = DisplaySpace.height();

        this.panelWidth = Math.min(Math.max(480, dw - 40), 760);
        this.panelHeight = Math.min(Math.max(280, dh - 40), 440);
        this.panelX = (dw - this.panelWidth) / 2;
        this.panelY = (dh - this.panelHeight) / 2;
    }

    private void filterMods() {
        filteredMods.clear();
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);

        for (ModEntry mod : manager.getInstalledMods()) {
            // Category filter
            if (!"all".equals(selectedCategory)) {
                if (!selectedCategory.equals(mod.getCategory())) {
                    continue;
                }
            }

            // Search filter
            if (!query.isEmpty()) {
                boolean match = mod.getName().toLowerCase(Locale.ROOT).contains(query)
                        || mod.getId().toLowerCase(Locale.ROOT).contains(query)
                        || mod.getDescription().toLowerCase(Locale.ROOT).contains(query)
                        || mod.getAuthorsString().toLowerCase(Locale.ROOT).contains(query);
                if (!match) {
                    continue;
                }
            }

            filteredMods.add(mod);
        }

        if (selectedMod == null || !filteredMods.contains(selectedMod)) {
            selectedMod = filteredMods.isEmpty() ? null : filteredMods.get(0);
        }

        // Calculate scroll bounds
        int listHeight = panelHeight - 96;
        int totalContentHeight = filteredMods.size() * 46;
        this.maxScroll = Math.max(0, totalContentHeight - listHeight);
        if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - this.lastUpdate) / 1000.0f);
        this.lastUpdate = now;

        float anim = this.openAnimation != null ? this.openAnimation.getValue() : 1.0f;
        int animatedPanelY = (int) ((float) this.panelY + (1.0f - anim) * 12.0f);
        int alpha = (int) (anim * 255.0f);
        if (alpha < 5) return;

        int dw = DisplaySpace.width();
        int dh = DisplaySpace.height();

        // Dark ambient backdrop
        graphics.fill(0, 0, dw, dh, VayuHUDUI.withAlpha(0x90000000, alpha));

        // Main Glass Panel
        int panelBg = VayuHUDUI.withAlpha(0xF2070D18, alpha);
        int panelBorder = VayuHUDUI.withAlpha(0x3A38BDF8, alpha);
        VayuHUDUI.roundedRect(graphics, panelX, animatedPanelY, panelWidth, panelHeight, 10, panelBg);
        VayuHUDUI.roundedOutline(graphics, panelX, animatedPanelY, panelWidth, panelHeight, 10, panelBorder);

        // Header
        drawHeader(graphics, panelX, animatedPanelY, panelWidth, pxMouseX, pxMouseY, alpha);

        // Category & Search Bar
        drawControlsRow(graphics, panelX, animatedPanelY + 40, panelWidth, pxMouseX, pxMouseY, alpha, dt);

        // Content split: Left = Mod List (52%), Right = Mod Details (48%)
        int listW = (int) ((panelWidth - 28) * 0.52f);
        int detailsW = panelWidth - 28 - listW - 10;
        int contentY = animatedPanelY + 70;
        int contentH = panelHeight - 80;

        drawModList(graphics, panelX + 12, contentY, listW, contentH, pxMouseX, pxMouseY, alpha);
        drawModDetails(graphics, panelX + 12 + listW + 10, contentY, detailsW, contentH, pxMouseX, pxMouseY, alpha);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha) {
        int headY = y + 10;

        // Left Emblem + Title
        int iconX = x + 14;
        int iconY = headY;
        VayuHUDUI.roundedRect(graphics, iconX, iconY, 22, 22, 5, 0xFF0284C7);
        VayuHUDUI.drawModsVector(graphics, iconX + 11, iconY + 11, 12, 0xFFFFFFFF);

        graphics.text(this.font, "VAYUCLIENT MOD MANAGER", iconX + 30, headY + 3, VayuTheme.PRIMARY, true);
        graphics.text(this.font, "Installed JARs & Runtime Integrations (" + manager.getInstalledMods().size() + " Loaded)", iconX + 30, headY + 13, VayuTheme.TEXT_MUTED, false);

        // Top Navigation Switcher (Center-Right)
        int navBtnH = 20;
        int curX = x + w - 12;

        // 1. Close Button
        int closeW = 20;
        curX -= closeW;
        boolean ch = mouseX >= curX && mouseX <= curX + closeW && mouseY >= headY && mouseY <= headY + navBtnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, closeW, navBtnH, 4, ch ? 0xFFDC2626 : 0xD01F1212);
        VayuHUDUI.roundedOutline(graphics, curX, headY, closeW, navBtnH, 4, ch ? 0xFFEF4444 : 0x44EF4444);
        VayuHUDUI.drawCloseVector(graphics, curX + closeW / 2, headY + navBtnH / 2, 8, 0xFFFFFFFF);

        // 2. Open Mods Folder Button
        int folderW = 86;
        curX -= (folderW + 6);
        boolean fh = mouseX >= curX && mouseX <= curX + folderW && mouseY >= headY && mouseY <= headY + navBtnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, folderW, navBtnH, 4, fh ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, curX, headY, folderW, navBtnH, 4, fh ? VayuTheme.PRIMARY : 0x2A38BDF8);
        graphics.text(this.font, "📁 Open Folder", curX + 8, headY + 6, fh ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);

        // 3. Switch to HUD Modules Button
        int hudBtnW = 82;
        curX -= (hudBtnW + 6);
        boolean hh = mouseX >= curX && mouseX <= curX + hudBtnW && mouseY >= headY && mouseY <= headY + navBtnH;
        VayuHUDUI.roundedRect(graphics, curX, headY, hudBtnW, navBtnH, 4, hh ? 0xFF0284C7 : 0xD00F1722);
        VayuHUDUI.roundedOutline(graphics, curX, headY, hudBtnW, navBtnH, 4, hh ? VayuTheme.PRIMARY : 0x3338BDF8);
        graphics.text(this.font, "HUD Modules", curX + 10, headY + 6, hh ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY, true);
    }

    private void drawControlsRow(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, int alpha, float dt) {
        int tabX = x + 12;
        int tabH = 20;

        // Categories
        for (int i = 0; i < CATEGORIES.length; i++) {
            String catKey = CATEGORIES[i];
            String catName = CATEGORY_NAMES[i];
            int count = getCategoryCount(catKey);
            String label = catName + " (" + count + ")";
            int tabW = this.font.width(label) + 12;

            boolean isSelected = selectedCategory.equals(catKey);
            boolean isHovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH;

            this.tabHover[i] = AnimationUtils.smoothDelta(this.tabHover[i], isHovered ? 1.0f : 0.0f, 0.4f, dt * 60.0f);
            int bg = isSelected ? VayuTheme.PRIMARY : VayuHUDUI.blend(0xD00A111A, 0xE6141E2D, this.tabHover[i]);
            VayuHUDUI.roundedRect(graphics, tabX, y, tabW, tabH, 4, VayuHUDUI.withAlpha(bg, alpha));
            VayuHUDUI.roundedOutline(graphics, tabX, y, tabW, tabH, 4, isSelected ? VayuTheme.PRIMARY : 0x2238BDF8);

            int textColor = isSelected ? 0xFF050A10 : (isHovered ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED);
            graphics.text(this.font, label, tabX + 6, y + 6, textColor, isSelected);

            tabX += tabW + 5;
        }

        // Search Input
        int searchW = Math.min(150, Math.max(90, (x + w - 12) - tabX));
        if (searchW > 60) {
            int searchX = x + w - 12 - searchW;
            int searchBg = this.searchFocused ? 0xE6141E2D : 0xD00A111A;
            VayuHUDUI.roundedRect(graphics, searchX, y, searchW, tabH, 4, searchBg);
            VayuHUDUI.roundedOutline(graphics, searchX, y, searchW, tabH, 4, this.searchFocused ? VayuTheme.PRIMARY : 0x2238BDF8);

            int textX = searchX + 8;
            int textY = y + 6;

            if (this.searchQuery.isEmpty() && !this.searchFocused) {
                graphics.text(this.font, "Search mods...", textX, textY, VayuTheme.TEXT_MUTED, false);
            } else {
                graphics.text(this.font, this.searchQuery, textX, textY, 0xFFFFFFFF, false);
                if (this.searchFocused && (System.currentTimeMillis() - this.cursorBlinkTime) % 1000L < 500L) {
                    int cursorX = textX + this.font.width(this.searchQuery);
                    graphics.fill(cursorX, textY, cursorX + 1, textY + 8, 0xFF38BDF8);
                }
            }
        }
    }

    private int getCategoryCount(String cat) {
        if ("all".equals(cat)) return manager.getInstalledMods().size();
        int c = 0;
        for (ModEntry m : manager.getInstalledMods()) {
            if (cat.equals(m.getCategory())) c++;
        }
        return c;
    }

    private void drawModList(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY, int alpha) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xC0070E1A);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x2238BDF8);

        graphics.enableScissor(x, y, x + w, y + h);

        int itemH = 44;
        int curY = y + 4 - (int) scrollOffset;

        for (ModEntry mod : filteredMods) {
            if (curY + itemH >= y && curY <= y + h) {
                boolean isSelected = (selectedMod == mod);
                boolean isHovered = mouseX >= x + 4 && mouseX <= x + w - 4 && mouseY >= curY && mouseY <= curY + itemH && mouseY >= y && mouseY <= y + h;

                int bg = isSelected ? 0xD00F2338 : (isHovered ? 0xC0141E2D : 0x800A111A);
                int border = isSelected ? VayuTheme.PRIMARY : (isHovered ? 0x4438BDF8 : 0x1538BDF8);

                VayuHUDUI.roundedRect(graphics, x + 4, curY, w - 8, itemH - 4, 4, bg);
                VayuHUDUI.roundedOutline(graphics, x + 4, curY, w - 8, itemH - 4, 4, border);

                // Mod Emblem Box (Initial letter)
                int emblemSize = 24;
                int emblemX = x + 10;
                int emblemY = curY + (itemH - 4 - emblemSize) / 2;
                int emblemColor = getEmblemColor(mod.getCategory());
                VayuHUDUI.roundedRect(graphics, emblemX, emblemY, emblemSize, emblemSize, 4, emblemColor);
                String initial = mod.getName().isEmpty() ? "M" : mod.getName().substring(0, 1).toUpperCase(Locale.ROOT);
                graphics.text(this.font, initial, emblemX + (emblemSize - font.width(initial)) / 2, emblemY + 8, 0xFFFFFFFF, true);

                // Mod Name & Version
                int textX = emblemX + emblemSize + 8;
                graphics.text(this.font, mod.getName(), textX, curY + 6, isSelected ? 0xFFFFFFFF : (isHovered ? 0xFFFFFFFF : VayuTheme.TEXT_PRIMARY), true);

                // Subtitle: ID • vVersion
                String sub = mod.getId() + " • v" + mod.getVersion();
                if (this.font.width(sub) > w - 110) {
                    sub = mod.getVersion();
                }
                graphics.text(this.font, sub, textX, curY + 18, VayuTheme.TEXT_MUTED, false);

                // Config Indicator Badge on the right
                if (mod.hasConfig()) {
                    int badgeW = 44;
                    int badgeH = 16;
                    int badgeX = x + w - 12 - badgeW;
                    int badgeY = curY + (itemH - 4 - badgeH) / 2;
                    VayuHUDUI.roundedRect(graphics, badgeX, badgeY, badgeW, badgeH, 3, 0xD00284C7);
                    graphics.text(this.font, "⚙ Config", badgeX + 4, badgeY + 4, 0xFFFFFFFF, false);
                }
            }
            curY += itemH;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbW = 4;
            int sbX = x + w - sbW - 2;
            int sbH = (int) (h * (h / (float) (filteredMods.size() * itemH)));
            sbH = Math.max(16, sbH);
            int sbY = y + (int) ((h - sbH) * (scrollOffset / maxScroll));
            VayuHUDUI.roundedRect(graphics, sbX, sbY, sbW, sbH, 2, 0x6638BDF8);
        }
    }

    private int getEmblemColor(String cat) {
        return switch (cat) {
            case "performance" -> 0xFF10B981; // Green
            case "visual" -> 0xFF8B5CF6;      // Purple
            case "utility" -> 0xFF0284C7;     // Cyan
            case "library" -> 0xFF64748B;     // Slate
            default -> 0xFF0284C7;
        };
    }

    private void drawModDetails(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY, int alpha) {
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 6, 0xD0070E1A);
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 6, 0x3338BDF8);

        if (selectedMod == null) {
            graphics.text(this.font, "Select a mod to view details", x + 16, y + 20, VayuTheme.TEXT_MUTED, false);
            return;
        }

        int innerX = x + 12;
        int innerY = y + 12;
        int innerW = w - 24;

        // Top Mod Banner Header
        int embSize = 32;
        int embCol = getEmblemColor(selectedMod.getCategory());
        VayuHUDUI.roundedRect(graphics, innerX, innerY, embSize, embSize, 6, embCol);
        String initial = selectedMod.getName().isEmpty() ? "M" : selectedMod.getName().substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.text(this.font, initial, innerX + (embSize - font.width(initial)) / 2, innerY + 12, 0xFFFFFFFF, true);

        // Mod Title & Category Badge
        graphics.text(this.font, selectedMod.getName(), innerX + embSize + 10, innerY + 2, VayuTheme.PRIMARY, true);
        String subHead = "ID: " + selectedMod.getId() + " • v" + selectedMod.getVersion() + " (" + selectedMod.getLoader() + ")";
        graphics.text(this.font, subHead, innerX + embSize + 10, innerY + 14, VayuTheme.TEXT_MUTED, false);

        int curY = innerY + embSize + 12;

        // Action Buttons Row (Configure Mod / Open in Explorer)
        int btnH = 24;
        if (selectedMod.hasConfig()) {
            boolean cfgHover = mouseX >= innerX && mouseX <= innerX + innerW && mouseY >= curY && mouseY <= curY + btnH;
            VayuHUDUI.roundedRect(graphics, innerX, curY, innerW, btnH, 4, cfgHover ? 0xFF0284C7 : 0xD00369A1);
            VayuHUDUI.roundedOutline(graphics, innerX, curY, innerW, btnH, 4, cfgHover ? 0xFF38BDF8 : 0x6638BDF8);
            String cfgText = "⚙ CONFIGURE MOD SETTINGS";
            graphics.text(this.font, cfgText, innerX + (innerW - font.width(cfgText)) / 2, curY + 8, 0xFFFFFFFF, true);
            curY += btnH + 6;
        }

        // Secondary Action: Reveal File in Explorer
        boolean revHover = mouseX >= innerX && mouseX <= innerX + innerW && mouseY >= curY && mouseY <= curY + btnH;
        VayuHUDUI.roundedRect(graphics, innerX, curY, innerW, btnH, 4, revHover ? 0xE6141E2D : 0xD00A111A);
        VayuHUDUI.roundedOutline(graphics, innerX, curY, innerW, btnH, 4, revHover ? VayuTheme.PRIMARY : 0x2A38BDF8);
        String revText = "📂 Show File: " + selectedMod.getFileName();
        if (font.width(revText) > innerW - 12) {
            revText = "📂 Show JAR in File Explorer";
        }
        graphics.text(this.font, revText, innerX + 8, curY + 8, revHover ? 0xFFFFFFFF : VayuTheme.TEXT_MUTED, false);
        curY += btnH + 10;

        // Details Section Divider
        graphics.fill(innerX, curY, innerX + innerW, curY + 1, 0x2238BDF8);
        curY += 8;

        // Metadata Fields
        graphics.text(this.font, "Authors:", innerX, curY, VayuTheme.TEXT_MUTED, false);
        graphics.text(this.font, selectedMod.getAuthorsString(), innerX + 54, curY, 0xFFCBD5E1, false);
        curY += 14;

        // Links Row (Homepage, Issues, Sources)
        int linkX = innerX;
        if (selectedMod.getHomepage() != null) {
            int lW = font.width("🌐 Homepage") + 10;
            boolean lh = mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16;
            VayuHUDUI.roundedRect(graphics, linkX, curY, lW, 16, 3, lh ? 0xE6141E2D : 0x800A111A);
            graphics.text(this.font, "🌐 Homepage", linkX + 5, curY + 4, lh ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED, false);
            linkX += lW + 6;
        }
        if (selectedMod.getSources() != null) {
            int lW = font.width("💻 Sources") + 10;
            boolean lh = mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16;
            VayuHUDUI.roundedRect(graphics, linkX, curY, lW, 16, 3, lh ? 0xE6141E2D : 0x800A111A);
            graphics.text(this.font, "💻 Sources", linkX + 5, curY + 4, lh ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED, false);
            linkX += lW + 6;
        }
        if (selectedMod.getIssues() != null) {
            int lW = font.width("🐛 Issues") + 10;
            boolean lh = mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16;
            VayuHUDUI.roundedRect(graphics, linkX, curY, lW, 16, 3, lh ? 0xE6141E2D : 0x800A111A);
            graphics.text(this.font, "🐛 Issues", linkX + 5, curY + 4, lh ? VayuTheme.PRIMARY : VayuTheme.TEXT_MUTED, false);
        }
        curY += 22;

        // Description Box
        graphics.text(this.font, "Description:", innerX, curY, VayuTheme.TEXT_MUTED, false);
        curY += 12;

        int descBoxH = (y + h - 12) - curY;
        if (descBoxH > 20) {
            VayuHUDUI.roundedRect(graphics, innerX, curY, innerW, descBoxH, 4, 0x80050A10);
            graphics.enableScissor(innerX, curY, innerX + innerW, curY + descBoxH);

            List<String> wrapped = wrapText(selectedMod.getDescription(), innerW - 16);
            int lineY = curY + 6;
            for (String line : wrapped) {
                if (lineY + 10 > curY + descBoxH) break;
                graphics.text(this.font, line, innerX + 8, lineY, 0xFFCBD5E1, false);
                lineY += 11;
            }
            graphics.disableScissor();
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("No description provided.");
            return lines;
        }

        String[] paragraphs = text.split("\n");
        for (String p : paragraphs) {
            String[] words = p.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                String candidate = sb.length() == 0 ? w : sb + " " + w;
                if (font.width(candidate) <= maxWidth) {
                    sb = new StringBuilder(candidate);
                } else {
                    if (sb.length() > 0) lines.add(sb.toString());
                    sb = new StringBuilder(w);
                }
            }
            if (sb.length() > 0) lines.add(sb.toString());
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        int button = event.button();

        if (button != 0) return super.mouseClicked(event, bl);

        int headY = panelY + 10;
        int navBtnH = 20;

        // 1. Close Button
        int closeW = 20;
        int closeX = panelX + panelWidth - 12 - closeW;
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= headY && mouseY <= headY + navBtnH) {
            onClose();
            return true;
        }

        // 2. Open Mods Folder
        int folderW = 86;
        int folderX = closeX - 6 - folderW;
        if (mouseX >= folderX && mouseX <= folderX + folderW && mouseY >= headY && mouseY <= headY + navBtnH) {
            manager.openModsFolder();
            return true;
        }

        // 3. Switch to HUD Modules
        int hudBtnW = 82;
        int hudBtnX = folderX - 6 - hudBtnW;
        if (mouseX >= hudBtnX && mouseX <= hudBtnX + hudBtnW && mouseY >= headY && mouseY <= headY + navBtnH) {
            Minecraft.getInstance().gui.setScreen(new ClickGUIScreen());
            return true;
        }

        // 4. Category Tabs
        int tabX = panelX + 12;
        int tabY = panelY + 40;
        int tabH = 20;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String catKey = CATEGORIES[i];
            String catName = CATEGORY_NAMES[i];
            int count = getCategoryCount(catKey);
            int tabW = this.font.width(catName + " (" + count + ")") + 12;

            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                selectedCategory = catKey;
                scrollOffset = 0.0;
                filterMods();
                return true;
            }
            tabX += tabW + 5;
        }

        // 5. Search Box Focus
        int searchW = Math.min(150, Math.max(90, (panelX + panelWidth - 12) - tabX));
        int searchX = panelX + panelWidth - 12 - searchW;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= tabY && mouseY <= tabY + tabH) {
            searchFocused = true;
            cursorBlinkTime = System.currentTimeMillis();
            return true;
        } else {
            searchFocused = false;
        }

        // 6. Mod List Selection & Actions
        int listW = (int) ((panelWidth - 28) * 0.52f);
        int contentY = panelY + 70;
        int contentH = panelHeight - 80;
        int itemH = 44;

        if (mouseX >= panelX + 12 && mouseX <= panelX + 12 + listW && mouseY >= contentY && mouseY <= contentY + contentH) {
            int relativeY = (int) (mouseY - contentY + scrollOffset - 4);
            int index = relativeY / itemH;
            if (index >= 0 && index < filteredMods.size()) {
                selectedMod = filteredMods.get(index);
                return true;
            }
        }

        // 7. Mod Details Action Buttons
        if (selectedMod != null) {
            int detailsX = panelX + 12 + listW + 10;
            int detailsW = panelWidth - 28 - listW - 10;
            int innerX = detailsX + 12;
            int innerW = detailsW - 24;
            int embSize = 32;
            int curY = contentY + 12 + embSize + 12;
            int btnH = 24;

            // Configure Button
            if (selectedMod.hasConfig()) {
                if (mouseX >= innerX && mouseX <= innerX + innerW && mouseY >= curY && mouseY <= curY + btnH) {
                    Screen configScreen = manager.createConfigScreen(selectedMod.getId(), this);
                    if (configScreen != null) {
                        Minecraft.getInstance().gui.setScreen(configScreen);
                    }
                    return true;
                }
                curY += btnH + 6;
            }

            // Reveal File Button
            if (mouseX >= innerX && mouseX <= innerX + innerW && mouseY >= curY && mouseY <= curY + btnH) {
                manager.openInFolder(selectedMod.getJarPath());
                return true;
            }
            curY += btnH + 10;

            // Links (Homepage, Sources, Issues)
            curY += 9 + 14;
            int linkX = innerX;
            if (selectedMod.getHomepage() != null) {
                int lW = font.width("🌐 Homepage") + 10;
                if (mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16) {
                    manager.openUrl(selectedMod.getHomepage());
                    return true;
                }
                linkX += lW + 6;
            }
            if (selectedMod.getSources() != null) {
                int lW = font.width("💻 Sources") + 10;
                if (mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16) {
                    manager.openUrl(selectedMod.getSources());
                    return true;
                }
                linkX += lW + 6;
            }
            if (selectedMod.getIssues() != null) {
                int lW = font.width("🐛 Issues") + 10;
                if (mouseX >= linkX && mouseX <= linkX + lW && mouseY >= curY && mouseY <= curY + 16) {
                    manager.openUrl(selectedMod.getIssues());
                    return true;
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
        if (searchFocused) {
            if (event.key() == 259) { // Backspace
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    filterMods();
                }
                return true;
            }
            if (event.key() == 256) { // Escape
                searchFocused = false;
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
        if (searchFocused) {
            char c = (char) event.codepoint();
            if (c >= 32 && c != 127) {
                searchQuery += c;
                filterMods();
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (parent != null) {
            Minecraft.getInstance().gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
