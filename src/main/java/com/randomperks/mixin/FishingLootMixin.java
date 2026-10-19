package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "渔者之幸" —— 钓鱼时额外有 10% 概率多钓一个鹦鹉螺壳。
 *
 * 拦的是 {@code FishingBobberEntity.use(ItemStack)} 里对钓鱼战利品表的 generateLoot 调用:
 * 它发生在掉落列表生成完、物品撒出去之前, 正好可以往列表里加一项。
 *
 * 幸运属性本身已经通过参数里的 {@code owner.getLuck()} 生效 (它提高宝藏类的权重),
 * 这里只负责鹦鹉螺壳这额外的一层。
 */
@Mixin(FishingBobberEntity.class)
public class FishingLootMixin {

    private static final float BONUS_NAUTILUS_CHANCE = 0.10f;

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootWorldContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"
            )
    )
    private ObjectArrayList<ItemStack> randomperks$bonusNautilus(LootTable table, LootWorldContext params) {
        ObjectArrayList<ItemStack> loot = table.generateLoot(params);

        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        if (!(self.getPlayerOwner() instanceof ServerPlayerEntity owner)) return loot;
        if (!PerkBoostQuery.INSTANCE.hasLuckyFishing(owner)) return loot;
        if (owner.getRandom().nextFloat() >= BONUS_NAUTILUS_CHANCE) return loot;

        loot.add(new ItemStack(Items.NAUTILUS_SHELL));
        return loot;
    }
}
