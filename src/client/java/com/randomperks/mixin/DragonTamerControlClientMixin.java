package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.DragonTamerService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
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
@Mixin(MobEntity.class)
public abstract class DragonTamerControlClientMixin {

    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void randomperks$localRiderControls(CallbackInfoReturnable<LivingEntity> cir) {
        MobEntity self = (MobEntity) (Object) this;
        if (!self.getWorld().isClient()) return;
        if (!(self.getFirstPassenger() instanceof ClientPlayerEntity rider)) return;
        if (rider != MinecraftClient.getInstance().player) return;
        if (!ClientPerkFlags.has(DragonTamerService.PERK_ID)) return;
        if (!DragonTamerService.INSTANCE.isManuallySteered(self)) return;
        cir.setReturnValue(rider);
    }
}
