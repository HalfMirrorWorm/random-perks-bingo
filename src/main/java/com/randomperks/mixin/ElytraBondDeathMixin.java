package com.randomperks.mixin;

import com.randomperks.game.ElytraBondService;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "风之翼"词条: 死亡时把绑定鞘翅取下**暂存** —— 既不被原版掉落, 也不被销毁,
 * 重生时原样装回 (单物品版"死亡不掉落")。
 */
@Mixin(ServerPlayerEntity.class)
public class ElytraBondDeathMixin {

    @Inject(method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"))
    private void randomperks$saveBondedElytra(DamageSource source, CallbackInfo ci) {
        ElytraBondService.INSTANCE.saveOnDeath((ServerPlayerEntity) (Object) this);
    }
}
