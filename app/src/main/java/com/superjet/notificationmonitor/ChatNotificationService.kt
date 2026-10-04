package com.superjet.notificationmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

class ChatNotificationService : Service() {
    companion object {
        private const val CHANNEL = "superjet_staff_chat"
        private const val SERVICE_ID = 3011
        private const val PREFS = "superjet_chat_state"
        private const val LAST_ID = "last_chat_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val n = serviceNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                SERVICE_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(SERVICE_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SecureConfig.getToken(this).isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!pollingStarted) {
            pollingStarted = true
            scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var since = prefs.getLong(LAST_ID, 0L)
        while (scope.isActive) {
            try {
                val result = StaffClient.pollChatNotifications(this@ChatNotificationService, since)
                if (result.ok) {
                    val messages = result.body.optJSONArray("messages") ?: JSONArray()
                    for (i in 0 until messages.length()) {
                        val m = messages.optJSONObject(i) ?: continue
                        val id = m.optLong("id", 0L)
                        if (id > since) {
                            showMessageNotification(
                                m.optString("customer_name", "عميل"),
                                m.optString("booking_id", ""),
                                m.optString("message", "")
                            )
                            since = id
                            prefs.edit().putLong(LAST_ID, since).apply()
                        }
                    }
                } else if (
                    result.error.contains("HTTP_401") ||
                    result.error.contains("STAFF_AUTH_REQUIRED")
                ) {
                    stopSelf()
                    break
                }
            } catch (_: Throwable) {
                // Network failure: keep the service alive and retry.
            }
            delay(1000)
        }
    }

    private fun showMessageNotification(customer: String, booking: String, message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        val intent = Intent(this, MainActivityPro::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_booking_id", booking)
        }
        val requestCode = (booking.hashCode() and 0x7fffffff)
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            if (booking.isNotBlank()) append("$booking • ")
            append(customer)
            if (message.isNotBlank()) append(": $message")
        }

        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.superjet_logo)
            .setContentTitle("SuperJet • رسالة عميل جديدة")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { text }))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        manager.notify(10000 + requestCode % 100000, n)
    }

    private fun serviceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.superjet_logo)
            .setContentTitle("SuperJet Staff")
            .setContentText("مراقبة رسائل العملاء وإشعارات الدفع")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "SuperJet Staff",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    override fun onDestroy() {
        pollingStarted = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
