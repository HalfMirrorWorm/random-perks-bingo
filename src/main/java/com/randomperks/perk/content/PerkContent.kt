package com.randomperks.perk.content

/**
 * 所有词条的注册入口 (由 RandomPerksMod.onInitialize 调用)。
 */
object PerkContent {
    fun registerAll() {
        SwiftPerks.registerAll()
        DelverPerks.registerAll()
        HarvestPerks.registerAll()
        HunterPerks.registerAll()
        ArtisanPerks.registerAll()
        SeekerPerks.registerAll()
    }
}
