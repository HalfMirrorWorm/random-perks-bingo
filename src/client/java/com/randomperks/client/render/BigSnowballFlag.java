package com.randomperks.client.render;

/**
 * 挂在投掷物渲染状态上的"这是大雪球"标记。
 *
 * 实体 id 只能在 updateRenderState 阶段读到 (render 阶段手上只有 state), 所以
 * 需要一个中转字段 (由 BigSnowballRenderStateMixin 混入 FlyingItemEntityRenderState)。
 */
public interface BigSnowballFlag {

    boolean randomperks$isBig();

    void randomperks$setBig(boolean big);
}
