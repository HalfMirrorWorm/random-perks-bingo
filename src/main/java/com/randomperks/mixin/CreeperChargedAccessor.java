package com.randomperks.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 原版 CreeperEntity 只有 isCharged() 读方法, CHARGED 是私有的 TrackedData。
 * 这里暴露出来, 供"核电轻而易举"直接把苦力怕设为闪电形态 (不借助闪电实体, 避免额外伤害/点燃)。
 */
@Mixin(CreeperEntity.class)
public interface CreeperChargedAccessor {

    @Accessor("CHARGED")
    static TrackedData<Boolean> getChargedTracker() {
        throw new AssertionError();
    }

    /** 引信总时长 (tick); 原版默认 30 (1.5 秒) */
    @Accessor("fuseTime")
    void setFuseTime(int fuseTime);
}
