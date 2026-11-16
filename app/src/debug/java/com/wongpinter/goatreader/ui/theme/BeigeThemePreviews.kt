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
import com.wongpinter.goatreader.data.PageTurnAnimation
import com.wongpinter.goatreader.data.ReaderSettings
import com.wongpinter.goatreader.data.ReadingMode
import com.wongpinter.goatreader.ui.reader.ReaderSettingsControls
import com.wongpinter.goatreader.ui.reader.readerColors
import com.wongpinter.goatreader.ui.settings.SettingsEntryPage

/** Live controls with memory-only settings, including the screenshot's paged mode. */
@Preview(name = "Beige · reader settings", widthDp = 393, heightDp = 882)
@Preview(name = "Beige · reader settings · large text", widthDp = 360, heightDp = 800, fontScale = 1.5f)
@Composable
private fun BeigeReaderSettingsPreview() = GoatReaderTheme(AppTheme.SEPIA) {
    val colors = readerColors(AppTheme.SEPIA)
    Surface(color = colors.chrome, contentColor = colors.onChrome) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            ReaderSettingsControls(
                settings = ReaderSettings(readingMode = ReadingMode.PAGES, pageTurnAnimation = PageTurnAnimation.SLIDE),
                appSettings = AppSettings(theme = AppTheme.SEPIA, followSystemTheme = false),
                colors = colors, brightness = null,
                onUpdate = {}, onUpdateApp = {}, onBrightnessChange = {},
            )
        }
    }
}

@Preview(name = "Beige · app settings", widthDp = 393, heightDp = 882, showSystemUi = true)
@Composable
private fun BeigeSettingsPreview() = GoatReaderTheme(AppTheme.SEPIA) {
    SettingsEntryPage(
        settings = AppSettings(theme = AppTheme.SEPIA, followSystemTheme = false),
        backupFrequency = BackupFrequency.OFF, lastBackupAt = null,
        onBack = {}, onOpenTheme = {}, onOpenReading = {}, onOpenPrivacy = {},
        onOpenBackup = {}, onOpenAbout = {},
    )
}
