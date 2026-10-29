package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把"破坏方块时的随机判定"统一提前到流程最开头。
 *
 * <p>Fabric 的 {@code PlayerBlockBreakEvents.AFTER} 注入在 {@code Block.onBroken} 里,
 * 比原版生成掉落 ({@code Block.afterBreak → Block.dropStacks}) **更早**。
 * 如果等到 dropStacks 才掷骰, 幸运矿工/八十/连锁挖矿等 onBlockBreak 回调
 * 读到的会是上一次破坏的结果。
 *
 * <p>所以在 {@code tryBreakBlock} 的 HEAD 一次掷出:
 * <ul>
 *   <li>透视之眼的 35% 不掉落判定</li>
 *   <li>矿脉嗅觉/老矿工的额外掉落判定 (命中时整批掉落翻倍)</li>
 * </ul>
 * 掉落入口和所有 onBlockBreak 都读同一份结果。
 */
@Mixin(ServerPlayerInteractionManager.class)
public class BreakDecisionMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    public ServerWorld world;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void randomperks$rollBreakDecisions(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PerkBoostQuery.INSTANCE.rollBreakDecisions(this.player, pos, this.world.getBlockState(pos));
    }
}
