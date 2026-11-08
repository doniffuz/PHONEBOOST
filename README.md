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
**Variables:** `PB_SUPPORT_URL`, `PB_PRIVACY_POLICY_URL` (sayt URL + `#privacy`), `PB_DONATION_URL`, `PB_DONATION_CONFIG_URL`, `PB_UPDATE_URL`.

## Xavfsizlik
- Karta raqami, admin parol/secret kodda, README da va APK ichida yo'q. Karta faqat HTTPS config orqali olinadi, xotirada turadi, loglanmaydi.
- To'lov muvaffaqiyati ilova tomonidan hech qachon taxmin qilinmaydi.
- Admin autentifikatsiyasi Android ilovada YO'Q: u alohida Admin Web Panelda (Supabase Auth + email OTP), serverda tekshiriladi.

## Ma'lum cheklovlar
- Android boshqa ilovalarni yopish, FPS oshirish, boshqa ilovalar keshini tozalashga ruxsat bermaydi: ilova bularni qilmaydi.
- `WRITE_SECURE_SETTINGS` manifestdan OLIB TASHLANGAN. Speed sahifasi animatsiya qiymatlarini faqat o'qiydi va Developer options yo'riqnomasini beradi; Game Boost tizim sozlamasini o'zgartirmaydi.
- Admin Web Panel: `admin-panel/` (alohida loyiha, Android ichida admin ekran YO'Q). Build: `cd admin-panel && npm install && npm run check`. `public/config.js` ga faqat ommaviy API manzili yoziladi.
- Click/Payme/Uzum/TBC: merchant integratsiya YO'Q. Ular hamma joyda "Not configured"; yoqib bo'lmaydi va success qilib bo'lmaydi (DB trigger + Edge Function). Manual card/QR to'lovlar admin qo'lda tasdiqlamaguncha `pending` qoladi.
- Android: e'londagi rasm (`imageUrl`) hali ko'rsatilmaydi (rasm yuklash kutubxonasi yo'q); QR faqat havola/matn sifatida chiqadi, QR rasm chizilmaydi.
- Gradle wrapper birinchi CI ishida avtomatik yaratiladi.

## Holat (build tekshiruvi)
- Gradle wrapper (8.9) repoga qo'shilgan; CI `./gradlew` bilan build qiladi.
- Kod bu muhitda KOMPILYATSIYA QILINMAGAN (tarmoq yo'q): BUILD NOT VERIFIED. Haqiqiy natija GitHub Actions `BUILD_REPORT.txt` da.
- `QUERY_ALL_PACKAGES` ishlatilmaydi (faqat `<queries>` orqali launcher ilovalari ko'rinadi).

## Backend (Supabase)
Loyiha: `bmyvvwyfgswjgktlsqlu`. Fayllar: `supabase/migrations/` (jadvallar + RLS policy'lar + admin role), `supabase/functions/api/` (Edge Function), `supabase/tests/rls.test.sql` (pgTAP).
**Admin kirish:** Supabase Auth (email+parol) → emailga 6 xonali OTP → server `admin_sessions` ga yozadi. `is_admin()` (RLS) faqat shunday to'liq tasdiqlangan sessiyani admin deb biladi. Batafsil: `docs/ADMIN_ARCHITECTURE.md`.
**Birinchi admin (parol repoda YO'Q):** Dashboard > Authentication > Add user (parolni o'zingiz o'rnating), keyin `supabase/seed_admin.sql` ni SQL Editor'da bir marta ishga tushiring.
**Auth sozlamalari (OTP expiry/rate limit, signup yopiq):** `supabase/config.toml` lokal uchun; hosted loyihada Dashboard > Authentication da yoki `supabase config push` bilan qo'llang va tekshiring.
Deploy: GitHub Actions "Deploy Supabase backend" (`supabase.yml`). **Secrets:** `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD` (ixtiyoriy: `SUPABASE_ANON_KEY` — smoke test uchun). **Variable:** `ADMIN_ALLOWED_ORIGINS` (admin web panel domenlari, vergul bilan; bo'sh = CORS yopiq). `service_role` kalitini Supabase funksiyaga o'zi beradi: u hech qayerga yozilmaydi.
Endpointlar (`.../functions/v1/api`): `/admin/login` → `/admin/verify-otp` → `/admin/refresh`, `/admin/logout|me|stats|users|optimization|game-boost|diagnostics|donations|errors|version|announcements|maintenance|settings|audit-logs` (admin sessiya bilan), ochiq (rate limit bilan): `GET /update[?current=N]`, `/donation-config`, `/donation-status?ref=PB-…`, `/top-donors`, `/announcements`, `/status`, `POST /ingest`, `/donation-event` (har doim `pending`). Admin: `/admin/top-donors`, `/admin/donations`, `/admin/announcements`, `/admin/version`, `/admin/settings` (provayderlar, QR, tavsif).
`PB_UPDATE_URL` = `.../update`, `PB_DONATION_CONFIG_URL` = `.../donation-config`.
Karta raqami faqat admin orqali bazaga yoziladi (`POST /admin/settings` `donation.card`), repoda yo'q; audit logda qiymati yozilmaydi. Donation "confirmed" holatini faqat admin qo'yadi.

## Tekshiruv (qanday ishga tushiriladi)
```
node --experimental-strip-types --test supabase/functions/api/lib.test.ts   # backend sof mantiq
supabase start && supabase test db                                          # RLS/pgTAP (Docker kerak)
cd admin-panel && npm install && npm run check                              # typecheck + lint + test + build
bash scripts/security-scan.sh                                               # statik xavfsizlik skaneri
./gradlew lint testDebugUnitTest assembleDebug                              # Android (CI: build.yml)
```
CI: `backend-tests.yml`, `admin-panel.yml`, `build.yml`. CI natijasi ko'rilmaguncha hech narsa "PASS" deb hisoblanmaydi.
