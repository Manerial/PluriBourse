---
baseline_commit: f9e20d2746ab1748649524dde1b1fb25d8881c56
---

# Story 3.14: Catégorie du lot

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole enregistrant un lot au dépôt,
I want saisir une seule catégorie pour l'ensemble du lot plutôt qu'une catégorie par article membre,
so that la saisie reflète la réalité physique du lot (tous ses articles restent groupés sur une seule table) et ne peut plus produire un lot dont les membres sont dispersés sur des tables différentes.

## Acceptance Criteria

1. **Création d'un lot — une seule catégorie pour tout le lot (FR-022 amendé).** Étant donné que le bénévole crée un lot, quand il remplit le formulaire, alors il saisit **une** catégorie pour le lot entier (plus de sélecteur de catégorie par article membre) ; chaque article du lot ne saisit plus que nom/description, indicateur complet/incomplet et commentaire.

2. **Table assignée une seule fois pour tout le lot (FR-023 précisé).** Étant donné qu'un lot est créé avec sa catégorie, quand il est sauvegardé, alors **une seule** table est assignée à partir de cette catégorie (algorithme FR-023 inchangé : même table que les autres articles du vendeur dans cette catégorie, sinon la table la moins chargée toutes catégories confondues) **et tous les articles du lot partagent ce même numéro de table**.

3. **Modification de la catégorie d'un lot déjà enregistré.** Étant donné qu'un lot est enregistré en phase Dépôt, quand le bénévole modifie sa catégorie, alors une nouvelle table est assignée une seule fois pour le lot entier (même algorithme FR-023) et appliquée à tous ses membres, y compris ceux ajoutés dans la même requête ; les membres dont la catégorie ne change pas ne déclenchent aucune réassignation.

4. **Ajout d'un article à un lot existant.** Étant donné qu'un lot est enregistré avec sa catégorie, quand le bénévole ajoute un nouvel article au lot (sans changer la catégorie du lot), alors le nouvel article reçoit la catégorie et la table déjà partagées par le lot — la table déjà assignée est retrouvée directement (pas de nouveau calcul de la table la moins chargée), sans recalcul depuis zéro.

5. **Étiquette thermique et bilan de vente vendeur affichent la catégorie du lot.** Étant donné qu'une étiquette ou une ligne du bilan de vente PDF (Story 5.2) concerne un article membre d'un lot, quand elle est rendue, alors elle affiche la catégorie du lot (comportement déjà correct aujourd'hui car `Item.category` reste renseigné pour chaque membre — voir Dev Notes § Décision de conception, aucune régression attendue mais à couvrir par un test explicite).

6. **Un article membre d'un lot ne peut plus être modifié via l'endpoint article individuel.** Étant donné qu'un article appartient à un lot, quand une requête `PUT /api/items/{id}` cible cet article, alors elle est refusée avec 422 `item-belongs-to-lot` — seul `PUT /api/lots/{id}` peut modifier un article membre (nouvelle garde nécessaire : `ItemService.update()` supposait jusqu'ici que `item.getCategory()` par article restait librement modifiable article par article, hypothèse invalidée par cette story — voir Dev Notes).

7. **Migration des lots existants.** Étant donné des lots déjà enregistrés en base (aucune donnée réelle en production, mais l'environnement de dev local peut en contenir), quand la migration Liquibase s'exécute, alors chaque lot existant reçoit `category_id` déduit de son premier membre (par `id` croissant) — pas de perte de données, pas de réinitialisation manuelle requise.

## Tasks / Subtasks

- [x] **Backend — Migration Liquibase `lots.category_id` (AC 2, 7)**
  - [x] `pluribourse-backend/src/main/resources/db/changelog/033-lot-category.xml` (NEW) :
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

        <changeSet id="033-lot-category" author="pluribourse">
            <addColumn tableName="lots">
                <column name="category_id" type="BIGINT">
                    <constraints nullable="true" foreignKeyName="fk_lots_category" references="edition_categories(id)"/>
                </column>
            </addColumn>
            <!-- Backfill from each lot's first member (by id) — a lot always has >= 2 members
                 (FR-043), so this subquery always resolves. No real prod data at stake (sprint-change-
                 proposal-2026-08-24 § Point 3), but a populated local dev DB must not break. -->
            <sql>
                UPDATE lots l
                SET l.category_id = (
                    SELECT i.category_id FROM items i WHERE i.lot_id = l.id ORDER BY i.id ASC LIMIT 1
                );
            </sql>
            <addNotNullConstraint tableName="lots" columnName="category_id" columnDataType="BIGINT"/>

            <rollback>
                <dropColumn tableName="lots" columnName="category_id"/>
            </rollback>
        </changeSet>

    </databaseChangeLog>
    ```
  - [x] `db.changelog-master.xml` (UPDATE) — add `<include file="db/changelog/033-lot-category.xml"/>` after the `032-edition-currency.xml` line.
  - [x] **`items.category_id` stays untouched — do NOT make it nullable.** See Dev Notes § Décision de conception before touching `Item`.

- [x] **Backend — `Lot.category` entity field (AC 1, 2)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Lot.java` (UPDATE) — add after `sellerProfile`:
    ```java
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private EditionCategory category;
    ```
    Needs `import org.pluribourse.domain.edition.entity.*;` — already imported.

- [x] **Backend — DTOs: move `categoryId` from lot item to lot level (AC 1)**
  - [x] `CreateLotItemDto.java` (UPDATE) — remove `categoryId`:
    ```java
    public record CreateLotItemDto(
            @NotBlank @Size(max = 200) String name,
            boolean incomplete,
            @Size(max = 500) String comment
    ) {
    }
    ```
  - [x] `CreateLotDto.java` (UPDATE) — add `categoryId` as the 2nd component (right after `sellerProfileId`, mirroring `CreateItemDto`'s field order):
    ```java
    public record CreateLotDto(
            @NotNull Long sellerProfileId,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal globalPrice,
            @NotNull @Valid @Size(min = 2, max = 50, message = "A lot must contain between 2 and 50 items")
            List<CreateLotItemDto> items
    ) {
    }
    ```
  - [x] `UpdateLotItemDto.java` (UPDATE) — remove `categoryId`, keep `id`:
    ```java
    public record UpdateLotItemDto(
            Long id,
            @NotBlank @Size(max = 200) String name,
            boolean incomplete,
            @Size(max = 500) String comment
    ) {
    }
    ```
  - [x] `UpdateLotDto.java` (UPDATE) — add `categoryId` as the 1st component:
    ```java
    public record UpdateLotDto(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal globalPrice,
            @NotNull @Valid @Size(min = 2, max = 50, message = "A lot must contain between 2 and 50 items")
            List<UpdateLotItemDto> items
    ) {
    }
    ```
  - [x] `LotDto.java` (UPDATE) — add `categoryId`/`categoryName` (only constructed in `LotService`, not elsewhere — see Dev Notes § Sites d'appel positionnels, no compiler-driven sweep needed for this one):
    ```java
    public record LotDto(
            Long id,
            String name,
            BigDecimal globalPrice,
            Long categoryId,
            String categoryName,
            List<ItemDto> items
    ) {
    }
    ```

- [x] **Backend — `TableAssignmentService`: exclude a set of item ids, not just one (AC 3)**
  - [x] `TableAssignmentService.java` (UPDATE) — add a collection-based overload; the existing single-id overload delegates to it. New Javadoc explains why:
    ```java
    /**
     * NO_EXCLUSION is a sentinel, never a real id (IDENTITY starts at 1) — keeps the "exclude none"
     * case a valid non-empty JPQL IN-list instead of special-casing an empty Collection parameter.
     */
    private static final Set<Long> NO_EXCLUSION = Set.of(-1L);

    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition) {
        return assignTable(sellerProfile, category, edition, NO_EXCLUSION);
    }

    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition, Long excludeItemId) {
        return assignTable(sellerProfile, category, edition, excludeItemId == null ? NO_EXCLUSION : Set.of(excludeItemId));
    }

    /**
     * excludeItemIds must contain the ids of every item being reassigned together (AC 3: a lot
     * reassigns all its members' table in one operation, not one at a time) so none of them bias
     * the "already has a table" lookup or the load count via their own (still current, about to
     * change) rows. Pass {@link #NO_EXCLUSION} when assigning brand-new items.
     * <p>
     * Locks the category row first (joins the caller's transaction, held until it commits) so two
     * concurrent first-deposits into the same category serialize instead of both computing the
     * load count from a pre-insert state and picking the same table.
     */
    @Transactional
    public int assignTable(SellerProfile sellerProfile, EditionCategory category, Edition edition, Collection<Long> excludeItemIds) {
        categoryRepository.lockById(category.getId());
        return itemRepository.findTableNumberBySellerProfileIdAndCategoryId(sellerProfile.getId(), category.getId(), excludeItemIds)
                .orElseGet(() -> leastLoadedTable(category, edition, excludeItemIds));
    }

    private int leastLoadedTable(EditionCategory category, Edition edition, Collection<Long> excludeItemIds) {
        Set<Integer> tableNumbers = category.getTableNumbers();
        Map<Integer, Long> countsByTable = new TreeMap<>();
        for (Integer tableNumber : tableNumbers) {
            countsByTable.put(tableNumber, 0L);
        }
        for (Object[] row : itemRepository.countByTableNumber(edition.getId(), tableNumbers, excludeItemIds)) {
            countsByTable.put((Integer) row[0], (Long) row[1]);
        }
        return countsByTable.entrySet().stream()
                .min(Map.Entry.<Integer, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }
    ```
  - [x] `ItemRepository.java` (UPDATE) — both queries switch from the single-id null-check to `NOT IN` over the sentinel-safe collection:
    ```java
    @Query("""
            SELECT DISTINCT i.tableNumber FROM Item i
            WHERE i.sellerProfile.id = :sellerProfileId AND i.category.id = :categoryId
              AND i.id NOT IN :excludeItemIds
            """)
    Optional<Integer> findTableNumberBySellerProfileIdAndCategoryId(
            @Param("sellerProfileId") Long sellerProfileId, @Param("categoryId") Long categoryId,
            @Param("excludeItemIds") Collection<Long> excludeItemIds);

    @Query("""
            SELECT i.tableNumber, COUNT(i) FROM Item i
            WHERE i.edition.id = :editionId AND i.tableNumber IN :tableNumbers
              AND i.id NOT IN :excludeItemIds
            GROUP BY i.tableNumber
            """)
    List<Object[]> countByTableNumber(@Param("editionId") Long editionId, @Param("tableNumbers") Collection<Integer> tableNumbers,
                                      @Param("excludeItemIds") Collection<Long> excludeItemIds);
    ```
  - [x] `ItemService.java` — its two call sites (`assignTable(sellerProfile, category, edition)` in `create()`, `assignTable(item.getSellerProfile(), category, item.getEdition(), item.getId())` in `update()`) are unchanged — they resolve to the new overloads automatically.

- [x] **Backend — `LotService.create()` rewrite: one category, one lock, one table (AC 1, 2)**
  - [x] `LotService.java` (UPDATE) — replaces the per-item category list/lock-ordering loop entirely (deadlock-ordering by ascending category id is no longer needed — there is only one category to lock per lot now):
    ```java
    @Transactional
    public LotDto create(CreateLotDto dto) {
        Edition edition = editionService.getActiveEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = editionScopedLookup.findSellerInEdition(dto.sellerProfileId(), edition);
        EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), edition);

        Lot lot = new Lot();
        lot.setEdition(edition);
        lot.setSellerProfile(sellerProfile);
        lot.setCategory(category);
        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        lot = repository.save(lot);

        // Lock the seller once for the whole lot (FR-026), then assign ONE shared table for every
        // member from the lot's single category (FR-023) — same lock ordering as before (seller,
        // then category) but only ever one category per lot now, so no cross-lot deadlock ordering
        // is needed anymore.
        SellerProfile lockedSeller = sellerRepository.lockById(sellerProfile.getId())
                .orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()));
        int nextItemNumber = lockedSeller.getNextItemNumber();
        int tableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition);

        Item[] createdItems = new Item[dto.items().size()];
        for (int i = 0; i < dto.items().size(); i++) {
            CreateLotItemDto itemDto = dto.items().get(i);
            if (nextItemNumber > Item.MAX_BARCODE_SEGMENT) {
                throw new TooManyItemsException(sellerProfile.getId());
            }
            Item item = new Item();
            item.setEdition(edition);
            item.setSellerProfile(sellerProfile);
            item.setCategory(category);
            item.setLot(lot);
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            item.setTableNumber(tableNumber);
            item.setItemNumber(nextItemNumber++);
            createdItems[i] = itemRepository.save(item);
        }
        lockedSeller.setNextItemNumber(nextItemNumber);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), category.getId(), category.getName(),
                itemMapper.toDtos(Arrays.asList(createdItems)));
    }
    ```

- [x] **Backend — `LotService.update()` rewrite: lot-level category change, shared table reassignment (AC 3, 4)**
  - [x] `LotService.java` (UPDATE) — the deletion-of-absent-members block at the top stays **unchanged**; everything from the old category-diff loop onward is replaced:
    ```java
    @Transactional
    public LotDto update(Long lotId, UpdateLotDto dto) {
        Lot lot = repository.findById(lotId).orElseThrow(() -> new LotNotFoundException(lotId));
        Edition edition = lot.getEdition();
        PhaseGuard.requireDepositPhase(edition);
        SellerProfile sellerProfile = lot.getSellerProfile();
        EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), edition);

        List<Item> currentMembers = lot.getItems();
        Map<Long, Item> currentById = currentMembers.stream().collect(Collectors.toMap(Item::getId, item -> item));

        List<Integer> updateIndexes = new ArrayList<>();
        List<Integer> newIndexes = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < dto.items().size(); i++) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            if (itemDto.id() != null) {
                if (!currentById.containsKey(itemDto.id())) {
                    throw new ItemNotFoundException(itemDto.id());
                }
                if (!seenIds.add(itemDto.id())) {
                    throw new DuplicateLotItemException(itemDto.id());
                }
                updateIndexes.add(i);
            } else {
                newIndexes.add(i);
            }
        }

        // Unchanged from the current implementation: any current member absent from the submitted
        // list is removed for good (see Javadoc below / story 3.10 Dev Notes).
        Set<Long> submittedIds = updateIndexes.stream().map(i -> dto.items().get(i).id()).collect(Collectors.toSet());
        for (Item member : currentMembers) {
            if (!submittedIds.contains(member.getId())) {
                itemRepository.delete(member);
            }
        }

        // A lot has ONE category shared by every member (FR-022/FR-023, this story) — reassign the
        // shared table only when the category actually changes or a member is added, never per item.
        boolean categoryChanged = !lot.getCategory().getId().equals(category.getId());
        boolean hasNewItems = !newIndexes.isEmpty();
        Integer sharedTableNumber = null;
        if (categoryChanged) {
            // Excludes every remaining current member (still sitting on the OLD table) so their own
            // rows never bias the recount of the NEW category's least-loaded table.
            Set<Long> remainingMemberIds = updateIndexes.stream().map(i -> dto.items().get(i).id()).collect(Collectors.toSet());
            sharedTableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition, remainingMemberIds);
        } else if (hasNewItems) {
            // Category unchanged: the seller's existing members are still in it, so the normal
            // "already has a table in this category" lookup finds them directly — no exclusion.
            sharedTableNumber = tableAssignmentService.assignTable(sellerProfile, category, edition);
        }

        SellerProfile lockedSeller = hasNewItems
                ? sellerRepository.lockById(sellerProfile.getId()).orElseThrow(() -> new SellerNotFoundException(sellerProfile.getId()))
                : sellerProfile;
        int nextItemNumber = hasNewItems ? lockedSeller.getNextItemNumber() : 0;

        for (int i : updateIndexes) {
            UpdateLotItemDto itemDto = dto.items().get(i);
            Item item = currentById.get(itemDto.id());
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            if (categoryChanged) {
                item.setCategory(category);
                item.setTableNumber(sharedTableNumber);
            }
            itemRepository.save(item);
        }
        for (int i : newIndexes) {
            if (nextItemNumber > Item.MAX_BARCODE_SEGMENT) {
                throw new TooManyItemsException(sellerProfile.getId());
            }
            UpdateLotItemDto itemDto = dto.items().get(i);
            Item item = new Item();
            item.setEdition(edition);
            item.setSellerProfile(sellerProfile);
            item.setCategory(category);
            item.setLot(lot);
            item.setName(itemDto.name());
            item.setIncomplete(itemDto.incomplete());
            item.setComment(itemDto.comment());
            item.setTableNumber(sharedTableNumber);
            item.setItemNumber(nextItemNumber++);
            itemRepository.save(item);
        }
        if (hasNewItems) {
            lockedSeller.setNextItemNumber(nextItemNumber);
        }

        lot.setCategory(category);
        lot.setName(dto.name());
        lot.setGlobalPrice(dto.globalPrice());
        repository.save(lot);

        return new LotDto(lot.getId(), lot.getName(), lot.getGlobalPrice(), category.getId(), category.getName(),
                itemMapper.toDtos(itemRepository.findAllByLotIdOrderById(lot.getId())));
    }
    ```
  - [x] Update the class-level Javadoc on `update()` — it currently says "its category may be reassigned, which moves its table per FR-023" about individual members; reword to describe lot-level category reassignment instead.
  - [x] `delete()` is unchanged.

- [x] **Backend — guard `ItemService.update()` against lot members (AC 6)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/ItemBelongsToLotException.java` (NEW):
    ```java
    package org.pluribourse.domain.item.exception;

    import org.pluribourse.shared.exception.BusinessException;
    import org.springframework.http.HttpStatus;

    public class ItemBelongsToLotException extends BusinessException {

        public ItemBelongsToLotException(Long id) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "item-belongs-to-lot",
                    "Item " + id + " belongs to a lot and can only be modified through the lot — its category and table are shared with every other member.");
        }
    }
    ```
  - [x] `ItemService.java` (UPDATE) — `update()` gains the guard right after `PhaseGuard`, same position `LotService` uses for its own business checks (resolve entity → phase guard → business rule):
    ```java
    @Transactional
    public ItemDto update(Long id, CreateItemDto dto) {
        Item item = findById(id);
        PhaseGuard.requireDepositPhase(item.getEdition());
        if (item.getLot() != null) {
            throw new ItemBelongsToLotException(id);
        }
        mapper.updateEntityFromDto(dto, item);
        if (!item.getCategory().getId().equals(dto.categoryId())) {
            EditionCategory category = editionScopedLookup.findCategoryInEdition(dto.categoryId(), item.getEdition());
            int tableNumber = tableAssignmentService.assignTable(item.getSellerProfile(), category, item.getEdition(), item.getId());
            item.setCategory(category);
            item.setTableNumber(tableNumber);
        }
        return mapper.toDto(repository.save(item));
    }
    ```
  - [x] `delete()` in `ItemService` is deliberately **not** guarded the same way — see Dev Notes § Hors périmètre.
  - [x] `GlobalExceptionHandler` needs no change — `BusinessException` subclasses are already mapped generically (confirm by reading `GlobalExceptionHandler.java` if unfamiliar with the pattern; every other exception in this package follows it with zero handler-side wiring).

- [x] **Backend — fix 9 test files with a shared per-item categoryId → hoist it to the lot level (mechanical, AC 1)**
  - [x] Every file below constructs 2-item lots where **both** `CreateLotItemDto` calls already pass the *same* `categoryId` variable — this is a pure DTO-shape fix (move that value to `CreateLotDto`'s new 2nd argument, delete it from both `CreateLotItemDto` calls), **no test assertion changes**:
    - `src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java` — 1 `CreateLotDto` (line ~193), 2 `CreateLotItemDto` (lines ~194-195)
    - `src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` — 2 `CreateLotDto` (lines ~215, ~222), 4 `CreateLotItemDto` (lines ~216-217, ~223-224)
    - `src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` — 1 `CreateLotDto` (line ~184), 2 `CreateLotItemDto` (lines ~185-186)
    - `src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java` — 1 `CreateLotDto` (line ~215), 2 `CreateLotItemDto` (lines ~216-217)
    - `src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — 1 `CreateLotDto` (line ~155), 2 `CreateLotItemDto` (lines ~156-157)
    - `src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java` — 1 `CreateLotDto` (line ~212), 2 `CreateLotItemDto` (lines ~213-214)
    - `src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` — 2 `CreateLotDto` (lines ~164, ~177), 4 `CreateLotItemDto` (lines ~165-166, ~178-179)
    - `src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java` — 1 `CreateLotDto` (line ~155), 2 `CreateLotItemDto` (lines ~156-157)
    - `src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java` — 1 `CreateLotDto` (line ~100), 2 `CreateLotItemDto` (lines ~101-102)
  - [x] Example transform (`ThermalLabelPrintingIT.java`):
    ```java
    // Before
    CreateLotDto payload = new CreateLotDto(sellerAId, "Lot Duo", new BigDecimal("12.00"), List.of(
            new CreateLotItemDto(categoryId, "Piece A", false, null),
            new CreateLotItemDto(categoryId, "Piece B", false, null)
    ));
    // After
    CreateLotDto payload = new CreateLotDto(sellerAId, categoryId, "Lot Duo", new BigDecimal("12.00"), List.of(
            new CreateLotItemDto("Piece A", false, null),
            new CreateLotItemDto("Piece B", false, null)
    ));
    ```
  - [x] Compiler-driven sweep: after this task, `mvn -pl pluribourse-backend compile test-compile` must be clean before moving to `LotManagementIT.java` — any remaining `CreateLotDto`/`CreateLotItemDto`/`UpdateLotDto`/`UpdateLotItemDto` call site not listed above (there should be none beyond `LotManagementIT.java` and `LotService.java` itself) is a sign this list missed something; re-grep `new (Create|Update)Lot(Item)?Dto\(` across `src/` before considering this task done.

- [x] **Backend — restructure `LotManagementIT.java` for the single-category model (AC 1, 2, 3, 4, 6)**
  - [x] This file is the only one exercising genuinely cross-category lots (fixture: `Jouets=[1,2]`, `Livres=[2,3]`, overlapping tables — keep this fixture, it stays useful for single-category assignment tests). Every scenario built around "different categories within the same lot" or "reassign one member's category independently" no longer has an equivalent — restructure around these scenarios instead (reuse `@Order` sequencing and the existing session/edition/category setup in `@BeforeAll`):
    1. Create lot outside Deposit phase → still blocked (`no-active-edition`, unchanged from Story 2.10's fix) — `CreateLotDto` now needs a `categoryId` even for this made-up payload.
    2. Advance edition to Deposit — unchanged.
    3. Create seller — unchanged.
    4. Create lot with 1 item → 400 (unchanged, still enforced by `@Size(min = 2)`).
    5. Create lot with a blank item name → 400 (unchanged).
    6. **Create lot with 2 items in `Jouets`** → both items get the SAME table (assert `tableNumber` equal for both, not a per-item different table like the old test) — this replaces the old "different categories → different tables" scenario, since that shape no longer exists.
    7. Get items by seller → lot items still expose `lotId`/`lotName`/`lotPrice`/null `price` (unchanged) **and now also the lot's `categoryId`/`categoryName`** via `ItemDto.categoryId`/`categoryName` (still populated per item — see Dev Notes § Décision de conception) — add an assertion on that.
    8. Create lot with unknown `categoryId` (now on `CreateLotDto`, not on an item) → 404 `category-not-found` (same error type, moved to a different DTO-level field).
    9. Create lot with a category from another edition → 404 `category-not-found` (same, moved to lot level).
    10. Update lot name/price only (same `categoryId`) → reflected on all members, `categoryChanged` stays false, no table reassignment (assert unchanged table numbers).
    11. Create a second lot (for deletion tests later) — unchanged shape, one `categoryId` for the whole lot.
    12. **Update lot: add an item without changing category** → new item lands on the lot's already-assigned table (exercises the `hasNewItems && !categoryChanged` branch — no exclusion needed, `findTableNumberBySellerProfileIdAndCategoryId` finds the existing members directly).
    13. **Update lot: change the lot's category (`Jouets` → `Livres`)** → every member (old + newly added in the same request) gets reassigned to a single new shared table computed once, excluding the lot's own remaining members from the recount (exercises the `categoryChanged` branch and the multi-id `excludeItemIds` path — this is the scenario most likely to hide a bug, mirror the old test's assertion style but assert ALL members land on the SAME new table).
    14. Update lot with an item id not belonging to the lot → 404 `item-not-found` (unchanged).
    15. Update lot: remove a member → 200, member gone from subsequent `GET /items` (unchanged).
    16. Update lot to a single item → 400 (unchanged).
    17. Update unknown lot → 404 `lot-not-found` (unchanged).
    18. Update lot with a duplicate item id → 422 `duplicate-lot-item-id` (unchanged).
    19. Delete unknown lot → 404 (unchanged).
    20. Delete lot → removes lot and all members (unchanged).
    21. **NEW — `PUT /api/items/{id}` on a lot member → 422 `item-belongs-to-lot`** (AC 6): use an item id captured from an earlier lot-creation step; assert the error type ends with `/item-belongs-to-lot`.
    22. Advance edition to Sale phase — unchanged.
    23. Update/delete/create lot outside Deposit phase → still 422 `item-modification-locked` (unchanged) — payloads now need a `categoryId` on `CreateLotDto`/`UpdateLotDto` to remain structurally valid, even though the phase guard rejects before reaching category resolution.
  - [x] Drop the class-level Javadoc line "applied independently per lot item" (no longer true — table assignment is now lot-wide, not per item) and reword it to describe the single-category model.

- [x] **Frontend — models (AC 1)**
  - [x] `pluribourse-frontend/src/app/models/lot.model.ts` (UPDATE):
    ```typescript
    import { ItemDto } from './item.model';

    export interface CreateLotItemRequest {
      name: string;
      incomplete: boolean;
      comment: string | null;
    }

    export interface CreateLotRequest {
      sellerProfileId: number;
      categoryId: number;
      name: string;
      globalPrice: number;
      items: CreateLotItemRequest[];
    }

    export interface UpdateLotItemRequest {
      id: number | null;
      name: string;
      incomplete: boolean;
      comment: string | null;
    }

    export interface UpdateLotRequest {
      categoryId: number;
      name: string;
      globalPrice: number;
      items: UpdateLotItemRequest[];
    }

    export interface LotDto {
      id: number;
      name: string;
      globalPrice: number;
      categoryId: number;
      categoryName: string;
      items: ItemDto[];
    }
    ```
  - [x] `item.model.ts` — **no change** (`ItemDto.categoryId`/`categoryName` stay as-is; still populated for lot members).

- [x] **Frontend — `lot-form.component.ts` (AC 1)**
  - [x] Remove `categoryId` from the `LotItemRow` interface, `createItemRow()`, `emptyItemRow()`.
  - [x] Add a `categoryId` control to the top-level `form` group:
    ```typescript
    readonly form = this.fb.nonNullable.group({
      name: ['', [Validators.required, Validators.maxLength(200)]],
      globalPrice: [0, [Validators.required, Validators.min(0.01)]],
      categoryId: [null as number | null, [Validators.required]],
      items: this.itemsFormArray,
    });
    ```
  - [x] In the edit-mode `effect()`, patch it from the lot: `this.form.patchValue({ name: lot.name, globalPrice: lot.globalPrice, categoryId: lot.categoryId });`
  - [x] In `resetForm()`, also reset it: `this.form.patchValue({ name: '', globalPrice: 0, categoryId: null });`
  - [x] `toItemPayload()` drops `categoryId` from its return value.
  - [x] `onSubmit()` — both `CreateLotRequest`/`UpdateLotRequest` payloads add `categoryId: raw.categoryId!`.

- [x] **Frontend — `lot-form.component.html` (AC 1)**
  - [x] Move the category `mat-select` out of the per-item-row loop into the top `form-row` (alongside lot name/price):
    ```html
    <div class="form-row">
      <mat-form-field appearance="outline">
        <mat-label>{{ 'volunteer.deposit.item.lotForm.lotName' | translate }}</mat-label>
        <input matInput formControlName="name" type="text" />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'volunteer.deposit.item.lotForm.lotPrice' | translate }}</mat-label>
        <input matInput formControlName="globalPrice" type="number" step="1" min="0" />
        <span matTextSuffix>{{ currency() }}</span>
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>{{ 'volunteer.deposit.item.lotForm.lotCategory' | translate }}</mat-label>
        <mat-select formControlName="categoryId">
          @for (category of categories(); track category.id) {
            <mat-option [value]="category.id">{{ category.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </div>
    ```
  - [x] Remove the now-empty per-item `mat-form-field appearance="outline" class="lot-item-row__category"` block inside the `@for (itemGroup of itemsFormArray.controls; ...)` loop.

- [x] **Frontend — `deposit-page.component.ts` (AC 1)** — not a `lot-form`/`lot.model` file, easy to miss: it hand-builds a `LotDto` object literal from already-loaded `ItemDto[]` (no HTTP round-trip), so it breaks as soon as `LotDto` gains required `categoryId`/`categoryName`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (UPDATE) — `startEditLot()` (~line 154-168): add `categoryId`/`categoryName` to the literal passed to `this.editingLot.set({...})`, sourced from `first` (an `ItemDto`, which already carries both per lot member):
    ```typescript
    // Before
    this.editingLot.set({
      id: lotId,
      name: first.lotName!,
      globalPrice: first.lotPrice!,
      items: lotItems,
    });
    // After
    this.editingLot.set({
      id: lotId,
      name: first.lotName!,
      globalPrice: first.lotPrice!,
      categoryId: first.categoryId,
      categoryName: first.categoryName,
      items: lotItems,
    });
    ```
  - [x] `deposit-page.component.spec.ts` (UPDATE) — `MOCK_LOT` (~line 72) needs `categoryId`/`categoryName` added, matching `MOCK_LOT_ITEM`'s values. Test `'startEditLot() rebuilds the LotDto from already-loaded items without a new HTTP call'` (~line 352-370): add the same `categoryId`/`categoryName` to the `expect(component.editingLot()).toEqual({...})` object.

- [x] **Frontend — i18n (AC 1)**
  - [x] `fr.json` — rename `volunteer.deposit.item.lotForm.itemCategory` → `lotCategory`, value `"Catégorie du lot"` (was `"Catégorie"`).
  - [x] `en.json` — same rename, value `"Lot category"` (was `"Category"`).

- [x] **Frontend — `lot-form.component.spec.ts` (AC 1)** — 4 existing tests need a real rewrite here, not just new assertions (verified against the actual file):
  - [x] `MOCK_LOT` (line 16-50) — add `categoryId: 1, categoryName: 'Jouets'` at the lot level; the existing per-item `categoryId`/`categoryName` inside `MOCK_LOT.items[]` stay as-is (still valid on `ItemDto`).
  - [x] `fillValidForm()` helper (line 222-227) — with strictly-typed reactive forms, `categoryId` is no longer a key on the per-item group; leaving it in `setValue({...})` is a compile error, not just a stale assertion:
    ```typescript
    // Before
    function fillValidForm(cmp: LotFormComponent): void {
      cmp.form.controls.name.setValue('Lot Jouets');
      cmp.form.controls.globalPrice.setValue(15);
      cmp.itemsFormArray.at(0).setValue({ id: null, name: 'Piece A', categoryId: 1, incomplete: false, comment: '' });
      cmp.itemsFormArray.at(1).setValue({ id: null, name: 'Piece B', categoryId: 1, incomplete: false, comment: '' });
    }
    // After
    function fillValidForm(cmp: LotFormComponent): void {
      cmp.form.controls.name.setValue('Lot Jouets');
      cmp.form.controls.globalPrice.setValue(15);
      cmp.form.controls.categoryId.setValue(1);
      cmp.itemsFormArray.at(0).setValue({ id: null, name: 'Piece A', incomplete: false, comment: '' });
      cmp.itemsFormArray.at(1).setValue({ id: null, name: 'Piece B', incomplete: false, comment: '' });
    }
    ```
  - [x] `'calls create with the assembled payload and emits saved'` (line 108-125) — `categoryId` moves from each item entry to the top-level request:
    ```typescript
    expect(lotServiceMock.create).toHaveBeenCalledWith({
      sellerProfileId: 5,
      categoryId: 1,
      name: 'Lot Jouets',
      globalPrice: 15,
      items: [
        { name: 'Piece A', incomplete: false, comment: null },
        { name: 'Piece B', incomplete: false, comment: null },
      ],
    });
    ```
  - [x] `'prefills the form and marks isEditing as true'` (line 171-183) — `categoryId` is no longer on the per-item value; add a separate assertion on the new top-level control:
    ```typescript
    expect(component.form.controls.categoryId.value).toBe(1);
    expect(component.itemsFormArray.at(0).value).toEqual({
      id: 100,
      name: 'Piece A',
      incomplete: false,
      comment: '',
    });
    ```
  - [x] `'calls update with existing ids preserved and null id for a newly added row'` (line 185-201) — the old scenario deliberately gave the new item C a *different* `categoryId` (2) than the lot's existing members (1); that shape no longer exists (one category per lot, no per-item override), so simplify to adding item C without any category variation, and move `categoryId` to the update request's top level:
    ```typescript
    component.addItemRow();
    component.itemsFormArray.at(2).setValue({ id: null, name: 'Piece C', incomplete: true, comment: 'Neuve' });

    await component.onSubmit();

    expect(lotServiceMock.update).toHaveBeenCalledWith(20, {
      categoryId: 1,
      name: 'Lot Jouets',
      globalPrice: 15,
      items: [
        { id: 100, name: 'Piece A', incomplete: false, comment: null },
        { id: 101, name: 'Piece B', incomplete: false, comment: null },
        { id: null, name: 'Piece C', incomplete: true, comment: 'Neuve' },
      ],
    });
    expect(lotServiceMock.create).not.toHaveBeenCalled();
    ```
  - [x] Add one new test: submitting with the lot-level `categoryId` selected but otherwise unchanged succeeds — there is no per-item category control left to fill, so this mainly guards against a regression where `categoryId` silently stops being required on the top-level group (`Validators.required` on `form.controls.categoryId`, mirrored from `lot-form.component.ts`'s new control).

- [x] **Verification**
  - [x] `mvn -pl pluribourse-backend test` — full backend suite green, 0 failure.
  - [x] `npm test` (in `pluribourse-frontend/`) — full frontend suite green.
  - [x] `mvn -pl pluribourse-backend clean package` and a frontend production build — confirm no residual compile-time reference to the removed `CreateLotItemDto.categoryId()`/`UpdateLotItemDto.categoryId()` anywhere (grep, don't just trust the IDE).

### Review Findings

- [x] [Review][Decision] Backfill de migration `033-lot-category.xml` peut laisser `category_id` NULL et faire échouer `addNotNullConstraint` — Si un lot a moins de 2 membres au moment de la migration (via le bug préexistant documenté de `ItemService.delete()`, § Hors périmètre), la sous-requête corrélée du backfill (lignes 17-22) ne retourne aucune ligne pour ce lot et `addNotNullConstraint` (ligne 23) fait échouer toute la migration sur une base de dev locale qui contiendrait un tel lot. Résolu à la racine : garde ajoutée sur `ItemService.delete()` (voir ci-dessous) empêchant désormais la création d'un tel lot cassé ; utilisateur recrée sa BDD de dev pour tout lot déjà cassé aujourd'hui.
- [x] [Review][Patch] AC 5 exige un test explicite prouvant que l'étiquette et le bilan de vente affichent la catégorie du lot pour un article membre — assertions ajoutées : `ThermalLabelPrintingIT` Order 8 (les deux étiquettes de `Lot Duo` affichent `--- Jouets ---`) et `SettlementReportPrintingIT` Order 8 (`Jouets` apparaît 2 fois : ligne de l'article standalone `Peluche` ET ligne du `Lot Invendu`) [pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java, pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java]
- [x] [Review][Patch] `LotService.update()` peut transmettre un `Set` vide (au lieu du sentinel `NO_EXCLUSION`) à `TableAssignmentService.assignTable(..., Collection<Long>)` quand la catégorie change et que tous les membres soumis sont nouveaux — corrigé à la source dans `TableAssignmentService.assignTable(Collection<Long>)` (substitue `NO_EXCLUSION` si la collection reçue est vide), protège tout appelant futur [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/TableAssignmentService.java:53] — test de régression ajouté (`LotManagementIT` Order 24 : changement de catégorie avec tous membres remplacés par des nouveaux)
- [x] [Review][Patch] **Bug découvert pendant la revue en écrivant le test de régression ci-dessus** : `LotService.update()` supprimait des membres via `itemRepository.delete(member)` sans les retirer de la collection en mémoire `lot.getItems()` (même référence que `currentMembers`) — si un changement de catégorie/ajout déclenche une requête intermédiaire (`assignTable`), celle-ci force un auto-flush des suppressions en attente, puis le `repository.save(lot)` final échoue en tentant de fusionner (`merge`) des références d'articles déjà supprimés en base (`ObjectNotFoundException`, 500). Bug préexistant depuis la Story 3.10 (logique de réconciliation), jamais exercé par un scénario combinant suppression de membre(s) + changement de catégorie/ajout dans le même appel. Corrigé : `currentMembers.removeIf(...)` retire désormais les membres supprimés de la collection en même temps que la suppression en base [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java:129-136]
- [x] [Review][Patch] `sharedTableNumber` (nullable, initialisé à `null`) n'a aucune garde défensive avant d'être utilisé dans les boucles d'écriture — repose implicitement sur `categoryChanged || hasNewItems` — vérifié : la logique actuelle est saine (les deux sites de lecture ne sont atteints que si l'une des deux branches a bien tourné), donc pas d'ajout de garde runtime pour un cas qui ne peut pas se produire aujourd'hui (CLAUDE.md), seulement un commentaire clarifiant l'invariant pour un futur refactor [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java:139]
- [x] [Review][Patch] Incohérence documentaire sur le nombre de scénarios de test restructurés (23 vs 25) entre la Task checklist, le commentaire du changelog sprint-status et les Completion Notes List — clarifié : Completion Notes List mis à jour (29 scénarios finaux, historique 23 planifiés → 25 en fin de dev-story → 29 en revue) ; le commentaire sprint-status.yaml (ligne 75) reste inchangé car c'est une entrée de journal datée reflétant fidèlement le plan au moment de la création de la story, pas l'état final [_bmad-output/implementation-artifacts/3-14-categorie-du-lot.md]
- [x] [Review][Patch] L'exemption de garde sur `PATCH /api/items/{id}` pour un article membre de lot est justifiée dans les Dev Notes mais jamais vérifiée par un test — test ajouté (`LotManagementIT` Order 25 : `PATCH` sur un article membre de lot réussit toujours, `category`/`tableNumber` non affectés) [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemService.java]
- [x] [Review][Defer] Décision de conception assumée : `Item.category` reste renseigné (recopié du lot) au lieu d'être retiré comme l'énonçait littéralement le sprint-change-proposal — bien argumentée dans les Dev Notes (risques concrets identifiés), décision technique interne selon le dev, mais reste un écart vis-à-vis du texte du sprint-change-proposal [_bmad-output/implementation-artifacts/3-14-categorie-du-lot.md:644] — deferred, pre-existing rationale already documented
- [x] [Review][Defer] Couplage `Item.category`/`Lot.category` maintenu uniquement par convention de code (recopié à l'écriture), sans contrainte DB ni test de cohérence garantissant qu'ils ne divergent jamais [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java] — deferred, pre-existing design tradeoff
- [x] [Review][Defer] Le frontend (`startEditLot()`) dérive `categoryId`/`categoryName` du premier item du lot sans vérification défensive si les membres divergent [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts] — deferred, dépend de l'invariant backend
- [x] [Review][Defer] Sentinelle `NO_EXCLUSION = Set.of(-1L)` fragile/peu documentée hors d'un seul commentaire [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/TableAssignmentService.java:30] — deferred, pre-existing pattern, fonctionnel aujourd'hui
- [x] [Review][Patch] Bug préexistant `ItemService.delete()` permettait de supprimer un membre de lot individuellement et de casser l'invariant "≥2 membres" (FR-043) — corrigé : `ItemService.delete()` refuse désormais (422 `lot-below-minimum-members`, nouvelle `LotBelowMinimumMembersException`) uniquement quand le lot n'a plus que 2 membres ; la suppression reste autorisée au-dessus de ce seuil (décision utilisateur : suppression individuelle d'un membre de lot permise tant que le lot garde ≥ 2 membres, pas d'interdiction totale) [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemService.java, pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/LotBelowMinimumMembersException.java] — 2 tests ajoutés (`LotManagementIT` Order 22-23 : suppression autorisée à 3→2, refusée à 2→1)

## Dev Notes

### Décision de conception — `Item.category` reste renseigné pour les membres de lot (écart assumé par rapport à l'énoncé littéral du sprint-change-proposal)

Le `sprint-change-proposal-2026-08-24.md` (point 3) dit littéralement : *"Retrait de `Item.category` pour les membres d'un lot"* et *"`ThermalLabelRenderer` : source de la catégorie affichée passe de `item.getCategory()` à `lot.getCategory()`"*. Cette story **ne suit pas cette phrase à la lettre** — décision actée ici, sans besoin de validation utilisateur (choix d'implémentation interne, pas un choix métier/UX) :

**`Item.category` reste `NOT NULL` et continue d'être renseigné pour CHAQUE membre de lot, mais n'est plus choisi indépendamment — il est copié depuis `Lot.category` à l'écriture** (`LotService.create()`/`update()` font `item.setCategory(category)` où `category` est la catégorie du lot). Le champ `categoryId` disparaît uniquement des **DTOs d'entrée** (`CreateLotItemDto`, `UpdateLotItemDto`) — jamais de l'entité `Item` ni des DTOs de lecture (`ItemDto`, `ItemCatalogDto` gardent `categoryId`/`categoryName` inchangés).

**Pourquoi.** Une analyse exhaustive du code réel (pas seulement de l'énoncé du sprint-change-proposal) montre que rendre `Item.category` réellement nullable pour les membres de lot casse silencieusement une chaîne de dépendances bien plus large que prévu :

- **4 requêtes `ItemRepository` avec `JOIN FETCH i.category` (inner join, pas `LEFT JOIN`)** perdraient purement et simplement les articles de lot des résultats si leur `category_id` devenait `NULL` : `findAllBySellerProfileIdOrderByItemNumberAsc` (impression étiquettes/bordereau), `findAllByEditionIdForCatalog` (catalogue admin), `findAllBySellerProfileIdForSettlementReport` et `findAllByEditionIdForSettlementReport` (bilans de vente). Un lot deviendrait invisible dans le catalogue et dans les bilans — régression majeure, aucune AC ne le demande.
- **`ItemCatalogService.getCatalog()`** filtre directement `i.getCategory().getId()` (ligne 53) — `NullPointerException` sur tout article de lot dès qu'un filtre catégorie est actif.
- **Le tri catalogue par `"category.name"`** (`ItemCatalogService.ALLOWED_SORT_FIELDS`, résolu par réflexion sur `Item` par la librairie tierce `jPageFlow`/`FilterService`, **avant** le mapping vers DTO) n'a aucune garantie documentée de tolérance au `null` sur un chemin imbriqué — risque de plantage de tri, pas seulement de valeur vide.
- **`ItemService.update()`** fait déjà `item.getCategory().getId()` sans null-check (ligne 72) — `NullPointerException` (500) si jamais atteint pour un article de lot.
- **`SettlementReportRenderer.buildUnsoldItemsTable()`** et **`EditionArchivingService.archiveEdition()`** lisent aussi `item.getCategory().getName()` directement sur des articles potentiellement membres de lot.

Garder `Item.category` toujours renseigné (recopié depuis le lot) élimine tous ces risques sans aucune modification de ces 6 fichiers, tout en satisfaisant intégralement FR-022 (plus de **saisie** individuelle de catégorie par article de lot — le formulaire ne l'expose plus) et FR-023 (table assignée une seule fois, à partir de la catégorie du lot, partagée par tous les membres). `ThermalLabelRenderer` n'a donc **pas besoin** d'être modifié : `item.getCategory().getName()` est déjà, par construction, la catégorie du lot pour un membre. Un test explicite (AC 5, Task "restructure LotManagementIT") vérifie ce fait plutôt que de le supposer.

**Conséquence positive notable :** cette décision simplifie fortement `LotService` — la boucle de verrouillage multi-catégories par ordre croissant d'id (`lockOrder`, anti-deadlock entre lots concurrents référençant les mêmes catégories en ordre inverse) disparaît entièrement puisqu'il n'y a plus qu'une seule catégorie par lot à verrouiller.

### Écart avec l'UX doc existant (non actualisé par cette story)

`EXPERIENCE.md` (Flow 1, ligne 266) décrit encore "...ajoute les articles du lot (nom + catégorie par article)..." — l'exact opposé de l'AC1. C'est une trace de conception obsolète, pas une erreur de cette story : le comportement implémenté suit le sprint-change-proposal (source de vérité pour ce changement, validée avec l'utilisateur), pas ce fragment de narration UX. Ne pas se fier à cette ligne pour concevoir le formulaire ; `EXPERIENCE.md` n'est pas mis à jour par cette story.

### Portée volontairement exclue — `PATCH /api/items/{id}` (complétude) non gardé contre les membres de lot

Seul `PUT /api/items/{id}` (modification nom/prix/catégorie) reçoit la garde `ItemBelongsToLotException` (AC 6, portée strictement lue). `PATCH /api/items/{id}` (`ItemCompletenessRequest` — bascule complet/incomplet + commentaire, déjà autorisé "dans toutes les phases" par FR-025) reste accessible sur un membre de lot sans garde supplémentaire : il ne touche ni `category` ni `tableNumber`, donc ne réintroduit aucun des risques (catégorie/table divergentes) que cette story corrige. Vérifié, hors périmètre par construction.

### Portée volontairement exclue — `ItemService.delete()` non gardé contre les membres de lot

`ItemService.delete()` permet aujourd'hui déjà de supprimer un article individuel via `DELETE /api/items/{id}`, y compris — en théorie — un membre de lot, ce qui casserait la règle "un lot a toujours ≥ 2 membres" (FR-043) et contournerait le nettoyage FK géré par `LotService.delete()`/`update()`. **C'est une faille préexistante, non introduite ni aggravée par cette story** (elle existait déjà identiquement avant, `Item.lot` existe depuis la Story 3.3) : contrairement à `update()`, dont le risque passe de "incohérence silencieuse" à "NullPointerException" à cause de cette story si `Item.category` avait été rendu nullable — ce qui n'est plus le cas avec la décision ci-dessus — `delete()` ne voit aucun changement de risque. Vérifié et volontairement laissé hors périmètre, sans besoin de validation utilisateur (bug préexistant orthogonal, à traiter dans une story dédiée si confirmé comme un besoin réel).

### Sites d'appel positionnels de DTO record (compilation)

- `LotDto` : construit uniquement dans `LotService.java` (2 sites, tous deux mis à jour par les tâches ci-dessus) — **aucun test ne le construit positionnellement** (toujours désérialisé depuis une réponse JSON via `ObjectMapper`), donc aucun balayage compilateur nécessaire pour ce DTO au-delà de `LotService` lui-même.
- `CreateLotDto`/`CreateLotItemDto` : 11 fichiers au total (`grep -rn "new \(Create\|Update\)Lot\(Item\)\?Dto("` depuis `pluribourse-backend/`) — `LotService.java` (production) + 10 fichiers de test. 9 d'entre eux n'utilisent qu'une seule catégorie partagée par les deux membres du lot qu'ils créent : correctif mécanique listé exhaustivement dans la Task dédiée ci-dessus. Le 10e (`LotManagementIT.java`) est le seul à exercer des lots multi-catégories et nécessite une restructuration de scénario, pas un simple déplacement d'argument.
- `UpdateLotDto`/`UpdateLotItemDto` : uniquement dans `LotManagementIT.java` (déjà couvert par sa restructuration) et `LotService.java`.

### Réutilisation

- `EditionScopedLookup.findCategoryInEdition(Long, Edition)` (inchangé) résout déjà la catégorie unique du lot exactement comme il résolvait déjà celle de chaque article — un seul appel désormais dans `create()`/`update()`, contre N avant.
- `TableAssignmentService` reste le seul point d'entrée pour FR-023 — cette story généralise son exclusion (`Long` → `Collection<Long>`) plutôt que de dupliquer sa logique pour le cas "lot entier".

### Testing

- Backend : philosophie E2E par les contrôleurs (CLAUDE.md) — toute la logique de réassignation ci-dessus est vérifiée via `LotManagementIT.java`, pas de test de service isolé.
- Les 9 fichiers listés dans la tâche "correctif mécanique" n'ont **aucune assertion à changer** — seule la forme des DTOs construits change. Si l'un d'eux échoue après la modification, c'est un signal que son scénario dépendait implicitement d'une catégorie par article (à vérifier au cas par cas, ne pas juste ajuster l'assertion pour faire passer le test).
- Prêter une attention particulière au scénario 13 de la restructuration `LotManagementIT.java` (changement de catégorie du lot avec réassignation groupée) — c'est le chemin `categoryChanged` + `excludeItemIds` multi-valeurs, jamais exercé par le code actuel.

### Project Structure Notes

- Aucun nouveau package — toutes les modifications restent dans `org.pluribourse.domain.item.*` (backend) et `pluribourse-frontend/src/app/{models,features/volunteer/deposit}` (frontend), cohérent avec la Story 3.3/3.10 déjà en place.
- `architecture.md` reste obsolète sur les chemins de packages (déjà noté par la Story 2.7) — ne pas s'y fier pour la structure, se fier au code réel déjà cité ci-dessus.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#Point 3 — Catégorie du lot]
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.3 : Création et gestion des lots]
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.10 : Modification d'un lot après saisie]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/TableAssignmentService.java]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java]
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]

## Previous Story Intelligence

Story précédente de l'Epic 3 : **3.13 — Ignorer une imprimante détectée** (`3-13-ignorer-une-imprimante-detectee.md`, `done`). Sans lien fonctionnel avec cette story (impression PrinterBridge vs. modèle de données lot/catégorie) — aucun pattern de code réutilisable directement, mais confirme la convention de test déjà appliquée ici : `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`, données persistées entre méthodes, un scénario métier par classe.

La dernière story ayant touché `LotService`/le modèle de lot est la **3.10 — Modification d'un lot après saisie** (`done`) : c'est elle qui a introduit la logique de réconciliation (`update()` par diff d'ids soumis/existants) que cette story conserve intégralement en tête de méthode, et la règle "un membre de lot n'a jamais de prix propre, donc jamais détaché en article standalone — toujours supprimé" (citée dans le Javadoc de `LotService.update()`), qui reste inchangée ici.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

Aucun blocage rencontré — la story fournissait déjà le code cible exact (migration, entité, DTOs, services, tests) ; l'implémentation a consisté à appliquer ce code prescrit fichier par fichier puis à valider par compilation/tests, sans écart nécessitant investigation.

### Completion Notes List

- Migration Liquibase `033-lot-category.xml` créée et référencée dans `db.changelog-master.xml` : ajout de `lots.category_id`, backfill depuis le premier membre de chaque lot existant (par id croissant), puis contrainte `NOT NULL`. `items.category_id` volontairement laissé inchangé (voir Dev Notes § Décision de conception).
- `Lot.category` (entité), DTOs (`CreateLotDto`/`CreateLotItemDto`/`UpdateLotDto`/`UpdateLotItemDto`/`LotDto`) et `TableAssignmentService` (surcharge `Collection<Long> excludeItemIds`, sentinel `NO_EXCLUSION`) implémentés exactement comme prescrit par la story.
- `LotService.create()`/`update()` réécrits : une seule catégorie par lot, un seul verrou catégorie, une seule table partagée assignée/réassignée par requête (au lieu d'un verrouillage/assignation par article membre). La boucle de verrouillage multi-catégories par ordre croissant d'id a disparu, comme anticipé par les Dev Notes.
- `ItemService.update()` : garde `ItemBelongsToLotException` (422 `item-belongs-to-lot`) ajoutée pour tout article membre d'un lot. `ItemService.delete()` initialement laissé non gardé (hors périmètre, voir Dev Notes), puis gardé pendant la revue de code (voir § Review Findings) : refuse (422 `lot-below-minimum-members`) de supprimer un membre de lot uniquement quand le lot n'a plus que 2 membres, la suppression restant permise au-dessus de ce seuil.
- 9 fichiers de test corrigés mécaniquement (categoryId déplacé de `CreateLotItemDto`/`UpdateLotItemDto` vers `CreateLotDto`/`UpdateLotDto`) — aucune assertion modifiée, conformément à la story.
- `LotManagementIT.java` restructuré en 29 scénarios autour du modèle à catégorie unique (au lieu de catégories multiples par lot ; 23 scénarios prévus au plan initial, portés à 25 en fin de dev-story pour couvrir l'AC 6, puis à 29 pendant la revue de code) : notamment le scénario 6 (2 articles d'un même lot partagent désormais la même table), le scénario 13 (changement de catégorie du lot + ajout d'un article dans la même requête → tous les membres réassignés à une seule nouvelle table partagée, chemin `categoryChanged` + `excludeItemIds` multi-valeurs), le scénario 21 (`PUT /api/items/{id}` sur un membre de lot → 422 `item-belongs-to-lot`) et les scénarios 22-25, ajoutés en revue (22-23 : `DELETE /api/items/{id}` sur un membre de lot, autorisé à 3→2 membres, refusé à 2→1 ; 24 : changement de catégorie du lot avec tous membres remplacés par des nouveaux, régression `TableAssignmentService`/`LotService.update()` ; 25 : `PATCH /api/items/{id}` sur un membre de lot reste fonctionnel).
- Frontend : `categoryId` déplacé du niveau article vers le niveau lot dans `lot.model.ts`, `lot-form.component.ts`/`.html` (contrôle de formulaire au niveau lot, sélecteur catégorie déplacé hors de la boucle par article) et `deposit-page.component.ts` (`startEditLot()` reconstruit désormais `categoryId`/`categoryName` sur le `LotDto` reconstruit localement). Clé i18n `itemCategory` renommée en `lotCategory` (fr/en).
- `lot-form.component.spec.ts` : 4 tests réécrits (payloads, prefill, `fillValidForm()`) + 1 nouveau test ajouté (garde de régression sur `Validators.required` de `categoryId`) ; `deposit-page.component.spec.ts` : `MOCK_LOT` et l'assertion de `startEditLot()` mis à jour avec `categoryId`/`categoryName`.
- Aucun écart supplémentaire découvert par rapport au code prescrit par la story — tous les fichiers, chemins et numéros de ligne cités dans les Tasks/Subtasks correspondaient au code réel.
- Validation finale (dev-story) : `mvn -o test` → 530/530 tests backend verts (0 échec, 0 erreur) ; `npm test` → 671/671 tests frontend verts (670 existants + 1 nouveau) ; `mvn -o clean package` backend réussi ; `ng build` (production) frontend réussi ; grep de vérification confirmant l'absence de toute référence positionnelle résiduelle à l'ancien shape des DTOs de lot.
- Validation finale (revue de code, 2026-08-26) : `mvn -o test` → 534/534 tests backend verts (0 échec, 0 erreur) après application des patchs (garde `ItemService.delete()`, fix `TableAssignmentService`/`LotService.update()` sur collection vide et collection en mémoire désynchronisée, assertions AC 5 sur étiquette/bilan de vente, test `PATCH` sur article de lot).

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/resources/db/changelog/033-lot-category.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/ItemBelongsToLotException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/LotBelowMinimumMembersException.java` (ajouté en revue de code)

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Lot.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/CreateLotItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/CreateLotDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/UpdateLotItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/UpdateLotDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/LotDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/TableAssignmentService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemService.java`

**Backend — tests modifiés**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java` (restructuration complète)

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/lot.model.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`
