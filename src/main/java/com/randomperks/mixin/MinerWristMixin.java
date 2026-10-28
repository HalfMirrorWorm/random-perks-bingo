package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "矿工手腕"的"挖矿石额外提速"部分。
 *
 * <p>原版 {@code PlayerEntity.getBlockBreakingSpeed(BlockState)} 是挖速的最终结算点
 * (方块硬度、工具速度、急迫、疲劳、水下、悬空都在里面), 这里在 TAIL 乘一个额外倍率 ——
 * 只在目标是矿石时生效, 因此不影响挖土/砍树。
 *
 * <p>通用的 +15% 仍走 {@code BLOCK_BREAK_SPEED} 属性 (所有方块), 本 mixin 只补矿石部分。
 */
@Mixin(PlayerEntity.class)
public class MinerWristMixin {

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void randomperks$oreMiningBonus(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        float multiplier = PerkBoostQuery.INSTANCE.miningBreakSpeedMultiplier(self, state);
        if (multiplier != 1.0f) {
            cir.setReturnValue(cir.getReturnValueF() * multiplier);
        }
    }
}
