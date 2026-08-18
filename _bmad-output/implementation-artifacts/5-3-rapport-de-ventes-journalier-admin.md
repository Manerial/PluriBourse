---
baseline_commit: 6dac095697a2cc8e1266735a98ead45ae601e97a
---

# Story 5.3: Rapport de ventes journalier (Admin)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want générer un bilan des ventes journalier en phase Vente,
so that je puisse suivre les recettes et la performance des ventes au cours de l'événement.

**Point d'entrée du sprint.** L'ordre naturel du sprint status pointait vers la Story 2.7 (backlog), mais elle reste bloquée : elle dépend de la Story 5.1 (done) ET d'une story de génération du bilan d'édition PDF (Story 5.4, Epic 5, toujours backlog) — dépendance documentée dans sprint-status.yaml. La Story 5.3 est la story suivante réalisable de l'Epic 5, sans aucune dépendance flaguée dans epics.md.

**Périmètre : full-stack, nouveau package `report`.** Aucun package `org.pluribourse.domain.report` n'existe encore — architecture.md (§ Structure des packages, ligne 625) l'anticipe déjà (`ReportController`, `ReportService`, DTOs `DailySummaryDto`/`EditionSummaryDto`/`OutstandingSellerDto`) mais ces noms sont indicatifs, pas normatifs (même écart déjà constaté en Story 5.1 pour `PayoutController`/`PayoutService`, jamais implémentés tels quels). Cette story crée le sous-ensemble minimal : `AdminReportController`, `ReportService`, `DailySalesReportDto`, plus `DailySalesReportPrintService` (patron `SettlementReportPrintService`, Story 5.2) et un nouveau renderer `DailyReportRenderer` (patron `SettlementReportRenderer`, dans `domain.print.service` comme les trois renderers existants). Story 5.4 (bilan d'édition) et 5.5 (page `/admin/reports` multi-sections) réutiliseront ce même package `report` sans le restructurer.

## Acceptance Criteria

1. **Contenu du bilan journalier (FR-054, FR-094).** Étant donné que l'édition est en phase Vente, quand l'admin consulte/actualise le bilan journalier, alors il couvre la journée calendaire en cours (minuit à minuit, fuseau du serveur — l'instance est auto-hébergée mono-serveur, aucune gestion multi-fuseau existante ailleurs dans le projet) et affiche : nombre d'articles vendus aujourd'hui, nombre d'articles invendus, recettes brutes journalières, commission journalière perçue par l'association, ventilation des recettes par moyen de paiement (espèces, chèque, carte).
2. **PDF (FR-057).** Étant donné que le bilan est généré en PDF via OpenPDF 3.0.0 (bouton « Imprimer »), alors il utilise la langue des documents de l'édition (`Edition.documentLanguage`, jamais la préférence de l'utilisateur connecté — même patron que `SettlementReportRenderer`/`InvoiceRenderer`) et contient les mêmes données que la vue écran de l'AC 1. Mis en file d'attente pour impression A4 (même mécanisme que Stories 5.1/5.2 : sélection d'imprimante en session, `PrintQueueService`), pas de téléchargement direct.
3. **Accès admin uniquement (FR-058).** Étant donné qu'un bénévole tente d'accéder à `GET/POST /admin/reports/daily`, alors l'accès est refusé avec un 403 (`@PreAuthorize("hasRole('ADMIN')")` au niveau contrôleur, même patron que `AdminSellerController`). Côté frontend, la route `/admin/reports` est déjà inaccessible à un bénévole via `adminGuard` (garde existante sur `/admin/**`, `app.routes.ts:44`) — défense en profondeur seulement, le backend ne doit jamais faire confiance au client seul.
4. **Actualisation (AC 4 epics.md).** Étant donné que l'admin clique sur « Actualiser », alors une nouvelle requête `GET /admin/reports/daily` est déclenchée et le bilan affiché reflète les dernières ventes de la journée.
5. **Garde de phase serveur.** Étant donné que l'édition n'est pas en phase Vente, quand `GET` ou `POST /admin/reports/daily(/print)` est appelé, alors le serveur répond 422 `sale-phase-required` — réutilise `PhaseGuard.requireSalePhase` tel quel (même garde que le scan POS, Story 4.1), pas une nouvelle méthode de `PhaseGuard`. Décision actée avec l'utilisateur (voir Dev Notes § Écarts, point 1) : non explicite dans epics.md mais cohérente avec le patron de défense en profondeur déjà appliqué à toutes les autres pages phase-dépendantes du projet.

## Tasks / Subtasks

### Backend

- [x] **`ItemPricing` — extraire `computeCommission` (AC 1)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` (UPDATE — lire le fichier en entier) : extraire la formule de commission déjà présente dans `computeNetPayout` en une méthode publique réutilisable, `computeNetPayout` l'appelle plutôt que de dupliquer la formule pour la deuxième fois dans le projet :
    ```java
    public static BigDecimal computeCommission(BigDecimal total, BigDecimal commissionRate) {
        return total.multiply(commissionRate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal computeNetPayout(BigDecimal total, BigDecimal commissionRate) {
        return total.subtract(computeCommission(total, commissionRate)).setScale(2, RoundingMode.HALF_UP);
    }
    ```
    Ne rien changer d'autre à ce fichier (`computeTotal`, `distinctByLot` inchangés). Comportement de `computeNetPayout` strictement identique (même arrondi intermédiaire à 4 décimales avant le `subtract`) — vérifier qu'aucun test existant (`SettlementIT`, `SettlementReportPrintingIT`, `DepositSlipPrintingIT`) ne régresse.

- [x] **`SaleRepository` — ventes de la journée (AC 1)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java` (UPDATE — actuellement vide de méthode custom) :
    ```java
    /**
     * Daily sales report (story 5.3, FR-054): every Sale validated within the given calendar-day
     * window, used to compute gross revenue and the payment-method breakdown in memory (BigDecimal
     * sums, same convention as ItemPricing — no SQL-level aggregation anywhere else in this project).
     */
    @Query("SELECT s FROM Sale s WHERE s.edition.id = :editionId AND s.soldAt >= :dayStart AND s.soldAt < :dayEnd")
    List<Sale> findAllByEditionIdAndSoldAtBetween(@Param("editionId") Long editionId,
            @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);
    ```

- [x] **`ItemRepository` — comptages du bilan journalier (AC 1)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE — lire le fichier en entier, respecter le style Javadoc existant) : deux nouvelles méthodes
    ```java
    /**
     * Daily sales report (story 5.3, FR-054): total unsold items in the active edition as of now —
     * a snapshot count, not scoped to any calendar day (an unsold item has no sale date to filter
     * by; only the sold count below is day-scoped).
     */
    long countByEditionIdAndSoldFalse(Long editionId);

    /**
     * Daily sales report (story 5.3, FR-054): items sold within the given calendar-day window.
     * JOIN FETCH lot for lot-aware counting via {@link ItemPricing#distinctByLot} — a lot with any
     * member sold inside the window counts once, matching every other lot-aware count/total in this
     * codebase (decision confirmed with the user at create-story: 1 per lot, not per member).
     */
    @Query("SELECT i FROM Item i LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId AND i.sale.soldAt >= :dayStart AND i.sale.soldAt < :dayEnd")
    List<Item> findAllSoldByEditionIdAndSoldAtBetween(@Param("editionId") Long editionId,
            @Param("dayStart") LocalDateTime dayStart, @Param("dayEnd") LocalDateTime dayEnd);
    ```
    `i.sale.soldAt` est une navigation JPQL implicite à travers l'association `Item.sale` (to-one) — équivaut à un inner join, exclut automatiquement les items sans vente (`sale IS NULL`), aucun filtre `sold = true` explicite nécessaire.

- [x] **`DailySalesReportDto` — nouveau package `report` (AC 1)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/DailySalesReportDto.java` (NEW) — record, même style que `SettlementDto` :
    ```java
    public record DailySalesReportDto(
            LocalDate reportDate,
            long soldItemCount,
            long unsoldItemCount,
            BigDecimal grossRevenue,
            BigDecimal commission,
            BigDecimal cashTotal,
            BigDecimal checkTotal,
            BigDecimal cardTotal) {
    }
    ```

- [x] **`ReportService` — calcul du bilan (AC 1, 5)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` (NEW) :
    ```java
    @Service
    @RequiredArgsConstructor
    public class ReportService {

        private final SaleRepository saleRepository;
        private final ItemRepository itemRepository;

        @Transactional(readOnly = true)
        public DailySalesReportDto getDailyReport(Edition edition) {
            PhaseGuard.requireSalePhase(edition);

            LocalDate today = LocalDate.now();
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            List<Sale> todaysSales = saleRepository.findAllByEditionIdAndSoldAtBetween(edition.getId(), dayStart, dayEnd);
            List<Item> soldItemsToday = itemRepository.findAllSoldByEditionIdAndSoldAtBetween(edition.getId(), dayStart, dayEnd);
            long unsoldItemCount = itemRepository.countByEditionIdAndSoldFalse(edition.getId());

            BigDecimal cash = BigDecimal.ZERO;
            BigDecimal check = BigDecimal.ZERO;
            BigDecimal card = BigDecimal.ZERO;
            for (Sale sale : todaysSales) {
                switch (sale.getPaymentMethod()) {
                    case CASH -> cash = cash.add(sale.getTotal());
                    case CHECK -> check = check.add(sale.getTotal());
                    case CARD -> card = card.add(sale.getTotal());
                }
            }
            BigDecimal grossRevenue = cash.add(check).add(card).setScale(2, RoundingMode.HALF_UP);
            BigDecimal commission = ItemPricing.computeCommission(grossRevenue, edition.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
            long soldItemCount = ItemPricing.distinctByLot(soldItemsToday).size();

            return new DailySalesReportDto(today, soldItemCount, unsoldItemCount, grossRevenue, commission,
                    cash.setScale(2, RoundingMode.HALF_UP), check.setScale(2, RoundingMode.HALF_UP), card.setScale(2, RoundingMode.HALF_UP));
        }
    }
    ```
    Recettes brutes calculées à partir de `Sale.total` (déjà lot-aware, calculé une fois pour toutes à la validation du panier — Story 4.2) plutôt que recalculées via `ItemPricing.computeTotal` sur les items : évite de re-dériver un prix de lot potentiellement partiel (piège déjà rencontré et corrigé dans `SettlementReportRenderer`, Story 5.2 review). `PhaseGuard.requireSalePhase` appelé ici (couche service), pas dans le contrôleur — même emplacement que `SettlementReportPrintService.printReport`.

- [x] **`DailyReportRenderer` — nouveau renderer PDF (AC 2)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java` (NEW) — même patron exact que `SettlementReportRenderer`/`InvoiceRenderer` (polices CP1252 non embarquées en bloc `static {}`, `MessageSource` injecté, `PdfWriter.setCompressionLevel(PdfStream.NO_COMPRESSION)`). Plus simple que `SettlementReportRenderer` : pas de tableau d'articles, seulement des compteurs/montants (FR-054 dit « nombre », pas une liste ligne par ligne) :
    ```java
    public byte[] renderDailyReport(String editionName, DailySalesReportDto report, Locale documentLocale) {
        // Titre, editionName, date (report.reportDate())
        // Paragraphes : soldItemCount, unsoldItemCount, grossRevenue, commission
        // Petit PdfPTable 2 colonnes (moyen de paiement / montant) : Espèces, Chèque, Carte
        // Clés messages : print.dailyReport.*
    }
    ```
  - [ ] `pluribourse-backend/src/main/resources/messages_fr.properties` (UPDATE, après le bloc `print.settlementReport.*`) :
    ```properties
    # Daily sales report PDF rendering (Story 5.3)
    print.dailyReport.title=Bilan des ventes journalier
    print.dailyReport.date=Date : {0}
    print.dailyReport.soldCount=Articles vendus aujourd'hui : {0}
    print.dailyReport.unsoldCount=Articles invendus : {0}
    print.dailyReport.grossRevenue=Recettes brutes : {0}€
    print.dailyReport.commission=Commission perçue : {0}€
    print.dailyReport.paymentBreakdown=Ventilation par moyen de paiement
    print.dailyReport.column.method=Moyen de paiement
    print.dailyReport.column.amount=Montant
    print.dailyReport.method.cash=Espèces
    print.dailyReport.method.check=Chèque
    print.dailyReport.method.card=Carte
    ```
  - [ ] `pluribourse-backend/src/main/resources/messages_en.properties` (UPDATE) — même structure, traduction anglaise (« Daily sales report », « Items sold today: {0} », « Items unsold: {0} », « Gross revenue: {0}€ », « Commission earned: {0}€ », « Payment method breakdown », « Payment method », « Amount », « Cash », « Check », « Card »).

- [x] **`DocumentPrintService` — nouveau job (AC 2)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE — lire le fichier en entier) : injecter `DailyReportRenderer`, ajouter `buildDailyReportJob(String editionName, DailySalesReportDto report, Locale documentLocale)` + `printDailyReport(...)` privée, même patron exact que `buildSettlementReportJob`/`printSettlementReport` juste au-dessus. Ne pas réordonner les méthodes existantes.
  - [x] **Régression de constructeur à anticiper (même piège qu'en Story 5.2).** L'ajout de `DailyReportRenderer` élargit le constructeur `@RequiredArgsConstructor` de `DocumentPrintService` de 4 à 5 arguments. Trois classes de test l'instancient manuellement avec les 4 arguments actuels (`depositSlipRenderer, invoiceRenderer, settlementReportRenderer, mockClient`) et **ne compileront plus** sans mise à jour :
    - `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java:225`
    - `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java:298`
    - `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java:385`
    Autowire `DailyReportRenderer` dans les trois (même correctif que la Story 5.2 avait appliqué pour `SettlementReportRenderer` sur les deux premières) — aucun changement de comportement testé, uniquement l'arité du constructeur.

- [x] **`DailySalesReportPrintService` — orchestration impression (AC 2, 5)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/DailySalesReportPrintService.java` (NEW) — même patron exact que `SettlementReportPrintService` (Story 5.2) :
    ```java
    @Service
    @RequiredArgsConstructor
    public class DailySalesReportPrintService {

        private final EditionService editionService;
        private final ReportService reportService;
        private final PrinterSelectionService printerSelectionService;
        private final PrintQueueService printQueueService;
        private final DocumentPrintService documentPrintService;

        @Transactional(readOnly = true)
        public void printDailyReport(HttpSession session) {
            Edition edition = editionService.getActiveEdition();
            DailySalesReportDto report = reportService.getDailyReport(edition); // garde de phase déjà appliquée dedans
            String editionName = edition.getName();
            Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

            Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                    .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
            if (!printQueueService.isAvailable(a4PrinterId)) {
                throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
            }

            printQueueService.submit(a4PrinterId, documentPrintService.buildDailyReportJob(editionName, report, documentLocale));
        }
    }
    ```
    Réutilise `InvalidPrinterSelectionException` existant (code `invalid-printer-selection`), pas de nouvelle exception. L'admin a déjà accès à `/printer-selection` depuis la Story 5.2 (lien ouvert dans le menu utilisateur) — aucun changement supplémentaire nécessaire sur ce point.

- [x] **`AdminReportController` — nouveau contrôleur (AC 1, 2, 3, 4)**
  - [ ] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java` (NEW) — même patron exact que `AdminSellerController` (`@PreAuthorize` au niveau classe) :
    ```java
    @RestController
    @RequestMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public class AdminReportController {

        private final EditionService editionService;
        private final ReportService reportService;
        private final DailySalesReportPrintService dailySalesReportPrintService;

        @GetMapping("/daily")
        public ResponseEntity<DailySalesReportDto> getDailyReport() {
            return ResponseEntity.ok(reportService.getDailyReport(editionService.getActiveEdition()));
        }

        @PostMapping("/daily/print")
        public ResponseEntity<Void> printDailyReport(HttpSession session) {
            dailySalesReportPrintService.printDailyReport(session);
            return ResponseEntity.noContent().build();
        }
    }
    ```

- [x] **Backend — test E2E dédié (AC 1, 2, 3, 4, 5)**
  - [ ] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java` (NEW) — colocalisé dans `domain.print` malgré son déclencheur dans `domain.report`, même précédent déjà établi pour `SettlementReportPrintingIT`/`InvoicePrintingIT`. Storyboard `@Order` suggéré :
    1. Créer une édition (commission 10%), un vendeur, des articles standards + un lot de 2 membres, avancer jusqu'à Vente.
    2. Vendre aujourd'hui via le flux POS existant : une vente CASH, une vente CARD incluant le lot (2 membres, 1 seul scanné/vendu — vérifie le comptage "1 par lot").
    3. Backdater une vente CHECK existante à hier pour prouver le bornage jour calendaire : valider la vente normalement via le flux POS existant (HTTP), puis charger l'entité `Sale` via `SaleRepository` (déjà autowiré et utilisé en lecture dans `InvoicePrintingIT`, précédent direct) et `sale.setSoldAt(LocalDateTime.now().minusDays(1))` + `saleRepository.save(sale)` — écriture directe hors HTTP, exception ciblée et documentée à la philosophie E2E-par-contrôleur (aucun mécanisme HTTP existant ne permet de simuler "hier" ; même type d'exception déjà actée pour `SaleConcurrencyIT`, Story 4.4, Dev Notes). La vente reste créée par le flux réel, seule sa date est ajustée après coup.
    4. `GET /admin/reports/daily` → 200 : `soldItemCount` compte le lot comme 1, `unsoldItemCount` reflète les invendus de l'édition (indépendant du jour), recettes brutes = somme CASH+CARD du jour uniquement (pas la vente d'hier), commission = 10% de ce total, ventilation CASH/CARD correcte, CHECK à 0€ pour le jour courant.
    5. Sélectionner une imprimante A4 en session admin, `POST /admin/reports/daily/print` → 204, contenu PDF vérifié (`%PDF`, libellés + montants attendus, même style `countOccurrences`/assertions textuelles qu'`InvoicePrintingIT`).
    6. Langue : édition `Language.EN` → PDF en anglais (labels EN, absence des labels FR) — même style que `SettlementReportPrintingIT` Order 9.
    7. Garde de phase : édition hors Vente (ex. Post-vente) → `GET` et `POST` renvoient 422 `sale-phase-required`.
    8. Garde de rôle : session bénévole → 403 sur `GET` et `POST` (`@PreAuthorize` niveau classe, pas besoin de test par endpoint séparé si un seul scénario couvre les deux verbes).
    9. Pas d'imprimante A4 sélectionnée → 422 `invalid-printer-selection` sur `POST` uniquement.
  - [x] Vérifier `SaleConcurrencyIT` (Story 4.4, done) et le reste de la suite `pos`/`item` — aucune régression attendue, `ItemRepository`/`SaleRepository` reçoivent uniquement des ajouts.

### Frontend

- [x] **Modèle + service (AC 1, 2, 4)**
  - [ ] `pluribourse-frontend/src/app/models/daily-sales-report.model.ts` (NEW) :
    ```typescript
    export interface DailySalesReportDto {
      reportDate: string; // ISO LocalDate
      soldItemCount: number;
      unsoldItemCount: number;
      grossRevenue: number; // BigDecimal sérialisé en number par Jackson — confirmé sur settlement.model.ts:11 (amountDue: number), même convention ici
      commission: number;
      cashTotal: number;
      checkTotal: number;
      cardTotal: number;
    }
    ```
  - [ ] `pluribourse-frontend/src/app/services/report.service.ts` (NEW) :
    ```typescript
    @Injectable({ providedIn: 'root' })
    export class ReportService {
      private readonly http = inject(HttpClient);

      getDailyReport(): Observable<DailySalesReportDto> {
        return this.http.get<DailySalesReportDto>('/api/admin/reports/daily');
      }

      printDailyReport(): Observable<void> {
        return this.http.post<void>('/api/admin/reports/daily/print', null);
      }
    }
    ```
  - [ ] `pluribourse-frontend/src/app/services/report.service.spec.ts` (NEW) — deux scénarios simples (patron `settlement.service.spec.ts`).

- [x] **`ReportPageComponent` — nouvelle page (AC 1, 2, 3, 4)**
  - [ ] `pluribourse-frontend/src/app/features/report/report-page.component.ts` (NEW) — même patron `load()`/`refresh()`/signals `isLoading`/`error` que `PrintQueueListComponent`, mais déclenché par un `effect()` réactif sur la phase plutôt qu'un `ngOnInit` ponctuel (voir justification dans le constructeur ci-dessous — race avec `AppLayoutComponent.loadEdition()`) :
    ```typescript
    export class ReportPageComponent {
      private readonly reportService = inject(ReportService);
      private readonly currentEditionService = inject(CurrentEditionService);
      private readonly toast = inject(ToastService);
      private readonly translate = inject(TranslateService);

      readonly report = signal<DailySalesReportDto | null>(null);
      readonly isLoading = signal(false);
      readonly error = signal<string | null>(null);
      readonly printing = signal(false);

      readonly isSalePhase = computed(() => this.currentEditionService.currentEdition()?.phase === ActivePhase.SALE);

      constructor() {
        // effect() réactif, PAS un simple ngOnInit ponctuel : AppLayoutComponent.loadEdition()
        // (parent, app-layout.component.ts:79) résout de façon async APRÈS le montage de ce
        // composant enfant — currentEdition() vaut donc souvent encore null au moment précis où
        // ngOnInit s'exécuterait. Un test one-shot sur isSalePhase() dans ngOnInit manquerait le
        // vrai chargement dans ce cas (page vide en phase Vente après un accès direct/rafraîchi
        // sur /admin/reports) et ne se rattraperait jamais. Même race déjà identifiée et gérée par
        // AppLayoutComponent lui-même pour sa propre logique de redirection (constructor, "isFirstRun").
        // Un effect() reste réactif aux deux sens : chargement dès que la phase Vente devient vraie
        // (y compris après résolution tardive), ET remise à null si la phase change pendant la
        // consultation de la page (cohérent avec le "absent, pas grisée" de la Story 5.5).
        effect(() => {
          if (this.isSalePhase()) {
            void this.load();
          } else {
            this.report.set(null);
          }
        });
      }

      async refresh(): Promise<void> {
        await this.load();
      }

      private async load(): Promise<void> {
        this.isLoading.set(true);
        this.error.set(null);
        try {
          this.report.set(await firstValueFrom(this.reportService.getDailyReport()));
        } catch {
          this.error.set('admin.reports.error.load');
        } finally {
          this.isLoading.set(false);
        }
      }

      async printReport(): Promise<void> {
        if (this.printing()) return;
        this.printing.set(true);
        try {
          await firstValueFrom(this.reportService.printDailyReport());
          this.toast.showSuccess(this.translate.instant('admin.reports.success.print'));
        } catch (err: unknown) {
          if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
            this.toast.showError(this.translate.instant('admin.reports.error.printerUnavailable'));
          } else {
            this.toast.showError(this.translate.instant('admin.reports.error.print'));
          }
        } finally {
          this.printing.set(false);
        }
      }
    }
    ```
    Mécanisme de détection identique à `settlement-list.component.ts` (`printReport()`, lignes 182-192) : `extractErrorType` importé de `../../shared/http-error.util` (utilitaire RFC7807 déjà existant, ne pas en recréer un). Ne pas s'en écarter.
    En dehors de la phase Vente (`!isSalePhase()`), la section bilan journalier n'est PAS chargée ni affichée — `EmptyStateComponent` générique (« Aucun rapport disponible dans cette phase. », clé `admin.reports.emptyPhase`). Story 5.5 (backlog) remplacera cette page par une vraie page multi-sections conditionnées par phase (synthèse Post-vente/Clôturée, exports CSV) — cette story ne construit que le strict nécessaire pour la section Vente, sans anticiper la structure finale de 5.5.
  - [ ] `pluribourse-frontend/src/app/features/report/report-page.component.html` (NEW — jamais de template inline) : skeleton pendant le chargement (`SkeletonRowComponent` ou équivalent déjà utilisé pour un état agrégé plutôt qu'une liste — vérifier s'il existe un pattern de "carte" en chargement ailleurs, sinon un simple état `isLoading` textuel suffit, pas de nouvelle abstraction pour un seul usage), bouton « Actualiser », compteurs/montants affichés en cartes simples (`mat-card`) — pattern visuel « Metric tile » de `EXPERIENCE.md` (chiffre en `title-lg`, libellé en `label-lg`, lecture seule) reproduit directement en HTML/SCSS de cette page, **sans créer de composant partagé `MetricTileComponent`** : un seul consommateur aujourd'hui, l'extraction sera justifiée quand 5.4/5.5 auront le même besoin (pas d'abstraction prématurée). Tableau de ventilation par moyen de paiement (3 lignes), bouton « Imprimer » (`[disabled]="printing()"`).
  - [ ] `pluribourse-frontend/src/app/features/report/report-page.component.scss` (NEW).
  - [ ] `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts` (NEW) — scénarios : chargement quand `currentEdition()` est déjà en phase Vente au montage, **chargement différé quand `currentEdition()` passe à Vente seulement après le montage** (simule la résolution tardive de `loadEdition()` — le scénario qui justifie l'`effect()`, à ne pas oublier), actualisation, hors phase Vente → état vide sans appel HTTP, retour à l'état vide si la phase change pendant la consultation, clic Imprimer → succès/erreur générique/erreur imprimante indisponible, garde de réentrance sur le bouton Imprimer.

- [x] **Route + navigation (AC 3)**
  - [ ] `pluribourse-frontend/src/app/features/admin/admin.routes.ts` (UPDATE — lire le fichier en entier) : ajouter
    ```typescript
    {
      path: 'reports',
      loadComponent: () =>
        import('../report/report-page.component').then((m) => m.ReportPageComponent),
    },
    ```
    Pas de guard de phase sur la route elle-même (contrairement à `settlement`) : la page reste accessible dans toutes les phases admin, seul son contenu varie — `adminGuard` (déjà appliqué au parent `/admin`) suffit pour l'accès rôle.
  - [ ] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE — lire le fichier en entier) : nouveau lien sidebar dans la section `nav.sections.management` (aux côtés de `printers`/`users`/`settings`/`print-queue`), icône Material `assessment` (confirmée par `EXPERIENCE.md`) :
    ```html
    <a
      routerLink="/admin/reports"
      routerLinkActive="sidebar__item--active"
      ariaCurrentWhenActive="page"
      class="sidebar__item"
      [attr.aria-label]="sidebarCollapsed() ? ('nav.admin.reports' | translate) : null"
      [matTooltip]="sidebarCollapsed() ? ('nav.admin.reports' | translate) : ''"
      matTooltipPosition="right">
      <span class="material-symbols-outlined" aria-hidden="true">assessment</span>
      <span class="sidebar__item-label">{{ 'nav.admin.reports' | translate }}</span>
    </a>
    ```
  - [ ] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` (UPDATE) — nouveau scénario : lien visible pour un admin, absent pour un bénévole (même style que les tests existants sur `printQueue`/`settlement`).

- [x] **i18n (AC 1, 2, 4)**
  - [ ] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) — sous `nav.admin` :
    ```json
    "reports": "Rapports"
    ```
    Nouveau namespace `admin.reports` (vérifier la structure exacte des namespaces `admin.*` déjà présents — `admin.printQueue`, `admin.printers` — avant d'ajouter, pour rester cohérent) :
    ```json
    "reports": {
      "daily": {
        "title": "Bilan journalier",
        "refresh": "Actualiser",
        "print": "Imprimer",
        "soldCount": "Articles vendus aujourd'hui",
        "unsoldCount": "Articles invendus",
        "grossRevenue": "Recettes brutes",
        "commission": "Commission perçue",
        "paymentBreakdown": "Ventilation par moyen de paiement",
        "cash": "Espèces",
        "check": "Chèque",
        "card": "Carte"
      },
      "emptyPhase": "Aucun rapport disponible dans cette phase.",
      "success": {
        "print": "Bilan envoyé à l'imprimante."
      },
      "error": {
        "load": "Impossible de charger le bilan journalier.",
        "print": "Impossible d'imprimer le bilan.",
        "printerUnavailable": "Aucune imprimante A4 disponible. Sélectionnez une imprimante."
      }
    }
    ```
  - [ ] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — même structure, traduction anglaise.

### Review Findings

- [x] [Review][Patch] `unsoldItemCount` n'applique pas le comptage 1-par-lot alors que la décision utilisateur (Dev Notes § Écarts, point 4) couvre explicitement « vendus/invendus » — `ItemRepository.countByEditionIdAndSoldFalse` compte chaque ligne `Item` brute, sans passer par `ItemPricing.distinctByLot` comme le fait `soldItemCount`. Pour un lot ≥3 membres avec 2+ membres invendus, le compteur surcompte par rapport à la convention documentée. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java:128, pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java:45] — Corrigé : `countByEditionIdAndSoldFalse` remplacée par `findAllUnsoldByEditionId` (JOIN FETCH lot), `ReportService` applique `ItemPricing.distinctByLot` aux deux compteurs.
- [x] [Review][Patch] `€` codé en dur dans le template Angular (`{{ dailyReport.grossRevenue }}€`, etc.) au lieu de passer par le patron i18n déjà établi par `settlement-list.component.html` (`'settlement.amountFormat' | translate: { amount: ... }`) — viole la règle « tous les textes UI passent par i18n » du CLAUDE.md et n'impose pas 2 décimales. [pluribourse-frontend/src/app/features/report/report-page.component.html:36,40,56,60,64] — Corrigé : nouvelle clé `admin.reports.daily.amountFormat` (FR+EN), template utilisant `| translate: { amount: ....toFixed(2) }`.
- [x] [Review][Patch] `€` codé en dur par concaténation de chaîne dans `DailyReportRenderer.addPaymentRow`, alors que tous les autres libellés de la même classe passent par `messageSource` — incohérent avec le reste du fichier. [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java:110] — Corrigé : nouvelle clé `print.dailyReport.amountFormat` (FR+EN), `addPaymentRow` route désormais le montant par `messageSource.getMessage`.
- [x] [Review][Patch] Le `switch` sur `PaymentMethod` dans `ReportService.getDailyReport` n'a pas de branche `default` — c'est un switch-statement (pas une expression), donc le compilateur n'impose pas l'exhaustivité : un futur moyen de paiement ajouté à l'enum disparaîtrait silencieusement des recettes du bilan sans erreur. [pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java:51-55] — Corrigé : ajout d'une branche `default` levant `IllegalStateException`.
- [x] [Review][Patch] `ItemPricing.computeCommission` (nouvelle méthode publique) n'a pas de JavaDoc alors que sa sémantique n'est pas évidente (taux en pourcentage 0-100, arrondi intermédiaire fixé à 4 décimales) — requis par CLAUDE.md pour les méthodes non triviales à paramètres non évidents. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java:32] — Corrigé : JavaDoc ajoutée.
- [x] [Review][Patch] `refresh()`/`load()` de `ReportPageComponent` n'a pas de garde de réentrance, contrairement à `printReport()` — combiné au nouvel `effect()` qui peut redéclencher `load()` à chaque transition de phase, une réponse HTTP plus ancienne peut écraser une réponse plus récente en cas d'appels rapprochés. [pluribourse-frontend/src/app/features/report/report-page.component.ts:37-66] — Corrigé : garde `if (this.isLoading()) return;` ajoutée en tête de `load()`.
- [x] [Review][Defer] `DailyReportRenderer.renderDailyReport` ne capture que `DocumentException` — une `NoSuchMessageException` de `messageSource.getMessage` (clé i18n manquante) se propagerait sans appeler `document.close()`. Motif de report : ce patron try/catch (pas de catch générique, pas de try-with-resources) est déjà celui des 3 autres renderers existants (`InvoiceRenderer`, `DepositSlipRenderer`, `SettlementReportRenderer`) — pré-existant, non introduit par cette story ; les 12 nouvelles clés `print.dailyReport.*` sont bien présentes dans les deux fichiers de messages, donc le risque ne se matérialise pas aujourd'hui. [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java:90-92] — deferred, pre-existing

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`InvoiceRenderer`/`DepositSlipRenderer`/`SettlementReportRenderer`** (`domain/print/service/`) : patron exact à reproduire pour `DailyReportRenderer` — polices CP1252 non embarquées en `static {}`, `MessageSource` injecté, `PdfWriter.setCompressionLevel(PdfStream.NO_COMPRESSION)`.
- **`SettlementReportPrintService`** (`domain/payout/service/`, Story 5.2) : patron exact pour `DailySalesReportPrintService` — extraction en valeurs simples avant construction du `PrintJob`, `PrinterSelectionService.getSelectedPrinterId`/`PrintQueueService.isAvailable`/`submit`, `InvalidPrinterSelectionException` réutilisée telle quelle.
- **`PhaseGuard.requireSalePhase`** (Story 4.1) : réutilisée telle quelle — ne pas créer de nouvelle méthode de garde pour la phase Vente, elle existe déjà et est déjà testée.
- **`ItemPricing.distinctByLot`** : seule vérité du comptage lot-aware — jamais de logique de dédoublonnage de lot réécrite localement.
- **`PrintQueueListComponent`** (frontend, `features/admin/print-queue/`) : patron `load()`/`refresh()`/signals `isLoading`/`error` à reproduire pour `ReportPageComponent`.
- **`AppLayoutComponent.loadEdition()`** (`layout/app-layout/app-layout.component.ts:79`) : appelé une seule fois au niveau du shell parent, résout **après** le montage des composants enfants (dont `ReportPageComponent`). `AppLayoutComponent` gère déjà cette race pour sa propre logique de redirection via un `effect()` (constructeur, drapeau `isFirstRun`) — `ReportPageComponent` doit utiliser le même mécanisme réactif (`effect()` sur `isSalePhase()`) plutôt qu'un `ngOnInit` ponctuel, sous peine de rater le chargement sur un accès direct/rafraîchissement de `/admin/reports`.
- **Accès admin à `/printer-selection`** (Story 5.2) : déjà ouvert, aucun changement supplémentaire nécessaire pour que le bouton Imprimer fonctionne côté admin.
- **`adminGuard`** (`core/guards/admin.guard.ts`, appliqué à `/admin` dans `app.routes.ts:44`) : couvre déjà l'accès rôle pour toute route `/admin/**`, y compris la nouvelle `/admin/reports` — aucun nouveau guard frontend nécessaire pour l'AC 3.

### Écarts par rapport aux sources de planification — actés avec l'utilisateur

1. **Garde de phase serveur non explicite dans epics.md.** L'AC de Story 5.3 dans epics.md ne décrit pas explicitement de code d'erreur pour un appel hors phase Vente (contrairement à l'AC 3, qui donne un 403 explicite pour un bénévole). **Décision utilisateur (create-story) : appliquer `PhaseGuard.requireSalePhase` aux deux endpoints, 422 `sale-phase-required`** — cohérent avec le patron de défense en profondeur déjà appliqué systématiquement ailleurs dans ce projet (POS, Settlement), le client n'étant jamais fait confiance seul.
2. **Vue écran en plus du PDF.** Ni epics.md ni architecture.md ne précisent explicitement un endpoint `GET` JSON distinct du PDF — mais l'AC « l'admin actualise le rapport journalier... reflète les dernières données » (epics.md) et la Story 5.5 (« section bilan journalier... avec un bouton Actualiser », lecture seule à l'écran) n'ont de sens qu'avec un état affiché et rafraîchissable, séparé de la génération PDF (qui, elle, s'enfile sur une file d'impression physique — aucune notion d'« actualisation » n'existe dans ce mécanisme ailleurs dans le projet). **Décision utilisateur (create-story) : `GET /admin/reports/daily` (JSON, écran) + `POST /admin/reports/daily/print` (PDF, file d'impression) — deux endpoints distincts**, même séparation lecture/action que `SettlementController` (`GET /settlements` vs `POST /{id}/report/print`).
3. **Route `/admin/reports` créée par cette story, pas par la Story 5.5.** La Story 5.5 (backlog) est censée créer la page `/admin/reports` avec ses 3 sections conditionnées par phase (bilan journalier en Vente, synthèse + exports CSV en Post-vente/Clôturée). Comme 5.3 passe avant 5.5 dans le sprint, **décision utilisateur (create-story) : créer la route et le squelette de page maintenant**, avec uniquement la section bilan journalier (visible seulement en phase Vente, `EmptyStateComponent` générique sinon) — 5.5 étendra ce même composant avec les sections manquantes plutôt que de repartir de zéro, évitant de refaire deux fois le routing/guard/403.
4. **Comptage des lots dans les compteurs vendus/invendus.** epics.md ne précise pas si un lot compte pour 1 ou pour son nombre de membres dans « nombre d'articles vendus/invendus ». **Décision utilisateur (create-story) : 1 par lot**, cohérent avec `ItemPricing.distinctByLot`, déjà la convention établie partout ailleurs (bilan de vente, panier POS, factures).
5. **« Articles invendus » = snapshot, pas borné à la journée.** FR-054 dit « articles vendus/invendus pour la journée », mais un article invendu n'a pas de date de vente à filtrer — lecture retenue (sans ambiguïté réelle, non soumise à confirmation) : le compteur invendus reflète l'état actuel de l'édition active, indépendamment du jour.
6. **Pas de nouveau composant `MetricTileComponent`.** `EXPERIENCE.md` documente un patron visuel « Metric tile » réutilisé par cette page et par la future synthèse de 5.4/5.5, mais cette story est la première à en avoir besoin. Reproduit directement dans `report-page.component.html`/`.scss` sans extraction — l'abstraction sera justifiée par un deuxième consommateur réel (5.4 ou 5.5), pas anticipée ici.

### Aucune maquette UX dédiée

Contrairement à la plupart des stories précédentes, **aucun fichier `mock-admin-reports*.html` n'existe** dans `ux-designs/ux-PluriBourse-2026-06-09/mockups/` — seule `EXPERIENCE.md` mentionne la page (tableau de routes, nav sidebar, patron « Metric tile »). La mise en page de `report-page.component.html` doit suivre les tokens de design déjà établis (`{colors}`, `{typography}`, cartes `mat-card`) et le patron `PrintQueueListComponent`/`SettlementListComponent` pour la cohérence visuelle, sans référence pixel-perfect à reproduire.

### Project Structure Notes

- Nouveau package `org.pluribourse.domain.report` (`controller/`, `service/`, `dto/`) — premier fichier de ce package, aucune convention interne à respecter au-delà des patrons déjà cités.
- `DailyReportRenderer` (NEW) vit dans `org.pluribourse.domain.print.service` (déjà existant), pas dans `report` — même convention que `SettlementReportRenderer` (renderer dans `print`, orchestration dans le package métier déclencheur).
- Aucune migration Liquibase — lecture seule (`ItemRepository`, `SaleRepository`) et génération de document à la volée, aucune donnée persistée par cette story.
- Nouveau dossier frontend `features/report/` (premier composant du domaine F6 Rapports).
- Fichiers UPDATE en dehors du nouveau périmètre (à lire intégralement avant modification) : `ItemPricing.java`, `SaleRepository.java`, `ItemRepository.java`, `DocumentPrintService.java`, `messages_fr.properties`, `messages_en.properties`, `admin.routes.ts`, `app-layout.component.html`/`.spec.ts`, `fr.json`, `en.json`.

### Fichiers à lire avant modification

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java`, `InvoiceRenderer.java` (référence directe — patron complet à reproduire pour `DailyReportRenderer`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE — lire en entier)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementReportPrintService.java` (référence directe — patron complet pour `DailySalesReportPrintService`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/SettlementController.java`, `seller/controller/AdminSellerController.java` (référence — patron d'endpoint `@PreAuthorize` niveau classe)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` (UPDATE — lire en entier)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`, `pos/repository/SaleRepository.java` (UPDATE — lire en entier pour respecter le style des requêtes/Javadoc existantes)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` (référence — `requireSalePhase`, ne pas modifier)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` (référence directe — patron complet de test à reproduire, `PrinterBridgeDouble`)
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.ts`/`.html` (référence directe — patron `load()`/`refresh()`/signals)
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` (référence — gestion 422 `invalid-printer-selection`, à reproduire exactement, pas à réinventer)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (référence — patron alternatif de gestion 422 `invalid-printer-selection`)
- `pluribourse-frontend/src/app/services/settlement.service.ts`, `print-queue.service.ts` (référence — style des services HTTP)
- `pluribourse-frontend/src/app/models/settlement.model.ts` (référence — confirme la convention `number` pour un champ `BigDecimal` sérialisé, déjà appliquée à `DailySalesReportDto`)
- `pluribourse-frontend/src/app/app.routes.ts`, `features/admin/admin.routes.ts` (UPDATE — lire en entier)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE — lire en entier) et `.spec.ts` (UPDATE)
- `pluribourse-frontend/src/app/services/current-edition.service.ts`, `models/active-phase.enum.ts` (référence — détection de la phase Vente côté client)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` (référence directe — tableau de routes ligne 46, nav sidebar ligne 83, patron « Metric tile » ligne 157)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.3] — ACs source
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.5] — page `/admin/reports` multi-sections (backlog), justifie le squelette créé par cette story
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] — FR-054 (bilan journalier), FR-057 (PDF), FR-058 (admin uniquement), FR-094 (ventilation moyen de paiement)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/.decision-log.md] — 2026-06-08 Reporting : deux bilans distincts (journalier/édition), tous en PDF, admin uniquement
- [Source: _bmad-output/planning-artifacts/architecture.md#Structure des packages] — package `report` anticipé (noms de classes indicatifs, pas normatifs — voir Dev Notes § écarts)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — tableau de routes, nav sidebar, patron « Metric tile » (aucune maquette dédiée, voir Dev Notes)
- [Source: _bmad-output/implementation-artifacts/5-2-generation-du-bilan-de-vente-pdf.md] — story précédente directe de l'épic (patron renderer + print service + contrôleur)
- [Source: _bmad-output/implementation-artifacts/5-1-flux-de-solde-des-vendeurs.md] — patron `PhaseGuard`/séparation service impression vs service métier
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/**, payout/**, item/**, pos/**] — lus intégralement pour les patrons réutilisés
- [Source: pluribourse-frontend/src/app/**] — fichiers listés en § Fichiers à lire, lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

Aucun écart imprévu par rapport au plan de la story pendant l'implémentation — les trois lacunes déjà identifiées et corrigées lors de la validation post-création (`bmad-create-story validate`, régression de constructeur `DocumentPrintService`, type frontend `number` pour `BigDecimal`, `effect()` réactif contre la race `AppLayoutComponent.loadEdition()`) étaient déjà intégrées dans le plan avant `dev-story` et n'ont pas nécessité de nouvel arbitrage. Un seul ajustement mineur non anticipé, découvert en écrivant le template `report-page.component.html` : le plan de la story ne prévoyait pas de clé i18n dédiée pour les en-têtes de colonne du tableau de ventilation (« Moyen de paiement » / « Montant ») — réutiliser `admin.reports.daily.grossRevenue` comme en-tête de colonne aurait affiché « Recettes brutes » au lieu de « Montant », un libellé incorrect. Corrigé en ajoutant deux clés `admin.reports.daily.method`/`admin.reports.daily.amount` (FR+EN), cohérentes avec le reste du namespace — aucun changement de comportement, pure lacune de planning i18n.

### Completion Notes List

- Backend : nouveau package `org.pluribourse.domain.report` (`AdminReportController`, `ReportService`, `DailySalesReportPrintService`, `DailySalesReportDto`). `ItemPricing.computeCommission` extrait de `computeNetPayout` (comportement strictement identique, arrondi intermédiaire à 4 décimales inchangé). `SaleRepository.findAllByEditionIdAndSoldAtBetween` (première méthode custom de ce repository, jusqu'ici vide), `ItemRepository.countByEditionIdAndSoldFalse`/`findAllSoldByEditionIdAndSoldAtBetween`. Nouveau renderer `DailyReportRenderer` (`domain/print/service`, patron `SettlementReportRenderer`, plus simple — pas de tableau d'articles, seulement compteurs/montants + tableau de ventilation 3 lignes). `DocumentPrintService.buildDailyReportJob` (5e renderer injecté). Recettes brutes calculées à partir de `Sale.total` (pas de re-dérivation via `ItemPricing.computeTotal`, évite le piège lot partiellement vendu déjà rencontré en Story 5.2). Endpoint `GET /admin/reports/daily` (écran, JSON) + `POST /admin/reports/daily/print` (PDF, file d'impression A4) sur `AdminReportController`, `@PreAuthorize("hasRole('ADMIN')")` niveau classe, `PhaseGuard.requireSalePhase` réutilisé tel quel (422 `sale-phase-required` hors phase Vente).
- Régression de constructeur anticipée et corrigée (identique au piège de la Story 5.2) : `DocumentPrintService` passé de 4 à 5 arguments — `DepositSlipPrintingIT`, `InvoicePrintingIT`, `SettlementReportPrintingIT` mis à jour (autowire `DailyReportRenderer`, constructeur manuel élargi), aucun changement de comportement testé.
- Nouveau test `DailyReportPrintingIT` (15 scénarios, storyboard `@Order`, colocalisé dans `domain.print` comme les trois renderers existants) : création édition/vendeur/articles/lot, vente CASH + vente CARD (lot partiellement scanné, compte pour 1) aujourd'hui, vente CHECK backdatée à hier (écriture directe `SaleRepository`, exception ciblée à la philosophie E2E-par-contrôleur déjà actée pour `SaleConcurrencyIT`) pour prouver le bornage jour calendaire, contenu du bilan (GET), garde de rôle (403 bénévole), garde de phase (422 hors Vente), contenu PDF FR/EN, envoi des bytes via `PrinterBridgeClient` mocké, flux HTTP complet (mise en file), imprimante A4 non sélectionnée (422 `invalid-printer-selection`). 422/422 tests backend, aucune régression.
- Frontend : `daily-sales-report.model.ts` (`number` pour tous les champs `BigDecimal`, confirmé contre `settlement.model.ts`), `report.service.ts` (`getDailyReport`/`printDailyReport`, patron `settlement.service.ts`). Nouveau `ReportPageComponent` (`features/report/`) : `effect()` réactif sur `isSalePhase()` (pas un `ngOnInit` ponctuel — évite la race avec `AppLayoutComponent.loadEdition()`, résolue de façon asynchrone après le montage du composant enfant), section bilan journalier visible uniquement en phase Vente (`app-empty-state` générique sinon), boutons Actualiser/Imprimer, gestion du 422 `invalid-printer-selection` identique à `settlement-list.component.ts` (`extractErrorType`). Cartes « Metric tile » (compteurs/montants) et tableau de ventilation par moyen de paiement construits directement dans le template de cette page, sans nouveau composant partagé (un seul consommateur à ce stade). Route `/admin/reports` ajoutée à `admin.routes.ts` (pas de garde de phase sur la route elle-même — seul le contenu varie), lien sidebar « Rapports » (icône `assessment`) ajouté dans `app-layout.component.html`, section `nav.sections.management`. i18n `admin.reports.*`/`nav.admin.reports` (FR+EN), y compris les deux clés `method`/`amount` ajoutées pendant l'implémentation (voir Debug Log). 575/575 tests frontend, build de production sans erreur, aucune régression.

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/DailySalesReportDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/DailySalesReportPrintService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java`

**Backend — UPDATE**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/ItemPricing.java` — `computeCommission` extrait
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java` — `findAllByEditionIdAndSoldAtBetween`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` — `findAllUnsoldByEditionId` (lot-aware, review fix), `findAllSoldByEditionIdAndSoldAtBetween`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` — `buildDailyReportJob`, injection `DailyReportRenderer`
- `pluribourse-backend/src/main/resources/messages_fr.properties` — namespace `print.dailyReport.*`
- `pluribourse-backend/src/main/resources/messages_en.properties` — namespace `print.dailyReport.*`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — constructeur `DocumentPrintService` (régression, autowire `DailyReportRenderer`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` — constructeur `DocumentPrintService` (régression, autowire `DailyReportRenderer`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` — constructeur `DocumentPrintService` (régression, autowire `DailyReportRenderer`)

**Frontend — NEW**
- `pluribourse-frontend/src/app/models/daily-sales-report.model.ts`
- `pluribourse-frontend/src/app/services/report.service.ts`
- `pluribourse-frontend/src/app/services/report.service.spec.ts`
- `pluribourse-frontend/src/app/features/report/report-page.component.ts`
- `pluribourse-frontend/src/app/features/report/report-page.component.html`
- `pluribourse-frontend/src/app/features/report/report-page.component.scss`
- `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts`

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — route `reports`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — lien sidebar « Rapports »
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` — nouveau scénario
- `pluribourse-frontend/public/i18n/fr.json` — `nav.admin.reports`, namespace `admin.reports.*`
- `pluribourse-frontend/public/i18n/en.json` — `nav.admin.reports`, namespace `admin.reports.*`

## Change Log

- 2026-08-18 — create-story : story créée après clarification avec l'utilisateur sur trois points (mécanisme écran + PDF plutôt que PDF seul ; création de la route `/admin/reports` maintenant plutôt que d'attendre la Story 5.5 ; comptage des lots à 1 par lot). Statut → ready-for-dev.
- 2026-08-18 — validate (bmad-create-story validate) : 3 lacunes trouvées et corrigées avant dev — régression de constructeur `DocumentPrintService` non anticipée (3 classes de test à mettre à jour), type frontend erroné (`string` au lieu de `number` pour un champ `BigDecimal`), race condition réelle sur la détection de phase (`ngOnInit` ponctuel → `effect()` réactif, contre `AppLayoutComponent.loadEdition()` qui résout après le montage du composant enfant).
- 2026-08-18 — dev-story : implémentation complète full-stack. Backend : nouveau package `report` (`AdminReportController`, `ReportService`, `DailySalesReportPrintService`, `DailySalesReportDto`), `DailyReportRenderer`, `ItemPricing.computeCommission` extrait, `SaleRepository`/`ItemRepository` étendus. Régression de constructeur `DocumentPrintService` corrigée sur les 3 classes de test concernées (anticipée dans la story). Nouveau test `DailyReportPrintingIT` (15 scénarios). Frontend : `ReportPageComponent` (`effect()` réactif sur la phase), route `/admin/reports`, lien sidebar, i18n complet (2 clés `method`/`amount` ajoutées, lacune de planning i18n découverte en écrivant le template — voir Debug Log). 422/422 tests backend, 575/575 tests frontend, build de production sans erreur, aucune régression. Statut → review.
- 2026-08-18 — code review (bmad-code-review, 3 couches parallèles : Blind Hunter, Edge Case Hunter, Acceptance Auditor) : 0 decision-needed, 6 patch, 1 defer, 6 rejetés comme bruit (contredits par des conventions déjà établies ou des décisions déjà actées dans la story — arrondi commission après recettes, absence de `Clock`/fuseau explicite, signals non-`readonly`, ternaire `Language`, absence de paramètre de date, `unsoldItemCount` en snapshot). Les 6 patches ont été appliqués : `unsoldItemCount` rendu lot-aware (`ItemRepository.findAllUnsoldByEditionId` + `ItemPricing.distinctByLot`, corrige une contradiction avec la décision utilisateur "1 par lot" pour vendus/invendus) ; `€` routé par i18n au lieu d'être codé en dur (nouvelle clé `amountFormat`, backend + frontend) ; branche `default` ajoutée au `switch` `PaymentMethod` ; JavaDoc ajoutée sur `ItemPricing.computeCommission` ; garde de réentrance ajoutée sur `ReportPageComponent.load()`. 1 item déféré (`DailyReportRenderer` : catch `DocumentException` seul, pré-existant sur les 4 renderers, sans risque actuel — 12 clés `print.dailyReport.*` vérifiées présentes FR+EN). 15/15 tests `DailyReportPrintingIT` + régressions `DepositSlipPrintingIT`/`InvoicePrintingIT`/`SettlementReportPrintingIT` verts, 575/575 tests frontend verts. Statut → done.
