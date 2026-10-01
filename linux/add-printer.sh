#!/usr/bin/env bash
# Assistant d'ajout d'une imprimante : thermique Bluetooth, ou reseau/A4 via CUPS.
#
# Usage : sudo add-printer.sh

set -euo pipefail

BLUETOOTH_CONFIG_FILE="/etc/printerbridge/bluetooth-printers.conf"

log() {
    echo "==> $*"
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "Ce script doit etre lance avec sudo : sudo add-printer.sh" >&2
    exit 1
fi

add_bluetooth_printer() {
    if [[ ! -f "${BLUETOOTH_CONFIG_FILE}" ]]; then
        echo "${BLUETOOTH_CONFIG_FILE} n'existe pas — lance d'abord pluribourse-install.sh pour mettre en place le service de liaison Bluetooth." >&2
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
        bluetoothctl devices || true
    fi

    echo ""
    read -rp "Adresse MAC de l'imprimante (format AA:BB:CC:DD:EE:FF) : " MAC
    if [[ -z "${MAC}" ]]; then
        echo "Adresse MAC vide, abandon." >&2
        exit 1
    fi

    if grep -qi "^${MAC}\b" "${BLUETOOTH_CONFIG_FILE}" 2>/dev/null; then
        log "${MAC} est deja dans ${BLUETOOTH_CONFIG_FILE}, rien a ajouter."
    else
        ALREADY_PAIRED=false
        if bluetoothctl info "${MAC}" 2>/dev/null | grep -q "Paired: yes"; then
            ALREADY_PAIRED=true
        fi

        if [[ "${ALREADY_PAIRED}" == "true" ]]; then
            log "${MAC} est deja appairee, pas besoin de repasser par bluetoothctl."
        else
            # Chaque `bluetoothctl <commande>` lance un process a part qui se termine aussitot --
            # un agent enregistre dans l'un disparait avec lui, donc "power on"/"agent"/
            # "default-agent" doivent etre tapes DANS la meme session interactive que "pair", pas
            # en pre-commandes separees (constate en pratique : "No agent is registered" sinon).
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

            # bluetoothctl rend la main que l'appairage ait reussi ou non (PIN faux, sortie
            # prematuree de "exit"...) -- sans cette verification, une tentative ratee finissait
            # quand meme enregistree dans bluetooth-printers.conf (constate en pratique).
            if ! bluetoothctl info "${MAC}" 2>/dev/null | grep -q "Paired: yes"; then
                echo "${MAC} n'est pas appairee (l'appairage a du echouer ou etre interrompu) — rien n'est enregistre. Relance add-printer.sh pour reessayer." >&2
                exit 1
            fi
        fi

        read -rp "Canal RFCOMM (defaut 1, presque toujours le bon) : " CHANNEL
        CHANNEL="${CHANNEL:-1}"

        echo "${MAC} ${CHANNEL}" >> "${BLUETOOTH_CONFIG_FILE}"
        log "${MAC} (canal ${CHANNEL}) ajoutee a ${BLUETOOTH_CONFIG_FILE}."
    fi

    log "Redemarrage du service de liaison RFCOMM..."
    systemctl restart bind-thermal-printers.service
    systemctl status bind-thermal-printers.service --no-pager -l | grep -i "${MAC}" || true

    log "Termine. PrinterBridge la verra automatiquement au prochain GET /printers, sans redemarrage necessaire."
}

add_network_printer() {
    # lpadmin/lpinfo viennent du paquet cups (cups-client). cups-bsd (lpr/lpq) est un paquet
    # separe, absent par defaut -- mais javax.print (PrinterBridge) shelle en interne `lpr` pour
    # soumettre un job sur Linux (detail d'implementation de sun.print.PSPrinterJob, pas configurable).
    # Sans cups-bsd, impression impossible via PrinterBridge meme si `lp`/CUPS fonctionnent tres
    # bien par ailleurs (constate en pratique : "Cannot run program /usr/bin/lpr", cf. CLAUDE.md).
    if ! command -v lpadmin >/dev/null 2>&1 || ! command -v lpr >/dev/null 2>&1; then
        log "CUPS et/ou cups-bsd (lpr) manquant(s), installation..."
        apt-get update -qq
        apt-get install -y -qq cups cups-bsd
    fi

    echo ""
    read -rp "Autoriser l'administration CUPS a distance (interface web depuis un autre PC) ? [o/N] " DO_REMOTE_ADMIN
    if [[ "${DO_REMOTE_ADMIN,,}" == "o" ]]; then
        cupsctl --remote-admin --remote-any
        systemctl restart cups
        log "Interface web CUPS accessible sur https://<ip-du-pi>:631 (reseau local uniquement, ne jamais exposer sur internet)."
    fi

    log "Imprimantes reseau detectees :"
    lpinfo -v | grep "^network" || log "(aucune detection automatique — tu peux quand meme entrer l'IP a la main ci-dessous)"

    echo ""
    read -rp "Nom a donner a cette imprimante (sans espace) : " PRINTER_NAME
    if [[ -z "${PRINTER_NAME}" ]]; then
        echo "Nom vide, abandon." >&2
        exit 1
    fi
    read -rp "Adresse IP de l'imprimante : " PRINTER_IP
    if [[ -z "${PRINTER_IP}" ]]; then
        echo "IP vide, abandon." >&2
        exit 1
    fi

    # Le pilote "everywhere" (IPP Everywhere, sans PPD specifique) exige une connexion IPP -- il est
    # incompatible avec une URI socket:// (port JetDirect brut), contrairement a ce qu'on avait
    # d'abord code ici (constate en pratique : "IPP Everywhere driver requires an ipp connection").
    # Fonctionne pour la quasi-totalite des imprimantes recentes (AirPrint/IPP Everywhere) ; repli en
    # JetDirect brut + pilote generique pour les plus anciennes qui ne le supportent pas.
    log "Tentative via IPP Everywhere (pilote generique, imprimantes recentes)..."
    if lpadmin -p "${PRINTER_NAME}" -E -v "ipp://${PRINTER_IP}/ipp/print" -m everywhere 2>/dev/null; then
        log "${PRINTER_NAME} (${PRINTER_IP}) ajoutee a CUPS via IPP Everywhere."
    else
        log "IPP Everywhere a echoue (imprimante plus ancienne ?) -- tentative en JetDirect brut..."
        if lpadmin -p "${PRINTER_NAME}" -E -v "socket://${PRINTER_IP}:9100" -m drv:///sample.drv/generic.ppd; then
            log "${PRINTER_NAME} (${PRINTER_IP}) ajoutee a CUPS avec un pilote generique (JetDirect)."
        else
            echo "Impossible d'ajouter l'imprimante automatiquement. Utilise l'interface web CUPS (https://<ip-du-pi>:631 -> Administration -> Add Printer), qui detecte mieux le bon pilote pour ce modele." >&2
            exit 1
        fi
    fi
    log "Termine. PrinterBridge la verra automatiquement au prochain GET /printers, sans redemarrage necessaire."
}

echo "Quel type d'imprimante veux-tu ajouter ?"
echo "  1) Thermique Bluetooth"
echo "  2) Reseau/A4 (CUPS)"
read -rp "Choix [1/2] : " PRINTER_KIND

case "${PRINTER_KIND}" in
    1) add_bluetooth_printer ;;
    2) add_network_printer ;;
    *)
        echo "Choix invalide." >&2
        exit 1
        ;;
esac
