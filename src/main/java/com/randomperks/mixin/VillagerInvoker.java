package com.randomperks.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link VillagerEntity#fillRecipes()} —— "老牧师" 需要逐级调用它来累积交易。
 *
 * 原版只在**升级时**追加当前等级的那一组交易 (fillRecipes 里是
 * {@code factories = map.get(data.getLevel())}, 只取当前等级), 所以直接
 * setVillagerData(level=5) 再取 offers, 只会拿到 5 级那 2 个 —— 新手级的那些根本没被填进去。
 */
@Mixin(VillagerEntity.class)
public interface VillagerInvoker {

    // 注: 方法名不带 `$` —— Kotlin 标识符里不能直接写它, 而调用方在 Kotlin 里
    @Invoker("fillRecipes")
    void randomperksFillRecipes();
}
