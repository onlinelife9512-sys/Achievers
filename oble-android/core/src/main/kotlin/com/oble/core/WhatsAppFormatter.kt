package com.oble.core

import java.net.URLEncoder

/** Builds the WhatsApp message for a note and the official click-to-chat link. */
object WhatsAppFormatter {

    data class Note(
        val type: NoteType,
        val title: String,
        val summary: String,
        val category: String,
        val status: String,
        val actionItems: List<String>,
        val time: String,
    )

    fun format(note: Note): String = buildString {
        append(note.type.emoji).append(" *").append(note.type.label).append("*\n\n")
        append('*').append(note.title.trim()).append("*\n")
        append(note.summary.trim()).append('\n')
        if (note.actionItems.isNotEmpty()) {
            append("\nNext steps:\n")
            note.actionItems.forEach { append("• ").append(it.trim()).append('\n') }
        }
        append("\nCategory: ").append(note.category)
        append("\nStatus: ").append(note.status)
        append("\nTime: ").append(note.time)
        append("\n\n— captured by OBLE")
    }

    /** Several queued notes in one message, so a single tap delivers all of them. */
    fun formatBatch(notes: List<Note>): String =
        if (notes.size == 1) format(notes.first())
        else notes.joinToString("\n\n━━━━━━━━━━\n\n") { format(it).removeSuffix("\n\n— captured by OBLE") } +
            "\n\n— ${notes.size} notes captured by OBLE"

    /**
     * WhatsApp Cloud API template parameters may not contain new lines, tabs or more
     * than four consecutive spaces. This flattens a formatted message accordingly.
     */
    fun flattenForTemplate(message: String): String =
        message.replace(Regex("\\s*\\n+\\s*"), " | ")
            .replace('\t', ' ')
            .replace(Regex(" {2,}"), " ")
            .trim(' ', '|')
            .take(1000)

    /**
     * Normalises a phone number to the international digits-only form wa.me and the
     * Cloud API expect (e.g. "+91 98765-43210" -> "919876543210"). A 10-digit number
     * is assumed to be Indian and gets the [defaultCountryCode].
     */
    fun normalizePhone(raw: String, defaultCountryCode: String = "91"): String? {
        var digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("00")) digits = digits.removePrefix("00")
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length == 10) digits = defaultCountryCode + digits
        return digits.takeIf { it.length in 11..15 }
    }

    /** Official WhatsApp click-to-chat link with a pre-filled message. */
    fun clickToChatUrl(phoneDigits: String?, message: String): String {
        val text = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return if (phoneDigits.isNullOrEmpty()) "https://wa.me/?text=$text"
        else "https://wa.me/$phoneDigits?text=$text"
    }
}
