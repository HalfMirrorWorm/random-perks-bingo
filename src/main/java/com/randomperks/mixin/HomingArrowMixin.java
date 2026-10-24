package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * "这不是挂?" —— 持有该词条的玩家射出的**箭**会自动追踪附近的目标。
 *
 * 只处理 {@link ArrowEntity} (弓/弩射出的箭), 不碰三叉戟、雪球等其他弹射物。
 *
 * <p>索敌只在**箭生成后的第一 tick** 做一次: 以射手当时的准心方向为准, 在周围
 * [ACQUIRE_RANGE] 格内选**与准心夹角最小**的生物 (不做视线遮挡判断, 可以隔墙锁定)。
 * 锁定后整段飞行都不换目标, 不会因为途中出现更近的生物而改追别人。
 *
 * <p>★ 追踪期间临时开启 {@code noClip}: 原版箭只要路径上碰到方块就会立即
 * {@code setInGround} 并停下, 哪怕目标就在 2 格外的墙角后面 —— 表现出来就是
 * "追踪箭老被旁边的方块挡住"。开启后 {@code Entity.move} 直接位移、不产生方块碰撞,
 * 箭可以穿墙追到锁定目标。
 *
 * <p>★ 但 {@code noClip} 的箭在原版里**不参与实体碰撞**: {@code tick()} 的
 * {@code bl = !isNoClip()} 为 false 时走的是 {@code setPosition} 分支, 根本不会调用
 * {@code applyCollision}; 即使调用了, {@code applyCollision} 里也有
 * {@code !this.noClip} 的检查。所以命中必须由本 mixin 自己做 —— 沿本 tick 的位移线段
 * 对锁定目标做一次射线检测, 命中后手动调用原版的 {@code hitOrDeflect}。
 * 以前"把箭挪到目标身上"的写法只会每 tick 把箭拽回目标中心再飞出去 (看起来像绕圈),
 * 不会造成任何伤害。
 *
 * 目标丢失 (死亡/被移除/词条被清掉) 时恢复 {@code noClip = false}, 让箭按原版行为落地。
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class HomingArrowMixin {

    /** 发射瞬间索敌的最大半径 (格) —— 只看准心角度, 穿墙 */
    private static final double ACQUIRE_RANGE = 64.0;

    /** 索敌的最小视线夹角余弦: 约 30° 锥角, 准心附近没有生物就不锁定 */
    private static final double MIN_AIM_DOT = Math.cos(Math.toRadians(30.0));

    /**
     * 每 tick 向目标偏转的比例 (0 = 不转, 1 = 立刻对准)。
     *
     * ★ 必须满转向: 箭速约 3 格/tick, 0.35 的平滑偏转会让箭以约 8 格为半径绕圈,
     *   目标一旦在半径内就永远追不上 (表现为"箭一直转但射不到人")。
     */
    private static final double TURN_RATE = 1.0;

    /** 命中检测时目标碰撞箱的额外容差 (格) */
    private static final double HIT_BOX_MARGIN = 0.3;

    /** 速度低于此值视为已落地/停滞, 不再追踪 */
    private static final double MIN_SPEED = 0.05;

    /**
     * 被本 mixin 临时开启过 noClip 的箭。
     *
     * 用弱引用集合, 箭被移除后自动回收; tick 只在服务端线程跑, 不需要同步。
     */
    private static final Set<PersistentProjectileEntity> HOMING_ARROWS =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 每支箭在**发射瞬间**锁定的目标。之后不再重新索敌 ——
     * 否则飞行途中最近的目标变了, 箭会突然改追别人。
     */
    private static final Map<PersistentProjectileEntity, LivingEntity> LOCKED_TARGETS =
            new WeakHashMap<>();

    /** 已经做过索敌尝试的箭; 第一 tick 没锁到目标也不再反复扫世界 */
    private static final Set<PersistentProjectileEntity> ACQUIRE_ATTEMPTED =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Inject(method = "tick", at = @At("HEAD"))
    private void randomperks$homeToTarget(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self instanceof ArrowEntity)) return;
        if (!(self.getWorld() instanceof ServerWorld world)) return;

        Entity owner = self.getOwner();
        if (!(owner instanceof ServerPlayerEntity shooter)
                || !PerkBoostQuery.INSTANCE.hasHomingBow(shooter)) {
            // 词条被清掉 / 箭的主人不是玩家: 恢复碰撞, 按原版行为落地
            stopTracking(self);
            return;
        }

        Vec3d velocity = self.getVelocity();
        double speed = velocity.length();
        if (speed < MIN_SPEED) {
            // 已命中/落地/停滞: 恢复原版碰撞, 不要让它以后被推动时继续穿墙
            stopTracking(self);
            return;
        }

        // 只在箭生成后的第一 tick 索敌: 用射手当时的准心方向, 锁定后全程不再换目标。
        // 索敌只看角度、不做视线遮挡判断 —— 隔着墙也能锁定准心对着的生物。
        if (!ACQUIRE_ATTEMPTED.contains(self)) {
            ACQUIRE_ATTEMPTED.add(self);
            LivingEntity acquired = acquireTarget(world, shooter);
            if (acquired != null) LOCKED_TARGETS.put(self, acquired);
        }

        LivingEntity target = LOCKED_TARGETS.get(self);
        if (target == null || !target.isAlive() || target.isRemoved() || target.getWorld() != world) {
            stopTracking(self);
            return;
        }

        // 追踪期间穿墙, 避免在墙角/门框/洞穴里被近处方块挡下
        if (HOMING_ARROWS.add(self)) self.setNoClip(true);

        // 所有目标统一满转向 + 贴近直接判中 (平滑偏转会导致绕圈, 见 TURN_RATE 注释)
        double turnRate = TURN_RATE;

        Vec3d aim = target.getPos()
                .add(0.0, target.getHeight() * 0.5, 0.0)
                .subtract(self.getPos());
        double aimLength = aim.length();
        if (aimLength < 1.0E-4) return;

        Vec3d blended = velocity.normalize().multiply(1.0 - turnRate)
                .add(aim.multiply(turnRate / aimLength));
        double blendedLength = blended.length();
        if (blendedLength < 1.0E-4) return;

        self.setVelocity(blended.multiply(speed / blendedLength));

        // ★ 必须手动置 velocityDirty —— Entity.setVelocity(Vec3d) 只赋值, 不会把这个标志置位,
        //   而服务端正是靠它决定要不要给客户端发 EntityVelocityUpdateS2CPacket
        //   (EntityTrackerEntry: `if (entity.velocityDirty || ...) sendVelocity`)。
        //   漏掉这一句的话: 服务端的箭在拐弯, 客户端却一直按初始速度渲染, 只能靠偶发的位置
        //   修正往回拉 —— 看起来就是"追踪几乎不起作用"。原版所有改速度的地方
        //   (弹反 / 爆炸 / 命中回弹) 都会手动置位。
        self.velocityDirty = true;

        // ★ 命中结算 (noClip 的箭原版不做实体碰撞, 原因见类注释):
        //   沿本 tick 的位移线段 [当前位置, 当前位置 + 速度] 对锁定目标做射线检测。
        Vec3d from = self.getPos();
        Vec3d to = from.add(self.getVelocity());
        Box hitBox = target.getBoundingBox().expand(HIT_BOX_MARGIN);
        Optional<Vec3d> hitPos = hitBox.raycast(from, to);
        if (hitPos.isPresent() || hitBox.contains(from)) {
            // hitOrDeflect 是 ProjectileEntity 上的 protected 方法, 通过 @Invoker 调用
            // (箭实体一定实现了这个接口)
            ((ProjectileHitInvoker) self).randomperks$invokeHitOrDeflect(
                    new EntityHitResult(target, hitPos.orElse(from))
            );
            // 命中或弹反后都不再追踪 —— 否则下一 tick 又会把箭拽回目标,
            // 被盾弹反的箭会反复撞盾。
            stopTracking(self);
        }
    }

    /**
     * 把同步过来的"穿墙"标记写进客户端实体的 {@code noClip} 字段。
     *
     * {@code PersistentProjectileEntity.isNoClip()} 在客户端读的是数据追踪器里的
     * PROJECTILE_FLAGS 位, 但 {@code Entity.move()} 只看 {@code Entity.noClip} 字段 ——
     * 不同步的话服务端的箭在穿墙, 客户端却仍会撞在方块上, 看起来一顿一顿的。
     */
    @Inject(method = "onTrackedDataSet", at = @At("TAIL"))
    private void randomperks$syncClientNoClip(TrackedData<?> data, CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!self.getWorld().isClient) return;
        ((Entity) (Object) self).noClip = self.isNoClip();
    }

    /** 停止追踪并恢复原版碰撞 */
    private static void stopTracking(PersistentProjectileEntity arrow) {
        if (HOMING_ARROWS.remove(arrow)) arrow.setNoClip(false);
        LOCKED_TARGETS.remove(arrow);
    }

    /**
     * 发射瞬间索敌: 在射手周围 [ACQUIRE_RANGE] 格内, 选**与准心夹角最小**的生物。
     *
     * 不做视线遮挡判断 —— 即使目标在墙后/洞里, 只要准心对着它就会被锁定。
     * 射手自己、队友、自己驯服的生物除外; 距离不参与排序。
     */
    private static LivingEntity acquireTarget(ServerWorld world, ServerPlayerEntity shooter) {
        Vec3d eye = shooter.getEyePos();
        Vec3d look = shooter.getRotationVec(1.0f);

        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                shooter.getBoundingBox().expand(ACQUIRE_RANGE),
                entity -> entity != shooter && entity.isAlive() && !entity.isRemoved()
                        && !entity.isSpectator() && !isAlly(shooter, entity));

        LivingEntity best = null;
        double bestDot = MIN_AIM_DOT;
        for (LivingEntity candidate : candidates) {
            Vec3d aim = candidate.getPos()
                    .add(0.0, candidate.getHeight() * 0.5, 0.0)
                    .subtract(eye);
            double length = aim.length();
            if (length < 1.0E-4) continue;

            double dot = aim.dotProduct(look) / length;
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    /** 是否属于"自己人" —— 同队玩家, 或射手自己驯服的生物 */
    private static boolean isAlly(ServerPlayerEntity shooter, LivingEntity entity) {
        if (entity instanceof PlayerEntity other) {
            String own = shooter.getScoreboardTeam() == null ? null : shooter.getScoreboardTeam().getName();
            String theirs = other.getScoreboardTeam() == null ? null : other.getScoreboardTeam().getName();
            return own != null && own.equals(theirs);
        }
        if (entity instanceof TameableEntity tameable) {
            LivingEntity owner = tameable.getOwner();
            return owner != null && owner.getUuid().equals(shooter.getUuid());
        }
        return false;
    }
}
