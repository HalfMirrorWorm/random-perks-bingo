package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "巧手"词条: 挖掘方块时 50% 概率不消耗主手工具耐久。
 *
 * Fabric 的 PlayerBlockBreakEvents.AFTER 在原版 postMine (扣耐久) 之前触发,
 * 因此不能在事件里"补回耐久" —— 直接在 postMine 头部拦截: 命中时跳过本次扣除。
 */
@Mixin(ItemStack.class)
public class SteadyHandMixin {

    @Inject(method = "postMine", at = @At("HEAD"), cancellable = true)
    private void randomperks$steadyHand(
            World world, BlockState state, BlockPos pos, PlayerEntity miner, CallbackInfo ci
    ) {
        if (!(miner instanceof ServerPlayerEntity player)) return;
        if (PerkBoostQuery.INSTANCE.rollSteadyHand(player)) {
            ci.cancel();
        }
    }
}
