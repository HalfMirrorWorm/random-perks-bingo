package com.randomperks.state

import com.randomperks.RandomPerksMod
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkSession
import com.randomperks.game.RewindService
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRegistry
import com.randomperks.server.ServerHolder
import me.jfenn.bingo.api.BingoEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * 词条状态的**跨重启持久化**。
 *
 * <h3>要解决的问题</h3>
 * [PerkStateManager] 是纯内存态。对局进行中服务器被关闭时, bingo 自己的对局存档还在
 * (重启后仍处于 PLAYING), 玩家的词条却全没了 —— 而且 bingo 只有在**状态真正变化**进入
 * PLAYING 时才会发 GAME_STARTED (见 `ScopedEvents.onEnter(..., requireChange = true)`),
 * 重启时它只是把存档里的状态原样重放一遍, 所以我们这边没有任何"重新开局"的信号。
 *
 * <h3>做法</h3>
 * <ul>
 *   <li>保存: 服务器停止前 (SERVER_STOPPING) 写一份 NBT 到存档目录; 对局中每 30 秒再兜底
 *       写一次, 这样即便进程被强杀, 最多也只丢 30 秒。</li>
 *   <li>读取: bingo 初始化完成 (BingoEvents.INIT) 后, 若存档里的对局仍处于进行中
 *       (LOADING / COUNTDOWN / PLAYING) 就把状态读回来; 否则说明那份文件是上一局的残留, 直接删掉。</li>
 *   <li>删除: 对局结束 / 重置 / 新开局时删除文件, 避免旧数据被套到下一局。</li>
 * </ul>
 *
 * <p>读回状态后, 玩家加入时补跑一次 `onRespawn` + `onPerkListChanged` ——
 * 属性修饰符 (临时修饰符不写进玩家存档) 这类效果需要重挂。
 *
 * <p>存档位置: `<世界目录>/random-perks-bingo-perks.nbt`。
 */
object PerkStatePersistence {

    private const val FILE_NAME = "random-perks-bingo-perks.nbt"
    private const val KEY_VERSION = "version"
    private const val CURRENT_VERSION = 1

    /** 兜底自动保存间隔 (tick): 30 秒 */
    private const val AUTOSAVE_TICKS = 20 * 30

    /** 本局是否在进行中 (由 bingo 生命周期维护; 服务器停止时据此决定存还是删) */
    @Volatile
    private var gameActive = false

    private var nextAutosaveTick = 0

    /** 服务器启动后还没做首次检查 (INIT 只做标记, 真正恢复放到第一 tick, 见 [restoreIfGameRunning]) */
    private var pendingRestoreCheck = false

    /** 从文件读回状态的玩家: 加入时补挂一次词条效果 */
    private val pendingApply = mutableSetOf<UUID>()

    fun register() {
        // bingo 在服务器启动时先读回 BingoState, 之后才发 INIT。这里只打标记, 真正的恢复
        // 放到第一 tick —— INIT 阶段 ServerHolder 有没有赋值取决于 SERVER_STARTED 监听器的
        // 执行顺序, 跨 mod 并不保证。
        // ★ 必须注册在 RewindService 之后: 它先反射拿到 BingoState, 本类要靠它判断对局是否还在进行
        BingoEvents.INIT.register { pendingRestoreCheck = true }

        BingoEvents.GAME_STARTED.register {
            gameActive = true
            nextAutosaveTick = 0
            deleteFile(ServerHolder.server)   // 新的一局: 丢掉上一局可能残留的文件
        }
        BingoEvents.GAME_ENDED.register { onGameFinished() }
        BingoEvents.GAME_RESET.register { onGameFinished() }
        // 注意: CLOSE 在服务器停止时也会触发 (SERVER_STOPPED), 比 SERVER_STOPPING 晚 ——
        // 不能在这里删文件, 否则刚存下来的状态会被清掉

        ServerLifecycleEvents.SERVER_STOPPING.register { saveOrDelete() }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (pendingRestoreCheck) {
                pendingRestoreCheck = false
                restoreIfGameRunning(server)
            }
            autosave(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (pendingApply.remove(player.uuid)) {
                player.entityWorld.server?.execute { applyPerks(player) }
            }
        }
    }

    private fun onGameFinished() {
        if (!gameActive) return
        gameActive = false
        pendingApply.clear()
        deleteFile(ServerHolder.server)
    }

    // ------------------------------------------------------------------
    // save
    // ------------------------------------------------------------------

    private fun autosave(server: MinecraftServer) {
        if (!gameActive) return
        if (server.ticks < nextAutosaveTick) return
        nextAutosaveTick = server.ticks + AUTOSAVE_TICKS
        write(server)
    }

    private fun saveOrDelete() {
        val server = ServerHolder.server ?: return
        if (gameActive) write(server) else deleteFile(server)
    }

    private fun write(server: MinecraftServer) {
        val path = statePath(server) ?: return
        val root = NbtCompound()
        root.putInt(KEY_VERSION, CURRENT_VERSION)
        root.putBoolean("enabled", PerkSession.isEnabled())

        val players = NbtList()
        for (state in PerkStateManager.all()) {
            val entry = NbtCompound()
            entry.putString("uuid", state.uuid.toString())

            val pending = NbtList()
            for (offer in state.pendingOffers) {
                val inner = NbtList()
                offer.forEach { inner.add(NbtString.of(it)) }
                pending.add(inner)
            }
            entry.put("pending", pending)

            val owned = NbtList()
            state.ownedPerks.forEach { owned.add(NbtString.of(it.id)) }
            entry.put("owned", owned)

            val used = NbtList()
            state.usedPerks.forEach { used.add(NbtString.of(it)) }
            entry.put("used", used)

            val cooldowns = NbtCompound()
            state.cooldowns.forEach { (id, tick) -> cooldowns.putLong(id, tick) }
            entry.put("cooldowns", cooldowns)

            val memory = NbtCompound()
            state.memory.forEach { (key, value) -> memory.putString(key, value) }
            entry.put("memory", memory)

            players.add(entry)
        }
        root.put("players", players)

        try {
            val tmp = path.resolveSibling("${path.fileName}.tmp")
            NbtIo.write(root, tmp)
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[RandomPerks] failed to save perk state", t)
        }
    }

    // ------------------------------------------------------------------
    // restore
    // ------------------------------------------------------------------

    private fun restoreIfGameRunning(server: MinecraftServer) {
        val bingo = RewindService.currentBingoState()
        if (bingo == null) {
            RandomPerksMod.logger.warn("[RandomPerks] bingo state unavailable; keeping the perk state file")
            return
        }

        // 对局没在运行: 这份文件属于上一局, 丢掉
        if (!bingo.state.isPlayingOrCountdown) {
            deleteFile(server)
            gameActive = false
            return
        }
        gameActive = true

        val path = statePath(server) ?: return
        if (!Files.exists(path)) return

        val root = try {
            NbtIo.read(path)
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[RandomPerks] failed to read perk state file", t)
            return
        } ?: return

        try {
            restore(server, root)
        } catch (t: Throwable) {
            RandomPerksMod.logger.error("[RandomPerks] failed to restore perk state", t)
            return
        }

        nextAutosaveTick = server.ticks + AUTOSAVE_TICKS
        RandomPerksMod.logger.info(
            "[RandomPerks] restored perk state for {} player(s) from the previous server run",
            pendingApply.size,
        )
    }

    private fun restore(server: MinecraftServer, root: NbtCompound) {
        PerkSession.restoreRound(root.getBoolean("enabled", false))

        val players = root.getListOrEmpty("players")
        for (i in 0 until players.size) {
            val entry = players.getCompoundOrEmpty(i)
            val uuid = runCatching { UUID.fromString(entry.getString("uuid", "")) }.getOrNull() ?: continue

            val state = PerkStateManager.getOrCreate(uuid)
            state.reset()

            val pending = entry.getListOrEmpty("pending")
            for (j in 0 until pending.size) {
                val inner = pending.getListOrEmpty(j)
                val offer = ArrayList<String>(inner.size)
                for (k in 0 until inner.size) {
                    val id = inner.getString(k, "")
                    if (id.isNotEmpty()) offer.add(id)
                }
                if (offer.isNotEmpty()) state.pendingOffers.add(offer)
            }

            val owned = entry.getListOrEmpty("owned")
            for (j in 0 until owned.size) {
                val id = owned.getString(j, "")
                val perk = PerkRegistry.byId(id) ?: continue
                state.ownedPerks.add(perk)
            }

            val used = entry.getListOrEmpty("used")
            for (j in 0 until used.size) {
                val id = used.getString(j, "")
                if (id.isNotEmpty()) state.usedPerks.add(id)
            }

            val cooldowns = entry.getCompoundOrEmpty("cooldowns")
            for (key in cooldowns.getKeys()) {
                state.cooldowns[key] = cooldowns.getLong(key, 0L)
            }

            val memory = entry.getCompoundOrEmpty("memory")
            for (key in memory.getKeys()) {
                state.memory[key] = memory.getString(key, "")
            }

            if (state.ownedPerks.isNotEmpty() || state.pendingOffers.isNotEmpty()) {
                pendingApply.add(uuid)
            }
        }

        // 服务器刚启动, 通常没有在线玩家; 万一有, 直接补挂
        for (uuid in pendingApply.toList()) {
            val player = server.playerManager.getPlayer(uuid) ?: continue
            pendingApply.remove(uuid)
            applyPerks(player)
        }
    }

    /**
     * 重挂词条效果。
     *
     * 需要补跑的原因: 属性修饰符分"临时/持久"两种, 词条用的都是**临时**修饰符,
     * 它们不会写进玩家存档, 重启后自然消失; 而永久状态效果是写进存档的, 但为了让
     * "顺风而行"之类的联动重新按当前词条列表结算, 也一并跑一次 onPerkListChanged。
     */
    private fun applyPerks(player: ServerPlayerEntity) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (state.ownedPerks.isEmpty()) {
            PerkGrantService.syncAbilities(player)
            return
        }

        val ctx = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { perk.onRespawn(ctx) }
                .onFailure { RandomPerksMod.logger.warn("[RandomPerks] perk {} onRespawn failed after restore", perk.id, it) }
        }
        for (perk in state.ownedPerks.toList()) {
            runCatching { perk.onPerkListChanged(ctx) }
                .onFailure { RandomPerksMod.logger.warn("[RandomPerks] perk {} list-change failed after restore", perk.id, it) }
        }
        PerkGrantService.syncAbilities(player)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun statePath(server: MinecraftServer): Path? = try {
        server.getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME)
    } catch (t: Throwable) {
        RandomPerksMod.logger.warn("[RandomPerks] could not resolve the save path", t)
        null
    }

    private fun deleteFile(server: MinecraftServer?) {
        val path = server?.let { statePath(it) } ?: return
        try {
            Files.deleteIfExists(path)
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[RandomPerks] failed to delete the perk state file", t)
        }
    }
}
