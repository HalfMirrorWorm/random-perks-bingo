package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * 它**不调用** {@link Inventory#getMaxCount} —— 所以只改背包那边, 界面上的堆叠仍然停在 64。
 * 这里对玩家背包的槽位单独放行。非玩家背包的槽位 (箱子、工作台合成格等) 不受影响。
 */
@Mixin(Slot.class)
public class BigBackpackSlotMixin {

    @Shadow
    @Final
    public Inventory inventory;

    @Inject(method = "getMaxItemCount(Lnet/minecraft/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void randomperks$bigBackpackMaxCount(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(this.inventory instanceof PlayerInventory playerInventory)) return;

        PlayerEntity owner = playerInventory.player;
        boolean hasPerk;
        if (owner.getWorld().isClient()) {
            hasPerk = ClientPerkFlags.has("artisan_big_backpack");
        } else {
            hasPerk = owner instanceof ServerPlayerEntity serverPlayer
                && PerkBoostQuery.INSTANCE.hasBigBackpack(serverPlayer);
        }
        if (!hasPerk) return;

        int max = stack.getMaxCount();
        if (max <= 1) return;
        cir.setReturnValue(Math.min(max * 4, 256));
    }
}
