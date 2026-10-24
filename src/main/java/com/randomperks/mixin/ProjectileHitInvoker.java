package com.randomperks.mixin;

import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link ProjectileEntity#hitOrDeflect} (protected)。
 *
 * <p>为什么不用 @Shadow: {@code hitOrDeflect} 声明在 ProjectileEntity 上,
 * PersistentProjectileEntity 只是继承。本项目没有 refmap (Loom 1.14 直接把 mixin 里的
 * yarn 名 remap 成 intermediary), 而 @Shadow 在目标类**自身**里找不到继承成员就会在
 * APPLY 阶段抛 InvalidMixinException。用声明类上的 @Invoker 取就没有这个问题。
 */
@Mixin(ProjectileEntity.class)
public interface ProjectileHitInvoker {

    @Invoker("hitOrDeflect")
    ProjectileDeflection randomperks$invokeHitOrDeflect(HitResult hitResult);
}
