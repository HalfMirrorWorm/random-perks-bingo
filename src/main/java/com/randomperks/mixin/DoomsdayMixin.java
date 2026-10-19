package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "天启日": 被持有该词条的玩家击杀时, **强制死亡掉落** —— 无视 keepInventory 游戏规则。
 * 触发次数有上限 (前 4 次击杀); 持词条者**自己死亡**时同样会强制掉落。
 *
 * <p><b>为什么注入 onDeath 而不是 PlayerEntity.dropInventory</b>: 原版
 * {@code LivingEntity.damage()} 的执行顺序是
 *
 * <pre>
 *   if (this.isDead()) {
 *       this.onDeath(source);          // ← drop() → dropInventory() 在这里被调用
 *   }
 *   ...
 *   this.lastDamageSource = source;    // ← 伤害源在 onDeath **之后**才写入
 *   this.lastDamageTime   = this.getWorld().getTime();
 * </pre>
 *
 * 也就是说在 dropInventory 里读 {@code getRecentDamageSource()} 拿到的是**上一次**伤害的
 * 源 (若距今超过 40 tick 还会被置成 null), 而不是这次致命一击 —— 致命一击的源只存在于
 * onDeath 的入参里。(另一个会写 lastDamageSource 的 {@code onDamaged()} 在原版里根本没有
 * 调用者, 是个给 mod 用的空钩子。)
 *
 * <p>这里手动执行原版 keepInventory=false 时的两件事 (消失诅咒物品清除 + 背包全部丢出)。
 * 之后原版的 {@code drop()} 仍会照常走到 dropInventory, 但 keepInventory 开着时它什么都不做,
 * 所以不会重复掉落。
 */
@Mixin(ServerPlayerEntity.class)
public class DoomsdayMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void randomperks$forceDrop(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;

        // 与原版 onDeath 的判定保持一致 (旁观者不产生掉落)
        if (self.isSpectator()) return;
        // keepInventory 关闭时原版本来就会掉落, 不必干预
        if (!self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;

        if (PerkBoostQuery.INSTANCE.hasDoomsday(self)) {
            // 持词条者自己死亡 → 一律强制掉落 (不分死因)
        } else {
            // 被持词条者击杀 → 只有前 4 次生效
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            if (!PerkBoostQuery.INSTANCE.hasDoomsday(killer)) return;
            if (!PerkBoostQuery.INSTANCE.consumeDoomsdayKill(killer)) return;
        }

        ((PlayerEntityInvoker) self).randomperks$vanishCursedItems();
        self.getInventory().dropAll();
    }
}
