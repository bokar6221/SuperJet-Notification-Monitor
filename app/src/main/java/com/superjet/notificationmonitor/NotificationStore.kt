package com.superjet.notificationmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NotificationItem(
    val app: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long
)

object NotificationStore {
    private const val PREFS = "notification_store"
    private const val KEY = "items"
    private const val MAX = 300

    fun add(context: Context, item: NotificationItem) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = JSONArray(p.getString(KEY, "[]"))
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("app", item.app)
            put("packageName", item.packageName)
            put("title", item.title)
            put("text", item.text)
            put("time", item.time)
        })
        for (i in 0 until minOf(old.length(), MAX - 1)) arr.put(old.getJSONObject(i))
        p.edit().putString(KEY, arr.toString()).apply()
    }

    fun all(context: Context): List<NotificationItem> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(p.getString(KEY, "[]"))
        val out = mutableListOf<NotificationItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(NotificationItem(
                o.optString("app"),
                o.optString("packageName"),
                o.optString("title"),
                o.optString("text"),
                o.optLong("time")
            ))
        }
        return out
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
