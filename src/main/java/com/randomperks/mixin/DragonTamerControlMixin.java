package com.randomperks.mixin;

import com.randomperks.game.DragonTamerService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "驯龙高手": 服务端承认骑手是这只坐骑的控制者。
 *
 * <p>这样 {@code onVehicleMove} 才会收下客户端算好的位置。服务端不再自己跑
 * {@code travel}: {@code isLogicalSideForUpdatingMovement()} 在"由玩家控制"时为 false,
 * 位置以载具移动包为准, 跟马、猪同一条链路。客户端那一半在
 * {@code DragonTamerControlClientMixin}。
 *
 * <p>马/猪/炽足兽和套了挽具的快乐恶魂走原版, 这里不改。
 */
@Mixin(Mob.class)
public abstract class DragonTamerControlMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void randomperks$serverRiderControls(CallbackInfoReturnable<LivingEntity> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self.getFirstPassenger() instanceof ServerPlayer rider)) return;
        if (!DragonTamerService.INSTANCE.isManuallySteered(self)) return;
        if (DragonTamerService.INSTANCE.isManualRide(rider, self)) {
            cir.setReturnValue(rider);
        }
    }
}
