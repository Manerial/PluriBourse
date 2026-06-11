---
stepsCompleted: ["step-01-document-discovery", "step-02-prd-analysis", "step-03-epic-coverage-validation", "step-04-ux-alignment", "step-05-epic-quality-review", "step-06-final-assessment"]
documentsIncluded:
  prd: "prds/prd-PluriBourse-2026-06-08/prd.md"
  prdAddendum: "prds/prd-PluriBourse-2026-06-08/addendum.md"
  architecture: "architecture.md"
  epics: "epics.md"
  uxDesign: "ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md"
  uxExperience: "ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md"
  uxPrdCoverage: "ux-designs/ux-PluriBourse-2026-06-09/review-prd-coverage.md"
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-12
**Project:** PluriBourse

---

## PRD Analysis

### Functional Requirements

**F1 — Internationalisation (EN/FR)** *(7 FRs)*
- FR-001: UI available in English and French.
- FR-002: Default language detected from browser, stored in account preferences on first login.
- FR-003: Each user can change their language preference in account settings.
- FR-004: All UI text externalized — no hardcoded strings.
- FR-005: Language of all printed documents configured per edition.
- FR-006: Each edition has its own document language, initialized from instance setting at creation.
- FR-007: Edition document language modifiable by admin at any time; instance default only applies to new editions.

**F2 — Edition Management & Lifecycle** *(13 FRs)*
- FR-008: Admin can create an edition with a free name.
- FR-009: Multiple editions per year supported.
- FR-010: Only one active edition at a time (Preparation/Deposit/Sale/Post-sale = active; Closed = inactive).
- FR-011: Any phase transition requires explicit admin confirmation dialog.
- FR-012: Active phase displayed clearly to all logged-in users.
- FR-013: Admin triggers edition closure via "Clôturer l'Édition" button in Post-sale phase; PDFs generated in both languages; edition becomes read-only.
- FR-014: Edition past Preparation phase cannot be deleted.
- FR-015: Each edition's data strictly siloed (articles, sales, reports).
- FR-016: Each edition has its own commission rate, initialized from instance default (20%); frozen once Deposit phase starts.
- FR-080: Admin can copy category/table-mapping structure from a closed edition when creating a new one.
- FR-082: Admin can roll back one phase at a time; all recorded data preserved (sales stay sold, settlements stay valid).
- FR-088: Post-closure admin can trigger "Nettoyer l'Édition" to permanently delete article records and seller profiles; rollback to Post-sale disabled afterwards.
- FR-096: Edition closure gated on all sellers being settled or unclaimed; server rejects closure request (409) if violated.

**F3 — Seller & Item Management (Deposit Phase)** *(20 FRs)*
- FR-017: Admin configures item category list per edition.
- FR-018: Admin configures category-to-table mapping per edition (many-to-many); frozen once Deposit starts; rollback re-enables editing.
- FR-019: Seller profiles per edition; required fields: last name, first name, email, phone.
- FR-020: Volunteer searches seller by name or email; creates new profile if not found.
- FR-021: Admin can delete seller in Deposit phase (GDPR erasure), with explicit confirmation.
- FR-022: Per item: name/description, price, category, complete/incomplete indicator, comment if incomplete.
- FR-023: Table auto-assigned by system using category-table mapping; same table reused if seller already has items in category; otherwise least-loaded table chosen.
- FR-024: Item can only be edited or deleted in Deposit phase.
- FR-025: Complete/incomplete indicator and comment modifiable in all phases.
- FR-089: Commission applies normally to incomplete items; completeness does not affect price or commission rate.
- FR-043: Volunteer can create a bundle (indivisible set of items at a single global price).
- FR-044: Each bundle item has its own name/description and its own label.
- FR-045: Bundle item label displays "Prix du lot: X€" and "Lot indivisible: X/N".
- FR-026: Unique Code 128 barcode generated server-side per item; 8 digits: 4 seller number + 4 item number.
- FR-027: Item label displays: edition name / blank line / framed category / item name+price / "INCOMPLET" if applicable / table number / blank line / Code 128 bitmap / barcode number XXXX-XXXX / blank line. No seller name (GDPR).
- FR-028: Label printing triggered automatically when volunteer validates a seller's deposit.
- FR-029: Print jobs queued server-side and executed sequentially.
- FR-030: Printed roll format per seller: [seller separator] → [item label] → [article separator] → [item label] → …
- FR-031: Deposit receipt printable per seller: item list, unit prices, expected net payout after commission.
- FR-032: Thermal ticket width configurable in admin settings (default: 57mm).

**F4 — Point of Sale (Sale Phase)** *(16 FRs)*
- FR-033: POS interface supports USB HID barcode scanner.
- FR-034: Scan component handles AZERTY/QWERTY keyboard layout differences transparently via key-code mapping.
- FR-035: Each scanned item added to current buyer's cart; name and price displayed.
- FR-036: Scanning already-sold item displays explicit error; item not added to cart.
- FR-037: Scanning incomplete item displays informational warning with missing detail; item can still be sold.
- FR-038: Cashier can remove individual items from cart before payment validation.
- FR-039: Payment validation marks all cart items as sold; no returns or exchanges possible.
- FR-040: After validation, buyer invoice printable on demand via central print server.
- FR-041: Invoice displays: item list, unit prices, total, association name (from instance settings), edition name, date. Bundle appears as single line.
- FR-042: App supports minimum 3 simultaneous POS stations without data conflicts.
- FR-046: Scanning a bundle item shows bundle name in red with counter "X/N scanned".
- FR-047: System blocks payment validation until bundle is complete (all N items scanned).
- FR-048: Complete bundle sold at global bundle price; commission applied to full bundle price.
- FR-081: If cashier cannot complete a bundle, they can remove the entire bundle from cart.
- FR-090: If admin triggers phase transition while volunteer has active cart, cart is cancelled with explicit error message.
- FR-093: Payment validation requires payment method selection (cash/cheque/card); optional "amount tendered" for cash triggers change calculation.

**F5 — Post-Sale & Payouts** *(6 FRs)*
- FR-095: Settlement page is F5 entry point; lists all edition sellers filterable by status; actions per line: print sale report, settle, mark unclaimed; accessible at /volunteer/settlement and /admin/settlement; admin sees phone+email columns.
- FR-049: Sale report printable per seller in Post-sale phase.
- FR-050: Sale report contains: sold items (name, unit price), unsold items (name, category, table number), gross total, commission deducted, net amount to pay out. Bundle appears as single line.
- FR-051: To settle seller: volunteer enters cash amount paid and clicks "Solder"; warning if amount < net calculated (can still validate); blocked if amount > net. Status set to Soldé.
- FR-052: "Non réclamé" button records full owed amount as association revenue.
- FR-053: Unsettled sellers identifiable via dedicated filter on settlement page.

**F6 — Reports** *(8 FRs)*
- FR-054: Admin can generate a daily report at any time during Sale phase; covers current calendar day; contains: sold/unsold item count, daily revenue, daily commission.
- FR-055: Edition report generated at closure; contains: total sold/unsold, gross revenue, total commission.
- FR-094: Daily and edition reports include revenue breakdown by payment method (cash, cheque, card).
- FR-057: All reports generated as PDF.
- FR-058: Reports accessible to admin only.
- FR-059: Closed editions show aggregated metrics in read-only; article and seller details available until Clean action; after clean, only aggregated metrics accessible.
- FR-091: Admin can export item catalogue as CSV in Post-sale and Closed phases; download triggered directly.
- FR-092: Admin can export seller payouts as CSV in Post-sale and Closed phases; download triggered directly.

**F7 — User Accounts & Access Control** *(8 FRs)*
- FR-060: Admin creates, edits, and deactivates volunteer accounts; admin can reset volunteer password.
- FR-061: One admin account per instance.
- FR-062: On first launch, admin account initialized with Admin/Admin credentials; admin forced to change password on first login.
- FR-063: Admin password reset via server-side CLI command generating temporary password; forced change on next login.
- FR-064: Admin and Volunteer roles strictly separated; admin cannot access volunteer interfaces from admin account.
- FR-065: Volunteer interface adapts to active phase; in Post-sale phase volunteer can print seller sale report.
- FR-066: Sessions do not expire automatically (local closed network, day-of constraints).
- FR-067: Each account stores interface language preference (EN/FR), detected from browser on first login, modifiable in settings.

**F8 — Infrastructure & Deployment** *(7 FRs)*
- FR-068: Server runs on Linux, macOS, and Windows without code modification.
- FR-069: Minimum spec: Raspberry Pi 4 (2 GB RAM) or equivalent 64-bit machine.
- FR-070: Application deployed via Docker Compose (Spring Boot + MariaDB); data in persistent Docker volumes.
- FR-071: Updates applied with two commands: `docker compose pull && docker compose up -d`; persistent data preserved.
- FR-072: Client stations access application via browser; no local installation required.
- FR-073: Admin settings page centralizes instance configuration: association name, default commission rate, default document language, thermal ticket width.
- FR-074: Installation guide is a standalone deliverable for non-technical users; 7 mandatory sections; must be followable end-to-end without assistance.

**F9 — Print Infrastructure** *(5 FRs)*
- FR-075: All printing routed via central server; no printer required on client stations.
- FR-076: Thermal printer (item labels): connected to server via USB; width per FR-032; queue per FR-029.
- FR-077: Standard printer (A4 documents): connected to server via USB; PDF generated server-side, sent directly without preview.
- FR-078: User triggers printing from interface; request handled by server with no client-side action required.
- FR-079: On print error (offline, jam, out of paper): user notified with explicit message indicating cause; print queue suspended; user can retry or skip the failed job; admin has view of queue state and current errors.

**F10 — Item Catalogue** *(4 FRs)*
- FR-083: Filterable and sortable item catalogue accessible to admin and volunteers during all phases of active edition.
- FR-084: Catalogue filterable by: name/description, barcode, category, table, sold/unsold status, complete/incomplete, seller name.
- FR-085: Catalogue sortable by any visible column.
- FR-086: Catalogue shows active edition items only; no item-level data available after Clean action.

**Total Functional Requirements: 94**

---

### Non-Functional Requirements

- NFR-001 (Performance): Usable on Raspberry Pi 4 (2 GB RAM) under event load (~100 sellers, ~1,700 items, 3 simultaneous stations); POS operations (scan, payment validation) < 500ms; other pages (catalogue, reports) < 1s under nominal load.
- NFR-002 (Concurrency): Simultaneous operations from multiple stations cause no data conflicts; system prevents simultaneous sale of same item from two stations — second station receives explicit error.
- NFR-003 (Financial Accuracy): Payout calculations (price − commission) exact to the cent for each seller and edition totals.
- NFR-004 (Browser Compatibility): Interface works on any modern browser (Chrome, Firefox, Edge, Safari) on any OS.
- NFR-005 (Scanner Compatibility): USB HID scanners work without configuration regardless of keyboard layout (AZERTY/QWERTY).
- NFR-006 (Reliability): No data loss on unexpected browser closure or client station failure.
- NFR-007 (GDPR): Seller personal data (name, first name, email, phone) deletable on request; anonymized data in archived editions does not allow re-identification.

**Total Non-Functional Requirements: 7**

---

### Additional Requirements & Constraints

- **Financial constraint**: All financial calculations must use `BigDecimal` (from CLAUDE.md) — never `float` or `double`.
- **No PII in logs**: Seller name, email, phone number must not appear in application logs.
- **i18n architecture**: Frontend uses ngx-translate (JSON files); Backend uses Spring MessageSource (.properties files). Consistent key naming convention recommended for shared business terms.
- **Print protocol**: ESC/POS for thermal printer; `escpos-coffee` library candidate.
- **Barcode**: Code 128 generated server-side as bitmap before ESC/POS transmission.
- **Commission frozen**: Rate locked per edition once Deposit phase starts; applies to all items including incomplete and bundle items.
- **Session management**: Non-goal for v1 — sessions do not expire (local closed-network constraint).
- **FR-056 obsoleted**: Replaced by FR-095 (settlement page as F5 entry point).

---

### PRD Completeness Assessment

The PRD is thorough and well-structured. All 10 feature groups (F1–F10) are fully specified with numbered requirements (FR-001 to FR-096, with intentional gaps for removed/merged requirements). NFRs cover the 7 most critical quality attributes. The addendum correctly captures post-UX decisions and clarifies FR-095 (settlement page redesign), FR-091/092 (CSV exports), FR-093/094 (payment method). No ambiguities or missing requirements detected at PRD level.

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Epic | Story | Status |
|----|------|-------|--------|
| FR-001 | Epic 1 | 1.6 | ✓ Covered |
| FR-002 | Epic 1 | 1.6 | ✓ Covered |
| FR-003 | Epic 1 | 1.6 | ✓ Covered |
| FR-004 | Epic 1 | 1.6 | ✓ Covered |
| FR-005 | Epic 1+2 | 1.6, 2.1 | ✓ Covered |
| FR-006 | Epic 1+2 | 1.5, 2.1 | ✓ Covered |
| FR-007 | Epic 1+2 | 1.5, 2.1 | ✓ Covered |
| FR-008 | Epic 2 | 2.1 | ✓ Covered |
| FR-009 | Epic 2 | 2.1 | ✓ Covered |
| FR-010 | Epic 2 | 2.1 | ✓ Covered |
| FR-011 | Epic 2 | 2.2 | ✓ Covered |
| FR-012 | Epic 2 | 2.4 | ✓ Covered |
| FR-013 | Epic 2 | 2.5 | ✓ Covered |
| FR-014 | Epic 2 | 2.1 | ✓ Covered |
| FR-015 | Epic 2 | 2.1 | ✓ Covered |
| FR-016 | Epic 2 | 2.1, 2.2 | ✓ Covered |
| FR-017 | Epic 2 | 2.3 | ✓ Covered |
| FR-018 | Epic 2 | 2.3 | ✓ Covered |
| FR-019 | Epic 3 | 3.1 | ✓ Covered |
| FR-020 | Epic 3 | 3.1 | ✓ Covered |
| FR-021 | Epic 3 | 3.1 | ✓ Covered |
| FR-022 | Epic 3 | 3.2 | ✓ Covered |
| FR-023 | Epic 3 | 3.2 | ✓ Covered |
| FR-024 | Epic 3 | 3.2 | ✓ Covered |
| FR-025 | Epic 3 | 3.2 | ✓ Covered |
| FR-026 | Epic 3 | 3.5 | ✓ Covered |
| FR-027 | Epic 3 | 3.5 | ✓ Covered |
| FR-028 | Epic 3 | 3.5 | ✓ Covered |
| FR-029 | Epic 3 | 3.4 | ✓ Covered |
| FR-030 | Epic 3 | 3.5 | ✓ Covered |
| FR-031 | Epic 3 | 3.6 | ✓ Covered |
| FR-032 | Epic 3 | 3.5 | ✓ Covered |
| FR-033 | Epic 4 | 4.1 | ✓ Covered |
| FR-034 | Epic 4 | 4.1 | ✓ Covered |
| FR-035 | Epic 4 | 4.1 | ✓ Covered |
| FR-036 | Epic 4 | 4.1 | ✓ Covered |
| FR-037 | Epic 4 | 4.1 | ✓ Covered |
| FR-038 | Epic 4 | 4.2 | ✓ Covered |
| FR-039 | Epic 4 | 4.2 | ✓ Covered |
| FR-040 | Epic 4 | 4.5 | ✓ Covered |
| FR-041 | Epic 4 | 4.5 | ✓ Covered |
| FR-042 | Epic 4 | 4.4 | ✓ Covered |
| FR-043 | Epic 3 | 3.3 | ✓ Covered |
| FR-044 | Epic 3 | 3.3 | ✓ Covered |
| FR-045 | Epic 3 | 3.3, 3.5 | ✓ Covered |
| FR-046 | Epic 4 | 4.3 | ✓ Covered |
| FR-047 | Epic 4 | 4.3 | ✓ Covered |
| FR-048 | Epic 4 | 4.3 | ✓ Covered |
| FR-049 | Epic 5 | 5.2 | ✓ Covered |
| FR-050 | Epic 5 | 5.2 | ✓ Covered |
| FR-051 | Epic 5 | 5.1 | ✓ Covered |
| FR-052 | Epic 5 | 5.1 | ✓ Covered |
| FR-053 | Epic 5 | 5.1 | ✓ Covered |
| FR-054 | Epic 5 | 5.3 | ✓ Covered |
| FR-055 | Epic 5 | 5.4 | ✓ Covered |
| FR-057 | Epic 5 | 5.3, 5.4 | ✓ Covered |
| FR-058 | Epic 5 | 5.3 | ✓ Covered |
| FR-059 | Epic 5 | 5.4 | ✓ Covered |
| FR-060 | Epic 1 | 1.3 | ✓ Covered |
| FR-061 | Epic 1 | 1.3 | ✓ Covered |
| FR-062 | Epic 1 | 1.2 | ✓ Covered |
| FR-063 | Epic 1 | 1.4 | ✓ Covered |
| FR-064 | Epic 1 | 1.2 | ✓ Covered |
| FR-065 | Epic 1 | 1.7 | ✓ Covered |
| FR-066 | Epic 1 | 1.2 | ✓ Covered |
| FR-067 | Epic 1 | 1.6 | ✓ Covered |
| FR-068 | Epic 1 | 1.1 | ✓ Covered |
| FR-069 | Epic 1 | 1.1 | ✓ Covered |
| FR-070 | Epic 1 | 1.1 | ✓ Covered |
| FR-071 | Epic 1 | 1.9 | ✓ Covered |
| FR-072 | Epic 1 | 1.1 | ✓ Covered |
| FR-073 | Epic 1 | 1.5 | ✓ Covered |
| FR-074 | Epic 1 | 1.9 | ✓ Covered |
| FR-075 | Epic 3 | 3.4 | ✓ Covered |
| FR-076 | Epic 3 | 3.4 | ✓ Covered |
| FR-077 | Epic 3 | 3.4 | ✓ Covered |
| FR-078 | Epic 3 | 3.4 | ✓ Covered |
| FR-079 | Epic 3 | 3.4, 3.7 | ✓ Covered |
| FR-080 | Epic 2 | 2.3 | ✓ Covered |
| FR-081 | Epic 4 | 4.3 | ✓ Covered |
| FR-082 | Epic 2 | 2.2 | ✓ Covered |
| FR-083 | Epic 6 | 6.1 | ✓ Covered |
| FR-084 | Epic 6 | 6.1 | ✓ Covered |
| FR-085 | Epic 6 | 6.1 | ✓ Covered |
| FR-086 | Epic 6 | 6.1 | ✓ Covered |
| FR-088 | Epic 2 | 2.5 | ✓ Covered |
| FR-089 | Epic 3 | 3.2, 5.2 | ✓ Covered |
| FR-090 | Epic 2+4 | 2.6, 4.6 | ✓ Covered |
| FR-091 | Epic 5 | 5.5 | ✓ Covered |
| FR-092 | Epic 5 | 5.5 | ✓ Covered |
| FR-093 | Epic 4 | 4.2 | ✓ Covered |
| FR-094 | Epic 5 | 5.3, 5.4 | ✓ Covered |
| FR-095 | Epic 5 | 5.1, 5.4 | ✓ Covered |
| FR-096 | Epic 2 | 2.5 | ✓ Covered |

### Missing Requirements

**None.** All 94 FRs are covered by the epics.

### Notable Coverage Observations

- **FR-017/018** (category & table mapping): Located under F3 in the PRD, but intentionally implemented in Epic 2 Story 2.3 — logically correct as these are edition-level admin pre-configuration settings.
- **FR-090** (cart cancellation on phase change): Split across Epic 2 Story 2.6 (server-side SSE emission) and Epic 4 Story 4.6 (Angular POS client handling) — intentional and well-documented.
- **FR-075–079** (print infrastructure): Entirely absorbed into Epic 3 with dedicated stories (3.4 for infrastructure, 3.5–3.6 for generation, 3.7 for admin diagnostics).
- **UX-DR22** (print button with spinner feedback on settlement list): Appears in both Epic 3 (deposit context) and Epic 5 (post-sale settlement context) — correct cross-epic reuse.

### NFR Coverage

All 7 NFRs are covered:
- NFR-001 (Performance): Addressed by ARCH-001 stack choices and Story 1.1 baseline
- NFR-002 (Concurrency): Explicitly covered by ARCH-003, Story 4.4 (optimistic locking + Testcontainers test)
- NFR-003 (Financial Accuracy): BigDecimal enforced in Stories 1.5, 2.1, 3.2, 5.2
- NFR-004 (Browser Compatibility): Addressed by Story 1.7 (Angular Material cross-browser)
- NFR-005 (Scanner Compatibility): Addressed by Story 4.1 (AZERTY/QWERTY key-code mapping)
- NFR-006 (Reliability): Addressed by ARCH-002 (Spring Session JDBC for session persistence)
- NFR-007 (GDPR): Addressed by Stories 3.1 (seller deletion), 5.4 (clean action), plus CLAUDE.md constraint on no PII in logs

### Coverage Statistics

- Total PRD FRs: **94**
- FRs covered in epics: **94**
- Coverage: **100%**
- Total NFRs: **7** — all covered
- Total UX-DRs: **22** — all covered (UX-DR1–22)
- Total ARCH requirements: **16** — all covered (ARCH-001–016)

---

## UX Alignment Assessment

### UX Document Status

**Found — comprehensive.** Four documents present:
- `DESIGN.md` — visual design tokens (colors, typography, spacing, components)
- `EXPERIENCE.md` (status: final, updated 2026-06-12) — behavioral spine with IA, flows, component patterns, accessibility
- `review-prd-coverage.md` (2026-06-09) — formal UX coverage review against PRD
- `review-accessibility.md` + `review-coherence.md` — additional quality reviews

### UX ↔ PRD Alignment

A formal UX coverage review was conducted on 2026-06-09 and identified **2 FAILURES** and **8 CONCERNS**. All have been resolved at the epic/story level:

| Issue | Severity | Resolution in Epics |
|-------|----------|-------------------|
| Archived edition read-only view + post-cleanup state (FR-059, FR-086, FR-088) | FAILURE → Resolved | Story 2.5 (cleanup empty state), Story 5.4 (archived edition aggregated metrics) |
| Post-sale sale report print trigger (FR-049, FR-050, FR-065) | FAILURE → Resolved | Story 5.1 (print button per seller row, UX-DR22), Story 5.2 (dedicated print criteria) |
| Phase rollback UI (FR-082) | CONCERN → Resolved | Story 2.2 (rollback button, same dialog, disabled state after cleanup) |
| GDPR seller deletion UI (FR-021) | CONCERN → Resolved | Story 3.1 (explicit deletion AC with GDPR erasure confirmation) |
| Deposit receipt print trigger (FR-031) | CONCERN → Resolved | Story 3.6 (automatic, parallel to label printing at deposit validation) |
| Buyer invoice print trigger (FR-040, FR-041) | CONCERN → Resolved | Story 4.5 (dedicated POS invoice story, print button after payment) |
| Admin settings page content (FR-073, FR-016, FR-032) | CONCERN → Resolved | Story 1.5 (full criteria for all 4 settings fields with BigDecimal enforcement) |
| Commission freeze UX (FR-016) | CONCERN → Resolved | Story 2.1 (explicit error: "taux figé une fois le Dépôt démarré") |
| Forced password change first launch (FR-062) | CONCERN → Resolved | Story 1.2 (redirect to forced password change page, access blocked until changed) |
| Account language preference page (FR-003, FR-067) | CONCERN → Resolved | Story 1.6 (/account page with immediate language switch, no reload) |
| Concurrent scan conflict mechanism (NFR-002) | CONCERN → Resolved | Story 4.4 (optimistic locking 409 response, inline error display) |

### UX ↔ Architecture Alignment

All UX requirements have architectural support:

| UX Requirement | Architectural Support |
|---|---|
| UX-DR4 (real-time phase chip via SSE) | ARCH-012 (SseEmitterRegistry, `phase-changed` event) |
| UX-DR10 (scanner auto-focus, AZERTY/QWERTY) | Angular key-code mapping component (NFR-005) |
| UX-DR14 (cart with bundle grouping, SSE cart-cancel) | ARCH-012 (`basket-cancelled` SSE event) |
| UX-DR19 (print button spinner + toast) | ARCH-009 (LinkedBlockingQueue, server-side print job status) |
| UX-DR21 (cart cancellation notification) | ARCH-012 (SSE `basket-cancelled` payload) |
| UX-DR11 (filterable/sortable lists, MatPaginator) | ARCH-005 (JPageFlow for paginated/filtered endpoints) |
| Sessions persist across server restart | ARCH-002 (Spring Session JDBC) |
| Financial calculations (prices, commissions) | NFR-003 (BigDecimal throughout — CLAUDE.md constraint) |

### Warnings

**One known technical limitation (documented, not blocking):**
- ARCH-005 notes a known bug in JPageFlow v1.5.0: BigDecimal sort is broken. Story 6.1 documents this as a known failure pending a library fix. The catalogue is still fully usable; only price-column sorting is affected.

**UX spec gaps closed at story level (not in EXPERIENCE.md directly):**
- Admin settings page field specs, account page, archived edition detail page, and GDPR deletion dialog are specified in story ACs rather than in EXPERIENCE.md. This is acceptable — the stories provide sufficient implementation guidance for these screens.

---

## Epic Quality Review

### Epic Structure Validation

#### User Value Focus

| Epic | User-Centric Goal | Assessment |
|------|------------------|-----------|
| Epic 1 | "Admins and volunteers can deploy, log in, manage accounts, configure instance, and use the app in their preferred language" | ✅ User value — foundation with clear user outcomes |
| Epic 2 | "Admins can create editions, drive the full phase cycle, roll back phases, and close/clean editions" | ✅ User value |
| Epic 3 | "Volunteers can register sellers and all their items (incl. bundles) with auto table assignment, and print labels and deposit receipts" | ✅ User value |
| Epic 4 | "Volunteers can scan items, manage carts with full bundle support, finalize sales and print invoices — safely on multiple simultaneous stations" | ✅ User value |
| Epic 5 | "Volunteers can settle sellers and process payouts. Admins can generate PDF reports, identify unsettled sellers, and officially close editions." | ✅ User value |
| Epic 6 | "Admins and volunteers can browse, search and filter all items from the active edition in all phases" | ✅ User value |

All 6 epics are user-centric with clear outcomes. None are purely technical milestones.

#### Epic Independence

- **Epic 1** stands alone completely ✅
- **Epic 2** requires Epic 1 (auth/session) — correct ✅
- **Epic 3** requires Epics 1+2 (auth + phase state + categories) — correct ✅
- **Epic 4** requires Epics 1+2+3 (auth + phases + items to scan) — correct ✅
- **Epic 5** requires Epics 1+2+3+4 (settlements require completed sales) — correct ✅
- **Epic 6** requires Epic 3 (items must exist to catalogue them) — could run in parallel with Epics 4-5 ✅

No circular dependencies. No forward dependencies between epics.

### Story Quality Assessment

#### Story Sizing

All stories are appropriately scoped — each delivers one coherent, demonstrable outcome. Notable observations:

- **Story 1.1** (project skeleton setup) is a developer story with no direct user-visible value. This is the correct first story for a greenfield project and acknowledged by ARCH-001.
- **Story 3.4** (print queue infrastructure) is explicitly marked as `"Story technique prérequise (spike accepté) — Aucune valeur utilisateur visible en sprint review."` The team has transparently acknowledged this. This is an acceptable pragmatic exception given the complexity of ESC/POS print infrastructure.
- **Story 2.6** (server-side cart cancellation) is a server-only story. The Angular client handling is deliberately deferred to **Story 4.6** with an explicit dependency note. This split is intentional and documented.

#### Acceptance Criteria Quality

BDD format (Given/When/Then) is consistently used across all 28 stories. Sample assessment:

| Story | AC Quality | Notes |
|-------|-----------|-------|
| 1.2 (Auth) | ✅ Strong | Covers happy path, forced password change, role separation, session persistence |
| 2.1 (Edition CRUD) | ✅ Strong | Covers commission freeze, language inheritance, deletion guard |
| 3.2 (Item registration) | ✅ Strong | Covers table assignment algorithm both branches, incompleteness, phase lock |
| 4.4 (Concurrency) | ✅ Strong | 409 response, conflict list display, optimistic locking, Testcontainers test required |
| 5.1 (Settlement) | ✅ Strong | Under-payment warning, over-payment block, unclaimed flow, print button |
| 6.1 (Catalogue) | ✅ Strong | Explicitly documents JPageFlow BigDecimal sort as known failure |

No vague ACs found ("user can login" style). Error conditions are covered. All ACs are testable and measurable.

### Dependency Analysis

#### Within-Epic Dependencies

All within-epic dependencies flow correctly in story order:
- Story 3.4 (print infrastructure) is explicitly prerequisite to 3.5, 3.6, 3.7
- Story 1.7 (design system) is prerequisite to all UI stories
- Story 4.6 declares dependency on Story 2.6 — this is a cross-epic backward dependency (2 < 4), which is correct

#### Database/Entity Creation Timing

Story 1.1 creates 4 initial Liquibase changesets:
- `001-core-schema`: users table (required before any auth story)
- `002-spring-session`: required before Story 1.2 (auth)
- `003-category-table-mapping`: required before Story 2.3
- `004-instance-config`: required before Story 1.5

This is appropriate for a greenfield project — all tables are foundational and Liquibase allows incremental additions per story. No table is created prematurely for features far in the future.

### Best Practices Compliance Checklist

| | Epic 1 | Epic 2 | Epic 3 | Epic 4 | Epic 5 | Epic 6 |
|--|:------:|:------:|:------:|:------:|:------:|:------:|
| Delivers user value | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Functions independently (relative to sequence) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Stories appropriately sized | ✅ | ✅ | ✅* | ✅ | ✅ | ✅ |
| No forward dependencies | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Clear BDD acceptance criteria | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| FR traceability maintained | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

_* Story 3.4 is a technical spike, explicitly acknowledged_

### Quality Findings by Severity

#### 🟡 Minor Concerns (non-blocking)

1. **Story 3.4 is a technical story** — no user-visible value at sprint review. The team has been transparent ("spike accepté"). Recommend keeping it but ensuring the sprint review demo focuses on Stories 3.5+ which consume the infrastructure.

2. **Story 2.6 server/client split** — the separation of FR-090 across two epics (2.6 server, 4.6 client) is intentional and documented, but a developer unfamiliar with the plan could miss Story 4.6. Recommend adding a cross-reference in Story 4.6's definition of done.

3. **JPageFlow BigDecimal sort bug (ARCH-005)** — acknowledged in Story 6.1. Recommend tracking the library fix as a backlog item so it doesn't fall through the cracks post-launch.

#### 🔴 Critical Violations

**None found.**

#### 🟠 Major Issues

**None found.**

---

## Summary and Recommendations

### Overall Readiness Status

# ✅ READY

PluriBourse is ready to proceed to implementation. All planning artifacts are complete, coherent, and consistent. No critical issues or major blockers were identified.

### Assessment Summary

| Dimension | Score | Finding |
|-----------|-------|---------|
| PRD completeness | ✅ 100% | 94 FRs, 7 NFRs, fully specified |
| Epic FR coverage | ✅ 100% | 94/94 FRs covered across 6 epics |
| UX alignment | ✅ Resolved | All prior review failures/concerns addressed in epics |
| Architecture ↔ UX | ✅ Aligned | All UX components architecturally supported |
| Epic quality | ✅ Excellent | No critical violations; BDD ACs throughout |
| Story quality | ✅ Excellent | No forward dependencies; all stories independently completable |
| ARCH coverage | ✅ 100% | 16/16 ARCH requirements covered |
| UX-DR coverage | ✅ 100% | 22/22 UX design requirements covered |

### Issues Requiring Attention Before/During Implementation

#### Before Starting (Recommended)

1. **Add a cross-reference in Story 4.6** to explicitly call out Story 2.6 as a prerequisite. This prevents a developer picking up Story 4.6 in isolation from missing the SSE server-side implementation.

2. **Create a backlog item for JPageFlow BigDecimal sort fix** (ARCH-005). Story 6.1 documents this as a known failure, but it risks being forgotten post-launch. Track it as a p2 bug.

#### During Implementation (Awareness)

3. **Story 3.4 sprint review** — this technical story produces no user-visible demo. Plan the Epic 3 sprint review around Stories 3.5+ which deliver the first visible output (printed labels). Don't let Story 3.4 block the sprint demo.

### Recommended Implementation Order

The epics are correctly sequenced. Recommended start:

1. **Epic 1** — Start with Story 1.1 (project skeleton). Stories 1.1 → 1.7 → 1.8 establish the shared foundation. Stories 1.2–1.6 can be developed in parallel after 1.1 is complete.
2. **Epic 2** — Deliver the phase lifecycle engine. This is the state machine all other features depend on.
3. **Epics 3 & 6** — Can be developed in parallel (catalogue only reads items; deposit creates them).
4. **Epic 4** — Requires items from Epic 3.
5. **Epic 5** — Requires completed sales from Epic 4.

### Final Note

This assessment examined **94 FRs, 7 NFRs, 22 UX design requirements, and 16 architecture requirements** across 6 epics and 28 stories. **3 minor concerns** were identified — none blocking implementation. The planning artifacts are of high quality and the team can proceed with confidence.

**Report generated:** `_bmad-output/planning-artifacts/implementation-readiness-report-2026-06-12.md`
**Assessment date:** 2026-06-12
