package com.wongpinter.goatreader.ui.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.AppTheme
import com.wongpinter.goatreader.data.LightThemeDefault
import com.wongpinter.goatreader.data.effectiveTheme
import com.wongpinter.goatreader.ui.theme.GoatReaderTheme

@Preview(name = "Welcome · Light", widthDp = 393, heightDp = 852, showSystemUi = true)
@Preview(name = "Welcome · full screen", widthDp = 393, heightDp = 1600)
@Preview(name = "Welcome · narrow", widthDp = 320, heightDp = 852)
@Preview(name = "Welcome · large text", widthDp = 360, heightDp = 1000, fontScale = 1.5f)
@Preview(name = "Welcome · landscape", widthDp = 852, heightDp = 393)
@Composable
internal fun WelcomeLight() = InteractiveWelcome(AppSettings(theme = AppTheme.WHITE, followSystemTheme = false))

@Preview(name = "Welcome · Beige", widthDp = 393, heightDp = 1600)
@Composable
internal fun WelcomeBeige() = InteractiveWelcome(AppSettings(theme = AppTheme.SEPIA, followSystemTheme = false))

@Preview(name = "Welcome · Midnight", widthDp = 393, heightDp = 1600)
@Composable
internal fun WelcomeMidnight() = InteractiveWelcome(AppSettings(theme = AppTheme.OLED, followSystemTheme = false))

@Preview(name = "Welcome · follows light system", widthDp = 393, heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun WelcomeSystemLight() = InteractiveWelcome(AppSettings())

@Preview(name = "Welcome · follows dark system · Beige default", widthDp = 393, heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun WelcomeSystemDark() = InteractiveWelcome(AppSettings(lightThemeDefault = LightThemeDefault.BEIGE))

@Preview(name = "Welcome · follows light system · Beige default", widthDp = 393, heightDp = 1600,
    uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun WelcomeSystemBeige() = InteractiveWelcome(AppSettings(lightThemeDefault = LightThemeDefault.BEIGE))

@Preview(name = "Welcome · Material You", widthDp = 393, heightDp = 1600, apiLevel = 36,
    uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun WelcomeMaterialYou() = InteractiveWelcome(AppSettings(dynamicColor = true))

@Preview(name = "Welcome · Android 8", widthDp = 393, heightDp = 1600, apiLevel = 26)
@Composable
internal fun WelcomeLegacy() = InteractiveWelcome(AppSettings())

@Composable
private fun InteractiveWelcome(initialSettings: AppSettings) {
    var settings by remember { mutableStateOf(initialSettings) }
    GoatReaderTheme(settings.effectiveTheme(isSystemInDarkTheme()), settings.dynamicColor) {
        WelcomeScreen(settings, onSettingsChange = { settings = it }, onContinue = {})
    }
}

@Preview(name = "Welcome · saving", widthDp = 393, heightDp = 1600)
@Composable
internal fun WelcomeSaving() {
    GoatReaderTheme(AppTheme.WHITE) {
        WelcomeScreen(AppSettings(followSystemTheme = false), {}, {}, isSaving = true)
    }
}

@Preview(name = "Welcome · retry", widthDp = 393, heightDp = 1600)
@Composable
internal fun WelcomeError() {
    GoatReaderTheme(AppTheme.WHITE) {
        WelcomeScreen(
            AppSettings(followSystemTheme = false), {}, {},
            errorMessage = "Couldn’t save your preferences. Please try again.",
        )
    }
}
