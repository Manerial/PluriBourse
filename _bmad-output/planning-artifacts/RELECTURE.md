# Relecture PluriBourse — Suivi de lecture

> Cocher au fur et à mesure. L'ordre recommandé est celui des étapes.
> Les vérifications de cohérence inter-documents ont déjà été faites — cette relecture est axée sur le fond : "est-ce que c'est exactement ce que je veux construire ?"

---

## Étape 1 — PRD

**Question fil rouge : tout ce qui est décrit correspond-il à ma vision du produit ?**

- [ ] `planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md`
  - [x] Énoncé du problème & Vision
  - [ ] Rôles (Admin, Bénévole, Vendeur hors app)
  - [ ] Périmètre v1 — Inclus
  - [ ] Périmètre v1 — Hors périmètre
  - [ ] F1 — Internationalisation (FR-001–007)
  - [ ] F2 — Gestion des éditions & cycle de vie (FR-008–016, FR-080, FR-082, FR-088)
  - [ ] F3 — Gestion des vendeurs & articles (FR-017–032)
  - [ ] F4 — Point de vente (FR-033–042)
  - [ ] F4bis — Lots (FR-043–048)
  - [ ] F5 — Post-vente & Reversements (FR-049–053)
  - [ ] F6 — Rapports (FR-054–059)
  - [ ] F7 — Comptes & Accès (FR-060–067)
  - [ ] F8 — Infrastructure & Déploiement (FR-068–074)
  - [ ] F9 — Impression (FR-075–079)
  - [ ] F10 — Catalogue articles (FR-083–089, FR-090)
  - [ ] Exigences non fonctionnelles (NFR)
  - [ ] Métriques de succès

- [ ] `planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md`
  - [ ] Infrastructure & déploiement
  - [ ] FR-056 — décision vendeurs non soldés (pas de PDF)
  - [ ] FR-091 / FR-092 — exports CSV
  - [ ] Architecture i18n
  - [ ] Impression étiquettes thermiques 57mm

---

## Étape 2 — UX

**Question fil rouge : un bénévole non technique pourrait-il s'en sortir le jour J ?**

### Mockups (ouvrir dans le navigateur)

- [ ] `ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-admin-vendors.html` — liste vendeurs admin
- [ ] `ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html` — formulaire dépôt bénévole
- [ ] `ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-pos-caisse.html` — caisse POS (lot incomplet)
- [ ] `ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-pos-caisse-lot-complet.html` — caisse POS (lot complet)
- [ ] `ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-phase-control.html` — contrôle de phase admin

### Specs comportementales

- [ ] `ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md`
  - [ ] Architecture de l'information (routes Admin + Bénévole)
  - [ ] Navigation sidebar Admin
  - [ ] Voice & Tone
  - [ ] Component Patterns
  - [ ] State Patterns
  - [ ] Interaction Primitives
  - [ ] Accessibility Floor
  - [ ] Flow 1 — Dépôt vendeur
  - [ ] Flow 2 — Vente avec lot incomplet
  - [ ] Flow 3 — Reversement, vendeur absent
  - [ ] Flow 4 — Transition de phase

### Specs visuelles

- [ ] `ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md`
  - [ ] Brand & Style
  - [ ] Colors
  - [ ] Typography
  - [ ] Layout & Spacing
  - [ ] Components

---

## Étape 3 — Architecture

**Question fil rouge : les décisions techniques sont-elles raisonnables à implémenter ?**

- [ ] `planning-artifacts/architecture.md`
  - [ ] Contexte & contraintes
  - [ ] Stack technique (Spring Boot 4.0.6, Angular 21, MariaDB, JPageFlow)
  - [ ] Modèle de données — entités et relations
  - [ ] ARCH-001 à ARCH-005 — décisions structurantes backend
  - [ ] ARCH-006 à ARCH-010 — frontend & impression
  - [ ] ARCH-011 à ARCH-016 — infrastructure, sécurité, SSE, i18n
  - [ ] File d'impression ESC/POS
  - [ ] Gestion de la concurrence (verrou optimiste `@Version`)
  - [ ] SSE — `SseEmitterRegistry`

---

## Étape 4 — Epics

**Question fil rouge : les critères d'acceptation couvrent-ils tous les cas auxquels je pense ?**

### Epic 1 — Fondation applicative, Auth & i18n
- [ ] Story 1.1 — Squelette projet & Docker Compose
- [ ] Story 1.2 — Authentification & RBAC
- [ ] Story 1.3 — Comptes bénévoles & gestion admin
- [ ] Story 1.4 — Paramètres instance
- [ ] Story 1.5 — Réinitialisation mot de passe
- [ ] Story 1.6 — Préférence de langue utilisateur & i18n
- [ ] Story 1.7 — Système de design Angular Material
- [ ] Story 1.8 — Composants partagés (dialogs, toasts, accessibilité)

### Epic 2 — Gestion du cycle de vie des éditions
- [ ] Story 2.1 — CRUD édition & configuration taux de commission
- [ ] Story 2.2 — Contrôle du cycle de phases ⚠️ *critique — machine à états*
- [ ] Story 2.3 — Catégories & correspondance des tables
- [ ] Story 2.4 — Notification de phase en temps réel (SSE)
- [ ] Story 2.5 — Clôture & Nettoyage de l'édition

### Epic 3 — Enregistrement vendeurs & Dépôt
- [ ] Story 3.1 — Gestion des profils vendeurs
- [ ] Story 3.2 — Enregistrement d'articles & assignation de table
- [ ] Story 3.3 — Création et gestion des lots
- [ ] Story 3.4 — Infrastructure d'impression (files d'attente serveur)
- [ ] Story 3.5 — Génération & Impression des étiquettes thermiques
- [ ] Story 3.6 — Génération & Impression du bordereau de dépôt PDF

### Epic 4 — Point de vente
- [ ] Story 4.1 — Composant scanner & scan d'articles
- [ ] Story 4.2 — Gestion du panier & validation du paiement
- [ ] Story 4.3 — Gestion des lots au POS ⚠️ *logique d'intégrité complexe*
- [ ] Story 4.4 — Sécurité de la concurrence multi-postes ⚠️ *cas limite critique*
- [ ] Story 4.5 — Impression de la facture acheteur
- [ ] Story 4.6 — Annulation du panier lors d'une transition de phase

### Epic 5 — Post-vente, Reversements & Rapports
- [ ] Story 5.1 — Flux de solde des vendeurs
- [ ] Story 5.2 — Génération du bilan de vente PDF
- [ ] Story 5.3 — Rapport de ventes journalier (Admin)
- [ ] Story 5.4 — Bilan d'édition & vendeurs non soldés
- [ ] Story 5.5 — Page des rapports admin

### Epic 6 — Catalogue articles
- [ ] Story 6.1 — Catalogue filtrable & triable
- [ ] Story 6.2 — Secours catalogue-vers-panier au POS

---

## Étape 5 — Rapport final

- [ ] `planning-artifacts/implementation-readiness-report-2026-06-09.md`
  - [ ] Observations et résolutions
  - [ ] Statut global — confirmer "READY FOR IMPLEMENTATION"

---

## Mes notes de relecture

<!-- Espace libre pour noter les questions, doutes ou décisions à prendre pendant la lecture -->
