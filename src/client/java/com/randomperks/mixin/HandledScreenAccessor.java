package com.randomperks.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link HandledScreen} 的 GUI 左上角坐标 (protected 的 x / y)。
 *
 * 为什么不用 @Shadow: 这两个字段声明在 HandledScreen 上, 而我们的 mixin 目标是 InventoryScreen
 * 之类的子类时, 没有 refmap 的 @Shadow 在目标类自身找不到继承字段, 会在 APPLY 阶段直接失败。
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    @Accessor("x")
    int randomperks$getX();

    @Accessor("y")
    int randomperks$getY();
}
