---
baseline_commit: 6c52a9a0ca6a567cbf687d3e2be16033c7e930a0
---

# Story 4.4: Sécurité de la concurrence multi-postes

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole sur n'importe quel poste caissier,
I want que le système empêche la double vente du même article,
so that deux caissiers ne puissent pas accidentellement vendre le même article à deux acheteurs différents.

## Acceptance Criteria

1. **Conflit détecté à la validation (FR-042, NFR-002).** Si deux bénévoles sur des postes séparés ont le même article dans leurs paniers et que le premier valide avec succès, la validation du second retourne un 409 avec la liste des articles en conflit. **Déjà implémenté et déjà couvert par un test E2E séquentiel** (`PosBasketService.validate` — branche `alreadySold`, testée par `PosBasketIT.a_sale_conflict_is_detected_at_validation_not_at_scan`, `@Order(20)`, depuis la Story 4.2) — aucune modification de ce comportement dans cette story.
2. **UX de conflit côté client.** Quand un conflit 409 (`basket-validation-conflict`) est reçu, une notification inline liste les articles en conflit par nom ; le bénévole les retire manuellement et revalide (pas de réessai automatique). **Déjà implémenté** (`pos-page.component.ts:145-149`, depuis la Story 4.2) — aucune modification de ce comportement dans cette story.
3. **Verrou optimiste sous concurrence réelle (NFR-002) — le cœur de cette story.** Quand une vente est en cours de validation sur un poste et qu'une écriture concurrente sur le même `Item` est détectée par le verrou optimiste (`Item.@Version`), la transaction perdante est annulée et un 409 est retourné (`BasketValidationConflictException`, branche `ObjectOptimisticLockingFailureException` de `PosBasketService.validate`, lignes 173-177) — **aucune vente partielle n'est enregistrée** (ni `Sale` orphelin, ni `Item` à moitié vendu). Ce code existe déjà (Story 4.2) mais n'a **jamais été exercé par un test** : le seul test de conflit existant (`@Order 20`) est **séquentiel** (le premier bénévole valide et son call HTTP se termine complètement avant que le second ne commence) — il exerce uniquement la branche `alreadySold` (vérification en amont), jamais la branche `ObjectOptimisticLockingFailureException` (catch au flush). Cette story ajoute la preuve manquante : un test à deux threads réellement concurrents contre une vraie base MariaDB (pas H2, dont la sémantique de verrouillage diffère — architecture.md § Concurrence POS), conformément à la Note de développement de l'epic.

## Tasks / Subtasks

- [x] Backend — dépendance Testcontainers (AC: 3)
  - [x] `pluribourse-backend/pom.xml` (UPDATE) : ajouter en scope `test`
    ```xml
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mariadb</artifactId>
        <scope>test</scope>
    </dependency>
    ```
    Pas de `<version>` explicite : `spring-boot-starter-parent` gère déjà `testcontainers.version` dans son import BOM (`spring-boot-dependencies`) — cohérent avec le reste du `pom.xml` qui laisse Spring Boot piloter les versions des dépendances qu'il gère. Vérifier au premier build que la version résolue est compatible avec Spring Boot 4.0.6 (même note de prudence que Springdoc, déjà présente dans ce fichier).
- [x] Backend — nouveau test de concurrence (AC: 3) — **nouvelle classe, ne pas ajouter à `PosBasketIT`** : scénario métier différent (infrastructure MariaDB réelle vs H2, pas de `MockMvc`/session HTTP), cohérent avec le principe une-classe-un-scénario. Nom et emplacement fixés par `architecture.md` (arborescence des tests, section Backend) : `org.pluribourse.domain.pos.SaleConcurrencyIT`.
  - [x] `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java` (NEW) :
    - **N'étend pas `org.pluribourse.shared.IntegrationTest`** (cette base est câblée pour H2 + `MockMvc` — story 4.2/4.3 uniquement). Nouvelle classe `@SpringBootTest` autonome, avec ses propres annotations :
      ```java
      @SpringBootTest
      @Testcontainers
      @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
      class SaleConcurrencyIT {

          @Container
          @ServiceConnection
          static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11");

          // ...
      }
      ```
      `mariadb:11` — même tag majeur que `.docker/docker-compose.yml` (image de production), pour que le comportement de verrouillage testé soit représentatif de l'environnement réel, pas d'une version arbitraire. `@ServiceConnection` (Spring Boot 3.1+/4.x, `spring-boot-testcontainers`) fait pointer `spring.datasource.*` vers le conteneur automatiquement — **remplace** la config H2 de `src/test/resources/application.properties` pour cette seule classe, sans y toucher (les autres classes de test continuent d'utiliser H2 inchangé). Le changelog Liquibase de test (`db.changelog-test.xml`, incluant `test-data.sql`) reste appliqué tel quel — il est déjà écrit pour tourner nativement sur MariaDB (`002-spring-session.xml` a d'ailleurs déjà un changeset `dbms="mariadb,mysql"` distinct du changeset `dbms="h2"`), donc aucune adaptation de migration n'est attendue.
    - **Garde d'environnement (Docker requis)** : ajouter en tout premier, avant toute tentative de démarrage du conteneur, un contrôle qui **skip** proprement (pas d'échec dur) si Docker n'est pas disponible sur la machine — sinon `mvn test`/`npm test`-équivalent backend casserait pour tout développeur sans Docker lancé, alors que le projet n'a **aucun pipeline CI** pour l'instant (architecture.md : « CI/CD : Aucun pour la v1 » — ce test est donc voué à tourner uniquement en local, sur les machines qui ont Docker). Concrètement :
      ```java
      @BeforeAll
      static void requireDocker() {
          Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                  "Docker indisponible — test de concurrence MariaDB ignoré (nécessite Testcontainers)");
      }
      ```
      `org.junit.jupiter.api.Assumptions` + `org.testcontainers.DockerClientFactory` — un skip JUnit (pas un échec), reporté comme tel dans le résumé Surefire. Ce test reste inclus dans le pattern Surefire existant (`**/*IT.java`) plutôt que d'exiger une configuration Maven séparée (profil/Failsafe dédié), pour rester cohérent avec l'infrastructure de build actuelle (un seul plugin Surefire, pas de séparation unit/IT) — la garde `assumeTrue` est le seul filet de sécurité contre la casse du build sans Docker.
    - **Fixtures — construites directement via les repositories (pas de `MockMvc`/HTTP)**, cohérent avec le fait que ce test contourne délibérément la couche contrôleur (voir Dev Notes § Exception à la philosophie de test E2E) :
      - Une `Edition` en phase `SALE` (`phase = PhaseType.SALE`, `commissionRate`, `documentLanguage`, `createdAt`, `startDate`, `endDate`, `archived = false` — tous `@Column(nullable = false)` sur l'entité) — `EditionService.getActiveEdition()` s'appuie sur `findFirstByPhaseIn(PhaseType.ACTIVE)`, qui inclut `SALE`.
      - Une `SellerProfile` (`edition`, `firstName`, `lastName`, `email`, `phone`, `sellerNumber`, `nextItemNumber`), une `EditionCategory` (`edition`, `name`), un `Item` **unique et non vendu** (`edition`, `sellerProfile`, `category`, `name`, `price` non nul, `incomplete = false`, `sold = false`, `tableNumber`, `itemNumber`) — c'est l'article que les deux paniers vont se disputer.
      - Deux `User` déjà existants via `UserRepository.findByUsername("volunteer1")`/`"volunteer2"` (fixture `test-data.sql`, chargée par le même changelog de test) — pas besoin de session HTTP, juste leurs IDs pour appeler `PosBasketService.validate(basketId, dto, userId)` directement.
      - Deux `Basket` (un par volontaire, `edition` + `user`), chacun avec un seul `BasketItem` pointant vers le **même** `Item` — reproduit exactement le scénario de l'AC 3 (« deux bénévoles ... ont le même article dans leurs paniers »), volontairement réduit à un seul article par panier pour isoler la course sur ce seul `Item` (pas de bruit d'un panier multi-articles).
    - **Le test à deux threads** : injecter `PosBasketService` et `PlatformTransactionManager` (`@Autowired` — autoconfiguré par `spring-boot-starter-data-jpa` en tant que `JpaTransactionManager`, déjà présent, aucune config additionnelle). **`TransactionTemplate` n'est pas un bean Spring Boot auto-enregistré** — l'instancier soi-même : `new TransactionTemplate(transactionManager)`. **Champ d'instance uniquement** (initialisation inline sur le champ, ou dans un `@BeforeEach` non statique) — **jamais dans le `@BeforeAll requireDocker()`** ci-dessus, qui reste statique (lifecycle JUnit5 par défaut) et n'a donc pas accès au champ `@Autowired transactionManager`, lui-même non statique. Un `ExecutorService` à 2 threads + un `CountDownLatch(1)` comme signal de départ commun (`latch.await()` en tout début de chaque `Runnable`, `latch.countDown()` juste avant de soumettre les deux tâches) pour démarrer les deux threads le plus simultanément possible. Chaque thread exécute :
      ```java
      transactionTemplate.execute(status ->
              posBasketService.validate(basketId, new ValidateBasketDto(PaymentMethod.CASH, null), userId));
      ```
      capturé via `Future<SaleDto>` (l'appel à `PosBasketService.validate` reste `@Transactional` avec la propagation par défaut `REQUIRED` — il rejoint simplement la transaction déjà ouverte par le `TransactionTemplate` englobant, pas de conflit de propagation). **Aucune barrière/latch supplémentaire n'est nécessaire à l'intérieur de `validate()`** (et aucune instrumentation de code de production pour les tests, cf. CLAUDE.md) : les deux appels effectuent chacun plusieurs allers-retours DB réels avant d'atteindre le flush de l'`Item` partagé (résolution de l'édition active, garde de phase, résolution du panier possédé, lecture des items du panier avec `JOIN FETCH`, vérification `alreadySold`, insertion du `Sale`) — démarrés en même temps sur le même conteneur MariaDB, les deux threads atteignent cette vérification quasi simultanément dans l'immense majorité des exécutions, ce qui suffit à produire une véritable course. Concrètement, deux issues sont possibles selon l'ordre exact d'arrivée, **toutes deux valides et acceptées par ce test** (l'objectif est l'issue observable du contrat, pas le chemin de code interne précis) :
      - les deux passent la vérification `alreadySold` (aucun n'a encore flush) → le perdant échoue au flush de l'`Item` partagé (`ObjectOptimisticLockingFailureException`, capturée par `validate()` et convertie en `BasketValidationConflictException`) — **c'est la branche que ce test vise en priorité**, jamais exercée jusqu'ici ;
      - le gagnant committe juste assez tôt pour que le perdant le voie déjà vendu à sa propre vérification `alreadySold` — même résultat observable (409, liste de conflit), déjà couvert par `PosBasketIT` mais sans incidence sur la validité de ce test.
    - **Assertions** : exactement un des deux `Future.get()` retourne normalement un `SaleDto` non nul ; l'autre lève une `ExecutionException` dont la cause est `BasketValidationConflictException` (`assertThatThrownBy(...).hasCauseInstanceOf(...)`, `getConflictingItems()` contient l'article partagé). Puis, relecture de l'`Item` en base (`itemRepository.findById(itemId)`) : `isSold()` est `true`, `getSale()` n'est pas `null` et pointe **exactement** vers le `Sale` créé par le gagnant (pas de second `Sale` orphelin — vérifier `saleRepository.count()` == 1 pour cette édition). Ceci prouve l'absence de vente partielle (dernière clause de l'AC 3).
    - JavaDoc de classe expliquant le scénario et pourquoi ce test contourne `MockMvc`/le contrôleur (cf. Dev Notes ci-dessous) — cohérent avec la pratique déjà en place pour `PosBasketIT` (JavaDoc de classe résumant le scénario).
- [x] Aucune modification de code de production attendue. Si l'implémentation du test révèle un comportement différent de celui décrit dans l'AC 3 (par exemple une vente partielle observée, ou aucune des deux transactions ne recevant de conflit), **ne pas contourner par un correctif ad hoc dans le test** : documenter précisément l'écart observé dans Dev Agent Record § Debug Log References et remonter la question avant de modifier `PosBasketService`.
  - [x] **Écart effectivement observé** (voir Debug Log References) : un correctif de `PosBasketService.validate` a été nécessaire et appliqué, avec l'accord explicite de l'utilisateur avant modification (voir Debug Log References pour le détail et la justification).

## Review Findings

Revue de code effectuée (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor). 0 decision-needed, 3 patch, 3 defer, ~9 rejetés comme bruit.

- [x] [Review][Patch] Le guard "sans Docker" n'assure pas un skip propre — `@Testcontainers` démarre le conteneur statique via son `BeforeAllCallback`, qui s'exécute **avant** la méthode `@BeforeAll requireDocker()` de la classe (ordre JUnit5 documenté) : sans Docker, `MARIADB.start()` lève avant que `Assumptions.assumeTrue(...)` ne s'exécute — la classe part en erreur, pas en skip. Contredit la Task ("skip propre, pas d'échec dur") et le Javadoc de la classe. **Appliqué** : `@Testcontainers(disabledWithoutDocker = true)` — le garde `requireDocker()`/`Assumptions`/`DockerClientFactory` manuel, devenu redondant, a été retiré. [SaleConcurrencyIT.java:65-73]
- [x] [Review][Patch] Ajouter un commentaire de garde expliquant que la méthode de test ne doit jamais être enveloppée dans `@Transactional` — les fixtures doivent être committées (via l'auto-commit de chaque `save()` individuel) pour être visibles des deux transactions concurrentes lancées dans les threads séparés. **Appliqué** : commentaire ajouté juste au-dessus de `@Test`. [SaleConcurrencyIT.java:108-111]
- [x] [Review][Patch] Durcir la détection de `SnapshotIsolationException` face à un enrobage d'exception plus profond dans une future version de Spring/Hibernate. **Appliqué avec correction en cours de route** : la première tentative (`NestedExceptionUtils.getMostSpecificCause(e)`) était en réalité un régression — `getMostSpecificCause` retourne la cause **la plus profonde** de la chaîne (le `SQLException` brut du driver MariaDB), pas la première occurrence de `SnapshotIsolationException`, donc le test recommençait à échouer (rejeu confirmé, cause identifiée via `surefire-reports`). Remplacé par un parcours manuel de la chaîne de causes (`isCausedBy(Throwable, Class)`, nouvelle méthode privée) qui cherche `SnapshotIsolationException` à n'importe quel niveau. `SaleConcurrencyIT` re-validé stable sur 2/2 exécutions après correction. [PosBasketService.java:179-188, 256-266]
- [x] [Review][Defer] La branche `JpaSystemException`/`SnapshotIsolationException` n'est exercée que comme dernière (et seule) itération de la boucle `for (Item item : items)` — le fixture du test met un seul article par panier. Le comportement pour un panier multi-articles où un item plus tôt dans la boucle a déjà réussi puis un item suivant entre en conflit n'est pas prouvé. Préexistant : la branche `ObjectOptimisticLockingFailureException` d'origine (Story 4.2) a exactement la même limite, jamais testée avec un panier mixte. [PosBasketService.java:169-186] — deferred, pre-existing
- [x] [Review][Defer] Aucun pipeline CI n'exécute ce test après merge — contrainte projet documentée et acceptée (`architecture.md` : « CI/CD : Aucun pour la v1 », roadmap : "peut être ajouté de manière incrémentale") ; en l'absence de CI, ce test ne s'exécute que sur les machines de développeurs ayant Docker lancé. [SaleConcurrencyIT.java] — deferred, pre-existing
- [x] [Review][Defer] `GlobalExceptionHandler` n'a aucun handler générique pour `JpaSystemException` — toute exception de ce type non reconnue (branche "rethrow" du nouveau catch, ou tout autre appel ailleurs dans l'app) tombe dans le comportement d'erreur par défaut de Spring Boot, pas dans le contrat RFC7807 utilisé partout ailleurs. Préexistant à ce diff (le handler n'a jamais couvert ce type), non introduit par cette story. [GlobalExceptionHandler.java] — deferred, pre-existing

**Note de transparence** : l'Acceptance Auditor a initialement signalé une absence de trace de confirmation utilisateur pour la seconde exception à la philosophie de test E2E-par-contrôleur — **faux positif** : la story contient bien cette confirmation (§ Dev Notes "Exception à la philosophie de test E2E-par-contrôleur"), simplement omise par erreur dans le résumé condensé transmis à cet agent. Rejeté sans action.

### Round 2 (re-review après application des 3 patches ci-dessus)

Revue de code effectuée à nouveau (bmad-code-review re-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor). Les 3 patches du round 1 confirmés réellement présents et corrects par l'Acceptance Auditor. 0 decision-needed, 1 patch, 2 defer, 7 rejetés comme bruit.

- [x] [Review][Patch] `executor.shutdown()` jamais atteint si `future.get()` (thread principal) est interrompu — la méthode déclare `throws InterruptedException`, donc une interruption du thread principal pendant l'attente saute l'appel `shutdown()` en fin de méthode (Acceptance Auditor). **Appliqué** : `ExecutorService` géré en `try`-with-resources (`AutoCloseable` depuis Java 19, projet en Java 21) — fermeture garantie même en cas d'interruption. [SaleConcurrencyIT.java:160-183]
- [x] [Review][Defer] Aucun test unitaire rapide dédié pour `isCausedBy`, malgré la régression réelle que cette exacte logique a déjà causée dans ce cycle de revue (Blind Hunter) — réel et de valeur (un test unitaire trivial aurait détecté l'erreur `getMostSpecificCause` en millisecondes au lieu de nécessiter un run Testcontainers de 30s), mais en tension avec la philosophie de test du projet (« pas de tests de service isolés », CLAUDE.md) : nécessiterait une décision de conception (rendre `isCausedBy` testable isolément — visibilité, extraction) plutôt qu'un correctif trivial. [PosBasketService.java:264-274] — deferred
- [x] [Review][Defer] Le test ne peut pas distinguer, en boîte noire, laquelle des deux branches `catch` (`ObjectOptimisticLockingFailureException` préexistante vs `JpaSystemException`/`SnapshotIsolationException` ajoutée par cette story) a effectivement intercepté la course (Acceptance Auditor). **Vérifié empiriquement non-applicable en l'état actuel** : les logs de `mvn test` (`Error: 1020-HY000: Record has changed since last read`) confirment à chaque exécution que c'est bien la nouvelle branche qui est exercée, pas l'ancienne — cohérent avec le Debug Log References ci-dessus. Le risque soulevé reste réel pour l'avenir (si un changement de version Hibernate/driver faisait redevenir `ObjectOptimisticLockingFailureException` le chemin emprunté, le test resterait vert sans qu'on s'en aperçoive), mais l'adresser nécessiterait de tester un détail d'implémentation interne plutôt que le contrat observable — pas souhaitable. [SaleConcurrencyIT.java] — deferred

**Rejetés comme bruit (round 2)** : risque de boucle infinie dans `isCausedBy` sur une chaîne de causes cyclique (le JDK l'empêche en pratique via `initCause()`, scénario non atteignable ici) ; `isCausedBy` matchant potentiellement une cause non liée plus profonde dans la chaîne (non réaliste : `saveAndFlush(item)` ne flush que l'état de cet item précis, pas d'opération en cascade) ; commentaire Javadoc sur l'ordre JUnit5 non vérifié par le round 2 (déjà vérifié empiriquement par `javap`/comportement réel au round 1) ; garde anti-`@Transactional` uniquement documentaire (disproportionné d'outiller ça pour une seule méthode) ; versions `pom.xml` non fixées (déjà vérifiées via `dependency:tree` au round 1) ; `disabledWithoutDocker` ne couvrant pas l'échec de démarrage du conteneur pour d'autres raisons (déjà écarté au round 1) ; `SaleConcurrencyIT` n'étendant pas `IntegrationTest`/pas de `@TestInstance(PER_CLASS)` — **faux positif** : déviation explicitement voulue et documentée dans la Task de la story elle-même (« N'étend pas `org.pluribourse.shared.IntegrationTest` »), pas un oubli.

### Round 3 (re-review après le patch `try`-with-resources)

Revue de code effectuée une troisième fois (Blind Hunter + Acceptance Auditor — Edge Case Hunter non relancé, changement trop ciblé). Le patch `try`-with-resources du round 2 confirmé réellement présent et correct par les deux agents (portée des variables, ordre d'exécution des assertions vs `close()`, absence de double-close). 0 decision-needed, 0 patch, 0 nouveau defer (enrichissement d'un defer existant, voir ci-dessous), 1 rejeté comme bruit (nitpick cosmétique sur le libellé d'un commentaire, sans action).

- **Enrichissement du defer round 1 "branche non exercée en panier multi-articles"** (Acceptance Auditor) : au-delà du simple manque de couverture déjà noté, la boucle `for (Item item : items)` de `PosBasketService.validate` continue d'appeler `saveAndFlush` sur les items suivants en réutilisant la même `Session`/`EntityManager` Hibernate après qu'un item précédent a échoué au flush — or la spec JPA (§3.4) documente ce réemploi post-échec comme non garanti (« it is not defined whether the transaction can proceed with further processing »). Risque latent réel pour un panier multi-articles en conflit partiel, mais **préexistant** (design hérité de la Story 4.2, ni introduit ni aggravé par cette story) et hors du périmètre de l'AC 3 de cette story (qui ne garantit que l'absence de vente partielle — déjà assurée quel que soit le comportement de la boucle, puisque toute la transaction est annulée dans tous les cas). Documenté ici pour visibilité ; corriger proprement impliquerait un arbitrage de conception (arrêter la boucle au premier échec — perte de précision du rapport de conflits — vs. accepter le risque Hibernate documenté) qui dépasse le périmètre de cette story.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- Le mécanisme complet de détection de conflit (409 + liste des articles, AC 1/2) est **entièrement implémenté depuis la Story 4.2** et **testé end-to-end** par `PosBasketIT.a_sale_conflict_is_detected_at_validation_not_at_scan` (`@Order(20)`) : deux paniers scannent le même article, le premier valide avec succès, le second reçoit 409 avec `conflictingItems[0].name`. Ne pas dupliquer ce test, ne pas modifier ce comportement.
- `PosBasketService.validate` (`pos/service/PosBasketService.java:130-189`) porte déjà la logique complète : vérification `alreadySold` en amont (ligne 141-147) **et** flush item-par-item avec capture de `ObjectOptimisticLockingFailureException` (lignes 169-181, JavaDoc de la méthode l'explique déjà : « flushed one item at a time ... so that every item that actually lost the optimistic-lock race is identified precisely »). Cette seconde branche est le seul morceau de comportement de cette story qui n'a **jamais** été exercé par un test — c'est le sens même de la story, pas une lacune à combler ailleurs dans le code.
- `Item.@Version` (`item/entity/Item.java:63-64`) est le verrou optimiste déjà en place (Story 4.2) — aucune entité, aucune migration à modifier.
- `BasketValidationConflictException`/`GlobalExceptionHandler.handleBasketValidationConflict` (déjà en place depuis 4.2) exposent déjà `conflictingItems` sur le corps RFC 7807 — rien à changer côté contrat d'erreur.
- Côté frontend, `pos-page.component.ts` gère déjà le 409 `basket-validation-conflict` (lignes 143-149) — **aucune tâche frontend dans cette story**.

### « Filet de sécurité BDD » de architecture.md : ligne obsolète, ne pas l'implémenter telle quelle (confirmé avec l'utilisateur)

`architecture.md` (§ Concurrence — POS) liste un « Filet de sécurité : Contrainte `UNIQUE` en BDD sur l'état vendu d'un article ». Vérifié contre le schéma réel (`020-item-sold-status.xml`, `Item.java`) : `sold` est une simple colonne `BOOLEAN` sur `items`, et la relation vente↔article est portée par la FK nullable `Item.sale_id` (un article a *au plus* un `Sale`, pas de table de jonction). Une contrainte `UNIQUE` sur une colonne booléenne n'a aucun sens applicable (elle interdirait plus d'une ligne `sold = true` dans toute la table) ; le design réel — un booléen + une FK, protégés par `Item.@Version` — a divergé de cette ligne d'architecture au fil des Stories 3.x/4.2, comme dans plusieurs cas déjà documentés sur ce projet (ex. Story 6.1/ARCH-005, Story 4.3 § règle CSS morte). **Décision confirmée avec l'utilisateur lors de la création de cette story : ne pas ajouter de contrainte `UNIQUE`** ; le verrou optimiste (`@Version`) est déjà le mécanisme réel et suffisant, conforme à la ligne « Stratégie de verrouillage » de la même section d'architecture.md, qui elle reste exacte.

### Exception à la philosophie de test E2E-par-contrôleur (confirmée avec l'utilisateur)

CLAUDE.md prescrit « E2E par les contrôleurs uniquement » avec une seule exception nommée (client d'un système externe, ex. `PrinterBridgeClient`). Le test de cette story (`SaleConcurrencyIT`) appelle `PosBasketService.validate(...)` directement depuis deux threads, en contournant `MockMvc`/le contrôleur — ce n'est **pas** littéralement l'exception déjà nommée (ce n'est pas une frontière avec un système externe). C'est une nécessité technique, pas un choix de confort : produire une interférence déterministe entre deux transactions concurrentes exige un contrôle fin des frontières de transaction (`TransactionTemplate`, deux threads réels) qu'un appel HTTP séquentiel via `MockMvc` ne permet pas d'obtenir de façon fiable — et c'est exactement la méthode prescrite par `architecture.md` (§ Concurrence POS, ligne « Exigence de test ») et par la Note de développement de l'epic (`epics.md#Story 4.4`). **Traité comme une seconde exception sanctionnée, confirmée avec l'utilisateur lors de la création de cette story**, distincte de celle de `PrinterBridgeClient` mais motivée par la même catégorie de raison (frontière technique qu'aucun autre outil du projet ne permet de franchir proprement).

### Project Structure Notes

- Backend : une seule nouvelle classe de test (`SaleConcurrencyIT`), une seule mise à jour de `pom.xml` (3 dépendances test-scope). Aucun nouveau package de production, aucune migration Liquibase, aucune entité modifiée.
- Frontend : **aucune tâche**. Le comportement 409 est déjà géré depuis la Story 4.2 (AC 1/2 de cette story documentent l'existant, ne demandent aucun changement).
- Ce test ne participera à aucun pipeline CI pour l'instant (`architecture.md` : « CI/CD : Aucun pour la v1 ») — il est voué à être exécuté manuellement en local par un développeur ayant Docker, d'où la garde `assumeTrue` (skip propre, pas d'échec) plutôt qu'un échec dur en son absence.

### Fichiers à lire avant modification

- `pluribourse-backend/pom.xml` (UPDATE — ajout de dépendances test-scope uniquement)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java` (référence — méthode `validate`, ne pas modifier sauf écart de comportement constaté, voir Tasks)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/exception/BasketValidationConflictException.java`, `entity/Basket.java`, `entity/BasketItem.java` (référence)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java` (référence — `@Version`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/edition/entity/Edition.java`, `entity/PhaseType.java`, `service/EditionService.java` (référence — construction de la fixture `Edition` en phase `SALE`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/seller/entity/SellerProfile.java`, `domain/edition/entity/EditionCategory.java` (référence — champs requis des fixtures)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java` (référence — scénario de conflit séquentiel déjà couvert, ne pas dupliquer ; style de JavaDoc de classe à réutiliser)
- `pluribourse-backend/src/test/resources/application.properties`, `src/test/resources/db/changelog/db.changelog-test.xml`, `test-data.sql` (référence — `volunteer1`/`volunteer2` réutilisés par ID, changelog déjà compatible MariaDB)
- `.docker/docker-compose.yml` (référence — tag `mariadb:11` à répliquer dans le conteneur de test)

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.4] — ACs source (FR-042) + Note de développement (Testcontainers MariaDB, deux threads `TransactionTemplate`)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#F4 — Point de Vente] — FR-042, NFR-002
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS (Point de Vente)] — stratégie de verrouillage, exigence de test Testcontainers MariaDB (ligne « filet de sécurité UNIQUE » vérifiée obsolète, voir Dev Notes)
- [Source: _bmad-output/planning-artifacts/architecture.md#Backend — Structure de Répertoires Complète] — nom de classe `SaleConcurrencyIT` (le reste de l'arborescence de tests qui y est esquissée — `BasketServiceTest`, `EditionServiceTest`, etc. — est obsolète face à la philosophie de test E2E établie depuis, non suivi)
- [Source: _bmad-output/implementation-artifacts/4-2-gestion-du-panier-validation-du-paiement.md] — introduit `PosBasketService.validate`, le verrou optimiste par item, `BasketValidationConflictException`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/**, domain/item/entity/Item.java, domain/edition/**] — lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketIT.java, src/test/resources/**] — lus intégralement
- [Source: pluribourse-backend/pom.xml, .docker/docker-compose.yml] — lus intégralement

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (dev-story)

### Debug Log References

**⚠️ Écart de comportement observé sur l'AC 3 — HALT avant modification de code de production (conformément à la garde de la Task 3)**

`SaleConcurrencyIT` a été écrit et exécuté avec succès contre un vrai conteneur MariaDB (Testcontainers, `mariadb:11`, Docker disponible localement). Le test reproduit de façon **déterministe** (2/2 exécutions) le scénario de la course réelle décrite par l'AC 3, mais avec un résultat différent de celui attendu :

- **Attendu (AC 3)** : le perdant de la course reçoit une `BasketValidationConflictException` (409 propre, liste des articles en conflit), capturée par le `catch (ObjectOptimisticLockingFailureException e)` de `PosBasketService.validate` (`PosBasketService.java:174-177`).
- **Observé** : le perdant lève une `org.springframework.orm.jpa.JpaSystemException` **non catchée**, qui se propage hors de la transaction. Chaîne de causes complète (capturée via un `printStackTrace()` temporaire, retiré du test final) :
  1. `org.mariadb.jdbc.SQLException`: *"(conn=5) Record has changed since last read in table 'items'; try restarting transaction"* — erreur native MariaDB **1020** (`ER_CHECKREAD`), levée directement par le connecteur JDBC sur l'`UPDATE ... WHERE id=? AND version=?` de `itemRepository.saveAndFlush(item)`.
  2. Hibernate 7.2 traduit explicitement cette erreur 1020 via `org.hibernate.dialect.MariaDBDialect.buildSQLExceptionConversionDelegate` en `org.hibernate.exception.SnapshotIsolationException` — **pas** en `StaleObjectStateException`/`OptimisticLockException` (le mapping dialect-spécifique confirme que Hibernate traite ce cas MariaDB comme une famille d'exception distincte du verrou optimiste classique).
  3. N'étant pas reconnue comme exception de verrou optimiste, Spring (`HibernateExceptionTranslator`) la traduit en `JpaSystemException` générique — pas en `ObjectOptimisticLockingFailureException`.
  4. Le `catch (ObjectOptimisticLockingFailureException e)` de `PosBasketService.validate` (ligne 175) ne matche donc jamais cette exception : elle remonte non gérée, annule la transaction (aucune vente partielle — la partie "sécurité des données" de l'AC 3 tient bon), mais **sans** le 409 propre attendu côté client (le comportement réel dépendrait du gestionnaire d'exception générique, non conçu pour ce cas).

Root cause écarté : ce n'est **pas** un artefact du pilote JDBC en mode "bulk statements" — reproduit à l'identique avec `useBulkStmts=false` explicitement désactivé sur l'URL Testcontainers. C'est bien MariaDB/InnoDB qui détecte le conflit d'écriture concurrente *avant* que le mécanisme de comptage de lignes affectées (0 ligne → `StaleStateException`) sur lequel `PosBasketService` s'appuie n'ait l'occasion de s'exécuter — un comportement propre à MariaDB, absent sous H2 (ce qui explique que ni `PosBasketIT` ni aucun test H2 existant n'ait jamais pu détecter cet écart).

**Conformément à la consigne explicite de la story ("ne pas contourner par un correctif ad hoc dans le test ... remonter la question avant de modifier PosBasketService") et à CLAUDE.md, aucune modification de `PosBasketService` n'a été apportée avant d'avoir soumis cet écart à l'utilisateur.**

**Résolution (accord utilisateur) :** l'utilisateur a choisi de corriger `PosBasketService.validate` dans le périmètre de cette story plutôt que de reporter le correctif à une story séparée. Correctif appliqué : le bloc `try/catch` autour de `itemRepository.saveAndFlush(item)` (lignes ~173-186) capture désormais, en plus de `ObjectOptimisticLockingFailureException`, un `JpaSystemException` dont la cause est une `org.hibernate.exception.SnapshotIsolationException` — traité comme le même conflit de vente concurrente (ajout à `conflicts`, même `ConflictingItemDto`). Tout autre `JpaSystemException` (cause différente) est re-levé tel quel — pas de sur-capture générique. `SaleConcurrencyIT` passe désormais de façon stable (2/2 exécutions consécutives contre MariaDB réel), et la suite complète (365/365, H2) ne montre aucune régression.

### Completion Notes List

- Dépendances Testcontainers ajoutées à `pom.xml` (artifactIds Testcontainers 2.x corrigés après vérification `dependency:tree` — voir File List).
- `SaleConcurrencyIT` écrit et validé contre un vrai conteneur MariaDB (Testcontainers, `mariadb:11`) : deux threads `TransactionTemplate` concurrents sur `PosBasketService.validate`, exactement un gagnant, l'autre reçoit `BasketValidationConflictException` avec l'article en conflit ; relecture BDD confirme `Item.sold=true`, `Item.sale` pointant vers l'unique `Sale` créé (pas de vente partielle, pas de `Sale` orphelin).
- Écart réel découvert par ce test (branche jamais exercée avant cette story) : sous MariaDB réel, l'erreur native 1020 est traduite par Hibernate en `SnapshotIsolationException`/`JpaSystemException`, pas en `ObjectOptimisticLockingFailureException` — invisible sous H2, donc jamais détectée par les tests existants. Documenté et soumis à l'utilisateur avant correctif (voir ci-dessus).
- `PosBasketService.validate` corrigé (avec accord utilisateur) pour traiter aussi ce cas MariaDB-spécifique comme un conflit de vente propre (409), conformément à l'intention de l'AC 3.
- Suite complète backend : 365/365 tests, 0 échec, 0 erreur — aucune régression.
- Revue de code (bmad-code-review, Blind Hunter + Edge Case Hunter + Acceptance Auditor) : 0 decision-needed, 3 patch appliqués (skip Docker propre via `disabledWithoutDocker = true`, garde anti-`@Transactional`, détection `SnapshotIsolationException` durcie via parcours de chaîne de causes), 3 defer (préexistants, documentés dans `deferred-work.md`), ~9 rejetés comme bruit dont un faux positif transparent (voir Review Findings). Suite complète re-validée après patches : 365/365 backend, `SaleConcurrencyIT` stable sur 2/2 exécutions supplémentaires.
- Re-revue de code (round 2, après application des 3 patches) : les 3 patches confirmés réellement présents et corrects par l'Acceptance Auditor. 0 decision-needed, 1 patch appliqué (`ExecutorService` en try-with-resources), 2 defer, 7 rejetés comme bruit dont 1 faux positif (voir Review Findings § Round 2). Suite complète re-validée : 365/365 backend, `SaleConcurrencyIT` stable.

### File List

- `pluribourse-backend/pom.xml` (UPDATE) — ajout `spring-boot-testcontainers`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-mariadb` (scope test). **Note** : la story indiquait les artifactIds Testcontainers 1.x (`junit-jupiter`, `mariadb`) ; le BOM Spring Boot 4.0.6 gère en réalité Testcontainers **2.0.5**, qui les a renommés (`testcontainers-junit-jupiter`, `testcontainers-mariadb`) — corrigé après vérification par `dependency:tree`.
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/SaleConcurrencyIT.java` (NEW, puis patché en 2 rounds de revue) — test à deux threads `TransactionTemplate` concurrents contre MariaDB réel (Testcontainers). `@Testcontainers(disabledWithoutDocker = true)` (skip propre sans Docker), commentaire de garde anti-`@Transactional`, `ExecutorService` en try-with-resources (fermeture garantie même si `future.get()` est interrompu). Passe de façon stable.
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosBasketService.java` (UPDATE, puis patché en revue) — `validate()` : le catch autour de `itemRepository.saveAndFlush(item)` gère désormais aussi le cas MariaDB `JpaSystemException`/`SnapshotIsolationException` (écart découvert par `SaleConcurrencyIT`, correctif approuvé par l'utilisateur — voir Debug Log References), détection durcie via la nouvelle méthode privée `isCausedBy(Throwable, Class)` (parcours de la chaîne de causes) plutôt qu'un simple `getCause()`.

## Change Log

- 2026-08-13 — dev-story : implémentation complète (Testcontainers, `SaleConcurrencyIT`, correctif `PosBasketService.validate` pour le cas MariaDB `SnapshotIsolationException`). Statut → review.
- 2026-08-13 — code-review : 3 patch appliqués (skip Docker propre, garde anti-`@Transactional`, détection de cause durcie), 3 defer documentés, 1 faux positif écarté. Statut → done.
- 2026-08-13 — code-review (round 2, re-review) : 3 patches du round 1 confirmés corrects, 1 patch supplémentaire appliqué (`ExecutorService` try-with-resources), 2 defer documentés, 1 faux positif écarté. Statut → done (confirmé).
