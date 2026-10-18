package com.randomperks.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 暴露 EntityRenderDispatcher 的渲染器表 —— 供 PigMorphRenderer 借用原版猪渲染器 */
@Mixin(EntityRenderDispatcher.class)
public interface RenderDispatcherAccessor {

    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?, ?>> randomperks$getRenderers();
}
