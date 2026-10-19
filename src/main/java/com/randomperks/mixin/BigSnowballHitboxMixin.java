package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "打雪仗": 大雪球画大了, 命中判定也要跟着变大。
 *
 * <p>原版投掷物的命中是一条**射线** (从当前位置沿速度方向到下一 tick 的位置), 雪球自己的
 * 碰撞箱并不参与 —— 所以单纯把渲染放大 2.5 倍, 判定还是原来那么细, 目标必须正好挡在
 * 雪球中心飞过的直线上。
 *
 * <p>{@code ProjectileUtil.getCollision} 会把每个候选实体的碰撞箱按
 * {@code getToleranceMargin(projectile)} 向外扩一圈再和射线求交。把大雪球的 margin
 * 补上"多出来的半径", 判定范围就和外观对上了:
 *
 * <pre>
 *   原版雪球边长 0.25 → 半径 0.125
 *   放大 2.5 倍后半径 0.3125
 *   差 0.1875 格
 * </pre>
 *
 * <p>只对打了 {@code randomperks_big_snowball} tag 的雪球生效, 其他投掷物不受影响。
 * 注意 tag 不同步给客户端, 所以这条只在服务端实际生效 —— 命中本来也是服务端结算的。
 */
@Mixin(ProjectileUtil.class)
public class BigSnowballHitboxMixin {

    /** 原版雪球边长 (EntityType.SNOWBALL 的尺寸) */
    private static final float SNOWBALL_SIZE = 0.25f;

    /** 渲染放大倍数 (与 BigSnowballRendererMixin.SCALE 保持一致) */
    private static final float RENDER_SCALE = 2.5f;

    /** 判定半径应该多出来的部分 */
    private static final float EXTRA_MARGIN = (RENDER_SCALE - 1.0f) * SNOWBALL_SIZE / 2.0f;

    @Inject(method = "getToleranceMargin", at = @At("RETURN"), cancellable = true)
    private static void randomperks$bigSnowballMargin(Entity entity, CallbackInfoReturnable<Float> cir) {
        if (!PerkBoostQuery.INSTANCE.isBigSnowball(entity)) return;
        cir.setReturnValue(cir.getReturnValue() + EXTRA_MARGIN);
    }
}
