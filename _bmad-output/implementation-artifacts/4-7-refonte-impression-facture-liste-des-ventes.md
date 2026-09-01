---
baseline_commit: 4df4a8d433b3bb0164ba993199c4ac6be5f02be9
---

# Story 4.7: Refonte de l'impression de la facture + écran « Liste des ventes »

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole caissier,
I want cocher « Imprimer la facture » directement dans la validation du paiement **et** retrouver n'importe quelle vente passée de l'édition active depuis un écran dédié pour la réimprimer,
so that l'acheteur repart toujours avec son justificatif — sans dépendre d'un bouton qui disparaît au bout de 30 secondes ni d'être le caissier qui a réalisé la vente.

**Origine :** Sprint Change Proposal 2026-08-24, points 5 & 6 (regroupés en une seule story cohérente sur décision du sprint change — voir `_bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md` § « Points 5 & 6 »). `epics.md` n'a **pas** été amendé (même convention que les stories 2.9 / 2.10 / 3.14, toutes issues du même sprint change proposal) — la source normative des exigences est le sprint change proposal, complété par cette story.

## Contexte code existant (lire avant de commencer)

L'impression de facture acheteur est déjà livrée (Story 4.5, `done`). Cette story **retouche** ce flux, elle ne le crée pas :

- `PosInvoicePrintService.printInvoice(saleId, userId, session)` génère le PDF (`InvoiceRenderer`, OpenPDF), le pousse sur la file de l'imprimante A4 sélectionnée en session, sans garde de phase (une `Sale` est un enregistrement immuable). **Restriction actuelle à retirer :** `if (!sale.getUser().getId().equals(userId)) { throw new SaleNotFoundException(saleId); }` — seul l'auteur de la vente peut réimprimer.
- `PosSaleController` expose `POST /pos/sales/{saleId}/invoice/print` → 204.
- `pos-page.component.ts` capture la `Sale` retournée par `validate()` dans un signal `lastSale`, affiche un bouton « Imprimer la facture » pendant `INVOICE_BUTTON_VISIBLE_MS = 30000` ms via un `setTimeout` (`showInvoiceButton()` / `invoiceButtonTimer` / `printInvoice()` / `printingInvoice`). **Tout ce mécanisme temporaire est supprimé par cette story.**
- `payment-dialog.component.ts` retourne un `ValidateBasketRequest` (`{ paymentMethod, amountGiven }`) ; ce même objet est le corps HTTP de `POST /pos/baskets/{id}/validate`.
- Il n'existe **aucune** notion de « poste de caisse » / station dans le modèle : une `Sale` porte uniquement `user` (`@ManyToOne` non nul), `edition`, `paymentMethod`, `amountGiven`, `total`, `soldAt`. Le « poste » du sprint change proposal se lit donc comme **le bénévole caissier** (`Sale.user`) — voir Dev Notes § Décision « poste de caisse ».
- `SaleRepository` a déjà `findAllByEditionId(editionId)` (Story 5.4) et `findAllByEditionIdAndSoldAtBetween(...)` (Story 5.3). Aucune ne fait de `JOIN FETCH s.user`.

## Acceptance Criteria

### Partie A — Case à cocher « Imprimer la facture » à la validation (FR-107)

1. **Case cochée par défaut dans le dialogue de paiement.** Étant donné que le caissier ouvre le dialogue de validation du paiement (`PaymentDialogComponent`), quand le dialogue s'affiche, alors une case à cocher « Imprimer la facture » est présente et **cochée par défaut**. Le caissier peut la décocher avant de confirmer.

2. **Impression automatique si la case est cochée.** Étant donné que la case « Imprimer la facture » est cochée quand le caissier confirme le paiement, quand `POST /pos/baskets/{basketId}/validate` répond 200, alors le frontend déclenche immédiatement `POST /pos/sales/{saleId}/invoice/print` avec l'`id` de la `Sale` retournée — sans clic supplémentaire. Si la case est décochée, aucune impression n'est déclenchée.

3. **L'impression automatique est best-effort et ne remet jamais la vente en cause.** Étant donné que l'impression automatique échoue (ex. aucune imprimante A4 sélectionnée → 422 `invalid-printer-selection`, ou 5xx), quand l'erreur revient, alors la vente **reste validée** (elle ne dépend pas de l'impression), le panier neuf est chargé normalement, et un toast d'erreur dédié est affiché : message spécifique `volunteer.pos.invoice.error.a4PrinterUnavailable` pour un 422 `invalid-printer-selection`, `volunteer.pos.invoice.error.generic` sinon. Un succès affiche `volunteer.pos.invoice.success`.

4. **Le corps HTTP de `validate` est inchangé.** `ValidateBasketDto` (backend) et `ValidateBasketRequest` (frontend, corps de `POST .../validate`) ne reçoivent **aucun** nouveau champ — le choix « imprimer ou non » ne transite pas par cet endpoint. La transaction atomique de `PosBasketService.validate` n'est pas touchée par cette story.

### Partie B — Suppression du bouton temporaire à 30 secondes (FR-040 amendé)

5. **Le bouton « Imprimer la facture » de l'écran caisse et sa fenêtre de 30 s sont supprimés.** Étant donné qu'un paiement vient d'être validé sur `/volunteer/pos`, quand le panier neuf se charge, alors **aucun** bouton « Imprimer la facture » n'apparaît sur l'écran caisse, et aucun `setTimeout` lié à une facture n'est armé. `pos-page.component.ts` ne référence plus `lastSale`, `printingInvoice`, `invoiceButtonTimer`, `INVOICE_BUTTON_VISIBLE_MS`, `showInvoiceButton()` ni la méthode `printInvoice()`.

6. **Aucune régression sur l'annulation de panier (Story 4.6).** Étant donné que la Story 4.6 documentait explicitement que `lastSale`/`printInvoice` restaient volontairement actifs après un `basket-cancelled`, quand ce mécanisme disparaît, alors les gardes anti-résurrection (`basketCancelled()`) de `onScan`/`removeItem`/`removeLot`/`openPaymentDialog`/`loadBasket` restent **strictement inchangées** ; seules les assertions de tests portant sur la survie du bouton facture après annulation sont retirées (le comportement testé n'existe plus).

### Partie C — Réimpression ouverte à tout bénévole caissier (FR-108)

7. **Retrait de la restriction « auteur de la vente uniquement ».** Étant donné que le bénévole caissier B (qui n'a pas réalisé la vente du caissier A) appelle `POST /pos/sales/{saleId}/invoice/print` pour cette vente, quand la vente appartient à l'édition active, alors l'appel répond 204 et le job est mis en file — **plus de 404 `sale-not-found` pour cause de non-appartenance**.

8. **La réimpression reste bornée à l'édition active.** Étant donné un `saleId` inexistant, ou appartenant à une **autre** édition que l'édition active, quand `POST /pos/sales/{saleId}/invoice/print` est appelé, alors la réponse est 404 `sale-not-found` (on ne distingue jamais « n'existe pas » de « appartient à une autre édition », même patron IDOR que l'implémentation actuelle). Un `saleId` valide de l'édition active → 204.

9. **Aucune imprimante A4 sélectionnée (inchangé, Story 4.5 AC 7).** Si aucune imprimante A4 n'est sélectionnée en session, ou si celle sélectionnée n'est plus disponible, l'appel renvoie 422 `invalid-printer-selection`. La vente reste enregistrée.

### Partie D — Écran « Liste des ventes » (FR-108)

10. **Accès et chargement initial.** Étant donné que le bénévole (ou l'admin) navigue vers `/volunteer/sales` en phase Vente, quand la page se charge, alors toutes les ventes de l'édition active sont affichées, paginées (`MatPaginator`, 50 par page par défaut), les plus récentes en premier par défaut (`soldAt` décroissant), avec des filtres en ligne au-dessus de la liste. Colonnes : date/heure (`soldAt`), caissier, moyen de paiement, total. Une action « Réimprimer la facture » par ligne.

11. **Filtre par plage date/heure.** Étant donné que le bénévole renseigne un « du » et/ou un « au » (date + heure), quand le filtre s'applique, alors seules les ventes dont `soldAt` est dans l'intervalle **fermé `[du, au]` (les deux bornes incluses)** sont listées. Une borne laissée vide = pas de limite de ce côté.

12. **Filtre par caissier (« poste »).** Étant donné que le bénévole choisit un caissier dans le sélecteur, quand le filtre s'applique, alors seules les ventes de ce caissier sont listées. Le sélecteur est peuplé depuis `GET /pos/sales/cashiers` (liste des caissiers ayant ≥ 1 vente sur l'édition active). Option « Tous » = pas de filtre caissier.

13. **Tri par en-tête de colonne.** Étant donné que le bénévole clique un en-tête de colonne triable (date/heure, caissier, moyen de paiement, total), quand il clique une fois, alors la liste se trie ascendant avec indicateur visible ; un second clic trie descendant (`MatSortModule`, même patron que `ItemCatalogComponent`).

14. **Réimpression depuis une ligne.** Étant donné que le bénévole clique « Réimprimer la facture » sur une ligne, quand l'appel `POST /pos/sales/{saleId}/invoice/print` répond 204, alors un toast succès `volunteer.pos.invoice.success` est affiché ; un 422 `invalid-printer-selection` → toast `volunteer.pos.invoice.error.a4PrinterUnavailable` ; toute autre erreur → `volunteer.pos.invoice.error.generic`. Un verrou anti-double-clic par ligne (ou global) empêche les envois multiples pendant qu'un appel est en vol.

15. **États vide / chargement / erreur.** Liste vide (aucune vente, ou aucun résultat après filtre) → `EmptyStateComponent` avec `volunteer.sales.empty`. Chargement → `SkeletonRowComponent`. Pas d'édition active (`no-active-edition`) → `NotificationInlineComponent` avec `volunteer.sales.error.noActiveEdition` ; échec générique → `volunteer.sales.error.load`. Mêmes patrons que `ItemCatalogComponent`.

16. **Filtres concurrents.** Étant donné plusieurs bénévoles filtrant simultanément, quand chacun soumet des combinaisons différentes, alors chacun reçoit indépendamment son propre résultat correct (garanti par construction : `GET` stateless, aucun état de filtre partagé côté serveur).

17. **Découvrabilité.** Une entrée « Liste des ventes » est ajoutée à la sidebar bénévole, visible en phase Vente (même conditionnement que l'entrée « Caisse »), pointant vers `/volunteer/sales`.

## Tasks / Subtasks

### Backend

- [x] **T1 — Retrait de la restriction d'appartenance + scoping édition active sur l'impression (AC 7, 8, 9)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java` (UPDATE) :
    - Supprimer le paramètre `Long userId` de `printInvoice(...)` et le bloc `if (!sale.getUser().getId().equals(userId)) { ... }`.
    - Remplacer la garde d'appartenance par une garde d'**édition active** : après `saleRepository.findById(saleId).orElseThrow(() -> new SaleNotFoundException(saleId))`, comparer `sale.getEdition().getId()` à `editionService.getActiveEdition().getId()` ; si différent → `throw new SaleNotFoundException(saleId)` (même patron IDOR : on ne révèle jamais qu'une vente d'une autre édition existe). Injecter `EditionService` (`@RequiredArgsConstructor`, ajouter le champ `final`). **Attention à l'ordre des champs `final`** vis-à-vis du constructeur généré par Lombok si un test instancie ce service à la main (vérifier `InvocePrintingIT` / grep `new PosInvoicePrintService(`).
    - Mettre à jour le JavaDoc de classe : la restriction n'est plus « auteur de la vente » mais « vente de l'édition active » (FR-108). Réécrire aussi le commentaire inline de la garde (aujourd'hui `// ... (IDOR, AC 6) ...`) : nouveau motif = scoping édition active, et corriger le n° d'AC → AC 8 de cette story.
    - Ne **pas** ajouter de `PhaseGuard` : décision de conception de la Story 4.5 conservée (une `Sale` est immuable, aucun AC ne conditionne la réimpression à la phase). La réimpression doit rester possible en Vente **et** Post-vente.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java` (UPDATE) : `printInvoice(...)` n'a plus besoin de `Authentication` ni de `userId(authentication)` — appeler `service.printInvoice(saleId, session)`. Supprimer la méthode privée `userId(...)` si elle devient inutilisée.

- [x] **T2 — Endpoint liste des ventes (AC 10, 11, 12, 13, 16)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java` (UPDATE) : ajouter
    ```java
    /**
     * Sales list screen (story 4.7, FR-108): every Sale of the edition, with its cashier eagerly
     * fetched — the list DTO exposes the cashier's username, and mapping happens after the
     * transaction closes (same JOIN FETCH rationale as ItemRepository.findAllByEditionIdForCatalog).
     * Filtering (date range, cashier) and sort/page are applied in memory afterwards by
     * SaleListService, not in this query.
     */
    @Query("SELECT s FROM Sale s JOIN FETCH s.user WHERE s.edition.id = :editionId ORDER BY s.soldAt DESC")
    List<Sale> findAllByEditionIdForList(@Param("editionId") Long editionId);
    ```
    L'`ORDER BY s.soldAt DESC` explicite est le tri par défaut (AC 10) **et** garantit un ordre stable pour la pagination quand aucun `sort` n'est fourni (même raisonnement que `findAllByEditionIdForCatalog`, cf. Story 6.1 Dev Notes).
    Ajouter aussi la requête des caissiers (AC 12) : `@Query("SELECT DISTINCT s.user.username FROM Sale s WHERE s.edition.id = :editionId ORDER BY s.user.username") List<String> findDistinctCashierUsernamesByEditionId(@Param("editionId") Long editionId);`.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListItemDto.java` (NEW) :
    ```java
    public record SaleListItemDto(
            Long id,
            LocalDateTime soldAt,
            String cashier,          // Sale.user.username
            PaymentMethod paymentMethod,
            BigDecimal total,
            String currency          // edition currency, so the frontend formats amounts without a second call
    ) {}
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListPageDto.java` (NEW) : `public record SaleListPageDto(Page<SaleListItemDto> page) {}` — même enveloppe que `ItemCatalogPageDto` (Spring `Page<T>` sérialisé directement, patron déjà en place, cf. Story 6.1).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListFilterDto.java` (NEW) : record interne construit par le contrôleur depuis des `@RequestParam` individuels (jamais bindé directement), même patron que `ItemCatalogFilterDto` :
    ```java
    public record SaleListFilterDto(
            LocalDateTime dateFrom,   // nullable
            LocalDateTime dateTo,     // nullable
            String cashier,           // nullable — exact match on username
            int page,
            int size,
            String sort               // nullable — e.g. "soldAt,desc"
    ) {}
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/mapper/SaleListMapper.java` (NEW, MapStruct) : `SaleListItemDto toDto(Sale sale, String currency)` — `cashier` mappé depuis `sale.user.username`. Suivre le style des mappers MapStruct existants du projet (cf. `ScanResultMapper`). Si le passage du `currency` en second paramètre complique le mapper MapStruct, un mapping manuel dans le service est acceptable (3 champs triviaux + 1 dérivé) — trancher à l'implémentation selon CLAUDE.md (pas d'abstraction prématurée).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/SaleListService.java` (NEW) — **copie structurelle de `ItemCatalogService`** :
    ```java
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("soldAt", "user.username", "paymentMethod", "total");

    @Transactional(readOnly = true)
    public SaleListPageDto getSales(SaleListFilterDto filter) {
        validateSort(filter.sort()); // whitelist → InvalidSortFieldException (400, type "invalid-sort-field"). Créer une jumelle dans pos/exception (message "Cannot sort the sales list by field: ...") : celle de item/exception a un message figé "Cannot sort the catalog by field", inexact ici.
        Edition edition = editionService.getActiveEdition();
        List<Sale> all = saleRepository.findAllByEditionIdForList(edition.getId());
        String currency = edition.getCurrency();
        List<Sale> filtered = all.stream()
                .filter(s -> filter.dateFrom() == null || !s.getSoldAt().isBefore(filter.dateFrom())) // soldAt >= dateFrom
                .filter(s -> filter.dateTo() == null || !s.getSoldAt().isAfter(filter.dateTo()))       // soldAt <= dateTo — les deux bornes incluses (décision utilisateur)
                .filter(s -> filter.cashier() == null || filter.cashier().isBlank()
                        || filter.cashier().equalsIgnoreCase(s.getUser().getUsername()))
                .toList();
        FilterDto pagingOnly = new FilterDto();
        pagingOnly.setPage(clampPage(filter.page(), filter.size(), filtered.size()));
        pagingOnly.setSize(filter.size());
        pagingOnly.setSort(filter.sort());
        Page<SaleListItemDto> page = FilterService.filterData(filtered, pagingOnly,
                sales -> sales.stream().map(s -> mapper.toDto(s, currency)).toList());
        return new SaleListPageDto(page);
    }

    public List<String> getCashiers() {
        return saleRepository.findDistinctCashierUsernamesByEditionId(
                editionService.getActiveEdition().getId());
    }
    ```
    Ajouter dans `SaleRepository` : `@Query("SELECT DISTINCT s.user.username FROM Sale s WHERE s.edition.id = :editionId ORDER BY s.user.username") List<String> findDistinctCashierUsernamesByEditionId(@Param("editionId") Long editionId);` — plus direct et plus simple à asserter que recharger toutes les ventes (`findAllByEditionIdForList`) juste pour en tirer des usernames distincts.
    Tri : `soldAt` (`LocalDateTime`), `total` (`BigDecimal` — OK depuis JPageFlow 1.7.0, cf. Story 6.1), `user.username` (chemin pointé résolu par réflexion, comme `sellerProfile.lastName` en Story 6.1), `paymentMethod` (enum — `Comparable`, trié par ordre de déclaration `CASH < CHECK < CARD` ; couvrir par un test `sort=paymentMethod,asc` pour confirmer que JPageFlow 1.7.0 le gère sans exception).
    Reprendre **à l'identique** `clampPage(int, int, int)` et `validateSort(String)` de `ItemCatalogService` (mêmes JavaDoc expliquant le comportement de JPageFlow : `Page.empty()` au-delà de la dernière page ; `NullPointerException` brute sur champ de tri inconnu). Ne pas factoriser dans une classe partagée pour cette story — duplication de 2 méthodes courtes < abstraction prématurée (CLAUDE.md), et `ItemCatalogService` est un fichier stable déjà revu.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java` (UPDATE) : ajouter deux `@GetMapping` sur le contrôleur `/pos/sales` existant (même ressource — pas de nouveau contrôleur). **Annoter la _classe_ `PosSaleController` avec `@Validated`** (elle ne l'est pas aujourd'hui) — c'est ce qui fait lever `ConstraintViolationException` (→ 422) sur les `@Min`/`@Max` des `@RequestParam` ; posé sur la méthode, ces bornes ne produisent pas le 422 attendu. Copier exactement le placement de `ItemCatalogController` (`@Validated` au niveau classe).
    ```java
    @GetMapping
    public ResponseEntity<SaleListPageDto> listSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String cashier,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(saleListService.getSales(
                new SaleListFilterDto(dateFrom, dateTo, cashier, page, size, sort)));
    }

    @GetMapping("/cashiers")
    public ResponseEntity<List<String>> listCashiers() {
        return ResponseEntity.ok(saleListService.getCashiers());
    }
    ```
    Bornes `@Min`/`@Max` sur `page`/`size` → 422 **uniquement si `@Validated` est sur la classe** (`GlobalExceptionHandler` mappe `ConstraintViolationException` → 422 `UNPROCESSABLE_CONTENT` ; `MethodArgumentNotValidException` → 400). Patron du finding de revue Story 6.1. Pas de `@PreAuthorize` : règle globale authentifié-non-SELLER héritée de `SecurityConfig`, comme pour `ItemCatalogController` et le reste de `PosSaleController` — ADMIN **et** VOLUNTEER doivent pouvoir atteindre `/pos/sales`.
    ⚠ Vérifier qu'il n'y a **pas** de collision de route entre `@GetMapping` (racine `/pos/sales`) et `@GetMapping("/cashiers")` et le `@PostMapping("/{saleId}/invoice/print")` existant — `/cashiers` doit être déclaré / résoudre avant `/{saleId}` si un `@GetMapping("/{saleId}")` existait, mais ici les verbes diffèrent (GET vs POST) donc pas de conflit ; `/cashiers` est un chemin littéral, pas capturé par `{saleId}`.

- [x] **T3 — Tests backend**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (UPDATE) — refléter le changement d'AC 7/8 :
    - `@Order(10)` `printing_another_volunteers_sale_returns_404_ownership_is_never_confirmed_or_denied` → **renommer et inverser** : `printing_another_volunteers_sale_is_now_allowed_for_any_cashier` — `volunteer2Session` réimprime la vente de `volunteer1` → 204 (au lieu de 404). `volunteer2` doit avoir une imprimante A4 sélectionnée pour son propre `session` (ajouter la sélection dans le setup, comme pour `volunteer1`) — sinon l'appel tombe en 422, pas 204.
    - `@Order(11)` `printing_a_nonexistent_sale_returns_404` → conservé tel quel.
    - **Nouveau test — E2E uniquement** (pas de test unitaire mocké : `EditionService` est une couche interne, Mockito interdit hors composant externe, CLAUDE.md § Tests) : garder l'`id` d'une vente réalisée pendant le scénario, puis faire avancer l'édition jusqu'à `CLOSED` et créer/activer une nouvelle édition ; `POST /pos/sales/{ancienSaleId}/invoice/print` → 404 `sale-not-found` (la vente existe mais n'appartient plus à l'édition active). Si le montage d'une 2e édition alourdit trop `InvoicePrintingIT`, placer ce cas dans `SaleListIT` (qui monte déjà un cycle d'édition complet) plutôt que dans un test isolé.
    - Ajuster tout `new PosInvoicePrintService(...)` manuel à la nouvelle signature (champ `final EditionService` ajouté → nouveau paramètre du constructeur Lombok). `DocumentPrintService` **ne change pas** de signature ; `@Order(8)` (`document_print_service_sends_the_rendered_invoice_pdf_bytes_via_printer_bridge_client`) instancie `new DocumentPrintService(...)` à la main mais n'est **pas** impacté — vérifier tout de même par grep `new PosInvoicePrintService(` (prod + test).
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleListIT.java` (NEW) — story-board `@TestMethodOrder(OrderAnnotation.class)`, structure calquée sur `ItemCatalogIT` :
    - Setup : édition en Vente, 2 catégories, 2 vendeurs, articles, imprimante A4. Réaliser **plusieurs vraies ventes** via `PosBasketController` avec `volunteer1Session` et `volunteer2Session` (au moins 2 caissiers distincts, 3-4 ventes, moyens de paiement variés, à des `soldAt` différents — si `soldAt = LocalDateTime.now()` rend le contrôle de plage difficile en test, asserter les bornes de façon relative : `dateFrom = now().minusMinutes(1)` renvoie tout, `dateTo = now().minusYears(1)` renvoie rien).
    - `@Order` — `GET /pos/sales` sans filtre : renvoie toutes les ventes, `page.totalElements` correct, tri par défaut `soldAt` décroissant (asserter `content[0].soldAt >= content[1].soldAt`).
    - `@Order` — filtre `cashier=volunteer2` : ne renvoie que les ventes de `volunteer2`.
    - `@Order` — filtre `dateFrom`/`dateTo` : **les deux bornes incluses** (asserter explicitement qu'une vente dont `soldAt` vaut exactement `dateFrom` est renvoyée, et qu'une vente dont `soldAt` vaut exactement `dateTo` est renvoyée elle aussi).
    - `@Order` — `sort=total,asc` puis `total,desc` : bascule ascendant/descendant (JPageFlow 1.7.0 gère `BigDecimal`, cf. Story 6.1 Change Log — pas de `@Disabled` à prévoir).
    - `@Order` — `sort=unknownField` → 400 `invalid-sort-field` ; `page=-1` → 422 ; `size=999` → 422.
    - `@Order` — `GET /pos/sales/cashiers` : renvoie `["volunteer1", "volunteer2"]` triés, sans doublon.
    - `@Order` — session ADMIN (`test_admin`) : `GET /pos/sales` → 200 (accessible aux deux rôles, AC 10).
    - `@Order` — SELLER → 403 (comportement `SecurityConfig`, pas implémenté par cette story mais asserté comme filet, cf. `ItemCatalogIT`).
    - Filtres concurrents (AC 16) : deux requêtes avec filtres différents renvoient chacune leur résultat (peut être un simple test séquentiel prouvant l'absence d'état partagé, comme `ItemCatalogIT`).

### Frontend

- [x] **T4 — Case à cocher dans le dialogue de paiement (AC 1, 2, 3, 4)**
  - [x] `pluribourse-frontend/src/app/models/pos.model.ts` (UPDATE) : **ne pas** toucher `ValidateBasketRequest` (contrat HTTP pur). Ajouter :
    ```typescript
    export interface PaymentDialogResult {
      request: ValidateBasketRequest;
      printInvoice: boolean;
    }
    ```
    (ou, si plus simple, `{ paymentMethod, amountGiven, printInvoice }` et laisser `pos.service.validate()` reconstruire le corps depuis les deux premiers — trancher à l'implémentation ; l'essentiel : `printInvoice` **ne doit jamais** partir dans le corps de `validate`.)
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts` (UPDATE) :
    - Importer `MatCheckboxModule`, l'ajouter aux `imports`.
    - `readonly printInvoice = signal(true);` (coché par défaut, AC 1).
    - `confirm()` : `this.dialogRef.close({ request: { paymentMethod: ..., amountGiven: ... }, printInvoice: this.printInvoice() });`. Le type générique de `DialogRef` passe de `ValidateBasketRequest | undefined` à `PaymentDialogResult | undefined`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.html` (UPDATE) : ajouter, dans `.dialog__actions` ou juste au-dessus, une `<mat-checkbox>` liée à `printInvoice()` :
    ```html
    <mat-checkbox [checked]="printInvoice()" (change)="printInvoice.set($event.checked)">
      {{ 'volunteer.pos.payment.printInvoice' | translate }}
    </mat-checkbox>
    ```
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.service.ts` (UPDATE) : type de retour `Observable<PaymentDialogResult | undefined>`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` (UPDATE) — `openPaymentDialog()` :
    - `const result = await firstValueFrom(this.paymentDialogService.open({...}));` → `result` est maintenant `PaymentDialogResult | undefined`.
    - `if (!result) { return; }`
    - `const sale = await firstValueFrom(this.posService.validate(currentBasket.id, result.request));`
    - Après le `this.loadBasket()` de succès : `if (result.printInvoice) { void this.autoPrintInvoice(sale.id); }` — **best-effort, ne pas `await` de façon bloquante avant le chargement du panier ; ne jamais laisser une erreur d'impression empêcher le chargement du panier neuf** (AC 3).
    - Nouvelle méthode privée `autoPrintInvoice(saleId: number)` : `try { await firstValueFrom(this.posService.printInvoice(saleId)); this.toast.showSuccess('volunteer.pos.invoice.success'); } catch (err) { ...même dispatch 422 → a4PrinterUnavailable / sinon generic que l'ancienne méthode printInvoice()... }`. Réutiliser `HttpErrorResponse` + `extractErrorType` déjà importés.
    - **Garde `basketCancelled()`** dans `autoPrintInvoice` par cohérence avec les autres continuations post-`await` (Story 4.6) : si un `basket-cancelled` est arrivé entre-temps, ne pas écraser le toast persistant d'annulation par un toast de succès/erreur d'impression. *(Nuance : la Story 4.6 avait justement décidé que l'impression restait fonctionnelle après annulation pour le bouton 30 s. Ici l'impression est un effet automatique de la validation, pas une action volontaire post-annulation — on protège donc le toast persistant. Documenter ce choix dans un commentaire.)*
  - [x] `pluribourse-frontend/src/app/services/pos.service.ts` : `printInvoice(saleId): Observable<void>` **inchangé** (déjà présent, Story 4.5) ; `validate(basketId, dto): Observable<Sale>` **inchangé** (le corps reste `ValidateBasketRequest`).

- [x] **T5 — Suppression du bouton temporaire 30 s (AC 5, 6)**
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` (UPDATE) : supprimer
    - la constante `INVOICE_BUTTON_VISIBLE_MS` et son commentaire,
    - les signaux `lastSale`, `printingInvoice`,
    - le champ `invoiceButtonTimer` et le `destroyRef.onDestroy(() => clearTimeout(this.invoiceButtonTimer))` du constructeur (si le constructeur devient vide, le retirer),
    - la méthode `showInvoiceButton(sale)` et l'ancienne méthode publique `printInvoice()` (remplacée par la privée `autoPrintInvoice`),
    - l'appel `this.showInvoiceButton(sale)` dans `openPaymentDialog()`.
    - Conserver l'import `Sale` (toujours utilisé : type de retour de `validate()` + `autoPrintInvoice(sale.id)`). Conserver l'import `HttpErrorResponse` (utilisé par `autoPrintInvoice`). Si `MatIconModule`/`MatButtonModule` ne servaient qu'au bouton facture, vérifier leur usage résiduel avant de les retirer des `imports` (probablement encore utilisés par le panier).
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html` (UPDATE) : supprimer entièrement le bloc `@if (lastSale(); as sale) { <button class="print-invoice-btn" ...> }`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss` (UPDATE) : supprimer la règle `.print-invoice-btn`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` (UPDATE) :
    - Supprimer les tests devenus caducs (identifiés par grep `lastSale` / `printInvoice` / `.print-invoice-btn` / `advanceTimersByTime(30000)`) : « shows the invoice button after a successful validation », « hides the invoice button 30 seconds after », « a scan while the invoice button is visible never clears it », « printing the invoice calls the service... », « 422 dedicated toast », « réimpression sans disparition du bouton ».
    - Si un test de la Story 4.6 asserte que `lastSale`/`printInvoice` **survivent** à un `basket-cancelled`, retirer uniquement ces assertions (le reste du test — garde anti-résurrection — reste valable).
    - **Nouveaux tests** : (a) confirmer le paiement avec `printInvoice: true` dans le résultat du dialogue → `posService.printInvoice(sale.id)` appelé une fois + toast succès ; (b) avec `printInvoice: false` → `posService.printInvoice` **jamais** appelé ; (c) `printInvoice(saleId)` rejette avec 422 `invalid-printer-selection` → toast `a4PrinterUnavailable`, et le panier neuf est tout de même chargé (`loadBasket` appelé) ; (d) un 500 sur l'auto-impression → toast `generic`, vente non impactée. Adapter le mock `paymentDialogServiceMock.open` pour renvoyer `{ request: {...}, printInvoice: <bool> }`.

- [x] **T6 — Écran « Liste des ventes » (AC 10-16)**
  - [x] `pluribourse-frontend/src/app/models/pos.model.ts` (UPDATE) : ajouter
    ```typescript
    export interface SaleListItem {
      id: number;
      soldAt: string;           // ISO datetime
      cashier: string;
      paymentMethod: PaymentMethod;
      total: number;
      currency: string;
    }
    export interface SaleListFilter {
      dateFrom?: string;        // ISO datetime (from <input type="datetime-local"> → append seconds if needed)
      dateTo?: string;
      cashier?: string;
      page: number;
      size: number;
      sort?: string;            // e.g. "soldAt,desc"
    }
    // Réutiliser PageResponse<T> de models/seller.model.ts
    export interface SaleListPageResponse { page: PageResponse<SaleListItem>; }
    ```
  - [x] `pluribourse-frontend/src/app/services/pos.service.ts` (UPDATE) : ajouter
    ```typescript
    listSales(filter: SaleListFilter): Observable<SaleListPageResponse> {
      let params = new HttpParams()
        .set('page', filter.page).set('size', filter.size);
      if (filter.dateFrom) { params = params.set('dateFrom', filter.dateFrom); }
      if (filter.dateTo) { params = params.set('dateTo', filter.dateTo); }
      if (filter.cashier) { params = params.set('cashier', filter.cashier); }
      if (filter.sort) { params = params.set('sort', filter.sort); }
      return this.http.get<SaleListPageResponse>('/api/pos/sales', { params });
    }
    listCashiers(): Observable<string[]> {
      return this.http.get<string[]>('/api/pos/sales/cashiers');
    }
    ```
    (`printInvoice(saleId)` déjà présent — réutilisé tel quel pour la réimpression.)
  - [x] `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.ts` + `.html` (fichier séparé, **jamais** de template inline) + `.scss` + `.spec.ts` (NEW) — **structure calquée sur `features/catalog/item-catalog.component.ts`** :
    - Signaux : `sales`, `totalElements`, `pageIndex`, `pageSize = 50`, `isLoading`, `error`, `cashiers` (peuplé via `posService.listCashiers()` au `ngOnInit`), filtres `dateFromFilter`/`dateToFilter` (string, liés à `<input type="datetime-local">`), `cashierFilter` (string | null), `sortField`/`sortDirection`, `reprintInFlightId` (number | null — verrou anti-double-clic par ligne, AC 14).
    - `loadPage(page)` avec garde `requestSequence` contre les réponses obsolètes (copie de `ItemCatalogComponent.loadPage`), construit le `SaleListFilter`, appelle `posService.listSales(...)`.
    - Filtres date/caissier : rechargent immédiatement au `change` (pas de debounce nécessaire, ce ne sont pas des champs texte libres — cf. Story 6.1 : debounce réservé aux champs texte).
    - `MatSortModule` / `mat-sort-header` sur les 4 colonnes, ids **exactement** `soldAt`, `user.username`, `paymentMethod`, `total` (doivent matcher `ALLOWED_SORT_FIELDS` backend — même contrainte que `ItemCatalogComponent`, documenter dans le HTML). `matSortDisableClear` pour le cycle asc→desc→asc (patron Story 6.1).
    - `MatPaginator` (`[disabled]="isLoading()"`), tous les filtres `[disabled]="isLoading()"`.
    - Réimpression : méthode `async reprint(saleId)` — garde `if (this.reprintInFlightId() !== null) return;`, `this.reprintInFlightId.set(saleId)`, `try { await firstValueFrom(this.posService.printInvoice(saleId)); toast.showSuccess('volunteer.pos.invoice.success'); } catch (err) { ...dispatch 422 invalid-printer-selection → a4PrinterUnavailable / sinon generic... } finally { this.reprintInFlightId.set(null); }`. Bouton de ligne `[disabled]="reprintInFlightId() !== null"`.
    - Formatage : date/heure via `DatePipe` (`| date:'short'` ou `'dd/MM/yyyy HH:mm'` — vérifier la locale enregistrée dans l'app ; le projet utilise ngx-translate, pas forcément `registerLocaleData` — si `DatePipe` n'est pas déjà utilisé ailleurs, un format ISO tronqué manuel est acceptable). Montant via la clé i18n `volunteer.sales.columns.totalFormat` = `"{{ total }} {{ currency }}"` (même patron que `catalog.columns.priceFormat` / `volunteer.pos.basket.priceFormat`). Moyen de paiement via `volunteer.sales.paymentMethod.CASH|CHECK|CARD` (ou réutiliser des clés existantes si `volunteer.pos.payment.method.*` conviennent — vérifier : `cash`/`check`/`card` en minuscules existent déjà).
    - États : `EmptyStateComponent` (`volunteer.sales.empty`), `SkeletonRowComponent` (chargement), `NotificationInlineComponent` (`error()` : `no-active-edition` → `volunteer.sales.error.noActiveEdition`, sinon `volunteer.sales.error.load`). Copier le dispatch d'erreur de `ItemCatalogComponent.loadPage`.
  - [x] `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts` (UPDATE) : ajouter
    ```typescript
    {
      path: 'sales',
      canActivate: [salePhaseGuard],
      loadComponent: () =>
        import('./sales/sales-list.component').then((m) => m.SalesListComponent),
    },
    ```
    Guard `salePhaseGuard` (déjà importé dans ce fichier) — miroir de la route `pos`. *(Voir Dev Notes § Question ouverte : faut-il aussi autoriser POST_SALE ?)*
  - [x] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE) : dans le bloc `@if (isVolunteer())`, sous l'entrée `/volunteer/pos` (elle-même conditionnée `phase === 'SALE'`), ajouter une entrée identique en structure pour `/volunteer/sales`, même condition `@if (currentEdition()?.phase === 'SALE')`, icône `receipt_long`, label `{{ 'nav.volunteer.sales' | translate }}`.
    - **Ne pas** ajouter `/volunteer/sales` à `PHASE_BOUND_VOLUNTEER_PATHS` dans `app-layout.component.ts` **sauf** si on veut que le bénévole soit auto-rebasculé vers son landing quand la phase quitte Vente (cohérent avec `/volunteer/pos` qui y est). → **Décision : l'ajouter** à `PHASE_BOUND_VOLUNTEER_PATHS`, comportement cohérent avec la caisse (l'écran n'a plus de sens hors Vente si on garde le guard `salePhaseGuard`).
  - [x] `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.spec.ts` (NEW) : chargement initial, pagination, filtre date, filtre caissier (+ peuplement du sélecteur via `listCashiers`), tri asc/desc, réimpression (succès + toast, 422 → toast dédié, verrou anti-double-clic), états vide/chargement/erreur (`no-active-edition` et générique), garde `requestSequence` contre réponse obsolète.

- [x] **T7 — i18n**
  - [x] `pluribourse-frontend/public/i18n/fr.json` + `en.json` (UPDATE) :
    - Sous `volunteer.pos.payment`, ajouter `"printInvoice": "Imprimer la facture"` / `"Print invoice"`.
    - Nouveau namespace `volunteer.sales` :
      ```
      volunteer.sales.title
      volunteer.sales.columns.soldAt / .cashier / .paymentMethod / .total / .actions / .totalFormat
      volunteer.sales.filters.dateFrom / .dateTo / .cashier / .allCashiers
      volunteer.sales.reprint            (libellé du bouton de ligne)
      volunteer.sales.paymentMethod.CASH / .CHECK / .CARD   (si non réutilisées depuis volunteer.pos.payment.method)
      volunteer.sales.empty
      volunteer.sales.error.noActiveEdition
      volunteer.sales.error.load
      ```
    - `nav.volunteer.sales` = `"Liste des ventes"` / `"Sales list"`.
    - Les clés `volunteer.pos.invoice.success` / `volunteer.pos.invoice.error.generic` / `volunteer.pos.invoice.error.a4PrinterUnavailable` existent déjà (Story 4.5) — **réutilisées** pour l'auto-impression (T4) et la réimpression (T6), ne pas les dupliquer.
  - [x] Backend `messages_fr.properties` / `messages_en.properties` : **aucun changement** — le PDF facture (`print.invoice.*`) est inchangé, seul le déclenchement évolue.

### Vérification finale

- [x] `./mvnw -q test` (suite backend complète) — 0 échec, aucune régression sur `InvoicePrintingIT`, `PosBasketIT`, `SaleConcurrencyIT`, `Daily/EditionReportPrintingIT`.
- [x] `npm test` dans `pluribourse-frontend/` — 0 échec, aucune régression sur `pos-page.component.spec.ts`, `payment-dialog.component.spec.ts`, `app-layout.component.spec.ts`.
- [x] `npm run build` (production frontend) — 0 erreur TypeScript, `sales-list.component` apparaît comme chunk lazy séparé.
- [x] `./mvnw -q clean package` — BUILD SUCCESS.
- [x] Couverture ≥ 80 % sur les fichiers nouveaux/modifiés (`SaleListService`, `sales-list.component.ts`).

### Review Findings

_Revue de code adverse (bmad-code-review, 2026-09-01) — 3 couches parallèles (Blind Hunter, Edge Case Hunter, Acceptance Auditor). Aucune violation d'AC. 0 decision-needed, 4 patch, 5 defer, 11 rejetés comme bruit / faux positifs / choix déjà tranchés par la story._

- [x] [Review][Patch] Javadoc `SaleListFilterDto` dit « exact match » alors que le filtre caissier utilise `equalsIgnoreCase` [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListFilterDto.java:6] — **corrigé** : javadoc alignée sur « case-insensitive match on `Sale.user.username` ».
- [x] [Review][Patch] `findAllByEditionIdForList` sans départage de pagination — `ORDER BY s.soldAt DESC` seul [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java:38] — **corrigé** : `ORDER BY s.soldAt DESC, s.id DESC` (+ javadoc). Deux ventes au même `soldAt` (postes concurrents) ont désormais un ordre déterministe. NB : `findAllByEditionIdForCatalog` de la 6.1 garde la même lacune latente sur le tri par nom — non touché.
- [x] [Review][Patch] Bouton « Réimprimer » désactivé sur **toutes** les lignes au lieu de la seule ligne en vol [pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.html:70] — **corrigé** : `[disabled]="reprintInFlightId() === sale.id"` (conforme au commentaire « keyed by row » ; le verrou anti-double-clic global reste assuré par la garde dans `reprint()`).
- [x] [Review][Patch] Format `datetime-local` sans secondes jamais exercé en test E2E [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleListIT.java:262] — **corrigé** : `@Order(7)` envoie désormais aussi des bornes `dateFrom`/`dateTo` sans secondes (`"2999-01-01T00:00"`), prouvant que `@DateTimeFormat(iso = DATE_TIME)` accepte le format brut du champ `datetime-local`.

- [x] [Review][Defer] Frontend écrit l'index de page **demandé**, pas le `page.number` clampé renvoyé par le serveur [pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.ts:159] — deferred, pre-existing : identique à `ItemCatalogComponent.loadPage:170` (Story 6.1, déjà revue) ; la story impose la copie structurelle. Correctif éventuel à porter sur les deux fichiers (`result.page.number`).
- [x] [Review][Defer] `validateSort` valide le champ de tri mais pas la direction [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/SaleListService.java:92] — deferred, pre-existing : `sort=total,garbage` passe au `FilterService`. Copie verbatim de `ItemCatalogService.validateSort` (Story 6.1), même comportement partout.
- [x] [Review][Defer] Toutes les ventes de l'édition chargées en mémoire à chaque appel `/pos/sales` [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/SaleListService.java:47] — deferred, pre-existing : filtrage/tri/pagination en mémoire sur un `JOIN FETCH` complet, patron 6.1 imposé par `architecture.md` (§ Pagination — `FilterService.filterData()` obligatoire pour les listes). Le volume de ventes peut dépasser celui du catalogue → à noter au backlog V2.
- [x] [Review][Defer] Montants affichés sans 2 décimales fixes (« 5 € » au lieu de « 5.00 € ») [pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.html:73] — deferred, pre-existing : `totalFormat = "{{ total }} {{ currency }}"` est le patron i18n de toute l'app (`catalog.columns.priceFormat`, `volunteer.pos.basket.priceFormat`). Le corriger est un changement transverse, hors périmètre 4.7.
- [x] [Review][Defer] `@RequestParam` date non parsable → 400 non-RFC7807 qui reste collé dans le filtre [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java:64] — deferred, pre-existing : `MethodArgumentTypeMismatchException` non mappée par `GlobalExceptionHandler` ; atteignable seulement depuis un navigateur sans `datetime-local` natif. Lacune de handler préexistante, pas propre à cette story.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`ItemCatalogService` + `ItemCatalogComponent` (Story 6.1)** sont le **gabarit direct** de l'écran « Liste des ventes » : filtrage manuel en mémoire (`Stream` + prédicats) sur la liste complète chargée en un `JOIN FETCH`, puis délégation tri+pagination à `FilterService.filterData()` (JPageFlow) avec `filterParams` laissé `null`. Reprendre `clampPage()`, `validateSort()` + whitelist `ALLOWED_SORT_FIELDS`, la garde `requestSequence` frontend, les états vide/chargement/erreur (`EmptyStateComponent` / `SkeletonRowComponent` / `NotificationInlineComponent`), le tri `MatSortModule` + `matSortDisableClear`. **Ne pas** inventer un autre patron.
- **`PosInvoicePrintService` / `InvoiceRenderer` / `DocumentPrintService.buildInvoiceJob` / `PrintQueueService` / `PrinterSelectionService` (Story 4.5)** portent déjà toute la génération PDF + file A4 + sélection imprimante. Cette story **retire une garde** (appartenance → édition active) et **change le déclencheur** côté frontend. Elle ne touche ni le renderer, ni la file, ni le contenu du PDF, ni les clés `print.invoice.*`.
- **`SaleRepository.findAllByEditionId` (Story 5.4)** existe mais sans `JOIN FETCH s.user` — ajouter `findAllByEditionIdForList` plutôt que de modifier la méthode existante (utilisée par `ReportService.getEditionReport`, ne pas risquer de régression sur son plan de requête).
- **`FilterService.filterData()` (JPageFlow, `com.jPageFlow.utils`)** est **obligatoire** pour tout endpoint de liste paginée/filtrable (architecture.md § « Directives »). Version projet : **1.7.0** (corrige ARCH-005, le tri `BigDecimal` fonctionne — cf. Story 6.1 Change Log). Ne pas changer la version.
- **`volunteer.pos.invoice.*` (i18n, Story 4.5)** — clés succès / erreur générique / erreur imprimante A4 déjà en place, FR+EN. Réutilisées par l'auto-impression et la réimpression.
- **`PHASE_BOUND_VOLUNTEER_PATHS` + effet réactif dans `AppLayoutComponent` (Story 1.13 / correctifs 2026-08-24)** gèrent déjà le rebasculement d'un bénévole quand la phase change. Ajouter `/volunteer/sales` à cette liste (comportement cohérent avec `/volunteer/pos`).

### Décision (confirmée) : « poste de caisse » = compte bénévole caissier (`Sale.user`)

Le sprint change proposal parle d'un filtre « par date/heure et **poste** de caisse ». **Confirmé avec l'utilisateur (2026-09-01) :** un « poste » est simplement le PC d'un caissier, sur lequel il est loggé avec **son propre compte bénévole** et travaille comme tel — il n'y a pas, et il n'y aura pas, d'entité station distincte du compte. `Sale.user` (`@ManyToOne` non nul, garanti au niveau schéma) **est** le poste.

Le filtre « poste » est donc un filtre **par caissier** (`Sale.user.username`), avec un sélecteur peuplé depuis `GET /pos/sales/cashiers` (usernames distincts des caissiers ayant ≥ 1 vente sur l'édition active).

Affichage du caissier : **`username`** (toujours renseigné, `unique`, `NOT NULL`) — confirmé suffisant par l'utilisateur, pas de repli `prénom nom`. Cohérent avec ce que le bénévole voit à la connexion.

### Borne haute du filtre date — décision utilisateur : **les deux bornes incluses**

`soldAt >= dateFrom AND soldAt <= dateTo` (`!isBefore(dateFrom)` et `!isAfter(dateTo)`). C'est la lecture naturelle d'une plage choisie explicitement par l'utilisateur. Une borne vide = pas de contrainte de ce côté (`filter.dateX() == null`). Avec un sélecteur `datetime-local` (précision minute), `dateTo = 2026-06-12T18:00` inclut une vente à `18:00:00` mais pas à `18:00:30` — comportement attendu et acceptable pour une précision minute.

`<input type="datetime-local">` produit `"2026-06-12T14:30"` (sans secondes). Spring `@DateTimeFormat(iso = DATE_TIME)` sur un `LocalDateTime` accepte `"2026-06-12T14:30"` **et** `"2026-06-12T14:30:00"` — vérifier au test ; si le format sans secondes pose problème, normaliser côté frontend en suffixant `:00` avant l'appel.

**`ReportService` — ne rien changer (tranché 2026-09-01).** L'utilisateur avait évoqué d'y aligner les bornes ; après discussion (le `<= dayEnd` littéral double-compterait une vente de minuit pile dans deux rapports journaliers, régression sur un rapport financier `done`), **option (a) retenue : `ReportService.getDailyReport` reste inchangé**. Son intervalle semi-ouvert `[minuit J, minuit J+1[` capture déjà sémantiquement « tout le jour J, extrémités comprises ». Cette story ne touche pas `ReportService`.

### Fuseau horaire

`Sale.soldAt` est un `LocalDateTime` (pas d'offset), écrit via `LocalDateTime.now()` sur le serveur (`PosBasketService.validate`). Les bornes du filtre sont interprétées dans le **même** repère (heure serveur, sans conversion). C'est cohérent avec tout le reste du module (rapport journalier, `soldAt` affiché tel quel). Ne pas introduire de gestion de timezone dans cette story.

### `PosInvoicePrintService` — retrait de la garde d'appartenance

Avant (Story 4.5) : IDOR strict — seul `sale.getUser()` peut réimprimer, sinon 404. FR-108 ouvre explicitement la réimpression à **tout bénévole caissier**. On remplace la garde par un **scoping à l'édition active** : `sale.getEdition().getId() != editionService.getActiveEdition().getId()` → 404 `sale-not-found` (même code que « n'existe pas », on ne révèle pas l'existence d'une vente d'une autre édition). Raisons :

1. Toutes les autres features sont scopées à l'édition active (`getActiveEdition()`) — la liste des ventes elle-même ne montre que l'édition active, donc un `saleId` d'une autre édition ne peut venir que d'une manipulation directe.
2. Évite qu'un `saleId` deviné expose les articles/montants d'une édition passée via le PDF.
3. `NoActiveEditionException` (404) est déjà levée par `getActiveEdition()` s'il n'y a aucune édition active — comportement acceptable (pas d'édition active ⇒ pas de réimpression), cohérent avec le reste.

La signature perd `Long userId` (plus utilisée). Le contrôleur perd `Authentication` / `userId(authentication)`. **Vérifier par grep tous les appelants** de `printInvoice(` (prod + tests) avant de committer.

### Suppression du bouton 30 s — points de vigilance

- La Story 4.5 tirait la fenêtre de 30 s d'`EXPERIENCE.md` (§ Panier POS — état post-validation, et micro-interaction « Post-validation POS — facture disponible »). Ces deux passages d'`EXPERIENCE.md` deviennent **obsolètes** après cette story. Comme pour `epics.md` (jamais amendé pour les stories du sprint change proposal 2026-08-24), on **ne modifie pas** `EXPERIENCE.md` dans le cadre du dev — c'est une dérive documentaire connue, à noter dans le Change Log, pas un blocage.
- La Story 4.6 (`done`) a explicitement décidé que `lastSale`/`printInvoice` **survivaient** à un `basket-cancelled` (« l'impression reste délibérément fonctionnelle après annulation »). Ce raisonnement disparaît avec le bouton. Les gardes `basketCancelled()` de `onScan`/`removeItem`/`removeLot`/`openPaymentDialog`/`loadBasket` **ne changent pas** — elles protègent le panier et le toast persistant, pas le bouton facture. Seules les **assertions de test** sur la survie du bouton sont retirées.
- Ne pas retirer l'import `Sale` de `pos-page.component.ts` : `validate()` renvoie un `Sale` et `autoPrintInvoice(sale.id)` en a besoin.

### Auto-impression best-effort — ne jamais casser la validation

`PosBasketService.validate` est une transaction atomique qui marque les articles vendus et supprime le panier (Story 4.2). Elle **ne doit pas** être modifiée. L'auto-impression est un appel HTTP **séparé** (`POST /pos/sales/{saleId}/invoice/print`) déclenché par le frontend **après** un `validate()` réussi. Séquence dans `openPaymentDialog()` :

1. `validate()` → 200 + `Sale`.
2. `this.lastScanIssue.set(null)`.
3. `await this.loadBasket()` — charge le panier neuf (inchangé).
4. Si `result.printInvoice` : `void this.autoPrintInvoice(sale.id)` — non bloquant, sa propre gestion d'erreur, son propre toast. Une erreur ici (422, 500, réseau) **n'annule rien** : la vente est faite, le panier neuf est là.

C'est exactement le patron « best-effort découplé » déjà utilisé pour l'impression groupée (Story 5.6) et la génération du bilan à la clôture (Story 2.7).

### `SaleListService` — copie de `ItemCatalogService`, pas de factorisation

`clampPage` et `validateSort` sont dupliqués depuis `ItemCatalogService` (2 méthodes statiques courtes). **Ne pas** extraire une classe utilitaire partagée : CLAUDE.md décourage l'abstraction prématurée, `ItemCatalogService` est un fichier stable déjà revu (Story 6.1), et le couplage d'un service `pos` à un helper `item` serait un mauvais signal d'architecture. `InvalidSortFieldException` : réutiliser celle de `org.pluribourse.domain.item.exception` si l'importer depuis `pos` reste acceptable (elle est générique — « champ de tri inconnu »), sinon en créer une jumelle dans `pos/exception` (mêmes status 400 + type `invalid-sort-field`). Trancher à l'implémentation ; privilégier la réutilisation si l'exception est vraiment agnostique du domaine.

### Route et guard de l'écran

`/volunteer/sales` sous `volunteerRoutes`, `canActivate: [salePhaseGuard]` — miroir exact de `/volunteer/pos`. `SalesListComponent` dans `features/volunteer/sales/` (composant **spécifique bénévole**, contrairement à `features/catalog/` qui est cross-rôle : le sprint change proposal cible « tout bénévole caissier »). L'admin y accède quand même via l'URL directe (l'endpoint `GET /pos/sales` est ouvert aux deux rôles) mais **aucune** entrée sidebar admin n'est ajoutée par cette story — l'admin a déjà `/admin/reports` pour l'agrégat.

**Confirmé avec l'utilisateur (2026-09-01) :** on part sur cette configuration minimale (Vente uniquement, entrée sidebar bénévole en phase Vente, pas d'accès Post-vente, pas d'entrée admin) — « on verra à l'usage ». Un élargissement ultérieur (Post-vente, sidebar admin) sera une évolution incrémentale, pas un prérequis de cette story.

### Project Structure Notes

- Backend : tout dans `org.pluribourse.domain.pos.{service,controller,dto,mapper,repository}` — cohérent avec la table de correspondance fonctionnalité→structure d'`architecture.md` (F4 — POS). `PosSaleController` gagne 2 `@GetMapping` (liste + caissiers) à côté du `@PostMapping` d'impression existant — même ressource `/pos/sales`, pas de nouveau contrôleur.
- Frontend : `features/volunteer/sales/` (nouveau dossier, 4 fichiers). Modifs sur `models/pos.model.ts`, `services/pos.service.ts`, `features/volunteer/pos/{pos-page,payment-dialog}.component.*` (+ specs), `features/volunteer/volunteer.routes.ts`, `layout/app-layout/app-layout.component.{ts,html}`, `public/i18n/{fr,en}.json`.
- Aucune migration Liquibase. Aucune entité modifiée (`Sale`/`User` inchangés).

### Fichiers à lire avant modification

- `pluribourse-backend/.../domain/pos/service/PosInvoicePrintService.java`, `controller/PosSaleController.java` (UPDATE — cœur de la Partie C)
- `pluribourse-backend/.../domain/pos/repository/SaleRepository.java` (UPDATE — ajout `findAllByEditionIdForList`)
- `pluribourse-backend/.../domain/pos/entity/Sale.java`, `domain/user/entities/User.java` (référence — champs disponibles pour le DTO)
- `pluribourse-backend/.../domain/item/service/ItemCatalogService.java` (référence directe — gabarit à copier pour `SaleListService`, y compris `clampPage`/`validateSort`)
- `pluribourse-backend/.../domain/item/controller/ItemCatalogController.java` (référence — `@Validated` + `@Min`/`@Max` sur `page`/`size`)
- `pluribourse-backend/.../domain/report/service/ReportService.java` (référence de contexte uniquement — **ne PAS calquer** son intervalle semi-ouvert `soldAt < dayEnd` : le filtre de cette story utilise les **deux bornes incluses**, cf. Dev Notes § Borne haute du filtre date. `ReportService` reste inchangé.)
- `pluribourse-backend/.../domain/edition/service/EditionService.java` (référence — `getActiveEdition()` lève `NoActiveEditionException`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (UPDATE — `@Order(10)` à inverser), `domain/item/ItemCatalogIT.java` (référence — gabarit de `SaleListIT`), `domain/pos/PosBasketIT.java` (référence — scan/validate via `MockMvc` pour produire de vraies `Sale`)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.{ts,html,scss,spec.ts}` (UPDATE — lire intégralement : `openPaymentDialog()`, `showInvoiceButton()`, `printInvoice()`, gardes `basketCancelled()`)
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.{ts,html}`, `payment-dialog.service.ts` (UPDATE — case à cocher, type de retour)
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.{ts,html}` (référence directe — gabarit de `SalesListComponent`)
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts` (UPDATE — nouvelle route)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.{ts,html}` (UPDATE — entrée sidebar + `PHASE_BOUND_VOLUNTEER_PATHS`)
- `pluribourse-frontend/src/app/models/pos.model.ts`, `models/seller.model.ts` (référence — `PageResponse<T>` à réutiliser), `services/pos.service.ts`
- `pluribourse-frontend/public/i18n/{fr,en}.json` (UPDATE — `volunteer.pos.payment.printInvoice`, namespace `volunteer.sales.*`, `nav.volunteer.sales` ; `volunteer.pos.invoice.*` déjà présent)
- `pluribourse-frontend/src/app/shared/http-error.util.ts` (référence — `extractErrorType`)

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#Points 5 & 6 — Impression de la facture acheteur] — FR-107, FR-108, FR-040 amendé ; décisions validées (case cochée par défaut, réimpression ouverte à tout caissier, écran Liste des ventes filtrable date/poste, suppression du bouton 30 s)
- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#5. Plan de transmission] — « UX pour l'écran "Liste des ventes" avant chiffrage dev » : le design de cet écran n'a pas été produit (pas d'entrée dans EXPERIENCE.md) → décisions tranchées dans cette story, questions ouvertes en fin de document
- [Source: _bmad-output/implementation-artifacts/4-5-impression-de-la-facture-acheteur.md] — `PosInvoicePrintService`, `InvoiceRenderer`, endpoint `POST /pos/sales/{saleId}/invoice/print`, bouton 30 s (`INVOICE_BUTTON_VISIBLE_MS`), IDOR 404, 422 `invalid-printer-selection`, piège `LazyInitializationException` sur le thread de file (valeurs simples extraites avant le job)
- [Source: _bmad-output/implementation-artifacts/4-6-gestion-du-changement-de-phase-dans-le-composant-pos-cote-client.md] — gardes `basketCancelled()` anti-résurrection, décision « `lastSale`/`printInvoice` survivent volontairement à l'annulation » (rendue caduque ici), `PHASE_BOUND_VOLUNTEER_PATHS`
- [Source: _bmad-output/implementation-artifacts/6-1-catalogue-articles-liste-filtrable-triable.md] — gabarit filtrage en mémoire + `FilterService.filterData()`, `clampPage`/`validateSort`/whitelist, garde `requestSequence`, `MatSortModule`+`matSortDisableClear`, états `EmptyState`/`SkeletonRow`/`NotificationInline`, JPageFlow 1.7.0 (ARCH-005 corrigé)
- [Source: _bmad-output/implementation-artifacts/5-3-rapport-de-ventes-journalier-admin.md via ReportService] — contexte du choix Q3 : son intervalle **semi-ouvert** `soldAt < dayEnd` est délibérément **non repris** ici (filtre 4.7 = deux bornes incluses) ; `ReportService` reste inchangé
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/**, domain/item/service/ItemCatalogService.java, domain/report/service/ReportService.java, domain/edition/service/EditionService.java] — lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java, domain/item/ItemCatalogIT.java] — lus (structure et ordres de test)
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/**, features/catalog/item-catalog.component.ts, layout/app-layout/**, services/pos.service.ts, models/pos.model.ts] — lus intégralement
- [Source: _bmad-output/planning-artifacts/architecture.md#Pagination — JPageFlow, #Directives, #Concurrence — POS, #Frontière d'Impression] — `FilterService.filterData()` obligatoire pour les listes paginées ; `Page<T>` sérialisé directement ; pas de garde de phase requise sur une `Sale`

## Questions — statut

1. **« Poste de caisse » = compte bénévole.** ✅ **Résolu (2026-09-01).** Un poste = le PC d'un caissier, loggé sur son propre compte bénévole ; pas d'entité station distincte, ni maintenant ni prévue. Filtre par `Sale.user.username`.
2. **Accès de l'écran.** ✅ **Résolu (2026-09-01).** Configuration minimale retenue : `/volunteer/sales`, `salePhaseGuard`, entrée sidebar bénévole en phase Vente uniquement, pas d'accès Post-vente, pas d'entrée admin. « On verra à l'usage. »
3. **Bornes du filtre date : les deux incluses.** ✅ **Résolu (2026-09-01).** Story 4.7 : `soldAt >= dateFrom AND soldAt <= dateTo`. `ReportService.getDailyReport` (Story 5.3) : **inchangé** — option (a) retenue, son intervalle semi-ouvert `[minuit J, minuit J+1[` capture déjà « tout le jour J » et un `<=` littéral introduirait un double-comptage de minuit pile.
4. **Affichage date/heure + libellé caissier.** ✅ **Résolu (2026-09-01).** `username` suffit (pas de `prénom nom`), `soldAt` en format court.

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (bmad-dev-story)

### Debug Log References

- `./mvnw test` — 551 tests, 0 failure / 0 error (dont `SaleListIT` 16, `InvoicePrintingIT` 14).
- `./mvnw -q clean package` — BUILD SUCCESS (`target/pluribourse-0.0.1-SNAPSHOT.jar`).
- `npm test` (frontend) — 686 tests, 0 échec (67 fichiers). `sales-list.component.spec.ts` 14, `payment-dialog.component.spec.ts` +2, `pos-page.component.spec.ts` remanié (6 tests bouton 30 s retirés → 5 nouveaux tests auto-impression).
- `npm run build` — 0 erreur TS ; `sales-list-component` = chunk lazy séparé (7.79 kB raw / 2.52 kB transfer).
- Parité clés i18n fr.json ↔ en.json vérifiée (0 clé orpheline de part et d'autre).

### Completion Notes List

**Partie C (T1) — retrait garde d'appartenance + scoping édition active**
- `PosInvoicePrintService.printInvoice` : signature `(Long saleId, Long userId, HttpSession)` → `(Long saleId, HttpSession)`. Garde `sale.getUser().getId().equals(userId)` remplacée par `sale.getEdition().getId().equals(editionService.getActiveEdition().getId())` — même 404 `sale-not-found` IDOR-safe. `EditionService` injecté (champ `final`, `@RequiredArgsConstructor`). Aucun `new PosInvoicePrintService(` manuel dans le code (prod + tests) : grep confirmé, aucun constructeur Lombok à réaligner. JavaDoc de classe + commentaire inline de garde réécrits (motif = édition active, n° d'AC → 8).
- `PosSaleController` : `printInvoice` n'a plus `Authentication` ni la méthode privée `userId(...)` (supprimée). `@Validated` ajouté **sur la classe** (pour le 422 des `@Min`/`@Max`).

**Partie D backend (T2) — endpoint liste des ventes**
- `SaleRepository` : `findAllByEditionIdForList` (`JOIN FETCH s.user`, `ORDER BY s.soldAt DESC`) + `findDistinctCashierUsernamesByEditionId` (DISTINCT, trié).
- Nouveaux : `SaleListItemDto`, `SaleListPageDto` (`Page<T>` direct, patron `ItemCatalogPageDto`), `SaleListFilterDto`, `SaleListMapper` (MapStruct, `toDto(Sale, String currency)` — `cashier` ← `sale.user.username`, `currency` ← param ; MapStruct multi-source a suffi, pas de mapping manuel), `pos/exception/InvalidSortFieldException` (jumelle du message « catalog » de `item/exception`).
- `SaleListService` : copie structurelle de `ItemCatalogService` — filtres mémoire (`dateFrom`/`dateTo` **bornes incluses** via `!isBefore`/`!isAfter`, `cashier` `equalsIgnoreCase`), `FilterService.filterData()` (JPageFlow 1.7.0), `clampPage`/`validateSort` recopiés à l'identique (pas de factorisation — CLAUDE.md). `ALLOWED_SORT_FIELDS = {soldAt, user.username, paymentMethod, total}`. `getCashiers()` s'appuie sur la requête DISTINCT dédiée.
- `PosSaleController` : 2 `@GetMapping` ajoutés (`/pos/sales` liste + `/pos/sales/cashiers`) sur le contrôleur existant, pas de nouveau contrôleur. Pas de `@PreAuthorize` (règle globale `SecurityConfig` : authentifié non-SELLER → ADMIN + VOLUNTEER OK). Pas de collision de route (verbes GET vs POST, `/cashiers` littéral).

**Tests backend (T3)**
- `InvoicePrintingIT` : `@Order(3)` sélectionne aussi une imprimante A4 pour `volunteer2` ; `@Order(10)` renommé/inversé (`printing_another_volunteers_sale_is_now_allowed_for_any_cashier`, 404→204) ; nouveau `@Order(14)` — vente d'une édition **clôturée** puis nouvelle édition active → `POST .../invoice/print` → 404 `sale-not-found` (E2E, pas de mock `EditionService`).
- `SaleListIT` (NEW, 16 scénarios `@Order`) : 4 vraies ventes via `PosBasketController` par 2 caissiers, moyens de paiement variés, `Thread.sleep(10)` pour étaler `soldAt`. Couvre : liste par défaut (`soldAt DESC`), accès ADMIN, filtre caissier + AC 16 (requêtes concurrentes séquentielles), **filtre date bornes incluses** (`dateFrom = plus ancienne` et `dateTo = plus récente` renvoient tout ; `dateFrom=dateTo` sur l'instant exact renvoie la vente), tri `total` asc/desc (BigDecimal), tri `user.username`, tri `paymentMethod` (enum, sans exception), `sort` inconnu → 400 `invalid-sort-field`, `page=-1`/`size=999` → 422 `validation-failed`, clamp page au-delà de la dernière, `/cashiers` DISTINCT trié, SELLER → 403, plus d'édition active → 404 `no-active-edition`.

**Partie A (T4) — case à cocher**
- `pos.model.ts` : `PaymentDialogResult { request: ValidateBasketRequest; printInvoice: boolean }` — `ValidateBasketRequest` **intouché** (contrat HTTP pur, AC 4).
- `PaymentDialogComponent` : `MatCheckboxModule`, `printInvoice = signal(true)`, `DialogRef<PaymentDialogResult | undefined>`, `confirm()` ferme avec `{ request: {...}, printInvoice: this.printInvoice() }`. HTML : `<mat-checkbox class="print-invoice-option">` juste au-dessus de `.dialog__actions`. `payment-dialog.service.ts` : type de retour aligné.
- `pos-page.component.openPaymentDialog()` : `validate(basketId, result.request)` ; après le `loadBasket()` de succès, `if (result.printInvoice) { void this.autoPrintInvoice(sale.id); }` (best-effort, non bloquant). Nouvelle méthode privée `autoPrintInvoice(saleId: number)` avec garde `basketCancelled()` (commentée : effet automatique ≠ action volontaire post-annulation, contrairement au bouton 30 s de la Story 4.6) + dispatch 422 `invalid-printer-selection` → `a4PrinterUnavailable` / sinon `generic`, succès → `success`. Import `Sale` retiré (plus référencé — `autoPrintInvoice` prend un `number`, cf. snippet de la story).

**Partie B (T5) — suppression du bouton 30 s**
- `pos-page.component.ts` : supprimés `INVOICE_BUTTON_VISIBLE_MS` + commentaire, signaux `lastSale`/`printingInvoice`, champ `invoiceButtonTimer`, constructeur (devenu vide), `showInvoiceButton()`, ancienne méthode publique `printInvoice()`. Gardes `basketCancelled()` de `onScan`/`removeItem`/`removeLot`/`openPaymentDialog`/`loadBasket` **strictement inchangées** (Story 4.6). HTML : bloc `@if (lastSale())` retiré. SCSS : règle `.print-invoice-btn` retirée. Clé i18n `volunteer.pos.invoice.button` (devenue orpheline) retirée fr + en.
- Spec : 6 tests bouton 30 s retirés ; mocks `paymentDialogServiceMock.open` adaptés à `{ request, printInvoice }` (y compris le test de régression « late validate error » de la Story 4.6, dont seule la forme du mock change). 5 nouveaux tests : (a) `printInvoice: true` → `printInvoice(sale.id)` 1× + toast succès ; (b) `printInvoice: false` → jamais appelé ; (c) 422 → toast `a4PrinterUnavailable` + panier neuf chargé ; (d) 500 → toast `generic`, vente intacte ; + un test « aucun bouton facture rendu après validation ». Helper `flush()` (macrotask) car `autoPrintInvoice` est détaché (`void`).

**Partie D frontend (T6) — écran Liste des ventes**
- `pos.model.ts` : `SaleListItem`, `SaleListFilter`, `SaleListPageResponse` (réutilise `PageResponse<T>` de `seller.model.ts`, import ajouté). `pos.service.ts` : `listSales()` (`HttpParams`, params optionnels omis si vides) + `listCashiers()`.
- `features/volunteer/sales/sales-list.component.{ts,html,scss,spec.ts}` (NEW) — calqué sur `ItemCatalogComponent` : signaux `sales`/`totalElements`/`pageIndex`/`pageSize=50`/`isLoading`/`error`/`cashiers`/`dateFromFilter`/`dateToFilter`/`cashierFilter`/`sortField`/`sortDirection`/`reprintInFlightId`, `loadPage(page)` + garde `requestSequence`, filtres date/caissier rechargent au `change` (pas de debounce), `MatSortModule` + `matSortDisableClear`, ids de tri `soldAt`/`user.username`/`paymentMethod`/`total` (commentés HTML — doivent matcher le backend), `MatPaginator` + filtres `[disabled]="isLoading()"`. `reprint(saleId)` : verrou `reprintInFlightId`, dispatch 422/générique, `finally` reset. Date affichée via `DatePipe` `'dd/MM/yyyy HH:mm'` (format numérique, pas de `registerLocaleData` requis) ; montant via `volunteer.sales.columns.totalFormat` ; moyen de paiement via `volunteer.sales.paymentMethod.<ENUM>`. États vide/chargement/erreur mutualisés (`EmptyStateComponent` icône `receipt_long` / `SkeletonRowComponent` / `NotificationInlineComponent`). 14 tests spec.
- `volunteer.routes.ts` : route `sales` (`canActivate: [salePhaseGuard]`, `loadComponent` lazy) — miroir de `pos`.
- `app-layout.component.html` : entrée sidebar `/volunteer/sales` (icône `receipt_long`, `@if phase === 'SALE'`, sous `/volunteer/pos`). `app-layout.component.ts` : `/volunteer/sales` ajouté à `PHASE_BOUND_VOLUNTEER_PATHS` (décision de la story). Pas d'entrée sidebar admin (l'admin garde `/admin/reports`).

**i18n (T7)** — `volunteer.pos.payment.printInvoice`, namespace complet `volunteer.sales.*` (title, columns, filters, paymentMethod.CASH/CHECK/CARD, reprint, empty, error.noActiveEdition/load), `nav.volunteer.sales`, fr + en. `volunteer.pos.invoice.*` (Story 4.5) réutilisées, non dupliquées. `messages_*.properties` backend : aucun changement.

**Dérive documentaire connue (non bloquante, notée au Change Log)** — `EXPERIENCE.md` (§ Panier POS état post-validation, micro-interaction « facture disponible ») reste inchangé : même convention que `epics.md` non amendé pour les stories du sprint change proposal 2026-08-24.

**Couverture** — `SaleListService` : toutes les branches (filtres date bornes, cashier blank/null/match, whitelist tri, clampPage) exercées par les 16 scénarios `SaleListIT`. `sales-list.component.ts` : chargement, pagination, filtres, tri (3 directions), `requestSequence` obsolète, reprint (succès / 422 / générique / verrou double-clic), 3 états d'erreur, échec `listCashiers` — 14 tests spec. ≥ 80 % atteint sur les deux fichiers neufs.

### File List

**Backend — modifiés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java`

**Backend — nouveaux**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListPageDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/SaleListFilterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/mapper/SaleListMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/InvalidSortFieldException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/SaleListService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleListIT.java`

**Frontend — modifiés**
- `pluribourse-frontend/src/app/models/pos.model.ts`
- `pluribourse-frontend/src/app/services/pos.service.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.html`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.service.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

**Frontend — nouveaux**
- `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.html`
- `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/sales/sales-list.component.spec.ts`

## Change Log

- 2026-09-01 — bmad-code-review : 3 revues parallèles (Blind Hunter, Edge Case Hunter, Acceptance Auditor). Aucune violation d'AC (17/17 satisfaites par le code). 0 decision-needed, 4 patch **appliqués**, 5 defer (consignés dans `deferred-work.md`), 11 rejetés (faux positifs `seller1`/`@Order(14)`, ou choix déjà tranchés par la story : Post-vente, fuseau horaire, borne minute, tri enum par ordinal). Patchs : (1) javadoc `SaleListFilterDto` « exact » → « case-insensitive » ; (2) `SaleRepository.findAllByEditionIdForList` gagne le départage `, s.id DESC` (pagination déterministe sur `soldAt` égal) ; (3) `sales-list.component.html` bouton réimpression désactivé par ligne (`=== sale.id`) et non globalement ; (4) `SaleListIT @Order(7)` couvre le format `datetime-local` sans secondes. `SaleListIT` 16/16, `InvoicePrintingIT` 14/14, suite frontend 686/686 — toutes vertes. Statut → done.
- 2026-09-01 — bmad-dev-story : implémentation complète (T1→T7). Backend : `PosInvoicePrintService` garde d'appartenance → scoping édition active (404 IDOR-safe), `PosSaleController` `@Validated` sur la classe + 2 `@GetMapping` (`/pos/sales`, `/pos/sales/cashiers`), `SaleListService` calqué sur `ItemCatalogService` (+ DTOs, mapper MapStruct, `InvalidSortFieldException` jumelle, 2 requêtes `SaleRepository`). Tests : `InvoicePrintingIT @Order(10)` inversé + `@Order(14)` (autre édition → 404), `SaleListIT` NEW (16 scénarios). Frontend : case « Imprimer la facture » cochée par défaut + auto-impression best-effort découplée, suppression totale du bouton 30 s (`lastSale`/`printingInvoice`/`invoiceButtonTimer`/`INVOICE_BUTTON_VISIBLE_MS`/`showInvoiceButton`/`printInvoice()`), écran `SalesListComponent` (route `/volunteer/sales` + entrée sidebar phase Vente + `PHASE_BOUND_VOLUNTEER_PATHS`), i18n `volunteer.sales.*` + `volunteer.pos.payment.printInvoice` fr/en. 551 tests backend verts, 686 tests frontend verts, `mvnw clean package` + `npm run build` OK.
- 2026-09-01 — `EXPERIENCE.md` volontairement **non modifié** (§ Panier POS — état post-validation, micro-interaction « Post-validation POS — facture disponible » désormais obsolètes) : même convention que `epics.md` non amendé pour les stories du sprint change proposal 2026-08-24. Dérive documentaire connue, à traiter hors dev.

- 2026-09-01 — bmad-create-story (validate) : revalidation à froid contre le code réel. Corrections appliquées : (1) `@Validated` déplacé sur la **classe** `PosSaleController` (sur la méthode, les `@Min`/`@Max` ne produisent pas le 422 attendu — `GlobalExceptionHandler` : `ConstraintViolationException` → 422, `MethodArgumentNotValidException` → 400) ; (2) références résiduelles au patron `soldAt < dayEnd` (borne haute exclusive) de `ReportService` neutralisées — le filtre 4.7 est à deux bornes incluses (Q3) ; (3) fallback « test unitaire `PosInvoicePrintService` avec `EditionService` mocké » supprimé (Mockito interdit hors composant externe, CLAUDE.md) — cas « autre édition » en E2E uniquement ; (4) chemin `pos.service.ts` corrigé (`src/app/services/`, pas `features/volunteer/pos/`) ; (5) `getCashiers()` s'appuie sur une requête dédiée `findDistinctCashierUsernamesByEditionId` plutôt que recharger toutes les ventes ; (6) commentaire inline IDOR de `PosInvoicePrintService` à réécrire (n° d'AC → 8, motif = scoping édition active) ; (7) `InvalidSortFieldException` : créer une jumelle dans `pos/exception` (message « catalog » figé inexact pour la liste des ventes).
- 2026-09-01 — bmad-create-story (clarifications utilisateur) : Q1 tranchée — « poste » = compte bénévole caissier (pas d'entité station, jamais prévue), filtre par `Sale.user.username` confirmé. Q2 tranchée — écran en phase Vente uniquement, pas de Post-vente ni d'entrée admin (« on verra à l'usage »). Q3 tranchée — **les deux bornes du filtre date incluses** (`soldAt >= dateFrom AND soldAt <= dateTo`) ; `ReportService` **inchangé** (option a : son intervalle semi-ouvert capture déjà tout le jour, un `<=` littéral double-compterait minuit pile). Q4 tranchée — `username` suffit pour la colonne caissier.
- 2026-09-01 — bmad-create-story : story créée (Sprint Change Proposal 2026-08-24, points 5 & 6 regroupés). Analyse exhaustive du code réel : le « poste de caisse » du sprint change proposal n'a aucun support modèle (aucune entité station — `Sale` ne porte que `user`) → filtre implémenté par caissier (`Sale.user.username`) + endpoint `GET /pos/sales/cashiers` ; décision tranchée sans blocage, question ouverte n°1 posée pour un éventuel vrai concept de poste. UX de l'écran « Liste des ventes » jamais produite (confirmé absente d'EXPERIENCE.md, le sprint change proposal la renvoyait à un handoff UX) → écran calqué structurellement sur `ItemCatalogComponent`/`ItemCatalogService` (Story 6.1) : filtrage en mémoire + `FilterService.filterData()` (JPageFlow 1.7.0), `clampPage`/`validateSort`/whitelist, `MatSortModule`, états vide/chargement/erreur mutualisés. Partie C : retrait de la garde d'appartenance sur `PosInvoicePrintService.printInvoice` remplacée par un scoping à l'édition active (404 IDOR-safe pour une vente d'une autre édition) ; `InvoicePrintingIT @Order(10)` à inverser (404→204). Partie B : suppression complète du bouton temporaire 30 s (`lastSale`/`invoiceButtonTimer`/`INVOICE_BUTTON_VISIBLE_MS`/`showInvoiceButton`/`printInvoice()`), gardes `basketCancelled()` de la Story 4.6 explicitement préservées, EXPERIENCE.md laissé tel quel (dérive documentaire connue, même convention que epics.md pour ce sprint change proposal). Partie A : case « Imprimer la facture » cochée par défaut dans `PaymentDialogComponent`, auto-impression best-effort découplée post-`validate()` (aucun champ ajouté à `ValidateBasketDto`/`ValidateBasketRequest`, nouveau type `PaymentDialogResult`). Statut → ready-for-dev.
