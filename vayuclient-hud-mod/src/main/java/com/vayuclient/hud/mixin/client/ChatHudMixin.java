/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.ChatComponent
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.ModifyVariable
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.core.ModuleManager;
import com.vayuclient.hud.modules.impl.hud.ChatTimestamps;
import com.vayuclient.hud.modules.impl.utility.AutoGG;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={ChatComponent.class})
public class ChatHudMixin {
    @ModifyVariable(method={"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"}, at=@At(value="HEAD"), argsOnly=true, ordinal=0)
    private Component modifyMessage(Component message) {
        ChatTimestamps timestamps;
        AutoGG autoGG;
        ModuleManager mm = ModuleManager.getInstance();
        if (mm != null && (autoGG = mm.getModule(AutoGG.class)) != null && autoGG.isEnabled()) {
            autoGG.onChatReceived(message.getString());
        }
        if ((timestamps = ChatTimestamps.getInstance()) == null || !timestamps.isEnabled()) {
            return message;
        }
        MutableComponent timestampComponent = Component.literal((String)timestamps.getFormattedTimestamp());
        return timestampComponent.append(message);
    }
}

