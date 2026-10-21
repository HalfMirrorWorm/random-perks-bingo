package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.effect.PerkEffects
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * "顺手牵羊"词条的服务端逻辑。
 *
 * 流程: 玩家在 V 界面点击该词条 → 下发候选玩家列表 → 玩家在选择器里点一名 → 下发他的背包 →
 * 点一格偷走。一次性: 偷取成功后词条即记为已用。
 */
object StealService {

    const val PERK_ID = "seeker_pickpocket"
    const val TARGET_KEY = "pickpocket_target"

    /** 玩家背包格数 (快捷栏 9 + 主背包 27) */
    private const val SLOT_COUNT = 36

    /** 解析已锁定的偷窃目标 (离线/不存在返回 null) */
    fun resolveTarget(player: ServerPlayerEntity, state: PlayerPerkState): ServerPlayerEntity? =
        state.memory[TARGET_KEY]
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { player.server?.playerManager?.getPlayer(it) }

    /** 候选目标: 其他队伍的在线玩家 (以原版队伍判断归属; 无队伍时视为全体为敌); 排除旁观者 */
    private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> {
        val server = player.server ?: return emptyList()
        val myTeam = player.scoreboardTeam?.name
        return server.playerManager.playerList.filter { other ->
            !other.isSpectator &&
                other.uuid != player.uuid &&
                (myTeam == null || other.scoreboardTeam?.name != myTeam)
        }
    }

    /** 玩家点击词条时调用: 下发候选玩家列表, 由玩家自己挑 (没有候选就提示) */
    fun handleRequest(player: ServerPlayerEntity) {
        if (!PerkSession.isEnabled()) return

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return
        if (state.isUsed(PERK_ID)) return   // 已经偷过: 不再弹选择器

        val targets = candidates(player).map { PlayerTargetEntry(it.uuid, it.name.string) }
        if (targets.isEmpty()) {
            player.sendMessage(Text.translatable("random-perks-bingo.message.steal_no_target"), true)
            return
        }
        PerkNetworking.sendStealTargets(player, targets)
    }

    /** 客户端在选择器里选中了一名玩家 → 锁定并下发他的背包 */
    fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
        if (!PerkSession.isEnabled()) return

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return

        // 校验: 必须是当下的合法候选 —— 否则客户端伪造 uuid 就能偷任意人 (含队友/离线者)
        val target = candidates(player).firstOrNull { it.uuid == uuid }
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
        if (!state.hasPerk(PERK_ID)) return
        if (state.isUsed(PERK_ID)) return   // 已经偷过

        val target = resolveTarget(player, state)
        if (target == null) {
            // 目标已离线: 给个提示 (以前是静默无反应, 玩家点了会以为卡了)
            player.sendMessage(Text.translatable("random-perks-bingo.message.steal_no_target"), true)
            return
        }
        val taken = target.inventory.removeStack(index)
        if (taken.isEmpty) return

        // ★ 必须在 giveStack **之前**取出物品名与玩家名:
        //   giveStack → PlayerInventory.offerOrDrop 会把这一栈的数量清成 0 (物品已全进背包),
        //   之后再读 taken.name 就变成"空气" —— 这正是之前提示一直显示"偷到了 空气!"的原因
        //   (受害者的提示同样受影响)。
        val takenName = taken.name.copy()
        val targetName = target.name.copy()

        // 转移物品 (PerkEffects.giveStack 内部用 offerOrDrop: 偷窃者的背包放不下会掉在脚下, 不会蒸发)
        PerkEffects.giveStack(player, taken)

        // 一次性: 记为已用。**不移除词条** —— 词条继续留在列表里, 抽卡时天然处于排除集,
        // 所以同一局不会再次获得 (也就不需要"局内移出词条"这件事)
        state.markUsed(PERK_ID)
        state.memory.remove(TARGET_KEY)
        PerkGrantService.notifyPerkListChanged(player)
        PerkGrantService.syncAbilities(player)

        player.sendMessage(
            Text.translatable("random-perks-bingo.message.steal_success", takenName, targetName),
            false,
        )
        // 受害者提示: 聊天栏 + actionbar 各发一次 —— 聊天栏很容易被其他消息刷掉/滚出屏幕,
        // actionbar 就在准星下方, 不会错过
        val victimMessage = Text.translatable("random-perks-bingo.message.steal_victim", takenName)
        target.sendMessage(victimMessage, false)
        target.sendMessage(victimMessage, true)

        RandomPerksMod.logger.info(
            "[RandomPerks] {} stole {} from {} (victim notified)",
            player.name.string, takenName.string, targetName.string,
        )
    }
}
