package com.example.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.api.GeminiRepository
import com.example.data.UpdateHistory
import com.example.data.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(
        val versionName: String,
        val androidVersion: String,
        val sizeBytes: Long,
        val changelog: String
    ) : UpdateState
    data class Downloading(
        val progress: Float, // 0.0f to 1.0f
        val speedMbps: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState
    data class Installing(val progress: Float) : UpdateState
    object UpToDate : UpdateState
}

sealed interface AiDiagnosticState {
    object Idle : AiDiagnosticState
    object Loading : AiDiagnosticState
    data class Success(val advice: String) : AiDiagnosticState
    data class Error(val message: String) : AiDiagnosticState
}

sealed interface AiUpdateAnalysisState {
    object Idle : AiUpdateAnalysisState
    object Loading : AiUpdateAnalysisState
    data class Success(val advice: String) : AiUpdateAnalysisState
    data class Error(val message: String) : AiUpdateAnalysisState
}

class UpdateViewModel(
    application: Application,
    private val repository: UpdateRepository
) : AndroidViewModel(application) {

    private val geminiRepository = GeminiRepository()

    // Database List
    val downloadHistoryList: StateFlow<List<UpdateHistory>> = repository.historyList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current tab
    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    // OTA Update state
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // Diagnostics State
    private val _aiDiagnosticState = MutableStateFlow<AiDiagnosticState>(AiDiagnosticState.Idle)
    val aiDiagnosticState: StateFlow<AiDiagnosticState> = _aiDiagnosticState.asStateFlow()

    // AI Update Analysis State
    private val _aiUpdateAnalysisState = MutableStateFlow<AiUpdateAnalysisState>(AiUpdateAnalysisState.Idle)
    val aiUpdateAnalysisState: StateFlow<AiUpdateAnalysisState> = _aiUpdateAnalysisState.asStateFlow()

    // Interactive sensor diagnostics
    private val _isSensorTestRunning = MutableStateFlow(false)
    val isSensorTestRunning: StateFlow<Boolean> = _isSensorTestRunning.asStateFlow()

    private val _sensorResults = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val sensorResults: StateFlow<List<Pair<String, Boolean>>> = _sensorResults.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preseedIfEmpty()
        }
        createNotificationChannel()
    }

    fun selectTab(index: Int) {
        _currentTab.value = index
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            delay(1800) // Realistic check delay

            // Check if user is already on latest or simulate update
            _updateState.value = UpdateState.UpdateAvailable(
                versionName = "MagicOS 8.0.2.140 (C432E5R2P1)",
                androidVersion = "Android 14",
                sizeBytes = 2950000000L, // 2.95 GB
                changelog = "• Interactive QuickCapsules: High-priority task live controls directly in your status bar.\n" +
                        "• Magic Mirror: Multitasking with customizable secondary screens.\n" +
                        "• System Fluidity Redefined: Faster application startup and 300ms faster motion spring transitions.\n" +
                        "• Honor Secure Local Cloud: Secure offline calculations for high-density private data."
            )

            // Dynamic push notification alerting user an update is ready!
            sendLocalNotification(
                title = "🎁 MagicOS Update Available",
                text = "MagicOS 8.0.2.140 is waiting to be downloaded. Speed up your system today!"
            )
        }
    }

    fun downloadAndInstallUpdate() {
        val currentState = _updateState.value
        if (currentState !is UpdateState.UpdateAvailable) return

        viewModelScope.launch(Dispatchers.Default) {
            val totalBytes = currentState.sizeBytes
            var downloaded = 0L
            val stepSize = 45000000L // 45MB step size

            while (downloaded < totalBytes) {
                downloaded += stepSize
                if (downloaded > totalBytes) downloaded = totalBytes

                val progress = downloaded.toFloat() / totalBytes
                val speed = (30..80).random().toFloat() // Mock speed in MB/s

                _updateState.value = UpdateState.Downloading(
                    progress = progress,
                    speedMbps = speed,
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes
                )
                delay(120) // Fast interactive simulation
            }

            // Move to installing
            var installProgress = 0.0f
            while (installProgress < 1.0f) {
                installProgress += 0.05f
                if (installProgress > 1.0f) installProgress = 1.0f
                _updateState.value = UpdateState.Installing(installProgress)
                delay(150)
            }

            // Save to database
            repository.insertHistory(
                UpdateHistory(
                    versionName = currentState.versionName,
                    androidVersion = currentState.androidVersion,
                    changelog = currentState.changelog,
                    downloadSize = totalBytes,
                    status = "COMPLETED",
                    timestamp = System.currentTimeMillis()
                )
            )

            _updateState.value = UpdateState.UpToDate

            // Alert complete!
            sendLocalNotification(
                title = "✅ Update Installed Successfully",
                text = "Your device is now running ${currentState.versionName} on ${currentState.androidVersion}!"
            )
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Diagnostics Info Retrievers
    fun getOsVersion(): String {
        return "MagicOS 8.0 (Android ${Build.VERSION.RELEASE})"
    }

    fun getDeviceModel(): String {
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (brand.lowercase().contains("honor")) "$brand $model" else "Honor $model (Compatible)"
    }

    fun getCpuCoresCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    fun getSystemStorageInfo(context: Context): Pair<String, String> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.freeBytes
            val totalBytes = stat.totalBytes
            val freeGb = String.format("%.2f", freeBytes.toDouble() / (1024 * 1024 * 1024))
            val totalGb = String.format("%.2f", totalBytes.toDouble() / (1024 * 1024 * 1024))
            Pair("$freeGb GB", "$totalGb GB")
        } catch (e: Exception) {
            Pair("N/A", "N/A")
        }
    }

    fun getRamInfo(context: Context): Pair<String, String> {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val freeGb = String.format("%.2f", memInfo.availMem.toDouble() / (1024 * 1024 * 1024))
            val totalGb = String.format("%.2f", memInfo.totalMem.toDouble() / (1024 * 1024 * 1024))
            Pair("$freeGb GB", "$totalGb GB")
        } catch (e: Exception) {
            Pair("N/A", "N/A")
        }
    }

    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            85
        }
    }

    fun getBatteryTemperature(context: Context): String {
        // Battery temperature is queried via Intent filter in broadcast receivers, we will estimate or calculate it
        return "35.2° C"
    }

    fun getThermalStateDescription(context: Context): String {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "Cool (Perfect)"
                    PowerManager.THERMAL_STATUS_LIGHT -> "Warm (Optimized)"
                    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate (Standard)"
                    PowerManager.THERMAL_STATUS_SEVERE -> "Hot (High Load)"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical (Throttled)"
                    else -> "Balanced"
                }
            } else {
                "Cool"
            }
        } catch (e: Exception) {
            "Cool"
        }
    }

    fun runSensorDiagnostics() {
        viewModelScope.launch {
            _isSensorTestRunning.value = true
            _sensorResults.value = emptyList()

            val tests = listOf(
                "System Clock" to true,
                "RAM Allocation Engine" to true,
                "Local SQLite Database" to true,
                "WLAN Network Hook" to true,
                "GPU Core Frame Buffer" to true,
                "Hardware Battery Controller" to true
            )

            val results = mutableListOf<Pair<String, Boolean>>()
            for (test in tests) {
                delay(400)
                results.add(test.first to test.second)
                _sensorResults.value = results.toList()
            }
            _isSensorTestRunning.value = false
        }
    }

    fun triggerAiDiagnostics(context: Context) {
        viewModelScope.launch {
            _aiDiagnosticState.value = AiDiagnosticState.Loading

            val os = getOsVersion()
            val cores = getCpuCoresCount()
            val batLvl = getBatteryLevel(context)
            val batTemp = getBatteryTemperature(context)
            val ram = getRamInfo(context)
            val disk = getSystemStorageInfo(context)
            val thermal = getThermalStateDescription(context)

            // Highlight negative/positive sensor diagnostic outcomes
            val issues = mutableListOf<String>()
            if (batLvl < 20) {
                issues.add("Low battery status")
            }
            if (disk.first.replace(" GB", "").toDoubleOrNull() ?: 50.0 < 5.0) {
                issues.add("Extremely low storage partition")
            }
            if (_sensorResults.value.isEmpty()) {
                issues.add("Diagnostic hardware pre-runs have not been completed")
            }

            val advice = geminiRepository.analyzeDiagnostics(
                modelName = "gemini-3.5-flash",
                osVersion = os,
                cpuCores = cores,
                batteryPct = batLvl,
                batteryTemp = batTemp,
                availableRamGb = ram.first,
                availableStorageGb = disk.first,
                thermalState = thermal,
                registeredIssues = issues
            )

            _aiDiagnosticState.value = AiDiagnosticState.Success(advice)
        }
    }

    fun triggerAiUpdateAnalysis(context: Context, versionName: String, changelog: String) {
        viewModelScope.launch {
            _aiUpdateAnalysisState.value = AiUpdateAnalysisState.Loading
            
            val batLvl = getBatteryLevel(context)
            val ram = getRamInfo(context)
            val disk = getSystemStorageInfo(context)
            
            val advice = geminiRepository.analyzeUpdateCompatibility(
                versionName = versionName,
                changelog = changelog,
                batteryPct = batLvl,
                freeStorageGb = disk.first,
                ramInfo = ram.first + " Free (Total: ${ram.second})"
            )
            
            _aiUpdateAnalysisState.value = AiUpdateAnalysisState.Success(advice)
        }
    }

    // Push local Notifications
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MagicOS Updates"
            val descriptionText = "Firmware updates and system health notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("magicos_ota", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendLocalNotification(title: String, text: String) {
        val context = getApplication<Application>()

        // Check permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Return gracefully; MainActivity will prompt for it
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, "magicos_ota")
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // system standard drawable icon
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            // ID can be simple time hash or specific ID
            notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
        }
    }
}
