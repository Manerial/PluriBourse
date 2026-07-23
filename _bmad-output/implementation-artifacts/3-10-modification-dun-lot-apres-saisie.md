---
baseline_commit: 745854d
---

# Story 3.10: Modification d'un lot après saisie

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole,
I want modifier un lot déjà enregistré (nom, prix global, articles membres) ou le supprimer entièrement,
so that je puisse corriger une erreur de saisie sans devoir supprimer et recréer tout le lot.

## Acceptance Criteria

1. `PUT /lots/{id}` : un lot enregistré en phase Dépôt peut être modifié — nom, prix global, et sa liste d'articles membres (ajout d'un nouvel article, modification du nom/catégorie/indicateur incomplet/commentaire d'un article membre existant). Si la catégorie d'un article membre change, sa table est réassignée selon l'algorithme FR-023 (même règle que `ItemService.update()`, Story 3.2).
2. Le corps de `PUT /lots/{id}` doit contenir **entre 2 et 50 articles** (même contrainte que `POST /lots`, Story 3.3) — tout article membre existant **absent** de la liste soumise est **supprimé définitivement** (pas détaché en article individuel — voir Dev Notes § Retirer un article = suppression). Soumettre une liste avec moins de 2 articles est donc refusé nativement (400, violation de `@Size(min=2)`), ce qui couvre à la fois "retirer un article d'un lot qui n'en a que 2" et tout autre cas sous le minimum.
3. `DELETE /lots/{id}` : supprime le lot **et** tous ses articles membres (aucune suppression en cascade en base — voir Dev Notes § Contrainte FK critique, le service doit supprimer les articles avant le lot).
4. Hors phase Dépôt, `PUT /lots/{id}` et `DELETE /lots/{id}` sont refusés avec 422 `item-modification-locked` (réutilise `ItemModificationNotAllowedException` via `PhaseGuard.requireDepositPhase`, comme toutes les autres mutations d'article/lot).
5. Frontend (`/volunteer/deposit`, phase Dépôt) : chaque lot affiche une action « Modifier le lot » et « Supprimer le lot », visible **une seule fois par lot** (pas une fois par ligne d'article membre — voir Dev Notes § Dédoublonnage par lot). « Modifier le lot » ouvre `LotFormComponent` pré-rempli (nom, prix, articles membres avec leurs catégories/commentaires/indicateur incomplet) ; la soumission met à jour le lot. « Supprimer le lot » passe par une confirmation puis supprime le lot et tous ses articles.

## Tasks / Subtasks

- [x] Backend — DTOs de modification (AC: 1, 2)
  - [x] Nouveau `UpdateLotDto` (record, `org.pluribourse.domain.item.dto`) : `@NotBlank @Size(max = 200) String name`, `@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal globalPrice`, `@NotNull @Valid @Size(min = 2, max = 50, message = "A lot must contain between 2 and 50 items") List<UpdateLotItemDto> items` — mêmes contraintes que `CreateLotDto`.
  - [x] Nouveau `UpdateLotItemDto` (record) : `Long id` (**nullable** — `null` signifie "nouvel article à ajouter au lot", non-null doit référencer un article membre existant de ce lot), `@NotNull Long categoryId`, `@NotBlank @Size(max = 200) String name`, `boolean incomplete`, `@Size(max = 500) String comment`. **Pas de champ prix** — un article de lot n'a jamais de prix individuel (`Item.price` reste `null`, invariant établi en Story 3.3, inchangé par cette story).
  - [x] Nouvelle exception `LotNotFoundException extends BusinessException` (404, code `lot-not-found`), même modèle que `ItemNotFoundException`.
- [x] Backend — `LotService.update()` (AC: 1, 2)
  - [x] `LotService.update(Long lotId, UpdateLotDto dto)`, `@Transactional` : résout le `Lot` (`LotNotFoundException` si absent), `PhaseGuard.requireDepositPhase(lot.getEdition())`, résout **toutes** les `categoryId` référencées via `EditionScopedLookup.findCategoryInEdition` avant toute mutation (même raisonnement fail-fast que `create()`).
  - [x] Charge les membres actuels via `itemRepository.findAllByLotIdOrderById(lot.getId())`. Partitionne `dto.items()` : entrées avec `id` non-null (à mettre à jour — vérifier que l'id appartient bien à ce lot, sinon `ItemNotFoundException` réutilisée, cohérent avec la réutilisation déjà pratiquée par `EditionScopedLookup` pour les cas cross-edition) vs entrées avec `id` null (nouveaux articles). Tout membre actuel dont l'id n'apparaît pas dans les entrées non-null soumises est **supprimé** (`itemRepository.delete(...)`) — voir Dev Notes § Retirer un article = suppression.
  - [x] **Verrouillage conditionnel du vendeur** (`sellerRepository.lockById`) : uniquement s'il y a **au moins un nouvel article** à ajouter (entrées `id == null`), avant toute réassignation de catégorie — voir Dev Notes § Ordre de verrouillage. Aucun verrou si la requête ne fait que renommer/changer le prix du lot ou modifier des articles existants sans ajout, cohérent avec `ItemService.update()` qui ne verrouille jamais le vendeur pour un changement de catégorie seul.
  - [x] Pour les articles membres mis à jour dont la catégorie change : `tableAssignmentService.assignTable(lot.getSellerProfile(), nouvelleCategorie, edition, item.getId())` (avec `excludeItemId`, comme `ItemService.update()`). Pour les nouveaux articles : `tableAssignmentService.assignTable(sellerProfile, categorie, edition)` (sans exclusion, comme `create()`) + `itemNumber` assigné depuis le compteur `SellerProfile.nextItemNumber` verrouillé (même pattern que `create()`, y compris la vérification `TooManyItemsException` si `> Item.MAX_BARCODE_SEGMENT`).
  - [x] **Ordre de verrouillage des catégories** : traiter les réassignations de catégorie (articles mis à jour) et les nouveaux articles **triés par id de catégorie croissant** — même technique que `LotService.create()` (`lockOrder`, tri par `categories.get(i).getId()`) — voir Dev Notes § Ordre de verrouillage.
  - [x] Met à jour `lot.setName(dto.name())`/`lot.setGlobalPrice(dto.globalPrice())`, sauvegarde le lot et chaque article modifié/créé explicitement (`repository.save(...)`, même style que `ItemService.update()` — pas de dirty-checking implicite silencieux). Retourne `LotDto` reconstruit depuis `itemRepository.findAllByLotIdOrderById(lot.getId())` (ordre stable, même requête que la création).
- [x] Backend — `LotService.delete()` (AC: 3, 4)
  - [x] `LotService.delete(Long lotId)`, `@Transactional` : résout le `Lot` (`LotNotFoundException`), `PhaseGuard.requireDepositPhase`. **Supprime d'abord tous les articles membres** (`itemRepository.deleteAll(itemRepository.findAllByLotIdOrderById(lotId))`), **puis** le lot (`repository.delete(lot)`) — voir Dev Notes § Contrainte FK critique, l'ordre inverse lève une violation de contrainte.
- [x] Backend — contrôleur (AC: 1, 3, 4)
  - [x] `LotController` (UPDATE) : `PUT /{id}` → `@Valid @RequestBody UpdateLotDto` → 200 `LotDto`. `DELETE /{id}` → 204. Pas de nouvelle annotation de sécurité — `/lots/**` est déjà accessible aux bénévoles authentifiés (comme `POST /lots` existant), aucune restriction ADMIN.
- [x] Frontend — modèle & service (AC: 1, 3, 5)
  - [x] `models/lot.model.ts` (UPDATE) : ajouter `UpdateLotItemRequest { id: number | null; categoryId: number; name: string; incomplete: boolean; comment: string | null; }` et `UpdateLotRequest { name: string; globalPrice: number; items: UpdateLotItemRequest[]; }`.
  - [x] `services/lot.service.ts` (UPDATE) : ajouter `update(id: number, data: UpdateLotRequest): Observable<LotDto>` (`PUT /api/lots/{id}`), `delete(id: number): Observable<void>` (`DELETE /api/lots/{id}`).
- [x] Frontend — `LotFormComponent` en mode édition (AC: 1, 5)
  - [x] `lot-form.component.ts` (UPDATE) : ajouter `editingLot = input<LotDto | null>(null)` + `isEditing = computed(() => this.editingLot() !== null)`, même pattern que `ItemFormComponent.editingItem`/`isEditing`. `effect()` pré-remplissant `form`(name/globalPrice) et reconstruisant `itemsFormArray` depuis `editingLot()!.items` (un `FormGroup` par article, **avec un champ caché `id`** en plus de `name`/`categoryId`/`incomplete`/`comment` — nécessaire pour distinguer mise à jour vs nouvel article à la soumission). `createItemRow()` doit accepter un `id: number | null` optionnel (défaut `null` pour les nouvelles lignes ajoutées via "+ Ajouter un article").
  - [x] `onSubmit()` : branche `editingLot() ? lotService.update(editingLot()!.id, dto) : lotService.create(dto)` — le `dto` de mise à jour mappe `raw.items` en `UpdateLotItemRequest[]` avec `id: item.id` (peut être `null`). Ne réinitialise le formulaire après succès **que** si `!editingLot()` (création) — cohérent avec `ItemFormComponent.onSubmit()`.
  - [x] `lot-form.component.html` (UPDATE) : titre conditionnel (`createTitle`/`editTitle`, nouvelles clés i18n), libellé du bouton de soumission conditionnel (`submit`/`save`), bouton "Annuler" toujours visible en mode édition (même condition `@if (isEditing())` que `item-form.component.html`).
- [x] Frontend — page dépôt : actions par lot + suppression (AC: 5)
  - [x] `deposit-page.component.ts` (UPDATE) : nouveau signal `editingLot = signal<LotDto | null>(null)`. `startEditLot(lotId)` : reconstruit un `LotDto` **depuis `items()` déjà chargé** (`items().filter(i => i.lotId === lotId)`, `name`/`globalPrice` pris sur `lotName`/`lotPrice` du premier item trouvé) — **pas de nouvel appel `GET`**, toutes les données nécessaires sont déjà dans la liste à plat retournée par `GET /items?sellerProfileId=` (voir Dev Notes § Reconstruction du LotDto côté client). Force aussi `depositMode.set('lot')` (le bénévole peut cliquer "Modifier le lot" alors que le sélecteur est sur "Article individuel"). `confirmDeleteLot(lotId, lotName)` : dialog de confirmation (`ConfirmDialogService`, `confirmVariant: 'error'`, même style que `confirmDelete(item)`) puis `lotService.delete(lotId)`, toast succès/erreur, recharge `items()`.
  - [x] **Réinitialisation croisée de l'état d'édition** (voir Dev Notes § Bug de régression déjà rencontré en Story 3.3) : `setDepositMode()` doit remettre `editingLot` à `null` en plus de `editingItem` ; `startEdit(item)` (édition individuelle) doit aussi remettre `editingLot` à `null` ; l'`effect()` de changement de vendeur doit aussi remettre `editingLot` à `null`. `onLotSaved()` doit remettre `editingLot` à `null` en plus de `depositMode`. Le `cancelled` de `<app-lot-form>` (déjà câblé sur `setDepositMode('individual')`) couvre déjà la remise à `null` une fois le point précédent appliqué.
  - [x] `deposit-page.component.html` (UPDATE) : passer `[editingLot]="editingLot()"` à `<app-lot-form>`. Dans la boucle `@for (item of items(); ...)`, calculer quelle ligne est la **première** rencontrée pour un `lotId` donné (voir Dev Notes § Dédoublonnage par lot) et n'afficher "Modifier le lot"/"Supprimer le lot" que sur cette ligne (remplace l'actuel `@if (!item.lotId) { ... }` qui masque toute action sur les lignes de lot — désormais : `@if (!item.lotId) { <!-- actions article individuel --> } @else if (isFirstLotRow(item)) { <!-- actions lot --> }`).
  - [x] i18n `fr.json`/`en.json` (UPDATE) : `lotForm.editTitle`, `lotForm.save` ; `list.editLot` ("Modifier le lot"), `list.deleteLot` ("Supprimer le lot") ; `deleteLotDialog.title`/`description` (mentionner que **tous les articles du lot** sont supprimés) ; `success.deleteLot` ; `error.deleteLot`. Pas de clé `error.updateLot` séparée : `lotForm.error.save` (déjà existante, généralisée de "créé" à "modifié") couvre l'échec inline du formulaire pour la création **et** la modification, même convention que `item.form.error.save` côté `ItemFormComponent`.
- [x] Tests backend (AC: 1-4)
  - [x] Étendre `LotManagementIT` (`org.pluribourse.domain.item`, ne pas créer une nouvelle classe — même fixture `@BeforeAll` déjà en place : édition, catégories Jouets/Livres avec tables partagées, `createdLotId` déjà capturé par les tests de création existants) avec de nouveaux `@Order` après les tests existants : mise à jour nom/prix (200, `GET /items` reflète `lotName`/`lotPrice` sur toutes les lignes membres) ; ajout d'un article au lot (nouveau `tableNumber` assigné, `itemNumber` continue le compteur du vendeur sans collision avec les articles déjà créés) ; changement de catégorie d'un article membre (table réassignée, vérifier via `excludeItemId` qu'elle ne compte pas son ancien état) ; retrait d'un article d'un lot à 3 membres → 200, `GET /items` ne renvoie plus cet id ; soumission à 1 seul article → 400 (violation `@Size(min=2)`) ; id d'article n'appartenant pas à ce lot → 404 `item-not-found` ; lot inconnu → 404 `lot-not-found` ; hors phase Dépôt → 422 `item-modification-locked` (avancer l'édition de test en phase Vente comme fait ailleurs, ou réutiliser une édition dédiée fermée) ; suppression du lot → 204, `GET /items?sellerProfileId=` ne renvoie plus aucun des anciens membres ; suppression hors phase Dépôt → 422.
- [x] Tests frontend
  - [x] `lot-form.component.spec.ts` (UPDATE) : pré-remplissage du formulaire en mode édition (`editingLot` avec ses items), soumission appelle `lotService.update()` avec les bons `id` par ligne (existants conservés, `null` pour une ligne ajoutée en cours d'édition), titre/libellés conditionnels, bouton Annuler visible en édition.
  - [x] `deposit-page.component.spec.ts` (UPDATE) : les actions "Modifier le lot"/"Supprimer le lot" n'apparaissent qu'une fois par lot (pas répétées sur chaque ligne membre) ; clic sur "Modifier le lot" force `depositMode` sur `'lot'` et pré-remplit `editingLot` depuis les items déjà chargés (aucun nouvel appel HTTP) ; suppression du lot (confirmation → succès → toast → rechargement de la liste) ; remise à `null` de `editingLot` lors d'un changement de vendeur, d'un changement de mode, ou du démarrage d'une édition d'article individuel.

### Review Findings

- [x] [Review][Decision] Duplicate submitted item `id` in `UpdateLotDto.items()` is not rejected — **Résolu avec l'utilisateur : option 1, ajouter une validation explicite.** Nouvelle `DuplicateLotItemException` (422, code `duplicate-lot-item-id`), vérification ajoutée dans la boucle de partition de `LotService.update()` (un `Set<Long>` détecte le doublon avant toute mutation), nouveau test `update_lot_with_duplicate_item_id_is_rejected` (`@Order(19)`). [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java]
- [x] [Review][Patch] `confirmDeleteLot()` shows the individual-article phase-locked message instead of a lot-scoped one — **Corrigé** : utilise désormais `volunteer.deposit.item.lotForm.error.phaseLocked`. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts:174]
- [x] [Review][Patch] `lotForm.error.phaseLocked` wording was generalized from "ne peut pas être créé" to "ne peut pas être modifié", but this key is read from the single shared catch branch in `LotFormComponent.onSubmit()` used by **both** `create()` and `update()` — a phase-lock failure while creating a brand-new lot now shows an incorrect "modifié" message. — **Corrigé** : libellé rendu phase-agnostique ("ne peut pas être créé ou modifié en dehors de la phase Dépôt") en fr/en. [pluribourse-frontend/public/i18n/fr.json, en.json — `item.lotForm.error.phaseLocked`]
- [x] [Review][Patch] Dead code: `cancelEditLot()` added to `DepositPageComponent` but never called from anywhere — **Corrigé** : méthode supprimée. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts:147]
- [x] [Review][Patch] `LotManagementIT.update_lot_change_item_category_reassigns_table` (`@Order(12)`) doesn't test what it claims — it resubmits "Playmobil" with `id: null` instead of its captured id. — **Corrigé** : `itemPlaymobilId` capturé à `@Order(11)` et réutilisé à `@Order(13)` (renumérotée), assertion ajoutée que l'id de Playmobil est préservé (pas supprimé/recréé). [pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]
- [x] [Review][Patch] No test combines an existing-member category reassignment with a new item in the same `PUT` request — **Corrigé** : nouveau test `update_lot_reassigns_category_and_adds_item_in_the_same_request` (`@Order(14)`). [pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]
- [x] [Review][Patch] `update_lot_with_item_id_not_belonging_to_lot_returns_404` only proves a nonexistent id 404s, not that a real id belonging to a *different* lot is rejected — **Corrigé** : `create_second_lot_for_cross_lot_and_deletion_tests` (`@Order(11)`, déplacée plus tôt) capture `secondLotItemAId`, réutilisé dans une seconde assertion 404 de `@Order(15)`. [pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]
- [x] [Review][Patch] No `DELETE /lots/{unknown-id}` → 404 `lot-not-found` test exists — **Corrigé** : nouveau test `delete_unknown_lot_returns_404` (`@Order(20)`). [pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]
- [x] [Review][Patch] No DOM-level click test exercises the new `(click)="startEditLot(item.lotId)"` / `(click)="confirmDeleteLot(item.lotId, item.lotName!)"` template bindings — **Corrigé** : nouveau test avec clics réels sur les boutons rendus, vérifiant les arguments (`item.lotId`/`item.lotName`, pas `item.id`) via spy. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts]
- [x] [Review][Patch] Missing JavaDoc on the new complex `LotService.update()`/`delete()` methods — **Corrigé** : JavaDoc ajoutée sur les deux méthodes. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java]
- [x] [Review][Defer] Wildcard imports (`import jakarta.validation.*;` etc.) in `UpdateLotDto`/`UpdateLotItemDto` vs. explicit imports in other new files of the same diff — deferred, pure style nit, harmless. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/UpdateLotDto.java]
- [x] [Review][Defer] Backend `noChangeIndexes`/`lockOrder` update loops (both set name/incomplete/comment) and frontend create/update DTO-mapping blocks in `onSubmit()` each duplicate a few lines instead of a shared helper — deferred, minor DRY nit, not a correctness issue. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java, pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts]
- [x] [Review][Defer] `resetForm()` helper removed and its 2-line body duplicated in two call sites (prefill effect's "no lot" branch, post-create success in `onSubmit()`) instead of re-extracted — deferred, minor DRY nit. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts]
- [x] [Review][Defer] Currency rounding (`Math.round(x * 100) / 100`) duplicated between the create and update branches of `onSubmit()` instead of extracted once — deferred, pre-existing pattern from the create branch, now also present in the update branch. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts]
- [x] [Review][Defer] New prefill `effect()` reads `this.sellerId()` purely for dependency-tracking, defensively, with no test covering the scenario it guards against — deferred, mirrors an already-unqualified pattern in `ItemFormComponent`, low risk. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts]
- [x] [Review][Defer] No test for the 51-items upper bound or name/comment length limits on the update path — deferred, symmetric gap already present (and never tested) on the pre-existing `create()` path, not a regression. [pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java]
- [x] [Review][Defer] `TooManyItemsException` unreachable/untested via the update path — deferred, same pre-existing, project-wide untested state as `create()`. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java]
- [x] [Review][Defer] Concurrent `PUT`/`DELETE` on the same lot could hit an unhandled `ObjectOptimisticLockingFailureException` (raw 500) — deferred, systemic gap already identified and deferred for `Item` (Story 3.2) and `Printer` (Story 3.8), `Lot` now shares the same `@Version` pattern with no dedicated handler in `GlobalExceptionHandler`. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java]
- [x] [Review][Defer] `startEdit()` doesn't reset `depositMode` back to `'individual'`, so clicking "Modifier l'article" while in Lot mode silently appears to do nothing (item-form isn't rendered) — deferred, pre-existing gap predating this story; this diff only added the `editingLot` reset to the same method. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts]
- [x] [Review][Defer] `startEditLot()` returns silently with no toast if the lot's items are no longer in the already-loaded list (e.g. deleted concurrently in another session) — deferred, extremely rare race, no AC requires handling it. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts]
- [x] [Review][Defer] `confirmDeleteLot()`'s catch branch doesn't reload the item list or clear `editingLot` on a non-422 error (e.g. 404 if the lot was already deleted elsewhere) — deferred, mirrors the exact same pre-existing pattern already used by `confirmDelete()` for individual items. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts]
- [x] [Review][Defer] No double-click/in-flight guard on the new lot delete button (a rapid double-click could fire two `DELETE`s, the second 404ing and showing a spurious error after a successful deletion) — deferred, mirrors the same pre-existing gap on the individual item's delete button (`confirmDelete()`). [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html]
- [x] [Review][Defer] `LotFormComponent.onSubmit()`'s 404 case (lot or item no longer exists, e.g. concurrent deletion) falls into the generic error branch — form stays open referencing a stale entity, parent list isn't refreshed. Deferred, rare concurrent-deletion race, no AC requires handling it. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts]

### Re-review (2026-07-22)

- [x] [Review][Patch] `confirmDeleteLot()`'s 422 phase-locked toast shows the create/update wording ("créé ou modifié"), never mentioning deletion — **Corrigé** : nouvelle clé dédiée `item.error.deleteLotPhaseLocked` ("Ce lot ne peut pas être supprimé en dehors de la phase Dépôt.") en fr/en, utilisée à la place de `lotForm.error.phaseLocked` dans `confirmDeleteLot()`. Test mis à jour. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts, pluribourse-frontend/public/i18n/fr.json, en.json]
- [x] [Review][Defer] No row-level locking on `Lot`/`Item` during `update()`/`delete()` — concurrent mutations (two `PUT`s, or a `PUT` racing a `DELETE`) can race in `assignTable()`'s table-occupancy computation, since only the seller row is locked and only conditionally. Consistent with the already-deferred systemic `@Version` conflict gap in this same story, and with `ItemService.update()`'s pre-existing no-lock-for-category-only-change pattern cited by the Dev Notes as precedent. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java] — deferred, pre-existing pattern
- [x] [Review][Defer] `PhaseGuard` check can be stale by transaction commit time (TOCTOU on edition phase) — checked once at method entry; same pattern as every other `PhaseGuard.requireDepositPhase()` call site in the codebase. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java] — deferred, pre-existing pattern
- [x] [Review][Defer] Inconsistent deletion pattern (`itemRepository.delete()` per item in `update()`'s absent-member loop vs `deleteAll()` in `delete()`) and redundant explicit `.save()` calls on already-tracked entities in the `noChangeIndexes` loop. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java] — deferred, minor DRY/efficiency nit, same caliber as prior deferrals in this story
- [x] [Review][Defer] Lot edit/delete buttons aren't gated behind `isDepositPhase()` in the template — sits in the same unguarded `article-row__actions` block as the pre-existing individual-item edit/delete buttons. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html] — deferred, pre-existing UX gap extended consistently, not a regression
- [x] [Review][Defer] Non-null assertions (`item.lotName!`, `first.lotName!`/`first.lotPrice!`) on independently-nullable `ItemDto.lotName`/`lotPrice` fields — the "always set together when `lotId` is set" invariant is backend convention only, not type-enforced. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html, deposit-page.component.ts] — deferred, not reachable in practice, low severity
- [x] [Review][Defer] Internal DB ids embedded in error `detail` text (`"Lot not found: " + id`, `"Item id " + id + " appears more than once..."`). [pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/LotNotFoundException.java, DuplicateLotItemException.java] — deferred, consistent with existing `ItemNotFoundException`-style convention project-wide
- [x] [Review][Defer] `confirmDeleteLot()` captures `selectedSeller()` before awaiting the delete call; a seller switch mid-flight reloads the wrong seller's items on success. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts] — deferred, rare race, no AC requires handling it

## Dev Notes

### Point de départ — ce qui existe déjà (ne pas réinventer)

Cette story **étend** le module `Lot` livré par la Story 3.3, elle ne le recrée pas :

- `LotService.create()`, `LotController` (`POST /lots`), `Lot`/`Item` entités, `LotRepository`/`ItemRepository` (`findAllByLotIdOrderById` déjà présente et directement réutilisable) : **inchangés**, servent de modèle direct pour `update()`/`delete()`.
- `ItemService.update()` (`org.pluribourse.domain.item.service`) : modèle direct pour la logique de réassignation de table d'un article membre dont la catégorie change (`tableAssignmentService.assignTable(..., excludeItemId)`).
- `PhaseGuard.requireDepositPhase(Edition)` : réutilisée telle quelle, ne pas dupliquer.
- `EditionScopedLookup.findCategoryInEdition` : réutilisée telle quelle pour valider les `categoryId` soumis.
- Frontend : `ItemFormComponent` (`editingItem`/`isEditing`/effect de pré-remplissage) est le modèle direct pour ajouter le mode édition à `LotFormComponent` — même structure, même style de gestion `loading`/`error`.

### Retirer un article = suppression, jamais un détachement

Le formulaire d'édition du lot (`lot-form.component.html`) n'a **aucun champ prix individuel** — un article de lot a toujours `Item.price = null` (invariant établi en Story 3.3). Si un article est retiré de la liste soumise à `PUT /lots/{id}`, il n'existe **aucune donnée** (pas de prix) permettant de le transformer en article individuel valide. La seule interprétation cohérente : un article absent de la liste soumise est **supprimé définitivement** (`itemRepository.delete(...)`), exactement comme "Supprimer l'article" pour un article individuel (Story 3.2, FR-024) — pas une opération de "détachement du lot". Ne pas introduire de DTO/UI pour détacher-avec-prix : aucun AC ne le demande, ce serait un fait accompli non sollicité.

### Contrainte FK critique — `fk_items_lot` n'a pas de suppression en cascade

`015-lots.xml` (migration Liquibase de la Story 3.3) définit `fk_items_lot` **sans** `deleteCascade="true"` (contrairement à `fk_lots_edition`/`fk_lots_seller_profile`, qui l'ont). Appeler `repository.delete(lot)` alors que des `Item` référencent encore ce `lot_id` lève une violation de contrainte d'intégrité (exception non gérée, 500). `LotService.delete()` **doit** supprimer tous les articles membres **avant** de supprimer le lot — l'ordre inverse casse en production, pas seulement en test.

### Ordre de verrouillage — pourquoi le verrou vendeur est conditionnel dans `update()`

`LotService.create()` verrouille systématiquement le vendeur (`sellerRepository.lockById`) **avant** toute assignation de table — nécessaire car chaque article créé consomme le compteur `SellerProfile.nextItemNumber`, qui doit être lu/incrémenté sous verrou pour éviter que deux créations concurrentes n'attribuent le même numéro (FR-026). Ce verrou n'a **aucune utilité** en dehors de la protection de ce compteur : `ItemService.update()` (réassignation de catégorie d'un article individuel, sans nouveau numéro à attribuer) ne verrouille d'ailleurs jamais le vendeur, uniquement la catégorie via `assignTable()`.

`LotService.update()` suit ce même principe : verrouiller le vendeur **seulement si la requête ajoute au moins un nouvel article au lot** (donc consomme `nextItemNumber`) — jamais pour un simple renommage/changement de prix ou une modification d'articles existants sans ajout. Verrouiller par précaution dans tous les cas ajouterait une contention inutile (ex. un bénévole qui renomme un lot ferait attendre un autre bénévole qui dépose un article pour le même vendeur, alors qu'aucune donnée commune n'est en jeu).

Dans le cas où un nouvel article est bien ajouté, trier les réassignations de catégorie (articles modifiés + nouveaux articles) par id de catégorie croissant avant de les traiter, comme le fait déjà `create()` (`lockOrder`) — ceci reste nécessaire indépendamment du verrou vendeur, pour éviter un deadlock ABBA entre deux `update()`/`create()` concurrents qui verrouilleraient des catégories communes dans un ordre différent.

### Dédoublonnage par lot — affichage frontend

La liste « Articles déposés » affiche toujours **une ligne par article**, y compris pour les membres d'un lot (décision de scope actée en Story 3.3, non remise en cause par cette story — pas d'agrégation visuelle). Les actions "Modifier le lot"/"Supprimer le lot" ne doivent apparaître **qu'une seule fois** par lot (sur la première ligne membre rencontrée dans l'ordre d'itération), pas répétées sur chaque ligne — sinon l'interface donne l'impression de N actions indépendantes alors qu'elles opèrent toutes sur le même lot. Technique recommandée : un `computed()` (ou une méthode appelée depuis le template) qui parcourt `items()` une fois et construit un `Set<number>` des ids d'articles "première occurrence de leur lot" (même technique de déduplication par id que `DepositSlipRenderer`/`ThermalLabelRenderer` côté backend, Story 3.5/3.6, `LinkedHashSet` des ids de lot déjà vus).

### Reconstruction du `LotDto` côté client — pas de nouvel appel réseau

`GET /items?sellerProfileId=` (déjà utilisé pour charger `items()`) renvoie une liste à plat où chaque `ItemDto` membre d'un lot porte déjà `lotId`/`lotName`/`lotPrice` (Story 3.3). Pour ouvrir `LotFormComponent` en mode édition, il n'est **pas nécessaire** d'ajouter un `GET /lots/{id}` : `items().filter(i => i.lotId === lotId)` donne exactement la liste des membres actuels avec tout ce qu'il faut (id, name, categoryId, incomplete, comment par article ; lotName/lotPrice identiques sur toutes les lignes filtrées, à prendre sur la première). Ne pas ajouter d'endpoint `GET` supplémentaire pour ce seul besoin — inutile, la donnée est déjà en mémoire côté client.

### Bug de régression déjà rencontré en Story 3.3 — ne pas le reproduire pour `editingLot`

Un des findings de revue de la Story 3.3 était exactement cette classe de bug : `setDepositMode()` ne remettait pas `editingItem()` à `null` lors du changement de mode, laissant `ItemFormComponent` pré-rempli avec une édition abandonnée après un aller-retour de mode. Cette story introduit un second signal d'état d'édition (`editingLot`) exposé au même niveau — **appliquer la même discipline dès l'écriture initiale** plutôt que de la découvrir en revue une seconde fois : tout point qui remet `editingItem` à `null` (`setDepositMode()`, l'`effect()` de changement de vendeur, `startEdit()`) doit aussi remettre `editingLot` à `null`, et réciproquement `startEditLot()` doit remettre `editingItem` à `null`.

### Project Structure Notes

- Toutes les nouvelles classes backend dans `org.pluribourse.domain.item.{dto,exception,service,controller}` — même module que Stories 3.2/3.3, aucune nouvelle arborescence.
- Aucune migration Liquibase : `Lot`/`Item` existent déjà avec tous les champs nécessaires (Story 3.3).
- Frontend : aucun nouveau fichier de composant — extension de `lot-form.component.ts/.html`, `deposit-page.component.ts/.html`, `lot.model.ts`, `lot.service.ts` existants.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java` — ajouter `update()`/`delete()` à côté de `create()` existant, réutiliser ses champs injectés (`repository`, `itemRepository`, `editionScopedLookup`, `editionService`, `tableAssignmentService`, `sellerRepository`, `itemMapper`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/controller/LotController.java` — ajouter `PUT`/`DELETE`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` — `findAllByLotIdOrderById` déjà présente, ne pas la redéfinir
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts`/`.html` — ajouter le mode édition
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`/`.html` — actions par lot, gestion de `editingLot`
- `pluribourse-frontend/src/app/models/lot.model.ts`, `services/lot.service.ts` — ajouter update/delete
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java` — étendre, ne pas dupliquer la fixture `@BeforeAll`

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.10 (ajoutée le 2026-07-21, après Story 3.9)]
- [Source: _bmad-output/implementation-artifacts/3-3-creation-et-gestion-des-lots.md] — Dev Notes § "Modification ou suppression d'un lot déjà sauvegardé" (scope explicitement différé vers cette story), schéma de migration `015-lots.xml`, pattern `LotService.create()`
- [Source: _bmad-output/implementation-artifacts/3-2-enregistrement-darticles-assignation-automatique-de-table.md] — AC5/6 (modification/suppression d'un article individuel), modèle direct pour la logique de réassignation de table
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemService.java] — `update()`/`delete()` existants, modèle direct
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/TableAssignmentService.java] — signature `assignTable(sellerProfile, category, edition, excludeItemId)`, JavaDoc sur le rôle de `excludeItemId`
- [Source: pluribourse-backend/src/main/resources/db/changelog/015-lots.xml] — absence de `deleteCascade` sur `fk_items_lot`, contrainte critique pour `delete()`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java] — `findAllByLotIdOrderById`, `findTableNumberBySellerProfileIdAndCategoryId`, `countByTableNumber` déjà disponibles
- [Source: pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts] — pattern `editingItem`/`isEditing`/effect de pré-remplissage, modèle direct pour `LotFormComponent`
- [Source: pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts] — Review Finding Story 3.3 sur `setDepositMode()` ne réinitialisant pas `editingItem()`, à ne pas reproduire pour `editingLot`
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java] — fixture existante (édition, catégories Jouets/Livres tables partagées) à étendre

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `mvnw.cmd -q test -Dtest=LotManagementIT` → 22/22 passed (initial implementation)
- `mvnw.cmd test` (full backend suite) → 286/286 passed, BUILD SUCCESS, no regressions
- `npm test -- --include "**/lot-form.component.spec.ts" --watch=false` → 14/14 passed
- `npm test -- --include "**/deposit-page.component.spec.ts" --watch=false` → 41/41 passed
- `npm test -- --watch=false` (full frontend suite, Vitest) → 432/432 passed, 49/49 test files, no regressions
- `ng build` → succeeds (validates Angular template type-checking for the new `@else if (isFirstLotRow(item))` branch)
- **Post-review**: `mvnw.cmd -q test -Dtest=LotManagementIT` → 25/25 passed
- **Post-review**: `mvnw.cmd test` (full backend suite) → 289/289 passed, BUILD SUCCESS, no regressions
- **Post-review**: `npm test -- --include "**/deposit-page.component.spec.ts" --watch=false` → 43/43 passed
- **Post-review**: `npm test -- --watch=false` (full frontend suite, Vitest) → 433/433 passed, no regressions
- **Post-review**: `ng build` → succeeds

### Completion Notes List

- `LotService.update()`/`delete()` implemented exactly per Dev Notes: category resolution fail-fast before any mutation, absent members deleted (never detached — no price data exists to make that valid), category reassignment via `assignTable(..., excludeItemId)`, new items via `assignTable(...)` + locked `nextItemNumber` counter with `TooManyItemsException` guard, lots deleted by removing member items first (`fk_items_lot` has no delete cascade).
- Seller lock in `update()` implemented as **conditional** (only when at least one new item is added), not unconditional — this was the one design point flagged and corrected during story validation (see Dev Notes § Ordre de verrouillage): mirrors `ItemService.update()`, which never locks the seller for a category-only change, avoiding contention on trivial lot rename/price edits.
- Category-lock ordering refined beyond the story's literal wording: only member updates whose category actually changes are included in the ascending-category-id lock order together with new items; members updated without a category change are processed separately with no lock at all (minimal lock scope, same correctness guarantee).
- `LotController`: `PUT /{id}` → 200 `LotDto`, `DELETE /{id}` → 204, no new security annotation (`/lots/**` already open to any authenticated volunteer, verified against `SecurityConfig`).
- Frontend: `LotFormComponent` gained `editingLot`/`isEditing` (mirrors `ItemFormComponent`), a hidden `id` control per item row (`LotItemRow` shape), and `setItemRows()`/`emptyItemRow()` helpers shared between the edit-prefill effect and the post-create reset (avoids duplicating the array-rebuild logic). `onSubmit()` branches to `lotService.update()`/`create()` based on `editingLot()`.
- `DepositPageComponent`: `editingLot` signal, `firstLotItemIds`/`isFirstLotRow()` computed (dedup by lot, `LinkedHashSet`-equivalent technique), `startEditLot()` (rebuilds `LotDto` from already-loaded `items()`, no extra `GET`), `confirmDeleteLot()`. Cross-reset discipline applied everywhere `editingItem` is reset (`setDepositMode()`, seller-change effect, `startEdit()`) and vice versa (`startEditLot()` resets `editingItem`) — the Story 3.3 regression class this Dev Notes section warned about was not reproduced.
- i18n: no separate `error.updateLot` key — `lotForm.error.save` (existing key, message generalized from "cannot be **created**" to "cannot be **modified**" since it is now shared between create and update failures) covers both, same convention as `item.form.error.save`.
- `LotManagementIT` extended (not duplicated) with 12 new `@Order` tests (10-21, renumbering the two pre-existing phase-lock tests to 19 and 22): rename/price propagation, add item (seller's existing category table reused per FR-023, not a fresh least-loaded computation), category reassignment, member removal, `<2 items` rejection (400), foreign item id (404 `item-not-found`), unknown lot (404 `lot-not-found`), a dedicated second lot created and deleted successfully in-phase (204) so the original lot survives intact for the two out-of-phase tests (422 on both `PUT` and `DELETE`) — `itemNumber` counter continuity was not asserted directly since `ItemDto` never exposes it (consistent with existing creation tests, which only assert `tableNumber`/`price`).
- Frontend tests: `lot-form.component.spec.ts` covers prefill, update-payload id mapping (existing ids preserved, `null` for a newly added row), no-reset-after-update, and switching back to create mode. `deposit-page.component.spec.ts` covers per-lot action dedup (DOM), `isFirstLotRow()`, `startEditLot()` (no extra HTTP call), cross-reset of `editingItem`/`editingLot`, and `confirmDeleteLot()` (cancel, success, phase-locked, generic error).

### Code Review — Patches Applied (2026-07-22)

- Duplicate submitted item `id` in `UpdateLotDto.items()` now rejected: new `DuplicateLotItemException` (422, `duplicate-lot-item-id`), checked in `LotService.update()`'s partition loop before any mutation.
- `confirmDeleteLot()` now shows the lot-scoped phase-locked message (`lotForm.error.phaseLocked`) instead of the individual-article one.
- `lotForm.error.phaseLocked` wording made phase-agnostic ("créé ou modifié") since it's shared by the create and update failure paths.
- Removed dead `cancelEditLot()` method (never called).
- `LotManagementIT` restructured: `create_second_lot_for_cross_lot_and_deletion_tests` moved earlier (`@Order 11`) to capture `secondLotItemAId` for a proper "item belongs to a different lot" 404 case; Playmobil's real id is now captured and reused (was incorrectly resubmitted as `id: null`, silently deleting and recreating it); new combined test reassigning a category and adding an item in the same request; new `delete_unknown_lot_returns_404`; new `update_lot_with_duplicate_item_id_is_rejected`. 25 tests total (was 22), fully renumbered `@Order(11)`-`@Order(25)`.
- Added a DOM-level click test in `deposit-page.component.spec.ts` asserting the rendered buttons call `startEditLot`/`confirmDeleteLot` with `item.lotId`/`item.lotName`, not `item.id`.
- Added JavaDoc to `LotService.update()`/`delete()`.
- 13 lower-priority findings deferred (pre-existing patterns, rare races, minor DRY nits) — see `### Review Findings` above and `deferred-work.md`.

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/UpdateLotDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/dto/UpdateLotItemDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/LotNotFoundException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/DuplicateLotItemException.java` (nouveau — ajouté en revue)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/LotService.java` (modifié — `update()`/`delete()`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/controller/LotController.java` (modifié — `PUT`/`DELETE`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java` (modifié — nouveaux tests, capture des ids d'articles, renumérotation)
- `pluribourse-frontend/src/app/models/lot.model.ts` (modifié — `UpdateLotItemRequest`, `UpdateLotRequest`)
- `pluribourse-frontend/src/app/services/lot.service.ts` (modifié — `update()`, `delete()`)
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts` (modifié — mode édition)
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.html` (modifié — titre/libellé conditionnels)
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.spec.ts` (modifié — tests mode édition)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (modifié — `editingLot`, actions par lot, suppression)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html` (modifié — actions par lot dédupliquées)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts` (modifié — tests actions/suppression de lot)
- `pluribourse-frontend/public/i18n/fr.json` (modifié — clés `lotForm.editTitle`/`.save`, `list.editLot`/`.deleteLot`, `deleteLotDialog.*`, `success.deleteLot`, `error.deleteLot`)
- `pluribourse-frontend/public/i18n/en.json` (idem)

## Change Log

- 2026-07-22 : Implémentation complète de la Story 3.10 (modification/suppression d'un lot après saisie, `PUT`/`DELETE /lots/{id}`). 286/286 tests backend et 432/432 tests frontend passent, aucune régression.
- 2026-07-22 : Revue de code (Blind Hunter, Edge Case Hunter, Acceptance Auditor). 1 décision tranchée avec l'utilisateur (validation des id dupliqués ajoutée), 9 patches appliqués (2 bugs de wording i18n, code mort supprimé, bug de test masqué corrigé, 4 tests manquants ajoutés, JavaDoc ajoutée), 13 items différés (patterns pré-existants, races rares, nits DRY mineurs), 1 écarté comme bruit. 289/289 tests backend et 433/433 tests frontend passent après application des patches, aucune régression. Statut → `done`.
- 2026-07-22 : Re-revue de code sur les changements non committés (Blind Hunter, Edge Case Hunter, Acceptance Auditor). 1 patch appliqué (toast de suppression hors-phase affichait le message "créé ou modifié" au lieu de mentionner la suppression — nouvelle clé i18n dédiée `error.deleteLotPhaseLocked`), 7 items différés (absence de verrouillage de ligne Lot/Item, TOCTOU PhaseGuard, nits DRY/suppression, boutons lot non gated par phase, assertions non-null sur champs nullables, ids internes dans les messages d'erreur, capture de vendeur périmée dans `confirmDeleteLot()`), 9 findings écartés comme bruit (déjà décidés/différés dans la revue précédente, ou cohérents avec des conventions du projet déjà établies). 42/42 tests frontend `deposit-page.component.spec.ts` passent après application du patch. Statut inchangé → `done`.
