package com.wongpinter.goatreader.ui.reader

import com.wongpinter.goatreader.data.PageMargins
import com.wongpinter.goatreader.data.PageTurnAnimation
import com.wongpinter.goatreader.data.ReaderFont
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStylePresetTest {
    @Test
    fun `preset updates typography and margins while preserving unrelated book settings`() {
        val original = ReaderSettings(
            fontSizeSp = 26f,
            lineHeight = 1.8f,
            font = ReaderFont.SANS,
            pageMarginDp = 36,
            readingMode = ReadingMode.PAGES,
            pageTurnAnimation = PageTurnAnimation.NONE,
            bookStyles = true,
        )

        val compact = original.withPreset(ReaderStylePreset.COMPACT)

        assertEquals(16f, compact.fontSizeSp)
        assertEquals(1.35f, compact.lineHeight)
        assertEquals(12, compact.sideMarginDp)
        assertEquals(PageMargins.NORMAL, compact.pageMargins)
        assertEquals(ReaderFont.SANS, compact.font)
        assertEquals(ReadingMode.PAGES, compact.readingMode)
        assertEquals(PageTurnAnimation.NONE, compact.pageTurnAnimation)
        assertTrue(compact.bookStyles)
        assertTrue(compact.matches(ReaderStylePreset.COMPACT))
        assertFalse(compact.matches(ReaderStylePreset.COMFORTABLE))
    }

    @Test
    fun `all quick presets stay within reader setting bounds`() {
        for (preset in ReaderStylePreset.entries) {
            val settings = ReaderSettings().withPreset(preset)
            assertTrue(settings.fontSizeSp in 12f..32f)
            assertTrue(settings.lineHeight in 1.2f..2f)
            assertTrue(settings.sideMarginDp in ReaderSettings.MIN_SIDE_MARGIN_DP..ReaderSettings.MAX_SIDE_MARGIN_DP)
            assertTrue(settings.matches(preset))
        }
    }
}
