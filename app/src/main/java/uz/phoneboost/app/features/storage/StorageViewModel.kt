package uz.phoneboost.app.features.storage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.phoneboost.app.data.apps.AppsRepository
import uz.phoneboost.app.data.storage.MediaBreakdown
import uz.phoneboost.app.data.storage.StoragePermissions
import uz.phoneboost.app.data.storage.StorageRepository

data class StorageUiState(
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
    val media: MediaBreakdown? = null,
    val appsApkBytes: Long? = null
)

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val storageRepository = StorageRepository(context)
    private val appsRepository = AppsRepository(context)

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val granted = StoragePermissions.granted(context)
            _state.value = _state.value.copy(loading = true, permissionGranted = granted)
            val apps = runCatching { appsRepository.load() }.getOrDefault(emptyList())
            val apkTotal = apps.mapNotNull { it.apkSizeBytes }.sum().takeIf { apps.isNotEmpty() && it > 0 }
            val media = if (granted) storageRepository.scanMedia() else null
            _state.value = _state.value.copy(
                loading = false,
                permissionGranted = granted,
                media = media,
                appsApkBytes = apkTotal
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(permissionDenied = !granted)
        refresh()
    }
}
