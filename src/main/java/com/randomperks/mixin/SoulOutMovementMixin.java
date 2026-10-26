package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出鞘": 灵魂形态下水平穿墙。
 *
 * <p>把垂直与水平移动拆开 —— 垂直仍走原版方块碰撞 (不会掉穿地板/天花板),
 * 水平临时 noClip 穿过墙体。客户端标记由 {@code SoulOutS2C} 同步。
 */
@Mixin(Entity.class)
public class SoulOutMovementMixin {

    private static final ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void randomperks$soulOutPhaseThroughWalls(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerEntity player)) return;
        if (SPLITTING.get()) return;
        if (!ClientPerkFlags.soulOutActive) return;
        if (player.isSpectator()) return;
        if (Math.abs(movement.x) < 1.0E-7 && Math.abs(movement.z) < 1.0E-7) return;

        SPLITTING.set(true);
        boolean oldNoClip = player.noClip;
        try {
            // 垂直: 保持原版方块碰撞
            player.move(type, new Vec3d(0.0, movement.y, 0.0));

            // 水平: 忽略方块碰撞
            player.noClip = true;
            player.move(type, new Vec3d(movement.x, 0.0, movement.z));
        } finally {
            player.noClip = oldNoClip;
            SPLITTING.set(false);
        }

        ci.cancel();
    }
}
