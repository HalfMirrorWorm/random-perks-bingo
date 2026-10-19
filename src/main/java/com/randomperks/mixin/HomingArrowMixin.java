package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * "这不是挂?" —— 持有该词条的玩家射出的**箭**会自动追踪附近的目标。
 *
 * 只处理 {@link ArrowEntity} (弓/弩射出的箭), 不碰三叉戟、雪球等其他弹射物。
 * 转向在每 tick 的 HEAD 完成: 先按当前速度与"指向目标"两个方向的加权平均求出新方向,
 * 再按原速度重新赋予 —— 于是箭会平滑拐弯而不是瞬间掉头。
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class HomingArrowMixin {

    /** 追踪半径 (格) */
    private static final double HOMING_RANGE = 16.0;

    /** 每 tick 向目标偏转的比例 (0 = 不转, 1 = 立刻对准) */
    private static final double TURN_RATE = 0.35;

    /** 玩家目标用满转向 —— 每 tick 直接对准, 不再慢慢拐弯 */
    private static final double PLAYER_TURN_RATE = 1.0;

    /** 玩家目标进入这个距离 (格) 就直接结算为命中 */
    private static final double FORCE_HIT_RANGE = 3.0;

    /** 速度低于此值视为已落地/停滞, 不再追踪 */
    private static final double MIN_SPEED = 0.05;

    @Inject(method = "tick", at = @At("HEAD"))
    private void randomperks$homeToTarget(CallbackInfo ci) {
        PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
        if (!(self instanceof ArrowEntity)) return;
        if (!(self.getWorld() instanceof ServerWorld world)) return;

        Entity owner = self.getOwner();
        if (!(owner instanceof ServerPlayerEntity shooter)) return;
        if (!PerkBoostQuery.INSTANCE.hasHomingBow(shooter)) return;

        Vec3d velocity = self.getVelocity();
        double speed = velocity.length();
        if (speed < MIN_SPEED) return;

        LivingEntity target = findTarget(world, self, shooter);
        if (target == null) return;

        // 玩家目标: 满转向 + 贴近直接判中; 其他目标保持原来的平滑偏转
        boolean hardLock = target instanceof ServerPlayerEntity;
        double turnRate = hardLock ? PLAYER_TURN_RATE : TURN_RATE;

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

        // 直接结算: 把箭挪到目标身体中心。本 tick 原版的碰撞检测用的是
        // getPos() → getPos() + velocity 这条射线, 起点落在目标碰撞箱里就必定判中,
        // 伤害/药水效果/丢弃箭实体全走正常的 onEntityHit 流程。
        if (hardLock && self.squaredDistanceTo(target) <= FORCE_HIT_RANGE * FORCE_HIT_RANGE) {
            self.setPosition(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
        }
    }

    /** 最近的敌对目标 (射手自己、队友、射手驯服的生物除外); 找不到返回 null */
    private static LivingEntity findTarget(
            ServerWorld world,
            PersistentProjectileEntity arrow,
            ServerPlayerEntity shooter) {
        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                arrow.getBoundingBox().expand(HOMING_RANGE),
                entity -> entity != shooter && entity.isAlive() && !isAlly(shooter, entity));

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.squaredDistanceTo(arrow);
            if (distance < bestDistance) {
                bestDistance = distance;
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
