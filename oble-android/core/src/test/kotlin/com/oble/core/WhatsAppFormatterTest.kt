package com.oble.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppFormatterTest {
    private val note = WhatsAppFormatter.Note(
        NoteType.IDEA, "Repeat Customer Detection",
        "Identify returning restaurant customers automatically using their mobile number.",
        "Restaurant Software", "New Idea", listOf("Store numbers"), "22 Sep 2026, 6:30 PM",
    )

    @Test fun formatsMessage() {
        val m = WhatsAppFormatter.format(note)
        assertTrue(m.startsWith("💡 *IDEA*"))
        assertTrue(m.contains("Category: Restaurant Software"))
        assertTrue(m.contains("• Store numbers"))
    }

    @Test fun flattensForTemplate() {
        val flat = WhatsAppFormatter.flattenForTemplate(WhatsAppFormatter.format(note))
        assertFalse(flat.contains('\n'))
        assertFalse(flat.contains("  "))
    }

    @Test fun phones() {
        assertEquals("919876543210", WhatsAppFormatter.normalizePhone("+91 98765-43210"))
        assertEquals("919876543210", WhatsAppFormatter.normalizePhone("09876543210"))
        assertEquals("919876543210", WhatsAppFormatter.normalizePhone("9876543210"))
        assertEquals("14155550100", WhatsAppFormatter.normalizePhone("0014155550100"))
        assertNull(WhatsAppFormatter.normalizePhone("123"))
    }

    @Test fun link() {
        val url = WhatsAppFormatter.clickToChatUrl("919876543210", "a b&c")
        assertEquals("https://wa.me/919876543210?text=a%20b%26c", url)
    }
}
