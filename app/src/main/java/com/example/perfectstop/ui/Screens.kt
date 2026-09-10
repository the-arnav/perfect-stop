package com.example.perfectstop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectstop.model.*
import com.example.perfectstop.viewmodel.GameViewModel
import com.example.perfectstop.viewmodel.GameUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

@Composable
fun PerfectStopApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(state.phase != GamePhase.MENU) {
        if (state.phase in listOf(GamePhase.HISTORY, GamePhase.LEADERBOARD)) viewModel.showMenu()
        else viewModel.requestLeaveLobby()
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.phase != GamePhase.MENU) TextButton(onClick = {
                    if (state.phase in listOf(GamePhase.HISTORY, GamePhase.LEADERBOARD)) viewModel.showMenu()
                    else viewModel.requestLeaveLobby()
                }) { Text("Back") }
                Text(when (state.phase) {
                    GamePhase.MENU -> "Perfect Stop"
                    GamePhase.HISTORY -> "History"
                    GamePhase.LEADERBOARD -> "Leaderboard"
                    GamePhase.LOBBY -> "Lobby"
                    GamePhase.RESULTS -> "Results"
                    else -> if (state.reaction) "Reaction" else if (state.isBlindMode) "Blind" else "Target"
                }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::toggleTheme) { Text(if (state.darkTheme) "Light" else "Dark") }
            }
            HorizontalDivider()
            when (state.phase) {
                GamePhase.MENU -> Menu(viewModel)
                GamePhase.LOBBY -> Lobby(viewModel, state)
                GamePhase.HISTORY -> History(state)
                GamePhase.LEADERBOARD -> Leaderboard(state)
                GamePhase.RESULTS -> Results(viewModel, state)
                else -> Game(viewModel, state)
            }
        }
        if (state.isLeaveDialogVisible) AlertDialog(onDismissRequest = viewModel::dismissLeaveDialog,
            title = { Text("Leave game?") }, text = { Text("This round will end. Completed games stay saved.") },
            confirmButton = { TextButton(onClick = viewModel::confirmLeaveLobby) { Text("Leave") } },
            dismissButton = { TextButton(onClick = viewModel::dismissLeaveDialog) { Text("Stay") } })
        state.pendingJoinRequest?.let { request ->
            AlertDialog(onDismissRequest = viewModel::rejectJoinRequest,
                title = { Text("Join request") }, text = { Text(request.playerName) },
                confirmButton = { TextButton(onClick = viewModel::acceptJoinRequest) { Text("Accept") } },
                dismissButton = { TextButton(onClick = viewModel::rejectJoinRequest) { Text("Decline") } })
        }
    }
}

@Composable private fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(4.dp)) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable private fun Menu(vm: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by rememberSaveable { mutableStateOf(UserProfile.getOrGenerateUsername(context)) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name, onValueChange = { name = it.take(16); UserProfile.saveUsername(context, name) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Player name") }, singleLine = true,
            shape = RoundedCornerShape(4.dp))
        Spacer(Modifier.height(8.dp))
        Action("Host game") { vm.initAsHost(name) }
        OutlinedButton(onClick = { vm.initAsClient(name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(4.dp)) { Text("Join game") }
        MenuRow("Practice") { vm.initSolo(name); vm.setReaction(false) }
        MenuRow("Reaction") { vm.initSolo(name); vm.setReaction(true) }
        MenuRow("History") { vm.showHistory() }
        MenuRow("Leaderboard") { vm.showLeaderboard() }
    }
}

@Composable private fun MenuRow(label: String, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            val color = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(18.dp)) {
                drawLine(color, Offset(size.width * .35f, size.height * .2f), Offset(size.width * .65f, size.height * .5f), 2.dp.toPx())
                drawLine(color, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .35f, size.height * .8f), 2.dp.toPx())
            }
        }
        HorizontalDivider()
    }
}

@Composable private fun Toggle(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, { onChange() }, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable private fun Modes(selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Target", "Blind", "Reaction").forEach { mode ->
            FilterChip(selected == mode, { onSelect(mode) }, label = { Text(mode, fontSize = 13.sp) }, shape = RoundedCornerShape(4.dp))
        }
    }
}

@Composable private fun Lobby(vm: GameViewModel, state: GameUiState) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isHostDisconnectedBannerVisible) {
            Text("Host disconnected", color = MaterialTheme.colorScheme.error)
            Action("Reconnect") { vm.initAsClient(UserProfile.getOrGenerateUsername(vm.getApplication())) }
        } else if (state.role != GameRole.SOLO) {
            Text(when (state.connectionState) {
                BleConnectionState.SCANNING -> "Finding host…"
                BleConnectionState.CONNECTING -> "Connecting…"
                BleConnectionState.CONNECTED -> "Connected"
                BleConnectionState.ADVERTISING -> "Waiting for players…"
                else -> "Bluetooth unavailable"
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.role != GameRole.CLIENT) Modes(if (state.reaction) "Reaction" else if (state.isBlindMode) "Blind" else "Target") {
            vm.setReaction(it == "Reaction"); vm.toggleBlindMode(it == "Blind")
        }
        if (state.reaction) SignalLights(-1, Modifier.fillMaxWidth().height(180.dp))
        state.players.forEach { player ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(player.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(if (player.index == state.localPlayerIndex) "You" else if (player.isHost) "Host" else "Ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
        if (state.role == GameRole.SOLO) {
            Text("Difficulty", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotDifficulty.entries.forEach { difficulty ->
                    FilterChip(state.botDifficulty == difficulty, { vm.setBotDifficulty(difficulty) }, label = { Text(difficulty.name.lowercase().replaceFirstChar { it.uppercase() }) }, shape = RoundedCornerShape(4.dp))
                }
            }
        }
        Toggle("Sound", state.isSoundEnabled, vm::toggleSound)
        Toggle("Haptics", state.isHapticsEnabled, vm::toggleHaptics)
        if (state.role == GameRole.HOST) Toggle("Accept players automatically", state.isAutoAcceptEnabled, vm::toggleAutoAccept)
        if (state.role != GameRole.CLIENT) Action("Start", state.role == GameRole.SOLO || state.players.size >= 2, vm::startRound)
        else if (!state.isHostDisconnectedBannerVisible) Text("Waiting for host", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun Game(vm: GameViewModel, state: GameUiState) {
    val player = state.players.firstOrNull { it.index == state.localPlayerIndex }
    val countdown = state.phase == GamePhase.COUNTDOWN
    val stopped = player?.isStopped == true
    val hidden = !state.reaction && state.isBlindMode && state.elapsedTimeMs >= 3000 && !stopped
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.reaction) SignalLights(if (countdown) state.signalStage else 2, Modifier.fillMaxWidth().height(260.dp), vm::onGreenDrawn)
        else {
            Text("Target", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${seconds(state.targetTimeMs.toLong())} s", fontFamily = FontFamily.Monospace, fontSize = 30.sp)
            Spacer(Modifier.height(48.dp))
        }
        Text(when {
            player?.stoppedTimeMs == -1L -> "False start"
            countdown && state.reaction -> if (state.signalStage == 0) "Ready" else "Hold"
            countdown -> state.countdownNumber.toString()
            hidden -> "—.———"
            else -> seconds(player?.stoppedTimeMs ?: state.elapsedTimeMs)
        }, fontSize = if (state.reaction && countdown || player?.stoppedTimeMs == -1L) 36.sp else 56.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
        if (stopped) Text("Waiting for players", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (hidden) Text("Blind", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(36.dp))
        val enabled = !stopped && (!countdown || state.reaction)
        Surface(color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                .onKeyEvent { event ->
                    if (enabled && event.type == KeyEventType.KeyDown && event.key in listOf(Key.Enter, Key.Spacebar, Key.DirectionCenter)) { vm.pressStopButton(); true } else false
                }.focusable(enabled)
                .semantics { role = Role.Button; if (!enabled) disabled(); onClick("Stop") { if (enabled) vm.pressStopButton(); enabled } }
                .pointerInput(enabled) { if (enabled) detectTapGestures(onPress = { vm.pressStopButton(); tryAwaitRelease() }) }) {
            Box(contentAlignment = Alignment.Center) { Text(if (stopped) "Stopped" else "Stop", fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Three vector signal towers. Position and state labels supplement colour. */
@Composable fun SignalLights(stage: Int, modifier: Modifier = Modifier, onGreenDrawn: () -> Unit = {}) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = when(stage) { 0 -> "Red lights. Ready"; 1 -> "Yellow lights. Hold"; 2 -> "Green lights. Go"; else -> "Three traffic lights" } }) {
        val towerW = minOf(size.width / 4.3f, size.height / 3.6f)
        val gap = towerW * .25f
        val left = (size.width - towerW * 3 - gap * 2) / 2
        val top = (size.height - towerW * 3.3f) / 2
        repeat(3) { col ->
            val x = left + col * (towerW + gap)
            drawRoundRect(Color(0xFF161616), Offset(x, top), Size(towerW, towerW * 3.3f), CornerRadius(6.dp.toPx()))
            drawRoundRect(outline, Offset(x, top), Size(towerW, towerW * 3.3f), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
            repeat(3) { row ->
                val center = Offset(x + towerW / 2, top + towerW * (.6f + row * 1.05f))
                val lit = row == stage
                val color = if (!lit) Color(0xFF303030) else when(row) { 0 -> Color(0xFFFF453A); 1 -> Color(0xFFFFC400); else -> Color(0xFF38D66B) }
                drawCircle(color, towerW * .32f, center)
                drawCircle(if (lit) Color.White.copy(alpha = .55f) else Color(0xFF555555), towerW * .32f, center, style = Stroke(1.dp.toPx()))
            }
        }
        if (stage == 2) onGreenDrawn()
    }
}

@Composable private fun PlayerResult(player: Player, reaction: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(player.rank.toString().padStart(2, '0'), fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, fontWeight = FontWeight.Medium)
            Text(if (player.stoppedTimeMs == -1L) "False start / no stop" else if (reaction) "${player.stoppedTimeMs} ms" else "${player.formattedStoppedTime} · ${player.formattedDelta}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${player.score} pts", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider()
}

@Composable private fun Results(vm: GameViewModel, state: GameUiState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val local = state.players.firstOrNull { it.index == state.localPlayerIndex }
        Text("${local?.score ?: 0} pts", fontSize = 48.sp, fontFamily = FontFamily.Monospace)
        if (!state.reaction) Text("Target ${seconds(state.targetTimeMs.toLong())} s", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.players.sortedBy { it.rank }.forEach { PlayerResult(it, state.reaction) }
        if (state.role != GameRole.CLIENT) Action("Play again", onClick = vm::playAgain)
        else Text("Waiting for host", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun History(state: GameUiState) {
    if (state.history.isEmpty()) { Text("No completed games", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val format = remember { SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 24.dp)) {
        items(state.history, key = { it.id }) { game ->
            var expanded by rememberSaveable(game.id) { mutableStateOf(false) }
            val local = game.players.firstOrNull { it.identity == game.localIdentity }
            Column {
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(game.mode, style = MaterialTheme.typography.titleMedium)
                        Text(format.format(Date(game.date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${local?.score ?: 0} pts", fontFamily = FontFamily.Monospace)
                }
                if (expanded) {
                    if (game.target > 0) Text("Target ${seconds(game.target.toLong())} s", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    game.players.sortedBy { it.rank }.forEach { PlayerResult(it, game.mode == "Reaction") }
                } else HorizontalDivider()
            }
        }
    }
}

@Composable private fun Leaderboard(state: GameUiState) {
    var mode by rememberSaveable { mutableStateOf("Target") }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Modes(mode) { mode = it }
        val rows = state.history.filter { it.mode == mode }.flatMap { it.players }
            .filterNot { it.identity in listOf("b1", "b2") }.groupBy { it.identity }.values
            .sortedWith(compareByDescending<List<Player>> { it.sumOf { p -> p.score.toLong() } }.thenBy { it.first().name })
        if (rows.isEmpty()) Text("No scores yet", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
            items(rows) { games ->
                val total = games.sumOf { it.score.toLong() }
                val rank = rows.count { other -> other.sumOf { it.score.toLong() } > total } + 1
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rank.toString().padStart(2, '0'), Modifier.width(36.dp), fontFamily = FontFamily.Monospace)
                    Column(Modifier.weight(1f)) {
                        Text(games.first().name, fontWeight = FontWeight.Medium)
                        Text("${games.size} games · ${total / games.size} avg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$total pts", fontFamily = FontFamily.Monospace)
                }
                HorizontalDivider()
            }
        }
    }
}
