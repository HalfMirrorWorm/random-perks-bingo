package com.randomperks.mixin;

import com.randomperks.config.PerkConfig;
import me.jfenn.bingo.common.menu.ButtonKt;
import me.jfenn.bingo.common.menu.MenuComponent;
import me.jfenn.bingo.common.options.BingoCardOptions;
import me.jfenn.bingo.common.options.OptionsService;
import me.jfenn.bingo.common.state.BingoState;
import me.jfenn.bingo.common.text.TextProvider;
import me.jfenn.bingo.platform.text.IText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 把「随机词条」开关放进大厅菜单的「游戏模式」面板 (与抢夺/物品占领/隐藏任务/消耗并列)。
 *
 * <p>bingo 2.9.6 的模式面板是: 标题 + 2 列 × 2 行模式格子 + 一整行「特性」按钮。
 * 这里把「特性」按钮改成第三行右格 (缩成普通格子大小), 第三行左格放「随机词条」,
 * 于是模式面板变成 2 列 × 3 行, 不再需要在特性页单独占一块。
 *
 * <p>开关直接读写本 mod 的 {@link PerkConfig} (持久化到 config/random-perks-bingo.json),
 * 与 `/randomperks enable|disable` 完全同源; 开局时由 PerkSession 快照, 所以是「下一局生效」。
 *
 * <p>按钮点击后 bingo 的 registerButtonInteraction 会自动广播 OptionsChangedEvent,
 * 菜单随之重绘, 按钮的亮/暗状态和 tooltip 就会刷新 —— 不需要手动 markDirty。
 */
@Mixin(targets = "me.jfenn.bingo.common.menu.GameModesKt")
public class PerkMenuToggleMixin {

    // GameModes.kt 的私有布局常量 (TILE_WIDTH / TILE_HEIGHT / TILE_MARGIN / offsetY)
    private static final double TILE_WIDTH = 1.2;
    private static final double TILE_HEIGHT = 0.7;
    private static final double TILE_MARGIN = 0.1;
    private static final double TILE_OFFSET_X = (TILE_WIDTH + TILE_MARGIN) / 2.0;
    /** 第三行格子的中心 y (面板原点下方) */
    private static final double ROW_3_Y = -(0.6 + 3 * TILE_HEIGHT + 2 * TILE_MARGIN);
    /** 「特性」按钮原本的中心 y */
    private static final double FEATURES_ORIGINAL_Y = -(0.6 + 2 * TILE_HEIGHT + 2 * TILE_MARGIN + 0.4);

    /** GameModesKt 里「特性」按钮的唯一一次注册调用 */
    private static final String FEATURES_BUTTON_CALL =
            "Lme/jfenn/bingo/common/menu/ButtonKt;registerTileButton$default("
                    + "Lme/jfenn/bingo/common/menu/MenuComponent;"
                    + "Lorg/joml/Vector3d;"
                    + "Ljava/lang/String;"
                    + "Lme/jfenn/bingo/platform/text/IText;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "F"
                    + "Ljava/util/List;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "Lme/jfenn/bingo/common/menu/Property;"
                    + "Ljava/lang/String;"
                    + "Lme/jfenn/bingo/platform/IDisplayEntity$Brightness;"
                    + "DD"
                    + "Lkotlin/jvm/functions/Function1;"
                    + "Lkotlin/jvm/functions/Function1;"
                    + "ILjava/lang/Object;)V";

    private static final String KEY_NAME = "random-perks-bingo.menu.perks.name";
    private static final String KEY_TOOLTIP = "random-perks-bingo.menu.perks.tooltip";
    private static final String KEY_STATE_ON = "random-perks-bingo.menu.perks.state.on";
    private static final String KEY_STATE_OFF = "random-perks-bingo.menu.perks.state.off";
    private static final String KEY_ENABLED = "random-perks-bingo.menu.perks.enabled";
    private static final String KEY_DISABLED = "random-perks-bingo.menu.perks.disabled";
    private static final String KEY_CONFLICT_INVENTORY = "random-perks-bingo.menu.perks.conflict.inventory";
    private static final String KEY_CONFLICT_TOOLTIP = "random-perks-bingo.menu.perks.conflict.tooltip";

    /** 「特性」按钮: 从整行挪到第三行右格 */
    @ModifyArg(
            method = "registerGameModes",
            at = @At(value = "INVOKE", target = FEATURES_BUTTON_CALL),
            index = 1
    )
    private static Vector3d randomperks$moveFeaturesButton(Vector3d position) {
        return new Vector3d(position).add(TILE_OFFSET_X, ROW_3_Y - FEATURES_ORIGINAL_Y, 0.0);
    }

    /** 「特性」按钮: 宽度缩成普通模式格 */
    @ModifyArg(
            method = "registerGameModes",
            at = @At(value = "INVOKE", target = FEATURES_BUTTON_CALL),
            index = 11
    )
    private static double randomperks$shrinkFeaturesWidth(double width) {
        return TILE_WIDTH;
    }

    /** 「特性」按钮: 高度改成普通模式格 */
    @ModifyArg(
            method = "registerGameModes",
            at = @At(value = "INVOKE", target = FEATURES_BUTTON_CALL),
            index = 12
    )
    private static double randomperks$shrinkFeaturesHeight(double height) {
        return TILE_HEIGHT;
    }

    /** 第三行左格: 随机词条模式开关 */
    @Inject(method = "registerGameModes", at = @At("TAIL"))
    private static void randomperks$addPerkMode(
            MenuComponent component,
            Vector3d position,
            BingoState state,
            OptionsService optionsService,
            CallbackInfo ci
    ) {
        TextProvider text = component.getText();

        IText label = text.translatable(KEY_NAME, "随机词条");
        boolean enabled = PerkConfig.INSTANCE.getEnabled();

        List<IText> tooltip = List.of(
                text.translatable(KEY_NAME, "随机词条").formatted(Formatting.GREEN),
                text.translatable(enabled ? KEY_STATE_ON : KEY_STATE_OFF,
                        enabled ? "当前: 已启用" : "当前: 已禁用"),
                text.translatable(KEY_TOOLTIP, "开启或关闭词条系统 (下一局生效)"),
                text.translatable(KEY_CONFLICT_TOOLTIP, "与物品占领模式互斥: 开启词条会关闭物品占领模式")
        );

        ButtonKt.registerIconButton(
                component,
                new Vector3d(position).add(-TILE_OFFSET_X, ROW_3_Y, 0.0),
                "🎲",
                label,
                component.<IText>computedProperty(() -> label),
                tooltip,
                component.<Boolean>computedProperty(() -> PerkConfig.INSTANCE.getEnabled()),
                TILE_WIDTH,
                TILE_HEIGHT,
                null,
                player -> {
                    boolean newEnabled = !PerkConfig.INSTANCE.getEnabled();

                    // 词条模式与物品占领模式(抢占)互斥 —— 与 bingo 自身的
                    // 「物品占领 ↔ 消耗」互斥保持一致: 后打开的那个生效。
                    boolean disabledInventory = false;
                    if (newEnabled) {
                        try {
                            BingoCardOptions options = state.getActiveCard().getOptions();
                            if (options.isInventoryMode()) {
                                options.setInventoryMode(false);
                                disabledInventory = true;
                            }
                        } catch (Throwable ignored) {
                            // 没有活动卡牌等异常不应阻止开关本身
                        }
                    }

                    PerkConfig.INSTANCE.set(newEnabled);
                    player.sendHotbarMessage(text.translatable(
                            newEnabled ? KEY_ENABLED : KEY_DISABLED,
                            newEnabled
                                    ? "[RandomPerks] 词条系统已启用 —— 将于【下一局】生效"
                                    : "[RandomPerks] 词条系统已禁用 —— 将于【下一局】生效"));
                    if (disabledInventory) {
                        player.getPlayer().sendMessage(
                                Text.translatable(KEY_CONFLICT_INVENTORY), false
                        );
                    }
                    return kotlin.Unit.INSTANCE;
                }
        );
    }
}
