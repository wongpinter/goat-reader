package com.wongpinter.goatreader.ui.theme

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.BackupFrequency
import com.wongpinter.goatreader.data.model.BackupMode
import com.wongpinter.goatreader.ui.settings.ReadingSettingsPage
import com.wongpinter.goatreader.ui.settings.BackupHero
import com.wongpinter.goatreader.ui.settings.BackupScopeRow
import com.wongpinter.goatreader.ui.settings.BackupFolderRow
import com.wongpinter.goatreader.ui.settings.BackupViewModel
import com.wongpinter.goatreader.ui.settings.SettingsPageScaffold
import com.wongpinter.goatreader.ui.settings.SettingsCard
import com.wongpinter.goatreader.ui.settings.SettingsControlRow
import com.wongpinter.goatreader.ui.settings.SettingsDivider
import com.wongpinter.goatreader.ui.settings.SettingsSectionLabel
import com.wongpinter.goatreader.ui.settings.SettingsHelper

@Preview(name = "Light · reading settings", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun LightReadingSettingsPreview() {
    GoatReaderTheme(AppTheme.WHITE) {
        ReadingSettingsPage(AppSettings(theme = AppTheme.WHITE), onUpdate = {}, onBack = {})
    }
}

/** Actual backup components in the no-folder state, without accessing backup storage. */
@Preview(name = "Light · backup controls", widthDp = 393, heightDp = 852, showSystemUi = true)
@Composable
private fun LightBackupControlsPreview() {
    BackupControlsPreview(AppTheme.WHITE, BackupMode.FULL)
}

@Preview(name = "Midnight · backup · Data only", widthDp = 393, heightDp = 900, showSystemUi = true)
@Composable
private fun MidnightBackupControlsPreview() = BackupControlsPreview(AppTheme.OLED, BackupMode.DATA)

@Preview(name = "Midnight · backup · books · large text", widthDp = 360, heightDp = 900, fontScale = 1.5f)
@Composable
private fun MidnightBackupLargeTextPreview() = BackupControlsPreview(AppTheme.OLED, BackupMode.FULL)

@Preview(name = "Beige · backup controls", widthDp = 393, heightDp = 900, showSystemUi = true)
@Composable
private fun BeigeBackupControlsPreview() = BackupControlsPreview(AppTheme.SEPIA, BackupMode.DATA)

@Composable
private fun BackupControlsPreview(theme: AppTheme, mode: BackupMode) {
    GoatReaderTheme(theme) {
        SettingsPageScaffold(title = "Backup", onBack = {}) {
            BackupHero(
                state = BackupViewModel.State.Idle, lastBackupAt = null,
                summary = BackupViewModel.LibrarySummary(bookCount = 14, quoteCount = 2),
                enabled = true, onBackup = {}, onRestore = {},
            )
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("Back up now includes")
            SettingsCard {
                BackupScopeRow(mode == BackupMode.DATA, "Data only", "Reading Log with covers, Reading Queue, notes, progress and settings", "Small", true, {})
                BackupScopeRow(mode == BackupMode.FULL, "Data and books", "The same, plus every book file and cover", "~359 MB", true, {})
            }
            SettingsHelper("Automatic backups always use Data only, avoiding large background transfers.")
            Spacer(Modifier.height(24.dp))
            SettingsSectionLabel("Automatic backups")
            SettingsCard {
                BackupFolderRow(folder = null, enabled = true, onChange = {})
                SettingsDivider()
                SettingsControlRow(
                    icon = Icons.Rounded.Schedule, title = "How often", subtitle = "The 5 most recent backups are kept",
                    selected = BackupFrequency.OFF,
                    options = listOf(BackupFrequency.OFF to "Off", BackupFrequency.DAILY to "Daily", BackupFrequency.WEEKLY to "Weekly"),
                    onSelected = {}, enabled = false,
                )
            }
        }
    }
}
