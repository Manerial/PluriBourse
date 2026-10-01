# Met a jour une installation PluriBourse + PrinterBridge deja en place : derniere version du code
# (git pull), dernieres images Docker, et derniere version de PrinterBridge.
#
# Ne reverifie aucun prerequis -- role de pluribourse-install.ps1, a lancer une seule fois en premier.
#
# Usage : .\pluribourse-update.ps1 (depuis un PowerShell "Executer en tant qu'administrateur")
#
# Differences vs Linux (pluribourse-update.sh), pas des oublis : scripts utilitaires non recopies
# ailleurs (tourne directement depuis le depot clone, pas de /usr/local/sbin) ; pas de liaison
# Bluetooth ni de detection de passerelle Docker -- voir pluribourse-install.ps1 pour le detail.

#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"

# #Requires seul ne suffit pas si ce script est un jour lance via `irm | iex` -- voir
# pluribourse-install.ps1 pour le detail.
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Ce script doit etre execute depuis un PowerShell 'Executer en tant qu'administrateur'."
    exit 1
}

$InstallDir = "C:\PluriBourse"
$PrinterBridgeRepo = "Manerial/PrinterBridge"
# Suppose une propriete INSTALLDIR (convention WiX) -- voir pluribourse-install.ps1 pour le detail.
$PrinterBridgeInstallDir = "C:\Program Files\PrinterBridge"

function Log($Message) {
    Write-Host "==> $Message"
}

# $ErrorActionPreference = "Stop" ne couvre pas le code de sortie d'un executable natif -- sans ca,
# un `git`/`docker compose`/`msiexec` en echec laisserait le script continuer en silence.
function Assert-LastExitCodeSuccess([string]$Description) {
    if ($LASTEXITCODE -ne 0) {
        throw "$Description a echoue (code $LASTEXITCODE)."
    }
}

function Install-PrinterBridgeMsi([string]$Url, [string]$FileName) {
    $tmpMsi = Join-Path $env:TEMP $FileName
    Invoke-WebRequest -Uri $Url -OutFile $tmpMsi
    # Start-Process ne peuple pas $LASTEXITCODE -- -PassThru recupere le code de sortie a verifier.
    $process = Start-Process msiexec.exe -ArgumentList "/i `"$tmpMsi`" /qn INSTALLDIR=`"$PrinterBridgeInstallDir`"" -Wait -PassThru
    Remove-Item $tmpMsi -Force
    if ($process.ExitCode -ne 0) {
        throw "La mise a jour du .msi PrinterBridge a echoue (code $($process.ExitCode))."
    }
}

# `restart: unless-stopped` + `depends_on` : voir pluribourse-install.ps1 pour le detail de la course
# au demarrage que ce retry absorbe.
$ComposeUpMaxAttempts = 5
$ComposeUpRetryDelaySeconds = 15

function Invoke-ComposeUpWithRetry {
    for ($attempt = 1; $attempt -le $ComposeUpMaxAttempts; $attempt++) {
        docker compose up -d
        if ($LASTEXITCODE -eq 0) { return }
        if ($attempt -ge $ComposeUpMaxAttempts) {
            throw "docker compose up a echoue apres $ComposeUpMaxAttempts tentatives."
        }
        Log "docker compose up a echoue (tentative $attempt/$ComposeUpMaxAttempts), nouvel essai dans ${ComposeUpRetryDelaySeconds}s..."
        Start-Sleep -Seconds $ComposeUpRetryDelaySeconds
    }
}

function Get-InstalledPrinterBridgeVersion {
    $paths = @(
        "HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKLM:\Software\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*"
    )
    Get-ItemProperty -Path $paths -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -eq "PrinterBridge" } |
        Select-Object -First 1 -ExpandProperty DisplayVersion
}

if (-not (Test-Path "$InstallDir\.git")) {
    Write-Error "PluriBourse n'est pas encore installe dans $InstallDir -- lance d'abord pluribourse-install.ps1."
    exit 1
}
if (-not (Get-InstalledPrinterBridgeVersion)) {
    Write-Error "PrinterBridge n'est pas encore installe -- lance d'abord pluribourse-install.ps1."
    exit 1
}

# --- 1. Code PluriBourse ---
Log "Recuperation des dernieres modifications de PluriBourse..."
git -C $InstallDir pull --ff-only
Assert-LastExitCodeSuccess "git pull"

# --- 2. Images Docker ---
$ComposeDir = Join-Path $InstallDir ".docker"
Push-Location $ComposeDir
try {
    Log "Mise a jour des images Docker (docker compose pull && up)..."
    docker compose pull
    Assert-LastExitCodeSuccess "docker compose pull"
    Invoke-ComposeUpWithRetry
} finally {
    Pop-Location
}
Log "PluriBourse est a jour et demarre."

# --- 3. Mettre a jour PrinterBridge si une nouvelle version existe ---
Log "Verification de la derniere version de PrinterBridge..."
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$PrinterBridgeRepo/releases/latest"
$asset = $release.assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
if (-not $asset) {
    Write-Error "Impossible de trouver le .msi de PrinterBridge dans la derniere release GitHub."
    exit 1
}
$latestVersion = $release.tag_name -replace '^v', ''
$installedVersion = Get-InstalledPrinterBridgeVersion

$packageUpdated = $false
if ($installedVersion -ne $latestVersion) {
    Log "Mise a jour de PrinterBridge $installedVersion -> $latestVersion..."
    Install-PrinterBridgeMsi -Url $asset.browser_download_url -FileName $asset.name
    $packageUpdated = $true
} else {
    Log "PrinterBridge deja a jour ($installedVersion)."
}

# --- 4. (Re)demarrer PrinterBridge ---
$existingProcess = Get-Process -Name "PrinterBridge" -ErrorAction SilentlyContinue
if ($existingProcess) {
    if ($packageUpdated) {
        Log "Redemarrage de PrinterBridge pour appliquer la mise a jour..."
        $existingProcess | Stop-Process -Force
        Start-Sleep -Seconds 2
        Start-Process "$PrinterBridgeInstallDir\PrinterBridge.exe"
    } else {
        Log "PrinterBridge deja actif."
    }
} else {
    Log "Demarrage de PrinterBridge..."
    Start-Process "$PrinterBridgeInstallDir\PrinterBridge.exe"
}

Log "Mise a jour terminee."
Log "PluriBourse : http://localhost/"
Log "PrinterBridge : actif sur 127.0.0.1 (port 7420)"
