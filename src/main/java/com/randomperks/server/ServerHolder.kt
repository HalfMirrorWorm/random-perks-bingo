package com.randomperks.server

import net.minecraft.server.MinecraftServer

/**
 * 当前服务端实例的引用 (由 Fabric 生命周期事件维护)。
 */
object ServerHolder {
    @Volatile
    var server: MinecraftServer? = null
}
