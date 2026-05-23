package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class DashboardTab(val label: String, val testTag: String) {
    RF_LEARNING("RF 433MHz", "tab_rf_learning"),
    LORA_LEARNING("LoRa Scan", "tab_lora_learning"),
    SAVED_CODES("Stored Signals", "tab_saved_codes"),
    CONSOLE_HISTORY("Console Feed", "tab_console_history"),
    SETUP_GUIDE("Docs & Code", "tab_setup_guide")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(DashboardTab.RF_LEARNING) }
    
    // ViewModel state bindings
    val isRfLearningActive by viewModel.isRfLearningActive.collectAsStateWithLifecycle()
    val isLoraLearningActive by viewModel.isLoraLearningActive.collectAsStateWithLifecycle()
    val connState by viewModel.connectionState.collectAsStateWithLifecycle()
    val frequency by viewModel.selectedFrequency.collectAsStateWithLifecycle()
    
    // Live Captures bindings
    val liveRfCaptured by viewModel.liveCapturedRfSignal.collectAsStateWithLifecycle()
    val liveLoraCaptured by viewModel.liveCapturedLoraPacket.collectAsStateWithLifecycle()

    // Modals states
    var showRfSaveModal by remember { mutableStateOf(false) }
    var rfSaveName by remember { mutableStateOf("") }
    
    var showLoraSaveModal by remember { mutableStateOf(false) }
    var loraSaveName by remember { mutableStateOf("") }

    // Intercept show save triggers
    LaunchedEffect(liveRfCaptured) {
        if (liveRfCaptured != null) {
            rfSaveName = "Gate Remote " + Random().nextInt(100, 999)
            showRfSaveModal = true
        }
    }

    LaunchedEffect(liveLoraCaptured) {
        if (liveLoraCaptured != null) {
            loraSaveName = "Sensor Node " + Random().nextInt(10, 99)
            showLoraSaveModal = true
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            HeaderBar(
                connState = connState,
                frequency = frequency,
                onFrequencyChanged = { viewModel.selectedFrequency.value = it },
                onSettingClicked = { activeTab = DashboardTab.SETUP_GUIDE }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .drawBehind {
                    // Subtle background gradient grid
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF131416),
                                DarkBackground
                            )
                        )
                    )
                }
        ) {
            // Main views multiplexer
            Crossfade(targetState = activeTab, label = "TabTransitions") { tab ->
                when (tab) {
                    DashboardTab.RF_LEARNING -> RfLearningView(viewModel = viewModel)
                    DashboardTab.LORA_LEARNING -> LoraLearningView(viewModel = viewModel)
                    DashboardTab.SAVED_CODES -> SavedCodesView(viewModel = viewModel)
                    DashboardTab.CONSOLE_HISTORY -> ConnectionHistoryView(viewModel = viewModel)
                    DashboardTab.SETUP_GUIDE -> SetupDocumentationView()
                }
            }

            // --- Capture Modals ---

            if (showRfSaveModal && liveRfCaptured != null) {
                AlertDialog(
                    onDismissRequest = {
                        showRfSaveModal = false
                        viewModel.dismissRfCapture()
                    },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageIcons = Icons.Default.Build,
                                contentDescription = "Captured",
                                tint = CyberCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "RF Code Captured!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "A 433MHz ASK/OOK signal has been decoded successfully.",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Raw Pulse:", color = Color.Gray, fontSize = 11.sp)
                                        Text(liveRfCaptured?.rawCode ?: "", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Protocol ID:", color = Color.Gray, fontSize = 11.sp)
                                        Text(liveRfCaptured?.protocol ?: "", color = Color.White, fontSize = 12.sp)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Frequency:", color = Color.Gray, fontSize = 11.sp)
                                        Text("${liveRfCaptured?.frequency} MHz", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }

                            Text("Assign Tag Name:", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                            OutlinedTextField(
                                value = rfSaveName,
                                onValueChange = { rfSaveName = it },
                                placeholder = { Text("e.g. Garage Door") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_rf_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = CyberCyan
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.insertRfCode(
                                    name = rfSaveName.trim().ifEmpty { "RF Capt " + Random().nextInt(100, 999) },
                                    rawCode = liveRfCaptured?.rawCode ?: "",
                                    protocol = liveRfCaptured?.protocol ?: "1"
                                )
                                showRfSaveModal = false
                                viewModel.dismissRfCapture()
                                Toast.makeText(context, "RF Code Saved Instantly!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground),
                            modifier = Modifier.testTag("submit_rf_save")
                        ) {
                            Text("Save Signal", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showRfSaveModal = false
                                viewModel.dismissRfCapture()
                            }
                        ) {
                            Text("Discard", color = Color.Gray)
                        }
                    }
                )
            }

            if (showLoraSaveModal && liveLoraCaptured != null) {
                AlertDialog(
                    onDismissRequest = {
                        showLoraSaveModal = false
                        viewModel.dismissLoraCapture()
                    },
                    containerColor = DarkSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageIcons = Icons.Default.Send,
                                contentDescription = "Captured",
                                tint = CyberTeal,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "LoRa Packet RX!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                "Incoming RF packet decoded successfully using LoRa SF${liveLoraCaptured?.sf}.",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Payload Output:", color = Color.Gray, fontSize = 11.sp)
                                    Text(
                                        liveLoraCaptured?.payload ?: "",
                                        color = CyberTeal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Params:", color = Color.Gray, fontSize = 11.sp)
                                        Text("SF${liveLoraCaptured?.sf} / ${liveLoraCaptured?.bw}kHz", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }

                            Text("Assign Node Name:", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                            OutlinedTextField(
                                value = loraSaveName,
                                onValueChange = { loraSaveName = it },
                                placeholder = { Text("e.g. Weather Sensor") },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_lora_name_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberTeal,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = CyberTeal
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.insertLoraCode(
                                    name = loraSaveName.trim().ifEmpty { "LoRa Pin " + Random().nextInt(10, 99) },
                                    payload = liveLoraCaptured?.payload ?: ""
                                )
                                showLoraSaveModal = false
                                viewModel.dismissLoraCapture()
                                Toast.makeText(context, "LoRa Packet Saved!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = DarkBackground),
                            modifier = Modifier.testTag("submit_lora_save")
                        ) {
                            Text("Save Packet", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showLoraSaveModal = false
                                viewModel.dismissLoraCapture()
                            }
                        ) {
                            Text("Discard", color = Color.Gray)
                        }
                    }
                )
            }
        }
    }
}

// Helper to bridge vector scaling safely
@Composable
fun Icon(imageIcons: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, tint: Color, modifier: Modifier = Modifier) {
    Icon(imageVector = imageIcons, contentDescription = contentDescription, tint = tint, modifier = modifier)
}

// --- Dynamic Header ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBar(
    connState: ConnectionState,
    frequency: Double,
    onFrequencyChanged: (Double) -> Unit,
    onSettingClicked: () -> Unit
) {
    Surface(
        color = DarkSurface,
        tonalElevation = 6.dp,
        border = BorderStroke(width = 0.5.dp, color = Color(0xFF49454F))
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = CyberCyan,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RF",
                            color = ReplayBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "RF & LoRa Lab",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val statusText = when (connState) {
                                ConnectionState.OFFLINE -> "ESP32 Offline"
                                ConnectionState.CHECKING -> "ESP32 Connecting"
                                ConnectionState.CONNECTED -> "ESP32 Connected"
                            }
                            val statusCol = when (connState) {
                                ConnectionState.OFFLINE -> Color.Gray
                                ConnectionState.CHECKING -> CyberTeal
                                ConnectionState.CONNECTED -> ActiveGreen
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = statusCol,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = statusText.uppercase(),
                                color = statusCol,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Settings icon button
                IconButton(
                    onClick = onSettingClicked,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageIcons = Icons.Default.Settings,
                        contentDescription = "Setup Documentation",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Quick Hardware Stats Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(color = DarkSurfaceVariant, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageIcons = Icons.Default.Check,
                        contentDescription = "Wi-Fi",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ESP32_0X7FF", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Freq Band:", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    val frequencies = listOf(315.0, 433.92, 868.0, 915.0)
                    var expandedMenu by remember { mutableStateOf(false) }
                    
                    Box {
                        Text(
                            text = "$frequency MHz",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { expandedMenu = true }
                                .background(color = DarkSurface, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.background(DarkSurfaceVariant)
                        ) {
                            frequencies.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text("$freq MHz", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        onFrequencyChanged(freq)
                                        expandedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Scrolling Activity Canvas Oscilloscope Wave ---

@Composable
fun PulseSignalWaves(pulseActive: Boolean, waveColor: Color, modifier: Modifier = Modifier) {
    if (!pulseActive) {
        // Flatline
        Canvas(modifier = modifier) {
            val h = size.height / 2
            val w = size.width
            drawLine(
                color = waveColor.copy(alpha = 0.2f),
                start = Offset(0f, h),
                end = Offset(w, h),
                strokeWidth = 2.dp.toPx()
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "Oscilloscope")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "Phase"
    )

    Canvas(modifier = modifier) {
        val h = size.height / 2
        val w = size.width
        val points = 100
        val segmentWidth = w / points
        
        var prevX = 0f
        var prevY = h

        for (i in 0..points) {
            val x = i * segmentWidth
            // Simulate OOK pulse wave or LoRa chirp wave
            val radian = (i.toFloat() / points) * 6 * Math.PI.toFloat() + phaseShift
            // Adding ASK multi-frequency harmonics
            val sine = Math.sin(radian.toDouble()).toFloat()
            val noise = Math.sin((radian * 3).toDouble()).toFloat() * 0.3f
            val y = h + (sine + noise) * (h * 0.7f)
            
            drawLine(
                color = waveColor,
                start = Offset(prevX, prevY),
                end = Offset(x, y),
                strokeWidth = 2.dp.toPx()
            )
            prevX = x
            prevY = y
        }
    }
}

// --- Bottom Bar ---

@Composable
fun BottomNavigationBar(
    activeTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    Surface(
        color = DarkSurface,
        tonalElevation = 8.dp,
        border = BorderStroke(width = 0.5.dp, color = Color(0xFF49454F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding(),
            windowInsets = WindowInsets.navigationBars
        ) {
            DashboardTab.values().forEach { tab ->
                val isSelected = activeTab == tab
                
                val icon = when (tab) {
                    DashboardTab.RF_LEARNING -> Icons.Default.Build
                    DashboardTab.LORA_LEARNING -> Icons.Default.Send
                    DashboardTab.SAVED_CODES -> Icons.Default.List
                    DashboardTab.CONSOLE_HISTORY -> Icons.Default.Info
                    DashboardTab.SETUP_GUIDE -> Icons.Default.Menu
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.testTag(tab.testTag),
                    icon = {
                        Icon(
                            imageIcons = icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) ReplayBlue else Color(0xFFCAC4D0),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) Color(0xFFE6E1E5) else Color(0xFFCAC4D0)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyberCyan,
                        selectedIconColor = ReplayBlue,
                        unselectedIconColor = Color(0xFFCAC4D0),
                        selectedTextColor = Color(0xFFE6E1E5),
                        unselectedTextColor = Color(0xFFCAC4D0)
                    )
                )
            }
        }
    }
}

// --- RF Learning Mode View ---

@Composable
fun RfLearningView(viewModel: DashboardViewModel) {
    val isRfLearningActive by viewModel.isRfLearningActive.collectAsStateWithLifecycle()
    val logs by viewModel.connectionLogs.collectAsStateWithLifecycle()
    
    val filteredRfLogs = remember(logs) {
        logs.filter { it.deviceId == "433_RX_Module" || it.deviceId == "UserAction" || it.event.contains("RF") }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Mode activation head
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, if (isRfLearningActive) CyberCyan else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Learning Transceiver Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("ASK/OOK Modulation (433.92 MHz)", color = Color.Gray, fontSize = 12.sp)
                        }

                        // Start/Stop Switch Button
                        Button(
                            onClick = { viewModel.toggleRfLearning() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRfLearningActive) AlertRed else CyberCyan,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_rf_learn_btn")
                        ) {
                            Text(
                                text = if (isRfLearningActive) "STOP" else "LEARN",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scanner wave draw
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .background(color = DarkBackground, shape = RoundedCornerShape(8.dp))
                            .border(width = 0.5.dp, color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        PulseSignalWaves(
                            pulseActive = isRfLearningActive,
                            waveColor = CyberCyan,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Info Overlay
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (isRfLearningActive) ActiveGreen else Color.Gray,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRfLearningActive) "MONITORING..." else "SCANNER OFFLINE",
                                    color = if (isRfLearningActive) ActiveGreen else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isRfLearningActive) {
                            Text(
                                text = "OOK DEC: ENABLED",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Live simulation capture trigger (for environments where hardware isn't attached)
            if (isRfLearningActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        // Trigger simulation signal manual
                        viewModel.isRfLearningActive.value = true // ensure
                        viewModel.dismissRfCapture()
                        // Wait a microsec then fire
                        val fakeVal = Random().nextInt(1000000, 9999999).toString()
                        viewModel.liveCapturedRfSignal.value = RfCode(
                            name = "Captured Code " + Random().nextInt(100, 999),
                            rawCode = fakeVal,
                            protocol = "1",
                            frequency = viewModel.selectedFrequency.value
                        )
                        viewModel.logEvent("SIGNAL MANUAL RX: Intercepted raw code $fakeVal", "433_RX_Module")
                    }
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageIcons = Icons.Default.AddCircle, contentDescription = "Simulate", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Click to manually Force/Inject Sim captured RF pulse", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Live intercept console logs
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Live RF Intercept Console", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredRfLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No current signals captured in this session.\nToggle RF LEARN mode and wait for OOK telemetry packets.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        filteredRfLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val logTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                Text(
                                    "[$logTimeStr] ${log.event}",
                                    color = if (log.event.contains("SIGNAL")) CyberCyan else Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.7f)
                                )
                                Text(
                                    log.deviceId,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(0.3f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- LoRa Learning Mode View ---

@Composable
fun LoraLearningView(viewModel: DashboardViewModel) {
    val isLoraLearningActive by viewModel.isLoraLearningActive.collectAsStateWithLifecycle()
    val sf by viewModel.selectedLoraSF.collectAsStateWithLifecycle()
    val bw by viewModel.selectedLoraBW.collectAsStateWithLifecycle()
    
    val logs by viewModel.connectionLogs.collectAsStateWithLifecycle()
    val filteredLoraLogs = remember(logs) {
        logs.filter { it.deviceId == "SX1278_Module" || it.deviceId == "UserAction" || it.event.contains("LoRa") }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, if (isLoraLearningActive) CyberTeal else Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LoRa Packet Transceiver", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("SX1276/78 Telemetry Decoder", color = Color.Gray, fontSize = 12.sp)
                        }

                        // Start/Stop Switch Button
                        Button(
                            onClick = { viewModel.toggleLoraLearning() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLoraLearningActive) AlertRed else CyberTeal,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("toggle_lora_learn_btn")
                        ) {
                            Text(
                                text = if (isLoraLearningActive) "STOP" else "SCAN",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scanner wave draw
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .background(color = DarkBackground, shape = RoundedCornerShape(8.dp))
                            .border(width = 0.5.dp, color = Color(0xFF1E293B), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        PulseSignalWaves(
                            pulseActive = isLoraLearningActive,
                            waveColor = CyberTeal,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Info Overlay
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            color = if (isLoraLearningActive) ActiveGreen else Color.Gray,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isLoraLearningActive) "RX SENSING..." else "TRANSCEIVER OFFLINE",
                                    color = if (isLoraLearningActive) ActiveGreen else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isLoraLearningActive) {
                            Text(
                                text = "LORA CRC check: OK",
                                color = CyberTeal,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Configuration controls
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LoRa Waveform Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // SF Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Spreading Factor (SF):", color = Color.LightGray, fontSize = 12.sp)
                        Row {
                            listOf(7, 8, 10, 12).forEach { itemSf ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable { viewModel.selectedLoraSF.value = itemSf }
                                        .background(
                                            color = if (sf == itemSf) CyberTeal else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "SF$itemSf",
                                        color = if (sf == itemSf) DarkBackground else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // BW Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bandwidth (BW):", color = Color.LightGray, fontSize = 12.sp)
                        Row {
                            listOf(62.5, 125.0, 250.0).forEach { itemBw ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clickable { viewModel.selectedLoraBW.value = itemBw }
                                        .background(
                                            color = if (bw == itemBw) CyberTeal else DarkSurfaceVariant,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${itemBw}kHz",
                                        color = if (bw == itemBw) DarkBackground else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // Live simulation packet trigger
            if (isLoraLearningActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.isLoraLearningActive.value = true // ensure
                        viewModel.dismissLoraCapture()
                        
                        val telemetrySample = "{\"temp\":${String.format(Locale.US, "%.1f", Random().nextDouble(20.0, 32.0))},\"hum\":${Random().nextInt(40, 80)},\"bat\":${Random().nextInt(80, 100)}}"
                        viewModel.liveCapturedLoraPacket.value = LoraCode(
                            name = "Captured Packet " + Random().nextInt(10, 99),
                            payload = telemetrySample,
                            frequency = viewModel.selectedFrequency.value,
                            sf = viewModel.selectedLoraSF.value,
                            bw = viewModel.selectedLoraBW.value,
                            crc = true
                        )
                        viewModel.logEvent("LORA MANUAL RX: Received payload \"$telemetrySample\"", "SX1278_Module")
                    }
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageIcons = Icons.Default.AddCircle, contentDescription = "Simulate", tint = CyberTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inject Simulated LoRa Telemetry Node packet", color = CyberTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            // Live packet transcripts
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Telemetry Decoded Logs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredLoraLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active LoRa packets detected.\nActivate SCAN mode and configure SF/BW bandwidth bands.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        filteredLoraLogs.forEach { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val logTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                Text(
                                    "[$logTimeStr] ${log.event}",
                                    color = if (log.event.contains("LORA")) CyberTeal else Color.White,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.75f)
                                )
                                Text(
                                    log.deviceId,
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(0.25f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Stored Signals Tab View ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedCodesView(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val rfCodes by viewModel.rfCodesList.collectAsStateWithLifecycle()
    val loraCodes by viewModel.loraCodesList.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf(0) } // 0 = RF, 1 = LoRa
    var editNodeTargetRf by remember { mutableStateOf<RfCode?>(null) }
    var editNodeTargetLora by remember { mutableStateOf<LoraCode?>(null) }
    var renameInputName by remember { mutableStateOf("") }

    val rfQuery by viewModel.rfSearchQuery.collectAsStateWithLifecycle()
    val loraQuery by viewModel.loraSearchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = if (activeSubTab == 0) rfQuery else loraQuery,
            onValueChange = {
                if (activeSubTab == 0) viewModel.rfSearchQuery.value = it
                else viewModel.loraSearchQuery.value = it
            },
            placeholder = { Text("Filter signals query...", color = Color.Gray) },
            leadingIcon = { Icon(imageIcons = Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("signal_search_bar"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color.Gray
            )
        )

        // Sub-tabs segment selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(color = DarkSurface, shape = RoundedCornerShape(8.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeSubTab = 0 }
                    .background(
                        color = if (activeSubTab == 0) DarkSurfaceVariant else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageIcons = Icons.Default.Build,
                        contentDescription = "RF",
                        tint = if (activeSubTab == 0) CyberCyan else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "RF ASK Codes",
                        color = if (activeSubTab == 0) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeSubTab = 1 }
                    .background(
                        color = if (activeSubTab == 1) DarkSurfaceVariant else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageIcons = Icons.Default.Send,
                        contentDescription = "LoRa",
                        tint = if (activeSubTab == 1) CyberTeal else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "LoRa Packets",
                        color = if (activeSubTab == 1) Color.White else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // List Multiplexer
        if (activeSubTab == 0) {
            // RF Codes
            if (rfCodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (rfQuery.isNotBlank()) "No records match search selection." else "Database empty.\nPerform RF captures in the monitoring tab.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rfCodes, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement()
                                .testTag("rf_code_card_${item.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.7f)) {
                                        Text(
                                            item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "Protocol: ${item.protocol} | Freq: ${item.frequency}MHz",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF0F172A), CircleShape)
                                            .clickable {
                                                editNodeTargetRf = item
                                                renameInputName = item.name
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageIcons = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Raw Code representation banner
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = DarkBackground,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.rawCode,
                                        color = CyberCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ASK PULSE",
                                        color = Color.DarkGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Controls line
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Delete
                                    TextButton(
                                        onClick = { viewModel.deleteRfCode(item) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                                    ) {
                                        Icon(imageIcons = Icons.Default.Delete, contentDescription = null, tint = AlertRed, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Copy
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("RF Raw Code", item.rawCode))
                                            Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                                    ) {
                                        Icon(imageIcons = Icons.Default.Share, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Replay
                                    Button(
                                        onClick = {
                                            viewModel.triggerReplayRf(item)
                                            Toast.makeText(context, "Replaying stored RF signal...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(imageIcons = Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Replay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // LoRa Packets
            if (loraCodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (loraQuery.isNotBlank()) "No records match search selection." else "Database empty.\nConnect a transceiver or start scanning to save packets.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(loraCodes, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement()
                                .testTag("lora_packet_card_${item.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.7f)) {
                                        Text(
                                            item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "SF${item.sf} | BW: ${item.bw}kHz | FreqMax: ${item.frequency}MHz",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF0F172A), CircleShape)
                                            .clickable {
                                                editNodeTargetLora = item
                                                renameInputName = item.name
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageIcons = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = CyberTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Raw Code representation banner
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = DarkBackground,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.payload,
                                        color = CyberTeal,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.75f)
                                    )
                                    Text(
                                        text = "RSSI ${item.rssi}",
                                        color = Color.DarkGray,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.25f),
                                        textAlign = TextAlign.End
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Controls line
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Delete
                                    TextButton(
                                        onClick = { viewModel.deleteLoraCode(item) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                                    ) {
                                        Icon(imageIcons = Icons.Default.Delete, contentDescription = null, tint = AlertRed, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Copy
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("LoRa Payload", item.payload))
                                            Toast.makeText(context, "Payload copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                                    ) {
                                        Icon(imageIcons = Icons.Default.Share, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Replay
                                    Button(
                                        onClick = {
                                            viewModel.triggerReplayLora(item)
                                            Toast.makeText(context, "Replaying stored LoRa package...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = DarkBackground),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(imageIcons = Icons.Default.Send, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Replay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Dialogs definitions ---

        if (editNodeTargetRf != null) {
            AlertDialog(
                onDismissRequest = { editNodeTargetRf = null },
                containerColor = DarkSurface,
                title = { Text("Rename RF Signal Entry", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_rf_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            editNodeTargetRf?.let {
                                viewModel.updateRfCodeName(it, renameInputName)
                            }
                            editNodeTargetRf = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground)
                    ) {
                        Text("Update", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editNodeTargetRf = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        if (editNodeTargetLora != null) {
            AlertDialog(
                onDismissRequest = { editNodeTargetLora = null },
                containerColor = DarkSurface,
                title = { Text("Rename LoRa Packet Node", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_lora_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberTeal,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            editNodeTargetLora?.let {
                                viewModel.updateLoraCodeName(it, renameInputName)
                            }
                            editNodeTargetLora = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = DarkBackground)
                    ) {
                        Text("Update", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editNodeTargetLora = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

// --- History & Server Configuration View ---

@Composable
fun ConnectionHistoryView(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val logs by viewModel.connectionLogs.collectAsStateWithLifecycle()
    val apiIp by viewModel.apiIpAddress.collectAsStateWithLifecycle()
    val apiPort by viewModel.apiPort.collectAsStateWithLifecycle()
    val jwt by viewModel.jwtToken.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Express Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Digital Workspace Server Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Transmit decodes to real SQL/Express API network layer.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = apiIp,
                            onValueChange = { viewModel.apiIpAddress.value = it },
                            label = { Text("Server Host IP", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f).testTag("host_ip_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = apiPort,
                            onValueChange = { viewModel.apiPort.value = it },
                            label = { Text("Port", fontSize = 10.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            singleLine = true,
                            modifier = Modifier.weight(0.3f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color.DarkGray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = jwt,
                        onValueChange = { viewModel.jwtToken.value = it },
                        label = { Text("Session JWT Authentication Token", fontSize = 10.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.testServerConnection() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground),
                        modifier = Modifier.fillMaxWidth().testTag("test_conn_btn")
                    ) {
                        Text("Connect & Check Server Status", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Live Log List Component
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transceiver Logger History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        
                        Row {
                            TextButton(
                                onClick = { viewModel.clearLogs() },
                                colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                                modifier = Modifier.testTag("clear_logs_btn")
                            ) {
                                Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No telemetry log records available.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        // Max 30 elements scrolling wrapper
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .background(color = DarkBackground, shape = RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            logs.forEach { log ->
                                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "[$dateStr] ${log.deviceId}",
                                            color = if (log.deviceId.contains("User") || log.deviceId.contains("Dash")) CyberCyan else CyberTeal,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = log.event,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                    Divider(color = Color(0xFF1E293B), thickness = 0.5.dp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Download Exporter Button Triggers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                                            onClick = {
                                                val csv = viewModel.exportLogsAsCsv()
                                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Console Logs CSV", csv))
                                                Toast.makeText(context, "CSV logs copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                            modifier = Modifier.weight(1f).testTag("export_csv_btn")
                                        ) {
                                            Icon(imageIcons = Icons.Default.Share, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Export CSV", fontSize = 11.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                val json = viewModel.exportLogsAsJson()
                                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Console Logs JSON", json))
                                                Toast.makeText(context, "JSON logs copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                            modifier = Modifier.weight(1f).testTag("export_json_btn")
                                        ) {
                                            Icon(imageIcons = Icons.Default.List, contentDescription = null, tint = CyberTeal, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Export JSON", fontSize = 11.sp, color = Color.White)
                                        }
                    }
                }
            }
        }
    }
}

// --- Setup, Code Copy & Deliverables Tab ---

@Composable
fun SetupDocumentationView() {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var expandedAssetIndex by remember { mutableStateOf(0) } // 0 = Guide, 1 = Sketch, 2 = Express, 3 = SQL / Schema

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Deliverables & Firmware Repository", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Copy direct production-ready setups to build your physical transceiver console.", color = Color.Gray, fontSize = 12.sp)
        }

        // Accordion Tabs
        val itemsList = listOf(
            Triple("Hardware Setup Reference Manual", "Detailed schematics, wiring list, and physical architecture.", 0),
            Triple("ESP32 Capture Firmware (.ino)", "C++ Arduino workspace utilising RadioLib + rc-switch templates.", 1),
            Triple("Node.js + Socket.io Server (API)", "Complete Express backend service, API routes, and database models.", 2),
            Triple("PostgreSQL Schema & Index Queries", "Complete relational DB entity mappings and optimization queries.", 3)
        )

        items(itemsList) { (title, desc, index) ->
            val isExpanded = expandedAssetIndex == index
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isExpanded) DarkSurface else DarkSurface.copy(alpha = 0.6f)),
                border = BorderStroke(width = 1.dp, color = if (isExpanded) CyberCyan.copy(alpha = 0.5f) else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedAssetIndex = index }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.85f)) {
                            Text(title, color = if (isExpanded) CyberCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(desc, color = Color.Gray, fontSize = 11.sp)
                        }
                        Icon(
                            imageIcons = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp).weight(0.15f)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))

                        val payloadCode = when (index) {
                            0 -> HardwareAssets.setupGuide
                            1 -> HardwareAssets.esp32Sketch
                            2 -> HardwareAssets.nodeBackend
                            else -> HardwareAssets.dbSchemaDoc
                        }

                        // Code Frame Content Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .background(color = DarkBackground, shape = RoundedCornerShape(4.dp))
                                .border(width = 0.5.dp, color = Color(0xFF1E293B), shape = RoundedCornerShape(4.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = payloadCode,
                                color = if (index == 0) Color.LightGray else CyberTeal,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("Transceiver Source Code", payloadCode))
                                Toast.makeText(context, "Source code copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageIcons = Icons.Default.Share, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Complete Resource Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
