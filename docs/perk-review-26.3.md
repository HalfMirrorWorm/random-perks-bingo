# 26.3 词条审查记录

- 日期：2026-11-03（审查）· 2026-11-05（第一批修复）
- 分支：`26.3`（与 `main` 同步）
- 审查基线：`1f7636a`
- 状态：**「会丢东西或复制」5 条已修复**，其余待处理
- 对照：`zh_cn.json` 的玩家描述，以及获得、重生、移除、每秒 `onTick`、一次性点击

## 2026-11-05 修复记录（第 1 批：丢东西 / 复制）

1. **远程传输**：候选排除死亡玩家（尸体收不到物品）；目标为创造模式且背包装不下时先算空间，
   放不下就整份掉在脚下，不再依赖 `Inventory.add` 之后的剩余数量（创造模式会把剩余清零）。
2. **便携潜影盒**：界面开着时每 tick 把临时库存写回 `CONTAINER` 组件（内容不变则跳过），
   断开连接时再补一次 —— 玩家掉线 / 服务器崩溃都不会出现"背包里有、盒子里还有"的复制。
3. **游戏BUG**：`onActivate` 已实现，选择器被 Esc 关掉后可以再从 V 界面点一次；
   随机替换排除原物品，并优先挑堆叠上限放得下当前数量的物品；
   数量超过新物品上限时拆堆掉落，不再产生 64 把剑这种非法堆叠。
4. **逆转时间**：恢复顺序改为「词条状态 → 玩家 NBT → 容器 → 生物 → 清理新掉落物 → 重挂效果」，
   检查点里的药水不再被 `clearAllPerkState` 的 `onRemove` 清掉；回溯时删除这段时间新生成的
   掉落物与经验球；容器所在区块先同步加载再还原；描述补上 10 秒倒计时。
5. **大背包**：上限改为 `min(上限×4, 99)`（原版存档 `count` 的极限是 99）；
   移除词条时把超额堆叠拆到空位、放不下就掉落；描述补上 99 的上限说明。

范围是 `src/main/java/com/randomperks/perk/content/` 里注册的全部词条，并沿 mixin 与服务核对了实际效果。切洋葱、海带成骨、骨粉撒花、苔藓侵蚀与描述一致，下面不单列。

## 会丢东西或复制

### 1. 远程传输 `summon_remote_transfer`

`SummonPerks.kt` 的候选只排除旁观者和自己，不看目标是否存活。死亡界面上的队友仍在玩家列表里。提交后先从自己背包拿走物品，再 `inventory.add` 进那具尸体并标记已使用。非保留物品栏时，这些物品不会掉落，也不会跟着重生。

目标是创造模式且背包塞不下时，原版 `PlayerInventory.insertStack` 会把剩余数量清成 0（`ItemStack.java` 的 `isInCreativeMode` 分支）。调用方看到剩余为空就不再掉在脚下，溢出部分消失。

### 2. 便携潜影盒 `seeker_magnet_upgrade`

`ContainerOpenService.kt` 把潜影盒内容复制进临时库存，只在界面关闭且玩家仍在线时写回 `CONTAINER` 组件。`END_SERVER_TICK` 里如果 `getPlayer` 已经是 null，会话被直接删掉，不写回。

从盒子拿进背包的物品留在玩家存档里，盒子组件仍是打开时的旧内容，重进后两边都有。放进界面、尚未写回的物品只存在被丢掉的临时库存里。末影箱打开的是玩家自己的末影箱，没有这条路径。

### 3. 游戏BUG `mythic_hodgepodge`

获得时弹出选择器，但不是 `isOneTime`，V 界面不能再次打开。`TargetPickScreen` 可以按 Esc 关闭且不发包。关掉界面，或 `sendActionTargets` 发不出去之后，效果不会发生，也没有第二次机会。`MythicPerks.kt` 的 `handleTargetPick` 只有选中后才 `markUsed`。

`randomItem` 不排除原来的物品，结果可以和原来相同。`ItemStack(replacement, stack.count)` 不按 `maxStackSize` 截断，64 个圆石可以变成一格 64 把剑或 64 个鞘翅。

### 4. 逆转时间 `mythic_time_rewind`

描述是获得后立即触发。`RewindService.kt` 的 `COUNTDOWN_TICKS` 是 10 秒，`begin` 先广播再空等。

回溯不删除这段时间生成的掉落物和经验球。矿石写回之后，地上的掉落物还在。容器只快照检查点时已加载的方块实体；还原时区块未加载就 `continue`（`restoreContainers`）。描述承诺容器物品会被还原，未加载的箱子会被跳过。

`finishRestore` 先装回记录点里的玩家 NBT（含药水），再走 `clearAllPerkState` 的 `onRemove`。深海之息会无条件清掉水下呼吸，古拉鲨鱼会清海豚祝福和潮涌能量，记录点里同类型的药水会被一起清掉。

### 5. 大背包 `artisan_big_backpack`

描述是堆叠上限变为 4 倍。`BigBackpackInventoryMixin` 与 `BigBackpackSlotMixin` 返回 `min(物品上限 × 4, 256)`，64 一组的物品可以堆到 256。

玩家背包存档使用 `ItemStack` 的 codec，`count` 的范围是 `Codecs.rangedInt(1, 99)`（`ItemStack.java`）。100 到 256 的堆叠在对局中可以暂时存在，存档或重进时该格写不进去。移除词条时也没有把超额堆叠拆开。

## 伤害和攻击

### 6. 忠心护主 `summon_loyal_guard`

描述是附近属于你的宠物替你承受 2 倍伤害。`PerkWorldEventService` 在 `tryRedirect` 返回 true 时取消玩家这次伤害。`LoyalGuardService.tryRedirect` 找到宠物后无条件 `return true`，不看 `hurtServer` / `damage` 的结果。

原版 `LivingEntity.damage` 在受伤冷却前期且新伤害不高于 `lastDamageTaken` 时直接返回 false；火焰伤害遇到火焰抗性也会返回 false。宠物没扣血，玩家也不扣血。连续受击时，宠物通常只吃下第一下的 2 倍。

### 7. 老玩家 `hunter_veteran`

描述是剑变为 1.9 版本以前的剑。`SWEEPING_DAMAGE_RATIO` 的范围是 0 到 1（`EntityAttributes.java`）。这里加的 -1 会被夹回 0。`Player.attack` 在满蓄力、着地、非冲刺击退、且这一下没有暴击时仍然横扫，周围生物受到 `1 + 0 × 攻击力` 的伤害和横扫音效。

举剑期间，`LivingEntityDamageMixin` 把该玩家受到的全部伤害乘 0.5。摔落、岩浆、溺水、魔法，以及从背后打来的攻击都会减半。

### 8. 珠光护手 `hunter_pearl_gauntlet`

描述是每次攻击都会暴击，剑不再能横扫。`PearlGauntletMixin` 注入的是 `Player.canCriticalAttack`。26.3 的 `Player.attack` 已把暴击条件内联（`PlayerEntity.java` 约 1161 行），源码里没有 `canCriticalAttack`。这个注入对不上当前的攻击函数。横扫比例同样是会被夹回 0 的 -1，剑仍然横扫。

### 9. 刺猬 `hunter_hedgehog`、盘丝大仙 `hunter_spider_weaving`

两条都挂在 Fabric `ServerLivingEntityEvents.AFTER_DAMAGE` 上。该事件的说明写明：伤害把实体打死时不触发。致死的那一击不会按 75% 反弹。一击杀死蜘蛛时身上没有盘丝，`WeavingMobEffect` 只在移除原因为被击杀时织网，死亡不会出蛛网。先打出非致死伤害、5 秒内再击杀，织网仍然会发生。

### 10. 风之步伐 `swift_wind_stride`

描述是疾跑时移动速度额外提升 30%（注释里的目标是 1.3 倍变成 1.69 倍）。加成是 `ADD_MULTIPLIED_TOTAL` 的 +0.39。26.3 的 `EntityAttributeInstance.computeValue` 对这个运算是逐个相乘，不是先相加：原版疾跑 ×1.3，再 ×1.39，约 1.81 倍基础速度，相对原版疾跑是快 39%。

修饰符在 `setSprinting` 时由客户端本地挂上。获得词条时若已经在疾跑，服务端会挂上，客户端要松开再按下才加速。移除词条或对局清空时，服务端会摘掉，客户端只要疾跑标记还在就会留着加成，直到下一次疾跑状态切换。`WindStrideClient.kt`。

## 和描述不一致

### 11. 冲刺 `swift_dash`

描述是在空中按下潜行键，朝视线方向冲刺，冷却 3 秒。`PerkAbilityService.handleDash` 另外限制：每段离地只能冲一次，滑翔中直接拒绝，并且只改水平速度，垂直速度原样保留。

### 12. 古拉鲨鱼 `swift_gura_shark`

描述把海豚祝福和潮涌能量都写成永久。海豚祝福是永久效果，陆地禁疾跑与 `GuraSharkSprintMixin` 一致。潮涌能量只在 `isInWaterOrRain` 时续 260 tick（13 秒），上岸后会掉。`SwiftPerks.kt` 的 `onTick`。

### 13. 弹簧腿 `swift_jump`

词条本身只加 `JUMP_STRENGTH` +0.13，高度约 2 格，与描述一致。`PermanentEffects.ENTRIES` 仍把 `MobEffects.JUMP_BOOST` 登记在 `swift_jump` 上。玩家身上的跳跃提升被牛奶、「落地，水」的 `removeAllEffects`，或有限时长到期清掉时，`EffectPersistMixin` 会补上一份无限时长的跳跃提升 I。它和 +0.13 叠在一起，跳得比描述高，并且留到词条移除。

### 14. 深海之息 `swift_deep_breath`、喵人斧 `artisan_nya_axe`

两条描述都是永久效果。获得和重生时会挂无限时长，移除时会清掉。`PermanentEffects.ENTRIES` 没有水下呼吸，也没有虚弱。喝牛奶，或被有限时长的同类效果覆盖后再到期，效果不会补回，要到下次重生才回来。

### 15. 天启日 `mythic_doomsday`

描述是 20 分钟内被你击杀的前 4 名玩家掉落背包位；只有你被敌方玩家击杀时，自己才掉落并结束效果。`DoomsdayMixin` 里受害者掉落也要求 `enemyKill`。击杀队友不掉背包，也不计入 4 次。两边都没有队伍时 `isSameTeam` 返回 true，同样不触发。

`beginDoomsday` 把结束时间存成绝对 `server.tickCount`。逆转时间不回退服务器 tick，只把 memory 原样装回去，回档区间会吃掉剩余时间。

### 16. SMP玩家 `hunter_headhunter`

描述是每击杀一名敌方玩家，永久 +2 生命上限，没有写上限。`MAX_BONUS` 把加成卡在 +20。达到 20 之后不再加属性，也不再回血。

### 17. 烈焰喷射 `summon_fireball`

描述是可以像恶魂一样发射火球。`FireballService` 只注册了 `UseItemCallback`。对着方块右键走 `FireChargeItem.useOn`，会点火并消耗火焰弹，不生成火球。

### 18. 这不是挂？ `hunter_homing_bow`

描述是锁定发射瞬间、准心方向 64 格内夹角最小的敌人，可隔墙，追踪时穿方块。`HomingArrowMixin` 的 `MIN_AIM_DOT` 是 30°，准心 30° 外即使只有一个敌人也不会锁定。候选是碰撞箱外扩 64 格里的活体，牛、村民、盔甲架都能抢走目标。外扩是立方体，斜角上可以超过 64 格。

### 19. 结束吧 `summon_end_portal`

从脚上方 2 格起向上找空气，`pos` 的初值就是起点。这一段全不是空气时仍 `setBlockAndUpdate`，原方块不掉落。超出世界高度时 `getBlockState` 得到虚空空气，`isAir` 为真；`setBlock` 对越界返回 false，代码不看返回值，仍然向全服公告该坐标。`SummonPerks.kt` 的 `EndPortal`。

### 20. 驯龙高手 `summon_dragon_tamer`

描述是右键任意生物即可骑乘，只排除玩家和 Boss。`DragonTamerService.tryMount` 对没有鞍的 `AbstractHorse`、猪和炽足兽直接返回 false。`isForbidden` 只拦截末影龙、凋灵和监守者。同一语言文件里 Boss盲盒把远古守卫者列为 Boss，远古守卫者可以骑，右键还会放出光束。

### 21. 哈基米 `harvest_cat_aura`

描述是苦力怕会害怕持有该词条的玩家。`CatAuraMixin` 在找不到真猫时只取 `getNearestPlayer(mob, maxDist)`，距离是 6 格。这名最近玩家没有词条时直接返回，更远但仍在 6 格内的持有者不会成为逃跑目标。

### 22. 乐魂 `summon_happy_ghast`

描述是永久速度 I。代码挂的是 `MobEffects.SPEED`。26.3 里速度效果只给 `MOVEMENT_SPEED` 增加 20%（`StatusEffects.java`）。快乐恶魂的 `travel` 和骑乘都只读 `FLYING_SPEED`。自主飞行和被骑乘的速度都不变。效果实例还把可见性设为 false。

### 23. 千锤百炼 `artisan_tempering`

损坏铁砧的标记只在 `setPlacedBy` 时写入内存中的 `GlobalPos`，挖掉时才经 `getDrops` 把标记转回物品。26.3 的铁砧继承 `FallingBlock`。下落用 `setBlockAndUpdate` 换掉原位置，不走 `getDrops`，也不走 `setPlacedBy`。旧坐标上的标记留在空气上，落地后的铁砧没有标记，之后按原版损坏铁砧的概率碎裂。`TemperingAnvilBlockMixin.java`。

### 24. 矿石判断被名称子串放大

`PerkBoostQuery.isOreBlock` 和 `DelverPerks.isOreBlock` 在原版矿石标签之外用 `path.contains("ore")`。`heavy_core` 和 `spore_blossom` 的路径都含有 `ore`，但它们不是矿石。

挖沉重核心或孢子花会被贪吃蛇记一层。矿脉嗅觉、老矿工的额外份、矿工手腕的 1.25 倍也会套上。透视之眼用同一条规则描边。连锁挖矿用手持镐挖孢子花时，会把相连的孢子花一并挖掉。

### 25. 连锁挖矿 `delver_chain_mining`

描述是用镐挖掘矿石时，把相连的同种矿石一并挖掘。连锁块走 `Level.destroyBlock`。Fabric 的 `PlayerBlockBreakEvents.AFTER` 只挂在 `ServerPlayerGameMode.destroyBlock` 上，这些方块不再进入 `onBlockBreak`。

同时持有贪吃蛇时，只有亲手敲掉的那一块加层。矿脉嗅觉和老矿工的额外掉落在破坏流程开头掷一次，整条矿脉共用，不是逐块概率。透视之眼的 35% 消失和幸运矿工也只结算主方块。`MAX_CHAIN` 为 64，描述没有上限。比较用的是方块实例相等，`coal_ore` 与 `deepslate_coal_ore` 不算同一种，矿脉在深板岩交界处断开。

### 26. 老矿工 `delver_collector`

描述是挖掘等级 +1。`OldMinerMixin.heldPickaxeTier` 把空手和任何非镐物品的等级当成 0，再 +1 后当作木镐。空手、剑、斧都能收获只需木镐的方块。

### 27. 流浪商人 `summon_wandering_trader`

只在脚下是空气且下方是完整碰撞箱时刷在玩家位置。否则 `nearbyStandable` 在水平 ±16 格取 `MOTION_BLOCKING_NO_LEAVES` 高度图。半砖、楼梯、土径、耕地、灵魂沙、地毯或水里都会失败。人在洞穴里时，商人和羊驼刷到地表，而不是玩家身边。`SeekerPerks.kt` 约 389 行。

### 28. 何易位 `summon_swap`

倒计时只保存在 `SwapService.pending` 里。结算时检查双方在线且存活，不检查施法者是否还拥有 `summon_swap`。对局结束会走 `BingoLifecycle.resetAll` 里的 `SwapService.clear`。命令移除词条只调用 `onRemove`，这条词条没有 `onRemove`，倒计时会继续走完并传送。

### 29. 创造闪光 `mythic_creative_flash`

获得时切到创造模式 30 tick（1.5 秒）。断线会立刻还原。词条没有 `onRemove`。对局结束或命令移除时不会取消 `CreativeFlashService.active`。计时结束时 `restore` 会把游戏模式写回闪光前的模式；若此时已经被改成旁观或其他模式，会被盖掉。

## 计时和持续状态

### 30. 补给线、末影补给、末影仓库、药剂师

`seeker_supply_line`、`seeker_ender_supply`、`seeker_ender_cache`、`seeker_alchemist` 把下次发放时间写成绝对 `server.tickCount`，并随词条状态存档。`tickCount` 不进世界存档，新进程从 0 计。重启后当前 tick 远小于存下来的截止 tick，`onTick` 会一直认为冷却未到。对局若已进行几十分钟，下一份补给会再等那么久。

### 31. 灵魂出窍 `delver_soul_out`

`onRespawn` 无条件调用 `SoulOutService.end`。死亡后结束是合理的。读档重挂和回溯也走 `onRespawn`（`PerkStatePersistence.kt`、`RewindService.kt`），不会再次 `onAcquire`。中途重启或回溯到仍在出窍的快照时，词条已经 `markUsed`，剩余时间被清掉。截止时间本身是绝对 `tickCount`，新进程从 0 计。

描述要求期间身体无法移动、攻击或使用物品。服务端激活时不清除速度、不取消鞘翅、不锁定位置。已经在滑翔的身体会继续沿当前速度飞。`SoulOutService.kt`。

### 32. 开摆 `seeker_lay_flat`

五分钟到期和中途死亡的石斧标记只存在 `LayFlatService` 的内存集合里。到期时玩家离线就不再发放自然罗盘和结构罗盘。重启后两个集合都空，失明和缓慢 IV 写在玩家实体上，会留下来，罗盘和石斧都不会补。对局结束时 `LayFlatService.clear` 只清 map，词条没有 `onRemove`，这两个效果不会被摘掉。

### 33. !?区区!? `seeker_puny`

矿物在 `VerminSwarmService.start` 之前已经给到，词条也已经 `markUsed`。蠹虫计时只在内存里。目标掉线时当前 tick 就 `remove`，重新上线不恢复。重启后 map 为空，剩余时间里的蠹虫不再生成。

### 34. 马来 `summon_horse`

召唤的马带有 `randomperks_summoned_horse`，并调用了 `setPersistenceRequired`。传送逻辑只检查标签、主人和距离，不检查玩家是否仍持有 `summon_horse`。`BingoLifecycle.resetAll` 不会移除这些马。词条被移除或对局结束后，马仍会在距离超过 24 格或主人换维度时传送过去。`SummonedHorseService.kt`。
