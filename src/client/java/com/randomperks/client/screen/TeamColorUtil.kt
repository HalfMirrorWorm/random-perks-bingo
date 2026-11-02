package com.randomperks.client.screen

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.TeamColor
import java.util.Optional
import java.util.UUID

/**
 * 客户端从玩家列表/计分板读取队伍颜色, 用于所有玩家名称的着色。
 * 读不到队伍时回退到默认白色。
 */
object TeamColorUtil {

    fun forPlayer(uuid: UUID?): Int {
        if (uuid == null) return ScreenStyle.TEXT_NAME
        val entry = Minecraft.getInstance().connection?.getPlayerInfo(uuid)
            ?: return ScreenStyle.TEXT_NAME
        return fromTeamColor(entry.team?.color)
    }

    fun forPlayerName(name: String): Int {
        val handler = Minecraft.getInstance().connection ?: return ScreenStyle.TEXT_NAME
        val entry = handler.onlinePlayers.firstOrNull { it.profile.name == name }
            ?: return ScreenStyle.TEXT_NAME
        return fromTeamColor(entry.team?.color)
    }

    private fun fromTeamColor(color: Optional<TeamColor>?): Int {
        val rgb = color?.orElse(null)?.rgb() ?: return ScreenStyle.TEXT_NAME
        return 0xFF000000.toInt() or (rgb and 0xFFFFFF)
    }
}
