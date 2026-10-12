package com.superjet.notificationmonitor

import java.util.Locale

data class ParsedNotification(
    val provider: String = "UNKNOWN",
    val transactionType: String = "UNKNOWN",
    val amount: Double? = null,
    val reference: String = "",
    val senderPhone: String = "",
    val recipientAccount: String = "",
    val transactionDate: String = "",
    val transactionTime: String = "",
    val status: String = "UNPARSED",
    val isPaymentNotification: Boolean = false,
    val senderIdentifier: String = ""
)

object NotificationParserEngine {
    private val smsPackages = listOf(
        "com.android.mms", "com.android.messaging", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms.service", "com.truecaller", "truecaller", "messaging", "sms"
    )
    private val nonPaymentPackages = listOf(
        "com.whatsapp", "telegram", "com.facebook.orca",
        "com.instagram.android", "com.google.android.gms", "com.google.android.gm", "com.android.chrome",
        "com.microsoft.emmx", "com.brave.browser"
    )

    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification {
        val provider = identifyProvider(appName, packageName, title)
            ?: return ParsedNotification(status = "NOT_PAYMENT")
        val text = normalizeIdentity(normalizeDigits("$title\n$body"))
        if (!isTransactionNotice(text)) {
            return ParsedNotification(provider = provider, status = "NOT_PAYMENT", isPaymentNotification = false)
        }

        val direction = transactionType(text)
        val amount = extractAmount(text)
        val allPhones = phones(text)
        val explicitSender = contextPhone(text, listOf(
            "من رقم", "من الرقم", "رقم المرسل", "المرسل", "من", "from number", "from phone", "from", "sender number", "sender", "paid by"
        ))
        val explicitRecipient = contextPhone(text, listOf(
            "على رقم محفظتك", "على رقم حسابك", "رقم المستلم", "رقم المستفيد", "إلى رقم", "الى رقم", "لرقم", "إلى", "الى",
            "to number", "to phone", "recipient", "beneficiary", "sent to", "paid to"
        ))

        // Never guess a sender from an unlabeled phone. It could be the wallet owner or recipient.
        // If no explicit sender label exists, preserve the notice as PARTIAL for staff review.
        val sender = explicitSender
        val recipient = explicitRecipient.ifBlank {
            when (direction) {
                "TRANSFER_OUT" -> allPhones.lastOrNull().orEmpty()
                "TRANSFER_IN" -> allPhones.drop(1).firstOrNull().orEmpty()
                else -> ""
            }
        }
        val senderIdentifier = extractSenderIdentifier(text)
        val reference = extractReference(text)
        val dt = extractDate(text)
        val tm = extractTime(text)
        val parsed = amount != null && direction != "UNKNOWN" && (sender.isNotBlank() || senderIdentifier.isNotBlank() || reference.isNotBlank())
        return ParsedNotification(
            provider = provider,
            transactionType = direction,
            amount = amount,
            reference = reference,
            senderPhone = sender,
            recipientAccount = recipient,
            transactionDate = dt,
            transactionTime = tm,
            status = if (parsed) "PARSED" else "PARTIAL",
            isPaymentNotification = true,
            senderIdentifier = senderIdentifier
        )
    }

    /** Identify the source from app/package metadata, never by searching the notification body. */
    private fun identifyProvider(appName: String, packageName: String, title: String): String? {
        val source = normalizeIdentity("$appName $packageName")
        val titleText = normalizeIdentity(title)
        if (nonPaymentPackages.any { packageName.contains(it, ignoreCase = true) || source.contains(it, ignoreCase = true) }) return null
        providerFromIdentity(source)?.let { return it }
        val isSms = smsPackages.any { packageName.contains(it, ignoreCase = true) || source.contains(it, ignoreCase = true) } ||
            source.contains("الرسائل") || source.contains("messages")
        if (isSms) return providerFromIdentity(titleText)
        return null
    }

    private fun providerFromIdentity(value: String): String? = when {
        value.containsAny("vodafone cash", "vf-cash", "vf cash", "فودافون كاش", "كاش فودافون", "vodafone") -> "VODAFONE_CASH"
        value.containsAny("orange cash", "orangecash", "اورنج كاش", "أورنج كاش", "orange") -> "ORANGE_CASH"
        value.containsAny("etisalat cash", "etisalat money", "اتصالات كاش", "اتصالات موني", "etisalat", "اتصالات مصر") -> "ETISALAT_CASH"
        value.containsAny("we pay", "wepay", "وي باي", "وى باى") -> "WE_PAY"
        value.containsAny("instapay", "انستا باي", "إنستا باي", "انستاباي", "انستا باي مصر") -> "INSTAPAY"
        else -> null
    }

    /** Promotions, balance offers, cashback and chat messages are ignored unless wording describes a specific transaction. */
    private fun isTransactionNotice(s: String): Boolean {
        val q = normalizeIdentity(s)
        val incoming = q.containsAny(
            "تم استلام", "لقد استلمت", "استلمت مبلغ", "تحويل وارد", "حوالة واردة", "وصلك تحويل",
            "ايداع وارد", "إيداع وارد", "تم الايداع", "تم الإيداع", "received from", "you have received",
            "you received", "credited to", "deposit received", "money received", "تم تحويل مبلغ لك",
            "حول لك مبلغ", "حوّل لك مبلغ", "تم تحويل مبلغ الى محفظتك", "تم تحويل مبلغ إلى محفظتك"
        )
        val outgoing = Regex("(?:تم\\s*تحويل)(?:\\s|[:：])", RegexOption.IGNORE_CASE).containsMatchIn(q) ||
            q.containsAny("تم دفع مبلغ", "تم خصم مبلغ", "تم خصم", "تم ارسال مبلغ", "تم إرسال مبلغ", "تم ارسال ", "تم إرسال ",
                "أرسلت مبلغ", "ارسلت مبلغ", "قمت بتحويل", "قمت ب تحويل", "تمت عملية التحويل", "تمت عملية تحويل",
                "you sent ", "sent to ", "payment of ", "paid ", "debited", "successful transfer", "transfer successful")
        return incoming || outgoing
    }

    private fun extractAmount(s: String): Double? {
        val q = normalizeDigits(s)
        val transactionAmount = Regex(
            "(?:تم\\s*استلام(?:\\s+مبلغ)?|استلمت(?:\\s+مبلغ)?|لقد\\s+استلمت|تم\\s*تحويل(?:\\s+مبلغ)?|قمت\\s*بتحويل(?:\\s+مبلغ)?|تمت?\\s+(?:عملية\\s+)?التحويل|تمت?\\s+(?:عملية\\s+)?تحويل|نجحت?\\s+عملية\\s+التحويل|تم\\s*دفع(?:\\s+مبلغ)?|تم\\s*خصم(?:\\s+مبلغ)?|تم\\s*(?:إرسال|ارسال)(?:\\s+مبلغ)?|received|you\\s+have\\s+received|you\\s+received|credited|you\\s+sent|paid|amount\\s+transferred|قيمة\\s*التحويل|مبلغ\\s*التحويل)[^0-9]{0,12}([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            RegexOption.IGNORE_CASE
        )
        for (m in transactionAmount.findAll(q)) parseMoney(m.groupValues[1])?.let { return it }

        val currency = Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(?:جنيه(?:\\s*مصري)?|ج\\.?\\s*م\\.?|EGP|L\\.?E\\.?|LE)", RegexOption.IGNORE_CASE)
        val cutoffWords = listOf("رصيدك الحالي", "الرصيد المتاح", "مصاريف الخدمة", "رسوم الخدمة", "balance", "service fee", "transaction fee")
        var cutoff = q.length
        val cues = listOf("تم استلام", "لقد استلمت", "استلمت مبلغ", "تم تحويل ", "تم تحويل مبلغ", "قمت بتحويل", "تمت عملية التحويل", "تم دفع مبلغ", "تم خصم", "received", "you have received", "credited", "sent to", "paid ")
        val start = cues.map { q.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        cutoffWords.forEach { word ->
            val i = q.indexOf(word, startIndex = start, ignoreCase = true)
            if (i >= 0 && i < cutoff) cutoff = i
        }
        val boundedStart = start.coerceAtMost(q.length)
        val beforeBalance = q.substring(boundedStart, cutoff.coerceAtLeast(boundedStart))
        for (m in currency.findAll(beforeBalance)) parseMoney(m.groupValues[1])?.let { return it }
        return null
    }

    private fun parseMoney(raw: String): Double? {
        val cleaned = normalizeDigits(raw).replace(",", "").trim()
        if (cleaned.count { it.isDigit() } !in 1..8) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return value.takeIf { it > 0.0 && it < 1_000_000.0 }
    }

    private fun transactionType(s: String): String {
        val q = normalizeIdentity(s)
        if (q.containsAny(
                "تم استلام", "لقد استلمت", "استلمت مبلغ", "تحويل وارد", "حوالة واردة", "وصلك تحويل",
                "ايداع وارد", "إيداع وارد", "تم الإيداع", "تم الايداع", "received from", "you have received",
                "you received", "credited to", "deposit received", "money received", "تم تحويل مبلغ لك",
                "حول لك مبلغ", "حوّل لك مبلغ", "تم تحويل مبلغ الى محفظتك", "تم تحويل مبلغ إلى محفظتك"
            )) return "TRANSFER_IN"
        if (q.containsAny("تم دفع مبلغ", "تم خصم", "تم ارسال مبلغ", "تم إرسال مبلغ", "تم ارسال ", "تم إرسال ", "أرسلت مبلغ", "ارسلت مبلغ", "قمت بتحويل", "تمت عملية التحويل", "تمت عملية تحويل", "you sent ", "sent to ", "paid ", "debited", "successful transfer", "transfer successful") ||
            (Regex("(?:تم\\s*تحويل)(?:\\s|[:：])", RegexOption.IGNORE_CASE).containsMatchIn(q) && q.containsAny("لرقم", "إلى رقم", "الى رقم", "الي رقم", "to number", "to phone", "للمستفيد", "لحساب", "الي حساب"))) return "TRANSFER_OUT"
        return "UNKNOWN"
    }

    private fun extractReference(s: String): String {
        val q = normalizeIdentity(normalizeDigits(s))
        val explicit = Regex("(?:رقم\\s*(?:العملية|التحويل)|الرقم\\s*المرجعي|رقم\\s*المرجع|مرجع|reference(?:\\s*(?:id|number))?|transaction\\s*(?:id|number)|ref)\\s*[:：#-]?\\s*([0-9A-Z-]{4,40})", RegexOption.IGNORE_CASE)
            .find(q)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (explicit.isBlank()) return ""
        val digits = explicit.filter { it.isDigit() }
        return if (digits.length in 10..11 && digits.startsWith("01")) "" else explicit
    }

    private fun phones(s: String): List<String> = Regex("(?<![0-9])(?:(?:\\+?20|0020)\\s*)?01[0125][0-9]{8}(?![0-9])")
        .findAll(normalizeIdentity(normalizeDigits(s))).map { normalizePhone(it.value) }.distinct().toList()

    private fun contextPhone(s: String, labels: List<String>): String {
        val q = normalizeIdentity(normalizeDigits(s))
        val labelsPattern = labels.joinToString("|") { Regex.escape(normalizeIdentity(it)) }
        val regex = Regex("(?:$labelsPattern)\\s*[:：#-]?\\s*(?:(?:رقم|الرقم|number|phone)\\s*)?((?:(?:\\+?20|0020)\\s*)?01[0125][0-9]{8})", RegexOption.IGNORE_CASE)
        return regex.find(q)?.groupValues?.getOrNull(1)?.let(::normalizePhone).orEmpty()
    }

    private fun extractSenderIdentifier(s: String): String {
        val q = normalizeIdentity(normalizeDigits(s))
        val pattern = Regex("(?:من|from|sender|المرسل)\\s*(?:(?:رقم|الرقم|number|account|حساب)\\s*)?[:：-]?\\s*([A-Za-z][A-Za-z0-9._@-]{2,63})", RegexOption.IGNORE_CASE)
        return pattern.find(q)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun extractDate(s: String): String = Regex("(?<!\\d)(?:[0-9]{4}[-/.][0-9]{1,2}[-/.][0-9]{1,2}|[0-9]{1,2}[-/.][0-9]{1,2}[-/.][0-9]{2,4})(?!\\d)")
        .find(normalizeIdentity(normalizeDigits(s)))?.value.orEmpty()

    private fun extractTime(s: String): String = Regex("(?<!\\d)([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)(?!\\d)")
        .find(normalizeIdentity(normalizeDigits(s)))?.groupValues?.getOrNull(1).orEmpty()

    private fun normalizePhone(value: String): String {
        val x = value.replace(" ", "").replace("-", "")
        return when {
            x.startsWith("0020") -> "0" + x.drop(4)
            x.startsWith("+20") -> "0" + x.drop(3)
            x.startsWith("20") && x.length == 12 -> "0" + x.drop(2)
            else -> x
        }
    }

    private fun normalizeIdentity(value: String): String = normalizeDigits(value)
        .replace("\u200e", "").replace("\u200f", "").replace("\u202a", "").replace("\u202c", "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
        .lowercase(Locale.ROOT)

    private fun normalizeDigits(value: String): String {
        val ar = "٠١٢٣٤٥٦٧٨٩"
        val fa = "۰۱۲۳۴۵۶۷۸۹"
        return buildString {
            for (c in value) when {
                c in ar -> append(('0'.code + ar.indexOf(c)).toChar())
                c in fa -> append(('0'.code + fa.indexOf(c)).toChar())
                c == '٫' -> append('.')
                c == '٬' -> append(',')
                else -> append(c)
            }
        }
    }

    private fun String.containsAny(vararg parts: String): Boolean = parts.any { contains(it, ignoreCase = true) }
}