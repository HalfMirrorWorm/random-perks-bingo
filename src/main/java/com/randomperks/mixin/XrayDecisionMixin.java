package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "透视之眼"的判定时机修复。
 *
 * <p>Fabric 的 {@code PlayerBlockBreakEvents.AFTER} 注入在 {@code Block.onBroken} 里,
 * 比原版生成掉落 ({@code Block.afterBreak → Block.dropStacks}) **更早**。
 * 如果等到 dropStacks 才掷 35% 骰, 那么幸运矿工/八十/连锁挖矿等 onBlockBreak 回调
 * 读到的会是上一次破坏的结果。
 *
 * <p>因此在破坏流程的最开始 ({@code tryBreakBlock} HEAD) 掷一次并记录 (tick, pos) → 结果,
 * 之后掉落入口和所有 onBlockBreak 都读同一份结果。
 */
@Mixin(ServerPlayerInteractionManager.class)
public class XrayDecisionMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void randomperks$rollXrayDecision(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PerkBoostQuery.INSTANCE.rollXrayDecision(this.player, pos);
    }
}
