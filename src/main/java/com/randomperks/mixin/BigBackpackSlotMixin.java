package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "大背包" 的上限计算 (界面里的点击 / 拖拽 / Shift 移动那一条路径)。
 *
 * <p>原版 {@code Slot.getMaxItemCount(ItemStack)} = min(背包上限 99, 物品自身上限),
 * 它**不调用** {@link Container#getMaxStackSize} —— 所以只改背包那边, 界面上的堆叠仍然停在 64。
 * 这里对玩家背包的槽位单独放行。非玩家背包的槽位 (箱子、工作台合成格等) 不受影响。
 */
@Mixin(Slot.class)
public class BigBackpackSlotMixin {

    @Shadow
    @Final
    public Container container;

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void randomperks$bigBackpackMaxCount(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(this.container instanceof Inventory playerInventory)) return;

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
        cir.setReturnValue(Math.min(max * 4, 256));
    }
}
