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
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.accessor.EntityRenderStateAccessor;
import net.fastclient.hud.modules.impl.render.NametagIconModule;
import net.fastclient.hud.network.FastClientUserCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderer.class})
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    private static final String FC_ICON_CHAR = "\ue000";
    private static final Style FC_ICON_STYLE = Style.EMPTY.withFont((FontDescription)new FontDescription.Resource(Identifier.fromNamespaceAndPath((String)"fastclient-hud", (String)"icon")));

    @Inject(method={"extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"}, at={@At(value="TAIL")})
    private void onExtractRenderState(T entity, S state, float partialTicks, CallbackInfo ci) {
        if (state instanceof EntityRenderStateAccessor) {
            EntityRenderStateAccessor accessor = (EntityRenderStateAccessor)state;
            accessor.fastclient$setEntity((Entity)entity);
        }
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (!NametagIconModule.shouldShowAboveHead()) {
            return;
        }
        boolean isSelf = entity == Minecraft.getInstance().player;
        String name = player.getGameProfile().name();
        if (!isSelf && !FastClientUserCache.getInstance().isFastClientUser(name)) {
            return;
        }
        if (((EntityRenderState)state).nameTag == null) {
            return;
        }
        MutableComponent icon = Component.literal((String)FC_ICON_CHAR).withStyle(FC_ICON_STYLE);
        ((EntityRenderState)state).nameTag = Component.literal((String)"").append((Component)icon).append(" ").append(((EntityRenderState)state).nameTag);
    }
}

