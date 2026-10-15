package com.randomperks.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WanderingTraderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 调用原版流浪商人刷新的私有实现 (trySpawn): 随机选一名玩家 → 48 格内寻找合法位置 →
 * 生成商人 + 2 只商队羊驼 → 设置漫游目标, 与自然刷新完全一致。
 */
@Mixin(WanderingTraderManager.class)
public interface WanderingTraderManagerInvoker {

    @Invoker("trySpawn")
    boolean invokeTrySpawn(ServerWorld world);
}
