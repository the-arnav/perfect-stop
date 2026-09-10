package com.example.perfectstop.ble

import com.example.perfectstop.model.Player
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlePacketTest {

    @Test
    fun testJoinRequestPacket() {
        val nameBytes = "TurboRacer".toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(3 + nameBytes.size)
        buf.put(BleManager.OPCODE_JOIN_REQUEST.toByte())
        buf.put(42.toByte())
        buf.put(nameBytes.size.toByte())
        buf.put(nameBytes)
        val bytes = buf.array()

        assertEquals(BleManager.OPCODE_JOIN_REQUEST, bytes[0].toInt() and 0xFF)
        assertEquals(42, bytes[1].toInt() and 0xFF)
        val len = bytes[2].toInt() and 0xFF
        val decodedName = String(bytes, 3, len, Charsets.UTF_8)
        assertEquals("TurboRacer", decodedName)
    }

    @Test
    fun testStartRoundPacket() {
        val targetMs = 7500
        val epochMs = 1720000000L
        val isBlind = true

        val buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        buf.put(BleManager.OPCODE_START_ROUND.toByte())
        buf.putShort(targetMs.toShort())
        buf.putInt((epochMs and 0xFFFFFFFFL).toInt())
        buf.put(if (isBlind) 1.toByte() else 0.toByte())
        val bytes = buf.array()

        assertEquals(8, bytes.size)
        val readBuf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(BleManager.OPCODE_START_ROUND, readBuf.get(0).toInt() and 0xFF)
        assertEquals(7500, readBuf.getShort(1).toInt() and 0xFFFF)
        assertEquals(1720000000L, readBuf.getInt(3).toLong() and 0xFFFFFFFFL)
        assertEquals(1, readBuf.get(7).toInt())
    }

    @Test
    fun testPlayerStopPacket() {
        val playerIdx = 2
        val stoppedMs = 7514L

        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(BleManager.OPCODE_PLAYER_STOP.toByte())
        buf.put(playerIdx.toByte())
        buf.putInt((stoppedMs and 0xFFFFFFFFL).toInt())
        val bytes = buf.array()

        assertEquals(6, bytes.size)
        val readBuf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        assertEquals(BleManager.OPCODE_PLAYER_STOP, readBuf.get(0).toInt() and 0xFF)
        assertEquals(2, readBuf.get(1).toInt() and 0xFF)
        assertEquals(7514L, readBuf.getInt(2).toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun testRosterSyncPacket() {
        val players = listOf(
            Player(0, "p0", "Alice", isHost = true),
            Player(1, "p1", "Bob", isHost = false),
            Player(2, "p2", "Charlie", isHost = false)
        )

        val totalSize = 2 + (3 + 5) + (3 + 3) + (3 + 7)
        val buf = ByteBuffer.allocate(totalSize)
        buf.put(BleManager.OPCODE_ROSTER_SYNC.toByte())
        buf.put(players.size.toByte())

        for (p in players) {
            val nBytes = p.name.toByteArray(Charsets.UTF_8)
            buf.put(p.index.toByte())
            buf.put(if (p.isHost) 1.toByte() else 0.toByte())
            buf.put(nBytes.size.toByte())
            buf.put(nBytes)
        }
        val bytes = buf.array()

        assertEquals(BleManager.OPCODE_ROSTER_SYNC, bytes[0].toInt() and 0xFF)
        val count = bytes[1].toInt() and 0xFF
        assertEquals(3, count)

        // Decode manually
        var offset = 2
        val decoded = mutableListOf<Player>()
        for (i in 0 until count) {
            val idx = bytes[offset].toInt() and 0xFF
            val isHost = bytes[offset + 1].toInt() != 0
            val len = bytes[offset + 2].toInt() and 0xFF
            offset += 3
            val name = String(bytes, offset, len, Charsets.UTF_8)
            offset += len
            decoded.add(Player(idx, "p_$idx", name, isHost))
        }

        assertEquals(3, decoded.size)
        assertEquals("Alice", decoded[0].name)
        assertTrue(decoded[0].isHost)
        assertEquals("Bob", decoded[1].name)
        assertFalse(decoded[1].isHost)
        assertEquals("Charlie", decoded[2].name)
    }
}
