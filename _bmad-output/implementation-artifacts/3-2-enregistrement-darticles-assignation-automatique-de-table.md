---
baseline_commit: 902319dc521ffe72fb36b44e68e04b0b0f189265
---

# Story 3.2: Enregistrement d'articles & Assignation automatique de table

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole,
I want enregistrer des articles pour un vendeur avec assignation automatique de table,
so that les articles soient correctement catalogués et localisés physiquement pendant l'événement.

## Acceptance Criteria

1. Vendeur sélectionné, article saisi dans une catégorie où ce vendeur a déjà des articles pour cette édition → la même table que ses articles existants dans cette catégorie lui est assignée (FR-023) ; le numéro de table est affiché immédiatement après la sauvegarde.
2. Vendeur sélectionné, premier article saisi dans une catégorie pour cette édition → la table ayant le moins d'articles **toutes catégories confondues** parmi celles configurées pour cette catégorie lui est assignée (FR-023) ; le numéro de table est affiché immédiatement après la sauvegarde.
3. Le formulaire de saisie d'article propose un champ commentaire optionnel, disponible indépendamment de l'état de la case complet/incomplet (FR-022).
4. Cocher « Incomplet » stocke l'indicateur d'incomplétude avec l'article.
5. En phase Dépôt, modifier le nom, le prix ou la catégorie d'un article sauvegarde la modification ; si la catégorie a changé, la table est réassignée selon l'algorithme FR-023 (même table si le vendeur est déjà présent dans la nouvelle catégorie, sinon table la moins chargée toutes catégories confondues).
6. En phase Dépôt, supprimer un article le retire de la liste du vendeur (FR-024).
7. Hors phase Dépôt, toute tentative de modification ou de suppression d'un article est bloquée avec un message explicite.
8. L'indicateur complet/incomplet et le commentaire restent modifiables dans **toutes** les phases, avec sauvegarde immédiate (FR-025). Tous les prix sont stockés en `BigDecimal` (NFR-003).
9. `SellerProfile.canBeDeleted()` remplace son `hasNoSelledArticles` figé à `false` par une vérification réelle : le vendeur peut être supprimé s'il est en phase Dépôt et qu'aucun article n'est enregistré pour lui dans cette édition (FR-021). *(Correction 2026-07-03 : le périmètre de phase a été resserré à Dépôt seul, sur demande explicite du Product Owner — voir Change Log.)*
10. `EditionService.deleteEdition()` refuse la suppression d'une édition tant qu'il reste au moins un article enregistré pour cette édition (cas d'un retour arrière Dépôt → Préparation qui préserve les données, FR-082), même si la phase autoriserait par ailleurs la suppression.

## Tasks / Subtasks

- [x] Backend — module `item` (AC: 1, 2, 3, 4, 5, 6, 7, 8)
  - [x] Migration Liquibase `013-items.xml` (voir Dev Notes pour le schéma exact) incluse dans `db.changelog-master.xml`
  - [x] Entité `Item` (`org.pluribourse.item.entity`) : `edition`, `sellerProfile`, `category` (**réutilise** `org.pluribourse.edition.entity.EditionCategory` — ne pas créer de nouvelle entité `Category`), `name`, `price` (BigDecimal), `incomplete`, `comment`, `tableNumber`, `@Version version`
  - [x] `ItemRepository` : requêtes pour (a) table déjà assignée au vendeur dans une catégorie, (b) comptage d'articles par numéro de table sur un ensemble de tables, (c) existence d'articles pour un vendeur, (d) existence d'articles pour une édition, (e) liste des articles d'un vendeur
  - [x] `TableAssignmentService` (ou méthode privée dans `ItemService`) implémentant l'algorithme FR-023 (voir Dev Notes — pseudo-code fourni)
  - [x] `ItemDto` / `CreateItemDto` (records, validation Bean Validation), `ItemMapper` (MapStruct)
  - [x] `ItemService` : `create(dto)`, `update(id, dto)` (nom/prix/catégorie, verrouillé hors Dépôt), `updateCompleteness(id, dto)` (incomplet/commentaire, toutes phases), `delete(id)` (verrouillé hors Dépôt), `getBySellerProfile(sellerProfileId)`
  - [x] `ItemController` (`/api/items`, accessible ADMIN + VOLUNTEER, cohérent avec `SellerController`) : `POST /`, `GET /?sellerProfileId=`, `PUT /{id}`, `PATCH /{id}`, `DELETE /{id}`
  - [x] Nouvel endpoint volontaire pour lire les catégories de l'édition active (**gap identifié** — voir Dev Notes : `/admin/editions/{id}/categories` est verrouillé ADMIN, le bénévole ne peut pas peupler le sélecteur de catégorie du formulaire d'article)
  - [x] Exceptions : `ItemNotFoundException` (404), `ItemModificationNotAllowedException` (422, type `item-modification-locked`)
  - [x] **Corriger** `SellerProfile.canBeDeleted()` (bug préexistant vérifié, voir Dev Notes) : accepter un paramètre `hasNoRegisteredArticles`, phase Dépôt uniquement
  - [x] Mettre à jour `SellerService.delete()` pour fournir ce paramètre via `ItemRepository`
  - [x] Mettre à jour `EditionService.deleteEdition()` pour refuser la suppression si `ItemRepository.existsByEditionId(id)` est vrai (nouvelle exception ou réutilisation d'`EditionCannotBeDeletedException`, voir Dev Notes)
- [x] Frontend — formulaire d'article (AC 1-8)
  - [x] `models/item.model.ts` : `ItemDto`, `CreateItemDto`
  - [x] `services/item.service.ts` : `create()`, `getBySeller()`, `update()`, `updateCompleteness()`, `delete()`
  - [x] `services/category.service.ts` : ajouter une méthode consommant le nouvel endpoint bénévole (voir tâche backend ci-dessus) — ne pas appeler l'endpoint admin depuis le contexte bénévole
  - [x] `features/volunteer/deposit/item-form.component.ts` (+ `.html` dédié) : nom, prix, sélecteur de catégorie, case Incomplet, commentaire optionnel ; affiche le numéro de table assigné après sauvegarde (`NotificationInlineComponent` ou équivalent)
  - [x] `features/volunteer/deposit/deposit-page.component.ts` (ou équivalent orchestrateur) : compose `seller-search` + liste des articles déposés (pattern `article-row` du mockup) + `item-form` ; édition/suppression d'article en phase Dépôt, toggle complet/incomplet + commentaire en toute phase
  - [x] Enregistrer/mettre à jour la route `/volunteer/deposit` dans `volunteer.routes.ts` si un composant orchestrateur dédié est introduit
- [x] i18n : clés `volunteer.deposit.item.*` dans `fr.json`/`en.json`
- [x] Tests backend : `ItemManagementIT` (E2E via contrôleurs, pattern `SellerManagementIT`) couvrant la création, l'assignation de table (les deux branches de l'algorithme), la modification/suppression verrouillée hors Dépôt, l'édition complet/incomplet+commentaire en toute phase, la suppression vendeur bloquée s'il reste des articles, la suppression édition bloquée s'il reste des articles
  - [x] **Corriger** les deux tests actuellement rouges dans `SellerManagementIT` (voir Dev Notes) : `admin_deletes_seller_in_deposit_phase`, `search_no_longer_finds_deleted_seller`
- [x] Tests frontend : specs Vitest pour les nouveaux composants/services (couverture ≥ 80 %)

### Review Findings (frontend pass — 2/2)

Revue adversarielle sur `git diff 902319d..d6ecc33 -- pluribourse-frontend` (commits `6161ce7`, `d6ecc33`), 3 couches. ~30 constats bruts → 15 constats dédupliqués après vérification manuelle du code.

- [x] [Review][Decision] Scope creep non documenté : sidebar rétractable admin persistée en localStorage, absente du périmètre de la Story 3.2 — **Résolu avec l'utilisateur 2026-07-04 : garder et durcir.** Voir correctifs ci-dessous.
- [x] [Review][Decision] Pas de garde de phase côté routing sur `/volunteer/deposit` (AC 7) — **Résolu avec l'utilisateur 2026-07-04 : corriger maintenant.** Voir correctif ci-dessous.
- [x] [Review][Patch] `ItemFormComponent.categoryId` initialisé à `0 as number | null` — `Validators.required` n'exclut pas `0`, un article peut être soumis sans catégorie réellement sélectionnée (confirmé indépendamment par les 3 couches de revue) → **Corrigé** : défaut `null` ; test de régression ajouté [pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts:47]
- [x] [Review][Patch] Symbole `€` codé en dur dans le template au lieu de passer par ngx-translate (violation CLAUDE.md) → **Corrigé** : clé i18n `volunteer.deposit.item.list.priceFormat` [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html:38]
- [x] [Review][Patch] AC 7 non respecté : aucun flux de mutation d'article ne détecte le type d'erreur RFC 7807 `item-modification-locked` → **Corrigé** : reprise du pattern `extractErrorType`/`HttpErrorResponse` de `phase-control.component.ts` dans `item-form.component.ts` (onSubmit) et `deposit-page.component.ts` (confirmDelete), nouvelle clé i18n `error.phaseLocked` [pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts, deposit-page.component.ts]
- [x] [Review][Patch] Pas de validation client `endDate >= startDate` sur le formulaire d'édition → **Corrigé** : nouveau `dateRangeValidator` (validateur de groupe) + test unitaire dédié [pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts, pluribourse-frontend/src/app/shared/validators/date-range.validator.ts]
- [x] [Review][Patch] Le message "Table N assignée" reste affiché après changement de vendeur si le formulaire n'était pas en édition → **Corrigé** : l'effet de réinitialisation dépend désormais aussi de `sellerId()` [pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts]
- [x] [Review][Patch] Pas de garde de route sur `/volunteer/deposit` vérifiant la phase active → **Corrigé** : nouveau `depositPhaseGuard` (canActivate) + tests unitaires [pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts, pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts]
- [x] [Review][Patch] `localStorage` non protégé par try/catch pour la préférence de sidebar et clé non namespacée par utilisateur → **Corrigé** : try/catch + clé `pluribourse.sidebarCollapsed.<username>` [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts]
- [x] [Review][Patch] `MAT_DATE_LOCALE` figé sur `'fr-FR'` indépendamment de la langue active de l'application → **Corrigé** : factory basée sur `TranslateService.getCurrentLang()` + resynchronisation sur `onLangChange` via `DateAdapter.setLocale()` [pluribourse-frontend/src/app/app.config.ts]
- [x] [Review][Defer] `fromIsoDate`/`toIsoDate` sans validation défensive (NaN, chaîne malformée) et usage de `!` non-null — risque réduit désormais que le backend garantit des dates non nulles et valides (backfill migration 014 + validation Bean Validation) [pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts] — deferred, low risk post-fix backend
- [x] [Review][Defer] `price` typé `number` (JS float) sans validateur `max`, pas de `BigDecimal`/représentation décimale sûre côté frontend — aucun calcul arithmétique n'est effectué côté client dans ce diff (affichage/saisie uniquement), risque réel faible à ce stade [pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts] — deferred, no client-side arithmetic yet
- [x] [Review][Defer] Perte silencieuse de détail d'erreur (catch générique) sur la plupart des flux, `extractErrorType` avec `endsWith` fragile — pattern déjà présent ailleurs dans le code (`phase-control.component.ts`), pas une régression introduite par cette story [pluribourse-frontend/src/app/features/volunteer/deposit/*, pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts] — deferred, pre-existing pattern
- [x] [Review][Defer] Lost-update possible si un même article est édité deux fois rapidement (toggle incomplet + commentaire) avant résolution de la première requête [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts] — deferred, faible probabilité en usage réel (un seul bénévole édite un article à la fois)
- [x] [Review][Defer] Pas de test de la suppression concurrente d'un vendeur pendant qu'un autre poste dépose ses articles [pluribourse-frontend/src/app/features/volunteer/deposit/*.spec.ts] — deferred, edge case multi-poste non couvert par les ACs de cette story

`npm run build` : OK. `npm test` : **331/331 tests verts** (2 nouveaux fichiers de specs : `deposit-phase.guard.spec.ts`, `date-range.validator.spec.ts` ; +1 test de régression dans `item-form.component.spec.ts`).

### Review Findings (backend pass — 1/2)

Revue adversarielle sur `git diff 902319d..d6ecc33 -- pluribourse-backend` (commits `6161ce7`, `d6ecc33`), 3 couches (Blind Hunter, Edge Case Hunter, Acceptance Auditor vs cette spec). 31 constats bruts → 15 constats dédupliqués après vérification manuelle du code.

- [x] [Review][Decision] Scope creep non documenté (dates d'édition obligatoires + verrou "catégories requises avant Dépôt") — **Résolu avec l'utilisateur 2026-07-03 : garder et compléter.** Voir correctifs ci-dessous.
- [x] [Review][Decision] Race condition sur l'assignation de table en cas de créations concurrentes — **Résolu avec l'utilisateur 2026-07-03 : corriger maintenant.** Voir correctif ci-dessous.
- [x] [Review][Decision] `saveCategories()` peut heurter une violation FK non gérée si des articles existent encore — **Résolu avec l'utilisateur 2026-07-03 : bloquer la modification des catégories si des articles sont assignés à l'édition, symétrique au garde-fou déjà posé sur `EditionService.deleteEdition()`.** Voir correctif ci-dessous.
- [x] [Review][Patch] Migration `014-edition-dates-not-null.xml` n'a pas de backfill — échouera au démarrage si une édition existante a `start_date`/`end_date` null → **Corrigé** : changeset de backfill (`start_date`/`end_date` ← `created_at`) ajouté avant la contrainte NOT NULL [pluribourse-backend/src/main/resources/db/changelog/014-edition-dates-not-null.xml]
- [x] [Review][Patch] `EditionDto` ne valide pas `startDate < endDate` → **Corrigé** : `@AssertTrue isDateRangeValid()` ajouté [pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java]
- [x] [Review][Patch] `EditionService.advancePhase()`/`NoCategoriesConfiguredException` ne vérifie pas que chaque catégorie a au moins un `tableNumber` configuré avant d'autoriser le passage en phase DEPOSIT → **Analysé, aucun changement nécessaire** : `EditionCategoryDto.tableNumbers` porte déjà `@NotEmpty` (validé côté `saveCategories`/`copyFromEdition`), une catégorie sans table ne peut donc jamais être persistée — le scénario est déjà impossible [pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionCategoryDto.java]
- [x] [Review][Patch] Pas de synchronisation sur l'assignation de table : deux créations concurrentes du 1er article d'un vendeur dans une catégorie peuvent aboutir sur deux tables différentes, violant la garantie FR-023 → **Corrigé** : verrou pessimiste (`EditionCategoryRepository.lockById`) posé sur la catégorie avant le calcul, sérialisant les assignations concurrentes [pluribourse-backend/src/main/java/org/pluribourse/item/service/TableAssignmentService.java, pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionCategoryRepository.java]
- [x] [Review][Patch] `EditionCategoryService.saveCategories()` doit refuser la modification des catégories si des articles existent pour l'édition → **Corrigé** : nouvelle `CategoriesInUseException` levée par `requirePreparationPhase()`, réutilisée par `saveCategories()` et `copyFromEdition()` [pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionCategoryService.java]
- [x] [Review][Patch] `NonUniqueResultException` dans la requête de recherche de table dès qu'un vendeur a ≥2 articles dans une même catégorie (le cas même décrit par l'AC 1) → **Corrigé** : `DISTINCT` ajouté à la requête JPQL ; test de non-régression ajouté (`ItemManagementIT.third_item_same_seller_same_category_does_not_crash_table_lookup`) [pluribourse-backend/src/main/java/org/pluribourse/item/repository/ItemRepository.java:27-32]
- [x] [Review][Patch] `ItemService.findCategory()` lève une `jakarta.persistence.EntityNotFoundException` brute au lieu d'une `BusinessException` du projet → **Corrigé** : nouvelle `CategoryNotFoundException` (404, RFC 7807) [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java, pluribourse-backend/src/main/java/org/pluribourse/item/exception/CategoryNotFoundException.java]
- [x] [Review][Patch] `create()`/`update()` ne vérifient pas que `sellerProfileId`/`categoryId` appartiennent à l'édition active → **Corrigé** : `findSellerInEdition()`/`findCategoryInEdition()` vérifient l'appartenance à l'édition active [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java:35-46]
- [x] [Review][Patch] `GET /items?sellerProfileId=` n'a aucun cadrage par édition → **Corrigé** : `getBySellerProfile()` vérifie désormais que le vendeur appartient à l'édition active [pluribourse-backend/src/main/java/org/pluribourse/item/controller/ItemController.java:21-23, pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java]
- [x] [Review][Patch] `CreateItemDto.price` n'a pas de contrainte `@Digits` → **Corrigé** : `@Digits(integer = 8, fraction = 2)` ajouté [pluribourse-backend/src/main/java/org/pluribourse/item/dto/CreateItemDto.java]
- [x] [Review][Defer] Aucune gestion d'exception ni test pour les conflits de verrouillage optimiste (`@Version` sur `Item`) [pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java] — deferred, pre-existing : explicitement hors périmètre de cette story selon les Dev Notes ("Ne pas implémenter la logique de conflit de vente... c'est le périmètre de l'Epic 4 (POS)")

**Effet de bord corrigé** : le garde-fou `NoCategoriesConfiguredException` (conservé par décision utilisateur) bloquait 3 suites de tests pré-existantes (`SellerManagementIT`, `CurrentEditionIT`, `VolunteerEditionGateIT`, Stories 3.1/2.3/2.6) qui avancent une édition en phase Dépôt sans configurer de catégorie au préalable. Corrigé en ajoutant la configuration d'une catégorie avant l'avancement de phase dans ces 3 classes de tests. Suite complète : **187/187 tests verts**.

## Dev Notes

### ⚠️ Bug préexistant vérifié — suite de tests actuellement rouge sur `main`

`SellerProfile.canBeDeleted()` (`pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java:34-39`) est :
```java
public boolean canBeDeleted() {
    PhaseType phase = edition.getPhase();
    boolean isOnDeletablePhase = phase == PhaseType.PREPARATION;
    boolean hasNoSelledArticles = false; // TODO avec Story 3.2
    return isOnDeletablePhase && hasNoSelledArticles;
}
```
`hasNoSelledArticles` étant figé à `false`, cette méthode retourne **toujours** `false`, quelle que soit la phase. Vérifié en exécutant `mvn -Dtest=SellerManagementIT test` sur `main` (commit baseline `902319d`) : **2 tests actuellement en échec** :
- `admin_deletes_seller_in_deposit_phase` — attend `204`, obtient `422` (`seller-deletion-locked`)
- `search_no_longer_finds_deleted_seller` — le vendeur n'a pas été supprimé donc réapparaît dans la recherche

Ce n'est pas une régression à introduire par cette story — c'est un état déjà cassé sur `main`, déjà connu et scopé par la Story 3.1 (`// TODO avec Story 3.2`) : `hasNoSelledArticles` reste toujours `false`, quelle que soit la phase, alors que le comportement attendu (FR-021) est que la suppression réussisse en phase Dépôt dès lors qu'aucun article n'est enregistré pour le vendeur. Cette story doit corriger :
1. Remplacer `hasNoSelledArticles` (toujours `false`) par un paramètre réel `hasNoRegisteredArticles` fourni par `SellerService.delete()` via une requête `ItemRepository`.
2. Conserver `isOnDeletablePhase` restreint à `phase == PhaseType.DEPOSIT` — c'est la seule phase où FR-021 autorise la suppression d'un vendeur.

> **Correction 2026-07-03 :** une première itération de cette story avait élargi `isOnDeletablePhase` à `PREPARATION || DEPOSIT`, sur la base d'une lecture littérale d'un brouillon antérieur de l'AC 9. Le Product Owner a explicitement confirmé que le périmètre voulu est **Dépôt uniquement** — `epics.md` (FR-021 et l'AC de la Story 3.1) a été corrigé en conséquence. Voir Change Log.

Suggestion de signature (renommage demandé explicitement par `epics.md` ligne 1100, `hasNoSelledArticles` → `hasNoRegisteredArticles`) :
```java
public boolean canBeDeleted(boolean hasNoRegisteredArticles) {
    PhaseType phase = edition.getPhase();
    boolean isOnDeletablePhase = phase == PhaseType.DEPOSIT;
    return isOnDeletablePhase && hasNoRegisteredArticles;
}
```
`SellerService.delete()` (`seller/service/SellerService.java:61-69`) devient :
```java
SellerProfile seller = repository.findById(id).orElseThrow(() -> new SellerNotFoundException(id));
boolean hasNoRegisteredArticles = !itemRepository.existsBySellerProfileId(id);
if (!seller.canBeDeleted(hasNoRegisteredArticles)) {
    throw new SellerDeletionNotAllowedException();
}
repository.delete(seller);
```
Après ce correctif, les deux tests actuellement rouges doivent repasser au vert **sans changer leurs assertions** — ils décrivent déjà le comportement cible.

### Gap d'architecture — pas de duplication d'entité `Category`

`architecture.md` (arborescence indicative, lignes 593-599) suggère `item/entity/Category.java` et `item/entity/TableAssignment.java`. **Ces deux entités ne doivent pas être créées** :
- La Story 2.5 a déjà introduit `EditionCategory` (`org.pluribourse.edition.entity.EditionCategory`) avec un `@ElementCollection Set<Integer> tableNumbers` (table `category_table_assignments`, migration `011-edition-categories.xml`). C'est la seule source de vérité pour les catégories et les numéros de table configurés — `Item.category` doit référencer `EditionCategory` directement (import cross-package `org.pluribourse.edition.entity.EditionCategory`), pas une nouvelle entité dupliquée.
- Il n'existe pas de table physique « Table » distincte : un numéro de table est un simple entier de `EditionCategory.tableNumbers`. Pas besoin d'entité `TableAssignment` : l'assignation d'un article à une table est un simple champ `Item.tableNumber` (Integer), et la charge par table se calcule par une requête de comptage sur `Item` (voir algorithme ci-dessous). Une entité `TableAssignment` séparée serait une sur-ingénierie non justifiée par les ACs.

### Gap d'accès — le bénévole ne peut pas lire les catégories de l'édition active

`EditionCategoryController` (`edition/controller/EditionCategoryController.java`) est monté sur `/admin/editions/{editionId}/categories` avec `@PreAuthorize("hasRole('ADMIN')")`, et `SecurityConfig` bloque tout `/admin/**` aux non-ADMIN (`SecurityConfig.java:63`). Le bénévole n'a donc **aucun moyen actuel** de récupérer la liste des catégories pour peupler le sélecteur du formulaire d'article. C'est un vrai gap fonctionnel introduit par cette story, pas une simple réutilisation.

Solution recommandée (cohérente avec le pattern `CurrentEditionController`, qui expose déjà en lecture seule un sous-ensemble de données d'édition sous `/api/editions/current` sans préfixe admin) : ajouter un contrôleur non-admin, par exemple dans `edition.controller` :
```java
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CurrentEditionCategoryController {
    private final EditionCategoryService categoryService;
    private final EditionService editionService;

    @GetMapping
    public ResponseEntity<List<EditionCategoryDto>> getCategoriesForActiveEdition() {
        Long activeEditionId = editionService.getActiveEdition().getId();
        return ResponseEntity.ok(categoryService.getCategories(activeEditionId));
    }
}
```
Aucune nouvelle méthode de service n'est nécessaire — `EditionCategoryService.getCategories(editionId)` existe déjà et n'a pas de restriction de rôle intrinsèque (seule la couche contrôleur imposait ADMIN). Le DTO `EditionCategoryDto` inclut `tableNumbers` : c'est acceptable de l'exposer au bénévole (ce ne sont pas des données personnelles), mais le frontend n'en a besoin que pour le nom/id de catégorie — l'algorithme d'assignation de table reste entièrement côté serveur.

### Algorithme d'assignation automatique de table (FR-023)

Exécuté côté serveur, à la création d'un article et à sa modification si la catégorie change :
1. **Le vendeur a-t-il déjà un article dans cette catégorie (cette édition) ?** Requête `ItemRepository` : `SELECT i.tableNumber FROM Item i WHERE i.sellerProfile.id = :sellerId AND i.category.id = :categoryId` (n'importe quel résultat, tous les articles du même vendeur dans la même catégorie partagent la même table) → si présent, réutiliser ce `tableNumber`.
2. **Sinon (premier article du vendeur dans cette catégorie)** : parmi `category.getTableNumbers()` (le `Set<Integer>` configuré pour cette catégorie), choisir celui ayant le moins d'articles **toutes catégories confondues** dans l'édition active. Requête de comptage groupé :
   ```java
   @Query("SELECT i.tableNumber, COUNT(i) FROM Item i WHERE i.edition.id = :editionId AND i.tableNumber IN :tableNumbers GROUP BY i.tableNumber")
   List<Object[]> countByTableNumber(@Param("editionId") Long editionId, @Param("tableNumbers") Collection<Integer> tableNumbers);
   ```
   Puis, en mémoire : construire une map `tableNumber → count` initialisée à `0` pour toutes les tables de la catégorie (une table sans articles n'apparaît pas dans le résultat de la requête), fusionner les comptages obtenus, et choisir le `tableNumber` minimal en cas d'égalité (déterminisme requis pour les tests).
3. **Réassignation lors d'une modification de catégorie (AC 5)** : ré-exécuter exactement le même algorithme (étape 1 puis 2) avec la nouvelle catégorie — pas de logique différente entre création et modification.

### Schéma de migration `013-items.xml`

```xml
<createTable tableName="items">
    <column name="id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="edition_id" type="BIGINT">
        <constraints nullable="false" foreignKeyName="fk_items_edition"
                     references="editions(id)" deleteCascade="true"/>
    </column>
    <column name="seller_profile_id" type="BIGINT">
        <constraints nullable="false" foreignKeyName="fk_items_seller_profile"
                     references="seller_profiles(id)" deleteCascade="true"/>
    </column>
    <column name="category_id" type="BIGINT">
        <constraints nullable="false" foreignKeyName="fk_items_category"
                     references="edition_categories(id)"/>
    </column>
    <column name="name" type="VARCHAR(200)"><constraints nullable="false"/></column>
    <column name="price" type="DECIMAL(10,2)"><constraints nullable="false"/></column>
    <column name="incomplete" type="BOOLEAN" defaultValueBoolean="false"><constraints nullable="false"/></column>
    <column name="comment" type="VARCHAR(500)"/>
    <column name="table_number" type="INT"><constraints nullable="false"/></column>
    <column name="version" type="BIGINT" defaultValueNumeric="0"><constraints nullable="false"/></column>
</createTable>
```
- `edition_id` : dénormalisé sur `Item` (même pattern que `SellerProfile.edition` et `EditionCategory.edition`) pour permettre `existsByEditionId(Long)` sans jointure via `sellerProfile` — nécessaire pour AC 10.
- `category_id` : **pas** de `deleteCascade` — une catégorie ne peut de toute façon être supprimée qu'en phase Préparation (`CategoriesLockedException` sinon), avant que des articles n'existent (les articles ne sont créés qu'à partir de la phase Dépôt) : aucun conflit possible en pratique, mais éviter la cascade documente cette invariance.
- `version` : colonne `@Version` requise (verrouillage optimiste sur l'entité `Item`, `architecture.md`, ligne 234, tableau « Concurrence — POS (Point de Vente) »). **Ne pas** implémenter la logique de conflit de vente (état vendu, 409) dans cette story — c'est le périmètre de l'Epic 4 (POS). Ajouter uniquement la colonne/l'annotation maintenant évite une migration supplémentaire plus tard.
- **Ne pas ajouter** de colonne `sold`/`soldAt`/`barcode` dans cette story : ce sont les périmètres des Stories 3.5 (code-barres) et Epic 4 (statut vendu). Les ACs de cette story ne les mentionnent pas — les ajouter maintenant serait de la sur-ingénierie (CLAUDE.md : ne pas concevoir pour des besoins futurs hypothétiques).

Prochain numéro de migration disponible : `013` (dernier existant : `012-seller-profiles.xml`).

### Forme des DTOs

- `CreateItemDto(Long sellerProfileId, Long categoryId, @NotBlank @Size(max=200) String name, @DecimalMin("0.01") BigDecimal price, boolean incomplete, @Size(max=500) String comment)` — `tableNumber` n'est **jamais** fourni par le client, toujours calculé côté serveur.
- `ItemDto(Long id, Long sellerProfileId, Long categoryId, String categoryName, String name, BigDecimal price, boolean incomplete, String comment, Integer tableNumber)` — `categoryName` dénormalisé dans le DTO de lecture (via le mapper, à partir de `item.getCategory().getName()`) pour que le frontend affiche directement le nom de catégorie dans la liste des articles (`article-meta` du mockup) sans requête supplémentaire.
- DTO séparé pour le PATCH complet/incomplet : `ItemCompletenessDto(boolean incomplete, @Size(max=500) String comment)` — évite d'exposer nom/prix/catégorie sur un endpoint qui ne les modifie jamais.

### Package `org.pluribourse.item`

Suivre la structure des modules existants : `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `exception/`. Pattern de référence le plus proche pour le CRUD + verrouillage de phase : `seller/service/SellerService.java` (résolution de l'édition active via `EditionService.getActiveEdition()`, exception 422 si phase incorrecte).

**Sécurité** : monter `ItemController` sur `/api/items` (pas de `@PreAuthorize`, cohérent avec `SellerController` — accessible à tout utilisateur authentifié non-SELLER via la règle `.anyRequest().access(...)` de `SecurityConfig.java:65-72`).

**Verrouillage de phase (AC 5, 6, 7)** : réutiliser le pattern `SellerManagementNotAllowedException`/`CategoriesLockedException` — nouvelle exception `ItemModificationNotAllowedException` (422, type `item-modification-locked`) levée par `ItemService.update()` et `ItemService.delete()` si `edition.getPhase() != PhaseType.DEPOSIT`. **Ne pas** appliquer ce verrou à `ItemService.create()` (les ACs ne restreignent pas la création à la phase Dépôt explicitement, mais en pratique le formulaire de dépôt n'est accessible qu'en phase Dépôt via le même mécanisme que `SellerService.search()`/`create()` — reprendre `requireDepositPhase(edition)` par symétrie avec le module `seller`). **Ne pas** appliquer ce verrou à `ItemService.updateCompleteness()` (AC 8 : modifiable dans toutes les phases).

**BigDecimal (NFR-003)** : `price` en `BigDecimal`, jamais `float`/`double` — validation `@DecimalMin("0.01")` a minima (un article gratuit n'a pas de sens métier ; à confirmer/adapter si le mockup ou une story ultérieure prouve le contraire, mais aucune AC ne mentionne de prix nul ici).

### AC 10 — Suppression d'édition bloquée par la présence d'articles

`EditionService.deleteEdition()` (`edition/service/EditionService.java:76-83`) ne vérifie aujourd'hui que la phase :
```java
@Transactional
public void deleteEdition(Long id) {
    Edition edition = findById(id);
    if (edition.getPhase() != PhaseType.PREPARATION) {
        throw new EditionCannotBeDeletedException();
    }
    repository.delete(edition);
}
```
Ajouter une vérification d'absence d'articles, en plus (pas à la place) du check de phase existant :
```java
if (edition.getPhase() != PhaseType.PREPARATION) {
    throw new EditionCannotBeDeletedException();
}
if (itemRepository.existsByEditionId(id)) {
    throw new EditionCannotBeDeletedException();
}
repository.delete(edition);
```
Cela nécessite d'injecter `ItemRepository` dans `EditionService` — dépendance cross-module (`edition` → `item`), déjà acceptée dans ce projet dans l'autre sens (`item`/`seller` dépendent d'`edition`). Réutiliser `EditionCannotBeDeletedException` existante (même code d'erreur RFC 7807 `edition-cannot-be-deleted`) plutôt que d'en créer une nouvelle. **Attention au message** : le message actuel de l'exception (`"Editions that have progressed past Preparation phase cannot be deleted."`) devient factuellement faux dans ce nouveau scénario — l'édition refusée ici **est** en phase Préparation (cas d'un retour arrière Dépôt → Préparation avec articles restants), elle n'a pas « dépassé » cette phase. Reformuler le message en un texte neutre couvrant les deux causes, par exemple `"This edition cannot be deleted in its current state."`.

### Frontend

- Modèle de référence pour formulaire : `seller-form.component.ts` (Reactive Forms, `FormBuilder.nonNullable.group`, signal `loading`/`error`, `output()` pour `created`/`cancelled`).
- Modèle de référence pour édition/suppression inline avec mise à jour optimiste : `seller-list.component.ts` (`ConfirmDialogService`, `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts`) et `pluribourse-frontend/src/app/shared/components/toast/toast.service.ts` pour les retours de succès/erreur transitoires.
- Le mockup `mock-deposit.html` (`_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html`) montre le **sélecteur de type Article individuel/Lot** (`.type-selector`) — **ce sélecteur relève de la Story 3.3** (mode Lot). Cette story implémente **uniquement** le formulaire d'article individuel : ne pas construire le sélecteur segmenté maintenant, juste le formulaire simple (nom, prix, catégorie, case Incomplet, commentaire) repris du style `.card-form`/`.form-grid`/`.field-input` du mockup.
- **Le mockup ne montre aucun exemple visuel du mode Article individuel** : le `.card-form` visible y est figé en mode Lot, et aucun champ commentaire n'apparaît nulle part dans le fichier. Il n'y a donc pas de référence visuelle directe pour la disposition nom/prix/catégorie/case Incomplet/commentaire — composer ce formulaire en réutilisant les classes CSS génériques du mockup (`.field-group`/`.field-label`/`.field-input`/`.checkbox-row`), la disposition exacte étant laissée à l'appréciation du dev agent.
- Le mockup montre la liste « Articles déposés (N) » (`.article-list`/`.article-row`) avec nom, catégorie, prix, chip de statut (Complet/Incomplet) — **mais ne montre pas** d'affordance d'édition/suppression par ligne, ni de contrôle inline pour basculer complet/incomplet ou éditer le commentaire (AC 8, valable dans toutes les phases). C'est une lacune du mockup vis-à-vis des ACs de cette story : ajouter une action minimale par ligne (ex. icône crayon ouvrant le formulaire pré-rempli pour nom/prix/catégorie en phase Dépôt, icône/case à cocher directe pour complet/incomplet + un champ commentaire accessible en toute phase) en restant cohérent avec le style visuel existant (`article-row`, boutons icône `MatIconModule`) — décision d'implémentation à faire par le dev agent en l'absence de mockup dédié, pas un blocage.
- `CurrentEditionService.currentEdition` (signal déjà injecté) n'est pas nécessaire ici : comme pour `seller.service.ts`, le backend résout l'édition active lui-même ; le frontend n'a pas besoin de transmettre d'`editionId`.
- `SellerSearchComponent` (`features/volunteer/deposit/seller-search.component.ts:41`) expose déjà le signal `selectedSeller` (`SellerDto | null`), renseigné par la méthode `selectSeller()` (`:77-83`) — le composant orchestrateur de dépôt (à créer) doit afficher `item-form` + la liste d'articles seulement quand un vendeur est sélectionné. Au commit baseline, `volunteer.routes.ts` route directement vers `SellerSearchComponent` (pas de wrapper existant) : le composant orchestrateur doit être introduit et substitué dans `volunteer.routes.ts`.
- Convention i18n : suivre le format déjà établi sous `volunteer.deposit.*` (voir `fr.json`/`en.json` autour de la clé `deposit.form.*`) — ajouter sous `volunteer.deposit.item.*`.

### Testing Standards

- E2E uniquement via les contrôleurs (`org.pluribourse.shared.IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`, données persistantes entre méthodes). Nouvelle classe `ItemManagementIT` dans `org.pluribourse.item`.
- Scénario suggéré : créer édition (catégories avec tables se chevauchant entre catégories pour bien tester le comptage « toutes catégories confondues », ex. Jouets=[1,2], Livres=[2,3]) → phase DEPOSIT → créer vendeur → créer article Jouets (1er article → table la moins chargée parmi [1,2], soit 1 par défaut à vide) → créer 2e article même vendeur même catégorie → même table que le 1er (AC 1) → créer article Livres pour un autre vendeur (1er de la catégorie → parmi [2,3], la table 2 a déjà 2 articles de la catégorie Jouets donc compte dans le total global → si 3 est moins chargée, 3 est choisie) → modifier catégorie d'un article (Jouets→Livres) → vérifier réassignation (AC 5) → supprimer un article en phase DEPOSIT (AC 6) → avancer en phase SALE → modification/suppression refusées (422 `item-modification-locked`, AC 7) → toggle incomplet + commentaire accepté même en phase SALE (AC 8) → tenter de supprimer le vendeur avec articles restants → refusé → supprimer tous ses articles → suppression vendeur acceptée (AC 9) → tenter de supprimer l'édition avec des articles restants (retour en PREPARATION au préalable) → refusé (AC 10).
- **Corriger en premier** les 2 tests actuellement rouges de `SellerManagementIT` (voir section bug ci-dessus) avant d'écrire les nouveaux tests — sinon la suite de régression reste rouge indépendamment du travail de cette story.
- Comptes de test disponibles : `test_admin` (ADMIN), `volunteer1`/`volunteer2` (VOLUNTEER), mot de passe `Admin` (voir `test-data.sql`). Aucune donnée `item` de fixture actuellement dans `test-data.sql` — les créer via les endpoints dans le test, pas en SQL statique (cohérent avec l'absence de catégories/vendeurs de fixture).
- Couverture backend et frontend ≥ 80 %.

### Project Structure Notes

- Nouveau package `org.pluribourse.item` (`controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `exception/`) — conforme à la structure unifiée d'`architecture.md`, à l'exception des entités `Category`/`TableAssignment` volontairement omises (voir gap d'architecture ci-dessus).
- Modification cross-module attendue et acceptée : `EditionService` (edition) dépend désormais d'`ItemRepository` (item) pour AC 10 ; `SellerService` (seller) dépend désormais d'`ItemRepository` (item) pour AC 9 — cohérent avec la dépendance déjà existante d'`item` vers `edition`/`seller` (aucun cycle : `edition` ne dépend pas d'un service `item`, seulement du repository).
- Prochain numéro de migration : `013` (dernier existant : `012-seller-profiles.xml`).
- Prochain numéro de story dans `sprint-status.yaml` après celle-ci : `3-3-creation-et-gestion-des-lots`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.2, lignes 1053-1106] (ACs, note technique `SellerProfile.canBeDeleted()`, note technique `EditionService.deleteEdition()`)
- [Source: _bmad-output/planning-artifacts/epics.md#FR-022, FR-023, FR-024, FR-025, NFR-003]
- [Source: _bmad-output/planning-artifacts/architecture.md, ligne 234, tableau "Concurrence — POS (Point de Vente)"] (verrouillage optimiste `@Version` sur `Item`)
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 593-599] (arborescence indicative `item/` — Category/TableAssignment volontairement non repris, voir Dev Notes)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR15, UX-DR16]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java] (bug vérifié par exécution de test, voir Dev Notes)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/entity/EditionCategory.java, EditionCategoryDto.java, EditionCategoryService.java, EditionCategoryController.java]
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java] (règles d'accès `/api/admin/**` vs. reste)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/seller/SellerManagementIT.java] (2 tests actuellement en échec, lignes 244-256)
- [Source: pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.ts, seller-form.component.ts] (patterns formulaire/sélection vendeur)
- [Source: pluribourse-frontend/src/app/models/category.model.ts, services/category.service.ts]
- [Source: pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts, shared/components/toast/toast.service.ts]
- [Source: pluribourse-backend/src/main/resources/db/changelog/011-edition-categories.xml, 012-seller-profiles.xml] (pattern de migration)
- Vérification empirique : `mvn -Dtest=SellerManagementIT test` exécuté sur `main` (commit `902319d`) le 2026-07-03 — 2 échecs confirmés (voir Dev Notes).

## Dev Agent Record

### Agent Model Used

claude-sonnet-5

### Debug Log References

- `mvn -Dtest=SellerManagementIT test` (baseline `902319d`) : 2 échecs confirmés avant correctif (`admin_deletes_seller_in_deposit_phase`, `search_no_longer_finds_deleted_seller`)
- `mvn -Dtest=SellerManagementIT test` (après correctif `canBeDeleted`) : 21/21 tests verts
- `mvn -Dtest=ItemManagementIT test` : 24/24 tests verts
- `mvn test` (suite complète backend) : 185/185 tests verts, aucune régression
- `npm run build` (frontend) : build OK sans erreur de compilation/template
- `npm test` (frontend) : 317/317 tests verts (42 fichiers de specs)

### Completion Notes List

- Corrigé le bug préexistant `SellerProfile.canBeDeleted()` : `hasNoSelledArticles` figé à `false` remplacé par un paramètre réel `hasNoRegisteredArticles` fourni par `SellerService.delete()` via `ItemRepository.existsBySellerProfileId()` ; phase éligible restreinte à `DEPOSIT` (FR-021). Les 2 tests rouges de `SellerManagementIT` repassent au vert sans modification de leurs assertions.
- **Correction 2026-07-03 (post-review) :** le Product Owner a confirmé que la suppression d'un vendeur ne doit être possible **qu'en phase Dépôt**, pas en Préparation. `isOnDeletablePhase` resserré en conséquence dans `SellerProfile.canBeDeleted()` ; `epics.md` (FR-021 et l'AC technique de la Story 3.2) et l'AC 9 de cette story mis à jour pour refléter ce périmètre. Aucun test existant ne dépendait de la suppression en phase Préparation — suite de régression revérifiée après correction (185 tests backend verts).
- Nouveau module backend `org.pluribourse.item` complet (entité, repository, DTOs, mapper MapStruct, service, contrôleur, exceptions) suivant le pattern du module `seller`. `EditionCategory` réutilisée directement (pas de duplication d'entité `Category`/`TableAssignment`).
- Algorithme d'assignation de table (FR-023) implémenté dans `TableAssignmentService`. Point d'attention traité : lors d'une réassignation de catégorie (AC 5), l'article en cours de modification est explicitement exclu (paramètre `excludeItemId` sur les requêtes `ItemRepository`) pour éviter qu'il s'auto-influence via l'auto-flush Hibernate déclenché par les requêtes JPQL de comptage — sans cette exclusion, l'article en transit vers sa nouvelle catégorie pouvait fausser son propre calcul de table.
- Nouvel endpoint bénévole `GET /categories` (`CurrentEditionCategoryController`) résolvant l'édition active pour peupler le sélecteur de catégorie du formulaire — comble le gap d'accès identifié dans les Dev Notes (`/admin/editions/{id}/categories` étant verrouillé ADMIN).
- `EditionService.deleteEdition()` : ajout de la vérification `ItemRepository.existsByEditionId()` en plus du check de phase existant ; message de `EditionCannotBeDeletedException` reformulé pour rester factuellement correct sur les deux causes de refus possibles.
- Frontend : `ItemFormComponent` gère à la fois création et édition (name/prix/catégorie) via un input `editingItem` optionnel, avec affichage du numéro de table assigné après sauvegarde. `DepositPageComponent` orchestre `SellerSearchComponent` (accès à son signal `selectedSeller` via `viewChild` + référence directe dans le template) + liste des articles déposés avec actions inline (édition, suppression avec confirmation, toggle complet/incomplet, édition de commentaire).
- Route `/volunteer/deposit` mise à jour pour charger `DepositPageComponent` au lieu de `SellerSearchComponent` directement.
- Pas d'outil de mesure de couverture automatisée configuré dans le projet (ni JaCoCo côté backend, ni `@vitest/coverage-v8` côté frontend) — cohérent avec l'état existant du repo. La cible de 80 % est visée par la conception des suites de tests (E2E backend couvrant chaque branche de l'algorithme et chaque AC ; specs frontend couvrant tous les chemins succès/erreur des nouveaux composants/services) plutôt que mesurée automatiquement.

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/resources/db/changelog/013-items.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/repository/ItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/TableAssignmentService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/CreateItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/ItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/ItemCompletenessDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/mapper/ItemMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/exception/ItemNotFoundException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/exception/ItemModificationNotAllowedException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/controller/ItemController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/CurrentEditionCategoryController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/item/ItemManagementIT.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java`
- `pluribourse-backend/src/main/java/org/pluribourse/seller/service/SellerService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/exception/EditionCannotBeDeletedException.java`

**Frontend — nouveaux fichiers**
- `pluribourse-frontend/src/app/models/item.model.ts`
- `pluribourse-frontend/src/app/services/item.service.ts`
- `pluribourse-frontend/src/app/services/item.service.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/services/category.service.ts`
- `pluribourse-frontend/src/app/services/category.service.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-07-03 : Implémentation complète de la story 3.2 (backend module `item`, algorithme FR-023, correctif bug `SellerProfile.canBeDeleted()`, blocage suppression édition avec articles restants, frontend formulaire d'article + page de dépôt orchestrée, i18n, tests backend et frontend). Statut → review.
- 2026-07-03 (post-review) : Correction du périmètre de phase de `SellerProfile.canBeDeleted()` sur demande du Product Owner : suppression possible en phase Dépôt uniquement (pas Préparation). `epics.md` (FR-021, AC technique Story 3.2) mis à jour en cohérence. AC 9 de cette story corrigée. Aucune régression (185 tests backend, 317 tests frontend toujours verts).
- 2026-07-04 (code review, backend + frontend) : Revue adversarielle complète (Blind Hunter / Edge Case Hunter / Acceptance Auditor) sur `git diff 902319d..d6ecc33`, en deux passes (backend puis frontend). Corrections notables : bug critique `NonUniqueResultException` sur l'assignation de table dès qu'un vendeur a ≥2 articles dans une catégorie ; verrou pessimiste anti-race-condition sur l'assignation de table ; blocage de la modification des catégories si des articles existent ; cadrage par édition active sur `create`/`update`/`getBySellerProfile` ; exceptions RFC 7807 propres au lieu d'exceptions JPA brutes ; backfill de migration pour les dates d'édition ; validation `startDate < endDate` (back + front) ; bug de formulaire `categoryId` par défaut à `0` au lieu de `null` (article soumis sans catégorie) ; message d'erreur explicite de verrouillage de phase (AC 7) sur les mutations d'article ; garde de route `/volunteer/deposit` sur la phase Dépôt ; `€` codé en dur remplacé par une clé i18n. Deux scope creep non documentés identifiés et tranchés avec le Product Owner (dates d'édition obligatoires + verrou catégories avant Dépôt côté backend ; sidebar rétractable admin côté frontend) : conservés et complétés/durcis. Effet de bord corrigé : 3 suites de tests pré-existantes (`SellerManagementIT`, `CurrentEditionIT`, `VolunteerEditionGateIT`) mises à jour pour configurer une catégorie avant d'avancer en phase Dépôt. Statut → done. **187/187 tests backend verts, 331/331 tests frontend verts, `npm run build` OK.**
