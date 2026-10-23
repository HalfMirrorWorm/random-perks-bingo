package com.randomperks.game

import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.Flutterer
import net.minecraft.entity.MovementType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.ai.RangedAttackMob
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.boss.dragon.EnderDragonEntity
import net.minecraft.entity.mob.BlazeEntity
import net.minecraft.entity.mob.GhastEntity
import net.minecraft.entity.mob.PhantomEntity
import net.minecraft.entity.mob.VexEntity
import net.minecraft.entity.mob.WardenEntity
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.entity.passive.BatEntity
import net.minecraft.entity.passive.PigEntity
import net.minecraft.entity.passive.StriderEntity
import net.minecraft.entity.projectile.FireballEntity
import net.minecraft.entity.projectile.SmallFireballEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.PlayerInput
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * "驯龙高手" (金 · 唤法) 的服务端逻辑。
 *
 * - 右键任意生物 → `startRiding(mob, true)` 骑上去 (玩家与 Boss 除外);
 * - 骑乘时读取骑手的 [net.minecraft.util.PlayerInput] 手动驱动坐骑 (AI 暂时停用);
 * - 右键 → 让坐骑朝视线方向的目标使用能力:
 *     - 恶魂/烈焰人 → 发射火球;
 *     - 实现 [RangedAttackMob] 的生物 → 使用它自己的远程攻击 (骷髅射箭、女巫丢药水、雪傀儡丢雪球、羊驼吐口水等);
 *     - 其他生物 → 目标在近战范围内时使用普通近战攻击。
 *
 * 骑乘状态按骑手 UUID 记录; 下马/死亡/词条被清掉/掉线时恢复坐骑原本的 AI 状态。
 */
object DragonTamerService {

    const val PERK_ID = "summon_dragon_tamer"

    /** 骑乘时右键使用能力的冷却: 1 秒 */
    private const val ABILITY_COOLDOWN_TICKS = 20L

    /** 能力选敌的最大距离 (格) */
    private const val TARGET_RANGE = 24.0

    /** 视线锥的最小 dot (越小锥角越大) */
    private const val MIN_TARGET_DOT = 0.55

    /** 近战能力允许的最大距离 (格) */
    private const val MELEE_RANGE = 4.0

    private data class Ride(
        val mob: MobEntity,
        val previousAiDisabled: Boolean,
        /** 是否由本服务手动驱动; 马/猪/炽足兽等已有原版骑乘控制的生物交给原版 */
        val manualControl: Boolean,
    )

    private val active = mutableMapOf<UUID, Ride>()

    private data class CachedInput(val input: PlayerInput, val tick: Long)

    /** 客户端每 tick 上报的原始输入 (比原版 PlayerInput 包更可靠) */
    private val inputs = mutableMapOf<UUID, CachedInput>()

    /** 输入缓存有效期: 超过这么多 tick 视为客户端没在发 (界面暂停/断线), 回退到原版输入 */
    private const val INPUT_TTL_TICKS = 5L

    fun register() {
        // 右键实体: 骑上生物 / 骑乘中使用能力
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (world.isClient || hand != Hand.MAIN_HAND || player !is ServerPlayerEntity) {
                return@register ActionResult.PASS
            }

            if (useAbility(player)) return@register ActionResult.SUCCESS
            if (tryMount(player, entity)) return@register ActionResult.SUCCESS
            ActionResult.PASS
        }

        // 右键空气/物品: 骑乘中使用能力 (没有可用能力时放行, 不影响吃东西/放方块)
        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClient || hand != Hand.MAIN_HAND || player !is ServerPlayerEntity) {
                return@register ActionResult.PASS
            }
            if (useAbility(player)) ActionResult.SUCCESS else ActionResult.PASS
        }

        // 右键方块: 同上
        UseBlockCallback.EVENT.register { player, world, hand, _ ->
            if (world.isClient || hand != Hand.MAIN_HAND || player !is ServerPlayerEntity) {
                return@register ActionResult.PASS
            }
            if (useAbility(player)) ActionResult.SUCCESS else ActionResult.PASS
        }

        // 驱动坐骑移动 + 清理失效骑乘
        ServerTickEvents.START_SERVER_TICK.register { server ->
            if (active.isEmpty()) return@register

            for ((uuid, ride) in active.toMap()) {
                val rider = server.playerManager.getPlayer(uuid)
                val mob = ride.mob
                if (rider == null || rider.vehicle !== mob || mob.isRemoved || !mob.isAlive ||
                    !PerkBoostQuery.hasDragonTamer(rider)
                ) {
                    if (rider != null && rider.vehicle === mob) rider.stopRiding()
                    restore(ride)
                    active.remove(uuid)
                    inputs.remove(uuid)
                    continue
                }
                if (ride.manualControl) drive(rider, mob)
            }
        }
    }

    /** 客户端上报的移动输入 (每 tick 一次) */
    fun handleInput(player: ServerPlayerEntity, input: PlayerInput) {
        if (!PerkBoostQuery.hasDragonTamer(player)) return
        if (player.vehicle !is MobEntity) return

        // 潜行 = 下马。原版对非标准载具的自动下马不一定生效, 这里显式处理
        if (input.sneak()) {
            player.stopRiding()
            return
        }

        val now = player.server?.ticks?.toLong() ?: return
        inputs[player.uuid] = CachedInput(input, now)
    }

    /** 尝试骑上一只生物 (失败返回 false, 交还原版交互) */
    fun tryMount(rider: ServerPlayerEntity, target: Entity): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasDragonTamer(rider)) return false
        if (rider.hasVehicle()) return false

        val mob = target as? MobEntity ?: return false
        if (!mob.isAlive || mob.isRemoved) return false
        if (isForbidden(mob)) return false

        if (!rider.startRiding(mob, true)) return false

        // 马/猪/炽足兽等已有原版骑乘控制逻辑, 不要和它抢方向
        val manual = !usesVanillaControl(mob)
        val previousAi = mob.isAiDisabled
        active[rider.uuid] = Ride(mob, previousAi, manual)

        if (manual) {
            mob.setAiDisabled(true)
            mob.navigation.stop()
        }
        mob.setTarget(null)

        rider.sendMessage(
            Text.translatable("random-perks-bingo.message.dragon_tamer_mounted", mob.name),
            false,
        )
        return true
    }

    /** 骑乘时右键: 让坐骑使用能力; 返回 true 表示本次右键已被消耗 */
    fun useAbility(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasDragonTamer(player)) return false

        val mob = player.vehicle as? MobEntity ?: return false
        val world = player.world

        val state = PerkStateManager.get(player.uuid) ?: return false
        val now = player.server?.ticks?.toLong() ?: return false
        if (now < (state.cooldowns[PERK_ID] ?: 0L)) return false

        val target = findTarget(player, mob) ?: return false

        val used = when {
            mob is GhastEntity -> {
                val dir = target.pos.add(0.0, target.height * 0.5, 0.0)
                    .subtract(mob.pos.add(0.0, mob.height * 0.5, 0.0))
                val fireball = FireballEntity(world, mob, dir, 1)
                fireball.refreshPositionAndAngles(mob.x, mob.y + mob.height * 0.5, mob.z, mob.yaw, mob.pitch)
                world.spawnEntity(fireball)
                true
            }
            mob is BlazeEntity -> {
                val dir = target.pos.add(0.0, target.height * 0.5, 0.0)
                    .subtract(mob.pos.add(0.0, mob.height * 0.5, 0.0))
                val fireball = SmallFireballEntity(world, mob, dir)
                fireball.refreshPositionAndAngles(mob.x, mob.y + mob.height * 0.5, mob.z, mob.yaw, mob.pitch)
                world.spawnEntity(fireball)
                true
            }
            mob is RangedAttackMob -> {
                mob.shootAt(target, 1.0f)
                true
            }
            mob.squaredDistanceTo(target) <= MELEE_RANGE * MELEE_RANGE -> {
                mob.tryAttack(world, target)
            }
            else -> false
        }

        if (used) state.cooldowns[PERK_ID] = now + ABILITY_COOLDOWN_TICKS
        return used
    }

    /** 对局重置: 让所有骑手下来并恢复坐骑 AI */
    fun clear() {
        for ((uuid, ride) in active) {
            ServerHolder.server?.playerManager?.getPlayer(uuid)?.stopRiding()
            restore(ride)
        }
        active.clear()
        inputs.clear()
    }

    private fun restore(ride: Ride) {
        if (ride.manualControl) ride.mob.setAiDisabled(ride.previousAiDisabled)
    }

    /** 手动驱动坐骑: 朝向跟随骑手, WASD 给水平位移, 跳跃/飞行给垂直速度 */
    private fun drive(rider: ServerPlayerEntity, mob: MobEntity) {
        val now = rider.server?.ticks?.toLong() ?: return
        val cached = inputs[rider.uuid]
        // 优先使用客户端专发的输入包; 缓存过期才回退到原版 PlayerInput
        val input = if (cached != null && now - cached.tick <= INPUT_TTL_TICKS) {
            cached.input
        } else {
            rider.playerInput
        }

        val forward = (if (input.forward()) 1.0 else 0.0) - (if (input.backward()) 1.0 else 0.0)
        val strafe = (if (input.left()) 1.0 else 0.0) - (if (input.right()) 1.0 else 0.0)

        val yawRad = Math.toRadians(rider.yaw.toDouble())
        val fx = -sin(yawRad)
        val fz = cos(yawRad)
        val rx = cos(yawRad)
        val rz = sin(yawRad)

        var mx = fx * forward + rx * strafe
        var mz = fz * forward + rz * strafe
        val length = kotlin.math.sqrt(mx * mx + mz * mz)
        if (length > 1.0E-4) {
            mx /= length
            mz /= length
        }

        val speed = (mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 1.8)
            .coerceIn(0.12, 0.4)
        val horizontal = if (length > 1.0E-4) speed else 0.0

        // ★ 水平位移直接 move, 不依赖 MobEntity.travel 对"AI 已关闭"的处理:
        //   不同生物对 velocity / travel 的实现差异很大 (马、飞行生物、被骑乘时的
        //   isLogicalSideForUpdatingMovement 等), 直接位移最稳; 垂直交给原版 tick。
        if (horizontal > 0.0) {
            mob.move(MovementType.SELF, Vec3d(mx * horizontal, 0.0, mz * horizontal))
        }

        // 清掉 AI 残留的前进/横移速度, 避免原版 travel 再叠加一份
        mob.setForwardSpeed(0f)
        mob.setSidewaysSpeed(0f)

        val vy = when {
            isFlying(mob) -> when {
                input.jump() -> 0.18
                rider.pitch > 25.0f -> -0.15
                else -> 0.0
            }
            input.jump() && mob.isOnGround -> 0.42
            else -> 0.0
        }

        mob.velocity = Vec3d(0.0, vy, 0.0)
        mob.velocityDirty = true

        mob.setYaw(rider.yaw)
        mob.setHeadYaw(rider.yaw)
        mob.setBodyYaw(rider.yaw)
    }

    /** 视线锥内、最近的合法目标 (不含骑手与坐骑; 需要无方块遮挡) */
    private fun findTarget(player: ServerPlayerEntity, mob: MobEntity): LivingEntity? {
        val world = player.world
        val eye = player.eyePos
        val look = player.getRotationVec(1.0f)

        var best: LivingEntity? = null
        var bestScore = Double.MAX_VALUE

        for (entity in world.getEntitiesByClass(
            LivingEntity::class.java,
            player.boundingBox.expand(TARGET_RANGE),
        ) { it !== player && it !== mob && it.isAlive && !it.isRemoved }) {
            if (entity is ServerPlayerEntity && entity.isSpectator) continue

            val center = entity.pos.add(0.0, entity.height * 0.5, 0.0)
            val delta = center.subtract(eye)
            val distance = delta.length()
            if (distance < 1.0E-4 || distance > TARGET_RANGE) continue

            val dot = delta.normalize().dotProduct(look)
            if (dot < MIN_TARGET_DOT) continue

            // 方块遮挡: 视线到目标之间若有方块, 跳过
            val hit = world.raycast(
                RaycastContext(eye, center, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player),
            )
            if (hit.type != HitResult.Type.MISS && eye.squaredDistanceTo(hit.pos) < distance * distance) continue

            val score = distance / dot
            if (score < bestScore) {
                bestScore = score
                best = entity
            }
        }
        return best
    }

    private fun isForbidden(mob: MobEntity): Boolean =
        mob is EnderDragonEntity || mob is WitherEntity || mob is WardenEntity

    /**
     * 马/猪/炽足兽只有在**确实有控制乘客** (装了鞍) 时才交给原版 ---
     * 没鞍时原版根本不会响应 WASD, 必须由本服务手动驱动。
     */
    private fun usesVanillaControl(mob: MobEntity): Boolean =
        (mob is AbstractHorseEntity || mob is PigEntity || mob is StriderEntity) &&
            mob.controllingPassenger != null

    private fun isFlying(mob: MobEntity): Boolean =
        mob is GhastEntity || mob is PhantomEntity || mob is BatEntity || mob is VexEntity ||
            mob is Flutterer
}
