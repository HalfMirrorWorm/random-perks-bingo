package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "透视之眼"的代价: 用镐挖方块时, 30% 概率**不生成**该方块的掉落物。
 *
 * 注入原版掉落生成的唯一入口 Block.dropStacks (玩家破坏路径会传入破坏者与工具),
 * 判定命中时直接取消 —— 从源头掐掉, 而不是事后扫描删除 (不会误伤附近的其他物品,
 * 也没有"连挖时被前后两次判定重复删除"的问题)。
 */
@Mixin(Block.class)
public class XrayDropSuppressMixin {

    @Inject(
        method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void randomperks$xraySuppressDrop(
        BlockState state, World world, BlockPos pos,
        BlockEntity blockEntity, Entity entity, ItemStack tool,
        CallbackInfo ci
    ) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (!PerkBoostQuery.INSTANCE.shouldSuppressXrayDrop(player, tool)) return;
        ci.cancel();
    }
}
