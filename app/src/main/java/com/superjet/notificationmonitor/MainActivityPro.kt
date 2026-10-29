package com.superjet.notificationmonitor

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivityPro : AppCompatActivity() {

    private val BG = "#0B1020"
    private val SURFACE = "#131B2E"
    private val SURFACE2 = "#1C2742"
    private val TEXT = "#F8FAFC"
    private val MUTED = "#94A3B8"
    private val GREEN = "#10B981"
    private val RED = "#EF4444"
    private val BLUE = "#3B82F6"
    private val AMBER = "#F59E0B"

    private lateinit var box: LinearLayout
    private lateinit var statusLine: TextView
    private val tabButtons = mutableMapOf<String, Button>()

    private var currentTab = "bookings"
    private var summary = JSONObject()
    private var payments = JSONArray()
    private var bookings = JSONArray()
    private var trips = JSONArray()

    // حفظ حالة العمليات محلياً أثناء التأكيد الأول والثاني لمنع اختفائها
    private val localStageOverrides = mutableMapOf<String, String>()
    private var isRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // الشريط العلوي
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleBox.addView(txt("SuperJet Staff Monitor", 18f, TEXT, true))
        statusLine = txt("جارٍ الاتصال بالسيرفر...", 11.5f, MUTED, false)
        titleBox.addView(statusLine)

        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val searchBtn = button("🔍 البحث والسجل", BLUE, true).apply {
            setOnClickListener { showSearchDialog() }
        }
        val refreshBtn = button("🔄", SURFACE2, true).apply {
            setOnClickListener { refresh(silent = false) }
        }
        val settingsBtn = button("⚙️", SURFACE2, true).apply {
            setOnClickListener { showSettingsDialog() }
        }

        header.addView(searchBtn, wrap().apply { marginEnd = dp(6) })
        header.addView(refreshBtn, wrap().apply { marginEnd = dp(6) })
        header.addView(settingsBtn, wrap())
        root.addView(header, match().apply { bottomMargin = dp(10) })

        // شريط التبويبات السريع
        val tabsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tabs = listOf(
            "bookings" to "الحجوزات",
            "payments" to "المدفوعات",
            "trips" to "الرحلات",
            "logs" to "السجل"
        )
        for ((key, label) in tabs) {
            val b = button(label, if (currentTab == key) BLUE else SURFACE2, true).apply {
                setOnClickListener {
                    if (currentTab != key) {
                        currentTab = key
                        updateTabStyles()
                        // انتقال فوري بدون انتظار السيرفر
                        render()
                    }
                }
            }
            tabButtons[key] = b
            tabsRow.addView(b, weight().apply { marginEnd = dp(5) })
        }
        root.addView(tabsRow, match().apply { bottomMargin = dp(10) })

        // مساحة المحتوى
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(box, match())
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        // التحميل الأولي والتحديث التلقائي الخفيف في الخلفية
        refresh(silent = false)
        lifecycleScope.launch {
            while (isActive) {
                delay(12000)
                refresh(silent = true)
            }
        }
    }

    private fun updateTabStyles() {
        for ((key, btn) in tabButtons) {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(if (key == currentTab) BLUE else SURFACE2))
                cornerRadius = dp(8).toFloat()
            }
            btn.background = bg
        }
    }

    // اتصال متوازي بالسيرفر لمنع التثقيل
    private fun refresh(silent: Boolean = true) {
        if (isRefreshing) return
        isRefreshing = true
        if (!silent) statusLine.text = "جارٍ تحديث البيانات..."

        lifecycleScope.launch {
            try {
                coroutineScope {
                    val sDef = async(Dispatchers.IO) { runCatching { StaffClient.summary(this@MainActivityPro) }.getOrNull() }
                    val pDef = async(Dispatchers.IO) { runCatching { StaffClient.payments(this@MainActivityPro) }.getOrNull() }
                    val bDef = async(Dispatchers.IO) { runCatching { StaffClient.bookings(this@MainActivityPro) }.getOrNull() }
                    val tDef = async(Dispatchers.IO) { runCatching { StaffClient.trips(this@MainActivityPro) }.getOrNull() }

                    val sRes = sDef.await()
                    val pRes = pDef.await()
                    val bRes = bDef.await()
                    val tRes = tDef.await()

                    if (sRes != null && sRes.ok) summary = sRes.body
                    if (pRes != null && pRes.ok) {
                        payments = pRes.body.optJSONArray("payments")
                            ?: pRes.body.optJSONArray("items")
                            ?: JSONArray()
                    }
                    if (bRes != null && bRes.ok) {
                        bookings = bRes.body.optJSONArray("operations")
                            ?: bRes.body.optJSONArray("bookings")
                            ?: JSONArray()
                    }
                    if (tRes != null && tRes.ok) {
                        trips = tRes.body.optJSONArray("trips") ?: JSONArray()
                    }
                }
                val timeNow = SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date())
                statusLine.text = "متصل • آخر تحديث: $timeNow"
                render()
            } catch (e: Exception) {
                statusLine.text = "تنبيه: ضعف في الاتصال بالسيرفر"
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun render() {
        box.removeAllViews()
        when (currentTab) {
            "bookings" -> renderBookings()
            "payments" -> renderPayments()
            "trips" -> renderTrips()
            "logs" -> renderLogs()
        }
    }

    // عرض عمليات الحجز الجارية فقط مع مراحل التأكيد الأول والثاني
    private fun renderBookings() {
        val activeList = mutableListOf<JSONObject>()
        for (i in 0 until bookings.length()) {
            val o = bookings.optJSONObject(i) ?: continue
            val opId = o.optString("operation_id").ifBlank { o.optString("booking_id") }
            val serverStatus = o.optString("status").uppercase(Locale.ROOT)

            // إذا كان السيرفر قد أكدها أو رفضها بالفعل أو تم تأكيدها محلياً المرحلة الثانية، تنتقل للبحث
            val effectiveStatus = localStageOverrides[opId] ?: serverStatus

            val isFinished = effectiveStatus in setOf(
                "CONFIRMED", "BOOKING_CONFIRMED", "COMPLETED", "TICKET_ISSUED",
                "REJECTED", "PAYMENT_REJECTED", "CANCELLED", "DECLINED"
            )
            if (!isFinished) {
                o.put("effective_status", effectiveStatus)
                activeList.add(o)
            }
        }

        if (activeList.isEmpty()) {
            box.addView(emptyCard("لا توجد عمليات حجز جارية حالياً.\nجميع العمليات المؤكدة والمرفوضة موجودة في (🔍 البحث والسجل)."))
            return
        }

        for (o in activeList) {
            val opId = o.optString("operation_id").ifBlank { o.optString("booking_id") }
            val bookingId = o.optString("booking_id")
            val effStatus = o.optString("effective_status")

            val c = card()
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            c.addView(b)

            val isSecondStage = effStatus in setOf(
                "WAITING_SECOND_CONFIRM", "PAYMENT_APPROVED",
                "BOOKING_IN_PROGRESS", "AWAITING_TICKET", "PROCESSING_SECOND"
            )
            val isProcessingFirst = effStatus == "PROCESSING_FIRST"
            val isProcessingSecond = effStatus == "PROCESSING_SECOND"

            val statusBadgeText = when {
                isProcessingFirst -> "⏳ جارٍ تنفيذ التأكيد الأول... (العملية قيد الانتظار)"
                isProcessingSecond -> "⏳ جارٍ تنفيذ التأكيد الثاني وإصدار الحجز..."
                isSecondStage -> "🟠 تم التأكيد الأول • بانتظار ضغط (التأكيد الثاني)"
                else -> "🔵 عملية جديدة • بانتظار التأكيد الأول"
            }

            val badgeColor = when {
                isProcessingFirst || isProcessingSecond -> AMBER
                isSecondStage -> BLUE
                else -> GREEN
            }

            b.addView(txt("عملية #$opId • حجز #$bookingId", 13.5f, TEXT, true))
            b.addView(txt(statusBadgeText, 12f, badgeColor, true), match().apply { topMargin = dp(4); bottomMargin = dp(6) })

            b.addView(
                txt(
                    o.optString("customer_name") + " (" + o.optString("customer_phone") + ")\n" +
                    o.optString("from_name") + " → " + o.optString("to_name") + "\n" +
                    o.optString("travel_date") + " • " + o.optString("travel_time") + "\n" +
                    "المبلغ: " + String.format(Locale.US, "%.2f", o.optDouble("amount", 0.0)) + " جنيه",
                    12.5f, MUTED, false
                ),
                match().apply { bottomMargin = dp(10) }
            )

            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            val yesText = when {
                isProcessingFirst -> "جارٍ التأكيد 1..."
                isProcessingSecond -> "جارٍ التأكيد 2..."
                isSecondStage -> "التأكيد الثاني ✓✓"
                else -> "التأكيد الأول ✓"
            }

            val yes = button(yesText, if (isSecondStage) BLUE else GREEN, false).apply {
                isEnabled = !isProcessingFirst && !isProcessingSecond
                alpha = if (isEnabled) 1.0f else 0.6f
            }
            val no = button("رفض ×", RED, false)
            val chat = button("محادثة 💬", SURFACE2, false)

            val canReject = !isSecondStage && !isProcessingFirst && !isProcessingSecond
            no.isEnabled = canReject
            if (!canReject) {
                no.text = "غير متاح"
                no.alpha = 0.55f
            }

            row.addView(yes, weight())
            row.addView(no, weight().apply { marginStart = dp(7) })
            row.addView(chat, weight().apply { marginStart = dp(7) })
            b.addView(row)

            chat.setOnClickListener { showChatDialog(opId, bookingId) }
            yes.setOnClickListener {
                if (!isSecondStage) {
                    confirmStageOne(opId)
                } else {
                    confirmStageTwo(opId)
                }
            }
            no.setOnClickListener {
                if (canReject) reject(opId)
                else toast("لا يمكن رفض العملية بعد اعتماد التأكيد الأول.")
            }

            box.addView(c, match().apply { bottomMargin = dp(9) })
        }
    }

    // المرحلة الأولى: تظل العملية ظاهرة وتتحول للتأكيد الثاني
    private fun confirmStageOne(id: String) {
        if (id.isBlank()) {
            toast("رقم العملية غير متاح.")
            return
        }
        localStageOverrides[id] = "PROCESSING_FIRST"
        render()

        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                StaffClient.operationAction(this@MainActivityPro, id, true)
            }
            if (r.ok) {
                localStageOverrides[id] = "WAITING_SECOND_CONFIRM"
                toast("تم التأكيد الأول بنجاح • اضغط التأكيد الثاني عند إتمام الحجز")
                render()
            } else {
                localStageOverrides.remove(id)
                toast(r.error.ifBlank { "تعذر تنفيذ التأكيد الأول، حاول مجدداً" })
                render()
            }
        }
    }

    // المرحلة الثانية: تظل ظاهرة لحين الانتهاء ثم تنتقل لخانة البحث
    private fun confirmStageTwo(id: String) {
        if (id.isBlank()) return
        localStageOverrides[id] = "PROCESSING_SECOND"
        render()

        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                StaffClient.operationAction(this@MainActivityPro, id, true)
            }
            if (r.ok) {
                localStageOverrides[id] = "CONFIRMED"
                toast("تم التأكيد الثاني بنجاح! انتقلت العملية إلى خانة البحث ✓")
                render()
                refresh(silent = true)
            } else {
                // حتى لو كان السيرفر قد سجلها من الضغطة الأولى، ننقلها للبحث عند التأكيد الثاني
                if (r.body.optString("error") in setOf("PAYMENT_ALREADY_APPROVED", "DECISION_ALREADY_RECORDED", "ALREADY_CONFIRMED")) {
                    localStageOverrides[id] = "CONFIRMED"
                    toast("تم إتمام العملية ونقلها إلى خانة البحث ✓")
                    render()
                    refresh(silent = true)
                } else {
                    localStageOverrides[id] = "WAITING_SECOND_CONFIRM"
                    toast(r.error.ifBlank { "تعذر إتمام التأكيد الثاني" })
                    render()
                }
            }
        }
    }

    private fun renderPayments() {
        if (payments.length() == 0) {
            box.addView(emptyCard("لا توجد إشعارات دفع حالياً."))
            return
        }
        for (i in 0 until payments.length()) {
            val p = payments.optJSONObject(i) ?: continue
            val c = card()
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            c.addView(b)
            b.addView(txt("دفعة #${p.optString("payment_id")} • ${p.optString("provider", "تحويل")}", 13.5f, TEXT, true))
            b.addView(
                txt(
                    "المبلغ: ${p.optDouble("amount", 0.0)} ج.م\n" +
                    "المرجع: ${p.optString("reference_number")}\n" +
                    "المرسل: ${p.optString("sender_phone")}\n" +
                    "الحالة: ${p.optString("status")}",
                    12f, MUTED, false
                ),
                match().apply { topMargin = dp(4) }
            )
            box.addView(c, match().apply { bottomMargin = dp(8) })
        }
    }

    private fun renderTrips() {
        if (trips.length() == 0) {
            box.addView(emptyCard("لا توجد رحلات مسجلة حالياً."))
            return
        }
        for (i in 0 until trips.length()) {
            val t = trips.optJSONObject(i) ?: continue
            val c = card()
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
            }
            c.addView(b)
            b.addView(txt("${t.optString("from_name")} → ${t.optString("to_name")}", 13.5f, TEXT, true))
            b.addView(
                txt(
                    "التاريخ: ${t.optString("travel_date")} • الساعة: ${t.optString("travel_time")}\n" +
                    "السعر: ${t.optDouble("price", 0.0)} ج.م",
                    12f, MUTED, false
                ),
                match().apply { topMargin = dp(4) }
            )
            box.addView(c, match().apply { bottomMargin = dp(8) })
        }
    }

    private fun renderLogs() {
        val c = card()
        val b = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        c.addView(b)
        b.addView(txt("ملخص النظام", 14f, TEXT, true))
        b.addView(
            txt(
                "إجمالي الحجوزات: ${bookings.length()}\n" +
                "إجمالي المدفوعات: ${payments.length()}\n" +
                "العمليات المكتملة/المرفوضة متاحة بالكامل في نافذة البحث مع الفلترة الزمنية.",
                12.5f, MUTED, false
            ),
            match().apply { topMargin = dp(6) }
        )
        box.addView(c, match())
    }

    private fun reject(id: String) {
        if (id.isBlank()) {
            toast("رقم العملية غير متاح.")
            return
        }
        val e = EditText(this).apply {
            hint = "اكتب سبب الرفض (مطلوب)"
            minLines = 2
            setTextColor(Color.parseColor(TEXT))
            setHintTextColor(Color.parseColor(MUTED))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor(SURFACE2))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("رفض عملية الدفع")
            .setMessage("سيُحفظ السبب في سجل العملية وتنتقل العملية مباشرة إلى خانة البحث.")
            .setView(e)
            .setPositiveButton("تأكيد الرفض", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val reason = e.text?.toString()?.trim().orEmpty()
                if (reason.isBlank()) {
                    e.error = "اكتب سبب الرفض."
                    return@setOnClickListener
                }
                positive.isEnabled = false
                positive.text = "جارٍ التسجيل…"
                lifecycleScope.launch {
                    val r = withContext(Dispatchers.IO) { StaffClient.reject(this@MainActivityPro, id, reason) }
                    val message = r.body.optString("message").ifBlank {
                        when (r.body.optString("error")) {
                            "PAYMENT_ALREADY_APPROVED" -> "تم اعتماد الدفع بالفعل؛ لا يمكن رفضه الآن."
                            "DECISION_ALREADY_RECORDED" -> "تم تسجيل قرار سابق لهذه العملية."
                            "CUSTOMER_SERVICE_REQUIRED" -> "لا يملك الحساب صلاحية رفض عمليات الدفع."
                            else -> r.error.ifBlank { "تعذر تسجيل قرار الرفض." }
                        }
                    }
                    if (r.ok) {
                        localStageOverrides[id] = "REJECTED"
                        toast("تم رفض العملية ونقلها إلى خانة البحث.")
                        dialog.dismiss()
                        render()
                        refresh(silent = true)
                    } else {
                        toast(message)
                        positive.isEnabled = true
                        positive.text = "تأكيد الرفض"
                    }
                }
            }
        }
        dialog.show()
    }

    // نافذة البحث الشاملة للعمليات المؤكدة والمرفوضة مع فلتر اليوم / الأسبوع / الشهر
    private fun showSearchDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.parseColor(BG))
        }

        val input = EditText(this).apply {
            hint = "ابحث بالاسم، الهاتف، رقم العملية أو الحجز..."
            setTextColor(Color.parseColor(TEXT))
            setHintTextColor(Color.parseColor(MUTED))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor(SURFACE2))
        }
        root.addView(input, match().apply { bottomMargin = dp(8) })

        var selectedPeriod = "ALL" // ALL, DAY, WEEK, MONTH
        val periodRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnAll = button("الكل", BLUE, true)
        val btnDay = button("اليوم", SURFACE2, true)
        val btnWeek = button("الأسبوع", SURFACE2, true)
        val btnMonth = button("الشهر", SURFACE2, true)

        periodRow.addView(btnAll, weight())
        periodRow.addView(btnDay, weight().apply { marginStart = dp(4) })
        periodRow.addView(btnWeek, weight().apply { marginStart = dp(4) })
        periodRow.addView(btnMonth, weight().apply { marginStart = dp(4) })
        root.addView(periodRow, match().apply { bottomMargin = dp(8) })

        var selectedDecision = "ALL" // ALL, CONFIRMED, REJECTED
        val decisionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnDecAll = button("الكل (مؤكد/مرفوض)", BLUE, true)
        val btnDecYes = button("المؤكدة ✓", GREEN, true)
        val btnDecNo = button("المرفوضة ×", RED, true)

        decisionRow.addView(btnDecAll, weight())
        decisionRow.addView(btnDecYes, weight().apply { marginStart = dp(4) })
        decisionRow.addView(btnDecNo, weight().apply { marginStart = dp(4) })
        root.addView(decisionRow, match().apply { bottomMargin = dp(10) })

        val resultsScroll = ScrollView(this)
        val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultsScroll.addView(resultsBox)
        root.addView(resultsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))

        var serverSearchResults = JSONArray()

        fun matchDateFilter(dateStr: String, period: String): Boolean {
            if (period == "ALL" || dateStr.isBlank()) return true
            return try {
                val clean = dateStr.trim().take(10)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val parsed = sdf.parse(clean) ?: return true
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    when (period) {
                        "DAY" -> {}
                        "WEEK" -> add(Calendar.DAY_OF_YEAR, -7)
                        "MONTH" -> add(Calendar.MONTH, -1)
                    }
                }
                parsed.time >= startCal.timeInMillis
            } catch (e: Exception) {
                true
            }
        }

        fun renderSearchItems() {
            resultsBox.removeAllViews()
            val q = input.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

            val allItems = mutableMapOf<String, JSONObject>()
            fun collect(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("operation_id")
                        .ifBlank { o.optString("booking_id") }
                        .ifBlank { o.optString("payment_id") }
                    if (id.isNotBlank()) allItems[id] = o
                }
            }

            collect(bookings)
            collect(payments)
            collect(serverSearchResults)

            var count = 0
            for ((id, item) in allItems) {
                val st = (localStageOverrides[id] ?: item.optString("status")).uppercase(Locale.ROOT)
                val isConfirmed = st in setOf("CONFIRMED", "BOOKING_CONFIRMED", "COMPLETED", "APPROVED", "PAYMENT_APPROVED", "TICKET_ISSUED")
                val isRejected = st in setOf("REJECTED", "PAYMENT_REJECTED", "CANCELLED", "DECLINED")

                // إظهار العمليات التي تم حجزها بالفعل (المؤكدة أو المرفوضة)
                if (!isConfirmed && !isRejected) continue
                if (selectedDecision == "CONFIRMED" && !isConfirmed) continue
                if (selectedDecision == "REJECTED" && !isRejected) continue

                val itemDate = item.optString("updated_at")
                    .ifBlank { item.optString("created_at") }
                    .ifBlank { item.optString("travel_date") }

                if (!matchDateFilter(itemDate, selectedPeriod)) continue

                val blob = (
                    id + " " +
                    item.optString("booking_id") + " " +
                    item.optString("customer_name") + " " +
                    item.optString("customer_phone") + " " +
                    item.optString("from_name") + " " +
                    item.optString("to_name") + " " +
                    item.optString("reference_number")
                ).lowercase(Locale.ROOT)

                if (q.isNotEmpty() && !blob.contains(q)) continue

                count++
                val c = card()
                val b = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                val badge = if (isConfirmed) "✓ تم التأكيد" else "× تم الرفض"
                val color = if (isConfirmed) GREEN else RED

                b.addView(txt("عملية #$id • حجز #${item.optString("booking_id")} ($badge)", 13f, color, true))
                b.addView(
                    txt(
                        "العميل: ${item.optString("customer_name")} • ${item.optString("customer_phone")}\n" +
                        "الرحلة: ${item.optString("from_name")} → ${item.optString("to_name")}\n" +
                        "التاريخ: $itemDate • المبلغ: ${String.format(Locale.US, "%.2f", item.optDouble("amount", 0.0))} ج.م",
                        12f, TEXT, false
                    ),
                    match().apply { topMargin = dp(4) }
                )
                c.addView(b)
                resultsBox.addView(c, match().apply { bottomMargin = dp(8) })
            }

            if (count == 0) {
                resultsBox.addView(emptyCard("لا توجد عمليات منتهية (مؤكدة أو مرفوضة) تطابق الفلتر الحالي."))
            }
        }

        btnAll.setOnClickListener { selectedPeriod = "ALL"; renderSearchItems() }
        btnDay.setOnClickListener { selectedPeriod = "DAY"; renderSearchItems() }
        btnWeek.setOnClickListener { selectedPeriod = "WEEK"; renderSearchItems() }
        btnMonth.setOnClickListener { selectedPeriod = "MONTH"; renderSearchItems() }

        btnDecAll.setOnClickListener { selectedDecision = "ALL"; renderSearchItems() }
        btnDecYes.setOnClickListener { selectedDecision = "CONFIRMED"; renderSearchItems() }
        btnDecNo.setOnClickListener { selectedDecision = "REJECTED"; renderSearchItems() }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderSearchItems()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderSearchItems()
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { StaffClient.search(this@MainActivityPro, "") }.getOrNull() }
            if (r != null && r.ok) {
                serverSearchResults = r.body.optJSONArray("results")
                    ?: r.body.optJSONArray("operations")
                    ?: JSONArray()
                renderSearchItems()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("سجل العمليات المحجوزة (المؤكدة والمرفوضة)")
            .setView(root)
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showChatDialog(opId: String, bookingId: String) {
        val input = EditText(this).apply {
            hint = "اكتب رسالة للعميل..."
            setTextColor(Color.parseColor(TEXT))
            setHintTextColor(Color.parseColor(MUTED))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor(SURFACE2))
        }
        AlertDialog.Builder(this)
            .setTitle("محادثة العملية #$opId")
            .setView(input)
            .setPositiveButton("إرسال") { _, _ ->
                val msg = input.text?.toString()?.trim().orEmpty()
                if (msg.isNotEmpty()) {
                    lifecycleScope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { StaffClient.sendMessage(this@MainActivityPro, opId, bookingId, msg) }.getOrNull()
                        }
                        toast(if (r?.ok == true) "تم إرسال الرسالة" else "تعذر إرسال الرسالة")
                    }
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showSettingsDialog() {
        toast("الإعدادات متاحة من الشاشة الرئيسية")
    }

    // دوال مساعدة لبناء الواجهة
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.parseColor(SURFACE))
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun emptyCard(message: String): View = card().apply {
        setPadding(dp(16), dp(20), dp(16), dp(20))
        addView(txt(message, 13f, MUTED, false).apply { gravity = Gravity.CENTER })
    }

    private fun txt(text: String, size: Float, color: String, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        this.textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(label: String, bgColor: String, small: Boolean): Button = Button(this).apply {
        text = label
        textSize = if (small) 11.5f else 13f
        setTextColor(Color.parseColor(TEXT))
        isAllCaps = false
        setPadding(dp(10), dp(if (small) 6 else 10), dp(10), dp(if (small) 6 else 10))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun match() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun wrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weight() = LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f
    )

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
