package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "美食家"词条: 食用任意食物时, 恢复的饥饿值与饱和度均提升 25%。
 * 在原版进食完成后补足差额。
 */
@Mixin(ConsumableComponent.class)
public class ConsumableBoostMixin {

    @Inject(method = "finishConsumption", at = @At("TAIL"))
    private void randomperks$boostFood(
            World world,
            LivingEntity user,
            ItemStack stack,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (!PerkBoostQuery.INSTANCE.hasEatBoost(player)) return;

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return;

        int bonusFood = Math.round(food.nutrition() * 0.25f);
        float bonusSaturation = food.saturation() * 0.25f;

        HungerManager hunger = player.getHungerManager();
        int newFood = Math.min(20, hunger.getFoodLevel() + bonusFood);
        hunger.setFoodLevel(newFood);
        hunger.setSaturationLevel(Math.min(newFood, hunger.getSaturationLevel() + bonusSaturation));
    }
}
