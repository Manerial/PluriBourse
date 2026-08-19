---
baseline_commit: 66edc5f6e5570ce935c134710c80ca250aecaaab
---

# Story 3.1: Gestion des profils vendeurs

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole,
I want rechercher des vendeurs existants et enregistrer de nouveaux profils vendeurs,
so that les vendeurs puissent être associés à leurs articles sans ressaisir leurs informations à chaque édition.

## Acceptance Criteria

1. Sur `/volunteer/deposit`, le champ de recherche vendeur reçoit le focus automatiquement au chargement (UX-DR15).
2. La saisie d'un nom ou d'un e-mail déclenche une recherche en temps réel (à chaque caractère) parmi les vendeurs de l'édition active.
3. Si aucun résultat ne correspond, un bouton « Créer un nouveau profil » est affiché.
4. La création d'un profil (nom, prénom, e-mail, téléphone) le rend immédiatement sélectionnable pour l'enregistrement d'articles (FR-019, FR-020).
5. Un e-mail au format invalide retourne un 422 RFC 7807 avec un message d'erreur sur le champ e-mail.
6. Un champ obligatoire vide (nom, prénom, e-mail ou téléphone) retourne un 422 RFC 7807 identifiant le champ manquant (FR-019).
7. L'admin peut supprimer un vendeur uniquement en phase Dépôt et uniquement s'il n'a aucun article enregistré dans cette édition (FR-021) ; la suppression efface définitivement le profil vendeur, sans jamais supprimer d'articles en cascade — voir amendement 2026-08-19 ci-dessous. Hors phase Dépôt, ou avec des articles encore enregistrés, la suppression est refusée explicitement.
8. Aucune donnée personnelle (nom, e-mail, téléphone) n'apparaît dans les logs applicatifs.

## Tasks / Subtasks

- [x] Backend — module `seller` (AC: 1-8)
  - [x] Migration Liquibase `012-seller-profiles.xml` : table `seller_profiles` (id, edition_id FK `deleteCascade`, first_name, last_name, email, phone), incluse dans `db.changelog-master.xml`
  - [x] Entité `SellerProfile` (`org.pluribourse.seller.entity`) : `edition` (ManyToOne LAZY), firstName, lastName, email, phone
  - [x] `EditionService.requireActiveEdition()` : nouvelle méthode publique retournant l'édition active (`repository.findFirstByPhaseIn(PhaseType.ACTIVE)`) ou levant `NoActiveEditionException` (nouvelle exception dans `org.pluribourse.edition.exception`, type `no-active-edition`) — réutilisable par les futurs modules `item`/`pos`
  - [x] `SellerRepository` : `findAllByEditionId`, requête de recherche case-insensitive sur firstName/lastName/email (AC 2)
  - [x] `SellerDto` (record) avec validation : `@NotBlank` firstName/lastName/phone, `@NotBlank @Email` email
  - [x] `SellerMapper` (MapStruct) : toDto/toEntity (id ignoré à la création, edition assignée en service)
  - [x] `SellerService` : `search(query)`, `create(dto)` (résolvent l'édition active via `requireActiveEdition()`/`getActiveEdition()`), `delete(id)` (vérifie phase == DEPOSIT sinon `SellerDeletionNotAllowedException` 422 `seller-deletion-locked`)
  - [x] `SellerController` (`/api/sellers`, accessible ADMIN + VOLUNTEER, pas de `@PreAuthorize` — cohérent avec `CurrentEditionController`) : `GET /search?query=`, `POST /`
  - [x] `AdminSellerController` (`/api/admin/sellers`, `@PreAuthorize("hasRole('ADMIN')")`) : `GET /` (liste paginée/filtrable via JPageFlow, AC 7 support), `DELETE /{id}` (AC 7)
  - [x] Exceptions : `SellerNotFoundException` (404), `SellerDeletionNotAllowedException` (422)
- [x] Backend — dépendance JPageFlow (AC 7, `/admin/sellers`)
  - [x] Ajouter le repository JitPack + la dépendance dans `pom.xml` (voir Dev Notes)
  - [x] `AdminSellerController.getSellers(FilterDto filterDto)` → `SellerService` charge tous les vendeurs de l'édition demandée puis délègue à `FilterService.filterData(...)`
  - [x] Vérifier que le build Maven résout `spring-data-commons` sans conflit avec le BOM Spring Boot 4.0.6 (risque documenté ARCH-005/architecture.md)
- [x] Frontend — recherche/création vendeur (AC 1-4)
  - [x] `models/seller.model.ts` : `SellerDto { id, firstName, lastName, email, phone }`
  - [x] `services/seller.service.ts` : `search()`, `create()`, `getSellers()` (admin, paginé), `delete()` (admin)
  - [x] `features/volunteer/deposit/seller-search.component.ts` (+ `.html` dédié) : champ de recherche autofocus, résultats temps réel, bouton « Créer un nouveau profil »
  - [x] `features/volunteer/deposit/seller-form.component.ts` (+ `.html` dédié) : formulaire de création, réutilise le pattern `user-form.component.ts`
  - [x] Enregistrer la route dans `volunteer.routes.ts` (actuellement vide) : `/volunteer/deposit`
- [x] Frontend — gestion admin des vendeurs (AC 7)
  - [x] `features/admin/sellers/seller-list.component.ts` (+ `.html`) : tableau `MatPaginator` (taille 50, UX-DR11), colonnes Vendeur/Téléphone/Email/Actions (pas de colonne Articles/Statut — dépendent des Stories 3.2 et Epic 5, hors périmètre)
  - [x] Bouton suppression avec `ConfirmDialogService` (irréversible, mention RGPD) — pattern `user-list.component.ts::confirmDelete`
  - [x] Route `/admin/sellers` dans `admin.routes.ts` + item de nav « Vendeurs » (icône `group`) dans la section `nav.sections.activeEdition` de `app-layout.component.html`
- [x] i18n : clés `fr.json`/`en.json` sous `volunteer.deposit.*` et `admin.sellers.*` + `nav.admin.sellers`
- [x] Tests backend : `SellerManagementIT` (E2E via contrôleurs, `@Order`, pattern `EditionCategoryIT`)
- [x] Tests frontend : specs Vitest pour les nouveaux composants/services (couverture ≥ 80 %)

### Review Findings

- [x] [Review][Patch] **(résolu : rejeter en 422)** Pas de protection contre les doublons de vendeurs — Décision utilisateur du 2026-07-02 : `SellerService.create` vérifie désormais l'existence d'un vendeur avec le même e-mail dans l'édition active via `SellerRepository.existsByEditionIdAndEmailIgnoreCase` et rejette la création avec `SellerEmailAlreadyExistsException` (422 RFC 7807, type `seller-email-already-exists`). Testé par `SellerManagementIT#create_with_email_already_used_in_active_edition_returns_422`. [SellerService.java, SellerEmailAlreadyExistsException.java]
- [x] [Review][Patch] Recherche vendeur vulnérable à l'énumération via wildcards SQL non échappés — Corrigé : `SellerService.search` échappe désormais `%`/`_`/`\` avant liaison JPQL (`ESCAPE '\\'` ajouté à la requête) et plafonne les résultats à 50. Testé par `SellerManagementIT#search_treats_percent_and_underscore_as_literal_characters_not_sql_wildcards`. [SellerRepository.java, SellerService.java]
- [x] [Review][Patch] Changement de contrat `GET /api/editions/current` (204→404) non répercuté côté frontend — Corrigé : `current-edition.service.ts` traite désormais un 404 (`no-active-edition`) comme "aucune édition active" et remet le signal `currentEdition` à `null` ; les autres erreurs (5xx/réseau) restent silencieusement absorbées comme avant (comportement accepté en Story 2.6). Spec `current-edition.service.spec.ts` mis à jour (204→404). [current-edition.service.ts, current-edition.service.spec.ts]
- [x] [Review][Patch] Test négatif manquant : rôle SELLER contre `/api/sellers` — Corrigé : ajout d'un compte fixture `seller1` (rôle SELLER) dans `test-data.sql`, et de deux tests (`seller_role_cannot_search_sellers`, `seller_role_cannot_create_seller`) vérifiant un 403. [test-data.sql, SellerManagementIT.java]
- [x] [Review][Patch] Formulaire de création laissé ouvert pendant qu'une nouvelle recherche peut réafficher des résultats derrière lui — Corrigé : le bloc résultats est désormais gardé par `!showCreateForm()`. [seller-search.component.html]
- [x] [Review][Patch] Suppression du dernier vendeur d'une page laisse l'admin sur une page vide — Corrigé : `confirmDelete()` recule d'une page si la ligne supprimée était la dernière de la page courante (page > 0). Testé par un nouveau cas dans `seller-list.component.spec.ts`. [seller-list.component.ts]
- [x] [Review][Patch] **(résolu : dismiss après ré-examen)** Régression de style : imports wildcard introduits dans les nouveaux fichiers et un fichier préexistant — Ré-examiné pendant l'application des patchs : les imports wildcard sont en réalité déjà le style dominant dans `pluribourse-backend` (52 fichiers sur 79, dont `EditionService.java` lui-même, référence explicitement citée par les Dev Notes de cette story). Les fichiers `EditionCategoryController`/`EditionCategoryRepository` cités par la revue comme "pattern de référence" sont l'exception, pas la norme. Revert non appliqué pour éviter d'introduire une incohérence par rapport à la convention majoritaire réelle du projet. Aucune action requise sauf si le projet adopte formellement une règle d'imports explicites.
- [x] [Review][Patch] Paramètres de requête `SellerRepository` sans `@Param`, contrairement au pattern de référence cité — Corrigé en même temps que le fix d'échappement des wildcards (`@Param("editionId")`/`@Param("query")` ajoutés). [SellerRepository.java]
- [x] [Review][Patch] Pas de trim des champs vendeur persistés — Corrigé : `SellerService.create` trim désormais `firstName`/`lastName`/`email`/`phone` avant persistance. [SellerService.java]
- [x] [Review][Defer] **(résolu par amendement 2026-08-19)** AC7 « et tous ses articles » non vérifiable/implémentable pour l'instant — aucune entité `Item` n'existe encore [SellerService.java delete()] — Story 3.2 a depuis tranché : blocage de la suppression si des articles restent enregistrés, pas de cascade. AC7 amendée en conséquence (voir ci-dessus et Dev Notes).
- [x] [Review][Defer] Aucune validation d'entrée sur le binding `FilterDto` de `/admin/sellers` (page/size négatifs, tri malformé) — première utilisation de JPageFlow dans le code [AdminSellerController.java:21] — deferred, cross-cutting JPageFlow concern
- [x] [Review][Defer] Pas de validation de format côté serveur sur `phone` (seulement `@NotBlank`/`@Size(max=30)`) [SellerDto.java] — deferred, conforme à la lettre du spec (AC6)
- [x] [Review][Defer] Gestion d'erreur générique côté client masque le message d'erreur serveur spécifique [seller-form.component.ts, seller-list.component.ts] — deferred, pattern pré-existant (`user-form.component.ts`)

## Dev Notes

### Amendement 2026-08-19 — AC7 : suppression bloquée, pas en cascade

L'AC7 telle qu'écrite à l'origine (« la suppression efface définitivement le profil et tous ses articles ») décrivait une suppression en cascade, non implémentable au moment de cette story faute d'entité `Item`. La Story 3.2 (voir `epics.md`) a depuis tranché explicitement dans l'autre sens : `SellerProfile.canBeDeleted()` combine phase Dépôt **et** absence de tout article enregistré pour ce vendeur — la suppression est **refusée** (422 `seller-deletion-locked`) tant qu'il reste au moins un article, plutôt que de les supprimer avec lui. `SellerService.delete()` refuse aussi la suppression si un solde (`Settlement`, Story 5.1) existe déjà pour ce vendeur, pour ne jamais effacer un enregistrement financier. Ce texte AC7, ainsi que FR-021 et le Gherkin de cette story dans `epics.md`, ont été alignés sur ce comportement réel lors d'un audit de la dette différée du 2026-08-19 — aucun changement de code, uniquement de documentation.

### Contexte et périmètre

- Cette story a été choisie à la place de la Story 2.7 (suivante dans `sprint-status.yaml`), car 2.7 dépend d'entités (`Item`, `Settlement`) qui n'existent pas encore — voir décision utilisateur du 2026-07-01/02. Epic 2 reste `in-progress` ; 2.7/2.8 seront traitées après les Epics 3 et 5.
- Le mockup `mock-deposit.html` montre le flux complet dépôt vendeur + articles, mais **seule la partie recherche/création vendeur relève de cette story** — le formulaire de saisie d'article (type individuel/lot) est Story 3.2/3.3.
- FR-019 : les profils vendeurs sont **propres à chaque édition** (pas de réutilisation cross-édition) — la recherche est donc scopée à l'édition active uniquement, jamais aux éditions passées.

### Backend — package `org.pluribourse.seller`

Suivre exactement la structure des modules existants (`edition`, `instanceconfig`) : `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `exception/`.

Pattern de référence le plus proche : `EditionCategoryService`/`EditionCategoryController`/`EditionCategoryMapper` (CRUD scopé à une édition, DTO unique read+write avec `id` nullable à la création, mapper avec paramètre additionnel géré côté service). Voir `pluribourse-backend/src/main/java/org/pluribourse/edition/{service,controller,mapper}/EditionCategory*.java`.

**Résolution de l'édition active** : ni `search`, ni `create` ne prennent un `editionId` en paramètre — le bénévole travaille toujours dans l'édition active (une seule possible à la fois, FR-010). Ajouter `EditionService.requireActiveEdition()` :
```java
public Edition requireActiveEdition() {
    return repository.findFirstByPhaseIn(PhaseType.ACTIVE)
            .orElseThrow(NoActiveEditionException::new);
}
```
Cette méthode sera réutilisée telle quelle par les futurs modules `item`/`pos` (Epic 3/4) — ce n'est pas une sur-ingénierie pour cette seule story.

**Sécurité** : ne pas placer `search`/`create` sous `/api/admin/**` — le bénévole doit y accéder, et `SecurityConfig` bloque `/api/admin/**` aux non-ADMIN. Utiliser `/api/sellers` (accessible à tout utilisateur authentifié non-SELLER, cf. règle `.anyRequest().access(...)` dans `SecurityConfig.java:56-67`). La suppression (AC 7, ADMIN uniquement) va sous `/api/admin/sellers/{id}`.

**Suppression RGPD** (AC 7/8) : vérifier `edition.getPhase() == PhaseType.DEPOSIT` avant suppression, sinon lever `SellerDeletionNotAllowedException` (422, type `seller-deletion-locked`, cf. `CategoriesLockedException` pour le pattern). Ne jamais logger `firstName`/`lastName`/`email`/`phone` — aucun `Logger`/`log.info(...)` n'existe actuellement dans les modules `edition`/`instanceconfig` ; ne pas en introduire ici référençant ces champs.

### Dépendance JPageFlow (AC 7 — liste admin `/admin/sellers`)

ARCH-005 mandate JPageFlow (`FilterService.filterData()`) pour tout endpoint de liste paginée/filtrable, et UX-DR11 nomme explicitement « la liste vendeurs » comme cas d'usage. **C'est la toute première utilisation de cette dépendance dans ce projet** — elle n'est pas encore dans `pom.xml`.

JPageFlow est un projet de l'utilisateur (`github.com/Manerial/JPageFlow`, licence MIT), publié via JitPack :
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependency>
    <groupId>com.github.Manerial</groupId>
    <artifactId>JPageFlow</artifactId>
    <version>1.5.0</version>
</dependency>
```

**API réelle** (package `com.jPageFlow.utils`, vérifiée sur le code source local) :
- `FilterService.filterData(List<ENTRY> list, FilterDto filterDto, Function<List<ENTRY>, List<RETURN>> callback)` → `Page<RETURN>`. **Opère en mémoire sur une liste déjà chargée** — ce n'est pas un filtrage SQL. `AdminSellerController.getSellers` ne prend pas d'`editionId` en paramètre : comme pour les endpoints bénévole, l'édition est résolue via `EditionService.requireActiveEdition()` (une seule édition active à la fois, FR-010 — pas de sélecteur d'édition sur `/admin/sellers`). Charger tous les vendeurs de cette édition (`sellerRepository.findAllByEditionId(...)`, ~100 lignes max par NFR-001) puis déléguer :
  ```java
  List<SellerProfile> all = sellerRepository.findAllByEditionId(editionService.requireActiveEdition().getId());
  return FilterService.filterData(all, filterDto, list -> list.stream().map(mapper::toDto).toList());
  ```
- `FilterDto` : `page` (0-indexé), `offset`, `size` (défaut 10 — le frontend doit explicitement envoyer `size=50` par défaut pour respecter UX-DR11), `sort` (ex. `"firstName,asc"`), `filterParams` (`Map<String,String>`, clé = nom de champ **de l'entité** `SellerProfile`, valeur = filtre `contains` insensible à la casse pour les `String`). Binder `FilterDto` en `@ModelAttribute`/paramètre de requête sur le contrôleur GET.
- Tri : le filtrage/tri opère par réflexion sur les champs de `SellerProfile` (pas du DTO). Les champs `String` (firstName, lastName, email, phone) sont comparés correctement. **Ne pas exposer de tri sur un champ `BigDecimal`** — bug connu (comparaison lexicographique du `toString()`, ARCH-005) ; sans objet ici, aucun champ `SellerProfile` n'est un `BigDecimal`.
- Note Swagger (extraite de `FilterService.FILTER_DESCRIPTION`) : ne pas exposer `filterParams` dans le body Swagger — cohérent avec ARCH-014 (Springdoc actif en profil `dev` uniquement).

Vérifier lors du premier build que la résolution Maven de `spring-data-commons:3.5.5` (déclarée par JPageFlow) ne casse rien avec le BOM Spring Boot 4.0.6 — risque déjà noté dans `architecture.md` mais jamais vérifié en pratique jusqu'à cette story.

### Frontend

- Modèle de référence pour formulaire de création : `pluribourse-frontend/src/app/features/admin/users/user-form.component.ts` (Reactive Forms, `FormBuilder.nonNullable.group`, signal `loading`/`error`).
- Modèle de référence pour liste admin + suppression confirmée : `user-list.component.ts` (`ConfirmDialogService.open(...)`, `ToastService`, mise à jour optimiste du signal après succès).
- `CurrentEditionService.currentEdition` (signal, déjà injecté dans `AppLayoutComponent`) expose l'édition active — ne pas dupliquer cette logique côté frontend pour la recherche/création vendeur : le backend résout l'édition active lui-même, le frontend n'a pas besoin de transmettre un `editionId`.
- `volunteer.routes.ts` est actuellement vide (`export const volunteerRoutes: Routes = [];`) — c'est la première route du module bénévole.
- **IA de la sidebar admin** : le mockup `mock-admin-vendors.html` place « Vendeurs » dans la section « Édition active » (pas « Gestion »). Le code actuel (`app-layout.component.html`) ne contient dans cette section que le lien « Éditions » — ajouter « Vendeurs » à côté sans déplacer « Éditions » (déplacement hors périmètre de cette story). Icône `group` (telle que spécifiée dans le mockup), même si elle duplique visuellement celle du lien « Utilisateurs » — c'est un choix déjà fait dans le design, pas à corriger ici.
- Colonnes de la page `/admin/sellers` : **seulement** Vendeur / Téléphone / Email / Actions pour cette story. Le mockup montre aussi « Articles » (nombre) et « Statut » (soldé/non soldé) — ces colonnes dépendent d'entités qui n'existent pas encore (`Item` = Story 3.2, `Settlement` = Epic 5) ; ne pas les ajouter maintenant, ni en placeholder.
- Convention i18n : les clés existantes dépassent déjà la limite « 3 niveaux max » notée dans `epics.md` (ex. `admin.users.create.title` = 4 niveaux, `admin.users.error.load` = 4 niveaux). Suivre la convention réellement en place dans `fr.json`/`en.json`, pas la règle du document de planification.

### Testing Standards

- E2E uniquement via les contrôleurs (`org.pluribourse.shared.IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`, données persistantes entre méthodes). Nouvelle classe `SellerManagementIT` dans `org.pluribourse.seller`.
- Scénario suggéré : créer une édition (phase PREPARATION, active) → login `volunteer1` → recherche vide (aucun résultat) → création profil vendeur (AC 4) → recherche le retrouve (AC 2) → email invalide → 422 (AC 5) → champ manquant → 422 (AC 6) → suppression admin refusée en PREPARATION (AC 7, 422 `seller-deletion-locked`) → avancer en DEPOSIT → suppression admin réussie (204, AC 7) → recherche ne retrouve plus le vendeur.
- Comptes de test disponibles : `test_admin` (ADMIN), `volunteer1`/`volunteer2` (VOLUNTEER), mot de passe `Admin` (voir `test-data.sql`).
- Couverture backend et frontend ≥ 80 %.

### Project Structure Notes

- Aucune variance avec la structure unifiée (`architecture.md` prévoit exactement `seller/{controller,service,repository,entity,dto,mapper}`).
- Prochain numéro de migration : `012` (dernier existant : `011-edition-categories.xml`).
- Prochain numéro de story dans `sprint-status.yaml` après celle-ci : `3-2-enregistrement-darticles-assignation-automatique-de-table`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.1 : Gestion des profils vendeurs] (lignes 971-1006)
- [Source: _bmad-output/planning-artifacts/epics.md#FR-019, FR-020, FR-021]
- [Source: _bmad-output/planning-artifacts/architecture.md#ARCH-005] (JPageFlow, bug tri BigDecimal)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR11, UX-DR15]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-admin-vendors.html]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/{service,controller,mapper}/EditionCategory*.java] (pattern CRUD scopé édition)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java] (règles d'accès `/api/admin/**` vs. reste)
- [Source local: C:\Users\JHER\IdeaProjects\JPageFlow\src\main\java\com\jPageFlow\utils\{FilterService,FilterDto,FilterSort}.java] (API réelle vérifiée)
- [Source: pluribourse-frontend/src/app/features/admin/users/user-form.component.ts, user-list.component.ts] (patterns formulaire/liste+suppression)

## Dev Agent Record

### Agent Model Used

claude-sonnet-5

### Debug Log References

- Divergence AC5/AC6 (422) vs comportement réel de `GlobalExceptionHandler.handleMethodArgumentNotValid` (400) : décision utilisateur de suivre le précédent déjà établi en Story 2.1 (`EditionManagementIT#create_edition_with_blank_name_returns_400_rfc7807`) — ne pas toucher au handler partagé, tests `SellerManagementIT` alignés sur 400.
- `EditionService`/`CurrentEditionController`/`NoActiveEditionException` ont été retravaillés en parallèle (hors périmètre de cette story, refactor du endpoint `/api/editions/current`) : la méthode ajoutée par cette story a été renommée `requireActiveEdition()` → `getActiveEdition()`, et `NoActiveEditionException` répond finalement en 404 (`no-active-edition`) plutôt qu'en 409. `CurrentEditionIT` a été mis à jour (2 tests) pour refléter ce comportement suite à validation utilisateur, afin que la suite de régression complète repasse au vert.
- JPageFlow résolu en version 1.6.0 (bump mineur sans changement d'API vs 1.5.0 indiqué dans les Dev Notes) — vérifié via `git diff 1.5.0 1.6.0` sur le dépôt source et `javap` sur le jar résolu.

### Completion Notes List

- Module backend `org.pluribourse.seller` complet (entité, repository, DTO validé, mapper MapStruct, service, deux contrôleurs) suivant le pattern `EditionCategory*`.
- `EditionService.getActiveEdition()` ajouté comme méthode publique réutilisable, remplace la résolution ad-hoc de l'édition active pour les futurs modules `item`/`pos`.
- Dépendance JPageFlow ajoutée (JitPack, 1.6.0) ; `AdminSellerController.getSellers` délègue le filtrage/tri/pagination en mémoire via `FilterService.filterData(all, filterDto, mapper::toDtos)`.
- Frontend : `SellerSearchComponent` (route `/volunteer/deposit`, autofocus différé pour éviter `ExpressionChangedAfterItHasBeenCheckedError` avec `MatFormField`, recherche temps réel via `switchMap`), `SellerFormComponent` (création inline), `SellerListComponent` admin avec `MatPaginator` (taille 50, UX-DR11) et suppression confirmée.
- i18n : clés `volunteer.deposit.*`, `admin.sellers.*`, `nav.admin.sellers` ajoutées en `fr.json`/`en.json`.
- Tests : `SellerManagementIT` (13 tests, scénario E2E complet incluant recherche, création, validations 400, pagination admin, verrou RGPD hors phase Dépôt, suppression en phase Dépôt, 404 vendeur inconnu) ; specs Vitest pour `SellerService`, `SellerFormComponent`, `SellerSearchComponent`, `SellerListComponent`.
- Suite de régression complète : 154 tests backend (0 échec), 254 tests frontend (0 échec).
- Aucune donnée personnelle (nom/e-mail/téléphone) journalisée — aucun `Logger` introduit dans le module `seller` (AC8).

### File List

- `pluribourse-backend/src/main/resources/db/changelog/012-seller-profiles.xml` _(new)_
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/repository/SellerRepository.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/dto/SellerDto.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/mapper/SellerMapper.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/controller/SellerController.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/controller/AdminSellerController.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/exception/SellerNotFoundException.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/seller/exception/SellerDeletionNotAllowedException.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/exception/NoActiveEditionException.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/CurrentEditionController.java`
- `pluribourse-backend/pom.xml`
- `pluribourse-backend/src/test/java/org/pluribourse/seller/SellerManagementIT.java` _(new)_
- `pluribourse-backend/src/test/java/org/pluribourse/edition/CurrentEditionIT.java`
- `pluribourse-frontend/src/app/models/seller.model.ts` _(new)_
- `pluribourse-frontend/src/app/services/seller.service.ts` _(new)_
- `pluribourse-frontend/src/app/services/seller.service.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.ts` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.html` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.scss` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-form.component.ts` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-form.component.html` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-form.component.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.ts` _(new)_
- `pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.html` _(new)_
- `pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.scss` _(new)_
- `pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

| Date | Change |
|------|--------|
| 2026-07-02 | Story file created (ready-for-dev) |
| 2026-07-02 | Implementation complete — status → review |
| 2026-08-19 | AC7 amendée (documentation seule) : suppression bloquée si articles restants, pas de cascade — aligne le texte sur le comportement réel livré (Story 3.2) |
