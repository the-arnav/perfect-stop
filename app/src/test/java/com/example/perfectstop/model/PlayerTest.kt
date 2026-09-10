package com.example.perfectstop.model

import org.junit.Assert.*
import org.junit.Test

class PlayerTest {
    @Test
    fun formatsTimingResults() {
        val player = Player(0, "p0", "Player", stoppedTimeMs = 7534, deltaMs = 34)
        assertEquals("7.534s", player.formattedStoppedTime)
        assertEquals("+0.034 s", player.formattedDelta)

        val playerUnder = Player(1, "p1", "Player", stoppedTimeMs = 7450, deltaMs = -50)
        assertEquals("7.450s", playerUnder.formattedStoppedTime)
        assertEquals("-0.050 s", playerUnder.formattedDelta)

        val playerPerfect = Player(2, "p2", "Player", stoppedTimeMs = 7500, deltaMs = 0)
        assertEquals("PERFECT (±0.000s)", playerPerfect.formattedDelta)
    }

    @Test
    fun resetReturnsNewImmutablePlayer() {
        val original = Player(0, "p0", "Player", stoppedTimeMs = 7534, deltaMs = 34, isStopped = true, rank = 1)
        val resetPlayer = original.withReset()

        assertNull(resetPlayer.stoppedTimeMs)
        assertNull(resetPlayer.deltaMs)
        assertFalse(resetPlayer.isStopped)
        assertEquals(0, resetPlayer.rank)

        // Verify original remains untouched (immutability)
        assertEquals(7534L, original.stoppedTimeMs)
        assertEquals(34L, original.deltaMs)
        assertTrue(original.isStopped)
        assertEquals(1, original.rank)
    }

    @Test
    fun rankingLogicSelectsSmallestAbsoluteDelta() {
        val p1 = Player(0, "p1", "Alice", stoppedTimeMs = 7580, deltaMs = 80)
        val p2 = Player(1, "p2", "Bob", stoppedTimeMs = 7485, deltaMs = -15)
        val p3 = Player(2, "p3", "Charlie", stoppedTimeMs = 7540, deltaMs = 40)

        val sorted = listOf(p1, p2, p3).sortedBy { Math.abs(it.deltaMs ?: 999999L) }
        assertEquals("Bob", sorted[0].name) // |-15| = 15ms -> 1st
        assertEquals("Charlie", sorted[1].name) // |40| = 40ms -> 2nd
        assertEquals("Alice", sorted[2].name) // |80| = 80ms -> 3rd
    }
}
