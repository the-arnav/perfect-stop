package com.example.perfectstop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.perfectstop.ui.PerfectStopApp
import com.example.perfectstop.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()
    private var permissionsReady by mutableStateOf(false)
    private var bluetoothReady by mutableStateOf(false)
    private var locationReady by mutableStateOf(true)
    private val bluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshReadiness() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshReadiness() }

    override fun onResume() {
        super.onResume()
        refreshReadiness()
    }

    private fun requiredPermissions() = if (Build.VERSION.SDK_INT >= 31) listOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT
    ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun refreshReadiness() {
        permissionsReady = requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        bluetoothReady = permissionsReady && try { getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (_: SecurityException) { false }
        locationReady = Build.VERSION.SDK_INT >= 31 || androidx.core.location.LocationManagerCompat.isLocationEnabled(getSystemService(android.location.LocationManager::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable seamless edge-to-edge with transparent system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        setContent {
            val gameState by viewModel.uiState.collectAsState()
            val ready = permissionsReady && bluetoothReady && locationReady
            val needsConnection = gameState.phase == com.example.perfectstop.model.GamePhase.LOBBY && gameState.role != com.example.perfectstop.model.GameRole.SOLO
            var awaitingConnection by remember { mutableStateOf(false) }
            LaunchedEffect(ready, needsConnection) {
                if (!needsConnection) awaitingConnection = false
                else if (!ready) awaitingConnection = true
                else if (awaitingConnection) {
                    awaitingConnection = false
                    val name = com.example.perfectstop.model.UserProfile.getOrGenerateUsername(this@MainActivity)
                    if (gameState.role == com.example.perfectstop.model.GameRole.HOST) viewModel.initAsHost(name)
                    else viewModel.initAsClient(name)
                }
            }
            SideEffect {
                insetsController.isAppearanceLightStatusBars = !gameState.darkTheme
                insetsController.isAppearanceLightNavigationBars = !gameState.darkTheme
            }
            com.example.perfectstop.theme.PerfectStopTheme(darkTheme = gameState.darkTheme) {
                if (!needsConnection || ready) PerfectStopApp(viewModel = viewModel)
                else Surface(modifier = Modifier.fillMaxSize()) {
                    BackHandler { viewModel.confirmLeaveLobby() }
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center) {
                        Text("Bluetooth access", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(16.dp))
                        Text("Allow access to connect nearby phones.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(24.dp))
                        Text(if (permissionsReady) "Permissions allowed" else "Allow nearby-device permissions")
                        Spacer(Modifier.height(12.dp))
                        Text(if (bluetoothReady) "Bluetooth enabled" else "Turn on Bluetooth")
                        if (!locationReady) Text("3. Enable Location for Bluetooth discovery on this Android version")
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = {
                            when {
                                !permissionsReady -> requestRequiredPermissions()
                                !bluetoothReady -> bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                else -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (!permissionsReady) "Allow permissions" else if (!bluetoothReady) "Turn on Bluetooth" else "Enable Location")
                        }
                        TextButton(onClick = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }) {
                            Text("Open app settings")
                        }
                        TextButton(onClick = viewModel::confirmLeaveLobby) { Text("Back") }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
