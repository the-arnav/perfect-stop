package com.example.perfectstop.ble

import com.example.perfectstop.model.Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Pure codec shared by transport and protocol tests. */
object BlePackets {
    fun buildJoinRequest(playerId: Int, playerName: String, identity: String = UUID.randomUUID().toString()): ByteArray {
        val nameBytes = playerName.toByteArray(Charsets.UTF_8).take(32).toByteArray()
        val buf = ByteBuffer.allocate(3 + nameBytes.size + 16)
        buf.put(BleManager.OPCODE_JOIN_REQUEST.toByte())
        buf.put((playerId and 0xFF).toByte())
        buf.put(nameBytes.size.toByte())
        buf.put(nameBytes)
        val uuid = UUID.fromString(identity)
        buf.putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
        return buf.array()
    }

    fun buildJoinAccept(assignedIndex: Int, totalPlayers: Int): ByteArray {
        return byteArrayOf(
            BleManager.OPCODE_JOIN_ACCEPT.toByte(),
            (assignedIndex and 0xFF).toByte(),
            (totalPlayers and 0xFF).toByte()
        )
    }

    fun buildStartRound(targetMs: Int, countdownMs: Int, isBlindMode: Boolean): ByteArray {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buf.put(BleManager.OPCODE_START_ROUND.toByte())
        buf.putShort(targetMs.toShort())
        buf.putInt(countdownMs)
        buf.put(if (isBlindMode) 1.toByte() else 0.toByte())
        return buf.array()
    }

    fun buildPlayerStop(playerIndex: Int, stoppedMs: Long): ByteArray {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(BleManager.OPCODE_PLAYER_STOP.toByte())
        buf.put((playerIndex and 0xFF).toByte())
        buf.putInt((stoppedMs and 0xFFFFFFFFL).toInt())
        return buf.array()
    }

    fun buildRoundReset(): ByteArray {
        return byteArrayOf(BleManager.OPCODE_ROUND_RESET.toByte())
    }

    /**
     * Build 0x06 ROSTER_SYNC packet:
     * [0x06, PlayerCount, (Index, IsHost, NameLen, NameBytes)...]
     */
    fun buildRosterSync(players: List<Player>): ByteArray {
        val safePlayers = players.take(4)
        var totalSize = 2 // Opcode + Count
        val serialized = safePlayers.map { p ->
            val nBytes = p.name.toByteArray(Charsets.UTF_8).take(24).toByteArray()
            totalSize += 19 + nBytes.size
            Triple(p.index, p.isHost, nBytes)
        }

        val buf = ByteBuffer.allocate(totalSize)
        buf.put(BleManager.OPCODE_ROSTER_SYNC.toByte())
        buf.put(safePlayers.size.toByte())
        for ((idx, isHost, nBytes) in serialized) {
            buf.put((idx and 0xFF).toByte())
            buf.put(if (isHost) 1.toByte() else 0.toByte())
            buf.put(nBytes.size.toByte())
            buf.put(nBytes)
            val player = safePlayers.first { it.index == idx }
            val uuid = runCatching { UUID.fromString(player.identity) }.getOrElse { UUID.nameUUIDFromBytes(player.identity.toByteArray()) }
            buf.putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits)
        }
        return buf.array()
    }

    fun parseRosterSync(bytes: ByteArray): List<Player>? {
        if (bytes.size < 2 || (bytes[0].toInt() and 0xFF) != BleManager.OPCODE_ROSTER_SYNC) return null
        val count = bytes[1].toInt() and 0xFF
        if (count !in 1..4) return null
        val list = mutableListOf<Player>()
        var offset = 2
        for (i in 0 until count) {
            if (offset + 3 > bytes.size) return null
            val idx = bytes[offset].toInt() and 0xFF
            val isHost = bytes[offset + 1].toInt() != 0
            val nameLen = bytes[offset + 2].toInt() and 0xFF
            offset += 3
            if (offset + nameLen + 16 > bytes.size || idx !in 0..3 || list.any { it.index == idx }) return null
            val name = String(bytes, offset, nameLen, Charsets.UTF_8)
            offset += nameLen
            val identityBytes = ByteBuffer.wrap(bytes, offset, 16)
            val identity = UUID(identityBytes.long, identityBytes.long).toString()
            offset += 16
            list.add(Player(index = idx, id = "player_$idx", name = name, isHost = isHost, identity = identity))
        }
        return list.takeIf { offset == bytes.size && it.count { p -> p.isHost } == 1 }
    }


}

