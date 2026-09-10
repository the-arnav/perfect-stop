package com.example.perfectstop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.perfectstop.model.BleConnectionState
import com.example.perfectstop.model.BotDifficulty
import com.example.perfectstop.model.GamePhase
import com.example.perfectstop.model.GameRole
import com.example.perfectstop.model.Player
import com.example.perfectstop.viewmodel.GameViewModel

// Colors
val BgDark = Color(0xFF12131A)
val SurfaceCard = Color(0xFF1B1D28)
val SurfaceElevated = Color(0xFF242738)
val BorderColor = Color(0xFF2E3248)
val NeonCyan = Color(0xFF6ADDE1)
val HotPink = Color(0xFFFF7897)
val NeonYellow = Color(0xFFF0CF79)
val NeonGreen = Color(0xFF79DEAD)
val NeonPurple = Color(0xFFA855F7)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFADB3C9)

@Composable
fun PerfectStopApp(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Root Surface covers 100% of the display edge-to-edge with the dark theme color
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgDark
    ) {
        // Content is inset safely below status bar and above navigation bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            when (state.phase) {
                GamePhase.MENU -> MenuScreen(viewModel)
                GamePhase.LOBBY -> LobbyScreen(viewModel)
                GamePhase.COUNTDOWN, GamePhase.RUNNING, GamePhase.PLAYER_STOPPED -> GameScreen(viewModel)
                GamePhase.RESULTS -> ResultsScreen(viewModel)
            }

            // Leave Match Confirmation Dialog
            if (state.isLeaveDialogVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissLeaveDialog() },
                    title = { Text("LEAVE LOBBY?", color = HotPink, fontWeight = FontWeight.Black) },
                    text = { Text("Are you sure you want to leave? You will be disconnected from the match.", color = TextPrimary) },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmLeaveLobby() },
                            colors = ButtonDefaults.buttonColors(containerColor = HotPink)
                        ) {
                            Text("LEAVE", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.dismissLeaveDialog() }) {
                            Text("STAY", color = TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = SurfaceCard,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Top App Bar with Mute & Exit (Smoothly Blended & Structured)
// -------------------------------------------------------------
@Composable
fun TopActionBar(
    title: String,
    viewModel: GameViewModel,
    showExit: Boolean = true
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showExit) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .clickable { viewModel.requestLeaveLobby() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "✕", fontSize = 16.sp, fontWeight = FontWeight.Black, color = HotPink)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = NeonCyan
                )
            }

            // Sound and Haptics Mute Controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .clickable { viewModel.toggleSound() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (state.isSoundEnabled) "🔊" else "🔇", fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .clickable { viewModel.toggleHaptics() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (state.isHapticsEnabled) "📳" else "📴", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle gradient divider smoothly fading into background elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.5f), BorderColor.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        // Bluetooth / Connection Status Banner
        ConnectionStatusBanner(state.connectionState, state.isHostDisconnectedBannerVisible) {
            viewModel.dismissHostDisconnectedBanner()
        }
    }
}

// -------------------------------------------------------------
// Sleek Minimalist Stopwatch Logo Vector
// -------------------------------------------------------------
@Composable
fun StopwatchLogo(
    modifier: Modifier = Modifier,
    ringColor: Color = Color.White,
    accentColor: Color = NeonCyan
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.54f
        val r = w * 0.35f
        val strokeWidth = w * 0.075f

        // Outer stopwatch ring
        drawCircle(
            color = ringColor,
            radius = r,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )

        // Crown stem
        val stemW = w * 0.08f
        val stemTop = cy - r - (w * 0.12f)
        val stemH = w * 0.12f
        drawRect(
            color = ringColor,
            topLeft = androidx.compose.ui.geometry.Offset(cx - stemW / 2f, stemTop),
            size = androidx.compose.ui.geometry.Size(stemW, stemH)
        )

        // Crown top pusher button
        val btnW = w * 0.30f
        val btnH = w * 0.08f
        drawRoundRect(
            color = accentColor,
            topLeft = androidx.compose.ui.geometry.Offset(cx - btnW / 2f, stemTop - btnH),
            size = androidx.compose.ui.geometry.Size(btnW, btnH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(btnH / 2f, btnH / 2f)
        )

        // Center stop needle pointing to 12 o'clock
        val needleLen = r * 0.65f
        drawLine(
            color = accentColor,
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(cx, cy - needleLen),
            strokeWidth = strokeWidth * 0.85f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Center pivot pin
        drawCircle(
            color = ringColor,
            radius = strokeWidth * 0.7f,
            center = androidx.compose.ui.geometry.Offset(cx, cy)
        )

        // Cardinal tick marks
        val tickLen = r * 0.22f
        val tickStroke = strokeWidth * 0.5f
        // 3 o'clock
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset(cx + r - strokeWidth / 2f, cy),
            end = androidx.compose.ui.geometry.Offset(cx + r - strokeWidth / 2f - tickLen, cy),
            strokeWidth = tickStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // 9 o'clock
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset(cx - r + strokeWidth / 2f, cy),
            end = androidx.compose.ui.geometry.Offset(cx - r + strokeWidth / 2f + tickLen, cy),
            strokeWidth = tickStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        // 6 o'clock
        drawLine(
            color = ringColor.copy(alpha = 0.6f),
            start = androidx.compose.ui.geometry.Offset(cx, cy + r - strokeWidth / 2f),
            end = androidx.compose.ui.geometry.Offset(cx, cy + r - strokeWidth / 2f - tickLen),
            strokeWidth = tickStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun ConnectionStatusBanner(
    connectionState: BleConnectionState,
    isHostDisconnected: Boolean,
    onDismissHostDisconnected: () -> Unit
) {
    if (isHostDisconnected) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(HotPink.copy(alpha = 0.2f))
                .border(1.dp, HotPink, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠️ Host disconnected from session!",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = HotPink
            )
            Text(
                text = "DISMISS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = HotPink,
                modifier = Modifier.clickable { onDismissHostDisconnected() }
            )
        }
    } else if (connectionState == BleConnectionState.BLUETOOTH_DISABLED) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeonYellow.copy(alpha = 0.2f))
                .border(1.dp, NeonYellow, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "⚠️ Bluetooth is disabled. Please enable Bluetooth in Settings.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow
            )
        }
    } else if (connectionState == BleConnectionState.SCANNING) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeonCyan.copy(alpha = 0.15f))
                .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "📡 Scanning for Host phone... Tap phones to pair!",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
        }
    } else if (connectionState == BleConnectionState.CONNECTED) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NeonGreen.copy(alpha = 0.15f))
                .border(1.dp, NeonGreen, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "⚡ Connected to Host (GATT active)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = NeonGreen
            )
        }
    }
}

// -------------------------------------------------------------
// 1. MENU SCREEN
// -------------------------------------------------------------
@Composable
fun MenuScreen(viewModel: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(com.example.perfectstop.model.UserProfile.getOrGenerateUsername(context)) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopActionBar(title = "PERFECT STOP", viewModel = viewModel, showExit = false)

        Spacer(modifier = Modifier.height(16.dp))

        // Title Graphic with Sleek Stopwatch Logo
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceElevated)
                    .border(2.dp, NeonCyan, RoundedCornerShape(22.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                StopwatchLogo(
                    modifier = Modifier.fillMaxSize(),
                    ringColor = Color.White,
                    accentColor = NeonCyan
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "PERFECT STOP",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = TextPrimary
            )
            Text(
                text = "OFFLINE BLUETOOTH MULTIPLAYER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Name input with persistence
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(16)
                com.example.perfectstop.model.UserProfile.saveUsername(context, it)
            },
            label = { Text("YOUR CALLSIGN / NAME", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeButton(
                title = "CREATE LOBBY (HOST)",
                subtitle = "Advertise BLE room & accept tap joins",
                accentColor = NeonCyan,
                icon = "📡"
            ) {
                viewModel.initAsHost(if (name.isBlank()) "Host" else name)
            }

            ModeButton(
                title = "JOIN LOBBY (CLIENT)",
                subtitle = "Tap phone against Host to pair",
                accentColor = HotPink,
                icon = "📱"
            ) {
                viewModel.initAsClient(if (name.isBlank()) "Challenger" else name)
            }

            ModeButton(
                title = "SOLO PRACTICE",
                subtitle = "Train against CyberBots offline",
                accentColor = NeonYellow,
                icon = "🤖"
            ) {
                viewModel.initSolo(if (name.isBlank()) "Solo Pro" else name)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun ModeButton(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(2.dp, accentColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = accentColor)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

// -------------------------------------------------------------
// 2. LOBBY SCREEN
// -------------------------------------------------------------
@Composable
fun LobbyScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isHost = state.role == GameRole.HOST || state.role == GameRole.SOLO
    val isSolo = state.role == GameRole.SOLO

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopActionBar(
                title = if (isHost) "HOST LOBBY" else (if (isSolo) "SOLO ARENA" else "CLIENT LOBBY"),
                viewModel = viewModel,
                showExit = true
            )

            // Pulsing Tap Graphic
            TapGraphicBox(
                isHost = isHost,
                onSimulateTap = { viewModel.simulateTapPairing() },
                isCapped = state.players.size >= 4
            )

            // Bot Difficulty selector in Solo mode
            if (isSolo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BotDifficulty.values().forEach { diff ->
                        val selected = state.botDifficulty == diff
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonYellow else Color.Transparent)
                                .clickable { viewModel.setBotDifficulty(diff) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = diff.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selected) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            // Player Roster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.5.dp, BorderColor, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PLAYERS IN ROOM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${state.players.size} / 4",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.players.size >= 4) HotPink else NeonCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.players) { player ->
                            val isYou = player.index == state.localPlayerIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isYou) SurfaceElevated else BgDark)
                                    .border(1.dp, if (isYou) NeonCyan else BorderColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = if (player.isHost) "👑" else "🎮", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = player.name + if (isYou) " (YOU)" else "",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NeonGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "READY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Host Auto-Accept Joins Toggle
            if (isHost && !isSolo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .border(1.5.dp, if (state.isAutoAcceptEnabled) NeonCyan else BorderColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "AUTO-ACCEPT JOINS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "Instant 1-tap join without prompt", fontSize = 10.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = state.isAutoAcceptEnabled,
                        onCheckedChange = { viewModel.toggleAutoAccept() },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.3f))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Blind Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.5.dp, if (state.isBlindMode) NeonPurple else BorderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "BLIND MODE", fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(text = "Digits blur at 03.000s! Count in your head.", fontSize = 10.sp, color = TextSecondary)
                }
                Switch(
                    checked = state.isBlindMode,
                    onCheckedChange = { if (isHost) viewModel.toggleBlindMode(it) },
                    enabled = isHost,
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonPurple, checkedTrackColor = NeonPurple.copy(alpha = 0.3f))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start Round Button
            if (isHost) {
                Button(
                    onClick = { viewModel.startRound() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text(
                        text = "START ROUND",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = Color.Black
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated)
                        .border(1.5.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "WAITING FOR HOST TO START...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Host Confirmation Modal Sheet
        if (state.pendingJoinRequest != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(SurfaceCard)
                        .border(2.dp, NeonCyan, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "📱💥", fontSize = 36.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PHYSICAL TAP DETECTED!",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${state.pendingJoinRequest?.playerName} tapped their phone and wants to join!",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.rejectJoinRequest() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, HotPink)
                        ) {
                            Text("NO", fontWeight = FontWeight.Black, color = HotPink)
                        }
                        Button(
                            onClick = { viewModel.acceptJoinRequest() },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                        ) {
                            Text("YES (ADD)", fontWeight = FontWeight.Black, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TapGraphicBox(onSimulateTap: () -> Unit, isCapped: Boolean = false, isHost: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "tapAnim")
    val translation by infiniteTransition.animateFloat(
        initialValue = 26f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trans"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.5.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.height(72.dp).width(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left Phone
            Box(
                modifier = Modifier
                    .offset(x = (-translation).dp)
                    .rotate(14f)
                    .size(34.dp, 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .border(2.dp, NeonCyan, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📱", fontSize = 15.sp)
            }
            // Right Phone
            Box(
                modifier = Modifier
                    .offset(x = translation.dp)
                    .rotate(-14f)
                    .size(34.dp, 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .border(2.dp, HotPink, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📱", fontSize = 15.sp)
            }
        }
        Text(
            text = if (isCapped) "Room is full (4/4 players)" else if (isHost) "Ask friends to select Join Lobby nearby" else "Connecting automatically • Retry pairing below",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCapped) HotPink else TextSecondary
        )
        if (!isCapped && !isHost) {
            Button(
                onClick = onSimulateTap,
                colors = ButtonDefaults.buttonColors(containerColor = NeonYellow),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = "⚡ TAP PHONES / PAIR NOW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 3. GAME SCREEN
// -------------------------------------------------------------
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isCountdown = state.phase == GamePhase.COUNTDOWN
    val isRunning = state.phase == GamePhase.RUNNING
    val isStopped = state.phase == GamePhase.PLAYER_STOPPED
    val localPlayer = state.players.firstOrNull { it.index == state.localPlayerIndex }

    val shouldBlur = state.isBlindMode && isRunning && state.elapsedTimeMs >= 3000L

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopActionBar(
                title = "MATCH ACTIVE",
                viewModel = viewModel,
                showExit = true
            )

            // Target Time Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(2.dp, if (isCountdown) NeonCyan else NeonYellow, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isCountdown) "ROLLING TARGET..." else "TARGET TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isCountdown) NeonCyan else NeonYellow,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "%.3fs", state.targetTimeMs / 1000.0),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            }

            // Center Stopwatch Arena
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isBlindMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (shouldBlur) HotPink.copy(alpha = 0.2f) else NeonPurple.copy(alpha = 0.2f))
                            .border(1.dp, if (shouldBlur) HotPink else NeonPurple, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (shouldBlur) "BLIND ACTIVE: COUNT IN YOUR HEAD!" else "BLIND MODE: BLURS AT 03.000s",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (shouldBlur) HotPink else NeonPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Digital Clock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .border(2.dp, if (shouldBlur) HotPink else NeonCyan, RoundedCornerShape(20.dp))
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ms = if (isStopped) (localPlayer?.stoppedTimeMs ?: state.elapsedTimeMs) else state.elapsedTimeMs
                    val mins = ms / 60000
                    val secs = (ms % 60000) / 1000
                    val millis = ms % 1000
                    val formatted = String.format(java.util.Locale.US, "%02d:%02d.%03d", mins, secs, millis)

                    Text(
                        text = formatted,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isStopped) NeonGreen else if (shouldBlur) HotPink else NeonCyan,
                        modifier = Modifier.blur(if (shouldBlur) 16.dp else 0.dp)
                    )

                    if (shouldBlur) {
                        Text(
                            text = "??:??.???",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = HotPink
                        )
                    }
                }

                // Locked In Banner
                if (isStopped) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceElevated)
                            .border(1.5.dp, NeonGreen, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "LOCKED IN: ${localPlayer?.formattedStoppedTime}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Delta: ${localPlayer?.formattedDelta}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = NeonYellow)
                            Text(text = "Waiting for opponents...", fontSize = 11.sp, color = NeonCyan)
                        }
                    }
                }
            }

            // Giant Tactile STOP Button
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Button(
                onClick = { viewModel.pressStopButton() },
                enabled = isRunning,
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .offset(y = if (isPressed) 6.dp else 0.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) HotPink else SurfaceElevated,
                    disabledContainerColor = SurfaceElevated
                )
            ) {
                Text(
                    text = if (isStopped) "LOCKED IN!" else "STOP!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = if (isRunning) Color.White else TextSecondary
                )
            }
        }

        // Countdown Overlay (3... 2... 1... GO!)
        if (isCountdown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.countdownNumber > 0) "${state.countdownNumber}" else "GO!",
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (state.countdownNumber == 0) NeonGreen else NeonCyan
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 4. RESULTS SCREEN
// -------------------------------------------------------------
@Composable
fun ResultsScreen(viewModel: GameViewModel) {
    val state by viewModel.uiState.collectAsState()
    val isHost = state.role == GameRole.HOST || state.role == GameRole.SOLO
    val sorted = state.players.sortedBy { kotlin.math.abs(it.deltaMs ?: 999999L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        TopActionBar(
            title = "RESULTS",
            viewModel = viewModel,
            showExit = true
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TARGET: ${String.format(java.util.Locale.US, "%.3fs", state.targetTimeMs / 1000.0)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NeonYellow
            )
        }

        // Podium View
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            val p2 = sorted.getOrNull(1)
            val p1 = sorted.getOrNull(0)
            val p3 = sorted.getOrNull(2)

            if (p2 != null) {
                PodiumCol(player = p2, rank = "2ND", heightDp = 75, color = NeonCyan, modifier = Modifier.weight(1f))
            }
            if (p1 != null) {
                PodiumCol(player = p1, rank = "1ST 👑", heightDp = 115, color = NeonGreen, modifier = Modifier.weight(1f))
            }
            if (p3 != null) {
                PodiumCol(player = p3, rank = "3RD", heightDp = 55, color = NeonYellow, modifier = Modifier.weight(1f))
            }
        }

        // Leaderboard List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .border(1.5.dp, BorderColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorted) { player ->
                    val isYou = player.index == state.localPlayerIndex
                    val isWin = player.rank == 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isWin) NeonGreen.copy(alpha = 0.1f) else SurfaceElevated)
                            .border(1.dp, if (isWin) NeonGreen else BorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${player.rank} ${player.name}" + if (isYou) " (YOU)" else "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(text = "Time: ${player.formattedStoppedTime}", fontSize = 11.sp, color = TextSecondary)
                        }
                        Text(
                            text = player.formattedDelta,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isWin) NeonGreen else NeonYellow
                        )
                    }
                }
            }
        }

        // Play Again Button
        if (isHost) {
            Button(
                onClick = { viewModel.playAgain() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text(text = "PLAY AGAIN", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
        } else {
            Text(text = "WAITING FOR HOST TO RESET...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
        }
    }
}

@Composable
fun PodiumCol(player: Player, rank: String, heightDp: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = player.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(SurfaceElevated)
                .border(1.5.dp, color, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = rank, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}
