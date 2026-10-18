package com.randomperks.game

import com.randomperks.RandomPerksMod
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * 客户端 / 服务端 mod 版本校验。
 *
 * 流程:
 * <ol>
 *   <li>玩家 JOIN → 进入"等待握手"名单, 给 5 秒宽限;</li>
 *   <li>客户端在 [net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN]
 *       里把自己的 mod 版本发过来;</li>
 *   <li>版本一致 → 放行; 不一致 → 断开并告知双方版本号;</li>
 *   <li>5 秒内没收到 → 判定为"客户端没装这个 mod", 同样断开。</li>
 * </ol>
 *
 * 这样既拦住了版本不一致, 也拦住了"服务端装了 / 客户端没装"的情况 —— 后者在游戏里
 * 表现为看不到词条 UI、无法选词条, 体验是坏的, 不如直接拒绝。
 */
object ModVersionService {

    /** 等待客户端上报版本的宽限时间 */
    private const val HANDSHAKE_TIMEOUT_TICKS = 5 * 20

    /** uuid → 握手截止 tick */
    private val pending = mutableMapOf<UUID, Long>()

    private var cachedVersion: String? = null

    /** 本端 mod 版本 (取自 fabric.mod.json 的 version) */
    fun currentVersion(): String {
        cachedVersion?.let { return it }

        val version = FabricLoader.getInstance()
            .getModContainer(RandomPerksMod.MOD_ID)
            .map { it.metadata.version.friendlyString }
            .orElse("unknown")

        cachedVersion = version
        return version
    }

    fun register() {
        // 玩家加入 → 开始倒计时
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            pending[handler.player.uuid] = server.ticks.toLong() + HANDSHAKE_TIMEOUT_TICKS
        }

        // 掉线 / 被踢 → 清理
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            pending.remove(handler.player.uuid)
        }

        // 超时未上报 → 视为客户端没装 mod
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pending.isEmpty()) return@register
            val now = server.ticks.toLong()

            for ((uuid, deadline) in pending.toList()) {
                if (now < deadline) continue
                pending.remove(uuid)

                val player = server.playerManager.getPlayer(uuid) ?: continue
                player.networkHandler.disconnect(
                    Text.translatable("random-perks-bingo.message.version_missing"),
                )
            }
        }

    }

    /** 收到客户端上报的版本 → 校验; 返回是否放行 */
    fun checkClientVersion(player: ServerPlayerEntity, clientVersion: String): Boolean {
        pending.remove(player.uuid)

        val serverVersion = currentVersion()
        if (clientVersion == serverVersion) return true

        player.networkHandler.disconnect(
            Text.translatable(
                "random-perks-bingo.message.version_mismatch",
                serverVersion,
                clientVersion,
            ),
        )
        return false
    }
}
