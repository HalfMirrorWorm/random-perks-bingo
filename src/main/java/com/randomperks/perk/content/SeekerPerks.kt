package com.randomperks.perk.content

import com.randomperks.RandomPerksMod
import com.randomperks.effect.PerkEffects
import com.randomperks.game.GlowMarkService
import com.randomperks.game.LayFlatService
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkSession
import com.randomperks.game.SavageMagicService
import com.randomperks.game.StealService
import com.randomperks.game.VerminSwarmService
import com.randomperks.game.WarmArchaeologyService
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import com.randomperks.state.PerkStateManager
import net.minecraft.world.level.block.Block
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.core.HolderSet
import net.minecraft.tags.BiomeTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.item.trading.TradeSet
import net.minecraft.world.item.trading.TradeSets
import net.minecraft.world.item.trading.VillagerTrade
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.entity.npc.villager.VillagerData
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.entity.npc.villager.VillagerType
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner
import net.minecraft.world.entity.ai.village.poi.PoiTypes
import net.minecraft.world.level.levelgen.structure.BuiltinStructures
import java.util.UUID

/**
 * 秘藏 SEEKER —— 补给 / 信息 / 找物 (HUD 信息类效果将在后续里程碑加入)。
 */
object SeekerPerks {

    /** 蓝 · 侦察兵: 望远镜(使用时高亮标记周围一切生物与玩家) + 指南针 + 藏宝图 */
    object Scout : PerkDefinition {
        override val id = "seeker_scout"
        override val rarity = PerkRarity.BLUE
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
            if (!player.isScoping) return

            val eyePos = player.eyePosition
            val lookVec = player.lookAngle
            val endPos = eyePos.add(lookVec.scale(REACH))
            val searchBox = player.boundingBox.expandTowards(lookVec.scale(REACH)).inflate(1.0)

            val hit = ProjectileUtil.getEntityHitResult(
                player, eyePos, endPos, searchBox,
                { entity ->
                    entity !== player && entity is LivingEntity && entity.isAlive &&
                        !(entity is Player && entity.isSpectator)
                },
                REACH * REACH,
            )

            val target = hit?.entity as? LivingEntity ?: return
            // 团队可见的发光标记 (代替原版全局发光)
            GlowMarkService.markFor(GlowMarkService.teamViewers(player), target, GLOW_MARK_MILLIS)
        }

        /** 生成原版沉船式藏宝图: 指向最近"埋藏的宝藏"(沙滩下的宝箱), 红叉标记; 找不到时返回 null */
        private fun createTreasureMap(player: ServerPlayer): ItemStack? {
            val world = player.level() as? ServerLevel ?: return null

            val pos = runCatching {
                val structureRegistry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                val buriedTreasure = structureRegistry.getOrThrow(BuiltinStructures.BURIED_TREASURE)
                world.chunkSource.generator.findNearestMapStructure(
                    world,
                    HolderSet.direct(buriedTreasure),
                    player.blockPosition(),
                    100,
                    false,
                )?.first
            }.getOrNull() ?: return null

            // 地图以宝藏位置为中心, 打开即可看到红叉
            val mapStack = MapItem.create(world, pos.x, pos.z, 1.toByte(), true, true)
            MapItemSavedData.addTargetDecoration(mapStack, pos, "random-perks-bingo:treasure", MapDecorationTypes.RED_X)
            mapStack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable("random-perks-bingo.item.treasure_map").withStyle(ChatFormatting.GOLD),
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

        private fun now(ctx: PerkContext): Long = ctx.player.level().getServer().tickCount.toLong()

        private fun grant(ctx: PerkContext) {
            val (item, count) = pool[ctx.player.random.nextInt(pool.size)]
            PerkEffects.giveStack(ctx.player, ItemStack(item, count))
        }
    }

    /** 紫 · **疾风**: 末影补给 —— 立即获得 1 枚末影珍珠, 之后每 3 分钟再获得 1 枚。
     *  流派归疾风 (id 沿用 `seeker_` 前缀不改, 以免旧存档对不上), 因此会算进"顺风而行"的疾风联动。 */
    object EnderSupply : PerkDefinition {
        override val id = "seeker_ender_supply"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SWIFT
        override fun icon() = ItemStack(Items.ENDER_PEARL)

        private const val COOLDOWN_TICKS = 3 * 60 * 20

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

        private fun now(ctx: PerkContext): Long = ctx.player.level().getServer().tickCount.toLong()
    }

    /** 金 · 秘藏 · 质变: **获得时立即**打开一名随机敌方玩家的背包, 偷走其中一格自选物品 (一次性) */
    object Pickpocket : PerkDefinition {
        override val id = "seeker_pickpocket"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.LEAD)

        override val isOneTime = true

        /** 由 V 界面点击触发: 弹出目标选择器, 选定后打开其背包 (无候选时提示) */
        override fun onActivate(ctx: PerkContext) = StealService.handleRequest(ctx.player)
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
            val world = player.level() as? ServerLevel ?: return
            val creatures = world.getEntitiesOfClass(
                LivingEntity::class.java,
                player.boundingBox.inflate(RANGE),
            ) { it !== player && it !is Player && it.isAlive &&
                // 盒子查询只是粗筛, 这里再按**球面**判一次: 否则斜角方向能到 ~111 格 (描述写的是 64 格)
                it.distanceToSqr(player) <= RANGE * RANGE }

            // 每秒重发 (仅自己可见)
            if (creatures.isNotEmpty()) {
                PerkNetworking.sendGlowMark(player, creatures.map { it.id }, GLOW_MARK_MILLIS)
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

        private fun now(ctx: PerkContext): Long = ctx.player.level().getServer().tickCount.toLong()
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
            PerkEffects.giveStack(ctx.player, PotionContents.createItemStack(Items.POTION, potion))
        }

        private fun now(ctx: PerkContext): Long = ctx.player.level().getServer().tickCount.toLong()
    }

    /** 蓝 · 唤法 · 贮藏: **流浪拴绳** —— 在词条列表点击使用, **直接在玩家脚下**生成一只流浪商人 + 2 只商队羊驼,
     *  并附赠 5 个绿宝石。
     *
     *  不再走原版刷新事件 —— WanderingTraderManager.trySpawn 有生物群系 / 白天 / 概率 / 计时器
     *  一堆限制, 经常刷不出来 ("先试原版、失败再兜底"的写法仍然会漏)。现在直接生成,
     *  保证刷在玩家那边。 */
    object WanderingTrader : PerkDefinition {
        override val id = "summon_wandering_trader"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.EMERALD)

        override val isOneTime = true

        /** 召唤成功时附赠的绿宝石数量 */
        private const val EMERALD_REWARD = 5

        override fun onActivate(ctx: PerkContext) {
            val world = ctx.player.level() as? ServerLevel ?: return
            if (!spawnAt(world, ctx.player.blockPosition())) {
                ctx.player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.trader_failed"),
                    false,
                )
                return
            }

            PerkEffects.giveStack(ctx.player, ItemStack(Items.EMERALD, EMERALD_REWARD))
            ctx.state.markUsed(id)
            PerkGrantService.notifyPerkListChanged(ctx.player)
            PerkGrantService.syncAbilities(ctx.player)
        }

        /** 商人 + 2 只羊驼; 玩家脚下站不住 (水里/卡墙) 时才在附近找一块 */
        private fun spawnAt(world: ServerLevel, center: BlockPos): Boolean {
            val pos = if (standable(world, center)) center else nearbyStandable(world, center) ?: center

            val trader = net.minecraft.world.entity.EntityTypes.WANDERING_TRADER
                .create(world, net.minecraft.world.entity.EntitySpawnReason.EVENT) ?: return false
            trader.snapTo(
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                world.random.nextFloat() * 360f, 0f,
            )
            trader.setDespawnDelay(48000)
            world.addFreshEntity(trader)
            // 与原版一致: 给它一个漫游目标, 商人会自己在附近走动
            trader.setWanderTarget(pos)
            trader.setHomeTo(pos, 16)

            repeat(2) { index ->
                val llama = net.minecraft.world.entity.EntityTypes.TRADER_LLAMA
                    .create(world, net.minecraft.world.entity.EntitySpawnReason.EVENT) ?: return@repeat
                llama.snapTo(
                    pos.x + 0.5 + if (index == 0) -1.2 else 1.2, pos.y.toDouble(), pos.z + 0.5,
                    world.random.nextFloat() * 360f, 0f,
                )
                llama.setDespawnDelay(47999)
                world.addFreshEntity(llama)
                // 与原版一致: 拴在商人身上, 羊驼会跟着他走
                llama.setLeashedTo(trader, true)
            }
            return true
        }

        /** 站得住: 脚下与头顶都是空气, 下方有碰撞箱。
         *  不再要求下方是"完整方块" —— 半砖/楼梯/土径/耕地/灵魂沙/地毯下面也能刷出来 */
        private fun standable(world: ServerLevel, pos: BlockPos): Boolean {
            if (!world.getBlockState(pos).isAir) return false
            if (!world.getBlockState(pos.above()).isAir) return false
            val below = world.getBlockState(pos.below())
            return !below.getCollisionShape(world, pos.below()).isEmpty
        }

        private fun nearbyStandable(world: ServerLevel, center: BlockPos): BlockPos? {
            // 先在玩家周围 (同高度 ±3 格) 做 3D 搜索 —— 人在洞穴里时也刷在身边, 不会跑到地表
            for (i in 0 until 24) {
                val candidate = center.offset(
                    world.random.nextInt(17) - 8,
                    world.random.nextInt(7) - 3,
                    world.random.nextInt(17) - 8,
                )
                if (standable(world, candidate)) return candidate
            }
            // 兜底: 附近的地表高度图 (与原版 "找地面" 的行为一致)
            for (i in 0 until 12) {
                val candidate = center.offset(world.random.nextInt(33) - 16, 0, world.random.nextInt(33) - 16)
                val top = world.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate)
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
            val world = player.level() as? ServerLevel ?: return

            val villagePos = runCatching {
                world.findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, player.blockPosition(), 100, false)
            }.getOrNull()

            if (villagePos == null) {
                PerkEffects.giveStack(player, ItemStack(Items.COMPASS))
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("random-perks-bingo.message.village_not_found"),
                    true,
                )
                return
            }

            // 优先指向村庄的钟 (meeting POI); 村庄范围内找不到钟则退回结构起点
            val bellPos = runCatching {
                world.poiManager.findClosest(
                    { entry -> entry.unwrapKey().map { it == PoiTypes.MEETING }.orElse(false) },
                    villagePos,
                    96,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY,
                ).orElse(null)
            }.getOrNull()

            val target = bellPos ?: villagePos
            val stack = ItemStack(Items.COMPASS)
            stack.set(
                DataComponents.LODESTONE_TRACKER,
                net.minecraft.world.item.component.LodestoneTracker(
                    java.util.Optional.of(net.minecraft.core.GlobalPos.of(world.dimension(), target)),
                    false,
                ),
            )
            PerkEffects.giveStack(player, stack)
        }
    }

    /** 蓝 · 秘藏: **!?区区!?** —— 在 V 界面点击使用后弹出玩家选择器, 选中一名玩家后:
     *  1) 送他**随机一种矿物 4 个** (区区薄礼)
     *  2) **2 分钟内每 6 秒**在他 5 格内刷一只蠹虫 (由 [VerminSwarmService] 独立推进) */
    object Puny : PerkDefinition {
        override val id = "seeker_puny"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.SILVERFISH_SPAWN_EGG)

        override val isOneTime = true

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

        override fun onActivate(ctx: PerkContext) {
            val player = ctx.player
            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendSystemMessage(Component.translatable("random-perks-bingo.message.puny_no_target"), true)
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                "puny",
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端在选择器里选中了一名玩家 → 送矿物 + 召来蠹虫 */
        fun handleTargetPick(player: ServerPlayer, uuid: UUID) {
            if (!PerkSession.isEnabled()) return

            val state = PerkStateManager.get(player.uuid) ?: return
            if (!state.hasPerk(id)) return
            if (state.isUsed(id)) return

            // 校验: 必须是当下的合法候选 (防止客户端伪造 uuid)
            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return

            val mineral = MINERALS[target.random.nextInt(MINERALS.size)]
            PerkEffects.giveStack(target, ItemStack(mineral, GIFT_COUNT))
            VerminSwarmService.start(target)
            state.markUsed(id)

            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.puny_given", target.name, ItemStack(mineral).hoverName),
                false,
            )
            target.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.puny_received", ItemStack(mineral).hoverName),
                true,
            )
        }

        /** 候选: 所有在线玩家 (不限队伍/维度), 不含旁观者 */
        private fun candidates(player: ServerPlayer): List<ServerPlayer> =
            player.level().getServer().playerList.players.filter { !it.isSpectator }
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
            val world = player.level() as? ServerLevel ?: return
            val registry = world.registryAccess().lookupOrThrow(Registries.BLOCK)
            val center = player.blockPosition().below()

            for (dx in -RADIUS..RADIUS) {
                for (dz in -RADIUS..RADIUS) {
                    val block = randomBlock(registry, player.random) ?: continue
                    world.setBlockAndUpdate(center.offset(dx, 0, dz), block.defaultBlockState())
                }
            }
        }

        /** 从方块注册表里随机挑一个非空气方块 (getRandom 可能给到空气, 重试几次) */
        private fun randomBlock(
            registry: Registry<Block>,
            random: net.minecraft.util.RandomSource,
        ): Block? {
            repeat(16) {
                val entry = registry.getRandom(random).orElse(null) ?: return null
                val block = entry.value()
                if (!block.defaultBlockState().isAir) return block
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

    /** 绿 · 秘藏: **磁力升级** —— 拾取范围扩大到 5 格 (MagnetService 每 0.25 秒扫描一次);
     *  手持潜影盒 / 末影箱右键可直接打开 (ContainerOpenService)。 */
    object MagnetUpgrade : PerkDefinition {
        override val id = "seeker_magnet_upgrade"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.HOPPER)
    }

    /** 蓝 · 秘藏 · 贮藏: **老牧师** —— 在词条列表点击使用, 获得 16 个绿宝石,
     *  并在玩家当前位置召唤一只**大师级牧师**村民; 但他的交易项会被随机标成 **缺货** 0~3 项
     *  (交易项还在, 只是点不了)。不往周围或地表另找落点。 */
    object OldPriest : PerkDefinition {
        override val id = "seeker_old_priest"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.EMERALD)

        override val isOneTime = true

        /** 获得时赠送的绿宝石数量 */
        private const val EMERALD_REWARD = 16

        /** 最多随机缺货的交易项数量 */
        private const val MAX_DISABLED = 3

        override fun onActivate(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.EMERALD, EMERALD_REWARD))

            val world = ctx.player.level() as? ServerLevel ?: return
            if (!spawnAt(world, ctx.player)) {
                ctx.player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.old_priest_failed"),
                    false,
                )
            }

            ctx.state.markUsed(id)
            PerkGrantService.notifyPerkListChanged(ctx.player)
            PerkGrantService.syncAbilities(ctx.player)
        }

        private fun spawnAt(world: ServerLevel, player: ServerPlayer): Boolean {
            val villager = EntityTypes.VILLAGER.create(world, EntitySpawnReason.EVENT) ?: return false

            val registryManager = world.registryAccess()
            val type = registryManager.lookupOrThrow(Registries.VILLAGER_TYPE)
                .getOrThrow(VillagerType.PLAINS)
            val profession = registryManager.lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .getOrThrow(VillagerProfession.CLERIC)
            // 等级直接设 5 级 (界面显示为大师级)
            villager.setVillagerData(VillagerData(type, profession, 5))
            villager.snapTo(
                player.x, player.y, player.z,
                player.yRot, 0f,
            )
            // 交易列表按 1→5 级的 TradeSet 自己抽。先摆好位置, 战利品上下文里的原点才是玩家脚下。
            villager.setOffers(buildMasterClericOffers(villager, world))
            villager.setPersistenceRequired()
            world.addFreshEntity(villager)

            // 诊断: 把逐级累积出来的交易列表打出来, 方便核对等级顺序与内容
            RandomPerksMod.logger.info(
                "[RandomPerks] old priest trades: {}",
                villager.offers.joinToString(" | ") {
                    "${it.costA.hoverName.string} x${it.costA.count}" +
                        " -> ${it.result.hoverName.string} x${it.result.count}"
                },
            )

            // 交易是懒生成的: getOffers() 会按当前等级补满 10 项, 再把其中随机的 0~3 项标成缺货。
            // TradeOffer.setToOutOfStock() 就是把 uses 顶到 maxUses —— 界面上显示为红色的缺货叉, 交易项本身还在。
            val pool = villager.offers.toMutableList()
            repeat(minOf(world.random.nextInt(MAX_DISABLED + 1), pool.size)) {
                pool.removeAt(world.random.nextInt(pool.size)).setToOutOfStock()
            }
            return true
        }

        /**
         * 按 1→5 级从当前世界的交易表构建大师级牧师的交易列表。
         *
         * 26.3 起每级是一份 [TradeSet]: 抽几项、能否重复都由这张表决定,
         * 实验性交易重平衡开着时注册表里已经是那一套, 这里不再另选一张表。
         * 顺序仍是新手 → 学徒 → 老手 → 专家 → 大师。
         */
        private fun buildMasterClericOffers(villager: Villager, world: ServerLevel): MerchantOffers {
            val offers = MerchantOffers()
            for (level in CLERIC_LEVELS) {
                addTradeSet(villager, world, offers, level)
            }
            return offers
        }

        private fun addTradeSet(
            villager: Villager,
            world: ServerLevel,
            offers: MerchantOffers,
            key: ResourceKey<TradeSet>,
        ) {
            val tradeSet = world.registryAccess().lookupOrThrow(Registries.TRADE_SET)
                .getOptional(key).orElse(null) ?: return
            val context = LootContext.Builder(
                LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .withParameter(
                        LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED,
                        net.minecraft.util.Unit.INSTANCE,
                    )
                    .create(LootContextParamSets.VILLAGER_TRADE),
            ).create(tradeSet.randomSequence())
            val count = tradeSet.calculateNumberOfTrades(context)
            val pool = ArrayList<Holder<VillagerTrade>>(tradeSet.trades().toList())
            var added = 0
            while (added < count && pool.isNotEmpty()) {
                val index = context.random.nextInt(pool.size)
                val offer = pool[index].value().getOffer(context)
                if (offer == null) {
                    pool.removeAt(index)
                    continue
                }
                if (!tradeSet.allowDuplicates()) pool.removeAt(index)
                offers.add(offer)
                added++
            }
        }

        private val CLERIC_LEVELS = arrayOf(
            TradeSets.CLERIC_LEVEL_1,
            TradeSets.CLERIC_LEVEL_2,
            TradeSets.CLERIC_LEVEL_3,
            TradeSets.CLERIC_LEVEL_4,
            TradeSets.CLERIC_LEVEL_5,
        )
    }

    /** 蓝 · 秘藏: **暖水考古** —— 获得一把刷子; 用刷子刷珊瑚块会把它刷掉, 并有 10% 概率
     *  刷出一颗**颜色对应**的矿物 (蓝→青金石、粉→钻石、紫→紫水晶、红→红石、黄→金粒)。
     *  交互由 [WarmArchaeologyService] 接管 (原版刷子不认可珊瑚)。 */
    object WarmArchaeology : PerkDefinition {
        override val id = WarmArchaeologyService.PERK_ID
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.BRUSH)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BRUSH))
        }
    }

    /** 紫 · 秘藏: **野人魔了** —— 获得 10 个绿宝石;
     *  手持职业方块右键傻子村民, 消耗方块把他变成该职业的大师级村民;
     *  手持谜之炖菜右键任意成年村民, 把它变成傻子村民 (见 [SavageMagicService]) */
    object SavageMagic : PerkDefinition {
        override val id = SavageMagicService.PERK_ID
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.VILLAGER_SPAWN_EGG)

        private const val EMERALDS = 10

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.EMERALD, EMERALDS))
        }
    }

    /** 绿 · 秘藏: **村庄英雄** —— 永久获得村庄英雄效果; 并且你的交易不会让村民缺货
     *  (由 VillageHeroTradeMixin 在每次交易结算后清零 uses) */
    object VillageHero : PerkDefinition {
        override val id = "seeker_village_hero"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.SEEKER
        override fun icon() = ItemStack(Items.EMERALD)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) =
            PerkEffects.permanentEffect(ctx.player, MobEffects.HERO_OF_THE_VILLAGE, 0)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeEffect(ctx.player, MobEffects.HERO_OF_THE_VILLAGE)
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
        PerkRegistry.register(WarmArchaeology)
        PerkRegistry.register(VillageHero)
        PerkRegistry.register(SavageMagic)
    }
}
