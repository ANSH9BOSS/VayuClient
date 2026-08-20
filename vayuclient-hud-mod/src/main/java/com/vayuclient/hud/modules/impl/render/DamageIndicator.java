package com.vayuclient.hud.modules.impl.render;

import com.vayuclient.hud.modules.Category;
import com.vayuclient.hud.modules.Module;
import com.vayuclient.hud.modules.settings.BooleanSetting;
import com.vayuclient.hud.modules.settings.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

public class DamageIndicator extends Module {
    private final BooleanSetting showPlayers = this.register(new BooleanSetting("show_players", "Show for players", true));
    private final BooleanSetting showHostile = this.register(new BooleanSetting("show_hostile", "Show for hostile mobs", true));
    private final BooleanSetting showPassive = this.register(new BooleanSetting("show_passive", "Show for passive mobs", true));
    private final BooleanSetting showAbsorption = this.register(new BooleanSetting("show_absorption", "Show absorption hearts", true));
    private final ColorSetting heartColor = this.register(new ColorSetting("heart_color", "Heart Icon Color", 255, 60, 60));

    public DamageIndicator() {
        super("DamageIndicator", "Overhead heart health indicator above entities", Category.RENDER);
    }

    @Override
    public boolean isHudVisible() {
        return false;
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRender(GuiGraphicsExtractor graphics, float tickDelta) {
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

    public boolean shouldShowAbsorption() {
        return this.showAbsorption.isEnabled();
    }

    public boolean shouldShowFor(Entity entity) {
        if (entity == null || !this.isEnabled()) {
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
        return false;
    }

    public static float resolveHealth(LivingEntity entity) {
        if (entity == null) return 20.0f;
        float health = entity.getHealth();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            try {
                if (player.level() != null) {
                    net.minecraft.world.scores.Scoreboard sb = player.level().getScoreboard();
                    if (sb != null) {
                        net.minecraft.world.scores.Objective obj = sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.BELOW_NAME);
                        if (obj != null) {
                            net.minecraft.world.scores.ReadOnlyScoreInfo info = sb.getPlayerScoreInfo(player, obj);
                            if (info != null && info.value() > 0) {
                                float val = (float)info.value();
                                return val > 10.0f ? val : val * 2.0f;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        return health;
    }
}
