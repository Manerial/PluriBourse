---
baseline_commit: bec959b0288b85d3572d39ad5b2d9eb9d214c6cd
---

# Story 2.7: Edition Closure & Edition Archiving

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrator,
I want to officially close an edition and optionally archive its item records,
so that the edition is properly wrapped up and storage can be freed after the event.

## Acceptance Criteria

1. **Given** the edition is in Post-vente phase and at least one seller is still unsettled, **When** the admin clicks "Clôturer l'édition", **Then** the confirmation dialog shows *"X vendeur(s) non soldé(s) seront automatiquement marqués Non réclamé. Montant total transféré aux recettes de l'association : Y,YY €."* (FR-096) **And** the "Clôturer l'édition" button is enabled.

2. **Given** every seller is already Soldé or Non réclamé, **When** the admin clicks "Clôturer l'édition", **Then** the standard confirmation dialog is shown, without the unsettled-sellers warning.

3. **Given** the admin confirms the closure, **When** the transaction executes, **Then** every still-unsettled seller is marked Non réclamé and their due amount recorded as association revenue, atomically with the phase transition (FR-096) **And** the edition's phase becomes Clôturée and turns read-only.

4. **Given** the closure in AC 3 succeeded, **When** the client receives the success response, **Then** it immediately triggers generation of the edition-report PDF in both FR and EN (FR-013), submitted to the admin's currently-selected A4 printer. *(Decision, see Dev Notes § Scope decisions: this print step is decoupled from AC 3's atomic transaction — a missing/unavailable printer must never block the closure itself. A failure here is reported to the admin as its own, separate error; it never undoes the closure.)*

5. **Given** the edition is Clôturée and item records still exist, **When** the admin views the phase-control dialog, **Then** an "Archiver l'édition" button is visible (secondary, error-colored style).

6. **Given** the admin clicks "Archiver l'édition" and confirms, **When** the action completes, **Then** every item of the edition is copied into a new archive table with its name, category and status (sold/unsold) — lot items archived individually, with no lot reference retained — **And** all item records of this edition are permanently deleted **And** all seller profiles of this edition are permanently deleted **And** the "Archiver l'édition" button disappears **And** rollback from Clôturée is permanently disabled for this edition (FR-088).

7. **Given** a Clôturée edition has been archived, **When** the admin views the edition report, **Then** the aggregated metrics (items sold/unsold, gross revenue, commission, payment breakdown, net payout total, association revenue total) remain visible read-only (FR-059) — served from a snapshot frozen at archive time, since the underlying item/settlement rows no longer exist to recompute them from.

*(Note: epics.md's version of AC 6 additionally described the catalog screen showing "Édition archivée — aucun article." — deliberately out of scope here, see Dev Notes § Scope decisions. This story does not touch `ItemCatalogService`/`ItemCatalogComponent`.)*

## Tasks / Subtasks

- [x] **T1 — Backend: migration `025-archived-items.xml`** (AC: 6)
  - [ ] Create table `archived_items`: `id` BIGINT PK autoIncrement, `edition_id` BIGINT NOT NULL FK → `editions(id)` (`fk_archived_items_edition`, no cascade — the `Edition` row itself is never deleted), `name` VARCHAR(200) NOT NULL, `category_name` VARCHAR(100) NOT NULL, `sold` BOOLEAN NOT NULL. Follow the `024-settlements.xml` template exactly (one `<changeSet>`, `author="pluribourse"`).
  - [ ] Add `<include file="db/changelog/025-archived-items.xml"/>` to `db.changelog-master.xml` after the `024-settlements.xml` line.

- [x] **T2 — Backend: migration `026-edition-archive-snapshot.xml`** (AC: 7)
  - [ ] Add 9 **nullable** columns to `editions` (populated only at archive time): `archived_sold_item_count` BIGINT, `archived_unsold_item_count` BIGINT, `archived_gross_revenue` DECIMAL(10,2), `archived_commission` DECIMAL(10,2), `archived_cash_total` DECIMAL(10,2), `archived_check_total` DECIMAL(10,2), `archived_card_total` DECIMAL(10,2), `archived_net_payout_total` DECIMAL(10,2), `archived_association_revenue_total` DECIMAL(10,2).
  - [ ] Add the `<include>` to `db.changelog-master.xml` after `025-archived-items.xml`.

- [x] **T3 — Backend: `Edition` entity — snapshot fields** (AC: 7)
  - [ ] Add the 9 nullable fields from T2 to `Edition.java` (`domain/edition/entity/Edition.java`), same naming/typing convention as existing columns (`@Column(name = "archived_...")`).

- [x] **T4 — Backend: `EditionDto` — `hasItems`** (AC: 5)
  - [ ] Add `Boolean hasItems` as the **10th (last) component** of the `EditionDto` record (`domain/edition/dto/EditionDto.java`). It is a derived field, not backed by an `Edition` column.
  - [ ] `EditionMapper.toDto` (`domain/edition/mapper/EditionMapper.java`): add `@Mapping(target = "hasItems", ignore = true)`.
  - [ ] `EditionService.getEditionById(Long id)`: after `mapper.toDto(edition)`, rebuild the record with `hasItems = itemRepository.existsByEditionId(id)` (method already exists, used by `deleteEdition`). This is the **only** place `hasItems` is populated — the phase-transition endpoints (`advance`/`rollback`/new `close`) keep returning it unset; the frontend re-fetches via `getById` when it needs a fresh read (see T19 Dev Notes).
  - [ ] ⚠ **Compilation fallout (mandatory, not optional):** `EditionDto` is a Java **record**, and `new EditionDto(...)` is called **positionally** in 38 call sites across 20 test files. Adding the 10th component breaks compilation of every one until a trailing `null` argument is added (these tests don't assert on `hasItems`, so `null` is always the correct fix — never a computed value). Let the compiler drive this: build, fix each reported call site by appending `, null)`. Exact files (confirmed by `grep -rn "new EditionDto(" pluribourse-backend/src --include=*.java`, occurrence count in parentheses): `domain/edition/CurrentEditionIT.java` (1), `domain/edition/EditionCategoryIT.java` (3), `domain/edition/EditionManagementIT.java` (14), `domain/edition/PhaseTransitionIT.java` (1), `domain/item/ItemCatalogIT.java` (1), `domain/item/ItemManagementIT.java` (1), `domain/item/LotManagementIT.java` (2), `domain/payout/SettlementIT.java` (2), `domain/pos/PosBasketCancellationIT.java` (1), `domain/pos/PosBasketIT.java` (1), `domain/pos/PosScanIT.java` (1), `domain/print/BulkSettlementReportPrintingIT.java` (1), `domain/print/DailyReportPrintingIT.java` (1), `domain/print/DepositSlipPrintingIT.java` (1), `domain/print/EditionReportPrintingIT.java` (1), `domain/print/InvoicePrintingIT.java` (1), `domain/print/ReportExportIT.java` (1), `domain/print/SettlementReportPrintingIT.java` (2), `domain/print/ThermalLabelPrintingIT.java` (1), `domain/seller/SellerManagementIT.java` (1).

- [x] **T5 — Backend: `SettlementService.closeAllUnsettledAsUnclaimed`** (AC: 1, 3)
  - [ ] New `@Transactional` method in `SettlementService` (`domain/payout/service/SettlementService.java`): reuse `getSellersMatchingFilter(edition, SettlementFilter.UNSETTLED)` (already batched, sorted by `sellerNumber`, no N+1 — NFR-001) to get the unsettled sellers, then for each call the existing private `computeAmountDue` + `persistSettlement(seller, SettlementStatus.UNCLAIMED, amountDue, amountDue)` (same pattern as `markUnclaimed`, both already in this class). Return the summed `BigDecimal` total transferred. No phase guard inside — same convention as `getSettlementsForEdition`/`getSellersMatchingFilter`, the caller (T6) is responsible for its own guard.

- [x] **T6 — Backend: `EditionClosingService`** (AC: 1, 2, 3)
  - [ ] New class `domain/edition/service/EditionClosingService.java`, `@Service @RequiredArgsConstructor`, depends on `EditionService` + `SettlementService`.
  - [ ] `closeEdition(Long id)`: `Edition edition = editionService.requireEdition(id)`; **then guard in this order** — `if (edition.getPhase() == PhaseType.CLOSED) { throw new PhaseAlreadyClosedException(); }` (reuse as-is, `domain.edition.exception`) **before** `PhaseGuard.requirePostSalePhase(edition)` (reuse as-is, `domain.item.service.PhaseGuard`, covers PREPARATION/DEPOSIT/SALE). See Dev Notes § Guard ordering for why this explicit CLOSED check must come first. Then `settlementService.closeAllUnsettledAsUnclaimed(edition)`; `return editionService.advancePhase(id)`. All of this runs inside **one** `@Transactional` boundary (default `REQUIRED` propagation joins the same transaction as `advancePhase`'s own `@Transactional`) — this is what makes AC 3 atomic, no extra plumbing needed. `advancePhase` already resolves `POST_SALE → CLOSED` via its existing `computeNextPhase` switch; no changes needed there.

- [x] **T7 — Backend: `EditionController` — `POST /{id}/close`** (AC: 1, 2, 3)
  - [ ] Inject `EditionClosingService`, add `@PostMapping("/{id}/close")` returning the `EditionDto` from `closeEdition(id)`. Class-level `@PreAuthorize("hasRole('ADMIN')")` already covers it.

- [x] **T8 — Backend: `EditionSummaryReportPrintService.printEditionReportBothLanguages`** (AC: 4)
  - [ ] New method, same shape as the existing `printEditionReport(Long editionId, HttpSession session)` but hardcodes both locales instead of reading `edition.getDocumentLanguage()`: resolve `edition`/`report`/`editionName` once, resolve+validate the session's selected A4 printer once (same `InvalidPrinterSelectionException` checks), then call `documentPrintService.buildEditionReportJob(editionName, report, Locale.FRENCH)` and `...Locale.ENGLISH)`, submitting both to `printQueueService`. `reportService.getEditionReport(edition)` already works in CLOSED (`PhaseGuard.requirePostSaleOrClosedPhase`), so this is safe to call right after AC 3's transition commits.

- [x] **T9 — Backend: `AdminReportController` — `POST /edition/{editionId}/print-closure`** (AC: 4)
  - [ ] New endpoint mirroring the existing `/edition/{editionId}/print`, calling `editionSummaryReportPrintService.printEditionReportBothLanguages(editionId, session)`.

- [x] **T10 — Backend: `ReportService.getEditionReport` — archived branch** (AC: 7)
  - [ ] At the top of the method (after the existing `PhaseGuard.requirePostSaleOrClosedPhase(edition)` call), branch: `if (edition.isArchived()) { return buildFromArchivedSnapshot(edition); }` else keep the existing live-computation body unchanged. `buildFromArchivedSnapshot` is a small private method building the `EditionSummaryReportDto` straight from the 9 frozen `Edition` fields from T3 — no queries.

- [x] **T11 — Backend: `domain.archive` package — `ArchivedItem`** (AC: 6)
  - [ ] New package `org.pluribourse.domain.archive`. `entity/ArchivedItem.java`: `id` (identity PK), `edition` (`@ManyToOne`, `edition_id`), `name` (String, 200), `categoryName` (String, 100), `sold` (boolean) — matches T1's table exactly.
  - [ ] `repository/ArchivedItemRepository.java extends JpaRepository<ArchivedItem, Long>` (add `findAllByEditionId` too — unused by this story, but the natural query Story 6.2 will need; cheap to add now while the table is fresh in context, skip if it feels like scope creep).

- [x] **T12 — Backend: `EditionArchivingService`** (AC: 5, 6, 7)
  - [ ] New class `domain/archive/service/EditionArchivingService.java`, depends on `EditionService`, `EditionRepository`, `ItemRepository`, `ArchivedItemRepository`, `SettlementRepository`, `SellerRepository`, `ReportService`.
  - [ ] `archiveEdition(Long id)`, `@Transactional`:
    1. `Edition edition = editionService.requireEdition(id)`.
    2. Guard: `edition.getPhase() != PhaseType.CLOSED` → new `EditionNotClosedException`.
    3. Guard: `edition.isArchived()` → new `EditionAlreadyArchivedException`.
    4. `List<Item> items = itemRepository.findAllByEditionIdForSettlementReport(id)` (reuse as-is — story 5.6's query already does exactly the right JOIN FETCH category+lot, ordered by seller then item number, for the whole edition. **Do not write a new item-fetch query.**). Guard: `items.isEmpty()` → new `NoItemsToArchiveException` (defense in depth — the button is already hidden client-side when `hasItems` is false).
    5. **Before deleting anything**, snapshot the report: `EditionSummaryReportDto snapshot = reportService.getEditionReport(edition)` (still resolves live — nothing's deleted yet at this point). Copy its 9 fields onto the `Edition` entity's T3 columns.
    6. Map `items` → `ArchivedItem` (`name`, `category.getName()`, `sold` — ignore `lot` entirely, AC 6 requires no lot reference), `archivedItemRepository.saveAll(...)`.
    7. `itemRepository.deleteAll(items)`.
    8. **Delete settlements before sellers** (see Dev Notes § FK ordering): `List<Settlement> settlements = settlementRepository.findAllBySellerProfileEditionId(id); settlementRepository.deleteAll(settlements);`.
    9. `List<SellerProfile> sellers = sellerRepository.findAllByEditionId(id); sellerRepository.deleteAll(sellers);` — cascades to any remaining `Lot` rows via the existing `fk_lots_seller_profile` (`deleteCascade=true`, migration 015); items are already gone from step 7, so the items cascade is moot.
    10. `edition.setArchived(true); editionRepository.save(edition);` (the snapshot fields from step 5 are saved in the same call — set them on `edition` before this save, not in a separate one).
    11. Return the mapped `EditionDto`.
  - [ ] New exceptions in `domain/archive/exception/`: `EditionNotClosedException`, `EditionAlreadyArchivedException`, `NoItemsToArchiveException` — copy the existing exception class shape from a sibling like `PhaseAlreadyClosedException` (`domain/edition/exception/`) so they map to the same kind of 422 + `type` URI the frontend's `extractErrorType` already expects.

- [x] **T13 — Backend: `EditionController` — `POST /{id}/archive`** (AC: 5, 6, 7)
  - [ ] Inject `EditionArchivingService`, add `@PostMapping("/{id}/archive")` returning the `EditionDto`.

- [x] **T14 — Backend: integration test `EditionClosingIT`** (AC: 1, 2, 3, 4)
  - [ ] Package `org.pluribourse.domain.edition`, extends `IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`, storyboard style. Setup: create edition, advance PREPARATION→DEPOSIT→SALE→POST_SALE via real `POST /api/admin/editions/{id}/phase/advance` calls (matches the pattern already used in `BulkSettlementReportPrintingIT`/`EditionReportPrintingIT`), create ≥2 sellers with sold items, settle one (SETTLED), leave one UNSETTLED.
  - [ ] Scenarios: close while PREPARATION/DEPOSIT/SALE → 422 `settlement-not-allowed` (via `PhaseGuard.requirePostSalePhase`, reused as-is — the error type/message reads as settlement-specific in this context; accepted as a documented minor rough edge, see Dev Notes § Guard ordering, not a new exception class); close as non-admin → 403; close with 1 unsettled seller → 200, phase CLOSED — assert the effect via `GET /api/admin/reports/edition/{id}` `associationRevenueTotal` increasing by the unsettled seller's due amount (⚠ `GET /api/settlements` is no longer reachable once CLOSED — `PhaseGuard.requirePostSalePhase` — do not rely on it for post-closure assertions); close an already-CLOSED edition → 422 `phase-already-closed` (`PhaseAlreadyClosedException`, thrown directly by `EditionClosingService`'s own explicit CLOSED check — **not** surfaced via `advancePhase`, see T6); rollback after closure still works and is unaffected (existing behavior, quick regression check only).
  - [ ] Print-closure scenario (AC 4): with an A4 printer registered/selected in session (reuse `PrinterBridgeDouble` setup from `EditionReportPrintingIT`), `POST /api/admin/reports/edition/{id}/print-closure` → 204, and assert two jobs were submitted (or two print calls recorded on the double) — one per locale. No printer selected → 422 `invalid-printer-selection`, and separately assert this does **not** revert the edition's phase (closure already committed independently).

- [x] **T15 — Backend: integration test `EditionArchivingIT`** (AC: 5, 6, 7)
  - [ ] Package `org.pluribourse.domain.archive`, same conventions. Storyboard: edition through PREPARATION→...→CLOSED (reuse the same real-HTTP advance pattern), 2 sellers with sold + unsold items, one item in a lot of 2.
  - [ ] Scenarios: archive while not CLOSED → 422; archive with 0 items → 422 (create a second edition with none, or strip items first); archive → 200, then assert: `archived_items` row count == item count, lot items present individually (2 rows, no lot grouping) with correct `categoryName`/`sold`; `items`/`seller_profiles`/`settlements` tables empty for this edition; `GET /api/admin/reports/edition/{id}` still returns the exact pre-archive figures (compare against a report fetched just before archiving); rollback attempt after archiving → 422 `PhaseRollbackAfterArchiveException` (already-existing guard, `EditionService.computePreviousPhase`, wire-through regression check); archive an already-archived edition → 422.

- [x] **T16 — Frontend: `EditionDto` model** (AC: 5)
  - [ ] Add `hasItems?: boolean` (**optional**, not `hasItems: boolean`) to `EditionDto` (`models/edition.model.ts`). Optional matches T4's backend behavior (unset on `advance`/`rollback`/`close` responses) **and** avoids breaking the ~8 existing spec files that build complete `EditionDto` object literals (`edition-list.component.spec.ts`, `deposit-phase.guard.spec.ts`, `sale-phase.guard.spec.ts`, `settlement-phase.guard.spec.ts`, `edition.service.spec.ts`, `current-edition.service.spec.ts`, `report-page.component.spec.ts`) — TypeScript structural typing does not require an optional property to be present in an object literal, so none of those files need changes.

- [x] **T17 — Frontend: `edition.service.ts`** (AC: 1, 3, 5, 6)
  - [ ] `closeEdition(id: number): Observable<EditionDto>` → `POST /api/admin/editions/${id}/close`.
  - [ ] `archiveEdition(id: number): Observable<EditionDto>` → `POST /api/admin/editions/${id}/archive`.

- [x] **T18 — Frontend: `report.service.ts`** (AC: 4)
  - [ ] `printEditionReportClosure(editionId: number): Observable<void>` → `POST /api/admin/reports/edition/${editionId}/print-closure`.

- [x] **T19 — Frontend: `PhaseControlComponent`** (AC: 1, 2, 3, 4, 5, 6)
  - [ ] Inject `SettlementService` and `ReportService` (both already exist, not currently used here).
  - [ ] `canAdvance()`: exclude `POST_SALE` too (`e.phase !== 'CLOSED' && e.phase !== 'POST_SALE'`) — closing POST_SALE now goes through the dedicated flow below, not the generic advance button.
  - [ ] `canClose()`: `e.phase === 'POST_SALE'`.
  - [ ] `canArchive()`: `e.phase === 'CLOSED' && !e.archived && e.hasItems`.
  - [ ] `confirmClose()`: fetch `settlementService.getSettlements()` (works while POST_SALE, same precondition as closing); filter `status === 'UNSETTLED'`; sum `amountDue` with `Big` (not native `+` — money, same convention as `settlement-list.component.ts`'s `warningBelowDue`/`blockedAboveDue`); build the dialog description from `phase.close.dialog.warningUnsettled` (interpolating `count`/`amount`) when the unsettled list is non-empty, else `phase.close.dialog.description`; on confirm, call `editionService.closeEdition(e.id)`, toast success, `dialogRef.close()`; **then** (AC 4, best-effort, does not affect the already-closed edition) call `reportService.printEditionReportClosure(e.id)`, toast error on failure (`phase.close.error.printReport`) without any rollback/dialog-reopen — the closure already succeeded and the dialog is already closed by this point.
  - [ ] `confirmArchive()`: same shape as `confirmUnclaimed()` in `settlement-list.component.ts` (`confirmVariant: 'error'`), static dialog text (no dynamic interpolation needed, unlike close); on confirm, `editionService.archiveEdition(e.id)`, toast success, `dialogRef.close()`.
  - [ ] Reuse the existing `isSubmitting` guard for both new flows (mirrors `confirmAdvance`/`confirmRollback`).

- [x] **T20 — Frontend: `phase-control.component.html`** (AC: 1, 2, 3, 5)
  - [ ] Add a "Clôturer l'édition" button (`*ngIf`-equivalent `@if (canClose())`) next to/instead of the generic advance button for this phase — same `mat-flat-button color="primary"` style as advance.
  - [ ] Add an "Archiver l'édition" button `@if (canArchive())` — `mat-button color="warn"` (secondary, error-colored per UX-DR18). ⚠ No shared "danger zone" component/wrapper exists in the codebase — EXPERIENCE.md describes it only as a UX concept, never implemented as a reusable component; existing destructive actions elsewhere (printer/seller/user delete, category removal) are just standalone `mat-button color="warn"` buttons. Follow that same standalone-button convention here — do not search for or invent a shared wrapper.

- [x] **T21 — Frontend: i18n `fr.json`/`en.json`** (AC: 1, 2, 3, 4, 5)
  - [ ] Add under `phase`: `close.button`, `close.success`, `close.error.generic`, `close.error.printReport`, `close.dialog.title`, `close.dialog.description`, `close.dialog.warningUnsettled` (interpolates `{{count}}`/`{{amount}}`, FR text must match epics.md's exact wording from AC 1); `archive.button`, `archive.success`, `archive.error`, `archive.dialog.title`, `archive.dialog.description` (FR text: *"Archiver et supprimer tous les articles de cette édition. Cette action est irréversible."* — verbatim from EXPERIENCE.md UX-DR18).
  - [ ] Remove `phase.advance.dialog.description.POST_SALE` (both files) — dead once `canAdvance()` excludes `POST_SALE` (T19); its text is superseded by `phase.close.dialog.description`.

- [x] **T22 — Frontend: `phase-control.component.spec.ts`** (AC: 1, 2, 3, 4, 5, 6)
  - [ ] Mock `SettlementService`/`ReportService`. Tests: close button visible only in POST_SALE; archive button visible only in CLOSED+!archived+hasItems; dialog shows the unsettled warning with correct interpolated count/amount when unsettled sellers exist, standard description otherwise; successful close closes the dialog and calls the print-closure endpoint; a print-closure failure still leaves the close itself as a success (separate toast, dialog already closed); archive dialog uses `confirmVariant: 'error'`; closing then reopening the dialog re-fetches via `getById` and reflects the refreshed `hasItems` (covers T4's "unset until re-fetch" behavior end-to-end).

### Review Findings

- [x] [Review][Defer] FR-096 bypass via the pre-existing `/phase/advance` endpoint [EditionService.java:137-145] — deferred, needs a dedicated follow-up story: `EditionService.advancePhase`/`computeNextPhase` still allows a direct `POST_SALE → CLOSED` transition with zero guard, completely bypassing `EditionClosingService`'s atomic auto-Non-réclamé logic (FR-096). Confirmed live in code and demonstrated by this diff's own `EditionArchivingIT.advance_to_closed()` (Order 7), which closes via the old endpoint and leaves seller "Bob" permanently UNSETTLED with no `Settlement` row. Blocking this transition (unconditionally or only when unsettled sellers remain) was investigated during review and found to break 7 pre-existing IT files across 4 already-`done` stories that use `/phase/advance` as pure scaffolding to reach Clôturée with sellers never settled: `PhaseTransitionIT` (Story 2.2, ×2), `ItemCatalogIT`, `SettlementIT` (Story 5.1 — Bob deliberately unsettled), `SettlementReportPrintingIT`, `EditionReportPrintingIT` (Story 5.4 — no seller ever settled), `ReportExportIT` — plus this story's own `EditionArchivingIT`. User decision: spin off a dedicated story rather than patch inline during this review, given the cross-story blast radius.

- [x] [Review][Patch] Print partial-failure not surfaced distinctly [EditionSummaryReportPrintService.java:70-71] — fixed: each locale submitted independently (try/catch mirroring `SettlementReportPrintService#printAllReports`), throws only if both fail.
- [x] [Review][Patch] `hasItems` hardcoded to `false` instead of left `null` in archive response [EditionArchivingService.java:88-90] — fixed: now `null`, consistent with `advancePhase`/`rollbackPhase`/`closeEdition`.
- [x] [Review][Patch] Dead return value from `closeAllUnsettledAsUnclaimed` discarded by caller [EditionClosingService.java:40] — fixed: method now returns `void`.

## Dev Notes

### Scope decisions (already resolved — do not re-litigate with the user)

1. **PDF generation decoupled from the closure transaction** (AC 3/4). The only existing PDF pipeline (`EditionSummaryReportPrintService`) always sends physically to the admin's selected A4 printer session — no download/storage mechanism exists anywhere in `domain.print`. Making the atomic closure transaction (FR-096: phase + auto-Non-réclamé) depend on printer hardware would mean an edition can never be closed without a printer connected. Decision: keep AC 3's transaction to phase+settlements only; the EN+FR PDF generation is a second, independent HTTP call triggered automatically by the frontend right after a successful close, with its own error handling (mirrors the 5.6 pattern of best-effort print with a separate error surface).
2. **Aggregated-metrics snapshot** (AC 7). FR-059 requires metrics to stay visible after archiving, but FR-088 requires deleting the underlying `Item`/`Settlement` rows the metrics are computed from. There is no existing mechanism for this — none of epics.md/PRD/EXPERIENCE.md specify one. Resolution: freeze the 9 `EditionSummaryReportDto` fields onto new `Edition` columns at archive time (before deletion), and have `ReportService.getEditionReport` serve those instead of recomputing once `edition.isArchived()`.
3. **Catalog empty state deferred — Story 6.2 covers a different screen, not the same one.** `ItemCatalogService`/`ItemCatalogComponent` resolve only the currently-active edition (`getActiveEdition()`, which already excludes `CLOSED`) — by the time an edition is archived it has already been `CLOSED` for a while, and the catalog has already been showing a generic "no active edition" state since the moment of closure, not specifically an archived-edition message. ⚠ Story 6.2 ("consultation catalogue édition archivée", backlog, depends on this story) does **not** simply add this exact empty state to the existing catalog screen — per epics.md, it charters a **new, separate** archived-edition consultation screen (edition selector + pagination). This story does not touch the catalog at all and does not attempt to reproduce epics.md's literal AC 6 wording ("Édition archivée — aucun article.") anywhere — that copy belongs to whatever screen Story 6.2 builds, not to this story's scope.
4. **AC 7's 9 metrics vs. epics.md's/EXPERIENCE.md's shorter lists.** epics.md's AC 7 text names only 3 metrics ("total des ventes, reversements, recettes de l'association"); EXPERIENCE.md names 6, including a "total articles déposés" figure that has no equivalent anywhere in `EditionSummaryReportDto`. The 9 fields frozen by T2/T3 are an exact 1:1 mirror of `EditionSummaryReportDto`'s real fields (`soldItemCount`, `unsoldItemCount`, `grossRevenue`, `commission`, `cashTotal`, `checkTotal`, `cardTotal`, `netPayoutTotal`, `associationRevenueTotal`) — the only structure `ReportService` actually produces. Decision: snapshot exactly what `getEditionReport` already returns, not a hand-picked subset matching either source document's prose — do not attempt to add a "total articles déposés" field, it doesn't exist anywhere upstream.

### Guard ordering in `EditionClosingService.closeEdition` (critical, will break T14 otherwise)

`PhaseGuard.requirePostSalePhase(edition)` (`domain.item.service`) throws `SettlementNotAllowedException` for **any** phase other than `POST_SALE` — including `CLOSED`. If it were called alone, closing an already-`CLOSED` edition would throw `SettlementNotAllowedException`, never reach `advancePhase()`/`computeNextPhase()`, and never produce `PhaseAlreadyClosedException`. T6 therefore checks `edition.getPhase() == PhaseType.CLOSED` **explicitly, first**, throwing the existing `PhaseAlreadyClosedException` directly — this keeps the "already closed" case correctly typed without adding a new exception class (`PhaseAlreadyClosedException` already exists for exactly this concept on the `advance` endpoint). The remaining case (PREPARATION/DEPOSIT/SALE) still goes through `PhaseGuard.requirePostSalePhase`, whose `SettlementNotAllowedException` type/message ("Seller settlement is only available...") is Settlement-flavored rather than Closure-flavored — accepted as-is to avoid a third phase-guard exception class; do not build a dedicated `EditionNotPostSaleException` for this story.

### FK ordering at archive time (critical, will break otherwise)

`fk_settlements_seller_profile` (migration `024-settlements.xml`) has **no** `deleteCascade` — this is deliberate (`SellerService.delete()` explicitly blocks deleting a seller with a `Settlement` row, comment: *"A settled/unclaimed payout is a financial record — deleting its seller must never silently cascade-delete it"*). After AC 3's closure, **every** seller has a `Settlement` row (SETTLED or UNCLAIMED). `EditionArchivingService` must delete `Settlement` rows explicitly **before** deleting `SellerProfile` rows (T12 step 8 before step 9) — it must not go through `SellerService.delete()`, which would reject every seller for exactly this reason. `Item` (`fk_items_seller_profile`) and `Lot` (`fk_lots_seller_profile`) both already have `deleteCascade="true"` (migrations 013/015) — items are deleted explicitly anyway (step 7, needed to build the archive rows first), lots are cleaned up automatically by the seller-profile cascade in step 9.

### Reuse — do not rebuild what already exists

- Item fetch for archiving: `ItemRepository.findAllByEditionIdForSettlementReport(editionId)` (story 5.6) already does the exact JOIN FETCH (category + lot) over the whole edition, ordered — reuse it verbatim, do not add a new query.
- Unsettled-seller batch resolution: `SettlementService.getSellersMatchingFilter(edition, SettlementFilter.UNSETTLED)` (story 5.6) already returns exactly the sellers to auto-mark, batched, sorted by `sellerNumber` — reuse it in T5, do not re-derive "unsettled" from scratch.
- Closure preview (AC 1/2 dialog numbers): **no new backend endpoint.** The frontend already fetches `GET /api/settlements` elsewhere (`SettlementListComponent`) with exactly the `status`/`amountDue` fields needed — `PhaseControlComponent.confirmClose()` calls the same `SettlementService.getSettlements()` and computes count/total client-side with `Big`, matching the existing `settlement-list.component.ts` money-arithmetic convention (never native `+` on currency).
- `EditionService.advancePhase(id)` is reused unmodified for the actual POST_SALE→CLOSED transition (T6) — it already broadcasts `phase-changed` via SSE (`savePhaseThenSendEvent`) and already special-cases per-target-phase business rules (see the existing `NoCategoriesConfigured` check for DEPOSIT) — closing follows the same established pattern, no new SSE work needed.

### Circular-dependency trap (why `EditionClosingService`/`EditionArchivingService` exist as new classes)

`SettlementService` and `ReportService` already depend on `EditionService`. If closure/archiving logic were added as new methods directly inside `EditionService`, it would need to depend on `SettlementService`/`ReportService` in turn — a circular Spring bean dependency that fails to start. That's why T6 and T12 are new orchestrating services (in `domain.edition.service` and `domain.archive.service` respectively) that depend *on* `EditionService`/`SettlementService`/`ReportService`, never the other way around.

### Testing standards (CLAUDE.md)

E2E through controllers only, one class per business scenario, `@TestMethodOrder(OrderAnnotation.class)`, data persists across `@Order` methods (no class-level `@Transactional`), extends `org.pluribourse.shared.IntegrationTest`. Advance an edition to the phase you need via real `POST /api/admin/editions/{id}/phase/advance` calls (established pattern in `BulkSettlementReportPrintingIT`/`EditionReportPrintingIT`), not direct repository mutation. `GET /api/settlements` is blocked once the edition leaves POST_SALE — do not use it to assert post-closure state; use `GET /api/admin/reports/edition/{id}` instead (reachable in both POST_SALE and CLOSED).

### Project Structure Notes

- New package: `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/{entity,repository,service,exception}` — `ArchivedItem`, `ArchivedItemRepository`, `EditionArchivingService`, 3 new exceptions.
- New class (existing package): `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionClosingService.java`.
- Modified: `EditionService.java`, `EditionController.java`, `EditionDto.java`, `EditionMapper.java`, `Edition.java` (all `domain/edition/*`); `SettlementService.java` (`domain/payout/service/`); `ReportService.java`, `EditionSummaryReportPrintService.java`, `AdminReportController.java` (all `domain/report/*`).
- New migrations: `025-archived-items.xml`, `026-edition-archive-snapshot.xml` + 2 new `<include>` lines in `db.changelog-master.xml`.
- New tests: `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionClosingIT.java`, `.../domain/archive/EditionArchivingIT.java`.
- Modified frontend: `models/edition.model.ts`, `services/edition.service.ts`, `services/report.service.ts`, `features/admin/editions/phase-control/phase-control.component.{ts,html,spec.ts}`, `public/i18n/{fr,en}.json`.
- No route changes: the phase-control screen is a CDK dialog (`PhaseControlComponent`, opened from `EditionListComponent.openPhaseDialog()`), **not** a standalone `/admin/editions/{id}/phase` route — despite what EXPERIENCE.md's "page" framing and architecture.md might suggest, that's how Story 2.2 actually shipped. Extend the existing dialog; do not create a new route/page.
- Architecture.md's package paths (`org.pluribourse.edition.*`, `org.pluribourse.item.*`) are stale — the real code lives under `org.pluribourse.domain.*` throughout (confirmed by every file read for this story).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.7] (lines ~960-1001) — AC source, FR-013/FR-052/FR-059/FR-082/FR-088/FR-096.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md#UX-DR18] — Archive button style, confirmation copy; "danger zone" is a UX concept only, no matching shared component exists in the codebase (see T20).
- [Source: pluribourse-backend/.../domain/edition/service/EditionService.java] — `advancePhase`, `computePreviousPhase` (already refuses rollback after archive), `savePhaseThenSendEvent`.
- [Source: pluribourse-backend/.../domain/item/service/PhaseGuard.java] — `requirePostSalePhase` reused in T6 (Settlement-flavored exception, see Dev Notes § Guard ordering); `requirePostSaleOrClosedPhase` already covers T10's `ReportService.getEditionReport`.
- [Source: pluribourse-backend/.../domain/edition/exception/PhaseAlreadyClosedException.java] — reused directly (not via `advancePhase`) in T6's explicit CLOSED guard.
- [Source: pluribourse-backend/.../domain/payout/service/SettlementService.java] — `getSellersMatchingFilter`, `markUnclaimed`, `persistSettlement`, `getAssociationRetainedTotal`.
- [Source: pluribourse-backend/.../domain/report/service/ReportService.java#getEditionReport] — live computation to branch around when archived.
- [Source: pluribourse-backend/.../domain/report/service/EditionSummaryReportPrintService.java] — single-locale print pattern to duplicate for both languages.
- [Source: pluribourse-backend/.../domain/item/repository/ItemRepository.java#findAllByEditionIdForSettlementReport] — reused as-is for archiving.
- [Source: pluribourse-backend/.../domain/seller/service/SellerService.java#delete] — why settlements must be deleted explicitly, not via this method.
- [Source: pluribourse-backend/src/main/resources/db/changelog/024-settlements.xml] — migration template.
- [Source: pluribourse-frontend/.../features/admin/editions/phase-control/phase-control.component.ts] — dialog to extend, `canAdvance`/`canRollback`/`confirmAdvance` patterns.
- [Source: pluribourse-frontend/.../features/settlement/settlement-list.component.ts#confirmUnclaimed] — dynamic-amount confirm-dialog pattern to replicate for the close warning.
- [Source: _bmad-output/implementation-artifacts/2-2-controle-du-cycle-de-phases-boites-de-dialogue-de-confirmation.md] — original phase-control story, confirms the dialog-not-route reality.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), via dev-story (Claude Code)

### Debug Log References

Aucun — implémentation conforme à la story, aucun écart de production détecté en cours de route.

### Completion Notes List

- Backend (T1-T4) : deux migrations Liquibase (`archived_items`, 9 colonnes `archived_*` nullables sur
  `editions`) ; `EditionDto.hasItems` ajouté comme 10ᵉ composant positionnel du record, uniquement
  peuplé par `EditionService.getEditionById` (`itemRepository.existsByEditionId`), les endpoints de
  transition de phase continuent de le laisser `null`. Effet de bord de compilation attendu et traité :
  38 sites d'appel `new EditionDto(...)` répartis sur 20 classes de test corrigés mécaniquement (script
  Python à parenthésage équilibré, pas de sed ligne-à-ligne) en ajoutant un `null` final — 0 site oublié,
  confirmé par `test-compile`.
- Backend (T5-T10) : `SettlementService.closeAllUnsettledAsUnclaimed` réutilise
  `getSellersMatchingFilter`/`computeAmountDue`/`persistSettlement` existants (aucune requête
  nouvelle). `EditionClosingService` (nouveau, `domain.edition.service`) garde explicitement l'état
  CLOSED *avant* `PhaseGuard.requirePostSalePhase` (sinon `SettlementNotAllowedException` masquerait
  `PhaseAlreadyClosedException` pour une édition déjà clôturée) puis délègue à `advancePhase` dans la
  même transaction (AC 3 atomique par propagation `REQUIRED`, sans plomberie supplémentaire).
  `EditionSummaryReportPrintService.printEditionReportBothLanguages` duplique `printEditionReport` en
  soumettant deux jobs (FR puis EN) plutôt que de dériver `documentLanguage`. `ReportService.getEditionReport`
  branche sur `edition.isArchived()` en tout début (après le garde de phase existant) vers une nouvelle
  méthode privée qui construit le DTO directement depuis les 9 colonnes gelées, sans requête.
- Backend (T11-T13) : nouveau package `domain.archive` (`ArchivedItem`, `ArchivedItemRepository`,
  `EditionArchivingService`, 3 exceptions). `EditionArchivingService` est un service orchestrateur
  séparé (comme `EditionClosingService`) pour éviter un cycle Spring — `SettlementService`/`ReportService`
  dépendent déjà de `EditionService`. Ordre de suppression strictement conforme aux Dev Notes : snapshot
  du rapport → copie des articles → suppression des articles → suppression des `Settlement` → suppression
  des `SellerProfile` (jamais via `SellerService.delete()`, qui rejetterait tout vendeur soldé/non
  réclamé) → `archived = true` avec les 9 champs gelés dans le même `save`.
- Backend (T14-T15) : `EditionClosingIT` (13 scénarios) et `EditionArchivingIT` (12 scénarios), storyboard
  par contrôleurs uniquement, phases avancées via de vrais appels HTTP. Écart mineur assumé sur la
  vérification "deux jobs soumis, un par locale" (AC 4) : la story suggérait de compter les appels
  enregistrés sur `PrinterBridgeDouble`, mais ce double est HTTP-only (le vrai flux d'impression passe
  par WebSocket via `PrinterBridgeClient` et échoue toujours contre lui, comme déjà démontré par
  `EditionReportPrintingIT` Order 17) — vérifié à la place via `PrinterQueueHandle.getQueueDepth()` :
  après échec et suspension du premier job, le second reste visible en file (profondeur 1), preuve que
  deux jobs distincts ont bien été soumis. `associationRevenueTotal` avant/après clôture comparé plutôt
  que `GET /api/settlements` (bloqué hors Post-vente, cf. Dev Notes de la story).
- Frontend (T16-T22) : `EditionDto.hasItems` rendu optionnel (évite de casser les littéraux `EditionDto`
  existants dans ~8 fichiers `*.spec.ts`, conforme à la story). `PhaseControlComponent.canAdvance()`
  exclut désormais `POST_SALE` (repris par `canClose()`) ; `confirmClose()` récupère les règlements via
  `SettlementService.getSettlements()`, additionne les montants dus des vendeurs `UNSETTLED` avec `Big`
  (jamais l'opérateur `+` natif sur de l'argent, convention déjà en place dans `settlement-list.component.ts`),
  puis déclenche `printEditionReportClosure` en best-effort *après* la fermeture du dialogue — un échec
  d'impression ne défait jamais la clôture déjà réussie. `confirmArchive()` reprend le patron
  `confirmUnclaimed` (`confirmVariant: 'error'`). Clé i18n morte `phase.advance.dialog.description.POST_SALE`
  supprimée des deux langues (plus jamais atteinte, `canAdvance()` l'exclut).
- Vérifications : 490/490 tests backend (465 + 25 nouveaux : 13 `EditionClosingIT` + 12 `EditionArchivingIT`),
  623/623 tests frontend (613 + 10 nouveaux dans `phase-control.component.spec.ts`), aucune régression.
  Vérification visuelle humaine du flux clôture/archivage sur `/admin/editions` (CLAUDE.md § Interaction
  utilisateur) en attente — `dev-story` a tourné sans supervision interactive.

### File List

**Backend :**
- `pluribourse-backend/src/main/resources/db/changelog/025-archived-items.xml` (nouveau)
- `pluribourse-backend/src/main/resources/db/changelog/026-edition-archive-snapshot.xml` (nouveau)
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/Edition.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/dto/EditionDto.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/mapper/EditionMapper.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionClosingService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/controller/EditionController.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/EditionSummaryReportPrintService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/entity/ArchivedItem.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/repository/ArchivedItemRepository.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/exception/EditionNotClosedException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/exception/EditionAlreadyArchivedException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/exception/NoItemsToArchiveException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/EditionArchivingService.java` (nouveau)

**Tests backend (nouveaux) :**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionClosingIT.java` (nouveau)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java` (nouveau)

**Tests backend (fallout compilation `EditionDto`, 10ᵉ composant `hasItems`, modifiés) :**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionCategoryIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosScanIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ReportExportIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/seller/SellerManagementIT.java`

**Frontend :**
- `pluribourse-frontend/src/app/models/edition.model.ts` (modifié)
- `pluribourse-frontend/src/app/services/edition.service.ts` (modifié)
- `pluribourse-frontend/src/app/services/report.service.ts` (modifié)
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts` (modifié)
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.html` (modifié)
- `pluribourse-frontend/public/i18n/fr.json` (modifié)
- `pluribourse-frontend/public/i18n/en.json` (modifié)

**Tests frontend :**
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts` (modifié)

## Change Log

- 2026-08-20 — implémentation initiale (dev-story) : clôture d'édition (auto-Non-réclamé atomique +
  transition de phase, impression EN+FR best-effort découplée) et archivage d'édition (copie des
  articles vers `archived_items`, suppression `items`/`settlements`/`seller_profiles`, gel du rapport
  de synthèse). 22 tâches, 25 nouveaux tests backend, 10 nouveaux tests frontend, aucune régression sur
  les 490+623 tests existants.
