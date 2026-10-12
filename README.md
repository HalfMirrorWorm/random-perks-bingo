# Rogue Bingo（肉鸽 Bingo 扩展）

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

## 构建

```bash
./gradlew build          # 产物在 build/libs/rogue-bingo-<version>.jar
./gradlew runServer      # 开发服务器（自动加载 bingo 2.9.6）
./gradlew runClient      # 开发客户端
```

**国内网络**：首次构建需要代理拉取 Gradle 发行版与依赖。已在
`~/.gradle/gradle.properties` 中配置（127.0.0.1:7890）。若代理端口不同，请修改该文件。

## 技术结构

- **检测层**（仅有的 bingo 内部耦合）：`src/main/java/com/roguebingo/mixin/`
  - Mixin 注入 bingo 的 `ScoreUpdateService.sendItemCaptured`（每个格子被完成的语义正确触发点）
  - `BingoHook` 把 bingo 类型转换为纯数据（UUID/String）后交给 Kotlin 侧
  - ⚠ 这些类必须用 **Java** 写（bingo 相关类型是 Kotlin `internal`，Kotlin 编译器禁止跨模块访问）
- **游戏逻辑**：`com.roguebingo.*`（Kotlin）
- **客户端**：`src/client/java/com/roguebingo/client/`

## 里程碑

- [x] **M1** 工程骨架 + mixin 注入验证
  - 开发服务器启动即打印 `[RogueBingo] mixin applied to bingo ScoreUpdateService — scoring hook is ACTIVE`
- [x] **M2** 最小闭环代码（完成格子 → 发放机会 → 三选一 → 效果生效）— 待游戏内实测
- [ ] **M3** 正式三选一 UI（品质色卡牌/图标/描述排版）
- [ ] **M4** 词条系统完整化（品质概率/去重池/生命周期）
- [ ] **M5** 36 个词条内容填充
- [ ] **M6** 打磨与平衡

## 开发环境注意事项

- bingo 的 Jar-in-Jar 依赖（HikariCP / sqlite-jdbc / sqldelight）在开发环境不会被 Loom 自动加载，
  已在 `build.gradle` 用 `localRuntime` 补齐（仅开发用，不会打进本 mod）。
- 首次构建后可直接 `./gradlew runServer` 或 `./gradlew runClient`（会自动带上 bingo 2.9.6）。
