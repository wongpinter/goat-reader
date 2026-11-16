package com.wongpinter.goatreader.ui.reader

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/** Intercepts only deliberate vertical chapter swipes at the current scroll boundary. */
internal class ChapterSwipeFrameLayout(
    context: Context,
    private val onChapterSwipe: (ChapterSwipeDirection) -> Unit,
    private val onReaderTap: () -> Unit = {},
    private val captureChapterSwipe: (() -> (ChapterSwipeDirection) -> Unit)? = null,
) : FrameLayout(context) {
    var chapterSwipesEnabled: Boolean = false
    var minimumTravelDp: Int = CHAPTER_SWIPE_MIN_TRAVEL_DP
    var chapterSwipeBoundaries: ChapterSwipeBoundaries = ChapterSwipeBoundaries()
    var chapterSwipeBoundaryProvider: (() -> ChapterSwipeBoundaries)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var candidate = false
    private var intercepted = false
    private var tracker: ChapterBoundarySwipeTracker? = null
    private var capturedChapterSwipe: ((ChapterSwipeDirection) -> Unit)? = null
    private var readerTapCandidate = false
    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapDownTime = 0L

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val content = getChildAt(0)
                readerTapCandidate = content != null &&
                    event.x in (content.left + content.width * 0.2f)..(content.right - content.width * 0.2f) &&
                    event.y in (content.top + content.height * 0.2f)..(content.bottom - content.height * 0.2f)
                tapDownX = event.x
                tapDownY = event.y
                tapDownTime = event.eventTime
            }

            MotionEvent.ACTION_MOVE -> {
                if (absDistance(event.x - tapDownX) > touchSlop || absDistance(event.y - tapDownY) > touchSlop) {
                    readerTapCandidate = false
                }
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> readerTapCandidate = false
            MotionEvent.ACTION_UP -> {
                val isTap = readerTapCandidate &&
                    absDistance(event.x - tapDownX) <= touchSlop && absDistance(event.y - tapDownY) <= touchSlop &&
                    event.eventTime - tapDownTime <= ViewConfiguration.getLongPressTimeout().toLong()
                val handled = super.dispatchTouchEvent(event)
                readerTapCandidate = false
                if (isTap) onReaderTap()
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // A WebView disallows parent interception as soon as scrolling starts.
        // Keep observing only while a boundary swipe is armed; the tracker still
        // waits for the configured deliberate vertical pull before taking the gesture.
        super.requestDisallowInterceptTouchEvent(disallowIntercept && !candidate)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetGesture()
                val boundaries = if (chapterSwipesEnabled) {
                    chapterSwipeBoundaryProvider?.invoke() ?: chapterSwipeBoundaries
                } else {
                    ChapterSwipeBoundaries()
                }
                candidate = chapterSwipesEnabled && boundaries.hasBoundary &&
                    startInReaderContent(event.x, event.y)
                tracker = if (candidate) ChapterBoundarySwipeTracker(
                    startX = event.x,
                    startY = event.y,
                    boundaries = boundaries,
                    minimumTravel = dp(minimumTravelDp).toFloat(),
                    touchSlop = touchSlop,
                ) else null
                if (tracker?.startsAtBoundary == true) {
                    capturedChapterSwipe = captureChapterSwipe?.invoke()
                } else {
                    candidate = false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!candidate) return false
                val update = tracker?.move(
                    event.x, event.y, pointerCount = event.pointerCount, enabled = chapterSwipesEnabled,
                ) ?: return false
                if (update.cancelled) {
                    candidate = false
                    return false
                }
                if (update.shouldIntercept) {
                    intercepted = true
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!intercepted) resetGesture()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!intercepted) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                tracker?.move(
                    event.x, event.y, pointerCount = event.pointerCount, enabled = chapterSwipesEnabled,
                )
            }

            MotionEvent.ACTION_POINTER_DOWN -> tracker?.cancel()

            MotionEvent.ACTION_UP -> {
                val swipeHandler = capturedChapterSwipe ?: onChapterSwipe
                tracker?.release(event.x, event.y, enabled = chapterSwipesEnabled)?.let(swipeHandler)
                resetGesture()
            }

            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return true
    }

    private fun startInReaderContent(x: Float, y: Float): Boolean {
        val content = getChildAt(0) ?: return false
        val systemGestures = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemGestures())
        val topInset = max(dp(8), (systemGestures?.top ?: 0) + dp(8))
        val bottomInset = max(dp(8), (systemGestures?.bottom ?: 0) + dp(8))
        return x in (content.left + dp(8)).toFloat()..(content.right - dp(8)).toFloat() &&
            y in (content.top + topInset).toFloat()..(content.bottom - bottomInset).toFloat()
    }

    private fun resetGesture() {
        candidate = false
        intercepted = false
        tracker = null
        capturedChapterSwipe = null
    }

    private fun absDistance(value: Float): Float = kotlin.math.abs(value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
