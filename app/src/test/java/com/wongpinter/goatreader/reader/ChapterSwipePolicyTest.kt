package com.wongpinter.goatreader.reader

import com.wongpinter.goatreader.ui.reader.CHAPTER_SWIPE_MIN_TRAVEL_DP
import com.wongpinter.goatreader.ui.reader.ChapterBoundarySwipeTracker
import com.wongpinter.goatreader.ui.reader.ChapterSwipeBoundaries
import com.wongpinter.goatreader.ui.reader.ChapterSwipeDirection
import com.wongpinter.goatreader.ui.reader.epubChapterSwipeBoundaries
import com.wongpinter.goatreader.ui.reader.epubVisualEdge
import com.wongpinter.goatreader.ui.reader.nativeChapterSwipeBoundaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSwipePolicyTest {
    @Test fun `native reader enables previous only at chapter top and next only at chapter end`() {
        val starts = listOf(0, 4, 9)
        assertEquals(
            ChapterSwipeBoundaries(previous = true, chapterIndex = 1),
            nativeChapterSwipeBoundaries(4, 0, 4, starts, finalItemAtViewportEnd = false),
        )
        assertEquals(
            ChapterSwipeBoundaries(next = true, chapterIndex = 1),
            nativeChapterSwipeBoundaries(5, 0, 8, starts, finalItemAtViewportEnd = true),
        )
        assertEquals(
            ChapterSwipeBoundaries(),
            nativeChapterSwipeBoundaries(4, 12, 7, starts, finalItemAtViewportEnd = false),
        )
        assertEquals(
            ChapterSwipeBoundaries(),
            nativeChapterSwipeBoundaries(0, 0, 3, starts, finalItemAtViewportEnd = false),
        )
        assertEquals(
            ChapterSwipeBoundaries(previous = true, chapterIndex = 2),
            nativeChapterSwipeBoundaries(9, 0, 11, starts, finalItemAtViewportEnd = true),
        )
    }

    @Test fun `EPUB visual edge requires a webview that cannot scroll toward the edge`() {
        assertFalse(epubVisualEdge(null))
        assertFalse(epubVisualEdge(true))
        assertTrue(epubVisualEdge(false))
    }

    @Test fun `EPUB gestures require the actual resource edge and an adjacent chapter`() {
        assertEquals(
            ChapterSwipeBoundaries(previous = true),
            epubChapterSwipeBoundaries(
                atResourceStart = true,
                atResourceEnd = false,
                canNavigatePrevious = true,
                canNavigateNext = true,
            ),
        )
        assertEquals(
            ChapterSwipeBoundaries(next = true),
            epubChapterSwipeBoundaries(
                atResourceStart = false,
                atResourceEnd = true,
                canNavigatePrevious = true,
                canNavigateNext = true,
            ),
        )
        assertEquals(
            ChapterSwipeBoundaries(),
            epubChapterSwipeBoundaries(
                atResourceStart = false,
                atResourceEnd = false,
                canNavigatePrevious = true,
                canNavigateNext = true,
            ),
        )
        assertEquals(
            ChapterSwipeBoundaries(),
            epubChapterSwipeBoundaries(
                atResourceStart = true,
                atResourceEnd = true,
                canNavigatePrevious = false,
                canNavigateNext = false,
            ),
        )
    }

    private fun tracker(boundaries: ChapterSwipeBoundaries) = ChapterBoundarySwipeTracker(
        startX = 180f,
        startY = 200f,
        boundaries = boundaries,
        minimumTravel = CHAPTER_SWIPE_MIN_TRAVEL_DP.toFloat(),
        touchSlop = 8f,
    )

    @Test fun `chapter swipe needs a 200dp pull in the permitted direction`() {
        val next = tracker(ChapterSwipeBoundaries(next = true))
        assertFalse(next.move(184f, 1f).shouldConsume)
        assertTrue(next.move(184f, 0f).shouldConsume)
        assertEquals(ChapterSwipeDirection.NEXT, next.release(184f, 0f))

        val previous = tracker(ChapterSwipeBoundaries(previous = true))
        assertFalse(previous.move(177f, 399f).shouldConsume)
        assertTrue(previous.move(177f, 400f).shouldConsume)
        assertEquals(ChapterSwipeDirection.PREVIOUS, previous.release(177f, 400f))
    }

    @Test fun `ordinary direction short and horizontal swipes stay with scrolling`() {
        val wrongDirection = tracker(ChapterSwipeBoundaries(next = true))
        assertTrue(wrongDirection.move(180f, 300f).cancelled)
        assertNull(wrongDirection.release(180f, 300f))

        val short = tracker(ChapterSwipeBoundaries(next = true))
        assertFalse(short.move(180f, 130f).shouldConsume)
        assertNull(short.release(180f, 130f))

        val horizontal = tracker(ChapterSwipeBoundaries(next = true))
        assertTrue(horizontal.move(300f, 198f).cancelled)
        assertNull(horizontal.release(300f, 198f))
    }

    @Test fun `interior gestures multitouch and reversed swipes cannot navigate`() {
        val interior = tracker(ChapterSwipeBoundaries())
        assertFalse(interior.startsAtBoundary)
        assertNull(interior.release(180f, 80f))

        val multitouch = tracker(ChapterSwipeBoundaries(next = true))
        assertTrue(multitouch.move(180f, 100f, pointerCount = 2).cancelled)
        assertNull(multitouch.release(180f, 80f))

        val reversed = tracker(ChapterSwipeBoundaries(next = true))
        reversed.move(180f, 100f)
        assertTrue(reversed.move(180f, 115f).cancelled)
        assertNull(reversed.release(180f, 80f))

        val disabled = tracker(ChapterSwipeBoundaries(next = true))
        assertNull(disabled.release(180f, 80f, enabled = false))
    }
}
