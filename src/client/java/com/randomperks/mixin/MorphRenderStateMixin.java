package com.randomperks.mixin;

import com.randomperks.client.render.MorphSkinHolder;
import net.minecraft.client.render.entity.state.PigEntityRenderState;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给猪的渲染状态挂一个"要替换成的玩家皮肤"字段 ("变群友"用) */
@Mixin(PigEntityRenderState.class)
public class MorphRenderStateMixin implements MorphSkinHolder {

    @Unique
    private SkinTextures randomperks$skin;

    @Override
    public SkinTextures randomperks$getSkin() {
        return this.randomperks$skin;
    }

    @Override
    public void randomperks$setSkin(SkinTextures skin) {
        this.randomperks$skin = skin;
    }
}
