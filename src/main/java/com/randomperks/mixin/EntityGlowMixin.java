package com.randomperks.mixin;

import com.randomperks.client.GlowMarkState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    /** 0 = 尚未分配实体 ID (刷怪笼/结构方块里的静态展示实体) */
    @Shadow
    private int id;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void randomperks$isGlowing(CallbackInfoReturnable<Boolean> cir) {
        // 刷怪笼的展示实体在 Entity.load 阶段就会走到这里, 此时还没有实体 ID;
        // 直接 getId() 会抛 IllegalStateException, 导致刷怪笼每帧都异常 + 严重掉帧。
        if (this.id == 0) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (GlowMarkState.INSTANCE.isMarked(self.getId())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasGlowingTag", at = @At("HEAD"), cancellable = true)
    private void randomperks$isGlowingLocal(CallbackInfoReturnable<Boolean> cir) {
        if (this.id == 0) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (GlowMarkState.INSTANCE.isMarked(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}
