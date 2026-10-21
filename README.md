# Random Perks Bingo（随机词条 Bingo 扩展）

给 Minecraft Fabric mod [Yet Another Bingo](https://gitlab.com/horrificdev/bingo) 的扩展：
团队完成 bingo 格子时，每位队员获得一次「三选一」词条选择机会；共 **101 个词条**，
分蓝/紫/金/神话四档品质，按六种流派（疾风/深掘/丰饶/猎杀/秘藏/匠造）设计，
同流派词条之间有配合，构筑感强。

## 目标环境

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.6 / 1.21.7 / 1.21.8（编译于 1.21.7） |
| Yet Another Bingo | 2.9.6（1.21.8 分支最终版） |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.128.2+1.21.7 |
| Kotlin / FLK | 2.2.21 / 1.13.7+kotlin.2.2.21 |
| Java | 21 |

## 玩法与按键

- 团队完成一个 bingo 格子 → **每位队员**获得一次「三选一」词条机会（每人独立随机、互不影响）
- 每局开局每人免费获得 1 次选择机会
- 共 101 个词条，分四档品质（普通 / 稀有 / 传说 / 神话），按六种流派设计（疾风 / 深掘 / 丰饶 / 猎杀 / 秘藏 / 匠造），同流派词条之间有配合

| 按键 | 功能 |
|---|---|
| `X` | 打开词条选择（有待选机会时） |
| `V` | 查看已获得的词条 |
| `K` | 词条百科（全部词条 + 收集进度，未获得的显示为暗色） |

按键可在「选项 → 按键控制 → 随机词条 Bingo」中自定义。

## 指令（需要 OP 权限）

```mcfunction
/randomperks status                        # 系统状态: 本局/下局开关, 词条总数, 各玩家词条与待选
/randomperks enable                        # 启用词条系统 (下一局生效)
/randomperks disable                       # 禁用词条系统 (下一局生效, 当前对局继续运行)
/randomperks grant [次数]                   # 给自己发放选择机会
/randomperks perk list                     # 列出全部词条 id
/randomperks perk add <玩家> <词条id>       # 给玩家添加词条 (效果即时生效, 词条id 可 Tab 补全)
/randomperks perk remove <玩家> <词条id>    # 移除词条 (附带效果会被清理)
/randomperks perk clear <玩家>              # 清空玩家全部词条
```

词条系统的开关是**每局生效**的：对局开始时读取配置快照，局中切换只影响**下一局**。
开关持久化到配置文件 `config/random-perks-bingo.json`（`{"enabled": true}`），
`enable`/`disable` 命令切换时会实时写回该文件。

## 构建

```bash
./gradlew build          # 产物在 build/libs/random-perks-bingo-<version>.jar
./gradlew runServer      # 开发服务器（自动加载 bingo 2.9.6）
./gradlew runClient      # 开发客户端
```

## 技术结构

- **检测层**（仅有的 bingo 内部耦合）：
  - `src/main/java/com/randomperks/mixin/`：Mixin 注入 bingo 的 `ScoreUpdateService.sendItemCaptured`（每个格子被完成的语义正确触发点）
  - `src/main/java/com/randomperks/hook/BingoHook.java`：把 bingo 类型转换为纯数据（UUID/String）后交给 Kotlin 侧
  - ⚠ 这些类必须用 **Java** 写（bingo 相关类型是 Kotlin `internal`，Kotlin 编译器禁止跨模块访问）
- **词条定义**：`src/main/java/com/randomperks/perk/content/*.kt`（id / 稀有度 / 流派 / 效果），
  名称与描述在 `src/main/resources/assets/random-perks-bingo/lang/{zh_cn,en_us}.json`；
  完整 id 对照表见 [`docs/perk-names.md`](docs/perk-names.md)
- **游戏逻辑**：`com.randomperks.*`（Kotlin）
- **客户端**：`src/client/java/com/randomperks/client/`

## 里程碑

- [x] **M1** 工程骨架 + mixin 注入验证
  - 开发服务器启动即打印 `[RandomPerks] mixin applied to bingo ScoreUpdateService — scoring hook is ACTIVE`
- [x] **M2** 最小闭环（完成格子 → 发放机会 → 三选一 → 效果生效）— 已通过自动化端到端测试
- [x] **M3** 三选一 UI（品质色卡牌/图标/流派标签/悬停）+ 词条列表界面（按键 V）
- [x] **M4** 词条系统完整化（品质概率/去重池/生命周期/tick 驱动/重生重挂/单元测试）
- [x] **M5**（第一批）27 个词条，覆盖 6 流派（金色词条暂 1 个）
- [x] **M6** 词条扩充至 **101 个**：4 档品质（蓝/紫/金/神话）、6 大流派，含主动技能、召唤物与全局唯一的神话词条
- [ ] **M7** 打磨与平衡（信息类 HUD、数值平衡）

## 开发环境注意事项

- bingo 的 Jar-in-Jar 依赖（HikariCP / sqlite-jdbc / sqldelight）在开发环境不会被 Loom 自动加载，
  已在 `build.gradle` 用 `localRuntime` 补齐（仅开发用，不会打进本 mod）。
- 首次构建后可直接 `./gradlew runServer` 或 `./gradlew runClient`（会自动带上 bingo 2.9.6）。
- 开发环境附带 **Carpet**（假玩家，仅开发用）。注意 bingo 与 Carpet 在 `HungerManager` 上有 mixin
  冲突（Carpet 优先，bingo 的 setExhaustion 被跳过），仅影响开发环境，正式游玩不要同时装 Carpet。

## 自动化端到端测试

无需真人即可验证完整链路（完成格子 → 发机会 → 三选一 → 效果生效）：

```powershell
# 1. 启动开发服务器（首次需在 run/server.properties 启用 RCON:
#    enable-rcon=true, rcon.password=randomperks_test）
./gradlew runServer

# 2. 另开终端运行测试脚本（用 Carpet 假玩家 + RCON 驱动）
./scripts/e2e-test.ps1
```

脚本会：生成假玩家 → 创建队伍 → 开局（验证开局送 1 次机会）→ 用物品触发格子完成
（验证 Mixin hook 与机会发放）→ 执行三选一（验证抽取/选择/效果）。

> 注：`scripts/` 仅本地保留，未随仓库发布。

调试命令：`/randomperks status | enable | disable | grant [n] | simulate | pick <0-2> | perk add/remove/clear <玩家> [词条id]`（需要 OP 权限）。

### 已知的测试技巧

- 假玩家不发送移动包，bingo 的 `WaitUntilLoadedController` 会卡在 LOADING 状态；
  `e2e-test.ps1` 通过"开局后暂时让假玩家离队"让该检查通过（须在 40 秒内完成）。
- RCON 以控制台身份执行命令，`/randomperks pick` 这类需要玩家的命令要用
  `/execute as <player> run ...` 指定玩家。

## 开源协议

本项目与 [Yet Another Bingo](https://gitlab.com/horrificdev/bingo) 保持一致，使用
[GNU Lesser General Public License v3.0](LICENSE)（`LGPL-3.0-only`）。
