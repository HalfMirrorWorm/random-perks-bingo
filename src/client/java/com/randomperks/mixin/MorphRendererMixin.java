package com.randomperks.mixin;

import com.randomperks.client.render.PigMorphRenderer;
import com.randomperks.game.MorphState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变群友" —— 处于变猪状态的实体改用 {@link PigMorphRenderer} 渲染。
 *
 * 只换渲染器, 不碰实体本身: 位置、动画、名字标签、碰撞箱提示等仍然走原版流程。
 */
@Mixin(EntityRenderDispatcher.class)
public class MorphRendererMixin {

    // 必须写完整描述符: getRenderer 有两个重载 (收实体 / 收渲染状态), 只写方法名会同时匹配,
    // 另一个的签名对不上 → 注入失败 → defaultRequire=1 直接崩启动
    @Inject(
            method = "getRenderer(Lnet/minecraft/entity/Entity;)Lnet/minecraft/client/render/entity/EntityRenderer;",
            at = @At("HEAD"),
            cancellable = true)
    private void randomperks$pigRenderer(Entity entity, CallbackInfoReturnable<EntityRenderer> cir) {
        if (!MorphState.isMorphed(entity.getId())) return;

        PigMorphRenderer renderer = PigMorphRenderer.get();
        if (renderer != null) {
            cir.setReturnValue(renderer);
        }
    }
}
