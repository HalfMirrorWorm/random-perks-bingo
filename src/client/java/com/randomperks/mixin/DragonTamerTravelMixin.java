package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.DragonTamerMotion;
import com.randomperks.game.DragonTamerService;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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

    @Inject(method = "travelRidden", at = @At("HEAD"), cancellable = true)
    private void randomperks$directDrive(Player controllingPlayer, Vec3 movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof Mob mob)) return;
        if (!(controllingPlayer instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!ClientPerkFlags.has(DragonTamerService.PERK_ID)) return;
        if (!DragonTamerService.INSTANCE.isManuallySteered(mob)) return;

        if (mob.getInterpolation() != null) {
            mob.getInterpolation().cancel();
        }

        double previousVertical = AIRBORNE_VERTICAL.getOrDefault(mob.getId(), 0.0);
        Vec3 velocity = DragonTamerMotion.INSTANCE.velocity(
                mob, player.getYRot(), player.getXRot(), player.input.keyPresses, previousVertical);
        AIRBORNE_VERTICAL.put(mob.getId(), velocity.y);

        mob.setDeltaMovement(velocity);
        mob.move(MoverType.SELF, velocity);

        mob.setYRot(player.getYRot());
        mob.setYHeadRot(player.getYRot());
        mob.setYBodyRot(player.getYRot());
        if (DragonTamerService.INSTANCE.isFlyingMount(mob)) {
            mob.setXRot(player.getXRot() * 0.5F);
            mob.fallDistance = 0.0;
            player.fallDistance = 0.0;
        } else {
            mob.setXRot(0.0F);
        }
        ci.cancel();
    }
}
