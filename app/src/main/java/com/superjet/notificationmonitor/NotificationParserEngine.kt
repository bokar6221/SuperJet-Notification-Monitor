package com.superjet.notificationmonitor

import java.util.Locale

data class ParsedNotification(
    val provider: String = "UNKNOWN",
    val transactionType: String = "UNKNOWN",
    val amount: Double? = null,
    val reference: String = "",
    val senderPhone: String = "",
    val recipientAccount: String = "",
    val status: String = "UNPARSED",
    val isPaymentNotification: Boolean = false
)

interface NotificationParser {
    fun supports(appName: String, packageName: String, title: String, body: String): Boolean
    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification
}

object NotificationParserEngine {
    private val parsers: List<NotificationParser> = listOf(
        VodafoneCashParser,
        OrangeCashParser,
        EtisalatCashParser,
        WePayParser,
        InstaPayParser
    )

    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification {
        val paymentLike = isPaymentLike(appName, packageName, title, body)
        val parser = parsers.firstOrNull { it.supports(appName, packageName, title, body) }

        return when {
            parser != null -> parser.parse(appName, packageName, title, body)
            paymentLike -> GenericPaymentParser.parse(appName, packageName, title, body)
            else -> ParsedNotification(status = "NOT_PAYMENT", isPaymentNotification = false)
        }
    }

    private fun isPaymentLike(appName: String, packageName: String, title: String, body: String): Boolean {
        val s = listOf(appName, packageName, title, body)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

        val keywords = listOf(
            "transfer", "transferred", "received", "payment", "cash",
            "instapay", "vodafone", "orange", "etisalat", "we pay",
            "تحويل", "تم استلام", "استلمت", "محفظة", "مبلغ",
            "عملية", "دفع", "إيداع", "خصم"
        )
        return keywords.any { s.contains(it) }
    }
}

private object GenericPaymentParser {
    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification {
        val normalized = normalizeDigits("$title\n$body")
        val amount = extractAmount(normalized)
        val reference = extractReference(normalized)
        val phone = extractPhone(normalized)
        val type = detectType(normalized)

        val hasUseful = amount != null || reference.isNotBlank() || phone.isNotBlank()
        return ParsedNotification(
            provider = appName.ifBlank { "UNKNOWN" },
            transactionType = type,
            amount = amount,
            reference = reference,
            senderPhone = phone,
            status = if (hasUseful) "PARSED" else "UNPARSED",
            isPaymentNotification = true
        )
    }

    private fun extractAmount(s: String): Double? {
        val patterns = listOf(
            """(?i)(?:amount|value|بقيمة|بمبلغ|المبلغ|مبلغ|رصيد)[^0-9٠-٩]{0,15}([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)""",
            """([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)\s*(?:EGP|جنيه|ج\.م|جنية)"""
        )
        for (p in patterns) {
            val m = Regex(p).find(s) ?: continue
            val raw = m.groupValues[1].replace(",", "").replace("٫", ".")
            raw.toDoubleOrNull()?.let {
                if (it in 0.01..1_000_000.0) return it
            }
        }
        return null
    }

    private fun extractReference(s: String): String {
        val pattern = """(?i)(?:reference|ref|transaction\s*id|reference\s*number|رقم\s*المرجع|مرجع|رقم\s*العملية)[^0-9٠-٩]{0,20}([0-9٠-٩]{6,30})"""
        Regex(pattern).find(s)?.groupValues?.getOrNull(1)?.let {
            return normalizeDigits(it)
        }
        return ""
    }

    private fun extractPhone(s: String): String {
        val pattern = """(?<![0-9٠-٩])(?:01|٠١)[0-9٠-٩]{9}(?![0-9٠-٩])"""
        return Regex(pattern)
            .findAll(s)
            .map { normalizeDigits(it.value) }
            .firstOrNull()
            ?: ""
    }

    private fun detectType(s: String): String {
        return when {
            Regex("""(?i)\b(received|credit|credited|inbound|تم\s+استلام|إيداع|تحويل\s+وارد)\b""")
                .containsMatchIn(s) -> "TRANSFER_IN"
            Regex("""(?i)\b(sent|debit|debited|outbound|خصم|تحويل\s+صادر)\b""")
                .containsMatchIn(s) -> "TRANSFER_OUT"
            else -> "UNKNOWN"
        }
    }

    internal fun normalizeDigits(value: String): String {
        val arabic = "٠١٢٣٤٥٦٧٨٩"
        val eastern = "۰۱۲۳۴۵۶۷۸۹"
        return buildString(value.length) {
            for (c in value) {
                when {
                    c in arabic -> append(('0'.code + arabic.indexOf(c)).toChar())
                    c in eastern -> append(('0'.code + eastern.indexOf(c)).toChar())
                    else -> append(c)
                }
            }
        }
    }
}

private object VodafoneCashParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("vodafone", "فودافون")

    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse(appName.ifBlank { "Vodafone Cash" }, packageName, title, body)
            .copy(provider = "VODAFONE_CASH")
}

private object OrangeCashParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("orange", "أورنج", "اورنج")

    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse(appName.ifBlank { "Orange Cash" }, packageName, title, body)
            .copy(provider = "ORANGE_CASH")
}

private object EtisalatCashParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("etisalat", "اتصالات")

    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse(appName.ifBlank { "Etisalat Cash" }, packageName, title, body)
            .copy(provider = "ETISALAT_CASH")
}

private object WePayParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("we pay", "wepay", "وي باي", "وى باى")

    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse(appName.ifBlank { "WE Pay" }, packageName, title, body)
            .copy(provider = "WE_PAY")
}

private object InstaPayParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("instapay", "انستا باي", "إنستا باي")

    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse(appName.ifBlank { "InstaPay" }, packageName, title, body)
            .copy(provider = "INSTAPAY")
}

private fun String.containsAny(vararg values: String): Boolean {
    val source = lowercase(Locale.ROOT)
    return values.any { source.contains(it.lowercase(Locale.ROOT)) }
}
