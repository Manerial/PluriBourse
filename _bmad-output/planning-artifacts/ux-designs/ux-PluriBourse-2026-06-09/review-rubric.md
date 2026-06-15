# Spine Pair Review — PluriBourse (re-passe post-corrections)

## Overall verdict

La paire de spines est **prête pour la consommation en aval (architecture, story-dev)**. Les 10 corrections ciblées (1 critical, 2 high, 3 medium, 4 low) sont toutes résolues sans exception. Une régression de sévérité medium a été introduite lors de l'ajout des entrées comportementales : `{typography.display-sm}` dans le composant `metric-tile` ne résout pas — le token n'existe pas dans le frontmatter DESIGN.md. Un seul correctif à traiter avant distribution finale. Le reste de la spine pair est solide : couverture de flux complète sur 6 parcours utilisateurs, système de tokens quasi-exhaustif, sections dans l'ordre canonique, accessibilité remarquable.

---

## 1. Flow coverage — Vérifié / Aucune régression

Tous les flows de la passe précédente sont intacts. Les 6 Key Flows ont protagoniste nommé, étapes numérotées, climax beat et chemin d'échec là où applicable. Aucune modification dans la section Key Flows.

### Findings

- **low** Flow 2 (Lot incomplet / Marc) reste sans failure path nommé. Statut inchangé — acceptable, l'état est couvert en State Patterns. (EXPERIENCE.md, Flow 2). *Fix :* Optionnel — "Failure: scanner déconnecté → [voir State Patterns]".

---

## 2. Token completeness — Résolu / Régression détectée

**Critical résolu :** `{colors.error}` → `{colors.primary}` sur le label de lot dans le composant "Lot dans le panier" (ligne 142). La correction est présente et sémantiquement correcte : "label du lot en `{colors.primary}`".

**Low résolu :** `{colors.warning}` complété — le composant `banner` (ligne 156) spécifie maintenant "Fond `{colors.warning}`, texte `{colors.on-warning}`". La bordure gauche est héritée du frontmatter `banner` de DESIGN.md — cohérent.

### Findings

- **medium** `{typography.display-sm}` (EXPERIENCE.md, ligne 157 — composant `metric-tile` : "chiffre en `{typography.display-sm}`") est une **régression introduite lors de la correction de la passe précédente**. Le token ne figure pas dans le frontmatter DESIGN.md — les niveaux typographiques définis sont : `display` (32px), `headline` (24px), `title-lg` (18px), `title-md` (16px), `body-lg` (16px), `body-md` (14px), `label-lg` (14px), `label-sm` (12px). Un dev résolvant ce token obtiendra une référence non définie. (EXPERIENCE.md ligne 157 ; DESIGN.md frontmatter `typography`). *Fix :* Remplacer `{typography.display-sm}` par `{typography.headline}` (24px/700) ou `{typography.title-lg}` (18px/600) selon la taille visuelle souhaitée pour la tuile de métrique — et aligner DESIGN.md frontmatter `metric-tile.value-font` si nécessaire (actuellement `{typography.title-lg}`, cohérent avec `title-lg` comme correction).

---

## 3. Component coverage — Résolu / Aucune régression

**Medium résolu :** Les 6 composants manquants ont été ajoutés avec entrées comportementales dédiées :
- `segmented-control` (ligne 155) — mappe sur `MatButtonToggleGroup`, segment actif `{colors.primary}`, hauteur 40px, mutuellement exclusif.
- `banner` (ligne 156) — non fermable, fond `{colors.warning}`, texte `{colors.on-warning}`, icône `warning`.
- `metric-tile` (ligne 157) — lecture seule, sans hover, label `{typography.label-lg}` (note : voir finding §2 sur `display-sm`).
- `danger-zone` (ligne 158) — wrapper non standalone, toujours suivi d'un dialog de confirmation.
- `skeleton-row` (ligne 159) — 3 à 5 lignes, même hauteur que `list-row`.
- `list-row` (ligne 160) — cliquable → fiche détail, actions toujours visibles (pas de hover-only).

### Findings

Aucun nouveau finding dans cette section.

---

## 4. State coverage — Résolu / Aucune régression

**Low résolu :** État "Reversements — liste vide" ajouté (ligne 190) : icône centré + "Aucun vendeur enregistré pour cette édition." Aucune action. Cohérent avec les autres états vides.

**Low résolu :** Comportement `Enter` dans "Somme remise (€)" ajouté en Interaction Primitives (ligne 216) : déplace le focus vers "Confirmer" (champ optionnel dans contexte multi-champ — ne soumet pas).

### Findings

Aucun nouveau finding dans cette section.

---

## 5. Visual reference coverage — Résolu / Aucune régression

**Medium résolu :** Note blockquote ajoutée en tête de la section IA (lignes 36-38) : "Les maquettes référencées dans cette section sont des références de composition — EXPERIENCE.md prévaut en cas de conflit entre une maquette et cette spine." La règle "spine wins" est maintenant établie dès l'entrée dans la section IA, avant toute référence aux maquettes.

### Findings

Aucun nouveau finding dans cette section.

---

## 6. Bloat & overspecification — Résolu / Aucune régression

**High résolu :** RFC 7807, verrou optimiste et garantie de non-double-vente retirés. Le composant "Conflit de scan concurrent" (ligne 227) conserve uniquement : "Réponse HTTP 409 synchrone au scan → notification inline rouge, scanner reste actif." Conforme au niveau d'abstraction comportemental.

**High résolu :** `SerialPort.getCommPorts()` remplacé par "liste déroulante des ports série disponibles sur le serveur — affiche le nom descriptif de l'appareil Bluetooth appairé" (ligne 153). Neutre.

**Medium résolu :** "non persistée en base" retiré du composant "Sélection d'imprimante — login bénévole" (ligne 154). Correctement absent.

### Findings

Aucun nouveau finding dans cette section.

---

## 7. Inheritance discipline — Résolu / Aucune régression

**Low résolu :** Entrée datée "Reversements sidebar" ajoutée dans le decision log (ligne 34-35) : "*(Mise à jour 2026-06-11 : 'Reversements' ajouté dans la sidebar Admin, visible uniquement en phases Post-vente et Clôturée — masqué les autres phases.)*" Cohérent avec EXPERIENCE.md IA ligne 81.

### Findings

Aucun nouveau finding dans cette section.

---

## 8. Shape fit — Résolu / Aucune régression

**High résolu :** Section "Inspiration & Anti-patterns" ajoutée en EXPERIENCE.md (lignes 22-33), après Foundation et avant Information Architecture. Contenu repris du decision log : 3 inspirations positives (Stripe, Square POS, Notion) et 4 anti-patterns bannis (interfaces POS surchargées, couleur primaire pour statuts, wizards multi-étapes, modals pour actions non destructives). Positionnement conforme au modèle de référence (experience-example-shadcn.md).

Ordre DESIGN.md 8/8 présentes, inchangé. Sections EXPERIENCE.md 8/8 présentes, inchangées.

### Findings

Aucun nouveau finding dans cette section.

---

## Mechanical notes

```
RÉSOLUS (passe précédente)
  critical  {colors.error} → {colors.primary} sur label lot          ✓
  high      RFC 7807 / verrou optimiste / SerialPort retirés          ✓
  high      Section Inspiration & Anti-patterns ajoutée               ✓
  medium    Note "spine wins" en tête de IA                           ✓
  medium    Entrées comportementales segmented-control, banner,
            metric-tile, danger-zone, skeleton-row, list-row           ✓
  medium    "non persistée en base" retiré                            ✓
  low       {colors.warning} complété avec on-warning et composant    ✓
  low       État "Reversements liste vide" ajouté                     ✓
  low       Comportement Enter "Somme remise" ajouté                  ✓
  low       Entrée datée "Reversements sidebar" dans decision log      ✓

NOUVEAU FINDING (régression)
  medium    {typography.display-sm} dans metric-tile ne résout pas
            — token inexistant dans DESIGN.md frontmatter.
            Correction : remplacer par {typography.title-lg}
            (cohérent avec metric-tile.value-font dans frontmatter).

INCHANGÉS (findings low de passe précédente jugés non bloquants)
  low       Flow 2 sans failure path nommé — acceptable
```

---

## Résumé des findings actifs

| Sévérité | Nombre | Section | Description |
|---|---|---|---|
| critical | 0 | — | — |
| high | 0 | — | — |
| medium | 1 | §2 Token completeness | `{typography.display-sm}` inexistant dans metric-tile |
| low | 1 | §1 Flow coverage | Flow 2 sans failure path nommé (non bloquant) |

**Total findings actifs : 2** (0 critical · 0 high · 1 medium · 1 low).

Le seul correctif requis avant distribution finale : remplacer `{typography.display-sm}` par `{typography.title-lg}` dans EXPERIENCE.md ligne 157 (composant `metric-tile`).
