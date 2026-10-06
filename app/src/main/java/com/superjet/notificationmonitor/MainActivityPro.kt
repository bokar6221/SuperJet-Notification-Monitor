package com.superjet.notificationmonitor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivityPro : AppCompatActivity() {
    companion object {
        private const val BG="#07111D"; private const val SURFACE="#101D2B"
        private const val SURFACE2="#0D1824"; private const val NAVY="#07111D"
        private const val STROKE="#294055"; private const val GOLD="#F2C14E"
        private const val BLUE="#397DFF"; private const val GREEN="#32CC86"
        private const val RED="#E96868"; private const val TEXT="#F5F7FA"
        private const val MUTED="#9AAABC"
    }

    private lateinit var root: LinearLayout
    private var userBox: TextInputEditText?=null
    private var passBox: TextInputEditText?=null
    private var status: TextView?=null
    private var opsBox: LinearLayout?=null
    private var notifBox: LinearLayout?=null
    private var statOps: TextView?=null
    private var statAmount: TextView?=null
    private var statNotif: TextView?=null
    private var selectedOperation=""
    private var busy=false
    private var chatServiceStarted=false
    private var pendingOpenBookingId=""

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.parseColor(NAVY)
        window.navigationBarColor=Color.parseColor(NAVY)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.parseColor(BG))}
        setContentView(root)
        pendingOpenBookingId=intent?.getStringExtra("open_booking_id").orEmpty()
        if(SecureConfig.getToken(this).isBlank()) showLogin() else { showDashboard(); startChatService(); maybeOpenNotificationBooking() }
    }

    override fun onResume(){
        super.onResume()
        if(SecureConfig.getToken(this).isNotBlank() && opsBox!=null) refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenBookingId=intent?.getStringExtra("open_booking_id").orEmpty()
        if(pendingOpenBookingId.isNotBlank() && SecureConfig.getToken(this).isNotBlank()) maybeOpenNotificationBooking()
    }


    private fun reset(){root.removeAllViews();status=null;opsBox=null;notifBox=null}

    private fun showLogin(){
        reset()
        val scroll=ScrollView(this).apply{isFillViewport=true}
        val page=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(22),dp(28),dp(22),dp(28))
        }

        val logo=ImageView(this).apply{setImageResource(R.drawable.superjet_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE}
        page.addView(logo,lp(118,118).apply{bottomMargin=dp(12)})
        page.addView(txt("SUPERJET • STAFF OPERATIONS",11.5f,GOLD,true).apply{gravity=Gravity.CENTER},match().apply{bottomMargin=dp(7)})
        page.addView(txt("SuperJet Staff",31f,TEXT,true).apply{gravity=Gravity.CENTER},match())
        page.addView(txt("الدفع • الحجوزات • مطابقة التحويلات",14f,MUTED,false).apply{gravity=Gravity.CENTER},match().apply{bottomMargin=dp(24)})

        val card=card(SURFACE,24f)
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(22),dp(20),dp(20))}
        card.addView(form)
        form.addView(txt("تسجيل دخول الموظف",19f,TEXT,true),match().apply{bottomMargin=dp(17)})

        val ul=TextInputLayout(this).apply{
            hint="اسم المستخدم";boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        userBox=TextInputEditText(this).apply{isSingleLine=true;textSize=16f;inputType=InputType.TYPE_CLASS_TEXT;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED))}
        ul.addView(userBox);form.addView(ul,match().apply{bottomMargin=dp(13)})

        val pl=TextInputLayout(this).apply{
            hint="كلمة المرور";boxBackgroundMode=TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        passBox=TextInputEditText(this).apply{isSingleLine=true;textSize=16f;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED))}
        pl.addView(passBox);form.addView(pl,match().apply{bottomMargin=dp(18)})

        val login=button("دخول آمن  ↪",GOLD,true)
        form.addView(login,match())
        login.setOnClickListener{doLogin(login)}

        form.addView(txt("لا يحتاج الموظف إدخال عنوان السيرفر أو Token. يتم إنشاء جلسة آمنة بعد تسجيل الدخول.",11f,MUTED,false).apply{
            gravity=Gravity.CENTER
        },match().apply{topMargin=dp(13)})
        page.addView(card,match().apply{bottomMargin=dp(18)})
        page.addView(txt("SUPERJET STAFF • v3.3 FINAL PRO",10.5f,MUTED,true).apply{gravity=Gravity.CENTER},match())
        scroll.addView(page)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
    }

    private fun doLogin(btn:MaterialButton){
        val u=userBox?.text?.toString()?.trim().orEmpty()
        val p=passBox?.text?.toString().orEmpty()
        if(u.isBlank()||p.isBlank()){toast("أدخل اسم المستخدم وكلمة المرور.");return}
        btn.isEnabled=false
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){StaffClient.login(this@MainActivityPro,u,p)}
            btn.isEnabled=true
            if(r.ok) { startChatService(); requestNotificationPermission(); showDashboard() } else toast(r.error)
        }
    }

    private fun showDashboard(){
        reset()
        val scroll=ScrollView(this).apply{isFillViewport=true}
        val page=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(24))}
        scroll.addView(page)

        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val logo=ImageView(this).apply{setImageResource(R.drawable.superjet_logo);scaleType=ImageView.ScaleType.CENTER_INSIDE}
        header.addView(logo,lp(54,54).apply{marginEnd=dp(10)})
        val h=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;}
        h.addView(txt("SuperJet Staff",23f,TEXT,true),match())
        status=txt("جارٍ تحميل بيانات الموظف...",12f,MUTED,false);h.addView(status,match().apply{topMargin=dp(2)})
        header.addView(h,LinearLayout.LayoutParams(0,-2,1f))
        val out=button("خروج",RED,false).apply{minHeight=dp(42)}
        header.addView(out,wrap().apply{marginStart=dp(8)})
        out.setOnClickListener{SecureConfig.clearToken(this);stopChatService();showLogin()}
        page.addView(header,match().apply{bottomMargin=dp(14)})

        val hero=card("#0F2435",20f);val hb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(15),dp(16),dp(15))}
        hero.addView(hb)
        hb.addView(txt("مركز العمليات",18f,TEXT,true),match().apply{bottomMargin=dp(8)})
        val badges=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        badges.addView(pill("● متصل",GREEN),wrap())
        badges.addView(pill(if(notificationAccess())"الإشعارات مفعلة" else "الإشعارات تحتاج تفعيل",if(notificationAccess())GREEN else GOLD),wrap().apply{marginStart=dp(7)})
        hb.addView(badges)
        page.addView(hero,match().apply{bottomMargin=dp(12)})

        val actions=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val a1=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val sync=button("مزامنة الدفع  ⇄",GOLD,true);val access=button("صلاحية الإشعارات  ◉",BLUE,false)
        a1.addView(sync,weight());a1.addView(access,weight().apply{marginStart=dp(8)});actions.addView(a1,match().apply{bottomMargin=dp(8)})
        val a2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val refreshBtn=button("تحديث  ⟳",BLUE,false);val info=button("حالة الجهاز  ✓",GREEN,false);val searchBtn=button("بحث  ⌕",GOLD,true)
        a2.addView(refreshBtn,weight());a2.addView(info,weight().apply{marginStart=dp(8)});a2.addView(searchBtn,weight().apply{marginStart=dp(8)});actions.addView(a2)
        page.addView(actions,match().apply{bottomMargin=dp(18)})
        sync.setOnClickListener{manualSync()}
        access.setOnClickListener{startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}
        refreshBtn.setOnClickListener{refresh()}
        info.setOnClickListener{showDeviceInfo()};searchBtn.setOnClickListener{showSearchDialog()}

        page.addView(txt("ملخص اليوم",18f,TEXT,true),match().apply{bottomMargin=dp(8)})
        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val s1=stat("العمليات","0",BLUE).also{statOps=it.findViewWithTag<TextView>("stat_value")};val s2=stat("الإجمالي","0.00",GOLD).also{statAmount=it.findViewWithTag<TextView>("stat_value")};val s3=stat("الإشعارات","0",GREEN).also{statNotif=it.findViewWithTag<TextView>("stat_value")}
        stats.addView(s1,weightCard());stats.addView(s2,weightCard().apply{marginStart=dp(8)});stats.addView(s3,weightCard().apply{marginStart=dp(8)})
        page.addView(stats,match().apply{bottomMargin=dp(18)})

        page.addView(txt("طلبات الدفع والحجوزات",18f,TEXT,true),match().apply{bottomMargin=dp(8)})
        opsBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        page.addView(opsBox,match().apply{bottomMargin=dp(18)})

        page.addView(txt("آخر إشعارات الدفع",18f,TEXT,true),match().apply{bottomMargin=dp(8)})
        notifBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        page.addView(notifBox,match())
        page.addView(txt("SuperJet Staff • Payment Reconciliation",10.5f,MUTED,false).apply{gravity=Gravity.CENTER},match().apply{topMargin=dp(18)})
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        refresh()
    }

    private fun refresh(){
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){StaffClient.dashboard(this@MainActivityPro)}
            val locals=NotificationStore.all(this@MainActivityPro).filter{it.isPaymentNotification || it.parseStatus=="UNPARSED"}
            statNotif?.text=locals.size.toString()
            renderLocal(locals)
            if(!r.ok){
                if(r.error.startsWith("HTTP_401")){SecureConfig.clearToken(this@MainActivityPro);showLogin();return@launch}
                status?.text="تعذر تحديث البيانات"
                renderOperations(JSONArray())
                return@launch
            }
            val emp=r.body.optJSONObject("employee")?:JSONObject()
            val d=r.body.optJSONObject("dashboard")?:JSONObject()
            val today=d.optJSONObject("today")?:JSONObject()
            status?.text=emp.optString("name").ifBlank{"الموظف"}+" • "+if(emp.optString("role")=="manager")"مدير" else "موظف"+" • "+emp.optString("status").ifBlank{"OFF_SHIFT"}
            statOps?.text=today.optInt("operations",0).toString()
            statAmount?.text=String.format(Locale.US,"%.2f",today.optDouble("total",0.0))
            renderOperations(d.optJSONArray("operations")?:JSONArray())
        }
    }

    private fun renderOperations(a:JSONArray){
        val box=opsBox?:return;box.removeAllViews()
        if(a.length()==0){box.addView(empty("لا توجد عمليات دفع حاليًا.\nستظهر هنا عند ربط إشعار دفع بطلب حجز."));return}
        for(i in 0 until a.length()){
            val o=a.optJSONObject(i)?:continue
            val c=card(SURFACE,18f);val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(14),dp(14),dp(14))};c.addView(b)
            val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            top.addView(txt(o.optString("booking_id").ifBlank{"طلب دفع"},15f,TEXT,true),LinearLayout.LayoutParams(0,-2,1f))
            top.addView(pill(o.optString("status").ifBlank{"PAYMENT_PENDING"},GREEN),wrap());b.addView(top,match().apply{bottomMargin=dp(7)})
            b.addView(txt(o.optString("customer_name")+"\n"+o.optString("from_name")+" → "+o.optString("to_name")+"\n"+o.optString("travel_date")+" • "+o.optString("travel_time")+"\nالمبلغ: "+String.format(Locale.US,"%.2f",o.optDouble("amount",0.0))+" جنيه",12.5f,MUTED,false),match().apply{bottomMargin=dp(10)})
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            val yes=button("تأكيد  ✓",GREEN,false);val no=button("رفض  ×",RED,false);val chat=button("محادثة  💬",BLUE,false)
            row.addView(yes,weight());row.addView(no,weight().apply{marginStart=dp(7)});row.addView(chat,weight().apply{marginStart=dp(7)});b.addView(row);chat.setOnClickListener{showChatDialog(o.optString("operation_id"),o.optString("booking_id"))}
            yes.setOnClickListener{operationAction(o.optString("operation_id"),true)}
            no.setOnClickListener{reject(o.optString("operation_id"))}
            box.addView(c,match().apply{bottomMargin=dp(9)})
        }
    }

    private fun renderLocal(list:List<NotificationItem>){
        val box=notifBox?:return;box.removeAllViews()
        if(list.isEmpty()){box.addView(empty("لم يتم اكتشاف إشعار دفع حتى الآن.\nاترك قارئ الإشعارات مفعّلًا وسنظهر VF-Cash هنا فورًا."));return}
        for(x in list.take(8)){
            val c=card(SURFACE2,18f);val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12))};c.addView(b)
            val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            top.addView(txt(provider(x.provider),13f,GOLD,true),LinearLayout.LayoutParams(0,-2,1f))
            top.addView(pill(if(x.synced)"تمت المزامنة" else if(x.syncError.isNotBlank())"تعذر الإرسال" else "مكتشف",if(x.synced)GREEN else GOLD),wrap());b.addView(top,match().apply{bottomMargin=dp(6)})
            val amount=x.amount?.let{String.format(Locale.US,"%.2f جنيه",it)}?:"المبلغ غير مستخرج"
            val nums=listOf(x.senderPhone,x.recipientAccount).filter{it.isNotBlank()}.joinToString(" • ")
            b.addView(txt(amount+"\n"+if(nums.isBlank())"الأرقام: —" else nums+"\nالمرجع: "+x.reference.ifBlank{"—"}+"\n"+SimpleDateFormat("dd/MM/yyyy • HH:mm",Locale.getDefault()).format(Date(x.time)),12.5f,MUTED,false),match())
            if(x.parseStatus=="UNPARSED")b.addView(txt("الإشعار محفوظ للمراجعة — لم يتم تخمين بيانات ناقصة.",11f,GOLD,true),match().apply{topMargin=dp(5)})
            box.addView(c,match().apply{bottomMargin=dp(8)})
        }
    }

    private fun manualSync(){
        if(busy)return;busy=true
        lifecycleScope.launch{
            val pending=withContext(Dispatchers.IO){NotificationStore.pending(this@MainActivityPro)}
            var ok=0;var bad=0
            for(x in pending){
                val r=withContext(Dispatchers.IO){NotificationApi.syncOne(this@MainActivityPro,x)}
                if(r.ok){NotificationStore.markSynced(this@MainActivityPro,x.eventId);ok++}else{NotificationStore.markSynced(this@MainActivityPro,x.eventId,r.error);bad++}
            }
            busy=false;toast("المزامنة: نجح $ok • فشل $bad");refresh()
        }
    }

    private fun operationAction(id:String,approve:Boolean){
        if(id.isBlank())return
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){if(approve)StaffClient.approve(this@MainActivityPro,id) else StaffClient.reject(this@MainActivityPro,id,"رفض موظف")}
            toast(if(r.ok)"تم حفظ القرار." else r.error);if(r.ok)refresh()
        }
    }

    private fun reject(id:String){
        val e=EditText(this).apply{hint="سبب الرفض";minLines=2;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED))}
        AlertDialog.Builder(this).setTitle("رفض عملية الدفع").setView(e).setPositiveButton("رفض"){_,_->lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){StaffClient.reject(this@MainActivityPro,id,e.text.toString().ifBlank{"رفض موظف"})}
            toast(if(r.ok)"تم رفض العملية." else r.error);if(r.ok)refresh()
        }}.setNegativeButton("إلغاء",null).show()
    }

    private fun showSearchDialog(){
        val input=EditText(this).apply{hint="كود الحجز أو الموبايل أو اسم العميل أو المرجع";isSingleLine=true;setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));setBackgroundColor(Color.parseColor(SURFACE2));setPadding(dp(14),dp(12),dp(14),dp(12))}
        AlertDialog.Builder(this).setTitle("بحث آمن داخل عمليات SuperJet").setView(input).setPositiveButton("بحث"){_,_->
            val q=input.text.toString().trim();if(q.isBlank())return@setPositiveButton
            lifecycleScope.launch{
                val r=withContext(Dispatchers.IO){StaffClient.search(this@MainActivityPro,q)}
                if(!r.ok){toast(r.error);return@launch}
                val arr=r.body.optJSONArray("results")?:JSONArray();if(arr.length()==0){toast("لا توجد نتائج مطابقة.");return@launch}
                val labels=ArrayList<String>();for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;labels.add(x.optString("booking_id")+" • "+x.optString("customer_name")+" • "+String.format(Locale.US,"%.2f",x.optDouble("amount",0.0))+" جنيه")}
                AlertDialog.Builder(this@MainActivityPro).setTitle("نتائج البحث").setItems(labels.toTypedArray()){_,which->
                    val x=arr.optJSONObject(which)?:return@setItems;showChatDialog(x.optString("operation_id"),x.optString("booking_id"))
                }.setNegativeButton("إغلاق",null).show()
            }
        }.setNegativeButton("إلغاء",null).show()
    }


    private fun maybeOpenNotificationBooking(){
        val bid=pendingOpenBookingId.trim()
        if(bid.isBlank()) return
        pendingOpenBookingId=""
        lifecycleScope.launch {
            val r=withContext(Dispatchers.IO){StaffClient.search(this@MainActivityPro,bid)}
            if(!r.ok)return@launch
            val arr=r.body.optJSONArray("results")?:return@launch
            if(arr.length()==0)return@launch
            val x=arr.optJSONObject(0)?:return@launch
            val op=x.optString("operation_id")
            val booking=x.optString("booking_id").ifBlank{bid}
            if(op.isNotBlank()) showChatDialog(op,booking)
        }
    }

    private fun showDeviceInfo(){
        val id=StaffClient.deviceId(this)
        val enabled=notificationAccess()
        AlertDialog.Builder(this).setTitle("حالة الجهاز").setMessage("الجهاز: "+id+"\nNotification Access: "+if(enabled)"مفعّل ✅" else "غير مفعّل ❌").setPositiveButton("حسنًا",null).show()
    }

    private fun showChatDialog(operationId:String, bookingId:String){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(6),dp(10),0)}
        val messages=TextView(this).apply{textSize=13f;setTextColor(Color.parseColor(TEXT));setPadding(dp(8),dp(8),dp(8),dp(8))}
        val scroll=ScrollView(this).apply{addView(messages);layoutParams=LinearLayout.LayoutParams(-1,dp(300))}
        val input=EditText(this).apply{hint="اكتب رسالة للعميل...";setTextColor(Color.parseColor(TEXT));setHintTextColor(Color.parseColor(MUTED));setBackgroundColor(Color.parseColor(SURFACE2));setPadding(dp(14),dp(10),dp(14),dp(10))}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(10),0,0)}
        val send=button("إرسال",GOLD,true);row.addView(input,LinearLayout.LayoutParams(0,dp(52),1f));row.addView(send,LinearLayout.LayoutParams(dp(100),dp(52)).apply{marginStart=dp(8)})
        box.addView(scroll);box.addView(row)
        val dialog=AlertDialog.Builder(this).setTitle("محادثة الطلب $bookingId").setView(box).setNegativeButton("إغلاق",null).create()
        fun refresh(){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.chat(this@MainActivityPro,operationId)};if(r.ok){val arr=r.body.optJSONArray("messages")?:JSONArray();val sb=StringBuilder();for(i in 0 until arr.length()){val z=arr.optJSONObject(i)?:continue;sb.append(if(z.optString("sender_type")=="staff")"أنت" else "العميل").append(": ").append(z.optString("message")).append("\n\n")};messages.text=sb.toString().ifBlank{"لا توجد رسائل بعد."};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}}
        send.setOnClickListener{val msg=input.text.toString().trim();if(msg.isBlank())return@setOnClickListener;send.isEnabled=false;lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.sendChat(this@MainActivityPro,operationId,msg)};send.isEnabled=true;if(r.ok){input.setText("");refresh()}else toast(r.error)}}
        dialog.setOnShowListener{refresh();lifecycleScope.launch{repeat(20){kotlinx.coroutines.delay(2500);if(dialog.isShowing)refresh()}}}
        dialog.show()
    }

    private fun requestNotificationPermission(){
        if(android.os.Build.VERSION.SDK_INT>=33){ requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),7001) }
    }

    private fun startChatService(){
        if(chatServiceStarted)return
        try{ androidx.core.content.ContextCompat.startForegroundService(this,Intent(this,ChatNotificationService::class.java)); chatServiceStarted=true }catch(_:Exception){}
    }

    private fun stopChatService(){
        try{ stopService(Intent(this,ChatNotificationService::class.java)) }catch(_:Exception){}
        chatServiceStarted=false
    }

    private fun notificationAccess():Boolean{
        val e=Settings.Secure.getString(contentResolver,"enabled_notification_listeners")?:""
        return e.contains(packageName)
    }
    private fun provider(p:String)=when(p){"VODAFONE_CASH"->"VF-Cash";"ORANGE_CASH"->"Orange Cash";"ETISALAT_CASH"->"Etisalat Cash";"WE_PAY"->"WE Pay";"INSTAPAY"->"InstaPay";else->p.ifBlank{"إشعار دفع"}}
    private fun txt(s:String,size:Float,color:String,bold:Boolean)=TextView(this).apply{text=s;textSize=size;setTextColor(Color.parseColor(color));typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;setLineSpacing(0f,1.16f)}
    private fun card(bg:String,r:Float)=MaterialCardView(this).apply{radius=dp(r.toInt()).toFloat();cardElevation=dp(3).toFloat();setCardBackgroundColor(Color.parseColor(bg));strokeWidth=dp(1);strokeColor=Color.parseColor(STROKE)}
    private fun pill(s:String,color:String)=txt(s,10.5f,color,true).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.parseColor("#18232E"));cornerRadius=dp(10).toFloat()};setPadding(dp(8),dp(5),dp(8),dp(5))}
    private fun button(s:String,bg:String,dark:Boolean)=MaterialButton(this).apply{text=s;textSize=12.5f;isAllCaps=false;cornerRadius=dp(15);insetTop=0;insetBottom=0;minHeight=dp(50);backgroundTintList=android.content.res.ColorStateList.valueOf(Color.parseColor(bg));setTextColor(Color.parseColor(if(dark)NAVY else TEXT))}
    private fun stat(t:String,v:String,color:String):MaterialCardView {
        val c=MaterialCardView(this).apply{radius=dp(16).toFloat();cardElevation=dp(2).toFloat();setCardBackgroundColor(Color.parseColor(SURFACE));strokeWidth=dp(1);strokeColor=Color.parseColor(STROKE)}
        val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(10),dp(10),dp(10))}
        b.addView(txt(t,10.5f,MUTED,true),match())
        val value=txt(v,19f,color,true);value.tag="stat_value";b.addView(value,match().apply{topMargin=dp(5)})
        c.addView(b);return c
    }
    private fun empty(s:String)=card(SURFACE,17f).apply{addView(txt(s,12f,MUTED,false).apply{gravity=Gravity.CENTER;setPadding(dp(12),dp(18),dp(12),dp(18))})}
    private fun match()=LinearLayout.LayoutParams(-1,-2);private fun wrap()=LinearLayout.LayoutParams(-2,-2)
    private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(dp(w),dp(h))
    private fun weight()=LinearLayout.LayoutParams(0,dp(50),1f);private fun weightCard()=LinearLayout.LayoutParams(0,-2,1f)
    private fun dp(v:Int)=(v*resources.displayMetrics.density+0.5f).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}

data class StaffResult(val ok:Boolean,val body:JSONObject=JSONObject(),val error:String="")
object StaffClient{
    private const val C=10000;private const val R=20000
    fun deviceId(c:Context)=Settings.Secure.getString(c.contentResolver,Settings.Secure.ANDROID_ID)?.takeIf{it.isNotBlank()}?:UUID.nameUUIDFromBytes((c.packageName+android.os.Build.MODEL).toByteArray()).toString()
    fun login(c:Context,u:String,p:String):StaffResult{
        val j=JSONObject().apply{put("username",u);put("password",p);put("device_id",deviceId(c))}
        val r=req(c,"POST","/api/mobile/login",j.toString(),false)
        if(r.ok){
            SecureConfig.setToken(c,r.body.optString("token"))
            val cursor=r.body.optJSONObject("alert_cursor")
            if(cursor!=null) ChatNotificationService.seedState(c,cursor.optLong("event_id",0L),cursor.optLong("chat_id",0L))
            else ChatNotificationService.resetState(c)
        }
        return r
    }
    fun logout(c:Context)=req(c,"POST","/api/mobile/logout","{}",true)
    fun heartbeat(c:Context)=req(c,"POST","/api/mobile/heartbeat","{}",true)
    fun dashboard(c:Context)=req(c,"GET","/api/mobile/dashboard",null,true)
    fun operation(c:Context,id:String)=req(c,"GET","/api/mobile/operations/"+id,null,true)
    fun reanalyze(c:Context,id:String)=req(c,"POST","/api/mobile/operations/$id/reanalyze-proof","{}",true)
    fun approve(c:Context,id:String)=req(c,"POST","/api/mobile/operations/$id/approve","{}",true)
    fun reject(c:Context,id:String,reason:String)=req(c,"POST","/api/mobile/operations/$id/reject",JSONObject().put("reason",reason).toString(),true)
    fun chat(c:Context,operationId:String)=req(c,"GET","/api/mobile/operations/"+operationId+"/chat",null,true)
    fun sendChat(c:Context,operationId:String,message:String):StaffResult{
        return req(c,"POST","/api/mobile/operations/"+operationId+"/chat",JSONObject().put("message",message).toString(),true)
    }
    fun sendChatMedia(c:Context,operationId:String,message:String,uri:Uri):StaffResult{
        val base=SecureConfig.getServerUrl(c).trimEnd('/')
        if(base.isBlank()) return StaffResult(false,error="SERVER_URL_NOT_CONFIGURED")
        val mime=c.contentResolver.getType(uri)?.lowercase().orEmpty().ifBlank{"image/jpeg"}
        val safeMime=when(mime){"image/png","image/webp","image/jpeg"->mime;else->"image/jpeg"}
        val ext=when(safeMime){"image/png"->"png";"image/webp"->"webp";else->"jpg"}
        val boundary="----SuperJetBoundary"+UUID.randomUUID()
        val cn=(java.net.URL(base+"/api/mobile/operations/"+operationId+"/chat").openConnection() as java.net.HttpURLConnection).apply{
            requestMethod="POST";connectTimeout=C;readTimeout=R;doOutput=true
            setRequestProperty("Accept","application/json")
            setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(c))
            setRequestProperty("X-SuperJet-Device-Id",deviceId(c))
            setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary)
        }
        return try{
            java.io.DataOutputStream(cn.outputStream).use{out->
                if(message.isNotBlank()){
                    out.writeBytes("--"+boundary+"\\r\\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"message\"\\r\\n\\r\\n")
                    out.write(message.toByteArray(Charsets.UTF_8));out.writeBytes("\\r\\n")
                }
                out.writeBytes("--"+boundary+"\\r\\n")
                out.writeBytes("Content-Disposition: form-data; name=\"media\"; filename=\"chat_image."+ext+"\"\\r\\n")
                out.writeBytes("Content-Type: "+safeMime+"\\r\\n\\r\\n")
                c.contentResolver.openInputStream(uri)?.use{it.copyTo(out)} ?: return StaffResult(false,error="MEDIA_READ_FAILED")
                out.writeBytes("\\r\\n--"+boundary+"--\\r\\n")
            }
            val code=cn.responseCode
            val st=if(code in 200..299)cn.inputStream else cn.errorStream
            val raw=if(st!=null)java.io.BufferedReader(java.io.InputStreamReader(st,Charsets.UTF_8)).use{it.readText()} else ""
            val jo=runCatching{JSONObject(raw)}.getOrElse{JSONObject()}
            if(code in 200..299) StaffResult(true,jo) else StaffResult(false,jo,"HTTP_"+code+":"+jo.optString("error",raw))
        }catch(e:Exception){StaffResult(false,error=e.javaClass.simpleName+":"+(e.message?:"upload error"))}finally{cn.disconnect()}
    }
    fun search(c:Context,q:String)=req(c,"GET","/api/mobile/search?q="+java.net.URLEncoder.encode(q,"UTF-8"),null,true)
    fun pollStaffAlerts(c:Context,sinceEventId:Long,sinceChatId:Long)=req(c,"GET","/api/mobile/alerts?since_event_id="+sinceEventId+"&since_chat_id="+sinceChatId+"&wait=20",null,true)
    fun initializeStaffAlerts(c:Context)=req(c,"GET","/api/mobile/alerts?initialize=1&wait=0",null,true)
    fun pollChatNotifications(c:Context,sinceId:Long)=req(c,"GET","/api/mobile/chat/notifications?since_id="+sinceId+"&wait=20",null,true)
    private fun req(c:Context,method:String,path:String,body:String?,auth:Boolean):StaffResult{
        val base=SecureConfig.getServerUrl(c).trimEnd('/')
        if(base.isBlank())return StaffResult(false,error="SERVER_URL_NOT_CONFIGURED")
        val cn=(java.net.URL(base+path).openConnection() as java.net.HttpURLConnection).apply{
            requestMethod=method;connectTimeout=C;readTimeout=R
            setRequestProperty("Accept","application/json")
            if(auth){setRequestProperty("Authorization","Bearer "+SecureConfig.getToken(c));setRequestProperty("X-SuperJet-Device-Id",deviceId(c))}
            if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json; charset=UTF-8")}
        }
        return try{
            if(body!=null)cn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
            val code=cn.responseCode;val st=if(code in 200..299)cn.inputStream else cn.errorStream
            val raw=if(st!=null)java.io.BufferedReader(java.io.InputStreamReader(st,Charsets.UTF_8)).use{it.readText()} else ""
            val jo=runCatching{JSONObject(raw)}.getOrElse{JSONObject()}
            if(code in 200..299)StaffResult(true,jo) else StaffResult(false,jo,"HTTP_$code:"+jo.optString("error",raw))
        }catch(e:Exception){StaffResult(false,error=e.javaClass.simpleName+":"+(e.message?:"network error"))}finally{cn.disconnect()}
    }
}