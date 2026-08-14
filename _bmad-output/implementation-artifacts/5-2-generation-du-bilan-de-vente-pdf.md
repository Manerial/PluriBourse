---
baseline_commit: 27633b8fd037b21314e9a3765575cc06c00b5091
---

# Story 5.2: Génération du bilan de vente PDF

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole ou administrateur,
I want générer un bilan de vente par vendeur affichant les articles vendus, les invendus et le reversement net,
so that les vendeurs puissent récupérer leur paiement avec un détail complet, et repérer leurs invendus par table avant de solder.

**Point d'entrée du sprint.** L'ordre naturel du sprint status pointait vers la Story 2.7 (backlog), mais elle reste bloquée : elle dépend de la Story 5.1 (désormais done) ET d'une story de génération du bilan d'édition PDF (Story 5.4, Epic 5, toujours backlog) — dépendance documentée dans sprint-status.yaml le 2026-07-30, toujours valable. 5.2 est la story suivante réalisable : elle enchaîne directement sur le bouton « Imprimer le bilan de vente » explicitement laissé non fonctionnel à la fin de la Story 5.1 (AC 6 de cette story, différé ici par décision actée le 2026-08-14).

**Périmètre : full-stack.** Nouveau renderer PDF (patron `InvoiceRenderer`/`DepositSlipRenderer`, Story 3.6/4.5) + un nouveau service/endpoint d'impression (patron `PosInvoicePrintService`/`PosSaleController`, Story 4.5) + le bouton dans `SettlementListComponent` (Story 5.1) côté frontend.

## Acceptance Criteria

1. **Contenu du PDF (FR-050).** Étant donné qu'un bilan de vente est demandé pour un vendeur, quand le PDF est généré via OpenPDF 3.0.0, alors il contient : les articles **vendus** (nom, prix unitaire), les articles **invendus** (nom, catégorie, numéro de table), le total brut (des articles vendus), la commission déduite, le montant net à reverser. Un lot apparaît sur une seule ligne (nom du lot, prix du lot) dans sa section (vendus ou invendus selon son statut), quel que soit son nombre de membres.
2. **Indicateur incomplet sans effet sur le calcul (FR-089).** Étant donné qu'un vendeur a vendu des articles avec l'indicateur incomplet, quand le reversement net est calculé, alors la commission s'applique au taux plein — l'incomplétude n'affecte ni la commission ni le prix de vente. Toutes les valeurs monétaires utilisent `BigDecimal` (NFR-003).
3. **Langue du document.** Étant donné que la langue des documents de l'édition est « FR », quand le PDF est généré, alors tous les libellés et en-têtes utilisent les entrées de `messages_fr.properties` (résolution par `Edition.documentLanguage`, jamais par la préférence de langue de l'utilisateur connecté — même patron que `InvoiceRenderer`/`DepositSlipRenderer`).
4. **Bouton « Imprimer le bilan » — disponible sur toute ligne, quel que soit le statut (UX-DR22).** Étant donné que le bénévole (`/volunteer/settlement`) ou l'administrateur (`/admin/settlement`) consulte la liste des reversements, alors un bouton « Imprimer le bilan » est disponible sur **chaque** ligne vendeur, quel que soit son statut (Non soldé, Soldé, Non réclamé). Cliquer dessus met le PDF en file d'attente pour impression A4 avec retour visuel (bouton désactivé pendant la requête) et toast succès/erreur. Résout un écart entre deux sources de planification — voir Dev Notes § Écarts, point 1 (décision actée avec l'utilisateur le 2026-08-14) : l'AC 6 de la Story 5.1 (epics.md) ne mentionnait le bouton que pour un vendeur déjà Soldé, mais les deux maquettes UX (`mock-volunteer-settlement.html`, `mock-admin-settlement.html`) et `EXPERIENCE.md` (Flow 5, Component Patterns) le montrent disponible en permanence — seuls « Solder »/« Non réclamé » se masquent une fois le vendeur soldé (comportement déjà en place dans `SettlementListComponent` depuis la Story 5.1, à ne pas modifier).
5. **Impression admin (UX-DR22).** Étant donné que l'administrateur clique sur « Imprimer le bilan » depuis `/admin/settlement`, alors le même PDF est généré et mis en file d'attente pour impression, exactement comme côté bénévole. Nécessite d'ouvrir l'accès à l'écran `/printer-selection` à l'administrateur — voir Dev Notes § Écarts, point 2 (décision actée avec l'utilisateur le 2026-08-14) : un compte ADMIN ne passe aujourd'hui jamais par cet écran (FR-098 est un interstitiel exclusivement bénévole après connexion), donc sa session n'a jamais d'imprimante A4 sélectionnée — sans ce changement, le clic échouerait systématiquement en 422 `invalid-printer-selection`.

## Tasks / Subtasks

### Backend

- [x] **`ItemRepository` — items d'un vendeur avec catégorie chargée (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE — lire le fichier en entier avant modification, respecter le style Javadoc existant) : nouvelle méthode
    ```java
    /**
     * Seller sales report PDF (story 5.2): all items (sold and unsold) for one seller, captured
     * into a PrintJob closure like {@link #findAllBySaleIdOrderById} — JOIN FETCH category in
     * addition to lot (unlike findAllBySellerProfileIdOrderByItemNumberAsc), since unsold items
     * must show their category name (FR-050) and the renderer never touches sellerProfile/edition.
     */
    @Query("SELECT i FROM Item i JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.sellerProfile.id = :sellerProfileId ORDER BY i.itemNumber ASC")
    List<Item> findAllBySellerProfileIdForSettlementReport(@Param("sellerProfileId") Long sellerProfileId);
    ```
    Ne pas réutiliser/modifier `findAllBySellerProfileIdOrderByItemNumberAsc` (Story 3.5, thermal labels) : elle ne fetch pas `category`, et c'est un fichier stable déjà testé pour un autre usage — un ajout de `JOIN FETCH` non nécessaire à son appelant actuel serait un changement hors périmètre de cette story.

- [x] **`SettlementService` — élargir la visibilité de `requireSellerOfEdition` (AC 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` (UPDATE — lire le fichier en entier) : changer `private SellerProfile requireSellerOfEdition(...)` en package-private (retirer `private`) pour que `SettlementReportPrintService` (même package `org.pluribourse.domain.payout.service`) le réutilise tel quel — même garde IDOR (404 générique) que `settle`/`markUnclaimed`, une seule vérité. Ne rien changer d'autre à ce fichier (`settle`, `markUnclaimed`, `getSettlements`, `persistSettlement` restent identiques).

- [x] **`SettlementReportRenderer` — nouveau renderer PDF (AC 1, 2, 3)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java` (NEW) — même patron exact que `InvoiceRenderer`/`DepositSlipRenderer` (polices CP1252 non embarquées en `static {}`, `MessageSource` injecté, `PdfWriter.setCompressionLevel(PdfStream.NO_COMPRESSION)` pour rester greppable en test) :
    ```java
    public byte[] renderReport(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        // items = TOUS les items du vendeur (vendus + invendus), déjà JOIN FETCH category+lot
        // Même patron d'en-tête que DepositSlipRenderer.renderSlip : titre, sellerProfile.getFirstName()
        //   + getLastName(), sellerProfile.getEdition().getName() — cette dernière lecture lazy est
        //   déjà le patron exact et validé de DepositSlipRenderer (Story 3.6, done, jamais de
        //   LazyInitializationException en production sur cet appel précis), donc SANS risque
        //   nouveau ici : reproduire tel quel, ne pas extraire l'edition en paramètre séparé.
        List<Item> soldItems = items.stream().filter(Item::isSold).toList();
        List<Item> unsoldItems = items.stream().filter(i -> !i.isSold()).toList();

        // Section "Articles vendus" : ItemPricing.distinctByLot(soldItems), 2 colonnes (nom, prix)
        //   — même table que InvoiceRenderer.buildItemsTable, clé print.settlementReport.column.*
        // Section "Articles invendus" : ItemPricing.distinctByLot(unsoldItems), 3 colonnes
        //   (nom, catégorie, table) — catégorie/table lues sur l'item représentatif du lot
        //   (celui retourné par distinctByLot), item.getCategory().getName() / item.getTableNumber()
        BigDecimal total = ItemPricing.computeTotal(soldItems);
        BigDecimal net = ItemPricing.computeNetPayout(total, commissionRate);
        // print.settlementReport.totalGross / .commission / .netAmount, même style que
        // print.slip.commission / print.slip.netAmount (DepositSlipRenderer)
    }
    ```
    Ne pas fusionner avec `DepositSlipRenderer` ni `InvoiceRenderer` malgré la ressemblance structurelle : les trois documents sont déjà indépendamment testés (story 3.6/4.5), et ce renderer a une structure réellement différente (deux tables au lieu d'une, section invendus inexistante ailleurs). Pas de nom d'association ici (contrairement à `InvoiceRenderer`) : ni l'AC 1 de cette story ni les maquettes ne le demandent pour ce document destiné au vendeur — rester au plus près de `DepositSlipRenderer`, le précédent le plus proche (même destinataire : le vendeur).
  - [x] `pluribourse-backend/src/main/resources/messages_fr.properties` (UPDATE, après le bloc `print.invoice.*`) :
    ```properties
    # Seller sales report (bilan de vente) PDF rendering (Story 5.2)
    print.settlementReport.title=Bilan de vente
    print.settlementReport.soldSection=Articles vendus
    print.settlementReport.unsoldSection=Articles invendus
    print.settlementReport.column.item=Article
    print.settlementReport.column.price=Prix
    print.settlementReport.column.category=Catégorie
    print.settlementReport.column.table=Table
    print.settlementReport.totalGross=Total brut : {0}€
    print.settlementReport.commission=Taux de commission : {0}%
    print.settlementReport.netAmount=Reversement net : {0}€
    ```
  - [x] `pluribourse-backend/src/main/resources/messages_en.properties` (UPDATE) — même structure, traduction anglaise (ex. « Sales report », « Sold items », « Unsold items », « Category », « Table », « Gross total: {0}€ », « Commission rate: {0}% », « Net payout: {0}€ »).

- [x] **`DocumentPrintService` — nouveau job (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE — lire le fichier en entier) : injecter `SettlementReportRenderer`, ajouter
    ```java
    public PrintJob buildSettlementReportJob(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        return printer -> printSettlementReport(printer.getPrinterBridgeId(), sellerProfile, items, commissionRate, documentLocale);
    }

    private void printSettlementReport(String printerBridgeId, SellerProfile sellerProfile, List<Item> items,
            BigDecimal commissionRate, Locale documentLocale) {
        byte[] pdf = settlementReportRenderer.renderReport(sellerProfile, items, commissionRate, documentLocale);
        printPdf(printerBridgeId, pdf);
    }
    ```
    Même patron exact que `buildInvoiceJob`/`printInvoice` juste au-dessus dans ce fichier — ne pas réordonner les méthodes existantes.

- [x] **`SettlementReportPrintService` — nouveau service (AC 1, 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementReportPrintService.java` (NEW) — même patron exact que `PosInvoicePrintService` (Story 4.5) : `@Transactional(readOnly = true)`, `commissionRate`/`documentLocale` extraits en valeurs simples avant de construire le `PrintJob` (il s'exécute plus tard, sur le thread consommateur de la file, après la fin de cette transaction — piège `LazyInitializationException` déjà rencontré deux fois sur ce module, cf. Javadoc `PosInvoicePrintService`). `seller` (`SellerProfile`), lui, est passé tel quel dans la closure — pas une violation de cette règle : `SettlementReportRenderer` ne touche que `getFirstName()`/`getLastName()`/`getEdition().getName()`, exactement l'appel déjà validé en production par `DepositSlipRenderer` depuis la Story 3.6, jamais `Edition`/`Sale` dans leur ensemble (le cas qui a réellement posé problème en 3.5/4.5) :
    ```java
    @Service
    @RequiredArgsConstructor
    public class SettlementReportPrintService {

        private final ItemRepository itemRepository;
        private final EditionService editionService;
        private final SettlementService settlementService; // requireSellerOfEdition seulement
        private final PrinterSelectionService printerSelectionService;
        private final PrintQueueService printQueueService;
        private final DocumentPrintService documentPrintService;

        public void printReport(Long sellerId, HttpSession session) {
            Edition edition = editionService.getActiveEdition();
            PhaseGuard.requirePostSalePhase(edition); // même garde que SettlementService — défense
                // en profondeur, la page /volunteer|admin/settlement est déjà phase-gated côté client
            SellerProfile seller = settlementService.requireSellerOfEdition(sellerId, edition); // réutilisé, pas dupliqué
            BigDecimal commissionRate = edition.getCommissionRate(); // lu avant submit, edition reste géré tout du long de cette transaction readOnly
            Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

            List<Item> items = itemRepository.findAllBySellerProfileIdForSettlementReport(sellerId);

            Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                    .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
            if (!printQueueService.isAvailable(a4PrinterId)) {
                throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
            }

            printQueueService.submit(a4PrinterId,
                    documentPrintService.buildSettlementReportJob(seller, items, commissionRate, documentLocale));
        }
    }
    ```
    Ne pas ajouter ce comportement à `SettlementService` : ce service reste dédié au flux de solde (Story 5.1), même séparation que `PosInvoicePrintService`/`PosBasketService` (Story 4.5 Dev Notes). Réutilise `InvalidPrinterSelectionException` existant (pas de nouvelle exception) — même code d'erreur `invalid-printer-selection` que le dépôt/la facture.

- [x] **`PrinterSelectionController` — élargir l'accès à ADMIN (AC 5)** — écart découvert pendant `dev-story`, non anticipé en `create-story` : `@PreAuthorize("hasRole('VOLUNTEER')")` bloquait tout accès admin à `/printers/available`/`/printers/selection` au niveau serveur, indépendamment du lien frontend. Décision utilisateur (2026-08-14) : remplacé par `@PreAuthorize("hasAnyRole('VOLUNTEER', 'ADMIN')")`. Régression de test connue et corrigée : `PrinterSelectionIT.admin_session_is_forbidden_on_all_endpoints` (Story 3.9, done) encodait l'ancien comportement — renommé `admin_session_can_reach_all_endpoints`, assertions inversées (200 au lieu de 403).
- [x] **`SettlementController` — nouvel endpoint (AC 1, 4, 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/SettlementController.java` (UPDATE) : injecter `SettlementReportPrintService`, ajouter
    ```java
    @PostMapping("/{sellerId}/report/print")
    public ResponseEntity<Void> printReport(@PathVariable Long sellerId, HttpSession session) {
        reportPrintService.printReport(sellerId, session);
        return ResponseEntity.noContent().build();
    }
    ```
    Pas de nouvelle règle de sécurité — même route partagée ADMIN+VOLUNTEER que le reste de `/settlements` (pas de `@PreAuthorize`, patron déjà établi Story 5.1).

- [x] **Backend — test E2E dédié (AC 1, 2, 3, 4, 5)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` (NEW) — même famille que `InvoicePrintingIT`/`DepositSlipPrintingIT` (co-localisées dans `domain.print` malgré leur déclencheur métier dans un autre package, précédent déjà établi) : `PrinterBridgeDouble`, storyboard `@Order`. Scénario suggéré :
    1. Créer une édition (commission 10%), un vendeur, un article standard vendu (via le flux POS existant) et un article standard NON vendu, avancer l'édition jusqu'à Post-vente.
    2. Enregistrer/sélectionner une imprimante A4 pour la session volontaire.
    3. Appel direct de `settlementReportRenderer.renderReport(...)` : vérifie que le PDF contient l'article vendu dans la section « Articles vendus », l'article invendu avec sa catégorie et son numéro de table dans la section « Articles invendus », le total brut, la commission (10%) et le net (FR-050) — même style d'assertions que `InvoicePrintingIT` Order 7 (`countOccurrences` pour prouver qu'un lot n'apparaît qu'une fois si un cas de lot est ajouté au scénario).
    4. `document_print_service` : job construit avec un `PrinterBridgeClient` mocké, vérifie l'envoi des bytes PDF (`%PDF`) — même style qu'`InvoicePrintingIT` Order 8.
    5. `POST /settlements/{sellerId}/report/print` via HTTP → 204, job mis en file (même style qu'Order 9 : le job échoue contre `PrinterBridgeDouble` qui ne supporte pas le WebSocket, ce qui suffit à prouver que le chemin contrôleur → service → `PrinterBridgeClient` réel s'exécute sans lever avant d'atteindre `PrinterBridgeClient`).
    6. Rejouabilité : un second appel après `discard` de la file en erreur est encore accepté (204) — aucune notion de « déjà imprimé ».
    7. Garde de phase : `settlement-not-allowed` (422) hors Post-vente.
    8. IDOR : vendeur d'une autre édition → 404 générique (même raisonnement qu'`SettlementIT` Order 9).
    9. Pas d'imprimante A4 sélectionnée → 422 `invalid-printer-selection` (même style qu'`InvoicePrintingIT` Order 12).
    10. Accessible aussi bien par une session admin que bénévole (pas de `@PreAuthorize`).
  - [x] Vérifier `SettlementIT` (Story 5.1, done) — aucune régression attendue après le changement de visibilité de `requireSellerOfEdition`, mais à re-exécuter explicitement puisqu'elle n'est pas modifiée par cette story.

### Frontend

- [x] **`SettlementService` — nouvel appel (AC 1, 4, 5)**
  - [x] `pluribourse-frontend/src/app/services/settlement.service.ts` (UPDATE) : ajouter
    ```typescript
    printReport(sellerId: number): Observable<void> {
      return this.http.post<void>(`/api/settlements/${sellerId}/report/print`, null);
    }
    ```
  - [x] `pluribourse-frontend/src/app/services/settlement.service.spec.ts` (UPDATE) — nouveau scénario pour `printReport`, même style que les tests existants de `settle`/`markUnclaimed`.

- [x] **`SettlementListComponent` — bouton d'impression (AC 4, 5)**
  - [x] `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` (UPDATE — lire le fichier en entier) :
    - Ajouter `readonly printingReportForSellerId = signal<number | null>(null);`
    - Nouvelle méthode `async printReport(settlement: SettlementDto): Promise<void>` — même style que `deposit-page.component.ts.reprintDepositSlip()` (Story 3.6) : garde de réentrance (`if (this.printingReportForSellerId() !== null) return;`), `try/catch/finally`, distinction du 422 `invalid-printer-selection` (toast `settlement.error.printerUnavailable`) des autres erreurs (toast générique `settlement.error.printReport`), toast succès `settlement.success.printReport` sinon. **Aucune confirm dialog** — contrairement à `reprintDepositSlip`/`reprintLabels`, ni epics.md AC 4 de cette story ni le mockup n'en montrent une pour ce bouton (mise en file immédiate au clic, cf. AC 4).
  - [x] `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html` (UPDATE — lire le fichier en entier) : ajouter le bouton « Imprimer le bilan » **en dehors** du bloc `@if (settlement.status === 'UNSETTLED')` existant (ligne ~60) — il doit apparaître pour TOUTE ligne, `Solder`/`Non réclamé` restant conditionnés à `UNSETTLED` comme aujourd'hui. Structure suggérée :
    ```html
    <td>
      <div class="actions-cell">
        <button type="button" mat-button [disabled]="printingReportForSellerId() !== null" (click)="printReport(settlement)">
          <mat-icon aria-hidden="true">print</mat-icon>
          {{ 'settlement.actions.printReport' | translate }}
        </button>
        @if (settlement.status === 'UNSETTLED') {
          <button type="button" mat-button color="primary" [disabled]="submitting()" (click)="openSettleForm(settlement)">
            {{ 'settlement.actions.settle' | translate }}
          </button>
          <button type="button" mat-button [disabled]="submitting()" (click)="confirmUnclaimed(settlement)">
            <mat-icon aria-hidden="true">block</mat-icon>
            {{ 'settlement.actions.unclaimed' | translate }}
          </button>
        }
      </div>
    </td>
    ```
  - [x] `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts` (UPDATE) — nouveaux scénarios : bouton visible sur une ligne `SETTLED`/`UNCLAIMED` (pas seulement `UNSETTLED`), clic déclenche `settlementService.printReport`, toast succès, 422 `invalid-printer-selection` → toast `printerUnavailable`, autre erreur → toast générique `printReport`, bouton désactivé pendant la requête pour cette ligne uniquement (une seconde ligne reste cliquable).

- [x] **Ouvrir `/printer-selection` à l'administrateur (AC 5)**
  - [x] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE — lire les lignes 44–54 en entier) : retirer le `@if (isVolunteer())` (ligne 49) qui masque le lien « Sélection d'imprimante » du menu utilisateur pour un ADMIN — le lien devient inconditionnel pour tout utilisateur connecté (même route `/printer-selection`, même clé i18n `nav.printerSelection` déjà existante, aucune nouvelle entrée de sidebar à créer).
  - [x] `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` (UPDATE — lire les lignes 95–126 en entier, **régression connue**) : le test `'does not render a link to /printer-selection in the user menu for an admin'` (ligne 110) encode l'ancien comportement volontairement retiré ci-dessus et **va échouer** tel quel — le remplacer par `'renders a link to /printer-selection in the user menu for an admin'` avec l'assertion inversée (`expect(link).toBeTruthy()`), même structure que le test symétrique bénévole juste en dessous (ligne 118).
  - [x] `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.ts` (UPDATE — lire le fichier en entier) : `onSubmit()` redirige aujourd'hui inconditionnellement vers `/volunteer` (ligne 51) — un admin y atterrirait sur une page bénévole après sélection. Injecter `AuthService`, rendre la redirection sensible au rôle :
    ```typescript
    private readonly auth = inject(AuthService);
    // ...
    const target = this.auth.currentUser()?.role === 'ADMIN' ? '/admin' : '/volunteer';
    await this.router.navigate([target]);
    ```
  - [x] `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.spec.ts` (UPDATE — lire le fichier en entier) : le(s) test(s) existant(s) vérifiant la redirection après soumission encodent probablement `/volunteer` en dur pour tout rôle — vérifier et ajouter un scénario ADMIN (`router.navigate` appelé avec `['/admin']`) à côté du scénario bénévole existant (`['/volunteer']`).

- [x] **i18n (AC 1, 4)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE, dans le namespace `settlement` existant) :
    ```json
    "actions": {
      "settle": "Solder",
      "unclaimed": "Non réclamé",
      "confirm": "Valider",
      "cancel": "Annuler",
      "reprintDepositSlip": "Réimprimer le bordereau de dépôt",
      "printReport": "Imprimer le bilan"
    },
    ```
    Et sous `success`/`error` :
    ```json
    "success": {
      "settle": "Vendeur réglé.",
      "unclaimed": "Montant transféré aux recettes de l'association.",
      "printReport": "Bilan envoyé à l'imprimante."
    },
    "error": {
      "load": "Impossible de charger la liste des reversements.",
      "noActiveEdition": "Aucune édition active.",
      "settle": "Impossible d'enregistrer le solde.",
      "printReport": "Impossible d'imprimer le bilan.",
      "printerUnavailable": "Aucune imprimante A4 disponible. Sélectionnez une imprimante."
    }
    ```
    Vérifier les clés `error`/`success` exactes déjà présentes (voir fichier) avant d'ajouter — ne pas dupliquer si un libellé générique équivalent existe déjà.
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — même structure, traduction anglaise.

### Review Findings

_Revue bmad-code-review (Blind Hunter + Edge Case Hunter + Acceptance Auditor), diff `27633b8..a00fd31`, 2026-08-14._

- [x] [Review][Patch] Lot partiellement vendu : doublon inter-sections et prix manquant en section invendus — `SettlementReportRenderer.java` (`renderReport`/`buildUnsoldItemsTable`) partitionne `items` en `soldItems`/`unsoldItems` via `Item::isSold` **avant** d'appeler `ItemPricing.distinctByLot()` séparément sur chaque partition. Or `PosBasketService.buildLotGroups`/`LotGroupDto.scannedCount` prouvent qu'un lot peut être vendu partiellement (certains membres scannés/vendus, d'autres non). Conséquence confirmée par 3 agents de revue indépendamment : un tel lot apparaissait sur deux lignes et son prix plein était compté même partiellement vendu ; un lot entièrement invendu n'affichait jamais son prix, contrairement à l'AC 1. **Décision utilisateur (2026-08-14) :** (a) un lot est considéré "vendu" pour ce rapport dès qu'au moins un de ses membres est vendu — tous ses membres rejoignent alors la section Vendus, prix plein compté une fois (cohérent avec `ItemPricing.computeTotal` déjà en place, aucun changement de calcul financier requis) ; (b) le tableau "Articles invendus" gagne une 4e colonne "Prix" (clé i18n `print.settlementReport.column.price`, déjà existante), remplie uniquement pour les lignes de lot, vide pour les articles individuels. **Appliqué :** calcul de `soldLotIds` (IDs des lots ayant au moins un membre vendu) avant la partition, chaque item de lot routé selon `soldLotIds.contains(lot.getId())` plutôt que son propre `isSold()` ; 4e colonne `Prix` ajoutée à `buildUnsoldItemsTable`. Nouveau scénario de test avec un lot à statut mixte (Lot Mixte) et un lot entièrement invendu (Lot Invendu) — couvre aussi le defer "AC 1 lot dedup jamais testé".
- [x] [Review][Patch] AC 2 (indicateur incomplet) non couvert par un test malgré la story l'annonçant couvert par `SettlementReportPrintingIT` [pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java] — **Appliqué :** article "Doudou" (`incomplete=true`) ajouté au scénario, vendu, total/net vérifiés inchangés (commission pleine).
- [x] [Review][Patch] AC 3 (langue du document) jamais testée de bout en bout — le test Order 8 appelle `settlementReportRenderer.renderReport(..., Locale.FRENCH)` directement, sans passer par `SettlementReportPrintService.printReport()` avec une édition `Language.EN` [pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java] — **Appliqué :** nouveau test Order 9, même items rendus avec `Locale.ENGLISH`, assertions positives (labels EN) et négatives (absence des labels FR).
- [x] [Review][Patch] Numéro de table non vérifié par assertion dans le test de contenu PDF (Order 8), alors que le Javadoc de la classe de test prétend le couvrir [pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java] — **Appliqué :** table de la catégorie changée de 1 à 7 (évite toute collision avec les montants du scénario) et assertion `.contains("7")` ajoutée.
- [x] [Review][Dismiss] Commentaire de remplacement "mal indenté" (8 espaces au lieu de 4) [pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterSelectionIT.java:197] — faux positif, vérifié : l'indentation à 8 espaces pour un commentaire placé entre les annotations et la signature de méthode est le style déjà établi dans tout le module (`InvoicePrintingIT.java:267`, `290` ; `SettlementReportPrintingIT.java` Order 8/9), pas une incohérence introduite ici.
- [x] [Review][Defer] TOCTOU non-atomique `isAvailable`/`submit` dans `SettlementReportPrintService.printReport` [pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementReportPrintService.java] — deferred, pre-existing (même dette déjà actée pour `PosInvoicePrintService`/4.5 et services jumeaux)
- [x] [Review][Defer] `SettlementReportPrintingIT` Order 10 ne peut pas distinguer une vraie `LazyInitializationException` cross-thread d'un simple échec WebSocket du double de test [pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java] — deferred, pre-existing (limitation connue de `PrinterBridgeDouble`, déjà présente pour `InvoicePrintingIT`/`DepositSlipPrintingIT`)
- [x] [Review][Defer] `DocumentPrintService.buildSettlementReportJob` capture `SellerProfile` entier dans la closure du `PrintJob` plutôt que d'en extraire des valeurs simples [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java] — deferred, pre-existing (reproduit sciemment, et documenté dans la story, le patron déjà validé en production par `DepositSlipRenderer` depuis la Story 3.6)
- [x] [Review][Defer] Aucun scénario testé pour un vendeur sans aucun article vendu ni invendu (rapport à deux tableaux vides) [pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java] — deferred, pre-existing (cas limite réel mais mineur, même famille que les defers de couverture déjà actés sur ce module)

**Rejetés comme bruit (6), vérifiés contre le code source réel :**
- `net`/`commissionRate` non normalisés avant `toPlainString()` — faux : `ItemPricing.computeNetPayout` applique déjà `.setScale(2, HALF_UP)`, et `commissionRate` est `DECIMAL(5,2)` garanti par la colonne BDD (`Edition.java:32`, `precision=5, scale=2`).
- `@PreAuthorize` élargie "à tout le contrôleur, pas seulement l'endpoint de sélection" — faux : `PrinterSelectionController` ne contient que les 3 endpoints nécessaires à la sélection d'imprimante (`/available`, `GET/POST /selection`), aucun endpoint étranger exposé par erreur.
- Toast unique pour deux causes 422 différentes (pas d'imprimante sélectionnée vs imprimante indisponible) — reproduit fidèlement le patron déjà établi `reprintDepositSlip`/`reprintLabels` (Story 3.6), comportement délibérément copié, pas une régression introduite ici.
- `printer-selection.component.ts` — comparaison stricte `=== 'ADMIN'` sans traiter un rôle "inattendu" — la route est déjà protégée en amont (backend `hasAnyRole('VOLUNTEER','ADMIN')`), un rôle tiers ne peut pas atteindre ce point après une soumission réussie ; patron ternaire déjà utilisé ailleurs dans le projet.
- Croissance du commentaire YAML `sprint-status.yaml` en une ligne géante — observation méta sur le processus, pas un défaut de code introduit par ce diff.
- Chiffres de tests auto-déclarés dans le Dev Agent Record ("406/406 backend, 560/560 frontend") non vérifiables depuis le diff — observation méta, pas un défaut de code.

**Patches appliqués (4/4) et revalidés :** `./mvnw test` (suite complète) : 407/407 tests backend (406 + 1 net, `SettlementReportPrintingIT` passée de 13 à 14 scénarios), aucune régression. Aucun changement frontend nécessaire pour ces patchs.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`InvoiceRenderer`/`DepositSlipRenderer`** (`domain/print/service/`) : patron exact à reproduire pour `SettlementReportRenderer` — polices CP1252 non embarquées en bloc `static {}` (le constructeur `Font(family, size, style)` sans `BaseFont` bascule silencieusement sur une police CID Unicode embarquée dès qu'un `€` est rendu), `PdfWriter.setCompressionLevel(PdfStream.NO_COMPRESSION)` pour garder le flux greppable en test, `MessageSource` injecté pour toutes les chaînes.
- **`ItemPricing.computeTotal`/`computeNetPayout`/`distinctByLot`** (`domain/item/service/ItemPricing.java`) : déjà la seule vérité de calcul lot-aware et de commission — ne jamais réécrire une variante locale dans `SettlementReportRenderer`.
- **`PosInvoicePrintService`** (`domain/pos/service/`, Story 4.5) : patron exact pour `SettlementReportPrintService` — extraction en valeurs simples avant construction du `PrintJob`, `PrinterSelectionService.getSelectedPrinterId`/`PrintQueueService.isAvailable`/`submit`, `InvalidPrinterSelectionException` réutilisée telle quelle (pas de nouvelle exception).
- **`SettlementService.requireSellerOfEdition`** (Story 5.1) : garde IDOR déjà écrite et testée — élargie en visibilité (pas dupliquée) pour `SettlementReportPrintService`.
- **`PhaseGuard.requirePostSalePhase`** (Story 5.1) : réutilisée telle quelle pour l'endpoint d'impression — même défense en profondeur que le reste de `/settlements`.
- **Clé i18n `nav.printerSelection`** (déjà existante, `"Sélection d'imprimante"`) : réutilisée telle quelle pour l'ouverture du lien à l'admin, aucune nouvelle clé de libellé nécessaire pour ce point.

### Écarts par rapport aux sources de planification — actés avec l'utilisateur le 2026-08-14

1. **Visibilité du bouton « Imprimer le bilan » — epics.md vs. maquettes UX.** L'AC 6 de la Story 5.1 dans epics.md ne décrit le bouton que pour un vendeur déjà Soldé (« Étant donné qu'un vendeur a été soldé... alors un bouton... est disponible »). Mais `mock-volunteer-settlement.html` montre le bouton sur ses 3 lignes, toutes « Non soldé », et `mock-admin-settlement.html` porte un commentaire explicite dans son état « ligne soldée » : « Actions Solder et Non réclamé masquées, seul Imprimer reste disponible » — impliquant sans ambiguïté que le bouton est présent AVANT le solde aussi. `EXPERIENCE.md` Flow 5 confirme : le bénévole imprime le bilan **avant** de solder, pour que le vendeur puisse d'abord trier ses invendus par table. **Décision utilisateur : le bouton est toujours visible, quel que soit le statut** (AC 4 de cette story). Les deux maquettes + Flow 5 + le Component Pattern dédié (« Récapitulatif reversement imprimable ») convergent sans ambiguïté vers ce comportement ; l'AC 6 de 5.1 n'était qu'une description partielle (un seul cas d'exemple), pas une restriction voulue.
2. **Accès admin à `/printer-selection` — comportement volontairement restreint jusqu'ici.** `AppLayoutComponent` masque aujourd'hui explicitement le lien vers `/printer-selection` dans le menu utilisateur pour un ADMIN (`@if (isVolunteer())`, `app-layout.component.html:49`), comportement couvert par un test dédié (`app-layout.component.spec.ts:110`, `'does not render a link to /printer-selection in the user menu for an admin'`). C'est cohérent avec le fait qu'aucune action d'impression déclenchée par un admin n'existait avant cette story — FR-098 (l'interstitiel post-connexion) reste, lui, strictement bénévole et n'est pas modifié. **Décision utilisateur : ouvrir ce lien à l'admin** (seul changement : la visibilité conditionnelle du lien existant, pas un nouvel écran ni une nouvelle route) et rendre `PrinterSelectionComponent.onSubmit()` sensible au rôle pour la redirection post-sélection (aujourd'hui codée en dur vers `/volunteer`). Le test cité ci-dessus doit être inversé, pas supprimé — voir Tasks § `app-layout.component.spec.ts`.
3. **Nommage `SettlementReportPrintService`/`SettlementReportRenderer`.** epics.md/architecture.md ne nomment aucune classe pour cette story (contrairement à Story 5.1 où `architecture.md` documentait `PayoutService`/`PayoutController`, déjà écarté en 5.1). Noms choisis par cohérence avec `PosInvoicePrintService`/`InvoiceRenderer` (Story 4.5, patron le plus proche : un bouton + un renderer livrés dans la même story).

### Project Structure Notes

- Aucun nouveau package — `SettlementReportRenderer`/`DocumentPrintService` (UPDATE) vivent dans `org.pluribourse.domain.print.service` (déjà existant), `SettlementReportPrintService` dans `org.pluribourse.domain.payout.service` (déjà existant depuis la Story 5.1).
- Aucune migration Liquibase — cette story ne touche aucune donnée persistée, uniquement de la lecture (`ItemRepository`) et de la génération de document à la volée.
- Fichiers UPDATE en dehors du nouveau périmètre (à lire intégralement avant modification) : `ItemRepository.java`, `SettlementService.java`, `DocumentPrintService.java`, `SettlementController.java`, `messages_fr.properties`, `messages_en.properties`, `settlement.service.ts`, `settlement-list.component.ts`/`.html`, `app-layout.component.html`/`.spec.ts`, `printer-selection.component.ts`/`.spec.ts`, `fr.json`, `en.json`.

### Fichiers à lire avant modification

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java`, `DepositSlipRenderer.java` (référence directe — patron complet à reproduire pour `SettlementReportRenderer`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE — lire en entier)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java`, `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosSaleController.java` (référence directe — patron complet à reproduire pour `SettlementReportPrintService`/l'endpoint)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` (UPDATE — lire en entier, notamment `requireSellerOfEdition`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/SettlementController.java` (UPDATE — lire en entier)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (UPDATE — lire en entier pour respecter le style des requêtes/Javadoc existantes)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java`, `EditionCategory.java` (référence — champs disponibles : `category.name`, `tableNumber`, `sold`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (référence directe — patron complet de test à reproduire, y compris `PrinterBridgeDouble`/`waitUntil`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java` (référence — patron de setup édition/vendeurs/phase, à ne pas modifier)
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts`/`.html`/`.spec.ts` (UPDATE — lire en entier, notamment la ligne ~60 `@if (settlement.status === 'UNSETTLED')`)
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` (référence directe — patron `reprintDepositSlip`/`reprintLabels` : garde de réentrance, gestion 422 `invalid-printer-selection`)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE — lire les lignes 44–54 en entier) et `.spec.ts` (UPDATE — lire les lignes 95–126 en entier, un test existant doit être inversé)
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.ts` (UPDATE — lire en entier, redirection ligne 51) et `.spec.ts` (UPDATE)
- `pluribourse-frontend/src/app/services/settlement.service.ts`/`.spec.ts` (UPDATE — lire en entier)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-volunteer-settlement.html`, `mock-admin-settlement.html` (référence directe — bouton « Imprimer le bilan » présent sur toutes les lignes, commentaire explicite sur l'état ligne soldée)
- `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md` (référence directe — Flow 5, Component Patterns « Récapitulatif reversement imprimable »)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.2] — ACs source (FR-050, FR-089 ; l'AC de langue du document n'est rattachée à aucun FR précis dans epics.md, cf. F1 i18n double-couche dans architecture.md)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.1] — AC 6 (bouton différé ici), voir Dev Notes § Écarts point 1 pour la résolution du conflit avec les maquettes
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] — FR-050, FR-089 (texte normatif complet)
- [Source: _bmad-output/planning-artifacts/implementation-readiness-report-2026-06-12.md] — confirme le partage du trigger d'impression entre 5.1 (bouton) et 5.2 (génération)
- [Source: _bmad-output/planning-artifacts/architecture.md#Structure des packages] — patron `payout/`/`print/` (les mêmes écarts de nommage package déjà actés en Story 5.1 s'appliquent)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — Flow 5 (bilan avant solde), Component Patterns (« Récapitulatif reversement imprimable »), tableaux de routes Admin/Bénévole
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-volunteer-settlement.html] — bouton présent sur toutes les lignes
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-admin-settlement.html] — état « ligne soldée », commentaire explicite sur la persistance du bouton Imprimer
- [Source: _bmad-output/implementation-artifacts/5-1-flux-de-solde-des-vendeurs.md] — story précédente directe (package `payout`, `SettlementService`, `SettlementListComponent`, décisions déjà actées)
- [Source: _bmad-output/implementation-artifacts/4-5-impression-de-la-facture-acheteur.md] — patron complet (renderer + service + contrôleur + test IT) le plus proche de cette story
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/**, payout/**, item/**, pos/**] — lus intégralement pour les patrons réutilisés
- [Source: pluribourse-frontend/src/app/**] — fichiers listés en § Fichiers à lire, lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

Écart réel non anticipé en `create-story`, découvert au premier lancement du test E2E : `PrinterSelectionController` porte `@PreAuthorize("hasRole('VOLUNTEER')")` au niveau classe — un ADMIN aurait reçu 403 sur `/printers/available` et `/printers/selection` même avec le lien frontend ouvert (la story avait anticipé le blocage frontend `@if (isVolunteer())` mais pas ce blocage serveur). Documenté et soumis à l'utilisateur avant modification de code de production (conformément à la garde de la story) ; l'utilisateur a choisi d'élargir l'annotation à `hasAnyRole('VOLUNTEER', 'ADMIN')`. Régression de test connue et corrigée : `PrinterSelectionIT.admin_session_is_forbidden_on_all_endpoints` (Story 3.9, done) encodait l'ancien comportement — renommée `admin_session_can_reach_all_endpoints`, assertions inversées. Deuxième ajustement, mineur, découvert en écrivant les tests frontend : le pseudo-code de la story désactivait le bouton d'impression uniquement pour la ligne cliquée (`printingReportForSellerId() === settlement.sellerId`), mais la garde de réentrance dans `printReport()` est globale (un seul signal `number | null`, pas un `Set`) — un clic sur une autre ligne pendant l'impression en cours aurait donc silencieusement échoué sans retour visuel. Corrigé en désactivant tous les boutons d'impression pendant qu'un envoi est en cours (`printingReportForSellerId() !== null`), cohérent avec le fait que la file d'impression backend est de toute façon mono-thread par imprimante (`PrintQueueService`).
Aucun autre écart par rapport au plan de la story. Backend : `./mvnw test` (suite complète) : 406/406 tests backend, aucune régression, `SettlementIT` (Story 5.1) et `DepositSlipPrintingIT`/`InvoicePrintingIT` (constructeur `DocumentPrintService` élargi à 4 arguments, corrigé dans les deux classes) confirmés au vert. Frontend : `npm test` (suite complète) : 560/560 tests frontend (59 fichiers), aucune régression. `npm run build` (production) : aucune erreur TypeScript.

### Completion Notes List

- Backend : `ItemRepository.findAllBySellerProfileIdForSettlementReport` (JOIN FETCH category+lot, réutilisée pour vendus ET invendus). `SettlementService.requireSellerOfEdition` élargie de `private` à package-private, réutilisée telle quelle par `SettlementReportPrintService` (pas de duplication de la garde IDOR). Nouveau renderer `SettlementReportRenderer` (`domain/print/service`, patron `DepositSlipRenderer`/`InvoiceRenderer`, deux tables — vendus 2 colonnes, invendus 3 colonnes — pas de nom d'association, seller + edition en en-tête comme le bordereau de dépôt). `DocumentPrintService.buildSettlementReportJob` (nouveau job, même patron `buildInvoiceJob`). Nouveau service `SettlementReportPrintService` (`domain/payout/service`, distinct de `SettlementService` — même séparation que `PosInvoicePrintService`/`PosBasketService`), nouvel endpoint `POST /settlements/{sellerId}/report/print` sur `SettlementController` existant (204, pas de `@PreAuthorize` nouveau).
- Écart de sécurité découvert et résolu (voir Debug Log) : `PrinterSelectionController` élargi de `hasRole('VOLUNTEER')` à `hasAnyRole('VOLUNTEER', 'ADMIN')` — nécessaire pour que l'AC 5 (impression admin) fonctionne réellement de bout en bout, pas seulement côté UI.
- Nouveau test `SettlementReportPrintingIT` (13 scénarios, storyboard `@Order`, co-localisé dans `domain.print` comme `InvoicePrintingIT`/`DepositSlipPrintingIT`) : contenu du PDF (sections vendus/invendus, total/commission/net), envoi des bytes via `PrinterBridgeClient` mocké, flux HTTP complet (mise en file, rejouabilité), garde de phase, IDOR cross-édition, absence d'imprimante sélectionnée, accessibilité admin ET bénévole (avec sélection d'imprimante admin dédiée, prouvant l'AC 5 de bout en bout). `PrinterSelectionIT` et `DepositSlipPrintingIT`/`InvoicePrintingIT` mis à jour pour la régression de constructeur/sécurité ci-dessus.
- Frontend : `settlement.service.ts.printReport()` (patron `settle`/`markUnclaimed`). `SettlementListComponent` : bouton « Imprimer le bilan » désormais **toujours visible** par ligne (hors du bloc `@if (status === 'UNSETTLED')`), `printingReportForSellerId` (garde de réentrance globale, cf. Debug Log), gestion 422 `invalid-printer-selection` distincte des autres erreurs, aucune confirm dialog (mise en file immédiate). `AppLayoutComponent` : lien « Sélection d'imprimante » du menu utilisateur rendu inconditionnel (retrait de `@if (isVolunteer())`). `PrinterSelectionComponent.onSubmit()` : redirection post-sélection sensible au rôle (`/admin` vs `/volunteer`, `AuthService` injecté).
- i18n : `settlement.actions.printReport`, `settlement.success.printReport`, `settlement.error.printReport`, `settlement.error.printerUnavailable` (FR+EN). Aucune nouvelle clé `nav.*` (réutilisation de `nav.printerSelection` existante). Backend : namespace `print.settlementReport.*` complet (FR+EN).

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementReportPrintService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`

**Backend — UPDATE**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` — `findAllBySellerProfileIdForSettlementReport`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` — `requireSellerOfEdition` élargie en package-private
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` — `buildSettlementReportJob`, injection `SettlementReportRenderer`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/SettlementController.java` — endpoint `POST /{sellerId}/report/print`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterSelectionController.java` — `@PreAuthorize` élargie à ADMIN (écart, voir Debug Log)
- `pluribourse-backend/src/main/resources/messages_fr.properties` — namespace `print.settlementReport.*`
- `pluribourse-backend/src/main/resources/messages_en.properties` — namespace `print.settlementReport.*`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — constructeur `DocumentPrintService` (régression, autowire `SettlementReportRenderer`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` — constructeur `DocumentPrintService` (régression, autowire `SettlementReportRenderer`)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterSelectionIT.java` — test de régression inversé (accès admin désormais autorisé)

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/services/settlement.service.ts` — `printReport`
- `pluribourse-frontend/src/app/services/settlement.service.spec.ts` — nouveau scénario
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` — `printingReportForSellerId`, `printReport()`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html` — bouton « Imprimer le bilan »
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts` — nouveaux scénarios
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — lien `/printer-selection` inconditionnel
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` — test de régression inversé
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.ts` — redirection sensible au rôle
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.spec.ts` — nouveau scénario ADMIN
- `pluribourse-frontend/public/i18n/fr.json` — namespace `settlement.actions/success/error`
- `pluribourse-frontend/public/i18n/en.json` — namespace `settlement.actions/success/error`

## Change Log

- 2026-08-14 — create-story : story créée après clarification avec l'utilisateur sur deux points (visibilité du bouton « Imprimer le bilan » sur toutes les lignes, contrairement à la lettre de l'AC 6 de la Story 5.1 — confirmée par les deux maquettes UX et EXPERIENCE.md Flow 5 ; ouverture de l'écran `/printer-selection` à l'admin, aujourd'hui explicitement masqué par un test dédié, pour que le bouton d'impression admin fonctionne). Statut → ready-for-dev.
- 2026-08-14 — dev-story : implémentation complète full-stack (`SettlementReportRenderer`, `SettlementReportPrintService`, endpoint `POST /settlements/{sellerId}/report/print`, bouton toujours visible dans `SettlementListComponent`). Écart non anticipé découvert et soumis à l'utilisateur avant modification de code de production : `PrinterSelectionController` bloquait l'admin par `@PreAuthorize("hasRole('VOLUNTEER')")` au niveau serveur — élargi à `hasAnyRole('VOLUNTEER', 'ADMIN')`, régression de test `PrinterSelectionIT` inversée. Garde de réentrance du bouton d'impression rendue globale (pas par ligne) pour rester cohérente avec la file d'impression mono-thread par imprimante. 406/406 tests backend, 560/560 tests frontend, build de production sans erreur, aucune régression. Statut → review.
- 2026-08-14 — code-review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : 1 decision-needed résolue avec l'utilisateur (lot partiellement vendu — règle "vendu dès qu'un membre l'est", 4e colonne Prix pour les lots invendus), 4 patch appliqués (fix + 3 lacunes de couverture de test AC 2/AC 3/numéro de table), 4 defer documentés dans `deferred-work.md`, 7 rejetés comme bruit dont 1 faux positif vérifié après application (indentation `PrinterSelectionIT` en réalité conforme au style établi du module). 407/407 tests backend re-validés, aucune régression. Statut → done.
