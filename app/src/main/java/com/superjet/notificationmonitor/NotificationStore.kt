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
    val parseStatus: String,
    val isPaymentNotification: Boolean
)

object NotificationStore {
    private const val PREFS = "notification_store"
    private const val KEY = "items"
    private const val MAX = 300

    fun add(context: Context, item: NotificationItem) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(p.getString(KEY, "[]"))
        for (i in 0 until old.length()) {
            if (old.getJSONObject(i).optString("eventId") == item.eventId) return
        }

        val arr = JSONArray()
        arr.put(JSONObject().apply {
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
            put("parseStatus", item.parseStatus)
            put("isPaymentNotification", item.isPaymentNotification)
        })
        for (i in 0 until minOf(old.length(), MAX - 1)) {
            arr.put(old.getJSONObject(i))
        }
        p.edit().putString(KEY, arr.toString()).apply()
    }

    fun all(context: Context): List<NotificationItem> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString(KEY, "[]"))
        val out = mutableListOf<NotificationItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
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
                    parseStatus = o.optString("parseStatus"),
                    isPaymentNotification = o.optBoolean("isPaymentNotification")
                )
            )
        }
        return out
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
