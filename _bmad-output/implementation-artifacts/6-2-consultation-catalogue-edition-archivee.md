---
baseline_commit: bec959b0288b85d3572d39ad5b2d9eb9d214c6cd
---

# Story 6.2: Consultation du catalogue d'une édition archivée

Status: done

## Story

As an administrator,
I want to browse the archived catalog of a past edition,
so that I can retrieve an edition's item history after its closure and archiving.

## Acceptance Criteria

1. **Given** the admin navigates to the archived-catalog consultation screen, **When** the page loads, **Then** a selector lists all archived editions (name, dates) **And** no items are displayed until an edition is selected.
2. **Given** the admin selects an archived edition, **When** the selection is confirmed, **Then** the list of that edition's archived items is displayed with pagination (50 per page, MatPaginator), limited to the data preserved by archiving: name, category, sold/unsold status (FR-102).
3. **Given** the user applies one or more filters, **When** the filters are submitted, **Then** the list updates, filtered by name, category, sold/unsold status — no barcode/table/seller filter, since that data no longer exists after archiving (FR-088).
4. **Given** the user clicks a sortable column header, **When** clicked once, **Then** the list sorts ascending with a visible indicator **And** clicking again sorts descending.
5. **Given** a volunteer (non-admin) attempts to reach this consultation, **When** the request is sent, **Then** access is denied (403) — admin-only.

## Tasks / Subtasks

- [x] **T1 — Backend: `domain.archive.dto` — `ArchivedItemDto`, `ArchivedItemFilterDto`, `ArchivedItemPageDto`** (AC: 2, 3)
  - [x] `ArchivedItemDto(Long id, String name, String categoryName, boolean sold)` — mirrors `ArchivedItem`'s 3 preserved fields exactly (`domain/archive/dto/ArchivedItemDto.java`).
  - [x] `ArchivedItemFilterDto(String name, String categoryName, Boolean sold, int page, int size, String sort)` — same shape/rationale as `ItemCatalogFilterDto` (`domain/item/dto/ItemCatalogFilterDto.java`): built by the controller from individual `@RequestParam`s, never bound directly (`domain/archive/dto/ArchivedItemFilterDto.java`).
  - [x] `ArchivedItemPageDto(Page<ArchivedItemDto> page)` (`domain/archive/dto/ArchivedItemPageDto.java`) — same wrapper shape as `ItemCatalogPageDto`.

- [x] **T2 — Backend: `ArchivedItemMapper`** (AC: 2)
  - [x] `domain/archive/mapper/ArchivedItemMapper.java`, `@Mapper(componentModel = "spring")`. `ArchivedItemDto toDto(ArchivedItem item)` (field names already match 1:1 — no `@Mapping` needed) and `List<ArchivedItemDto> toDtos(List<ArchivedItem> items)`.

- [x] **T3 — Backend: `ArchivedItemService`** (AC: 2, 3, 4)
  - [x] `domain/archive/service/ArchivedItemService.java`, `@Service @RequiredArgsConstructor`, depends on `EditionService`, `ArchivedItemRepository`, `ArchivedItemMapper`.
  - [x] `getArchivedCatalog(Long editionId, ArchivedItemFilterDto filter)`, `@Transactional(readOnly = true)`:
    1. `editionService.requireEdition(editionId)` — reused as-is purely to 404 (`EditionNotFoundException`) on an unknown edition id; **do not** additionally guard on `edition.isArchived()`. `archived_items` rows only ever exist for editions `EditionArchivingService` has actually archived (the only writer to that table) — a non-archived edition id naturally yields an empty page with no data-integrity risk, and the frontend selector (T9) already restricts selection to archived editions only. Adding a second guard/exception here would be unused defensive code with no AC backing it.
    2. Validate `filter.sort()` against a whitelist `Set.of("name", "categoryName", "sold")` — copy `ItemCatalogService.validateSort`'s exact approach (throws the **existing** `org.pluribourse.domain.item.exception.InvalidSortFieldException` — its message ("Cannot sort the catalog by field: ...") is generic enough to reuse verbatim; do not create a new exception class for this).
    3. `List<ArchivedItem> all = archivedItemRepository.findAllByEditionId(editionId)` (already exists, added proactively by Story 2.7 for exactly this use).
    4. Filter in-memory exactly like `ItemCatalogService.getCatalog`: `name` — case-insensitive substring match; `categoryName` — **exact** match (dropdown-driven on the frontend, see T9, not free text); `sold` — tri-state exact match when non-null.
    5. Paginate with `FilterService.filterData` (`com.jPageFlow.utils.*`) exactly like `ItemCatalogService`: build a paging-only `FilterDto` (page/size/sort), reuse the **same `clampPage` logic** (JPageFlow returns `Page.empty()` — losing `totalElements` — for a page number past the last one; copy the private static helper locally, do not extract a shared utility for a second 1-call-site usage).
    6. Return `new ArchivedItemPageDto(page)`.

- [x] **T4 — Backend: `ArchivedItemController`** (AC: 2, 3, 4, 5)
  - [x] `domain/archive/controller/ArchivedItemController.java`, `@RestController @RequestMapping("/admin/archive/editions/{editionId}/items") @PreAuthorize("hasRole('ADMIN')") @Validated @RequiredArgsConstructor` — explicit class-level guard, same convention as `EditionController`/`EditionCategoryController` (not the implicit `SecurityConfig` `anyRequest()` rule `ItemCatalogController` relies on, since this endpoint is admin-only, not admin+volunteer).
  - [x] `@GetMapping` returning `ResponseEntity<ArchivedItemPageDto>`, params: `name` (optional String), `categoryName` (optional String), `sold` (optional Boolean), `page` (`@RequestParam(defaultValue = "0") @Min(0)`), `size` (`@RequestParam(defaultValue = "50") @Min(1) @Max(200)` — reuse `ItemCatalogController.MAX_PAGE_SIZE = 200` as a local constant), `sort` (optional String). Build `ArchivedItemFilterDto`, delegate to `ArchivedItemService.getArchivedCatalog(editionId, filter)`.

- [x] **T5 — Backend: integration test `ArchivedCatalogIT`** (AC: 1–5)
  - [x] Package `org.pluribourse.domain.archive`, extends `IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`, storyboard style, data persists across `@Order` methods.
  - [x] Setup: reuse `EditionArchivingIT`'s exact pattern (`pluribourse-backend/src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java`) to get an edition through PREPARATION→...→CLOSED via real HTTP `phase/advance` calls, with ≥2 categories, ≥2 sellers, a mix of sold/unsold items (including one lot, archived member-by-member per Story 2.7), then `POST /api/admin/editions/{id}/archive` to populate `archived_items`.
  - [x] Scenarios: `GET /api/admin/archive/editions/{id}/items` as admin → 200, page content matches the archived items (name/categoryName/sold only, no barcode/table/seller/price/lot fields exist on the DTO); filter by `name` (substring, case-insensitive); filter by `categoryName` (exact); filter by `sold=true`/`false`; sort by `name`/`categoryName`/`sold` ascending then descending (`sort=name,asc` / `sort=name,desc`) — for `sold`, assert the natural boolean order (`false` before `true` ascending, reversed descending); invalid `sort` field → 400 `invalid-sort-field`; pagination with `size` smaller than the archived item count → correct `totalElements`/`content` size across ≥2 pages; **as volunteer** → 403; a **second** archived edition with its own distinct items → its `GET` returns only its own items (edition-scoping regression guard); an edition that exists but was never archived (e.g. a fresh PREPARATION edition) → 200 with an empty page (not 404 — only a genuinely unknown edition id 404s, per T3 step 1).

- [x] **T6 — Frontend: `archived-item.model.ts`** (AC: 1, 2, 3)
  - [x] New file `pluribourse-frontend/src/app/models/archived-item.model.ts`: `ArchivedItemDto { id: number; name: string; categoryName: string; sold: boolean; }`; `ArchivedCatalogFilter { name?: string; categoryName?: string; sold?: boolean; page: number; size: number; sort?: string; }`; `ArchivedItemPageResponse { page: PageResponse<ArchivedItemDto>; }` (import `PageResponse` from `./seller.model`, same as `item.model.ts`).

- [x] **T7 — Frontend: `archived-item.service.ts`** (AC: 1, 2, 3, 4)
  - [x] New file `pluribourse-frontend/src/app/services/archived-item.service.ts`, `providedIn: 'root'`. `getArchivedCatalog(editionId: number, filter: ArchivedCatalogFilter): Observable<ArchivedItemPageResponse>` → `GET /api/admin/archive/editions/${editionId}/items`, building `HttpParams` exactly like `ItemService.getCatalog` (`page`/`size` always set, others via a `setIfDefined` helper — copy that private helper locally, same as `ItemService`'s own, no shared extraction for a 2nd use).

- [x] **T8 — Frontend: i18n `fr.json`/`en.json`** (AC: 1, 2, 3, 4, 5)
  - [x] New top-level-under-`admin` namespace `admin.archivedCatalog` (sibling of the existing `admin.reports`, `admin.printers`): `title`, `editionSelector.label`, `editionSelector.placeholder` (shown when no archived edition exists yet), `filters.name`, `filters.category`, `filters.categoryAll`, `filters.sold`, `filters.soldOptions.all/sold/unsold`, `columns.name/category/sold`, `empty.noSelection` (before any edition is picked), `empty.noResults` (selected edition has a filter combination matching nothing), `error.load`.
  - [x] Add `nav.admin.archivedCatalog` under the existing `nav.admin` object (`fr.json`: "Catalogue archivé").

- [x] **T9 — Frontend: `ArchivedCatalogComponent`** (AC: 1, 2, 3, 4)
  - [x] New standalone component, `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.{ts,html,scss,spec.ts}` (new folder, sibling of `features/admin/editions/`, `features/admin/sellers/`, etc. — this screen is admin-only, unlike the shared `features/catalog/` used by both roles for the active-edition catalog).
  - [x] On init, load `editionService.getAll()` and keep only `archived === true` editions (reuses `EditionService.getAll()` as-is — same `filter(e => e.archived)` pattern already used for `phase === 'CLOSED'` in `edition-categories.component.ts:88`; no new backend endpoint). Sort by `startDate` descending (most recent past edition first) — not specified by any AC/source doc, but the only edition attribute (name/dates) exposed by the selector per AC 1, so this is the natural default; do not add a sort-order UI control, out of scope.
  - [x] Edition selector: `mat-select` bound to a `selectedEditionId` signal, same pattern as `edition-categories.component.html`'s source-edition picker (lines 19–32) — options are `{{ ed.name }}` (AC 1 only requires name/dates in the selector; if a "dates" sub-label is wanted, use the existing `edition.list.*` date-formatting i18n convention already established in `edition-list.component.html`, do not invent a new date format key).
  - [x] On selection change, reset `pageIndex` to 0 and load page 0 via `archivedItemService.getArchivedCatalog`. No items are fetched/displayed before a selection exists (AC 1) — mirror `ItemCatalogComponent`'s `loadPage`/`requestSequence` out-of-order-response guard (copy the pattern, including the debounced text-filter `Subject` for the `name` filter, `TEXT_FILTER_DEBOUNCE_MS = 300`).
  - [x] Filters: `name` (debounced text input), `categoryName` (`mat-select` populated from `categoryService.getCategories(selectedEditionId)` — reused as-is, works for any edition id regardless of phase, see Dev Notes § Category filter below), `sold` (tri-state `mat-select`, same `TriState = boolean | null` pattern as `ItemCatalogComponent`).
  - [x] Table: 3 sortable columns (`name`, `categoryName`, `sold`) with `matSort`/`mat-sort-header`, `MatPaginator` (50/page, `hidePageSize`), skeleton-row while loading, empty-state component both for "nothing selected yet" and "no results for this filter" (two distinct messages, see T8), same structural pattern as `item-catalog.component.html` minus the barcode/table/seller/price columns.

- [x] **T10 — Frontend: `admin.routes.ts` + sidebar nav entry** (AC: 1)
  - [x] Add a lazy route in `pluribourse-frontend/src/app/features/admin/admin.routes.ts`: `path: 'archived-catalog'`, `loadComponent` → `ArchivedCatalogComponent`. Already covered by the parent `/admin` route's `adminGuard` (`app.routes.ts`) — no new guard needed, and satisfies AC 5 (403/redirect for non-admins) together with the backend's own `@PreAuthorize` (T4).
  - [x] Add a sidebar entry in `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`, in the `nav.sections.management` block (alongside `reports`/`printers`/`users`/`settings`/`print-queue`/`settlement`), `routerLink="/admin/archived-catalog"`, icon `inventory_2` (same icon already used by the active catalog's empty-state, `item-catalog.component.html:71`), label `nav.admin.archivedCatalog`. **Story 6.1 deliberately left both `/admin/catalog` and this future screen's nav entries out of scope** (`_bmad-output/implementation-artifacts/6-1-catalogue-articles-liste-filtrable-triable.md:411`) — this task only adds the entry for **this** new route; do not also add one for `/admin/catalog`, that remains explicitly out of scope for this story.

- [x] **T11 — Frontend: `archived-catalog.component.spec.ts`** (AC: 1, 2, 3, 4)
  - [x] Mock `EditionService`/`CategoryService`/`ArchivedItemService`. Tests: selector lists only `archived === true` editions; no HTTP call to load items fires before a selection; selecting an edition loads page 0 and renders rows; name/category/sold filters each trigger a reload with the right params and reset to page 0; sort header click toggles asc/desc and reloads; pagination event loads the requested page; out-of-order response guard (mirror `ItemCatalogComponent`'s `requestSequence` test if one exists in `item-catalog.component.spec.ts`, otherwise the equivalent new test here).

### Review Findings

- [x] [Review][Patch] `categoryName=""` traité comme un filtre littéral au lieu de "aucun filtre" dans `ArchivedItemService` — incohérent avec le traitement de `name` (qui utilise `isBlank()`) [pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java:386]
- [x] [Review][Patch] Ordre de validation inversé dans `getArchivedCatalog` : `validateSort()` est appelé avant `requireEdition()`, contrairement à l'ordre documenté en T3 (1. `requireEdition`, 2. `validateSort`) — une édition inexistante combinée à un `sort` invalide renvoie 400 au lieu de 404, non testé [pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java:380-382]
- [x] [Review][Patch] AC1 non respecté : le sélecteur d'édition n'affiche que le nom, pas les dates, alors que l'AC1 exige "name, dates" — les Dev Notes pointent déjà vers la convention de formatage de dates existante (`edition.list.*`) à réutiliser [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html]
- [x] [Review][Patch] Aucune erreur remontée si le chargement des éditions archivées (`ngOnInit`) ou des catégories (`loadCategories`) échoue — retombe silencieusement sur un tableau vide, indiscernable d'un état "vide légitime" ; pas d'état de chargement distinct avant la résolution du fetch initial [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts]
- [x] [Review][Patch] `loadCategories` n'a pas de garde `requestSequence` — un changement rapide d'édition peut laisser une réponse de catégories obsolète écraser le dropdown de la nouvelle édition sélectionnée [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts:133-140]
- [x] [Review][Patch] `onEditionChange` ne réinitialise pas `error()` avant d'attendre les catégories/articles de la nouvelle édition — une notification d'erreur obsolète peut apparaître brièvement pour la nouvelle sélection [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts:89-106]
- [x] [Review][Patch] Les clés i18n `admin.archivedCatalog.filters.soldOptions.sold/unsold` (conçues comme libellés de filtre) sont réutilisées telles quelles comme texte de cellule dans la colonne "sold" du tableau — couplage entre deux usages UI distincts [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html, pluribourse-frontend/public/i18n/fr.json, pluribourse-frontend/public/i18n/en.json]
- [x] [Review][Patch] Trous de couverture de tests dans `ArchivedCatalogIT` : `clampPage` jamais testé sur une page hors limites, borne `size=200/201` jamais testée, combinaison de filtres (ex. `name`+`sold`) jamais testée [pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java]
- [x] [Review][Patch] Le bloc changelog ajouté dans `sprint-status.yaml` mélange deux récits chronologiques différents ("implémentée, statut → review" suivi immédiatement d'un reliquat "créée, statut → ready-for-dev") sans séparation claire [_bmad-output/implementation-artifacts/sprint-status.yaml]
- [x] [Review][Defer] Le jeton de direction du tri (ex. `sort=name,sideways`) n'est jamais validé [pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java:406-413] — deferred, pre-existing : reproduit fidèlement `ItemCatalogService.validateSort`, copié tel quel selon les Dev Notes.
- [x] [Review][Defer] Pas d'annulation des requêtes HTTP (pas de `switchMap`) lors de changements rapides de filtre/tri/page [pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts] — deferred, pre-existing : hérité tel quel du pattern `ItemCatalogComponent`, explicitement demandé par les Dev Notes.
- [x] [Review][Defer] Chargement complet en mémoire de tous les articles archivés d'une édition avant filtrage/pagination [pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java:383] — deferred, pre-existing pattern : réutilisation délibérée du pattern JPageFlow de `ItemCatalogService` selon les Dev Notes ; risque de passage à l'échelle réel à surveiller à mesure que les archives s'accumulent sur plusieurs années.
- [x] [Review][Defer] `toLowerCase()` sans `Locale` explicite dans le filtre `name` (risque de bug "Turkish-I") [pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java:420] — deferred, pre-existing : hérité tel quel du pattern `ItemCatalogService.getCatalog`.

## Dev Notes

### Data model — nothing new to migrate

The `archived_items` table, `ArchivedItem` entity, and `ArchivedItemRepository.findAllByEditionId` **already exist**, built by Story 2.7 specifically anticipating this story (see `ArchivedItemRepository.java:10`: *"unused by this story, but the natural query Story 6.2 will need"*). **No new Liquibase migration.** `ArchivedItem` has exactly 3 preserved fields — `name`, `categoryName` (plain string, no FK to `Category`), `sold` — plus `id`/`edition`. There is no `price`, `barcode`, `tableNumber`, `sellerProfile`, or `lot` reference: these were deliberately dropped at archive time (FR-088), which is why AC 3 explicitly excludes them from filtering.

### Category filter — reuses `EditionCategoryController`, does not join `ArchivedItem` to `Category`

`ArchivedItem.categoryName` is a decoupled string snapshot, not an FK — there is no join path from an archived item back to a live `Category` row. `GET /admin/editions/{editionId}/categories` (`EditionCategoryController`, existing, no phase restriction — works for any edition id, any phase, admin-only) already returns that edition's category definitions and is safe to reuse for the filter dropdown's option list, since `EditionArchivingService` never deletes `Category`/`EditionCategory` rows (only `Item`, `Settlement`, `SellerProfile`). The category filter itself is applied as an **exact string match** against `ArchivedItemDto.categoryName` (the dropdown value), not a foreign-key filter — accept the narrow edge case where a category was renamed *after* this edition was archived (dropdown then shows the current name, which won't exact-match the frozen `categoryName` values) as a cosmetic, extremely rare inconsistency; do not build reconciliation logic for it, no AC requires it.

### Pattern to replicate — `ItemCatalogService`/`ItemCatalogController`/`ItemCatalogComponent` (Story 6.1)

This story is structurally a smaller twin of Story 6.1's active-edition catalog, scoped to a single archived edition (by path variable, not "the active edition") and with 3 fields instead of 11. Reuse every established convention verbatim: JPageFlow `FilterService.filterData` on an in-memory-filtered list (not `filterParams` reflection — same reasoning as 6.1: exact-match dimensions here, `categoryName`/`sold`, are straightforward, but consistency with the sibling service matters more than a marginal reflection-based rewrite), the `clampPage` bug workaround, the `ALLOWED_SORT_FIELDS` whitelist + `InvalidSortFieldException` guard, the frontend's debounced-text-filter + `requestSequence` out-of-order-response guard, and the skeleton-row/empty-state/notification-inline UI components.

### Why a new controller/service instead of extending `ItemCatalogService`

`ItemCatalogService.getCatalog` is explicitly scoped to `editionService.getActiveEdition()` and documented as out of scope for past editions (`ItemCatalogService.java:39-43`: *"a closed or archived edition's catalog is out of scope for this story (see a future story for historical/past-edition catalog browsing)"* — this **is** that future story). The entity (`ArchivedItem` vs `Item`), DTO shape, and access scope (admin-only vs admin+volunteer) all differ enough that a parallel `domain.archive` service/controller/mapper is the right boundary — same reasoning already established for `EditionArchivingService` living in `domain.archive` rather than being bolted onto `domain.item`.

### Not in scope (do not implement)

- **Story 6.1's catalog empty state** ("Édition archivée — aucun article.") on `/admin/catalog`/`/volunteer/catalog` — that was explicitly deferred by Story 2.7's Dev Notes to whatever screen 6.2 builds, but 6.2's epics.md ACs describe a **separate, new** consultation screen (edition selector + its own list), not a modification of the existing live catalog's empty state. Do not touch `ItemCatalogService`/`ItemCatalogComponent`.
- Any endpoint filtering archived editions server-side — the selector reuses `GET /admin/editions` (`EditionService.getAll()`, frontend `EditionService.getAll()`) and filters `archived === true` client-side, exactly like the existing `phase === 'CLOSED'` filter in `edition-categories.component.ts:88`. No new "list archived editions" endpoint.
- Cross-edition comparative statistics — explicitly named in `sprint-change-proposal-2026-07-29.md` and PRD FR-102 as a *future* need (reserved as FR-103), not this story's job.

### Testing standards (CLAUDE.md)

E2E through controllers only, one class per business scenario, `@TestMethodOrder(OrderAnnotation.class)`, data persists across `@Order` methods (no class-level `@Transactional`), extends `org.pluribourse.shared.IntegrationTest`. Reuse `EditionArchivingIT`'s real-HTTP setup pattern to reach an archived edition rather than inserting `ArchivedItem` rows directly via the repository.

### Project Structure Notes

- New package: `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/{dto,mapper,controller}` — `ArchivedItemDto`, `ArchivedItemFilterDto`, `ArchivedItemPageDto`, `ArchivedItemMapper`, `ArchivedItemService` (in the existing `domain/archive/service/`), `ArchivedItemController`.
- New test: `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java`.
- New frontend: `models/archived-item.model.ts`, `services/archived-item.service.ts`, `features/admin/archived-catalog/archived-catalog.component.{ts,html,scss,spec.ts}`.
- Modified frontend: `features/admin/admin.routes.ts`, `layout/app-layout/app-layout.component.html`, `public/i18n/{fr,en}.json`.
- No backend migration, no changes to `EditionArchivingService`, `ItemCatalogService`/`ItemCatalogController`/`ItemCatalogComponent`, or `EditionCategoryController`/`EditionService`.
- `architecture.md`'s package paths (`org.pluribourse.edition.*`, `org.pluribourse.item.*`) are stale — real code is under `org.pluribourse.domain.*` throughout (confirmed directly against the code, consistent with every prior story's finding).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 6.2] (lines ~1876-1907) — AC source, FR-088/FR-102.
- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-07-29.md] — origin of FR-102, scope rationale (no price/barcode/table/seller after archiving; comparative-stats future scope explicitly excluded).
- [Source: _bmad-output/implementation-artifacts/2-7-cloture-de-ledition-archivage-de-ledition.md] — `archived_items`/`ArchivedItem`/`ArchivedItemRepository` already built for this story; FK/deletion ordering context (not touched here, read-only consumer).
- [Source: pluribourse-backend/.../domain/archive/entity/ArchivedItem.java], [.../repository/ArchivedItemRepository.java], [.../service/EditionArchivingService.java] — existing data model and the only writer to it.
- [Source: pluribourse-backend/.../domain/item/controller/ItemCatalogController.java], [.../service/ItemCatalogService.java], [.../dto/ItemCatalog{Dto,FilterDto,PageDto}.java] — pattern to replicate (filtering, JPageFlow paging, sort whitelist, `clampPage`).
- [Source: pluribourse-backend/.../domain/edition/controller/EditionCategoryController.java] — reused as-is for the category filter's option list (no phase restriction, admin-only, scoped by `editionId` path variable).
- [Source: pluribourse-backend/.../domain/edition/service/EditionService.java#requireEdition] — reused for the 404 guard.
- [Source: pluribourse-frontend/.../features/catalog/item-catalog.component.{ts,html}] — frontend pattern to replicate (signals, debounced filters, `requestSequence` guard, skeleton/empty states).
- [Source: pluribourse-frontend/.../features/admin/editions/edition-categories/edition-categories.component.{ts,html}] — `mat-select` edition-picker pattern (lines 19-32/81-88) reused for the archived-edition selector.
- [Source: pluribourse-frontend/.../features/report/report-page.component.ts] — sibling admin-only feature for `admin.*` i18n namespace convention.
- [Source: pluribourse-frontend/.../services/edition.service.ts#getAll], [.../models/edition.model.ts] — `EditionDto.archived: boolean` (non-optional), reused for client-side filtering.
- [Source: pluribourse-frontend/.../app.routes.ts], [.../features/admin/admin.routes.ts] — `adminGuard` already covers every child route; new route just needs registering.
- [Source: pluribourse-frontend/.../layout/app-layout/app-layout.component.html] — sidebar structure; confirms **no** existing nav entry for `/admin/catalog` either (Story 6.1 left it out of scope, `6-1-...md:411`) — this story only adds its own entry, not a retroactive one for 6.1.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw.cmd -Dtest=ArchivedCatalogIT test` → 15/15 passed on first run (setup pattern copied verbatim from `EditionArchivingIT`, no iteration needed).
- `./mvnw.cmd test` (full backend suite) → 505/505 passed, BUILD SUCCESS, no regression.
- `npm test` (full frontend suite) → 62 files, 635/635 passed (623 existing + 12 new in `archived-catalog.component.spec.ts`), no regression. One TypeScript compile error surfaced on the first run — mock `EditionDto.documentLanguage` used the string literal `'FR'` instead of the `Language.FR` enum member — fixed in the spec file.

### Completion Notes List

- Backend package `domain.archive.{dto,mapper,controller}` created exactly as scoped: `ArchivedItemDto`/`ArchivedItemFilterDto`/`ArchivedItemPageDto`, `ArchivedItemMapper`, `ArchivedItemService` (in the existing `domain/archive/service/`), `ArchivedItemController`. No changes to `ItemCatalogService`/`ItemCatalogController`/`EditionArchivingService`/`EditionCategoryController`, as scoped by Dev Notes.
- `ArchivedCatalogIT` builds one main archived edition (2 categories, 2 sellers, one lot archived member-by-member — 4 archived items mixing sold/unsold) plus a second archived edition (cross-edition regression guard) and a fresh never-archived edition (empty-page-not-404 guard). All 15 scenarios from T5 covered, including the added `sold` sort-order assertion (`false` before `true` ascending).
- Frontend: `ArchivedCatalogComponent` follows `ItemCatalogComponent`'s pattern verbatim (debounced name filter, `requestSequence` out-of-order guard, `TriState` sold filter, skeleton/empty-state/notification-inline). On edition change, filters/sort/pagination and the category dropdown are reset before reloading — not explicitly required by the ACs but necessary so a previously selected `categoryName` value that doesn't exist in the newly selected edition can't silently produce an empty result.
- Category filter dropdown populated via `categoryService.getCategories(selectedEditionId)` as scoped — option values are category **names** (string), matching `ArchivedItemDto.categoryName`'s exact-match semantics, not category IDs.
- Sidebar entry added only for `/admin/archived-catalog` (icon `inventory_2`, reused from the active catalog's empty-state icon) — no retroactive entry added for `/admin/catalog`, which Story 6.1 left explicitly out of scope.

### File List

**Backend — new files**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/dto/ArchivedItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/dto/ArchivedItemFilterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/dto/ArchivedItemPageDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/mapper/ArchivedItemMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/ArchivedItemService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/controller/ArchivedItemController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java`

**Frontend — new files**
- `pluribourse-frontend/src/app/models/archived-item.model.ts`
- `pluribourse-frontend/src/app/services/archived-item.service.ts`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.scss`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.spec.ts`

**Frontend — modified files**
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-08-20 : Implémentation complète de la Story 6.2 (consultation admin-only du catalogue archivé d'une édition passée, FR-088/FR-102). Backend : nouveau package `domain.archive.{dto,mapper,controller}` réutilisant `ArchivedItemRepository.findAllByEditionId` (déjà créé par la Story 2.7), `GET /admin/archive/editions/{editionId}/items` avec filtrage en mémoire (nom/catégorie/vendu) + tri/pagination via `FilterService.filterData` (JPageFlow), whitelist de tri + `InvalidSortFieldException` réutilisée, admin-only via `@PreAuthorize` classe. Frontend : `ArchivedCatalogComponent` (sélecteur d'édition archivée, aucun article affiché avant sélection, filtres avec debounce, tri `MatSortModule`, pagination 50/page), nouvelle entrée sidebar `/admin/archived-catalog`. 505/505 tests backend (15 nouveaux dans `ArchivedCatalogIT`), 635/635 tests frontend (12 nouveaux), aucune régression. Statut → `review`.
- 2026-08-21 : Revue de code terminée (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor). 9 patchs appliqués : ordre `requireEdition`/`validateSort` corrigé dans `ArchivedItemService` (404 avant 400) ; filtre `categoryName=""` traité comme "aucun filtre" (cohérent avec `name`) ; sélecteur d'édition affiche désormais les dates (AC1) ; gestion d'erreur ajoutée sur le chargement des éditions archivées et des catégories (signal `categoryError` distinct, état de chargement `isLoadingEditions`) ; garde `requestSequence` ajoutée à `loadCategories` ; `error()`/`categoryError()` réinitialisés au changement d'édition ; clés i18n dédiées `columns.soldValues.sold/unsold` pour la cellule du tableau (découplées des libellés de filtre) ; 4 tests ajoutés à `ArchivedCatalogIT` (filtres combinés, clamp de page hors limites, borne `size=200/201`, `categoryName` vide) ; changelog `sprint-status.yaml` réorganisé. 4 items différés (voir `deferred-work.md`) : direction de tri non validée, pas d'annulation HTTP (`switchMap`), chargement intégral en mémoire de l'archive, `toLowerCase()` sans `Locale` — tous hérités tels quels de `ItemCatalogService`/`ItemCatalogComponent` selon les Dev Notes de la story. 509/509 tests backend, 635/635 tests frontend, build de production frontend propre, aucune régression. Statut → `done`.
