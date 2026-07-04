# TimedAppLauncher build script
# Usage: .\build.ps1

$ErrorActionPreference = "Stop"
$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$ProjectDir = $PSScriptRoot
$ZipPath = "$env:TEMP\cmdline-tools.zip"

function Download-WithRetry {
    param(
        [string[]]$Urls,
        [string]$OutFile,
        [long]$MinSize = 1
    )
    foreach ($url in $Urls) {
        Write-Host "Downloading: $url"
        for ($i = 1; $i -le 5; $i++) {
            curl.exe -L -C - --retry 3 --retry-delay 5 -o $OutFile $url
            if ((Test-Path $OutFile) -and ((Get-Item $OutFile).Length -ge $MinSize)) {
                Write-Host "OK: $OutFile ($((Get-Item $OutFile).Length) bytes)"
                return $true
            }
            Write-Host "Retry $i/5 ..."
            Start-Sleep -Seconds 3
        }
    }
    return $false
}

Write-Host "=== Step 1/5: Setup Android SDK ===" -ForegroundColor Cyan

if (-not (Test-Path "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat")) {
    if (-not (Test-Path $ZipPath)) {
        Write-Host "ERROR: Missing $ZipPath" -ForegroundColor Red
        exit 1
    }
    New-Item -ItemType Directory -Force -Path "$SdkRoot\cmdline-tools\latest" | Out-Null
    $TempExtract = "$env:TEMP\android-cmdline-extract"
    if (Test-Path $TempExtract) { Remove-Item $TempExtract -Recurse -Force }
    Expand-Archive -Path $ZipPath -DestinationPath $TempExtract -Force
    Get-ChildItem "$TempExtract\cmdline-tools" | Move-Item -Destination "$SdkRoot\cmdline-tools\latest\" -Force
    Write-Host "SDK tools extracted to $SdkRoot"
}

$env:ANDROID_HOME = $SdkRoot
$sdkmanager = "$SdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"

Write-Host "=== Step 2/5: Install SDK packages ===" -ForegroundColor Cyan
& $sdkmanager --sdk_root=$SdkRoot "platform-tools" "platforms;android-34" "build-tools;34.0.0"

Write-Host "=== Step 3/5: Accept SDK licenses ===" -ForegroundColor Cyan
$yes = ("y`n" * 20)
$yes | & $sdkmanager --sdk_root=$SdkRoot --licenses

Write-Host "=== Step 4/5: Write local.properties ===" -ForegroundColor Cyan
$localProps = Join-Path $ProjectDir "local.properties"
$sdkDirLine = "sdk.dir=" + ($SdkRoot -replace "\\", "/")
Set-Content -Path $localProps -Value $sdkDirLine -Encoding ASCII

Write-Host "=== Step 5/5: Build Debug APK ===" -ForegroundColor Cyan
Set-Location $ProjectDir

$wrapperJar = Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "Missing gradle-wrapper.jar, preparing Gradle ..."
    $gradleZip = "$env:TEMP\gradle-8.2-bin.zip"
    $gradleUrls = @(
        "https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip",
        "https://mirrors.huaweicloud.com/gradle/gradle-8.2-bin.zip",
        "https://services.gradle.org/distributions/gradle-8.2-bin.zip"
    )
    if (-not (Download-WithRetry -Urls $gradleUrls -OutFile $gradleZip -MinSize 50000000)) {
        Write-Host "ERROR: Gradle download failed. Try with proxy ON, then run:" -ForegroundColor Red
        Write-Host "curl.exe -L -C - -o `"$gradleZip`" `"https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip`""
        exit 1
    }
    $gradleDir = "$env:TEMP\gradle-8.2"
    if (-not (Test-Path "$gradleDir\bin\gradle.bat")) {
        Expand-Archive -Path $gradleZip -DestinationPath $env:TEMP -Force
    }
    & "$gradleDir\bin\gradle.bat" wrapper --gradle-version 8.2 --gradle-distribution-url "https://mirrors.cloud.tencent.com/gradle/gradle-8.2-bin.zip"
}

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "ERROR: gradlew.bat missing" -ForegroundColor Red
    exit 1
}

.\gradlew.bat assembleDebug

$apk = Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    Write-Host ""
    Write-Host "BUILD SUCCESS" -ForegroundColor Green
    Write-Host "APK: $apk" -ForegroundColor Green
} else {
    Write-Host "BUILD FAILED - check errors above" -ForegroundColor Red
    exit 1
}
