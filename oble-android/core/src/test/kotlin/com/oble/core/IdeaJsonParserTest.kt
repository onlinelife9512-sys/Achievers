package com.oble.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaJsonParserTest {
    @Test fun parsesSingleObjectSpecShape() {
        val raw = """{"is_idea": true, "title": "Repeat Customer Detection",
            "summary": "Identify returning restaurant customers automatically using their mobile number.",
            "category": "Restaurant Software", "action_items": ["Store numbers"], "confidence": 0.91}"""
        val ideas = IdeaJsonParser.parse(raw)
        assertEquals(1, ideas.size)
        assertEquals("Repeat Customer Detection", ideas[0].title)
        assertEquals(0.91, ideas[0].confidence, 1e-9)
        assertEquals(listOf("Store numbers"), ideas[0].actionItems)
    }

    @Test fun parsesItemsWithFencesAndMerge() {
        val raw = "```json\n{\"items\":[{\"is_idea\":true,\"action\":\"merge\",\"merge_into_id\":\"7\",\"type\":\"feature\",\"title\":\"T\",\"summary\":\"S\",\"category\":\"C\",\"action_items\":[],\"confidence\":85}]}\n```"
        val idea = IdeaJsonParser.parse(raw).single()
        assertEquals(7L, idea.mergeIntoId)
        assertEquals(NoteType.FEATURE, idea.type)
        assertEquals(0.85, idea.confidence, 1e-9)
    }

    @Test fun emptyAndGarbage() {
        assertTrue(IdeaJsonParser.parse("{\"items\":[]}").isEmpty())
        assertTrue(IdeaJsonParser.parse("sorry, nothing here").isEmpty())
    }

    @Test fun transcript() {
        val (text, lang) = IdeaJsonParser.parseTranscript("{\"text\":\"આ feature સારું છે\",\"language\":\"gu-en\"}")
        assertEquals("આ feature સારું છે", text)
        assertEquals("gu-en", lang)
    }
}
