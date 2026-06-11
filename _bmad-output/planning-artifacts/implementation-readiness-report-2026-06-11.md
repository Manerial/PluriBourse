---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
documentsUsed:
  prd: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md
  prdAddendum: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md
  architecture: _bmad-output/planning-artifacts/architecture.md
  epics: _bmad-output/planning-artifacts/epics.md
  uxDesign: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md
  uxExperience: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-11
**Project:** PluriBourse

---

## PRD Analysis

### Functional Requirements

| Groupe | FRs | Nb |
|---|---|---|
| F1 — Internationalisation | FR-001–007 | 7 |
| F2 — Éditions & Cycle de vie | FR-008–016, FR-080, FR-082, FR-088 | 12 |
| F3 — Vendeurs & Articles | FR-017–032, FR-043–045, FR-089 | 20 |
| F4 — Point de Vente | FR-033–042, FR-046–048, FR-081, FR-090, FR-093 | 16 |
| F5 — Post-Vente & Reversements | FR-049–053 | 5 |
| F6 — Rapports | FR-054–059, FR-091, FR-092, FR-094 | 9 |
| F7 — Comptes & Accès | FR-060–067 | 8 |
| F8 — Infrastructure & Déploiement | FR-068–074 | 7 |
| F9 — Infrastructure d'Impression | FR-075–079 | 5 |
| F10 — Catalogue Articles | FR-083–086 | 4 |
| **Total** | | **93** |

### Non-Functional Requirements

| ID | Catégorie |
|---|---|
| NFR-001 | Performance (500ms caisse, 1s pages, Raspberry Pi 4) |
| NFR-002 | Concurrence (verrou optimiste, 3 postes simultanés) |
| NFR-003 | Exactitude financière (BigDecimal, précision centime) |
| NFR-004 | Compatibilité navigateur (Chrome, Firefox, Edge, Safari) |
| NFR-005 | Compatibilité scanner (USB HID, AZERTY/QWERTY) |
| NFR-006 | Fiabilité (aucune perte de données sur fermeture navigateur) |
| NFR-007 | RGPD (suppression données personnelles vendeurs) |
| **Total** | **7** |

### Additional Requirements & Constraints

- **RGPD :** Nom du vendeur n'apparaît pas sur les étiquettes articles (FR-027).
- **Architecture i18n :** Frontend : ngx-translate (JSON). Backend : Spring MessageSource (.properties). Fichiers séparés.
- **Impression ESC/POS :** Protocole ESC/POS pour imprimante thermique 57mm. Bibliothèque candidate : `escpos-coffee`.
- **Code 128 :** Généré côté serveur, rendu en bitmap avant envoi ESC/POS.
- **BigDecimal :** Tous les calculs financiers (commission, reversements) en BigDecimal.
- **Logs :** Aucune donnée personnelle (nom, email, téléphone vendeur) dans les logs.

---

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Groupe PRD | Epic | Statut |
|---|---|---|---|
| FR-001–004 | F1 i18n | Epic 1 | ✓ Couvert |
| FR-005–007 | F1+F2 i18n | Epic 1+2 | ✓ Couvert |
| FR-008–016 | F2 Éditions | Epic 2 | ✓ Couvert |
| FR-017–018 | F2 Catégories | Epic 2 — Story 2.3 | ✓ Couvert |
| FR-019–021 | F3 Vendeurs | Epic 3 | ✓ Couvert |
| FR-022–025 | F3 Articles | Epic 3 | ✓ Couvert |
| FR-026–032 | F3 Impression | Epic 3 | ✓ Couvert |
| FR-033–042 | F4 POS | Epic 4 | ✓ Couvert |
| FR-043–045 | F3 Lots dépôt | Epic 3 | ✓ Couvert |
| FR-046–048 | F4 Lots caisse | Epic 4 | ✓ Couvert |
| FR-049–053 | F5 Post-vente | Epic 5 | ✓ Couvert |
| FR-054–059 | F6 Rapports | Epic 5 | ✓ Couvert |
| FR-060–067 | F7 Comptes | Epic 1 | ✓ Couvert |
| FR-068–074 | F8 Infrastructure | Epic 1 | ✓ Couvert |
| FR-075–079 | F9 Impression | Epic 3 | ✓ Couvert |
| FR-080 | F2 Copie édition | Epic 2 | ✓ Couvert |
| FR-081 | F4 Retrait lot | Epic 4 | ✓ Couvert |
| FR-082 | F2 Rollback phase | Epic 2 | ✓ Couvert |
| FR-083–086 | F10 Catalogue | Epic 6 | ✓ Couvert |
| FR-088 | F2 Nettoyage | Epic 2 | ✓ Couvert |
| FR-089 | F3 Commission incomplet | Epic 3 | ✓ Couvert |
| FR-090 | F4 Panier/transition phase | Epic 2+4 | ✓ Couvert |
| FR-091–092 | F6 Exports CSV | Epic 5 | ✓ Couvert |
| FR-093 | F4 Moyen de paiement | Epic 4 — Story 4.2 | ✓ Couvert |
| FR-094 | F6 Ventilation paiement | Epic 5 | ✓ Couvert |

### Missing Requirements

Aucun FR manquant.

### Coverage Statistics

- **Total FRs PRD :** 93
- **FRs couverts dans les épics :** 93
- **Couverture :** 100 %

---

### PRD Completeness Assessment

Le PRD est complet et bien structuré. Il couvre 93 exigences fonctionnelles réparties sur 10 groupes (F1–F10) et 7 NFRs. Les exigences tardives (FR-080, FR-081, FR-082, FR-088–094) sont correctement intégrées. Les cas limites importants (lots incomplets, retour de phase, vendeurs soldés, conflits de scan simultané) sont explicitement spécifiés.

---

## UX Alignment Assessment

### Documents UX

| Document | Chemin | Statut |
|---|---|---|
| DESIGN.md | `ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md` | ✓ Présent (10/06/2026) |
| EXPERIENCE.md | `ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` | ✓ Présent (11/06/2026) |

### Couverture des Exigences UX

22 exigences UX (UX-DR1–UX-DR22) identifiées dans `epics.md`. Chacune est référencée dans au moins une story des épics 1 à 6.

### UX ↔ PRD — Alignement

| Zone PRD | FRs | Couverture EXPERIENCE.md | Résultat |
|---|---|---|---|
| Cycle de vie des phases | F2 (FR-008–016, FR-082) | Phase chip SSE, waiting page, flow 4 (transition de phase), bannière clôturée | ✓ Aligné |
| Scanner POS | FR-033, FR-034 | Composant `Scanner input` : USB HID, autofocus, AZERTY/QWERTY, pas de debounce | ✓ Aligné |
| Lots en caisse | FR-046, FR-047, FR-048, FR-081 | État "Lot incomplet" + Flow 2 (Marc retire le lot) + composant "Lot dans le panier" | ✓ Aligné (OQ-001 ouvert) |
| Dépôt vendeur | FR-017–025, FR-043–045 | Flow 1 (Sophie), composant "Formulaire dépôt", table auto-assignée | ✓ Aligné |
| Reversement | FR-049–053 | Flow 3 (vendeur absent, non réclamé), composant "Récapitulatif reversement imprimable" | ✓ Aligné |
| Notification phase SSE | FR-090 | Phase chip, état "Changement de phase pendant transaction", toast panier annulé | ✓ Aligné |
| Rapports | FR-054–059, FR-091–092, FR-094 | Composant "Page Rapports" avec affichage conditionnel par phase, boutons export CSV | ✓ Aligné |
| Impression | FR-075–079 | Interaction Primitives (Impression), composants thermique + A4, toast persistant imprimante hors ligne | ✓ Aligné |
| Accessibilité | NFR-004 | WCAG 2.2 AA, focus ring, focus trap dialogs, aria-labels, taille 44×44px | ✓ Aligné |
| Conflit POS multi-postes | NFR-002 | État "Conflit POS" : notification rouge + liste articles conflictuels, retrait manuel | ✓ Aligné |
| **Moyen de paiement (FR-093)** | FR-093 | **Non décrit dans EXPERIENCE.md** — aucun composant de sélection de moyen de paiement | ⚠️ Lacune mineure |

### UX ↔ Architecture — Alignement

| Exigence UX | Décision Architecturale | Résultat |
|---|---|---|
| SSE phase-changed + basket-cancelled | `SseEmitter` Spring + `EventSource` Angular (architecture §Notification de Phase) | ✓ Supporté |
| Angular Material (MatPaginator, dialogs, toasts) | `@angular/material` dans les dépendances frontend | ✓ Supporté |
| Scanning USB HID + AZERTY/QWERTY | Mapping des codes de touches dans le composant Angular (architecture §Frontend) | ✓ Supporté |
| Conflit POS 409 + liste articles | Verrouillage optimiste + `@Version` + contrainte UNIQUE BDD (architecture §Concurrence) | ✓ Supporté |
| Session sans expiration | `server.servlet.session.timeout=-1` (architecture §Auth) | ✓ Supporté |
| Toast persistant imprimante | Erreurs remontées via SSE/polling (architecture §Impression) | ✓ Supporté |
| Skeleton rows (chargement initial) | Pas de cache v1 — MariaDB SSD, réponses rapides NFR-001 | ✓ Compatible |
| Responsive / mobile | Non requis — déploiement desktop événementiel uniquement | ✓ Hors périmètre confirmé |

### Lacunes et Risques

| ID | Sévérité | Description | Recommandation |
|---|---|---|---|
| UX-GAP-001 | Mineure | FR-093 (moyen de paiement au POS) non couvert dans EXPERIENCE.md — l'UX du sélecteur cash/carte/chèque lors de la validation du panier n'est pas spécifiée | À détailler dans la story 4.2 avant implémentation |
| UX-OQ-001 | Mineure | OQ-001 ouvert — comportement de blocage lot incomplet (FR-047) non définitivement validé par le métier | À confirmer avant d'implémenter Story 4.3 |

### Conclusion UX

Les documents UX sont complets et alignés avec le PRD et l'architecture sur la quasi-totalité du périmètre. Une lacune mineure est identifiée (FR-093) — résolue : la Story 4.2 couvre explicitement le sélecteur de moyen de paiement dans ses AC. L'OQ-001 reste ouvert en attente de confirmation métier.

---

## Epic Quality Review

### Synthèse par Epic

| Epic | Titre | Valeur utilisateur | Indépendance | Verdict |
|---|---|---|---|---|
| 1 | Fondation applicative, Auth & i18n | ✓ Clairement utilisateur (déploiement, connexion, langue) | ✓ Autonome | ✓ Conforme |
| 2 | Gestion du cycle de vie des éditions | ✓ Admin pilote les phases | ✓ Requiert Epic 1 uniquement | ✓ Conforme |
| 3 | Enregistrement des vendeurs & Dépôt | ✓ Bénévoles enregistrent les vendeurs | ✓ Requiert Epics 1+2 | ✓ Conforme |
| 4 | Point de vente | ✓ Bénévoles vendent les articles | ✓ Requiert Epics 1+2+3 | ✓ Conforme |
| 5 | Post-vente, Reversements & Rapports | ✓ Bénévoles soldent les vendeurs | ✓ Requiert Epics 1–4 | ✓ Conforme |
| 6 | Catalogue articles | ✓ Tous les rôles consultent les articles | ✓ Requiert Epics 1+3 | ✓ Conforme |

Aucune dépendance circulaire. Chaque épic consomme uniquement les sorties des épics précédents.

### Conformité des Stories

| Story | Persona | AC BDD | Cas d'erreur | Verdict |
|---|---|---|---|---|
| 1.1 Squelette projet | Développeur | ✓ | ✓ (profil dev/prod) | ⚠️ Technique |
| 1.2 Auth & RBAC | Admin | ✓ | ✓ (403, logout) | ✓ |
| 1.3 Comptes bénévoles | Admin | ✓ | ✓ (compte désactivé, FR-061) | ✓ |
| 1.4 Reset mdp CLI | Admin | ✓ | ✓ (changement forcé) | ✓ |
| 1.5 Config instance | Admin | ✓ | ✓ (héritage éditions) | ✓ |
| 1.6 i18n | Utilisateur | ✓ | ✓ (langue navigateur non prise en charge) | ✓ |
| 1.7 Design system | Utilisateur | ✓ | ✓ (multi-navigateurs) | ✓ |
| 1.8 Composants partagés | Utilisateur | ✓ | ✓ (toast erreur persistant) | ✓ |
| 1.9 Guide installation | Responsable non technique | ✓ | ✓ (vérification résultat) | ✓ |
| 2.1 CRUD éditions | Admin | ✓ | ✓ (édition active déjà présente) | ✓ |
| 2.2 Contrôle phases | Admin | ✓ | ✓ (retour arrière post-nettoyage) | ✓ |
| 2.3 Catégories & Tables | Admin | ✓ | ✓ (table manquante) | ✓ |
| 2.4 SSE phase chip | Bénévole | ✓ | ✓ (reconnexion SSE) | ✓ |
| 2.5 Clôture & Nettoyage | Admin | ✓ | ✓ (métriques post-nettoyage) | ✓ |
| 2.6 Annulation panier SSE | Admin | ✓ | ✓ (panier absent) | ✓ |
| 3.1 Profils vendeurs | Bénévole | ✓ | ✓ (RGPD, pas de PII dans logs) | ✓ |
| 3.2 Articles & Table auto | Bénévole | ✓ | ✓ (hors phase Dépôt) | ✓ |
| 3.3 Lots dépôt | Bénévole | ✓ | ✓ (étiquette lot) | ✓ |
| 3.4 Infrastructure impression | Développeur | ✓ | ✓ (imprimante hors ligne) | ⚠️ Technique |
| 3.5 Étiquettes thermiques | Bénévole | ✓ | ✓ (RGPD étiquette) | ✓ |
| 3.6 Bordereau dépôt PDF | Bénévole | ✓ | ✓ (réimprimable) | ✓ |
| 3.7 Diagnostic file impression | Admin | ✓ | ✓ (403 bénévole) | ✓ |
| 4.1 Scanner | Bénévole caissier | ✓ | ✓ (article vendu, incomplet) | ✓ |
| 4.2 Panier & Paiement | Bénévole caissier | ✓ | ⚠️ AC structure (cf. ci-dessous) | ⚠️ Mineure |
| 4.3 Lots POS | Bénévole caissier | ✓ | ✓ (retrait lot entier) | ✓ |
| 4.4 Concurrence multi-postes | Bénévole caissier | ✓ | ✓ (409, test Testcontainers) | ✓ |
| 4.5 Facture acheteur | Bénévole caissier | ✓ | ✓ (réimprimable) | ✓ |
| 4.6 Changement phase POS | Bénévole caissier | ✓ | ✓ (panier absent) | ✓ |
| 5.1 Solde vendeurs | Bénévole | ✓ | ✓ (montant sup/inf, non réclamé) | ✓ |
| 5.2 Bilan vente PDF | Bénévole/Admin | ✓ | ✓ (BigDecimal, i18n) | ✓ |
| 5.3 Bilan journalier | Admin | ✓ | ✓ (403 bénévole) | ✓ |
| 5.4 Bilan édition & non soldés | Admin | ✓ | ✓ (post-nettoyage) | ✓ |
| 5.5 Page rapports | Admin | ✓ | ✓ (sections conditionnelles par phase) | ✓ |
| 6.1 Catalogue filtrable | Admin/Bénévole | ✓ | ✓ (post-nettoyage, bug JPageFlow) | ⚠️ Mineure |

### Violations et Risques Identifiés

#### 🟡 Préoccupations Mineures

| ID | Story | Description | Recommandation |
|---|---|---|---|
| EQ-001 | Story 1.1 | Persona développeur (`En tant que développeur`) — story technique sans valeur utilisateur directe. Standard pour les stories de setup ; auto-justifié par la nécessité du squelette de projet. | Acceptable — pattern reconnu pour les stories fondatrices. Aucune action. |
| EQ-002 | Story 3.4 | Story technique explicitement marquée « Aucune valeur utilisateur visible en sprint review ». | Acceptable — bien documenté, prérequis nécessaire pour les Stories 3.5–3.7. Aucune action. |
| EQ-003 | Story 4.2 | Un AC utilise un anti-pattern BDD : le `Alors` décrit une action utilisateur plutôt qu'une réponse système (« Alors le bénévole sélectionne le moyen de paiement »). Devrait être : « Alors un sélecteur de moyen de paiement est affiché ». | Corriger l'AC avant implémentation pour éviter toute ambiguïté. |
| EQ-004 | Story 6.1 | Un AC encode un bug bibliothèque connu (JPageFlow v1.5.0, tri BigDecimal) comme échec de test acceptable. Cela risque de rester non résolu indéfiniment. | Ajouter un ticket de suivi pour le correctif JPageFlow. Reformuler l'AC pour rejeter le tri BigDecimal cassé plutôt que l'accepter. |

#### ✅ Aucune Violation Critique ni Majeure

- Pas d'épic purement technique sans valeur utilisateur
- Pas de dépendances en avant (forward dependencies) non déclarées
- Pas de circularité entre épics
- Pas de stories surdimensionnées ne pouvant être complétées indépendamment
- Schéma BDD : les 4 changesets initiaux (Story 1.1) couvrent le minimum nécessaire pour l'auth et Spring Session — les tables métier sont créées dans les épics concernés

### Conclusion Qualité des Épics

34 stories sur 34 sont correctement structurées. Aucune violation critique ou majeure. 4 préoccupations mineures identifiées (EQ-001 à EQ-004), dont 2 sont des patterns documentés (stories techniques), 1 est un léger défaut de formulation AC (Story 4.2) et 1 porte sur un bug bibliothèque à tracer (Story 6.1).

---

## Synthèse et Recommandations

### Statut Global de Préparation

**PRÊT POUR L'IMPLÉMENTATION**

Aucune violation critique ni majeure détectée à travers les 5 axes d'évaluation. Le projet est prêt à démarrer l'Epic 1 dès maintenant.

### Problèmes Nécessitant une Action Avant les Stories Concernées

| Priorité | ID | Story concernée | Action requise |
|---|---|---|---|
| Avant Story 4.2 | EQ-003 | Story 4.2 | Reformuler l'AC BDD : « Alors le bénévole sélectionne... » → « Alors un sélecteur de moyen de paiement est affiché ». |
| Avant Story 4.3 | UX-OQ-001 | Story 4.3 | Confirmer avec le métier si la validation doit être bloquée (OQ-001) ou si un avertissement seul suffit. La décision par défaut (blocage) est documentée dans `open-questions.md`. |
| Avant Story 6.1 | EQ-004 | Story 6.1 | Créer un ticket de suivi pour le correctif JPageFlow v1.5.0. L'AC devrait rejeter le tri BigDecimal cassé plutôt que l'accepter comme échec toléré. |

### Prochaines Étapes Recommandées

1. **Démarrer l'Epic 1** — Commencer par la Story 1.1 (squelette projet + Docker Compose). Toutes les dépendances sont en place.
2. **Corriger l'AC de la Story 4.2** — Avant d'implémenter l'Epic 4, reformuler l'AC du moyen de paiement (EQ-003).
3. **Trancher OQ-001** — Avant d'implémenter la Story 4.3, obtenir une confirmation métier sur le comportement des lots incomplets au POS.
4. **Tracker le bug JPageFlow** — Ouvrir un ticket pour suivre la résolution du tri BigDecimal (ARCH-005) avant l'implémentation de la Story 6.1.

### Note Finale

Cette évaluation a examiné 93 exigences fonctionnelles, 7 NFRs, 22 exigences UX, 6 épics et 34 stories. Elle a identifié **6 points d'attention** (2 UX + 4 Epic Quality), tous de sévérité mineure. Aucun ne bloque le démarrage de l'implémentation — certains devront être résolus avant les stories spécifiques de l'Epic 4 et de l'Epic 6.

**Évaluateur :** Claude (bmad-check-implementation-readiness)  
**Date :** 2026-06-11
