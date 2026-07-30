---
title: "Proposition de changement de sprint : Ignorer une imprimante détectée + rattrapage documentaire PrinterBridge"
date: 2026-07-28
status: approved
author: Manerial (via Claude Code)
---

> **Correction 2026-07-29** : ce document assignait FR-100 à la nouvelle exigence "ignorer une imprimante", en collision avec le FR-100 pré-existant des dates d'édition (Story 2.4). Renumérotée en **FR-104** dans le PRD et `epics.md` — voir `sprint-change-proposal-2026-07-29.md`. Les mentions ci-dessous sont laissées telles quelles pour l'historique, à lire comme FR-104.

# Proposition de changement de sprint : Ignorer une imprimante détectée + rattrapage documentaire PrinterBridge

## 1. Résumé du problème

Deux problèmes distincts, traités dans la même passe à la demande de l'utilisateur.

**Problème A — nouvelle exigence.** Depuis l'intégration de PrinterBridge (Stories 3.11/3.12), le dialog "Ajouter une imprimante" liste toutes les imprimantes détectées par PrinterBridge et non encore enregistrées. Certaines de ces imprimantes ne seront jamais enregistrées (imprimante d'un voisin détectée par erreur, imprimante temporaire) mais réapparaissent à chaque scan, encombrant la liste. Aucun mécanisme n'existe pour les exclure durablement.

**Problème B — dette de process découverte en préparant cette passe.** La proposition de changement du 2026-07-27 (origine des Stories 3.11/3.12, intégration PrinterBridge) a été **approuvée mais jamais réellement appliquée** aux artefacts de planification : `prd.md` (FR-076/077/079/074), `epics.md` (aucune mention de PrinterBridge ni des Stories 3.11/3.12) et `architecture.md` décrivent encore l'ancien mécanisme de connexion directe (port série / IP+port TCP), alors que le code livré (297/298 puis 298/298 tests backend, 430/430 tests frontend, statut `done` en `sprint-status.yaml`) implémente bien PrinterBridge. `.docker/docker-compose.yml` n'a pas non plus l'entrée `extra_hosts` prévue, et `GUIDE_INSTALLATION.md` ne mentionne pas l'installation de PrinterBridge — deux oublis qui ont un impact réel : `application-prod.properties` pointe vers `http://host.docker.internal:7420`, une adresse qui ne se résout **pas** sous Docker Engine natif (Linux/RPi4, cible documentée par l'architecture) sans l'entrée `extra_hosts` manquante.

De plus, deux petits ajustements ont été livrés en direct dans la session précédant cette passe (loader avant l'ouverture du dialog "Ajouter une imprimante" ; exclusion des imprimantes déjà enregistrées de la liste de découverte) sans passer par une story — également rattrapés ici.

## 2. Analyse d'impact

### Impact sur les epics

- **Epic 3 (Enregistrement des vendeurs & Dépôt)** — reste *in-progress*, réalisable comme prévu. Une nouvelle story **3.13** s'ajoute après 3.12 (toujours avant 6-1, priorité déjà actée le 2026-07-27). Aucune epic ne devient obsolète, aucune nouvelle epic n'est nécessaire.
- **Epics 4, 5, 6** — non impactés. Le changement reste entièrement contenu dans le module `print`.

### Conflits d'artefacts

| Artefact | Sections concernées | Nature du changement |
|---|---|---|
| PRD | FR-076, FR-077, FR-079, FR-074 (rattrapage) ; nouvelle FR-100 | Rattrapage + nouvelle exigence |
| Architecture | "Infrastructure d'Impression", nouvelle "Frontière PrinterBridge", `Printer.java`, tableau "Infrastructure & Déploiement", nouvelle entité `IgnoredPrinter` | Rattrapage + nouveau modèle de données |
| Epics/Stories | Stories 3.4/3.7/3.8/3.9 (bandeaux, rattrapage), nouvelles Stories 3.11/3.12 (rattrapage complet), nouvelle Story 3.13 | Rattrapage + nouvelle story |
| UX (`EXPERIENCE.md`) | "Page file d'impression" (bandeau), "Gestion des imprimantes (Admin)" (réécriture complète du composant : liste avec actions par ligne, section imprimantes ignorées) | Rattrapage + nouvelle interaction |
| Infrastructure | `.docker/docker-compose.yml` | Ajout `extra_hosts: host.docker.internal:host-gateway` sur le service `backend` |
| Guide d'installation | Section "Configuration initiale" | Nouvelle étape : installation/lancement de PrinterBridge |

### Impact technique

- Backend : nouvelle entité `IgnoredPrinter` + `IgnoredPrinterRepository` + migration Liquibase (table `ignored_printers`) ; `PrinterService.discover()` étend son filtrage existant (imprimantes déjà enregistrées) pour exclure aussi les imprimantes ignorées ; nouveaux endpoints pour ignorer/réactiver.
- Frontend : `printer-form.component` remplace son `mat-select` unique par une liste avec deux actions par ligne ("Enregistrer" / "Ignorer") ; nouvelle section "Imprimantes ignorées" sur `printer-list.component`.
- Infrastructure : `.docker/docker-compose.yml` (`extra_hosts` sur le service `backend`).
- Documentation : `GUIDE_INSTALLATION.md` (nouvelle étape PrinterBridge).

## 3. Approche recommandée

**Option retenue : Ajustement direct (Option 1)**, via une nouvelle story (3.13) dans Epic 3, combinée à un rattrapage documentaire des artefacts déjà approuvés le 2026-07-27 mais jamais appliqués.

**Justification** :
- La logique métier de la Story 3.13 est un ajout ciblé sur un périmètre déjà bien délimité (module `print`, dialog de création d'imprimante) — pas de remise en cause du MVP.
- Le rattrapage documentaire ne change aucune décision déjà prise : il aligne les artefacts sur ce qui a réellement été livré et approuvé.
- L'entrée `extra_hosts` manquante est un vrai risque de déploiement (pas seulement documentaire) sur les cibles Linux/RPi4 — corrigée dans la foulée.

**Effort estimé** : Faible à moyen (1 nouvelle entité + migration, refonte modérée du dialog frontend, rattrapage de 5 documents).
**Risque** : Faible — aucune remise en cause de code déjà livré et fonctionnel (3.11/3.12 fonctionnent correctement), uniquement de la documentation en retard + un ajout ciblé.

## 4. Propositions de changement détaillées

### PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**FR-076** — ANCIEN : *"...associée à un port série sélectionné depuis la liste des périphériques Bluetooth déjà appairés au niveau OS (`SerialPort.getCommPorts()`)... l'application consomme uniquement les ports série déjà disponibles."*
NOUVEAU : *"...associée à une imprimante détectée par le service PrinterBridge (composant natif séparé, installé sur le poste admin, qui possède l'accès matériel Bluetooth). PrinterBridge consomme les ports déjà disponibles et les expose à PluriBourse via une API locale."*

**FR-077** — ANCIEN : *"...adressée par IP ou hostname et port TCP (défaut : 9100). PDF généré côté serveur, envoyé directement à l'imprimante via TCP sans aperçu."*
NOUVEAU : *"...sélectionnée parmi les imprimantes déjà installées dans le spouleur d'impression du système d'exploitation, détectées et exposées par PrinterBridge. PDF généré côté serveur, transmis à PrinterBridge qui le soumet au spouleur OS."*

**FR-079** — la vérification de connectivité au démarrage se fait via un appel au statut PrinterBridge plutôt qu'un test direct de port/adresse.

**FR-074** — ajouter une sous-étape à la section 5 "Configuration initiale" : installation et lancement de PrinterBridge sur le poste admin, avant l'enregistrement des imprimantes.

**FR-100 (nouvelle)** : L'admin peut ignorer une imprimante détectée par PrinterBridge mais non enregistrée, afin qu'elle cesse d'apparaître dans la liste de découverte (`GET /admin/printers/discovered`). L'action est réversible : une section dédiée liste les imprimantes ignorées, avec une action permettant de les réactiver (elles réapparaissent alors dans la découverte au prochain scan). Une imprimante déjà enregistrée dans le registre ne peut pas être ignorée.

### Architecture (`architecture.md`)

**Section "Infrastructure d'Impression"** — les deux lignes du tableau sont réécrites pour décrire une délégation via WebSocket à PrinterBridge plutôt qu'un accès direct au port série/à la socket TCP.

**Nouvelle sous-section "Frontière PrinterBridge"** : PluriBourse ↔ PrinterBridge via HTTP/WebSocket (`host.docker.internal` / `extra_hosts: host-gateway` sous Docker Engine natif). PrinterBridge est un repository séparé (`github.com/Manerial/PrinterBridge`), pas un module du monorepo. Le backend ne stocke plus d'adresse physique, uniquement un identifiant opaque renvoyé par PrinterBridge. Endpoints consommés : `GET /printers`, `GET /printers/{id}/status`, `POST /printers/{id}/test-print`, `WS /printers/{id}/print`. Les imprimantes ignorées (nouvelle table `ignored_printers`) sont un concept propre à PluriBourse — PrinterBridge continue de les détecter à chaque scan, le filtrage se fait entièrement côté PluriBourse.

**`print/entity/Printer.java`** — ANCIEN : *"type: THERMAL | A4, port série ou IP/port"* → NOUVEAU : *"type: THERMAL | A4, printerBridgeId (identifiant opaque renvoyé par PrinterBridge), widthMm (THERMAL uniquement)"*.
**`print/entity/IgnoredPrinter.java`** (nouveau) : `printerBridgeId`, `ignoredAt` — imprimante détectée par PrinterBridge mais explicitement exclue de la découverte, jamais enregistrée dans `printers`.
**`print/repository/IgnoredPrinterRepository.java`** (nouveau).

**"Organisation du Référentiel"** — note ajoutée : PrinterBridge est un repository frère, hors de ce monorepo.

**Tableau "Infrastructure & Déploiement"** — nouvelle ligne : *"Passerelle hôte | `extra_hosts: - \"host.docker.internal:host-gateway\"` dans `docker-compose.yml` | Nécessaire sous Docker Engine natif (Linux/RPi4) pour joindre PrinterBridge — Docker Desktop le résout nativement"*.

### Epics & Stories (`epics.md`)

Stories 3.4, 3.7, 3.8, 3.9 : ajout d'un avertissement en tête — *"⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir sprint-change-proposal-2026-07-27.md). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge."* Aucune AC existante n'est supprimée ni réécrite.

**Rattrapage Story 3.11 : Intégration de PrinterBridge — connexion et statut**
- Découverte des imprimantes PrinterBridge (à l'exclusion des imprimantes déjà enregistrées), avec indicateur de chargement avant l'ouverture du dialog de création.
- Message d'avertissement si PrinterBridge est injoignable.
- Type dérivé automatiquement de l'imprimante sélectionnée, identifiant opaque `printerBridgeId`.
- Vérification de connectivité via le statut PrinterBridge, erreurs PrinterBridge injoignable distinguées d'une imprimante hors ligne.
- Bouton "Tester l'impression" par imprimante enregistrée.

**Rattrapage Story 3.12 : Intégration de PrinterBridge — envoi des jobs d'impression**
- `ThermalPrintService`/`DocumentPrintService` envoient le contenu via `WS /printers/{id}/print`.
- Erreurs PrinterBridge injoignable distinguées d'un échec d'impression signalé par PrinterBridge.
- Signature de `PrintQueueService.submit()` strictement inchangée.

**Nouvelle Story 3.13 : Ignorer une imprimante détectée (FR-100)**
- Le dialog "Ajouter une imprimante" présente chaque imprimante détectée comme une ligne avec deux actions : "Enregistrer" et "Ignorer" (remplace le menu déroulant unique de la Story 3.11).
- Une imprimante ignorée disparaît de la découverte, y compris lors des scans suivants.
- Section "Imprimantes ignorées" (repliée par défaut) sur `/admin/printers`, avec action "Réactiver" par ligne.
- Une imprimante déjà enregistrée ne peut pas être ignorée.
- Accès réservé à l'ADMIN (403 pour un bénévole).

### UX (`EXPERIENCE.md`)

**"Page file d'impression"** (ligne 152) — ANCIEN : *"Imprimante inaccessible au démarrage — vérifiez la connexion Bluetooth / réseau."* NOUVEAU : *"Imprimante inaccessible — vérifiez la connexion Bluetooth / réseau de l'imprimante elle-même (si PrinterBridge répond) ou le service PrinterBridge (si injoignable, voir bandeau dédié)."* + nouvel état **"Agent PrinterBridge injoignable"** (réutilise `NotificationInlineComponent` variant `warning`) sur `/admin/printers` et `/admin/print-queue`.

**"Gestion des imprimantes (Admin)"** (ligne 153) — réécrite : liste unique des imprimantes enregistrées (nom, type déduit, statut, largeur si thermique) avec bouton "Tester l'impression" par ligne. Bouton "Ajouter une imprimante" ouvre un dialog listant les imprimantes détectées par PrinterBridge, chacune en ligne avec deux actions ("Enregistrer" / "Ignorer"). Indicateur de chargement avant l'ouverture du dialog. Bandeau d'avertissement si PrinterBridge injoignable. Section "Imprimantes ignorées" (repliée par défaut) avec action "Réactiver" par ligne. Suppression inchangée.

### Infrastructure (`.docker/docker-compose.yml`)

Ajout sur le service `backend` :
```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

### Guide d'installation (`GUIDE_INSTALLATION.md`)

Section "Configuration initiale" — nouvelle étape : *"Installez et lancez PrinterBridge sur le poste qui pilotera les imprimantes (voir le repository dédié `github.com/Manerial/PrinterBridge`), puis rendez-vous dans **Imprimantes** (`/admin/printers`) pour enregistrer vos imprimantes détectées."*

## 5. Passation pour l'implémentation

**Classification du changement : Modéré.** Une nouvelle story insérée dans Epic 3, combinée à un rattrapage documentaire coordonné de plusieurs artefacts de planification déjà approuvés antérieurement. Pas de refonte stratégique du produit (MVP inchangé), donc pas d'escalade PM/Architecte nécessaire.

**Prochaines étapes** :
1. Appliquer les modifications ci-dessus à `prd.md`, `architecture.md`, `epics.md`, `EXPERIENCE.md`, `.docker/docker-compose.yml`, `GUIDE_INSTALLATION.md` (édition directe des fichiers) — **cette fois-ci, vérifier explicitement l'application avant de clore la story**, contrairement à la proposition du 2026-07-27.
2. Mettre à jour `sprint-status.yaml` : ajouter la Story 3.13 en `backlog` dans Epic 3, après 3.12.
3. Lancer `bmad-create-story` pour générer le fichier de story détaillé de 3.13, puis `bmad-dev-story` pour l'implémentation.

**Critères de succès** : un admin peut ignorer une imprimante détectée depuis le dialog de création, elle cesse de réapparaître dans la découverte, et peut être réactivée depuis une section dédiée ; les cinq documents de planification reflètent fidèlement l'état réel du code (PrinterBridge + ignorer une imprimante) ; un déploiement Docker Engine natif (Linux/RPi4) peut effectivement joindre PrinterBridge via `host.docker.internal`.
