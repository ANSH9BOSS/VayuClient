/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.entity.player.PlayerSkin
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.appearance.PlayerAppearanceService;
import net.fastclient.hud.modules.impl.render.FastClientSkinsModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AbstractClientPlayer.class})
public abstract class AbstractClientPlayerMixin {
    @Inject(method={"getSkin"}, at={@At(value="RETURN")}, cancellable=true)
    private void fastclient$resolveAppearance(CallbackInfoReturnable<PlayerSkin> callback) {
        if (!FastClientSkinsModule.isActive()) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer)this;
        callback.setReturnValue((Object)PlayerAppearanceService.getInstance().resolve((PlayerSkin)callback.getReturnValue(), player.getGameProfile().name()));
    }
}

