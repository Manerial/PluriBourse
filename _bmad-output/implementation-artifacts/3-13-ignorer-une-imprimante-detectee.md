---
baseline_commit: 10910332f6c2f81052e2e99207d8c7766989bf76
---

# Story 3.13: Ignorer une imprimante détectée

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want ignorer une imprimante détectée par PrinterBridge mais que je ne souhaite pas enregistrer (imprimante d'un voisin, imprimante temporaire),
so that elle cesse d'encombrer la liste de découverte à chaque scan, tout en gardant la possibilité de revenir sur ce choix.

## Acceptance Criteria

1. `POST /admin/printers/discovered/{printerBridgeId}/ignore` (ADMIN uniquement, nouveau) : ajoute `printerBridgeId` au registre des imprimantes ignorées (nouvelle table `ignored_printers`). 204 No Content. Si `printerBridgeId` correspond à une imprimante déjà enregistrée dans `printers`, échoue en 422 en réutilisant `InvalidPrinterConfigurationException` (même pattern que la validation de nom dupliqué en Story 3.4/3.8) — l'action ne s'applique qu'aux imprimantes détectées et non enregistrées.
2. `GET /admin/printers/discovered` (existant, Story 3.11) : exclut désormais aussi les imprimantes dont le `printerBridgeId` figure dans `ignored_printers`, en plus du filtre existant sur les imprimantes déjà enregistrées dans `printers`.
3. `GET /admin/printers/ignored` (ADMIN uniquement, nouveau) : retourne `List<IgnoredPrinterDto>` (`printerBridgeId`, `name`, `ignoredAt`). Le `name` est résolu en croisant avec la liste **brute, non filtrée** retournée par `PrinterBridgeClient.discover()` (pas `PrinterService.discover()`, qui exclurait justement les entrées qu'on cherche à afficher). Si PrinterBridge est injoignable au moment de cet appel, `name` vaut `null` pour les entrées concernées plutôt que de faire échouer toute la requête en 503 — l'admin doit pouvoir consulter/réactiver ses imprimantes ignorées même si PrinterBridge est temporairement indisponible.
4. `DELETE /admin/printers/ignored/{printerBridgeId}` (ADMIN uniquement, nouveau) : retire l'entrée du registre des imprimantes ignorées ("réactiver"). 204 No Content. 404 (nouvelle `IgnoredPrinterNotFoundException`) si l'id n'est pas dans le registre des imprimantes ignorées.
5. `IgnoredPrinter` (nouvelle entité, table `ignored_printers`) : `id` (PK auto), `printerBridgeId` (VARCHAR(32), unique, not null — même longueur que `Printer.printerBridgeId`), `ignoredAt` (`LocalDate`, not null).
6. Frontend `/admin/printers`, dialog "Ajouter une imprimante" (`printer-form.component`) : le `mat-select` unique de la Story 3.11 est remplacé par une liste — chaque imprimante détectée est une ligne (nom, statut) avec deux actions : **"Enregistrer"** et **"Ignorer"**. Cliquer "Enregistrer" affiche le sous-formulaire existant (nom d'affichage, largeur si THERMAL) pour cette imprimante précise, avec le bandeau d'erreur de soumission (`error()`) qui n'apparaît que dans cette vue sous-formulaire, jamais dans la vue liste. Cliquer "Ignorer" appelle immédiatement le nouvel endpoint, retire la ligne de la liste affichée, toast succès/erreur — **sans fermer le dialog** (l'admin peut enchaîner sur une autre imprimante). Un bouton "Retour" depuis le sous-formulaire revient à la liste sans fermer le dialog. La vue liste conserve elle-même un bouton "Annuler" explicite en pied de dialog (en plus de la croix de fermeture du `dialog-shell`), cohérent avec le reste des dialogs du projet.
7. Frontend `/admin/printers`, `printer-list.component` : nouvelle section repliable "Imprimantes ignorées" (`mat-expansion-panel`, **repliée par défaut**) affichée après le bloc principal (tableau ou état vide), **indépendamment** de l'état de la liste principale (elle apparaît que la liste principale soit vide ou peuplée), chargée via `GET /admin/printers/ignored`. Chaque ligne : nom (ou `printerBridgeId` si `name` est `null`), date d'ajout, bouton "Réactiver". Réactiver appelle `DELETE /admin/printers/ignored/{printerBridgeId}`, retire la ligne, toast succès/erreur.
8. Les trois nouvelles routes (`POST .../ignore`, `GET /ignored`, `DELETE /ignored/{id}`) sont protégées par le même `@PreAuthorize("hasRole('ADMIN')")` de classe que le reste de `PrinterController` — aucune annotation supplémentaire nécessaire, à vérifier par un test E2E confirmant le 403 bénévole (cohérent avec Stories 3.8/3.11).

## Tasks / Subtasks

- [x] Backend — entité, migration, repository (AC: 5)
  - [x] Nouvelle migration `019-ignored-printers.xml` : `createTable ignored_printers` (`id` BIGINT PK auto-incrémenté, `printer_bridge_id` VARCHAR(32) NOT NULL UNIQUE, `ignored_at` DATE NOT NULL). Ajouter l'`<include>` dans `db.changelog-master.xml`, après `018-printer-bridge-id.xml`.
  - [x] `IgnoredPrinter.java` (nouveau, `org.pluribourse.domain.print.entity`) : `@Entity @Table(name = "ignored_printers") @Getter @Setter @NoArgsConstructor` (même style que `Printer.java` — pas de `@Version`, pas de mise à jour concurrente possible sur cette entité). Champs : `Long id` (`@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`), `String printerBridgeId` (`@Column(name = "printer_bridge_id", nullable = false, unique = true, length = 32)`), `LocalDate ignoredAt` (`@Column(name = "ignored_at", nullable = false)`).
  - [x] `IgnoredPrinterRepository.java` (nouveau, `org.pluribourse.domain.print.repository`) : `extends JpaRepository<IgnoredPrinter, Long>`. Ajouter `@Query("select p.printerBridgeId from IgnoredPrinter p") Set<String> findAllPrinterBridgeIds()` — **copier exactement** le pattern de `PrinterRepository.findAllPrinterBridgeIds()` (voir Dev Notes, la convention dérivée Spring Data a échoué pour ce même besoin en Story 3.11). Ajouter aussi `Optional<IgnoredPrinter> findByPrinterBridgeId(String printerBridgeId)`.
- [x] Backend — DTO, exception (AC: 1, 3, 4)
  - [x] `IgnoredPrinterDto.java` (nouveau record, `org.pluribourse.domain.print.dto`) : `(String printerBridgeId, String name, LocalDate ignoredAt)`.
  - [x] `IgnoredPrinterNotFoundException.java` (nouveau, `org.pluribourse.domain.print.exception`, `extends BusinessException`) : `HttpStatus.NOT_FOUND`, code `ignored-printer-not-found`. Modèle exact : `PrinterNotFoundException.java`, sauf que le constructeur prend un `String printerBridgeId` (pas un `Long id`).
  - [x] **Ne pas créer** de nouvelle exception pour le cas "déjà enregistrée" (AC1) — réutiliser `InvalidPrinterConfigurationException` existante, exactement comme `PrinterService.create()` le fait déjà pour un nom dupliqué.
- [x] Backend — service (AC: 1, 2, 3, 4)
  - [x] `PrinterService.java` (UPDATE, injecter `IgnoredPrinterRepository` en constructeur — `@RequiredArgsConstructor` l'ajoute automatiquement) : `discover()` étend son filtre existant avec les imprimantes ignorées (voir Dev Notes pour le diff exact attendu).
  - [x] Nouvelle méthode `void ignore(String printerBridgeId)` : si `repository.findAllPrinterBridgeIds().contains(printerBridgeId)` → lève `new InvalidPrinterConfigurationException("Printer '" + printerBridgeId + "' is already registered and cannot be ignored.")`. Sinon, si `ignoredPrinterRepository.findByPrinterBridgeId(printerBridgeId)` est déjà présent → ne rien faire (idempotent, évite un conflit sur la contrainte `unique` si l'admin clique deux fois). Sinon, `save(new IgnoredPrinter avec printerBridgeId, ignoredAt = LocalDate.now())`. **Décision assumée, pas un oubli** : cette méthode ne revérifie pas en direct auprès de `printerBridgeClient.discover()` que l'id est toujours détecté par PrinterBridge au moment de l'appel — ajouter cette vérification créerait un aller-retour PrinterBridge supplémentaire et un couplage inutile pour un cas limite (id plus détecté entre l'affichage de la liste et le clic) sans valeur ajoutée réelle.
  - [x] Nouvelle méthode `List<IgnoredPrinterDto> listIgnored()` : charge toutes les `IgnoredPrinter`. Tente `printerBridgeClient.discover()` (liste brute, **pas** `this.discover()`) pour résoudre `name` par `printerBridgeId` — construire une `Map<String, String>` id→nom à partir de ce résultat. Si `PrinterBridgeUnavailableException` est levée par cet appel, la catcher **localement** (ne jamais la laisser se propager depuis cette méthode) et traiter la map comme vide, donnant `name = null` à chaque `IgnoredPrinterDto`.
  - [x] Nouvelle méthode `void reactivate(String printerBridgeId)` : `ignoredPrinterRepository.findByPrinterBridgeId(printerBridgeId).orElseThrow(() -> new IgnoredPrinterNotFoundException(printerBridgeId))`, puis `ignoredPrinterRepository.delete(...)`.
- [x] Backend — contrôleur (AC: 1, 3, 4, 8)
  - [x] `PrinterController.java` (UPDATE) : ajouter `@PostMapping("/discovered/{printerBridgeId}/ignore")` → `ResponseEntity<Void>` (204, `.noContent().build()`, appelle `service.ignore(printerBridgeId)`) ; `@GetMapping("/ignored")` → `List<IgnoredPrinterDto>` (`service.listIgnored()`) ; `@DeleteMapping("/ignored/{printerBridgeId}")` → `ResponseEntity<Void>` (204, `service.reactivate(printerBridgeId)`). Aucune nouvelle annotation `@PreAuthorize` — héritée de la classe.
  - [x] `GlobalExceptionHandler` : aucune modification — `IgnoredPrinterNotFoundException`/`InvalidPrinterConfigurationException` étendent déjà `BusinessException`, couvertes par le handler générique existant.
- [x] Frontend — modèle & service (AC: 1, 3, 4)
  - [x] `printer-registry.model.ts` (UPDATE) : ajouter `IgnoredPrinter { printerBridgeId: string; name: string | null; ignoredAt: string; }`.
  - [x] `printer-registry.service.ts` (UPDATE) : ajouter `ignore(printerBridgeId: string): Observable<void>` (`POST /api/admin/printers/discovered/${printerBridgeId}/ignore`, corps vide), `listIgnored(): Observable<IgnoredPrinter[]>` (`GET /api/admin/printers/ignored`), `reactivate(printerBridgeId: string): Observable<void>` (`DELETE /api/admin/printers/ignored/${printerBridgeId}`).
- [x] Frontend — dialog de création : liste + actions par ligne (AC: 6)
  - [x] `printer-form.component.ts` (UPDATE) : injecter `ToastService` (absent aujourd'hui de ce composant — `inject(ToastService)`, même import que dans `printer-list.component.ts`). Nouveau signal `selectedPrinter = signal<DiscoveredPrinter | null>(null)`. Nouvelle méthode `selectRow(printer: DiscoveredPrinter): void` — appelle `form.controls.printerBridgeId.setValue(printer.printerBridgeId)` (déclenche `applySelectedPrinter` via le `valueChanges` existant, **ne pas supprimer** cet abonnement) puis `selectedPrinter.set(printer)`. Nouvelle méthode `backToList(): void` — `selectedPrinter.set(null)`, réinitialise `form.reset()` (name, widthMm). **Ne pas réinitialiser `selectedType` manuellement** : `form.reset()` remet `printerBridgeId` à `''`, ce qui redéclenche son `valueChanges` existant → `applySelectedPrinter('')` → `selectedType.set(null)` automatiquement (aucune imprimante ne correspond à `''` dans `discoveredPrinters()`) ; un reset manuel additionnel serait redondant, et passer `{ emitEvent: false }` à `form.reset()` casserait ce mécanisme. Nouvelle méthode `async ignoreRow(printer: DiscoveredPrinter): Promise<void>` — appelle `printerRegistryService.ignore(printer.printerBridgeId)`, puis `discoveredPrinters.update(list => list.filter(p => p.printerBridgeId !== printer.printerBridgeId))`, toast succès (`toast.showSuccess`) ; en cas d'échec, toast erreur — le dialog reste ouvert dans tous les cas.
  - [x] `printer-form.component.html` (UPDATE) : `@if (!selectedPrinter())` → liste des `discoveredPrinters()` (une ligne par imprimante : nom + statut traduit + bouton "Enregistrer" `(click)="selectRow(printer)"` + bouton "Ignorer" `(click)="ignoreRow(printer)"`), suivie d'un bouton "Annuler" `(click)="cancel()"` en pied de liste (la vue liste n'a pas accès au bouton "Ajouter"/soumission, qui reste dans la vue sous-formulaire). `@else` → le sous-formulaire actuel (name, widthMm si THERMAL, **et le bandeau `error()` existant, qui ne doit apparaître que dans cette branche**) avec un bouton "Retour" `(click)="backToList()"` ajouté à côté d'"Annuler"/"Ajouter".
- [x] Frontend — section "Imprimantes ignorées" (AC: 7)
  - [x] `printer-list.component.ts` (UPDATE) : importer `MatExpansionModule`. Nouveau signal `ignoredPrinters = signal<IgnoredPrinter[]>([])`, chargé dans `load()` via un **second bloc `try/catch` indépendant** du bloc existant qui charge `printers()` — **ne pas** ajouter l'appel à `listIgnored()` dans le même `try` que `list()` : un échec de `listIgnored()` ne doit jamais déclencher `this.error.set('admin.printers.error.load')` (réservé à l'échec du chargement de la liste principale) ; en cas d'échec de `listIgnored()`, laisser `ignoredPrinters` à `[]` silencieusement (section vide ou repliée, sans toast ni bandeau). Nouvelle méthode `async reactivate(printer: IgnoredPrinter): Promise<void>` — appelle le service, retire la ligne de `ignoredPrinters()`, toast succès/erreur.
  - [x] `printer-list.component.html` (UPDATE) : `<mat-expansion-panel>` **replié par défaut** (ne pas lier `expanded` à `true`), placé après le bloc `@if (!isLoading() && !error()) { ... }` existant (état vide ou tableau) — **visible dans les deux cas**, pas seulement quand la liste principale est vide ou seulement quand elle est peuplée. Titre "Imprimantes ignorées (N)" où N = `ignoredPrinters().length`, une ligne par imprimante ignorée (nom ou `printerBridgeId` si `name` est `null`, date, bouton "Réactiver"). État vide : message dédié si la liste est vide.
- [x] i18n (AC: 6, 7)
  - [x] `fr.json`/`en.json` (UPDATE, namespace `admin.printers.*` existant) : ajouter `create.registerAction` ("Enregistrer"), `create.ignoreAction` ("Ignorer"), `create.back` ("Retour"), `ignoredSection.title`, `ignoredSection.reactivate`, `ignoredSection.empty`, `success.ignore`/`error.ignore`, `success.reactivate`/`error.reactivate`. **Supprimer** la clé `create.printerBridgeId` (libellé de l'ancien `mat-select`, devenue morte — ce projet audite les clés i18n inutilisées, ne pas la laisser traîner).
- [x] Tests backend (AC: 1-5, 8)
  - [x] Étendre `PrinterRegistryIT` (nouveaux `@Order` après le dernier existant, 13) : ignorer une imprimante détectée par le double PrinterBridge puis vérifier son absence de `GET /discovered` ; ignorer une imprimante déjà enregistrée → 422 ; `GET /ignored` retourne l'entrée avec son `name` résolu depuis le double ; `GET /ignored` avec le double PrinterBridge arrêté → entrée avec `name: null` (jamais 503) ; réactiver → réapparition dans `GET /discovered`, disparition de `GET /ignored` ; réactiver un id inconnu du registre des imprimantes ignorées → 404 ; accès bénévole aux trois nouvelles routes → 403 (étendre le test `volunteer_session_is_forbidden_on_every_registry_endpoint` existant plutôt que d'en créer un nouveau). Mettre à jour le Javadoc de tête de la classe (`Order` déjà en commentaire) pour mentionner aussi la Story 3.13, comme il mentionne déjà les Stories 3.8/3.11.
- [x] Tests frontend
  - [x] `printer-form.component.spec.ts` (UPDATE) : la liste s'affiche par défaut (aucune sélection) ; clic "Enregistrer" affiche le sous-formulaire pour l'imprimante choisie ; "Retour" revient à la liste sans fermer le dialog ; clic "Ignorer" retire la ligne, ne ferme pas le dialog, toast succès. Les tests existants qui manipulent directement `component.form.controls.printerBridgeId.setValue(...)` (dérivation de `selectedType`, validation de `widthMm`) restent valides tels quels — ils testent `applySelectedPrinter()`, indépendant du HTML retiré ; ne pas les réécrire.
  - [x] `printer-list.component.spec.ts` (UPDATE) : section imprimantes ignorées chargée et affichée ; réactiver retire la ligne et déclenche un toast succès.

### Review Findings

_Revue adversariale (Blind Hunter + Edge Case Hunter + Acceptance Auditor, code-review 2026-07-28)._

- [x] [Review][Patch] `ignore()` non réellement idempotent sous concurrence — un double appel simultané peut contourner le check `findByPrinterBridgeId().isPresent()` et heurter la contrainte `unique` en base, remontant une `DataIntegrityViolationException` brute (500) au lieu du no-op documenté [`pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java:107-124`] — corrigé : `save()` catch désormais `DataIntegrityViolationException`, même no-op silencieux qu'un insert non concurrent
- [x] [Review][Patch] `listIgnored()` plante intégralement si PrinterBridge renvoie un nom `null` ou un `id` en double — `Collectors.toMap` lève `NullPointerException`/`IllegalStateException` non catchées [`pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java:129-138`] — corrigé : boucle `put()` manuelle sur un `HashMap` au lieu de `Collectors.toMap`, tolère les noms `null` et les doublons
- [x] [Review][Patch] `backToList()` ne réinitialise pas le signal `error()` — un bandeau d'erreur de soumission périmé peut réapparaître pour une imprimante nouvellement sélectionnée [`pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts:88-92`] — corrigé : `this.error.set(null)` ajouté
- [x] [Review][Patch] Nouvelles tables (liste de découverte, liste des imprimantes ignorées) sans en-têtes de colonnes, boutons d'action par ligne sans nom accessible liant l'action à l'imprimante concernée [`printer-form.component.html`, `printer-list.component.html`] — corrigé : `<thead>` ajouté aux deux tables, `[attr.aria-label]` interpolé par imprimante sur "Enregistrer"/"Ignorer"/"Réactiver" (nouvelles clés i18n `registerActionFor`/`ignoreActionFor`/`reactivateFor`)
- [x] [Review][Patch] Aucun test ne vérifie le comportement idempotent explicitement revendiqué dans les Completion Notes (double appel `ignore()` sur la même imprimante) [`pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`] — corrigé : nouveau test `ignoring_an_already_ignored_printer_is_idempotent` (Order 14), tests suivants renumérotés
- [x] [Review][Patch] Aucune garde anti-double-clic sur "Ignorer"/"Réactiver", contrairement au pattern déjà établi (`testingId`/`submitting`) pour "Tester l'impression"/suppression dans ces mêmes fichiers [`printer-form.component.ts`, `printer-list.component.ts`] — corrigé : signaux `ignoringId`/`reactivatingId` ajoutés, boutons désactivés pendant l'appel en cours, même pattern que `testingId`
- [x] [Review][Defer] Une imprimante ignorée pourrait être enregistrée directement via `POST /admin/printers` sans passer par la réactivation, laissant une ligne `ignored_printers` orpheline mais sans impact fonctionnel (`discover()` l'exclurait de toute façon via le check "déjà enregistrée") — pas de chemin UI actuel ne permet ce cas [`PrinterService.java`] — déféré, impact nul
- [x] [Review][Defer] `ignoredAt` stocké en `LocalDate` (pas d'heure) et aucun tri explicite sur `listIgnored()`/`findAll()` — ordre d'affichage arbitraire [`IgnoredPrinter.java`, `PrinterService.java`] — déféré, non requis par l'AC
- [x] [Review][Defer] `@PathVariable String printerBridgeId` non validé en longueur face au `VARCHAR(32)` de l'entité — un id surdimensionné produit une erreur DB brute plutôt qu'un 400 propre [`PrinterController.java`] — déféré, aucun précédent de validation de path-variable ailleurs dans ce contrôleur, risque faible (ADMIN uniquement)
- [x] [Review][Defer] `listIgnored()` ne catche que `PrinterBridgeUnavailableException`, pas d'autres échecs de transport de `printerBridgeClient.discover()` (ex. 5xx de PrinterBridge lui-même) — périmètre d'exception hérité tel quel de `discover()` depuis la Story 3.11, non étendu par cette story [`PrinterService.java`] — déféré, même périmètre que l'existant
- [x] [Review][Defer] Toast générique unique pour tout échec ignorer/réactiver (422 déjà enregistrée, 404 introuvable, 5xx/réseau), perdant la distinction que le backend encode via le statut HTTP [`printer-form.component.ts`, `printer-list.component.ts`] — déféré, amélioration non requise par l'AC
- [x] [Review][Defer] Aucun message d'état vide ("aucune imprimante détectée") dans la vue liste du dialog de création quand `discoveredPrinters()` est vide [`printer-form.component.html`] — déféré, confort UX mineur non requis par l'AC

### Re-review Findings (2026-07-28, 2ᵉ passe)

_Revue relancée sur le diff incluant les 6 correctifs ci-dessus._

- [x] [Review][Patch] Le test d'idempotence (Order 14) ne teste que des appels séquentiels — le second court-circuite via `findByPrinterBridgeId().isPresent()` avant d'atteindre `save()`, donc la branche `catch (DataIntegrityViolationException)` ajoutée au patch précédent n'était en réalité jamais exercée [`PrinterRegistryIT.java`] — corrigé : nouveau test `concurrent_ignore_calls_on_the_same_printer_both_succeed` (Order 21, deux threads + `CyclicBarrier`, même pattern que `PrintQueueDiagnosticsIT` Story 3.7). **Ce test a révélé un bug réel** que le test séquentiel masquait : `@Transactional` sur `ignore()` faisait planter le second appel concurrent avec `UnexpectedRollbackException` — Hibernate marque la session comme invalide après un échec de flush, même si l'exception est catchée en Java, ce qui empoisonne toute la transaction englobante. **Corrigé** : `@Transactional` retiré de `ignore()` (chaque appel de repository garde sa propre transaction auto-gérée par Spring Data ; aucune atomicité multi-instructions n'est réellement nécessaire ici). Vérifié stable sur 5 exécutions consécutives de la classe complète après correctif.
- [x] [Review][Patch] Nullabilité de `IgnoredPrinterDto.name` non documentée [`IgnoredPrinterDto.java`] — corrigé : Javadoc ajoutée expliquant les deux cas (PrinterBridge injoignable, id non résolu)
- [x] [Review][Defer] Catch `DataIntegrityViolationException` dans `ignore()` trop large — pourrait masquer une autre violation de contrainte (ex. id surdimensionné) comme un faux succès [`PrinterService.java`] — déféré, le resserrer proprement nécessiterait une introspection d'exception spécifique au SGBD (SQLState/nom de contrainte), fragile entre H2 (tests) et MariaDB (prod), pour un cas très marginal (l'id vient uniquement de PrinterBridge, pas d'une saisie utilisateur)
- [x] [Review][Defer] Les nouveaux `<th>` n'ont pas de `scope="col"` [`printer-form.component.html`, `printer-list.component.html`] — déféré, aucune table de toute l'application n'utilise cet attribut (vérifié par recherche globale) — gap d'accessibilité systémique préexistant, pas une régression de cette story
- [x] [Review][Defer] Pas de nettoyage réciproque si une imprimante ignorée est enregistrée directement via l'API [`PrinterService.java`] — déféré, même cause racine et même raisonnement que le défer équivalent du 1er tour (aucun impact fonctionnel, `discover()` exclurait de toute façon via le check "déjà enregistrée")

## Dev Notes

### Point de départ — ce qui existe déjà (ne pas réinventer)

- Le filtrage des imprimantes déjà enregistrées dans `discover()` (Story 3.11, déjà en production) est le modèle exact à étendre — **état actuel exact** (`PrinterService.java`) :
  ```java
  public List<DiscoveredPrinterDto> discover() {
      Set<String> registeredPrinterBridgeIds = repository.findAllPrinterBridgeIds();
      return printerBridgeClient.discover().stream()
              .filter(printer -> !registeredPrinterBridgeIds.contains(printer.id()))
              .map(this::toDiscoveredPrinterDto)
              .toList();
  }
  ```
  **Nouvel état attendu** :
  ```java
  public List<DiscoveredPrinterDto> discover() {
      Set<String> registeredPrinterBridgeIds = repository.findAllPrinterBridgeIds();
      Set<String> ignoredPrinterBridgeIds = ignoredPrinterRepository.findAllPrinterBridgeIds();
      return printerBridgeClient.discover().stream()
              .filter(printer -> !registeredPrinterBridgeIds.contains(printer.id())
                      && !ignoredPrinterBridgeIds.contains(printer.id()))
              .map(this::toDiscoveredPrinterDto)
              .toList();
  }
  ```
  Un seul `.filter()` combinant les deux exclusions — pas deux `.filter()` chaînés.
- `PrinterRepository.findAllPrinterBridgeIds()` (`@Query("select p.printerBridgeId from Printer p")`) est le pattern à dupliquer **à l'identique** pour `IgnoredPrinterRepository`. **Piège déjà rencontré** : la convention dérivée Spring Data `findPrinterBridgeIdBy()` (sans `@Query`) a été essayée pour ce besoin exact et a échoué à l'exécution — Hibernate construisait une requête sélectionnant l'entité complète au lieu de la seule colonne, provoquant un `ConversionFailedException` (`LinkedHashSet<Printer>` → `Set<String>`). Toujours utiliser `@Query` explicite pour ce genre de projection sur une seule colonne.
- `InvalidPrinterConfigurationException` (existante, 422) est déjà utilisée pour un nom de printer dupliqué (`PrinterService.create()`, catch de `DataIntegrityViolationException`) — même famille de règle métier que "imprimante déjà enregistrée, ne peut pas être ignorée" (AC1). La réutiliser directement, ne pas créer de nouvelle exception pour ce cas.
- `ToastService` est déjà injecté dans `printer-list.component.ts` — copier le même import/pattern dans `printer-form.component.ts`, qui ne l'a pas aujourd'hui.
- `MatExpansionModule` n'est utilisé nulle part ailleurs dans le projet — première utilisation, mais fait partie de `@angular/material` (dépendance déjà présente), aucune installation nécessaire.

### `printer-form.component` — passage d'un formulaire unique à une liste + sous-formulaire

Le composant actuel pilote tout via un seul `FormGroup` avec un `mat-select` sur `printerBridgeId`, dont les `valueChanges` déclenchent `applySelectedPrinter()`. Cette story remplace le `mat-select` par une liste de lignes cliquables (une par imprimante détectée), chacune avec deux boutons. **Ne pas supprimer `applySelectedPrinter()`** — il reste le point unique qui dérive `selectedType` et configure la validation de `widthMm` ; il continue d'être déclenché par le `valueChanges` existant sur `printerBridgeId`, lui-même désormais réglé programmatiquement par `selectRow()` plutôt que par la sélection d'un `mat-select`. Le dialog ne se ferme plus qu'au clic sur "Ajouter" (soumission réussie) ou "Annuler"/croix — "Ignorer" et "Retour" restent dans le dialog, contrairement à `onSubmit()` qui appelle `dialogRef.close()`.

### Ne pas confondre avec `checkStatus()`/vérification de connectivité

Le filtre "ignorées" ne concerne que `discover()` (liste de découverte pour le dialog de création). Il ne change **rien** à `checkAccessibility()`/`checkStatus()` (Story 3.11, vérification de connectivité des imprimantes déjà enregistrées) — deux méthodes distinctes du même `PrinterBridgeClient`. Aucune imprimante déjà enregistrée ne peut de toute façon se retrouver dans `ignored_printers` (contrainte applicative de l'AC1).

### Migration Liquibase — numérotation

Dernier changelog inclus dans `db.changelog-master.xml` : `018-printer-bridge-id.xml`. Cette story ajoute `019-ignored-printers.xml`.

### Project Structure Notes

- Aucun nouveau dossier — toutes les nouvelles classes backend dans `org.pluribourse.domain.print.{entity,repository,dto,exception}` (packages déjà existants). Frontend : extension de `features/admin/printers/` et `services/printer-registry.service.ts`/`models/printer-registry.model.ts` existants — aucun nouveau composant Angular ; `MatExpansionModule` s'ajoute aux imports de `printer-list.component.ts`.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/repository/PrinterRepository.java` (référence, pattern `@Query` à dupliquer)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/exception/PrinterNotFoundException.java` (modèle pour `IgnoredPrinterNotFoundException`), `InvalidPrinterConfigurationException.java` (référence, réutilisée telle quelle)
- `pluribourse-backend/src/main/resources/db/changelog/018-printer-bridge-id.xml` (référence, ne pas modifier) et `db.changelog-master.xml` (ajouter l'include)
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts`/`.html`, `printer-list.component.ts`/`.html`
- `pluribourse-frontend/src/app/models/printer-registry.model.ts`, `services/printer-registry.service.ts`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/PrinterBridgeDouble.java` (référence, ne pas modifier — "ignorer" est un concept 100% côté PluriBourse, aucune nouvelle route à y simuler)

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-07-28.md] — origine et justification complète de cette story
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.13] — ACs source
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md#FR-100]
- [Source: _bmad-output/implementation-artifacts/3-11-integration-printerbridge-connexion-et-statut.md] — pattern `discover()`/filtrage, pattern d'exception (`BusinessException`), leçon sur `findPrinterBridgeIdBy()` (échec de la convention dérivée Spring Data, résolu par `@Query` explicite), commit `1091033` (état réel livré, y compris le filtrage des imprimantes déjà enregistrées ajouté en session)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java, repository/PrinterRepository.java, controller/PrinterController.java, entity/Printer.java, exception/PrinterNotFoundException.java, exception/InvalidPrinterConfigurationException.java] — lus intégralement
- [Source: pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts, .html, printer-list.component.ts, .html] — lus intégralement
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md#Gestion des imprimantes (Admin)] — description cible de l'interaction liste + actions par ligne + section imprimantes ignorées

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw -q compile` → BUILD SUCCESS après l'ajout de l'entité/repository/service/contrôleur.
- `./mvnw -q -Dtest=PrinterRegistryIT test` → 19/19 passed (13 existants + 6 nouveaux : ignorer exclut de la découverte, ignorer une imprimante déjà enregistrée → 422, liste des ignorées avec nom résolu, réactiver, réactiver un id inconnu → 404, liste des ignorées toujours accessible avec PrinterBridge injoignable).
- `./mvnw -q test` (suite complète backend) → 307/307 passed, BUILD SUCCESS, aucune régression.
- `npm test` (suite complète frontend) → 49 fichiers de test, 441/441 passed (432 existants + 9 nouveaux), aucune régression.

### Completion Notes List

- Entité `IgnoredPrinter`/`IgnoredPrinterRepository`/migration `019-ignored-printers.xml` implémentées exactement selon les Dev Notes — `@Query` explicite pour `findAllPrinterBridgeIds()`, pas la convention dérivée (piège documenté depuis la Story 3.11).
- `PrinterService.discover()` étendu avec un seul `.filter()` combinant l'exclusion des imprimantes enregistrées et ignorées, comme prévu.
- `PrinterService.ignore()` : rejette une imprimante déjà enregistrée en réutilisant `InvalidPrinterConfigurationException` (422) — aucune nouvelle exception créée pour ce cas, conformément à la story. Idempotent sur double appel.
- `PrinterService.listIgnored()` : résout les noms via un appel brut à `printerBridgeClient.discover()` (pas `this.discover()`), catch local de `PrinterBridgeUnavailableException` — jamais de 503 sur cette route, vérifié par un test dédié avec le double PrinterBridge arrêté.
- Frontend `printer-form.component` : le `mat-select` unique remplacé par une liste (`<table class="data-table">`, réutilise les classes globales existantes plutôt que d'introduire du CSS spécifique) avec actions "Enregistrer"/"Ignorer" par ligne ; `applySelectedPrinter()` conservé tel quel, déclenché désormais via `selectRow()`. Le bandeau d'erreur de soumission reste scopé à la vue sous-formulaire.
- Frontend `printer-list.component` : section "Imprimantes ignorées" via `MatExpansionModule` (première utilisation dans le projet), chargement dans un bloc `try/catch` strictement séparé de celui de la liste principale — vérifié par un test dédié (l'échec de `listIgnored()` ne déclenche pas le bandeau d'erreur principal).
- i18n : clé `create.printerBridgeId` (libellé de l'ancien `mat-select`) supprimée de `fr.json`/`en.json`, devenue morte après la refonte du dialog.
- Aucune déviation par rapport au plan de la story — implémentée telle qu'écrite, y compris les décisions assumées (pas de revérification live PrinterBridge dans `ignore()`, entité `IgnoredPrinter` à deux colonnes sans stockage du nom).

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/resources/db/changelog/019-ignored-printers.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/entity/IgnoredPrinter.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/repository/IgnoredPrinterRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/IgnoredPrinterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/exception/IgnoredPrinterNotFoundException.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/printer-registry.model.ts`
- `pluribourse-frontend/src/app/services/printer-registry.service.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.html`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.html`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-07-28 : Implémentation complète de la Story 3.13 (ignorer une imprimante détectée non enregistrée, réversible). Nouvelle entité `IgnoredPrinter` + migration `019-ignored-printers.xml` ; `PrinterService.discover()` exclut désormais aussi les imprimantes ignorées ; nouveaux endpoints `POST /discovered/{id}/ignore`, `GET /ignored`, `DELETE /ignored/{id}`. Frontend : dialog "Ajouter une imprimante" refondu en liste avec actions "Enregistrer"/"Ignorer" par ligne ; nouvelle section repliable "Imprimantes ignorées" sur `/admin/printers`. 307/307 tests backend et 441/441 tests frontend passent, aucune régression. Statut → `review`.
- 2026-07-28 : Revue de code adversariale (Blind Hunter + Edge Case Hunter + Acceptance Auditor) — 0 decision-needed, 6 patch appliqués, 6 defer consignés dans `deferred-work.md`, 11 rejetés comme bruit (dont une affirmation factuellement fausse sur l'absence de tests frontend, et plusieurs "incohérences" qui respectaient en réalité des décisions déjà documentées dans les Dev Notes). Correctifs : idempotence de `ignore()` sous concurrence, robustesse de `listIgnored()` face à un nom `null`/id dupliqué, réinitialisation du signal d'erreur dans `backToList()`, en-têtes de colonnes + `aria-label` par imprimante sur les nouvelles tables, nouveau test d'idempotence, garde anti-double-clic sur "Ignorer"/"Réactiver". 20/20 tests `PrinterRegistryIT` (307/307 backend), 441/441 frontend. Statut → `done`.
- 2026-07-28 : 2ᵉ passe de revue sur le diff patché — 0 decision-needed, 2 patch appliqués, 3 defer supplémentaires consignés, 11 rejetés (dont 3 doublons exacts de defers déjà consignés au 1er tour, et plusieurs points respectant des conventions déjà établies ailleurs dans le projet, ex. `@Column(unique = true)` dupliqué comme sur `Printer.name`). **Bug réel trouvé et corrigé** : l'Acceptance Auditor a signalé que le test d'idempotence séquentiel (Order 14) ne testait pas réellement la protection anti-race ajoutée au 1er tour (le second appel court-circuitait avant le `catch`). Un vrai test de concurrence (Order 21, deux threads + `CyclicBarrier`, même pattern que `PrintQueueDiagnosticsIT` Story 3.7) a révélé que `@Transactional` sur `ignore()` provoquait un `UnexpectedRollbackException` sur le second appel concurrent — Hibernate invalide la session après un échec de flush, même si l'exception est catchée en Java. Corrigé en retirant `@Transactional` de `ignore()` (chaque appel de repository garde sa propre transaction ; aucune atomicité multi-instructions n'est nécessaire). Vérifié stable sur 5 exécutions consécutives de la classe complète. Javadoc ajoutée sur `IgnoredPrinterDto.name` (nullabilité). 21/21 tests `PrinterRegistryIT` (307/307 backend), 441/441 frontend. Statut → `done` (confirmé).
