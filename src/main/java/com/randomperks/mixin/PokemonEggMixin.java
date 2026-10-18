package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "宝可梦" (金 · 丰饶) —— 持词条者扔出的鸡蛋砸中非 Boss 生物时, 把这只生物"收服":
 * 生物消失, 原地掉落它对应的刷怪蛋, 并返还 2 个鸡蛋。
 *
 * <p>几个边界:
 * <ul>
 *   <li>玩家不会被收服 (否则就是 PvP 秒杀技能了);</li>
 *   <li>三大 Boss (末影龙/凋灵/监守者) 排除; 其余"没有刷怪蛋"的生物由
 *       {@link SpawnEggItem#forEntity} 返回 null 自然挡掉;</li>
 *   <li>用 {@code discard()} 而不是 {@code kill()}, 所以不会走原版掉落表。</li>
 * </ul>
 */
@Mixin(EggEntity.class)
public class PokemonEggMixin {

    /** 成功收服后返还的鸡蛋数 */
    private static final int EGG_REFUND = 2;

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void randomperks$capture(EntityHitResult result, CallbackInfo ci) {
        EggEntity self = (EggEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) return;

        Entity owner = self.getOwner();
        if (!(owner instanceof ServerPlayerEntity player)) return;
        if (!PerkBoostQuery.INSTANCE.hasPokemon(player)) return;

        Entity target = result.getEntity();
        if (!(target instanceof LivingEntity living)) return;
        if (living instanceof PlayerEntity) return;
        if (isBoss(living)) return;

        // 没有刷怪蛋的生物 (末影龙之外还有不少) 无法收服
        SpawnEggItem egg = SpawnEggItem.forEntity(living.getType());
        if (egg == null) return;

        // 先掉蛋再移除 (dropStack 需要实体还活着才能拿到位置)
        living.dropStack(world, new ItemStack(egg));
        living.discard();

        player.getInventory().offerOrDrop(new ItemStack(Items.EGG, EGG_REFUND));
    }

    /** 原版没有 BOSSES 标签, 手动列三大 Boss */
    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragonEntity
                || entity instanceof WitherEntity
                || entity instanceof WardenEntity;
    }
}
