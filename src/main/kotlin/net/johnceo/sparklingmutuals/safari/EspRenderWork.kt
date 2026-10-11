package net.johnceo.sparklingmutuals.safari

import net.minecraft.world.phys.AABB
import java.util.Base64

/** Cache immutable profile values, never entity state; changed profiles are decoded immediately. */
internal class EspTextureCache(
    private val capacity: Int = 256,
    private val maxEncodedLength: Int = 8192,
    private val decode: (String) -> String? = ::decodeTextureHash
) {
    init { require(capacity > 0 && maxEncodedLength > 0) }
    private val values = LinkedHashMap<String, String?>(capacity, .75f, true)

    @Synchronized fun hash(encoded: String?): String? {
        if (encoded == null) return null
        // Preserve parsing of oversized inputs without retaining their potentially large strings.
        if (encoded.length > maxEncodedLength) return decode(encoded)
        val cached = values[encoded]
        if (cached != null || values.containsKey(encoded)) return cached
        val result = decode(encoded)
        values[encoded] = result
        if (values.size > capacity) values.entries.iterator().let { it.next(); it.remove() }
        return result
    }
}

private val texturePattern = Regex("textures\\.minecraft\\.net/texture/([a-fA-F0-9]{64})(?![a-fA-F0-9])")
private fun decodeTextureHash(encoded: String): String? = try {
    texturePattern.find(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))?.groupValues?.get(1)?.lowercase()
} catch (_: IllegalArgumentException) { null }

/** Emit the original corner/axis order without allocating corner arrays or endpoint lists. */
internal object EspBoxLines {
    inline fun edges(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float,
        emit: (Float, Float, Float, Float, Float, Float, Int) -> Unit) {
        for (i in 0..7) {
            val x = if (i and 1 == 0) minX else maxX
            val y = if (i and 2 == 0) minY else maxY
            val z = if (i and 4 == 0) minZ else maxZ
            var bit = 1
            while (bit <= 4) {
                if (i and bit == 0) {
                    val end = i or bit
                    val ex = if (end and 1 == 0) minX else maxX
                    val ey = if (end and 2 == 0) minY else maxY
                    val ez = if (end and 4 == 0) minZ else maxZ
                    if (x.toBits() != ex.toBits() || y.toBits() != ey.toBits() || z.toBits() != ez.toBits())
                        emit(x, y, z, ex, ey, ez, if (bit == 1) 0 else if (bit == 2) 1 else 2)
                }
                bit = bit shl 1
            }
        }
    }
}

/** Critter boxes are drawn a little larger than the hitbox, and never thinner than [MIN_SIZE], so small critters stay easy to spot and the outline sits clear of the model. */
internal object EspBoxSize {
    const val PADDING = .2
    const val MIN_SIZE = .9
    /** One axis of a padded box: grown by [PADDING] each side, then widened about its centre up to [MIN_SIZE]. */
    fun axis(min: Double, max: Double): Pair<Double, Double> {
        val grow = maxOf(0.0, MIN_SIZE - (max - min + 2 * PADDING)) / 2
        return (min - PADDING - grow) to (max + PADDING + grow)
    }
    fun padded(box: AABB): AABB {
        val (minX, maxX) = axis(box.minX, box.maxX)
        val (minY, maxY) = axis(box.minY, box.maxY)
        val (minZ, maxZ) = axis(box.minZ, box.maxZ)
        return AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }
}
