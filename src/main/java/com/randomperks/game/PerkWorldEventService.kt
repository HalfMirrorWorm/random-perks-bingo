package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.content.HarvestPerks
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.CropBlock
import net.minecraft.block.NetherWartBlock
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.entity.passive.ChickenEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * 把世界事件 (击杀 / 破坏方块 / 装备变化 / 实体交互) 分发给玩家已获得的词条。
 */
object PerkWorldEventService {

    /** "逃跑计划"的状态效果持续时间: 10 秒 */
    private const val ESCAPE_PLAN_TICKS = 10 * 20

    /** "拾穗"的额外掉落概率 */
    private const val SEED_BONUS_CHANCE = 0.5f

    /** "拾穗"可开出的种子池 */
    private val SEED_POOL: List<Item> = listOf(
        Items.WHEAT_SEEDS,
        Items.BEETROOT_SEEDS,
        Items.PUMPKIN_SEEDS,
        Items.MELON_SEEDS,
        Items.TORCHFLOWER_SEEDS,
        Items.PITCHER_POD,
        Items.NETHER_WART,
        Items.CARROT,
        Items.POTATO,
    )

    /** 是否是成熟作物 (小麦/胡萝卜/马铃薯/甜菜 + 下界疣) */
    private fun isMatureCrop(state: BlockState): Boolean {
        val block = state.block
        if (block is CropBlock) return block.isMature(state)
        if (block is NetherWartBlock) return state.get(NetherWartBlock.AGE) >= 3
        return false
    }

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

        // 收获成熟作物 → "拾穗": 50% 概率额外掉一个随机种子
        // (注意: 这一块原先被误嵌在下面的 UseEntityCallback lambda 里, 结果是每右键一次实体
        //  就多注册一个监听器 —— 拾穗的额外掉落会随右键次数翻倍, 监听器也永远不释放。)
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            if (player !is ServerPlayerEntity) return@register
            if (!PerkBoostQuery.hasGleaning(player)) return@register
            if (!isMatureCrop(state)) return@register
            if (player.random.nextFloat() >= SEED_BONUS_CHANCE) return@register

            Block.dropStack(world, pos, ItemStack(SEED_POOL[player.random.nextInt(SEED_POOL.size)]))
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

        // 右键方块 → "切洋葱": 手持镐在黑曜石 / 哭泣的黑曜石之间切换
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClient) return@register ActionResult.PASS
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            OnionCutService.handleUse(player, world as ServerWorld, hitResult.blockPos)
        }

        // "属于你的马"交互时的兼容修复 (含旧版本召唤的马):
        // 旧代码只设了主人没设驯服标志, 未驯服的马会被原版 openInventory 的
        // isTame 检查拒绝 (表现为"按 E 打不开装备界面"), 见到就补上驯服标志。
        // 其余交互 (骑乘 / 按 E 或潜行右键开装备界面 / 剪刀剪下装备) 全部交还原版处理。
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS

            // "叮咚鸡": 手持染料右键鸡 → 给它染色 (原版染料对鸡没有反应)
            if (entity is ChickenEntity) {
                val dyed = ChickenDyeService.tryDye(player, entity, hand)
                if (dyed != ActionResult.PASS) return@register dyed
            }

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

    /** 吃完一份食物 (由 ConsumableBoostMixin 注入原版 ConsumableComponent.finishConsumption 调用) */
    fun dispatchFoodEaten(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onFoodEaten(ctx) }
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
