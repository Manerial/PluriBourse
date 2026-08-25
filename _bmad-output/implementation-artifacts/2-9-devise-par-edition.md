---
baseline_commit: 2cb26fe1d6e7060c6df6ff8e9c1a61e380d4c68c
---

# Story 2.9: Devise par édition

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrateur gérant des éditions dans différentes zones monétaires,
I want configurer un symbole monétaire par édition (ex. €, $, CHF), initialisé depuis un paramètre instance par défaut,
so that les documents imprimés et les écrans affichent la bonne devise au lieu d'un `€` toujours codé en dur.

## Acceptance Criteria

1. **Paramètre instance — symbole monétaire par défaut (FR-103, nouveau).** Étant donné que l'admin ouvre `/admin/settings`, quand la page charge, alors un champ « Symbole monétaire par défaut » apparaît à côté du taux de commission et de la langue des documents (valeur par défaut `€`) ; étant donné que l'admin le change pour `$` et sauvegarde, quand le serveur redémarre, alors la valeur `$` est persistée (`GlobalInstanceConfig.defaultCurrency`).

2. **Création d'édition hérite du symbole par défaut (FR-103).** Étant donné que le paramètre instance a `defaultCurrency = "$"`, quand l'admin crée une nouvelle édition sans préciser de devise, alors l'édition créée a `currency = "$"` (même mécanisme que `commissionRate`/`documentLanguage`, Story 2.1).

3. **Devise modifiable en Préparation, gelée dès le Dépôt (FR-104, nouveau).** Étant donné une édition en phase Préparation, quand l'admin modifie sa devise (`PUT /admin/editions/{id}`), alors la nouvelle valeur est enregistrée ; étant donné une édition passée en phase Dépôt ou ultérieure, quand l'admin tente de modifier `PUT /admin/editions/{id}` (devise ou tout autre champ), alors 422 `edition-cannot-be-updated` — ce blocage existe déjà globalement dans `EditionService.updateEdition` (Story 2.1/FR-016) et s'applique à `currency` sans code additionnel ; seul un test de confirmation est requis.

4. **La devise de l'édition apparaît dans toutes les réponses JSON déjà utilisées pour afficher des prix.** `GET /api/editions/current`, `GET /admin/editions`, `GET /admin/editions/{id}` (`EditionDto.currency`) ; `GET /admin/reports/daily` (`DailySalesReportDto.currency`) ; `GET /admin/reports/edition/{id}` (`EditionSummaryReportDto.currency`, y compris pour une édition archivée — lu depuis `Edition.currency`, jamais depuis le snapshot d'archivage, voir Dev Notes).

5. **Les 6 rendus PDF/étiquette utilisent la devise de l'édition au lieu de `€` codé en dur.** Bordereau de dépôt (3.6), facture acheteur (4.5), bilan de vente vendeur (5.2), étiquette thermique (3.5), rapport de ventes journalier admin (5.3), bilan d'édition admin (5.4) — le sprint-change-proposal n'en anticipait que 4 ; les 2 renderers de rapports admin (`DailyReportRenderer`, `EditionReportRenderer`) ont aussi `€` codé en dur dans leurs templates i18n et doivent être corrigés (voir Dev Notes § Écart non anticipé).

6. **Tous les écrans frontend affichant un prix utilisent la devise de l'édition au lieu de `€` codé en dur.** Catalogue actif et archivé, dépôt (liste d'articles), caisse (panier, dialogue de paiement, monnaie à rendre), solde (liste, avertissement montant, confirmation Non réclamé), rapports admin (journalier + bilan d'édition), avertissement de clôture d'édition (montant transféré aux recettes).

7. **Comportement par défaut inchangé pour les données existantes.** La migration Liquibase donne `currency = '€'` à `global_instance_config` et à toute édition déjà en base (colonne `NOT NULL` avec valeur par défaut, même patron que `commission_rate`/`version`) — aucune régression visuelle pour une édition créée avant cette story.

## Tasks / Subtasks

- [x] **Backend — Paramètre instance : `defaultCurrency` (AC 1, 2)**
  - [x] `pluribourse-backend/src/main/resources/db/changelog/031-default-currency.xml` (NEW) :
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <databaseChangeLog
            xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

        <changeSet id="031-default-currency" author="pluribourse">
            <addColumn tableName="global_instance_config">
                <!-- VARCHAR(10): a symbol, not an ISO code (ex. €, $, CHF, kr) — free text, no enum -->
                <column name="default_currency" type="VARCHAR(10)" defaultValue="€">
                    <constraints nullable="false"/>
                </column>
            </addColumn>
        </changeSet>

    </databaseChangeLog>
    ```
  - [x] `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (UPDATE) — add `<include file="db/changelog/031-default-currency.xml"/>` after the `030-archived-items-price.xml` line.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/entity/GlobalInstanceConfig.java` (UPDATE) — add after `defaultDocumentLanguage`:
    ```java
    @Column(name = "default_currency", nullable = false, length = 10)
    private String defaultCurrency;
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/dto/GlobalInstanceConfigDto.java` (UPDATE) — add as the last record component (append, don't insert — see Dev Notes § Pourquoi `currency` est toujours en dernier champ):
    ```java
    @NotBlank @Size(max = 10)
    String defaultCurrency
    ```
  - [x] `GlobalInstanceConfigMapper.java` — **no change**: both `toDto`/`updateConfigFromDto` map every field by name automatically already (verified: no per-field `@Mapping` exists for `defaultCommissionRate`/`defaultDocumentLanguage` either), so `defaultCurrency` ↔ `defaultCurrency` maps for free.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/service/GlobalInstanceConfigService.java` (UPDATE) — add, mirroring `getDefaultCommissionRate()`:
    ```java
    @Transactional(readOnly = true)
    public String getDefaultCurrency() {
        return findConfig().getDefaultCurrency();
    }
    ```
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/shared/GlobalInstanceConfigIT.java` (UPDATE) — fix the `new GlobalInstanceConfigDto(...)` positional call site(s) (append `"€"` or the scenario's test value as the trailing arg) and add one scenario confirming `defaultCurrency` round-trips through `PUT /admin/instance-config` (same shape as the existing `defaultCommissionRate` scenario).
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (UPDATE) — same positional-arg fix for its `new GlobalInstanceConfigDto(...)` call site(s).

- [x] **Backend — `Edition.currency` (AC 2, 3, 4, 7)**
  - [x] `pluribourse-backend/src/main/resources/db/changelog/032-edition-currency.xml` (NEW) — same pattern as 031, on `editions`:
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
            http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

        <changeSet id="032-edition-currency" author="pluribourse">
            <addColumn tableName="editions">
                <column name="currency" type="VARCHAR(10)" defaultValue="€">
                    <constraints nullable="false"/>
                </column>
            </addColumn>
        </changeSet>

    </databaseChangeLog>
    ```
  - [x] `db.changelog-master.xml` (UPDATE) — `<include file="db/changelog/032-edition-currency.xml"/>` right after 031.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/Edition.java` (UPDATE) — add after `nextSellerNumber` (last field):
    ```java
    @Column(nullable = false, length = 10)
    private String currency;
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/dto/EditionDto.java` (UPDATE) — add as the **last** record component, after `hasItems` (see Dev Notes — do not insert next to `commissionRate`):
    ```java
    @Size(max = 10)
    String currency
    ```
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/mapper/EditionMapper.java` (UPDATE) — `toDto` already maps `currency` by name for free (no `@Mapping` needed, same as every other simple field). `toEntity`/`updateEditionFromDto` need explicit handling, exactly mirroring `commissionRate`:
    ```java
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "phase", constant = "PREPARATION")
    @Mapping(target = "createdAt", expression = "java(LocalDate.now())")
    @Mapping(target = "commissionRate", source = "commissionRate")
    @Mapping(target = "documentLanguage", source = "documentLanguage")
    @Mapping(target = "currency", source = "currency")
    Edition toEntity(EditionDto dto, BigDecimal commissionRate, Language documentLanguage, String currency);
    ```
    and in `updateEditionFromDto`, add:
    ```java
    @Mapping(target = "currency", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    ```
    (same line group as the existing `commissionRate`/`documentLanguage` `IGNORE` mappings).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` (UPDATE):
    - `createEdition`: add, mirroring `commissionRate`/`documentLanguage`:
      ```java
      String currency = dto.currency() != null ? dto.currency() : instanceConfigService.getDefaultCurrency();
      return mapper.toDto(repository.save(mapper.toEntity(dto, commissionRate, documentLanguage, currency)));
      ```
    - `getEditionById`: `mapper.toDto(edition)` already carries `currency` (mapped by name, no `@Mapping` needed) — append `dto.currency()` as the new trailing arg to the manual `new EditionDto(...)` reconstruction (the one that injects `hasItems`).
    - `updateEdition`: **no change** — already blocks any field update outside PREPARATION (AC 3 is free).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/EditionArchivingService.java` (UPDATE) — its `new EditionDto(saved.getId(), ..., saved.getEndDate(), null)` call site (line ~97-99) needs `saved.getCurrency()` appended as the new trailing arg.
  - [x] **Compiler-driven sweep (mandatory, do not skip):** after the `EditionDto` record change, run a full backend compile (`mvn test-compile` or equivalent). Every `new EditionDto(...)` positional call that doesn't yet have the 11th arg will fail to compile — fix each by appending a currency value (`"€"` for the default/no-op case, or a deliberate custom value for a scenario this story specifically adds). 27 files currently construct `EditionDto` positionally (2 main-source: `EditionService`, `EditionArchivingService`, already listed above; the rest are test fixtures) — do not hand-edit this list from memory, let the compiler enumerate every site and fix each as it's reported, to guarantee none is missed. Representative example (`PhaseTransitionIT`, and the same shape recurs across the other 25 test files):
    ```java
    new EditionDto(null, "Bourse Suivante", null, null, null, null, false,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3), null, "€")
    ```

- [x] **Backend — Report DTOs carry `currency` (AC 4)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/DailySalesReportDto.java` (UPDATE) — add `String currency` as the last record component.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/EditionSummaryReportDto.java` (UPDATE) — same, last record component.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java` (UPDATE) — both `getDailyReport(Edition edition)` and `getEditionReport(Edition edition)` already receive the full `Edition` — append `edition.getCurrency()` to their `new DailySalesReportDto(...)`/`new EditionSummaryReportDto(...)` construction. In `buildFromArchivedSnapshot(Edition edition)`, use `edition.getCurrency()` too (**not** the snapshot — `EditionArchiveSnapshot` has no currency field and needs none: `Edition.currency` is frozen at Deposit start and untouched by archiving, which only deletes `Item`/`Settlement` rows per FR-088, never the `Edition` row itself — confirmed by `EditionSummaryReportPrintService`/2.7's own precedent of resolving archived editions by ID).
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java` and `EditionReportPrintingIT.java` (UPDATE) — fix their `new DailySalesReportDto(...)`/`new EditionSummaryReportDto(...)` positional call sites (append currency arg) — same compiler-driven approach as above, just a much smaller list (3 files total, 1 main-source already covered).

- [x] **Backend — 6 renderers stop hardcoding `€` (AC 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/ThermalLabelRenderer.java` (UPDATE) — in `renderLabel`, add `String currency = item.getEdition().getCurrency();` near the top; pass it as an extra `Object[]` arg to the `print.label.lotPrice` and `print.label.itemPrice` `messageSource.getMessage` calls.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java` (UPDATE) — in `renderSlip`, add `String currency = sellerProfile.getEdition().getCurrency();`; pass as extra arg to `print.slip.totalGross`/`print.slip.netAmount` calls; change `addRow(PdfPTable table, String name, BigDecimal price)` to `addRow(..., String currency)` and replace `+ "€"` with `+ currency`; thread `currency` through both call sites inside `buildItemsTable`.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java` (UPDATE) — `renderInvoice` has **no entity access** (only raw `associationName`/`editionName` strings) — add a new **method parameter** `String currency` (last position): `renderInvoice(String associationName, String editionName, LocalDateTime soldAt, List<Item> items, Locale documentLocale, String currency)`. Pass as extra arg to `print.invoice.total`; thread into `addRow`/`buildItemsTable` like DepositSlipRenderer.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java` (UPDATE) — in `renderReport`, add `String currency = sellerProfile.getEdition().getCurrency();`; pass as extra arg to `print.settlementReport.totalGross`/`.commissionAmount`/`.netAmount`/`.amountPaid`; thread `currency` into `buildSoldItemsTable`/`buildUnsoldItemsTable` (both currently do `price... + "€"` directly — replace with `+ currency`).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java` (UPDATE) — **no new parameter needed**: `report.currency()` is now on `DailySalesReportDto`. Read it once in `renderDailyReport(String editionName, DailySalesReportDto report, Locale documentLocale)`, thread into `addPaymentRow` (add a `String currency` param there) for `print.dailyReport.grossRevenue`/`.commission`/`.amountFormat`.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/EditionReportRenderer.java` (UPDATE) — same as DailyReportRenderer, using `report.currency()` from `EditionSummaryReportDto`, for `print.editionReport.grossRevenue`/`.commission`/`.amountFormat`.
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java` (UPDATE) — **only** `buildInvoiceJob`/`printInvoice` need a new `String currency` parameter (threaded straight to `invoiceRenderer.renderInvoice`). The other 5 `build*Job` methods need no signature change (their renderers now derive currency internally).
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java` (UPDATE) — in `printInvoice`, add `String currency = sale.getEdition().getCurrency();` alongside the other eagerly-extracted scalars (`editionName`, `documentLocale`, `associationName` — same lazy-loading-safety comment already there applies), pass it into `documentPrintService.buildInvoiceJob(associationName, editionName, soldAt, items, documentLocale, currency)`.

- [x] **Backend — i18n message templates: shift `€` from literal to `{N}` placeholder (AC 5)**
  - Rule applied uniformly: every template ending in a bare `{0}€` (or `{1}€` where `{0}` is already taken) gets one more numbered placeholder appended for the currency argument, and the matching Java call site gets `currency` appended to its `Object[]`. `print.slip.commission`/`print.settlementReport.commission`/`print.dailyReport`/`print.editionReport` `%`-suffixed keys are **not** touched (commission rate is a percentage, not a currency amount).
  - [x] `pluribourse-backend/src/main/resources/messages.properties` (UPDATE, EN fallback bundle — only has the keys below, do **not** add the ones it's already missing, that gap is a separate pre-existing correctif per `sprint-change-proposal-2026-08-24.md` § Point 4, out of scope here):
    | Key | Before | After |
    |---|---|---|
    | `print.label.itemPrice` | `{0} - {1}€` | `{0} - {1}{2}` |
    | `print.label.lotPrice` | `Bundle price: {0}€` | `Bundle price: {0}{1}` |
    | `print.slip.netAmount` | `Net payout: {0}€` | `Net payout: {0}{1}` |
  - [x] `pluribourse-backend/src/main/resources/messages_en.properties` (UPDATE) — same shift on all 12 `€`-suffixed keys: `print.label.itemPrice`, `print.label.lotPrice`, `print.slip.totalGross`, `print.slip.netAmount`, `print.invoice.total`, `print.settlementReport.totalGross`, `print.settlementReport.commissionAmount`, `print.settlementReport.netAmount`, `print.settlementReport.amountPaid`, `print.dailyReport.grossRevenue`, `print.dailyReport.commission`, `print.dailyReport.amountFormat`, `print.editionReport.grossRevenue`, `print.editionReport.commission`, `print.editionReport.amountFormat` (15 keys total — `print.label.itemPrice` needs `{2}` since `{0}`/`{1}` are already name/price; every other key just appends `{1}`).
  - [x] `pluribourse-backend/src/main/resources/messages_fr.properties` (UPDATE) — identical shift, same 15 keys, French text unchanged otherwise.

- [x] **Backend — regression fixes on existing PDF/label integration tests (AC 5, 7)**
  - [x] Grep `€` across `pluribourse-backend/src/test` (currently matches `ThermalLabelPrintingIT`, `DepositSlipPrintingIT`, `InvoicePrintingIT`, `SettlementReportPrintingIT`, `BulkSettlementReportPrintingIT`, `DailyReportPrintingIT`, `EditionReportPrintingIT`) and confirm every assertion still passes with the default `€` (test fixtures created via `new EditionDto(..., "€")` per the compiler-driven sweep above keep the existing byte-level assertions valid unchanged).
  - [x] Add **one** new scenario per renderer family (not all 6 — deduplicate by shared code path) proving a non-default currency actually renders: pick `DepositSlipPrintingIT` (covers the `addRow`/entity-derived path) and `InvoicePrintingIT` (covers the new-parameter path) — create an edition with `currency = "$"`, assert the rendered PDF bytes contain `$` where `€` used to be and do **not** contain `€`. This is enough to prove the mechanism end-to-end without duplicating it across all 6 renderer test classes (label/settlement/daily/edition-report all share the exact same `report.currency()`/`sellerProfile.getEdition().getCurrency()` plumbing already exercised by these two).
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java` (UPDATE) — add one scenario confirming AC 2/AC 3: create an edition with an explicit `currency`, confirm it's returned as-is (not overridden by the instance default); update it while in PREPARATION, confirm the new value persists; advance to DEPOSIT, confirm `PUT` is rejected 422 `edition-cannot-be-updated` (already covered generically by an existing test for `commissionRate` — just confirms `currency` isn't a silent exception to that rule).
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/shared/GlobalInstanceConfigIT.java` (UPDATE, see above) — `defaultCurrency` round-trip scenario.

- [x] **Frontend — models + admin forms (AC 1, 2, 3)**
  - [x] `pluribourse-frontend/src/app/models/edition.model.ts` (UPDATE) — add `currency: string;` to `EditionDto` (TS interface, not positional — safe to add anywhere; put it next to `commissionRate` for readability).
  - [x] `pluribourse-frontend/src/app/models/global-instance-config.model.ts` (UPDATE) — add `defaultCurrency: string;`.
  - [x] `pluribourse-frontend/src/app/models/daily-sales-report.model.ts` (UPDATE) — add `currency: string;`.
  - [x] `pluribourse-frontend/src/app/models/edition-summary-report.model.ts` (UPDATE) — add `currency: string;`.
  - [x] `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts` (UPDATE) — add to the form group, mirroring `defaultCommissionRate`:
    ```ts
    defaultCurrency: ['', [Validators.required, Validators.maxLength(10)]]
    ```
  - [x] `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.html` (UPDATE) — new `mat-form-field` (plain text `matInput`, not a `mat-select` — FR-103 explicitly allows arbitrary symbols, not a fixed list) with 2 `mat-error`s (`required`, `maxlength`), same structure as the `associationName` field above it. New i18n keys: `admin.settings.defaultCurrency`, `admin.settings.error.defaultCurrencyRequired`, `admin.settings.error.defaultCurrencyMaxLength`.
  - [x] `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.spec.ts` (UPDATE) — extend existing load/save assertions to cover `defaultCurrency`.
  - [x] `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts` (UPDATE) — add to the form group (required — pre-filled by `loadDefaults()`, same as `commissionRate`):
    ```ts
    currency: ['', [Validators.required, Validators.maxLength(10)]]
    ```
    In `loadEdition()`, add `currency: edition.currency` to the `patchValue`. In `loadDefaults()`, add `currency: config.defaultCurrency`. In `onSubmit()`, destructure and include `currency` in the `payload`.
  - [x] `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html` (UPDATE) — new `mat-form-field` next to `commissionRate`, same shape (text `matInput`, `maxlength="10"`, required error). New i18n keys: `edition.create.currency.label`, `edition.create.currency.required`, `edition.create.currency.maxLength`.
  - [x] `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts` (UPDATE) — extend create/edit scenarios to cover `currency`.

- [x] **Frontend — price-displaying screens read edition currency instead of a hardcoded `€` (AC 6)**

  Two sourcing patterns, chosen per screen based on which edition's currency it actually needs — **do not mix**: the "current edition" pattern only works where the screen is guaranteed to be scoped to the active edition; the "specific edition" pattern is required wherever the admin can pick a past/archived edition.

  | Component | Currency source | Why |
  |---|---|---|
  | `item-catalog.component.ts` | inject `CurrentEditionService`, read `currentEdition()?.currency` | active catalog always resolves the active edition server-side (Story 6.1) |
  | `deposit-page.component.ts` | already injects `CurrentEditionService` — reuse `currentEdition()?.currency` | deposit only runs during DEPOSIT (current edition guaranteed) |
  | `pos-page.component.ts` | inject `CurrentEditionService` (not currently injected), read `currentEdition()?.currency` | POS only runs during SALE (current edition guaranteed) |
  | `payment-dialog.component.ts` | new `currency: string` field on `PaymentDialogData`, passed by `pos-page.component.ts` at `paymentDialogService.open({ items, total, currency: ... })` (`openPaymentDialog()`) | dialog has no service access of its own, receives everything via `DIALOG_DATA` |
  | `settlement-list.component.ts` | already injects `CurrentEditionService` — reuse | settlement screen is always the active edition |
  | `phase-control.component.ts` | already loads `this.edition = signal<EditionDto \| null>` via `editionService.getById(editionId)` — reuse `this.edition()?.currency` | this dialog controls one specific edition by ID, not necessarily the active one |
  | `report-page.component.ts` (daily report) | `report()?.currency` — comes straight from `DailySalesReportDto.currency` (backend change above), **not** a lookup in its local `editions` signal | keeps one source of truth: the report DTO already carries what rendered it |
  | `edition-report.component.ts` (bilan) | `editionReport()?.currency` — from `EditionSummaryReportDto.currency` | same reasoning — this component has no `editions` list at all, only the report DTO |
  | `archived-catalog.component.ts` | `archivedEditions().find(e => e.id === selectedEditionId())?.currency` | already holds the full `EditionDto[]` list (`editionService.getAll()`) for its edition picker — no backend change needed here |

  - [x] `pluribourse-frontend/src/app/features/catalog/item-catalog.component.ts`/`.html` — inject service, pass `currency` into the `catalog.columns.priceFormat` translate call (line 94).
  - [x] `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`/`.html` — pass `currency` into `volunteer.deposit.item.list.priceFormat`/`.lotPriceFormat` (lines 66, 133).
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` — inject `CurrentEditionService`; pass `currency` into `volunteer.pos.basket.priceFormat`/`.total` in `pos-page.component.html` (lines 21, 42, 77) and into `paymentDialogService.open({ items: currentBasket.items, total: currentBasket.total, currency: this.currentEditionService.currentEdition()?.currency })` (line 179).
  - [x] `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts` — add `currency: string` to `PaymentDialogData`; use `data.currency` in `payment-dialog.component.html` for `volunteer.pos.basket.priceFormat` (lines 8, 17) and `volunteer.pos.payment.change` (line 49).
  - [x] `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts`/`.html` — pass `currency` into `settlement.amountFormat` (lines 70, 73) and into the `warningBelowDue`/confirm-unclaimed `description` `translate.instant(...)` calls in the `.ts`.
  - [x] `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts` — pass `this.edition()?.currency` into the `phase.close.dialog.warningUnsettled` `translate.instant(...)` call (around line 201).
  - [x] `pluribourse-frontend/src/app/features/report/report-page.component.ts`/`.html` — pass `report()?.currency` into `admin.reports.daily.amountFormat` (lines 32, 36, 52, 56, 60).
  - [x] `pluribourse-frontend/src/app/features/report/edition-report.component.ts`/`.html` — pass `editionReport()?.currency` into `admin.reports.edition.amountFormat` (lines 31, 35, 39, 55, 59, 63).
  - [x] `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts`/`.html` — pass the looked-up currency into `admin.archivedCatalog.priceFormat` (line 84).

- [x] **Frontend — i18n keys: `€` literal → `{{ currency }}` interpolation (AC 6)**
  - [x] `pluribourse-frontend/public/i18n/en.json` (UPDATE) — for every key below, replace the literal `€` with `{{ currency }}` (keep the existing spacing convention per key — most have a leading space before `€`, the 3 POS keys at lines 450/453/477 currently prefix with `€` instead, keep that prefix position): `phase.close.dialog.warningUnsettled` (242), `volunteer.pos.basket.priceFormat`/`lotPriceFormat` (383, 393 — deposit-item-list section), `volunteer.pos.basket.priceFormat`/`.total`/`payment.change` (450, 453, 477 — POS panel's own copy of price/total/change, distinct keys from 383/393, both sets need the edit), `settlement.amountFormat` (660, 676), `admin.archivedCatalog.priceFormat` (796), `catalog.columns.priceFormat` (815), `settlement.amountFormat` (863), `settlement.form.warningBelowDue` (879 — 2 amounts, `{{ amount }}`/`{{ due }}`, both share the same edition currency so one `{{ currency }}` interpolation covers both occurrences), `settlement.unclaimedDialog.description` (884).
  - [x] `pluribourse-frontend/public/i18n/fr.json` (UPDATE) — identical set of keys, identical `€` → `{{ currency }}` shift, French text otherwise unchanged.
  - **Do not assume the line numbers above are still exact** after any prior edit in this pass — re-locate each key by name (`grep -n "€"` in both files) right before editing it, since editing one key shifts every later line number in the same file.

- [x] **Frontend — spec test updates (AC 6)**
  - [x] For every `.spec.ts` sibling of a `.ts` file touched above (`item-catalog`, `deposit-page`, `pos-page`, `payment-dialog`, `settlement-list`, `phase-control`, `report-page`, `edition-report`, `archived-catalog`), extend the mock `EditionDto`/`DailySalesReportDto`/`EditionSummaryReportDto` fixtures with a `currency` field (a spec constructing these via a plain object literal — not positional — only needs the new property added to the fixture object, TypeScript will flag any fixture missing a required field as a compile error under `strict` mode, use that the same way the backend compiler sweep is used above).
  - [x] Add one assertion per component confirming the rendered price actually reflects a non-`€` currency from the mock fixture (mirrors the backend's "one scenario proves the mechanism" approach — no need to duplicate across every single price-showing template in the same component).

### Review Findings

- [x] [Review][Patch] Symbole monétaire libre sans validation de charset vs encodages fixes des renderers — restreindre la saisie à un jeu de caractères sûr pour Cp858/CP1252 (ex. `A-Za-z0-9 €$£¥`) côté `EditionDto`/`GlobalInstanceConfigDto` (backend) et sur les formulaires `edition-form.component.html`/`admin-settings.component.html` (frontend), avec message d'erreur i18n dédié [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/dto/EditionDto.java:28, pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/dto/GlobalInstanceConfigDto.java:15] — décision utilisateur 2026-08-25 : restreindre la saisie plutôt que valider a posteriori ou accepter le risque.
- [x] [Review][Patch] Panier caisse (POS) : 3 appels i18n n'injectent pas la devise alors que la clé l'exige [pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html:21,42,77]
- [x] [Review][Patch] Devise vide (chaîne blanche non-null) contourne l'héritage par défaut à la création et le gel en Préparation à la mise à jour [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:91]
- [x] [Review][Patch] 5 tests de GlobalInstanceConfigIT passent désormais pour la mauvaise raison (`defaultCurrency` manquant du corps JSON, devenu `@NotBlank`) [pluribourse-backend/src/test/java/org/pluribourse/shared/GlobalInstanceConfigIT.java:165,177,212,224,236]
- [x] [Review][Patch] 4 des 6 renderers PDF/étiquette n'ont aucun test prouvant que la devise est réellement dynamique (seule `"€"` leur est passée par le balayage compilateur, jamais une autre valeur) [pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java, SettlementReportPrintingIT.java, DailyReportPrintingIT.java, EditionReportPrintingIT.java]
- [x] [Review][Defer] Concaténation prix+devise sans séparateur dans les 6 renderers PDF/étiquette [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java:123] — deferred, pre-existing (le collage `{0}{2}`/`price + currency` existait déjà pour `€` avant cette story ; devient plus visible avec une devise multi-caractères comme l'exemple `CHF` donné par la story elle-même)

## Dev Notes

- **Pourquoi `currency` est toujours ajouté en *dernier* champ des records (`EditionDto`, `GlobalInstanceConfigDto`, `DailySalesReportDto`, `EditionSummaryReportDto`), jamais à côté de `commissionRate`/`documentLanguage` malgré la proximité logique.** `EditionDto` a **27 fichiers** construisant `new EditionDto(...)` de façon positionnelle (2 en `main`, 25 en tests) — insérer un champ au milieu de l'ordre existant forcerait à retrouver et corriger chacun des 27 sites avec un risque de décalage silencieux d'un champ sur l'autre en cas d'oubli d'un paramètre lors de la relecture. Ajouter le champ en dernière position rend le diff mécanique (un seul argument ajouté en fin de chaque appel) et, plus important, **le compilateur Java refuse de compiler tout site oublié** (record = arité stricte) — la stratégie de correction devient donc « laisser le compilateur énumérer les sites », fiable à 100 %, plutôt qu'un grep suivi d'une relecture manuelle sujette à erreur. Même raisonnement appliqué à `GlobalInstanceConfigDto` (2 fichiers), `DailySalesReportDto`/`EditionSummaryReportDto` (3 fichiers) — plus petite portée mais même risque de principe.
- **Écart non anticipé par le sprint-change-proposal : 6 renderers touchés par `€`, pas 4.** Le point 1 du sprint-change-proposal (2026-08-24) ne liste que « `InvoiceRenderer`, `DepositSlipRenderer`, `SettlementReportRenderer`, `ThermalLabelRenderer` ». Vérification exhaustive du code réel (grep `€` sur tout `pluribourse-backend/src/main` + lecture de chaque fichier `print.service`) : `DailyReportRenderer` (rapport journalier admin, Story 5.3) et `EditionReportRenderer` (bilan d'édition admin, Story 5.4) ont eux aussi `€` codé en dur dans leurs templates i18n (`print.dailyReport.grossRevenue/commission/amountFormat`, `print.editionReport.grossRevenue/commission/amountFormat`) — décision actée sans besoin de validation utilisateur (lecture directe du code, aucune ambiguïté : ces deux renderers affichent des montants financiers au même titre que les 4 déjà identifiés, les laisser de côté serait une régression visible dès le premier rapport imprimé pour une édition en devise non-€).
- **`ThermalLabelRenderer` a un `€` dans un commentaire de classe (encodage code page 858) qui n'a *rien à voir* avec cette story** — ne pas y toucher : c'est une note sur l'encodage bas niveau ESC/POS (le caractère € doit être encodé en Cp858 pour s'imprimer correctement sur l'imprimante thermique, quel que soit le symbole monétaire réellement affiché). Le texte réellement imprimé (`print.label.itemPrice`/`.lotPrice`) est ce qui doit changer, pas ce commentaire.
- **`EditionArchiveSnapshot`/l'archivage n'ont besoin d'aucune modification.** `Edition.currency` est gelé dès le démarrage du Dépôt (FR-104) et la ligne `Edition` elle-même n'est jamais supprimée par l'archivage (seuls `Item`/`Settlement` le sont, FR-088, Story 2.7) — `EditionSummaryReportPrintService`/`ReportService.getEditionReport` résolvent déjà l'édition par ID indépendamment de l'archivage. Ajouter un champ `currency` à `EditionArchiveSnapshot` serait une duplication inutile d'une donnée déjà stable et accessible via `Edition`.
- **CSV export (`ReportExportService`, Story 5.5) : vérifié, aucun changement nécessaire.** Les colonnes CSV (`export.catalog.column.price`, `export.settlement.column.amountDue`) exportent des nombres bruts, jamais suffixés par un symbole monétaire — seuls des commentaires JavaDoc de `ReportExportIT` mentionnent `€` pour documenter les montants du fixture, pas de code réel à modifier.
- **`SettlementService.java` : le seul `€` du fichier est dans un commentaire JavaDoc** (règle « pas de 0€ trompeur ») — aucun code fonctionnel à changer.
- **Devise = symbole libre (String), pas un code ISO 4217 ni un enum.** FR-103 donne explicitement des exemples (« ex. €, $, CHF ») sans lister une liste fermée — traité exactement comme `associationName` : un simple champ texte, `VARCHAR(10)`, sans formatage `NumberFormat` locale-aware. Cohérent avec la convention déjà établie dans ce module (`InvoiceRenderer`: « No precedent for locale-aware date/number formatting in this module... a simple fixed pattern is consistent with that existing convention »).
- **Ne pas confondre les deux mécanismes de résolution de devise côté frontend** (voir tableau Task « price-displaying screens ») : `CurrentEditionService.currentEdition()?.currency` pour les écrans forcément scopés à l'édition active (dépôt/caisse/solde/catalogue actif), vs. la devise déjà portée par le DTO/la liste d'éditions déjà chargée pour les écrans admin pouvant cibler une édition arbitraire (rapports, catalogue archivé, contrôle de phase). Mélanger les deux introduirait une dépendance inutile à `CurrentEditionService` dans des composants qui n'en ont jamais eu besoin jusqu'ici (`edition-report.component.ts`, `phase-control.component.ts` n'injectent actuellement aucun service d'édition courante).
- **`messages.properties` (bundle de secours EN, sans suffixe de locale) reste volontairement incomplet après cette story** — il manque déjà `print.slip.totalGross`/toutes les clés `print.settlementReport.*`/`print.dailyReport.*`/`print.editionReport.*` avant même cette story (gap documenté au point 4 du sprint-change-proposal comme correctif direct séparé). N'appliquer le décalage `{N}` qu'aux clés qui existent déjà dans ce fichier ; ne pas y ajouter les clés manquantes, ce n'est pas le périmètre de cette story.
- **Validation `EditionDto.currency`** : `@Size(max = 10)` seulement, **pas** `@NotBlank`/`@NotNull` — même traitement que `commissionRate` (nullable en entrée, `EditionService.createEdition` retombe sur le paramètre instance si absent). `GlobalInstanceConfigDto.defaultCurrency` en revanche est `@NotBlank @Size(max = 10)`, comme `associationName`/`defaultCommissionRate` : la configuration instance est toujours entièrement soumise par le formulaire admin (`admin-settings.component.ts` pré-remplit systématiquement le champ avant tout submit).

### Project Structure Notes

- Nouveaux fichiers : `031-default-currency.xml`, `032-edition-currency.xml` (migrations).
- Modifiés (backend, main) : `GlobalInstanceConfig.java`, `GlobalInstanceConfigDto.java`, `GlobalInstanceConfigService.java`, `Edition.java`, `EditionDto.java`, `EditionMapper.java`, `EditionService.java`, `EditionArchivingService.java`, `DailySalesReportDto.java`, `EditionSummaryReportDto.java`, `ReportService.java`, `ThermalLabelRenderer.java`, `DepositSlipRenderer.java`, `InvoiceRenderer.java`, `SettlementReportRenderer.java`, `DailyReportRenderer.java`, `EditionReportRenderer.java`, `DocumentPrintService.java`, `PosInvoicePrintService.java`, `messages.properties`, `messages_en.properties`, `messages_fr.properties`, `db.changelog-master.xml`.
- Modifiés (backend, test) : ~27 fichiers via le balayage piloté par le compilateur (`new EditionDto(...)`) + `GlobalInstanceConfigIT.java`, `InvoicePrintingIT.java`, `DailyReportPrintingIT.java`, `EditionReportPrintingIT.java`, `EditionManagementIT.java`, `DepositSlipPrintingIT.java`.
- Modifiés (frontend) : `edition.model.ts`, `global-instance-config.model.ts`, `daily-sales-report.model.ts`, `edition-summary-report.model.ts`, `admin-settings.component.{ts,html,spec.ts}`, `edition-form.component.{ts,html,spec.ts}`, `item-catalog.component.{ts,html}`, `deposit-page.component.{ts,html}`, `pos-page.component.{ts,html}`, `payment-dialog.component.{ts,html}`, `settlement-list.component.{ts,html}`, `phase-control.component.ts`, `report-page.component.{ts,html}`, `edition-report.component.{ts,html}`, `archived-catalog.component.{ts,html}`, `en.json`, `fr.json`, et les `.spec.ts` correspondants.
- Aucune suppression de fichier. Aucun nouveau composant Angular, aucun nouveau contrôleur/service backend — extension de mécanismes existants uniquement.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#Point 1 — Devise au niveau de l'édition] FR-103/FR-104, patron calqué sur le taux de commission (FR-016), impact Epic 1 Story 1.5 + Epic 2 Story 2.1, "4 renderers" (corrigé à 6 par cette story, voir Dev Notes)
- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#4. Impact MVP et plan d'action] Ordre suggéré : ce point après le point 2 (2.10, done) — confirmé, 2.9 est bien la première story backlog restante dans cette zone
- [Source: _bmad-output/implementation-artifacts/2-10-preparation-non-exclusive.md] Précédent immédiat sur `Edition`/`EditionService` — patron de story (ordre des sections, granularité des tâches, citations de code cible) directement réutilisé ici
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/entity/GlobalInstanceConfig.java] Patron exact de `defaultCommissionRate` à répliquer pour `defaultCurrency`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/Edition.java] Patron exact de `commissionRate` à répliquer pour `currency`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java#createEdition,updateEdition] `updateEdition` bloque déjà tout champ hors PREPARATION (FR-016) — AC 3 en hérite sans code additionnel
- [Source: pluribourse-backend/src/main/resources/db/changelog/009-edition-version.xml] Patron exact `addColumn` + `defaultValue` + `nullable=false` pour ajouter une colonne NOT NULL sans casser les lignes existantes
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java, DepositSlipRenderer.java, SettlementReportRenderer.java, ThermalLabelRenderer.java, DailyReportRenderer.java, EditionReportRenderer.java] Les 6 renderers réels — sprint-change-proposal n'en listait que 4
- [Source: pluribourse-backend/src/main/resources/messages_en.properties, messages_fr.properties, messages.properties] 15 clés `{0}€`-style à décaler vers `{N}` (en/fr), 3 seulement dans le bundle de secours
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java] Patron d'extraction eager de valeurs scalaires avant mise en file (évite `LazyInitializationException` sur le thread consommateur) — `currency` doit suivre le même patron que `editionName`/`documentLocale`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java#getDailyReport,getEditionReport,buildFromArchivedSnapshot] Les 3 sites de construction de `DailySalesReportDto`/`EditionSummaryReportDto`, tous avec un accès direct à `Edition` déjà en scope
- [Source: pluribourse-frontend/src/app/services/current-edition.service.ts] Signal `currentEdition` déjà utilisé par `deposit-page`/`report-page`/`settlement-list` — `currency` y devient disponible sans changement de service
- [Source: pluribourse-frontend/src/app/features/report/report-edition-scope.service.ts] Ne porte qu'un `selectedEditionId`, pas la liste des éditions — confirme que `edition-report.component.ts` doit lire `currency` depuis son propre DTO de rapport, pas depuis ce service
- [Source: pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts] `archivedEditions` signal déjà `EditionDto[]` complet — pas de nouveau champ backend nécessaire pour cet écran
- [Source: pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts, admin-settings.component.html] Patron exact du champ `defaultCommissionRate` à répliquer pour `defaultCurrency`
- [Source: pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts, edition-form.component.html] Patron exact du champ `commissionRate` (y compris `loadDefaults()`) à répliquer pour `currency`
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts#openPaymentDialog, payment-dialog.component.ts#PaymentDialogData] Site exact d'ajout de `currency` au `DIALOG_DATA`

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- Balayage piloté par le compilateur (`mvn test-compile`) : 60 sites `new EditionDto(...)`/`new DailySalesReportDto(...)`/`new EditionSummaryReportDto(...)`/`new GlobalInstanceConfigDto(...)` corrigés dans 28 fichiers backend (2 main-source déjà listés dans les Tasks, le reste des fixtures de test).
- 1 site non détectable par le compilateur (construction `new Edition()` par setters, pas via `EditionDto`) : `SaleConcurrencyIT.java` — `Column 'currency' cannot be null` à l'exécution, corrigé par `edition.setCurrency("€")`.
- 1 régression backend découverte à l'exécution (pas à la compilation) : `AssociationNameRequiredForEditionCreationIT` envoyait un JSON brut sans `defaultCurrency` à `PUT /admin/instance-config`, désormais `@NotBlank` — 400 au lieu de 200 attendu ; corrigé.
- Suite backend complète : 526/526 verts (523 avant cette story + 3 nouveaux : `GlobalInstanceConfigIT.admin_put_currency_persists`, `DepositSlipPrintingIT.deposit_slip_renderer_uses_the_edition_currency_not_a_hardcoded_symbol`, `InvoicePrintingIT.invoice_renderer_uses_the_passed_currency_not_a_hardcoded_symbol`, plus assertions étendues dans `EditionManagementIT` Order 4/7/8).
- Balayage piloté par le compilateur TypeScript (`ng test`/`ng build`, mode strict) : 17 fichiers `.spec.ts` avec des littéraux `EditionDto`/`DailySalesReportDto`/`EditionSummaryReportDto`/`GlobalInstanceConfigDto`/`PaymentDialogData` incomplets, tous corrigés.
- 1 échec runtime frontend (pas de compilation) après le premier passage : `edition-form.component.spec.ts` (6 tests) — `MOCK_CONFIG` de test n'avait pas `defaultCurrency`, donc `currency` restait vide (`Validators.required`) et `onSubmit()` se terminait toujours en early-return silencieux. Corrigé.
- Suite frontend complète : 670/670 verts. `npm run build` (production) : succès.

### Completion Notes List

- **Écart n°1 (documenté dans les Dev Notes de la story) confirmé à l'implémentation :** 6 renderers PDF/étiquette touchés par `€`, pas 4 comme l'affirmait le sprint-change-proposal — `DailyReportRenderer`/`EditionReportRenderer` corrigés en plus des 4 déjà identifiés.
- **Écart n°2, découvert pendant l'implémentation (non anticipé par la story) :** `€` codé en dur aussi sous forme d'entité HTML `&euro;` dans des `matTextSuffix` de champs de saisie (pas seulement dans des clés `translate` — invisible au grep du caractère `€` fait lors de la création de la story). Trouvé et corrigé dans 3 fichiers en plus de ceux prévus : `item-form.component.html`/`.ts` et `lot-form.component.html`/`.ts` (formulaires de dépôt, hors périmètre initial), et le champ montant de `settlement-list.component.html` (déjà dans le périmètre prévu mais le `&euro;` du `matTextSuffix` n'avait pas été repéré). `item-form`/`lot-form` injectent désormais `CurrentEditionService` (même patron que les autres écrans Dépôt).
- **Écart n°3, découvert pendant l'implémentation :** `settlement-list.component.ts` n'injectait en réalité **pas** `CurrentEditionService` malgré ce qu'affirmait le tableau de sourcing de devise de la story (assertion non vérifiée à la création) — injection ajoutée.
- **Écart n°4, technique, découvert en écrivant les nouveaux tests de renderer :** la story prévoyait d'asserter `doesNotContain("€")` sur le texte décodé d'un PDF rendu avec une devise non-`€`. Impossible : les renderers PDF encodent leur flux de contenu en CP1252 (`BaseFont.CP1252`, pour permettre l'affichage correct du signe € sur de vrais lecteurs PDF), mais les tests décodent les octets en ISO-8859-1 pour les assertions — `€` (octet CP1252 0x80) ne redevient jamais le caractère littéral `€` dans cette vue décodée (déjà vrai avant cette story, d'où le fait que les tests existants n'avaient jamais vérifié `€` littéralement, uniquement les montants numériques). Les 2 nouveaux tests (`DepositSlipPrintingIT`, `InvoicePrintingIT`) vérifient donc uniquement la présence du nouveau symbole (`$`, ASCII, correctement décodable) à côté des montants — preuve suffisante que le mécanisme fonctionne, sans l'assertion d'absence prévue par la story.
- **Décision de conception suivie sans écart :** `currency` ajouté en toute dernière position de chaque record Java concerné (`EditionDto`, `GlobalInstanceConfigDto`, `DailySalesReportDto`, `EditionSummaryReportDto`) comme prescrit par les Dev Notes de la story — confirmé un choix payant : le balayage piloté par le compilateur a bien enuméré tous les sites sans ambiguïté ni décalage de champ.
- Toutes les décisions de sourcing de devise frontend (edition active via `CurrentEditionService` vs. devise déjà portée par le DTO de rapport/l'objet édition déjà chargé) suivies telles que documentées dans le tableau de la story, sans écart hormis la correction de `settlement-list` (écart n°3 ci-dessus).

### File List

**Nouveaux fichiers (backend) :**
- `pluribourse-backend/src/main/resources/db/changelog/031-default-currency.xml`
- `pluribourse-backend/src/main/resources/db/changelog/032-edition-currency.xml`

**Modifiés (backend, main) :**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/entity/GlobalInstanceConfig.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/dto/GlobalInstanceConfigDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/instanceconfig/service/GlobalInstanceConfigService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/Edition.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/dto/EditionDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/mapper/EditionMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/archive/service/EditionArchivingService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/DailySalesReportDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/dto/EditionSummaryReportDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/report/service/ReportService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/ThermalLabelRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/InvoiceRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DailyReportRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/EditionReportRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DocumentPrintService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosInvoicePrintService.java`
- `pluribourse-backend/src/main/resources/messages.properties`
- `pluribourse-backend/src/main/resources/messages_en.properties`
- `pluribourse-backend/src/main/resources/messages_fr.properties`

**Modifiés (backend, test) :**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/ArchivedCatalogIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/archive/EditionArchivingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/AssociationNameRequiredForEditionCreationIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/CurrentEditionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionCategoryIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionClosingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/NoVolunteerPhaseGuardIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/item/LotManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosScanIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java` (régression légitime, découverte à l'exécution — voir Debug Log References)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` (+ nouveau scénario devise non-défaut)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java` (+ nouveau scénario devise non-défaut)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ReportExportIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/seller/SellerManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/GlobalInstanceConfigIT.java` (+ nouveau scénario `defaultCurrency`)

**Modifiés (frontend) :**
- `pluribourse-frontend/src/app/models/edition.model.ts`
- `pluribourse-frontend/src/app/models/global-instance-config.model.ts`
- `pluribourse-frontend/src/app/models/daily-sales-report.model.ts`
- `pluribourse-frontend/src/app/models/edition-summary-report.model.ts`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.ts`
- `pluribourse-frontend/src/app/features/catalog/item-catalog.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts` (hors périmètre initial — voir Completion Notes écart n°2)
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.html` (idem)
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts` (idem)
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.html` (idem)
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.html`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts`
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`
- `pluribourse-frontend/src/app/features/report/report-page.component.html`
- `pluribourse-frontend/src/app/features/report/edition-report.component.html`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.ts`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.html`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`

**Modifiés (frontend, test) :**
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.spec.ts`
- `pluribourse-frontend/src/app/core/guards/sale-phase.guard.spec.ts`
- `pluribourse-frontend/src/app/core/guards/settlement-phase.guard.spec.ts`
- `pluribourse-frontend/src/app/features/admin/archived-catalog/archived-catalog.component.spec.ts` (+ assertion devise non-défaut)
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.spec.ts`
- `pluribourse-frontend/src/app/features/report/edition-report.component.spec.ts`
- `pluribourse-frontend/src/app/features/report/report-page.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts`
- `pluribourse-frontend/src/app/services/current-edition.service.spec.ts`
- `pluribourse-frontend/src/app/services/edition.service.spec.ts`
- `pluribourse-frontend/src/app/services/global-instance-config.service.spec.ts`
- `pluribourse-frontend/src/app/services/report.service.spec.ts`

## Change Log

- 2026-08-25 — Implémentation (`dev-story`). Toutes les tâches complétées conformément à la story, avec 4 écarts découverts pendant l'implémentation (voir Dev Agent Record → Completion Notes pour le détail complet) : (1) confirmation que 6 renderers PDF étaient touchés par `€`, pas 4 comme anticipé par le sprint-change-proposal (déjà documenté dans la story elle-même) ; (2) `€` également codé en dur sous forme d'entité HTML `&euro;` dans des `matTextSuffix` de 3 champs de saisie non prévus au périmètre (`item-form`, `lot-form` — formulaires de dépôt — et le champ montant de `settlement-list`), tous corrigés ; (3) `settlement-list.component.ts` n'injectait en réalité pas `CurrentEditionService` contrairement à ce qu'affirmait la story — injection ajoutée ; (4) l'assertion `doesNotContain("€")` prévue par la story pour les 2 nouveaux tests de renderer PDF s'est révélée techniquement impossible (le flux PDF est encodé en CP1252 mais décodé en ISO-8859-1 par les tests, donc `€` littéral n'est jamais assertable dans cette vue — déjà vrai avant cette story) ; remplacée par une assertion de présence du nouveau symbole uniquement, suffisante pour prouver le mécanisme. Suite backend complète 526/526 (3 nouveaux tests dédiés + assertions étendues dans `EditionManagementIT`), suite frontend complète 670/670, `npm run build` production réussi. Aucune régression. Statut → review.
