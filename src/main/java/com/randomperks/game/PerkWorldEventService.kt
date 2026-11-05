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
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.NetherWartBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

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

    /** 是否是成熟作物 (小麦/胡萝卜/马铃薯/甜菜 + 下界疣) —— 与"田园修补"共用判定 */
    private fun isMatureCrop(state: BlockState): Boolean = PerkBoostQuery.isMatureCrop(state)

    fun register() {
        // 击杀生物
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
            val killer = damageSource.entity as? ServerPlayer ?: return@register
            dispatch(killer) { ctx, perk -> perk.onKill(ctx, entity) }
        }

        // 破坏方块
        PlayerBlockBreakEvents.AFTER.register { _, player, pos, state, _ ->
            if (player !is ServerPlayer) return@register
            dispatch(player) { ctx, perk -> perk.onBlockBreak(ctx, pos, state) }
        }

        // 收获成熟作物 → "拾穗": 50% 概率额外掉一个随机种子
        // (注意: 这一块原先被误嵌在下面的 UseEntityCallback lambda 里, 结果是每右键一次实体
        //  就多注册一个监听器 —— 拾穗的额外掉落会随右键次数翻倍, 监听器也永远不释放。)
        PlayerBlockBreakEvents.AFTER.register { world, player, pos, state, _ ->
            if (player !is ServerPlayer) return@register
            if (!PerkBoostQuery.hasGleaning(player)) return@register
            if (!isMatureCrop(state)) return@register
            if (player.random.nextFloat() >= SEED_BONUS_CHANCE) return@register

            Block.popResource(world, pos, ItemStack(SEED_POOL[player.random.nextInt(SEED_POOL.size)]))
        }

        // 受伤后 → "逃跑计划": 挨了生物一下就给速度 II + 抗性 I
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            if (damageTaken <= 0.0f) return@register
            // 注意: Kotlin 的 `!is` 不能像 `is` 那样在条件里声明新变量, 所以用 as?
            val player = entity as? ServerPlayer ?: return@register
            // "生物伤害" = 有生物作为攻击者 (含玩家); 摔落/岩浆这类没有攻击者的不触发
            if (source.entity !is LivingEntity) return@register
            if (!PerkBoostQuery.hasEscapePlan(player)) return@register

            player.addEffect(MobEffectInstance(MobEffects.SPEED, ESCAPE_PLAN_TICKS, 1))
            player.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ESCAPE_PLAN_TICKS, 0))
        }

        // 攻击敌方玩家 → "苔藓侵蚀": 随机转化对方 2 件物品
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, _, damageTaken, _ ->
            if (damageTaken <= 0.0f) return@register
            val victim = entity as? ServerPlayer ?: return@register
            val attacker = source.entity as? ServerPlayer ?: return@register
            FungalInfectionService.handleAttack(attacker, victim)
        }

        // 受到玩家伤害 → "盘丝大仙": 蜘蛛获得 5 秒盘丝 (织网) 效果。
        // 必须在结算**之前**挂上: AFTER_DAMAGE 在致死一击时不触发,
        // 而盘丝的织网正是在死亡时结算的 —— 后挂就等于一击秒杀永远不出网。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            if (amount <= 0.0f) return@register true
            val spider = entity as? Spider ?: return@register true
            val attacker = source.entity as? ServerPlayer ?: return@register true
            if (!PerkBoostQuery.hasSpiderWeaving(attacker)) return@register true
            spider.addEffect(MobEffectInstance(MobEffects.WEAVING, 5 * 20, 0))
            true
        }

        // 受到伤害 → "忠心护主": 由附近属于该玩家的宠物承受 2 倍伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            val player = entity as? ServerPlayer ?: return@register true
            !LoyalGuardService.tryRedirect(player, source, amount)
        }

        // 受到生物伤害 → "刺猬": 把 75% 伤害反弹给攻击者。
        // 用 ALLOW_DAMAGE 在结算前反弹: AFTER_DAMAGE 在致死一击时不触发, 否则最后一击白挨。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            if (amount <= 0.0f) return@register true
            val player = entity as? ServerPlayer ?: return@register true
            if (!PerkBoostQuery.hasHedgehog(player)) return@register true
            // 反弹本身也走 THORNS 伤害; 跳过它, 避免两个刺猬互相递归
            if (source.`is`(DamageTypes.THORNS)) return@register true
            val attacker = source.entity as? LivingEntity ?: return@register true
            if (attacker === player) return@register true
            val attackerWorld = attacker.level() as? ServerLevel ?: return@register true

            attacker.hurtServer(
                attackerWorld,
                attackerWorld.damageSources().thorns(player),
                amount * 0.75f,
            )
            true
        }

        // 右键方块 → "切洋葱" / "海带成骨" / "点石成金"
        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClientSide) return@register InteractionResult.PASS
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            val serverWorld = world as ServerLevel
            val onion = OnionCutService.handleUse(player, serverWorld, hitResult.blockPos)
            if (onion != InteractionResult.PASS) return@register onion
            val kelp = KelpToBoneService.handleUse(player, serverWorld, hitResult.blockPos)
            if (kelp != InteractionResult.PASS) return@register kelp
            MidasTouchService.handleUse(player, serverWorld, hitResult.blockPos)
        }

        // "属于你的马"交互时的兼容修复 (含旧版本召唤的马):
        // 旧代码只设了主人没设驯服标志, 未驯服的马会被原版 openInventory 的
        // isTame 检查拒绝 (表现为"按 E 打不开装备界面"), 见到就补上驯服标志。
        // 其余交互 (骑乘 / 按 E 或潜行右键开装备界面 / 剪刀剪下装备) 全部交还原版处理。
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS

            // "叮咚鸡": 手持染料右键鸡 → 给它染色 (原版染料对鸡没有反应)
            if (entity is Chicken) {
                val dyed = ChickenDyeService.tryDye(player, entity, hand)
                if (dyed != InteractionResult.PASS) return@register dyed
            }

            if (entity !is AbstractHorse) return@register InteractionResult.PASS
            val owner = entity.ownerReference ?: return@register InteractionResult.PASS
            if (owner.uuid != player.uuid) return@register InteractionResult.PASS

            if (!entity.isTamed && HarvestPerks.SummonHorse.TAG in entity.entityTags()) {
                entity.setTamed(true)
            }

            InteractionResult.PASS
        }
    }

    /** 装备变化 (由 EquipmentChangeMixin 注入原版 equipStack 调用) —— 即时重算装备相关词条 */
    fun dispatchEquipmentChange(player: ServerPlayer) {
        dispatch(player) { ctx, perk -> perk.onEquipmentChange(ctx) }
    }

    /** 吃完一份食物 (由 ConsumableBoostMixin 注入原版 ConsumableComponent.finishConsumption 调用) */
    fun dispatchFoodEaten(player: ServerPlayer) {
        dispatch(player) { ctx, perk -> perk.onFoodEaten(ctx) }
    }

    private inline fun dispatch(
        player: ServerPlayer,
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
