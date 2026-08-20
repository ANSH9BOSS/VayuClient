/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
 *  net.minecraft.client.renderer.RenderPipelines
 *  net.minecraft.resources.Identifier
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.gui.DisplaySpace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Screen.class})
public class JoinMultiplayerScreenMixin {
    private static final String DOMAIN = "fastclient-hud";
    private static final int TEX_W = 510;
    private static final int TEX_H = 161;
    private static final float CHROME_SCALE = 1.25f;

    @Inject(method={"extractRenderState"}, at={@At(value="TAIL")})
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!(this instanceof JoinMultiplayerScreen)) {
            return;
        }
        int screenW = DisplaySpace.width();
        int screenH = DisplaySpace.height();
        float s = Math.min((float)screenW / 1920.0f, (float)screenH / 1080.0f);
        float chrome = s * 1.25f;
        DisplaySpace.push(graphics);
        int padding = (int)(20.0f * chrome);
        int logoW = (int)(120.0f * chrome);
        int logoH = (int)((float)logoW * 161.0f / 510.0f);
        int logoX = screenW - logoW - padding;
        int logoY = screenH - logoH - padding + (int)(15.0f * chrome);
        graphics.blit(RenderPipelines.GUI_TEXTURED, DisplaySpace.texture(Identifier.fromNamespaceAndPath((String)DOMAIN, (String)"textures/gui/title-fastclient-logo.png")), logoX, logoY, 0.0f, 0.0f, logoW, logoH, 510, 161, 510, 161);
        DisplaySpace.pop(graphics);
    }
}

