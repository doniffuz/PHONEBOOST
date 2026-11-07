package uz.phoneboost.app.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import uz.phoneboost.app.BuildConfig

/** Banner: yuklanmaguncha 0 balandlikda turadi (bo‘sh joy qoldirmaydi, hech narsani bosmaydi). */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!BuildConfig.ADS_ENABLED) return
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.AD_BANNER_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loaded = false
                }
            }
            runCatching { loadAd(AdRequest.Builder().build()) }
        }
    }
    DisposableEffect(adView) {
        onDispose { runCatching { adView.destroy() } }
    }
    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth().height(if (loaded) 50.dp else 0.dp)
    )
}
