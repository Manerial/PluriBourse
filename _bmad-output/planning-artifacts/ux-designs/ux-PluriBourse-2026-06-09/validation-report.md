# Validation Report — PluriBourse (Run 2)

- **DESIGN.md / EXPERIENCE.md:** `ux-designs/ux-PluriBourse-2026-06-09/`
- **Run at:** 2026-06-15T00:00:00
- **Reviewers:** rubric walker · cohérence · couverture PRD

## Synthèse globale

La paire de spines PluriBourse — après intégration des 10 corrections de la première passe — est **globalement solide et prête pour le développement des flux opérationnels principaux**. Tous les findings critiques et high bloquants de la première validation sont résolus sans exception. Les 6 Key Flows, la cohérence de navigation (aucune route orpheline), le vocabulaire métier stable et les patterns d'accessibilité sont confirmés par trois reviewers indépendants.

Cette deuxième passe est élargie aux lentilles **cohérence interne** et **couverture PRD**. Un seul point de blocage technique émerge : le token `{typography.display-sm}` dans le composant `metric-tile` n'existe pas dans le système de design — confirmé indépendamment par le rubric walker et le reviewer cohérence.

**À traiter avant implémentation :** 1 critical (token manquant), 4 high (exception états vides non documentée, variante Banner non documentée, FR-025 indicateur complet/incomplet hors Dépôt, FR-084 filtre vendeur Catalogue). 12 medium constituent un second niveau de priorité, à adresser sprint par sprint.

## Verdicts rubric (8 dimensions)

| Dimension | Verdict |
|---|---|
| Flow coverage | **strong** |
| Token completeness | **adequate** (1 medium régression) |
| Component coverage | **strong** |
| State coverage | **strong** |
| Visual reference coverage | **strong** |
| Bloat & overspecification | **strong** |
| Inheritance discipline | **strong** |
| Shape fit | **strong** |

## Findings par sévérité

### Critical (1)

**[Rubric §2 + Cohérence 2.2]** `{typography.display-sm}` inexistant dans DESIGN.md (EXPERIENCE.md ligne 157 — composant metric-tile)
Scale typographique définie : display, headline, title-lg, title-md, body-lg, body-md, label-lg, label-sm. `display-sm` absent. `metric-tile.value-font` dans DESIGN.md frontmatter est déjà `{typography.title-lg}`.
*Fix :* Remplacer `{typography.display-sm}` par `{typography.title-lg}` dans EXPERIENCE.md ligne 157.

---

### High (4)

**[Cohérence 2.3]** Exception états vides sans action non documentée (EXPERIENCE.md — Voice and Tone)
Voice and Tone énonce "Jamais un état vide sans sortie" mais trois états documentés n'ont pas d'action : /volunteer/waiting en Préparation, Reversements liste vide, Catalogue post-Archivage. Contradiction formelle (cas métier-justifiés).
*Fix :* Ajouter dans Voice and Tone : "Exception : les états structurellement bloquants (phase non ouverte, édition archivée) n'ont pas d'action proposée — le message explicatif suffit."

**[Cohérence 2.6]** Variante Banner (info vs warning) non documentée (EXPERIENCE.md — Component Patterns Banner + Édition archivée)
Banner utilisé avec `{colors.warning}` / `{colors.on-warning}` (Catégories verrouillées) et `{colors.primary-container}` (Édition archivée). Aucune variante documentée.
*Fix :* Ajouter section "Variantes" dans la définition Banner : `warning` (Catégories verrouillées) et `info` (Édition archivée, tokens primary-container / on-primary-container).

**[PRD F3]** FR-025 — Indicateur complet/incomplet non modifiable hors phase Dépôt (EXPERIENCE.md — composant Catalogue)
Aucun chemin UX pour modifier complet/incomplet et commentaire en phases Vente, Post-vente, Clôturée.
*Fix :* Ajouter dans le composant Catalogue une action "Modifier statut" (accessible en toutes phases), dialog ou inline edit limité à complet/incomplet + commentaire.

**[PRD F3]** FR-084 — Filtre "nom du vendeur" absent du composant Catalogue (EXPERIENCE.md — composant Catalogue / liste filtrée)
*Fix :* Ajouter "nom du vendeur" dans la liste des filtres.

---

### Medium (12)

**[Cohérence 2.1]** Segmented control — tokens divergents : EXPERIENCE.md prose `{colors.primary}` vs DESIGN.md frontmatter `{colors.primary-container}` / `{colors.on-primary-container}`.
*Fix :* Aligner EXPERIENCE.md sur DESIGN.md frontmatter ou documenter l'exception.

**[Cohérence 2.2]** Dualité sémantique `warning` / `primary-container` (valeurs hex identiques `#FFF4EE`, noms différents — DESIGN.md frontmatter colors).
*Fix :* Documenter l'alias dans DESIGN.md Colors ou fusionner les tokens.

**[Cohérence 2.3]** Bouton "Confirmer" de l'action Non réclamé en `primary` alors que l'action est irréversible (EXPERIENCE.md — Key Flow 3). Exception non documentée vs pattern `error` pour les autres actions irréversibles.
*Fix :* Documenter l'exception (irréversible mais non destructif = primary suffisant) ou aligner sur `error`.

**[Cohérence 2.3]** Référence "Clean Edition" orpheline dans Interaction Primitives — terme disparu dans EXPERIENCE.md v2026-06-15 (remplacé par "Archiver l'édition").
*Fix :* Remplacer "Clean Edition" par "Archiver l'édition".

**[Cohérence 2.6]** Contraintes de phase absentes de 3 composants : Page Rapports (inaccessible Préparation/Dépôt), Page Reversements (Post-vente + Clôturée), Fiche Catégories & Tables (lecture seule de Dépôt à Clôturée).
*Fix :* Ajouter une note de phase dans chaque composant.

**[PRD F2]** FR-055 — Génération PDF bilan d'édition non décrite dans le composant Contrôle de phase (ni spinner, ni toast).
*Fix :* Enrichir le composant avec état de clôture : spinner + toast "Bilan d'édition généré (EN + FR)".

**[PRD F3]** FR-045 — Étiquette thermique lot sans spec visuelle différenciante (Prix du lot, mention Lot indivisible X/N).
*Fix :* Tableau comparatif étiquette standard vs lot dans le composant Formulaire lot.

**[PRD F3]** FR-019 — Champs vendeur obligatoires et validations absents du composant Formulaire dépôt.
*Fix :* Ajouter liste des champs (nom, prénom, email, téléphone) et leurs validations.

**[PRD F4]** FR-041 — Structure de la facture acheteur spécifiée dans le PRD uniquement ; EXPERIENCE.md ne décrit que le bouton.
*Fix :* Note sur la structure PDF (champs obligatoires) dans le composant Panier POS post-validation.

**[PRD F2]** FR-014 — Comportement du bouton Supprimer édition post-Préparation non spécifié (absent ? désactivé ?).
*Fix :* Préciser dans le composant "Page Éditions — liste".

**[PRD F3]** FR-030 — Structure du rouleau thermique (séparateur vendeur → étiquettes → séparateur article) documentée dans le PRD uniquement.
*Fix :* Note dans Interaction Primitives "Impression".

---

### Low (9)

**[Rubric §1]** Flow 2 sans failure path nommé. Optionnel.

**[Cohérence 2.1]** `notification-inline` — casse variable dans EXPERIENCE.md. *Fix :* Uniformiser en kebab-case.

**[Cohérence 2.1]** Banner — deux variantes visuellement différentes sans entrée dans DESIGN.md frontmatter (traité avec le finding high ci-dessus).

**[PRD F2]** FR-027 — Layout étiquette standard (ordre champs, INCOMPLET conditionnel, barcode, absence nom vendeur) dans le PRD uniquement. *Fix :* Note de structure dans Formulaire dépôt.

**[PRD F3]** FR-024 — Suppression d'article sans spec UX complète (ni CTA, ni dialog, ni post-état). *Fix :* Danger zone + dialog + toast dans composant Catalogue phase Dépôt.

**[PRD F2]** FR-010 — Unicité édition active sans contrainte UX sur le bouton "Créer une édition". *Fix :* Bouton désactivé + tooltip si édition active existe.

**[PRD F7]** FR-061 — Sélecteur de rôle non contraint (Admin ne doit pas être proposable). *Fix :* Exclure le rôle Admin du sélecteur de création utilisateur.

**[PRD F5]** FR-050 — Contenu bilan de vente décrit narrativement dans Flow 5 sans spec formelle de composant. *Fix :* Liste des champs dans Récapitulatif reversement imprimable.

---

## Couverture PRD par groupe fonctionnel

| Groupe | Total FRs UX | Couverts ✓ | Implicites ~ | Non couverts ✗ | N/A |
|---|---|---|---|---|---|
| F1 — Internationalisation | 7 | 5 | 1 | 0 | 1 |
| F2 — Éditions & Cycle de vie | 11 | 9 | 1 | 0 | 1 |
| F3 — Vendeurs & Articles | 18 | 9 | 4 | 3 | 2 |
| F4 — Point de Vente | 13 | 11 | 1 | 0 | 1 |
| F5 — Post-Vente | 7 | 5 | 2 | 0 | 0 |
| F6 — Rapports | 8 | 7 | 1 | 0 | 2 |
| F7 — Comptes Utilisateurs | 8 | 5 | 3 | 0 | 0 |
| F8 — Infrastructure (UI) | 8 | 2 | 0 | 0 | 6 |
| F9 — Impression | 5 | 5 | 0 | 0 | 0 |
| F10 — Catalogue | 4 | 3 | 1 | 0 | 0 |
| **Total** | **89** | **61** | **14** | **3** | **13** |

Taux explicite : **76 %** · Taux incluant implicites : **91 %**

## Résolutions passe 1 — 10/10 ✓

- ✓ critical — `{colors.error}` → `{colors.primary}` sur label de lot dans le Panier POS
- ✓ high — RFC 7807, verrou optimiste, garantie de non-double-vente retirés d'EXPERIENCE.md
- ✓ high — `SerialPort.getCommPorts()` remplacé par formulation neutre
- ✓ high — Section Inspiration & Anti-patterns ajoutée après Foundation
- ✓ medium — Note "spine wins on conflict" en blockquote en tête de la section IA
- ✓ medium — Entrées comportementales pour 6 composants (segmented-control, banner, metric-tile, danger-zone, skeleton-row, list-row)
- ✓ medium — "non persistée en base" retiré du composant Sélection d'imprimante
- ✓ low — `{colors.warning}` complété avec `{colors.on-warning}` et référence au composant banner
- ✓ low — État "Reversements — liste vide" ajouté dans State Patterns
- ✓ low — Comportement Enter dans "Somme remise" ajouté dans Interaction Primitives
- ✓ low — Entrée datée "Reversements sidebar" ajoutée dans le decision log

## Reviewer files

- `review-rubric.md`
- `review-coherence.md`
- `review-prd-coverage.md`
