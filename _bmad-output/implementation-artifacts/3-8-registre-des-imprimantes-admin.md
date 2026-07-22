---
baseline_commit: 067f0cb8488e4e0b5bb87c522a4864a29e2c1513
---

# Story 3.8: Registre des imprimantes (Admin)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want enregistrer, consulter et supprimer les imprimantes thermiques et A4 disponibles depuis une page `/admin/printers`,
so that les bénévoles puissent les sélectionner à leur connexion et que chaque imprimante dispose de sa propre file d'impression, sans intervention développeur.

## Acceptance Criteria

1. `GET /admin/printers` (ADMIN uniquement) renvoie **toutes** les imprimantes enregistrées (THERMAL et A4) sous forme de liste plate : `id`, `name`, `type`, `connected` (booléen — `true` quand aucune erreur runtime n'est mémorisée pour sa file). Pas de pagination (peu d'imprimantes par événement, cf. Story 3.4 Dev Notes § Thread par imprimante). La page `/admin/printers` affiche ces données en tableau (nom, type, badge de statut de connexion, actions), même structure que `/admin/users`.
2. `POST /admin/printers` existe déjà (Story 3.4) et n'est **pas modifié côté backend**. Cette story l'expose dans l'UI via un dialog "Ajouter une imprimante" : type THERMAL → dropdown de ports série (AC3) + sélecteur de largeur (57/80 mm, FR-032) ; type A4 → champs adresse IP/hostname + port TCP (optionnel, défaut 9100, FR-077). À la sauvegarde, le dialog se ferme et la liste se recharge.
3. `GET /admin/printers/serial-ports` (ADMIN uniquement) renvoie les ports série actuellement visibles par la JVM via `com.fazecast.jSerialComm.SerialPort.getCommPorts()` : pour chacun, `systemPortName` (valeur à stocker dans `CreatePrinterDto.serialPort`) et `descriptiveName` (nom descriptif de l'appareil Bluetooth appairé, affiché dans le dropdown).
4. `DELETE /admin/printers/{id}` (ADMIN uniquement) : supprime la ligne `Printer` en base **et** détruit sa file d'impression (handle retiré du registre `PrintQueueService`, thread consommateur interrompu — pas seulement orphelin). 404 (`PrinterNotFoundException`, réutilisée telle quelle) si l'id est inconnu. 204 en cas de succès. Côté UI, précédé d'un dialog de confirmation (`ConfirmDialogService`) — cf. `EXPERIENCE.md` ligne 135, qui cite explicitement "suppression d'imprimante" comme cas d'usage du pattern de confirmation.
5. Une fois une imprimante supprimée, tout appel ultérieur à `PrintQueueService.submit(id, ...)` ou `isAvailable(id)` pour cet id échoue/renvoie `false` immédiatement — comportement déjà existant (`PrinterNotFoundException` / handle absent de la map), **aucun changement requis** dans `DepositValidationService`. Un bénévole dont la session référence encore l'imprimante supprimée reçoit donc une erreur à son prochain job d'impression (FR-098).
6. Une session bénévole appelant n'importe quel endpoint `/admin/printers/**` (liste, ports série, création, suppression) reçoit 403 — couvert nativement par `SecurityConfig` (`/admin/**` → `hasRole('ADMIN')`, comme tous les autres contrôleurs admin), un seul test suffit à le vérifier.

## Tasks / Subtasks

- [x] Backend — teardown de file par imprimante (AC: 4, 5)
  - [x] `PrinterQueueHandle.java` (UPDATE) : ajouter `public void stop()` → `consumerThread.interrupt()`. La boucle `consume()` (déjà `catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }`) sort proprement — ne touche à aucune autre logique existante.
  - [x] `PrintQueueService.java` (UPDATE) : ajouter `public void unregisterPrinter(Long id)` → `PrinterQueueHandle handle = handles.remove(id); if (handle != null) { handle.stop(); }`. Idempotent (id déjà absent → no-op).
- [x] Backend — service registre (AC: 1, 2, 3, 4)
  - [x] `PrinterService.java` (UPDATE, injection déjà en place — `PrinterRepository`, `PrinterMapper`, `PrintQueueService`) :
    - `List<PrinterSummaryDto> list()` : `repository.findAll()`, pour chaque `Printer` résout `printQueueService.getHandle(printer.getId())` (jamais `null` en pratique pour une imprimante persistée — même invariant que Story 3.7 Dev Notes § Invariant handle/printer) et construit `connected = handle.getLastError() == null`.
    - `List<SerialPortDto> listAvailableSerialPorts()` : `Arrays.stream(SerialPort.getCommPorts()).map(p -> new SerialPortDto(p.getSystemPortName(), p.getDescriptivePortName())).toList()`. Pas de wrapper de timeout ici (contrairement à `openPort()` dans `SerialPrinterConnectivityChecker`) — c'est une simple énumération, pas une I/O bloquante potentiellement infinie.
    - `void delete(Long id)` : `Printer printer = repository.findById(id).orElseThrow(() -> new PrinterNotFoundException(id));` puis `repository.delete(printer);` puis `printQueueService.unregisterPrinter(id);` — dans cet ordre (échec de suppression BDD ne doit pas détruire une file encore valide).
  - [x] Nouveau DTO `dto/PrinterSummaryDto.java` (record) : `Long id, String name, PrinterType type, boolean connected`. **Ne pas** réutiliser `PrinterStatusDto` (Story 3.7, domaine diagnostic distinct : `queueDepth`/`jobInProgress`/`lastError`/`canRetry` hors périmètre de cette page registre — AC1 ne demande qu'un statut de connexion simple, pas la richesse 3-états ambre/vert/rouge de 3.7).
  - [x] Nouveau DTO `dto/SerialPortDto.java` (record) : `String systemPortName, String descriptiveName`.
  - [x] **Aucune nouvelle exception** : réutiliser `PrinterNotFoundException` (404, `printer-not-found`) telle quelle pour AC4, comme le fait déjà `PrintQueueDiagnosticsService`.
- [x] Backend — contrôleur (AC: 1, 3, 4, 6)
  - [x] `PrinterController.java` (UPDATE, garder `@RequestMapping("/admin/printers")` + `@PreAuthorize("hasRole('ADMIN')")` existants) : ajouter `GET ""` → `List<PrinterSummaryDto>`, `GET "/serial-ports"` → `List<SerialPortDto>`, `DELETE "/{id}"` → 204 (`ResponseEntity<Void>`). Le `POST ""` existant reste inchangé.
- [x] Frontend — modèle & service (AC: 1, 2, 3, 4)
  - [x] `models/printer-registry.model.ts` (nouveau, domaine distinct de `printer.model.ts` — sélection bénévole — et de `printer-status.model.ts` — diagnostic, cf. convention établie Story 3.7) : `interface PrinterSummary { id: number; name: string; type: 'THERMAL' | 'A4'; connected: boolean; }`, `interface SerialPortOption { systemPortName: string; descriptiveName: string; }`, `interface CreatePrinterPayload { name: string; type: 'THERMAL' | 'A4'; serialPort: string | null; widthMm: number | null; host: string | null; port: number | null; }`.
  - [x] `services/printer-registry.service.ts` (nouveau, ne pas étendre `print.service.ts`/`print-queue.service.ts` — domaines distincts) : `list(): Observable<PrinterSummary[]>` (`GET /api/admin/printers`), `listSerialPorts(): Observable<SerialPortOption[]>` (`GET /api/admin/printers/serial-ports`), `create(payload: CreatePrinterPayload): Observable<void>` (`POST /api/admin/printers`), `delete(id: number): Observable<void>` (`DELETE /api/admin/printers/${id}`).
- [x] Frontend — page liste + dialog de création (AC: 1, 2, 4)
  - [x] Nouveau dossier `features/admin/printers/`, même niveau que `features/admin/users/`.
  - [x] `printer-list.component.ts` + `.html` (nouveau fichier, jamais de template inline — CLAUDE.md) + `.scss` + `.spec.ts` : tableau (`class="data-table"`, classe globale déjà utilisée par `/admin/users`/`/admin/sellers`) avec colonnes nom/type/statut/actions. Statut de connexion via les classes globales **déjà existantes** `.badge`/`.badge--active`/`.badge--inactive` (`styles.scss` lignes 167-196, utilisées par `/admin/users` et `/admin/sellers`) — **ne pas** recréer un chip local comme `print-queue-list.component.scss` (celui-ci a inventé son propre chip 3-états car aucun badge partagé ne convenait à son besoin spécifique ambre/vert/rouge ; ce n'est pas le cas ici, AC1 ne demande qu'un statut binaire connecté/hors ligne). `SkeletonRowComponent` pendant le chargement, `EmptyStateComponent` (icône `print_connect`, cohérent avec l'icône nav dédiée, cf. Dev Notes § Nav) si aucune imprimante, `NotificationInlineComponent` pour les erreurs de chargement. Bouton "Ajouter une imprimante" ouvre le dialog de création (`Dialog.open`, pattern exact de `UserListComponent.openCreateDialog()` — `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts:95-107`) ; `ref.closed.subscribe(() => this.load())` recharge la liste. Bouton "Supprimer" par ligne → `ConfirmDialogService.open({ title, description, confirmVariant: 'error' })` (pattern exact de `UserListComponent.confirmDelete()`/`SellerListComponent.confirmDelete()`), puis appel `printerRegistryService.delete(id)` + toast succès/erreur + retrait optimiste de la ligne (pas besoin de recharger toute la liste, cf. `UserListComponent.confirmDelete()` qui filtre `this.users.update(...)`).
  - [x] `printer-form.component.ts` + `.html` (nouveau, dialog auto-soumis — **pas** de retour de données au parent, pattern exact de `UserFormComponent`/`user-form.component.html`, `pluribourse-frontend/src/app/features/admin/users/user-form.component.ts`) : `FormGroup` avec `name` (required, maxLength 100), `type` (`mat-select` THERMAL/A4, défaut THERMAL), puis champs conditionnels selon `type` — `serialPort` (`mat-select` rempli depuis `printerRegistryService.listSerialPorts()` chargé au `ngOnInit`, valeur = `systemPortName`, libellé = `descriptiveName`, pattern direct de `printer-selection.component.html` lignes 11-19) + `widthMm` (`mat-select` avec deux `mat-option` fixes 57/80) si THERMAL ; `host` + `port` (optionnel, non renseigné = laissé `null`, le backend applique le défaut 9100) si A4. Basculer les validateurs (`Validators.required` sur les champs pertinents) sur `type.valueChanges` — ne jamais valider les deux jeux de champs simultanément. Si `listSerialPorts()` renvoie une liste vide, afficher une notification inline (`admin.printers.create.noSerialPort`) plutôt qu'un dropdown vide silencieux — pas de saisie manuelle du port (hors périmètre AC3, qui impose le dropdown). `onSubmit()` appelle `printerRegistryService.create(...)` puis `dialogRef.close()` (comme `UserFormComponent.onSubmit()`), erreur → notification inline générique (`admin.printers.error.create`, un seul type d'erreur backend possible — 422 nom dupliqué ou configuration incohérente — pas besoin de distinguer, cohérent avec `UserFormComponent`).
  - [x] `admin.routes.ts` (UPDATE) : ajouter la route `printers` → `PrinterListComponent`, même style `loadComponent` que les entrées existantes.
  - [x] `app-layout.component.html` (UPDATE) : ajouter un item de nav "Imprimantes" (icône `print_connect`, cf. `EXPERIENCE.md` ligne 84) dans `nav.sections.management`, **avant l'item `Utilisateurs` existant** — c'est le premier item de la section (lignes 91-101), pas `print-queue` (dernier item, lignes 115-125). Ordre cible `EXPERIENCE.md` lignes 81-86 : Éditions, Rapports, Imprimantes, Utilisateurs, Paramètres, File d'impression — la sidebar actuelle n'a pas encore "Éditions"/"Rapports" dans cette section, mais "Imprimantes" doit précéder "Utilisateurs"/"Paramètres"/"File d'impression", pas les suivre. Même structure `<a routerLink>` que les items voisins.
  - [x] `fr.json`/`en.json` (UPDATE) : nouvelle clé `nav.admin.printers` (à côté de `nav.admin.printQueue` ligne 94) + nouveau namespace `admin.printers.*` (titre, colonnes, statuts connecté/hors ligne, type THERMAL/A4, actions, état vide, dialog de suppression, toasts succès/erreur, sous-namespace `create.*` pour le formulaire). **Namespace autonome, ne pas référencer `admin.printQueue.type.*`** — convention du projet déjà établie : chaque feature admin duplique ses propres clés plutôt que de les partager (ex. `admin.printQueue.*` et `admin.users.*` sont deux namespaces indépendants sans clés communes malgré des besoins similaires — statuts, actions).
- [x] Tests backend (AC: 1-6)
  - [x] Nouvelle classe `PrinterRegistryIT` (`org.pluribourse.domain.print`, E2E via `IntegrationTest`, même style que `PrintInfrastructureIT` — sessions admin/bénévole en `@BeforeAll`, `createReachablePrinter(name)` via `POST /api/admin/printers` sur un `ServerSocket` local). Couvrir : liste vide initiale (attention — imprimantes créées par les tests précédents de la même classe s'accumulent, filtrer par id retourné à la création comme le fait `PrintQueueDiagnosticsIT.findStatus()`, ne pas assumer une taille fixe) ; imprimante joignable → `connected=true` dans `GET /admin/printers` ; imprimante injoignable (port `1` local, technique `PrintInfrastructureIT` Order 6) → `connected=false` ; `GET /admin/printers/serial-ports` → 200 + tableau JSON (vide accepté, pas de matériel Bluetooth en CI, cf. Story 3.4 Dev Notes § Stratégie de test point 4) ; `DELETE` d'une imprimante existante → 204, l'imprimante n'apparaît plus dans `GET /admin/printers`, et `printQueueService.getHandle(id)` (bean autowired, même exception documentée que `PrintInfrastructureIT`) devient `null` immédiatement après ; `submit()` vers l'id supprimé lève `PrinterNotFoundException` (réutiliser la technique `assertThatThrownBy` de `PrintInfrastructureIT` Order 10) ; `DELETE` d'un id inconnu → 404 `printer-not-found` ; 403 bénévole sur `GET`/`GET serial-ports`/`DELETE`.
- [x] Tests frontend
  - [x] `printer-list.component.spec.ts` : rendu du tableau depuis un mock du service, badge connecté/hors ligne via les classes `.badge--active`/`.badge--inactive`, état vide, état de chargement, ouverture du dialog de création + rechargement à la fermeture, confirmation + suppression + retrait optimiste de la ligne + toast succès/erreur.
  - [x] `printer-form.component.spec.ts` : bascule des champs visibles/validateurs selon `type` (THERMAL ↔ A4), chargement des ports série au `ngOnInit`, état "aucun port détecté", soumission réussie ferme le dialog, erreur backend affiche la notification inline sans fermer le dialog.

### Review Findings

- [x] [Review][Patch] NPE possible dans `PrinterService.list()` en cas de handle absent (course avec `delete()`) [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java]
- [x] [Review][Patch] Le formulaire Angular ne réinitialise pas les champs de l'ancien type lors du bascule THERMAL ↔ A4 [pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts]
- [x] [Review][Patch] Champ `port` sans validateurs client (min/max) alors que le backend impose `@Min(1)@Max(65535)` [pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts]
- [x] [Review][Patch] Chaîne `" mm"` codée en dur (hors i18n) dans le sélecteur de largeur [pluribourse-frontend/src/app/features/admin/printers/printer-form.component.html]
- [x] [Review][Defer] Jobs en attente dans la file silencieusement perdus à la suppression d'une imprimante, sans trace ni avertissement admin — deferred, pre-existing scope decision (Dev Notes § Teardown de file ne couvre que le job en cours, pas la purge/traçabilité des jobs en attente ; amélioration UX possible dans une story dédiée) [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java]
- [x] [Review][Defer] `unregisterPrinter()` n'est pas synchronisé avec le commit de la transaction de `delete()` (le handle peut disparaître de la mémoire avant que la ligne ne soit réellement supprimée en base) — deferred, pre-existing pattern (aucune synchronisation transactionnelle de ce type ailleurs dans le code, corriger proprement nécessiterait un `TransactionSynchronization afterCommit` ; le null-check ajouté en patch neutralise déjà le symptôme observable) [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java]

### Review Findings — re-review (2026-07-22)

- [x] [Review][Patch] Le patch précédent oublie le champ `port` : non réinitialisé lors du bascule A4 → THERMAL, une valeur périmée peut être persistée sur une imprimante THERMAL ou bloquer silencieusement la soumission si elle échoue au validateur min/max [pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts]
- [x] [Review][Patch] `PrinterService.listAvailableSerialPorts()` n'intercepte aucune exception autour de l'appel natif jSerialComm — un échec de la bibliothèque native (déploiement Docker sans accès matériel) remonterait en 500 non structuré [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java]
- [x] [Review][Defer] `DELETE` concurrent du même id peut lever une `ObjectOptimisticLockingFailureException` non mappée (au lieu d'un 404 idempotent) — deferred, gap pré-existant et systémique (même limitation déjà déférée pour l'entité `Item` en Story 3.2, `@Version` sans handler dédié dans `GlobalExceptionHandler`), pas spécifique à cette story [pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java]
- [x] [Review][Defer] `DELETE /admin/printers/{id}` avec un id non numérique renvoie le 400 Spring par défaut au lieu d'un `ProblemDetail` structuré — deferred, comportement systémique de tous les contrôleurs à `@PathVariable Long id` de l'application, pas spécifique à cette story [pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java]



### Point de départ — ce qui existe déjà (ne pas réinventer)

Le module `org.pluribourse.domain.print` (package renommé depuis `org.pluribourse.print` par un refactor "domain" postérieur à la Story 3.4 — voir Git Intelligence ci-dessous) contient déjà, livré et testé par les Stories 3.4/3.7/3.9 :

- `Printer`/`PrinterType` (entité, `name` UNIQUE, pas de champ de statut — voir Story 3.4 Dev Notes § Statut runtime vs persistance, toujours valable).
- `PrinterRepository extends JpaRepository<Printer, Long>`.
- `PrintQueueService` : `Map<Long, PrinterQueueHandle> handles` (`ConcurrentHashMap`), `registerPrinter(Printer)`, `submit(Long, PrintJob)`, `getHandle(Long)`, `isAvailable(Long)`, `reloadFromDatabase()`. **Ne contient aucune méthode de désenregistrement** — c'est tout le delta backend de cette story (`unregisterPrinter`).
- `PrinterQueueHandle` : thread consommateur daemon + `LinkedBlockingDeque`, `suspended`/`lastError`/`lastFailedJob`/`jobInProgress` (Story 3.7), `requeueFailedJobAtHead()`/`discardFailedJob()`/`errorSnapshot()` (Story 3.7). **Ne contient aucune méthode d'arrêt** — c'est le second delta (`stop()`).
- `PrinterService.create(CreatePrinterDto)` : validation type/champs, défaut port A4 = 9100, gestion `DataIntegrityViolationException` (nom dupliqué → 422). Inchangée par cette story.
- `PrinterController` : `POST ""` uniquement (`@RequestMapping("/admin/printers")`, `@PreAuthorize("hasRole('ADMIN')")`). Cette story y ajoute `GET ""`, `GET "/serial-ports"`, `DELETE "/{id}"`.
- `PrinterMapper` (MapStruct) : `toDto`/`toEntity` pour `PrinterDto`/`CreatePrinterDto` uniquement — **ne pas** y ajouter `PrinterSummaryDto`/`SerialPortDto`, ce sont des projections calculées (croisant `Printer` + `PrintQueueService`), construites manuellement dans `PrinterService`, même approche que `PrintQueueDiagnosticsService.listStatuses()`.
- `PrinterNotFoundException` (404, `printer-not-found`) et `InvalidPrinterConfigurationException` (422) — aucune nouvelle exception nécessaire pour cette story.
- `PrinterSelectionService.isAvailable(printer)` délègue déjà à `printQueueService.isAvailable(printer.getId())`, qui retourne `false` sans NPE si le handle est absent — le comportement post-suppression (AC5) fonctionne donc sans aucune modification de `PrinterSelectionService`/`DepositValidationService`.

### Invariant handle/printer (rappel Story 3.7)

`registerPrinter()` est appelé pour toute imprimante en base (démarrage + création) — aucune ligne `Printer` n'existe sans handle correspondant. `PrinterService.list()` peut donc appeler `printQueueService.getHandle(printer.getId())` sans null-check défensif, comme `PrintQueueDiagnosticsService.listStatuses()`.

### Teardown de file à la suppression — comportement exact

`unregisterPrinter(id)` retire le handle de la map (`handles.remove(id)`) puis appelle `handle.stop()` → `consumerThread.interrupt()`. Si le thread est en attente bloquante (`deque.takeFirst()` ou `Thread.sleep(200)` en état suspendu), il lève `InterruptedException`, déjà catchée dans `consume()` (`Thread.currentThread().interrupt(); return;`) → sortie propre de la boucle, aucune modification de `consume()` nécessaire. Si un job est en cours d'exécution (I/O bloquante synchrone, ex. `ThermalPrintService.printWithTimeout`), l'interruption ne l'arrête pas immédiatement — le thread termine ce job puis sort à son retour en tête de boucle. Ce comportement (pas de kill forcé mi-job) n'est pas spécifié par l'AC et est acceptable — ne pas complexifier avec une interruption plus agressive.

### `GET /admin/printers/serial-ports` — API jSerialComm

`com.fazecast.jSerialComm.SerialPort.getCommPorts()` (méthode statique, déjà utilisée pour l'ouverture de port dans `SerialPrinterConnectivityChecker`, dépendance `jSerialComm:2.11.4` déjà présente depuis la Story 3.4 — aucun ajout de dépendance). Chaque `SerialPort` expose `getSystemPortName()` (identifiant à passer tel quel dans `CreatePrinterDto.serialPort`, c'est ce que lit `SerialPort.getCommPort(printer.getSerialPort())` dans `ThermalPrintService`/`SerialPrinterConnectivityChecker`) et `getDescriptivePortName()` (nom lisible de l'appareil, ex. nom du périphérique Bluetooth appairé — à vérifier contre la Javadoc jSerialComm 2.11.4 en implémentant, méthode publique documentée). **Gap de test assumé, identique à la Story 3.4** : aucun port Bluetooth réel en CI, donc `getCommPorts()` renverra probablement un tableau vide dans les tests — le test vérifie 200 + un tableau JSON valide (vide accepté), pas son contenu.

### `PrinterSummaryDto` vs `PrinterStatusDto` — pourquoi deux DTOs distincts

`/admin/printers` (registre CRUD, cette story) et `/admin/print-queue` (diagnostic, Story 3.7) restent deux routes et deux périmètres distincts, comme explicitement tranché en Story 3.7 Dev Notes ("**Ne pas** ajouter cette route dans `PrinterController` existant... route distincte, périmètre distinct"). AC1 de cette story ne demande qu'un statut de connexion binaire (nom, type, statut) — pas `queueDepth`/`jobInProgress`/`lastError`/`canRetry`, qui restent le périmètre exclusif de la page diagnostic. D'où un DTO dédié, plus étroit.

### Badge de statut — réutiliser les classes globales existantes

`styles.scss` (lignes 167-196) définit déjà `.badge`/`.badge--active`/`.badge--inactive`/`.badge__dot`, utilisées telles quelles par `/admin/users` (statut actif/inactif) et `/admin/sellers`. C'est le pattern à suivre ici pour le statut connecté/hors ligne (2 états, table admin classique) — **ne pas** recréer un chip local comme `print-queue-list.component.scss` (`.connection-chip`), qui a introduit son propre composant visuel uniquement parce qu'il avait besoin d'un 3ᵉ état (ambre "en erreur") sans équivalent dans `.badge`. Cette story n'a pas ce besoin.

### Nav — emplacement et icône

`EXPERIENCE.md` (lignes 81-86) place "Imprimantes" (icône `print_connect`) dans la section "Gestion", **avant** "Utilisateurs"/"Paramètres" — cohérent avec la structure actuelle de `nav.sections.management` (`app-layout.component.html` lignes 88-127, qui contient déjà, dans cet ordre, Bénévoles/Paramètres/File d'impression). Ajouter l'item **en tête de section, avant "Bénévoles"** (premier item actuel) pour respecter l'ordre `EXPERIENCE.md` — pas avant "File d'impression" (dernier item), qui le placerait après "Bénévoles"/"Paramètres", à l'envers de l'ordre visé.

### Formulaire de création — dialog, pas de page dédiée

`EXPERIENCE.md` ligne 53 et ligne 135 (DialogShellComponent, citant explicitement "modification de statut d'article" et "suppression d'imprimante" comme exemples futurs) confirment le pattern dialog pour les formulaires CRUD courts. `UserFormComponent`/`user-form.component.html` (Story 1.3) est le modèle direct : dialog auto-soumis (le composant appelle lui-même le service et se ferme, ne retourne pas de données au parent — contrairement à `ResetPasswordDialogComponent` qui retourne une valeur). Le sélecteur de ports série suit le pattern `mat-select` de `printer-selection.component.html` (Story 3.9, lignes 11-19) — options peuplées depuis un appel HTTP au chargement.

### Project Structure Notes

- Backend : toutes les nouvelles classes dans `org.pluribourse.domain.print.{controller,dto,service}`, package déjà existant depuis le refactor "domain" (commit `db9b6ec`), aucune nouvelle arborescence.
- Frontend : nouveau dossier `features/admin/printers/`, même niveau que `features/admin/users/`, `features/admin/print-queue/`.
- Aucune migration Liquibase (aucun changement de schéma — `list()`/`delete()` utilisent la table `printers` existante depuis la migration `016-printers.xml`, Story 3.4).
- Prochain numéro de story après celle-ci : `3-9-selection-dimprimante-par-le-benevole-a-la-connexion` (déjà livrée, `done`) — cette story ferme le dernier gap connu du module `print` avant l'Épic 4.

### Git Intelligence

Commit `db9b6ec` ("Clean code : use domain package") a déplacé tout le backend de `org.pluribourse.{print,item,seller,...}` vers `org.pluribourse.domain.{print,item,seller,...}` **après** la Story 3.4 (dont le Dev Agent Record cite encore `org.pluribourse.print`) — toutes les références de package dans cette story utilisent déjà le chemin actuel `org.pluribourse.domain.print`. Vérifié directement sur le code présent au commit `067f0cb` (HEAD au moment de la rédaction de cette story).

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java` — ajouter `unregisterPrinter(Long)`, ne toucher à rien d'autre (`registerPrinter`/`submit`/`getHandle`/`isAvailable`/`reloadFromDatabase` inchangés).
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterQueueHandle.java` — ajouter `stop()`, ne toucher à rien d'autre.
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java` — ajouter `list()`/`listAvailableSerialPorts()`/`delete()`, garder `create()` et `validateConfiguration()` intacts.
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java` — ajouter 3 méthodes, garder `create()` intact.
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — ajouter l'entrée `printers`.
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — ajouter l'item de nav.
- `pluribourse-frontend/src/styles.scss` — **ne rien y ajouter**, les classes `.badge` nécessaires existent déjà (lignes 167-196).
- `pluribourse-frontend/public/i18n/fr.json`/`en.json` — nouvelles clés `nav.admin.printers` + `admin.printers.*`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.8 (lignes 1270-1300)]
- [Source: _bmad-output/planning-artifacts/epics.md#FR-032, FR-076, FR-077, FR-079, FR-098 (largeur configurable, registre thermique/A4, diagnostic, sélection bénévole)]
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 627-632] (arborescence indicative module `print/`)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md, lignes 48, 53, 81-86, 135] (route `/admin/printers`, nav "Imprimantes"/`print_connect`, DialogShellComponent citant explicitement "suppression d'imprimante")
- [Source: _bmad-output/implementation-artifacts/3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques.md] — `PrintQueueService`/`PrinterQueueHandle`/`Printer`, Dev Notes § Statut runtime vs persistance (toujours valable), § Thread par imprimante (pas de pool, peu d'imprimantes), Review Finding déféré "Pas d'endpoint update/delete... explicitement scope de la Story 3.8"
- [Source: _bmad-output/implementation-artifacts/3-7-vue-admin-de-diagnostic-des-imprimantes.md] — `PrintQueueDiagnosticsService` (modèle de service composant `PrinterRepository`+`PrintQueueService`), Dev Notes § Invariant handle/printer, décision de séparation de route `/admin/print-queue` vs `/admin/printers`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java, PrinterQueueHandle.java, PrinterService.java, PrinterController.java] — code actuel (lu intégralement pour cette story)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SerialPrinterConnectivityChecker.java] — usage existant de `com.fazecast.jSerialComm.SerialPort`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/item/service/DepositValidationService.java, lignes 69, 70, 90] — seul consommateur actuel de `PrintQueueService.submit()`, non modifié par cette story (AC5)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintInfrastructureIT.java] — patterns de test réutilisables (`createReachablePrinter`, `waitUntil`, sessions admin/bénévole, imprimante injoignable via port `1`)
- [Source: pluribourse-frontend/src/app/features/admin/users/user-list.component.ts, user-form.component.ts, user-form.component.html] — pattern dialog auto-soumis + rechargement à la fermeture, modèle direct pour `printer-list`/`printer-form`
- [Source: pluribourse-frontend/src/app/features/admin/sellers/seller-list.component.ts] — pattern `confirmDelete()` avec `ConfirmDialogService`
- [Source: pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.html, .ts] — pattern `mat-select` peuplé depuis un appel HTTP, domaine imprimante direct
- [Source: pluribourse-frontend/src/styles.scss, lignes 167-196] — classes `.badge`/`.badge--active`/`.badge--inactive` à réutiliser telles quelles
- [Source: pluribourse-frontend/src/app/features/admin/admin.routes.ts, layout/app-layout/app-layout.component.html] — points d'insertion route + nav
- [Source: pluribourse-frontend/public/i18n/fr.json, lignes 89-95, 489-520] — emplacement `nav.admin.*` et namespace voisin `admin.printQueue.*` (convention de duplication de namespace à suivre, ne pas référencer)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

Aucune anomalie rencontrée. Suite de tests backend complète (`./mvnw test`) : 275 tests, 0 échec. Suite de tests frontend complète (`npm test`) : 413 tests / 49 fichiers, 0 échec.

### Completion Notes List

- Backend : `PrinterQueueHandle.stop()` interrompt le thread consommateur ; `PrintQueueService.unregisterPrinter(id)` retire le handle de la map et l'arrête (idempotent).
- Backend : `PrinterService.list()` (registre AC1, statut = `getLastError() == null`), `listAvailableSerialPorts()` (jSerialComm `getCommPorts()`, sans wrapper de timeout car non-bloquant), `delete()` (suppression BDD puis désenregistrement de la file, dans cet ordre).
- Backend : nouveaux DTOs `PrinterSummaryDto`/`SerialPortDto` (construits manuellement dans le service, pas via MapStruct — projections calculées, cohérent avec `PrintQueueDiagnosticsService`).
- Backend : `PrinterController` complété avec `GET ""`, `GET /serial-ports`, `DELETE /{id}` ; `POST` existant inchangé. Aucune nouvelle exception (réutilisation de `PrinterNotFoundException`).
- Backend : nouvelle classe de test E2E `PrinterRegistryIT` (8 scénarios : liste vide, imprimante joignable/injoignable, ports série, suppression + arrêt de file, soumission après suppression → `PrinterNotFoundException`, 404 id inconnu, 403 bénévole sur les 3 endpoints).
- Frontend : nouveau domaine `features/admin/printers/` (`printer-list`/`printer-form`) suivant le pattern dialog auto-soumis de `UserFormComponent` et le pattern `confirmDelete()`/retrait optimiste de `UserListComponent`/`SellerListComponent`. Badges de statut via les classes globales `.badge`/`.badge--active`/`.badge--inactive` existantes (pas de nouveau chip).
- Frontend : formulaire de création avec bascule des validateurs THERMAL ↔ A4 sur `type.valueChanges` (jamais les deux jeux de champs requis simultanément), chargement des ports série au `ngOnInit`, notification si aucun port détecté.
- Frontend : route `/admin/printers`, item de nav "Imprimantes" (icône `print_connect`) ajouté en tête de la section Gestion (avant "Bénévoles"), namespaces i18n `nav.admin.printers` et `admin.printers.*` autonomes (fr/en), sans référence croisée à `admin.printQueue.*`.
- Frontend : `printer-list.component.spec.ts` (rendu, état vide, dialog de création + rechargement, confirmation + suppression + retrait optimiste + toasts) et `printer-form.component.spec.ts` (bascule des validateurs, chargement des ports série, soumission THERMAL/A4, erreur backend).

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterQueueHandle.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/PrinterSummaryDto.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/SerialPortDto.java` (NEW)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java` (NEW)
- `pluribourse-frontend/src/app/models/printer-registry.model.ts` (NEW)
- `pluribourse-frontend/src/app/services/printer-registry.service.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.html` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.scss` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.spec.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.html` (NEW)
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.spec.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` (UPDATE)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE)
- `pluribourse-frontend/public/i18n/fr.json` (UPDATE)
- `pluribourse-frontend/public/i18n/en.json` (UPDATE)

## Change Log

- 2026-07-22 : Implémentation complète de la story (registre CRUD imprimantes admin, backend + frontend + tests). Statut → review.
- 2026-07-22 : Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). 4 patches appliqués (NPE défensif dans `list()`, reset des champs périmés au bascule de type, validateurs client sur `port`, clé i18n pour l'unité "mm"), 2 items différés dans `deferred-work.md`, 7 signalements rejetés après vérification (dont un faux positif sur le badge de statut, invalidé par relecture du code de `PrinterQueueHandle`). Backend 41/41, frontend 418/418. Statut → done.
- 2026-07-22 : Re-review demandée par l'utilisateur. L'Acceptance Auditor et l'Edge Case Hunter ont détecté que le patch précédent sur le reset des champs oubliait le champ `port`. 2 nouveaux patches appliqués (reset de `port` au bascule A4 → THERMAL, fallback défensif dans `listAvailableSerialPorts()` sur échec de la bibliothèque native), 2 nouveaux items différés dans `deferred-work.md` (gaps systémiques pré-existants : `@Version` sans handler dédié, `@PathVariable Long` mal formé). 10 signalements rejetés après vérification (faux positifs répétés + conventions déjà établies ailleurs dans le code). Backend 41/41, frontend 418/418. Statut → done.
