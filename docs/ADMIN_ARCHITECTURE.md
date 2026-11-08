# PHONEBOOST Admin — arxitektura (B bosqich)

```
PHONEBOOST Android ilova ──HTTPS──► Supabase (Edge Function `api`, DB+RLS, Auth) ◄──HTTPS── Admin Web Panel (alohida loyiha)
```
Android ilovada admin kodi YO'Q (AdminScreen olib tashlangan). Web panel hali yaratilmagan; quyida u uchun tayyor API shartnomasi.

## Login oqimi (server tomonida majburiy)
1. `POST /admin/login {email,password}` → server Supabase Auth'da parolni tekshiradi (sessiya mijozga berilmaydi), emailga 6 xonali OTP yuboriladi. Javob: `{challengeId, expiresIn:600, sentTo:"d***@g***.com"}`.
2. `POST /admin/verify-otp {challengeId, code}` → server OTP'ni Supabase Auth orqali tekshiradi. Faqat muvaffaqiyatda `admin_sessions` ga yoziladi va `{accessToken, refreshToken, expiresIn}` qaytadi.
3. Keyingi so'rovlar: `Authorization: Bearer <accessToken>`. `POST /admin/refresh {refreshToken}` yangilaydi. `POST /admin/logout` sessiyani bekor qiladi.

## Xavfsizlik qoidalari
| Talab | Qanday ta'minlanadi |
|---|---|
| OTP bir martalik | `challenge_consume()` atomik (`used_at`), Supabase Auth OTP ham bir martalik |
| OTP expiry | challenge 10 daq, Supabase OTP 5 daq (`otp_expiry=300`) |
| Noto'g'ri urinish limiti | `challenge_attempt()` — 5 urinishdan keyin challenge o'ladi; yangi login (parol) kerak |
| Rate limit | `rate_hit()`: login IP 10/15daq, email 5/15daq; OTP yuborish 5/soat; verify IP 30/15daq; admin API 120/daq; ochiq endpointlar IP bo'yicha |
| Admin role serverda | `admin_profiles` (mijoz yoza olmaydi) + `admin_sessions`; `is_admin()` ikkalasini tekshiradi |
| OTP'ni aylanib o'tish yo'q | GoTrue'ga to'g'ridan-to'g'ri OTP bilan kirilsa ham `session_id` `admin_sessions`da yo'q → `is_admin()=false`, Edge Function ham rad etadi |
| Parol kodda yo'q | Parol faqat Supabase Auth'da; admin Dashboard'da yaratiladi |
| service_role | faqat Edge Function muhitida (Supabase o'zi beradi) |
| Audit | `audit_logs` append-only (trigger), faqat admin o'qiydi; sezgir maydon qiymatlari yozilmaydi |
| CORS | faqat `ADMIN_ALLOWED_ORIGINS`; `*` hech qachon |

## Web panel bo'limlari → API
| Bo'lim | Endpoint | Holat |
|---|---|---|
| Login / OTP | `/admin/login`, `/admin/verify-otp` | backend bor |
| Dashboard | `GET /admin/stats?days=7\|30\|90` | bor (bugun/hafta/oy donation bo'linmasi yo'q) |
| Users | `GET /admin/users` | bor (anonim install statistikasi) |
| Donations | `GET/POST /admin/donations` | bor (statuslar: pending/confirmed/rejected; Failed/Cancelled yo'q) |
| Top 10 Donors | — | **YO'Q** (donor nomi/roziligi ustunlari kerak, C bosqich) |
| Announcements | `GET/POST /admin/announcements` (create, edit, enable/disable, delete) | bor (rasm/publish vaqti yo'q) |
| App Update | `GET/POST /admin/version` | bor (min. qo'llab-quvvatlanadigan versiya yo'q) |
| Donation Settings | `GET/POST /admin/settings` | bor (QR/merchant konfiguratsiyasi yo'q) |
| General Settings | `POST /admin/settings` (maintenance) | qisman |
| Audit Logs | `GET /admin/audit-logs?offset=` | bor |
