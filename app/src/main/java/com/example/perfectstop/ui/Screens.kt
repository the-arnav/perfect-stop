package com.example.perfectstop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
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
                if (state.phase != GamePhase.MENU) IconButton(onClick = {
                    if (state.phase in listOf(GamePhase.HISTORY, GamePhase.LEADERBOARD)) viewModel.showMenu()
                    else viewModel.requestLeaveLobby()
                }) { GameIcon(GameGlyph.BACK, label = "Back") }
                Text(when (state.phase) {
                    GamePhase.MENU -> "Perfect Stop"
                    GamePhase.HISTORY -> "History"
                    GamePhase.LEADERBOARD -> "Leaderboard"
                    GamePhase.LOBBY -> if (state.role == GameRole.SOLO) "Practice" else "Lobby"
                    GamePhase.RESULTS -> "Results"
                    else -> if (state.reaction) "Signals" else if (state.isBlindMode) "Blind" else "Target"
                }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::toggleTheme) { GameIcon(if (state.darkTheme) GameGlyph.SUN else GameGlyph.MOON, label = if (state.darkTheme) "Switch to light theme" else "Switch to dark theme") }
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
    var editingName by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf("Target") }
    fun configure() { vm.setReaction(mode == "Reaction"); vm.toggleBlindMode(mode == "Blind") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(GameGlyph.PERSON, Modifier.size(22.dp))
            Text(name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { editingName = !editingName }) { GameIcon(GameGlyph.EDIT, label = "Edit player name") }
        }
        if (editingName) OutlinedTextField(name, onValueChange = { name = it.take(16); UserProfile.saveUsername(context, name) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Player name") }, singleLine = true, shape = RoundedCornerShape(4.dp))
        Modes(mode) { mode = it }
        ModeStage(mode)
        Action("Practice") { vm.initSolo(name); configure() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.initAsHost(name); configure() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(6.dp)) {
                GameIcon(GameGlyph.BLUETOOTH, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Host")
            }
            OutlinedButton(onClick = { vm.initAsClient(name) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(6.dp)) {
                GameIcon(GameGlyph.ARROW, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Join")
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NavigationTile("History", GameGlyph.HISTORY, Modifier.weight(1f), vm::showHistory)
            NavigationTile("Leaderboard", GameGlyph.TROPHY, Modifier.weight(1f), vm::showLeaderboard)
        }
    }
}

@Composable private fun NavigationTile(title: String, glyph: GameGlyph, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GameIcon(glyph, Modifier.size(26.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun ModeStage(mode: String, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (mode == "Reaction") SignalLights(-1, Modifier.fillMaxWidth().height(if (compact) 130.dp else 190.dp))
        else TimingDial(mode == "Blind", Modifier.size(if (compact) 130.dp else 190.dp))
        if (!compact) {
            Text(if (mode == "Reaction") "Signals" else mode, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
            if (mode != "Reaction") Text(if (mode == "Blind") "Hidden after 3.000 s" else "4.000–15.000 s",
                Modifier.padding(top = 6.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Toggle(label: String, checked: Boolean, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, { onChange() }, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable private fun Modes(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("Target", "Blind", "Reaction").forEach { mode ->
            val active = selected == mode
            Column(Modifier.weight(1f).selectable(active, role = Role.Tab, onClick = { onSelect(mode) })
                .padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GameIcon(when(mode) { "Target" -> GameGlyph.TIMER; "Blind" -> GameGlyph.BLIND; else -> GameGlyph.SIGNAL },
                    Modifier.size(22.dp), color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (mode == "Reaction") "Signals" else mode, fontSize = 13.sp, fontWeight = if(active) FontWeight.Bold else FontWeight.Normal)
                Box(Modifier.fillMaxWidth().height(2.dp).background(if(active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable private fun DifficultyControl(selected: BotDifficulty, onSelect: (BotDifficulty) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Difficulty", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp).selectableGroup()) {
            BotDifficulty.entries.forEach { level ->
                val active = level == selected
                Box(Modifier.weight(1f).clip(RoundedCornerShape(4.dp))
                    .background(if(active) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(level) }).heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(level.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp,
                        color = if(active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if(active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Slider(value = selected.ordinal.toFloat(), onValueChange = { onSelect(BotDifficulty.entries[it.roundToInt().coerceIn(0,2)]) },
            valueRange = 0f..2f, steps = 1, modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Bot difficulty"; stateDescription = selected.name.lowercase()
            })
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
        if (state.role != GameRole.CLIENT) ModeStage(if (state.reaction) "Reaction" else if (state.isBlindMode) "Blind" else "Target", compact = true)
        Text(if (state.role == GameRole.SOLO) "Line-up" else "Players", style = MaterialTheme.typography.titleMedium)
        state.players.forEach { player ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (player.id in listOf("b1", "b2")) Text(player.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    else GameIcon(GameGlyph.PERSON, Modifier.size(22.dp))
                }
                Text(player.name, Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Medium)
                Text(if (player.index == state.localPlayerIndex) "You" else if (state.role == GameRole.SOLO) "Bot" else if (player.isHost) "Host" else "Ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.role == GameRole.SOLO) {
            DifficultyControl(state.botDifficulty, vm::setBotDifficulty)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingButton("Sound", GameGlyph.SOUND, state.isSoundEnabled, Modifier.weight(1f), vm::toggleSound)
            SettingButton("Haptics", GameGlyph.HAPTIC, state.isHapticsEnabled, Modifier.weight(1f), vm::toggleHaptics)
        }
        if (state.role == GameRole.HOST) Toggle("Accept players automatically", state.isAutoAcceptEnabled, vm::toggleAutoAccept)
        if (state.role != GameRole.CLIENT) Action("Start", state.role == GameRole.SOLO || state.players.size >= 2, vm::startRound)
        else if (!state.isHostDisconnectedBannerVisible) Text("Waiting for host", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SettingButton(label: String, glyph: GameGlyph, checked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.semantics { stateDescription = if(checked) "On" else "Off" },
        shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameIcon(glyph, Modifier.size(20.dp), color = if(checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp)
                Text(if(checked) "On" else "Off", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GameIcon(GameGlyph.TROPHY, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text("${local?.score ?: 0}", fontSize = 48.sp, fontFamily = FontFamily.Monospace)
                Text("Points", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("#${local?.rank ?: 0}", fontSize = 28.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                    GameIcon(when(game.mode) { "Reaction" -> GameGlyph.SIGNAL; "Blind" -> GameGlyph.BLIND; else -> GameGlyph.TIMER }, Modifier.padding(end = 14.dp).size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if(game.mode == "Reaction") "Signals" else game.mode, style = MaterialTheme.typography.titleMedium)
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
