package com.randomperks.mixin;

import com.randomperks.menu.BingoMenuBridge;
import me.jfenn.bingo.common.menu.MenuComponent;
import me.jfenn.bingo.common.menu.Property;
import me.jfenn.bingo.platform.IDisplayEntity;
import me.jfenn.bingo.platform.IPlayerHandle;
import me.jfenn.bingo.platform.text.IText;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import java.util.List;

/**
 * 记录 bingo 大厅菜单里**最近一次**注册的方块按钮 (位置 / 宽高)。
 *
 * <p>ROOT 页最后注册的那一个就是「开始游戏」按钮 (见 bingo 的 {@code MenuPage.kt#createRootPage},
 * 开始按钮是函数的最后一句)。{@link BingoMenuPageMixin} 用它把「上局回顾」塞到开始按钮正下方。
 */
@Mixin(targets = "me.jfenn.bingo.common.menu.ButtonKt", remap = false)
public class BingoMenuButtonMixin {

    @Inject(method = "registerTileButton", at = @At("HEAD"), remap = false)
    private static void randomperks$recordLastTileButton(
            MenuComponent component,
            Vector3d position,
            String icon,
            IText text,
            Property<IText> textProp,
            float textScale,
            List<IText> tooltip,
            Property<List<IText>> tooltipProp,
            Property<Boolean> isActiveProp,
            String blockIdentifier,
            IDisplayEntity.Brightness brightness,
            double width,
            double height,
            Function1<IPlayerHandle, Boolean> permissionGetter,
            Function1<IPlayerHandle, Unit> onClick,
            CallbackInfo ci
    ) {
        BingoMenuBridge.lastComponent = component;
        BingoMenuBridge.lastPosition = position;
        BingoMenuBridge.lastWidth = width;
        BingoMenuBridge.lastHeight = height;
    }
}
