package com.randomperks.mixin;

import com.randomperks.game.FireballService;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "烈焰喷射"打出的火焰弹**不可被攻击反弹**。
 *
 * <p>原版 {@code PlayerEntity.attack} 对 {@code minecraft:redirectable_projectile} 标签的
 * 弹射物 (火球在里面) 会调用 {@code deflect(REDIRECTED, attacker, attacker, true)}
 * 把它打回去。这里对带 {@link FireballService#FIREBALL_TAG} 命令标签的火球直接返回 false:
 * 攻击不会改变它的飞行方向, 火球继续朝原方向飞。
 *
 * <p>只拦 {@code fromAttack = true} 的情况 (即玩家近战打弹射物) ——
 * 其他来源的偏转 (例如世界边界) 不受影响, 也不会出现"穿过实体不爆炸"的副作用。
 */
@Mixin(ProjectileEntity.class)
public class FireballNoDeflectMixin {

    @Inject(method = "deflect", at = @At("HEAD"), cancellable = true)
    private void randomperks$noDeflect(ProjectileDeflection deflection, Entity deflector, Entity owner,
                                       boolean fromAttack, CallbackInfoReturnable<Boolean> cir) {
        if (!fromAttack) return;

        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.getCommandTags().contains(FireballService.FIREBALL_TAG)) return;

        cir.setReturnValue(false);
    }
}
