package uz.phoneboost.app.ads

import android.app.Activity
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
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import uz.phoneboost.app.BuildConfig

/**
 * Reklama arxitekturasi. Qoidalar:
 *  - reklama faqat foydalanuvchi ishi tugagandan keyin (optimallashtirish natijasi) yoki o‘z xohishi bilan (Rewarded) chiqadi;
 *  - Interstitial: har 3-chi yakunlangan optimallashtirishda va oxirgisidan kamida 5 daqiqa o‘tgan bo‘lsa;
 *  - Banner: faqat sahifa oxirida, hech qaysi tugmani yopmaydi; yuklanmasa joy egallamaydi;
 *  - reklama yuklanmasa/xato bo‘lsa ilova odatdagidek ishlayveradi;
 *  - har ishga tushganda UMP consent yangilanadi; SDK va reklama so‘rovlari FAQAT canRequestAds()==true bo‘lgandan keyin;
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

    /** Reklama so‘rashga ruxsat bor-yo‘qligi (UMP). Banner shunga qarab chiziladi. */
    val canRequestAds = mutableStateOf(false)

    /** Foydalanuvchiga "Reklama maxfiylik sozlamalari" qatori ko‘rsatilishi kerakmi (UMP talabi). */
    val privacyOptionsRequired = mutableStateOf(false)

    private var consentInfo: ConsentInformation? = null

    /** Har ishga tushganda: consent yangilanadi -> kerak bo‘lsa forma -> keyin SDK. */
    fun init(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        val ctx = activity.applicationContext
        appContext = ctx
        val info = UserMessagingPlatform.getConsentInformation(ctx)
        consentInfo = info
        val params = ConsentRequestParameters.Builder().apply {
            // Faqat debug: EEA hududini sinash. Release'da bu blok umuman ishlamaydi.
            if (BuildConfig.DEBUG) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(ctx)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build()
                )
            }
        }.build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { afterConsent() }
            },
            { afterConsent() }
        )
        // Oldingi sessiyada consent olingan bo‘lsa, SDK ni darhol ishga tushirish mumkin.
        if (info.canRequestAds()) startSdk()
    }

    private fun afterConsent() {
        val info = consentInfo ?: return
        privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) startSdk()
    }

    /** Settings'dagi "Reklama maxfiylik sozlamalari" tugmasi. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { afterConsent() }
    }

    private fun startSdk() {
        val ctx = appContext ?: return
        if (started || consentInfo?.canRequestAds() != true) return
        started = true
        canRequestAds.value = true
        // Google tavsiyasi: initialize ni fon oqimida chaqirish (UI bloklanmasin).
        Thread {
            runCatching {
                MobileAds.initialize(ctx) {
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
        if (!started || interstitial != null || interstitialLoading) return
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
        if (!started || rewarded != null || rewardedLoading) return
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
