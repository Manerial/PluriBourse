#!/usr/bin/env bash
# Assistant d'ajout d'une imprimante thermique Bluetooth : liste/scanne les appareils, laisse
# l'admin choisir une adresse MAC, passe la main a bluetoothctl en interactif pour l'appairage/PIN
# (pas de simulation fragile de ce prompt — trop capricieux d'un modele d'imprimante a l'autre,
# constate en pratique sur un Netum), puis enregistre la MAC dans bluetooth-printers.conf et
# redemarre le service de liaison RFCOMM.
#
# Usage : sudo add-printer.sh

set -euo pipefail

CONFIG_FILE="/etc/printerbridge/bluetooth-printers.conf"

log() {
    echo "==> $*"
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "Ce script doit etre lance avec sudo : sudo add-printer.sh" >&2
    exit 1
fi

if [[ ! -f "${CONFIG_FILE}" ]]; then
    echo "${CONFIG_FILE} n'existe pas — lance d'abord install.sh (sans --start) pour mettre en place le service de liaison Bluetooth." >&2
    exit 1
fi

log "Appareils Bluetooth deja connus :"
bluetoothctl devices || true

echo ""
read -rp "Scanner pour trouver une nouvelle imprimante non listee ci-dessus ? [o/N] " DO_SCAN
if [[ "${DO_SCAN,,}" == "o" ]]; then
    log "Scan en cours (15s)..."
    bluetoothctl --timeout 15 scan on || true
    log "Appareils apres scan :"
    bluetoothctl devices
fi

echo ""
read -rp "Adresse MAC de l'imprimante (format AA:BB:CC:DD:EE:FF) : " MAC
if [[ -z "${MAC}" ]]; then
    echo "Adresse MAC vide, abandon." >&2
    exit 1
fi

if grep -qi "^${MAC}\b" "${CONFIG_FILE}" 2>/dev/null; then
    log "${MAC} est deja dans ${CONFIG_FILE}, rien a ajouter."
else
    ALREADY_PAIRED=false
    if bluetoothctl info "${MAC}" 2>/dev/null | grep -q "Paired: yes"; then
        ALREADY_PAIRED=true
    fi

    if [[ "${ALREADY_PAIRED}" == "true" ]]; then
        log "${MAC} est deja appairee, pas besoin de repasser par bluetoothctl."
    else
        # Chaque `bluetoothctl <commande>` lance un process a part qui se termine aussitot -- un
        # agent enregistre dans l'un disparait avec lui, donc "power on"/"agent"/"default-agent"
        # doivent etre tapes DANS la meme session interactive que "pair", pas en pre-commandes
        # separees (constate en pratique : "No agent is registered" sinon).
        echo ""
        log "Ouverture de bluetoothctl en interactif — a l'interieur, tape dans l'ordre :"
        log "  power on"
        log "  agent KeyboardOnly"
        log "  default-agent"
        log "  pair ${MAC}"
        log "  (entre le code PIN de l'imprimante si demande — voir sa notice, souvent 0000 ou 1234)"
        log "  trust ${MAC}"
        log "  exit"
        echo ""
        bluetoothctl

        # bluetoothctl rend la main que l'appairage ait reussi ou non (PIN faux, sortie prematuree
        # de "exit"...) -- sans cette verification, une tentative ratee finissait quand meme
        # enregistree dans bluetooth-printers.conf (constate en pratique).
        if ! bluetoothctl info "${MAC}" 2>/dev/null | grep -q "Paired: yes"; then
            echo "${MAC} n'est pas appairee (l'appairage a du echouer ou etre interrompu) — rien n'est enregistre. Relance add-printer.sh pour reessayer." >&2
            exit 1
        fi
    fi

    read -rp "Canal RFCOMM (defaut 1, presque toujours le bon) : " CHANNEL
    CHANNEL="${CHANNEL:-1}"

    echo "${MAC} ${CHANNEL}" >> "${CONFIG_FILE}"
    log "${MAC} (canal ${CHANNEL}) ajoutee a ${CONFIG_FILE}."
fi

log "Redemarrage du service de liaison RFCOMM..."
systemctl restart bind-thermal-printers.service
systemctl status bind-thermal-printers.service --no-pager -l | grep -i "${MAC}" || true

log "Termine. PrinterBridge la verra automatiquement au prochain GET /printers, sans redemarrage necessaire."
