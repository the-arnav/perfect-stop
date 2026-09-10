package com.example.perfectstop.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.example.perfectstop.model.BleConnectionState
import com.example.perfectstop.model.Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BleManager(
    private val context: Context,
    private val onPacketReceived: (ByteArray) -> Unit,
    private val onRssiUpdated: (Int) -> Unit,
    private val onConnectionStateChanged: (BleConnectionState) -> Unit
) {
    companion object {
        // Version 2 includes persistent identities in join and roster packets.
        val SERVICE_UUID: UUID = UUID.fromString("0000BEF2-0000-1000-8000-00805F9B34FB")
        val CHAR_UUID: UUID = UUID.fromString("0000CAFE-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        const val DEVICE_NAME = "PERFECT_STOP_HOST"

        const val OPCODE_JOIN_REQUEST = 0x01
        const val OPCODE_JOIN_ACCEPT = 0x02
        const val OPCODE_START_ROUND = 0x03
        const val OPCODE_PLAYER_STOP = 0x04
        const val OPCODE_ROUND_RESET = 0x05
        const val OPCODE_ROSTER_SYNC = 0x06
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private val connectedClients = ConcurrentHashMap<String, BluetoothDevice>()

    private var scanner: BluetoothLeScanner? = null
    private var hostGatt: BluetoothGatt? = null
    private var hostCharacteristic: BluetoothGattCharacteristic? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val peerIds = ConcurrentHashMap<String, Int>()
    private val subscribed = ConcurrentHashMap.newKeySet<String>()
    private val outgoing = java.util.ArrayDeque<Pair<BluetoothDevice, ByteArray>>()
    private var sending = false
    private var nextPeerId = 1
    private val assignedPlayers = ConcurrentHashMap<Int, Int>()
    fun assignPlayer(peerId: Int, index: Int) { assignedPlayers[peerId] = index }
    var clockOffsetMs = 0L
        private set
    private var pingSent = 0L
    private var calibration = ClockCalibration()
    private var samples = 0

    fun sendToPeer(peerId: Int, packet: ByteArray) {
        val device = connectedClients.values.firstOrNull { peerIds[it.address] == peerId } ?: return
        handler.post { outgoing.add(device to packet.copyOf()); drain() }
    }

    @SuppressLint("MissingPermission")
    private fun drain() {
        if (sending || outgoing.isEmpty()) return
        val (device, packet) = outgoing.removeFirst()
        val server = gattServer ?: return
        val characteristic = server.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID) ?: return
        if (packet.size == 25 && packet[0].toInt() == 8) {
            ByteBuffer.wrap(packet).putLong(17, android.os.SystemClock.elapsedRealtime())
        }
        sending = true
        val ok = try {
            if (Build.VERSION.SDK_INT >= 33) server.notifyCharacteristicChanged(device, characteristic, true, packet) == BluetoothStatusCodes.SUCCESS
            else {
                characteristic.value = packet
                server.notifyCharacteristicChanged(device, characteristic, true)
            }
        } catch (_: SecurityException) { false }
        if (!ok) { sending = false; handler.postDelayed({ drain() }, 30) }
    }

    var onReadyToTransmit: (() -> Unit)? = null
    var onPeerDisconnected: ((Int) -> Unit)? = null

    val isBluetoothEnabled: Boolean
        get() = try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }

    // --- Packet Builders ---

    fun buildJoinRequest(playerId: Int, playerName: String, identity: String) = BlePackets.buildJoinRequest(playerId, playerName, identity)
    fun buildJoinAccept(assignedIndex: Int, totalPlayers: Int) = BlePackets.buildJoinAccept(assignedIndex, totalPlayers)
    fun buildStartRound(targetMs: Int, countdownMs: Int, isBlindMode: Boolean) = BlePackets.buildStartRound(targetMs, countdownMs, isBlindMode)
    fun buildPlayerStop(playerIndex: Int, stoppedMs: Long) = BlePackets.buildPlayerStop(playerIndex, stoppedMs)
    fun buildRoundReset() = BlePackets.buildRoundReset()
    fun buildRosterSync(players: List<Player>) = BlePackets.buildRosterSync(players)
    fun parseRosterSync(bytes: ByteArray) = BlePackets.parseRosterSync(bytes)

    // --- Host Mode (GATT Server + Advertiser) ---

    @SuppressLint("MissingPermission")
    fun startHostAdvertising(): Boolean {
        if (!hasPermissions()) { onConnectionStateChanged(BleConnectionState.DISCONNECTED); return false }
        if (!isBluetoothEnabled) {
            onConnectionStateChanged(BleConnectionState.BLUETOOTH_DISABLED)
            return false
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            onConnectionStateChanged(BleConnectionState.DISCONNECTED)
            return false
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        // Start GATT Server
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Add CCCD descriptor to GATT Server
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        return true
    }

    @SuppressLint("MissingPermission")
    fun broadcastPacketFromHost(packet: ByteArray, burstCount: Int = 3) {
        handler.post {
            connectedClients.values.filter { subscribed.contains(it.address) }.forEach {
                outgoing.add(it to packet.copyOf())
            }
            drain()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            onConnectionStateChanged(BleConnectionState.ADVERTISING)
        }
        override fun onStartFailure(errorCode: Int) {
            onConnectionStateChanged(BleConnectionState.DISCONNECTED)
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onConnectionStateChanged(BleConnectionState.DISCONNECTED)
                return
            }
            val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build()
            val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).build()
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            handler.post { sending = false; drain() }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedClients[device.address] = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedClients.remove(device.address)
                subscribed.remove(device.address)
                handler.post { peerIds[device.address]?.let { onPeerDisconnected?.invoke(it) } }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            connectedClients[device.address] = device
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (preparedWrite || offset != 0 || characteristic.uuid != CHAR_UUID || value == null || value.isEmpty()) return
            handler.post {
                val id = peerIds.getOrPut(device.address) { nextPeerId++ }
                if (value[0].toInt() == 7 && value.size == 9) {
                    sendToPeer(id, ByteBuffer.allocate(25).put(8.toByte()).putLong(ByteBuffer.wrap(value).getLong(1))
                        .putLong(android.os.SystemClock.elapsedRealtime()).putLong(0).array())
                } else {
                    val packet = value.copyOf()
                    if (packet[0].toInt() == OPCODE_JOIN_REQUEST && packet.size >= 3) packet[1] = id.toByte()
                    if (packet[0].toInt() == OPCODE_PLAYER_STOP && packet.size == 6) {
                        val assigned = assignedPlayers[id] ?: return@post
                        packet[1] = assigned.toByte()
                    }
                    if (packet[0].toInt() in listOf(OPCODE_JOIN_REQUEST, OPCODE_PLAYER_STOP)) onPacketReceived(packet)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            connectedClients[device.address] = device
            // CRITICAL: Set descriptor value on server so Android allows notification dispatch!
            @Suppress("DEPRECATION")
            descriptor.value = value
            if (value?.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) == true) subscribed.add(device.address)
            else subscribed.remove(device.address)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    // --- Client Mode (Scanner + GATT Client) ---

    @SuppressLint("MissingPermission")
    fun startClientScanning() {
        if (!hasPermissions()) { onConnectionStateChanged(BleConnectionState.DISCONNECTED); return }
        if (!isBluetoothEnabled) {
            onConnectionStateChanged(BleConnectionState.BLUETOOTH_DISABLED)
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            onConnectionStateChanged(BleConnectionState.DISCONNECTED)
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        onConnectionStateChanged(BleConnectionState.SCANNING)
        scanner?.startScan(filters, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) { onConnectionStateChanged(BleConnectionState.DISCONNECTED) }
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                onRssiUpdated(it.rssi)
                if (hostGatt == null) {
                    onConnectionStateChanged(BleConnectionState.CONNECTING)
                    hostGatt = it.device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
                }
            }
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onConnectionStateChanged(BleConnectionState.CONNECTING)
                if (!gatt.requestMtu(185)) {
                    onConnectionStateChanged(BleConnectionState.DISCONNECTED)
                    gatt.disconnect()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onConnectionStateChanged(BleConnectionState.DISCONNECTED)
                hostCharacteristic = null
                try {
                    hostGatt?.close()
                } catch (_: Exception) {}
                hostGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && mtu >= 177) gatt.discoverServices()
            else {
                onConnectionStateChanged(BleConnectionState.DISCONNECTED)
                gatt.disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val char = service?.getCharacteristic(CHAR_UUID)
                if (char != null) {
                    hostCharacteristic = char
                    // 1. Enable notifications locally
                    gatt.setCharacteristicNotification(char, true)

                    // 2. Write to CCCD descriptor (0x2902) to trigger remote notification stream
                    val cccd = char.getDescriptor(CCCD_UUID)
                    if (cccd != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(cccd)
                        }
                    } else {
                        onConnectionStateChanged(BleConnectionState.CONNECTED)
                        onReadyToTransmit?.invoke()
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onConnectionStateChanged(BleConnectionState.CONNECTED)
                calibration = ClockCalibration()
                samples = 0
                handler.post { sendClockProbe() }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            characteristic.value?.let { receiveFromHost(it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receiveFromHost(value)
        }
    }

    private fun receiveFromHost(value: ByteArray) {
        val receivedAt = android.os.SystemClock.elapsedRealtime()
        handler.post {
            if (value.size == 25 && value[0].toInt() == 8) {
                val buffer = ByteBuffer.wrap(value)
                calibration.add(buffer.getLong(1), buffer.getLong(9), buffer.getLong(17), receivedAt)
                clockOffsetMs = calibration.offsetMs
                samples++
                if (samples < 5) handler.postDelayed({ sendClockProbe() }, 80)
                else onReadyToTransmit?.invoke()
            } else onPacketReceived(value)
        }
    }

    private fun sendClockProbe() {
        if (hostCharacteristic == null) return
        pingSent = android.os.SystemClock.elapsedRealtime()
        sendPacketFromClient(ByteBuffer.allocate(9).put(7.toByte()).putLong(pingSent).array())
    }

    @SuppressLint("MissingPermission")
    fun sendPacketFromClient(packet: ByteArray): Boolean {
        val gatt = hostGatt ?: return false
        val char = hostCharacteristic ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                char.value = packet
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf(
            android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
        ) else listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        return permissions.all { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()
            gattServer = null
            scanner?.stopScan(scanCallback)
            scanner = null
            hostGatt?.disconnect()
            hostGatt?.close()
            hostGatt = null
            hostCharacteristic = null
            connectedClients.clear()
            subscribed.clear()
            peerIds.clear()
            assignedPlayers.clear()
            outgoing.clear()
            sending = false
            nextPeerId = 1
            onConnectionStateChanged(BleConnectionState.DISCONNECTED)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
