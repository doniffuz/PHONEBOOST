package uz.phoneboost.app.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import uz.phoneboost.app.BuildConfig

/**
 * Reklama arxitekturasi. Qoidalar:
 *  - reklama faqat foydalanuvchi ishi tugagandan keyin (optimallashtirish natijasi) yoki o‘z xohishi bilan (Rewarded) chiqadi;
 *  - Interstitial: har 3-chi yakunlangan optimallashtirishda va oxirgisidan kamida 5 daqiqa o‘tgan bo‘lsa;
 *  - Banner: faqat sahifa oxirida, hech qaysi tugmani yopmaydi; yuklanmasa joy egallamaydi;
 *  - reklama yuklanmasa/xato bo‘lsa ilova odatdagidek ishlayveradi;
 *  - ADS_ENABLED=false (release'da haqiqiy ID berilmagan) bo‘lsa SDK umuman ishga tushmaydi.
 * ID lar BuildConfig dan olinadi (debug = Google test ID; release = gradle property yoki test ID).
 */
object Ads {
    private const val INTERSTITIAL_EVERY_N_SCANS = 3
    private const val INTERSTITIAL_MIN_GAP_MS = 5 * 60 * 1000L

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var appContext: Context? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var completedScans = 0
    private var lastInterstitialAt = 0L
    private var lastScanId = 0

    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    /** Compose o‘qiydi: Rewarded tugmasi faqat reklama tayyor bo‘lganda ko‘rsatiladi. */
    val rewardedReady = mutableStateOf(false)

    fun init(app: Application) {
        if (started || !BuildConfig.ADS_ENABLED) return
        started = true
        appContext = app.applicationContext
        // Google tavsiyasi: initialize ni fon oqimida chaqirish (UI bloklanmasin).
        Thread {
            runCatching {
                MobileAds.initialize(app) {
                    main.post {
                        loadInterstitial()
                        loadRewarded()
                    }
                }
            }
        }.start()
    }

    // ---- Interstitial ----

    private fun loadInterstitial() {
        val ctx = appContext ?: return
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        runCatching {
            InterstitialAd.load(
                ctx,
                BuildConfig.AD_INTERSTITIAL_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        interstitialLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitial = null
                        interstitialLoading = false
                    }
                }
            )
        }.onFailure { interstitialLoading = false }
    }

    /** Optimallashtirish natijasi ko‘rsatilganda chaqiriladi. Qoidalarga mos kelmasa hech narsa qilmaydi. */
    fun onOptimizationCompleted(activity: Activity, scanId: Int) {
        // Ekran aylantirilganda bir xil natija ikki marta sanalmasin.
        if (scanId == lastScanId) return
        lastScanId = scanId
        completedScans++
        val now = System.currentTimeMillis()
        val due = completedScans % INTERSTITIAL_EVERY_N_SCANS == 0
        val gapOk = lastInterstitialAt == 0L || now - lastInterstitialAt >= INTERSTITIAL_MIN_GAP_MS
        val ad = interstitial
        if (!due || !gapOk || ad == null || activity.isFinishing) {
            loadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                loadInterstitial()
            }
        }
        lastInterstitialAt = now
        runCatching { ad.show(activity) }.onFailure { interstitial = null }
    }

    // ---- Rewarded (ixtiyoriy qo‘llab-quvvatlash) ----

    private fun loadRewarded() {
        val ctx = appContext ?: return
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        runCatching {
            RewardedAd.load(
                ctx,
                BuildConfig.AD_REWARDED_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewarded = ad
                        rewardedLoading = false
                        rewardedReady.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewarded = null
                        rewardedLoading = false
                        rewardedReady.value = false
                    }
                }
            )
        }.onFailure { rewardedLoading = false }
    }

    /** Foydalanuvchi ixtiyoriy ravishda reklama ko‘radi. Hech qanday funksiya buning evaziga qulflanmaydi. */
    fun showRewarded(activity: Activity, onFinished: (earned: Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null || activity.isFinishing) {
            loadRewarded()
            onFinished(false)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                rewardedReady.value = false
                loadRewarded()
                onFinished(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                rewardedReady.value = false
                loadRewarded()
                onFinished(false)
            }
        }
        runCatching { ad.show(activity) { earned = true } }.onFailure {
            rewarded = null
            rewardedReady.value = false
            onFinished(false)
        }
    }
}
