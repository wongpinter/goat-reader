package com.wongpinter.goatreader.debug

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wongpinter.goatreader.GoatReaderApp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.effectiveTheme
import com.wongpinter.goatreader.ui.reader.ReaderScreen
import com.wongpinter.goatreader.ui.theme.GoatReaderTheme

/** Debug-only host for the real reader, isolated from launcher/onboarding navigation. */
class ReaderOpeningTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Physical-device runs must keep receiving frames while the fixture
        // waits for background work. This debug host shows generated test text.
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        Log.i("GoatReaderPerf", "ui_harness_created")
        val app = application as GoatReaderApp
        setContent {
            val settings by app.settingsRepository.appSettings.collectAsStateWithLifecycle(AppSettings())
            GoatReaderTheme(
                theme = settings.effectiveTheme(isSystemInDarkTheme()),
                dynamicColor = settings.dynamicColor,
            ) {
                ReaderScreen(bookId = bookId, onBack = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "reader_test_book_id"
    }
}
