package uz.phoneboost.app.features.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.phoneboost.app.data.apps.AppsRepository
import uz.phoneboost.app.domain.model.AppEntry

data class AppsUiState(val loading: Boolean = true, val apps: List<AppEntry> = emptyList())

/** App Manager va Privacy Check shu yerdan o‘qiydi. */
class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppsRepository(application.applicationContext)

    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val apps = runCatching { repository.load() }.getOrDefault(emptyList())
            _state.value = AppsUiState(loading = false, apps = apps)
        }
    }
}
