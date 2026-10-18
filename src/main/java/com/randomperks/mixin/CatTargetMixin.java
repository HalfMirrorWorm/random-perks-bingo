package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "老吴!?": 猫会主动把**持有该词条的玩家**设为目标。
 *
 * 猫本身已经有攻击 AI (PounceAtTargetGoal + AttackGoal), 这里只往 targetSelector 里补一条目标规则。
 */
@Mixin(CatEntity.class)
public class CatTargetMixin {

    @Shadow @Final protected GoalSelector targetSelector;

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void randomperks$huntOldWu(CallbackInfo ci) {
        this.targetSelector.add(1, new ActiveTargetGoal<>(
            (CatEntity) (Object) this,
            PlayerEntity.class,
            true,
            (entity, world) -> entity instanceof ServerPlayerEntity player
                && PerkBoostQuery.INSTANCE.hasOldWu(player)
        ));
    }
}
