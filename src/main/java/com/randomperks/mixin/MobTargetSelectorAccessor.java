package com.randomperks.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 MobEntity 的 targetSelector。
 *
 * 为什么不用 @Shadow: {@code targetSelector} 声明在 MobEntity 上, CatEntity 只是继承。
 * 本项目没有 refmap (Loom 1.14 直接把 mixin 里的 yarn 名 remap 成 intermediary),
 * 而 @Shadow 在目标类**自身**里找不到这个字段就会在 APPLY 阶段抛 InvalidMixinException
 * ("field_6185 was not located in the target class class_1451")。
 * 用声明类上的 @Accessor 取就没有这个问题。
 */
@Mixin(MobEntity.class)
public interface MobTargetSelectorAccessor {

    @Accessor("targetSelector")
    GoalSelector randomperks$getTargetSelector();
}
