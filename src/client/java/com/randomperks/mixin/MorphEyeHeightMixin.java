package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的视角伪装 —— 变猪期间眼睛高度跟着降下来。
 *
 * 和 main 源集的 MorphDimensionsMixin (碰撞箱) 用的是同一组常量, 只是分工不同:
 * 眼睛高度纯粹是客户端表现 (改这里不会顺带压扁碰撞箱), 碰撞箱则两端都要一致。
 * 相机实际读的是 {@code standingEyeHeight}, 那份缓存在 {@code calculateDimensions()}
 * 时从碰撞箱的 eyeHeight 写上, 不经过这个方法。复活后的重算在 MorphSpawnMixin。
 *
 * 注: 目标方法在 Entity 上是 final, 但 @Inject 是往方法体里插字节码, 与 final 无关。
 */
@Mixin(Entity.class)
public class MorphEyeHeightMixin {

    @Inject(
            method = "getEyeHeight(Lnet/minecraft/world/entity/Pose;)F",
            at = @At("HEAD"),
            cancellable = true)
    private void randomperks$pigEyeHeight(Pose pose, CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        // 只对玩家生效 (词条效果是玩家变猪), 且用 UUID —— 客户端重生会复用实体 id
        if (!(self instanceof Player player)) return;
        if (!MorphState.isMorphed(player.getUUID())) return;

        cir.setReturnValue(MorphState.PIG_EYE_HEIGHT);
    }
}
