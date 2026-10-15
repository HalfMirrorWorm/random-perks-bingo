package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "风之翼"词条: 死亡时**不掉落**绑定鞘翅 (胸甲槽里那件)。
 *
 * 在死亡流程最前端清空胸甲槽, 让原版掉落逻辑看不到它; 重生时由词条 onRespawn
 * 重新绑定 —— 净效果: 不掉落、不复制、无法刷。
 * (清空引发的装备事件里, 词条的自动换装因 isAlive 守卫不会给尸体补装)
 */
@Mixin(ServerPlayerEntity.class)
public class ElytraBondDeathMixin {

    @Inject(method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"))
    private void randomperks$keepBondedElytra(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        if (!PerkBoostQuery.INSTANCE.hasElytraBond(player)) return;
        if (!player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
    }
}
