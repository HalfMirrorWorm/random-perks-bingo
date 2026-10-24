package com.randomperks.mixin;

import com.randomperks.game.DragonTamerService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "驯龙高手": 手动操控坐骑时, 忽略客户端发来的 {@code VehicleMoveC2SPacket}。
 *
 * <p>原版里玩家骑乘坐骑时, 客户端是移动权威: 每 tick 发送 {@code VehicleMoveC2SPacket},
 * 服务端在 {@code onVehicleMove} 里把坐骑直接定位到客户端报告的位置。
 * 我们的手动坐骑是**服务端驱动**的 (客户端没有对应的移动逻辑), 客户端报告的永远是
 * 上一 tick 的旧位置 —— 如果照单全收, 服务端刚算出的位移会被立刻覆盖, 表现为坐骑原地不动。
 *
 * <p>这里把 {@code onVehicleMove} 里 "该坐骑由这名玩家控制" 的判定对由本服务手动操控的坐骑
 * 伪装成 false, 让原版跳过整个应用流程 (待处理的传送仍在判定之前处理, 不受影响)。
 * 坐骑的真实位置由服务端 {@code travel} 计算后通过实体追踪同步给客户端。
 */
@Mixin(ServerPlayNetworkHandler.class)
public class DragonTamerVehicleMoveMixin {

    @Redirect(
            method = "onVehicleMove",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getControllingPassenger()Lnet/minecraft/entity/LivingEntity;"
            )
    )
    private static LivingEntity randomperks$ignoreClientVehicleMove(Entity entity) {
        LivingEntity controlling = entity.getControllingPassenger();
        if (controlling instanceof ServerPlayerEntity rider
                && entity instanceof MobEntity mob
                && DragonTamerService.INSTANCE.isManualRide(rider, mob)) {
            return null;
        }
        return controlling;
    }
}
