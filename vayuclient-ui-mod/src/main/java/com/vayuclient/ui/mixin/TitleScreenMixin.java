package com.vayuclient.ui.mixin;

import com.vayuclient.ui.VayuClientUI;
import com.vayuclient.ui.gui.VayuTitleScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
    "net.minecraft.class_442",
    "net.minecraft.client.gui.screens.TitleScreen",
    "net.minecraft.client.gui.screen.TitleScreen"
}, remap = false)
public class TitleScreenMixin {

    @Inject(method = {"init", "method_25426"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void onInit(CallbackInfo ci) {
        try {
            if (VayuClientUI.isEnabled() && !((Object)this instanceof VayuTitleScreen)) {
                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    client.setScreenAndShow(new VayuTitleScreen());
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            // Safe fallback to default title screen
        }
    }
}
