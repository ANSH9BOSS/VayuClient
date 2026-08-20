/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.HitResult$Type
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockOverlayModule
extends Module {
    private final ColorSetting overlayColor = this.register(new ColorSetting("color", "Overlay color", 255, 102, 51, 255));
    private final ColorSetting outlineColor = this.register(new ColorSetting("outline_color", "Outline color", 255, 138, 92, 255));
    private final NumberSetting fillTransparency = this.register(new NumberSetting("fill_transparency", "Fill opacity: 0 is invisible and 255 is fully opaque", 48.0, 0.0, 255.0, 1.0));
    private final NumberSetting outlineTransparency = this.register(new NumberSetting("outline_transparency", "Border opacity: 0 is invisible and 255 is fully opaque", 220.0, 0.0, 255.0, 1.0));
    private final BooleanSetting showOutline = this.register(new BooleanSetting("show_outline", "Show outline", true));
    private final BooleanSetting showFill = this.register(new BooleanSetting("show_fill", "Show filled overlay", true));
    private final NumberSetting outlineWidth = this.register(new NumberSetting("outline_width", "Outline thickness", 2.0, 1.0, 5.0, 0.5));
    private final NumberSetting expandAmount = this.register(new NumberSetting("expand", "Expand overlay (prevents z-fighting)", 0.002, 0.0, 0.01, 0.001));
    private final BooleanSetting showHud = this.register(new BooleanSetting("show_hud", "Show block info HUD", true));
    private final BooleanSetting showIcon = this.register(new BooleanSetting("show_icon", "Show block icon", true));
    private final BooleanSetting showBlockName = this.register(new BooleanSetting("show_name", "Show block name", true));
    private final BooleanSetting showModName = this.register(new BooleanSetting("show_mod", "Show mod/namespace", true));
    private final BooleanSetting showCoords = this.register(new BooleanSetting("show_coords", "Show block coordinates", false));
    private final BooleanSetting hudBackground = this.register(new BooleanSetting("hud_background", "Show HUD background", true));
    private final ColorSetting hudBgColor = this.register(new ColorSetting("hud_bg_color", "HUD background color", 0, 0, 0, 180));
    private final NumberSetting hudBgOpacity = this.register(new NumberSetting("hud_bg_opacity", "HUD background opacity", 128.0, 0.0, 255.0, 1.0));
    private final ColorSetting hudTextColor = this.register(new ColorSetting("hud_text_color", "HUD text color", 255, 255, 255, 255));
    private final ColorSetting hudModColor = this.register(new ColorSetting("hud_mod_color", "Mod name color", 100, 100, 255, 255));
    private final ModeSetting hudPosition = this.register(new ModeSetting("hud_position", "HUD position", "Custom", new String[]{"Custom", "Top Center", "Top Left", "Top Right", "Bottom Center", "Bottom Left", "Bottom Right"}));
    private final NumberSetting hudOffsetX = this.register(new NumberSetting("hud_offset_x", "HUD X offset", 0.0, -500.0, 500.0, 1.0));
    private final NumberSetting hudOffsetY = this.register(new NumberSetting("hud_offset_y", "HUD Y offset", 50.0, -500.0, 500.0, 1.0));
    private String cachedBlockName = "";
    private String cachedModName = "";
    private BlockPos cachedBlockPos = null;
    private ItemStack cachedItemStack = ItemStack.EMPTY;

    public BlockOverlayModule() {
        super("Block Overlay", "Highlights the block you're looking at with optional WAILA-like HUD", Category.RENDER);
        this.fillTransparency.visibleWhen(this.showFill::isEnabled);
        this.outlineTransparency.visibleWhen(this.showOutline::isEnabled);
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        int x;
        if (!this.isInGame() || !((Boolean)this.showHud.getValue()).booleanValue()) {
            return;
        }
        if (BlockOverlayModule.mc.hitResult == null || BlockOverlayModule.mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult blockHit = (BlockHitResult)BlockOverlayModule.mc.hitResult;
        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = BlockOverlayModule.mc.level.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (!blockPos.equals((Object)this.cachedBlockPos)) {
            this.cachedBlockPos = blockPos;
            this.cachedItemStack = new ItemStack((ItemLike)block.asItem());
            this.cachedBlockName = block.getName().getString();
            Identifier blockId = BuiltInRegistries.BLOCK.getKey((Object)block);
            String namespace = blockId.getNamespace();
            this.cachedModName = this.formatModName(namespace);
        }
        int padding = 6;
        int iconSize = (Boolean)this.showIcon.getValue() != false ? 16 : 0;
        int iconPadding = (Boolean)this.showIcon.getValue() != false ? 4 : 0;
        int textHeight = 0;
        int maxTextWidth = 0;
        if (((Boolean)this.showBlockName.getValue()).booleanValue()) {
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(this.cachedBlockName));
            textHeight += 10;
        }
        if (((Boolean)this.showModName.getValue()).booleanValue()) {
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(this.cachedModName));
            textHeight += 10;
        }
        if (((Boolean)this.showCoords.getValue()).booleanValue()) {
            String coordsText = String.format("X: %d  Y: %d  Z: %d", blockPos.getX(), blockPos.getY(), blockPos.getZ());
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(coordsText));
            textHeight += 10;
        }
        int hudWidth = padding * 2 + iconSize + iconPadding + maxTextWidth;
        int hudHeight = padding * 2 + Math.max(iconSize, textHeight);
        int screenWidth = DisplaySpace.width();
        int screenHeight = DisplaySpace.height();
        String pos = (String)this.hudPosition.getValue();
        int offsetX = ((Double)this.hudOffsetX.getValue()).intValue();
        int offsetY = ((Double)this.hudOffsetY.getValue()).intValue();
        int y = switch (pos) {
            case "Top Left" -> {
                x = 10 + offsetX;
                yield 10 + offsetY;
            }
            case "Top Right" -> {
                x = screenWidth - hudWidth - 10 + offsetX;
                yield 10 + offsetY;
            }
            case "Bottom Center" -> {
                x = (screenWidth - hudWidth) / 2 + offsetX;
                yield screenHeight - hudHeight - 60 + offsetY;
            }
            case "Bottom Left" -> {
                x = 10 + offsetX;
                yield screenHeight - hudHeight - 10 + offsetY;
            }
            case "Bottom Right" -> {
                x = screenWidth - hudWidth - 10 + offsetX;
                yield screenHeight - hudHeight - 10 + offsetY;
            }
            case "Custom" -> {
                x = this.getHudX();
                yield this.getHudY();
            }
            default -> {
                x = (screenWidth - hudWidth) / 2 + offsetX;
                yield 10 + offsetY;
            }
        };
        float scale = this.getHudScale();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        if (((Boolean)this.hudBackground.getValue()).booleanValue()) {
            int backgroundColor = this.hudBgOpacity.getIntValue() << 24 | this.hudBgColor.getRGB() & 0xFFFFFF;
            FastClientUI.roundedRect(graphics, x, y, hudWidth, hudHeight, 4, backgroundColor);
            FastClientUI.outline(graphics, x, y, hudWidth, hudHeight, 1154997472);
        }
        int contentX = x + padding;
        int contentY = y + padding;
        if (((Boolean)this.showIcon.getValue()).booleanValue() && !this.cachedItemStack.isEmpty()) {
            graphics.item(this.cachedItemStack, contentX, contentY);
            contentX += iconSize + iconPadding;
        }
        if (((Boolean)this.showIcon.getValue()).booleanValue() && textHeight < iconSize) {
            contentY += (iconSize - textHeight) / 2;
        }
        int textColor = this.hudTextColor.getRGB() | 0xFF000000;
        int modColor = this.hudModColor.getRGB() | 0xFF000000;
        if (((Boolean)this.showBlockName.getValue()).booleanValue()) {
            graphics.text(BlockOverlayModule.mc.font, this.cachedBlockName, contentX, contentY, textColor, true);
            contentY += 10;
        }
        if (((Boolean)this.showModName.getValue()).booleanValue()) {
            graphics.text(BlockOverlayModule.mc.font, this.cachedModName, contentX, contentY, modColor, true);
            contentY += 10;
        }
        if (((Boolean)this.showCoords.getValue()).booleanValue()) {
            String coordsText = String.format("X: %d  Y: %d  Z: %d", blockPos.getX(), blockPos.getY(), blockPos.getZ());
            graphics.text(BlockOverlayModule.mc.font, coordsText, contentX, contentY, -5592406, true);
        }
        graphics.pose().popMatrix();
    }

    private String formatModName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Minecraft";
        }
        String[] parts = namespace.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            formatted.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return formatted.toString().trim();
    }

    @Override
    public int getHudWidth() {
        String modName;
        int padding = 6;
        int iconSize = (Boolean)this.showIcon.getValue() != false ? 16 : 0;
        int iconPadding = (Boolean)this.showIcon.getValue() != false ? 4 : 0;
        int maxTextWidth = 0;
        String blockName = this.cachedBlockName.isEmpty() ? "Block Name" : this.cachedBlockName;
        String string = modName = this.cachedModName.isEmpty() ? "Minecraft" : this.cachedModName;
        if (((Boolean)this.showBlockName.getValue()).booleanValue()) {
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(blockName));
        }
        if (((Boolean)this.showModName.getValue()).booleanValue()) {
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(modName));
        }
        if (((Boolean)this.showCoords.getValue()).booleanValue()) {
            String coordsText = this.cachedBlockPos != null ? String.format("X: %d  Y: %d  Z: %d", this.cachedBlockPos.getX(), this.cachedBlockPos.getY(), this.cachedBlockPos.getZ()) : "X: 000  Y: 00  Z: 000";
            maxTextWidth = Math.max(maxTextWidth, BlockOverlayModule.mc.font.width(coordsText));
        }
        return padding * 2 + iconSize + iconPadding + maxTextWidth;
    }

    @Override
    public int getHudHeight() {
        int padding = 6;
        int iconSize = (Boolean)this.showIcon.getValue() != false ? 16 : 0;
        int textHeight = 0;
        if (((Boolean)this.showBlockName.getValue()).booleanValue()) {
            textHeight += 10;
        }
        if (((Boolean)this.showModName.getValue()).booleanValue()) {
            textHeight += 10;
        }
        if (((Boolean)this.showCoords.getValue()).booleanValue()) {
            textHeight += 10;
        }
        return padding * 2 + Math.max(iconSize, textHeight);
    }

    public int getOverlayColorARGB() {
        int rgb = this.overlayColor.getRGB() & 0xFFFFFF;
        int alpha = ((Double)this.fillTransparency.getValue()).intValue();
        return alpha << 24 | rgb;
    }

    public int getOutlineColorARGB() {
        int rgb = this.outlineColor.getRGB() & 0xFFFFFF;
        int alpha = ((Double)this.outlineTransparency.getValue()).intValue();
        return alpha << 24 | rgb;
    }

    public boolean shouldShowOutline() {
        return (Boolean)this.showOutline.getValue();
    }

    public boolean shouldShowFill() {
        return (Boolean)this.showFill.getValue();
    }

    public float getOutlineWidth() {
        return this.outlineWidth.getFloatValue();
    }

    public double getExpandAmount() {
        return (Double)this.expandAmount.getValue();
    }

    @Override
    public boolean isHudVisible() {
        return (Boolean)this.showHud.getValue();
    }

    public boolean isHudEnabled() {
        return (Boolean)this.showHud.getValue();
    }

    public boolean isCustomPosition() {
        return this.hudPosition.is("Custom");
    }

    @Override
    public void setHudPosition(int x, int y) {
        super.setHudPosition(x, y);
        if (!this.hudPosition.is("Custom")) {
            this.hudPosition.setValue("Custom");
        }
    }
}

