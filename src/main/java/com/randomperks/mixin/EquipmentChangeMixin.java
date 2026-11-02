package com.randomperks.mixin;

import com.randomperks.game.PerkWorldEventService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装备变化即时通知 (当前 Fabric API 没有装备变化事件, 直接注入原版 LivingEntity.equipStack)。
 * 用于"皇帝新衣"等"未穿护甲"类词条的即时结算 (不再等每秒轮询)。
 */
@Mixin(LivingEntity.class)
public class EquipmentChangeMixin {

    @Inject(method = "setItemSlot", at = @At("TAIL"))
    private void randomperks$onEquipStack(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        PerkWorldEventService.INSTANCE.dispatchEquipmentChange(player);
    }
}
