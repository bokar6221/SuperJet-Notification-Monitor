package com.superjet.notificationmonitor

import java.util.Locale

data class ParsedNotification(
    val provider:String="UNKNOWN", val transactionType:String="UNKNOWN", val amount:Double?=null,
    val reference:String="", val senderPhone:String="", val recipientAccount:String="",
    val transactionDate:String="", val transactionTime:String="", val status:String="UNPARSED",
    val isPaymentNotification:Boolean=false
)

interface NotificationParser{
    fun supports(appName:String,packageName:String,title:String,body:String):Boolean
    fun parse(appName:String,packageName:String,title:String,body:String):ParsedNotification
}

object NotificationParserEngine{
    private val parsers=listOf(VodafoneCashParser,OrangeCashParser,EtisalatCashParser,WePayParser,InstaPayParser)
    fun parse(appName:String,packageName:String,title:String,body:String):ParsedNotification{
        if(appName.containsAny("telegram")||packageName.contains("telegram",true))return ParsedNotification(status="NOT_PAYMENT")
        val p=parsers.firstOrNull{it.supports(appName,packageName,title,body)}
        return p?.parse(appName,packageName,title,body)?:GenericPaymentParser.parse(appName,packageName,title,body)
    }
}
private object GenericPaymentParser{
    fun parse(app:String,pkg:String,title:String,body:String):ParsedNotification{
        val s=normalizeDigits("$title\n$body")
        val amount=amount(s);val phones=phones(s);val ref=reference(s);val type=type(s)
        val looksPayment=amount!=null||ref.isNotBlank()||type!="UNKNOWN"||s.containsAny("تحويل","تم استلام","محفظة","رصيد")
        return ParsedNotification(provider=app.ifBlank{"UNKNOWN"},transactionType=if(type=="UNKNOWN"&&amount!=null)"TRANSFER_IN" else type,
            amount=amount,reference=ref,senderPhone=phones.firstOrNull().orEmpty(),recipientAccount=phones.getOrNull(1).orEmpty(),
            transactionDate=date(s),transactionTime=time(s),status=if(looksPayment)"PARSED" else "NOT_PAYMENT",isPaymentNotification=looksPayment)
    }
    private fun amount(s:String):Double?{
        val ps=listOf(
            Regex("""(?i)(?:مبلغ|المبلغ|بقيمة|بمبلغ|amount|value)[^\d٠-٩]{0,20}([\d٠-٩][\d٠-٩,]*(?:[.٫][\d٠-٩]{1,2})?)"""),
            Regex("""([\d٠-٩][\d٠-٩,]*(?:[.٫][\d٠-٩]{1,2})?)\s*(?:جنيه|EGP|ج\.م)""")
        )
        for(p in ps){val raw=p.find(s)?.groupValues?.getOrNull(1)?.replace(",","")?.replace('٫','.')?:continue;raw.toDoubleOrNull()?.let{if(it>0&&it<1_000_000)return it}}
        return null
    }
    private fun reference(s:String)=Regex("""(?i)(?:رقم\s*العملية|رقم\s*التحويل|المرجع|reference|transaction\s*id|ref)[^\d٠-٩]{0,20}([\d٠-٩]{5,30})""").find(s)?.groupValues?.get(1)?.let(::normalizeDigits).orEmpty()
    private fun phones(s:String)=Regex("""(?<![\d])(?:\+?20\s*)?01[0-25][\d]{8}(?![\d])""").findAll(s).map{normalizePhone(it.value)}.distinct().toList()
    private fun type(s:String):String=when{
        s.containsAny("تم استلام","استلمت","تحويل وارد","إيداع","received","credited","credit")->"TRANSFER_IN"
        s.containsAny("تم تحويل","تحويل صادر","sent","debited","debit","outbound")->"TRANSFER_OUT"
        else->"UNKNOWN"}
    private fun date(s:String)=Regex("""([\d]{1,2}[-/][\d]{1,2}[-/][\d]{2,4})""").find(s)?.groupValues?.get(1).orEmpty()
    private fun time(s:String)=Regex("""([\d]{1,2}:[\d]{2}(?::[\d]{2})?)""").find(s)?.groupValues?.get(1).orEmpty()
    fun normalizeDigits(v:String):String{
        val a="٠١٢٣٤٥٦٧٨٩";val e="۰۱۲۳۴۵۶۷۸۹";return buildString{for(c in v)when{c in a->append(('0'.code+a.indexOf(c)).toChar());c in e->append(('0'.code+e.indexOf(c)).toChar());else->append(c)}}
    }
    private fun normalizePhone(v:String):String{val x=v.replace(" ","").replace("-","");return if(x.startsWith("+20"))"0"+x.drop(3) else if(x.startsWith("20")&&x.length==12)"0"+x.drop(2) else x}
}
private object VodafoneCashParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("vodafone","vf-cash","فودافون")
    override fun parse(a:String,p:String,t:String,b:String):ParsedNotification{
        val g=GenericPaymentParser.parse("VF-Cash",p,t,b)
        return g.copy(provider="VODAFONE_CASH",transactionType=if(g.transactionType=="UNKNOWN"&&g.amount!=null)"TRANSFER_IN" else g.transactionType,
            status=if(g.amount!=null)"PARSED" else g.status,isPaymentNotification=g.amount!=null||g.reference.isNotBlank())
    }
}
private object OrangeCashParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("orange","أورنج","اورنج")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("Orange Cash",p,t,b).copy(provider="ORANGE_CASH")
}
private object EtisalatCashParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("etisalat","اتصالات")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("Etisalat Cash",p,t,b).copy(provider="ETISALAT_CASH")
}
private object WePayParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("we pay","wepay","وي باي","وى باى")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("WE Pay",p,t,b).copy(provider="WE_PAY")
}
private object InstaPayParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("instapay","انستا باي","إنستا باي")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("InstaPay",p,t,b).copy(provider="INSTAPAY")
}
private fun String.containsAny(vararg x:String)=x.any{lowercase(Locale.ROOT).contains(it.lowercase(Locale.ROOT))}