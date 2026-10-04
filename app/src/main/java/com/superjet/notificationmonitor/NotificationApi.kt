package com.superjet.notificationmonitor

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NotificationApiResult(
    val ok: Boolean,
    val error: String = ""
)

object NotificationApi {
    private const val ENDPOINT = "/api/mobile/android-notification"
    private val RECEIVED_FORMAT = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        Locale.US
    )

    fun syncOne(context: Context, item: NotificationItem): NotificationApiResult {
        val token = SecureConfig.getToken(context)
        if (token.isBlank()) return NotificationApiResult(false, "LOGIN_REQUIRED")

        val connection = (URL(SecureConfig.getServerUrl() + ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("X-SuperJet-Device-Id", StaffApi.deviceId(context))
            setRequestProperty("X-Idempotency-Key", item.eventId)
        }

        val receivedAt = Instant.ofEpochMilli(item.time)
            .atZone(ZoneId.systemDefault())
            .format(RECEIVED_FORMAT)

        val json = JSONObject()
        json.put("event_id", item.eventId)
        json.put("package", item.packageName)
        json.put("package_name", item.packageName)
        json.put("app_label", item.app)
        json.put("app", item.app)
        json.put("title", item.title)
        json.put("text", item.text)
        json.put("raw_notification", item.text)
        if (item.amount != null) json.put("amount", item.amount)
        json.put("reference", item.reference)
        json.put("sender_phone", item.senderPhone)
        json.put("recipient_phone", item.recipientAccount)
        json.put("transaction_date", item.transactionDate)
        json.put("transaction_time", item.transactionTime)
        json.put("transaction_type", item.transactionType)
        json.put("provider", item.provider)
        json.put("parse_status", item.parseStatus)
        json.put("received_at", receivedAt)

        return try {
            connection.outputStream.use { output ->
                output.write(json.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else ""
            if (code in 200..299) NotificationApiResult(true)
            else NotificationApiResult(false, "HTTP_" + code + ":" + response)
        } catch (e: Exception) {
            NotificationApiResult(false, e.javaClass.simpleName + ":" + (e.message ?: "network error"))
        } finally {
            connection.disconnect()
        }
    }
}