package com.randomperks.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 PlayerEntity 的 protected {@code vanishCursedItems()} (消失诅咒物品清除)。
 *
 * 为什么不用 @Shadow: "天启日" 的强制掉落挂在 {@link DoomsdayMixin} 里, 而那个 mixin 的
 * 目标是 ServerPlayerEntity —— {@code vanishCursedItems} 却声明在 PlayerEntity。
 * 跨类访问 protected 成员只能走 @Invoker: mixin 生成的桥接方法写在**目标类内部**,
 * 因此能合法调用 protected 方法。
 */
@Mixin(PlayerEntity.class)
public interface PlayerEntityInvoker {

    @Invoker("vanishCursedItems")
    void randomperks$vanishCursedItems();
}
