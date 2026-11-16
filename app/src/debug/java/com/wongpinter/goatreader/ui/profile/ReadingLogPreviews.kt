package com.wongpinter.goatreader.ui.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.model.ReadingLogDate
import com.wongpinter.goatreader.data.model.ReadingLogEntry
import com.wongpinter.goatreader.data.model.ReadingLogSource
import com.wongpinter.goatreader.data.model.ReadingLogStatus
import com.wongpinter.goatreader.ui.library.BookInfoContent
import com.wongpinter.goatreader.ui.library.BookInfoState
import com.wongpinter.goatreader.data.model.Book
import com.wongpinter.goatreader.data.model.BookFormat
import com.wongpinter.goatreader.ui.theme.GoatReaderTheme
import com.wongpinter.goatreader.ui.theme.floatingSurface

private val previewLog = listOf(
    ReadingLogEntry("dune", "Dune", "Frank Herbert", "The Dune Chronicles", ReadingLogSource.PAPER,
        startedOn = ReadingLogDate(2026, 9, 10), finishedOn = ReadingLogDate(2026, 10, 2), ratingHalfStars = 9,
        review = "A world that feels much larger than one book."),
    ReadingLogEntry("piranesi", "Piranesi", "Susanna Clarke", source = ReadingLogSource.GOATREADER,
        status = ReadingLogStatus.IN_PROGRESS, startedOn = ReadingLogDate(2026, 9), readingSeconds = 8400),
    ReadingLogEntry("hitchhiker", "The Hitchhiker’s Guide to the Galaxy", "Douglas Adams", source = ReadingLogSource.EXTERNAL,
        startedOn = ReadingLogDate(2016), finishedOn = ReadingLogDate(2016), ratingHalfStars = 8),
    ReadingLogEntry("remembered", "A book from childhood", source = ReadingLogSource.PAPER),
)

@Preview(name = "Reading Log · Light 360", widthDp = 360, heightDp = 800, showSystemUi = true)
@Composable private fun LogLightPreview() = LogPreview(AppTheme.WHITE)

@Preview(name = "Reading Log · Midnight", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable private fun LogDarkPreview() = LogPreview(AppTheme.OLED)

@Preview(name = "Reading Log · Manual / large text", widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable private fun LogLargePreview() = LogPreview(AppTheme.WHITE, ReadingLogSort.MANUAL)

@Preview(name = "Reading Log · Editor short viewport", widthDp = 360, heightDp = 420)
@Composable private fun LogEditorPreview() {
    GoatReaderTheme(AppTheme.WHITE) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.floatingSurface) {
            ReadingLogEditorForm(ReadingLogEditor(previewLog.first(), previewLog.first()), false, null, ReadingLogActions(), {})
        }
    }
}

@Preview(name = "Reading Log · Shared book info", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable private fun LogInfoPreview() {
    val entry = previewLog.first()
    GoatReaderTheme(AppTheme.WHITE) {
        BookInfoContent(BookInfoState(book = Book(entry.id, entry.title, entry.author, BookFormat.EPUB, addedAtMillis = 0,
            series = entry.series), loading = false, fileLoading = false, readingLogEntry = entry, logOnly = true), {}, {}, {})
    }
}

@Composable private fun LogPreview(theme: AppTheme, sort: ReadingLogSort = ReadingLogSort.FINISHED_NEWEST) {
    GoatReaderTheme(theme) {
        ReadingLogContent(previewLog, true, false, null, sort, remember { SnackbarHostState() })
    }
}
