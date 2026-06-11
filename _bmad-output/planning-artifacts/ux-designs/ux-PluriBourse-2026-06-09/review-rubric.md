# Spine Pair Review — PluriBourse

## Overall verdict

The spine pair is well-structured and operationally complete for a first implementation wave: the DESIGN.md token system is nearly fully self-consistent, and EXPERIENCE.md covers states and flows at an unusually granular level. The primary risk is a single undefined `{colors.warning}` token that would block a developer implementing the locked-categories banner, combined with an orphaned mockup (`mock-admin-edition-create.html`) that covers three FRs with no reference in EXPERIENCE.md.

---

## 1. Flow coverage — strong

**What was checked:** Three declared sources in the frontmatter (prd.md, addendum.md, architecture.md). Six named Key Flows exist. Each flow has a named protagonist, numbered steps, a labeled climax beat, and at least one failure path where operationally relevant.

### Findings

- **medium** Flow 1 (Dépôt) has one failure path (imprimante hors ligne) but does not cover the failure path where the search returns no results and the bénévole must create a new seller profile mid-flow — this is a fork documented in the Component Patterns section but not narrated in the flow steps. (EXPERIENCE.md, Flow 1, step 2–3). *Fix:* Add a step 2b: "Si absent de la liste, elle clique 'Créer un profil', saisit prénom/nom/email, puis arrive au formulaire de saisie."

- **low** Flow 6 (Premier lancement) ends at step 5 with "l'instance est prête" but does not narrate the creation of the first Edition, which is a natural next action visible on `/admin/settings` redirect. Not critical — the flow covers the forced-password and settings FRs correctly. (EXPERIENCE.md, Flow 6, step 5). *Fix:* Add a note: "Étape suivante non couverte dans ce flow — voir Key Flows § Contrôle de phase."

- **low** No dedicated flow covers the Admin catalogue or articles management (browsing, editing article details, reprinting labels). These surfaces appear in the IA table but have no flow. (EXPERIENCE.md, IA table: `/admin/catalog`). *Fix:* Add an optional Flow 7 or note it as a future flow if scope is intentionally deferred.

---

## 2. Token completeness — adequate

**What was checked:** All YAML frontmatter tokens in DESIGN.md (colors, typography, elevation, rounded, spacing, components) and every `{path.to.token}` reference in both spines. Color tokens verified for hex values. Component token references cross-checked against defined tokens.

### Findings

- **critical** `{colors.warning}` is referenced in EXPERIENCE.md (Component Patterns, Fiche Catégories & Tables, line 124: `bannière {colors.warning}`) but is **not defined** anywhere in DESIGN.md. No hex value, no role alias. A developer implementing the locked-categories banner has no color to use. (EXPERIENCE.md line 124; DESIGN.md frontmatter). *Fix:* Add `warning: '#FFF4EE'` and `on-warning: '#8C2910'` to `colors:` in DESIGN.md frontmatter (matches the mockup's `--warning-bg`/`--warning-text` CSS variables and the existing `status-chip-warning` intent), or replace the reference with `{colors.primary-container}` / `{colors.on-primary-container}` which already covers the corail-doux warning role.

- **medium** `status-chip-success` hardcodes `#F0FDF4` and `#166534` directly in the YAML rather than referencing tokens (no `colors.success-container` or `colors.on-success-container` defined). This is inconsistent with the token-reference discipline used by all other chips. (DESIGN.md, lines 141–142). *Fix:* Either add `success-container: '#F0FDF4'` and `on-success-container: '#166534'` to `colors:`, or document the intentional exception ("green is a one-off semantic color, no token family needed") in the Brand & Style prose.

- **low** `button-primary.hover-background` is a hardcoded hex `#A83A1E` rather than a token reference. A semantic token `{colors.primary-hover}` or a note explaining the darkening rule would be preferable. (DESIGN.md, line 111). *Fix:* Add `primary-hover: '#A83A1E'` to `colors:` or add a prose note in the Colors section explaining the 15% darkening rule.

- **low** `sidebar-item.foreground` uses a hardcoded `rgba(245,238,234,0.65)` opacity variant of `{colors.on-surface}`. This value will silently diverge if the surface palette changes. (DESIGN.md, line 169). *Fix:* Document as `on-surface-muted: 'rgba(245,238,234,0.65)'` in `colors:` or add a prose note.

---

## 3. Component coverage — adequate

**What was checked:** Every component name appearing in either spine. DESIGN.md has a `components:` YAML block with 13 named tokens plus prose descriptions in the Components section. EXPERIENCE.md Component Patterns table has 22 named components. Cross-checked for asymmetries.

### Findings

- **high** **Topbar** is described in DESIGN.md prose (Layout & Spacing, line 234) and in EXPERIENCE.md Component Patterns, but has **no entry in the DESIGN.md `components:` YAML block**. No visual spec tokens for its background color, height (56px is in prose only), or shadow rule (absence is in Elevation prose). A developer reading only the YAML would have no topbar spec. (DESIGN.md Components section). *Fix:* Add a `topbar:` entry in the components YAML: `background: '{colors.surface}'`, `height: '56px'`, `border-bottom: 'none'`, `shadow: 'none'`.

- **high** **Scanner input** (EXPERIENCE.md Component Patterns) has no visual token entry in DESIGN.md. It inherits from `input:` but the autofocus ring, the result zone (`aria-live`), and the "article ajouté" feedback area have no visual spec. (EXPERIENCE.md line 119; DESIGN.md). *Fix:* Add a note in the `input:` component entry: "La variante scanner hérite de `input:` ; la zone de résultat utilise `{colors.primary-container}` pour l'article ajouté, `{colors.error-container}` pour le rejet."

- **medium** **Skeleton rows** (State Patterns: "Chargement initial") are referenced as an Angular Material pattern but have no visual token guidance — no color, no animation duration. A developer might choose any Material skeleton style. (EXPERIENCE.md State Patterns, line 145). *Fix:* Add a one-liner to DESIGN.md Components: `skeleton-row: { background: '{colors.surface-variant}', animation: 'pulse 1.5s ease-in-out infinite' }`.

- **medium** **Segmented control / sélecteur de type** (EXPERIENCE.md, Formulaire dépôt: "contrôle segmenté à deux segments") has no visual spec in DESIGN.md. Not in the YAML or prose. Angular Material has `mat-button-toggle-group` — the visual mapping to PluriBourse tokens is unspecified. (EXPERIENCE.md line 122). *Fix:* Add a `segmented-control:` entry referencing `{colors.primary-container}` for active segment and `{colors.surface-variant}` for inactive.

- **low** **Chip input** (EXPERIENCE.md, Fiche Catégories & Tables: chips de numéro de table) has no dedicated visual spec. It re-uses `status-chip-*` visually but the interaction spec (type + Enter) is only behavioral. (EXPERIENCE.md line 124). *Fix:* Note in the `status-chip-warning` or in a new `chip-input:` entry that table chips use `{colors.primary-container}` background with `×` button.

---

## 4. State coverage — strong

**What was checked:** All IA surfaces from both IA tables (Admin: 12 surfaces, Bénévole: 5 phase-scoped surfaces, Partagées: 3). Expected states: empty, loading/cold-start, error, offline, permission-denied/role-restricted, phase-restricted, conflict. Cross-checked against State Patterns table (16 entries) and Component Patterns.

### Findings

- **medium** `/admin/reports` has no explicit error-of-loading state. The generic "Erreur de chargement" pattern applies (State Patterns, line 148) but Reports has conditional phase-gated content — if a phase change occurs while the page is open, there is no specified state for "report content no longer applicable to current phase." (EXPERIENCE.md, Page Rapports component; State Patterns). *Fix:* Add a state entry: "Phase change while Reports is open — refresh banner or conditional re-render per current phase."

- **medium** `/admin/users` (Users management surface) has no component entry in Component Patterns and no state coverage. It appears in the IA table but is entirely undocumented behaviourally. This is a significant gap for the developer implementing that surface. (EXPERIENCE.md IA table line 36; Component Patterns table). *Fix:* Add a minimal component entry for Page Utilisateurs: list of users, invite/create action, role assignment, password reset trigger.

- **medium** `/admin/editions` (Editions list) has no component or state entry. A developer implementing the list view has no spec for the empty state (no editions yet — first use), no column spec, and no create button placement. (EXPERIENCE.md IA table line 27). *Fix:* Add a component entry for "Liste des éditions" with empty state: "Aucune édition. Créez votre première édition." and a primary button.

- **low** `/login` has no state coverage — no failed authentication state, no "account locked" state. These are security-relevant UI states. (EXPERIENCE.md, Partagées IA table). *Fix:* Add a state entry for login failure: inline error "Identifiants incorrects. Vérifiez votre saisie." (do not specify which field is wrong — security).

- **low** `/admin/print-queue` appears in the IA table but has no component or state entry. (EXPERIENCE.md IA table line 35). *Fix:* Add a minimal spec or explicitly note: "Hors scope v1 spine — développé selon les patterns Toast + Notification inline existants."

---

## 5. Visual reference coverage — adequate

**What was checked:** All files referenced via `→` links in EXPERIENCE.md. Verified existence on disk. Checked inline placement (all references are in the IA section header block). Flagged `mock-admin-edition-create.html` orphan.

### Files referenced in EXPERIENCE.md — all confirmed present on disk:

| Referenced path | Exists | Section linked from |
|---|---|---|
| `.working/navigation-layouts.html` | Yes | IA / Navigation Admin |
| `mockups/mock-pos-caisse.html` | Yes | IA block |
| `mockups/mock-pos-caisse-lot-complet.html` | Yes | IA block |
| `mockups/mock-deposit.html` | Yes | IA block + Component Patterns |
| `mockups/mock-admin-categories.html` | Yes | IA block + Component Patterns |
| `mockups/mock-phase-control.html` | Yes | IA block |
| `mockups/mock-pos-paiement.html` | Yes | IA block + Component Patterns |
| `mockups/mock-admin-vendors.html` | Yes | IA block |
| `mockups/mock-admin-settlement.html` | Yes | Component Patterns |
| `mockups/mock-volunteer-settlement.html` | Yes | Component Patterns |

### Working files present but NOT referenced in EXPERIENCE.md:

`.working/color-directions.html`, `.working/color-directions-2.html`, `.working/color-directions-3.html`, `.working/typography-directions.html`, `.working/shape-shadow-icons.html` — these are direction-exploration artifacts, not spec references. Their absence from the spine is correct.

`.working/mock-deposit.html`, `.working/mock-admin-vendors.html`, `.working/mock-phase-control.html`, `.working/mock-pos-caisse.html`, `.working/mock-pos-caisse-lot-complet.html` — working copies, superseded by `mockups/` finals. Correct to not reference.

### Findings

- **high** `mockups/mock-admin-edition-create.html` exists on disk and its internal comment declares it governs "Component Patterns § Formulaire édition, State Patterns § Copier depuis édition (FR-008, FR-017, FR-018, FR-080)". It is **not referenced anywhere in EXPERIENCE.md**. The Edition creation/detail surface (`/admin/editions/:id`) is listed in the IA table but has no Component Patterns entry and no mockup link. A developer implementing the edition creation form has no behavioral spec and no mockup pointer. (EXPERIENCE.md IA table line 28; `mockups/mock-admin-edition-create.html`). *Fix:* Add a component entry "Formulaire édition — création / détail" in Component Patterns, and add a reference link `→ Maquette : mockups/mock-admin-edition-create.html` in the IA section alongside the other mockup links.

- **medium** All 10 mockup references are clustered in the IA section header block rather than placed inline at the relevant Component Patterns entry. This means a developer reading the "Fiche Catégories & Tables" component entry must scroll back to the IA block to find the mockup link, rather than having it co-located. Only `mock-deposit.html`, `mock-admin-categories.html`, `mock-pos-paiement.html`, `mock-admin-settlement.html`, `mock-volunteer-settlement.html` have their reference repeated inline in the Component Patterns row — the others (POS caisse, admin-vendors, phase-control) do not. *Fix:* Ensure all component entries that have a corresponding mockup include the `→ Maquette :` reference inline in the Component Patterns row, not only in the IA block.

---

## 6. Bloat & overspecification — strong

**What was checked:** Source restatement (verbatim FR/persona copying), pixel specs where tokens cover it, prose where tables work better, decorative narrative untied to a decision.

### Findings

- **low** DESIGN.md Brand & Style section contains a paragraph about PluriBourse being "multi-association" and adapting to any association without carrying a strong brand identity. This is a product-positioning rationale that belongs in the PRD or decision log, not the design spine. It has no downstream impact on implementation decisions. (DESIGN.md lines 194–195). *Fix:* Move to `.decision-log.md` or trim to one sentence.

- **low** EXPERIENCE.md Component Patterns describes `*ngIf="isAdmin"` (a specific Angular directive) inline in the Page Reversements spec (line 129). Implementation details belong in the architecture doc or story acceptance criteria, not the experience spine. *Fix:* Replace with "colonnes conditionnelles au rôle" — the behavioral intent — and let the story AC specify the Angular mechanism.

---

## 7. Inheritance discipline — strong

**What was checked:** Sources frontmatter paths (verified structure matches known repo layout), glossary/phase label consistency, component names across both files, token cross-references.

### Findings

- **medium** The `sources:` frontmatter in EXPERIENCE.md lists `_bmad-output/planning-artifacts/architecture.md` as a flat path. This is a relative path from the project root — correct if the toolchain resolves from root — but inconsistent with the other two sources which are deeper paths. If any toolchain extracts sources relative to the spine's own directory, this path would not resolve. (EXPERIENCE.md line 8). *Fix:* Verify the resolution rule and either confirm the path is intentional or make it consistent: `_bmad-output/planning-artifacts/architecture/architecture.md` (if the architecture doc is in a subdirectory).

- **medium** Phase label "Post-vente" appears in DESIGN.md (line 271: phase chip labels) and is used consistently in EXPERIENCE.md flows and IA. However, the decision log (line 7) uses "Post-Vente" (capital V). Minor casing inconsistency. *Fix:* Standardize to "Post-vente" (lowercase v) everywhere, including `.decision-log.md`.

- **low** DESIGN.md Component Patterns prose names the component "Notification d'erreur inline" (line 285) but EXPERIENCE.md Component Patterns names it "Notification inline" (line 116). Same component, different names. *Fix:* Align to one canonical name. Suggest "Notification inline" (shorter, covers warning and error cases both per the spec).

- **low** The `.decision-log.md` sidebar section (line 33) lists "Vendeurs · Articles · Éditions · Rapports · Utilisateurs · Paramètres" as sidebar items — notably missing "Reversements" which is present in EXPERIENCE.md IA sidebar spec (line 63: `Reversements (icône: payments)`). The decision log predates the Reversements sidebar addition. (`.decision-log.md` line 33; EXPERIENCE.md line 63). *Fix:* Update `.decision-log.md` sidebar entry to include Reversements, or add a dated addendum entry.

---

## 8. Shape fit — strong

**What was checked:** DESIGN.md section order against canonical sequence. EXPERIENCE.md required sections present. Required-when-applicable sections (Responsive, Inspiration).

### DESIGN.md canonical order — verified:

| Expected | Actual | Status |
|---|---|---|
| Brand & Style | Brand & Style | ✓ |
| Colors | Colors | ✓ |
| Typography | Typography | ✓ |
| Layout & Spacing | Layout & Spacing | ✓ |
| Elevation & Depth | Elevation & Depth | ✓ |
| Shapes | Shapes | ✓ |
| Components | Components | ✓ |
| Do's and Don'ts | Do's and Don'ts | ✓ |

All 8 sections present in canonical order. No extra sections.

### EXPERIENCE.md required sections — verified:

| Required section | Present | Notes |
|---|---|---|
| Foundation | ✓ | |
| Information Architecture | ✓ | |
| Voice and Tone | ✓ | |
| Component Patterns | ✓ | |
| State Patterns | ✓ | |
| Interaction Primitives | ✓ | |
| Accessibility Floor | ✓ | |
| Key Flows | ✓ | |

### Required-when-applicable:

- **Responsive** — absent, defensible. EXPERIENCE.md Foundation explicitly declares "desktop uniquement (aucun breakpoint mobile en v1)." Absence is intentional and documented.
- **Inspiration** — no dedicated section, but `.decision-log.md` records rejected directions (6 color families rejected with rationale, navigation Option 3 selected over others). This partially satisfies the inspiration/rejects record. The absence of external reference products (competitor apps, Material reference apps) is a minor gap.

### Findings

- **low** No "Inspiration" section exists in either spine, and `.decision-log.md` does not name any reference products. While the decisions are recorded, the absence of reference UIs means future contributors have no benchmark for "what good looks like" for this domain. (DESIGN.md; EXPERIENCE.md; `.decision-log.md`). *Fix:* Add one entry in `.decision-log.md`: reference products considered (e.g., other event management or POS web apps) or explicitly state "aucun référentiel de produit consulté — direction issue du brain dump utilisateur seul."

---

## Mechanical notes

1. **Undefined token** `{colors.warning}` — EXPERIENCE.md line 124 (Fiche Catégories & Tables). No definition in DESIGN.md frontmatter. This will cause a silent rendering failure or require a developer decision. Priority: critical.

2. **Orphan mockup** `mockups/mock-admin-edition-create.html` — file governs FR-008, FR-017, FR-018, FR-080 per its own internal comment, but no EXPERIENCE.md Component Patterns entry exists for the Edition creation/detail surface. The IA table lists the route but no behavioral spec is provided. Priority: high.

3. **Component name mismatch** — "Notification d'erreur inline" (DESIGN.md line 285) vs "Notification inline" (EXPERIENCE.md line 116). Single component, two names.

4. **Phase label casing** — "Post-vente" (spines) vs "Post-Vente" (decision log line 7). Minor but will cause grep/search mismatches in story tooling.

5. **Sources frontmatter path** — `_bmad-output/planning-artifacts/architecture.md` may not resolve depending on toolchain root assumption. Verify.

6. **Missing IA surfaces in Component Patterns** — `/admin/users`, `/admin/editions` (list), `/admin/print-queue`, `/login` have no behavioral spec. These are out-of-scope for the current iteration but should be explicitly tagged as deferred rather than silently absent.
