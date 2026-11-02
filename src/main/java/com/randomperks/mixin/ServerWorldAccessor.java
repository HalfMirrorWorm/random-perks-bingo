package com.randomperks.mixin;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 ServerWorld 的私有字段, 供"流浪拴绳"词条调用原版流浪商人刷新逻辑。
 */
@Mixin(ServerLevel.class)
public interface ServerWorldAccessor {

    /** 世界持有的特殊生成器列表 (其中包含 WanderingTraderManager) */
    @Accessor("customSpawners")
    List<CustomSpawner> getSpawners();

    /** 世界属性 (构造 WanderingTraderManager 时的回退参数) */
    @Accessor("serverLevelData")
    ServerLevelData getWorldProperties();
}
