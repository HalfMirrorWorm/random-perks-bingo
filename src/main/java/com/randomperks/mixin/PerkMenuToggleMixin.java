package com.randomperks.mixin;

import com.randomperks.config.PerkConfig;
import me.jfenn.bingo.common.menu.ButtonKt;
import me.jfenn.bingo.common.menu.ConstantProperty;
import me.jfenn.bingo.common.menu.MenuComponent;
import me.jfenn.bingo.common.menu.Property;
import me.jfenn.bingo.common.menu.TitlePanelKt;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 在 bingo 大厅菜单的「特性 / Features」页里加一个「随机词条」开关。
 *
 * <p>这一页原本是 2 列 × 3 行 (预览卡牌 / 保留物品 / 鞘翅 / 夜视 / PvP / 解锁配方)。
 * bingo 2.9.6 的该页右侧还剩下 1 格宽的空位 (布局见 MenuPage.createFeaturesPage),
 * 所以这里在特性面板右侧补一列: 标题「词条」+ 一个开关按钮。
 *
 * <p>开关直接读写本 mod 的 [PerkConfig] (持久化到 config/random-perks-bingo.json),
 * 与 `/randomperks enable|disable` 完全同源; 开局时由 PerkSession 快照, 所以是「下一局生效」。
 *
 * <p>按钮点击后 bingo 的 registerButtonInteraction 会自动广播 OptionsChangedEvent,
 * 菜单随之重绘, 按钮的亮/暗状态和 tooltip 就会刷新 —— 不需要手动 markDirty。
 */
@Mixin(targets = "me.jfenn.bingo.common.menu.FeaturesKt")
public class PerkMenuToggleMixin {

    /** 特性面板中心 → 右侧空位中心的水平偏移 */
    private static final double TOGGLE_OFFSET_X = 8.10;
    private static final double TOGGLE_WIDTH = 1.0;

    private static final String KEY_TITLE = "random-perks-bingo.menu.perks.title";
    private static final String KEY_NAME = "random-perks-bingo.menu.perks.name";
    private static final String KEY_TOOLTIP = "random-perks-bingo.menu.perks.tooltip";
    private static final String KEY_STATE_ON = "random-perks-bingo.menu.perks.state.on";
    private static final String KEY_STATE_OFF = "random-perks-bingo.menu.perks.state.off";
    private static final String KEY_ENABLED = "random-perks-bingo.menu.perks.enabled";
    private static final String KEY_DISABLED = "random-perks-bingo.menu.perks.disabled";
    private static final String KEY_CONFLICT_INVENTORY = "random-perks-bingo.menu.perks.conflict.inventory";
    private static final String KEY_CONFLICT_TOOLTIP = "random-perks-bingo.menu.perks.conflict.tooltip";

    @Inject(method = "registerFeatures", at = @At("TAIL"))
    private static void randomperks$addPerkToggle(
            MenuComponent component,
            Vector3d position,
            BingoState state,
            OptionsService optionsService,
            CallbackInfo ci
    ) {
        TextProvider text = component.getText();

        IText title = text.translatable(KEY_TITLE, "词条");
        TitlePanelKt.registerTitlePanel(
                component,
                new Vector3d(position).add(TOGGLE_OFFSET_X, -0.5, 0.0),
                TOGGLE_WIDTH,
                0.3,
                title,
                new ConstantProperty<>(title)
        );

        IText label = text.translatable(KEY_NAME, "随机词条");

        // 与特性页其他按钮保持一致: 图标在上、文字在下, 避免小尺寸 tile 里图标压住文字。
        // tooltip 在菜单重绘时重建, 点击后由 bingo 的 OptionsChangedEvent 触发刷新。
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
                // 与第一行特性按钮同一高度: 标题下方 0.6 + 按钮高 0.6
                new Vector3d(position).add(TOGGLE_OFFSET_X, -1.2, 0.0),
                "🎲",
                label,
                component.<IText>computedProperty(() -> label),
                tooltip,
                component.<Boolean>computedProperty(() -> PerkConfig.INSTANCE.getEnabled()),
                TOGGLE_WIDTH,
                0.6,
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
