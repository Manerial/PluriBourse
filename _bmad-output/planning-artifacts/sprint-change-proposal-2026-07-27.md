---
title: "Proposition de changement de sprint : Intégration de PrinterBridge"
date: 2026-07-27
status: approved
author: Manerial (via Claude Code)
---

# Proposition de changement de sprint : Intégration de PrinterBridge

## 1. Résumé du problème

PluriBourse (backend Spring Boot, déployé en conteneur Docker) connecte aujourd'hui les imprimantes **directement** : port série RFCOMM Bluetooth (via `jSerialComm`) pour les imprimantes thermiques, socket TCP brute pour les imprimantes A4 réseau. Ce mécanisme est décrit dans le PRD (FR-076, FR-077), l'architecture (ARCH-009, section "Infrastructure d'Impression") et implémenté par les Stories 3.4, 3.5, 3.7, 3.8, 3.9 — toutes déjà **done**.

**Problème découvert après livraison** : le Bluetooth est un périphérique matériel de la machine hôte, invisible depuis un conteneur Docker par conception. Aucun mécanisme de passthrough fiable n'existe, en particulier sous Docker Desktop (Windows/Mac). Conséquence concrète : le flux d'impression thermique — cœur de la phase Dépôt (FR-028, génération automatique des étiquettes) — ne fonctionne pas de façon fiable dans le mode de déploiement documenté (Docker Compose, FR-070).

**Preuves** :
- Test réseau réel sur une imprimante A4 d'entreprise : échec de connexion, diagnostiqué comme un problème de segmentation réseau (hors périmètre PluriBourse), mais qui a révélé au passage l'absence totale de solution pour le cas Bluetooth.
- Analyse technique : aucun mécanisme sain de passthrough Bluetooth vers un conteneur, notamment sous Docker Desktop.
- Solution retenue et **déjà implémentée dans un repository séparé** : [PrinterBridge](https://github.com/Manerial/PrinterBridge) — un service natif installé sur le poste admin, qui possède seul l'accès matériel (Bluetooth + spouleur d'impression OS) et l'expose via une API HTTP/WebSocket locale (`GET /printers`, `GET /printers/{id}/status`, `POST /printers/{id}/test-print`, `WS /printers/{id}/print`).

## 2. Analyse d'impact

### Impact sur les epics

- **Epic 3 (Enregistrement des vendeurs & Dépôt)** — toujours *in-progress*. Ses stories 3.4, 3.7, 3.8, 3.9 (déjà done) décrivent un mécanisme de connexion obsolète. Pas de réécriture de leurs ACs d'origine (elles restent l'historique de ce qui a été livré) — annotées comme remplacées, avec deux nouvelles stories (3.11, 3.12) qui portent l'implémentation réelle.
- **Epic 6 (Catalogue articles)** — sa story `6-1` (déjà *ready-for-dev*) est **reséquencée après** les nouvelles stories 3.11/3.12 : un flux déjà en usage réel (impression) prime sur une nouvelle fonctionnalité pas encore commencée.
- **Epics 4 et 5** (POS, Post-vente/Rapports — backlog, pas commencés) — non impactés tant que `PrintQueueService.submit(printerId, job)` garde la même signature côté PluriBourse. Contrainte à respecter explicitement lors de l'implémentation.
- Aucune epic n'est invalidée, aucune nouvelle epic n'est nécessaire.

### Conflits d'artefacts

| Artefact | Sections concernées | Nature du changement |
|---|---|---|
| PRD | FR-076, FR-077, FR-079, FR-074 | Reformulation — le mécanisme de connexion passe par PrinterBridge |
| Architecture | "Infrastructure d'Impression", nouvelle "Frontière PrinterBridge", `Printer.java`, "Organisation du Référentiel", tableau "Infrastructure & Déploiement" | Nouvelle frontière d'intégration + modèle de données |
| Epics/Stories | Stories 3.4/3.7/3.8/3.9 (annotées), nouvelles Stories 3.11/3.12 | Ajout de stories, pas de réécriture de l'historique |
| UX (`EXPERIENCE.md`) | Composant "Gestion des imprimantes", nouvel état "Agent PrinterBridge injoignable", bandeau file d'impression | 2 composants modifiés + 1 nouvel état (réutilise `banner`/`toast` existants, aucun nouveau composant visuel) |
| Infrastructure | `docker-compose.yml` | Ajout `extra_hosts: host.docker.internal:host-gateway` |
| Guide d'installation | Section 5 (Configuration initiale) | Nouvelle sous-étape : installation/lancement de PrinterBridge |

### Impact technique

- Backend : `Printer` (entité + migration Liquibase), `PrinterConnectivityChecker` (remplacé par client HTTP), `ThermalPrintService`/`DocumentPrintService` (remplacés par client WebSocket), `PrinterController`/`PrinterService`.
- Frontend : `printer-form.component.ts/html` (sélection depuis découverte au lieu de saisie manuelle), ajout bouton "Tester l'impression".
- Infrastructure : `docker-compose.yml`.
- **Compromis assumé** (validé explicitement) : l'installation nécessite désormais une application native supplémentaire (PrinterBridge) en plus de `docker compose up -d` — accroc à l'objectif "zéro installation locale" du PRD (FR-072, G3), documenté plutôt que dissimulé.

## 3. Approche recommandée

**Option retenue : Ajustement direct (Option 1)**, via de nouvelles stories dans Epic 3 plutôt qu'une nouvelle epic ou un rollback.

**Justification** :
- La logique métier déjà livrée (files par imprimante, vue diagnostic, sélection par le bénévole, registre admin) reste correcte — seul le mécanisme de connexion bas niveau doit changer. Un rollback (Option 2) jetterait du travail fonctionnel pour rien.
- Aucune exigence F9 ne devient inatteignable — pas de révision du MVP nécessaire (Option 3 non applicable).
- Le changement reste circonscrit à la couche transport si `PrintQueueService.submit()` garde sa signature — impact contenu sur le reste de l'application.

**Effort estimé** : Moyen (5-6 fichiers backend, 1 composant frontend, 1 fichier infra, mises à jour PRD/architecture/guide).
**Risque** : Moyen — touche du code déjà livré et testé, mais périmètre délimité.

## 4. Propositions de changement détaillées

### PRD (`prd.md`)

**FR-076** — ANCIEN : *"...associée à un port série sélectionné depuis la liste des périphériques Bluetooth déjà appairés au niveau OS (`SerialPort.getCommPorts()`)... l'application consomme uniquement les ports série déjà disponibles."*
NOUVEAU : *"...associée à une imprimante détectée par le service PrinterBridge (composant natif séparé, installé sur le poste admin, qui possède l'accès matériel Bluetooth). PrinterBridge consomme les ports déjà disponibles et les expose à PluriBourse via une API locale."*

**FR-077** — ANCIEN : *"...adressée par IP ou hostname et port TCP (défaut : 9100). PDF généré côté serveur, envoyé directement à l'imprimante via TCP sans aperçu."*
NOUVEAU : *"...sélectionnée parmi les imprimantes déjà installées dans le spouleur d'impression du système d'exploitation, détectées et exposées par PrinterBridge. PDF généré côté serveur, transmis à PrinterBridge qui le soumet au spouleur OS."*

**FR-079** — la vérification de connectivité au démarrage se fait via un appel au statut PrinterBridge plutôt qu'un test direct de port/adresse.

**FR-074** — ajouter une sous-étape à la section 5 "Configuration initiale" : installation et lancement de PrinterBridge sur le poste admin, avant l'enregistrement des imprimantes.

### Architecture (`architecture.md`)

**Section "Infrastructure d'Impression"** — les deux lignes du tableau (thermique/A4) sont réécrites pour décrire une délégation via WebSocket à PrinterBridge plutôt qu'un accès direct au port série/à la socket TCP.

**Nouvelle sous-section "Frontière PrinterBridge"** : PluriBourse ↔ PrinterBridge via HTTP/WebSocket (`host.docker.internal` / `extra_hosts: host-gateway` sous Linux natif). PrinterBridge est un repository séparé (`github.com/Manerial/PrinterBridge`), pas un module du monorepo. Le backend ne stocke plus d'adresse physique, uniquement un identifiant stable renvoyé par PrinterBridge. Endpoints consommés : `GET /printers`, `GET /printers/{id}/status`, `POST /printers/{id}/test-print`, `WS /printers/{id}/print`.

**`print/entity/Printer.java`** — ANCIEN : *"type: THERMAL | A4, port série ou IP/port"* → NOUVEAU : *"type: THERMAL | A4, printerBridgeId (identifiant opaque renvoyé par PrinterBridge), widthMm (THERMAL uniquement)"*.

**"Organisation du Référentiel"** — note ajoutée : PrinterBridge est un repository frère, hors de ce monorepo.

**Tableau "Infrastructure & Déploiement"** — nouvelle ligne : `docker-compose.yml` doit inclure `extra_hosts: - "host.docker.internal:host-gateway"`.

### Epics & Stories (`epics.md`)

Stories 3.4, 3.5, 3.7, 3.8, 3.9 : ajout d'un avertissement en tête — *"⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir sprint-change-proposal-2026-07-27.md). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge."* Aucune AC existante n'est supprimée ni réécrite.

**Nouvelle Story 3.11 : Intégration de PrinterBridge — connexion et statut**
- Le backend appelle `GET /printers` (PrinterBridge) pour peupler la découverte, au lieu de `SerialPort.getCommPorts()`/saisie IP.
- Le backend appelle `GET /printers/{id}/status` pour la vérification de connectivité (remplace le test direct au démarrage, FR-079).
- `POST /printers/{id}/test-print` exposé côté PluriBourse ; bouton "Tester l'impression" par imprimante dans `/admin/printers`.
- L'admin peut toujours supprimer une imprimante du registre (continuité de l'AC existante, Story 3.8) — sert aussi à nettoyer une entrée devenue obsolète si l'identifiant PrinterBridge change (ex. réattribution d'un port COM).
- `Printer` (entité) stocke un identifiant opaque PrinterBridge au lieu de host/port/serialPort.

**Nouvelle Story 3.12 : Intégration de PrinterBridge — envoi des jobs d'impression**
- `ThermalPrintService`/`DocumentPrintService` envoient le contenu via `WS /printers/{id}/print` au lieu d'écrire directement sur `Socket`/`SerialPort`.

### UX (`EXPERIENCE.md`)

**Composant "Gestion des imprimantes (Admin)"** — ANCIEN : formulaires séparés avec saisie manuelle (port série en dropdown serveur / IP+port TCP). NOUVEAU : liste unique des imprimantes détectées par PrinterBridge (nom, type déduit, statut). Bouton "Enregistrer" par ligne détectée (sélecteur de largeur pour le thermique avant confirmation). Bouton "Tester l'impression" par imprimante enregistrée (`POST /printers/{id}/test-print`, feedback spinner + toast). Suppression inchangée.

**Nouvel état "Agent PrinterBridge injoignable"** (State Patterns, réutilise le composant `banner` variante `warning`) : si le backend ne parvient pas à joindre PrinterBridge, bandeau en tête de `/admin/printers` et `/admin/print-queue` : *"Le service PrinterBridge ne répond pas sur ce poste. Vérifiez qu'il est lancé."* — distinct du toast "imprimante hors ligne" (qui suppose PrinterBridge joignable mais l'imprimante non).

**Bandeau page file d'impression** — ANCIEN : *"Imprimante inaccessible au démarrage — vérifiez la connexion Bluetooth / réseau."* NOUVEAU : *"Imprimante inaccessible — vérifiez la connexion Bluetooth / réseau de l'imprimante elle-même (si PrinterBridge répond) ou le service PrinterBridge (si injoignable, voir bandeau dédié)."*

## 5. Passation pour l'implémentation

**Classification du changement : Modéré.** Réorganisation du backlog (2 nouvelles stories insérées avant 6-1) + mises à jour coordonnées de plusieurs artefacts de planification. Pas de refonte stratégique du produit (MVP inchangé), donc pas d'escalade PM/Architecte nécessaire.

**Prochaines étapes** :
1. Appliquer les modifications ci-dessus à `prd.md`, `architecture.md`, `epics.md`, `EXPERIENCE.md` (édition directe des fichiers).
2. Mettre à jour `sprint-status.yaml` : ajouter les Stories 3.11 et 3.12 en `backlog` dans Epic 3, positionnées avant `6-1`.
3. Lancer `bmad-create-story` pour générer le fichier de story détaillé de 3.11, puis 3.12, suivis de `bmad-dev-story` pour l'implémentation.
4. Mettre à jour `docker-compose.yml` (`extra_hosts`) et `GUIDE_INSTALLATION.md` (nouvelle sous-étape) au moment de l'implémentation de 3.11.

**Critères de succès** : un admin peut enregistrer une imprimante détectée par PrinterBridge (thermique ou A4) sans jamais saisir de port série/IP dans PluriBourse ; un job d'impression (étiquette ou PDF) déclenché depuis PluriBourse aboutit physiquement via PrinterBridge ; l'indisponibilité de PrinterBridge est distinguée clairement de l'indisponibilité d'une imprimante précise dans l'interface admin.
