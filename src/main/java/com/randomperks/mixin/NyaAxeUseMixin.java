package com.randomperks.mixin;

import com.randomperks.game.NyaAxeService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "喵人斧": 斧头真正命中目标后给使用者挂永久虚弱 I。
 *
 * <p>{@code Player.itemAttackInteraction} 只在伤害实际结算时调用
 * {@link ItemStack#postHurtEnemy(LivingEntity, LivingEntity)} —— 被盾牌完全挡下、
 * 目标处于创造模式等没有造成伤害的情况不会走到这里, 与"斧头消耗了一次耐久"同步。
 */
@Mixin(ItemStack.class)
public class NyaAxeUseMixin {

    @Inject(method = "postHurtEnemy", at = @At("TAIL"))
    private void randomperks$applyNyaAxePenalty(
            LivingEntity target,
            LivingEntity attacker,
            CallbackInfo ci
    ) {
        if (!(attacker instanceof ServerPlayer player)) return;
        if (!NyaAxeService.INSTANCE.isMarked((ItemStack) (Object) this)) return;
        NyaAxeService.INSTANCE.onUsed(player);
    }
}
