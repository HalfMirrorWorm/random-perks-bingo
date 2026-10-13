package com.roguebingo.perk.content

import com.roguebingo.effect.PerkEffects
import com.roguebingo.perk.PerkArchetype
import com.roguebingo.perk.PerkContext
import com.roguebingo.perk.PerkDefinition
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.perk.PerkRarity
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.FilledMapItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.map.MapDecorationTypes
import net.minecraft.item.map.MapState
import net.minecraft.registry.tag.StructureTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos

/**
 * 秘藏 SEEKER —— 补给 / 信息 / 找物 (HUD 信息类效果将在后续里程碑加入)。
 */
object SeekerPerks {

    /** 紫 · 侦察兵: 望远镜(使用时高亮标记周围一切生物与玩家) + 指南针 + 藏宝图 */
    object Scout : PerkDefinition {
        override val id = "seeker_scout"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.SPYGLASS)

        /** 望远镜标记的半径 (格) */
        private const val SCAN_RADIUS = 128.0

        /** 发光持续时间 (tick), 略长于刷新间隔以避免闪烁 */
        private const val GLOW_DURATION = 50

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SPYGLASS))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.COMPASS))
            createTreasureMap(ctx.player)?.let { PerkEffects.giveStack(ctx.player, it) }
                ?: PerkEffects.giveStack(ctx.player, ItemStack(Items.MAP))
        }

        /** 使用望远镜时, 高亮标记周围所有生物与玩家 (发光轮廓可透视看到) */
        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            if (!player.isUsingSpyglass) return

            val world = player.world as? ServerWorld ?: return
            val nearby = world.getEntitiesByClass(
                LivingEntity::class.java,
                player.boundingBox.expand(SCAN_RADIUS),
            ) { entity ->
                entity !== player && entity.isAlive && !(entity is PlayerEntity && entity.isSpectator)
            }

            for (entity in nearby) {
                entity.addStatusEffect(
                    StatusEffectInstance(StatusEffects.GLOWING, GLOW_DURATION, 0, true, false, false)
                )
            }
        }

        /** 生成一张标记了最近结构的藏宝图 (红叉标记); 找不到结构时返回 null */
        private fun createTreasureMap(player: ServerPlayerEntity): ItemStack? {
            val world = player.world as? ServerWorld ?: return null

            // 按优先级尝试: 藏宝图结构(船难/埋藏宝藏) -> 村庄 -> 废弃传送门
            val structureTags = listOf(
                StructureTags.ON_TREASURE_MAPS,
                StructureTags.VILLAGE,
                StructureTags.RUINED_PORTAL,
                StructureTags.SHIPWRECK,
            )

            var targetPos: BlockPos? = null
            for (tag in structureTags) {
                targetPos = runCatching {
                    world.locateStructure(tag, player.blockPos, 40, false)
                }.getOrNull()
                if (targetPos != null) break
            }
            val pos = targetPos ?: return null

            // 以玩家位置为中心创建地图, 并在目标结构处加红叉标记
            val mapStack = FilledMapItem.createMap(
                world, player.blockPos.x, player.blockPos.z, 1.toByte(), true, true,
            )
            MapState.addDecorationsNbt(mapStack, pos, "rogue-bingo:treasure", MapDecorationTypes.RED_X)
            mapStack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.translatable("rogue-bingo.item.treasure_map").formatted(Formatting.GOLD),
            )
            return mapStack
        }
    }

    /** 紫 · 补给线: 立即获得一份随机补给, 之后每 45 秒再获得一份 */
    object SupplyLine : PerkDefinition {
        override val id = "seeker_supply_line"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.ENDER_CHEST)

        private const val COOLDOWN_TICKS = 45 * 20

        private val pool: List<Pair<Item, Int>> = listOf(
            Items.TORCH to 16,
            Items.BREAD to 8,
            Items.ARROW to 16,
            Items.OAK_PLANKS to 16,
            Items.LEATHER to 4,
            Items.IRON_INGOT to 2,
            Items.COAL to 8,
            Items.STRING to 4,
        )

        override fun onAcquire(ctx: PerkContext) {
            grant(ctx)
            ctx.state.cooldowns[id] = (now(ctx) + COOLDOWN_TICKS)
        }

        override fun onTick(ctx: PerkContext) {
            val current = now(ctx)
            val next = ctx.state.cooldowns[id] ?: 0L
            if (current < next) return
            ctx.state.cooldowns[id] = current + COOLDOWN_TICKS
            grant(ctx)
        }

        private fun now(ctx: PerkContext): Long = ctx.player.server?.ticks?.toLong() ?: 0L

        private fun grant(ctx: PerkContext) {
            val (item, count) = pool[ctx.player.random.nextInt(pool.size)]
            PerkEffects.giveStack(ctx.player, ItemStack(item, count))
        }
    }

    /** 紫 · 末影补给: 立即获得 1 枚末影珍珠, 之后每 5 分钟再获得 1 枚 */
    object EnderSupply : PerkDefinition {
        override val id = "seeker_ender_supply"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.ENDER_PEARL)

        private const val COOLDOWN_TICKS = 5 * 60 * 20

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.ENDER_PEARL))
            ctx.state.cooldowns[id] = (now(ctx) + COOLDOWN_TICKS)
        }

        override fun onTick(ctx: PerkContext) {
            val current = now(ctx)
            val next = ctx.state.cooldowns[id] ?: 0L
            if (current < next) return
            ctx.state.cooldowns[id] = current + COOLDOWN_TICKS
            PerkEffects.giveStack(ctx.player, ItemStack(Items.ENDER_PEARL))
        }

        private fun now(ctx: PerkContext): Long = ctx.player.server?.ticks?.toLong() ?: 0L
    }

    fun registerAll() {
        PerkRegistry.register(Scout)
        PerkRegistry.register(SupplyLine)
        PerkRegistry.register(EnderSupply)
    }
}
