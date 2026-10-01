#!/usr/bin/env bash
# Démarrage rapide quotidien de PluriBourse + PrinterBridge, sur une install déjà en place —
# remplace l'ancien `pluribourse-install.sh --start`. Propose de (re)connecter le Pi à un réseau
# WiFi (utile en changeant de lieu d'événement), rappelle l'existence de `pluribourse-update.sh`
# pour une mise à jour, puis démarre les containers Docker, met à jour la configuration réseau de
# PrinterBridge et le (re)lance — sans repasser par aucune vérification de prérequis/dépôt/paquets.
# Pas un substitut à une première installation.
#
# Usage : sudo pluribourse-start.sh

set -euo pipefail

INSTALL_DIR="/opt/pluribourse"
COMPOSE_DIR="${INSTALL_DIR}/.docker"
# Voir pluribourse-install.sh pour le détail de ce choix (le bridge Docker par défaut, pas le réseau propre au
# projet Compose — host.docker.internal résout toujours vers celui-ci, cf. CLAUDE.md de PrinterBridge).
DOCKER_DEFAULT_NETWORK="bridge"

log() {
    echo "==> $*"
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "Ce script doit être lancé avec sudo : sudo pluribourse-start.sh" >&2
    exit 1
fi

if [[ -z "${SUDO_USER:-}" || "${SUDO_USER}" == "root" ]]; then
    echo "Lance ce script avec 'sudo' depuis ton compte utilisateur habituel, pas en root direct." >&2
    exit 1
fi
ADMIN_USER="${SUDO_USER}"
ADMIN_UID="$(id -u "${ADMIN_USER}")"
ADMIN_HOME="$(getent passwd "${ADMIN_USER}" | cut -d: -f6)"

# systemctl --user a besoin d'une vraie session utilisateur active (voir CLAUDE.md de PrinterBridge,
# "Failed to connect to bus").
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

# --- 1. WiFi (optionnel) ---
echo ""
read -rp "Connecter ce Raspberry Pi à un réseau WiFi (nouveau lieu d'événement, etc.) ? [o/N] " DO_WIFI
if [[ "${DO_WIFI,,}" == "o" ]]; then
    log "Réseaux WiFi disponibles :"
    nmcli dev wifi rescan 2>/dev/null || true
    nmcli dev wifi list

    echo ""
    read -rp "Nom du réseau (SSID) : " WIFI_SSID
    if [[ -z "${WIFI_SSID}" ]]; then
        echo "SSID vide, connexion WiFi ignorée." >&2
    else
        # --ask fait demander le mot de passe par nmcli lui-même, de façon interactive, plutôt que
        # de le passer en argument — un mot de passe inline a déjà posé un souci de parsing en
        # pratique ("secrets were required but not provided", cf. CLAUDE.md).
        if nmcli --ask dev wifi connect "${WIFI_SSID}"; then
            log "Connecté à ${WIFI_SSID}."
        else
            echo "Connexion à ${WIFI_SSID} échouée — verifie le SSID/mot de passe, ou utilise 'nmtui' pour un diagnostic plus detaille." >&2
        fi
    fi
fi

# --- 2. Rappel mise à jour ---
log "Rappel : pour mettre PluriBourse/PrinterBridge à jour, lance 'sudo pluribourse-update.sh' séparément (ce script ne vérifie aucune nouvelle version)."

# --- 3. docker compose up ---
# `depends_on: condition: service_healthy` (docker-compose.yml) fait déjà attendre que db/backend
# soient healthy avant de démarrer ce qui en dépend. Ne re-pull pas les images (le rôle de
# pluribourse-update.sh) — juste redémarrer ce qui est déjà présent localement.
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

log "Démarrage de PluriBourse (docker compose up)..."
(cd "${COMPOSE_DIR}" && compose_up_with_retry)
log "PluriBourse est démarré."

# --- 4. Détecter la passerelle du réseau Docker ---
GATEWAY="$(docker network inspect "${DOCKER_DEFAULT_NETWORK}" --format '{{(index .IPAM.Config 0).Gateway}}')"
log "Adresse de la passerelle Docker détectée : ${GATEWAY}"

# --- 5. Surcharge systemd : adresse du bridge Docker ---
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

log "Démarrage terminé."
log "PluriBourse : http://localhost/"
log "PrinterBridge : actif sur 127.0.0.1 et ${GATEWAY} (port 7420)"
