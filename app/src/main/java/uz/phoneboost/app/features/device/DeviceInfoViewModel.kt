package uz.phoneboost.app.features.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.phoneboost.app.data.device.DeviceInfoRepository
import uz.phoneboost.app.domain.model.DeviceInfo

class DeviceInfoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceInfoRepository(application.applicationContext)

    private val _info = MutableStateFlow<DeviceInfo?>(null)
    val info: StateFlow<DeviceInfo?> = _info.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** true = o‘qish tugadi (info null bo‘lsa — Android ma’lumot bermadi). */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { repository.read() }.getOrNull() }
            _info.value = result
            _loaded.value = true
        }
    }
}
