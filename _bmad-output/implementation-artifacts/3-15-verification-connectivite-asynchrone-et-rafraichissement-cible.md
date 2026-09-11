---
baseline_commit: 47725f09d37d87aef81932a3bf27f1dc99e51d3e
---

# Story 3.15: Vérification de connectivité asynchrone et rafraîchissement ciblé

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want que l'ajout d'une imprimante soit immédiat et que la connectivité reste à jour sans action bloquante de ma part,
so that je puisse enregistrer plusieurs imprimantes rapidement et diagnostiquer une imprimante précise sans attendre.

> **Story ajoutée après coup (2026-09-11)** — implémente le correctif issu du correct-course sur les
> Stories 3.7, 3.11, 3.13 (voir `sprint-change-proposal-2026-09-11.md`, approuvé par Manerial le
> 2026-09-11). Retire le check de connectivité **bloquant** effectué aujourd'hui dans le cycle
> requête/réponse de `POST /admin/printers` ; le remplace par (a) un statut connu **déjà renvoyé**
> par `discover()` et seedé sans nouvel appel réseau, (b) une vérification périodique en tâche de
> fond, (c) un rafraîchissement manuel ciblé par imprimante. Amende les Stories 3.7, 3.11 et 3.13
> (déjà appliqué dans `epics.md`/`prd.md`/`architecture.md`/le fichier de la Story 3.7 par cette
> même session, avant la création de ce fichier — rien à committer séparément côté planification).

## Acceptance Criteria

1. **Étant donné** que l'admin enregistre une imprimante détectée par `discover()`
   **Quand** la création est soumise
   **Alors** elle est persistée et son état de connectivité initialisé immédiatement à partir du
   statut déjà connu de `discover()`, sans nouvel appel à PrinterBridge (Story 3.11 amendée)

2. **Étant donné** que le serveur est démarré
   **Quand** le délai configuré (`printer.connectivity.refresh.interval`, modèle
   `pos.basket.reaper.interval`) s'écoule
   **Alors** toutes les imprimantes enregistrées non suspendues sont revérifiées auprès de
   PrinterBridge en tâche de fond, sans action de l'admin (FR-079 amendé)

3. **Étant donné** qu'un job d'impression est en cours sur une imprimante au moment où le scheduler
   l'atteint
   **Quand** la vérification de connectivité s'exécute
   **Alors** aucune interférence n'est introduite — délégué à PrinterBridge
   (`PrinterLocks.tryLock()` non bloquant côté PrinterBridge)

4. **Étant donné** que l'admin consulte `/admin/printers`
   **Quand** il déclenche le rafraîchissement d'une imprimante précise
   **Alors** seule cette imprimante est revérifiée en direct auprès de PrinterBridge, sans affecter
   les autres

5. **Étant donné** les boutons "Actualiser" globaux existants sur `/admin/printers` et
   `/admin/print-queue`
   **Quand** cette story est livrée
   **Alors** ils sont retirés, ainsi que l'endpoint `POST /admin/print-queue/refresh` et les tests
   qui l'exercent exclusivement

6. **Étant donné** le dialog "Ajouter une imprimante"
   **Quand** un enregistrement réussit
   **Alors** le comportement suit l'AC amendée de la Story 3.13 (retrait local de la liste
   détectée, pas de fermeture du dialog)

## Tasks / Subtasks

- [x] **Backend — `CreatePrinterDto` transporte le statut connu de `discover()` (AC: 1)**
  - [x] `CreatePrinterDto` (record, UPDATE) : ajouter `@NotNull PrinterStatus status` en 5ᵉ champ
    (après `printerBridgeId`). ⚠️ **C'est un record — voir Dev Notes § Rayon d'impact de
    `CreatePrinterDto` avant de toucher un seul fichier de test.**
  - [x] `PrinterMapper.toEntity()` : aucun changement — `status` n'existe pas sur `Printer` (état
    purement en mémoire, invariant inchangé depuis la Story 3.4), MapStruct ignore silencieusement
    ce champ source non mappé (pas de `unmappedSourcePolicy` strict configuré sur ce projet).
- [x] **Backend — `PrintQueueService` : enregistrement seedé vs. rechargement live (AC: 1, 3)**
  - [x] `registerPrinter(Printer printer)` (existant) : **aucun changement** — reste le chemin
    "live check", utilisé uniquement par `reloadFromDatabase()` (démarrage serveur, AC amendée de
    la Story 3.11).
  - [x] Nouvelle surcharge `registerPrinter(Printer printer, PrinterStatus knownStatus)` : construit
    le `PrinterQueueHandle`, seede `lastError` directement depuis `knownStatus` (`OFFLINE` → message
    d'erreur explicite type "signalée hors ligne par PrinterBridge au moment de la découverte" ;
    `ONLINE`/`UNKNOWN` → `lastError=null`, même philosophie fail-open que les checkers existants)
    **sans jamais appeler `PrinterConnectivityChecker.checkAccessibility()`**, puis `handle.start()`.
    Utilisée uniquement par `PrinterService.create()`.
  - [x] Extraire la logique de `refreshConnectivity()` (boucle) dans une méthode privée
    `refreshOne(Printer printer)` (skip si handle absent/suspendu, sinon appelle le checker et met à
    jour `lastError` — code strictement identique à aujourd'hui, juste factorisé).
  - [x] Nouvelle méthode publique `refreshConnectivity(Long printerId)` : résout le `Printer`
    (`PrinterNotFoundException` sinon), appelle `refreshOne(printer)` — c'est la version "une seule
    imprimante" de la méthode existante, réutilisée par le rafraîchissement ciblé (AC4) **et** par
    rien d'autre (le scheduler, lui, continue d'appeler la version toutes-imprimantes existante).
- [x] **Backend — `PrinterService` : création seedée + rafraîchissement ciblé (AC: 1, 4)**
  - [x] `create(CreatePrinterDto dto)` : remplacer `printQueueService.registerPrinter(printer)` par
    `printQueueService.registerPrinter(printer, dto.status())`.
  - [x] Extraire `toSummaryDto(Printer)` du lambda de `list()` (aucun changement de comportement,
    juste factorisé pour être réutilisé ci-dessous).
  - [x] Nouvelle méthode `PrinterSummaryDto refreshConnectivity(Long id)` : résout le `Printer`
    (`PrinterNotFoundException` sinon), appelle `printQueueService.refreshConnectivity(id)`, retourne
    `toSummaryDto(printer)` (reflète l'état déjà à jour, `printQueueService.refreshConnectivity` est
    synchrone).
- [x] **Backend — nouvel endpoint de rafraîchissement ciblé (AC: 4)**
  - [x] `PrinterController` : `POST /admin/printers/{id}/refresh-connectivity` → `PrinterSummaryDto`
    (même famille que `POST /admin/printers/{id}/test-print`, `ADMIN` uniquement via le
    `@PreAuthorize` de classe déjà en place).
- [x] **Backend — scheduler périodique (AC: 2, 3)**
  - [x] Nouveau `PrinterConnectivityRefreshService`
    (`org.pluribourse.domain.print.service`, même package que `PrintQueueService`) — **modèle exact
    `BasketReaperService`** (`pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketReaperService.java`,
    lu intégralement pour cette story) : `@Service`, `@Slf4j`, `@RequiredArgsConstructor`,
    `@ConditionalOnProperty(name = "printer.connectivity.refresh.enabled", matchIfMissing = true)`.
    Une seule méthode `@Scheduled(fixedDelayString = "${printer.connectivity.refresh.interval:PT5M}",
    initialDelayString = "${printer.connectivity.refresh.interval:PT5M}")` qui appelle
    **directement** `printQueueService.refreshConnectivity()` (méthode toutes-imprimantes
    existante, inchangée — déjà couverte par `PrintQueueDiagnosticsIT` @Order(12-14) *avant* leur
    suppression, voir tâche tests ci-dessous : sa logique de skip des imprimantes suspendues et son
    comportement stale-detection sont déjà éprouvés, ce scheduler ne fait que l'appeler sur un
    timer). `@PostConstruct` avec un log `info` d'activation (même raison que
    `BasketReaperService.logActivation()` : seul signal qu'un opérateur a de vérifier que le
    scheduler est bien câblé).
  - [x] `SchedulingConfig.java` (UPDATE, Javadoc uniquement) : ajouter ce 3ᵉ `@Scheduled` à la liste
    documentée (garde `@ConditionalOnProperty`, même famille que `BasketReaperService`).
- [x] **Backend — retrait du rafraîchissement global (AC: 5)**
  - [x] `PrintQueueController` : retirer `POST /admin/print-queue/refresh` (`refreshStatuses()`).
  - [x] `PrintQueueDiagnosticsService` : retirer `refreshStatuses()`. **Ne pas toucher**
    `PrintQueueService.refreshConnectivity()` (sans argument) — il reste le mécanisme interne
    utilisé par le nouveau scheduler.
- [x] **Backend — configuration (AC: 2)**
  - [x] `application.properties` (main) : ajouter, à côté du bloc `pos.basket.*` (même style de
    commentaire justifiant l'ordre des durées) :
    `printer.connectivity.refresh.interval=PT5M` (délai plus long que `pos.basket.reaper.interval`
    par choix délibéré — un scan Bluetooth via `PrinterLocks`/RFCOMM n'est pas gratuit côté
    PrinterBridge, contrairement à un ping réseau, voir `sprint-change-proposal-2026-09-11.md` §1
    Evidence) et `printer.connectivity.refresh.enabled=true`.
  - [x] `src/test/resources/application.properties` : ajouter
    `printer.connectivity.refresh.enabled=false` (même raison que `pos.basket.reaper.enabled=false`
    / `sse.keepalive.enabled=false` : le scheduler ne doit jamais interférer avec les IT
    story-board ordonnées qui manipulent `PrinterBridgeDouble` entre méthodes).
- [x] **Frontend — payload de création transporte le statut (AC: 1)**
  - [x] `printer-registry.model.ts` : `CreatePrinterPayload` gagne `status: 'ONLINE' | 'OFFLINE' |
    'UNKNOWN'`.
  - [x] `printer-form.component.ts`, `onSubmit()` : ajouter `status: this.selectedPrinter()!.status`
    au payload (`selectedPrinter` est déjà un `DiscoveredPrinter`, qui porte déjà `status` —
    aucun nouvel appel réseau requis côté frontend non plus).
- [x] **Frontend — le dialog reste ouvert après un enregistrement réussi (AC: 6, Story 3.13 amendée)**
  - [x] `printer-form.component.ts`, `onSubmit()` : remplacer `this.dialogRef.close()` par le même
    patron que `ignoreRow()` juste en dessous — retirer l'imprimante tout juste enregistrée de
    `this.discoveredPrinters` (`update(list => list.filter(...))`) puis `this.backToList()` (déjà
    présent, remet `selectedPrinter`/le formulaire à zéro). Fermer le dialog (`dialogRef.close()`)
    **seulement** si `discoveredPrinters()` devient vide après ce retrait.
  - [x] `printer-list.component.ts`, `openCreateDialog()` : **aucun changement** — `ref.closed`
    continue de recharger la liste `/admin/printers` en arrière-plan à la fermeture finale du
    dialog ; les enregistrements successifs pendant que le dialog reste ouvert ne rafraîchissent pas
    la table de fond avant cette fermeture, comportement acceptable non couvert par l'AC.
- [x] **Frontend — rafraîchissement ciblé par ligne (AC: 4)**
  - [x] `printer-registry.service.ts` : nouvelle méthode `refreshConnectivity(id: number):
    Observable<PrinterSummary>` → `POST /api/admin/printers/${id}/refresh-connectivity`.
  - [x] `printer-list.component.ts` : nouveau signal `refreshingId = signal<number | null>(null)` +
    méthode `refreshConnectivity(printer: PrinterSummary)` (patron `testPrint()` juste au-dessus :
    spinner via le signal, met à jour l'entrée correspondante dans `this.printers` avec la
    `PrinterSummary` retournée — `list.map(p => p.id === printer.id ? updated : p)` — toast
    succès/erreur).
  - [x] `printer-list.component.html` : nouveau bouton icône `refresh` dans `.actions-cell`, à côté
    de "Tester l'impression".
- [x] **Frontend — retrait du rafraîchissement global sur `/admin/print-queue` (AC: 5)**
  - [x] `print-queue.service.ts` : retirer `refreshStatuses()`.
  - [x] `print-queue-list.component.ts` : retirer `refresh()`, le paramètre `live` de `load()`
    (devient `load(showLoadingState: boolean)`, appelle toujours `getStatuses()`) et son usage dans
    `ngOnInit()`/`runAction()`.
  - [x] `print-queue-list.component.html` : retirer le bouton "Actualiser" du header.
- [x] **i18n (AC: 4, 5)**
  - [x] `fr.json`/`en.json`, namespace `admin.printers.*` : ajouter `actions.refreshConnectivity`,
    `success.refreshConnectivity`, `error.refreshConnectivity`.
  - [x] `fr.json`/`en.json`, namespace `admin.printQueue.*` : retirer `refresh` et `error.refresh`
    (devenus inutilisés — le bouton et son handler disparaissent).
- [x] **Tests backend (AC: 1-5)**
  - [x] Appliquer l'inventaire complet du § Rayon d'impact de `CreatePrinterDto` (Dev Notes) — ne
    **rien** oublier, sous peine de compilation cassée dans des fichiers de test sans rapport avec
    cette story (renderers PDF, clôture d'édition...).
  - [x] `PrintInfrastructureIT` @Order(5) (`create_printer_with_unreachable_target_still_succeeds_and_is_marked_in_error`)
    : adapter le commentaire/l'intention — ce test validait jusqu'ici un **vrai appel réseau** au
    double PrinterBridge à la création ; il doit désormais prouver que passer `status=OFFLINE` dans
    le payload **seed** `lastError` sans aucun appel réseau. Renforcer l'assertion en dépeuplant le
    double (`printerBridgeDouble.unregister(bridgeId)` avant l'appel `POST`) : si un appel live
    subsistait par erreur, PrinterBridge répondrait 404→OFFLINE quand même (faux négatif) — retirer
    l'enregistrement au double avant la création est ce qui rend ce test capable de détecter une
    régression vers l'ancien comportement.
  - [x] `PrinterRegistryIT` @Order(5) (`unreachable_printer_appears_disconnected_in_the_registry`) :
    même renforcement — dépeupler le double pour le `bridgeId` avant `createPrinter(...,
    PrinterStatus.OFFLINE)`.
  - [x] `PrintQueueDiagnosticsIT` : retirer @Order(12) `refresh_detects_a_printer_that_went_offline_since_registration`,
    @Order(13) `refresh_detects_a_printer_that_came_back_online_since_registration`, @Order(14)
    `refresh_does_not_touch_a_suspended_printer`, et la méthode privée `refreshStatuses()`
    (n'appelait que l'endpoint supprimé). Dans @Order(10) `volunteer_session_is_forbidden_on_every_endpoint`,
    retirer l'assertion `POST /api/admin/print-queue/refresh`.
  - [x] Migrer l'essentiel de la couverture perdue (@Order 12-14) vers le **nouvel** endpoint ciblé,
    dans `PrinterRegistryIT` (propriétaire naturel de `/admin/printers`) : un printer online→offline
    détecté par `POST /{id}/refresh-connectivity` (pas par un simple GET, qui reste sur l'état
    caché), un offline→online, et un printer suspendu (job en échec) dont `refresh-connectivity` ne
    touche pas `lastError`/`suspended` (même invariant que `PrinterQueueHandle`, testé aujourd'hui
    par @Order(14) avant suppression) — plus un test 404 pour un id inconnu et un 403 volontaire
    pour une session bénévole sur ce nouvel endpoint.
  - [x] Nouveau test d'intégration pour le scheduler — **modèle exact `PosBasketReaperIT`**
    (`pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketReaperIT.java`, lu
    intégralement) : `@TestPropertySource(properties = {"printer.connectivity.refresh.enabled=true",
    "printer.connectivity.refresh.interval=PT1H", "spring.datasource.url=jdbc:h2:mem:printer-refresh-testdb;..."})`
    pour forcer un contexte Spring dédié (le bean scheduler n'existe pas dans le contexte partagé,
    `enabled=false`) **et** `@DynamicPropertySource` démarrant son propre `PrinterBridgeDouble`
    (même patron que `PrinterRegistryIT`/`PrintQueueDiagnosticsIT`). Enregistre un printer online,
    le double bascule offline, invoque `printerConnectivityRefreshService.refreshAll()` (nom exact
    de la méthode `@Scheduled`) directement, vérifie `lastError` mis à jour via
    `printQueueService.getHandle(id)`.
  - [x] `PrinterSelectionIT` : vérifier après migration que `bridge-unavailable` (Order 2, `status=
    PrinterStatus.OFFLINE` dans le payload de création) rend toujours le printer indisponible à
    `GET /api/printers/available` (Order 3) — comportement inchangé pour l'appelant, seul le chemin
    interne change.
- [x] **Tests frontend**
  - [x] `printer-form.component.spec.ts` : `status` présent dans le payload envoyé ; soumission
    réussie ne ferme plus le dialog tant que `discoveredPrinters()` n'est pas vide ; fermeture
    automatique quand la liste devient vide après un enregistrement.
  - [x] `printer-list.component.spec.ts` : nouveau bouton de rafraîchissement ciblé — succès (met à
    jour la ligne), erreur (toast), état désactivé pendant l'appel.
  - [x] `print-queue-list.component.spec.ts` : retirer les cas de test du bouton "Actualiser"
    supprimé (`refresh()`, appel à `refreshStatuses()`).

### Review Findings

- [x] [Review][Defer] Rafraîchissement de connectivité concurrent peut rompre l'invariant
  torn-state documenté, désormais déclenché automatiquement toutes les 5 min — `refreshOne()`
  (`PrintQueueService.java:145-159`, boucle appelée par le nouveau scheduler AC2 **et** le nouvel
  endpoint ciblé AC4) lit `isSuspended()`/écrit `setLastError(null)` sans synchronisation, alors que
  `PrinterQueueHandle.consume()` (`PrinterQueueHandle.java:132-165`) écrit `lastError`/`suspended`
  dans un bloc `synchronized(this)` au même instant si un job échoue. La race existait déjà avant
  cette story (logique de `refreshOne()` = extraction identique de l'ancien `refreshConnectivity()`).
  — deferred, décision Manerial (revue du 2026-09-11) : une fois l'état torn produit, `isSuspended()`
  reste `true` donc `refreshOne()` s'arrête tout de suite sur les passages suivants (la garde de skip
  elle-même) — impact réel = texte de dernière erreur vide sur une file par ailleurs correctement
  affichée "suspendue" avec Relancer/Ignorer fonctionnels, pas une panne masquée. Probabilité faible
  sur une petite installation (1 RPi, quelques postes, pannes d'impression physiques déjà rares) ;
  fix trivial si besoin plus tard (`synchronized(handle)` autour du checker + `setLastError` dans
  `refreshOne()`, même monitor que `consume()`/`requeueFailedJobAtHead()`/`discardFailedJob()`/
  `errorSnapshot()`).
- [x] [Review][Patch] Imprimante `UNKNOWN` (Bluetooth/thermique, cas majoritaire FR-077) affichée
  "connectée" dès la création sans aucune vérification serveur — `registerPrinter(Printer,
  PrinterStatus)` (`PrintQueueService.java:71-78`) traitait `UNKNOWN` exactement comme `ONLINE`.
  Décision Manerial (revue du 2026-09-11) : `UNKNOWN` doit rester visuellement distinct, pas
  "connecté". **Appliqué** : nouveau champ `PrinterQueueHandle.pendingVerification` (seedé `true`
  pour `UNKNOWN`, effacé par le premier vrai check dans `refreshOne()`), propagé sur
  `PrinterSummaryDto`/`PrinterStatusDto` (champ additif), 3ᵉ état badge "Vérification en attente" sur
  `/admin/printers` et `/admin/print-queue`, clés i18n `admin.printers.status.pendingVerification` /
  `admin.printQueue.status.pendingVerification` (fr/en). Tests : `PrinterRegistryIT` @Order(24-25),
  `PrinterConnectivityRefreshIT` @Order(2), `printer-list.component.spec.ts`,
  `print-queue-list.component.spec.ts`.
- [x] [Review][Patch] Double lecture BDD + double branche 404 dupliquée dans
  `PrinterService.refreshConnectivity(id)` [PrinterService.java:82-86, PrintQueueService.java:135-138]
  — **Appliqué** : `PrintQueueService.refreshConnectivity(Long)` retourne désormais le `Printer`
  résolu, réutilisé par `PrinterService.refreshConnectivity(id)` au lieu d'un second `findById`.
- [x] [Review][Patch] `registerPrinter(Printer, PrinterStatus)` saute le garde-fou `containsKey` que
  son overload jumelle utilise, avant un `putIfAbsent` qui pourrait sinon laisser un thread consommateur
  orphelin [PrintQueueService.java:71-78] — **Appliqué** : même garde `containsKey` ajoutée en tête
  de méthode.
- [x] [Review][Defer] Rafraîchissement ciblé d'une imprimante suspendue renvoie 200 + toast succès
  alors que rien n'a été vérifié [PrintQueueService.java:145-149, printer-list.component.ts:64-75] —
  deferred, pre-existing (comportement de skip déjà existant avant cette story, invariant documenté et
  testé — seule la visibilité par-imprimante est nouvelle)

### Revue complémentaire (bmad-code-review, session fraîche, 2026-09-11)

Seconde revue à 3 couches parallèles (Blind Hunter + Edge Case Hunter + Acceptance Auditor) sur le
même diff, dans une nouvelle session sans mémoire de la revue ci-dessus.

- [x] [Review][Decision] Badge "Vérification en attente" sur `/admin/print-queue` sans aucun levier
  d'action sur cette page — cette story retire le bouton "Actualiser" global de `/admin/print-queue`
  (AC5) mais y introduit ce nouvel état `pendingVerification` (badge affiché, aucune action possible
  depuis cette page pour le résoudre : il faut attendre le scheduler (jusqu'à 5 min) ou naviguer vers
  `/admin/printers` pour un rafraîchissement ciblé). Décision Manerial (revue du 2026-09-11) :
  **accepté tel quel** — le scheduler rattrape sous 5 min et `/admin/printers` offre déjà le
  rafraîchissement ciblé, pas d'action supplémentaire nécessaire sur cette page pour une petite
  installation.
- [x] [Review][Patch] `PrintQueueService.registerPrinter(Printer, PrinterStatus)`
  [PrintQueueService.java:75-90] : le garde `containsKey`+`putIfAbsent` n'était pas atomique — deux
  créations concurrentes sur le même id (théoriquement inatteignable avec les IDs auto-incrémentés
  actuels, mais le garde prétendait s'en protéger) pouvaient toutes deux démarrer un thread
  consommateur, l'une restant orpheline. **Appliqué** : construit le handle puis `putIfAbsent`, arrête
  (`handle.stop()`) le handle perdant si `putIfAbsent` renvoie une entrée existante.
- [x] [Review][Patch] `print-queue-list.component.ts::connectionState()`
  [print-queue-list.component.ts:38-49] : l'ordre des branches testait `printer.connected` avant
  `printer.canRetry` — pendant l'état "torn" déjà documenté ci-dessus (race `refreshOne()`/`consume()`),
  une file réellement suspendue avec `lastError` remis à `null` s'affichait comme "Connecté" (chip
  verte) alors que les boutons Relancer/Ignorer apparaissaient quand même (dérivés de `canRetry`, lu
  séparément), sans aucun message d'erreur visible. **Appliqué** : `canRetry` testé avant `connected`,
  aligné sur `printer-list.component.ts` qui teste déjà son état prioritaire (`pendingVerification`)
  en premier. Nouveau test `print-queue-list.component.spec.ts` (fixture torn-state
  `SUSPENDED_BUT_CONNECTED_PRINTER`).
- [x] [Review][Defer] Race `refreshOne()`/`consume()` (torn state) — reconfirmée en lecture de code
  indépendante (`setLastError`/`setPendingVerification` sont de simples écritures `volatile`, hors de
  tout bloc `synchronized`, contrairement à `consume()`/`errorSnapshot()`) — deferred, pre-existing,
  décision Manerial déjà actée ci-dessus. Précision apportée par cette revue : une fois le patch
  `connectionState()` ci-dessus appliqué, l'impact réel correspond exactement à l'hypothèse déjà
  validée (texte d'erreur vide sur une file correctement affichée "En erreur" avec Relancer/Ignorer
  fonctionnels) — avant ce patch, le badge affichait à tort "Connecté".
- [x] [Review][Defer] `PrinterService.toSummaryDto()` [PrinterService.java:70-75] lit
  `handle.getLastError()` puis `handle.isPendingVerification()` séparément (pas via un instantané
  atomique comme `errorSnapshot()`) — deferred, impact mineur : `PrinterSummaryDto` n'expose pas
  `suspended`, donc l'incohérence possible entre les deux lectures reste sans conséquence visible
  sérieuse.
- [x] [Review][Defer] `POST /admin/printers/{id}/refresh-connectivity` sans aucun throttling, alors
  que le scan Bluetooth "n'est pas gratuit" (justification même de l'intervalle du scheduler) —
  deferred, pas d'urgence à l'échelle actuelle (1 RPi, peu d'imprimantes, action admin explicite), à
  surveiller si l'usage s'intensifie.
- [x] [Review][Defer] Race `DELETE /admin/printers/{id}` concurrent vs. rafraîchissement ciblé en
  cours [PrintQueueService.java:149-153, PrinterService.java delete/refreshConnectivity] : peut
  renvoyer 200 au lieu de 404 pour une imprimante supprimée entre-temps — deferred, edge case rare,
  impact mineur, cohérent avec la tolérance déjà assumée pour ce type de race dans cette story.
- [x] [Review][Defer] `PrinterConnectivityRefreshService` partage le thread unique par défaut du
  scheduler Spring avec `BasketReaperService`/keepalive SSE (risque de contention déjà documenté dans
  `deferred-work.md` pour 2 tâches, désormais 3 avec ce scheduler) — deferred, non réévalué par le
  sprint-change-proposal 2026-09-11, à signaler dans `deferred-work.md`.
- [x] [Review][Defer] Rafraîchissement ciblé sur une imprimante suspendue renvoie 200 + toast succès
  sans vérification réelle — deferred, déjà identifié dans le Review Findings ci-dessus (revue
  précédente), reconfirmé inchangé par cette seconde revue.

Findings écartés (dismiss, 14) : confiance totale au statut client sans revérification serveur (c'est
l'objectif même de l'AC1/SCP approuvée) ; absence de test de l'absence du bean quand
`enabled=false` (cohérent avec la même lacune préexistante sur `BasketReaperService`) ; le test du
scheduler n'exerce jamais le vrai déclenchement temporisé (modèle exact `PosBasketReaperIT`, même
patron) ; message d'erreur backend en anglais non-i18n (cohérent avec `describeError()` préexistant) ;
l'AC5 présuppose à tort qu'un bouton "Actualiser" global existait déjà sur `/admin/printers`
(remarque factuelle hors-diff déjà signalée par la revue précédente) ; `UNKNOWN`+`pendingVerification`
au-delà du texte littéral de l'AC1 (déjà décidé/patché, decision D2 revue précédente) ; fragilité
générale des records Java à constructeur positionnel en fixture de test (dette de conception globale,
pas une régression de ce diff) ; chiffres de couverture cités dans le changelog non vérifiables depuis
le diff seul (méthodologique) ; champ additif sur `PrinterSummaryDto`/`PrinterStatusDto` sans
politique de versionnement (non-problème technique, JSON additif sûr) ; hypothèse de sécurité AC3
reposant sur `PrinterLocks` côté PrinterBridge, dépôt externe non vérifiable depuis ce diff ;
suppression de `POST /admin/print-queue/refresh` sans vérifier de consommateurs externes (application
self-hosted, aucun consommateur externe connu) ; `pendingVerification` masquant un job en échec sur
`printer-list.component.ts` (non-problème : cette page n'expose pas du tout l'état de job, scope
différent de `/admin/print-queue`) ; statut seedé potentiellement périmé si plusieurs imprimantes
sont enregistrées dans la même ouverture de dialog (AC6) sans rappel de `discover()` (compromis
assumé par le design approuvé de cette story, rattrapé par le scheduler sous 5 min) ; couverture de
test asymétrique (renforcement anti-régression appliqué seulement aux cas `OFFLINE`, jamais
`ONLINE`) — trou mineur, code jugé sûr par lecture, non bloquant.

## Dev Notes

### Ce qui existe déjà (ne pas réinventer)

- `PrintQueueService`/`PrinterQueueHandle` : orchestration des files, `lastError`/`suspended`,
  `errorSnapshot()`. Le contrat `submit(printerId, job)` (ARCH-009, gelé depuis la Story 3.12) reste
  **strictement inchangé** — cette story ne touche à rien du côté exécution des jobs.
  `refreshConnectivity()` (sans argument, boucle toutes-imprimantes, skip des suspendues) **existe
  déjà** et fait exactement ce qu'il faut pour le scheduler (AC2/AC3) — ne pas le réécrire, l'appeler
  depuis le nouveau `@Scheduled`.
- `PrinterConnectivityChecker`/`NetworkPrinterConnectivityChecker`/`ThermalPrinterConnectivityChecker`
  : **aucun changement**. Le contrat (`checkAccessibility` lève si inaccessible) reste utilisé tel
  quel par `reloadFromDatabase()` (démarrage) et par `refreshConnectivity()`/`refreshConnectivity(id)`
  (scheduler + rafraîchissement ciblé) — seule la **création** (`PrinterService.create()`) cesse de
  passer par eux.
- `PrinterQueueHandle.setLastError()` est déjà package-private dans
  `org.pluribourse.domain.print.service` — accessible directement depuis la nouvelle surcharge de
  `PrintQueueService.registerPrinter(Printer, PrinterStatus)`, aucune modification de
  `PrinterQueueHandle` nécessaire.
- `BasketReaperService`/`SchedulingConfig` (Story 4.9) : modèle exact à copier pour le nouveau
  scheduler — `@ConditionalOnProperty(matchIfMissing = true)`, `fixedDelayString` +
  `initialDelayString` sur la **même** propriété (évite un sweep immédiat au démarrage), log d'
  activation en `@PostConstruct`, désactivé dans le profil de test.

### Rayon d'impact de `CreatePrinterDto` — à lire avant de toucher un fichier de test

`CreatePrinterDto` est un **record** : ajouter un 5ᵉ champ casse la compilation de **tout** appel au
constructeur positionnel `new CreatePrinterDto(...)`, y compris dans des classes de test sans rapport
fonctionnel avec cette story (elles créent juste une imprimante comme fixture pour tester un rendu
PDF). Un appel JSON (Jackson, depuis le frontend ou `objectMapper.writeValueAsString(new
CreatePrinterDto(...))`) n'est pas concerné différemment — c'est le constructeur Java positionnel qui
casse.

**Règle** : le statut passé doit refléter ce que le double `PrinterBridgeDouble` a **réellement**
enregistré pour ce `printerBridgeId` juste avant l'appel — sous peine de test qui compile mais ment
sur l'état initial de l'imprimante (aucun impact fonctionnel réel puisque `discover()` n'est jamais
appelé nulle part dans ces fixtures, seul le 5ᵉ argument compte désormais). Inventaire exhaustif
(grep `new CreatePrinterDto(` + `printerBridgeDouble.register(` sur tout le backend, vérifié
ligne par ligne) :

**Ajouter `PrinterStatus.ONLINE`** (le double est enregistré `ONLINE` juste avant, ou le test ne
regarde jamais l'état de connectivité) :
- `BulkSettlementReportPrintingIT.java:280`, `DailyReportPrintingIT.java:237`,
  `DepositSlipPrintingIT.java:178`, `DepositSlipPrintingIT.java:310`,
  `EditionReportPrintingIT.java:228`, `EditionClosingIT.java:235` (package `domain.edition`, pas
  `domain.print` — ne pas l'oublier),  `InvoicePrintingIT.java:202`,
  `SettlementReportPrintingIT.java:244`, `PrinterSelectionIT.java:91`
- `PrintInfrastructureIT.java:86` et `:98` (Order 1/2, échouent en validation *avant* d'atteindre le
  statut — valeur inerte, `ONLINE` par convention) ; `:123` (Order4, pas d'assertion sur la
  connectivité) ; helper `createReachablePrinter`/`registerFakePrinter("ONLINE")` (ligne ~254)
- `PrinterRegistryIT.java` : étendre le helper privé `createPrinter(String name, String
  printerBridgeId)` (ligne ~388) avec un paramètre `PrinterStatus status` ; `ONLINE` pour tous ses
  appelants sauf celui listé ci-dessous
- `PrintQueueDiagnosticsIT.java` : étendre `createPrinterWithBridgeId(String name, String bridgeId)`
  (ligne ~374) avec un paramètre `PrinterStatus status` ; le helper `createPrinter(String name,
  String bridgeStatus)` (ligne ~368) le déduit via `PrinterStatus.valueOf(bridgeStatus)` et le
  propage — couvre `createReachablePrinter`/`createUnreachablePrinter` sans toucher leurs appelants

**Ajouter `PrinterStatus.OFFLINE`** (le test dépend explicitement d'un état déconnecté — **critique**,
inverser produirait un faux vert silencieux) :
- `PrinterSelectionIT.java:100` (`bridge-unavailable`, @Order2 — @Order3 asserte que ce printer est
  absent de `/api/printers/available`)
- `PrintInfrastructureIT.java:140` (@Order5, `registerFakePrinter("OFFLINE")` — asserte
  `handle.getLastError() != null`)
- `PrinterRegistryIT.java:122` (@Order5, `bridge-offline-1` — asserte `summary.connected() ==
  false`)
- `ThermalLabelPrintingIT.java:301` (`bridge-thermal-never-registered` — le commentaire du test dit
  explicitement "this printer starts 'in error'", intention à préserver)

### `POST /admin/printers/{id}/refresh-connectivity` — choix de propriétaire

Le rafraîchissement ciblé vit sur `PrinterController`/`PrinterService` (pas
`PrintQueueController`/`PrintQueueDiagnosticsService`) : l'AC4 le situe explicitement sur
`/admin/printers`, et c'est le contrôleur qui possède déjà l'action `POST .../test-print` de même
forme (id + action, retour synchrone). `PrintQueueController` perd son unique action de
rafraîchissement (AC5) sans en gagner une nouvelle.

### Pourquoi le scheduler ne duplique aucune logique

`PrintQueueService.refreshConnectivity()` (sans argument) fait déjà : boucle sur toutes les
imprimantes enregistrées, skip si le handle est absent ou suspendu (invariant torn-state de
`PrinterQueueHandle` respecté), appelle le checker par type, met à jour `lastError`. C'est
*exactement* le contrat de l'AC2/AC3. Le nouveau `PrinterConnectivityRefreshService` n'est qu'un
timer qui l'appelle — zéro nouvelle logique de traversée/skip à écrire ou à tester isolément (déjà
couvert avant cette story par `PrintQueueDiagnosticsIT`, dont une partie de la couverture migre vers
le nouvel endpoint ciblé, voir Tasks).

### Project Structure Notes

- Backend : tout le nouveau code dans `org.pluribourse.domain.print.{service,controller}` (existant)
  — aucune nouvelle arborescence. `PrinterConnectivityRefreshService` rejoint `PrintQueueService` dans
  `domain.print.service`, au même niveau que `BasketReaperService` dans `domain.pos.service`.
- Frontend : aucun nouveau dossier — extension de `features/admin/printers/`,
  `features/admin/print-queue/`, `services/printer-registry.service.ts`/`print-queue.service.ts`,
  `models/printer-registry.model.ts` existants.
- Nouvelle propriété `printer.connectivity.refresh.{interval,enabled}` — uniquement dans
  `application.properties` (main) + `src/test/resources/application.properties`, comme
  `pos.basket.reaper.*` (pas de valeur différenciée par profil dev/prod, aucune raison de le faire
  ici).
- `docs/workflow-ajout-imprimante.md` (présent dans l'arbre de travail, non commité) documente le
  flux **avant** cette story — hors périmètre des tâches ci-dessus (responsabilité de suivi listée
  dans `sprint-change-proposal-2026-09-11.md` §5, pas une tâche de cette story), mais à signaler à
  Manerial en fin de story pour qu'il le régénère.

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-11.md] — origine complète,
  approuvée par Manerial le 2026-09-11 ; §4 contient le texte exact déjà appliqué à `epics.md`
  (Stories 3.11/3.13 amendées + cette story), `prds/prd-PluriBourse-2026-06-08/prd.md` (FR-079), et
  `architecture.md` (table Infrastructure d'Impression + section Frontière PrinterBridge)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.15] — ACs canoniques de cette story
  (identiques à ci-dessus)
- [Source: _bmad-output/implementation-artifacts/3-11-integration-printerbridge-connexion-et-statut.md]
  — mécanisme de connectivité actuel (`PrinterBridgeClient`, checkers, `PrinterStatus`), lu
  intégralement ; c'est l'AC4 de cette story-ci que 3.15 amende
  ("démarrage du serveur **ou création**" → "démarrage du serveur" seul)
- [Source: _bmad-output/implementation-artifacts/3-7-vue-admin-de-diagnostic-des-imprimantes.md,
  ligne 151-154] — note de complétion amendée par cette même session : le bouton "Actualiser" de
  `/admin/print-queue` disparaît au profit du scheduler de cette story
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java,
  PrinterService.java, PrinterQueueHandle.java, PrinterBridgeClient.java] — lus intégralement
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/pos/service/BasketReaperService.java,
  pluribourse-backend/src/main/java/org/pluribourse/shared/config/SchedulingConfig.java] — modèle
  exact du scheduler à reproduire, lus intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketReaperIT.java] —
  modèle exact du test d'intégration du scheduler (contexte Spring dédié, base H2 dédiée, invocation
  directe de la méthode planifiée), lu intégralement
- [Source: pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java,
  PrinterRegistryIT.java, PrintInfrastructureIT.java] — lus intégralement pour établir l'inventaire
  du rayon d'impact de `CreatePrinterDto` ci-dessus
- [Source: pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts, .html,
  printer-list.component.ts, .html] — lus intégralement
- [Source: pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.ts, .html,
  services/print-queue.service.ts] — lus intégralement
- [Source: pluribourse-frontend/public/i18n/fr.json, lignes 645-778] — namespaces
  `admin.printQueue.*`/`admin.printers.*` existants, lus pour localiser les clés à ajouter/retirer

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw -q -o compile` puis `-o test-compile` → BUILD SUCCESS après l'ajout du 5ᵉ champ `status` sur `CreatePrinterDto` et la mise à jour de tout l'inventaire de call sites listé en Dev Notes.
- `./mvnw -q -o clean package` (suite backend complète) → BUILD SUCCESS, jar produit, Docker présent → `SaleConcurrencyIT` exécuté. `PrinterConnectivityRefreshIT` (nouveau, contexte Spring dédié H2 `printer-refresh-testdb`) démarré et vert.
- `npm test` (suite frontend complète) → 67 fichiers, 732/732 passed.
- `npm run build` → succès, sans warning.
- Vérification manuelle de parité i18n fr.json/en.json : 607/607 clés de part et d'autre (script de diff des clés aplaties), aucune clé orpheline.

### Completion Notes List

- `CreatePrinterDto` : 5ᵉ champ `status` (`@NotNull PrinterStatus`) ajouté en dernière position comme prescrit ; inventaire exhaustif des ~20 call sites positionnels dans 12 fichiers de test (Dev Notes § Rayon d'impact) appliqué intégralement — `ONLINE` partout où le test ne regarde pas la connectivité, `OFFLINE` sur les 4 call sites qui en dépendent explicitement (`PrinterSelectionIT:100`, `PrintInfrastructureIT` @Order5, `PrinterRegistryIT` @Order5, `ThermalLabelPrintingIT:301`). `PrinterMapper.toEntity()` inchangé (MapStruct ignore silencieusement le champ source non mappé, confirmé par la compilation).
- `PrintQueueService` : nouvelle surcharge `registerPrinter(Printer, PrinterStatus)` — seede `lastError` depuis le statut connu, ne touche jamais `PrinterConnectivityChecker`. `refreshConnectivity()` factorisé en une méthode privée `refreshOne(Printer)`, réutilisée par la nouvelle `refreshConnectivity(Long)` (rafraîchissement ciblé) sans dupliquer la logique de skip des imprimantes suspendues.
- `PrinterService.create()` bascule sur la surcharge seedée ; nouvelle méthode `refreshConnectivity(Long id)` (AC4), `toSummaryDto(Printer)` extrait du lambda de `list()` pour être réutilisé.
- Nouveau `PrinterConnectivityRefreshService` — copie fidèle du patron `BasketReaperService` (Story 4.9) : `@ConditionalOnProperty(matchIfMissing = true)`, `fixedDelayString`/`initialDelayString` sur la même propriété, log d'activation en `@PostConstruct`, désactivé en profil test. N'appelle que `PrintQueueService.refreshConnectivity()` existant, aucune logique de traversée nouvelle.
- Endpoint `POST /admin/printers/{id}/refresh-connectivity` sur `PrinterController` (même famille que `test-print`) ; `POST /admin/print-queue/refresh` retiré de `PrintQueueController`/`PrintQueueDiagnosticsService`.
- `PrintInfrastructureIT` @Order(5) réécrite : le double PrinterBridge est dépeuplé (`unregister`) juste avant la création, qui reçoit `status=OFFLINE` explicitement dans le payload — prouve que l'erreur vient du statut seedé et non d'un appel réseau resté en place. Même renforcement appliqué à `PrinterRegistryIT` @Order(5).
- Couverture perdue par la suppression de `PrintQueueDiagnosticsIT` @Order(12-14) migrée vers 5 nouveaux tests dans `PrinterRegistryIT` (propriétaire de `/admin/printers`) : online→offline, offline→online, imprimante suspendue non affectée par le rafraîchissement ciblé, 404 id inconnu, 403 session bénévole.
- Nouveau `PrinterConnectivityRefreshIT` — modèle exact `PosBasketReaperIT` (Story 4.9) : contexte Spring dédié (`printer.connectivity.refresh.enabled=true`, intervalle `PT1H`, base H2 `printer-refresh-testdb`), fixture via l'endpoint HTTP, invocation directe de `refreshAll()`.
- Frontend : `printer-form.component.onSubmit()` transmet `status: this.selectedPrinter()!.status` (déjà connu du `DiscoveredPrinter` sélectionné, aucun appel réseau supplémentaire) et ne ferme plus le dialog après un enregistrement réussi — retire l'imprimante de `discoveredPrinters()`, revient à la liste (`backToList()`), ferme seulement si la liste devient vide (AC6, Story 3.13 amendée).
- `printer-list.component` : nouveau bouton icône "Vérifier la connectivité" par ligne (`refreshingId` signal, patron `testPrint()`), toast succès/erreur dédié.
- `print-queue-list.component`/`print-queue.service` : bouton "Actualiser" et `refreshStatuses()` retirés ; `load()` perd son paramètre `live`, ne consomme plus que l'état en cache (`getStatuses()`).
- i18n : `admin.printers.actions/success/error.refreshConnectivity` ajoutées ; `admin.printQueue.refresh` et `admin.printQueue.error.refresh` retirées (fr/en). Parité 607/607 vérifiée.
- Aucune déviation par rapport au plan de la story — implémentée telle qu'écrite. `docs/workflow-ajout-imprimante.md` (déjà présent dans l'arbre de travail, non commité) documente le flux **avant** cette story : hors périmètre des tâches ci-dessus, à régénérer par Manerial (signalé en Dev Notes § Project Structure Notes).

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterConnectivityRefreshService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterConnectivityRefreshIT.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/CreatePrinterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueDiagnosticsService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrintQueueController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/config/SchedulingConfig.java`
- `pluribourse-backend/src/main/resources/application.properties`
- `pluribourse-backend/src/test/resources/application.properties`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintInfrastructureIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterSelectionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/ThermalLabelPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/BulkSettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DailyReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/DepositSlipPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/EditionReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/InvoicePrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/SettlementReportPrintingIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/edition/EditionClosingIT.java`

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/printer-registry.model.ts`
- `pluribourse-frontend/src/app/services/printer-registry.service.ts`
- `pluribourse-frontend/src/app/services/print-queue.service.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.html`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.html`
- `pluribourse-frontend/src/app/features/admin/print-queue/print-queue-list.component.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-09-11 : Seconde revue de code (bmad-code-review, session fraîche, 3 revues parallèles Blind Hunter + Edge Case Hunter + Acceptance Auditor) sur le même diff, sans mémoire de la revue précédente. Acceptance Auditor : 0 violation indépendamment confirmée sur les 6 AC. 1 decision-needed tranchée par Manerial : badge "Vérification en attente" sans levier d'action sur `/admin/print-queue` — **accepté tel quel** (scheduler sous 5 min + rafraîchissement ciblé déjà disponible sur `/admin/printers`). 2 patchs appliqués : (1) `PrintQueueService.registerPrinter(Printer, PrinterStatus)` — le garde `containsKey`+`putIfAbsent` n'était pas réellement atomique (thread consommateur orphelin possible en cas de collision d'id) ; remplacé par `putIfAbsent` puis arrêt du handle perdant si la map avait déjà une entrée. (2) `print-queue-list.component.ts::connectionState()` — testait `connected` avant `canRetry`, ce qui affichait à tort "Connecté" (au lieu de "En erreur") pendant l'état torn déjà documenté (race `refreshOne()`/`consume()`) ; `canRetry` testé en premier désormais, +1 test frontend (fixture torn-state). 6 findings différés (reconfirmation de la race déjà connue avec précision sur son impact réel une fois le patch (2) appliqué, lecture non-atomique mineure dans `PrinterService.toSummaryDto()`, absence de throttling sur le rafraîchissement ciblé, race DELETE concurrent → 200 au lieu de 404, contention du thread unique du scheduler partagé avec `BasketReaperService`/SSE, toast succès sans vérification réelle sur une file suspendue déjà connu) — copiés dans `deferred-work.md`. 14 findings écartés (déjà décidés/documentés, cohérents avec un patron préexistant du projet, ou hors périmètre). Vérifs post-patch : `./mvnw -o clean package` BUILD SUCCESS (Docker présent → `SaleConcurrencyIT`/`SettlementConcurrencyIT` exécutés) ; `npm test` 735/735 (67 fichiers, +1 test torn-state). Aucune migration Liquibase, aucun nouvel endpoint/route.
- 2026-09-11 : Revue de code (bmad-code-review, 3 revues parallèles Blind Hunter + Edge Case Hunter + Acceptance Auditor) → `done`. Acceptance Auditor : 0 violation sur les 6 AC (1 remarque factuelle mineure hors-diff : l'AC5 présuppose à tort qu'un bouton "Actualiser" global existait déjà sur `/admin/printers` avant cette story — n'a jamais existé, rien à corriger côté implémentation). 2 decision-needed tranchées par Manerial : (D1) race `refreshOne()` (scheduler + refresh ciblé, non synchronisé) vs `PrinterQueueHandle.consume()` (job en échec, `synchronized`) pouvant produire l'état torn `lastError==null`/`suspended==true` — pré-existante, désormais exercée automatiquement toutes les 5 min ; **différé** (une fois torn, `isSuspended()` reste `true` donc `refreshOne()` s'arrête tout de suite sur les passages suivants — impact réel = texte d'erreur vide sur une file par ailleurs correctement affichée "suspendue", pas une panne masquée ; probabilité faible sur une petite installation). (D2) imprimante `UNKNOWN` (Bluetooth/thermique, cas majoritaire FR-077) affichée "connectée" dès la création sans vérification serveur — écart réel par rapport au texte de la SCP 2026-09-11 approuvée (qui ne dit nulle part d'afficher `UNKNOWN` comme connecté) ; **patché** : nouveau `PrinterQueueHandle.pendingVerification` (seedé à la création pour `UNKNOWN`, effacé par le premier vrai check dans `refreshOne()`), propagé sur `PrinterSummaryDto`/`PrinterStatusDto` (champs additifs), 3ᵉ état badge "Vérification en attente" sur `/admin/printers` et `/admin/print-queue`, clés i18n `admin.printers.status.pendingVerification`/`admin.printQueue.status.pendingVerification` (fr/en). 2 patchs supplémentaires appliqués : double lecture BDD + double branche 404 dupliquée entre `PrinterService.refreshConnectivity(id)` et `PrintQueueService.refreshConnectivity(Long)` (ce dernier retourne désormais le `Printer` résolu, réutilisé par l'appelant) ; garde `containsKey` manquante sur `registerPrinter(Printer, PrinterStatus)` alignée sur sa sœur `registerPrinter(Printer)` (évite un thread consommateur orphelin en cas de collision d'id). 6 findings écartés (déjà décidés/documentés dans la story ou couverts par la philosophie de test du projet). Vérifs post-patch : `./mvnw -o clean package` BUILD SUCCESS, 590/590 tests verts (Docker présent → `SaleConcurrencyIT` + `SettlementConcurrencyIT` exécutés) ; `npm test` 734/734 (67 fichiers, +2 tests pour `pendingVerification`) ; `npm run build` sans warning ; parité i18n fr.json/en.json 609/609 (607 + 2 clés `pendingVerification`). Aucune migration Liquibase, aucun nouvel endpoint/route. Vérification visuelle à faire par Manerial (nouveau badge "Vérification en attente").
- 2026-09-11 : Implémentation complète de la Story 3.15 (correctif SCP 2026-09-11 sur les Stories 3.7/3.11/3.13). Retrait du check de connectivité bloquant à la création d'une imprimante, remplacé par (a) le statut déjà connu de `discover()` seedé sans nouvel appel réseau, (b) un scheduler périodique `PrinterConnectivityRefreshService` (modèle `BasketReaperService`), (c) un rafraîchissement manuel ciblé `POST /admin/printers/{id}/refresh-connectivity`. Boutons "Actualiser" globaux et `POST /admin/print-queue/refresh` retirés. Dialog "Ajouter une imprimante" reste ouvert après un enregistrement réussi (Story 3.13 amendée). Backend : 100 % vert (Docker présent → `SaleConcurrencyIT` exécuté), nouveau `PrinterConnectivityRefreshIT`. Frontend : 732/732 tests, build sans warning, parité i18n 607/607. Statut → `review`.
