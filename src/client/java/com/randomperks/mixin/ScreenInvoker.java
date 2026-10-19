package com.randomperks.mixin;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Screen#addDrawableChild} (protected) —— 用于在**别人家的界面**上挂按钮。
 *
 * <p>目前只有「大厅配置菜单」用到: YACL 的 YACLScreen 是第三方界面, 我们拿不到它的子类,
 * 只能从 ScreenEvents 的回调里往 screen 上加。
 *
 * <p><b>泛型签名必须和原版一致</b>: 原方法是
 * {@code <T extends Element & Drawable & Selectable> T addDrawableChild(T)},
 * 擦除后描述符是 {@code (LElement;)LElement;}。若这里写成返回 ClickableWidget,
 * Loom 会按描述符去找方法而匹配不上, 结果注解值不会被重映射成 intermediary,
 * 运行时就找不到 addDrawableChild 而注入失败。
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    // 注: 方法名不带 `$` —— Kotlin 标识符里不能直接写它
    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T randomperksAddDrawableChild(T drawable);
}
