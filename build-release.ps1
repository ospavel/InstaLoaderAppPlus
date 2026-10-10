param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = $PSScriptRoot
$BuilderImage = 'instaloaderapp-android-builder:local'
$GradleCacheVolume = 'instaloaderapp-gradle-cache'
$AndroidHomeVolume = 'instaloaderapp-android-home'

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @('assembleRelease')
}

$KeystoreProperties = Join-Path $ProjectRoot 'keystore.properties'
if (-not (Test-Path $KeystoreProperties)) {
    throw 'Release signing is not configured: keystore.properties is missing.'
}

$StoreFileLine = Get-Content $KeystoreProperties | Where-Object { $_ -match '^\s*storeFile\s*=' } | Select-Object -First 1
if (-not $StoreFileLine) {
    throw 'Release signing is not configured: storeFile is missing in keystore.properties.'
}
$StoreFile = ($StoreFileLine -split '=', 2)[1].Trim()
if (-not (Test-Path (Join-Path $ProjectRoot $StoreFile))) {
    throw "Release signing key was not found: $StoreFile"
}

$DockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $DockerCommand) {
    throw 'Docker CLI was not found. Start Docker Desktop and ensure docker.exe is on PATH.'
}

$DockerOs = & docker info --format '{{.OSType}}'
if ($LASTEXITCODE -ne 0) {
    throw 'Cannot connect to Docker. Start Docker Desktop and retry.'
}
if ($DockerOs.Trim() -ne 'linux') {
    throw 'Docker is using Windows containers. Switch Docker Desktop to Linux containers.'
}

& docker build --platform linux/amd64 --tag $BuilderImage --file (Join-Path $ProjectRoot 'docker/android-builder/Dockerfile') $ProjectRoot
if ($LASTEXITCODE -ne 0) {
    throw 'Could not build the Android builder Docker image.'
}

$null = & docker volume create $GradleCacheVolume
if ($LASTEXITCODE -ne 0) {
    throw 'Could not create the Gradle cache volume.'
}
$null = & docker volume create $AndroidHomeVolume
if ($LASTEXITCODE -ne 0) {
    throw 'Could not create the Android home volume.'
}

$ProjectMount = "type=bind,source=$ProjectRoot,target=/workspace"
& docker run --rm `
    --platform linux/amd64 `
    --env 'HOME=/tmp' `
    --env 'JAVA_TOOL_OPTIONS=-Duser.home=/tmp' `
    --env 'GRADLE_USER_HOME=/tmp/gradle-home' `
    --mount "type=volume,source=$GradleCacheVolume,target=/tmp/gradle-home" `
    --mount "type=volume,source=$AndroidHomeVolume,target=/tmp/.android" `
    --mount $ProjectMount `
    --workdir /workspace `
    $BuilderImage `
    sh ./gradlew --no-watch-fs @GradleArgs
if ($LASTEXITCODE -ne 0) {
    throw 'Release build failed. See the Gradle output above.'
}

$ApkPath = Join-Path $ProjectRoot 'app/build/outputs/apk/release/app-release.apk'
if (-not (Test-Path $ApkPath)) {
    throw "Build finished, but APK was not found at $ApkPath"
}
Write-Host "`nRelease APK: $ApkPath"
