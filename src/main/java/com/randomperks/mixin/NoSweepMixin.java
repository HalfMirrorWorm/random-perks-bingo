package com.randomperks.mixin;

import com.randomperks.game.ClientPerkFlags;
import com.randomperks.game.PerkBoostQuery;
import com.randomperks.game.PerkDownsideSuppression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "老玩家" / "珠光护手": 剑不再横扫。
 *
 * <p>以前是靠把 {@code SWEEPING_DAMAGE_RATIO} 改成 -1 实现的, 但这个属性只影响横扫**伤害**
 * (`1 + 比例 × 攻击力`), 而且会被夹到 0 —— 横扫判定本身不看它,
 * 所以照样会触发横扫音效, 周围生物还会吃到 1 点伤害。
 *
 * <p>这里直接让 {@code isSweepAttack} 对持词条者返回 false, 从判定源头关掉。
 */
@Mixin(Player.class)
public class NoSweepMixin {

    private static final String VETERAN = "hunter_veteran";
    private static final String PEARL_GAUNTLET = "hunter_pearl_gauntlet";

    @Inject(method = "isSweepAttack", at = @At("HEAD"), cancellable = true)
    private void randomperks$noSweep(boolean charged, boolean crit, boolean knockbackPushed,
                                     CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        boolean hasPerk;
        if (self.level().isClientSide()) {
            boolean veteran = ClientPerkFlags.has(VETERAN)
                && !ClientPerkFlags.isDownsideSuppressed(VETERAN);
            boolean pearl = ClientPerkFlags.has(PEARL_GAUNTLET)
                && !ClientPerkFlags.isDownsideSuppressed(PEARL_GAUNTLET);
            hasPerk = veteran || pearl;
        } else if (self instanceof ServerPlayer player) {
            boolean veteran = PerkBoostQuery.INSTANCE.hasOldSchoolCombat(player)
                && !PerkDownsideSuppression.INSTANCE.isSuppressed(player, VETERAN);
            boolean pearl = PerkBoostQuery.INSTANCE.hasPearlGauntlet(player)
                && !PerkDownsideSuppression.INSTANCE.isSuppressed(player, PEARL_GAUNTLET);
            hasPerk = veteran || pearl;
        } else {
            hasPerk = false;
        }
        if (hasPerk) {
            cir.setReturnValue(false);
        }
    }
}
