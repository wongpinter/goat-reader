package com.wongpinter.goatreader.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.BackupFrequency
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.BookFormat
import com.wongpinter.goatreader.ui.library.BookInfoContent
import com.wongpinter.goatreader.ui.library.BookInfoState
import com.wongpinter.goatreader.ui.reader.ReaderSettingsControls
import com.wongpinter.goatreader.ui.reader.readerColors
import com.wongpinter.goatreader.ui.settings.SettingsEntryPage

/** Actual screen components with in-memory data; no repositories or device writes. */
@Preview(name = "Light · settings", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun LightSettingsPreview() {
    GoatReaderTheme(AppTheme.WHITE) {
        SettingsEntryPage(
            settings = AppSettings(theme = AppTheme.WHITE, followSystemTheme = false),
            backupFrequency = BackupFrequency.OFF,
            lastBackupAt = null,
            onBack = {}, onOpenTheme = {}, onOpenReading = {},
            onOpenPrivacy = {}, onOpenBackup = {}, onOpenAbout = {},
        )
    }
}

@Preview(name = "Light · book info", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun LightBookInfoPreview() {
    GoatReaderTheme(AppTheme.WHITE) {
        Surface {
            BookInfoContent(
                state = BookInfoState(
                    book = Book(
                        id = "palette-preview", title = "The Secret Garden", author = "Frances Hodgson Burnett",
                        format = BookFormat.EPUB, fileName = "preview.epub", addedAtMillis = 0,
                        publisher = "William Heinemann", year = "1911", language = "en",
                        description = "A story of friendship, curiosity, and a garden brought back to life.",
                        readingSeconds = 2400,
                    ),
                    loading = false, fileLoading = false, fileAvailable = true, sizeBytes = 601088,
                ),
                onBack = {}, onEdit = {}, onShare = {},
            )
        }
    }
}

@Preview(name = "Light · reader controls", widthDp = 393, heightDp = 852)
@Composable
private fun LightReaderControlsPreview() {
    GoatReaderTheme(AppTheme.WHITE) {
        val colors = readerColors(AppTheme.WHITE)
        Surface(color = colors.chrome, contentColor = colors.onChrome) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ReaderSettingsControls(
                    settings = ReaderSettings(), appSettings = AppSettings(theme = AppTheme.WHITE),
                    colors = colors, brightness = null,
                    onUpdate = {}, onUpdateApp = {}, onBrightnessChange = {},
                )
            }
        }
    }
}
