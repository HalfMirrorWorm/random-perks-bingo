package com.randomperks.game

import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.component.type.PotionContentsComponent
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.ai.RangedAttackMob
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.AbstractSkeletonEntity
import net.minecraft.entity.mob.BlazeEntity
import net.minecraft.entity.mob.BreezeEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.DrownedEntity
import net.minecraft.entity.mob.ElderGuardianEntity
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.mob.EvokerEntity
import net.minecraft.entity.mob.EvokerFangsEntity
import net.minecraft.entity.mob.GhastEntity
import net.minecraft.entity.mob.GuardianEntity
import net.minecraft.entity.mob.IllusionerEntity
import net.minecraft.entity.mob.MagmaCubeEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.mob.PhantomEntity
import net.minecraft.entity.mob.PiglinEntity
import net.minecraft.entity.mob.PillagerEntity
import net.minecraft.entity.mob.RavagerEntity
import net.minecraft.entity.raid.RaiderEntity
import net.minecraft.entity.mob.ShulkerEntity
import net.minecraft.entity.mob.SlimeEntity
import net.minecraft.entity.mob.WitchEntity
import net.minecraft.entity.mob.WitherSkeletonEntity
import net.minecraft.entity.passive.BeeEntity
import net.minecraft.entity.passive.FrogEntity
import net.minecraft.entity.passive.GoatEntity
import net.minecraft.entity.passive.HappyGhastEntity
import net.minecraft.entity.passive.IronGolemEntity
import net.minecraft.entity.passive.LlamaEntity
import net.minecraft.entity.passive.SnowGolemEntity
import net.minecraft.entity.projectile.ArrowEntity
import net.minecraft.entity.projectile.BreezeWindChargeEntity
import net.minecraft.entity.projectile.FireballEntity
import net.minecraft.entity.projectile.ProjectileEntity
import net.minecraft.entity.projectile.ShulkerBulletEntity
import net.minecraft.entity.projectile.SmallFireballEntity
import net.minecraft.entity.projectile.thrown.SplashPotionEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.potion.Potions
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
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
    fun use(player: ServerPlayerEntity, mob: MobEntity, target: LivingEntity?, aim: Vec3d): Long? {
        val world = player.entityWorld
        return when (mob) {
            is HappyGhastEntity -> gust(player, mob)
            is GhastEntity -> ghast(player, world, mob, target, aim)
            is BlazeEntity -> blaze(player, world, mob, aim)
            is CreeperEntity -> creeper(player, world, mob, target, aim)
            is BreezeEntity -> breeze(player, world, mob, aim)
            is ShulkerEntity -> shulker(player, world, mob, target, aim)
            is ElderGuardianEntity -> elder(player, world, mob, target)
            is GuardianEntity -> guardian(player, world, mob, target)
            is EvokerEntity -> evoker(player, world, mob)
            is RavagerEntity -> ravager(player, world, mob)
            is GoatEntity -> goat(player, world, mob)
            is IronGolemEntity -> melee(player, mob, target, 5.5, IRON, null)
            is PhantomEntity -> melee(player, mob, target, 6.0, PHANTOM, SoundEvents.ENTITY_PHANTOM_BITE)
            is BeeEntity -> bee(player, world, mob, target)
            is FrogEntity -> frog(player, world, mob, target)
            is MagmaCubeEntity -> slam(player, world, mob, true)
            is SlimeEntity -> slam(player, world, mob, false)
            is EndermanEntity -> blink(player, mob, target)
            is WitherSkeletonEntity -> melee(player, mob, target, reachOf(mob), MELEE, null)
            is PillagerEntity -> {
                looseArrow(world, mob, aim, mob.mainHandStack)
                16L
            }
            is PiglinEntity -> if (mob.mainHandStack.isOf(Items.CROSSBOW)) {
                looseArrow(world, mob, aim, mob.mainHandStack)
                16L
            } else {
                melee(player, mob, target, reachOf(mob), MELEE, null)
            }
            is WitchEntity -> witch(player, world, mob, target, aim)
            is RangedAttackMob -> shootVanilla(world, mob, target, aim)
            else -> melee(player, mob, target, reachOf(mob), MELEE, null)
        }
    }

    private fun gust(player: ServerPlayerEntity, mob: HappyGhastEntity): Long {
        val world = player.entityWorld
        val look = player.getRotationVec(1.0f)
        val origin = mob.entityPos.add(0.0, mob.height * 0.45, 0.0).add(look.multiply(2.2))
        world.spawnParticles(ParticleTypes.GUST, origin.x, origin.y, origin.z, 1, 0.2, 0.15, 0.2, 0.0)
        world.spawnParticles(ParticleTypes.SMALL_GUST, origin.x, origin.y, origin.z, 10, 0.7, 0.35, 0.7, 0.04)
        mob.playSound(SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 0.85f, 1.25f)
        for (entity in cone(player, mob, 5.5, 0.35)) {
            val away = entity.entityPos.subtract(mob.entityPos)
            val flat = Vec3d(away.x, 0.0, away.z)
            val push = if (flat.lengthSquared() < 1.0e-4) horizontalLook(player.yaw) else flat.normalize()
            entity.addVelocity(push.multiply(0.9).add(0.0, 0.42, 0.0))
        }
        return GUST
    }

    private fun ghast(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: GhastEntity,
        target: LivingEntity?,
        aim: Vec3d,
    ): Long {
        val look = player.getRotationVec(1.0f)
        val origin = Vec3d(mob.x + look.x * 4.0, mob.getBodyY(0.5) + 0.5, mob.z + look.z * 4.0)
        val to = if (target != null) target.entityPos.add(0.0, target.height * 0.5, 0.0) else aim
        val velocity = direction(player, to.subtract(origin))
        val fireball = FireballEntity(world, mob, velocity, mob.fireballStrength)
        fireball.setPosition(origin)
        world.spawnEntity(fireball)
        mob.playSound(SoundEvents.ENTITY_GHAST_SHOOT, 1.0f, 1.0f)
        return GHAST
    }

    private fun blaze(player: ServerPlayerEntity, world: ServerWorld, mob: BlazeEntity, aim: Vec3d): Long {
        val origin = mob.entityPos.add(0.0, mob.height * 0.5, 0.0)
        val dir = direction(player, aim.subtract(origin))
        val side = perpendicular(dir)
        for (i in -1..1) {
            val velocity = dir.add(side.multiply(i * 0.16)).normalize()
            val ball = SmallFireballEntity(world, mob, velocity)
            val spawn = origin.add(dir.multiply(1.3)).add(side.multiply(i * 0.35))
            ball.setPosition(spawn)
            world.spawnEntity(ball)
        }
        mob.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f)
        return BLAZE
    }

    private fun creeper(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: CreeperEntity,
        target: LivingEntity?,
        aim: Vec3d,
    ): Long {
        val power = if (mob.isCharged) 2.2f else 1.3f
        val safe = power * 2.0 + 1.0
        val look = player.getRotationVec(1.0f)
        val eye = player.eyePos
        val aimed = if (target != null) {
            target.entityPos.add(0.0, target.height * 0.5, 0.0)
        } else {
            val end = eye.add(look.multiply(12.0))
            val hit = world.raycast(
                RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player),
            )
            if (hit.type == HitResult.Type.MISS) aim else hit.pos
        }
        val point = if (blastClear(player, mob, aimed, safe)) {
            aimed
        } else {
            var found: Vec3d? = null
            var distance = safe + 0.5
            while (distance <= 16.0) {
                val candidate = eye.add(look.multiply(distance))
                if (blastClear(player, mob, candidate, safe)) {
                    found = candidate
                    break
                }
                distance += 1.0
            }
            found
        }
        if (point == null) {
            coneBlast(player, world, mob, if (mob.isCharged) 10f else 6f)
            return CREEPER
        }
        world.createExplosion(mob, point.x, point.y, point.z, power, World.ExplosionSourceType.NONE)
        return CREEPER
    }

    /** 近距离放不出安全爆炸时, 只打前方的敌人, 不破坏方块 */
    private fun coneBlast(player: ServerPlayerEntity, world: ServerWorld, mob: MobEntity, amount: Float) {
        val front = mob.entityPos.add(horizontalLook(player.yaw).multiply(2.4)).add(0.0, mob.height * 0.5, 0.0)
        world.spawnParticles(ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0.15, 0.15, 0.15, 0.0)
        mob.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.9f, 1.1f)
        val source = mob.damageSources.mobAttack(player)
        for (entity in cone(player, mob, 4.5, 0.25)) {
            if (entity.damage(world, source, amount)) {
                entity.takeKnockback(0.7, mob.x - entity.x, mob.z - entity.z)
                entity.velocityDirty = true
            }
        }
    }

    private fun blastClear(player: ServerPlayerEntity, mob: MobEntity, point: Vec3d, radius: Double): Boolean {
        val radiusSq = radius * radius
        if (point.squaredDistanceTo(player.entityPos) < radiusSq) return false
        if (point.squaredDistanceTo(mob.entityPos) < radiusSq) return false
        val box = Box.of(point, radius * 2.0 + 1.0, radius * 2.0 + 1.0, radius * 2.0 + 1.0)
        for (entity in player.entityWorld.getEntitiesByClass(LivingEntity::class.java, box) { it.isAlive }) {
            if (!DragonTamerService.isAlly(player, entity)) continue
            if (entity.squaredDistanceTo(point) < radiusSq) return false
        }
        return true
    }

    private fun breeze(player: ServerPlayerEntity, world: ServerWorld, mob: BreezeEntity, aim: Vec3d): Long {
        val look = player.getRotationVec(1.0f)
        val charge = BreezeWindChargeEntity(mob, world)
        charge.setPosition(mob.x + look.x * 2.4, mob.chargeY, mob.z + look.z * 2.4)
        val delta = aim.subtract(charge.entityPos)
        ProjectileEntity.spawnWithVelocity(
            charge, world, ItemStack.EMPTY, delta.x, delta.y, delta.z, 0.7f, 1.0f,
        )
        mob.playSound(SoundEvents.ENTITY_BREEZE_SHOOT, 1.5f, 1.0f)
        return BREEZE
    }

    private fun shulker(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: ShulkerEntity,
        target: LivingEntity?,
        aim: Vec3d,
    ): Long {
        val aimEntity: Entity = target ?: aimMarker(player, world, aim)
        val bullet = ShulkerBulletEntity(world, mob, aimEntity, Direction.Axis.Y)
        val look = player.getRotationVec(1.0f)
        bullet.setPosition(bullet.x + look.x * 1.4, bullet.y, bullet.z + look.z * 1.4)
        world.spawnEntity(bullet)
        mob.playSound(SoundEvents.ENTITY_SHULKER_SHOOT, 1.0f, 1.0f)
        return SHULKER
    }

    /** 没有真实目标时, 潜影贝子弹需要一个会留在世界上的靶子 */
    private fun aimMarker(player: ServerPlayerEntity, world: ServerWorld, aim: Vec3d): ArmorStandEntity {
        val stand = ArmorStandEntity(world, aim.x, aim.y, aim.z)
        stand.setInvisible(true)
        stand.setNoGravity(true)
        stand.setInvulnerable(true)
        stand.isSilent = true
        world.spawnEntity(stand)
        val now = player.entityWorld.server?.ticks?.toLong() ?: 0L
        DragonTamerService.trackAimMarker(stand, now + 80L)
        return stand
    }

    private fun guardian(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: GuardianEntity,
        target: LivingEntity?,
    ): Long? {
        if (target == null) return null
        beam(world, mob.eyePos, target.eyePos)
        target.damage(world, mob.damageSources.indirectMagic(mob, player), 6.0f)
        mob.playSound(SoundEvents.ENTITY_GUARDIAN_ATTACK, 1.0f, 1.0f)
        return GUARDIAN
    }

    private fun elder(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: ElderGuardianEntity,
        target: LivingEntity?,
    ): Long? {
        if (target == null) return null
        beam(world, mob.eyePos, target.eyePos)
        target.damage(world, mob.damageSources.indirectMagic(mob, player), 8.0f)
        target.addStatusEffect(StatusEffectInstance(StatusEffects.MINING_FATIGUE, 600, 1), player)
        mob.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f)
        return ELDER
    }

    private fun beam(world: ServerWorld, from: Vec3d, to: Vec3d) {
        for (i in 1..5) {
            val point = from.lerp(to, i / 5.0)
            world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    private fun evoker(player: ServerPlayerEntity, world: ServerWorld, mob: EvokerEntity): Long? {
        val yaw = Math.toRadians(player.yaw.toDouble())
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
        mob.playSound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f)
        return EVOKER
    }

    private fun placeFang(
        world: ServerWorld,
        owner: LivingEntity,
        x: Double,
        z: Double,
        y: Double,
        yaw: Float,
        warmup: Int,
    ): Boolean {
        var pos = BlockPos.ofFloored(x, y, z)
        val limit = pos.y - 6
        var surface = 0.0
        var found = false
        while (pos.y >= limit) {
            val below = pos.down()
            val ground = world.getBlockState(below)
            if (ground.isSideSolidFullSquare(world, below, Direction.UP)) {
                val shape = world.getBlockState(pos).getCollisionShape(world, pos)
                if (!shape.isEmpty) surface = shape.getMax(Direction.Axis.Y)
                found = true
                break
            }
            pos = below
        }
        if (!found) return false
        world.spawnEntity(EvokerFangsEntity(world, x, pos.y + surface, z, yaw, warmup, owner))
        return true
    }

    private fun ravager(player: ServerPlayerEntity, world: ServerWorld, mob: RavagerEntity): Long {
        val center = mob.boundingBox.center
        world.spawnParticles(ParticleTypes.POOF, center.x, center.y, center.z, 24, 0.7, 0.45, 0.7, 0.02)
        mob.playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f)
        val source = mob.damageSources.mobAttack(player)
        for (entity in cone(player, mob, 4.2, 0.15)) {
            entity.damage(world, source, 6.0f)
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
            entity.addVelocity(vx, 0.45, vz)
        }
        return RAVAGER
    }

    private fun goat(player: ServerPlayerEntity, world: ServerWorld, mob: GoatEntity): Long {
        val front = mob.entityPos.add(horizontalLook(player.yaw).multiply(1.6)).add(0.0, mob.height * 0.55, 0.0)
        world.spawnParticles(ParticleTypes.EXPLOSION, front.x, front.y, front.z, 1, 0.1, 0.1, 0.1, 0.0)
        mob.playSound(SoundEvents.ENTITY_GOAT_RAM_IMPACT, 1.0f, 1.0f)
        val amount = (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)?.value ?: 6.0).toFloat().coerceAtLeast(5f)
        val source = mob.damageSources.mobAttack(player)
        for (entity in cone(player, mob, 3.6, 0.4)) {
            if (entity.damage(world, source, amount)) {
                entity.takeKnockback(1.8, mob.x - entity.x, mob.z - entity.z)
                entity.velocityDirty = true
            }
        }
        return GOAT
    }

    private fun bee(player: ServerPlayerEntity, world: ServerWorld, mob: BeeEntity, target: LivingEntity?): Long? {
        if (target == null || mob.squaredDistanceTo(target) > 16.0) return null
        target.damage(world, mob.damageSources.sting(mob), 2.0f)
        target.addStatusEffect(StatusEffectInstance(StatusEffects.POISON, 200, 0), mob)
        mob.playSound(SoundEvents.ENTITY_BEE_STING, 1.0f, 1.0f)
        mob.swingHand(Hand.MAIN_HAND)
        return BEE
    }

    private fun frog(player: ServerPlayerEntity, world: ServerWorld, mob: FrogEntity, target: LivingEntity?): Long? {
        if (target == null || mob.squaredDistanceTo(target) > 64.0) return null
        target.damage(world, mob.damageSources.mobAttack(player), 3.0f)
        val pull = mob.entityPos.add(0.0, mob.height * 0.4, 0.0).subtract(target.entityPos)
        val length = pull.length().coerceAtLeast(0.001)
        target.addVelocity(pull.x / length * 0.7, 0.35, pull.z / length * 0.7)
        mob.playSound(SoundEvents.ENTITY_FROG_TONGUE, 1.0f, 1.0f)
        return FROG
    }

    private fun slam(player: ServerPlayerEntity, world: ServerWorld, mob: SlimeEntity, ignite: Boolean): Long {
        val radius = (mob.size + 0.8).coerceAtLeast(1.6)
        val amount = (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)?.value ?: mob.size.toDouble())
            .toFloat()
            .coerceAtLeast(mob.size.toFloat())
        val source = mob.damageSources.mobAttack(player)
        val box = mob.boundingBox.expand(radius, 0.6, radius)
        for (entity in world.getEntitiesByClass(LivingEntity::class.java, box) { it.isAlive && !it.isSpectator }) {
            if (DragonTamerService.isAlly(player, entity)) continue
            if (entity.squaredDistanceTo(mob) > radius * radius) continue
            entity.damage(world, source, amount)
            if (ignite) entity.setOnFireFor(4.0f)
            entity.takeKnockback(0.45, mob.x - entity.x, mob.z - entity.z)
            entity.velocityDirty = true
        }
        val sound = if (ignite) SoundEvents.ENTITY_MAGMA_CUBE_SQUISH else SoundEvents.ENTITY_SLIME_ATTACK
        mob.playSound(sound, 1.0f, 1.0f)
        world.spawnParticles(
            if (ignite) ParticleTypes.FLAME else ParticleTypes.ITEM_SLIME,
            mob.x, mob.getBodyY(0.5), mob.z,
            8, mob.width * 0.6, 0.2, mob.width * 0.6, 0.02,
        )
        return SLIME
    }

    private fun blink(player: ServerPlayerEntity, mob: EndermanEntity, target: LivingEntity?): Long? {
        val now = player.entityWorld.server?.ticks?.toLong() ?: return null
        val from = mob.entityPos
        val look = player.getRotationVec(1.0f)
        var arrived = false
        if (target != null) {
            val back = player.entityPos.subtract(target.entityPos)
            val flat = Vec3d(back.x, 0.0, back.z)
            val offset = if (flat.lengthSquared() < 1.0e-4) horizontalLook(player.yaw).multiply(-1.5) else flat.normalize().multiply(1.5)
            val dest = target.entityPos.add(offset)
            arrived = mob.teleport(dest.x, dest.y, dest.z, true)
        }
        if (!arrived) {
            val eye = player.eyePos
            val end = eye.add(look.multiply(32.0))
            val hit = player.entityWorld.raycast(
                RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player),
            )
            if (hit.type != HitResult.Type.BLOCK) return null
            val dest = hit.pos.subtract(look.multiply(0.8))
            if (!mob.teleport(dest.x, dest.y, dest.z, true)) return null
        }
        mob.setYaw(player.yaw)
        mob.setHeadYaw(player.yaw)
        mob.setBodyYaw(player.yaw)
        mob.fallDistance = 0.0
        player.fallDistance = 0.0
        mob.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
        DragonTamerService.markReposition(player, from, now + 10L)
        return ENDERMAN
    }

    private fun witch(
        player: ServerPlayerEntity,
        world: ServerWorld,
        mob: WitchEntity,
        target: LivingEntity?,
        aim: Vec3d,
    ): Long? {
        if (mob.isDrinking) return null
        // 原版对着掠夺者会丢治疗。骑着打的时候改成伤害药水, 避免把敌人奶起来。
        if (target != null && target !is RaiderEntity) {
            mob.shootAt(target, 1.0f)
            return WITCH
        }
        val to = if (target != null) target.entityPos.add(0.0, target.height * 0.5, 0.0) else aim
        val stack = PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HARMING)
        val delta = direction(player, to.subtract(mob.eyePos))
        ProjectileEntity.spawnWithVelocity(
            { potionWorld, shooter, potion -> SplashPotionEntity(potionWorld, shooter, potion) },
            world,
            stack,
            mob,
            delta.x,
            delta.y,
            delta.z,
            0.75f,
            1.0f,
        )
        mob.playSound(SoundEvents.ENTITY_WITCH_THROW, 1.0f, 0.9f)
        return WITCH
    }

    private fun shootVanilla(world: ServerWorld, mob: RangedAttackMob, target: LivingEntity?, aim: Vec3d): Long {
        val shot = target ?: ArmorStandEntity(world, aim.x, aim.y, aim.z)
        mob.shootAt(shot, 1.0f)
        if (target == null) shot.discard()
        val shooter = mob as MobEntity
        return when (shooter) {
            is DrownedEntity -> 20L
            is SnowGolemEntity -> 8L
            is LlamaEntity -> 16L
            is AbstractSkeletonEntity -> SKELETON
            is IllusionerEntity -> 16L
            else -> 16L
        }
    }

    /** 弩没装填时 [PillagerEntity.shootAt] 不会出箭, 这里直接生成一支 */
    private fun looseArrow(world: ServerWorld, mob: MobEntity, aim: Vec3d, shotFrom: ItemStack) {
        val stack = ItemStack(Items.ARROW)
        val weapon = if (shotFrom.isOf(Items.CROSSBOW)) shotFrom else ItemStack(Items.CROSSBOW)
        val arrow = ArrowEntity(world, mob, stack, weapon)
        val delta = direction(mob, aim.subtract(mob.eyePos))
        ProjectileEntity.spawnWithVelocity(arrow, world, stack, delta.x, delta.y, delta.z, 1.6f, 1.0f)
        mob.playSound(SoundEvents.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f)
    }

    private fun melee(
        player: ServerPlayerEntity,
        mob: MobEntity,
        target: LivingEntity?,
        reach: Double,
        cooldown: Long,
        sound: SoundEvent?,
    ): Long? {
        if (target == null) return null
        val damage = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)?.value ?: 0.0
        if (damage <= 0.0) return null
        if (mob.squaredDistanceTo(target) > reach * reach) return null
        mob.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.eyePos)
        val hit = mob.tryAttack(player.entityWorld, target)
        // 铁傀儡进了攻击动作就会挥臂, 打空也吃冷却, 免得连点把方块放出去
        if (!hit && mob !is IronGolemEntity) return null
        mob.swingHand(Hand.MAIN_HAND)
        if (hit && sound != null) mob.playSound(sound, 1.0f, 1.0f)
        return cooldown
    }

    private fun reachOf(mob: MobEntity): Double = (mob.width + 3.2).coerceIn(3.2, 6.0)

    private fun cone(player: ServerPlayerEntity, mob: MobEntity, range: Double, minDot: Double): List<LivingEntity> {
        val forward = horizontalLook(player.yaw)
        val box = mob.boundingBox.expand(range, range, range)
        val found = ArrayList<LivingEntity>()
        for (entity in player.entityWorld.getEntitiesByClass(LivingEntity::class.java, box) { it.isAlive && !it.isSpectator }) {
            if (DragonTamerService.isAlly(player, entity)) continue
            val delta = entity.entityPos.subtract(mob.entityPos)
            if (delta.length() > range) continue
            val flat = Vec3d(delta.x, 0.0, delta.z)
            if (flat.lengthSquared() > 0.09 && flat.normalize().dotProduct(forward) < minDot) continue
            found.add(entity)
        }
        return found
    }

    private fun horizontalLook(yaw: Float): Vec3d {
        val rad = Math.toRadians(yaw.toDouble())
        return Vec3d(-sin(rad), 0.0, cos(rad))
    }

    private fun direction(player: ServerPlayerEntity, delta: Vec3d): Vec3d =
        if (delta.lengthSquared() < 1.0e-4) player.getRotationVec(1.0f) else delta.normalize()

    private fun direction(mob: MobEntity, delta: Vec3d): Vec3d =
        if (delta.lengthSquared() < 1.0e-4) mob.rotationVector else delta.normalize()

    private fun perpendicular(dir: Vec3d): Vec3d {
        val side = Vec3d(-dir.z, 0.0, dir.x)
        return if (side.lengthSquared() < 1.0e-4) Vec3d(1.0, 0.0, 0.0) else side.normalize()
    }
}
