package me.rerere.rikkahub.data.db.fts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MessageFuzzySearchTest {
    private val terms = listOf("a", "b", "c")

    @Test
    fun `gap count measures only characters between ordered terms`() {
        assertEquals(0, gapCount("abc"))
        assertEquals(1, gapCount("axbc"))
        assertEquals(1, gapCount("abxc"))
        assertEquals(2, gapCount("axbxc"))
        assertEquals(2, gapCount("abxxc"))
    }

    @Test
    fun `matching chooses the tightest occurrence instead of the first occurrence`() {
        val ranges = "a---abc".findOrderedTerms(terms)

        assertNotNull(ranges)
        assertEquals(0, ranges!!.orderedTermGapCount())
        assertEquals(4, ranges.first().first)
    }

    @Test
    fun `matching is case insensitive`() {
        val ranges = "AxBxC".findOrderedTerms(terms)

        assertNotNull(ranges)
        assertEquals(2, ranges!!.orderedTermGapCount())
        assertEquals(0, ranges.first().first)
        assertEquals(4, ranges.last().first)
    }

    private fun gapCount(text: String): Int =
        text.findOrderedTerms(terms)!!.orderedTermGapCount()
}
