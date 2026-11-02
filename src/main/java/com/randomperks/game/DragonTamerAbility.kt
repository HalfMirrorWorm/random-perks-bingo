package com.randomperks.game

import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.zombie.Drowned
import net.minecraft.world.entity.monster.ElderGuardian
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Guardian
import net.minecraft.world.entity.monster.illager.Illusioner
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob
import net.minecraft.world.entity.monster.cubemob.MagmaCube
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.piglin.Piglin
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.raid.Raider
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.entity.monster.cubemob.Slime
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.animal.bee.Bee
import net.minecraft.world.entity.animal.frog.Frog
import net.minecraft.world.entity.animal.goat.Goat
import net.minecraft.world.entity.animal.happyghast.HappyGhast
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.animal.equine.Llama
import net.minecraft.world.entity.animal.golem.SnowGolem
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ShulkerBullet
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.HitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 驯龙高手的右键招式。每种生物用自己的手段, 冷却各不相同。
 *
 * 打不中 (没有目标、瞬移失败、攻击伤害为 0) 返回 null, 这次右键交给原版。
 * 不引爆苦力怕, 不让蜜蜂进入刺击冷却, 不把坐骑写进玩家的记分板队伍。
 * 骑手、同队玩家和队友正在骑的生物不进选敌, 范围技也跳过他们。
 */
object DragonTamerAbility {

    private const val MELEE = 12L
    private const val SKELETON = 14L
    private const val BLAZE = 12L
    private const val GHAST = 30L
    private const val WITCH = 24L
    private const val BREEZE = 20L
    private const val SHULKER = 20L
    private const val GUARDIAN = 24L
    private const val ELDER = 36L
    private const val CREEPER = 40L
    private const val RAVAGER = 36L
    private const val GOAT = 16L
    private const val IRON = 18L
    private const val ENDERMAN = 12L
    private const val EVOKER = 30L
    private const val GUST = 16L
    private const val SLIME = 16L
    private const val PHANTOM = 12L
    private const val BEE = 12L
    private const val FROG = 12L

    /** 返回冷却刻数; null 表示这次点击没有使出招 */
    fun use(player: ServerPlayer, mob: Mob, target: LivingEntity?, aim: Vec3): Long? {
        val world = player.level()
        return when (mob) {
            is HappyGhast -> gust(player, mob)
            is Ghast -> ghast(player, world, mob, target, aim)
            is Blaze -> blaze(player, world, mob, aim)
            is Creeper -> creeper(player, world, mob, target, aim)
            is Breeze -> breeze(player, world, mob, aim)
            is Shulker -> shulker(player, world, mob, target, aim)
            is ElderGuardian -> elder(player, world, mob, target)
            is Guardian -> guardian(player, world, mob, target)
            is Evoker -> evoker(player, world, mob)
            is Ravager -> ravager(player, world, mob)
            is Goat -> goat(player, world, mob)
            is IronGolem -> melee(player, mob, target, 5.5, IRON, null)
            is Phantom -> melee(player, mob, target, 6.0, PHANTOM, SoundEvents.PHANTOM_BITE)
            is Bee -> bee(player, world, mob, target)
            is Frog -> frog(player, world, mob, target)
            is MagmaCube -> slam(player, world, mob, true)
            is Slime -> slam(player, world, mob, false)
            is EnderMan -> blink(player, mob, target)
            is WitherSkeleton -> melee(player, mob, target, reachOf(mob), MELEE, null)
            is Pillager -> {
                looseArrow(world, mob, aim, mob.mainHandItem)
                16L
            }
            is Piglin -> if (mob.mainHandItem.`is`(Items.CROSSBOW)) {
                looseArrow(world, mob, aim, mob.mainHandItem)
                16L
            } else {
                melee(player, mob, target, reachOf(mob), MELEE, null)
            }
            is Witch -> witch(player, world, mob, target, aim)
            is RangedAttackMob -> shootVanilla(world, mob, target, aim)
            else -> melee(player, mob, target, reachOf(mob), MELEE, null)
        }
    }

    private fun gust(player: ServerPlayer, mob: HappyGhast): Long {
        val world = player.level()
        val look = player.getViewVector(1.0f)
        val origin = mob.position().add(0.0, mob.bbHeight * 0.45, 0.0).add(look.scale(2.2))
        world.sendParticles(ParticleTypes.GUST, origin.x, origin.y, origin.z, 1, 0.2, 0.15, 0.2, 0.0)
        world.sendParticles(ParticleTypes.SMALL_GUST, origin.x, origin.y, origin.z, 10, 0.7, 0.35, 0.7, 0.04)
        mob.playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.85f, 1.25f)
        for (entity in cone(player, mob, 5.5, 0.35)) {
            val away = entity.position().subtract(mob.position())
            val flat = Vec3(away.x, 0.0, away.z)
            val push = if (flat.lengthSqr() < 1.0e-4) horizontalLook(player.yRot) else flat.normalize()
            entity.push(push.scale(0.9).add(0.0, 0.42, 0.0))
        }
        return GUST
    }

    private fun ghast(
        player: ServerPlayer,
        world: ServerLevel,
        mob: Ghast,
        target: LivingEntity?,
        aim: Vec3,
    ): Long {
        val look = player.getViewVector(1.0f)
        val origin = Vec3(mob.x + look.x * 4.0, mob.getY(0.5) + 0.5, mob.z + look.z * 4.0)
        val to = if (target != null) target.position().add(0.0, target.bbHeight * 0.5, 0.0) else aim
        val velocity = direction(player, to.subtract(origin))
        val fireball = LargeFireball(world, mob, velocity, mob.explosionPower)
        fireball.setPos(origin)
        world.addFreshEntity(fireball)
        mob.playSound(SoundEvents.GHAST_SHOOT, 1.0f, 1.0f)
        return GHAST
    }

    private fun blaze(player: ServerPlayer, world: ServerLevel, mob: Blaze, aim: Vec3): Long {
        val origin = mob.position().add(0.0, mob.bbHeight * 0.5, 0.0)
        val dir = direction(player, aim.subtract(origin))
        val side = perpendicular(dir)
        for (i in -1..1) {
            val velocity = dir.add(side.scale(i * 0.16)).normalize()
            val ball = SmallFireball(world, mob, velocity)
            val spawn = origin.add(dir.scale(1.3)).add(side.scale(i * 0.35))
            ball.setPos(spawn)
            world.addFreshEntity(ball)
        }
        mob.playSound(SoundEvents.BLAZE_SHOOT, 1.0f, 1.0f)
        return BLAZE
    }

    private fun creeper(
        player: ServerPlayer,
        world: ServerLevel,
        mob: Creeper,
        target: LivingEntity?,
        aim: Vec3,
    ): Long {
        val power = if (mob.isPowered) 2.2f else 1.3f
        val safe = power * 2.0 + 1.0
        val look = player.getViewVector(1.0f)
        val eye = player.eyePosition
        val aimed = if (target != null) {
            target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        } else {
            val end = eye.add(look.scale(12.0))
            val hit = world.clip(
                ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player),
            )
            if (hit.type == HitResult.Type.MISS) aim else hit.location
        }
        val point = if (blastClear(player, mob, aimed, safe)) {
            aimed
        } else {
            var found: Vec3? = null
            var distance = safe + 0.5
            while (distance <= 16.0) {
                val candidate = eye.add(look.scale(distance))
                if (blastClear(player, mob, candidate, safe)) {
                    found = candidate
                    break
                }
                distance += 1.0
            }
            found
        }
        if (point == null) {
            coneBlast(player, world, mob, if (mob.isPowered) 10f else 6f)
            return CREEPER
        }
        world.explode(mob, point.x, point.y, point.z, power, Level.ExplosionInteraction.NONE)
        return CREEPER
    }

    /** 近距离放不出安全爆炸时, 只打前方的敌人, 不破坏方块 */
    private fun coneBlast(player: ServerPlayer, world: ServerLevel, mob: Mob, amount: Float) {
        val front = mob.position().add(horizontalLook(player.yRot).scale(2.4)).add(0.0, mob.bbHeight * 0.5, 0.0)
        world.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0.15, 0.15, 0.15, 0.0)
        mob.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.1f)
        val source = mob.damageSources().mobAttack(player)
        for (entity in cone(player, mob, 4.5, 0.25)) {
            if (entity.hurtServer(world, source, amount)) {
                entity.knockback(0.7, mob.x - entity.x, mob.z - entity.z, entity.damageSources().generic(), 0f)
                entity.hurtMarked = true
            }
        }
    }

    private fun blastClear(player: ServerPlayer, mob: Mob, point: Vec3, radius: Double): Boolean {
        val radiusSq = radius * radius
        if (point.distanceToSqr(player.position()) < radiusSq) return false
        if (point.distanceToSqr(mob.position()) < radiusSq) return false
        val box = AABB.ofSize(point, radius * 2.0 + 1.0, radius * 2.0 + 1.0, radius * 2.0 + 1.0)
        for (entity in player.level().getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive }) {
            if (!DragonTamerService.isAlly(player, entity)) continue
            if (entity.distanceToSqr(point) < radiusSq) return false
        }
        return true
    }

    private fun breeze(player: ServerPlayer, world: ServerLevel, mob: Breeze, aim: Vec3): Long {
        val look = player.getViewVector(1.0f)
        val charge = BreezeWindCharge(mob, world)
        charge.setPos(mob.x + look.x * 2.4, mob.firingYPosition, mob.z + look.z * 2.4)
        val delta = aim.subtract(charge.position())
        Projectile.spawnProjectileUsingShoot(
            charge, world, ItemStack.EMPTY, delta.x, delta.y, delta.z, 0.7f, 1.0f,
        )
        mob.playSound(SoundEvents.BREEZE_SHOOT, 1.5f, 1.0f)
        return BREEZE
    }

    private fun shulker(
        player: ServerPlayer,
        world: ServerLevel,
        mob: Shulker,
        target: LivingEntity?,
        aim: Vec3,
    ): Long {
        val aimEntity: Entity = target ?: aimMarker(player, world, aim)
        val bullet = ShulkerBullet(world, mob, aimEntity, Direction.Axis.Y)
        val look = player.getViewVector(1.0f)
        bullet.setPos(bullet.x + look.x * 1.4, bullet.y, bullet.z + look.z * 1.4)
        world.addFreshEntity(bullet)
        mob.playSound(SoundEvents.SHULKER_SHOOT, 1.0f, 1.0f)
        return SHULKER
    }

    /** 没有真实目标时, 潜影贝子弹需要一个会留在世界上的靶子 */
    private fun aimMarker(player: ServerPlayer, world: ServerLevel, aim: Vec3): ArmorStand {
        val stand = ArmorStand(world, aim.x, aim.y, aim.z)
        stand.setInvisible(true)
        stand.setNoGravity(true)
        stand.setInvulnerable(true)
        stand.isSilent = true
        world.addFreshEntity(stand)
        val now = player.level().getServer().tickCount.toLong()
        DragonTamerService.trackAimMarker(stand, now + 80L)
        return stand
    }

    private fun guardian(
        player: ServerPlayer,
        world: ServerLevel,
        mob: Guardian,
        target: LivingEntity?,
    ): Long? {
        if (target == null) return null
        beam(world, mob.eyePosition, target.eyePosition)
        target.hurtServer(world, mob.damageSources().indirectMagic(mob, player), 6.0f)
        mob.playSound(SoundEvents.GUARDIAN_ATTACK, 1.0f, 1.0f)
        return GUARDIAN
    }

    private fun elder(
        player: ServerPlayer,
        world: ServerLevel,
        mob: ElderGuardian,
        target: LivingEntity?,
    ): Long? {
        if (target == null) return null
        beam(world, mob.eyePosition, target.eyePosition)
        target.hurtServer(world, mob.damageSources().indirectMagic(mob, player), 8.0f)
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, 600, 1), player)
        mob.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 1.0f)
        return ELDER
    }

    private fun beam(world: ServerLevel, from: Vec3, to: Vec3) {
        for (i in 1..5) {
            val point = from.lerp(to, i / 5.0)
            world.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    private fun evoker(player: ServerPlayer, world: ServerLevel, mob: Evoker): Long? {
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forwardX = -sin(yaw)
        val forwardZ = cos(yaw)
        var spawned = 0
        for (i in 0 until 8) {
            val distance = 2.5 + i * 1.25
            if (placeFang(world, mob, mob.x + forwardX * distance, mob.z + forwardZ * distance, mob.y, yaw.toFloat(), i * 2)) {
                spawned++
            }
        }
        if (spawned == 0) return null
        mob.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.0f)
        return EVOKER
    }

    private fun placeFang(
        world: ServerLevel,
        owner: LivingEntity,
        x: Double,
        z: Double,
        y: Double,
        yaw: Float,
        warmup: Int,
    ): Boolean {
        var pos = BlockPos.containing(x, y, z)
        val limit = pos.y - 6
        var surface = 0.0
        var found = false
        while (pos.y >= limit) {
            val below = pos.below()
            val ground = world.getBlockState(below)
            if (ground.isFaceSturdy(world, below, Direction.UP)) {
                val shape = world.getBlockState(pos).getCollisionShape(world, pos)
                if (!shape.isEmpty) surface = shape.max(Direction.Axis.Y)
                found = true
                break
            }
            pos = below
        }
        if (!found) return false
        world.addFreshEntity(EvokerFangs(world, x, pos.y + surface, z, yaw, warmup, owner))
        return true
    }

    private fun ravager(player: ServerPlayer, world: ServerLevel, mob: Ravager): Long {
        val center = mob.boundingBox.center
        world.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 24, 0.7, 0.45, 0.7, 0.02)
        mob.playSound(SoundEvents.RAVAGER_ROAR, 1.0f, 1.0f)
        val source = mob.damageSources().mobAttack(player)
        for (entity in cone(player, mob, 4.2, 0.15)) {
            entity.hurtServer(world, source, 6.0f)
            val dx = entity.x - mob.x
            val dz = entity.z - mob.z
            val denom = (dx * dx + dz * dz).coerceAtLeast(0.001)
            var vx = dx / denom * 4.0
            var vz = dz / denom * 4.0
            val speed = sqrt(vx * vx + vz * vz)
            // 贴脸时原版公式会把人甩出好几格, 骑乘战里收在 1.5 格/刻
            if (speed > 1.5) {
                vx *= 1.5 / speed
                vz *= 1.5 / speed
            }
            entity.push(vx, 0.45, vz)
        }
        return RAVAGER
    }

    private fun goat(player: ServerPlayer, world: ServerLevel, mob: Goat): Long {
        val front = mob.position().add(horizontalLook(player.yRot).scale(1.6)).add(0.0, mob.bbHeight * 0.55, 0.0)
        world.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0.1, 0.1, 0.1, 0.0)
        mob.playSound(SoundEvents.GOAT_RAM_IMPACT, 1.0f, 1.0f)
        val amount = (mob.getAttribute(Attributes.ATTACK_DAMAGE)?.value ?: 6.0).toFloat().coerceAtLeast(5f)
        val source = mob.damageSources().mobAttack(player)
        for (entity in cone(player, mob, 3.6, 0.4)) {
            if (entity.hurtServer(world, source, amount)) {
                entity.knockback(1.8, mob.x - entity.x, mob.z - entity.z, entity.damageSources().generic(), 0f)
                entity.hurtMarked = true
            }
        }
        return GOAT
    }

    private fun bee(player: ServerPlayer, world: ServerLevel, mob: Bee, target: LivingEntity?): Long? {
        if (target == null || mob.distanceToSqr(target) > 16.0) return null
        target.hurtServer(world, mob.damageSources().sting(mob), 2.0f)
        target.addEffect(MobEffectInstance(MobEffects.POISON, 200, 0), mob)
        mob.playSound(SoundEvents.BEE_STING, 1.0f, 1.0f)
        mob.swing(InteractionHand.MAIN_HAND, true)
        return BEE
    }

    private fun frog(player: ServerPlayer, world: ServerLevel, mob: Frog, target: LivingEntity?): Long? {
        if (target == null || mob.distanceToSqr(target) > 64.0) return null
        target.hurtServer(world, mob.damageSources().mobAttack(player), 3.0f)
        val pull = mob.position().add(0.0, mob.bbHeight * 0.4, 0.0).subtract(target.position())
        val length = pull.length().coerceAtLeast(0.001)
        target.push(pull.x / length * 0.7, 0.35, pull.z / length * 0.7)
        mob.playSound(SoundEvents.FROG_TONGUE, 1.0f, 1.0f)
        return FROG
    }

    private fun slam(player: ServerPlayer, world: ServerLevel, mob: AbstractCubeMob, ignite: Boolean): Long {
        val radius = (mob.size + 0.8).coerceAtLeast(1.6)
        val amount = (mob.getAttribute(Attributes.ATTACK_DAMAGE)?.value ?: mob.size.toDouble())
            .toFloat()
            .coerceAtLeast(mob.size.toFloat())
        val source = mob.damageSources().mobAttack(player)
        val box = mob.boundingBox.inflate(radius, 0.6, radius)
        for (entity in world.getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !it.isSpectator }) {
            if (DragonTamerService.isAlly(player, entity)) continue
            if (entity.distanceToSqr(mob) > radius * radius) continue
            entity.hurtServer(world, source, amount)
            if (ignite) entity.igniteForSeconds(4.0f)
            entity.knockback(0.45, mob.x - entity.x, mob.z - entity.z, entity.damageSources().generic(), 0f)
            entity.hurtMarked = true
        }
        val sound = if (ignite) SoundEvents.MAGMA_CUBE_SQUISH else SoundEvents.SLIME_ATTACK
        mob.playSound(sound, 1.0f, 1.0f)
        world.sendParticles(
            if (ignite) ParticleTypes.FLAME else ParticleTypes.ITEM_SLIME,
            mob.x, mob.getY(0.5), mob.z,
            8, mob.bbWidth * 0.6, 0.2, mob.bbWidth * 0.6, 0.02,
        )
        return SLIME
    }

    private fun blink(player: ServerPlayer, mob: EnderMan, target: LivingEntity?): Long? {
        val now = player.level().getServer().tickCount.toLong()
        val from = mob.position()
        val look = player.getViewVector(1.0f)
        var arrived = false
        if (target != null) {
            val back = player.position().subtract(target.position())
            val flat = Vec3(back.x, 0.0, back.z)
            val offset = if (flat.lengthSqr() < 1.0e-4) horizontalLook(player.yRot).scale(-1.5) else flat.normalize().scale(1.5)
            val dest = target.position().add(offset)
            arrived = mob.randomTeleport(dest.x, dest.y, dest.z, true)
        }
        if (!arrived) {
            val eye = player.eyePosition
            val end = eye.add(look.scale(32.0))
            val hit = player.level().clip(
                ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player),
            )
            if (hit.type != HitResult.Type.BLOCK) return null
            val dest = hit.location.subtract(look.scale(0.8))
            if (!mob.randomTeleport(dest.x, dest.y, dest.z, true)) return null
        }
        mob.setYRot(player.yRot)
        mob.setYHeadRot(player.yRot)
        mob.setYBodyRot(player.yRot)
        mob.fallDistance = 0.0
        player.fallDistance = 0.0
        mob.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f)
        DragonTamerService.markReposition(player, from, now + 10L)
        return ENDERMAN
    }

    private fun witch(
        player: ServerPlayer,
        world: ServerLevel,
        mob: Witch,
        target: LivingEntity?,
        aim: Vec3,
    ): Long? {
        if (mob.isDrinkingPotion) return null
        // 原版对着掠夺者会丢治疗。骑着打的时候改成伤害药水, 避免把敌人奶起来。
        if (target != null && target !is Raider) {
            mob.performRangedAttack(target, 1.0f)
            return WITCH
        }
        val to = if (target != null) target.position().add(0.0, target.bbHeight * 0.5, 0.0) else aim
        val stack = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING)
        val delta = direction(player, to.subtract(mob.eyePosition))
        Projectile.spawnProjectileUsingShoot(
            { potionWorld, shooter, potion -> ThrownSplashPotion(potionWorld, shooter, potion) },
            world,
            stack,
            mob,
            delta.x,
            delta.y,
            delta.z,
            0.75f,
            1.0f,
        )
        mob.playSound(SoundEvents.WITCH_THROW, 1.0f, 0.9f)
        return WITCH
    }

    private fun shootVanilla(world: ServerLevel, mob: RangedAttackMob, target: LivingEntity?, aim: Vec3): Long {
        val shot = target ?: ArmorStand(world, aim.x, aim.y, aim.z)
        mob.performRangedAttack(shot, 1.0f)
        if (target == null) shot.discard()
        val shooter = mob as Mob
        return when (shooter) {
            is Drowned -> 20L
            is SnowGolem -> 8L
            is Llama -> 16L
            is AbstractSkeleton -> SKELETON
            is Illusioner -> 16L
            else -> 16L
        }
    }

    /** 弩没装填时 [PillagerEntity.shootAt] 不会出箭, 这里直接生成一支 */
    private fun looseArrow(world: ServerLevel, mob: Mob, aim: Vec3, shotFrom: ItemStack) {
        val stack = ItemStack(Items.ARROW)
        val weapon = if (shotFrom.`is`(Items.CROSSBOW)) shotFrom else ItemStack(Items.CROSSBOW)
        val arrow = Arrow(world, mob, stack, weapon)
        val delta = direction(mob, aim.subtract(mob.eyePosition))
        Projectile.spawnProjectileUsingShoot(arrow, world, stack, delta.x, delta.y, delta.z, 1.6f, 1.0f)
        mob.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.0f)
    }

    private fun melee(
        player: ServerPlayer,
        mob: Mob,
        target: LivingEntity?,
        reach: Double,
        cooldown: Long,
        sound: SoundEvent?,
    ): Long? {
        if (target == null) return null
        val damage = mob.getAttribute(Attributes.ATTACK_DAMAGE)?.value ?: 0.0
        if (damage <= 0.0) return null
        if (mob.distanceToSqr(target) > reach * reach) return null
        mob.lookAt(EntityAnchorArgument.Anchor.EYES, target.eyePosition)
        val hit = mob.doHurtTarget(player.level(), target)
        // 铁傀儡进了攻击动作就会挥臂, 打空也吃冷却, 免得连点把方块放出去
        if (!hit && mob !is IronGolem) return null
        mob.swing(InteractionHand.MAIN_HAND, true)
        if (hit && sound != null) mob.playSound(sound, 1.0f, 1.0f)
        return cooldown
    }

    private fun reachOf(mob: Mob): Double = (mob.bbWidth + 3.2).coerceIn(3.2, 6.0)

    private fun cone(player: ServerPlayer, mob: Mob, range: Double, minDot: Double): List<LivingEntity> {
        val forward = horizontalLook(player.yRot)
        val box = mob.boundingBox.inflate(range, range, range)
        val found = ArrayList<LivingEntity>()
        for (entity in player.level().getEntitiesOfClass(LivingEntity::class.java, box) { it.isAlive && !it.isSpectator }) {
            if (DragonTamerService.isAlly(player, entity)) continue
            val delta = entity.position().subtract(mob.position())
            if (delta.length() > range) continue
            val flat = Vec3(delta.x, 0.0, delta.z)
            if (flat.lengthSqr() > 0.09 && flat.normalize().dot(forward) < minDot) continue
            found.add(entity)
        }
        return found
    }

    private fun horizontalLook(yaw: Float): Vec3 {
        val rad = Math.toRadians(yaw.toDouble())
        return Vec3(-sin(rad), 0.0, cos(rad))
    }

    private fun direction(player: ServerPlayer, delta: Vec3): Vec3 =
        if (delta.lengthSqr() < 1.0e-4) player.getViewVector(1.0f) else delta.normalize()

    private fun direction(mob: Mob, delta: Vec3): Vec3 =
        if (delta.lengthSqr() < 1.0e-4) mob.lookAngle else delta.normalize()

    private fun perpendicular(dir: Vec3): Vec3 {
        val side = Vec3(-dir.z, 0.0, dir.x)
        return if (side.lengthSqr() < 1.0e-4) Vec3(1.0, 0.0, 0.0) else side.normalize()
    }
}
