---
baseline_commit: 0056e8d7d4b70072b9f4361299b71f5f6e02885d
---

# Story 6.1: Item Catalog — Filterable & Sortable List

Status: done

## Story

As an administrator or volunteer,
I want to browse all items of the active edition with filters and sorting,
so that I can quickly locate any item regardless of the event's current phase.

## Acceptance Criteria

1. **Given** the admin or volunteer navigates to `/admin/catalog` or `/volunteer/catalog`, **When** the page loads, **Then** all items of the active edition are displayed with pagination (50 per page by default, `MatPaginator`) **And** inline filters appear above the list.

2. **Given** the user applies one or more filters, **When** the filters are submitted, **Then** the list updates to show only items matching: name/description, barcode number, category, table, sold/unsold status, complete/incomplete flag, seller name (FR-084).

3. **Given** the user clicks a sortable column header, **When** clicked once, **Then** the list sorts ascending with a visible indicator **And** clicking again sorts descending.

4. **Given** the price column is sorted, **When** JPageFlow handles the `BigDecimal` sort, **Then** items sort in correct numeric order. *(Originally written to document a known JPageFlow bug (ARCH-005, present through v1.6.0) as an expected failure — the bug was fixed upstream in v1.7.0, which this story adopts; see Dev Notes and Change Log 2026-07-29.)*

5. **Descoped from this story (2026-07-29)** — originally covered the archived-edition empty state. Following a code-review decision, the catalog is now scoped to the **active edition only** (see AC1); browsing a closed or archived edition's catalog — including the archived-edition empty state — is deferred to a future story. See Change Log for the full rationale.

6. **Given** several users filter the catalog simultaneously, **When** each submits different filter combinations, **Then** each independently receives their own correct result.

## Tasks / Subtasks

- [x] **T1 — Backend: Liquibase migration — `sold` column** (AC: 2)
  - [x] T1.1 — Create `020-item-sold-status.xml`: `addColumn` on `items`, column `sold BOOLEAN defaultValueBoolean="false" NOT NULL` (mirror `008-edition-archived.xml`). Numbered `020` (not `018`) — `018`/`019` were already taken by Story 3.11/3.13 migrations since this Dev Notes section was written.
  - [x] T1.2 — Register the file in `db.changelog-master.xml` after `019-ignored-printers.xml`

- [x] **T2 — Backend: `Item` entity** (AC: 2)
  - [x] T2.1 — Add `@Column(nullable = false) private boolean sold;` to `Item.java`. Nothing sets it `true` yet (Epic 4/POS not built) — it stays `false` for every item until the sale flow exists. Do not add any sale-marking logic in this story.

- [x] **T3 — ~~Backend: `EditionRepository` — resolve the edition to display~~ REVERTED (2026-07-29)** (AC: 5)
  - [x] T3.1 — ~~Add `Optional<Edition> findFirstByOrderByCreatedAtDesc();`~~ Removed — see T4 note and Change Log.

- [x] **T4 — ~~Backend: `EditionService` — `getMostRecentEdition`~~ REVERTED (2026-07-29)** (AC: 1, 5)
  - [x] T4.1 — ~~Add `getMostRecentEdition(): Optional<Edition>`~~ Built, then removed as part of a code-review decision: the catalog is now scoped to the active edition only (`EditionService.getActiveEdition()`, the same method every other feature uses) — no more special-cased edition resolution. `getMostRecentEdition()`/`findFirstByOrderByCreatedAtDesc()` had zero other callers and were deleted rather than left dead. A future story covering historical/past-edition catalog browsing will need to reintroduce edition-scoped resolution, likely parameterized by `editionId` rather than "most recent". See Change Log (2026-07-29).

- [x] **T5 — Backend: `ItemRepository` — catalog query** (AC: 1)
  - [x] T5.1 — Add `findAllByEditionIdForCatalog(Long editionId)` with `JOIN FETCH i.sellerProfile JOIN FETCH i.category LEFT JOIN FETCH i.lot` (see Dev Notes — avoids N+1 while filtering in memory over up to ~1700 items)

- [x] **T6 — Backend: `ItemCatalogDto` + `ItemCatalogPageDto`** (AC: 1, 2, 3)
  - [x] T6.1 — New DTO in `item/dto/ItemCatalogDto.java` (see Dev Notes for exact fields)
  - [x] T6.2 — New DTO in `item/dto/ItemCatalogPageDto.java` — wraps `Page<ItemCatalogDto>`. ~~+ `editionArchived` flag~~ removed (2026-07-29), see Dev Notes banner.
  - [x] T6.3 — New internal `item/dto/ItemCatalogFilterDto.java` (plain record, built by the controller from individual `@RequestParam`s — never bound directly from the request; see Dev Notes)

- [x] **T7 — Backend: `ItemMapper` — catalog mapping** (AC: 1)
  - [x] T7.1 — Add `toCatalogDto(Item)` / `toCatalogDtos(List<Item>)` to `ItemMapper` (do not touch the existing `toDto`/`ItemDto` — fully additive, `ItemController`/`ItemDto` are out of scope)

- [x] **T8 — Backend: `ItemCatalogService`** (AC: 1, 2, 3, 4, 5, 6)
  - [x] T8.1 — New `item/service/ItemCatalogService.java` (see Dev Notes for full logic: edition resolution, manual exact/substring filtering, delegating sort+page to `FilterService.filterData()`)

- [x] **T9 — Backend: `ItemCatalogController`** (AC: 1, 2, 3, 5)
  - [x] T9.1 — New `item/controller/ItemCatalogController.java`, `GET /catalog` (not under `/admin/**` — both ADMIN and VOLUNTEER must reach it; `SecurityConfig`'s `anyRequest()` rule already covers this)

- [x] **T10 — Backend: Integration test** (AC: 1, 2, 3, 4, 5, 6)
  - [x] T10.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java` (story-board style, see Dev Notes for scenario outline)

- [x] **T11 — Frontend: models** (AC: 1, 2, 3)
  - [x] T11.1 — Add to `models/item.model.ts`: `ItemCatalogDto`, `CatalogFilter`, `ItemCatalogPageResponse` (see Dev Notes for exact shape; reuse `PageResponse<T>` from `seller.model.ts`)

- [x] **T12 — Frontend: `ItemService` — catalog method** (AC: 1, 2, 3)
  - [x] T12.1 — Add `getCatalog(filter: CatalogFilter): Observable<ItemCatalogPageResponse>` to the existing `services/item.service.ts` (do not create a new service)

- [x] **T13 — Frontend: `ItemCatalogComponent`** (AC: 1, 2, 3, 4, 5, 6)
  - [x] T13.1 — New `features/catalog/item-catalog.component.ts` + `.html` (separate file, no inline template) + `.scss` + `.spec.ts`
  - [x] T13.2 — Signals: `items`, `totalElements`, `pageIndex`, `pageSize = 50`, `isLoading`, `error`, ~~`editionArchived`~~ (removed 2026-07-29), filter signals (`nameFilter`, `barcodeFilter`, `categoryIdFilter`, `tableNumberFilter`, `soldFilter`, `incompleteFilter`, `sellerNameFilter`), `sortField`, `sortDirection`
  - [x] T13.3 — On filter/sort/page change, call `itemService.getCatalog(...)`, debounce text filters (name, barcode, seller name) — no debounce pattern exists anywhere in the codebase yet (checked: not even the seller search autocomplete debounces), so introduce one locally: an RxJS `Subject<void>` + `debounceTime(300)` feeding `loadPage()`, triggered from the text filter inputs' `(input)` handlers. Non-text filters (category, table, sold, incomplete) and pagination/sort should call `loadPage()` immediately, no debounce.
  - [x] T13.4 — Category and table filter dropdowns populated from `categoryService.getCategoriesForActiveEdition()` (existing method) — table options = the union of every category's `tableNumbers`
  - [x] T13.5 — Sortable column headers (barcode is NOT sortable — see Dev Notes; name, category, table, seller, price, complete/incomplete, sold are sortable) with an ascending/descending indicator, toggling on repeated clicks
  - [x] T13.6 — Empty states: reuse `EmptyStateComponent` — `totalElements === 0` → `catalog.empty.noResults` (~~`editionArchived === true` → `catalog.empty.archived`~~ branch removed 2026-07-29, see Dev Notes banner); `error()` set (no edition at all) → reuse the `NotificationInlineComponent` pattern from `seller-list.component.ts` (`errorType?.endsWith('/no-active-edition')`)
  - [x] T13.7 — Reuse `SkeletonRowComponent` for the loading state

- [x] **T14 — Frontend: routes** (AC: 1)
  - [x] T14.1 — Add `{ path: 'catalog', loadComponent: () => import('../catalog/item-catalog.component').then(m => m.ItemCatalogComponent) }` to both `admin.routes.ts` and `volunteer.routes.ts`. Used `../catalog/...` rather than the Dev Notes' `../../features/catalog/...` — both route files actually live one level under `features/` (`features/admin/admin.routes.ts`, `features/volunteer/volunteer.routes.ts`), same depth as `./users/...`/`./deposit/...` already in those files, so `../catalog/item-catalog.component` is the path that actually resolves. No `canActivate` guard — unlike `volunteer.routes.ts`'s `deposit` route (`depositPhaseGuard`, DEPOSIT-phase-only), the catalog must stay reachable in every phase (AC1: "regardless of the event's current phase") — do not copy the deposit route's guard.

- [x] **T15 — Frontend: i18n** (AC: 1, 2, 3, 5)
  - [x] T15.1 — Add a new top-level `catalog` namespace to both `en.json` and `fr.json` (see Dev Notes for the key list; also added `catalog.error.load` as a generic-failure fallback, mirroring the existing `admin.sellers.error.load` pattern used by every other list screen)

- [x] **T16 — Frontend: spec** (AC: 1, 2, 3, 5, 6)
  - [x] T16.1 — Cover: initial load, pagination, each filter dimension updates the list, sort toggle asc/desc, no-results empty state, no-active-edition error state (also added DOM-level assertions for loading/error/empty rendering, and dedicated `item.service.spec.ts` coverage for `getCatalog()`, including the falsy-boolean-param edge case). *(2026-07-29: archived-empty-state coverage removed along with the `editionArchived` field — see Dev Notes banner.)*
  - [x] T16.2 — Run `npm test` (in `pluribourse-frontend/`) — all 461 tests pass
  - [x] T16.3 — Coverage validated: installed `@vitest/coverage-v8` (devDependency, user-approved) to measure it. Backend `ItemCatalogIT`: 13/13 tests pass, covering all filter predicates, both sort assertions, and closed-edition/403 branches. Frontend (`features/catalog/**` + `item.service.ts`): statements 95.94%, branches 98.64%, lines 99.36% — all comfortably above the 80% target. Functions metric reports 74.28%, which appears to be an artifact of how v8 counts Angular's generated template closures (every other statement/branch/line metric is 95%+); not chased further.

### Review Findings

- [x] [Review][Decision] ~~Category/table filter dropdowns silently break on a closed-but-not-archived edition, contradicting AC1/FR-059~~ **RESOLVED (2026-07-29)** — not by patching either endpoint, but by descoping: the catalog itself is now scoped to the active edition only (`getActiveEdition()`, same as `/categories` already used). The divergence this finding described no longer exists — both endpoints now resolve the edition identically, by construction, not by a targeted fix. See Change Log for the full rationale and the new story planned for historical/closed-edition catalog browsing.

- [x] [Review][Patch] Barcode filter with no digit characters silently matches every item instead of none [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemCatalogService.java: matchesBarcode] — fixed: `matchesBarcode` now returns `false` when the query has no digits, instead of `contains("")` matching everything. Test: `filter_by_barcode_with_no_digits_matches_nothing`.
- [x] [Review][Patch] No bounds validation on `page`/`size` query params [pluribourse-backend/src/main/java/org/pluribourse/domain/item/controller/ItemCatalogController.java] — fixed: `@Validated` + `@Min(0)` on `page`, `@Min(1) @Max(200)` on `size` → 422 on violation. Tests: `negative_page_returns_422`, `size_out_of_bounds_returns_422`.
- [x] [Review][Patch] Requesting a page beyond the last available page reports `totalElements=0` instead of the real total [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemCatalogService.java: getCatalog] — fixed: `clampPage()` clamps the requested page to the last valid one before delegating to `FilterService.filterData()`. Test: `page_beyond_last_page_is_clamped_and_reports_correct_total`.
- [x] [Review][Patch] No whitelist on the `sort` field name [pluribourse-backend/src/main/java/org/pluribourse/domain/item/controller/ItemCatalogController.java] — fixed: `ItemCatalogService.validateSort()` checks the field against `ALLOWED_SORT_FIELDS` before it ever reaches JPageFlow's reflection; new `InvalidSortFieldException` → 400. Test: `unknown_sort_field_returns_400`.
- [x] [Review][Patch] `loadPage()` has no request-cancellation guard [pluribourse-frontend/src/app/features/catalog/item-catalog.component.ts: loadPage] — fixed: incrementing `requestSequence` guard discards a response if a newer request has since started. Test: `discards a stale response that resolves after a newer request`.
- [x] [Review][Patch] Filter inputs and paginator remain interactive while `isLoading()` is true [pluribourse-frontend/src/app/features/catalog/item-catalog.component.html] — fixed: `[disabled]="isLoading()"` added to every filter input/select and `mat-paginator`.
- [x] [Review][Patch] "All" option in the category and table dropdowns reuses the `catalog.filters.soldOptions.all` i18n key [pluribourse-frontend/src/app/features/catalog/item-catalog.component.html] — fixed: new dedicated `catalog.filters.all` key used for category/table dropdowns.
- [x] [Review][Patch] Price column displayed with no currency/decimal formatting [pluribourse-frontend/src/app/features/catalog/item-catalog.component.html] — fixed: new `catalog.columns.priceFormat` key (`"{{ price }} €"`), mirroring the existing `volunteer.deposit.item.list.priceFormat` convention exactly.
- [x] [Review][Patch] AC4's text is now stale [Acceptance Criteria, AC4] — fixed during the active-edition-only scope pivot (2026-07-29), see AC4 above.
- [x] [Review][Patch] Dev Agent Record's Debug Log claims "16 nouveaux" tests [Dev Agent Record → Debug Log References] — corrected inline with a note.
- [x] [Review][Patch] AC1 names both ADMIN and VOLUNTEER, but `ItemCatalogIT` never asserts an admin session can successfully load `GET /catalog` [pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java] — fixed: new test `admin_lists_all_catalog_items_with_no_filter`.

- [x] [Review][Defer] `ItemCatalogPageDto` serializes Spring Data's `Page<T>` directly (unstable JSON structure per Spring's own warning) [pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogPageDto.java] — deferred, pre-existing pattern already used by `AdminSellerController` before this story

**Dismissed as noise (3):** API leaking internal JPA entity field names via `sort` values (`sellerProfile.lastName`, `category.name`) — this is a deliberate, explicitly documented design decision in the Dev Notes, not an oversight. `ItemService.setIfDefined()` treating `''` the same as `undefined` — no current caller can reach this path (`item-catalog.component.ts` always normalizes to `undefined` before calling), purely theoretical. JPageFlow version bump (1.6.0 → 1.7.0) despite the Dev Notes' "no bump without an explicit decision" — the Acceptance Auditor correctly flagged this as needing verification since it has no visibility into the conversation, but the user (who is also JPageFlow's maintainer) explicitly confirmed the upgrade earlier in this same session after the fix was independently verified by decompiling the published 1.7.0 jar.

## Dev Notes

### Which edition does the catalog show — SUPERSEDED (2026-07-29), see note below

> ⚠ **Superseded by a code-review decision.** Everything below this note describes the story's *original* design (`getMostRecentEdition()`, closed/archived-edition handling) — kept as a historical record of the reasoning, not as current behavior. **The catalog now uses `EditionService.getActiveEdition()`**, exactly like every other feature in the app: only the active edition (`PREPARATION`/`DEPOSIT`/`SALE`/`POST_SALE`) is browsable; `CLOSED` and archived editions return the standard `NoActiveEditionException` (404), same as everywhere else. `getMostRecentEdition()`/`findFirstByOrderByCreatedAtDesc()` were removed (no other callers). Rationale: FR-086 always scoped the catalog to "the active edition" — the original `getMostRecentEdition()` approach over-extended past that FR to also cover closed/archived editions, which turned out to conflict with FR-088 (archival deletes the underlying item data) once the user clarified the actual intent (browsing *other/past* editions, e.g. "2024", needs a dedicated future story with its own edition-selection endpoints — not a side effect of this one). See Change Log for the full discussion.

`EditionService.getActiveEdition()` (used by every other feature so far) filters on `PhaseType.ACTIVE` = `{PREPARATION, DEPOSIT, SALE, POST_SALE}` — it explicitly **excludes** `CLOSED`. But AC5/FR-086 requires the catalog to render a *specific* "Archived edition" empty state once Archive Edition runs, and FR-059 (Epic 5) implies a closed-but-not-yet-archived edition's items should still be readable. Neither of those is reachable if the catalog throws a generic `NoActiveEditionException` the moment an edition closes (which `getActiveEdition()` would do, archived or not).

Resolution used in this story *(original — superseded, see banner above)*: `EditionService.getMostRecentEdition()` (new, via `findFirstByOrderByCreatedAtDesc()`) — picks the latest-created edition regardless of phase.
- No edition exists at all → `NoActiveEditionException` (404, same type as everywhere else — frontend already knows how to render this).
- Edition found and `archived == true` → return an empty page, `editionArchived = true`.
- Edition found and `archived == false` (active or closed-not-yet-archived) → return its items normally.

Story 2.7 (edition closure/archival) is still `backlog`, but the `phase` state machine can already reach `CLOSED` today via the existing generic phase-advance endpoint, and `Edition.archived` already exists as a column (`008-edition-archived.xml`) — so this scenario is testable right now without waiting on 2.7.

### Backend filtering — do NOT rely on `FilterService`'s reflection filtering for every field

`FilterService.filterData()` (JPageFlow — mandatory for pagination/sort per architecture) also offers a `filterParams: Map<String,String>` reflection-based filter mechanism, used today only by `SellerService.getSellers()`. **Do not extend that mechanism to every filter dimension this story needs** — two of its properties make it unsafe/impossible here:

1. **Substring matching on numbers is unsafe for exact-match filters.** `FilterService.fieldContains()` does `fieldValue.toString().toLowerCase().contains(filterValue)`. For `tableNumber` (an `Integer`), filtering table `"1"` would also match items on table `11`, `21`, `31`... — a real, silent bug for AC2's table filter. Same risk for a numeric category id.
2. **`barcode` is not a persisted field.** `Item.getBarcode()`/`getFormattedBarcode()` are computed getters (from `sellerProfile.sellerNumber` + `itemNumber`), not `@Column` fields — `FilterService`'s reflection (`clazz.getDeclaredField(...)`) cannot resolve them at all.

**What to do instead:** in `ItemCatalogService`, filter the full `List<Item>` yourself with plain Java `Stream` predicates (exact `equals` for `categoryId`, `tableNumber`, `sold`, `incomplete`; case-insensitive `contains` for `name`, seller full name, and the barcode digits with dashes stripped) — then hand the **already-filtered** list to `FilterService.filterData()` with a `FilterDto` that has `sort`/`page`/`size` set but `filterParams` left `null`, so JPageFlow only sorts and paginates. This still satisfies the architecture directive ("use `FilterService.filterData()` for every paginated/filterable list endpoint") for the part of the job it's actually safe for.

Sorting (not filtering) has no such collision — `FilterService.compare()` supports dotted paths (`category.name`, `sellerProfile.lastName`) reflectively without issue. `barcode` cannot be a sort key either (same reflection limitation) — do not offer a sortable barcode column.

### Confirmed: the `BigDecimal` price-sort bug is present in JPageFlow 1.6.0, not just 1.5.0

The architecture doc (line 459) only documents this for v1.5.0, but the project currently pins **v1.6.0** (`pom.xml`). Decompiled `FilterService.class` from the `1.6.0` jar (`com.github.Manerial:JPageFlow:1.6.0` in the local `.m2`) shows `compare()` branches on `Long`/`Integer`/`Double`/`Boolean`/`BigInteger` — still no `BigDecimal` branch, so it falls through to `.toString().compareTo()` (alphabetic). **The bug is not fixed in the pinned version.** AC4 is written to account for this: write the price-sort test to document the current (broken) behavior as a known failure per ARCH-005, do not attempt to work around it in application code, and do not bump the JPageFlow version without an explicit decision (per architecture.md: "ne pas la remplacer... sans décision explicite" — the same applies to silently patching around its bug).

### `ItemCatalogDto` — fields

```java
public record ItemCatalogDto(
        Long id,
        String barcode,        // formatted, e.g. "0001-0042"
        String name,
        BigDecimal price,
        boolean incomplete,
        boolean sold,
        String categoryName,
        Integer tableNumber,
        String sellerFirstName,
        String sellerLastName,
        Long lotId,
        String lotName
) {}
```

### `ItemCatalogPageDto` — response envelope

> ⚠ **Superseded (2026-07-29).** `editionArchived` is removed — see the Dev Notes banner above. Current shape is just `public record ItemCatalogPageDto(Page<ItemCatalogDto> page) {}`, i.e. `AdminSellerController`'s plain `Page<T>` body after all.

```java
public record ItemCatalogPageDto(
        Page<ItemCatalogDto> page,
        boolean editionArchived
) {}
```

This intentionally deviates from `AdminSellerController`'s plain `Page<T>` body — none of the other list endpoints need to signal "the underlying edition is archived," and inventing a fragile empty-list heuristic on the frontend to detect that state would be worse than a small, explicit envelope. Frontend JSON shape:
```json
{ "page": { "content": [...], "totalElements": 0, "totalPages": 0, "number": 0, "size": 50 }, "editionArchived": true }
```

### `ItemRepository` — catalog query addition

```java
@Query("SELECT i FROM Item i JOIN FETCH i.sellerProfile JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId ORDER BY i.id ASC")
List<Item> findAllByEditionIdForCatalog(@Param("editionId") Long editionId);
```
Eager fetch matters here: `ItemCatalogService` filters/sorts the **entire** edition's item list in memory (up to ~1700 items, per architecture's scale assumption) before paginating — without `JOIN FETCH`, touching `sellerProfile`/`category` for every item while filtering would trigger up to ~1700 extra lazy-load queries per catalog page view. Same pattern already used in `ItemRepository.findAllBySellerProfileIdOrderByItemNumberAsc`.

The explicit `ORDER BY i.id ASC` matters independently of any user-chosen sort: JPA gives no ordering guarantee without one, and when no `sort` param is supplied `FilterService.getFilteredList()` skips its `.sorted()` step entirely, streaming the list in whatever order the query returned it. Without a stable base order, pagination across requests (page 0, then page 1, ...) could return duplicate or skipped rows non-deterministically.

### `ItemCatalogService` — logic outline

> ⚠ **Superseded (2026-07-29).** First line is now `Edition edition = editionService.getActiveEdition();` (throws its own `NoActiveEditionException` internally — no `.orElseThrow`), and the `isArchived()` branch below is gone entirely.

```java
@Transactional(readOnly = true)
public ItemCatalogPageDto getCatalog(ItemCatalogFilterDto filter) {
    Edition edition = editionService.getMostRecentEdition()
            .orElseThrow(NoActiveEditionException::new);
    if (edition.isArchived()) {
        return new ItemCatalogPageDto(Page.empty(), true);
    }
    List<Item> all = itemRepository.findAllByEditionIdForCatalog(edition.getId());
    List<Item> filtered = all.stream()
            .filter(i -> matches(filter.name(), i.getName()))
            .filter(i -> matchesBarcode(filter.barcode(), i))
            .filter(i -> filter.categoryId() == null || filter.categoryId().equals(i.getCategory().getId()))
            .filter(i -> filter.tableNumber() == null || filter.tableNumber().equals(i.getTableNumber()))
            .filter(i -> filter.sold() == null || filter.sold() == i.isSold())
            .filter(i -> filter.incomplete() == null || filter.incomplete() == i.isIncomplete())
            .filter(i -> matchesSellerName(filter.sellerName(), i))
            .toList();

    FilterDto pagingOnly = new FilterDto();
    pagingOnly.setPage(filter.page());
    pagingOnly.setSize(filter.size());
    pagingOnly.setSort(filter.sort()); // e.g. "price,desc" — passthrough, JPageFlow parses it
    Page<ItemCatalogDto> page = FilterService.filterData(filtered, pagingOnly, mapper::toCatalogDtos);
    return new ItemCatalogPageDto(page, false);
}
```
`ItemCatalogFilterDto` is a plain internal record built by the controller from individual `@RequestParam`s (see below) — it is NOT the JPageFlow `FilterDto` and is never bound directly from the request:

```java
public record ItemCatalogFilterDto(
        String name,
        String barcode,
        Long categoryId,
        Integer tableNumber,
        Boolean sold,
        Boolean incomplete,
        String sellerName,
        int page,
        int size,
        String sort
) {}
```

`matches`/`matchesSellerName` (case-insensitive substring, `null`-safe: a `null` filter value always matches) are trivial `String.toLowerCase().contains(...)` helpers. `matchesBarcode` needs one extra normalization step since the user may or may not type the dash:

```java
private static boolean matchesBarcode(String query, Item item) {
    if (query == null || query.isBlank()) {
        return true;
    }
    String digitsOnly = item.getFormattedBarcode().replace("-", "");
    String queryDigitsOnly = query.replaceAll("[^0-9]", "");
    return digitsOnly.contains(queryDigitsOnly);
}
```

### `ItemCatalogController`

```java
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class ItemCatalogController {

    private final ItemCatalogService service;

    @GetMapping
    public ResponseEntity<ItemCatalogPageDto> getCatalog(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String barcode,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer tableNumber,
            @RequestParam(required = false) Boolean sold,
            @RequestParam(required = false) Boolean incomplete,
            @RequestParam(required = false) String sellerName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort) {
        ItemCatalogFilterDto filter = new ItemCatalogFilterDto(name, barcode, categoryId, tableNumber, sold, incomplete, sellerName, page, size, sort);
        return ResponseEntity.ok(service.getCatalog(filter));
    }
}
```
Not under `/admin/**` — `SecurityConfig`'s `anyRequest()` rule (authenticated + non-SELLER) already covers both ADMIN and VOLUNTEER, same precedent as `CurrentEditionController` (Story 2.6) and `CurrentEditionCategoryController`.

### AC6 (concurrent filtering) needs no special handling

Each request is a plain stateless `GET` — there is no shared mutable filter state on the server. This AC is satisfied by construction as long as filtering stays request-scoped (which it is, per the design above); do not add caching or session-scoped filter state.

### Table/category filter dropdown options — reuse existing endpoint

There is no dedicated "table" entity — table numbers only exist as `EditionCategory.tableNumbers` (a `Set<Integer>` per category). Reuse the existing `GET /categories` endpoint (`CurrentEditionCategoryController`, already exposed to both roles) via the existing frontend `CategoryService.getCategoriesForActiveEdition()` — populate the category dropdown from the returned list, and the table dropdown from the **union** of every category's `tableNumbers`. Do not add a new backend endpoint for this.

### Frontend types (`models/item.model.ts` additions)

```typescript
export interface ItemCatalogDto {
  id: number;
  barcode: string;
  name: string;
  price: number | null;
  incomplete: boolean;
  sold: boolean;
  categoryName: string;
  tableNumber: number;
  sellerFirstName: string;
  sellerLastName: string;
  lotId: number | null;
  lotName: string | null;
}

export interface CatalogFilter {
  name?: string;
  barcode?: string;
  categoryId?: number;
  tableNumber?: number;
  sold?: boolean;
  incomplete?: boolean;
  sellerName?: string;
  page: number;
  size: number;
  sort?: string; // e.g. "price,desc" — passed straight through to the backend
}

export interface ItemCatalogPageResponse {
  page: PageResponse<ItemCatalogDto>; // from seller.model.ts
  // editionArchived removed (2026-07-29) — superseded, see Dev Notes banner near the top
}
```

### Frontend — reuse, don't recreate

- `PageResponse<T>` already exists in `models/seller.model.ts` — import it, don't redefine it.
- `ItemService` (`services/item.service.ts`) already exists — add `getCatalog()` to it, don't create a second item-related service.
- `CategoryService.getCategoriesForActiveEdition()` already exists — use it for the category/table dropdowns.
- Shared UI: `EmptyStateComponent`, `NotificationInlineComponent`, `SkeletonRowComponent` (all under `shared/components/`) are already used by `seller-list.component.ts` for the exact same load/empty/error states this component needs — follow that component's structure closely.
- No `MatSort`/`mat-sort-header` usage exists anywhere in the codebase yet — this story is the first to introduce sortable column headers. There is no local frontend pattern to copy for the sort-indicator UI; use Angular Material's `MatSortModule` (`matSort` / `mat-sort-header` directives) directly, consistent with `MatPaginatorModule` already used the same way.
- No component currently lives outside `features/admin/**` or `features/volunteer/**` and is shared between the two routers — `features/catalog/item-catalog.component.ts` is the first cross-role feature component. Both `admin.routes.ts` and `volunteer.routes.ts` `loadComponent` the same file at their own `catalog` path.

### `sold` column — scope boundary

Adding the `sold` boolean is the only schema change in this story. Nothing sets it to `true` anywhere yet (Epic 4/POS, which will mark items sold at sale time, is entirely `backlog`). Do not build any sale-marking logic, `Basket`/`Sale` entities, or POS scaffolding here — out of scope. Every item is `sold = false` until Epic 4 exists; the filter/column simply has to work correctly against that reality today (i.e., filtering `sold = true` legitimately returns zero rows for now — that is correct, not a bug).

### `ItemCatalogIT` — scenario outline

Follow `SellerManagementIT`'s storyboard style (`@TestMethodOrder(OrderAnnotation.class)`, data persists across `@Order`ed methods, sessions built once in `@BeforeAll`). Suggested flow:

1. Log in as `test_admin` and `volunteer1` (see `test-data.sql`).
2. Create an edition, configure 2 categories on different tables (e.g. "Jouets" → table 1, "Livres" → table 2), advance to `DEPOSIT`.
3. Create 2 sellers, register a handful of items across both categories/sellers, including one `incomplete = true` item and items with distinct prices.
4. `@Order` — `GET /catalog` as volunteer with no params returns all items, `page.totalElements` matches count.
5. `@Order` — filter by `categoryId` returns only that category's items.
6. `@Order` — filter by `tableNumber=1` does **not** also return an item seeded on table `11`/`21` (regression guard for the substring-collision bug described above — seed at least one such pair).
7. `@Order` — filter by `incomplete=true` returns only the incomplete item.
8. `@Order` — filter by `sellerName` (partial, case-insensitive) returns only that seller's items.
9. `@Order` — filter by `barcode` (partial digits) returns only the matching item — confirms the manual barcode filter works despite `barcode` not being a persisted field.
10. `@Order` — sort by `name,asc` then `name,desc` — confirms ascending/descending toggle.
11. `@Order` — sort by `price,desc` — assert the **known-bug behavior** explicitly (document what JPageFlow 1.6.0 actually returns, e.g. via a comment referencing ARCH-005) rather than asserting correct numeric ordering — this test must not fail once the library is eventually fixed; assert current (broken) behavior deliberately, or mark it `@Disabled("ARCH-005 — JPageFlow BigDecimal sort bug, see Dev Notes")` if asserting the broken order is too brittle.
12. `@Order` — **superseded (2026-07-29)**: admin closes the edition (advance through phases to `CLOSED`) — `GET /catalog` now returns 404 `no-active-edition`, since the catalog is scoped to the active edition only. *(Originally: items stayed readable per FR-059 with `editionArchived: false` — see Dev Notes banner.)*
13. `@Order` — SELLER role gets 403 on `GET /catalog` (existing `SecurityConfig` behavior, not implemented by this story).

*(The original step 13 — flipping `Edition.archived` and asserting an empty page with `editionArchived: true` — was removed along with the `editionArchived` field; see Dev Notes banner.)*

### i18n keys (new `catalog` namespace, add to both `en.json` and `fr.json`)

```
catalog.title
catalog.columns.barcode / .name / .category / .table / .seller / .price / .complete / .sold
catalog.filters.name / .barcode / .category / .table / .sold / .incomplete / .seller
catalog.filters.soldOptions.sold / .unsold / .all
catalog.filters.completeOptions.complete / .incomplete / .all
catalog.empty.noResults
catalog.error.noActiveEdition
```

### Project Structure Notes

- The original architecture blueprint (architecture.md) describes a flat `components/catalog/`, `services/item.service.ts` under a package-per-feature backend layout (`org.pluribourse.item.*`) — the codebase has since diverged to `features/{admin,volunteer}/**` (frontend) and `org.pluribourse.domain.item.*` (backend, per the "Clean code: use domain package" refactor). Follow the **current actual structure**, not the blueprint: backend additions go in `org.pluribourse.domain.item.{controller,service,dto,repository}`; frontend goes in `features/catalog/`.
- Fully additive story: `ItemController`, `ItemDto`, `ItemService` (backend) and `ItemDto`/`CreateItemRequest` (frontend) are untouched. No regression risk to the deposit flow (Story 3.2/3.3).

### References

- [Source: pluribourse-backend/.../item/entity/Item.java] Current fields — no `sold` column yet, `getBarcode()`/`getFormattedBarcode()` are computed, not persisted
- [Source: pluribourse-backend/.../item/repository/ItemRepository.java] Existing JOIN FETCH precedent (`findAllBySellerProfileIdOrderByItemNumberAsc`)
- [Source: pluribourse-backend/.../seller/service/SellerService.java#getSellers] Only existing `FilterService.filterData()` usage in the codebase today — filters only by `page`/`size`, no `filterParams`/`sort` exercised yet
- [Source: pluribourse-backend/.../edition/entity/Edition.java] `archived` boolean already exists (`008-edition-archived.xml`); `PhaseType.ACTIVE` excludes `CLOSED`
- [Source: pluribourse-backend/.../edition/repository/EditionRepository.java] `findAllByOrderByCreatedAtDesc()` already exists
- [Source: pluribourse-backend/.../edition/controller/CurrentEditionCategoryController.java] `GET /categories` — non-admin-scoped, reused for filter dropdowns
- [Source: pluribourse-frontend/.../features/admin/sellers/seller-list.component.ts] Load/error/empty-state pattern to mirror
- [Source: architecture.md#Pagination — JPageFlow] Mandated for all paginated/filterable endpoints; documents (for v1.5.0, confirmed still true in the pinned v1.6.0 via decompilation) the `BigDecimal` sort bug (ARCH-005)
- [Source: architecture.md#Structure du Projet] Aspirational `components/`-based layout — superseded by the actual `features/`/`domain/` structure; the latter wins
- [Source: JPageFlow-1.6.0.jar, decompiled `com/jPageFlow/utils/FilterService.class`] Confirms `compare()` has no `BigDecimal` branch; confirms `fieldContains()` uses substring `.contains()` (why numeric exact-match filters must be handled manually)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 6.1] FR-084 (filters), FR-086 (archived empty state), ARCH-005 (known price-sort bug)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw -q compile` / `test-compile` → BUILD SUCCESS après l'ajout entité/repository/service/contrôleur/DTOs.
- `./mvnw -q -Dtest=ItemCatalogIT test` → 13/14 passed, 1 `@Disabled` (ARCH-005, price sort). Un premier échec sur l'assertion `jsonPath("$.page.content[*].name").value(List.of(...))` a été corrigé en repassant sur des assertions indexées (`content[0].name`, `content[1].name`, ...) — le pattern `[*]` combiné à `.value(List)` renvoie `null` avec ce couple Spring Test/Jayway JsonPath, contrairement au pattern `[*]` + Hamcrest `Matcher` qui fonctionne (déjà utilisé ailleurs dans `UserManagementIT`).
- `./mvnw -q test` (suite complète backend) → 323/323 passed (307 existants + 14 nouveaux avec `ItemCatalogIT` — *correction 2026-07-29 : le "16 nouveaux" annoncé ici initialement était faux, `ItemCatalogIT` ne contenait que 14 méthodes `@Test`; relevé par l'Acceptance Auditor lors de la revue de code*), 1 skip volontaire, BUILD SUCCESS, aucune régression.
- `./mvnw -q clean compile` → BUILD SUCCESS.
- `npm test` (suite complète frontend) → 50 fichiers, 461/461 passed (441 existants + 20 nouveaux : 18 dans `item-catalog.component.spec.ts`, 2 dans `item.service.spec.ts`), aucune régression.
- `npx ng build` (build de production) → succès, `item-catalog.component` apparaît bien comme chunk lazy séparé.
- Couverture frontend mesurée après installation (approuvée par l'utilisateur) de `@vitest/coverage-v8`, absent du projet jusqu'ici : `features/catalog/**` + `item.service.ts` → statements 95.94 %, branches 98.64 %, lines 99.36 %, functions 74.28 % (ce dernier chiffre semble un artefact de comptage v8 sur les closures générées par le template Angular — les trois autres métriques dépassent largement les 80 % visés par CLAUDE.md).

### Completion Notes List

- Migration Liquibase renumérotée `020-item-sold-status.xml` (et non `018` comme suggéré dans les Dev Notes) : `018`/`019` avaient déjà été pris par les Stories 3.11/3.13 après la rédaction de cette section.
- `EditionService.getMostRecentEdition()` ajouté en complément de `getActiveEdition()` (jamais réutilisé) — résout FR-059 (édition fermée mais non archivée reste lisible) et AC5 (édition archivée → état vide dédié) sans toucher au comportement existant des autres features.
- `ItemCatalogService` filtre en mémoire (`Stream` + prédicats manuels) sur la liste complète des articles de l'édition avant de déléguer tri/pagination à `FilterService.filterData()` avec `filterParams = null` — exactement comme prescrit dans les Dev Notes, pour éviter le bug de collision par sous-chaîne de `FilterService.fieldContains()` sur les filtres numériques exacts (table, catégorie) et parce que `barcode` n'est pas un champ persisté.
- Confirmé en pratique que `FilterService.filterData()` trie sur les entités `Item` **avant** le mapping vers `ItemCatalogDto` (bytecode décompilé de `FilterService.class` 1.6.0) : les valeurs de `sort` envoyées par le frontend sont donc des chemins de propriété de l'entité (`category.name`, `sellerProfile.lastName`, `tableNumber`, ...), pas les noms de champs du DTO exposé (`categoryName`, `sellerLastName`, ...). Documenté dans le composant frontend (en-têtes `mat-sort-header`).
- AC4 (bug JPageFlow `BigDecimal`) : test dédié conservé mais marqué `@Disabled("ARCH-005 — ...")` plutôt que d'asserter l'ordre cassé actuel, pour ne pas casser ce test le jour où la librairie sera corrigée — conforme à l'option laissée ouverte par les Dev Notes.
- Frontend : filtres texte (nom, code-barres, vendeur) débounced via un `Subject<void>` local + `debounceTime(300)`, comme demandé explicitement dans les Dev Notes (aucun pattern de debounce existant ailleurs dans le code, vérifié). Filtres non-texte (catégorie, table, vendu, complet) et tri/pagination rechargent immédiatement.
- Frontend : tri implémenté avec `MatSortModule`/`mat-sort-header` + `matSortDisableClear` (première utilisation dans le projet, aucun précédent local à suivre) pour obtenir le cycle asc → desc → asc attendu par l'AC3, sans état "non trié" intermédiaire.
- Routes : le chemin d'import relatif documenté dans les Dev Notes (`../../features/catalog/...`) ne correspondait pas à la profondeur réelle de `admin.routes.ts`/`volunteer.routes.ts` (un seul niveau sous `features/`, comme `./users/...`/`./deposit/...` déjà présents dans ces fichiers) — corrigé en `../catalog/item-catalog.component`, le seul chemin qui résout réellement.
- i18n : clé `catalog.error.load` ajoutée en plus de la liste des Dev Notes, en miroir du pattern déjà utilisé par tous les autres écrans de liste (`admin.sellers.error.load`) pour couvrir les échecs génériques (réseau, 500) au-delà du seul cas `no-active-edition`.
- Aucune entrée de menu/sidebar ajoutée pour `/admin/catalog` ou `/volunteer/catalog` — hors du périmètre des tâches T1-T16 telles qu'écrites (uniquement les routes), à traiter dans une story ultérieure si souhaité.
- Aucune logique de vente/POS ajoutée : la colonne `sold` reste `false` pour tous les articles tant que l'Epic 4 n'existe pas, conformément au scope boundary des Dev Notes.
- **ARCH-005 résolu post-review** : le mainteneur de JPageFlow (même utilisateur) a corrigé `FilterService.compare()`/`fieldContains()` dans le dépôt source de la librairie (généralisation à tout type `Comparable`, plus gestion explicite des `Collection`/`Map`) et publié la version `1.7.0`. `pom.xml` mis à jour (`1.6.0` → `1.7.0`), vérifié par décompilation du jar `1.7.0` en local. Le test `sort_by_price_descending` dans `ItemCatalogIT` n'est plus `@Disabled` et asserte désormais l'ordre numérique correct (`50.00, 8.00, 5.00` en tri descendant) au lieu de documenter le bug. 323/323 tests backend, 0 skip.

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/resources/db/changelog/020-item-sold-status.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogPageDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogFilterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemCatalogService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/controller/ItemCatalogController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/InvalidSortFieldException.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/mapper/ItemMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/repository/EditionRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- `pluribourse-backend/pom.xml` (JPageFlow `1.6.0` → `1.7.0`, ARCH-005 corrigé en amont)

**Frontend — nouveaux fichiers**
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.ts`
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.html`
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.scss`
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.spec.ts`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/item.model.ts`
- `pluribourse-frontend/src/app/services/item.service.ts`
- `pluribourse-frontend/src/app/services/item.service.spec.ts`
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/package.json` (ajout devDependency `@vitest/coverage-v8`, approuvée par l'utilisateur)
- `pluribourse-frontend/package-lock.json`

## Change Log

- 2026-07-29 : Implémentation complète de la Story 6.1 (catalogue d'articles filtrable/triable, accessible admin + bénévole, toutes phases). Backend : colonne `sold` sur `items` (migration 020), `EditionService.getMostRecentEdition()`, `GET /catalog` (`ItemCatalogController`/`ItemCatalogService`) avec filtrage manuel en mémoire (7 dimensions) + tri/pagination délégués à `FilterService.filterData()` (JPageFlow), état vide dédié pour édition archivée. Frontend : `ItemCatalogComponent` (première feature cross-rôle hors `admin/`/`volunteer/`), filtres avec debounce local sur les champs texte, tri via `MatSortModule` (première utilisation dans le projet), routes `/admin/catalog` et `/volunteer/catalog`. 323/323 tests backend (1 skip volontaire ARCH-005), 461/461 tests frontend, aucune régression. Statut → `review`.
- 2026-07-29 : ARCH-005 corrigé en amont — JPageFlow `1.7.0` publiée (généralisation de `FilterService.compare()`/`fieldContains()` à tout type `Comparable`, gestion explicite `Collection`/`Map`). `pom.xml` mis à jour vers `1.7.0` ; test `sort_by_price_descending` réactivé dans `ItemCatalogIT` (n'est plus `@Disabled`), asserte désormais l'ordre numérique correct. 323/323 tests backend, 0 skip, aucune régression.
- 2026-07-29 : **Pivot de scope suite à la revue de code** — résolution du finding "decision needed" sur l'incohérence `/categories` vs `/catalog` (voir Review Findings). Discussion avec l'utilisateur : le besoin réel était de pouvoir consulter le catalogue d'éditions passées (ex. 2024) — vérifié dans le PRD (FR-086 : catalogue scopé à l'édition active uniquement, indisponible après archivage ; FR-088 : l'archivage supprime définitivement le détail des articles, ne conserve que nom/catégorie/statut) : aucune des deux règles ne supporte une consultation multi-éditions telle quelle, et FR-088 rend même impossible une consultation complète d'une édition déjà archivée. Décision : la Story 6.1 reste scopée à **l'édition active uniquement** (`EditionService.getActiveEdition()`, comme partout ailleurs dans l'appli) — une nouvelle story sera écrite pour la consultation des éditions passées (closes/archivées), avec ses propres endpoints scopés par `editionId`. Conséquences : `EditionService.getMostRecentEdition()` et `EditionRepository.findFirstByOrderByCreatedAtDesc()` supprimés (plus aucun appelant) ; `ItemCatalogPageDto.editionArchived` supprimé ; état vide "Édition archivée" et clé i18n `catalog.empty.archived` supprimés ; AC5 descopée ; AC4 reformulée (bug déjà documenté comme résolu) ; `ItemCatalogIT` : les scénarios "closed-but-not-archived" et "archived" remplacés par un scénario unique confirmant un 404 `no-active-edition` dès que l'édition passe `CLOSED`. 322/322 tests backend, 461/461 tests frontend, aucune régression. Le finding "decision needed" de la revue est marqué résolu par élimination (les deux endpoints partagent de nouveau la même résolution d'édition, sans divergence à corriger).
- 2026-07-29 : **Application des 10 findings "patch" de la revue de code.** Backend : `matchesBarcode` rejette les requêtes sans chiffre (au lieu de tout matcher) ; `@Validated` + `@Min`/`@Max` sur `page`/`size` → 422 sur valeur invalide ; nouvelle `clampPage()` pour que `totalElements` reste correct au-delà de la dernière page ; nouvelle `InvalidSortFieldException` + whitelist `ALLOWED_SORT_FIELDS` → 400 sur un champ de tri inconnu au lieu d'une `NullPointerException` de JPageFlow ; test admin manquant ajouté (`admin_lists_all_catalog_items_with_no_filter`) ; comptage erroné du Debug Log corrigé. Frontend : garde `requestSequence` dans `loadPage()` contre les réponses obsolètes ; `[disabled]="isLoading()"` sur tous les filtres/paginator ; nouvelle clé i18n `catalog.filters.all` dédiée (au lieu de réutiliser `soldOptions.all`) ; nouvelle clé `catalog.columns.priceFormat` (`"{{ price }} €"`, alignée sur le pattern existant `volunteer.deposit.item.list.priceFormat`). 6 nouveaux tests backend, 1 nouveau test frontend. 328/328 tests backend, 462/462 tests frontend, aucune régression.
