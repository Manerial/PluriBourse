---
baseline_commit: b05d863f033304410ea870139d19a9e6d3544e36
---

# Story 4.1: Composant scanner & Scan d'articles

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a bénévole caissier,
I want scanner des articles avec un scanner code-barres USB fonctionnant quelle que soit la disposition du clavier,
so that je peux traiter les ventes rapidement sans configurer chaque poste de travail.

## Acceptance Criteria

1. Route `/volunteer/pos` (nouvelle) : à l'ouverture, le champ de saisie scanner est auto-focalisé (`queueMicrotask` après `ngAfterViewInit`, même pattern que `SellerSearchComponent`) et capture tous les événements clavier — aucun autre champ interactif ne doit voler le focus au chargement.
2. Après un clic ailleurs sur la page, si 500ms s'écoulent sans aucune frappe clavier (peu importe où sur la page), le focus revient automatiquement sur le champ scanner.
3. Un scanner USB HID envoyant un code-barres sur une disposition physique QWERTY alors que l'OS est configuré en AZERTY doit produire le bon code-barres numérique — décodage via `KeyboardEvent.code` (identifiant physique, indépendant de la disposition), jamais via `KeyboardEvent.key` (FR-034, NFR-005).
4. Code-barres valide (8 chiffres, format article existant `SSSSNNNN` : 4 chiffres numéro vendeur + 4 chiffres numéro article) scanné pour un article trouvé, non vendu → l'article est ajouté à la liste du panier côté client (nom + prix affichés) (FR-035).
5. Code-barres scanné pour un article déjà vendu (`item.sold == true`) → erreur inline « Article déjà vendu sur un autre poste. », article non ajouté (FR-036).
6. Code-barres scanné pour un article avec l'indicateur incomplet (`item.incomplete == true`) → avertissement inline affichant le commentaire de l'article (détail manquant), article ajouté quand même (FR-037).
7. Code-barres ne correspondant à aucun article de l'édition active (format invalide, vendeur/numéro d'article inexistant) → erreur inline « Article introuvable. », rien n'est ajouté.
8. `/volunteer/pos` n'est accessible qu'en phase Vente — un accès direct/marque-page hors phase Vente redirige vers `/404`, même garde que `depositPhaseGuard` (Story 3.9). Le bénévole atterrit automatiquement sur `/volunteer/pos` quand l'édition passe en phase Vente (extension de `resolveVolunteerLandingPath`).
9. Le backend refuse le scan si l'édition active n'est pas en phase Vente (422, garde défense-en-profondeur miroir de la garde frontend — le client n'est jamais fiable seul, cf. patrons de validation de l'architecture).

## Tasks / Subtasks

- [x] Backend — package `pos`, DTO, mapper, exceptions (AC: 4, 5, 6, 7, 9)
  - [x] Nouveau package `org.pluribourse.domain.pos` (`controller/`, `service/`, `dto/`, `mapper/`) — première story d'Epic 4, ce package n'existe pas encore. Aucune entité/migration Liquibase requise pour cette story : `Item.sold` et `Item.version` existent déjà (créés en amont de l'Epic 4, prêts pour F4).
  - [x] `ScanResultDto.java` (nouveau record, `org.pluribourse.domain.pos.dto`) : `(Long itemId, String name, BigDecimal price, boolean incomplete, String comment)`. Pas de champs lot (`lotId`/`lotName`) — la gestion des lots au POS est le périmètre de la Story 4.3, ne pas anticiper.
  - [x] `ScanResultMapper.java` (nouveau, `org.pluribourse.domain.pos.mapper`, `@Mapper(componentModel = "spring")`, même style que `ItemMapper`) : `@Mapping(target = "itemId", source = "id") ScanResultDto toDto(Item item);`.
  - [x] `ItemNotFoundException.java` (UPDATE, `org.pluribourse.domain.item.exception`) : ajouter un constructeur surchargé `ItemNotFoundException(String barcode)` → même `errorCode` ("item-not-found"), message `"Item not found for barcode: " + barcode`. Réutilisé pour le format invalide ET l'absence de correspondance — ne pas créer une exception dédiée pour ces deux cas (une seule erreur utilisateur : « Article introuvable »).
  - [x] `ItemAlreadySoldException.java` (nouveau, `org.pluribourse.domain.item.exception`, `extends BusinessException`) : constructeur `ItemAlreadySoldException(Long itemId)` → `HttpStatus.CONFLICT` (409), code `"item-already-sold"`, message `"Item already sold: " + itemId` — **ce code exact est déjà documenté dans l'exemple RFC 7807 de l'architecture** (`architecture.md`, section Patrons de Format), ne pas en inventer un autre.
  - [x] `PhaseGuard.java` (UPDATE, `org.pluribourse.domain.item.service`) : ajouter `requireSalePhase(Edition edition)` levant une nouvelle `SalePhaseRequiredException` si `edition.getPhase() != PhaseType.SALE`. Placée ici (pas dans un nouveau `pos.service.PhaseGuard`) pour garder tous les gardes de phase et leurs exceptions au même endroit, comme le Javadoc de tête de la classe l'indique déjà ("Shared between ItemService and LotService" — commentaire à étendre pour mentionner aussi PosService).
  - [x] `SalePhaseRequiredException.java` (nouveau, `org.pluribourse.domain.item.exception`, même modèle que `ItemModificationNotAllowedException`) : `HttpStatus.UNPROCESSABLE_ENTITY` (422), code `"sale-phase-required"`.
- [x] Backend — repository (AC: 4, 5, 6, 7)
  - [x] `ItemRepository.java` (UPDATE) : ajouter
    ```java
    @Query("SELECT i FROM Item i WHERE i.edition.id = :editionId " +
           "AND i.sellerProfile.sellerNumber = :sellerNumber AND i.itemNumber = :itemNumber")
    Optional<Item> findByEditionIdAndSellerNumberAndItemNumber(
            @Param("editionId") Long editionId, @Param("sellerNumber") int sellerNumber, @Param("itemNumber") int itemNumber);
    ```
    Une seule requête DB (pas un aller-retour via `SellerRepository` puis `ItemRepository`) — `sellerNumber` est scopé par édition (voir Dev Notes). **Pas de `JOIN FETCH`** sur `sellerProfile` : contrairement à `findAllByEditionIdForCatalog` (qui en a besoin pour éviter des lazy-loads en boucle), `ScanResultDto` n'expose aucune donnée vendeur — un `JOIN FETCH` ici chargerait une association jamais lue, à l'inverse du principe déjà appliqué ailleurs dans ce fichier.
- [x] Backend — service, contrôleur (AC: 4, 5, 6, 7, 9)
  - [x] `PosScanService.java` (nouveau, `org.pluribourse.domain.pos.service`) : méthode `@Transactional(readOnly = true) ScanResultDto scan(String barcode)`. Logique : `edition = editionService.getActiveEdition()` → `PhaseGuard.requireSalePhase(edition)` → parser `barcode` (regex stricte `^\d{8}$`, sinon lever `new ItemNotFoundException(barcode)` immédiatement, ne pas tenter `Integer.parseInt` sur une entrée non conforme) → `sellerNumber = parseInt(barcode.substring(0,4))`, `itemNumber = parseInt(barcode.substring(4,8))` → `item = itemRepository.findByEditionIdAndSellerNumberAndItemNumber(edition.getId(), sellerNumber, itemNumber).orElseThrow(() -> new ItemNotFoundException(barcode))` → si `item.isSold()` lever `new ItemAlreadySoldException(item.getId())` → sinon `mapper.toDto(item)`.
  - [x] `PosController.java` (nouveau, `org.pluribourse.domain.pos.controller`, `@RequestMapping("/pos")`) : `@GetMapping("/scan") ResponseEntity<ScanResultDto> scan(@RequestParam String barcode)`. Aucune annotation `@PreAuthorize` — hérite de la règle globale `SecurityConfig` (authentifié + non-SELLER, même chose que `ItemController`) ; ADMIN et VOLUNTEER y ont accès, comme prévu.
  - [x] `GlobalExceptionHandler` : aucune modification — `ItemAlreadySoldException`/`SalePhaseRequiredException` étendent déjà `BusinessException`, couvertes par le handler générique existant.
- [x] Frontend — routing & garde de phase (AC: 1, 8)
  - [x] `active-phase.enum.ts` (UPDATE) : `resolveVolunteerLandingPath` — ajouter `phase === ActivePhase.SALE ? '/volunteer/pos' : ...` avant le fallback `/404`. **Sans ce changement, `/volunteer/pos` reste inatteignable par la navigation normale** (redirection racine `/volunteer` et redirection réactive de `AppLayoutComponent` sur changement de phase passent toutes deux par cette fonction).
  - [x] `sale-phase.guard.ts` (nouveau, `core/guards/`) : copie exacte du pattern de `deposit-phase.guard.ts` mais teste `phase === ActivePhase.SALE` uniquement (pas de phase secondaire tolérée, contrairement à Post-vente pour le dépôt).
  - [x] `volunteer.routes.ts` (UPDATE) : ajouter la route `{ path: 'pos', canActivate: [salePhaseGuard], loadComponent: () => import('./pos/pos-page.component').then(m => m.PosPageComponent) }`.
- [x] Frontend — modèle & service (AC: 4, 5, 6, 7)
  - [x] `pos.model.ts` (nouveau, `models/`) : `ScanResult { itemId: number; name: string; price: number | null; incomplete: boolean; comment: string | null; }`. Pas de `basket.model.ts` pour cette story — le panier n'est qu'un signal client (voir Dev Notes), le modèle persisté du panier (Story 4.2) introduira sa propre interface le moment venu.
  - [x] `pos.service.ts` (nouveau, `services/`) : `scan(barcode: string): Observable<ScanResult>` → `this.http.get<ScanResult>('/api/pos/scan', { params: { barcode } })`, même style que `ItemService`.
- [x] Frontend — composant scanner (AC: 1, 2, 3)
  - [x] `features/volunteer/pos/scanner-input.component.ts/.html/.scss/.spec.ts` (nouveau) : champ de saisie isolé, auto-focus + re-focus 500ms + décodage `event.code` → chiffre. Émet `(barcodeScanned)="onScan($event)"` (string de 8 caractères) vers le parent — ne fait aucun appel HTTP lui-même. Voir Dev Notes pour l'algorithme exact (buffer interne, table `event.code` → chiffre, gestion Entrée).
- [x] Frontend — page POS (AC: 4, 5, 6, 7)
  - [x] `features/volunteer/pos/pos-page.component.ts/.html/.scss/.spec.ts` (nouveau) : héberge `<app-scanner-input>`, un signal `basket = signal<ScanResult[]>([])` (client uniquement, pas de persistance pour cette story), une liste simple affichant nom+prix par article scanné (pas de suppression de ligne — Story 4.2), et un `NotificationInlineComponent` piloté par un signal `lastScanIssue = signal<{ message: string; variant: 'warning' | 'error' } | null>(null)`. `onScan(barcode)` : appelle `posService.scan(barcode)` ; succès + `incomplete === false` → push dans `basket`, efface `lastScanIssue` ; succès + `incomplete === true` → push dans `basket` **et** affiche l'avertissement (`variant: 'warning'`, message = `volunteer.pos.warning.incomplete` interpolé avec `comment`) ; erreur HTTP → `extractErrorType()` (réutiliser l'utilitaire existant, ne pas le dupliquer) : `endsWith('/item-already-sold')` → erreur inline (`variant: 'error'`) sans ajout ; `endsWith('/item-not-found')` → erreur inline générique sans ajout ; `endsWith('/no-active-edition')` → réutiliser le message existant `volunteer.deposit.error.noActiveEdition` (même contrat d'erreur déjà géré ailleurs, ne pas dupliquer le texte) ; tout autre cas → toast erreur générique (`ToastService`, cohérence avec le reste de l'app).
- [x] i18n (AC: 1, 5, 6, 7)
  - [x] `fr.json`/`en.json` (UPDATE) : nouveau namespace `volunteer.pos.*` (imbriqué sous `volunteer`, **comme `volunteer.deposit.*`** — ne pas suivre l'exemple `pos.basket.*` top-level de `architecture.md`, qui date d'avant que la convention réelle `volunteer.*` ne se stabilise sur ce projet ; `catalog.*` reste top-level parce qu'il est partagé admin/bénévole, ce qui n'est pas le cas ici). Clés : `scanner.ariaLabel` ("Scanner ou saisir un code-barres", UX-DR10), `scanner.placeholder`, `basket.title`, `basket.empty` (texte simple, pas le composant `EmptyStateComponent` partagé — pas d'action pertinente à proposer ici, juste "aucun article scanné"), `error.alreadySold` ("Article déjà vendu sur un autre poste."), `error.notFound` ("Article introuvable."), `warning.incomplete` (avec interpolation `{{comment}}`).
- [x] Tests backend (AC: 4, 5, 6, 7, 9)
  - [x] `PosScanIT.java` (nouveau, `org.pluribourse.domain.pos`, `extends IntegrationTest`, `@TestMethodOrder(OrderAnnotation.class)`) : scénario complet, **ordre important pour éviter tout retour arrière de phase** — 1) créer édition + catégorie (Préparation) ; 2) avancer en Dépôt, créer vendeur + article (+ un second article, pour le cas incomplet) ; 3) **avant d'avancer en Vente**, scanner le code-barres de l'article → 422 `sale-phase-required` (la garde de phase doit être vérifiée en Dépôt, pas après être passé en Vente puis revenu en arrière) ; 4) avancer en Vente (`POST /admin/editions/{id}/phase/advance`, même pattern que `ItemCatalogIT`) ; 5) scanner le code-barres réel du premier article → 200 + payload correct ; 6) scanner un code-barres bien formé mais inexistant → 404 `item-not-found` ; 7) scanner un code-barres mal formé (7 chiffres, lettres) → 404 `item-not-found` ; 8) marquer le premier article vendu **directement via `ItemRepository` injecté dans le test** (aucun endpoint n'existe encore pour marquer un article vendu — ce sera la Story 4.2 — poser `item.setSold(true)` + `repository.saveAndFlush(item)` est la seule voie disponible pour ce cas de test) puis re-scanner → 409 `item-already-sold` ; 9) marquer le second article incomplet via `PATCH /items/{id}` (endpoint existant, Story 3.x) puis le scanner → 200 avec `incomplete: true` et `comment` renseigné ; 10) accès bénévole ET admin tous deux acceptés sur le scan (pas de restriction de rôle au-delà de non-SELLER) ; 11) accès non authentifié → 401/redirection login (comportement Spring Security existant, vérifier juste la non-régression).
- [x] Tests frontend
  - [x] `scanner-input.component.spec.ts` (nouveau) : auto-focus au chargement ; frappe de codes `Digit`/`Numpad` produit le bon buffer indépendamment de `event.key` simulé (simuler un `KeyboardEvent` avec un `key` volontairement incohérent avec `code` pour prouver que seul `code` est utilisé) ; `Enter`/`NumpadEnter` émet `barcodeScanned` avec le buffer et le vide ; re-focus après 500ms d'inactivité clavier simulée (`vi.useFakeTimers()`).
  - [x] `pos-page.component.spec.ts` (nouveau) : scan réussi ajoute à la liste ; scan d'article vendu affiche l'erreur inline et n'ajoute rien ; scan d'article incomplet ajoute **et** affiche l'avertissement ; scan introuvable affiche l'erreur générique.
  - [x] `sale-phase.guard.spec.ts` (nouveau, même structure que `deposit-phase.guard.spec.ts`).

### Review Findings

_Revue adversariale (Blind Hunter + Edge Case Hunter + Acceptance Auditor), code-review 2026-07-30._

- [x] [Review][Patch] **(Décision : corriger les deux gardes)** `salePhaseGuard` plante au lieu de rediriger vers `/404` quand `loadEdition()` échoue — `CurrentEditionService.loadEdition()` (`pluribourse-frontend/src/app/services/current-edition.service.ts:29-36`) mappe **toute** `HttpErrorResponse` (y compris le cas routinier 404 "pas d'édition active") vers `EMPTY` via `catchError`. `firstValueFrom()` sur un Observable qui se termine sans jamais émettre rejette avec une `EmptyError`. `sale-phase.guard.ts` fait `await firstValueFrom(currentEditionService.loadEdition())` sans `try/catch` : au lieu de résoudre vers `/404` comme prévu, la garde lève une exception non interceptée (`NavigationError` côté Router). **Ce n'est pas un bug introduit par cette story** : il est reproduit à l'identique depuis `deposit-phase.guard.ts` (Story 3.9), qui a exactement le même défaut et le même angle mort de test (son spec mocke `loadEdition()` avec `of(undefined)`, sans jamais exercer le vrai chemin `EMPTY`). Décision nécessaire : (a) corriger uniquement `sale-phase.guard.ts` (ajouter un `catch` qui résout vers `/404`), au prix d'une incohérence avec `deposit-phase.guard.ts` qui resterait cassé ; (b) corriger les deux gardes maintenant, ce qui touche un fichier de la Story 3.9 déjà livrée ; (c) ne rien corriger maintenant et consigner comme dette technique dédiée.
- [x] [Review][Patch] **(Décision : corriger maintenant)** Aucune protection contre le double-scan du même article dans le panier — `pos-page.component.ts:36`, `onScan()` fait `this.basket.update(items => [...items, result])` sans vérifier si `result.itemId` est déjà présent. Rien ne marque un article vendu avant la validation du paiement (Story 4.2), donc scanner deux fois le même code-barres (double lecture du scanner, re-scan accidentel) duplique silencieusement la ligne dans le panier, sans avertissement. Signalé indépendamment par le Blind Hunter et l'Edge Case Hunter. Décision nécessaire : empêcher/avertir le doublon fait-il partie du périmètre de cette story (l'étape d'ajout au scan), ou est-ce délibérément le périmètre de la Story 4.2 (« Gestion du panier », qui possède l'intégrité du contenu du panier — suppression de ligne, gestion des lots, validation) ?
- [x] [Review][Patch] Le minuteur de re-focus à 500ms n'est pas perpétuel — l'AC2 casse dès le premier cycle inactivité-puis-clic après le tout premier déclenchement [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:81-84`] — `armRefocusTimer()` planifie un unique `setTimeout` dont le callback ne fait que `.focus()`, sans jamais se replanifier lui-même. Seuls le constructeur (une fois) et une frappe clavier au niveau `document` réarment le minuteur. Si le bénévole clique ailleurs sans plus jamais taper, le tout premier déclenchement (500ms après le chargement ou la dernière frappe) est le dernier : un clic ultérieur sans frappe ne sera jamais suivi d'un retour de focus, puisqu'aucun minuteur n'est plus programmé pour le détecter. Aggravant : tant que le focus est perdu, les frappes du scanner n'atteignent jamais `onKeydown()` (lié uniquement à l'`<input>`, pas à `document`) — seul `onAnyKeydown()` (niveau document) les voit et réarme le minuteur, donc un scan entier effectué hors focus est silencieusement perdu et ne se traduit que par un retour de focus 500ms trop tard.
- [x] [Review][Patch] Piège clavier — `Tab`/`Shift+Tab`/collage bloqués sans condition [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:76-78`] — la branche finale d'`onKeydown()` appelle `event.preventDefault()` pour toute touche qui n'est pas un chiffre/Entrée/Retour arrière suivis, y compris `Tab`, `Shift+Tab` et les combinaisons avec modificateur (`Ctrl+V`/`Ctrl+C`). Un utilisateur au clavier seul ne peut plus sortir du champ par tabulation une fois qu'il a le focus (contrevient à l'exigence d'ordre de tabulation WCAG 2.2 AA du projet, UX-DR20), et coller un code-barres manuellement est impossible malgré l'aria-label/placeholder (« Scanner ou saisir un code-barres ») qui laisse entendre que la saisie manuelle est prise en charge.
- [x] [Review][Patch] Buffer jamais réinitialisé sur perte de focus et sans longueur maximale — un scan partiel périmé peut fusionner avec le scan suivant [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:33,56-79`] — le buffer s'accumule sans limite et n'est vidé que sur Entrée ou caractère par caractère via Retour arrière. Si le focus est perdu en plein scan (clic manuel, scan partiel n'ayant jamais reçu d'Entrée), les chiffres restants persistent ; quand le minuteur de re-focus à 500ms ramène le focus, les chiffres du **prochain** scan réel viennent s'ajouter aux résidus périmés. Si le total atteint 8 chiffres, cela résout silencieusement vers un article différent et erroné, ajouté au panier sans la moindre erreur. Par ailleurs, une touche bloquée ou un scanner défaillant fait grossir le buffer indéfiniment sans retour visuel.
- [x] [Review][Patch] Le re-focus au niveau document peut voler le focus à un autre champ [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:49-54,81-84`] — latent aujourd'hui (aucun autre champ sur `pos-page`), mais le callback d'`armRefocusTimer()` appelle `.focus()` sans condition, sans vérifier ce que `document.activeElement` est réellement. Tout futur champ ajouté à cette page (ou une boîte de dialogue ouverte par-dessus) se ferait arracher le focus en pleine saisie après 500ms.
- [x] [Review][Patch] Prix du panier affiché sans mise en forme i18n [`pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html`] — `{{ item.price }}` interpole le nombre brut sans pipe monétaire ni format i18n, contrairement à la liste équivalente ailleurs dans l'app (`volunteer.deposit.item.list.priceFormat` : `"{{ price }} €"`).
- [x] [Review][Patch] `PosScanService.scan()` sans Javadoc de méthode malgré une logique non triviale [`pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosScanService.java`] — CLAUDE.md rend la Javadoc obligatoire sur la logique non triviale ; `scan()` enchaîne résolution d'édition active, garde de phase, validation de format, parsing, recherche en base et vérification "vendu", chacune avec une branche d'exception distincte, sans commentaire de tête expliquant le contrat — contrairement à `PhaseGuard.requireSalePhase()` du même diff, qui en a une.
- [x] [Review][Defer] Aucune protection de réentrance sur `onScan()` — deux scans rapprochés peuvent se chevaucher et désynchroniser `lastScanIssue` [`pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts:33-48`] — déféré, probabilité réelle faible compte tenu de la cadence de scan typique et du budget <500ms de NFR-001 ; consigné comme dette technique.
- [x] [Review][Defer] La redirection réactive existante (`AppLayoutComponent`) quitte silencieusement `/volunteer/pos` dès que la phase change, détruisant le panier non sauvegardé sans avertissement — pattern préexistant (Story 3.9), qui s'applique désormais aussi à `/volunteer/pos` depuis l'extension de `resolveVolunteerLandingPath`. Explicitement le périmètre de la Story 4.6 (« Gestion du changement de phase dans le composant POS — côté client » : événement SSE `basket-cancelled`, toast persistant, panier vidé explicitement, scanner désactivé) — déféré à cette story selon le découpage des epics.

### Review Findings — 2ᵉ passe (2026-07-30)

_Revue adversariale relancée sur le diff patché (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Deux des correctifs du round 1 avaient eux-mêmes introduit de nouveaux défauts — exactement ce qu'une 2ᵉ passe doit attraper._

- [x] [Review][Patch] Javadoc de `PosScanService.scan()` pointe vers la mauvaise exception [`pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosScanService.java`] — `({@link ItemAlreadySoldException 422 sale-phase-required} otherwise)` alors que `sale-phase-required` (422) est levée par `SalePhaseRequiredException`, pas par `ItemAlreadySoldException` (409, `item-already-sold`) — confusion directe entre deux exceptions du même diff, documentation trompeuse.
- [x] [Review][Patch] Le collage manuel (Ctrl+V) ne fonctionne pas réellement, et les combinaisons modificateur natives désynchronisent l'affichage [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:64-68`] — le correctif round 1 laisse passer `ctrlKey`/`metaKey` sans `preventDefault()`, mais aucun handler `(paste)` n'est câblé vers le signal `buffer` : le DOM natif reçoit le texte collé, le signal ne bouge pas, et le prochain rendu (`[value]="buffer()"`) écrase silencieusement ce qui a été collé. Par ailleurs, `Ctrl+Retour arrière` (suppression de mot native du navigateur) modifie la valeur du DOM sans jamais toucher `buffer`, désynchronisant l'affichage de ce qui sera réellement soumis à l'Entrée suivante.
- [x] [Review][Patch] Le buffer se vide trop agressivement et casse la saisie manuelle lente [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:102-105`] — le vidage anti-fusion ajouté au round 1 partage le même délai de 500ms que le re-focus. Un scanner envoie ses 8 chiffres en quelques dizaines de ms (jamais affecté), mais un bénévole tapant à la main avec une pause de plus de 500ms entre deux chiffres (plausible : relire l'étiquette, hésitation) se fait effacer son buffer sans le moindre retour visuel ou message.
- [x] [Review][Patch] `EDITABLE_TAGS` ne couvre que les balises natives, pas les widgets composites Material [`pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts:24`] — un `mat-select` ouvert ou une CDK overlay/dialog ne sont pas des balises `<select>`/`<dialog>` natives mais des `<div role="listbox">`/`<div role="dialog">`. Une future story ajoutant un tel composant sur cette page se ferait voler le focus par le minuteur de 500ms en pleine interaction.
- [x] [Review][Patch] Course TOCTOU sur la garde anti-doublon [`pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts:33-48`] — `onScan()` est asynchrone et n'a pas de garde de réentrance ; deux scans rapprochés du même code-barres (gâchette du scanner appuyée deux fois, lecture répétée) peuvent tous deux lire `basket()` avant qu'aucun n'y ait écrit, contournant la garde anti-doublon ajoutée au round 1. Ce finding remplace le defer « pas de garde de réentrance » du round 1 (upgradé en patch, corrige les deux à la fois).
- [x] [Review][Patch] Duplication intégrale du correctif entre les deux gardes de phase [`pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts`, `sale-phase.guard.ts`] — le bloc `try/catch` autour de `loadEdition()` est copié-collé mot pour mot dans les deux fichiers ; la prochaine correction devra être appliquée deux fois, avec le risque d'oublier l'un des deux (déjà arrivé une fois pour ce bug précis).
- [x] [Review][Patch] `@for (item of basket(); track $index)` cassera silencieusement dès qu'une ligne pourra être retirée [`pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html`] — la Story 4.2 introduira le retrait d'article ; `track item.itemId` (stable, déjà disponible) évite un mauvais réattachement DOM par ligne.
- [x] [Review][Defer] Le `catch` des deux gardes de phase confond toute panne HTTP (500, timeout réseau) avec le cas routinier « pas d'édition active » (404), redirigeant les deux vers `/404` sans distinction [`current-edition.service.ts:29-36`] — signalé une 2ᵉ fois par l'Edge Case Hunter. Corriger proprement nécessiterait de revoir le contrat d'erreur de `CurrentEditionService.loadEdition()` lui-même, utilisé par bien plus que ces deux gardes — hors périmètre proportionné d'un correctif de garde. À traiter dans une story dédiée à la résilience réseau si ce silence devient un vrai point de friction terrain.
- [x] [Review][Defer] Compromis assumé : `Tab`/collage échappent à la capture clavier d'AC1, et l'exception anti-vol-de-focus échappe au « peu importe où » d'AC2 — les deux sont des choix délibérés déjà raisonnés au round 1 (l'alternative, un piège clavier violant WCAG 2.2 AA, était strictement pire) ; l'Acceptance Auditor de la 2ᵉ passe les a re-signalés pour transparence, aucun changement nécessaire.
- [x] [Review][Defer] Doublon de scan inter-postes (deux sessions distinctes scannent le même article) — conforme au design de concurrence en deux temps déjà documenté dans l'architecture (le vrai conflit est détecté à la validation du paiement, Story 4.2, pas au scan) ; re-signalé par l'Edge Case Hunter, aucune action nouvelle.

## Dev Notes

### Ce qui existe déjà — ne pas réinventer

- `Item.sold` (boolean) et `Item.@Version` existent déjà sur l'entité (`pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java:43-59`) — préparés en amont pour l'Epic 4. Cette story **lit** `sold`, ne le modifie jamais (aucun endpoint ne le fait encore : ce sera la Story 4.2, validation du panier).
- Le code-barres n'est **jamais persisté** — toujours dérivé de `sellerProfile.sellerNumber` (4 chiffres) + `itemNumber` (4 chiffres), voir `Item.getBarcode()`/`getFormattedBarcode()`. `sellerNumber` est un compteur séquentiel **par édition** (`SellerService`, `edition.nextSellerNumber`), donc `(editionId, sellerNumber, itemNumber)` identifie un article de façon unique — la requête `findByEditionIdAndSellerNumberAndItemNumber` ci-dessus est la seule façon correcte de résoudre un scan.
- `EditionService.getActiveEdition()` (lève `NoActiveEditionException`, 404, code `no-active-edition`) est le point d'entrée standard pour résoudre l'édition active — déjà utilisé par `ItemService`, `SellerService`. Le frontend gère déjà ce contrat d'erreur (`SellerSearchComponent`, `volunteer.deposit.error.noActiveEdition`) — répliquer le même traitement plutôt que d'introduire un nouveau message.
- `PhaseGuard` (`org.pluribourse.domain.item.service.PhaseGuard`) est le point d'extension pour les règles de phase — `requireDepositPhase`/`requireDepositOrPostSalePhase` existent déjà. Ajouter `requireSalePhase` au même endroit, pas un nouveau garde dans `pos.service`.
- `extractErrorType(HttpErrorResponse)` (`shared/http-error.util.ts`) est l'utilitaire déjà utilisé partout pour distinguer les types d'erreur RFC 7807 côté Angular (voir `seller-search.component.ts`, `deposit-page.component.ts`) — le réutiliser tel quel.
- Le pattern auto-focus (`viewChild<ElementRef<HTMLInputElement>>`, `queueMicrotask(() => ref()?.nativeElement.focus())` dans `ngAfterViewInit`) est établi par `SellerSearchComponent` — **le déferrement via `queueMicrotask` est nécessaire**, pas cosmétique : un focus synchrone dans `ngAfterViewInit` mute les host bindings placeholder/label de `MatFormField` en plein cycle de détection de changement et déclenche `ExpressionChangedAfterItHasBeenCheckedError` (leçon déjà tirée sur ce composant, ne pas la re-découvrir).

### Package `pos` — première story de l'Epic 4

Aucun package `org.pluribourse.domain.pos` n'existe avant cette story. La structure cible (`controller/`, `service/`, `dto/`, `mapper/`) suit le même patron que `edition/`, `seller/`, `item/`. **`entity/`, `repository/` n'existent pas encore dans ce package** — `Basket`/`Sale` (entités persistées du panier, cf. `architecture.md`) sont explicitement le périmètre de la **Story 4.2**, pas de celle-ci (cf. dépendance documentée dans `sprint-status.yaml` : la Story 2.8 « ne peut pas démarrer avant 4.1 → 4.2 » parce que l'entité panier POS n'existe qu'après 4.2). Ne pas créer `Basket`/`BasketItem`/`Sale` maintenant — le panier de cette story est **uniquement un signal Angular côté client**, non persisté, cohérent avec NFR-006 qui ne s'applique qu'à partir du moment où une transaction est en cours (introduite en 4.2).

### Décodage clavier AZERTY/QWERTY (AC 3) — algorithme précis

Le risque concret n'est pas les lettres mais **les chiffres** : sur un clavier AZERTY, la rangée de chiffres produit des symboles (`&`, `é`, `"`, `'`, etc.) sans la touche Majuscule, alors qu'un scanner USB HID configuré en disposition US envoie les événements clavier sans Majuscule pour les chiffres. `KeyboardEvent.key` refléterait donc le symbole AZERTY, pas le chiffre — **`KeyboardEvent.code`** (identifiant physique, indépendant de l'OS) doit être utilisé à la place :

```typescript
const CODE_TO_DIGIT: Record<string, string> = {
  Digit0: '0', Digit1: '1', Digit2: '2', Digit3: '3', Digit4: '4',
  Digit5: '5', Digit6: '6', Digit7: '7', Digit8: '8', Digit9: '9',
  Numpad0: '0', Numpad1: '1', Numpad2: '2', Numpad3: '3', Numpad4: '4',
  Numpad5: '5', Numpad6: '6', Numpad7: '7', Numpad8: '8', Numpad9: '9',
};
```

Sur `keydown` du champ scanner : si `event.code` est une clé de cette table, `event.preventDefault()` (empêcher la vraie valeur AZERTY d'atteindre le DOM) et ajouter le chiffre mappé à un buffer interne (signal `buffer = signal('')`), affiché en le liant à l'input (pas la valeur native du champ). Si `event.code === 'Enter' || event.code === 'NumpadEnter'`, émettre le buffer complet via `barcodeScanned` et le vider — **pas de debounce** (UX-DR10 explicite). Toute autre touche est ignorée (`preventDefault` également, pour empêcher toute pollution du buffer par une touche non numérique).

### Re-focus après 500ms d'inactivité clavier (AC 2)

```typescript
@HostListener('document:keydown')
onAnyKeydown(): void {
  clearTimeout(this.refocusTimer);
  this.refocusTimer = setTimeout(() => this.scannerInput()?.nativeElement.focus(), 500);
}
```
Écouter au niveau `document` (pas seulement l'input) — le déclencheur est **n'importe quelle frappe clavier sur la page**, pas seulement dans le champ scanner (le bénévole peut avoir cliqué ailleurs). Réarmer à chaque frappe (pas un minuteur unique). Nettoyer via `DestroyRef().onDestroy(() => clearTimeout(...))` — pattern déjà utilisé (`takeUntilDestroyed`) ailleurs dans le projet pour les abonnements, ici adapté pour un `setTimeout` brut.

### Tester le cas « article déjà vendu » sans endpoint de vente

Aucun endpoint ne permet aujourd'hui de marquer un article vendu (`Item.sold`) — ce sera la Story 4.2. Le test `PosScanIT` doit injecter `ItemRepository` directement pour poser `sold = true` avant de re-scanner via le contrôleur. Ce n'est pas une entorse à la philosophie « E2E par les contrôleurs uniquement » : l'assertion elle-même passe bien par le contrôleur (`GET /pos/scan`), seule la **préparation** de l'état contourne l'absence d'API — même logique que d'autres IT du projet qui construisent leur fixture via repository quand aucune route ne le permet encore.

### Project Structure Notes

- **`architecture.md` décrit une arborescence backend `org.pluribourse.{feature}.{layer}` et frontend `components/`/`services/`/`models/` — le code réel diverge** : le backend utilise `org.pluribourse.domain.{feature}.{layer}` (préfixe `domain` supplémentaire) et le frontend utilise `features/{role}/{feature}/` (pas `components/`), avec `services/` et `models/` plats à la racine de `app/`, tels quels. Cette story suit le code réel, pas le document d'architecture littéral :
  - Backend : `org.pluribourse.domain.pos.{controller,service,dto,mapper}`.
  - Frontend : `features/volunteer/pos/` (miroir exact de `features/volunteer/deposit/`, puisque le POS est aussi un flux 100% bénévole) — pas `components/pos/`.
- Clés i18n sous `volunteer.pos.*` (imbriquées), pas `pos.*` top-level — voir justification dans la tâche i18n ci-dessus.
- Aucune migration Liquibase, aucune nouvelle entité — cette story est purement lecture (scan = consultation d'un article existant + vérification d'état).

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/ItemNotFoundException.java`, `ItemModificationNotAllowedException.java` (modèle pour `SalePhaseRequiredException`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java` (référence, ne pas modifier)
- `pluribourse-frontend/src/app/models/active-phase.enum.ts`
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts` (modèle exact pour `sale-phase.guard.ts`)
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.ts` (modèle auto-focus)
- `pluribourse-frontend/src/app/shared/http-error.util.ts` (référence, ne pas modifier)
- `pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.ts` (référence, ne pas modifier)
- `pluribourse-frontend/public/i18n/fr.json`, `en.json`

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.1] — ACs source (FR-033 à FR-037, FR-034 en particulier)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR10] — spécification exacte du composant de saisie scanner
- [Source: _bmad-output/planning-artifacts/architecture.md#Concurrence — POS] — modèle `@Version`, vérification d'état vendu, exemple RFC 7807 `item-already-sold`
- [Source: _bmad-output/planning-artifacts/architecture.md#Architecture Frontend] — décodage clavier dans le composant Angular, pas côté backend
- [Source: _bmad-output/implementation-artifacts/sprint-status.yaml] — dépendance documentée 2.8 → 4.1 → 4.2 (entité panier POS pas encore créée)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/entity/Item.java, repository/ItemRepository.java, service/ItemService.java, service/PhaseGuard.java, entity/SellerProfile.java] — lus intégralement
- [Source: pluribourse-frontend/src/app/features/volunteer/deposit/seller-search.component.ts, deposit-page.component.ts, volunteer.routes.ts; core/guards/deposit-phase.guard.ts; models/active-phase.enum.ts] — lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/item/ItemCatalogIT.java] — modèle de scénario E2E (édition → catégories → avance de phase → sessions multi-rôles)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw -q compile` → BUILD SUCCESS après l'ajout du package `pos`.
- `./mvnw -q -Dtest=PosScanIT test` → 12/12 passed.
- `./mvnw -q test` (suite complète backend) → 340/340 passed (328 existants + 12 nouveaux), BUILD SUCCESS, aucune régression.
- `npm test` (suite complète frontend) → 54 fichiers, 479/479 passed (462 existants + 17 nouveaux), aucune régression.
- `npm run build` (Angular, mode production) → succès, aucune erreur TypeScript.

### Completion Notes List

- Backend : nouveau package `org.pluribourse.domain.pos` (`controller`, `service`, `dto`, `mapper`) implémenté exactement selon les Dev Notes — aucune entité/migration Liquibase, `Item.sold`/`Item.@Version` réutilisés tels quels.
- `PhaseGuard.requireSalePhase()` ajouté au garde de phase existant (`item.service`), pas de nouveau garde dédié `pos` — conforme à la décision documentée.
- `ItemNotFoundException` étendue d'un constructeur `String barcode` (réutilisé pour format invalide ET absence de correspondance) ; `ItemAlreadySoldException` (409) et `SalePhaseRequiredException` (422) nouvelles, mêmes modèles que les exceptions existantes du package `item.exception`.
- `ItemRepository.findByEditionIdAndSellerNumberAndItemNumber` : requête JPQL explicite, sans `JOIN FETCH` sur `sellerProfile` (non lu par `ScanResultDto`), conformément à la correction apportée lors de la revue de la story.
- Frontend : `resolveVolunteerLandingPath` étendu (phase Vente → `/volunteer/pos`), sinon la route restait inatteignable par la navigation normale — corrigé comme prévu dans les Dev Notes.
- `scanner-input.component` : décodage `KeyboardEvent.code` (Digit0-9/Numpad0-9) indépendant de `event.key`, buffer interne (signal), `Backspace` géré pour la correction manuelle (ajout non listé explicitement dans les tâches mais nécessaire : l'aria-label « Scanner ou saisir un code-barres » implique une saisie manuelle possible). Auto-focus (`queueMicrotask`) + re-focus 500ms (`@HostListener('document:keydown')`, timer réarmé à chaque frappe, nettoyé via `DestroyRef`).
- `pos-page.component` : panier 100% signal client (`ScanResult[]`), aucune entité persistée — conforme au périmètre de la story (Basket/Sale = Story 4.2). Ajout d'une clé i18n `volunteer.pos.error.generic` non explicitement listée dans la tâche i18n mais requise par la même tâche de gestion d'erreur (toast générique pour tout autre cas HTTP).
- Tests backend (`PosScanIT`) : scénario en 12 étapes suivant l'ordre imposé par les Dev Notes (garde de phase testée en Dépôt, avant l'avance vers Vente, pas après retour arrière) ; le marquage "vendu" est fait directement via `ItemRepository` injecté (aucun endpoint de vente n'existe encore, ce sera la Story 4.2), documenté comme décision assumée.
- Tests frontend : `scanner-input.component.spec.ts` prouve explicitement que seul `event.code` pilote le décodage (KeyboardEvent avec `key` volontairement incohérent avec `code`) ; re-focus testé avec `vi.useFakeTimers()`. `pos.service.spec.ts` ajouté en plus des tests explicitement listés (cohérence avec la convention du projet : chaque service a son spec).
- Aucune déviation par rapport au plan de la story au-delà des deux ajouts mineurs documentés ci-dessus (support `Backspace`, clé i18n `error.generic`), tous deux nécessaires à la cohérence des tâches déjà écrites.

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/dto/ScanResultDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/mapper/ScanResultMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosScanService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/controller/PosController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/ItemAlreadySoldException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/SalePhaseRequiredException.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosScanIT.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/exception/ItemNotFoundException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/PhaseGuard.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/item/repository/ItemRepository.java`

**Frontend — nouveaux fichiers**
- `pluribourse-frontend/src/app/models/pos.model.ts`
- `pluribourse-frontend/src/app/services/pos.service.ts`
- `pluribourse-frontend/src/app/services/pos.service.spec.ts`
- `pluribourse-frontend/src/app/core/guards/sale-phase.guard.ts`
- `pluribourse-frontend/src/app/core/guards/sale-phase.guard.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.html`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/pos/scanner-input.component.spec.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.ts`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.html`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.scss`
- `pluribourse-frontend/src/app/features/volunteer/pos/pos-page.component.spec.ts`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/active-phase.enum.ts`
- `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.ts` (revue de code : correction du même bug que `sale-phase.guard.ts`, puis extraction de `edition-load.util.ts` en 2ᵉ passe)
- `pluribourse-frontend/src/app/core/guards/deposit-phase.guard.spec.ts` (revue de code : nouveau test de régression)

**Frontend — nouveaux fichiers (2ᵉ passe de revue)**
- `pluribourse-frontend/src/app/core/guards/edition-load.util.ts`
- `pluribourse-frontend/src/app/core/guards/edition-load.util.spec.ts`

**Backend — fichiers modifiés (2ᵉ passe de revue)**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/PosScanService.java` (correction Javadoc)

## Change Log

- 2026-07-30 : Implémentation complète de la Story 4.1 (composant scanner USB HID + endpoint de scan POS, gestion AZERTY/QWERTY, panier client, garde de phase Vente). Nouveau package backend `pos` (scan uniquement, pas de `Basket`/`Sale` — Story 4.2) ; `PhaseGuard.requireSalePhase()`, `ItemAlreadySoldException`, `SalePhaseRequiredException` nouveaux ; `ItemRepository` étendu d'une requête de résolution barcode → article. Frontend : route `/volunteer/pos` (+ `salePhaseGuard`, extension de `resolveVolunteerLandingPath`), `scanner-input.component` (décodage clavier indépendant de la disposition OS, auto-focus + re-focus 500ms), `pos-page.component` (panier signal, gestion des erreurs/avertissements inline). 340/340 tests backend et 479/479 tests frontend passent, aucune régression. Statut → `review`.
- 2026-07-30 : Revue de code adversariale (Blind Hunter + Edge Case Hunter + Acceptance Auditor) — 2 decision-needed résolues (corriger les deux gardes de phase pour le bug `loadEdition()`/`EmptyError` ; corriger le doublon de scan dans le panier maintenant plutôt que déférer à la Story 4.2), 8 patch appliqués, 2 defer consignés dans `deferred-work.md`, 7 rejetés comme bruit (dont deux faux positifs de l'agent sans contexte projet — incohérence de route `/pos` et absence de `@PreAuthorize`, tous deux couverts par des mécanismes globaux existants déjà vérifiés). Correctifs : minuteur de re-focus rendu perpétuel (au lieu d'un déclenchement unique, cassant l'AC2 après le premier cycle) ; piège clavier levé (`Tab`/`Shift+Tab`/collage ne sont plus bloqués) ; buffer vidé sur inactivité + plafonné à 8 chiffres (empêchait la fusion d'un scan partiel périmé avec le scan suivant) ; re-focus ne vole plus le focus à un autre champ actif ; garde contre le doublon de scan dans le panier ; `deposit-phase.guard.ts`/`sale-phase.guard.ts` ne plantent plus quand `loadEdition()` échoue ; format i18n du prix harmonisé avec le reste de l'app ; Javadoc ajoutée sur `PosScanService.scan()`. 340/340 tests backend, 488/488 tests frontend (54 fichiers), aucune régression. Statut → `done`.
- 2026-07-30 : 2ᵉ passe de revue adversariale sur le diff patché — deux des correctifs du round 1 avaient eux-mêmes introduit de nouveaux défauts (exactement ce qu'une 2ᵉ passe doit attraper). 0 decision-needed, 7 patch appliqués, 2 defer supplémentaires consignés, plusieurs points rejetés (dont des re-signalements de compromis déjà assumés au round 1 — Tab/collage vs AC1, exception anti-vol-de-focus vs AC2 — et le doublon de scan inter-postes, conforme au design de concurrence en deux temps de l'architecture). Correctifs : Javadoc de `PosScanService.scan()` corrigée (pointait vers la mauvaise exception) ; collage manuel (Ctrl+V) réellement câblé via un handler `(paste)` dédié plutôt qu'un simple laisser-passer inopérant, et les combinaisons modificateur natives (ex. Ctrl+Retour arrière) bloquées pour ne plus désynchroniser l'affichage du buffer réel ; vidage du buffer sur inactivité découplé du minuteur de re-focus (nouveau délai de 3000ms dédié, au lieu de 500ms, pour ne pas casser la saisie manuelle lente) ; protection anti-vol-de-focus étendue aux widgets composites ARIA (`role="dialog"`/`"listbox"`/`"combobox"`...), pas seulement aux balises natives ; garde de réentrance sur `onScan()` (corrige la course TOCTOU sur la garde anti-doublon, remplace le defer du round 1) ; logique de garde de phase partagée extraite dans `edition-load.util.ts` (élimine la duplication entre `deposit-phase.guard.ts`/`sale-phase.guard.ts`) ; `track item.itemId` au lieu de `track $index` dans la liste du panier (anticipe le retrait de ligne en Story 4.2). 340/340 tests backend, 495/495 tests frontend (55 fichiers), aucune régression. Statut → `done` (confirmé).
