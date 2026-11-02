package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import com.randomperks.network.BigSnowballS2C;
import com.randomperks.network.PerkNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
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
@Mixin(Snowball.class)
public class BigSnowballMixin {

    /** 服务端标记 (客户端看不到) */
    private static final String TAG = PerkBoostQuery.BIG_SNOWBALL_TAG;

    /** 命中伤害 */
    private static final float DAMAGE = 3.0f;

    /** 水平击退强度 */
    private static final double KNOCKBACK = 1.5;

    /** 额外上抛速度 ("击飞"的垂直分量) */
    private static final double LAUNCH = 0.35;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void randomperks$markBigSnowball(Level world, LivingEntity owner, ItemStack stack, CallbackInfo ci) {
        if (world.isClientSide()) return;
        if (!(owner instanceof ServerPlayer player)) return;
        if (!PerkBoostQuery.INSTANCE.hasBigSnowball(player)) return;

        Snowball self = (Snowball) (Object) this;
        self.addTag(TAG);

        if (!(world instanceof ServerLevel serverWorld)) return;
        BigSnowballS2C payload = new BigSnowballS2C(self.getId());
        for (ServerPlayer viewer : serverWorld.getServer().getPlayerList().getPlayers()) {
            PerkNetworking.INSTANCE.sendBigSnowball(viewer, payload);
        }
    }

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void randomperks$bigSnowballHit(EntityHitResult result, CallbackInfo ci) {
        Snowball self = (Snowball) (Object) this;
        if (!self.entityTags().contains(TAG)) return;

        Entity hit = result.getEntity();
        if (!(hit instanceof LivingEntity living)) return;

        // 只在服务端结算伤害 (onEntityHit 在两端都会跑, 客户端那次只负责粒子/音效)
        if (!(self.level() instanceof ServerLevel serverWorld)) return;

        // 原版对烈焰人本来就打 3 点, 这里只补差额, 免得叠加成 6 点
        float amount = hit instanceof Blaze ? 0.0f : DAMAGE;
        if (amount > 0.0f) {
            living.hurtServer(serverWorld, self.damageSources().thrown(self, self.getOwner()), amount);
        }

        // 击飞: 水平推开 + 一点上抛
        living.knockback(KNOCKBACK, self.getX() - living.getX(), self.getZ() - living.getZ(), living.damageSources().generic(), 0.0f);
        living.push(0.0, LAUNCH, 0.0);
        living.hurtMarked = true;
    }
}
