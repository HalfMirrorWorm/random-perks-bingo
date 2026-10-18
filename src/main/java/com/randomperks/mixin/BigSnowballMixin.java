package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import com.randomperks.network.BigSnowballS2C;
import com.randomperks.network.PerkNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "雪球大作战" (蓝 · 猎杀) —— 持有该词条的玩家扔出的雪球会造成伤害并击飞。
 *
 * <ul>
 *   <li>生成时打一个 commandTag 作为服务端标记, 并广播给客户端 (tag 本身不同步, 见下)</li>
 *   <li>命中生物时补 3 点伤害 + 明显击退</li>
 * </ul>
 *
 * 注: {@code Entity.commandTags} 是个普通字段、不在 TrackedData 里, **不会同步给客户端**,
 * 所以"变大"这件事只能靠单独的包通知 (BigSnowballS2C)。
 */
@Mixin(SnowballEntity.class)
public class BigSnowballMixin {

    /** 服务端标记 (客户端看不到) */
    private static final String TAG = "randomperks_big_snowball";

    /** 命中伤害 */
    private static final float DAMAGE = 3.0f;

    /** 水平击退强度 */
    private static final double KNOCKBACK = 1.5;

    /** 额外上抛速度 ("击飞"的垂直分量) */
    private static final double LAUNCH = 0.35;

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("TAIL"))
    private void randomperks$markBigSnowball(World world, LivingEntity owner, ItemStack stack, CallbackInfo ci) {
        if (world.isClient()) return;
        if (!(owner instanceof ServerPlayerEntity player)) return;
        if (!PerkBoostQuery.INSTANCE.hasBigSnowball(player)) return;

        SnowballEntity self = (SnowballEntity) (Object) this;
        self.addCommandTag(TAG);

        if (!(world instanceof ServerWorld serverWorld)) return;
        BigSnowballS2C payload = new BigSnowballS2C(self.getId());
        for (ServerPlayerEntity viewer : serverWorld.getServer().getPlayerManager().getPlayerList()) {
            PerkNetworking.INSTANCE.sendBigSnowball(viewer, payload);
        }
    }

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void randomperks$bigSnowballHit(EntityHitResult result, CallbackInfo ci) {
        SnowballEntity self = (SnowballEntity) (Object) this;
        if (!self.getCommandTags().contains(TAG)) return;

        Entity hit = result.getEntity();
        if (!(hit instanceof LivingEntity living)) return;

        // 只在服务端结算伤害 (onEntityHit 在两端都会跑, 客户端那次只负责粒子/音效)
        if (!(self.getWorld() instanceof ServerWorld serverWorld)) return;

        // 原版对烈焰人本来就打 3 点, 这里只补差额, 免得叠加成 6 点
        float amount = hit instanceof BlazeEntity ? 0.0f : DAMAGE;
        if (amount > 0.0f) {
            living.damage(serverWorld, self.getDamageSources().thrown(self, self.getOwner()), amount);
        }

        // 击飞: 水平推开 + 一点上抛
        living.takeKnockback(KNOCKBACK, self.getX() - living.getX(), self.getZ() - living.getZ());
        living.addVelocity(0.0, LAUNCH, 0.0);
        living.velocityModified = true;
    }
}
