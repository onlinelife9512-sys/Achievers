package com.oble.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateResolverTest {
    private val resolver = DuplicateResolver()
    private val existing = listOf(
        ExistingNote(1, "Repeat Customer Detection", "Identify returning restaurant customers automatically using their mobile number.", "Restaurant Software"),
        ExistingNote(2, "Monthly Subscription Pricing", "Sell the software on a monthly subscription.", "Pricing"),
    )

    private fun idea(title: String, summary: String, category: String = "Restaurant Software", merge: Long? = null) =
        DetectedIdea(true, title, summary, category, emptyList(), 0.9, mergeIntoId = merge)

    @Test fun explicitMergeWins() =
        assertEquals(DuplicateResolver.Decision.Merge(2, 1.0), resolver.resolve(idea("X", "Y", merge = 2), null, existing))

    @Test fun lexicalDuplicate() {
        val d = resolver.resolve(idea("Repeat Customer Detection", "Identify returning restaurant customers automatically using mobile number."), null, existing)
        assertTrue(d is DuplicateResolver.Decision.Duplicate && d.targetId == 1L)
    }

    @Test fun unrelatedCreates() =
        assertEquals(DuplicateResolver.Decision.Create, resolver.resolve(idea("Hire Delivery Staff", "Recruit two riders for evening delivery.", "Hiring"), null, existing))

    @Test fun semanticMerge() {
        val e = listOf(existing[0].copy(embedding = floatArrayOf(1f, 0f, 0f)))
        val d = resolver.resolve(idea("Coupons For Repeat Customers", "Send coupons to returning customers."), floatArrayOf(0.88f, 0.47f, 0f), e)
        assertTrue(d is DuplicateResolver.Decision.Merge)
    }

    @Test fun mergesActionItems() =
        assertEquals(listOf("Save numbers", "Send coupons"),
            DuplicateResolver.mergeActionItems(listOf("Save numbers"), listOf("save numbers", "Send coupons")))
}
