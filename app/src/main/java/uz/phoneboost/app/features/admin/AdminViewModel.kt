package uz.phoneboost.app.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.phoneboost.app.BuildConfig
import uz.phoneboost.app.data.admin.AdminApi
import uz.phoneboost.app.data.admin.AdminSession
import uz.phoneboost.app.data.admin.AdminStats
import uz.phoneboost.app.data.admin.HttpAdminApi

data class AdminUi(
    val session: AdminSession? = null,
    val busy: Boolean = false,
    val error: Boolean = false,
    val days: Int = 7,
    val stats: AdminStats? = null,
    val logs: List<String> = emptyList()
)

class AdminViewModel : ViewModel() {
    private val api: AdminApi? =
        BuildConfig.ADMIN_API_URL.takeIf { it.startsWith("https://") }?.let { HttpAdminApi(it) }
    val configured: Boolean get() = api != null

    private val _ui = MutableStateFlow(AdminUi())
    val ui: StateFlow<AdminUi> = _ui.asStateFlow()

    fun login(email: String, password: String) {
        val a = api ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = false) }
            val r = a.login(email, password)
            val s = r.getOrNull()
            _ui.update { it.copy(session = s, busy = false, error = s == null) }
            if (s != null) reload()
        }
    }

    fun setDays(d: Int) { _ui.update { it.copy(days = d) }; reload() }

    fun reload() {
        val a = api ?: return
        val s = _ui.value.session ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = false) }
            val st = a.stats(s, _ui.value.days)
            val lg = a.logs(s)
            _ui.update { it.copy(stats = st.getOrNull(), logs = lg.getOrDefault(emptyList()), busy = false, error = st.isFailure) }
        }
    }

    fun setMaintenance(on: Boolean) = act { a, s -> a.setMaintenance(s, on) }
    fun announce(text: String) = act { a, s -> a.announce(s, text) }

    private fun act(block: suspend (AdminApi, AdminSession) -> Result<Unit>) {
        val a = api ?: return
        val s = _ui.value.session ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val ok = block(a, s).isSuccess
            _ui.update { it.copy(busy = false, error = !ok) }
            if (ok) reload()
        }
    }

    fun logout() { _ui.value = AdminUi() }
}
