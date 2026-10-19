package com.randomperks.mixin;

import com.randomperks.client.ChickenDyeState;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 把"当前正在渲染的鸡"的染色应用到模型的 color 参数上 ("叮咚鸡")。
 *
 * 只改 5 参数版的 render: 4 参数版内部就是转调它并传 -1 (白色)。
 * 参数索引 0=this, 1=matrices, 2=vertices, 3=light, 4=overlay, 5=color。
 */
@Mixin(Model.class)
public class ModelTintMixin {

    @ModifyVariable(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5
    )
    private int randomperks$applyChickenTint(int color) {
        int tint = ChickenDyeState.currentTint();
        return tint == ChickenDyeState.NO_TINT ? color : tint;
    }
}
