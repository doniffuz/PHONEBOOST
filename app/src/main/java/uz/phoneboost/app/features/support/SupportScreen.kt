package uz.phoneboost.app.features.support

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.phoneboost.app.R
import uz.phoneboost.app.core.AppConfig
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.PrimaryButton
import uz.phoneboost.app.core.ui.SecondaryButton
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.ui.findActivity
import uz.phoneboost.app.core.util.openUrl
import uz.phoneboost.app.data.Supporter
import uz.phoneboost.app.data.SupportersRepository
import java.text.NumberFormat
import java.util.Locale

class SupportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SupportersRepository(application.applicationContext)
    private val _top = MutableStateFlow<List<Supporter>>(emptyList())
    val top: StateFlow<List<Supporter>> = _top.asStateFlow()

    init {
        viewModelScope.launch { _top.value = repository.top10() }
    }
}

@Composable
fun SupportScreen(viewModel: SupportViewModel = viewModel()) {
    val context = LocalContext.current
    val top by viewModel.top.collectAsStateWithLifecycle()
    val unavailable = stringResource(R.string.settings_unavailable)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.nav_support),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            SectionCard(R.string.support_about_project) {
                InfoRow(stringResource(R.string.about_developer), stringResource(R.string.dev_name))
                InfoRow(stringResource(R.string.about_owner), stringResource(R.string.owner_name))
                NoteText(stringResource(R.string.support_voluntary))
            }

            SectionCard(R.string.support_how) {
                NoteText(stringResource(R.string.support_free_note))
                if (AppConfig.SUPPORT_URL.isNotBlank()) {
                    PrimaryButton(stringResource(R.string.support_site), onClick = {
                        if (!context.openUrl(AppConfig.SUPPORT_URL)) {
                            Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                        }
                    })
                } else {
                    NoteText(stringResource(R.string.support_site_not_set))
                }
                NoteText(stringResource(R.string.support_anonymous_note))
            }

            DonationSection()

            SectionCard(R.string.support_top10) {
                if (top.isEmpty()) {
                    NoteText(stringResource(R.string.support_top_empty))
                } else {
                    val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
                    top.forEachIndexed { index, s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${index + 1}. " + (s.name ?: stringResource(R.string.support_anonymous)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                nf.format(s.amount) + " " + stringResource(R.string.support_currency),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
