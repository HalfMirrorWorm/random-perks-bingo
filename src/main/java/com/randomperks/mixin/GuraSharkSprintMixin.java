package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "古拉鲨鱼": 持有者**在陆地上无法疾跑** (在水里不受限制)。
 *
 * <p>疾跑是**客户端权威**的: 客户端的 ClientPlayerEntity#tickMovement 判定后调
 * {@code LivingEntity#setSprinting(true)}, 再把 ClientCommandC2SPacket 发给服务端,
 * 服务端跟着调一次 setSprinting; 真正的 +30% 加速也是这个方法挂在移动速度上的。
 * 所以拦在 setSprinting 的 HEAD 上, 两端就都不会进入疾跑状态 ——
 * 客户端不会发出疾跑指令, 服务端也不会挂加速修饰符。
 *
 * <p>"陆地" = 不在水里。雨中、创意飞行不算 (飞行本来就不是陆地疾跑, 且疾跑只影响
 * 视野; 拦掉会让人莫名难受)。
 *
 * <p>只拦 {@code setSprinting(true)}; {@code setSprinting(false)} 一律放行,
 * 否则会卡在"想停停不下来"的状态。走上陆地时残留的疾跑标记由客户端
 * GuraSharkSprintClientMixin 在 tickMovement 开头主动清掉。
 */
@Mixin(LivingEntity.class)
public class GuraSharkSprintMixin {

    private static final String PERK_ID = "swift_gura_shark";

    @Inject(method = "setSprinting(Z)V", at = @At("HEAD"), cancellable = true)
    private void randomperks$blockLandSprint(boolean sprinting, CallbackInfo ci) {
        if (!sprinting) return;
        if (!((Object) this instanceof PlayerEntity player)) return;
        if (player.isTouchingWater()) return;
        if (player.getAbilities().flying) return;

        boolean hasPerk;
        if (player.getEntityWorld().isClient()) {
            hasPerk = ClientPerkFlags.has(PERK_ID);
        } else {
            hasPerk = player instanceof ServerPlayerEntity serverPlayer
                && PerkBoostQuery.INSTANCE.hasGuraShark(serverPlayer);
        }
        if (hasPerk) ci.cancel();
    }
}
