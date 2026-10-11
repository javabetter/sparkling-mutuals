package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.random.Random

class EspOptimizationTest {
    @Test fun `texture cache avoids repeated decoding including misses and evicts least recently used data`() {
        var decodes = 0
        val cache = EspTextureCache(capacity = 2, maxEncodedLength = 16) {
            decodes++; if (it == "bad") null else it.uppercase()
        }
        repeat(1000) { assertEquals("A", cache.hash("a")); assertNull(cache.hash("bad")) }
        assertEquals(2, decodes)
        assertEquals("A", cache.hash("a"))
        assertEquals("B", cache.hash("b"))
        assertEquals(3, decodes)
        assertNull(cache.hash("bad"))
        assertEquals(4, decodes)
        repeat(2) { assertEquals("X".repeat(17), cache.hash("x".repeat(17))) }
        assertEquals(6, decodes)
        assertEquals("B", cache.hash("b"))
        assertNull(cache.hash(null)); assertEquals(6, decodes)
    }
    @Test fun `outline edges preserve original vertex order normals and degenerate float geometry`() {
        fun original(b: List<Float>): List<List<Float>> {
            val corners = List(8) { i -> floatArrayOf(b[if (i and 1 == 0) 0 else 3],
                b[if (i and 2 == 0) 1 else 4], b[if (i and 4 == 0) 2 else 5]) }
            return buildList {
                for (i in 0..7) for (bit in listOf(1, 2, 4)) if (i and bit == 0) {
                    if (corners[i].contentEquals(corners[i or bit])) continue
                    add(corners[i].toList() + corners[i or bit].toList() + (if (bit == 1) 0f else if (bit == 2) 1f else 2f))
                }
            }
        }
        val random = Random(21)
        val boxes = List(100) { List(6) { random.nextFloat() * 200 - 100 } } +
            listOf(List(6) { 0f }, listOf(-0f, Float.NaN, 0f, 0f, Float.NaN, 1f), listOf(1f, 2f, 3f, 1f, 8f, 3f))
        for (b in boxes) {
            val actual = mutableListOf<List<Float>>()
            EspBoxLines.edges(b[0], b[1], b[2], b[3], b[4], b[5]) { x, y, z, ex, ey, ez, axis ->
                actual.add(listOf(x, y, z, ex, ey, ez, axis.toFloat()))
            }
            assertEquals(original(b), actual)
        }
    }
    @Test fun `texture parsing preserves invalid uppercase changed and oversized profile data`() {
        fun encoded(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())
        val hash = "aB".repeat(32)
        val profile = encoded("https://textures.minecraft.net/texture/$hash")
        repeat(20) {
            assertEquals(hash.lowercase(), SafariEspRules.textureHash(profile))
            assertNull(SafariEspRules.textureHash(null))
            assertNull(SafariEspRules.textureHash("not base64!"))
            assertNull(SafariEspRules.textureHash(encoded("https://textures.minecraft.net/texture/" + hash + "a")))
        }
        assertEquals("c".repeat(64), SafariEspRules.textureHash(encoded("https://textures.minecraft.net/texture/" + "c".repeat(64))))
        assertEquals(hash.lowercase(), SafariEspRules.textureHash(encoded(" ".repeat(10000) + "https://textures.minecraft.net/texture/$hash")))
    }
    @Test fun `floor marker grouping preserves threshold minimum IDs duplicates and insertion order`() {
        val random = Random(741)
        repeat(100) {
            val markers = List(random.nextInt(400)) {
                EspDrop(random.nextInt(-50, 50), random.nextInt(-2, 3), random.nextInt(-1, 2), random.nextInt(-2, 3))
            }
            val expected = markers.groupBy { Triple(it.x, it.y, it.z) }.values
                .filter { it.size >= 3 }.map { group -> group.minBy { it.id } }
            assertEquals(expected, SafariEspRules.floorDrops(markers))
        }
    }
}
