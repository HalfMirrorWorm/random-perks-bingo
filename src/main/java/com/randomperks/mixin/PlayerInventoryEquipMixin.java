package com.randomperks.mixin;

import com.randomperks.game.PerkWorldEventService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家背包的**装备索引** (36+, 走 equipment.put) 的写入不经过 LivingEntity.equipStack,
 * 补在这里分发装备变化事件 —— 保证界面穿/脱护甲时"皇帝新衣"等词条即时重算。
 */
@Mixin(PlayerInventory.class)
public class PlayerInventoryEquipMixin {

    @Shadow
    public PlayerEntity player;

    @Inject(method = "setStack(ILnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void randomperks$equipChanged(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot < 36) return;  // 0-35 为主背包/快捷栏 (PlayerInventory.MAIN_SIZE)
        if (!(this.player instanceof ServerPlayerEntity serverPlayer)) return;
        PerkWorldEventService.INSTANCE.dispatchEquipmentChange(serverPlayer);
    }
}
