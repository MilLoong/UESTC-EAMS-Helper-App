#!/usr/bin/env bash
# WSL 里一键编译并装到 USB 连接的手机（走 Windows adb）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /mnt/c/Users/123/Downloads/UESTC-EAMS-Helper-debug.apk
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$ROOT/scripts/install-phone-debug.ps1"
