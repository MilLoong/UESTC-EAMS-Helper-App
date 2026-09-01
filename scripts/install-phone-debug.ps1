# Install debug APK to USB-connected phone via Windows adb.
$ErrorActionPreference = "Stop"

$Adb = "C:\Users\123\platform-tools\adb.exe"
$Apk = "C:\Users\123\Downloads\UESTC-EAMS-Helper-debug.apk"
$Port = 5038

if (-not (Test-Path $Adb)) { Write-Error "adb not found: $Adb" }
if (-not (Test-Path $Apk)) { Write-Error "apk not found: $Apk" }

& $Adb -P $Port devices -l
& $Adb -P $Port shell settings put global verifier_verify_adb_installs 0 | Out-Null
& $Adb -P $Port shell settings put global package_verifier_enable 0 | Out-Null
& $Adb -P $Port shell settings put global upload_apk_enable 0 | Out-Null
& $Adb -P $Port install -r -g $Apk
Write-Host "Done."
