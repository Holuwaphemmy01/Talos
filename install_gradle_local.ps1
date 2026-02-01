$gradleVersion = "9.3.1"
$zipFile = "$PSScriptRoot\gradle-$gradleVersion-bin.zip"
$tempDir = "$PSScriptRoot\gradle_temp"

Write-Host "Checking for local file: $zipFile" -ForegroundColor Cyan

if (-not (Test-Path $zipFile)) {
    Write-Error "File not found! Please download '$zipFile' manually and place it in this folder."
    Write-Host "Download Link: https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -ForegroundColor Yellow
    exit 1
}

Write-Host "Found zip file! Extracting..." -ForegroundColor Cyan
if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
Expand-Archive -Path $zipFile -DestinationPath $tempDir -Force

Write-Host "Generating Wrapper..." -ForegroundColor Cyan
$gradleBin = "$tempDir\gradle-$gradleVersion\bin\gradle.bat"

if (-not (Test-Path $gradleBin)) {
    Write-Error "Could not find gradle.bat at $gradleBin"
    exit 1
}

# Run gradle wrapper to set up the project
& $gradleBin wrapper --gradle-version $gradleVersion

Write-Host "Cleaning up..." -ForegroundColor Cyan
Remove-Item $tempDir -Recurse -Force
# We keep the zip file just in case

Write-Host "SUCCESS! Build environment is ready." -ForegroundColor Green
Write-Host "You can now run: .\gradlew assembleDebug" -ForegroundColor White
