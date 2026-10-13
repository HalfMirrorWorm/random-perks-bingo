package com.roguebingo.command

import com.roguebingo.game.PerkGrantService
import com.roguebingo.game.PerkOfferService
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.state.PerkStateManager
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * 开发者/调试命令: /roguebingo
 *
 * - status   查看词条注册与玩家状态概况
 * - grant [n]  给自己发放 n 次选择机会 (默认 1)
 * - simulate   模拟"本队完成一个格子"的完整触发链路
 */
object RogueBingoCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("roguebingo")
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        literal("status").executes { ctx ->
                            val states = PerkStateManager.all()
                            val owned = states.sumOf { it.ownedPerks.size }
                            val pending = states.sumOf { it.pendingChoices }
                            ctx.source.sendFeedback({
                                Text.literal(
                                    "[RogueBingo] perks=${PerkRegistry.size()}, players=${states.size}, " +
                                        "owned=$owned, pending=$pending"
                                )
                            }, false)
                            states.forEach { s ->
                                ctx.source.sendFeedback({
                                    Text.literal("  ${s.uuid}: owned=${s.ownedPerks.joinToString { it.id }}, pending=${s.pendingChoices}")
                                }, false)
                            }
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
                                // 服务端直接执行"请求 + 选择" (跳过客户端, 用于自动化测试)
                                PerkOfferService.handleRequest(player)
                                val state = PerkStateManager.getOrCreate(player.uuid)
                                ctx.source.sendFeedback({
                                    Text.literal("[RogueBingo] rolled offer: ${state.pendingOffer}")
                                }, false)
                                PerkOfferService.handlePick(player, index)
                                1
                            }
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
}
