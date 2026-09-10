package com.example.perfectstop.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingScoreTest {
    @Test fun exactAndProportionalScores() {
        assertEquals(1000, TimingScore.points(0))
        assertEquals(999, TimingScore.points(1))
        assertEquals(750, TimingScore.points(250))
        assertEquals(500, TimingScore.points(500))
        assertEquals(1, TimingScore.points(999))
        assertEquals(0, TimingScore.points(1000))
    }
    @Test fun earlyAndLateHaveIdenticalPenalty() {
        for (error in 0L..1100L) assertEquals(TimingScore.points(error), TimingScore.points(-error))
    }
    @Test fun invalidAndExtremeValuesScoreZero() {
        assertEquals(0, TimingScore.points(null))
        assertEquals(0, TimingScore.points(-1, invalid = true))
        assertEquals(0, TimingScore.points(Long.MIN_VALUE))
        assertEquals(0, TimingScore.points(Long.MAX_VALUE))
    }
    @Test fun scoresNeverImproveAsErrorIncreases() {
        for (error in 0L..2000L) assertTrue(TimingScore.points(error) >= TimingScore.points(error + 1))
    }
    @Test fun falseStartPlayerDoesNotEarnPoints() {
        assertEquals(0, Player(0, "a", "A", stoppedTimeMs = -1, deltaMs = -1).score)
        assertEquals(800, Player(0, "a", "A", stoppedTimeMs = 200, deltaMs = 200).score)
    }
}
