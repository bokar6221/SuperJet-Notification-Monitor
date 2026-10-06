package com.superjet.notificationmonitor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class StaffChatActivity : AppCompatActivity() {
    companion object {
        private const val REFRESH_MS = 3500L
        private const val RETRY_MS = 6500L
        private val IMAGE_CACHE = object : android.util.LruCache<String, Bitmap>(20 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return maxOf(1, value.byteCount / 1024)
            }
        }
    }

    private lateinit var messagesView: RecyclerView
    private lateinit var input: EditText
    private lateinit var send: MaterialButton
    private lateinit var attach: MaterialButton
    private lateinit var adapter: MessageAdapter
    private var operationId = ""
    private var bookingId = ""
    private var selectedImage: Uri? = null
    private var refreshJob: Job? = null
    private var sending = false
    private var lastBottomId = 0L

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImage = uri
        attach.text = if (uri != null) "✓ صورة" else "＋"
        attach.contentDescription = if (uri != null) "الصورة مرفقة" else "إرفاق صورة"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        operationId = intent.getStringExtra("operation_id").orEmpty()
        bookingId = intent.getStringExtra("booking_id").orEmpty()
        if (operationId.isBlank()) {
            finish()
            return
        }

        window.statusBarColor = Color.parseColor(StaffTabsActivity.NAVY)
        window.navigationBarColor = Color.parseColor(StaffTabsActivity.NAVY)
        buildUi()

        lifecycleScope.launch {
            reload()
            var waitMs = REFRESH_MS
            while (isActive) {
                delay(waitMs)
                val ok = reload(false)
                waitMs = if (ok) REFRESH_MS else RETRY_MS
            }
        }
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(StaffTabsActivity.BG))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(Color.parseColor(StaffTabsActivity.NAVY))
        }

        val back = button("‹", StaffTabsActivity.SURFACE, false).apply {
            textSize = 28f
            minWidth = dp(52)
            contentDescription = "رجوع"
        }
        top.addView(back, lp(52, 52))
        back.setOnClickListener { finish() }

        val avatar = TextView(this).apply {
            text = "👤"
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = roundBg("#173250", 40f)
        }
        top.addView(avatar, lp(48, 48).apply { marginStart = dp(9) })

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        titleBox.addView(txt("محادثة العميل", 17f, StaffTabsActivity.TEXT, true), match())
        titleBox.addView(
            txt(if (bookingId.isBlank()) "متابعة العملية" else bookingId, 11f, StaffTabsActivity.MUTED, false),
            match().apply { topMargin = dp(2) }
        )
        top.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, match())

        messagesView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StaffChatActivity).apply {
                stackFromEnd = true
            }
            setPadding(dp(10), dp(8), dp(10), dp(10))
            clipToPadding = false
            itemAnimator = null
        }
        adapter = MessageAdapter()
        messagesView.adapter = adapter
        root.addView(messagesView, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(8), dp(8), dp(10))
            setBackgroundColor(Color.parseColor(StaffTabsActivity.NAVY))
        }

        attach = button("＋", StaffTabsActivity.SURFACE, false).apply {
            textSize = 21f
            minWidth = dp(54)
            contentDescription = "إرفاق صورة"
        }
        composer.addView(attach, lp(54, 52))
        attach.setOnClickListener { picker.launch("image/*") }

        val inputBox = FrameLayout(this).apply {
            background = roundBg(StaffTabsActivity.SURFACE2, 24f)
            setPadding(dp(13), 0, dp(13), 0)
        }
        input = EditText(this).apply {
            hint = "اكتب رسالة..."
            textSize = 15f
            setTextColor(Color.parseColor(StaffTabsActivity.TEXT))
            setHintTextColor(Color.parseColor(StaffTabsActivity.MUTED))
            background = null
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(false)
            maxLines = 5
            isVerticalScrollBarEnabled = true
            setPadding(0, 0, 0, 0)
        }
        inputBox.addView(input, FrameLayout.LayoutParams(-1, dp(52)))
        composer.addView(
            inputBox,
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(7) }
        )

        send = button("➤", StaffTabsActivity.GOLD, true).apply {
            textSize = 20f
            minWidth = dp(56)
            contentDescription = "إرسال"
        }
        composer.addView(send, lp(56, 52).apply { marginStart = dp(7) })
        root.addView(composer, match())

        send.setOnClickListener { sendMessage() }
        setContentView(root)
    }

    private suspend fun reload(showProgress: Boolean = true): Boolean {
        if (isFinishing || isDestroyed) return false
        val result = withContext(Dispatchers.IO) {
            StaffClient.chat(this@StaffChatActivity, operationId)
        }
        if (!result.ok) return false

        val messages = result.body.optJSONArray("messages") ?: JSONArray()
        val latestId = if (messages.length() > 0) {
            messages.optJSONObject(messages.length() - 1)?.optLong("id", 0L) ?: 0L
        } else 0L
        val wasAtBottom = !messagesView.canScrollVertically(1) || adapter.itemCount == 0
        adapter.setItems(messages)

        if (latestId > lastBottomId && (wasAtBottom || adapter.itemCount <= 1)) {
            lastBottomId = latestId
            messagesView.post { messagesView.scrollToPosition(maxOf(0, adapter.itemCount - 1)) }
        } else if (latestId > lastBottomId && messagesView.canScrollVertically(1).not()) {
            lastBottomId = latestId
        }
        return true
    }

    private fun sendMessage() {
        if (sending) return
        val message = input.text.toString().trim()
        val image = selectedImage
        if (message.isBlank() && image == null) {
            toast("اكتب رسالة أو اختر صورة.")
            return
        }

        sending = true
        send.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (image != null) {
                    StaffClient.sendChatMedia(this@StaffChatActivity, operationId, message, image)
                } else {
                    StaffClient.sendChat(this@StaffChatActivity, operationId, message)
                }
            }
            sending = false
            send.isEnabled = true

            if (result.ok) {
                input.setText("")
                selectedImage = null
                attach.text = "＋"
                attach.contentDescription = "إرفاق صورة"
                reload()
            } else {
                toast(result.error)
            }
        }
    }

    private inner class MessageAdapter : RecyclerView.Adapter<MessageVH>() {
        private val items = ArrayList<JSONObject>()

        init { setHasStableIds(true) }

        override fun getItemId(position: Int): Long {
            return items.getOrNull(position)?.optLong("id", position.toLong()) ?: position.toLong()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageVH {
            return MessageVH(
                LinearLayout(this@StaffChatActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(2), dp(3), dp(2), dp(3))
                }
            )
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: MessageVH, position: Int) {
            holder.bind(items[position])
        }

        fun setItems(array: JSONArray) {
            val incoming = ArrayList<JSONObject>()
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.let(incoming::add)
            }
            var same = items.size == incoming.size
            if (same) {
                for (i in items.indices) {
                    if (items[i].optLong("id", -1L) != incoming[i].optLong("id", -1L)) {
                        same = false
                        break
                    }
                    if (items[i].optString("message") != incoming[i].optString("message") ||
                        items[i].optString("media_url") != incoming[i].optString("media_url")) {
                        same = false
                        break
                    }
                }
            }
            if (same) return

            items.clear()
            items.addAll(incoming)
            notifyDataSetChanged()
        }
    }

    private inner class MessageVH(
        private val row: LinearLayout
    ) : RecyclerView.ViewHolder(row) {

        fun bind(message: JSONObject) {
            val own = message.optString("sender_type") == "staff"
            row.gravity = if (own) Gravity.END else Gravity.START
            row.removeAllViews()

            val bubble = MaterialCardView(this@StaffChatActivity).apply {
                radius = dp(18).toFloat()
                strokeWidth = dp(1)
                strokeColor = Color.parseColor(StaffTabsActivity.STROKE)
                setCardBackgroundColor(Color.parseColor(if (own) "#1C3854" else "#142230"))
            }

            val inner = LinearLayout(this@StaffChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }

            inner.addView(
                txt(if (own) "أنت" else "العميل", 10f,
                    if (own) StaffTabsActivity.GOLD else StaffTabsActivity.GREEN, true),
                match()
            )

            val body = message.optString("message")
            if (body.isNotBlank()) {
                inner.addView(
                    txt(body, 14f, StaffTabsActivity.TEXT, false),
                    match().apply { topMargin = dp(4) }
                )
            }

            val mediaUrl = message.optString("media_url")
            if (mediaUrl.isNotBlank()) {
                val imageBox = FrameLayout(this@StaffChatActivity)
                imageBox.background = roundBg("#0B1622", 14f)
                val image = ImageView(this@StaffChatActivity).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    minimumHeight = dp(120)
                    setImageResource(android.R.drawable.ic_menu_gallery)
                    tag = mediaUrl
                }
                imageBox.addView(image, FrameLayout.LayoutParams(dp(260), dp(195)))
                inner.addView(imageBox, LinearLayout.LayoutParams(dp(260), dp(195)).apply { topMargin = dp(7) })

                loadImageInto(image, mediaUrl)
                image.setOnClickListener { showImageDialog(mediaUrl) }
            }

            bubble.addView(inner)
            row.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        }
    }

    private fun loadImageInto(view: ImageView, path: String) {
        val base = SecureConfig.getServerUrl(this).trimEnd('/')
        val url = if (path.startsWith("http")) path else base + path
        val cached = synchronized(IMAGE_CACHE) { IMAGE_CACHE.get(url) }
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadImage(url) }
            if (bitmap != null) {
                synchronized(IMAGE_CACHE) { IMAGE_CACHE.put(url, bitmap) }
                if (view.tag == path || view.tag == url) view.setImageBitmap(bitmap)
            }
        }
    }

    private fun loadImage(url: String): Bitmap? = runCatching {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 25000
                useCaches = true
                setRequestProperty(
                    "Authorization",
                    "Bearer " + SecureConfig.getToken(this@StaffChatActivity)
                )
                setRequestProperty(
                    "X-SuperJet-Device-Id",
                    StaffClient.deviceId(this@StaffChatActivity)
                )
            }
        try {
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun showImageDialog(path: String) {
        val base = SecureConfig.getServerUrl(this).trimEnd('/')
        val url = if (path.startsWith("http")) path else base + path
        val cached = synchronized(IMAGE_CACHE) { IMAGE_CACHE.get(url) }
        if (cached == null) {
            toast("الصورة لم تكتمل بعد، حاول ثانية.")
            return
        }
        val image = ImageView(this).apply {
            setImageBitmap(cached)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setTitle("صورة المحادثة")
            .setView(image)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun button(text: String, bg: String, dark: Boolean) =
        MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 12.5f
            minHeight = dp(48)
            cornerRadius = dp(16)
            insetTop = 0
            insetBottom = 0
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(bg))
            setTextColor(
                Color.parseColor(if (dark) StaffTabsActivity.NAVY else StaffTabsActivity.TEXT)
            )
        }

    private fun txt(text: String, size: Float, color: String, bold: Boolean) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.parseColor(color))
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setLineSpacing(0f, 1.14f)
        }

    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun lp(width: Int, height: Int) = LinearLayout.LayoutParams(dp(width), dp(height))
    private fun roundBg(color: String, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius.toInt()).toFloat()
        }

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
