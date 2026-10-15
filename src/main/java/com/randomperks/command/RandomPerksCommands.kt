package com.randomperks.command

import com.randomperks.config.PerkConfig
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkOfferService
import com.randomperks.game.PerkSession
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PerkStateManager
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * 开发者/调试命令: /randomperks
 *
 * - status   查看词条注册与玩家状态概况
 * - grant [n]  给自己发放 n 次选择机会 (默认 1)
 * - simulate   模拟"本队完成一个格子"的完整触发链路
 */
object RandomPerksCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("randomperks")
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        literal("status").executes { ctx ->
                            val states = PerkStateManager.all()
                            val owned = states.sumOf { it.ownedPerks.size }
                            val pending = states.sumOf { it.pendingChoices }
                            ctx.source.sendFeedback({
                                Text.literal(
                                    "[RandomPerks] 本局=${if (PerkSession.isEnabled()) "启用" else "禁用"}, " +
                                        "下局=${if (PerkConfig.enabled) "启用" else "禁用"}, " +
                                        "perks=${PerkRegistry.size()}, players=${states.size}, owned=$owned, pending=$pending"
                                )
                            }, false)
                            states.forEach { s ->
                                ctx.source.sendFeedback({
                                    Text.literal(
                                        "  ${s.uuid}: owned=${s.ownedPerks.joinToString { it.id }}, " +
                                            "pending=${s.pendingChoices}, current=${s.currentOffer()}"
                                    )
                                }, false)
                            }
                            1
                        }
                    )
                    .then(
                        literal("enable").executes { ctx ->
                            PerkConfig.set(true)
                            ctx.source.sendFeedback({
                                Text.literal("[RandomPerks] 词条系统已启用 —— 将于【下一局】生效 (当前对局不受影响)")
                            }, true)
                            1
                        }
                    )
                    .then(
                        literal("disable").executes { ctx ->
                            PerkConfig.set(false)
                            ctx.source.sendFeedback({
                                Text.literal("[RandomPerks] 词条系统已禁用 —— 将于【下一局】生效 (当前对局继续运行)")
                            }, true)
                            1
                        }
                    )
                    .then(
                        literal("grant").executes { ctx -> grantSelf(ctx, 1) }
                            .then(
                                net.minecraft.server.command.CommandManager.argument(
                                    "count",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20),
                                ).executes { ctx ->
                                    grantSelf(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))
                                }
                            )
                    )
                    .then(
                        literal("simulate").executes { ctx ->
                            val player = ctx.source.player ?: return@executes 0
                            // 模拟"该玩家所在队伍完成了一个格子"的完整触发链路
                            PerkGrantService.onTeamScored(listOf(player.uuid), "test:simulated_objective")
                            1
                        }
                    )
                    .then(
                        literal("pick").then(
                            net.minecraft.server.command.CommandManager.argument(
                                "index",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2),
                            ).executes { ctx ->
                                val player = ctx.source.player ?: return@executes 0
                                val index = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "index")
                                // 服务端直接执行"选择" (跳过客户端, 用于自动化测试)
                                val state = PerkStateManager.getOrCreate(player.uuid)
                                ctx.source.sendFeedback({
                                    Text.literal("[RandomPerks] current offer: ${state.currentOffer()}")
                                }, false)
                                PerkOfferService.handlePick(player, index)
                                1
                            }
                        )
                    )
                    .then(
                        literal("perk")
                            .then(
                                literal("list").executes { ctx ->
                                    ctx.source.sendFeedback({
                                        Text.literal("[RandomPerks] ${PerkRegistry.size()} 个词条:")
                                    }, false)
                                    PerkRegistry.all.forEach { perk ->
                                        ctx.source.sendFeedback({
                                            Text.literal("  ${perk.id} (${perk.rarity} · ${perk.archetype.id})")
                                        }, false)
                                    }
                                    1
                                }
                            )
                            .then(
                                literal("add")
                                    .then(
                                        argument("player", EntityArgumentType.player())
                                            .then(
                                                argument("perkId", StringArgumentType.string())
                                                    .suggests(perkSuggestions)
                                                    .executes { ctx -> addPerk(ctx) }
                                            )
                                    )
                            )
                            .then(
                                literal("remove")
                                    .then(
                                        argument("player", EntityArgumentType.player())
                                            .then(
                                                argument("perkId", StringArgumentType.string())
                                                    .suggests(perkSuggestions)
                                                    .executes { ctx -> removePerk(ctx) }
                                            )
                                    )
                            )
                            .then(
                                literal("clear")
                                    .then(
                                        argument("player", EntityArgumentType.player())
                                            .executes { ctx -> clearPerks(ctx) }
                                    )
                            )
                    )
            )
        }
    }

    private fun grantSelf(ctx: CommandContext<ServerCommandSource>, count: Int): Int {
        val player = ctx.source.player ?: return 0
        PerkGrantService.grantChoice(player, count)
        return count
    }

    // ---- 词条编辑 (调试) ----

    private val perkSuggestions: SuggestionProvider<ServerCommandSource> =
        SuggestionProvider { _, builder ->
            PerkRegistry.all.forEach { perk ->
                builder.suggest(perk.id, Text.literal("${perk.rarity} · ${perk.archetype.id}"))
            }
            builder.buildFuture()
        }

    private fun addPerk(ctx: CommandContext<ServerCommandSource>): Int {
        val target = EntityArgumentType.getPlayer(ctx, "player")
        val perkId = StringArgumentType.getString(ctx, "perkId")
        val perk = PerkRegistry.byId(perkId) ?: run {
            ctx.source.sendError(Text.literal("未知词条: $perkId (可用 /randomperks perk list 查看)"))
            return 0
        }

        val state = PerkStateManager.getOrCreate(target.uuid)
        if (state.hasPerk(perk.id)) {
            ctx.source.sendError(Text.literal("${target.name.string} 已拥有词条 ${perk.id}"))
            return 0
        }

        state.ownedPerks.add(perk)
        runCatching { perk.onAcquire(PerkContext(target, state)) }
            .onFailure { ctx.source.sendError(Text.literal("应用效果时出错: ${it.message}")) }
        PerkGrantService.notifyPerkListChanged(target)
        PerkGrantService.syncAbilities(target)

        ctx.source.sendFeedback({
            Text.literal("已为 ${target.name.string} 添加词条: ${perk.id} (${perk.rarity})")
        }, true)
        return 1
    }

    private fun removePerk(ctx: CommandContext<ServerCommandSource>): Int {
        val target = EntityArgumentType.getPlayer(ctx, "player")
        val perkId = StringArgumentType.getString(ctx, "perkId")

        val state = PerkStateManager.getOrCreate(target.uuid)
        val perk = state.ownedPerks.find { it.id == perkId } ?: run {
            ctx.source.sendError(Text.literal("${target.name.string} 没有词条 $perkId"))
            return 0
        }

        state.ownedPerks.remove(perk)
        runCatching { perk.onRemove(PerkContext(target, state)) }
            .onFailure { ctx.source.sendError(Text.literal("清理效果时出错: ${it.message}")) }
        PerkGrantService.notifyPerkListChanged(target)
        PerkGrantService.syncAbilities(target)

        ctx.source.sendFeedback({
            Text.literal("已移除 ${target.name.string} 的词条: ${perk.id}")
        }, true)
        return 1
    }

    private fun clearPerks(ctx: CommandContext<ServerCommandSource>): Int {
        val target = EntityArgumentType.getPlayer(ctx, "player")
        val state = PerkStateManager.getOrCreate(target.uuid)

        val count = state.ownedPerks.size
        val perks = state.ownedPerks.toList()
        state.ownedPerks.clear()
        for (perk in perks) {
            runCatching { perk.onRemove(PerkContext(target, state)) }
        }
        PerkGrantService.syncAbilities(target)

        ctx.source.sendFeedback({
            Text.literal("已清空 ${target.name.string} 的 $count 个词条")
        }, true)
        return count
    }
}
