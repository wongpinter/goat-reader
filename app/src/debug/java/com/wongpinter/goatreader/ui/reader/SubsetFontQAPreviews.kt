package com.wongpinter.goatreader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.ReaderFont
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.groupCustomFonts
import com.wongpinter.goatreader.ui.theme.GoatReaderTheme
import com.wongpinter.goatreader.ui.theme.customFontFamily
import java.io.File

// Optional local font fixture for previews; kept outside application resources and backups.
private const val subsetQaFonts = ".artifacts/qa-fonts/embedded-subset"

@Preview(name = "Embedded EPUB subset · families", widthDp = 393, heightDp = 650)
@Composable
private fun EmbeddedSubsetFamiliesQAPreview() = EmbeddedSubsetDialogQA(AppTheme.WHITE)

@Preview(name = "Embedded EPUB subset · styles", widthDp = 393, heightDp = 750)
@Composable
private fun EmbeddedSubsetStylesQAPreview() = EmbeddedSubsetDialogQA(AppTheme.WHITE, styles = true)

@Preview(name = "Embedded EPUB subset · Beige styles", widthDp = 393, heightDp = 750)
@Composable
private fun EmbeddedSubsetStylesBeigeQAPreview() = EmbeddedSubsetDialogQA(AppTheme.SEPIA, styles = true)

@Preview(name = "Embedded EPUB subset · narrow large text", widthDp = 320, heightDp = 850, fontScale = 1.5f)
@Composable
private fun EmbeddedSubsetStylesNarrowQAPreview() = EmbeddedSubsetDialogQA(AppTheme.WHITE, styles = true)

@Composable
private fun EmbeddedSubsetDialogQA(theme: AppTheme, styles: Boolean = false) {
    val group = remember { groupCustomFonts(File(subsetQaFonts).listFiles().orEmpty().toList()).firstOrNull() }
    GoatReaderTheme(theme) {
        val colors = readerColors(theme)
        Box(Modifier.fillMaxSize().background(colors.background).padding(24.dp), contentAlignment = Alignment.Center) {
            if (group == null) {
                Text("Place extracted EPUB font files in .artifacts/qa-fonts/embedded-subset to render this preview.")
            } else {
                CustomFontDialogSurface(Modifier.widthIn(max = 345.dp)) {
                    CustomFontsContent(
                        colors = colors,
                        fonts = emptyList(),
                        embedded = listOf(group),
                        opened = group.takeIf { styles },
                        fromBook = true,
                        bodyFontPath = group.preview.path,
                        bodyFontAuto = true,
                    )
                }
            }
        }
    }
}

@Preview(name = "Embedded EPUB subset · font chip selection", widthDp = 393, heightDp = 430)
@Composable
private fun EmbeddedSubsetFontChipQAPreview() {
    val group = remember { groupCustomFonts(File(subsetQaFonts).listFiles().orEmpty().toList()).firstOrNull() }
    GoatReaderTheme(AppTheme.WHITE) {
        val colors = readerColors(AppTheme.WHITE)
        Column(Modifier.fillMaxSize().background(colors.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Publisher's formatting")
            ReaderFontPicker(
                settings = ReaderSettings(bookStyles = true),
                colors = colors,
                bookFontName = "Times new roman",
                bookFontPath = group?.preview?.path,
                bookFontFamily = group?.let { customFontFamily(it.preview.path, auto = true, familyPaths = it.faces.map { face -> face.path }.distinct()) },
                onSelect = {},
            )
            Text("Sans selected")
            ReaderFontPicker(ReaderSettings(font = ReaderFont.SANS), colors, onSelect = {})
            Text("Literata selected")
            ReaderFontPicker(ReaderSettings(font = ReaderFont.LITERATA), colors, onSelect = {})
        }
    }
}
