---
baseline_commit: e0d356ac5e98dd5d227e26b3821b63fc0c8c8ec2
---

# Story 4.9 : Détection de poste de caisse inactif et libération du panier

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant que bénévole caissier travaillant sur plusieurs postes de caisse,
je veux qu'un panier laissé sur un poste qui ne donne plus signe de vie (onglet fermé, navigateur planté, réseau coupé, ou session expirée après 1 h d'inactivité) soit automatiquement annulé et ses réservations de lot libérées,
afin qu'un lot ne reste pas invendable à **toutes** les caisses jusqu'au changement de phase parce qu'un collègue a abandonné son poste sans se déconnecter.

## Contexte & origine

Issue de la **SCP 2026-09-04** (`_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-04.md`, approuvée par Manerial le 2026-09-04). Déclencheur : point `decision-needed` **D2** de la revue de code de la **Story 4.8**.

**Le problème (D2).** La réservation de lot au scan livrée par la Story 4.8 (`lots.reserved_by_basket_id`, posée quand le premier membre d'un lot entre dans un panier de caisse) n'est levée **que** par une action explicite sur ce panier : retrait du dernier membre, `removeLot`, `validate()` réussi, changement de phase d'édition, ou `POST /auth/logout` **explicite**. Un poste fermé (onglet/navigateur), planté, sans réseau, ou simplement expiré par le timeout d'inactivité de 1 h **ne fait aucune de ces choses**. La réservation reste sur un panier mort et **chaque** membre du lot devient invendable **à tous les postes** jusqu'à ce que la transition SALE → POST_SALE lance l'annulation en masse. Le backend n'a **aucun signal fiable** pour un poste parti sans se déconnecter.

**Décision Manerial (D2, option 1).** Livrer la Story 4.8 telle quelle ; fermer la lacune dans une story de suivi dédiée — la présente story. C'est la « suite différée » que la SCP 2026-09-03 (§3, question ouverte #2 « story d'expiration de session ») avait explicitement renvoyée à après la livraison de 4.8, activée maintenant.

**Cause racine.** HTTP est requête/réponse : l'absence de requête ≠ un « l'utilisateur est parti » observable. Les trois façons dont un poste disparaît réellement — onglet/navigateur fermé, machine plantée / coupure de courant, perte réseau — ne produisent **aucun** signal backend ; la session reste dans `spring_session` jusqu'à son timeout d'inactivité de 1 h, et le nettoyage périodique de Spring Session JDBC fait alors un `DELETE` en masse de la ligne **sans** exécuter `LogoutFilter` / le moindre `LogoutHandler` et sans évènement applicatif fiable par session. Le seul mécanisme qui observe tout ça est un **signal de vie envoyé par le client** tant que la page caisse est ouverte, plus un **balayage serveur** qui annule les paniers dont le signal s'est tu. Ce même mécanisme **absorbe** la lacune du timeout d'inactivité FR-066 : une session expirée cesse d'émettre des heartbeats et est balayée comme n'importe quel poste mort.

**Ce qui est déjà fait (ne pas refaire).** Les amendements d'artefacts de la SCP 2026-09-04 sont **déjà appliqués et commités** (commit `e0d356a`, baseline de cette story) :

- `prd.md` : **FR-110 amendé** (durée de vie du panier *effectivement* bornée par une détection de poste inactif : heartbeat + balayage serveur) ; **FR-066 amendé** (un panier laissé sur une session expirée par inactivité est annulé et ses réservations libérées par la détection de poste inactif). **Pas de nouvelle FR** (décision SCP §5 #4).
- `epics.md` : lignes miroir FR-110 (l.85), FR-109 (l.93), FR-066 (l.123) ; UX-DR21 (l.206) étendue (« panier récupéré par la détection de poste inactif → disparition silencieuse, panier vide au retour du poste, **aucune chaîne i18n dédiée** ») ; cartes de couverture FR-066 (l.281), FR-109 (l.261), FR-110 (l.307) ; note Story 2.8 (l.1028) étendue (« la variante silencieuse `cancelBasketSilently` est également invoquée par le balayage de postes inactifs, Story 4.9 »).
- `architecture.md` : § Concurrence — POS, ligne « Intégrité des lots » (l.237) — « détection de poste inactif (heartbeat absent → balayage serveur, Story 4.9) » ajoutée à l'énumération des déclencheurs de libération ; § Notification de Changement de Phase, nouvelle ligne **« Déclencheur (ter) »** (l.252) — le balayage `@Scheduled` réutilise `cancelBasketSilently`, **aucun** broadcast.

  → Conséquence : **cette story n'a aucun amendement `architecture.md` / `prd.md` / `epics.md` à porter** ; elle implémente ce qui y est déjà décrit. Seul reste un point de *bookkeeping* (T9, note W5 de la Story 4.8). `EXPERIENCE.md` volontairement non amendé (dérive documentaire connue et assumée, même convention que 2.7 / 2.9 / 3.14 / 4.7 / 5.8 / 4.8).

**Statut des stories touchées (toutes `done`, jamais rouvertes).** 4.8 (origine D2 / note W5 — mise à jour de bookkeeping uniquement), 2.8 (note déjà étendue dans `epics.md`).

**Portée technique.** Back : 1 migration Liquibase (`036`), 1 colonne sur `baskets`, 1 endpoint heartbeat, 1 tâche `@Scheduled` (premier usage de `@EnableScheduling` du projet), 1 correctif ciblé dans `PosBasketService.validate()`. Front : `pos-page.component.ts` + `pos.service.ts` uniquement — **aucun template, aucune route, aucun composant, aucune dépendance, aucune clé i18n nouvelle**. Tests : 1 nouvelle classe IT + ajouts à 3 classes existantes.

**Story indivisible.** L'endpoint, la colonne, le minuteur front et le reaper forment **un seul mécanisme** : livrer un sous-ensemble laisse la lacune ouverte. Effort : moyen. Risque : faible à moyen.

---

## Points de conception figés (SCP §5 — ne pas rediscuter)

| Sujet | Décision |
|---|---|
| Seuil d'inactivité (« dead-threshold ») | `PT3M` par défaut (≈ 3 heartbeats de 60 s manqués). Propriété `pos.basket.heartbeat.dead-threshold`, ajustable sans redéploiement (ex. `PT5M` si le wifi de la salle est trop instable). |
| Fréquence du balayage | `PT2M` par défaut. Propriété `pos.basket.reaper.interval`. |
| Le reaper touche-t-il la session ? | **Non.** Il annule le **panier seul** (libère les réservations, supprime panier + items). Il n'invalide **pas** la session. Un poste qui revient obtient un panier vide neuf, exactement comme après un logout / un changement de phase. |
| TTL / purge par réservation ? | **Non.** Le signal de vie est au niveau **panier** (`baskets.last_seen_at`), pas au niveau réservation. `lots.reserved_at` (colonne diagnostique de la Story 4.8) n'est **pas** lue par le reaper. |
| Condition de balayage | Purement temporelle : `last_seen_at` antérieur à `now - dead-threshold`. **Pas** de recoupement avec `spring_session`. |
| Nouvelle FR ? | **Non.** Amendement de FR-110 + FR-066 (déjà fait). |
| `navigator.sendBeacon` sur `pagehide` | **Hors périmètre.** Petit suivi possible plus tard (nettoyage instantané sur une fermeture *propre* d'onglet uniquement ; ne couvre pas le plantage). Ne pas l'implémenter ici, ne pas rouvrir le sujet en revue. |
| Index / FK sur `last_seen_at` | **Aucun.** Volume v1 : une poignée de paniers actifs par édition. |
| SSE émis par le reaper | **Aucun** broadcast, jamais. |
| i18n / UX du panier balayé | Réutilise le comportement existant « panier introuvable → panier vide au prochain geste » (UX-DR21 étendu). **Aucune** chaîne dédiée, aucun toast. |
| Tolérance à la course | Le reaper doit tolérer un panier annulé entre sa requête et son appel `cancelBasketSilently` (`validate()` / logout / changement de phase concurrents) → capture et poursuite panier par panier. |

---

## Acceptance Criteria

> Les blocs Given/When/Then dérivent des « Success criteria » et du tableau « Technical impact » de la SCP 2026-09-04, et des lignes déjà amendées dans `architecture.md` (§ Concurrence — POS, § Notification de Changement de Phase « Déclencheur (ter) ») et `epics.md` (UX-DR21).

**AC1 — Endpoint heartbeat**
**Étant donné** une édition en phase Vente et un panier de caisse appartenant au caissier
**Quand** le client appelle `POST /api/pos/baskets/{basketId}/heartbeat`
**Alors** `baskets.last_seen_at` du panier est mis à l'instant courant
**Et** la réponse est **204 No Content** (corps vide)
**Et** l'endpoint exige un utilisateur authentifié non-`SELLER` (il tombe sous la règle globale `anyRequest()` de `SecurityConfig` — aucun nouveau matcher, aucune exemption CSRF ; c'est un `POST` protégé CSRF comme les autres endpoints POS)
**Et** un heartbeat pour un `basketId` qui n'existe pas ou qui appartient à un autre utilisateur renvoie **404** (`BasketNotFoundException`, garde `requireOwnedBasket` — identique aux autres endpoints POS, IDOR-safe : pas de distinction entre « inexistant » et « pas à vous »)
**Et** un heartbeat hors phase Vente renvoie l'erreur de la garde de phase existante (`SalePhaseRequiredException`)

**AC2 — Toute activité rafraîchit `last_seen_at`**
**Étant donné** un panier de caisse
**Quand** l'une des opérations `getOrCreateCurrentBasket`, `addItem`, `removeItem`, `removeLot` s'exécute avec succès, ou quand le panier vient d'être créé
**Alors** `baskets.last_seen_at` est positionné à l'instant courant dans la même transaction
**Et** un panier activement utilisé (heartbeat ou action réelle depuis moins de `dead-threshold`) n'est **jamais** balayé par le reaper

**AC3 — Le reaper annule les paniers morts et libère leurs réservations**
**Étant donné** un panier actif dont `last_seen_at` est antérieur à `now - pos.basket.heartbeat.dead-threshold`
**Quand** la tâche `BasketReaperService` s'exécute (par le planificateur, ou invoquée directement en test)
**Alors** ce panier et tous ses `BasketItem` sont supprimés via `BasketCancellationService.cancelBasketSilently`
**Et** chaque `lots.reserved_by_basket_id` que ce panier détenait repasse à `NULL`
**Et** un second poste peut ensuite scanner un article de ce lot (le lot est redevenu vendable)
**Et** la tâche est planifiée à `@Scheduled(fixedDelayString = "${pos.basket.reaper.interval}")` (défaut `PT2M`)
**Et** le reaper n'émet **aucun** broadcast SSE (`cancelBasketSilently` n'enregistre aucune `TransactionSynchronization`)

**AC4 — Session expirée par inactivité : même nettoyage, aucun mécanisme dédié**
**Étant donné** un panier actif laissé sur une session qui a atteint le timeout d'inactivité FR-066 (1 h, `spring.session.timeout=PT1H`)
**Quand** le heartbeat s'est tu depuis plus que `dead-threshold` (la page caisse n'émet plus rien)
**Alors** le panier est annulé et ses réservations libérées par **le même** balayage que pour un poste fermé ou planté — il n'existe **pas** de code branché sur un évènement d'expiration de session
**Et** le reaper ne consulte jamais `spring_session` (condition purement temporelle)

**AC5 — Tolérance à la course, best-effort, pas de donnée personnelle dans les logs**
**Étant donné** un balayage qui a sélectionné plusieurs paniers périmés
**Quand** l'un d'eux est annulé en concurrence (par un `validate()`, un logout ou un changement de phase) entre la requête du reaper et son appel `cancelBasketSilently`
**Alors** le reaper capture l'exception, la journalise sans donnée personnelle (ni nom, ni e-mail, ni téléphone vendeur, ni identifiant utilisateur — patron `BasketCancellingLogoutHandler` / `SessionInvalidationService`), et **poursuit** avec les paniers suivants
**Et** aucune réponse 500 ni interruption du balayage ne survient
**Et** chaque panier est traité dans sa propre transaction (`cancelBasketSilently` est `@Transactional` ; un échec sur un panier n'annule pas le nettoyage des autres)

**AC6 — Reaper inerte dans la suite de tests**
**Étant donné** le profil de test
**Alors** `pos.basket.reaper.enabled=false` est présent dans `src/test/resources/application.properties`
**Et** `BasketReaperService` est neutralisé quand cette propriété vaut `false` (`@ConditionalOnProperty(name = "pos.basket.reaper.enabled", matchIfMissing = true)`)
**Et** la méthode planifiée ne se déclenche **jamais** d'elle-même pendant la suite IT (aucune collision avec les scénarios story-board persistants de `PosBasketIT` / `PosBasketLogoutCancellationIT`)
**Et** `PosBasketReaperIT` invoque la méthode du reaper **directement**

**AC7 — `validate()` libère la réservation d'un lot rejeté au pré-check « frère vendu committé » (Blind Hunter #7)**
**Étant donné** un panier contenant un membre d'un lot dont un frère a **déjà été vendu dans une vente committée**, la réservation de ce lot étant détenue par ce panier
**Quand** le caissier valide le paiement et que `PosBasketService.validate()` rejette au pré-check `existsByLotIdAndSoldTrue` (`LotAlreadySoldException`, 409 `lot-already-sold`)
**Alors** la réservation de ce lot (`lots.reserved_by_basket_id`) est libérée **avant** que l'exception ne se propage — d'une manière qui **survit au rollback** de la transaction `@Transactional` de `validate()` (la réservation a été committée par une transaction `addItem` antérieure ; une simple libération dans la transaction de `validate()` serait annulée avec l'exception)
**Et** le reste du comportement de `validate()` est **inchangé** (les autres chemins d'échec — `EmptyBasketException`, `BasketValidationConflictException`, `InvalidAmountGivenException` — ne libèrent toujours rien : le panier existe encore, le caissier peut réessayer, et le reaper / le logout finiront par nettoyer)
**Et** un lot que ce panier ne pourra plus jamais valider n'est plus laissé bloqué à toutes les caisses

**AC8 — Minuteur de vie côté client**
**Étant donné** la page caisse (`pos-page`) montée avec un panier actif
**Quand** `HEARTBEAT_INTERVAL_MS` (60 000 ms) s'écoule
**Alors** le composant appelle `POST /api/pos/baskets/{id}/heartbeat` avec l'id du panier courant
**Et** le minuteur est arrêté à la destruction du composant (`takeUntilDestroyed(this.destroyRef)` — pas de `implements OnDestroy`, cohérent avec l'abonnement SSE existant)
**Et** aucun heartbeat n'est envoyé tant que `basketCancelled()` est vrai ou que `basket()` est `null`
**Et** un heartbeat en échec (réseau, 404 panier déjà balayé, 409 mauvaise phase) est **avalé silencieusement** : aucun toast, aucun `lastScanIssue`, et le minuteur continue d'émettre aux intervalles suivants (l'erreur est capturée **dans** le callback — `subscribe({ error: () => {} })` ou `catchError` — sinon une seule erreur HTTP terminerait le flux `interval`)
**Et aucune** nouvelle clé i18n, **aucun** nouveau template, route, composant ou dépendance

**AC9 — Migration `036` & mapping d'entité**
**Étant donné** `pluribourse-backend/src/main/resources/db/changelog/036-basket-last-seen.xml`
**Alors** il contient un seul `changeSet id="036-basket-last-seen" author="pluribourse"` qui fait `<addColumn tableName="baskets">` de `last_seen_at` type `DATETIME`, `nullable="true"` (nullable **uniquement** pour que l'`addColumn` soit légal sur d'éventuelles lignes existantes ; le code l'écrit toujours — création du panier, chaque action POS, chaque heartbeat)
**Et** il n'y a **ni FK, ni index**
**Et** il a un `<rollback>` explicite qui fait `<dropColumn tableName="baskets" columnName="last_seen_at"/>`
**Et** un commentaire XML référence la SCP 2026-09-04, FR-110 / FR-066, et la raison du choix « nullable, pas de FK, pas d'index »
**Et** `<include file="db/changelog/036-basket-last-seen.xml"/>` est la **dernière** ligne de `db.changelog-master.xml` (après `035`)
**Et** l'entité `Basket` mappe la colonne (`@Column(name = "last_seen_at")`, `LocalDateTime lastSeenAt`) de façon cohérente avec `spring.jpa.hibernate.ddl-auto=validate` (colonne nullable → pas de `nullable = false` sur l'entité)
**Et** la migration s'applique automatiquement au contexte de test H2 (le changelog de test inclut le master) — aucune action de test spécifique

**AC10 — Vérification de build**
**Étant donné** la story livrée
**Quand** on lance `./mvnw clean package` puis, dans `pluribourse-frontend/`, `npm test` et `npm run build`
**Alors** les trois commandes réussissent sans échec ni warning (`PosBasketReaperIT` invoque la méthode planifiée directement ; le reaper est désactivé dans le profil de test ; `SaleConcurrencyIT` reste *skipped* si Docker est absent — inchangé)
**Et** la parité des clés i18n `fr.json` ⇔ `en.json` est inchangée (aucune clé ajoutée ni retirée)

---

## Tasks / Subtasks

### T1 — Modèle de données : colonne `baskets.last_seen_at` (AC : 9)

- [x] Créer `pluribourse-backend/src/main/resources/db/changelog/036-basket-last-seen.xml` :
  - [x] `changeSet id="036-basket-last-seen" author="pluribourse"`.
  - [x] Commentaire XML : réf. SCP 2026-09-04, FR-110 / FR-066, rôle de la colonne (signal de vie du poste), justification « nullable pour légaliser l'`addColumn`, le code l'écrit toujours ; pas de FK ; pas d'index (volume v1) ».
  - [x] `<addColumn tableName="baskets"><column name="last_seen_at" type="DATETIME"><constraints nullable="true"/></column></addColumn>`.
  - [x] `<rollback><dropColumn tableName="baskets" columnName="last_seen_at"/></rollback>`.
  - [x] **Ni** `<addForeignKeyConstraint>`, **ni** `<createIndex>`.
- [x] Ajouter `<include file="db/changelog/036-basket-last-seen.xml"/>` en **dernière** ligne de `db.changelog-master.xml`, après le `<include>` de `035-lot-reservation.xml`, avant `</databaseChangeLog>`. Format identique aux autres (`db/changelog/NNN-nom.xml`, pas de `relativeToChangelogFile`).
- [x] `domain/pos/entity/Basket.java` : ajouter `private LocalDateTime lastSeenAt;` avec `@Column(name = "last_seen_at")` ; ajouter l'import `java.time.LocalDateTime`. Ne **pas** mettre `nullable = false` (cohérence `ddl-auto=validate` ↔ migration nullable). Ne **pas** ajouter `@Version`, ni `@CreationTimestamp`/`@UpdateTimestamp`, ni `@PrePersist` — l'affectation est **explicite** dans le service (convention du projet, cf. `Sale.setSoldAt(LocalDateTime.now())` dans `validate()`).

### T2 — Heartbeat côté serveur (AC : 1, 2)

- [x] `domain/pos/service/PosBasketService.java` :
  - [x] Nouvelle méthode `@Transactional public void recordHeartbeat(Long basketId, Long userId)` :
    - [x] `phaseGuard.requireSalePhase()` (même garde que les 4 méthodes mutantes).
    - [x] `Basket basket = requireOwnedBasket(basketId, userId);` (garde IDOR existante — lève `BasketNotFoundException` 404 si absent ou pas au caissier).
    - [x] `basket.setLastSeenAt(LocalDateTime.now());` (le `@Transactional` + entité managée suffisent au flush ; pas de `save` explicite requis, aligné sur le reste du service).
    - [x] JavaDoc : rôle (signal de vie de la page caisse), pourquoi la garde de phase, retour `void`.
  - [x] Introduire un helper privé `private void touch(Basket basket) { basket.setLastSeenAt(LocalDateTime.now()); }` et l'appeler dans `getOrCreateCurrentBasket` (sur le panier retourné, existant ou fraîchement créé), `addItem`, `removeItem`, `removeLot`, et dans `createBasket` (valeur initiale à la création). **Ne pas** toucher `validate()` (le panier y est supprimé de toute façon).
  - [x] Vérifier qu'aucune autre méthode ne mute un `Basket` sans passer par `touch` (à ce jour : les 4 ci-dessus + création ; si une future mutation apparaît, elle devra `touch`).
- [x] `domain/pos/controller/PosBasketController.java` :
  - [x] `@PostMapping("/{basketId}/heartbeat")` → `public ResponseEntity<Void> heartbeat(@PathVariable Long basketId, Authentication authentication)` : `service.recordHeartbeat(basketId, userId(authentication)); return ResponseEntity.noContent().build();`
  - [x] Réutiliser le helper `userId(Authentication)` existant (l.61-63). Pas de `@PreAuthorize` (héritage de la règle globale non-`SELLER`).
- [x] `SecurityConfig` : **aucun changement** — `/pos/baskets/*/heartbeat` tombe sous `anyRequest()` (authentifié non-`SELLER`) et sous la protection CSRF standard (ne **pas** l'ajouter à `ignoringRequestMatchers`).

### T3 — Infrastructure de planification (AC : 3, 6)

- [x] Créer `shared/config/SchedulingConfig.java` : `@Configuration @EnableScheduling` (classe dédiée, **pas** `@EnableScheduling` sur `PluribourseApplication` — celle-ci a un mode CLI `WebApplicationType.NONE` et l'annotation y serait active dans **tous** les `@SpringBootTest`). Javadoc : premier usage de `@Scheduled` du projet.
- [x] `application.properties` : ajouter (bloc commenté, à la suite des propriétés applicatives type `printerbridge.*`) :
  - [x] `pos.basket.heartbeat.dead-threshold=PT3M`
  - [x] `pos.basket.reaper.interval=PT2M`
  - [x] `pos.basket.reaper.enabled=true`
  - [x] Commentaire : relation des trois durées — minuteur front 60 s **<** `dead-threshold` (`PT3M` ≈ 3 battements manqués) **≪** `spring.session.timeout=PT1H` ; `dead-threshold` ajustable à `PT5M` sans redéploiement si le réseau de la salle est instable.
- [x] `src/test/resources/application.properties` : ajouter `pos.basket.reaper.enabled=false`.

### T4 — `BasketReaperService` (AC : 3, 4, 5)

- [x] `domain/pos/repository/BasketRepository.java` : ajouter la requête de balayage. **Recommandé** (défense contre d'éventuelles lignes `last_seen_at IS NULL` antérieures à la migration) :
  ```java
  @Query("SELECT b FROM Basket b WHERE b.lastSeenAt IS NULL OR b.lastSeenAt < :threshold")
  List<Basket> findStale(@Param("threshold") LocalDateTime threshold);
  ```
  (Une dérivée `findAllByLastSeenAtBefore(LocalDateTime)` suffit fonctionnellement puisque `createBasket` écrit toujours la colonne ; la variante `@Query` avec `IS NULL` est le filet ceinture-bretelles — justifier le choix retenu dans les Dev Agent Record.)
- [x] Créer `domain/pos/service/BasketReaperService.java` :
  - [x] `@Service @Slf4j @RequiredArgsConstructor`, `@ConditionalOnProperty(name = "pos.basket.reaper.enabled", matchIfMissing = true)`.
  - [x] Dépendances : `BasketRepository`, `BasketCancellationService`. (Pas de cycle : `BasketCancellationService` ne dépend que de `BasketRepository` / `LotRepository` / `SseEmitterRegistry`.)
  - [x] `@Value("${pos.basket.heartbeat.dead-threshold}") private Duration deadThreshold;` (Spring convertit `PT3M` en `java.time.Duration`).
  - [x] `@Scheduled(fixedDelayString = "${pos.basket.reaper.interval}") @Transactional(propagation = Propagation.NEVER)` (ou méthode non transactionnelle) `public void reapInactiveBaskets()` :
    - [x] `LocalDateTime threshold = LocalDateTime.now().minus(deadThreshold);`
    - [x] `List<Basket> stale = basketRepository.findStale(threshold);`
    - [x] `for (Basket basket : stale) { try { basketCancellationService.cancelBasketSilently(basket); } catch (RuntimeException e) { log.warn("Failed to reap inactive POS basket", e); } }`
    - [x] Journalisation de synthèse **sans donnée personnelle** : `log.debug`/`log.info` du nombre de paniers balayés (pas d'`basketId`, pas d'`userId`, pas de nom vendeur).
  - [x] JavaDoc de classe : pourquoi elle existe (D2 / lacune FR-066), pourquoi **aucun** SSE (le broadcast `basket-cancelled` n'est pas ciblé et viderait le panier des autres caissiers — même raison que la déconnexion explicite), pourquoi le `try/catch` par panier (tolérance à la course), pourquoi c'est le filet de rattrapage de l'expiration de session par inactivité.
- [x] `BasketCancellationService` : étendre la JavaDoc de classe pour mentionner le **3ᵉ** déclencheur de `cancelBasketSilently` (balayage de postes inactifs) à côté de « changement de phase » (`cancelBaskets`) et « déconnexion explicite ». Aucun changement de code fonctionnel ici (le reaper boucle sur `cancelBasketSilently(basket)` — une transaction par panier, robuste ; **ne pas** ajouter de surcharge batch).

### T5 — Correctif `validate()` pour Blind Hunter #7 (AC : 7)

- [x] `domain/pos/service/BasketCancellationService.java` : ajouter
  ```java
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void releaseLotReservationInNewTransaction(Long lotId, Long basketId) {
      lotRepository.releaseLot(lotId, basketId);
  }
  ```
  JavaDoc : **pourquoi** `REQUIRES_NEW` — l'appelant (`PosBasketService.validate()`) est `@Transactional` et va lever une `RuntimeException` qui *rollback* sa transaction ; la réservation à libérer a été committée par une transaction `addItem` antérieure ; sans transaction séparée, la libération serait annulée avec l'exception.
- [x] `domain/pos/service/PosBasketService.java` :
  - [x] Injecter `BasketCancellationService` (constructeur `@RequiredArgsConstructor` — vérifier l'absence de cycle : OK).
  - [x] Dans la boucle de pré-check de `validate()` (actuellement `PosBasketService.java:175-184`, `for (Item representative : ItemPricing.distinctByLot(items)) { ... if (... existsByLotIdAndSoldTrue(lot.getId())) throw new LotAlreadySoldException(lot.getId()); }`), **avant** le `throw` :
    `basketCancellationService.releaseLotReservationInNewTransaction(lot.getId(), basket.getId());`
    puis `throw new LotAlreadySoldException(lot.getId());`
  - [x] Commentaire inline (le *pourquoi*) : réservation committée par un `addItem` antérieur ; ce panier ne pourra plus jamais valider ce lot (le pré-check échouera indéfiniment) → sans cette libération le lot reste bloqué à toutes les caisses ; libération en transaction séparée car `validate()` va rollback.
  - [x] Ne **rien** changer aux autres chemins de sortie de `validate()` (`EmptyBasketException` l.164, `BasketValidationConflictException` l.172 et l.229, `InvalidAmountGivenException` l.190) — comportement inchangé, cf. AC7.
  - [x] **Point à valider avec Manerial avant dev** (voir « Questions ouvertes ») : libérer **le seul lot rejeté** (`releaseLot(lotId, basketId)`, ce que dit la SCP au mot près) vs libérer **toutes** les réservations du panier (`releaseAllByBasketId` — le panier est de toute façon condamné sur ce chemin). Défaut retenu : le seul lot rejeté (littéral SCP).

### T6 — Minuteur de vie côté client (AC : 8)

- [x] `src/app/services/pos.service.ts` : ajouter, à la suite des routes `baskets/{id}` :
  ```ts
  sendHeartbeat(basketId: number): Observable<void> {
    return this.http.post<void>(`/api/pos/baskets/${basketId}/heartbeat`, null);
  }
  ```
  (Style calqué sur `printInvoice` — `Observable<void>`, corps `null`, URL relative `/api/pos/...`, aucune gestion d'erreur dans le service.)
- [x] `src/app/features/volunteer/pos/pos-page.component.ts` :
  - [x] En tête de fichier : `const HEARTBEAT_INTERVAL_MS = 60_000;` avec un commentaire (« signal de vie du poste ; le serveur balaie après `pos.basket.heartbeat.dead-threshold` ≈ 3 battements manqués »).
  - [x] `import { interval } from 'rxjs';`
  - [x] Dans `ngOnInit()`, **après** l'abonnement SSE existant (l.64-66), même structure :
    ```ts
    interval(HEARTBEAT_INTERVAL_MS).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.sendHeartbeat());
    ```
  - [x] Méthode privée :
    ```ts
    private sendHeartbeat(): void {
      const currentBasket = this.basket();
      if (this.basketCancelled() || !currentBasket) {
        return;
      }
      this.posService.sendHeartbeat(currentBasket.id).subscribe({ error: () => {} });
    }
    ```
    Le double test `basketCancelled() || !currentBasket` est le patron maison (l.71, 106, 131, 165). Le `error: () => {}` est **obligatoire** : sans lui, une erreur HTTP remonterait et terminerait le flux `interval` → plus aucun heartbeat.
  - [x] Ne **pas** ajouter `implements OnDestroy` (le composant n'en a pas ; `takeUntilDestroyed` gère tout).
  - [x] Ne **pas** émettre de heartbeat immédiat au montage (`interval()` seul — pas de `startWith`/`timer(0, …)`).
  - [x] Aucun `beforeunload`, aucun `navigator.sendBeacon` (hors périmètre).

### T7 — Tests backend (AC : 2, 3, 4, 5, 6, 7)

- [x] Créer `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketReaperIT.java` :
  - [x] `extends org.pluribourse.shared.IntegrationTest`, `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`. JavaDoc de classe : **entorse documentée à la règle « E2E par les contrôleurs »** (même statut que `SaleConcurrencyIT`, acté à la création de la Story 4.4) — le setup se fait via les endpoints, mais le `last_seen_at` est antidaté en base et la méthode planifiée est **invoquée directement** (`@Autowired BasketReaperService`), le reaper étant désactivé dans le profil de test.
  - [x] `@Order(1)` — setup story-board : `POST` création d'édition → phase DEPOSIT → créer vendeur + 1 lot (≥2 membres) + articles hors lot → phase SALE → login `volunteer1` et `volunteer2` (`setUpSessions` calqué sur `PosBasketIT` / `PosBasketLogoutCancellationIT`) → `volunteer1` : `GET /pos/baskets/current` puis `POST .../items` d'un membre du lot (réservation prise) + d'un article hors lot → asserts BDD : panier `volunteer1` non vide, `lotRepository.findById(lotId).getReservedByBasketId()` == id du panier de `volunteer1`.
  - [x] `@Order(2)` — `volunteer2` ouvre son panier et y ajoute un article hors lot (panier « frais », `last_seen_at` courant).
  - [x] `@Order(3)` — antidater : charger le `Basket` de `volunteer1`, `setLastSeenAt(LocalDateTime.now().minusMinutes(10))`, `basketRepository.saveAndFlush(...)`.
  - [x] `@Order(4)` — `basketReaperService.reapInactiveBaskets()` → asserts : panier + `basket_items` de `volunteer1` supprimés (`basketRepository.findByEditionIdAndUserId(editionId, volunteer1Id).isEmpty()`), `lotRepository.findById(lotId).getReservedByBasketId() == null` ; **panier de `volunteer2` intact** (`last_seen_at` récent).
  - [x] `@Order(5)` — après le balayage, `volunteer2` fait `POST .../items` d'un membre du lot précédemment réservé → **succès** (le lot est redevenu vendable, réservation reprise pour le panier de `volunteer2`).
  - [x] `@Order(6)` — **tolérance à la course** : supprimer directement le panier de `volunteer2` (`basketRepository.deleteById(...)`), puis rappeler `reapInactiveBaskets()` avec ce panier encore dans la liste (antidaté au préalable) → **aucune exception**, le balayage se termine, les autres paniers éventuels sont traités. (Alternative si plus simple : deux paniers antidatés, l'un annulé via `POST /auth/logout` juste avant l'appel du reaper → pas d'erreur.)
  - [x] `@Order(7)` — **AC4** : documenter (commentaire) qu'une session expirée par inactivité retombe exactement sur `@Order(3-4)` (heartbeat tu → `last_seen_at` ancien → balayage) ; il n'y a pas de code d'expiration de session à tester séparément. Un `@Test` symbolique peut asserter qu'aucun bean d'écoute `SessionExpiredEvent`/`SessionDeletedEvent` lié au panier n'existe (optionnel).
- [x] `PosBasketIT.java` — ajouter en fin de classe (renuméroter la queue `@Order` si nécessaire, comme la Story 4.8 l'a fait) :
  - [x] Le heartbeat rafraîchit `last_seen_at` : `POST /pos/baskets/{id}/heartbeat` → 204, puis relire l'entité `Basket` et asserter `lastSeenAt` avancé (comparer à une valeur antidatée posée juste avant).
  - [x] Un panier « frais » n'est **pas** balayé : `basketReaperService.reapInactiveBaskets()` (autowire) juste après une action réelle → le panier existe toujours, sa réservation aussi.
  - [x] `validate()` pré-check libère le lot rejeté (**AC7**) : monter un lot dont un frère est vendu dans une vente committée, réservation détenue par le panier courant → `POST .../validate` → **409 `lot-already-sold`** **et** `lotRepository.findById(rejectedLotId).getReservedByBasketId() == null` après coup. (S'assurer que le test prouve la libération *malgré* le rollback de `validate()`.)
  - [x] Heartbeat d'un panier appartenant à un autre utilisateur → **404**.
  - [x] Heartbeat hors phase Vente → erreur de garde de phase (peut vivre dans une classe déjà orientée « phase », ex. `PosScanIT` / `PhaseGuard*IT`, si `PosBasketIT` ne franchit pas la phase Vente proprement pour ce cas).
- [x] Vérifier que `PosBasketLogoutCancellationIT` reste vert sans modification (le reaper désactivé en test n'interfère pas ; les nouveaux `touch(...)` ne changent pas les assertions existantes).
- [x] Vérifier que `PosBasketCancellationIT` (Story 2.8, annulation de panier au changement de phase) reste vert sans modification (mêmes raisons : `touch(...)` n'ajoute qu'une écriture de colonne, aucune assertion existante n'y est sensible).

### T8 — Tests frontend (AC : 8)

- [x] `src/app/services/pos.service.spec.ts` : `sendHeartbeat` émet `POST /api/pos/baskets/{id}/heartbeat`, corps `null` (`HttpTestingController`, `expectOne`, `method === 'POST'`, `req.flush(null)`) — modèle : le test `addItem` existant.
- [x] `src/app/features/volunteer/pos/pos-page.component.spec.ts` :
  - [x] Ajouter `sendHeartbeat: vi.fn()` à `posServiceMock`.
  - [x] Cas 1 — heartbeat périodique : `vi.useFakeTimers()` **après** `createComponent(BASKET_WITH_ITEM)`, `sendHeartbeat.mockReturnValue(of(undefined))`, `vi.advanceTimersByTime(60_000)` → `expect(sendHeartbeat).toHaveBeenCalledWith(<basket id>)` ; ré-avancer 60 000 → 2 appels.
  - [x] Cas 2 — arrêt à la destruction : après `fixture.destroy()`, `vi.advanceTimersByTime(120_000)` → aucun appel supplémentaire.
  - [x] Cas 3 — silencieux sur annulation : `basketCancelled$.next({...})` puis `vi.advanceTimersByTime(60_000)` → aucun heartbeat (garde `basketCancelled()` / `basket() === null`).
  - [x] Cas 4 — échec avalé : `sendHeartbeat.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })))`, avancer 2 intervalles → `toastMock.showError` **non appelé**, `sendHeartbeat` appelé **2 fois** (le flux `interval` survit à l'erreur).
  - [x] Cas 5 — pas de panier au démarrage : `getCurrentBasket` → `NEVER` (cf. test existant), avancer le temps → aucun heartbeat.
  - [x] `afterEach` remet déjà `vi.useRealTimers()`.

### T9 — Bookkeeping (pas de code)

- [x] Story 4.8 (`_bmad-output/implementation-artifacts/4-8-reservation-de-lot-au-scan-et-annulation-panier-a-la-deconnexion.md`), section `## Dev Agent Record` / `### Review Findings`, entrée **W5** : remplacer « → **Story 4.9** (SCP 2026-09-04 …) : … » par la même mention **suivie de** « — **livrée** par la Story 4.9 ». Ne **pas** rouvrir la Story 4.8 (statut `done` inchangé).
- [x] Vérifier que la note Story 2.8 dans `epics.md` (l.1028) contient bien « la variante silencieuse `cancelBasketSilently` … est également invoquée par le balayage de postes inactifs (Story 4.9) » (amendement SCP déjà commité — sinon l'ajouter).
- [x] `deferred-work.md` : marquer l'item W5 (section « code review of story 4-8 ») comme *livré par la Story 4.9* (une ligne). Le point Blind Hunter #7 y est adossé — le noter livré aussi.
- [x] `EXPERIENCE.md` : **ne pas** amender (dérive documentaire assumée — note pour une passe UX groupée future).

### T10 — Vérification finale (AC : 10)

- [x] `cd pluribourse-backend && ./mvnw clean package` → BUILD SUCCESS ; `PosBasketReaperIT` vert (méthode du reaper invoquée directement) ; `PosBasketIT`, `PosScanIT`, `PosBasketLogoutCancellationIT` verts ; `SaleConcurrencyIT` *skipped* si Docker absent (inchangé).
- [x] `cd pluribourse-frontend && npm test` → suite verte (nouveaux cas `pos-page` + `pos.service` compris) ; `npm run build` → aucun warning.
- [x] Parité i18n `fr.json` ⇔ `en.json` : **inchangée** (aucune clé ajoutée / retirée). Vérifier aussi `messages*.properties` inchangés.
- [x] Fournir à Manerial une **checklist de vérification visuelle** (l'IA ne teste pas visuellement, ne touche pas la base de dev locale) :
  - Deux navigateurs / deux comptes bénévoles, édition en phase Vente.
  - Poste A : ouvrir la caisse, scanner un membre d'un lot (réservation prise). **Fermer l'onglet A** sans se déconnecter.
  - Poste B : tenter de scanner un frère du même lot → refus `lot-reserved` immédiatement après la fermeture.
  - Attendre `dead-threshold` + `reaper.interval` (≈ 3-5 min avec les défauts). Re-tenter sur le poste B → **le scan passe** (lot libéré, panier A disparu).
  - Poste A rouvert : la caisse repart sur un **panier vide** (aucun message d'erreur, aucune régression).
  - Vérifier dans les logs applicatifs : `log.warn`/`log.info` du reaper **sans** nom, e-mail, téléphone vendeur ni identifiant utilisateur.

---

### Review Findings

> Revue de code adversariale (bmad-code-review, 2026-09-07) — 3 couches parallèles (Blind Hunter, Edge Case Hunter, Acceptance Auditor) contre le diff non commité + 4 fichiers neufs. Aucun AC cassé (AC1–AC10 satisfaits en code, décisions figées SCP §5 respectées). 4 `decision` tranchées par Manerial (D1→patch, D2→patch, D3→accepté, D4→accepté), 8 `patch` appliqués, 0 `defer`, ~20 écartés comme bruit / par conception / faux positifs.

**Décisions (tranchées par Manerial 2026-09-07)**

- [x] [Review][Decision→Patch] Aucun chemin de récupération client quand le reaper fauche le panier — le heartbeat en échec `404 basket-not-found` est avalé, `basketCancelled` n'est jamais posé, et `handleScanError`/`handleValidationError` n'avaient **pas** de branche `/basket-not-found` → toast générique + panier périmé affiché. **Décision : option b (patch).** Branche `/basket-not-found` ajoutée dans `handleScanError` **et** `handleValidationError` : `lastScanIssue` remis à `null` + `void this.loadBasket()` → rechargement silencieux sur le panier vide neuf que `getOrCreateCurrentBasket` renvoie, aucune chaîne i18n, aucun toast (conforme SCP §5 « UX du panier balayé »). [pos-page.component.ts]
- [x] [Review][Decision→Patch] Premier balayage au démarrage sans délai initial — `@Scheduled` sans `initialDelay` → 1re passe ~immédiate ; un redémarrage en phase Vente qui dépasse `dead-threshold` fauche tous les paniers ouverts avant leur prochain heartbeat. **Décision : option a (patch).** `initialDelayString = "${pos.basket.reaper.interval:PT2M}"` ajouté sur `@Scheduled` (un intervalle de grâce au démarrage) ; JavaDoc classe + méthode mises à jour ; commentaire de `PosBasketReaperIT` corrigé (avec `interval=PT1H` l'`initialDelay` vaut PT1H → le planificateur ne se déclenche plus jamais seul pendant la suite). [BasketReaperService.java]
- [x] [Review][Decision→Accepté] `validate()` ne libère que le premier lot rejeté au pré-check « frère vendu » — les lots suivants du même panier ayant aussi un frère vendu gardent `reserved_by_basket_id` jusqu'au `removeLot`/reaper/logout. **Décision : option b (accepté).** Ces lots sont `lot-already-sold`-morts partout de toute façon (fuite cosmétique : `lot-reserved` au lieu de `lot-already-sold` aux autres postes jusqu'au nettoyage) ; « le seul lot rejeté » = littéral SCP ; les vérifs au scan (`lot-already-sold` / `lot-reserved`, story 4.8) couvrent déjà le cas nominal. Idée UX « lot en cours de vente à la caisse X » = candidate story de suivi séparée (expose l'identité d'un collègue → décision UX/RGPD à part).
- [x] [Review][Decision→Accepté] Le reaper agit sur un instantané figé (`last_seen_at` pas relu entre `findStale` et le delete) — un panier réactivé pile dans la fenêtre d'itération serait quand même supprimé. **Décision : option b (accepté).** Fenêtre quasi nulle, volume v1 minuscule. Un `DELETE` en masse (proposé en alternative) est écarté : il échoue sur la FK `basket_items` (pas de `ON DELETE CASCADE`, le bulk JPQL ne déclenche pas la cascade JPA), il ne s'appuierait que sur le filet FK pour les réservations, et surtout il viole AC5 / points figés (une transaction par panier, tolérance à la course, réutilisation de `cancelBasketSilently`, « pas de surcharge batch » T4).

**Patchs (appliqués 2026-09-07)**

- [x] [Review][Patch] Log du reaper trompeur quand toutes les annulations échouent [BasketReaperService.java] — compteur `failed` ajouté ; `log.info("...{} reaped, {} failed")` dès que `reaped > 0 || failed > 0`, `log.debug("found no inactive basket")` seulement si la liste était vide. Message `log.warn` conservé (un id de panier n'est pas une donnée personnelle au sens NFR-007 ; PII = nom/e-mail/téléphone vendeur, identifiant utilisateur).
- [x] [Review][Patch] `PosBasketReaperIT` @Order(6) / @Order(7) [PosBasketReaperIT.java] — @Order(6) renommé + commentaire honnête (un test boîte noire ne peut pas forcer l'interleaving requête↔appel ; `findStale` re-requête à chaque passage donc un panier supprimé avant n'est pas dans la worklist) ; assertions renforcées : le panier survivant porte maintenant une **réservation de lot**, on asserte panier + `basket_items` supprimés **et** `reserved_by_basket_id` repassé à `null`. @Order(7) renommé + commentaire ramené à ce qu'il prouve réellement (ancre de doc pour AC4, critère « sans code »).
- [x] [Review][Patch] Assertion faible sur le heartbeat, `PosBasketIT` @Order(24) [PosBasketIT.java] — capture de `beforeHeartbeat = LocalDateTime.now()` juste avant la requête, assertion `isAfterOrEqualTo(beforeHeartbeat)` (au lieu de `isAfter(staleStamp)` où `staleStamp` est à −30 min).
- [x] [Review][Patch] Placeholders de propriétés sans valeur par défaut [BasketReaperService.java] — `${pos.basket.heartbeat.dead-threshold:PT3M}` et `${pos.basket.reaper.interval:PT2M}` (les valeurs d'`application.properties` restent prioritaires).
- [x] [Review][Patch] Trace au démarrage du reaper [BasketReaperService.java] — champ `reaperInterval` injecté + `@PostConstruct logActivation()` : `log.info("POS basket reaper active — sweeps every {} for baskets silent longer than {}", ...)`.
- [x] [Review][Patch] JavaDoc de `SchedulingConfig` corrigée [SchedulingConfig.java] — la phrase « éviterait de démarrer l'ordonnanceur dans tous les `@SpringBootTest` » remplacée : la `@Configuration` y est bien chargée ; ce qui garde les tests silencieux = `@ConditionalOnProperty` sur le reaper + absence d'autre `@Scheduled`. Le point valide (mode CLI `WebApplicationType.NONE`) est conservé.

**Vérification post-patchs (2026-09-07)** : `./mvnw clean package` → BUILD SUCCESS, **581 tests / 0 échec / 0 skip** (Docker présent → `SaleConcurrencyIT` exécuté ; `PosBasketReaperIT` 7/7, `PosBasketIT` 28/28). `npm test` → **727 passed** (+2 cas `/basket-not-found`). `npm run build` → 0 warning. Parité i18n inchangée (aucune clé ajoutée). Vérification visuelle (checklist T10) : à faire par Manerial.

**Écartés (pour mémoire, non actionnables)** : risque de deadlock `REQUIRES_NEW` dans `validate()` (aucun `@Lock` sur le chemin d'`UPDATE lots`, la tx externe ne tient aucun verrou sur la ligne) ; « réservation libérée mais item laissé dans le panier » (par conception AC7 — lot mort partout de toute façon) ; reaper non transactionnel / entités détachées / OLFE avalée (design de tolérance à la course voulu) ; `findStale` sans filtre d'état / purement temporel (décision figée ; paniers supprimés à la validation/annulation) ; clause `OR lastSeenAt IS NULL` (ne matche que les lignes pré-036, documenté) ; fake timers non restaurés (faux positif — `afterEach(() => vi.useRealTimers())` présent l.86) ; @Order(6) « réservation orpheline » (FK `ON DELETE SET NULL` 035) ; @Order(23) item 17 `sold` sans Sale (patron établi @Order(20), item non réutilisé) ; `GET /baskets/current` écrit maintenant (voulu AC2) ; `LocalDateTime.now()` sans `Clock` (convention projet, T1 interdit `@CreationTimestamp`) ; premier heartbeat à t+60s (voulu) ; pas de `@Version` sur `Basket` (T1 l'interdit ; effet bénin, client avale les réponses heartbeat) ; `NoActiveEditionException` au lieu de 404/422 (cohérent avec toutes les méthodes POS) ; `DATETIME` MariaDB tronque le sous-seconde (négligeable vs PT3M) ; `reaper-testdb` non fermée / `findStale` sans limite (volume v1) ; 2e contexte Spring de `PosBasketReaperIT` (entorse E2E déjà documentée et acceptée) ; props de test au-delà de T3 (nécessaires, documentées) ; import `java.time.*` (style du fichier).

---

## Dev Notes

### Patrons d'architecture & contraintes

- **Couches** Contrôleur → Service → Repository. DTOs à la frontière API (le heartbeat n'a **pas** de DTO — `POST` sans corps, réponse 204). MapStruct pour tout mapping entité ↔ DTO (non concerné ici). Lombok (`@RequiredArgsConstructor`, `@Slf4j`, `@Getter`/`@Setter`).
- **Types explicites, jamais `var`.** Accolades obligatoires sur tout `if` / `else` / `for` / `while`, même corps mono-ligne. JavaDoc sur la logique non triviale : `BasketReaperService` (classe + méthode planifiée), `recordHeartbeat`, `releaseLotReservationInNewTransaction`, la requête `findStale`, le correctif dans `validate()`. Pas de JavaDoc sur getters/setters.
- **Migrations Liquibase** : `NNN-nom.xml`, `author="pluribourse"`, `<rollback>` explicite obligatoire, commentaire XML riche (contexte SCP/story, choix de conception). `<include>` en dernière ligne du master. Le `003` est un trou historique — la numérotation continue est `…033, 034, 035, 036`.
- **Aucun calcul financier** dans cette story (règle projet `BigDecimal` sans objet ici).
- **Aucune donnée personnelle dans les logs** (NFR-007 / CLAUDE.md) : le reaper journalise au plus un **compteur**. Patron exact à copier : `BasketCancellingLogoutHandler` (`log.warn("Failed to cancel active POS basket on logout", e)` — message fixe, sans identité) et `SessionInvalidationService` (`log.warn("Failed to look up sessions to revoke", e)`).
- **Pas de cycle de dépendances.** `PosBasketService` dépend de `EditionService` ; `EditionService` ne peut donc pas dépendre de `PosBasketService`. `BasketCancellationService` ne dépend que de `BasketRepository` / `LotRepository` / `SseEmitterRegistry` → il est injectable dans `PosBasketService`, `BasketReaperService`, `EditionService` et le `LogoutHandler` sans risque. Injecter `BasketCancellationService` dans `PosBasketService` (T5) est sûr.
- **`spring-context` fournit `@Scheduled` / `@EnableScheduling`** (transitif via les starters) — **aucune dépendance `pom.xml` à ajouter**, ni Quartz. C'est le **premier** `@Scheduled` du projet (`grep` sur `@Scheduled|@EnableScheduling|SchedulingConfigurer` = 0 résultat aujourd'hui).

### Fichiers à modifier — état actuel / ce que la story change / ce qui doit être préservé

**`domain/pos/entity/Basket.java`** — *état actuel* : `@Entity @Table(name = "baskets")`, `@Getter @Setter @NoArgsConstructor`. Champs : `id` (`IDENTITY`), `edition` (`@ManyToOne LAZY optional=false`, `edition_id`), `user` (`@ManyToOne LAZY optional=false`, `user_id`), `items` (`@OneToMany(mappedBy="basket", cascade=ALL, orphanRemoval=true)`). **Aucun** timestamp, **aucun** `@Version`, **aucun** champ d'état (présent = actif ; supprimé = fini). Table `baskets` (migration 021) : contrainte `uk_baskets_edition_user UNIQUE(edition_id, user_id)`, FK `fk_baskets_edition ON DELETE CASCADE`, FK `fk_baskets_user` (sans cascade). `basket_items` : FK `fk_basket_items_basket ON DELETE CASCADE`. *Change* : `+ LocalDateTime lastSeenAt` (`@Column(name="last_seen_at")`, nullable). *Préserver* : `uk_baskets_edition_user` (un panier par user/édition), les cascades (`cancelBasketSilently` s'appuie sur `items` cascade ALL + orphanRemoval et sur la FK `ON DELETE CASCADE`).

**`domain/pos/repository/BasketRepository.java`** — *état actuel* : `extends JpaRepository<Basket, Long>`, deux méthodes : `findByEditionIdAndUserId(Long, Long)`, `findAllByEditionId(Long)`. Aucune requête par date. *Change* : `+ findStale(LocalDateTime threshold)` (`@Query` avec `lastSeenAt IS NULL OR lastSeenAt < :threshold` — recommandé — ou dérivée `findAllByLastSeenAtBefore`). *Préserver* : les deux méthodes existantes (utilisées par `PosBasketService`, `EditionService`, `BasketCancellingLogoutHandler`).

**`domain/pos/service/PosBasketService.java`** — *état actuel* : `@Service @RequiredArgsConstructor`. 5 méthodes publiques `@Transactional`, toutes gardées phase Vente (`PhaseGuard.requireSalePhase` directement ou via `posScanService.scan`) :
  - `getOrCreateCurrentBasket(Long userId)` — crée via `createBasket` (méthode privée, ~l.243-256, `catch DataIntegrityViolationException` pour la course de création).
  - `addItem(Long basketId, String barcode, Long userId)` — prend la réservation au **premier** membre du lot : `reserveLotForBasket(lotId, basketId)` (l.92) si `basketItemRepository.findAllByBasketIdAndItemLotId(...).isEmpty()`. `reserveLotForBasket` (privée, ~l.310-323) : `if (lotRepository.reserveLot(lotId, basketId, LocalDateTime.now()) == 0) throw new LotReservedException(lotId);` + gestion `JpaSystemException` (course snapshot-isolation MariaDB) avec re-lecture du détenteur.
  - `removeItem(...)` — libère au **dernier** membre : `lotRepository.releaseLot(lotId, basketId)` (l.119).
  - `removeLot(...)` — `lotRepository.releaseLot(lotId, basketId)` (l.138).
  - `validate(Long basketId, ValidateBasketDto dto, Long userId)` (~l.156-241) — **pré-check** frère vendu committé ~l.175-184 (`for (Item representative : ItemPricing.distinctByLot(items)) { Lot lot = representative.getLot(); if (lot != null && itemRepository.existsByLotIdAndSoldTrue(lot.getId())) throw new LotAlreadySoldException(lot.getId()); }`) ; libération de **toutes** les réservations au succès `lotRepository.releaseAllByBasketId(basket.getId())` (~l.234) juste avant `basketRepository.delete(basket)` (~l.235). Catch per-item `ObjectOptimisticLockingFailureException` / `SnapshotIsolationException` (garde `@Version` article, Story 4.4) ~l.210-227. Helper IDOR `requireOwnedBasket(Long basketId, Long userId)` (~l.258-266) — `BasketNotFoundException` si absent **ou** appartient à un autre user (jamais distingués).
  *Change* : (a) `+ recordHeartbeat(basketId, userId)` ; (b) `+` helper `touch(Basket)` appelé par `getOrCreateCurrentBasket` / `addItem` / `removeItem` / `removeLot` / `createBasket` ; (c) dans le pré-check de `validate()`, `releaseLotReservationInNewTransaction(lot.getId(), basket.getId())` **avant** le `throw` ; (d) `+` dépendance `BasketCancellationService`.
  *Préserver* : l'ordre des gardes (phase avant IDOR), `reserveLotForBasket` et sa gestion `JpaSystemException`/snapshot-isolation **intacte**, le catch per-item `@Version` article, `existsByLotIdAndSoldTrue` + ses 2 call sites, `requireOwnedBasket`, `createBasket` + son `catch DataIntegrityViolationException`, la libération `releaseAllByBasketId` sur le chemin succès de `validate()`.

**`domain/pos/service/BasketCancellationService.java`** — *état actuel* : `@Service @RequiredArgsConstructor`. Deps `BasketRepository`, `LotRepository`, `SseEmitterRegistry` (aucune vers `EditionService`/`PosBasketService` — anti-cycle). `cancelBaskets(Collection<Basket>, PhaseType phaseForEvent)` `@Transactional` : no-op si vide, `releaseAndDelete(baskets)`, puis **une** `TransactionSynchronization.afterCommit()` → `broadcast("basket-cancelled", new BasketCancelledEventDto(editionId, phaseForEvent))`. `cancelBasketSilently(Basket)` `@Transactional` : `releaseAndDelete(List.of(basket))` seul, **aucune** synchro, **aucun** broadcast ; tolère un `Basket` détaché (le `deleteAll` le merge). Privée `releaseAndDelete(Collection<Basket>)` : `baskets.forEach(b -> lotRepository.releaseAllByBasketId(b.getId()))` puis `basketRepository.deleteAll(baskets)`. *Change* : `+ releaseLotReservationInNewTransaction(Long lotId, Long basketId)` (`@Transactional(propagation = REQUIRES_NEW)`) ; JavaDoc de classe étendue (3ᵉ déclencheur `cancelBasketSilently`). *Préserver* : la distinction stricte des deux points d'entrée (avec/sans SSE), `cancelBaskets` = **seul** émetteur de `basket-cancelled`, la tolérance au `Basket` détaché, l'absence de dépendance vers `EditionService`/`PosBasketService`.

**`shared/security/handlers/BasketCancellingLogoutHandler.java`** — *état actuel* : `@Slf4j @Component @RequiredArgsConstructor implements LogoutHandler`. Deps `EditionRepository`, `BasketRepository`, `BasketCancellationService`. `logout(...)` : garde `authentication == null || !(getPrincipal() instanceof PluriBourseUserDetails)` → return ; `try { editionRepository.findFirstByPhaseIn(PhaseType.ACTIVE).ifPresent(edition -> basketRepository.findByEditionIdAndUserId(edition.getId(), principal.getUserId()).ifPresent(basketCancellationService::cancelBasketSilently)); } catch (RuntimeException e) { log.warn("Failed to cancel active POS basket on logout", e); }`. *Change* : **aucun** — c'est le **modèle exact** du `BasketReaperService` (résolution best-effort, `try/catch (RuntimeException)` + `log.warn` sans PII, délégation à `cancelBasketSilently`). *Préserver* : tout.

**`domain/pos/controller/PosBasketController.java`** — *état actuel* : `@RestController @RequestMapping("/pos/baskets") @RequiredArgsConstructor`. Pas de `@Validated` classe. 5 endpoints : `GET /current`, `POST /{basketId}/items?barcode=`, `DELETE /{basketId}/items/{itemId}`, `DELETE /{basketId}/lots/{lotId}`, `POST /{basketId}/validate` (`@Valid @RequestBody`). Helper `userId(Authentication)` (l.61-63) : `((PluriBourseUserDetails) authentication.getPrincipal()).getUserId()`. Tous renvoient `ResponseEntity.ok(...)`. *Change* : `+ POST /{basketId}/heartbeat` → `ResponseEntity<Void>` 204. *Préserver* : le contrat des 5 endpoints existants, l'absence de `@PreAuthorize` (héritage règle globale), le helper `userId`.

**`shared/security/SecurityConfig.java`** — *état actuel* : `authorizeHttpRequests` (l.62-74) : `permitAll` = `/actuator/health` + `/auth/login` **uniquement** ; `/admin/**` → `hasRole("ADMIN")` ; `anyRequest()` → authentifié **et** non-anonyme **et** rôle ≠ `ROLE_SELLER`. CSRF (`CookieCsrfTokenRepository.withHttpOnlyFalse()`, path `/`, `ignoringRequestMatchers("/auth/login")` **seul**). Logout : `logoutUrl("/auth/logout")`, `.addLogoutHandler(basketCancellingLogoutHandler)` (Story 4.8), `.invalidateHttpSession(true)`, `.deleteCookies("JSESSIONID","SESSION")`. Il n'existe **pas** de rôle « caissier » — c'est `ROLE_VOLUNTEER` (ou `ROLE_ADMIN`) via la règle non-`SELLER`. *Change* : **aucun** — `/pos/baskets/*/heartbeat` est déjà couvert par `anyRequest()` + CSRF. *Préserver* : `permitAll` limité, la règle `anyRequest()` non-`SELLER`, `ignoringRequestMatchers("/auth/login")` seul (ne **pas** exempter le heartbeat de CSRF), toute la chaîne logout.

**`domain/edition/service/EditionService.java`** — *état actuel* : `savePhaseThenSendEvent(...)` enregistre la synchro `phase-changed` **puis** appelle `basketCancellationService.cancelBaskets(basketRepository.findAllByEditionId(id), newPhase)` — l'ordre garantit `phase-changed` avant `basket-cancelled` à l'`afterCommit`. *Change* : **aucun**. Le reaper est **indépendant** de l'édition/la phase (un `Basket` n'existe qu'en phase Vente ; un changement de phase annule déjà tout via `cancelBaskets`). *Préserver* : la machine à états de phase, l'ordre d'enregistrement des synchros.

**`application.properties`** — *état actuel* (sessions, l.18-23) : `spring.session.store-type=jdbc`, `spring.session.timeout=PT1H` (**unique** source de vérité de l'inactivité — la ligne morte `server.servlet.session.timeout=2h` a déjà été supprimée par la Story 4.8), `spring.session.jdbc.initialize-schema=never`. `spring.jpa.hibernate.ddl-auto=validate` (toute nouvelle colonne **doit** être migrée Liquibase et matcher l'entité). Aucune propriété de scheduling. *Change* : `+ pos.basket.heartbeat.dead-threshold`, `+ pos.basket.reaper.interval`, `+ pos.basket.reaper.enabled`. *Préserver* : `spring.session.timeout=PT1H` intact, ne pas réintroduire `server.servlet.session.timeout`.

**`db/changelog/db.changelog-master.xml`** — *état actuel* : 34 `<include>` séquentiels, format `<include file="db/changelog/NNN-nom.xml"/>`, dernier = `035-lot-reservation.xml`. *Change* : `+ <include ... 036-basket-last-seen.xml/>` en dernière position. *Préserver* : l'ordre croissant, le format exact.

**`035-lot-reservation.xml`** (modèle pour `036`) — `<changeSet id="035-lot-reservation" author="pluribourse">` + gros commentaire + `<addColumn tableName="lots">` (2 colonnes nullables, `BIGINT` / `DATETIME`) + `<addForeignKeyConstraint ... onDelete="SET NULL"/>` + `<rollback>` (drop FK puis drop colonnes). Pour `036` : **une seule** colonne, `DATETIME`, nullable, **pas de FK ni d'index**, `<rollback>` = `<dropColumn>`. Type temporel du projet = `DATETIME` (cf. `sales.sold_at` en 021).

**`pos-page.component.ts`** — *état actuel* : composant standalone, `implements OnInit` **seulement** (pas de `OnDestroy`). Injecte `PosService`, `SseService`, `PaymentDialogService`, `CurrentEditionService`, `ToastService`, `TranslateService`, `DestroyRef` via `inject()`. Signals : `basket = signal<Basket | null>(null)` (le panier actif ; `null` = aucun / annulé), `basketCancelled = signal(false)` (drapeau **one-way**, jamais remis à `false` sans reload — garde générale « ignorer les réponses en vol obsolètes »), `lastScanIssue`, `removeInFlight`. Champs privés `scanInFlight`, `validateInFlight`. `ngOnInit()` : `void this.loadBasket()` + abonnement SSE `this.sseService.basketCancelled().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.onBasketCancelled())`. `onBasketCancelled()` : si `basket() === null` → return ; sinon `basket.set(null)`, `lastScanIssue.set(null)`, `basketCancelled.set(true)`, toast `volunteer.pos.error.phaseChanged`. Tous les autres appels HTTP sont ponctuels via `firstValueFrom(...)`. **Aucun `interval()`/`timer()` RxJS nulle part dans le frontend** — le heartbeat serait le premier. *Change* : `+ HEARTBEAT_INTERVAL_MS`, `+ import { interval }`, `+` abonnement `interval(...).pipe(takeUntilDestroyed(...))` dans `ngOnInit`, `+ private sendHeartbeat()`. *Préserver* : les gardes `basketCancelled() || !currentBasket`, `takeUntilDestroyed` comme seul mécanisme de nettoyage (pas de `OnDestroy`), `volunteer.pos.error.phaseChanged` réservée au SSE changement de phase (ne **pas** la réutiliser pour l'inactivité).

**`pos.service.ts`** — *état actuel* : `@Injectable({ providedIn: 'root' })`, `http = inject(HttpClient)`. Toutes les méthodes renvoient un `Observable<T>` **brut** (le `firstValueFrom` est côté composant), aucune gestion d'erreur dans le service, URLs relatives `/api/pos/...`. Modèle direct pour `sendHeartbeat` : `printInvoice(saleId): Observable<void>` (`http.post<void>(url, null)`). *Change* : `+ sendHeartbeat(basketId): Observable<void>`. *Préserver* : le style (Observable brut, pas de `catchError` service-side).

### Le correctif Blind Hunter #7 — pourquoi ce n'est pas une ligne triviale

`validate()` est `@Transactional`. Le pré-check `existsByLotIdAndSoldTrue` lève `LotAlreadySoldException` (une `RuntimeException`) → **rollback** de la transaction de `validate()`. La réservation à libérer a été **committée par une transaction `addItem` antérieure**. Un `lotRepository.releaseLot(...)` posé naïvement avant le `throw`, dans la transaction de `validate()`, serait **annulé avec le rollback** → le correctif ne tiendrait pas. D'où `releaseLotReservationInNewTransaction` en `@Transactional(propagation = REQUIRES_NEW)` sur un **autre bean** (`BasketCancellationService`) — l'auto-invocation d'une méthode `REQUIRES_NEW` sur `this` ne franchit pas le proxy Spring et n'ouvrirait pas de nouvelle transaction. La libération est ainsi **committée indépendamment** avant que `validate()` ne rollback.

Les autres sorties par exception de `validate()` (`EmptyBasketException`, `BasketValidationConflictException` ×2, `InvalidAmountGivenException`) laissent la réservation **en l'état** : le panier existe toujours, le caissier peut retirer l'article fautif et réessayer, et si le poste est abandonné le reaper / le logout nettoie. Le cas `LotAlreadySoldException` est différent : le pré-check échouera **indéfiniment** pour ce panier sur ce lot → le lot resterait bloqué à **toutes** les caisses. C'est le seul cas que la SCP demande de traiter.

### Reaper — points de conception

- **Transaction** : la méthode `@Scheduled` est **non transactionnelle** ; chaque `cancelBasketSilently(basket)` ouvre sa propre transaction. Le `try/catch (RuntimeException)` **par panier** garantit qu'un échec (panier déjà supprimé par une course, entité détachée récalcitrante…) n'interrompt pas le balayage. **Ne pas** englober la boucle dans une transaction unique, **ne pas** ajouter de surcharge batch à `BasketCancellationService`.
- **`last_seen_at IS NULL`** : la colonne est nullable pour légaliser l'`addColumn`. La requête `last_seen_at < :threshold` **exclut** naturellement les `NULL`. `createBasket` écrivant toujours la colonne, aucun panier créé après la migration n'aura `NULL` ; le seul cas possible est un panier antérieur à la migration (aucune donnée de prod concernée). La requête `findStale` recommandée ajoute `OR lastSeenAt IS NULL` par prudence — un tel panier périmé est de toute façon à balayer.
- **Filtre par phase / édition** : inutile. Un `Basket` n'existe qu'en phase Vente (invariant : `findByEditionIdAndUserId` renvoie vide hors Vente) et un changement de phase annule déjà tous les paniers via `cancelBaskets`. Condition **purement temporelle** (SCP §5 #5).
- **`Duration`** : `@Value("${pos.basket.heartbeat.dead-threshold}") private Duration deadThreshold;` — Spring Boot convertit `PT3M` (ISO-8601) en `java.time.Duration`. `LocalDateTime.now().minus(deadThreshold)`.
- **Homogénéité temporelle** : rester sur `LocalDateTime.now()` (comme `reserved_at` en 4.8, `Sale.setSoldAt` en `validate()`), pas d'`Instant`/`ZonedDateTime`. Tant que écriture et lecture se font dans le fuseau de la JVM, la comparaison est cohérente ; le projet ne fixe aucune convention de timezone applicative.

### Standards de test backend

- **E2E par les contrôleurs**, une classe = un scénario story-board (`@TestMethodOrder(OrderAnnotation.class)` + `@Order(N)`), données persistantes entre méthodes (pas de `@Transactional` classe), `extends org.pluribourse.shared.IntegrationTest` (`@SpringBootTest` + `@DirtiesContext(AFTER_CLASS)` + `@TestInstance(PER_CLASS)`), base **H2** remise à zéro entre classes (`spring.liquibase.drop-first=true`). Données de référence `test-data.sql` : `test_admin`, `volunteer1`, `volunteer2`.
- **`PosBasketReaperIT` est une entorse documentée** à « E2E par les contrôleurs » — au même titre que `SaleConcurrencyIT`, actée à la création de la Story 4.4. Le setup passe par les endpoints, mais `last_seen_at` est antidaté en base et `BasketReaperService.reapInactiveBaskets()` est **invoqué directement** (le planificateur est neutralisé par `pos.basket.reaper.enabled=false`). **Justifier explicitement dans la JavaDoc de classe.**
- **Ne pas asserter le SSE** en MockMvc (aucun client abonné) — état BDD + statut HTTP uniquement. L'absence de broadcast est garantie par construction (`cancelBasketSilently` n'enregistre aucune synchro).
- Couverture cible **80 %**.
- Fixtures lot (rappel Story 4.8 / `PosBasketIT`) : `SellerProfile.sellerNumber`, chaque `Item.itemNumber`, barcode `String.format("%04d%04d", sellerNumber, itemNumber)`, édition en phase `SALE`.

### Front

- Composants standalone, Signals (pas de NgRx), **jamais** de template inline (non concerné : pas de template touché), **jamais** de chaîne codée en dur — mais **aucune** clé i18n nouvelle ici.
- Tests : **Vitest** via `npm test` dans `pluribourse-frontend/` (**pas** `npx vitest run`). Faux timers = **`vi.useFakeTimers()` / `vi.advanceTimersByTime(...)`** (pas `fakeAsync`/`tick`, pas jasmine clock) — modèle : `scanner-input.component.spec.ts:127`, `settlement-list.component.spec.ts:489`. `vi.advanceTimersByTime` pilote bien un `interval()` RxJS. `afterEach` du spec `pos-page` remet déjà `vi.useRealTimers()`.
- Couverture cible **80 %**.

### Interaction avec les stories existantes

- **Story 4.6** (gestion changement de phase côté client) : le drapeau `basketCancelled()` et les gardes `scanInFlight`/`validateInFlight`/`removeInFlight` restent intacts. Après un balayage, la prochaine action du caissier renverra 404 « panier introuvable » — le composant gère déjà ce cas (re-fetch d'un panier vide, pas de toast). Le minuteur ne pinge pas tant que `basketCancelled()` est vrai.
- **Story 2.8** (annulation panier au changement de phase, serveur) : inchangée. La note `epics.md` l.1028 mentionne déjà que `cancelBasketSilently` est aussi appelé par le balayage.
- **Story 1.12** (`SessionInvalidationService`) : **non touchée**. Le reaper ne consulte ni ne modifie les sessions. `SessionInvalidationService` sert seulement de **modèle de style** (best-effort loggué sans PII).
- **Story 4.4** (garde `@Version` per-item article) : **non touchée**. `@Version` est sur `Item`, pas sur `Lot` ; le catch per-item de `validate()` reste intact.

### Questions ouvertes (à trancher avant / pendant `dev-story`)

1. **Périmètre du correctif Blind Hunter #7** : libérer **le seul lot rejeté** (`releaseLot(lotId, basketId)` — littéral SCP) ou **toutes** les réservations du panier (`releaseAllByBasketId` — le panier est condamné sur ce chemin de toute façon) ? Défaut retenu dans les tâches : le seul lot rejeté. À confirmer avec Manerial.
2. **Requête `findStale`** : `@Query` avec `OR lastSeenAt IS NULL` (recommandé, ceinture-bretelles) ou dérivée simple `findAllByLastSeenAtBefore` (suffisante puisque `createBasket` écrit toujours la colonne) ? Choix à acter et à noter dans le Dev Agent Record.
3. **Course « poste qui revient » (W2 généralisée)** : le reaper supprime le panier A pendant que le poste A, réseau revenu, envoie un `addItem(A,…)`. `addItem` → violation FK `basket_id` → `catch (DataIntegrityViolationException)` rapporte à tort `item-already-in-basket` (ou remonte un 500). Faut-il un traitement dans cette story (message « votre panier a été annulé ») ou différer explicitement comme la Story 4.8 a différé W2 ? Défaut : **différer** (même nature que W2, hors périmètre SCP), à confirmer.
4. **Observabilité** : `log.info` « N paniers balayés » à chaque passage du reaper (sans PII) — souhaité ou trop verbeux (`log.debug`) ? Défaut : `log.debug` sauf N > 0 en `log.info`.
5. **Réalignement documentaire D1 / patch P4 de la Story 4.8** (catch `SnapshotIsolationException` dans `reserveLotForBasket` vs AC-A7 / T-A8 / SCP 2026-09-03 §5) : la Story 4.8 l'a laissé « à acter à la clôture de la story » ; la SCP 2026-09-04 ne l'inscrit pas dans la 4.9. Cette story touche `PosBasketService` / la même zone — greffer le réalignement de texte ici, ou le laisser comme item séparé ? Défaut : **item séparé** (ne pas élargir le périmètre).

### Project Structure Notes

- Aucune nouvelle route front, aucun nouveau composant, aucun nouveau template, aucune dépendance (`pom.xml` / `package.json` inchangés). Nouveaux fichiers backend : `db/changelog/036-basket-last-seen.xml`, `shared/config/SchedulingConfig.java`, `domain/pos/service/BasketReaperService.java`, `src/test/java/org/pluribourse/domain/pos/PosBasketReaperIT.java`. Le reste = modifications.
- Numérotation Liquibase : `036` suit `035` (le `003` reste un trou historique).
- `@EnableScheduling` sur une `@Configuration` dédiée (`shared/config/`), **pas** sur `PluribourseApplication` (mode CLI + activation dans tous les `@SpringBootTest`).
- Nommage des propriétés : `pos.basket.*` (il n'existe pas encore de préfixe `pluribourse.*` dans `application.properties` — `printerbridge.base-url` est le précédent de propriété applicative custom ; `pos.basket.*` est cohérent avec le domaine).

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-04.md — §1 Problem statement / Root cause ; §2 Story impact + Technical impact (summary) + Artifact conflicts ; §3 Recommended approach + Sensitive points ; §4 Group A/B/C/D (P-PRD1/2, P-EP1-7, P-ARCH1/2, P-SS1, P-DB1) ; §5 Open questions — resolved ; §6 Success criteria]
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md — FR-110 (§F4 — Point de Vente), FR-109 (§F4 bis — Lots en Caisse), FR-066 (§F7 — Comptes Utilisateurs & Contrôle d'Accès), FR-090, FR-042, NFR-001, NFR-002, NFR-006, NFR-007]
- [Source: _bmad-output/planning-artifacts/epics.md — l.85 (miroir FR-110), l.93 (miroir FR-109), l.123 (miroir FR-066), l.206 (UX-DR21), l.261 / l.281 / l.307 (cartes de couverture), l.1028 (note Story 2.8), Epic 4 l.337-342]
- [Source: _bmad-output/planning-artifacts/architecture.md — § Concurrence — POS (Point de Vente), ligne « Intégrité des lots » (l.237) et « Exigence de test » ; § Notification de Changement de Phase (FR-090), « Déclencheur (bis) » (l.251) et « Déclencheur (ter) » (l.252) ; § Patrons de Communication (événements SSE) ; § Authentification & Sécurité ; § Patrons de Nommage (BDD) ; § Architecture des Données]
- [Source: _bmad-output/implementation-artifacts/4-8-reservation-de-lot-au-scan-et-annulation-panier-a-la-deconnexion.md — Découpage Partie B (`BasketCancellationService`, `cancelBasketSilently`, `BasketCancellingLogoutHandler`) ; Dev Notes « LogoutHandler & cycle de vie », « Ordre des broadcasts SSE », « @Modifying bulk update », « Sémantique affected rows » ; Review Findings D1 / D2 / W5 / Patch P1 / Patch P4 ; T-A1 (migration 035), T-A3 (LotRepository), T-B1 (BasketCancellationService), T-B3 (LogoutHandler), T-B5 (nettoyage session)]
- [Source: _bmad-output/implementation-artifacts/deferred-work.md — « code review of story 4-8 (2026-09-04) » : W1-W5, Blind Hunter #7]
- [Source: CLAUDE.md — Langue, Interaction utilisateur, Budget IA, Environnement de dev local, Architecture backend/frontend, JavaDoc, Style de code, Commentaires, Tests backend/frontend, Contraintes clés]
- [Code: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java (~l.156-241 validate, ~l.175-184 pré-check, ~l.234 releaseAll, ~l.310-323 reserveLotForBasket, ~l.258-266 requireOwnedBasket)]
- [Code: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketCancellationService.java (cancelBaskets / cancelBasketSilently / releaseAndDelete)]
- [Code: pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/BasketCancellingLogoutHandler.java]
- [Code: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/entity/Basket.java ; domain/pos/repository/BasketRepository.java ; domain/pos/controller/PosBasketController.java ; domain/item/repository/LotRepository.java (reserveLot / releaseLot / releaseAllByBasketId) ; domain/item/entity/Lot.java (reservedByBasketId / reservedAt)]
- [Code: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java ; org/pluribourse/PluribourseApplication.java ; shared/security/SessionInvalidationService.java]
- [Code: pluribourse-backend/src/main/resources/application.properties ; src/test/resources/application.properties ; db/changelog/db.changelog-master.xml ; db/changelog/035-lot-reservation.xml]
- [Code: pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts (ngOnInit l.61-67, SSE l.64-66, onBasketCancelled l.152-161) ; src/app/services/pos.service.ts (printInvoice) ; src/app/features/volunteer/pos/pos-page.component.spec.ts ; src/app/services/pos.service.spec.ts]
- [Code: pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.spec.ts:127 ; src/app/features/settlement/settlement-list.component.spec.ts:490 (patrons faux timers Vitest)]

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (bmad-dev-story)

### Debug Log References

- `PosBasketReaperIT` : premier essai en `@Order(6)` échouait (`addItem` → 409 `item-already-in-basket`) — la variable `v2FreshBasketId` n'était **pas** fraîche : le panier de `volunteer2` n'est jamais annulé dans le scénario (seul celui de `volunteer1` l'est en `@Order(4)`), donc `GET /pos/baskets/current` renvoyait son panier existant qui contenait déjà l'article. Corrigé en réutilisant `volunteer2BasketId` directement (backdate + `deleteById`) et en n'ajoutant l'article que sur un panier réellement neuf de `volunteer1` (article dédié « Article C », jamais scanné avant).
- `PosBasketReaperIT` doit réactiver `pos.basket.reaper.enabled=true` (via `@TestPropertySource`) pour que le bean `BasketReaperService` (gardé par `@ConditionalOnProperty`) existe et soit `@Autowired`-able. Cela crée un **second** contexte Spring ; il reçoit sa propre base H2 (`jdbc:h2:mem:reaper-testdb`) pour ne jamais partager le schéma/les données avec le contexte partagé sur `jdbc:h2:mem:testdb`. Intervalle porté à `PT1H` pour que le seul auto-déclenchement `@Scheduled` soit celui, inoffensif (0 panier), du démarrage du contexte.

### Completion Notes List

- **T1 — Migration 036 + entité.** `036-basket-last-seen.xml` : `addColumn baskets.last_seen_at DATETIME nullable`, `<rollback>` = `dropColumn`, aucune FK, aucun index, commentaire XML riche (SCP 2026-09-04 / FR-110 / FR-066 + justification « nullable pour l'addColumn, écrite toujours par le code »). `<include>` en dernière ligne du master après `035`. `Basket.lastSeenAt` (`@Column(name = "last_seen_at")`, `LocalDateTime`, sans `nullable = false`, cohérent `ddl-auto=validate`), affectation explicite dans le service — pas de `@PrePersist`/`@CreationTimestamp`.
- **T2 — Heartbeat serveur.** `PosBasketService.recordHeartbeat(basketId, userId)` `@Transactional` : `PhaseGuard.requireSalePhase` → `requireOwnedBasket` (404 IDOR-safe) → `touch(basket)`. Helper privé `touch(Basket)` (`setLastSeenAt(now())`) appelé par `getOrCreateCurrentBasket`, `addItem`, `removeItem`, `removeLot` et `createBasket`. `PosBasketController.heartbeat` → `POST /{basketId}/heartbeat` → `ResponseEntity<Void>` 204, helper `userId(Authentication)` réutilisé. **Aucun** changement `SecurityConfig` (règle globale non-`SELLER` + CSRF standard).
- **T3 — Planification.** `shared/config/SchedulingConfig` `@Configuration @EnableScheduling` dédié (Javadoc : 1er `@Scheduled` du projet, pas sur `PluribourseApplication` à cause du mode CLI + activation dans tous les `@SpringBootTest`). `application.properties` : `pos.basket.heartbeat.dead-threshold=PT3M`, `pos.basket.reaper.interval=PT2M`, `pos.basket.reaper.enabled=true` + commentaire de relation des durées. `src/test/resources/application.properties` : `pos.basket.reaper.enabled=false` (+ les 2 durées, pour que les placeholders `@Value`/`@Scheduled` résolvent toujours).
- **T4 — Reaper.** `BasketRepository.findStale(threshold)` : `@Query` **avec `OR b.lastSeenAt IS NULL`** (choix Q2 = ceinture-bretelles ; coût nul, un panier `last_seen_at NULL` est de toute façon à balayer). `BasketReaperService` `@Service @Slf4j @ConditionalOnProperty(name = "pos.basket.reaper.enabled", matchIfMissing = true)`, deps `BasketRepository` + `BasketCancellationService` (aucun cycle), `@Value` `Duration deadThreshold`, `@Scheduled(fixedDelayString = "${pos.basket.reaper.interval}")` **non transactionnelle** : `findStale` → boucle `try { cancelBasketSilently(basket) } catch (RuntimeException) { log.warn(...) }` par panier, log de synthèse `log.info` si N>0 sinon `log.debug` (choix Q4). Javadoc de classe : pourquoi elle existe (D2 / FR-066), pourquoi **aucun SSE**, pourquoi le try/catch par panier, filet de l'expiration de session. Javadoc de classe `BasketCancellationService` étendue (3ᵉ déclencheur de `cancelBasketSilently`).
- **T5 — Correctif Blind Hunter #7.** `BasketCancellationService.releaseLotReservationInNewTransaction(lotId, basketId)` `@Transactional(propagation = REQUIRES_NEW)` → `lotRepository.releaseLot(lotId, basketId)` ; Javadoc = le *pourquoi* (`validate()` `@Transactional` va rollback, réservation committée par un `addItem` antérieur, autre bean pour franchir le proxy). `PosBasketService.validate()` : injection `BasketCancellationService` ; dans la boucle de pré-check `existsByLotIdAndSoldTrue`, appel `releaseLotReservationInNewTransaction(lot.getId(), basket.getId())` **avant** le `throw new LotAlreadySoldException(...)` + commentaire inline. **Périmètre Q1 = le seul lot rejeté** (littéral SCP). Autres chemins d'échec de `validate()` inchangés.
- **T6 — Minuteur front.** `pos.service.ts` : `sendHeartbeat(basketId): Observable<void>` (`http.post<void>(url, null)`, style `printInvoice`). `pos-page.component.ts` : `const HEARTBEAT_INTERVAL_MS = 60_000` + commentaire, `import { interval }`, abonnement `interval(HEARTBEAT_INTERVAL_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.sendHeartbeat())` **après** l'abonnement SSE dans `ngOnInit`, `private sendHeartbeat()` avec garde `basketCancelled() || !currentBasket` et `subscribe({ error: () => {} })` (obligatoire, sinon une erreur HTTP tue le flux `interval`). Pas de `OnDestroy`, pas de tick immédiat, pas de `beforeunload`/`sendBeacon`.
- **T7 — Tests backend.** `PosBasketReaperIT` NEW (7 `@Order`, entorse E2E documentée façon `SaleConcurrencyIT` : setup par endpoints, `last_seen_at` antidaté en base, `reapInactiveBaskets()` invoqué directement). `PosBasketIT` : 6ᵉ lot fixture « Lot Blind Hunter » (#16/#17) en `@Order(2)` ; `@Order(23)` AC7 (validate() rejeté au pré-check → réservation libérée malgré le rollback) ; `@Order(24)` heartbeat 204 + `last_seen_at` avancé + 404 pour un autre utilisateur ; `@Order(26)` + assertion heartbeat interdit au rôle `SELLER` (renommée `..._on_all_basket_endpoints`) ; `@Order(28)` + assertion heartbeat hors phase Vente → 422 `sale-phase-required`. `PosBasketLogoutCancellationIT` et `PosBasketCancellationIT` verts **sans modification**.
- **T8 — Tests frontend.** `pos.service.spec.ts` : `sendHeartbeat` émet `POST .../heartbeat` corps `null`. `pos-page.component.spec.ts` : `sendHeartbeat: vi.fn()` ajouté au mock + 5 cas (`vi.useFakeTimers()` **avant** `createComponent` pour que le `setInterval` de `interval()` soit faux) : tick périodique (1 puis 2 appels avec l'id du panier), arrêt à `fixture.destroy()`, silence après `basketCancelled`, échec 404 avalé (2 appels, pas de toast), pas de heartbeat tant que le panier initial n'a pas résolu (`NEVER`).
- **T9 — Bookkeeping.** Story 4.8 §Review Findings W5 → « — **livrée** par la Story 4.9 » (+ mention du correctif Blind Hunter #7). `deferred-work.md` : item D2/W5 barré « LIVRÉ par la Story 4.9 » + résumé des artefacts livrés. `epics.md` l.1028 (note Story 2.8) : déjà amendée dans le baseline `e0d356a` — vérifiée, rien à ajouter. `EXPERIENCE.md` volontairement non amendé (dérive documentaire assumée).
- **T10 — Vérifications.** `./mvnw clean package` : **BUILD SUCCESS**, jar produit, **581 tests / 0 échec / 0 erreur** (baseline 4.8 = 571 + 3 skip ; ici Docker présent → `SaleConcurrencyIT` exécuté). Frontend `npm test` : **725 tests / 725 verts** (67 fichiers ; baseline 719 + 6). `npm run build` : OK, **aucun warning** (chunk `pos-page-component` séparé). **Aucune** clé i18n `fr.json`/`en.json` ajoutée ni retirée ; `messages*.properties` inchangés. Base de dev locale non touchée.
- **Q3 (course « poste qui revient ») — différée** (décision Manerial). Précision : le scénario nominal (le poste A revient après suppression du panier par le reaper, session encore valide) est un **404 `basket-not-found`** propre déjà géré par le front Story 4.6 (`requireOwnedBasket` est la 1ʳᵉ ligne d'`addItem`). Le seul résidu est une micro-course de quelques millisecondes (suppression entre `requireOwnedBasket` et `saveAndFlush`) → 409 au libellé `item-already-in-basket` imparfait, **jamais un 500** (le `catch DataIntegrityViolationException` est là). Si la session a aussi expiré : redirection login. Consigné dans `deferred-work.md` (item W2 pré-existant, même nature).
- **Q5 (réalignement doc D1/P4 de la Story 4.8) — non traité ici** (item séparé, ne pas élargir le périmètre 4.9).
- **Vérification visuelle** : à faire par Manerial (checklist en T10 de la story — 2 navigateurs / 2 comptes, phase Vente, fermeture d'onglet, attente `dead-threshold` + `reaper.interval`, re-scan sur le poste B qui passe, panier vide au retour du poste A, logs reaper sans donnée perso).

### File List

**Backend — nouveaux**
- `pluribourse-backend/src/main/resources/db/changelog/036-basket-last-seen.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/config/SchedulingConfig.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketReaperService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketReaperIT.java`

**Backend — modifiés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/entity/Basket.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/repository/BasketRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketCancellationService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosBasketController.java`
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/resources/application.properties`
- `pluribourse-backend/src/test/resources/application.properties`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java`

**Frontend — modifiés**
- `pluribourse-frontend/src/app/services/pos.service.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts`
- `pluribourse-frontend/src/app/services/pos.service.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts`

**Artefacts — modifiés (bookkeeping T9)**
- `_bmad-output/implementation-artifacts/4-8-reservation-de-lot-au-scan-et-annulation-panier-a-la-deconnexion.md`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

### Change Log

| Date | Version | Description | Auteur |
|---|---|---|---|
| 2026-09-07 | 0.1 | Implémentation complète de la Story 4.9 (T1–T10). Détection de poste de caisse inactif : migration `036` (`baskets.last_seen_at`), endpoint `POST /api/pos/baskets/{id}/heartbeat` (204), `touch()` sur toute action POS, `BasketReaperService` `@Scheduled` (réutilise `cancelBasketSilently`, aucun SSE), minuteur `interval(60 s)` côté `pos-page`, correctif Blind Hunter #7 dans `validate()` (`releaseLotReservationInNewTransaction`, `REQUIRES_NEW`). Backend 581 tests verts, frontend 725 verts, build OK. Aucune clé i18n, route, composant ni dépendance nouvelle. Statut → review. | claude-sonnet-5 |
