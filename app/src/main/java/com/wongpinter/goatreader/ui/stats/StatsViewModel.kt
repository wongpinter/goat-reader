package com.wongpinter.goatreader.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wongpinter.goatreader.GoatReaderApp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.BookRepository
import com.wongpinter.goatreader.data.ReadingStats
import com.wongpinter.goatreader.data.SettingsRepository
import com.wongpinter.goatreader.data.StatsRepository
import com.wongpinter.goatreader.data.model.Book
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    bookRepository: BookRepository,
) : ViewModel() {

    val stats: StateFlow<ReadingStats> = statsRepository.stats

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val books: StateFlow<List<Book>> = bookRepository.books

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateApp { it.copy(dailyGoalMinutes = minutes) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoatReaderApp
                StatsViewModel(app.statsRepository, app.settingsRepository, app.bookRepository)
            }
        }
    }
}
