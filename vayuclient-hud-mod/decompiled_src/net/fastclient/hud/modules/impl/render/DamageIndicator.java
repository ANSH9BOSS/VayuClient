/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.decoration.ArmorStand
 *  net.minecraft.world.entity.monster.Monster
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.phys.EntityHitResult
 *  net.minecraft.world.phys.HitResult
 *  net.minecraft.world.phys.HitResult$Type
 */
package net.fastclient.hud.modules.impl.render;

import net.fastclient.hud.accessor.EntityRenderStateAccessor;
import net.fastclient.hud.gui.DisplaySpace;
import net.fastclient.hud.gui.FastClientUI;
import net.fastclient.hud.modules.Category;
import net.fastclient.hud.modules.Module;
import net.fastclient.hud.modules.settings.BooleanSetting;
import net.fastclient.hud.modules.settings.ColorSetting;
import net.fastclient.hud.modules.settings.ModeSetting;
import net.fastclient.hud.modules.settings.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class DamageIndicator
extends Module {
    private final ModeSetting displayMode = this.register(new ModeSetting("mode", "Display mode", "Crosshair", new String[]{"Crosshair", "World", "Panel", "Both"}));
    private final BooleanSetting showPlayers = this.register(new BooleanSetting("show_players", "Show for players", true));
    private final BooleanSetting showHostile = this.register(new BooleanSetting("show_hostile", "Show for hostile mobs", true));
    private final BooleanSetting showPassive = this.register(new BooleanSetting("show_passive", "Show for passive mobs", true));
    private final NumberSetting worldRange = this.register(new NumberSetting("world_range", "Range for world health bars", 24.0, 8.0, 64.0, 4.0));
    private final NumberSetting worldBarWidth = this.register(new NumberSetting("world_bar_width", "World health bar width", 1.5, 0.5, 3.0, 0.1));
    private final NumberSetting worldBarHeight = this.register(new NumberSetting("world_bar_height", "World health bar height", 0.1, 0.05, 0.3, 0.01));
    private final BooleanSetting worldShowName = this.register(new BooleanSetting("world_show_name", "Show name in world", false));
    private final BooleanSetting worldOnlyDamaged = this.register(new BooleanSetting("world_only_damaged", "Only show when damaged", false));
    private final BooleanSetting showName = this.register(new BooleanSetting("show_name", "Show entity name", true));
    private final BooleanSetting showHealthText = this.register(new BooleanSetting("health_text", "Show health as text", true));
    private final BooleanSetting showPercent = this.register(new BooleanSetting("show_percent", "Show as percentage", false));
    private final NumberSetting barWidth = this.register(new NumberSetting("bar_width", "Health bar width", 80.0, 40.0, 150.0, 5.0));
    private final NumberSetting verticalOffset = this.register(new NumberSetting("offset", "Vertical offset from crosshair", 50.0, 20.0, 100.0, 5.0));
    private final ColorSetting healthyColor = this.register(new ColorSetting("healthy_color", "Color when health is high", 100, 255, 100));
    private final ColorSetting damagedColor = this.register(new ColorSetting("damaged_color", "Color when health is low", 255, 80, 80));
    private static final int PANEL_WIDTH = 130;
    private static final int PANEL_HEIGHT = 38;
    private float displayedHealth = 0.0f;
    private float animatedHealthLag = 0.0f;
    private LivingEntity targetEntity = null;
    private long lastTargetTime = 0L;
    private float fadeAlpha = 0.0f;

    public DamageIndicator() {
        super("DamageIndicator", "Shows health of entities", Category.HUD);
        this.showPercent.visibleWhen(this.showHealthText::isEnabled);
        this.worldRange.visibleWhen(() -> ((String)this.displayMode.getValue()).equals("World"));
        this.worldBarWidth.visibleWhen(() -> ((String)this.displayMode.getValue()).equals("World"));
        this.worldBarHeight.visibleWhen(() -> ((String)this.displayMode.getValue()).equals("World"));
        this.worldShowName.visibleWhen(() -> ((String)this.displayMode.getValue()).equals("World"));
        this.worldOnlyDamaged.visibleWhen(() -> ((String)this.displayMode.getValue()).equals("World"));
        this.verticalOffset.visibleWhen(() -> {
            String m = (String)this.displayMode.getValue();
            return m.equals("Crosshair") || m.equals("Both");
        });
        this.barWidth.visibleWhen(() -> {
            String m = (String)this.displayMode.getValue();
            return m.equals("Crosshair") || m.equals("Both");
        });
        this.showName.visibleWhen(() -> !((String)this.displayMode.getValue()).equals("World"));
        this.showHealthText.visibleWhen(() -> !((String)this.displayMode.getValue()).equals("World"));
    }

    @Override
    public int getHudWidth() {
        return 130;
    }

    @Override
    public int getHudHeight() {
        return 38;
    }

    @Override
    public boolean isHudVisible() {
        String mode = (String)this.displayMode.getValue();
        return mode.equals("Panel") || mode.equals("Both");
    }

    @Override
    public void onTick() {
        EntityHitResult entityHit;
        Entity entity;
        if (!this.isInGame()) {
            this.targetEntity = null;
            return;
        }
        HitResult hitResult = DamageIndicator.mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY && (entity = (entityHit = (EntityHitResult)hitResult).getEntity()) instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (this.shouldShowFor(entity)) {
                if (this.targetEntity != living) {
                    this.displayedHealth = living.getHealth();
                    this.animatedHealthLag = living.getHealth();
                }
                this.targetEntity = living;
                this.lastTargetTime = System.currentTimeMillis();
                return;
            }
        }
        if (System.currentTimeMillis() - this.lastTargetTime > 500L) {
            this.targetEntity = null;
        }
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
        if (!this.isInGame() || DamageIndicator.mc.level == null || DamageIndicator.mc.player == null) {
            return;
        }
        String mode = (String)this.displayMode.getValue();
        if (mode.equals("World")) {
            return;
        }
        float targetAlpha = this.targetEntity != null && !this.targetEntity.isDeadOrDying() ? 1.0f : 0.0f;
        this.fadeAlpha += (targetAlpha - this.fadeAlpha) * 0.3f;
        if (this.fadeAlpha < 0.01f) {
            return;
        }
        if (this.targetEntity != null) {
            float currentHealth = this.targetEntity.getHealth();
            this.displayedHealth += (currentHealth - this.displayedHealth) * 0.5f;
            this.animatedHealthLag += (currentHealth - this.animatedHealthLag) * 0.1f;
        }
        if (mode.equals("Crosshair") || mode.equals("Both")) {
            this.renderCrosshairIndicator(graphics);
        }
        if (mode.equals("Panel") || mode.equals("Both")) {
            this.renderPanel(graphics);
        }
    }

    private void renderCrosshairIndicator(GuiGraphicsExtractor graphics) {
        if (this.targetEntity == null) {
            return;
        }
        int screenWidth = DisplaySpace.width();
        int screenHeight = DisplaySpace.height();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        float maxHealth = this.targetEntity.getMaxHealth();
        float healthPercent = Math.min(1.0f, Math.max(0.0f, this.displayedHealth / maxHealth));
        float lagPercent = Math.min(1.0f, Math.max(0.0f, this.animatedHealthLag / maxHealth));
        int barW = this.barWidth.getIntValue();
        int barH = 4;
        int yOffset = this.verticalOffset.getIntValue();
        int alpha = (int)(this.fadeAlpha * 255.0f);
        int bgAlpha = (int)(this.fadeAlpha * 200.0f);
        int indicatorX = centerX - barW / 2;
        int indicatorY = centerY - yOffset;
        if (this.showName.isEnabled()) {
            String name = this.targetEntity.getDisplayName().getString();
            int nameWidth = DamageIndicator.mc.font.width(name);
            int nameX = centerX - nameWidth / 2;
            int nameY = indicatorY - 12;
            graphics.text(DamageIndicator.mc.font, name, nameX, nameY, alpha << 24 | 0xFFFFFF, true);
            indicatorY += 2;
        }
        int barX = indicatorX;
        int barY = indicatorY;
        int bgColor = bgAlpha << 24 | 0x101317;
        FastClientUI.roundedRect(graphics, barX - 2, barY - 2, barW + 4, barH + 4, 2, (int)(this.fadeAlpha * 120.0f) << 24 | 0x101317);
        FastClientUI.roundedRect(graphics, barX, barY, barW, barH, 2, bgColor);
        if (lagPercent > healthPercent) {
            int lagWidth = (int)((float)barW * lagPercent);
            int damageColor = alpha << 24 | 0xFF3333;
            graphics.fill(barX, barY, barX + lagWidth, barY + barH, damageColor);
        }
        int healthColor = this.interpolateColor(healthPercent);
        int filledWidth = Math.max(0, (int)((float)barW * healthPercent));
        if (filledWidth > 0) {
            graphics.fill(barX, barY, barX + filledWidth, barY + barH, alpha << 24 | healthColor);
            int highlight = this.brightenColor(healthColor, 1.4f);
            graphics.fill(barX, barY, barX + filledWidth, barY + 1, alpha << 24 | highlight);
        }
        int borderAlpha = (int)(this.fadeAlpha * 120.0f);
        FastClientUI.outline(graphics, barX - 2, barY - 2, barW + 4, barH + 4, FastClientUI.withAlpha(1725422816, borderAlpha));
        if (this.showHealthText.isEnabled()) {
            String healthText = this.formatHealth(this.displayedHealth, maxHealth, healthPercent);
            int textWidth = DamageIndicator.mc.font.width(healthText);
            int textX = centerX - textWidth / 2;
            int textY = barY + barH + 3;
            graphics.text(DamageIndicator.mc.font, healthText, textX, textY, alpha << 24 | 0xCCCCCC, true);
        }
    }

    private void renderPanel(GuiGraphicsExtractor graphics) {
        if (this.targetEntity == null) {
            return;
        }
        float maxHealth = this.targetEntity.getMaxHealth();
        float healthPercent = Math.min(1.0f, Math.max(0.0f, this.displayedHealth / maxHealth));
        int healthColor = this.interpolateColor(healthPercent);
        double distance = DamageIndicator.mc.player.distanceTo((Entity)this.targetEntity);
        int x = this.getHudX();
        int y = this.getHudY();
        float scale = this.getHudScale();
        int panelW = 130;
        int panelH = 38;
        int padding = 6;
        int alpha = (int)(this.fadeAlpha * 240.0f);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float)x, (float)y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate((float)(-x), (float)(-y));
        FastClientUI.hudPanel(graphics, x, y, panelW, panelH);
        graphics.fill(x, y, x + 2, y + panelH, alpha << 24 | healthColor);
        int contentX = x + padding + 2;
        int contentY = y + padding;
        int barW = panelW - padding * 2 - 2;
        Object name = this.targetEntity.getDisplayName().getString();
        if (((String)name).length() > 14) {
            name = ((String)name).substring(0, 14) + "...";
        }
        String distText = String.format("%.1fm", distance);
        graphics.text(DamageIndicator.mc.font, (String)name, contentX, contentY, alpha << 24 | 0xFFFFFF, false);
        int distWidth = DamageIndicator.mc.font.width(distText);
        graphics.text(DamageIndicator.mc.font, distText, x + panelW - padding - distWidth, contentY, alpha << 24 | 0x888888, false);
        int barH = 4;
        FastClientUI.roundedRect(graphics, contentX, contentY += 11, barW, barH, 2, -1441128412);
        int filledW = Math.max(0, (int)((float)barW * healthPercent));
        if (filledW > 0) {
            graphics.fill(contentX, contentY, contentX + filledW, contentY + barH, alpha << 24 | healthColor);
            graphics.fill(contentX, contentY, contentX + filledW, contentY + 1, alpha << 24 | this.brightenColor(healthColor, 1.3f));
        }
        String healthStr = this.formatHealth(this.displayedHealth, maxHealth, healthPercent);
        graphics.text(DamageIndicator.mc.font, healthStr, contentX, contentY += 7, alpha << 24 | healthColor, false);
        graphics.pose().popMatrix();
    }

    public boolean isWorldMode() {
        return ((String)this.displayMode.getValue()).equals("World");
    }

    public double getWorldRange() {
        return (Double)this.worldRange.getValue();
    }

    public boolean isOnlyDamaged() {
        return this.worldOnlyDamaged.isEnabled();
    }

    public float getWorldBarWidth() {
        return ((Double)this.worldBarWidth.getValue()).floatValue();
    }

    public float getWorldBarHeight() {
        return ((Double)this.worldBarHeight.getValue()).floatValue();
    }

    public boolean shouldShowWorldName() {
        return this.worldShowName.isEnabled();
    }

    public float getEntityHealth(EntityRenderState state) {
        EntityRenderStateAccessor accessor;
        Entity entity;
        if (state instanceof EntityRenderStateAccessor && (entity = (accessor = (EntityRenderStateAccessor)state).fastclient$getEntity()) instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (this.shouldShowFor(entity)) {
                return living.getHealth();
            }
        }
        return -1.0f;
    }

    public float getEntityMaxHealth(EntityRenderState state) {
        EntityRenderStateAccessor accessor;
        Entity entity;
        if (state instanceof EntityRenderStateAccessor && (entity = (accessor = (EntityRenderStateAccessor)state).fastclient$getEntity()) instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (this.shouldShowFor(entity)) {
                return living.getMaxHealth();
            }
        }
        return 0.0f;
    }

    public int getHealthColor(float healthPercent) {
        return this.interpolateColor(healthPercent);
    }

    public boolean shouldShowPlayers() {
        return this.showPlayers.isEnabled();
    }

    public boolean shouldShowHostile() {
        return this.showHostile.isEnabled();
    }

    public boolean shouldShowPassive() {
        return this.showPassive.isEnabled();
    }

    public boolean shouldRenderWorldHealthBar(LivingEntity entity) {
        if (!this.shouldShowFor((Entity)entity)) {
            return false;
        }
        if (entity.isDeadOrDying()) {
            return false;
        }
        return !this.worldOnlyDamaged.isEnabled() || !(entity.getHealth() >= entity.getMaxHealth());
    }

    private String formatHealth(float health, float maxHealth, float healthPercent) {
        if (this.showPercent.isEnabled()) {
            return String.format("%.0f%%", Float.valueOf(healthPercent * 100.0f));
        }
        if ((double)health == Math.floor(health) && (double)maxHealth == Math.floor(maxHealth)) {
            return String.format("%.0f / %.0f \u2764", Float.valueOf(health), Float.valueOf(maxHealth));
        }
        return String.format("%.1f / %.0f \u2764", Float.valueOf(health), Float.valueOf(maxHealth));
    }

    private boolean shouldShowFor(Entity entity) {
        if (entity instanceof ArmorStand) {
            return false;
        }
        if (entity == DamageIndicator.mc.player) {
            return false;
        }
        if (entity instanceof Player) {
            return this.showPlayers.isEnabled();
        }
        if (entity instanceof Monster) {
            return this.showHostile.isEnabled();
        }
        if (entity instanceof Animal) {
            return this.showPassive.isEnabled();
        }
        return true;
    }

    private int interpolateColor(float percent) {
        int r1 = this.damagedColor.getRed();
        int g1 = this.damagedColor.getGreen();
        int b1 = this.damagedColor.getBlue();
        int r2 = this.healthyColor.getRed();
        int g2 = this.healthyColor.getGreen();
        int b2 = this.healthyColor.getBlue();
        float t = percent * percent * (3.0f - 2.0f * percent);
        int r = (int)((float)r1 + (float)(r2 - r1) * t);
        int g = (int)((float)g1 + (float)(g2 - g1) * t);
        int b = (int)((float)b1 + (float)(b2 - b1) * t);
        return r << 16 | g << 8 | b;
    }

    private int brightenColor(int color, float factor) {
        int r = Math.min(255, (int)((float)(color >> 16 & 0xFF) * factor));
        int g = Math.min(255, (int)((float)(color >> 8 & 0xFF) * factor));
        int b = Math.min(255, (int)((float)(color & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }
}

