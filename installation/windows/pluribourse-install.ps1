# Installe PluriBourse (Docker Compose) + PrinterBridge sur une machine Windows neuve, pour une
# PREMIERE installation uniquement.
#
# Usage (machine neuve) :
#   irm https://raw.githubusercontent.com/Manerial/PluriBourse/main/installation/windows/pluribourse-install.ps1 | iex
# Usage (repo deja clone) :
#   .\pluribourse-install.ps1
#
# Idempotent, ne prend aucun argument (aligne sur pluribourse-install.sh cote Linux). Pour la suite :
# pluribourse-update.ps1 (mise a jour) et pluribourse-start.ps1 (demarrage quotidien).
#
# A lancer depuis un PowerShell "Executer en tant qu'administrateur".
#
# Differences volontaires vs Linux (pluribourse-install.sh/add-printer.sh), pas des oublis : pas de
# liaison Bluetooth ni de detection de passerelle Docker (Windows/Docker Desktop gerent ca nativement),
# pas de supervision sur crash type systemd (point ouvert, cf. CLAUDE.md de PrinterBridge).

#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"

# #Requires seul ne suffit pas : ignore silencieusement si le script est execute via `irm | iex`
# (usage documente plus haut) -- verification explicite, comme le `[[ $EUID -ne 0 ]]` cote Linux.
$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Ce script doit etre execute depuis un PowerShell 'Executer en tant qu'administrateur'."
    exit 1
}

if ($args.Count -gt 0) {
    Write-Error "Ce script ne prend plus d'argument -- voir pluribourse-update.ps1 (mise a jour) et pluribourse-start.ps1 (demarrage)."
    exit 1
}

$InstallDir = "C:\PluriBourse"
$RepoUrl = "https://github.com/Manerial/PluriBourse.git"
$PrinterBridgeRepo = "Manerial/PrinterBridge"
# Chemin impose plutot que de laisser --win-dir-chooser demander a l'utilisateur -- necessaire pour
# retrouver PrinterBridge.exe plus bas. Suppose une propriete INSTALLDIR (convention WiX), pas
# encore confirme sur un vrai .msi construit.
$PrinterBridgeInstallDir = "C:\Program Files\PrinterBridge"

function Log($Message) {
    Write-Host "==> $Message"
}

function New-RandomHex([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    -join ($buffer | ForEach-Object { $_.ToString("x2") })
}

# Necessaire apres un `winget install` : le PATH systeme est mis a jour mais pas celui de ce process.
function Update-SessionPath {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
        [System.Environment]::GetEnvironmentVariable("Path", "User")
}

# $ErrorActionPreference = "Stop" ne couvre pas le code de sortie d'un executable natif -- sans ca,
# un `docker compose`/`git`/`msiexec` en echec laisserait le script continuer en silence.
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
        throw "L'installation du .msi PrinterBridge a echoue (code $($process.ExitCode))."
    }
}

# `restart: unless-stopped` relance les containers sans respecter `depends_on` -- juste apres le
# demarrage de Docker Desktop, le backend peut tenter de joindre la base avant qu'elle soit prete.
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

# --- 0. WSL2 (prerequis Docker Desktop) ---
# `wsl --install` exige presque toujours un redemarrage sur une machine ou WSL2 n'etait pas deja
# actif -- impossible d'enchainer sans reboot.
if (-not (Get-Command wsl -ErrorAction SilentlyContinue)) {
    Write-Error "WSL n'est pas disponible sur cette version de Windows (Windows 10 1903+ ou Windows 11 requis)."
    exit 1
}
wsl --status *> $null
if ($LASTEXITCODE -ne 0) {
    Log "Installation de WSL2 (prerequis Docker Desktop)..."
    wsl --install --no-distribution
    Write-Host ""
    Write-Host "WSL2 vient d'etre installe. Un REDEMARRAGE est necessaire avant de continuer." -ForegroundColor Yellow
    Write-Host "Redemarre la machine, puis relance ce script." -ForegroundColor Yellow
    exit 0
}

# --- 1. Prerequis minimaux (git) ---
# curl est deja fourni par Windows ; jq/openssl n'ont pas d'equivalent a installer (JSON natif via
# Invoke-RestMethod, aleatoire via New-RandomHex).
Log "Verification des prerequis (git)..."
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Error "git n'est pas installe et winget n'est pas disponible pour l'installer automatiquement. Installe git manuellement (https://git-scm.com) puis relance ce script."
        exit 1
    }
    Log "Installation de git..."
    winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
    Update-SessionPath
}

# --- 2. Cloner PluriBourse ---
if (Test-Path "$InstallDir\.git") {
    Log "PluriBourse deja present dans $InstallDir (utilise pluribourse-update.ps1 pour le mettre a jour)."
} else {
    Log "Telechargement de PluriBourse dans $InstallDir..."
    git clone $RepoUrl $InstallDir
    Assert-LastExitCodeSuccess "git clone"
}

# --- 3. Docker Desktop (installation si absente) ---
$dockerInstalled = [bool](Get-Command docker -ErrorAction SilentlyContinue)
if ($dockerInstalled) {
    Log "Docker deja installe, rien a faire."
} else {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Write-Error "winget n'est pas disponible. Installe Docker Desktop manuellement : https://www.docker.com/products/docker-desktop"
        exit 1
    }
    Log "Installation de Docker Desktop..."
    winget install --id Docker.DockerDesktop -e --source winget --accept-package-agreements --accept-source-agreements
    Update-SessionPath
}

# Docker Desktop est une appli GUI, pas un service actif au boot -- il faut la lancer et attendre
# que le moteur reponde (jusqu'a 90s au premier demarrage).
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
        Write-Error "Docker Desktop ne repond pas apres 90s. Lance-le manuellement (accepte l'accord de licence si demande au premier lancement), attends qu'il soit pret, puis relance ce script."
        exit 1
    }
}

$ComposeDir = Join-Path $InstallDir ".docker"
$EnvFile = Join-Path $ComposeDir ".env"

# --- 4. Fichier .env  -  genere une seule fois, jamais regenere ni ecrase ---
if (Test-Path $EnvFile) {
    Log ".env deja present, conserve tel quel."
} else {
    Log "Generation d'un .env avec des mots de passe aleatoires..."
    $dbPassword = New-RandomHex 24
    $rootPassword = New-RandomHex 24
    @"
DB_NAME=pluribourse
DB_PASSWORD=$dbPassword
MYSQL_ROOT_PASSWORD=$rootPassword
SPRING_PROFILES_ACTIVE=prod
"@ | Set-Content -Path $EnvFile -Encoding utf8NoBOM
}

# --- 5. docker compose up ---
Push-Location $ComposeDir
try {
    Log "Demarrage de PluriBourse (docker compose pull && up)..."
    docker compose pull
    Assert-LastExitCodeSuccess "docker compose pull"
    Invoke-ComposeUpWithRetry
} finally {
    Pop-Location
}
Log "PluriBourse est demarre."

# Pas de detection de passerelle Docker ici : Docker Desktop resout host.docker.internal nativement,
# le conteneur backend joint PrinterBridge sur 127.0.0.1 sans configuration supplementaire.

# --- 6. Installer PrinterBridge ---
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
if (-not $installedVersion) {
    Log "Installation de PrinterBridge $latestVersion..."
    Install-PrinterBridgeMsi -Url $asset.browser_download_url -FileName $asset.name
    $packageUpdated = $true
} else {
    Log "PrinterBridge deja installe ($installedVersion, utilise pluribourse-update.ps1 pour le mettre a jour)."
}

# --- 7. Demarrer PrinterBridge ---
# $packageUpdated couvre le cas rare d'un process deja lance avant cette installation (vu en dev) --
# sans relance il resterait sur l'ancien binaire.
$existingProcess = Get-Process -Name "PrinterBridge" -ErrorAction SilentlyContinue
if ($existingProcess) {
    if ($packageUpdated) {
        Log "Redemarrage de PrinterBridge pour appliquer l'installation..."
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

Log "Installation terminee."
Log "PluriBourse : http://localhost/"
Log "PrinterBridge : actif sur 127.0.0.1 (port 7420)"
Log ""
Log "Imprimante thermique : appaire-la normalement via les parametres Bluetooth Windows, le port COM"
Log "se cree automatiquement  -  pas d'etape manuelle supplementaire contrairement a Linux."
Log ""
Log "Pour un demarrage rapide au quotidien : .\pluribourse-start.ps1"
Log "Pour recuperer les dernieres versions : .\pluribourse-update.ps1"
