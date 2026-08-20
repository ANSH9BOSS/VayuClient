package com.vayuclient.hud.gui.screens;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import com.vayuclient.hud.VayuHUDClient;
import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuFonts;
import com.vayuclient.hud.gui.VayuHUDUI;
import com.vayuclient.hud.gui.VayuTheme;
import com.vayuclient.hud.gui.components.ColorPicker;
import com.vayuclient.hud.gui.components.Dropdown;
import com.vayuclient.hud.gui.components.KeybindButton;
import com.vayuclient.hud.gui.components.Slider;
import com.vayuclient.hud.gui.components.TextInput;
import com.vayuclient.hud.gui.components.ToggleButton;
import com.vayuclient.hud.gui.components.UIComponent;
import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import com.vayuclient.hud.modules.settings.KeybindSetting;
import com.vayuclient.hud.modules.settings.ModeSetting;
import com.vayuclient.hud.modules.settings.NumberSetting;
import com.vayuclient.hud.modules.settings.Setting;
import com.vayuclient.hud.modules.settings.TextSetting;
import com.vayuclient.hud.render.AnimationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

public class ModuleConfigScreen extends Screen {
    private final Module module;
    private final Screen parent;
    private final List<UIComponent> components = new ArrayList<>();
    private final IdentityHashMap<UIComponent, SettingRow> rows = new IdentityHashMap<>();
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private double maxScroll = 0.0;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private AnimationUtils.Animation openAnimation;
    private long lastUpdate = System.currentTimeMillis();

    // Interactive Header Bounds
    private int backBtnX, backBtnY, backBtnW, backBtnH;
    private int headerToggleX, headerToggleY, headerToggleW, headerToggleH;
    private int hudPosBtnX, hudPosBtnY, hudPosBtnW, hudPosBtnH;

    public ModuleConfigScreen(Module module, Screen parent) {
        super(Component.literal(module.getDisplayName() + " Configuration"));
        this.module = module;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.components.clear();
        this.rows.clear();

        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();

        this.panelWidth = Math.max(360, Math.min(displayWidth - 40, (int)((double)displayWidth * 0.54)));
        this.panelHeight = Math.max(280, Math.min(displayHeight - 60, (int)((double)displayHeight * 0.76)));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = (displayHeight - this.panelHeight) / 2;

        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 220L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);

        int settingWidth = this.panelWidth - 64;
        int centerX = this.panelX + 32;
        int y = this.panelY + 116;
        int rowGap = 12;

        // 1. Module Keybind Row
        KeybindButton moduleKeybind = new KeybindButton(centerX, y, settingWidth, 28, "toggle_keybind", this.module.getKeyBinding(), this.module.getKeyModifiers(), (keyCode, modifiers) -> {
            this.module.setKeyBinding(keyCode, modifiers);
            this.saveAndUpdate();
        });
        this.addRow(moduleKeybind, this.module.getName().equals("Waypoints") ? "Key used to open the waypoint manager while enabled." : "Key used to quickly toggle this module on or off in-game.", 28);
        y += this.rowHeight(28) + rowGap;

        // 2. Dynamic Settings Rows
        for (Setting<?> setting : this.module.getSettings()) {
            if (!setting.isVisible()) continue;
            UIComponent component = null;
            int controlHeight = 28;

            if (setting instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting)setting;
                component = new ToggleButton(centerX, y, settingWidth, 28, bs.getName(), bs.getValue(), v -> {
                    bs.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting)setting;
                controlHeight = 50;
                component = new Slider(centerX, y, settingWidth, controlHeight, ns.getName(), ns.getValue(), ns.getMin(), ns.getMax(), ns.getStep(), v -> {
                    ns.setValue(v);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting)setting;
                String[] modes = ms.getModes();
                component = new Dropdown(centerX, y, settingWidth, 28, ms.getName(), modes, ms.getValue(), v -> {
                    ms.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting)setting;
                component = new ColorPicker(centerX, y, settingWidth, 28, cs.getName(), cs.getValue(), color -> {
                    cs.setValue(color);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof TextSetting) {
                TextSetting ts = (TextSetting)setting;
                component = new TextInput(centerX, y, settingWidth, 28, ts.getName(), ts.getValue(), value -> {
                    ts.setValue(value);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof KeybindSetting) {
                KeybindSetting ks = (KeybindSetting)setting;
                component = new KeybindButton(centerX, y, settingWidth, 28, ks.getName(), ks.getKey(), value -> {
                    ks.setKey(value);
                    this.saveAndUpdate();
                });
            }

            if (component == null) continue;
            this.addRow(component, setting.getDescription(), controlHeight);
            y += this.rowHeight(controlHeight) + rowGap;
        }

        this.maxScroll = Math.max(0, y - (this.panelY + this.panelHeight - 44));
    }

    private void addRow(UIComponent component, String description, int controlHeight) {
        this.components.add(component);
        this.rows.put(component, new SettingRow(description == null ? "" : description, controlHeight));
    }

    private int rowHeight(int controlHeight) {
        return controlHeight + 32;
    }

    private void rebuildComponents() {
        double savedScroll = this.targetScrollOffset;
        this.init();
        this.targetScrollOffset = Math.min(savedScroll, this.maxScroll);
    }

    private void saveAndUpdate() {
        VayuHUDClient.getInstance().getModuleManager().saveConfig();
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), 0xCC020610);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);

        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);

        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;

        this.scrollOffset = AnimationUtils.smoothDelta((float)this.scrollOffset, (float)this.targetScrollOffset, 0.35f, dt * 60.0f);
        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int)((float)this.panelY + (1.0f - animProgress) * 24.0f);
        int alpha = (int)(animProgress * 255.0f);

        // 1. Panel Container Glassmorphism
        this.drawPanelBackground(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, alpha);

        // 2. Hero Header
        this.drawHeader(graphics, this.panelX, animatedPanelY, this.panelWidth, alpha, pxMouseX, pxMouseY);

        // 3. Scrollable Setting Cards
        int contentY = animatedPanelY + 104;
        int contentHeight = this.panelHeight - 144;

        DisplaySpace.enableScissor(graphics, this.panelX + 8, contentY, this.panelX + this.panelWidth - 8, contentY + contentHeight);

        // Pass 1: Render regular cards & controls
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            int originalY = component.getY();
            int adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);

            boolean isExpandedOverlay = (component instanceof Dropdown && ((Dropdown)component).getHeight() > 32)
                || (component instanceof ColorPicker && ((ColorPicker)component).isExpanded());

            if (!isExpandedOverlay && adjustedY + component.getHeight() > contentY - 12 && adjustedY < contentY + contentHeight + 12) {
                this.drawSettingRowBackground(graphics, component, adjustedY, pxMouseX, pxMouseY);
                component.render(graphics, pxMouseX, pxMouseY, delta);
                this.drawSettingDescription(graphics, component, adjustedY, alpha);
            }
            component.setY(originalY);
        }

        DisplaySpace.disableScissor(graphics);

        // Pass 2: Render popout overlays (Dropdowns / ColorPicker popups outside scissor)
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            int originalY = component.getY();
            int adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);

            boolean isExpandedOverlay = (component instanceof Dropdown && ((Dropdown)component).isExpanded())
                || (component instanceof ColorPicker && ((ColorPicker)component).isExpanded());

            if (isExpandedOverlay) {
                component.render(graphics, pxMouseX, pxMouseY, delta);
            }
            component.setY(originalY);
        }

        // 4. Modern Scrollbar
        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics, this.panelX + this.panelWidth - 8, contentY, 3, contentHeight, alpha);
        }

        // 5. Polished Footer
        this.drawFooter(graphics, this.panelX, animatedPanelY + this.panelHeight - 34, this.panelWidth, alpha);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        // Deep Glass Surface
        VayuHUDUI.roundedRect(graphics, x, y, w, h, 10, VayuHUDUI.withAlpha(0xF0080F1D, alpha));
        // Cyan Ambient Border
        VayuHUDUI.roundedOutline(graphics, x, y, w, h, 10, VayuHUDUI.withAlpha(0x4438BDF8, alpha));
        // Top Glow Accent Line
        graphics.fill(x + 12, y, x + w - 12, y + 2, VayuHUDUI.withAlpha(0xFF38BDF8, alpha));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha, int mouseX, int mouseY) {
        // 1. Back Button Pill
        this.backBtnX = x + 16;
        this.backBtnY = y + 16;
        this.backBtnW = 60;
        this.backBtnH = 26;
        boolean backHover = isHovered(this.backBtnX, this.backBtnY, this.backBtnW, this.backBtnH, mouseX, mouseY);

        VayuHUDUI.roundedRect(graphics, this.backBtnX, this.backBtnY, this.backBtnW, this.backBtnH, 6, 
            VayuHUDUI.withAlpha(backHover ? 0xFF0284C7 : 0xD0111B2B, alpha));
        VayuHUDUI.roundedOutline(graphics, this.backBtnX, this.backBtnY, this.backBtnW, this.backBtnH, 6, 
            VayuHUDUI.withAlpha(backHover ? 0xFF38BDF8 : 0x3338BDF8, alpha));
        
        String backText = "‹ BACK";
        this.drawUiText(graphics, backText, this.backBtnX + 10, this.backBtnY + 8, VayuHUDUI.withAlpha(backHover ? 0xFFFFFFFF : 0xFF38BDF8, alpha));

        // 2. Module Icon Glass Container
        int iconBoxSize = 36;
        int iconBoxX = x + 84;
        int iconBoxY = y + 12;

        VayuHUDUI.roundedRect(graphics, iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, 8, VayuHUDUI.withAlpha(0xD0111E30, alpha));
        VayuHUDUI.roundedOutline(graphics, iconBoxX, iconBoxY, iconBoxSize, iconBoxSize, 8, VayuHUDUI.withAlpha(0x4438BDF8, alpha));
        graphics.blit(RenderPipelines.GUI_TEXTURED, VayuHUDUI.icon(this.module), iconBoxX + 6, iconBoxY + 6, 0.0f, 0.0f, 24, 24, 24, 24, VayuHUDUI.withAlpha(-1, alpha));

        // 3. Module Title & Category Badge
        int textLeft = iconBoxX + iconBoxSize + 12;
        this.drawTitleText(graphics, this.module.getDisplayName(), textLeft, y + 13, VayuHUDUI.withAlpha(0xFFFFFFFF, alpha));

        String categoryTag = "[ " + this.module.getCategory().name() + " ]";
        int titleWidth = this.titleTextWidth(this.module.getDisplayName());
        this.drawUiText(graphics, categoryTag, textLeft + titleWidth + 8, y + 16, VayuHUDUI.withAlpha(0xFF38BDF8, alpha));

        // 4. Interactive Header Module Toggle Switch
        boolean isEnabled = this.module.isEnabled();
        String toggleText = isEnabled ? "● ENABLED" : "○ DISABLED";
        int toggleColor = isEnabled ? 0xFF22C55E : 0xFF64748B;
        int toggleBg = isEnabled ? 0x3322C55E : 0x2264748B;
        int toggleBorder = isEnabled ? 0x8822C55E : 0x4464748B;

        int toggleTextW = this.uiTextWidth(toggleText);
        this.headerToggleW = toggleTextW + 16;
        this.headerToggleH = 22;
        this.headerToggleX = x + w - 16 - this.headerToggleW;
        this.headerToggleY = y + 14;

        boolean toggleHover = isHovered(this.headerToggleX, this.headerToggleY, this.headerToggleW, this.headerToggleH, mouseX, mouseY);
        if (toggleHover) {
            toggleBg = isEnabled ? 0x5522C55E : 0x4464748B;
        }

        VayuHUDUI.roundedRect(graphics, this.headerToggleX, this.headerToggleY, this.headerToggleW, this.headerToggleH, 5, VayuHUDUI.withAlpha(toggleBg, alpha));
        VayuHUDUI.roundedOutline(graphics, this.headerToggleX, this.headerToggleY, this.headerToggleW, this.headerToggleH, 5, VayuHUDUI.withAlpha(toggleBorder, alpha));
        this.drawUiText(graphics, toggleText, this.headerToggleX + 8, this.headerToggleY + 6, VayuHUDUI.withAlpha(toggleColor, alpha));

        // 5. HUD Position Shortcut Button (if HUD category)
        if (this.module.getCategory() == Category.HUD) {
            String hudBtnText = "🎯 POSITION";
            int hudTextW = this.uiTextWidth(hudBtnText);
            this.hudPosBtnW = hudTextW + 14;
            this.hudPosBtnH = 22;
            this.hudPosBtnX = this.headerToggleX - this.hudPosBtnW - 8;
            this.hudPosBtnY = y + 14;

            boolean hudHover = isHovered(this.hudPosBtnX, this.hudPosBtnY, this.hudPosBtnW, this.hudPosBtnH, mouseX, mouseY);
            VayuHUDUI.roundedRect(graphics, this.hudPosBtnX, this.hudPosBtnY, this.hudPosBtnW, this.hudPosBtnH, 5, 
                VayuHUDUI.withAlpha(hudHover ? 0xFF0284C7 : 0xD0111B2B, alpha));
            VayuHUDUI.roundedOutline(graphics, this.hudPosBtnX, this.hudPosBtnY, this.hudPosBtnW, this.hudPosBtnH, 5, 
                VayuHUDUI.withAlpha(hudHover ? 0xFF38BDF8 : 0x3338BDF8, alpha));
            this.drawUiText(graphics, hudBtnText, this.hudPosBtnX + 7, this.hudPosBtnY + 6, VayuHUDUI.withAlpha(hudHover ? 0xFFFFFFFF : 0xFF38BDF8, alpha));
        } else {
            this.hudPosBtnW = 0;
        }

        // 6. Subtitle Description
        String desc = this.module.getDescription();
        desc = this.fitBodyText(desc != null ? desc : "", w - 120);
        this.drawUiText(graphics, desc, textLeft, y + 36, VayuHUDUI.withAlpha(0xFF94A3B8, alpha));

        // Separator Line
        graphics.fill(x + 16, y + 96, x + w - 16, y + 97, VayuHUDUI.withAlpha(0x2A38BDF8, alpha));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha) {
        String hint = "ESC or ‹ Back to return • All changes applied & saved automatically ⚡";
        int hintWidth = this.uiTextWidth(hint);
        this.drawUiText(graphics, hint, x + (w - hintWidth) / 2, y + 10, VayuHUDUI.withAlpha(0xFF64748B, alpha));
    }

    private void drawSettingRowBackground(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY, int mouseX, int mouseY) {
        SettingRow row = this.rows.get(component);
        if (row == null) return;

        int cardX = component.getX() - 10;
        int cardY = adjustedY - 6;
        int cardW = component.getWidth() + 20;
        int cardH = this.rowHeight(row.controlHeight()) + 2;

        boolean hovered = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
        int bg = hovered ? 0xE80E1A2E : 0xD80A1220;
        int border = hovered ? 0x6638BDF8 : 0x2238BDF8;

        VayuHUDUI.roundedRect(graphics, cardX, cardY, cardW, cardH, 6, bg);
        VayuHUDUI.roundedOutline(graphics, cardX, cardY, cardW, cardH, 6, border);
    }

    private void drawSettingDescription(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY, int alpha) {
        SettingRow row = this.rows.get(component);
        if (row == null || row.description().isEmpty()) return;

        String description = this.fitBodyText(row.description(), component.getWidth() - 24);
        this.drawUiText(graphics, description, component.getX() + 12, adjustedY + row.controlHeight() + 8, VayuHUDUI.withAlpha(0xFF64748B, alpha));
    }

    private static boolean isHovered(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private String fitBodyText(String text, int maxWidth) {
        if (this.uiTextWidth(text) <= maxWidth) return text;
        String result = text;
        while (result.length() > 1 && this.uiTextWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void drawUiText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = VayuFonts.bodyScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, VayuFonts.body(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private int uiTextWidth(String text) {
        return Math.round((float)this.font.width((FormattedText)VayuFonts.body(text)) * VayuFonts.bodyScale());
    }

    private void drawTitleText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = VayuFonts.titleScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, VayuFonts.title(text), x, y, color, true);
        graphics.pose().popMatrix();
    }

    private int titleTextWidth(String text) {
        return Math.round((float)this.font.width((FormattedText)VayuFonts.title(text)) * VayuFonts.titleScale());
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        graphics.fill(x, y, x + w, y + h, VayuHUDUI.withAlpha(0x221E293B, alpha));
        double visibleRatio = (double)h / ((double)h + this.maxScroll);
        int thumbHeight = Math.max(24, (int)((double)h * visibleRatio));
        int thumbY = y + (int)(this.scrollOffset / this.maxScroll * (double)(h - thumbHeight));
        VayuHUDUI.roundedRect(graphics, x - 1, thumbY, w + 2, thumbHeight, 2, VayuHUDUI.withAlpha(0xFF38BDF8, alpha));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());

        // Back Button
        if (mouseX >= (double)this.backBtnX && mouseX <= (double)(this.backBtnX + this.backBtnW)
            && mouseY >= (double)this.backBtnY && mouseY <= (double)(this.backBtnY + this.backBtnH)) {
            this.onClose();
            return true;
        }

        // Header Toggle Button
        if (mouseX >= (double)this.headerToggleX && mouseX <= (double)(this.headerToggleX + this.headerToggleW)
            && mouseY >= (double)this.headerToggleY && mouseY <= (double)(this.headerToggleY + this.headerToggleH)) {
            this.module.toggle();
            this.saveAndUpdate();
            this.rebuildComponents();
            return true;
        }

        // HUD Position Button
        if (this.hudPosBtnW > 0 && mouseX >= (double)this.hudPosBtnX && mouseX <= (double)(this.hudPosBtnX + this.hudPosBtnW)
            && mouseY >= (double)this.hudPosBtnY && mouseY <= (double)(this.hudPosBtnY + this.hudPosBtnH)) {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new HudEditorScreen());
            }
            return true;
        }

        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl);
            component.setY(originalY);
            if (result) return true;
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            component.mouseReleased(pxEvent);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseDragged(pxEvent, DisplaySpace.mouseDelta(deltaX), DisplaySpace.mouseDelta(deltaY));
            component.setY(originalY);
            if (result) return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 28.0));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESC
            this.onClose();
            return true;
        }
        for (UIComponent component : this.components) {
            if (component.keyPressed(event)) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (UIComponent component : this.components) {
            if (component.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record SettingRow(String description, int controlHeight) {
    }
}
