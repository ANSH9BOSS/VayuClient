/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.entity.EntityRenderer
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.FontDescription
 *  net.minecraft.network.chat.FontDescription$Resource
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.network.chat.Style
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.accessor.EntityRenderStateAccessor;
import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.render.DamageIndicator;
import com.vayuclient.hud.modules.impl.render.NametagIconModule;
import com.vayuclient.hud.network.VayuUserCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderer.class})
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    private static final String VAYU_ICON_CHAR = "\ue000";
    private static final Style VAYU_ICON_STYLE = Style.EMPTY.withFont((FontDescription)new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"vayuclient-hud", (String)"icon")));
    private static final Component VAYU_ICON_COMPONENT = Component.literal((String)VAYU_ICON_CHAR).withStyle(VAYU_ICON_STYLE);

    private static final String[] INT_CACHE = new String[101];
    static {
        for (int i = 0; i <= 100; i++) {
            INT_CACHE[i] = String.valueOf(i);
        }
    }

    private static String fastInt(int val) {
        if (val >= 0 && val <= 100) return INT_CACHE[val];
        return String.valueOf(val);
    }

    private static String formatHearts(float hearts) {
        int intVal = (int)hearts;
        if (hearts == (float)intVal) {
            return fastInt(intVal);
        }
        int tenths = (int)((hearts - intVal) * 10.0f);
        return fastInt(intVal) + "." + fastInt(Math.max(0, Math.min(9, tenths)));
    }

    @Inject(method={"extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"}, at={@At(value="TAIL")})
    private void onExtractRenderState(T entity, S state, float partialTicks, CallbackInfo ci) {
        if (state instanceof EntityRenderStateAccessor) {
            EntityRenderStateAccessor accessor = (EntityRenderStateAccessor)state;
            accessor.vayuclient$setEntity((Entity)entity);
        }

        if (entity instanceof Player) {
            boolean showDamage = false;
            DamageIndicator di = null;
            ModuleManager mm = ModuleManager.getInstance();
            if (mm != null) {
                di = mm.getModule(DamageIndicator.class);
                showDamage = di != null && di.isEnabled();
            }
            boolean showNametag = NametagIconModule.shouldShowAboveHead();

            // FAST EXIT: If neither feature is active, zero overhead per entity render
            if (!showDamage && !showNametag) {
                return;
            }

            Player player = (Player)entity;
            boolean isSelf = entity == Minecraft.getInstance().player;

            // 1. Overhead Health Indicator
            if (showDamage && di != null && di.shouldShowPlayers() && !isSelf && !player.isDeadOrDying()) {
                float health = DamageIndicator.resolveHealth(player);
                float absorption = player.getAbsorptionAmount();
                float healthHearts = Math.max(0.0f, health / 2.0f);
                float absHearts = Math.max(0.0f, absorption / 2.0f);

                String healthText = formatHearts(healthHearts);

                StringBuilder sb = new StringBuilder();
                sb.append(" §c\u2764 §f").append(healthText);
                if (absHearts > 0.05f && di.shouldShowAbsorption()) {
                    String absText = formatHearts(absHearts);
                    sb.append(" §7| §e\u2764 §e").append(absText);
                }

                Component healthTag = Component.literal(sb.toString());
                if (((EntityRenderState)state).nameTag != null) {
                    ((EntityRenderState)state).nameTag = Component.literal("")
                        .append(((EntityRenderState)state).nameTag)
                        .append(healthTag);
                } else {
                    ((EntityRenderState)state).nameTag = Component.literal("")
                        .append(player.getDisplayName())
                        .append(healthTag);
                }
            }

            // 2. Nametag Icon Badge
            if (showNametag) {
                String playerName = com.vayuclient.hud.utils.PlayerUtils.getProfileName(player.getGameProfile());
                boolean isVayu = isSelf || VayuUserCache.getInstance().isVayuClientUser(playerName);
                if (isVayu) {
                    if (((EntityRenderState)state).nameTag != null) {
                        ((EntityRenderState)state).nameTag = Component.literal("")
                            .append(VAYU_ICON_COMPONENT)
                            .append(" ")
                            .append(((EntityRenderState)state).nameTag);
                    } else if (!isSelf) {
                        ((EntityRenderState)state).nameTag = Component.literal("")
                            .append(VAYU_ICON_COMPONENT)
                            .append(" ")
                            .append(player.getDisplayName());
                    }
                }
            }
        }
    }
}

