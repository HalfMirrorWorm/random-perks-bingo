package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.ElytraBondService
import com.randomperks.game.PerkAbilityService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.PerkSession
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.FireworksComponent
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * 疾风 SWIFT —— 跑图 / 位移 / 机动。
 */
object SwiftPerks {

    private val MOD_SPEED = Identifier.of("random-perks-bingo", "swift_speed")
    private val MOD_FALL = Identifier.of("random-perks-bingo", "swift_feather_fall")
    private val MOD_TAILWIND = Identifier.of("random-perks-bingo", "swift_tailwind")

    /** 蓝 · 轻装疾行: 移动速度 +8% */
    object Speed : PerkDefinition {
        override val id = "swift_speed"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.FEATHER)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED, 0.08, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
    }

    /** 蓝 · 柔软鞋底: 摔落伤害 -70% */
    object FeatherFall : PerkDefinition {
        override val id = "swift_feather_fall"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.LEATHER_BOOTS)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL, -0.7, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.FALL_DAMAGE_MULTIPLIER, MOD_FALL)
    }

    /** 蓝 · 弹簧腿: 跳跃高度提升到约 2 格 (原版默认约 1.25 格) */
    object Jump : PerkDefinition {
        override val id = "swift_jump"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.RABBIT_FOOT)

        private val MOD_JUMP = Identifier.of("random-perks-bingo", "swift_jump")

        /** 原版跳跃初速度 0.42 → 约 1.25 格; 跳跃高度 ∝ 初速度², 故 0.42 + 0.13 = 0.55 → 约 2.1 格 */
        private const val JUMP_BONUS = 0.13

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.JUMP_STRENGTH, MOD_JUMP, JUMP_BONUS)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.JUMP_STRENGTH, MOD_JUMP)
    }

    /** 紫 · 风之步伐: 疾跑时移动速度额外 +20% —— 与原版疾跑加成同机制 (属性修饰符),
     *  由 WindStrideMixin / WindStrideClientMixin 在疾跑状态切换时挂/摘 (无轮询, 即开即停) */
    object WindStride : PerkDefinition {
        override val id = "swift_wind_stride"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SUGAR)

        /** 获得时若正在疾跑, 立即生效 (否则等下一次疾跑状态切换) */
        override fun onAcquire(ctx: PerkContext) {
            if (ctx.player.isSprinting) PerkBoostQuery.applyWindStride(ctx.player)
        }

        override fun onRemove(ctx: PerkContext) = PerkBoostQuery.clearWindStride(ctx.player)
    }

    /** 紫 · 顺风而行: 移动速度 +10%, 每个其他疾风词条额外 +3% (流派联动; 词条列表变化时即时重算, 无定时轮询) */
    object Tailwind : PerkDefinition {
        override val id = "swift_tailwind"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.PHANTOM_MEMBRANE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        /** 获得/移除任一词条时即时重算 (替代每秒轮询) */
        override fun onPerkListChanged(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) {
            val allies = (ctx.countArchetype(PerkArchetype.SWIFT) - 1).coerceAtLeast(0)
            val bonus = 0.10 + 0.03 * allies
            PerkEffects.setAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_TAILWIND)
    }

    /** 金 · 疾风 · 质变: 空中可以再跳跃一次 (每段离地一次; 由 PerkAbilityService + PlayerLandingMixin 实现) */
    object DoubleJump : PerkDefinition {
        override val id = "swift_double_jump"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.SLIME_BALL)

        /** 重生后复位"每段离地一次"标记 (新实体的空中状态已重置) */
        override fun onRespawn(ctx: PerkContext) = PerkAbilityService.clearDoubleJumpUsed(ctx.player)
    }

    /** 金 · 疾风 · 质变: 胸甲槽永久绑定鞘翅 (获得飞行能力), 但移动速度降低 30%。
     *  绑定 = 绑定诅咒 (界面层锁死, 拿不下来);
     *  死亡不掉落 = 死亡时暂存、重生原样归还 (ElytraBondService + ElytraBondDeathMixin);
     *  词条移除时直接收回鞘翅 (不归还)。 */
    object ElytraBond : PerkDefinition {
        override val id = "swift_elytra_bond"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ELYTRA)

        private val MOD_SPEED = Identifier.of("random-perks-bingo", "elytra_bond_speed")

        override fun onAcquire(ctx: PerkContext) {
            applySpeed(ctx)
            enforceChest(ctx)
            // 附赠 2 个烟花火箭 (飞行时长 2) —— 没有 FIREWORKS 组件的裸火箭是废品, 飞不了
            val rocket = ItemStack(Items.FIREWORK_ROCKET, 2)
            rocket.set(DataComponentTypes.FIREWORKS, FireworksComponent(2, emptyList()))
            PerkEffects.giveStack(ctx.player, rocket)
        }

        override fun onRespawn(ctx: PerkContext) {
            applySpeed(ctx)
            // 死亡暂存的鞘翅原样装回 (同一件, 耐久等数据保留); 没有暂存则重新绑定
            val saved = ElytraBondService.takeSaved(ctx.player)
            if (saved != null && saved.isOf(Items.ELYTRA)) {
                ctx.player.equipStack(EquipmentSlot.CHEST, saved)
            } else {
                enforceChest(ctx)
            }
        }

        /** 胸甲槽被换下鞘翅的瞬间立即换回 (覆盖被替换/清空等非界面路径; 界面路径已被绑定诅咒锁死。
         *  注: 鞘翅"飞坏"(同一件耐久耗尽) 不触发补装 —— 玩家需自行用幻翼膜修复) */
        override fun onEquipmentChange(ctx: PerkContext) = enforceChest(ctx)

        /** 移动速度降低 30% (总速度 x0.7) */
        private fun applySpeed(ctx: PerkContext) {
            PerkEffects.setAttribute(
                ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED,
                -0.3, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
            )
        }

        /** 胸甲槽强制为鞘翅 (带绑定诅咒, 界面层锁死); 被顶下来的装备退回背包 (放不下则会掉落) */
        private fun enforceChest(ctx: PerkContext) {
            val player = ctx.player
            if (!player.isAlive) return  // 死亡瞬间不补装 (此时鞘翅已被死亡暂存取走)

            val current = player.getEquippedStack(EquipmentSlot.CHEST)
            if (current.isOf(Items.ELYTRA)) return

            val elytra = ItemStack(Items.ELYTRA)
            PerkEffects.enchant(player, elytra, Enchantments.BINDING_CURSE, 1)  // 界面层锁死: 拿不下来
            player.equipStack(EquipmentSlot.CHEST, elytra)
            if (!current.isEmpty) {
                player.giveItemStack(current)
            }
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, MOD_SPEED)
            // 词条移除: 直接收回这只由词条发放的鞘翅 (不归还 —— 对局结束/命令移除统一语义;
            // 死亡暂存中的那只也一并作废)
            ctx.state.savedChestItem = null
            val chest = ctx.player.getEquippedStack(EquipmentSlot.CHEST)
            if (chest.isOf(Items.ELYTRA)) {
                ctx.player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY)
            }
        }
    }

    /** 蓝 · 疾风: 仅在受到**致命**摔落伤害时, 背包里的水桶会像不死图腾一样救你一命
     *  (只消耗水, 保留空桶; 带"水桶上浮旋转"的图腾式演出) */
    object LifeBucket : PerkDefinition {
        override val id = "swift_life_bucket"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.WATER_BUCKET)
        // 效果由 FallBucketMixin 实现 (仅摔落伤害)
    }

    /** 金 · 疾风: 获得一把**激流 I** 的三叉戟 —— 在水中或雨中按住右键即可朝准星方向高速冲出
     *  (原版机制: 激流只在水中/雨中可用, 陆地或晴天只能当普通三叉戟近战) */
    object GuraShark : PerkDefinition {
        override val id = "swift_gura_shark"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.TRIDENT)

        private const val RIPTIDE_LEVEL = 1

        override fun onAcquire(ctx: PerkContext) {
            val trident = ItemStack(Items.TRIDENT)
            PerkEffects.enchant(ctx.player, trident, Enchantments.RIPTIDE, RIPTIDE_LEVEL)
            PerkEffects.giveStack(ctx.player, trident)
        }
    }

    /** 紫 · 疾风: **全向移动** —— 八个方向都能疾跑, 而不是单纯加移速。
     *
     *  原版疾跑判定只认"前进": ClientPlayerEntity 的 canStartSprinting / shouldStopSprinting /
     *  shouldStopSwimSprinting 里用的都是 input.hasForwardMovement(), 所以侧移和后退永远跑不起来。
     *  OmnimovementMixin 在客户端把这一条换成"任意方向有输入", 于是 W/A/S/D 与四个斜向
     *  都能进入并保持疾跑 (疾跑本身的 +30% 仍由原版 LivingEntity.setSprinting 挂)。 */
    object Omnimovement : PerkDefinition {
        override val id = "swift_omnimovement"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.FEATHER)

        /** 旧实现是常驻 +30% 移速修饰符, 而且是 addPersistentModifier (会写进玩家 NBT)。
         *  改成疾跑判定放行后不再需要它 —— 获得/重生/失去时都主动清一遍, 老存档里的残留
         *  也能顺带抹掉。 */
        private val LEGACY_SPEED = Identifier.of("random-perks-bingo", "omnimovement_speed")

        override fun onAcquire(ctx: PerkContext) = clearLegacy(ctx)
        override fun onRespawn(ctx: PerkContext) = clearLegacy(ctx)
        override fun onRemove(ctx: PerkContext) = clearLegacy(ctx)

        private fun clearLegacy(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.MOVEMENT_SPEED, LEGACY_SPEED)
    }

    /** 金 · 疾风: **冲刺** —— 在空中按下潜行键, 朝视线水平方向冲刺一次 (每次腾空限一次)。
     *  由客户端 AbilityHandler 检测按键, 服务端 PerkAbilityService.handleDash 验证并施加冲量。 */
    object Dash : PerkDefinition {
        override val id = "swift_dash"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.WIND_CHARGE)

        /** 重生后复位"每段离地一次"标记 (新实体的空中状态已重置) */
        override fun onRespawn(ctx: PerkContext) = PerkAbilityService.clearDashUsed(ctx.player)
    }

    /** 蓝 · 迅捷: **逃跑计划** —— 受到生物伤害后, 自动获得速度 II 与抗性提升 I, 持续 10 秒。
     *  (触发逻辑在 PerkWorldEventService 的 ServerLivingEntityEvents.AFTER_DAMAGE) */
    object EscapePlan : PerkDefinition {
        override val id = "swift_escape_plan"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.RABBIT_FOOT)
    }

    /** 紫 · 疾风: **易位** —— 在 V 界面点击使用后连续选择两名玩家 (可以选自己), 交换他们的位置。
     *  跨维度的两人无法交换 —— 两边的坐标根本不在同一个空间里。 */
    object Swap : PerkDefinition {
        override val id = "swift_swap"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ENDER_PEARL)

        override val isOneTime = true

        private const val PURPOSE = "swap"

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val candidates = player.server?.playerManager?.playerList
                ?.filter { it.isAlive && !it.isSpectator }
                ?.map { PlayerTargetEntry(it.uuid, it.name.string) }
                ?: emptyList()

            // 少于两人就没得选 —— 包括自己也算一个候选
            if (candidates.size < 2) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.swap_not_enough"), true)
                return
            }
            PerkNetworking.sendDualTargets(player, PURPOSE, candidates)
        }

        /** 客户端两次选择都完成后调用: 交换两人的位置 (朝向各自保留) */
        fun handlePick(player: ServerPlayerEntity, first: UUID, second: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return
            if (first == second) return

            val server = player.server ?: return
            val a = server.playerManager.getPlayer(first) ?: return
            val b = server.playerManager.getPlayer(second) ?: return
            if (a === b) return

            val worldA = a.world
            val worldB = b.world
            if (worldA !== worldB) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.swap_cross_world"), true)
                return
            }

            // 骑着东西的话先下来, 否则载具留在原地、人却飞走了
            a.stopRiding()
            b.stopRiding()

            val posA = a.pos
            val yawA = a.yaw
            val pitchA = a.pitch
            val posB = b.pos
            val yawB = b.yaw
            val pitchB = b.pitch

            a.teleport(worldA, posB.x, posB.y, posB.z, emptySet(), yawA, pitchA, true)
            b.teleport(worldB, posA.x, posA.y, posA.z, emptySet(), yawB, pitchB, true)
            state.markUsed(id)
        }
    }

    fun registerAll() {
        com.randomperks.perk.PerkRegistry.register(EscapePlan)
        com.randomperks.perk.PerkRegistry.register(Swap)
        com.randomperks.perk.PerkRegistry.register(Speed)
        com.randomperks.perk.PerkRegistry.register(FeatherFall)
        com.randomperks.perk.PerkRegistry.register(Jump)
        com.randomperks.perk.PerkRegistry.register(WindStride)
        com.randomperks.perk.PerkRegistry.register(Tailwind)
        com.randomperks.perk.PerkRegistry.register(DoubleJump)
        com.randomperks.perk.PerkRegistry.register(ElytraBond)
        com.randomperks.perk.PerkRegistry.register(LifeBucket)
        com.randomperks.perk.PerkRegistry.register(GuraShark)
        com.randomperks.perk.PerkRegistry.register(Omnimovement)
        com.randomperks.perk.PerkRegistry.register(Dash)
    }
}
