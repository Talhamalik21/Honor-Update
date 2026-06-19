package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.UpdateHistory
import com.example.data.UpdateRepository
import com.example.ui.theme.*
import com.example.viewmodel.AiDiagnosticState
import com.example.viewmodel.AiUpdateAnalysisState
import com.example.viewmodel.UpdateState
import com.example.viewmodel.UpdateViewModel
import com.example.viewmodel.UpdateViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room database bootstrapping
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "magicos_update_db"
        ).fallbackToDestructiveMigration().build()

        val repository = UpdateRepository(db.updateHistoryDao())
        val factory = UpdateViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[UpdateViewModel::class.java]

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var isBannerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.UpdateAvailable) {
            isBannerDismissed = false
        }
    }
    
    // Notification permission launcher
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            viewModel.sendLocalNotification(
                title = "🔔 Channels Enabled",
                text = "Notifications for MagicOS OTA rollouts have been successfully configured on your device."
            )
        }
    }

    // Proactively request notification permission on first launch if on Tiramisu+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = "MagicOS Update Hub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Upload, contentDescription = "Updates") },
                    label = { Text("Update Center") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = HonorMuted
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = HonorMuted
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Diagnostics") },
                    label = { Text("Diagnostics") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = HonorMuted
                    )
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                0 -> UpdateCenterTab(
                    viewModel = viewModel,
                    hasPermission = hasNotificationPermission,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                1 -> DownloadHistoryTab(viewModel = viewModel)
                2 -> SystemDiagnosticsTab(viewModel = viewModel)
            }

            // Floating Clean Minimal Update Notification Banner
            AnimatedVisibility(
                visible = updateState is UpdateState.UpdateAvailable && !isBannerDismissed,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(99f)
            ) {
                val availableUpdate = updateState as? UpdateState.UpdateAvailable
                UpdateNotificationBanner(
                    versionName = availableUpdate?.versionName ?: "MagicOS 8.0.2",
                    onDownloadClick = {
                        viewModel.downloadAndInstallUpdate()
                    },
                    onDismiss = {
                        isBannerDismissed = true
                    }
                )
            }
        }
    }
}

@Composable
fun UpdateCenterTab(
    viewModel: UpdateViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High fidelity branding hero (Clean Minimalism - Material You style)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MinimalBlueContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Central circle illustration mimicking check_circle from HTML
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape)
                            .border(3.dp, MinimalBlue, CircleShape)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success check icon",
                            tint = MinimalBlueDark,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = viewModel.getDeviceModel(),
                        color = OnPrimaryContainerColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current Version: ${viewModel.getOsVersion()}",
                        color = OnPrimaryContainerSupportingColor.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Status ID",
                            tint = OnPrimaryContainerColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Official Release Channel",
                            color = OnPrimaryContainerColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Live dynamic actions container
        when (val state = updateState) {
            is UpdateState.Idle -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large Pulsing Check Target
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(160.dp)
                            .padding(16.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = HonorBlue.copy(alpha = 0.1f),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 4.dp.toPx())
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Up to date check icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(68.dp)
                        )
                    }
                    
                    Text(
                        text = "Your MagicOS system is healthy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Last system check: Today ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}",
                        color = HonorMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.checkForUpdates() },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp)
                            .testTag("check_updates_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check for Updates", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            is UpdateState.Checking -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        )
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(160.dp)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Loading",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(64.dp)
                                .rotate(rotation)
                        )
                    }
                    Text(
                        text = "Connecting to Honor Rollout Servers...",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verifying cryptographic device hardware signature",
                        color = HonorMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is UpdateState.UpdateAvailable -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = "New update alert",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    text = "Update Available",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.versionName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Android Platform: ${state.androidVersion} | Size: ${
                                        String.format("%.2f", state.sizeBytes.toFloat() / (1024 * 1024 * 1024))
                                    } GB",
                                    fontSize = 13.sp,
                                    color = HonorMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "CHANGELOG & FEATURES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = HonorMuted,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = state.changelog,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AiUpdateCompatibilityAdvisor(
                        viewModel = viewModel,
                        versionName = state.versionName,
                        changelog = state.changelog
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.dismissUpdate() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .padding(end = 8.dp),
                            shape = CircleShape
                        ) {
                            Text("Later", fontSize = 15.sp)
                        }

                        Button(
                            onClick = { viewModel.downloadAndInstallUpdate() },
                            modifier = Modifier
                                .weight(2f)
                                .height(50.dp)
                                .testTag("download_and_install_button"),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download & Install", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
            is UpdateState.Downloading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Downloading Firmware Package...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${String.format("%.2f", state.downloadedBytes.toFloat() / (1024 * 1024 * 1024))} GB of " +
                                    "${String.format("%.2f", state.totalBytes.toFloat() / (1024 * 1024 * 1024))} GB",
                            fontSize = 13.sp,
                            color = HonorMuted
                        )
                        Text(
                            text = "${state.speedMbps.toInt()} MB/s",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Your device will verify the downloaded file automatically upon completion.",
                        fontSize = 11.sp,
                        color = HonorMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is UpdateState.Installing -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseOutSine),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .shadow(2.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Gear",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .rotate(pulseScale * 360f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Applying MagicOS Update Partition...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = HonorTeal,
                        trackColor = HonorTeal.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Step ${(state.progress * 100).toInt()}% Done. Do not reboot system.",
                        fontSize = 12.sp,
                        color = HonorMuted
                    )
                }
            }
            is UpdateState.UpToDate -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .shadow(2.dp, CircleShape)
                            .background(HonorTeal.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = HonorTeal,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Congratulations!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your device is updated and operating on the latest MagicOS distribution. Enjoy the custom optimizations!",
                        fontSize = 13.sp,
                        color = HonorMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.dismissUpdate() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Return to Hub")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Real-Time Notification test settings
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "REAL-TIME ROLLOUT NOTIFICATIONS",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = HonorMuted,
            modifier = Modifier.align(Alignment.Start),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Notif Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Push Rollout Delivery",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasPermission) "Status: Enabled" else "Status: Permission Required",
                        fontSize = 12.sp,
                        color = if (hasPermission) HonorTeal else HonorCrimson
                    )
                }
                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HonorOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.sendLocalNotification(
                                title = "📢 Honor Rollout Center",
                                text = "A safety framework verification update has successfully completed deployment in your sector."
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test Trigger", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadHistoryTab(viewModel: UpdateViewModel) {
    val history by viewModel.downloadHistoryList.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Firmware Logs",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${history.size} total downloads stored offline",
                    fontSize = 12.sp,
                    color = HonorMuted
                )
            }

            if (history.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearAllHistory() },
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear logs",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty",
                        tint = HonorMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "History is empty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloaded update records will populate here dynamically.",
                        color = HonorMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    HistoryItemCard(item = item)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: UpdateHistory) {
    var expanded by remember { mutableStateOf(false) }
    val formattedDate = remember(item.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(HonorTeal.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Check",
                        tint = HonorTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.versionName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        color = HonorMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "COMPLETED",
                        color = HonorTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${String.format("%.2f", item.downloadSize.toFloat() / (1024 * 1024 * 1024))} GB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Package Android Base: ${item.androidVersion}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Deployment Details:\n${item.changelog}",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = HonorMuted
                    )
                }
            }
        }
    }
}

@Composable
fun SystemDiagnosticsTab(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val aiState by viewModel.aiDiagnosticState.collectAsStateWithLifecycle()
    val isSensorRunning by viewModel.isSensorTestRunning.collectAsStateWithLifecycle()
    val sensorResults by viewModel.sensorResults.collectAsStateWithLifecycle()

    val storageData = remember(context) { viewModel.getSystemStorageInfo(context) }
    val ramData = remember(context) { viewModel.getRamInfo(context) }
    val batteryLvl = remember(context) { viewModel.getBatteryLevel(context) }
    val batteryTemp = remember(context) { viewModel.getBatteryTemperature(context) }
    val thermalState = remember(context) { viewModel.getThermalStateDescription(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Telemetry Diagnostics",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Real-time MagicOS device controller values",
            fontSize = 12.sp,
            color = HonorMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        SystemStatusIndicatorCard(isSensorRunning = isSensorRunning)

        Spacer(modifier = Modifier.height(16.dp))

        // Grid of hardware stats
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                StatCard(
                    title = "Battery Pool",
                    value = "$batteryLvl%",
                    subtitle = "Temp: $batteryTemp",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = HonorTeal
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                StatCard(
                    title = "Active RAM",
                    value = ramData.first,
                    subtitle = "Total: ${ramData.second}",
                    icon = Icons.Default.Memory,
                    iconColor = HonorBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                StatCard(
                    title = "Storage Space",
                    value = storageData.first,
                    subtitle = "Total: ${storageData.second}",
                    icon = Icons.Default.Storage,
                    iconColor = HonorOrange
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                StatCard(
                    title = "Thermal Load",
                    value = thermalState,
                    subtitle = "CPU Core Throttle",
                    icon = Icons.Default.Thermostat,
                    iconColor = HonorCrimson
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Card 2: Interactive integrity test
        Text(
            text = "HARDWARE INTEGRITY PRE-CHECKS",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = HonorMuted,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Register integrity metrics",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pre-check before system updates",
                            fontSize = 11.sp,
                            color = HonorMuted
                        )
                    }

                    Button(
                        onClick = { viewModel.runSensorDiagnostics() },
                        enabled = !isSensorRunning,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = CircleShape,
                        modifier = Modifier.testTag("run_diagnostics_button")
                    ) {
                        Text(if (isSensorRunning) "Running..." else "Run", fontSize = 12.sp)
                    }
                }

                if (sensorResults.isNotEmpty() || isSensorRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (result in sensorResults) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = result.first,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Pass",
                                        tint = HonorTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "PASS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HonorTeal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gemini AI Advisor Card
        Text(
            text = "AI OPTIMIZATION ADVISOR",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = HonorMuted,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        HonorBlue,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Gemini System Recommendation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "MagicOS custom calibration",
                            fontSize = 11.sp,
                            color = HonorMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (val state = aiState) {
                    is AiDiagnosticState.Idle -> {
                        Text(
                            text = "Run the Gemini advisor to receive customized phone optimization recommendations based on active system telemetry values.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.triggerAiDiagnostics(context) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        ) {
                            Text("Request Advisory Report", fontWeight = FontWeight.Bold)
                        }
                    }
                    is AiDiagnosticState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Evaluating hardware parameters...",
                                fontSize = 12.sp,
                                color = HonorMuted
                            )
                        }
                    }
                    is AiDiagnosticState.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, HonorBorderLight, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.advice,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.triggerAiDiagnostics(context) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        ) {
                            Text("Recalibrate & Refresh", fontWeight = FontWeight.Bold)
                        }
                    }
                    is AiDiagnosticState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.triggerAiDiagnostics(context) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        ) {
                            Text("Retry Diagnostic Connection", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Flat crisp minimal look with borders
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = HonorMuted
                )
                // Small beautiful icon token container from design HTML
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = HonorMuted
            )
        }
    }
}

@Composable
fun UpdateNotificationBanner(
    versionName: String,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .testTag("update_notification_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MinimalBlueContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = "New Update Alert Icon",
                        tint = MinimalBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "New Firmware Update",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HonorMuted
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = versionName.ifEmpty { "MagicOS 8.0.2" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Download Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MinimalBlue,
                        modifier = Modifier
                            .clickable { onDownloadClick() }
                            .testTag("direct_download_link")
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("dismiss_banner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss Banner",
                    tint = HonorMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AiUpdateCompatibilityAdvisor(
    viewModel: UpdateViewModel,
    versionName: String,
    changelog: String
) {
    val context = LocalContext.current
    val aiUpdateState by viewModel.aiUpdateAnalysisState.collectAsStateWithLifecycle()

    Text(
        text = "MAGICOS AI RELEASE INSIGHTS",
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = HonorMuted,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MinimalBlueMedium,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gemini Release Architect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Compatibility & impact analysis",
                        fontSize = 11.sp,
                        color = HonorMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (val state = aiUpdateState) {
                is AiUpdateAnalysisState.Idle -> {
                    Text(
                        text = "Instruct Gemini to evaluate this $versionName firmware update. The model will assess real-time storage availability, battery pool constraints, and release changelogs to generate a custom compatibility grade.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.triggerAiUpdateAnalysis(context, versionName, changelog) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape
                    ) {
                        Text("Analyze Update with AI", fontWeight = FontWeight.Bold)
                    }
                }
                is AiUpdateAnalysisState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing update package telemetry...",
                            fontSize = 12.sp,
                            color = HonorMuted
                        )
                    }
                }
                is AiUpdateAnalysisState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, HonorBorderLight, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = state.advice,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.triggerAiUpdateAnalysis(context, versionName, changelog) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape
                    ) {
                        Text("Recalibrate & Re-analyze", fontWeight = FontWeight.Bold)
                    }
                }
                is AiUpdateAnalysisState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.triggerAiUpdateAnalysis(context, versionName, changelog) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = CircleShape
                    ) {
                        Text("Retry AI Calibration", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemStatusIndicatorCard(isSensorRunning: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("system_status_indicator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, HonorBorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp)
            ) {
                // Repeating pulse outer glow ring
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 2.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale"
                )
                
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha"
                )
                
                val pulseColor = if (isSensorRunning) MinimalBlue else MinimalEmerald
                
                // Pulsing outer shadow ring
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            alpha = alpha
                        )
                        .background(pulseColor, CircleShape)
                )
                
                // Solid inner indicator dot with breathing alpha
                val breathingAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "breath"
                )
                
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer(alpha = breathingAlpha)
                        .background(pulseColor, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSensorRunning) "DIAGNOSTICS IN PROGRESS" else "SYSTEM TELEMETRY STATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSensorRunning) MinimalBlueMedium else MinimalEmerald,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isSensorRunning) "Analyzing controller metrics and pre-checks..." else "All hardware registers and services reporting nominal.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


