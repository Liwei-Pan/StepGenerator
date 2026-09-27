package com.pan.liwe.step

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.random.Random

class StepGeneratorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var generatingJob: Job? = null
    private var totalSteps = 0
    private var lastRecordTime: Instant = Instant.now()

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        // Android 14 起，health 型前景服務必須持有 ACTIVITY_RECOGNITION，否則
        // startForeground 會丟 SecurityException。先擋下來直接收工，避免
        // START_STICKY 把單次失敗放大成無限重啟崩潰迴圈。
        if (!hasHealthForegroundServicePermission()) {
            stopSelf()
            return
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            else
                0
        )
        startGenerating()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) {
            stopGenerating()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun hasHealthForegroundServicePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startGenerating() {
        if (generatingJob?.isActive == true) return
        lastRecordTime = Instant.now()
        generatingJob = scope.launch {
            while (true) {
                delay(1000)
                val steps = Random.nextInt(1, 6)
                val now = Instant.now()
                try {
                    HealthConnectHelper.writeSteps(applicationContext, lastRecordTime, now, steps)
                    totalSteps += steps
                } catch (_: Exception) {
                    // 寫入失敗（例如權限被收回）時略過這一筆，下一秒重試
                }
                lastRecordTime = now
                notificationManager.notify(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    private fun stopGenerating() {
        generatingJob?.cancel()
        generatingJob = null
    }

    override fun onDestroy() {
        generatingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val statusText = getString(R.string.notif_status_running, totalSteps)
        val exitPendingIntent = actionPendingIntent(ACTION_EXIT, 102)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.action_exit), exitPendingIntent)
            .build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, StepGeneratorService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_EXIT = "com.pan.liwe.step.action.EXIT"
        private const val CHANNEL_ID = "step_generator_channel"
        private const val NOTIFICATION_ID = 1
    }
}
