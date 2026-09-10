package com.example.perfectstop.model

enum class GamePhase {
    MENU,
    HISTORY,
    LEADERBOARD,
    LOBBY,
    COUNTDOWN,
    RUNNING,
    PLAYER_STOPPED,
    RESULTS
}

enum class GameRole {
    HOST,
    CLIENT,
    SOLO
}

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    ADVERTISING,
    CONNECTING,
    CONNECTED,
    BLUETOOTH_DISABLED,
    PERMISSION_DENIED
}

enum class BotDifficulty(val label: String, val varianceMs: Int) {
    CASUAL("CASUAL (±180ms)", 180),
    VETERAN("VETERAN (±60ms)", 60),
    PRO("PRO (±20ms)", 20)
}

/**
 * Immutable Player model representing a participant in the match.
 * All properties are immutable (val) to ensure predictable StateFlow emissions.
 */
data class Player(
    val index: Int,
    val id: String,
    val name: String,
    val isHost: Boolean = false,
    val stoppedTimeMs: Long? = null,
    val deltaMs: Long? = null,
    val isStopped: Boolean = false,
    val rank: Int = 0,
    val identity: String = id
) {
    val score: Int get() = TimingScore.points(deltaMs, stoppedTimeMs == -1L)
    val formattedDelta: String
        get() {
            val d = deltaMs ?: return "--"
            if (d == 0L) return "PERFECT (±0.000s)"
            val sign = if (d > 0) "+" else "-"
            val sec = String.format(java.util.Locale.US, "%.3f", Math.abs(d) / 1000.0)
            return "$sign$sec s"
        }

    val formattedStoppedTime: String
        get() {
            val t = stoppedTimeMs ?: return "--:--.---"
            return String.format(java.util.Locale.US, "%.3fs", t / 1000.0)
        }

    fun withReset(): Player = copy(
        stoppedTimeMs = null,
        deltaMs = null,
        isStopped = false,
        rank = 0
    )
}

object TimingScore {
    fun points(errorMs: Long?, invalid: Boolean = false): Int =
        if (invalid || errorMs == null || errorMs !in -999L..999L) 0 else 1000 - kotlin.math.abs(errorMs).toInt()
}

data class JoinRequestNotification(
    val playerId: Int,
    val playerName: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
