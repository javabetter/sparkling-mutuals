package net.johnceo.sparklingmutuals

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.johnceo.sparklingmutuals.alerts.AlertManager
import net.johnceo.sparklingmutuals.commands.*
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.config.ContestConfig
import net.johnceo.sparklingmutuals.contest.ContestHud
import net.johnceo.sparklingmutuals.contest.ContestTracker
import net.johnceo.sparklingmutuals.party.PartyManager
import net.johnceo.sparklingmutuals.hud.SmallAlerts
import net.johnceo.sparklingmutuals.safari.*
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object SparklingMutuals : ModInitializer {

	const val MOD_ID = "sparkling-mutuals"

	private val logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		logger.info("Sparkling Mutuals loaded!")

		val configDirectory = FabricLoader.getInstance().configDir

		ConfigManager.init(configDirectory)
		ContestConfig.init(configDirectory)

        PartyCommands.register()
		ApiKeyCommand.register()
		SparklingCommand.register()
		AlertCommand.register()

		PartyManager.init()
		AlertManager.onInitialize()
        HideyhoQuest.register()
        SafariTracking.register()
        BeeNests.register()
        net.johnceo.sparklingmutuals.contest.HoneyHives.register()
        SafariEsp.register()
        TeammateEsp.register()
        ClientTickEvents.START_CLIENT_TICK.register(AutoClicker::tick)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            PartyManager.onClientTick(client)
            PartyCommands.onClientTick(client)
            ContestTracker.onClientTick(client)
            net.johnceo.sparklingmutuals.contest.HoneyHives.tick(client)
            SafariAssist.onClientTick(client)
            SafariTracking.onClientTick(client)
            SafariSparklingMode.tick(client)
            BeeNests.tick(client)
            SafariStructures.tick(client)
            SafariEsp.tick(client)
            SafariBiomeClearTracking.tick(client)
            SafariInventoryAlerts.tick(client)
            HideyhoQuest.onClientTick(client)
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            PartyCommands.reset()
            PartyManager.reset()
            SafariSparklingMode.reset()
            AlertManager.cancelPendingAlert()
            SafariAssist.reset()
            HideyhoQuest.reset()
            SafariTracking.finish()
            SafariEsp.reset()
            AutoClicker.reset()
        }

		HudElementRegistry.attachElementBefore(
			VanillaHudElements.CHAT,
			id("contest_hud")
		) { graphics, deltaTracker ->
			ContestHud.render(graphics, deltaTracker)
			SafariAssist.render(graphics)
            SafariTracking.render(graphics)
            SparklingEncounters.render(graphics)
            SmallAlerts.render(graphics)
		}
	}

	fun id(path: String): Identifier =
		Identifier.fromNamespaceAndPath(MOD_ID, path)
}
