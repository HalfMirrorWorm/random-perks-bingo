package com.randomperks.mixin;

import com.randomperks.game.DragonTamerService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "驯龙高手" 手动操控坐骑的移动开关。
 *
 * <p>{@code LivingEntity.tickMovement()} 只有在
 * {@code getControllingPassenger() instanceof PlayerEntity} 时才会走
 * {@code travelControlled(...)} → {@code travel(...)} 分支; 而 {@link MobEntity}
 * 的实现只认 "另一只生物在操控" (且要求 AI 未禁用), 玩家骑乘时永远返回 null。
 *
 * <p>本服务为了阻止坐骑自己乱跑会禁用 AI, 于是普通生物既不走 AI 移动、也不走玩家操控移动,
 * 结果就是 "骑上去 WASD 完全没反应"。这里对由本服务手动操控的坐骑把骑手作为
 * controlling passenger 返回, 让原版正常执行 travel (速度/重力/碰撞), 实际方向仍由
 * {@link DragonTamerService} 每 tick 根据骑手输入写入。
 */
@Mixin(MobEntity.class)
public abstract class DragonTamerMountMixin {

    @Inject(method = "getControllingPassenger", at = @At("RETURN"), cancellable = true)
    private void randomperks$allowPlayerControlledMount(CallbackInfoReturnable<LivingEntity> cir) {
        if (cir.getReturnValue() != null) return;

        MobEntity self = (MobEntity) (Object) this;
        if (!(self.getFirstPassenger() instanceof ServerPlayerEntity rider)) return;

        if (DragonTamerService.INSTANCE.isManualRide(rider, self)) {
            cir.setReturnValue(rider);
        }
    }
}
