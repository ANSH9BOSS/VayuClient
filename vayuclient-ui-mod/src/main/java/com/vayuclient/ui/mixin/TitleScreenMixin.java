package com.vayuclient.ui.mixin;

import com.vayuclient.ui.VayuClientUI;
import com.vayuclient.ui.gui.VayuTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {TitleScreen.class})
public class TitleScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (VayuClientUI.isEnabled() && client != null && !((Object)this instanceof VayuTitleScreen)) {
            client.setScreenAndShow(new VayuTitleScreen());
            ci.cancel();
        }
    }
}
