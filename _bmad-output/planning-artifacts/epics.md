---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md'
---

# PluriBourse - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for PluriBourse, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

**F1 â€” Internationalisation (EN/FR)**
- FR-001: The user interface is available in English and French.
- FR-002: The default UI language is detected from the browser on first access and stored in the user account preferences.
- FR-003: Each user can change their language preference in account settings.
- FR-004: All UI text is externalized â€” no UI text is hardcoded in the source code.
- FR-005: The language of all printed documents is configured at the instance level by the admin.
- FR-006: The document language setting is instance-wide and applies to all editions.
- FR-007: The document language setting is modifiable by the admin at any time.

**F2 â€” Edition Management & Event Lifecycle**
- FR-008: The admin can create an edition with a free-form name.
- FR-009: Multiple editions can be created per year.
- FR-010: Only one edition can be active at a time.
- FR-011: Every phase transition â€” forward or backward â€” requires explicit admin confirmation via a dialog.
- FR-012: The active phase of the current edition is displayed clearly to all connected users.
- FR-013: The admin triggers edition closure via "Close Edition" in Post-sale phase. All documents are generated as PDFs in both languages. The edition becomes read-only.
- FR-014: An archived edition cannot be deleted.
- FR-015: Each edition's data is strictly isolated.
- FR-016: The commission rate is configured at instance setup (default 20%), modifiable by the admin until the Deposit phase starts, then frozen for that edition.
- FR-080: When creating a new edition, the admin can copy categories and table mapping from an existing edition.
- FR-082: The admin can roll back the active phase one step at a time. Data preserved. Rollback from Closed unavailable after Clean Edition.
- FR-088: Post-closure, the admin can trigger "Clean Edition" â€” permanently deletes item records. Requires explicit confirmation. Disables rollback to Post-sale.

**F3 â€” Seller & Product Management (Deposit Phase)**
- FR-017: The admin configures the list of item categories per edition.
- FR-018: The admin configures the category-to-table mapping per edition.
- FR-019: Seller profiles persist across editions. Mandatory fields: last name, first name, email, phone number.
- FR-020: The volunteer searches for an existing seller by name or email. If not found, a new profile is created.
- FR-021: The admin can delete a seller profile (GDPR). Deletion anonymizes PII across all editions.
- FR-022: For each item, the volunteer enters: name/description, price, category, complete/incomplete flag, and a comment if incomplete.
- FR-023: The table is automatically assigned by the system based on the edition's category-to-table mapping.
- FR-024: An item can be corrected or deleted only during the Deposit phase.
- FR-025: The complete/incomplete flag and its comment are modifiable in any phase.
- FR-026: A unique Code 128 barcode is generated server-side for each registered item.
- FR-027: The item label displays: Code 128 barcode graphic, human-readable barcode number, item name, price, category, table number, incompleteness indicator if applicable. No seller name (GDPR).
- FR-028: The system triggers label printing automatically when a volunteer validates a seller's deposit.
- FR-029: Print jobs are queued server-side and executed sequentially.
- FR-030: The printed roll follows: [seller separator: seller name + edition] â†’ [item label] â†’ [item separator] â†’ [item label] â†’ â€¦
- FR-031: A deposit slip is printable per seller: item list, unit prices, expected net payout after commission.
- FR-032: The thermal ticket width is configurable in admin settings (default: 57mm).
- FR-043: A volunteer can create a lot by assigning a name and a global price, then adding multiple items to it.
- FR-044: Each item in a lot has its own name/description and receives its own label.
- FR-045: A lot item label displays: "Lot price: Xâ‚¬" in place of individual price, and "Indivisible lot: X/N".

**F4 â€” Point of Sale (Sale Phase)**
- FR-033: The cashier interface allows sales via USB HID barcode scanner.
- FR-034: The scan component handles AZERTY/QWERTY keyboard layout differences transparently via key code mapping.
- FR-035: Each scanned item is added to the current buyer's basket. System displays item name and price.
- FR-036: Scanning an already-sold item displays an explicit error message. Item not added to basket.
- FR-037: Scanning an incomplete item displays an informative warning. Item can still be sold.
- FR-038: The cashier can remove one or more individual items from the basket before payment validation.
- FR-039: Payment validation marks all basket items as sold and closes the transaction. No modification after.
- FR-040: After validation, a buyer invoice is printable on demand.
- FR-041: The invoice displays: item list, unit prices, total, association name, edition name, date. A lot appears as a single line.
- FR-042: The application supports a minimum of 3 simultaneous cashier workstations without data conflicts.
- FR-046: Scanning an item belonging to a lot displays the lot name in red with "X/N scanned" counter.
- FR-047: The system blocks payment validation until the lot is complete (all N items scanned).
- FR-048: Once complete, the lot is sold at its global lot price.
- FR-081: If a cashier cannot complete a lot, they can remove the entire lot from the basket.
- FR-090: If the admin triggers a phase transition while a volunteer has an active basket, the system cancels the basket and displays an explicit error message.

**F5 â€” Post-Sale & Payouts**
- FR-049: In Post-sale phase, a sales summary ("Bilan de vente") is printable per seller.
- FR-050: The sales summary contains: sold items, unsold items with table location, gross total, commission, net payout. A lot appears as a single line.
- FR-051: To settle a seller, the volunteer enters the cash amount and clicks "Settle". Status changes to Settled.
- FR-052: "Not collected" button transfers the full amount owed to association revenue.
- FR-053: Unsettled sellers are identifiable with their phone number visible.

**F6 â€” Reporting**
- FR-054: A daily summary is generatable by the admin during Sale phase. Covers current calendar day: items sold/unsold, revenue, commission.
- FR-055: An edition summary is generated at edition closure: total items sold/unsold, gross revenue, total commission.
- FR-056: An outstanding sellers report lists unsettled sellers with phone number.
- FR-057: All reports are generated as PDF.
- FR-058: Reports are accessible to the admin only.
- FR-059: Archived editions display aggregate metrics and seller profiles in read-only mode. Item-level detail via PDF only.

**F7 â€” User Accounts & Access Control**
- FR-060: The admin creates, modifies, and deactivates volunteer accounts. Admin can reset a volunteer's password.
- FR-061: There is one admin account per instance.
- FR-062: At first launch, the admin account is initialized with Admin/Admin credentials. Admin is forced to change password on first login.
- FR-063: If the admin loses their password, a command run on the server generates a temporary password. Admin forced to change it on next login.
- FR-064: Admin and Volunteer roles are strictly separated. Admin cannot access volunteer interfaces.
- FR-065: The volunteer interface adapts to the active phase. In Post-sale, volunteer can print sales summary.
- FR-066: Sessions do not expire automatically.
- FR-067: Each account stores a UI language preference (EN/FR), detected from browser on creation, modifiable in settings.

**F8 â€” Infrastructure & Deployment**
- FR-068: The server runs on Linux, macOS, and Windows without code changes.
- FR-069: Minimum specification: Raspberry Pi 4 (2 GB RAM). SSD/USB storage strongly recommended.
- FR-070: The application is deployed via Docker Compose (Spring Boot + MariaDB). Data in persistent Docker volumes.
- FR-071: Updates applied with: `docker compose pull && docker compose up -d`. Data preserved.
- FR-072: Client workstations access the application via browser â€” no local installation required.
- FR-073: An admin settings page centralizes instance configuration: association name, commission rate, document language, thermal ticket width.
- FR-074: The installation guide targets non-technical users. Covers Docker installation, startup, initial configuration, password reset, and update procedure per OS (Linux, macOS, Windows).

**F9 â€” Print Infrastructure**
- FR-075: All printing is routed through the central server â€” no printer required on client workstations.
- FR-076: Thermal printer (item labels): connected via USB. Sequential print queue.
- FR-077: Standard printer (A4 documents): connected via USB. PDF sent directly to printer without preview.
- FR-078: A user triggers printing from the interface; processed by server, no action required on client.
- FR-079: In case of print error, the user is notified in the interface with an explicit message.

**F10 â€” Item Catalog**
- FR-083: A filterable, sortable item catalog is accessible to admin and volunteers during all phases of the active edition.
- FR-084: Catalog filtered by: name/description, barcode number, category, table, sold/unsold status, complete/incomplete flag, seller name.
- FR-085: Catalog sortable by any visible column.
- FR-086: Catalog displays items from active edition only. Not available after Clean Edition action.
- FR-087: During Sale phase, a volunteer can add an item from catalog directly to current basket (fallback for unreadable barcodes). Prevents adding already-sold or already-in-basket items.
- FR-089: Commission applies normally to items sold with incomplete flag.

### NonFunctional Requirements

- NFR-001: Performance â€” application is usable on Raspberry Pi 4 (2 GB RAM) without noticeable degradation under event load (3 simultaneous workstations, ~1,700 items).
- NFR-002: Concurrency â€” simultaneous operations from multiple workstations do not generate data conflicts.
- NFR-003: Financial Accuracy â€” payout calculations accurate to the cent. All monetary values use BigDecimal â€” never float or double.
- NFR-004: Browser Compatibility â€” interface works on any modern browser (Chrome, Firefox, Edge, Safari) on any OS.
- NFR-005: Scanner Compatibility â€” USB HID scanners function without configuration, regardless of keyboard layout (AZERTY/QWERTY).
- NFR-006: Reliability â€” no data loss on unexpected browser close or client workstation failure.
- NFR-007: GDPR â€” seller personal data deletable on request. Anonymized data in archived editions must not allow re-identification. No PII in application logs.

### Additional Requirements

Architecture-derived requirements that impact implementation:

- ARCH-001: Project scaffolding is the first implementation story â€” Spring Initializr (Spring Boot 4.0.6, Java 21, Maven) + `ng new pluribourse-frontend --standalone --routing --style=scss`.
- ARCH-002: Spring Session JDBC (MariaDB) for session persistence â€” sessions must survive container restarts during events.
- ARCH-003: Optimistic locking (`@Version` on `Item` entity) + DB UNIQUE constraint on sold item state for POS concurrency. Conflict detected at payment validation, returns 409 with conflicting item list.
- ARCH-004: Testcontainers (MariaDB) integration test required for POS concurrency before F4 ships â€” H2 locking behavior differs from MariaDB.
- ARCH-005: JPageFlow (`FilterService.filterData()`) for all paginated/filterable list endpoints. Known bug: BigDecimal sort broken in v1.5.0 â€” fix required before price-sort feature.
- ARCH-006: Liquibase migrations: 4 initial changesets â€” 001-core-schema (users + seller_profile_id nullable FK), 002-spring-session, 003-category-table-mapping, 004-instance-config.
- ARCH-007: MapStruct for all entityâ†”DTO mapping (manually added post Spring Initializr, not in Initializr UI).
- ARCH-008: OpenPDF 3.0.0 (LGPL) for all PDF generation. iText 7 (AGPL) explicitly rejected.
- ARCH-009: escpos-coffee (or equivalent) for ESC/POS thermal printing. Two independent LinkedBlockingQueue instances (thermal / A4) â€” at-most-once delivery, re-triggerable from UI.
- ARCH-010: ZXing for Code 128 barcode generation (Apache 2.0).
- ARCH-011: Role `SELLER` declared in the codebase and blocked at 403 in v1 via `SecurityConfig`. No SELLER endpoints or UI until v2.
- ARCH-012: SSE (`SseEmitterRegistry`) must be initialized before phase transition endpoints. Events: `phase-changed` (payload: editionId, newPhase, previousPhase) and `basket-cancelled`.
- ARCH-013: RFC 7807 Problem Details for all error responses via `@ControllerAdvice`.
- ARCH-014: Springdoc OpenAPI enabled in `dev` profile only, disabled in `prod`.
- ARCH-015: Cross-component build order â€” Phase state machine (F2) must be implemented before F3, F4, F5, F10. Spring Session JDBC requires Liquibase migration before any auth feature. Print queue consumers must be Spring beans before F3/F4 printing.
- ARCH-016: ESC/POS label format: seller separator â†’ item label (Code 128 bitmap, barcode number, name, price, category, table, incompleteness indicator) â†’ item separator â†’ â€¦

### UX Design Requirements

- UX-DR1: Implement Angular Material 3 global theme with all DESIGN.md design tokens: coral primary (`#C44626` light / `#F07040` dark), warm beige surfaces (`#FFFBF9` light / `#1A0C06` dark), sidebar-bg (`#2A100A`), semantic status colors (success green `#166534`/`#F0FDF4`, warning coral-container, error red `#BA1A1A`/`#FFDAD6`), elevation tokens (3 levels), shape/rounded tokens (5 levels: 4/8/12/20/999px), spacing scale (base-4: 4/8/16/24/32/48/64px).
- UX-DR2: Implement DM Sans font (Google Fonts, SIL OFL) with 8-level typography scale â€” display (32px/700) to label-sm (12px/600 uppercase). Minimum font size 12px enforced.
- UX-DR3: Implement `AppLayoutComponent` with fixed topbar (56px height) + optional sidebar (200px width, admin only, non-collapsable in v1) + content zone (24px padding, max 640px for forms, unlimited for tables).
- UX-DR4: Implement phase chip component (topbar center): pill rounded-full, primary-container background, â— coral indicator, real-time SSE update with 150ms fade transition. Clickable admin (â†’ phase control page), non-clickable volunteer. aria-label "Phase actuelle : [phase]".
- UX-DR5: Implement role badge component (topbar right): pill rounded-full, admin style (primary-container), volunteer style (surface-variant), label-sm uppercase.
- UX-DR6: Implement confirm dialog component: rounded-xl, elevation level-3, 50% dark overlay, title + consequence description + confirm button + cancel (ghost) button, focus trapped, initial focus on cancel button, Echap closes.
- UX-DR7: Implement inline notification component: primary-container background, 3px left border coral, Material Symbols `warning` icon, appears below triggering element in flow (not toast), persists until resolution.
- UX-DR8: Implement toast component: bottom-right position, success (4s auto-dismiss), error system (persistent until interaction), max 1 simultaneous toast.
- UX-DR9: Implement sidebar navigation component (admin only): dark background sidebar-bg `#2A100A`, flat navigation (no submenus), sections separated by label-sm uppercase labels ("Ã‰dition active" / "Gestion"), active item determined by current route (primary coral background + white text), Material Symbols 18px icons.
- UX-DR10: Implement scanner input component: auto-focused on caisse open, auto-refocus after 500ms keyboard inactivity, AZERTY/QWERTY key code mapping, Enter/`\n` triggers processing, no debounce, aria-label "Scanner ou saisir un code-barres", aria-live="polite" on scan result zone.
- UX-DR11: Implement filterable/sortable list pattern with `MatPaginator` (default page size 50), column header click sorting (â†‘â†“ indicator), inline filters above list. Used by item catalog and vendor list.
- UX-DR12: Implement skeleton loading state (3â€“5 Angular Material skeleton rows) for lists during initial data load. No global spinner.
- UX-DR13: Implement empty state component: centered Material Symbol icon + descriptive phrase + primary action button. Always offers an exit. Used by vendor list, catalog, results filtered empty (with "Clear filters" action).
- UX-DR14: Implement basket POS component: item list with name + unit price, individual remove button (close icon per line), lot grouping (lot header in red with "X/N scannÃ©s" counter + lot subtotal, no individual item price), "Retirer le lot entier" button from first lot item, "Valider" blocked if lot incomplete, basket auto-cleared on SSE basket-cancelled.
- UX-DR15: Implement deposit form flow (volunteer): seller search by name/email â†’ "CrÃ©er un profil" if not found â†’ item registration (name, price, category selector, complete/incomplete checkbox + comment field) with auto-assigned table display. Autofocus on seller search field on page load.
- UX-DR16: Implement categories & tables admin component: editable mode before Deposit phase starts, read-only after. On new edition: "Copy from existing edition" (edition selector dropdown) or "Configure manually" option.
- UX-DR17: Implement reports admin page with phase-conditional content sections: daily summary section (Sale phase only, refresh button), synthesis section (Post-sale + Closed, read-only), CSV export buttons (catalog + payouts, Post-sale + Closed, direct download no dialog), printable outstanding sellers list (Post-sale, opens browser print view).
- UX-DR18: Implement "Clean Edition" action: secondary error-color button, irreversible confirmation dialog ("Supprimer tous les articles de cette Ã©dition. Cette action est irrÃ©versible."), post-clean empty state "Ã‰dition nettoyÃ©e â€” aucun article." without action, button disappears after clean. Button visible only if items still exist.
- UX-DR19: Implement print button feedback pattern: spinner in button during queue submission, success toast (4s), persistent error toast if printer offline with "Fermer" button. Always re-triggerable.
- UX-DR20: Implement WCAG 2.2 AA accessibility floor: focus rings on all interactive elements (never suppressed), tab order following visual reading order, focus trap in confirmation dialogs, screen reader announcements via aria-live/aria-label/aria-describedby, 44Ã—44px minimum touch targets, decorative icons aria-hidden="true", semantic icons with accompanying text or aria-label.
- UX-DR21: Implement phase transition handling in volunteer POS interface: SSE `basket-cancelled` event â†’ persistent toast "La phase a changÃ©. Votre panier a Ã©tÃ© annulÃ©." â†’ basket cleared â†’ scanner disabled until page reload.
- UX-DR22: Implement settlement/payout print button on volunteer settlement list (per vendor row, after settlement) and on admin vendor detail page. Spinner feedback during queue, toast on result.

### FR Coverage Map

- FR-001: Epic 1 â€” i18n UI available in EN/FR
- FR-002: Epic 1 â€” Browser language detection â†’ user preference
- FR-003: Epic 1 â€” User can change language preference in account settings
- FR-004: Epic 1 â€” All UI text externalized (no hardcoded strings)
- FR-005: Epic 1 â€” Document language configured at instance level
- FR-006: Epic 1 â€” Document language is instance-wide
- FR-007: Epic 1 â€” Document language modifiable by admin
- FR-008: Epic 2 â€” Admin creates edition with free-form name
- FR-009: Epic 2 â€” Multiple editions per year
- FR-010: Epic 2 â€” Only one active edition at a time
- FR-011: Epic 2 â€” Phase transition requires confirmation dialog
- FR-012: Epic 2 â€” Active phase displayed to all users
- FR-013: Epic 2 â€” Edition closure generates PDFs, edition becomes read-only
- FR-014: Epic 2 â€” Archived edition cannot be deleted
- FR-015: Epic 2 â€” Edition data strictly isolated
- FR-016: Epic 2 â€” Commission rate frozen once Deposit phase starts
- FR-017: Epic 3 â€” Admin configures item categories per edition
- FR-018: Epic 3 â€” Admin configures category-to-table mapping
- FR-019: Epic 3 â€” Seller profiles persist across editions
- FR-020: Epic 3 â€” Volunteer searches/creates seller profiles
- FR-021: Epic 3 â€” Admin can delete seller profile (GDPR anonymization)
- FR-022: Epic 3 â€” Volunteer enters item details
- FR-023: Epic 3 â€” Table auto-assigned from category mapping
- FR-024: Epic 3 â€” Item correctable/deletable during Deposit phase only
- FR-025: Epic 3 â€” Complete/incomplete flag modifiable in any phase
- FR-026: Epic 3 â€” Code 128 barcode generated server-side per item
- FR-027: Epic 3 â€” Item label format (barcode, name, price, category, table, incompleteness)
- FR-028: Epic 3 â€” Labels printed automatically on deposit validation
- FR-029: Epic 3 â€” Print jobs queued sequentially server-side
- FR-030: Epic 3 â€” Thermal roll format: seller separator â†’ item labels
- FR-031: Epic 3 â€” Deposit slip printable per seller
- FR-032: Epic 3 â€” Thermal ticket width configurable (default 57mm)
- FR-033: Epic 4 â€” Cashier interface with USB HID scanner
- FR-034: Epic 4 â€” AZERTY/QWERTY transparent handling via key code mapping
- FR-035: Epic 4 â€” Scanned item added to basket with name and price
- FR-036: Epic 4 â€” Already-sold item scan: error message, not added
- FR-037: Epic 4 â€” Incomplete item scan: warning, still sellable
- FR-038: Epic 4 â€” Cashier can remove items from basket before validation
- FR-039: Epic 4 â€” Payment validation marks items sold, closes transaction
- FR-040: Epic 4 â€” Buyer invoice printable on demand after validation
- FR-041: Epic 4 â€” Invoice format: item list, prices, total, association, edition, date
- FR-042: Epic 4 â€” Minimum 3 simultaneous cashier workstations without conflicts
- FR-043: Epic 3 â€” Volunteer can create a lot with global price + multiple items
- FR-044: Epic 3 â€” Each lot item has its own name and label
- FR-045: Epic 3 â€” Lot item label shows lot price and "Indivisible lot: X/N"
- FR-046: Epic 4 â€” Scanning lot item shows lot name in red + "X/N scanned" counter
- FR-047: Epic 4 â€” Validation blocked until lot is complete
- FR-048: Epic 4 â€” Complete lot sold at global lot price
- FR-049: Epic 5 â€” Sales summary printable per seller in Post-sale phase
- FR-050: Epic 5 â€” Sales summary: sold items, unsold items + table, gross total, commission, net payout
- FR-051: Epic 5 â€” Volunteer settles seller: enters cash amount, clicks Settle
- FR-052: Epic 5 â€” "Not collected" button transfers payout to association revenue
- FR-053: Epic 5 â€” Unsettled sellers identifiable with phone number
- FR-054: Epic 5 â€” Daily summary generatable by admin during Sale phase
- FR-055: Epic 5 â€” Edition summary generated at edition closure
- FR-056: Epic 5 â€” Outstanding sellers report (unsettled + phone number)
- FR-057: Epic 5 â€” All reports generated as PDF
- FR-058: Epic 5 â€” Reports accessible to admin only
- FR-059: Epic 5 â€” Archived editions: aggregate metrics read-only, item detail via PDF only
- FR-060: Epic 1 â€” Admin creates/modifies/deactivates volunteer accounts, resets passwords
- FR-061: Epic 1 â€” One admin account per instance
- FR-062: Epic 1 â€” First launch: Admin/Admin credentials, force password change
- FR-063: Epic 1 â€” Admin password reset via server CLI command
- FR-064: Epic 1 â€” Admin/Volunteer roles strictly separated
- FR-065: Epic 1 â€” Volunteer interface adapts to active phase
- FR-066: Epic 1 â€” Sessions do not expire automatically
- FR-067: Epic 1 â€” Each account stores UI language preference (EN/FR)
- FR-068: Epic 1 â€” Server runs on Linux, macOS, Windows without code changes
- FR-069: Epic 1 â€” Minimum spec: Raspberry Pi 4 (2 GB RAM)
- FR-070: Epic 1 â€” Deployed via Docker Compose, data in persistent volumes
- FR-071: Epic 1 â€” Updates via `docker compose pull && docker compose up -d`
- FR-072: Epic 1 â€” Client workstations access via browser, no local install
- FR-073: Epic 1 â€” Admin settings page: association name, commission rate, doc language, ticket width
- FR-074: Epic 1 â€” Installation guide for non-technical users, per OS (Linux/macOS/Windows)
- FR-075: Epic 3 â€” All printing routed through central server
- FR-076: Epic 3 â€” Thermal printer (labels) via USB, sequential queue
- FR-077: Epic 3 â€” Standard printer (A4) via USB, PDF sent directly
- FR-078: Epic 3 â€” User triggers printing; no action required on client
- FR-079: Epic 3 â€” Print error: explicit user notification in UI
- FR-080: Epic 2 â€” New edition can copy categories/table mapping from existing edition
- FR-081: Epic 4 â€” Cashier can remove entire lot from basket
- FR-082: Epic 2 â€” Admin can roll back phase one step at a time, data preserved
- FR-083: Epic 6 â€” Filterable/sortable item catalog accessible during all phases
- FR-084: Epic 6 â€” Catalog filters: name, barcode, category, table, sold/unsold, complete/incomplete, seller
- FR-085: Epic 6 â€” Catalog sortable by any visible column
- FR-086: Epic 6 â€” Catalog shows active edition only; not available after Clean Edition
- FR-087: Epic 6 â€” During Sale phase: add item from catalog to basket (scanner fallback)
- FR-088: Epic 2 â€” "Clean Edition" permanently deletes item records; disables rollback to Post-sale
- FR-089: Epic 5 â€” Commission applies normally to items sold with incomplete flag
- FR-090: Epic 4 â€” Phase transition while basket active: basket cancelled, explicit message to volunteer

## Epic List

### Epic 1: Application Foundation, Auth & i18n
Admins and volunteers can deploy the application, log in with appropriate roles, manage user accounts, configure the instance, and use the application in their preferred language (EN/FR). All shared UI components and the Angular Material design system are in place.

**FRs covered:** FR-001â€“007, FR-060â€“067, FR-068â€“074
**Architecture:** ARCH-001, ARCH-002, ARCH-006, ARCH-007, ARCH-011, ARCH-013, ARCH-014
**UX:** UX-DR1, UX-DR2, UX-DR3, UX-DR5, UX-DR6, UX-DR7, UX-DR8, UX-DR9, UX-DR12, UX-DR13, UX-DR20

### Epic 2: Edition Lifecycle Management
Admins can create editions, drive the full phase lifecycle (Deposit â†’ Sale â†’ Post-sale â†’ Closed), roll back phases, and close/clean editions. All connected users see the active phase in real-time via SSE.

**FRs covered:** FR-008â€“016, FR-080, FR-082, FR-088
**Architecture:** ARCH-012, ARCH-015 (phase machine prerequisite)
**UX:** UX-DR4, UX-DR18

### Epic 3: Seller Registration & Deposit
Volunteers can register sellers and all their items (including lots) with automatic table assignment, and print labels and deposit slips via the centralized thermal printer.

**FRs covered:** FR-017â€“032, FR-043â€“045, FR-075â€“079
**Architecture:** ARCH-003, ARCH-008, ARCH-009, ARCH-010, ARCH-015 (print queue prerequisite), ARCH-016
**UX:** UX-DR15, UX-DR16, UX-DR19, UX-DR22

### Epic 4: Point of Sale
Volunteers can scan items with a USB barcode scanner, manage baskets with full lot support, complete sales, and print buyer invoices â€” safely across multiple simultaneous workstations.

**FRs covered:** FR-033â€“042, FR-046â€“048, FR-081, FR-090
**Architecture:** ARCH-003 (concurrency validation), ARCH-004
**UX:** UX-DR10, UX-DR14, UX-DR21

### Epic 5: Post-Sale, Payouts & Reporting
Volunteers can settle sellers and process payouts. Admins can generate daily and edition summary reports as PDFs, identify unsettled sellers, and officially close editions.

**FRs covered:** FR-049â€“059, FR-089
**UX:** UX-DR17, UX-DR22

### Epic 6: Item Catalog
Admins and volunteers can browse, search, and filter all items in the active edition across all phases. During Sale phase, volunteers can add items directly to the basket from the catalog as a scanner fallback.

**FRs covered:** FR-083â€“087
**Architecture:** ARCH-005
**UX:** UX-DR11

---

## Epic 1: Application Foundation, Auth & i18n

Admins and volunteers can deploy the application, log in with appropriate roles, manage user accounts, configure the instance, and use the application in their preferred language (EN/FR). All shared UI components and the Angular Material design system are in place.

### Story 1.1: Project Scaffolding & Docker Compose Baseline

As a developer,
I want the full technology stack initialized with Docker Compose and a running development environment,
So that feature development can begin on a stable, reproducible foundation.

**Acceptance Criteria:**

**Given** the repository is cloned
**When** `docker compose up -d` is run
**Then** the Spring Boot app starts and responds at `/actuator/health`
**And** the Angular dev server starts at `http://localhost:4200`
**And** the MariaDB container runs with a persistent volume

**Given** the Spring Boot app starts
**When** Liquibase migrations run
**Then** the `users` table exists with all fields including `preferred_language` and nullable `seller_profile_id` FK
**And** Spring Session JDBC tables exist
**And** a default admin account (username: "Admin", BCrypt hash of "Admin") is seeded

**Given** the application returns an error
**When** any endpoint produces a 4xx or 5xx
**Then** the response follows RFC 7807 Problem Details format (`type`, `title`, `status`, `detail`, `instance`)

**Given** the `dev` Spring profile is active
**When** `/swagger-ui.html` is accessed
**Then** the Springdoc OpenAPI UI is available

**Given** the `prod` Spring profile is active
**When** `/swagger-ui.html` is accessed
**Then** a 404 is returned

### Story 1.2: Spring Security Authentication & Role-Based Access Control

As an admin,
I want to log in with my credentials and have role-enforced access to admin pages,
So that the application is secure and admin/volunteer interfaces are strictly separated from the start.

**Acceptance Criteria:**

**Given** the application is freshly deployed
**When** any user navigates to a protected route
**Then** they are redirected to `/login`

**Given** the admin submits "Admin" / "Admin" on first login
**When** authentication succeeds
**Then** the system redirects immediately to a mandatory password-change page
**And** access to all other pages is blocked until the password is changed
**And** the session is stored in the `spring_session` MariaDB table

**Given** a session is established
**When** the server container is restarted
**Then** the session survives and the user remains logged in (FR-066)

**Given** a VOLUNTEER attempts to access `/admin/*`
**When** the request is processed
**Then** a 403 is returned

**Given** any request from a user with role SELLER
**When** processed by Spring Security
**Then** a 403 is returned regardless of the endpoint

**Given** an admin logs out
**When** `/logout` is called
**Then** the session is invalidated in the database

### Story 1.3: Volunteer Account Management

As an admin,
I want to create, modify, deactivate volunteer accounts, and reset their passwords,
So that I control who has access to the application during the event.

**Acceptance Criteria:**

**Given** the admin navigates to `/admin/users`
**When** the page loads
**Then** all volunteer accounts are listed with name, status (active/inactive), and role badge

**Given** the admin fills in first name, last name, username, and password for a new volunteer
**When** the form is submitted
**Then** a VOLUNTEER account is created and the volunteer can log in immediately

**Given** the admin resets a volunteer's password
**When** the reset is submitted
**Then** the volunteer's password is updated
**And** the volunteer is forced to change it on next login

**Given** the admin deactivates a volunteer account
**When** that volunteer attempts to log in
**Then** login is rejected with a clear "Account disabled" message

**Given** one admin account already exists
**When** the admin attempts to create a second admin account
**Then** the system rejects it with an explicit error (FR-061: one admin per instance)

### Story 1.4: Admin Password Recovery CLI

As an admin who has forgotten their password,
I want to reset it via a server-side command,
So that I can regain access without developer intervention or direct database manipulation.

**Acceptance Criteria:**

**Given** the admin has forgotten their password
**When** they run the app with the `--reset-admin-password` argument
**Then** a new temporary password (12+ chars, alphanumeric) is printed to the console
**And** the admin account password is updated in the database (BCrypt)
**And** a force-password-change flag is set on the account

**Given** the temporary password has been generated
**When** the admin logs in with it
**Then** they are immediately redirected to the mandatory password-change page
**And** they cannot access any other page until the password is changed

### Story 1.5: Instance Configuration & Admin Settings Page

As an admin,
I want to configure the instance settings in a dedicated page,
So that the application reflects my association's identity and operational parameters.

**Acceptance Criteria:**

**Given** the admin navigates to `/admin/settings`
**When** the page loads
**Then** the current configuration is displayed: association name, commission rate (default 20%), document language (EN/FR), thermal ticket width (default 57mm)

**Given** the admin updates the association name and saves
**When** the server restarts
**Then** the association name is preserved (persisted in DB)

**Given** the admin sets the commission rate to 15% and saves
**When** the value is stored
**Then** the stored value is a BigDecimal `15.00` (not float or double)

**Given** the admin sets document language to "FR"
**When** a PDF is later generated
**Then** the PDF content uses entries from `messages_fr.properties`

**Given** the admin changes the thermal ticket width and saves
**When** a print job is later sent
**Then** the new width (BigDecimal, mm) is used for that print job

### Story 1.6: User Language Preference & i18n Infrastructure

As a user,
I want the application to display in my preferred language (English or French),
So that I can work comfortably in my native language during the event.

**Acceptance Criteria:**

**Given** a new user accesses the app for the first time with browser language `fr`
**When** the page loads
**Then** the interface displays in French
**And** `preferredLanguage: FR` is stored on their user account

**Given** a new user's browser is set to English or any unsupported language
**When** the page loads
**Then** the interface displays in English and `preferredLanguage: EN` is stored

**Given** a logged-in user goes to `/account` and selects the other language
**When** they save the preference
**Then** the interface switches to the selected language immediately (no page reload)
**And** the preference survives logout and login

**Given** any page renders
**When** any visible text is inspected
**Then** all text originates from `en.json` or `fr.json` translation keys â€” no hardcoded strings (FR-004)
**And** i18n keys follow the `feature.section.key` format (max 3 levels)

**Given** a PDF document is generated
**When** the instance document language is "FR"
**Then** all document text uses entries from `messages_fr.properties`

### Story 1.7: Angular Material Design System & Application Layout

As a user navigating the application,
I want a consistent, role-adapted visual design with clear navigation,
So that I can find what I need instantly under event-day pressure.

**Acceptance Criteria:**

**Given** any authenticated user loads any page
**When** the page renders
**Then** the topbar (56px) is visible with logo left, role badge top-right, phase chip center (static at this stage)
**And** the DM Sans font and coral primary `#C44626` are applied consistently

**Given** an admin is logged in
**When** any admin page loads
**Then** the sidebar (200px, background `#2A100A`) is shown with sections "Edition active" / "Gestion" and flat navigation links
**And** the currently active route is highlighted in coral (`#C44626` background, white text)

**Given** a volunteer is logged in
**When** any volunteer page loads
**Then** no sidebar is shown

**Given** an action button is rendered as a primary action
**When** the button appears
**Then** it uses the coral filled style
**And** at most one primary (filled coral) button appears per visible section

**Given** the Angular Material theme is applied
**When** rendered in Chrome, Firefox, Edge, or Safari on Linux/macOS/Windows
**Then** colors, typography, elevation, and rounded corners match the DESIGN.md token specifications

### Story 1.8: Shared UI Components â€” Dialogs, Notifications & Accessibility

As a user performing operations in the application,
I want clear feedback, accessible confirmations, and helpful empty states,
So that I can act confidently without making accidental mistakes under pressure.

**Acceptance Criteria:**

**Given** an irreversible action is triggered
**When** the confirm dialog appears
**Then** it shows a title, consequence description, confirm button, and cancel (ghost) button
**And** focus is trapped inside the dialog
**And** initial focus is on the cancel button
**And** pressing Echap closes the dialog without acting

**Given** a successful operation completes
**When** the result is returned
**Then** a success toast appears bottom-right for 4 seconds then disappears automatically
**And** at most one toast is visible at any time

**Given** a system error occurs (printer offline, network failure)
**When** surfaced to the user
**Then** a persistent error toast appears bottom-right with a "Fermer" button that must be clicked to dismiss

**Given** a business error occurs inline within a workflow
**When** the error is triggered
**Then** an inline notification appears directly below the triggering element (not a toast)
**And** it persists until the error is resolved or a new action is taken

**Given** a list is loading initial data
**When** the API request is in progress
**Then** 3-5 skeleton rows are displayed and no global spinner blocks the interface

**Given** a list has no items
**When** the empty state renders
**Then** a centered Material icon, descriptive message, and primary action button (where applicable) are shown

**Given** any element is focused via keyboard Tab
**When** focus lands on any button, link, or input
**Then** a visible focus ring (coral primary, never suppressed) is shown
**And** all interactive elements have a minimum 44x44px touch target
**And** decorative icons have `aria-hidden="true"`
**And** semantic icons have an `aria-label` or visible text label

---

## Epic 2: Edition Lifecycle Management

Admins can create editions, drive the full phase lifecycle (Deposit -> Sale -> Post-sale -> Closed), roll back phases, and close/clean editions. All connected users see the active phase in real-time via SSE.

### Story 2.1: Edition CRUD & Commission Rate Configuration

As an admin,
I want to create and manage editions with a free-form name and configurable commission rate,
So that each event is properly identified and financially configured before sellers arrive.

**Acceptance Criteria:**

**Given** the admin navigates to `/admin/editions`
**When** the page loads
**Then** all editions are listed with name, creation date, and current phase

**Given** the admin fills in an edition name and submits
**When** the form is submitted
**Then** a new edition is created with phase "Deposit" and commission rate 20% (default)

**Given** no active edition exists
**When** the admin activates an edition
**Then** it becomes the active edition

**Given** one edition is already active
**When** the admin attempts to activate a second edition
**Then** the system rejects it with an explicit error (FR-010)

**Given** an edition is in Deposit phase (not yet started)
**When** the admin changes the commission rate to 15%
**Then** the rate is saved as BigDecimal `15.00`

**Given** an edition has entered Deposit phase
**When** the admin attempts to modify the commission rate
**Then** the system rejects it with an explicit error (FR-016: rate frozen once Deposit starts)

**Given** an archived edition exists
**When** the admin attempts to delete it
**Then** the system rejects the deletion (FR-014)

### Story 2.2: Phase Lifecycle Control & Confirmation Dialogs

As an admin,
I want to advance or roll back the edition phase with explicit confirmation,
So that phase transitions are intentional and their consequences are clearly communicated.

**Acceptance Criteria:**

**Given** the admin is on `/admin/editions/:id/phase`
**When** the page loads
**Then** the current phase is clearly displayed with available forward and backward transition buttons

**Given** the admin clicks a phase transition button
**When** the confirm dialog appears
**Then** it names the destination phase and describes the main consequence
**And** two buttons are shown: confirm (primary) and cancel (ghost)

**Given** the admin confirms a forward phase transition
**When** the transition completes
**Then** the edition phase is updated in the database
**And** the phase chip in the topbar reflects the new phase for all users

**Given** the admin confirms a rollback transition
**When** the transition completes
**Then** the phase is rolled back one step
**And** all data recorded in the rolled-back phase is preserved (FR-082)

**Given** an edition has been closed and Clean Edition was triggered
**When** the admin views the phase control page
**Then** the rollback from Closed button is absent (FR-082: rollback disabled after Clean)

**Given** a phase transition completes
**When** the server processes it
**Then** an SSE event `phase-changed` is broadcast with `{editionId, newPhase, previousPhase}`

### Story 2.3: Edition Categories & Table Mapping

As an admin,
I want to configure item categories and their table assignments per edition,
So that items are automatically routed to the correct tables during deposit.

**Acceptance Criteria:**

**Given** the admin opens a new edition's categories page `/admin/editions/:id/categories`
**When** the page loads
**Then** the categories list is empty and editable
**And** an option "Copy from existing edition" is available with an edition selector dropdown

**Given** the admin selects "Copy from existing edition" and confirms
**When** the copy completes
**Then** all categories and table mappings from the selected edition are applied to the new edition (FR-080)

**Given** the admin adds a category (e.g. "Jouets") assigned to tables 1, 2, 3
**When** saved
**Then** items in that category will be auto-assigned to tables 1-3

**Given** the edition has not yet entered Deposit phase
**When** the admin edits categories and table mapping
**Then** edits are saved immediately

**Given** the edition has entered Deposit phase
**When** the admin opens the categories page
**Then** the page is read-only with a banner indicating "Categories locked"

### Story 2.4: Real-Time Phase Notification via SSE

As a volunteer,
I want to see the active phase update in real-time in the topbar without refreshing,
So that I always know which interface I should be using without manual page reloads.

**Acceptance Criteria:**

**Given** a volunteer is logged in and connected
**When** the admin transitions the edition to Sale phase
**Then** the phase chip in the volunteer topbar updates within 2 seconds
**And** the chip uses a 150ms fade transition

**Given** the Angular app initializes
**When** a user logs in
**Then** `PhaseService` opens an `EventSource` connection to `GET /api/sse/events`
**And** the current phase is loaded as a `Signal<Phase>` from an initial REST call

**Given** the SSE connection is interrupted
**When** connectivity is restored
**Then** `EventSource` auto-reconnects without requiring user action

**Given** an admin transitions a phase
**When** the SSE event is broadcast
**Then** all connected clients receive the `phase-changed` event
**And** the `SseEmitterRegistry` closes the emitter after broadcasting

### Story 2.5: Edition Closure & Clean Edition

As an admin,
I want to officially close an edition and optionally clean its item records,
So that the edition is properly archived and storage can be freed after the event.

**Acceptance Criteria:**

**Given** the edition is in Post-sale phase
**When** the admin clicks "Close Edition" and confirms
**Then** the edition phase changes to "Closed" and becomes read-only
**And** edition summary PDFs are generated in both EN and FR (FR-013)

**Given** the edition is Closed and item records exist
**When** the admin views the edition detail
**Then** a "Clean Edition" button is visible (secondary error-color style)

**Given** the admin clicks "Clean Edition" and confirms
**When** the action completes
**Then** all item records for that edition are permanently deleted
**And** the "Clean Edition" button disappears
**And** the catalog shows "Edition cleaned - no items." empty state
**And** rollback from Closed is permanently disabled for this edition (FR-088)

**Given** a Closed edition has been cleaned
**When** the admin views the edition
**Then** aggregate metrics (total sales, revenue, commission) remain visible in read-only mode (FR-059)

---

## Epic 3: Seller Registration & Deposit

Volunteers can register sellers and all their items (including lots) with automatic table assignment, and print labels and deposit slips via the centralized thermal printer.

### Story 3.1: Seller Profile Management

As a volunteer,
I want to search for existing sellers and register new seller profiles,
So that sellers can be associated with their items without re-entering their information each edition.

**Acceptance Criteria:**

**Given** the volunteer is on the deposit page `/volunteer/deposit`
**When** the page loads
**Then** the seller search field receives focus automatically

**Given** the volunteer types a name or email
**When** characters are entered
**Then** matching seller profiles appear in real-time

**Given** no matching seller is found
**When** the volunteer sees the empty result
**Then** a "Create new profile" button is displayed

**Given** the volunteer fills in last name, first name, email, and phone
**When** the form is submitted
**Then** a new seller profile is created and immediately selectable for item registration

**Given** a seller profile exists from a previous edition
**When** the volunteer selects it
**Then** the profile is reused â€” no duplicate is created (FR-019 cross-edition persistence)

**Given** the admin triggers a GDPR deletion on a seller profile
**When** the deletion completes
**Then** last name, first name, email, and phone are anonymized across all editions (FR-021)
**And** item descriptions belonging to this seller are also anonymized
**And** product categories are retained
**And** no PII appears in application logs

### Story 3.2: Item Registration & Auto-Table Assignment

As a volunteer,
I want to register items for a seller with automatic table assignment,
So that items are correctly cataloged and physically located during the event.

**Acceptance Criteria:**

**Given** a seller is selected and the volunteer enters an item
**When** they fill in name/description, price, category, and complete/incomplete flag
**Then** the table is automatically assigned from the edition category-to-table mapping (FR-023)
**And** the assigned table number is displayed immediately

**Given** the volunteer checks "Incomplete" for an item
**When** saving the item
**Then** a comment field is required
**And** the incompleteness indicator is stored with the item

**Given** an item is registered during Deposit phase
**When** the volunteer edits its name, price, or category
**Then** the change is saved and the table is re-assigned if the category changed (FR-024)

**Given** an item is registered during Deposit phase
**When** the volunteer deletes it
**Then** the item is removed from the seller list (FR-024)

**Given** the edition has advanced past Deposit phase
**When** a volunteer attempts to edit or delete an item
**Then** the action is blocked with an explicit message

**Given** an item exists in any phase
**When** a volunteer modifies the complete/incomplete flag or comment
**Then** the change is saved immediately (FR-025)
**And** all prices are stored as BigDecimal (NFR-003)

### Story 3.3: Lot Creation & Management

As a volunteer,
I want to group items into an indivisible lot with a single global price,
So that sets sold together are treated as an atomic unit during the sale.

**Acceptance Criteria:**

**Given** the volunteer is registering items for a seller
**When** they choose to create a lot
**Then** they can enter a lot name and a global price (BigDecimal)
**And** they can add multiple items to the lot, each with its own name/description (FR-043, FR-044)

**Given** a lot contains multiple items
**When** the lot is saved
**Then** each item has its own barcode generated (one label per item, FR-044)
**And** lot items inherit auto-table assignment from their category

**Given** a lot item label is generated
**When** rendered
**Then** it shows "Lot price: Xâ‚¬" instead of individual price
**And** "Indivisible lot: X/N" where X is the item position and N is the total (FR-045)

### Story 3.4: Print Infrastructure â€” Server-Side Queues

As a volunteer who triggers printing,
I want print jobs to be processed server-side without any printer on my workstation,
So that printing works from any browser-connected workstation during the event.

**Acceptance Criteria:**

**Given** the Spring Boot application starts
**When** the context is initialized
**Then** two `LinkedBlockingQueue` beans exist: one for thermal labels, one for A4 documents
**And** each queue has a dedicated consumer thread running as a Spring bean

**Given** multiple print jobs are submitted concurrently
**When** they enter the thermal queue
**Then** jobs are executed sequentially â€” one at a time (FR-029)

**Given** a user triggers printing from the interface
**When** the request is received
**Then** a spinner appears in the print button during queue submission (UX-DR19)
**And** no action is required on the client workstation (FR-078)

**Given** a print job completes successfully
**When** the consumer thread finishes
**Then** a success toast appears for 4 seconds

**Given** the printer is offline or errors
**When** a print job fails
**Then** a persistent error toast appears: "The [thermal/A4] printer is not responding. Check the USB connection." (FR-079)
**And** the print action remains re-triggerable from the interface

### Story 3.5: Thermal Label Generation & Printing

As a volunteer validating a seller deposit,
I want item labels to be automatically printed on the thermal printer,
So that items are physically labeled immediately after deposit without manual steps.

**Acceptance Criteria:**

**Given** an item is registered
**When** saved
**Then** a unique Code 128 barcode is generated server-side using ZXing (FR-026)

**Given** a deposit is validated
**When** validation completes
**Then** all labels for that seller are automatically queued for thermal printing (FR-028)
**And** the roll format is: seller separator (name + edition) -> item label -> item separator -> item label -> ... (FR-030)

**Given** a label is generated for a standard item
**When** rendered for ESC/POS
**Then** it displays: Code 128 barcode graphic (bitmap), human-readable barcode number, item name (wrapping if needed), price, category, table number, incompleteness indicator if applicable
**And** no seller name appears on the label (GDPR, FR-027)

**Given** a label is generated for a lot item
**When** rendered
**Then** it shows "Lot price: Xâ‚¬" and "Indivisible lot: X/N" (FR-045)

**Given** the instance config has a thermal ticket width set
**When** the ESC/POS job is prepared
**Then** that width is applied (FR-032, default 57mm)

### Story 3.6: Deposit Slip PDF Generation & Printing

As a volunteer completing a deposit,
I want to print a deposit slip per seller,
So that the seller has a paper record of what they deposited and how much they will receive.

**Acceptance Criteria:**

**Given** a seller deposit is complete
**When** the volunteer clicks "Print deposit slip"
**Then** a PDF is generated server-side using OpenPDF 3.0.0 in the instance document language

**Given** the PDF is generated
**When** the content is rendered
**Then** it contains: item list (name, unit price), commission rate, expected net payout (BigDecimal, cent-accurate, FR-031)
**And** a lot appears as a single line (lot name, lot price)

**Given** the PDF is generated
**When** sent for printing
**Then** it is queued in the A4 document queue and sent to the USB standard printer

**Given** the deposit slip was printed once
**When** the volunteer re-triggers printing later
**Then** the slip is re-generated and re-queued (always re-printable)

---

## Epic 4: Point of Sale

Volunteers can scan items with a USB barcode scanner, manage baskets with full lot support, complete sales, and print buyer invoices -- safely across multiple simultaneous workstations.

### Story 4.1: Scanner Component & Item Scan

As a volunteer cashier,
I want to scan items with a USB barcode scanner that works regardless of keyboard layout,
So that I can process sales quickly without configuring each workstation.

**Acceptance Criteria:**

**Given** the volunteer opens `/volunteer/pos`
**When** the page loads
**Then** the scanner input field is auto-focused and captures all keyboard events

**Given** the volunteer clicks elsewhere on the page
**When** 500ms of keyboard inactivity elapses
**Then** focus returns automatically to the scanner input

**Given** a scanner sends a barcode on a QWERTY layout while the OS is set to AZERTY
**When** the scan is processed
**Then** the correct barcode value is decoded via key code mapping (FR-034)

**Given** a valid barcode is scanned
**When** the item is found and available
**Then** the item is added to the basket and displays name and price (FR-035)

**Given** a barcode is scanned for an already-sold item
**When** the lookup completes
**Then** an inline error appears: "Item already sold on another workstation." (FR-036)
**And** the item is not added to the basket

**Given** a barcode is scanned for an item with the incomplete flag
**When** the item is found
**Then** an inline warning is displayed with the missing detail (FR-037)
**And** the item is added to the basket (still sellable)

### Story 4.2: Basket Management & Payment Validation

As a volunteer cashier,
I want to manage the buyer basket and validate payment,
So that I can complete transactions cleanly with a full audit trail.

**Acceptance Criteria:**

**Given** items have been added to the basket
**When** the basket is displayed
**Then** each item shows its name and unit price
**And** the running total is shown at the bottom

**Given** the volunteer wants to remove an item
**When** they click the close icon on an item row
**Then** the item is removed from the basket

**Given** the basket contains only complete items (no incomplete lots)
**When** the volunteer clicks "Validate"
**Then** all basket items are marked as sold in a single atomic transaction (FR-039)
**And** the basket is cleared and ready for a new transaction

**Given** payment has been validated
**When** the transaction closes
**Then** no item can be returned or modified (FR-039: no returns or exchanges)

### Story 4.3: Lot Handling at POS

As a volunteer cashier,
I want the system to enforce lot integrity during scanning,
So that indivisible lots are sold complete or not at all.

**Acceptance Criteria:**

**Given** a scanned item belongs to a lot
**When** it is added to the basket
**Then** the lot group appears with the lot name in red and a counter "1/N scanned" (FR-046)
**And** the lot subtotal is shown in the group header
**And** no individual item price is shown within the lot group

**Given** a lot is partially scanned
**When** the volunteer clicks "Validate"
**Then** validation is blocked with an inline message indicating how many items are missing (FR-047)

**Given** all N items of a lot are scanned
**When** the last item is added
**Then** the lot is marked complete and sold at its global price (FR-048)

**Given** a lot is partially scanned and the buyer cannot find remaining items
**When** the volunteer clicks "Remove entire lot"
**Then** all items of that lot are removed from the basket (FR-081)
**And** the validate button re-enables if no other blocking lot remains

**Given** a completed lot is validated
**When** the invoice is generated
**Then** the lot appears as a single line: lot name and lot price (FR-041)

### Story 4.4: Multi-Workstation Concurrency Safety

As a volunteer on any cashier workstation,
I want the system to prevent double-selling the same item,
So that two cashiers cannot accidentally sell the same item to different buyers.

**Acceptance Criteria:**

**Given** two volunteers on separate workstations have the same item in their baskets
**When** the first volunteer validates successfully
**Then** the second volunteer's validation returns a 409 with the list of conflicting items

**Given** a 409 conflict is returned
**When** the Angular POS component receives it
**Then** an inline notification lists the conflicting items by name
**And** the volunteer manually removes them and re-validates

**Given** a sale is being validated
**When** the optimistic lock (`@Version` on `Item`) detects a concurrent write
**Then** the transaction rolls back and a 409 is returned -- no partial sale recorded

**Given** a Testcontainers MariaDB integration test
**When** two concurrent `TransactionTemplate` threads validate overlapping baskets
**Then** exactly one succeeds and the other receives a 409

### Story 4.5: Buyer Invoice Printing

As a volunteer cashier,
I want to print a buyer invoice on demand after a validated sale,
So that the buyer has a paper record of their purchase.

**Acceptance Criteria:**

**Given** a payment has been validated
**When** the volunteer clicks "Print invoice"
**Then** a PDF is generated server-side using OpenPDF 3.0.0

**Given** the PDF is generated
**When** the content is rendered
**Then** it contains: item list (name, unit price), basket total, association name, edition name, date (FR-041)
**And** a lot appears as a single line (lot name, lot price)

**Given** the PDF is generated
**When** sent for printing
**Then** it is queued in the A4 document queue and sent to the USB standard printer

**Given** the invoice was printed once
**When** the volunteer re-triggers printing
**Then** the invoice is re-queued (always re-printable)

### Story 4.6: Phase Transition Basket Cancellation

As a volunteer cashier with an active basket,
I want to be immediately notified if the admin changes the phase while I am mid-transaction,
So that I do not attempt to complete a sale in a phase where it is no longer valid.

**Acceptance Criteria:**

**Given** a volunteer has an active basket on the cashier page
**When** the admin transitions the edition phase
**Then** the server cancels all active baskets and sends SSE `basket-cancelled` to affected clients (FR-090)

**Given** the Angular POS component receives `basket-cancelled`
**When** the event arrives
**Then** a persistent toast appears: "The phase has changed. Your basket has been cancelled."
**And** the basket is cleared
**And** the scanner input is disabled

**Given** the scanner is disabled after basket cancellation
**When** the volunteer wants to resume
**Then** they must reload the cashier page to reactivate the scanner

**Given** a volunteer has no active basket
**When** a phase transition occurs
**Then** no basket-cancelled event is sent to them -- only the phase-changed event updates the phase chip

---

## Epic 5: Post-Sale, Payouts & Reporting

Volunteers can settle sellers and process payouts. Admins can generate daily and edition summary reports as PDFs, identify unsettled sellers, and officially close editions.

### Story 5.1: Seller Settlement Workflow

As a volunteer,
I want to see the list of unsettled sellers and settle them or mark their payout as not collected,
So that all payouts are accounted for before the end of the event.

**Acceptance Criteria:**

**Given** the volunteer navigates to `/volunteer/settlement`
**When** the page loads
**Then** all unsettled sellers are listed with name, amount owed, and phone number (FR-053)

**Given** the volunteer clicks "Settle" on a seller
**When** the settle action completes
**Then** the seller status changes to Settled
**And** the seller disappears from the unsettled list

**Given** a seller does not wish to collect their payout
**When** the volunteer clicks "Not collected" (FR-052)
**Then** a confirm dialog appears: "The amount of X.XX EUR will be transferred to the association's revenue. This action is irreversible."
**And** on confirmation, the full amount owed is recorded as association revenue
**And** the seller is removed from the unsettled list

**Given** a seller has been settled
**When** the volunteer views the settlement list
**Then** a "Print sales summary" button is available for that seller (UX-DR22)
**And** clicking it queues the PDF for A4 printing with spinner and toast feedback

### Story 5.2: Sales Summary PDF Generation

As a volunteer or admin,
I want to generate a sales summary per seller showing sold items, unsold items, and net payout,
So that sellers can collect their payment with a detailed breakdown.

**Acceptance Criteria:**

**Given** a sales summary is requested for a seller
**When** the PDF is generated using OpenPDF 3.0.0
**Then** it contains: sold items (name, unit price), unsold items (name, category, table number), gross total, commission deducted, net amount to pay out (FR-050)
**And** a lot appears as a single line (lot name, lot price)

**Given** a seller sold items with the incomplete flag
**When** the net payout is calculated
**Then** commission applies at the full rate -- incompleteness does not affect commission or sale price (FR-089)
**And** all monetary values use BigDecimal (cent-accurate, NFR-003)

**Given** the PDF language is set to "FR"
**When** the document is generated
**Then** all labels and headers use `messages_fr.properties` entries

**Given** the admin views a seller's detail page
**When** they click "Print sales summary"
**Then** the same PDF is generated and queued for printing (UX-DR22)

### Story 5.3: Daily Sales Report (Admin)

As an admin,
I want to generate a daily sales summary during the Sale phase,
So that I can monitor daily revenue and sales performance during the event.

**Acceptance Criteria:**

**Given** the edition is in Sale phase
**When** the admin generates a daily summary
**Then** the report covers the current calendar day
**And** contains: items sold and unsold for the day, daily gross revenue, daily commission earned (FR-054)

**Given** the report is generated
**When** the PDF is produced using OpenPDF 3.0.0
**Then** it uses the instance document language (FR-057)

**Given** a volunteer attempts to access the reports page
**When** the route is loaded
**Then** access is denied with a 403 (FR-058: admin only)

**Given** the admin refreshes the daily report
**When** refresh is triggered
**Then** the report reflects the latest sales data for that calendar day

### Story 5.4: Edition Summary & Outstanding Sellers Reports

As an admin,
I want edition-level summary reports and a list of unsettled sellers,
So that I have a complete financial picture at the close of the event.

**Acceptance Criteria:**

**Given** the edition is closed
**When** the admin views the reports page
**Then** an edition summary PDF is available: total items sold/unsold, total gross revenue, total commission earned (FR-055)

**Given** the admin requests the outstanding sellers report
**When** generated
**Then** it lists all unsettled sellers with their phone number as a PDF (FR-056, FR-057)

**Given** an edition is archived
**When** any user views the edition
**Then** aggregate metrics are visible in read-only mode
**And** item-level detail is accessible only through the PDFs generated at closure (FR-059)

**Given** the Clean Edition action has been triggered
**When** item records are deleted
**Then** aggregate metrics remain available (stored independently of item records)

### Story 5.5: Admin Reports Page

As an admin,
I want a reports page that shows only sections relevant to the current phase,
So that I can act quickly without navigating irrelevant options.

**Acceptance Criteria:**

**Given** the edition is in Sale phase
**When** the admin navigates to `/admin/reports`
**Then** only the daily summary section is shown with a "Refresh" button
**And** synthesis and export sections are absent

**Given** the edition is in Post-sale or Closed phase
**When** the admin navigates to `/admin/reports`
**Then** the synthesis section is visible (total sales, payouts, association revenue) in read-only mode
**And** two CSV export buttons appear: "Export catalog" and "Export payouts"
**And** clicking a CSV export triggers a direct file download with no dialog

**Given** the edition is in Post-sale phase
**When** the admin views the reports page
**Then** a "Print outstanding sellers list" button is visible
**And** clicking it opens the browser print view with the unsettled sellers list

**Given** a phase does not match a report section's availability condition
**When** the admin views the reports page
**Then** that section is completely absent (not greyed out -- absent)

---

## Epic 6: Item Catalog

Admins and volunteers can browse, search, and filter all items in the active edition across all phases. During Sale phase, volunteers can add items directly to the basket from the catalog as a scanner fallback.

### Story 6.1: Item Catalog -- Filterable & Sortable List

As an admin or volunteer,
I want to browse all items in the active edition with filters and sorting,
So that I can quickly locate any item regardless of which phase the event is in.

**Acceptance Criteria:**

**Given** the admin or volunteer navigates to `/admin/catalog` or `/volunteer/catalog`
**When** the page loads
**Then** all items of the active edition are displayed with pagination (default 50 per page, MatPaginator)
**And** inline filters appear above the list

**Given** the user applies one or more filters
**When** filters are submitted
**Then** the list updates to show only matching items filtered by: name/description, barcode number, category, table, sold/unsold status, complete/incomplete flag, seller name (FR-084)

**Given** the user clicks a sortable column header
**When** clicked once
**Then** the list sorts ascending with a visible indicator
**And** clicking again sorts descending

**Given** the price column is sorted
**When** JPageFlow processes the BigDecimal sort
**Then** the sort is attempted; if the known bug (JPageFlow v1.5.0) is present, the test documents this as a known failure pending the library patch (ARCH-005)

**Given** the Clean Edition action has been triggered
**When** a user navigates to the catalog
**Then** an empty state appears: "Edition cleaned -- no items." with no action (FR-086)

**Given** multiple users filter the catalog simultaneously
**When** each submits different filter combinations
**Then** each receives their own correct result independently

### Story 6.2: Catalog-to-Basket POS Fallback

As a volunteer cashier,
I want to add an item from the catalog directly to the basket during Sale phase,
So that I can process sales even when a barcode is damaged or unreadable.

**Acceptance Criteria:**

**Given** the edition is in Sale phase and the volunteer is on the cashier page
**When** they open the catalog
**Then** each item row shows an "Add to basket" button

**Given** the volunteer clicks "Add to basket" for an available item
**When** the request is processed
**Then** the item is added to the current basket with its name and price displayed

**Given** the volunteer attempts to add an already-sold item
**When** the request is processed
**Then** the system rejects it with an inline error message (FR-087: already-sold guard)

**Given** the item is already present in the current basket
**When** the volunteer clicks "Add to basket" again
**Then** the system rejects it with an inline error message (FR-087: already-in-basket guard)

**Given** the edition is not in Sale phase
**When** any user views the catalog
**Then** no "Add to basket" button is shown -- catalog is read-only browse only (FR-083)


