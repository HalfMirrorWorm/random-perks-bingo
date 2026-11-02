package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的碰撞箱伪装 —— 变猪期间用猪的尺寸 (0.6 × 0.9)。
 *
 * ★ 必须挂在 {@link Avatar} 上, 不能挂 {@link net.minecraft.world.entity.LivingEntity}:
 *   26.3 里玩家的姿势尺寸表挪到了 Avatar, 它重写 {@code getDefaultDimensions}
 *   并且不调用 super。Player 自己不再覆盖这个方法。
 *
 * 睡姿 / 死亡姿势不覆盖, 免得躺下或倒地时也被压成猪的高度。
 * 复活后的高度不在这里处理: 客户端会新建玩家实体, 缓存的眼睛高度停在玩家默认值,
 * 由 MorphSpawnMixin 在实体加入世界时重算。
 *
 * 碰撞箱属于"服务端权威 + 客户端预测"的东西, 所以 [MorphState] 两端各记一份;
 * 状态变化时由调用方主动 {@code calculateDimensions()} 刷新已缓存的 boundingBox。
 */
@Mixin(Avatar.class)
public class MorphDimensionsMixin {

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.SLEEPING || pose == Pose.DYING) return;
        if (!((Object) this instanceof Player player)) return;
        if (MorphState.isMorphed(player.getUUID())) {
            cir.setReturnValue(EntityDimensions.fixed(MorphState.PIG_WIDTH, MorphState.PIG_HEIGHT));
        }
    }
}
