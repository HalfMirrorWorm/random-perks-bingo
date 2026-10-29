package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "老矿工"的挖掘等级 +1。
 *
 * <p>原版 {@code PlayerEntity.canHarvest(state)} 决定两件事:
 * <ul>
 *   <li>能否掉落 (破坏后是否调用 afterBreak 生成掉落物);</li>
 *   <li>挖掘速度的除数 (能收获 30 / 不能收获 100)。</li>
 * </ul>
 * 所以在这里放行, 等价于"用高一级的镐挖掘": 有掉落, 也不会吃 100 倍的慢速惩罚。
 *
 * <p>等级以**材料**定义: 空手或任何非镐物品都视为木镐 (等级 1),
 * 手持镐则按材料 +1 —— 木/金 → 石, 石 → 铁, 铁 → 钻石, 钻石 → 下界合金。
 * 只对镐类方块生效。
 */
@Mixin(PlayerEntity.class)
public class OldMinerMixin {

    private static final String PERK_ID = "delver_collector";

    @Inject(method = "canHarvest", at = @At("RETURN"), cancellable = true)
    private void randomperks$oldMinerTier(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) {
            if (!ClientPerkFlags.has(PERK_ID)) return;
        } else {
            if (!PerkBoostQuery.INSTANCE.hasOldMiner(self)) return;
        }

        // 只对镐类方块生效, 避免给斧/锹类方块凭空发掉落
        if (!state.isIn(BlockTags.PICKAXE_MINEABLE)) return;

        int heldTier = heldPickaxeTier(self.getInventory().getSelectedStack());
        int effectiveTier = Math.min(heldTier + 1, 5);  // 下界合金封顶
        int requiredTier = requiredTier(state);
        if (effectiveTier >= requiredTier) {
            cir.setReturnValue(true);
        }
    }

    /** 手持物品的等级: 0=空手/非镐 (统一视为木镐), 1=木/金, 2=石, 3=铁, 4=钻石, 5=下界合金 */
    private static int heldPickaxeTier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!stack.isIn(ItemTags.PICKAXES)) return 0;   // 任何非镐物品都视为木镐

        if (stack.isOf(Items.NETHERITE_PICKAXE)) return 5;
        if (stack.isOf(Items.DIAMOND_PICKAXE)) return 4;
        if (stack.isOf(Items.IRON_PICKAXE)) return 3;
        if (stack.isOf(Items.STONE_PICKAXE)) return 2;
        if (stack.isOf(Items.WOODEN_PICKAXE) || stack.isOf(Items.GOLDEN_PICKAXE)) return 1;

        // 模组镐: 按"已经能正确挖掘的代表性方块"推断等级
        if (stack.isSuitableFor(Blocks.OBSIDIAN.getDefaultState())) return 4;
        if (stack.isSuitableFor(Blocks.DIAMOND_ORE.getDefaultState())) return 3;
        if (stack.isSuitableFor(Blocks.IRON_ORE.getDefaultState())) return 2;
        return 1;
    }

    /** 方块所需等级: 只认原版的 needs_*_tool 标签, 其余镐类方块按木级处理 */
    private static int requiredTier(BlockState state) {
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) return 4;
        if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) return 3;
        if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) return 2;
        return 1;
    }
}
