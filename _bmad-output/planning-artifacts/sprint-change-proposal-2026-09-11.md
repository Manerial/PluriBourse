# Sprint Change Proposal: Asynchronous printer connectivity check on creation

- **Date:** 2026-09-11
- **Author:** Manerial (via bmad-correct-course)
- **Skill:** bmad-correct-course
- **Mode:** Incremental
- **Status:** Approved by Manerial (2026-09-11)

---

## 1. Issue Summary

Adding a printer from the admin UI ("Ajouter une imprimante") is slow at the final "Ajouter" click, even though the discovery step (fetching the list of printers PrinterBridge already knows about) is comparatively fast and accepted as-is.

### Root cause

`POST /admin/printers` (`PrinterController.create()` → `PrinterService.create()`) calls `PrintQueueService.registerPrinter()` synchronously, inside the same HTTP request/response cycle. `registerPrinter()` → `createHandle()` (`PrintQueueService.java:128-140`) runs a **blocking** connectivity check against PrinterBridge (`PrinterBridgeClient.checkStatus()`, `CONNECT_TIMEOUT=2s` + `STATUS_READ_TIMEOUT=10s`, `PrinterBridgeClient.java:32-33`) before the request returns — up to ~12s in the worst case.

This check is redundant: the admin already saw this printer's status a moment earlier, in the response of `GET /admin/printers/discovered` (`discover()`), which the picker's dialog is built from. `PrinterBridgeDiscoveredPrinter` (`PrinterBridgeClient.java`) documents that `GET /printers` and `GET /printers/{id}/status` on PrinterBridge return the identical shape — the status is already known, just never carried forward into `CreatePrinterDto`.

A second, related friction point: adding several printers in one admin session requires closing and reopening the "Ajouter une imprimante" dialog for each one, which re-runs `discover()` from scratch every time (`printer-list.component.ts:86-96,109`) — `dialogRef.close()` fires unconditionally on a successful create (`printer-form.component.ts:76`).

### Evidence

- `PrinterService.java:41-53` (`create()`) → `PrintQueueService.registerPrinter()` → `createHandle()` (`PrintQueueService.java:128-140`) → blocking check.
- `PrinterBridgeClient.java:32-33`: `CONNECT_TIMEOUT=2s`, `STATUS_READ_TIMEOUT=10s`.
- Cross-checked against the PrinterBridge source (`../PrinterBridge`, sibling repo): `BluetoothPrinterDiscovery.testConnectivity()` uses `PrinterLocks.forPrinter(id).tryLock()` — **non-blocking**, so a scheduled/periodic check can never collide with an in-flight print job (it just reports the last-known state instead of competing for the RFCOMM connection).
- `PrinterBridge/CLAUDE.md:147` explicitly anticipates "si PluriBourse sonde le statut régulièrement" (regular polling), already covered by a 5s cache on port/MAC resolution — validates a scheduler-based approach as an expected usage pattern, not an edge case.
- `NetworkPrinterDiscovery.discover()` (PrinterBridge) already computes a real status for A4/network printers cheaply (OS `PrinterIsAcceptingJobs` attribute); `BluetoothPrinterDiscovery.discover()` (list) always returns `UNKNOWN` for Bluetooth — only `findById()` (single-printer `/status`) does a real, non-free RFCOMM `port.openPort()/closePort()` check.

---

## 2. Impact Analysis

### Epic Impact

Epic 3 (Vendeurs & Articles / infrastructure d'impression) is fully delivered (`sprint-status.yaml:780-800`, stories 3.1→3.14 all `done`) and its goal remains fully met — no epic-level scope change. All other epics (4, 5, 6) are also fully delivered and have no dependency on the internals being changed here; `PrintQueueService.submit(printerId, job)` — the one contract other epics rely on — is explicitly untouched (frozen by Story 3.12's AC) and stays that way.

### Story Impact

- **Story 3.11** (Intégration PrinterBridge — connexion et statut): its AC currently mandates a live connectivity check "au démarrage du serveur **ou création**" — amended to keep the live check at startup only; creation now reuses the status already returned by `discover()`.
- **Story 3.7** (Vue admin de diagnostic des imprimantes): its Completion Notes record a dev-time decision to add a manual "Actualiser" (refresh-all) button — this capability is removed, superseded by the background scheduler + a per-printer refresh action.
- **Story 3.13** (Ignorer une imprimante détectée): gains a new AC describing that a successful "Enregistrer" no longer closes the dialog — the just-registered printer is removed from the local discovered-list instead, without a new `discover()` call.
- **New Story 3.15** created to carry the actual implementation (see Section 4).

### Artifact Conflicts

- **PRD** — FR-079 mentions startup-time verification and the diagnostic view, but says nothing about periodic re-verification or a targeted per-printer refresh — amended to add both.
- **Architecture** — "Infrastructure d'Impression" table doesn't mention any periodic/background connectivity mechanism — new row added. "Frontière PrinterBridge" section lists the PrinterBridge endpoints consumed but not who calls `GET /printers/{id}/status` or when — clarified (startup / scheduler / per-printer admin action; no longer creation).
- **UX specs** (`EXPERIENCE.md`, `DESIGN.md`) — checked, no wireframe/flow describes the "Ajouter une imprimante" dialog or the "Actualiser" button in enough detail to conflict. No change needed, consistent with the documentation-drift convention already accepted on this module (see Story 3.7 Dev Notes).

### Technical Impact

- Backend: split `PrintQueueService.registerPrinter()`/`createHandle()` into two paths (live check at startup reload vs. seeded-from-known-status at creation); new `@Scheduled` background task reusing `@EnableScheduling` (introduced by Story 4.9's `BasketReaperService`, same `fixedDelay` + `@ConditionalOnProperty` pattern); new single-printer refresh capability + endpoint; removal of `PrintQueueController.refreshStatuses()` (`POST /admin/print-queue/refresh`), `PrintQueueDiagnosticsService.refreshStatuses()`, and the 3 `PrintQueueDiagnosticsIT` tests that exclusively exercise that endpoint.
- Frontend: `printer-form.component.ts` (local list removal instead of dialog close on success), `printer-list.component.html`/`.ts` (new per-printer refresh button, "Actualiser" button removed), `print-queue-list.component.html`/`.ts` (its "Actualiser" button + `refresh()` removed), `print-queue.service.ts` (`refreshStatuses()` removed).
- New configuration property for the scheduler interval (model: `pos.basket.reaper.interval`).
- `docs/workflow-ajout-imprimante.md` (published earlier in this same session) documents the **pre-change** flow — needs regenerating once Story 3.15 ships.
- No data model change (connectivity state stays purely in-memory, per the existing invariant documented on `PrinterQueueHandle`).

---

## 3. Recommended Approach

**Direct Adjustment** (Option 1): amend the ACs/Dev Notes of Stories 3.7, 3.11, 3.13, and add one new, narrowly-scoped follow-up story (3.15) to carry the implementation — the same pattern already used repeatedly on this project (SCP 2026-07-27, 2026-07-28, 2026-09-03, 2026-09-04: existing-story amendment + a small follow-up story).

- **Rollback** (Option 2) is not viable/applicable: Stories 3.4-3.13 are live, actively-used infrastructure, not a failed approach to undo.
- **MVP review** (Option 3) is not viable/applicable: no PRD goal (G1-G5) or MVP scope is put at risk; this is a quality/perf refinement inside already-delivered scope.

**Effort:** Medium (spans backend + frontend, several files, but every mechanism reused is an already-established pattern in this codebase — `BasketReaperService` for the scheduler, `ignoreRow()` for the local list-removal pattern).
**Risk:** Low (no new infrastructure, no data migration, the print module is already well-isolated and well-tested; PrinterBridge-side safety for concurrent checks during an active print job is verified from source, not assumed).

---

## 4. Detailed Change Proposals

### 4.1 — Epics (`_bmad-output/planning-artifacts/epics.md`)

**Story 3.11 — AC amendment (lines 1412-1414)**

```
OLD:
**Étant donné** qu'une imprimante enregistrée doit être vérifiée (démarrage du serveur ou création)
**Quand** la vérification de connectivité s'exécute
**Alors** elle interroge le statut PrinterBridge de l'imprimante plutôt que d'ouvrir une socket/port série ; une erreur PrinterBridge injoignable est distinguée d'une imprimante spécifiquement signalée hors ligne

NEW:
**Étant donné** qu'une imprimante enregistrée doit être vérifiée au démarrage du serveur
**Quand** la vérification de connectivité s'exécute
**Alors** elle interroge le statut PrinterBridge de l'imprimante plutôt que d'ouvrir une socket/port série ; une erreur PrinterBridge injoignable est distinguée d'une imprimante spécifiquement signalée hors ligne

**Étant donné** que l'admin enregistre une nouvelle imprimante depuis une imprimante détectée par `discover()`
**Quand** la création est soumise
**Alors** le statut déjà renvoyé par `discover()` (réseau : statut réel ; Bluetooth : `UNKNOWN`, PrinterBridge ne le teste jamais à ce niveau) est utilisé directement pour initialiser l'état de connectivité de l'imprimante — aucun nouvel appel à PrinterBridge n'est déclenché à la création
*(Amendé — voir sprint-change-proposal-2026-09-11.md)*
```

**Story 3.13 — new AC (after line 1460)**

```
NEW:
**Étant donné** que l'admin vient d'enregistrer une imprimante depuis le dialog "Ajouter une imprimante"
**Quand** l'enregistrement réussit
**Alors** le dialog reste ouvert sur la liste des imprimantes détectées, l'imprimante qui vient d'être enregistrée est retirée de cette liste (sans nouvel appel à `discover()`) — l'admin peut enchaîner l'enregistrement d'une autre imprimante détectée sans rouvrir le dialog
**Et** le dialog ne se ferme que sur "Annuler", ou automatiquement si la liste des imprimantes détectées devient vide après un enregistrement
*(Ajouté — voir sprint-change-proposal-2026-09-11.md)*
```

**New Story 3.15 — implementation** (added after Story 3.14, before the Epic 3 closing `---`)

```
### Story 3.15 : Vérification de connectivité asynchrone et rafraîchissement ciblé

> **Story ajoutée après coup (2026-09-11)** — implémente le correctif issu du correct-course sur les Stories 3.7, 3.11, 3.13 (voir `sprint-change-proposal-2026-09-11.md`) : suppression du check de connectivité bloquant à la création d'une imprimante, vérification périodique en tâche de fond, rafraîchissement manuel ciblé par imprimante.

En tant qu'administrateur,
je veux que l'ajout d'une imprimante soit immédiat et que la connectivité reste à jour sans action bloquante de ma part,
afin de pouvoir enregistrer plusieurs imprimantes rapidement et diagnostiquer une imprimante précise sans attendre.

**Critères d'acceptation :**

**Étant donné** que l'admin enregistre une imprimante détectée par `discover()`
**Quand** la création est soumise
**Alors** elle est persistée et son état de connectivité initialisé immédiatement à partir du statut déjà connu de `discover()`, sans nouvel appel à PrinterBridge (Story 3.11 amendée)

**Étant donné** que le serveur est démarré
**Quand** le délai configuré (`printer.connectivity.refresh.interval`, modèle `pos.basket.reaper.interval`) s'écoule
**Alors** toutes les imprimantes enregistrées non suspendues sont revérifiées auprès de PrinterBridge en tâche de fond, sans action de l'admin (FR-079 amendé)

**Étant donné** qu'un job d'impression est en cours sur une imprimante au moment où le scheduler l'atteint
**Quand** la vérification de connectivité s'exécute
**Alors** aucune interférence n'est introduite — délégué à PrinterBridge (`PrinterLocks.tryLock()` non bloquant côté PrinterBridge)

**Étant donné** que l'admin consulte `/admin/printers`
**Quand** il déclenche le rafraîchissement d'une imprimante précise
**Alors** seule cette imprimante est revérifiée en direct auprès de PrinterBridge, sans affecter les autres

**Étant donné** les boutons "Actualiser" globaux existants sur `/admin/printers` et `/admin/print-queue`
**Quand** cette story est livrée
**Alors** ils sont retirés, ainsi que l'endpoint `POST /admin/print-queue/refresh` et les tests qui l'exercent exclusivement

**Étant donné** le dialog "Ajouter une imprimante"
**Quand** un enregistrement réussit
**Alors** le comportement suit l'AC amendée de la Story 3.13 (retrait local de la liste détectée, pas de fermeture du dialog)
```

**Story 3.7 — Completion Notes amendment** (`_bmad-output/implementation-artifacts/3-7-vue-admin-de-diagnostic-des-imprimantes.md`, line 151)

```
OLD:
- Implémentation strictement conforme au périmètre des ACs de l'épic : pas de SSE, pas d'historique de jobs individuels (voir Dev Notes § Périmètre : temps réel) — rafraîchissement manuel via bouton "Actualiser" uniquement. **Signalé en review** : si la richesse complète décrite par `EXPERIENCE.md` (SSE, historique de jobs) est souhaitée, proposer une story dédiée plutôt que l'ajouter ici — non tranché, resté hors scope.

NEW:
- Implémentation strictement conforme au périmètre des ACs de l'épic : pas de SSE, pas d'historique de jobs individuels (voir Dev Notes § Périmètre : temps réel). **Signalé en review** : si la richesse complète décrite par `EXPERIENCE.md` (SSE, historique de jobs) est souhaitée, proposer une story dédiée plutôt que l'ajouter ici — non tranché, resté hors scope.
- **Amendé — voir sprint-change-proposal-2026-09-11.md** : le bouton "Actualiser" (rafraîchissement live de toutes les imprimantes à la demande) est retiré de `/admin/print-queue`. La connectivité est désormais maintenue à jour par un scheduler de fond (Story 3.15) ; `GET /admin/print-queue` continue d'afficher l'état déjà connu en mémoire (`listStatuses()`, inchangé), sans déclencher de nouveau check live.
```

### 4.2 — PRD (`_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md`, line 334)

```
OLD:
| FR-079 | En cas d'erreur d'impression (imprimante hors ligne, bourrage papier, manque de papier), l'utilisateur est notifié dans l'interface avec un message explicite indiquant la cause de l'erreur. La file de l'imprimante concernée est suspendue ; les autres files ne sont pas affectées. L'utilisateur peut relancer le job en erreur ou l'ignorer pour reprendre la file. L'admin dispose d'une vue de diagnostic affichant par imprimante enregistrée : profondeur de file, statut du thread consommateur, dernière erreur. Au démarrage du serveur, la connectivité de chaque imprimante enregistrée est vérifiée via un appel au statut PrinterBridge (plutôt qu'un test direct de port/adresse) ; toute imprimante inaccessible est signalée par une alerte dans le tableau de bord admin, avec une distinction entre PrinterBridge lui-même injoignable et une imprimante spécifique signalée hors ligne. |

NEW:
| FR-079 | En cas d'erreur d'impression (imprimante hors ligne, bourrage papier, manque de papier), l'utilisateur est notifié dans l'interface avec un message explicite indiquant la cause de l'erreur. La file de l'imprimante concernée est suspendue ; les autres files ne sont pas affectées. L'utilisateur peut relancer le job en erreur ou l'ignorer pour reprendre la file. L'admin dispose d'une vue de diagnostic affichant par imprimante enregistrée : profondeur de file, statut du thread consommateur, dernière erreur. Au démarrage du serveur, la connectivité de chaque imprimante enregistrée est vérifiée via un appel au statut PrinterBridge (plutôt qu'un test direct de port/adresse) ; toute imprimante inaccessible est signalée par une alerte dans le tableau de bord admin, avec une distinction entre PrinterBridge lui-même injoignable et une imprimante spécifique signalée hors ligne. **Entre deux démarrages, la connectivité de chaque imprimante enregistrée est réévaluée à intervalle régulier configurable par une tâche de fond côté serveur, sans action de l'admin. L'admin peut en complément forcer une réévaluation immédiate pour une imprimante donnée, individuellement, depuis `/admin/printers`.** *(Amendé — voir sprint-change-proposal-2026-09-11.md.)* |
```

### 4.3 — Architecture (`_bmad-output/planning-artifacts/architecture.md`)

**"Infrastructure d'Impression" table (lines 259-267) — new row**

```
NEW row appended to the table:
| **Vérification périodique de connectivité** | **`@Scheduled` (réutilise `@EnableScheduling`, introduit Story 4.9) sur toutes les imprimantes enregistrées non suspendues, intervalle configurable (`fixedDelay`, propriété dédiée)** | **Maintient l'état de connectivité à jour sans check bloquant dans le cycle requête/réponse HTTP (FR-079). Sans danger vis-à-vis d'un job en cours : `PrinterLocks.tryLock()` côté PrinterBridge est non bloquant (voir Frontière PrinterBridge). *(Ajouté — SCP 2026-09-11)*** |
```

**"Frontière PrinterBridge" section (lines 272-274) — addition**

```
NEW paragraph appended:
**`GET /printers/{id}/status` est appelé à trois moments distincts : au démarrage du serveur (toutes les imprimantes enregistrées), par la tâche de fond planifiée (toutes les imprimantes enregistrées non suspendues, voir Infrastructure d'Impression), et à la demande de l'admin pour une imprimante précise (`/admin/printers`). Il n'est plus appelé à la création d'une imprimante — le statut déjà renvoyé par `GET /printers` (découverte) est réutilisé directement. *(Amendé — SCP 2026-09-11)***
```

---

## 5. Implementation Handoff

**Scope classification: Moderate** — one new story added to the backlog (3.15) alongside amendments to three existing, already-delivered stories; no epic-level restructuring, no PRD/MVP renegotiation.

**Routed to:** Product Owner / Developer agent.

**Responsibilities:**
- Run `bmad-create-story` for Story 3.15 to produce its full dev-context story file (this proposal only sketches the story's shape/ACs, not implementation-ready dev notes).
- `bmad-dev-story` implements 3.15, which must also apply, in the same pass: the removal of `PrintQueueController.refreshStatuses()` + its 3 `PrintQueueDiagnosticsIT` tests; the split of `PrintQueueService.registerPrinter()`/`createHandle()`; the new scheduler service + config property; the new per-printer refresh capability/endpoint; the frontend changes across `printer-form`, `printer-list`, `print-queue-list`, `print-queue.service.ts`.
- After 3.15 ships, regenerate `docs/workflow-ajout-imprimante.md` — it currently documents the pre-change flow.

**Success criteria:** `POST /admin/printers` returns without a live PrinterBridge round-trip; adding N printers from one `discover()` result requires no repeated dialog re-opening; `/admin/printers` connectivity stays fresh without a manual global refresh action; no regression on the existing print-queue diagnostic/resume/discard behavior (Story 3.7 ACs untouched otherwise).
