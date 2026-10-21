package com.randomperks.perk.content

import com.randomperks.RandomPerksMod
import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.game.LayFlatService
import com.randomperks.game.PerkSession
import com.randomperks.game.StealService
import com.randomperks.game.VerminSwarmService
import com.randomperks.mixin.VillagerInvoker
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.PotionContentsComponent
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.SpawnReason
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
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.tag.BiomeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.BlockPos
import net.minecraft.village.VillagerData
import net.minecraft.village.VillagerProfession
import net.minecraft.village.VillagerType
import net.minecraft.world.Heightmap
import net.minecraft.world.WanderingTraderManager
import net.minecraft.world.poi.PointOfInterestTypes
import net.minecraft.world.gen.structure.StructureKeys
import java.util.UUID

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

    /** 蓝 · 补给线: 立即获得一份随机补给, 之后每 2 分钟再获得一份 */
    object SupplyLine : PerkDefinition {
        override val id = "seeker_supply_line"
        override val rarity = PerkRarity.BLUE
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

        /** 立即生效: 获得瞬间弹出目标选择器, 选定后打开其背包 (无候选时提示) */
        override fun onAcquire(ctx: PerkContext) = StealService.handleRequest(ctx.player)
    }

    /** 金 · 秘藏 · 质变: 永久透视 —— 高亮标记 32 格内的所有生物 (不含玩家) */
    object LifeVision : PerkDefinition {
        override val id = "seeker_life_vision"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.SPIDER_EYE)

        private const val RANGE = 32.0

        /** 标记有效期: 2 秒 —— 每秒续期不会闪烁, 又不会在生物离开范围后残留太久 */
        private const val GLOW_MARK_MILLIS = 2_000L

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return
            val creatures = world.getEntitiesByClass(
                LivingEntity::class.java,
                player.boundingBox.expand(RANGE),
            ) { it !== player && it !is PlayerEntity && it.isAlive &&
                // 盒子查询只是粗筛, 这里再按**球面**判一次: 否则斜角方向能到 ~111 格 (描述写的是 64 格)
                it.squaredDistanceTo(player) <= RANGE * RANGE }

            // 每秒重发 (仅自己可见)
            if (creatures.isNotEmpty()) {
                PerkNetworking.sendGlowMark(player, creatures.map { it.id }, GLOW_MARK_MILLIS)
            }
        }
    }

    /** 紫 · 秘藏: 立即获得一个末影箱, 之后每 10 分钟再获得一个 */
    object EnderCache : PerkDefinition {
        override val id = "seeker_ender_cache"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.ENDER_CHEST)

        private const val COOLDOWN_TICKS = 10 * 60 * 20

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

    /** 蓝 · 秘藏: 获得时**直接在玩家脚下**生成一只流浪商人 + 2 只商队羊驼。
     *
     *  不再走原版刷新事件 —— WanderingTraderManager.trySpawn 有生物群系 / 白天 / 概率 / 计时器
     *  一堆限制, 经常刷不出来 ("先试原版、失败再兜底"的写法仍然会漏)。现在直接生成,
     *  保证刷在玩家那边。 */
    object WanderingTrader : PerkDefinition {
        override val id = "seeker_wandering_trader"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.EMERALD)

        private const val SPAWNED_KEY = "trader_spawned"

        override fun onAcquire(ctx: PerkContext) {
            if (ctx.state.memory[SPAWNED_KEY] == "1") return

            val world = ctx.player.world as? ServerWorld ?: return
            if (spawnAt(world, ctx.player.blockPos)) {
                ctx.state.memory[SPAWNED_KEY] = "1"
            } else {
                ctx.player.sendMessage(
                    Text.translatable("random-perks-bingo.message.trader_failed"),
                    false,
                )
            }
        }

        /** 商人 + 2 只羊驼; 玩家脚下站不住 (水里/卡墙) 时才在附近找一块 */
        private fun spawnAt(world: ServerWorld, center: BlockPos): Boolean {
            val pos = if (standable(world, center)) center else nearbyStandable(world, center) ?: center

            val trader = net.minecraft.entity.EntityType.WANDERING_TRADER
                .create(world, net.minecraft.entity.SpawnReason.EVENT) ?: return false
            trader.refreshPositionAndAngles(
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                world.random.nextFloat() * 360f, 0f,
            )
            trader.setDespawnDelay(48000)
            world.spawnEntity(trader)
            // 与原版一致: 给它一个漫游目标, 商人会自己在附近走动
            trader.setWanderTarget(pos)
            trader.setPositionTarget(pos, 16)

            repeat(2) { index ->
                val llama = net.minecraft.entity.EntityType.TRADER_LLAMA
                    .create(world, net.minecraft.entity.SpawnReason.EVENT) ?: return@repeat
                llama.refreshPositionAndAngles(
                    pos.x + 0.5 + if (index == 0) -1.2 else 1.2, pos.y.toDouble(), pos.z + 0.5,
                    world.random.nextFloat() * 360f, 0f,
                )
                llama.setDespawnDelay(47999)
                world.spawnEntity(llama)
                // 与原版一致: 拴在商人身上, 羊驼会跟着他走
                llama.attachLeash(trader, true)
            }
            return true
        }

        private fun standable(world: ServerWorld, pos: BlockPos): Boolean =
            world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())

        private fun nearbyStandable(world: ServerWorld, center: BlockPos): BlockPos? {
            for (i in 0 until 12) {
                val candidate = center.add(world.random.nextInt(33) - 16, 0, world.random.nextInt(33) - 16)
                val top = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate)
                if (standable(world, top)) return top
            }
            return null
        }
    }

    /** 蓝 · 秘藏: 获得一个指向最近村庄**钟**的磁石指针 (找不到钟则指向村庄结构起点) */
    object VillageCompass : PerkDefinition {
        override val id = "seeker_village_compass"
        override val rarity = PerkRarity.BLUE
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

    /** 蓝 · 秘藏: **!?区区!?** —— 获得时弹出玩家选择器, 选中一名玩家后:
     *  1) 送他**随机一种矿物 4 个** (区区薄礼)
     *  2) **2 分钟内每 6 秒**在他 5 格内刷一只蠹虫 (由 [VerminSwarmService] 独立推进) */
    object Puny : PerkDefinition {
        override val id = "seeker_puny"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.SILVERFISH_SPAWN_EGG)

        /** 每份"区区"矿物给 4 个 */
        private const val GIFT_COUNT = 4

        /** 随机矿物池 (锭/宝石形态, 拿到就能用) */
        private val MINERALS = listOf(
            Items.COAL,
            Items.IRON_INGOT,
            Items.COPPER_INGOT,
            Items.GOLD_INGOT,
            Items.REDSTONE,
            Items.LAPIS_LAZULI,
            Items.DIAMOND,
            Items.EMERALD,
            Items.QUARTZ,
            Items.AMETHYST_SHARD,
        )

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendMessage(Text.translatable("random-perks-bingo.message.puny_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "puny",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 送矿物 + 召来蠹虫 */
        fun handleTargetPick(player: ServerPlayerEntity, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return

            val mineral = MINERALS[target.random.nextInt(MINERALS.size)]
            PerkEffects.giveStack(target, ItemStack(mineral, GIFT_COUNT))
            VerminSwarmService.start(target)

            player.sendMessage(
                Text.translatable("random-perks-bingo.message.puny_given", target.name, mineral.name),
                false,
            )
            target.sendMessage(
                Text.translatable("random-perks-bingo.message.puny_received", mineral.name),
                true,
            )
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayerEntity): List<ServerPlayerEntity> =
            player.server?.playerManager?.playerList?.filter { !it.isSpectator } ?: emptyList()
    }

    /** 蓝 · 秘藏: **随机地基** —— 获得瞬间, 把脚下 3×3 的地面各替换成一块随机方块 */
    object RandomGround : PerkDefinition {
        override val id = "seeker_random_ground"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.GRASS_BLOCK)

        /** 3×3 = 以脚下为中心, 水平各外扩 1 格 */
        private const val RADIUS = 1

        override fun onAcquire(ctx: PerkContext) {
            val player = ctx.player
            val world = player.world as? ServerWorld ?: return
            val registry = world.registryManager.getOrThrow(RegistryKeys.BLOCK)
            val center = player.blockPos.down()

            for (dx in -RADIUS..RADIUS) {
                for (dz in -RADIUS..RADIUS) {
                    val block = randomBlock(registry, player.random) ?: continue
                    world.setBlockState(center.add(dx, 0, dz), block.defaultState)
                }
            }
        }

        /** 从方块注册表里随机挑一个非空气方块 (getRandom 可能给到空气, 重试几次) */
        private fun randomBlock(
            registry: Registry<Block>,
            random: net.minecraft.util.math.random.Random,
        ): Block? {
            repeat(16) {
                val entry = registry.getRandom(random).orElse(null) ?: return null
                val block = entry.value()
                if (!block.defaultState.isAir) return block
            }
            return null
        }
    }

    /** 紫 · 秘藏: **开摆** —— 获得时被失明 + 缓慢 IV 折磨 5 分钟;
     *  熬过去拿到自然罗盘 + 结构罗盘, 中途死掉则只拿到 1 把石斧 (LayFlatService 实现) */
    object LayFlat : PerkDefinition {
        override val id = "seeker_lay_flat"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.RECOVERY_COMPASS)

        /** 两个罗盘 mod 缺一不可 —— 没装就不进词条池 */
        override fun isAvailable(): Boolean = LayFlatService.hasCompassMods()

        override fun onAcquire(ctx: PerkContext) = LayFlatService.start(ctx.player)
    }

    /** 蓝 · 秘藏: **磁力升级** —— 拾取范围扩大到 16 格 (MagnetService 每 0.25 秒扫描一次);
     *  背包界面额外显示一个垃圾桶格 (TrashSlotMixin 绘制 / TrashSlotClickMixin 处理点击)。 */
    object MagnetUpgrade : PerkDefinition {
        override val id = "seeker_magnet_upgrade"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.HOPPER)
    }

    /** 蓝 · 秘藏: **老牧师** —— 在脚下召唤一只**大师级牧师**村民, 但他的交易项会被随机标成
     *  **缺货** 0~10 项 (交易项还在, 只是点不了; 10 项全缺就是一只什么也不卖的牧师)。 */
    object OldPriest : PerkDefinition {
        override val id = "seeker_old_priest"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.EMERALD)

        /** 大师级牧师的交易项总数: 2 项 × 5 个等级 */
        private const val MAX_DISABLED = 10

        override fun onAcquire(ctx: PerkContext) {
            val world = ctx.player.world as? ServerWorld ?: return
            if (!spawnAt(world, ctx.player.blockPos)) {
                ctx.player.sendMessage(
                    Text.translatable("random-perks-bingo.message.old_priest_failed"),
                    false,
                )
            }
        }

        private fun spawnAt(world: ServerWorld, center: BlockPos): Boolean {
            val pos = if (standable(world, center)) center else nearbyStandable(world, center) ?: center

            val villager = EntityType.VILLAGER.create(world, SpawnReason.EVENT) ?: return false

            val registryManager = world.registryManager
            val type = registryManager.getOrThrow(RegistryKeys.VILLAGER_TYPE)
                .getOrThrow(VillagerType.PLAINS)
            val profession = registryManager.getOrThrow(RegistryKeys.VILLAGER_PROFESSION)
                .getOrThrow(VillagerProfession.CLERIC)
            // 原版只在**升级时**追加当前等级的交易 (fillRecipes 只取 map.get(level) 那一组),
            // 所以直接设 5 级再取 offers 只会得到 5 级那 2 项。这里从 1 级开始逐级"升"上去,
            // 每级手动补一次 fillRecipes, 最终累积成 5 个等级共 10 项。
            villager.setVillagerData(VillagerData(type, profession, 1))
            villager.offers
            for (level in 2..5) {
                villager.setVillagerData(VillagerData(type, profession, level))
                (villager as VillagerInvoker).randomperksFillRecipes()
            }

            villager.refreshPositionAndAngles(
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                world.random.nextFloat() * 360f, 0f,
            )
            villager.setPersistent()
            world.spawnEntity(villager)

            // 诊断: 把逐级累积出来的交易列表打出来, 方便核对等级顺序与内容
            RandomPerksMod.logger.info(
                "[RandomPerks] old priest trades: {}",
                villager.offers.joinToString(" | ") {
                    "${it.displayedFirstBuyItem.name.string} x${it.displayedFirstBuyItem.count}" +
                        " -> ${it.sellItem.name.string} x${it.sellItem.count}"
                },
            )

            // 交易是懒生成的: getOffers() 会按当前等级补满 10 项, 再把其中随机的 0~10 项标成缺货。
            // TradeOffer.disable() 就是把 uses 顶到 maxUses —— 界面上显示为红色的缺货叉, 交易项本身还在。
            val pool = villager.offers.toMutableList()
            repeat(minOf(world.random.nextInt(MAX_DISABLED + 1), pool.size)) {
                pool.removeAt(world.random.nextInt(pool.size)).disable()
            }
            return true
        }

        private fun standable(world: ServerWorld, pos: BlockPos): Boolean =
            world.getBlockState(pos).isAir && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())

        private fun nearbyStandable(world: ServerWorld, center: BlockPos): BlockPos? {
            for (i in 0 until 12) {
                val candidate = center.add(world.random.nextInt(33) - 16, 0, world.random.nextInt(33) - 16)
                val top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate)
                if (standable(world, top)) return top
            }
            return null
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
        PerkRegistry.register(OldPriest)
        PerkRegistry.register(VillageCompass)
        PerkRegistry.register(Puny)
        PerkRegistry.register(RandomGround)
        PerkRegistry.register(LayFlat)
        PerkRegistry.register(MagnetUpgrade)
    }
}
