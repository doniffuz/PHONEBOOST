package uz.phoneboost.app.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.phoneboost.app.R
import uz.phoneboost.app.core.ui.NoteText
import uz.phoneboost.app.core.ui.SectionCard
import uz.phoneboost.app.data.remote.Announcement
import uz.phoneboost.app.data.remote.BackendRepository

/** Backend'dagi faol e'lonlar. Yo'q yoki backend sozlanmagan bo'lsa — hech narsa chizilmaydi. */
@Composable
fun AnnouncementsCard() {
    var items by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    LaunchedEffect(Unit) { items = BackendRepository.announcements().take(3) }
    if (items.isEmpty()) return
    SectionCard(R.string.announcements_title, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { a ->
                if (a.title.isNotBlank()) Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                NoteText(a.message)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}
