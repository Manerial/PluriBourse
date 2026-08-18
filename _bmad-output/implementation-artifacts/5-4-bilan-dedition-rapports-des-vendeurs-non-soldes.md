---
baseline_commit: dbe5936d5f9937cb8aafc1fbf2788eeca93cb6c1
---

# Story 5.4: Bilan d'édition & Rapports des vendeurs non soldés

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want des rapports de bilan au niveau de l'édition et une liste des vendeurs non soldés,
so that j'aie une vision financière complète à la clôture de l'événement.

**Périmètre réduit à AC 1 (nouveau développement) — voir Dev Notes § Écarts pour la justification complète.** epics.md liste 4 AC pour cette story. Après analyse du code existant :
- **AC 1** (bilan d'édition PDF, FR-055/FR-094) est le seul travail neuf de cette story.
- **AC 2** (FR-095 — vendeurs non soldés visibles avec téléphone/email via le filtre admin sur `/admin/settlement`) est **déjà entièrement livré par la Story 5.1** (`SettlementListComponent`, colonnes conditionnelles `isAdmin()`, filtre `unsettled`/`settled`/`all`). Aucun code neuf — tâche de vérification uniquement.
- **AC 3 et AC 4** (FR-059 — consultation en lecture seule pendant Clôturée, puis métriques agrégées seules après Archivage) sont **hors périmètre technique de cette story** : elles butent sur une limite architecturale déjà identifiée et documentée dans la Story 5.1 (`EditionService.getActiveEdition()` exclut `CLOSED` de `PhaseType.ACTIVE`, ce qui rend **toute** l'application admin inaccessible dès qu'une édition est clôturée, pas seulement les rapports) et, pour AC 4, sur l'action d'Archivage elle-même (FR-088, table d'archive) qui n'existe pas encore — c'est le périmètre exclusif de la Story 2.7 (backlog). Voir Dev Notes § Écarts pour la recommandation détaillée.

## Acceptance Criteria

1. **Bilan d'édition, FR-055/FR-094 (NOUVEAU — périmètre réel de cette story).** Étant donné que l'édition est en phase Post-vente ou Clôturée, quand l'admin consulte la page des rapports (`/admin/reports`), alors une section « Bilan d'édition » est disponible avec : total des articles vendus/invendus sur toute l'édition, recettes brutes totales, commission totale perçue, ventilation des recettes par moyen de paiement (espèces/chèque/carte) — même structure de données que le bilan journalier (Story 5.3) mais agrégée sur l'édition entière, pas sur une journée. Un bouton « Imprimer » met le PDF en file d'impression A4 (patron `DailySalesReportPrintService`, Story 5.3).
   - **Écart assumé sur la précondition littérale d'epics.md** (« édition clôturée ») : la section est codée visible dès Post-vente, pas seulement Clôturée — cohérent avec le tableau de routes d'EXPERIENCE.md (`Rapports | /admin/reports | Vente (journalier) · Post-vente · Clôturée`, ligne 46) qui traite déjà Post-vente et Clôturée comme la même classe d'accessibilité pour cette page, et avec l'AC 2 de la Story 5.5 qui groupe explicitement « Post-vente ou Clôturée » pour la section synthèse voisine. Toutes les données nécessaires (ventes, articles) sont figées dès la fin de la phase Vente — rien de fonctionnel n'empêche la génération dès Post-vente. **Nuance sur cette citation** : la même table d'EXPERIENCE.md annonce aussi `/admin/settlement` accessible « Post-vente · Clôturée » (ligne 51), ce qui est déjà faux en pratique aujourd'hui pour la même raison que le point 1 des Écarts ci-dessous (`getActiveEdition()` exclut `CLOSED`) — la moitié Clôturée de `/admin/reports` aura le même sort côté frontend tant que cette limite n'est pas résolue (Story 2.7). La citation reste valable pour justifier le choix Post-vente, mais ne pas la traiter comme une preuve que Clôturée fonctionnera réellement à l'écran dès cette story (voir Tasks § `report-page.component.ts` pour le détail).
2. **Vendeurs non soldés — vérification uniquement, FR-095 (déjà livré par la Story 5.1).** Étant donné que l'admin navigue vers `/admin/settlement`, quand la page se charge, alors les vendeurs non soldés sont visibles avec téléphone et email via le filtre « Non soldés » (`SettlementListComponent`, `isAdmin()` pilote l'affichage des colonnes). **Aucun code à écrire** — confirmer par lecture du code + un test manuel visuel (CLAUDE.md § Interaction utilisateur) que ce comportement fonctionne toujours après les changements de cette story ; ne pas dupliquer `SettlementIT`/`settlement-list.component.spec.ts` existants.
3. **Hors périmètre — lecture seule pendant Clôturée, FR-059 (partie 1).** Non implémenté dans cette story. Voir Dev Notes § Écarts.
4. **Hors périmètre — métriques agrégées seules après Archivage, FR-059 (partie 2) + FR-088.** Non implémenté dans cette story — dépend de l'action d'Archivage (Story 2.7, backlog), qui n'existe pas encore. Voir Dev Notes § Écarts.

## Tasks / Subtasks

### Backend

- [x] **`PhaseGuard` — nouvelle garde (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` (UPDATE — lire en entier avant modification) : ajouter, à côté de `requirePostSalePhase`
    ```java
    /**
     * The edition-wide summary report (story 5.4, FR-055) is meaningful only once the Sale
     * phase has ended — reachable in Post-vente and Clôturée alike (EXPERIENCE.md treats both
     * as the same accessibility class for /admin/reports).
     */
    public static void requirePostSaleOrClosedPhase(Edition edition) {
        if (edition.getPhase() != PhaseType.POST_SALE && edition.getPhase() != PhaseType.CLOSED) {
            throw new EditionReportNotAllowedException();
        }
    }
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/EditionReportNotAllowedException.java` (NEW) : `extends BusinessException`, 422, code `edition-report-not-allowed`. Même patron exact que `SettlementNotAllowedException` (même package co-localisé).

- [x] **`EditionService` — résolution d'édition par ID pour les rapports (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` (UPDATE — lire en entier) : exposer publiquement la résolution par ID déjà utilisée en privé par `getEditionById` :
    ```java
    @Transactional(readOnly = true)
    public Edition requireEdition(Long id) {
        return findById(id);
    }
    ```
    **Pourquoi pas `getActiveEdition()`.** `getActiveEdition()` s'appuie sur `PhaseType.ACTIVE`, qui exclut explicitement `CLOSED` — l'utiliser ici rendrait la garde `requirePostSaleOrClosedPhase` partiellement morte (la branche `CLOSED` ne serait jamais atteinte, l'édition ne serait plus résolvable du tout dès la clôture). Le rapport d'édition doit rester consultable par ID, indépendamment de ce filtre — voir Dev Notes § Écarts pour la limite plus large que ce choix contourne sans la résoudre.

- [x] **`ItemPricing`/`ItemRepository`/`SaleRepository` — agrégation sur toute l'édition, pas une journée (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java` (UPDATE — lire en entier) : nouvelle méthode
    ```java
    /**
     * Edition summary report (story 5.4, FR-055): every Sale of the edition's whole lifetime,
     * not bounded to a single day (contrast with findAllByEditionIdAndSoldAtBetween, story 5.3).
     */
    @Query("SELECT s FROM Sale s WHERE s.edition.id = :editionId")
    List<Sale> findAllByEditionId(@Param("editionId") Long editionId);
    ```
  - [x] Réutiliser tels quels `ItemRepository.findAllByEditionIdAndSoldTrue` (déjà utilisé par `SettlementService`, JOIN FETCH lot, tous les articles vendus de l'édition) et `ItemRepository.findAllUnsoldByEditionId` (déjà utilisé par `ReportService.getDailyReport`, Story 5.3) — **ne pas écrire de nouvelle requête d'articles**, les deux méthodes existantes couvrent déjà tout le périmètre de l'édition (pas de bornage date).

- [x] **`EditionSummaryReportDto` (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/EditionSummaryReportDto.java` (NEW, record) — même forme que `DailySalesReportDto` (Story 5.3) **sans** `reportDate` :
    ```java
    public record EditionSummaryReportDto(
            long soldItemCount,
            long unsoldItemCount,
            BigDecimal grossRevenue,
            BigDecimal commission,
            BigDecimal cashTotal,
            BigDecimal checkTotal,
            BigDecimal cardTotal) {
    }
    ```

- [x] **`ReportService.getEditionReport` (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` (UPDATE — lire en entier, ne pas dupliquer la logique de `getDailyReport`) : nouvelle méthode
    ```java
    @Transactional(readOnly = true)
    public EditionSummaryReportDto getEditionReport(Edition edition) {
        PhaseGuard.requirePostSaleOrClosedPhase(edition);

        List<Sale> allSales = saleRepository.findAllByEditionId(edition.getId());
        List<Item> soldItems = itemRepository.findAllByEditionIdAndSoldTrue(edition.getId());
        List<Item> unsoldItems = itemRepository.findAllUnsoldByEditionId(edition.getId());

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal check = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Sale sale : allSales) {
            switch (sale.getPaymentMethod()) {
                case CASH -> cash = cash.add(sale.getTotal());
                case CHECK -> check = check.add(sale.getTotal());
                case CARD -> card = card.add(sale.getTotal());
                default -> throw new IllegalStateException("Unhandled payment method: " + sale.getPaymentMethod());
            }
        }
        BigDecimal grossRevenue = cash.add(check).add(card).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = ItemPricing.computeCommission(grossRevenue, edition.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
        long soldItemCount = ItemPricing.distinctByLot(soldItems).size();
        long unsoldItemCount = ItemPricing.distinctByLot(unsoldItems).size();

        return new EditionSummaryReportDto(soldItemCount, unsoldItemCount, grossRevenue, commission,
                cash.setScale(2, RoundingMode.HALF_UP), check.setScale(2, RoundingMode.HALF_UP), card.setScale(2, RoundingMode.HALF_UP));
    }
    ```
    Même structure caractère pour caractère que `getDailyReport` (Story 5.3) hormis la fenêtre temporelle (édition entière, pas de `dayStart`/`dayEnd`) et la garde de phase (`requirePostSaleOrClosedPhase` au lieu de `requireSalePhase`) — **le `switch` conserve sa branche `default`** (review finding déjà résolu sur `getDailyReport`, ne pas régresser).

- [x] **`EditionReportRenderer` (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/EditionReportRenderer.java` (NEW) — même patron exact que `DailyReportRenderer` (Story 5.3, lire le fichier en entier avant d'écrire celui-ci) : polices `BaseFont.CP1252`/`NOT_EMBEDDED` (piège Euro déjà documenté), `writer.setCompressionLevel(PdfStream.NO_COMPRESSION)`, table de ventilation par moyen de paiement identique. **Différences avec `DailyReportRenderer`** : pas de ligne « Date », namespace de clés `print.editionReport.*` au lieu de `print.dailyReport.*`, méthode `renderEditionReport(String editionName, EditionSummaryReportDto report, Locale documentLocale)`.
    - **Note pour une future consommation par la Story 2.7** (FR-013, génération EN+FR à la clôture) : cette méthode prend déjà `documentLocale` en paramètre, comme toutes les autres méthodes `renderXxx` du module impression — 2.7 pourra l'appeler deux fois (`Locale.FRENCH` puis `Locale.ENGLISH`) sans aucune modification de ce renderer. Ne pas construire de mécanisme « dual langue » dans cette story, ce n'est pas son périmètre (voir Dev Notes § Écarts).

- [x] **`DocumentPrintService.buildEditionReportJob` (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE — lire en entier) : injecter `EditionReportRenderer`, ajouter
    ```java
    public PrintJob buildEditionReportJob(String editionName, EditionSummaryReportDto report, Locale documentLocale) {
        return printer -> printEditionReport(printer.getPrinterBridgeId(), editionName, report, documentLocale);
    }
    ```
    et la méthode privée `printEditionReport` correspondante, même patron exact que `printDailyReport`.

- [x] **`EditionSummaryReportPrintService` (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/EditionSummaryReportPrintService.java` (NEW) — même patron exact que `DailySalesReportPrintService` (Story 5.3) : résout l'édition via `editionService.requireEdition(editionId)` (pas `getActiveEdition()`), appelle `reportService.getEditionReport(edition)` (garde de phase déjà appliquée dedans), résout l'imprimante A4 sélectionnée en session, soumet le job à `PrintQueueService`.

- [x] **`AdminReportController` — nouveaux endpoints (AC 1)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java` (UPDATE — lire en entier) : injecter `EditionSummaryReportPrintService`, ajouter
    ```java
    @GetMapping("/edition/{editionId}")
    public ResponseEntity<EditionSummaryReportDto> getEditionReport(@PathVariable Long editionId) {
        return ResponseEntity.ok(reportService.getEditionReport(editionService.requireEdition(editionId)));
    }

    @PostMapping("/edition/{editionId}/print")
    public ResponseEntity<Void> printEditionReport(@PathVariable Long editionId, HttpSession session) {
        editionSummaryReportPrintService.printEditionReport(editionId, session);
        return ResponseEntity.noContent().build();
    }
    ```
    **Résolution par ID explicite, pas par « édition active implicite »** — seule route de ce contrôleur (et la seule de tout le projet en dehors de `EditionController.getEditionById`) à prendre l'ID en chemin plutôt que de s'appuyer sur `/editions/current`. C'est un choix délibéré (voir Dev Notes § Écarts) : le frontend transmet l'ID qu'il connaît déjà via `currentEditionService.currentEdition()!.id` tant que l'édition reste résolvable côté client — cela rend le endpoint backend correct et testable pour Post-vente **et** Clôturée dès aujourd'hui, même si l'atteignabilité complète de Clôturée depuis l'interface reste bloquée par ailleurs (§ Écarts).

- [x] **Backend — test E2E (AC 1)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java` (NEW — **package `domain.print`, PAS `domain.report`** : `DailyReportPrintingIT`, qu'on imite ici, vit réellement dans `domain.print` avec les 8 autres classes `*PrintingIT`/tests d'impression ; `domain.report` (tests) n'a aucun fichier aujourd'hui — ne pas créer un nouveau package de test pour une seule classe), `extends IntegrationTest`, storyboard `@TestMethodOrder`/`@Order` (patron `DailyReportPrintingIT`, Story 5.3 — lire ce fichier en entier avant d'écrire celui-ci, notamment sa mise en place de session imprimante et son helper d'assertion sur le contenu PDF non compressé). Scénarios suggérés :
    1. Setup : édition → Dépôt → 2 vendeurs, articles (un standard, un en lot de 2, un invendu) → Vente → vendre les articles via POS avec deux moyens de paiement différents (CASH et CARD) pour prouver l'agrégation multi-jours/multi-moyens → avancer l'édition de Vente vers Post-vente (`POST /admin/editions/{id}/phase/advance`, 3ᵉ appel à cette route depuis la création : PREPARATION→DEPOSIT→SALE→POST_SALE, patron exact de la mise en place de `DailyReportPrintingIT`).
    2. `GET /admin/reports/edition/{id}` en phase Vente → 422 `edition-report-not-allowed` (garde de phase).
    3. `GET /admin/reports/edition/{id}` en phase Post-vente → 200, compteurs/recettes/commission/ventilation exacts, vérifiés contre un calcul manuel `BigDecimal`.
    4. Faire avancer l'édition une 4ᵉ fois jusqu'à Clôturée (`POST /admin/editions/{id}/phase/advance` — même route, déjà exercée par `DailyReportPrintingIT`, atteignable dès aujourd'hui indépendamment de la Story 2.7) → `GET /admin/reports/edition/{id}` → 200, mêmes valeurs qu'à l'étape 3 (rien ne doit changer entre Post-vente et Clôturée, aucune vente n'est possible dans l'intervalle). **Ce scénario est la preuve que la résolution par ID (pas `getActiveEdition()`) fonctionne réellement en Clôturée** — point central de cette story, à ne pas sauter. C'est aussi le **seul** endroit où le chemin Clôturée de cette story est réellement vérifié : côté frontend, `isEditionReportPhase()` ne peut pas être exercé manuellement aujourd'hui (voir Tasks § `report-page.component.ts`).
    5. `POST /admin/reports/edition/{id}/print` → job en file, contenu PDF vérifié (comptages, montants, libellés i18n FR).
    6. Accès bénévole → 403 (même garde `@PreAuthorize("hasRole('ADMIN')")` déjà sur `AdminReportController`, FR-058).
    7. `GET /admin/reports/edition/{id}` avec un ID d'édition inexistant → 404 `edition-not-found` (`EditionNotFoundException`, déjà existante).
  - [x] Vérifier `DailyReportPrintingIT` (Story 5.3, done) — aucune régression attendue après l'ajout de `EditionReportRenderer` à `DocumentPrintService` (nouvel argument constructeur, 5→6). Re-exécuter explicitement et corriger l'instanciation manuelle de `DocumentPrintService` dans ces 4 classes (déjà identifiées, ne pas re-grepper) : `DailyReportPrintingIT.java:391`, `DepositSlipPrintingIT.java:228`, `InvoicePrintingIT.java:301`, `SettlementReportPrintingIT.java:388` (toutes dans `domain/print/`, même piège déjà rencontré et corrigé aux Stories 4.5/5.2/5.3).

- [x] **i18n backend (AC 1)**
  - [x] `pluribourse-backend/src/main/resources/messages_fr.properties` (UPDATE) — nouveau namespace `print.editionReport.*`, calqué sur `print.dailyReport.*` (Story 5.3, lignes 39-51) en retirant la clé `date` :
    ```properties
    print.editionReport.title=Bilan d'édition
    print.editionReport.soldCount=Articles vendus : {0}
    print.editionReport.unsoldCount=Articles invendus : {0}
    print.editionReport.grossRevenue=Recettes brutes totales : {0}€
    print.editionReport.commission=Commission totale perçue : {0}€
    print.editionReport.paymentBreakdown=Ventilation par moyen de paiement
    print.editionReport.column.method=Moyen de paiement
    print.editionReport.column.amount=Montant
    print.editionReport.amountFormat={0}€
    print.editionReport.method.cash=Espèces
    print.editionReport.method.check=Chèque
    print.editionReport.method.card=Carte
    ```
  - [x] `pluribourse-backend/src/main/resources/messages_en.properties` (UPDATE) — même structure, traduction anglaise (« Edition summary », « Items sold: {0} », etc.).

### Frontend

- [x] **Modèle & service (AC 1)**
  - [x] `pluribourse-frontend/src/app/models/edition-summary-report.model.ts` (NEW) — même forme que `daily-sales-report.model.ts` (Story 5.3) sans `reportDate` :
    ```typescript
    export interface EditionSummaryReportDto {
      soldItemCount: number;
      unsoldItemCount: number;
      grossRevenue: number;
      commission: number;
      cashTotal: number;
      checkTotal: number;
      cardTotal: number;
    }
    ```
  - [x] `pluribourse-frontend/src/app/services/report.service.ts` (UPDATE — lire en entier) : ajouter `getEditionReport(editionId: number): Observable<EditionSummaryReportDto>` (`GET /api/admin/reports/edition/{editionId}`) et `printEditionReport(editionId: number): Observable<void>` (`POST /api/admin/reports/edition/{editionId}/print`), même style que `getDailyReport`/`printDailyReport` existants.

- [x] **`ReportPageComponent` — nouvelle section (AC 1)**
  - [x] `pluribourse-frontend/src/app/features/report/report-page.component.ts` (UPDATE — **lire en entier avant modification**, notamment le commentaire sur l'`effect()` réactif et pourquoi ce n'est pas un `ngOnInit`) :
    - **Piège de typage — `ActivePhase` n'a PAS de membre `CLOSED`** (`active-phase.enum.ts` : `ActivePhase = PREPARATION | DEPOSIT | SALE | POST_SALE`, exclut délibérément `CLOSED`, cf. `ACTIVE_PHASES`/`CurrentEditionService`). `ActivePhase.CLOSED` ne compile pas. Comparer à la string literal `'CLOSED'` (type `PhaseType`, importé depuis `../../models/edition.model`) :
      `readonly isEditionReportPhase = computed(() => { const phase = this.currentEditionService.currentEdition()?.phase; return phase === ActivePhase.POST_SALE || phase === 'CLOSED'; });`
    - **Limite connue, à ne pas tenter de "corriger" dans cette story : la branche `'CLOSED'` ci-dessus est aujourd'hui du code mort côté frontend.** `CurrentEditionService.currentEdition()` repasse à `null` dès qu'une édition atteint `CLOSED`, dans les deux chemins : chargement initial (`loadEdition()` → `GET /editions/current` → `EditionService.getActiveEdition()` exclut `CLOSED` de `PhaseType.ACTIVE` → 404 → `set(null)`) et mise à jour SSE (`updateFromEvent()` : `if (!ACTIVE_PHASES.has(event.newPhase)) { ...set(null) }`, `ACTIVE_PHASES` dérivé du même enum à 4 valeurs). Donc `isEditionReportPhase()` ne peut aujourd'hui jamais être observé vrai dans l'app qui tourne — seul le endpoint backend (résolu par ID explicite, pas par édition active) est réellement exercé en Clôturée, via le test E2E ci-dessous. La branche `'CLOSED'` existe uniquement pour que le composant soit déjà correct le jour où la Story 2.7 résoudra cette limite plus large (§ Écarts) — ne pas la retirer, ne pas essayer de la rendre atteignable ici.
    - Un second signal `editionReport = signal<EditionSummaryReportDto | null>(null)` + `isLoadingEditionReport`/`editionReportError`/`printingEditionReport`, même style que les signaux existants du bilan journalier — **ne pas fusionner les deux rapports dans un seul jeu de signaux**, ce sont deux sections indépendantes avec des conditions de phase disjointes (SALE vs POST_SALE/CLOSED), exactement comme `SettlementListComponent` garde ses propres signaux malgré le voisinage avec `ReportPageComponent` dans la même page à terme (Story 5.5).
    - Étendre l'`effect()` existant (pas en ajouter un second — un seul point de réactivité sur `currentEditionService.currentEdition()`, cohérent avec le commentaire déjà en place) : charger le bilan journalier si `isSalePhase()`, charger le bilan d'édition si `isEditionReportPhase()`, remettre les deux à `null` sinon.
    - `loadEditionReport()`/`printEditionReport()` : même garde de réentrance (`if (this.isLoadingEditionReport()) return;` / `if (this.printingEditionReport()) return;`) et même gestion d'erreur 422 `invalid-printer-selection` que les méthodes existantes du bilan journalier — **appeler `this.reportService.getEditionReport(this.currentEditionService.currentEdition()!.id)`**, l'ID est garanti non-null dans les deux branches où `isEditionReportPhase()` est vrai (le signal `currentEdition()` porte déjà l'édition résolue à ce stade). En cas d'échec de `loadEditionReport()`, poser `admin.reports.error.loadEdition` (nouvelle clé, voir § i18n frontend ci-dessous) — **pas** `admin.reports.error.load`, dont le texte réel (« Impossible de charger le bilan journalier. ») est spécifique au bilan journalier et afficherait un message trompeur pour un échec du bilan d'édition.
  - [x] `pluribourse-frontend/src/app/features/report/report-page.component.html` (UPDATE — **fichier séparé, jamais de template inline**, CLAUDE.md) : restructurer l'unique `@if (!isSalePhase()) { empty-state } @else { ... }` actuel en deux blocs indépendants (`@if (isSalePhase())` pour le bilan journalier existant, `@if (isEditionReportPhase())` pour la nouvelle section bilan d'édition, même structure `stat-grid`/`data-table` que la section journalière), avec un état vide commun uniquement si **aucune** des deux conditions n'est vraie (PREPARATION/DEPOSIT) — respecte la règle « absent, pas grisée » déjà établie par cette page (Story 5.3 Dev Notes, anticipant l'AC 3 de la Story 5.5). Bouton « Imprimer » de la section bilan d'édition avec spinner/désactivation pendant `printingEditionReport()` (patron UX-DR19 déjà appliqué à la section journalière).

- [x] **i18n frontend (AC 1)**
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) — sous `admin.reports` (à côté de `daily`, ligne ~601), nouvelle clé `edition` :
    ```json
    "edition": {
      "title": "Bilan d'édition",
      "print": "Imprimer",
      "soldCount": "Articles vendus",
      "unsoldCount": "Articles invendus",
      "grossRevenue": "Recettes brutes totales",
      "commission": "Commission totale perçue",
      "paymentBreakdown": "Ventilation par moyen de paiement",
      "method": "Moyen de paiement",
      "amount": "Montant",
      "cash": "Espèces",
      "check": "Chèque",
      "card": "Carte",
      "amountFormat": "{{ amount }} €"
    },
    "error": {
      "loadEdition": "Impossible de charger le bilan d'édition."
    }
    ```
    Réutiliser `admin.reports.success.print`/`admin.reports.error.print`/`admin.reports.error.printerUnavailable`/`admin.reports.emptyPhase` existants (génériques, déjà partagés) plutôt que d'en dupliquer des variantes `edition.*` — vérifier leur libellé reste correct pour les deux sections avant réutilisation (« Bilan envoyé à l'imprimante. » convient aux deux). **Ne pas réutiliser `admin.reports.error.load`** (ligne 622 existante) pour l'échec de chargement du bilan d'édition : son texte réel est câblé sur « journalier » (« Impossible de charger le bilan journalier. ») — d'où la nouvelle clé `admin.reports.error.loadEdition` ci-dessus, utilisée par `loadEditionReport()` (§ Tasks `report-page.component.ts`).
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — même structure, traduction anglaise.

- [x] **Frontend — tests (AC 1)**
  - [x] `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts` (UPDATE — lire en entier) : nouveaux scénarios pour la section bilan d'édition (chargement/affichage en Post-vente, en Clôturée, absence en Vente/Préparation/Dépôt, impression avec garde de réentrance et gestion d'erreur imprimante), en suivant exactement les scénarios déjà écrits pour la section journalière.

### Vérification (AC 2 — aucun code)

- [x] Relire `SettlementListComponent`/`SettlementController`/`SettlementService` (Story 5.1, done) et confirmer que rien dans cette story ne les modifie — confirmé : `git status` en fin d'implémentation ne montre aucun de ces trois fichiers modifié. **Vérification visuelle par l'utilisateur encore en attente** (CLAUDE.md § Interaction utilisateur) — dev-story tourne sans supervision directe, donc n'a pas pu la solliciter en direct ; à faire avant de considérer cette story pleinement close par un humain (voir Dev Agent Record § Completion Notes).

### Review Findings

- [x] [Review][Patch] `printEditionReport()` utilise `currentEditionService.currentEdition()!.id` — si le signal repasse à `null` entre le rendu du bouton et l'exécution du clic (course avec un événement SSE `phase-changed`), l'assertion non-null lève une `TypeError`. Elle est interceptée par le `catch` générique existant (pas de crash), mais affiche à tort le toast « impossible d'imprimer » au lieu d'échouer proprement — le commentaire de la story affirmait à tort que l'ID était « garanti non-null ». [pluribourse-frontend/src/app/features/report/report-page.component.ts:135] — Corrigé : garde explicite (`const edition = currentEdition(); if (!edition) { return; }`) avant de positionner `printingEditionReport`, no-op silencieux au lieu de l'assertion non-null. Nouveau test de régression ajouté.
- [x] [Review][Defer] `AdminReportController.getEditionReport` résout l'édition (`requireEdition`) puis calcule le rapport (`getEditionReport`) dans deux transactions `readOnly` séparées, laissant une fenêtre TOCTOU étroite si la phase change entre les deux appels — patron identique déjà présent sur le endpoint GET du bilan journalier (Story 5.3 : `getActiveEdition()` puis `getDailyReport()`), non introduit par ce diff. [pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java:42-43] — deferred, pre-existing.
- [x] [Review][Defer] Les sous-classes `*NotAllowedException` continuent de proliférer (6 désormais : `DepositReprintNotAllowedException`, `ItemModificationNotAllowedException`, `SettlementNotAllowedException`, `EditionReportNotAllowedException`, `SellerDeletionNotAllowedException`, `SellerManagementNotAllowedException`) plutôt qu'une exception paramétrée unique — convention déjà établie dans tout le code base, cette story s'y conforme exactement, ne l'introduit pas. [pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/EditionReportNotAllowedException.java] — deferred, pre-existing.
- [x] [Review][Defer] `EditionReportRenderer` duplique quasi intégralement le boilerplate de `DailyReportRenderer` (polices, `NO_COMPRESSION`, construction de table) au lieu de partager une base commune — patron déjà présent sur les 5 renderers PDF du projet, explicitement demandé par les Tasks de cette story (« même patron exact »), non introduit ici. [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/EditionReportRenderer.java] — deferred, pre-existing.
- [x] [Review][Defer] `EditionSummaryReportPrintService` duplique le boilerplate résolution imprimante/vérification disponibilité/soumission job de `DailySalesReportPrintService` — même patron déjà présent sur tous les services d'impression de rapports/bilans, non introduit ici. [pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/EditionSummaryReportPrintService.java] — deferred, pre-existing.
- [x] [Review][Defer] La branche `default -> throw new IllegalStateException(...)` du `switch` sur `PaymentMethod` dans `getEditionReport` (ajoutée pour éviter qu'un futur moyen de paiement disparaisse silencieusement) n'a aucun test qui l'exerce — lacune identique déjà acceptée sur la même branche de `getDailyReport` (Story 5.3). [pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java:89] — deferred, pre-existing.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- **`ReportService.getDailyReport`/`DailyReportRenderer`/`DailySalesReportPrintService`/`AdminReportController`** (`domain/report/**`, `domain/print/service/DailyReportRenderer.java`, Story 5.3) — patron direct à reproduire caractère pour caractère pour le bilan d'édition, seules la fenêtre temporelle et la garde de phase changent.
- **`ItemPricing.computeCommission`/`distinctByLot`** (`domain/item/service/ItemPricing.java`) — déjà extrait et documenté (Story 5.3 review), réutilisé tel quel.
- **`ItemRepository.findAllByEditionIdAndSoldTrue`** (Story 5.1) et **`findAllUnsoldByEditionId`** (Story 5.3) — couvrent déjà tout le périmètre « édition entière », aucune nouvelle requête d'articles à écrire.
- **`PhaseGuard`** (`domain/item/service/PhaseGuard.java`) — classe utilitaire cross-domaine déjà étendue à 4 reprises (item, pos, payout, report) ; cette story y ajoute une 5e garde, ne pas créer de garde ad hoc ailleurs.
- **`ReportPageComponent`** (`features/report/`, Story 5.3) — squelette de page déjà créé avec route `/admin/reports`, lien sidebar, `effect()` réactif sur la phase. Cette story l'étend, ne recrée rien.
- **`EditionService.getEditionById`** — la résolution par ID existe déjà en interne (méthode privée `findById`), cette story se contente de l'exposer publiquement (`requireEdition`) plutôt que d'écrire une nouvelle requête.

### Écarts par rapport à epics.md — actés dans cette story, à confirmer avec l'utilisateur

**1. Périmètre réduit à AC 1 — AC 3/AC 4 hors de portée technique actuelle.**

Le code existant a une limite architecturale déjà identifiée dans les Dev Notes de la Story 5.1 (§ « Limite connue, non bloquante ») : `EditionService.getActiveEdition()` repose sur `repository.findFirstByPhaseIn(PhaseType.ACTIVE)`, et `PhaseType.ACTIVE = {PREPARATION, DEPOSIT, SALE, POST_SALE}` **exclut `CLOSED`**. Or `/editions/current` (`CurrentEditionController`, consommé par `CurrentEditionService.loadEdition()` au niveau de `AppLayoutComponent`, donc de **toute la coquille applicative admin**) appelle `getActiveEditionDto()` → `getActiveEdition()`. Résultat vérifié dans le code : dès qu'une édition passe en Clôturée (transition déjà possible aujourd'hui via `POST /admin/editions/{id}/phase/advance`, Story 2.2 — `computeNextPhase` autorise `POST_SALE → CLOSED` indépendamment de toute logique de Story 2.7), **toute** la navigation admin qui dépend de l'édition courante casse (`/admin/sellers`, `/admin/catalog`, `/admin/settlement`, et sans le contournement de cette story, `/admin/reports` aussi) — pas une régression de cette story, une limite déjà présente et déjà déférée (Story 5.1 → « à traiter par la Story 2.7 ou une story dédiée »). **Ne pas confondre avec la dépendance de la Story 6-2 sur 2.7** (cf. sprint-status.yaml) : 6-2 dépend de la table d'archive que l'action Archiver alimente (FR-102, consultation d'un catalogue archivé) — une dépendance de disponibilité de données, distincte de ce bug de navigation `getActiveEdition()`/`PhaseType.ACTIVE`. Les deux stories dépendent de 2.7, mais pour deux raisons de fond différentes.

FR-059 (texte normatif du PRD, non reformulé) : « Les éditions clôturées affichent les métriques agrégées en lecture seule. Les profils vendeurs et le détail des articles restent consultables jusqu'au déclenchement de l'action Archiver l'Édition ; après archivage, seules les métriques agrégées sont accessibles en base. »

AC 4 est en plus **littéralement non testable aujourd'hui** : elle suppose que l'action « Archiver l'édition » (FR-088) a été déclenchée, mais cette action — et la table d'archive qu'elle alimente — n'existe pas encore ; c'est le périmètre exclusif et non ambigu de la Story 2.7 (backlog).

**Décision prise pour cette story** (à confirmer avec l'utilisateur avant `dev-story`, comme pour les écarts des Stories 5.1/5.2/5.3) : livrer uniquement AC 1 (capacité de calcul + rendu PDF du bilan d'édition, réutilisable telle quelle par la Story 2.7 pour FR-013) et AC 2 (vérification, déjà livré). **Recommandation** : déplacer AC 3/AC 4 vers la Story 2.7 elle-même (qui devra de toute façon résoudre l'accessibilité post-clôture pour livrer ses propres AC de confirmation/archivage) plutôt que vers une story dédiée séparée — 2.7 est le seul endroit du planning où « l'édition vient de passer Clôturée » et « l'Archivage vient d'être déclenché » sont des états réellement atteignables et testables.

**2. FR-013 (génération EN+FR automatique à la clôture) n'est pas déclenchée par cette story.**

epics.md (Story 2.7, ligne 962) et architecture.md (FR-013) placent la génération automatique des PDF EN+FR **au moment du clic sur « Clôturer l'Édition »**, une action qui appartient explicitement à la Story 2.7 (pas encore écrite). Cette story construit la capacité de calcul/rendu (`ReportService.getEditionReport`, `EditionReportRenderer`) que la Story 2.7 invoquera ensuite deux fois (une fois par langue) dans sa propre transaction de clôture — exactement le même rapport entre les Stories 5.1 et 2.7 pour `SettlementService`/FR-096 (auto-marquage Non réclamé). Cette story expose en plus un accès direct (bouton « Imprimer » sur `/admin/reports`) pour consultation/réimpression à la demande, dans la langue des documents de l'édition (patron Story 5.3), pas les deux langues simultanément — la génération EN+FR systématique reste le périmètre de 2.7.

**3. Endpoint résolu par ID d'édition, pas par « édition active ».**

Seule déviation du patron REST établi par `AdminReportController` (Story 5.3, qui utilise `editionService.getActiveEdition()` implicitement). Nécessaire pour que le endpoint reste correct en Clôturée (voir Tasks § `AdminReportController`) — contournement local et volontairement minimal de la limite décrite au point 1, pas une résolution de cette limite au niveau de l'application entière (qui resterait un changement bien plus large, touchant `SellerService`, `ItemCatalogService`, `SettlementService`, `CurrentEditionController`, hors périmètre de cette story).

### Project Structure Notes

- Aucun nouveau package backend — extension de `domain.report.*` (Story 5.3) et `domain.print.service.*`.
- Aucun nouveau dossier frontend — extension de `features/report/` (Story 5.3).
- Fichiers UPDATE à lire intégralement avant modification : `PhaseGuard.java`, `EditionService.java`, `SaleRepository.java`, `ReportService.java`, `DocumentPrintService.java`, `AdminReportController.java`, `report.service.ts`, `report-page.component.ts`, `report-page.component.html`, `messages_fr.properties`/`messages_en.properties`, `fr.json`/`en.json`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.4] — AC source (FR-055, FR-094, FR-095, FR-059)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.7] — dépendance croisée explicite sur « une story de génération du bilan d'édition PDF EN/FR (FR-013) », c'est cette story
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.5] — AC 2 (section synthèse « Post-vente ou Clôturée »), précédent direct pour la condition de phase choisie ici
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] — FR-013, FR-054/055/057/058/059/094 (texte normatif complet)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — tableau de routes admin (ligne 46 : `/admin/reports` accessible Vente/Post-vente/Clôturée), pattern « Metric tile » (ligne 157, vue détail édition archivée — hors périmètre ici), état vide catalogue archivé (ligne 165, précédent pour le traitement de FR-059/FR-088 partout ailleurs dans le projet)
- [Source: _bmad-output/implementation-artifacts/5-3-rapport-de-ventes-journalier-admin.md] — patron direct (`ReportService`, `DailyReportRenderer`, `DailySalesReportPrintService`, `AdminReportController`, `ReportPageComponent`) — story sœur immédiate
- [Source: _bmad-output/implementation-artifacts/5-1-flux-de-solde-des-vendeurs.md] — AC 2 déjà livré intégralement (`SettlementListComponent`) ; § « Limite connue, non bloquante » = origine documentée de l'écart § 1 ci-dessus
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/**, report/**, print/**, item/service/PhaseGuard.java] — lus intégralement pour les patrons réutilisés et la vérification de la limite `getActiveEdition()`/`PhaseType.ACTIVE`
- [Source: pluribourse-frontend/src/app/features/report/**, services/current-edition.service.ts] — lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (create-story, validate, dev-story)

### Debug Log References

- Backend : `./mvnw -o compile` puis `./mvnw -o test-compile` propres avant tout run de test ; `./mvnw -o test` complet — 440/440 tests backend verts (422 avant cette story + 18 nouveaux `EditionReportPrintingIT`), 0 régression.
- Frontend : `npx tsc --noEmit -p tsconfig.app.json` et `-p tsconfig.spec.json` propres ; `npm test` complet — 585/585 tests frontend verts (575 avant cette story + 10 nouveaux scénarios bilan d'édition), 0 régression.

### Completion Notes List

- Analyse exhaustive du code existant (pas seulement d'epics.md) avant rédaction : `SettlementListComponent` (Story 5.1) livre déjà l'intégralité de l'AC 2 d'epics.md — confirmé en lisant `settlement-list.component.ts`/`.html` ligne par ligne (colonnes `isAdmin()`, filtre `statusFilter`). Aucune nouvelle tâche créée pour un travail déjà fait.
- Limite architecturale vérifiée directement dans le code (pas supposée) : `EditionService.getActiveEdition()` → `PhaseType.ACTIVE` (n'inclut pas `CLOSED`) → `/editions/current` → `CurrentEditionService.loadEdition()` → `AppLayoutComponent` — chaîne complète tracée pour confirmer que la coquille admin entière (pas seulement les rapports) casse dès qu'une édition passe Clôturée. Confirmé cohérent avec la note déjà laissée dans la Story 5.1 et avec la dépendance déjà documentée de la Story 6-2 sur la Story 2.7 pour la même cause racine.
- Confirmé que `CLOSED` est déjà atteignable aujourd'hui via `POST /admin/editions/{id}/phase/advance` (Story 2.2, `computeNextPhase` n'a aucune dépendance sur la Story 2.7) — permet d'écrire un test E2E backend réel du scénario Clôturée pour cette story (`EditionReportPrintingIT`), même si l'atteignabilité *via l'interface* reste bloquée par la limite ci-dessus.
- **dev-story** : implémentation complète full-stack, en suivant caractère pour caractère les patrons de la Story 5.3 déjà identifiés en Dev Notes. Backend : `PhaseGuard.requirePostSaleOrClosedPhase`, `EditionReportNotAllowedException`, `EditionService.requireEdition`, `SaleRepository.findAllByEditionId`, `EditionSummaryReportDto`, `ReportService.getEditionReport`, `EditionReportRenderer` (patron `DailyReportRenderer` sans la ligne Date), `DocumentPrintService.buildEditionReportJob` (constructeur 5→6 arguments), `EditionSummaryReportPrintService`, deux nouveaux endpoints sur `AdminReportController`. Nouveau test `EditionReportPrintingIT` (18 scénarios @Order, package `domain.print` comme spécifié) — scénario dédié prouvant que la résolution par ID reste correcte en Clôturée (avance l'édition une 4ᵉ fois via la route déjà existante depuis la Story 2.2, aucun changement de valeurs entre Post-vente et Clôturée), et scénario Livre/CHECK backdaté à hier prouvant que le bilan d'édition, contrairement au bilan journalier, n'exclut PAS les ventes anciennes. Les 4 classes de test instanciant `DocumentPrintService` manuellement (déjà listées dans la story) corrigées pour la nouvelle arité. Frontend : `isEditionReportPhase` avec comparaison à la string literal `'CLOSED'` (pas `ActivePhase.CLOSED`, qui n'existe pas), `effect()` existant étendu (pas de second effect), signaux dédiés `editionReport`/`isLoadingEditionReport`/`editionReportError`/`printingEditionReport`, template restructuré en deux blocs `@if` indépendants + état vide commun, nouvelle clé i18n `admin.reports.error.loadEdition` (pas de réutilisation de la clé `error.load` câblée sur « journalier »). Aucun écart non anticipé par rapport au plan de la story — tous les pièges déjà identifiés en Dev Notes (typage `ActivePhase`, emplacement du test, route d'avancement de phase, clé i18n) ont été évités tels que documentés.
- **Vérification AC 2** : `git status` en fin d'implémentation confirme qu'aucun fichier `SettlementListComponent`/`SettlementController`/`SettlementService` n'a été touché par cette story. **Reste en attente** : la vérification visuelle par un humain de `/admin/settlement` (filtre « Non soldés », colonnes téléphone/email) demandée par CLAUDE.md § Interaction utilisateur — `dev-story` a tourné sans supervision interactive et n'a pas pu la solliciter ; à faire avant de considérer cette story pleinement close.

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/EditionReportNotAllowedException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/EditionSummaryReportDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/EditionReportRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/EditionSummaryReportPrintService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java` (package `print`, pas `report` — voir Tasks § test E2E)

**Backend — UPDATE**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java` — `requirePostSaleOrClosedPhase`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` — `requireEdition`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/SaleRepository.java` — `findAllByEditionId`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` — `getEditionReport`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` — `buildEditionReportJob`, injection `EditionReportRenderer`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/controller/AdminReportController.java` — `GET/POST /admin/reports/edition/{editionId}[/print]`
- `pluribourse-backend/src/main/resources/messages_fr.properties` — namespace `print.editionReport.*`
- `pluribourse-backend/src/main/resources/messages_en.properties` — namespace `print.editionReport.*`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java` — arité `DocumentPrintService` (5→6), champ `editionReportRenderer`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — idem
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` — idem
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` — idem

**Frontend — NEW**
- `pluribourse-frontend/src/app/models/edition-summary-report.model.ts`

**Frontend — UPDATE**
- `pluribourse-frontend/src/app/services/report.service.ts` — `getEditionReport`, `printEditionReport`
- `pluribourse-frontend/src/app/features/report/report-page.component.ts` — section bilan d'édition
- `pluribourse-frontend/src/app/features/report/report-page.component.html` — section bilan d'édition
- `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts` — nouveaux scénarios
- `pluribourse-frontend/public/i18n/fr.json` — `admin.reports.edition.*`
- `pluribourse-frontend/public/i18n/en.json` — `admin.reports.edition.*`

## Change Log

- 2026-08-18 — create-story : story créée. Périmètre réduit à AC 1 (bilan d'édition, seul travail neuf) après vérification que l'AC 2 d'epics.md est déjà entièrement livré par la Story 5.1 (`SettlementListComponent`). AC 3/AC 4 identifiés comme hors périmètre technique actuel — bloqués par une limite architecturale déjà documentée dans la Story 5.1 (`EditionService.getActiveEdition()` exclut `CLOSED`, casse toute la navigation admin post-clôture) et, pour AC 4, par l'action d'Archivage (FR-088) qui n'existe pas encore (Story 2.7, backlog). Recommandation actée : déplacer AC 3/AC 4 vers la Story 2.7. Endpoint de rapport d'édition conçu par résolution d'ID explicite (`EditionService.requireEdition`) plutôt que « édition active implicite », pour rester correct et testable en Clôturée sans résoudre la limite plus large. Statut → ready-for-dev.
- 2026-08-18 — validate (bmad-create-story validate, contexte neuf) : 5 lacunes critiques trouvées et corrigées avant dev — `ActivePhase.CLOSED` inexistant dans l'enum frontend (erreur de compilation, corrigé en comparaison à la string literal `'CLOSED'`) ; branche Clôturée du signal `isEditionReportPhase` clarifiée comme code mort frontend aujourd'hui (`CurrentEditionService` remet `currentEdition()` à `null` dès `CLOSED`, dans les deux chemins de mise à jour) ; mauvais package pour le nouveau test E2E (`domain.report` → `domain.print`, aligné sur `DailyReportPrintingIT` et les 8 autres classes `*PrintingIT`) ; mauvaise route citée pour l'avancement de phase (`PUT /editions/{id}/advance` → `POST /admin/editions/{id}/phase/advance`) ; clé i18n `admin.reports.error.load` (texte câblé sur « journalier ») remplacée par une nouvelle clé `admin.reports.error.loadEdition` pour l'échec de chargement du bilan d'édition. 4 améliorations appliquées : liste explicite des 4 fichiers de test à corriger pour l'arité `DocumentPrintService` (au lieu d'un grep différé) ; parallèle avec la dépendance de la Story 6-2 sur 2.7 corrigé (raison de fond différente — table d'archive FR-102, pas le bug `getActiveEdition()`) ; texte normatif de FR-059 cité directement ; nuance ajoutée sur la citation EXPERIENCE.md (même table annonce `/admin/settlement` accessible en Clôturée, déjà faux en pratique aujourd'hui pour la même cause racine). 1 optimisation appliquée (reformulation du scénario de setup du test E2E). Statut → ready-for-dev (confirmé).
- 2026-08-18 — dev-story : implémentation complète full-stack, patron Story 5.3 reproduit caractère pour caractère. Backend : `PhaseGuard.requirePostSaleOrClosedPhase`, `EditionReportNotAllowedException`, `EditionService.requireEdition`, `SaleRepository.findAllByEditionId`, `EditionSummaryReportDto`, `ReportService.getEditionReport`, `EditionReportRenderer`, `DocumentPrintService.buildEditionReportJob`, `EditionSummaryReportPrintService`, endpoints `GET/POST /admin/reports/edition/{editionId}[/print]`. Nouveau test `EditionReportPrintingIT` (18 scénarios, package `domain.print`) — preuve E2E que la résolution par ID reste correcte en Clôturée, et que le bilan d'édition (contrairement au journalier) n'exclut pas les ventes anciennes. 4 classes de test corrigées pour la nouvelle arité de `DocumentPrintService`. Frontend : section bilan d'édition sur `ReportPageComponent`/`.html` (signaux dédiés, `effect()` existant étendu, comparaison `'CLOSED'` en string literal), nouvelle clé i18n `admin.reports.error.loadEdition`, 10 nouveaux scénarios `report-page.component.spec.ts`. AC 2 vérifié par lecture de code (aucun fichier Settlement touché) — vérification visuelle utilisateur de `/admin/settlement` encore en attente (dev-story non supervisé). 440/440 tests backend (422 + 18 nouveaux), 585/585 tests frontend (575 + 10 nouveaux), aucune régression. Statut → review.
- 2026-08-18 — code-review (bmad-code-review, 3 couches parallèles : Blind Hunter, Edge Case Hunter, Acceptance Auditor) : Acceptance Auditor confirme 0 violation d'AC et 0 dérive entre les Tasks prescrites et le code réel. 0 decision-needed, 1 patch appliqué, 5 defer documentés dans `deferred-work.md`, 12 rejetés comme bruit après vérification directe dans le code (pas de simple supposition) — notamment : risque d'arrondi écarté (`Sale.total` est `scale=2` au niveau entité/BDD, aucune divergence possible) ; « chevauchement de domaine » du package des exceptions et « prolifération » des sous-classes `*NotAllowedException` écartés (convention déjà établie sur 5-6 classes existantes, dont `SettlementNotAllowedException`) ; `@PreAuthorize("ADMIN")` déjà présent au niveau classe sur `AdminReportController`, couvre les deux nouveaux endpoints sans modification visible dans le diff ; ordre calcul-avant-vérification-imprimante et branche `default` non testée du switch tous deux identiques au patron déjà accepté de la Story 5.3 ; branche frontend `'CLOSED'` confirmée être une décision délibérée déjà actée en validate, pas un oubli. Patch appliqué : `printEditionReport()` remplace l'assertion non-null `currentEdition()!.id` par une garde explicite (no-op silencieux si l'édition redevient `null` entre le rendu du bouton et le clic — course SSE étroite mais réelle), avec un nouveau test de régression. 586/586 tests frontend re-validés après patch (585 + 1 nouveau), aucune régression. Statut → done.
