package com.randomperks.mixin;

import com.randomperks.game.FireballService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "烈焰喷射"打出的火焰弹**不可被攻击反弹**。
 *
 * <p>原版玩家近战打可反弹弹射物时会调用 {@code deflect(..., fromAttack = true)}。
 * 带 {@link FireballService#FIREBALL_TAG} 命令标签的火球直接返回 false:
 * 攻击不会改变它的飞行方向, 火球继续朝原方向飞。
 * {@code double} 那个重载会转到这个 {@code Vec3} 重载, 所以只拦这一处。
 *
 * <p>只拦 {@code fromAttack = true} 的情况 (即玩家近战打弹射物) ——
 * 其他来源的偏转 (例如世界边界) 不受影响, 也不会出现"穿过实体不爆炸"的副作用。
 */
@Mixin(Projectile.class)
public class FireballNoDeflectMixin {

    @Inject(
            method = "deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityReference;ZLnet/minecraft/world/phys/Vec3;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void randomperks$noDeflect(ProjectileDeflection deflection, Entity deflector, EntityReference<Entity> owner,
                                       boolean fromAttack, Vec3 movement, CallbackInfoReturnable<Boolean> cir) {
        if (!fromAttack) return;

        Projectile self = (Projectile) (Object) this;
        if (!self.entityTags().contains(FireballService.FIREBALL_TAG)) return;

        cir.setReturnValue(false);
    }
}
