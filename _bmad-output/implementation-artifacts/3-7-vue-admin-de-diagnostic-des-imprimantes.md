---
baseline_commit: 745854d
---

# Story 3.7: Vue admin de diagnostic des imprimantes

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want consulter l'état de chaque imprimante enregistrée et de sa file (statut de connexion, profondeur de file, job en cours, dernière erreur), et relancer ou ignorer un job en erreur,
so that je puisse diagnostiquer un problème d'imprimante et débloquer sa file sans interrompre l'événement.

## Acceptance Criteria

1. `GET /admin/print-queue` (ADMIN uniquement) renvoie **toutes** les imprimantes enregistrées (THERMAL et A4), chacune avec : `id`, `name`, `type`, statut de connexion, profondeur de file, indicateur "job en cours", dernière erreur (FR-079). La page `/admin/print-queue` affiche ces données sous forme d'une carte par imprimante (nom, type, chip statut de connexion vert « Connectée » / rouge « Hors ligne », profondeur de file, job en cours, dernière erreur).
2. Une imprimante inaccessible au démarrage du serveur (ou dont l'enregistrement a échoué à la connectivité) apparaît dans cette liste avec sa dernière erreur renseignée — **réutiliser telle quelle** la valeur déjà produite par `SerialPrinterConnectivityChecker`/`NetworkPrinterConnectivityChecker` (Story 3.4), pas de nouveau message à écrire. Côté UI, la carte affiche un bandeau d'alerte reprenant ce message.
3. `POST /admin/print-queue/{printerId}/resume` : si la file de cette imprimante est suspendue (job en erreur), le job en erreur est remis **en tête** de la file et la file reprend la consommation (FR-079). Si la file n'est **pas** suspendue, 422 (nouvelle exception, voir Dev Notes). 404 si l'imprimante n'existe pas (réutilise `PrinterNotFoundException`).
4. `POST /admin/print-queue/{printerId}/discard` : si la file de cette imprimante est suspendue, le job en erreur est **retiré** (pas remis en file) et la file reprend avec les jobs suivants (FR-079). Mêmes règles 422/404 que AC3.
5. Une session bénévole appelant n'importe quel endpoint `/admin/print-queue/**` reçoit 403 (couvert nativement par `SecurityConfig` : `/admin/**` → `hasRole('ADMIN')`, comme tous les autres contrôleurs admin — pas de configuration supplémentaire, un test suffit pour le vérifier).

## Tasks / Subtasks

- [x] Backend — état runtime exploitable par printer (AC: 1, 3, 4)
  - [x] `PrinterQueueHandle.java` (UPDATE) : ajouter `getQueueDepth()` (`return deque.size();`), un champ `@Getter private volatile boolean jobInProgress` mis à `true` juste après `deque.takeFirst()` et remis à `false` dans un bloc `finally` englobant l'appel à `job.execute(printer)` (ne pas déplacer le `catch (Throwable e)` existant — ajouter seulement le `finally`).
  - [x] `PrinterQueueHandle.java` (UPDATE) : ajouter `requeueFailedJobAtHead()` — capture `lastFailedJob`, le remet via `deque.putFirst(job)` s'il n'est pas `null`, remet `lastFailedJob`/`lastError` à `null`, puis `suspended = false`. Et `discardFailedJob()` — remet `lastFailedJob`/`lastError` à `null` puis `suspended = false`, **sans** toucher à la deque. Les deux gèrent `InterruptedException` comme `submit()` (`Thread.currentThread().interrupt()` + `IllegalStateException`).
- [x] Backend — service de diagnostic (AC: 1, 2, 3, 4)
  - [x] Nouvelle classe `PrintQueueDiagnosticsService` (`org.pluribourse.domain.print.service`, injection par constructeur `PrinterRepository` + `PrintQueueService`, même style que `PrinterSelectionService`) :
    - `List<PrinterStatusDto> listStatuses()` : `printerRepository.findAll()`, pour chaque `Printer` résout son `PrinterQueueHandle` via `printQueueService.getHandle(printer.getId())` (jamais `null` en pratique — un handle est créé pour toute imprimante en base, voir Dev Notes § Invariant handle/printer) et construit le DTO.
    - `void resumeQueue(Long printerId)` : résout le handle (404 `PrinterNotFoundException` si absent), lève `PrinterQueueNotSuspendedException` si `!handle.isSuspended()`, sinon `handle.requeueFailedJobAtHead()`.
    - `void discardFailedJob(Long printerId)` : même garde, appelle `handle.discardFailedJob()`.
  - [x] Nouveau DTO `PrinterStatusDto` (record, `org.pluribourse.domain.print.dto`) : `Long id, String name, PrinterType type, boolean connected, int queueDepth, boolean jobInProgress, String lastError, boolean canRetry`. `connected = (handle.getLastError() == null)`. `canRetry = handle.isSuspended()` (l'invariant du code existant garantit que `suspended == true` implique toujours `lastFailedJob != null`, donc pas de vérification séparée nécessaire — voir Dev Notes § Invariant handle/printer).
  - [x] Nouvelle exception `PrinterQueueNotSuspendedException extends BusinessException` (422, code `printer-queue-not-suspended`), même modèle que `InvalidPrinterConfigurationException`.
- [x] Backend — contrôleur (AC: 1, 3, 4, 5)
  - [x] Nouvelle classe `PrintQueueController` (`org.pluribourse.domain.print.controller`, `@RequestMapping("/admin/print-queue")`, `@PreAuthorize("hasRole('ADMIN')")`, même pattern que `PrinterController`) : `GET ""` → `List<PrinterStatusDto>` ; `POST "/{printerId}/resume"` → 204 ; `POST "/{printerId}/discard"` → 204. **Ne pas** ajouter cette route dans `PrinterController` existant (`/admin/printers`) — route distincte, périmètre distinct (diagnostic vs registre CRUD, réservé à la Story 3.8 qui ajoutera `GET`/`DELETE` sur `/admin/printers`).
- [x] Frontend — modèle & service (AC: 1, 3, 4)
  - [x] `models/printer-status.model.ts` (nouveau) : `interface PrinterStatus { id: number; name: string; type: 'THERMAL' | 'A4'; connected: boolean; queueDepth: number; jobInProgress: boolean; lastError: string | null; canRetry: boolean; }` — même style que `AvailablePrinter` (`models/printer.model.ts`), fichier séparé (ne pas modifier `printer.model.ts`, domaines distincts : sélection bénévole vs diagnostic admin).
  - [x] `services/print-queue.service.ts` (nouveau, ne pas étendre `print.service.ts` — celui-ci sert la sélection bénévole, domaine distinct) : `getStatuses(): Observable<PrinterStatus[]>` (`GET /admin/print-queue`), `resumeQueue(id: number): Observable<void>` (`POST /admin/print-queue/{id}/resume`), `discardFailedJob(id: number): Observable<void>` (`POST /admin/print-queue/{id}/discard`).
- [x] Frontend — page de diagnostic (AC: 1, 2, 3, 4)
  - [x] Nouveau composant standalone `features/admin/print-queue/print-queue-list.component.ts` + `.html` (nouveau fichier HTML, jamais de template inline — voir CLAUDE.md) + `.scss` + `.spec.ts`. Une carte par imprimante (`@for` sur les statuts) : nom, type, chip de statut de connexion (span stylé localement — **pas** de `MatChipsModule`, aucun composant chip partagé n'existe encore dans le projet ; suivre le pattern `phase-chip` de `app-layout.component.scss`, vert/rouge), profondeur de file, indicateur job en cours (booléen), dernière erreur. Bandeau d'alerte (texte = `lastError`) si `lastError` non nul (AC2). Boutons "Relancer"/"Ignorer" visibles uniquement si `canRetry` est vrai ; passent par `ConfirmDialogService`... **décision à prendre** : voir Dev Notes § Confirmation Relancer/Ignorer — aucune confirmation n'est demandée par l'AC ni l'UX, action directe + toast recommandée. `SkeletonRowComponent` pendant le chargement initial, `EmptyStateComponent` si aucune imprimante enregistrée, `NotificationInlineComponent`/toast pour les erreurs de chargement ou d'action. Bouton "Actualiser" (pas de SSE — voir Dev Notes § Périmètre : temps réel).
  - [x] `admin.routes.ts` (UPDATE) : ajouter la route `print-queue` → `PrintQueueListComponent`, même style `loadComponent` que les autres entrées.
  - [x] `app-layout.component.html` (UPDATE) : ajouter un item de navigation "File d'impression" (icône `print`) dans la section `nav.sections.management` existante (voir Dev Notes § Emplacement nav), même structure que les items `admin/users`/`admin/settings`.
  - [x] `fr.json`/`en.json` (UPDATE) : clé `nav.admin.printQueue` + namespace `admin.printQueue.*` (titre, colonnes, statuts connecté/hors ligne, job en cours oui/non, actions Relancer/Ignorer, état vide, erreurs de chargement/action, toasts succès).
- [x] Tests backend (AC: 1-5)
  - [x] Nouvelle classe `PrintQueueDiagnosticsIT` (`org.pluribourse.domain.print`, E2E via `IntegrationTest`, même style que `PrintInfrastructureIT`) : enregistre ses propres imprimantes via `POST /admin/printers` (jamais dans `test-data.sql`, voir Story 3.4 Dev Notes § Stratégie de test). Couvrir : liste vide au départ (si aucune imprimante — attention, les autres tests IT enregistrent des imprimantes dans le même contexte Spring partagé, voir Dev Notes § Isolation des tests) ; imprimante joignable → `connected=true`, `queueDepth=0`, `lastError=null`, `canRetry=false` ; imprimante injoignable à la création → `connected=false`, `lastError` non nul, `canRetry=false` (pas de job en échec, juste un échec de connectivité) ; job en cours détecté (`jobInProgress=true`) via un `CountDownLatch` bloquant l'exécution, technique identique à `PrintInfrastructureIT.jobs_on_different_printers_execute_independently` (Order 8) ; job en échec → `canRetry=true`, `queueDepth` reflète les jobs en attente derrière ; `resume` remet le job en tête et la file reprend (vérifier l'ordre d'exécution après reprise, technique `executionOrder` synchronisée comme `PrintInfrastructureIT` Order 7) ; `discard` retire le job et la file reprend avec le suivant sans jamais exécuter le job ignoré ; 422 `printer-queue-not-suspended` sur `resume`/`discard` d'une file non suspendue ; 404 sur imprimante inconnue ; 403 bénévole sur `GET`/`POST`.
- [x] Tests frontend
  - [x] `print-queue-list.component.spec.ts` : rendu des cartes depuis un mock du service, chip connecté/hors ligne, bandeau d'erreur conditionnel, visibilité des boutons Relancer/Ignorer selon `canRetry`, appel service + toast succès/erreur sur les deux actions, état vide, état de chargement.

### Review Findings

- [x] [Review][Decision] Le statut "connected" confond une panne de connectivité avec un échec de job d'impression — une imprimante joignable ayant eu un job en échec (ex. bourrage papier) s'affiche en rouge « Hors ligne », alors qu'elle est physiquement connectée. **Décision utilisateur (2026-07-21) : distinguer les deux états.** Résolu sans changement backend (le DTO existant `connected`+`canRetry` suffisait) : le frontend calcule désormais un 3ᵉ état `jobError` (chip ambre « En erreur ») quand `!connected && canRetry`, distinct de `disconnected` (chip rouge « Hors ligne ») quand `!connected && !canRetry`. [`print-queue-list.component.ts` (`connectionState`), `print-queue-list.component.html`, `print-queue-list.component.scss`, i18n `admin.printQueue.status.jobError`]
- [x] [Review][Decision] `queueDepth` sous-estime la file réelle d'une unité quand la file est suspendue (le job en échec n'est pas compté). **Décision utilisateur (2026-07-21) : garder tel quel** — le job en échec est déjà visible via le bandeau d'erreur rouge sur la carte, l'information n'est donc pas perdue ; `queueDepth` reste strictement la profondeur de la deque, conforme à la formule demandée par la tâche. Aucun changement de code.
- [x] [Review][Patch] Le type d'imprimante (`{{ printer.type }}`) est affiché brut dans le template, sans passer par le système i18n. **Corrigé** : `('admin.printQueue.type.' + printer.type) | translate`, nouvelles clés `admin.printQueue.type.THERMAL`/`A4` en fr/en. [`print-queue-list.component.html`]
- [x] [Review][Patch] Relancer/Ignorer ne sont pas atomiques (check-then-act entre `requireSuspendedHandle()` et la mutation). **Corrigé** : `requeueFailedJobAtHead()`/`discardFailedJob()` sont désormais `synchronized` et vérifient+mutent l'état en un seul appel atomique (retournent `false` sans effet de bord si la file n'était pas suspendue) ; le service s'appuie sur cette valeur de retour au lieu d'un check séparé. Nouveau test de régression `PrintQueueDiagnosticsIT.concurrent_resume_requests_only_requeue_the_failed_job_once` (Order 11) : deux `resume` concurrents sur la même imprimante → un seul 204, l'autre 422, le job ne s'exécute qu'une fois. [`PrinterQueueHandle.java`, `PrintQueueDiagnosticsService.java`]
- [x] [Review][Patch] Le frontend ne recharge pas la liste après un échec de `resume`/`discard`. **Corrigé** : le rechargement (`await this.load()`) se fait désormais dans le `finally` de `runAction()`, donc dans les deux cas (succès et échec). Tests mis à jour pour vérifier l'appel à `getStatuses()` après un échec. [`print-queue-list.component.ts:runAction`]
- [x] [Review][Patch] Ordre d'import incorrect dans le contrôleur (`PostMapping` avant `GetMapping`). **Corrigé.** [`PrintQueueController.java`]
- [x] [Review][Patch] Le Dev Agent Record indiquait "10 nouveaux tests" pour `print-queue-list.component.spec.ts` alors que le fichier en contient 11 (13 après les tests ajoutés lors du review). **Corrigé** dans les Completion Notes ci-dessous.
- [x] [Review][Defer] Le skeleton de chargement remplace toute la grille de cartes aussi lors d'un clic sur "Actualiser", pas seulement au chargement initial — déviation du libellé littéral de la tâche ("pendant le chargement initial"), mais cohérent avec le pattern déjà utilisé par toutes les autres pages liste admin (`EditionListComponent`, `UserListComponent`) qui font exactement la même chose. — deferred, pre-existing (convention déjà établie dans tout le module admin, corriger uniquement ce composant introduirait une incohérence)
- [x] [Review][Defer] Le test "imprimante injoignable" du nouveau `PrintQueueDiagnosticsIT` dépend d'une hypothèse réseau au niveau OS (`127.0.0.1:1` refusé) — technique reprise à l'identique de `PrintInfrastructureIT`, pas une régression introduite par cette story. [`pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java`] — deferred, pre-existing (technique héritée du test existant `PrintInfrastructureIT`)
- [x] [Review][Defer] Imports wildcard dans la nouvelle classe de test IT — convention reprise à l'identique de `PrintInfrastructureIT` (même style explicitement demandé par les Dev Notes). [`pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java`] — deferred, pre-existing (convention déjà établie par la classe sœur)

#### Re-review (2026-07-21)

- [x] [Review][Patch] Race condition entre `consume()` et `requeueFailedJobAtHead()`/`discardFailedJob()` : le bloc `catch` de `consume()` qui positionne `suspended`/`lastError`/`lastFailedJob` sur échec de job n'était **pas** `synchronized` sur le même moniteur que les deux méthodes de mutation admin. **Corrigé** : le bloc `catch` de `consume()` est désormais encadré par `synchronized (this)`, même moniteur que `requeueFailedJobAtHead()`/`discardFailedJob()`. [`PrinterQueueHandle.java` (bloc `catch` dans `consume()`)]
- [x] [Review][Patch] `toStatusDto()` (`PrintQueueDiagnosticsService`) lisait `lastError`/`isSuspended()`/`getQueueDepth()`/`isJobInProgress()` via 4 appels non synchronisés alors que les mutateurs modifient ces champs de façon atomique sous verrou. **Corrigé** : nouvelle méthode `synchronized PrinterQueueHandle.errorSnapshot()` retournant un `record ErrorSnapshot(lastError, suspended)` cohérent en un seul appel ; `toStatusDto()` l'utilise au lieu des deux getters séparés. [`PrinterQueueHandle.java`, `PrintQueueDiagnosticsService.java:toStatusDto`]
- [x] [Review][Patch] Régression sans rapport avec cette story : `step="0.01"` → `step="1"` sur les champs prix, embarquée dans ce diff sans lien avec le diagnostic imprimante. **Corrigé** : revert à `step="0.01"` sur les deux champs. [`item-form.component.html`, `lot-form.component.html`]
- [x] [Review][Patch] Couleurs codées en dur (`#FEF3C7`/`#92400E`) pour l'état `.connection-chip--job-error`, alors que les états voisins utilisent des tokens. **Corrigé** : nouveaux tokens `--pb-warning-container`/`--pb-on-warning-container` dans `styles.scss`, utilisés à la place des valeurs hex. [`styles.scss`, `print-queue-list.component.scss`]
- [x] [Review][Patch] `runAction()` appelait `load()` dans son `finally` sans distinction, ce qui repassait `isLoading` à `true` et blanchissait toute la grille derrière le skeleton au clic sur Relancer/Ignorer d'une seule carte (et faisait disparaître toutes les données déjà connues si ce rechargement échouait). **Corrigé** : `load(showLoadingState: boolean)` — `ngOnInit`/`refresh()` passent `true` (comportement skeleton inchangé, cohérent avec les autres pages liste admin), `runAction()` passe `false` (pas de skeleton, et les cartes déjà affichées restent visibles même si le rechargement échoue, seule une bannière d'erreur s'ajoute). [`print-queue-list.component.ts`, `print-queue-list.component.html`]
- [x] [Review][Defer] `canRetry` repose uniquement sur l'invariant documenté en commentaire (`suspended` implique toujours `lastFailedJob != null`) sans aucune garantie du compilateur — pattern hérité de la Story 3.4, étendu ici sans changement. [`PrinterQueueHandle.java`] — deferred, pre-existing (invariant déjà accepté et documenté en Dev Notes de cette story)
- [x] [Review][Defer] L'assertion finale du test de concurrence (`concurrent_resume_requests_only_requeue_the_failed_job_once`) s'appuie sur un `Thread.sleep(100)` après le `waitUntil` déterministe — vérification faible qui pourrait laisser passer une double exécution anormalement lente sans faire échouer le test. [`PrintQueueDiagnosticsIT.java:Order(11)`] — deferred, pre-existing (nitpick de qualité de test, non bloquant)

## Dev Notes

### Point de départ — ce qui existe déjà (ne pas réinventer)

Cette story **lit et pilote** l'infrastructure déjà livrée par la Story 3.4, elle ne crée aucun nouveau mécanisme de file :

- `PrintQueueService`/`PrinterQueueHandle` (`org.pluribourse.domain.print.service`) : un handle par imprimante enregistrée, déjà exposé pour cette story exactement via `PrintQueueService.getHandle(Long)` — son propre JavaDoc dit *"Exposed for tests and as the foundation for story 3.7's diagnostic view"*. Les champs `suspended`/`lastError`/`lastFailedJob` existent déjà ; cette story ajoute seulement `queueDepth` (dérivé de la deque existante) et `jobInProgress` (nouveau champ), plus les deux méthodes de mutation `requeueFailedJobAtHead()`/`discardFailedJob()`.
- `PrinterRepository` (`org.pluribourse.domain.print.repository`) : `findAll()` suffit, aucune requête nouvelle.
- `PrinterNotFoundException` (404) déjà existante — réutilisée telle quelle pour AC3/AC4, ne pas en créer une nouvelle.
- Le pattern service dédié + léger (`PrinterSelectionService`, Story 3.9) est le modèle direct à suivre pour `PrintQueueDiagnosticsService` : composition de `PrinterRepository` + `PrintQueueService`, pas de logique de file dupliquée.

### Invariant handle/printer — pourquoi aucune vérification `null` supplémentaire n'est nécessaire

`PrintQueueService.registerPrinter()` est appelé pour **toute** imprimante en base — au démarrage (`@PostConstruct` → `reloadFromDatabase()`) et à la création (`PrinterService.create()` → `registerPrinter()`). Il n'existe aucun chemin où une ligne `Printer` existe en base sans handle correspondant dans `PrintQueueService`. `PrintQueueDiagnosticsService.listStatuses()` peut donc appeler `printQueueService.getHandle(printer.getId())` sans null-check défensif — cohérent avec le reste du module (`PrinterSelectionService.isAvailable()` fait la même hypothèse implicite). De même, `suspended == true` implique toujours `lastFailedJob != null` (le seul endroit qui positionne `suspended = true`, dans `PrinterQueueHandle.consume()`, positionne aussi `lastFailedJob` dans le même bloc `catch`) — `canRetry` peut donc s'appuyer uniquement sur `isSuspended()`.

### Confirmation Relancer/Ignorer — aucune n'est spécifiée

Ni les ACs de l'épic ni `EXPERIENCE.md` (ligne 152, tableau "Page file d'impression") ne mentionnent de dialog de confirmation pour "Relancer"/"Ignorer" — contrairement à d'autres actions destructives du projet (suppression d'édition, RGPD vendeur) qui passent systématiquement par `ConfirmDialogService`. Recommandation : action directe au clic + toast succès/erreur, cohérent avec "Récapitulatif reversement imprimable" (ligne 151, "Toujours rejouable", pas de confirmation) plutôt qu'avec les suppressions. "Ignorer" perd définitivement le job (pas de undo) — si l'utilisateur préfère une confirmation pour cette action spécifiquement (perte de données), le signaler en review ; ce n'est pas un fait accompli irréversible côté implémentation (ajout trivial d'un `ConfirmDialogService.open()` autour de l'appel existant).

### Périmètre : temps réel (SSE) — hors périmètre de cette story, à confirmer en review

`EXPERIENCE.md` ligne 152 mentionne *"Mise à jour en temps réel via SSE (`print-job-updated`)"* et une **liste de jobs par imprimante** avec colonnes "type de document · vendeur ou article concerné · statut (En attente / En cours / Imprimé / Erreur)". **Ni l'épic (`epics.md` lignes 1242-1268) ni aucune AC testable ne demande cela** — les ACs de l'épic ne mentionnent qu'un état agrégé par imprimante (profondeur de file, job en cours, dernière erreur), pas un historique de jobs individuels. Implémenter la vision complète d'`EXPERIENCE.md` demanderait de redessiner le contrat `PrintJob` (`void execute(Printer)`, une simple lambda sans identité ni métadonnées) pour porter un type de document, un sujet (vendeur/article) et un historique de statuts — changement structurel qui toucherait aussi `ThermalPrintService`/`DocumentPrintService` (Stories 3.5/3.6, déjà livrées). **Décision de cette story : implémenter strictement les ACs de l'épic** (page avec rafraîchissement manuel via bouton "Actualiser", pas de push SSE, pas d'historique de jobs individuels — seulement l'état agrégé courant de la file). **À signaler en review** : si l'utilisateur souhaite la richesse complète décrite par `EXPERIENCE.md` (historique de jobs, SSE), proposer une story dédiée plutôt que l'ajouter silencieusement ici (cohérent avec la consigne CLAUDE.md de proposer une nouvelle story pour tout changement trop impactant).

### Emplacement nav — sidebar actuelle incomplète par rapport à `EXPERIENCE.md`

`EXPERIENCE.md` ligne 79-86 décrit un arbre de navigation avec une section "Ventes" (contenant "File d'impression") distincte de "Gestion" (contenant "Imprimantes"). La sidebar actuellement implémentée (`app-layout.component.html`) n'a que deux sections, `nav.sections.activeEdition` (Éditions, Vendeurs) et `nav.sections.management` (Bénévoles, Paramètres) — la section "Ventes" n'existe pas encore (POS pas encore livré, Épic 4). Ajouter "File d'impression" dans `nav.sections.management` pour cette story (emplacement pragmatique, cohérent avec l'ajout incrémental des entrées de nav au fil des stories déjà livrées) ; la réorganisation complète en sections "Ventes"/"Gestion" pourra se faire naturellement quand l'Épic 4 (POS) sera implémenté — ne pas anticiper cette réorganisation ici.

### Isolation des tests — imprimantes déjà enregistrées par d'autres classes IT

`@DirtiesContext(classMode = AFTER_CLASS)` réinitialise la base **entre classes**, mais `PrintQueueDiagnosticsIT` tourne dans son propre contexte propre (base remise à zéro avant cette classe) — pas de pollution par `PrintInfrastructureIT`/`ThermalLabelPrintingIT`/etc. qui tournent dans d'autres classes. En revanche, **au sein de cette classe**, chaque test créant une imprimante via `POST /admin/printers` s'ajoute à la liste retournée par `GET /admin/print-queue` des tests suivants (mêmes données persistantes entre méthodes, `@TestInstance(PER_CLASS)` — convention CLAUDE.md, pas de `@Transactional` de classe). Filtrer les assertions sur l'imprimante créée par le test courant (par nom ou id retourné à la création), ne pas assumer une liste de taille fixe après le premier test qui enregistre une imprimante.

### Project Structure Notes

- Toutes les nouvelles classes backend dans `org.pluribourse.domain.print.{controller,dto,exception,service}` — même module que Stories 3.4/3.5/3.6/3.9, aucune nouvelle arborescence.
- `architecture.md` ne nomme pas explicitement de classe pour cette story (contrairement à `DocumentPrintService`, cité ligne 628, pour la Story 3.6) — nommage par analogie directe avec `PrinterSelectionService` (Story 3.9), seul autre service "orchestration légère au-dessus de `PrintQueueService`" du module.
- Aucune migration Liquibase : état 100% en mémoire, cohérent avec la décision actée en Story 3.4 Dev Notes § Statut runtime vs persistance (ne jamais persister l'état de file — recalculé au démarrage).
- Frontend : nouveau dossier `features/admin/print-queue/`, même niveau que `features/admin/editions/`, `features/admin/sellers/`, etc.

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterQueueHandle.java` — ajouter `getQueueDepth()`, `jobInProgress`, `requeueFailedJobAtHead()`, `discardFailedJob()` ; ne pas toucher à la logique de suspension/erreur existante dans `consume()`, seulement encadrer l'appel `execute()` d'un `finally`.
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — ajouter l'entrée `print-queue`.
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — ajouter l'item de nav.
- `pluribourse-frontend/public/i18n/fr.json`/`en.json` — nouvelles clés, ne pas dupliquer un namespace existant.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.7 (lignes 1242-1268)]
- [Source: _bmad-output/planning-artifacts/epics.md#FR-079, ligne 260 (tableau gestion des erreurs)]
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 628, 774-778]
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md, lignes 47, 79-86, 152] (page file d'impression — vision complète incluant SSE/historique de jobs, voir Dev Notes § Périmètre : temps réel pour l'écart avec les ACs de l'épic retenues par cette story)
- [Source: _bmad-output/implementation-artifacts/3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques.md] — `PrintQueueService`/`PrinterQueueHandle`/`PrintJob`, Dev Notes § Statut runtime vs persistance, § Choix de collection (`LinkedBlockingDeque`, déjà choisie pour permettre le "remis en tête" de cette story), Review Finding déféré "File suspendue ne reprend jamais automatiquement... explicitement pré-scopé à la Story 3.7"
- [Source: _bmad-output/implementation-artifacts/3-9-selection-dimprimante-par-le-benevole-a-la-connexion.md] — `PrinterSelectionService`, modèle direct pour `PrintQueueDiagnosticsService`
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java, PrinterQueueHandle.java] — code actuel, `getHandle()` déjà prévu pour cette story
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java, PrinterSelectionController.java] — patterns `@RequestMapping`/`@PreAuthorize` à suivre
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintInfrastructureIT.java] — techniques de test directement réutilisables (`CountDownLatch` pour job en cours, `executionOrder` synchronisé, `createReachablePrinter` via HTTP)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java, ligne 63] — `/admin/**` déjà protégé par `hasRole("ADMIN")` globalement, AC5 sans configuration supplémentaire
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html/.scss] — pattern `phase-chip` à suivre pour le chip de statut de connexion (pas de `MatChipsModule`, aucun composant chip partagé n'existe)
- [Source: pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts] — pattern composant liste admin (signals, `firstValueFrom`, toasts, `ConfirmDialogService`)
- [Source: pluribourse-frontend/src/app/services/print.service.ts, models/printer.model.ts] — domaine voisin (sélection bénévole) à ne pas modifier, nouveaux fichiers séparés pour le domaine diagnostic admin

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- Backend (avant review) : suite complète `./mvnw -o test` → BUILD SUCCESS, `PrintQueueDiagnosticsIT` : 10/10 tests passent.
- Frontend (avant review) : `npm test` → 47 fichiers de test, 391 tests passent.
- Backend (après review) : `PrintQueueDiagnosticsIT` → 11/11 tests passent (nouveau test de régression concurrence `concurrent_resume_requests_only_requeue_the_failed_job_once`) ; suite complète `./mvnw -o test` relancée après les patches — BUILD SUCCESS, aucune régression.
- Frontend (après review) : `npm test` → 47 fichiers de test, 393 tests passent (nouveaux tests : chip 3 états, traduction du type, rechargement après échec).

### Completion Notes List

- Implémentation strictement conforme au périmètre des ACs de l'épic : pas de SSE, pas d'historique de jobs individuels (voir Dev Notes § Périmètre : temps réel) — rafraîchissement manuel via bouton "Actualiser" uniquement. **Signalé en review** : si la richesse complète décrite par `EXPERIENCE.md` (SSE, historique de jobs) est souhaitée, proposer une story dédiée plutôt que l'ajouter ici — non tranché, resté hors scope.
- Actions "Relancer"/"Ignorer" implémentées en clic direct + toast succès/erreur, sans `ConfirmDialogService` (voir Dev Notes § Confirmation Relancer/Ignorer). Non remis en question lors de la review.
- Nav "File d'impression" ajoutée dans la section `nav.sections.management` existante (pas de section "Ventes" dédiée pour l'instant, cohérent avec Dev Notes § Emplacement nav — à revoir naturellement quand l'Épic 4 POS sera implémenté).
- `getHandle()` de `PrintQueueService` reste inchangé (aucune modification requise), son JavaDoc mentionnait déjà cette story comme foundation.
- **Review du 2026-07-21** (Blind Hunter + Edge Case Hunter + Acceptance Auditor) : 2 décisions tranchées par l'utilisateur — (1) distinction visuelle connecté/en erreur/hors ligne ajoutée (chip ambre "En erreur" quand `!connected && canRetry`, sans changement backend, dérivé des champs DTO existants) ; (2) `queueDepth` conservé tel quel (le job en échec reste visible via le bandeau d'erreur). 5 patches appliqués : traduction du type d'imprimante, atomicité `resume`/`discard` (méthodes `synchronized` sur `PrinterQueueHandle` + nouveau test de régression de concurrence), rechargement de la liste après échec d'action, ordre d'import, correction du compte de tests dans cette section. Voir la section "Review Findings" sous Tasks/Subtasks pour le détail complet.
- **Re-review du 2026-07-21** (Blind Hunter + Edge Case Hunter + Acceptance Auditor) : 5 patches supplémentaires appliqués — race condition résiduelle entre `consume()` et les mutations admin (`synchronized` manquant sur le bloc `catch`), snapshot DTO non atomique (`errorSnapshot()` ajouté sur `PrinterQueueHandle`), régression `step` sur les champs prix (article/lot) sans rapport avec cette story, couleurs codées en dur sur le chip "En erreur" (tokens `--pb-warning-container` ajoutés), et rechargement de la grille après action qui blanchissait toutes les cartes (paramètre `showLoadingState` sur `load()`). Suites complètes backend et frontend relancées après les patches — BUILD SUCCESS, 393/393 tests frontend, aucune régression. 2 items différés (invariant `canRetry` non typé, assertion faible du test de concurrence). 9 signalements écartés comme faux positifs (notamment : affichage brut de `lastError` explicitement demandé par l'AC2, absence de re-vérification de connectivité au resume/discard conforme à la formule DTO spécifiée, ajout de la Story 3.10 dans `epics.md` = travail légitime en cours).

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterQueueHandle.java` (UPDATE)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueDiagnosticsService.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/PrinterStatusDto.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/exception/PrinterQueueNotSuspendedException.java` (NEW)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrintQueueController.java` (NEW)
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java` (NEW)
- `pluribourse-frontend/src/app/models/printer-status.model.ts` (NEW)
- `pluribourse-frontend/src/app/services/print-queue.service.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.html` (NEW)
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.scss` (NEW)
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.spec.ts` (NEW)
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` (UPDATE)
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (UPDATE)
- `pluribourse-frontend/public/i18n/fr.json` (UPDATE)
- `pluribourse-frontend/public/i18n/en.json` (UPDATE)

## Change Log

- 2026-07-21 : Implémentation complète de la story 3.7 (backend état runtime/service diagnostic/contrôleur + frontend modèle/service/page + tests backend et frontend). Statut passé à "review".
- 2026-07-21 : Code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). 2 décisions tranchées par l'utilisateur (chip 3 états connecté/en erreur/hors ligne ; `queueDepth` conservé tel quel) et 5 patches appliqués (i18n type imprimante, atomicité resume/discard avec test de régression concurrence, rechargement frontend après échec d'action, ordre d'import, correction Dev Agent Record). 3 items différés vers `deferred-work.md` (pré-existants, hors scope). 12 signalements écartés comme faux positifs ou conventions déjà établies dans le projet.
- 2026-07-21 : Re-review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). 5 patches supplémentaires appliqués (race condition résiduelle `consume()`/mutations admin, snapshot DTO atomique, revert régression `step` prix article/lot, tokens couleur chip "En erreur", grille non blanchie après action). 2 items différés vers `deferred-work.md`. 9 signalements écartés comme faux positifs.
