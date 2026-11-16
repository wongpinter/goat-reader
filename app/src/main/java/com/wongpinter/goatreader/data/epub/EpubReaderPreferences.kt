@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.wongpinter.goatreader.data.epub

import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.ReaderFont
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReaderTextAlignment
import com.wongpinter.goatreader.data.ReadingMode
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

/** Maps Goat Reader's saved per-book controls to Readium CSS preferences. */
internal object EpubReaderPreferences {
    private const val DEFAULT_FONT_SIZE_SP = 18.0
    private const val DEFAULT_SIDE_MARGIN_DP = 34.0

    fun from(settings: ReaderSettings, theme: AppTheme): EpubPreferences = EpubPreferences(
        fontFamily = settings.font.readiumFamily,
        fontSize = (settings.fontSizeSp.toDouble() / DEFAULT_FONT_SIZE_SP).coerceIn(0.1, 5.0),
        lineHeight = settings.lineHeight.coerceIn(1.0f, 2.0f).toDouble(),
        pageMargins = (settings.sideMarginDp / DEFAULT_SIDE_MARGIN_DP).coerceIn(0.0, 4.0),
        hyphens = true,
        publisherStyles = false,
        scroll = settings.readingMode == ReadingMode.SCROLL,
        textAlign = when (settings.textAlignment) {
            ReaderTextAlignment.START -> TextAlign.START
            ReaderTextAlignment.JUSTIFY -> TextAlign.JUSTIFY
        },
        theme = when (theme) {
            AppTheme.WHITE -> Theme.LIGHT
            AppTheme.SEPIA -> Theme.SEPIA
            AppTheme.OLED -> Theme.DARK
        },
    )

    // The bundled Atkinson font is declared in the navigator configuration and served
    // through Readium's authorized asset path; user-imported files remain reader-local.
    private val ReaderFont.readiumFamily: FontFamily
        get() = when (this) {
            ReaderFont.LITERATA, ReaderFont.SERIF, ReaderFont.CUSTOM -> FontFamily.SERIF
            ReaderFont.SANS -> FontFamily.SANS_SERIF
            ReaderFont.ATKINSON -> FontFamily("Atkinson Hyperlegible Next")
        }
}
