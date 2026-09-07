---
baseline_commit: 274e3375fd5c65ee99606d3b0b78d62f677542fc
---

# Story 4.8 : Réservation de lot au scan & annulation du panier à la déconnexion

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que bénévole caissier travaillant sur plusieurs postes de caisse,
je veux qu'un lot soit **réservé pour mon panier** dès que j'en scanne un article, et que mon panier actif soit **abandonné si je me déconnecte**,
afin que deux caisses ne se disputent jamais les membres d'un même lot (plus de course à la validation, plus de risque de 500), et qu'un poste laissé après déconnexion ne garde pas de panier ni de réservation orpheline.

## Contexte & origine

Issue de la **SCP 2026-09-03** (`_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-03.md`, approuvée par Manerial le 2026-09-03). Déclencheur : point `decision-needed` **D1** de la revue de code de la **Story 5.8** — la garde de concurrence FR-109 livrée par 5.8 (`LotRepository.bumpVersion`, force-increment de `Lot.@Version` en masse dans `PosBasketService.validate()`) peut sortir en **HTTP 500** (deadlock MariaDB 1213 / lock-wait 1205 non mappés) quand deux postes valident des paniers touchant les deux mêmes lots en ordre de scan inverse. Le patch de contournement **P5** (tri des lots par `id` avant la boucle de bump) écarte le 1213 mais pas le 1205. Manerial a décidé de **remplacer entièrement** le verrou optimiste par une **réservation légère de lot** prise à l'ajout au panier.

**Ce qui est déjà fait (ne pas refaire) :**

- Les amendements d'artefacts de la SCP sont **déjà appliqués et commités** (commit `274e337` « Correct course ») :
  - `prd.md` : **FR-109 réécrit** (réservation au scan), **FR-110 créé** (panier persisté, durée de vie bornée par la session ; déconnexion explicite → annulation), **FR-066 amendé** (expiration après 1 h d'inactivité).
  - `epics.md` : ACs Story 4.3 (bloc FR-109), notes Stories 4.4 et 2.8, lignes miroir FR-109/FR-110/FR-066, carte de couverture, « FR couvertes » Epic 4, UX-DR21.
  - `architecture.md` § Concurrence — POS (l.234, 237, 239) : « Stratégie de verrouillage », « Intégrité des lots » **remplacée**, « Exigence de test » réécrite ; § Notification de Changement de Phase l.251 : « Déclencheur (bis) ».
- Les amendements ci-dessus **ne sont pas rejoués** par cette story. En revanche, elle porte **une correction ciblée de 3 lignes d'artefacts** (voir « Correction d'artefacts portée par cette story », Dev Notes) : la notification de déconnexion **ne passe pas** par un `basket-cancelled` SSE, contrairement à la première rédaction de FR-110 / architecture / epics.

**Portée technique :** back (concurrence POS + sécurité logout) + **1 migration Liquibase** (`035`) + retouches front (`.ts` + i18n `.json` uniquement, **aucun template, aucune route, aucun composant, aucune dépendance nouvelle**) + **correction de 3 lignes** dans `prd.md` / `architecture.md` / `epics.md` (clause SSE de la déconnexion). `EXPERIENCE.md` volontairement non amendé (dérive documentaire connue et assumée, même convention que 2.7 / 2.9 / 3.14 / 4.7 / 5.8).

**Statut des stories amendées (toutes `done`, livrées par cette story, jamais rouvertes) :** 4.3 (bloc AC FR-109), 4.4 (note : lot ≠ `@Version`), 2.8 (note : routine d'annulation extraite + libération des réservations). 5.8 (origine du déclencheur, non rouverte).

## Découpage en 2 parties

| Partie | Déclencheur | Objet | Couches |
|---|---|---|---|
| **A** | D1 | **Réservation de lot au scan / à l'ajout au panier.** Migration `035` (2 colonnes sur `lots` + FK `ON DELETE SET NULL`). `addItem` réserve atomiquement le lot pour le panier au **premier** membre ajouté (0 ligne → `LotReservedException` 409 `lot-reserved`, l'article n'entre pas). Libération : `removeItem` (dernier membre du lot), `removeLot`, `validate()` succès. **Retrait** de `bumpVersion`, `isLotVersionRace`, du catch `JpaSystemException` du bump, du patch P5. **Conservation** de `existsByLotIdAndSoldTrue` + ses 2 appels (frère vendu dans une vente committée). Front : branche `/lot-reserved` + clé i18n `volunteer.pos.error.lotReserved`. | Back (POS + exception + repo + entité + migration + concurrence) + Front (`pos-page.component.ts` + i18n `.json`) |
| **B** | Décision Manerial « il s'est déconnecté, on ne garde pas son panier » + fermeture de la fenêtre de réservation orpheline de la Partie A | **Annulation du panier actif à la déconnexion explicite (FR-110).** Nouveau `BasketCancellationService` (`domain/pos/service`) avec **deux points d'entrée** : `cancelBaskets(baskets, phaseForEvent)` — libère les réservations, supprime `BasketItem` + `Basket`, émet **un** `basket-cancelled` après commit (utilisé par le changement de phase) ; `cancelBasketSilently(basket)` — mêmes suppressions/libérations **sans aucun événement** (utilisé par la déconnexion : `basket-cancelled` est un *broadcast* à tous les postes, il annulerait le panier des autres caissiers ; les autres onglets du même utilisateur, dont la session vient d'être invalidée, se déconnectent d'eux-mêmes). `EditionService.savePhaseThenSendEvent` **délègue** son annulation en masse à `cancelBaskets`. Nouveau `LogoutHandler` câblé dans `SecurityConfig` : appelle `cancelBasketSilently` sur le panier actif du bénévole pour l'édition active, best-effort. **Nettoyage config** : suppression de la ligne morte `server.servlet.session.timeout=2h` + de son commentaire. **Aucun changement front.** | Back (`SecurityConfig` + `LogoutHandler` + `BasketCancellationService` + `EditionService` + `application.properties`) + **3 lignes d'artefacts** (clause SSE FR-110) |

---

## Acceptance Criteria

> Les blocs Given/When/Then reprennent les ACs déjà amendés dans `epics.md` (Story 4.3, bloc FR-109 réécrit) et les points figés de la SCP §5 (« Points de conception figés », « Critères de succès »).

### Partie A — Réservation de lot au scan / à l'ajout au panier

**AC-A1 — Réservation prise au premier membre**
**Étant donné** une édition en phase Vente et un panier de caisse appartenant au caissier
**Quand** le caissier scanne (ajoute au panier) un article appartenant à un lot dont **aucun membre** n'est encore dans ce panier
**Alors** l'article est ajouté au panier
**Et** le lot est marqué réservé pour ce panier : `lots.reserved_by_basket_id = <id du panier>`, `lots.reserved_at` renseigné
**Et** ajouter un **second** membre du **même** lot **au même panier** est accepté sans nouvelle écriture de réservation (idempotence — le panier détient déjà le lot)

**AC-A2 — Rejet depuis un autre panier**
**Étant donné** qu'un lot est réservé pour le panier du caissier A
**Quand** le caissier B scanne (ajoute au panier) un autre membre du même lot depuis **son** panier
**Alors** l'ajout est rejeté avec un **409 `lot-reserved`** (type `https://pluribourse/errors/lot-reserved`, message « lot en cours d'encaissement sur une autre caisse »)
**Et** l'article n'entre **pas** dans le panier de B
**Et** aucune réponse 500 n'est possible sur ce chemin

**AC-A3 — Libération au retrait du dernier membre**
**Étant donné** qu'un panier détient un ou plusieurs membres d'un lot réservé
**Quand** le caissier retire (`removeItem`) le **dernier** membre de ce lot encore présent dans le panier
**Alors** la réservation est levée : `lots.reserved_by_basket_id` et `lots.reserved_at` repassent à `NULL`
**Et** le retrait d'un membre alors qu'il en reste **au moins un** dans le panier **ne** libère **pas** la réservation

**AC-A4 — Libération au retrait du lot entier et à la validation**
**Étant donné** qu'un panier détient un lot réservé
**Quand** le caissier clique sur « Retirer le lot entier » (`removeLot`, FR-081) **ou** valide le paiement du panier
**Alors** la réservation est levée (`reserved_by_basket_id` → `NULL`)
**Et** après une validation réussie, un second poste peut à son tour scanner un article de ce lot — sauf si un membre a été vendu dans la vente qui vient d'être committée (voir AC-A5)

**AC-A5 — Frère déjà vendu dans une vente committée (comportement conservé)**
**Étant donné** qu'au moins un article d'un lot a été marqué vendu dans une vente déjà committée
**Quand** un caissier scanne un autre membre de ce lot, ou valide un panier le contenant
**Alors** l'opération est rejetée avec un **409 `lot-already-sold`** — au scan (`PosScanService.scan`) **et** à la validation (`PosBasketService.validate`, pré-check `existsByLotIdAndSoldTrue`)
**Et** ce chemin est **indépendant** de la réservation (il fonctionne même si `reserved_by_basket_id` est `NULL`)

**AC-A6 — Concurrence réelle (Testcontainers MariaDB)**
**Étant donné** deux paniers distincts, un par caissier, et deux membres d'un même lot non encore réservé
**Quand** les deux `addItem` s'exécutent en concurrence, chacun dans sa propre transaction
**Alors** exactement un obtient la réservation, l'autre reçoit `LotReservedException` (409 `lot-reserved`)
**Et** aucune vente n'est créée, aucun deadlock ni lock-wait n'est possible sur ce chemin
**Et** l'ancien scénario de course **à la validation** pour les lots n'a plus lieu d'être testé (le mécanisme `bumpVersion` a disparu)

**AC-A7 — Ancien mécanisme retiré**
**Étant donné** le code livré par cette story
**Alors** `LotRepository.bumpVersion`, `PosBasketService.isLotVersionRace`, la branche `catch (JpaSystemException)` du bloc de bump et le tri P5 (`Comparator.comparing(... getLot().getId())` de `lotRepresentatives`) **n'existent plus**
**Et** `ItemRepository.existsByLotIdAndSoldTrue` et ses **deux** appels (garde de `PosScanService.scan`, pré-check de `PosBasketService.validate`) sont **conservés**
**Et** le catch per-item `ObjectOptimisticLockingFailureException` / `SnapshotIsolationException` (garde `@Version` de l'**article**, Story 4.4) est **conservé intact**

**AC-A8 — Front : erreur inline `lot-reserved`**
**Étant donné** un ajout au panier ou une validation rejetés en 409 `lot-reserved`
**Quand** `pos-page.component.ts` traite l'erreur (`handleScanError` **et** `handleValidationError`)
**Alors** une notification inline **erreur** est affichée sous le scanner avec le texte de `volunteer.pos.error.lotReserved`
**Et** le panier est laissé inchangé (pas de résolution automatique)
**Et** `volunteer.pos.error.lotAlreadySold` reste utilisée pour le cas AC-A5 (clé conservée, non supprimée)
**Et** `fr.json` et `en.json` contiennent tous deux la nouvelle clé (parité i18n)

### Partie B — Annulation du panier à la déconnexion explicite (FR-110)

**AC-B1 — Déconnexion → panier annulé**
**Étant donné** un bénévole connecté avec un panier de caisse actif pour l'édition active (contenant des articles, dont éventuellement un lot réservé)
**Quand** il appelle `POST /api/auth/logout`
**Alors** la déconnexion réussit (HTTP 200, session invalidée, cookies supprimés — comportement existant inchangé)
**Et** son `Basket` actif et tous ses `BasketItem` sont supprimés
**Et** toutes les réservations qu'il détenait (`lots.reserved_by_basket_id = <id de ce panier>`) repassent à `NULL`
**Et aucun** évènement SSE n'est émis — ni `basket-cancelled`, ni autre : `basket-cancelled` est un *broadcast* à tous les postes connectés (`SseEmitterRegistry.broadcast`) et le `BasketCancelledEventDto` ne porte pas d'identité utilisateur ; l'émettre annulerait le panier **en cours** des autres caissiers
**Et** les autres onglets du **même** utilisateur (même navigateur), dont la session partagée vient d'être invalidée, sont déconnectés à leur tour à leur prochaine requête (401 → redirection `/login`) — aucun panier périmé résiduel exploitable

**AC-B2 — Best-effort, jamais bloquant**
**Étant donné** une déconnexion
**Quand** le bénévole n'a **aucun** panier actif, ou qu'il n'y a **aucune** édition active, ou qu'un échec survient pendant le nettoyage du panier
**Alors** la déconnexion réussit quand même (HTTP 200)
**Et** aucune donnée personnelle (nom, e-mail, téléphone du vendeur, identifiant utilisateur) n'apparaît dans les logs en cas d'échec

**AC-B3 — Routine d'annulation partagée**
**Étant donné** un changement de phase d'édition (FR-090) qui annule les paniers actifs
**Quand** `EditionService.savePhaseThenSendEvent` s'exécute
**Alors** l'annulation passe par la routine partagée `BasketCancellationService.cancelBaskets(...)` : libération des réservations de lot + suppression `BasketItem`/`Basket` + `basket-cancelled` différé après commit
**Et** le changement de phase émet toujours **un seul** `basket-cancelled` (pas un par panier) et toujours son `phase-changed` — la déconnexion (AC-B1), elle, passe par `cancelBasketSilently(...)` : **mêmes** suppressions/libérations, **aucun** événement
**Et** les scénarios existants de Story 2.8 (annulation du panier au changement de phase, côté serveur) restent verts
**Et** l'ordre relatif des deux broadcasts du changement de phase peut changer selon l'ordre d'enregistrement des synchros (voir Dev Notes « Ordre des broadcasts SSE ») — sans impact fonctionnel, aucun test n'observe le SSE en MockMvc

**AC-B4 — Nettoyage de configuration**
**Étant donné** `application.properties`
**Alors** la ligne morte `server.servlet.session.timeout=2h` et son commentaire sont **supprimés**
**Et** `spring.session.timeout=PT1H` (Spring Session JDBC) reste l'**unique** source de vérité du délai d'inactivité (1 h)
**Et** l'application démarre et tous les tests existants passent (aucune régression sur la persistance de session FR-066 : la session survit à un redémarrage du conteneur)

---

## Tasks / Subtasks

### Partie A — Réservation de lot

- [x] **T-A1 — Migration Liquibase `035-lot-reservation.xml`** (AC : A1, A2)
  - [x] Créer `pluribourse-backend/src/main/resources/db/changelog/035-lot-reservation.xml` : `changeSet id="035-lot-reservation" author="pluribourse"`.
  - [x] `<addColumn tableName="lots">` : `reserved_by_basket_id` type `BIGINT` nullable ; `reserved_at` type `DATETIME` nullable.
  - [x] `<addForeignKeyConstraint baseTableName="lots" baseColumnNames="reserved_by_basket_id" constraintName="fk_lots_reserved_basket" referencedTableName="baskets" referencedColumnNames="id" onDelete="SET NULL"/>` — même convention que `fk_items_sale` (`022`).
  - [x] `<rollback>` : `dropForeignKeyConstraint` puis `dropColumn` × 2.
  - [x] Commentaire XML : SCP 2026-09-03, FR-109, `reserved_at` purement diagnostic (pas de TTL), justification du `ON DELETE SET NULL` (filet BDD derrière la libération applicative, `baskets` = table à forte rotation).
  - [x] Ajouter `<include file="db/changelog/035-lot-reservation.xml"/>` en dernière ligne de `db.changelog-master.xml` (après `034`).
- [x] **T-A2 — Entité `Lot`** (AC : A1)
  - [x] Ajouter `@Column(name = "reserved_by_basket_id") private Long reservedByBasketId;` (pas de `@ManyToOne` vers `Basket` — `pos` dépend de `item`, jamais l'inverse ; identifiant brut, même choix que `archived_items.lot_ref` en `034`).
  - [x] Ajouter `@Column(name = "reserved_at") private LocalDateTime reservedAt;`.
  - [x] Ajouter l'import `java.time.LocalDateTime` — les imports actuels de `Lot.java` sont `java.util.*` + `java.math.*` uniquement.
  - [x] **Ne pas** toucher `@Version version` (toujours utilisé par `LotService.update` — voir Dev Notes « `LotService.update` et les nouvelles colonnes » : aucun risque d'écrasement de réservation, les deux vivent dans des phases disjointes).
- [x] **T-A3 — `LotRepository`** (AC : A2, A3, A4, A7)
  - [x] **Supprimer** `bumpVersion`.
  - [x] `@Modifying @Query("UPDATE Lot l SET l.reservedByBasketId = :basketId, l.reservedAt = :now WHERE l.id = :lotId AND (l.reservedByBasketId IS NULL OR l.reservedByBasketId = :basketId)") int reserveLot(...)`.
  - [x] `@Modifying @Query("UPDATE Lot l SET l.reservedByBasketId = NULL, l.reservedAt = NULL WHERE l.id = :lotId") int releaseLot(Long lotId)`.
  - [x] `@Modifying @Query("UPDATE Lot l SET l.reservedByBasketId = NULL, l.reservedAt = NULL WHERE l.reservedByBasketId = :basketId") int releaseAllByBasketId(Long basketId)`.
  - [x] JavaDoc courte sur `reserveLot` : jeton de revendication optimiste, 0 ligne → un autre panier détient le lot ; la clause `OR l.reservedByBasketId = :basketId` rend l'appel idempotent pour le panier détenteur.
- [x] **T-A4 — Exception `LotReservedException`** (AC : A2, A8)
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/LotReservedException.java`, `extends BusinessException`, `super(HttpStatus.CONFLICT, "lot-reserved", "Lot reserved by another basket: " + lotId)`.
  - [x] JavaDoc : mappée par `GlobalExceptionHandler.handleBusiness` en 409 `type=https://pluribourse/errors/lot-reserved`, aucun handler dédié ; ne porte que le `lotId` (CLAUDE.md : pas de données perso).
- [x] **T-A5 — `PosBasketService.addItem` : prise de réservation** (AC : A1, A2)
  - [x] Après le contrôle `ItemAlreadyInBasketException` et **avant** l'insertion du `BasketItem` : si `scanned.lotId() != null` **et** `basketItemRepository.findAllByBasketIdAndItemLotId(basketId, scanned.lotId()).isEmpty()` (aucun membre encore présent) → `if (lotRepository.reserveLot(scanned.lotId(), basketId, LocalDateTime.now()) == 0) { throw new LotReservedException(scanned.lotId()); }`.
  - [x] Laisser intact le `try/catch (DataIntegrityViolationException)` autour de `saveAndFlush`.
- [x] **T-A6 — `PosBasketService.removeItem` : libération dernier membre** (AC : A3)
  - [x] Avant `basketItemRepository.delete(basketItem)`, capturer `Long lotId = basketItem.getItem().getLot() != null ? basketItem.getItem().getLot().getId() : null;`.
  - [x] Après le `delete`, si `lotId != null` : `if (basketItemRepository.findAllByBasketIdAndItemLotId(basketId, lotId).isEmpty()) { lotRepository.releaseLot(lotId); }` (la requête auto-flush le `delete` en attente, comme le fait déjà `findAllByBasketIdOrderById` dans `toDto`).
- [x] **T-A7 — `PosBasketService.removeLot` : libération** (AC : A4)
  - [x] Après `basketItemRepository.deleteAll(lotItems)` : `lotRepository.releaseLot(lotId);`.
- [x] **T-A8 — `PosBasketService.validate` : nettoyage + libération** (AC : A4, A5, A7)
  - [x] Remplacer la construction de `lotRepresentatives` (liste triée P5) par une simple boucle sur `ItemPricing.distinctByLot(items)` : pour chaque représentant dont `getLot() != null`, `if (itemRepository.existsByLotIdAndSoldTrue(lot.getId())) { throw new LotAlreadySoldException(lot.getId()); }`. **Conserver** ce pré-check (AC-A5).
  - [x] **Supprimer entièrement** le bloc `for (Item representative : lotRepresentatives) { ... lotRepository.bumpVersion(...) ... catch (JpaSystemException e) ... }` (avec son gros commentaire).
  - [x] **Supprimer** la méthode privée `isLotVersionRace`. **Conserver** `isCausedBy` (encore utilisée par le catch per-item `SnapshotIsolationException`).
  - [x] Juste avant `basketRepository.delete(basket)` : `lotRepository.releaseAllByBasketId(basket.getId());` (libération explicite déterministe + la FK `ON DELETE SET NULL` en filet).
  - [x] Nettoyer les imports devenus inutiles : `java.util.Comparator`, `java.sql.SQLException`. **Garder** `JpaSystemException`, `org.hibernate.exception.SnapshotIsolationException`, `ObjectOptimisticLockingFailureException`, `DataIntegrityViolationException`, `LotAlreadySoldException`. Ajouter l'import `LotReservedException`.
  - [x] Mettre à jour le JavaDoc de `validate` (retirer la mention du force-increment `Lot.@Version` ; garder la description du check per-item `@Version` de l'article).
- [x] **T-A9 — Front : branche `lot-reserved` + i18n** (AC : A8)
  - [x] `pos-page.component.ts` → `handleScanError` : ajouter, à côté de la branche `/lot-already-sold`, `if (type?.endsWith('/lot-reserved')) { this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.lotReserved'), variant: 'error' }); return; }`.
  - [x] `pos-page.component.ts` → `handleValidationError` : même branche `/lot-reserved` (défense — un ajout concurrent pourrait en théorie remonter ici).
  - [x] `pluribourse-frontend/public/i18n/fr.json` sous `volunteer.pos.error` : `"lotReserved": "Ce lot est en cours d'encaissement sur une autre caisse."` (garder `lotAlreadySold`).
  - [x] `pluribourse-frontend/public/i18n/en.json` sous `volunteer.pos.error` : `"lotReserved": "This lot is being checked out at another checkout."`.
- [x] **T-A10 — Tests Partie A**
  - [x] `SaleConcurrencyIT` : **réécrire** `two_concurrent_validations_of_different_members_of_the_same_lot_exactly_one_succeeds` → nouveau nom (ex. `two_concurrent_add_item_of_two_members_of_the_same_lot_exactly_one_reserves`). Deux threads, chacun `transactionTemplate.execute(status -> posBasketService.addItem(basketId, barcode, userId))` sur deux membres d'un même lot dans deux paniers distincts.
  - [x] **Prérequis fixtures** (sinon `addItem` → `posScanService.scan` lève `ItemNotFoundException`) : le `SellerProfile` porte `sellerNumber` (ex. `1`) ; **chaque `Item` membre** porte `itemNumber` (le helper `newLotMember(...)` reçoit déjà le paramètre — vérifier qu'il fait bien `item.setItemNumber(itemNumber)`) ; édition en phase `SALE`. Barcode = `String.format("%04d%04d", sellerNumber, itemNumber)`.
  - [x] Les deux paniers doivent être **vides** au départ : les créer via `posBasketService.getOrCreateCurrentBasket(userId)` (ou l'insert direct d'un `Basket` sans item), **pas** via `createBasketWithItem(...)` — c'est l'`addItem` concurrent lui-même qui prend la réservation.
  - [x] Assertions : exactement un succès, l'autre `LotReservedException` ; `saleRepository.count() == 0` ; `lotRepository.findById(lotId).getReservedByBasketId()` == id du panier gagnant ; aucun deadlock ni lock-wait.
  - [x] `SaleConcurrencyIT` : conserver `two_concurrent_validations_on_the_same_item_exactly_one_succeeds` **inchangé** (prouve la garde `@Version` de l'article). Mettre à jour le JavaDoc de classe (références de lignes obsolètes `PosBasketService.java:169-181` + « le scénario lot cible désormais `addItem` »).
  - [x] `PosBasketIT` : **ajouter** des méthodes `@Order` en fin de story-board (E2E via MockMvc, H2) : (a) réservation prise au premier membre + `reserved_by_basket_id` visible ; (b) un autre panier (volunteer2) → 409 `lot-reserved` ; (c) retrait du dernier membre → volunteer2 peut alors ajouter ; (d) `removeLot` → libération ; (e) `validate` réussie → `reserved_by_basket_id` à `NULL` ; (f) deux membres du même lot dans **le même** panier → les deux ajouts réussissent (idempotence AC-A1).
  - [x] `PosBasketIT` : vérifier que `a_lot_with_one_member_already_sold_is_rejected_at_add_item_and_at_validation` (@Order 20) reste vert (pré-check `existsByLotIdAndSoldTrue` conservé).
  - [x] `PosScanIT` : `scanning_a_sibling_of_an_already_sold_lot_returns_409` (@Order 13) reste vert (scan inchangé). Aucun ajout attendu ici.

### Partie B — Annulation à la déconnexion

- [x] **T-B1 — `BasketCancellationService` (routine partagée)** (AC : B1, B3)
  - [x] Nouveau `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketCancellationService.java`, `@Service @RequiredArgsConstructor`. Dépendances : `BasketRepository`, `LotRepository`, `SseEmitterRegistry` (aucune dépendance vers `EditionService`/`PosBasketService` → pas de cycle).
  - [x] Méthode privée `releaseAndDelete(Collection<Basket> baskets)` : `baskets.forEach(b -> lotRepository.releaseAllByBasketId(b.getId()))` ; `basketRepository.deleteAll(baskets)` (les `basket_items` suivent par cascade JPA + FK `deleteCascade` — comportement déjà éprouvé par `EditionService`).
  - [x] `@Transactional public void cancelBaskets(Collection<Basket> baskets, PhaseType phaseForEvent)` : si vide → return ; `Long editionId = baskets.iterator().next().getEdition().getId();` (accès id sur proxy lazy — sûr même si le `Basket` est détaché) ; `releaseAndDelete(baskets)` ; enregistrer une `TransactionSynchronization.afterCommit()` qui `sseEmitterRegistry.broadcast("basket-cancelled", new BasketCancelledEventDto(editionId, phaseForEvent))`. **Seule** cette méthode émet l'événement — chemin changement de phase (FR-090), où le broadcast à tous les postes est correct (tous les paniers sont réellement annulés).
  - [x] `@Transactional public void cancelBasketSilently(Basket basket)` : `releaseAndDelete(List.of(basket))` — **aucune** `TransactionSynchronization`, **aucun** broadcast. Chemin déconnexion (FR-110) : `basket-cancelled` est un *broadcast* non ciblé (`SseEmitterRegistry.broadcast` → tous les emitters ; `BasketCancelledEventDto` sans `userId`), il annulerait le panier des autres caissiers. Les autres onglets du même utilisateur se déconnectent seuls (session invalidée).
  - [x] JavaDoc : routine unique d'annulation de panier (FR-090 changement de phase **et** FR-110 déconnexion) ; expliquer pourquoi le chemin déconnexion est silencieux (blast radius du broadcast, voir Dev Notes « Notification de déconnexion — pas de SSE ») ; la FK `ON DELETE SET NULL` est le filet BDD derrière `releaseAllByBasketId`.
- [x] **T-B2 — `EditionService.savePhaseThenSendEvent` : déléguer** (AC : B3)
  - [x] Injecter `BasketCancellationService`.
  - [x] Remplacer le bloc `List<Basket> activeBaskets = ...; BasketCancelledEventDto basketCancelledEvent = ...; if (!activeBaskets.isEmpty()) { basketRepository.deleteAll(activeBaskets); }` **et** la partie `if (basketCancelledEvent != null) { sseEmitterRegistry.broadcast("basket-cancelled", ...); }` de la synchro `afterCommit` par : `basketCancellationService.cancelBaskets(basketRepository.findAllByEditionId(id), newPhase);`.
  - [x] **Ordre des broadcasts (voir Dev Notes « Ordre des broadcasts SSE »)** : placer l'appel `cancelBaskets(...)` **après** le `TransactionSynchronizationManager.registerSynchronization(...)` qui diffuse `phase-changed`, pour conserver l'ordre actuel `phase-changed` puis `basket-cancelled` à l'`afterCommit`. Le `DELETE` des paniers a lieu de toute façon dans la même transaction avant commit ; seul l'ordre d'enregistrement des synchros importe. Documenter le choix en commentaire.
  - [x] Conserver la synchro `afterCommit` qui diffuse `phase-changed` (elle reste propre à `EditionService`).
  - [x] Retirer les usages/imports devenus morts si `basketRepository` / `sseEmitterRegistry` ne servent plus ailleurs dans la classe (vérifier — `basketRepository` sert encore via `findAllByEditionId`, `sseEmitterRegistry` sert encore pour `phase-changed`). Ne rien retirer qui reste utilisé. `BasketCancelledEventDto` n'est probablement plus référencé ici → retirer l'import.
- [x] **T-B3 — `LogoutHandler`** (AC : B1, B2)
  - [x] Nouveau `pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/BasketCancellingLogoutHandler.java`, `@Component @RequiredArgsConstructor @Slf4j`, `implements org.springframework.security.web.authentication.logout.LogoutHandler`.
  - [x] Dépendances : `EditionRepository`, `BasketRepository`, `BasketCancellationService`.
  - [x] `logout(request, response, authentication)` : si `authentication == null` ou `!(authentication.getPrincipal() instanceof PluriBourseUserDetails principal)` → return. Sinon `try { editionRepository.findFirstByPhaseIn(PhaseType.ACTIVE).ifPresent(edition -> basketRepository.findByEditionIdAndUserId(edition.getId(), principal.getUserId()).ifPresent(basketCancellationService::cancelBasketSilently)); } catch (RuntimeException e) { log.warn("Failed to cancel active POS basket on logout", e); }`.
  - [x] `PhaseType.ACTIVE` est `List.of(DEPOSIT, SALE, POST_SALE)` (constante de l'enum) — `findFirstByPhaseIn` résout donc l'édition active quelle que soit sa phase parmi les trois. Un `Basket` n'existe qu'en phase Vente : hors Vente, `findByEditionIdAndUserId` renvoie `Optional.empty()` et le handler ne fait rien (comportement voulu, AC-B2).
  - [x] `cancelBasketSilently` ne prend que le `Basket` : pas besoin de propager `editionId` ni la phase (aucun événement). Le `Basket` est ici **détaché** (le `LogoutFilter` n'ouvre pas de transaction) — `cancelBasketSilently` étant `@Transactional`, le `deleteAll(List.of(basket))` merge l'entité détachée (comportement standard Spring Data).
  - [x] Utiliser le **paramètre** `authentication` (capturé par `LogoutFilter` avant tout handler), jamais `SecurityContextHolder` — l'ordre vis-à-vis de `invalidateHttpSession` est alors sans importance (travail purement BDD).
  - [x] Aucune donnée perso dans le message de log.
- [x] **T-B4 — Câblage `SecurityConfig`** (AC : B1)
  - [x] Ajouter le paramètre `BasketCancellingLogoutHandler basketCancellingLogoutHandler` à `filterChain(...)`.
  - [x] Dans `.logout(logout -> logout...)`, ajouter `.addLogoutHandler(basketCancellingLogoutHandler)` (garder `.logoutUrl`, `.logoutSuccessHandler`, `.invalidateHttpSession(true)`, `.deleteCookies(...)`).
- [x] **T-B5 — Nettoyage `application.properties`** (AC : B4)
  - [x] Supprimer la ligne `server.servlet.session.timeout=2h` **et** son commentaire `# Idle timeout: auto-logout after 2h ...`.
  - [x] Ajuster le commentaire de `spring.session.timeout=PT1H` pour qu'il soit l'unique référence du délai d'inactivité (retirer la mention « takes precedence over server.servlet.session.timeout », préciser « 1 h, source de vérité unique »).
- [x] **T-B6 — Test Partie B**
  - [x] Nouveau `PosBasketLogoutCancellationIT extends IntegrationTest` (E2E via MockMvc, H2, story-board `@Order`) : créer édition → phase Dépôt → vendeur + lot + articles → phase Vente → login **volunteer1 et volunteer2** (`setUpSessions` calqué sur `PosBasketIT`) → **chaque** bénévole ouvre son panier (`GET /pos/baskets/current`) et y ajoute un article (`POST /pos/baskets/{id}/items` : volunteer1 = un membre de lot ⇒ réservation + un article hors lot ; volunteer2 = un article hors lot) → asserts BDD : les **deux** paniers non vides, `lotRepository.findById(lotId).getReservedByBasketId()` == id du panier de volunteer1.
  - [x] `POST /api/auth/logout` **par volunteer1** (`.session(session1).with(csrf())`) → 200 → asserts : panier + `basket_items` de volunteer1 supprimés, `getReservedByBasketId() == null` ; **le panier de volunteer2 et ses `basket_items` sont intacts** (garde-fou du point C1 : la déconnexion ne touche que l'utilisateur qui part).
  - [x] Puis second logout / logout sans panier → toujours 200 (best-effort, pas de NPE).
  - [x] Ne pas asserter l'émission ni l'absence de SSE (pas de client abonné en MockMvc) ; se limiter à l'état BDD + statut HTTP. L'absence de broadcast au logout est garantie par construction (`cancelBasketSilently` n'enregistre aucune synchro).
  - [x] Vérifier que les IT de Story 2.8 (annulation panier au changement de phase) et les IT de logout existantes (`PasswordChangeFlowIT`, `LanguagePreferenceIT`) restent vertes.

### Vérification finale

- [x] `./mvnw clean package` vert (dont `SaleConcurrencyIT` réécrit, exécuté là où Docker est présent — skip propre sinon).
- [x] `cd pluribourse-frontend && npm test` vert ; `npm run build` sans warning.
- [x] Parité i18n `fr.json` ⇔ `en.json` (`volunteer.pos.error.lotReserved` dans les deux, `lotAlreadySold` conservée).
- [x] Vérification visuelle par Manerial (voir « Vérification visuelle »).

---

## Dev Notes

### Patrons d'architecture & contraintes

- **Modèle de concurrence des lots (architecture.md § Concurrence — POS, l.234-239, déjà amendé)** : réservation légère = jeton de revendication optimiste, `UPDATE lots SET reserved_by_basket_id=:b, reserved_at=:now WHERE id=:l AND (reserved_by_basket_id IS NULL OR reserved_by_basket_id=:b)`. **Aucun verrou pessimiste**, aucun `SELECT ... FOR UPDATE`, aucun verrou tenu entre deux requêtes. 0 ligne modifiée ⇒ 409 `lot-reserved`. Deux paniers ne pouvant plus détenir de membres du même lot, **la course multi-postes à la validation ne peut plus se produire** → c'est la raison pour laquelle `bumpVersion` et sa machinerie sont retirés.
- **Sémantique « affected rows » MariaDB / H2** : la clause `OR reserved_by_basket_id = :basketId` rend `reserveLot` idempotent pour le panier détenteur. Le connecteur MariaDB (`useAffectedRows=false` par défaut) et H2 renvoient le nombre de lignes **trouvées** (matched), pas seulement modifiées → un re-`reserveLot` du même panier renvoie `1`. Le garde-fou côté service (`findAllByBasketIdAndItemLotId(...).isEmpty()` dans `addItem` : on ne réserve qu'au **premier** membre) évite de dépendre de cette subtilité pour le chemin nominal ; AC-A1 (f) la teste explicitement.
- **`@Modifying` bulk update** : `reserveLot` / `releaseLot` / `releaseAllByBasketId` court-circuitent le contexte de persistance (comme `bumpVersion` avant). Ne pas relire l'entité `Lot` managée après ces appels dans la même transaction en s'attendant à voir la nouvelle valeur ; le service lit toujours l'état via des requêtes fraîches (`existsByLotIdAndSoldTrue`, `findAllByBasketIdAndItemLotId`) ou n'en a pas besoin.
- **Ordre des gardes dans `addItem`** : (1) `requireOwnedBasket` (IDOR) → (2) `posScanService.scan` (phase + format + not-found + `item-already-sold` + `lot-already-sold` frère committé) → (3) `ItemAlreadyInBasketException` → (4) **réservation** → (5) insert `BasketItem`. La réservation vient après le check « déjà dans le panier » (aucune mutation nécessaire si l'article y est déjà) et avant l'insert (pour que `findAllByBasketIdAndItemLotId(...).isEmpty()` signifie bien « aucun membre encore présent »).
- **`validate()` : deux gardes lot distinctes, une seule conservée.** Le pré-check `existsByLotIdAndSoldTrue` (frère vendu dans une vente committée) **reste** — la réservation ne couvre pas ce cas (panier disparu, réservation déjà levée). Le force-increment `bumpVersion` **part** — redondant une fois la réservation en place.
- **Pas de cycle de dépendances** : `PosBasketService` dépend de `EditionService`. Donc `EditionService` **ne peut pas** injecter `PosBasketService`. La routine d'annulation va dans un **nouveau** `BasketCancellationService` (repos + SSE uniquement), injecté par `EditionService` **et** par le `LogoutHandler`.
- **Notification de déconnexion — pas de SSE (point C1).** `SseEmitterRegistry.broadcast` diffuse à **tous** les emitters POS connectés et `BasketCancelledEventDto(editionId, newPhase)` ne porte **aucune identité utilisateur**. Émettre `basket-cancelled` à la déconnexion annulerait donc le panier **en cours** de tous les autres caissiers (panier vidé, scanner désactivé jusqu'au reload, toast « La phase a changé » — sur des postes où rien n'a changé). Le chemin déconnexion (`cancelBasketSilently`) n'émet **rien**. Les autres onglets du **même** utilisateur (même navigateur) partagent le cookie de session, invalidé par `invalidateHttpSession(true)` + `deleteCookies` : ils tombent en 401 → `/login` à leur prochaine requête. La première rédaction de FR-110 / architecture / epics (« notifiés via `basket-cancelled` », « même mécanisme que FR-090 ») supposait à tort que le broadcast était ciblé ; elle est corrigée par cette story (voir « Correction d'artefacts portée par cette story »).
- **Ordre des broadcasts SSE au changement de phase (point E1).** Aujourd'hui `savePhaseThenSendEvent` émet `phase-changed` puis `basket-cancelled` dans un unique `afterCommit`. Après délégation à `cancelBaskets` (qui enregistre sa propre synchro `afterCommit`), l'ordre dépend de l'ordre d'enregistrement. Enregistrer la synchro `phase-changed` **avant** d'appeler `cancelBaskets(...)` conserve l'ordre actuel. Sans impact fonctionnel (handlers front indépendants, aucun test n'observe le SSE en MockMvc) — mais autant ne pas le changer sans raison.
- **`LotService.update` et les nouvelles colonnes (point E2).** `Lot` n'a pas `@DynamicUpdate` → `LotService.update` fait un `repository.save(lot)` qui réécrit **toutes** les colonnes, `reserved_by_basket_id`/`reserved_at` comprises. C'est **sans danger** : `create`/`update`/`delete` de `LotService` sont tous `PhaseGuard.requireDepositPhase`, or une réservation n'existe qu'en phase **Vente** — aucun recouvrement, aucun risque d'écraser une réservation. Ne **pas** ajouter `@DynamicUpdate` ni de relecture défensive.
- **`LogoutHandler` & cycle de vie** : `LogoutFilter` capture l'`Authentication` **avant** d'exécuter le moindre handler et le passe en paramètre à chaque handler du `CompositeLogoutHandler`. Utiliser ce paramètre (pas `SecurityContextHolder`) rend l'ordre d'exécution vis-à-vis de `SecurityContextLogoutHandler` / `invalidateHttpSession` **sans effet** sur la correction (le handler ne touche que la BDD). `cancelBasketSilently` étant `@Transactional`, le nettoyage s'exécute dans sa propre transaction (le filtre de logout n'en ouvre pas) ; le `Basket` résolu hors transaction y arrive détaché → `deleteAll` le merge. `PhaseType.ACTIVE` = `List.of(DEPOSIT, SALE, POST_SALE)` : `findFirstByPhaseIn(PhaseType.ACTIVE)` résout l'édition active dans n'importe laquelle des 3 phases, mais un `Basket` n'existe qu'en Vente (sinon `findByEditionIdAndUserId` est vide, no-op — point E4).
- **`/auth/logout`** est un POST protégé CSRF (non listé dans `ignoringRequestMatchers`) — inchangé ; les tests doivent envoyer `.with(csrf())`.
- **Style (CLAUDE.md)** : accolades obligatoires sur tout `if`/`for` même mono-ligne ; **types explicites**, **jamais `var`** ; JavaDoc sur la logique non triviale (`reserveLot`, `cancelBaskets` / `cancelBasketSilently`, `BasketCancellingLogoutHandler`, `LotReservedException`) ; pas de donnée perso dans les logs.
- **Tests backend (CLAUDE.md)** : E2E par les contrôleurs, une classe = un scénario story-board (`@TestMethodOrder` + `@Order`), données persistantes entre méthodes, extends `IntegrationTest`. **Exception documentée** : `SaleConcurrencyIT` (Testcontainers + `TransactionTemplate` directs, sans contrôleur) — nécessaire pour une vraie course entre transactions, déjà acté à la création de la Story 4.4.
- **Front (CLAUDE.md)** : composants standalone, Signals, **jamais** de template inline. Ici seuls `pos-page.component.ts` (branches d'erreur) et les JSON i18n changent — pas de nouveau HTML.

### Fichiers à modifier — état actuel / ce qui change / ce qui doit être préservé

| Fichier | État actuel | Ce que la story change | À préserver |
|---|---|---|---|
| `db/changelog/035-lot-reservation.xml` | n'existe pas (dernier = `034`) | **NEW** — 2 colonnes sur `lots` + FK `ON DELETE SET NULL` | convention Liquibase (id `NNN-nom`, `author="pluribourse"`, `<rollback>` explicite) |
| `db/changelog/db.changelog-master.xml` | 33 `<include>` (le `003` est absent), jusqu'à `034` | +1 `<include>` `035` en dernière ligne | ordre |
| `domain/item/entity/Lot.java` | `id`, FKs, `name`, `globalPrice`, `items` (`@OneToMany EAGER`), `@Version version` | +`reservedByBasketId` (`Long`), +`reservedAt` (`LocalDateTime`) | `@Version` (utilisé par `LotService.update`), `items` EAGER |
| `domain/item/repository/LotRepository.java` | 1 seule méthode : `bumpVersion` | **−`bumpVersion`** ; +`reserveLot`, +`releaseLot`, +`releaseAllByBasketId` | (le repo n'a pas d'autre usage) |
| `domain/pos/service/PosBasketService.java` | `addItem` (scan + `ItemAlreadyInBasket` + insert) ; `removeItem` / `removeLot` (delete simple) ; `validate` (pré-check `existsByLotIdAndSoldTrue` **trié P5** + boucle `bumpVersion` + `isLotVersionRace` + catch per-item `@Version`) | `addItem` réserve au 1ᵉʳ membre ; `removeItem` libère au dernier membre ; `removeLot` libère ; `validate` : pré-check **non trié** conservé, **boucle `bumpVersion` supprimée**, `isLotVersionRace` supprimée, `releaseAllByBasketId` avant `delete(basket)` | catch per-item `ObjectOptimisticLockingFailureException`/`SnapshotIsolationException` (garde `@Version` article, Story 4.4) ; `isCausedBy` ; `ItemAlreadyInBasketException` + son catch `DataIntegrityViolationException` ; `createBasket` ; `requireOwnedBasket` (IDOR) ; garde de phase (AC 9) sur les 5 méthodes |
| `domain/pos/exception/LotReservedException.java` | n'existe pas | **NEW** — `BusinessException` 409 `lot-reserved` | forme identique à `LotAlreadySoldException` (pas de handler dédié) |
| `domain/pos/service/BasketCancellationService.java` | n'existe pas | **NEW** — `cancelBaskets(list, phase)` : release réservations + `deleteAll` + `basket-cancelled` différé (changement de phase) ; `cancelBasketSilently(basket)` : mêmes suppressions **sans** événement (déconnexion) | — |
| `domain/edition/service/EditionService.java` | `savePhaseThenSendEvent` : `findAllByEditionId` + `deleteAll` inline + `basket-cancelled` dans la synchro `afterCommit` (aux côtés de `phase-changed`) | délègue l'annulation à `basketCancellationService.cancelBaskets(...)` (appel placé **après** le `registerSynchronization` du `phase-changed` pour garder l'ordre des broadcasts) ; garde la synchro `phase-changed` | toute la machine à états de phase (`computeNextPhase`/`computePreviousPhase`, gardes `EditionAlreadyActive`/`NoCategories`/`NoVolunteer`, `closePostSaleToClosed`) ; l'émission `phase-changed` après commit ; le fait qu'un seul `basket-cancelled` soit émis par changement de phase |
| `shared/security/handlers/BasketCancellingLogoutHandler.java` | n'existe pas (aucun `LogoutHandler` custom dans le projet) | **NEW** — annule le panier actif du principal pour l'édition active, best-effort | — |
| `shared/security/SecurityConfig.java` | `.logout(logoutUrl, logoutSuccessHandler, invalidateHttpSession(true), deleteCookies)` | +`.addLogoutHandler(basketCancellingLogoutHandler)` + param méthode | `.invalidateHttpSession(true)`, `.deleteCookies("JSESSIONID","SESSION")`, `logoutSuccessHandler` (200), le reste de la chaîne (CSRF cookie path `/`, `authorizeHttpRequests`, `ForcePasswordChangeFilter`, provider inline) |
| `application.properties` | l.18-25 : `spring.session.store-type=jdbc`, `spring.session.timeout=PT1H` (l.21), **`server.servlet.session.timeout=2h` (l.23, morte)**, `spring.session.jdbc.initialize-schema=never` | supprime l.22-23 (commentaire + prop morte) ; `spring.session.timeout=PT1H` = source unique | `store-type=jdbc`, `PT1H`, `initialize-schema=never`, tout le reste du fichier |
| `features/volunteer/pos/pos-page.component.ts` | `handleScanError` / `handleValidationError` : branches `item-already-sold`, `lot-already-sold`, `item-not-found`, `item-already-in-basket`, `basket-validation-conflict`, `no-active-edition` | +branche `/lot-reserved` dans les **deux** méthodes | toutes les autres branches, les gardes `basketCancelled()` (Story 4.6), `scanInFlight`/`removeInFlight`/`validateInFlight` |
| `public/i18n/fr.json` & `en.json` | `volunteer.pos.error.{alreadySold,lotAlreadySold,notFound,generic,conflict,phaseChanged}` | +`lotReserved` dans les deux | `lotAlreadySold` (cas frère committé, AC-A5) |

### Tests — fichiers concernés

| Fichier | Action |
|---|---|
| `pluribourse-backend/.../pos/SaleConcurrencyIT.java` | **Réécrire** la méthode lot (course à `addItem`, plus à `validate`) ; garder la méthode article ; MAJ JavaDoc de classe |
| `pluribourse-backend/.../pos/PosBasketIT.java` | **Ajouter** des `@Order` en fin de story-board (réservation, libérations, idempotence même panier) ; vérifier @Order 20 toujours vert |
| `pluribourse-backend/.../pos/PosScanIT.java` | Vérifier @Order 13 vert (scan inchangé) — pas de nouveau test attendu |
| `pluribourse-backend/.../pos/PosBasketLogoutCancellationIT.java` | **NEW** — E2E logout : panier + `basket_items` supprimés, réservations à `NULL`, 200 même sans panier |
| `pluribourse-frontend/.../pos/pos-page.component.spec.ts` | **Ajouter** 2 cas `lot-reserved` (scan + validation), calqués sur les cas `lot-already-sold` existants (l.130, l.237) |

### Previous story intelligence (Story 5.8 + revue de code 2026-09-03)

- La Story 5.8 a livré FR-109 comme **détection à la validation** (`bumpVersion` + `isLotVersionRace` + catch `JpaSystemException` de l'isolation snapshot). La revue de code a tranché (point D1, option 3 puis correct-course) : ce mécanisme est **remplacé**, pas corrigé.
- Le patch **P5** de la revue 5.8 (tri `Comparator.comparing(r -> r.getLot().getId())` de `lotRepresentatives` dans `validate()`) était un contournement du deadlock 1213 — il **disparaît** avec `bumpVersion`.
- La Story 5.8 review MAJ a aussi nettoyé `PosBasketService` (P1) : suppression d'un `catch (ObjectOptimisticLockingFailureException | OptimisticLockException)` mort sur le bulk `@Modifying`, retrait de l'import `OptimisticLockException`. Ne pas réintroduire.
- `existsByLotIdAndSoldTrue` (ajout 5.8) et ses 2 call sites sont **explicitement conservés** (SCP §5).
- `ScanResultDto` porte déjà `lotId` (ajout Story 5.8 / 4.3) — `addItem` n'a pas besoin de recharger le `Lot` pour savoir si l'article appartient à un lot.
- `SessionInvalidationService` (Story 1.12) enveloppe `FindByIndexNameSessionRepository.findByPrincipalName` pour révoquer les sessions d'un principal — c'est le **modèle** de code best-effort/logué à suivre pour le `LogoutHandler`, mais la Partie B n'a pas besoin de résoudre une session : le `LogoutHandler` reçoit l'`Authentication` en paramètre, `principal.getUserId()` suffit.

### Git intelligence

- `274e337` « Correct course » = les amendements d'artefacts de la SCP 2026-09-03 (prd/epics/architecture/sprint-status). C'est le `baseline_commit` de cette story.
- `c7d19c1` « Add story 5.8 » : a introduit `bumpVersion`, `existsByLotIdAndSoldTrue`, `LotAlreadySoldException`, la garde scan + le pré-check validation, `SaleConcurrencyIT` méthode lot, la branche front `lot-already-sold`. C'est la surface exacte que cette story ré-architecture.
- `9afd5b5` « Add story 4.7 » : dernière évolution `pos-page.component.ts` (auto-print facture) — le fichier est stable, les gardes `basketCancelled()` datent de 4.6.
- Convention Liquibase récente : `033-lot-category`, `034-archived-item-lot` — `addColumn` nullable sans `defaultValue` ; `034` a choisi **pas de FK** vers une table d'archive — ne PAS transposer ce choix ici (`baskets` n'est pas une archive, la SCP tranche pour une FK `ON DELETE SET NULL`).

### Latest tech information

- **Liquibase** `<addForeignKeyConstraint onDelete="SET NULL">` : supporté H2 + MariaDB, aucune option spécifique. La colonne `reserved_by_basket_id` doit être nullable (elle l'est) pour que `SET NULL` soit légal.
- **Spring Boot 4 / Spring Security 6.x** : `HttpSecurity.logout(...).addLogoutHandler(LogoutHandler)` ajoute au `CompositeLogoutHandler` du `LogoutFilter`. `LogoutFilter` lit `Authentication` une fois en début de `doFilter` et le passe à tous les handlers — indépendamment de leur ordre et de `invalidateHttpSession`. `spring-session-jdbc` est actif (`pom.xml` + `spring.session.store-type=jdbc`) : `server.servlet.session.timeout` est **ignoré** au profit de `spring.session.timeout` — d'où la suppression de la ligne morte.
- **Spring Data JPA** `@Modifying` : par défaut ni `flushAutomatically` ni `clearAutomatically`. Comportement identique à `bumpVersion` aujourd'hui — pas besoin de les activer ici (le service ne relit pas l'entité managée après coup).

### Project Structure Notes

- Migration : `pluribourse-backend/src/main/resources/db/changelog/035-lot-reservation.xml` + `<include>` dans `db.changelog-master.xml`. Le changelog de test (`src/test/resources/db/changelog/db.changelog-test.xml`) inclut le master → `035` s'applique aussi en test H2, aucune action.
- Exceptions POS : `domain/pos/exception/` (à côté de `LotAlreadySoldException`).
- Routine d'annulation : `domain/pos/service/BasketCancellationService.java` (paquet `pos`, pas `edition` — c'est du domaine panier).
- `LogoutHandler` : `shared/security/handlers/` (à côté de `LogoutSuccessHandler`).
- Front : aucun nouveau fichier — `pos-page.component.ts` + `public/i18n/{fr,en}.json`.

### Correction d'artefacts portée par cette story

Trois lignes commitées en `274e337` décrivaient la notification de déconnexion comme un `basket-cancelled` SSE « vers ses autres onglets » (« même mécanisme que FR-090 »). Ce mécanisme est un *broadcast* non ciblé (voir Dev Notes « Notification de déconnexion — pas de SSE ») : il annulerait le panier des autres caissiers. La story le remplace par une déconnexion silencieuse côté panier + déconnexion naturelle des autres onglets via la session invalidée. **Éditer (working tree, non commité — Manerial gère ses commits) :**

- **`prds/prd-PluriBourse-2026-06-08/prd.md`** (FR-110, l.~233) : remplacer « Les autres onglets du même utilisateur sont notifiés via l'événement SSE `basket-cancelled` (même mécanisme que FR-090). » par « Les autres onglets du même utilisateur, partageant la session ainsi invalidée, sont déconnectés à leur tour (401 → écran de connexion à la requête suivante) ; **aucun** événement SSE dédié n'est émis pour la déconnexion — un `basket-cancelled` serait diffusé à tous les postes. »
- **`architecture.md`** § Notification de Changement de Phase, ligne « Déclencheur (bis) » (l.~251) : remplacer « annule son panier actif et émet `basket-cancelled` vers ses autres onglets (FR-110) » par « annule son panier actif et libère ses réservations de lot (FR-110), **sans** émettre `basket-cancelled` — la session invalidée déconnecte d'elle-même les autres onglets du même utilisateur » ; dans la cellule justification, remplacer « Réutilise le même événement et le même `SseEmitterRegistry` que la transition de phase » par « Réutilise la routine d'annulation de panier partagée (`BasketCancellationService`) ; pas de broadcast (il toucherait les autres caissiers) ».
- **`epics.md`** l.85 et l.307 (lignes miroir FR-110) : remplacer « les autres onglets de l'utilisateur reçoivent l'évènement SSE `basket-cancelled` » par « les autres onglets du même utilisateur sont déconnectés à leur tour (session partagée invalidée) ».

Le fichier SCP `sprint-change-proposal-2026-09-03.md` (historique, `status: approved`) **n'est pas retouché** : sa supposition « même mécanisme que FR-090 » est corrigée ici, pas réécrite dans la proposition datée.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-03.md] — spec complète, §4 (propositions détaillées P-*), §5 (« Parties de la Story 4.8 », « Points de conception figés », « Critères de succès », « Questions ouvertes » toutes tranchées).
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS (Point de Vente)] — l.234 (stratégie de verrouillage), l.237 (intégrité des lots — réservation), l.239 (exigence de test).
- [Source: _bmad-output/planning-artifacts/architecture.md#Notification de Changement de Phase (FR-090)] — l.251 « Déclencheur (bis) » (déconnexion → annulation du panier ; **à corriger** : ne passe pas par `basket-cancelled`, voir « Correction d'artefacts portée par cette story »).
- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.3 : Gestion des lots au POS] — bloc AC FR-109 réécrit (l.~1576-1588).
- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.4] — note « lot ≠ `@Version` ».
- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.8] — note « routine d'annulation extraite + libération des réservations ».
- [Source: prds/prd-PluriBourse-2026-06-08/prd.md] — FR-109 (réécrit), FR-110 (nouveau), FR-066 (amendé, 1 h).
- [Source: pluribourse-backend/.../pos/service/PosBasketService.java] — `addItem` l.80-101, `removeItem` l.103-111, `removeLot` l.118-128, `validate` l.140-264 (pré-check l.159-176, bloc `bumpVersion` l.194-226 à supprimer, `isLotVersionRace` l.343-353 à supprimer, `isCausedBy` l.326-333 à garder).
- [Source: pluribourse-backend/.../edition/service/EditionService.java] — `savePhaseThenSendEvent` l.210-233 (annulation panier l.215-221 + synchro `afterCommit` l.223-231).
- [Source: pluribourse-backend/.../shared/security/SecurityConfig.java] — `.logout(...)` l.80-85.
- [Source: pluribourse-backend/.../shared/security/SessionInvalidationService.java] — modèle best-effort/logué (`findByPrincipalName`, catch `RuntimeException` + `log.warn`).
- [Source: pluribourse-backend/src/main/resources/db/changelog/022-items-sale-fk-set-null.xml] — modèle `addForeignKeyConstraint ... onDelete="SET NULL"`.
- [Source: pluribourse-backend/src/main/resources/db/changelog/021-pos-baskets.xml] — schéma `baskets` / `basket_items` (FK `deleteCascade="true"`, `uk_baskets_edition_user`).
- [Source: pluribourse-backend/.../pos/SaleConcurrencyIT.java] — méthode lot l.229-318 à réécrire ; méthode article l.136-224 à conserver ; exception documentée à la règle E2E-par-contrôleur (JavaDoc l.51-71).
- [Source: pluribourse-frontend/.../pos/pos-page.component.ts] — `handleValidationError` l.247-265, `handleScanError` l.267-293.
- [Source: pluribourse-frontend/.../pos/pos-page.component.spec.ts] — cas `lot-already-sold` scan l.130-136, validation l.237-246.
- [Source: pluribourse-frontend/public/i18n/fr.json] — `volunteer.pos.error` l.487-494.

### Vérification visuelle (Manerial)

À faire manuellement après implémentation (l'IA ne modifie pas la base de dev locale, ne teste pas visuellement) :

1. Deux navigateurs / deux comptes bénévoles, phase Vente. Poste A scanne un article d'un lot → poste B scanne un autre membre du même lot → **message inline rouge** « Ce lot est en cours d'encaissement sur une autre caisse. », l'article n'entre pas dans le panier de B.
2. Poste A retire ce dernier membre du lot (ou « Retirer le lot entier », ou valide) → poste B peut alors scanner le lot.
3. Poste A avec un panier non vide (dont un lot) → se déconnecte. **Vérifier** : (a) à la reconnexion de A, panier vide ; (b) si un second onglet de A était ouvert, il bascule sur l'écran de connexion à la première action ; (c) **le panier en cours du poste B (autre bénévole) n'a pas bougé** — la déconnexion de A ne concerne que lui (garde-fou C1).
4. Redémarrer le conteneur serveur pendant une session active → l'utilisateur **n'est pas** déconnecté (FR-066, persistance JDBC intacte).

### Points à valider avec Manerial (challengeables)

- **Routine d'annulation — implémentation retenue** : `BasketCancellationService` dédié (repos + SSE) avec **deux points d'entrée** — `cancelBaskets(list, phase)` (broadcast `basket-cancelled`, chemin changement de phase) et `cancelBasketSilently(basket)` (aucun événement, chemin déconnexion). Injecté par `EditionService` **et** le `LogoutHandler`. Rejeté : une méthode sur `EditionService` (cycle `PosBasketService → EditionService`) ou sur `PosBasketService` (pas de `SseEmitterRegistry`). Le silence au logout est **imposé** par le point C1 (le broadcast `basket-cancelled` n'est pas ciblé → il annulerait le panier des autres caissiers), ce n'est pas un choix ouvert. Alternative plus minimale si jugée préférable : ne PAS créer `cancelBaskets` du tout, ajouter seulement `releaseAllByBasketId` avant `deleteAll` dans `savePhaseThenSendEvent`, et n'exposer que `cancelBasketSilently` — mais on perd la routine unique partagée (AC-B3).
- **`reserved_at`** : conservé comme colonne (diagnostic/observabilité, jamais lu par la logique) conformément aux points figés SCP §5. Peut être retiré si Manerial préfère une seule colonne.

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (bmad-dev-story workflow)

### Debug Log References

- `./mvnw test` (backend) — 571 tests, 0 failure, 0 erreur, 3 skipped (`SaleConcurrencyIT` ×2 : Docker absent dans l'environnement de dev, skip propre via `@Testcontainers(disabledWithoutDocker = true)` ; +1 skip préexistant hors périmètre). BUILD SUCCESS.
- `npm test` (frontend) — 67 fichiers, 719 tests, tous verts.
- `npm run build` (frontend) — bundle généré, aucun warning, aucune erreur.
- Parité i18n `volunteer.pos.error` vérifiée par script : clés identiques `fr.json` ⇔ `en.json` (`alreadySold,conflict,generic,lotAlreadySold,lotReserved,notFound,phaseChanged`).
- `grep -rn "bumpVersion|isLotVersionRace|server.servlet.session.timeout" src/` — aucune occurrence résiduelle (main + test).

### Completion Notes List

**Partie A — Réservation de lot**

- Migration `035-lot-reservation.xml` : `reserved_by_basket_id` (BIGINT nullable) + `reserved_at` (DATETIME nullable) sur `lots`, FK `fk_lots_reserved_basket` → `baskets(id)` `ON DELETE SET NULL`, `<rollback>` explicite, `<include>` ajouté en dernière ligne du master (après `034`). S'applique aussi en test H2 via `db.changelog-test.xml`.
- `Lot` : colonnes ajoutées (identifiant brut, pas de `@ManyToOne`), import `java.time.*` (style wildcard du fichier), `@Version` intact.
- `LotRepository` : `bumpVersion` supprimé ; `reserveLot` (jeton optimiste idempotent pour le panier détenteur), `releaseLot`, `releaseAllByBasketId` ajoutés (+ JavaDoc).
- `LotReservedException` (`BusinessException` 409 `lot-reserved`, ne porte que le `lotId`).
- `PosBasketService` : `addItem` réserve au 1ᵉʳ membre du lot ; `removeItem` libère quand le dernier membre part ; `removeLot` libère ; `validate` — pré-check `existsByLotIdAndSoldTrue` **non trié** conservé, bloc `bumpVersion` + `isLotVersionRace` **supprimés**, `releaseAllByBasketId(basket.getId())` avant `delete(basket)`, JavaDoc réécrit ; imports `Comparator` + `java.sql.SQLException` retirés, `LotReservedException` ajouté ; catch per-item `@Version` (Story 4.4) intact.
- **Écart assumé vs T-A5 (à valider — voir plus bas)** : la prise de réservation dans `addItem` est enveloppée dans un helper privé `reserveLotForBasket(lotId, basketId)` qui, en plus du cas « 0 ligne → `LotReservedException` », **capture `JpaSystemException` causé par `SnapshotIsolationException`** et le convertit aussi en `LotReservedException`. Sans ce catch, sur MariaDB avec `innodb_snapshot_isolation` **ON** (défaut depuis 11.6.2 — la raison même de la machinerie `isLotVersionRace` de la Story 5.8), l'`UPDATE` perdant sur la ligne `lots` concurremment modifiée est **rejeté d'emblée (erreur 1020)** au lieu de matcher 0 ligne → `addItem` sortirait en **500**, ce qui viole explicitement AC-A2 (« aucune réponse 500 n'est possible sur ce chemin ») et AC-A6. Réutilise `isCausedBy` (déjà présent, encore utilisé par le catch per-item). Défensif, aligné sur les AC, risque faible.
- Front : branche `/lot-reserved` ajoutée dans `handleScanError` **et** `handleValidationError` (`pos-page.component.ts`) ; clé `volunteer.pos.error.lotReserved` ajoutée dans `fr.json` et `en.json` ; `lotAlreadySold` conservée.
- Tests : `SaleConcurrencyIT` — méthode lot **réécrite** (`two_concurrent_add_item_of_two_members_of_the_same_lot_exactly_one_reserves`, course sur `addItem` via `TransactionTemplate`, empty baskets, assertions : 1 succès / 1 `LotReservedException` / `saleRepository.count()==0` / `reservedByBasketId` == gagnant / `reservedAt` non nul) ; méthode article **inchangée** ; JavaDoc de classe réécrit (refs de lignes obsolètes retirées). `PosBasketIT` — 2 `@Order` ajoutés (`@Order(21)` cycle réservation/idempotence/409 autre panier/libération removeItem+removeLot ; `@Order(22)` libération à `validate`), 2 nouveaux lots dans la fixture (`Lot Réservation A/B`, barcodes 0012-0015), autowire `LotRepository`, tail `@Order` 21-24 renumérotés 25-28, `@Order(20)` (lot-already-sold) toujours vert. `PosScanIT` `@Order(13)` toujours vert (scan inchangé).

**Partie B — Annulation à la déconnexion**

- `BasketCancellationService` (`domain/pos/service`, `@Service @RequiredArgsConstructor`, deps : `BasketRepository` + `LotRepository` + `SseEmitterRegistry`, aucun cycle) : `cancelBaskets(Collection<Basket>, PhaseType)` — libère les réservations, `deleteAll`, **un** `basket-cancelled` en `afterCommit` (chemin changement de phase) ; `cancelBasketSilently(Basket)` — mêmes libérations/suppressions, **aucune** synchro, **aucun** broadcast (chemin déconnexion) ; JavaDoc expliquant le blast-radius du broadcast non ciblé.
- `EditionService.savePhaseThenSendEvent` : injecte `BasketCancellationService`, enregistre la synchro `phase-changed` **puis** appelle `cancelBaskets(basketRepository.findAllByEditionId(id), newPhase)` (ordre des broadcasts `phase-changed` → `basket-cancelled` conservé) ; suppression inline + `BasketCancelledEventDto` local retirés.
- `BasketCancellingLogoutHandler` (`shared/security/handlers`, `implements LogoutHandler`, `@Component @RequiredArgsConstructor @Slf4j`) : lit l'`Authentication` **en paramètre**, résout l'édition active (`findFirstByPhaseIn(PhaseType.ACTIVE)`), le panier du principal, délègue à `cancelBasketSilently` ; `try/catch (RuntimeException)` + `log.warn` sans donnée perso ; no-op hors phase Vente (aucun panier).
- `SecurityConfig` : paramètre `BasketCancellingLogoutHandler` ajouté à `filterChain`, `.addLogoutHandler(basketCancellingLogoutHandler)` dans `.logout(...)` (reste de la chaîne intact).
- `application.properties` : ligne morte `server.servlet.session.timeout=2h` + son commentaire **supprimés** ; commentaire de `spring.session.timeout=PT1H` réécrit (« 1 h, source de vérité unique », FR-066).
- Test : `PosBasketLogoutCancellationIT` (E2E MockMvc/H2, 4 `@Order`) — 2 bénévoles remplissent chacun un panier (volunteer1 détient une réservation de lot), `POST /auth/logout` volunteer1 → 200 + panier/`basket_items` de volunteer1 supprimés + `reservedByBasketId` null + **panier de volunteer2 intact** ; logout répété + logout admin sans panier → 200. SSE non assertée. IT Story 2.8 (`PosBasketCancellationIT`) + logout ITs (`PasswordChangeFlowIT`, `LanguagePreferenceIT`) toujours vertes.

**Correction d'artefacts (working tree, non commité — Manerial gère ses commits)**

- `prd.md` (FR-110), `architecture.md` (§ Notification de Changement de Phase, « Déclencheur (bis) »), `epics.md` (l.85 + l.307) : la clause SSE de la déconnexion était **déjà corrigée** dans le working tree au démarrage de la story (marqueurs « clause SSE corrigée par la Story 4.8 » présents), conforme au libellé prescrit — rien à ajouter.

**Point challengeable pour Manerial**

- Le catch `SnapshotIsolationException` → `LotReservedException` dans `addItem` (helper `reserveLotForBasket`) est un ajout par rapport au libellé littéral de T-A5, motivé par AC-A2/AC-A6 (pas de 500 possible) sur MariaDB `innodb_snapshot_isolation=ON`. À confirmer / challenger.

### File List

**Backend — nouveau**

- `pluribourse-backend/src/main/resources/db/changelog/035-lot-reservation.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/LotReservedException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketCancellationService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/BasketCancellingLogoutHandler.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketLogoutCancellationIT.java`

**Backend — modifié**

- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/resources/application.properties`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Lot.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/LotRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`

**Frontend — modifié**

- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

**Artefacts de planification — modifié (working tree, non commité)**

- `_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md`
- `_bmad-output/planning-artifacts/architecture.md`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

| Date | Version | Description | Auteur |
|---|---|---|---|
| 2026-09-04 | 1.0 | Implémentation Story 4.8 : réservation de lot au scan (migration `035`, `PosBasketService`, retrait `bumpVersion`/`isLotVersionRace`/P5) + annulation du panier à la déconnexion explicite (`BasketCancellationService`, `BasketCancellingLogoutHandler`, `SecurityConfig`, nettoyage `application.properties`) + front (`pos-page.component.ts`, i18n). Tests : `SaleConcurrencyIT` (méthode lot réécrite sur `addItem`), `PosBasketIT` (+2 scénarios), nouveau `PosBasketLogoutCancellationIT`. Backend 571 verts / 3 skip (Docker absent), frontend 719 verts. Statut → review. | claude-sonnet-5 |

---

## Review Findings

_Revue de code adversariale (bmad-code-review, 2026-09-04) — 3 revues parallèles : Blind Hunter, Edge Case Hunter, Acceptance Auditor. 2 decision-needed tranchées par Manerial le 2026-09-04 (D1 → patch P4, D2 → defer W5), 4 patch, 5 defer, 15 rejetés._

### Décisions tranchées (2026-09-04)

- **D1 — Le `catch (JpaSystemException)` / `SnapshotIsolationException` réintroduit dans `PosBasketService.reserveLotForBasket`.** Contredit un point figé de la SCP 2026-09-03 §5 + **AC-A7** + **T-A8**, mais le retirer exposerait `addItem` à un **500** sur MariaDB `innodb_snapshot_isolation=ON` (viole **AC-A2**/**AC-A6**). **Décision Manerial : option 2 — garder ET renforcer.** La fenêtre de collision est de l'ordre de la milliseconde (traitements courts), cas rare, mais l'assurance est peu coûteuse. → devient le **patch P4**. AC-A7 / T-A8 / la SCP seront à réaligner (le catch snapshot reste nécessaire sur ce chemin) — hors périmètre d'un patch de code, à acter à la clôture de la story.
- **D2 — La réservation de lot au scan n'a aucun mécanisme d'expiration / purge (FR-066 différé).** Un poste fermé / planté / en timeout d'inactivité 1 h laisse `lots.reserved_by_basket_id` sur un panier mort → lot invendable à toutes les caisses jusqu'au changement de phase. Le back n'a **aucun** moyen fiable de détecter une caisse morte (fermeture d'onglet / plantage / coupure réseau ne produisent aucun signal ; la seule vraie détection = un *heartbeat* envoyé par le front + balayage back). **Décision Manerial : option 1 — livrer 4.8 telle quelle, créer une story de suivi dédiée « détection de poste mort par heartbeat » via correct-course.** Story moyenne (nouvel endpoint ping + colonne `baskets.last_seen_at` + migration + minuteur front + tâche `@Scheduled` réutilisant `BasketCancellationService.cancelBasketSilently` + réglages seuil/UX). → **defer W5**. Pas de balayage `reserved_at` jetable ajouté à 4.8.

### Patch

- [x] **[Review][Patch] P1 — `LotRepository.releaseLot` n'est pas borné au panier détenteur** [pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/LotRepository.java:30] — `UPDATE Lot l SET reservedByBasketId = NULL WHERE l.id = :lotId`, sans prédicat `AND l.reservedByBasketId = :basketId` (contrairement à `releaseAllByBasketId`). Appelé sans garde par `removeItem` (l.118) et `removeLot` (l.137). Sûr uniquement grâce à l'invariant « un panier ne contient que des membres d'un lot qu'il a réservé » ; si cet invariant casse (cf. D1/D2), `removeItem`/`removeLot` sur le panier A effacent la réservation du panier B. Ajouter le prédicat `basketId` (défense en profondeur, forme identique à `releaseAllByBasketId`), passer `basketId` depuis les deux call sites.
- [x] **[Review][Patch] P2 — AC-A4 : le chemin positif « un second poste peut alors scanner un article de ce lot » après validation réussie n'est pas asséré** [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java] — `@Order(22)` vérifie `reservedByBasketId == null` après `validate` mais aucun test ne fait re-réserver le lot par un autre panier **après une vente committée**. Ajouter l'assertion (volunteer2 `addItem` d'un membre du lot B → succès).
- [x] **[Review][Patch] P3 — AC-B2 : la branche « aucune édition active » à la déconnexion n'est pas testée** [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketLogoutCancellationIT.java] — `@Order(4)` couvre « aucun panier » (logout admin) et « logout répété », pas « aucune édition en phase active ». Ajouter un cas : logout alors qu'aucune édition n'est en phase `ACTIVE` → 200, no-op.
- [x] **[Review][Patch] P4 — D1 tranchée : renforcer le `catch` snapshot de `reserveLotForBasket`** [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:306-317] — (a) élargir la détection pour re-couvrir la forme `java.sql.SQLException` brute (erreur MariaDB 1020 nue) en plus de `SnapshotIsolationException`, comme le faisait l'ancien `isLotVersionRace` (ré-ajouter l'import `java.sql.SQLException` retiré) ; (b) neutraliser le faux positif « même caissier, deux membres du même lot scannés en concurrence dans le même panier » : sous snapshot isolation, l'`UPDATE` perdant est rejeté même quand `reservedByBasketId` vaut déjà `:basketId` (re-claim idempotent) — sur `SnapshotIsolationException`, relire `Lot.reservedByBasketId` et, s'il est déjà égal à `basketId`, traiter comme un succès (pas de `LotReservedException`).

### Defer

- [x] **[Review][Defer] W1 — `BasketCancellingLogoutHandler` : `catch (RuntimeException)` large et silencieux** [pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/BasketCancellingLogoutHandler.java] — deferred, pré-existant (patron `SessionInvalidationService`, Story 1.12). Sur échec réel du nettoyage (merge/cascade d'entité détachée, ou ligne déjà supprimée par un changement de phase concurrent), le panier + la réservation sont orphelins alors que le logout renvoie 200 ; seulement un `log.warn`, pas de retry ni de filet. La forme du catch suit un patron établi du projet ; le manque de rattrapage est l'objet de la Décision D2.
- [x] **[Review][Defer] W2 — Déconnexion en course avec un `addItem` du même utilisateur sur un autre onglet** [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:98] — deferred, race multi-onglets peu probable + hypothèse de catch pré-existante. La ligne `basket` est supprimée pendant `addItem` ; l'insert `basket_items` viole la FK ; `catch (DataIntegrityViolationException)` rapporte aveuglément `item-already-in-basket` (409), ou un 500 brut si la forme de la violation diffère.
- [x] **[Review][Defer] W3 — `SaleConcurrencyIT` (AC-A6, Testcontainers MariaDB) skippé faute de Docker** [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java] — deferred. À rejouer avec Docker avant merge : c'est le seul test qui exerce la vraie course `reserveLot` et le chemin snapshot-isolation derrière la Décision D1.
- [x] **[Review][Defer] W4 — `SaleConcurrencyIT.two_concurrent_add_item...` n'assère pas le rollback propre du perdant** [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java] — deferred. Vérifie le gagnant + `saleRepository.count()==0` mais pas que l'`addItem` perdant n'a laissé **aucun** `BasketItem` persisté. Ajouter l'assertion quand Docker est disponible (avec W3).
- [x] **[Review][Defer] W5 — D2 tranchée : aucune expiration / purge des réservations de lot pour un poste mort sans déconnexion explicite** [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:87-92] — deferred, décision Manerial (option 1) : hors périmètre 4.8. **→ Story 4.9** (SCP 2026-09-04, `sprint-change-proposal-2026-09-04.md`) : « détection de poste de caisse inactif et libération du panier » — endpoint heartbeat + `baskets.last_seen_at` (migration 036) + minuteur front + tâche `@Scheduled` (`BasketReaperService`) réutilisant `BasketCancellationService.cancelBasketSilently` + correctif ciblé `validate()` pour Blind Hunter #7. Risque assumé jusqu'à livraison de 4.9 : un lot peut rester réservé jusqu'au changement de phase si un bénévole abandonne son poste sans se déconnecter. — **livrée par la Story 4.9** (2026-09-07). Le point Blind Hunter #7 (réservation non libérée au rollback du pré-check `existsByLotIdAndSoldTrue` de `validate()`) est également corrigé dans la Story 4.9 (`BasketCancellationService.releaseLotReservationInNewTransaction`, `@Transactional(REQUIRES_NEW)`).

### Rejetés (15) — non retenus

Blind Hunter : sécurité d'écriture de `Lot` (les mutations `LotService` sont phase Dépôt uniquement, les réservations phase Vente uniquement — aucun recouvrement, Dev Notes E2) ; ordre des broadcasts SSE (conçu, ordre d'insertion préservé — confirmé par Edge Case Hunter) ; `findFirstByPhaseIn` (invariant « une seule édition active » garanti par `existsByPhaseIn`) ; `addItem` transactionnel (confirmé, l.78) ; retrait `server.servlet.session.timeout` (explicitement dans le périmètre, T-B5) ; trou de numérotation `@Order` (inoffensif) ; commentaire de test imprécis ; canal scan front / `endsWith` (conforme au style du fichier). Edge Case Hunter : autres onglets → 401 → `/login` (déjà géré par `authInterceptor` global) ; lock-wait/deadlock → 500 (`UPDATE` mono-ligne par PK, un lot par appel, `validate()` ne tient plus la ligne — résiduel noté sous D1). Acceptance Auditor : extraction du helper `reserveLotForBasket` (bénin) ; « 3 lignes » (raccourci pour 3 documents ; le diff suit la section détaillée en 4 lignes) ; formulation des artefacts au-delà du texte cité (dans l'intention, présent dans le working tree avant la story) ; « 401 → login » inhérent ; tests Story 2.8 verts (dev rapporte 571).
