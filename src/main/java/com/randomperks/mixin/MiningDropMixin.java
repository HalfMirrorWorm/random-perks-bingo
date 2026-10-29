package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 挖掘掉落的两条"代价/收益"路径, 都挂在原版唯一的掉落生成入口
 * {@code Block.dropStacks(state, world, pos, be, entity, tool)}:
 *
 * <ol>
 *   <li><b>透视之眼</b>: 35% 概率直接取消, 不生成任何掉落。</li>
 *   <li><b>矿脉嗅觉 / 老矿工</b>: 概率**合并后只掷一次骰**(1-(1-p1)(1-p2)),
 *       命中时用**同一份**掉落结果生成两遍 —— 只结算一次时运, 但两份内容完全一致。
 *       生成两遍而不是把数量 ×2, 是为了不破坏"最大堆叠为 1"的物品。</li>
 * </ol>
 */
@Mixin(Block.class)
public class MiningDropMixin {

    @Inject(
            method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void randomperks$miningDrops(
            BlockState state, World world, BlockPos pos,
            BlockEntity blockEntity, Entity entity, ItemStack tool,
            CallbackInfo ci
    ) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!(world instanceof ServerWorld serverWorld)) return;

        // 透视之眼: 读取 XrayDecisionMixin 在破坏流程开头掷出的判定
        if (PerkBoostQuery.INSTANCE.isXraySuppressed(player, pos)) {
            ci.cancel();
            return;
        }

        // 矿脉嗅觉 / 老矿工: 合并概率, 只掷一次
        float extraChance = PerkBoostQuery.INSTANCE.miningExtraDropChance(player, state);
        if (extraChance <= 0f) return;
        if (player.getRandom().nextFloat() >= extraChance) return;

        // 只结算一次掉落表 (含时运), 然后把同一份结果生成两遍
        List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, player, tool);
        for (ItemStack drop : drops) {
            Block.dropStack(serverWorld, pos, drop.copy());
            Block.dropStack(serverWorld, pos, drop.copy());
        }
        state.onStacksDropped(serverWorld, pos, tool, true);

        ci.cancel();
    }
}
