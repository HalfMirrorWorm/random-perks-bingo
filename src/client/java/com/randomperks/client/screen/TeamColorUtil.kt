package com.randomperks.client.screen

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.util.UUID

/**
 * 客户端从玩家列表/计分板读取队伍颜色, 用于所有玩家名称的着色。
 * 26.1 的队伍颜色还是 ChatFormatting, 读不到时回退到默认白色。
 */
object TeamColorUtil {

    fun forPlayer(uuid: UUID?): Int {
        if (uuid == null) return ScreenStyle.TEXT_NAME
        val entry = Minecraft.getInstance().connection?.getPlayerInfo(uuid)
            ?: return ScreenStyle.TEXT_NAME
        return fromFormatting(entry.team?.color)
    }

    fun forPlayerName(name: String): Int {
        val handler = Minecraft.getInstance().connection ?: return ScreenStyle.TEXT_NAME
        val entry = handler.onlinePlayers.firstOrNull { it.profile.name == name }
            ?: return ScreenStyle.TEXT_NAME
        return fromFormatting(entry.team?.color)
    }

    private fun fromFormatting(color: ChatFormatting?): Int {
        val rgb = color?.color ?: return ScreenStyle.TEXT_NAME
        return 0xFF000000.toInt() or (rgb and 0xFFFFFF)
    }
}
