package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "大胃袋": 饥饿值与饱和度都满了也能继续吃 —— 解除 HungerManager.isNotFull() 的限制。
 *
 * 注入 ConsumableComponent.canConsume(用户, 物品), 它同时管着:
 *  - 服务端: 能不能真的吃下去
 *  - 客户端: 能不能吃 —— 返回 false 时原版客户端**不会发包**, 所以两边都得放行
 *
 * 只放开带 FOOD 组件的物品 (真正意义上的"食物"); 药水等保持原样。
 */
@Mixin(ConsumableComponent.class)
public class BigStomachMixin {

    private static final String BIG_STOMACH_PERK = "harvest_big_stomach";

    @Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
    private void randomperks$alwaysEat(LivingEntity user, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.get(DataComponentTypes.FOOD) == null) return;

        if (user.getWorld().isClient()) {
            if (ClientPerkFlags.has(BIG_STOMACH_PERK)) cir.setReturnValue(true);
            return;
        }
        if (user instanceof ServerPlayerEntity player && PerkBoostQuery.INSTANCE.hasBigStomach(player)) {
            cir.setReturnValue(true);
        }
    }
}
