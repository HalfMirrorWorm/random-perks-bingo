package com.randomperks.game

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

/**
 * "何易位" (紫 · 唤法): 选择一名玩家, **立即**传送到他的位置 (可跨维度)。
 *
 * 没有冷却, 也没有前置延迟 —— 选中目标后马上结算。词条本身是一次性的
 * (由 [com.randomperks.perk.PerkDefinition.isOneTime] + PerkStateManager.markUsed 控制)。
 */
object SwapService {

    const val PERK_ID = "summon_swap"

    /**
     * 把施放者传送到目标当前所在的位置。
     *
     * @return true 表示传送成功
     */
    fun teleportTo(caster: ServerPlayerEntity, target: ServerPlayerEntity): Boolean {
        if (caster === target) return false
        if (!target.isAlive || target.isRemoved || target.isSpectator) return false

        // 骑着东西的话先下来, 否则载具会留在原地
        caster.stopRiding()

        caster.teleport(
            target.world,
            target.x, target.y, target.z,
            emptySet(),
            caster.yaw,
            caster.pitch,
            true,
        )

        caster.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_teleported", target.name),
            false,
        )
        target.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_arrived", caster.name),
            false,
        )
        return true
    }

    /** 对局重置: 现在没有需要清理的持续状态, 保留该入口与生命周期调用保持一致 */
    fun clear() {
    }
}
