---
baseline_commit: 805ed00fc84c415232fb8b656d54f667c05e27e0
---

# Story 5.6: Impression groupée des bilans de vente (Admin)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant qu'administrateur,
je veux imprimer en un seul clic les bilans de vente de tous les vendeurs correspondant au filtre actif,
afin d'éviter de déclencher les impressions une par une avant de commencer les règlements.

## Acceptance Criteria

1. **Étant donné** que l'édition est en phase Post-vente et que l'admin consulte `/admin/settlement`,
   **quand** la page se charge, **alors** un bouton "Imprimer tous les bilans" est visible en haut de
   la liste (FR-097).
2. **Étant donné** que le filtre actif est "Tous", **quand** l'admin clique sur "Imprimer tous les
   bilans", **alors** un travail d'impression A4 est enfilé pour chaque vendeur de l'édition active
   **et** le contenu de chaque bilan respecte le format FR-050.
3. **Étant donné** que le filtre actif est "Non soldés", **quand** l'admin clique sur "Imprimer tous
   les bilans", **alors** seuls les bilans des vendeurs non soldés sont enfilés (FR-097).
4. **Étant donné** que l'admin clique sur le bouton, **quand** la soumission est en cours, **alors** le
   bouton passe en état désactivé avec un spinner inline (UX-DR19) **et**, à la fin, un toast succès
   (4 s) indique le nombre de bilans mis en file (ex. : "20 bilans mis en file d'impression.").
5. **Étant donné** qu'un ou plusieurs enfilages échouent, **quand** la soumission se termine, **alors**
   un toast d'erreur persistant indique le nombre d'échecs et contient un lien vers
   `/admin/print-queue` **et** les travaux déjà enfilés avec succès ne sont pas annulés.
6. **Étant donné** que l'édition est en phase Clôturée, **quand** l'admin consulte
   `/admin/settlement`, **alors** le bouton est absent. *(Déjà garanti structurellement — voir Dev
   Notes § Clôturée : aucune action requise.)*
7. **Étant donné** qu'un bénévole consulte `/volunteer/settlement`, **quand** la page se charge,
   **alors** le bouton est absent (FR-097).

## Tasks / Subtasks

- [x] **Task 1 — Backend : filtre de sélection des vendeurs + impression groupée (AC 2, AC 3)**
  - [x] Nouvel enum `SettlementFilter`
        (`pluribourse-backend/.../domain/payout/dto/SettlementFilter.java`), une constante par valeur
        de filtre déjà utilisée côté frontend (`StatusFilter`), avec un prédicat porté par la
        constante elle-même (pas de `switch` externe à dupliquer) :
        ```java
        public enum SettlementFilter {
            ALL {
                @Override public boolean matches(SettlementStatus status) { return true; }
            },
            UNSETTLED {
                @Override public boolean matches(SettlementStatus status) { return status == SettlementStatus.UNSETTLED; }
            },
            SETTLED {
                @Override public boolean matches(SettlementStatus status) { return status != SettlementStatus.UNSETTLED; }
            };

            public abstract boolean matches(SettlementStatus status);
        }
        ```
        `SETTLED` regroupe `SettlementStatus.SETTLED` **et** `UNCLAIMED` — c'est exactement la même
        règle que le filtre "Soldés" déjà en place côté frontend
        (`filter === 'settled' ? s.status !== 'UNSETTLED' : ...` dans `settlement-list.component.ts`).
        Ne pas réinventer une troisième variante de ce regroupement.
  - [x] Dans `SettlementService`, nouvelle méthode publique `getSellersMatchingFilter(Edition edition,
        SettlementFilter filter)` retournant `List<SellerProfile>` — même patron **batché** que
        `getSettlementsForEdition` (une requête vendeurs + une requête soldes groupée par vendeur, pas
        de scan par vendeur) mais exposant les entités `SellerProfile` elles-mêmes (nécessaires pour
        construire les `PrintJob`), pas des DTO :
        ```java
        @Transactional(readOnly = true)
        public List<SellerProfile> getSellersMatchingFilter(Edition edition, SettlementFilter filter) {
            List<SellerProfile> sellers = sellerRepository.findAllByEditionId(edition.getId());
            Map<Long, SettlementStatus> statusBySellerId = settlementRepository.findAllBySellerProfileEditionId(edition.getId()).stream()
                    .collect(Collectors.toMap(s -> s.getSellerProfile().getId(), Settlement::getStatus));
            return sellers.stream()
                    .filter(seller -> filter.matches(statusBySellerId.getOrDefault(seller.getId(), SettlementStatus.UNSETTLED)))
                    .sorted(Comparator.comparing(SellerProfile::getSellerNumber))
                    .toList();
        }
        ```
        **N'applique aucune garde de phase elle-même** — même convention que `getSettlementsForEdition`
        (Javadoc identique : "callers are responsible for their own"), l'appelant (Task ci-dessous)
        applique déjà `PhaseGuard.requirePostSalePhase`.
        **Le tri par `sellerNumber` est nécessaire** : `sellerRepository.findAllByEditionId` (méthode
        dérivée Spring Data) ne garantit aucun ordre. Sans ce tri, la pile physique de bilans A4 sortis
        de l'imprimante (jusqu'à ~100, NFR-001) serait dans un ordre arbitraire — un vrai irritant pour
        l'admin qui doit ensuite manipuler cette pile. `sellerNumber` est la clé de tri déjà utilisée
        pour identifier physiquement un vendeur ailleurs dans ce module.
  - [x] Nouvelle méthode de requête sur `ItemRepository`
        (`pluribourse-backend/.../domain/item/repository/ItemRepository.java`), pour charger en **une
        seule requête** les articles de tous les vendeurs de l'édition (évite exactement le scan N+1
        par vendeur déjà corrigé/documenté à plusieurs reprises dans ce module — Story 5.1 review,
        Story 5.5 Dev Notes, pertinent vu NFR-001 ~100 vendeurs) :
        ```java
        /**
         * Bulk settlement report printing (story 5.6, FR-097): every item (sold and unsold) across
         * the whole edition, grouped by seller in memory afterwards — same batched pattern as
         * {@link #findAllByEditionIdAndSoldTrue}, avoiding a per-seller N+1 query in the print loop
         * (NFR-001, ~100 sellers). JOIN FETCH category + lot, same as
         * {@link #findAllBySellerProfileIdForSettlementReport} (the per-seller equivalent used by the
         * single-report endpoint) — sellerProfile is read only via its already-cached id to key the
         * grouping, never triggering a lazy load.
         */
        @Query("SELECT i FROM Item i JOIN FETCH i.category LEFT JOIN FETCH i.lot WHERE i.edition.id = :editionId ORDER BY i.sellerProfile.id ASC, i.itemNumber ASC")
        List<Item> findAllByEditionIdForSettlementReport(@Param("editionId") Long editionId);
        ```
        L'`ORDER BY` double clé est nécessaire : sans lui, une fois les items regroupés par vendeur en
        mémoire (`Collectors.groupingBy`), l'ordre à l'intérieur de chaque groupe ne serait pas garanti
        `itemNumber ASC` comme l'attend `SettlementReportRenderer` (même ordre que la requête
        per-seller existante).
  - [x] Nouveau DTO `BulkSettlementReportPrintResultDto`
        (`.../domain/payout/dto/BulkSettlementReportPrintResultDto.java`) : `record
        BulkSettlementReportPrintResultDto(int succeededCount, int failedCount)`.
  - [x] Dans `SettlementReportPrintService` (**pas** un nouveau service — même service que
        `printReport`, même préoccupation "impression du bilan de vente", toutes les dépendances
        nécessaires sont déjà injectées, aucun changement de constructeur ; ajouter `@Slf4j` sur la
        classe si absent), nouvelle méthode `printAllReports(SettlementFilter filter, HttpSession
        session)` :
        ```java
        @Transactional(readOnly = true)
        public BulkSettlementReportPrintResultDto printAllReports(SettlementFilter filter, HttpSession session) {
            Edition edition = editionService.getActiveEdition();
            PhaseGuard.requirePostSalePhase(edition);

            Long a4PrinterId = printerSelectionService.getSelectedPrinterId(session, PrinterType.A4)
                    .orElseThrow(() -> new InvalidPrinterSelectionException("No A4 printer selected in session"));
            if (!printQueueService.isAvailable(a4PrinterId)) {
                throw new InvalidPrinterSelectionException("Selected A4 printer is not currently available");
            }

            BigDecimal commissionRate = edition.getCommissionRate();
            Locale documentLocale = edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;

            List<SellerProfile> sellers = settlementService.getSellersMatchingFilter(edition, filter);
            Map<Long, List<Item>> itemsBySellerId = itemRepository.findAllByEditionIdForSettlementReport(edition.getId()).stream()
                    .collect(Collectors.groupingBy(i -> i.getSellerProfile().getId()));

            int succeeded = 0;
            int failed = 0;
            for (SellerProfile seller : sellers) {
                try {
                    List<Item> items = itemsBySellerId.getOrDefault(seller.getId(), List.of());
                    printQueueService.submit(a4PrinterId, documentPrintService.buildSettlementReportJob(seller, items, commissionRate, documentLocale));
                    succeeded++;
                } catch (RuntimeException e) {
                    log.warn("Failed to queue settlement report for seller {}: {}", seller.getId(), e.getMessage());
                    failed++;
                }
            }
            return new BulkSettlementReportPrintResultDto(succeeded, failed);
        }
        ```
        **Pourquoi le `try/catch` par vendeur, même s'il n'est quasiment jamais exercé en pratique :**
        `PrintQueueService.submit()` ne fait qu'ajouter le job à une deque en mémoire
        (`PrinterQueueHandle.submit`) — il n'échoue jamais une fois `isAvailable()` vérifié, sauf la
        course étroite où l'imprimante est désenregistrée entre la vérification et cette itération
        (`PrinterNotFoundException`). C'est délibérément une défense en profondeur, pas un chemin
        couramment exercé — voir Dev Notes § Limite de test connue. Le `try/catch` par itération est
        ce qui garantit littéralement l'AC 5 ("les travaux déjà enfilés avec succès ne sont pas
        annulés") : ne jamais laisser une exception sur un vendeur interrompre la boucle pour les
        suivants. Le `log.warn` dans le `catch` trace l'id numérique du vendeur (pas de nom, email ou
        téléphone — conforme CLAUDE.md) : sans cette trace, ni l'admin ni un dev ne pourraient
        déterminer a posteriori quel vendeur a échoué, seul `failedCount` étant exposé côté client.
  - [x] Nouveau contrôleur `AdminSettlementController`
        (`.../domain/payout/controller/AdminSettlementController.java`), même patron que
        `AdminSellerController` (contrôleur admin-only séparé, sous `/admin/...`, `@PreAuthorize` de
        **classe**). Note : `@PreAuthorize` de méthode existe bien ailleurs dans ce codebase
        (`AccountController.updateLanguagePreference`, `AuthController` `/me` et `/change-password`),
        mais toujours pour restreindre à `hasAnyRole('ADMIN', 'VOLUNTEER')` un endpoint d'un contrôleur
        déjà partagé — jamais pour rendre une seule méthode admin-only au milieu d'un contrôleur par
        ailleurs non gardé. Pour cette forme précise (sous-ensemble admin-only d'un domaine par
        ailleurs partagé), le patron déjà établi reste le contrôleur frère dédié
        (`SellerController`/`AdminSellerController`), pas une annotation de méthode isolée :
        ```java
        @RestController
        @RequestMapping("/admin/settlements")
        @PreAuthorize("hasRole('ADMIN')")
        @RequiredArgsConstructor
        public class AdminSettlementController {

            private final SettlementReportPrintService reportPrintService;

            @PostMapping("/report/print-all")
            public ResponseEntity<BulkSettlementReportPrintResultDto> printAllReports(
                    @RequestParam SettlementFilter filter, HttpSession session) {
                return ResponseEntity.ok(reportPrintService.printAllReports(filter, session));
            }
        }
        ```
        Le `SettlementController` existant (`/settlements`, partagé ADMIN+VOLUNTEER, story 5.1) **n'est
        pas touché** — cette action est admin-only par nature (AC 7), donc un contrôleur frère dédié
        sous `/admin/settlements`, pas une addition au contrôleur partagé.
        Résolution par `editionService.getActiveEdition()` (pas de résolution par ID explicite,
        contrairement aux endpoints d'export de la Story 5.5) : cette action n'est **jamais**
        disponible en Clôturée (AC 6), donc la limite connue de `getActiveEdition()` qui exclut CLOSED
        n'est pas un problème ici — au contraire, elle donne l'AC 6 gratuitement (voir Dev Notes).

- [x] **Task 2 — Frontend : lien cliquable dans un toast d'erreur (nouvelle capacité, AC 5)**
  - [x] Le système de toast actuel (`shared/components/toast/toast.service.ts`) ne supporte
        **aucun** lien — `Toast` n'a que `message`/`type`. C'est une lacune réelle à combler, pas un
        détail : l'AC 5 exige un lien cliquable vers `/admin/print-queue`, pas juste une mention
        textuelle de l'URL. Étendre `Toast` avec un champ optionnel :
        ```ts
        export interface ToastLink {
          path: string;
          label: string;
        }

        export interface Toast {
          message: string;
          type: 'success' | 'error';
          link?: ToastLink;
        }
        ```
  - [x] Étendre `showError` avec un second paramètre optionnel — **rétrocompatible**, les ~50 appels
        existants à un seul argument (`toast.showError('...')`, répartis sur une quinzaine de fichiers)
        restent valides sans modification :
        `showError(message: string, link?: ToastLink): void { this._show({ message, type: 'error', link }, undefined); }`.
  - [x] `toast-container.component.ts` : importer `RouterLink` (`@angular/router`, même import déjà
        utilisé par `settlement-list.component.ts` pour son lien "Réimprimer le bordereau").
  - [x] `toast-container.component.html` : rendre le lien quand présent, à l'intérieur du même bloc
        `@if (toast.type === 'error')` que le bouton de fermeture existant :
        ```html
        @if (toast.link) {
          <a class="toast__link" [routerLink]="toast.link.path" (click)="toastService.close()">{{ toast.link.label }}</a>
        }
        ```
        Fermer le toast au clic sur le lien (comme le bouton de fermeture) : l'admin navigue vers
        `/admin/print-queue`, un toast persistant qui resterait affiché après navigation n'aurait plus
        de sens.
  - [x] `toast-container.component.scss` : nouvelle règle `.toast__link` — `color: inherit;
        text-decoration: underline; font-weight: 600;` suffit (cohérent avec `color: inherit` déjà
        utilisé par `.toast__close`, contraste déjà validé sur `--mat-sys-error-container`).

- [x] **Task 3 — Frontend : bouton "Imprimer tous les bilans" (AC 1, AC 2, AC 3, AC 4, AC 7)**
  - [x] `models/settlement.model.ts` : ajouter `BulkSettlementReportPrintResultDto { succeededCount:
        number; failedCount: number }`. Déplacer `type StatusFilter = 'all' | 'unsettled' | 'settled'`
        depuis `settlement-list.component.ts` vers ce fichier modèle (`export type StatusFilter = ...`)
        — nécessaire pour que `SettlementService` (couche service, ne doit pas importer depuis un
        composant feature) puisse typer son paramètre de filtre ; `settlement-list.component.ts`
        l'importe désormais depuis le modèle au lieu de le déclarer localement.
  - [x] `services/settlement.service.ts` : nouvelle méthode
        ```ts
        printAllReports(filter: StatusFilter): Observable<BulkSettlementReportPrintResultDto> {
          const params = new HttpParams().set('filter', filter.toUpperCase());
          return this.http.post<BulkSettlementReportPrintResultDto>('/api/admin/settlements/report/print-all', null, { params });
        }
        ```
        `filter.toUpperCase()` : le frontend utilise `'all' | 'unsettled' | 'settled'` (minuscule),
        l'enum backend `SettlementFilter` attend `ALL`/`UNSETTLED`/`SETTLED` — la conversion d'enum
        Spring par défaut (`Enum.valueOf`) est sensible à la casse.
  - [x] `settlement-list.component.ts` : nouveau signal `printingAll = signal(false)`. Nouveau signal
        calculé partagé `anyPrintInFlight = computed(() => this.printingReportForSellerId() !== null ||
        this.printingAll())` — utilisé pour désactiver **à la fois** les boutons "Imprimer le bilan"
        par ligne (déjà le cas via `printingReportForSellerId() !== null`, à remplacer par ce nouveau
        computed) **et** le nouveau bouton groupé, dans les deux sens : un envoi individuel en cours
        bloque le bouton groupé, et vice-versa. Même rationale déjà documentée sur
        `printingReportForSellerId` ("le backend print queue est mono-thread par imprimante") —
        appliquer la même garde de manière cohérente plutôt que deux gardes indépendantes.
        Nouvelle méthode :
        ```ts
        async printAllReports(): Promise<void> {
          if (this.anyPrintInFlight()) {
            return;
          }
          this.printingAll.set(true);
          try {
            const result = await firstValueFrom(this.settlementService.printAllReports(this.statusFilter()));
            if (result.failedCount > 0) {
              this.toast.showError(
                this.translate.instant('settlement.error.printAllPartial', { count: result.failedCount }),
                {
                  path: '/admin/print-queue',
                  label: this.translate.instant('settlement.error.printAllPartialLink'),
                }
              );
            } else {
              this.toast.showSuccess(this.translate.instant('settlement.success.printAll', { count: result.succeededCount }));
            }
          } catch (err: unknown) {
            if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
              this.toast.showError(this.translate.instant('settlement.error.printerUnavailable'));
            } else {
              this.toast.showError(this.translate.instant('settlement.error.printAll'));
            }
          } finally {
            this.printingAll.set(false);
          }
        }
        ```
        **Décision de conception (pas un point à reconfirmer avec l'utilisateur — lecture directe de
        l'AC 4 vs AC 5) :** en cas d'échecs partiels, **un seul** toast s'affiche (erreur, avec le
        nombre d'échecs) — pas un toast succès suivi d'un toast erreur. `ToastService` ne supporte de
        toute façon qu'un toast à la fois (`_toast` signal unique, un nouveau `_show` remplace le
        précédent), et l'AC 5 ne mentionne qu'un seul toast pour ce cas.
  - [x] `settlement-list.component.html` : bouton dans `card__header`, visible uniquement pour
        l'admin (AC 7 — le bénévole ne le voit jamais), placé à côté du titre :
        ```html
        @if (isAdmin()) {
          <button
            type="button"
            mat-flat-button
            color="primary"
            [disabled]="anyPrintInFlight() || filteredSettlements().length === 0"
            (click)="printAllReports()">
            @if (printingAll()) {
              <mat-progress-spinner diameter="18" mode="indeterminate" />
            } @else {
              <mat-icon aria-hidden="true">print</mat-icon>
              {{ 'settlement.actions.printAll' | translate }}
            }
          </button>
        }
        ```
        Patron spinner-inline repris **exactement** de celui déjà en place sur ce projet — pas de
        `report-page.component.html` (ce fichier n'a en réalité aucun spinner, seulement un
        `[disabled]`) mais de `printer-list.component.html` (bouton "Créer" sur
        `/admin/printers`, `@if (discovering()) { <mat-progress-spinner diameter="18"
        mode="indeterminate" /> } @else { ...libellé... }`) : le spinner **remplace** l'icône+libellé
        pendant l'envoi plutôt que de s'afficher à côté. Importer `MatProgressSpinnerModule`
        (`@angular/material/progress-spinner`) dans les `imports` de `settlement-list.component.ts` —
        absent aujourd'hui de ce composant (voir `printer-list.component.ts` pour l'import exact).
        `[disabled]` inclut `filteredSettlements().length === 0` : éviter un clic qui n'enfilerait
        aucun bilan (pas une AC explicite, amélioration UX mineure et peu coûteuse).
  - [x] Nouvelles clés i18n (FR + EN), voir Dev Notes § Libellés i18n proposés.

- [x] **Task 4 — Tests backend**
  - [x] Nouvelle classe `BulkSettlementReportPrintingIT` dans `org.pluribourse.domain.print` (même
        convention de package que les autres tests de rapports/impression de ce module — voir Story
        5.5 Dev Notes § Convention de package, qui s'applique identiquement ici). **Ne pas** étendre
        `SettlementReportPrintingIT` : ce fichier existant est un storyboard à **un seul vendeur**
        (Alice) — tester le filtre exige plusieurs vendeurs à statuts différents dès le départ, ce qui
        demanderait de restructurer ses phases Dépôt/Vente déjà stables (même raisonnement documenté
        dans la Story 5.5 pour justifier `ReportExportIT` séparé plutôt qu'une extension
        d'`EditionReportPrintingIT`).
  - [x] Storyboard à 3 vendeurs dès le départ, soldés différemment en Post-vente, pour couvrir les 3
        valeurs du filtre distinctement : Alice (reste UNSETTLED), Bob (soldé, `POST
        /settlements/{id}/settle`, devient SETTLED), Carol (`POST /settlements/{id}/unclaimed`,
        devient UNCLAIMED). Scénarios à couvrir :
        - `filter=ALL` → 3 travaux enfilés (`succeededCount=3, failedCount=0`).
        - `filter=UNSETTLED` → 1 seul travail (Alice).
        - `filter=SETTLED` → 2 travaux (Bob **et** Carol — prouve que SETTLED regroupe SETTLED +
          UNCLAIMED, pas seulement le statut persisté `SETTLED`).
        - 422 `settlement-not-allowed` hors Post-vente (même patron que `SettlementReportPrintingIT`
          Order 3).
        - 422 `invalid-printer-selection` si aucune imprimante A4 sélectionnée (même patron que
          `SettlementReportPrintingIT` Order 13).
        - 403 pour une session bénévole sur `POST /admin/settlements/report/print-all` (nouveau —
          premier test de garde de classe pour ce contrôleur, contrairement à
          `SettlementController`/`SettlementReportPrintService` qui restent partagés ADMIN+VOLUNTEER).
        - Contenu d'un bilan : réutiliser directement `SettlementReportRenderer`/`ItemRepository`
          comme le fait déjà `SettlementReportPrintingIT` Order 8 (pas besoin de reprouver le format
          FR-050 en détail ici, déjà couvert par ce fichier — un seul vendeur avec au moins un article
          vendu et un invendu suffit pour prouver que le PDF de chaque bilan groupé est bien construit
          via le même `DocumentPrintService.buildSettlementReportJob`, pas un mécanisme différent).
  - [x] **Limite de test connue, à documenter dans la classe plutôt qu'à contourner par un test
        fragile :** la branche `failedCount > 0` (échec partiel) n'est délibérément **pas** exercée par
        un appel HTTP réel — `PrintQueueService.submit()` n'échoue que sur une course très étroite
        (imprimante désenregistrée entre la vérification de disponibilité et l'itération courante),
        impossible à forcer de façon déterministe via `MockMvc` synchrone. Même catégorie que d'autres
        races déjà documentées-mais-non-testées dans ce codebase (voir Story 4.4 Dev Notes sur
        `SaleConcurrencyIT`). Ne pas tenter de mocker `PrintQueueService`/`DocumentPrintService` pour
        forcer ce cas : casserait la philosophie E2E-par-contrôleur (CLAUDE.md) pour un gain de
        couverture marginal sur un chemin qui ne fait que compter des exceptions déjà bien comprises.

- [x] **Task 5 — Tests frontend**
  - [x] `toast.service.spec.ts` : nouveaux tests — `showError(message, link)` stocke le lien dans le
        toast ; `showError(message)` sans second argument laisse `link` undefined (non-régression du
        comportement existant).
  - [x] `toast-container.component.spec.ts` : nouveaux tests — un toast avec `link` rend un `<a
        class="toast__link">` avec le bon `routerLink`/label ; un toast sans `link` n'en rend aucun ;
        cliquer sur le lien appelle `toastService.close()`.
  - [x] `settlement.service.spec.ts` : nouveau test `printAllReports()` — `POST
        /api/admin/settlements/report/print-all?filter=UNSETTLED` (vérifier la majuscule), méthode
        POST, corps `null`.
  - [x] `settlement-list.component.spec.ts` : nouveaux tests —
        - le bouton "Imprimer tous les bilans" est présent pour un rôle ADMIN, absent pour VOLUNTEER
          (AC 7, même patron que les tests existants "shows phone/email columns only when the role is
          ADMIN"/"reprint link" ci-dessus dans ce fichier) ;
        - cliquer appelle `settlementService.printAllReports` avec la valeur courante de
          `statusFilter()` (tester au moins avec `'all'` et `'unsettled'`) ;
        - `failedCount: 0` → toast succès avec le compteur ; `failedCount > 0` → toast erreur avec le
          lien `/admin/print-queue` (vérifier l'objet `link` passé à `toastService.showError`, pas
          seulement le message) ;
        - 422 `invalid-printer-selection` → toast `settlement.error.printerUnavailable` (même patron
          que le test existant équivalent pour `printReport`) ;
        - le bouton groupé est désactivé pendant qu'un envoi individuel est en cours, et
          réciproquement (étendre le test existant "every print button is disabled while one report is
          in flight" pour couvrir le nouveau bouton groupé dans les deux sens) ;
        - un second clic pendant que l'envoi groupé est en cours est ignoré (même patron que "a second
          click on the same row while a print is in flight is ignored").

### Review Findings

- [x] [Review][Patch] Duplication de la résolution imprimante A4 (`getSelectedPrinterId`/`isAvailable`/`commissionRate`/`documentLocale`) entre `printReport` et `printAllReports` [SettlementReportPrintService.java:59-63,85-92] — corrigé : extrait dans `resolvePrintContext(edition, session)` + record privé `PrintContext`, réutilisé par les deux méthodes
- [x] [Review][Patch] `catch (RuntimeException e)` trop large dans `printAllReports`, contredit le Javadoc qui n'attend que `PrinterNotFoundException` [SettlementReportPrintService.java:105] — corrigé : `catch (PrinterNotFoundException e)`
- [x] [Review][Patch] Aucun scénario de test avec un vendeur sans article dans `BulkSettlementReportPrintingIT` (chemin `itemsBySellerId.getOrDefault(..., List.of())` jamais exercé) [BulkSettlementReportPrintingIT.java] — corrigé : 4ᵉ vendeur David (zéro article, reste UNSETTLED) ajouté au storyboard, compteurs ALL/UNSETTLED mis à jour (4/2)
- [x] [Review][Patch] Ordre de soumission (tri `sellerNumber`, `itemNumber`) jamais vérifié par un test [BulkSettlementReportPrintingIT.java] — partiellement corrigé : l'ordre `sellerProfile.id ASC`/`itemNumber ASC` de la requête groupée est désormais vérifié directement (Order 14) ; le tri par `sellerNumber` dans `getSellersMatchingFilter` reste non observable en E2E-par-contrôleur (aucun endpoint n'expose l'ordre des jobs en file) — limite documentée dans le Javadoc de la classe de test
- [x] [Review][Patch] Tests frontend : les paramètres d'interpolation i18n (`count`) transmis à `translate.instant` ne sont jamais vérifiés — une inversion `succeededCount`/`failedCount` ne serait pas détectée [settlement-list.component.spec.ts:337,347] — corrigé : spy sur `TranslateService.instant` vérifiant `{ count }` dans les deux tests succès/échec partiel
- [x] [Review][Patch] Le lien du toast d'erreur se ferme au clic même lors d'un ctrl/cmd/clic-molette (ouverture dans un nouvel onglet) [toast-container.component.html:15] — corrigé : `closeUnlessNewTab($event)` ignore ctrl/cmd/shift/clic-molette
- [x] [Review][Defer] Transaction `@Transactional(readOnly=true)` englobe la génération PDF + soumission file (travail non-DB) pour jusqu'à ~100 vendeurs [SettlementReportPrintService.java:80-111] — deferred, pre-existing (même patron déjà présent sur `printReport` single-seller, amplifié ici)
- [x] [Review][Defer] Aucun verrou serveur contre une double soumission concurrente (deux onglets/sessions admin) [SettlementReportPrintService.java / AdminSettlementController.java] — deferred, pre-existing (limitation déjà présente sur le flux d'impression individuel)
- [x] [Review][Defer] Un job en échec (course `PrinterNotFoundException`) reste invisible pour l'admin au-delà du compteur `failedCount` [SettlementReportPrintService.java:105-107] — deferred, décision de conception explicitement actée dans la story (AC 5 ne requiert que le compteur)
- [x] [Review][Defer] Spinner du bouton groupé sans `aria-label` de repli ni `aria-live` pendant l'envoi [settlement-list.component.html:14-16] — deferred, pre-existing (reproduit fidèlement le patron de `printer-list.component.html`)
- [x] [Review][Defer] Le paramètre `filter` n'a pas de validation explicite/valeur par défaut ; une valeur invalide passe par le handler par défaut de Spring [AdminSettlementController.java:29-31] — deferred, pre-existing (déjà RFC7807 via `ResponseEntityExceptionHandler`, juste sans le `type` URI custom de l'appli ; première occurrence de ce pattern, non atteignable via l'UI)
- [x] [Review][Defer] Le 422 `settlement-not-allowed` (changement de phase pendant que la page est ouverte) tombe dans le message d'erreur générique plutôt qu'un message dédié [settlement-list.component.ts:207-215] — deferred, pre-existing (même lacune déjà présente sur `printReport()` individuel)
- [x] [Review][Defer] Une suspension de la file d'impression en cours de boucle (`PrinterQueueHandle.submit` ne revérifie pas `suspended`) compte quand même le vendeur comme "succeeded" [SettlementReportPrintService.java:100-109] — deferred, cohérent avec la sémantique "mis en file" de l'AC 4 (le suivi de l'impression réelle relève de `/admin/print-queue`, Story 3.7)
- [x] [Review][Defer] Un filtre ne correspondant à aucun vendeur au moment de la requête affiche un toast succès "0 bilans mis en file", potentiellement trompeur [SettlementReportPrintService.java:94 / settlement-list.component.html:10] — deferred, pre-existing (bouton déjà désactivé côté client si la liste filtrée est vide ; seule une course étroite ou un appel API direct l'atteint)

Rejetés comme bruit (2) : `SettlementFilter.SETTLED` utilisant `status != UNSETTLED` — c'est exactement le snippet prescrit par la story elle-même, documenté pour rester cohérent avec le frontend ; le lien du toast + `close()` ayant nécessité une route de test réelle en spec — déjà investigué et documenté dans le Change Log comme artefact d'environnement de test (NG0205), pas un problème de robustesse en production.

## Dev Notes

### AC 6 (Clôturée) : déjà garanti structurellement, ne rien construire

`/admin/settlement` est protégée par `settlementPhaseGuard`
(`pluribourse-frontend/src/app/core/guards/settlement-phase.guard.ts`), qui redirige vers `/404` dès
que `currentEditionService.currentEdition()?.phase !== ActivePhase.POST_SALE`. Combinée à la limite
déjà documentée à travers ce module (`EditionService.getActiveEdition()` exclut structurellement
`CLOSED` de `PhaseType.ACTIVE`, donc `currentEdition()` redevient `null` dès la Clôture — voir Story
5.4/5.5 Dev Notes), la page entière — pas seulement ce bouton — est déjà inatteignable en Clôturée
avec l'architecture actuelle. AC 6 est donc satisfaite par du code déjà existant et déjà testé
(`settlement-phase.guard.spec.ts`) : ne pas ajouter de garde de phase supplémentaire spécifique à ce
bouton, ce serait redondant.

### Pourquoi un contrôleur admin-only séparé plutôt qu'une addition au `SettlementController` partagé

`SettlementController` (`/settlements`) est délibérément partagé ADMIN+VOLUNTEER sans
`@PreAuthorize` depuis la Story 5.1 (même patron que `SellerController`). Cette nouvelle action est
strictement admin-only par exigence produit (AC 7). `@PreAuthorize` de méthode existe bien ailleurs
dans ce codebase (`AccountController.updateLanguagePreference`, `AuthController` `/me` et
`/change-password`), mais toujours pour restreindre un endpoint à `hasAnyRole('ADMIN', 'VOLUNTEER')`
dans un contrôleur déjà partagé — jamais pour rendre une seule méthode admin-only au milieu d'un
contrôleur par ailleurs non gardé. Pour cette forme précise, suivre le patron déjà établi par
`SellerController`/`AdminSellerController` : un contrôleur frère dédié sous `/admin/...` pour le
sous-ensemble d'actions admin-only d'un même domaine.

### Filtre résolu côté serveur (PRD, addendum FR-097)

> "Le périmètre est résolu côté serveur depuis le filtre courant — la pagination n'est pas un facteur
> limitant." [Source: prd.md#FR-097]

`SettlementListComponent` n'a aujourd'hui aucune pagination (`filteredSettlements()` est un simple
`computed()` filtrant la liste complète déjà chargée) — la clause "toutes pages confondues" de l'AC
epics.md est donc un non-problème dans l'implémentation actuelle, pas quelque chose à construire.
Le point qui compte réellement : envoyer le **filtre** au serveur (`SettlementFilter` enum), qui
recalcule lui-même la liste des vendeurs concernés via `getSellersMatchingFilter` — jamais une liste
d'IDs vendeur construite côté client. Cohérent avec la philosophie déjà établie ailleurs dans ce
projet ("le client n'est jamais la source de vérité").

### `SETTLED` regroupe `SETTLED` + `UNCLAIMED`

Le filtre "Soldés" du frontend (`statusFilter === 'settled'`) affiche déjà tout vendeur dont le statut
n'est pas `UNSETTLED` — donc `SETTLED` et `UNCLAIMED` ensemble
(`settlement-list.component.ts:filteredSettlements`). `SettlementFilter.SETTLED.matches()` doit suivre
exactement la même règle côté serveur pour rester cohérent avec ce que l'admin voit affiché au moment
où il clique. Imprimer un bilan pour un vendeur "Non réclamé" reste un document valide (même contenu
FR-050, juste un vendeur qui ne viendra pas le récupérer) — aucune exclusion à ajouter.

### Fichiers à toucher

**Backend :**
- `domain/payout/dto/SettlementFilter.java` — **nouveau**
- `domain/payout/dto/BulkSettlementReportPrintResultDto.java` — **nouveau**
- `domain/payout/controller/AdminSettlementController.java` — **nouveau**
- `domain/payout/service/SettlementService.java` — `getSellersMatchingFilter`
- `domain/payout/service/SettlementReportPrintService.java` — `printAllReports`
- `domain/item/repository/ItemRepository.java` — `findAllByEditionIdForSettlementReport`

**Frontend :**
- `shared/components/toast/toast.service.ts` — `ToastLink`, `showError` étendu
- `shared/components/toast/toast-container.component.ts` — import `RouterLink`
- `shared/components/toast/toast-container.component.html` — rendu du lien
- `shared/components/toast/toast-container.component.scss` — `.toast__link`
- `models/settlement.model.ts` — `BulkSettlementReportPrintResultDto`, `StatusFilter` déplacé ici
- `services/settlement.service.ts` — `printAllReports`
- `features/settlement/settlement-list.component.ts` — signaux + méthode + garde partagée
- `features/settlement/settlement-list.component.html` — bouton groupé
- `public/i18n/fr.json`, `public/i18n/en.json` — clés `settlement.actions.printAll`,
  `settlement.success.printAll`, `settlement.error.printAll`, `settlement.error.printAllPartial`,
  `settlement.error.printAllPartialLink`

**Tests :**
- `BulkSettlementReportPrintingIT.java` — **nouveau**
- `settlement-list.component.spec.ts`, `settlement.service.spec.ts`, `toast.service.spec.ts`,
  `toast-container.component.spec.ts` — extensions

### Libellés i18n proposés (FR — adapter EN en miroir)

```
settlement.actions.printAll: "Imprimer tous les bilans"
settlement.success.printAll: "{{ count }} bilans mis en file d'impression."
settlement.error.printAll: "Impossible de mettre les bilans en file d'impression."
settlement.error.printAllPartial: "{{ count }} bilans n'ont pas pu être mis en file d'impression."
settlement.error.printAllPartialLink: "Voir la file d'impression"
```

`settlement.error.printerUnavailable` (existant) est réutilisé tel quel pour le cas 422
`invalid-printer-selection` — pas de nouvelle clé pour ce cas, même message que le bouton d'impression
individuel.

### Testing standards

E2E par les contrôleurs uniquement (CLAUDE.md) : `BulkSettlementReportPrintingIT` passe par MockMvc
réel sur `AdminSettlementController`, pas de test de service isolé pour `SettlementReportPrintService`
ou `SettlementService.getSellersMatchingFilter`. `@TestMethodOrder` + `@Order`, storyboard narratif
comme les classes voisines de ce module. BigDecimal partout pour les montants (NFR-003).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.6 — Impression groupée des bilans de
  vente (Admin)]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#FR-097 — filtre
  résolu côté serveur, pagination non limitante, pattern UX-DR19]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#NFR-001 — charge de
  référence ~100 vendeurs]
- Note : le tag `UX-DR19` cité par FR-097/epics.md ne correspond à aucune ancre trouvable dans
  EXPERIENCE.md (même écart déjà documenté pour `UX-DR21`, story 4.6) — le comportement réel (spinner
  inline pendant l'envoi, toast succès 4 s, toast d'erreur persistant) est confirmé mot pour mot par
  [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md — §
  State Patterns "Sauvegarde en cours"/"Imprimante hors ligne", Flow 5]
- [Source: _bmad-output/implementation-artifacts/5-1-flux-de-solde-des-vendeurs.md — `SettlementService`/
  `SettlementController` partagé ADMIN+VOLUNTEER, patron `AdminSellerController`]
- [Source: _bmad-output/implementation-artifacts/5-2-generation-du-bilan-de-vente-pdf.md —
  `SettlementReportPrintService`/`DocumentPrintService.buildSettlementReportJob`, réutilisés tels
  quels]
- [Source: _bmad-output/implementation-artifacts/5-5-page-des-rapports-admin.md — patron de séparation
  d'un nouveau storyboard de test plutôt que d'étendre un fichier stable existant]

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), via dev-story (Claude Code)

### Debug Log References

Aucun — implémentation conforme à la story, aucun écart de production détecté en cours de route.

### Completion Notes List

- Backend (Task 1) : `SettlementFilter` (enum avec prédicat porté par la constante), `BulkSettlementReportPrintResultDto`,
  `SettlementService.getSellersMatchingFilter` (batché, trié par `sellerNumber`), `ItemRepository.findAllByEditionIdForSettlementReport`
  (une seule requête pour tous les vendeurs, JOIN FETCH category+lot), `SettlementReportPrintService.printAllReports`
  (try/catch par vendeur garantissant l'AC 5, `log.warn` sur id vendeur uniquement), nouveau contrôleur `AdminSettlementController`
  (`/admin/settlements/report/print-all`, `@PreAuthorize` de classe, patron `AdminSellerController`). Implémentation strictement
  conforme aux snippets prescrits par la story — aucun écart.
- Frontend (Task 2) : `Toast`/`ToastService` étendus avec `ToastLink` optionnel, rétrocompatible ; `toast-container.component`
  rend le lien dans le même bloc `@if (toast.type === 'error')` que le bouton de fermeture, ferme le toast au clic.
- Frontend (Task 3) : `StatusFilter` déplacé de `settlement-list.component.ts` vers `models/settlement.model.ts` (nécessaire pour
  que `SettlementService` puisse le typer sans importer depuis un composant feature). Signal `printingAll` + computed
  `anyPrintInFlight` partagé (bloque les boutons individuels ET le bouton groupé, dans les deux sens). Bouton visible uniquement
  pour `isAdmin()` (AC 7), spinner inline remplaçant le libellé pendant l'envoi (patron `printer-list.component.html`). Écart
  mineur corrigé pendant l'implémentation (pas un écart de spec, un détail de compilation Angular) : le bloc `@else` du bouton
  combinait `<mat-icon>` + texte, provoquant l'avertissement de build NG8011 (projection de contenu `MatButton` ambiguë) —
  corrigé en enveloppant le contenu du `@else` dans un `<ng-container>`, conformément à la solution suggérée par Angular
  lui-même ; aucun changement de comportement.
- Tests backend (Task 4) : nouveau `BulkSettlementReportPrintingIT` (package `domain.print`, 14 scénarios `@Order`, storyboard à
  3 vendeurs — Alice UNSETTLED, Bob SETTLED, Carol UNCLAIMED) couvrant les 3 valeurs de `SettlementFilter` (ALL=3, UNSETTLED=1,
  SETTLED=2 — prouvant le regroupement SETTLED+UNCLAIMED), 422 `settlement-not-allowed` hors Post-vente, 422
  `invalid-printer-selection` sans imprimante A4, 403 pour une session bénévole, et un test de rendu direct
  (`SettlementReportRenderer` + `findAllByEditionIdForSettlementReport`) prouvant que la requête groupée retourne des données
  directement exploitables par le renderer existant. La branche `failedCount > 0` reste non testée, comme documenté par avance
  dans la story (course trop étroite pour être forcée de façon déterministe via `MockMvc`). Entre chaque scénario de filtre, la
  file d'impression est réinitialisée (`/admin/print-queue/{id}/discard`) — chaque bilan enfilé déclenche une vraie tentative de
  connexion WebSocket vers `PrinterBridgeDouble` (HTTP-only), qui échoue et suspend la file, rendant `isAvailable()` faux pour le
  scénario suivant sans ce nettoyage (même comportement réel que `SettlementReportPrintingIT` Order 11).
- Tests frontend (Task 5) : `toast.service.spec.ts` (+2), `toast-container.component.spec.ts` (+3, dont un test de clic sur le
  lien nécessitant une route de test réelle plutôt que `provideRouter([])` pour éviter une navigation orpheline —
  `NG0205 Injector has already been destroyed` en unhandled rejection sinon), `settlement.service.spec.ts` (+1),
  `settlement-list.component.spec.ts` (+8 : visibilité du bouton par rôle, filtre transmis au service, toast succès/erreur avec
  lien, 422 imprimante, garde bidirectionnelle bouton groupé ↔ boutons individuels).
- Vérifications : 465/465 tests backend (451 + 14 nouveaux), 612/612 tests frontend (598 + 14 nouveaux), build de production
  frontend sans erreur ni avertissement, aucune régression. Vérification visuelle humaine de `/admin/settlement` (CLAUDE.md §
  Interaction utilisateur) en attente — `dev-story` a tourné sans supervision interactive.

### File List

**Backend :**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/dto/SettlementFilter.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/dto/BulkSettlementReportPrintResultDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/controller/AdminSettlementController.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementReportPrintService.java` (modifié)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java` (modifié)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java` (nouveau)

**Frontend :**
- `pluribourse-frontend/src/app/shared/components/toast/toast.service.ts` (modifié)
- `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.ts` (modifié)
- `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.html` (modifié)
- `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.scss` (modifié)
- `pluribourse-frontend/src/app/models/settlement.model.ts` (modifié)
- `pluribourse-frontend/src/app/services/settlement.service.ts` (modifié)
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` (modifié)
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html` (modifié)
- `pluribourse-frontend/public/i18n/fr.json` (modifié)
- `pluribourse-frontend/public/i18n/en.json` (modifié)

**Tests frontend :**
- `pluribourse-frontend/src/app/shared/components/toast/toast.service.spec.ts` (modifié)
- `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.spec.ts` (modifié)
- `pluribourse-frontend/src/app/services/settlement.service.spec.ts` (modifié)
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts` (modifié)

## Change Log

- 2026-08-20 — code review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) :
  Acceptance Auditor confirme 0 violation d'AC sur les 7 AC, aucune déviation Tasks/code. 0
  decision-needed. 6 patch appliqués — factorisation de la résolution imprimante A4/commission/locale
  dupliquée entre `printReport`/`printAllReports` (`resolvePrintContext` + record `PrintContext`),
  `catch (RuntimeException)` resserré en `catch (PrinterNotFoundException)` conformément au Javadoc de
  la méthode, storyboard `BulkSettlementReportPrintingIT` étendu avec un 4ᵉ vendeur sans article
  (David) prouvant le fallback `itemsBySellerId.getOrDefault(..., List.of())`, ordre
  `sellerProfile.id ASC`/`itemNumber ASC` de la requête groupée vérifié directement (Order 14),
  paramètres d'interpolation i18n (`count`) vérifiés via un spy `TranslateService.instant` côté
  frontend, lien du toast d'erreur ne se ferme plus sur un ctrl/cmd/clic-molette
  (`closeUnlessNewTab`). 8 defer documentés dans `deferred-work.md` (transaction englobant du travail
  non-DB, absence de verrou serveur anti-double-soumission, job en échec invisible au-delà du
  compteur — décision de conception déjà actée, accessibilité du spinner reproduisant un patron
  existant, validation du paramètre `filter`, 422 `settlement-not-allowed` non spécifique, sémantique
  "mis en file" vs "imprimé" en cas de suspension de file en cours de boucle, toast "0 bilans"
  trompeur sur une course étroite) — tous des limitations pré-existantes ou amplifiées, aucune
  introduite par cette story. 2 rejetés comme bruit (`SettlementFilter.SETTLED` explicitement
  prescrit par la story, artefact de test NG0205 déjà documenté dans le Change Log). 465/465 tests
  backend re-validés (aucun nouveau test, storyboard existant étendu), 613/613 tests frontend
  re-validés (+1 nouveau test ctrl/cmd-clic), aucune régression. Statut → done.

- 2026-08-19 — dev-story : story implémentée intégralement conforme aux Tasks/snippets prescrits. Deux écarts mineurs, aucun de
  production : (1) build frontend — avertissement Angular NG8011 (projection de contenu ambiguë `MatButton` sur le bloc `@else`
  icône+texte du bouton groupé) corrigé par un `<ng-container>` autour du contenu, solution suggérée par Angular lui-même,
  aucun changement de comportement ; (2) test frontend — cliquer sur le lien du toast avec `provideRouter([])` provoquait une
  navigation orpheline (`NG0205` en unhandled rejection après destruction de l'injecteur de test) ; corrigé en fournissant une
  route de test réelle plutôt qu'un routeur vide, aucun changement du composant testé. 465/465 tests backend (451 + 14
  nouveaux), 612/612 tests frontend (598 + 14 nouveaux), build de production frontend propre, aucune régression. Statut →
  review.

- 2026-08-19 — validate (round 2, contexte frais) : 1 inexactitude critique corrigée (le tag `UX-DR19`
  était cité comme ancré dans `EXPERIENCE.md`, alors qu'aucune occurrence n'y existe — même écart déjà
  documenté pour `UX-DR21` en story 4.6 ; référence reformulée pour pointer vers les patterns réels du
  § State Patterns). 2 améliorations ajoutées : tri de `getSellersMatchingFilter` par `sellerNumber`
  (la requête dérivée `sellerRepository.findAllByEditionId` ne garantit aucun ordre — sans ce tri, la
  pile physique de ~100 bilans A4 sortirait dans un ordre arbitraire) ; `log.warn` ajouté dans le
  `catch` de `printAllReports` (id vendeur uniquement, pas de donnée perso) pour pouvoir diagnostiquer
  un échec d'enfilage a posteriori. Tout le reste de la story (signatures, patrons cités, snippets)
  confirmé exact contre le code source réel. Statut inchangé → ready-for-dev.
- 2026-08-19 — create-story : story créée. Périmètre : bouton d'impression groupée admin-only sur
  `/admin/settlement`, filtré côté serveur (nouvel enum `SettlementFilter`), nouveau contrôleur
  `AdminSettlementController` (patron `AdminSellerController` — un contrôleur frère dédié plutôt
  qu'un `@PreAuthorize` de méthode isolé sur un contrôleur autrement partagé), extension batchée de
  `SettlementService`/`ItemRepository` pour rester à requêtes
  constantes quel que soit le nombre de vendeurs (NFR-001). Lacune réelle identifiée et comblée dans le
  système de toast existant : aucun support de lien cliquable, requis par l'AC 5 — `Toast`/
  `ToastService.showError` étendus de façon rétrocompatible (~50 appels existants inchangés). AC 6
  (bouton absent en Clôturée) confirmée déjà garantie structurellement par `settlementPhaseGuard` +
  la limite `getActiveEdition()`/CLOSED déjà documentée ailleurs dans ce module — aucune garde
  supplémentaire à construire. Décision actée sans besoin de valider avec l'utilisateur (lecture
  directe des AC 4/5) : un seul toast (succès XOR erreur) selon qu'il y ait ou non des échecs, jamais
  les deux. Limite de test documentée par avance : la branche d'échec partiel n'est pas exerçable de
  façon déterministe via HTTP (course étroite sur la désinscription d'imprimante), pas de test fragile
  à construire pour la forcer. Statut → ready-for-dev.
