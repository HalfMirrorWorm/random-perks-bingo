# 26.3 全量词条复核（第二轮）

- 日期：2026-11-21
- 范围：`PerkContent.registerAll()` 注册的全部 **152** 个词条
  （9 个内容文件；含 13 个特质、13 个 id 由服务常量提供的词条）
- 方法：逐个对照 `zh_cn.json` / `en_us.json` 的玩家描述与词条定义，
  再沿 mixin / 服务核对实际效果；前一轮已修复或用户明确"保持现状"的条目不再重复提出
- 结论：**152 个已全部核对**；发现 2 个现行代码问题、1 个服务遗漏、1 组目标选择器问题、
  若干描述/交互问题。以下按严重程度排列

---

## 一、需要修代码

### 1. 核电轻而易举：爆炸把**原本就是空气**的格子也变成矿石（高）

- 中：选择任意一名玩家, 在他头顶生成一只高亮显示的闪电苦力怕；爆炸炸掉的方块会变成随机矿石。
- 英：Choose any player: a glowing charged creeper spawns above them; blocks destroyed by its explosion turn into random ores.

实现：`NukeExplosionMixin` 在 `ServerExplosion.calculateExplodedPositions` 的 RETURN 缓存位置列表，
`NukeExplosionService.fillOres` 对列表中**当前为空气**的格子填随机矿石。

问题：`calculateExplodedPositions` 的判定是
`power > 0 && damageCalculator.shouldBlockExplode(...)`；默认
`ExplosionDamageCalculator.shouldBlockExplode` 直接返回 `true`，而空气/空流体在
`getBlockExplosionResistance` 里返回 `Optional.empty`、不扣威力。因此该列表是**整颗爆炸的球形体积**
（含原本就空的格子），并不只是被炸掉的方块。

场景：闪电苦力怕在开阔地爆炸时，爆心周围（含地面上方、既有洞穴）所有空气格都会变成矿石，
形成一整团矿石；描述只承诺"炸掉的方块"变矿。

修法：在 `calculateExplodedPositions` RETURN 时先筛掉**爆炸前就是空气**的位置
（快照 `getBlockState(pos).isAir`），`fillOres` 仍保留"爆炸后为空气"的二次检查；
这样抗爆方块（仍是原方块）与原本的空气都不会被动。

### 2. 渔者之幸：钓饵 III 实际只有 3 tick 的等待缩减（中）

- 中：钓鱼自带钓饵 III 与海之眷顾 III；每次钓起额外有 50% 概率得到一个鹦鹉螺壳。
- 英：Fishing always has Lure III and Luck of the Sea III; each catch has a 50% chance for an extra nautilus shell.

实现：`FishingEnchantMixin` 把浮标的等待缩减设为 `Math.max(waitTimeReductionTicks, 3)`。
原版 `FishingRodItem.use` 传给 `FishingHook` 的是
`(int)(EnchantmentHelper.getFishingTimeReduction(...) * 20.0f)`；
`data/minecraft/enchantment/lure.json` 的 `fishing_time_reduction` 为
base 5.0、每级 +5.0 → **钓饵 III = 15 秒 = 300 tick**。所以未附魔鱼竿只得到 3 tick（0.15 秒）缩减，
不是钓饵 III；附魔了钓饵 I/II 的鱼竿也不会被抬到 III。

海之眷顾的 `luck` 参数是等级（0~3），`Math.max(luckBonus, 3)` 是对的。

修法：把 3 改成 `15 * 20`（300），或从钓饵 III 的数据计算。

### 3. 野人魔了：缺少石切台（石匠）（中）

- 中：获得 10 个绿宝石；手持职业方块右键傻子村民可把他转职成该职业的大师，喂村民谜之炖菜会让他变回傻子。
- 英：Gain 10 emeralds; right-click a nitwit with a profession block to make it a master of that profession. Feeding a villager suspicious stew turns it into a nitwit.

`SavageMagicService.BLOCK_TO_PROFESSION` 列了 12 种工作站，
唯独没有 `Items.STONECUTTER -> VillagerProfession.MASON`。
手持石切台右键傻子村民会落到原版逻辑（无反应）。

修法：补一行 `Items.STONECUTTER to VillagerProfession.MASON`。

### 4. 目标选择器仍会把死亡界面上的玩家列为候选（中）

描述都是"选择一名玩家"，但下列词条的 `candidates` 只过滤旁观者：

- `seeker_puny`（!?区区!?）
- `artisan_poisoner`（绝命毒师）
- `mythic_hodgepodge`（游戏BUG）
- `summon_call_lightning`（引雷术）
- `hunter_easy_nuke`（核电轻而易举）
- `summon_insomnia`（失眠）
- `seeker_pickpocket` / `StealService`（顺手牵羊）

死亡界面的玩家仍在 `playerList` 中；选中后：

- 游戏BUG：主背包可能已被死亡清空 → 扣掉一次使用次数、`changed = 0`；
- 绝命毒师：给尸体挂负面效果，重生时随死亡清除；
- 失眠：把弩和光灵箭塞进死者的尸体背包；
- 顺手牵羊：打开一个空的尸体背包；
- 区区 / 引雷 / 核电：效果落在死亡点。

远程传输、何易位、共鸣已经排除了 `!isAlive`；建议统一成一个候选辅助函数，
至少加上 `it.isAlive && !it.isRemoved`。

### 5. 游戏BUG：目标主背包为空时仍扣一次使用次数（低）

`mythic_hodgepodge` 的 `slots` 为空时仍会走到 `state.consumeUse(this)`，
消息显示"换了 0 格物品"。选中一个主背包为空的活人（或上面的死者）会白白消耗两次机会中的一次。
建议 `slots.isEmpty()` 时提示并 `return`，不扣次数。

### 6. 猎杀印记：会把死亡玩家当成"最近的敌人"（低）

- 中：始终标记最近的敌方玩家（方位、坐标、距离与高亮）；目标在 256 格内时你移速 +50%。
- 英：The nearest enemy is always marked (direction, coords, distance, outline); +50% speed while within 256 blocks of them.

`HunterPerks.MarkedPrey.nearestEnemy` 没有过滤 `isAlive`：最近的敌人死亡后，
追踪/高亮/加速仍会以尸体为目标，直到他重生或另一个敌人更近。
建议加上 `other.isAlive`（必要时再排除 `isDeadOrDying`）。

---

## 二、描述问题

### 7. 连锁挖矿：描述缺"单次最多 64 块"与"深板岩变种"（中）

- 当前中：用镐挖掘矿石时, 会把相连的同种矿石一并挖掘。
- 当前英：Mining an ore with a pickaxe also breaks connected ores of the same type.

代码：`MAX_CHAIN = 64`；`oreKey` 会去掉 `deepslate_` 前缀，把
`coal_ore` 与 `deepslate_coal_ore` 当成同一矿脉。上一轮 #25 的记录写了
"描述补上单次最多 64 块和含深板岩变种"，但当前语言文件里没有这两句，属于记录与文件不一致。
建议补成："……会把相连的同种矿石（含深板岩变种）一并挖掘，单次最多 64 块。"

### 8. 一键伐木：描述缺"单次最多 128 块"（低）

当前描述只说"一键砍倒整棵相连的同种树"，代码 `MAX_LOGS = 128`。
建议补上"单次最多 128 块"。

### 9. 老吴!?：只有**未驯服**的猫会主动攻击（低）

- 当前中：猫会主动攻击你; 杀死猫额外掉 3 根线, 并额外掉落 1 件猫的礼物。
- 当前英：Cats will actively attack you; killing a cat drops 3 extra string and 1 extra cat gift item.

`CatTargetMixin` 的目标规则要求 `!self.isTame()`，家养的猫（例如队友的宠物）不会攻击你；
`CatFleeMixin` 则对所有猫生效（猫不再怕你）。如果这是有意的（不策反别人的宠物），
描述建议改成"未驯服的猫会主动攻击你"；如果想让家猫也攻击，则要改代码。

---

## 三、设计交互（需要你定）

### 10. "屏蔽"是否应约束非三选一的获取途径

- 中：选择一个词条类型，之后你的词条选择中不会再出现该类型的词条。
- 英：Choose a perk archetype; it will no longer appear in your perk offers.

普通三选一、蔚蓝、精选、屏蔽后的重抽都已读取 `PerkExclusion`；但：

- `PerkGrantService.grantRandomPerks`（天赐）调用 `rollSingle(excluded)` 时没有传
  `excludedArchetype`，可以直接发到被屏蔽的流派；
- `TraitPerks.Transmute.purpleCandidates`（升华）没有过滤被屏蔽的流派；
- `TraitPerks.Replay`（回放）可能回放被屏蔽流派的词条（它是"复刻别人施加过的"，语义上可以另算）。

如果"不再出现"指**所有获取途径**，应给天赐/升华补上过滤；如果只指"三选一候选"，保持现状即可。

### 11. "净化"是否应覆盖天启日的自掉落

- 中：选择一个自己已拥有的词条，使其负面效果无效。
- 英：Choose one of your perks to nullify its downside.

`PerkDownsideSuppression.ELIGIBLE` 目前覆盖 14 个有明确负面效果的词条，
但不含 `mythic_doomsday` 的"你被敌方玩家击杀时也掉落主背包并结束效果"。
如果这是一条可净化的负面效果，加进去即可（击杀侧的强制掉落保持不变）；
如果红/神话词条不参与净化，保持现状。

---

## 四、仅注释过期（不影响玩法）

- `SwiftPerks.GuraShark` 上方注释仍写"潮涌能量只在水/雨中续 13 秒、上岸会掉"，
  实际 `applyPermanent` 已改成无限时长（下方 246~251 行的注释才是对的）。
- `SwiftPerks.Dash` 注释写"水平方向、每段离地限一次"；实际是含垂直分量的视线方向、只保留 3 秒冷却。
- `HarvestPerks.Pokemon` 注释写"获得 4 个鸡蛋"；实际 16 个（语言文件也是 16）。
- `HunterPerks.HomingBow` 注释写"16 格内最近的敌对目标"；实际 64 格、按准心夹角最小锁定。
- `SwiftPerks.WaypointClock` 注释写"当前维度 2000 格"；实际只允许主世界。
- `SeekerPerks.LifeVision` 的注释写"描述写的是 64 格"；当前描述/代码都是 32 格。

---

## 五、覆盖清单

| 文件 | 数量 | 说明 |
|---|---:|---|
| SwiftPerks.kt | 18 | 疾风 |
| SeekerPerks.kt | 16 | 秘藏（含暖水考古/野人魔了） |
| DelverPerks.kt | 19 | 深掘（含切洋葱） |
| HunterPerks.kt | 25 | 猎杀 / 唤法 |
| HarvestPerks.kt | 29 | 丰饶 / 唤法（含海带成骨、骨粉撒花、苔藓侵蚀、世界一直下雨） |
| ArtisanPerks.kt | 20 | 匠造（含有福同享、点石成金、复活甲） |
| SummonPerks.kt | 8 | 唤法（含共鸣、忠心护主） |
| MythicPerks.kt | 4 | 神话 / 红 |
| TraitPerks.kt | 13 | 特质 |
| **合计** | **152** | |

已按前一轮结论保持现状、本轮不重复提出的：灵魂出窍（#31）、开摆（#32）、
区区描述（#33）、马来的跨局残留（#34）。其余词条的描述、数值、触发条件与清理路径未发现新的不一致。
