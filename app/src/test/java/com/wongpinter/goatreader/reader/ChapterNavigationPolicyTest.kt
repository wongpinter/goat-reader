package com.wongpinter.goatreader.reader

import com.wongpinter.goatreader.ui.reader.adjacentChapterEntryIndex
import com.wongpinter.goatreader.ui.reader.chapterEndClearancePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNavigationPolicyTest {
    private val chapterSpineIndices = listOf(0, 2, 4, 7)

    @Test fun `chooses adjacent chapter in requested direction`() {
        assertEquals(1, adjacentChapterEntryIndex(1, chapterSpineIndices, forward = true))
        assertEquals(1, adjacentChapterEntryIndex(4, chapterSpineIndices, forward = false))
        assertEquals(3, adjacentChapterEntryIndex(4, chapterSpineIndices, forward = true))
    }

    @Test fun `binary search skips same-spine entries and preserves duplicate ordering`() {
        val indices = listOf(0, 2, 2, 2, 7)
        assertEquals(4, adjacentChapterEntryIndex(2, indices, forward = true))
        assertEquals(0, adjacentChapterEntryIndex(2, indices, forward = false))
    }

    @Test fun `clearance is placed only at actual TOC chapter ends`() {
        val starts = listOf(0 to "one.xhtml", 3 to "four.xhtml", 5 to "six.xhtml")
        assertEquals(false, chapterEndClearancePlan(1, 5, starts).atDocumentEnd)
        assertEquals(true, chapterEndClearancePlan(2, 5, starts).atDocumentEnd)
        assertEquals(true, chapterEndClearancePlan(5, 5, starts).atDocumentEnd)
    }

    @Test fun `same document chapter boundaries use their TOC anchor fragments`() {
        val starts = listOf(
            0 to "book.xhtml#part-one",
            0 to "book.xhtml#part-two",
            0 to "book.xhtml#part-three",
            2 to "last.xhtml#last-chapter",
        )
        val plan = chapterEndClearancePlan(0, 2, starts)
        assertEquals(false, plan.atDocumentEnd)
        assertEquals(listOf("part-two", "part-three"), plan.beforeFragments)
    }

    @Test fun `chapter navigation stops at first and last entries`() {
        assertNull(adjacentChapterEntryIndex(0, chapterSpineIndices, forward = false))
        assertNull(adjacentChapterEntryIndex(7, chapterSpineIndices, forward = true))
        assertNull(adjacentChapterEntryIndex(-1, chapterSpineIndices, forward = true))
        assertNull(adjacentChapterEntryIndex(1, emptyList(), forward = false))
    }
}
