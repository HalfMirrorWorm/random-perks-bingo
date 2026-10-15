package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkContext
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * "顺手牵羊"词条的服务端逻辑: 打开目标背包 / 转移物品。
 *
 * 交互: 客户端按 G → StealRequestC2S → 下发目标背包内容 → 点击某格 → StealPickC2S → 转移。
 * 一次性: 偷取成功后词条即被消耗。
 */
object StealService {

    const val PERK_ID = "seeker_pickpocket"
    const val TARGET_KEY = "pickpocket_target"

    /** 玩家背包格数 (快捷栏 9 + 主背包 27) */
    private const val SLOT_COUNT = 36

    /** 解析当前偷窃目标 (离线/不存在返回 null) */
    fun resolveTarget(player: ServerPlayerEntity, state: PlayerPerkState): ServerPlayerEntity? =
        state.memory[TARGET_KEY]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { player.server?.playerManager?.getPlayer(it) }

    /** 随机选择一名其他队伍的在线玩家 */
    private fun pickRandomTarget(player: ServerPlayerEntity): ServerPlayerEntity? {
        val server = player.server ?: return null
        val myTeam = player.scoreboardTeam?.name
        return server.playerManager.playerList
            .filter { other ->
                other.uuid != player.uuid && (myTeam == null || other.scoreboardTeam?.name != myTeam)
            }
            .randomOrNull()
    }

    /** 客户端请求打开偷窃界面 (目标在此时随机决定并锁定, 直到选择完成) */
    fun handleRequest(player: ServerPlayerEntity) {
        if (!PerkSession.isEnabled()) return

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return

        val target = pickRandomTarget(player)
        if (target == null) {
            player.sendMessage(Text.translatable("random-perks-bingo.message.steal_no_target"), true)
            return
        }
        state.memory[TARGET_KEY] = target.uuid.toString()

        val items = (0 until SLOT_COUNT).map { target.inventory.getStack(it).copy() }
        PerkNetworking.sendStealOpen(player, target.name.string, items)
    }

    /** 客户端选择了目标背包的第 index 格 */
    fun handlePick(player: ServerPlayerEntity, index: Int) {
        if (!PerkSession.isEnabled()) return
        if (index !in 0 until SLOT_COUNT) return

        val state = PerkStateManager.get(player.uuid) ?: return
        val perk = state.ownedPerks.find { it.id == PERK_ID } ?: return

        val target = resolveTarget(player, state) ?: return
        val stack = target.inventory.getStack(index)
        if (stack.isEmpty) return

        // 转移物品 (偷窃者的背包放不下则会掉落)
        val taken = target.inventory.removeStack(index)
        player.giveItemStack(taken)

        // 一次性: 消耗词条
        state.ownedPerks.remove(perk)
        state.memory.remove(TARGET_KEY)
        runCatching { perk.onRemove(PerkContext(player, state)) }
        PerkGrantService.notifyPerkListChanged(player)
        PerkGrantService.syncAbilities(player)

        player.sendMessage(
            Text.translatable("random-perks-bingo.message.steal_success", taken.name, target.name),
            false,
        )
        target.sendMessage(
            Text.translatable("random-perks-bingo.message.steal_victim", taken.name),
            false,
        )

        RandomPerksMod.logger.info(
            "[RandomPerks] {} stole {} from {}",
            player.name.string, taken.name.string, target.name.string,
        )
    }
}
