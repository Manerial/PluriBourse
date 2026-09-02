---
baseline_commit: dc2487cba50cc20a93d202f1654a580b865cee2e
---

# Story 6.3: Prix et marqueur « (lot) » dans les catalogues

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrator or volunteer,
I want lot member items to show the lot's price with a "(lot)" marker in both the active-edition catalog and the archived-edition catalog,
so that I can see a meaningful price for lot members (blank today in the active catalog) and tell at a glance that a row belongs to a lot rather than being a standalone item.

## Acceptance Criteria

1. **Given** an item that belongs to a lot, **When** the active catalog list renders, **Then** its price cell shows the lot's global price followed by a `(lot)` marker (e.g. `10 € (lot)`) **And** a standalone item shows its own price, unchanged.
2. **Given** `GET /api/catalog`, **When** the page is returned, **Then** every entry with a non-null `lotId` also carries `lotPrice` equal to the lot's `globalPrice` **And** standalone entries carry `lotPrice = null` **And** `price` stays `null` for lot members (unchanged).
3. **Given** an archived item that belonged to a lot, **When** the archived catalog list renders, **Then** its price cell shows `price` followed by a `(lot)` marker **And** a standalone archived item is unchanged.
4. **Given** an edition is archived, **When** the archived rows are written, **Then** each lot member row stores the originating lot's id in `lot_ref` and the lot's name in `lot_name` **And** standalone item rows store `null` for both.
5. **Given** two different lots in the same edition that share the same name, **When** the edition is archived, **Then** their archived members carry **different** `lot_ref` values despite the identical `lot_name`.
6. **Given** `GET /api/admin/archive/editions/{id}/items`, **When** the page is returned, **Then** each entry carries `lotRef` and `lotName` (`null` for standalone items).
7. **Given** a volunteer (non-admin) calls the archived endpoint, **When** the request is sent, **Then** access is still denied (403) — unchanged.
8. **Given** an edition archived before this migration, **When** its archived catalog is consulted, **Then** its existing rows keep `lot_ref = null` / `lot_name = null` and their members render without the marker — accepted as-is (dev-only data, will be reset).

## Tasks / Subtasks

> Parts A and B ship in **one commit**. Part A is a small additive change with no migration; Part B carries migration `034`. They are one story because they are the same user-visible behaviour applied to the two catalog screens.

### Part A — Active-edition catalog

- [x] **T1 — Backend: `ItemCatalogDto` + `ItemMapper`** (AC: 1, 2)
  - [x] Add `BigDecimal lotPrice` to `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogDto.java`, after `lotName` (record component).
  - [x] In `pluribourse-backend/src/main/java/org/pluribourse/domain/item/mapper/ItemMapper.java`, `toCatalogDto`: add `@Mapping(target = "lotPrice", source = "lot.globalPrice")` — the **identical** one-liner already present on `toDto` (`ItemMapper.java:17`). Standalone items have `lot == null` → MapStruct null-safe source navigation yields `null`, no NPE.
  - [x] No query change: `ItemRepository.findAllByEditionIdForCatalog` (`ItemRepository.java:76`) already does `LEFT JOIN FETCH i.lot` — `item.getLot().getGlobalPrice()` is safe and there is no new N+1.
  - [x] **Do not** touch `ALLOWED_SORT_FIELDS` in `ItemCatalogService` (`ItemCatalogService.java:30-32`): `price` keeps sorting on `Item.price` (`null` for lot members). Sorting on the effective lot price is a post-mapping concern incompatible with `FilterService.filterData` and is out of scope (see Dev Notes § Not in scope).

- [x] **T2 — Frontend: model + template + i18n** (AC: 1)
  - [x] `pluribourse-frontend/src/app/models/item.model.ts` — `ItemCatalogDto`: add `lotPrice: number | null`.
  - [x] `pluribourse-frontend/src/app/features/catalog/item-catalog.component.html` — price cell (line 94): when `item.lotId !== null`, render with a new key `catalog.columns.priceLotFormat` bound to `item.lotPrice`; otherwise keep the existing `catalog.columns.priceFormat` bound to `item.price`. Keep this file's raw-value formatting (no `.toFixed`), matching the current cell.
  - [x] i18n — add `catalog.columns.priceLotFormat` **next to** the existing `catalog.columns.priceFormat` in **both** `pluribourse-frontend/public/i18n/fr.json` and `pluribourse-frontend/public/i18n/en.json`: value `"{{ price }} {{ currency }} (lot)"`.

- [x] **T3 — Backend test: `ItemCatalogIT`** (AC: 1, 2)
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java` creates **no lot** today. Add one during setup, in phase DEPOSIT, inside `@Order(3)` `register_items_across_sellers_and_categories` (or a new `@Order` method numbered **between 3 and 17** — see below): `POST /api/lots` (`volunteerSession`) with a `CreateLotDto(sellerProfileId, categoryId, name, globalPrice, List.of(CreateLotItemDto(name, incomplete, comment), ...))` — payload shape per `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java` (and mirrored in `ArchivedCatalogIT` lines 97-103).
  - [x] **This IT cannot use a "separate edition" escape hatch.** `/api/catalog` is scoped to the single active edition, and `@Order(18)` `catalog_unavailable_once_edition_is_closed` closes it — every `/api/catalog` call must sit in the `@Order` 4–17 window, against the one storyboard edition. So the lot fixture **must** go into that shared edition and **every downstream assertion updated in the same pass**:
    - `@Order(4)` / `@Order(5)` — `totalElements` / `content.length()` == 3 → new total (3 + lot member count).
    - `@Order(11)` `sort_by_name_toggles...` — lot-member names enter the ordering; fix the `content[i].name` expectations.
    - `@Order(12)` `sort_by_price_descending` — asserts `content[0..2].price` == 50/8/5, and a lot member's `price` is `null`. **First verify** `sort=price,desc` does not throw on a `null` `BigDecimal`: `FilterService.compare()` falls back to `Comparable.compareTo()` (see that test's own comment), and `null.compareTo(...)` would NPE. If null-price rows don't sort cleanly, keep lot members out of this assertion (filter the fixture set) rather than asserting a fragile order.
    - `@Order(16)` `page_beyond_last_page_is_clamped...` — asserts `totalElements` == 3 and `content[0].name` == "Console" → update to the new total / last-page row.
    - `@Order(6)` (filter by category) and `@Order(7)` (filter `tableNumber=1`) only if the lot's category / table collides with "Jouets" / table 1 — put the lot in another category (e.g. "Jeux") to leave 6 and 7 untouched.
  - [x] New `@Order` assertion method (numbered 4–17) on `GET /api/catalog?sort=name,asc`: a lot-member entry has `lotId` non-null, `lotName` set, `lotPrice` == the lot `globalPrice`, `price` == `null`; a standalone entry has `lotPrice` == `null` and `price` set. Assert on named rows — `jsonPath(...).exists()` is `false` for a JSON `null`, use `.value(...)` / `.doesNotExist()` deliberately.

- [x] **T4 — Frontend test: `item-catalog.component.spec.ts`** (AC: 1)
  - [x] `pluribourse-frontend/src/app/features/catalog/item-catalog.component.spec.ts` — `MOCK_ITEMS` (lines 14-17) must gain `lotPrice: null` on every existing entry (TypeScript compile).
  - [x] For the lot-member row, **do not grow `MOCK_ITEMS`** — `'loads categories and the first page of items'` asserts `component.items().length` / `component.totalElements()` == `2` and `MOCK_PAGE` (line 19) hardcodes `totalElements: 2`. Give the new test its own page: `itemServiceMock.getCatalog.mockReturnValueOnce(of({ page: { content: [...three rows...], totalElements: 3, ... } }))`, the third row a lot member (`lotId`, `lotName` set, `lotPrice` set, `price: null`). (If you extend `MOCK_ITEMS` instead, bump every `=== 2` assertion and `MOCK_PAGE.totalElements` to `3` in the same pass.)
  - [x] Add an `it(...)` that renders the table and asserts the lot-member row's price cell contains the lot price **and** the `(lot)` marker text, and a standalone row shows its plain price. (There is no price-cell rendering test today — this is new.)

### Part B — Archived-edition catalog

- [x] **T5 — Backend: Liquibase migration `034-archived-item-lot.xml`** (AC: 4, 8)
  - [x] New file `pluribourse-backend/src/main/resources/db/changelog/034-archived-item-lot.xml`. `addColumn` on `archived_items`: `lot_ref` `BIGINT` **nullable** (no FK), `lot_name` `VARCHAR(200)` **nullable**. Shape follows `030-archived-items-price.xml` **minus** the `NOT NULL` + `defaultValue` (these columns are legitimately null for standalone items and for pre-existing rows).
  - [x] Register it in `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` on a new `<include>` line after `033-lot-category.xml` (currently the last).
  - [x] **No `<update>` / backfill changeset** — existing rows stay `null` by design (AC 8).

- [x] **T6 — Backend: `ArchivedItem` entity** (AC: 4)
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/entity/ArchivedItem.java` — add `@Column(name = "lot_ref") private Long lotRef;` and `@Column(name = "lot_name", length = 200) private String lotName;` (both nullable, no `nullable = false`).

- [x] **T7 — Backend: `EditionArchivingService`** (AC: 4, 5)
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/EditionArchivingService.java`, in the `items.stream().map(...)` block (lines 68-78): resolve `Lot lot = item.getLot();` once, then `archivedItem.setLotRef(lot != null ? lot.getId() : null)` and `archivedItem.setLotName(lot != null ? lot.getName() : null)`.
  - [x] Reuse the same local `lot` for the existing price line (`archivedItem.setPrice(lot != null ? lot.getGlobalPrice() : item.getPrice())`) — behaviour unchanged, just de-duplicated.
  - [x] `item.getLot()` is safe here: the source list comes from `itemRepository.findAllByEditionIdForSettlementReport` (`ItemRepository.java:161`) which does `LEFT JOIN FETCH i.lot`, inside the method's `@Transactional` (no `LazyInitializationException`).

- [x] **T8 — Backend: `ArchivedItemDto` + `ArchivedItemMapper`** (AC: 6)
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/dto/ArchivedItemDto.java` — add `Long lotRef` and `String lotName` record components.
  - [x] `ArchivedItemMapper.toDto` — no `@Mapping` needed, field names match 1:1.
  - [x] **No change to `ArchivedItemService`**: `ALLOWED_SORT_FIELDS` unchanged (no lot sort); the `name` filter keeps matching `name` only, not `lotName` — consistent with the active catalog.

- [x] **T9 — Frontend: model + template + i18n** (AC: 3)
  - [x] `pluribourse-frontend/src/app/models/archived-item.model.ts` — `ArchivedItemDto`: add `lotRef: number | null`, `lotName: string | null`.
  - [x] `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html` — price cell (line 84): when `item.lotRef !== null`, use a new key `admin.archivedCatalog.priceLotFormat` bound to `item.price.toFixed(2)`; else keep `admin.archivedCatalog.priceFormat`. Keep this file's `.toFixed(2)` style.
  - [x] i18n — add `admin.archivedCatalog.priceLotFormat` next to the existing `admin.archivedCatalog.priceFormat` in **both** `fr.json` and `en.json`: value `"{{ price }} {{ currency }} (lot)"`.

- [x] **T10 — Backend test: `ArchivedCatalogIT`** (AC: 4, 5, 6)
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java` already archives one lot (`"Lot Duo"`, `8.00 €`, members `Duo A` / `Duo B` — lines 97-103) plus standalone `Kapla` / `Robot` (`10.00 €`). Extend:
    - assert `Duo A` / `Duo B` archived rows expose `lotName == "Lot Duo"` and a **non-null `lotRef`, equal for both**;
    - assert `Kapla` / `Robot` rows have `lotRef == null` and `lotName == null` (assert on named rows via `sort=name,asc`).
  - [x] Prove AC 5 with a **second** lot also named `"Lot Duo"` (different seller and/or price). ~10 assertions across this storyboard hard-code the 4-row archived set (`@Order(3)/(4)/(5)/(6)/(7)/(9)/(11)/(13)/(17)/(20)` …), so **prefer** a dedicated new `@Order` method that builds **its own edition** (like the existing `secondEditionId` / `freshEditionId` methods), archives it within the method, and asserts the two homonym lots' members carry **different** `lotRef` despite identical `lotName` — leaving the shared storyboard counts untouched. Only fold the 2nd lot into the main edition if you then update every count/row assertion above in the same pass.
  - [x] Extend the DTO-shape test (`@Order(3)`, `admin_lists_all_archived_items_with_no_filter_and_dto_shape_is_limited`, lines 120-135): it currently sends **no `sort`** and asserts on `content[0]`, which is not deterministically a lot member — add `.param("sort", "name,asc")` to the request so `content[0]` is `Duo A`, then assert `lotRef` (`.value(...)`, non-null) and `lotName` (`.value("Lot Duo")`) are present, and keep the existing `lotId`/`tableNumber`/etc. `.doesNotExist()` assertions. (Adding the sort does not perturb `@Order(3)`'s count/shape assertions.)
  - [x] Refresh the now-stale class Javadoc on `EditionArchivingIT` (~line 50: "`archived_items` (lot members archived individually, no lot reference retained)") — this story makes the archive retain `lot_ref` / `lot_name`. `ArchivedCatalogIT`'s own class Javadoc (line 42) is fine but add a word on the lot-ref columns if you touch it.

- [x] **T11 — Frontend test: `archived-catalog.component.spec.ts`** (AC: 3)
  - [x] `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.spec.ts` — `MOCK_ITEMS` (lines 22-25) must gain `lotRef: null, lotName: null` on every existing entry (TypeScript compile).
  - [x] For the lot-member row, **do not grow `MOCK_ITEMS`** — `'selecting an edition loads categories and the first page of items'` asserts `component.items().length` / `component.totalElements()` == `2` (lines 92-93) and `MOCK_PAGE` (line 27) hardcodes `totalElements: 2`. Give the new test its own page: `archivedItemServiceMock.getArchivedCatalog.mockReturnValueOnce(of({ page: { content: [...], totalElements: 3, ... } }))`, one row a lot member (`lotRef` set, `lotName` set). (If you extend `MOCK_ITEMS` instead, bump the `=== 2` assertions and `MOCK_PAGE.totalElements` to `3`.)
  - [x] Add an `it(...)` asserting the `(lot)` marker appears only on the lot row's price cell.

- [x] **T12 — `sprint-status.yaml`**
  - [x] `bmad-create-story` sets `6-3-prix-du-lot-dans-les-catalogues: ready-for-dev`. No further manual edit needed; `dev-story` / `code-review` advance it afterwards.

### Review Findings

<!-- Added by bmad-code-review 2026-09-02. 3 layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor. 0 AC violations. -->

- [x] [Review][Defer] Le tri par « Prix » du catalogue actif contredit visuellement le prix affiché — Après cette story, une ligne membre de lot affiche `20 € (lot)` mais le tri `mat-sort-header="price"` reste sur `Item.price` (`null` pour les membres, `ALLOWED_SORT_FIELDS` inchangé). Résultat : les membres de lot se regroupent en tête (`asc`) ou en queue (`desc`) indépendamment du 20 € affiché. Le tri sur le prix effectif du lot est explicitement listé « hors périmètre » dans la story. Sources : Edge Case Hunter. **Différé** (décision utilisateur, 2026-09-02) : se règle proprement en même temps que la story de regroupement repliable des membres de lot — voir `deferred-work.md` § « Deferred from: code review of story-6.3 ».
- [x] [Review][Patch] `ItemCatalogIT.@Order(12)` couple les assertions au tri interne des `null` de JPageFlow — `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java`. **Corrigé** (2026-09-02) : la méthode teste désormais **les deux directions**. Révélation au passage : le signe `null` de `FilterService.compare()` **est** inversé par la direction — les lignes à `price` null se retrouvent **en dernier sous `desc`** mais **en premier sous `asc`** (`content[0..1]` = null, `content[2..4]` = 5/8/50). Méthode renommée `sort_by_price_orders_non_null_prices_and_pushes_null_priced_lot_members_to_the_direction_edge`. Vérifié : `ItemCatalogIT` 20/20 vert. Sources : Blind Hunter + Edge Case Hunter + Acceptance Auditor.
- [x] [Review][Patch] Assertions de cellule de prix trop lâches dans les specs front — **Corrigé** (2026-09-02). `item-catalog.component.spec.ts` : `CurrentEditionService.currentEdition` fixé (`currency: 'EUR'`), assertions `toBe('5 EUR')` / `toBe('20 EUR (lot)')` sur le texte normalisé de la cellule. `archived-catalog.component.spec.ts` : assertions `toBe('5.00 $')` / `toBe('8.00 $ (lot)')` (devise `$` déjà fournie par l'édition sélectionnée). Vérifié : 702/702 tests front verts. Sources : Blind Hunter + Acceptance Auditor.

## Dev Notes

### One story, one commit

Part A (active catalog) and Part B (archived catalog) are the same behaviour ("show the lot's price + a `(lot)` marker") applied to two screens. Part A: 2 backend files + 3 frontend files, no migration. Part B: migration `034` + entity/DTO/service-archiving + 2 frontend files. Ship together so the "lot price in catalogs" change is atomic.

### Files to be modified — current state and what changes

- **`ItemCatalogDto.java`** (record, 12 components incl. `lotId`, `lotName`) — UPDATE: append `BigDecimal lotPrice`. No behaviour, pure data carrier.
- **`ItemMapper.java`** — UPDATE: `toCatalogDto` gains one `@Mapping`. `toDto` already maps `lot.globalPrice → lotPrice` (line 17) — copy it. Do not touch `toDto`/`toEntity`/`updateEntityFromDto`.
- **`ItemCatalogService.java`** — READ ONLY for this story. Filtering/paging/sort untouched. The catalog query it calls already fetches `lot`.
- **`item-catalog.component.html`** — UPDATE: only the `<td>` for price (line 94). Everything else (filters, sort headers, paginator, skeleton/empty/error states) unchanged. `currency()` signal already available.
- **`item.model.ts`** — UPDATE: `ItemCatalogDto` interface gains `lotPrice`. `ItemDto` (separate interface) already has `lotPrice` — do not confuse the two.
- **`ArchivedItem.java`** (entity, `@Table("archived_items")`, fields `name`, `categoryName`, `sold`, `price`) — UPDATE: +2 nullable columns.
- **`ArchivedItemDto.java`** (record: `id`, `name`, `categoryName`, `sold`, `price`) — UPDATE: +`lotRef`, +`lotName`.
- **`ArchivedItemMapper.java`** — no code change (1:1), but re-generate/verify MapStruct impl picks up the new fields.
- **`EditionArchivingService.java`** — UPDATE: the `map` lambda (lines 68-78) sets 2 more fields. `archiveEdition` is `@Transactional`; the item list is lot-fetched. Do not change deletion ordering (items → settlements → sellers) or the snapshot logic.
- **`archived-catalog.component.html`** — UPDATE: only the price `<td>` (line 84).
- **`archived-item.model.ts`** — UPDATE: `ArchivedItemDto` interface +2 fields.
- **`db.changelog-master.xml`** — UPDATE: one `<include>` line appended.

### Why `lot_ref` (opaque id), not `is_lot` (boolean)

`archived_items` stores **no seller and no lot identity** — only `name`, `categoryName`, `sold`, `price`, `edition`. Two lots with the same name (even from two sellers) are indistinguishable with only a name or a boolean. Storing the originating `Lot.id` as a plain nullable `BIGINT` gives a stable grouping discriminator (AC 5) and makes a future "collapse archived lot members into one expandable row" story cheap. **No FK constraint**: archiving deletes `items` but leaves `lots` rows (orphaned); a later cleanup of those orphans must not cascade into an archive. `lot_name` is stored for display and that future grouping; the `(lot)` marker keys off `lot_ref != null`, the name is not needed to decide whether to show it.

### `price` on archived lot members is already correct

`EditionArchivingService.java:76` already writes `lot.getGlobalPrice()` (not the member's `null` price) into `archived_items.price` — same `ItemPricing` convention as `SettlementReportRenderer` / `DepositSlipRenderer`. The archived **amount** is right today; this story adds the *"this is a lot"* signal so N members at 10 € stop reading as N unrelated items. (`archived_items.price` was introduced by migration `030-archived-items-price.xml` in a post-6.2 code review, never reflected in the PRD until FR-088/FR-102 were amended by `sprint-change-proposal-2026-09-02.md`.)

### Not in scope (do not implement)

- Grouping / collapsing lot members into a single expandable row in either catalog (the original request — deferred by decision; see `sprint-change-proposal-2026-09-02.md`). Both catalogs keep one row per (member) item.
- Sorting the active catalog by the effective lot price — stays on `Item.price` (`null` for lot members).
- Matching the `name` filter against lot names — filter keeps matching item `name` only, both catalogs.
- Backfilling `lot_ref` / `lot_name` on editions archived before migration `034` (impossible anyway — source `items.lot_id` rows are deleted at archive time; accepted per AC 8, dev DB will be reset).

### Testing standards (CLAUDE.md)

E2E through controllers only, one class per business scenario, `@TestMethodOrder(OrderAnnotation.class)`, data persists across `@Order` methods (no class-level `@Transactional`), extends `org.pluribourse.shared.IntegrationTest`. **Extend** the existing `ItemCatalogIT` and `ArchivedCatalogIT` storyboards — do not add new IT classes. `EditionArchivingIT` is a regression guard (additive nullable columns — expected to still pass untouched; run it). Frontend: Vitest via `npm test` in `pluribourse-frontend/` (not `npx vitest run`). Coverage target 80 % both sides.

### Project Structure Notes

- No new components, services, controllers, endpoints, or routes. `ArchivedItemService` / `ItemCatalogService` logic untouched.
- New file: `034-archived-item-lot.xml` (+ its master registration). Migration numbering: `033-lot-category.xml` is currently last.
- i18n: new keys are siblings of existing `*.priceFormat` keys under `catalog.columns` and `admin.archivedCatalog` — both `fr.json` and `en.json` (no hard-coded strings in templates — CLAUDE.md).
- `architecture.md` package paths (`org.pluribourse.item.*`, `org.pluribourse.edition.*`) are stale — real code is `org.pluribourse.domain.*` (consistent with every prior story).
- Financial values: `BigDecimal` end to end (`lotPrice` is `BigDecimal` backend, `number` in the TS model like every other price field) — never `float`/`double` (CLAUDE.md).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 6.3] — user story + AC (Given/When/Then FR).
- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-02.md] — origin, scoping (marker now, grouping deferred), `lot_ref` rationale, FR-088/FR-102 amendments, no backfill.
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] — FR-088 (archive conserve prix effectif + référence de lot), FR-102 (catalogue archivé affiche prix + marqueur « (lot) »), FR-041/048 (convention lot : une ligne, prix du lot, pas de prix individuel).
- [Source: pluribourse-backend/.../domain/item/dto/ItemCatalogDto.java], [.../domain/item/mapper/ItemMapper.java:12-18] — `toCatalogDto` + the `lot.globalPrice → lotPrice` mapping on `toDto` to mirror.
- [Source: pluribourse-backend/.../domain/item/service/ItemCatalogService.java:30-32] — `ALLOWED_SORT_FIELDS`; price sort stays on `Item.price`.
- [Source: pluribourse-backend/.../domain/item/repository/ItemRepository.java:76,161] — `findAllByEditionIdForCatalog` and `findAllByEditionIdForSettlementReport` both `LEFT JOIN FETCH i.lot` (no lazy-init risk in mapper / archiving).
- [Source: pluribourse-backend/.../domain/archive/entity/ArchivedItem.java], [.../domain/archive/dto/ArchivedItemDto.java], [.../domain/archive/mapper/ArchivedItemMapper.java] — archived row model; 1:1 mapper.
- [Source: pluribourse-backend/.../domain/archive/service/EditionArchivingService.java:47-100] — `archiveEdition` (`@Transactional`), the `map` lambda (68-78) to extend, deletion ordering to preserve.
- [Source: pluribourse-backend/src/main/resources/db/changelog/030-archived-items-price.xml] + `db.changelog-master.xml` — `addColumn` on `archived_items` pattern + registration point (after `033-lot-category.xml`).
- [Source: pluribourse-backend/.../domain/archive/ArchivedCatalogIT.java:97-155] — `"Lot Duo"` archived-lot storyboard to extend (T10).
- [Source: pluribourse-backend/.../domain/item/ItemCatalogIT.java:113-212] — storyboard to extend with a lot fixture (T3); no lot created there today.
- [Source: pluribourse-backend/.../domain/item/LotManagementIT.java] — `POST /api/lots` / `CreateLotDto` / `CreateLotItemDto` payload shape.
- [Source: pluribourse-frontend/.../features/catalog/item-catalog.component.html:94], [.../models/item.model.ts:32-45] — active catalog price cell + `ItemCatalogDto` model (distinct from `ItemDto`, which already has `lotPrice`).
- [Source: pluribourse-frontend/.../features/catalog/item-catalog.component.spec.ts:14-16] — `MOCK_ITEMS` to update (add `lotPrice`) + add lot-member row.
- [Source: pluribourse-frontend/.../features/admin/archived-catalog/archived-catalog.component.html:79-85], [.../models/archived-item.model.ts] — archived catalog price cell + model.
- [Source: pluribourse-frontend/.../features/admin/archived-catalog/archived-catalog.component.spec.ts:22-24] — `MOCK_ITEMS` to update (add `lotRef`/`lotName`).
- [Source: pluribourse-frontend/public/i18n/fr.json + en.json] — existing `catalog.columns.priceFormat` and `admin.archivedCatalog.priceFormat`; add `priceLotFormat` beside each.
- [Source: _bmad-output/implementation-artifacts/6-2-consultation-catalogue-edition-archivee.md] — sibling story; archived-catalog conventions, `archived_items` model history.
- [Source: CLAUDE.md] — types explicites / pas de `var`, accolades obligatoires, pas de template inline, i18n obligatoire, `BigDecimal` pour les montants, tests E2E par les contrôleurs.

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (dev-story workflow)

### Debug Log References

- `mvnw -o test` (full backend suite) — 559 tests, 0 failures.
- `mvnw -o test -Dtest=ItemCatalogIT,ArchivedCatalogIT,EditionArchivingIT` — 20 / 22 / 12, 0 failures.
- `npm test` (frontend) — 702 tests, 0 failures (700 pré-existants + 2 nouveaux).
- `npm run build` — OK. `mvnw -o test-compile` — OK (MapStruct régénère `ItemMapperImpl.toCatalogDto` / `ArchivedItemMapperImpl.toDto` avec les nouveaux champs).

### Completion Notes List

**Partie A — catalogue actif**
- `ItemCatalogDto` : +`BigDecimal lotPrice` (dernier composant). `ItemMapper.toCatalogDto` : +`@Mapping(target = "lotPrice", source = "lot.globalPrice")` — navigation null-safe MapStruct (`itemLotGlobalPrice` retourne `null` si `lot == null`). Aucune requête, aucun tri touchés (`ALLOWED_SORT_FIELDS` inchangé — le tri `price` reste sur `Item.price`).
- `item.model.ts` `ItemCatalogDto` : +`lotPrice`. Template : cellule prix branchée sur `item.lotId !== null` → `catalog.columns.priceLotFormat` (lié à `item.lotPrice`), sinon `priceFormat` inchangé. Nouvelle clé i18n `catalog.columns.priceLotFormat` = `"{{ price }} {{ currency }} (lot)"` en fr + en.
- `ItemCatalogIT` : fixture lot « Lot BD » (2.00 membres `Duo BD 1/2`, `globalPrice` 20.00) ajoutée dans `@Order(3)` (catégorie « Jeux » / vendeur Bruno pour ne pas perturber les filtres catégorie/table/vendeur). Assertions mises à jour : `@Order(4/5)` total 3→5 ; `@Order(11)` ordre complet des 5 lignes ; `@Order(16)` total/last-page ; `@Order(12)` **inchangée sur `content[0..2]`** + 2 assertions ajoutées — vérifié par désassemblage de `FilterService.compare()` (JPageFlow 1.7.0) que les `price` null sont traités **avant** la branche `Comparable` (pas de NPE) et triés **en dernier** sous `desc`. Nouvelle méthode `@Order(17)` (AC 1/2), queue renumérotée 17→18/18→19/19→20.
- `item-catalog.component.spec.ts` : `lotPrice: null` sur `MOCK_ITEMS` ; nouveau test avec page dédiée (`mockReturnValueOnce`) + `TranslateService.setTranslation` pour asserter le rendu `(lot)` sur la ligne lot uniquement.

**Partie B — catalogue archivé**
- Migration `034-archived-item-lot.xml` : `addColumn` `lot_ref` BIGINT **nullable sans FK** + `lot_name` VARCHAR(200) nullable sur `archived_items` (shape de `030` moins `NOT NULL`/`defaultValue`). Enregistrée après `033` dans le master. Pas de backfill (AC 8). `<rollback>` fourni.
- `ArchivedItem` : +`lotRef` / `lotName` (nullable). `ArchivedItemDto` : +`Long lotRef` / `String lotName` (mapper 1:1, aucun `@Mapping`). `EditionArchivingService` : `Lot lot = item.getLot()` résolu une fois, `setLotRef`/`setLotName`, ligne `setPrice` dédupliquée sur la même variable (comportement identique).
- `archived-item.model.ts` : +`lotRef` / `lotName`. Template : cellule prix branchée sur `item.lotRef !== null` → `admin.archivedCatalog.priceLotFormat` (style `.toFixed(2)` conservé). Nouvelle clé i18n en fr + en.
- `ArchivedCatalogIT` : `@Order(3)` trié (`sort=name,asc`) + assertions `lotRef`/`lotName` sur `content[0]` (« Duo A ») ; nouvelle `@Order(21)` (AC 4 — `Duo A`/`Duo B` même `lot_ref` non-null, `Kapla`/`Robot` null) ; nouvelle `@Order(22)` (AC 5 — édition dédiée, deux lots homonymes « Lot Homonyme » → `lot_ref` distincts). Javadoc obsolète de `EditionArchivingIT` (« no lot reference retained ») rafraîchie.
- `archived-catalog.component.spec.ts` : `lotRef`/`lotName: null` sur `MOCK_ITEMS` ; nouveau test page dédiée + `setTranslation` pour asserter le marqueur `(lot)` sur la seule ligne lot.

**Écarts / décisions**
- Aucun écart par rapport au code prescrit par la story. `@Order` cible « entre 3 et 17 » impossible (créneaux tous pris) → nouvelle méthode `@Order(17)` + renumérotation de la queue (3 annotations). Aucune construction positionnelle de `ItemCatalogDto` / `ArchivedItemDto` nulle part (uniquement MapStruct) → pas de balayage compilateur nécessaire.
- Aucune nouvelle dépendance. Aucun nouvel endpoint / composant / route. Base de dev locale non touchée.

### File List

**Backend — main**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/ItemCatalogDto.java` (M)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/mapper/ItemMapper.java` (M)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/entity/ArchivedItem.java` (M)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/dto/ArchivedItemDto.java` (M)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/EditionArchivingService.java` (M)
- `pluribourse-backend/src/main/resources/db/changelog/034-archived-item-lot.xml` (A)
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (M)

**Backend — test**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java` (M)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java` (M)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java` (M — Javadoc)

**Frontend**
- `pluribourse-frontend/src/app/models/item.model.ts` (M)
- `pluribourse-frontend/src/app/models/archived-item.model.ts` (M)
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.html` (M)
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.spec.ts` (M)
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html` (M)
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.spec.ts` (M)
- `pluribourse-frontend/public/i18n/fr.json` (M)
- `pluribourse-frontend/public/i18n/en.json` (M)

## Change Log

- 2026-09-02 : Création de la Story 6.3 via `bmad-create-story`, sur la base du `sprint-change-proposal-2026-09-02.md` (amende FR-088 + FR-102). Périmètre première itération volontairement réduit : prix du lot + marqueur « (lot) » dans le catalogue actif et le catalogue archivé, **sans** regroupement repliable (reporté). Parties A (catalogue actif — `lotPrice` sur `ItemCatalogDto`, additif) et B (catalogue archivé — migration `034` : `lot_ref` BIGINT nullable sans FK + `lot_name` sur `archived_items`, peuplés par `EditionArchivingService`) à livrer dans le même commit. Éditions déjà archivées non rétro-remplies (base de dev). Statut → ready-for-dev.
- 2026-09-02 : Passe de validation (`bmad-create-story validate`) — précisions sur le ripple des storyboards de test. `ItemCatalogIT` : pas d'édition dédiée possible (catalogue borné à l'édition active, fermée en `@Order(18)`) → fixture lot dans l'édition partagée, assertions `@Order(4/5/11/12/16)` à mettre à jour, et vérifier que `sort=price,desc` ne NPE pas sur un `price` null avant d'asserter l'ordre. `ArchivedCatalogIT` : pour AC 5, méthode `@Order` dédiée avec sa propre édition (évite de casser ~10 assertions figées à 4 lignes) ; `@Order(3)` DTO-shape à trier (`sort=name,asc`) avant d'asserter `lotRef`/`lotName` sur `content[0]`. Specs front (`T4`/`T11`) : ne pas grossir `MOCK_ITEMS` (assertions `=== 2` + `MOCK_PAGE.totalElements`), passer par un `mockReturnValueOnce` dédié. Javadoc obsolète de `EditionArchivingIT` (« no lot reference retained ») à rafraîchir.
- 2026-09-02 : Implémentation (`bmad-dev-story`), statut → review. T1-T12 livrées dans un seul lot de changements. Partie A : `lotPrice` (`BigDecimal`) sur `ItemCatalogDto` + `@Mapping` sur `toCatalogDto` ; template catalogue actif branché sur `lotId`, clé i18n `catalog.columns.priceLotFormat` fr/en. Partie B : migration `034` (`lot_ref` BIGINT nullable sans FK + `lot_name` sur `archived_items`), `ArchivedItem`/`ArchivedItemDto` + `EditionArchivingService` peuplant les deux champs (`Lot` résolu une fois, ligne `setPrice` dédupliquée), template catalogue archivé branché sur `lotRef`, clé i18n `admin.archivedCatalog.priceLotFormat` fr/en. `sort=price,desc` sur `price` null vérifié par désassemblage `FilterService.compare()` (JPageFlow 1.7.0) : null géré avant la branche `Comparable`, trié en dernier sous `desc` — `@Order(12)` d'`ItemCatalogIT` reste vert sans changement de `content[0..2]`. Créneaux `@Order` 4-17 tous pris → nouvelle méthode `@Order(17)` + queue renumérotée. AC 5 prouvée par `ArchivedCatalogIT.@Order(22)` (édition dédiée, deux lots homonymes → `lot_ref` distincts). Vérifs : 559 tests backend verts, 702 tests frontend verts (+2), `npm run build` OK, migration `034` appliquée par le contexte de test H2 (`drop-first`).
