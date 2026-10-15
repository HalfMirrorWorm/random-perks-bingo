package com.randomperks.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 LivingEntity 的 protected getJumpVelocity(), 供"二段跳"读取玩家**真实的跳跃冲量**
 * (自动包含跳跃提升等一切加成, 保证"第二次跳跃 = 和第一次完全相同的跳跃")。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpVelocityInvoker {

    @Invoker("getJumpVelocity")
    float invokeGetJumpVelocity();
}
