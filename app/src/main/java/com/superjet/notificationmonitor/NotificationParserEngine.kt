package com.superjet.notificationmonitor

import java.util.Locale

data class ParsedNotification(
    val provider:String="UNKNOWN",val transactionType:String="UNKNOWN",val amount:Double?=null,
    val reference:String="",val senderPhone:String="",val recipientAccount:String="",
    val transactionDate:String="",val transactionTime:String="",val status:String="UNPARSED",
    val isPaymentNotification:Boolean=false
)

interface NotificationParser{
    fun supports(appName:String,packageName:String,title:String,body:String):Boolean
    fun parse(appName:String,packageName:String,title:String,body:String):ParsedNotification
}

object NotificationParserEngine{
    private val parsers=listOf(VodafoneCashParser,OrangeCashParser,EtisalatCashParser,WePayParser,InstaPayParser)
    fun parse(appName:String,packageName:String,title:String,body:String):ParsedNotification{
        if(packageName.contains("telegram",true))return ParsedNotification(status="NOT_PAYMENT")
        val p=parsers.firstOrNull{it.supports(appName,packageName,title,body)}
        return p?.parse(appName,packageName,title,body)?:GenericPaymentParser.parse(appName,packageName,title,body)
    }
}

private object GenericPaymentParser{
    fun parse(app:String,pkg:String,title:String,body:String):ParsedNotification{
        val s=normalizeDigits("$title\n$body")
        val amount=amount(s)
        val phones=phones(s)
        val ref=reference(s)
        val type=type(s)
        val sender=contextPhone(s,listOf("من","من رقم","from","sender","مرسل","المرسل"))?:phones.firstOrNull().orEmpty()
        val recipient=contextPhone(s,listOf("إلى","الى","إلى رقم","الى رقم","لصالح","إلى حساب","to","recipient","receiver","المستلم","استلم على","استلام على"))?:phones.drop(1).firstOrNull().orEmpty()
        val looksPayment=amount!=null||ref.isNotBlank()||type!="UNKNOWN"||s.containsAny("تحويل","تم استلام","تم تحويل","محفظة","رصيد","إيداع","عملية")
        return ParsedNotification(provider=app.ifBlank{"UNKNOWN"},transactionType=if(type=="UNKNOWN"&&amount!=null)"TRANSFER_IN" else type,
            amount=amount,reference=ref,senderPhone=sender,recipientAccount=recipient,
            transactionDate=date(s),transactionTime=time(s),status=if(looksPayment)"PARSED" else "NOT_PAYMENT",
            isPaymentNotification=looksPayment)
    }
    private fun amount(s:String):Double?{
        val patterns=listOf(
            Regex("""(?is)(?:المبلغ|مبلغ|بقيمة|بمبلغ|قيمة التحويل|تم استلام|استلام|amount|value|credited|received)[^0-9٠-٩]{0,60}([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)"""),
            Regex("""([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)s*(?:جنيه|ج.م|EGP|LE)"""),
            Regex("""(?is)([0-9٠-٩]{1,8}(?:[.٫][0-9٠-٩]{1,2})?)s*(?:جنيه|ج.م|EGP|LE)""")
        )
        for(p in patterns){
            val raw=p.find(s)?.groupValues?.getOrNull(1)?.replace(",","")?.replace('٫','.')?:continue
            raw.toDoubleOrNull()?.let{if(it>0&&it<1_000_000)return it}
        }
        return null
    }
    private fun reference(s:String)=Regex("""(?is)(?:رقمs*العملية|رقمs*التحويل|المرجع|رقمs*المرجع|transactions*(?:id|number)|reference|ref|مرجع)[^0-9٠-٩A-Z]{0,45}([0-9٠-٩A-Z-]{4,60})""").find(s)?.groupValues?.getOrNull(1)?.let(::normalizeDigits)?.trim().orEmpty()
    private fun phones(s:String)=Regex("""(?<![0-9])(?:+?20s*)?01[0-25][0-9٠-٩]{8}(?![0-9])""").findAll(s).map{normalizePhone(it.value)}.distinct().toList()
    private fun type(s:String)=when{
        s.containsAny("تم استلام","استلمت","تحويل وارد","إيداع","وصل","received","credited","credit")->"TRANSFER_IN"
        s.containsAny("تم تحويل","تحويل صادر","sent","debited","debit","outbound")->"TRANSFER_OUT"
        else->"UNKNOWN"
    }
    private fun contextPhone(s:String,labels:List<String>):String{
        val label=labels.joinToString("|"){Regex.escape(it)}
        val r=Regex("""(?is)(?:$label)[^0-9٠-٩]{0,80}((?:+?20s*)?01[0-25][0-9٠-٩]{8})""")
        return r.find(s)?.groupValues?.getOrNull(1)?.let(::normalizePhone).orEmpty()
    }
    private fun date(s:String)=Regex("""([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4})""").find(s)?.groupValues?.getOrNull(1).orEmpty()
    private fun time(s:String)=Regex("""([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)""").find(s)?.groupValues?.getOrNull(1).orEmpty()
    fun normalizeDigits(v:String):String{
        val ar="٠١٢٣٤٥٦٧٨٩";val fa="۰۱۲۳۴۵۶۷۸۹"
        return buildString{for(c in v)when{c in ar->append(('0'.code+ar.indexOf(c)).toChar());c in fa->append(('0'.code+fa.indexOf(c)).toChar());else->append(c)}}
    }
    private fun normalizePhone(v:String):String{
        val x=v.replace(" ","").replace("-","")
        return if(x.startsWith("+20"))"0"+x.drop(3) else if(x.startsWith("20")&&x.length==12)"0"+x.drop(2) else x
    }
}

private object VodafoneCashParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("vodafone","vodafone cash","vf-cash","فودافون")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("VF-Cash",p,t,b).copy(provider="VODAFONE_CASH")
}
private object OrangeCashParser:NotificationParser{
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("orange","orange cash","orangecash","أورنج","اورنج","أورانج")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("Orange Cash",p,t,b).copy(
        provider="ORANGE_CASH",
        status="PARSED",
        isPaymentNotification=true
    )
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
    override fun supports(a:String,p:String,t:String,b:String)="$a $p $t $b".containsAny("instapay","انستا باي","إنستا باي","انستا")
    override fun parse(a:String,p:String,t:String,b:String)=GenericPaymentParser.parse("InstaPay",p,t,b).copy(provider="INSTAPAY")
}
private fun String.containsAny(vararg x:String)=x.any{lowercase(Locale.ROOT).contains(it.lowercase(Locale.ROOT))}
