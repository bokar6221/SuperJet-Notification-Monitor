package com.superjet.notificationmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

class ChatNotificationService : Service() {
    companion object {
        private const val ALERT_CHANNEL = "superjet_staff_alerts_v3"
        private const val SERVICE_CHANNEL = "superjet_staff_service"
        private const val SERVICE_ID = 3011
        private const val PREFS = "superjet_staff_alert_state"
        private const val LAST_EVENT_ID = "last_event_id"
        private const val LAST_CHAT_ID = "last_chat_id"
        private const val SEEDED = "seeded"

        fun resetState(context: Context){
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply()
        }
        fun seedState(context: Context,eventId:Long,chatId:Long){
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putLong(LAST_EVENT_ID,eventId).putLong(LAST_CHAT_ID,chatId).putBoolean(SEEDED,true).apply()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val n = serviceNotification()
        if (Build.VERSION.SDK_INT >= 34) ServiceCompat.startForeground(this,SERVICE_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(SERVICE_ID,n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SecureConfig.getToken(this).isBlank()) { stopSelf(); return START_NOT_STICKY }
        if (!pollingStarted) { pollingStarted=true; scope.launch { pollLoop() } }
        return START_NOT_STICKY
    }

    private suspend fun pollLoop() {
        val prefs=getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        var sinceEvent=prefs.getLong(LAST_EVENT_ID,0L)
        var sinceChat=prefs.getLong(LAST_CHAT_ID,0L)
        if(!prefs.getBoolean(SEEDED,false)){
            val init=runCatching{StaffClient.initializeStaffAlerts(this@ChatNotificationService)}.getOrNull()
            if(init?.ok==true){
                sinceEvent=init.body.optLong("last_event_id",0L);sinceChat=init.body.optLong("last_chat_id",0L)
                seedState(this@ChatNotificationService,sinceEvent,sinceChat)
            }
        }
        while(scope.isActive){
            try{
                val r=StaffClient.pollStaffAlerts(this@ChatNotificationService,sinceEvent,sinceChat)
                if(r.ok){
                    val events=r.body.optJSONArray("events")?:JSONArray()
                    for(i in 0 until events.length()){
                        val e=events.optJSONObject(i)?:continue
                        val id=e.optLong("id",0L)
                        if(id>sinceEvent){
                            showEventNotification(e)
                            sinceEvent=id
                        }
                    }
                    val messages=r.body.optJSONArray("messages")?:JSONArray()
                    for(i in 0 until messages.length()){
                        val m=messages.optJSONObject(i)?:continue
                        val id=m.optLong("id",0L)
                        if(id>sinceChat){
                            showChatNotification(m)
                            sinceChat=id
                        }
                    }
                    sinceEvent=maxOf(sinceEvent,r.body.optLong("last_event_id",sinceEvent))
                    sinceChat=maxOf(sinceChat,r.body.optLong("last_chat_id",sinceChat))
                    prefs.edit().putLong(LAST_EVENT_ID,sinceEvent).putLong(LAST_CHAT_ID,sinceChat).putBoolean(SEEDED,true).apply()
                }else if(r.error.contains("HTTP_401")||r.error.contains("STAFF_AUTH_REQUIRED")){ stopSelf();break }
            }catch(_:Throwable){}
            delay(300)
        }
    }

    private fun showEventNotification(e:org.json.JSONObject){
        val type=e.optString("event_type")
        val booking=e.optString("booking_id")
        val op=e.optString("operation_id")
        val customer=e.optString("customer_name").ifBlank{"عميل"}
        val amount=e.optDouble("amount",Double.NaN)
        val title=when(type){
            "BOOKING_ASSIGNED","BOOKING_REASSIGNED"->"SuperJet • حجز جديد يحتاج تأكيد"
            "ANDROID_PAYMENT_RECEIVED"->"SuperJet • إشعار دفع يحتاج مراجعة"
            "PROOF_RECEIVED"->"SuperJet • إثبات دفع جديد"
            else->"SuperJet • تنبيه عملية جديدة"
        }
        val body=buildString{
            if(booking.isNotBlank())append(booking).append(" • ")
            append(customer)
            if(!amount.isNaN())append(" • ").append(String.format(java.util.Locale.US,"%.2f جنيه",amount))
        }
        notifyAlert(title,body,booking,op,false)
    }

    private fun showChatNotification(m:org.json.JSONObject){
        val booking=m.optString("booking_id");val op=m.optString("operation_id")
        val customer="عميل"
        val message=m.optString("message")
        val body=buildString{if(booking.isNotBlank())append(booking).append(" • ");append(customer);if(message.isNotBlank())append(": ").append(message)}
        notifyAlert("SuperJet • رسالة عميل جديدة",body,booking,op,true)
    }

    private fun notifyAlert(title:String,text:String,booking:String,operationId:String,chat:Boolean){
        val manager=getSystemService(NotificationManager::class.java)
        val intent=Intent(this,StaffTabsActivity::class.java).apply{
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_booking_id",booking);putExtra("open_operation_id",operationId);putExtra("open_chat",chat)
        }
        val requestCode=((operationId.ifBlank{booking}.hashCode() and 0x7fffffff) % 900000)+10000
        val pending=PendingIntent.getActivity(this,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(this,ALERT_CHANNEL)
            .setSmallIcon(R.drawable.superjet_logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(Notification.DEFAULT_ALL).setVibrate(longArrayOf(0,220,120,360))
            .build()
        manager.notify(10000+requestCode,n)
    }

    private fun serviceNotification():Notification=NotificationCompat.Builder(this,SERVICE_CHANNEL)
        .setSmallIcon(R.drawable.superjet_logo).setContentTitle("SuperJet Staff")
        .setContentText("الموظف متصل • مراقبة الحجوزات والدفع والمحادثات")
        .setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()

    private fun createChannels(){
        if(Build.VERSION.SDK_INT<26)return
        val manager=getSystemService(NotificationManager::class.java)
        val audio=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL,"SuperJet — حجوزات ودفع ومحادثات",NotificationManager.IMPORTANCE_HIGH).apply{
            description="تنبيه صوتي فوري للموظف عند حجز جديد أو إثبات/تحويل أو رسالة عميل"
            enableVibration(true);vibrationPattern=longArrayOf(0,220,120,360);setSound(audio,attrs);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        manager.createNotificationChannel(NotificationChannel(SERVICE_CHANNEL,"SuperJet — خدمة المراقبة",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})
    }

    override fun onDestroy(){pollingStarted=false;scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}