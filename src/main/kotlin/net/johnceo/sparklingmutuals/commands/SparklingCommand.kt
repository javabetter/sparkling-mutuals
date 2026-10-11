package net.johnceo.sparklingmutuals.commands

import net.johnceo.sparklingmutuals.LocalChat

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.johnceo.sparklingmutuals.contest.ContestGui
import net.johnceo.sparklingmutuals.config.SafariConfigScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.johnceo.sparklingmutuals.safari.CatchCountScreen
import net.johnceo.sparklingmutuals.safari.SafariFullClear
import net.johnceo.sparklingmutuals.safari.SafariMode
import net.johnceo.sparklingmutuals.safari.SafariEsp
import net.minecraft.network.chat.Component

object SparklingCommand {
    private fun mode(source: FabricClientCommandSource, selected: SafariMode): Int {
        SafariFullClear.select(selected)
        SafariFullClear.modeLines().forEach { source.sendFeedback(LocalChat.component(it)) }
        return 1
    }
    private fun open(screen: () -> Screen): Int {
        val client = Minecraft.getInstance()
        client.execute { client.setScreenAndShow(screen()) }
        return 1
    }

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("sm")
                    .executes { open(::SafariConfigScreen) }
                    .then(ClientCommands.literal("config").executes { open(::SafariConfigScreen) })
                    .then(ClientCommands.literal("gui").executes { open { ContestGui() } })
                    .then(ClientCommands.literal("history")
                        .then(ClientCommands.literal("import")
                            .then(ClientCommands.argument("folder", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes {
                                net.johnceo.sparklingmutuals.history.LogImporter.runImport(java.nio.file.Path.of(
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(it, "folder").replace("\"", ""))); 1 })
                            .executes { net.johnceo.sparklingmutuals.history.LogImporter.runImport(net.johnceo.sparklingmutuals.history.LogImporter.defaultLogs()); 1 })
                        .executes { open { net.johnceo.sparklingmutuals.history.HistoryScreen(null) } })
                    .then(ClientCommands.literal("achievements").executes {
                        open { net.johnceo.sparklingmutuals.history.HistoryScreen(null, net.johnceo.sparklingmutuals.history.HistoryScreen.Tab.ACHIEVEMENTS) } })
                    .then(ClientCommands.literal("stats").executes {
                        open { net.johnceo.sparklingmutuals.history.HistoryScreen(null, net.johnceo.sparklingmutuals.history.HistoryScreen.Tab.STATS) } })
                    .then(ClientCommands.literal("debug").executes { context ->
                        SafariEsp.debug(Minecraft.getInstance())
                        context.source.sendFeedback(LocalChat.component("[SM] Entity diagnostics saved to latest.log."))
                        1
                    })
            )
            dispatcher.register(ClientCommands.literal("captures").executes { open { CatchCountScreen() } })
            dispatcher.register(ClientCommands.literal("unique").executes { mode(it.source, SafariMode.UNIQUE) })
            dispatcher.register(ClientCommands.literal("sparkle").executes { mode(it.source, SafariMode.SPARKLING) })
            dispatcher.register(ClientCommands.literal("fc").executes { mode(it.source, SafariMode.FULL_CLEAR) })
        }
    }
}
