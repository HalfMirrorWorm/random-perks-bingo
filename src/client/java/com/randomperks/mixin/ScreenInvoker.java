package com.randomperks.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link Screen#addDrawableChild} (protected) —— 用于在**别人家的界面**上挂按钮。
 *
 * 目前只有「大厅配置菜单」用到: YACL 的 YACLScreen 是第三方界面, 我们拿不到它的子类,
 * 只能从 ScreenEvents 的回调里往 screen 上加。
 */
@Mixin(Screen.class)
public interface ScreenInvoker {

    // 注: 方法名不带 `$` —— Kotlin 标识符里不能直接写它
    @Invoker("addDrawableChild")
    ClickableWidget randomperksAddDrawableChild(ClickableWidget widget);
}
