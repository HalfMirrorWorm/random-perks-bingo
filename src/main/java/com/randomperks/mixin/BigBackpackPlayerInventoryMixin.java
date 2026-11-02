package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "大背包" 对原版 {@code PlayerInventory.offer} 的修正。
 *
 * <p>原版用**物品自身的堆叠上限**算剩余空间:
 * {@code room = stack.getMaxCount() - slotStack.getCount()}。4 倍堆叠下,
 * 背包格里的数量可以到 200, 而物品上限仍是 64 —— {@code room} 变成负数,
 * {@code split(负数)} 不会取走任何东西 (反而把传入的 stack 变大),
 * 随后 {@code goto 0} 的循环永远不会结束: 服务器主线程卡死 (看门狗崩溃),
 * 并且持续复制物品。
 *
 * <p>所以这里把该上限换成"背包格上限" (大背包生效时是 4 倍上限), 保证每次至少前进 1 个。
 * 没持有该词条时返回原值, 与未安装本 mod 的行为完全一致。
 */
@Mixin(Inventory.class)
public class BigBackpackPlayerInventoryMixin {

    private static final String PERK_ID = "artisan_big_backpack";

    @Redirect(
            method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I")
    )
    private int randomperks$bigBackpackOfferMax(ItemStack stack) {
        Inventory inventory = (Inventory) (Object) this;
        Player owner = inventory.player;

        boolean hasPerk;
        if (owner.level().isClientSide()) {
            hasPerk = ClientPerkFlags.has(PERK_ID);
        } else {
            hasPerk = owner instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasBigBackpack(serverPlayer);
        }

        return hasPerk ? inventory.getMaxStackSize(stack) : stack.getMaxStackSize();
    }
}
