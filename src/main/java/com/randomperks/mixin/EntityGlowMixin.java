package com.randomperks.mixin;

import com.randomperks.client.GlowMarkState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端发光标记: 让被标记的实体在本客户端显示发光轮廓
 * (由 GlowMarkS2C 驱动, 仅同队玩家会收到标记 —— 代替原版的全局发光效果)。
 *
 * 只在客户端加载 (见 mixins.randomperks.json 的 client 段)。
 */
@Mixin(Entity.class)
public class EntityGlowMixin {

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void randomperks$isGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (GlowMarkState.INSTANCE.isMarked(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isGlowingLocal", at = @At("HEAD"), cancellable = true)
    private void randomperks$isGlowingLocal(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (GlowMarkState.INSTANCE.isMarked(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}
