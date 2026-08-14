---
baseline_commit: 579511dc3d1f3ea864ec0f05c2e16675c5f15596
---

# Story 5.1: Flux de solde des vendeurs

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole (et, pour consultation, un administrateur),
I want voir la liste des vendeurs non soldés d'une édition et les solder ou marquer leur reversement comme non réclamé,
so that tous les reversements soient comptabilisés avant la fin de l'événement.

**Point d'entrée du sprint.** L'ordre naturel du sprint status pointait vers la Story 2.7 (backlog), mais elle est bloquée : elle dépend de cette story ET d'une story de génération du bilan d'édition PDF (Epic 5, backlog) — aucune des deux n'existait encore. 5.1 est la première story réalisable de l'Epic 5 (choix confirmé avec l'utilisateur le 2026-08-14) ; elle passe l'Epic 5 en `in-progress`.

**Périmètre : full-stack.** Aucune entité de reversement n'existe encore (`org.pluribourse.domain.payout` est un nouveau package). Cette story crée le socle (entité `Settlement`, service, endpoints) que la Story 2.7 (clôture — FR-096, auto-marquage "Non réclamé" des vendeurs restants) et les Stories 5.2–5.6 (bilan PDF, rapports) consommeront ensuite.

## Acceptance Criteria

1. **Liste des vendeurs non soldés (FR-053, FR-095).** Étant donné que le bénévole ou l'administrateur navigue vers `/volunteer/settlement` ou `/admin/settlement`, quand la page se charge, alors tous les vendeurs de l'édition active sont listés avec nom, prénom, montant dû et statut ; les colonnes téléphone et email ne sont affichées que dans la vue admin (FR-095) ; un filtre permet d'isoler les non soldés (FR-053).
2. **Solde en dessous du montant net — avertissement, pas de blocage (FR-051).** Étant donné que le montant saisi est strictement inférieur au montant net calculé, quand le bénévole clique sur « Solder », alors un avertissement « Le montant saisi (X,XX €) est inférieur au montant dû (Y,YY €). » s'affiche et le bénévole peut tout de même confirmer.
3. **Solde au-dessus du montant net — bloqué (FR-051).** Étant donné que le montant saisi est strictement supérieur au montant net calculé, quand le bénévole clique sur « Solder », alors la validation est bloquée avec un message d'erreur et le bénévole doit corriger le montant.
4. **Confirmation du solde (FR-051).** Étant donné qu'un solde est confirmé pour un vendeur, quand l'opération se termine, alors son statut passe à Soldé et il disparaît du filtre « Non soldés ».
5. **Non réclamé (FR-052).** Étant donné qu'un vendeur ne souhaite pas récupérer son reversement, quand le bénévole clique sur « Non réclamé » et confirme la boîte de dialogue (« Le montant de X,XX EUR sera transféré aux recettes de l'association. Cette action est irréversible. »), alors le montant total dû est enregistré et le vendeur est retiré de la liste des non soldés.
6. **Impression du bilan de vente — HORS PÉRIMÈTRE de cette story.** epics.md liste, pour un vendeur soldé, un bouton « Imprimer le bilan de vente » (UX-DR22) mettant un PDF en file A4. Ce PDF n'existe pas encore — sa génération est le sujet exclusif de la Story 5.2 (`Génération du bilan de vente PDF`, backlog). Ajouter un bouton non fonctionnel maintenant violerait « pas d'implémentation à moitié faite » (CLAUDE.md). **Décision actée avec l'utilisateur le 2026-08-14** : le bouton d'impression est entièrement différé à la Story 5.2, qui devra livrer both le renderer PDF et le bouton dans la même story (même patron que la Story 4.5 — bouton + renderer livrés ensemble). Ne pas ajouter de bouton, ni actif ni désactivé, dans cette story.

## Tasks / Subtasks

### Backend

- [x] **Migration Liquibase (AC 1, 2, 4, 5)**
  - [x] `pluribourse-backend/src/main/resources/db/changelog/024-settlements.xml` (NEW) — nouvelle table `settlements` :
    ```xml
    <createTable tableName="settlements">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="seller_profile_id" type="BIGINT">
            <constraints nullable="false" foreignKeyName="fk_settlements_seller_profile"
                         references="seller_profiles(id)" deleteCascade="true"/>
        </column>
        <column name="status" type="VARCHAR(20)">
            <constraints nullable="false"/>
        </column>
        <column name="amount" type="DECIMAL(10,2)">
            <constraints nullable="false"/>
        </column>
        <column name="settled_at" type="DATETIME" defaultValueComputed="CURRENT_TIMESTAMP">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <addUniqueConstraint tableName="settlements" columnNames="seller_profile_id"
                          constraintName="uk_settlements_seller_profile"/>
    ```
    Un vendeur sans ligne `settlements` est implicitement **UNSETTLED** — ne pas créer de ligne par défaut à la création du vendeur (Story 3.1, hors périmètre). `deleteCascade` : même convention que `fk_sales_edition`/`fk_baskets_edition` (021-pos-baskets.xml).
  - [x] `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (UPDATE) : ajouter `<include file="db/changelog/024-settlements.xml"/>` après la ligne `023-ignored-printer-name.xml`.

- [x] **`PhaseGuard` — nouvelle phase (AC 1, 2, 3, 4, 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` (UPDATE) : ajouter
    ```java
    /**
     * Settlement (story 5.1) is only reachable while the edition is in Post-vente — a
     * server-side mirror of the frontend's settlementPhaseGuard, since the client is never
     * trusted alone (same rationale as requireSalePhase).
     */
    public static void requirePostSalePhase(Edition edition) {
        if (edition.getPhase() != PhaseType.POST_SALE) {
            throw new SettlementNotAllowedException();
        }
    }
    ```
    Mettre à jour le Javadoc de la classe pour ajouter `SettlementService` à la liste des consommateurs (même style que la liste existante).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/SettlementNotAllowedException.java` (NEW) : `extends BusinessException`, 422, code `settlement-not-allowed`. Même patron exact que `SalePhaseRequiredException` (même package — toutes les exceptions de `PhaseGuard` y sont co-localisées, y compris celles utilisées hors du domaine `item`, cf. `SalePhaseRequiredException` consommée par `pos`).
  - [x] **Limite connue, non bloquante — ne pas tenter de la corriger dans cette story.** `EditionService.getActiveEdition()` (`repository.findFirstByPhaseIn(PhaseType.ACTIVE)`) exclut `CLOSED` de `PhaseType.ACTIVE` — dès que l'édition est clôturée, `getActiveEdition()` lève `NoActiveEditionException` et **toute** page qui en dépend (admin comme bénévole, y compris `/admin/sellers`, `/admin/catalog` déjà existantes) devient inaccessible. `EXPERIENCE.md` annonce `/admin/settlement` accessible « Post-vente · Clôturée », mais cette accessibilité en phase Clôturée n'est **techniquement pas atteignable** avec l'architecture actuelle — ce n'est pas une régression introduite ici, c'est une limite déjà présente pour toutes les pages admin. Cette story se limite donc à **Post-vente uniquement**, backend et frontend. Résoudre l'accès post-clôture (probablement via une résolution d'édition par ID plutôt que « édition active ») est hors périmètre — à traiter par la Story 2.7 ou une story dédiée.

- [x] **`ItemPricing` — extraire le calcul de reversement net, déjà dupliqué une fois (AC 2, 3, 4)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` (UPDATE) : ajouter, à côté de `computeTotal`
    ```java
    public static BigDecimal computeNetPayout(BigDecimal total, BigDecimal commissionRate) {
        BigDecimal commission = total.multiply(commissionRate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return total.subtract(commission).setScale(2, RoundingMode.HALF_UP);
    }
    ```
    Formule identique, caractère pour caractère, à la méthode privée actuellement dupliquée dans `DepositSlipRenderer.computeNetPayout` — **ne pas réinventer une variante**, ce serait un second calcul de commission divergent (contrainte projet : `BigDecimal` partout, une seule vérité de calcul).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java` (UPDATE) : supprimer la méthode privée `computeNetPayout`, remplacer son unique appel par `ItemPricing.computeNetPayout(total, commissionRate)`. Comportement inchangé (même formule) — aucun test de `DepositSlipPrintingIT`/`InvoicePrintingIT` ne doit être affecté.

- [x] **`ItemRepository` — items vendus d'une édition (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE) : nouvelle méthode
    ```java
    /**
     * Settlement list (story 5.1): sold items across the whole edition, grouped by seller in
     * memory afterwards. JOIN FETCH on lot only — sellerProfile is read only via its already-
     * cached id (item.getSellerProfile().getId()) to key the grouping, which never triggers a
     * lazy load on a Hibernate proxy (same reasoning as ScanResultDto's scan query).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId AND i.sold = true")
    List<Item> findAllByEditionIdAndSoldTrue(@Param("editionId") Long editionId);
    ```

- [x] **Package `org.pluribourse.domain.payout` (AC 1, 2, 3, 4, 5)**
  - [x] `entity/SettlementStatus.java` (NEW) — enum `UNSETTLED, SETTLED, UNCLAIMED`. **`UNSETTLED` n'est jamais persisté** — il ne représente que l'absence de ligne `Settlement` pour un vendeur, calculé au niveau service. Réutilisé tel quel côté DTO (pas de second enum dupliqué).
  - [x] `entity/Settlement.java` (NEW) :
    ```java
    @Entity
    @Table(name = "settlements")
    @Getter @Setter @NoArgsConstructor
    public class Settlement {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @OneToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "seller_profile_id", nullable = false, unique = true)
        private SellerProfile sellerProfile;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private SettlementStatus status;

        @Column(nullable = false, precision = 10, scale = 2)
        private BigDecimal amount;

        @Column(name = "settled_at", nullable = false)
        private LocalDateTime settledAt;
    }
    ```
  - [x] `repository/SettlementRepository.java` (NEW) :
    ```java
    public interface SettlementRepository extends JpaRepository<Settlement, Long> {
        Optional<Settlement> findBySellerProfileId(Long sellerProfileId);
        List<Settlement> findAllBySellerProfileEditionId(Long editionId);
    }
    ```
  - [x] `dto/SettlementDto.java` (NEW, record) : `Long sellerId, String firstName, String lastName, String phone, String email, BigDecimal amountDue, SettlementStatus status`. **Construit manuellement dans le service, pas de mapper MapStruct** — c'est un DTO agrégat (vendeur + montant calculé + statut optionnel), même précédent que `SaleDto`/`BasketDto`/`LotGroupDto` dans `pos` (déjà construits `new XxxDto(...)` à la main, pas de `@Mapper`).
  - [x] `dto/SettleDto.java` (NEW, record) : `@NotNull @DecimalMin(value = "0.00") BigDecimal amount`.
  - [x] `exception/SellerAlreadySettledException.java` (NEW) : `extends BusinessException`, 409, code `seller-already-settled`. Même patron que `ItemAlreadyInBasketException`.
  - [x] `exception/InvalidSettlementAmountException.java` (NEW) : `extends BusinessException`, 422, code `invalid-settlement-amount`. Même patron que `InvalidAmountGivenException`.
  - [x] `service/SettlementService.java` (NEW) — un seul service, pas de split `PayoutService`/`SettlementService` (architecture.md en suggère deux ; un seul suffit pour le périmètre réel de cette story, cohérent avec CLAUDE.md « pas d'abstraction prématurée » — voir Dev Notes § Écarts) :
    ```java
    @Service
    @RequiredArgsConstructor
    public class SettlementService {

        private final SellerRepository sellerRepository;
        private final SettlementRepository settlementRepository;
        private final ItemRepository itemRepository;
        private final EditionService editionService;

        @Transactional(readOnly = true)
        public List<SettlementDto> getSettlements() {
            Edition edition = editionService.getActiveEdition();
            PhaseGuard.requirePostSalePhase(edition);

            List<SellerProfile> sellers = sellerRepository.findAllByEditionId(edition.getId());
            Map<Long, Settlement> settlementBySellerId = settlementRepository.findAllBySellerProfileEditionId(edition.getId()).stream()
                    .collect(Collectors.toMap(s -> s.getSellerProfile().getId(), s -> s));
            Map<Long, List<Item>> soldItemsBySellerId = itemRepository.findAllByEditionIdAndSoldTrue(edition.getId()).stream()
                    .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));

            return sellers.stream().map(seller -> {
                BigDecimal total = ItemPricing.computeTotal(soldItemsBySellerId.getOrDefault(seller.getId(), List.of()));
                BigDecimal amountDue = ItemPricing.computeNetPayout(total, edition.getCommissionRate());
                Settlement settlement = settlementBySellerId.get(seller.getId());
                SettlementStatus status = settlement != null ? settlement.getStatus() : SettlementStatus.UNSETTLED;
                return new SettlementDto(seller.getId(), seller.getFirstName(), seller.getLastName(),
                        seller.getPhone(), seller.getEmail(), amountDue, status);
            }).toList();
        }

        @Transactional
        public SettlementDto settle(Long sellerId, SettleDto dto) {
            Edition edition = editionService.getActiveEdition();
            PhaseGuard.requirePostSalePhase(edition);
            SellerProfile seller = requireSellerOfEdition(sellerId, edition);
            requireNotAlreadySettled(seller);

            BigDecimal amountDue = computeAmountDue(seller, edition);
            if (dto.amount().compareTo(amountDue) > 0) {
                throw new InvalidSettlementAmountException();
            }
            return persistSettlement(seller, SettlementStatus.SETTLED, dto.amount(), amountDue);
        }

        @Transactional
        public SettlementDto markUnclaimed(Long sellerId) {
            Edition edition = editionService.getActiveEdition();
            PhaseGuard.requirePostSalePhase(edition);
            SellerProfile seller = requireSellerOfEdition(sellerId, edition);
            requireNotAlreadySettled(seller);

            BigDecimal amountDue = computeAmountDue(seller, edition);
            return persistSettlement(seller, SettlementStatus.UNCLAIMED, amountDue, amountDue);
        }

        // ... helpers privés : requireSellerOfEdition (404 SellerNotFoundException si absent
        // OU d'une autre édition — même raisonnement IDOR que PosBasketService.requireOwnedBasket),
        // requireNotAlreadySettled (409 SellerAlreadySettledException si findBySellerProfileId
        // présent), computeAmountDue (mêmes deux lignes que dans getSettlements — factoriser),
        // persistSettlement (construit et sauvegarde le Settlement, retourne le SettlementDto).
    }
    ```
    **Pourquoi `amount` est un paramètre séparé de `amountDue` dans `persistSettlement`** : pour `settle`, le montant persisté est celui **saisi** par le bénévole (FR-051, peut être inférieur au dû) ; pour `markUnclaimed`, c'est toujours le montant dû intégral (FR-052, jamais un choix du bénévole). Ne pas fusionner les deux méthodes malgré leur ressemblance — la source du montant diffère par construction.
  - [x] `controller/SettlementController.java` (NEW) — `/settlements`, **pas de `@PreAuthorize`** (route partagée ADMIN + VOLUNTEER, même patron exact que `SellerController` à `/sellers` : la règle par défaut de `SecurityConfig` — authentifié et non SELLER — suffit, aucun `requestMatchers` dédié à ajouter) :
    ```java
    @RestController
    @RequestMapping("/settlements")
    @RequiredArgsConstructor
    public class SettlementController {
        private final SettlementService service;

        @GetMapping
        public ResponseEntity<List<SettlementDto>> getSettlements() {
            return ResponseEntity.ok(service.getSettlements());
        }

        @PostMapping("/{sellerId}/settle")
        public ResponseEntity<SettlementDto> settle(@PathVariable Long sellerId, @Valid @RequestBody SettleDto dto) {
            return ResponseEntity.ok(service.settle(sellerId, dto));
        }

        @PostMapping("/{sellerId}/unclaimed")
        public ResponseEntity<SettlementDto> markUnclaimed(@PathVariable Long sellerId) {
            return ResponseEntity.ok(service.markUnclaimed(sellerId));
        }
    }
    ```

- [x] **Backend — test E2E (AC 1–5)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java` (NEW), storyboard `@TestMethodOrder`/`@Order`, `extends IntegrationTest` (voir CLAUDE.md — philosophie E2E par les contrôleurs). Scénario suggéré :
    1. Setup : créer une édition, la faire avancer jusqu'à Vente, créer 2 vendeurs, des articles pour chacun (un standard, un en lot), vendre les articles du vendeur 1 via POS (`PosBasketService`/endpoints POS existants) pour obtenir des items `sold = true`, faire avancer l'édition en Post-vente.
    2. `GET /settlements` avant tout solde → 2 vendeurs, vendeur 1 a un `amountDue > 0` et `status = UNSETTLED`, vendeur 2 (rien vendu) a `amountDue = 0`, `status = UNSETTLED`.
    3. `POST /settlements/{vendeur1Id}/settle` avec un montant strictement inférieur au dû → 200, la réponse ne contient pas d'erreur (le warning est purement frontend, AC 2) ; re-`GET /settlements` confirme `status = SETTLED`.
    4. `POST /settlements/{vendeur2Id}/settle` avec un montant strictement supérieur au dû (même à 0 dû, tenter un montant > 0) → 422 `invalid-settlement-amount` (AC 3).
    5. `POST /settlements/{vendeur1Id}/settle` une seconde fois (déjà soldé) → 409 `seller-already-settled`.
    6. `POST /settlements/{vendeur2Id}/unclaimed` → 200, `status = UNCLAIMED`, montant enregistré = montant dû intégral (AC 5).
    7. Garde de phase : tenter `GET /settlements` avec l'édition encore en phase Vente (ou une seconde édition créée à cet effet) → 422 `settlement-not-allowed`.
    8. Vérifier que l'endpoint est accessible aussi bien avec la session `test_admin` qu'avec `volunteer1` (pas de `@PreAuthorize`, contrairement à `AdminSellerController`).
  - [x] Vérifier `DepositSlipPrintingIT` (Story 3.6, done) — aucune régression attendue après le refactor `ItemPricing.computeNetPayout`, mais à re-exécuter explicitement puisque ce fichier n'est pas modifié par cette story.

### Frontend

- [x] **Modèle & service (AC 1, 2, 3, 4, 5)**
  - [x] `pluribourse-frontend/src/app/models/settlement.model.ts` (NEW) :
    ```typescript
    // Mirrors org.pluribourse.domain.payout.entity.SettlementStatus (backend) — UNSETTLED is
    // never persisted there either, it's the "no Settlement row" case.
    export type SettlementStatus = 'UNSETTLED' | 'SETTLED' | 'UNCLAIMED';

    export interface SettlementDto {
      sellerId: number;
      firstName: string;
      lastName: string;
      phone: string;
      email: string;
      amountDue: number;
      status: SettlementStatus;
    }

    export interface SettleRequest {
      amount: number;
    }
    ```
  - [x] `pluribourse-frontend/src/app/services/settlement.service.ts` (NEW) + `.spec.ts` — `getSettlements(): Observable<SettlementDto[]>` (`GET /api/settlements`), `settle(sellerId: number, amount: number): Observable<SettlementDto>` (`POST /api/settlements/{sellerId}/settle`), `markUnclaimed(sellerId: number): Observable<SettlementDto>` (`POST /api/settlements/{sellerId}/unclaimed`). Même style que `seller.service.ts`/`item.service.ts` (HttpClient injecté, pas de logique).

- [x] **Garde de phase (AC 1)**
  - [x] `pluribourse-frontend/src/app/core/guards/settlement-phase.guard.ts` (NEW) + `.spec.ts` — même patron exact que `sale-phase.guard.ts` (`loadEditionOrRedirect`, `phase === ActivePhase.POST_SALE`, sinon `/404`). Un seul guard, réutilisé pour les DEUX routes (`/volunteer/settlement` ET `/admin/settlement`) — première route admin phase-gated de l'app (toutes les autres sont « Toutes phases », cf. EXPERIENCE.md tableau Admin), mais aucune raison technique d'écrire deux gardes identiques.

- [x] **`resolveVolunteerLandingPath` — POST_SALE change de cible (AC 1)**
  - [x] `pluribourse-frontend/src/app/models/active-phase.enum.ts` (UPDATE) — **lire la fonction en entier avant de modifier**, ses commentaires expliquent le comportement actuel qu'il faut changer :
    ```typescript
    export function resolveVolunteerLandingPath(phase: PhaseType | undefined): string {
      if (phase === ActivePhase.PREPARATION) {
        return '/printer-selection';
      }
      if (phase === ActivePhase.DEPOSIT) {
        return '/volunteer/deposit';
      }
      if (phase === ActivePhase.SALE) {
        return '/volunteer/pos';
      }
      if (phase === ActivePhase.POST_SALE) {
        return '/volunteer/settlement';
      }
      return '/404';
    }
    ```
    **Changement de comportement assumé et déjà tranché avec l'utilisateur (2026-08-14)** : avant cette story, un bénévole en Post-vente atterrissait automatiquement sur `/volunteer/deposit` (pour réimprimer un bordereau, Story 3.6 AC 7). Après ce changement, il atterrit sur `/volunteer/settlement` — `/volunteer/deposit` reste accessible en Post-vente (le guard `depositPhaseGuard` n'est pas modifié, il autorise toujours DEPOSIT et POST_SALE), mais plus par atterrissage automatique. Compensé par un lien explicite depuis la page reversements (tâche dédiée ci-dessous) — **les bénévoles n'ont aucun menu de navigation** (contrairement aux admins, cf. `app-layout.component.html`), donc sans ce lien la réimpression deviendrait injoignable en pratique.

- [x] **Routes (AC 1)**
  - [x] `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts` (UPDATE) : ajouter
    ```typescript
    {
      path: 'settlement',
      canActivate: [settlementPhaseGuard],
      loadComponent: () =>
        import('../settlement/settlement-list.component').then((m) => m.SettlementListComponent),
    },
    ```
  - [x] `pluribourse-frontend/src/app/features/admin/admin.routes.ts` (UPDATE) : ajouter la même entrée sous `path: 'settlement'`, avec `canActivate: [settlementPhaseGuard]` (première route admin phase-gated — voir Dev Notes § Écarts pour la justification de cette exception à « Toutes phases »).
  - [x] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE) : ajouter un lien sidebar admin vers `/admin/settlement` dans la section `nav.sections.management` existante (même patron exact que les liens `/admin/print-queue`/`/admin/printers` déjà en place — `routerLink`, `routerLinkActive`, `matTooltip`, icône Material Symbols `payments`). Le lien reste toujours visible (comme tous les autres liens sidebar, aucun n'est masqué selon la phase) ; naviguer dessus hors Post-vente redirige vers `/404` via le guard, comportement déjà établi ailleurs dans l'app pour une navigation directe hors phase autorisée.

- [x] **`SettlementListComponent` — composant partagé admin/bénévole (AC 1, 2, 3, 4, 5)**
  - [x] Nouveau dossier `pluribourse-frontend/src/app/features/settlement/` (sibling de `features/catalog/`, même précédent de composant unique référencé par les deux jeux de routes — `item-catalog.component.ts` est le modèle direct à suivre pour la structure du fichier, pas pour son contenu).
  - [x] `settlement-list.component.ts` (NEW) :
    - `isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN')` (`AuthService` injecté) — pilote l'affichage des colonnes Téléphone/Email (AC 1) et du lien de réimpression bordereau (visible seulement côté bénévole, voir plus bas).
    - `settlements = signal<SettlementDto[]>([])`, `isLoading`, `error` (même style `catalog`/`sellers`).
    - `statusFilter = signal<'all' | 'unsettled' | 'settled'>('unsettled')` (mockup : onglet « Non soldés » actif par défaut). Filtrage **côté client** en `computed` — pas de rechargement serveur au changement d'onglet (le mockup ne montre aucun état de chargement entre les clics de filtre, contrairement au filtre catalogue qui recharge la page) :
      ```typescript
      readonly filteredSettlements = computed(() => {
        const filter = this.statusFilter();
        return this.settlements().filter(s =>
          filter === 'all' ? true :
          filter === 'unsettled' ? s.status === 'UNSETTLED' :
          s.status !== 'UNSETTLED'
        );
      });
      ```
      Aucun AC de epics.md ne teste explicitement les onglets « Tous »/« Soldés » (seul « Non soldés » est couvert par les ACs) — comportement déduit du mockup `mock-volunteer-settlement.html`, à confirmer visuellement par l'utilisateur (voir CLAUDE.md § Interaction utilisateur, toujours proposer une vérification visuelle).
    - `openSettleForm(sellerId)`/`closeSettleForm()` : signal `openSettleFormForSellerId = signal<number | null>(null)`, `settleAmount = signal<number | null>(null)` (pré-rempli au montant dû à l'ouverture, comme le mockup `value="12,00"`).
    - `computed warningBelowDue`/`blockedAboveDue` sur `settleAmount` vs `amountDue` de la ligne concernée — **même patron exact que `PaymentDialogComponent.confirmDisabled`** (`big.js`, déjà une dépendance du projet, ne pas introduire de nouvelle lib d'arithmétique décimale). `blockedAboveDue` désactive le bouton « Valider » ; `warningBelowDue` affiche `<app-notification-inline variant="warning">` sans désactiver (AC 2 vs AC 3).
    - `confirmSettle(sellerId)` : appelle `settlementService.settle(...)`, remplace la ligne dans `settlements()` par la réponse, toast succès (`ToastService.showSuccess`), ferme le formulaire inline. Erreur 422 `invalid-settlement-amount` improbable ici (déjà bloqué côté client) mais à gérer par un toast générique si elle survient quand même (ne jamais faire confiance au seul client, cf. `PosBasketService`).
    - `confirmUnclaimed(seller)` : `ConfirmDialogService.open({ title, description: translate.instant('settlement.unclaimedDialog.description', { amount: seller.amountDue }) })` (texte exact FR-052, montant interpolé) → si confirmé, `settlementService.markUnclaimed(...)`, met à jour la ligne, toast succès.
    - Chargement initial dans `ngOnInit` (`async`/`firstValueFrom`, même style `seller-list.component.ts`), gestion `no-active-edition`/erreur générique via `extractErrorType`.
  - [x] `settlement-list.component.html` (NEW, **fichier séparé, jamais de template inline** — CLAUDE.md) : structure `card card--list` (même classes CSS que `seller-list.component.html`) + `filter-toggle`/`filter-btn` (mockup, nouvelles classes à ajouter au SCSS partagé ou au fichier du composant — vérifier si `filter-toggle` existe déjà ailleurs dans le design system avant d'en créer un nouveau). Table : colonnes Vendeur / [Téléphone / Email si `isAdmin()`] / Montant dû / Statut / Actions. Ligne de formulaire inline sous la ligne du vendeur en cours de solde (`@if (openSettleFormForSellerId() === settlement.sellerId)`), pas de `<mat-dialog>` — le mockup montre un formulaire **inline**, pas une modale (contrairement à `payment-dialog`). Bouton « Réimprimer le bordereau de dépôt » (lien `routerLink="/volunteer/deposit"`) affiché **une seule fois en haut de page, uniquement si `!isAdmin()`** (pas de `/admin/deposit` — la réimpression reste une action bénévole via la fiche vendeur, cf. Story 3.6). `<app-empty-state>` si liste vide (EXPERIENCE.md : « Aucun vendeur enregistré pour cette édition. », aucune action). `<app-skeleton-row>` pendant le chargement.
  - [x] `settlement-list.component.scss` (NEW) — classes `filter-toggle`/`filter-btn`/`settlement-form-row` reprises du mockup si aucun équivalent générique n'existe déjà dans `shared/`.
  - [x] `settlement-list.component.spec.ts` (NEW) — scénarios : chargement + rendu liste, colonnes téléphone/email visibles seulement si rôle ADMIN mocké, filtre « Non soldés » par défaut masque les vendeurs soldés, solde avec montant < dû affiche l'avertissement sans bloquer, solde avec montant > dû désactive « Valider », solde réussi met à jour la ligne et affiche le toast succès, « Non réclamé » ouvre la dialog de confirmation puis met à jour la ligne, lien réimpression bordereau absent si `isAdmin()` vrai.

- [x] **`AppLayoutComponent` — régression à corriger (AC 1)**
  - [x] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` (UPDATE) : **lire les lignes 285–345 en entier avant de modifier** — le test `'does not redirect away from /volunteer/deposit when the phase moves from Dépôt to Post-vente'` (ligne 333) encode l'**ancien** comportement de `resolveVolunteerLandingPath` et **va échouer** une fois la fonction modifiée ci-dessus (le bénévole sera désormais redirigé vers `/volunteer/settlement`, plus vers `/volunteer/deposit`). Deux changements requis :
    1. Ajouter une route stub `{ path: 'volunteer/settlement', component: StubComponent }` à côté de `{ path: 'volunteer/deposit', component: StubComponent }` (ligne ~69).
    2. Remplacer le test ligne 333 par `'redirects away from /volunteer/deposit to /volunteer/settlement once the phase moves to Post-vente'`, assertion finale `expect(router.url).toBe('/volunteer/settlement')` — même structure de test, seule l'assertion finale change.
    Optionnel mais cohérent avec les tests existants (lignes 305–331, même patron pour DEPOSIT) : ajouter un test symétrique `'redirects from /404 to /volunteer/settlement once the phase reaches Post-vente'`.

- [x] **i18n (AC 1, 2, 3, 4, 5)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) — nouvelle clé racine `settlement` (namespace unique partagé admin/bénévole, même précédent que `catalog.*` qui n'a pas de variante `admin.catalog`/`volunteer.catalog`) :
    ```json
    "settlement": {
      "title": "Reversements",
      "filters": { "all": "Tous", "unsettled": "Non soldés", "settled": "Soldés" },
      "columns": { "name": "Vendeur", "phone": "Téléphone", "email": "Email", "amountDue": "Montant dû", "status": "Statut", "actions": "Actions" },
      "status": { "UNSETTLED": "Non soldé", "SETTLED": "Soldé", "UNCLAIMED": "Non réclamé" },
      "actions": { "settle": "Solder", "unclaimed": "Non réclamé", "confirm": "Valider", "cancel": "Annuler", "reprintDepositSlip": "Réimprimer le bordereau de dépôt" },
      "form": {
        "amountLabel": "Montant remis en espèces (€)",
        "warningBelowDue": "Le montant saisi ({{amount}} €) est inférieur au montant dû ({{due}} €).",
        "errorAboveDue": "Le montant saisi ne peut pas dépasser le montant dû."
      },
      "unclaimedDialog": {
        "title": "Confirmer « Non réclamé »",
        "description": "Le montant de {{amount}} € sera transféré aux recettes de l'association. Cette action est irréversible."
      },
      "success": { "settle": "Vendeur réglé.", "unclaimed": "Montant transféré aux recettes de l'association." },
      "error": { "load": "Impossible de charger la liste des reversements.", "noActiveEdition": "Aucune édition active.", "settle": "Impossible d'enregistrer le solde.", "alreadySettled": "Ce vendeur a déjà été soldé.", "amountTooHigh": "Le montant saisi dépasse le montant dû." },
      "empty": "Aucun vendeur enregistré pour cette édition."
    }
    ```
    Réutiliser la clé `error.noActiveEdition` existante si un texte générique équivalent existe déjà ailleurs (`catalog.error.noActiveEdition`) plutôt que d'en dupliquer le libellé — vérifier avant d'écrire une nouvelle valeur.
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — même structure, traduction anglaise.
  - [x] Ajouter `"settlement": "Reversements"` sous `nav.admin.*` (fr) et l'équivalent EN, pour le libellé du lien sidebar.

### Review Findings

- [x] [Review][Patch] (résolu depuis Decision) `deleteCascade="true"` sur `fk_settlements_seller_profile` détruisait silencieusement l'historique de reversement si un `SellerProfile` est supprimé. Décision utilisateur (2026-08-14) : bloquer la suppression plutôt que cascader — retirer `deleteCascade`(FK devient RESTRICT) et étendre `SellerService.delete()` avec le même patron que `canBeDeleted`/`SellerDeletionNotAllowedException` pour refuser proprement (422) la suppression d'un vendeur ayant un `Settlement`. [024-settlements.xml, SellerService.java]
- [x] [Review][Decision] AC 2 permet de confirmer « Solder » avec un montant arbitrairement bas, y compris 0,00 €, même quand le montant dû est positif — le vendeur passe alors en `SETTLED` avec un montant de 0,00 €, un résultat quasi identique à « Non réclamé » (`UNCLAIMED`) mais sans son texte distinct ni sa portée sémantique. — Décision utilisateur (2026-08-14) : laisser tel quel, AC 2 autorise explicitement tout montant strictement inférieur au dû sans plancher ; choix produit déjà tranché par l'epic, non un bug de code (confirmé par l'Acceptance Auditor, contexte spec complet, aucune violation relevée).
- [x] [Review][Patch] Race TOCTOU sur double-solde : deux appels concurrents `settle`/`unclaimed` pour le même vendeur peuvent tous deux passer `requireNotAlreadySettled` avant qu'aucun ne committe — le second lève une `DataIntegrityViolationException` brute (500) au lieu du `SellerAlreadySettledException` (409) attendu. [SettlementService.java:~97-117]
- [x] [Review][Patch] `SettleDto.amount` n'a pas de contrainte `@Digits` sur l'échelle/précision, et `persistSettlement` ne normalise pas l'échelle avant persistance — un montant du type `3.999` passe la comparaison puis diverge silencieusement dans la colonne `DECIMAL(10,2)`. [SettleDto.java, SettlementService.java:~111-116]
- [x] [Review][Patch] `computeAmountDue` re-scanne tous les items vendus de l'édition en mémoire à chaque appel `settle`/`markUnclaimed`, alors qu'une seule requête scoping vendeur+édition+vendu suffirait. [SettlementService.java:~409-415]
- [x] [Review][Patch] Le montant saisi négatif n'est pas bloqué côté client — `blockedAboveDue` ne vérifie que la borne haute, pas `< 0` (le `min="0"` HTML n'est qu'une suggestion, pas une garde JS). [settlement-list.component.ts:~79-86]
- [x] [Review][Patch] Confirmer « Non réclamé » pendant que le formulaire de solde inline du même vendeur est ouvert laisse ce formulaire affiché alors que la ligne n'affiche plus les actions (statut ≠ `UNSETTLED`) — fermer le formulaire dans `confirmUnclaimed` si `openSettleFormForSellerId()` correspond. [settlement-list.component.ts:~143-166]
- [x] [Review][Patch] JavaDoc manquante sur `persistSettlement` expliquant la distinction `amount`/`amountDue` — les Dev Notes de la story qualifient explicitement cette distinction de non évidente (règle JavaDoc de CLAUDE.md). [SettlementService.java]
- [x] [Review][Patch] Imports incohérents dans `SettlementIT.java` : `@org.junit.jupiter.api.BeforeAll` et `org.hamcrest.Matchers.endsWith` utilisés en qualifié complet inline au lieu d'être importés comme le reste du fichier. [SettlementIT.java]
- [x] [Review][Patch] La mitigation IDOR de `requireSellerOfEdition` (404 générique si le vendeur appartient à une autre édition) n'est exercée par aucun test — ajouter un scénario `SettlementIT` avec une seconde édition/vendeur. [SettlementService.java, SettlementIT.java]
- [x] [Review][Patch] Aucun test ne vérifie la validation bean de `SettleDto` (`@NotNull`/`@DecimalMin`) sur un montant manquant ou négatif. [SettlementIT.java]
- [x] [Review][Defer] Aucune trace de l'utilisateur (bénévole/admin) ayant réalisé un solde — `Settlement` a `status`/`amount`/`settledAt` mais pas de référence à l'acteur, contrairement à `Sale` qui a `user`. Aucun AC de cette story ne l'exige ; à considérer pour une story de rapports/audit future. — deferred, pre-existing

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`ItemPricing.computeTotal`/`distinctByLot`** (`domain/item/service/ItemPricing.java`) : déjà le calcul lot-aware partagé par `PosBasketService` et `DepositSlipRenderer` — cette story l'étend avec `computeNetPayout` (actuellement dupliqué en privé dans `DepositSlipRenderer`) plutôt que d'écrire un troisième calcul de commission.
- **`PhaseGuard`** (`domain/item/service/PhaseGuard.java`) : classe utilitaire déjà cross-domaine (`item`, `pos`) — cette story y ajoute `requirePostSalePhase`, ne pas créer de garde ad hoc dans `SettlementService`.
- **`SellerRepository.findAllByEditionId`** (déjà utilisé par `SellerService.getSellers`) — réutilisé tel quel, aucune nouvelle méthode de liste de vendeurs à écrire.
- **Le patron « route partagée admin+bénévole, un seul composant »** est déjà établi par `ItemCatalogComponent` (`features/catalog/`, référencé depuis `admin.routes.ts` et `volunteer.routes.ts`) et par `SellerController`/`SellerDto` côté backend (`/sellers`, sans `@PreAuthorize`, colonnes téléphone/email toujours renvoyées — c'est le **frontend** qui les masque conditionnellement, pas deux DTOs différents). Cette story suit exactement ce double précédent : un seul `SettlementDto` toujours complet, un seul `SettlementListComponent`, masquage des colonnes piloté par `isAdmin()` côté template.
- **`big.js` (`Big`)** : déjà utilisé par `PaymentDialogComponent` pour l'arithmétique décimale frontend (montant remis, monnaie à rendre) — réutiliser cette dépendance existante pour comparer `settleAmount` à `amountDue`, ne pas introduire de nouvelle librairie ni de soustraction `number` brute (`0.1 + 0.2` classique).
- **`ConfirmDialogService`** (`shared/components/confirm-dialog/`) : déjà le mécanisme de dialog de confirmation du projet (CDK Dialog) — utilisé tel quel pour « Non réclamé », pas de nouveau composant dialog.

### Écarts par rapport à architecture.md — non bloquants, actés

- **Package/nommage.** `architecture.md` (lignes 618–625) documente `org.pluribourse.payout.*` avec deux services (`PayoutService`, `SettlementService`) et un `PayoutController`/`PayoutMapper`. Le code réel de tout le projet vit sous `org.pluribourse.domain.*` (déjà noté dans les stories précédentes — `architecture.md` a un segment de package manquant, drift déjà connu). Cette story crée `org.pluribourse.domain.payout.*`, un seul service `SettlementService` (pas de split artificiel avec `PayoutService` tant qu'aucun besoin réel ne le justifie), un seul `SettlementController` (pas `PayoutController` — nommage cohérent avec l'entité/le service), pas de mapper MapStruct (`SettlementDto` est un agrégat, même exception déjà appliquée à `SaleDto`/`BasketDto`/`LotGroupDto`).
- **Accessibilité Post-vente · Clôturée (EXPERIENCE.md, `/admin/settlement`).** Voir Task § PhaseGuard ci-dessus — limite structurelle de `EditionService.getActiveEdition()` déjà présente pour toutes les pages admin, pas une régression de cette story. Périmètre réduit à Post-vente uniquement.
- **AC 5 d'epics.md (bouton d'impression du bilan).** Différé en intégralité à la Story 5.2 — voir Acceptance Criteria § 6 ci-dessus pour le raisonnement complet, décision actée avec l'utilisateur le 2026-08-14.
- **Atterrissage volontaire `/volunteer/deposit` → `/volunteer/settlement` en Post-vente.** Changement de comportement d'une story déjà `done` (3.6) assumé avec compensation (lien de réimpression explicite) — décision actée avec l'utilisateur le 2026-08-14. Voir Task § `resolveVolunteerLandingPath`.

### Project Structure Notes

- Nouveau package backend `org.pluribourse.domain.payout` (`entity`, `repository`, `service`, `dto`, `exception`, `controller`) — première story de l'Epic 5, aucun code existant à ce niveau.
- Nouveau dossier frontend `features/settlement/` (au même niveau que `features/catalog/`, PAS sous `features/admin/` ni `features/volunteer/` — composant partagé référencé par les deux jeux de routes).
- Fichiers UPDATE en dehors du nouveau périmètre (à lire intégralement avant modification, cf. section suivante) : `PhaseGuard.java`, `ItemPricing.java`, `DepositSlipRenderer.java`, `ItemRepository.java`, `db.changelog-master.xml`, `active-phase.enum.ts`, `volunteer.routes.ts`, `admin.routes.ts`, `app-layout.component.html`, `app-layout.component.spec.ts`, `fr.json`, `en.json`.
- Aucune modification de `SellerProfile`/`SellerService`/`SellerController` — le statut de solde vit entièrement dans la nouvelle table `settlements`, pas de nouvelle colonne sur `seller_profiles`.

### Fichiers à lire avant modification

- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java`, `ItemPricing.java` (UPDATE — lire en entier)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java` (UPDATE — lire en entier, notamment `computeNetPayout` à supprimer et son unique appelant)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE — lire en entier pour respecter le style des requêtes/Javadoc existantes)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java` (référence — patron `requireOwnedBasket`/gestion d'erreurs à reproduire pour `requireSellerOfEdition`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/seller/{repository,service,controller}/*.java` (référence directe — patron route partagée sans `@PreAuthorize`)
- `pluribourse-backend/src/main/resources/db/changelog/021-pos-baskets.xml`, `db.changelog-master.xml` (référence/UPDATE)
- `pluribourse-frontend/src/app/models/active-phase.enum.ts` (UPDATE — lire la fonction et TOUS ses commentaires avant modification)
- `pluribourse-frontend/src/app/core/guards/sale-phase.guard.ts`, `deposit-phase.guard.ts` (référence directe — patron à reproduire pour `settlement-phase.guard.ts`)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` (référence — mécanique de redirection réactive) et `.spec.ts` (UPDATE — lire les lignes 285–350 en entier, un test existant doit changer)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE — patron des liens sidebar existants à reproduire)
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.ts/.html` (référence directe — seul précédent de composant partagé admin/bénévole)
- `pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.ts/.html` (référence — structure de liste paginée, classes CSS `card card--list`/`data-table`)
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts` (référence directe — patron `big.js` pour la comparaison de montants)
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts` (référence — signature `open(data)`)
- `pluribourse-frontend/src/app/services/auth.service.ts` (référence — `currentUser().role` pour `isAdmin`)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-volunteer-settlement.html` (référence directe — layout table + formulaire inline)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` (référence directe — tableaux de routes admin/bénévole, Flow 3 et Flow 5, textes exacts des dialogs/toasts)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.1] — ACs source (FR-050 à FR-053, FR-095)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.7] — dépendance explicite sur cette story (FR-052/FR-096) et sur une story de bilan PDF distincte
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] — FR-050 à FR-053 (texte normatif complet)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md] — FR-095 (page de solde comme point d'entrée F5, remplace l'ancien FR-056)
- [Source: _bmad-output/planning-artifacts/implementation-readiness-report-2026-06-12.md] — trigger d'impression du bilan réparti entre 5.1 (bouton) et 5.2 (génération), confirme la décision de différer AC 6
- [Source: _bmad-output/planning-artifacts/architecture.md#Structure des packages] — patron `payout/` (package/nommage), voir Dev Notes § Écarts pour les déviations actées
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — tableaux de routes Admin/Bénévole (accessibilité par phase), Flow 3 (Non réclamé), Flow 5 (impression puis solde), Component Patterns (récapitulatif reversement imprimable), États vides/erreurs
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-volunteer-settlement.html] — layout exact (table, filtre, formulaire inline)
- [Source: _bmad-output/implementation-artifacts/4-6-gestion-du-changement-de-phase-dans-le-composant-pos-cote-client.md] — dernière story frontend du projet, patron de niveau de détail Dev Notes à reproduire
- [Source: _bmad-output/implementation-artifacts/3-6-generation-impression-automatique-du-bordereau-de-depot-pdf.md] — AC 7 (réimpression bordereau en Post-vente), contexte de la régression `resolveVolunteerLandingPath`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/**, pos/**, seller/**, print/**] — lus intégralement pour les patrons réutilisés
- [Source: pluribourse-frontend/src/app/**] — fichiers listés en § Fichiers à lire, lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

Implémentation directe sans écart par rapport au plan de la story — les trois écarts (AC 6 différé à 5.2, atterrissage `/volunteer/settlement`, limite Post-vente·Clôturée) avaient déjà été actés avec l'utilisateur lors de `create-story`, aucun nouveau écart rencontré pendant `dev-story`. Backend : `./mvnw test` (suite complète) : 391/391 tests backend, aucune régression, `DepositSlipPrintingIT` (13/13) confirmé au vert après le refactor `ItemPricing.computeNetPayout`. Frontend : `npm test` (suite complète) : 550/550 tests frontend (59 fichiers), aucune régression. `npm run build` (production) : aucune erreur TypeScript.

### Completion Notes List

- Backend : nouveau package `org.pluribourse.domain.payout` (`Settlement`/`SettlementStatus`, `SettlementRepository`, `SettlementDto`/`SettleDto`, `SellerAlreadySettledException`/`InvalidSettlementAmountException`, `SettlementService`/`SettlementController`), migration `024-settlements.xml`. `PhaseGuard.requirePostSalePhase` + `SettlementNotAllowedException` (co-localisée dans `domain.item.exception`, même patron que `SalePhaseRequiredException`). `ItemPricing.computeNetPayout` extrait (formule identique, caractère pour caractère) et `DepositSlipRenderer` refactoré pour l'appeler — plus de calcul de commission dupliqué. `ItemRepository.findAllByEditionIdAndSoldTrue` (JOIN FETCH lot uniquement, sellerProfile lu via son id déjà en cache pour grouper sans lazy load).
- `SettlementService` : un seul service (pas de split `PayoutService`/`SettlementService` — écart acté dans la story), `requireSellerOfEdition` reproduit le raisonnement IDOR de `PosBasketService.requireOwnedBasket` (404 générique, jamais de distinction "n'existe pas" / "appartient à une autre édition"). `settle`/`markUnclaimed` partagent `computeAmountDue`/`persistSettlement` mais restent deux méthodes distinctes (source du montant persisté différente par construction — saisi vs. dû intégral).
- Nouveau test `SettlementIT` (8 scénarios, storyboard `@Order`) : liste avant tout solde (vendeur avec vente vs. vendeur sans vente), solde en dessous du dû (200, pas de blocage), solde au-dessus (422 `invalid-settlement-amount`), double solde (409 `seller-already-settled`), non réclamé (montant dû intégral), garde de phase (422 `settlement-not-allowed` hors Post-vente), accessibilité admin + bénévole sans `@PreAuthorize`.
- Frontend : `settlement.model.ts`/`settlement.service.ts` (patron `seller.service.ts`), `settlement-phase.guard.ts` (patron `sale-phase.guard.ts`, garde les deux routes `/volunteer/settlement` et `/admin/settlement`). `resolveVolunteerLandingPath` : Post-vente redirige désormais vers `/volunteer/settlement` (au lieu de `/volunteer/deposit`) — `depositPhaseGuard` inchangé, `/volunteer/deposit` reste accessible en Post-vente, juste plus l'atterrissage automatique. Lien sidebar admin `/admin/settlement` (icône `payments`, patron des liens existants).
- `SettlementListComponent` (nouveau, `features/settlement/`, partagé admin/bénévole comme `ItemCatalogComponent`) : filtre client (`computed`, pas de rechargement serveur), formulaire de solde inline (pas de dialog), `big.js` pour la comparaison montant saisi/dû (même patron que `PaymentDialogComponent.confirmDisabled`), `ConfirmDialogService` pour « Non réclamé ». Colonnes téléphone/email masquées côté template selon `isAdmin()` — un seul `SettlementDto` toujours complet côté backend. Lien de réimpression du bordereau de dépôt affiché une seule fois en haut de page, uniquement côté bénévole. Aucun bouton d'impression du bilan de vente (AC 6, différé à la Story 5.2).
- `app-layout.component.spec.ts` : route stub `/volunteer/settlement` ajoutée, test de régression `does not redirect ... Post-vente` remplacé par un test `redirects ... to /volunteer/settlement`, un test symétrique `/404 → /volunteer/settlement` ajouté.
- i18n : namespace `settlement` complet (FR+EN), clé `nav.admin.settlement`. `settlement.amountFormat` ajoutée (non listée dans les Dev Notes) pour éviter un "€" codé en dur dans le template, même patron que `catalog.columns.priceFormat`.
- Un correctif de timing découvert pendant l'écriture des tests frontend : `SettlementListComponent`'s test harness nécessitait un second `fixture.detectChanges()` après `whenStable()` pour que le tableau (dépendant du chargement asynchrone) soit effectivement peint dans le DOM avant assertion — sans impact sur le composant lui-même, uniquement le spec.

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/resources/db/changelog/024-settlements.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/SettlementNotAllowedException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/entity/SettlementStatus.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/entity/Settlement.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/repository/SettlementRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/dto/SettlementDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/dto/SettleDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/exception/SellerAlreadySettledException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/exception/InvalidSettlementAmountException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/SettlementController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java`

**Backend — UPDATE**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` — include `024-settlements.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` — `requirePostSalePhase`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` — `computeNetPayout`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java` — appelle `ItemPricing.computeNetPayout`, méthode privée dupliquée supprimée
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` — `findAllByEditionIdAndSoldTrue`, `findAllBySellerProfileIdAndSoldTrue` (revue)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/seller/service/SellerService.java` — (revue) `delete()` refuse la suppression d'un vendeur ayant un `Settlement`

**Frontend — NEW**
- `pluribourse-frontend/src/app/models/settlement.model.ts`
- `pluribourse-frontend/src/app/services/settlement.service.ts`
- `pluribourse-frontend/src/app/services/settlement.service.spec.ts`
- `pluribourse-frontend/src/app/core/guards/settlement-phase.guard.ts`
- `pluribourse-frontend/src/app/core/guards/settlement-phase.guard.spec.ts`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.scss`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts`

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/models/active-phase.enum.ts` — `resolveVolunteerLandingPath` : Post-vente → `/volunteer/settlement`
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts` — route `settlement`
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — route `settlement`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — lien sidebar `/admin/settlement`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` — route stub + test de régression Post-vente remplacé, test symétrique ajouté
- `pluribourse-frontend/public/i18n/fr.json` — namespace `settlement`, `nav.admin.settlement`
- `pluribourse-frontend/public/i18n/en.json` — namespace `settlement`, `nav.admin.settlement`

## Change Log

- 2026-08-14 — create-story : story créée après clarification avec l'utilisateur sur trois points (choix de 5.1 comme point d'entrée du sprint malgré l'ordre naturel bloqué sur 2.7 ; atterrissage Post-vente redirigé vers `/volunteer/settlement` avec lien de compensation vers le dépôt ; AC 6/bouton d'impression différé en intégralité à la Story 5.2). Statut → ready-for-dev.
- 2026-08-14 — dev-story : implémentation complète full-stack (package backend `payout`, migration 024, `ItemPricing.computeNetPayout` extrait/dédupliqué, `SettlementListComponent` partagé admin/bénévole, `resolveVolunteerLandingPath` mis à jour, i18n FR/EN). Aucun écart par rapport au plan de la story. 391/391 tests backend, 550/550 tests frontend, build de production sans erreur, aucune régression. Statut → review.
- 2026-08-14 — code-review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : Acceptance Auditor confirme 0 violation d'AC. 2 decision-needed résolues avec l'utilisateur — (1) `deleteCascade` sur `fk_settlements_seller_profile` remplacé par un blocage explicite (`SellerService.delete()` refuse la suppression d'un vendeur ayant un `Settlement`, même patron que `canBeDeleted`/`SellerDeletionNotAllowedException`) ; (2) « Solder » à 0,00 € laissé tel quel, AC 2 l'autorise explicitement sans plancher. 10 patch appliqués : race TOCTOU sur double-solde (catch `DataIntegrityViolationException` → `SellerAlreadySettledException`), `@Digits` + normalisation d'échelle sur `SettleDto.amount`, `ItemRepository.findAllBySellerProfileIdAndSoldTrue` (évite un scan complet de l'édition par vendeur), montant négatif bloqué côté client, formulaire de solde inline fermé après « Non réclamé » sur la même ligne, JavaDoc `persistSettlement`, imports `SettlementIT` nettoyés, 2 nouveaux scénarios `SettlementIT` (IDOR cross-édition, validation bean `SettleDto`), 2 nouveaux tests `settlement-list.component.spec.ts`. 1 defer documenté dans `deferred-work.md` (pas de trace de l'acteur ayant réalisé un solde — aucun AC ne l'exige). 9 rejetés comme bruit (dont plusieurs faux positifs du Blind Hunter vérifiés contre le code source réel : import `RoundingMode` toujours utilisé, test `EMPTY` fidèle au comportement réel de `loadEdition()`, PII/absence de `@PreAuthorize` conformes au patron déjà établi par `SellerController`). 393/393 tests backend, 552/552 tests frontend, build de production sans erreur, aucune régression. Statut → done.
