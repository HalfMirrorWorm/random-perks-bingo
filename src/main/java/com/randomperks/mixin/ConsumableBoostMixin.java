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
 * "小鸟胃"词条: 食用任意食物时, 恢复的饥饿值与饱和度**至少**提升 50%。
 *
 * 注入点在 finishConsumption 的 TAIL —— 此时饥饿值已由 FoodComponent.onConsume 结算完毕
 * (原版流程: streamAll(Consumable) → FoodComponent.onConsume → HungerManager.eat), 这里只补差额。
 *
 * 饥饿值是整数, 用 Math.round 向上取整, 因此奇数营养值的食物实际超过 50%
 * (胡萝卜 3 → +2 = 67%, 干海带 1 → +1 = 100%); 饱和度是 float, 精确 +50%。
 * 另注: 蛋糕(方块)走 CakeBlock 自己的 HungerManager.add 路径, 不经过 ConsumableComponent, 不享受加成。
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

        // 注意: FoodComponent.saturation() 返回的是"饱和度点数"而非系数 ——
        // Builder.build() 里已按 calculateSaturation(nutrition, 系数) = nutrition * 系数 * 2 折算好
        // (面包: 5 * 0.6 * 2 = 6.0 点), 所以这里直接对点数取一半即可。
        int bonusFood = Math.round(food.nutrition() * 0.5f);
        float bonusSaturation = food.saturation() * 0.5f;

        // 钳制方式与原版 HungerManager.addInternal 完全一致: 食物钳 20, 饱和度钳到新食物值
        HungerManager hunger = player.getHungerManager();
        int newFood = Math.min(20, hunger.getFoodLevel() + bonusFood);
        hunger.setFoodLevel(newFood);
        hunger.setSaturationLevel(Math.min(newFood, hunger.getSaturationLevel() + bonusSaturation));
    }
}
