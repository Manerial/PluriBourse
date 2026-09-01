---
baseline_commit: 3db2f9f6c6d4a55893357d968af570a92f242679
---

# Story 5.7 : Synchronisation des postes de soldage

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que bénévole ou admin utilisant l'écran de solde depuis plusieurs postes simultanément,
je veux qu'un vendeur soldé (ou marqué « Non réclamé ») sur un poste disparaisse immédiatement des autres postes, et qu'une tentative de double-traitement du même vendeur échoue avec une erreur claire plutôt qu'une erreur générique,
afin qu'aucun vendeur ne soit payé deux fois et que les postes ne travaillent jamais sur une liste périmée.

## Contexte & origine

Issue du **sprint change proposal 2026-08-24, point 7** (regroupé seul dans sa propre story, cf. plan d'action du proposal). `epics.md` n'a **pas** été amendé pour ce point — les exigences ci-dessous font foi.

**Constat code (vérifié) :**

- `SettlementService.settle()` / `markUnclaimed()` font un contrôle lecture (`requireNotAlreadySettled`) puis une écriture, sans verrou applicatif. Le filet réel est la contrainte `uk_settlements_seller_profile` (changelog `024-settlements.xml`) : `persistSettlement` fait `saveAndFlush` et **catche déjà** `DataIntegrityViolationException` → `SellerAlreadySettledException` (HTTP **409**, code `seller-already-settled`). Le double-solde **séquentiel** est donc déjà couvert (`SettlementIT` @Order(5)).
- Ce qui **manque réellement** : (a) aucune synchro temps réel de la liste `/volunteer/settlement` ni `/admin/settlement` entre postes (contrairement au chip de phase, SSE, ARCH-012) ; (b) le poste perdant d'une course affiche un toast **générique** (`settlement.error.settle`) au lieu d'un message spécifique, et sa ligne reste périmée à l'écran ; (c) aucun test ne prouve la course concurrente réelle (deux threads).

## Décision de conception

**TL;DR :** pas de verrou ajouté ; la contrainte `UNIQUE` + le catch `DataIntegrityViolationException` déjà en place = le 409 propre exigé par NFR-008. Cette story ajoute l'évènement SSE `settlement-updated`, le message frontend spécifique, et les tests qui prouvent le chemin de conflit.

Le proposal évoque « ajout d'un verrouillage explicite avec 409 propre ». **Cette story n'ajoute PAS de verrou pessimiste** (`SELECT … FOR UPDATE`) ni de nouveau `@Version`. Argumentaire :

1. La création d'un `Settlement` est un **INSERT unique** (pas un cycle lecture-modification-écriture comme la vente d'`Item`). La contrainte `UNIQUE` sur `seller_profile_id` est donc une **garantie dure** ici, pas un simple « filet de sécurité » : deux INSERT concurrents pour le même vendeur → un seul réussit, l'autre lève `DataIntegrityViolationException`, déjà traduite en `SellerAlreadySettledException` (409, code métier explicite `seller-already-settled`).
2. `architecture.md § Concurrence — POS` a **explicitement rejeté** le verrouillage pessimiste (verrous maintenus, interblocages) au profit de l'optimiste + contrainte unique. Ajouter un verrou de ligne sur `seller_profiles` ici irait à l'encontre de cette décision d'architecture pour un gain nul (le 409 propre est déjà là).
3. NFR-008 exige littéralement : « Le second poste reçoit une erreur 409 explicite (même patron que NFR-002 pour la caisse), **pas une erreur générique** ». Le **statut HTTP + code métier** sont déjà conformes. Le vrai écart NFR-008 est **côté frontend** (message générique) — c'est là que porte le correctif (Task 5).

La story **durcit et prouve** le chemin de conflit existant (Javadoc + test de concurrence réelle), au lieu d'empiler un mécanisme redondant.

## Acceptance Criteria

### AC1 — Double-traitement concurrent du même vendeur → 409 explicite

**Étant donné** qu'un vendeur non soldé est visible sur deux postes de soldage
**Quand** les deux postes valident un `settle` (ou un `markUnclaimed`, ou un mélange des deux) pour ce vendeur quasi simultanément
**Alors** exactement un `Settlement` est persisté
**Et** le second poste reçoit une réponse HTTP **409** dont `type` se termine par `/seller-already-settled` — jamais un 500, jamais un 409 générique `concurrent-modification`

### AC2 — Message utilisateur spécifique + auto-correction de la ligne

**Étant donné** que l'action locale (`confirmSettle` ou `confirmUnclaimed`) reçoit un 409 `seller-already-settled`
**Quand** l'erreur est traitée côté frontend
**Alors** un toast d'erreur affiche **`settlement.error.alreadySettled`** (« Ce vendeur a déjà été soldé. » — clé déjà présente en `fr.json`/`en.json`), **pas** `settlement.error.settle`
**Et** la liste est rechargée depuis le serveur (rechargement silencieux, sans skeleton plein écran) pour que la ligne du vendeur reflète son statut réel
**Et** un échec de ce rechargement silencieux est absorbé sans vider la liste affichée ni déclencher la bannière `error()` (la liste courante reste à l'écran)
**Et** si le formulaire de solde inline est ouvert pour ce vendeur, il est fermé

### AC3 — Émission SSE `settlement-updated` à chaque solde / non-réclamé

**Étant donné** qu'un `settle` ou un `markUnclaimed` est **committé avec succès**
**Quand** la transaction est validée
**Alors** un évènement SSE nommé **`settlement-updated`** est diffusé à tous les émetteurs enregistrés via `SseEmitterRegistry.broadcast`, dans un `TransactionSynchronization.afterCommit()` (jamais avant le commit — même patron que `EditionService.savePhaseThenSendEvent`)
**Et** la charge utile porte au minimum `editionId` (Long) et `sellerId` (Long)

**Étant donné** qu'un `settle` échoue (422 montant, 409 déjà soldé, 404 mauvaise édition, transaction rollback)
**Quand** la requête se termine
**Alors** **aucun** évènement `settlement-updated` n'est émis
**Et** ce résultat est trivial pour les échecs 404 / rollback (l'enregistrement de la synchro `afterCommit` est fait *après* `persistSettlement`, donc jamais atteint) : seul le cas 422 fait l'objet d'un test dédié (Task 6), les cas 404 / rollback n'en exigent pas

### AC4 — Mise à jour temps réel de la liste sur les autres postes

**Étant donné** que plusieurs postes affichent `/volunteer/settlement` ou `/admin/settlement`
**Quand** un poste solde (ou marque « Non réclamé ») un vendeur
**Alors** les listes des **autres** postes se mettent à jour sans rechargement de page : le vendeur prend le nouveau statut et sort du filtre « Non soldés » actif
**Et** l'ordre d'affichage des lignes reste stable d'un rechargement au suivant (tri déterministe côté client — cf. Task 4 — puisque `GET /api/settlements` ne garantit aucun ordre) : aucun réordonnancement visible des lignes à chaque évènement distant
**Et** le poste **à l'origine** de l'action ne subit ni double mise à jour visible ni scintillement (sa mise à jour optimiste locale `applyUpdate` reste la source de vérité pour lui ; l'évènement SSE reçu en écho est absorbé sans reflet visible)

### AC5 — Robustesse de la souscription SSE

**Étant donné** que `SettlementListComponent` est détruit (navigation hors page)
**Alors** la souscription SSE est fermée (`takeUntilDestroyed` → `source.close()`), aucune connexion `EventSource` fuitée

**Étant donné** qu'un évènement `settlement-updated` malformé (JSON invalide, ou objet sans `sellerId`/`editionId` numériques) arrive
**Alors** il est ignoré silencieusement (type guard `isSettlementUpdatedEvent`, même patron que `isBasketCancelledEvent`)

### AC6 — Non-régression des flux existants

- Solde **en-dessous** du montant dû : toujours accepté avec avertissement (`SettlementIT` @Order(3)) — inchangé
- Solde **au-dessus** du montant dû : toujours 422 `invalid-settlement-amount` (@Order(4)) — inchangé
- Solde **séquentiel** d'un vendeur déjà soldé : toujours 409 `seller-already-settled` (@Order(5)) — inchangé
- `markUnclaimed` séquentiel : toujours 200 puis 409 au second appel — inchangé
- Endpoint hors Post-vente : toujours 422 `settlement-not-allowed` (@Order(7)) — inchangé
- Vendeur d'une autre édition : toujours 404 `seller-not-found` (@Order(9)) — inchangé
- La **fermeture d'édition** (`EditionClosingService` → `closeAllUnsettledAsUnclaimed`, story 2.7) **n'émet pas** `settlement-updated` : le `phase-changed` POST_SALE→CLOSED est déjà diffusé, `getSettlements()` renvoie alors 422 hors Post-vente, et le guard/redirection retire déjà les postes de la page — un `settlement-updated` viserait une liste que plus personne ne peut afficher. `EditionClosingIT` reste vert sans modification.

### AC7 — Test de concurrence réelle

Un test dédié prouve AC1 sous une **vraie course** entre deux transactions, calqué sur `SaleConcurrencyIT` :

- deux threads, chacun pilotant son `TransactionTemplate`, `CountDownLatch` de départ commun
- `@Testcontainers(disabledWithoutDocker = true)` + `MariaDBContainer<>("mariadb:11")` (H2 a une sémantique de contrainte/lock différente — cf. Javadoc de `SaleConcurrencyIT`)
- **hors MockMvc / hors contrôleur** : exception « technique » assumée à la philosophie E2E-par-contrôleur (CLAUDE.md), **déjà actée pour la story 4.4** (`SaleConcurrencyIT`) pour exactement la même raison (contrôle des frontières transactionnelles)
- assertions : exactement 1 `Settlement` en base pour ce vendeur ; l'autre `future.get()` lève une `ExecutionException` dont la cause est `SellerAlreadySettledException`
- le test E2E de comportement (statut en base après action, via `SettlementIT`) reste la couverture principale ; ce test de concurrence **s'y ajoute**, il ne le remplace pas.

## Tasks / Subtasks

- [x] **T1 — Backend : évènement SSE `settlement-updated`** (AC: 3, 6)
  - [x] Créer `org.pluribourse.domain.payout.dto.SettlementUpdatedEventDto` — `record SettlementUpdatedEventDto(Long editionId, Long sellerId)`. Miroir de `org.pluribourse.domain.edition.dto.PhaseChangedEventDto` / `BasketCancelledEventDto`. Javadoc courte : « Diffusé après commit d'un settle/markUnclaimed pour que les écrans de solde des autres postes se rafraîchissent (ARCH-017, story 5.7). »
  - [x] `SettlementService` : injecter `SseEmitterRegistry sseEmitterRegistry` (constructeur, via `@RequiredArgsConstructor` — ajouter le champ `final`).
  - [x] Dans `settle()` **et** `markUnclaimed()` : `settle()` finit aujourd'hui par `return persistSettlement(...)` (instruction unique) — capturer le résultat dans une variable locale avant d'enregistrer la synchro, exactement comme `savePhaseThenSendEvent` fait `Edition saved = repository.save(...)` → `registerSynchronization(...)` → `return mapper.toDto(saved)`. Soit : `SettlementDto result = persistSettlement(...); TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { sseEmitterRegistry.broadcast("settlement-updated", new SettlementUpdatedEventDto(edition.getId(), seller.getId())); } }); return result;`. Copier **exactement** le patron de `EditionService.savePhaseThenSendEvent` (imports `org.springframework.transaction.support.TransactionSynchronization` / `TransactionSynchronizationManager`).
  - [x] **Ne pas** émettre depuis `closeAllUnsettledAsUnclaimed` (justifié AC6). **Ne pas** émettre depuis `getSettlementsForEdition` / lectures.
  - [x] Ne toucher à **aucune** garde, calcul (`computeAmountDue`), ni logique de persistance existante.
- [x] **T2 — Backend : durcir + documenter le chemin de conflit** (AC: 1)
  - [x] Vérifier (sans le modifier) que `persistSettlement` catche `DataIntegrityViolationException` → `SellerAlreadySettledException`. C'est déjà le cas.
  - [x] Compléter le Javadoc de `persistSettlement` (et/ou `settle`) : citer **NFR-008** explicitement et renvoyer au nouveau test de concurrence (`SettlementConcurrencyIT`). Formuler que la contrainte `uk_settlements_seller_profile` est ici une garantie dure (INSERT unique, pas un read-modify-write) et non un simple filet.
  - [x] **Aucune migration Liquibase** — `uk_settlements_seller_profile` existe déjà (changelog `024-settlements.xml`). **Aucun** champ `Settlement.user` (hors périmètre, cf. item différé de la revue 5-1).
- [x] **T3 — Frontend : `SseService.settlementUpdated()`** (AC: 4, 5)
  - [x] `models/settlement.model.ts` : ajouter `export interface SettlementUpdatedEvent { editionId: number; sellerId: number; }`. (Choix : dans `settlement.model.ts` et non `edition.model.ts` — c'est un évènement de solde, pas de cycle de vie d'édition ; note laissée en commentaire pour le prochain qui cherchera à côté des autres `*Event`.)
  - [x] `services/sse.service.ts` : ajouter `isSettlementUpdatedEvent` (type guard, miroir exact de `isBasketCancelledEvent` : objet non-null, `editionId` number, `sellerId` number) et `settlementUpdated(): Observable<SettlementUpdatedEvent> { return this.listen('settlement-updated', isSettlementUpdatedEvent); }`.
- [x] **T4 — Frontend : `SettlementListComponent` réagit au SSE distant** (AC: 4, 5)
  - [x] Injecter `SseService` et `DestroyRef` (import `takeUntilDestroyed` de `@angular/core/rxjs-interop`, patron `app-layout.component.ts`).
  - [x] Dans le constructeur (ou `ngOnInit`) : `this.sseService.settlementUpdated().pipe(auditTime(250), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.onRemoteSettlementUpdate());`
  - [x] `private onRemoteSettlementUpdate(): void` → si `this.submitting()` est `true`, **ne rien faire** (l'action locale en cours + `applyUpdate` font foi pour ce poste ; le reload de rattrapage de Task 5 comblera ce qui a été ignoré) ; sinon appeler un rechargement **silencieux**.
  - [x] Extraire de `loadSettlements()` une variante `loadSettlements(silent = false)` : en mode `silent`, **ne touche ni `isLoading` ni le skeleton**, et **un échec n'appelle PAS `this.error.set(...)`** — la liste actuellement affichée est conservée telle quelle, l'erreur est avalée silencieusement (au plus un `console.debug`). Rationale : un rechargement de fond déclenché par l'action d'un autre poste ne doit jamais vider l'écran ni afficher la bannière `error()` (ex. course avec une clôture d'édition qui fait passer `GET /api/settlements` en 422). Le mode non-silencieux (`ngOnInit`, `error()` plein écran) est inchangé.
  - [x] **Tri déterministe** : `GET /api/settlements` (`getSettlementsForEdition`) ne garantit aucun ordre (cf. Javadoc de `getSellersMatchingFilter`). Aujourd'hui invisible (chargé une fois) ; avec un reload SSE à chaque solde distant, l'ordre des lignes pourrait varier → scintillement sur tous les postes. Trier `filteredSettlements` (ou un `computed` intermédiaire) par `lastName` puis `firstName` (le `SettlementDto` ne porte pas `sellerNumber`). Tri **côté client uniquement** — ne pas ajouter d'`ORDER BY` backend (hors périmètre, aucun AC serveur ne l'exige, éviterait de retoucher un service stable).
  - [x] Vérifier que le `@for ... track settlement.sellerId` existant suffit à éviter tout re-render brutal (il suffit).
- [x] **T5 — Frontend : message spécifique 409 sur action locale + rattrapage** (AC: 2, 4)
  - [x] `confirmSettle` : adapter le `catch {}` en `catch (err: unknown) {}`. Si `err instanceof HttpErrorResponse && err.status === 409 && extractErrorType(err)?.endsWith('/seller-already-settled')` → `toast.showError(translate.instant('settlement.error.alreadySettled'))` + `closeSettleForm()`. Sinon, comportement actuel inchangé (`toast.showError(translate.instant('settlement.error.settle'))`).
  - [x] `confirmUnclaimed` : même branche 409 dans son `catch` ; ne fermer le formulaire que s'il est ouvert pour ce vendeur (`if (this.openSettleFormForSellerId() === settlement.sellerId)`, garde déjà présente dans le chemin succès).
  - [x] **Reload de rattrapage** : dans le `finally` de `confirmSettle` **et** `confirmUnclaimed`, après `submitting.set(false)`, appeler `loadSettlements(true)` (silencieux). Couvre deux besoins : (a) réaligner la ligne sur l'état serveur réel après un 409 `seller-already-settled` (AC2) ; (b) rattraper les évènements `settlement-updated` distants ignorés par `onRemoteSettlementUpdate()` pendant que `submitting()` était `true` (AC4). Idempotent (`track sellerId` + tri déterministe), sans skeleton, sans bannière d'erreur.
  - [x] `settlement.error.alreadySettled` existe déjà en `fr.json` et `en.json` — **aucune** nouvelle clé i18n.
- [x] **T6 — Tests backend** (AC: 1, 3, 6, 7)
  - [x] **Créer `SettlementSyncIT`** (nouveau storyboard E2E, package `domain.payout`, étend `org.pluribourse.shared.IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`) : édition → DEPOSIT → 2 vendeurs, 1 article vendu → POST_SALE, puis les scénarios SSE ci-dessous. Patron « nouveau fichier plutôt qu'étendre un storyboard stable », déjà appliqué stories 5.5/5.6. *(Alternative rejetée : ajouter des `@Test @Order(12+)` à `SettlementIT` après `@Order(11)` — écartée car `@Order(9)` y clôt l'édition 1 puis crée l'édition 2, `@Order(11)` lit le rapport de l'édition 1 close : les nouveaux tests devraient jongler avec le vendeur de l'édition 2 active, fragile.)*
    - `settle` émet `settlement-updated` : ouvrir `GET /api/sse/events` en async (`request().asyncStarted()`, patron `PosBasketCancellationIT` @Order(2)), POST `/settlements/{sellerId}/settle`, asserter `sse.getResponse().getContentAsString()` contient `settlement-updated`, `"sellerId":<id>`, `"editionId":<id>`.
    - `markUnclaimed` émet `settlement-updated` (même technique).
    - un `settle` **rejeté** (montant > dû → 422) **n'émet pas** `settlement-updated` (asserter `doesNotContain("settlement-updated")` sur le flux, patron `PosBasketCancellationIT` @Order(3)/(5)).
  - [x] `SettlementConcurrencyIT` — **nouveau**, package `domain.payout`, calqué **ligne à ligne** sur `org.pluribourse.domain.pos.SaleConcurrencyIT` : `@SpringBootTest` + `@Testcontainers(disabledWithoutDocker = true)` + `@DirtiesContext(AFTER_CLASS)`, `MariaDBContainer<>("mariadb:11")` + `@ServiceConnection`, fixtures committées (jamais `@Transactional` sur classe/méthode), 2 threads via `ExecutorService` + `TransactionTemplate` + `CountDownLatch`. Scénario : édition POST_SALE, 1 vendeur avec 1 article vendu (net dû connu) ; deux threads appellent `settlementService.settle(sellerId, new SettleDto(montant))` (l'un avec le montant dû, l'autre avec un montant valide ≤ dû) ; asserter `successCount == 1`, l'`ExecutionException` de l'autre a pour cause `SellerAlreadySettledException`, `settlementRepository.findBySellerProfileId(sellerId)` présent et unique, `settlementRepository.count() == 1`. Ajouter une variante `settle` vs `markUnclaimed` concurrents si le coût est faible (même garantie).
  - [x] Pas de test de service isolé sur `SettlementService` en dehors de `SettlementConcurrencyIT` (qui est l'exception concurrence déjà tolérée).
- [x] **T7 — Tests frontend** (AC: 2, 4, 5)
  - [x] `sse.service.spec.ts` : +2 tests miroir des tests `basket-cancelled` existants — émet un `SettlementUpdatedEvent` parsé sur message `settlement-updated` ; ignore un payload objet sans `sellerId` numérique.
  - [x] `settlement-list.component.spec.ts` : 
    - un `settlement-updated` reçu déclenche un rechargement silencieux (spy sur `SettlementService.getSettlements`, pas de skeleton `isLoading`) ; ignoré si `submitting()` est `true`.
    - un rechargement silencieux qui **échoue** (service renvoie une erreur) ne vide pas `settlements()` et ne met pas `error()` — la liste précédente reste affichée.
    - la liste rendue est triée de façon déterministe (`lastName` puis `firstName`) : deux réponses `getSettlements` dans des ordres différents produisent le même ordre affiché.
    - `confirmSettle` : un 409 `seller-already-settled` renvoyé par le service → toast `settlement.error.alreadySettled` (spy `ToastService.showError` + `TranslateService.instant`), formulaire fermé ; un 422 conserve le message `settlement.error.settle`.
    - `confirmSettle` / `confirmUnclaimed` : un `loadSettlements(true)` de rattrapage est déclenché dans le `finally` (spy `getSettlements`, y compris après un succès).
    - `confirmUnclaimed` : même assertion 409 ; formulaire fermé seulement s'il était ouvert pour ce vendeur.
    - la souscription SSE est fermée à la destruction du composant (mock `SseService.settlementUpdated` renvoyant un `Subject`, vérifier `takeUntilDestroyed`).
- [x] **T8 — Vérifications finales**
  - [x] `./mvnw -q clean package` (backend) : suite verte. Noter le delta de comptage (`SettlementConcurrencyIT` **skippé** si Docker absent — ne pas compter comme échec, cf. `SaleConcurrencyIT`).
  - [x] `npm test` dans `pluribourse-frontend/` : suite verte, delta de comptage noté.
  - [x] `npm run build` (frontend) : sans erreur ni warning.
  - [x] Vérification visuelle humaine de `/volunteer/settlement` et `/admin/settlement` en double onglet **laissée à Manerial** (CLAUDE.md — jamais fait par l'agent).

## Dev Notes

### Périmètre exact

| Dans le périmètre | Hors périmètre |
|---|---|
| Évènement SSE `settlement-updated` émis par `settle` + `markUnclaimed` | Émission depuis `closeAllUnsettledAsUnclaimed` / fermeture d'édition (AC6) |
| Rafraîchissement temps réel de `SettlementListComponent` (les deux routes) | Nouvel endpoint `GET /settlements/{sellerId}` — rechargement complet suffit (~100 lignes en mémoire, pas de pagination, NFR-001) |
| Toast 409 spécifique + auto-correction de ligne côté frontend | Verrou pessimiste / `SELECT … FOR UPDATE` / nouveau `@Version` (cf. Décision de conception) |
| Tri client déterministe de la liste (`lastName`, `firstName`) pour stabiliser l'ordre entre rechargements SSE | `ORDER BY` backend sur `getSettlementsForEdition` (service stable, aucun AC serveur ne l'exige) |
| Test de concurrence réelle (`SettlementConcurrencyIT`) | `Settlement.user` (traçabilité de l'acteur — item différé revue 5-1, aucun AC ne l'exige) |
| Javadoc NFR-008 sur `persistSettlement`/`settle` | Rafraîchissement SSE de `/admin/reports` ou d'autres écrans |
| Réutilisation de `settlement.error.alreadySettled` (déjà en i18n) | Nouvelles clés i18n |

### Fichiers à créer / modifier

**Backend**
- `domain/payout/dto/SettlementUpdatedEventDto.java` — **NOUVEAU** (record `editionId`, `sellerId`)
- `domain/payout/service/SettlementService.java` — **MODIFIÉ** : champ `final SseEmitterRegistry`, bloc `afterCommit` dans `settle()` et `markUnclaimed()`, Javadoc NFR-008
- `src/test/java/org/pluribourse/domain/payout/SettlementSyncIT.java` — **NOUVEAU** (storyboard E2E : émission / non-émission SSE)
- `src/test/java/org/pluribourse/domain/payout/SettlementConcurrencyIT.java` — **NOUVEAU** (Testcontainers, 2 threads)

**Frontend**
- `src/app/models/settlement.model.ts` — **MODIFIÉ** : `SettlementUpdatedEvent`
- `src/app/services/sse.service.ts` — **MODIFIÉ** : `isSettlementUpdatedEvent`, `settlementUpdated()`
- `src/app/features/settlement/settlement-list.component.ts` — **MODIFIÉ** : souscription SSE, `onRemoteSettlementUpdate`, `loadSettlements(silent)` (échec avalé en mode silencieux), tri client déterministe de la liste, reload de rattrapage dans le `finally` de `confirmSettle`/`confirmUnclaimed`, gestion 409 dans `confirmSettle`/`confirmUnclaimed`
- `src/app/services/sse.service.spec.ts` — **MODIFIÉ** (+2)
- `src/app/features/settlement/settlement-list.component.spec.ts` — **MODIFIÉ**

**Aucun** changement : `settlement-list.component.html`/`.scss` (le `@for track sellerId` existant suffit), `SettlementController`, `AdminSettlementController`, `SettleDto`, `Settlement` entité, `settlement.service.ts` (frontend), fichiers i18n, changelogs Liquibase, `EditionClosingService`.

### État actuel des fichiers UPDATE (à préserver)

**`SettlementService.java`** — service `@Transactional`, dépend de `SellerRepository`, `SettlementRepository`, `ItemRepository`, `EditionService`. Méthodes publiques : `getSettlements` (readOnly, garde Post-vente), `getSettlementsForEdition`, `getAssociationRetainedTotal`, `getSellersMatchingFilter`, `closeAllUnsettledAsUnclaimed` (fermeture d'édition — **ne pas** émettre ici), `settle`, `markUnclaimed`, `getAmountPaid(BySellerId)`, `getSettledPayoutTotal`. `settle`/`markUnclaimed` suivent le même squelette : `getActiveEdition()` → `PhaseGuard.requirePostSalePhase` → `requireSellerOfEdition` (IDOR-safe, 404 générique) → `requireNotAlreadySettled` (409) → `computeAmountDue` → (settle seulement : garde montant > dû → 422) → `persistSettlement(...)`. `persistSettlement` fait `saveAndFlush` + catch `DataIntegrityViolationException` → `SellerAlreadySettledException`. **Ne rien changer à cette chaîne** ; ajouter uniquement le `afterCommit` en fin de `settle`/`markUnclaimed`.

**`settlement-list.component.ts`** — composant standalone unique pour `/volunteer/settlement` **et** `/admin/settlement` (distingués par `isAdmin()`). Signals : `settlements`, `isLoading`, `error`, `submitting`, `statusFilter`, `filteredSettlements` (computed), `openSettleFormForSellerId`, `printingReportForSellerId`, `printingAll`, `anyPrintInFlight`. `confirmSettle`/`confirmUnclaimed` : `submitting.set(true)` → appel service → `applyUpdate(updated)` (remplace la ligne locale) → toast succès → `finally submitting.set(false)`. `catch` **actuellement générique** (`catch {}` → `settlement.error.settle`). `loadSettlements()` : `isLoading.set(true)` → `getSettlements()` → `settlements.set(...)` (aucun tri appliqué aujourd'hui — l'ordre vient du backend, non garanti ; cf. Task 4). `applyUpdate(updated)` : `settlements.update(list => list.map(s => s.sellerId === updated.sellerId ? updated : s))`. **Préserver** : gardes `anyPrintInFlight`, mise à jour optimiste `applyUpdate` (source de vérité pour le poste actif), toute la logique `openSettlement`/`warningBelowDue`/`blockedAboveDue`.

**`sse.service.ts`** — `listen<T>(eventName, isValid)` ouvre **une** `EventSource('/api/sse/events', { withCredentials: true })` par souscription, `addEventListener(eventName, ...)`, `JSON.parse` + type guard, `source.onerror` gère la déconnexion permanente (`readyState === CLOSED` → `clearSession` + redirect login). Méthodes existantes : `phaseChanges()`, `basketCancelled()`. Ajouter `settlementUpdated()` sur le même modèle.

### Patrons à réutiliser (ne pas réinventer)

| Besoin | Source de référence |
|---|---|
| Broadcast SSE après commit | `EditionService.savePhaseThenSendEvent` (`TransactionSynchronizationManager.registerSynchronization` + `afterCommit` → `sseEmitterRegistry.broadcast("phase-changed", dto)`) |
| DTO d'évènement SSE | `domain/edition/dto/PhaseChangedEventDto`, `BasketCancelledEventDto` (records) |
| Méthode + type guard `SseService` | `basketCancelled()` / `isBasketCancelledEvent` |
| Souscription SSE dans un composant | `app-layout.component.ts` : `this.sseService.phaseChanges().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(...)` |
| Assertion SSE en test E2E | `PosBasketCancellationIT` @Order(2)/(3)/(5) : `GET /api/sse/events` + `request().asyncStarted()` + `sse.getResponse().getContentAsString()` `.contains(...)` / `.doesNotContain(...)` |
| Test de course concurrente réelle | `pos/SaleConcurrencyIT` (Testcontainers MariaDB, 2 threads, `TransactionTemplate`, `disabledWithoutDocker = true`, hors MockMvc — exception « technique » assumée) |
| Détection d'un type d'erreur RFC 7807 côté frontend | `extractErrorType(err)?.endsWith('/...')` (déjà utilisé dans `printReport`/`printAllReports` du même composant) |
| Storyboard de test dédié vs extension | Stories 5.5/5.6 : nouveau fichier `*IT` plutôt que d'alourdir un storyboard stable |

### Interprétation « canal SSE dédié » (ARCH-017)

Le code ne possède **qu'un seul flux** SSE (`/sse/events`, `SseEmitterRegistry` unique) sur lequel coexistent déjà `phase-changed` et `basket-cancelled` comme évènements **nommés**. « Canal dédié » se réalise donc comme un **évènement nommé dédié** (`settlement-updated`) + payload dédié (`SettlementUpdatedEventDto`) + méthode `SseService` dédiée — exactement comme `basket-cancelled` a été ajouté à la story 2.8. Pas de nouvel endpoint ni de nouveau registre.

### Anti-boucle côté poste émetteur (AC4)

Le poste qui solde reçoit son propre `settlement-updated` en écho (broadcast à *tous* les émetteurs). Absorption : `onRemoteSettlementUpdate()` **ne fait rien si `submitting()` est `true`**, et de toute façon `applyUpdate` a déjà posé la bonne valeur localement. Le `loadSettlements(true)` de rattrapage placé dans le `finally` de `confirmSettle`/`confirmUnclaimed` (Task 5) confirme alors la même donnée (idempotent, pas de scintillement grâce au `track sellerId` + tri déterministe) et rattrape au passage les évènements distants ignorés pendant `submitting()`. `auditTime(250)` amortit une éventuelle rafale (soldages concurrents sur ~3 postes — volume faible ; la fermeture d'édition n'émet pas).

### Contraintes projet applicables

- Montants : `BigDecimal` partout, jamais `float`/`double` (NFR-003) — le nouveau code ne fait pas de calcul monétaire, il transporte des `Long` d'ID.
- Aucune donnée perso (nom, email, téléphone vendeur) dans les logs : `SettlementUpdatedEventDto` ne porte que des IDs — OK. Ne pas logger le DTO avec un `SellerProfile`.
- Type explicite des variables backend, **jamais** `var` ; accolades obligatoires sur tout `if`/`for` même mono-ligne.
- Frontend : composant standalone, Signals (pas de NgRx), **template HTML séparé** (déjà le cas, non modifié), tous les textes via ngx-translate.
- Tests backend : E2E par les contrôleurs ; `SettlementConcurrencyIT` est l'exception concurrence explicitement tolérée (frontière transactionnelle, déjà admise story 4.4). `@TestMethodOrder` + `@Order` + données persistantes entre méthodes pour le storyboard `SettlementSyncIT` ; classes IT étendent `org.pluribourse.shared.IntegrationTest` (sauf `SettlementConcurrencyIT`, qui suit `SaleConcurrencyIT` : `@SpringBootTest` autonome + Testcontainers).
- Couverture cible ≥ 80 % (back et front).

### Project Structure Notes

- `architecture.md` est **obsolète** sur ce module : il documente `payout/PayoutController.java` + `PayoutService.java` — le code réel a `domain/payout/controller/{SettlementController,AdminSettlementController}.java` et **pas** de `PayoutService`. Se fier au code, pas à `architecture.md`, pour les chemins.
- `ARCH-017` et `NFR-008` sont **nouveaux** (sprint change proposal 2026-08-24) et **pas encore reportés dans `architecture.md`** — cohérent avec la dérive documentaire déjà constatée sur les stories 2.7/2.9/3.14 (`architecture.md`/`EXPERIENCE.md` non amendés au fil des sprint changes). Ne pas bloquer là-dessus.
- Le `SseEmitterRegistry` est bien un bean initialisé avant les points d'entrée de transition (contrainte d'ordre F2, `architecture.md`) — rien à faire, il est déjà injecté ailleurs.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-08-24.md#Point 7 — Synchronisation des postes de soldage] (NFR-008, ARCH-017, impacts Epic 5 / Story 5.1)
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS] (verrouillage optimiste `@Version` + contrainte unique BDD ; rejet explicite du pessimiste)
- [Source: _bmad-output/planning-artifacts/architecture.md#Notification de phase (SSE)] (SseEmitter par client, registre thread-safe, EventSource Angular, ARCH-012)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.1 — Flux de solde des vendeurs] (FR-051/FR-052/FR-053, endpoints `/settlements/{sellerId}/settle` et `/unclaimed`, liste partagée ADMIN+VOLUNTEER)
- [Source: _bmad-output/implementation-artifacts/5-1-flux-de-solde-des-vendeurs.md] (`SettlementService`/`SettlementController` partagé sans `@PreAuthorize`, `requireNotAlreadySettled` + contrainte unique = filet, IDOR-safe `requireSellerOfEdition`)
- [Source: _bmad-output/implementation-artifacts/5-6-impression-groupee-des-bilans-de-vente-admin.md] (patron `AdminSettlementController` frère ; `SettlementFilter` ; item différé « aucun verrou serveur anti-double-soumission » — sur le flux d'impression groupée, pas sur `settle`)
- [Source: pluribourse-backend/.../domain/payout/service/SettlementService.java] (`settle`, `markUnclaimed`, `persistSettlement` + catch `DataIntegrityViolationException`)
- [Source: pluribourse-backend/.../domain/payout/exception/SellerAlreadySettledException.java] (`HttpStatus.CONFLICT`, code `seller-already-settled`)
- [Source: pluribourse-backend/.../domain/edition/service/EditionService.java:210-233] (`savePhaseThenSendEvent` — patron `afterCommit` + `broadcast`)
- [Source: pluribourse-backend/.../shared/sse/SseEmitterRegistry.java] (`broadcast(String, Object)`, connexions maintenues)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java] (patron de test 2 threads / Testcontainers / hors MockMvc)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java:163-205] (patron d'assertion SSE E2E via `asyncStarted` + contenu du flux)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementIT.java] (storyboard story 5.1 : @Order 3/4/5/7/9 déjà en place — non-régression)
- [Source: pluribourse-backend/src/main/resources/db/changelog/024-settlements.xml] (`uk_settlements_seller_profile` — contrainte unique existante, pas de nouvelle migration)
- [Source: pluribourse-frontend/src/app/services/sse.service.ts] (`listen`, `phaseChanges`, `basketCancelled`, type guards)
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts:87-92] (souscription SSE + `takeUntilDestroyed`)
- [Source: pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts] (`confirmSettle`/`confirmUnclaimed`/`applyUpdate`/`loadSettlements`)
- [Source: pluribourse-frontend/public/i18n/fr.json + en.json#settlement.error.alreadySettled] (clé déjà présente, actuellement inutilisée)
- Note : NFR-008 exige aussi le patron « même que NFR-002 pour la caisse ». Le pendant caisse est `BasketValidationConflictException` (409, code `basket-validation-conflict`) traduit en toast spécifique côté `pos-page.component.ts`. Le miroir ici : 409 `seller-already-settled` → toast `settlement.error.alreadySettled` (Task 5).

### Latest tech information

Aucune nouvelle librairie ni montée de version. SSE Spring (`SseEmitter`) et `EventSource` navigateur sont stables et déjà en usage dans le projet depuis la story 2.6. Testcontainers `MariaDBContainer` déjà utilisé par `SaleConcurrencyIT`. `takeUntilDestroyed` / `DestroyRef` : API Angular stable, déjà utilisée dans `app-layout.component.ts`. `auditTime` : opérateur RxJS standard.

### Git intelligence

Les 5 derniers commits sont « un commit = une story complète » (4.7 impression facture, 3.14 catégorie lot, 2.9 devise, 2.10 préparation non exclusive, 1.13 nav bénévole). Story 4.7 a touché `SaleRepository`/`sales-list.component` (écran liste filtrable) — **pas de recouvrement** avec 5.7, qui réutilise le composant `settlement-list` existant sans en refaire un écran liste. Aucune dépendance de librairie ajoutée récemment pertinente ici. Baseline de dev attendue : dernier commit de `main` au démarrage de `dev-story` (à renseigner dans le Dev Agent Record).

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (Claude Code, workflow bmad-dev-story)

### Debug Log References

- `./mvnw.cmd test -Dtest=SettlementSyncIT,SettlementIT,PosBasketCancellationIT` → 21/21 verts (nouveau storyboard 4/4 + régressions 11/11 et 6/6).
- `./mvnw.cmd test -Dtest=SettlementConcurrencyIT` → 1/1 vert (Testcontainers MariaDB, Docker présent). **1re tentative en échec** : la variante « settle vs markUnclaimed » ajoutée comme 2e `@Test` créait une 2e édition POST_SALE ; `EditionService.getActiveEdition()` (`findFirstByPhaseIn`) résolvait alors la mauvaise édition → `SellerNotFoundException`. Variante retirée — un seul `@Test`, une seule édition active, exactement comme `SaleConcurrencyIT` (raison documentée dans le Javadoc de la classe).
- `npm test` (frontend) → 698/698 verts, 67 fichiers (+2 sur `sse.service.spec.ts`, +8 net sur `settlement-list.component.spec.ts`).
- `npm run build` (frontend) → OK, aucun warning.

### Completion Notes List

- **T1 — SSE `settlement-updated` :** `SettlementUpdatedEventDto(Long editionId, Long sellerId)` créé. **Écart assumé vs la story** : placé dans `org.pluribourse.shared.sse` (avec `PhaseChangedEventDto` / `BasketCancelledEventDto`, les seuls DTO d'évènement SSE du projet et les seuls sites d'appel `broadcast`) et non `org.pluribourse.domain.payout.dto`. La story décrivait ce DTO comme un « miroir de `org.pluribourse.domain.edition.dto.PhaseChangedEventDto` » — package inexact (le vrai est `shared.sse`), donc la prémisse du choix `domain.payout.dto` ne tient pas. `File List` et le tableau « Fichiers à créer / modifier » reflètent le chemin réel.
- **T1 :** `SettlementService.settle()` et `markUnclaimed()` capturent le `SettlementDto` dans une locale puis enregistrent un `TransactionSynchronization.afterCommit()` via `broadcastSettlementUpdatedAfterCommit(edition, seller)` (patron `EditionService.savePhaseThenSendEvent` copié). Aucune émission depuis `closeAllUnsettledAsUnclaimed` ni les lectures. Aucune garde / calcul / persistance existante touchée.
- **T2 :** `persistSettlement` inchangé (le catch `DataIntegrityViolationException` → `SellerAlreadySettledException` était déjà là). Javadoc complété : NFR-008, contrainte `uk_settlements_seller_profile` comme garantie dure (INSERT unique), renvoi vers `SettlementConcurrencyIT`. Aucune migration Liquibase.
- **T3 :** `SettlementUpdatedEvent { editionId; sellerId }` dans `settlement.model.ts`. `sse.service.ts` : `isSettlementUpdatedEvent` (miroir de `isBasketCancelledEvent`) + `settlementUpdated()`.
- **T4 :** `SettlementListComponent` injecte `SseService` + `DestroyRef`, s'abonne dans le constructeur : `settlementUpdated().pipe(auditTime(250), takeUntilDestroyed(destroyRef))`. `onRemoteSettlementUpdate()` : no-op si `submitting()`, sinon `loadSettlements(true)`. `loadSettlements(silent = false)` : en mode silencieux ne touche ni `isLoading` ni `error()`, un échec est avalé (`console.debug`) et la liste affichée est conservée. Tri client déterministe (`lastName` puis `firstName`) appliqué dans le `computed` `filteredSettlements`. `@for … track sellerId` inchangé.
- **T5 :** `confirmSettle` / `confirmUnclaimed` : `catch (err: unknown)` + helper `isAlreadySettledConflict(err)` (409 + type RFC 7807 `…/seller-already-settled`) → toast `settlement.error.alreadySettled` + fermeture du formulaire (pour `confirmUnclaimed`, seulement si ouvert pour ce vendeur). Reload de rattrapage `loadSettlements(true)` dans le `finally` des deux méthodes. Aucune nouvelle clé i18n (`settlement.error.alreadySettled` déjà présente FR/EN).
- **T6 :** `SettlementSyncIT` (storyboard E2E : émission SSE sur settle + markUnclaimed, non-émission sur settle rejeté 422). `SettlementConcurrencyIT` (Testcontainers, 2 threads, `TransactionTemplate` + `CountDownLatch`, calqué sur `SaleConcurrencyIT`, hors MockMvc) : `successCount == 1`, cause `SellerAlreadySettledException`, exactement 1 `Settlement` en base.
- **T7 :** `sse.service.spec.ts` +2 (parse `SettlementUpdatedEvent`, ignore payload sans `sellerId`). `settlement-list.component.spec.ts` : reload silencieux déclenché / ignoré si `submitting`, échec silencieux qui ne vide pas la liste ni ne lève `error()`, ordre d'affichage déterministe quel que soit l'ordre serveur, toast 409 spécifique + formulaire fermé (settle & unclaimed), reload de rattrapage dans le `finally`, souscription SSE fermée à la destruction. 4 tests existants adaptés (ordre déterministe `[2, 1]` ; `getSettlements` re-mocké sur l'état post-action pour absorber le reload de rattrapage).
- **T8 :** `./mvnw.cmd clean package` — voir Change Log pour le delta de comptage. Vérification visuelle double-onglet `/volunteer/settlement` + `/admin/settlement` **laissée à Manerial** (CLAUDE.md).
- **AC6 :** `EditionClosingIT`, `SettlementIT` @Order 3/4/5/7/9 inchangés et verts — aucune régression sur les flux de solde existants ; la fermeture d'édition n'émet pas `settlement-updated`.

### File List

**Backend**
- `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SettlementUpdatedEventDto.java` — **NOUVEAU**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/payout/service/SettlementService.java` — **MODIFIÉ**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementSyncIT.java` — **NOUVEAU**
- `pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementConcurrencyIT.java` — **NOUVEAU**

**Frontend**
- `pluribourse-frontend/src/app/models/settlement.model.ts` — **MODIFIÉ**
- `pluribourse-frontend/src/app/services/sse.service.ts` — **MODIFIÉ**
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts` — **MODIFIÉ**
- `pluribourse-frontend/src/app/services/sse.service.spec.ts` — **MODIFIÉ**
- `pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts` — **MODIFIÉ**

**Artefacts**
- `_bmad-output/implementation-artifacts/5-7-synchronisation-des-postes-de-soldage.md` — frontmatter `baseline_commit`, cases Tasks, Dev Agent Record, Status
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — statut 5-7 → in-progress → review

## Change Log

| Date | Version | Description |
|---|---|---|
| 2026-09-01 | 0.1 | Story 5.7 implémentée : évènement SSE `settlement-updated` (émis après commit d'un settle/markUnclaimed), synchro temps réel + tri client déterministe de `SettlementListComponent`, toast 409 `seller-already-settled` spécifique + reload de rattrapage, Javadoc NFR-008, tests `SettlementSyncIT` / `SettlementConcurrencyIT` + tests frontend. |
| 2026-09-01 | 0.2 | Vérifs finales T8. Backend `./mvnw.cmd clean package` : **BUILD SUCCESS, 556 tests, 0 échec / 0 erreur / 0 skip** (Docker présent → `SettlementConcurrencyIT` exécuté ; sans Docker il serait skippé comme `SaleConcurrencyIT` → 555). Delta backend : +5 tests (`SettlementSyncIT` 4, `SettlementConcurrencyIT` 1). Frontend `npm test` : **698 tests, 0 échec** (delta +12 : `sse.service.spec.ts` +2, `settlement-list.component.spec.ts` +10, dont 4 tests existants adaptés). `npm run build` : OK, aucun warning. |
| 2026-09-01 | 0.3 | Revue de code (bmad-code-review) : 7 patchs appliqués. Front `settlement-list.component.ts` — `loadSettlements` réinitialise `error()` sur succès (bannière d'erreur ne masque plus une liste fraîche) ; garde latest-wins (compteur monotone) entre rechargements concurrents ; départage de tri final sur `sellerId` (homonymes stables) ; `console.debug` de rechargement silencieux ne sérialise plus l'objet `HttpErrorResponse`. `settlement-list.component.spec.ts` — +2 tests (départage `firstName`/`sellerId`, `confirmUnclaimed` 409 laisse ouvert le formulaire d'un autre vendeur). Back `SettlementConcurrencyIT` — 2e course settle-vs-markUnclaimed sur un 2e vendeur de la même édition (AC1 « mélange des deux »). Front `npm test` : **700 tests, 0 échec**. `SettlementConcurrencyIT` : 1/1 vert (Docker présent). 6 constats classés `defer` (patrons de test déjà validés, limitations SSE pré-existantes) → `deferred-work.md`. Statut → done. |

## Review Findings

_Revue de code adversariale (bmad-code-review, 2026-09-01) — 3 couches parallèles : Blind Hunter, Edge Case Hunter, Acceptance Auditor. Baseline `3db2f9f` → `bc050d1`. 0 decision-needed, 7 patch, 6 defer, 20 rejetés comme bruit / faux positifs._

- [x] [Review][Patch] `loadSettlements` succès ne réinitialise jamais `error()` — après un `ngOnInit` en échec, un rechargement silencieux qui réussit remplit `settlements()` mais l'écran reste bloqué sur la bannière d'erreur (`@if (!isLoading() && !error())` masque la liste). Ajouter `this.error.set(null)` sur le chemin succès. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:303]
- [x] [Review][Patch] Tri client sans départage final — deux vendeurs de mêmes `lastName` + `firstName` retombent sur l'ordre serveur non garanti et scintillent encore à chaque rechargement SSE (le défaut même que le tri devait corriger, AC4). Ajouter `|| a.sellerId - b.sellerId`. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:70]
- [x] [Review][Patch] `console.debug('Silent settlement reload failed…', err)` sérialise l'objet `HttpErrorResponse` complet dans la console navigateur — incohérent avec la règle projet « pas de données perso dans les logs applicatifs » (pas de fuite réelle ici : erreur `GET /api/settlements` RFC 7807 sans PII, mais l'objet entier est déversé). Logger le message seul ou retirer l'argument. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:306]
- [x] [Review][Patch] Le test de tri déterministe n'exerce jamais la branche `firstName` du départage exigée par T7 (les deux lignes ne diffèrent que par `lastName`). Ajouter une paire même-`lastName` / `firstName` différent. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts]
- [x] [Review][Patch] Le test 409 de `confirmUnclaimed` n'assère que la branche positive (formulaire fermé) ; la branche négative « formulaire ouvert pour un AUTRE vendeur → reste ouvert » (T5/T7) n'est pas couverte. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.spec.ts]
- [x] [Review][Patch] Aucune garde latest-wins entre appels `loadSettlements` concurrents (`ngOnInit` + rattrapage `finally` + déclenchement SSE) — une réponse `getSettlements` plus ancienne qui se résout en dernier écrase des lignes fraîches par des lignes périmées. Compteur de requête monotone, ignorer une réponse dont la requête n'est plus la dernière. [pluribourse-frontend/src/app/features/settlement/settlement-list.component.ts:296]
- [x] [Review][Patch] `SettlementConcurrencyIT` ne prouve que settle-vs-settle ; AC1 énumère explicitement `markUnclaimed` et le mélange settle/markUnclaimed. Ajouter un 2e vendeur + une course mixte dans le même `@Test` (une seule édition POST_SALE — la raison du retrait de la variante était une 2e édition, pas la course elle-même). [pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementConcurrencyIT.java:96]
- [x] [Review][Defer] `SettlementConcurrencyIT` : pas de barrière forçant les deux transactions à passer `requireNotAlreadySettled` avant l'INSERT, pas de timeout sur `future.get()`, catch limité à `DataIntegrityViolationException` (un deadlock 1213 → `DeadlockLoserDataAccessException` non traduit), `conflict` seulement null-checké [pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementConcurrencyIT.java:137] — deferred, réplique verbatim le patron `SaleConcurrencyIT` validé avec l'utilisateur à la création de la story 4.4 (mêmes compromis déjà acceptés).
- [x] [Review][Defer] `SettlementSyncIT` : corps SSE lu en synchrone sans `asyncDispatch`/poll (assertions dépendantes du timing du flush), 3 connexions `/api/sse/events` ouvertes et jamais fermées [pluribourse-backend/src/test/java/org/pluribourse/domain/payout/SettlementSyncIT.java:162] — deferred, réplique le patron établi `PosBasketCancellationIT` @Order(2)/(3)/(5) ; suite complète verte (556 tests).
- [x] [Review][Defer] `SseEmitterRegistry.broadcast` s'exécute en synchrone sur le thread de requête — la réponse HTTP du settle attend l'écriture vers tous les émetteurs [pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseEmitterRegistry.java:29] — deferred, design pré-existant partagé par `phase-changed`/`basket-cancelled`.
- [x] [Review][Defer] Aucun rejeu des évènements manqués pendant une fenêtre de reconnexion `EventSource` — un poste déconnecté 30 s reste sur une liste périmée jusqu'au prochain évènement ou action locale [pluribourse-frontend/src/app/services/sse.service.ts:60] — deferred, limitation d'architecture SSE pré-existante affectant les 3 types d'évènements.
- [x] [Review][Defer] Chaque `SseService.listen()` ouvre sa propre `EventSource` ; `settlementUpdated()` est la 3e connexion concurrente par onglet, se rapprochant du plafond ~6/hôte HTTP/1.1 [pluribourse-frontend/src/app/services/sse.service.ts:60] — deferred, design pré-existant ; cette story ajoute un consommateur.
- [x] [Review][Defer] `/admin/settlement` n'est pas phase-gardé côté client après une clôture d'édition (la redirection du layout est bénévole-seulement, `if (!this.isVolunteer()) return;`) : un rechargement silencieux qui tombe en 422 est avalé et laisse des lignes périmées cliquables [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts:68] — deferred, pré-existant (la liste admin était déjà chargée une seule fois) ; le nouveau Javadoc « every terminal has already left the page » surestime la réalité. Peut justifier sa propre story.
