# Random Perks Bingo（随机词条 Bingo 扩展）

给 Minecraft Fabric mod [Yet Another Bingo](https://gitlab.com/horrificdev/bingo) 的扩展：
团队完成 bingo 格子时，每位队员获得一次「三选一」词条选择机会；词条分蓝/紫/金三档品质，
按六种流派（疾风/深掘/丰饶/猎杀/秘藏/匠造）设计，同流派词条之间有配合，构筑感强。

## 目标环境

| 项目 | 版本 |
|---|---|
| Minecraft | 1.21.6 / 1.21.7 / 1.21.8（编译于 1.21.7） |
| Yet Another Bingo | 2.9.6（1.21.8 分支最终版） |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.128.2+1.21.7 |
| Kotlin / FLK | 2.2.21 / 1.13.7+kotlin.2.2.21 |
| Java | 21 |

## 功能开关

词条系统可以整体启用/禁用（不想用扩展时无需卸载）：

- **命令**（OP）：`/randomperks enable` / `/randomperks disable`
  - 禁用时会**清空所有玩家当前的词条与效果**，且不再发放新的选择机会
- **配置文件**：`config/random-perks-bingo.json`（`{"enabled": true}`）
  - 服务器启动时读取；命令切换会实时写回该文件

## 构建

```bash
./gradlew build          # 产物在 build/libs/random-perks-bingo-<version>.jar
./gradlew runServer      # 开发服务器（自动加载 bingo 2.9.6）
./gradlew runClient      # 开发客户端
```

**国内网络**：首次构建需要代理拉取 Gradle 发行版与依赖。已在
`~/.gradle/gradle.properties` 中配置（127.0.0.1:7890）。若代理端口不同，请修改该文件。

## 技术结构

- **检测层**（仅有的 bingo 内部耦合）：`src/main/java/com/randomperks/mixin/`
  - Mixin 注入 bingo 的 `ScoreUpdateService.sendItemCaptured`（每个格子被完成的语义正确触发点）
  - `BingoHook` 把 bingo 类型转换为纯数据（UUID/String）后交给 Kotlin 侧
  - ⚠ 这些类必须用 **Java** 写（bingo 相关类型是 Kotlin `internal`，Kotlin 编译器禁止跨模块访问）
- **游戏逻辑**：`com.randomperks.*`（Kotlin）
- **客户端**：`src/client/java/com/randomperks/client/`

## 里程碑

- [x] **M1** 工程骨架 + mixin 注入验证
  - 开发服务器启动即打印 `[RandomPerks] mixin applied to bingo ScoreUpdateService — scoring hook is ACTIVE`
- [x] **M2** 最小闭环（完成格子 → 发放机会 → 三选一 → 效果生效）— 已通过自动化端到端测试
- [x] **M3** 三选一 UI（品质色卡牌/图标/流派标签/悬停）+ 词条列表界面（按键 C）
- [x] **M4** 词条系统完整化（品质概率/去重池/生命周期/tick 驱动/重生重挂/单元测试）
- [x] **M5**（第一批）27 个词条，覆盖 6 流派（金色词条暂 1 个）
- [ ] **M6** 打磨与平衡（主动技能系统、信息类 HUD、更多金词条）

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

调试命令：`/randomperks status | enable | disable | grant [n] | simulate | pick <0-2> | perk add/remove/clear <玩家> [词条id]`（需要 OP 权限）。

### 已知的测试技巧

- 假玩家不发送移动包，bingo 的 `WaitUntilLoadedController` 会卡在 LOADING 状态；
  `e2e-test.ps1` 通过"开局后暂时让假玩家离队"让该检查通过（须在 40 秒内完成）。
- RCON 以控制台身份执行命令，`/randomperks pick` 这类需要玩家的命令要用
  `/execute as <player> run ...` 指定玩家。
