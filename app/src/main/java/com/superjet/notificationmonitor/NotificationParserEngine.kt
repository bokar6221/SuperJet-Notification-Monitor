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
    val isPaymentNotification: Boolean = false
)

interface NotificationParser {
    fun supports(appName: String, packageName: String, title: String, body: String): Boolean
    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification
}

object NotificationParserEngine {
    private val parsers: List<NotificationParser> = listOf(
        VodafoneCashParser, OrangeCashParser, EtisalatCashParser, WePayParser, InstaPayParser
    )

    private val ignoredPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.openai.chatgpt",
        "com.elmujib.direct"
    )

    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification {
        if (packageName in ignoredPackages) {
            return ParsedNotification(status = "NOT_PAYMENT", isPaymentNotification = false)
        }
        val parser = parsers.firstOrNull { it.supports(appName, packageName, title, body) }
        return when {
            parser != null -> parser.parse(appName, packageName, title, body)
            isPaymentLike(appName, packageName, title, body) -> GenericPaymentParser.parse(appName, packageName, title, body)
            else -> ParsedNotification(status = "NOT_PAYMENT", isPaymentNotification = false)
        }
    }

    private fun isPaymentLike(appName: String, packageName: String, title: String, body: String): Boolean {
        val s = listOf(appName, packageName, title, body).joinToString(" ").lowercase(Locale.ROOT)
        val keywords = listOf(
            "transfer", "transferred", "received", "payment", "cash", "money transfer",
            "instapay", "vodafone", "vf-cash", "orange", "etisalat", "we pay",
            "تحويل", "تم استلام", "استلمت", "محفظة", "عملية", "إيداع", "خصم"
        )
        return keywords.any { s.contains(it) } &&
            GenericPaymentParser.looksLikeStructuredPayment(s)
    }
}

private object GenericPaymentParser {
    fun parse(appName: String, packageName: String, title: String, body: String): ParsedNotification {
        val normalized = normalizeDigits("$title\n$body")
        val amount = extractAmount(normalized)
        val type = detectType(normalized)
        val reference = extractReference(normalized)
        val (senderPhone, recipientPhone) = extractLabeledPhones(normalized)
        val fallbackPhone = extractPhone(normalized)
        val resolvedRecipient = if (recipientPhone.isNotBlank()) {
            recipientPhone
        } else if (type == "TRANSFER_OUT" && containsAny(normalized, "إلى", "الى", "to", "recipient", "المستلم", "لـ")) {
            fallbackPhone
        } else ""
        val resolvedSender = if (senderPhone.isNotBlank()) {
            senderPhone
        } else if (type == "TRANSFER_IN" && containsAny(normalized, "من", "from", "sender", "المرسل")) {
            fallbackPhone
        } else ""
        val status = if (amount != null && (reference.isNotBlank() || type != "UNKNOWN")) "PARSED" else "UNPARSED"
        val paymentLike = amount != null && (reference.isNotBlank() || type != "UNKNOWN")

        return ParsedNotification(
            provider = appName.ifBlank { "UNKNOWN" },
            transactionType = type,
            amount = amount,
            reference = reference,
            senderPhone = resolvedSender,
            recipientAccount = resolvedRecipient,
            transactionDate = date,
            transactionTime = time,
            status = status,
            isPaymentNotification = paymentLike
        )
    }

    private fun extractAmount(s: String): Double? {
        val patterns = listOf(
            """(?i)(?:amount|value|بقيمة|بمبلغ|المبلغ|مبلغ|رصيد)[^0-9٠-٩]{0,20}([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)""",
            """([0-9٠-٩][0-9٠-٩,]*(?:[.٫][0-9٠-٩]{1,2})?)\s*(?:EGP|جنيه|جنية|ج\.م)"""
        )
        for (p in patterns) {
            Regex(p).find(s)?.groupValues?.getOrNull(1)?.let { raw ->
                raw.replace(",", "").replace("٫", ".").toDoubleOrNull()?.let { if (it in 0.01..1_000_000.0) return it }
            }
        }
        return null
    }

    private fun extractReference(s: String): String {
        val p = """(?i)(?:reference|ref|transaction\s*id|reference\s*number|رقم\s*المرجع|مرجع|رقم\s*العملية)[^0-9٠-٩]{0,20}([0-9٠-٩]{6,30})"""
        return Regex(p).find(s)?.groupValues?.getOrNull(1)?.let(::normalizeDigits).orEmpty()
    }

    private fun extractPhone(s: String): String {
        val p = """(?<![0-9٠-٩])(?:01|٠١)[0-9٠-٩]{9}(?![0-9٠-٩])"""
        return Regex(p).findAll(s).map { normalizeDigits(it.value) }.firstOrNull().orEmpty()
    }

    private fun extractLabeledPhones(s: String): Pair<String, String> {
        val phone = """(?:\+?20\s*)?(?:01|٠١)[0-9٠-٩]{9}"""
        val senderPatterns = listOf(
            Regex("""(?i)(?:رقم\s*المرسل|المرسل|من|sender|from)[^0-9٠-٩]{0,20}($phone)"""),
            Regex("""(?i)($phone)[^0-9٠-٩]{0,20}(?:المرسل|sender)""")
        )
        val recipientPatterns = listOf(
            Regex("""(?i)(?:رقم\s*المستلم|المستلم|إلى|الى|recipient|to)[^0-9٠-٩]{0,20}($phone)"""),
            Regex("""(?i)($phone)[^0-9٠-٩]{0,20}(?:المستلم|recipient)""")
        )
        val sender = senderPatterns.asSequence().mapNotNull { r -> r.find(s)?.groupValues?.getOrNull(1) }
            .map(::normalizeDigits).firstOrNull().orEmpty()
        val recipient = recipientPatterns.asSequence().mapNotNull { r -> r.find(s)?.groupValues?.getOrNull(1) }
            .map(::normalizeDigits).firstOrNull().orEmpty()
        return sender to recipient
    }

    internal fun looksLikeStructuredPayment(s: String): Boolean {
        val typeWords = listOf(
            "تم تحويل", "تحويل صادر", "تحويل وارد", "تم استلام", "استلمت", "إيداع", "خصم",
            "transferred", "transfer", "received", "credited", "debited", "credit", "debit"
        )
        val referenceLike = listOf(
            "reference", "ref", "transaction", "transaction id", "رقم العملية", "رقم التحويل", "المرجع"
        )
        return typeWords.any { s.contains(it, ignoreCase = true) } ||
            referenceLike.any { s.contains(it, ignoreCase = true) }
    }

    private fun extractTransactionDateTime(s: String): Pair<String, String> {
        val patterns = listOf(
            """(?i)(?:تاريخ\s*العملية|transaction\s*date|date)[^0-9٠-٩]{0,10}([0-9٠-٩]{1,2}[-/][0-9٠-٩]{1,2}[-/][0-9٠-٩]{2,4})\s+([0-9٠-٩]{1,2}:[0-9٠-٩]{2}(?::[0-9٠-٩]{2})?)""",
            """(?i)([0-9٠-٩]{1,2}[-/][0-9٠-٩]{1,2}[-/][0-9٠-٩]{2,4})\s+([0-9٠-٩]{1,2}:[0-9٠-٩]{2}(?::[0-9٠-٩]{2})?)"""
        )
        for (p in patterns) {
            val m = Regex(p).find(s) ?: continue
            return normalizeDigits(m.groupValues[1]) to normalizeDigits(m.groupValues[2])
        }
        return "" to ""
    }

    private fun detectType(s: String): String {
        val incoming = listOf("تم استلام", "استلمت", "تحويل وارد", "إيداع", "received", "credited", "credit", "inbound")
        val outgoing = listOf("تم تحويل", "تحويل صادر", "sent", "debited", "debit", "outbound")
        val incomingHit = incoming.any { s.contains(it, ignoreCase = true) }
        val outgoingHit = outgoing.any { s.contains(it, ignoreCase = true) }
        return when {
            outgoingHit -> "TRANSFER_OUT"
            incomingHit -> "TRANSFER_IN"
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
        "$appName $packageName $title $body".containsAny("vodafone", "vf-cash", "فودافون")
    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse("VF-Cash", packageName, title, body).copy(provider = "VODAFONE_CASH")
}
private object OrangeCashParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("orange", "أورنج", "اورنج")
    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse("Orange Cash", packageName, title, body).copy(provider = "ORANGE_CASH")
}
private object EtisalatCashParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("etisalat", "اتصالات")
    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse("Etisalat Cash", packageName, title, body).copy(provider = "ETISALAT_CASH")
}
private object WePayParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("we pay", "wepay", "وي باي", "وى باى")
    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse("WE Pay", packageName, title, body).copy(provider = "WE_PAY")
}
private object InstaPayParser : NotificationParser {
    override fun supports(appName: String, packageName: String, title: String, body: String) =
        "$appName $packageName $title $body".containsAny("instapay", "انستا باي", "إنستا باي")
    override fun parse(appName: String, packageName: String, title: String, body: String) =
        GenericPaymentParser.parse("InstaPay", packageName, title, body).copy(provider = "INSTAPAY")
}
private fun String.containsAny(vararg values: String): Boolean {
    val source = lowercase(Locale.ROOT)
    return values.any { source.contains(it.lowercase(Locale.ROOT)) }
}
