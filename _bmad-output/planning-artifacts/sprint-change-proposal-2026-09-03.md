---
title: "Proposition de changement de sprint : Réservation de lot au scan + annulation du panier à la déconnexion"
date: 2026-09-03
status: approved
approved_by: Manerial
approved_date: 2026-09-03
author: Manerial (via Claude Code)
supersedes_decision: "Mécanisme de concurrence FR-109 de la SCP 2026-09-02b (force-increment `Lot.@Version` via `LotRepository.bumpVersion`) + décision de revue de code D1 du 2026-09-03 (ordre de verrouillage canonique par `lot.getId()`, patch P5) — tous deux remplacés par une réservation de lot."
---

# Proposition de changement de sprint : Réservation de lot au scan + annulation du panier à la déconnexion

> Numérotée `-2026-09-03`. Fait suite à la revue de code (`bmad-code-review`) de la **Story 5.8** (2026-09-03) et remplace le mécanisme de concurrence des lots livré par la **SCP 2026-09-02b**.

**Déclencheur :** la revue de code de la Story 5.8 a fait remonter un point `decision-needed` (**D1**) : la garde de concurrence de `PosBasketService.validate()` — un `LotRepository.bumpVersion(id, expectedVersion)` en masse (`UPDATE lots SET version = version + 1 WHERE id = ? AND version = ?`) — peut se manifester en **HTTP 500** (violant l'AC-C3 « jamais un 500 ») quand deux postes valident des paniers touchant les **deux mêmes lots dans un ordre de scan inverse** : deadlock MariaDB 1213 → `CannotAcquireLockException` non rattrapée (aucun handler `DataAccessException` dans `GlobalExceptionHandler`), ou lock-wait timeout 1205. Un correctif de contournement (**P5** : trier la boucle de bump par `lot.getId()`) a été appliqué, le timeout 1205 restant un risque résiduel commenté. Manerial a alors décidé de **remplacer entièrement le mécanisme de verrou optimiste par une réservation légère de lot prise au scan / à l'ajout au panier** — modèle de concurrence plus propre, mais qui rouvre une décision d'architecture gelée (§ Concurrence — POS) → d'où ce correct-course.

**Mode :** revue par lot (run de fond, décisions pré-arrêtées par Manerial — voir « Points de conception figés »). Vérification du code réel effectuée avant rédaction.

---

## 1. Résumé du problème identifié

| # | Sujet | Catégorie BMAD |
|---|---|---|
| D1 | La garde de course multi-postes de FR-109 (`bumpVersion` en masse sur `Lot.@Version`) prend un **verrou d'écriture de ligne** que deux paniers multi-lots peuvent acquérir en ordre croisé → **deadlock 1213 / lock-wait 1205** non mappés → **500**. Le patch P5 (ordre canonique par id) écarte le 1213 mais pas le 1205, et laisse un mécanisme (`bumpVersion`, `isLotVersionRace`, catch `JpaSystemException` de l'isolation snapshot) qui n'existe que pour compenser le fait que l'intégrité des lots est vérifiée **trop tard** (à la validation). | **Limitation technique découverte à l'implémentation.** FR-047 autorise la validation d'un lot incomplet mais n'a jamais défini comment empêcher deux caisses de se disputer les membres d'un même lot **avant** la validation. |

### Analyse de la cause racine

FR-109 (SCP 2026-09-02b) a été implémentée comme une **détection de conflit à la validation** (même patron que le verrou optimiste `@Version` sur `Item` pour le double-encaissement d'un article). Or un lot n'est pas un article : c'est une **unité de vente composite** dont les membres sont scannés un par un, potentiellement sur plusieurs minutes, potentiellement sur des postes différents. Détecter le conflit seulement à la validation oblige à un force-increment de version en masse au moment critique de la transaction — d'où le verrou de ligne, d'où le deadlock.

Le bon niveau pour l'intégrité d'un lot est **l'entrée du membre dans le panier** : dès qu'un article d'un lot est ajouté à un panier, plus aucun autre panier ne doit pouvoir toucher ce lot. C'est une **réservation**, prise atomiquement par un `UPDATE … WHERE reserved_by_basket_id IS NULL`, sans verrou tenu entre deux requêtes et sans attente — un jeton de revendication optimiste, pas un verrou pessimiste.

Corollaire : si deux paniers ne peuvent plus détenir de membres du même lot, **la course multi-postes à la validation ne peut plus se produire** → `bumpVersion` et toute sa machinerie deviennent inutiles et peuvent être retirés.

### Preuves (code réel)

- `PosBasketService.validate()` (~l.160-217) : boucle `lotRepresentatives` (patch P5, `Comparator.comparing(r -> r.getLot().getId())`) → `lotRepository.bumpVersion(lot.getId(), lot.getVersion())` juste après `saleRepository.save(sale)` ; `catch (ObjectOptimisticLockingFailureException | OptimisticLockException | JpaSystemException e)` filtré par `isLotVersionRace(e)` (~l.326) → `LotAlreadySoldException`.
- `LotRepository.bumpVersion` : `@Modifying @Query("UPDATE Lot l SET l.version = l.version + 1 WHERE l.id = :id AND l.version = :expectedVersion")`.
- `GlobalExceptionHandler` : aucun `@ExceptionHandler(DataAccessException.class)` / `CannotAcquireLockException` → un deadlock non rattrapé sort en 500.
- `PosScanService.scan()` (~l.54-60) : garde `item.getLot() != null && itemRepository.existsByLotIdAndSoldTrue(lot.getId())` → `LotAlreadySoldException` — **cette garde reste pertinente** (cas « un frère a été vendu dans une vente déjà committée »).
- `PosBasketService.addItem()` (~l.80) : ajoute un seul `itemId`, aucune notion de lot au niveau du panier aujourd'hui.
- `Lot` (`domain/item/entity/Lot.java`) : `@Table(name = "lots")`, `@Version Long version` (utilisé par `LotService.update`), aucune colonne de réservation.
- `Basket` (`domain/pos/entity/Basket.java`) : `@Table(name = "baskets")`, contrainte `uk_baskets_edition_user` (un panier actif par bénévole et par édition) ; `basket_items` a `fk_basket_items_basket … deleteCascade="true"`.
- `EditionService` (~l.216) : seule annulation de panier existante — chemin de changement de phase, supprime les `BasketItem` + `Basket` en masse et diffuse `basket-cancelled` via `SseEmitterRegistry`.
- `SecurityConfig` (~l.80-83) + `LogoutSuccessHandler` : `.logout(...)` avec `.invalidateHttpSession(true)` ; aucun nettoyage du panier au logout aujourd'hui.
- `application.properties:18-24` : `spring-session-jdbc` (pom.xml) + `spring.session.store-type=jdbc` + `spring.session.timeout=PT1H` sont actifs → **délai d'inactivité effectif = 1 heure**. `server.servlet.session.timeout=2h` (l.23) est de la **config morte** (Spring Session prend le pas — commentaire l.22), et le commentaire l.22 dit encore « 2h » en contradiction avec l.20. L'ancienne rédaction de FR-066 (« les sessions n'expirent pas automatiquement ») est fausse → amendée (P-PRD3), ligne morte à supprimer dans la Story 4.8.
- Migrations Liquibase : dernière = `034-archived-item-lot.xml`. Convention FK : `fk_items_sale` (`022`) utilise `onDelete="SET NULL"` quand une cascade casserait ; `034` a délibérément choisi **pas de FK** pour une colonne pointant vers une table de données conservées (archive).
- `SaleConcurrencyIT` : méthode `two_concurrent_validations_of_different_members_of_the_same_lot_exactly_one_succeeds` prouve aujourd'hui le rejet **à la validation**.

---

## 2. Analyse d'impact

### Impact sur les epics

Les 6 epics restent `done`. **Aucun nouvel epic, aucun epic obsolète, aucun reséquencement.** Une **nouvelle story `4-8`** (multi-parties A/B) livre le changement ; trois stories `done` sont amendées de façon incrémentale (même patron que les SCP 2026-08-24 / 2026-09-02b).

- **Epic 4 — Point de vente** : Story 4.3 amendée (FR-109 réécrit : réservation au scan, plus verrou optimiste) ; Story 4.4 annotée (l'intégrité des lots ne passe plus par `@Version`). L'intro d'epic (« gestion des paniers avec prise en charge complète des lots », « en toute sécurité sur plusieurs postes ») reste exacte.
- **Epic 2 — Cycle de vie des éditions** : Story 2.8 annotée — la routine d'annulation de panier par changement de phase est **extraite / réutilisée** par la déconnexion, et libère désormais les réservations de lot. Intro inchangée.
- **Epic 1 — Fondation & Auth** : la déconnexion explicite (`/auth/logout`) gagne un `LogoutHandler` qui annule le panier actif. Aucune story `done` d'Epic 1 n'est rouverte (comportement additif, greffé sur `SecurityConfig` existant). Couverture de FR-110 mentionnée côté Epic 1 pour la partie logout.

### Impact sur les stories

| Story | Statut | Changement |
|---|---|---|
| **4.8** (nouvelle) | backlog | Story multi-parties A (réservation de lot au scan) + B (annulation du panier à la déconnexion). À rédiger via `bmad-create-story`. |
| 4.3 — Gestion des lots au POS | done | Bloc AC FR-109 réécrit (P-EP1). Livré par la Story 4.8, jamais rouverte. |
| 4.4 — Sécurité de la concurrence multi-postes | done | Note ajoutée (P-EP2) : lot ≠ `@Version`. Aucun code touché par 4.4. |
| 2.8 — Annulation du panier au changement de phase | done | Note ajoutée (P-EP3) : routine extraite + libération des réservations. La refacto d'extraction est portée par 4.8. |
| 5.8 — Post-vente & lots partiels | done | Origine du déclencheur. Le bloc `### Review Findings` de son fichier de story pointe déjà « Réservation de lot au scan » comme suite. Non rouverte. |

### Conflits d'artefacts

- **PRD** (`prds/prd-PluriBourse-2026-06-08/prd.md`) — **FR-109 réécrit** (réservation au scan, libération à la déconnexion) ; **FR-110 créé** (panier persisté côté serveur, durée de vie bornée par la session) ; **FR-066 amendé** (les sessions expirent après **1 h** d'inactivité — l'ancienne rédaction « les sessions n'expirent pas automatiquement » contredisait l'implémentation ; ligne morte `server.servlet.session.timeout=2h` à supprimer dans la Story 4.8). FR-047 : aucun changement de texte (« lot réputé vendu comme un tout » reste vrai). **MVP non affecté**, aucune réduction de périmètre.
- **Epics** (`epics.md`) — lignes miroir FR-109 + ajout FR-110 (section « F4 » et carte de couverture) ; **lignes miroir FR-066 amendées** (2 emplacements) ; « FR couvertes » Epic 4 (+FR-109, +FR-110) ; AC Story 4.3 ; notes Stories 4.4 et 2.8 ; **UX-DR21** — la déconnexion ne mobilise que le feedback de déconnexion générique, **pas** de message distinct « panier annulé », pas de nouvelle chaîne i18n (décision Manerial 2026-09-03).
- **Architecture** (`architecture.md`) — § Concurrence — POS : ligne « Stratégie de verrouillage » précisée (le `@Version` reste pour l'article, pas pour le lot), ligne **« Intégrité des lots »** entièrement remplacée (réservation légère, pas de verrou pessimiste), ligne **« Exigence de test »** réécrite (le scénario multi-postes Testcontainers cible désormais le rejet à l'`addItem`). § Notification de Changement de Phase : ligne « Déclencheur (bis) » — `basket-cancelled` est aussi émis à la déconnexion (mécanisme SSE, sans exigence d'un toast dédié).
- **UX** (`ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md`) — **volontairement non amendé** (dérive documentaire connue et assumée, même convention que 2.7 / 2.9 / 3.14 / 4.7 / 5.8). Le nouvel état d'erreur inline `lot-reserved` sous le scanner est un **variant du pattern existant** « Conflit de scan concurrent (article déjà vendu depuis un autre poste) » (EXPERIENCE.md l.196, 228-229) et de UX-DR7 (notification inline). Signalé pour un futur passage de mise à jour UX groupé.
- **Migration BDD** — **nouvelle** `035-lot-reservation.xml` : 2 colonnes nullables sur `lots` (`reserved_by_basket_id BIGINT NULL` + FK `fk_lots_reserved_basket → baskets(id) ON DELETE SET NULL`, `reserved_at TIMESTAMP NULL`). Pas de table dédiée `lot_reservation`.
- **i18n** — `pluribourse-frontend/public/i18n/{fr,en}.json` : nouvelle clé `volunteer.pos.error.lotReserved` ; `volunteer.pos.error.lotAlreadySold` **conservée** (cas « frère vendu dans une vente committée »). Backend `messages{,_fr,_en}.properties` : aucune clé nouvelle attendue (l'erreur 409 ne porte qu'un slug).
- **Tests** — `SaleConcurrencyIT` : la méthode lot est **réécrite** pour prouver le rejet à l'`addItem` (409 `lot-reserved`), pas à la validation ; `PosScanIT` / `PosBasketIT` ajustés (réservation, libération) ; nouveau IT de déconnexion (panier + `BasketItem` supprimés, `reserved_by_basket_id` remis à `NULL`, `basket-cancelled` émis) ; spec front `pos-page.component.spec` (branche `lot-reserved`). Aucun test de toast de déconnexion (pas de message dédié).
- **Sprint status** (`implementation-artifacts/sprint-status.yaml`) — entrée `4-8-…: backlog` sous Epic 4 + bloc de commentaire daté 2026-09-03.
- **Aucun impact** : CI/CD, IaC, déploiement, monitoring, pipeline d'impression, contrats d'API publics (hors nouveau slug d'erreur 409 `lot-reserved`).

### Impact technique (synthèse)

| Zone | Fichiers principaux |
|---|---|
| Modèle de données | `domain/item/entity/Lot.java` (+ `reservedByBasketId`, `reservedAt`) ; `db/changelog/035-lot-reservation.xml` (**nouveau**) ; `db.changelog-master.xml` (include) |
| Réservation (Partie A) | `domain/item/repository/LotRepository.java` (+ `reserveLot`/`releaseLot` `@Modifying` ; **suppression** de `bumpVersion`) ; `domain/pos/service/PosBasketService.java` (`addItem` → réserve ; `removeItem` → libère si dernier membre du lot ; `removeLot` → libère ; `validate` → libère puis **suppression** de la boucle `lotRepresentatives`/P5, de `isLotVersionRace`, du catch `JpaSystemException`) ; `domain/pos/exception/` (nouveau `LotReservedException` → 409 `lot-reserved`) ; conservation de `ItemRepository.existsByLotIdAndSoldTrue` + ses 2 appels |
| Annulation au logout (Partie B) | `shared/security/SecurityConfig.java` (`.logout().addLogoutHandler(...)`) ; nouveau `LogoutHandler` (avant invalidation de session, principal encore disponible) ; `domain/edition/service/EditionService.java` ~l.216 (extraire une routine `cancelBasket(basket)` réutilisable — supprime `BasketItem` + `Basket`, `NULL` sur `lots.reserved_by_basket_id`, émet `basket-cancelled`) ; `SessionInvalidationService` (Story 1.12, `FindByIndexNameSessionRepository.findByPrincipalName`) comme hook réutilisable pour la résolution de session par principal ; `application.properties` (retrait ligne morte `server.servlet.session.timeout=2h` + commentaire l.22) |
| Front | `features/pos/pos-page.component.ts` (`handleScanError` / `handleValidationError` → branche `/lot-reserved` → `volunteer.pos.error.lotReserved`) ; `{fr,en}.json` (clé `lotReserved` uniquement). Pas de nouvelle chaîne ni de toast pour la déconnexion (feedback générique existant) |
| Tests | `SaleConcurrencyIT` (méthode lot réécrite : rejet `addItem`), `PosScanIT`, `PosBasketIT`, `pos-page.component.spec`, spec logout |

---

## 3. Approche recommandée

**Ajustement direct** (Option 1 du checklist correct-course) via **une nouvelle story `4-8` multi-parties (A + B)**, plus amendements incrémentaux des Stories 4.3 / 4.4 / 2.8 — même patron que les SCP 2026-08-24 et 2026-09-02b.

- **Pas de rollback de la Story 5.8** : ses parties B / D / E (case impression au solde, tableaux « détail des lots », bilan restructuré) sont indépendantes de D1 et restent en place. Seule la partie C (FR-109) est ré-architecturée — et par remplacement en avant, pas par revert : le slug d'erreur, la garde au scan `existsByLotIdAndSoldTrue`, le message inline et le test de concurrence dédié sont conservés ou transposés.
- **Pas de revue MVP** : le MVP livré (6 epics) est intact, aucune réduction de périmètre. FR-109 gagne en robustesse, ne perd rien.
- **Une story et non deux** : les parties A et B se recoupent fortement — la routine `cancelBasket` extraite en B est *le* point de libération des réservations créées en A pour le cas « le caissier s'en va » ; les livrer séparément laisserait une fenêtre de réservation orpheline entre les deux. Un document unique évite qu'un des points de conception ne passe à la trappe.

**Effort : Moyen. Risque : Faible-Moyen.**
- Point sensible n°1 : la migration `035` ajoute une FK `lots → baskets`. `baskets` est une table à forte rotation (supprimée à chaque validation) ; le `ON DELETE SET NULL` est **voulu** — il fait de la suppression de panier un déclencheur de libération automatique et infaillible (défense en profondeur derrière la libération applicative).
- Point sensible n°2 : le `LogoutHandler` doit tourner **avant** `invalidateHttpSession` (principal encore présent) et rester best-effort — un échec de nettoyage ne doit pas bloquer la déconnexion.
- Point sensible n°3 : `removeItem` ne libère la réservation que lorsque le **dernier** membre du lot quitte le panier — condition à tester explicitement.
- La suppression de `bumpVersion` / `isLotVersionRace` / du catch `JpaSystemException` **réduit** la surface de risque (retrait du chemin qui produisait le 500).

**Hors périmètre / suite différée :** l'**expiration** de session par inactivité (FR-066 — 1 h via `spring.session.timeout=PT1H`) ne libère pas les réservations ni n'annule le panier. Traitement **différé, à réévaluer après livraison de la Story 4.8** (une story dédiée, branchée sur Spring Session `SessionDeletedEvent` / `SessionExpiredEvent` → `cancelBasket`, si jugée nécessaire à ce moment-là). Le nettoyage de la ligne morte `server.servlet.session.timeout=2h`, lui, **entre** dans le périmètre de la Story 4.8 (Partie B) — voir P-PRD3.

---

## 4. Propositions de changement détaillées

> Décisions pré-arrêtées par Manerial (run de fond) — voir « Points de conception figés » au §5.

### Groupe A — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**P-PRD1 — FR-109 (réécrire)** — section « Lots en Caisse »

- OLD :
  > Un lot ne peut être vendu qu'une seule fois. Dès qu'un de ses articles est marqué vendu, scanner un autre article du même lot au POS est rejeté avec une erreur 409 explicite (« article appartenant à un lot déjà vendu »), **au scan et à la validation du panier** (course multi-postes). Le prix global du lot est encaissé une seule fois (FR-048). Les articles non vendus d'un lot vendu reviennent au vendeur et figurent comme invendus au bilan de vente (FR-050). *(2026-09-02, voir `sprint-change-proposal-2026-09-02b.md`.)*
- NEW :
  > Un lot ne peut être vendu qu'une seule fois. **Dès qu'un de ses articles est ajouté à un panier de caisse (au scan ou à l'ajout au panier), le lot entier est réservé pour ce panier.** Ajouter un article du même lot depuis un **autre** panier est rejeté avec une erreur 409 explicite (« lot en cours d'encaissement sur une autre caisse »). La réservation est levée quand le dernier article du lot quitte le panier, quand le lot est retiré du panier (FR-081), quand le panier est validé, quand le panier est annulé par un changement de phase (FR-090), ou quand le caissier se déconnecte explicitement (FR-110). En complément, un article dont un frère de lot a **déjà été vendu dans une vente committée** reste rejeté (409, au scan et à la validation du panier). Le prix global du lot est encaissé une seule fois (FR-048). Les articles non vendus d'un lot vendu reviennent au vendeur et figurent comme invendus au bilan de vente (FR-050). *(Réservation prise au scan : 2026-09-03, voir `sprint-change-proposal-2026-09-03.md` — remplace le mécanisme de verrou optimiste `Lot.@Version` de la SCP 2026-09-02b.)*

**P-PRD2 — FR-110 (nouveau)** — section « Point de Vente », à la suite de FR-093

- NEW :
  > | FR-110 | Le panier de caisse est **persisté côté serveur** (un panier actif au plus par bénévole et par édition — contrainte d'unicité). Sa durée de vie est **bornée par la session du caissier** : une **déconnexion explicite** (`/auth/logout`) annule le panier actif du bénévole pour l'édition active (articles et panier supprimés) et **libère les réservations de lot** qu'il détenait (FR-109). Les autres onglets du même utilisateur sont notifiés via l'événement SSE `basket-cancelled` (même mécanisme que FR-090). L'annulation du panier sur **expiration** de session par inactivité (FR-066) n'est pas traitée par cette itération — suite possible, à réévaluer après livraison. *(2026-09-03, voir `sprint-change-proposal-2026-09-03.md`.)* |

**P-PRD3 — FR-066 (amender)** — section « Authentification & Comptes »

- OLD :
  > | FR-066 | Les sessions n'expirent pas automatiquement. [NON-GOAL pour v1 : la gestion des sessions n'est pas requise — la plateforme opère sur un réseau local fermé et une reconnexion forcée le jour J serait trop contraignante pour les bénévoles.] |
- NEW :
  > | FR-066 | Les sessions sont persistées côté serveur (Spring Session JDBC, table `spring_session`) et **survivent à un redémarrage du conteneur serveur** — un bénévole n'est pas déconnecté par un simple redémarrage du service. En revanche, **après 1 heure d'inactivité, la session expire et l'utilisateur est déconnecté automatiquement** (`spring.session.timeout=PT1H`) : c'est un choix de sécurité délibéré pour les postes bénévoles partagés en libre accès dans la salle. *(Amendé 2026-09-03, voir `sprint-change-proposal-2026-09-03.md` — l'ancienne rédaction « les sessions n'expirent pas automatiquement » contredisait l'implémentation livrée.)* |
- **Justification** : l'implémentation livrée expire bien les sessions par inactivité. Vérification de code (2026-09-03) : `spring-session-jdbc` est au `pom.xml`, `spring.session.store-type=jdbc` et `spring.session.timeout=PT1H` sont actifs → **1 heure est la valeur effective**. Le NON-GOAL initial (« réseau local fermé, pas besoin de gestion de session ») ne tient plus dès lors que les postes bénévoles sont partagés et en libre accès.
- **Nettoyage de configuration (dans le périmètre de la Story 4.8, Partie B)** : `application.properties:23` `server.servlet.session.timeout=2h` est de la **config morte** — Spring Session (`spring.session.timeout=PT1H`) prend le pas, comme le note déjà le commentaire l.22. Action : **supprimer la ligne `server.servlet.session.timeout=2h`** et **corriger le commentaire l.22** (qui parle encore de « 2h ») pour que `spring.session.timeout=PT1H` soit l'unique source de vérité du délai d'inactivité. La Partie B de la Story 4.8 touche déjà `SecurityConfig` / le cycle session-logout : ce nettoyage y est rattaché, pas dans une story ni un commit séparés.

**P-PRD4 — FR-047 / FR-050 / NFR-006** — pas d'édition.
- FR-047 : « dès qu'un article du lot est vendu, le lot est réputé vendu comme un tout » reste exact (sémantique post-validation). La réservation est un concept pré-validation, complémentaire.
- FR-050 : le bilan (tableaux « détail des lots », comptage) est inchangé.
- NFR-006 : le panier persisté côté serveur **est** le mécanisme qui satisfait déjà « aucune perte de données lors d'une fermeture inattendue » ; une fermeture *inattendue* (sans logout) conserve panier **et** réservation côté serveur jusqu'à la reconnexion, le logout explicite ou l'expiration de session. Aucune régression, pas de clause à ajouter.

### Groupe B — Epics : critères d'acceptation (`epics.md`)

**P-EP1 — Story 4.3 (l.1576-1579, remplacer le bloc `*(SCP 2026-09-02b)*`)**

- OLD :
  ```
  **Étant donné** qu'au moins un article d'un lot a été vendu
  **Quand** un caissier scanne un autre article du même lot
  **Alors** le scan est rejeté avec une erreur explicite (« cet article appartient à un lot déjà vendu ») — au scan **et** à la validation du panier (course multi-postes)
  **Et** les articles non vendus du lot reviennent au vendeur et apparaissent comme invendus au bilan (FR-109) *(SCP 2026-09-02b)*
  ```
- NEW :
  ```
  **Étant donné** qu'un caissier a ajouté un article d'un lot à son panier
  **Quand** un second caissier scanne (ou ajoute au panier) un autre article du même lot
  **Alors** l'ajout est rejeté avec un 409 `lot-reserved` (« lot en cours d'encaissement sur une autre caisse ») — le lot est réservé pour le premier panier tant qu'il y détient au moins un article
  **Et** la réservation est levée au retrait du dernier membre du lot, au retrait du lot (FR-081), à la validation, à l'annulation du panier (changement de phase) ou à la déconnexion explicite du premier caissier
  **Et** si un frère du lot a déjà été vendu dans une vente committée, le scan/ajout est rejeté (409) au scan comme à la validation
  **Et** les articles non vendus d'un lot vendu reviennent au vendeur et apparaissent comme invendus au bilan (FR-109) *(SCP 2026-09-03 — réservation au scan, remplace le verrou optimiste de la SCP 2026-09-02b)*
  ```

**P-EP2 — Story 4.4 (après la note « Notes de développement » l.1611-1612, ajouter)**

- NEW :
  > **Note (SCP 2026-09-03) :** l'intégrité des lots (FR-109) ne repose plus sur le verrou optimiste décrit dans cette story mais sur une **réservation de lot** prise à l'ajout au panier (Story 4.8). Le verrou `@Version` sur `Item` reste la garde du double-encaissement d'un **article individuel**. Le test Testcontainers de cette story (deux `TransactionTemplate` concurrents sur des paniers qui se chevauchent → un seul 409) reste valable pour les articles ; le scénario **lot** correspondant cible désormais le rejet à l'`addItem`.

**P-EP3 — Story 2.8 (après le bloc AC « le payload contient l'`editionId` et la nouvelle phase » l.1024, ajouter)**

- NEW :
  > **Note (SCP 2026-09-03) :** la routine d'annulation d'un panier (suppression des `BasketItem` + du `Basket`, émission de `basket-cancelled`) est **extraite** en un point réutilisable (Story 4.8) et invoquée aussi par la déconnexion explicite (FR-110). Toute annulation de panier — changement de phase inclus — **libère désormais les réservations de lot** (`lots.reserved_by_basket_id`) détenues par ce panier (FR-109).

### Groupe C — Epics : lignes miroir des FR, carte de couverture, UX-DR (`epics.md`)

**P-EP4 — Ligne miroir FR-109 (l.92, section « F4 bis — Lots en caisse »)**

- OLD :
  > - FR-109 : Un lot ne se vend qu'une fois — scanner un article d'un lot déjà vendu est rejeté (409, au scan et à la validation) ; les articles restants reviennent au vendeur. *(SCP 2026-09-02b)*
- NEW :
  > - FR-109 : Un lot ne se vend qu'une fois — dès qu'un de ses articles entre dans un panier, le lot est **réservé pour ce panier** (`UPDATE lots … WHERE reserved_by_basket_id IS NULL`) ; ajouter un membre du même lot depuis un autre panier est rejeté (409 `lot-reserved`). Réservation levée au retrait du dernier membre / retrait du lot / validation / annulation de panier / déconnexion. Un frère déjà vendu (vente committée) reste rejeté. Les articles restants reviennent au vendeur. *(SCP 2026-09-03 — remplace le verrou optimiste `Lot.@Version` de la SCP 2026-09-02b)*

**P-EP5 — Ligne miroir FR-110 (nouvelle, section « F4 — Point de vente », après FR-090 l.83)**

- NEW :
  > - FR-110 : Le panier de caisse est persisté côté serveur (un par bénévole et par édition) ; sa durée de vie est bornée par la session. Une **déconnexion explicite** annule le panier actif et libère les réservations de lot ; les autres onglets de l'utilisateur reçoivent l'évènement SSE `basket-cancelled`. L'annulation du panier sur expiration de session par inactivité (FR-066) est laissée en suite possible, à réévaluer après livraison. *(SCP 2026-09-03)*

**P-EP5bis — Lignes miroir FR-066 (`epics.md` l.123 section « F7 » et l.281 carte de couverture)**

- l.123 OLD : `- FR-066 : Les sessions n'expirent pas automatiquement.`
  l.123 NEW : `- FR-066 : Les sessions sont persistées (Spring Session JDBC) et survivent à un redémarrage du conteneur serveur ; une inactivité prolongée entraîne en revanche une déconnexion automatique sur les postes bénévoles partagés (choix de sécurité délibéré, `spring.session.timeout`). *(Amendé SCP 2026-09-03.)*`
- l.281 OLD : `- FR-066 : Epic 1 — Les sessions n'expirent pas automatiquement`
  l.281 NEW : `- FR-066 : Epic 1 — Sessions persistées (Spring Session JDBC), survivent au redémarrage du conteneur ; déconnexion automatique après inactivité prolongée sur les postes bénévoles partagés (`spring.session.timeout`) *(amendé SCP 2026-09-03)*`
- Story 1.2 (`epics.md` l.417) : l'AC « le conteneur serveur est redémarré → la session survit (FR-066) » reste **valide et inchangée** (persistance JDBC).

**P-EP6 — Carte de couverture FR-109 (l.260)**

- OLD :
  > - FR-109 : Epic 4 — Un lot ne se vend qu'une fois ; scan d'un article d'un lot déjà vendu rejeté (409, au scan et à la validation) ; articles restants rendus au vendeur *(SCP 2026-09-02b)*
- NEW :
  > - FR-109 : Epic 4 — Un lot ne se vend qu'une fois ; **réservation du lot prise à l'ajout au panier** (409 `lot-reserved` si un autre panier le détient), levée au retrait/validation/annulation/déconnexion ; frère déjà vendu (vente committée) toujours rejeté ; articles restants rendus au vendeur *(SCP 2026-09-03 — remplace le verrou `@Version` de la SCP 2026-09-02b)*

**P-EP7 — Carte de couverture FR-110 (nouvelle, après FR-090 l.305)**

- NEW :
  > - FR-110 : Epic 4 — Panier de caisse persisté côté serveur, durée de vie bornée par la session ; déconnexion explicite → annulation du panier actif + libération des réservations de lot + SSE `basket-cancelled` aux autres onglets *(SCP 2026-09-03)*

**P-EP8 — « FR couvertes » Epic 4 (l.338)**

- OLD :
  > **FR couvertes :** FR-033–042, FR-046–048, FR-081, FR-090 (côté client), FR-093
- NEW :
  > **FR couvertes :** FR-033–042, FR-046–048, FR-081, FR-090 (côté client), FR-093, FR-109, FR-110

**P-EP9 — UX-DR21 (l.205)**

- OLD :
  > - UX-DR21 : Implémenter la gestion des transitions de phase dans l'interface POS bénévole : événement SSE `basket-cancelled` → toast persistant « La phase a changé. Votre panier a été annulé. » → panier vidé → scanner désactivé jusqu'au rechargement de la page.
- NEW :
  > - UX-DR21 : Implémenter la gestion des transitions de phase dans l'interface POS bénévole : événement SSE `basket-cancelled` → toast persistant « La phase a changé. Votre panier a été annulé. » → panier vidé → scanner désactivé jusqu'au rechargement de la page. La déconnexion explicite d'un caissier (FR-110) s'appuie sur le feedback de déconnexion générique existant (redirection vers l'écran de connexion) : la disparition silencieuse du panier est acceptable — aucun toast ni chaîne i18n dédiés à l'annulation du panier ne sont ajoutés pour ce cas. *(Note déconnexion : SCP 2026-09-03.)*
- **Décision Manerial (2026-09-03) :** pas de message utilisateur distinct pour la déconnexion. UX-DR21 reste centré sur le comportement (transition de phase) ; le cas logout ne mobilise que le retour de déconnexion générique déjà en place, sans nouvelle chaîne i18n.

### Groupe D — Architecture (`architecture.md`, § Concurrence — POS)

**P-ARCH1 — Ligne « Stratégie de verrouillage » (l.234)**

- OLD :
  > | Stratégie de verrouillage | **Verrouillage optimiste** (`@Version` sur l'entité `Item`) | Faible contention attendue sur 3 postes ; pas de verrous maintenus, pas d'interblocages |
- NEW :
  > | Stratégie de verrouillage | **Verrouillage optimiste** (`@Version` sur l'entité `Item`) pour le double-encaissement d'un article individuel ; **réservation légère** pour l'intégrité d'un lot (voir ligne dédiée) | Faible contention attendue sur 3 postes ; pas de verrous pessimistes, pas de verrous maintenus entre deux requêtes, pas d'interblocages |

**P-ARCH2 — Ligne « Intégrité des lots » (l.237, remplacer entièrement)**

- OLD :
  > | Intégrité des lots | Un lot ne se vend qu'une fois : dès qu'un article du lot est vendu, scanner un autre article du même lot est rejeté (**au scan et à la validation**, 409, même patron que le verrouillage optimiste `@Version`). Les articles non vendus d'un lot vendu reviennent au vendeur (FR-109) | Empêche le double-encaissement du prix global d'un lot ; l'« intégrité des lots » de F4 (déjà mentionnée) inclut désormais cette règle *(SCP 2026-09-02b)* |
- NEW :
  > | Intégrité des lots | **Réservation légère de lot** prise à l'ajout d'un membre au panier : `UPDATE lots SET reserved_by_basket_id = :basketId, reserved_at = :now WHERE id = :lotId AND (reserved_by_basket_id IS NULL OR reserved_by_basket_id = :basketId)`. 0 ligne modifiée → un autre panier détient le lot → **409 `lot-reserved`** (« lot en cours d'encaissement sur une autre caisse »). Aucun verrou de ligne tenu entre deux requêtes, aucune attente : jeton de revendication optimiste, **pas** de verrou pessimiste (ni `SELECT … FOR UPDATE`, ni verrou maintenu pendant le temps de réflexion du caissier). Libération : retrait du dernier membre du lot, retrait du lot (FR-081), validation, annulation de panier (changement de phase, FR-090), déconnexion explicite (FR-110). En complément, un frère de lot déjà `sold` dans une vente committée reste rejeté (`ItemRepository.existsByLotIdAndSoldTrue`, au scan et à la validation). Deux paniers ne pouvant plus détenir de membres du même lot, la **course multi-postes à la validation ne peut plus se produire** — le force-increment `Lot.@Version` (`LotRepository.bumpVersion`) de la SCP 2026-09-02b est **retiré**. | Empêche le double-encaissement du prix global d'un lot **sans** introduire d'interblocage ni d'attente de verrou (le `bumpVersion` en masse pouvait produire un deadlock MariaDB 1213 / lock-wait 1205 → 500). *(SCP 2026-09-03 — remplace la ligne de la SCP 2026-09-02b)* |

**P-ARCH3 — Ligne « Exigence de test » (l.239, réécrire)**

- OLD :
  > | Exigence de test | Test d'intégration avec deux `TransactionTemplate`s concurrents + **Testcontainers (MariaDB)** en CI ; **+ un scénario couvrant le rejet du scan d'un article de lot déjà vendu, au scan et en course multi-postes à la validation** | Le comportement de verrouillage de H2 diffère de MariaDB ; une vraie BDD est requise pour ce test |
- NEW :
  > | Exigence de test | Test d'intégration avec deux `TransactionTemplate`s concurrents + **Testcontainers (MariaDB)** en CI ; **+ un scénario multi-postes où deux paniers tentent de réserver le même lot : exactement un obtient la réservation, l'autre est rejeté en 409 `lot-reserved` dès l'`addItem` (plus au moment de la validation) ; + un scénario de rejet du scan/ajout d'un article dont un frère de lot est déjà vendu dans une vente committée** | Le comportement de verrouillage de H2 diffère de MariaDB ; une vraie BDD est requise pour ce test |

**P-ARCH4 — § Notification de Changement de Phase (FR-090), après la ligne « Déclencheur » (l.250)**

- NEW (ligne à ajouter au tableau) :
  > | Déclencheur (bis) | La **déconnexion explicite** d'un caissier annule son panier actif et émet `basket-cancelled` vers ses autres onglets (FR-110) | Réutilise le même événement et le même `SseEmitterRegistry` que la transition de phase ; la routine d'annulation de panier est partagée |

### Groupe E — UX (`ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md`)

**P-UX1 — Pas d'édition.** EXPERIENCE.md n'est volontairement pas amendé (dérive documentaire connue, même convention que 5.8). Deux points à intégrer lors d'un futur passage de mise à jour UX groupé :
- Table des états d'erreur, ligne « Conflit de scan concurrent » : ajouter un variant `lot-reserved` — notification inline rouge sous le scanner (« Ce lot est en cours d'encaissement sur une autre caisse. »), l'article n'est pas ajouté, le scanner reste actif. Même traitement visuel que « article déjà vendu ».
- Flow POS : préciser que le retrait du dernier article d'un lot (ou « Retirer le lot entier ») **libère** le lot pour les autres caisses.

### Groupe F — Migration de base de données (`pluribourse-backend/src/main/resources/db/changelog/`)

**P-DB1 — Nouvelle migration `035-lot-reservation.xml`** (à créer par la Story 4.8, mentionnée ici pour cadrage)

- 2 colonnes nullables sur `lots` :
  - `reserved_by_basket_id BIGINT NULL` avec **FK `fk_lots_reserved_basket` → `baskets(id)` `onDelete="SET NULL"`**
  - `reserved_at` `TIMESTAMP` / `DATETIME` `NULL` (diagnostic / observabilité uniquement — aucune sémantique de TTL)
- **Décision (a) — FK avec `ON DELETE SET NULL`, pas « sans FK »** : `baskets` est une table opérationnelle vivante à forte rotation (supprimée à chaque validation, chaque retrait, chaque changement de phase). Le `SET NULL` fait de **toute suppression de panier un déclencheur de libération automatique**, atomique et impossible à oublier — c'est exactement la sémantique voulue pour les chemins « changement de phase » et « déconnexion », et une défense en profondeur derrière la libération applicative. Le contre-argument de `034` (« ne pas cascader vers une table d'archive/historique ») ne s'applique pas : `lots` n'est pas une archive, et voir la réservation s'effacer avec le panier est souhaitable, pas nuisible. Cohérent avec `fk_items_sale … onDelete="SET NULL"` (`022`).
- **Décision (b) — pas de TTL.** Aucune tâche planifiée, aucune purge par âge. `reserved_at` n'est pas lu par la logique métier.
- **Décision (c) — retrait de l'ancien mécanisme** : supprimer `LotRepository.bumpVersion`, `PosBasketService.isLotVersionRace`, la branche `catch (JpaSystemException)` de l'isolation snapshot, et le patch de revue **P5** (tri `Comparator.comparing(...getLot().getId())` de `lotRepresentatives` dans `validate()`). **Conserver** `ItemRepository.existsByLotIdAndSoldTrue` et ses 2 appels (garde au scan dans `PosScanService.scan` ; boucle de pré-check dans `validate()`).

---

## 5. Plan de transmission (handoff)

**Ampleur : Modérée.** Nouvelle story `4-8` (backend concurrence + sécurité + migration Liquibase + retouches front) + 3 stories `done` annotées + 1 décision d'architecture ré-ouverte et retranchée. Pas de replan, MVP intact → **pas d'escalade PM / Architecte**.

| Élément | Ampleur | Transmis à |
|---|---|---|
| Édits d'artefacts P-PRD1..4, P-EP1..9 (+ P-EP5bis), P-ARCH1..4, P-UX1 (note), sprint-status | Mineure | **Appliqués comme modifications du working tree (2026-09-03)** — non commités. À relire par Manerial. |
| Story 4.8 — Parties A + B | Modérée | **PO / Dev — nouvelle story via `bmad-create-story`**, analyse de code réel obligatoire (migration FK `lots→baskets`, `LogoutHandler` avant invalidation de session, libération « dernier membre du lot », réécriture `SaleConcurrencyIT`, suppression de la ligne morte `server.servlet.session.timeout=2h` + commentaire l.22) |
| Story de suivi « expiration de session → annulation du panier / libération des réservations » | Mineure, **différée** | À réévaluer **après** livraison de la Story 4.8 (pas un go immédiat). Non bloquante. |
| Nettoyage config `server.servlet.session.timeout=2h` (ligne morte) + commentaire l.22 | Mineure | **Dans le périmètre de la Story 4.8, Partie B** — `spring.session.timeout=PT1H` reste l'unique source de vérité (1 h). |
| Amendement FR-066 (P-PRD3) | Mineure | **Appliqué (working tree)** — `prd.md` + lignes miroir `epics.md`. |

### Parties de la Story 4.8

| Partie | Contenu | Déclencheur |
|---|---|---|
| **A — Réservation de lot au scan / à l'ajout au panier** | Migration `035` (2 colonnes sur `lots` + FK `ON DELETE SET NULL`). `Lot` : `reservedByBasketId`, `reservedAt`. `LotRepository` : `reserveLot(lotId, basketId, now)` + `releaseLot(lotId)` `@Modifying` ; **suppression** de `bumpVersion`. `PosBasketService` : `addItem` réserve atomiquement (0 ligne → `LotReservedException` 409 `lot-reserved`, l'article n'entre pas dans le panier) ; `removeItem` libère **quand le dernier membre du lot quitte le panier** ; `removeLot` libère ; `validate()` libère à la fin **et** perd la boucle `lotRepresentatives`/P5, `isLotVersionRace`, le catch `JpaSystemException`. **Conserver** `existsByLotIdAndSoldTrue` + ses 2 appels. Nouveau `LotReservedException`. Front : branche `/lot-reserved` dans `handleScanError` **et** `handleValidationError` → `volunteer.pos.error.lotReserved` (variant error) ; clé i18n `fr`/`en`. `SaleConcurrencyIT` : méthode lot **réécrite** — deux `addItem` concurrents sur deux membres du même lot → exactement un réussit, l'autre 409 `lot-reserved` ; plus aucune assertion de course à la validation pour les lots. `PosScanIT` / `PosBasketIT` : réservation prise, réservation levée, frère vendu committé toujours rejeté. | D1 |
| **B — Annulation du panier à la déconnexion explicite** | Extraire de `EditionService` (~l.216) une routine `cancelBasket(basket)` réutilisable : supprime les `BasketItem` + le `Basket`, `NULL` sur `lots.reserved_by_basket_id` des lots pointant vers lui (la FK `SET NULL` le fait aussi côté BDD — ceinture + bretelles), émet `basket-cancelled` via `SseEmitterRegistry`. `SecurityConfig` : `.logout().addLogoutHandler(new BasketCancellingLogoutHandler(...))` — s'exécute **avant** `.invalidateHttpSession(true)`, principal encore disponible ; best-effort (un échec ne bloque pas la déconnexion) ; ne cible que le panier actif de l'utilisateur pour l'édition active. **Hook réutilisable** : `SessionInvalidationService` (Story 1.12) enveloppe déjà `FindByIndexNameSessionRepository.findByPrincipalName(...)` pour retrouver / invalider les sessions d'un principal — s'en inspirer / le réutiliser plutôt que de recâbler l'accès au `SessionRepository`. **Nettoyage config** : supprimer la ligne morte `application.properties:23` `server.servlet.session.timeout=2h` et corriger le commentaire l.22 (garde `spring.session.timeout=PT1H` comme unique source de vérité). **Front : aucun changement** — pas de message ni de toast dédiés à la déconnexion (le retour de déconnexion générique suffit, décision Manerial 2026-09-03) ; sur les autres onglets, le handler `basket-cancelled` existant vide le panier. Test : IT de déconnexion — panier + `BasketItem` supprimés, `reserved_by_basket_id` remis à `NULL`, `basket-cancelled` émis ; la déconnexion réussit même si le panier est déjà vide. | Décision Manerial « il s'est déconnecté, on ne garde pas son panier en mémoire » + fermeture de la fenêtre de réservation orpheline de la Partie A |

### Points de conception figés (décidés par Manerial, 2026-09-03)

- **Stockage** : 2 colonnes nullables sur `lots` (`reserved_by_basket_id` + `reserved_at`), **pas** de table `lot_reservation`.
- **FK** : `reserved_by_basket_id` → `baskets(id)` **avec `ON DELETE SET NULL`** (justification P-DB1 / décision a).
- **Pas de TTL** — `reserved_at` est purement diagnostic.
- **Points de libération** : `removeItem` (dernier membre du lot uniquement), `removeLot`, `validate()` succès, déconnexion explicite, changement de phase (routine d'annulation en masse existante). **Pas** l'expiration de session par inactivité (suite différée, à réévaluer après 4.8).
- **Retrait de l'ancien mécanisme** : `bumpVersion`, `isLotVersionRace`, catch `JpaSystemException` snapshot, patch P5. **Conservation** : `existsByLotIdAndSoldTrue` + ses 2 appels (cas « frère vendu dans une vente committée »).
- **Slug d'erreur** : `lot-reserved` (nouveau, 409) pour le conflit de réservation ; `lot-already-sold` (existant, 409) conservé pour le frère déjà vendu.
- **`SaleConcurrencyIT`** : la méthode lot prouve désormais le rejet à l'`addItem`, pas à la validation.
- **Justification du retrait de `bumpVersion`** : une réservation rend impossible que deux paniers détiennent des membres du même lot ; la course multi-postes à `validate()` ne peut donc plus se produire.

### Critères de succès

- Deux caisses ne peuvent jamais détenir simultanément des membres du même lot : la seconde reçoit un **409 `lot-reserved` dès l'`addItem`**, jamais un 500, jamais deux ventes portant le même lot.
- Aucun deadlock / lock-wait possible sur le chemin de validation d'un lot (le `bumpVersion` en masse a disparu).
- Le retrait du dernier membre d'un lot, le retrait du lot, la validation, l'annulation de panier et la déconnexion explicite **libèrent** la réservation — vérifiable par un second poste qui peut alors scanner le lot.
- Une déconnexion explicite supprime le panier actif du caissier et remet à `NULL` toutes les réservations qu'il détenait ; ses autres onglets reçoivent `basket-cancelled`.
- Le cas « un frère du lot a été vendu dans une vente committée » reste rejeté (409) au scan et à la validation.
- `./mvnw clean package` vert (dont `SaleConcurrencyIT` réécrit, exécuté là où Docker est présent) ; `npm test` + `npm run build` verts, sans warning.
- Parité i18n `fr.json` ⇔ `en.json` ; `volunteer.pos.error.lotReserved` présent dans les deux, `volunteer.pos.error.lotAlreadySold` conservé.

### Questions ouvertes (pour Manerial)

Les 4 questions ont été tranchées par Manerial le 2026-09-03 (voir aussi le journal `sprint-status.yaml`).

1. **Home de la story** — ✅ **Résolue.** Une seule story `4-8` (Epic 4), deux parties A/B. Inchangé.
2. **Story de suivi « expiration de session »** — 🔄 **Différée.** Décision reportée : à réévaluer **après** livraison de la Story 4.8 (pas un go immédiat). Consignée au §5 « Hors périmètre / suite différée ». Aucun fichier de backlog V2 n'existe dans le dépôt (`_bmad-output/**`) ; l'item vit donc dans cette SCP — à recopier dans le backlog V2 de Manerial (mémoire projet) s'il le souhaite.
3. **FR-066** — ✅ **Résolue.** Vérification de code (2026-09-03) : Spring Session JDBC actif + `spring.session.timeout=PT1H` → **1 heure est la valeur effective** ; `server.servlet.session.timeout=2h` (l.23) est de la config morte (ignorée), avec un commentaire l.22 qui la contredit. Manerial tranche : **on part sur 1 heure**. FR-066 amendé (P-PRD3, appliqué) — déconnexion automatique après 1 h d'inactivité sur postes bénévoles partagés, choix de sécurité délibéré. Le nettoyage de la ligne morte `server.servlet.session.timeout=2h` + du commentaire l.22 est **inclus dans le périmètre de la Story 4.8 (Partie B)** — pas une story ni un commit séparés.
4. **Feedback de déconnexion (UX-DR21)** — ✅ **Résolue.** **Pas** de message distinct. La disparition silencieuse du panier est acceptée ; le retour de déconnexion générique (« Vous avez été déconnecté », redirection vers `/login`) suffit. UX-DR21 reste centré sur la transition de phase ; aucune nouvelle chaîne i18n dédiée à l'annulation du panier.
