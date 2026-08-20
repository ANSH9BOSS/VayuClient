/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.ShaderManager
 *  net.minecraft.client.renderer.ShaderManager$CompilationCache
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package net.fastclient.hud.mixin.client;

import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={ShaderManager.class})
public interface ShaderManagerAccessor {
    @Accessor
    public ShaderManager.CompilationCache getCompilationCache();
}

