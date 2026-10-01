# Demarrage rapide quotidien de PluriBourse + PrinterBridge, sur une install deja en place. Demarre
# Docker Desktop si besoin, les containers, et (re)lance PrinterBridge  -  sans verification de
# prerequis/depot/paquets. Pas un substitut a une premiere installation.
#
# Usage : .\pluribourse-start.ps1 (depuis un PowerShell "Executer en tant qu'administrateur")
#
# Difference avec l'equivalent Linux (pluribourse-start.sh) : pas de proposition de (re)connexion
# WiFi ici - ce prompt existe cote Linux pour un Raspberry Pi deplace d'un lieu d'evenement a
# l'autre, un scenario qui ne concerne pas un poste de bureau Windows fixe.

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
# Suppose une propriete INSTALLDIR (convention WiX) -- voir pluribourse-install.ps1 pour le detail.
$PrinterBridgeInstallDir = "C:\Program Files\PrinterBridge"

function Log($Message) {
    Write-Host "==> $Message"
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

Log "Rappel : pour recuperer les dernieres versions de PluriBourse/PrinterBridge, lance '.\pluribourse-update.ps1' separement (ce script ne verifie aucune nouvelle version)."

# --- 1. Docker Desktop ---
# Appli GUI, pas un service actif au boot -- il faut la lancer et attendre que le moteur reponde.
$dockerRunning = $false
try { docker info *> $null; $dockerRunning = $LASTEXITCODE -eq 0 } catch {}

if (-not $dockerRunning) {
    Log "Demarrage de Docker Desktop..."
    Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        try {
            docker info *> $null
            if ($LASTEXITCODE -eq 0) { $dockerRunning = $true; break }
        } catch {}
        Start-Sleep -Seconds 3
    }
    if (-not $dockerRunning) {
        Write-Error "Docker Desktop ne repond pas apres 90s. Lance-le manuellement, attends qu'il soit pret, puis relance ce script."
        exit 1
    }
}

# --- 2. docker compose up (pas de pull  -  le role de pluribourse-update.ps1) ---
$ComposeDir = Join-Path $InstallDir ".docker"
Push-Location $ComposeDir
try {
    Log "Demarrage de PluriBourse (docker compose up)..."
    Invoke-ComposeUpWithRetry
} finally {
    Pop-Location
}
Log "PluriBourse est demarre."

# --- 3. (Re)demarrer PrinterBridge ---
$existingProcess = Get-Process -Name "PrinterBridge" -ErrorAction SilentlyContinue
if ($existingProcess) {
    Log "PrinterBridge deja actif."
} else {
    Log "Demarrage de PrinterBridge..."
    Start-Process "$PrinterBridgeInstallDir\PrinterBridge.exe"
}

Log "Demarrage termine."
Log "PluriBourse : http://localhost/"
Log "PrinterBridge : actif sur 127.0.0.1 (port 7420)"
