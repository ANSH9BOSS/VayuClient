/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.vayuclient.hud.mixin.client;

import com.vayuclient.hud.accessor.EntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={EntityRenderState.class})
public class EntityRenderStateMixin
implements EntityRenderStateAccessor {
    @Unique
    private Entity vayuclient$entity;

    @Override
    public void vayuclient$setEntity(Entity entity) {
        this.vayuclient$entity = entity;
    }

    @Override
    public Entity vayuclient$getEntity() {
        return this.vayuclient$entity;
    }
}

