package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "宝可梦" (金 · 丰饶) —— 持词条者扔出的鸡蛋砸中非 Boss 生物时, 把这只生物"收服":
 * 生物消失, 原地掉落它对应的刷怪蛋。
 *
 * <p>几个边界:
 * <ul>
 *   <li>玩家不会被收服 (否则就是 PvP 秒杀技能了);</li>
 *   <li>三大 Boss (末影龙/凋灵/监守者) 排除; 其余"没有刷怪蛋"的生物由
 *       {@link SpawnEggItem#byId} 返回 null 自然挡掉;</li>
 *   <li>用 {@code discard()} 而不是 {@code kill()}, 所以不会走原版掉落表。</li>
 * </ul>
 */
@Mixin(ThrownEgg.class)
public class PokemonEggMixin {

    @Inject(method = "onHitEntity", at = @At("TAIL"))
    private void randomperks$capture(EntityHitResult result, CallbackInfo ci) {
        ThrownEgg self = (ThrownEgg) (Object) this;
        if (!(self.level() instanceof ServerLevel world)) return;

        Entity owner = self.getOwner();
        if (!(owner instanceof ServerPlayer player)) return;
        if (!PerkBoostQuery.INSTANCE.hasPokemon(player)) return;

        Entity target = result.getEntity();
        if (!(target instanceof LivingEntity living)) return;
        if (living instanceof Player) return;
        if (isBoss(living)) return;

        // 没有刷怪蛋的生物 (末影龙之外还有不少) 无法收服
        var egg = SpawnEggItem.byId(living.getType());
        if (egg.isEmpty()) return;

        // 先掉蛋再移除 (dropStack 需要实体还活着才能拿到位置)
        living.spawnAtLocation(world, new ItemStack(egg.get().value()));
        living.discard();
    }

    /** 原版没有 BOSSES 标签, 手动列三大 Boss */
    private static boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Warden;
    }
}
