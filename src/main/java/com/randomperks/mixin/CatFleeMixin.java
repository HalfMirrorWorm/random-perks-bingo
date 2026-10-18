package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "老吴!?": 猫**不再害怕**持有该词条的玩家 —— 附近有这类玩家时, 一只猫的逃跑 goal 不会启动。
 *
 * (猫的 CatFleeGoal 是包级私有内部类, 这里用 Mixin targets 字符串定位, 不直接引用它)
 */
@Mixin(targets = "net.minecraft.entity.passive.CatEntity$CatFleeGoal")
public class CatFleeMixin {

    @Shadow @Final private CatEntity cat;

    @Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
    private void randomperks$notAfraid(CallbackInfoReturnable<Boolean> cir) {
        World world = this.cat.getWorld();
        if (world.isClient()) return;

        for (PlayerEntity player : world.getPlayers()) {
            if (player instanceof ServerPlayerEntity serverPlayer
                && PerkBoostQuery.INSTANCE.hasOldWu(serverPlayer)
                && serverPlayer.squaredDistanceTo(this.cat) < 32.0 * 32.0) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
