package com.wongpinter.goatreader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wongpinter.goatreader.GoatReaderApp
import com.wongpinter.goatreader.data.AppSettings
import com.wongpinter.goatreader.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.updateApp(transform) }
    }

    /** External-app dialogs await persistence so a failed choice stays editable and is reported. */
    suspend fun saveExternalApp(transform: (AppSettings) -> AppSettings) {
        settingsRepository.updateApp(transform)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GoatReaderApp
                SettingsViewModel(app.settingsRepository)
            }
        }
    }
}
