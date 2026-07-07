---
baseline_commit: 7917a0b
---

# Story 3.4: Infrastructure d'impression — Registre d'imprimantes & Files dynamiques

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

> **Story technique prérequise (infrastructure enabler)** — aucune valeur utilisateur visible en sprint review. Livrée avant les Stories 3.5 (étiquettes), 3.6 (bordereau PDF), 3.7 (diagnostic admin), 3.8 (registre admin complet) et 3.9 (sélection imprimante bénévole), qui consomment `PrintQueueService`. La Definition of Done repose uniquement sur les ACs techniques ci-dessous.

## Story

As a bénévole déclenchant une impression,
I want que les travaux d'impression soient traités côté serveur et routés vers l'imprimante que j'ai sélectionnée,
so that l'impression fonctionne depuis n'importe quel poste connecté via navigateur pendant l'événement.

## Acceptance Criteria

1. Au démarrage de l'application, une `LinkedBlockingDeque` (voir Dev Notes § Choix de collection) et un thread consommateur dédié sont instanciés pour **chaque imprimante déjà enregistrée en base** (ARCH-009). Les imprimantes THERMAL utilisent jSerialComm sur le port série RFCOMM Bluetooth configuré ; les imprimantes A4 utilisent une socket TCP vers l'adresse réseau configurée.
2. Au démarrage, chaque port série (THERMAL) et chaque adresse réseau (A4) configurés sont testés en accessibilité. Toute imprimante inaccessible est marquée en erreur dans son état de statut runtime (FR-079) — l'application démarre normalement malgré une imprimante hors ligne.
3. Plusieurs travaux soumis vers la même imprimante s'exécutent séquentiellement, un à la fois. Les files de deux imprimantes différentes s'exécutent indépendamment, sans se bloquer mutuellement (FR-029).
4. Un travail d'impression peut être soumis programmatiquement (`PrintQueueService.submit(printerId, job)`) sans appel direct depuis un contrôleur — c'est le seul point d'entrée pour toute story consommatrice (3.5, 3.6).
5. Quand un travail échoue (exception levée par le job), la file de cette imprimante est suspendue (elle cesse de consommer) et la dernière erreur est mémorisée ; les files des autres imprimantes ne sont pas affectées.
6. Un nouveau point d'entrée admin `POST /admin/printers` permet d'enregistrer une imprimante (THERMAL ou A4) : à la sauvegarde, une file et un thread consommateur sont instanciés dynamiquement pour cette imprimante, sans redémarrage de l'application (ARCH-009, préfigure FR-077/FR-032 exploités pleinement par la Story 3.8).

**Hors périmètre de cette story (voir Dev Notes § Scope) :** génération de contenu ESC/POS ou PDF réel (Stories 3.5/3.6) ; vue de diagnostic, relance/ignorance d'un job en erreur (Story 3.7) ; UI d'administration complète (dropdown des ports série, liste, suppression d'imprimante) et sélection imprimante par le bénévole (Stories 3.8/3.9).

## Tasks / Subtasks

- [x] Backend — dépendance & entité `Printer` (AC: 1, 2, 6)
  - [x] Ajouter la dépendance Maven `com.fazecast:jSerialComm:2.11.4` au `pom.xml` — **ne pas** ajouter `escpos-coffee` dans cette story (génération du contenu ESC/POS = Story 3.5, pas encore nécessaire)
  - [x] Migration Liquibase `016-printers.xml` (voir Dev Notes § Schéma), incluse dans `db.changelog-master.xml`
  - [x] Nouveau module `org.pluribourse.print` : `entity/Printer.java`, `entity/PrinterType.java` (enum `THERMAL, A4`), `repository/PrinterRepository.java extends JpaRepository<Printer, Long>`
- [x] Backend — `PrintQueueService` : orchestration des files (AC: 1, 2, 3, 4, 5)
  - [x] `service/PrintQueueService.java` (`@Component`, `@Slf4j`, injection par constructeur de `PrinterRepository` + `PrinterConnectivityChecker`) : `Map<Long, PrinterQueueHandle>` (`ConcurrentHashMap`), `@PostConstruct` charge tous les `Printer` existants et appelle `registerPrinter()` pour chacun
  - [x] `service/PrinterQueueHandle.java` (classe interne ou dédiée) : `LinkedBlockingDeque<PrintJob>`, `Thread` consommateur dédié (nommé `print-queue-{printerId}`, daemon), `volatile boolean suspended`, `volatile String lastError`, `volatile PrintJob lastFailedJob`
  - [x] `registerPrinter(Printer)` : teste l'accessibilité via `PrinterConnectivityChecker`, marque `lastError` si inaccessible (ne bloque pas la création de la file/thread), démarre le thread consommateur
  - [x] Boucle du consommateur : si `suspended`, attendre (ne pas consommer) ; sinon `deque.takeFirst()` (bloquant), exécuter `job.execute(printer)`, en cas d'exception : `lastError` + `lastFailedJob` renseignés, `suspended = true`
  - [x] `submit(Long printerId, PrintJob job)` : résout le handle (404 via `PrinterNotFoundException` si imprimante inconnue), `deque.putLast(job)` — accepté même si la file est suspendue (le job attend la reprise)
  - [x] `interface PrintJob { void execute(Printer printer); }` (package `org.pluribourse.print.service`, ou sous-package dédié) — contrat consommé par les Stories 3.5/3.6, pas de logique métier ici
- [x] Backend — vérification d'accessibilité (AC: 2)
  - [x] `service/PrinterConnectivityChecker.java` (interface) + deux implémentations : `SerialPrinterConnectivityChecker` (jSerialComm : `SerialPort.getCommPort(printer.getSerialPort())`, tente `openPort()`/`closePort()`) et `NetworkPrinterConnectivityChecker` (`java.net.Socket` avec timeout de connexion vers `host:port`) ; un dispatcher (ou le service lui-même) choisit l'implémentation selon `printer.getType()`
- [x] Backend — enregistrement minimal d'imprimante (AC: 6)
  - [x] `dto/CreatePrinterDto.java` (record) : `name` (`@NotBlank @Size(max=100)`), `type` (`@NotNull PrinterType`), `serialPort` (nullable, thermal), `widthMm` (nullable `Integer`, thermal), `host` (nullable, A4), `port` (nullable `Integer`, A4)
  - [x] `dto/PrinterDto.java` (record) : `id`, `name`, `type`, `serialPort`, `widthMm`, `host`, `port` — **pas** de champ de statut (voir Dev Notes § Statut runtime vs persistance)
  - [x] `mapper/PrinterMapper.java` (MapStruct, pattern identique aux autres mappers du projet)
  - [x] `service/PrinterService.java` : `create(CreatePrinterDto)` — valide la cohérence type/champs (422 `invalid-printer-configuration` si THERMAL sans `serialPort`/`widthMm`, ou A4 sans `host` ; `port` défaulté à `9100` si null pour A4 ; `widthMm` doit valoir 57 ou 80 pour THERMAL), persiste via `PrinterRepository`, appelle `PrintQueueService.registerPrinter()` pour instancier la file dynamiquement
  - [x] `controller/PrinterController.java` (`@RequestMapping("/admin/printers")`, `@PreAuthorize("hasRole('ADMIN')")`, pattern identique à `GlobalInstanceConfigController`) : `POST /` → 201 + `PrinterDto`. **Ne pas** ajouter `GET`/`DELETE` ni le dropdown des ports série disponibles — hors périmètre (Story 3.8)
  - [x] `exception/PrinterNotFoundException.java` (404, `printer-not-found`) et `exception/InvalidPrinterConfigurationException.java` (422, `invalid-printer-configuration`), toutes deux `extends BusinessException`
- [x] Tests backend (AC: 1-6)
  - [x] `PrintInfrastructureIT` (`org.pluribourse.print`, E2E via `IntegrationTest`) : voir Dev Notes § Stratégie de test — combine appels HTTP (`POST /admin/printers`) et appels directs sur le bean `PrintQueueService` autowired (exception documentée et bornée à cette story, voir Dev Notes)
- [x] Aucun changement frontend dans cette story (pas de valeur UI livrée)

### Review Findings

- [x] [Review][Decision] Imprimante A4 : socket réseau vers host/port fourni par l'admin sans validation (surface SSRF) — `NetworkPrinterConnectivityChecker` ouvre une `Socket` brute vers l'host/port fourni tel quel par l'admin, sans allowlist ni restriction (ex. adresses loopback/link-local/metadata). **Résolu avec l'utilisateur 2026-07-07 : risque accepté** — instance auto-hébergée, endpoint `ADMIN`-only, configuration réseau local de l'événement ; frontière de confiance acceptée telle quelle, aucun code à changer. [pluribourse-backend/src/main/java/org/pluribourse/print/service/NetworkPrinterConnectivityChecker.java]
- [x] [Review][Patch] Pas de timeout explicite sur `SerialPrinterConnectivityChecker.openPort()` [pluribourse-backend/src/main/java/org/pluribourse/print/service/SerialPrinterConnectivityChecker.java] — **Corrigé** : `openPort()` exécuté sur un thread dédié borné à 2000ms via `CompletableFuture.get(timeout)`, `IllegalStateException` levée en cas de dépassement.
- [x] [Review][Patch] Message d'exception perdu (peut être `null`) dans `lastError`, masquant silencieusement un échec réel [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:79, PrinterQueueHandle.java:66] — **Corrigé** : nouvelle méthode `PrinterQueueHandle.describeError()` avec repli sur le nom de la classe d'exception si le message est `null`, utilisée aux deux endroits.
- [x] [Review][Patch] Nom d'imprimante dupliqué remonte en 500 brut (`DataIntegrityViolationException` non gérée) au lieu d'un 422 propre [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterService.java:34] — **Corrigé** : `repository.save()` entouré d'un `catch (DataIntegrityViolationException e)` relançant `InvalidPrinterConfigurationException`.
- [x] [Review][Patch] `CreatePrinterDto` : bornes de validation manquantes (`@Size` sur `serialPort`/`host`, `@Min`/`@Max` sur `port`) [pluribourse-backend/src/main/java/org/pluribourse/print/dto/CreatePrinterDto.java] — **Corrigé** : `@Size(max=100)` sur `serialPort`, `@Size(max=255)` sur `host`, `@Min(1)`/`@Max(65535)` sur `port`.
- [x] [Review][Patch] Le thread consommateur meurt silencieusement sur une `Error` non-RuntimeException (seul `catch (RuntimeException e)` est présent), sans supervision ni possibilité de reprise même future [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterQueueHandle.java:65] — **Corrigé** : `catch (Throwable e)` dans la boucle de consommation, avec commentaire justifiant pourquoi ce thread daemon ne doit jamais mourir.
- [x] [Review][Patch] `@Version` placé en dernier champ dans `Printer.java` au lieu du premier, contrairement à la convention `Edition.java` explicitement citée par cette story [pluribourse-backend/src/main/java/org/pluribourse/print/entity/Printer.java:35-36] — **Corrigé** : `@Version` déplacé avant `@Id`, identique à `Edition.java`.
- [x] [Review][Patch] I/O bloquant de vérification de connectivité exécuté dans `computeIfAbsent` — viole le contrat `ConcurrentHashMap` (fonction de remapping doit être rapide et ne pas toucher d'autres entrées) [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:54,73-84] — **Corrigé** : `createHandle()` construit désormais en dehors de `computeIfAbsent` (check `containsKey` + `putIfAbsent`).

Vérifié : `PrintInfrastructureIT` (11/11) et la suite backend complète (214/214) passent après application des patches, aucune régression.
- [x] [Review][Defer] File suspendue ne reprend jamais automatiquement, croissance illimitée de la `LinkedBlockingDeque`, attente active 200ms tant que suspendue [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterQueueHandle.java:18,58-59,68] — deferred, pre-existing, explicitement pré-scopé à la Story 3.7 (reprise/relance d'une file en erreur)
- [x] [Review][Defer] Pas de hook d'arrêt (`@PreDestroy`) pour interrompre proprement les threads consommateurs daemon à l'arrêt de l'application [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterQueueHandle.java] — deferred, pre-existing, threads daemon évitent un blocage JVM ; drain propre = amélioration future hors AC
- [x] [Review][Defer] Pas d'endpoint update/delete ; le handle en mémoire garde un instantané `Printer` périmé et `registerPrinter()` est un no-op pour un id déjà enregistré, risque de thread orphelin si une imprimante était un jour supprimée [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:54] — deferred, pre-existing, explicitement scope de la Story 3.8 (registre admin complet)
- [x] [Review][Defer] Aucun `PrinterConnectivityChecker` pour un `PrinterType` donné → `map.get()` retourne `null` → NPE avalée en tant que `lastError` [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:75-77] — deferred, pre-existing, actuellement inatteignable (exactement 2 checkers pour les 2 types existants)
- [x] [Review][Defer] `registerPrinter` appelé avec un `Printer` transitoire (id `null`) provoquerait une NPE sur clé nulle de la `ConcurrentHashMap` [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:54] — deferred, pre-existing, actuellement inatteignable (les deux points d'appel persistent l'imprimante avant l'enregistrement)
- [x] [Review][Defer] Un second `PrinterConnectivityChecker` enregistré pour le même type ferait planter le démarrage (`Collectors.toMap` sans fonction de fusion) [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java:31-32] — deferred, pre-existing, actuellement inatteignable, échec rapide et visible si ça survenait
- [x] [Review][Defer] Tests sensibles au timing (attente active à intervalle fixe, hypothèses d'ordonnancement de threads dans le test `Order 8`) [pluribourse-backend/src/test/java/org/pluribourse/print/PrintInfrastructureIT.java:254-263] — deferred, pre-existing, compromis accepté pour tester l'orchestration de threads sans mock
- [x] [Review][Defer] `registerPrinter()` s'exécute dans le bloc `@Transactional` de `create()` : connexion DB tenue ouverte pendant l'I/O bloquant, risque de handle orphelin en cas de rollback futur [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterService.java:27-37] — deferred, pre-existing, non exploitable aujourd'hui (le corps de la méthode ne peut pas échouer après l'appel à `registerPrinter`)
- [x] [Review][Defer] Champs non pertinents pour le type choisi (`host`/`port` pour THERMAL, `serialPort`/`widthMm` pour A4) ni rejetés ni nettoyés, persistés silencieusement [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterService.java:39-52] — deferred, pre-existing, endpoint volontairement minimal dans cette story, richesse de validation naturellement du ressort du vrai formulaire de la Story 3.8

## Dev Notes

### Scope — ce que cette story fait et ne fait pas

Les ACs de l'épic pour la Story 3.4 (`epics.md` lignes 1148-1188) décrivent l'infrastructure de file d'impression elle-même : instanciation des files/threads au démarrage, exécution séquentielle par imprimante, isolation entre files, remontée d'erreur avec suspension. **Ne pas implémenter dans cette story** :
- La génération du contenu à imprimer (ESC/POS pour étiquettes, PDF pour bordereau) — Stories 3.5/3.6. `PrintJob.execute(Printer)` reste un contrat vide de logique métier ici.
- La vue de diagnostic (`/admin/print-queue`), la relance (« Relancer ») et l'abandon (« Ignorer ») d'un job en erreur — Story 3.7. Cette story expose seulement les champs internes (`suspended`, `lastError`, `lastFailedJob`) nécessaires à leur future lecture/mutation par 3.7, sans les exposer via HTTP.
- Le registre admin complet (`/admin/printers` : liste, formulaire avec dropdown des ports série via `SerialPort.getCommPorts()`, suppression avec destruction de file) — Story 3.8. Cette story n'ajoute que `POST /admin/printers`, strictement pour permettre à ses propres tests d'enregistrer une imprimante et d'exercer l'orchestration des files ; **pas de `GET`/`DELETE`**.
- La sélection d'imprimante par le bénévole à la connexion — Story 3.9.

**Décision de scope à confirmer en review** (sur le modèle des arbitrages déjà actés en Story 3.3) : le périmètre exact de `POST /admin/printers` dans cette story-ci plutôt qu'en 3.8 est un choix d'implémentation — il n'existe aucune AC explicite de l'épic pour 3.4 qui mentionne un endpoint HTTP. Il est nécessaire ici uniquement parce que la philosophie de test du projet (E2E via contrôleurs) exige un moyen d'enregistrer une imprimante en base pour tester l'orchestration des files ; sans lui, aucun test ne pourrait exercer AC 6 ni alimenter les tests d'AC 1-5 de façon réaliste. Si l'utilisateur préfère reporter tout endpoint HTTP à la Story 3.8, l'alternative est de peupler les imprimantes de test uniquement via `PrinterRepository.save()` direct dans les tests (voir Dev Notes § Stratégie de test, option B) — signalé en review plutôt qu'improvisé silencieusement.

### Pourquoi jSerialComm et pas encore escpos-coffee

`epics.md` ARCH-009 (ligne 173) mentionne les deux : « escpos-coffee (ou équivalent) pour l'impression thermique ESC/POS via jSerialComm (port série RFCOMM Bluetooth) ». Ces deux bibliothèques ont des rôles distincts : **jSerialComm** ouvre/écrit sur le port série physique (nécessaire dès cette story pour le test d'accessibilité AC 2) ; **escpos-coffee** génère le flux d'octets ESC/POS (nom d'article, code-barres bitmap, etc.) — logique de contenu qui n'existe pas encore (Story 3.5, qui n'a pas encore été implémentée, y compris la génération de code-barres pour les articles individuels de la Story 3.2/3.3). N'ajouter que jSerialComm maintenant ; ajouter escpos-coffee en Story 3.5 quand son usage réel apparaît.

**Note sur une divergence d'artefact plus ancienne :** l'addendum PRD (`prds/prd-PluriBourse-2026-06-08/addendum.md`, section « Impression étiquettes ») décrit une imprimante thermique **USB**. C'est une information obsolète : `epics.md` (ARCH-009, Story 3.4 AC1, `EXPERIENCE.md` lignes 117/188/270/301 — « Vérifiez la connexion Bluetooth / réseau ») est cohérent et plus récent sur le choix **Bluetooth RFCOMM via jSerialComm**. Suivre `epics.md`, pas l'addendum, sur ce point précis.

### Schéma de migration `016-printers.xml`

Prochain numéro de migration disponible : `016` (dernier existant : `015-lots.xml`).

```xml
<createTable tableName="printers">
    <column name="id" type="BIGINT" autoIncrement="true">
        <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="name" type="VARCHAR(100)">
        <constraints nullable="false" unique="true" uniqueConstraintName="uk_printers_name"/>
    </column>
    <column name="type" type="VARCHAR(20)"><constraints nullable="false"/></column>
    <column name="serial_port" type="VARCHAR(100)"><constraints nullable="true"/></column>
    <column name="width_mm" type="INT"><constraints nullable="true"/></column>
    <column name="host" type="VARCHAR(255)"><constraints nullable="true"/></column>
    <column name="port" type="INT"><constraints nullable="true"/></column>
    <column name="version" type="BIGINT" defaultValueNumeric="0"><constraints nullable="false"/></column>
</createTable>
```

- Une seule table `printers` avec colonnes nullables spécifiques à chaque variante (`serial_port`/`width_mm` pour THERMAL, `host`/`port` pour A4), pas de table par type — cohérent avec l'arborescence indicative de `architecture.md` (ligne 630 : `Printer.java (type: THERMAL | A4, port série ou IP/port)`, une seule entité).
- **Pas de colonne de statut** (voir section suivante).
- `name` unique : évite deux imprimantes de même nom d'affichage, utile dès la Story 3.9 (sélection par nom).

### Statut runtime vs persistance — ne pas persister l'état des files

L'état d'une imprimante (accessible/en erreur, profondeur de file, job en cours, dernière erreur) est **entièrement recalculé à chaque démarrage de l'application** — les files et threads sont recréés à froid (AC 1). Persister ce statut en base introduirait une source de vérité obsolète dès le redémarrage suivant (ex. un statut "en erreur" figé en base alors que l'imprimante est réparée et que le thread tourne normalement). Garder cet état **exclusivement en mémoire**, porté par `PrinterQueueHandle` (un par imprimante, conservé dans la map de `PrintQueueService`). `PrinterDto`/`Printer` (entité) ne contiennent que la configuration (nom, type, port série ou host/port, largeur) — jamais de statut. La Story 3.7 lira cet état directement depuis `PrintQueueService` (méthode à ajouter par cette story future, ex. `getStatus(Long printerId)`), pas depuis la base.

### Choix de collection : `LinkedBlockingDeque` plutôt que `LinkedBlockingQueue`

`architecture.md` (ligne 257) et `epics.md` (ARCH-009) nomment `LinkedBlockingQueue`. Cette story recommande `LinkedBlockingDeque` à la place — même famille (FIFO, bornable, thread-safe, bloquante), mais avec `putFirst()`/`takeFirst()` en plus. Raison : la Story 3.7 (AC « Relancer ») exige que le job en erreur soit « remis **en tête** de file » — impossible avec une `LinkedBlockingQueue` simple sans passer par une structure annexe. Adopter `LinkedBlockingDeque` dès maintenant (utilisée uniquement en mode FIFO standard — `putLast`/`takeFirst` — pour tout le périmètre de cette story) évite une migration de type plus tard. **Ceci est une déviation mineure et délibérée du nom exact de classe cité dans l'architecture** — à signaler en review si l'utilisateur préfère rester strictement sur `LinkedBlockingQueue` et traiter le « remis en tête » autrement en Story 3.7 (ex. file annexe à un seul élément prioritaire).

### Thread par imprimante — pas de pool

Un `Thread` (platform thread, daemon, nommé `print-queue-{printerId}`) dédié par imprimante, démarré à l'enregistrement (démarrage ou `POST /admin/printers`). Le nombre d'imprimantes reste faible (quelques unités par événement) — pas de justification à un pool ou à des threads virtuels ici ; rester simple (cohérent avec le choix `LinkedBlockingQueue`/`Deque` en mémoire plutôt qu'une solution de messagerie externe, `architecture.md` ligne 257 : "Simple, pas d'infrastructure supplémentaire").

### Stratégie de test — exception documentée et bornée à cette story

Le projet suit strictement « E2E par les contrôleurs uniquement » (CLAUDE.md). Cette story est un cas limite légitime : **aucun contrôleur n'expose encore la soumission d'un job d'impression** (ce sera `ThermalPrintService`/`DocumentPrintService`, Stories 3.5/3.6, qui appelleront `PrintQueueService.submit()` en interne) ni l'état d'une file (Story 3.7). Construire des endpoints jetables pour contourner cette limite dupliquerait le périmètre de stories futures. **Décision** : `PrintInfrastructureIT` (package `org.pluribourse.print`, étend `IntegrationTest`) combine :
1. **Via HTTP** — `POST /admin/printers` (session admin) pour créer une imprimante A4 pointant vers un `ServerSocket` de test local (`localhost`, port éphémère ouvert dans `@BeforeAll`) → 201, `PrinterDto` retourné sans champ de statut. Refus 422 si configuration incohérente (THERMAL sans `serialPort`, A4 sans `host`). Accès refusé 403 pour une session bénévole (comme tous les autres endpoints `/admin/**`).
2. **Via appel direct sur le bean `PrintQueueService` autowired** (exception documentée, pas un test de service isolé au sens interdit par CLAUDE.md — le bean est celui du contexte Spring réel, complètement intégré) : `submit(printerId, job)` pour vérifier l'exécution séquentielle (plusieurs jobs sur la même imprimante s'exécutent dans l'ordre — utiliser une liste synchronisée que chaque job remplit), l'indépendance entre deux files (un job lent sur l'imprimante A ne retarde pas un job sur l'imprimante B), la suspension après échec (job qui lève une exception → `lastError` renseigné, job suivant sur la même file non consommé tant que non repris).
3. **Rechargement à froid simulé** pour AC 1/2 : insérer une ligne `Printer` via `PrinterRepository.save()` (pas via HTTP) puis invoquer directement la méthode de (re)chargement de `PrintQueueService` (rendre cette méthode `public` ou package-visible, ex. `reloadFromDatabase()`, appelée aussi bien par `@PostConstruct` que par ce test) pour simuler un redémarrage sans relancer réellement la JVM — technique standard, pas un contournement.
4. **Gap accepté, non testé automatiquement** : le chemin `SerialPrinterConnectivityChecker` (jSerialComm réel) ne peut pas être exercé en CI sans matériel Bluetooth — aucune imprimante THERMAL n'est présente dans `test-data.sql` (partagé par **toutes** les classes IT, ne jamais y ajouter d'imprimante — cela déclencherait de vrais accès matériel/série au démarrage de chaque classe de test du projet). Seul le chemin A4 (`NetworkPrinterConnectivityChecker`, testable via `ServerSocket` local) est couvert par des tests automatisés. Cohérent avec l'absence de CI (`architecture.md` ligne 298) et avec des gaps similaires déjà acceptés en Story 3.3.

### Risque connu — jSerialComm sur cible Raspberry Pi

`architecture.md` cible un déploiement Raspberry Pi (ligne 54). jSerialComm embarque des binaires natifs pour ARM32/ARMHF/ARM64 et fonctionne en pratique sur Raspberry Pi, mais des problèmes de chargement de bibliothèque native ont été rapportés sur certains modèles (Pi Zero 2W notamment, cf. issues GitHub Fazecast/jSerialComm #503, #455). Non bloquant pour cette story (aucun matériel Raspberry Pi disponible en dev), mais à vérifier lors du premier déploiement réel sur le Raspberry Pi cible — signaler à l'utilisateur si un modèle Pi Zero est envisagé.

### Project Structure Notes

- Nouveau module top-level `org.pluribourse.print` (pas sous `shared/`) : `entity/`, `repository/`, `service/`, `dto/`, `mapper/`, `controller/`, `exception/` — même structure que `item/`, `seller/`, `edition/`.
- Prochain numéro de migration : `016` (dernier existant : `015-lots.xml`).
- Prochain numéro de story après celle-ci : `3-5-generation-impression-des-etiquettes-thermiques`.
- Dépendance ajoutée : `com.fazecast:jSerialComm:2.11.4` (Maven Central, dernière version stable vérifiée). **Ne pas** ajouter `escpos-coffee` (Story 3.5).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.4, lignes 1148-1188] (ACs)
- [Source: _bmad-output/planning-artifacts/epics.md#ARCH-009, ligne 173 ; FR-029, FR-032, FR-077, FR-078, FR-079, ligne 63-66, 138-141]
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 253-263] (décisions Infrastructure d'Impression : file en mémoire, injection par constructeur, gestion d'erreur)
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 626-632] (arborescence indicative module `print/`)
- [Source: _bmad-output/planning-artifacts/architecture.md, lignes 774-778] (frontière d'impression : `PrintQueueService` seul point d'entrée, consommateurs sur threads dédiés)
- [Source: _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md#Impression étiquettes] (info obsolète USB — voir Dev Notes § jSerialComm)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md, lignes 117, 188, 270, 301] (confirmation Bluetooth/réseau dans les messages d'erreur utilisateur)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseEmitterRegistry.java] (pattern de registre en mémoire thread-safe réutilisé comme référence pour `PrintQueueService`)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SessionInvalidationService.java] (convention `@Slf4j` Lombok)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/instanceconfig/controller/GlobalInstanceConfigController.java] (pattern `@RequestMapping("/admin/...")` + `@PreAuthorize("hasRole('ADMIN')")`)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/exception/GlobalExceptionHandler.java, BusinessException.java] (pattern RFC 7807 réutilisé pour les nouvelles exceptions)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java] (convention entité : Lombok `@Getter @Setter @NoArgsConstructor`, `@Version` en tête, `@Enumerated(EnumType.STRING)`)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/shared/IntegrationTest.java] (base de test E2E : `@SpringBootTest`, `@DirtiesContext(AFTER_CLASS)`, contexte partagé — d'où l'interdiction d'ajouter des imprimantes dans `test-data.sql`)
- [Source: pluribourse-backend/src/test/resources/db/changelog/db.changelog-test.xml, test-data.sql] (changelog de test global à toutes les classes IT)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/item/ItemManagementIT.java] (pattern `@TestMethodOrder`, sessions admin/volontaire/vendeur créées une fois, `MockHttpSession`)
- [Source: pluribourse-backend/pom.xml] (dépendances actuelles — aucune bibliothèque d'impression présente avant cette story)
- [Source: _bmad-output/implementation-artifacts/3-3-creation-et-gestion-des-lots.md] (Dev Notes de la story précédente : dernier numéro de migration utilisé, prochaine story identifiée, conventions de test)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `mvnw.cmd -o test -Dtest=PrintInfrastructureIT` → 11/11 passed
- `mvnw.cmd -o test` (full backend suite) → 214/214 passed, BUILD SUCCESS, no regressions

### Completion Notes List

- Implémenté le module `org.pluribourse.print` complet : entité `Printer`/`PrinterType`, `PrinterRepository`, `PrintQueueService` (registre thread-safe `ConcurrentHashMap<Long, PrinterQueueHandle>`, rechargement à froid via `@PostConstruct` + `reloadFromDatabase()` public réutilisable par les tests), `PrinterQueueHandle` (un `Thread` daemon + `LinkedBlockingDeque<PrintJob>` par imprimante, suspension après échec), `PrinterConnectivityChecker` + 2 implémentations (`SerialPrinterConnectivityChecker` via jSerialComm, `NetworkPrinterConnectivityChecker` via `Socket`), `PrinterService`/`PrinterController` (`POST /admin/printers`, ADMIN uniquement), exceptions `PrinterNotFoundException` (404) et `InvalidPrinterConfigurationException` (422).
- Suivi les deux déviations mineures déjà documentées et pré-signalées dans les Dev Notes : `LinkedBlockingDeque` au lieu de `LinkedBlockingQueue` (nommé dans `architecture.md`/`epics.md`), et le périmètre de `POST /admin/printers` inclus dans cette story plutôt que reporté à la 3.8 — **à confirmer par l'utilisateur en review**, aucune des deux n'est un fait accompli irréversible (renommage de collection trivial ; endpoint déplaçable vers 3.8 sans re-façonnage si souhaité).
- Le champ `port` de `Printer`/`PrinterDto` n'a pas de contrainte `NOT NULL` en base (nullable en XML) pour rester cohérent avec THERMAL où `port` est sans objet — le défaut `9100` est appliqué uniquement en service pour A4, jamais en colonne.
- Gap de couverture assumé et documenté dans les Dev Notes de la story (§ Stratégie de test, point 4) : le chemin `SerialPrinterConnectivityChecker` (jSerialComm réel sur port série) n'est testé par aucun test automatisé — pas de matériel Bluetooth/série disponible en CI, cohérent avec l'absence d'imprimante THERMAL dans `test-data.sql`. Seul le chemin A4 (`NetworkPrinterConnectivityChecker`) est couvert.
- `PrintInfrastructureIT` (11 tests) couvre les 6 ACs : validation 422 (THERMAL sans `serialPort`/`widthMm`, A4 sans `host`), 403 bénévole, 201 sans champ de statut, défaut `port=9100`, création réussie malgré une cible injoignable (file marquée en erreur sans bloquer), exécution séquentielle sur une même file, indépendance entre deux files, suspension après échec sans affecter les autres files, 404 sur imprimante inconnue, rechargement à froid simulé (`reloadFromDatabase()`) enregistrant une imprimante déjà en base et la marquant en erreur si injoignable.

### File List

- `pluribourse-backend/pom.xml` (modifié — dépendance jSerialComm)
- `pluribourse-backend/src/main/resources/db/changelog/016-printers.xml` (nouveau)
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` (modifié — include 016)
- `pluribourse-backend/src/main/java/org/pluribourse/print/entity/Printer.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/entity/PrinterType.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/repository/PrinterRepository.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintJob.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterConnectivityChecker.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/SerialPrinterConnectivityChecker.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/NetworkPrinterConnectivityChecker.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterQueueHandle.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/dto/CreatePrinterDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/dto/PrinterDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/mapper/PrinterMapper.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/controller/PrinterController.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/exception/PrinterNotFoundException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/exception/InvalidPrinterConfigurationException.java` (nouveau)
- `pluribourse-backend/src/test/java/org/pluribourse/print/PrintInfrastructureIT.java` (nouveau)

## Change Log

- 2026-07-07 : Implémentation complète de la Story 3.4 (infrastructure d'impression : registre `Printer`, `PrintQueueService`, files/threads dynamiques par imprimante, `POST /admin/printers`). 214/214 tests backend passent, aucune régression.
