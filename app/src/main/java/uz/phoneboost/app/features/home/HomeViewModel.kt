package uz.phoneboost.app.features.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.phoneboost.app.data.device.DeviceStatusRepository
import uz.phoneboost.app.data.history.HistoryEntry
import uz.phoneboost.app.data.history.HistoryRepository
import uz.phoneboost.app.domain.HealthResult
import uz.phoneboost.app.domain.HealthScoreCalculator
import uz.phoneboost.app.domain.Recommendation
import uz.phoneboost.app.domain.RecommendationEngine
import uz.phoneboost.app.domain.SCAN_STEP_COUNT
import uz.phoneboost.app.domain.model.DeviceStatus

data class HomeUiState(
    val status: DeviceStatus? = null,
    val result: HealthResult? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val checkedAtMillis: Long? = null,
    val scanning: Boolean = false,
    val scanStep: Int = 0,
    val showResults: Boolean = false,
    /** Yakunlangan optimallashtirishlar soni (reklama qoidalari uchun). */
    val scanCount: Int = 0
) {
    fun withStatus(status: DeviceStatus): HomeUiState = copy(
        status = status,
        result = HealthScoreCalculator.calculate(status),
        recommendations = RecommendationEngine.analyze(status),
        checkedAtMillis = System.currentTimeMillis()
    )
}

/** Ilova bo‘yicha umumiy (Activity darajasidagi) holat: Bosh sahifa, Hisobot va Batareya shu yerdan o‘qiydi. */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceStatusRepository(application.applicationContext)
    private val history = HistoryRepository(application.applicationContext)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Animatsiyasiz, jimgina qayta o‘lchash (sahifa ochilganda). */
    fun refresh() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            val status = repository.read()
            _state.update { if (it.scanning) it else it.withStatus(status) }
        }
    }

    /** “HOZIR OPTIMALLASHTIRISH”: holatni qayta o‘lchab, tavsiyalarni tayyorlaydi. */
    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanStep = 0, showResults = false) }
            val status = repository.read()
            repeat(SCAN_STEP_COUNT) { index ->
                delay(350)
                _state.update { it.copy(scanStep = index + 1) }
            }
            delay(250)
            // Natija tarixga saqlanadi (faqat shu telefonda).
            val score = HealthScoreCalculator.calculate(status)?.score
            withContext(Dispatchers.IO) { runCatching { history.add(HistoryEntry.from(status, score, HistoryEntry.SOURCE_MANUAL)) } }
            _state.update { it.withStatus(status).copy(scanning = false, showResults = true, scanCount = it.scanCount + 1) }
        }
    }
}
