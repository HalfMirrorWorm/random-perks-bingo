package com.randomperks.client.render;

import net.minecraft.client.util.SkinTextures;

/**
 * 挂在猪的渲染状态上的"要替换成的玩家皮肤"。
 *
 * 被变的实体如果是玩家, 猪头位置会渲染成他自己的皮肤 —— 皮肤只能在
 * updateRenderState 时从实体上取到, 而 render 阶段已经拿不到实体了,
 * 所以需要一个中转字段 (由 MorphRenderStateMixin 混入 PigEntityRenderState)。
 */
public interface MorphSkinHolder {

    SkinTextures randomperks$getSkin();

    void randomperks$setSkin(SkinTextures skin);
}
