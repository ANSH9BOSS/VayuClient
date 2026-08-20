/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.CharacterEvent
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.network.chat.FormattedText
 */
package net.fastclient.hud.gui.screens;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import net.fastclient.hud.FastClientHUDClient;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientFonts;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.gui.components.ColorPicker;
import net.fastclient.hud.gui.components.Dropdown;
import net.fastclient.hud.gui.components.KeybindButton;
import net.fastclient.hud.gui.components.Slider;
import net.fastclient.hud.gui.components.TextInput;
import net.fastclient.hud.gui.components.ToggleButton;
import net.fastclient.hud.gui.components.UIComponent;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.fastclient.hud.modules.settings.KeybindSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.fastclient.hud.modules.settings.Setting;
import net.fastclient.hud.modules.settings.TextSetting;
import net.fastclient.hud.render.AnimationUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FormattedText;

public class ModuleConfigScreen
extends Screen {
    private final Module module;
    private final Screen parent;
    private final List<UIComponent> components = new ArrayList<UIComponent>();
    private final IdentityHashMap<UIComponent, SettingRow> rows = new IdentityHashMap();
    private double scrollOffset = 0.0;
    private double targetScrollOffset = 0.0;
    private double maxScroll = 0.0;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private AnimationUtils.Animation openAnimation;
    private long lastUpdate = System.currentTimeMillis();
    private int backBtnX;
    private int backBtnY;
    private int backBtnSize = 30;

    public ModuleConfigScreen(Module module, Screen parent) {
        super(FastClientFonts.moduleName(module.getDisplayName() + " Settings"));
        this.module = module;
        this.parent = parent;
    }

    protected void init() {
        this.components.clear();
        this.rows.clear();
        int displayWidth = DisplaySpace.width();
        int displayHeight = DisplaySpace.height();
        this.panelWidth = Math.max(1, Math.min(displayWidth - 48, (int)((double)displayWidth * 0.48)));
        this.panelHeight = Math.max(1, Math.min(displayHeight - 72, (int)((double)displayHeight * 0.72)));
        this.panelX = (displayWidth - this.panelWidth) / 2;
        this.panelY = (displayHeight - this.panelHeight) / 2;
        if (this.openAnimation == null) {
            this.openAnimation = new AnimationUtils.Animation(0.0f, 200L);
            this.openAnimation.setEasing(AnimationUtils::easeOutCubic);
        }
        this.openAnimation.animateTo(1.0f);
        int settingWidth = this.panelWidth - 72;
        int centerX = this.panelX + 36;
        int y = this.panelY + 112;
        int rowGap = 14;
        KeybindButton moduleKeybind = new KeybindButton(centerX, y, settingWidth, 28, "toggle_keybind", this.module.getKeyBinding(), this.module.getKeyModifiers(), (keyCode, modifiers) -> {
            this.module.setKeyBinding((int)keyCode, (int)modifiers);
            this.saveAndUpdate();
        });
        this.addRow(moduleKeybind, this.module.getName().equals("Waypoints") ? "Key used to open the waypoint manager while the module is enabled." : "Key used to enable or disable this module.", 28);
        y += this.rowHeight(28) + rowGap;
        for (Setting<?> setting : this.module.getSettings()) {
            if (!setting.isVisible()) continue;
            UIComponent component = null;
            int controlHeight = 28;
            if (setting instanceof BooleanSetting) {
                BooleanSetting bs = (BooleanSetting)setting;
                component = new ToggleButton(centerX, y, settingWidth, 28, bs.getName(), (Boolean)bs.getValue(), v -> {
                    bs.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof NumberSetting) {
                NumberSetting ns = (NumberSetting)setting;
                controlHeight = 52;
                component = new Slider(centerX, y, settingWidth, controlHeight, ns.getName(), (Double)ns.getValue(), ns.getMin(), ns.getMax(), ns.getStep(), v -> {
                    ns.setValue((Double)v);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof ModeSetting) {
                ModeSetting ms = (ModeSetting)setting;
                String[] modes = ms.getModes();
                component = new Dropdown(centerX, y, settingWidth, 28, ms.getName(), modes, (String)ms.getValue(), v -> {
                    ms.setValue(v);
                    this.saveAndUpdate();
                    this.rebuildComponents();
                });
            } else if (setting instanceof ColorSetting) {
                ColorSetting cs = (ColorSetting)setting;
                component = new ColorPicker(centerX, y, settingWidth, 28, cs.getName(), (Color)cs.getValue(), color -> {
                    cs.setValue(color);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof TextSetting) {
                TextSetting ts = (TextSetting)setting;
                component = new TextInput(centerX, y, settingWidth, 28, ts.getName(), (String)ts.getValue(), value -> {
                    ts.setValue(value);
                    this.saveAndUpdate();
                });
            } else if (setting instanceof KeybindSetting) {
                KeybindSetting ks = (KeybindSetting)setting;
                component = new KeybindButton(centerX, y, settingWidth, 28, ks.getName(), ks.getKey(), value -> {
                    ks.setKey((int)value);
                    this.saveAndUpdate();
                });
            }
            if (component == null) continue;
            this.addRow(component, setting.getDescription(), controlHeight);
            y += this.rowHeight(controlHeight) + rowGap;
        }
        this.maxScroll = Math.max(0, y - (this.panelY + this.panelHeight - 40));
    }

    private void addRow(UIComponent component, String description, int controlHeight) {
        this.components.add(component);
        this.rows.put(component, new SettingRow(description == null ? "" : description, controlHeight));
    }

    private int rowHeight(int controlHeight) {
        return controlHeight + 36;
    }

    private void rebuildComponents() {
        double savedScroll = this.targetScrollOffset;
        this.init();
        this.targetScrollOffset = Math.min(savedScroll, this.maxScroll);
    }

    private void saveAndUpdate() {
        FastClientHUDClient.getInstance().getModuleManager().saveConfig();
    }

    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, DisplaySpace.width(), DisplaySpace.height(), FastClientUI.withAlpha(-16777216, 170));
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean isExpandedOverlay;
        int adjustedY;
        int originalY;
        int pxMouseX = DisplaySpace.mouseX(mouseX);
        int pxMouseY = DisplaySpace.mouseY(mouseY);
        DisplaySpace.push(graphics);
        this.extractBackground(graphics, pxMouseX, pxMouseY, delta);
        long now = System.currentTimeMillis();
        float dt = (float)(now - this.lastUpdate) / 1000.0f;
        this.lastUpdate = now;
        this.scrollOffset = AnimationUtils.smoothDelta((float)this.scrollOffset, (float)this.targetScrollOffset, 0.3f, dt * 60.0f);
        float animProgress = this.openAnimation.getValue();
        int animatedPanelY = (int)((float)this.panelY + (1.0f - animProgress) * 30.0f);
        int alpha = (int)(animProgress * 255.0f);
        this.drawPanelBackground(graphics, this.panelX, animatedPanelY, this.panelWidth, this.panelHeight, alpha);
        this.drawHeader(graphics, this.panelX, animatedPanelY, this.panelWidth, alpha);
        int contentY = animatedPanelY + 98;
        int contentHeight = this.panelHeight - 134;
        DisplaySpace.enableScissor(graphics, this.panelX + 12, contentY, this.panelX + this.panelWidth - 12, contentY + contentHeight);
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            originalY = component.getY();
            adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);
            boolean bl = isExpandedOverlay = component instanceof Dropdown && ((Dropdown)component).getHeight() > 32 || component instanceof ColorPicker && ((ColorPicker)component).isExpanded();
            if (!isExpandedOverlay && adjustedY + component.getHeight() > contentY - 10 && adjustedY < contentY + contentHeight + 10) {
                this.drawSettingRowBackground(graphics, component, adjustedY);
                component.render(graphics, pxMouseX, pxMouseY, delta);
                this.drawSettingDescription(graphics, component, adjustedY, alpha);
            }
            component.setY(originalY);
        }
        DisplaySpace.disableScissor(graphics);
        for (UIComponent component : this.components) {
            if (!component.isVisible()) continue;
            originalY = component.getY();
            adjustedY = originalY - (int)this.scrollOffset + (animatedPanelY - this.panelY);
            component.setY(adjustedY);
            boolean bl = isExpandedOverlay = component instanceof Dropdown && ((Dropdown)component).getHeight() > 32 || component instanceof ColorPicker && ((ColorPicker)component).isExpanded();
            if (isExpandedOverlay) {
                component.render(graphics, pxMouseX, pxMouseY, delta);
            }
            component.setY(originalY);
        }
        if (this.maxScroll > 0.0) {
            this.renderScrollbar(graphics, this.panelX + this.panelWidth - 11, contentY, 4, contentHeight, alpha);
        }
        this.drawFooter(graphics, this.panelX, animatedPanelY + this.panelHeight - 32, this.panelWidth, alpha);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        DisplaySpace.pop(graphics);
    }

    private void drawPanelBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        FastClientUI.roundedRect(graphics, x, y, w, h, 7, FastClientUI.withAlpha(-234156528, alpha));
        FastClientUI.outline(graphics, x, y, w, h, FastClientUI.withAlpha(1143616571, alpha));
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha) {
        int statusColor;
        String status;
        this.backBtnX = x + 14;
        this.backBtnY = y + 18;
        FastClientUI.roundedRect(graphics, this.backBtnX, this.backBtnY, this.backBtnSize, this.backBtnSize, 5, FastClientUI.withAlpha(-435153640, alpha));
        String backLabel = "\u2190";
        this.drawUiText(graphics, backLabel, this.backBtnX + (this.backBtnSize - this.uiTextWidth(backLabel)) / 2, this.backBtnY + (this.backBtnSize - this.uiLineHeight()) / 2 + 1, FastClientUI.withAlpha(-7303024, alpha));
        int iconSize = 32;
        int iconX = x + 56;
        int iconY = y + 17;
        FastClientUI.roundedRect(graphics, iconX, iconY, iconSize, iconSize, 5, FastClientUI.withAlpha(-435153640, alpha));
        graphics.blit(RenderPipelines.GUI_TEXTURED, FastClientUI.icon(this.module), iconX + 5, iconY + 5, 0.0f, 0.0f, 22, 22, 22, 22, FastClientUI.withAlpha(-1, alpha));
        if (this.module.isHotkeyOnly()) {
            boolean hasKey = this.module.getKeyBinding() != 0;
            int dotColor = hasKey ? -15243738 : -1854655;
            status = hasKey ? "\u2713 Hotkey Set" : "\u26a0 Set a hotkey to use";
            statusColor = dotColor;
        } else {
            int dotColor = this.module.isEnabled() ? -15243738 : -9934744;
            status = this.module.isEnabled() ? "\u2713 Enabled" : "\u25cb Disabled";
            statusColor = this.module.isEnabled() ? -15243738 : -7303024;
        }
        graphics.fill(x, y, x + w, y + 2, FastClientUI.withAlpha(this.module.isEnabled() ? -39373 : 1143616571, alpha));
        this.drawTitleText(graphics, this.module.getDisplayName(), x + 100, y + 14, FastClientUI.withAlpha(-723724, alpha));
        this.drawUiText(graphics, status, x + 100, y + 39, FastClientUI.withAlpha(statusColor, alpha));
        String desc = this.module.getDescription();
        desc = this.fitBodyText(desc != null ? desc : "", w - 116);
        this.drawUiText(graphics, desc, x + 100, y + 64, FastClientUI.withAlpha(-7303024, alpha));
        graphics.fill(x + 14, y + 92, x + w - 14, y + 93, FastClientUI.withAlpha(1143616571, alpha));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int x, int y, int w, int alpha) {
        String hint = "ESC to go back - changes save automatically";
        int hintWidth = this.uiTextWidth(hint);
        this.drawUiText(graphics, hint, x + (w - hintWidth) / 2, y + 8, FastClientUI.withAlpha(-7303024, alpha));
    }

    private void drawSettingRowBackground(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY) {
        SettingRow row = this.rows.get(component);
        if (row == null) {
            return;
        }
        FastClientUI.roundedRect(graphics, component.getX() - 10, adjustedY - 6, component.getWidth() + 20, this.rowHeight(row.controlHeight()) + 3, 5, FastClientUI.withAlpha(-653586413, 150));
        FastClientUI.outline(graphics, component.getX() - 10, adjustedY - 6, component.getWidth() + 20, this.rowHeight(row.controlHeight()) + 3, FastClientUI.withAlpha(1143616571, 130));
    }

    private void drawSettingDescription(GuiGraphicsExtractor graphics, UIComponent component, int adjustedY, int alpha) {
        SettingRow row = this.rows.get(component);
        if (row == null || row.description().isEmpty()) {
            return;
        }
        String description = this.fitBodyText(row.description(), component.getWidth() - 24);
        this.drawUiText(graphics, description, component.getX() + 12, adjustedY + row.controlHeight() + 10, FastClientUI.withAlpha(-9934744, alpha));
    }

    private String fitBodyText(String text, int maxWidth) {
        if (this.uiTextWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && this.uiTextWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private void drawUiText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = FastClientFonts.bodyScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, FastClientFonts.body(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private int uiTextWidth(String text) {
        return Math.round((float)this.font.width((FormattedText)FastClientFonts.body(text)) * FastClientFonts.bodyScale());
    }

    private int uiLineHeight() {
        Objects.requireNonNull(this.font);
        return Math.round(9.0f * FastClientFonts.bodyScale());
    }

    private void drawTitleText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        float scale = FastClientFonts.titleScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        graphics.text(this.font, FastClientFonts.title(text), x, y, color, false);
        graphics.pose().popMatrix();
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int alpha) {
        graphics.fill(x, y, x + w, y + h, FastClientUI.withAlpha(-435153640, 90));
        double visibleRatio = (double)h / ((double)h + this.maxScroll);
        int thumbHeight = Math.max(20, (int)((double)h * visibleRatio));
        int thumbY = y + (int)(this.scrollOffset / this.maxScroll * (double)(h - thumbHeight));
        graphics.fill(x, thumbY, x + w, thumbY + thumbHeight, FastClientUI.withAlpha(-7303024, alpha));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = DisplaySpace.mouseX(event.x());
        double mouseY = DisplaySpace.mouseY(event.y());
        if (mouseX >= (double)this.backBtnX && mouseX <= (double)(this.backBtnX + this.backBtnSize) && mouseY >= (double)this.backBtnY && mouseY <= (double)(this.backBtnY + this.backBtnSize)) {
            this.onClose();
            return true;
        }
        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseClicked(new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), bl);
            component.setY(originalY);
            if (!result) continue;
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            component.mouseReleased(pxEvent);
        }
        return super.mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        MouseButtonEvent pxEvent = new MouseButtonEvent((double)DisplaySpace.mouseX(event.x()), (double)DisplaySpace.mouseY(event.y()), event.buttonInfo());
        for (UIComponent component : this.components) {
            int originalY = component.getY();
            component.setY(originalY - (int)this.scrollOffset);
            boolean result = component.mouseDragged(pxEvent, DisplaySpace.mouseDelta(deltaX), DisplaySpace.mouseDelta(deltaY));
            component.setY(originalY);
            if (!result) continue;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        this.targetScrollOffset = Math.max(0.0, Math.min(this.maxScroll, this.targetScrollOffset - vertAmount * 25.0));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 344) {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(null);
            }
            return true;
        }
        for (UIComponent component : this.components) {
            if (!component.keyPressed(event)) continue;
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        for (UIComponent component : this.components) {
            if (!component.charTyped(event)) continue;
            return true;
        }
        return super.charTyped(event);
    }

    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private record SettingRow(String description, int controlHeight) {
    }
}

