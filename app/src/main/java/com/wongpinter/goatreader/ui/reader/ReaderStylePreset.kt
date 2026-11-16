package com.wongpinter.goatreader.ui.reader

import com.wongpinter.goatreader.data.PageMargins
import com.wongpinter.goatreader.data.ReaderSettings
import kotlin.math.abs

/** Quick typography choices; each action updates only this book's reading settings. */
internal enum class ReaderStylePreset(
    val fontSizeSp: Float,
    val lineHeight: Float,
    val sideMarginDp: Int,
) {
    COMPACT(fontSizeSp = 16f, lineHeight = 1.35f, sideMarginDp = 12),
    COMFORTABLE(fontSizeSp = 18f, lineHeight = 1.5f, sideMarginDp = 16),
    LARGE_TEXT(fontSizeSp = 22f, lineHeight = 1.65f, sideMarginDp = 20),
}

internal fun ReaderSettings.withPreset(preset: ReaderStylePreset): ReaderSettings = copy(
    fontSizeSp = preset.fontSizeSp,
    lineHeight = preset.lineHeight,
    pageMargins = PageMargins.NORMAL,
    pageMarginDp = preset.sideMarginDp,
)

internal fun ReaderSettings.matches(preset: ReaderStylePreset): Boolean =
    abs(fontSizeSp - preset.fontSizeSp) < 0.01f &&
        abs(lineHeight - preset.lineHeight) < 0.01f &&
        sideMarginDp == preset.sideMarginDp
