param(
    [string]$Serial,
    [string]$Adb = 'adb',
    [switch]$ColdStartOnly,
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '../output/history-rescan-e2e')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$appApk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
foreach ($apk in @($appApk, $testApk)) {
    if (!(Test-Path -LiteralPath $apk)) { throw "缺少 APK，请先构建：$apk" }
}
New-Item -ItemType Directory -Force -Path $ArtifactDirectory | Out-Null
$devices = @(& $Adb devices -l)
if ($LASTEXITCODE -ne 0) { throw '无法查询 Android 设备。' }
$devices | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'devices.txt') -Encoding UTF8
if (!$Serial) {
    $ready = @($devices | Where-Object { $_ -match '^\S+\s+device\b' })
    if ($ready.Count -ne 1) {
        'NOT RUN: Connect and authorize one Android camera device, or specify -Serial.' |
            Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'instrumentation.txt') -Encoding UTF8
        throw '未连接且授权唯一的 Android 设备；设备测试未执行。'
    }
    $Serial = ($ready[0] -split '\s+')[0]
}
if ((& $Adb -s $Serial get-state) -ne 'device') { throw '设备未就绪。' }
foreach ($apk in @($appApk, $testApk)) {
    & $Adb -s $Serial install -r -t -g $apk
    if ($LASTEXITCODE -ne 0) { throw "安装失败：$apk" }
}
Write-Host '请保持镜头对准光照充分的纸质文档。测试包含真实拍摄，将保留原有智能手拍回归产生的记录。'
$classes = 'cn.yuandianai.juziscan.scan.HistoryDocumentPersistenceTest,cn.yuandianai.juziscan.scan.HistoryRescanE2eTest,cn.yuandianai.juziscan.scan.SmartManualCaptureTest,cn.yuandianai.juziscan.scan.SmartManualCaptureE2eTest,cn.yuandianai.juziscan.scan.HistoryQualityAndReorderE2eTest'
if (!$ColdStartOnly) {
$result = & $Adb -s $Serial shell am instrument -w -r -e class $classes 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
$instrumentExit = $LASTEXITCODE
$result | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'instrumentation.txt') -Encoding UTF8
& $Adb -s $Serial logcat -d -s JuZiScan | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'app-log.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw '导出应用日志失败。' }
& $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/history-rescan-e2e/.' $ArtifactDirectory
if ($LASTEXITCODE -ne 0) { throw '导出测试工件失败。' }
New-Item -ItemType Directory -Force -Path (Join-Path $ArtifactDirectory 'smart-manual') | Out-Null
& $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/smart-manual-e2e/.' (Join-Path $ArtifactDirectory 'smart-manual')
if ($LASTEXITCODE -ne 0) { throw '导出连续扫描工件失败。' }
$result | Write-Output
if ($instrumentExit -ne 0 -or ($result -join "`n") -notmatch 'OK \(14 tests\)') {
    throw "设备测试未通过，请检查 $ArtifactDirectory/instrumentation.txt"
}
}
$phases = [ordered]@{
    'capture-three' = 'captureThreeRealPages'
    'reopen-append' = 'reopenThreeAppendAutoAndManualPages'
    'reopen-five' = 'reopenFiveNoOpShareAndPrint'
}
foreach ($phase in $phases.GetEnumerator()) {
    & $Adb -s $Serial shell am force-stop cn.yuandianai.juziscan
    if ($LASTEXITCODE -ne 0) { throw '冷启动前停止应用失败。' }
    $case = 'cn.yuandianai.juziscan.scan.HistoryProcessRestartE2eTest#' + $phase.Value
    $phaseResult = & $Adb -s $Serial shell am instrument -w -r -e coldStartPhase true -e class $case 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
    $phaseExit = $LASTEXITCODE
    $phaseResult | Set-Content -LiteralPath (Join-Path $ArtifactDirectory ($phase.Key + '.txt')) -Encoding UTF8
    & $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/history-rescan-e2e/.' $ArtifactDirectory
    if ($LASTEXITCODE -ne 0) { throw '导出冷启动测试工件失败。' }
    $phaseResult | Write-Output
    if ($phaseExit -ne 0 -or ($phaseResult -join "`n") -notmatch 'OK \(1 tests?\)') {
        throw "冷启动阶段 $($phase.Key) 未通过，请检查对应结果文件。"
    }
}
& $Adb -s $Serial logcat -d -t 4000 -s JuZiScan | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'app-log.txt') -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw '导出最终应用日志失败。' }
if ($ColdStartOnly) { Write-Host "3 个跨进程阶段通过；本次未运行 14 项基础回归。工件保存在 $ArtifactDirectory。" }
else { Write-Host "17 项设备测试通过，工件保存在 $ArtifactDirectory；保留一份真实五页续扫文档供人工查看。" }
