package com.randomperks.game

import com.randomperks.RandomPerksMod
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 客户端 / 服务端 mod 版本校验 —— 走 Fabric 的**登录阶段**查询机制。
 *
 * 流程:
 * <ol>
 *   <li>登录开始 (QUERY_START) → 服务端发一个带版本号的 query;</li>
 *   <li>客户端在 [ClientLoginNetworking] 里回一个带自己版本号的响应;</li>
 *   <li>服务端收到后比对: 不一致就 disconnect; 客户端没装 mod 时 understood = false, 同样 disconnect。</li>
 * </ol>
 *
 * 走 LOGIN 而不是 PLAY 的好处: 校验发生在玩家**进入世界之前**, 失败的客户端会停在登录界面
 * 看到原因, 而不是先加载进世界再被踢出去; 也不需要超时兜底 (登录流程会等所有 query 完成)。
 */
object ModVersionService {

    /** 登录阶段的自定义 channel */
    val CHANNEL: Identifier = Identifier.of(RandomPerksMod.MOD_ID, "mod_version")

    @Volatile
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

    fun registerServer() {
        // 登录开始 → 把服务端版本发给客户端
        ServerLoginConnectionEvents.QUERY_START.register { _, _, sender, _ ->
            val buf = PacketByteBufs.create()
            buf.writeString(currentVersion())
            sender.sendPacket(CHANNEL, buf)
        }

        // 收到客户端的响应 → 比对
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL) { _, listener, understood, buf, _, _ ->
            if (!understood) {
                // 客户端没装这个 mod (或者不认识这个 channel)
                listener.disconnect(Text.translatable("random-perks-bingo.message.version_missing"))
                return@registerGlobalReceiver
            }

            val clientVersion = runCatching { buf.readString() }.getOrNull() ?: "unknown"
            val serverVersion = currentVersion()
            if (clientVersion != serverVersion) {
                listener.disconnect(
                    Text.translatable(
                        "random-perks-bingo.message.version_mismatch",
                        serverVersion,
                        clientVersion,
                    ),
                )
            }
        }
    }
}
