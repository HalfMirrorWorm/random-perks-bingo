package com.randomperks.hook;

import me.jfenn.bingo.common.card.BingoCard;
import me.jfenn.bingo.common.card.objective.BingoObjective;
import me.jfenn.bingo.common.team.BingoTeam;
import me.jfenn.bingo.platform.player.PlayerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * bingo 内部类型 → 纯数据 的转换层。
 *
 * <p>这一层 (连同 mixin) 是本 mod 唯一依赖 bingo 内部类的部分;
 * 所有游戏逻辑都在 Kotlin 侧, 只接收 UUID / String 等纯数据。
 * 这样 bingo 内部结构变化时, 需要改动的面被限制在这个包里。
 */
public final class BingoHook {

    private static final Logger LOG = LoggerFactory.getLogger("random-perks-bingo/hook");

    private BingoHook() {
    }

    public static void onItemCaptured(
            PlayerProfile scoredPlayer,
            BingoTeam team,
            BingoObjective objective,
            BingoCard card
    ) {
        if (team == null || objective == null) return;

        List<UUID> members = new ArrayList<>();
        for (PlayerProfile profile : team.getPlayers()) {
            members.add(profile.getUuid());
        }

        RandomPerksHooks.INSTANCE.onTeamScored(
                team.getId(),
                members,
                scoredPlayer != null ? scoredPlayer.getUuid() : null,
                objective.getId()
        );
    }

    public static void logHookApplied() {
        LOG.info("[RandomPerks] mixin applied to bingo ScoreUpdateService — scoring hook is ACTIVE");
    }

    public static void logFailure(Throwable t) {
        LOG.error("[RandomPerks] unexpected error in scoring hook", t);
    }
}
