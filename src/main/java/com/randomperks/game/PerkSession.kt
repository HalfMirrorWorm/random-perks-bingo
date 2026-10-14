package com.randomperks.game

import com.randomperks.config.PerkConfig

/**
 * 当前对局的词条系统运行状态 (不持久化)。
 *
 * 开关是"每局生效"的: 每局开局时从 [PerkConfig] 快照一次,
 * 局中通过命令修改开关**不影响当前对局**, 从下一局开始生效。
 */
object PerkSession {

    /** 本局是否启用 (开局时快照); null = 尚未开局, 此时按配置值处理 */
    private var currentRoundEnabled: Boolean? = null

    /** 当前是否应当发放词条机会 */
    fun isEnabled(): Boolean = currentRoundEnabled ?: PerkConfig.enabled

    /** 开局时调用: 快照本局开关 */
    fun beginRound() {
        currentRoundEnabled = PerkConfig.enabled
    }

    /** 对局清理/重置时调用 */
    fun reset() {
        currentRoundEnabled = null
    }
}
