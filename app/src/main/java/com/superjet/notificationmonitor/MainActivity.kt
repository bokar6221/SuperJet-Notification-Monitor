package com.superjet.notificationmonitor

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: NotificationAdapter

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

        val status = TextView(this).apply {
            text = "فعّل صلاحية Notification Access ثم ارجع للتطبيق."
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
        if (::adapter.isInitialized) adapter.refresh()
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

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 15f
            setPadding(12, 18, 12, 18)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val x = items[position]
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(x.time))
        holder.view.text = "${x.app}\n${x.title}\n${x.text}\n$time\n${x.packageName}"
    }

    override fun getItemCount() = items.size
}
