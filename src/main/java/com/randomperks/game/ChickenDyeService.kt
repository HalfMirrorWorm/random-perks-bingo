package com.randomperks.game

import com.randomperks.network.ChickenDyeS2C
import com.randomperks.network.PerkNetworking
import com.randomperks.state.PerkStateManager
import net.minecraft.entity.passive.ChickenEntity
import net.minecraft.item.DyeItem
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * "叮咚鸡" —— 用染料右键**任意**鸡给它染色。
 *
 * 颜色本身是纯客户端表现: 服务端把 (实体 id, ARGB) 广播一次, 客户端存进
 * `com.randomperks.client.ChickenDyeState`, 渲染时给鸡的模型加 tint
 * (Model.render 有一个带 color 参数的重载)。
 *
 * 同时在鸡的 commandTags 上留一份 `randomperks_dye_<argb>`, 这样服务端随时能查回颜色
 * (客户端那份是会话级的, 区块卸载重载后会丢 —— 重载时按 tag 重新广播即可)。
 */
object ChickenDyeService {

    const val PERK_ID = "harvest_dingdong_chicken"

    private const val TAG_PREFIX = "randomperks_dye_"

    /** 手持染料右键鸡时调用; 返回 PASS 表示"这不是我管的事", 交还原版 */
    fun tryDye(player: ServerPlayerEntity, target: ChickenEntity, hand: Hand): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS

        val stack = player.getStackInHand(hand)
        val dye = stack.item as? DyeItem ?: return ActionResult.PASS

        applyDye(target, dye.color.entityColor)

        if (!player.abilities.creativeMode) stack.decrement(1)
        player.world.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ITEM_DYE_USE, SoundCategory.PLAYERS, 1.0f, 1.0f,
        )
        return ActionResult.SUCCESS
    }

    /** 染色 + 广播; 颜色同时记在 commandTags 上供服务端查回 */
    fun applyDye(chicken: ChickenEntity, color: Int) {
        chicken.commandTags.removeAll { it.startsWith(TAG_PREFIX) }
        chicken.addCommandTag(TAG_PREFIX + color)
        broadcast(chicken, color)
    }

    /** 从 commandTags 里读回颜色; 没染过返回 null */
    fun colorOf(chicken: ChickenEntity): Int? =
        chicken.commandTags.firstOrNull { it.startsWith(TAG_PREFIX) }
            ?.removePrefix(TAG_PREFIX)
            ?.toIntOrNull()

    private fun broadcast(chicken: ChickenEntity, color: Int) {
        val server = chicken.server ?: return
        val payload = ChickenDyeS2C(chicken.id, color)
        for (player in server.playerManager.playerList) {
            PerkNetworking.sendChickenDye(player, payload)
        }
    }
}
