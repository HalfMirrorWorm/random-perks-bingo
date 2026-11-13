package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * "破壁人": 给被玩家挖掉的基岩补一份基岩掉落。
 *
 * <p>基岩没有原版掉落表, 即使 {@link WallbreakerHarvestMixin} 让收获判定通过,
 * {@code Block.getDrops} 也会返回空列表。这里只在"玩家 + 持有词条 + 工具能挖黑曜石"
 * 时追加一个基岩物品, 与黑曜石"挖掉即掉落自身"的效果对齐。
 */
@Mixin(Block.class)
public class WallbreakerDropMixin {

    @Inject(
            method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
            at = @At("RETURN")
    )
    private static void randomperks$bedrockDrop(
            BlockState state, ServerLevel world, BlockPos pos, BlockEntity blockEntity,
            Entity entity, ItemInstance tool, CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        if (!state.is(Blocks.BEDROCK)) return;
        if (!(entity instanceof ServerPlayer player)) return;
        if (!PerkBoostQuery.INSTANCE.hasWallbreaker(player)) return;
        // 用 getDrops 传入的工具副本判断: 最后 1 点耐久可能已在 mineBlock 时断掉,
        // 此时主手已经是空的, 但这次破坏确实是用能挖黑曜石的镐完成的
        if (!(tool instanceof ItemStack toolStack)) return;
        if (!toolStack.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return;

        // 空基岩掉落表可能返回不可变列表, 复制一份再追加
        List<ItemStack> drops = new ArrayList<>(cir.getReturnValue());
        drops.add(new ItemStack(Items.BEDROCK));
        cir.setReturnValue(drops);
    }
}
