package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log

class DeviceController(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isTorchActive = false

    private val popularPackages = mapOf(
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "telegram" to "org.telegram.messenger",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "netflix" to "com.netflix.mediaclient",
        "camera" to "com.android.camera",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "calculator" to "com.google.android.calculator"
    )

    fun toggleTorch(enable: Boolean? = null): Boolean {
        if (cameraManager == null) return false
        val targetState = enable ?: !isTorchActive
        return try {
            val cameraIdList = cameraManager.cameraIdList
            for (id in cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraManager.setTorchMode(id, targetState)
                    isTorchActive = targetState
                    vibrateFeedback(40)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("DeviceController", "Error toggling torch: ${e.message}")
            false
        }
    }

    fun isTorchOn(): Boolean = isTorchActive

    fun setVolume(percent: Int): Int {
        if (audioManager == null) return 0
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent / 100f) * maxVol).toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        vibrateFeedback(30)
        return target
    }

    fun adjustVolumeRelative(delta: Int): Int {
        if (audioManager == null) return 0
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (current + delta).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        vibrateFeedback(30)
        return target
    }

    fun muteVolume(): Boolean {
        if (audioManager == null) return false
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        vibrateFeedback(50)
        return true
    }

    fun getVolumeInfo(): Pair<Int, Int> {
        if (audioManager == null) return Pair(7, 15)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return Pair(current, max)
    }

    fun vibrateFeedback(durationMs: Long = 60) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceController", "Vibration failed: ${e.message}")
        }
    }

    fun launchApp(appNameOrPackage: String): AgentExecutionResult {
        val query = appNameOrPackage.trim().lowercase()
        val targetPackage = popularPackages[query] ?: query

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            vibrateFeedback(50)
            return AgentExecutionResult(
                actionName = "LAUNCH_APP",
                isSuccess = true,
                message = "Successfully launched $appNameOrPackage"
            )
        }

        // Check if user asked to open camera or dialer or calculator specifically
        if (query.contains("camera")) {
            val cameraIntent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(cameraIntent)
                return AgentExecutionResult("LAUNCH_CAMERA", true, "Camera opened.")
            } catch (_: Exception) {}
        }

        // If not installed, prompt or direct to Play Store!
        return downloadOrOpenPlayStore(targetPackage, appNameOrPackage)
    }

    fun downloadOrOpenPlayStore(packageName: String, displayName: String = packageName): AgentExecutionResult {
        val target = popularPackages[displayName.lowercase()] ?: packageName
        val marketUri = Uri.parse("market://details?id=$target")
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            vibrateFeedback(60)
            AgentExecutionResult(
                actionName = "DOWNLOAD_APP",
                isSuccess = true,
                message = "Opening Google Play Store to install $displayName ($target)"
            )
        } catch (e: Exception) {
            // Fallback to browser Play Store link
            val webUri = Uri.parse("https://play.google.com/store/apps/details?id=$target")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
                AgentExecutionResult(
                    actionName = "DOWNLOAD_APP",
                    isSuccess = true,
                    message = "Opened Play Store web download link for $displayName"
                )
            } catch (err: Exception) {
                AgentExecutionResult(
                    actionName = "DOWNLOAD_APP",
                    isSuccess = false,
                    message = "Failed to launch Play Store: ${err.message}"
                )
            }
        }
    }

    fun openWebSearch(query: String): AgentExecutionResult {
        val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AgentExecutionResult("WEB_SEARCH", true, "Searched the web for \"$query\"")
        } catch (e: Exception) {
            AgentExecutionResult("WEB_SEARCH", false, "Unable to open browser: ${e.message}")
        }
    }

    fun openSettingsScreen(action: String): AgentExecutionResult {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            vibrateFeedback(40)
            AgentExecutionResult("OPEN_SETTINGS", true, "Opened device settings.")
        } catch (e: Exception) {
            AgentExecutionResult("OPEN_SETTINGS", false, "Could not open settings: ${e.message}")
        }
    }

    fun setTimer(seconds: Int, message: String = "Maria AI Timer"): AgentExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AgentExecutionResult("SET_TIMER", true, "Timer set for $seconds seconds.")
        } catch (e: Exception) {
            AgentExecutionResult("SET_TIMER", false, "Could not start timer: ${e.message}")
        }
    }

    fun openDialer(phoneNumber: String = ""): AgentExecutionResult {
        val uri = if (phoneNumber.isNotBlank()) Uri.parse("tel:$phoneNumber") else Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AgentExecutionResult("OPEN_DIALER", true, "Phone dialer opened.")
        } catch (e: Exception) {
            AgentExecutionResult("OPEN_DIALER", false, "Could not open dialer: ${e.message}")
        }
    }

    fun getDeviceTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (scale > 0 && level >= 0) (level * 100 / scale) else 88
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempCelsius = tempRaw / 10f

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(4096)
        val freeRamMb = (memInfo.availMem / (1024 * 1024)).coerceAtLeast(1024)

        var totalStorageGb = 128L
        var freeStorageGb = 64L
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            totalStorageGb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            freeStorageGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
        } catch (_: Exception) {}

        val (curVol, maxVol) = getVolumeInfo()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        return DeviceTelemetry(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            batteryTempCelsius = tempCelsius,
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            totalStorageGb = totalStorageGb,
            freeStorageGb = freeStorageGb,
            isTorchOn = isTorchActive,
            currentVolume = curVol,
            maxVolume = maxVol,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            isNetworkConnected = isOnline
        )
    }
}
