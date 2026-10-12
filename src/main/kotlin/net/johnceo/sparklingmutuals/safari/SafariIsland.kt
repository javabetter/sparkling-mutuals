package net.johnceo.sparklingmutuals.safari

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.azureaaron.hmapi.events.HypixelPacketEvents
import net.azureaaron.hmapi.network.HypixelNetworking
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket
import net.minecraft.client.Minecraft

/**
 * Whether the player is on the Critter Safari island itself (Mod API mode `safari`), rather than the Critter
 * Safari zone on Torrhus Canyon, which shares the name and also has a Safari Manager.
 */
object SafariIsland {
    const val MODE = "safari"
    @Volatile var onIsland = false
        private set

    fun register() {
        HypixelNetworking.registerToEvents(Object2IntOpenHashMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<HypixelS2CPacket>>()
            .apply { put(LocationUpdateS2CPacket.ID, 1) })
        HypixelPacketEvents.LOCATION_UPDATE.register { packet ->
            if (packet is LocationUpdateS2CPacket) Minecraft.getInstance().execute { onIsland = isSafari(packet.mode().orElse(null)) }
        }
    }
    fun isSafari(mode: String?) = mode.equals(MODE, true)
    fun reset() { onIsland = false }
}
