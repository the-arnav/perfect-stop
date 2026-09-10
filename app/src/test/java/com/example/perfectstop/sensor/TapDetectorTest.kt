package com.example.perfectstop.sensor

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class TapDetectorTest {

    @Test
    fun testVectorMagnitudeMath() {
        // At rest: ax=0, ay=0, az=9.80665 -> 1.0G
        val restMagnitude = sqrt(0.0 * 0.0 + 0.0 * 0.0 + 9.80665 * 9.80665) / TapDetector.GRAVITY
        assertEquals(1.0, restMagnitude, 0.001)

        // Impulse bump: ax=12.0, ay=8.0, az=18.0 -> ~23.06 m/s^2 -> ~2.35G
        val bumpMagnitude = sqrt(12.0 * 12.0 + 8.0 * 8.0 + 18.0 * 18.0) / TapDetector.GRAVITY
        assertTrue(bumpMagnitude > TapDetector.TAP_ACCEL_THRESHOLD_G)
    }

    @Test
    fun testThresholdConstants() {
        assertEquals(1.8, TapDetector.TAP_ACCEL_THRESHOLD_G, 0.001)
        assertEquals(-65, TapDetector.TAP_RSSI_THRESHOLD_DBM)
        assertEquals(1500L, TapDetector.TAP_COINCIDENCE_WINDOW_MS)
    }

    @Test
    fun testCoincidenceCondition() {
        val joltTime = 1000L
        val rssiTime = 1080L // 80ms delta <= 150ms
        val delta = Math.abs(joltTime - rssiTime)
        assertTrue(delta <= TapDetector.TAP_COINCIDENCE_WINDOW_MS)

        val nonCoincidentRssiTime = 2600L
        val deltaTooLarge = Math.abs(joltTime - nonCoincidentRssiTime)
        assertFalse(deltaTooLarge <= TapDetector.TAP_COINCIDENCE_WINDOW_MS)
    }
}
