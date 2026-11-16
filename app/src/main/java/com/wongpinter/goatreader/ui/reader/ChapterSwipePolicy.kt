package com.wongpinter.goatreader.ui.reader

import kotlin.math.abs

internal enum class ChapterSwipeDirection { PREVIOUS, NEXT }

internal const val CHAPTER_SWIPE_MIN_TRAVEL_DP = 200

internal data class ChapterSwipeBoundaries(
    val previous: Boolean = false,
    val next: Boolean = false,
    val chapterIndex: Int? = null,
) {
    fun permits(direction: ChapterSwipeDirection): Boolean = when (direction) {
        ChapterSwipeDirection.PREVIOUS -> previous
        ChapterSwipeDirection.NEXT -> next
    }

    val hasBoundary: Boolean get() = previous || next
}

/** Chapter-only vertical swipes are available only at the native scroll surface's boundaries. */
internal fun nativeChapterSwipeBoundaries(
    firstVisibleIndex: Int,
    firstVisibleScrollOffset: Int,
    lastVisibleIndex: Int,
    chapterStarts: List<Int>,
    finalItemAtViewportEnd: Boolean,
    startTolerancePx: Int = 0,
): ChapterSwipeBoundaries {
    if (chapterStarts.isEmpty() || firstVisibleIndex < 0 || lastVisibleIndex < firstVisibleIndex) {
        return ChapterSwipeBoundaries()
    }
    val chapterIndex = chapterStarts.indexOfLast { it <= firstVisibleIndex }
    if (chapterIndex !in chapterStarts.indices) return ChapterSwipeBoundaries()
    val chapterStart = chapterStarts[chapterIndex]
    val nextChapterStart = chapterStarts.getOrNull(chapterIndex + 1)
    val chapterEnd = nextChapterStart?.minus(1)
    val previous = chapterIndex > 0 && firstVisibleIndex == chapterStart &&
        firstVisibleScrollOffset in 0..startTolerancePx
    val next = chapterEnd != null && lastVisibleIndex >= chapterEnd && finalItemAtViewportEnd
    return ChapterSwipeBoundaries(
        previous = previous,
        next = next,
        chapterIndex = chapterIndex.takeIf { previous || next },
    )
}

/** A missing scroll position must never arm a boundary gesture. */
internal fun epubVisualEdge(canScrollTowardEdge: Boolean?): Boolean = canScrollTowardEdge == false

/** Navigate only when the visible EPUB resource is at an edge. */
internal fun epubChapterSwipeBoundaries(
    atResourceStart: Boolean,
    atResourceEnd: Boolean,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
): ChapterSwipeBoundaries = ChapterSwipeBoundaries(
    previous = atResourceStart && canNavigatePrevious,
    next = atResourceEnd && canNavigateNext,
)

internal data class ChapterSwipeUpdate(
    val shouldIntercept: Boolean,
    val shouldConsume: Boolean,
    val cancelled: Boolean,
)

/** Recognizes a deliberate vertical swipe at a chapter boundary, shared by Compose and Readium. */
internal class ChapterBoundarySwipeTracker(
    private val startX: Float,
    private val startY: Float,
    private val boundaries: ChapterSwipeBoundaries,
    private val minimumTravel: Float,
    private val touchSlop: Float,
) {
    private var lastY = startY
    private var intent: ChapterSwipeDirection? = null
    private var reversalTravel = 0f
    private var cancelled = false

    val startsAtBoundary: Boolean
        get() = startX.isFinite() && startY.isFinite() && minimumTravel.isFinite() &&
            touchSlop.isFinite() && minimumTravel > 0f && touchSlop >= 0f && boundaries.hasBoundary

    fun move(x: Float, y: Float, pointerCount: Int = 1, enabled: Boolean = true): ChapterSwipeUpdate {
        if (!enabled || pointerCount != 1 || !x.isFinite() || !y.isFinite() || !startsAtBoundary) cancel()
        val dx = x - startX
        val dy = y - startY
        if (!cancelled && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.25f) cancel()

        val direction = when {
            dy > 0f -> ChapterSwipeDirection.PREVIOUS
            dy < 0f -> ChapterSwipeDirection.NEXT
            else -> null
        }
        if (!cancelled && direction != null && abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.5f) {
            if (!boundaries.permits(direction) || intent != null && intent != direction) {
                cancel()
            } else {
                intent = direction
                val stepY = y - lastY
                val expectedSign = if (direction == ChapterSwipeDirection.PREVIOUS) 1f else -1f
                if (stepY * expectedSign < 0f) {
                    reversalTravel += abs(stepY)
                    if (reversalTravel > touchSlop) cancel()
                } else if (stepY * expectedSign > 0f) {
                    reversalTravel = 0f
                }
            }
        }
        lastY = y
        val committed = !cancelled && intent != null && abs(dy) >= minimumTravel &&
            abs(dy) > abs(dx) * 1.5f
        return ChapterSwipeUpdate(
            shouldIntercept = committed,
            shouldConsume = committed,
            cancelled = cancelled,
        )
    }

    fun release(x: Float, y: Float, enabled: Boolean = true): ChapterSwipeDirection? {
        val update = move(x, y, enabled = enabled)
        if (update.cancelled || !update.shouldConsume) return null
        return intent
    }

    fun cancel() {
        cancelled = true
        intent = null
    }
}
