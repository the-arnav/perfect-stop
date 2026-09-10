package com.example.perfectstop.model

import android.content.Context
import kotlin.random.Random

/**
 * Manages unique, randomized player callsigns per installed application.
 * Ensures every newly downloaded app has a unique random username out-of-the-box,
 * avoiding duplicate name collisions when pairing over BLE.
 */
object UserProfile {
    private const val PREFS_NAME = "perfect_stop_profile"
    private const val KEY_USERNAME = "saved_username"

    private val CALLSIGN_PREFIXES = listOf(
        "Chrono", "Apex", "Pulse", "Neon", "Quantum",
        "Vortex", "Hyper", "Velocity", "Zenith", "Blitz",
        "Sonic", "Phantom", "Cipher", "Turbo", "Shift",
        "Falcon", "Nova", "Titan", "Strike", "Shadow",
        "Vector", "Echo", "Specter", "Orbit", "Drift"
    )

    fun getOrGenerateUsername(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USERNAME, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val prefix = CALLSIGN_PREFIXES.random()
        val suffix = Random.nextInt(100, 999)
        val generated = "${prefix}_$suffix"

        prefs.edit().putString(KEY_USERNAME, generated).apply()
        return generated
    }

    fun saveUsername(context: Context, name: String) {
        val trimmed = name.trim().take(16)
        if (trimmed.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_USERNAME, trimmed).apply()
        }
    }
}
