package com.superjet.notificationmonitor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var loginUser: EditText? = null
    private var loginPass: EditText? = null
    private var statusText: TextView? = null
    private var operationsBox: LinearLayout? = null
    private var selectedOperationId: String = ""

    private val ticketPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty() || selectedOperationId.isBlank()) return@registerForActivityResult
        showFinalRefDialog(uris.take(4))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(root)
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (SecureConfig.getToken(this).isNotBlank() && operationsBox != null) {
            refreshDashboard()
        }
    }

    private fun clearRoot() {
        root.removeAllViews()
    }

    private fun showHome() {
        if (SecureConfig.getToken(this).isBlank()) showLogin() else showDashboard()
    }

    private fun showLogin() {
        clearRoot()
        val title = TextView(this).apply {
            text = "SuperJet Staff"
            textSize = 28f
        }
        val sub = TextView(this).apply {
            text = "نظام الموظفين للدفع والحجوزات والمتابعة"
            textSize = 16f
            setPadding(0, 8, 0, 22)
        }
        loginUser = EditText(this).apply {
            hint = "اسم المستخدم"
            setSingleLine()
        }
        loginPass = EditText(this).apply {
            hint = "كلمة المرور"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val login = Button(this).apply {
            text = "دخول آمن"
            setOnClickListener { doLogin() }
        }
        val fixed = TextView(this).apply {
            text = "إعدادات الاتصال ثابتة داخل التطبيق ولا يحتاج الموظف لإدخال Server أو Token."
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }
        root.addView(title)
        root.addView(sub)
        root.addView(loginUser)
        root.addView(loginPass)
        root.addView(login)
        root.addView(fixed)
    }

    private fun doLogin() {
        val u = loginUser?.text?.toString()?.trim().orEmpty()
        val p = loginPass?.text?.toString().orEmpty()
        if (u.isBlank() || p.isBlank()) {
            Toast.makeText(this, "أدخل اسم المستخدم وكلمة المرور.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "جاري تسجيل الدخول...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { StaffApi.login(this@MainActivity, u, p) }
            if (r.ok) {
                NotificationSyncScheduler.enqueue(this@MainActivity)
                showDashboard()
            } else {
                Toast.makeText(this@MainActivity, r.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDashboard() {
        clearRoot()
        statusText = TextView(this).apply { textSize = 15f; setPadding(0, 8, 0, 12) }
        root.addView(TextView(this).apply {
            text = "SuperJet Staff"
            textSize = 26f
        })
        root.addView(statusText)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refresh = Button(this).apply {
            text = "تحديث"
            setOnClickListener { refreshDashboard() }
        }
        val sync = Button(this).apply {
            text = "مزامنة الدفع"
            setOnClickListener { manualSync() }
        }
        val access = Button(this).apply {
            text = "صلاحية الإشعارات"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        val logout = Button(this).apply {
            text = "خروج"
            setOnClickListener {
                SecureConfig.clearSession(this@MainActivity)
                NotificationSyncScheduler.cancel(this@MainActivity)
                showLogin()
            }
        }
        bar.addView(refresh, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(sync, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(access, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(logout, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(bar)

        val scroll = ScrollView(this)
        operationsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 24)
        }
        scroll.addView(operationsBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        refreshDashboard()
    }

    private fun refreshDashboard() {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { StaffApi.dashboard(this@MainActivity) }
            if (!r.ok) {
                if (r.error.startsWith("HTTP_401") || r.error.contains("STAFF_AUTH_REQUIRED")) {
                    SecureConfig.clearSession(this@MainActivity)
                    showLogin()
                    return@launch
                }
                statusText?.text = "تعذر تحديث البيانات: " + r.error
                return@launch
            }
            val employee = r.body.optJSONObject("employee") ?: JSONObject()
            val d = r.body.optJSONObject("dashboard") ?: JSONObject()
            val byMethod = d.optJSONArray("by_method") ?: org.json.JSONArray()
            var walletTotal = 0.0
            var instaTotal = 0.0
            for (i in 0 until byMethod.length()) {
                val x = byMethod.optJSONObject(i) ?: continue
                when (x.optString("payment_method")) {
                    "محفظة إلكترونية" -> walletTotal = x.optDouble("total", 0.0)
                    "إنستا باي" -> instaTotal = x.optDouble("total", 0.0)
                }
            }
            statusText?.text = employee.optString("name") + " • " +
                (if (employee.optString("role") == "manager") "مدير" else "خدمة عملاء") +
                " • " + employee.optString("status") +
                "\nالعمليات اليوم: " + d.optJSONObject("today")?.optInt("operations", 0) +
                " • إجمالي اليوم: " + d.optJSONObject("today")?.optDouble("total", 0.0) + " جنيه" +
                "\nمحافظ: " + walletTotal + " • InstaPay: " + instaTotal + " جنيه"
            renderOperations(d.optJSONArray("operations") ?: JSONArray())
        }
    }

    private fun renderOperations(arr: JSONArray) {
        val box = operationsBox ?: return
        box.removeAllViews()
        if (arr.length() == 0) {
            box.addView(TextView(this).apply {
                text = "لا توجد عمليات حالياً."
                textSize = 16f
                setPadding(12, 20, 12, 20)
            })
            return
        }
        for (i in 0 until arr.length()) {
            val op = arr.optJSONObject(i) ?: continue
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(0xFF1A2028.toInt())
            }
            val title = TextView(this).apply {
                textSize = 17f
                text = op.optString("booking_id") + " • " + op.optString("status")
            }
            val info = TextView(this).apply {
                text = op.optString("customer_name") + "\n" +
                    op.optString("from_name") + " → " + op.optString("to_name") + "\n" +
                    op.optString("travel_date") + " " + op.optString("travel_time") + "\n" +
                    "المبلغ: " + op.optDouble("amount", 0.0) + " جنيه • " +
                    op.optString("payment_method")
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val approve = Button(this).apply {
                text = "تأكيد الدفع"
                setOnClickListener { act(op.optString("operation_id"), true) }
            }
            val reject = Button(this).apply {
                text = "رفض"
                setOnClickListener { rejectPrompt(op.optString("operation_id")) }
            }
            val ticket = Button(this).apply {
                text = "التذكرة"
                setOnClickListener {
                    selectedOperationId = op.optString("operation_id")
                    ticketPicker.launch("image/*")
                }
            }
            buttons.addView(approve, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(reject, LinearLayout.LayoutParams(0, -2, 1f))
            buttons.addView(ticket, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(title)
            card.addView(info)
            card.addView(buttons)
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 0, 0, 12)
            box.addView(card, lp)
        }
    }

    private fun act(operationId: String, approve: Boolean) {
        if (operationId.isBlank()) return
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                if (approve) StaffApi.approve(this@MainActivity, operationId)
                else StaffApi.reject(this@MainActivity, operationId, "رفض موظف")
            }
            Toast.makeText(this@MainActivity, if (r.ok) "تم الحفظ." else r.error, Toast.LENGTH_LONG).show()
            if (r.ok) refreshDashboard()
        }
    }

    private fun rejectPrompt(operationId: String) {
        val input = EditText(this).apply { hint = "سبب الرفض" }
        android.app.AlertDialog.Builder(this)
            .setTitle("رفض عملية الدفع")
            .setView(input)
            .setPositiveButton("رفض") { _, _ ->
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) {
                        StaffApi.reject(this@MainActivity, operationId, input.text.toString().ifBlank { "رفض موظف" })
                    }
                    Toast.makeText(this@MainActivity, if (r.ok) "تم رفض العملية." else r.error, Toast.LENGTH_LONG).show()
                    if (r.ok) refreshDashboard()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showFinalRefDialog(uris: List<Uri>) {
        val input = EditText(this).apply {
            hint = "رقم الحجز الفعلي في SuperJet (اختياري)"
            setSingleLine()
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("رفع التذكرة الفعلية")
            .setMessage("بعد إتمام الحجز الحقيقي في SuperJet اختر صورة أو صور التذكرة.")
            .setView(input)
            .setPositiveButton("إرسال للعميل") { _, _ ->
                lifecycleScope.launch {
                    Toast.makeText(this@MainActivity, "جاري رفع وإرسال التذكرة...", Toast.LENGTH_SHORT).show()
                    val r = withContext(Dispatchers.IO) {
                        StaffApi.uploadTicket(this@MainActivity, selectedOperationId, uris, input.text.toString())
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (r.ok) "تم إرسال التذكرة للعميل." else r.error,
                        Toast.LENGTH_LONG
                    ).show()
                    if (r.ok) refreshDashboard()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun manualSync() {
        NotificationSyncScheduler.cancel(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val pending = NotificationStore.pending(this@MainActivity)
            var sent = 0
            var failed = 0
            for (item in pending) {
                val r = NotificationApi.syncOne(this@MainActivity, item)
                if (r.ok) {
                    NotificationStore.markSynced(this@MainActivity, item.eventId)
                    sent++
                } else {
                    NotificationStore.markSynced(this@MainActivity, item.eventId, r.error)
                    failed++
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "مزامنة: تم " + sent + " • فشل " + failed, Toast.LENGTH_LONG).show()
                NotificationSyncScheduler.enqueue(this@MainActivity)
                refreshDashboard()
            }
        }
    }
}