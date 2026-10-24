package com.randomperks.mixin;

import com.randomperks.config.PerkConfig;
import kotlin.reflect.KMutableProperty1;
import me.jfenn.bingo.common.card.BingoCard;
import me.jfenn.bingo.common.options.BingoCardOptions;
import me.jfenn.bingo.common.options.OptionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「词条模式」与 bingo 的「物品占领模式 (抢占)」互斥。
 *
 * <p>bingo 自己在 {@link OptionsService#toggleCardMode} 里实现了
 * 「物品占领模式 ↔ 消耗模式」互斥: 打开其中一个就强行关闭另一个。
 * 这里复用同一个入口, 补上反方向的约束 —— 打开物品占领模式时关闭词条系统。
 *
 * <p>菜单按钮与 `/bingo options` 命令都走这个方法, 所以这里一处即可覆盖全部入口。
 * 正方向 (打开词条时关闭物品占领) 由 {@link PerkMenuToggleMixin} 处理。
 */
@Mixin(targets = "me.jfenn.bingo.common.options.OptionsService")
public class PerkModeConflictMixin {

    private static final Logger LOG = LoggerFactory.getLogger("random-perks-bingo/mode-conflict");

    @Inject(method = "toggleCardMode", at = @At("TAIL"))
    private void randomperks$enforceModeConflict(
            OptionsService.Context ctx,
            BingoCard card,
            KMutableProperty1<BingoCardOptions, Boolean> prop,
            Boolean value,
            CallbackInfo ci
    ) {
        try {
            if (card == null || prop == null) return;
            if (!"isInventoryMode".equals(prop.getName())) return;
            // 只在「物品占领模式确实被打开」时处理
            if (!card.getOptions().isInventoryMode()) return;
            if (!PerkConfig.INSTANCE.getEnabled()) return;

            PerkConfig.INSTANCE.set(false);
            LOG.info("[RandomPerks] inventory mode enabled → perk system disabled (conflict)");
        } catch (Throwable t) {
            // 冲突处理失败绝不能影响 bingo 自己的选项逻辑
            LOG.error("[RandomPerks] failed to enforce perk/inventory mode conflict", t);
        }
    }
}
