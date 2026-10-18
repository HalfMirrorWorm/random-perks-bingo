package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "天启日": 被持有该词条的玩家击杀时, **强制死亡掉落** —— 无视 keepInventory 游戏规则。
 *
 * 原版 PlayerEntity.dropInventory 在 keepInventory 开启时什么都不做, 这里取消它并手动执行
 * 完整的掉落流程 (消失诅咒物品照常消失 + 背包全部丢出), 与正常死亡掉落一致。
 *
 * 注意: keepInventory 关闭时原版本就会掉落, 所以只在规则开启时干预 (避免重复掉落)。
 */
@Mixin(PlayerEntity.class)
public abstract class DoomsdayMixin {

    @Shadow protected abstract void vanishCursedItems();

    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void randomperks$forceDrop(ServerWorld world, CallbackInfo ci) {
        if (!world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        DamageSource source = self.getRecentDamageSource();
        if (source == null) return;
        if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
        if (!PerkBoostQuery.INSTANCE.hasDoomsday(killer)) return;

        ci.cancel();
        this.vanishCursedItems();
        self.getInventory().dropAll();
    }
}
