package com.superjet.notificationmonitor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: NotificationAdapter
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "SuperJet Payment Monitor"
            textSize = 24f
        }

        status = TextView(this).apply {
            text = "قارئ الإشعارات يعمل."
            textSize = 16f
            setPadding(0, 16, 0, 16)
        }

        val settings = Button(this).apply {
            text = "فتح صلاحية قراءة الإشعارات"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }

        val clear = Button(this).apply {
            text = "مسح السجل"
            setOnClickListener {
                NotificationStore.clear(this@MainActivity)
                adapter.refresh()
                updateStatus()
            }
        }

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        adapter = NotificationAdapter(this)
        list.adapter = adapter

        layout.addView(title)
        layout.addView(status)
        layout.addView(settings)
        layout.addView(clear)
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.refresh()
            updateStatus()
        }
    }

    private fun updateStatus() {
        status.text = "قارئ الإشعارات يعمل — عدد السجلات: " + adapter.itemCount
    }
}

class NotificationAdapter(private val activity: AppCompatActivity) :
    RecyclerView.Adapter<NotificationAdapter.VH>() {

    private var items = NotificationStore.all(activity)

    fun refresh() {
        items = NotificationStore.all(activity)
        notifyDataSetChanged()
    }

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 14f
            setPadding(12, 18, 12, 18)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val x = items[position]
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(x.time))

        val header = if (x.isPaymentNotification) {
            "💳 إشعار دفع — " + providerLabel(x.provider)
        } else {
            "🔔 إشعار عادي"
        }

        val details = if (x.isPaymentNotification) buildString {
            append("الحالة: ").append(statusLabel(x.parseStatus)).append("\n")
            if (x.transactionType != "UNKNOWN") {
                append("النوع: ").append(typeLabel(x.transactionType)).append("\n")
            }
            x.amount?.let {
                append("المبلغ: ").append(String.format(Locale.US, "%.2f", it))
                    .append(" جنيه\n")
            }
            if (x.reference.isNotBlank()) append("رقم العملية: ").append(x.reference).append("\n")
            if (x.senderPhone.isNotBlank()) append("رقم المحول: ").append(x.senderPhone).append("\n")
            if (x.recipientAccount.isNotBlank()) append("رقم المستلم: ").append(x.recipientAccount).append("\n")
        } else ""

        holder.view.text =
            header + "\n" +
            details +
            "العنوان: " + x.title + "\n" +
            "النص الكامل:\n" + x.text + "\n" +
            "الوقت: " + time + "\n" +
            "Package: " + x.packageName
    }

    override fun getItemCount() = items.size

    private fun providerLabel(provider: String): String = when (provider) {
        "VODAFONE_CASH" -> "Vodafone Cash"
        "ORANGE_CASH" -> "Orange Cash"
        "ETISALAT_CASH" -> "Etisalat Cash"
        "WE_PAY" -> "WE Pay"
        "INSTAPAY" -> "InstaPay"
        else -> provider.ifBlank { "غير معروف" }
    }

    private fun statusLabel(status: String): String = when (status) {
        "PARSED" -> "تم تحليل البيانات"
        "UNPARSED" -> "إشعار دفع غير مكتمل التحليل"
        "DUPLICATE" -> "مكرر"
        else -> status.ifBlank { "غير معروف" }
    }

    private fun typeLabel(type: String): String = when (type) {
        "TRANSFER_IN" -> "تحويل وارد"
        "TRANSFER_OUT" -> "تحويل صادر"
        else -> type
    }
}
