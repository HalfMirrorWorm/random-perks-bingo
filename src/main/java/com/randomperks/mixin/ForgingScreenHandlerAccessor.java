package com.randomperks.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取 ForgingScreenHandler 的 protected {@code context} (铁砧方块坐标)。 */
@Mixin(ItemCombinerMenu.class)
public interface ForgingScreenHandlerAccessor {

    @Accessor("access")
    ContainerLevelAccess getContext();
}
