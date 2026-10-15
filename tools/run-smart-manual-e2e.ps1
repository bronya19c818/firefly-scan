param(
    [string]$Serial,
    [string]$Adb = 'adb',
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '../output/smart-manual-e2e')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$appApk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
foreach ($apk in @($appApk, $testApk)) {
    if (!(Test-Path -LiteralPath $apk)) { throw "缺少 APK，请先运行 assembleDebug assembleDebugAndroidTest：$apk" }
}
if (!$Serial) {
    $deviceRows = @(& $Adb devices | Where-Object { $_ -match '^\S+\s+device$' })
    if ($deviceRows.Count -ne 1) { throw '请连接并授权一台 Android 设备，或用 -Serial 指定设备。' }
    $Serial = ($deviceRows[0] -split '\s+')[0]
}
if ((& $Adb -s $Serial get-state) -ne 'device') { throw '设备未就绪。' }
New-Item -ItemType Directory -Force -Path $ArtifactDirectory | Out-Null
foreach ($apk in @($appApk, $testApk)) {
    & $Adb -s $Serial install -r -t -g $apk
    if ($LASTEXITCODE -ne 0) { throw "安装失败：$apk" }
}
Write-Host '请保持镜头对准光照充分的纸质文档。测试将实际拍摄 5 页并保留扫描记录。'
$testResult = & $Adb -s $Serial shell am instrument -w -r -e class 'cn.yuandianai.juziscan.scan.SmartManualCaptureTest,cn.yuandianai.juziscan.scan.SmartManualCaptureE2eTest' 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
$instrumentExit = $LASTEXITCODE
$testResult | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'instrumentation.txt') -Encoding UTF8
Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'camera-log.txt') -Value '' -Encoding UTF8
& $Adb -s $Serial logcat -d -s JuZiScan | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'camera-log.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw '导出相机日志失败，请检查设备连接。' }
& $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/smart-manual-e2e/.' $ArtifactDirectory
if ($LASTEXITCODE -ne 0) { throw '取回测试截图失败，请检查设备连接。' }
$testResult | Write-Output
if ($instrumentExit -ne 0 -or ($testResult -join "`n") -notmatch 'OK \(5 tests\)') {
    throw "设备测试未通过，请检查 $ArtifactDirectory/instrumentation.txt"
}
Write-Host "5 项设备测试通过，证据保存在 $ArtifactDirectory"
