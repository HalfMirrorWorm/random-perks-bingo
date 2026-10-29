# 词条命名与名称映射

共 **128** 条词条：绿 19 / 蓝 55 / 紫 32 / 金 18 / 红 0 / 神话 4。

数据来源：`src/main/java/com/randomperks/perk/content/*.kt`（id / 稀有度 / 流派）+
`src/main/resources/assets/random-perks-bingo/lang/{zh_cn,en_us}.json`（名称）。
新增或改名词条时，请同步更新本表。

## 命名规则

- **词条 id**：`<流派>_<英文短名>`（如 `hunter_soul_harvest`）；神话单独使用 `mythic_` 前缀
- **语言 key**：`random-perks-bingo.perk.<id>.name` / `.desc`（中英各一份）
- **稀有度与抽取权重**：`green` 300 / `blue` 800 / `purple` 315 / `gold` 62 / `red` 20 / `mythic` 3
  （合计 1500：绿 20% / 蓝 53.3% / 紫 21% / 金 4.1% / 红 1.3% / 神话 0.2%）
- 绿色品质（粗糙）比普通更低级，已加入抽取体系
- 红色品质（禁忌）高于传说、低于神话，目前还没有词条使用
- 其他语言 key：`random-perks-bingo.rarity.<green|blue|purple|gold|red|mythic>`、
  `random-perks-bingo.message.*`（提示消息）、`random-perks-bingo.dimension.<overworld|nether|end>`、
  `random-perks-bingo.item.treasure_map`（藏宝图）

## 匠造 ARTISAN

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `artisan_supply` | 战地补给 | Field Supply | 蓝 |
| `artisan_toolkit` | 工具保养 | Toolkit | 蓝 |
| `artisan_builder` | 建筑师 | Builder | 绿 |
| `artisan_enchanter` | 附魔学徒 | Apprentice Enchanter | 蓝 |
| `artisan_quick_enchant` | 快速附魔 | Quick Enchant | 紫 |
| `artisan_waypoint_clock` | 传送时钟 | Waypoint Clock | 金 |
| `artisan_long_arms` | 手长 | Long Arms | 紫 |
| `artisan_fire_temper` | 火炼 | Smelting | 绿 |
| `artisan_poisoner` | 绝命毒师 | Breaking Bad | 紫 |
| `artisan_treasure_chest` | 百宝箱 | Treasure Chest | 紫 |
| `artisan_shulker_box` | 随身仓库 | Portable Storage | 绿 |
| `artisan_tempering` | 千锤百炼 | Tempering | 蓝 |
| `artisan_nya_axe` | 喵人斧 | Nya Axe | 金 |
| `artisan_midas_touch` | 点石成金 | Midas Touch | 蓝 |
| `artisan_revive_armor` | 复活甲 | Revive Armor | 金 |
| `artisan_barely_a_scratch` | 不痛不痒 | Barely a Scratch | 紫 |
| `artisan_turtle_master` | 神龟护体 | Turtle Master | 绿 |
| `artisan_trimmed_armor` | 纹饰护体 | Trimmed Armor | 紫 |
| `delver_toughness` | 坚韧 | Toughness | 蓝 |
| `delver_iron_skin` | 皇帝新衣 | Emperor's New Clothes | 蓝 |

## 深掘 DELVER

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `delver_mining` | 矿工手腕 | Miner's Wrist | 蓝 |
| `delver_night_vision` | 地下视野 | Underground Sight | 蓝 |
| `delver_haste` | 深层专注 | Deep Focus | 紫 |
| `delver_vein_scent` | 矿脉嗅觉 | Vein Scent | 紫 |
| `delver_xray_vision` | 透视之眼 | X-Ray Vision | 金 |
| `delver_lucky_digger` | 幸运矿工 | Lucky Digger | 蓝 |
| `delver_collector` | 老矿工 | Old Miner | 紫 |
| `delver_phoenix` | 不死鸟 | Phoenix | 紫 |
| `delver_onion_cut` | 切洋葱 | Cutting Onions | 绿 |
| `delver_eighty` | 八十 | Eighty | 蓝 |
| `delver_chain_mining` | 连锁挖矿 | Chain Mining | 紫 |
| `delver_carefree_sleep` | 无忧的安眠 | Carefree Sleep | 绿 |
| `delver_soul_out` | 灵魂出窍 | Out of Body | 金 |

## 丰饶 HARVEST

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `harvest_field_mender` | 田园修补 | Field Mender | 紫 |
| `harvest_gardener` | 园艺家 | Gardener | 蓝 |
| `harvest_lumberjack` | 伐木工 | Lumberjack | 蓝 |
| `harvest_herder` | 牧羊人 | Herder | 蓝 |
| `harvest_timber` | 樵夫之手 | Timber! | 绿 |
| `harvest_tree_feller` | 一键伐木 | One-Click Felling | 蓝 |
| `harvest_bounty` | 丰收之息 | Bounty | 蓝 |
| `harvest_bountiful_meal` | 丰盛大餐 | Bountiful Meal | 绿 |
| `harvest_gourmet` | 小鸟胃 | Eats Like a Bird | 蓝 |
| `harvest_cat_aura` | 哈基米 | Hakimi | 绿 |
| `harvest_big_stomach` | 大胃袋 | Bottomless Stomach | 蓝 |
| `harvest_yowl` | 老吴!? | Yowl!? | 绿 |
| `harvest_saplings` | 植岷 | Saplings | 绿 |
| `harvest_gleaning` | 拾穗 | Gleaning | 绿 |
| `harvest_dingdong_chicken` | 叮咚鸡 | Ding Dong Chicken | 绿 |
| `harvest_lucky_fishing` | 渔者之幸 | Fisher's Fortune | 绿 |
| `harvest_pig_morph` | 变群友 | Groupmate | 蓝 |
| `harvest_beekeeper` | 采蜜人 | Beekeeper | 蓝 |
| `harvest_kelp_to_bone` | 海带成骨 | Kelp to Bone | 蓝 |
| `harvest_bone_meal_flowers` | 骨粉撒花 | Bone Meal Bloom | 绿 |
| `harvest_fungal_infection` | 真菌感染 | Fungal Infection | 金 |
| `harvest_slow_chewing` | 细嚼慢咽 | Slow Chewing | 紫 |
| `harvest_well_fed` | 饱食终日 | Well-Fed | 紫 |

## 猎杀 HUNTER

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `hunter_strength` | 猎人直觉 | Hunter's Instinct | 蓝 |
| `hunter_vitality` | 强壮体魄 | Vitality | 蓝 |
| `hunter_vampire` | 战地急救 | Field Medic | 蓝 |
| `hunter_scavenger` | 剥皮专家 | Scavenger | 紫 |
| `hunter_soul_harvest` | 灵魂收割 | Soul Harvest | 紫 |
| `hunter_bare_rage` | 玻璃小炮 | Glass Cannon | 蓝 |
| `hunter_marked_prey` | 猎杀印记 | Hunter's Mark | 紫 |
| `hunter_greedy_hands` | 贪婪之手 | Greedy Hands | 紫 |
| `hunter_predator` | 我饥饿 | Me Hungry | 蓝 |
| `hunter_headhunter` | SMP玩家 | SMP Player | 紫 |
| `hunter_veteran` | 老玩家 | Veteran | 金 |
| `hunter_roly_poly` | 不倒翁 | Roly-Poly | 绿 |
| `hunter_homing_bow` | 这不是挂？ | Is This a Hack? | 紫 |
| `hunter_jockey` | Jockey | Jockey | 金 |
| `hunter_demon_pact` | 恶魔契约 | Demon Pact | 金 |
| `hunter_pearl_gauntlet` | 珠光护手 | Pearl Gauntlet | 蓝 |
| `hunter_satiation_strike` | 良子冲击 | Satiation Strike | 紫 |
| `hunter_munitions` | 军火补给 | Munitions Supply | 蓝 |
| `hunter_bounty_hunter` | 赏金猎人 | Bounty Hunter | 蓝 |
| `hunter_hedgehog` | 刺猬 | Hedgehog | 金 |
| `hunter_spider_weaving` | 盘丝大仙 | Web Spinner | 蓝 |

## 秘藏 SEEKER

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `seeker_scout` | 观鸟 | Birdwatcher | 蓝 |
| `seeker_supply_line` | 补给线 | Supply Line | 蓝 |
| `seeker_ender_supply` | 末影补给 | Ender Supply | 紫 |
| `seeker_pickpocket` | 顺手牵羊 | Pickpocket | 金 |
| `seeker_life_vision` | 生命透视 | Life Vision | 金 |
| `seeker_ender_cache` | 末影仓库 | Ender Cache | 紫 |
| `seeker_alchemist` | 药剂师 | Alchemist | 紫 |
| `seeker_village_compass` | 回村的诱惑 | Homecoming | 蓝 |
| `seeker_puny` | !?区区!? | Puny!? | 蓝 |
| `seeker_random_ground` | 随机地基 | Random Ground | 蓝 |
| `seeker_lay_flat` | 开摆 | Lay Flat | 紫 |
| `seeker_magnet_upgrade` | 磁力升级 | Magnet Upgrade | 绿 |
| `seeker_old_priest` | 老牧师 | Old Priest | 蓝 |
| `seeker_warm_archaeology` | 暖水考古 | Warm Water Archaeology | 蓝 |
| `seeker_village_hero` | 村庄英雄 | Hero of the Village | 蓝 |
| `seeker_savage_magic` | 野人魔了 | Savage Magic | 紫 |

## 迅捷 SWIFT

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `swift_speed` | 轻装疾行 | Light Feet | 蓝 |
| `swift_feather_fall` | 柔软鞋底 | Soft Soles | 蓝 |
| `swift_jump` | 弹簧腿 | Spring Legs | 蓝 |
| `swift_wind_stride` | 风之步伐 | Wind Stride | 紫 |
| `swift_tailwind` | 顺风而行 | Tailwind | 紫 |
| `swift_double_jump` | 二段跳 | Double Jump | 紫 |
| `swift_elytra_bond` | 风之翼 | Wings of the Wind | 金 |
| `swift_life_bucket` | 落地，水 | MLG Water | 绿 |
| `swift_gura_shark` | 古拉鲨鱼 | Gura Shark | 金 |
| `swift_omnimovement` | 全向移动 | Omnimovement | 蓝 |
| `swift_escape_plan` | 逃跑计划 | Escape Plan | 蓝 |
| `swift_deep_breath` | 深海之息 | Deep Breath | 蓝 |
| `swift_dash` | 冲刺 | Dash | 金 |

## 唤法 SUMMON

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `summon_dragon_tamer` | 驯龙高手 | Dragon Tamer | 金 |
| `summon_pokemon` | 宝可梦 | Pokemon | 紫 |
| `summon_fireball` | 烈焰喷射 | Ghast Fireball | 蓝 |
| `summon_tnt_throw` | TNT 投手 | TNT Thrower | 蓝 |
| `summon_horse` | 马来 | Horse, Come | 蓝 |
| `summon_happy_ghast` | 乐魂 | Happy Ghast | 蓝 |
| `summon_big_dog` | 大狗嚼 | Big Doggo | 蓝 |
| `summon_wandering_trader` | 流浪拴绳 | Wandering Leash | 蓝 |
| `summon_rancher` | 牧场主 | Rancher | 蓝 |
| `summon_call_lightning` | 引雷术 | Call Lightning | 蓝 |
| `summon_big_snowball` | 打雪仗 | Snowball Fight | 绿 |
| `summon_remote_transfer` | 远程传输 | Remote Transfer | 蓝 |
| `summon_swap` | 何易位 | Swap | 紫 |
| `summon_boss_egg` | Boss盲盒 | Boss Blind Box | 紫 |
| `summon_end_portal` | 结束吧 | End It | 金 |
| `summon_loyal_guard` | 忠心护主 | Loyal Guardian | 金 |
| `summon_insomnia` | 失眠 | Insomnia | 蓝 |
| `hunter_easy_nuke` | 核电轻而易举 | Easy Nuke | 蓝 |

## 神话 MYTHIC（全局唯一）

| id | 中文名 | 英文名 | 稀有度 |
|---|---|---|---|
| `mythic_creative_flash` | 创造闪光 | Creative Flash | 神话 |
| `mythic_doomsday` | 天启日 | Doomsday | 神话 |
| `mythic_hodgepodge` | 游戏BUG | Glitch | 神话 |
| `mythic_time_rewind` | 逆转时间 | Time Rewind | 神话 |

## 注意：id 与中文名不直译的词条

id 是机制名，中文名是风味名，排查问题时留意对应关系：

| id | 中文名 |
|---|---|
| `delver_iron_skin` | 皇帝新衣 |
| `harvest_gourmet` | 小鸟胃 |
| `hunter_vampire` | 战地急救 |
| `hunter_predator` | 我饥饿 |
| `hunter_headhunter` | SMP玩家 |
| `seeker_village_compass` | 回村的诱惑 |
| `swift_life_bucket` | 落地，水 |
| `harvest_yowl` | 老吴!?（猫叫拟声） |
