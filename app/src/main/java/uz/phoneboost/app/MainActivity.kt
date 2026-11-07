package uz.phoneboost.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.core.designsystem.PhoneBoostTheme
import uz.phoneboost.app.core.designsystem.isDark
import uz.phoneboost.app.features.settings.SettingsViewModel
import uz.phoneboost.app.navigation.PhoneBoostApp

class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val current = mode
            if (current != null) {
                val dark = current.isDark()
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(
                            Color.argb(0xE6, 0xFF, 0xFF, 0xFF),
                            Color.argb(0x80, 0x1B, 0x1B, 0x1B)
                        ) { dark }
                    )
                    onDispose { }
                }
                PhoneBoostTheme(mode = current) {
                    PhoneBoostApp(settingsViewModel)
                }
            }
        }
    }
}
