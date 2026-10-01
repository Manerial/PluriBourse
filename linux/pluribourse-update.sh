#!/usr/bin/env bash
# Met à jour une installation PluriBourse + PrinterBridge déjà en place : dernière version du code
# (git pull), scripts utilitaires rafraîchis (ont pu être corrigés entre deux versions), dernières
# images Docker, et dernière version de PrinterBridge.
#
# Ne revérifie aucun prérequis (curl, git, Docker Engine...) — c'est le rôle de pluribourse-install.sh, à lancer
# une seule fois pour une première installation. Ce script suppose que tout est déjà en place.
#
# Usage : sudo pluribourse-update.sh

set -euo pipefail

INSTALL_DIR="/opt/pluribourse"
COMPOSE_DIR="${INSTALL_DIR}/.docker"
PRINTERBRIDGE_REPO="Manerial/PrinterBridge"
DOCKER_DEFAULT_NETWORK="bridge"

log() {
    echo "==> $*"
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "Ce script doit être lancé avec sudo : sudo pluribourse-update.sh" >&2
    exit 1
fi

if [[ -z "${SUDO_USER:-}" || "${SUDO_USER}" == "root" ]]; then
    echo "Lance ce script avec 'sudo' depuis ton compte utilisateur habituel, pas en root direct." >&2
    exit 1
fi
ADMIN_USER="${SUDO_USER}"
ADMIN_UID="$(id -u "${ADMIN_USER}")"
ADMIN_HOME="$(getent passwd "${ADMIN_USER}" | cut -d: -f6)"

if [[ ! -d "/run/user/${ADMIN_UID}" ]]; then
    echo "Aucune session active trouvée pour ${ADMIN_USER} (/run/user/${ADMIN_UID} n'existe pas)." >&2
    echo "Lance ce script depuis une session de bureau ou un terminal ouvert en tant que ${ADMIN_USER}." >&2
    exit 1
fi

run_as_admin() {
    sudo -u "${ADMIN_USER}" XDG_RUNTIME_DIR="/run/user/${ADMIN_UID}" "$@"
}

if [[ ! -d "${INSTALL_DIR}/.git" ]]; then
    echo "PluriBourse n'est pas encore installé dans ${INSTALL_DIR} — lance d'abord pluribourse-install.sh." >&2
    exit 1
fi
if ! dpkg-query -W -f='${Version}' printerbridge >/dev/null 2>&1; then
    echo "PrinterBridge n'est pas encore installé — lance d'abord pluribourse-install.sh." >&2
    exit 1
fi

# --- 1. Code PluriBourse ---
log "Récupération des dernières modifications de PluriBourse..."
git -C "${INSTALL_DIR}" pull --ff-only
chown -R "${ADMIN_USER}:${ADMIN_USER}" "${INSTALL_DIR}"

# --- 2. Scripts utilitaires (peuvent avoir ete corriges entre deux versions, cf. CLAUDE.md) ---
log "Mise à jour des scripts utilitaires..."
install -m 0755 "${INSTALL_DIR}/linux/bind-thermal-printers.sh" /usr/local/sbin/bind-thermal-printers.sh
install -m 0644 "${INSTALL_DIR}/linux/bind-thermal-printers.service" /etc/systemd/system/bind-thermal-printers.service
install -m 0755 "${INSTALL_DIR}/linux/add-printer.sh" /usr/local/sbin/add-printer.sh
install -m 0755 "${INSTALL_DIR}/linux/pluribourse-start.sh" /usr/local/sbin/pluribourse-start.sh
install -m 0755 "${INSTALL_DIR}/linux/pluribourse-update.sh" /usr/local/sbin/pluribourse-update.sh
install -m 0755 "${INSTALL_DIR}/pluribourse-install.sh" /usr/local/sbin/pluribourse-install.sh
systemctl daemon-reload
systemctl restart bind-thermal-printers.service

# --- 3. Images Docker ---
# `restart: unless-stopped` + `depends_on` : voir pluribourse-start.sh pour le detail de la course
# au demarrage que ce retry absorbe (moins probable ici qu'au boot, mais sans cout a garder).
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

log "Mise à jour des images Docker (docker compose pull && up)..."
(cd "${COMPOSE_DIR}" && docker compose pull && compose_up_with_retry)
log "PluriBourse est à jour et démarré."

# --- 4. Détecter la passerelle du réseau Docker ---
GATEWAY="$(docker network inspect "${DOCKER_DEFAULT_NETWORK}" --format '{{(index .IPAM.Config 0).Gateway}}')"
log "Adresse de la passerelle Docker détectée : ${GATEWAY}"

# --- 5. Mettre à jour PrinterBridge si une nouvelle version existe ---
DEB_ARCH="$(dpkg --print-architecture)"
RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/${PRINTERBRIDGE_REPO}/releases/latest")"
DEB_URL="$(echo "${RELEASE_JSON}" | jq -r --arg suffix "_${DEB_ARCH}.deb" '.assets[] | select(.name | endswith($suffix)) | .browser_download_url')"
LATEST_VERSION="$(echo "${RELEASE_JSON}" | jq -r '.tag_name' | sed 's/^v//')"

if [[ -z "${DEB_URL}" || "${DEB_URL}" == "null" ]]; then
    echo "Impossible de trouver le .deb de PrinterBridge pour l'architecture ${DEB_ARCH} dans la dernière release GitHub." >&2
    exit 1
fi

INSTALLED_VERSION="$(dpkg-query -W -f='${Version}' printerbridge)"

PACKAGE_UPDATED=false
if [[ "${INSTALLED_VERSION}" != "${LATEST_VERSION}" ]]; then
    log "Mise à jour de PrinterBridge ${INSTALLED_VERSION} -> ${LATEST_VERSION}..."
    TMP_DEB="$(mktemp --suffix=.deb)"
    curl -fsSL "${DEB_URL}" -o "${TMP_DEB}"
    apt-get install -y "${TMP_DEB}"
    rm -f "${TMP_DEB}"
    PACKAGE_UPDATED=true
else
    log "PrinterBridge déjà à jour (${INSTALLED_VERSION})."
fi

# --- 6. Surcharge systemd : adresse du bridge Docker ---
# Généré ici, jamais dans PrinterBridge lui-même (cf. CLAUDE.md de PrinterBridge).
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
    if [[ "${CONFIG_CHANGED}" == "true" || "${PACKAGE_UPDATED}" == "true" ]]; then
        log "Redémarrage de PrinterBridge pour appliquer la mise à jour..."
        run_as_admin systemctl --user restart printerbridge
    else
        log "PrinterBridge déjà actif."
    fi
else
    log "Démarrage de PrinterBridge..."
    run_as_admin systemctl --user start printerbridge
fi

log "Mise à jour terminée."
log ""
log "Pour ajouter une imprimante (thermique Bluetooth ou réseau/A4) : sudo add-printer.sh"
