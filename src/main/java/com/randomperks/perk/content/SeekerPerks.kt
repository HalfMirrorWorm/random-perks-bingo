package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.game.StealService
import com.randomperks.mixin.ServerWorldAccessor
import com.randomperks.mixin.WanderingTraderManagerInvoker
import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.PotionContentsComponent
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.FilledMapItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.map.MapDecorationTypes
import net.minecraft.item.map.MapState
import net.minecraft.potion.Potions
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WanderingTraderManager
import net.minecraft.world.poi.PointOfInterestTypes
import net.minecraft.world.gen.structure.StructureKeys

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

        /** 标记持续时间: 瞄准一次即标记 1 分钟 (毫秒; 仅同队可见) */
        private const val GLOW_MARK_MILLIS = 60_000L

        /** 望远镜最大瞄准距离 (格) */
        private const val REACH = 128.0

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SPYGLASS))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.COMPASS))
            createTreasureMap(ctx.player)?.let { PerkEffects.giveStack(ctx.player, it) }
                ?: PerkEffects.giveStack(ctx.player, ItemStack(Items.MAP))
        }

        /** 使用望远镜时, 高亮标记**视线瞄准的**生物或玩家 (发光轮廓可透视看到) */
        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            if (!player.isUsingSpyglass) return

            val eyePos = player.eyePos
            val lookVec = player.rotationVector
            val endPos = eyePos.add(lookVec.multiply(REACH))
            val searchBox = player.boundingBox.stretch(lookVec.multiply(REACH)).expand(1.0)

            val hit = ProjectileUtil.raycast(
                player, eyePos, endPos, searchBox,
                { entity ->
                    entity !== player && entity is LivingEntity && entity.isAlive &&
                        !(entity is PlayerEntity && entity.isSpectator)
                },
                REACH * REACH,
            )

            val target = hit?.entity as? LivingEntity ?: return
            // 团队可见的发光标记 (代替原版全局发光)
            GlowMarkService.markFor(GlowMarkService.teamViewers(player), target, GLOW_MARK_MILLIS)
        }

        /** 生成原版沉船式藏宝图: 指向最近"埋藏的宝藏"(沙滩下的宝箱), 红叉标记; 找不到时返回 null */
        private fun createTreasureMap(player: ServerPlayerEntity): ItemStack? {
            val world = player.world as? ServerWorld ?: return null

            val pos = runCatching {
                val structureRegistry = world.registryManager.getOrThrow(RegistryKeys.STRUCTURE)
                val buriedTreasure = structureRegistry.getOrThrow(StructureKeys.BURIED_TREASURE)
                world.chunkManager.chunkGenerator.locateStructure(
                    world,
                    RegistryEntryList.of(buriedTreasure),
                    player.blockPos,
                    100,
                    false,
                )?.first
            }.getOrNull() ?: return null

            // 地图以宝藏位置为中心, 打开即可看到红叉
            val mapStack = FilledMapItem.createMap(world, pos.x, pos.z, 1.toByte(), true, true)
            MapState.addDecorationsNbt(mapStack, pos, "random-perks-bingo:treasure", MapDecorationTypes.RED_X)
            mapStack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.translatable("random-perks-bingo.item.treasure_map").formatted(Formatting.GOLD),
            )
            return mapStack
        }
    }

    /** 紫 · 补给线: 立即获得一份随机补给, 之后每 2 分钟再获得一份 */
    object SupplyLine : PerkDefinition {
        override val id = "seeker_supply_line"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.ENDER_CHEST)

        private const val COOLDOWN_TICKS = 2 * 60 * 20

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

    /** 金 · 秘藏 · 质变: **获得时立即**打开一名随机敌方玩家的背包, 偷走其中一格自选物品 (一次性) */
    object Pickpocket : PerkDefinition {
        override val id = "seeker_pickpocket"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.LEAD)

        /** 立即生效: 获得瞬间随机选一名敌方玩家并打开其背包 (无目标时提示) */
        override fun onAcquire(ctx: PerkContext) = StealService.handleRequest(ctx.player)
    }

    /** 金 · 秘藏 · 质变: 永久透视 —— 高亮标记 64 格内的所有生物 (不含玩家) */
    object LifeVision : PerkDefinition {
        override val id = "seeker_life_vision"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.SPIDER_EYE)

        private const val RANGE = 64.0

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return
            val creatures = world.getEntitiesByClass(
                LivingEntity::class.java,
                player.boundingBox.expand(RANGE),
            ) { it !== player && it !is PlayerEntity && it.isAlive }

            // 每秒重发 (标记 5 秒有效, 过期自动消失); 仅自己可见
            if (creatures.isNotEmpty()) {
                PerkNetworking.sendGlowMark(player, creatures.map { it.id }, 5_000L)
            }
        }
    }

    /** 紫 · 秘藏: 立即获得一个末影箱, 之后每 8 分钟再获得一个 */
    object EnderCache : PerkDefinition {
        override val id = "seeker_ender_cache"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.ENDER_CHEST)

        private const val COOLDOWN_TICKS = 8 * 60 * 20

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.ENDER_CHEST))
            ctx.state.cooldowns[id] = now(ctx) + COOLDOWN_TICKS
        }

        override fun onTick(ctx: PerkContext) {
            val current = now(ctx)
            if (current < (ctx.state.cooldowns[id] ?: 0L)) return
            ctx.state.cooldowns[id] = current + COOLDOWN_TICKS
            PerkEffects.giveStack(ctx.player, ItemStack(Items.ENDER_CHEST))
        }

        private fun now(ctx: PerkContext): Long = ctx.player.server?.ticks?.toLong() ?: 0L
    }

    /** 紫 · 秘藏: 立即获得 1 瓶随机药水, 之后每 2 分钟再获得 1 瓶 */
    object Alchemist : PerkDefinition {
        override val id = "seeker_alchemist"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.BREWING_STAND)

        private const val COOLDOWN_TICKS = 2 * 60 * 20

        /** 随机药水池 (纯正面/中性) */
        private val POTIONS = listOf(
            Potions.HEALING,
            Potions.REGENERATION,
            Potions.STRENGTH,
            Potions.SWIFTNESS,
            Potions.NIGHT_VISION,
            Potions.INVISIBILITY,
            Potions.FIRE_RESISTANCE,
            Potions.LEAPING,
            Potions.WATER_BREATHING,
            Potions.SLOW_FALLING,
        )

        override fun onAcquire(ctx: PerkContext) {
            grant(ctx)
            ctx.state.cooldowns[id] = now(ctx) + COOLDOWN_TICKS
        }

        override fun onTick(ctx: PerkContext) {
            val current = now(ctx)
            if (current < (ctx.state.cooldowns[id] ?: 0L)) return
            ctx.state.cooldowns[id] = current + COOLDOWN_TICKS
            grant(ctx)
        }

        private fun grant(ctx: PerkContext) {
            val potion = POTIONS[ctx.player.random.nextInt(POTIONS.size)]
            PerkEffects.giveStack(ctx.player, PotionContentsComponent.createStack(Items.POTION, potion))
        }

        private fun now(ctx: PerkContext): Long = ctx.player.server?.ticks?.toLong() ?: 0L
    }

    /** 蓝 · 秘藏: 立即触发一次原版流浪商人刷新事件; 生物群系不允许时提示, 换群系后自动重试 */
    object WanderingTrader : PerkDefinition {
        override val id = "seeker_wandering_trader"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.EMERALD)

        private const val SPAWNED_KEY = "trader_spawned"
        private const val BIOME_KEY = "trader_last_biome"
        private const val CHECK_KEY = "trader_next_check"

        /** 换群系检测间隔: 15 秒 (仅在尚未刷出时) */
        private const val CHECK_INTERVAL_TICKS = 15 * 20L

        override fun onAcquire(ctx: PerkContext) = attempt(ctx)

        /**
         * 仅在尚未刷出时: 每 15 秒检查一次玩家所在生物群系是否已变化, 变了就重试一次
         * (直到刷出为止)。原版没有"生物群系变化"事件, 用低频检查替代。
         */
        override fun onTick(ctx: PerkContext) {
            if (ctx.state.memory[SPAWNED_KEY] == "1") return

            val player = ctx.player
            val now = player.server?.ticks?.toLong() ?: return
            if (now < (ctx.state.cooldowns[CHECK_KEY] ?: 0L)) return
            ctx.state.cooldowns[CHECK_KEY] = now + CHECK_INTERVAL_TICKS

            val world = player.world as? ServerWorld ?: return
            val biome = biomeId(world, player.blockPos) ?: return
            if (biome == ctx.state.memory[BIOME_KEY]) return

            attempt(ctx)
        }

        /** 一次刷新尝试: 生物群系受限 → 提示并等待换群系; 成功 → 记录并停止 */
        private fun attempt(ctx: PerkContext) {
            if (ctx.state.memory[SPAWNED_KEY] == "1") return

            val player = ctx.player
            val world = player.world as? ServerWorld ?: return
            val pos = player.blockPos

            ctx.state.memory[BIOME_KEY] = biomeId(world, pos) ?: ""

            if (isBiomeBlocked(world, pos)) {
                player.sendMessage(
                    Text.translatable("random-perks-bingo.message.trader_biome_blocked"),
                    false,
                )
                return
            }
            if (spawnVanillaTrader(world)) {
                ctx.state.memory[SPAWNED_KEY] = "1"
            }
        }

        /** 生物群系 id (用于变化检测; 取不到时返回 null) */
        private fun biomeId(world: ServerWorld, pos: BlockPos): String? =
            world.getBiome(pos).key.map { it.value.toString() }.orElse(null)

        /** 玩家所在生物群系是否在原版"禁止流浪商人刷新"标签中 */
        private fun isBiomeBlocked(world: ServerWorld, pos: BlockPos): Boolean =
            world.getBiome(pos).isIn(BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)

        /**
         * 执行**原版流浪商人刷新事件** (WanderingTraderManager.trySpawn):
         * 随机选一名玩家 → 在其 48 格内寻找合法位置 → 生成商人 + 2 只商队羊驼 → 设置漫游目标,
         * 与自然刷新完全一致 (含生物群系限制等原版判定)。
         *
         * 原版该逻辑自带 1/10 概率门槛; 未命中时只消耗一次随机数, 代价可忽略,
         * 因此循环触发到命中为止 (上限 50 次, 兜底防"生物群系不允许"类死循环)。
         */
        private fun spawnVanillaTrader(world: ServerWorld): Boolean {
            val accessor = world as ServerWorldAccessor
            val manager = accessor.spawners
                .filterIsInstance<WanderingTraderManager>()
                .firstOrNull()
                ?: WanderingTraderManager(accessor.worldProperties)
            val invoker = manager as WanderingTraderManagerInvoker

            repeat(50) {
                if (invoker.invokeTrySpawn(world)) return true
            }
            return false
        }
    }

    /** 紫 · 秘藏: 获得一个指向最近村庄**钟**的磁石指针 (找不到钟则指向村庄结构起点) */
    object VillageCompass : PerkDefinition {
        override val id = "seeker_village_compass"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.COMPASS)

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return

            val villagePos = runCatching {
                world.locateStructure(net.minecraft.registry.tag.StructureTags.VILLAGE, player.blockPos, 100, false)
            }.getOrNull()

            if (villagePos == null) {
                PerkEffects.giveStack(player, ItemStack(Items.COMPASS))
                player.sendMessage(
                    net.minecraft.text.Text.translatable("random-perks-bingo.message.village_not_found"),
                    true,
                )
                return
            }

            // 优先指向村庄的钟 (meeting POI); 村庄范围内找不到钟则退回结构起点
            val bellPos = runCatching {
                world.pointOfInterestStorage.getNearestPosition(
                    { entry -> entry.key.map { it == PointOfInterestTypes.MEETING }.orElse(false) },
                    villagePos,
                    96,
                    net.minecraft.world.poi.PointOfInterestStorage.OccupationStatus.ANY,
                ).orElse(null)
            }.getOrNull()

            val target = bellPos ?: villagePos
            val stack = ItemStack(Items.COMPASS)
            stack.set(
                DataComponentTypes.LODESTONE_TRACKER,
                net.minecraft.component.type.LodestoneTrackerComponent(
                    java.util.Optional.of(net.minecraft.util.math.GlobalPos.create(world.registryKey, target)),
                    false,
                ),
            )
            PerkEffects.giveStack(player, stack)
        }
    }

    fun registerAll() {
        PerkRegistry.register(Scout)
        PerkRegistry.register(SupplyLine)
        PerkRegistry.register(EnderSupply)
        PerkRegistry.register(Pickpocket)
        PerkRegistry.register(LifeVision)
        PerkRegistry.register(EnderCache)
        PerkRegistry.register(Alchemist)
        PerkRegistry.register(WanderingTrader)
        PerkRegistry.register(VillageCompass)
    }
}
