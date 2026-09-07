---
baseline_commit: caf5ab089f50877387af8a20b05a0d96171f2804
---

# Story 2.11 : Résilience de la connexion SSE derrière le reverse-proxy

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

En tant qu'utilisateur connecté (admin ou bénévole) qui laisse une page ouverte,
je veux que la connexion temps réel (SSE) survive à une période sans évènement et à une coupure de transport,
afin de ne pas être renvoyé vers l'écran de connexion au bout d'une minute alors que ma session est parfaitement valide.

## Contexte & origine

Issue de la **SCP 2026-09-07** (`_bmad-output/planning-artifacts/sprint-change-proposal-2026-09-07.md`, approuvée par Manerial le 2026-09-07). Bug **pré-existant depuis la Story 2.6** (« Notification de phase en temps réel via SSE »), révélé en test manuel après la livraison de la Story 4.9 — **sans aucun lien avec 4.9**.

### Le symptôme

Tout utilisateur (constaté sur compte admin) est **redirigé vers `/login` au bout d'~1 minute** sur n'importe quelle page authentifiée. La **session backend reste valide** (`SPRING_SESSION` : `MAX_INACTIVE_INTERVAL = 3600`, `EXPIRY_TIME` ≈ +56 min). C'est le frontend Angular qui navigue de lui-même vers l'écran de connexion.

### Cause racine (précise)

1. Toute page authentifiée ouvre une `EventSource` app-wide sur `GET /api/sse/events` (`AppLayoutComponent.ngOnInit` → `SseService.phaseChanges()`).
2. **Spring `SseEmitter` ne valide la réponse HTTP (ligne de statut + en-têtes) qu'au premier `emitter.send(...)`.** `SseEmitterRegistry.register()` renvoie un émetteur nu sans rien envoyer ; sans changement de phase, le backend n'écrit **aucun octet** sur ce socket — pas même les en-têtes. Aucun keepalive non plus.
3. Le reverse-proxy livré (`pluribourse-frontend/nginx.conf`, bloc `location /api/`) n'a **aucun réglage SSE**. nginx transmet la requête à l'upstream et attend ; `proxy_read_timeout` (défaut **60 s**) est le délai max entre deux lectures depuis l'upstream. L'upstream n'envoyant rien — pas même les en-têtes — pendant 60 s, nginx conclut « upstream mort » et renvoie un **`504 Gateway Timeout`** synthétique au navigateur (il le peut encore, n'ayant rien transmis au client).
4. L'`EventSource` — bloqué en `CONNECTING` pendant les 60 s — reçoit le `504` → selon la spec WHATWG il **échoue la connexion**, passe `readyState = CLOSED`, émet `error`, et **ne se reconnecte pas**.
5. `sse.service.ts` `onerror` : `if (source.readyState === EventSource.CLOSED) { auth.clearSession(); currentEditionService.currentEdition.set(null); router.navigate(['/login']); }` → redirection forcée.

> Le statut observé est un **`504`**, pas un flux `200` coupé en cours : si Spring avait flushé les en-têtes `200 text/event-stream` tout de suite, nginx les aurait relayés, l'`EventSource` serait passé `OPEN`, et la coupure à 60 s aurait été une fermeture en cours de flux — que l'`EventSource` natif *réessaie*, sans `504` ni `/login`. Un `504` = les en-têtes ne sont jamais sortis du backend.

### Pourquoi la branche `onerror` → logout existe

Ajoutée **délibérément en revue de la Story 2.6** (Review Findings item 1 + 2e passe) pour gérer l'**expiration de session** : `EventSource` **contourne les intercepteurs Angular** (`authInterceptor`), donc le service ne peut pas bénéficier de la gestion globale du 401. À l'époque c'était défendable : `SseEmitterRegistry` fermait chaque émetteur après chaque broadcast → l'`EventSource` natif se reconnectait toutes les quelques secondes, et un `CLOSED` **permanent** ne pouvait raisonnablement signifier que « session perdue ».

**Deux faits ont invalidé cette hypothèse :**
- La **Story 4.8** a changé `SseEmitterRegistry` pour **garder les émetteurs ouverts** entre deux évènements — l'hypothèse `onerror` n'a jamais été revisitée.
- Un timeout de reverse-proxy produit un `CLOSED` permanent **avec une session parfaitement valide**.

Le mécanisme assimile désormais « le transport a hoqueté » à « vous êtes déconnecté », toutes les 60 s.

### Ce que cette story livre

Trois volets porteurs (indépendants du proxy) + un volet de durcissement. **(a) + (b) + (c) corrigent le bug sans toucher `nginx.conf`.**

| Volet | Contenu |
|---|---|
| **(a) porteur — backend** | `SseEmitterRegistry.register()` envoie une **frame initiale immédiate** (commentaire SSE) → valide la réponse `200 text/event-stream`, l'`EventSource` passe `OPEN` tout de suite, plus de `504` sur connexion oisive. + en-tête `X-Accel-Buffering: no` sur la réponse SSE. |
| **(b) porteur — backend** | **Keepalive planifié** (`@Scheduled`, réutilise le `@EnableScheduling` de la Story 4.9) : un commentaire SSE sur chaque émetteur toutes les ~20 s, même retrait défensif que `broadcast()`. |
| **(c) porteur — frontend** | `sse.service.ts` : **plus jamais** `clearSession()` / `navigate(['/login'])` sur un `CLOSED` de transport. Reconnexion applicative (délai fixe + jitter). Après N échecs consécutifs, sonde `GET /api/auth/me` (qui passe, elle, par `authInterceptor`) : 401/403 → l'intercepteur global redirige ; 2xx → on continue de réessayer. Resynchro de l'état de phase à la (re)connexion. `currentEdition` remis à `null` seulement sur logout confirmé. |
| **(d) durcissement — infra** | `nginx.conf` : bloc `location /api/sse/` dédié (`proxy_buffering off`, `proxy_http_version 1.1`, `Connection ''`, `proxy_read_timeout` long). Hors chemin critique — améliore la livraison temps réel (UX-DR4) et la portabilité vers un autre reverse-proxy. |

### Ce qui est DÉJÀ fait (ne pas refaire)

Les amendements d'artefacts de planification de la SCP 2026-09-07 sont **déjà appliqués** dans l'arbre de travail (à committer par Manerial avant le dev) :

- `architecture.md` § Notification de Changement de Phase — 3 lignes corrigées (Mécanisme / Impl. Spring / Impl. Angular) + nouvelle ligne « Résilience de la connexion ».
- `epics.md` — ARCH-012 (l.178), objectif Epic 2 (×2), UX-DR4.
- `prds/prd-PluriBourse-2026-06-08/prd.md` — FR-066 (précision « coupure SSE ≠ déconnexion »).
- `deferred-work.md` — section SCP 2026-09-07 (backoff exponentiel, bannière « connexion perdue », multicast SSE → V2).
- `sprint-status.yaml` — entrée `2-11-…: backlog` + `last_updated`.

→ **Cette story n'a AUCUN amendement `architecture.md` / `epics.md` / `prd.md` à porter.** Elle implémente ce qui y est déjà décrit. Seul reste un point de *bookkeeping* : notes miroir dans les fichiers des Stories 2.6 et 4.8 (T7).

## Points de conception figés (SCP §5 — ne pas rediscuter)

| Sujet | Décision |
|---|---|
| Sophistication du backoff | **Délai fixe (~5 s) + jitter** pour cette story. Backoff exponentiel + jitter → **V2** (`deferred-work.md`). Raison : 1 RPi 4 / 2 Go, ~3-5 postes + quelques onglets admin ; le keepalive rend les coupures rares ; l'exponentiel n'a d'intérêt qu'à grande échelle. |
| Intervalle keepalive | **20 s** (`sse.keepalive.interval=PT20S`). < 60 s (défaut proxy courant) et confortablement < `proxy_read_timeout`. |
| `proxy_read_timeout` du bloc SSE nginx | **3600s**. Le timeout serveur de `SseEmitter` (`30 * 60 * 1000L` aujourd'hui dans `SseEmitterRegistry`) peut être laissé à 30 min ou monté à 1 h pour symétrie — au choix du dev. ⚠️ Le keepalive empêche un **intermédiaire** (proxy) de voir la connexion comme morte ; il n'empêche **pas** le timeout **absolu** de `SseEmitter` de fermer la connexion à échéance (voir Dev Notes « Timeout absolu de `SseEmitter` »). Les deux sont indépendants : keepalive = idle-timeout proxy, timeout `SseEmitter` = durée de vie max de la requête async. |
| Bannière « connexion temps réel perdue » | **Hors périmètre** → V2. La reconnexion est totalement silencieuse : aucune nouvelle chaîne i18n, aucune UX de retry. |
| Multicast SSE (une `EventSource` par abonné) | **Hors périmètre** → V2 (déjà consigné `deferred-work.md`). Cette story ajoute un léger trafic keepalive par connexion, ce qui renforce l'intérêt du multiplexage, sans le traiter. |
| Le keepalive touche-t-il la session ? | **Non.** C'est un commentaire SSE (`:keepalive`). Aucun effet sur `SPRING_SESSION`, aucun `send` de données. |
| Redirection `/login` | Réservée à un **401/403 confirmé** (via la sonde `/api/auth/me` → `authInterceptor`) ou à l'expiration d'inactivité 1 h (FR-066). **Jamais** sur une erreur de transport. |

---

## Acceptance Criteria

1. **Étant donné** un utilisateur authentifié qui ouvre n'importe quelle page, **quand** l'app s'abonne à `/api/sse/events`, **alors** l'`EventSource` atteint `OPEN` immédiatement (le backend envoie une frame initiale depuis `register()`) — pas d'attente en `CONNECTING`, **et** aucun `504` même sans évènement en cours.

2. **Étant donné** une connexion SSE ouverte et oisive, **quand** il s'écoule plus que le `proxy_read_timeout` de nginx sans aucun évènement de phase, **alors** la connexion reste ouverte (keepalive backend ~20 s), le navigateur reçoit des commentaires keepalive, **et** il n'y a ni `504` ni redirection.

3. **Étant donné** une connexion SSE qui tombe (erreur de transport, `readyState === CLOSED`), **quand** `onerror` se déclenche, **alors** `SseService` ferme la source et planifie une reconnexion (~5 s + jitter) ; il **n'appelle pas** `clearSession()` et **ne navigue pas** vers `/login`.

4. **Étant donné** une connexion SSE en échec **N** fois consécutives (N = 2) sans `open` réussi entre-temps, **quand** le compteur atteint exactement N, **alors** `SseService` émet **une seule** sonde `GET /api/auth/me` ; un `401`/`403` laisse `authInterceptor` rediriger vers `/login` ; un `2xx` **remet `consecutiveFailures` à 0** — la reconnexion continue et la prochaine sonde ne partira qu'après N nouveaux échecs (pas une sonde par échec). Un `open` réussi remet lui aussi le compteur à 0.

5. **Étant donné** que la connexion du flux `phaseChanges` se ré-ouvre après une coupure, **quand** l'évènement `open` se déclenche, **alors** `CurrentEditionService.loadEdition()` est appelé pour resynchroniser le chip de phase (aucun rejeu des évènements manqués). Les flux `basketCancelled` / `settlementUpdated` ne déclenchent **pas** de resynchro (leurs composants gèrent déjà la fraîcheur).

6. **Étant donné** un onglet admin oisif (> 2 min, aucune interaction) et une édition avancée **PREPARATION → DEPOSIT** depuis un autre client, **quand** l'évènement `phase-changed` est délivré, **alors** le chip de phase se met à jour **sans** redirection vers `/login`.

7. **Étant donné** un redémarrage du backend (indisponible ~30 s), **quand** il revient, **alors** chaque flux SSE ouvert se reconnecte en quelques secondes et le chip de phase se resynchronise ; l'utilisateur **n'est pas** déconnecté.

8. **Étant donné** `pluribourse-frontend/nginx.conf`, **quand** il relaie `/api/sse/`, **alors** il utilise un bloc `location` dédié avec `proxy_buffering off`, `proxy_http_version 1.1`, `proxy_set_header Connection ''` et un `proxy_read_timeout` long ; **et** le backend pose `X-Accel-Buffering: no` sur la réponse SSE.

9. **Étant donné** les suites de tests frontend + backend, **quand** on les exécute, **alors** elles passent toutes (couverture ≥ 80 %) ; le test `sse.service.spec.ts` « CLOSED → clearSession + /login » est **remplacé** par des tests du nouveau comportement (pas de logout sur `CLOSED`, planification de reconnexion, sonde `/api/auth/me`).

---

## Tasks / Subtasks

- [x] **T1 — Backend : frame initiale + en-tête anti-buffering** (AC : 1, 8)
  - [x] T1.1 — Dans `SseEmitterRegistry.register()`, après `emitters.add(emitter)` et l'enregistrement des callbacks, envoyer une frame initiale : `emitter.send(SseEmitter.event().comment("ok"))`, entourée du **même** `try/catch (IOException | RuntimeException)` que `broadcast()` (client déjà parti entre la requête et cette ligne → `emitters.remove(emitter)` + `emitter.completeWithError(e)`).
  - [x] T1.2 — Dans `SseController.subscribe(...)`, injecter `HttpServletResponse response` et poser `response.setHeader("X-Accel-Buffering", "no");` avant `return registry.register();`.
  - [x] T1.3 — Vérifier que le `send()` pré-initialisation fonctionne en Spring Framework 7 (Spring bufferise les `send` avant que MVC ne câble l'émetteur et les rejoue à l'initialisation — patron documenté pour une frame « connected »). Si un `IllegalStateException` apparaît, replier sur un envoi depuis le contrôleur après `return` via le `TaskExecutor` MVC — **mais le chemin bufferisé est le comportement attendu**.

- [x] **T2 — Backend : keepalive planifié** (AC : 2)
  - [x] T2.1 — Ajouter à `SseEmitterRegistry` une méthode `@Scheduled(fixedDelayString = "${sse.keepalive.interval:PT20S}", initialDelayString = "${sse.keepalive.interval:PT20S}")` **`public void sendKeepalive()`** (`@Scheduled` exige une méthode non-`private` ; `public` comme `BasketReaperService.reapInactiveBaskets()`) qui itère `emitters` et envoie `SseEmitter.event().comment("keepalive")` à chacun, avec le **même** retrait défensif que `broadcast()` (`catch (IOException | RuntimeException)` → `emitters.remove` + `completeWithError`).
  - [x] T2.2 — Garde d'activation : `@Value("${sse.keepalive.enabled:true}") private boolean keepaliveEnabled;` + `if (!keepaliveEnabled) { return; }` en tête de `sendKeepalive()` (miroir de `pos.basket.reaper.enabled`, cf. Story 4.9). **Pas** de `@ConditionalOnProperty` sur la classe — `SseEmitterRegistry` est un bean cœur.
  - [x] T2.3 — `application.properties` : `sse.keepalive.interval=PT20S` + `sse.keepalive.enabled=true` (avec un commentaire sur la relation des durées : keepalive 20 s < 60 s défaut proxy < `proxy_read_timeout` 1 h).
  - [x] T2.4 — `src/test/resources/application.properties` : `sse.keepalive.enabled=false` + `sse.keepalive.interval=PT20S` (les 2 clés, pour que les placeholders `@Value`/`@Scheduled` résolvent toujours — miroir exact du bloc reaper juste au-dessus).
  - [x] T2.5 — `SchedulingConfig` (`@EnableScheduling`, Story 4.9) : **aucun changement** — il active déjà `@Scheduled` pour tout le contexte. Mettre à jour son JavaDoc de classe : elle n'est plus « le seul `@Scheduled` du projet » ⇒ mentionner le keepalive SSE (et le fait qu'avec `sse.keepalive.enabled=false` le keepalive ne s'exécute pas mais **le bean `@Scheduled` existe toujours** — la garde est dans la méthode, pas un `@ConditionalOnProperty`).

- [x] **T3 — Backend : tests** (AC : 2, 9)
  - [x] T3.1 — Nouveau test isolé de `SseEmitterRegistry.sendKeepalive()` : enregistrer un émetteur, appeler `sendKeepalive()`, asserter qu'un commentaire keepalive a bien été envoyé ; un émetteur qui lève `IOException` à l'envoi est retiré de `emitters`.
    - **Voie préférée — sans Mockito.** `SseEmitterRegistry` n'est pas « le client d'un système externe » au sens de l'exception `PrinterBridgeClient` (client HTTP/WebSocket sortant) — c'est de l'infra interne. Rester dans la règle CLAUDE.md avec une **sous-classe locale de test** de `SseEmitter` : une qui capture les `send(...)` dans une liste (assertion « commentaire envoyé »), une dont `send(...)` lève `IOException` (assertion « retiré de `emitters` » — via un `register()` puis vérification que l'émetteur n'est plus notifié au `sendKeepalive()` suivant, ou en exposant la taille du registre en `package-private` pour le test).
    - **Mockito** (`mock(SseEmitter.class)` + `verify`/`doThrow`) : **seulement** avec l'accord explicite de Manerial — l'assimiler à la frontière sortante `PrinterBridgeClient` est un raccourci contestable sur un bean cœur.
    - Le test s'ajoute à la couverture E2E, ne la remplace pas.
  - [x] T3.2 — La frame initiale (T1.1) ajoute un commentaire `:ok` **en tête** de chaque corps de réponse `/api/sse/events`. **Auditer toute assertion sur un corps SSE qui ne tolère pas un préfixe** (`.isEmpty()`, `.isBlank()`, `.hasSize(...)`, `.isEqualTo(...)`) et la corriger. IT concernées (les seules du `src/test` qui ouvrent `/api/sse/events`) :
    - **`PosBasketCancellationIT`** — SSE aux `@Order` 2, 3, 4 (`.contains(...)` / `.doesNotContain(...)` — **inchangées**) **et `@Order 6` `rejected_transition_broadcasts_no_event_at_all()` : `assertThat(sse.getResponse().getContentAsString()).isEmpty()` (≈ ligne 258) → CASSE.** La remplacer par `.doesNotContain("phase-changed").doesNotContain("basket-cancelled")` (l'intention du test = « aucun évènement métier diffusé », pas « zéro octet »).
    - **`SettlementSyncIT`** — SSE aux `@Order` 2, 3, 4 : `.contains(...)` / `.doesNotContain("settlement-updated")` — **inchangées** (`:ok` ne contient aucun de ces marqueurs).
    - **`PhaseTransitionIT`** — `@Order` 17/18/19 (statut + `Content-Type` uniquement) et `@Order 20` (`.contains("\"newPhase\":\"DEPOSIT\"")`…) — **inchangées**.
    - `CurrentEditionIT` **n'ouvre pas** de flux SSE — hors sujet.
  - [x] T3.2b — `sse.keepalive.enabled=false` en test (T2.4) ⇒ aucun commentaire keepalive ne vient polluer `getContentAsString()` ; seul le `:ok` de la frame initiale est présent, c'est bien ce que couvre l'audit ci-dessus.
  - [x] T3.3 — `mvn test` (backend) vert, couverture ≥ 80 %.

- [x] **T4 — Frontend : `sse.service.ts` — reconnexion applicative** (AC : 1, 3, 4, 5)
  - [x] T4.1 — Injecter `HttpClient` (`private readonly http = inject(HttpClient);`) — nécessaire pour la sonde. Garder `AuthService`, `Router`, `CurrentEditionService` injectés.
  - [x] T4.2 — Constantes en tête de fichier : `RECONNECT_BASE_MS = 4000`, `RECONNECT_JITTER_MS = 3000` (délai = `BASE + Math.random() * JITTER` → 4–7 s), `PROBE_AFTER_FAILURES = 2`.
  - [x] T4.3 — Refondre `private listen<T>(eventName, isValid, onReconnect?)` : la fabrique `Observable` gère désormais une **boucle de connexion** interne — `connect()` crée l'`EventSource`, un `open` handler remet `consecutiveFailures = 0` et appelle `onReconnect?.()`, un `onerror` handler ne fait rien tant que `readyState === CONNECTING` (retry natif en cours) et, sur `CLOSED` : `source.close()`, `source = null`, `consecutiveFailures++`, `if (consecutiveFailures === PROBE_AFTER_FAILURES) { this.probeSession(() => { consecutiveFailures = 0; }); }`, puis `scheduleReconnect()`. La comparaison est **`===`** (pas `>=`) : la sonde part **une seule fois** au franchissement du seuil ; un `2xx` remet le compteur à 0 (callback), donc la sonde suivante n'ira qu'après `PROBE_AFTER_FAILURES` nouveaux échecs — pas une sonde `/api/auth/me` par reconnexion ratée (AC 4).
  - [x] T4.4 — `scheduleReconnect()` : `setTimeout` avec le délai jitteré ; garde `if (disposed || reconnectTimer) return;` ; à l'échéance, `reconnectTimer = null; connect();`.
  - [x] T4.5 — Teardown de l'`Observable` : `disposed = true; if (reconnectTimer) clearTimeout(reconnectTimer); source?.close();` — **doit** annuler tout timer de reconnexion en attente (sinon fuite après `takeUntilDestroyed`).
  - [x] T4.6 — `private probeSession(onStillValid: () => void): void` : `this.http.get('/api/auth/me').subscribe({ next: () => onStillValid(), error: () => {} })`. Le `next` (session encore valide) exécute le callback qui remet `consecutiveFailures` à 0 (cf. T4.3, AC 4). Le `GET` passe par `authInterceptor` : un `401`/`403` déclenche `clearSession()` + `navigate(['/login'])` **dans l'intercepteur**, et la destruction de `AppLayoutComponent` qui s'ensuit tear-down l'`Observable` (donc stoppe la boucle). Optionnel (défensif) : sur `error` de la sonde, `disposed = true` + clear timer pour un arrêt dur immédiat.
  - [x] T4.7 — **Supprimer** de `onerror` les appels directs `this.auth.clearSession()`, `this.currentEditionService.currentEdition.set(null)`, `this.router.navigate(['/login'])`.
  - [x] T4.8 — `phaseChanges()` passe `onReconnect = () => this.currentEditionService.loadEdition().subscribe()`. `basketCancelled()` et `settlementUpdated()` **n'en passent pas** (AC 5).
  - [x] T4.9 — `currentEdition.set(null)` : ne subsiste **que** sur un chemin de logout confirmé — ici on le retire de `SseService` (l'`authInterceptor` + `restoreSession()`/`loadEdition()` du flux login le remettent déjà à `null` au bon moment). Retirer l'injection `CurrentEditionService` **seulement si** plus utilisée (elle l'est encore : `loadEdition()` pour la resynchro T4.8) — donc **garder** l'injection, retirer juste le `.currentEdition.set(null)`.

- [x] **T5 — Frontend : `sse.service.spec.ts`** (AC : 3, 4, 5, 9)
  - [x] T5.1 — Ajouter `provideHttpClient()` + `provideHttpClientTesting()` aux providers du `TestBed` ; récupérer `HttpTestingController`. **Restructurer `createMockEventSource`** : aujourd'hui le `ctor` renvoie un **singleton** `instance` — inutilisable pour tester une reconnexion (T5.3/T5.6 ont besoin d'une **nouvelle** `EventSource` par `new EventSource()`). Le transformer en fabrique qui, à chaque `new`, crée une instance fraîche (`addEventListener`/`close`/`readyState`/`onerror` propres), la pousse dans un tableau `instances[]` exposé, et permet de piloter l'évènement `open` sur la dernière instance (un `onopen` déclenchable **ou** capter le handler via `addEventListener.mock.calls.find(c => c[0] === 'open')`). Les tests existants (une seule instance) lisent alors `instances[0]`.
  - [x] T5.2 — Conserver le test « ne fait rien sur reconnect transitoire (`readyState` CONNECTING) ».
  - [x] T5.3 — **Remplacer** le test « clears the session and redirects to /login on permanent failure (readyState CLOSED) » par : sur `onerror` avec `readyState === CLOSED`, `mockClearSession` **non** appelé, `router.navigate` **non** appelé, `instance.close()` appelé, et (fake timers) une **nouvelle** `EventSource` créée après le délai de reconnexion.
  - [x] T5.4 — Nouveau test : après `PROBE_AFTER_FAILURES` `CLOSED` consécutifs, un `GET /api/auth/me` est émis (`httpTesting.expectOne('/api/auth/me')`). `flush({}, {status: 200})` → le compteur d'échecs repart, la reconnexion continue. (Le cas 401 est couvert par les tests existants de `authInterceptor` — ne pas le re-tester ici, juste vérifier que la requête part.)
  - [x] T5.5 — Nouveau test : sur l'évènement `open` de `phaseChanges()`, le `onReconnect` est invoqué — mocker `CurrentEditionService` avec `loadEdition: vi.fn().mockReturnValue(of(undefined))` et asserter l'appel. Vérifier que `basketCancelled()` / `settlementUpdated()` ne l'appellent pas.
  - [x] T5.6 — Nouveau test : au teardown (`subscription.unsubscribe()`) alors qu'un timer de reconnexion est en attente, le timer est annulé (aucune `EventSource` supplémentaire créée après un `vi.advanceTimersByTime`).
  - [x] T5.7 — `npm test` (dans `pluribourse-frontend/`) vert, couverture ≥ 80 %.

- [x] **T6 — Infra : `pluribourse-frontend/nginx.conf`** (AC : 8)
  - [x] T6.1 — Ajouter un bloc `location /api/sse/` **avant** `location /api/` :
    ```nginx
    location /api/sse/ {
        proxy_pass http://backend:8080/api/sse/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_http_version 1.1;
        proxy_set_header Connection '';
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        chunked_transfer_encoding off;
    }
    ```
  - [x] T6.2 — Ne **pas** modifier les autres blocs (`location /api/`, `/actuator/health`, `/login`, `/logout`, `location /`). nginx choisit le `location` préfixe le plus long → `/api/sse/` l'emporte sur `/api/` ; le placer au-dessus par lisibilité.
  - [ ] T6.3 — Rebuild de l'image `pluribourse-frontend` (le `nginx.conf` est copié dans l'image via son `Dockerfile`, ligne `COPY nginx.conf /etc/nginx/conf.d/default.conf`). Aucun changement `docker-compose.yml`.

- [x] **T7 — Bookkeeping : notes miroir dans les stories touchées** (AC : —)
  - [x] T7.1 — `2-6-notification-de-phase-en-temps-reel-via-sse.md` : ajouter une note en fin de Dev Notes / Review Findings — « `SseService.onerror` ne déconnecte plus sur `CLOSED` de transport (Story 2.11, SCP 2026-09-07) ; l'AC4 est désormais tenue aussi pour un échec *dur*, via une reconnexion applicative + une sonde `GET /api/auth/me` qui délègue le logout à `authInterceptor`. Le commentaire `onerror` de la 2.6 supposant "CLOSED ⇒ session perdue" est superseded. »
  - [x] T7.2 — `4-8-reservation-de-lot-au-scan-et-annulation-panier-a-la-deconnexion.md` : note miroir — « garder les émetteurs SSE ouverts entre deux évènements (changement de cette story) est la raison pour laquelle un `CLOSED` permanent ne peut plus être assimilé à "session expirée" ; le keepalive ajouté par la Story 2.11 en est la contrepartie. »
  - [x] T7.3 — **Rien** à modifier dans `epics.md` / `architecture.md` / `prd.md` : déjà fait par le correct-course SCP 2026-09-07 (cf. « Ce qui est DÉJÀ fait »).

- [ ] **T8 — Vérification manuelle (Manerial)** (AC : 1, 2, 6, 7)
  - [ ] T8.1 — Stack complète via `http://localhost` (les 3 conteneurs). Ouvrir la console dev (F12).
  - [ ] T8.2 — `GET /api/sse/events` atteint `readyState OPEN` immédiatement (onglet Network : réponse `200 text/event-stream` sans attente), reste ouvert **> 5 min** sans évènement de phase, aucun `504`.
  - [ ] T8.3 — Onglet admin oisif > 2 min : avancer l'édition **PREPARATION → DEPOSIT** depuis un autre navigateur/onglet → le chip de phase de l'onglet oisif se met à jour, **aucune** redirection `/login`.
  - [ ] T8.4 — `docker restart pluribourse-backend-1`, attendre ~30 s : l'onglet se reconnecte tout seul en quelques secondes, le chip se resynchronise, **pas** de déconnexion.
  - [ ] T8.5 — Se déconnecter réellement (bouton logout) ou laisser expirer la session 1 h → la redirection `/login` fonctionne toujours (via la sonde `/api/auth/me` → `authInterceptor`).

---

## Dev Notes

### État actuel des fichiers touchés (lire avant de coder)

**`pluribourse-backend/.../shared/sse/SseEmitterRegistry.java`** *(UPDATE)* — `@Component`. `CopyOnWriteArrayList<SseEmitter> emitters`. `register()` (`synchronized`) : `new SseEmitter(30 * 60 * 1000L)`, `emitters.add`, callbacks `onCompletion`/`onTimeout`/`onError` = `emitters.remove(emitter)`, `return emitter`. `broadcast(eventName, payload)` : boucle `for (SseEmitter emitter : emitters)` → `emitter.send(SseEmitter.event().name(eventName).data(payload))` ; `catch (IOException | RuntimeException e)` → `emitters.remove(emitter)` + `emitter.completeWithError(e)`.
- *Change* : + frame initiale dans `register()` (T1.1) ; + `@Scheduled sendKeepalive()` + garde `keepaliveEnabled` (T2).
- *Préserver* : la sémantique de `broadcast()`, le pattern de retrait défensif (le réutiliser tel quel), le timeout `30 min` du constructeur (sauf si le dev choisit de le monter à 1 h — cf. points figés), le `synchronized` sur `register()`.

**`pluribourse-backend/.../shared/sse/SseController.java`** *(UPDATE)* — `@RestController @RequestMapping("/sse")`. `@GetMapping("/events") public SseEmitter subscribe() { return registry.register(); }`.
- *Change* : + `HttpServletResponse` param + `response.setHeader("X-Accel-Buffering", "no")` (T1.2).
- *Préserver* : le mapping `/sse/events`, le retour direct de `registry.register()`.

**`pluribourse-backend/.../shared/config/SchedulingConfig.java`** *(UPDATE — JavaDoc uniquement)* — `@Configuration @EnableScheduling`, classe vide. JavaDoc actuel : « premier usage de `@EnableScheduling` du projet (story 4.9, `BasketReaperService`) … avec `pos.basket.reaper.enabled=false` le bean reaper est absent (`@ConditionalOnProperty`), il n'y a aucune autre méthode `@Scheduled` ».
- *Change* : JavaDoc — il y a maintenant **une deuxième** méthode `@Scheduled` (`SseEmitterRegistry.sendKeepalive`), qui, elle, n'a **pas** de `@ConditionalOnProperty` : le bean existe toujours, la garde `sse.keepalive.enabled` est **dans la méthode**. En test, `sse.keepalive.enabled=false` neutralise le corps mais le trigger `@Scheduled` reste enregistré (inoffensif : la méthode retourne immédiatement).

**`pluribourse-backend/src/main/resources/application.properties`** *(UPDATE)* — bloc reaper Story 4.9 en fin de fichier (`pos.basket.heartbeat.dead-threshold=PT3M`, `pos.basket.reaper.interval=PT2M`, `pos.basket.reaper.enabled=true`).
- *Change* : + `sse.keepalive.interval=PT20S`, + `sse.keepalive.enabled=true` avec commentaire de relation des durées.

**`pluribourse-backend/src/test/resources/application.properties`** *(UPDATE)* — bloc reaper : `pos.basket.reaper.enabled=false` + les 2 durées « pour que les placeholders `@Value`/`@Scheduled` résolvent toujours ».
- *Change* : + `sse.keepalive.enabled=false` + `sse.keepalive.interval=PT20S`, même justification, juste sous le bloc reaper.

**`pluribourse-frontend/src/app/services/sse.service.ts`** *(UPDATE — cœur de la story)* — `@Injectable({providedIn:'root'})`. Injecte `AuthService`, `Router`, `CurrentEditionService`. 3 méthodes publiques `phaseChanges()` / `basketCancelled()` / `settlementUpdated()` → `this.listen(eventName, isValidGuard)`. `private listen<T>(eventName, isValid)` : `new Observable(observer => { const source = new EventSource('/api/sse/events', {withCredentials:true}); source.addEventListener(eventName, msg => { try { const parsed = JSON.parse(msg.data); if (isValid(parsed)) observer.next(parsed); } catch {} }); source.onerror = () => { if (source.readyState === EventSource.CLOSED) { this.auth.clearSession(); this.currentEditionService.currentEdition.set(null); this.router.navigate(['/login']); } }; return () => source.close(); })`. Guards de type `isPhaseChangedEvent` / `isBasketCancelledEvent` / `isSettlementUpdatedEvent` + `isPhaseType` contre `ALL_PHASES` (ne pas toucher).
- *Change* : `listen` devient une **boucle de connexion** avec reconnexion + jitter + sonde (T4). `phaseChanges` passe un `onReconnect`. Suppression du triplet `clearSession`/`set(null)`/`navigate` dans `onerror`.
- *Préserver* : `{ withCredentials: true }`, la signature des 3 méthodes publiques, les guards de type et `isValid(parsed)` avant `observer.next`, le `close()` au teardown (désormais + `clearTimeout`), le fait que `EventSource` **contourne** `authInterceptor` (c'est **la** raison de la sonde `/api/auth/me`).

**`pluribourse-frontend/src/app/services/sse.service.spec.ts`** *(UPDATE)* — mock `EventSource` via `vi.stubGlobal`. `TestBed` providers : `provideRouter([])`, `{ provide: AuthService, useValue: { clearSession: mockClearSession } }`. Le mock `EventSource` (`createMockEventSource`) expose `addEventListener`, `close`, `onerror`, `readyState`, et un `ctor` `vi.fn` avec `CONNECTING/OPEN/CLOSED`. Derniers tests (l.170-194) : « CONNECTING → rien » (garder) et « CLOSED → clearSession + navigate » (**à remplacer**, T5.3).
- *Change* : + `provideHttpClient`/`provideHttpClientTesting` + `HttpTestingController` ; + `CurrentEditionService` mock avec `loadEdition` ; mock `EventSource` : + `onopen` déclenchable + compteur d'instances ; réécriture du test CLOSED + 3 nouveaux tests (T5).

**`pluribourse-frontend/nginx.conf`** *(UPDATE)* — 33 lignes. `server { listen 80; root …; location / { try_files … /index.html; } location /api/ { proxy_pass http://backend:8080/api/; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; } location = /actuator/health {…} location = /login {…} location = /logout {…} }`.
- *Change* : + bloc `location /api/sse/` avant `location /api/` (T6).
- *Préserver* : tous les autres blocs à l'identique.

### Frame initiale : pourquoi ça marche

`ResponseBodyEmitter` (parent de `SseEmitter`) bufferise les `send()` faits **avant** que `ResponseBodyEmitterReturnValueHandler` ne lui injecte son `Handler` (champ interne `earlySendAttempts`), puis les rejoue à l'initialisation. Appeler `emitter.send(SseEmitter.event().comment("ok"))` dans `register()` (avant le `return`) est donc sûr et **commit la réponse `200 text/event-stream`** dès que MVC câble l'émetteur → l'`EventSource` client passe `OPEN` sans attendre un évènement métier. C'est le patron standard de la frame « connected » SSE. (Si SF7 refusait un `send` pré-init — non attendu — voir T1.3 pour le repli.)

### Timeout absolu de `SseEmitter` (30 min) : reconnexion native attendue

`new SseEmitter(30 * 60 * 1000L)` est un timeout **absolu** sur la requête async — **pas** un idle-timeout. Même avec le keepalive, chaque connexion SSE est fermée par le serveur au bout de 30 min (ou 1 h si le dev monte la valeur) : `onTimeout` retire l'émetteur, la réponse se termine **proprement** (`200`, EOF). Le navigateur voit une fin de flux en cours de route → `readyState` repasse à `CONNECTING` → **reconnexion native de l'`EventSource`** (~3 s). C'est le cas « coupure en cours de flux » que l'`EventSource` natif réessaie tout seul : `onerror` se déclenche avec `readyState === CONNECTING`, notre handler ne fait **rien**, `connect()` n'est pas rappelé par notre boucle (c'est le navigateur qui rouvre). Pas de `504`, pas de logout, pas de sonde.

**Conséquence à connaître :** ne pas chercher à « corriger » ce reconnect de 30 min ; et lors de la vérif manuelle T8.2, une reconnexion observée au-delà de 30 min d'ouverture continue est **normale** (le chip se resynchronise via le `onReconnect` de `phaseChanges()`, cf. AC 5/7).

### Reconnexion frontend : forme cible (indicative)

```ts
private listen<T>(eventName: string, isValid: (v: unknown) => v is T, onReconnect?: () => void): Observable<T> {
  return new Observable(observer => {
    let source: EventSource | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let consecutiveFailures = 0;
    let disposed = false;

    const scheduleReconnect = () => {
      if (disposed || reconnectTimer) { return; }
      const delay = RECONNECT_BASE_MS + Math.random() * RECONNECT_JITTER_MS;
      reconnectTimer = setTimeout(() => { reconnectTimer = null; connect(); }, delay);
    };

    const connect = () => {
      source = new EventSource('/api/sse/events', { withCredentials: true });
      source.addEventListener('open', () => {
        consecutiveFailures = 0;
        onReconnect?.();
      });
      source.addEventListener(eventName, (event: MessageEvent) => {
        try {
          const parsed: unknown = JSON.parse(event.data);
          if (isValid(parsed)) { observer.next(parsed); }
        } catch {
          // malformed event — ignore
        }
      });
      source.onerror = () => {
        if (disposed || !source || source.readyState !== EventSource.CLOSED) {
          return; // CONNECTING → the browser is retrying natively; nothing to do
        }
        source.close();
        source = null;
        consecutiveFailures++;
        if (consecutiveFailures === PROBE_AFTER_FAILURES) {
          // Exactly once when the counter hits the threshold. A 2xx resets it, so the next
          // probe only goes out after PROBE_AFTER_FAILURES more failures — not one per retry.
          this.probeSession(() => { consecutiveFailures = 0; });
        }
        scheduleReconnect();
      };
    };

    connect();
    return () => {
      disposed = true;
      if (reconnectTimer) { clearTimeout(reconnectTimer); }
      source?.close();
    };
  });
}

private probeSession(onStillValid: () => void): void {
  // GET goes through authInterceptor: a 401/403 there triggers clearSession() + navigate(['/login']).
  // A 2xx means the session is still valid — reset the failure counter (AC 4).
  this.http.get('/api/auth/me').subscribe({ next: () => onStillValid(), error: () => {} });
}
```

`onReconnect` n'est câblé que par `phaseChanges()` : `() => this.currentEditionService.loadEdition().subscribe()`. Le `open` **initial** (première connexion) déclenche aussi `onReconnect` — bénin : `loadEdition()` est idempotent et déjà appelé par `AppLayoutComponent.ngOnInit` (garde de séquence monotone dans `CurrentEditionService`). Optionnel : un flag `firstConnect` dans la fabrique (skip `onReconnect?.()` au tout premier `open`) évite un `GET /api/editions/current` redondant à **chaque** chargement de page ; micro-optimisation, au choix du dev, sans impact sur les AC.

### Endpoint sonde `GET /api/auth/me`

Existe déjà (`AuthController`, utilisé par `AuthService.restoreSession()`). Renvoie `200 + CurrentUser` si session valide, `401` sinon (et `403 password-change-required` géré à part par `authInterceptor` → `/change-password`). C'est **le** endpoint léger idéal : il passe par `HttpClient` donc par `authInterceptor`, contrairement à l'`EventSource`.

### Contraintes projet applicables

- **Backend** : couches Contrôleur→Service→Repository ; `var` interdit, type explicite ; accolades obligatoires pour tout `if`/`for` (même corps 1 ligne) ; JavaDoc sur logique non triviale (`sendKeepalive`, la boucle `listen` côté front). Pas de donnée perso dans les logs (le keepalive ne logue rien ; en cas de `log` d'échec d'émetteur, compter uniquement).
- **Frontend** : composants/services standalone, Signals ; **jamais** de template inline (N/A ici, pas de composant) ; tout texte via ngx-translate (N/A — cette story n'ajoute **aucune** chaîne i18n, décision figée).
- **Tests backend** : E2E par les contrôleurs. Exception explicite (CLAUDE.md) : un client d'un système externe peut avoir son test de service isolé — le keepalive `SseEmitterRegistry` est une frontière sortante du même ordre (cf. `PrinterBridgeClient`), donc T3.1 est légitime **en plus** de la couverture E2E, pas à sa place.
- **Tests frontend** : Vitest via `npm test` dans `pluribourse-frontend/` (pas `npx vitest run`).
- **Budget IA** : vérifié suffisant (confirmé par Manerial au lancement du correct-course).

### Project Structure Notes

- Fichiers **modifiés** uniquement — **aucun fichier créé** côté source (hors nouveau test backend T3.1 si le dev le met dans une classe dédiée `SseEmitterRegistryKeepaliveTest.java` ; sinon assertion ajoutée à une classe existante).
- Aucune migration Liquibase, aucun changement de schéma, aucun endpoint nouveau, aucune route/ composant / dépendance / clé i18n.
- `SchedulingConfig` inchangé fonctionnellement (JavaDoc seulement).
- Rebuild image `pluribourse-frontend` requis pour T6 (nginx.conf embarqué).

### References

- [Source: _bmad-output/planning-artifacts/sprint-change-proposal-2026-09-07.md] — SCP complet : cause racine, découpage porteur/durcissement, §4 groupes A-H, §5 questions résolues, §6 AC.
- [Source: _bmad-output/planning-artifacts/architecture.md#Notification de Changement de Phase] — lignes Mécanisme / Impl. Spring / Impl. Angular / Résilience de la connexion (amendées SCP 2026-09-07).
- [Source: _bmad-output/planning-artifacts/epics.md#ARCH-012] + [#UX-DR4] + objectif Epic 2 — contrat de résilience SSE.
- [Source: prds/prd-PluriBourse-2026-06-08/prd.md#FR-066] — coupure SSE ≠ déconnexion ; redirection `/login` réservée au 401/403 confirmé ou à l'expiration 1 h.
- [Source: pluribourse-frontend/src/app/services/sse.service.ts] — `listen<T>` + `onerror` actuels (à refondre).
- [Source: pluribourse-frontend/src/app/core/interceptors/auth.interceptor.ts] — 401 → `clearSession()` + `/login` ; 403 (hors password-change) → idem. C'est ce que la sonde `/api/auth/me` réactive.
- [Source: pluribourse-frontend/src/app/services/auth.service.ts#restoreSession] — usage existant de `GET /api/auth/me`.
- [Source: pluribourse-frontend/src/app/services/current-edition.service.ts#loadEdition] — `Observable<void>`, garde de séquence monotone ; appelée pour la resynchro de phase à la reconnexion.
- [Source: pluribourse-backend/.../shared/sse/SseEmitterRegistry.java] — `emitters`, `register()`, `broadcast()` + pattern de retrait défensif à réutiliser.
- [Source: pluribourse-backend/.../shared/sse/SseController.java] — `GET /sse/events`.
- [Source: pluribourse-backend/.../shared/config/SchedulingConfig.java] — `@EnableScheduling` déjà en place (Story 4.9).
- [Source: pluribourse-backend/.../domain/pos/service/BasketReaperService.java] — patron `@Scheduled` + `@Value` `Duration` + garde `enabled` à mirrorer pour le keepalive.
- [Source: pluribourse-backend/src/test/resources/application.properties] — bloc reaper `enabled=false` + durées : mirrorer pour `sse.keepalive.*`.
- [Source: pluribourse-frontend/nginx.conf] — `location /api/` sans réglage SSE (cause #3) ; [Source: pluribourse-frontend/Dockerfile] — `COPY nginx.conf /etc/nginx/conf.d/default.conf`.
- [Source: _bmad-output/implementation-artifacts/2-6-notification-de-phase-en-temps-reel-via-sse.md#Review Findings] — historique de la branche `onerror` → logout (item 1, 2e passe items 409/412) ; dismissal « EventSource bypasses Angular interceptors … which is exactly why SseService needs its own handler » = fondement de la sonde.
- [Source: _bmad-output/implementation-artifacts/4-9-...md] — précédent `@Scheduled` / `@EnableScheduling` / toggle `enabled` de test.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#correct-course SCP 2026-09-07] — backoff exponentiel / bannière / multicast → V2 (hors périmètre).

## Dev Agent Record

### Agent Model Used

claude-sonnet-5 (bmad-dev-story)

### Debug Log References

- Backend ciblé : `SseEmitterRegistryKeepaliveTest` + `PhaseTransitionIT` + `PosBasketCancellationIT` + `SettlementSyncIT` — 33 tests verts. Confirme T1.3 : le `send()` bufferisé pré-initialisation fonctionne en Spring Framework 7 / Boot 4.0.6, aucun `IllegalStateException`, le chemin de repli T1.3 n'a pas servi.
- Backend complet : `./mvnw -o clean package` → `BUILD SUCCESS`, **584 tests / 0 échec / 0 skip** (Docker présent → `SaleConcurrencyIT` exécuté). Baseline 581 → +3 (nouveau test isolé keepalive).
- Frontend : `npm test` → **730 tests / 0 échec** (67 fichiers). Baseline 725 → +5 (net : −1 test « CLOSED → logout » supprimé, +6 nouveaux/remaniés). `npm run build` → bundle généré, **aucun warning**.
- i18n : aucune clé ajoutée/retirée ; `messages*.properties` et `fr.json` / `en.json` non touchés → parité inchangée.

### Completion Notes List

**Livré (T1–T7) :**

- **T1 — frame initiale + anti-buffering.** `SseEmitterRegistry.register()` envoie `SseEmitter.event().comment("ok")` avant le `return`, dans le même `try/catch (IOException | RuntimeException)` que `broadcast()`. `SseController.subscribe(HttpServletResponse)` pose `X-Accel-Buffering: no`. T1.3 vérifié : le buffered replay de `ResponseBodyEmitter` commit la réponse `200 text/event-stream` dès le câblage MVC — aucune régression sur les 3 IT qui asservissent le `Content-Type`/le corps SSE.
- **T2 — keepalive planifié.** `SseEmitterRegistry.sendKeepalive()` `@Scheduled(fixedDelayString/initialDelayString = "${sse.keepalive.interval:PT20S}")`, garde `keepaliveEnabled` en tête (pas de `@ConditionalOnProperty` — bean cœur), même retrait défensif que `broadcast()`. `application.properties` (main) : `sse.keepalive.interval=PT20S` + `enabled=true` + commentaire sur la relation des durées. `application.properties` (test) : `enabled=false` + les 2 durées (placeholders). JavaDoc de `SchedulingConfig` réécrit : 2 méthodes `@Scheduled`, gardes différentes (le keepalive garde-dans-méthode ⇒ le trigger `@Scheduled` reste enregistré en test, corps neutralisé).
- **T3 — tests backend.** Nouveau `SseEmitterRegistryKeepaliveTest` (isolé, sans Mockito, sans Spring) : sous-classes locales de `SseEmitter` (`RecordingEmitter` capture / échoue au besoin) ajoutées directement dans `emitters` — 3 cas (envoi à tous, retrait de l'émetteur en échec, no-op si désactivé). `PosBasketCancellationIT` `@Order(6)` : `.isEmpty()` → `.doesNotContain("phase-changed").doesNotContain("basket-cancelled")`. Aucune autre assertion SSE touchée (audit T3.2 confirmé : les autres n'utilisent que `.contains`/`.doesNotContain` sur des marqueurs métier).
- **T4/T5 — front.** `listen<T>` est désormais une boucle de connexion (`connect()` / `scheduleReconnect()` / `probeSession()`), délai `4000 + random*3000`, sonde `GET /api/auth/me` **exactement une fois** au franchissement `consecutiveFailures === PROBE_AFTER_FAILURES (2)` ; un `2xx` remet le compteur à 0. `onerror` ne fait plus **aucun** `clearSession` / `navigate` / `currentEdition.set(null)`. `phaseChanges()` seul passe `onReconnect = () => loadEdition().subscribe()`. Teardown : `disposed = true` + `clearTimeout` du timer en attente + `source?.close()`. Spec entièrement remaniée (fabrique multi-instances `instances[]`, `provideHttpClient(Testing)`, `HttpTestingController`) ; le test « CLOSED → logout » est remplacé par 3 cas (pas de logout + reconnexion, sonde après 2 échecs + reprise sur 2xx, resync `open` limité à `phaseChanges`) + le test « timer annulé au teardown ».
- **T6 — nginx.** Bloc `location /api/sse/` ajouté **avant** `location /api/` (`proxy_buffering off`, `proxy_http_version 1.1`, `Connection ''`, `proxy_cache off`, `proxy_read_timeout 3600s`, `chunked_transfer_encoding off`). Autres blocs intacts.
- **T7 — bookkeeping.** Notes miroir ajoutées en fin de `2-6-…md` (section « Superseded by Story 2.11 ») et `4-8-…md` (section « Note miroir — Story 2.11 »). Rien à porter dans epics/architecture/prd (SCP 2026-09-07 déjà dans l'arbre de travail).

**Écart assumé vs texte de la story (challengeable) :**

- **T4.1** demandait de « garder `AuthService`, `Router`, `CurrentEditionService` injectés ». `CurrentEditionService` est conservé (utilisé par le `onReconnect` de `phaseChanges`). En revanche `AuthService` et `Router` deviennent **totalement inutilisés** après le retrait du triplet `clearSession`/`set(null)`/`navigate` (T4.7) — les garder serait du code mort, ce que CLAUDE.md proscrit explicitement (« code mort front »). Ils sont donc **retirés** de `SseService`. Aucune conséquence AC : la redirection `/login` passe désormais uniquement par `authInterceptor` (via la sonde). Les providers `AuthService` / `Router` restent dans le `TestBed` du spec comme garde-fous de non-régression (`mockClearSession` / `router.navigate` asserés **non** appelés). À valider avec Manerial si tu préfères l'inverse.

**Reste à faire (hors code, côté Manerial) :**

- **T6.3** — rebuild de l'image `pluribourse-frontend` (le `nginx.conf` est embarqué via `COPY` dans le `Dockerfile`). Aucun changement `docker-compose.yml`.
- **T8** — vérification manuelle complète (stack `http://localhost`, OPEN immédiat, > 5 min sans `504`, onglet admin oisif + avance de phase, `docker restart` backend, logout réel).

### File List

**Backend — modifiés :**
- `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseEmitterRegistry.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/config/SchedulingConfig.java`
- `pluribourse-backend/src/main/resources/application.properties`
- `pluribourse-backend/src/test/resources/application.properties`
- `pluribourse-backend/src/test/java/org/pluribourse/domain/pos/PosBasketCancellationIT.java`

**Backend — créé :**
- `pluribourse-backend/src/test/java/org/pluribourse/shared/sse/SseEmitterRegistryKeepaliveTest.java`

**Frontend — modifiés :**
- `pluribourse-frontend/src/app/services/sse.service.ts`
- `pluribourse-frontend/src/app/services/sse.service.spec.ts`
- `pluribourse-frontend/nginx.conf`

**Documentation — modifiés (bookkeeping T7) :**
- `_bmad-output/implementation-artifacts/2-6-notification-de-phase-en-temps-reel-via-sse.md`
- `_bmad-output/implementation-artifacts/4-8-reservation-de-lot-au-scan-et-annulation-panier-a-la-deconnexion.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/2-11-resilience-connexion-sse-derriere-reverse-proxy.md`

### Change Log

| Date | Version | Description | Auteur |
|---|---|---|---|
| 2026-09-07 | 1.0 | Implémentation Story 2.11 : frame initiale SSE + `X-Accel-Buffering: no` (T1), keepalive `@Scheduled` 20 s avec garde `sse.keepalive.enabled` (T2), test isolé `SseEmitterRegistryKeepaliveTest` + correctif `PosBasketCancellationIT` @Order(6) (T3), reconnexion applicative front (délai + jitter, sonde `/api/auth/me`, plus de logout sur `CLOSED` de transport) + spec remaniée (T4/T5), bloc `location /api/sse/` nginx (T6), notes miroir Stories 2.6 / 4.8 (T7). Backend 584 verts / 0 skip (Docker présent), frontend 730 verts, `npm run build` sans warning, parité i18n inchangée. Écart assumé : `AuthService` / `Router` retirés de `SseService` (devenus code mort). Statut → review. T6.3 (rebuild image) + T8 (vérif manuelle) à faire par Manerial. | claude-sonnet-5 |

## Review Findings

Revue de code adverse (bmad-code-review, 3 revues parallèles : Blind Hunter + Edge Case Hunter + Acceptance Auditor) — 2026-09-07. 2 decision-needed, 4 patch, 3 deferred, 8 rejetés comme bruit.

### Decision needed (résolues 2026-09-07 par Manerial)

- [x] [Review][Decision] **Sonde `/api/auth/me` qui ne se ré-arme jamais après un échec non-401** [sse.service.ts:128] — `if (consecutiveFailures === PROBE_AFTER_FAILURES)` (égalité stricte) + compteur remis à 0 uniquement sur un 2xx. Si la sonde renvoie une erreur ≠ 401/403 (5xx, erreur réseau, timeout), `consecutiveFailures` continue de croître et la condition `=== 2` n'est plus jamais vraie → plus aucune sonde n'est émise pour la vie de l'abonnement. Déclencheur étroit (nécessite `/api/sse/events` cassé *spécifiquement* + `/api/auth/me` en erreur non-401) et auto-guéri par tout `open` réussi. Signalé par les 3 revues. **→ Décision Manerial : (c) laisser tel quel** — comportement aligné sur le point figé « reconnexion silencieuse », cas étroit et auto-guéri par un `open` réussi. Rejeté (pas de patch).
- [x] [Review][Decision] **`AuthService` et `Router` retirés de `SseService`** [sse.service.ts:1-7,52-53] — Écart assumé vs T4.1 (qui demandait de les garder injectés). Défendable : après T4.7 les deux deviennent du code mort, proscrit par CLAUDE.md ; AC3/AC4 tenues (aucun `clearSession`/`navigate` sur `CLOSED` de transport), la redirection `/login` passe désormais exclusivement par `authInterceptor` via la sonde. **→ Décision Manerial : (a) valider le retrait.** Les providers `AuthService`/`Router` restent dans le `TestBed` comme garde-fou de non-régression. Rejeté (pas de patch).

### Patch (appliqués 2026-09-07)

- [x] [Review][Patch] `register()` exécutait l'I/O `emitter.send(:ok)` sous le moniteur `synchronized` de la méthode — un client lent/semi-ouvert bloquait les autres `subscribe()` concurrents. **Fix :** `register()` n'est plus `synchronized` ; seuls `emitters.add` + les callbacks restent dans un bloc `synchronized (this)`, l'envoi de la frame initiale se fait hors du verrou (émetteur déjà enregistré, même retrait défensif en cas d'échec). [SseEmitterRegistry.java:33-53]
- [x] [Review][Patch] `@Slf4j` + `import lombok.extern.slf4j.Slf4j` jamais utilisés (aucun `log.`) — code mort. **Fix :** annotation et import retirés. [SseEmitterRegistry.java]
- [x] [Review][Patch] `PosBasketCancellationIT` @Order(6) : assertion trop faible. **Fix :** ajout de `.contains(":ok")` avant les `.doesNotContain(...)` — prouve que le flux s'est bien ouvert (frame initiale flushée). Vérifié : 6/6 verts. [PosBasketCancellationIT.java:258-263]
- [x] [Review][Patch] Bloc nginx `location /api/sse/` en préfixe simple. **Fix :** passé en `location ^~ /api/sse/` (match final, non masquable par un futur `location` regex) + commentaire mis à jour. [nginx.conf:11-14]

**Vérification :** `./mvnw -o test -Dtest=SseEmitterRegistryKeepaliveTest,PosBasketCancellationIT,PhaseTransitionIT,SettlementSyncIT` → 33 tests / 0 échec / 0 skip. Frontend non impacté par les patchs (seul `nginx.conf` touché, hors couverture Vitest).

### Deferred (pré-existant / hors périmètre — voir deferred-work.md)

- [x] [Review][Defer] `completeWithError()` appelé sur un émetteur déjà complété dans `sendKeepalive()` (et `broadcast()`) — peut lever hors de la méthode `@Scheduled` si le timeout absolu 30 min court-circuite l'envoi. Reproduit à l'identique le pattern de `broadcast()` (la story impose « réutiliser tel quel ») ; pré-existant ; `completeWithError` SF7 n'a pas de garde `Assert.state` donc le double-appel est le plus souvent un no-op. [SseEmitterRegistry.java:94-97] — deferred, pré-existant
- [x] [Review][Defer] Chaque flux (`phaseChanges`/`basketCancelled`/`settlementUpdated`) ouvre sa propre `EventSource` + sa propre boucle reconnexion/sonde → sur une coupure globale, 2-3 sondes `/api/auth/me` indépendantes ; 2-3 sockets SSE/onglet vers le plafond navigateur de ~6/hôte. AC4 rédigée par-connexion ; multiplexage explicitement V2. [sse.service.ts:83-149] — deferred, déjà tracé V2
- [x] [Review][Defer] Keepalive + reaper partagent le scheduler mono-thread par défaut de Spring — un `send()` bloquant retarderait le reaper et les keepalives des autres clients. Config pré-existante (story 4.9). [SchedulingConfig.java] — deferred, pré-existant
