package com.randomperks.mixin;

import me.jfenn.bingo.common.menu.MenuComponent;
import org.joml.Vector3d;

/**
 * 两个菜单 mixin 之间的中转站。
 *
 * <p>Mixin 的 {@code @Unique} 字段会并进目标类, 跨两个不同目标类没法直接共享,
 * 所以放在这个普通类里: {@link BingoMenuButtonMixin} 写, {@link BingoMenuPageMixin} 读。
 */
public final class BingoMenuBridge {

    /** 最近一次 registerTileButton 的组件 (用来确认确实是 ROOT 页) */
    public static MenuComponent lastComponent;

    /** 最近一次 registerTileButton 的位置 */
    public static Vector3d lastPosition;

    /** 最近一次 registerTileButton 的宽 / 高 */
    public static double lastWidth;
    public static double lastHeight;

    private BingoMenuBridge() {
    }
}
