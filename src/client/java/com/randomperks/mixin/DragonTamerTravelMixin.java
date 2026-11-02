package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.DragonTamerMotion;
import com.randomperks.game.DragonTamerService;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "驯龙高手": 截住 {@code travelControlled}, 本地直接驾驶。
 *
 * <p>不能去改 {@code LivingEntity.travel}: 恶魂、幻翼这些生物自己覆盖了 travel,
 * 注入父类方法不会跑到。{@code travelControlled} 是唯一的入口, 而且是 private。
 *
 * <p>地面坐骑的重力要自己累积。服务端会把速度清成 0, 如果每拍都读实体速度,
 * 下落会被重置成只有一拍重力。
 */
@Mixin(LivingEntity.class)
public abstract class DragonTamerTravelMixin {

    private static final Map<Integer, Double> AIRBORNE_VERTICAL = new HashMap<>();

    @Inject(method = "travelControlled", at = @At("HEAD"), cancellable = true)
    private void randomperks$directDrive(PlayerEntity controllingPlayer, Vec3d movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof MobEntity mob)) return;
        if (!(controllingPlayer instanceof ClientPlayerEntity player)) return;
        if (player != MinecraftClient.getInstance().player) return;
        if (!ClientPerkFlags.has(DragonTamerService.PERK_ID)) return;
        if (!DragonTamerService.INSTANCE.isManuallySteered(mob)) return;

        if (mob.getInterpolator() != null) {
            mob.getInterpolator().clear();
        }

        double previousVertical = AIRBORNE_VERTICAL.getOrDefault(mob.getId(), 0.0);
        Vec3d velocity = DragonTamerMotion.INSTANCE.velocity(
                mob, player.getYaw(), player.getPitch(), player.input.playerInput, previousVertical);
        AIRBORNE_VERTICAL.put(mob.getId(), velocity.y);

        mob.setVelocity(velocity);
        mob.move(MovementType.SELF, velocity);

        mob.setYaw(player.getYaw());
        mob.setHeadYaw(player.getYaw());
        mob.setBodyYaw(player.getYaw());
        if (DragonTamerService.INSTANCE.isFlyingMount(mob)) {
            mob.setPitch(player.getPitch() * 0.5F);
            mob.fallDistance = 0.0;
            player.fallDistance = 0.0;
        } else {
            mob.setPitch(0.0F);
        }
        ci.cancel();
    }
}
