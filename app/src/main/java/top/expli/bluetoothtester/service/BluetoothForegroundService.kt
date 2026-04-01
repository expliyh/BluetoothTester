package top.expli.bluetoothtester.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BluetoothForegroundService : Service() {

    enum class Reason {
        SppConnection,
        GattConnection,
        L2capConnection,
        Advertising,
        SpeedTest
    }

    companion object {
        const val CHANNEL_ID = "bt_tester_foreground"
        const val ACTION_STOP_REASON = "top.expli.bluetoothtester.action.STOP_REASON"
        const val EXTRA_REASON = "reason"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "BtForegroundService"
    }

    private val activeReasons = mutableSetOf<Reason>()
    private var channelCreated = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_REASON -> {
                val reasonName = intent.getStringExtra(EXTRA_REASON)
                val reason = reasonName?.let { name ->
                    runCatching { Reason.valueOf(name) }.getOrNull()
                }
                if (reason != null) {
                    stopByReason(reason)
                }
            }
            else -> {
                // Normal start — reason should be added via addReason() by the caller
            }
        }
        return START_STICKY
    }

    fun addReason(reason: Reason) {
        val wasEmpty = activeReasons.isEmpty()
        activeReasons.add(reason)
        Log.d(TAG, "addReason: $reason, active=$activeReasons")
        if (wasEmpty) {
            ensureNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        } else {
            updateNotification()
        }
    }

    fun removeReason(reason: Reason) {
        activeReasons.remove(reason)
        Log.d(TAG, "removeReason: $reason, active=$activeReasons")
        if (activeReasons.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            updateNotification()
        }
    }

    fun stopByReason(reason: Reason) {
        // TODO: Notify the corresponding module to stop its operation
        // For now, just remove the reason
        removeReason(reason)
    }

    fun updateSpeedTestProgress(progress: Int, max: Int) {
        if (activeReasons.contains(Reason.SpeedTest)) {
            val notification = buildNotification(speedTestProgress = progress, speedTestMax = max)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    // ─── Notification ───

    private fun ensureNotificationChannel() {
        if (channelCreated) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "蓝牙后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "蓝牙连接、广播和测速的后台服务通知"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        channelCreated = true
    }

    private fun buildNotification(
        speedTestProgress: Int = 0,
        speedTestMax: Int = 0
    ): android.app.Notification {
        val contentText = activeReasons.joinToString("、") { reason ->
            when (reason) {
                Reason.SppConnection -> "SPP 连接"
                Reason.GattConnection -> "GATT 连接"
                Reason.L2capConnection -> "L2CAP 连接"
                Reason.Advertising -> "BLE 广播"
                Reason.SpeedTest -> "测速中"
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("蓝牙测试工具")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Add per-reason stop action buttons
        for (reason in activeReasons) {
            val stopIntent = Intent(this, BluetoothForegroundService::class.java).apply {
                action = ACTION_STOP_REASON
                putExtra(EXTRA_REASON, reason.name)
            }
            val pendingIntent = PendingIntent.getService(
                this,
                reason.ordinal,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val label = when (reason) {
                Reason.SppConnection -> "停止 SPP"
                Reason.GattConnection -> "停止 GATT"
                Reason.L2capConnection -> "停止 L2CAP"
                Reason.Advertising -> "停止广播"
                Reason.SpeedTest -> "停止测速"
            }
            builder.addAction(0, label, pendingIntent)
        }

        // Speed test progress
        if (activeReasons.contains(Reason.SpeedTest) && speedTestMax > 0) {
            builder.setProgress(speedTestMax, speedTestProgress, false)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        activeReasons.clear()
        Log.d(TAG, "Service destroyed")
    }
}
