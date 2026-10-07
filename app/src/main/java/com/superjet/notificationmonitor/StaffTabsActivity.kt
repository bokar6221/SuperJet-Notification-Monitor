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

    override fun onCreate(b:Bundle?){super.onCreate(b);NotificationStore.pruneNonPayment(this);window.statusBarColor=Color.parseColor(NAVY);window.navigationBarColor=Color.parseColor(NAVY);root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.parseColor(BG))};setContentView(root);if(SecureConfig.getToken(this).isBlank())showLogin()else{showMain();startBgService();requestNotif();handleIncomingIntent(intent)}}

    private var lastTabLoadAt = 0L
    override fun onResume(){
        super.onResume()
        if(SecureConfig.getToken(this).isNotBlank() && content!=null){
            loadTab()
            lifecycleScope.launch{withContext(Dispatchers.IO){StaffClient.heartbeat(this@StaffTabsActivity)}}
        }
    }
    override fun onNewIntent(i:Intent?){super.onNewIntent(i);setIntent(i);if(SecureConfig.getToken(this).isNotBlank()){loadTab();handleIncomingIntent(i)}}

    private fun showLogin(){
        root.removeAllViews();tabs.clear()
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
        root.removeAllViews();tabs.clear()
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.parseColor(NAVY))}
        head.addView(ImageView(this).apply{setImageResource(R.drawable.superjet_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE},lp(44,44).apply{marginEnd=dp(8)})
        val h=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};h.addView(txt("SuperJet Staff",20f,TEXT,true),match());statusView=txt("متصل • خدمة التنبيهات مفعلة",10.5f,GREEN,false);h.addView(statusView,match());head.addView(h,LinearLayout.LayoutParams(0,-2,1f))
        val out=button("خروج",RED,false);head.addView(out,wrap());out.setOnClickListener{SecureConfig.clearToken(this);stopBgService();showLogin()};root.addView(head,match())
        val tabScroll=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;setBackgroundColor(Color.parseColor("#0B1622"))}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(7),dp(8),dp(8))}
        listOf("🔔 الإشعارات","💬 المحادثات","📋 عمليات الحجز","💰 المدفوعات").forEachIndexed{i,t->val b=button(t,if(i==0)GOLD else SURFACE,i==0);b.textSize=12f;minTabWidth(b);tabs.add(b);bar.addView(b,LinearLayout.LayoutParams(dp(118),dp(54)).apply{if(i>0)marginStart=dp(7)});b.setOnClickListener{selectTab(i)}};tabScroll.addView(bar);root.addView(tabScroll,match())
        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(20))};root.addView(ScrollView(this).apply{isFillViewport=true;addView(content)},LinearLayout.LayoutParams(-1,0,1f));selectTab(currentTab)
    }

    private fun selectTab(i:Int){
        currentTab=i
        tabs.forEachIndexed{n,b->
            val on=n==i
            b.backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor(if(on)GOLD else SURFACE))
            b.setTextColor(Color.parseColor(if(on)NAVY else TEXT))
        }
        lastTabLoadAt = 0L
        loadTab(force=true)
    }
    private fun loadTab(force:Boolean=false){
        val now=System.currentTimeMillis()
        if(!force && now-lastTabLoadAt<1200L) return
        lastTabLoadAt=now
        when(currentTab){0->loadNotifications();1->loadChats();2->loadBookingOperations();3->loadPayments()}
    }

    private fun loadNotifications(){
        clear();content?.addView(title("الإشعارات والمزامنة","إشعارات الدفع المقروءة من الهاتف وحالة المزامنة مع الخادم."))
        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val sync=button("مزامنة الآن",GOLD,true);val settings=button("صلاحية الإشعارات",BLUE,false);val clearBtn=button("مسح سجل الهاتف",RED,false)
        actions.addView(sync,weight());actions.addView(settings,weight().apply{marginStart=dp(6)});actions.addView(clearBtn,weight().apply{marginStart=dp(6)});content?.addView(actions,match().apply{bottomMargin=dp(12)})
        sync.setOnClickListener{syncNotifications()};settings.setOnClickListener{startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))};clearBtn.setOnClickListener{clearNotificationHistory()}
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

    private fun clearNotificationHistory(){
        AlertDialog.Builder(this).setTitle("مسح سجل الإشعارات")
            .setMessage("سيتم مسح السجل المحفوظ على الهاتف فقط. السجل الموجود على الخادم لا يتم حذفه.")
            .setPositiveButton("مسح"){_,_->NotificationStore.clear(this);toast("تم مسح سجل إشعارات الهاتف.");loadNotifications()}
            .setNegativeButton("إلغاء",null).show()
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
                val unread=o.optInt("unread_count",o.optInt("unread",0));if(unread>0)row.addView(pill(unread.toString(),RED),wrap());c.addView(row);c.setOnClickListener{openChat(o.optString("operation_id"),o.optString("booking_id"))};content?.addView(c,match().apply{bottomMargin=dp(7)})
            }
        }
    }

    private fun loadBookingOperations(){
        clear();content?.addView(title("عمليات الحجز","الحالية التي تحتاج قرارًا والمنتهية بالنجاح أو الرفض."))
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){apiGet("/api/mobile/booking-operations")}
            if(!r.ok){content?.addView(empty("تعذر تحميل عمليات الحجز: "+r.error));return@launch}
            val current=r.body.optJSONArray("current")?:JSONArray()
            val completed=r.body.optJSONArray("completed")?:JSONArray()
            content?.addView(txt("الحالية — تحتاج تأكيد أو رفض (${current.length()})",16f,TEXT,true),match().apply{bottomMargin=dp(7)})
            if(current.length()==0) content?.addView(empty("لا توجد عمليات حجز تحتاج قرارًا حاليًا."),match().apply{bottomMargin=dp(10)})
            else for(i in 0 until current.length()) content?.addView(bookingOperationCard(current.optJSONObject(i)?:continue,false),match().apply{bottomMargin=dp(8)})
            content?.addView(txt("المنتهية (${completed.length()})",16f,TEXT,true),match().apply{topMargin=dp(8);bottomMargin=dp(7)})
            if(completed.length()==0) content?.addView(empty("لا توجد عمليات منتهية."),match())
            else for(i in 0 until completed.length()) content?.addView(bookingOperationCard(completed.optJSONObject(i)?:continue,true),match().apply{bottomMargin=dp(8)})
        }
    }

    private fun compactPaymentResult(o:JSONObject):Pair<String,String>{
        val m=o.optJSONObject("match")?:o.optJSONObject("payment_match")?:JSONObject()
        val st=m.optString("status").ifBlank{o.optString("payment_match_status").ifBlank{"PENDING"}}.uppercase(Locale.ROOT)
        return when(st){
            "MATCHED"->Pair("الدفع مطابق ✅",GREEN)
            "CONFLICT","SUSPICIOUS","UNMATCHED"->Pair("الدفع غير مطابق ❌",RED)
            "PARTIAL_MATCH"->Pair("الدفع يحتاج مراجعة ⚠️",ORANGE)
            else->Pair("الدفع قيد التحقق ⏳",ORANGE)
        }
    }

    private fun bookingOperationCard(o:JSONObject,terminal:Boolean)=card(SURFACE,17f).apply{
        val id=o.optString("operation_id");val st=o.optString("status").ifBlank{"PAYMENT_PENDING"};val proof=o.optBoolean("proof_available",false);val payment=compactPaymentResult(o)
        val b=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11))}
        val top=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        top.addView(txt(o.optString("booking_id").ifBlank{"حجز"},14f,TEXT,true),LinearLayout.LayoutParams(0,-2,1f))
        top.addView(pill(statusText(st),statusColor(st)),wrap());b.addView(top)
        b.addView(txt(payment.first,12.5f,payment.second,true),match().apply{topMargin=dp(6)})
        val guestCode=o.optString("guest_booking_code").ifBlank{"300"}
        b.addView(txt(o.optString("customer_name").ifBlank{"عميل"}+"\n"+o.optString("from_name")+" → "+o.optString("to_name")+"\n"+o.optString("travel_date")+" • "+o.optString("travel_time")+"\nالمقاعد: "+seatText(o.optString("seats_json"))+"\nالمبلغ: "+fmt(o.optDouble("amount",0.0))+" جنيه",11.5f,MUTED,false),match().apply{topMargin=dp(7)})
        if(st=="TICKET_READY"){
            b.addView(txt("كود الحجز للغير: "+guestCode+" ✅",12f,GOLD,true),match().apply{topMargin=dp(6)})
        }
        b.addView(txt(if(proof)"📷 إثبات التحويل: موجود — اضغط لعرضه" else "📷 إثبات التحويل: لم يصل بعد",11f,if(proof)GREEN else ORANGE,true),match().apply{topMargin=dp(6)})
        val infoRow=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL}
        val proofBtn=button(if(proof)"📷 عرض الإثبات" else "📷 ملف الدفع",BLUE,false);infoRow.addView(proofBtn,weight());proofBtn.setOnClickListener{showOperationReview(id)}
        val chat=button("محادثة 💬",SURFACE,false);infoRow.addView(chat,weight().apply{marginStart=dp(6)});chat.setOnClickListener{openChat(id,o.optString("booking_id"))}
        b.addView(infoRow,match().apply{topMargin=dp(8)})
        if(!terminal){
            val actionRow=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL}
            val yes=button(if(proof)"تأكيد ✓" else "تأكيد — أرسل الإثبات",GREEN,false);val no=button("رفض ×",RED,false)
            yes.isEnabled=proof
            actionRow.addView(yes,weight());actionRow.addView(no,weight().apply{marginStart=dp(6)})
            yes.setOnClickListener{approveFromOperations(id)};no.setOnClickListener{rejectFromOperations(id)}
            b.addView(actionRow,match().apply{topMargin=dp(7)})
        } else b.addView(txt("العملية نهائية — المحادثة وتفاصيل الإثبات متاحة للمراجعة.",10.5f,MUTED,false),match().apply{topMargin=dp(7)})
        setOnClickListener{showOperationReview(id)}
        addView(b)
    }

    private fun approveFromOperations(id:String){
        lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.approve(this@StaffTabsActivity,id)}
            toast(if(r.ok)"تم الاعتماد وإصدار التذكرة في «تذكرتي»." else r.error);if(r.ok)loadBookingOperations()
        }
    }
    private fun rejectFromOperations(id:String){
        val e=EditText(this).apply{hint="سبب الرفض";setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));minLines=2}
        AlertDialog.Builder(this).setTitle("رفض الحجز").setView(e).setPositiveButton("رفض"){_,_->lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){StaffClient.reject(this@StaffTabsActivity,id,e.text.toString().ifBlank{"رفض موظف"})}
            toast(if(r.ok)"تم رفض العملية وحفظها ضمن المنتهية." else r.error);if(r.ok)loadBookingOperations()
        }}.setNegativeButton("إلغاء",null).show()
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
        val id=o.optString("operation_id");val st=o.optString("status").ifBlank{"PAYMENT_PENDING"};val terminal=st=="TICKET_READY"||st=="PAYMENT_REJECTED";val proof=o.optBoolean("proof_available",false)
        val payment=compactPaymentResult(o)
        val ms=(o.optJSONObject("match")?:JSONObject()).optString("status").ifBlank{"PENDING"}
        val body=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11))}
        val row=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        row.addView(txt(o.optString("booking_id").ifBlank{"عملية دفع"},14f,TEXT,true),LinearLayout.LayoutParams(0,-2,1f));row.addView(pill(statusText(st),statusColor(st)),wrap());body.addView(row)
        body.addView(txt(o.optString("customer_name")+"\n"+o.optString("from_name")+" → "+o.optString("to_name")+"\nالمبلغ: "+fmt(o.optDouble("amount",0.0))+" جنيه",11.5f,MUTED,false),match().apply{topMargin=dp(7)})
        val mr=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(7),0,0)}
        mr.addView(txt("حالة الدفع",11f,MUTED,true),LinearLayout.LayoutParams(0,-2,1f));mr.addView(txt(payment.first,12.5f,payment.second,true),wrap())
        val detailBtn=button("فتح العملية",BLUE,false);mr.addView(detailBtn,wrap().apply{marginStart=dp(8)});body.addView(mr);detailBtn.setOnClickListener{showOperationReview(id)}
        val buttons=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.HORIZONTAL}
        val chat=button("محادثة 💬",BLUE,false);buttons.addView(chat,weight());chat.setOnClickListener{openChat(id,o.optString("booking_id"))}
        if(!terminal){
            val yes=button(if(proof)"تأكيد ✓" else "تأكيد — الإثبات مطلوب",GREEN,false);val no=button("رفض ×",RED,false);yes.isEnabled=proof
            buttons.addView(yes,weight().apply{marginStart=dp(6)});buttons.addView(no,weight().apply{marginStart=dp(6)});yes.setOnClickListener{approve(id)};no.setOnClickListener{reject(id)}
        } else body.addView(txt("العملية نهائية — تم إخفاء التأكيد والرفض.",10.5f,MUTED,false),match().apply{topMargin=dp(7)})
        body.addView(buttons,match().apply{topMargin=dp(8)})
        val proofBtn=button(if(proof)"📷 عرض إثبات التحويل" else "📷 لا يوجد إثبات",BLUE,false);proofBtn.isEnabled=proof;proofBtn.setOnClickListener{showOperationReview(id)}
        body.addView(proofBtn,match().apply{topMargin=dp(7)});addView(body)
    }

    private fun seatText(raw:String):String = runCatching{
        val a=JSONArray(raw.ifBlank{"[]"});val out=ArrayList<String>();for(i in 0 until a.length())out.add(a.optString(i));out.joinToString(" • ")
    }.getOrElse{raw.ifBlank{"—"}}

    private fun showOperationReview(id:String){
        if(id.isBlank()) return
        lifecycleScope.launch{
            var r=withContext(Dispatchers.IO){StaffClient.operation(this@StaffTabsActivity,id)}
            if(!r.ok){toast(r.error);return@launch}
            var o=r.body.optJSONObject("operation")?:JSONObject()
            val proof=o.optJSONObject("proof")?:JSONObject()
            val initialRec=o.optJSONObject("reconciliation")?:JSONObject()
            val initialVision=o.optJSONObject("payment_vision")?:initialRec.optJSONObject("vision")?:JSONObject()
            val needVision=proof.optString("path").isNotBlank() && (initialRec.optBoolean("analysis_pending",false) || initialVision.length()==0)
            if(needVision){
                val rr=withContext(Dispatchers.IO){StaffClient.reanalyze(this@StaffTabsActivity,id)}
                if(rr.ok){r=rr;o=rr.body.optJSONObject("operation")?:o}
            }
            val proof2=o.optJSONObject("proof")?:JSONObject()
            val rec=o.optJSONObject("reconciliation")?:JSONObject()
            val vision=o.optJSONObject("payment_vision")?:rec.optJSONObject("vision")?:JSONObject()
            val match=(o.optJSONArray("matches")?.optJSONObject(0))?:JSONObject()
            val checks=match.optJSONObject("checks")?:runCatching{JSONObject(match.optString("checks_json","{}"))}.getOrElse{JSONObject()}
            val tx=o.optJSONArray("android")?.optJSONObject(0)?:JSONObject()
            val payment=compactPaymentResult(o)
            val box=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(4),dp(4),dp(4),0)}
            val scroll=ScrollView(this@StaffTabsActivity)
            val inner=LinearLayout(this@StaffTabsActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(6),dp(8),dp(6))}
            val title=txt(payment.first,15f,payment.second,true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;setPadding(dp(8),dp(8),dp(8),dp(10))}
            inner.addView(title,match())
            fun addLine(label:String,value:String,color:String=TEXT,bold:Boolean=false){inner.addView(txt(label+"\\n"+value,12f,color,bold),match().apply{topMargin=dp(6);bottomMargin=dp(4)})}
            addLine("العميل",o.optString("customer_name").ifBlank{"—"})
            addLine("الرحلة",o.optString("from_name")+" → "+o.optString("to_name"))
            addLine("الموعد",o.optString("travel_date")+" • "+o.optString("travel_time"))
            addLine("المقاعد",seatText(o.optString("seats_json")))
            addLine("مبلغ الحجز",fmt(o.optDouble("amount",0.0))+" جنيه",GOLD,true)
            addLine("وسيلة الدفع",o.optString("payment_method").ifBlank{"—"})
            addLine("حالة المطابقة",match.optString("status").ifBlank{"PENDING"}+" • "+match.optString("reason").ifBlank{"في انتظار اكتمال الأدلة"},payment.second,true)
            inner.addView(txt("تفاصيل فحوص الدفع",13f,TEXT,true),match().apply{topMargin=dp(10)})
            val checkNames=linkedMapOf(
                "booking_amount" to "مبلغ الحجز = مبلغ الصورة",
                "proof_amount" to "مبلغ الصورة = إشعار الهاتف",
                "proof_vs_android_amount" to "مبلغ الصورة = إشعار الهاتف",
                "reference" to "الرقم المرجعي مطابق",
                "sender_phone" to "رقم المرسل مطابق",
                "payment_account" to "حساب الاستلام مطابق",
                "target_account" to "حساب الاستلام مثبت",
                "target_account_exact" to "حساب الاستلام مثبت بدقة",
                "time_window" to "التحويل داخل الفترة المسموحة",
                "transfer_direction_ok" to "اتجاه العملية وارد للشركة",
                "android_present" to "إشعار Android موجود"
            )
            val seen=HashSet<String>()
            for((k,label) in checkNames){
                if(!checks.has(k)||seen.contains(label)) continue
                seen.add(label)
                val ok=checks.optBoolean(k,false)
                inner.addView(txt(label+"  "+if(ok)"✅ مطابق" else "❌ غير مطابق",11.5f,if(ok)GREEN else RED,!ok),match().apply{topMargin=dp(3)})
            }
            if(checks.length()==0) inner.addView(txt("لا توجد فحوص محفوظة بعد.",11.5f,MUTED,false),match())
            inner.addView(txt("Gemini Vision",13f,TEXT,true),match().apply{topMargin=dp(12)})
            addLine("المبلغ في الصورة",vision.optString("amount","غير واضح"))
            addLine("رقم المرسل",vision.optString("sender_phone",vision.optString("sender","غير واضح")))
            addLine("المرجع",vision.optString("reference","غير واضح"))
            addLine("المستلم",vision.optString("recipient_phone",vision.optString("recipient","غير واضح")))
            addLine("التاريخ / الوقت",(vision.optString("date")+" "+vision.optString("time")).trim().ifBlank{"غير واضح"})
            addLine("الثقة",vision.optString("confidence","—"))
            inner.addView(txt("إشعار الهاتف",13f,TEXT,true),match().apply{topMargin=dp(12)})
            if(tx.length()==0){
                addLine("الحالة","لا يوجد إشعار دفع مرتبط حتى الآن.",ORANGE,true)
            }else{
                addLine("المبلغ",tx.optString("amount","—"))
                addLine("المرجع",tx.optString("reference","—"))
                addLine("المرسل",tx.optString("sender_phone","—"))
                addLine("المستلم",tx.optString("recipient_account","—"))
                addLine("المزود",tx.optString("provider","—"))
                addLine("التاريخ / الوقت",tx.optString("transaction_date","—")+" • "+tx.optString("transaction_time","—"))
            }
            val image=ImageView(this@StaffTabsActivity).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.CENTER_INSIDE;minimumHeight=dp(150);setBackgroundColor(Color.parseColor(SURFACE2))}
            val mediaUrl=proof2.optString("public_media_url").ifBlank{proof2.optString("mobile_media_url").ifBlank{proof2.optString("media_url")}}
            if(mediaUrl.isNotBlank()){
                inner.addView(txt("📷 صورة إثبات التحويل",13f,GREEN,true),match().apply{topMargin=dp(10)})
                inner.addView(image,LinearLayout.LayoutParams(-1,dp(300)).apply{topMargin=dp(6);bottomMargin=dp(6)})
                lifecycleScope.launch{
                    val bm=withContext(Dispatchers.IO){loadImage(mediaUrl)}
                    if(bm!=null) image.setImageBitmap(bm) else image.setImageResource(android.R.drawable.ic_dialog_alert)
                }
                image.setOnClickListener{
                    if(image.drawable!=null) AlertDialog.Builder(this@StaffTabsActivity).setTitle("إثبات التحويل").setView(ImageView(this@StaffTabsActivity).apply{setImageDrawable(image.drawable);adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER}).setPositiveButton("إغلاق",null).show()
                }
            } else inner.addView(txt("📷 لا توجد صورة إثبات محفوظة لهذا الطلب.",12f,ORANGE,true),match().apply{topMargin=dp(10)})
            val re=button("🔎 إعادة تحليل الإثبات",GOLD,true)
            re.setOnClickListener{
                re.isEnabled=false;re.text="⏳ جارٍ التحليل..."
                lifecycleScope.launch{
                    val rr=withContext(Dispatchers.IO){StaffClient.reanalyze(this@StaffTabsActivity,id)}
                    if(rr.ok){toast("تم تحديث تحليل الدفع.");showOperationReview(id)}else{toast(rr.error);re.isEnabled=true;re.text="🔎 إعادة تحليل الإثبات"}
                }
            }
            inner.addView(re,match().apply{topMargin=dp(8)})
            scroll.addView(inner);box.addView(scroll,LinearLayout.LayoutParams(-1,dp(620)))
            AlertDialog.Builder(this@StaffTabsActivity).setTitle("تفاصيل التحقق من الدفع").setView(box).setPositiveButton("إغلاق",null).show()
        }
    }


    private fun showPaymentDetails(o:JSONObject){
        val m=o.optJSONObject("match")?:JSONObject();val rec=o.optJSONObject("reconciliation")?:JSONObject();val v=o.optJSONObject("payment_vision")?:rec.optJSONObject("vision")?:JSONObject()
        val checks=m.optJSONObject("checks")?:runCatching{JSONObject(m.optString("checks_json","{}"))}.getOrElse{JSONObject()}
        fun ck(k:String,label:String)=label+": "+if(checks.optBoolean(k,false))"✅ مطابق" else "❌ غير مطابق"
        val tx=o.optJSONArray("android")?:JSONArray();val tx0=if(tx.length()>0)tx.optJSONObject(0)?:JSONObject() else JSONObject()
        val msg=buildString{
            append("حالة المطابقة: ").append(m.optString("status").ifBlank{"UNMATCHED"}).append("\n")
            append("النتيجة: ").append(if(m.optString("status")=="MATCHED")"✅ مطابقة" else if(m.optString("status")=="PARTIAL_MATCH")"⚠️ مطابقة جزئية" else "❌ غير مطابقة").append("\n")
            append("السبب: ").append(m.optString("reason",rec.optString("reason","—"))).append("\n\n")
            append(ck("booking_amount","مبلغ الحجز مع الصورة")).append("\n")
            append(ck("proof_vs_android_amount","مبلغ الصورة مع إشعار الهاتف")).append("\n")
            append(ck("reference","الرقم المرجعي")).append("\n")
            append(ck("sender_phone","رقم المرسل")).append("\n")
            append(ck("payment_account","حساب الاستلام")).append("\n")
            append(ck("android_present","إشعار الدفع")).append("\n\n")
            append("Gemini Vision\n")
            append("المبلغ: ").append(v.optString("amount","غير واضح")).append("\n")
            append("المرسل: ").append(v.optString("sender_phone",v.optString("sender","غير واضح"))).append("\n")
            append("المرجع: ").append(v.optString("reference","غير واضح")).append("\n")
            append("المستلم: ").append(v.optString("recipient_phone",v.optString("recipient","غير واضح"))).append("\n")
            append("الثقة: ").append(v.optString("confidence","—")).append("\n\n")
            if(tx0.length()>0){append("إشعار الهاتف\nالمبلغ: ").append(tx0.optString("amount","—")).append("\nالمرجع: ").append(tx0.optString("reference","—")).append("\nالمرسل: ").append(tx0.optString("sender_phone","—")).append("\nالمستلم: ").append(tx0.optString("recipient_account","—")).append("\n")}
            val missing=checks.keys().asSequence().filter{!checks.optBoolean(it)}.map{it}.toList()
            if(missing.isNotEmpty())append("\nبيانات ناقصة: ").append(missing.joinToString("، "))
        }
        AlertDialog.Builder(this).setTitle("تفاصيل الدفع").setMessage(msg).setPositiveButton("إغلاق",null).show()
    }

    private fun approve(id:String){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.approve(this@StaffTabsActivity,id)};toast(if(r.ok)"تم اعتماد العملية وإصدار التذكرة." else r.error);if(r.ok)loadPayments()}}
    private fun reject(id:String){val e=EditText(this).apply{hint="سبب الرفض";setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));minLines=2};AlertDialog.Builder(this).setTitle("رفض عملية الدفع").setView(e).setPositiveButton("رفض"){_,_->lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.reject(this@StaffTabsActivity,id,e.text.toString().ifBlank{"رفض موظف"})};toast(if(r.ok)"تم رفض العملية." else r.error);if(r.ok)loadPayments()}}.setNegativeButton("إلغاء",null).show()}

    private fun openChat(operationId:String,bookingId:String){
        if(operationId.isBlank()){toast("رقم العملية غير متاح.");return}
        startActivity(Intent(this,StaffChatActivity::class.java).apply{
            putExtra("operation_id",operationId);putExtra("booking_id",bookingId)
        })
    }

    private fun handleIncomingIntent(i:Intent?){
        val operationId=i?.getStringExtra("open_operation_id").orEmpty()
        val bookingId=i?.getStringExtra("open_booking_id").orEmpty()
        val openChat=i?.getBooleanExtra("open_chat",false)?:false
        if(openChat && operationId.isNotBlank()){
            openChat(operationId,bookingId)
            i.removeExtra("open_chat");i.removeExtra("open_operation_id");i.removeExtra("open_booking_id")
        }
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
    private fun parseLocal(cn:HttpURLConnection):StaffResult{
        val code=cn.responseCode
        val st=if(code in 200..299)cn.inputStream else cn.errorStream
        val raw=if(st!=null)BufferedReader(InputStreamReader(st,Charsets.UTF_8)).use{it.readText()} else ""
        val jo=runCatching{JSONObject(raw)}.getOrElse{JSONObject()}
        return if(code in 200..299)StaffResult(true,jo) else StaffResult(false,jo,"HTTP_"+code+":"+jo.optString("error",raw))
    }

    private fun loadImage(path:String):Bitmap?=runCatching{
        val base=SecureConfig.getServerUrl(this).trimEnd('/')
        val u=if(path.startsWith("http"))path else base+path
        val cn=(URL(u).openConnection() as HttpURLConnection).apply{connectTimeout=12000;readTimeout=25000;setRequestProperty("Accept","image/*");setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(this@StaffTabsActivity));setRequestProperty("X-SuperJet-Device-Id",StaffClient.deviceId(this@StaffTabsActivity))}
        val code=cn.responseCode
        if(code !in 200..299){cn.disconnect();return@runCatching null}
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
    private fun minTabWidth(b:MaterialButton){b.minimumWidth=dp(118);b.maxLines=2;b.ellipsize=null}
    private fun match()=LinearLayout.LayoutParams(-1,-2);private fun wrap()=LinearLayout.LayoutParams(-2,-2);private fun weight()=LinearLayout.LayoutParams(0,dp(48),1f);private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(dp(w),dp(h));private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt();private var busy=false;private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}