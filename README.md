# PHONEBOOST MAX
Telefoningiz uchun aqlli optimizatsiya. Dasturchi: Donyor Abdullayev. Reklama yo‘q.

## GitHub Actions orqali build (asosiy usul)
`main` ga push, "Run workflow" yoki Release yaratilganda `.github/workflows/build.yml` ishlaydi va Artifacts / Release ga yuklaydi:
`PHONEBOOST-debug.apk` (telefonga o'rnatiladi), `PHONEBOOST-release[_UNSIGNED].apk/.aab`, `BUILD_REPORT.txt`, `BUILD_LOG.txt`.
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
PB_DONATION_URL=...            # ixtiyoriy; bo'sh = tugma o'chiq
PB_CONTACT_EMAIL=...           # ixtiyoriy; bo'sh = tugma o'chiq
PB_PRIVACY_POLICY_URL=...      # TODO: Play Market uchun majburiy
PB_DONATION_CONFIG_URL=https://...  # HTTPS JSON {"bank":"..","holder":"..","card":".."}; karta APK/repo ichida YO'Q, faqat serverda
PB_UPDATE_URL=https://...           # JSON {"versionCode":5,"versionName":"0.5.0","changelog":"..","url":"https://.."}
PB_ADMIN_API_URL=https://...        # Admin backend; bo'sh bo'lsa Admin Panel "Backend connection required" ko'rsatadi
```
- Ilovada reklama YO‘Q (AdMob/UMP butunlay olib tashlangan).

## Imzolash
`keystore.properties.example` ni `keystore.properties` ga nusxalab to'ldiring (gitignore'da, kodga yozilmaydi).
Kalit yaratish: `keytool -genkeypair -v -keystore release.jks -alias phoneboost -keyalg RSA -keysize 2048 -validity 10000`
`keystore.properties` bo'lmasa release imzosiz chiqadi (Play Market/o'rnatish uchun yaroqsiz).

## TOP 10
`app/src/main/assets/supporters.json`: `[{"name":"Ali","amount":50000,"anonymous":false}]`

## Tillar
`res/values` (O'zbek, default), `res/values-ru`, `res/values-en` — kalitlar bir xil.

## Release konfiguratsiyasi (GitHub)
**Secrets:** `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (imzo).
**Variables:** `PB_SUPPORT_URL`, `PB_PRIVACY_POLICY_URL` (sayt URL + `#privacy`), `PB_DONATION_URL`, `PB_DONATION_CONFIG_URL`, `PB_UPDATE_URL`, `PB_ADMIN_API_URL`.

## Xavfsizlik
- Karta raqami, admin parol/secret kodda, README da va APK ichida yo'q. Karta faqat HTTPS config orqali olinadi, xotirada turadi, loglanmaydi.
- To'lov muvaffaqiyati ilova tomonidan hech qachon taxmin qilinmaydi.
- Admin autentifikatsiyasi serverda; token faqat xotirada.

## Ma'lum cheklovlar
- Animatsiya tezligi / Game Boost animatsiya o'zgartirishi uchun ADB: `adb shell pm grant uz.phoneboost.app android.permission.WRITE_SECURE_SETTINGS`.
- Android boshqa ilovalarni yopish, FPS oshirish, boshqa ilovalar keshini tozalashga ruxsat bermaydi: ilova bularni qilmaydi.
- Admin Panel va donation statistikasi uchun backend alohida kerak (bu repoda yo'q).
- Gradle wrapper birinchi CI ishida avtomatik yaratiladi.

## Animatsiya tezligi (Speed) va WRITE_SECURE_SETTINGS
Uchala animatsiya qiymatini (Window/Transition/Animator) 0x–2x ga o'zgartirish uchun ilovaga maxsus ruxsat kerak. Oddiy ilova uni o'zi ololmaydi; foydalanuvchi bir marta ADB orqali beradi:
`adb shell pm grant uz.phoneboost.app android.permission.WRITE_SECURE_SETTINGS`
Ruxsat yo'q bo'lsa tugmalar o'chiq turadi va hech narsa o'zgarmaydi. Asl qiymatlar saqlanadi, "Avvalgi qiymatlarni qaytarish" bilan tiklanadi.
Google Play: bu ruxsatni e'lon qilish siyosat bo'yicha ko'rib chiqilishi mumkin. Rad etilsa, manifestdan olib tashlansa, ilova Developer options yo'riqnomasiga qaytadi.

## Holat (build tekshiruvi)
- Gradle wrapper (8.9) repoga qo'shilgan; CI `./gradlew` bilan build qiladi.
- Kod bu muhitda KOMPILYATSIYA QILINMAGAN (tarmoq yo'q): BUILD NOT VERIFIED. Haqiqiy natija GitHub Actions `BUILD_REPORT.txt` da.
- `QUERY_ALL_PACKAGES` ishlatilmaydi (faqat `<queries>` orqali launcher ilovalari ko'rinadi).

## Backend (Supabase)
Loyiha: `bmyvvwyfgswjgktlsqlu`. Fayllar: `supabase/migrations/` (11 jadval, RLS yoqilgan, policy yo'q), `supabase/functions/api/` (Edge Function).
Deploy: GitHub Actions "Deploy Supabase backend" (`supabase.yml`). Kerakli **Secrets**: `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` (ixtiyoriy: `ADMIN_JWT_SECRET`, bo'lmasa avtomatik yaratiladi).
Endpointlar (`https://bmyvvwyfgswjgktlsqlu.supabase.co/functions/v1/api`): `/admin/login|stats|users|optimization|game-boost|diagnostics|donations|errors|version|announcements|maintenance|settings` (token bilan), ochiq: `/update`, `/donation-config`, `/status`, `/ingest`, `/donation-event`.
`PB_ADMIN_API_URL` = shu URL, `PB_UPDATE_URL` = `.../update`, `PB_DONATION_CONFIG_URL` = `.../donation-config` (build workflow'da avtomatik default).
Karta raqami faqat admin orqali bazaga yoziladi (`POST /admin/settings` `donation.card`), repoda yo'q. Donation "confirmed" holatini faqat admin qo'yadi.
