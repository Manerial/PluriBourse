#!/usr/bin/env bash
# Installe PluriBourse (Docker Compose) + PrinterBridge sur une machine Debian/Ubuntu, pour une
# PREMIERE installation uniquement.
#
# Usage (machine neuve, rien d'installé) :
#   curl -fsSL https://raw.githubusercontent.com/Manerial/PluriBourse/main/pluribourse-install.sh | sudo bash
#
# Usage (repo déjà cloné) :
#   sudo ./pluribourse-install.sh
#
# Idempotent (relancer ne refait que ce qui manque), mais ne revérifie plus rien après la première
# fois : pour mettre à jour une install existante, voir pluribourse-update.sh ; pour un démarrage
# rapide au quotidien, voir pluribourse-start.sh. Ce script-ci installe les prérequis (Docker Engine,
# paquets système) et PrinterBridge une bonne fois pour toutes.
#
# À lancer avec sudo depuis le compte utilisateur habituel de l'admin (pas en root direct) :
# PrinterBridge tourne comme service systemd --user de ce compte, pas de root.

set -euo pipefail

if [[ $# -gt 0 ]]; then
    echo "Ce script ne prend plus d'argument — voir pluribourse-update.sh (mise à jour) et pluribourse-start.sh (démarrage)." >&2
    exit 1
fi

INSTALL_DIR="/opt/pluribourse"
REPO_URL="https://github.com/Manerial/PluriBourse.git"
PRINTERBRIDGE_REPO="Manerial/PrinterBridge"
# Le "bridge" par défaut de Docker (pas "pluribourse_default", le réseau propre au projet Compose) —
# constaté en pratique : `host.docker.internal` (extra_hosts: host-gateway, docker-compose.yml) résout
# TOUJOURS vers la passerelle de ce bridge par défaut, quel que soit le réseau auquel le conteneur est
# réellement connecté. Un `curl`/`wget` depuis le conteneur backend vers host.docker.internal a montré
# qu'il résolvait vers l'IP de "bridge", pas celle de "pluribourse_default", malgré backend attaché à
# ce dernier — un comportement Docker global à la machine, pas par réseau.
DOCKER_DEFAULT_NETWORK="bridge"

log() {
    echo "==> $*"
}

# `restart: unless-stopped` fait que Docker relance tous les containers en parallèle dès que le
# daemon démarre, sans respecter l'ordre `depends_on` (appliqué uniquement par `docker compose up`
# lui-même) — backend peut donc tenter de se connecter à la base avant qu'elle ait fini de démarrer
# et planter presque instantanément. Un nouvel essai après un court délai laisse le temps à la base
# de finir son initialisation.
COMPOSE_UP_MAX_ATTEMPTS=5
COMPOSE_UP_RETRY_DELAY_SECONDS=15

compose_up_with_retry() {
    local attempt=1
    while true; do
        if docker compose up -d; then
            return 0
        fi
        if (( attempt >= COMPOSE_UP_MAX_ATTEMPTS )); then
            echo "docker compose up a échoué après ${COMPOSE_UP_MAX_ATTEMPTS} tentatives." >&2
            return 1
        fi
        log "docker compose up a échoué (tentative ${attempt}/${COMPOSE_UP_MAX_ATTEMPTS}), nouvel essai dans ${COMPOSE_UP_RETRY_DELAY_SECONDS}s..."
        sleep "${COMPOSE_UP_RETRY_DELAY_SECONDS}"
        attempt=$((attempt + 1))
    done
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "Ce script doit être lancé avec sudo : sudo bash pluribourse-install.sh" >&2
    exit 1
fi

if [[ -z "${SUDO_USER:-}" || "${SUDO_USER}" == "root" ]]; then
    echo "Lance ce script avec 'sudo' depuis ton compte utilisateur habituel, pas en root direct." >&2
    echo "PrinterBridge doit être configuré pour cet utilisateur, pas pour root." >&2
    exit 1
fi
ADMIN_USER="${SUDO_USER}"
ADMIN_UID="$(id -u "${ADMIN_USER}")"
ADMIN_HOME="$(getent passwd "${ADMIN_USER}" | cut -d: -f6)"

# systemctl --user a besoin d'une vraie session utilisateur active (voir CLAUDE.md de PrinterBridge,
# "Failed to connect to bus") — sans /run/user/<uid>, aucune chance que ça marche, autant le dire
# clairement plutôt que de laisser échouer avec une erreur D-Bus cryptique plus loin.
if [[ ! -d "/run/user/${ADMIN_UID}" ]]; then
    echo "Aucune session active trouvée pour ${ADMIN_USER} (/run/user/${ADMIN_UID} n'existe pas)." >&2
    echo "Lance ce script depuis une session de bureau ou un terminal ouvert en tant que ${ADMIN_USER}," >&2
    echo "pas depuis une connexion SSH brute ou un 'su' sans session complète." >&2
    exit 1
fi

run_as_admin() {
    sudo -u "${ADMIN_USER}" XDG_RUNTIME_DIR="/run/user/${ADMIN_UID}" "$@"
}

# Nettoyage défensif : un docker.list laissé par un run précédent interrompu (ex. mauvais dépôt,
# panne réseau en plein milieu) fait échouer TOUT apt-get update qui suit — y compris pour des
# paquets sans rapport comme curl/git plus bas — bien après que ce script ait par ailleurs été
# corrigé. On ne le touche que si Docker n'est pas déjà fonctionnel : un fichier qui marche déjà
# n'a pas besoin d'être régénéré.
if [[ -f /etc/apt/sources.list.d/docker.list ]] \
        && ! (command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1); then
    rm -f /etc/apt/sources.list.d/docker.list
fi

# --- 1. Prérequis minimaux pour pouvoir cloner PluriBourse (curl/git) ---
# git n'est pas garanti présent sur une install Debian/Ubuntu minimale ; curl l'est presque toujours
# (nécessaire pour même récupérer ce script via `curl | sudo bash`), mais on le redemande explicitement
# par sécurité plutôt que de supposer.
log "Vérification des prérequis (curl, git)..."
apt-get update -qq
apt-get install -y -qq curl git

# --- 2. Cloner PluriBourse ---
if [[ -d "${INSTALL_DIR}/.git" ]]; then
    log "PluriBourse déjà présent dans ${INSTALL_DIR} (utilise pluribourse-update.sh pour le mettre à jour)."
else
    log "Téléchargement de PluriBourse dans ${INSTALL_DIR}..."
    git clone "${REPO_URL}" "${INSTALL_DIR}"
fi
chown -R "${ADMIN_USER}:${ADMIN_USER}" "${INSTALL_DIR}"

# --- 3. Docker Engine ---
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    log "Docker déjà installé, rien à faire."
else
    log "Installation de Docker Engine (dépôt officiel Docker)..."
    apt-get remove -y docker docker-engine docker.io containerd runc >/dev/null 2>&1 || true
    apt-get install -y ca-certificates
    install -m 0755 -d /etc/apt/keyrings

    # Debian et Ubuntu ont chacun leur propre dépôt Docker, avec des noms de code différents
    # (Debian : bookworm, trixie... ; Ubuntu : jammy, noble, resolute...) — se tromper de dépôt donne
    # "does not have a Release file" (constaté : WSL Ubuntu "resolute" contre le dépôt Debian).
    OS_ID="$(. /etc/os-release && echo "$ID")"
    OS_CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
    case "${OS_ID}" in
        debian) DOCKER_APT_REPO="debian" ;;
        ubuntu) DOCKER_APT_REPO="ubuntu" ;;
        *)
            echo "OS non supporté pour l'installation automatique de Docker : ${OS_ID} (attendu : debian ou ubuntu)." >&2
            exit 1
            ;;
    esac

    curl -fsSL "https://download.docker.com/linux/${DOCKER_APT_REPO}/gpg" -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${DOCKER_APT_REPO} ${OS_CODENAME} stable" \
        | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update -qq
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

log "Vérification des autres prérequis (jq, openssl, dbus-user-session)..."
apt-get install -y -qq jq openssl dbus-user-session

# --- 3bis. Service de liaison Bluetooth (imprimantes thermiques) + scripts utilitaires ---
# rfcomm bind ne survit pas à un redémarrage (cf. CLAUDE.md de PrinterBridge) — sans ça, l'admin
# devrait relier chaque imprimante à la main après chaque reboot, en plus une par une. Le fichier
# de config (une MAC par ligne) n'est copié qu'une fois, jamais régénéré ni écrasé : c'est à
# l'admin de le remplir avec ses propres imprimantes, ce script ne peut pas les deviner. Le script
# et l'unité systemd eux-mêmes vivent dans linux/ (fichiers versionnés, pas de heredoc ici).
log "Configuration du service de liaison Bluetooth (imprimantes thermiques)..."
BLUETOOTH_PRINTERS_CONF="/etc/printerbridge/bluetooth-printers.conf"

install -d /etc/printerbridge
if [[ -f "${BLUETOOTH_PRINTERS_CONF}" ]]; then
    log "Config des imprimantes Bluetooth déjà présente, conservée telle quelle."
else
    install -m 0644 "${INSTALL_DIR}/linux/bluetooth-printers.conf.example" "${BLUETOOTH_PRINTERS_CONF}"
fi

install -m 0755 "${INSTALL_DIR}/linux/bind-thermal-printers.sh" /usr/local/sbin/bind-thermal-printers.sh
install -m 0644 "${INSTALL_DIR}/linux/bind-thermal-printers.service" /etc/systemd/system/bind-thermal-printers.service
# add-printer.sh, pluribourse-start.sh et pluribourse-update.sh (cf. CLAUDE.md) sont a lancer
# explicitement par l'admin — pas automatiquement ici, juste rendus disponibles sur le PATH.
install -m 0755 "${INSTALL_DIR}/linux/add-printer.sh" /usr/local/sbin/add-printer.sh
install -m 0755 "${INSTALL_DIR}/linux/pluribourse-start.sh" /usr/local/sbin/pluribourse-start.sh
install -m 0755 "${INSTALL_DIR}/linux/pluribourse-update.sh" /usr/local/sbin/pluribourse-update.sh

systemctl daemon-reload
systemctl enable --now bind-thermal-printers.service

COMPOSE_DIR="${INSTALL_DIR}/.docker"
ENV_FILE="${COMPOSE_DIR}/.env"

# --- 4. Fichier .env — généré une seule fois, jamais régénéré ni écrasé ---
if [[ -f "${ENV_FILE}" ]]; then
    log ".env déjà présent, conservé tel quel."
else
    log "Génération d'un .env avec des mots de passe aléatoires..."
    DB_PASSWORD="$(openssl rand -hex 24)"
    MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
    cat > "${ENV_FILE}" <<EOF
DB_NAME=pluribourse
DB_PASSWORD=${DB_PASSWORD}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
SPRING_PROFILES_ACTIVE=prod
EOF
    chown "${ADMIN_USER}:${ADMIN_USER}" "${ENV_FILE}"
    chmod 600 "${ENV_FILE}"
fi

# --- 5. docker compose up ---
# `depends_on: condition: service_healthy` (docker-compose.yml) fait déjà attendre que
# db/backend soient healthy avant de démarrer ce qui en dépend — si un service ne devient jamais
# healthy, `docker compose up` remonte une erreur (set -e arrête le script ici, pas de boucle
# d'attente à réinventer).
log "Démarrage de PluriBourse (docker compose pull && up)..."
(cd "${COMPOSE_DIR}" && docker compose pull && compose_up_with_retry)
log "PluriBourse est démarré."

# --- 6. Détecter la passerelle du réseau Docker ---
GATEWAY="$(docker network inspect "${DOCKER_DEFAULT_NETWORK}" --format '{{(index .IPAM.Config 0).Gateway}}')"
log "Adresse de la passerelle Docker détectée : ${GATEWAY}"

# --- 7. Installer PrinterBridge ---
# dpkg --print-architecture donne directement le suffixe utilisé par jpackage pour nommer le .deb
# (amd64/arm64) — pas besoin de traduire depuis `uname -m` (x86_64/aarch64/...).
INSTALLED_VERSION="$(dpkg-query -W -f='${Version}' printerbridge 2>/dev/null || true)"

if [[ -z "${INSTALLED_VERSION}" ]]; then
    DEB_ARCH="$(dpkg --print-architecture)"
    RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/${PRINTERBRIDGE_REPO}/releases/latest")"
    DEB_URL="$(echo "${RELEASE_JSON}" | jq -r --arg suffix "_${DEB_ARCH}.deb" '.assets[] | select(.name | endswith($suffix)) | .browser_download_url')"
    LATEST_VERSION="$(echo "${RELEASE_JSON}" | jq -r '.tag_name' | sed 's/^v//')"

    if [[ -z "${DEB_URL}" || "${DEB_URL}" == "null" ]]; then
        echo "Impossible de trouver le .deb de PrinterBridge pour l'architecture ${DEB_ARCH} dans la dernière release GitHub." >&2
        exit 1
    fi

    log "Installation de PrinterBridge ${LATEST_VERSION}..."
    TMP_DEB="$(mktemp --suffix=.deb)"
    curl -fsSL "${DEB_URL}" -o "${TMP_DEB}"
    apt-get install -y "${TMP_DEB}"
    rm -f "${TMP_DEB}"
else
    log "PrinterBridge déjà installé (${INSTALLED_VERSION}, utilise pluribourse-update.sh pour le mettre à jour)."
fi

# --- 8. Surcharge systemd : adresse du bridge Docker ---
# Généré ici, jamais dans PrinterBridge lui-même (cf. CLAUDE.md de PrinterBridge) — PrinterBridge ne
# sait rien de Docker, il lit juste PRINTERBRIDGE_EXTRA_BIND_ADDRESSES.
OVERRIDE_DIR="${ADMIN_HOME}/.config/systemd/user/printerbridge.service.d"
DESIRED_OVERRIDE="[Service]
Environment=PRINTERBRIDGE_EXTRA_BIND_ADDRESSES=${GATEWAY}"

CURRENT_OVERRIDE=""
if [[ -f "${OVERRIDE_DIR}/override.conf" ]]; then
    CURRENT_OVERRIDE="$(cat "${OVERRIDE_DIR}/override.conf")"
fi

CONFIG_CHANGED=false
if [[ "${CURRENT_OVERRIDE}" != "${DESIRED_OVERRIDE}" ]]; then
    log "Configuration de PrinterBridge pour le réseau Docker (${GATEWAY})..."
    install -d -o "${ADMIN_USER}" -g "${ADMIN_USER}" "${OVERRIDE_DIR}"
    echo "${DESIRED_OVERRIDE}" > "${OVERRIDE_DIR}/override.conf"
    chown "${ADMIN_USER}:${ADMIN_USER}" "${OVERRIDE_DIR}/override.conf"
    CONFIG_CHANGED=true
else
    log "Configuration réseau de PrinterBridge déjà à jour."
fi

run_as_admin systemctl --user daemon-reload

if run_as_admin systemctl --user is-active --quiet printerbridge; then
    if [[ "${CONFIG_CHANGED}" == "true" ]]; then
        log "Redémarrage de PrinterBridge pour appliquer la nouvelle configuration réseau..."
        run_as_admin systemctl --user restart printerbridge
    else
        log "PrinterBridge déjà actif."
    fi
else
    log "Démarrage de PrinterBridge..."
    run_as_admin systemctl --user start printerbridge
fi

log "Installation terminée."
log "PluriBourse : http://localhost/"
log "PrinterBridge : actif sur 127.0.0.1 et ${GATEWAY} (port 7420)"
log ""
log "Pour ajouter une imprimante (thermique Bluetooth ou réseau/A4) : sudo add-printer.sh"
log "Pour un démarrage rapide au quotidien : sudo pluribourse-start.sh"
log "Pour mettre à jour PluriBourse/PrinterBridge : sudo pluribourse-update.sh"
