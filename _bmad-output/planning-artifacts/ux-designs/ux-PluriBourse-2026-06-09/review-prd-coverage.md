# PRD Coverage Review — PluriBourse UX
Date: 2026-06-09
Reviewer: PRD Coverage Lens

---

## Summary

EXPERIENCE.md covers the core operational flows (deposit, POS, settlement, phase transitions) solidly and its component inventory aligns well with the PRD. However, several UI-surfaced requirements are absent or significantly underspecified: seller GDPR anonymization, deposit-slip and invoice printing triggers, catalog-to-basket fallback, the admin Settings page, multi-language support in the UI itself, and the phase rollback flow. These gaps are not cosmetic — each one corresponds to a user-facing screen or interaction that will need to be designed.

---

## Findings

### PASS — Phase lifecycle coverage

All five phases (Inscription is pre-app, then Deposit → Sale → Post-sale → Closed) are represented. EXPERIENCE.md's IA table maps each Bénévole surface to the correct phase. The phase chip update via SSE (`phase-changed`) is specified for the topbar. Flow 4 demonstrates the forward transition with dialog and SSE broadcast. Rollback is mentioned in the PRD (FR-082) but is only partially covered — see CONCERN below.

### PASS — Core POS flows (FR-033 through FR-048, FR-081, FR-090)

Scanner input behavior, AZERTY/QWERTY transparency, basket management, lot grouping with X/N counter, blocked validation on incomplete lots, lot removal (FR-081), and basket cancellation on phase change (FR-090) are all specified in the Component Patterns and State Patterns sections, with Flow 2 providing the narrative walkthrough. This is the strongest area of coverage.

### PASS — User role coverage (Admin / Volunteer)

The two-role model is correctly reflected in the IA (separate routes, sidebar Admin-only, topbar role badge). FR-064 (admin cannot act as volunteer) is acknowledged in the Foundation section. The phase-adaptive Bénévole interface (FR-065) is covered by the IA table. FR-061 (single admin account) has no direct UX surface, so no gap there.

### PASS — Post-sale settlement core (FR-049 through FR-053)

Flow 3 covers the "Not collected" path with the correct confirmation dialog and irreversibility language. Unsettled seller list with phone number (FR-053) is represented. The settlement amount entry by the volunteer (FR-051) is implied by Flow 3 but not explicitly designed as a component — acceptable at spine level.

### PASS — Accessibility floor and tone

WCAG 2.2 AA, focus trap, aria-live for scanner, aria-label on phase chip, and 44×44px minimum targets are all specified. Voice and Tone section is thorough and directly addresses the volunteer-under-pressure context.

### PASS — Component patterns for printing feedback (FR-079)

The imprimante-hors-ligne toast pattern is defined in both State Patterns and Component Patterns, with the correct behavior (persistent toast, rejouable action). Covers FR-079.

---

### CONCERN — Phase rollback flow (FR-082)

FR-082 specifies that rollback (Closed → Post-sale → Sale → Deposit) is available one step at a time and requires confirmation. EXPERIENCE.md defines `/admin/editions/:id/phase` as the surface and shows the forward transition in Flow 4, but there is no specification of what the rollback trigger looks like, whether the same dialog pattern applies, or how the UI signals that rollback from Closed is unavailable after Clean Edition (FR-088). The Clean Edition action is mentioned in State Patterns (Phase Clôturée), but the combined rollback-disabled-after-clean state is not covered.

### CONCERN — Seller GDPR anonymization (FR-021)

FR-021 requires the admin to be able to delete a seller profile, which triggers anonymization across all editions (name, email, phone, item descriptions). This is a distinct destructive action with significant consequences. EXPERIENCE.md does not specify a UI surface for it: no mention on the `/admin/sellers/:id` fiche, no confirmation dialog spec, no explanation of what the anonymized fiche looks like post-action. The general "confirmation dialog for destructive actions" rule exists in Interaction Primitives but the specific trigger and post-state for GDPR deletion are absent.

### CONCERN — Deposit slip printing trigger (FR-031)

FR-031 requires a printable deposit slip per seller showing the item list, unit prices, and expected net payout. Flow 1 describes automatic label printing after deposit validation but does not mention deposit slip printing. It is unclear from EXPERIENCE.md whether the deposit slip prints automatically alongside the labels, or whether it requires a manual trigger on the seller fiche. This distinction affects the Dépôt form design. The Printing interaction primitive in EXPERIENCE.md (explicit button → spinner → toast) implies it is manual, but this is not confirmed for the deposit slip specifically.

### CONCERN — Buyer invoice printing trigger (FR-040, FR-041)

FR-040 specifies that after payment validation, a buyer invoice is printable "on demand." FR-041 specifies its content. EXPERIENCE.md does not describe where in the POS interface the invoice print button appears (in the basket after validation? on a post-transaction screen?), nor what the post-validation state of the POS looks like before the basket resets. This is a gap in the POS flow specification.

### CONCERN — Catalog-to-basket manual entry (FR-087)

FR-087 is a critical fallback for unreadable barcodes: a volunteer can add an item directly from the catalog to the current basket. EXPERIENCE.md references this briefly in the Scope section ("Manual basket entry from catalog (fallback for unreadable barcodes)") and lists `/volunteer/catalog` as available in all phases, but never specifies the interaction: Is there an "Add to basket" button on the catalog row that only appears during Sale phase? Does it open the POS view? Does the catalog need the active basket context? This flow has no corresponding Key Flow and no component pattern.

### CONCERN — Admin Settings page content (FR-073, FR-032, FR-005 through FR-007)

FR-073 specifies that an admin settings page centralizes: association name, commission rate, document language, and thermal ticket width. FR-032 makes ticket width configurable. FR-005–FR-007 define instance-level document language. EXPERIENCE.md lists `/admin/settings` in the IA table but provides no specification of the page content, field layout, or which settings are editable at which phase (e.g., commission rate frozen after Deposit starts — FR-016). This page needs a component or form spec.

### CONCERN — Commission rate freeze UX (FR-016)

FR-016 states the commission rate is modifiable until Deposit phase starts, then frozen for that edition. This implies a conditional edit state on the Settings or Edition detail page. EXPERIENCE.md notes that `/admin/editions/:id/categories` is "éditable" before Deposit and "read-only" after, but does not extend this pattern to the commission rate field. No visual treatment (disabled field, explanatory inline message) is specified.

### CONCERN — Initial admin setup and forced password change (FR-062)

FR-062 specifies that on first launch the admin logs in with Admin/Admin and is forced to change their password. This is a first-run UX flow with its own screen or modal. EXPERIENCE.md does not cover it. While it is a one-time flow, it is the first thing a deploying association will encounter.

### CONCERN — Language preference per account (FR-003, FR-067)

FR-003 and FR-067 require each account to store a UI language preference, modifiable in account settings. `/account` is listed in the shared IA but EXPERIENCE.md provides no specification of what the account page contains. Language switching is foundational to the ngx-translate requirement — it needs at minimum a surface spec.

### CONCERN — Non-functional requirements — performance perception (NFR-001)

NFR-001 requires no noticeable degradation on Raspberry Pi 4 under event load. EXPERIENCE.md specifies skeleton rows for loading states (good), but does not address perceived performance considerations specific to low-hardware contexts: no lazy loading strategy mentioned, no specification of maximum acceptable latency for scanner feedback, no guidance on debounce or request throttling for the catalog filters. These are UX-adjacent technical decisions that should be at least flagged in the spine.

### CONCERN — NFR-002 — Concurrent workstation conflict communication

NFR-002 and FR-042 require that simultaneous operations from 3+ workstations produce no conflicts. EXPERIENCE.md handles the already-sold item case (FR-036) and the basket cancellation on phase change (FR-090) via SSE. However, it does not address what happens when two cashiers scan the same item simultaneously at the exact same moment — the SSE `phase-changed` event is documented, but no equivalent `item-sold` conflict event is specified for the scanner result zone. The inline error "Article déjà vendu sur un autre poste" appears in Voice and Tone as a message example but its triggering mechanism and timing (is it synchronous on scan? asynchronous via SSE?) is not specified in Component Patterns.

---

### FAIL — Outstanding sellers report UI surface (FR-056)

FR-056 defines an "outstanding sellers report" listing unsettled sellers with their phone number, generated as PDF (FR-057). The `/admin/reports` route is in the IA, accessible in Post-sale and Closed phases. However, EXPERIENCE.md does not specify how reports are triggered, listed, or downloaded. There is no component pattern for the Reports page: no mention of a "Generate" button, no download mechanism, no distinction between the daily summary (FR-054, Sale phase only), the edition summary (FR-055, generated at close via FR-013), and the outstanding sellers report (FR-056). Three distinct reports with different triggers and lifecycles are collapsed into a single IA entry with no behavioral specification.

### FAIL — Archived edition read-only view and post-Clean state (FR-059, FR-086, FR-088)

FR-059 specifies that archived editions display aggregate metrics and seller profiles in read-only mode; item-level detail is only available in PDF. FR-086 adds that if Clean has been triggered, item-level data is not available in the catalog. FR-088 defines Clean Edition as a permanent action. EXPERIENCE.md mentions the "Phase Clôturée" state in State Patterns (read-only banner, "Nettoyer l'édition" button) but does not specify: what the archived edition detail page looks like, what aggregate metrics are displayed, how seller profiles appear without item detail, or how the catalog handles the post-Clean state (empty? hidden? message?). This is effectively an entire screen family absent from the spine.

### FAIL — Sales summary (bilan de vente) print trigger in Post-sale (FR-049, FR-050, FR-065)

FR-049 and FR-065 specify that in Post-sale phase, a volunteer can print a seller's sales summary to group their unsold items before handover. This is a primary Post-sale volunteer action — arguably more frequent than settlement itself. Flow 3 (settlement) is the only Post-sale key flow and it does not mention printing. The sales summary print button on the settlement list or seller fiche is completely absent from EXPERIENCE.md.

---

## Recommendations

Ordered by priority. Blockers (FAIL items) first.

### 1. [BLOCKER] Specify the Reports page (FR-054, FR-055, FR-056, FR-057, FR-058)
Add a component pattern and at minimum a wireframe description for `/admin/reports`. Define the three report types, their generation triggers (on-demand button vs. automatic at close), their access phases, and the download mechanism (PDF download link / in-browser open / auto-print). This is a three-report surface, not a single route.

### 2. [BLOCKER] Specify the archived edition view and post-Clean catalog state (FR-059, FR-086, FR-088)
Add a description of the archived edition detail page: aggregate metrics displayed, seller profile read-only view without item detail, and catalog behavior after Clean. Define what "Nettoyer l'édition" button triggers and what the post-Clean UI state looks like (disabled catalog entry? empty catalog with explanatory message?).

### 3. [BLOCKER] Add a Post-sale Key Flow covering sales summary printing (FR-049, FR-050, FR-065)
Flow 3 only covers settlement. Add Flow 5 (or extend Flow 3) to show the volunteer printing a seller's sales summary before handing over unsold items. Specify where the print trigger appears: on the settlement list row, or only on the seller fiche.

### 4. Specify the admin Settings page content and editable states (FR-073, FR-016, FR-032, FR-005)
Add a form spec for `/admin/settings`: fields (association name, commission rate, document language, thermal ticket width), their edit conditions (commission rate disabled after Deposit start with explanatory message), and how document language relates to printed output language.

### 5. Add catalog-to-basket Key Flow (FR-087)
Add a short flow showing how a volunteer accesses the catalog during Sale phase, locates an unreadable item, and adds it to the active basket. Specify whether "Add to basket" is contextually visible only during Sale phase, and how the volunteer returns to the POS after the action.

### 6. Specify GDPR seller deletion (FR-021)
On the `/admin/sellers/:id` fiche, document the delete/anonymize action: confirmation dialog content (consequences: anonymization across all editions, irreversible), and the post-anonymization state of the fiche (grayed-out placeholder data vs. removed entry).

### 7. Specify deposit slip and buyer invoice print triggers (FR-031, FR-040, FR-041)
Clarify in the deposit form flow whether the deposit slip prints automatically with labels (alongside FR-028) or is triggered manually. Clarify what the POS state looks like after transaction validation and where the invoice print button appears.

### 8. Cover the rollback flow and rollback-disabled-after-Clean state (FR-082, FR-088)
On `/admin/editions/:id/phase`, specify what rollback buttons look like, that the same confirmation dialog pattern applies, and how the UI communicates that rollback from Closed is permanently unavailable after Clean Edition.

### 9. Specify the `/account` page (FR-003, FR-067)
Add minimal spec for the account settings page: language preference selector (EN/FR), how it applies immediately without page reload (ngx-translate runtime switch), and any other user-editable fields.

### 10. Add first-run forced password change flow (FR-062)
Specify the Admin/Admin first-login flow: is it a redirect to a dedicated change-password screen, or an in-place modal on `/admin`? What prevents navigating away before the password is changed?

### 11. Clarify concurrent scan conflict delivery mechanism (NFR-002, FR-036)
In the Scanner input component pattern, specify whether the "already sold" error for a concurrent scan is a synchronous HTTP error response (most likely) or an async SSE push — and how the POS handles the case where two workstations succeed at scanning the same item within the same request window.
