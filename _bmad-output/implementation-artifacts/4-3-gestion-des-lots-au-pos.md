---
baseline_commit: 0796e8718f132eb9a5cefa2eff64ab079e7c45b4
---

# Story 4.3: Gestion des lots au POS

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole caissier,
I want être informé lorsqu'un lot est incomplet lors du scan,
so that je peux décider de le vendre en l'état ou de le retirer du panier.

## Acceptance Criteria

1. **Regroupement visuel des articles de lot (FR-046).** Dès qu'un article scanné appartient à un lot, il n'apparaît plus comme une ligne ordinaire du panier (comportement Story 4.2, désormais remplacé) : il rejoint un groupe visuel distinct par lot, affichant le nom du lot en évidence, un compteur « X/N scannés » et le sous-total du groupe. **Aucun prix individuel n'est affiché** pour un article appartenant à un lot, y compris à l'intérieur de ce groupe (cohérent avec `Item.price == null` pour tout article de lot — Story 3.3/4.2).
2. **Avertissement lot incomplet (FR-047).** Tant que X < N (compteur du groupe), une notification inline avertissement indique le nombre d'articles manquants dans le lot. Le bouton « Valider » reste actif — la vente d'un lot incomplet reste autorisée, aucun blocage.
3. **Lot complet (FR-048).** Une fois X == N, le groupe passe dans un état visuel « complet » distinct (icône succès plutôt qu'avertissement). Le calcul du prix (lot vendu à son prix global, une seule fois) est **déjà correctement implémenté depuis la Story 4.2** (`ItemPricing.computeTotal`) qu'il soit complet ou non — cette story n'a rien à changer côté calcul de vente ou de validation du paiement.
4. **Retirer le lot entier (FR-081).** Un bouton dédié sur le groupe retire en un seul appel serveur tous les articles de ce lot actuellement présents dans le panier (pas un retrait article par article côté client) — la vente peut continuer avec le reste du panier.
5. **Représentation sur la facture (FR-041) — déjà couvert par le modèle de données, aucune action dans cette story.** `Item.lot` et `Item.sale` (Story 4.2) portent déjà tout ce dont la Story 4.5 (génération de la facture PDF) aura besoin pour regrouper un lot sur une seule ligne. Cette story ne touche à aucun code d'impression ou de facturation.

## Tasks / Subtasks

- [x] Backend — DTOs (AC: 1, 2, 3)
  - [x] `org.pluribourse.domain.pos.dto.ScanResultDto` (UPDATE) : ajouter un champ `Long lotId` (nullable, `null` pour un article hors-lot) — c'est exactement le champ que la Story 4.2 avait explicitement laissé de côté pour cette story (« `ScanResultDto` n'a pas de champ `lotId`/`lotName` », Dev Notes 4.2 § Prix des lots).
  - [x] `org.pluribourse.domain.pos.mapper.ScanResultMapper` (UPDATE) : ajouter `@Mapping(target = "lotId", source = "lot.id")` sur `toDto` — MapStruct gère nativement la navigation imbriquée null-safe (`item.getLot()` peut être `null`).
  - [x] `org.pluribourse.domain.pos.dto.LotGroupDto` (nouveau, record) : `(Long lotId, String lotName, BigDecimal globalPrice, int scannedCount, int totalCount)`. `scannedCount` = nombre d'articles de ce lot présents dans **ce panier** ; `totalCount` = nombre total de membres du lot (`Lot.items.size()`), qu'ils soient scannés ou non.
  - [x] `org.pluribourse.domain.pos.dto.BasketDto` (UPDATE) : ajouter `List<LotGroupDto> lotGroups` — un groupe par lot **distinct** présent dans le panier, dans l'ordre de première apparition (cohérent avec `ItemPricing.distinctByLot`, voir ci-dessous). **Ne pas** retirer les articles de lot de `items` : `items` reste la liste complète (le frontend filtre par `item.lotId` pour décider où afficher chaque ligne, voir tâche frontend) — pas de rupture de contrat pour `GET /pos/scan` (Story 4.1) qui réutilise le même `ScanResultDto`.
- [x] Backend — exception (AC: 4)
  - [x] `org.pluribourse.domain.pos.exception.BasketLotNotFoundException` (nouveau, `extends BusinessException`) : 404, code `basket-lot-not-found`. Constructeur `(Long basketId, Long lotId)`, message informatif (même style que `BasketItemNotFoundException`). Levée quand `removeLot` ne trouve **aucun** article du lot demandé dans ce panier (lot déjà entièrement retiré, ou jamais scanné). **Aucun changement à `GlobalExceptionHandler`** — contrairement à `BasketValidationConflictException` (Story 4.2), cette exception ne porte pas de charge utile structurée ; le handler générique `BusinessException` suffit.
- [x] Backend — repository (AC: 4)
  - [x] `org.pluribourse.domain.pos.repository.BasketItemRepository` (UPDATE) : ajouter
    ```java
    @Query("SELECT bi FROM BasketItem bi JOIN bi.item i WHERE bi.basket.id = :basketId AND i.lot.id = :lotId")
    List<BasketItem> findAllByBasketIdAndItemLotId(@Param("basketId") Long basketId, @Param("lotId") Long lotId);
    ```
    Requête JPQL explicite (pas de nom de méthode dérivé) — cohérent avec `findAllByBasketIdOrderById` déjà présent dans ce repository, plutôt qu'un nom dérivé `findAllByBasketIdAndItemLotId` dont la résolution multi-niveaux (`item.lot.id`) serait moins lisible.
- [x] Backend — service (AC: 1, 2, 3, 4)
  - [x] `org.pluribourse.domain.pos.service.PosBasketService` (UPDATE) :
    - `toDto(Basket basket)` : après avoir construit `itemDtos`, construire aussi `lotGroups` via une nouvelle méthode privée `buildLotGroups(List<Item> items)`, et l'inclure dans le nouveau `BasketDto(basket.getId(), itemDtos, buildLotGroups(items), ItemPricing.computeTotal(items))`.
    - Nouvelle méthode privée `List<LotGroupDto> buildLotGroups(List<Item> items)` : itère `ItemPricing.distinctByLot(items)` (méthode **déjà existante**, `org.pluribourse.domain.item.service.ItemPricing` — réutiliser, ne pas redupliquer la logique « un représentant par lot distinct ») ; ignore les représentants sans lot (`item.getLot() == null`) ; pour chaque lot, compte `scannedCount` = nombre d'éléments de `items` (la liste complète, pas la liste dédupliquée) dont `getLot().getId()` correspond, et lit `totalCount` = `lot.getItems().size()`. **Note de performance assumée** : `Lot.items` est chargé en `EAGER` (`Lot.java:39`, décision déjà en place depuis la Story 3.3/3.10) — un accès à `.getItems()` par lot distinct déclenche une requête séparée par lot ; acceptable à l'échelle du projet (~3 postes POS, paniers de quelques articles, au plus 1-2 lots distincts par panier).
    - Nouvelle méthode publique `@Transactional BasketDto removeLot(Long basketId, Long lotId, Long userId)` : **`PhaseGuard.requireSalePhase(editionService.getActiveEdition())` en tout premier** (AC 9 de la Story 4.2 s'applique identiquement ici — même rationale que `removeItem`/`validate`, ne pas s'appuyer sur un garde hérité) ; puis `requireOwnedBasket(basketId, userId)` (méthode privée déjà existante — IDOR) ; `basketItemRepository.findAllByBasketIdAndItemLotId(basketId, lotId)` — si la liste est vide, lève `BasketLotNotFoundException(basketId, lotId)` ; sinon `basketItemRepository.deleteAll(...)` puis retourne `toDto(basket)`.
    - JavaDoc courte sur `removeLot` (méthode non triviale : suppression groupée + 404 explicite si rien à supprimer, cf. CLAUDE.md).
- [x] Backend — contrôleur (AC: 4)
  - [x] `org.pluribourse.domain.pos.controller.PosBasketController` (UPDATE) : ajouter
    ```java
    @DeleteMapping("/{basketId}/lots/{lotId}")
    public ResponseEntity<BasketDto> removeLot(
            @PathVariable Long basketId, @PathVariable Long lotId, Authentication authentication) {
        return ResponseEntity.ok(service.removeLot(basketId, lotId, userId(authentication)));
    }
    ```
    Aucune annotation `@PreAuthorize` (hérite de la règle globale, comme les 4 autres endpoints).
- [x] Frontend — modèle & service (AC: 1, 2, 3, 4)
  - [x] `models/pos.model.ts` (UPDATE) :
    ```typescript
    export interface ScanResult {
      itemId: number;
      name: string;
      price: number | null;
      incomplete: boolean;
      comment: string | null;
      lotId: number | null;
    }

    export interface LotGroup {
      lotId: number;
      lotName: string;
      globalPrice: number;
      scannedCount: number;
      totalCount: number;
    }

    export interface Basket {
      id: number;
      items: ScanResult[];
      lotGroups: LotGroup[];
      total: number;
    }
    ```
  - [x] `services/pos.service.ts` (UPDATE) : ajouter `removeLot(basketId: number, lotId: number): Observable<Basket>` → `DELETE /api/pos/baskets/${basketId}/lots/${lotId}`, même style que `removeItem`.
- [x] Frontend — page POS (AC: 1, 2, 3, 4)
  - [x] `features/volunteer/pos/pos-page.component.ts` (UPDATE) : ajouter `async removeLot(lotId: number): Promise<void>` — même patron exact que `removeItem` (réutilise le flag `removeInFlight` existant, pas un nouveau flag dédié : les deux actions de retrait sont mutuellement exclusives du point de vue de l'utilisateur), appelle `posService.removeLot(currentBasket.id, lotId)`, met à jour `this.basket`.
  - [x] `features/volunteer/pos/pos-page.component.html` (UPDATE, restructuration de la liste du panier) — cf. `mock-pos-caisse-lot-complet.html` (état complet) et `mock-pos-caisse.html` (état incomplet) :
    - Séparer l'affichage en deux blocs : (a) les articles **sans** lot (`basket()!.items` filtré `item.lotId == null`) — inchangé par rapport à la Story 4.2 (nom, prix, bouton retirer) ; (b) un bloc par lot, itérant `basket()!.lotGroups`, avec pour chaque groupe : en-tête (nom du lot + compteur `{{ scannedCount }}/{{ totalCount }} scannés`, sous-total = `group.globalPrice`, bouton « Retirer le lot entier » → `(click)="removeLot(group.lotId)"`), puis la liste indentée des articles de **ce** lot (`basket()!.items` filtré `item.lotId === group.lotId`) — nom + bouton retirer par ligne, **jamais de prix individuel** (pas de bloc `@if (item.price != null)` ici, contrairement aux articles sans lot : un article de lot a toujours `price == null`).
    - État visuel du groupe : `scannedCount === totalCount` → état « complet » (icône `check_circle` remplie, couleur succès) ; sinon → état « incomplet » (icône `warning`, couleur avertissement). Utiliser les tokens de couleur **déjà définis** dans `styles.scss` — `var(--pb-success-container)`/`var(--pb-on-success-container)` (`#F0FDF4`/`#166534`) et `var(--pb-warning-container)`/`var(--pb-on-warning-container)` (déjà utilisés par `print-queue-list.component.scss`/`toast-container.component.scss`) — plutôt que les valeurs hexadécimales codées en dur du mockup.
    - Sous chaque groupe incomplet, une notification via `<app-notification-inline>` (composant **déjà partagé**, réutilisé tel quel — pas de nouveau bloc `.lot-warning` bespoke comme dans le mockup HTML brut) : `[message]="'volunteer.pos.basket.lot.incomplete' | translate: { missing: group.totalCount - group.scannedCount, total: group.totalCount }"`, `[variant]="'warning'"`.
    - Le bouton « Retirer le lot entier » a un libellé visible (pas d'icône seule) : pas d'`aria-label` dédié nécessaire, contrairement au bouton de retrait par ligne (icône seule, `aria-label` déjà en place depuis la Story 4.2).
  - [x] `features/volunteer/pos/pos-page.component.scss` (UPDATE) : styles du groupe lot (fond, bordure gauche, icône, sous-total, bouton retirer, articles indentés sans prix) — réutiliser les tokens `--pb-space-*`/`--pb-rounded-*`/`--mat-sys-*` déjà en place dans ce fichier pour la cohérence, plus les tokens succès/avertissement ci-dessus pour les deux états.
  - [x] **Hors périmètre explicite** (ne pas implémenter) : regroupement des lots dans `payment-dialog.component` (le récapitulatif du dialog de paiement garde l'affichage ligne-par-ligne existant depuis la Story 4.2, avec `partOfLot` en lieu de prix — aucune AC de cette story ne l'exige) ; génération/impression de la facture (Story 4.5) ; toute logique de calcul de prix (déjà correcte depuis la Story 4.2).
- [x] i18n (AC: 1, 2, 4)
  - [x] `fr.json`/`en.json` (UPDATE), sous `volunteer.pos.basket.lot.*` (nouvelle sous-clé, même convention imbriquée que le reste de `volunteer.pos.*`) :
    - `header` : `"{{ name }} — {{ scanned }}/{{ total }} scannés"` (en : `"{{ name }} — {{ scanned }}/{{ total }} scanned"`).
    - `remove` : `"Retirer le lot entier"` (en : `"Remove entire lot"`).
    - `incomplete` : `"Lot incomplet — il manque {{ missing }} article(s) sur {{ total }}. Vous pouvez valider la vente ou retirer le lot."` (en : `"Incomplete lot — {{ missing }} item(s) missing out of {{ total }}. You can validate the sale or remove the lot."`) — pas d'accord grammatical singulier/pluriel géré (`article(s)`), cohérent avec le reste du projet (ex. `deposit.slip.submit` : « Valider le lot ({{ count }} articles) », toujours au pluriel quel que soit `count`).
- [x] Tests backend (AC: 1 à 4) — **étendre `PosBasketIT` existant, ne pas créer de nouvelle classe** (même scénario métier « panier POS », cf. principe une-classe-un-scénario)
  - [x] `advance_to_deposit_and_create_sellers_with_items_and_a_lot` (`@Order(2)`, UPDATE) : après la création de l'article « Insuffisant » (barcode `00010007`) et **avant** la création des articles de Bob, créer un **second lot** « Lot Retrait » (prix global `6.00`, 2 membres) sur Alice — barcodes attendus `00010008`/`00010009` (constantes `LOT2_ITEM_1_BARCODE`/`LOT2_ITEM_2_BARCODE`). Ce second lot sert uniquement au scénario de retrait complet ci-dessous, pour ne pas perturber l'état du lot « Lot Jouets » (barcodes `00010004`/`00010005`) déjà utilisé par les scénarios de validation existants (Order 6, 11).
  - [x] `adding_items_computes_a_lot_aware_total` (`@Order(6)`, UPDATE — assertions ajoutées, pas de changement de comportement) : après `afterFirstLotItem` (1 seul membre du lot scanné), asserter `afterFirstLotItem.lotGroups()` a taille 1, `lotGroups().get(0).scannedCount() == 1`, `.totalCount() == 2`, `.globalPrice()` `isEqualByComparingTo("10.00")` ; capturer `lotJouetsId = afterFirstLotItem.lotGroups().get(0).lotId()` (nouveau champ de classe `private Long lotJouetsId;`, réutilisé par le test de garde de phase ci-dessous). Après `afterSecondLotItem` (2/2 membres), asserter `lotGroups().get(0).scannedCount() == 2` (lot désormais complet).
  - [x] Nouveau test `@Order(19)` `removing_the_entire_lot_removes_all_its_items` (inséré **avant** l'actuel `@Order(19)` `a_sale_conflict_is_detected_at_validation_not_at_scan`, qui devient `@Order(20)` — **renuméroter tous les `@Order` suivants de +1, jusqu'à l'actuel 22 qui devient 23**) : utilise le panier de **`volunteer2`** (inutilisé avant l'actuel Order 19, et qui doit rester vide après ce nouveau test pour ne pas perturber le scénario de conflit qui suit) :
    1. `GET /current` (volunteer2) → capture `v2BasketId`.
    2. Ajoute `LOT2_ITEM_1_BARCODE` → `lotGroups` taille 1, `scannedCount == 1`, `totalCount == 2` ; capture `lotId`.
    3. Ajoute `LOT2_ITEM_2_BARCODE` → `scannedCount == 2`, `total` `isEqualByComparingTo("6.00")`.
    4. `DELETE /pos/baskets/{v2BasketId}/lots/{lotId}` → 200, `items` vide, `lotGroups` vide, `total` `isEqualByComparingTo("0")` — le panier de volunteer2 redevient vide, prêt pour le scénario de conflit suivant (désormais Order 20).
    5. Rejoue le **même** `DELETE .../lots/{lotId}` → 404 `basket-lot-not-found` (le lot n'est plus dans ce panier).
  - [x] `seller_role_is_forbidden_on_all_four_endpoints` (désormais `@Order(21)` après renumérotation, **renommer** en `..._all_five_endpoints`) : ajouter `mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/lots/1").session(sellerSession).with(csrf())).andExpect(status().isForbidden());` (même style littéral `1` que le test existant pour `/items/1`).
  - [x] `phase_guard_rejects_all_four_endpoints_once_sale_phase_ends` (désormais `@Order(23)`, **renommer** en `..._all_five_endpoints_once_sale_phase_ends`) : après le changement de phase vers `POST_SALE`, ajouter `mockMvc.perform(delete("/api/pos/baskets/" + volunteer1BasketId + "/lots/" + lotJouetsId).session(volunteer1Session).with(csrf())).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.type").value(endsWith("/sale-phase-required")));` — le garde de phase doit rejeter **avant** toute vérification de présence du lot dans le panier (cohérent avec le comportement déjà établi pour `removeItem`/`validate`).
  - [x] Vérifier que `BasketDto`/`LotGroupDto` sont bien désérialisables via `objectMapper.readValue(..., BasketDto.class)` dans les nouvelles assertions (aucun changement de configuration Jackson attendu, les deux sont des records simples).
- [x] Tests frontend
  - [x] `pos.service.spec.ts` (UPDATE) : un test pour `removeLot` (URL + méthode `DELETE`), même style que le test existant de `removeItem`.
  - [x] `pos-page.component.spec.ts` (UPDATE) : panier avec un article standalone + un lot incomplet (1/2) affiche le groupe séparément avec le bon compteur et la notification d'avertissement (message avec le nombre d'articles manquants) ; un lot complet (2/2) n'affiche **pas** de notification d'avertissement ; clic sur « Retirer le lot entier » appelle `posService.removeLot(basketId, lotId)` et met à jour l'affichage ; bouton « Valider » reste actif avec un lot incomplet dans le panier (non-régression explicite de l'AC 2 : pas de blocage) ; aucun prix individuel n'est rendu pour un article dont `lotId` n'est pas `null` (dans le groupe lot **et** en dehors, si un item de lot apparaissait par erreur hors groupe).

### Review Findings

- [x] [Review][Patch] Incohérence `==`/`===` sur la comparaison `item.lotId` dans le template [pos-page.component.html:18] — corrigé (`===`), 513/513 tests frontend toujours au vert après correction.
- [x] [Review][Defer] Race TOCTOU dans `removeLot` (`findAllByBasketIdAndItemLotId` puis `deleteAll`, non atomique) [PosBasketService.java:111-120] — deux appels `removeLot` concurrents sur le même lot pourraient tous deux lire une liste non vide avant que l'un des deux ne committe, le second renvoyant alors 200 (suppression idempotente, 0 ligne affectée) au lieu du 404 attendu. État final toujours correct (le lot est retiré), pas de corruption de données — même catégorie que la sécurité de la concurrence multi-postes explicitement reportée à la Story 4.4 dans les Dev Notes de 4.1/4.2. — deferred, pre-existing category of risk (Story 4.4 scope)
- [x] [Review][Defer] Logique de filtrage lot/hors-lot dans le template Angular (`@for`/`@if` imbriqués sur `basket()!.items`) plutôt que précalculée dans le composant [pos-page.component.html:17-63] — O(items × groupes), poursuit le patron déjà en place depuis la Story 4.2 pour les articles hors-lot ; acceptable à l'échelle actuelle (paniers de quelques articles, 1-2 lots max par panier, cf. Dev Notes § performance). — deferred, pre-existing template pattern
- [x] [Review][Defer] Bouton « Retirer le lot entier » sans binding `[disabled]` lié à `removeInFlight` [pos-page.component.html:43] — même patron que le bouton de retrait d'article existant depuis la Story 4.2 (garde uniquement côté TS, pas de reflet visuel pendant l'appel en cours) ; pas une régression introduite par cette story. — deferred, pre-existing button pattern
- [x] [Review][Defer] Structure du commentaire `last_updated` dans `sprint-status.yaml` (ligne unique croissante couvrant plusieurs stories) [sprint-status.yaml:38] — convention déjà en place dans tout le fichier avant cette story, question de process documentaire indépendante du code. — deferred, pre-existing documentation convention

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- `ItemPricing.distinctByLot(List<Item>)` et `ItemPricing.computeTotal(List<Item>)` (`org.pluribourse.domain.item.service.ItemPricing`, introduits Story 4.2) portent déjà toute la logique de dédoublonnage par lot et de calcul du total lot-aware — **réutiliser `distinctByLot` pour construire `lotGroups`**, ne pas réécrire une boucle de dédoublonnage équivalente. Le calcul du total lui-même (`computeTotal`) **ne change pas** dans cette story.
- `Lot.items` (`@OneToMany(mappedBy = "lot", fetch = FetchType.EAGER) @OrderBy("id ASC")`, `Lot.java:39`) donne directement le nombre total de membres du lot (`totalCount`) — pas besoin d'une requête dédiée.
- `BasketItemRepository.findAllByBasketIdOrderById` (Story 4.2, `JOIN FETCH bi.item i LEFT JOIN FETCH i.lot`) reste la méthode de lecture du panier dans `PosBasketService` — elle charge déjà `item.lot` en même temps, donc `buildLotGroups` peut lire `item.getLot()` sans déclencher de lazy-loading supplémentaire par article (seul `lot.getItems()` — collection séparée — coûte une requête additionnelle par lot distinct, voir tâche service ci-dessus).
- `requireOwnedBasket(Long, Long)` (IDOR) et `PhaseGuard.requireSalePhase(Edition)` (Story 4.1/4.2) sont réutilisés tels quels par `removeLot` — même garde, même ordre (phase avant ownership), cohérent avec `removeItem`/`validate`.
- `<app-notification-inline>` (`shared/components/notification-inline/`) est le composant **déjà utilisé** pour `lastScanIssue` dans `pos-page.component` — le mockup UX introduit un bloc `.lot-warning` bespoke qui reproduit exactement ce que ce composant fait déjà (icône `warning`, fond/bordure de couleur avertissement, `role="alert"`/`aria-live`) : **réutiliser le composant partagé, ne pas dupliquer son HTML/CSS**.
- Tokens de couleur `--pb-success-container`/`--pb-on-success-container`/`--pb-warning-container`/`--pb-on-warning-container` (`pluribourse-frontend/src/styles.scss:48-51`, déjà utilisés par `print-queue-list.component.scss`) — à utiliser pour les deux états du groupe lot plutôt que les valeurs hexadécimales du mockup (`#F0FDF4`/`#166534` pour l'état complet correspondent d'ailleurs exactement à ces tokens).

### Prix individuel de lot : vérifié absent des deux mockups, décision de conception néanmoins actée

Le fichier `mock-pos-caisse.html` (état lot incomplet) contient une règle CSS inutilisée `.lot-article-list .article-row__price { display: inline-block; ... }` — mais **aucun élément `.article-row__price` n'existe réellement dans le markup `.lot-article-list`** de ce mockup ni de `mock-pos-caisse-lot-complet.html` : le « 8,00 € »/« 10,50 € » visibles dans les deux mockups sont le sous-total du groupe (`.lot-group__subtotal`), pas un prix individuel. Cette règle CSS morte n'a donc aucun effet visuel dans les mockups ; il n'y a pas d'incohérence de rendu à corriger entre les deux fichiers.

**Décision de conception (confirmée, pas une correction d'incohérence) : aucun prix individuel dans le groupe lot, pour les deux états, complet et incomplet** — seul l'icône/la couleur/le texte du compteur changent entre les deux états ; le sous-total affiché est toujours `lot.getGlobalPrice()` (`group.globalPrice`). Cette décision reste la seule cohérente avec :
- l'AC 1 de l'epic (« aucun prix individuel n'est affiché dans le groupe lot ») ;
- le modèle de données existant (`Item.price` est **toujours** `null` pour un article appartenant à un lot, quel que soit l'état complet/incomplet du lot — `LotService.create()` ne fait jamais `item.setPrice(...)`) ;
- FR-048 (le lot a un **prix global unique**, qu'il soit complet ou non — c'est déjà le comportement implémenté par `ItemPricing.computeTotal` depuis la Story 4.2, qui ajoute `lot.getGlobalPrice()` dès qu'au moins un membre du lot est présent, peu importe combien).

### Project Structure Notes

- Backend : aucun nouveau package, aucune migration Liquibase — cette story ajoute uniquement des champs à des DTOs existants, un DTO/exception/méthode de repository/méthode de service/endpoint dans le package `pos` déjà en place depuis les Stories 4.1/4.2. Aucune modification d'entité (`Item`/`Lot`/`Basket`/`BasketItem` restent inchangés).
- Frontend : aucun nouveau composant — toute la logique de regroupement vit dans `pos-page.component` (template + petite logique de filtrage par `lotId`), pas un composant `lot-group` séparé (le panier reste un seul composant, cohérent avec la taille modeste de cette fonctionnalité et l'absence de réutilisation ailleurs dans l'app).
- Aucune nouvelle route, aucune nouvelle clé i18n en dehors de `volunteer.pos.basket.lot.*`.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/ScanResultDto.java`, `BasketDto.java`, `mapper/ScanResultMapper.java`, `service/PosBasketService.java`, `controller/PosBasketController.java`, `repository/BasketItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Lot.java` (référence — `items` EAGER, déjà en place), `service/ItemPricing.java` (réutilisée telle quelle, ne pas modifier sa logique de calcul), `service/LotService.java` (référence — confirme `Item.price == null` pour tout membre de lot)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` (à étendre, pas à dupliquer)
- `pluribourse-frontend/src/app/models/pos.model.ts`, `services/pos.service.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts/.html/.scss/.spec.ts`
- `pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.ts` (référence, ne pas modifier — juste réutiliser)
- `pluribourse-frontend/src/styles.scss` (référence — tokens `--pb-success-container`/`--pb-warning-container` déjà définis, lignes 49-51)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.3] — ACs source (FR-046, FR-047, FR-048, FR-081, FR-041)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#F4 — Point de Vente] — FR-041, FR-046 à FR-048, FR-081
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS (Point de Vente)] — aucun changement de modèle de concurrence dans cette story
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-pos-caisse-lot-complet.html] — état « lot complet » (icône `check_circle` remplie, fond/bordure succès, pas de prix individuel) — **suivi comme référence canonique**
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-pos-caisse.html] — état « lot incomplet » (icône `warning`, fond/bordure avertissement, notification inline) — suivi **sauf** la règle CSS forçant un prix individuel, incohérente avec le modèle de données (voir Dev Notes ci-dessus)
- [Source: _bmad-output/implementation-artifacts/4-2-gestion-du-panier-validation-du-paiement.md] — story précédente (contrat `BasketDto`/`ScanResultDto`, `ItemPricing`, patron CDK Dialog, décision explicite de reporter le regroupement visuel des lots à cette story)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/**, domain/item/entity/Lot.java, domain/item/service/ItemPricing.java, domain/item/service/LotService.java] — lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java] — lu intégralement (fixtures, ordre des scénarios, barcodes réservés)
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/**, shared/components/notification-inline/**, styles.scss] — lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5

### Debug Log References

None — implementation proceeded without blockers. Note: `PosBasketService.buildLotGroups` originally filtered `scannedCount` with `item.getLot().getId()` without a null-check for standalone items, which would have thrown an NPE at runtime for any basket mixing lot and non-lot items — caught and fixed during implementation (before any test run) by adding `item.getLot() != null &&` to the filter predicate.

### Completion Notes List

- Backend: `ScanResultDto.lotId` (mapped via `ScanResultMapper`), new `LotGroupDto` record, `BasketDto.lotGroups` (built by new `PosBasketService.buildLotGroups`, reusing `ItemPricing.distinctByLot`), new `PosBasketService.removeLot` (phase guard → ownership → 404 if lot absent → bulk delete), new `DELETE /pos/baskets/{basketId}/lots/{lotId}` endpoint, new `BasketLotNotFoundException`. No entity, migration, or pricing-logic change (as scoped).
- Frontend: `pos.model.ts`/`pos.service.ts` extended (`ScanResult.lotId`, `LotGroup`, `Basket.lotGroups`, `removeLot()`); `pos-page.component` template restructured to render lot items as grouped blocks (counter, subtotal, complete/incomplete state via `--pb-success-container`/`--pb-warning-container` tokens, reused `<app-notification-inline>`) instead of flat list rows; standalone items unchanged. New `removeLot()` method reuses the existing `removeInFlight` guard.
- i18n: `volunteer.pos.basket.lot.{header,remove,incomplete}` added to `fr.json`/`en.json`.
- Tests: `PosBasketIT` extended per plan — second lot fixture ("Lot Retrait", barcodes 00010008/00010009), `lotGroups` assertions on the existing lot-aware-total scenario, new `removing_the_entire_lot_removes_all_its_items` scenario (@Order 19, using volunteer2's basket), all subsequent `@Order` values shifted +1, the two four-endpoint guard tests renamed to "five endpoints" with the new `DELETE .../lots/{lotId}` case added. `pos.service.spec.ts` and `pos-page.component.spec.ts` extended; `payment-dialog.component.spec.ts` fixture updated for the new required `lotId` field (unrelated to this story's scope but required by the type change).
- Full regression: backend 364/364, frontend 513/513 — no failures.

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/ScanResultDto.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/LotGroupDto.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/BasketDto.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/mapper/ScanResultMapper.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/BasketLotNotFoundException.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketItemRepository.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosBasketController.java` (UPDATE)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` (UPDATE)
- `pluribourse-frontend/src/app/models/pos.model.ts` (UPDATE)
- `pluribourse-frontend/src/app/services/pos.service.ts` (UPDATE)
- `pluribourse-frontend/src/app/services/pos.service.spec.ts` (UPDATE)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` (UPDATE)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` (UPDATE)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss` (UPDATE)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` (UPDATE)
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.spec.ts` (UPDATE)
- `pluribourse-frontend/public/i18n/fr.json` (UPDATE)
- `pluribourse-frontend/public/i18n/en.json` (UPDATE)
