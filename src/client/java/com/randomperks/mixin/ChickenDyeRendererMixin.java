package com.randomperks.mixin;

import com.randomperks.client.ChickenDyeState;
import com.randomperks.client.render.ChickenDyeHolder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ChickenEntityRenderer;
import net.minecraft.client.render.entity.state.ChickenEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.ChickenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "叮咚鸡" 的渲染入口:
 * <ol>
 *   <li>{@code updateRenderState} —— 把这只鸡的颜色查出来塞进 render state;</li>
 *   <li>{@code render} 的头尾 —— 把颜色 push/pop 到 ThreadLocal, 供模型渲染时读取。</li>
 * </ol>
 * 真正改颜色的是 ModelTintMixin (模型渲染在 LivingEntityRenderer 里, 那一层拿不到实体)。
 */
@Mixin(ChickenEntityRenderer.class)
public class ChickenDyeRendererMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void randomperks$fillDyeColor(ChickenEntity entity, ChickenEntityRenderState state,
                                          float tickDelta, CallbackInfo ci) {
        ((ChickenDyeHolder) (Object) state).randomperks$setDyeColor(ChickenDyeState.colorOf(entity.getId()));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void randomperks$pushDye(ChickenEntityRenderState state, MatrixStack matrices,
                                     VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        int color = ((ChickenDyeHolder) (Object) state).randomperks$getDyeColor();
        if (color != ChickenDyeState.NO_TINT) {
            ChickenDyeState.pushTint(color);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void randomperks$popDye(ChickenEntityRenderState state, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        ChickenDyeState.popTint();
    }
}
