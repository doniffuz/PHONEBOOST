# PHONEBOOST Support sayti

Statik sayt (HTML/CSS/JS), build kerak emas. UZ / RU / EN.

## Sozlash (`config.js`) — hammasi PUBLIC, maxfiy narsa yozilmaydi
| Kalit | Ma'no |
|---|---|
| `DOWNLOAD_URL` | APK havolasi (https). Bo'sh = tugma o'chiq |
| `DONATION_API` | Payment backend (https). Bo'sh = "Payment provider ulanmagan" |
| `SUPPORTERS_URL` | Top 10 JSON. Faqat MUVAFFAQIYATLI to'lovlar |
| `TELEGRAM_URL` | Aloqa havolasi |

## Donation (hozir ulanmagan)
Provider ulanganda backend quyidagini bajarishi kerak:
`POST {DONATION_API}/donations` `{amount, anonymous, displayName}` -> `{checkoutUrl}` (https).
Karta raqami, API kalit va secretlar FAQAT backendda (environment/secret). Frontendga yozilmaydi.
Top 10 ro'yxatini backend faqat to'lov tasdiqlangandan keyin yangilaydi; `data/supporters.json` qo'lda soxta ma'lumot bilan to'ldirilmaydi.

## Joylash (GitHub Pages)
Repo → Settings → Pages → Source: **GitHub Actions**. `main` ga push qilinganda `website.yml` saytni joylaydi.
Sayt URL'i (`https://<user>.github.io/<repo>/`) ni GitHub → Settings → Secrets and variables → **Variables** ga
`PB_SUPPORT_URL` nomi bilan yozing — Android ilova shu havolani ochadi.
Android uchun ixtiyoriy Variables: `PB_PRIVACY_POLICY_URL` (= sayt URL + `#privacy`).
