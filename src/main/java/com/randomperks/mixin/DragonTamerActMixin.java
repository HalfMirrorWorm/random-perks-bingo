package com.randomperks.mixin;

import com.randomperks.game.DragonTamerService;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "驯龙高手" 手动坐骑的移动放行。
 *
 * <p>{@code LivingEntity.tickMovement()} 只有在
 * {@code canMoveVoluntarily() && canActVoluntarily()} 时才会执行 {@code travel(...)}
 * (速度/重力/碰撞都在里面)。而 {@link MobEntity#canActVoluntarily()} 要求
 * {@code !isAiDisabled()} —— 本服务为了防止坐骑自己乱跑会关闭 AI, 于是 travel 被跳过,
 * 服务端每 tick 写入的速度永远不会被应用, 表现就是 WASD 完全没反应。
 *
 * <p>这里只对"由本服务手动操控的坐骑"把 canActVoluntarily 放行。**不要**去改
 * {@code getControllingPassenger()}: 那会让服务端认为坐骑由玩家客户端控制,
 * {@code isLogicalSideForUpdatingMovement()} 变成 false, 整个移动块直接被跳过。
 */
@Mixin(MobEntity.class)
public abstract class DragonTamerActMixin {

    @Inject(method = "canActVoluntarily", at = @At("HEAD"), cancellable = true)
    private void randomperks$allowManualRideMovement(CallbackInfoReturnable<Boolean> cir) {
        MobEntity self = (MobEntity) (Object) this;
        if (!(self.getFirstPassenger() instanceof ServerPlayerEntity rider)) return;

        if (DragonTamerService.INSTANCE.isManualRide(rider, self)) {
            cir.setReturnValue(true);
        }
    }
}
