package com.randomperks.mixin;

import com.randomperks.client.ChickenDyeState;
import com.randomperks.client.render.ChickenDyeHolder;
import net.minecraft.client.render.entity.state.ChickenEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给鸡的渲染状态挂一个"染色"字段 ("叮咚鸡"用) */
@Mixin(ChickenEntityRenderState.class)
public class ChickenDyeRenderStateMixin implements ChickenDyeHolder {

    @Unique
    private int randomperks$dyeColor = ChickenDyeState.NO_TINT;

    @Override
    public int randomperks$getDyeColor() {
        return this.randomperks$dyeColor;
    }

    @Override
    public void randomperks$setDyeColor(int color) {
        this.randomperks$dyeColor = color;
    }
}
