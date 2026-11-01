package com.randomperks.menu;

import me.jfenn.bingo.common.menu.MenuComponent;
import org.joml.Vector3d;

/**
 * 两个菜单 mixin 之间的中转站。
 *
 * <p>Mixin 的 {@code @Unique} 字段会并进目标类, 跨两个不同目标类没法直接共享,
 * 所以放在这个普通类里:
 * {@code BingoMenuButtonMixin} 写, {@code BingoMenuPageMixin} 读。
 *
 * <p><b>注意必须放在 {@code com.randomperks.mixin} 包之外</b> ——
 * 那个包被 mixins.randomperks.json 声明为 mixin 专属包, Mixin 会拒绝任何 mixin
 * 直接引用该包下的普通类 (IllegalClassLoadError: ... cannot be referenced directly)。
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
