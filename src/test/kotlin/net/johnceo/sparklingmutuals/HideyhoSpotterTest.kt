package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.config.SafariTheme
import net.johnceo.sparklingmutuals.safari.HideyhoHiding
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HideyhoSpotterTest {
    @Test fun `spots inside the mansion's outline are inside, the rest outside`() {
        assertTrue(HideyhoHiding.inside(HideyhoHiding.Spot(-4, 68, -64)))
        assertTrue(HideyhoHiding.inside(HideyhoHiding.Spot(0, 79, -78)))
        assertFalse(HideyhoHiding.inside(HideyhoHiding.Spot(37, 70, -14)))
        assertFalse(HideyhoHiding.inside(HideyhoHiding.Spot(19, 70, -24)))
        // Just past the north wall's notch (z -87 at x 10..17).
        assertFalse(HideyhoHiding.inside(HideyhoHiding.Spot(13, 71, -88)))
    }
    @Test fun `every spot is in the Haunted biome, where the critter scan reports Hideyho`() {
        HideyhoHiding.SPOTS.forEach { spot ->
            assertEquals(net.johnceo.sparklingmutuals.safari.SafariBiome.HAUNTED,
                net.johnceo.sparklingmutuals.safari.SafariEspRules.biomeAt(spot.x + .5, spot.z + .5), spot.toString())
        }
    }
    @Test fun `Hideyho counts as settled only near a listed spot`() {
        assertEquals(HideyhoHiding.Spot(-4, 68, -64), HideyhoHiding.nearestSpot(-3.7, 68.0, -62.2))
        assertNull(HideyhoHiding.nearestSpot(-4.0, 45.0, -64.0), "still surfacing from below")
        assertNull(HideyhoHiding.nearestSpot(100.0, 70.0, 100.0))
    }
    @Test fun `only Hideyho's own lines arm and end the watch`() {
        assertTrue(HideyhoHiding.accepted("[MOB] Hideyho: Close your eyes and then come find me!"))
        assertTrue(HideyhoHiding.accepted("[MOB] Hideyho: No peeking!"))
        assertFalse(HideyhoHiding.accepted("Party > Farmer: [MOB] Hideyho: No peeking!"))
        assertTrue(HideyhoHiding.found("[MOB] Hideyho: Aah! You found me!"))
        assertFalse(HideyhoHiding.found("[MOB] Hideyho: Let's play again!"))
    }
    @Test fun `the verdict uses the mod's theme colours`() {
        fun colours(c: Component) = c.toFlatList().map { it.style.color?.value }
        val inside = LocalChat.hideyho(inside = true)
        val outside = LocalChat.hideyho(inside = false)
        assertEquals("Hideyho inside!", inside.string)
        assertEquals("Hideyho outside!", outside.string)
        assertEquals(listOf(SafariTheme.TEXT, SafariTheme.ACCENT), colours(inside))
        assertEquals(listOf(SafariTheme.TEXT, SafariTheme.TEXT), colours(outside))
        val line = LocalChat.component(inside)
        assertEquals("[SM] Hideyho inside!", line.string)
        assertEquals(SafariTheme.ACCENT, line.toFlatList().first().style.color?.value)
    }
}
