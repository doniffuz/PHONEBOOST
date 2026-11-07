package uz.phoneboost.app.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.phoneboost.app.data.device.DeviceStatusRepository
import uz.phoneboost.app.domain.HealthResult
import uz.phoneboost.app.domain.HealthScoreCalculator
import uz.phoneboost.app.domain.model.DeviceStatus

data class HomeUiState(
    val status: DeviceStatus? = null,
    val result: HealthResult? = null,
    val scanning: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceStatusRepository(application.applicationContext)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val status = repository.read()
            _state.value = HomeUiState(status, HealthScoreCalculator.calculate(status), false)
        }
    }

    /** Telefon holatini qayta o‘lchaydi (haqiqiy ma’lumot). */
    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true) }
            val status = repository.read()
            delay(1500)
            _state.value = HomeUiState(status, HealthScoreCalculator.calculate(status), false)
        }
    }
}
