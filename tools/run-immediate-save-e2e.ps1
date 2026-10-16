param(
    [string]$Serial,
    [string]$Adb = 'adb',
    [switch]$SkipRestart,
    [switch]$RestartOnly,
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '../output/immediate-save-e2e')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$ArtifactDirectory = [IO.Path]::GetFullPath($ArtifactDirectory)
New-Item -ItemType Directory -Force -Path $ArtifactDirectory | Out-Null
if (!$Serial) {
    $devices = @(& $Adb devices -l)
    $devices | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'devices.txt') -Encoding UTF8
    $ready = @($devices | Where-Object { $_ -match '^\S+\s+device\b' })
    if ($ready.Count -ne 1) { throw '请连接一台已授权手机，或指定 -Serial。' }
    $Serial = ($ready[0] -split '\s+')[0]
}
if ((& $Adb -s $Serial get-state) -ne 'device') { throw 'Android 设备未就绪。' }
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r -t -g (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw "安装失败：$apk" }
}
$runner = 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner'
$deviceArtifacts = '/sdcard/Android/data/cn.yuandianai.juziscan/files/immediate-save-e2e'
function Invoke-DeviceCase([string]$Case, [string]$Name, [switch]$Phase) {
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Phase) { $arguments += @('-e', 'immediatePhase', 'true') }
    $arguments += @('-e', 'class', $Case, $runner)
    $result = & $Adb @arguments 2>&1
    $exitCode = $LASTEXITCODE
    $result | Set-Content -LiteralPath (Join-Path $ArtifactDirectory "$Name.txt") -Encoding UTF8
    $joined = $result -join "`n"
    $result | Write-Output
    if ($exitCode -ne 0 -or $joined -match 'FAILURES|Process crashed' -or $joined -notmatch 'OK \(\d+ tests?\)') {
        throw "设备测试失败：$Name"
    }
}
Write-Host '请先解锁手机、保持纸张完整入镜；脚本保留用户文档，仅清理测试自己创建的文档。'
try {
    if (!$RestartOnly) {
        Invoke-DeviceCase 'cn.yuandianai.juziscan.scan.ImmediateDurabilityTest,cn.yuandianai.juziscan.scan.ImmediateSaveE2eTest' 'instrumentation'
    }
    if (!$SkipRestart) {
        & $Adb -s $Serial shell am force-stop cn.yuandianai.juziscan
        & $Adb -s $Serial shell rm -f "$deviceArtifacts/force-stop-ready"
        $adbPath = (Get-Command $Adb).Source
        $captureLog = Join-Path $ArtifactDirectory 'forced-capture.txt'
        $captureError = Join-Path $ArtifactDirectory 'forced-capture-stderr.txt'
        $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'immediatePhase', 'true',
            '-e', 'class', 'cn.yuandianai.juziscan.scan.ImmediateProcessRestartE2eTest#captureAndWaitForForceStop', $runner)
        $captureProcess = Start-Process -FilePath $adbPath -ArgumentList $arguments -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $captureLog -RedirectStandardError $captureError
        $deadline = (Get-Date).AddSeconds(180)
        $checkpoint = $false
        while ((Get-Date) -lt $deadline -and !$captureProcess.HasExited) {
            & $Adb -s $Serial shell test -f "$deviceArtifacts/force-stop-ready"
            if ($LASTEXITCODE -eq 0) { $checkpoint = $true; break }
            Start-Sleep -Milliseconds 500
            $captureProcess.Refresh()
        }
        if (!$checkpoint) { throw '未到达可靠拍摄断点，不能把本次终止计为通过。' }
        & $Adb -s $Serial shell am force-stop cn.yuandianai.juziscan
        $captureProcess.WaitForExit(10000) | Out-Null
        'Force-stopped after five acknowledged captures, before entering preview; intentional instrumentation termination.' |
            Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'force-stop.txt') -Encoding UTF8
        Invoke-DeviceCase 'cn.yuandianai.juziscan.scan.ImmediateProcessRestartE2eTest#recoverThenEditAndReturnImmediately' 'restore-edit' -Phase
        & $Adb -s $Serial shell am force-stop cn.yuandianai.juziscan
        Invoke-DeviceCase 'cn.yuandianai.juziscan.scan.ImmediateProcessRestartE2eTest#recoverEditsAfterSecondProcessTermination' 'restore-edits' -Phase
    }
} finally {
    & $Adb -s $Serial logcat -d -t 6000 -s JuZiScan AndroidRuntime |
        Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'app-log.txt') -Encoding UTF8
    & $Adb -s $Serial pull "$deviceArtifacts/." $ArtifactDirectory
}
