/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.multiplayer.ClientPacketListener
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 */
package com.vayuclient.hud.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={ClientPacketListener.class})
public class ClientPacketListenerMixin {
    @ModifyVariable(method={"sendChat(Ljava/lang/String;)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private String vayuclient$prependEquippedChatTags(String message) {
        return message;
    }
}

