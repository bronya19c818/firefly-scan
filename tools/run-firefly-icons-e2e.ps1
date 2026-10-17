param(
    [string]$Serial,
    [string]$Adb = 'adb',
    [string]$ArtifactDirectory = (Join-Path $PSScriptRoot '../output/firefly-icons-e2e')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$ArtifactDirectory = [IO.Path]::GetFullPath($ArtifactDirectory)
New-Item -ItemType Directory -Force -Path $ArtifactDirectory | Out-Null
if (!$Serial) {
    $rows = @(& $Adb devices -l | Where-Object { $_ -match '^\S+\s+device\b' })
    if ($rows.Count -ne 1) { throw '请连接一台已授权手机，或指定 -Serial。' }
    $Serial = ($rows[0] -split '\s+')[0]
}
foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $Adb -s $Serial install -r -t (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw "安装失败：$apk" }
}
& $Adb -s $Serial shell getprop ro.product.model | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'device.txt') -Encoding utf8
$classes = 'cn.yuandianai.juziscan.scan.FireflyIconNavigationE2eTest,cn.yuandianai.juziscan.scan.FireflyIconRenderE2eTest'
try {
    $result = & $Adb -s $Serial shell am instrument -w -r -e class $classes 'cn.yuandianai.juziscan.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
    $code = $LASTEXITCODE
    $result | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'instrumentation.txt') -Encoding utf8
    $result | Write-Output
    $joined = $result -join [Environment]::NewLine
    if ($code -ne 0 -or $joined -match 'FAILURES|Process crashed' -or $joined -notmatch 'OK \(7 tests\)') { throw '图标端到端验证失败，请检查 instrumentation.txt。' }
} finally {
    & $Adb -s $Serial pull '/sdcard/Android/data/cn.yuandianai.juziscan/files/firefly-icons-e2e/.' $ArtifactDirectory
    & $Adb -s $Serial logcat -d -t 3000 -s AndroidRuntime | Set-Content -LiteralPath (Join-Path $ArtifactDirectory 'crashes.txt') -Encoding utf8
}
