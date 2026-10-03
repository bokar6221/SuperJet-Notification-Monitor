package com.superjet.notificationmonitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

class NotificationReaderService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
            .orEmpty()

        val body = when {
            bigText.isNotBlank() -> bigText
            lines.isNotBlank() -> lines
            text.isNotBlank() -> text
            else -> ""
        }

        if (title.isBlank() && body.isBlank()) return

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val eventId = sha256(
            listOf(
                sbn.key,
                sbn.postTime.toString(),
                sbn.packageName,
                title,
                body
            ).joinToString("|")
        )

        val parsed = NotificationParserEngine.parse(
            appName = appName,
            packageName = sbn.packageName,
            title = title,
            body = body
        )

        NotificationStore.add(
            this,
            NotificationItem(
                eventId = eventId,
                app = appName,
                packageName = sbn.packageName,
                title = title,
                text = body,
                time = sbn.postTime,
                provider = parsed.provider,
                transactionType = parsed.transactionType,
                amount = parsed.amount,
                reference = parsed.reference,
                senderPhone = parsed.senderPhone,
                recipientAccount = parsed.recipientAccount,
                parseStatus = parsed.status,
                isPaymentNotification = parsed.isPaymentNotification
            )
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
