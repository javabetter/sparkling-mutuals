package net.johnceo.sparklingmutuals

import net.johnceo.sparklingmutuals.safari.EspBoxSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EspBoxSizeTest {
    private fun assertAxis(min: Double, max: Double, expected: Pair<Double, Double>) {
        val (a, b) = EspBoxSize.axis(min, max)
        assertEquals(expected.first, a, 1e-9); assertEquals(expected.second, b, 1e-9)
    }
    @Test fun `large hitboxes grow by the padding on each side`() {
        assertAxis(0.0, 2.0, -.2 to 2.2)
        assertAxis(-5.0, -3.5, -5.2 to -3.3)
    }
    @Test fun `small hitboxes widen about their centre to the minimum size`() {
        assertAxis(0.0, .3, -.3 to .6)
        assertAxis(1.0, 1.0, .55 to 1.45)
        val (a, b) = EspBoxSize.axis(10.0, 10.4)
        assertEquals(EspBoxSize.MIN_SIZE, b - a, 1e-9); assertEquals(10.2, (a + b) / 2, 1e-9)
    }
}
