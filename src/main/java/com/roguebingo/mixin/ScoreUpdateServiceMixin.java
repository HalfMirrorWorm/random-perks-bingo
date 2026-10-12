package com.roguebingo.mixin;

import com.roguebingo.hook.BingoHook;
import me.jfenn.bingo.common.card.BingoCard;
import me.jfenn.bingo.common.card.objective.BingoObjective;
import me.jfenn.bingo.common.scoring.ScoreUpdateService;
import me.jfenn.bingo.common.team.BingoTeam;
import me.jfenn.bingo.platform.player.PlayerProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 bingo 的"格子完成"计分点。
 *
 * <p>{@code ScoreUpdateService.sendItemCaptured} 在"某队拿下某个格子"时被调用一次,
 * 参数携带该队的完整信息 (队伍 / 成员 / 格子 / 捕获者)。
 *
 * <p>注意: 这个类必须用 Java 写 —— bingo 的 {@code ScoreUpdateService} 是 Kotlin
 * {@code internal} 声明, Kotlin 编译器不允许跨模块访问, 但字节码层面它是 public,
 * 因此 Java 可以正常引用。
 */
@Mixin(ScoreUpdateService.class)
public class ScoreUpdateServiceMixin {

    /** 探针: 确认 bingo 的计分服务已被本 mixin 接管 (类加载时可见) */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void roguebingo$onConstructed(CallbackInfo ci) {
        BingoHook.logHookApplied();
    }

    @Inject(method = "sendItemCaptured", at = @At("HEAD"))
    private void roguebingo$onItemCaptured(
            PlayerProfile scoredPlayer,
            BingoTeam team,
            BingoObjective objective,
            BingoCard card,
            CallbackInfo ci
    ) {
        try {
            BingoHook.onItemCaptured(scoredPlayer, team, objective, card);
        } catch (Throwable t) {
            // 扩展自身的异常绝不能破坏 bingo 的计分主流程
            BingoHook.logFailure(t);
        }
    }
}
