---
baseline_commit: 2136a8c
---

# Story 3.9: Sélection d'imprimante par le bénévole à la connexion (FR-098)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

> **Story réordonnée avant 3.5/3.6/3.7/3.8** (décision utilisateur, 2026-07-07) : `epics.md` liste cette story en dernier de l'Epic 3, mais la Story 3.5 (étiquettes) a une AC qui route l'impression thermique vers « l'imprimante sélectionnée en session » — mécanisme que seule cette story introduit. Sans elle, 3.5 ne peut pas déterminer vers quel `printerId` appeler `PrintQueueService.submit()`. Construire 3.9 en premier lève ce blocage pour 3.5/3.6.

## Story

As a bénévole,
I want choisir mon imprimante thermique et mon imprimante A4 à ma connexion,
so that mes travaux d'impression soient routés vers l'imprimante la plus proche de mon poste.

## Acceptance Criteria

1. Quand un bénévole se connecte avec succès (`POST /auth/login`), un écran de sélection d'imprimante (`/printer-selection`) s'affiche avant l'accès à l'interface principale — deux menus déroulants : un pour les imprimantes thermiques disponibles, un pour les imprimantes A4 disponibles (FR-098).
2. Une imprimante n'apparaît dans un menu que si elle est enregistrée **et** actuellement disponible (accessible, file non suspendue — voir Dev Notes § Critère de disponibilité). Un admin n'est jamais soumis à cet écran (restriction VOLUNTEER only, voir Dev Notes § Décision de scope).
3. Quand le bénévole valide sa sélection (un choix par type, ou aucun), elle est stockée **en session uniquement** (jamais en base) ; le bénévole est redirigé vers `/volunteer`, qui résout déjà la page d'atterrissage selon la phase active (`resolveVolunteerLandingPath`, inchangé par cette story).
4. Un travail d'impression thermique soumis pendant la session est routé vers la file de l'imprimante thermique sélectionnée — cette story expose le point de résolution (`PrinterSelectionService.getSelectedPrinterId(session, type)`) que les Stories 3.5/3.6 consommeront ; le routage effectif d'un job réel n'est pas testable ici (aucun endpoint de déclenchement d'impression n'existe encore).
5. Si l'imprimante sélectionnée devient indisponible au moment d'un job (géré nativement par `PrintQueueService`/`PrinterQueueHandle` — Story 3.4, AC 5) : le job échoue et est enregistré comme erreur sur la file de cette imprimante, sans retry ni reroutage automatique — aucun code supplémentaire requis dans cette story, ce comportement existe déjà.
6. Si aucune imprimante n'est enregistrée ou disponible, l'écran affiche un avertissement clair et le bénévole peut tout de même valider (avec des sélections `null`) et accéder à l'interface — l'impression restera en erreur jusqu'à résolution par un admin.
7. Après validation, un rechargement de page (F5) ne réaffiche pas l'écran de sélection tant que la session est active — l'état « déjà sélectionné » est lu depuis le serveur (`GET /printers/selection`), pas depuis un simple flag client volatile.

## Tasks / Subtasks

- [x] Backend — DTOs & exception (module `org.pluribourse.print`, AC: 1, 2, 6)
  - [x] `dto/AvailablePrinterDto.java` (record) : `id` (Long), `name` (String), `type` (PrinterType) — **pas** de champ de statut au sens `PrinterDto` (cohérent avec 3.4 § Statut runtime vs persistance) : cette liste ne contient déjà que des imprimantes filtrées comme disponibles, pas la totalité du registre
  - [x] `dto/PrinterSelectionDto.java` (record, requête POST) : `thermalPrinterId` (Long, nullable), `a4PrinterId` (Long, nullable) — pas de `@NotNull`, les deux sont volontairement optionnels (AC 6)
  - [x] `dto/PrinterSelectionStatusDto.java` (record, réponse GET/POST) : `done` (boolean), `thermalPrinterId` (Long, nullable), `a4PrinterId` (Long, nullable)
  - [x] `exception/InvalidPrinterSelectionException.java` (422, code `invalid-printer-selection`) `extends BusinessException` — levée si l'id fourni existe mais est du mauvais type, ou n'est pas actuellement disponible. Réutiliser `PrinterNotFoundException` (404, déjà existante) si l'id n'existe pas du tout.
- [x] Backend — `PrinterSelectionService` (AC: 1-7)
  - [x] `service/PrinterSelectionService.java` (`@Service`, `@RequiredArgsConstructor`, injection `PrinterRepository` + `PrintQueueService`)
  - [x] `listAvailablePrinters()` : `printerRepository.findAll()`, filtre chaque imprimante dont `printQueueService.getHandle(printer.getId())` est non-null, `!handle.isSuspended()` et `handle.getLastError() == null` (voir Dev Notes § Critère de disponibilité) → liste de `AvailablePrinterDto`
  - [x] `getStatus(HttpSession session)` : lit les 3 attributs de session (voir noms exacts en Dev Notes), retourne `PrinterSelectionStatusDto` — `done=false` et ids `null` si jamais renseignés
  - [x] `selectPrinters(HttpSession session, PrinterSelectionDto dto)` : valide chaque id non-null fourni via une méthode privée `validateSelection(Long printerId, PrinterType expectedType)` (404 si id inconnu, `InvalidPrinterSelectionException` si type incohérent ou imprimante non disponible au sens ci-dessus), stocke les deux ids (potentiellement `null`) **et** le flag `done=true` en session, retourne le `PrinterSelectionStatusDto` résultant
  - [x] `getSelectedPrinterId(HttpSession session, PrinterType type)` (public, `Optional<Long>`) — **contrat exposé pour les Stories 3.5/3.6** : lit l'attribut de session correspondant au type demandé. Aucune autre story ne doit dupliquer la lecture directe des attributs de session.
- [x] Backend — `PrinterSelectionController` (AC: 1, 2, 3, 6, 7)
  - [x] `controller/PrinterSelectionController.java` (`@RequestMapping("/printers")`, `@PreAuthorize("hasRole('VOLUNTEER')")`, `@RequiredArgsConstructor`, pattern identique à `PrinterController`/`AuthController`)
  - [x] `GET /printers/available` → 200 + `List<AvailablePrinterDto>`
  - [x] `GET /printers/selection` → 200 + `PrinterSelectionStatusDto` (lit la session via `HttpServletRequest`, comme `AuthController.changePassword`)
  - [x] `POST /printers/selection` (`@Valid @RequestBody PrinterSelectionDto`) → 200 + `PrinterSelectionStatusDto`
- [x] Backend — Tests (AC: 1-7, philosophie E2E via contrôleurs)
  - [x] `PrinterSelectionIT` (`org.pluribourse.print`, étend `IntegrationTest`) — voir Dev Notes § Stratégie de test pour la construction des imprimantes de test (aucune imprimante THERMAL/A4 dans `test-data.sql`, jamais)
- [x] Frontend — modèle & service (AC: 1, 2, 3, 6, 7)
  - [x] `models/printer.model.ts` : `interface AvailablePrinter { id: number; name: string; type: 'THERMAL' | 'A4'; }`, `interface PrinterSelectionStatus { done: boolean; thermalPrinterId: number | null; a4PrinterId: number | null; }`
  - [x] `services/print.service.ts` (nouveau, correspond à l'entrée indicative `architecture.md` ligne 724) : `getAvailablePrinters(): Observable<AvailablePrinter[]>` (`GET /api/printers/available`), `getSelectionStatus(): Observable<PrinterSelectionStatus>` (`GET /api/printers/selection`), `submitSelection(thermalPrinterId: number | null, a4PrinterId: number | null): Observable<PrinterSelectionStatus>` (`POST /api/printers/selection`)
- [x] Frontend — écran de sélection (AC: 1, 2, 3, 6)
  - [x] `features/auth/printer-selection/printer-selection.component.ts` + `.html` (nouveau fichier, jamais de template inline) + `.scss` — standalone, pattern structurel identique à `change-password.component.ts` (écran plein cadre hors `AppLayoutComponent`, pas de sidebar)
  - [x] Au chargement : appelle `getAvailablePrinters()`, sépare en deux listes par `type`. Si une liste est vide, affiche un avertissement inline (`NotificationInlineComponent`, pattern déjà utilisé par `login.component.ts`/`change-password.component.ts`) — texte via i18n, pas de chaîne codée en dur
  - [x] Deux `MatSelect` (thermique, A4), chacun avec une option vide ("Aucune") en tête de liste — la sélection n'est jamais obligatoire (AC 6)
  - [x] Bouton de validation **toujours actif** (contrairement au bouton "Valider le lot" de la Story 3.3 qui se désactive) — soumet même avec deux sélections vides
  - [x] `onSubmit()` : `submitSelection(...)`, puis `router.navigate(['/volunteer'])` — ne pas dupliquer la logique de résolution de phase, déjà gérée par `volunteer.routes.ts`
- [x] Frontend — routage & garde (AC: 1, 3, 7)
  - [x] `app.routes.ts` : nouvelle route top-level `{ path: 'printer-selection', canActivate: [authGuard], loadComponent: () => import('./features/auth/printer-selection/printer-selection.component').then(m => m.PrinterSelectionComponent) }`, positionnée comme `change-password` (hors `AppLayoutComponent`)
  - [x] `core/guards/auth.guard.ts` : après la vérification `forcePasswordChange` existante, ajouter — si `auth.currentUser()?.role === 'VOLUNTEER'` et `!auth.printerSelectionDone()` et `route.routeConfig?.path !== 'printer-selection'` → `router.createUrlTree(['/printer-selection'])`
  - [x] `services/auth.service.ts` : nouveau signal `readonly printerSelectionDone = signal(true)` (valeur par défaut `true` — ne bloque jamais ADMIN ni les rôles non concernés). Dans `login()` : si `user.role === 'VOLUNTEER'`, appeler `print.service.getSelectionStatus()` et positionner le signal à `status.done` avant de retourner (`await`, même pattern que l'appel `translateService.use()` déjà présent) ; sinon laisser à `true`. Même logique dans `restoreSession()`. Ajouter une méthode `markPrinterSelectionDone(): void` appelée par `PrinterSelectionComponent` après un `submitSelection()` réussi, pour positionner immédiatement le signal à `true` sans dépendre d'un aller-retour réseau supplémentaire
- [x] i18n (FR + EN, clé racine `auth.printerSelection`)
  - [x] `public/i18n/fr.json` et `.../en.json` : titre, description, labels des deux menus, option "Aucune", avertissement "aucune imprimante disponible", bouton de validation — voir Dev Notes § Ton (EXPERIENCE.md)
- [x] Aucune migration Liquibase — état 100% en session, rien de persisté (voir Dev Notes § Pourquoi pas de migration)

### Review Findings

- [x] [Review][Decision] Restriction VOLUNTEER-only sur les 3 endpoints `/printers/*` — `epics.md` ne mentionne que « le bénévole » et `auth.guard.ts` ne redirige que ce rôle, conforme à la Dev Notes § Décision de scope. Mais `EXPERIENCE.md` ligne 151 montre qu'un Admin déclenche aussi des impressions ailleurs (réimpression bilan de reversement) — aucun mécanisme équivalent n'existe pour lui. **Résolu avec l'utilisateur 2026-07-07 : restriction VOLUNTEER-only conservée**, conforme au texte littéral de l'epic ; la question du routage d'impression de l'Admin reste explicitement reportée aux Stories 3.6/5.x, aucun code à changer ici. [pluribourse-backend/src/main/java/org/pluribourse/print/controller/PrinterSelectionController.java:23]
- [x] [Review][Decision] Placement frontend `features/auth/printer-selection/` au lieu de `shared/components/` indiqué par `architecture.md` ligne 714 — suit la convention réelle du repo pour les écrans plein-cadre hors layout (login, change-password), mais l'utilisateur objecte à raison que la sélection d'imprimante n'a aucun rapport avec l'authentification (contrairement à login/change-password qui gèrent des identifiants). **Résolu avec l'utilisateur 2026-07-07 : nouveau dossier de premier niveau `features/setup/printer-selection/`**, dédié aux écrans de configuration post-connexion hors AppLayoutComponent, distinct à la fois de `auth/` (identifiants) et de `shared/components/` (widgets réutilisables intra-page). Voir patch de déplacement ci-dessous. [pluribourse-frontend/src/app/features/auth/printer-selection/printer-selection.component.ts]
- [x] [Review][Patch] `AuthService.login()`/`restoreSession()` : le nouvel appel `getSelectionStatus()` pour un VOLUNTEER n'est pas isolé — dans `login()`, aucun try/catch ne l'entoure, donc un échec du service d'impression fait rejeter `login()` alors que l'authentification a réussi ; dans `restoreSession()`, il tombe dans le catch prévu pour le cas 403 « mot de passe à changer », qui masque silencieusement toute autre erreur (500, réseau) comme si la session restait valide. [pluribourse-frontend/src/app/services/auth.service.ts:56-61,95-102] — **Corrigé** : extraction d'une méthode privée `refreshPrinterSelectionStatus()` avec son propre try/catch, repli sur `printerSelectionDone=false` en cas d'échec (pire cas : le bénévole revoit l'écran de sélection, sans faire échouer `login()` ni contaminer le catch 403 de `restoreSession()`).
- [x] [Review][Patch] `PrinterSelectionComponent` : ni `ngOnInit()` (`getAvailablePrinters()`) ni `onSubmit()` (`submitSelection()`) n'ont de gestion d'erreur — un échec réseau au chargement rend la liste vide indiscernable d'un « aucune imprimante enregistrée », et un échec de soumission (ex. 422 si l'imprimante devient indisponible entre l'affichage et le clic) réinitialise silencieusement `loading` sans aucun retour utilisateur ; aucune clé i18n d'erreur n'existe dans `fr.json`/`en.json` pour ce cas. [pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.ts:36-52] — **Corrigé** : signal `error` + try/catch sur les deux appels, notification inline `auth.printerSelection.error` (nouvelle clé fr/en) affichée quand `error()` est vrai.
- [x] [Review][Patch] `AuthService.clearSession()` (appelée au logout) ne réinitialise jamais `printerSelectionDone` à sa valeur par défaut `true` — si le `getSelectionStatus()` du login suivant échoue avant de positionner le signal (voir finding ci-dessus), la valeur du bénévole précédent fuite vers la nouvelle session. [pluribourse-frontend/src/app/services/auth.service.ts:32-34] — **Corrigé** : `clearSession()` positionne aussi `printerSelectionDone=true` ; `logout()` appelle désormais `clearSession()` au lieu de dupliquer `_currentUser.set(null)` (même gap, chemin non touché par cette story à l'origine).
- [x] [Review][Patch] `PrinterSelectionDto` n'a aucune annotation Bean Validation (`@NotNull`, etc.) — le `@Valid` du contrôleur sur ce DTO est donc inerte, toute la validation réelle est faite à la main dans le service. Pas un bug fonctionnel aujourd'hui, mais l'annotation est trompeuse en l'état. [pluribourse-backend/src/main/java/org/pluribourse/print/dto/PrinterSelectionDto.java] — **Corrigé** : `@Positive` sur les deux champs (Bean Validation traite `null` comme valide, donc l'optionnalité de l'AC6 est préservée ; seuls les ids ≤ 0 sont désormais rejetés en 400 avant d'atteindre le service).
- [x] [Review][Patch] Déplacer `printer-selection.component.ts/.html/.scss/.spec.ts` de `features/auth/printer-selection/` vers `features/setup/printer-selection/` (résolution de la Décision de placement ci-dessus) et mettre à jour `app.routes.ts` (chemin d'import) et `architecture.md` ligne 714 en conséquence. [pluribourse-frontend/src/app/features/auth/printer-selection/] — **Corrigé** : `git mv` des 4 fichiers vers `features/setup/printer-selection/`, import mis à jour dans `app.routes.ts`, `architecture.md` ligne 714 documente désormais `features/setup/`.
- [x] [Review][Defer] Écritures concurrentes possibles sur la même `HttpSession` dans `selectPrinters()` — deux `POST /printers/selection` interleavés pourraient produire un état incohérent (`done=true` avec des ids dépareillés) [pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterSelectionService.java:55-57] — deferred, pre-existing pattern de session non synchronisée, probabilité très faible (formulaire à soumission unique, bouton désactivé pendant `loading`)
- [x] [Review][Defer] Doublons de test fragiles pour simuler l'indisponibilité (port `1` en dur, `ServerSocket` jamais accepté) [pluribourse-backend/src/test/java/org/pluribourse/print/PrinterSelectionIT.java:233,290-292] — deferred, pre-existing, pattern déjà utilisé tel quel par `PrintInfrastructureIT` depuis la Story 3.4
- [x] [Review][Defer] Chaîne littérale `'VOLUNTEER'` dupliquée dans `auth.guard.ts`, `auth.service.ts` et les fichiers de specs plutôt qu'une constante/type partagé [pluribourse-frontend/src/app/core/guards/auth.guard.ts:493] — deferred, pre-existing, pattern déjà répété dans 16 fichiers du frontend avant cette story

## Dev Notes

### Décision de scope — restriction VOLUNTEER only (à confirmer en review)

`epics.md` (Story 3.9, lignes 1302-1333) ne mentionne que « le bénévole » dans le récit et les ACs — aucune mention d'un accès admin à cet écran. Pourtant, l'Admin déclenche aussi des impressions ailleurs dans le PRD (ex. `EXPERIENCE.md` ligne 151 : réimpression du récapitulatif de reversement accessible « Admin toutes phases »). Cette story **restreint `@PreAuthorize` à `hasRole('VOLUNTEER')`** sur toutes les nouvelles routes, strictement conforme au texte de l'epic. Comment l'Admin route ses propres impressions (récapitulatif, bilan) reste une question ouverte pour les Stories 3.6/5.x — **ne pas la résoudre ici**, signaler en review si l'utilisateur veut anticiper un mécanisme équivalent pour l'Admin.

### Critère de disponibilité — quel champ de `PrinterQueueHandle` utiliser

Une imprimante est « disponible » pour cette story si et seulement si : un handle existe (`PrintQueueService.getHandle(id) != null`, donc enregistrée et son thread consommateur tourne), `!handle.isSuspended()` (aucun job n'a échoué et bloqué sa file — Story 3.4 AC 5) **et** `handle.getLastError() == null` (le test d'accessibilité au démarrage/à l'enregistrement a réussi — Story 3.4 AC 2). Les deux conditions sont nécessaires : une imprimante inaccessible au démarrage a `lastError` renseigné mais `suspended` reste `false` (voir `PrintQueueService.createHandle()` — ne positionne jamais `suspended`, seul `PrinterQueueHandle.consume()` le fait après l'échec d'un job réel). Ignorer `lastError` laisserait apparaître comme disponible une imprimante jamais accessible.

### Pourquoi pas de migration Liquibase

Toute la donnée de cette story (sélection courante) vit exclusivement dans la session HTTP (Spring Session JDBC persiste déjà la session elle-même, donc survit à un redémarrage serveur — mais le **contenu métier** de la sélection n'est jamais un enregistrement `printers`/table dédiée). Cohérent avec la décision déjà actée en Story 3.4 § Statut runtime vs persistance : ne jamais persister un état recalculable/éphémère. Cette story ne touche donc **aucun fichier `db/changelog/`**.

### Pourquoi un flag serveur (`done`) et pas un simple signal client volatile

AC 1 dit littéralement que l'écran s'affiche « quand la connexion aboutit » — pas à chaque navigation. Un signal Angular remis à `false` par défaut à chaque rechargement de page (F5) re-déclencherait l'écran alors que la session serveur a déjà une sélection valide, ce qui contredirait l'expérience attendue (voir Flow narratif `EXPERIENCE.md` — aucun re-prompt mentionné en cours de session). D'où `GET /printers/selection` interrogé à la fois après `login()` et dans `restoreSession()`, pour que la vérité vienne du serveur, pas d'un état client qui ne survit pas à un F5.

### Placement frontend — écart avec `architecture.md` (à signaler en review)

`architecture.md` (ligne 714) place `printer-selection.component.ts` sous `shared/components/`. La codebase réelle place déjà les écrans plein-cadre hors `AppLayoutComponent` (login, changement de mot de passe forcé) sous `features/auth/` — pas sous `shared/`, qui est réservé aux composants réutilisés *dans* des pages (dialogs, notifications, toasts — voir `shared/components/` actuel). Cette story suit la convention réelle du repo (`features/auth/printer-selection/`) plutôt que l'arborescence indicative de `architecture.md`, cohérent avec des écarts similaires déjà actés en Story 3.4 (`LinkedBlockingDeque`) et 3.3.

### Portée AC 3/4 — sélection A4 également concernée, pas seulement thermique

L'AC de l'epic (« un bénévole déclenche une impression thermique... routé vers... l'imprimante thermique sélectionnée ») ne mentionne explicitement que le flux thermique. Mais l'écran expose bien deux listes (thermique **et** A4, cf. AC de l'épic ligne 1 : « deux listes déroulantes... une pour les thermiques, une pour les A4 »), et les bordereaux/bilans A4 (Stories 3.6, 5.x) auront le même besoin de routage. `PrinterSelectionService.getSelectedPrinterId(session, type)` est donc générique sur `PrinterType`, pas câblé uniquement pour THERMAL — décision d'implémentation cohérente avec l'écran lui-même, à confirmer en review si l'utilisateur préfère restreindre strictement au texte littéral de l'AC.

### Stratégie de test — construire ses propres imprimantes, jamais dans `test-data.sql`

Règle absolue héritée de la Story 3.4 : ne **jamais** ajouter d'imprimante dans `test-data.sql` (partagé par toutes les classes IT — déclencherait de vrais accès matériel au démarrage de chaque classe). `PrinterSelectionIT` doit construire ses propres imprimantes via `POST /admin/printers` (session admin), à l'identique du pattern `PrintInfrastructureIT` :
- Une imprimante A4 « disponible » : `host`/`port` pointant vers un `ServerSocket` de test local ouvert dans `@BeforeAll` (connectivité réussie → `lastError == null`).
- Une imprimante A4 « indisponible » : `host`/`port` pointant vers un port fermé/injoignable en local (`NetworkPrinterConnectivityChecker` échoue en ~2s max, pas de matériel requis) — sert à vérifier qu'elle **n'apparaît pas** dans `GET /printers/available` et qu'elle est rejetée par `POST /printers/selection` (422 `invalid-printer-selection`).
- **Ne pas** créer d'imprimante THERMAL réelle (gap de couverture déjà accepté en 3.4 § Stratégie de test point 4, jSerialComm non testable sans matériel série). Pour couvrir le cas « type incohérent » (ex. fournir l'id d'une imprimante A4 comme `thermalPrinterId`), utiliser l'imprimante A4 disponible créée ci-dessus — suffisant pour exercer la branche de validation de type sans dépendre du chemin THERMAL.
- Cas à couvrir : liste vide si aucune imprimante enregistrée (utiliser un moment du test avant toute création, ou une édition/contexte séparé) ; 404 sur id inconnu ; 422 sur type incohérent ; 422 sur imprimante indisponible ; 200 avec les deux ids `null` (AC 6) ; `GET /printers/selection` reflète l'état après `POST` ; persistance de la sélection à travers plusieurs requêtes sur la même `MockHttpSession` (pattern déjà utilisé par `ItemManagementIT`/`PrintInfrastructureIT`) ; 403 pour une session admin sur les 3 endpoints (volunteer-only, voir Dev Notes § Décision de scope).

### Ton UX (cohérence `EXPERIENCE.md`)

`EXPERIENCE.md` ligne 119 : « Actions : verbe + objet ». Utiliser des libellés dans cet esprit, ex. bouton "Continuer" ou "Accéder à l'application" (pas "Valider" seul, réservé à d'autres contextes du projet — laisser un choix éditorial au dev agent, cohérent avec le reste du vocabulaire déjà en place dans `fr.json`/`en.json`). Message d'avertissement liste vide sur le modèle de la ligne 188 (« L'imprimante [nom] ne répond pas. Vérifiez la connexion Bluetooth / réseau. ») — même registre direct et sans jargon.

### Project Structure Notes

- Aucun nouveau module top-level : tout dans `org.pluribourse.print` (déjà existant depuis 3.4) côté backend, et `features/auth/printer-selection/` + extension de `services/print.service.ts` (nouveau fichier) côté frontend.
- Fichiers backend à **lire avant modification** (contrat existant à respecter, ne pas casser) : `PrintQueueService.java` (`getHandle`, ne pas changer sa signature), `PrinterQueueHandle.java` (`isSuspended()`, `getLastError()`, tous deux déjà `@Getter` publics), `PrinterRepository.java` (actuellement vide d'méthodes custom — `findAll()` suffit, ne pas ajouter `findByType` sans besoin réel, le nombre d'imprimantes reste faible).
- Fichiers frontend à **lire avant modification** : `auth.service.ts` (étendre sans casser `CurrentUser` — le nouveau signal `printerSelectionDone` est séparé, ne pas l'ajouter à l'interface `CurrentUser` qui reflète strictement le JSON du backend), `auth.guard.ts` (ajouter la règle après celle de `forcePasswordChange`, sans la remplacer), `app.routes.ts` (ajouter la route en frère de `change-password`, ne pas la mettre sous les enfants de `AppLayoutComponent`).
- Prochain numéro de story après celle-ci selon l'ordre original de l'epic : `3-5-generation-impression-des-etiquettes-thermiques` (désormais débloquée par cette story).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.9, lignes 1302-1333] (ACs, FR-098)
- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.5, lignes 1190-1219] (dépendance qui motive le réordonnancement — AC « routé vers l'imprimante thermique sélectionnée » n'existe pas sans cette story)
- [Source: _bmad-output/planning-artifacts/architecture.md, ligne 714] (placement indicatif `printer-selection.component.ts`, écart documenté ci-dessus)
- [Source: _bmad-output/planning-artifacts/architecture.md, ligne 724] (`services/print.service.ts`, prévu mais pas encore créé)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md, lignes 119, 188] (ton UX, message imprimante hors ligne)
- [Source: _bmad-output/implementation-artifacts/3-4-infrastructure-dimpression-registre-dimprimantes-et-files-dynamiques.md] (infrastructure consommée : `PrintQueueService`, `PrinterQueueHandle`, conventions de test, règle « jamais d'imprimante dans test-data.sql »)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/print/service/PrintQueueService.java] (`getHandle`, `submit`)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterQueueHandle.java] (`isSuspended`, `getLastError`)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/print/service/NetworkPrinterConnectivityChecker.java] (timeout 2000ms, base du printer « indisponible » testable sans matériel)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/print/controller/PrinterController.java, PrinterService.java] (pattern controller/service à reproduire)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AuthController.java] (pattern lecture/écriture session via `HttpServletRequest`, `@PreAuthorize` par rôle)
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/LoginSuccessHandler.java] (confirme qu'aucune modification du flux de login lui-même n'est nécessaire — cette story reste isolée dans le module `print`)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/print/PrintInfrastructureIT.java] (pattern de test à reproduire : création d'imprimante via HTTP, `ServerSocket` local pour simuler A4 disponible)
- [Source: pluribourse-backend/src/test/java/org/pluribourse/item/ItemManagementIT.java] (pattern login multi-rôle en `@BeforeAll`, réutilisation de `MockHttpSession` entre méthodes ordonnées)
- [Source: pluribourse-backend/src/test/resources/db/changelog/test-data.sql] (fixtures partagées — jamais d'imprimante ici)
- [Source: pluribourse-frontend/src/app/features/auth/login/login.component.ts] (pattern redirection post-connexion par rôle)
- [Source: pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts] (pattern écran plein-cadre hors `AppLayoutComponent`, à reproduire pour `printer-selection`)
- [Source: pluribourse-frontend/src/app/services/auth.service.ts] (signal `currentUser`, pattern `login()`/`restoreSession()` à étendre)
- [Source: pluribourse-frontend/src/app/core/guards/auth.guard.ts] (pattern de garde à étendre — ordre des vérifications)
- [Source: pluribourse-frontend/src/app/app.routes.ts] (emplacement de la nouvelle route, cohérent avec `change-password`)
- [Source: pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts] (résolution de la page d'atterrissage par phase — inchangée, juste réutilisée après redirection)
- [Source: pluribourse-frontend/public/i18n/fr.json, lignes 19-34] (convention de clés `auth.*`, à étendre avec `auth.printerSelection.*`)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- `mvnw.cmd -q test -Dtest=PrinterSelectionIT` → 10/10 passed
- `mvnw.cmd test` (full backend suite) → 224/224 passed, BUILD SUCCESS, no regressions
- `npm test` (full frontend suite, Vitest) → 358/358 passed, 46/46 test files, no regressions
- Post-review : `mvnw.cmd -q test -Dtest=PrinterSelectionIT` → 11/11 passed (nouveau test `@Positive`)
- Post-review : `mvnw.cmd test` (full backend suite) → 225/225 passed, BUILD SUCCESS, no regressions
- Post-review : `npm test` (full frontend suite, Vitest) → 364/364 passed, 46/46 test files, no regressions

### Completion Notes List

- Implémenté le module `org.pluribourse.print` : DTOs (`AvailablePrinterDto`, `PrinterSelectionDto`, `PrinterSelectionStatusDto`), `InvalidPrinterSelectionException` (422), `PrinterSelectionService` (session-only, jamais persisté), `PrinterSelectionController` (`@PreAuthorize("hasRole('VOLUNTEER')")` sur les 3 endpoints, conforme à la Dev Notes § Décision de scope).
- `PrinterSelectionService.getSelectedPrinterId(session, type)` expose le contrat public consommé par les futures Stories 3.5/3.6, générique sur `PrinterType` (couvre THERMAL et A4, cf. Dev Notes § Portée AC 3/4).
- Critère de disponibilité implémenté exactement comme spécifié : `handle != null && !handle.isSuspended() && handle.getLastError() == null`.
- `PrinterSelectionIT` (10 tests) construit ses propres imprimantes A4 (une disponible via `ServerSocket` local, une indisponible via port fermé) — aucune imprimante ajoutée à `test-data.sql`. Couvre : liste vide avant tout enregistrement, liste filtrée aux seules imprimantes disponibles, 404 sur id inconnu, 422 type incohérent (imprimante A4 fournie comme `thermalPrinterId`), 422 imprimante indisponible, 200 avec sélection valide, `GET /printers/selection` reflète l'état après `POST` sur la même session, 200 avec les deux ids `null` (AC 6, session distincte), 403 pour une session admin sur les 3 endpoints.
- Frontend : `print.service.ts`, `printer-selection.component.ts/.html/.scss` (pattern `change-password.component.ts`, écran plein cadre hors `AppLayoutComponent`, sous `features/auth/` — écart avec `architecture.md` déjà documenté et accepté en Dev Notes), route top-level `printer-selection` dans `app.routes.ts`, garde ajoutée dans `auth.guard.ts` après la vérification `forcePasswordChange`, signal `printerSelectionDone` + méthode `markPrinterSelectionDone()` dans `auth.service.ts`, appelé depuis `login()`/`restoreSession()` pour les VOLUNTEER uniquement.
- Bouton de validation soumet toujours (aucun `Validators` sur les deux `FormControl`), conforme à l'AC 6 — seul `[disabled]="loading()"` empêche une double soumission pendant l'appel réseau.
- i18n : clés `auth.printerSelection.*` ajoutées dans `fr.json`/`en.json`, ton aligné sur `EXPERIENCE.md` (bouton "Accéder à l'application" / "Access the application", pas "Valider").
- Deux tests unitaires ajoutés/étendus en dehors du périmètre backend/frontend strict de cette story ont nécessité une correction de timing (microtask) dans `auth.service.spec.ts` pour les nouveaux scénarios `login()`/`restoreSession()` en tant que VOLUNTEER (deux appels HTTP séquentiels côté service) — sans quoi une requête HTTP non consommée fuitait vers le test suivant. Comportement de production non affecté, seul l'ordonnancement du test a été ajusté.
- Décision de scope VOLUNTEER-only : **confirmée en review 2026-07-07**, aucun changement. Écart de placement frontend : **résolu en review 2026-07-07** — déplacé vers `features/setup/printer-selection/` (ni `auth/`, ni `shared/components/`), voir § Review Findings ci-dessus.

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/print/dto/AvailablePrinterDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/dto/PrinterSelectionDto.java` (nouveau ; annotations `@Positive` ajoutées en review)
- `pluribourse-backend/src/main/java/org/pluribourse/print/dto/PrinterSelectionStatusDto.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/exception/InvalidPrinterSelectionException.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/service/PrinterSelectionService.java` (nouveau)
- `pluribourse-backend/src/main/java/org/pluribourse/print/controller/PrinterSelectionController.java` (nouveau)
- `pluribourse-backend/src/test/java/org/pluribourse/print/PrinterSelectionIT.java` (nouveau ; test de validation `@Positive` ajouté en review)
- `pluribourse-frontend/src/app/models/printer.model.ts` (nouveau)
- `pluribourse-frontend/src/app/services/print.service.ts` (nouveau)
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.ts` (nouveau, déplacé depuis `features/auth/` en review ; gestion d'erreur ajoutée)
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.html` (nouveau, déplacé depuis `features/auth/` en review)
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.scss` (nouveau, déplacé depuis `features/auth/` en review)
- `pluribourse-frontend/src/app/features/setup/printer-selection/printer-selection.component.spec.ts` (nouveau, déplacé depuis `features/auth/` en review ; tests de gestion d'erreur ajoutés)
- `pluribourse-frontend/src/app/app.routes.ts` (modifié — route `printer-selection`)
- `pluribourse-frontend/src/app/core/guards/auth.guard.ts` (modifié — redirection VOLUNTEER si sélection non faite)
- `pluribourse-frontend/src/app/core/guards/auth.guard.spec.ts` (modifié — nouveaux tests de garde)
- `pluribourse-frontend/src/app/services/auth.service.ts` (modifié — signal `printerSelectionDone`, `markPrinterSelectionDone()`, intégration dans `login()`/`restoreSession()`)
- `pluribourse-frontend/src/app/services/auth.service.spec.ts` (modifié — nouveaux tests `printerSelectionDone`)
- `pluribourse-frontend/public/i18n/fr.json` (modifié — clés `auth.printerSelection.*`)
- `pluribourse-frontend/public/i18n/en.json` (modifié — clés `auth.printerSelection.*`)

## Change Log

- 2026-07-07 : Implémentation complète de la Story 3.9 (écran de sélection d'imprimante par le bénévole à la connexion, état 100% en session). 224/224 tests backend et 358/358 tests frontend passent, aucune régression.
- 2026-07-07 : Revue de code. Décisions confirmées avec l'utilisateur : restriction VOLUNTEER-only conservée ; composant déplacé de `features/auth/` vers `features/setup/printer-selection/` (architecture.md mis à jour). 5 patches appliqués : isolation de l'appel `getSelectionStatus()` dans `login()`/`restoreSession()` (échec ne bloque plus l'authentification), gestion d'erreur dans `PrinterSelectionComponent` (signal `error` + clé i18n), reset de `printerSelectionDone` dans `clearSession()`/`logout()`, `@Positive` sur `PrinterSelectionDto`, déplacement du composant. 3 items différés (voir `deferred-work.md`). 225/225 tests backend et 364/364 tests frontend passent après application des patches, aucune régression. Statut → `done`.
