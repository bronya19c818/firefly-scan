param(
    [string]$Serial,
    [string]$Adb = 'adb',
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '../output/document-styles-e2e')
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
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r -t -g (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw "安装失败：$apk" }
}
$inputs = '/sdcard/Android/data/cn.yuandianai.juziscan/files/document-style-inputs'
& $Adb -s $Serial shell mkdir -p $inputs
& $Adb -s $Serial push (Join-Path $projectRoot 'test_image/.') "$inputs/"
if ($LASTEXITCODE -ne 0) { throw '样张复制失败' }
$runner = 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner'
function Invoke-Case([string]$Case, [string]$Name, [switch]$Phase) {
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($Phase) { $arguments += @('-e', 'documentStylePhase', 'true') }
    $arguments += @('-e', 'class', $Case, $runner)
    $result = & $Adb @arguments 2>&1
    $exitCode = $LASTEXITCODE
    $result | Set-Content -LiteralPath (Join-Path $ArtifactDirectory "$Name.txt") -Encoding UTF8
    $result | Write-Output
    $joined = $result -join [Environment]::NewLine
    if ($exitCode -ne 0 -or $joined -match 'FAILURES|Process crashed' -or $joined -notmatch 'OK \(\d+ tests?\)') {
        throw "设备测试失败：$Name"
    }
}
try {
    Invoke-Case 'cn.yuandianai.juziscan.scan.DocumentStylesE2eTest' 'instrumentation'
    Invoke-Case 'cn.yuandianai.juziscan.scan.DocumentStylesRestartE2eTest#persistBeforeProcessTermination' 'before-force-stop' -Phase
    & $Adb -s $Serial shell am force-stop cn.yuandianai.juziscan
    Invoke-Case 'cn.yuandianai.juziscan.scan.DocumentStylesRestartE2eTest#restoreAfterProcessTerminationAndExport' 'after-force-stop' -Phase
} finally {
    & $Adb -s $Serial logcat -d -t 6000 -s JuZiScan AndroidRuntime | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'app-log.txt') -Encoding UTF8
    & $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/document-styles-e2e/.' $ArtifactDirectory
}
