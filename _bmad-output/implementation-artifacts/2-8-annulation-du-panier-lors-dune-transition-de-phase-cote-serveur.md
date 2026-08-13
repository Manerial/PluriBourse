---
baseline_commit: a0b418f307ecb55d145101fa4b7d314329ce082c
---

# Story 2.8: Annulation du panier lors d'une transition de phase — côté serveur

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrateur déclenchant une transition de phase,
I want que le serveur invalide automatiquement les paniers POS actifs et notifie les clients concernés via SSE,
so that les bénévoles en caisse ne puissent pas finaliser une vente dans une phase qui n'est plus valide.

## Acceptance Criteria

1. **Annulation à toute transition, quelle que soit la direction (FR-090).** Étant donné qu'au moins un `Basket` existe pour l'édition (un bénévole a un panier actif sur `/volunteer/pos`), quand l'admin déclenche `POST /api/admin/editions/{id}/phase/advance` **ou** `POST /api/admin/editions/{id}/phase/rollback`, alors tous les `Basket` (et leurs `BasketItem`) de cette édition sont supprimés en base **et** un événement SSE `basket-cancelled` est diffusé à tous les clients connectés.
2. **Silence si aucun panier actif.** Étant donné qu'aucun `Basket` n'existe pour l'édition au moment de la transition, quand la transition se produit, alors **aucun** événement `basket-cancelled` n'est diffusé — seul `phase-changed` l'est (comportement déjà existant, Story 2.6, non modifié).
3. **Contenu du payload.** Le payload de `basket-cancelled` contient `editionId` (l'édition dont la phase vient de changer) et `newPhase` (la nouvelle phase) — même convention de nommage que `PhaseChangedEventDto` (`editionId`, `newPhase`).
4. **Atomicité avec la transition.** Étant donné qu'une transition de phase est refusée (ex. `advance` vers DEPOSIT sans catégories configurées, `rollback` depuis PREPARATION, `advance` depuis CLOSED), quand la requête échoue avec un 422, alors aucun panier n'est supprimé et aucun événement SSE n'est diffusé — la suppression des paniers et la mise à jour de la phase font partie de la même transaction, comme `phase-changed` (Story 2.6, diffusion différée à `afterCommit`).
5. **Portée : tous les paniers de l'édition, pas seulement celui d'un bénévole.** Si plusieurs bénévoles ont chacun un panier actif sur la même édition, la transition les supprime tous en un seul passage.

**Hors périmètre de cette story** (`epics.md` note de dev, Story 2.8) : la réception de l'événement côté Angular (toast persistant, vidage du panier client, désactivation du scanner) est traitée par la **Story 4.6**, qui dépend de celle-ci.

## Tasks / Subtasks

- [x] **Backend — `BasketRepository` : nouvelle requête (AC 1, 2, 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketRepository.java` (UPDATE) : ajouter
    ```java
    List<Basket> findAllByEditionId(Long editionId);
    ```
    Même convention dérivée que la méthode existante `findByEditionIdAndUserId` — pas de `@Query` nécessaire.

- [x] **Backend — nouveau DTO d'événement SSE (AC 3)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/BasketCancelledEventDto.java` (NEW) :
    ```java
    package org.pluribourse.shared.sse;

    import org.pluribourse.domain.edition.entity.PhaseType;

    public record BasketCancelledEventDto(Long editionId, PhaseType newPhase) {
    }
    ```
    Même patron que `PhaseChangedEventDto` (record, même package). Ne pas réutiliser `PhaseChangedEventDto` tel quel : son champ `previousPhase` n'a pas de sens pour cet événement et le nom de type serait trompeur pour les deux event listeners Angular distincts que la Story 4.6 devra créer.

- [x] **Backend — `EditionService` : annuler les paniers actifs à la transition (AC 1, 2, 3, 4, 5)**
  - [x] `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` (UPDATE) :
    - Ajouter le champ `private final BasketRepository basketRepository;` (constructeur généré par `@RequiredArgsConstructor`, rien d'autre à changer côté injection).
    - Ajouter les imports `import org.pluribourse.domain.pos.entity.*;` et `import org.pluribourse.domain.pos.repository.*;` — cohérent avec le style d'import wildcard déjà utilisé dans ce fichier (`import org.pluribourse.domain.item.repository.*;` existe déjà pour `ItemRepository`, même précédent de dépendance cross-domaine depuis `EditionService`).
    - Modifier `savePhaseThenSendEvent` (méthode privée, appelée par `advancePhase` ET `rollbackPhase` — donc couvre les deux directions pour l'AC 1 sans dupliquer la logique) :
    ```java
    private EditionDto savePhaseThenSendEvent(Long id, Edition edition, PhaseType newPhase, PhaseType previousPhase) {
        edition.setPhase(newPhase);
        Edition saved = repository.save(edition);
        PhaseChangedEventDto phaseChangedEvent = new PhaseChangedEventDto(id, newPhase, previousPhase);

        List<Basket> activeBaskets = basketRepository.findAllByEditionId(id);
        BasketCancelledEventDto basketCancelledEvent = activeBaskets.isEmpty()
                ? null
                : new BasketCancelledEventDto(id, newPhase);
        if (!activeBaskets.isEmpty()) {
            basketRepository.deleteAll(activeBaskets);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterRegistry.broadcast("phase-changed", phaseChangedEvent);
                if (basketCancelledEvent != null) {
                    sseEmitterRegistry.broadcast("basket-cancelled", basketCancelledEvent);
                }
            }
        });
        return mapper.toDto(saved);
    }
    ```
    **Pourquoi ça satisfait l'AC 4 (atomicité) sans test dédié :** `advancePhase`/`rollbackPhase` valident déjà toutes les règles métier (catégories configurées, phase CLOSED terminale, rollback bloqué depuis PREPARATION/après archivage) **avant** d'appeler `savePhaseThenSendEvent` — si une garde lève une exception, la méthode n'est jamais atteinte, donc ni la suppression des paniers ni l'enregistrement du `TransactionSynchronization` n'ont lieu. La méthode entière reste `@Transactional` (annotation déjà sur `advancePhase`/`rollbackPhase`), donc si une erreur survenait *pendant* `savePhaseThenSendEvent` lui-même, Spring annulerait la transaction et le `TransactionSynchronization.afterCommit()` ne serait jamais invoqué (il ne l'est qu'après un commit réussi) — aucun broadcast ne fuiterait pour une transition qui n'a pas eu lieu. C'est exactement le même raisonnement que celui déjà appliqué à `phase-changed` depuis la Story 2.6 ; ne pas réinventer une garde supplémentaire ici.
    **Pourquoi la suppression peut avoir lieu à n'importe quelle transition sans se soucier de la phase cible :** un `Basket` ne peut être créé que pendant la phase SALE (`PosBasketService.getOrCreateCurrentBasket` appelle `PhaseGuard.requireSalePhase` avant toute lecture/écriture). Comme cette story supprime systématiquement tous les paniers de l'édition à **chaque** transition (pas seulement en sortie de SALE), il ne peut jamais rester de panier résiduel au moment où l'édition revient en SALE par un retour arrière — inutile de conditionner la suppression à `previousPhase == SALE` ou `newPhase == SALE`.

- [x] **Backend — test d'intégration (AC 1, 2, 3, 5)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java` (NEW) — `class PosBasketCancellationIT extends IntegrationTest` + `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` (patron obligatoire CLAUDE.md, ne pas s'appuyer implicitement sur les autres classes citées ci-dessous). Storyboard dédié (CLAUDE.md : une classe = un scénario métier), calqué sur le patron `PhaseTransitionIT` (assertion SSE via `MockMvc` async, cf. `sse_endpoint_...`) et sur la création vendeur/article de `PosBasketIT` (`POST /api/sellers`, `POST /api/items`, `GET /api/pos/baskets/current`, `POST /api/pos/baskets/{basketId}/items?barcode=...`).
    **Important — une connexion SSE dédiée par scénario, jamais réutilisée entre eux :** `SseEmitterRegistry` ne rejoue pas l'historique — une connexion ouverte juste avant une transition ne capture que les événements de *cette* transition. Réutiliser une seule connexion à travers plusieurs transitions rendrait l'assertion "silence" de l'étape 5 non probante (le corps contiendrait déjà `basket-cancelled` de l'étape 4, donc `contains("basket-cancelled")` resterait vrai même si un second événement fuitait à tort). Storyboard suggéré :
    1. Login admin + volunteer1. Créer une édition + une catégorie. Avancer en DEPOSIT. Créer un vendeur + **deux** articles (les deux doivent être créés maintenant : la création d'article n'est possible qu'en phase DEPOSIT, et l'étape 6 a besoin d'un second article après être repassé par DEPOSIT). Le code-barres est déterministe (FR-026 : 4 chiffres n° vendeur dans l'édition + 4 chiffres n° article dans l'inventaire du vendeur) — pour le premier vendeur d'une édition fraîche, les deux articles ont pour code-barres `"00010001"` et `"00010002"`, exactement comme les constantes `ITEM_1_BARCODE`/`ITEM_2_BARCODE` de `PosBasketIT` ; pas besoin de les relire en base. Avancer en SALE.
    2. Volunteer : `GET /api/pos/baskets/current` (crée le panier), puis `POST /api/pos/baskets/{basketId}/items?barcode=00010001` pour scanner le premier article.
    3. **Connexion SSE #1** (admin) : `get("/api/sse/events").session(adminSession)`, `andExpect(request().asyncStarted())`, `.andReturn()`. Puis déclenche `POST /api/admin/editions/{id}/phase/advance` (SALE → POST_SALE).
    4. Assertions sur connexion #1 : réponse 200 + `$.phase` = `POST_SALE` ; **état BDD** (`basketRepository.findAllByEditionId(editionId)` vide — pattern CLAUDE.md « vérifier l'état en BDD après ») ; corps de la réponse SSE contient `"basket-cancelled"` et `"newPhase":"POST_SALE"` (AC 1, 3).
    5. Scénario "silence" (AC 2) : **ouvrir une connexion SSE #2 fraîche** (nouvel appel `get("/api/sse/events")`), puis rollback POST_SALE → SALE (aucun panier n'existe à ce moment, il a été supprimé à l'étape 4). Assertion sur le corps de la connexion #2 (celle-ci n'a jamais vu l'événement de l'étape 4) : contient `"phase-changed"` mais **pas du tout** `"basket-cancelled"`.
    6. Scénario retour arrière avec panier actif (AC 1, 5 — couvre la direction rollback) : volunteer recrée un panier (encore en SALE après l'étape 5, `GET /api/pos/baskets/current` renvoie un nouveau panier vide puisque l'ancien a été supprimé) + scanne le second article (`barcode=00010002`). **Ouvrir une connexion SSE #3 fraîche**, puis admin déclenche `POST /api/admin/editions/{id}/phase/rollback` (SALE → DEPOSIT). Assertions sur connexion #3 : mêmes vérifications qu'à l'étape 4 (panier supprimé en BDD, corps contient `basket-cancelled` avec `"newPhase":"DEPOSIT"`).
  - [x] Autowire `BasketRepository` dans le test pour la vérification d'état BDD (même pattern que `EditionRepository` dans `PhaseTransitionIT` ou `ItemRepository` dans `PosBasketIT`).

- [x] **Backend — correctif non anticipé : `PosBasketIT` cassée par le nouveau comportement (régression légitime, pas un bug de cette story)**
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` (UPDATE) — `phase_guard_rejects_all_five_endpoints_once_sale_phase_ends` (@Order 23) échouait après implémentation : `addItem` attendait 422 (`sale-phase-required`) mais recevait 404. Cause : cette méthode vérifie l'ownership du panier (`requireOwnedBasket`) **avant** la garde de phase, contrairement aux quatre autres endpoints du même test (`GET current`, `removeItem`, `removeLot`, `validate`) qui vérifient la phase en premier. Comme cette story supprime désormais le panier à la transition, `addItem` sur le `basketId` maintenant supprimé tombe légitimement en 404 `basket-not-found` — une réponse plus exacte que l'ancien 422 (la vraie raison est que le panier a été annulé, pas seulement que la phase a changé). Le Javadoc de la méthode annonçait déjà explicitement ce changement à venir (« a basket may legitimately outlive a phase change — its automatic cancellation is story 2.8 »). Renommée en `phase_guard_rejects_four_endpoints_and_the_cancelled_basket_404s_add_item`, assertion `addItem` mise à jour (404 `basket-not-found`), Javadoc réécrit pour expliquer les deux comportements distincts. Suite complète re-validée après correctif : 381/381 backend, 0 régression restante.

## Review Findings

- [x] [Review][Patch] AC 4 (atomicité sur transition refusée) non testée — aucun test ne prouve qu'un panier survit à une transition rejetée (422) [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java] — Corrigé : `rollback_to_preparation_reaches_the_edge_of_the_state_machine` (@Order 5) puis `rejected_transition_broadcasts_no_event_at_all` (@Order 6) prouvent qu'un rollback rejeté depuis PREPARATION ne diffuse aucun événement SSE (ni `phase-changed` ni `basket-cancelled`) et laisse la phase inchangée. Un panier réel ne peut pas coexister avec une transition rejetée (un `Basket` n'existe qu'en phase SALE, qui ne rejette jamais de transition) — documenté explicitement dans le Javadoc de la classe et de la méthode plutôt que masqué.
- [x] [Review][Patch] AC 5 (portée multi-paniers) non testée — `findAllByEditionId` n'est jamais exercé avec 2 paniers actifs simultanés (2 bénévoles) [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java] — Corrigé : `advance_with_two_active_baskets_cancels_both_and_broadcasts_once` (@Order 2, réécrite) crée un panier pour `volunteer1` ET `volunteer2` sur la même édition, vérifie `basketRepository.findAllByEditionId` de taille 2 avant la transition puis vide après.
- [x] [Review][Patch] AC 1 (suppression des `BasketItem`) vérifiée seulement par inférence via la cascade FK, jamais directement en interrogeant les lignes `basket_items` [pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java] — Corrigé : la même méthode @Order 2 interroge désormais `basketItemRepository.findAllByBasketIdOrderById` pour chacun des deux paniers après la transition et vérifie une liste vide, plutôt que de s'appuyer uniquement sur `deleteCascade="true"`.

- [x] [Review][Defer] Race TOCTOU : un panier créé par un bénévole entre le `SELECT` (`findAllByEditionId`) et le commit de la transition de phase pourrait échapper à l'annulation (sous READ_COMMITTED, sa transaction verrait encore l'ancienne phase) [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:154-165] — deferred, pre-existing class of concurrency issue (fenêtre de course très étroite, admin transition ≠ hot path), pattern déjà traité par la Story 4.4 comme un effort dédié (Testcontainers), hors périmètre de cette story
- [x] [Review][Defer] Race TOCTOU : une vente validée avec succès concurremment entre le `SELECT` et le `deleteAll` pourrait déclencher un `basket-cancelled` trompeur pour un panier en réalité vendu (le second `DELETE` est un no-op sans corruption) [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:159-165] — deferred, faible sévérité, aucune perte de données
- [x] [Review][Defer] Race TOCTOU : un `addItem` concurrent à une transition de phase pourrait violer la contrainte FK `basket_items.basket_id` et remonter en 500 brut plutôt qu'une erreur domaine propre (correction d'une prédiction erronée de l'Edge Case Hunter, qui annonçait à tort un 200 silencieux — la FK rejette l'insertion) [pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java:74-95] — deferred, fenêtre de course étroite
- [x] [Review][Defer] `EditionService` dépend désormais directement du package `pos` (`BasketRepository`) — dépendance cross-domaine cohérente avec le précédent déjà établi (`ItemRepository` dans la même classe), pas un nouveau problème introduit par cette story [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java] — deferred, cohérent avec le patron existant du projet
- [x] [Review][Defer] `basketRepository.deleteAll(activeBaskets)` génère N `DELETE` individuels (Spring Data JPA) plutôt qu'une requête bulk `@Modifying` — optimisation valable si le volume grandissait [pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java:163-165] — deferred, négligeable à l'échelle du projet (NFR-001 : ~3 postes caissiers simultanés)

**Dismissed as noise (11) :** « paniers/lots non libérés après annulation » (faux positif — un article n'est jamais marqué « réservé » par sa présence dans un panier, seul `Item.sold` compte, positionné uniquement à `validate()` ; vérifié dans `PosBasketService`) · « aucune piste d'audit pour une opération destructive » (hors périmètre — un panier est un état éphémère pré-vente, pas un enregistrement financier ; seule une `Sale` l'est, non touchée par cette story ; aucun autre flux d'annulation du projet, y compris `phase-changed` depuis 2.6, n'a de piste d'audit) · « incohérence de nommage `editionId` vs `id` entre les deux DTO d'événement » (faux positif — vérifié contre `PhaseChangedEventDto.java` : son champ est bien `editionId`, identique à `BasketCancelledEventDto` ; confusion du Blind Hunter entre le nom de variable locale à l'appel et le nom du champ du record) · « payload sans identifiant de panier/utilisateur » (conforme à l'AC 3 explicite de la story — `{editionId, newPhase}` uniquement — et au raisonnement documenté dans les Dev Notes : diffusion globale, filtrage côté client prévu par la Story 4.6) · « connexions SSE non fermées dans le test » (patron déjà établi et identique dans `PhaseTransitionIT`, inoffensif grâce à `@DirtiesContext(AFTER_CLASS)`) · « le Javadoc de `PosBasketIT` affirme un ordre de vérification interne au contrôleur non visible dans le diff » (faux positif — vérifié vrai contre le code source réel de `PosBasketService.addItem`, l'ownership est bien vérifié avant le scan) · « imports wildcard élargissent la surface de collision » (convention déjà établie dans tout le projet, non introduite par cette story) · « nombre magique `List.of(1)` dans le test » (convention déjà identique dans `PosBasketIT`/`PhaseTransitionIT`) · « la formulation \"in either direction\" du Javadoc survend une logique de branchement inexistante » (mauvaise lecture — la phrase décrit l'exigence FR-090 satisfaite quelle que soit la direction via le point d'accroche partagé, pas une branche conditionnelle) · « exception non gérée sur `deleteAll` remonte en 500 » (comportement générique déjà identique pour tout appel BDD dans ce service et dans tout le codebase, pas un risque nouveau) · « aucune garde sur `previousPhase`, requête systématique à chaque transition » (voulu et déjà justifié dans les Dev Notes de la story ; coût négligeable, une transition de phase n'est pas un chemin chaud).

## Dev Notes

- **Point d'accroche unique pour les deux directions.** `advancePhase` et `rollbackPhase` appellent tous deux `savePhaseThenSendEvent` (`EditionService.java:99` et `:107`) — un seul endroit à modifier couvre l'AC 1 pour l'avance et le retour arrière, pas de duplication de logique entre les deux méthodes publiques.
- **`SseEmitterRegistry.broadcast` est un vrai broadcast, pas un ciblage par utilisateur.** Il n'existe aucune association emitter↔utilisateur dans l'infrastructure SSE actuelle (`shared/sse/SseEmitterRegistry.java`) — toute diffusion touche tous les clients connectés, exactement comme `phase-changed` depuis la Story 2.6. L'AC 2 de cette story ("aucun événement... n'est envoyé" à un bénévole sans panier actif) est donc satisfaite au niveau **global** : si zéro panier n'existe pour toute l'édition, aucun `basket-cancelled` n'est diffusé à personne. Un bénévole individuel sans panier actif qui recevrait quand même l'événement (parce qu'un *autre* bénévole avait un panier actif) n'est pas un bug — c'est exactement le "cas théorique" que la Story 4.6 AC 3 anticipe déjà côté client ("le composant l'ignore silencieusement").
- **Écart avec architecture.md à documenter, pas à corriger.** Le tableau « Événements SSE » d'`architecture.md` (§ Patrons de Communication) documente un payload générique `{reason: "phase-changed"}` pour `basket-cancelled` — obsolète face à l'AC explicite et normatif d'`epics.md` Story 2.8 (« le payload contient l'`editionId` et la nouvelle phase »). Suivre `epics.md`, pas `architecture.md`, sur ce point précis (même type d'écart déjà rencontré et tranché en faveur d'epics.md dans les stories précédentes de cet epic).
- **Déviation de package déjà actée, ne pas la « corriger ».** `architecture.md` documente `org.pluribourse.{feature}.{couche}` ; le code réel est `org.pluribourse.domain.{feature}.{couche}` (ex. `org.pluribourse.domain.edition.service`, `org.pluribourse.domain.pos.repository`). Suivre le code réel — c'est une déviation assumée par les stories précédentes de l'epic, hors périmètre de celle-ci.
- **Aucun changement frontend, aucun changement i18n.** Cette story est strictement backend (le message toast et la désactivation du scanner appartiennent à la Story 4.6). Aucune nouvelle clé `en.json`/`fr.json`, aucun nouveau texte utilisateur.
- **Pas de nouvelle exception ni de nouveau code d'erreur RFC 7807.** Le chemin ajouté ne peut pas échouer indépendamment de la transition de phase elle-même (voir AC 4 ci-dessus) — aucun `@ControllerAdvice`/`BusinessException` à ajouter.
- **Cascade de suppression déjà en place au niveau BDD.** `db/changelog/021-pos-baskets.xml` déclare déjà `deleteCascade="true"` sur la FK `basket_items.basket_id → baskets.id` — `basketRepository.deleteAll(activeBaskets)` (suppression au niveau entité, cascade JPA `Basket.items` en `CascadeType.ALL`/`orphanRemoval=true`) est donc doublement sûr, même filet de sécurité que celui déjà utilisé par `PosBasketService.validate()` (`basketRepository.delete(basket)` après une vente).
- **Cette story résout un defer déjà documenté — ne pas le retraiter ailleurs.** `deferred-work.md` (« Deferred from: code review of story-4-2-gestion-du-panier-validation-du-paiement », 2026-07-31) signale : *« `requireOwnedBasket()` ne vérifie pas que le panier appartient à l'édition active [...] Un `basketId` périmé pourrait en théorie continuer à recevoir des articles de l'édition actuellement active. À adresser avec la Story 2.8. »* La purge systématique de tous les paniers d'une édition à **chaque** transition (cette story) referme cette brèche en effet de bord, sans qu'il soit nécessaire de toucher `PosBasketService.requireOwnedBasket()` : un `basketId` ne peut plus jamais survivre à la sortie de la phase SALE de son édition, et une nouvelle édition ne peut devenir active (FR-010, `EditionAlreadyActiveException`) qu'une fois l'édition précédente sortie de toutes ses phases actives — donc après que ses paniers ont déjà été purgés. **Ne pas ajouter de vérification d'édition dans `requireOwnedBasket()`** : ce serait un doublon inutile (scope creep) une fois la purge en place. Une fois cette story `done`, retirer ou marquer résolue l'entrée correspondante dans `deferred-work.md`.

### Project Structure Notes

- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketRepository.java`
- Modifié : `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java`
- Nouveau : `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/BasketCancelledEventDto.java`
- Nouveau : `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java`
- Aucun fichier frontend touché par cette story.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 2.8] Story source, ACs, note de dev sur le périmètre (client → Story 4.6)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.6] Dépendance explicite sur cette story ("Dépendance : Story 2.8")
- [Source: _bmad-output/planning-artifacts/architecture.md#Notification de Changement de Phase] SSE via `SseEmitterRegistry`, événement `basket-cancelled` (payload générique obsolète, voir Dev Notes)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java] `advancePhase`/`rollbackPhase`/`savePhaseThenSendEvent` — point d'accroche
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseEmitterRegistry.java] Broadcast sans ciblage par utilisateur — pas de notion d'émetteur par session
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/sse/PhaseChangedEventDto.java] Patron de record à suivre pour `BasketCancelledEventDto`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/entity/Basket.java] `edition`, `user`, `items` (cascade ALL, orphanRemoval)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketRepository.java] `findByEditionIdAndUserId` existant — patron pour `findAllByEditionId`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java] Javadoc de classe : « son annulation côté serveur est la story 2.8, pas celle-ci » — confirme le périmètre et l'invariant "un panier ne peut exister qu'en phase SALE"
- [Source: pluribourse-backend/src/main/resources/db/changelog/021-pos-baskets.xml] FK `basket_items.basket_id` avec `deleteCascade="true"`
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/edition/PhaseTransitionIT.java] Patron de test SSE async (`request().asyncStarted()`, lecture du corps de réponse après plusieurs événements sur la même connexion)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java] Patron de création vendeur/article/panier pour le setup du nouveau test
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#Deferred from: code review of story-4-2-gestion-du-panier-validation-du-paiement (2026-07-31)] Defer explicitement adressé « à traiter avec la Story 2.8 » — `requireOwnedBasket()` ne vérifie pas l'édition active ; résolu en effet de bord par la purge systématique de cette story (voir Dev Notes)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

- **Régression légitime détectée par la suite complète, non anticipée par la story : `PosBasketIT.phase_guard_rejects_all_five_endpoints_once_sale_phase_ends` (@Order 23).** Après implémentation du point d'accroche `EditionService.savePhaseThenSendEvent`, `mvn test` (suite complète) est passée de 377/377 à 380/381 avec un seul échec : `Status expected:<422> but was:<404>` sur l'assertion `addItem` de ce test. Root cause : ce test pré-existant (Story 4.2/4.3) exerçait volontairement le scénario « panier qui survit à un changement de phase », un gap explicitement documenté comme étant le périmètre de cette story (Javadoc de la méthode elle-même, et `PosBasketService`). Sur les cinq endpoints testés, quatre (`GET current`, `removeItem`, `removeLot`, `validate`) vérifient la garde de phase **avant** l'ownership du panier — inchangés, toujours 422. `addItem` fait l'inverse (ownership d'abord) — avec le panier désormais supprimé par cette story au moment de la transition, il tombe légitimement en 404 `basket-not-found` au lieu de 422 `sale-phase-required`, une réponse plus exacte. Corrigé en mettant à jour l'assertion + le nom de la méthode + le Javadoc (voir Tasks/Subtasks) plutôt qu'en contournant le nouveau comportement — conformément à la règle « le comportement du système dans son ensemble doit rester correct, pas seulement satisfaire les AC de la story ». Suite complète re-validée : 381/381.

### Completion Notes List

- Backend : `BasketRepository.findAllByEditionId` (requête dérivée), `BasketCancelledEventDto` (record, patron `PhaseChangedEventDto`), `EditionService.savePhaseThenSendEvent` modifiée pour purger tous les `Basket`/`BasketItem` de l'édition à chaque transition de phase (avance et retour arrière, même point d'accroche partagé) et diffuser `basket-cancelled` (`{editionId, newPhase}`) uniquement si des paniers existaient, dans le même `TransactionSynchronization.afterCommit` que `phase-changed` — atomicité gratuite, aucune garde supplémentaire nécessaire.
- Aucun changement frontend, aucun changement i18n — strictement backend, conformément à la story (réception côté Angular = Story 4.6).
- Aucune nouvelle exception ni code d'erreur RFC 7807 introduit — le chemin ajouté ne peut pas échouer indépendamment de la transition de phase elle-même.
- Résout en effet de bord le defer documenté dans `deferred-work.md` (revue Story 4.2, 2026-07-31) sur `requireOwnedBasket()` ne vérifiant pas l'édition active — un `basketId` périmé ne peut plus survivre à la sortie de la phase SALE de son édition. Entrée à retirer/marquer résolue dans `deferred-work.md`.
- Tests backend : nouvelle classe `PosBasketCancellationIT` (4 tests — avance avec panier actif, silence sans panier actif, retour arrière avec panier actif, une connexion SSE fraîche par scénario pour des assertions non ambiguës) ; `PosBasketIT` mise à jour (régression légitime, voir Debug Log).
- Suite complète re-validée : **381/381 tests backend** (0 échec, 0 erreur), aucune régression restante. Aucun test frontend concerné (aucun fichier frontend modifié).

### File List

**Backend — NEW**
- `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/BasketCancelledEventDto.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java`

**Backend — UPDATE**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketRepository.java` — nouvelle méthode `findAllByEditionId`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/service/EditionService.java` — injection `BasketRepository`, `savePhaseThenSendEvent` purge les paniers actifs et diffuse `basket-cancelled`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` — régression légitime corrigée (voir Debug Log) : méthode renommée, assertion `addItem` → 404 `basket-not-found`, Javadoc mis à jour

## Change Log

- 2026-08-13 — dev-story : implémentation complète (purge des paniers actifs + diffusion SSE `basket-cancelled` sur toute transition de phase, `EditionService.savePhaseThenSendEvent` comme point d'accroche unique, nouveau test `PosBasketCancellationIT`). Un correctif non anticipé mais légitime : `PosBasketIT` mise à jour suite au changement de comportement désormais correct (panier supprimé → 404 au lieu de 422 sur `addItem`, seul des cinq endpoints à vérifier l'ownership avant la phase). 381/381 tests backend, aucune régression. Aucun changement frontend. Statut → review.
- 2026-08-13 — code-review (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : 0 decision-needed, 3 patch appliqués (tous des trous de couverture de test, aucun bug de production — AC 4 atomicité sur transition refusée, AC 5 portée multi-paniers, AC 1 suppression directe des `BasketItem`), 5 defer documentés dans `deferred-work.md` (trois races TOCTOU à fenêtre étroite déjà dans la même famille que celles acceptées aux Stories 4.2/4.4/4.5, dépendance cross-domaine `EditionService`→`BasketRepository` cohérente avec le précédent `ItemRepository` déjà en place, `deleteAll` non-bulk négligeable à l'échelle du projet), 11 rejetés comme bruit (dont deux faux positifs vérifiés contre le code source réel : nommage de champ DTO en réalité cohérent, ordre de garde du Javadoc `PosBasketIT` confirmé exact). `PosBasketCancellationIT` étendue de 4 à 6 tests (scénario à deux paniers simultanés remplaçant le scénario à un seul panier pour l'AC 2, deux nouveaux tests @Order 5/6 prouvant l'AC 4 via un rollback rejeté depuis PREPARATION — un panier réel ne peut pas coexister avec une transition rejetée, documenté explicitement plutôt que contourné). 383/383 tests backend re-validés après patchs, aucune régression. Statut → done.
