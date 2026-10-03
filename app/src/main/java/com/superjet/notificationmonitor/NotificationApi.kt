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

object NotificationApi {
    private const val ENDPOINT = "/payment/android-notification"
    private val RECEIVED_FORMAT = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        Locale.US
    )

    fun syncOne(context: Context, item: NotificationItem): Result {
        val base = SecureConfig.getServerUrl(context).trimEnd('/')
        val token = SecureConfig.getToken(context)

        if (base.isBlank()) return Result.failure(
            IllegalStateException("SERVER_URL_NOT_CONFIGURED")
        )
        if (token.isBlank()) return Result.failure(
            IllegalStateException("ANDROID_TOKEN_NOT_CONFIGURED")
        )

        val connection = (URL(base + ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-SuperJet-Android-Token", token)
            setRequestProperty("X-Idempotency-Key", item.eventId)
        }

        val receivedAt = Instant.ofEpochMilli(item.time)
            .atZone(ZoneId.systemDefault())
            .format(RECEIVED_FORMAT)

        val json = JSONObject().apply {
            put("event_id", item.eventId)
            put("package", item.packageName)
            put("package_name", item.packageName)
            put("app_label", item.app)
            put("app", item.app)
            put("title", item.title)
            put("text", item.text)
            put("raw_notification", item.text)
            if (item.amount != null) put("amount", item.amount)
            put("reference", item.reference)
            put("sender_phone", item.senderPhone)
            put("recipient_phone", item.recipientAccount)
            put("transaction_date", item.transactionDate)
            put("transaction_time", item.transactionTime)
            put("transaction_type", item.transactionType)
            put("provider", item.provider)
            put("parse_status", item.parseStatus)
            put("received_at", receivedAt)
        }

        return try {
            connection.outputStream.use {
                it.write(json.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }
            connection.disconnect()

            if (code in 200..299) {
                Result.success(response)
            } else {
                Result.failure(IllegalStateException("HTTP_" + code + ":" + response))
            }
        } catch (e: Exception) {
            connection.disconnect()
            Result.failure(e)
        }
    }
}
