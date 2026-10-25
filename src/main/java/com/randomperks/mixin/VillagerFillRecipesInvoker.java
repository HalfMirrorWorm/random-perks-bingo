package com.randomperks.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code VillagerEntity.fillRecipes()} (protected)。
 *
 * <p>"野人魔了"需要按 1→5 级逐级填充交易列表: 只把等级设成 5 再读取的话,
 * 原版只会生成大师级那两条交易。
 */
@Mixin(VillagerEntity.class)
public interface VillagerFillRecipesInvoker {

    @Invoker("fillRecipes")
    void invokeFillRecipes();
}
