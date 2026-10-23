package com.randomperks.client.screen

import net.minecraft.client.MinecraftClient
import net.minecraft.util.Formatting
import java.util.UUID

/**
 * 客户端从玩家列表/计分板读取队伍颜色, 用于所有玩家名称的着色。
 * 读不到队伍时回退到默认白色。
 */
object TeamColorUtil {

    fun forPlayer(uuid: UUID?): Int {
        if (uuid == null) return ScreenStyle.TEXT_NAME
        val entry = MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(uuid)
            ?: return ScreenStyle.TEXT_NAME
        return fromTeamColor(entry.scoreboardTeam?.color)
    }

    fun forPlayerName(name: String): Int {
        val handler = MinecraftClient.getInstance().networkHandler ?: return ScreenStyle.TEXT_NAME
        val entry = handler.playerList.firstOrNull { it.profile.name == name }
            ?: return ScreenStyle.TEXT_NAME
        return fromTeamColor(entry.scoreboardTeam?.color)
    }

    private fun fromTeamColor(color: Formatting?): Int {
        val rgb = color?.colorValue ?: return ScreenStyle.TEXT_NAME
        return 0xFF000000.toInt() or (rgb and 0xFFFFFF)
    }
}
