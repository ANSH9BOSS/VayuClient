package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.gui.DisplaySpace;
import com.vayuclient.hud.gui.VayuHUDUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "me.shedaniel.clothconfig2.gui.ClothConfigScreen",
        "me.shedaniel.clothconfig2.gui.AbstractConfigScreen"
}, remap = false)
public abstract class ClothConfigSkinMixin extends Screen {

    protected ClothConfigSkinMixin() {
        super(Component.literal("Config"));
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vayu$renderClothBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int w = DisplaySpace.width();
        int h = DisplaySpace.height();

        // 1. Dark Glass Backdrop
        graphics.fill(0, 0, w, h, 0xF2070D18);

        // 2. Top Header Accent Strip
        graphics.fill(0, 0, w, 28, 0xD00A111A);
        graphics.fill(0, 27, w, 28, 0x3338BDF8);

        // 3. Bottom Footer Accent Strip
        graphics.fill(0, h - 32, w, h, 0xD00A111A);
        graphics.fill(0, h - 32, w, h - 31, 0x3338BDF8);

        // Cancel vanilla dirt background rendering
        ci.cancel();
    }
}
