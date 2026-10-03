package com.superjet.notificationmonitor

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
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

        val access = Button(this).apply {
            text = "فتح صلاحية قراءة الإشعارات"
            setOnClickListener {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
        }

        val connection = Button(this).apply {
            text = "إعداد اتصال SuperJet"
            setOnClickListener { showConnectionDialog() }
        }

        val sync = Button(this).apply {
            text = "مزامنة عمليات الدفع الآن"
            setOnClickListener {
                NotificationSyncScheduler.enqueue(this@MainActivity)
                status.text = "تم طلب المزامنة..."
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
        layout.addView(access)
        layout.addView(connection)
        layout.addView(sync)
        layout.addView(clear)
        layout.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)

        NotificationSyncScheduler.enqueue(this)
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.refresh()
            updateStatus()
        }
    }

    private fun updateStatus() {
        val pending = NotificationStore.pending(this).size
        val configured = SecureConfig.getServerUrl(this).isNotBlank() && SecureConfig.getToken(this).isNotBlank()
        status.text = "قارئ الإشعارات يعمل — السجلات: " + adapter.itemCount +
            " — دفع معلقة: " + pending +
            " — الاتصال: " + if (configured) "مُعد" else "غير مُعد"
    }

    private fun showConnectionDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        val url = EditText(this).apply {
            hint = "عنوان السيرفر الأساسي"
            singleLine = true
            setText(SecureConfig.getServerUrl(this@MainActivity))
        }
        val token = EditText(this).apply {
            hint = "Android Token"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(SecureConfig.getToken(this@MainActivity))
        }
        box.addView(url)
        box.addView(token)

        AlertDialog.Builder(this)
            .setTitle("إعداد اتصال SuperJet")
            .setMessage("التطبيق يضيف /payment/android-notification تلقائيًا. استخدم HTTPS. الـToken يُحفظ باستخدام Android Keystore.")
            .setView(box)
            .setPositiveButton("حفظ ومزامنة") { _, _ ->
                SecureConfig.setServerUrl(this, url.text.toString())
                SecureConfig.setToken(this, token.text.toString())
                NotificationSyncScheduler.enqueue(this)
                updateStatus()
            }
            .setNegativeButton("إلغاء", null)
            .show()
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
            append("المزامنة: ").append(if (x.synced) "تم الإرسال للسيرفر" else "معلقة").append("\n")
            if (x.transactionType != "UNKNOWN") append("النوع: ").append(typeLabel(x.transactionType)).append("\n")
            x.amount?.let { append("المبلغ: ").append(String.format(Locale.US, "%.2f", it)).append(" جنيه\n") }
            if (x.reference.isNotBlank()) append("رقم العملية: ").append(x.reference).append("\n")
            if (x.senderPhone.isNotBlank()) append("رقم المحول: ").append(x.senderPhone).append("\n")
            if (x.recipientAccount.isNotBlank()) append("رقم المستلم: ").append(x.recipientAccount).append("\n")
            if (x.transactionDate.isNotBlank()) append("تاريخ العملية: ").append(x.transactionDate).append("\n")
            if (x.transactionTime.isNotBlank()) append("وقت العملية: ").append(x.transactionTime).append("\n")
            if (x.syncError.isNotBlank()) append("خطأ المزامنة: ").append(x.syncError).append("\n")
        } else ""

        holder.view.text =
            header + "\n" +
            details +
            "العنوان: " + x.title + "\n" +
            "النص الكامل:\n" + x.text + "\n" +
            "وقت استقبال الإشعار: " + time + "\n" +
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
        else -> status.ifBlank { "غير معروف" }
    }
    private fun typeLabel(type: String): String = when (type) {
        "TRANSFER_IN" -> "تحويل وارد"
        "TRANSFER_OUT" -> "تحويل صادر"
        else -> type
    }
}
