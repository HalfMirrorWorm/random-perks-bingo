package com.randomperks.config

import com.randomperks.RandomPerksMod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

@Serializable
private data class ConfigData(val enabled: Boolean = true)

/**
 * 词条系统的总开关 (持久化到 `config/random-perks-bingo.json`)。
 *
 * - enabled = true (默认): 正常发放选择机会
 * - enabled = false: 不发放任何机会; 切换时会清空所有玩家的词条与效果
 */
object PerkConfig {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile: Path
        get() = FabricLoader.getInstance().configDir.resolve("random-perks-bingo.json")

    /** 当前是否启用词条系统 */
    var enabled: Boolean = true
        private set

    fun load() {
        enabled = runCatching {
            if (Files.exists(configFile)) {
                json.decodeFromString(ConfigData.serializer(), Files.readString(configFile)).enabled
            } else {
                true
            }
        }.getOrElse {
            RandomPerksMod.logger.warn("[RandomPerks] 配置读取失败, 默认启用: {}", it.toString())
            true
        }
        save() // 确保配置文件存在
        RandomPerksMod.logger.info("[RandomPerks] perk system enabled = {}", enabled)
    }

    fun set(value: Boolean) {
        enabled = value
        save()
    }

    private fun save() {
        runCatching {
            Files.createDirectories(configFile.parent)
            Files.writeString(configFile, json.encodeToString(ConfigData.serializer(), ConfigData(enabled)))
        }.onFailure {
            RandomPerksMod.logger.warn("[RandomPerks] 配置保存失败: {}", it.toString())
        }
    }
}
