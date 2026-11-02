package com.randomperks.game

import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.Flutterer
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.MobEntity
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
import net.minecraft.entity.passive.HappyGhastEntity
import net.minecraft.entity.passive.PigEntity
import net.minecraft.entity.passive.StriderEntity
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.util.UUID

/**
 * "驯龙高手" (金 · 唤法) 的服务端逻辑。
 *
 * - 右键任意生物 → `startRiding(mob, true)` 骑上去 (玩家与 Boss 除外);
 * - 骑乘时停掉坐骑 AI。移动由客户端按 [DragonTamerMotion] 直接驾驶
 *   (地面 WASD + 跳跃起跳; 飞行沿准星, 跳跃上升, 松手悬停), 服务端收载具移动包;
 * - 右键 → 让坐骑使出它自己的招式, 冷却按招式长短分开, 见 [DragonTamerAbility]。
 *   打不中时这次右键放行给原版。末影人瞬移后的短窗口里丢掉还停在旧位置的载具包。
 *
 * 骑乘状态按骑手 UUID 记录; 下马/死亡/词条被清掉/掉线时恢复坐骑原本的 AI 状态。
 */
object DragonTamerService {

    const val PERK_ID = "summon_dragon_tamer"

    /** 能力选敌的最大距离 (格) */
    private const val TARGET_RANGE = 24.0

    /** 视线锥的最小 dot (越小锥角越大) */
    private const val MIN_TARGET_DOT = 0.55

    private data class Ride(
        val mob: MobEntity,
        val previousAiDisabled: Boolean,
        /** 是否由本服务手动驱动; 马/猪/炽足兽, 以及已经把玩家当成控制者的生物交给原版 */
        val manualControl: Boolean,
        /** 手动驾驶的飞行生物会暂时关掉重力, 下马时还原 */
        val previousNoGravity: Boolean,
        val flying: Boolean,
        /** 末影人瞬移后, 这个刻之前丢掉还停在旧坐标的载具包 */
        var repositionUntilTick: Long = 0,
        var repositionFrom: Vec3d? = null,
    )

    private class AimMarker(val stand: ArmorStandEntity, val discardAt: Long)

    private val active = mutableMapOf<UUID, Ride>()

    /** 潜影贝对着空气射击时的隐形靶子, 到点丢掉 */
    private val aimMarkers = mutableListOf<AimMarker>()

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

        // 清理失效骑乘, 并把朝向转给旁观者。位移本身由客户端驾驶。
        ServerTickEvents.START_SERVER_TICK.register { server ->
            val now = server.ticks.toLong()
            if (aimMarkers.isNotEmpty()) {
                val markers = aimMarkers.iterator()
                while (markers.hasNext()) {
                    val marker = markers.next()
                    if (marker.stand.isRemoved || now >= marker.discardAt) {
                        if (!marker.stand.isRemoved) marker.stand.discard()
                        markers.remove()
                    }
                }
            }
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
                    continue
                }
                if (ride.manualControl) orient(rider, mob, ride.flying)
            }
        }
    }

    /** 尝试骑上一只生物 (失败返回 false, 交还原版交互) */
    fun tryMount(rider: ServerPlayerEntity, target: Entity): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasDragonTamer(rider)) return false
        if (rider.hasVehicle()) return false

        val mob = target as? MobEntity ?: return false
        if (!mob.isAlive || mob.isRemoved) return false
        if (isForbidden(mob)) return false

        // 没鞍的马 / 猪 / 炽足兽不允许骑乘 —— 交还原版交互 (喂食/开背包等)
        if (usesVanillaControl(mob) && !mob.hasSaddleEquipped()) return false

        if (!rider.startRiding(mob, true, true)) return false

        // 马/猪/炽足兽, 以及套了挽具的快乐恶魂这类"玩家就是控制者"的生物,
        // 原版已经用跳跃键上升, 不要再关掉 AI 跟它抢方向。
        val manual = !usesVanillaControl(mob) && mob.controllingPassenger !== rider
        val flying = manual && isFlyingMount(mob)
        val previousAi = mob.isAiDisabled
        val previousNoGravity = mob.hasNoGravity()
        active[rider.uuid] = Ride(mob, previousAi, manual, previousNoGravity, flying)

        if (manual) {
            mob.setAiDisabled(true)
            mob.navigation.stop()
            // 蜜蜂、蝙蝠、恼鬼走的是带重力的 travel, 不关的话按住跳也飞不起来
            if (flying) mob.setNoGravity(true)
        }
        mob.setTarget(null)
        return true
    }

    /** 骑乘时右键: 让坐骑使用能力; 返回 true 表示本次右键已被消耗 */
    fun useAbility(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasDragonTamer(player)) return false

        val mob = player.vehicle as? MobEntity ?: return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        val now = player.entityWorld.server?.ticks?.toLong() ?: return false
        if (now < (state.cooldowns[PERK_ID] ?: 0L)) return false

        // 不再要求"视野里必须有生物": 有目标就瞄准目标, 没有目标就朝准星方向
        val target = findTarget(player, mob)
        val aim = target?.let { it.entityPos.add(0.0, it.height * 0.5, 0.0) }
            ?: player.eyePos.add(player.getRotationVec(1.0f).multiply(TARGET_RANGE))

        val cooldown = DragonTamerAbility.use(player, mob, target, aim) ?: return false
        state.cooldowns[PERK_ID] = now + cooldown
        player.swingHand(Hand.MAIN_HAND)
        return true
    }

    /** 对局重置: 让所有骑手下来并恢复坐骑 AI */
    fun clear() {
        for ((uuid, ride) in active) {
            ServerHolder.server?.playerManager?.getPlayer(uuid)?.stopRiding()
            restore(ride)
        }
        active.clear()
        for (marker in aimMarkers) {
            if (!marker.stand.isRemoved) marker.stand.discard()
        }
        aimMarkers.clear()
    }

    private fun restore(ride: Ride) {
        if (!ride.manualControl) return
        ride.mob.setAiDisabled(ride.previousAiDisabled)
        if (ride.flying) ride.mob.setNoGravity(ride.previousNoGravity)
    }

    /** 该玩家当前是否在手动操控这只生物 (供 MobEntity.getControllingPassenger 的 mixin 判定) */
    fun isManualRide(rider: ServerPlayerEntity, mob: MobEntity): Boolean {
        val ride = active[rider.uuid] ?: return false
        return ride.manualControl && ride.mob === mob
    }

    /**
     * 服务端只同步朝向。位移由客户端驾驶后经载具包送回来;
     * 在包到达前先把身子转过去, 旁边的玩家不用等下一拍。
     * 飞行坐骑每拍清掉坠落距离, 俯冲落地不吃摔落伤。
     */
    private fun orient(rider: ServerPlayerEntity, mob: MobEntity, flying: Boolean) {
        mob.setYaw(rider.yaw)
        mob.setHeadYaw(rider.yaw)
        mob.setBodyYaw(rider.yaw)
        if (flying) {
            mob.setPitch(rider.pitch * 0.5f)
            mob.fallDistance = 0.0
            rider.fallDistance = 0.0
        } else {
            mob.setPitch(0.0f)
        }
    }

    /** 末影人已经落到新位置。接下来几拍里, 还停在 [from] 的载具包不要拿来改坐标。 */
    fun markReposition(rider: ServerPlayerEntity, from: Vec3d, untilTick: Long) {
        val ride = active[rider.uuid] ?: return
        ride.repositionFrom = from
        ride.repositionUntilTick = untilTick
        rider.networkHandler.sendPacket(VehicleMoveS2CPacket.fromVehicle(ride.mob))
    }

    /**
     * 瞬移窗口内, 离当前坐标超过 4 格、或者仍更靠近瞬移前位置的包是过期包。
     * 跟上了的驾驶包离当前坐标很近, 照常收下。
     */
    fun shouldIgnoreStaleVehicleMove(player: ServerPlayerEntity, packetPos: Vec3d): Boolean {
        val ride = active[player.uuid] ?: return false
        val now = player.entityWorld.server?.ticks?.toLong() ?: return false
        if (now > ride.repositionUntilTick) return false
        if (player.vehicle !== ride.mob) return false
        val fromMob = packetPos.squaredDistanceTo(ride.mob.entityPos)
        val origin = ride.repositionFrom
        // 4 格以上是旧坐标; 短距离瞬移则看包是否还更靠近瞬移前的位置
        val stale = fromMob > 16.0 ||
            (origin != null && packetPos.squaredDistanceTo(origin) + 0.25 < fromMob)
        if (!stale) return false
        player.networkHandler.sendPacket(VehicleMoveS2CPacket.fromVehicle(ride.mob))
        return true
    }

    fun trackAimMarker(stand: ArmorStandEntity, discardAt: Long) {
        aimMarkers.add(AimMarker(stand, discardAt))
    }

    /** 骑手、同队玩家, 以及队友正在骑的那只生物 */
    fun isAlly(rider: ServerPlayerEntity, entity: Entity): Boolean {
        if (entity === rider || entity === rider.vehicle) return true
        val team = rider.scoreboardTeam?.name
        if (team != null && entity.scoreboardTeam?.name == team) return true
        if (carriesTeammate(rider, entity, team)) return true
        val root = entity.rootVehicle
        if (root !== entity && carriesTeammate(rider, root, team)) return true
        return false
    }

    private fun carriesTeammate(rider: ServerPlayerEntity, vehicle: Entity, team: String?): Boolean {
        for (passenger in vehicle.passengerList) {
            if (passenger === rider) return true
            if (team != null && passenger is ServerPlayerEntity && passenger.scoreboardTeam?.name == team) {
                return true
            }
        }
        return false
    }

    /** 视线锥内、最近的合法目标 (不含骑手、队友和坐骑; 需要无方块遮挡) */
    private fun findTarget(player: ServerPlayerEntity, mob: MobEntity): LivingEntity? {
        val world = player.entityWorld
        val eye = player.eyePos
        val look = player.getRotationVec(1.0f)

        var best: LivingEntity? = null
        var bestScore = Double.MAX_VALUE

        for (entity in world.getEntitiesByClass(
            LivingEntity::class.java,
            player.boundingBox.expand(TARGET_RANGE),
        ) { it !== player && it !== mob && it.isAlive && !it.isRemoved }) {
            if (entity is ServerPlayerEntity && entity.isSpectator) continue
            if (entity is ArmorStandEntity) continue
            if (isAlly(player, entity)) continue

            val center = entity.entityPos.add(0.0, entity.height * 0.5, 0.0)
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

    private fun usesVanillaControl(mob: MobEntity): Boolean =
        mob is AbstractHorseEntity || mob is PigEntity || mob is StriderEntity

    /** 马/猪/炽足兽, 以及套了挽具的快乐恶魂, 继续用原版驾驶 */
    fun isManuallySteered(mob: MobEntity): Boolean {
        if (usesVanillaControl(mob)) return false
        if (mob is HappyGhastEntity && mob.isWearingBodyArmor()) return false
        return true
    }

    fun isFlyingMount(mob: MobEntity): Boolean =
        mob is GhastEntity || mob is PhantomEntity || mob is BatEntity || mob is VexEntity ||
            mob is BlazeEntity || mob is Flutterer ||
            mob.getAttributeInstance(EntityAttributes.FLYING_SPEED) != null
}
