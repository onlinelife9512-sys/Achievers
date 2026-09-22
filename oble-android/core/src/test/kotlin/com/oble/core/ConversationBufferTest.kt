package com.oble.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBufferTest {
    private val policy = ConversationBuffer.Policy(
        minNewChars = 50, minIntervalMs = 10_000, maxNewChars = 200,
        silenceFlushMs = 5_000, minCharsOnSilence = 20, contextChars = 100,
    )

    @Test fun doesNotAnalyseTinyFragments() {
        val b = ConversationBuffer(policy)
        b.add(ConversationBuffer.Segment(1, "hello", 0))
        assertFalse(b.shouldAnalyze(60_000))
    }

    @Test fun waitsForIntervalThenAnalyses() {
        val b = ConversationBuffer(policy)
        b.add(ConversationBuffer.Segment(1, "x".repeat(60), 0))
        b.add(ConversationBuffer.Segment(2, "y", 4_000))
        assertFalse(b.shouldAnalyze(4_000))
        assertTrue(b.shouldAnalyze(10_000))
    }

    @Test fun forcesWhenLarge() {
        val b = ConversationBuffer(policy)
        b.add(ConversationBuffer.Segment(1, "x".repeat(250), 0))
        assertTrue(b.shouldAnalyze(1))
    }

    @Test fun flushesOnSilence() {
        val b = ConversationBuffer(policy)
        b.add(ConversationBuffer.Segment(1, "x".repeat(30), 1_000))
        assertFalse(b.shouldAnalyze(3_000))
        assertTrue(b.shouldAnalyze(6_500))
    }

    @Test fun rollbackKeepsSpeechAndCommitKeepsContext() {
        val b = ConversationBuffer(policy)
        b.add(ConversationBuffer.Segment(1, "first point about customers", 0))
        val w = b.take(1_000)
        assertNotNull(w)
        assertEquals(0, b.pendingChars())
        b.rollback()
        assertEquals("first point about customers".length, b.pendingChars())

        b.take(2_000); b.commit()
        b.add(ConversationBuffer.Segment(2, "second point", 3_000))
        val w2 = b.take(4_000)!!
        assertEquals("first point about customers", w2.contextText)
        assertEquals("second point", w2.pendingText)
    }
}
