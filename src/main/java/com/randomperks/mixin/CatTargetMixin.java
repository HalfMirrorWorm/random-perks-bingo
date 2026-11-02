package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "老吴!?": 猫会主动把**持有该词条的玩家**设为目标。
 *
 * 猫本身已经有攻击 AI (PounceAtTargetGoal + AttackGoal), 这里只往 targetSelector 里补一条目标规则。
 *
 * 注: targetSelector 声明在 MobEntity 上 (CatEntity 只是继承), 所以走
 * {@link MobTargetSelectorAccessor} 而不是 @Shadow —— 没有 refmap 时 @Shadow 在目标类
 * 自身找不到继承字段, 会在 APPLY 阶段报 "field_6185 was not located"。
 */
@Mixin(Cat.class)
public class CatTargetMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void randomperks$huntOldWu(CallbackInfo ci) {
        Cat self = (Cat) (Object) this;

        ((MobTargetSelectorAccessor) this).randomperks$getTargetSelector().addGoal(1, new NearestAttackableTargetGoal<>(
            self,
            Player.class,
            true,
            // 只有**未驯服**的猫才会袭击持词条的玩家 —— 家养的猫不会被词条策反
            (entity, world) -> !self.isTame()
                && entity instanceof ServerPlayer player
                && PerkBoostQuery.INSTANCE.hasYowl(player)
        ));
    }
}
