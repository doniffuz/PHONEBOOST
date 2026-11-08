#!/usr/bin/env bash
# Statik xavfsizlik skaneri. Hech narsani "PASS" deb o'zi o'ylab topmaydi: faqat quyidagi qoidalarni tekshiradi.
# Ishga tushirish: bash scripts/security-scan.sh   (0 = topilma yo'q, 1 = topilma bor)
set -u
cd "$(dirname "$0")/.."
fail=0
EX=(--exclude-dir=.git --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.gradle --exclude=security-scan.sh --exclude=build.yml --exclude=backend-tests.yml --exclude=admin-panel.yml)
chk() { # nom, regex, so'ng: --opsiyalar va yo'llar (yo'l berilmasa "." qidiriladi)
  local name="$1" re="$2"; shift 2
  local opts=() paths=()
  for x in "$@"; do case "$x" in --*) opts+=("$x");; *) paths+=("$x");; esac; done
  [ ${#paths[@]} -eq 0 ] && paths=(.)
  # Izoh qatorlari (//, --, #, *) hisobga olinmaydi: faqat haqiqiy kod/konfiguratsiya.
  local out; out=$(grep -rInE "${EX[@]}" "${opts[@]}" -e "$re" "${paths[@]}" 2>/dev/null \
    | grep -vE '^[^:]+:[0-9]+:[[:space:]]*(//|--|#|\*|/\*)' || true)
  if [ -n "$out" ]; then echo "FAIL: $name"; echo "$out" | head -8; fail=1; else echo "ok:   $name"; fi
}
# service_role / maxfiy kalit klientda (Android, admin panel, sayt) bo'lmasin. Faqat serverda (supabase/) ruxsat.
chk "service_role klientda yo'q" 'service_role|SERVICE_ROLE|sb_secret_' app admin-panel website
chk "JWT/maxfiy kalit literali yo'q" 'eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.|sb_secret_[A-Za-z0-9_-]+|sbp_[a-f0-9]{30,}'
chk "DB connection string parol bilan yo'q" 'postgres(ql)?://[^ "'"'"'@]+:[^ "'"'"'@]+@'
chk "qattiq yozilgan parol/secret/otp yo'q" '(password|passwd|secret|api[_-]?key|otp)["'"'"' ]*[:=]["'"'"' ]*["'"'"'][A-Za-z0-9!@#$%^&*_.-]{6,}["'"'"']' --include=*.kt --include=*.ts --include=*.js --include=*.sql --include=*.toml --include=*.html --exclude=*.test.ts --exclude=*.test.sql
chk "admin parol/bootstrap qiymatlari kodga yozilmagan" '(ADMIN_JWT_SECRET|ADMIN_BOOTSTRAP_EMAIL|ADMIN_BOOTSTRAP_PASSWORD|ADMIN_PASSWORD)[\"' ]*[:=][\"' ]*[\"''][^\"'']{6,}[\"'']' --include=*.kt --include=*.ts --include=*.js --include=*.sql --include=*.toml --include=*.html
# Karta raqami: 12-19 raqamli ketma-ketlik (testlardagi aniq soxta qiymatlardan tashqari).
out=$(grep -rInE "${EX[@]}" --include=*.kt --include=*.ts --include=*.js --include=*.sql --include=*.md --include=*.json --include=*.html --include=*.xml -e '\b[0-9]{4}[ -]?[0-9]{4}[ -]?[0-9]{4}[ -]?[0-9]{1,7}\b' . 2>/dev/null \
  | grep -v -E '1111222233334444|123456789012345|1234 5678 9012 3456|dist/|versionCode|build_report' || true)
if [ -n "$out" ]; then echo "FAIL: karta-ga o'xshash raqam"; echo "$out" | head -8; fail=1; else echo "ok:   karta raqami yo'q (soxta test qiymatlaridan tashqari)"; fi
chk "OTP/parol/karta logga yozilmaydi" 'console\.(log|info|debug|warn|error)\([^)]*(otp|password|card|token|code)' supabase admin-panel/src
chk "admin panelda innerHTML/eval yo'q (XSS)" 'innerHTML|outerHTML|insertAdjacentHTML|document\.write|eval\(|new Function' admin-panel/src
chk "admin panelda localStorage'da token yo'q" 'localStorage' admin-panel/src
echo "INFO: WRITE_SECURE_SETTINGS mavjud — Play Store mosligi alohida tekshiriladi." 
# admin bypass: is_admin() har doim true qaytarmasin / "bypass|backdoor" so'zlari
chk "admin bypass izlari yo'q" 'bypass|backdoor|skip_?auth|disable_?auth' supabase admin-panel/src app/src/main
exit $fail
