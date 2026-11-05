package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "大背包" 的上限计算 (拾取 / 放入背包那一条路径)。
 *
 * <p>注入的是 {@link Container#getMaxStackSize(ItemStack)} 的默认实现 ——
 * {@code PlayerInventory.addStack()} 就是靠它算"这一格还能再塞多少",
 * 拾取、{@code offerOrDrop}、{@code insertStack} 全部走这里。
 *
 * <p>只有 {@code this} 是玩家背包时才放大; 箱子等容器仍按原版上限。
 * **不可堆叠物品 (上限 1) 保持 1**, 否则两把相同的工具/盔甲会被合并。
 */
@Mixin(Container.class)
public interface BigBackpackInventoryMixin {

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void randomperks$bigBackpackMaxCount(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof Inventory playerInventory)) return;

        Player owner = playerInventory.player;
        boolean hasPerk;
        if (owner.level().isClientSide()) {
            hasPerk = ClientPerkFlags.has("artisan_big_backpack");
        } else {
            hasPerk = owner instanceof ServerPlayer serverPlayer
                && PerkBoostQuery.INSTANCE.hasBigBackpack(serverPlayer);
        }
        if (!hasPerk) return;

        int max = stack.getMaxStackSize();
        if (max <= 1) return;
        cir.setReturnValue(Math.min(max * 4, 99));
    }
}

