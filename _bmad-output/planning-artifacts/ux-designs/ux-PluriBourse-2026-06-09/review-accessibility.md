# Accessibility Review — PluriBourse UX
Date: 2026-06-09
Reviewer: Accessibility Lens

---

## Summary

The spines demonstrate genuine accessibility intent: focus trapping in dialogs, `aria-live` on the scanner result zone, hover-only affordance ban, and a 44 × 44 px minimum target size all reflect deliberate WCAG thinking. However, three real failures exist — the input border and list-row hover change both fall well below the 3:1 UI-component threshold (SC 1.4.11), `on-surface-variant` on `surface-variant` fails the 4.5:1 normal-text threshold (SC 1.4.3), and the dark-mode outline border is unusably low contrast — and several significant gaps remain unaddressed, including focus restoration after dialog close, the scanner refocus loop as a potential keyboard trap, `aria-live` coverage for toasts and the phase chip SSE update, and WCAG 2.2-specific target-size and focus-appearance requirements.

---

## Findings

### PASS — Color contrast: primary on white (SC 1.4.3, 1.4.11)

`#C44626` on white computes to **4.94:1** (EXPERIENCE.md states 4.6:1, which is slightly understated but the actual ratio passes). This covers normal text (button labels, active sidebar text) and UI component boundaries where primary is used as a border/indicator. Passes AA.

### PASS — Color contrast: dark-mode primary on dark surface (SC 1.4.3)

`#F07040` on `#1A0C06` computes to **6.46:1**. EXPERIENCE.md states 5.2:1 — again understated, but the real ratio gives a comfortable AA pass. This covers dark-mode body text and active elements.

### PASS — Color contrast: primary-container token pairings (SC 1.4.3)

`on-primary-container` (`#8C2910`) on `primary-container` (`#FFF4EE`) = **7.96:1**. Covers phase chip, warning status chip, secondary button labels, and admin role badge. All pass at the normal-text threshold, which matters because `label-sm` (12 px/600) does not qualify as WCAG "large text" (which requires 18 pt/24 px normal or 14 pt/18.67 px bold — 12 px bold is 9 pt).

### PASS — Color contrast: success and error status chips (SC 1.4.3)

Success chip: `#166534` on `#F0FDF4` = **6.81:1**. Error chip: `#410002` on `#FFDAD6` = **13.26:1**. Both well above the 4.5:1 threshold for the 12 px bold label-sm text used.

### PASS — Color contrast: sidebar inactive text (SC 1.4.3)

The semi-transparent `rgba(245, 238, 234, 0.65)` composited over `#2A100A` produces approximately `rgb(173, 160, 155)`, giving **7.07:1** against the sidebar background. Passes AA.

### PASS — Color contrast: body text and on-surface tokens (SC 1.4.3)

`on-surface` (`#1A0A05`) on `surface` (`#FFFBF9`) = **18.74:1**. `on-surface-dark` (`#F5EAE4`) on `surface-dark` (`#1A0C06`) = **16.17:1**. Both are excellent.

### PASS — Focus trap in dialogs (SC 2.1.2)

EXPERIENCE.md explicitly specifies: "Focus piégé dans le dialog (accessibilité). Focus initial sur le bouton d'annulation (action sûre)." Placing initial focus on the safe/cancel action is best practice and correct.

### PASS — Keyboard activation primitives (SC 2.1.1)

Tab/Shift+Tab, Enter/Space for buttons and links, Escape for dialogs and popovers are all specified. The explicit ban on hover-only affordances is correct and removes a whole class of keyboard-inaccessibility failures.

### PASS — Aria labeling for interactive elements (SC 4.1.2)

Phase chip: `aria-label="Phase actuelle : Dépôt"` avoids the pitfall of exposing only the pill text to screen readers. Scanner input: `aria-label="Scanner ou saisir un code-barres"` is correct. Decorative icons: `aria-hidden="true"` is specified.

### PASS — Icon role clarity (SC 1.1.1)

Decorative icons carry `aria-hidden="true"`; meaningful icons must be accompanied by a visible text label or `aria-label`. This rule is stated, though enforcement in implementation will require code review.

### PASS — Empty-state design (SC 3.3.2 spirit)

Every empty state must offer an action — this prevents dead-ends and is consistent with providing guidance when an expected list is absent, which aids users relying on clear prompts.

---

### CONCERN — Focus restoration after dialog close is unspecified (SC 2.4.3)

EXPERIENCE.md specifies focus trap on dialog open and initial focus on the cancel button, but says nothing about where focus returns when the dialog closes (whether by Escape, "Annuler", or "Confirmer"). WCAG SC 2.4.3 (Focus Order) requires that focus is restored to a meaningful, predictable location — almost always the element that triggered the dialog. Without this being specified, implementations risk returning focus to the top of the page or to an undefined location, which disorients screen reader and keyboard users.

**Required addition:** "On dialog close (any trigger), return focus to the element that opened the dialog."

### CONCERN — Scanner refocus loop may constitute a keyboard trap (SC 2.1.2)

EXPERIENCE.md specifies that the POS scanner input "remet le focus automatiquement après 500ms d'inactivité clavier." SC 2.1.2 prohibits keyboard traps: users must be able to move focus away from any component using standard keys. An aggressive auto-refocus to the scanner input could prevent a keyboard-only user from reaching the basket line items, the "Retirer le lot entier" button, or the "Valider" button without a mouse click.

The 500 ms inactivity threshold may be insufficient for users who navigate slowly. A better approach: refocus the scanner only when the user presses a designated key (e.g., F2, or a visible "Retour au scanner" button), or suppress auto-refocus while keyboard navigation is in progress (detected by `keydown` events that are not scanner input).

**WCAG SC reference:** 2.1.2 No Keyboard Trap.

### CONCERN — `aria-live` coverage is incomplete for toasts and SSE updates (SC 4.1.3)

The scanner result area carries `aria-live="polite"` which is correct. However, the following dynamic updates have no specified live-region announcement:

1. **Toast messages** — success confirmations ("Dépôt enregistré.", "Vendeur réglé.") and persistent error toasts ("L'imprimante ne répond pas.") are positioned bottom-right and appear dynamically. Without an `aria-live` region, screen reader users receive no announcement.
2. **Phase chip SSE update** — when the phase changes via server-sent event, the chip text updates (with a 150 ms fade). The chip has an `aria-label` but there is no specification that an `aria-live` region announces the change. Screen reader users currently in the middle of a workflow will not hear that the phase changed.
3. **Basket POS SSE cancellation** — the "La phase a changé. Votre panier a été annulé." toast is persistent, but the basket content change (items cleared) also needs an announcement independent of the toast.

**WCAG SC reference:** 4.1.3 Status Messages (Level AA).

### CONCERN — Tab order anomaly: sidebar → content → topbar (SC 2.4.3)

EXPERIENCE.md states tab order as "Sidebar → contenu principal → topbar actions (ordre DOM correspondant)." Placing the topbar last in DOM order (and thus last in tab order) is unusual: the topbar is visually at the top, and users expect `Tab` from the last interactive element of the topbar to enter the sidebar or the main content — not to require tabbing through the entire page first. If the topbar is truly last in DOM, a keyboard user starting from the phase chip will need to tab through potentially dozens of sidebar and content elements to reach the profile icon.

**Required:** Verify that the topbar landmark is reachable early in the focus sequence — either via a skip link or by placing it first in DOM order. Consider a "Skip to main content" link as first focusable element (see SC 2.4.1 below).

**WCAG SC reference:** 2.4.3 Focus Order.

### CONCERN — No skip navigation link specified (SC 2.4.1)

The Admin layout has a persistent 200 px sidebar with six navigation links. Without a "Skip to main content" link as the first focusable element, keyboard and screen reader users must tab through all sidebar items on every page load or navigation event. WCAG SC 2.4.1 (Bypass Blocks, Level A) requires a mechanism to skip repeated navigation.

**Required addition:** A visually hidden skip link `<a href="#main-content">Skip to main content</a>` as the first element in the DOM, visible on focus.

### CONCERN — Page title updates on navigation are underdefined (SC 2.4.2)

EXPERIENCE.md states "`<title>` mis à jour" at each navigation, which is correct in principle. However, the spec does not define the title pattern (e.g., "Vendeurs — PluriBourse" vs "PluriBourse — Vendeurs"), nor whether Angular's router integration is handled via a title strategy. Without an Angular `TitleStrategy` implementation, `<title>` will not update on SPA navigation. This needs to be a concrete implementation requirement, not just a statement.

**WCAG SC reference:** 2.4.2 Page Titled.

### CONCERN — List count announced via `aria-label` is fragile (SC 1.3.1)

The spec states "Listes annoncées avec leur nombre d'éléments via `aria-label`." Using `aria-label` on a `<ul>` or `<table>` to convey count is acceptable, but if the list is live (count changes with filters), the `aria-label` must also be updated dynamically. With scroll-based loading ("scroll infini si nécessaire"), the count in the `aria-label` becomes stale as items load. No mechanism for updating it is specified.

**WCAG SC reference:** 1.3.1 Info and Relationships.

### CONCERN — Sidebar item target size at small padding (SC 2.5.8 / WCAG 2.2)

DESIGN.md specifies sidebar items with `padding: '8px 12px'` and `font: body-md (14px/400)`. A single line of 14 px text with 8 px vertical padding gives an effective height of approximately 14 × 1.5 (line height) + 16 px = 37 px. This is below the stated 44 × 44 px minimum target size. While WCAG 2.2 SC 2.5.8 (Minimum Target Size, AA) allows for exceptions when spacing compensates, the spec does not specify whether sidebar items have sufficient spacing between them to satisfy the offset exception. This needs explicit clarification.

### CONCERN — Icon-only buttons in POS basket lack accessible names (SC 4.1.2)

The POS basket specifies "Suppression individuelle par icône `close` sur chaque ligne." An icon-only button with no visible label requires an `aria-label` to be keyboard/screen-reader accessible. The spec does not state what the accessible name should be (e.g., `aria-label="Retirer [article name] du panier"`). Without the article name in the label, all close buttons would be announced identically ("Fermer" × N), making it impossible to distinguish them without visual context.

**WCAG SC reference:** 4.1.2 Name, Role, Value.

### CONCERN — Error announcement mechanism for form fields is incomplete (SC 3.3.1)

"Erreurs de formulaire annoncées via `aria-describedby`" is the right technique, but `aria-describedby` links are static — they connect a field to an element that already exists. Inline validation on `blur` means error elements are injected dynamically. The spec does not address whether the error container element pre-exists in the DOM (hidden until needed) or is injected. If injected, `aria-describedby` will not work unless the reference is added to the input at the same time the error element appears. Angular Material's form field handles this correctly if used as intended, but the spec should note this constraint explicitly.

**WCAG SC reference:** 3.3.1 Error Identification.

---

### FAIL — Input border contrast against surface is critically low (SC 1.4.11)

DESIGN.md specifies input border as `1.5px solid {colors.outline}` = `#EDE0D8` on surface `#FFFBF9`. Computed contrast: **1.26:1**. WCAG SC 1.4.11 (Non-text Contrast, Level AA) requires **3:1** for the visual indicator of UI components, including input field borders.

This is a real failure. The border is nearly invisible against the beige-white surface. At this ratio, users with low vision, cataracts, or in high-ambient-light environments will struggle to identify input fields.

**Fix:** Darken the outline token. `#78716C` (`on-surface-variant`) on `#FFFBF9` yields 4.18:1, which passes as a UI component boundary. Alternatively, use a background-fill approach (slightly offset surface-variant fill) to differentiate inputs from the page surface without relying on a thin border — Angular Material's filled input variant does this.

### FAIL — `on-surface-variant` on `surface-variant` fails normal-text threshold (SC 1.4.3)

Multiple components use `on-surface-variant` (`#78716C`) as text on `surface-variant` (`#F5EEEA`) backgrounds:

- **List rows** (`list-row` component: text on `surface-variant` background)
- **Role badge (Bénévole)**: `on-surface-variant` on `surface-variant`
- **Ghost button**: `on-surface-variant` on transparent (≈ surface `#FFFBF9`) = 4.66:1 — this one passes

Computed ratio for `#78716C` on `#F5EEEA`: **4.18:1**. This fails the 4.5:1 threshold for SC 1.4.3 (Contrast Minimum, Level AA) for normal-sized text. The role badge uses `label-sm` (12 px/600 = 9 pt bold), which is not large text, so the lower 3:1 large-text threshold does not apply.

**Fix:** Darken `on-surface-variant` to approximately `#6B6461` or slightly increase contrast. Alternatively, use `on-surface` (`#1A0A05`) for text within `surface-variant` list rows if visual hierarchy allows.

### FAIL — Dark-mode input/outline border contrast is critically low (SC 1.4.11)

DESIGN.md defines `outline-dark: '#5C3828'` on `surface-dark: '#1A0C06'`. Computed contrast: **1.87:1**. WCAG SC 1.4.11 requires 3:1 for UI component boundaries. This is a dark-mode parallel of the light-mode input border failure.

**Fix:** Lighten the dark-mode outline token. `on-surface-variant-dark` (`#C8B5AE`) on `surface-dark` achieves 8.80:1 and would work as a border color, though it may be too prominent. A value around `#7A5040` would target 3:1+.

---

## Recommendations

Ordered by priority. Items 1–3 are blockers for WCAG 2.2 AA conformance.

**1. [BLOCKER] Fix input border contrast — light mode (SC 1.4.11)**
Replace `outline: #EDE0D8` with a value that achieves ≥ 3:1 against `#FFFBF9`. Suggested: `#9E8F89` (3.05:1) as minimum, `#78716C` (4.18:1) for comfortable compliance. Update the `input.border` component token in DESIGN.md accordingly.

**2. [BLOCKER] Fix input/outline border contrast — dark mode (SC 1.4.11)**
Replace `outline-dark: #5C3828` with a value achieving ≥ 3:1 against `#1A0C06`. Suggested: `#8A5A44` (~3.2:1). Update DESIGN.md.

**3. [BLOCKER] Fix `on-surface-variant` on `surface-variant` text contrast (SC 1.4.3)**
The 4.18:1 ratio fails for the role badge (Bénévole) and list-row body text. Either darken `on-surface-variant` from `#78716C` to ≈ `#6B6461` (4.5:1+ on surface-variant) or use `on-surface` for text within surface-variant containers. Update both DESIGN.md tokens and verify all component uses.

**4. [HIGH] Specify focus restoration on dialog close (SC 2.4.3)**
Add to EXPERIENCE.md dialog pattern: "On dialog close (any trigger — Annuler, Echap, or confirm), return focus to the element that triggered the dialog."

**5. [HIGH] Add skip navigation link (SC 2.4.1)**
Specify a visually hidden, focus-visible skip link as the first DOM element: `<a class="skip-link" href="#main-content">Skip to main content</a>`. Required for Admin layout with its persistent sidebar.

**6. [HIGH] Specify `aria-live` for toast notifications (SC 4.1.3)**
Add to EXPERIENCE.md: "Toast container carries `role='status'` and `aria-live='polite'` for success toasts; `role='alert'` and `aria-live='assertive'` for persistent system-error toasts." This ensures screen readers announce toast messages without requiring visual monitoring.

**7. [HIGH] Specify `aria-live` for phase chip SSE update (SC 4.1.3)**
Add to EXPERIENCE.md: "When the phase changes via SSE, an `aria-live='polite'` region (may be visually hidden) announces 'Phase changée : [nouvelle phase].'" The chip `aria-label` update alone is insufficient — screen readers do not re-read elements whose `aria-label` changes silently.

**8. [HIGH] Resolve scanner auto-refocus keyboard trap risk (SC 2.1.2)**
Replace the 500 ms inactivity auto-refocus with an explicit "Return to scanner" interaction (a labeled button or dedicated hotkey). If auto-refocus is retained for operational reasons, add a documented exception mechanism (e.g., pressing Tab twice, or a visible "Pause scanner" toggle) that temporarily disables it, allowing keyboard users to navigate the basket.

**9. [MEDIUM] Define accessible names for POS basket close buttons (SC 4.1.2)**
Add to EXPERIENCE.md: "Each article close button in the POS basket carries `aria-label='Retirer [article name] du panier'`." This makes each button distinguishable in screen reader listings.

**10. [MEDIUM] Verify sidebar item minimum height (SC 2.5.8)**
Either increase sidebar item `padding` from `8px 12px` to `12px 12px` (yielding ≈ 44 px effective height for 14 px body-md text), or explicitly document that gap spacing between items satisfies the SC 2.5.8 offset exception.

**11. [MEDIUM] Specify `TitleStrategy` implementation for SPA page titles (SC 2.4.2)**
Add implementation note to EXPERIENCE.md: "Angular `TitleStrategy` must be implemented to update `<title>` on each route. Pattern: '[Surface name] — PluriBourse'."

**12. [LOW] Clarify `aria-label` update strategy for filtered/loading lists (SC 1.3.1)**
Specify that the `aria-label` count on list containers is updated reactively (via Angular binding) on each filter change or load event, and acknowledge that count accuracy during scroll-load is approximate ("X articles affichés").

**13. [LOW] Clarify error injection pattern for `aria-describedby` (SC 3.3.1)**
Note in EXPERIENCE.md: "Inline error containers are pre-rendered with empty content and `aria-live='polite'`; they are populated on validation, not injected. This ensures `aria-describedby` links remain valid." Angular Material's `<mat-error>` handles this correctly if used consistently.

**14. [LOW] Verify focus-visible style meets WCAG 2.2 SC 2.4.11 (Focus Appearance)**
WCAG 2.2 added SC 2.4.11 (Focus Appearance, Level AA): focus indicator must have a minimum area of the perimeter of the unfocused component, with at least 3:1 contrast between focused and unfocused states. The spec states "Focus ring hérité du token `{colors.primary}`" — verify that the Angular Material focus ring implementation meets the 2 px minimum and area requirements, not just the color requirement.
