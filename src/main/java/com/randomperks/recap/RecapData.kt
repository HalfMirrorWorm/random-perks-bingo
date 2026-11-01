package com.randomperks.recap

import kotlinx.serialization.Serializable

/**
 * 「上局回顾」的数据模型 —— 纯数据, 服务端记录、序列化后整包发给客户端渲染。
 *
 * 时间统一用**对局开始后的秒数** (`seconds`), 客户端显示成 mm:ss;
 * 起止墙钟时间只在标题里显示个大概。
 */
@Serializable
data class RecapData(
    val gameId: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    /** 开局时的队伍快照 (id → 显示名 / 颜色), 用来给客户端上色 */
    val teams: List<RecapTeam> = emptyList(),
    val picks: List<RecapPick> = emptyList(),
    val captures: List<RecapCapture> = emptyList(),
) {
    /** 没有任何数据 (还没打完过一局) */
    val isEmpty: Boolean get() = picks.isEmpty() && captures.isEmpty()
}

@Serializable
data class RecapTeam(
    val id: String,
    val name: String,
    /** ARGB */
    val color: Int,
)

/** 某位玩家选走了一个词条 */
@Serializable
data class RecapPick(
    val seconds: Int,
    val playerUuid: String,
    val playerName: String,
    val teamId: String,
    val perkId: String,
)

/** 某队拿下了一个格子 */
@Serializable
data class RecapCapture(
    val seconds: Int,
    val teamId: String,
    val playerUuid: String,
    val playerName: String,
    val objective: String,
)
