package uz.phoneboost.app.features.update

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import uz.phoneboost.app.BuildConfig
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.core.designsystem.PhoneBoostButton
import uz.phoneboost.app.core.designsystem.PhoneBoostLoading
import uz.phoneboost.app.core.ui.FeaturePage
import uz.phoneboost.app.core.ui.InfoRow
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.core.util.openUrl
import uz.phoneboost.app.data.update.UpdateRepository
import uz.phoneboost.app.data.update.UpdateResult

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    val unavailable = stringResource(R.string.settings_unavailable)

    FeaturePage(R.string.upd_title, onBack) {
        SectionCard(R.string.upd_current) {
            InfoRow(stringResource(R.string.upd_version), BuildConfig.VERSION_NAME)
            InfoRow(stringResource(R.string.upd_code), BuildConfig.VERSION_CODE.toString())
        }
        PhoneBoostButton(stringResource(R.string.upd_check), enabled = !checking, onClick = {
            checking = true
            scope.launch {
                result = UpdateRepository(BuildConfig.UPDATE_URL).check(BuildConfig.VERSION_CODE)
                checking = false
            }
        })
        if (checking) PhoneBoostLoading()
        when (val r = result) {
            null -> Unit
            UpdateResult.NotConfigured -> NoteText(stringResource(R.string.upd_not_configured), color = BrandColors.Warn)
            UpdateResult.UpToDate -> NoteText(stringResource(R.string.upd_uptodate), color = BrandColors.Good)
            UpdateResult.Error -> NoteText(stringResource(R.string.upd_error), color = BrandColors.Bad)
            is UpdateResult.Available -> SectionCard(R.string.upd_available) {
                InfoRow(stringResource(R.string.upd_version), r.versionName)
                if (r.changelog.isNotBlank()) {
                    NoteText(stringResource(R.string.upd_changelog))
                    NoteText(r.changelog, color = BrandColors.TextPrimary)
                }
                PhoneBoostButton(stringResource(R.string.upd_action), enabled = r.url.isNotBlank(), onClick = {
                    if (!context.openUrl(r.url)) Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}
