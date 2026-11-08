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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.phoneboost.app.core.designsystem.PhoneBoostTheme
import uz.phoneboost.app.core.designsystem.isDark
import uz.phoneboost.app.features.home.HomeViewModel
import uz.phoneboost.app.features.onboarding.OnboardingScreen
import uz.phoneboost.app.features.onboarding.SplashScreen
import uz.phoneboost.app.features.settings.SettingsViewModel
import uz.phoneboost.app.navigation.PhoneBoostApp

class MainActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

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
                            Color.argb(0xF0, 0x04, 0x0A, 0x1E)
                        ) { dark }
                    )
                    onDispose { }
                }
                PhoneBoostTheme(mode = current) {
                    var stage by rememberSaveable { mutableIntStateOf(0) }
                    val prefs = remember { getSharedPreferences("onboarding", MODE_PRIVATE) }
                    when (stage) {
                        0 -> SplashScreen(onDone = { stage = if (prefs.getBoolean("done", false)) 2 else 1 })
                        1 -> OnboardingScreen(onFinish = {
                            prefs.edit().putBoolean("done", true).apply()
                            stage = 2
                        })
                        else -> PhoneBoostApp(settingsViewModel, homeViewModel)
                    }
                }
            }
        }
    }
}
