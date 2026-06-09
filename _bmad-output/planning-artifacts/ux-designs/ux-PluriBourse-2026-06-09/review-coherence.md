# Coherence Review — PluriBourse UX (DESIGN↔EXPERIENCE)
Date: 2026-06-09
Reviewer: Coherence Lens

## Summary

The two documents are well-aligned in intent and share consistent terminology across most areas. However, three categories of real defects need fixing before implementation begins: two dangling token references (`{colors.sidebar-bg}` and `{elevation.*}`) that will break any token-driven theming pipeline, one direct contradiction on infinite scroll vs. pagination policy, and one ambiguous autofocus statement in Flow 1 that conflicts with the Component Patterns table. All other mismatches are minor wording or coverage gaps.

---

## Findings

### PASS — Typography scale

Both documents name and apply the same scale levels consistently: `title-lg`, `body-md`, `label-lg`, `label-sm`. The rules for prices (`title-md` / `{colors.primary}`) appear in DESIGN.md and are correctly reflected in the component behavioral descriptions in EXPERIENCE.md.

### PASS — Phase chip behavior and labels

Phase labels match exactly in both documents: "Dépôt" · "Vente" · "Post-vente" · "Clôturée". The clickability rule (Admin only → phase control panel) is consistent. The SSE `phase-changed` event and the fade 150ms transition are stated only in EXPERIENCE.md (as behavioral detail), which is correct by document purpose.

### PASS — Dialog de confirmation spec

Both documents describe the same structure: title + consequences + confirm button + cancel/ghost button. EXPERIENCE.md adds the correct behavioral extras (focus trap, Esc closes, focus initial on cancel). No contradiction.

### PASS — Toast behavior

Both agree: bottom-right, 4s for success, persistent for system errors (printer). EXPERIENCE.md adds "Max 1 toast simultané" and "bouton Fermer" on persistent — both are additive behavioral detail, not contradictions.

### PASS — Role badge variants

DESIGN.md defines two variants (Admin: primary-container / on-primary-container; Bénévole: surface-variant / on-surface-variant). EXPERIENCE.md Component Patterns table references the Topbar which includes "Role badge + icône profil" — consistent, though EXPERIENCE.md does not re-specify badge colors (correct, as DESIGN.md owns colors).

### PASS — Component naming consistency

The following names match exactly between documents: Phase chip, Role badge, Status chips (success/warning/error), Dialog de confirmation, Notification inline, Toast, Sidebar item, Boutons (primary / secondary / ghost).

### PASS — Sidebar width

DESIGN.md Layout: "Sidebar Admin : largeur fixe 200px." EXPERIENCE.md Component Patterns: "Largeur fixe 200px." Exact match.

### PASS — Topbar height

DESIGN.md: "Topbar : hauteur fixe 56px." EXPERIENCE.md does not re-state the height (correct, visual spec belongs to DESIGN.md). No contradiction.

### PASS — Destructive button color rule

DESIGN.md: "actions destructives utilisent le style `secondary` avec la couleur `error` — jamais un bouton primaire corail." EXPERIENCE.md Flow 3 correctly uses "Confirmer (primary)" for a non-destructive financial confirmation, and DESIGN.md Components Dialog section mentions "action confirmée = primary ou error" — consistent.

### PASS — Accessibility floor token reference

EXPERIENCE.md Accessibility section references `{colors.primary}` for focus ring — this token exists in DESIGN.md YAML.

---

### CONCERN — Component coverage asymmetry: EXPERIENCE.md has no DESIGN counterpart for several components

The following components appear in EXPERIENCE.md `## Component Patterns` but have no dedicated entry in DESIGN.md `## Components`:

- **Topbar** — DESIGN.md describes topbar contents inline in the Layout section, but no `topbar` key exists in the YAML `components` block. There is no spec for topbar background, border, or shadow token.
- **Catalogue / liste filtrée** — Behavioral spec in EXPERIENCE.md; no visual spec (header style, filter input appearance, sort arrow style) in DESIGN.md.
- **Scanner input** — Behavioral spec in EXPERIENCE.md; no visual spec in DESIGN.md (distinct from the generic `input` component: autofocus ring, scanner-active state styling).
- **Panier POS** — Behavioral spec in EXPERIENCE.md; no visual spec in DESIGN.md (panel layout, total row, Valider button placement).
- **Lot dans le panier** — Behavioral spec in EXPERIENCE.md; no visual spec.
- **Formulaire dépôt** — Behavioral spec in EXPERIENCE.md; no visual spec.
- **Fiche Catégories & Tables** — Behavioral spec in EXPERIENCE.md; no visual spec.

Conversely, DESIGN.md defines `list-row` (YAML component) but EXPERIENCE.md has no dedicated behavioral entry for it — acceptable since list-row is used implicitly by Catalogue and Vendeurs list.

**Severity:** Medium. Implementation can proceed, but developers must make visual decisions for these components without a canonical token-backed spec, risking visual inconsistency.

### CONCERN — Sidebar background color not tokenized

DESIGN.md Layout prose: "Fond sombre `{colors.sidebar-bg}` (#2A100A)."

The token `{colors.sidebar-bg}` does **not** exist in the DESIGN.md YAML `colors` block. The literal value `#2A100A` appears nowhere in the YAML either. The closest existing token is `surface-variant-dark: '#2A1510'` (dark theme), which is a different value.

EXPERIENCE.md does not reference `{colors.sidebar-bg}` directly (the Sidebar entry only states width and behavior), so there is no dangling EXPERIENCE.md reference — but the DESIGN.md prose itself cites a token it does not define.

**Severity:** Medium-High. Any theming system consuming the YAML will fail to resolve this reference.

### CONCERN — Wording mismatch: "Notification d'erreur inline" vs "Notification inline"

DESIGN.md `## Components` section heading: **"Notification d'erreur inline"**
EXPERIENCE.md Component Patterns table entry: **"Notification inline"**

The DESIGN.md description restricts this component to "erreurs métier (article déjà vendu, lot incomplet)." EXPERIENCE.md broadens the usage label to just "Notification inline" with usage "Erreurs métier dans le flux (POS, dépôt)" — which is the same scope but the name differs by one word.

**Severity:** Low. No functional impact, but the Angular component filename and selector should pick one canonical name to avoid divergence.

### CONCERN — Status chip color in EXPERIENCE.md State Patterns: "rouge" vs "orange"

EXPERIENCE.md `## State Patterns`:
- "Conflit POS (article déjà vendu)" → "Notification inline **rouge** sous le scanner"
- "Lot incomplet" → "Notification inline **orange** dans le panier"

DESIGN.md defines:
- `status-chip-warning`: uses `{colors.primary-container}` / `{colors.on-primary-container}` (corail doux) — this maps to orange-ish
- `status-chip-error`: uses `{colors.error-container}` / `{colors.on-error-container}` (rouge)

EXPERIENCE.md Flow 2 also uses "Notification inline **orange**" for lot incomplet, and DESIGN.md Component Patterns table describes lot incomplet as orange inline notification. The status chip warning token is corail/orange — this is consistent.

However EXPERIENCE.md State Patterns says POS conflict is "rouge" (inline notification) while the component described in DESIGN.md for inline errors uses `{colors.primary-container}` (corail/orange). A "red" inline notification would require the `status-chip-error` styling, but the Notification d'erreur inline component only uses primary-container styling. There is a potential gap: no visual spec exists for a **red** inline notification, only an orange one.

**Severity:** Medium. The implementation team needs a clarification: does a POS article conflict trigger a red inline notification (error style) or an orange one (warning style)?

---

### FAIL — Dangling token reference: `{elevation.level-2}` and `{elevation.level-3}` in DESIGN.md YAML

DESIGN.md YAML `components` block references:
- `card.shadow: '{elevation.level-2}'` (line 156)
- `dialog.shadow: '{elevation.level-3}'` (line 180)

There is no `elevation` key anywhere in the DESIGN.md YAML frontmatter. The elevation values are described only in prose (DESIGN.md `## Elevation & Depth` section) but never defined as YAML tokens.

EXPERIENCE.md does not directly reference `{elevation.*}` tokens, but it inherits these broken references indirectly through the `card` and `dialog` component tokens it relies on.

**File reference:** DESIGN.md lines 156, 180.

**Severity:** High / Blocker for token-driven theming. Any system that resolves `{elevation.level-2}` from the YAML will fail.

### FAIL — Direct contradiction: infinite scroll policy

EXPERIENCE.md `## Component Patterns`, Catalogue/liste filtrée entry:
> "Pas de pagination en v1 — **scroll infini si nécessaire** (volume modeste ~1 700 articles)."

EXPERIENCE.md `## Interaction Primitives`, Interdit section:
> "**Infinite scroll** (utiliser pagination ou chargement complet pour les volumes PluriBourse)"

These two statements directly contradict each other within the same document. One mandates infinite scroll for catalogues; the other explicitly forbids it.

**File reference:** EXPERIENCE.md lines 104 and 153.

**Severity:** High / Blocker for POS catalog and article list implementation. The team cannot implement both.

### FAIL — Autofocus inconsistency: scanner input in deposit flow (Flow 1)

EXPERIENCE.md `## Component Patterns`, Scanner input entry:
> "Champ auto-focused à l'ouverture de la **caisse** (POS)."

EXPERIENCE.md `## Key Flows`, Flow 1 (Dépôt):
> "Sophie arrive sur `/volunteer/deposit`. **Scanner input autofocused.**"

The deposit surface (`/volunteer/deposit`) is a deposit form, not the POS (caisse). The Component Patterns table scopes scanner input exclusively to the POS. If `/volunteer/deposit` also has an autofocused scanner, it must be specified as a second usage context of scanner input. If the autofocus in Flow 1 refers to the vendor search field instead of a barcode scanner, the flow text is misleading.

**File reference:** EXPERIENCE.md lines 105 and 175.

**Severity:** High. Deposit and POS are fundamentally different surfaces; conflating scanner behavior will result in implementation errors.

---

## Recommendations

Ordered by priority. Blockers first.

1. **(Blocker) Fix infinite scroll contradiction** — Choose one policy for all list surfaces and update EXPERIENCE.md to be consistent. Recommended: load all records (complete load) for the ~1 700 article volume, no pagination and no infinite scroll. Remove the "scroll infini si nécessaire" clause from Component Patterns and the "Infinite scroll" prohibition from the Interdit list (replace with the chosen positive rule).

2. **(Blocker) Define `elevation` tokens in DESIGN.md YAML** — Add an `elevation` block to the YAML frontmatter with keys `level-1`, `level-2`, `level-3` matching the shadow values in the `## Elevation & Depth` prose section. Example:
   ```yaml
   elevation:
     level-1: '0 1px 4px rgba(28,10,5,.08)'
     level-2: '0 4px 16px rgba(28,10,5,.14), 0 1px 4px rgba(28,10,5,.08)'
     level-3: '0 8px 24px rgba(28,10,5,.18), 0 2px 6px rgba(28,10,5,.10)'
   ```

3. **(Blocker) Define `colors.sidebar-bg` token in DESIGN.md YAML** — Add `sidebar-bg: '#2A100A'` to the `colors` block, or replace the prose reference with an existing token (`surface-variant-dark` is close but not identical — verify intent before aliasing).

4. **(Blocker) Clarify autofocus in Flow 1 (Deposit)** — Either: (a) extend the Scanner input component spec to cover `/volunteer/deposit` with its own autofocus behavior and scope, or (b) correct Flow 1 to say "Champ de recherche vendeur autofocused" (not scanner input). This distinction is critical for the Angular component architecture.

5. **(Medium) Clarify inline notification color for POS article conflict** — Decide whether article-already-sold conflict uses error (red) or warning (orange) inline notification styling, and add a visual spec to DESIGN.md `## Components` if a red variant of the inline notification is needed.

6. **(Medium) Add visual specs to DESIGN.md for POS-specific components** — Topbar, Panier POS, Lot dans le panier, Scanner input, and Formulaire dépôt should each have at least a minimal YAML entry in `components` (background, padding, key color references) so developers have a token-anchored reference and don't improvise.

7. **(Low) Align component name** — Standardize on either "Notification d'erreur inline" (DESIGN.md) or "Notification inline" (EXPERIENCE.md) across both documents and in Angular component naming (`notification-inline.component.ts` or `error-notification-inline.component.ts`).
