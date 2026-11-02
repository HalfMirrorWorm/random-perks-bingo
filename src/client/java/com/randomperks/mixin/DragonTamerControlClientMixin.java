package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.DragonTamerService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "驯龙高手": 本地玩家成为坐骑的控制者。
 *
 * <p>原版只有这时才会在客户端跑 {@code travel}, 并发送 {@code VehicleMoveC2SPacket}。
 * 实体追踪包看到"移动由本地玩家负责"就会直接丢掉, 不再把坐骑往服务端旧位置上插值。
 */
@Mixin(Mob.class)
public abstract class DragonTamerControlClientMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void randomperks$localRiderControls(CallbackInfoReturnable<LivingEntity> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide()) return;
        if (!(self.getFirstPassenger() instanceof LocalPlayer rider)) return;
        if (rider != Minecraft.getInstance().player) return;
        if (!ClientPerkFlags.has(DragonTamerService.PERK_ID)) return;
        if (!DragonTamerService.INSTANCE.isManuallySteered(self)) return;
        cir.setReturnValue(rider);
    }
}
