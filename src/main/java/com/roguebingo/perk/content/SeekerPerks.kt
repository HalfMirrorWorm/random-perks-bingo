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
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.item.FilledMapItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.map.MapDecorationTypes
import net.minecraft.item.map.MapState
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
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

        /** 标记持续时间 (tick): 瞄准时持续刷新, 移开视线后约 1.5 秒消失 */
        private const val GLOW_DURATION = 30

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
            target.addStatusEffect(
                StatusEffectInstance(StatusEffects.GLOWING, GLOW_DURATION, 0, true, false, false)
            )
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
