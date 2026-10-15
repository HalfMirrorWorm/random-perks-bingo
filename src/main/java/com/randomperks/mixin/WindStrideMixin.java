package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "风之步伐"词条 (服务端侧): 与原版疾跑加成的挂载点完全一致 ——
 * 在原版 LivingEntity.setSprinting 挂/摘 +30% 疾跑修饰符之后,
 * 为持有该词条的玩家挂/摘我们的 +20% 修饰符 (客户端侧由 WindStrideClientMixin 本地结算)。
 */
@Mixin(LivingEntity.class)
public class WindStrideMixin {

    @Inject(method = "setSprinting(Z)V", at = @At("TAIL"))
    private void randomperks$windStride(boolean sprinting, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        PerkBoostQuery.INSTANCE.updateWindStride(player, sprinting);
    }
}
