package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.Entity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.thrown.SnowballEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.tag.ItemTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * 供 Java mixin 查询词条状态的轻量桥接 (避免 Java 侧直接操作 Kotlin 状态对象)。
 */
object PerkBoostQuery {

    /** 玩家是否持有"猎人直觉" (对怪物造成伤害 +25%) */
    fun hasHunterInstinct(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_strength") == true

    /** 玩家是否持有"贪婪之手" (受到其他生物 2 倍伤害) */
    fun hasFragileToMobs(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_greedy_hands") == true

    /** 玩家是否持有"小鸟胃" (进食恢复翻倍) */
    fun hasEatBoost(player: PlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_gourmet") == true

    /** 玩家是否持有"救生桶" (摔落伤害时副手水桶当图腾) */
    fun hasFallBucket(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("swift_life_bucket") == true

    /** 玩家是否持有"哈基米" (对苦力怕等效于猫, 由 CatAuraMixin 调用) */
    fun hasCatAura(player: PlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_cat_aura") == true

    /** 玩家是否持有"地下视野" (bingo 夜视管制器放行用, 由 NightVisionControllerMixin 调用) */
    fun hasNightVisionPerk(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("delver_night_vision") == true

    // ---- "风之步伐": 疾跑加成 (与原版疾跑修饰符同机制, 由 WindStrideMixin 在疾跑状态切换时调用) ----

    private val WIND_STRIDE_MOD = Identifier.of("random-perks-bingo", "wind_stride_sprint")
    private const val WIND_STRIDE_BONUS = 0.2
    private const val WIND_STRIDE_PERK = "swift_wind_stride"

    /** 疾跑状态变化时: 持有词条 → 挂/摘 +20% 疾跑加成 (与原版 +30% 疾跑加成同桶相加 → 疾跑 1.5 倍) */
    fun updateWindStride(player: ServerPlayerEntity, sprinting: Boolean) {
        if (PerkStateManager.get(player.uuid)?.hasPerk(WIND_STRIDE_PERK) != true) return
        if (sprinting) applyWindStride(player) else clearWindStride(player)
    }

    /** 挂上疾跑加成 (临时修饰符: 与效果类修饰符一样不随存档保存; 获得词条时正在疾跑也会调用) */
    fun applyWindStride(player: ServerPlayerEntity) {
        val instance = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) ?: return
        instance.removeModifier(WIND_STRIDE_MOD)
        instance.addTemporaryModifier(
            EntityAttributeModifier(WIND_STRIDE_MOD, WIND_STRIDE_BONUS, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        )
    }

    /** 清除疾跑加成 */
    fun clearWindStride(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)?.removeModifier(WIND_STRIDE_MOD)
    }

    // ---- "透视之眼"的代价: 35% 不掉落 (由 XrayDropSuppressMixin 在 Block.dropStacks 拦截) ----

    private const val XRAY_PERK = "delver_xray_vision"

    /** 记录"该玩家本 tick 的挖块掉落被透视惩罚抑制"的 tick (供矿脉嗅觉等联动词条查询) */
    const val XRAY_SUPPRESS_TICK_KEY = "xray_suppress_tick"

    /** 用镐破坏方块时是否应抑制本次掉落生成 (持有词条 + 手持镐 + 35%) */
    fun shouldSuppressXrayDrop(player: ServerPlayerEntity, tool: ItemStack): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(XRAY_PERK)) return false
        if (!tool.isIn(ItemTags.PICKAXES)) return false
        if (player.random.nextFloat() >= 0.35f) return false
        // 抑制生效: 留下本 tick 的标记 —— "矿脉嗅觉"等额外掉落会据此一并抑制, 保证
        // 额外份同样被 X-Ray 惩罚 (不生成式下唯一能让联动成立的方式)
        state.memory[XRAY_SUPPRESS_TICK_KEY] = (player.server?.ticks ?: 0).toString()
        return true
    }

    // "喵人斧"不走自定义秒杀逻辑 —— 它是一把**金斧 + 锋利 1145 + 1 点耐久**,
    // 靠荒谬的附魔等级本身打出致命伤害 (见 ArtisanPerks.NyaAxe)。

    // ---- "大胃袋": 满饱食度也能吃 (由 BigStomachMixin 在 ConsumableComponent.canConsume 拦截) ----

    /** 玩家是否持有"大胃袋" */
    fun hasBigStomach(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_big_stomach") == true

    // ---- "老玩家": 剑的攻击冷却始终蓄满 (由 OldSchoolCombatMixin 在 getAttackCooldownProgress 拦截) ----

    /** 玩家是否持有"老玩家" */
    fun hasOldSchoolCombat(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_veteran") == true

    // ---- "渔者之幸": 幸运 +8 与钓鱼的鹦鹉螺壳加成 (由 FishingLootMixin 调用) ----

    /** 玩家是否持有"渔者之幸" */
    fun hasLuckyFishing(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_lucky_fishing") == true

    // ---- "不死鸟": 免疫火焰伤害 (由 PhoenixMixin 在 LivingEntity.damage 拦截) ----

    /** 玩家是否持有"不死鸟" */
    fun hasPhoenix(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("delver_phoenix") == true

    // ---- "无忧的安眠": 在床上爆炸的维度里放大床爆炸 (由 BedBombMixin 调用) ----

    /** 玩家是否持有"无忧的安眠" */
    fun hasCarefreeSleep(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("delver_carefree_sleep") == true

    // ---- "老吴!?" (拟声: 猫叫): 猫主动攻击持词条的玩家 (由 CatTargetMixin / CatFleeMixin 调用) ----

    /** 玩家是否持有"老吴!?" */
    fun hasYowl(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_yowl") == true

    // ---- "天启日" (神话): 被他击杀的玩家强制死亡掉落 (由 DoomsdayMixin 调用) ----

    /** 玩家是否持有"天启日" */
    fun hasDoomsday(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("mythic_doomsday") == true

    /** "天启日"效果持续时间: 20 分钟 */
    private const val DOOMSDAY_DURATION_TICKS = 20L * 60L * 20L

    /** 效果结束时刻 (server ticks); 不存在表示效果已结束 */
    private const val DOOMSDAY_END_TICK_KEY = "doomsday_end_tick"

    /** 获得词条时开始计时 */
    fun beginDoomsday(player: ServerPlayerEntity) {
        val state = PerkStateManager.get(player.uuid) ?: return
        val now = player.server?.ticks?.toLong() ?: return
        state.memory[DOOMSDAY_END_TICK_KEY] = (now + DOOMSDAY_DURATION_TICKS).toString()
    }

    /** 效果立即结束 (被敌方玩家击杀时调用) */
    fun endDoomsday(player: ServerPlayerEntity) {
        PerkStateManager.get(player.uuid)?.memory?.remove(DOOMSDAY_END_TICK_KEY)
    }

    /** 天启日效果是否仍在持续: 持有词条、未超时、未被击杀结束 */
    fun hasDoomsdayEffect(player: ServerPlayerEntity): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk("mythic_doomsday")) return false
        val endTick = state.memory[DOOMSDAY_END_TICK_KEY]?.toLongOrNull() ?: return false
        val now = player.server?.ticks?.toLong() ?: return false
        return now < endTick
    }

    /** "天启日"最多对几次击杀生效 */
    private const val DOOMSDAY_KILL_LIMIT = 4

    /** 已生效次数记在 state.memory 里, 随对局重置 */
    private const val DOOMSDAY_KILL_KEY = "doomsday_kills"

    /**
     * 记一次由"天启日"触发的强制掉落。
     *
     * @return 是否仍在前 [DOOMSDAY_KILL_LIMIT] 次以内 (之后一直返回 false)
     */
    fun consumeDoomsdayKill(killer: ServerPlayerEntity): Boolean {
        val state = PerkStateManager.get(killer.uuid) ?: return false
        val used = state.memory[DOOMSDAY_KILL_KEY]?.toIntOrNull() ?: 0
        if (used >= DOOMSDAY_KILL_LIMIT) return false
        state.memory[DOOMSDAY_KILL_KEY] = (used + 1).toString()
        return true
    }

    // ---- "这不是挂?" (紫): 射出的箭自动追踪 (由 HomingArrowMixin 在 PersistentProjectileEntity.tick 中转向) ----

    /** 玩家是否持有"这不是挂?" */
    fun hasHomingBow(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_homing_bow") == true

    // ---- "Jockey" (金): 可骑乘其他玩家 (由 JockeyService / PerkWorldEventService 调用) ----

    /** 玩家是否持有"Jockey" */
    fun hasJockey(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_jockey") == true

    // ---- "驯龙高手" (金 · 唤法): 可骑乘其他生物并控制它们使用能力 ----

    /** 玩家是否持有"驯龙高手" */
    fun hasDragonTamer(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("summon_dragon_tamer") == true

    // ---- "雪球大作战" (蓝): 扔出的雪球变大 + 3 伤害 + 击飞 (由 BigSnowballMixin 实现) ----

    /** 玩家是否持有"雪球大作战" */
    fun hasBigSnowball(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("summon_big_snowball") == true

    /** "雪球大作战" 大雪球的 commandTag (服务端标记; commandTags 不同步给客户端) */
    const val BIG_SNOWBALL_TAG = "randomperks_big_snowball"

    /** 该实体是否是"雪球大作战"的大雪球 */
    fun isBigSnowball(entity: Entity): Boolean =
        entity is SnowballEntity && BIG_SNOWBALL_TAG in entity.commandTags

    // ---- "恶魔契约" (金): 造成伤害翻倍, 受到伤害也翻倍 (由 LivingEntityDamageMixin 调用) ----

    /** 玩家是否持有"恶魔契约" */
    fun hasDemonPact(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_demon_pact") == true

    // ---- "珠光护手" (紫): 每次攻击都暴击 (由 PearlGauntletMixin 在 PlayerEntity.attack 里实现) ----

    /** 玩家是否持有"珠光护手" */
    fun hasPearlGauntlet(player: PlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_pearl_gauntlet") == true

    // ---- "逃跑计划" (蓝): 受到生物伤害后获得速度 II + 抗性 I (由 PerkWorldEventService 处理) ----

    /** 玩家是否持有"逃跑计划" */
    fun hasEscapePlan(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("swift_escape_plan") == true

    // ---- "良子冲击" (紫): 饱食度满时造成伤害 +50% (由 LivingEntityDamageMixin 调用) ----

    /** 玩家是否持有"良子冲击" */
    fun hasSatiationStrike(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_satiation_strike") == true

    // ---- "宝可梦" (金): 鸡蛋砸中非 Boss 生物 → 变成刷怪蛋 (由 PokemonEggMixin 实现) ----

    /** 玩家是否持有"宝可梦" */
    fun hasPokemon(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("summon_pokemon") == true

    // ---- "磁力升级" (蓝): 拾取范围 5 格 (由 MagnetService 定期扫描) ----

    /** 玩家是否持有"磁力升级" */
    fun hasMagnetUpgrade(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("seeker_magnet_upgrade") == true

    // ---- "拾穗" (蓝): 收获成熟作物时 50% 额外掉一个随机种子 (由 PerkWorldEventService 处理) ----

    /** 玩家是否持有"拾穗" */
    fun hasGleaning(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_gleaning") == true

    // ---- "TNT 投手" (紫): 手持 TNT 右键改为抛射 (由 TntThrowService 处理) ----

    /** 玩家是否持有"TNT 投手" */
    fun hasTntThrow(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("summon_tnt_throw") == true

    // ---- "骨粉撒花": 对草方块用骨粉时额外长出随机花 ----

    /** 玩家是否持有"骨粉撒花" */
    fun hasBoneMealFlowers(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_bone_meal_flowers") == true

    // ---- "真菌感染" (金): 攻击敌方玩家时转化对方物品 ----

    /** 玩家是否持有"真菌感染" */
    fun hasFungalInfection(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_fungal_infection") == true

    // ---- "细嚼慢咽" (紫): 进食食物期间受到伤害 -65% ----

    /** 玩家是否持有"细嚼慢咽" */
    fun hasSlowChewing(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("harvest_slow_chewing") == true

    // ---- "刺猬" (紫): 受到生物伤害时反弹 75% ----

    /** 玩家是否持有"刺猬" */
    fun hasHedgehog(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("hunter_hedgehog") == true

    // ---- "复活甲" (紫): 致命伤害时自动触发不死图腾 ----

    /** 玩家是否持有"复活甲" */
    fun hasReviveArmor(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk(ReviveArmorService.PERK_ID) == true

    // ---- "忠心护主" (紫): 受到的伤害由宠物承受 ----

    /** 玩家是否持有"忠心护主" */
    fun hasLoyalGuard(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk(LoyalGuardService.PERK_ID) == true

    // ---- "不痛不痒" (蓝): 5 点及以下伤害改为 1 点 ----

    /** 玩家是否持有"不痛不痒" */
    fun hasSmallDamageGuard(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk("artisan_barely_a_scratch") == true

    // ---- "野人魔了" (紫): 职业方块把傻子村民变成大师级村民 ----

    /** 玩家是否持有"野人魔了" */
    fun hasSavageMagic(player: ServerPlayerEntity): Boolean =
        PerkStateManager.get(player.uuid)?.hasPerk(SavageMagicService.PERK_ID) == true
}
