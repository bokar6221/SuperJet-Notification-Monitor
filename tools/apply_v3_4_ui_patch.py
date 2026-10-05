from pathlib import Path
p=Path('app/src/main/java/com/superjet/notificationmonitor/MainActivityPro.kt')
s=p.read_text(encoding='utf-8')
needle='page.addView(actions,match().apply{bottomMargin=dp(18)})\n        sync.setOnClickListener{manualSync()}'
repl='''page.addView(actions,match().apply{bottomMargin=dp(12)})
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val n1=button("الإشعارات",BLUE,false);val n2=button("المحادثات",GOLD,true);val n3=button("المدفوعات",GREEN,false)
        nav.addView(n1,weight());nav.addView(n2,weight().apply{marginStart=dp(6)});nav.addView(n3,weight().apply{marginStart=dp(6)})
        page.addView(nav,match().apply{bottomMargin=dp(18)})
        n1.setOnClickListener{showNotificationsPage()}; n2.setOnClickListener{showConversationsPage()}; n3.setOnClickListener{showPaymentsPage()}
        sync.setOnClickListener{manualSync()}'''
if needle not in s: raise SystemExit('dashboard actions marker not found')
s=s.replace(needle,repl,1)
needle='page.addView(stats,match().apply{bottomMargin=dp(18)})'
repl='''page.addView(stats,match().apply{bottomMargin=dp(10)})
        val balance=card("#102536",16f);val bb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))};balance.addView(bb);bb.addView(txt("الرصيد الفعلي المستلم من إشعارات الهاتف",11f,MUTED,true));val actual=txt("0.00 جنيه",23f,GOLD,true);actual.tag="actual_balance";bb.addView(actual,match().apply{topMargin=dp(4)});page.addView(balance,match().apply{bottomMargin=dp(18)})'''
s=s.replace(needle,repl,1)
s=s.replace('statAmount?.text=String.format(Locale.US,"%.2f",today.optDouble("total",0.0))\n            renderOperations','statAmount?.text=String.format(Locale.US,"%.2f",today.optDouble("total",0.0))\n            root.findViewWithTag<TextView>("actual_balance")?.text=String.format(Locale.US,"%.2f جنيه",d.optDouble("actual_balance",0.0))\n            renderOperations',1)
marker='    private fun renderOperations(a:JSONArray){'
methods='''    private fun showNotificationsPage(){ lifecycleScope.launch{ val r=withContext(Dispatchers.IO){StaffClient.notifications(this@MainActivityPro)}; if(!r.ok){toast(r.error);return@launch}; val arr=r.body.optJSONArray("notifications")?:JSONArray(); val box=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL}; if(arr.length()==0)box.addView(empty("لا توجد إشعارات دفع محفوظة.")); for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val c=card(SURFACE,16f);val b=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12))};b.addView(txt(provider(x.optString("provider")),13f,GOLD,true));b.addView(txt("المبلغ: "+String.format(Locale.US,"%.2f",x.optDouble("amount",0.0))+" جنيه\\nالمرجع: "+x.optString("reference").ifBlank{"—"}+"\\nمن: "+x.optString("sender_phone").ifBlank{"—"},12f,TEXT,false),match().apply{topMargin=dp(5)});c.addView(b);box.addView(c,match().apply{bottomMargin=dp(8)})};showPageDialog("الإشعارات والمزامنة",box)}}

    private fun showConversationsPage(){ lifecycleScope.launch{ val r=withContext(Dispatchers.IO){StaffClient.conversations(this@MainActivityPro)}; if(!r.ok){toast(r.error);return@launch}; val arr=r.body.optJSONArray("conversations")?:JSONArray(); val box=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL}; if(arr.length()==0)box.addView(empty("لا توجد محادثات.")); for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val c=card(SURFACE,16f);val b=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))};b.addView(txt(x.optString("customer_name").ifBlank{"عميل"}+"\\n"+x.optString("booking_id"),13f,TEXT,true),LinearLayout.LayoutParams(0,-2,1f));val unread=x.optInt("unread",0);if(unread>0)b.addView(pill(unread.toString(),RED),wrap());c.addView(b);c.setOnClickListener{showChatDialog(x.optString("operation_id"),x.optString("booking_id"))};box.addView(c,match().apply{bottomMargin=dp(8)})};showPageDialog("المحادثات مع العملاء",box)}}

    private fun showPaymentsPage(){ lifecycleScope.launch{ val r=withContext(Dispatchers.IO){StaffClient.payments(this@MainActivityPro)}; if(!r.ok){toast(r.error);return@launch}; val arr=r.body.optJSONArray("payments")?:JSONArray(); val box=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL}; if(arr.length()==0)box.addView(empty("لا توجد عمليات دفع.")); for(i in 0 until arr.length()){val x=arr.optJSONObject(i)?:continue;val st=x.optString("status");val c=card(SURFACE,16f);val b=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(11),dp(12),dp(11))};b.addView(txt(x.optString("booking_id"),13f,TEXT,true));b.addView(pill(st,if(st=="TICKET_READY")GREEN else if(st=="PAYMENT_REJECTED")RED else GOLD),wrap().apply{topMargin=dp(5)});b.addView(txt(x.optString("customer_name")+" • "+String.format(Locale.US,"%.2f",x.optDouble("amount",0.0))+" جنيه",12f,MUTED,false));c.addView(b);box.addView(c,match().apply{bottomMargin=dp(7)})};showPageDialog("عمليات الدفع: معلقة وناجحة ومرفوضة",box)}}

    private fun showPageDialog(title:String,content:View){ val sc=ScrollView(this).apply{addView(content)}; AlertDialog.Builder(this).setTitle(title).setView(sc).setPositiveButton("إغلاق",null).show() }

'''
s=s.replace(marker,methods+marker,1)
old='''    private fun operationAction(id:String,approve:Boolean){
        if(id.isBlank())return
        lifecycleScope.launch{
            val r=withContext(Dispatchers.IO){if(approve)StaffClient.approve(this@MainActivityPro,id) else StaffClient.reject(this@MainActivityPro,id,"رفض موظف")}
            toast(if(r.ok)"تم حفظ القرار." else r.error);if(r.ok)refresh()
        }
    }'''
new='''    private fun operationAction(id:String,approve:Boolean){
        if(id.isBlank())return
        if(!approve){reject(id);return}
        lifecycleScope.launch{
            val d=withContext(Dispatchers.IO){StaffClient.operation(this@MainActivityPro,id)}
            if(!d.ok){toast(d.error);return@launch}
            val o=d.body.optJSONObject("operation")?:JSONObject(); val m=o.optJSONArray("matches")?.optJSONObject(0); val tx=o.optJSONArray("android")?.optJSONObject(0)
            val summary="الحجز: "+o.optString("booking_id")+"\\nالعميل: "+o.optString("customer_name")+"\\nالمبلغ المطلوب: "+String.format(Locale.US,"%.2f",o.optDouble("amount",0.0))+" جنيه\\nإشعار الهاتف: "+(tx?.optString("amount")?.ifBlank{"غير موجود"}?:"غير موجود")+"\\nحالة المطابقة: "+(m?.optString("status")?.ifBlank{"غير مطابق"}?:"غير مطابق")
            AlertDialog.Builder(this@MainActivityPro).setTitle("مراجعة وتأكيد الدفع").setMessage(summary).setPositiveButton("تأكيد الدفع"){_,_->lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.approve(this@MainActivityPro,id)};toast(if(r.ok)"تم اعتماد الدفع. ارفع التذكرة لإرسالها للعميل." else r.error);if(r.ok)refresh()}}.setNegativeButton("إلغاء",null).show()
        }
    }'''
if old not in s: raise SystemExit('operation action marker not found')
s=s.replace(old,new,1)
old='''val messages=TextView(this).apply{textSize=13f;setTextColor(Color.parseColor(TEXT));setPadding(dp(8),dp(8),dp(8),dp(8))}
        val scroll=ScrollView(this).apply{addView(messages);layoutParams=LinearLayout.LayoutParams(-1,dp(300))}'''
new='''val messages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8));setBackgroundColor(Color.parseColor("#07111D"))}
        val scroll=ScrollView(this).apply{addView(messages);setBackgroundColor(Color.parseColor("#07111D"));layoutParams=LinearLayout.LayoutParams(-1,dp(320))}'''
if old not in s: raise SystemExit('chat view marker not found')
s=s.replace(old,new,1)
old='''fun refresh(){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.chat(this@MainActivityPro,operationId)};if(r.ok){val arr=r.body.optJSONArray("messages")?:JSONArray();val sb=StringBuilder();for(i in 0 until arr.length()){val z=arr.optJSONObject(i)?:continue;sb.append(if(z.optString("sender_type")=="staff")"أنت" else "العميل").append(": ").append(z.optString("message")).append("\\n\\n")};messages.text=sb.toString().ifBlank{"لا توجد رسائل بعد."};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}}'''
new='''fun refresh(){lifecycleScope.launch{val r=withContext(Dispatchers.IO){StaffClient.chat(this@MainActivityPro,operationId)};if(r.ok){val arr=r.body.optJSONArray("messages")?:JSONArray();messages.removeAllViews();for(i in 0 until arr.length()){val z=arr.optJSONObject(i)?:continue;val staff=z.optString("sender_type")=="staff";val bubble=LinearLayout(this@MainActivityPro).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(9),dp(12),dp(9));setBackgroundColor(Color.parseColor(if(staff)"#173A5C" else "#193E2E"))};bubble.addView(txt(if(staff)"أنت" else "العميل",10f,if(staff)"#B9D7FF" else "#A9F0C9",true));bubble.addView(txt(z.optString("message"),14f,TEXT,false),match().apply{topMargin=dp(3)});messages.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(7);marginStart=if(staff)dp(48) else dp(4);marginEnd=if(staff)dp(4) else dp(48)})};if(arr.length()==0)messages.addView(txt("لا توجد رسائل بعد.",13f,MUTED,false));scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}}'''
if old not in s: raise SystemExit('chat refresh marker not found')
s=s.replace(old,new,1)
marker='    fun deviceId(c:Context)='
api='''    fun operation(c:Context,id:String)=req(c,"GET","/api/mobile/operations/"+id,null,true)
    fun notifications(c:Context)=req(c,"GET","/api/mobile/notifications",null,true)
    fun conversations(c:Context)=req(c,"GET","/api/mobile/conversations",null,true)
    fun payments(c:Context)=req(c,"GET","/api/mobile/payments",null,true)
'''
s=s.replace(marker,api+marker,1)
p.write_text(s,encoding='utf-8')
print('patched',p)
