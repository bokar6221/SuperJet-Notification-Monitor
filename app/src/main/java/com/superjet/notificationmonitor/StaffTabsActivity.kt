package com.superjet.notificationmonitor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StaffTabsActivity : AppCompatActivity() {
    companion object {
        const val BG="#07111D"; const val SURFACE="#101D2B"; const val SURFACE2="#0D1824"
        const val NAVY="#07111D"; const val STROKE="#294055"; const val GOLD="#F2C14E"
        const val BLUE="#397DFF"; const val GREEN="#32CC86"; const val RED="#E96868"
        const val TEXT="#F5F7FA"; const val MUTED="#9AAABC"; const val ORANGE="#F59E0B"
    }

    private lateinit var root: LinearLayout
    private var content: LinearLayout?=null
    private var statusView:TextView?=null
    private var user:TextInputEditText?=null
    private var pass:TextInputEditText?=null
    private val tabs=mutableListOf<MaterialButton>()
    private var currentTab=0
    private var selectedImage:Uri?=null
    private var selectedImageLabel:TextView?=null
    private val imagePicker=registerForActivityResult(ActivityResultContracts.GetContent()){u->selectedImage=u;selectedImageLabel?.text=if(u!=null)"📷 صورة مرفقة" else ""}

    override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=Color.parseColor(NAVY);window.navigationBarColor=Color.parseColor(NAVY);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.parseColor(BG))};setContentView(root);if(SecureConfig.getToken(this).isBlank())showLogin()else{showMain();startBgService();requestNotif()}}

    override fun onResume(){super.onResume();if(SecureConfig.getToken(this).isNotBlank()&&content!=null)loadTab()}
    override fun onNewIntent(i:Intent?){super.onNewIntent(i);setIntent(i);if(SecureConfig.getToken(this).isNotBlank())loadTab()}

    private fun showLogin(){
        root.removeAllViews()
        val s=ScrollView(this).apply{isFillViewport=true}
        val p=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(20),dp(28),dp(20),dp(28))}
        p.addView(ImageView(this).apply{setImageResource(R.drawable.superjet_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE},lp(118,118))
        p.addView(txt("SUPERJET STAFF",12f,GOLD,true),match())
        p.addView(txt("مركز عمليات الموظفين",27f,TEXT,true),match())
        p.addView(txt("الإشعارات • المحادثات • المدفوعات",13f,MUTED,false),match())
        val c=card(SURFACE,22f);val f=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(20),dp(18),dp(20))};c.addView(f)
        f.addView(txt("تسجيل دخول الموظف",19f,TEXT,true),match().apply{bottomMargin=dp(14)})
        val uL=TextInputLayout(this).apply{hint="اسم المستخدم";boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE};user=TextInputEditText(this).apply{isSingleLine=true;inputType=InputType.TYPE_CLASS_TEXT;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED))};uL.addView(user);f.addView(uL,match().apply{bottomMargin=dp(12)})
        val pL=TextInputLayout(this).apply{hint="كلمة المرور";boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE;endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE};pass=TextInputEditText(this).apply{isSingleLine=true;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED))};pL.addView(pass);f.addView(pL,match().apply{bottomMargin=dp(16)})
        val b=button("دخول آمن",GOLD,true);f.addView(b,match());b.setOnClickListener{login(b)}
        p.addView(c,match());s.addView(p);root.addView(s,LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun login(b:MaterialButton){
        val u=user?.text?.toString()?.trim().orEmpty();val p=pass?.text?.toString().orEmpty()
        if(u.isBlank()||p.isBlank()){toast("أدخل اسم المستخدم وكلمة المرور.");return}
        b.isEnabled=false
        lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.login(this@StaffTabsActivity,u,p)};b.isEnabled=true;if(r.ok){showMain();startBgService();requestNotif()}else toast(r.error)}
    }

    private fun showMain(){
        root.removeAllViews()
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.parseColor(NAVY))}
        head.addView(ImageView(this).apply{setImageResource(R.drawable.superjet_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE},lp(44,44).apply{marginEnd=dp(8)})
        val h=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};h.addView(txt("SuperJet Staff",20f,TEXT,true),match());statusView=txt("متصل",10.5f,MUTED,false);h.addView(statusView,match());head.addView(h,LinearLayout.LayoutParams(0,-2,1f))
        val out=button("خروج",RED,false);head.addView(out,wrap());out.setOnClickListener{SecureConfig.clearToken(this);stopBgService();showLogin()};root.addView(head,match())
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(6),dp(8),dp(8));setBackgroundColor(Color.parseColor("#0B1622"))}
        listOf("🔔 الإشعارات","💬 المحادثات","💰 المدفوعات").forEachIndexed{i,t->val b=button(t,if(i==0)GOLD else SURFACE,i==0);b.textSize=11.5f;tabs.add(b);bar.addView(b,LinearLayout.LayoutParams(0,dp(52),1f).apply{if(i>0)marginStart=dp(6)});b.setOnClickListener{selectTab(i)}};root.addView(bar,match())
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(20))};root.addView(ScrollView(this).apply{isFillViewport=true;addView(content)},LinearLayout.LayoutParams(-1,0,1f));selectTab(currentTab)
    }

    private fun selectTab(i:Int){currentTab=i;tabs.forEachIndexed{n,b->val on=n==i;b.backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor(if(on)GOLD else SURFACE));b.setTextColor(Color.parseColor(if(on)NAVY else TEXT))};loadTab()}
    private fun loadTab(){when(currentTab){0->loadNotifications();1->loadChats();2->loadPayments()}}

    private fun loadNotifications(){
        clear();content?.addView(title("الإشعارات والمزامنة","إشعارات الدفع المقروءة من الهاتف وحالة المزامنة مع الخادم."))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val sync=button("مزامنة الآن",GOLD,true);val settings=button("صلاحية الإشعارات",BLUE,false);actions.addView(sync,weight());actions.addView(settings,weight().apply{marginStart=dp(7)});content?.addView(actions,match().apply{bottomMargin=dp(12)})
        sync.setOnClickListener{syncNotifications()};settings.setOnClickListener{startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){apiGet("/api/mobile/notifications")}
            val local=NotificationStore.all(this@StaffTabsActivity)
            content?.addView(summary("على الجهاز",local.size.toString(),GREEN),match().apply{bottomMargin=dp(8)})
            if(!r.ok){content?.addView(empty("تعذر قراءة قائمة المزامنة من الخادم: "+r.error));renderLocalNotifications(local);return@launch}
            val arr=r.body.optJSONArray("notifications")?:r.body.optJSONArray("items")?:JSONArray()
            if(arr.length()==0){renderLocalNotifications(local);return@launch}
            for(i in 0 until arr.length())content?.addView(notificationCard(arr.optJSONObject(i)?:continue),match().apply{bottomMargin=dp(7)})
        }
    }

    private fun renderLocalNotifications(a:List<NotificationItem>){
        for(n in a.take(20))content?.addView(card(SURFACE2,16f).apply{addView(txt(provider(n.provider)+"\n"+(n.amount?.let{fmt(it)+" جنيه"}?:"المبلغ غير مستخرج")+"\nالمرجع: "+n.reference.ifBlank{"—"}+"\n"+if(n.synced)"مزامنة ناجحة ✅" else if(n.syncError.isBlank())"بانتظار المزامنة" else "فشل: "+n.syncError,12f,TEXT,false).apply{setPadding(dp(12),dp(10),dp(12),dp(10))})},match().apply{bottomMargin=dp(7)})
    }

    private fun notificationCard(o:JSONObject)=card(SURFACE2,16f).apply{
        val s=o.optString("provider").ifBlank{o.optString("app_label").ifBlank{"إشعار دفع"}}
        val amount=o.optDouble("amount",Double.NaN);val ref=o.optString("reference")
        addView(txt(s+"\n"+if(amount.isNaN())"المبلغ غير مستخرج" else fmt(amount)+" جنيه"+"\nالمرجع: "+ref.ifBlank{"—"}+"\n"+o.optString("parse_status"),12f,TEXT,false).apply{setPadding(dp(12),dp(10),dp(12),dp(10))})
    }

    private fun syncNotifications(){
        if(busy)return;busy=true
        lifecycleScope.launch{
            val p=withContext(Dispatchers.IO){NotificationStore.pending(this@StaffTabsActivity)}
            var ok=0;var bad=0
            for(n in p){val r=withContext(Dispatchers.IO){NotificationApi.syncOne(this@StaffTabsActivity,n)};if(r.ok){NotificationStore.markSynced(this@StaffTabsActivity,n.eventId);ok++}else{NotificationStore.markSynced(this@StaffTabsActivity,n.eventId,r.error);bad++}}
            busy=false;toast("المزامنة: نجح $ok • فشل $bad");loadNotifications()
        }
    }

    private fun loadChats(){
        clear();content?.addView(title("محادثات العملاء","كل المحادثات الجارية والمنتهية. الرقم الأحمر هو عدد الرسائل غير المقروءة."))
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){apiGet("/api/mobile/chats")}
            if(!r.ok){content?.addView(empty(r.error));return@launch}
            val a=r.body.optJSONArray("chats")?:r.body.optJSONArray("items")?:JSONArray()
            if(a.length()==0){content?.addView(empty("لا توجد محادثات حاليًا."));return@launch}
            for(i in 0 until a.length()){
                val o=a.optJSONObject(i)?:continue;val c=card(SURFACE,17f);val row=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))}
                val box=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL};box.addView(txt(o.optString("customer_name").ifBlank{"عميل"},15f,TEXT,true),match());box.addView(txt(o.optString("last_message").ifBlank{o.optString("message").ifBlank{"لا توجد رسالة"}},11.5f,MUTED,false),match());row.addView(box,LinearLayout.LayoutParams(0,-2,1f))
                val unread=o.optInt("unread_count",o.optInt("unread",0));if(unread>0)row.addView(pill(unread.toString(),RED),wrap());c.addView(row);c.setOnClickListener{showChat(o.optString("operation_id"),o.optString("booking_id"))};content?.addView(c,match().apply{bottomMargin=dp(7)})
            }
        }
    }

    private fun loadPayments(){
        clear();content?.addView(title("المدفوعات","المعلقة والناجحة والمرفوضة. الإجمالي المالي يحسب الناجح فقط."))
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){apiGet("/api/mobile/payments")}
            if(!r.ok){content?.addView(empty(r.error));return@launch}
            val s=r.body.optJSONObject("summary")?:JSONObject()
            val totals=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL}
            totals.addView(summary("إجمالي الناجح فقط",fmt(s.optDouble("successful_total",0.0))+" جنيه",GREEN),match().apply{bottomMargin=dp(7)})
            totals.addView(summary("الرصيد الفعلي المستلم",fmt(s.optDouble("actual_received_balance",0.0))+" جنيه",GOLD),match().apply{bottomMargin=dp(10)})
            content?.addView(totals,match())
            val a=r.body.optJSONArray("payments")?:r.body.optJSONArray("operations")?:JSONArray()
            if(a.length()==0){content?.addView(empty("لا توجد عمليات دفع."));return@launch}
            for(i in 0 until a.length())content?.addView(paymentCard(a.optJSONObject(i)?:continue),match().apply{bottomMargin=dp(8)})
        }
    }

    private fun paymentCard(o:JSONObject)=card(SURFACE,17f).apply{
        val id=o.optString("operation_id");val st=o.optString("status").ifBlank{"PAYMENT_PENDING"};val terminal=st=="TICKET_READY"||st=="PAYMENT_REJECTED"
        val body=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11))}
        val row=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        row.addView(txt(o.optString("booking_id").ifBlank{"عملية دفع"},14f,TEXT,true),LinearLayout.LayoutParams(0,-2,1f));row.addView(pill(statusText(st),statusColor(st)),wrap());body.addView(row)
        body.addView(txt(o.optString("customer_name")+"\n"+o.optString("from_name")+" → "+o.optString("to_name")+"\nالمبلغ: "+fmt(o.optDouble("amount",0.0))+" جنيه\n"+o.optString("payment_match_status").ifBlank{"تفاصيل المطابقة محفوظة"},11.5f,MUTED,false),match().apply{topMargin=dp(7)})
        val buttons=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL}
        val chat=button("محادثة 💬",BLUE,false);buttons.addView(chat,weight());chat.setOnClickListener{showChat(id,o.optString("booking_id"))}
        if(!terminal){
            val yes=button("تأكيد ✓",GREEN,false);val no=button("رفض ×",RED,false);buttons.addView(yes,weight().apply{marginStart=dp(6)});buttons.addView(no,weight().apply{marginStart=dp(6)});yes.setOnClickListener{approve(id)};no.setOnClickListener{reject(id)}
        } else body.addView(txt("العملية نهائية — تم إخفاء التأكيد والرفض.",10.5f,MUTED,false),match().apply{topMargin=dp(7)})
        body.addView(buttons,match().apply{topMargin=dp(8)});addView(body)
    }

    private fun approve(id:String){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.approve(this@StaffTabsActivity,id)};toast(if(r.ok)"تم اعتماد العملية وإصدار التذكرة." else r.error);if(r.ok)loadPayments()}}
    private fun reject(id:String){val e=EditText(this).apply{hint="سبب الرفض";setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));minLines=2};AlertDialog.Builder(this).setTitle("رفض عملية الدفع").setView(e).setPositiveButton("رفض"){_,_->lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.reject(this@StaffTabsActivity,id,e.text.toString().ifBlank{"رفض موظف"})};toast(if(r.ok)"تم رفض العملية." else r.error);if(r.ok)loadPayments()}}.setNegativeButton("إلغاء",null).show()}

    private fun showChat(operationId:String,bookingId:String){
        if(operationId.isBlank()){toast("رقم العملية غير متاح.");return}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(5),dp(8),0)}
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(list);layoutParams=LinearLayout.LayoutParams(-1,dp(360))}
        val choose=button("إرفاق صورة 📷",BLUE,false);selectedImageLabel=txt("",10.5f,GOLD,true)
        val input=EditText(this).apply{hint="اكتب رسالة للعميل...";setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));setBackgroundColor(Color.parseColor(SURFACE2));setPadding(dp(12),dp(8),dp(12),dp(8))}
        val send=button("إرسال",GOLD,true)
        box.addView(scroll);val attach=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;addView(choose,wrap());addView(selectedImageLabel,LinearLayout.LayoutParams(0,-2,1f).apply{gravity=Gravity.CENTER_VERTICAL;marginStart=dp(8)})};box.addView(attach,match());val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;addView(input,LinearLayout.LayoutParams(0,dp(52),1f));addView(send,LinearLayout.LayoutParams(dp(95),dp(52)).apply{marginStart=dp(6)})};box.addView(row)
        val dlg=AlertDialog.Builder(this).setTitle("محادثة $bookingId").setView(box).setNegativeButton("إغلاق",null).create()
        choose.setOnClickListener{imagePicker.launch("image/*")}
        fun reload(){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.chat(this@StaffTabsActivity,operationId)};if(!r.ok)return@launch;list.removeAllViews();val a=r.body.optJSONArray("messages")?:JSONArray();for(i in 0 until a.length()){val m=a.optJSONObject(i)?:continue;val own=m.optString("sender_type")=="staff";val t=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;gravity=if(own)Gravity.END else Gravity.START;setPadding(dp(4),dp(3),dp(4),dp(3))};val bubble=card(if(own)"#1B3550" else "#162332",14f);val inner=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8))};inner.addView(txt(if(own)"أنت" else "العميل",10f,if(own)GOLD else GREEN,true),match());inner.addView(txt(m.optString("message"),12.5f,TEXT,false),match());if(m.optString("media_url").isNotBlank()){val im=ImageView(this@StaffTabsActivity).apply{adjustViewBounds=true;maxHeight=dp(220)};inner.addView(im,match().apply{topMargin=dp(6)});lifecycleScope.launch{val bm=withContext(Dispatchers.IO){loadImage(m.optString("media_url"))};if(bm!=null)im.setImageBitmap(bm)}};bubble.addView(inner);t.addView(bubble,wrap());list.addView(t)};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}
        send.setOnClickListener{val msg=input.text.toString().trim();val img=selectedImage;if(msg.isBlank()&&img==null){toast("اكتب رسالة أو اختر صورة.");return@setOnClickListener};send.isEnabled=false;lifecycleScope.launch{val r=withContext(Dispatchers.IO){if(img!=null)apiPostMedia("/api/mobile/operations/${operationId}/chat",msg,img)else StaffClient.sendChat(this@StaffTabsActivity,operationId,msg)};send.isEnabled=true;if(r.ok){input.setText("");selectedImage=null;selectedImageLabel?.text="";reload()}else toast(r.error)}}
        dlg.setOnShowListener{reload();lifecycleScope.launch{repeat(8){delay(2500);if(dlg.isShowing)reload()}}};dlg.show()
    }

    private fun apiGet(path:String):StaffResult{
        val base=SecureConfig.getServerUrl(this).trimEnd('/')
        if(base.isBlank()) return StaffResult(false,error="SERVER_URL_NOT_CONFIGURED")
        val cn=(URL(base+path).openConnection() as HttpURLConnection).apply{
            requestMethod="GET";connectTimeout=12000;readTimeout=25000
            setRequestProperty("Accept","application/json")
            setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(this@StaffTabsActivity))
            setRequestProperty("X-SuperJet-Device-Id",StaffClient.deviceId(this@StaffTabsActivity))
        }
        return try{parseLocal(cn)}catch(e:Exception){StaffResult(false,error=e.javaClass.simpleName+":"+(e.message?:"network error"))}finally{cn.disconnect()}
    }
    private fun apiPostMedia(path:String,msg:String,uri:Uri):StaffResult{
        val base=SecureConfig.getServerUrl(this).trimEnd('/')
        if(base.isBlank()) return StaffResult(false,error="SERVER_URL_NOT_CONFIGURED")
        val boundary="----SJ$"+"{UUID.randomUUID()}"
        val cn=(URL(base+path).openConnection() as HttpURLConnection).apply{
            requestMethod="POST";connectTimeout=12000;readTimeout=30000;doOutput=true
            setRequestProperty("Accept","application/json")
            setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(this@StaffTabsActivity))
            setRequestProperty("X-SuperJet-Device-Id",StaffClient.deviceId(this@StaffTabsActivity))
            setRequestProperty("Content-Type","multipart/form-data; boundary=$"+"{boundary}")
        }
        return try{
            DataOutputStream(cn.outputStream).use{out->
                if(msg.isNotBlank()){out.writeBytes("--$"+"{boundary}\r\nContent-Disposition: form-data; name=\"message\"\r\n\r\n");out.write(msg.toByteArray(Charsets.UTF_8));out.writeBytes("\r\n")}
                out.writeBytes("--$"+"{boundary}\r\nContent-Disposition: form-data; name=\"media\"; filename=\"chat_image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                contentResolver.openInputStream(uri)?.use{it.copyTo(out)}?:return StaffResult(false,error="MEDIA_READ_FAILED")
                out.writeBytes("\r\n--$"+"{boundary}--\r\n")
            }
            parseLocal(cn)
        }catch(e:Exception){StaffResult(false,error=e.javaClass.simpleName+":"+(e.message?:"upload error"))}finally{cn.disconnect()}
    }
    private fun parseLocal(cn:HttpURLConnection):StaffResult{
        val code=cn.responseCode
        val st=if(code in 200..299)cn.inputStream else cn.errorStream
        val raw=if(st!=null)BufferedReader(InputStreamReader(st,Charsets.UTF_8)).use{it.readText()} else ""
        val jo=runCatching{JSONObject(raw)}.getOrElse{JSONObject()}
        return if(code in 200..299)StaffResult(true,jo) else StaffResult(false,jo,"HTTP_$"+"{code}:"+jo.optString("error",raw))
    }

    private fun loadImage(path:String):Bitmap?=runCatching{
        val base=SecureConfig.getServerUrl(this).trimEnd('/')
        val u=if(path.startsWith("http"))path else base+path
        val cn=(URL(u).openConnection() as HttpURLConnection).apply{connectTimeout=12000;readTimeout=25000;setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(this));setRequestProperty("X-SuperJet-Device-Id",StaffClient.deviceId(this@StaffTabsActivity))}
        val bm=cn.inputStream.use{BitmapFactory.decodeStream(it)};cn.disconnect();bm
    }.getOrNull()

    private fun openBookingFromNotification(){ }
    private fun startBgService(){runCatching{ContextCompat.startForegroundService(this,Intent(this,ChatNotificationService::class.java))}}
    private fun stopBgService(){runCatching{stopService(Intent(this,ChatNotificationService::class.java))}}
    private fun requestNotif(){if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),7001)}
    private fun clear(){content?.removeAllViews()}
    private fun summary(t:String,v:String,c:String)=card(SURFACE,16f).apply{val r=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(12),dp(10),dp(12),dp(10))};r.addView(txt(t,11f,MUTED,true),LinearLayout.LayoutParams(0,-2,1f));r.addView(txt(v,18f,c,true),wrap());addView(r)}
    private fun title(t:String,s:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(txt(t,20f,TEXT,true),match());addView(txt(s,11f,MUTED,false),match().apply{topMargin=dp(4);bottomMargin=dp(10)})}
    private fun statusText(s:String)=when(s){"TICKET_READY"->"ناجحة";"PAYMENT_REJECTED"->"مرفوضة";"PAYMENT_APPROVED"->"معتمدة";"PAYMENT_REVIEW"->"تحت المراجعة";else->"معلقة"}
    private fun statusColor(s:String)=when(s){"TICKET_READY","PAYMENT_APPROVED"->GREEN;"PAYMENT_REJECTED"->RED;else->ORANGE}
    private fun provider(p:String)=when(p){"VODAFONE_CASH"->"VF-Cash";"ORANGE_CASH"->"Orange Cash";"ETISALAT_CASH"->"Etisalat Cash";"WE_PAY"->"WE Pay";"INSTAPAY"->"InstaPay";else->p.ifBlank{"إشعار دفع"}}
    private fun fmt(v:Double)=String.format(Locale.US,"%.2f",v)
    private fun txt(s:String,size:Float,color:String,bold:Boolean)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.parseColor(color));typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;setLineSpacing(0f,1.14f)}
    private fun pill(s:String,c:String)=txt(s,10f,c,true).apply{setPadding(dp(8),dp(5),dp(8),dp(5));background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.parseColor("#2A1720"));cornerRadius=dp(10).toFloat()}}
    private fun card(bg:String,r:Float)=MaterialCardView(this).apply{radius=dp(r.toInt()).toFloat();setCardBackgroundColor(Color.parseColor(bg));strokeWidth=dp(1);strokeColor=Color.parseColor(STROKE)}
    private fun button(s:String,bg:String,dark:Boolean)=MaterialButton(this).apply{text=s;isAllCaps=false;textSize=12f;minHeight=dp(48);cornerRadius=dp(14);insetTop=0;insetBottom=0;backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor(bg));setTextColor(Color.parseColor(if(dark)NAVY else TEXT))}
    private fun empty(s:String)=card(SURFACE,16f).apply{addView(txt(s,12f,MUTED,false).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;setPadding(dp(12),dp(18),dp(12),dp(18))})}
    private fun match()=LinearLayout.LayoutParams(-1,-2);private fun wrap()=LinearLayout.LayoutParams(-2,-2);private fun weight()=LinearLayout.LayoutParams(0,dp(48),1f);private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(dp(w),dp(h));private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt();private var busy=false;private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
