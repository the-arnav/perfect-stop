package com.example.perfectstop.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectstop.audio.AudioHapticEngine
import com.example.perfectstop.ble.BleManager
import com.example.perfectstop.model.BleConnectionState
import com.example.perfectstop.model.BotDifficulty
import com.example.perfectstop.model.GamePhase
import com.example.perfectstop.model.GameRole
import com.example.perfectstop.model.JoinRequestNotification
import com.example.perfectstop.model.Player
import com.example.perfectstop.model.UserProfile
import com.example.perfectstop.sensor.TapDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.random.Random

data class GameUiState(
    val phase: GamePhase = GamePhase.MENU,
    val role: GameRole = GameRole.HOST,
    val localPlayerIndex: Int = 0,
    val players: List<Player> = emptyList(),
    val isBlindMode: Boolean = true,
    val targetTimeMs: Int = 7500,
    val countdownNumber: Int = 3,
    val elapsedTimeMs: Long = 0L,
    val pendingJoinRequest: JoinRequestNotification? = null,
    val winner: Player? = null,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val botDifficulty: BotDifficulty = BotDifficulty.VETERAN,
    val isSoundEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val isLeaveDialogVisible: Boolean = false,
    val isHostDisconnectedBannerVisible: Boolean = false,
    val isAutoAcceptEnabled: Boolean = true
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    val audioHaptics = AudioHapticEngine(application)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var bleManager: BleManager? = null
    val tapDetector: TapDetector

    private var matchJob: Job? = null
    private var countdownJob: Job? = null
    private var startEpochMs: Long = 0L

    init {
        tapDetector = TapDetector(application) { accelG, rssi ->
            handleTapDetected(accelG, rssi)
        }
        bleManager = BleManager(
            application,
            onPacketReceived = { bytes -> viewModelScope.launch { handlePacket(bytes) } },
            onRssiUpdated = { rssi -> tapDetector.updateHostRssi(rssi) },
            onConnectionStateChanged = { state -> viewModelScope.launch { handleConnectionStateChanged(state) } }
        ).apply {
            onPeerDisconnected = { peerId ->
                viewModelScope.launch {
                    if (_uiState.value.role == GameRole.HOST) {
                        _uiState.value = _uiState.value.copy(players = _uiState.value.players.filter { it.id != "peer_$peerId" })
                        resetLobby()
                        bleManager?.broadcastPacketFromHost(bleManager!!.buildRoundReset())
                        bleManager?.broadcastPacketFromHost(bleManager!!.buildRosterSync(_uiState.value.players))
                    }
                }
            }
            onReadyToTransmit = {
                // Instantly send join request as soon as client BLE channel opens
                if (_uiState.value.role == GameRole.CLIENT && _uiState.value.phase == GamePhase.LOBBY) {
                    sendJoinRequestFromClient()
                }
            }
        }
    }

    private fun handleConnectionStateChanged(state: BleConnectionState) {
        _uiState.value = _uiState.value.copy(connectionState = state)
        if (state == BleConnectionState.DISCONNECTED && _uiState.value.role == GameRole.CLIENT) {
            if (_uiState.value.phase != GamePhase.MENU) {
                resetLobby()
                _uiState.value = _uiState.value.copy(isHostDisconnectedBannerVisible = true)
            }
            if (_uiState.value.phase == GamePhase.RUNNING || _uiState.value.phase == GamePhase.LOBBY) {
                _uiState.value = _uiState.value.copy(isHostDisconnectedBannerVisible = true)
            }
        }
    }

    fun dismissHostDisconnectedBanner() {
        _uiState.value = _uiState.value.copy(isHostDisconnectedBannerVisible = false)
    }

    // --- Sound & Haptics Toggles ---

    fun toggleSound() {
        val next = !_uiState.value.isSoundEnabled
        audioHaptics.isSoundEnabled = next
        _uiState.value = _uiState.value.copy(isSoundEnabled = next)
    }

    fun toggleHaptics() {
        val next = !_uiState.value.isHapticsEnabled
        audioHaptics.isHapticsEnabled = next
        _uiState.value = _uiState.value.copy(isHapticsEnabled = next)
    }

    fun toggleAutoAccept() {
        _uiState.value = _uiState.value.copy(isAutoAcceptEnabled = !_uiState.value.isAutoAcceptEnabled)
    }

    fun setBotDifficulty(difficulty: BotDifficulty) {
        _uiState.value = _uiState.value.copy(botDifficulty = difficulty)
    }

    // --- Navigation & Leaving ---

    fun requestLeaveLobby() {
        _uiState.value = _uiState.value.copy(isLeaveDialogVisible = true)
    }

    fun dismissLeaveDialog() {
        _uiState.value = _uiState.value.copy(isLeaveDialogVisible = false)
    }

    fun confirmLeaveLobby() {
        matchJob?.cancel()
        countdownJob?.cancel()
        tapDetector.stop()
        bleManager?.stop()

        _uiState.value = GameUiState(
            phase = GamePhase.MENU,
            isSoundEnabled = _uiState.value.isSoundEnabled,
            isHapticsEnabled = _uiState.value.isHapticsEnabled,
            botDifficulty = _uiState.value.botDifficulty
        )
    }

    // --- Role Initialization ---

    fun initAsHost(playerName: String) {
        val finalName = if (playerName.isBlank()) UserProfile.getOrGenerateUsername(getApplication()) else playerName
        UserProfile.saveUsername(getApplication(), finalName)

        val host = Player(0, "host", finalName, isHost = true)
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.LOBBY,
            role = GameRole.HOST,
            localPlayerIndex = 0,
            players = listOf(host),
            isHostDisconnectedBannerVisible = false
        )
        bleManager?.startHostAdvertising()
        tapDetector.start()
    }

    fun initAsClient(playerName: String) {
        val finalName = if (playerName.isBlank()) UserProfile.getOrGenerateUsername(getApplication()) else playerName
        UserProfile.saveUsername(getApplication(), finalName)

        val client = Player(1, "client", finalName, isHost = false)
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.LOBBY,
            role = GameRole.CLIENT,
            localPlayerIndex = 1,
            players = listOf(client),
            isHostDisconnectedBannerVisible = false
        )
        tapDetector.start()
        bleManager?.startClientScanning()
    }

    fun initSolo(playerName: String) {
        val finalName = if (playerName.isBlank()) UserProfile.getOrGenerateUsername(getApplication()) else playerName
        UserProfile.saveUsername(getApplication(), finalName)

        val p0 = Player(0, "p0", finalName, isHost = true)
        val b1 = Player(1, "b1", "CyberBot α")
        val b2 = Player(2, "b2", "NanoBot β")
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.LOBBY,
            role = GameRole.SOLO,
            localPlayerIndex = 0,
            players = listOf(p0, b1, b2),
            isHostDisconnectedBannerVisible = false
        )
    }

    fun toggleBlindMode(enabled: Boolean) {
        if (_uiState.value.role == GameRole.CLIENT) return
        _uiState.value = _uiState.value.copy(isBlindMode = enabled)
    }

    // --- Tap Pairing & Roster Management ---

    fun sendJoinRequestFromClient() {
        if (_uiState.value.role != GameRole.CLIENT) return
        val local = _uiState.value.players.firstOrNull { it.index == _uiState.value.localPlayerIndex }
        val name = local?.name ?: UserProfile.getOrGenerateUsername(getApplication())
        val pkt = bleManager?.buildJoinRequest(Random.nextInt(1, 255), name) ?: return
        bleManager?.sendPacketFromClient(pkt)
    }

    private fun handleTapDetected(accelG: Double, rssi: Int) {
        if (_uiState.value.phase != GamePhase.LOBBY) return
        if (_uiState.value.role == GameRole.CLIENT) {
            sendJoinRequestFromClient()
        }
    }

    fun simulateTapPairing() {
        if (_uiState.value.role == GameRole.CLIENT) sendJoinRequestFromClient()
    }

    private fun addPlayerToHost(playerName: String, peerId: Int = -1) {
        if (peerId < 0) return
        val existing = _uiState.value.players.firstOrNull { it.id == "peer_$peerId" }
        if (existing != null) {
            bleManager?.sendToPeer(peerId, bleManager!!.buildJoinAccept(existing.index, _uiState.value.players.size))
            bleManager?.broadcastPacketFromHost(bleManager!!.buildRosterSync(_uiState.value.players))
            return
        }
        if (_uiState.value.players.size >= 4) return
        val current = _uiState.value.players.toMutableList()
        val newIndex = (1..3).firstOrNull { index -> current.none { it.index == index } } ?: return
        val newPlayer = Player(newIndex, "peer_$peerId", playerName)
        bleManager?.assignPlayer(peerId, newIndex)
        current.add(newPlayer)

        _uiState.value = _uiState.value.copy(players = current)
        audioHaptics.triggerPairingBuzz()

        viewModelScope.launch {
            // 1. Send Join Accept packet
            val acceptPkt = bleManager?.buildJoinAccept(newIndex, current.size)
            acceptPkt?.let { bleManager?.sendToPeer(peerId, it) }

            delay(35) // Small inter-packet gap so BLE controller does not collide

            // 2. Broadcast full Roster Sync to all peers
            val rosterPkt = bleManager?.buildRosterSync(current)
            rosterPkt?.let { bleManager?.broadcastPacketFromHost(it, burstCount = 3) }
        }
    }

    fun acceptJoinRequest() {
        val req = _uiState.value.pendingJoinRequest ?: return
        _uiState.value = _uiState.value.copy(pendingJoinRequest = null)
        addPlayerToHost(req.playerName, req.playerId)
    }

    fun rejectJoinRequest() {
        _uiState.value = _uiState.value.copy(pendingJoinRequest = null)
    }

    // --- Packet Handling ---

    private fun handlePacket(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val opcode = bytes[0].toInt() and 0xFF

        when (opcode) {
            BleManager.OPCODE_JOIN_REQUEST -> {
                if (_uiState.value.role == GameRole.HOST && _uiState.value.phase == GamePhase.LOBBY) {
                    val pId = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 1
                    if (bytes.size < 3 || bytes.size != 3 + (bytes[2].toInt() and 255)) return
                    if (_uiState.value.players.any { it.id == "peer_$pId" }) {
                        addPlayerToHost("", pId)
                        return
                    }
                    if (_uiState.value.players.size >= 4) return
                    val nameLen = if (bytes.size > 2) bytes[2].toInt() and 0xFF else 0
                    val rawName = if (bytes.size >= 3 + nameLen && nameLen > 0) {
                        String(bytes, 3, nameLen, Charsets.UTF_8)
                    } else "Player_$pId"

                    // If same name, NEVER drop! Append discriminator suffix
                    var uniqueName = rawName
                    var suffix = 2
                    while (_uiState.value.players.any { it.name.equals(uniqueName, ignoreCase = true) }) {
                        uniqueName = "$rawName ($suffix)"
                        suffix++
                    }

                    if (_uiState.value.isAutoAcceptEnabled) {
                        addPlayerToHost(uniqueName, pId)
                    } else {
                        audioHaptics.triggerPairingBuzz()
                        _uiState.value = _uiState.value.copy(
                            pendingJoinRequest = JoinRequestNotification(pId, uniqueName, tapDetector.currentHostRssi)
                        )
                    }
                }
            }

            BleManager.OPCODE_JOIN_ACCEPT -> {
                if (_uiState.value.role == GameRole.CLIENT && bytes.size >= 2) {
                    val assignedIdx = bytes[1].toInt() and 0xFF
                    audioHaptics.triggerPairingBuzz()
                    _uiState.value = _uiState.value.copy(localPlayerIndex = assignedIdx)
                }
            }

            BleManager.OPCODE_ROSTER_SYNC -> {
                if (_uiState.value.role == GameRole.CLIENT) {
                    val syncedList = bleManager?.parseRosterSync(bytes)
                    if (!syncedList.isNullOrEmpty()) {
                        val myIdx = _uiState.value.localPlayerIndex
                        _uiState.value = _uiState.value.copy(
                            players = syncedList,
                            localPlayerIndex = myIdx
                        )
                    }
                }
            }

            BleManager.OPCODE_START_ROUND -> {
                if (_uiState.value.role == GameRole.CLIENT && _uiState.value.phase == GamePhase.LOBBY && bytes.size >= 8) {
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                    val target = buf.getShort(1).toInt() and 0xFFFF
                    val hostNow = android.os.SystemClock.elapsedRealtime() + (bleManager?.clockOffsetMs ?: 0)
                    val low = buf.getInt(3).toLong() and 0xFFFFFFFFL
                    val cycle = 1L shl 32
                    val candidate = (hostNow and (cycle - 1).inv()) or low
                    val deadline = listOf(candidate - cycle, candidate, candidate + cycle).minBy { abs(it - hostNow) }
                    val countdownMs = (deadline - hostNow).coerceIn(0, 5000).toInt()
                    val blind = buf.get(7).toInt() != 0
                    startRoundSequence(target, countdownMs, blind)
                }
            }

            BleManager.OPCODE_PLAYER_STOP -> {
                if (bytes.size >= 6) {
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                    val pIdx = buf.get(1).toInt() and 0xFF
                    val stopped = buf.getInt(2).toLong() and 0xFFFFFFFFL
                    recordPlayerStop(pIdx, stopped)

                    if (_uiState.value.role == GameRole.HOST) {
                        // Re-broadcast stop to other peers
                        bleManager?.broadcastPacketFromHost(bytes, burstCount = 2)
                    }
                }
            }

            BleManager.OPCODE_ROUND_RESET -> {
                if (_uiState.value.role == GameRole.CLIENT) resetLobby()
            }
        }
    }

    // --- Synchronized Round Sequence ---

    fun startRound() {
        if (_uiState.value.role == GameRole.CLIENT || _uiState.value.phase != GamePhase.LOBBY) return
        if (_uiState.value.role == GameRole.HOST && _uiState.value.players.size < 2) return
        val targets = listOf(5000, 6500, 7500, 8000, 9250, 10000)
        val target = targets.random()
        val countdownDurationMs = 3000

        // Broadcast to clients with burst redundancy
        val pkt = bleManager?.buildStartRound(target, (android.os.SystemClock.elapsedRealtime() + countdownDurationMs).toInt(), _uiState.value.isBlindMode)
        pkt?.let { bleManager?.broadcastPacketFromHost(it, burstCount = 3) }

        // Start Host countdown simultaneously
        startRoundSequence(target, countdownDurationMs, _uiState.value.isBlindMode)
    }

    private fun startRoundSequence(target: Int, countdownMs: Int, blindMode: Boolean) {
        val resetPlayers = _uiState.value.players.map { it.withReset() }
        startEpochMs = android.os.SystemClock.elapsedRealtime() + countdownMs

        _uiState.value = _uiState.value.copy(
            phase = GamePhase.COUNTDOWN,
            players = resetPlayers,
            targetTimeMs = target,
            isBlindMode = blindMode,
            countdownNumber = 3,
            elapsedTimeMs = 0L
        )

        audioHaptics.playBeepCountdown()

        // Latency-compensated countdown anchored to startEpochMs
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remainingMs = startEpochMs - android.os.SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) {
                    // GO!
                    audioHaptics.playBeepGo()
                    _uiState.value = _uiState.value.copy(countdownNumber = 0)
                    beginRunningStopwatch()
                    break
                } else if (remainingMs <= 1000L && _uiState.value.countdownNumber != 1) {
                    audioHaptics.playBeepCountdown()
                    _uiState.value = _uiState.value.copy(countdownNumber = 1)
                } else if (remainingMs in 1001L..2000L && _uiState.value.countdownNumber != 2) {
                    audioHaptics.playBeepCountdown()
                    _uiState.value = _uiState.value.copy(countdownNumber = 2)
                } else if (remainingMs > 2000L && _uiState.value.countdownNumber != 3) {
                    _uiState.value = _uiState.value.copy(countdownNumber = 3)
                }
                delay(15)
            }
        }
    }

    private fun beginRunningStopwatch() {
        _uiState.value = _uiState.value.copy(phase = GamePhase.RUNNING)

        matchJob?.cancel()
        matchJob = viewModelScope.launch {
            var lastTickSec = 0L
            while (_uiState.value.phase == GamePhase.RUNNING || _uiState.value.phase == GamePhase.PLAYER_STOPPED) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - startEpochMs
                val safeElapsed = if (elapsed < 0L) 0L else elapsed
                _uiState.value = _uiState.value.copy(elapsedTimeMs = safeElapsed)

                // Rhythmic tick every 500ms
                if (safeElapsed / 500 != lastTickSec) {
                    lastTickSec = safeElapsed / 500
                    audioHaptics.playTick()
                    audioHaptics.triggerSubtleTick()
                }

                // AI Bot stops in solo mode
                if (_uiState.value.role == GameRole.SOLO) {
                    val varianceRange = _uiState.value.botDifficulty.varianceMs
                    _uiState.value.players.filter { it.index != _uiState.value.localPlayerIndex && !it.isStopped }.forEach { bot ->
                        val botOffset = if (bot.index == 1) -varianceRange / 2 else varianceRange / 3
                        val botTarget = _uiState.value.targetTimeMs + botOffset
                        if (safeElapsed >= botTarget) {
                            recordPlayerStop(bot.index, safeElapsed)
                        }
                    }
                }

                // Grace timeout: 4 seconds past target
                if (_uiState.value.role != GameRole.CLIENT && safeElapsed >= _uiState.value.targetTimeMs + 4000L) {
                    _uiState.value.players.filter { !it.isStopped }.forEach {
                        if (_uiState.value.role == GameRole.HOST) {
                            bleManager?.broadcastPacketFromHost(bleManager!!.buildPlayerStop(it.index, safeElapsed))
                        }
                        recordPlayerStop(it.index, safeElapsed)
                    }
                }

                delay(12)
            }
        }
    }

    fun pressStopButton() {
        if (_uiState.value.phase != GamePhase.RUNNING) return
        val stopped = (android.os.SystemClock.elapsedRealtime() - startEpochMs).coerceAtLeast(0)

        audioHaptics.playStopSlam()
        audioHaptics.triggerMechanicalStopClick()

        _uiState.value = _uiState.value.copy(phase = GamePhase.PLAYER_STOPPED)
        recordPlayerStop(_uiState.value.localPlayerIndex, stopped)

        val pkt = bleManager?.buildPlayerStop(_uiState.value.localPlayerIndex, stopped)
        pkt?.let {
            if (_uiState.value.role == GameRole.HOST) {
                bleManager?.broadcastPacketFromHost(it, burstCount = 2)
            } else {
                bleManager?.sendPacketFromClient(it)
            }
        }
    }

    private fun recordPlayerStop(pIdx: Int, stoppedMs: Long) {
        if (_uiState.value.phase !in listOf(GamePhase.RUNNING, GamePhase.PLAYER_STOPPED)) return
        if (stoppedMs !in 0..(_uiState.value.targetTimeMs + 4500L)) return
        if (_uiState.value.players.none { it.index == pIdx && !it.isStopped }) return
        val updated = _uiState.value.players.map { p ->
            if (p.index == pIdx) {
                p.copy(
                    stoppedTimeMs = stoppedMs,
                    deltaMs = stoppedMs - _uiState.value.targetTimeMs,
                    isStopped = true
                )
            } else {
                p
            }
        }

        _uiState.value = _uiState.value.copy(players = updated)

        val allStopped = updated.isNotEmpty() && updated.all { it.isStopped }
        if (allStopped && _uiState.value.phase != GamePhase.RESULTS) {
            transitionToResults()
        }
    }

    private fun transitionToResults() {
        matchJob?.cancel()
        val list = _uiState.value.players
        val sorted = list.sortedBy { abs(it.deltaMs ?: 999999L) }

        val rankedList = list.map { p ->
            val rank = sorted.indexOfFirst { it.index == p.index } + 1
            p.copy(rank = rank)
        }

        audioHaptics.playFanfare()
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.RESULTS,
            players = rankedList,
            winner = rankedList.minByOrNull { abs(it.deltaMs ?: 999999L) }
        )
    }

    fun playAgain() {
        if (_uiState.value.role == GameRole.CLIENT || _uiState.value.phase != GamePhase.RESULTS) return
        val pkt = bleManager?.buildRoundReset()
        pkt?.let { bleManager?.broadcastPacketFromHost(it, burstCount = 3) }
        resetLobby()
    }

    private fun resetLobby() {
        matchJob?.cancel()
        countdownJob?.cancel()
        val resetList = _uiState.value.players.map { it.withReset() }
        _uiState.value = _uiState.value.copy(
            phase = GamePhase.LOBBY,
            players = resetList,
            elapsedTimeMs = 0L,
            winner = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        tapDetector.stop()
        bleManager?.stop()
        audioHaptics.release()
    }
}
