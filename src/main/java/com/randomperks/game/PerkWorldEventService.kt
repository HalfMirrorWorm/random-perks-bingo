package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.content.HarvestPerks
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * 把世界事件 (击杀 / 破坏方块 / 装备变化 / 实体交互) 分发给玩家已获得的词条。
 */
object PerkWorldEventService {

    /** "逃跑计划"的状态效果持续时间: 10 秒 */
    private const val ESCAPE_PLAN_TICKS = 10 * 20

    fun register() {
        // 击杀生物
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
            val killer = damageSource.attacker as? ServerPlayerEntity ?: return@register
            dispatch(killer) { ctx, perk -> perk.onKill(ctx, entity) }
        }

        // 破坏方块
        PlayerBlockBreakEvents.AFTER.register { _, player, pos, state, _ ->
            if (player !is ServerPlayerEntity) return@register
            dispatch(player) { ctx, perk -> perk.onBlockBreak(ctx, pos, state) }
        }

        // 受伤后 → "逃跑计划": 挨了生物一下就给速度 II + 抗性 I
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            if (damageTaken <= 0.0f) return@register
            // 注意: Kotlin 的 `!is` 不能像 `is` 那样在条件里声明新变量, 所以用 as?
            val player = entity as? ServerPlayerEntity ?: return@register
            // "生物伤害" = 有生物作为攻击者 (含玩家); 摔落/岩浆这类没有攻击者的不触发
            if (source.getAttacker() !is LivingEntity) return@register
            if (!PerkBoostQuery.hasEscapePlan(player)) return@register

            player.addStatusEffect(StatusEffectInstance(StatusEffects.SPEED, ESCAPE_PLAN_TICKS, 1))
            player.addStatusEffect(StatusEffectInstance(StatusEffects.RESISTANCE, ESCAPE_PLAN_TICKS, 0))
        }

        // 攻击命中 → "变群友": 把目标变成猪 (内部自带词条校验与冷却)
        AttackEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (entity is LivingEntity) MorphService.tryApply(player, entity)
            ActionResult.PASS
        }

        // "属于你的马"交互时的兼容修复 (含旧版本召唤的马):
        // 旧代码只设了主人没设驯服标志, 未驯服的马会被原版 openInventory 的
        // isTame 检查拒绝 (表现为"按 E 打不开装备界面"), 见到就补上驯服标志。
        // 其余交互 (骑乘 / 按 E 或潜行右键开装备界面 / 剪刀剪下装备) 全部交还原版处理。
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS

            // "Jockey": 右键玩家 → 骑到他身上 (失败则交还原版处理)
            if (entity is ServerPlayerEntity && PerkBoostQuery.hasJockey(player)) {
                if (JockeyService.mount(player, entity)) return@register ActionResult.SUCCESS
            }

            if (entity !is AbstractHorseEntity) return@register ActionResult.PASS
            val owner = entity.ownerReference ?: return@register ActionResult.PASS
            if (owner.uuid != player.uuid) return@register ActionResult.PASS

            if (!entity.isTame && HarvestPerks.SummonHorse.TAG in entity.commandTags) {
                entity.setTame(true)
            }

            ActionResult.PASS
        }
    }

    /** 装备变化 (由 EquipmentChangeMixin 注入原版 equipStack 调用) —— 即时重算装备相关词条 */
    fun dispatchEquipmentChange(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onEquipmentChange(ctx) }
    }

    /** 饱食度刚回满 (由 HungerRefillMixin 注入原版 HungerManager.update 调用) */
    fun dispatchHungerRefilled(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onHungerRefilled(ctx) }
    }

    private inline fun dispatch(
        player: ServerPlayerEntity,
        action: (PerkContext, PerkDefinition) -> Unit,
    ) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (state.ownedPerks.isEmpty()) return

        val ctx = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { action(ctx, perk) }
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} world-event failed", perk.id, it) }
        }
    }
}
