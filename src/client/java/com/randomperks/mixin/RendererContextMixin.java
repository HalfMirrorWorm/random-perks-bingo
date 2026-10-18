package com.randomperks.mixin;

import com.randomperks.client.render.PigMorphRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捕获 {@link EntityRendererFactory.Context} 实例。
 *
 * 自定义渲染器必须走 EntityRenderer 的构造 (要 dispatcher 和 textRenderer), 而 Context
 * 只在客户端资源重载时由原版创建 —— 与其自己拼八个组件, 不如在它构造完时留一份引用。
 */
@Mixin(EntityRendererFactory.Context.class)
public class RendererContextMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void randomperks$captureContext(CallbackInfo ci) {
        PigMorphRenderer.captureContext((EntityRendererFactory.Context) (Object) this);
    }
}
