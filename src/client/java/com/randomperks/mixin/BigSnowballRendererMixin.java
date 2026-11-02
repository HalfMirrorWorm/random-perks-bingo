package com.randomperks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.randomperks.client.BigSnowballState;
import com.randomperks.client.render.BigSnowballFlag;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "雪球大作战" —— 把大雪球画大。
 *
 * 雪球走通用的 {@link ThrownItemRenderer} (鸡蛋/末影珍珠共用),
 * 所以只缩放、不换渲染器; 是否要缩放由 extractRenderState 阶段从实体 id 查出来的标记决定。
 */
@Mixin(ThrownItemRenderer.class)
public class BigSnowballRendererMixin {

    /** 放大倍数 */
    private static final float SCALE = 2.5f;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/ThrownItemRenderState;F)V",
            at = @At("TAIL"))
    private void randomperks$markBig(
            Entity entity, ThrownItemRenderState state, float tickDelta, CallbackInfo ci) {
        ((BigSnowballFlag) (Object) state).randomperks$setBig(BigSnowballState.isBig(entity.getId()));
    }

    private static final String SUBMIT =
            "submit(Lnet/minecraft/client/renderer/entity/state/ThrownItemRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Inject(method = SUBMIT, at = @At("HEAD"))
    private void randomperks$scaleUp(
            ThrownItemRenderState state, PoseStack matrices, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        if (((BigSnowballFlag) (Object) state).randomperks$isBig()) {
            matrices.pushPose();
            matrices.scale(SCALE, SCALE, SCALE);
        }
    }

    @Inject(method = SUBMIT, at = @At("TAIL"))
    private void randomperks$scaleDown(
            ThrownItemRenderState state, PoseStack matrices, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        if (((BigSnowballFlag) (Object) state).randomperks$isBig()) {
            matrices.popPose();
        }
    }
}
