package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import me.jfenn.bingo.common.controller.NightVisionController;
import me.jfenn.bingo.platform.IPlayerHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * bingo 的 NightVisionController 每 tick 按自己的配置"管制"玩家的夜视
 * (不符就 removeEffect) —— 它不认识我们的"地下视野"词条, 会把我们的 ∞ 夜视
 * 反复剥掉, 造成"夜视一闪一闪/一下就没"。
 *
 * 这里让管制器对**持有该词条的玩家**直接放行; 词条在对局结束被清掉后,
 * 管制逻辑自动恢复正常。
 */
@Mixin(NightVisionController.class)
public class NightVisionControllerMixin {

    @Inject(method = "updateNightVision", at = @At("HEAD"), cancellable = true)
    private void randomperks$skipPerkHolders(IPlayerHandle playerHandle, CallbackInfo ci) {
        if (PerkBoostQuery.INSTANCE.hasNightVisionPerk(playerHandle.getPlayer())) {
            ci.cancel();
        }
    }
}
