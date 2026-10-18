package com.randomperks.mixin;

import com.randomperks.client.render.BigSnowballFlag;
import net.minecraft.client.render.entity.state.FlyingItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给投掷物的渲染状态挂一个"这是大雪球"标记 ("雪球大作战"用) */
@Mixin(FlyingItemEntityRenderState.class)
public class BigSnowballRenderStateMixin implements BigSnowballFlag {

    @Unique
    private boolean randomperks$big;

    @Override
    public boolean randomperks$isBig() {
        return this.randomperks$big;
    }

    @Override
    public void randomperks$setBig(boolean big) {
        this.randomperks$big = big;
    }
}
