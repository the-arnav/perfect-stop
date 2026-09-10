package com.example.perfectstop.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockCalibrationTest {
    @Test fun excludesHostQueueDelay() {
        val clock = ClockCalibration()
        clock.add(1000, 1510, 1710, 1220)
        assertEquals(500L, clock.offsetMs)
    }
    @Test fun prefersLowLatencySample() {
        val clock = ClockCalibration()
        clock.add(1000, 1560, 1560, 1080)
        clock.add(2000, 2505, 2505, 2010)
        clock.add(3000, 3570, 3570, 3100)
        assertEquals(500L, clock.offsetMs)
    }
}
