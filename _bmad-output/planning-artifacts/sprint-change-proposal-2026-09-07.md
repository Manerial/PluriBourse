# Sprint Change Proposal: SSE connection resilience behind the reverse proxy

- **Date:** 2026-09-07
- **Author:** Manerial (via bmad-correct-course)
- **Skill:** bmad-correct-course
- **Mode:** Incremental
- **Status:** Draft — pending Manerial approval

---

## 1. Problem statement

Any authenticated user (observed on an **admin** account) is redirected to `/login` after roughly **one minute** on any page of the SPA. The user is **not actually logged out** — the backend session is still valid — the Angular app navigates itself to the login screen.

### Root cause

1. Every authenticated page opens an app-wide `EventSource` on `GET /api/sse/events` (`AppLayoutComponent.ngOnInit` → `SseService.phaseChanges()`).
2. **Spring's `SseEmitter` does not commit the HTTP response (status line + headers) until the first `emitter.send(...)`.** `SseEmitterRegistry.register()` returns a bare emitter and sends nothing on registration; with no phase change, the backend writes **zero bytes** on that socket — not even the response headers. There is also no keepalive.
3. The bundled reverse proxy `pluribourse-frontend/nginx.conf`, block `location /api/`, has **no SSE-specific configuration**. nginx forwards the request upstream and waits for the response; `proxy_read_timeout` (default **60 s**) is the max delay between two reads from the upstream. Since the upstream sends nothing — not even headers — for 60 s, nginx concludes the upstream is dead and returns a synthesized **`504 Gateway Timeout`** to the browser (it still can, having forwarded nothing to the client yet).
4. The browser `EventSource` — stuck in `CONNECTING` the whole 60 s — receives the `504` → per the WHATWG spec it **fails the connection**, sets `readyState = CLOSED`, fires `error`, and **does not reconnect**.
5. `sse.service.ts` `onerror`: `if (source.readyState === EventSource.CLOSED) { auth.clearSession(); currentEditionService.currentEdition.set(null); router.navigate(['/login']); }` → forced redirect to login.

> The observed status is a **`504`**, not a `200` stream that drops mid-way. That distinction confirms the mechanism: if Spring had flushed the `200 text/event-stream` headers immediately, nginx would have relayed them at once, the `EventSource` would have reached `OPEN`, and the 60 s cut-off would have been a mid-stream connection close — which native `EventSource` *does* retry, with no `504` and no `/login`. A `504` means the response headers never left the backend.

The `onerror` → logout branch was **added deliberately during the Story 2.6 code review** (its review log: *"SSE permanent failure … is never recovered from … now detects `readyState === EventSource.CLOSED` and triggers `clearSession()` + `router.navigate(['/login'])`"*). The intent was to handle **session expiry**, because `EventSource` bypasses Angular's `authInterceptor` and cannot get the global 401 handling. At the time this was defensible: `SseEmitterRegistry` closed every emitter right after each broadcast, so native `EventSource` auto-reconnected every few seconds and a **permanent** `CLOSED` could realistically only mean "session gone".

Two facts have invalidated that assumption:

- **Story 4.8** changed `SseEmitterRegistry` to **keep emitters open** between events (so a client no longer reconnects every few seconds) — the `onerror` assumption was never revisited.
- A reverse-proxy read-timeout produces a permanent `CLOSED` **with a perfectly valid session**.

So the mechanism now equates "the transport hiccuped" with "you are logged out", and does it every 60 s.

### Why it looked like Story 4.9

Story 4.9 shipped the day the bug was noticed and introduced the only 60-second timer in the frontend (the POS heartbeat) plus `@EnableScheduling`. Investigation ruled both out: the symptom occurs on **admin-only pages** (no POS heartbeat there), the `SPRING_SESSION` row is healthy (`MAX_INACTIVE_INTERVAL = 3600`, expiry +56 min), and the failing request is `GET /api/sse/events` returning **504**, not a `4xx` on a heartbeat. The defect is **pre-existing since Story 2.6** (real-time phase notification via SSE).

### Evidence

- `SPRING_SESSION` (dev MariaDB, read-only query): single row, `MAX_INACTIVE_INTERVAL = 3600`, `EXPIRY_TIME` ≈ now + 56 min, `LAST_ACCESS_TIME` ≈ 3 min stale (SPA idle on `/login` after the redirect). Session is alive; the redirect is client-side.
- Network tab (Manerial): `GET http://localhost/api/sse/events` → **504** after ~60 s. URL on port 80 ⇒ traffic goes through the `pluribourse-frontend` nginx container.
- `pluribourse-frontend/nginx.conf` l.10-14: `location /api/` has only `proxy_pass` + `Host` / `X-Real-IP` headers — no `proxy_read_timeout`, no `proxy_buffering off`, no `proxy_http_version 1.1`.
- `SseEmitterRegistry.register()` — returns a bare `SseEmitter`, sends no initial frame; `broadcast()` is the only writer and no keepalive exists ⇒ an idle connection never commits its response headers.
- `SseService.listen().onerror` (`sse.service.ts` l.73-82) — `CLOSED` ⇒ `clearSession()` + redirect.
- `architecture.md` § Notification de Changement de Phase, l.247: *"HTTP simple (pas de problèmes de proxy sur le réseau local de la salle)"* — the assumption contradicted by the evidence; l.248 *"fermés après l'envoi de l'événement"* — stale since Story 4.8; l.249 *"reconnexion gérée nativement"* — misleading (no native reconnect after a hard HTTP error).
- Functional regression against **FR-066** (auto-logout after **1 h** of inactivity — not 60 s) and **UX-DR4** (phase chip updated live).

### Issue type

Failed approach / incorrect architectural assumption discovered during implementation testing. Not a new requirement, not a scope change.

---

## 2. Impact analysis

### Epic impact

- **Epic 2 — Gestion du cycle de vie des éditions** (owner of the SSE mechanism, F2). Its delivered stories stay valid; the SSE transport delivered by **Story 2.6** has a resilience defect that needs a corrective story. → **New Story `2-11` in Epic 2.** No epic scope redefinition, no epic removal, no resequencing. Precedent: Story 2-10 ("Préparation non-exclusive") was added to Epic 2 by correct-course.
- **Epic 4 — Point de vente** — where the bug was noticed. Structurally unaffected. The POS `basket-cancelled` SSE listener rides the same connection and benefits from the fix.
- **Epic 3 — Impression** — FR-079 allows printer-error notification *"via SSE ou réponse de polling"* (architecture l.263). If the shipped implementation uses SSE, it rides the same `/api/` proxy path and has the same 60 s cutoff. The nginx + keepalive fix covers it regardless; Story 2-11 verifies which path is live.

### Story impact

- **Story 2.6 (`2-6-notification-de-phase-en-temps-reel-via-sse`)** — amend Dev Notes / Review Findings: the `onerror` → `clearSession()` + redirect behavior is superseded (transport failure is no longer treated as logout); AC4 ("connection drops and reconnects … no user action needed") is now enforced for a *hard* failure too, not just a post-broadcast close.
- **Story 4.8** — mirror note: keeping emitters open (its change to `SseEmitterRegistry`) is why a permanent `CLOSED` can no longer be assumed to mean "session expired"; the keepalive added by 2-11 is the counterpart.
- **No future planned story is invalidated.** No new story beyond `2-11`.

### Artifact conflicts

| Artifact | Section | Conflict / change |
|---|---|---|
| `architecture.md` | § Notification de Changement de Phase, l.247 | "pas de problèmes de proxy sur le réseau local" is false — SSE needs explicit reverse-proxy config. |
| `architecture.md` | same, l.248 | "Émetteurs … fermés après l'envoi de l'événement" — stale since Story 4.8 (emitters kept open). |
| `architecture.md` | same, l.249 | "reconnexion gérée nativement" — misleading; native `EventSource` does not reconnect after a hard HTTP error, and the client must not treat `CLOSED` as an auth failure. |
| `architecture.md` | same section | Missing: SSE keepalive, `location /api/sse/` proxy contract, client reconnection-with-jitter policy, "redirect to `/login` only on a confirmed 401/403". |
| `epics.md` | ARCH-012 (l.178) | Add the proxy + keepalive + client-reconnection requirement to the SSE architectural constraint. |
| `epics.md` | Epic 2 goal line (l.324) | Light clarification: live phase via SSE with a maintained (keepalived) connection and automatic reconnection. |
| `epics.md` | UX-DR4 (l.189) | Define the degraded state: silent auto-reconnect, chip keeps last known phase, no user-facing error. |
| `epics.md` | Epic 2 story list | Add Story 2-11. |
| `prds/prd-PluriBourse-2026-06-08/prd.md` | FR-066 (l.286) | Add one clarifying sentence: a transient SSE/proxy transport failure must not trigger the automatic logout — only a confirmed 401/403 or the 1 h expiry does. MVP intact. |
| `pluribourse-frontend/nginx.conf` | `location /api/` | New dedicated `location /api/sse/` block (buffering off, long read timeout, HTTP/1.1). |
| `implementation-artifacts/sprint-status.yaml` | Epic 2 block | Add `2-11-…: backlog`; bump `last_updated`. |
| Testing strategy | — | New `SseService` unit tests + backend keepalive assertion + a documented manual verification through nginx. The E2E-through-controller philosophy does not cover nginx. |
| `docker-compose.yml` / deployment | — | **No change** — `nginx.conf` is baked into the frontend image via its Dockerfile. |
| Installation guide (FR-074) | — | **No change** — nginx is internal. |

### Technical impact (summary)

- **Infra:** `pluribourse-frontend/nginx.conf` — one new `location` block. Rebuild of the frontend image.
- **Backend:** `SseEmitterRegistry.register()` sends an immediate initial frame (commits the response) + an `X-Accel-Buffering: no` header; a scheduled keepalive writer over the emitters (reuses `@EnableScheduling`, available since Story 4.9), same defensive remove-on-`IOException` as `broadcast()`. One config property for the keepalive interval. No API contract change, no DB change.
- **Frontend:** `sse.service.ts` — `onerror` stops calling `clearSession()` / `navigate`. Adds a manual reconnect (fixed delay + jitter). Redirect to `/login` is delegated to `authInterceptor` via a lightweight `GET /api/auth/me` probe after repeated failures. `currentEdition` is reset only on a confirmed logout.
- **Risk:** Low-Medium. Watch for (a) a reconnection storm on backend restart — mitigated by jitter and a sane delay; (b) masking a real 401 — mitigated by the `/api/auth/me` probe so the global interceptor still owns the logout decision.

---

## 3. Recommended approach

**Direct adjustment** (checklist Option 1): **one new Story `2-11` in Epic 2**, plus incremental mirror-note updates to Stories 2.6 / 4.8 and the four planning artifacts above. Same pattern as SCP 2026-09-03 / 2026-09-04.

- **Option 2 (rollback Story 2.6):** rejected — SSE works; only resilience is missing. A rollback would remove a delivered feature (live phase updates).
- **Option 3 (MVP review):** N/A — real-time phase is in scope and mostly works; no scope reduction.

**Effort: Medium. Risk: Low-Medium.** MVP intact → no PM / Architect escalation.

### Story 2-11 scope split

`(a)` + `(b)` alone fix the `504` and keep the connection alive; `(c)` makes any residual drop harmless. The nginx work `(f)` is **not on the critical path** — with `(a)`+`(b)`+`(c)` the logout bug is gone without touching `nginx.conf`; `(f)` improves real-time delivery quality and portability across proxies.

| Tier | Content |
|---|---|
| **Must-fix — load-bearing, proxy-agnostic** | (a) `SseEmitterRegistry.register()` sends an **immediate initial frame** (comment `:ok` or a `connected` event) so the response commits and the `EventSource` reaches `OPEN` at once, even with no pending event; (b) **scheduled keepalive** (~20 s) on every emitter, same defensive remove-on-`IOException` as `broadcast()`; (c) frontend: **never** `clearSession()` / redirect on a transport `CLOSED` — manual reconnect (**fixed ~5 s delay + jitter**) + a `GET /api/auth/me` probe after repeated failures so `authInterceptor` owns the logout on a real 401/403; (d) `currentEdition` reset only on confirmed logout; (e) tests + documented manual verification through nginx. |
| **Must-fix — hardening (not a dependency of the above)** | (f) `location /api/sse/` nginx block (`proxy_buffering off`, `proxy_http_version 1.1`, long `proxy_read_timeout`) **and** an `X-Accel-Buffering: no` header on the SSE response from the backend — real-time delivery without buffering/batching (UX-DR4) and margin if a keepalive is briefly delayed. Matters for anyone deploying behind a different reverse proxy. |
| **Deferred to V2 backlog** | Exponential backoff (1 s → 2 s → 4 s … capped) instead of a fixed delay; max-retry UX ("connexion perdue" banner); SSE multicast (today one `EventSource` per subscriber ⇒ up to ~2 connections per tab). |

> **On "backoff"** (for the record, per Manerial's question): when the SSE link drops and the client must reconnect, *backoff* is how retry attempts are spaced. Native `EventSource` retries at a fixed interval (~3 s, server-hintable via `retry:`), but **only** for network-level drops — after a hard HTTP error (our 504) it gives up entirely, which is why we must code reconnection ourselves. *Fixed retry* = every N seconds. *Exponential backoff* = 1 s, 2 s, 4 s, 8 s … capped, so a long backend outage stops being hammered. *Jitter* = a small random offset per wait, so that if the 3–5 POS terminals all lose the link at the same instant (a backend restart — FR-066 says restarts are expected), they don't all retry in lockstep and re-overload the Raspberry Pi. Given the venue scale (1 RPi 4 / 2 GB, ~3–5 POS + a few admin tabs) and that the keepalive makes drops rare, **fixed 5 s + jitter is sufficient for 2-11**; exponential backoff earns its keep at hundreds of clients, not here → V2.

---

## 4. Detailed change proposals

> House convention: SCP prose in English; quoted edits to the French planning docs are in French.
> Line numbers are indicative — match on the quoted OLD text.

### Group A — Architecture (`architecture.md`, § Notification de Changement de Phase)

**P-ARCH1 — "Mécanisme" row justification (l.247)** — replace the tail of the justification cell

- OLD:
  > … `EventSource` se reconnecte automatiquement selon la RFC 8895 ; HTTP simple (pas de problèmes de proxy sur le réseau local de la salle)
- NEW:
  > … `EventSource` gère une reconnexion native pour les coupures réseau **mais pas après une réponse HTTP d'erreur**. Piège Spring : un `SseEmitter` **ne valide la réponse HTTP (statut + en-têtes) qu'au premier `send()`** — sans keepalive ni frame initiale, une connexion oisive n'émet aucun octet, le reverse-proxy attend puis renvoie 504, `EventSource` passe `CLOSED` sans réessayer. Trois garde-fous obligatoires : (1) le serveur **envoie une frame initiale dès `register()`** (valide la réponse, `EventSource` passe `OPEN` immédiatement) ; (2) un **keepalive** serveur périodique (commentaire SSE sur chaque émetteur) pour qu'aucun intermédiaire ne voie la connexion comme morte ; (3) une **reconnexion applicative** côté client (délai fixe + jitter) qui **n'invalide jamais la session**. Durcissement : le reverse-proxy livré (`pluribourse-frontend/nginx.conf`) expose un `location /api/sse/` dédié (`proxy_buffering off`, `proxy_http_version 1.1`, `proxy_read_timeout` long) et le backend pose `X-Accel-Buffering: no`. *(SCP 2026-09-07)*

**P-ARCH2 — "Implémentation Spring" row (l.248)** — replace the justification cell

- OLD:
  > Émetteurs gérés dans un registre thread-safe ; fermés après l'envoi de l'événement lors du changement de phase
- NEW:
  > Émetteurs gérés dans un registre thread-safe, **maintenus ouverts entre deux évènements** (depuis la Story 4.8) ; retirés uniquement à la déconnexion réelle du client (`onCompletion` / `onTimeout` / `onError`) ou sur `IOException` à l'écriture. Un **keepalive planifié** (`@Scheduled`, réutilise `@EnableScheduling` de la Story 4.9) écrit un commentaire SSE sur chaque émetteur à intervalle configurable (défaut ~20 s), avec le même retrait défensif que `broadcast()`. *(SCP 2026-09-07)*

**P-ARCH3 — "Implémentation Angular" row (l.249)** — replace the justification cell

- OLD:
  > Testable avec `jest.fn()` ; reconnexion gérée nativement
- NEW:
  > Testable avec `jest.fn()`. **Reconnexion applicative** : sur `error` avec `readyState === CLOSED`, le service ferme la source et replanifie une nouvelle connexion (délai fixe ~5 s + jitter) — il **n'invalide jamais la session** et ne redirige jamais de lui-même. Après plusieurs échecs consécutifs, une sonde `GET /api/auth/me` (qui passe, elle, par `authInterceptor`) tranche : 401/403 → l'intercepteur global redirige vers `/login` ; 2xx → le service continue de réessayer. `currentEdition` n'est remis à `null` que sur une déconnexion confirmée. *(SCP 2026-09-07)*

**P-ARCH4 — new row after "Implémentation Angular"** — add the resilience contract explicitly

- NEW:
  > | Résilience de la connexion | Keepalive serveur (~20 s) + reconnexion client (délai fixe + jitter) + `location /api/sse/` nginx (buffering off, read timeout long, HTTP/1.1) | Une coupure de transport (proxy, réseau, redémarrage backend) ne doit **jamais** déconnecter l'utilisateur (FR-066 : déconnexion auto seulement après 1 h d'inactivité) ; le chip de phase conserve la dernière phase connue et se resynchronise à la reconnexion (aucun rejeu des évènements manqués — le client recharge l'état via `GET /api/editions/current`). Backoff exponentiel et bannière « connexion perdue » : reportés V2. *(SCP 2026-09-07)* |

### Group B — Epics (`epics.md`)

**P-EP1 — ARCH-012 (l.178)** — append to the constraint

- OLD:
  > - ARCH-012 : SSE (`SseEmitterRegistry`) doit être initialisé avant les endpoints de transition de phase. Événements : `phase-changed` (payload : editionId, newPhase, previousPhase) et `basket-cancelled`.
- NEW:
  > - ARCH-012 : SSE (`SseEmitterRegistry`) doit être initialisé avant les endpoints de transition de phase. Événements : `phase-changed` (payload : editionId, newPhase, previousPhase) et `basket-cancelled`. La connexion SSE est **maintenue par un keepalive serveur** et le client applique une **reconnexion automatique** (délai fixe + jitter) ; une coupure de transport ne déconnecte jamais l'utilisateur (redirection `/login` réservée à un 401/403 confirmé ou à l'expiration de session FR-066). Le reverse-proxy expose un `location /api/sse/` dédié (buffering off, read timeout long, HTTP/1.1). *(SCP 2026-09-07, Story 2.11)*

**P-EP2 — Epic 2 goal line (l.324)** — replace the last sentence

- OLD:
  > Tous les utilisateurs connectés voient la phase active en temps réel via SSE.
- NEW:
  > Tous les utilisateurs connectés voient la phase active en temps réel via SSE — connexion maintenue par keepalive et reconnexion automatique, résiliente aux coupures de proxy/réseau.

**P-EP3 — UX-DR4 (l.189)** — append one sentence

- Append:
  > En cas de perte de la connexion SSE, la reconnexion est **silencieuse** : le chip conserve la dernière phase connue, aucune erreur n'est affichée, et il se met à jour dès la reconnexion. *(SCP 2026-09-07)*

**P-EP4 — Epic 2 story list** — ~~N/A~~. `epics.md`'s detailed `### Story 2.x` sections were last maintained at Story 2.8; correct-course-added stories (2.9 "devise", 2.10 "préparation non-exclusive") are tracked in `sprint-status.yaml` only, not back-ported into `epics.md`. Story 2-11 follows the same precedent — its "list entry" is `P-SS1` (Group G). No `epics.md` change here.

### Group C — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**P-PRD1 — FR-066 (l.286)** — append one sentence to the tail

- Append after the existing "… choix de sécurité délibéré pour les postes bénévoles partagés en libre accès dans la salle. …" clause:
  > Une coupure de la connexion temps réel (SSE) — reverse-proxy, réseau, redémarrage du service — **ne déclenche pas** cette déconnexion : le client se reconnecte automatiquement et ne redirige vers l'écran de connexion que sur un `401`/`403` confirmé ou à l'échéance du délai d'inactivité d'une heure. *(Précisé 2026-09-07, voir `sprint-change-proposal-2026-09-07.md` — l'ancien comportement redirigeait vers `/login` sur toute fermeture définitive de l'`EventSource`, y compris un simple timeout de proxy.)*

### Group D — Infra: `pluribourse-frontend/nginx.conf` (implemented by Story 2-11, described here for scoping)

*Hardening — not a prerequisite for the bug fix (Group E carries that). Improves real-time delivery quality and cross-proxy portability.*

**P-INFRA1 — new `location /api/sse/` block, placed BEFORE `location /api/`**

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

- `proxy_read_timeout 3600s` aligns with the `SseEmitter` server-side timeout (`SseEmitterRegistry` uses `30 * 60 * 1000L` today — Story 2-11 may raise it to 1 h for symmetry, or leave 30 min; either way the keepalive keeps the socket busy well under any timeout).
- nginx matches prefix `location` blocks longest-first, so `/api/sse/` wins over `/api/` for those requests — no ordering trap, but keep it above for readability.

### Group E — Backend: SSE connection commit + keepalive (implemented by Story 2-11, described here for scoping)

**P-BE1 — immediate initial frame on `SseEmitterRegistry.register()`** *(load-bearing)*

- Right after creating the emitter and registering it: `emitter.send(SseEmitter.event().comment("ok"))` (or a named `connected` event with no payload).
- This commits the `200 text/event-stream` response, so the client `EventSource` reaches `OPEN` immediately and nginx starts relaying — no more `504` on an idle connection.
- Wrap in the same try/catch as `broadcast()` (a client that vanished between the request and this line → remove + `completeWithError`).
- Also set `X-Accel-Buffering: no` on the response (via the controller or a small filter on `/api/sse/**`) so nginx disables buffering for this response even if the `location` block is missing or a different proxy is in front.

**P-BE2 — periodic keepalive over `SseEmitterRegistry` emitters** *(load-bearing)*

- A `@Scheduled(fixedDelayString = "${sse.keepalive.interval:PT20S}")` method iterates the registry and writes a comment frame (`SseEmitter.event().comment("keepalive")`) to each emitter.
- Same defensive handling as `broadcast()`: on `IOException | RuntimeException`, remove the emitter and `completeWithError(e)`.
- Reuses `SchedulingConfig` / `@EnableScheduling` (Story 4.9). Add `sse.keepalive.interval=PT20S` to `application.properties`; add it to `src/test/resources/application.properties` too so the `@Value` placeholder always resolves. Consider `sse.keepalive.enabled` (default true) mirroring the reaper toggle if the ordered SSE ITs need it off.
- Interval rationale: 20 s < any common proxy idle default (nginx 60 s) and comfortably < the `location /api/sse/` `proxy_read_timeout`.

### Group F — Frontend: `sse.service.ts` (implemented by Story 2-11, described here for scoping)

**P-FE1 — `onerror` no longer logs the user out**

- On `error` with `source.readyState === EventSource.CLOSED`: `source.close()`, then schedule a fresh `EventSource` after `RECONNECT_DELAY_MS` (~5000) plus jitter (±30 %). Keep reconnecting.
- Track consecutive failures. After N (e.g. 2) consecutive `CLOSED` with no successful `open`, fire one `HttpClient.get('/api/auth/me')`:
  - 401/403 → `authInterceptor` handles the redirect to `/login` (no direct `router.navigate` here); stop reconnecting.
  - 2xx → reset the failure counter, keep reconnecting.
- Remove the direct `auth.clearSession()` / `router.navigate(['/login'])` calls from `onerror`.
- `currentEditionService.currentEdition.set(null)` moves to the confirmed-logout path only (or is dropped — `restoreSession()` / `loadEdition()` already reset it on the login flow).
- `takeUntilDestroyed` / the Observable teardown must cancel any pending reconnect timer.

**P-FE2 — tests (`sse.service.spec.ts`)**

- A simulated `error` with `readyState === CLOSED` and a stubbed `GET /api/auth/me` → **200**: asserts `clearSession` is NOT called, `router.navigate` is NOT called, and a reconnect `EventSource` is created after the delay (fake timers).
- Same but `/api/auth/me` → **401**: asserts reconnection stops (the redirect is `authInterceptor`'s job and is covered by the existing interceptor tests).
- Existing SSE ITs (`CurrentEditionIT`, phase ITs) stay green.

### Group G — Sprint status (`implementation-artifacts/sprint-status.yaml`)

**P-SS1** — add under Epic 2, immediately before `epic-2-retrospective: optional`:

```
  # Correct-course SCP 2026-09-07 : bug pré-existant depuis la Story 2.6 (SSE), révélé en test post-4.9.
  # Derrière le reverse-proxy livré (pluribourse-frontend/nginx.conf, location /api/ sans réglage SSE),
  # GET /api/sse/events renvoie 504 au bout de 60 s (proxy_read_timeout défaut + proxy_buffering on,
  # aucun keepalive serveur). L'EventSource passe CLOSED sans reconnexion native, et sse.service.ts
  # onerror fait clearSession() + navigate(['/login']) — l'utilisateur (session backend valide) est
  # rejeté vers /login toutes les minutes. Régression vs FR-066 (déconnexion auto après 1 h, pas 60 s).
  # Cause réelle : SseEmitter ne valide la réponse HTTP qu'au 1er send() ; register() n'envoie rien →
  # sur connexion oisive le backend n'émet aucun octet → nginx renvoie 504 (attente en-têtes upstream).
  # Story 2-11 (porteur, indépendant du proxy) : (a) register() envoie une frame initiale immédiate
  # (commit la réponse → EventSource OPEN tout de suite, plus de 504) + en-tête X-Accel-Buffering: no ;
  # (b) keepalive backend planifié sur SseEmitterRegistry (~20 s, réutilise @EnableScheduling de 4.9) ;
  # (c) front : plus de clearSession/redirect sur CLOSED — reconnexion applicative (délai fixe + jitter),
  # sonde GET /api/auth/me pour laisser authInterceptor trancher un vrai 401/403.
  # Story 2-11 (durcissement, hors chemin critique) : (d) bloc nginx location /api/sse/ (buffering off,
  # HTTP/1.1, read timeout long). Backoff exponentiel + bannière « connexion perdue » + multicast SSE :
  # reportés V2 (deferred-work.md). Amende Stories 2.6 (onerror superseded) et 4.8 (émetteurs gardés
  # ouverts → CLOSED ≠ session expirée).
  2-11-resilience-connexion-sse-derriere-reverse-proxy: backlog
```

- Bump `last_updated` to `2026-09-07` with a one-line pointer to this SCP.

### Group H — Deferred-work backlog (`implementation-artifacts/deferred-work.md`)

**P-DW1** — add an entry:
> **SSE V2 hardening** — exponential backoff + jitter (vs the fixed delay shipped in 2-11); "connexion perdue" UX banner after max retries; SSE multicast (one shared `EventSource` per tab instead of one per subscriber). Source: SCP 2026-09-07 / Story 2-11.

---

## 5. Open questions

| # | Question | Recommendation |
|---|---|---|
| Q1 | Backoff sophistication in Story 2-11 | **Fixed 5 s + jitter** for 2-11; exponential backoff → V2 (`deferred-work.md`). Rationale in §3. **Confirm.** |
| Q2 | Keepalive interval | **20 s** (`sse.keepalive.interval=PT20S`). Confirm or set another value. |
| Q3 | `location /api/sse/` `proxy_read_timeout` | **3600s**, and optionally raise `SseEmitter` server timeout from 30 min to 1 h for symmetry. Confirm. |
| Q4 | Printer-error notification (FR-079) — SSE or polling in the shipped code? | Story 2-11 checks during dev; the fix covers both. No decision needed now. |
| Q5 | SSE multicast (one `EventSource` per subscriber) | Out of scope for 2-11, logged in `deferred-work.md` (P-DW1). Confirm. |
| Q6 | Story-file mirror notes (2.6, 4.8) | Bookkeeping — applied when Story 2-11 is created. |

---

## 6. Handoff plan

**Scope: Moderate.** New Story `2-11` (backend initial-frame + keepalive, frontend reconnection rework, nginx hardening block, tests) + 2 `done` stories mirror-noted + 4 planning artifacts amended. No replan, MVP intact → **no PM / Architect escalation.**

| Step | Agent / skill | Deliverable |
|---|---|---|
| 1 | **bmad-create-story** | `2-11-resilience-connexion-sse-derriere-reverse-proxy.md` with full context (this SCP, the artifact edits, AC below). |
| 2 | Apply artifact edits (Groups A–C) + `sprint-status.yaml` (G) + `deferred-work.md` (H) | Incremental edits to `architecture.md`, `epics.md`, `prd.md`. |
| 3 | **bmad-dev-story** | Implementation of Groups D–F + tests. |
| 4 | **bmad-code-review** | Fresh-context review. |
| 5 | Manerial | Manual verification through nginx (success criteria below). |

### Acceptance criteria for Story 2-11

1. `GET /api/sse/events` reaches `EventSource.readyState === OPEN` **immediately** (initial frame — no `CONNECTING` hang), and stays open **> 5 min** through the `pluribourse-frontend` nginx with no phase event; the browser receives keepalive frames; **no `504`**.
2. An **idle admin tab** (> 2 min, no interaction) receives a `PREPARATION → DEPOSIT` `phase-changed` event and updates the phase chip, **without** redirecting to `/login`.
3. Backend killed for ~30 s: the tab reconnects automatically within a few seconds of it returning; **no forced logout**, chip resynchronizes.
4. A genuinely expired / invalidated session still lands on `/login` — via the `GET /api/auth/me` probe path picked up by `authInterceptor`.
5. `sse.service.ts` `onerror` contains **no** direct `clearSession()` / `router.navigate` call.
6. Frontend + backend test suites green; coverage ≥ 80 %.
7. `architecture.md`, `epics.md` (ARCH-012, Epic 2 goal, UX-DR4), `prd.md` (FR-066) reflect the new resilience contract.

### Success criteria (post-merge)

- No user reports of unexpected `/login` redirects during normal use.
- Live phase chip updates observed on idle tabs across a full phase cycle.
- RPi 4: no measurable memory/CPU regression from the keepalive under the reference load (~5 connections).
