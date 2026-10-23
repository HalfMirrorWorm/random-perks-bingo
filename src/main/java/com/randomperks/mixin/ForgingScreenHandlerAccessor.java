package com.randomperks.mixin;

import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取 ForgingScreenHandler 的 protected {@code context} (铁砧方块坐标)。 */
@Mixin(ForgingScreenHandler.class)
public interface ForgingScreenHandlerAccessor {

    @Accessor("context")
    ScreenHandlerContext getContext();
}
