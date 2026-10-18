package com.randomperks.mixin;

import com.randomperks.client.BigSnowballState;
import com.randomperks.client.render.BigSnowballFlag;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.state.FlyingItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "雪球大作战" —— 把大雪球画大。
 *
 * 雪球在 1.21 走的是通用的 {@link FlyingItemEntityRenderer} (鸡蛋/末影珍珠共用),
 * 所以只缩放、不换渲染器; 是否要缩放由 updateRenderState 阶段从实体 id 查出来的标记决定。
 */
@Mixin(FlyingItemEntityRenderer.class)
public class BigSnowballRendererMixin {

    /** 放大倍数 */
    private static final float SCALE = 2.5f;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/FlyingItemEntityRenderState;F)V",
            at = @At("TAIL"))
    private void randomperks$markBig(
            Entity entity, FlyingItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ((BigSnowballFlag) (Object) state).randomperks$setBig(BigSnowballState.isBig(entity.getId()));
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/FlyingItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void randomperks$scaleUp(
            FlyingItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci) {
        if (((BigSnowballFlag) (Object) state).randomperks$isBig()) {
            matrices.push();
            matrices.scale(SCALE, SCALE, SCALE);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/FlyingItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void randomperks$scaleDown(
            FlyingItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, CallbackInfo ci) {
        if (((BigSnowballFlag) (Object) state).randomperks$isBig()) {
            matrices.pop();
        }
    }
}
