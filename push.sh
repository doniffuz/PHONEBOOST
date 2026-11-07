#!/data/data/com.termux/files/usr/bin/bash
# GitHub'ga yuborish: ./push.sh
cd "$(dirname "$0")"
[ -d .git ] || git init
git branch -M main
git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/doniffuz/PHONEBOOST.git
git add -A
git add -f .github
git commit -m "PHONEBOOST update" || true
git push -u origin main
