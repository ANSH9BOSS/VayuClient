/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.entity.state.EntityRenderState
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package net.fastclient.hud.mixin.client;

import net.fastclient.hud.accessor.EntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={EntityRenderState.class})
public class EntityRenderStateMixin
implements EntityRenderStateAccessor {
    @Unique
    private Entity fastclient$entity;

    @Override
    public void fastclient$setEntity(Entity entity) {
        this.fastclient$entity = entity;
    }

    @Override
    public Entity fastclient$getEntity() {
        return this.fastclient$entity;
    }
}

