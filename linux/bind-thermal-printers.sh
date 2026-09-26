#!/usr/bin/env bash
# Lie chaque imprimante listee a un port RFCOMM sequentiel (rfcomm0, rfcomm1, ...) au demarrage.
set -euo pipefail

CONFIG_FILE="/etc/printerbridge/bluetooth-printers.conf"
[[ -f "${CONFIG_FILE}" ]] || exit 0

# `Requires=bluetooth.service` (bind-thermal-printers.service) garantit que le service est demarre,
# pas que l'adaptateur a fini son initialisation (meme lecon que la course docker compose up au boot,
# cf. install.sh) -- on attend qu'il soit vraiment pret (present ET allume) avant de tenter les
# liaisons.
DEADLINE=$((SECONDS + 30))
until bluetoothctl show 2>/dev/null | grep -q "Powered: yes"; do
    if (( SECONDS >= DEADLINE )); then
        echo "Adaptateur Bluetooth toujours indisponible apres 30s, abandon." >&2
        exit 1
    fi
    sleep 1
done

DEVICE_NUM=0
while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"
    read -r mac channel <<< "$line"
    [[ -z "${mac:-}" ]] && continue
    channel="${channel:-1}"

    rfcomm release "${DEVICE_NUM}" 2>/dev/null || true
    if rfcomm bind "${DEVICE_NUM}" "${mac}" "${channel}"; then
        echo "Liee rfcomm${DEVICE_NUM} -> ${mac} (canal ${channel})"
    else
        echo "Echec de liaison rfcomm${DEVICE_NUM} -> ${mac} (imprimante eteinte/hors de portee ?)" >&2
    fi
    DEVICE_NUM=$((DEVICE_NUM + 1))
done < "${CONFIG_FILE}"
