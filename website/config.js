// PHONEBOOST sayti sozlamalari. Bu fayl PUBLIC — bu yerga parol, API kalit yoki karta raqami YOZILMAYDI.
window.PB_CONFIG = {
  // Android APK yuklab olish havolasi (https). Bo'sh bo'lsa tugma o'chiq turadi.
  DOWNLOAD_URL: "",
  // Donation backend/provider API (https). Bo'sh bo'lsa "To'lov xizmati ulanmagan" holati ko'rsatiladi.
  // Kutilgan API: POST {DONATION_API}/donations  body {amount, anonymous, displayName}  -> {checkoutUrl}
  DONATION_API: "",
  // Top 10: faqat muvaffaqiyatli to'lovlardan shakllangan ro'yxat. Format: [{"name":"Ali","amount":50000,"anonymous":false}]
  SUPPORTERS_URL: "data/supporters.json",
  TELEGRAM_URL: "https://t.me/Doniku_Admin"
};
