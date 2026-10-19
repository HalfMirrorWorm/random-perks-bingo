package com.randomperks.client.render;

/** 给鸡的渲染状态挂一个"染色"字段 ("叮咚鸡"用) */
public interface ChickenDyeHolder {

    /** 当前颜色 (ARGB); 未染色时是 ChickenDyeState.NO_TINT */
    int randomperks$getDyeColor();

    void randomperks$setDyeColor(int color);
}
