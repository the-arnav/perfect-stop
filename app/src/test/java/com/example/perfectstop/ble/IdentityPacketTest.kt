package com.example.perfectstop.ble

import com.example.perfectstop.model.Player
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.UUID

class IdentityPacketTest {
    private val alice = "ef429878-67b5-4f68-b53f-31b3213c3c40"
    private val bob = "e9914928-c47c-42a9-a89d-82d7e4b0e08b"
    @Test fun joinIdentitySurvivesTransportIdReplacement() {
        val bytes = BlePackets.buildJoinRequest(1, "Alice", alice)
        bytes[1] = 72
        val buffer = ByteBuffer.wrap(bytes, 3 + (bytes[2].toInt() and 255), 16)
        assertEquals(alice, UUID(buffer.long, buffer.long).toString())
    }
    @Test fun fullRosterPreservesNamesAndIdentities() {
        val roster = listOf(Player(0, "host", "Alice", true, identity = alice), Player(1, "peer_3", "Bob", identity = bob))
        val decoded = BlePackets.parseRosterSync(BlePackets.buildRosterSync(roster))!!
        assertEquals(listOf(alice, bob), decoded.map { it.identity })
        assertEquals(listOf("Alice", "Bob"), decoded.map { it.name })
        assertTrue(decoded[0].isHost)
    }
    @Test fun truncatedAndTrailingPacketsAreRejected() {
        val bytes = BlePackets.buildRosterSync(listOf(Player(0, "host", "Alice", true, identity = alice)))
        for (length in 0 until bytes.size) assertNull(BlePackets.parseRosterSync(bytes.copyOf(length)))
        assertNull(BlePackets.parseRosterSync(bytes + byteArrayOf(0)))
    }
    @Test fun falseStartRemainsSignedOnWire() {
        val bytes = BlePackets.buildPlayerStop(2, -1)
        assertEquals(-1, ByteBuffer.wrap(bytes).getInt(2))
    }
    @Test fun fullRandomTargetRangeFitsProtocol() {
        for (target in listOf(4000, 7341, 14999, 15000)) {
            val bytes = BlePackets.buildStartRound(target, 123456, true)
            assertEquals(target, ByteBuffer.wrap(bytes).getShort(1).toInt() and 65535)
        }
    }
    @Test fun maximumRosterFitsNegotiatedMtu() {
        val roster = (0..3).map { Player(it, "peer_$it", "A".repeat(24), it == 0, identity = UUID.randomUUID().toString()) }
        val packet = BlePackets.buildRosterSync(roster)
        assertEquals(174, packet.size)
        assertTrue(packet.size <= 177 - 3)
        assertEquals(roster.map { it.identity }, BlePackets.parseRosterSync(packet)!!.map { it.identity })
    }
    @Test fun acceptAndResetUseProductionCodec() {
        assertArrayEquals(byteArrayOf(2, 3, 4), BlePackets.buildJoinAccept(3, 4))
        assertArrayEquals(byteArrayOf(5), BlePackets.buildRoundReset())
    }
}
