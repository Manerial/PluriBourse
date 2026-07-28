---
baseline_commit: fa9f09103a85e08b7716ca0cc5c08c4646405ede
---

# Story 3.11: Intégration de PrinterBridge — connexion et statut

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a administrateur,
I want enregistrer une imprimante en la sélectionnant dans la liste détectée par PrinterBridge (service natif séparé) plutôt qu'en saisissant un port série ou une adresse IP,
so that le backend n'ait plus jamais à ouvrir lui-même une connexion matérielle — impossible depuis son conteneur Docker pour le Bluetooth (voir `sprint-change-proposal-2026-07-27.md`).

## Acceptance Criteria

1. `GET /admin/printers/discovered` (ADMIN uniquement, nouveau) : proxy vers `GET /printers` de PrinterBridge. Retourne une liste de `DiscoveredPrinterDto` (`printerBridgeId`, `name`, `type` — `THERMAL`/`A4`, mappé depuis `BLUETOOTH_THERMAL`/`NETWORK` — `status` — `ONLINE`/`OFFLINE`/`UNKNOWN`). Si PrinterBridge est injoignable (timeout/connexion refusée), retourne **503** avec un `ProblemDetail` RFC 7807 (`type` se terminant par `printerbridge-unavailable`) — jamais une liste vide, qui serait indistinguable de "PrinterBridge répond, aucune imprimante détectée".
2. `POST /admin/printers` (existant, Story 3.4) : `CreatePrinterDto` remplace `serialPort`/`host`/`port` par `printerBridgeId` (String, obligatoire, non vide). Validation : THERMAL nécessite `printerBridgeId` + `widthMm` (57 ou 80, inchangé) ; A4 nécessite `printerBridgeId` uniquement — plus de valeur par défaut de port à appliquer (il n'y a plus de port). Le nom d'affichage (`name`) reste saisi par l'admin, indépendant du nom détecté par PrinterBridge.
3. `Printer` (entité, table `printers`) : colonnes `serial_port`, `host`, `port` supprimées ; colonne `printer_bridge_id VARCHAR(32) NOT NULL` ajoutée. `width_mm` conservée (THERMAL uniquement, formatage d'étiquette — sans lien avec le transport).
4. Vérification de connectivité (démarrage du serveur + création d'imprimante, FR-079) : appelle `GET /printers/{printerBridgeId}/status` sur PrinterBridge au lieu d'ouvrir une socket TCP ou un port série. Une erreur de connexion **à PrinterBridge lui-même** (timeout, connexion refusée) produit un message distinct d'une imprimante **spécifiquement signalée hors ligne par PrinterBridge** — les deux remontent tels quels dans `lastError`/`PrinterQueueHandle` et s'affichent sans changement sur `/admin/print-queue` (Story 3.7, page non modifiée par cette story — voir Dev Notes § Pas de changement sur `/admin/print-queue`).
5. `POST /admin/printers/{id}/test-print` (ADMIN uniquement, nouveau) : résout le `Printer` PluriBourse par `id`, appelle `POST /printers/{printerBridgeId}/test-print` sur PrinterBridge, répercute le résultat (succès/message d'erreur — PrinterBridge génère lui-même le contenu de test, rien à produire côté PluriBourse). 404 (`PrinterNotFoundException`, réutilisée) si l'`id` PluriBourse est inconnu.
6. `GET /admin/printers/serial-ports` est supprimé (remplacé par AC1) : route, `SerialPortDto`, `PrinterService.listAvailableSerialPorts()` retirés.
7. Frontend `/admin/printers`, dialog "Ajouter une imprimante" (`printer-form.component`) : liste de `GET /admin/printers/discovered` au lieu des champs manuels port série / IP+port. L'admin sélectionne une imprimante détectée, saisit un nom d'affichage, et — si le type de l'imprimante sélectionnée est THERMAL — une largeur (inchangé). **Le type (`THERMAL`/`A4`) n'est plus un sélecteur manuel séparé** — il est dérivé automatiquement de l'imprimante détectée choisie (PrinterBridge le connaît déjà), pour éviter qu'un admin sélectionne une imprimante A4 tout en laissant le formulaire sur THERMAL. Si `GET /admin/printers/discovered` échoue en 503, un message d'avertissement (`app-notification-inline`, variant `warning` — **pas** de nouveau composant `Banner` : `EXPERIENCE.md` ligne 156 en décrit un mais aucun n'a jamais été implémenté en Angular, voir Dev Notes § Pas de composant Banner) remplace le formulaire : "Le service PrinterBridge ne répond pas sur ce poste. Vérifiez qu'il est lancé." — état bloquant, pas une notification transitoire, puisqu'aucune imprimante ne peut être enregistrée sans découverte.
8. Frontend `/admin/printers`, `printer-list.component` : bouton "Tester l'impression" (icône `print`) par ligne, à côté du bouton de suppression existant. Appelle `POST /admin/printers/{id}/test-print`, spinner pendant l'appel, toast succès/erreur selon le résultat renvoyé par PrinterBridge.
9. Suppression d'une imprimante (existante, Story 3.8, `DELETE /admin/printers/{id}`) : comportement inchangé, aucune modification requise — sert désormais aussi à nettoyer une entrée dont le `printerBridgeId` ne correspond plus à rien de détecté (ex. port COM réattribué après réappairage Bluetooth, cf. limite documentée dans `PrinterBridge/CLAUDE.md`).

## Tasks / Subtasks

- [x] Backend — client HTTP PrinterBridge (AC: 1, 4, 5)
  - [x] Nouveau `PrinterBridgeClient` (`org.pluribourse.domain.print.service`) : `List<PrinterBridgeDiscoveredPrinter> discover()` (`GET {baseUrl}/printers`), `PrinterBridgeDiscoveredPrinter checkStatus(String printerBridgeId)` (`GET {baseUrl}/printers/{id}/status`), `PrintResult testPrint(String printerBridgeId)` (`POST {baseUrl}/printers/{id}/test-print`). **`checkStatus()` retourne le même type `PrinterBridgeDiscoveredPrinter` que `discover()`** — vérifié sur le code réel de PrinterBridge (`ApiServer.java`) : les deux routes renvoient la même forme JSON (`Printer{id,name,type,status}`), pas de type distinct pour le statut. Construit son propre `RestClient` dans son constructeur (via `@Value("${printerbridge.base-url}")`) — pas de `@Bean RestClient` séparé dans une classe de config, ce client HTTP n'a qu'un seul consommateur. Utilise `RestClient` (Spring Framework 7, déjà sur le classpath via `spring-boot-starter-web` — **première intégration HTTP sortante du projet**, aucun `RestTemplate`/`WebClient` existant à réutiliser).
  - [x] `PrinterBridgeDiscoveredPrinter` (record, désérialisation JSON du `Printer` de PrinterBridge : `id`, `name`, `type` — `BLUETOOTH_THERMAL`/`NETWORK` —, `status` — `ONLINE`/`OFFLINE`/`UNKNOWN`).
  - [x] `PrintResult` (record, désérialisation du `PrintResult` de PrinterBridge : `status`, `message`).
  - [x] Configuration : propriété `printerbridge.base-url`, valeur par défaut `http://host.docker.internal:7420` (port fixe de PrinterBridge, `Main.PORT`) dans `application-prod.properties` ; `http://localhost:7420` dans `application-dev.properties` (le backend en dev tourne hors conteneur, `host.docker.internal` ne résout pas).
  - [x] `RestClient` configuré avec un timeout de connexion court (2s) et un timeout de lecture de 5s pour `discover()`/`checkStatus()` — ne doit jamais bloquer longtemps le démarrage du serveur (`reloadFromDatabase()` appelle `checkAccessibility()` pour chaque imprimante enregistrée en séquence). `testPrint()` peut utiliser un timeout de lecture plus long (15s, appel utilisateur à la demande, pas au démarrage).
  - [x] Nouvelle exception `PrinterBridgeUnavailableException extends RuntimeException` (non liée à `BusinessException` — voir Dev Notes § Deux familles d'erreurs distinctes) : levée par le client **spécifiquement** sur les erreurs de connexion — timeout, connexion refusée (`ResourceAccessException`/équivalent `IOException` sous-jacent à `RestClient`), pas sur un statut HTTP 4xx. **Un 404 de PrinterBridge doit être capturé séparément** (`RestClient` lève par défaut sur tout 4xx/5xx — intercepter précisément `HttpClientErrorException.NotFound`, pas un `catch` générique qui confondrait les deux cas) et traduit en statut `OFFLINE` plutôt qu'en exception : l'imprimante existe côté PluriBourse mais n'est plus détectée par PrinterBridge. Sans cette distinction précise dans le code du client, l'AC4 (messages d'erreur distincts) est impossible à respecter correctement.
- [x] Backend — entité, migration, DTOs (AC: 2, 3, 6)
  - [x] Nouvelle migration `018-printer-bridge-id.xml` : `dropColumn` sur `serial_port`, `host`, `port` ; `addColumn printer_bridge_id VARCHAR(32) NOT NULL` (pas de valeur par défaut sensée pour une colonne NOT NULL sur une table potentiellement déjà peuplée — voir Dev Notes § Migration et données existantes).
  - [x] `Printer.java` (UPDATE) : retirer `serialPort`/`host`/`port`, ajouter `String printerBridgeId` (`@Column(name = "printer_bridge_id", nullable = false, length = 32)`).
  - [x] `CreatePrinterDto.java` (UPDATE) : retirer `serialPort`/`host`/`port`, ajouter `@NotBlank @Size(max = 32) String printerBridgeId`.
  - [x] `PrinterDto.java` (UPDATE) : même remplacement de champs.
  - [x] Supprimer `SerialPortDto.java` (AC6).
  - [x] `PrinterMapper.java` (UPDATE) : aucun changement de structure nécessaire — MapStruct mappe `printerBridgeId` automatiquement par nom de champ identique, comme il le faisait pour `serialPort`/`host`/`port`.
  - [x] Nouveau `DiscoveredPrinterDto.java` (record, `org.pluribourse.domain.print.dto`) : `printerBridgeId`, `name`, `type` (`PrinterType` PluriBourse — mappé depuis `PrinterBridgeDiscoveredPrinter.type()`), `status` (nouvel enum local `DiscoveredPrinterStatus { ONLINE, OFFLINE, UNKNOWN }`, distinct de `PrinterStatus` si ce nom existe déjà ailleurs — vérifier avant de nommer).
- [x] Backend — remplacement des checkers de connectivité (AC: 4)
  - [x] `NetworkPrinterConnectivityChecker.java` (UPDATE, garder le nom de classe et `getSupportedType() = PrinterType.A4` — ne pas renommer, `PrintQueueService` construit sa map via `PrinterConnectivityChecker::getSupportedType`, aucune modification de `PrintQueueService` ne doit être nécessaire) : `checkAccessibility(Printer)` appelle `printerBridgeClient.checkStatus(printer.getPrinterBridgeId())` au lieu d'ouvrir une `Socket`. Lève `IllegalStateException` (comme aujourd'hui) si le statut retourné est `OFFLINE`, ou si `PrinterBridgeUnavailableException` est propagée (message distinct — voir AC4) ; `UNKNOWN` est traité comme accessible (ne bloque pas le démarrage, cohérent avec l'esprit actuel qui préfère ne pas cacher une imprimante par excès de prudence — voir `PrinterBridge`, `isLikelyRealDevice`, même philosophie "fail open").
  - [x] Renommer `SerialPrinterConnectivityChecker.java` → **`ThermalPrinterConnectivityChecker.java`** (garder `getSupportedType() = PrinterType.THERMAL`) : le nom actuel ("Serial") devient trompeur — la classe ne touche plus aucun port série, elle fait un appel HTTP comme sa voisine. Renommer ne touche **pas** `PrintQueueService` : sa map est construite via `PrinterConnectivityChecker::getSupportedType()` à l'exécution (injection Spring par liste de beans), le nom de classe n'intervient pas. Même remplacement que `NetworkPrinterConnectivityChecker`, appelle le même `PrinterBridgeClient.checkStatus()` — **le code des deux classes devient quasi identique** (seul `getSupportedType()` diffère) ; ne pas fusionner en une seule classe malgré la duplication, `PrintQueueService.connectivityCheckersByType` exige un bean distinct par `PrinterType` (`Collectors.toMap` échouerait sur une clé dupliquée) — dupliquer les ~10 lignes est le choix le plus simple, pas une dette technique à corriger ici.
  - [x] Aucune modification de `PrintQueueService.java`/`PrinterQueueHandle.java`/`PrinterConnectivityChecker.java` (interface inchangée) — le remplacement est entièrement interne aux deux classes ci-dessus.
- [x] Backend — service et contrôleur (AC: 1, 2, 5, 6)
  - [x] `PrinterService.java` (UPDATE) : `validateConfiguration()` — remplacer les vérifications `serialPort`/`host` par `printerBridgeId` non vide (commun aux deux types) ; retirer le bloc `if (type == A4 && port == null) port = 9100` (plus de port à défaulter). Nouvelle méthode `List<DiscoveredPrinterDto> discover()` → `printerBridgeClient.discover()`, mappe chaque `PrinterBridgeDiscoveredPrinter` en `DiscoveredPrinterDto` — **le mapping `BLUETOOTH_THERMAL→THERMAL`/`NETWORK→A4` vit ici**, dans le service (méthode privée), pas dans les DTOs qui restent de simples structures de données, cohérent avec `PrinterSummaryDto`/`SerialPortDto` déjà construits manuellement dans ce même service (Story 3.8) ; laisse `PrinterBridgeUnavailableException` se propager (catchée au niveau du `@ControllerAdvice`, voir ci-dessous). Nouvelle méthode `PrintResult testPrint(Long id)` → résout le `Printer` (`PrinterNotFoundException` sinon), appelle `printerBridgeClient.testPrint(printer.getPrinterBridgeId())`. Retirer `listAvailableSerialPorts()`.
  - [x] `PrinterController.java` (UPDATE) : remplacer `GET /serial-ports` par `GET /discovered` → `List<DiscoveredPrinterDto>`. Ajouter `POST /{id}/test-print` → `PrintResult`.
  - [x] `org.pluribourse.shared.exception.GlobalExceptionHandler` (UPDATE) : nouveau handler pour `PrinterBridgeUnavailableException` → 503, `ProblemDetail` (`type` cohérent avec les autres exceptions métier, ex. suffixe `printerbridge-unavailable`, pattern RFC 7807 déjà en place pour toutes les autres exceptions — ARCH-013).
- [x] Frontend — modèle & service (AC: 1, 5, 7, 8)
  - [x] `printer-registry.model.ts` (UPDATE) : remplacer `SerialPortOption`/`CreatePrinterPayload.serialPort|host|port` par `DiscoveredPrinter { printerBridgeId: string; name: string; type: 'THERMAL' | 'A4'; status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN'; }` et `CreatePrinterPayload { name: string; type: 'THERMAL' | 'A4'; printerBridgeId: string; widthMm: number | null; }`. Ajouter `PrintResult { status: 'OK' | 'ERROR'; message: string | null; }`.
  - [x] `printer-registry.service.ts` (UPDATE) : remplacer `listSerialPorts()` par `discover(): Observable<DiscoveredPrinter[]>` (`GET /api/admin/printers/discovered`). Ajouter `testPrint(id: number): Observable<PrintResult>` (`POST /api/admin/printers/${id}/test-print`).
- [x] Frontend — formulaire de création (AC: 2, 7)
  - [x] `printer-form.component.ts` (UPDATE) : remplacer les champs `serialPort`/`host`/`port` par `printerBridgeId` (select, rempli depuis `discover()`) — **retirer aussi le `FormControl type`** du groupe : le type n'est plus saisi par l'admin, il est dérivé de l'imprimante détectée sélectionnée (stocké dans un signal `selectedType = signal<'THERMAL' | 'A4' | null>(null)`, mis à jour par le `(selectionChange)`/`valueChanges` du select `printerBridgeId`, à partir de `discoveredPrinters().find(p => p.printerBridgeId === value)?.type`). `widthMm` reste conditionné à `selectedType() === 'THERMAL'` (inchangé dans son principe, juste sa condition source). `onSubmit()` envoie `type: selectedType()` dans le payload (le backend continue d'exiger `type` dans `CreatePrinterDto`, AC2 — seule son origine change côté frontend). Nouveau signal `discoveryError = signal<boolean>(false)` — `true` sur 503 de `discover()`.
  - [x] `printer-form.component.html` (UPDATE) : `@if (discoveryError())` → `<app-notification-inline variant="warning">` (composant déjà importé et utilisé dans ce même fichier pour l'ancien message "aucun port détecté" — réutilisation directe, pas un nouveau composant) au lieu du formulaire. Sinon, un seul `mat-select` peuplé par `discover()` (libellé = `name` + statut, ex. "Imprimante Bureau (hors ligne)"), au lieu du sélecteur de type + des deux jeux de champs conditionnels `serialPort`/`host+port`. Le champ largeur n'apparaît qu'après sélection d'une imprimante détectée de type THERMAL (`@if (selectedType() === 'THERMAL')`).
- [x] Frontend — bouton test d'impression (AC: 8)
  - [x] `printer-list.component.ts` (UPDATE) : nouvelle méthode `testPrint(printer: PrinterSummary)` — spinner (signal `testingId = signal<number | null>(null)`), appelle `printerRegistryService.testPrint(printer.id)`, toast succès/erreur selon `PrintResult.status`.
  - [x] `printer-list.component.html` (UPDATE) : bouton icône `print` par ligne, `[disabled]="testingId() === printer.id"`.
- [x] i18n (AC: 7, 8)
  - [x] `fr.json`/`en.json` (UPDATE, namespace `admin.printers.*` existant) : retirer `create.serialPort`/`create.host`/`create.port`/`create.noSerialPort` ; ajouter `create.printerBridgeId` (libellé du select), `create.discoveryUnavailable` (texte du bandeau), `testPrint` (libellé bouton), `success.testPrint`/`error.testPrint`.
- [x] Tests backend (AC: 1-6)
  - [x] `PrinterBridgeClient` : double de test HTTP léger plutôt que Mockito où c'est raisonnable (`com.sun.net.httpserver.HttpServer`, aucune dépendance supplémentaire, dans l'esprit du `ServerSocket` déjà utilisé pour A4 en Story 3.4 — cf. Dev Notes § Stratégie de test). Mockito reste l'option de repli légitime pour ce composant : c'est un appel à un **système externe** (`PrinterBridge`), l'exception explicite de CLAUDE.md ("pas de Mockito sauf pour les composants externes") s'applique ici pour la première fois dans le projet.
  - [x] Étendre `PrinterRegistryIT` (`org.pluribourse.domain.print`) : `GET /admin/printers/discovered` (200 + liste, 503 si le double HTTP est indisponible/arrêté) ; `POST /admin/printers` avec `printerBridgeId` (plus de `serialPort`/`host`/`port` dans le payload de test) ; `POST /admin/printers/{id}/test-print` (200 avec résultat du double, 404 id inconnu) ; connectivité au démarrage — imprimante dont le double HTTP répond `OFFLINE` → `connected=false` dans `GET /admin/printers` ; double HTTP arrêté → `lastError` contient un message distinguable ("PrinterBridge" dans le texte) d'un simple `OFFLINE`.
  - [x] Retirer les tests de `GET /admin/printers/serial-ports` (route supprimée).
- [x] Tests frontend
  - [x] `printer-form.component.spec.ts` (UPDATE) : chargement de `discover()` au `ngOnInit`, sélection d'une imprimante détectée, bandeau affiché sur 503, soumission avec `printerBridgeId`.
  - [x] `printer-list.component.spec.ts` (UPDATE) : bouton test d'impression — succès/erreur, état désactivé pendant l'appel.

### Review Findings

_Revue conjointe des Stories 3.11 et 3.12 (bmad-code-review), le diff des deux stories ayant été développé et testé ensemble. Les mêmes findings sont dupliqués dans la Story 3.12._

- [x] [Review][Decision] `testPrint()` ne gère pas le 404 PrinterBridge (printerBridgeId périmé), contrairement à `checkStatus()` — [`PrinterBridgeClient.java:103-112`]. **Résolu → Patch appliqué** : décision utilisateur = traduire en `PrintResult` ERROR (symétrique à `checkStatus()`). Confirmé empiriquement contre une instance PrinterBridge réelle (curl) que `test-print` renvoie bien un vrai 404 sur id inconnu. Corrigé : `PrinterBridgeClient.testPrint()` catch `HttpClientErrorException.NotFound` → `PrintResult(ERROR, ...)` ; double de test (`PrinterBridgeDouble`) corrigé pour simuler le vrai 404 au lieu de 200+ERROR ; nouveau test E2E `PrinterRegistryIT.test_print_returns_an_error_result_when_printerbridge_no_longer_knows_the_printer` (Order 7) + test unitaire `PrinterBridgeClientTest`.
- [x] [Review][Decision] Rejet du handshake WebSocket (id inconnu de PrinterBridge) classé à tort comme "PrinterBridge injoignable" — [`PrinterBridgeClient.java:145-160`]. **Résolu → Aucun bug, dismissed.** Investigation menée dans le code source réel de PrinterBridge (`ApiServer.java`/`PrintJobService.java`, repo `../PrinterBridge`) : contrairement à ce que laissait penser `PrinterBridge/CLAUDE.md` (document non figé, imprécis sur ce point), la route `WS /printers/{id}/print` **n'a aucune validation d'id au moment du handshake** — la connexion est toujours acceptée ; un id inconnu n'est détecté qu'après réception du payload (`PrintJobService.print()` → `UnknownPrinterException`), catché par `ApiServer.onPayload` comme n'importe quel autre échec métier et renvoyé comme un `PrintResult{status:"ERROR"}` **normal** sur la session déjà ouverte. Le code PluriBourse existant traite déjà correctement ce cas via `"ERROR".equals(result.status()) → IllegalStateException`. Aucune correction nécessaire.
- [x] [Review][Decision] Migration `018-printer-bridge-id.xml` : `NOT NULL` sans défaut ni backfill — [`pluribourse-backend/src/main/resources/db/changelog/018-printer-bridge-id.xml`]. **Résolu.** Utilisateur confirme : table `printers` vide partout (aucune imprimante enregistrée nulle part actuellement). Aucune action requise.
- [x] [Review][Decision] `PrinterBridgeClientTest` est un test de service isolé, hors philosophie E2E stricte — [`pluribourse-backend/src/test/java/org/pluribourse/domain/print/service/PrinterBridgeClientTest.java`]. **Résolu → Patch appliqué.** Décision utilisateur = accepter comme exception permanente. `CLAUDE.md` § Tests Backend mis à jour avec une exception explicite pour les clients de systèmes externes (même famille que l'exception Mockito déjà prévue), en clarifiant qu'elle s'ajoute à la couverture E2E sans la remplacer.
- [x] [Review][Patch] Fuite de session WebSocket + thread bloqué indéfiniment si PrinterBridge accepte la connexion mais ne répond jamais — [`PrinterBridgeClient.java:145-176`]. **Corrigé** : `session` fermée dans un bloc `finally` (`closeQuietly`, couvre tous les chemins) ; `executor.shutdownNow()` dans un `finally` de `print()` interrompt le thread de travail encore bloqué dans un appel WS bloquant (connexion ou `resultFuture.get()`, tous deux interruptibles) au lieu de laisser le `try-with-resources` d'origine attendre indéfiniment sa terminaison naturelle.
- [x] [Review][Patch] `printerBridgeId` non encodé dans l'URL WebSocket — [`PrinterBridgeClient.java:147`]. **Corrigé** : `UriComponentsBuilder.fromUriString(wsBaseUrl).path("/printers/{id}/print").buildAndExpand(printerBridgeId).encode().toUriString()`, même style de templating que les appels HTTP du même client.
- [x] [Review][Patch] `PrintResult.status` reste une `String` brute — [`pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintResult.java`]. **Corrigé** : nouvel enum `PrintResultStatus { OK, ERROR }`, `PrintResult.status` retypé, tous les sites d'appel/tests mis à jour. Sérialisation Jackson inchangée (nom d'enum = chaîne JSON), aucun impact sur le contrat frontend.
- [x] [Review][Patch] `new StandardWebSocketClient()` recréé à chaque appel de `print()` — [`PrinterBridgeClient.java:150`]. **Corrigé** : extrait en champ `private final StandardWebSocketClient webSocketClient` partagé.
- [x] [Review][Defer] Pas de garde si `printerBridgeClient.discover()` renvoie `null` (`.stream()` → NPE) ou si le champ `type` d'une imprimante découverte est `null` (mappé silencieusement vers `A4`) [`PrinterService.java:74-83`] — deferred, risque faible : le contrat PrinterBridge garantit un tableau et des champs renseignés ; à corriger si PrinterBridge se montre non fiable en pratique.
- [x] [Review][Defer] Aucun test ne fait passer un vrai aller-retour WebSocket réussi/ERROR par le code réel de `PrinterBridgeClient.print()`/`sendAndAwaitResult()`/`ResultCapturingHandler` (message de contrôle JSON, frame binaire, résultat "OK", "ERROR"→`IllegalStateException`) [`pluribourse-backend/src/test/java/org/pluribourse/domain/print/service/PrinterBridgeClientTest.java`] — deferred, gap déjà documenté consciemment dans les Completion Notes des deux stories (double WS jugé disproportionné) ; à revisiter si les bugs P1/D2 ci-dessus (justement dans ce chemin non testé) motivent d'investir dans un double WS.

## Dev Notes

### Point de départ — ce qui existe déjà (ne pas réinventer)

Le module `org.pluribourse.domain.print` (Stories 3.4/3.5/3.6/3.7/3.8/3.9, toutes `done`) contient déjà tout ce qui **ne change pas** dans cette story :
- `PrintQueueService`/`PrinterQueueHandle` : orchestration des files, un thread consommateur par imprimante, `lastError`/`suspended`/`errorSnapshot()`. **Aucune modification.** Le contrat `PrinterConnectivityChecker.checkAccessibility(Printer)` (lève une exception non catchée = imprimante inaccessible) est strictement préservé — seule son implémentation change.
- `/admin/print-queue` (Story 3.7, `PrintQueueDiagnosticsService`/`PrintQueueDiagnosticsController`) : affiche `lastError` tel quel. **Aucune modification** — un message d'erreur plus précis ("PrinterBridge injoignable" vs "imprimante hors ligne") suffit à distinguer les deux cas dans l'UI existante, sans toucher au code de cette page (voir § ci-dessous).
- `/setup` (Story 3.9, sélection d'imprimante bénévole) : dépend de `PrinterSelectionService.isAvailable()` → `PrintQueueService.isAvailable()`, lui-même basé sur `handle.getLastError() == null`. **Aucune modification.**
- `PrinterController`/`PrinterService`/`Printer`/`PrinterMapper`/`PrinterRepository`, `printer-list.component`, `printer-registry.service.ts` : **étendus**, pas recréés (voir Tasks).

### Pas de changement sur `/admin/print-queue`

Point important découvert en écrivant cette story (la proposition de changement de sprint envisageait à tort une bannière dédiée sur cette page aussi) : `PrintQueueDiagnosticsService`/la page `/admin/print-queue` affichent déjà `lastError` sans filtrage ni interprétation. Puisque `NetworkPrinterConnectivityChecker`/`SerialPrinterConnectivityChecker` sont les **seuls** points qui alimentent `lastError` (via `PrintQueueService.createHandle()`), il suffit que leurs messages d'exception soient rédigés distinctement ("Service PrinterBridge injoignable : ..." vs "Imprimante signalée hors ligne par PrinterBridge : ...") pour que la distinction apparaisse correctement sur `/admin/print-queue` **sans toucher à cette page**. Le nouveau bandeau bloquant (AC7) ne concerne que `/admin/printers`, dont le formulaire de création dépend directement et exclusivement de la découverte PrinterBridge.

### Deux familles d'erreurs distinctes

- `PrinterBridgeUnavailableException` (nouvelle, non liée à `BusinessException`) : PrinterBridge lui-même ne répond pas (timeout, connexion refusée sur `host.docker.internal:7420`). Remonte en 503 côté `GET /admin/printers/discovered` ; côté `checkAccessibility()`, produit un message d'erreur distinct dans `lastError`.
- Statut `OFFLINE`/`UNKNOWN` renvoyé par PrinterBridge (PrinterBridge répond, l'imprimante spécifique ne répond pas ou n'est plus détectée) : pas une exception au niveau HTTP, un statut normal à interpréter — `OFFLINE` → `IllegalStateException` (comme aujourd'hui), `UNKNOWN` → traité comme accessible (philosophie "fail open", cohérente avec `PrinterBridge`).

### Pas de composant `Banner` — réutiliser `NotificationInlineComponent`

`EXPERIENCE.md` (ligne 156) décrit un composant `banner` (bandeau persistant pleine largeur, variantes `warning`/`info`) déjà utilisé en texte dans plusieurs endroits du document — mais aucune implémentation Angular de ce composant n'existe dans `shared/components/` (vérifié : seul `NotificationInlineComponent` existe, avec un variant `warning` déjà utilisé dans `printer-form.component.html` pour l'ancien message "aucun port série détecté"). Créer un nouveau composant `Banner` pour ce seul besoin serait hors périmètre de cette story — `NotificationInlineComponent` variant `warning` couvre le besoin fonctionnel (persiste jusqu'à résolution, pas un toast transitoire) même s'il ne remplit pas toute la largeur de page comme documenté pour `banner`.

### Migration et données existantes

`018-printer-bridge-id.xml` ajoute `printer_bridge_id NOT NULL` sans valeur par défaut exploitable — toute ligne `printers` existante en base (créée avant cette story, via l'ancien mécanisme `serialPort`/`host`/`port`) n'a pas de `printerBridgeId` valide connu. Ce n'est pas un problème de compatibilité ascendante à résoudre : le projet n'a pas encore de déploiement en production avec des imprimantes réelles enregistrées via l'ancien mécanisme (MVP non livré) — la migration peut supposer une table `printers` vide ou acceptable à vider manuellement en dev. Ne pas ajouter de logique de migration de données complexe non demandée par l'AC.

### `RestClient` — première intégration HTTP sortante du projet

Aucun appel HTTP sortant n'existe ailleurs dans le backend (`architecture.md` ne mentionne ni `RestTemplate` ni `WebClient` ni `RestClient`). `RestClient` (Spring Framework 6.1+, disponible avec Spring Boot 4.0.6/Spring Framework 7 déjà sur le classpath du projet) est le choix le plus simple pour un appel HTTP synchrone bloquant — cohérent avec le modèle synchrone existant de `PrinterConnectivityChecker`/`PrintQueueHandle.consume()` (thread dédié, pas de programmation réactive ailleurs dans le projet).

### Stratégie de test — double HTTP léger, pas de vraie instance PrinterBridge en CI

Aucune instance réelle de PrinterBridge n'est disponible en CI (c'est un exécutable natif packagé séparément, pas une dépendance Maven). Un serveur `com.sun.net.httpserver.HttpServer` (JDK standard, zéro dépendance ajoutée) démarré dans `@BeforeAll` du test d'intégration, répondant aux trois routes (`/printers`, `/printers/{id}/status`, `/printers/{id}/test-print`) avec des réponses JSON fixes, permet de tester `PrinterBridgeClient` et le comportement de `PrintQueueService` sans vraie imprimante — même esprit que le `ServerSocket` local déjà utilisé pour tester l'accessibilité A4 en Story 3.4/3.8. Mockito reste une option acceptable pour isoler `PrinterBridgeClient` seul (composant externe, exception explicite de CLAUDE.md), mais le double HTTP couvre mieux le comportement de bout en bout (désérialisation JSON incluse) pour un coût quasi identique.

### Ce que cette story NE fait PAS (périmètre Story 3.12)

L'envoi effectif des jobs d'impression (`ThermalPrintService`/`DocumentPrintService` écrivant aujourd'hui directement sur `Socket`/`SerialPort`) n'est **pas** modifié par cette story — seuls la découverte et le statut de connectivité le sont. `ThermalPrintService`/`DocumentPrintService` continuent d'utiliser `printer.getSerialPort()`/`printer.getHost()`/`printer.getPort()` **jusqu'à la Story 3.12** — mais ces champs disparaissent de l'entité dans cette story-ci (AC3). **Point de vigilance critique pour l'ordre d'implémentation** : Story 3.11 doit être suivie immédiatement de la Story 3.12 avant toute mise en production intermédiaire — entre les deux, `ThermalPrintService`/`DocumentPrintService` ne compilent plus (référencent des champs supprimés de `Printer`). Si un développeur doit livrer 3.11 seule temporairement, il devra a minima adapter ces deux classes pour lire `printerBridgeId` sans changer leur transport (dette technique temporaire, à documenter dans la Story 3.12 si cette séquence se produit).

### Project Structure Notes

- Backend : toutes les nouvelles classes dans `org.pluribourse.domain.print.{service,dto,exception}` — package déjà existant (renommé depuis `org.pluribourse.print` par le commit `db9b6ec`, voir Story 3.8 Git Intelligence), aucune nouvelle arborescence.
- Frontend : aucun nouveau dossier — extension de `features/admin/printers/` et `services/printer-registry.service.ts`/`models/printer-registry.model.ts` existants.
- Nouvelle propriété de configuration `printerbridge.base-url` dans `application-prod.properties`/`application-dev.properties` (nouveau, aucune propriété de ce type n'existe encore dans le projet).

### Fichiers à lire avant modification (UPDATE, pas NEW)

- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/entity/Printer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/CreatePrinterDto.java`, `PrinterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/mapper/PrinterMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java` — garder `create()`/`delete()` (Story 3.4/3.8) intacts dans leur structure, remplacer uniquement la validation liée aux champs de connexion
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/NetworkPrinterConnectivityChecker.java`, `SerialPrinterConnectivityChecker.java` (à renommer `ThermalPrinterConnectivityChecker.java`)
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java`
- `pluribourse-backend/src/main/resources/db/changelog/016-printers.xml` (référence, ne pas modifier — migration immuable déjà appliquée) et `db.changelog-master.xml` (ajouter l'include de `018-printer-bridge-id.xml`)
- `pluribourse-frontend/src/app/models/printer-registry.model.ts`, `services/printer-registry.service.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts`/`.html`, `printer-list.component.ts`/`.html`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-07-27.md] — origine et justification complète de cette story
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.4, 3.7, 3.8, 3.9] — mécanisme d'origine remplacé, annoté comme obsolète
- [Source: PrinterBridge/CLAUDE.md, repo séparé github.com/Manerial/PrinterBridge] — API exposée (`GET /printers`, `GET /printers/{id}/status`, `POST /printers/{id}/test-print`), port fixe 7420 (`Main.PORT`), format `Printer{id,name,type,status}`/`PrintResult{status,message}`, limite connue sur l'instabilité de `printerBridgeId` en cas de réattribution de port COM
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintQueueService.java, PrinterQueueHandle.java, PrinterConnectivityChecker.java] — contrat inchangé, lu intégralement pour cette story
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/NetworkPrinterConnectivityChecker.java, SerialPrinterConnectivityChecker.java] — implémentation actuelle à remplacer (et à renommer pour ce dernier), lue intégralement
- [Source: pluribourse-backend/src/main/java/org/pluribourse/domain/print/entity/Printer.java, dto/CreatePrinterDto.java, PrinterDto.java, dto/PrinterSummaryDto.java, dto/SerialPortDto.java, mapper/PrinterMapper.java, controller/PrinterController.java] — lus intégralement
- [Source: pluribourse-backend/src/main/resources/db/changelog/016-printers.xml] — schéma actuel de `printers`
- [Source: pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts, .html, printer-list.component.ts] — lus intégralement, modèle direct pour les modifications
- [Source: pluribourse-frontend/src/app/models/printer-registry.model.ts, services/printer-registry.service.ts] — lus intégralement
- [Source: _bmad-output/implementation-artifacts/3-8-registre-des-imprimantes-admin.md] — conventions de package (`org.pluribourse.domain.print`, commit `db9b6ec`), pattern DTO séparé par route, pattern dialog auto-soumis, badge `.badge`/`.badge--active`
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md, ligne 153] — composant "Gestion des imprimantes" à adapter ; ligne 156, composant `banner` variante `warning` à réutiliser pour AC7

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `./mvnw.cmd -q compile` → BUILD SUCCESS après implémentation backend (avant Story 3.12 : échec attendu et documenté, `ThermalPrintService`/`DocumentPrintService` référençaient encore `getSerialPort()`/`getHost()`/`getPort()`, supprimés par l'AC3 de cette story — résolu en enchaînant sur la Story 3.12 dans la même session, cf. Dev Notes § Ce que cette story NE fait PAS)
- `./mvnw.cmd test -Dtest="org.pluribourse.domain.print.**"` → 81/81 passed (après Story 3.12) — 3 échecs intermédiaires corrigés en cours de route (voir Completion Notes)
- `./mvnw.cmd test` (suite complète backend) → 298/298 passed, BUILD SUCCESS, aucune régression hors module print
- `npm test` (suite complète frontend) → 49 fichiers de test, 430/430 passed, aucune régression

### Completion Notes List

- `PrinterBridgeClient` (HTTP) implémenté exactement selon les Dev Notes : deux `RestClient` internes (timeouts courts pour `discover()`/`checkStatus()`, timeout long pour `testPrint()`), `checkStatus()` retourne bien `PrinterBridgeDiscoveredPrinter` (pas de type fantôme), 404 de PrinterBridge capturé via `HttpClientErrorException.NotFound` et traduit en `OFFLINE`, erreurs de connexion via `ResourceAccessException` → `PrinterBridgeUnavailableException`.
- **Déviation par rapport au plan initial de la story** : `PrinterBridgeUnavailableException` étend en réalité `BusinessException` (pas `RuntimeException` nu comme prévu) — découverte en lisant `GlobalExceptionHandler` réel : un handler générique `@ExceptionHandler(BusinessException.class)` existe déjà et produit un `ProblemDetail` RFC 7807 à partir de `status`/`errorCode`/`message` portés par l'exception. Réutiliser cette classe de base évite d'ajouter un nouveau `@ExceptionHandler` dédié dans `GlobalExceptionHandler` (tâche prévue dans la story, finalement inutile) — validé par les tests `PrinterRegistryIT`/`PrinterBridgeClientTest`.
- **Déviation** : la validation "printerBridgeId non vide" prévue dans `PrinterService.validateConfiguration()` s'est révélée du code mort — `@NotBlank` sur le champ du DTO est intercepté par Bean Validation (`@Valid` sur le contrôleur) avant même d'atteindre le service, produisant un 400 (`validation-failed`), jamais le 422 initialement prévu. Supprimé le contrôle redondant dans le service (avec commentaire expliquant pourquoi) ; la story ne prévoyait pas explicitement le code HTTP attendu pour ce cas, ajusté à 400 pour rester cohérent avec le traitement de `name` (déjà `@NotBlank`, déjà 400).
- Renommage `SerialPrinterConnectivityChecker` → `ThermalPrinterConnectivityChecker` effectué sans aucun impact sur `PrintQueueService` (confirmé par les tests existants, tous passants sans modification de cette classe).
- `ThermalPrinterConnectivityChecker`/`NetworkPrinterConnectivityChecker` : statut `OFFLINE` → `IllegalStateException`, `UNKNOWN` → traité comme accessible (fail-open), `PrinterBridgeUnavailableException` propagée telle quelle.
- `PrinterService.discover()`/`testPrint(Long)` implémentés comme prévu ; mapping `BLUETOOTH_THERMAL/NETWORK` → `THERMAL/A4` dans une méthode privée du service.
- Frontend : `printer-form.component` dérive le type depuis l'imprimante sélectionnée (plus de sélecteur manuel), réutilise `NotificationInlineComponent` (pas de nouveau composant `Banner`, confirmé absent du projet). `printer-list.component` : bouton "Tester l'impression" par ligne.
- **Infrastructure de test créée** : `org.pluribourse.shared.PrinterBridgeDouble` (nouveau, `com.sun.net.httpserver.HttpServer`) — double HTTP réutilisable pour PrinterBridge, remplace le `ServerSocket` utilisé jusqu'ici pour simuler une imprimante A4 dans `PrinterRegistryIT`/`PrintInfrastructureIT`/`PrinterSelectionIT`/`PrintQueueDiagnosticsIT`. Câblé via `@DynamicPropertySource` (propriété `printerbridge.base-url` injectée avant démarrage du contexte Spring de chaque classe de test).
- 3 échecs de test corrigés lors de la première exécution complète (tous liés à des détails non anticipés dans la story, pas à des erreurs de conception) :
  1. Test attendant un 422 pour `printerBridgeId` manquant → corrigé en 400 (conséquence directe de la déviation `@NotBlank` ci-dessus).
  2. Un identifiant de test (`"bridge-slip-thermal-never-registered"`, 36 caractères) dépassait `@Size(max = 32)` sur `printerBridgeId` → raccourci.
  3. Un test attendait encore `IllegalStateException` pour un échec de connexion PrinterBridge, alors que le comportement corrigé (voir Story 3.12 ci-dessous) lève désormais `PrinterBridgeUnavailableException` dans ce cas précis → assertion mise à jour.
- **Point de vigilance signalé par la story confirmé en pratique** : après application seule des changements de cette story, le projet ne compilait plus (`ThermalPrintService`/`DocumentPrintService` référençaient des champs supprimés de `Printer`). Question posée à l'utilisateur, qui a choisi d'enchaîner directement sur la Story 3.12 dans la même session plutôt qu'un correctif temporaire — voir le Dev Agent Record de la Story 3.12 pour la suite.

### File List

**Backend — nouveaux fichiers**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/exception/PrinterBridgeUnavailableException.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterBridgeClient.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterBridgeDiscoveredPrinter.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterBridgePrinterType.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrintResult.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/entity/PrinterStatus.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/ThermalPrinterConnectivityChecker.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/DiscoveredPrinterDto.java`
- `pluribourse-backend/src/main/resources/db/changelog/018-printer-bridge-id.xml`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/PrinterBridgeDouble.java`

**Backend — fichiers modifiés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/entity/Printer.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/CreatePrinterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/PrinterDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/NetworkPrinterConnectivityChecker.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/PrinterService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/controller/PrinterController.java`
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/resources/application-dev.properties`
- `pluribourse-backend/src/main/resources/application-prod.properties`
- `pluribourse-backend/src/test/resources/application.properties`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterRegistryIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintInfrastructureIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrinterSelectionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/print/PrintQueueDiagnosticsIT.java`

**Backend — fichiers supprimés**
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/dto/SerialPortDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/domain/print/service/SerialPrinterConnectivityChecker.java` (renommé, voir `ThermalPrinterConnectivityChecker.java`)

**Frontend — fichiers modifiés**
- `pluribourse-frontend/src/app/models/printer-registry.model.ts`
- `pluribourse-frontend/src/app/services/printer-registry.service.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.html`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.html`
- `pluribourse-frontend/src/app/features/admin/printers/printer-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/printers/printer-list.component.spec.ts`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/public/i18n/en.json`

## Change Log

- 2026-07-27 : Implémentation complète de la Story 3.11 (intégration PrinterBridge — connexion, découverte, statut, test d'impression). Enchaînée immédiatement sur la Story 3.12 dans la même session (accord utilisateur explicite) car les deux stories laissent le projet dans un état non compilable prises séparément. 3 corrections mineures apportées en cours de route (code HTTP 400 vs 422 pour `printerBridgeId` manquant, longueur d'un identifiant de test, type d'exception attendu par un test). 298/298 tests backend et 430/430 tests frontend passent, aucune régression. Statut → `review`.
