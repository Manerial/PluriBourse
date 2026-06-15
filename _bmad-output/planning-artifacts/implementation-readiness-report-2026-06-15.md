---
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
status: complete
documentsInventoried:
  prd: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md
  prdAddendum: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md
  architecture: _bmad-output/planning-artifacts/architecture.md
  epics: _bmad-output/planning-artifacts/epics.md
  uxDesign: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md
  uxExperience: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-15
**Project:** PluriBourse

---

## PRD Analysis

### Functional Requirements (96 total)

**F1 — Internationalisation (7 FRs)**
- FR-001: UI disponible en EN et FR
- FR-002: Langue détectée depuis le navigateur à la première connexion, stockée en préférence de compte
- FR-003: Chaque utilisateur peut modifier sa préférence de langue dans les paramètres du compte
- FR-004: Tout le texte de l'interface externalisé — aucun texte codé en dur
- FR-005: Langue des documents imprimés configurée par édition
- FR-006: Chaque édition a sa propre langue de documents, initialisée depuis le paramètre instance
- FR-007: Langue de documents d'une édition modifiable à tout moment par l'admin ; paramètre instance s'applique uniquement aux nouvelles éditions

**F2 — Gestion des Éditions & Cycle de Vie (13 FRs)**
- FR-008: Admin crée une édition avec nom libre
- FR-009: Plusieurs éditions par an supportées
- FR-010: Une seule édition active à la fois (Préparation/Dépôt/Vente/Post-vente = active ; Clôturée = inactive)
- FR-011: Toute transition de phase (avant ou arrière) nécessite confirmation explicite via dialog
- FR-012: Phase active affichée à tous les utilisateurs connectés
- FR-013: Admin déclenche clôture ; PDF générés EN+FR ; lecture seule ; articles restent jusqu'à action Archiver
- FR-014: Édition ayant dépassé Préparation ne peut pas être supprimée
- FR-015: Données de chaque édition strictement cloisonnées
- FR-016: Taux de commission par édition (défaut 20 %), modifiable en Préparation uniquement ; gelé dès Dépôt
- FR-080: À la création, option copier catégories d'une édition clôturée ou configurer from scratch
- FR-082: Retour arrière phase par phase ; données toujours préservées ; articles vendeurs déjà soldés non remis en vente ; retour depuis Clôturé disponible seulement avant action Archiver
- FR-088: Action "Archiver l'Édition" : copie chaque article (nom, catégorie, statut) dans table d'archivage (lots individuellement) ; supprime articles et profils vendeurs ; désactive retour arrière ; confirmation obligatoire
- FR-096: À la clôture, vendeurs non soldés automatiquement marqués Non réclamé (atomique) ; dialog affiche X vendeurs + montant total si applicable

**F3 — Gestion des Vendeurs & Articles (20 FRs)**
- FR-017: Admin configure liste catégories par édition
- FR-018: Admin configure mapping catégorie-table ; au moins une table par catégorie ; figé à Dépôt ; relation many-to-many
- FR-019: Profils vendeurs propres à chaque édition ; champs obligatoires : nom, prénom, email, téléphone
- FR-020: Bénévole recherche vendeur par nom ou email ; création si non trouvé
- FR-021: Admin peut supprimer vendeur en phase Dépôt (RGPD) ; confirmation obligatoire ; supprime profil + articles
- FR-022: Pour chaque article : nom/description, prix, catégorie, indicateur complet/incomplet, commentaire optionnel
- FR-023: Table assignée automatiquement (même table si catégorie déjà présente pour le vendeur, sinon table la moins chargée)
- FR-024: Correction/suppression article uniquement en phase Dépôt
- FR-025: Indicateur complet/incomplet et commentaire modifiables dans toutes les phases
- FR-089: Commission s'applique normalement aux articles incomplets
- FR-043: Bénévole crée un lot : nom, prix global, plusieurs articles
- FR-044: Chaque article du lot a son propre nom et reçoit sa propre étiquette
- FR-045: Étiquette lot : "Prix du lot : X€" + "Lot indivisible : X/N" à la place du prix individuel
- FR-026: Code 128 généré côté serveur par article (8 chiffres : 4 vendeur + 4 article)
- FR-027: Format étiquette article (centré, ordre précis des champs, sans nom vendeur RGPD)
- FR-028: Impression étiquettes déclenchée automatiquement à la validation du dépôt
- FR-029: Travaux d'impression mis en file d'attente côté serveur, exécutés séquentiellement
- FR-030: Format rouleau : [séparateur vendeur] → [étiquette] → [séparateur article] → [étiquette] → …
- FR-031: Bordereau de dépôt imprimable par vendeur (articles, prix, reversement net attendu)
- FR-032: Largeur ticket thermique (57mm/80mm) configurable par imprimante (pas global)

**F4 — Point de Vente (16 FRs)**
- FR-033: Interface caisse avec scanner USB HID
- FR-034: Gestion AZERTY/QWERTY transparente par key code mapping
- FR-035: Article scanné ajouté au panier avec nom et prix
- FR-036: Article déjà vendu → erreur explicite ; non ajouté au panier
- FR-037: Article incomplet → avertissement informatif ; peut être vendu
- FR-038: Caissier peut retirer articles individuels du panier avant validation
- FR-039: Validation marque articles comme vendus ; pas de retour/échange
- FR-040: Facture acheteur imprimable à la demande après validation
- FR-041: Facture : articles, prix unitaires, total, nom association, nom édition, date ; lot sur une ligne
- FR-042: Minimum 3 postes simultanés sans conflits
- FR-046: Scan article de lot → nom du lot en rouge + compteur X/N
- FR-047: Lot incomplet à validation → avertissement non bloquant
- FR-048: Lot complet vendu au prix global ; commission sur prix global
- FR-081: Caissier peut retirer lot entier du panier
- FR-090: Changement de phase avec panier actif → panier annulé + message explicite
- FR-093: Sélection moyen de paiement obligatoire (espèces/chèque/carte) ; espèces : champ "somme remise" optionnel → calcul monnaie

**F5 — Post-Vente & Reversements (7 FRs)**
- FR-049: Bilan de vente imprimable par vendeur en Post-vente
- FR-050: Contenu bilan : articles vendus (nom, prix), invendus (nom, catégorie, table), total brut, commission, net ; lot sur une ligne
- FR-051: Solde vendeur : bénévole saisit montant espèces ; avertissement si < net ; bloqué si > net ; statut → Soldé
- FR-052: Bouton "Non réclamé" : montant net intégral → recettes association
- FR-053: Vendeurs non soldés filtrables dans la liste
- FR-095: Page de solde = point d'entrée F5 ; tous vendeurs de l'édition ; filtre statut ; actions par ligne ; admin voit téléphone + email ; composant Angular unique
- FR-097: Admin peut déclencher impression groupée bilans depuis /admin/settlement ; périmètre résolu serveur depuis filtre actif ; feedback UX-DR19

**F6 — Rapports (8 FRs)**
- FR-054: Bilan journalier (admin uniquement, phase Vente) : articles vendus/invendus jour, CA journalier, commission
- FR-055: Bilan d'édition généré à la clôture : total vendus/invendus, CA brut total, commission totale
- FR-057: Tous rapports en PDF
- FR-058: Rapports accessibles admin uniquement
- FR-059: Éditions clôturées : métriques agrégées en lecture seule ; détails disponibles jusqu'à action Archiver
- FR-091: Export CSV catalogue articles (Post-vente + Clôturée ; téléchargement direct)
- FR-092: Export CSV reversements vendeurs (Post-vente + Clôturée ; téléchargement direct)
- FR-094: Bilan journalier et bilan d'édition incluent ventilation par moyen de paiement

**F7 — Comptes Utilisateurs & Contrôle d'Accès (8 FRs)**
- FR-060: Admin crée/modifie/désactive comptes bénévoles ; peut réinitialiser MDP
- FR-061: Un seul compte admin par instance
- FR-062: Premier lancement : identifiants Admin/Admin ; changement MDP forcé à la première connexion
- FR-063: MDP admin perdu : commande serveur génère MDP temporaire ; changement forcé à la connexion suivante
- FR-064: Rôles Admin et Bénévole strictement séparés ; admin crée compte bénévole pour opérer en caisse
- FR-065: Interface bénévole s'adapte à la phase active
- FR-066: Sessions n'expirent pas automatiquement
- FR-067: Compte stocke préférence de langue (EN/FR)

**F8 — Infrastructure & Déploiement (7 FRs)**
- FR-068: Serveur fonctionne sous Linux, macOS et Windows sans modification du code
- FR-069: Spec minimale : Raspberry Pi 4 (2 Go RAM) ou machine 64 bits équivalente
- FR-070: Déploiement via Docker Compose (Spring Boot + MariaDB) ; volumes persistants
- FR-071: Mise à jour : deux commandes ; données préservées
- FR-072: Accès client via navigateur ; aucune installation locale sur les postes
- FR-073: Page paramètres admin : nom association, taux commission défaut, langue documents défaut
- FR-074: Guide d'installation livrable en 7 sections obligatoires ; public non technique

**F9 — Infrastructure d'Impression (6 FRs)**
- FR-075: Toute impression routée via serveur central ; aucune imprimante sur postes clients
- FR-076: Imprimantes thermiques Bluetooth : admin enregistre ; port série sélectionné depuis périphériques appairés OS ; largeur 57/80mm par imprimante ; file indépendante par imprimante ; appairage Bluetooth au niveau OS avant événement
- FR-077: Imprimantes A4 réseau : admin enregistre ; IP/hostname + port TCP (défaut 9100) ; PDF envoyé directement via TCP ; file indépendante par imprimante
- FR-078: Utilisateur déclenche impression depuis UI ; traité par serveur ; aucune action sur poste client
- FR-079: Erreur impression → notification explicite ; file concernée suspendue ; autres non affectées ; relance ou ignore par job ; vue diagnostic admin ; vérification au démarrage
- FR-098: À la connexion bénévole : sélection imprimante thermique + A4 parmi disponibles ; active pour toute la session ; non persistée entre sessions

**F10 — Catalogue Articles (4 FRs)**
- FR-083: Catalogue filtrable et triable accessible admin + bénévoles pendant toutes les phases
- FR-084: Filtres : nom/description, code-barres, catégorie, table, statut vendu/invendu, complet/incomplet, nom du vendeur
- FR-085: Tri par n'importe quelle colonne visible
- FR-086: Catalogue affiche articles édition active uniquement ; données indisponibles après action Archiver

### Non-Functional Requirements (7 NFRs)

- NFR-001: Performance — Raspberry Pi 4 (2 Go RAM) ; ~100 vendeurs, ~1700 articles, 3 postes ; POS < 500ms ; pages < 1s
- NFR-002: Concurrence — Pas de conflits de données multi-postes ; prévention vente simultanée même article
- NFR-003: Exactitude financière — Calculs reversements exacts au centime
- NFR-004: Compatibilité navigateur — Chrome, Firefox, Edge, Safari sur tout OS
- NFR-005: Compatibilité scanner — USB HID sans configuration, AZERTY/QWERTY transparent
- NFR-006: Fiabilité — Pas de perte de données sur fermeture inattendue navigateur ou défaillance poste
- NFR-007: RGPD — Données personnelles vendeur supprimables sur demande ; données archivées non réidentifiables

### PRD Completeness Assessment

Le PRD est complet et bien structuré. 96 FRs couvrant 10 groupes fonctionnels, 7 NFRs. Observations :
- L'addendum est correctement intégré (FR-094, FR-095, FR-091/092 formalisés)
- FR-056 est absent du PRD (gap de numérotation entre FR-055 et FR-057) — probablement une exigence supprimée lors de révisions ; aucune référence dans les epics non plus
- Les FRs d'impression (F9) sont précis sur les contraintes techniques

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Groupe | Couvert dans | Statut |
|---|---|---|---|
| FR-001 | F1 | Epic 1 | ✓ |
| FR-002 | F1 | Epic 1 | ✓ |
| FR-003 | F1 | Epic 1 | ✓ |
| FR-004 | F1 | Epic 1 | ✓ |
| FR-005 | F1 | Epic 1 + 2 | ✓ |
| FR-006 | F1 | Epic 1 + 2 | ✓ |
| FR-007 | F1 | Epic 1 + 2 | ✓ |
| FR-008 | F2 | Epic 2 | ✓ |
| FR-009 | F2 | Epic 2 | ✓ |
| FR-010 | F2 | Epic 2 | ✓ |
| FR-011 | F2 | Epic 2 | ✓ |
| FR-012 | F2 | Epic 2 | ✓ |
| FR-013 | F2 | Epic 2 | ✓ |
| FR-014 | F2 | Epic 2 | ✓ |
| FR-015 | F2 | Epic 2 | ✓ |
| FR-016 | F2 | Epic 2 | ✓ |
| FR-017 | F3 | Epic 2 (Story 2.3) | ✓ |
| FR-018 | F3 | Epic 2 (Story 2.3) | ✓ |
| FR-019 | F3 | Epic 3 | ✓ |
| FR-020 | F3 | Epic 3 | ✓ |
| FR-021 | F3 | Epic 3 | ✓ |
| FR-022 | F3 | Epic 3 | ✓ |
| FR-023 | F3 | Epic 3 | ✓ |
| FR-024 | F3 | Epic 3 | ✓ |
| FR-025 | F3 | Epic 3 | ✓ |
| FR-026 | F3 | Epic 3 | ✓ |
| FR-027 | F3 | Epic 3 | ✓ |
| FR-028 | F3 | Epic 3 | ✓ |
| FR-029 | F3 | Epic 3 | ✓ |
| FR-030 | F3 | Epic 3 | ✓ |
| FR-031 | F3 | Epic 3 | ✓ |
| FR-032 | F3 | Epic 3 | ✓ |
| FR-033 | F4 | Epic 4 | ✓ |
| FR-034 | F4 | Epic 4 | ✓ |
| FR-035 | F4 | Epic 4 | ✓ |
| FR-036 | F4 | Epic 4 | ✓ |
| FR-037 | F4 | Epic 4 | ✓ |
| FR-038 | F4 | Epic 4 | ✓ |
| FR-039 | F4 | Epic 4 | ✓ |
| FR-040 | F4 | Epic 4 | ✓ |
| FR-041 | F4 | Epic 4 | ✓ |
| FR-042 | F4 | Epic 4 | ✓ |
| FR-043 | F3 | Epic 3 | ✓ |
| FR-044 | F3 | Epic 3 | ✓ |
| FR-045 | F3 | Epic 3 | ✓ |
| FR-046 | F4 | Epic 4 | ✓ |
| FR-047 | F4 | Epic 4 | ✓ |
| FR-048 | F4 | Epic 4 | ✓ |
| FR-049 | F5 | Epic 5 | ✓ |
| FR-050 | F5 | Epic 5 | ✓ |
| FR-051 | F5 | Epic 5 | ✓ |
| FR-052 | F5 | Epic 5 | ✓ |
| FR-053 | F5 | Epic 5 | ✓ |
| FR-054 | F6 | Epic 5 | ✓ |
| FR-055 | F6 | Epic 5 | ✓ |
| FR-056 | — | **ABSENT DU PRD** | ⚠ gap numérotation |
| FR-057 | F6 | Epic 5 | ✓ |
| FR-058 | F6 | Epic 5 | ✓ |
| FR-059 | F6 | Epic 5 | ✓ |
| FR-060 | F7 | Epic 1 | ✓ |
| FR-061 | F7 | Epic 1 | ✓ |
| FR-062 | F7 | Epic 1 | ✓ |
| FR-063 | F7 | Epic 1 | ✓ |
| FR-064 | F7 | Epic 1 | ✓ |
| FR-065 | F7 | Epic 1 | ✓ |
| FR-066 | F7 | Epic 1 | ✓ |
| FR-067 | F7 | Epic 1 | ✓ |
| FR-068 | F8 | Epic 1 | ✓ |
| FR-069 | F8 | Epic 1 | ✓ |
| FR-070 | F8 | Epic 1 | ✓ |
| FR-071 | F8 | Epic 1 | ✓ |
| FR-072 | F8 | Epic 1 | ✓ |
| FR-073 | F8 | Epic 1 | ✓ |
| FR-074 | F8 | Epic 1 | ✓ |
| FR-075 | F9 | Epic 3 | ✓ |
| FR-076 | F9 | Epic 3 | ✓ |
| FR-077 | F9 | Epic 3 | ✓ |
| FR-078 | F9 | Epic 3 | ✓ |
| FR-079 | F9 | Epic 3 | ✓ |
| FR-080 | F2 | Epic 2 | ✓ |
| FR-081 | F4 | Epic 4 | ✓ |
| FR-082 | F2 | Epic 2 | ✓ |
| FR-083 | F10 | Epic 6 | ✓ |
| FR-084 | F10 | Epic 6 | ✓ |
| FR-085 | F10 | Epic 6 | ✓ |
| FR-086 | F10 | Epic 6 | ✓ |
| FR-088 | F2 | Epic 2 | ✓ |
| FR-089 | F3 | Epic 3 | ✓ |
| FR-090 | F4 | Epic 2 (Story 2.6, serveur) + Epic 4 (Story 4.6, client) | ✓ |
| FR-091 | F6 | Epic 5 | ✓ |
| FR-092 | F6 | Epic 5 | ✓ |
| FR-093 | F4 | Epic 4 | ✓ |
| FR-094 | F6 | Epic 5 | ✓ |
| FR-095 | F5 | Epic 5 | ✓ |
| FR-096 | F2 | Epic 2 | ✓ |
| FR-097 | F5 | Epic 5 | ✓ |
| FR-098 | F9 | Epic 3 | ✓ |

### NFR Coverage

| NFR | Adressé dans |
|---|---|
| NFR-001 Performance | Story 1.1 (Docker/RPi4), Story 4.4 (concurrence) ; non mappé explicitement dans le tableau de couverture |
| NFR-002 Concurrence | ARCH-003 + ARCH-004 + Story 4.4 (Testcontainers) |
| NFR-003 Précision financière | Story 1.5, Story 3.2, Story 5.1, Story 5.2 — BigDecimal mentionné |
| NFR-004 Compatibilité navigateur | Story 1.7 |
| NFR-005 Compatibilité scanner | Story 4.1 |
| NFR-006 Fiabilité | Story 2.4 (SSE reconnect), Story 1.2 (Spring Session) |
| NFR-007 RGPD | Story 3.1 (no PII in logs), Story 3.1 (deletion), Story 2.5 (archivage anonymisé) |

### Missing Requirements

**Aucun FR manquant.** Les 96 FRs du PRD sont tous couverts dans les epics.

### Coverage Statistics

- Total PRD FRs : 96
- FRs couverts dans les epics : 96
- Couverture : **100 %**
- Gap de numérotation : FR-056 absent du PRD et des epics (cohérent)
- NFRs : 7/7 adressés implicitement dans les stories et contraintes architecturales

### Observations architecturales notables

1. **FR-017, FR-018** (catégories/tables) — classées F3 dans le PRD mais couvertes dans Epic 2 (Story 2.3). Décision architecturale correcte : la configuration des catégories fait partie du cycle de vie de l'édition (phase Préparation).
2. **FR-090** (annulation panier lors changement de phase) — correctement décomposé sur deux stories : Story 2.6 (serveur, Epic 2) et Story 4.6 (client Angular, Epic 4). Séparation serveur/client propre.
3. **FR-075 à FR-079 + FR-098** — classées F9 dans le PRD mais couvertes dans Epic 3. Décision correcte : l'infrastructure d'impression est prérequise au dépôt (Epic 3) et s'appuie sur l'enregistrement des imprimantes.
4. **NFRs** — aucun tableau de couverture NFR explicite dans les epics, mais chaque NFR est tracé vers des stories/contraintes architecturales spécifiques. Acceptable pour une implémentation, mais une note explicite par NFR dans chaque epic pertinent renforcerait la traçabilité.

---

## UX Alignment Assessment

### UX Document Status

**Présents et finaux.** Deux documents :
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md` (tokens visuels, composants)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` (comportements, flux, états)

Les deux documents ont subi deux passes de validation (`/bmad-ux validate`) ; tous les findings critical + high ont été corrigés.

### UX ↔ PRD Alignment

Couverture validée lors du run 2 de validation UX : **76 % explicite, 91 % avec implicites.** Tous les FRs critiques sont couverts. Les epics (Stories 1.1 à 6.1) référencent explicitement les UX-DRx correspondants dans leur en-tête.

Référence complète : `validation-report.md` (couverture PRD par groupe fonctionnel).

### UX ↔ Architecture Alignment

| Exigence UX | Architecture | Statut |
|---|---|---|
| UX-DR1 Angular Material 3 + design tokens | `@angular/material` (MIT) + SCSS + `styles.scss` | ✓ |
| UX-DR2 DM Sans Google Fonts | SCSS (import Google Fonts) — non mentionné explicitement dans l'architecture mais trivial | ✓ implicite |
| UX-DR3 AppLayoutComponent (barre 56px + sidebar 200px) | `shared/nav.component.ts` + structure Angular standalone | ✓ |
| UX-DR4 Phase chip avec mise à jour SSE 150ms | `SseEmitterRegistry` + `phase.service.ts` + `Signal<Phase>` | ✓ |
| UX-DR10 Scanner + AZERTY/QWERTY + auto-focus 500ms | `scanner.component.ts` + key code mapping (FR-034) | ✓ |
| UX-DR11 MatPaginator 50/page + tri par colonne | JPageFlow (`FilterService.filterData()`) + Angular Material | ✓ |
| UX-DR14 Panier POS avec regroupement lot + basket-cancelled | `basket.component.ts` + `lot-warning.component.ts` + SSE `basket-cancelled` | ✓ |
| UX-DR19 Feedback bouton impression (spinner → toast) | `print.service.ts` + `PrintQueueService` + toast (UX-DR8) | ✓ |
| UX-DR20 Accessibilité WCAG 2.2 AA | Angular CDK Testing Harnesses ; focus ring via Angular Material 3 (M3 par défaut) | ✓ implicite |
| UX-DR21 Annulation panier sur changement de phase | SSE event `basket-cancelled` + `PhaseService` + Story 2.6 + Story 4.6 | ✓ |

### Alignment Issues

**Minor — N queues vs 2 queues dans la prose de l'architecture**

La section "Infrastructure d'Impression" de `architecture.md` décrit "deux instances `LinkedBlockingQueue` (thermique / document)" mais ARCH-009 et FR-076/FR-077 spécifient N files dynamiques, une par imprimante enregistrée. Cette ambiguïté est résolue dans les epics (Story 3.4 — instanciation d'une file par imprimante enregistrée), mais la prose de l'architecture est légèrement en retard.

*Impact : aucun — ARCH-009 est la contrainte authoritative et les stories l'appliquent correctement.*

**Minor — Entités/contrôleur de registre d'imprimantes absents de la structure architecture**

La structure de répertoires dans `architecture.md` sous `print/` ne montre que `PrintQueueService.java`, `ThermalPrintService.java`, `DocumentPrintService.java`. Elle ne liste pas d'entité `Printer`, de `PrinterRepository`, ni de `PrinterController` qui seront nécessaires pour FR-076/FR-077 (enregistrement admin) et FR-098 (sélection bénévole).

*Impact : mineur — Story 3.8 et Story 3.9 dans les epics spécifient ce comportement ; l'implémenteur devra ajouter ces classes dans le package `print/`.*

**Minor — Écran de sélection d'imprimante à la connexion (FR-098) absent de la structure Angular**

La structure frontend dans l'architecture ne montre pas de composant dédié à la sélection d'imprimante post-connexion. Story 3.9 le spécifie dans les epics.

*Impact : mineur — composant à créer sous `components/shared/` ou `components/pos/` lors de l'implémentation de Story 3.9.*

### Warnings

Aucun warning bloquant. Les trois gaps identifiés sont des lacunes de documentation de l'architecture, non des incompatibilités architecturales. L'architecture supporte pleinement toutes les exigences UX.

---

## Epic Quality Review

### Validation de la valeur utilisateur par epic

| Epic | Titre | User-centric ? | Valeur standalone ? |
|---|---|---|---|
| 1 | Fondation applicative, Auth & i18n | ✓ — déployer, se connecter, gérer comptes, configurer langue | ✓ — livrables tangibles (authentification fonctionnelle, UI cohérente, guide d'installation) |
| 2 | Gestion du cycle de vie des éditions | ✓ — créer/piloter/archiver des éditions | ✓ — admin peut créer une édition et avancer dans les phases |
| 3 | Enregistrement des vendeurs & Dépôt | ✓ — bénévoles enregistrent vendeurs et articles, impriment étiquettes | ✓ (nécessite Epic 1 + 2) |
| 4 | Point de vente | ✓ — bénévoles vendent des articles par scan | ✓ (nécessite Epic 1 + 2 + 3) |
| 5 | Post-vente, Reversements & Rapports | ✓ — reversements, rapports, exports | ✓ (nécessite Epic 1–4) |
| 6 | Catalogue articles | ✓ — parcourir/filtrer les articles pendant l'événement | ✓ (nécessite Epic 1 + 3) |

### Validation des dépendances inter-epics

- **Epic 1 → standalone** ✓
- **Epic 2 → Epic 1** ✓ (authentification prérequise)
- **Epic 3 → Epic 1 + 2** ✓ (phases prérequises pour les règles de dépôt)
- **Epic 4 → Epic 1 + 2 + 3** ✓ (articles doivent exister)
- **Epic 5 → Epic 1 + 4** ✓ (ventes doivent exister)
- **Epic 6 → Epic 1 + 3** ✓ (articles doivent exister)
- **Dépendance croisée documentée** : Story 4.6 (client Angular) dépend de Story 2.6 (serveur SSE). Dependency explicitement documentée dans la story — acceptable.

Aucune dépendance forward ni circulaire détectée.

### Validation par story — Points saillants

**Story 1.1 — Squelette de projet**
- Projet greenfield → story de setup en Epic 1, Story 1 est un pattern attendu (ARCH-001). ✓
- Les 4 changesets Liquibase initiaux créent des tables utilisées dans des stories ultérieures (catégories/tables en Story 2.3 ; instance_config en Story 1.5). Architecturalement mandaté (ARCH-006). Acceptable.
- ACs Given/When/Then, testables, complets. ✓

**Story 3.4 — Infrastructure d'impression**
- Labelisée "Story technique prérequise (spike accepté) — Aucune valeur utilisateur visible en sprint review."
- Contradiction terminologique : un spike sert à explorer l'inconnu, mais les ACs sont déterministes (Given/When/Then concrets). Il s'agit d'une **story d'infrastructure technique** (enabler story), pas d'un spike.
- Recommandation : renommer le label en "Story technique prérequise (infrastructure enabler)" pour clarté.
- Contenu et séquençage : ✓ — correctement placée avant Stories 3.5–3.9 qui l'utilisent.

**Acceptance Criteria — Revue transversale**
- Format Given/When/Then : appliqué de façon cohérente sur toutes les stories ✓
- Couverture des chemins d'erreur : présente dans les stories critiques (ex. scan article vendu → 4.1, conflit 409 → 4.4, montant supérieur au net → 5.1). ✓
- Mesurabilité : critères spécifiques et vérifiables ✓
- Chemins d'erreur manquants mineurs :
  - Story 3.1 : aucun AC pour l'email invalide lors de la création vendeur (format email validé côté serveur selon FR-019)
  - Story 2.1 : aucun AC pour le nom d'édition vide (validation Bean Validation implicite)
  - Ces omissions sont marginales — la validation est couverte par ARCH-013 (RFC 7807) et le comportement est standard.

**Taille des stories**
Toutes les stories sont dimensionnées de façon appropriée — aucun "super-story" épique dans une story, aucune story trop fine (ex. "créer un bouton"). Séquençage correct au sein de chaque epic.

**Traçabilité FR ↔ Story**
La section "Carte de couverture FR" de l'epics.md mappe exhaustivement chaque FR vers son epic. Les stories elles-mêmes citent les FR dans les critères d'acceptation. Traçabilité complète.

### Checklist qualité par epic

| Critère | Epic 1 | Epic 2 | Epic 3 | Epic 4 | Epic 5 | Epic 6 |
|---|---|---|---|---|---|---|
| Valeur utilisateur | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Indépendance (no forward deps) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Stories bien dimensionnées | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ACs Given/When/Then | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Traçabilité FR | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Tables créées au bon moment | ✓ (ARCH-006) | ✓ | ✓ | ✓ | ✓ | ✓ |

### Violations identifiées

#### 🔴 Critical — Aucune

#### 🟠 Major — Aucune

#### 🟡 Minor (2)

**M1 — Story 3.4 labellisée "spike" à tort**
La story est en réalité une infrastructure enabler story avec des ACs déterministes. Le label "spike accepté" est incorrect et peut créer de la confusion sur le type de livrable attendu.
*Recommandation : changer le label en "Story technique prérequise (infrastructure enabler)".*

**M2 — Validation email/nom manquante dans quelques ACs**
Stories 3.1 et 2.1 n'ont pas d'AC explicite pour les cas d'entrée invalide (email malformé, nom vide). Ceux-ci seront traités par Bean Validation + RFC 7807 mais ne sont pas vérifiés dans les ACs.
*Recommandation : ajouter un AC "Étant donné... quand un email invalide est soumis... Alors une réponse 422 avec Problem Details est retournée."*

---

## Summary and Recommendations

### Overall Readiness Status

# ✅ READY

PluriBourse est prêt pour la Phase 4 — Implémentation.

### Synthesis

| Dimension | Résultat | Détail |
|---|---|---|
| Document Discovery | ✓ Complet | 6 artefacts présents et inventoriés |
| PRD Analysis | ✓ Complet | 96 FRs, 7 NFRs, addendum intégré |
| Epic Coverage | ✓ 100 % | 96/96 FRs couverts, aucun FR manquant |
| UX Alignment | ✓ Aligné | Docs finaux, 2 passes de validation, 3 lacunes documentaires mineures |
| Epic Quality | ✓ Bon niveau | 0 critical, 0 major, 2 minor |
| **Synthèse** | **PRÊT** | **Aucun bloquant identifié** |

### Critical Issues Requiring Immediate Action

**Aucune.**

Tous les findings critiques et high des passes de validation UX ont été corrigés avant ce rapport. Le PRD est complet, les epics couvrent 100 % des FRs, l'architecture est mature.

### Issues à traiter en cours d'implémentation (non bloquantes)

**I1 — Architecture : prose "deux files" ≠ N files dynamiques (Minor)**
La section "Infrastructure d'Impression" de `architecture.md` décrit 2 files alors que ARCH-009 + FR-076/FR-077 spécifient N files dynamiques.
→ Corriger la prose lors de la première implémentation de la Story 3.4.

**I2 — Architecture : entités de registre d'imprimantes manquantes dans la structure (Minor)**
Le package `print/` ne liste pas les classes `Printer.java`, `PrinterRepository.java`, `PrinterController.java` qui seront nécessaires pour Stories 3.8 et 3.9.
→ Ajouter ces classes dans le package `print/` lors de l'implémentation des Stories 3.8–3.9.

**I3 — Architecture : composant sélection d'imprimante absent de la structure Angular (Minor)**
La structure frontend ne mentionne pas le composant de sélection d'imprimante à la connexion (FR-098, Story 3.9).
→ Créer sous `components/shared/printer-selection.component.ts` ou `components/pos/`.

**I4 — Story 3.4 : label "spike" incorrect (Minor)**
La story est en réalité une infrastructure enabler story, pas un spike d'exploration.
→ Renommer en "Story technique prérequise (infrastructure enabler)".

**I5 — Stories 3.1 et 2.1 : ACs de validation email/nom manquants (Minor)**
Les chemins d'erreur pour email malformé ou nom vide ne sont pas explicités.
→ Ajouter un AC de validation lors de l'implémentation (Bean Validation + RFC 7807 couvriront le comportement).

### Recommended Next Steps

1. **Démarrer Epic 1, Story 1.1** — Générer le squelette du projet (Spring Initializr + `ng new` + Docker Compose). Cf. ARCH-001 et les commandes exactes dans `architecture.md` §"Passation pour l'Implémentation".

2. **Suivre la séquence architecturale** — Epic 1 → Epic 2 (machine à états de phase en premier, ARCH-015) → Epic 3 (infrastructure d'impression avant dépôt, ARCH-009) → Epic 4 → Epic 5 → Epic 6.

3. **Ne pas ignorer ARCH-004** — Le test Testcontainers MariaDB de concurrence POS (Story 4.4) doit être livré avant la mise en production de F4. H2 ne peut pas valider le comportement de verrouillage optimiste.

4. **Corriger le bug BigDecimal de JPageFlow** — avant d'implémenter le tri par prix dans le catalogue (Story 6.1, ARCH-005).

5. **Traiter les 5 issues mineurs** ci-dessus au fur et à mesure des stories concernées — aucun ne bloque le démarrage.

### Final Note

Cette évaluation a couvert 5 étapes : discovery, analyse PRD, couverture epics, alignement UX, revue qualité epics. Elle a identifié **0 issue critique, 0 issue majeure, 5 issues mineures** (dont 3 lacunes documentaires de l'architecture et 2 ACs incomplets).

Les artefacts de planification de PluriBourse — PRD + addendum, architecture, UX (DESIGN.md + EXPERIENCE.md), epics — forment un ensemble cohérent et suffisamment précis pour démarrer l'implémentation sans ambiguïté architecturale ni risque de régression fonctionnelle.

**Verdict : PRÊT POUR L'IMPLÉMENTATION.**
