package com.example.perfectstop.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PastGame(val id: String, val date: Long, val mode: String, val target: Int,
                    val localIdentity: String, val players: List<Player>)

class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("game_records", Context.MODE_PRIVATE)
    private val device = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
    val identity: String = device.getString("id", null) ?: UUID.randomUUID().toString().also {
        device.edit().putString("id", it).commit()
    }
    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }
    fun read(): List<PastGame> = runCatching {
        val rows = JSONArray(prefs.getString("history", "[]"))
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val players = row.getJSONArray("players")
            PastGame(row.getString("id"), row.getLong("date"), row.getString("mode"), row.getInt("target"),
                row.getString("local"), (0 until players.length()).map { j ->
                    val p = players.getJSONObject(j)
                    Player(j, p.getString("identity"), p.getString("name"),
                        stoppedTimeMs = p.getLong("time"), deltaMs = p.getLong("delta"),
                        isStopped = true, rank = p.getInt("rank"), identity = p.getString("identity"))
                })
        }
    }.getOrDefault(emptyList())
    fun save(game: PastGame): List<PastGame> {
        val games = listOf(game) + read().filterNot { it.id == game.id }
        val rows = JSONArray()
        games.forEach { g ->
            val players = JSONArray()
            g.players.forEach { p -> players.put(JSONObject().put("identity", p.identity).put("name", p.name)
                .put("time", p.stoppedTimeMs ?: -1).put("delta", p.deltaMs ?: -1).put("rank", p.rank)) }
            rows.put(JSONObject().put("id", g.id).put("date", g.date).put("mode", g.mode)
                .put("target", g.target).put("local", g.localIdentity).put("players", players))
        }
        prefs.edit().putString("history", rows.toString()).apply()
        return games
    }
}
