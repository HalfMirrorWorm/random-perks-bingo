package com.randomperks.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * "老玩家" 剑格挡的移动减速惩罚 —— 1.8 里举剑格挡时移动输入只剩 20%。
 *
 * 拦的是 {@link LivingEntity#travel(Vec3d)} 的入参 (sideways, upward, forward)。
 * **只乘水平分量 x/z, 垂直分量 y 原样保留** —— 1.8 的写法是
 * {@code moveStrafe *= 0.2; moveForward *= 0.2}, 跳跃和下落不受影响; 整个向量一起乘的话
 * 连跳起来的高度都会变低。
 *
 * 不额外查词条: 在原版里剑没有 use 行为, 能进入"使用中 + 手里是剑"这个状态本身就是
 * OldSchoolBlockMixin 给持有词条的玩家开的口子。
 *
 * travel 只在 LivingEntity 上定义 (PlayerEntity 没有 override), 所以注入基类就够,
 * 客户端和服务端都会走到, 预测与权威一致。
 */
@Mixin(LivingEntity.class)
public class OldSchoolBlockSlowMixin {

    /** 1.8 的格挡减速系数 */
    private static final double BLOCK_SLOW_FACTOR = 0.2;

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d randomperks$slowWhileBlocking(Vec3d movementInput) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return movementInput;
        if (!player.isUsingItem()) return movementInput;
        if (!player.getActiveItem().isIn(ItemTags.SWORDS)) return movementInput;

        return new Vec3d(
                movementInput.x * BLOCK_SLOW_FACTOR,
                movementInput.y,
                movementInput.z * BLOCK_SLOW_FACTOR
        );
    }
}
