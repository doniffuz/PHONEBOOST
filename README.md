# PHONEBOOST
Clean • Optimize • Boost

## GitHub Actions orqali build (asosiy usul)
`main` ga push, "Run workflow" yoki Release yaratilganda `.github/workflows/build.yml` ishlaydi va Artifacts / Release ga yuklaydi:
`PHONEBOOST_DEBUG.apk` (telefonga o'rnatiladi), `PHONEBOOST_RELEASE[_UNSIGNED].apk/.aab`, `BUILD_REPORT.txt`, `BUILD_LOG.txt`.
Keystore Secrets berilmasa release fayllar nomida `_UNSIGNED` bo'ladi va telefonga o'rnatilmaydi (faqat Debug APK o'rnatiladi).

## Termux'da build
```
pkg install openjdk-17 gradle aapt2
export ANDROID_HOME=$HOME/android-sdk   # platforms;android-35 va build-tools kerak
./build.sh                              # debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./build.sh assembleRelease              # release APK -> app/build/outputs/apk/release/
./build.sh bundleRelease                # Play Market AAB -> app/build/outputs/bundle/release/
```

## Release sozlamalari (kodda emas, ~/.gradle/gradle.properties ichida)
```
PB_ADMOB_APP_ID=...            # TODO: haqiqiy AdMob ID kerak
PB_AD_BANNER_ID=...
PB_AD_INTERSTITIAL_ID=...
PB_AD_REWARDED_ID=...
PB_DONATION_URL=...            # ixtiyoriy; bo'sh = tugma o'chiq
PB_CONTACT_EMAIL=...           # ixtiyoriy; bo'sh = tugma o'chiq
PB_PRIVACY_POLICY_URL=...      # TODO: Play Market uchun majburiy
```
- Debug build: Google TEST reklama ID lari.
- Release build: 4 ta AdMob ID ham berilmasa reklama **o'chirilgan** (SDK ishga tushmaydi). Test ID release'da ishlatilmaydi.
  (Manifestdagi APPLICATION_ID maydoni SDK crash qilmasligi uchun to'ldiriladi, lekin reklama chiqmaydi.)

## Imzolash
`keystore.properties.example` ni `keystore.properties` ga nusxalab to'ldiring (gitignore'da, kodga yozilmaydi).
Kalit yaratish: `keytool -genkeypair -v -keystore release.jks -alias phoneboost -keyalg RSA -keysize 2048 -validity 10000`
`keystore.properties` bo'lmasa release imzosiz chiqadi (Play Market/o'rnatish uchun yaroqsiz).

## TOP 10
`app/src/main/assets/supporters.json`: `[{"name":"Ali","amount":50000,"anonymous":false}]`

## Tillar
`res/values` (O'zbek, default), `res/values-ru`, `res/values-en` — kalitlar bir xil.

## Release konfiguratsiyasi (GitHub)
**Secrets:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (imzo); ixtiyoriy `PB_ADMOB_APP_ID`, `PB_AD_BANNER_ID`, `PB_AD_INTERSTITIAL_ID`, `PB_AD_REWARDED_ID` (to'rttasi birga berilmasa reklama release'da o'chiq).
**Variables:** `PB_SUPPORT_URL` (sayt URL), `PB_PRIVACY_POLICY_URL` (sayt URL + `#privacy`).
Reklama yoqilganda Google UMP consent oynasi har ishga tushganda yangilanadi; AdMob konsolida GDPR xabarini (Privacy & messaging) sozlash kerak.

## Animatsiya tezligi (Speed) va WRITE_SECURE_SETTINGS
Uchala animatsiya qiymatini (Window/Transition/Animator) 0x–2x ga o'zgartirish uchun ilovaga maxsus ruxsat kerak. Oddiy ilova uni o'zi ololmaydi; foydalanuvchi bir marta ADB orqali beradi:
`adb shell pm grant uz.phoneboost.app android.permission.WRITE_SECURE_SETTINGS`
Ruxsat yo'q bo'lsa tugmalar o'chiq turadi va hech narsa o'zgarmaydi. Asl qiymatlar saqlanadi, "Avvalgi qiymatlarni qaytarish" bilan tiklanadi.
Google Play: bu ruxsatni e'lon qilish siyosat bo'yicha ko'rib chiqilishi mumkin. Rad etilsa, manifestdan olib tashlansa, ilova Developer options yo'riqnomasiga qaytadi.
