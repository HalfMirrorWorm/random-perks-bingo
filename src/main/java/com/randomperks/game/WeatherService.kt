package com.randomperks.game

import com.randomperks.network.PerkNetworking
import com.randomperks.state.PerkStateManager
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.WeatherData

/**
 * "世界一直下雨"词条的服务端逻辑。
 *
 * <p>一次性: 玩家在词条列表点击 → 下发天气选择器 → 玩家选一种天气 → 改**主世界**天气并记为已用。
 * 天气是全局的, 所以再广播一条消息告诉其他人是谁改的。
 */
object WeatherService {

    const val PERK_ID = "harvest_always_rain"

    /** 天气持续时间: 10 分钟 */
    private const val DURATION_TICKS = 10 * 60 * 20

    const val CLEAR = 0
    const val RAIN = 1
    const val THUNDER = 2

    /** 玩家在 V 界面点击词条: 下发选择器 (已用过就不再弹) */
    fun handleRequest(player: ServerPlayer) {
        if (!PerkSession.isEnabled()) return

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return
        if (state.isUsed(PERK_ID)) return

        PerkNetworking.sendWeatherSelect(player)
    }

    /** 客户端选定了天气 (0 晴 / 1 雨 / 2 雷雨) */
    fun handlePick(player: ServerPlayer, weather: Int) {
        if (!PerkSession.isEnabled()) return
        if (weather !in CLEAR..THUNDER) return   // 服务端校验, 不信客户端的序号

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return
        if (state.isUsed(PERK_ID)) return

        val server = player.level().getServer()
        val world = server.getLevel(Level.OVERWORLD) ?: return

        val data = world.weatherData
        when (weather) {
            CLEAR -> applyWeather(data, DURATION_TICKS, 0, raining = false, thundering = false)
            RAIN -> applyWeather(data, 0, DURATION_TICKS, raining = true, thundering = false)
            else -> applyWeather(data, 0, DURATION_TICKS, raining = true, thundering = true)
        }

        state.markUsed(PERK_ID)
        PerkGrantService.notifyPerkListChanged(player)
        PerkGrantService.syncAbilities(player)

        val messageKey = when (weather) {
            CLEAR -> "random-perks-bingo.message.weather_clear"
            RAIN -> "random-perks-bingo.message.weather_rain"
            else -> "random-perks-bingo.message.weather_thunder"
        }
        player.sendSystemMessage(Component.translatable(messageKey), true)

        // 天气是全局的: 广播让其他玩家知道是谁改的
        server.playerList.broadcastSystemMessage(
            Component.translatable("random-perks-bingo.message.weather_broadcast", player.name),
            false,
        )
    }

    /** 对齐 1.21 的 setWeatherParameters: 雷雨时长跟下雨时长走同一值。 */
    private fun applyWeather(
        data: WeatherData,
        clearTime: Int,
        rainTime: Int,
        raining: Boolean,
        thundering: Boolean,
    ) {
        data.clearWeatherTime = clearTime
        data.rainTime = rainTime
        data.thunderTime = rainTime
        data.isRaining = raining
        data.isThundering = thundering
    }
}
