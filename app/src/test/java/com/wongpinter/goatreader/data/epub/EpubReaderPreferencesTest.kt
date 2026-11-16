package com.wongpinter.goatreader.data.epub

import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.ReaderFont
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReaderTextAlignment
import com.wongpinter.goatreader.data.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

class EpubReaderPreferencesTest {
    @Test
    fun mapsReaderSettingsToReadiumPreferences() {
        val preferences = EpubReaderPreferences.from(
            ReaderSettings(
                fontSizeSp = 24f,
                lineHeight = 1.8f,
                font = ReaderFont.SANS,
                justify = true,
                textAlignment = ReaderTextAlignment.JUSTIFY,
                hyphenation = false,
                bookStyles = true,
                pageMarginDp = 17,
                readingMode = ReadingMode.SCROLL,
            ),
            AppTheme.SEPIA,
        )

        assertEquals(FontFamily.SANS_SERIF, preferences.fontFamily)
        assertEquals(24.0 / 18.0, preferences.fontSize!!, 0.0001)
        assertEquals(1.8, preferences.lineHeight!!, 0.0001)
        assertEquals(17.0 / 34.0, preferences.pageMargins!!, 0.0001)
        assertEquals(true, preferences.hyphens)
        assertEquals(false, preferences.publisherStyles)
        assertEquals(true, preferences.scroll)
        assertEquals(TextAlign.JUSTIFY, preferences.textAlign)
        assertEquals(Theme.SEPIA, preferences.theme)
    }

    @Test
    fun mapsBundledFontPreferenceToAvailableSystemSerif() {
        val preferences = EpubReaderPreferences.from(ReaderSettings(font = ReaderFont.LITERATA), AppTheme.WHITE)

        assertEquals(FontFamily.SERIF, preferences.fontFamily)
        assertEquals(TextAlign.START, preferences.textAlign)
    }

    @Test
    fun mapsAtkinsonToTheDeclaredCustomReadiumFamily() {
        val preferences = EpubReaderPreferences.from(ReaderSettings(font = ReaderFont.ATKINSON), AppTheme.WHITE)

        assertEquals(FontFamily("Atkinson Hyperlegible Next"), preferences.fontFamily)
    }

    @Test
    fun mapsAndBoundsDefaultTypographyAndDarkTheme() {
        val preferences = EpubReaderPreferences.from(
            ReaderSettings(font = ReaderFont.SERIF, fontSizeSp = 200f, lineHeight = 4f, pageMarginDp = 80),
            AppTheme.OLED,
        )

        assertEquals(FontFamily.SERIF, preferences.fontFamily)
        assertEquals(5.0, preferences.fontSize!!, 0.0001)
        assertEquals(2.0, preferences.lineHeight!!, 0.0001)
        assertEquals(48.0 / 34.0, preferences.pageMargins!!, 0.0001)
        assertEquals(Theme.DARK, preferences.theme)
    }
}
