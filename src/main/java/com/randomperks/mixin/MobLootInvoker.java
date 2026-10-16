package com.randomperks.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 MobEntity 的 protected dropLoot(), 供"贪婪之手"把一只生物的**掉落表再滚一次**。
 *
 * 这样额外掉落是"这只怪自己的战利品", 且完全走原版掉落结算 (击杀者/伤害来源/抢夺附魔/
 * killed_by_player 条件全部照旧)。取代了旧的"扫描尸体周围地上的物品实体再复制"——
 * 那种做法会连玩家刚丢出的物品一起复制 (物品复制漏洞), 同一 tick 群杀时还会链式放大。
 */
@Mixin(MobEntity.class)
public interface MobLootInvoker {

    @Invoker("dropLoot")
    void invokeDropLoot(ServerWorld world, DamageSource damageSource, boolean causedByPlayer);
}
