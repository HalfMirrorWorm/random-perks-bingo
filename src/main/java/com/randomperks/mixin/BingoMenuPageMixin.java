package com.randomperks.mixin;

import com.randomperks.menu.BingoMenuBridge;
import com.randomperks.recap.RecapService;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import me.jfenn.bingo.common.card.CardService;
import me.jfenn.bingo.common.config.ConfigService;
import me.jfenn.bingo.common.game.GameService;
import me.jfenn.bingo.common.menu.ButtonKt;
import me.jfenn.bingo.common.menu.CommonKt;
import me.jfenn.bingo.common.menu.ConstantProperty;
import me.jfenn.bingo.common.menu.MenuComponent;
import me.jfenn.bingo.common.options.BingoOptions;
import me.jfenn.bingo.common.options.OptionsService;
import me.jfenn.bingo.common.state.BingoState;
import me.jfenn.bingo.koin.core.scope.Scope;
import me.jfenn.bingo.platform.IPlayerHandle;
import me.jfenn.bingo.platform.text.IText;
import net.minecraft.server.network.ServerPlayerEntity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 在 bingo 大厅菜单的 ROOT 页里, **「开始游戏」按钮正下方**加一个「上局回顾」按钮。
 *
 * <p>做法: 在 {@code MenuPageKt#createRootPage} 返回时拿到刚建好的 {@link MenuComponent},
 * 再从 {@link BingoMenuBridge} 取出"最后注册的按钮"(就是开始按钮) 的位置/宽高,
 * 用同样的宽度、0.35 的高度挤在它下面 (菜单底部正好还有约 0.4 的空间)。
 *
 * <p>点击后由服务端把上一局的回顾整包发给该玩家, 客户端弹出回顾界面。
 * 权限判定直接放行 (不继承 bingo 的 CONFIGURE_GAME) —— 所有玩家都能看回顾。
 *
 * <p>本 mixin 的 {@code remap = false}: 目标类和参数都是 bingo 自己的类, 与 MC 混淆无关。
 */
@Mixin(targets = "me.jfenn.bingo.common.menu.MenuPageKt", remap = false)
public class BingoMenuPageMixin {

    /** 按钮高度: 开始按钮下方到菜单底部只剩约 0.4 */
    private static final double RECAP_BUTTON_HEIGHT = 0.35;
    private static final double RECAP_BUTTON_GAP = 0.05;

    @Inject(method = "createRootPage", at = @At("RETURN"), remap = false)
    private static void randomperks$addRecapButton(
            Scope koinScope,
            Vector3d position,
            BingoState state,
            BingoOptions options,
            OptionsService optionsService,
            GameService gameService,
            CardService cardService,
            ConfigService configService,
            CallbackInfoReturnable<MenuComponent> cir
    ) {
        MenuComponent component = cir.getReturnValue();
        if (component == null) return;

        // 防御: 只有当"最后注册的按钮"确实属于这个 ROOT 组件时才加
        if (BingoMenuBridge.lastComponent != component) return;
        Vector3d start = BingoMenuBridge.lastPosition;
        if (start == null) return;

        double width = BingoMenuBridge.lastWidth;
        double startHeight = BingoMenuBridge.lastHeight;
        double y = start.y - startHeight / 2.0 - RECAP_BUTTON_GAP - RECAP_BUTTON_HEIGHT / 2.0;

        IText label = component.getText().translatable(
                "random-perks-bingo.recap.title", "Last Game Recap"
        );

        Function1<IPlayerHandle, Boolean> everyone = player -> Boolean.TRUE;
        Function1<IPlayerHandle, Unit> onClick = player -> {
            ServerPlayerEntity serverPlayer = player.getPlayer();
            if (serverPlayer != null) {
                RecapService.INSTANCE.openFor(serverPlayer);
            }
            return Unit.INSTANCE;
        };

        ButtonKt.registerTileButton(
                component,
                new Vector3d(start.x, y, 0.0),
                null,                                          // icon
                label,                                         // text
                new ConstantProperty<>(label),                 // textProp
                0.7f,                                          // textScale (MENU_TEXT_SCALE)
                null,                                          // tooltip
                new ConstantProperty<List<IText>>(null),       // tooltipProp
                new ConstantProperty<>(false),                 // isActiveProp
                null,                                          // blockIdentifier (默认材质)
                CommonKt.getMENU_BRIGHTNESS_ALT(),
                width,
                RECAP_BUTTON_HEIGHT,
                everyone,
                onClick
        );
    }
}
