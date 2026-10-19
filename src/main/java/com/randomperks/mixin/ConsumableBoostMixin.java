package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import com.randomperks.game.PerkWorldEventService;
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
 * 进食相关的两个词条修正, 都挂在 {@code ConsumableComponent.finishConsumption} 的 TAIL ——
 * 此时饥饿值已由原版流程 (streamAll(Consumable) → FoodComponent.onConsume → HungerManager.eat)
 * 结算完毕。
 *
 * <ul>
 *   <li>"小鸟胃" —— 恢复的饥饿值与饱和度翻倍</li>
 *   <li>"丰盛大餐" —— 吃完一份食物时, 若饱食度已满 → 派发 {@code onFoodEaten}</li>
 * </ul>
 *
 * 两个效果写在同一个 handler 里而不是拆成两个 mixin: 它们注入的是同一个 TAIL 点,
 * 拆开后执行顺序不确定, "小鸟胃翻倍之后才算满"这种情形就会变成随机行为。
 *
 * <p>另注: 蛋糕(方块)走 CakeBlock 自己的 HungerManager.add 路径, 不经过 ConsumableComponent,
 * 两个词条都不享受。
 */
@Mixin(ConsumableComponent.class)
public class ConsumableBoostMixin {

    @Inject(method = "finishConsumption", at = @At("TAIL"))
    private void randomperks$afterEating(
            World world,
            LivingEntity user,
            ItemStack stack,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(user instanceof ServerPlayerEntity player)) return;

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null) return;   // 只处理食物 (药水等没有 FOOD 组件)

        // 1) "小鸟胃": 恢复的饥饿值与饱和度翻倍
        if (PerkBoostQuery.INSTANCE.hasEatBoost(player)) {
            // 注意: FoodComponent.saturation() 返回的是"饱和度点数"而非系数 ——
            // Builder.build() 里已按 calculateSaturation(nutrition, 系数) = nutrition * 系数 * 2 折算好
            // (面包: 5 * 0.6 * 2 = 6.0 点), 所以这里直接对点数取一半即可。
            int bonusFood = Math.round(food.nutrition() * 1.0f);
            float bonusSaturation = food.saturation() * 1.0f;

            // 钳制方式与原版 HungerManager.addInternal 完全一致: 食物钳 20, 饱和度钳到新食物值
            HungerManager hunger = player.getHungerManager();
            int newFood = Math.min(20, hunger.getFoodLevel() + bonusFood);
            hunger.setFoodLevel(newFood);
            hunger.setSaturationLevel(Math.min(newFood, hunger.getSaturationLevel() + bonusSaturation));
        }

        // 2) "丰盛大餐": 吃完一份食物时饱食度已满 → 随机炖菜效果
        //    (放在小鸟胃之后, 让"翻倍之后才满"也算满)
        if (player.getHungerManager().getFoodLevel() >= 20) {
            PerkWorldEventService.INSTANCE.dispatchFoodEaten(player);
        }
    }
}
