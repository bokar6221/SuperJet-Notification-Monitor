package com.superjet.notificationmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NotificationItem(
    val eventId: String,
    val app: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val provider: String,
    val transactionType: String,
    val amount: Double?,
    val reference: String,
    val senderPhone: String,
    val recipientAccount: String,
    val transactionDate: String,
    val transactionTime: String,
    val parseStatus: String,
    val isPaymentNotification: Boolean,
    val synced: Boolean = false,
    val syncError: String = ""
)

object NotificationStore {
    private const val PREFS = "notification_store"
    private const val KEY = "items"
    private const val MAX = 300

    @Synchronized
    fun add(context: Context, item: NotificationItem) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString(KEY, "[]"))
        for (i in 0 until old.length()) {
            if (old.getJSONObject(i).optString("eventId") == item.eventId) return
        }
        val arr = JSONArray()
        arr.put(toJson(item))
        for (i in 0 until minOf(old.length(), MAX - 1)) {
            arr.put(old.getJSONObject(i))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun markSynced(context: Context, eventId: String, error: String = "") {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString(KEY, "[]"))
        val arr = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            if (item.optString("eventId") == eventId) {
                item.put("synced", error.isBlank())
                item.put("syncError", error)
            }
            arr.put(item)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun pending(context: Context): List<NotificationItem> {
        return all(context).filter { it.isPaymentNotification && !it.synced }
    }

    fun all(context: Context): List<NotificationItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        val result = mutableListOf<NotificationItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(
                NotificationItem(
                    eventId = o.optString("eventId"),
                    app = o.optString("app"),
                    packageName = o.optString("packageName"),
                    title = o.optString("title"),
                    text = o.optString("text"),
                    time = o.optLong("time"),
                    provider = o.optString("provider"),
                    transactionType = o.optString("transactionType"),
                    amount = if (o.isNull("amount")) null else o.optDouble("amount"),
                    reference = o.optString("reference"),
                    senderPhone = o.optString("senderPhone"),
                    recipientAccount = o.optString("recipientAccount"),
                    transactionDate = o.optString("transactionDate"),
                    transactionTime = o.optString("transactionTime"),
                    parseStatus = o.optString("parseStatus"),
                    isPaymentNotification = o.optBoolean("isPaymentNotification"),
                    synced = o.optBoolean("synced", false),
                    syncError = o.optString("syncError")
                )
            )
        }
        return result
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    private fun toJson(item: NotificationItem): JSONObject {
        return JSONObject().apply {
            put("eventId", item.eventId)
            put("app", item.app)
            put("packageName", item.packageName)
            put("title", item.title)
            put("text", item.text)
            put("time", item.time)
            put("provider", item.provider)
            put("transactionType", item.transactionType)
            if (item.amount != null) put("amount", item.amount) else put("amount", JSONObject.NULL)
            put("reference", item.reference)
            put("senderPhone", item.senderPhone)
            put("recipientAccount", item.recipientAccount)
            put("transactionDate", item.transactionDate)
            put("transactionTime", item.transactionTime)
            put("parseStatus", item.parseStatus)
            put("isPaymentNotification", item.isPaymentNotification)
            put("synced", item.synced)
            put("syncError", item.syncError)
        }
    }
}