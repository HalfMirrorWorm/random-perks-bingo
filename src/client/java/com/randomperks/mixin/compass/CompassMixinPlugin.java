package com.randomperks.mixin.compass;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 罗盘兼容 mixin 的开关: 没装对应的 compass mod 就跳过, 避免 "找不到目标类" 的启动崩溃。
 *
 * <p>这两个 mixin 是纯客户端功能 (往第三方搜索框里补拼音匹配), 与服务器无关。
 */
public final class CompassMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".NaturesCompassSearchMixin")) {
            return FabricLoader.getInstance().isModLoaded("naturescompass");
        }
        if (mixinClassName.endsWith(".ExplorersCompassSearchMixin")) {
            return FabricLoader.getInstance().isModLoaded("explorerscompass");
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
