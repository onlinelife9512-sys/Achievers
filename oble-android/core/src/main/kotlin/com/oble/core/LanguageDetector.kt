package com.oble.core

/** Languages OBLE reasons about. Mixed codes are common in real conversations. */
enum class SpokenLanguage(val code: String, val label: String) {
    GUJARATI("gu", "Gujarati"),
    HINDI("hi", "Hindi"),
    ENGLISH("en", "English"),
    HINGLISH("hi-en", "Hinglish"),
    GUJARATI_ENGLISH("gu-en", "Gujarati + English"),
    MIXED("mixed", "Mixed"),
    UNKNOWN("und", "Unknown");

    companion object {
        fun fromCode(code: String?): SpokenLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Lightweight, offline script + vocabulary based language detector.
 *
 * Speech engines return text in the script they recognised, so script ranges are a
 * strong signal: Gujarati (U+0A80–U+0AFF), Devanagari (U+0900–U+097F), Latin.
 * Romanised Hindi ("Hinglish") and romanised Gujarati are detected with a small
 * list of very frequent function words.
 */
object LanguageDetector {

    private val hinglishWords = setOf(
        "hai", "hain", "nahi", "nahin", "kya", "kyu", "kyun", "karna", "karo", "karenge",
        "kar", "mein", "main", "hum", "tum", "aap", "yeh", "ye", "woh", "wo", "bhi",
        "toh", "to", "ki", "ka", "ke", "ko", "se", "par", "acha", "accha", "theek",
        "sakte", "sakta", "chahiye", "wala", "wale", "raha", "rahe", "hoga", "hota",
        "matlab", "abhi", "baad", "pehle", "bahut", "bohot", "jaise", "agar"
    )

    private val romanGujaratiWords = setOf(
        "che", "chhe", "nathi", "saru", "saaru", "thai", "thay", "jay", "jaay", "ma",
        "mate", "ena", "eno", "enu", "tyare", "biji", "vaar", "ave", "aave", "kari",
        "karvu", "karvo", "joie", "joiye", "shakay", "sakay", "pan", "ane", "hoy",
        "tame", "ame", "apde", "aapde", "kem", "shu", "su", "evu", "aa", "te"
    )

    private val englishWords = setOf(
        "the", "is", "are", "we", "can", "should", "customer", "feature", "app", "and",
        "for", "with", "this", "that", "idea", "business", "model", "monthly", "sell",
        "restaurant", "subscription", "automatically", "identify", "track", "coupon",
        "number", "mobile", "software", "owner", "useful", "send"
    )

    fun detect(text: String): SpokenLanguage {
        if (text.isBlank()) return SpokenLanguage.UNKNOWN
        var gu = 0; var hi = 0; var latin = 0
        for (ch in text) {
            when (ch.code) {
                in 0x0A80..0x0AFF -> gu++
                in 0x0900..0x097F -> hi++
                in 'a'.code..'z'.code, in 'A'.code..'Z'.code -> latin++
            }
        }
        val letters = gu + hi + latin
        if (letters == 0) return SpokenLanguage.UNKNOWN

        val guShare = gu.toDouble() / letters
        val hiShare = hi.toDouble() / letters
        val latinShare = latin.toDouble() / letters

        if (gu > 0 && hi > 0 && guShare > 0.15 && hiShare > 0.15) return SpokenLanguage.MIXED
        if (gu > 0 && guShare >= 0.15) {
            return if (latinShare >= 0.15) SpokenLanguage.GUJARATI_ENGLISH else SpokenLanguage.GUJARATI
        }
        if (hi > 0 && hiShare >= 0.15) {
            return if (latinShare >= 0.15) SpokenLanguage.HINGLISH else SpokenLanguage.HINDI
        }

        // Latin script only: decide between English, romanised Hindi and romanised Gujarati.
        val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return SpokenLanguage.UNKNOWN
        val hiHits = words.count { it in hinglishWords }
        val guHits = words.count { it in romanGujaratiWords }
        val enHits = words.count { it in englishWords }
        val threshold = maxOf(1, words.size / 8)
        return when {
            guHits >= threshold && guHits >= hiHits -> SpokenLanguage.GUJARATI_ENGLISH
            hiHits >= threshold -> SpokenLanguage.HINGLISH
            enHits > 0 || words.isNotEmpty() -> SpokenLanguage.ENGLISH
            else -> SpokenLanguage.UNKNOWN
        }
    }
}
