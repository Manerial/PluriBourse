---
baseline_commit: 53e73dc0f56f6c275a6c47c7e18e81fc4886a2f2
---

# Story 3.3: Création et gestion des lots

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole,
I want regrouper des articles en un lot indivisible avec un prix global unique,
so that les ensembles vendus ensemble soient traités comme une unité atomique lors de la vente.

## Acceptance Criteria

1. Dans le formulaire de dépôt, un sélecteur segmenté « Article individuel / Lot » permet de basculer le formulaire en mode Lot : les champs « Nom du lot » et « Prix global du lot (€) » remplacent la saisie individuelle (FR-043), et une liste d'articles apparaît avec un bouton « + Ajouter un article au lot ».
2. En mode Lot, chaque ligne d'article porte son propre nom/description, sa propre catégorie, une case Incomplet et un commentaire optionnel — **sans prix individuel** (FR-022, FR-043, FR-044).
3. Le bouton « Valider le lot » reste désactivé tant que moins de 2 articles sont présents ; son libellé reflète en temps réel le nombre d'articles saisis (ex. « Valider le lot (2 articles) »).
4. À la sauvegarde d'un lot, chaque article obtient sa propre table assignée automatiquement selon l'algorithme FR-023 déjà en place (Story 3.2), appliqué indépendamment à la catégorie de chaque article du lot.
5. Le lot ne peut être créé qu'en phase Dépôt, au même titre que les articles individuels (FR-024 par cohérence — verrouillage de phase déjà en place sur le module `item`).
6. La sauvegarde d'un lot est atomique : soit le lot et tous ses articles sont créés, soit rien n'est créé (ex. si une catégorie référencée n'existe pas ou n'appartient pas à l'édition active).
7. Les articles créés dans le cadre d'un lot apparaissent dans la liste « Articles déposés » du vendeur, avec une indication visuelle qu'ils appartiennent à un lot (nom du lot affiché) et le prix global du lot à la place d'un prix individuel.

**Hors périmètre de cette story (voir Dev Notes § Scope) :** génération de codes-barres et rendu des étiquettes (« Prix du lot : X€ », « Lot indivisible : X/N ») — Story 3.5 ; modification/suppression d'un lot déjà sauvegardé (renommage, prix, ajout/retrait d'un article) — aucune AC de l'épic ne le demande, à traiter dans une story dédiée si le besoin est confirmé.

## Tasks / Subtasks

- [x] Backend — entité `Lot` et module de création (AC: 1-6)
  - [x] Migration Liquibase `015-lots.xml` (voir Dev Notes pour le schéma exact), incluse dans `db.changelog-master.xml`
  - [x] Entité `Lot` (`org.pluribourse.item.entity.Lot`) : `edition`, `sellerProfile`, `name`, `globalPrice` (BigDecimal), `@Version version`
  - [x] `Item.java` : ajouter `@ManyToOne(fetch = LAZY) @JoinColumn(name = "lot_id") private Lot lot;` (nullable) ; assouplir `price` en `@Column(nullable = true, ...)` — reste obligatoire côté validation pour les articles individuels via `CreateItemDto`, mais doit accepter `null` pour un article de lot
  - [x] `LotRepository extends JpaRepository<Lot, Long>` (pas de requête custom nécessaire pour cette story)
  - [x] Extraire `findSellerInEdition`/`findCategoryInEdition` (actuellement `private` dans `ItemService`) dans une classe partagée `org.pluribourse.item.service.EditionScopedLookup` (composant Spring injecté par `SellerRepository`/`EditionCategoryRepository`), et faire dépendre `ItemService` et le nouveau `LotService` de ce composant — évite la duplication de la vérification d'appartenance à l'édition active
  - [x] `CreateLotItemDto` (record) : `categoryId` (`@NotNull`), `name` (`@NotBlank @Size(max=200)`), `incomplete` (boolean), `comment` (`@Size(max=500)`, nullable) — **pas** de champ `price`
  - [x] `CreateLotDto` (record) : `sellerProfileId` (`@NotNull`), `name` (`@NotBlank @Size(max=200)`), `globalPrice` (`@NotNull @DecimalMin("0.01") @Digits(integer=8, fraction=2)`), `items` (`@NotNull @Valid @Size(min=2, message="A lot must contain at least 2 items")` `List<CreateLotItemDto>`)
  - [x] `LotDto` (record) : `id`, `name`, `globalPrice`, `items` (`List<ItemDto>`) — assemblé manuellement dans `LotService` via `itemMapper.toDtos(items)`, pas de mapper dédié (agrégation cross-entité ponctuelle, pas un mapping 1:1)
  - [x] `LotService.create(CreateLotDto)` : **annoter `@Transactional`** (voir Dev Notes § Atomicité — c'est ce qui garantit AC 6, pas une boucle manuelle) ; résout l'édition active, vérifie la phase Dépôt (réutiliser exactement le même verrou que `ItemService` — voir Dev Notes), résout le vendeur puis **toutes** les catégories référencées par les articles du lot via `EditionScopedLookup` (valider l'existence de chaque catégorie avant de persister quoi que ce soit — évite de poser des verrous pessimistes `lockById` pour une requête vouée à échouer), puis persiste `Lot` et chaque `Item` (prix `null`, `lot` renseigné, table assignée via `TableAssignmentService.assignTable()` **inchangé**, une fois par article). `EditionScopedLookup` lève les exceptions **existantes** `SellerNotFoundException`/`CategoryNotFoundException` — ne pas créer de variantes dédiées aux lots.
  - [x] `ItemMapper.toDto()` : ajouter `@Mapping(target = "lotId", source = "lot.id")`, `@Mapping(target = "lotName", source = "lot.name")`, `@Mapping(target = "lotPrice", source = "lot.globalPrice")` ; `ItemDto` gagne ces 3 champs nullables
  - [x] `ItemMapper.toEntity()`/`updateEntityFromDto()` : ajouter `@Mapping(target = "lot", ignore = true)` par cohérence explicite avec les autres associations ignorées (le flux individuel ne touche jamais `lot`)
  - [x] `LotController` (`/api/lots`, même politique d'accès que `ItemController` — pas de `@PreAuthorize`, protégé par la règle générique non-SELLER de `SecurityConfig`) : `POST /` → 201 + `LotDto`
  - [x] Réutiliser `ItemModificationNotAllowedException` (422, `item-modification-locked`) pour le refus de création hors phase Dépôt — **ne pas** créer une nouvelle exception, le frontend gère déjà ce type
- [x] Frontend — sélecteur de type & formulaire Lot (AC: 1-3, 7)
  - [x] `models/lot.model.ts` : champs alignés **mot pour mot** sur les records backend (mêmes noms, mêmes types) —
    ```ts
    export interface CreateLotItemRequest {
      categoryId: number;
      name: string;
      incomplete: boolean;
      comment: string | null;
    }
    export interface CreateLotRequest {
      sellerProfileId: number;
      name: string;
      globalPrice: number;
      items: CreateLotItemRequest[];
    }
    export interface LotDto {
      id: number;
      name: string;
      globalPrice: number;
      items: ItemDto[];
    }
    ```
  - [x] `models/item.model.ts` : `ItemDto` gagne `lotId: number | null`, `lotName: string | null`, `lotPrice: number | null` ; **`price` devient `number | null`** (un article de lot renvoie `price: null`) — mettre à jour tout code frontend qui suppose `price` non-null (template de la liste, voir plus bas) **et** `item-form.component.ts` (voir Dev Notes § Impact du `price` nullable)
  - [x] `services/lot.service.ts` : `create(data: CreateLotRequest): Observable<LotDto>` → `POST /api/lots`
  - [x] `features/volunteer/deposit/lot-form.component.ts` (+ `.html` dédié, + `.scss`) : reprend le pattern Reactive Forms de `item-form.component.ts` (`FormBuilder.nonNullable`, signals `loading`/`error`, `output()` `saved`/`cancelled`) ; inputs `sellerId = input.required<number>()` et `categories = input.required<EditionCategoryDto[]>()` (mêmes noms/types que `ItemFormComponent`) ; `FormArray` d'articles initialisé à 2 lignes vides, boutons « + Ajouter un article au lot » / retirer une ligne, bouton de soumission désactivé si `< 2` lignes ou formulaire invalide, libellé dynamique via i18n avec `{{ count }}`
  - [x] `features/volunteer/deposit/deposit-page.component.ts`/`.html` : ajouter un signal `depositMode: signal<'individual' | 'lot'>('individual')`, un sélecteur segmenté (2 boutons, classes `.type-selector`/`.type-btn` du mockup) au-dessus du formulaire, affichage conditionnel de `<app-item-form>` ou `<app-lot-form>` selon `depositMode()` ; `onLotSaved()` recharge la liste des articles du vendeur (même pattern que `onItemSaved()`) et repasse `depositMode` à `'individual'`
  - [x] Liste « Articles déposés » : pour une ligne dont `item.lotId` n'est pas `null`, afficher le nom du lot (badge, classe `.lot-badge` du mockup) et le prix du lot (`item.lotPrice`) à la place de la catégorie/prix individuel ; **masquer les actions Modifier et Supprimer** pour ces lignes (voir Dev Notes § Scope — pas de gestion d'un lot existant dans cette story)
- [x] i18n : clés `volunteer.deposit.item.lotForm.*`, `volunteer.deposit.typeSelector.*`, `volunteer.deposit.item.list.lotBadge`/`lotPriceFormat` dans `fr.json`/`en.json`
- [x] Tests backend : `LotManagementIT` (E2E via contrôleurs, pattern `ItemManagementIT`) — création réussie avec assignation de table par article, refus si `< 2` articles (400), refus hors phase Dépôt (422 `item-modification-locked`), refus si une `categoryId` n'appartient pas à l'édition active, vérification que `GET /items?sellerProfileId=` renvoie bien les articles du lot avec `lotId`/`lotName`/`lotPrice` renseignés et `price` à `null`
- [x] Tests frontend : specs Vitest pour `LotFormComponent` (validation, libellé dynamique, ajout/retrait de ligne, soumission) et mise à jour des specs de `DepositPageComponent` (bascule de mode, affichage des lignes de lot) ; couverture ≥ 80 %

### Review Findings

- [x] [Review][Defer] Pas de contrainte niveau BDD garantissant `price` null ⇔ `lot_id` renseigné — L'invariant "un item a soit un prix individuel non-null, soit un `lot_id` non-null, jamais les deux, jamais ni l'un ni l'autre" repose uniquement sur le code applicatif (`LotService`/`ItemService`), sans `CHECK` dans `015-lots.xml`. [pluribourse-backend/src/main/resources/db/changelog/015-lots.xml] — deferred, même périmètre restreint que le gap DELETE déjà accepté.
- [x] [Review][Defer] `PUT /items/{id}` permet de fixer un prix non-null sur un article de lot sans le délier du lot — `ItemService.update()` (inchangé) n'a aucune garde empêchant la modification d'un item membre d'un lot via `CreateItemDto` (qui exige un prix non-null). Cela romprait l'invariant "un article de lot a `price = null`" dont dépend l'affichage de AC7. [pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java:50-61] — deferred, même raisonnement que le gap DELETE déjà accepté (bouton Modifier masqué côté frontend pour ces lignes).
- [x] [Review][Patch] `CreateLotDto.items` n'a pas de borne supérieure — `@Size(min = 2)` seulement, sans `max` ; une requête peut soumettre un nombre arbitraire d'articles, chacun déclenchant une résolution de catégorie + assignation de table dans une seule transaction. [pluribourse-backend/src/main/java/org/pluribourse/item/dto/CreateLotDto.java] — corrigé : `@Size(min = 2, max = 50)`.
- [x] [Review][Patch] `LotService` prend des verrous pessimistes par catégorie dans l'ordre de la liste reçue, risque de deadlock — deux créations de lots concurrentes référençant les mêmes catégories dans un ordre différent peuvent se bloquer mutuellement en BDD. Trier les catégories résolues par id avant assignation des tables pour garantir un ordre d'acquisition des verrous cohérent. [pluribourse-backend/src/main/java/org/pluribourse/item/service/LotService.java] — corrigé : les articles sont traités par ordre croissant d'id de catégorie pour l'assignation de table, tout en conservant l'ordre d'origine dans la réponse.
- [x] [Review][Patch] `LotManagementIT` ne teste jamais le scénario cross-édition que `EditionScopedLookup` est censé couvrir — le test « catégorie inconnue » n'utilise qu'un id inexistant (`999999L`), jamais un id de catégorie valide mais appartenant à une autre édition — exactement le cas que l'extraction d'`EditionScopedLookup` visait à sécuriser. [pluribourse-backend/src/test/java/org/pluribourse/item/LotManagementIT.java] — corrigé : nouveau test `create_lot_with_category_from_another_edition_returns_404` (Order 9), édition fermée dédiée créée en `@BeforeAll`.
- [x] [Review][Patch] Aucun test pour les contraintes de validation par article (`CreateLotItemDto`) — `@NotNull categoryId`, `@NotBlank @Size(max=200) name`, `@Size(max=500) comment` sont déclarées mais jamais exercées avec une entrée invalide pour vérifier un 400. [pluribourse-backend/src/test/java/org/pluribourse/item/LotManagementIT.java] — corrigé : nouveau test `create_lot_with_a_blank_item_name_is_rejected` (Order 5).
- [x] [Review][Patch] `setDepositMode()` ne réinitialise pas `editingItem()` lors du changement de mode — éditer un article individuel puis basculer en mode Lot puis revenir en mode Individuel laisse `ItemFormComponent` pré-rempli avec l'édition abandonnée, sans annulation explicite. [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts] — corrigé : `editingItem()` remis à `null` dans `setDepositMode()`.
- [x] [Review][Patch] Le bouton d'édition du commentaire n'est pas gardé par `@if (!item.lotId)` — seuls Modifier/Supprimer le sont ; ceci contredit la décision de scope documentée (aucune action de gestion sur les lignes d'un lot existant). [pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html] — corrigé : bouton commentaire déplacé dans le même bloc `@if (!item.lotId)`.
- [x] [Review][Patch] Les noms (lot et articles) ne sont pas trim() avant soumission, contrairement au commentaire — `Validators.required` n'exclut pas les chaînes composées uniquement d'espaces ; un nom `"   "` passe la validation client et échoue seulement côté serveur avec un message générique. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts] — corrigé : `name` et les noms d'articles sont trim() dans `onSubmit()`, même traitement que `comment`.
- [x] [Review][Patch] `globalPrice` n'est pas arrondi/normalisé à 2 décimales côté client avant soumission — repose entièrement sur le rejet serveur (`@Digits(fraction=2)`) plutôt que sur une normalisation client, incohérent avec la discipline `BigDecimal` du reste du code. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts] — corrigé : `globalPrice` arrondi à 2 décimales dans `onSubmit()`.
- [x] [Review][Patch] Le test `@Order(1)` de `LotManagementIT` utilise un `sellerProfileId = 1L` codé en dur qui n'existe pas encore à ce stade — le test ne passe que parce que la vérification de phase s'exécute avant la résolution du vendeur ; casserait silencieusement pour la mauvaise raison si cet ordre interne changeait. [pluribourse-backend/src/test/java/org/pluribourse/item/LotManagementIT.java] — corrigé : commentaire explicite ajouté documentant cette dépendance d'ordre (aucun seller ne peut légitimement exister à ce stade, la création de vendeur étant elle-même verrouillée en phase Dépôt).
- [x] [Review][Patch] Les Completion Notes surestiment le nombre de tests de `LotFormComponent` — annoncé « 12 tests », le diff en contient exactement 10 (`it(...)`) ; sans impact sur les ACs, mais fragilise la fiabilité de la déclaration de couverture ≥ 80 % non mesurée automatiquement. [pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.spec.ts] — corrigé : Completion Notes mises à jour (10 tests `LotFormComponent`, 11 tests `LotManagementIT` après ajout des 2 nouveaux tests).

## Dev Notes

### Scope — ce que cette story fait et ne fait pas

Les ACs de l'épic pour la Story 3.3 (`epics.md` lignes 1119-1146) couvrent **uniquement la création** d'un lot : sélecteur de type, saisie des articles du lot, validation du minimum de 2 articles, assignation de table par article. Deux points mentionnés dans les ACs relèvent en réalité d'autres stories déjà planifiées et **ne doivent pas être implémentés ici** :

- **Génération de code-barres et rendu d'étiquette** (« chaque article reçoit son propre code-barres généré », « Prix du lot : X€ », « Lot indivisible : X/N ») — c'est le périmètre explicite de la **Story 3.5** (génération/impression des étiquettes thermiques), qui n'existe pas encore dans le code (aucune génération de code-barres n'existe à ce stade, y compris pour les articles individuels créés en Story 3.2). Le modèle de données de cette story (chaque article de lot reste une entité `Item` individuelle avec son propre `id`) est suffisant pour que la Story 3.5 puisse s'y greffer sans migration supplémentaire.
- **Modification ou suppression d'un lot déjà sauvegardé** (renommer le lot, changer son prix global, ajouter/retirer un article après coup) — **aucune AC de l'épic ne demande cette fonctionnalité** pour la Story 3.3, contrairement aux articles individuels (Story 3.2, AC 5-6) qui la prévoient explicitement. Le mockup (`mock-deposit.html`) montre un lot déjà sauvegardé apparaissant en une seule ligne agrégée dans la liste, mais ceci est une illustration visuelle, pas une AC. **Décision de scope pour cette story** (à confirmer avec l'utilisateur en review, sur le modèle des scope creep déjà arbitrés en Story 3.2) : afficher chaque article d'un lot comme une ligne individuelle dans la liste « Articles déposés » (réutilisation intégrale du rendu `article-row` existant), avec juste une indication du nom/prix de lot, **sans** action Modifier/Supprimer sur ces lignes — plutôt que de construire l'agrégation « une ligne par lot » du mockup, qui nécessiterait de trancher des questions non spécifiées (que se passe-t-il si on supprime le lot entier ? un seul article du lot ? quelle catégorie afficher si les articles du lot ont des catégories différentes ?). Si l'utilisateur souhaite l'agrégation visuelle complète dès cette story, le signaler en review plutôt que de l'improviser silencieusement.

### Pourquoi un `Lot` distinct plutôt qu'un simple regroupement sur `Item`

`architecture.md` (arborescence indicative, ligne 595-598) suggère déjà `LotService`, `LotRepository`, `Lot.java` dans le module `item` — cohérent avec l'approche retenue ici : une entité `Lot` séparée (id, nom, prix global, vendeur, édition) référencée par chaque `Item` membre via une FK nullable `lot_id`. Alternative rejetée : stocker le nom/prix du lot directement sur chaque `Item` dupliqué N fois — casserait l'atomicité de la mise à jour (si jamais une story future permet de renommer un lot) et ne correspond pas à FR-048 (« le lot... a un prix », singulier, propriété du lot et non de chaque article).

### Schéma de migration `015-lots.xml`

Prochain numéro de migration disponible : `015` (dernier existant : `014-edition-dates-not-null.xml`).

```xml
<createTable tableName="lots">
    <column name="id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="edition_id" type="BIGINT">
        <constraints nullable="false" foreignKeyName="fk_lots_edition"
                     references="editions(id)" deleteCascade="true"/>
    </column>
    <column name="seller_profile_id" type="BIGINT">
        <constraints nullable="false" foreignKeyName="fk_lots_seller_profile"
                     references="seller_profiles(id)" deleteCascade="true"/>
    </column>
    <column name="name" type="VARCHAR(200)"><constraints nullable="false"/></column>
    <column name="global_price" type="DECIMAL(10,2)"><constraints nullable="false"/></column>
    <column name="version" type="BIGINT" defaultValueNumeric="0"><constraints nullable="false"/></column>
</createTable>
<addColumn tableName="items">
    <column name="lot_id" type="BIGINT">
        <constraints nullable="true" foreignKeyName="fk_items_lot" references="lots(id)"/>
    </column>
</addColumn>
<dropNotNullConstraint tableName="items" columnName="price" columnDataType="DECIMAL(10,2)"/>
```

- `lot_id` : **pas** de `deleteCascade` — aucune suppression de lot n'est implémentée dans cette story (voir Scope ci-dessus), même raisonnement que `items.category_id` (Story 3.2 Dev Notes) : documente l'invariance plutôt que d'anticiper un comportement non testé.
- `edition_id`/`seller_profile_id` sur `Lot` : dénormalisés, même pattern que `Item`/`SellerProfile`/`EditionCategory` — permet de scoper les lots à l'édition/vendeur sans jointure via les items.
- `price` sur `items` devient nullable : uniquement les articles de lot auront `price = null` ; `CreateItemDto` (flux individuel, inchangé) continue d'exiger un prix non-null via `@NotNull` — la contrainte de nullabilité se déplace donc entièrement de la base vers la validation applicative pour ce flux.
- **Suppression en cascade multi-parents** : `lots.seller_profile_id` et `items.seller_profile_id` ont tous deux `deleteCascade=true` depuis `seller_profiles` ; `items.lot_id` référence `lots(id)` **sans** cascade. Si un vendeur est supprimé, MariaDB/InnoDB résout les deux chaînes de cascade (`seller_profiles → lots` et `seller_profiles → items`) comme un seul graphe de suppression — l'absence de cascade sur `items.lot_id` ne bloque pas cette suppression conjointe (les deux tables perdent leurs lignes pour ce vendeur dans la même opération). Ne pas ajouter de cascade sur `items.lot_id` pour "sécuriser" ce cas : ce n'est pas nécessaire et introduirait un chemin de suppression en cascade non testé par les ACs de cette story.
- **Gap accepté** : l'endpoint générique existant `DELETE /items/{id}` (Story 3.2) reste utilisable tel quel sur un article membre d'un lot — aucune garde nouvelle n'est ajoutée pour l'interdire, et aucune AC ne le demande. Cela peut laisser un lot avec moins de 2 articles restants. Le frontend n'expose pas ce chemin pour les lignes de lot (bouton Supprimer masqué, voir Scope), mais l'API ne l'empêche pas. Accepté comme gap mineur cohérent avec le périmètre volontairement restreint de cette story (pas de gestion d'un lot existant) — à durcir si une story dédiée à la gestion des lots existants est créée.

### Atomicité (AC 6) — pourquoi un simple `@Transactional` suffit

`LotService.create()` doit porter `@Transactional` (comme `ItemService.create()`/`update()`). Vérifié : `BusinessException extends RuntimeException` (`shared/exception/BusinessException.java`), et `SellerNotFoundException`/`CategoryNotFoundException`/`ItemModificationNotAllowedException` en héritent toutes — ce sont donc des exceptions **non-checked**, sur lesquelles Spring déclenche un rollback **par défaut**, sans configuration supplémentaire. Conséquence directe : si une catégorie du 2ᵉ article du lot est invalide, la levée de `CategoryNotFoundException` annule aussi la création du `Lot` et du 1er article déjà persistés dans la même transaction. **Ne pas** entourer la boucle de création d'articles d'un `try/catch` "pour gérer proprement l'erreur" — cela avalerait l'exception et casserait le rollback, donc l'atomicité exigée par AC 6. Valider toutes les `categoryId` (via `EditionScopedLookup`) avant de persister le `Lot` ou le premier `Item` reste recommandé pour éviter des verrous pessimistes inutiles sur une requête vouée à échouer, mais ce n'est qu'une optimisation : l'atomicité elle-même vient du `@Transactional` + des exceptions non-checked, pas de l'ordre de validation.

### Impact du `price` nullable sur `ItemFormComponent`

`ItemFormComponent.form` a un contrôle `price` non-nullable (`FormBuilder.nonNullable`, valeur initiale `0`). Son `effect()` d'édition fait aujourd'hui `this.form.setValue({..., price: item.price, ...})`. Une fois `ItemDto.price` retypé `number | null`, ce `setValue` ne compile plus tel quel. Dans le flux normal, ce code n'est jamais exercé pour un article de lot (le bouton Modifier est masqué pour ces lignes — voir Scope), mais TypeScript ne le sait pas statiquement : corriger avec `price: item.price ?? 0` (ou équivalent) dans `item-form.component.ts` pour rester compilable, sans changer le comportement runtime réel (un article individuel a toujours un `price` non-null).

### Réutilisation stricte de l'algorithme FR-023 existant

`TableAssignmentService.assignTable(SellerProfile, EditionCategory, Edition)` (Story 3.2, inchangé) doit être appelé **une fois par article du lot**, avec la catégorie propre à cet article — pas de nouvelle méthode « batch ». Chaque article de lot suit exactement la même règle qu'un article individuel : même table que les articles déjà présents du vendeur dans cette catégorie, sinon la table la moins chargée toutes catégories confondues. Le verrou pessimiste sur `EditionCategoryRepository.lockById()` (déjà en place) protège aussi cette story sans modification : chaque appel à `assignTable()` verrouille sa propre catégorie le temps de son propre calcul.

### Verrouillage de phase — réutiliser, ne pas dupliquer la logique

`ItemService` a une méthode privée `requireDepositPhase(Edition)` qui lève `ItemModificationNotAllowedException` si `edition.getPhase() != PhaseType.DEPOSIT`. Le nouveau `LotService` doit appliquer exactement la même règle avant de créer un lot. Ne pas dupliquer cette méthode : soit la rendre package-private dans `ItemService` et l'invoquer depuis `LotService` (mais cela couplerait `LotService` à `ItemService` sans raison métier), soit — **option recommandée** — l'extraire en méthode statique dans une petite classe utilitaire partagée du package `item.service` (par exemple à côté de `EditionScopedLookup`, ou une classe `PhaseGuard` minimale prenant l'`Edition` en paramètre). Choix laissé au dev agent tant que la logique n'est écrite qu'une fois et que le message d'erreur/type RFC 7807 reste `item-modification-locked` (le frontend parse déjà ce type, ne pas en introduire un nouveau).

### Frontend — sélecteur de type et formulaire Lot

- Mockup de référence : `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html`, lignes 507-534 (CSS `.type-selector`/`.type-btn`) et lignes 759-854 (structure complète du formulaire en mode Lot : `#lot-name`, `#lot-price`, `.lot-articles-section`, `.lot-article-row` avec `.lot-num`/`.lot-name-input`/`.lot-cat-select`/`.lot-incomplet-check`/`.lot-remove-btn`, bouton `.btn-add-lot-article`).
- **Le mockup ne montre pas de champ commentaire par article de lot** (seulement nom, catégorie, case Incomplet) — pourtant FR-022/AC 2 de cette story l'exigent explicitement pour tout article, individuel ou de lot. Même lacune mockup/AC que celle déjà documentée en Story 3.2 pour le formulaire individuel : ajouter un champ commentaire par ligne malgré son absence visuelle dans le mockup (ex. petit champ texte ou icône dépliante, cohérent avec le style `.lot-article-row` existant), décision d'implémentation laissée au dev agent.
- `LotFormComponent` : `FormArray` de `FormGroup { name, categoryId, incomplete, comment }`, initialisé à 2 lignes vides (cohérent avec le libellé « Articles du lot — 2 minimum » du mockup) ; méthodes `addItemRow()`/`removeItemRow(index)` ; le bouton de soumission utilise `this.itemsFormArray.length` pour le libellé dynamique et pour l'état désactivé (`< 2` ou formulaire invalide ou `loading()`).
- Gestion d'erreur identique à `item-form.component.ts` : `extractErrorType(err)?.endsWith('/item-modification-locked')` → message de phase verrouillée ; `404 /no-active-edition` → message dédié ; sinon message générique de sauvegarde.
- `DepositPageComponent` : le sélecteur de type est un simple état local (`signal<'individual' | 'lot'>`), pas un nouveau composant partagé — deux boutons dans le template de `deposit-page.component.html`, au-dessus du `@if (depositMode() === 'individual') { <app-item-form ... /> } @else { <app-lot-form ... /> }`. Après sauvegarde d'un lot, recharger la liste (`loadItems(seller.id)`) exactement comme `onItemSaved()` le fait déjà pour un article individuel.
- Rendu des lignes de lot dans la liste : `item.lotId` non-null ⇒ afficher `item.lotName` (badge, classe `.lot-badge` du mockup ligne 319-320 déjà stylée) et `item.lotPrice` (au lieu de `item.price`) dans `.article-row__meta` ; masquer les boutons Modifier/Supprimer pour ces lignes (`@if (!item.lotId) { ...boutons... }`) — voir Scope ci-dessus.

### Testing Standards

- E2E uniquement via les contrôleurs (`org.pluribourse.shared.IntegrationTest`), nouvelle classe `LotManagementIT` dans `org.pluribourse.item`, pattern identique à `ItemManagementIT` (catégories avec tables qui se chevauchent pour exercer l'algorithme FR-023, sessions admin/volontaire créées dans `@BeforeAll`).
- Scénario suggéré : édition en phase Dépôt, catégories Jouets=[1,2] / Livres=[2,3], vendeur créé → `POST /lots` avec 1 seul article → 400 (violation `@Size(min=2)`) → `POST /lots` avec 2 articles de catégories différentes → 201, vérifier que chaque article a une table assignée selon l'algorithme existant, `price` à `null`, `lotId`/`lotName`/`lotPrice` cohérents → `GET /items?sellerProfileId=` renvoie les 2 articles avec ces champs de lot renseignés → avancer l'édition en phase Vente → `POST /lots` refusé (422 `item-modification-locked`) → `POST /lots` avec une `categoryId` inexistante ou d'une autre édition → 404.
- Couverture backend et frontend ≥ 80 %.

### Project Structure Notes

- Nouveaux fichiers dans le module existant `org.pluribourse.item` (pas de nouveau module top-level) : `entity/Lot.java`, `repository/LotRepository.java`, `service/LotService.java`, `service/EditionScopedLookup.java`, `dto/CreateLotDto.java`, `dto/CreateLotItemDto.java`, `dto/LotDto.java`, `controller/LotController.java`.
- Modification cross-cutting attendue : `ItemService` perd ses méthodes privées `findSellerInEdition`/`findCategoryInEdition` au profit de `EditionScopedLookup` (injecté), et `ItemMapper`/`ItemDto` gagnent les champs de lot.
- Prochain numéro de migration : `015` (dernier existant : `014-edition-dates-not-null.xml`).
- Prochain numéro de story dans `sprint-status.yaml` après celle-ci : `3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.3, lignes 1119-1146] (ACs)
- [Source: _bmad-output/planning-artifacts/epics.md#FR-022, FR-023, FR-043, FR-044, FR-045, FR-048]
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 593-599] (arborescence indicative `item/` avec `LotService`/`LotRepository`/`Lot.java`)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/mockups/mock-deposit.html, lignes 319-320, 507-534, 714-854] (CSS `.lot-badge`/`.type-selector`, structure complète du formulaire Lot, exemple de ligne de lot sauvegardé)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java, service/ItemService.java, service/TableAssignmentService.java, repository/ItemRepository.java, dto/CreateItemDto.java, dto/ItemDto.java, mapper/ItemMapper.java, controller/ItemController.java] (patterns réutilisés à l'identique)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/item/exception/ItemModificationNotAllowedException.java] (exception réutilisée, pas de nouveau type d'erreur)
- [Source: pluribourse-backend/src/main/resources/db/changelog/013-items.xml, 014-edition-dates-not-null.xml] (pattern de migration, dernier numéro utilisé)
- [Source: pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts, deposit-page.component.ts, deposit-page.component.html] (patterns Reactive Forms, signals, orchestration de page réutilisés)
- [Source: pluribourse-frontend/src/app/models/item.model.ts, services/item.service.ts]
- [Source: pluribourse-frontend/public/i18n/fr.json#volunteer.deposit.item] (convention de clés i18n existante à prolonger)
- [Source: _bmad-output/implementation-artifacts/3-2-enregistrement-darticles-assignation-automatique-de-table.md] (Dev Notes de la story précédente : bug préexistant déjà résolu, gaps mockup/AC déjà arbitrés, référence explicite à cette story comme prochaine étape)
- [Source: _bmad-output/implementation-artifacts/sprint-change-proposal-2026-07-06.md] (contexte : Story 2.3 amendée, aucun impact direct sur cette story mais even lecture de cohérence du sprint)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

Aucun — implémentation directe sans blocage nécessitant un log de debug dédié.

### Completion Notes List

- Backend : nouveau module `Lot` dans `org.pluribourse.item` (entité, `LotRepository`, DTOs, `LotService`, `LotController`). `EditionScopedLookup` et `PhaseGuard` extraits depuis les méthodes privées de `ItemService` pour être partagés avec `LotService`, comme prévu dans les Dev Notes (évite la duplication de la vérification d'appartenance à l'édition active et du verrou de phase Dépôt).
- Migration `015-lots.xml` : table `lots` (sans `deleteCascade` sur `items.lot_id`, conformément aux Dev Notes) + colonne `items.lot_id` nullable + `price` rendu nullable sur `items`.
- Atomicité (AC 6) : `LotService.create()` annoté `@Transactional`, toutes les `categoryId` du lot sont résolues via `EditionScopedLookup` avant toute persistance (Lot ou premier Item), pour éviter des verrous pessimistes inutiles sur une requête vouée à échouer.
- Assignation de table : `TableAssignmentService.assignTable()` réutilisé sans aucune modification, appelé une fois par article du lot avec sa propre catégorie (FR-023 appliqué indépendamment par article, vérifié dans `LotManagementIT`).
- Frontend : `LotFormComponent` (FormArray de 2 lignes minimum, ajout/retrait de ligne, libellé de soumission dynamique `{{ count }}`, désactivation si `< 2` lignes ou formulaire invalide). Sélecteur segmenté individuel/lot ajouté dans `DepositPageComponent` (signal `depositMode`, reset à `'individual'` à chaque changement de vendeur ou après sauvegarde d'un lot).
- Liste « Articles déposés » : lignes de lot affichées comme des lignes individuelles avec badge + prix de lot (décision de scope déjà actée dans les Dev Notes — pas d'agrégation visuelle « une ligne par lot »), actions Modifier/Supprimer masquées pour ces lignes.
- `ItemDto.price` retypé `number | null` côté frontend (impact sur `item-form.component.ts` corrigé via `price: item.price ?? 0`, sans changement de comportement runtime pour un article individuel qui a toujours un prix non nul).
- i18n : clés ajoutées dans `fr.json`/`en.json` (`volunteer.deposit.typeSelector.*`, `volunteer.deposit.item.lotForm.*`, `volunteer.deposit.item.list.lotBadge`/`lotPriceFormat`).
- Tests : `LotManagementIT` (11 tests E2E via contrôleurs, pattern `ItemManagementIT` — refus hors phase Dépôt, refus `< 2` articles, refus nom d'article vide, création avec assignation de table par article, `GET /items` renvoyant `lotId`/`lotName`/`lotPrice` et `price: null`, refus catégorie inconnue, refus catégorie d'une autre édition). Specs Vitest `LotFormComponent` (10 tests) + 4 nouveaux tests dans `DepositPageComponent.spec.ts` (bascule de mode, reset du mode au changement de vendeur, `onLotSaved()`, rendu des lignes de lot sans actions Modifier/Supprimer).
- Aucun outil de mesure de couverture automatisée configuré dans le repo (ni JaCoCo, ni `@vitest/coverage-v8`), cohérent avec l'état existant depuis la Story 3.2 — cible de 80 % visée par la conception des suites de tests plutôt que mesurée automatiquement.
- Suite de régression complète (revérifiée après application des patches de revue, 2026-07-07) : 15 classes IT backend / 203 tests verts (dont les 25 tests `ItemManagementIT` inchangés malgré l'extraction de `EditionScopedLookup`/`PhaseGuard`, et les 11 tests `LotManagementIT` après ajout de 2 tests lors de la revue) ; 345 tests frontend verts (45 fichiers, dont l'assertion mise à jour sur les actions masquées pour les lignes de lot) ; `mvn test`/`npm test` OK.

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/resources/db/changelog/015-lots.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/item/entity/Lot.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/repository/LotRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/EditionScopedLookup.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/PhaseGuard.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/LotService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/CreateLotItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/CreateLotDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/LotDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/controller/LotController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/item/LotManagementIT.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/item/entity/Item.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/service/ItemService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/dto/ItemDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/item/mapper/ItemMapper.java`

**Frontend — nouveaux fichiers**
- `pluribourse-frontend/src/app/models/lot.model.ts`
- `pluribourse-frontend/src/app/services/lot.service.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/deposit/lot-form.component.spec.ts`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/item.model.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/item-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.html`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/deposit/deposit-page.component.spec.ts`
- `pluribourse-frontend/src/app/services/item.service.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-07-07 : Implémentation complète de la story 3.3 (backend module `Lot`, extraction `EditionScopedLookup`/`PhaseGuard` partagés avec `ItemService`, frontend `LotFormComponent` + sélecteur de type + mise à jour de la liste des articles déposés, i18n, tests backend et frontend). Statut → review.
