package com.superjet.notificationmonitor

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class NotificationApiResult(val ok:Boolean,val error:String="")
object NotificationApi{
    private const val ENDPOINT="/api/mobile/android-notification"
    private val fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US)
    fun syncOne(c:Context,item:NotificationItem):NotificationApiResult{
        val base=SecureConfig.getServerUrl(c).trimEnd('/');val token=SecureConfig.getToken(c)
        if(base.isBlank())return NotificationApiResult(false,"SERVER_URL_NOT_CONFIGURED")
        if(token.isBlank())return NotificationApiResult(false,"LOGIN_REQUIRED")
        val cn=(URL(base+ENDPOINT).openConnection() as HttpURLConnection).apply{
            requestMethod="POST";connectTimeout=10000;readTimeout=15000;doOutput=true
            setRequestProperty("Content-Type","application/json; charset=UTF-8")
            setRequestProperty("Accept","application/json")
            setRequestProperty("Authorization","Bearer $token")
            setRequestProperty("X-SuperJet-Device-Id",deviceId(c))
            setRequestProperty("X-Idempotency-Key",item.eventId)
        }
        val received=Instant.ofEpochMilli(item.time).atZone(ZoneId.systemDefault()).format(fmt)
        val j=JSONObject().apply{
            put("event_id",item.eventId);put("package",item.packageName);put("package_name",item.packageName)
            put("app_label",item.app);put("app",item.app);put("title",item.title);put("text",item.text);put("raw_notification",item.text)
            if(item.amount!=null)put("amount",item.amount);put("reference",item.reference);put("sender_phone",item.senderPhone)
            put("recipient_phone",item.recipientAccount);put("transaction_date",item.transactionDate);put("transaction_time",item.transactionTime)
            put("transaction_type",item.transactionType);put("provider",item.provider);put("parse_status",item.parseStatus);put("received_at",received)
        }
        return try{
            cn.outputStream.use{it.write(j.toString().toByteArray(Charsets.UTF_8))}
            val code=cn.responseCode;val st=if(code in 200..299)cn.inputStream else cn.errorStream
            val raw=if(st!=null)BufferedReader(InputStreamReader(st,Charsets.UTF_8)).use{it.readText()} else ""
            if(code in 200..299)NotificationApiResult(true) else NotificationApiResult(false,"HTTP_$code:$raw")
        }catch(e:Exception){NotificationApiResult(false,e.javaClass.simpleName+":"+(e.message?:"network error"))}finally{cn.disconnect()}
    }
    private fun deviceId(c:Context)=Settings.Secure.getString(c.contentResolver,Settings.Secure.ANDROID_ID)?.takeIf{it.isNotBlank()}?:UUID.nameUUIDFromBytes((c.packageName+android.os.Build.MODEL).toByteArray()).toString()
}