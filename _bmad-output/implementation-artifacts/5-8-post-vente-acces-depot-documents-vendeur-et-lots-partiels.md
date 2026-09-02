---
baseline_commit: 6d243474c9d9189c2feb0c201c70c4f1d7745673
---

# Story 5.8 : Post-vente — accès Dépôt, documents vendeur & lots partiels

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que bénévole (ou admin) travaillant la phase Post-vente,
je veux que la fiche Dépôt disparaisse de la navigation Post-vente, que le formulaire de solde propose l'impression automatique du bilan, qu'un lot déjà entamé ne puisse plus être re-vendu au prix global, et que le bordereau de dépôt comme le bilan de vente détaillent chaque article membre d'un lot,
afin qu'aucun clic ne mène à une erreur 422, qu'aucun lot ne soit encaissé plusieurs fois, et que chaque vendeur sache exactement quels articles physiques récupérer.

## Contexte & origine

Issue de la **SCP 2026-09-02b** (`_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-02b.md`, approuvée par Manerial le 2026-09-02). Déclencheur : test de la phase Post-vente le 2026-09-02 — six constats (T1–T6, dont **T3 = bug de fond**) sur des epics tous à `done`. Même patron que la SCP 2026-08-24 : **story unique multi-parties A→E**, ajustement direct, pas de rollback, MVP intact.

**Ce qui est déjà fait (ne pas refaire) :**

- Les amendements d'artefacts **P1–P13 sont déjà appliqués** et vérifiés dans `prd.md` (FR-031, FR-047, FR-050, FR-095 amendés ; **FR-109 créé**), `epics.md` (lignes miroir + carte de couverture + UX-DR22 ligne 206 + ACs des Stories 3.6/4.3/5.1/5.2) et `architecture.md` (§ Concurrence — POS lignes 234-239 : ligne « Intégrité des lots » + complément exigence de test). Cette story **ne touche aucun artefact de planification**.
- **T2** (clé i18n `volunteer.deposit.error` dupliquée dans `fr.json`/`en.json`) : **déjà corrigé hors story** (deux blocs fusionnés en un seul de 6 clés, scan de doublons OK). Seule action restante : vérification visuelle (recherche vendeur en échec → message traduit).
- **FR-105 à FR-108** (SCP 2026-08-24) jamais backportés dans le PRD/epics : **hors périmètre**, signalé pour traitement séparé.

**Portée technique :** code + tests + i18n uniquement. **Aucune** migration Liquibase, **aucun** nouvel endpoint, **aucune** nouvelle route/composant Angular, **aucune** nouvelle dépendance. `EXPERIENCE.md` volontairement non amendé (dérive documentaire connue, même convention que 2.7/2.9/3.14/4.7).

**Statut des stories amendées (toutes `done`, livrées par cette story, jamais rouvertes) :** 3.6 → partie D+A · 4.3 → partie C · 5.1 → partie B · 5.2 → partie E. La partie A ne rouvre aucune story (retrait de navigation + resserrement de gardes).

## Découpage en 5 parties

| Partie | Déclencheur | Objet | Couches |
|---|---|---|---|
| **A** | T1 | Retirer l'accès à la fiche Dépôt (`/volunteer/deposit`) en phase Post-vente (entrée sidebar, garde de route, garde serveur). Conséquence assumée : plus de réimpression d'étiquettes ni de bordereau en Post-vente — pas de remplacement. | Front (nav + guard) + Back (`PhaseGuard`) |
| **B** | T5 | Formulaire de solde : case « Imprimer le bilan de vente » cochée par défaut → impression best-effort à la confirmation. Bouton « Imprimer le bilan » par ligne masqué tant que `status == UNSETTLED`. | Front seul |
| **C** | T3 | **FR-109** : un lot ne se vend qu'une fois. Garde 409 `lot-already-sold` au scan **et** à la validation du panier (course multi-postes). Prix global encaissé une seule fois. Articles restants rendus au vendeur. | Back (POS + exception + repo + concurrence) + Front (`handleScanError`/`handleValidationError`) |
| **D** | T6 | `DepositSlipRenderer` : nouveau tableau « détail des lots » (Lot · Catégorie du lot · Article), une ligne par membre, sans prix, rendu seulement s'il existe ≥1 lot. | Back (renderer + i18n `.properties`) |
| **E** | T4 | `SettlementReportRenderer` restructuré : tableau unifié des articles avec colonne Statut + tableau éclaté des membres de lots + ligne de comptage (`vendus + invendus = déposés`, sur `Item.isSold()` réel). Montants inchangés. | Back (renderer + i18n `.properties`) |

## Acceptance Criteria

> Les blocs Given/When/Then reprennent les ACs déjà amendés dans `epics.md` (Stories 3.6 L1231-1253, 4.3 L1553-1588, 5.1 L1669-1711, 5.2 L1713-1736) et les points figés de la SCP §5 (L202-217).

### Partie A — Retrait de l'accès Dépôt en Post-vente

**AC-A1 — Navigation bénévole**
**Étant donné** une édition active en phase Post-vente
**Quand** un bénévole affiche l'application
**Alors** l'entrée de navigation « Dépôt » (`/volunteer/deposit`) n'apparaît **pas** dans le rail bénévole (elle n'apparaît qu'en phase Dépôt)
**Et** l'entrée « Reversements » (`/volunteer/settlement`) reste la seule entrée de la phase Post-vente

**AC-A2 — Garde de route (client)**
**Étant donné** une édition active en phase Post-vente (ou Vente, Préparation, Clôturée)
**Quand** un bénévole tente d'atteindre `/volunteer/deposit` par URL directe ou favori
**Alors** `depositPhaseGuard` refuse l'activation (redirection `createUrlTree(['/404'])`) — seule la phase Dépôt autorise la route
**Et** le passage de phase Dépôt → Post-vente redirige un bénévole déjà sur `/volunteer/deposit` vers `/volunteer/settlement` (comportement `AppLayoutComponent` existant, **inchangé** — `/volunteer/deposit` reste dans `PHASE_BOUND_VOLUNTEER_PATHS`)

**AC-A3 — Garde serveur**
**Étant donné** une édition active en phase Post-vente
**Quand** un appel atteint `POST /api/sellers/{id}/deposit/slip/reprint` **ou** `POST /api/sellers/{id}/deposit/labels/reprint`
**Alors** la réponse est `422` avec `type` se terminant par `/deposit-reprint-not-allowed` — les **deux** réimpressions (bordereau ET étiquettes) exigent désormais la phase Dépôt
**Et** en phase Dépôt, les deux réimpressions fonctionnent exactement comme avant (aucune régression)

**AC-A4 — Aucun retrait fonctionnel en phase Dépôt**
**Étant donné** une édition active en phase Dépôt
**Alors** la fiche vendeur `/volunteer/deposit` et ses deux boutons de réimpression (bordereau, étiquettes) sont **inchangés**
**Et** aucune clé i18n n'est supprimée (les clés `volunteer.deposit.*reprintLabels*` / `*reprintSlip*` restent utilisées en phase Dépôt)

### Partie B — Case « Imprimer le bilan » au solde + visibilité du bouton par statut

**AC-B1 — Case dans le formulaire de solde**
**Étant donné** que le bénévole ouvre le formulaire de solde inline d'un vendeur
**Alors** une case « Imprimer le bilan de vente » y est présente, **cochée par défaut**
**Et** elle est réinitialisée à cochée à chaque ouverture (fermeture puis réouverture pour un autre vendeur)

**AC-B2 — Impression best-effort à la confirmation**
**Étant donné** que la case est cochée
**Quand** le bénévole confirme le solde et que le `settle` réussit
**Alors** le bilan de vente est mis en file d'impression A4 (`POST /api/settlements/{sellerId}/report/print`) de façon **best-effort et découplée** : l'appel n'est pas attendu avant la fin du flux de solde, et un échec d'impression **n'annule pas le solde** (pas de rollback, pas de bannière bloquante — un toast d'erreur d'impression au plus)
**Et** si la case est décochée, aucune impression n'est déclenchée
**Et** si le `settle` échoue (409 `seller-already-settled`, 422, …), aucune impression n'est déclenchée

**AC-B3 — Visibilité du bouton « Imprimer le bilan » par ligne**
**Étant donné** la liste de solde
**Alors** le bouton « Imprimer le bilan » d'une ligne n'est visible que si `settlement.status !== 'UNSETTLED'` (donc pour `SETTLED` **et** `UNCLAIMED`)
**Et** il est masqué pour les vendeurs non soldés (`UNSETTLED`)
**Et** le retour visuel de ré-impression manuelle (spinner pendant la mise en file, toast succès/erreur) est conservé

### Partie C — FR-109 : un lot ne se vend qu'une fois

**AC-C1 — Rejet au scan**
**Étant donné** qu'au moins un article d'un lot est marqué `sold` (vente committée)
**Quand** un caissier scanne (`GET /api/pos/scan`) un autre article du même lot — directement ou via `POST /api/pos/baskets/{id}/items`
**Alors** la réponse est `409` avec `type` se terminant par `/lot-already-sold`
**Et** l'article n'est pas ajouté au panier

**AC-C2 — Rejet à la validation**
**Étant donné** qu'un panier contient un article d'un lot dont un autre membre a été vendu entre-temps sur un autre poste
**Quand** le caissier valide le panier (`POST /api/pos/baskets/{id}/validate`)
**Alors** la validation est rejetée avec `409` `type` se terminant par `/lot-already-sold`
**Et** aucune vente n'est créée, aucun article du panier n'est marqué `sold`

**AC-C3 — Course multi-postes réelle**
**Étant donné** deux postes dont les paniers contiennent chacun un membre **différent** du même lot (aucun membre `sold` visible au pré-check des deux côtés)
**Quand** les deux postes valident quasi simultanément
**Alors** exactement une validation réussit et le prix global du lot est encaissé **une seule fois**
**Et** l'autre reçoit un `409 /lot-already-sold` — jamais un 500, jamais deux ventes portant le même lot
**Et** les articles non vendus du lot restent `sold = false`, `sale = null` (ils reviennent au vendeur, FR-109)

**AC-C4 — Front : message spécifique**
**Étant donné** un `409 /lot-already-sold` au scan **ou** à la validation
**Quand** l'erreur est traitée par `pos-page.component`
**Alors** une notification inline `variant: 'error'` affiche `volunteer.pos.error.lotAlreadySold`
**Et** le panier n'est pas modifié automatiquement (résolution manuelle — architecture § Concurrence POS : « Pas de réessai automatique »)

**AC-C5 — Prix global inchangé**
**Étant donné** un lot partiellement vendu (N-1 membres restants invendus)
**Alors** le montant encaissé pour ce lot reste son **prix global entier**, compté une seule fois (`ItemPricing.computeTotal` inchangé — pas de prorata, point figé SCP)

### Partie D — Bordereau de dépôt : tableau « détail des lots »

**AC-D1 — Nouveau tableau**
**Étant donné** un bordereau de dépôt PDF généré pour un vendeur possédant au moins un lot
**Quand** le PDF est rendu (`DepositSlipRenderer`)
**Alors** il contient, **en plus** du tableau des articles existant (lot = 1 ligne, nom + prix global — inchangé), un tableau « détail des lots » à **3 colonnes : nom du lot · catégorie du lot · nom de l'article**
**Et** ce tableau liste **chaque article membre de chaque lot**, une ligne par membre (pas de déduplication)
**Et** il ne comporte **aucune colonne prix** (les membres n'ont pas de prix individuel)

**AC-D2 — Section conditionnelle**
**Étant donné** un vendeur sans aucun lot (uniquement des articles individuels)
**Alors** le tableau « détail des lots » (titre + table) n'est **pas** rendu

**AC-D3 — Catégorie du lot**
**Étant donné** un article membre d'un lot
**Alors** la colonne « catégorie du lot » affiche la catégorie **propre du lot** (`Lot.category`, Story 3.14), obtenue via `item.getCategory().getName()` (recopiée depuis `Lot.category` à l'écriture depuis la Story 3.14 ; `i.category` est déjà `JOIN FETCH` dans `findAllBySellerProfileIdOrderByItemNumberAsc`) — **jamais** via `item.getLot().getCategory()` (LAZY, non fetch-join, rendu hors transaction → `LazyInitializationException`)

**AC-D4 — i18n**
**Alors** les 4 libellés du nouveau tableau (titre de section + 3 en-têtes) proviennent de `messages_fr.properties` / `messages_en.properties` (nouvelles clés `print.slip.*`), avec la même clé ajoutée aussi à `messages.properties` (défaut)

### Partie E — Bilan de vente restructuré

**AC-E1 — Tableau unifié des articles**
**Étant donné** un bilan de vente PDF (`SettlementReportRenderer`)
**Quand** le PDF est rendu
**Alors** les deux sections « Articles vendus » / « Articles invendus » sont remplacées par **un seul tableau unifié** à 5 colonnes : nom · catégorie · table · prix · **statut (vendu/invendu)**
**Et** un lot y apparaît sur **une seule ligne** (nom du lot, catégorie du lot, numéro de table d'un membre — tous les membres d'un lot partagent la même table depuis Story 3.14 —, prix global, statut « vendu » si ≥1 membre du lot est `sold`, sinon « invendu »), prix global compté **une seule fois**
**Et** un article individuel apparaît sur une ligne avec ses propres nom/catégorie/table/prix et son statut `isSold()`

**AC-E2 — Tableau éclaté des membres de lots**
**Étant donné** un bilan pour un vendeur possédant au moins un lot
**Alors** le PDF contient un tableau « détail des lots » à 5 colonnes : nom du lot · nom de l'article · catégorie du lot · table · **statut réel** (`item.isSold()` membre par membre)
**Et** il n'est pas rendu s'il n'existe aucun lot

**AC-E3 — Ligne de comptage**
**Alors** une ligne indique : **articles vendus / invendus / déposés**, où
- 1 article physique = 1 unité (lignes `Item`, **sans** `distinctByLot`)
- vendus = nombre d'`Item` avec `isSold() == true`
- invendus = nombre d'`Item` avec `isSold() == false`
- déposés = vendus + invendus (= total des `Item` du vendeur)
**Et** ce comptage repose sur `Item.isSold()` **réel**, **pas** sur la normalisation « lot avec ≥1 membre vendu = vendu en entier »

**AC-E4 — Montants inchangés + Montant remis conditionnel**
**Alors** total brut, commission et reversement net restent calculés par `ItemPricing.computeTotal(soldItems)` sur la liste **normalisée** (`soldItems` = tous les membres d'un lot dont ≥1 membre est vendu) — le prix global d'un lot partiellement vendu compté une seule fois, exactement comme aujourd'hui
**Et** la ligne « Montant remis » n'est affichée **que si le vendeur a été soldé** (`amountPaid != null`, càd `Settlement` en statut `SETTLED` — `getAmountPaid()` inchangé)

**AC-E5 — i18n**
**Alors** tous les nouveaux libellés (titre du tableau unifié, en-tête « statut », valeurs « vendu »/« invendu », titre du tableau éclaté, en-têtes de ses colonnes, ligne de comptage) proviennent de `messages_{fr,en}.properties` (nouvelles clés `print.settlementReport.*`) ; les clés `print.settlementReport.soldSection` / `.unsoldSection` désormais inutilisées sont supprimées

**AC-E6 — Impression groupée admin (Story 5.6)**
**Étant donné** l'impression groupée `POST /api/admin/settlements/report/print-all`
**Alors** chaque bilan produit respecte la nouvelle structure (même renderer) — `BulkSettlementReportPrintingIT` reste vert (assertions ajustées si besoin)

### AC-Z — Non-régression & vérifications transverses

- `./mvnw clean package` (backend) : suite verte. `SaleConcurrencyIT` **skippé proprement** si Docker absent (ne pas compter comme échec, cf. `@Testcontainers(disabledWithoutDocker = true)`).
- `npm test` dans `pluribourse-frontend/` : suite verte. `npm run build` : aucune erreur **ni warning**.
- Aucune migration Liquibase, aucun nouvel endpoint, aucune nouvelle route/composant Angular, aucune nouvelle dépendance.
- `fr.json` ⇔ `en.json` structurellement identiques ; pour **chaque clé `print.slip.*` / `print.settlementReport.*` ajoutée par cette story**, présence dans les **3** fichiers `messages.properties` + `messages_fr.properties` + `messages_en.properties` (défaut = valeur EN). Les clés `.properties` pré-existantes manquantes du défaut ne sont **pas** rattrapées (hors périmètre).
- Aucune donnée personnelle vendeur (nom, email, téléphone) dans les logs ; `LotAlreadySoldException` ne porte qu'un `lotId`.
- Base de dev locale non touchée ; vérification visuelle laissée à Manerial.
- `BigDecimal` pour tout montant ; type explicite backend, **jamais** `var`, accolades sur tout `if`/`for` ; front standalone + Signals + template HTML séparé + textes via ngx-translate.

## Tasks / Subtasks

- [x] **T-A — Partie A : retrait de l'accès Dépôt en Post-vente** (AC : A1–A4)
  - [x] Front `layout/app-layout/app-layout.component.html` (~l.208) : condition de l'entrée `/volunteer/deposit` `currentEdition()?.phase === 'DEPOSIT' || currentEdition()?.phase === 'POST_SALE'` → `currentEdition()?.phase === 'DEPOSIT'` seul. Ne pas toucher `/volunteer/pos|sales` (SALE), `/volunteer/catalog` (toujours visible), `/volunteer/settlement` (POST_SALE), ni le bloc admin `/admin/settlement`.
  - [x] Front `core/guards/deposit-phase.guard.ts` (~l.19) : retirer `|| phase === ActivePhase.POST_SALE` → `if (phase === ActivePhase.DEPOSIT) { return true; }`. Mettre à jour le commentaire l.7-10 (retirer « extended by story 3.6 to also allow Post-vente for deposit slip reprinting »).
  - [x] Front `models/active-phase.enum.ts` (l.20-25) : corriger le commentaire trompeur (`/volunteer/deposit` n'est plus accessible en Post-vente ; le « explicit link back to /volunteer/deposit » évoqué n'existe nulle part). **Ne pas** toucher `resolveVolunteerLandingPath` (POST_SALE → `/volunteer/settlement` déjà correct).
  - [x] Front : **NE PAS** retirer `/volunteer/deposit` de `PHASE_BOUND_VOLUNTEER_PATHS` (app-layout.component.ts l.21) — la redirection de phase doit continuer de bouncer un bénévole hors de cette page au passage en Post-vente. *(non touché)*
  - [x] Front : **NE PAS** modifier `deposit-page.component` ni ses clés i18n — la fiche Dépôt et ses 2 boutons de réimpression restent inchangés en phase Dépôt ; ils deviennent inatteignables en Post-vente via la garde de route. *(seul un commentaire obsolète citant l'ancien nom de méthode `requireDepositPhaseForSlipReprint` a été rafraîchi — aucun code/i18n/comportement modifié)*
  - [x] Back `domain/item/service/DepositValidationService.java` `resolveSellerDeposit()` (l.78-79) : remplacer `PhaseGuard.requireDepositOrPostSalePhase(edition)` par une garde **Dépôt seul conservant le slug `deposit-reprint-not-allowed` (422)**. Recommandé : renommer `PhaseGuard.requireDepositPhaseForSlipReprint` en `requireDepositPhaseForReprint` (corps inchangé : `phase != DEPOSIT → DepositReprintNotAllowedException`) et l'appeler ici ; retirer l'appel désormais redondant l.64 dans `reprintDepositSlip()` ; supprimer `PhaseGuard.requireDepositOrPostSalePhase` (plus aucun appelant). Mettre à jour le Javadoc de classe `DepositValidationService` (l.24-31) et de `PhaseGuard` (l.24-45).
  - [x] Back : **NE PAS** toucher `PhaseGuard.requireDepositPhase` (slug `item-modification-locked`, utilisé par `ItemService`/`LotService`/`SellerService`) ni les autres gardes. *(non touché)*
  - [x] Tests front `layout/app-layout/app-layout.component.spec.ts` : supprimer/inverser « shows the Deposit link in the Post-vente phase » (l.362-366) ; renommer « hides the Deposit link outside Deposit/Post-vente » (l.368-372) → « … outside Deposit » ; « redirects away from /volunteer/deposit … to /volunteer/settlement » (l.475-487) reste vert.
  - [x] Tests front `core/guards/deposit-phase.guard.spec.ts` : inverser « allows activation … Post-vente phase » (l.51-54) → « redirects to /404 … Post-vente » ; conserver `DEPOSIT → true`.
  - [x] Tests back `domain/print/ThermalLabelPrintingIT.java` : nouveaux `@Order(19)` (avance SALE→POST_SALE) + `@Order(20)` (`reprint_labels_in_post_sale_phase_is_blocked` → 422 `/deposit-reprint-not-allowed`). `@Order(1)` renommé `reprint_labels_before_any_active_edition_returns_404` + commentaire clarifié (il n'exerce PAS la garde de phase). 20/20 verts.
  - [x] Tests back `domain/print/DepositSlipPrintingIT.java` `@Order(13)` `reprint_deposit_slip_is_blocked_in_post_sale_phase` : reste vert — commentaire ajusté (garde Dépôt commune aux deux réimpressions). 15/15 verts.

- [x] **T-B — Partie B : case impression bilan au solde + bouton par statut** (AC : B1–B3) — **frontend seul**
  - [x] `features/settlement/settlement-list.component.ts` : `MatCheckboxModule` ajouté aux `imports`. Signal `readonly printReportOnSettle = signal(true);`. `closeSettleForm()` remet `printReportOnSettle.set(true)`.
  - [x] `settlement-list.component.ts` `confirmSettle()` : branche succès uniquement — `const shouldPrint = this.printReportOnSettle();` avant `this.closeSettleForm();`, puis `if (shouldPrint) { void this.autoPrintReport(sellerId); }`. `catch`/`finally` inchangés.
  - [x] `settlement-list.component.ts` : méthode privée `autoPrintReport(sellerId)` — patron `pos-page.autoPrintInvoice` : set `printingReportForSellerId`, try `printReport` + toast succès (chaîne résolue via `translate.instant`), catch 422 `/invalid-printer-selection` → `printerUnavailable` sinon `printReport`, finally `.set(null)`. Jamais de rethrow, ne touche pas `submitting`, n'annule pas le solde.
  - [x] `settlement-list.component.html` : bouton « Imprimer le bilan » encadré `@if (settlement.status !== 'UNSETTLED')`. `<mat-checkbox>` ajouté dans `.settlement-form-inner` (patron `payment-dialog.component.html`) : `[checked]="printReportOnSettle()" (change)="printReportOnSettle.set($event.checked)"` + `settlement.form.printReportOnSettle`.
  - [x] i18n : `settlement.form.printReportOnSettle` ajouté à `fr.json` (« Imprimer le bilan de vente ») et `en.json` (« Print the sales report »).
  - [x] Tests front `settlement-list.component.spec.ts` : test « print report button » réécrit (Alice UNSETTLED = 2 boutons sans imprimer ; Bob SETTLED = 1 → `sort()` === `[1, 2]`) ; tests `printReport` réorientés vers `BOB` ; `beforeEach` donne un défaut `printReport → of(undefined)` ; 4 nouveaux tests (case cochée → `printReport` appelé ; décochée → non appelé ; échec auto-print → ligne reste `SETTLED`, `error()` null, `submitting()` false ; réouverture du formulaire remet la case cochée). 43/43 verts.

- [x] **T-C — Partie C : FR-109 verrouillage lot** (AC : C1–C5)
  - [x] Back : `org.pluribourse.domain.pos.exception.LotAlreadySoldException extends BusinessException` créé — `super(CONFLICT, "lot-already-sold", "Lot already sold: " + lotId)`, patron `ItemAlreadySoldException`. Aucun handler ajouté.
  - [x] Back `ItemRepository.existsByLotIdAndSoldTrue(Long lotId)` ajouté (requête dérivée).
  - [x] Back `PosScanService.scan()` : garde `item.getLot() != null && itemRepository.existsByLotIdAndSoldTrue(lot.getId())` → `LotAlreadySoldException` après `isSold`. Javadoc complété. `PosBasketService.addItem` hérite via `posScanService.scan`.
  - [x] Back `PosBasketService.validate()` pré-check : boucle `ItemPricing.distinctByLot(items)` → `existsByLotIdAndSoldTrue` → `LotAlreadySoldException`, après le bloc `alreadySold`.
  - [x] Back `PosBasketService.validate()` — garde de course : **`LotRepository.bumpVersion(id, expectedVersion)` (bulk JPQL `@Modifying` `UPDATE Lot SET version = version + 1 WHERE id = ? AND version = ?`)** juste après `saleRepository.save(sale)`, avant la boucle `setSold`. **Écart assumé vs la story** : `entityManager.lock(lot, OPTIMISTIC_FORCE_INCREMENT)` + `flush()` ne fonctionne pas — l'incrément forcé JPA est **différé au `beforeTransactionCompletion`** (prouvé par le 1er run de `SaleConcurrencyIT` : `EntityIncrementVersionProcess.doBeforeTransactionCompletion` → `JpaSystemException` au commit, hors de `validate()`, non rattrapable). Le bulk update prend le verrou d'écriture de ligne immédiatement (sérialisation), le perdant matche 0 ligne **ou** (MariaDB snapshot isolation) échoue en 1020 → `LotAlreadySoldException` dans les deux cas, `try/catch` sur `ObjectOptimisticLockingFailureException | OptimisticLockException` + `JpaSystemException` filtré via `isLotVersionRace` (`SnapshotIsolationException` OU `SQLException` code 1020). Rollback intégral du perdant : pas de `Sale`, aucun membre `sold`, `sale = null` (AC-C3).
  - [x] Front `pos-page.component.ts` : branche `type?.endsWith('/lot-already-sold')` ajoutée dans `handleValidationError` (après `basket-validation-conflict`) ET `handleScanError` (avant `no-active-edition`) → `lastScanIssue` `volunteer.pos.error.lotAlreadySold` variant error.
  - [x] i18n : `volunteer.pos.error.lotAlreadySold` ajouté à `fr.json` / `en.json` sous `volunteer.pos.error`.
  - [x] Tests back `PosScanIT` : lot 2 membres ajouté au fixture `@Order(2)` (DEPOSIT) ; nouveau `@Order(13)` — membre marqué `sold` via repo, scan du frère → 409 `/lot-already-sold`, frère reste `sold=false`. 13/13 verts.
  - [x] Tests back `PosBasketIT` : 3e lot « Lot Course » ajouté au fixture ; nouveau `@Order(20)` — (a) membre de « Lot Retrait » vendu via repo → `POST /baskets/{id}/items` sur frère → 409 ; (b) « Lot Course » membre A au panier puis membre B vendu ailleurs → `validate` → 409 ; nettoyage `removeLot`. Renumérotation 20→21..23→24. 24/24 verts.
  - [x] Tests back `SaleConcurrencyIT` : `LotRepository` injecté, `@AfterEach wipeFixtures` (2 tests désormais → isolation FK-safe des ventes) ; nouvelle méthode `two_concurrent_validations_of_different_members_of_the_same_lot_exactly_one_succeeds` — lot 2 membres, baskets A/B, 2 `validate()` concurrents ; asserts `successCount == 1`, cause perdante `LotAlreadySoldException`, `saleRepository.count() == 1`, `total == 12.00`, exactement un membre `sold` avec `sale`, l'autre `sold=false`/`sale=null`. 2/2 verts (Docker présent).
  - [x] Tests front `pos-page.component.spec.ts` : test **scan** (`onScan` → `addItem` 409) + test **validation** (`openPaymentDialog` → `validate` 409) pour `lot-already-sold` → `lastScanIssue()` message `volunteer.pos.error.lotAlreadySold` variant error, panier inchangé. 32/32 verts.

- [x] **T-D — Partie D : bordereau détail des lots** (AC : D1–D4) — `DepositSlipRenderer`
  - [x] Back `DepositSlipRenderer.buildLotDetailTable(List<Item>, Locale)` — `PdfPTable` 3 col. (`print.slip.column.lot/.lotCategory/.lotItem`), une ligne par `item` tel que `item.getLot() != null` (PAS `distinctByLot`), cellules `item.getLot().getName()` · `item.getCategory().getName()` · `item.getName()`. Dans `renderSlip` : `Paragraph(print.slip.lotDetailSection, HEADER_FONT)` + table, après le tableau des articles, **uniquement si** `items.stream().anyMatch(i -> i.getLot() != null)`. Javadoc de classe + méthode.
  - [x] Back : `item.getCategory()` utilisé (fetch-join), jamais `item.getLot().getCategory()` (LAZY).
  - [x] i18n : `print.slip.lotDetailSection` + `.column.lot` + `.column.lotCategory` + `.column.lotItem` ajoutés aux 3 fichiers `messages*.properties`.
  - [x] Tests back `DepositSlipPrintingIT` : `@Order(5)` recalculé — `countOccurrences("Lot Duo")` 1→3 (1 tableau articles + 2 lignes membres), `countOccurrences("12.00")` reste 1 (pas de prix dans le détail), `+contains("Piece A"/"Piece B"/"Jouets")`. Nouveau `@Order(16)` — vendeur sans lot → `doesNotContain("Détail des lots")`. 16/16 verts.

- [x] **T-E — Partie E : bilan de vente restructuré** (AC : E1–E6) — `SettlementReportRenderer`
  - [x] Back `SettlementReportRenderer.renderReport()` : `buildSoldItemsTable` + `buildUnsoldItemsTable` + titres `soldSection`/`unsoldSection` remplacés par (1) `buildUnifiedItemsTable(items, soldLotIds, ...)` 5 col. nom·catégorie·table·prix·statut (lot : `soldLotIds.contains(lot.getId())` pour le statut, jamais `representative.isSold()` ; article : ses propres champs + `isSold()`), titre `itemsSection` ; (2) `buildLotDetailTable(items, ...)` 5 col. lot·article·catégorie·table·statut réel, 1 ligne/membre, rendu si ≥1 lot, titre `lotDetailSection` ; (3) ligne `countLine` (`{0}` = `items.stream().filter(Item::isSold).count()`, `{1}` = `size - {0}`, `{2}` = `size`, args en `String` pour éviter le formatage numérique locale).
  - [x] Back : normalisation `soldLotIds` / `soldItems` **conservée** (statut ligne de lot + totaux) ; `unsoldItems` supprimé (plus aucun consommateur) ; `total`/`commissionAmount`/`net` et `if (amountPaid != null)` **inchangés**. Helper `statusLabel(boolean, Locale)`.
  - [x] Back : catégorie via `item.getCategory().getName()`, table via `item.getTableNumber()`.
  - [x] i18n : `print.settlementReport.itemsSection`, `.column.status`, `.status.sold`, `.status.unsold`, `.lotDetailSection`, `.column.lot`, `.countLine` ajoutés aux 3 fichiers ; `.column.item` réutilisé pour la colonne article du tableau éclaté ; `.soldSection` / `.unsoldSection` **supprimés** de `messages_fr` / `messages_en` (absents du défaut).
  - [x] Tests back `SettlementReportPrintingIT` : `@Order(8)` recalculé — `countOccurrences("Lot Mixte")` 1→3, `("Lot Invendu")` 1→3, `("Jouets")` 2→9, `("8.00")` reste 1 ; `+contains("Statut"/"Mixte A"/"Mixte B"/"Invendu A"/"Invendu B"/"Peluche"+"7.00")` ; ligne de comptage `contains("3 vendus")`+`("4 invendus")` ; `"15.00"/"13.50"/"1.50"` inchangés. `@Order(9)` — `Sold items`/`Unsold items` → `Lot details`/`Status`/`3 sold`/`4 unsold`/`7 deposited`. 15/15 verts.
  - [x] Tests back `BulkSettlementReportPrintingIT` `@Order(14)` : `contains(...)` inchangés (fixture sans lot), commentaire obsolète (« no price cell ») corrigé. 14/14 verts.
  - [x] Tests back `DailyReportPrintingIT` (16/16) / `EditionReportPrintingIT` (19/19) : non impactés, verts.

- [x] **T-Z — Vérifications finales** (AC : Z)
  - [x] `./mvnw clean package` (racine backend) : **565 tests verts, 0 échec / 0 erreur / 0 skip** (baseline 559 → +6 : `ThermalLabelPrintingIT` +2, `DepositSlipPrintingIT` +1, `PosScanIT` +1, `PosBasketIT` +1, `SaleConcurrencyIT` +1). `SaleConcurrencyIT` **exécuté** (Docker présent), pas skippé. `pluribourse-0.0.1-SNAPSHOT.jar` produit.
  - [x] `npm test` (`pluribourse-frontend/`) : **717 tests verts** (67 fichiers ; baseline 702 → +15).
  - [x] `npm run build` (frontend) : exit 0, **aucune erreur ni warning**.
  - [x] Parité clés i18n : `fr.json` ⇔ `en.json` (605 ⇔ 605, 0 écart) ; toutes les nouvelles clés `print.slip.*` / `print.settlementReport.*` présentes dans les **3** `.properties` (`messages` défaut + `_fr` + `_en`) ; `messages_fr` ⇔ `messages_en` (0 écart) ; `.soldSection` / `.unsoldSection` supprimées partout.
  - [x] `baseline_commit` (frontmatter) = `6d243474…` (HEAD de `main` au démarrage). Inchangé — la Partie A a été commitée par l'utilisateur en cours de story (`0749c3c "Add story 5.8 : update post sale"`, **parent = `6d24347`**), les Parties B→E restent dans le working tree.
  - [ ] Vérification visuelle laissée à Manerial (CLAUDE.md) : (1) Post-vente sans entrée « Dépôt » ni route active ; (2) formulaire de solde, case cochée → bilan imprimé ; bouton bilan masqué pour un non soldé ; (3) scan d'un frère de lot déjà vendu → message inline spécifique ; (4) PDF bordereau (tableau « détail des lots ») et PDF bilan (tableau unifié + éclaté + ligne de comptage) ; (5) T2 — recherche vendeur en échec → message traduit.

### Review Findings

_bmad-code-review, 2026-09-03 — 3 revues parallèles (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Bilan : 1 décision (tranchée → patch), 5 patchs **appliqués**, 7 différés, 10 rejetés. Edge Case Hunter a échoué 1 fois (rate limit) puis abouti au retry ; les 3 couches ont finalement toutes tourné._

_Vérifs post-patchs : `./mvnw -o test-compile` OK ; IT affectées vertes (`PosBasketIT`, `PosScanIT`, `DepositSlipPrintingIT`, `SettlementReportPrintingIT`, `BulkSettlementReportPrintingIT`, `ThermalLabelPrintingIT`) ; `SaleConcurrencyIT` skippé (Docker absent sur la machine de revue — à rejouer où Docker est présent) ; `npm test` 717/717 ; `npm run build` sans warning._

- [x] [Review][Patch] Ordonnancement canonique des verrous de lot dans `validate()` [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:159-217] — _(issu d'un point decision-needed, tranché par Manerial le 2026-09-03 : option 3.)_ Les boucles de pré-check et de bump itèrent `ItemPricing.distinctByLot(items)` dans l'ordre de scan. Deux postes dont les paniers touchent les **deux mêmes lots dans un ordre inverse** s'interbloquent (MariaDB 1213) → `CannotAcquireLockException` non rattrapé, pas de handler `DataAccessException` dans `GlobalExceptionHandler` → **500** (viole AC-C3 « jamais un 500 »). Correctif : trier les lots distincts par `lot.getId()` avant la boucle de bump (et le pré-check) — patron anti-deadlock déjà utilisé par `LotService` d'avant la 3.14. Le `innodb_lock_wait_timeout` (1205), atteignable seulement avec paniers multi-lots + verrou tenu longtemps, reste un **risque résiduel documenté** par un commentaire dans le code (pas de mapping d'exception ajouté). Voir aussi la piste « Réservation de lot au scan » en discussion (remplacerait `bumpVersion`).

- [x] [Review][Patch] Nettoyer la gestion d'exception morte/trompeuse autour de `bumpVersion` [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:186-217,325-340] — La Javadoc de `isLotVersionRace` décrit encore « the em.lock()+flush path » (supprimé) ; le `catch (ObjectOptimisticLockingFailureException | OptimisticLockException)` sur un `UPDATE` `@Modifying` en masse est inatteignable (le DML en masse ne lève pas ces exceptions). Corriger la Javadoc pour décrire le chemin bulk-JPQL réel ; retirer le catch mort (garder la branche `JpaSystemException`/erreur 1020, atteignable sous l'isolation snapshot MariaDB).
- [x] [Review][Patch] `buildLotDetailTable` : lignes non groupées par lot [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java:120-142 ; SettlementReportRenderer.java:181-207] — Les deux `buildLotDetailTable` itèrent `items` dans l'ordre des numéros d'article ; un vendeur ayant enregistré ses lots en alternance obtient des lignes `Lot A / Lot B / Lot A / Lot B` dans un tableau censé détailler lot par lot. Trier les lignes membres par lot (puis par numéro d'article).
- [x] [Review][Patch] AC-B1 partiel : `printReportOnSettle` réinitialisé seulement dans `closeSettleForm()` [pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:162-165] — Ouvrir le formulaire de solde du vendeur B directement alors que celui de A est ouvert (le bouton « Solder » n'est `[disabled]` que sur `submitting()`) affiche le formulaire de B avec la case décochée héritée de A. AC-B1 : « réinitialisée à cochée à chaque ouverture ». Ajouter `this.printReportOnSettle.set(true)` dans `openSettleForm()`.
- [x] [Review][Patch] Ordre DOM de la case « Imprimer le bilan de vente » [pluribourse-frontend/src/app/features/settlement/settlement-list.component.html:118-135] — Placée après les boutons Valider/Annuler dans `.settlement-form-inner` : l'ordre de tabulation clavier est montant → Valider → Annuler → case, donc un utilisateur clavier atteint « Valider » avant l'option qui gouverne son effet. Déplacer le `<mat-checkbox>` avant les boutons d'action dans le template.

- [x] [Review][Defer] `LotRepository.bumpVersion` `@Modifying` sans `clearAutomatically`/`flushAutomatically` [pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/LotRepository.java] — différé, latent : le `Lot` managé garde sa `version` périmée après le bump ; inoffensif aujourd'hui (rien ne re-flush `Lot` dans `validate()` ensuite) mais invariant fragile ; correctif sûr non trivial (un `clearAutomatically` global détacherait les `items`/`sale` flushés juste après).
- [x] [Review][Defer] Aucun filet BDD pour « un lot vendu une seule fois » [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java] — différé, pré-existant : FR-109 repose entièrement sur `bumpVersion` (verrou d'écriture de ligne + prédicat de version). Sain sur InnoDB, mais une contrainte partielle-unique en BDD serait une garantie secondaire → nécessite une migration Liquibase = story dédiée (migrations hors périmètre ici).
- [x] [Review][Defer] Couverture `SaleConcurrencyIT` limitée au cas mono-lot [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java] — différé : non couverts — paniers multi-lots, deux membres d'un lot dans un même panier contre un 2e poste, comportement H2 de `bumpVersion` sous course réelle. Cohérent avec les différés de tests de concurrence des stories précédentes.
- [x] [Review][Defer] Lots pré-3.14 à catégories de membres divergentes [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java] — différé, pré-existant, dépendant des données : la migration 033 a rempli `Lot.category` depuis le 1er membre seulement ; `buildLotDetailTable` affiche `item.getCategory().getName()` par membre → lignes d'un même lot potentiellement divergentes. Pas de crash.
- [x] [Review][Defer] `messages.properties` (défaut) manque `print.settlementReport.column.{item,category,table,price}` [pluribourse-backend/src/main/resources/messages.properties] — différé, pré-existant, inerte : `documentLocale` est toujours FR ou EN (bundles complets) ; lacune que la story a explicitement mise hors périmètre. Toutes les clés *nouvelles* sont bien dans les 3 fichiers ; `.soldSection`/`.unsoldSection` supprimées partout.
- [x] [Review][Defer] AC-E6 : couverture ténue [pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java (@Order 14)] — différé : la fixture n'a aucun lot, donc le chemin d'impression groupée n'exerce ni le tableau unifié, ni la colonne statut, ni la ligne de comptage. Même renderer, test vert — AC techniquement satisfait.
- [x] [Review][Defer] Périmètre : le commit livre les amendements d'artefacts de planification [_bmad-output/planning-artifacts/{prd,epics,architecture}.md + sprint-change-proposal-2026-09-02b.md] — différé, hygiène : relativement à `baseline_commit 6d243474`, ce commit *livre* les amendements que le texte de la story dit « déjà appliqués » et que sa table de périmètre liste hors périmètre. Le texte des amendements est correct ; aucune action code.

## Dev Notes

### Périmètre exact

| Dans le périmètre | Hors périmètre |
|---|---|
| Front : condition de phase de l'entrée sidebar `/volunteer/deposit`, `depositPhaseGuard`, commentaires | Retrait de `/volunteer/deposit` de `PHASE_BOUND_VOLUNTEER_PATHS` ; `resolveVolunteerLandingPath` ; toute modif de `deposit-page.component` ou de ses clés i18n |
| Back : `DepositValidationService.resolveSellerDeposit` → garde Dépôt seul ; nettoyage `PhaseGuard` (`requireDepositPhaseForSlipReprint` → `…ForReprint`, suppression de `requireDepositOrPostSalePhase`) | `PhaseGuard.requireDepositPhase` (slug `item-modification-locked`) et ses appelants `ItemService`/`LotService`/`SellerService` ; tout nouveau slug pour la réimpression |
| Front : case `printReportOnSettle` + `autoPrintReport` best-effort dans `confirmSettle` ; `@if status !== 'UNSETTLED'` sur le bouton bilan | Refonte de `settlement-list` ; SSE `settlement-updated` (Story 5.7, inchangé) ; endpoint d'impression (réutilisé tel quel) ; `printAllReports` admin (visibilité inchangée) |
| Back : `LotAlreadySoldException` (409 `lot-already-sold`), `ItemRepository.existsByLotIdAndSoldTrue`, garde scan + garde validation + garde concurrence (`Lot` `@Version` FORCE_INCREMENT) | Modif de `ItemPricing.computeTotal` / `distinctByLot` (comportement figé) ; prorata d'un lot partiel ; `@ExceptionHandler` dédié (option minimale suffit) ; modif de `removeLot` / `buildLotGroups` |
| Front : branche `lot-already-sold` dans `handleScanError` **et** `handleValidationError` | Nouveau champ sur `ScanResultDto` (l'info transite par le `type` d'erreur) |
| Back : `DepositSlipRenderer` — tableau « détail des lots » (3 col., sans prix, conditionnel) | Refactor d'une classe de base / helper partagé entre renderers (non-partage **délibéré**, cf. Javadoc `InvoiceRenderer`/`SettlementReportRenderer`) |
| Back : `SettlementReportRenderer` — tableau unifié + colonne statut + tableau éclaté + ligne de comptage ; suppression `soldSection`/`unsoldSection` | Modif des totaux brut/commission/net et de la ligne « Montant remis » (déjà conformes) ; `DailyReportRenderer` / `EditionReportRenderer` / `InvoiceRenderer` / `ThermalLabelRenderer` |
| i18n : nouvelles clés `settlement.form.printReportOnSettle` (json), `volunteer.pos.error.lotAlreadySold` (json), `print.slip.*` + `print.settlementReport.*` (properties) | Amendements PRD/epics/architecture (déjà appliqués) ; `EXPERIENCE.md` (dérive assumée) ; backport FR-105–108 |
| Tests : E2E par contrôleurs + `SaleConcurrencyIT` (exception concurrence tolérée) + specs front | Tests de service isolés (sauf `SaleConcurrencyIT`), tests de migration Liquibase, tests de config Security |

### Fichiers à créer / modifier

**Backend — Partie A**
- `domain/item/service/DepositValidationService.java` — **MODIFIÉ** (`resolveSellerDeposit` : garde Dépôt seul ; `reprintDepositSlip` : retrait de l'appel redondant ; Javadoc)
- `domain/item/service/PhaseGuard.java` — **MODIFIÉ** (rename `requireDepositPhaseForSlipReprint` → `requireDepositPhaseForReprint` ; suppression `requireDepositOrPostSalePhase` ; Javadoc)
- `src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java` — **MODIFIÉ** (nouveau `@Order` POST_SALE → 422 ; `@Order(1)` renommé)
- `src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` — **MODIFIÉ** (`@Order(13)` commentaire ; + voir Partie D)

**Backend — Partie C**
- `domain/pos/exception/LotAlreadySoldException.java` — **NOUVEAU** (`BusinessException`, 409, `lot-already-sold`)
- `domain/item/repository/ItemRepository.java` — **MODIFIÉ** (`existsByLotIdAndSoldTrue`)
- `domain/pos/service/PosScanService.java` — **MODIFIÉ** (garde frère de lot vendu après `isSold`)
- `domain/pos/service/PosBasketService.java` — **MODIFIÉ** (garde de validation + garde de concurrence `Lot` FORCE_INCREMENT ; injecter `EntityManager` ou `LotRepository`)
- `src/test/java/org/pluribourse/domain/pos/PosScanIT.java` — **MODIFIÉ** (nouveau `@Order` + fixture lot)
- `src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` — **MODIFIÉ** (nouveau(x) `@Order`)
- `src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java` — **MODIFIÉ** (nouvelle méthode `@Test` + `LotRepository`)

**Backend — Parties D & E**
- `domain/print/service/DepositSlipRenderer.java` — **MODIFIÉ** (`buildLotDetailTable` + rendu conditionnel)
- `domain/print/service/SettlementReportRenderer.java` — **MODIFIÉ** (tableau unifié + éclaté + comptage ; `soldSection`/`unsoldSection` retirés ; totaux inchangés)
- `src/main/resources/messages.properties` + `messages_fr.properties` + `messages_en.properties` — **MODIFIÉ** (clés `print.slip.*` + `print.settlementReport.*` ; suppression `soldSection`/`unsoldSection`)
- `src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java` — **MODIFIÉ** (`@Order(8)` recalcul complet, `@Order(9)` libellés)
- `src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java` — **MODIFIÉ si besoin** (`@Order(14)`)

**Frontend — Partie A**
- `src/app/layout/app-layout/app-layout.component.html` — **MODIFIÉ** (l.208)
- `src/app/core/guards/deposit-phase.guard.ts` — **MODIFIÉ** (l.19 + commentaire)
- `src/app/models/active-phase.enum.ts` — **MODIFIÉ** (commentaire l.20-25)
- `src/app/layout/app-layout/app-layout.component.spec.ts` — **MODIFIÉ**
- `src/app/core/guards/deposit-phase.guard.spec.ts` — **MODIFIÉ**

**Frontend — Partie B**
- `src/app/features/settlement/settlement-list.component.ts` — **MODIFIÉ** (`MatCheckboxModule`, `printReportOnSettle`, `autoPrintReport`, branchement dans `confirmSettle`/`closeSettleForm`)
- `src/app/features/settlement/settlement-list.component.html` — **MODIFIÉ** (`@if status !== 'UNSETTLED'` + `<mat-checkbox>`)
- `src/app/features/settlement/settlement-list.component.spec.ts` — **MODIFIÉ**

**Frontend — Partie C**
- `src/app/features/volunteer/pos/pos-page.component.ts` — **MODIFIÉ** (`handleScanError` + `handleValidationError`)
- `src/app/features/volunteer/pos/pos-page.component.spec.ts` — **MODIFIÉ**

**Frontend — i18n**
- `public/i18n/fr.json` + `public/i18n/en.json` — **MODIFIÉ** (`settlement.form.printReportOnSettle`, `volunteer.pos.error.lotAlreadySold`)

### État actuel des fichiers UPDATE (à préserver)

**`PhaseGuard.java`** — classe `final` utilitaire, méthodes statiques, exceptions du package `domain/item/exception`. `requireDepositPhase` (l.16, `→ ItemModificationNotAllowedException`, 422 `item-modification-locked`) : **ne pas toucher** (utilisé par `ItemService` ×3, `LotService` ×3, `SellerService` ×2 via une copie privée). `requireDepositOrPostSalePhase` (l.26, `→ DepositReprintNotAllowedException`, 422 `deposit-reprint-not-allowed`) : **1 seul appelant**, `DepositValidationService.resolveSellerDeposit` l.79 — c'est la cible du resserrement. `requireDepositPhaseForSlipReprint` (l.40, même exception) : appelé en plus dans `reprintDepositSlip` l.64 ; devient redondant. `requireSalePhase` / `requirePostSalePhase` / `requirePostSaleOrClosedPhase` : intacts.

**`DepositValidationService.java`** — `@Transactional(readOnly = true)` × 2 méthodes publiques. `reprintLabels(sellerProfileId, session)` et `reprintDepositSlip(sellerProfileId, session)` passent toutes deux par `resolveSellerDeposit(sellerProfileId)` (l.77-85 : `getActiveEdition` → **garde de phase** → `editionScopedLookup.findSellerInEdition` (IDOR-safe) → `itemRepository.findAllBySellerProfileIdOrderByItemNumberAsc` → `EmptyDepositException` si vide). Après resserrement, `reprintLabels` devient Dépôt-seul lui aussi (voulu : SCP « réimpression d'étiquettes en Post-vente : supprimée »). **Préserver** : `EmptyDepositException`, le contrôle de printer par action, l'absence d'état « dépôt validé » persisté.

**`deposit-phase.guard.ts`** — `CanActivateFn` async : `loadEditionOrRedirect` → `currentEditionService.currentEdition()?.phase` → `DEPOSIT || POST_SALE ? true : router.createUrlTree(['/404'])`. Patron partagé avec `sale-phase.guard.ts` (SALE seul) et `settlement-phase.guard.ts` (POST_SALE seul). **Préserver** le passage `loadEditionOrRedirect` et le fallback `/404`.

**`app-layout.component.ts` / `.html`** — pas de signal `phase()` : la phase est `currentEdition()?.phase` (`currentEdition = this.currentEditionService.currentEdition`). L'`effect` du constructeur (l.59-95) redirige un **bénévole** hors d'un chemin de `PHASE_BOUND_VOLUNTEER_PATHS` (dont `/volunteer/deposit`) sur un **changement** de phase, vers `resolveVolunteerLandingPath(phase)`. **Préserver intégralement** : `isFirstRun`, le check `phase === previousPhase`, `if (!this.isVolunteer()) return`, la liste `PHASE_BOUND_VOLUNTEER_PATHS`. Le HTML : bloc `@if (isVolunteer())` l.204-274 avec les entrées phase-gated (`SALE` pour pos/sales, aucune condition pour catalog, `POST_SALE` pour settlement) — ne modifier **que** la ligne 208.

**`settlement-list.component.ts`** — composant standalone unique `/volunteer/settlement` **et** `/admin/settlement` (`isAdmin()`). Signals : `settlements`, `isLoading`, `error`, `submitting`, `statusFilter`, `filteredSettlements` (computed avec tri client déterministe `lastName` → `firstName` → `sellerId`, Story 5.7), `openSettleFormForSellerId`, `settleAmount`, `printingReportForSellerId`, `printingAll`, `anyPrintInFlight`, `openSettlement`, `warningBelowDue`, `blockedAboveDue`, `warningMessage`. `confirmSettle(sellerId)` : `submitting.set(true)` → `settle()` → `applyUpdate(updated)` → toast succès → `closeSettleForm()` ; `catch` = `isAlreadySettledConflict` → toast `settlement.error.alreadySettled` + `closeSettleForm()` sinon toast `settlement.error.settle` ; `finally` = `submitting.set(false)` + `void this.loadSettlements(true)` (reload de rattrapage SSE, Story 5.7). `printReport(settlement)` : garde `anyPrintInFlight` → `printingReportForSellerId.set(sellerId)` → `printReport()` → toast ; `catch` 422 `invalid-printer-selection` → `settlement.error.printerUnavailable` sinon `settlement.error.printReport` ; `finally` `.set(null)`. **Préserver** : `applyUpdate`, la garde `anyPrintInFlight`, la souscription SSE `settlementUpdated` + `takeUntilDestroyed`, `loadSettlements(silent)`, le tri déterministe, `warningBelowDue`/`blockedAboveDue`.

**`settlement-list.component.html`** — `<table class="data-table">`, colspan `isAdmin() ? 7 : 5`. Cellule d'actions l.77-100 : bouton « Solder » et « Non réclamé » déjà sous `@if (settlement.status === 'UNSETTLED')` ; bouton « Imprimer le bilan » **sans condition** (l.84-92) — c'est lui qu'on encadre. Formulaire inline l.102-140 : `@if (openSettleFormForSellerId() === settlement.sellerId)` → ligne `<tr class="settlement-form-row">` avec `.settlement-form-inner` (label + `mat-form-field` montant + boutons Valider/Annuler) + `app-notification-inline` conditionnels.

**`PosScanService.scan()`** — `@Transactional(readOnly = true)` : `getActiveEdition` → `requireSalePhase` → regex barcode `^\d{8}$` → `findByEditionIdAndSellerNumberAndItemNumber` (pas de `JOIN FETCH i.lot`, mais transaction ouverte + `Lot.items` EAGER) → `if (item.isSold()) throw new ItemAlreadySoldException(item.getId())` → `mapper.toDto(item)` (`ScanResultDto` record : `itemId, name, price, incomplete, comment, lotId`). **Préserver** toute la chaîne ; insérer la garde de lot **après** `isSold`.

**`PosBasketService.validate()`** — `@Transactional` : `getActiveEdition` → `requireSalePhase` → `requireOwnedBasket` (IDOR) → `items = basketItemsOf(basket)` (relu en base, `LEFT JOIN FETCH i.lot`, pas `i.lot.items`) → `EmptyBasketException` si vide → **pré-check `alreadySold`** (l.143-149 : `items.filter(Item::isSold)` → `BasketValidationConflictException`) → `total = ItemPricing.computeTotal(items)` → garde `amountGiven < total` (CASH) → création `Sale` → **boucle de flush** `for (Item item : items) { item.setSold(true); item.setSale(sale); saveAndFlush ; catch ObjectOptimisticLockingFailureException / JpaSystemException(SnapshotIsolationException) → conflicts.add }` → si `conflicts` → `BasketValidationConflictException` → `basketRepository.delete(basket)` → `SaleDto`. **Préserver** toute la chaîne et le double catch (H2 vs MariaDB) ; insérer la garde de validation après l.149 et la garde de concurrence autour de la boucle de flush.

**`ItemPricing.java`** — `final`, statique. `computeTotal(items)` = somme sur `distinctByLot(items)` de `lot.globalPrice` (si lot) ou `item.price`. `distinctByLot(items)` = 1 représentant par `lotId` (premier rencontré, ordre préservé) + tous les articles individuels. **Aucune modification** — comportement figé (partagé par `PosBasketService`, `SettlementService`, les 3 renderers PDF, `ReportService`).

**`DepositSlipRenderer.java`** — OpenPDF 3.0.0 (`org.openpdf.text.*`), `PdfPTable`/`PdfPCell`/`Phrase`, fonts en bloc `static`, helpers privés `headerCell` / `addRow` **non partagés** (délibéré). `renderSlip(sellerProfile, items, commissionRate, documentLocale)` : titre + identité + `buildItemsTable` (2 col., `distinctByLot`, lot = nom+prix global) + totaux (`computeTotal` → `computeNetPayout`). Rendu sur le thread consommateur de la file, **transaction fermée** → attention lazy-load.

**`SettlementReportRenderer.java`** — `renderReport(sellerProfile, items, commissionRate, documentLocale, amountPaid)`. Normalisation « lot vendu en entier » l.93-106 : `soldLotIds` (lots avec ≥1 membre `sold`) → `soldItems` (membres routés ensemble) / `unsoldItems`. `buildSoldItemsTable` (2 col.), `buildUnsoldItemsTable` (4 col. : item/catégorie/table/prix ; prix rempli seulement pour un lot). Totaux sur `soldItems` (`computeTotal` → `computeCommission` → `computeNetPayout`). `if (amountPaid != null)` → ligne « Montant remis ». **Préserver** la normalisation (pour la ligne de lot du tableau unifié + les totaux) et `if (amountPaid != null)` ; **ne PAS** faire dépendre la ligne de comptage ni le statut par membre du tableau éclaté de cette normalisation (→ `Item.isSold()` brut).

**`SettlementService.getAmountPaid(sellerId)`** — renvoie `null` si aucun `Settlement` **ou** statut `!= SETTLED` (donc `null` aussi pour `UNCLAIMED`). C'est ce `null` qui masque la ligne « Montant remis ». `SettlementStatus` = `{ UNSETTLED, SETTLED, UNCLAIMED }` (`UNSETTLED` jamais persisté). **Inchangé.**

### Patrons à réutiliser (ne pas réinventer)

| Besoin | Source de référence |
|---|---|
| Exception métier → RFC 7807 (409 + slug) | `domain/item/exception/ItemAlreadySoldException.java` (`super(HttpStatus.CONFLICT, "item-already-sold", "…")`) ; `GlobalExceptionHandler.handleBusiness` produit `type = https://pluribourse/errors/<slug>` |
| Exception avec données jointes (si jamais nécessaire) | `domain/pos/exception/BasketValidationConflictException.java` + `@ExceptionHandler` dédié dans `GlobalExceptionHandler` — **non requis** ici (option minimale suffit) |
| Verrou optimiste sur ligne partagée en course multi-postes | `Item.@Version` (Story 4.4) + boucle de flush de `PosBasketService.validate` (catch `ObjectOptimisticLockingFailureException` + `JpaSystemException`/`SnapshotIsolationException` pour H2) — transposer sur `Lot.@Version` via `LockModeType.OPTIMISTIC_FORCE_INCREMENT` |
| Test de course concurrente réelle | `domain/pos/SaleConcurrencyIT.java` (`@SpringBootTest` autonome — **n'étend pas `IntegrationTest`** —, `@Testcontainers(disabledWithoutDocker = true)`, `MariaDBContainer<>("mariadb:11")` + `@ServiceConnection`, fixtures committées, 2 threads via `ExecutorService` + `TransactionTemplate` + `CountDownLatch`, `successCount == 1` + cause de l'`ExecutionException`) |
| Détection d'un type d'erreur RFC 7807 côté front | `extractErrorType(err)?.endsWith('/slug')` (`http-error.util.ts`) — déjà dans `pos-page.component` (`handleScanError`, `handleValidationError`) et `settlement-list.component` (`isAlreadySettledConflict`, `printReport`) |
| Case « imprimer » cochée par défaut + impression best-effort découplée | Story 4.7 — `payment-dialog.component.ts` `readonly printInvoice = signal(true)` + balisage `.html` l.63-68 ; `pos-page.component.ts` `autoPrintInvoice(saleId)` (méthode privée, try/catch propre, toast propre, jamais de rethrow, ne bloque/n'annule rien) appelée en `void this.autoPrintInvoice(...)` après le succès de `validate()` |
| Nouveau `@Order` de test plutôt qu'alourdir un storyboard stable | Stories 5.5/5.6/5.7 — mais ici les scénarios lot s'insèrent dans `PosScanIT` / `PosBasketIT` / `SaleConcurrencyIT` / `*PrintingIT` existants (fixtures lot à ajouter), pas de nouveau fichier |
| Grep d'octets PDF (assertions de contenu) | `*PrintingIT` : `new String(pdf, StandardCharsets.ISO_8859_1)`, `startsWith("%PDF")`, `contains(...)`, `countOccurrences(...)` (helper privé local). `writer.setCompressionLevel(PdfStream.NO_COMPRESSION)` garde le texte greppable. `€` jamais assertable (CP1252). |
| Tableau PDF conditionnel | `SettlementReportRenderer.buildUnsoldItemsTable` (section rendue même vide aujourd'hui — pour la nouvelle section « détail des lots », l'entourer d'un `if (anyLot)`) |

### Points de conception figés (SCP 2026-09-02b §5, L202-217)

- **Prix d'un lot partiellement vendu** : prix global **entier**, encaissé **une seule fois** (pas de prorata).
- **Comptage du bilan** : 1 article physique = 1 unité (lignes `Item`), `sold` **réel** par membre, `vendus + invendus = déposés`.
- **Statut d'une ligne de lot** (tableau unifié) : 2 états — « vendu » si ≥1 membre vendu, sinon « invendu ».
- **Bouton « Imprimer le bilan »** : visible pour `status != UNSETTLED` (donc aussi `UNCLAIMED`).
- **Catégorie du lot** = catégorie **propre** du `Lot` (Story 3.14), pas celle de chaque membre.
- **Réimpression d'étiquettes en Post-vente** : supprimée, **pas de remplacement** dans cette itération (un bouton dédié dans les actions du bilan pourra être ajouté plus tard si besoin).
- **Erreur lot verrouillé** : le perdant d'une course reçoit `409 /lot-already-sold` (message « lot déjà vendu »), pas `basket-validation-conflict`.

### Pièges connus / risques

1. **`LazyInitializationException` sur `Lot.category`** (parties D & E) — `Lot.category` est `LAZY` et **n'est fetch-join dans aucune** des requêtes (`findAllBySellerProfileIdOrderByItemNumberAsc`, `findAllBySellerProfileIdForSettlementReport`, `findAllByEditionIdForSettlementReport` font toutes `LEFT JOIN FETCH i.lot` mais **pas** `i.lot.category`). Le rendu PDF est **hors transaction** (thread consommateur de file). ⇒ Utiliser `item.getCategory().getName()` (déjà `JOIN FETCH i.category`, et = `Lot.category` pour un membre depuis Story 3.14). Ne **jamais** `item.getLot().getCategory()`.
2. **IT « grep d'octets »** (parties D & E) — `DepositSlipPrintingIT.@Order(5)` et surtout `SettlementReportPrintingIT.@Order(8)` + `@Order(9)` : les `countOccurrences(...).isEqualTo(N)` explosent (noms de lots dupliqués entre tableau unifié et tableau éclaté ; catégorie répétée par membre). Recalculer précisément.
   **i18n `.properties` — règle unique (lève l'ambiguïté AC-D4 / AC-Z) : chaque nouvelle clé `print.slip.*` et `print.settlementReport.*` est ajoutée dans les 3 fichiers — `messages.properties` (défaut), `messages_fr.properties`, `messages_en.properties` — même valeur EN dans le défaut et `_en`.** `messages.properties` (défaut) est **déjà incomplet** avant cette story (il manque `print.slip.totalGross` et tout le bloc `print.settlementReport.*`) : **backfill hors périmètre** — ne pas rattraper les clés pré-existantes manquantes, seulement garantir que celles **ajoutées ici** sont dans les 3.
3. **Comptage ≠ calcul monétaire** (partie E) — la ligne de comptage et le statut par membre du tableau éclaté reposent sur `Item.isSold()` **brut** (`items`, sans `distinctByLot`). Les totaux brut/commission/net **ne changent pas** (`computeTotal(soldItems)` sur la liste normalisée, prix global une fois). Ne pas fusionner les deux logiques.
   **Statut d'une *ligne de lot* du tableau unifié : `soldLotIds.contains(lot.getId()) ? vendu : invendu` — JAMAIS `representative.isSold()`.** `distinctByLot` garde le **premier membre rencontré** comme représentant : pour un lot partiellement vendu ce peut être un membre invendu, `representative.isSold()` donnerait alors « invendu » à tort. `representative.getTableNumber()` / `.getCategory()` restent corrects (table + catégorie partagées par tous les membres depuis Story 3.14).
4. **Garde de concurrence lot** (partie C) — le `@Version` **par `Item`** ne protège pas contre deux membres **différents** du même lot vendus simultanément. `Lot` a un `@Version` mais il n'est jamais touché par la vente d'un membre. `OPTIMISTIC_FORCE_INCREMENT` sur `Lot` + flush explicite = deux `validate()` concurrents entrent en conflit sur `Lot.version`. Point de flush exact à ajuster ; **H2 (tests IT normaux) et MariaDB (`SaleConcurrencyIT`) ont une sémantique de verrou différente** — prévoir le double catch comme la boucle existante.
5. **Colonne « Table » d'une ligne de lot** (partie E, tableau unifié) — un `Lot` n'a **pas** de `tableNumber` ; utiliser `representative.getTableNumber()` (tous les membres d'un lot partagent la même table depuis Story 3.14). Il n'existe **ni** entité `TableAssignment` **ni** `getTable()`.
6. **`app-layout.component.html`** a été touché par la Story 4.7 (nav phase + `/volunteer/sales`) — la partie A modifie **une seule ligne** (l.208) ; attention à ne pas dériver sur les autres blocs `@if`.
7. **`settlement-list.component.spec.ts`** a été fortement remanié par la Story 5.7 (700 tests front) — les tests `printReport` visent aujourd'hui `ALICE` (`UNSETTLED`) ; après B, le bouton n'existe plus pour elle → réorienter vers `BOB` (`SETTLED`) et adapter le test de comptage de boutons.
8. **`DepositReprintNotAllowedException`** (422 `deposit-reprint-not-allowed`) doit rester le slug des deux réimpressions hors phase Dépôt (ne pas basculer sur `item-modification-locked` de `requireDepositPhase`) — d'où le rename `requireDepositPhaseForSlipReprint` → `requireDepositPhaseForReprint` plutôt qu'un remplacement par `requireDepositPhase`.

### Contraintes projet applicables

- Montants : `BigDecimal` partout, jamais `float`/`double` (NFR-003). Parties D/E : calcul monétaire gelé ; partie C : ne calcule pas (transporte des `Long` d'ID).
- Aucune donnée perso (nom/email/téléphone vendeur) dans les logs. `LotAlreadySoldException` ne porte qu'un `lotId`. Ne pas logger de `SellerProfile` / `SettlementDto` complet.
- Backend : type explicite, **jamais** `var` ; accolades sur tout `if`/`for`/`while` même mono-ligne ; JavaDoc sur logique non triviale (garde de concurrence, `buildLotDetailTable`, ligne de comptage).
- Frontend : composants standalone, Signals (pas de NgRx), **template HTML séparé** (déjà le cas), tous les textes via ngx-translate ; `fr.json` ⇔ `en.json` structurellement identiques.
- Tests backend : **E2E par les contrôleurs uniquement**, `@TestMethodOrder(OrderAnnotation)` + `@Order` + données persistantes entre méthodes ; classes IT étendent `org.pluribourse.shared.IntegrationTest` — **sauf `SaleConcurrencyIT`** (exception concurrence tolérée : `@SpringBootTest` autonome + Testcontainers, contrôle des frontières transactionnelles). Pas de Mockito hors composant externe.
- Couverture cible ≥ 80 % (back et front).
- MapStruct pour tout mapping entité↔DTO (les DTO existants suffisent ici ; aucun nouveau DTO n'est requis).

### Project Structure Notes

- Structure de packages réelle = `org.pluribourse.domain.{fonctionnalité}.{couche}` (segment `domain/` que `architecture.md` n'a jamais reporté). `shared/` = `org.pluribourse.shared.*`. Se fier au code.
- `architecture.md` documente `payout/PayoutController` + `PayoutService` : **obsolète** — le code réel a `domain/payout/controller/{SettlementController,AdminSettlementController}` + `SettlementService` + `SettlementReportPrintService`, pas de `PayoutService`.
- `architecture.md` ne connaît aucun « renderer » ni le module `archive/` : normal, gelé au 2026-06-09. `P14` de la SCP confirme « pipeline d'impression inchangé, seul le contenu des renderers évolue ».
- L'amendement `P13` d'`architecture.md` (§ Concurrence — POS, lignes 234-239 : ligne « Intégrité des lots » + complément exigence de test) est **déjà appliqué** — la story ne touche pas l'archi.
- Les Stories 3.14 (catégorie du lot) et 4.7 (refonte impression facture / liste des ventes) existent comme artefacts d'implémentation `done` mais **pas** dans `epics.md` (Epic 3 s'arrête à 3.13, Epic 4 à 4.6) — dérive documentaire assumée pour les stories issues de SCP. `Lot.category` (entité) et le patron auto-print de `payment-dialog.component` sont **bien présents dans le code**.
- `epics.md` n'a **pas** de section « Story 5.7 » ni « Story 5.8 » — cette story est synthétique (issue de la SCP), livrée sous la clé `5-8-post-vente-acces-depot-documents-vendeur-et-lots-partiels`.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-02b.md] — SCP intégrale : T1–T6, P1–P14, §5 parties A→E + points de conception figés + critères de succès
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.6] (L1231-1253 — bloc « il contient » + réimpression restreinte Dépôt ; L1248 tableau détail des lots ; L1253 `/volunteer/deposit` inaccessible en Post-vente)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.3] (L1553-1588 — L1576-1579 : bloc FR-109 scan rejeté au scan et à la validation)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.1] (L1669-1711 — L1703-1705 : case cochée par défaut, best-effort ; L1707-1710 : bouton réservé soldés/non réclamés)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.2] (L1713-1736 — L1723 : tableau unifié + statut + tableau détail + ligne de comptage + montant remis si soldé)
- [Source: _bmad-output/planning-artifacts/epics.md#UX Design Requirements] (L206 UX-DR22 amendé : case cochée par défaut + bouton visible soldés/non réclamés uniquement)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md] (FR-031 L212, FR-047 L239, FR-109 L242, FR-095 L250, FR-050 L252, FR-065 L284 — tous déjà amendés)
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS] (L234-239 : verrouillage optimiste `@Version` + contrainte unique BDD ; **rejet explicite du pessimiste** ; « Pas de réessai automatique » ; ligne « Intégrité des lots » P13 ; exigence de test Testcontainers MariaDB + scénario lot)
- [Source: _bmad-output/planning-artifacts/architecture.md#Infrastructure d'Impression] (L254-263, L788-792 : `LinkedBlockingQueue` en mémoire, au-plus-une-fois, redéclenchable UI FR-078, tout via `PrintQueueService`, sortie WebSocket → PrinterBridge — **figé**)
- [Source: _bmad-output/planning-artifacts/architecture.md] (L128 OpenPDF 3.0.0 ; L101 Java 21 ; L102 Spring Boot 4.0.6 ; L103 Angular 21 ; L445-452 exemple RFC 7807 `item-already-sold` 409)
- [Source: _bmad-output/implementation-artifacts/5-7-synchronisation-des-postes-de-soldage.md] (`settlement-list.component` : signals, `confirmSettle`, `loadSettlements(silent)`, tri déterministe, SSE `settlement-updated` — tout à préserver)
- [Source: pluribourse-backend/.../domain/item/service/PhaseGuard.java] (`requireDepositPhase` L16, `requireDepositOrPostSalePhase` L26, `requireDepositPhaseForSlipReprint` L40)
- [Source: pluribourse-backend/.../domain/item/service/DepositValidationService.java:77-85] (`resolveSellerDeposit` — garde de phase L79, seul appelant de `requireDepositOrPostSalePhase` ; `reprintDepositSlip` appelle en plus `requireDepositPhaseForSlipReprint` L64)
- [Source: pluribourse-backend/.../domain/seller/controller/SellerController.java:33-42] (`POST /api/sellers/{id}/deposit/labels/reprint` → `reprintLabels` L33-35 ; `POST /api/sellers/{id}/deposit/slip/reprint` → `reprintDepositSlip` L39-42 — les 2 endpoints visés par AC-A3, tous deux passant par `resolveSellerDeposit`)
- [Source: pluribourse-backend/.../domain/pos/service/PosScanService.java:44-53] (`scan` — garde `isSold` L50-52)
- [Source: pluribourse-backend/.../domain/pos/service/PosBasketService.java:124-203] (`validate` — pré-check `alreadySold` L143-149, boucle de flush L171-192)
- [Source: pluribourse-backend/.../domain/item/service/ItemPricing.java] (`computeTotal`, `distinctByLot` — **figés**)
- [Source: pluribourse-backend/.../domain/item/entity/Lot.java:29-50] (`category` LAZY `@ManyToOne` Story 3.14, `globalPrice`, `items` EAGER `@OrderBy id ASC`, `@Version` L49-50)
- [Source: pluribourse-backend/.../domain/item/entity/Item.java] (`sold` L52, `lot` LAZY nullable, `category` LAZY, `tableNumber` Integer, `price` nullable, `@Version` L63-65)
- [Source: pluribourse-backend/.../shared/exception/GlobalExceptionHandler.java] (`handleBusiness` → `type = https://pluribourse/errors/<errorCode>` ; `handleBasketValidationConflict` = patron données jointes)
- [Source: pluribourse-backend/.../shared/exception/BusinessException.java] (`super(HttpStatus, errorCode, message)`)
- [Source: pluribourse-backend/.../domain/print/service/DepositSlipRenderer.java] (OpenPDF, `buildItemsTable`, `distinctByLot`, helpers non partagés)
- [Source: pluribourse-backend/.../domain/print/service/SettlementReportRenderer.java] (normalisation `soldLotIds` L93-106, `buildSoldItemsTable`/`buildUnsoldItemsTable`, `if (amountPaid != null)` L130)
- [Source: pluribourse-backend/.../domain/payout/service/SettlementService.java:218-224] (`getAmountPaid` → `null` si != `SETTLED`)
- [Source: pluribourse-backend/src/main/resources/messages_fr.properties + messages_en.properties + messages.properties] (clés `print.slip.*` L12-18, `print.settlementReport.*` L26-38 ; défaut incomplet pour `print.slip.*`)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java] (patron 2 threads / Testcontainers / hors MockMvc)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java:196-220] (`@Order(5)` — assertions à recalculer) + `:342-351` (`@Order(13)`)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java:326-389] (`@Order(8)` + `@Order(9)` — assertions à recalculer)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java:128-140] (`@Order(1)` — phase gate à ajuster)
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/payment-dialog.component.ts:32 + .html:63-68] (patron case cochée par défaut)
- [Source: pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts:200-226] (`autoPrintInvoice` — patron best-effort découplé) + `:247-282` (`handleValidationError` / `handleScanError`)
- [Source: pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:156-267 + .html:77-140] (formulaire de solde, `printReport`, `confirmSettle`)
- [Source: pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts:14-21] (garde `DEPOSIT || POST_SALE`)
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html:204-274] (nav bénévole ; entrée deposit L208) + `.component.ts:18-21` (`PHASE_BOUND_VOLUNTEER_PATHS`)
- [Source: pluribourse-frontend/src/app/models/active-phase.enum.ts:20-40] (`resolveVolunteerLandingPath` + commentaire trompeur)
- [Source: pluribourse-frontend/public/i18n/fr.json:327-334] (bloc `volunteer.deposit.error` fusionné — T2 déjà corrigé) + `:487-502` (`volunteer.pos.error`) + `:890-948` (`settlement.*`)

### Latest tech information

Aucune nouvelle librairie ni montée de version. Java 21, Spring Boot 4.0.6 (Spring Framework 7 / Hibernate 7 — `LockModeType.OPTIMISTIC_FORCE_INCREMENT` et `EntityManager.lock` sont l'API JPA standard, inchangée), OpenPDF 3.0.0 (`org.openpdf.text.*`), Angular 21 (Signals, `MatCheckboxModule` déjà utilisé par `payment-dialog.component`), ngx-translate. Testcontainers `MariaDBContainer<>("mariadb:11")` déjà utilisé par `SaleConcurrencyIT`. Vitest (`npm test`) pour le front. `spring.liquibase.drop-first=true` pour la base H2 de test (pas de nouvelle migration à appliquer — aucune n'est créée).

### Git intelligence

Baseline attendue au démarrage de `dev-story` : HEAD de `main` (actuellement `6d24347 Fix code review`). Les 6 derniers commits suivent « un commit = une story complète » (6.3 prix lot catalogues, 5.7 synchro postes de soldage, 4.7 refonte impression facture, 3.14 catégorie lot, 2.9 devise, 2.10 préparation non exclusive). Recouvrements à surveiller :
- Story 4.7 a touché `app-layout.component.html` (nav phase, `/volunteer/sales`) et `settlement`… non : 4.7 = `sales-list`. Mais `app-layout.component.html` **a bien été modifié** par le dernier commit (`6d24347`, +34/-… sur le HTML) — la partie A modifie **une seule ligne** (l.208), pas de conflit mais fichier « chaud ».
- Story 5.7 a lourdement modifié `settlement-list.component.{ts,spec.ts}` (+124 / +223) — la partie B s'ajoute à `confirmSettle` (qui a déjà sa structure `try/catch/finally` + reload de rattrapage) et au `.spec` (700 tests front). Repartir de l'état actuel du fichier.
- Story 3.14 a introduit `Lot.category` + `034-archived-item-lot.xml` (6.3) — `Lot.category` est disponible et fetché indirectement via `Item.category` recopié.
- Aucune dépendance récente pertinente ajoutée.

### Questions pour l'auteur (non bloquantes — défaut retenu documenté)

1. **Partie C — mécanisme de concurrence lot** : `OPTIMISTIC_FORCE_INCREMENT` sur `Lot.@Version` (retenu, « même patron que `@Version` » demandé par P13). Alternative écartée : verrou pessimiste sur la ligne `Lot` (contraire à `architecture.md § Concurrence — POS` qui rejette explicitement le pessimiste). À confirmer avec l'auteur du proposal si un doute subsiste sur l'emplacement exact du flush.
2. **Partie B — spinner de `autoPrintReport`** : réutilise `printingReportForSellerId` (cohérence visuelle avec le bouton de ré-impression + blocage d'un double-print manuel). Le patron 4.7 (`autoPrintInvoice`) n'a pas d'équivalent ; on peut aussi ne rien afficher. Défaut retenu : réutiliser le signal.
3. **Partie E — position des nouveaux blocs dans le PDF bilan** : tableau unifié → tableau éclaté des lots (si ≥1 lot) → ligne de comptage → totaux → « Montant remis ». Non figé par la SCP ; ajuster si l'auteur préfère la ligne de comptage juste sous le tableau unifié.
4. **Partie D — position du tableau « détail des lots »** : après le tableau des articles, avant les totaux. Idem, non figé.

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (Claude Code, workflow bmad-dev-story)

### Debug Log References

- **Partie C — garde de concurrence lot, 1er échec de `SaleConcurrencyIT`** : l'approche `entityManager.lock(lot, OPTIMISTIC_FORCE_INCREMENT)` + `entityManager.flush()` (prescrite par la story) **ne lève rien dans `validate()`** : l'incrément forcé JPA est différé au `beforeTransactionCompletion` (`EntityIncrementVersionProcess.doBeforeTransactionCompletion`), donc l'échec (`JpaSystemException` ← `SnapshotIsolationException` ← `SQLException 1020` sur `lots`) remonte au **commit** du `TransactionTemplate`, hors du `try/catch` du service. Remplacé par un bulk JPQL `@Modifying` `LotRepository.bumpVersion(id, expectedVersion)` (`UPDATE Lot SET version = version + 1 WHERE id = ? AND version = ?`) exécuté immédiatement : prend le verrou d'écriture de ligne (sérialisation), le perdant matche 0 ligne ou échoue en 1020 → `LotAlreadySoldException`. `SaleConcurrencyIT` 2/2 vert après correction.

### Completion Notes List

- **A (T1)** — `depositPhaseGuard` resserré à `DEPOSIT` seul ; entrée sidebar `/volunteer/deposit` conditionnée `phase === 'DEPOSIT'` ; `PhaseGuard.requireDepositPhaseForSlipReprint` renommé `requireDepositPhaseForReprint`, `requireDepositOrPostSalePhase` supprimé, appel redondant retiré de `reprintDepositSlip` ; commentaires `active-phase.enum.ts` / `deposit-page.component.ts` (référence à l'ancienne méthode) rafraîchis. `ThermalLabelPrintingIT` : `@Order(1)` renommé + 2 nouveaux `@Order` (POST_SALE → 422 `deposit-reprint-not-allowed`, **première assertion réelle** de ce phase-gate pour les étiquettes). Partie A **commitée par l'utilisateur en cours de session** (`0749c3c`).
- **B (T5)** — `settlement-list` : signal `printReportOnSettle` (coché par défaut, réinit dans `closeSettleForm`), `autoPrintReport(sellerId)` best-effort appelé en `void` dans la branche succès de `confirmSettle` (patron `pos-page.autoPrintInvoice`), bouton « Imprimer le bilan » encadré `@if status !== 'UNSETTLED'`, `<mat-checkbox>` dans le formulaire inline + règle SCSS `flex-basis: 100%`. Nouvelle clé i18n `settlement.form.printReportOnSettle` (fr/en).
- **C (T3, FR-109)** — `LotAlreadySoldException` (409 `lot-already-sold`) ; `ItemRepository.existsByLotIdAndSoldTrue` ; garde au scan (`PosScanService.scan`) + garde au pré-check de `validate` + garde de concurrence via `LotRepository.bumpVersion` (voir Debug Log). Front `pos-page` : branche `lot-already-sold` dans `handleScanError` **et** `handleValidationError` → `volunteer.pos.error.lotAlreadySold` (fr/en). Prix global inchangé (`ItemPricing` intact).
- **D (T6)** — `DepositSlipRenderer.buildLotDetailTable` (3 col. Lot · Catégorie du lot · Article, 1 ligne/membre, sans prix, conditionnel ≥1 lot), catégorie via `item.getCategory()` (anti-`LazyInitializationException`). 4 clés `print.slip.*` × 3 fichiers `.properties`.
- **E (T4)** — `SettlementReportRenderer` : `buildSoldItemsTable`/`buildUnsoldItemsTable` remplacés par `buildUnifiedItemsTable` (5 col., statut de lot via `soldLotIds`) + `buildLotDetailTable` (5 col., statut réel par membre) + ligne de comptage (`Item.isSold()` brut, `vendus + invendus = déposés`). Normalisation `soldLotIds`/`soldItems` conservée pour statut lot + totaux ; totaux / « Montant remis » **inchangés**. 7 clés `print.settlementReport.*` × 3 fichiers ; `.soldSection`/`.unsoldSection` supprimées.
- **Vérifs** : backend `./mvnw clean package` 565/565 vert (jar produit, `SaleConcurrencyIT` exécuté), frontend `npm test` 717/717, `npm run build` sans warning, parité i18n OK. Aucune migration Liquibase, aucun nouvel endpoint / route / composant / dépendance. `EXPERIENCE.md` non amendé (dérive assumée). Base de dev locale non touchée. Vérification visuelle laissée à Manerial.

### File List

**Backend — modifiés (Partie A, déjà commitée dans `0749c3c`)**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java` *(commentaire `@Order(13)` ; les changements Partie D ci-dessous sont dans le working tree)*

**Backend — nouveaux**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/LotAlreadySoldException.java`

**Backend — modifiés (Parties C / D / E, working tree)**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/LotRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosScanService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/DepositSlipRenderer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SettlementReportRenderer.java`
- `pluribourse-backend/src/main/resources/messages.properties`
- `pluribourse-backend/src/main/resources/messages_fr.properties`
- `pluribourse-backend/src/main/resources/messages_en.properties`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosScanIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java`

**Frontend — modifiés (Partie A dans `0749c3c` ; B/C dans le working tree)**
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts` *(A)*
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.spec.ts` *(A)*
- `pluribourse-frontend/src/app/models/active-phase.enum.ts` *(A)*
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` *(A)*
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` *(A)*
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts` *(A — commentaire)*
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` *(B)*
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.html` *(B)*
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.scss` *(B)*
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts` *(B)*
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts` *(C)*
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts` *(C)*
- `pluribourse-frontend/public/i18n/fr.json` *(B + C)*
- `pluribourse-frontend/public/i18n/en.json` *(B + C)*

## Change Log

| Date | Version | Description |
|---|---|---|
| 2026-09-03 | 1.1 | Revue de code (bmad-code-review, 3 revues parallèles). 1 point decision-needed tranché par Manerial (option 3), 5 patchs **appliqués**, 7 différés (`deferred-work.md`), 10 rejetés. Patchs : (P5) tri des lots distincts par `id` dans `PosBasketService.validate()` — ordre de verrouillage canonique, supprime le deadlock 1213 (→ 500 non géré) ; timeout 1205 = risque résiduel commenté. (P1) nettoyage `PosBasketService` — Javadoc `isLotVersionRace` (retrait « em.lock()+flush path »), suppression du `catch (ObjectOptimisticLockingFailureException | OptimisticLockException)` mort sur le bulk `@Modifying`, import `OptimisticLockException` retiré. (P2) `DepositSlipRenderer` + `SettlementReportRenderer` : `buildLotDetailTable` trie les membres par (lot, numéro d'article) — plus d'entrelacement `Lot A / Lot B / Lot A`. (P3) `settlement-list.component.ts` : `printReportOnSettle.set(true)` aussi dans `openSettleForm()` (AC-B1 — bascule directe entre vendeurs). (P4) `settlement-list.component.html` : `<mat-checkbox>` déplacée avant les boutons dans le DOM (ordre de tabulation) + SCSS `order: 1` pour garder le rendu visuel sur sa propre ligne en bas. Vérifs : `./mvnw -o test-compile` OK, IT affectées vertes, `SaleConcurrencyIT` skippé (Docker absent), `npm test` 717/717, `npm run build` sans warning. Statut → done. Vérification visuelle toujours à faire par Manerial. |
| 2026-09-02 | 1.0 | Implémentation complète (bmad-dev-story), statut → review. Parties A→E livrées. **A** : `depositPhaseGuard` + sidebar Dépôt-seul, `PhaseGuard.requireDepositPhaseForSlipReprint` → `requireDepositPhaseForReprint`, `requireDepositOrPostSalePhase` supprimé ; `ThermalLabelPrintingIT` +2 `@Order` (POST_SALE → 422). **B** : `printReportOnSettle` signal + `autoPrintReport` best-effort dans `confirmSettle`, bouton bilan `@if status !== 'UNSETTLED'`, `<mat-checkbox>` + SCSS, clé `settlement.form.printReportOnSettle`. **C (FR-109)** : `LotAlreadySoldException` (409), `ItemRepository.existsByLotIdAndSoldTrue`, garde scan + garde pré-check `validate` + garde concurrence `LotRepository.bumpVersion` (bulk JPQL — l'`OPTIMISTIC_FORCE_INCREMENT` prescrit est différé au commit, non rattrapable ; documenté au Debug Log), front `handleScanError`/`handleValidationError`, clé `volunteer.pos.error.lotAlreadySold`, `SaleConcurrencyIT` + méthode lot. **D** : `DepositSlipRenderer.buildLotDetailTable` (3 col., conditionnel), 4 clés `print.slip.*` × 3 `.properties`. **E** : `SettlementReportRenderer` tableau unifié + éclaté + ligne de comptage, 7 clés `print.settlementReport.*` × 3 `.properties`, `.soldSection`/`.unsoldSection` supprimées ; totaux et « Montant remis » inchangés. Vérifs : backend 565/565, frontend 717/717, `npm run build` sans warning, parité i18n OK. Aucune migration Liquibase, aucun nouvel endpoint/route/composant/dépendance. Partie A commitée par l'utilisateur en cours de session (`0749c3c`, parent `6d24347`). |
| 2026-09-02 | 0.2 | Passe de validation (bmad-create-story `validate`) : références code/tests recoupées contre le code réel (back + front + IT) — toutes exactes à ±2 lignes, fixtures et valeurs d'assertion conformes, SCP fidèlement reprise. 7 ajustements appliqués : (1) Partie C — emplacement figé du lock+flush `Lot` (après `saleRepository.save`, avant la boucle `setSold`) comme unique point de sérialisation ; (2) règle i18n `.properties` unifiée (3 fichiers pour toute clé ajoutée ; backfill pré-existant hors périmètre) + AC-Z resserré ; (3) `ThermalLabelPrintingIT` — `@Order(1)` assert en fait 404 `no-active-edition` (PREPARATION), le nouveau test doit avancer explicitement en POST_SALE ; (4) Partie E — garde-fou statut ligne de lot via `soldLotIds`, jamais `representative.isSold()` ; (5) note « toasts = chaîne résolue via `translate.instant`, pas la clé » ; (6) référence `SellerController` (endpoints AC-A3) ajoutée ; (7) ordre prose `handleValidationError`/`handleScanError`. Aucun blocage — story confirmée `ready-for-dev`. |
| 2026-09-02 | 0.1 | Story 5.8 créée via bmad-create-story (analyse parallèle : epics/PRD, architecture, code POS, renderers PDF, frontend). Story unique multi-parties A→E issue de la SCP 2026-09-02b : (A) retrait accès Dépôt en Post-vente — 1 ligne HTML + `depositPhaseGuard` + `DepositValidationService`/`PhaseGuard` (garde Dépôt seul, slug `deposit-reprint-not-allowed` conservé) ; (B) case « Imprimer le bilan » cochée par défaut dans le formulaire de solde + `autoPrintReport` best-effort (patron 4.7) + bouton bilan masqué si `UNSETTLED` ; (C) FR-109 — `LotAlreadySoldException` (409 `lot-already-sold`), `ItemRepository.existsByLotIdAndSoldTrue`, garde scan + garde validation + garde concurrence `Lot.@Version` FORCE_INCREMENT, front `handleScanError`/`handleValidationError`, `SaleConcurrencyIT` (nouvelle méthode) ; (D) `DepositSlipRenderer` tableau « détail des lots » (3 col., sans prix, conditionnel, catégorie via `item.getCategory()` anti-LazyInit) ; (E) `SettlementReportRenderer` tableau unifié + colonne statut + tableau éclaté + ligne de comptage (`Item.isSold()` brut), totaux et « Montant remis » inchangés. Amendements PRD/epics/architecture déjà appliqués (P1–P13). T2 (i18n dupliquée) déjà corrigé hors story. Aucune migration Liquibase, aucun nouvel endpoint/route/composant/dépendance. Statut → ready-for-dev. |
