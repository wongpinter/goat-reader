package com.wongpinter.goatreader.reader

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wongpinter.goatreader.ui.reader.ChapterSwipeDirection
import com.wongpinter.goatreader.ui.reader.ChapterSwipeFrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterSwipeFrameLayoutTest {
    @Test
    fun oneMoveEdgeSwipeCommitsOnRelease() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val (layout, child, density) = fixture(swipes)
        val downTime = SystemClock.uptimeMillis()
        val x = 48f * density
        val y = layout.height / 2f

        send(layout, downTime, MotionEvent.ACTION_DOWN, x, y)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x + 90f * density, y, 16L)
        send(layout, downTime, MotionEvent.ACTION_UP, x + 90f * density, y, 32L)

        assertEquals(listOf(ChapterSwipeDirection.PREVIOUS), swipes)
        assertTrue(child.actions.contains(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun shortEdgeDragIsCapturedFromWebViewButDoesNotNavigate() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val (layout, child, density) = fixture(swipes)
        val downTime = SystemClock.uptimeMillis()
        val x = 320f * density
        val y = layout.height / 2f

        send(layout, downTime, MotionEvent.ACTION_DOWN, x, y)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x - 32f * density, y, 16L)
        send(layout, downTime, MotionEvent.ACTION_UP, x - 32f * density, y, 32L)

        assertTrue(swipes.isEmpty())
        assertTrue(child.actions.contains(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun contentTapNotDragOrLongPressRevealsReaderChrome() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val taps = mutableListOf<Unit>()
        val (layout, _, density) = fixture(swipes) { taps += Unit }
        val x = 180f * density
        val y = 320f * density

        val tapDown = SystemClock.uptimeMillis()
        send(layout, tapDown, MotionEvent.ACTION_DOWN, x, y)
        send(layout, tapDown, MotionEvent.ACTION_UP, x, y, 80L)
        assertEquals(1, taps.size)

        val dragDown = SystemClock.uptimeMillis()
        send(layout, dragDown, MotionEvent.ACTION_DOWN, x, y)
        send(layout, dragDown, MotionEvent.ACTION_MOVE, x + 40f * density, y, 32L)
        send(layout, dragDown, MotionEvent.ACTION_UP, x + 40f * density, y, 80L)

        val holdDown = SystemClock.uptimeMillis()
        send(layout, holdDown, MotionEvent.ACTION_DOWN, x, y)
        send(layout, holdDown, MotionEvent.ACTION_UP, x, y, 700L)
        assertEquals(1, taps.size)
    }

    @Test
    fun chromeToggleRequiresCentralTapZone() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val taps = mutableListOf<Unit>()
        val (layout, _, density) = fixture(swipes) { taps += Unit }
        val downTime = SystemClock.uptimeMillis()

        send(layout, downTime, MotionEvent.ACTION_DOWN, 12f * density, layout.height / 2f)
        send(layout, downTime, MotionEvent.ACTION_UP, 12f * density, layout.height / 2f, 80L)
        assertTrue(taps.isEmpty())

        val centerDown = SystemClock.uptimeMillis()
        send(layout, centerDown, MotionEvent.ACTION_DOWN, layout.width / 2f, layout.height / 2f)
        send(layout, centerDown, MotionEvent.ACTION_UP, layout.width / 2f, layout.height / 2f, 80L)
        assertEquals(1, taps.size)
    }

    @Test
    fun disabledPagesModeLeavesTouchEventsWithTheChild() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val (layout, child, density) = fixture(swipes)
        layout.chapterSwipesEnabled = false
        val downTime = SystemClock.uptimeMillis()
        val x = 48f * density
        val y = layout.height / 2f

        send(layout, downTime, MotionEvent.ACTION_DOWN, x, y)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x + 90f * density, y, 16L)
        send(layout, downTime, MotionEvent.ACTION_UP, x + 90f * density, y, 32L)

        assertTrue(swipes.isEmpty())
        assertTrue(child.actions.contains(MotionEvent.ACTION_MOVE))
        assertTrue(!child.actions.contains(MotionEvent.ACTION_CANCEL))
    }

    @Test
    fun interceptedGestureCanceledBySystemNeverNavigates() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val (layout, _, density) = fixture(swipes)
        val downTime = SystemClock.uptimeMillis()
        val x = 48f * density
        val y = layout.height / 2f

        send(layout, downTime, MotionEvent.ACTION_DOWN, x, y)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x + 90f * density, y, 16L)
        send(layout, downTime, MotionEvent.ACTION_CANCEL, x + 90f * density, y, 32L)

        assertTrue(swipes.isEmpty())
    }

    @Test
    fun reversalCancellationAndVerticalScrollNeverCommit() = onMain {
        val swipes = mutableListOf<ChapterSwipeDirection>()
        val (layout, _, density) = fixture(swipes)
        val downTime = SystemClock.uptimeMillis()
        val x = 320f * density
        val y = layout.height / 2f

        send(layout, downTime, MotionEvent.ACTION_DOWN, x, y)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x - 90f * density, y, 16L)
        send(layout, downTime, MotionEvent.ACTION_MOVE, x - 70f * density, y, 32L)
        send(layout, downTime, MotionEvent.ACTION_UP, x - 70f * density, y, 48L)
        assertTrue(swipes.isEmpty())

        val verticalDown = SystemClock.uptimeMillis()
        val leftX = 48f * density
        send(layout, verticalDown, MotionEvent.ACTION_DOWN, leftX, y)
        send(layout, verticalDown, MotionEvent.ACTION_MOVE, leftX, y + 90f * density, 16L)
        send(layout, verticalDown, MotionEvent.ACTION_UP, leftX, y + 90f * density, 32L)
        assertTrue(swipes.isEmpty())
    }

    private fun fixture(
        swipes: MutableList<ChapterSwipeDirection>,
        onReaderTap: () -> Unit = {},
    ): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val density = context.resources.displayMetrics.density
        val layout = ChapterSwipeFrameLayout(context, swipes::add, onReaderTap).apply {
            chapterSwipesEnabled = true
            addView(RecordingView(context), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
        val width = (360f * density).toInt()
        val height = (640f * density).toInt()
        layout.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout.layout(0, 0, width, height)
        val child = layout.getChildAt(0) as RecordingView
        return Fixture(layout, child, density)
    }

    private fun send(
        layout: ChapterSwipeFrameLayout,
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
        offsetMillis: Long = 0L,
    ) {
        val event = MotionEvent.obtain(downTime, downTime + offsetMillis, action, x, y, 0)
        try {
            layout.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    private data class Fixture(
        val layout: ChapterSwipeFrameLayout,
        val child: RecordingView,
        val density: Float,
    )

    private class RecordingView(context: android.content.Context) : View(context) {
        val actions = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            return true
        }
    }
}
