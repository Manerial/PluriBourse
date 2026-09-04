---
title: "Sprint Change Proposal: dead-POS-terminal detection by heartbeat — freeing an abandoned basket and its lot reservations"
date: 2026-09-04
status: approved
approved_by: Manerial
approved_date: 2026-09-04
author: Manerial (via Claude Code)
triggered_by: "Code review of Story 4.8 (bmad-code-review, 2026-09-04) — decision-needed point D2 (Manerial ruling: option 1 — ship 4.8, handle the gap in a dedicated follow-up story)."
activates: "Deferred item flagged by SCP 2026-09-03 §3 / open question #2 (« Story de suivi expiration de session → annulation du panier / libération des réservations », à réévaluer après livraison de la Story 4.8)."
---

# Sprint Change Proposal: dead-POS-terminal detection by heartbeat

> Numbered `-2026-09-04`. Follows the code review (`bmad-code-review`) of **Story 4.8** and activates the follow-up story that SCP 2026-09-03 deferred.

**Trigger.** The code review of Story 4.8 raised a `decision-needed` point (**D2**): the scan-time lot reservation introduced by 4.8 (`lots.reserved_by_basket_id`, set when the first member of a lot enters a POS basket) is released **only** by an explicit action on that basket — last member removed, `removeLot`, a successful `validate()`, an edition phase change, or an **explicit** `POST /auth/logout`. A POS terminal that is closed (tab or browser), crashes, loses the network, or simply hits the 1-hour idle-session timeout performs **none** of those. The reservation stays on a dead basket, and every member of that lot becomes unsellable **at every POS terminal** until the SALE→POST_SALE phase change runs the bulk cancellation. The backend has **no reliable signal** for a terminal that has gone away without logging out.

**Ruling (Manerial, D2, option 1).** Ship Story 4.8 as delivered; close the gap in a dedicated follow-up story. This is the "suite différée" that SCP 2026-09-03 (§3, open question #2) explicitly left to reassess after 4.8 shipped — activated now.

**Mode.** Batch review — the design was largely settled during the D2 discussion.

---

## 1. Problem statement

| # | Subject | BMAD category |
|---|---|---|
| D2 | The scan-time lot reservation (Story 4.8, FR-109) has **no expiry, no purge, and no cleanup on non-explicit session loss**. A closed / crashed / network-lost / idle-timed-out POS terminal leaves `lots.reserved_by_basket_id` set on a dead `Basket` → the whole lot is unsellable at every till until the phase change. New failure mode vs Story 5.8, whose validation-time `@Version` guard held no claim on an abandoned basket. | **Technical limitation discovered during implementation.** FR-110 states the basket's lifetime is "bornée par la session", but nothing enforces that on anything other than an explicit logout. |

### Root cause

The scan-time reservation is the right level for lot integrity (SCP 2026-09-03 root-cause analysis), but a reservation that only a *deliberate* release can clear needs a matching detection of "the basket owner is gone". HTTP is request/response: no request ≠ observable "the user left". The three ways a terminal actually goes away — tab/browser closed, machine crash / power loss, network loss — produce **no backend signal at all**; the session simply sits in `spring_session` until its 1-hour inactivity timeout, and Spring Session JDBC's periodic cleanup then bulk-`DELETE`s the row **without** running `LogoutFilter` / any `LogoutHandler` and without a dependable per-session application event.

The only mechanism that observes all of these is a **liveness signal sent by the client** while the POS page is open, plus a **server-side sweep** that cancels baskets whose signal has stopped. That also subsumes the FR-066 idle-timeout gap: an idled-out session stops sending heartbeats and is swept like any other dead terminal.

### Evidence

- `deferred-work.md` → section "Deferred from: code review of story 4-8 (…)", item **W5** (D2) and the Blind-Hunter-#7 sub-point.
- Story 4.8 file → `### Review Findings` → "Décisions tranchées (2026-09-04)" → **D2** and defer **W5**.
- SCP 2026-09-03 §3 ("Hors périmètre / suite différée") and §5 open question #2 — the same follow-up, deferred pending 4.8 delivery.
- `PosBasketService` (post-4.8): reservation set in `addItem` → `reserveLotForBasket`; released in `removeItem` (last member) / `removeLot` / `validate()` success (`releaseAllByBasketId`); `BasketCancellationService.cancelBasketSilently` (logout) / `cancelBaskets` (phase change). No time-based path.
- `035-lot-reservation.xml` comment: *"reserved_at est purement diagnostic … pas de TTL, pas de purge planifiée."*
- `application.properties`: `spring.session.store-type=jdbc` + `spring.session.timeout=PT1H`; `BasketCancellingLogoutHandler` is wired on `/auth/logout` only.
- No `@Scheduled` / `@EnableScheduling` anywhere in the backend today (this story adds the first one).
- `Basket` entity: `@Table(name = "baskets")`, `uk_baskets_edition_user`; `basket_items` FK `deleteCascade`. No timestamp column.
- Last Liquibase migration: `035-lot-reservation.xml`.

---

## 2. Impact analysis

### Epic impact

The 6 epics keep their status. **No new epic, no obsolete epic, no resequencing.** One **new story `4-9`** in Epic 4 (which stays `in-progress`) delivers the change; three `done` stories get incremental mirror-note updates (same pattern as SCP 2026-08-24 / 2026-09-02b / 2026-09-03).

- **Epic 4 — Point de vente**: new Story 4.9. Story 4.8 gains a note (its Review-Findings W5 is the origin; nothing re-opened). Epic intro ("gestion des paniers … en toute sécurité sur plusieurs postes") stays accurate.
- **Epic 2 — Cycle de vie des éditions**: Story 2.8 note extended — the shared `BasketCancellationService.cancelBasketSilently` is now also invoked by the liveness reaper.
- **Epic 1 — Fondation & Auth**: FR-066 mirror line updated — the idle-session gap it flagged is now actually closed (via the reaper, story 4.9). No Epic 1 `done` story re-opened.

### Story impact

| Story | Status | Change |
|---|---|---|
| **4.9** (new) | backlog | Heartbeat endpoint + `baskets.last_seen_at` + migration `036` + front liveness timer + `@Scheduled` reaper reusing `BasketCancellationService.cancelBasketSilently` + config thresholds. Plus a targeted fix for Blind-Hunter-#7 (`validate()` releases the reservation of a lot it rejects at the committed-sale pre-check). To be written via `bmad-create-story`. |
| 4.8 — Réservation de lot au scan & annulation à la déconnexion | done | Note added: the "poste mort" gap (Review Findings D2 / W5) is delivered by Story 4.9. Not re-opened. |
| 2.8 — Annulation du panier au changement de phase | done | Note extended (P-EP-2.8): `cancelBasketSilently` also invoked by the liveness reaper (Story 4.9). |
| 5.8 — Post-vente & lots partiels | done | Untouched. Chain of origin only (5.8 review → SCP 2026-09-03 → 4.8 → 4.8 review → this). |

### Artifact conflicts

- **PRD** (`prds/prd-PluriBourse-2026-06-08/prd.md`) — **FR-110 amended** (basket lifetime is enforced for non-explicit session loss too, via client heartbeat + server reaper); **FR-066 amended** (idle-timeout now also frees the basket and its lot reservations). FR-109 / FR-047 / NFR-006: no text change. **MVP unaffected**, no scope reduction — the feature only becomes robust against abandoned terminals.
- **Epics** (`epics.md`) — FR-110 mirror lines (l.85, l.307) and FR-066 mirror lines (l.123, l.281) amended; FR-109 mirror lines (l.93, l.261) + architecture "Intégrité des lots" gain the reaper as an additional release trigger; Story 2.8 note (l.1028) extended; "FR couvertes" Epic 4 unchanged (FR-110 already listed). **UX-DR21**: extended with a sentence — a reaped basket reuses the existing "basket gone → fresh empty basket on next action" behaviour, no dedicated toast or i18n string (consistent with the logout decision).
- **Architecture** (`architecture.md`) — § Concurrence — POS, "Intégrité des lots" line: add "détection de poste inactif (heartbeat absent)" to the release-trigger list. § Notification de Changement de Phase: add a "Déclencheur (ter)" row — the liveness reaper cancels an abandoned basket and releases its reservations **without** any SSE broadcast (same reasoning as the logout path: an untargeted `basket-cancelled` would wipe every other cashier's basket).
- **UX** (`ux-designs/…/EXPERIENCE.md`) — **not amended** (known, accepted documentation drift, same convention as 2.7 / 2.9 / 3.14 / 4.7 / 5.8). Note for a future grouped UX pass: a cashier whose terminal was reaped after a long disconnection gets a fresh empty basket on their next action (same as after an explicit logout / phase change).
- **DB migration** — **new** `036-basket-last-seen.xml`: one nullable column `baskets.last_seen_at DATETIME NULL` (set on basket creation and refreshed by every POS action + the heartbeat; nullable only so the `addColumn` on existing rows is legal — code always writes it). No new table, no FK, no index required for the v1 volume (~a handful of active baskets).
- **i18n** — none. The reaped-basket case reuses existing "basket not found" / re-fetch behaviour, exactly like the logout decision (UX-DR21, no dedicated string).
- **Backend config** — `application.properties`: `pos.basket.heartbeat.dead-threshold` (default `PT3M`), `pos.basket.reaper.interval` (default `PT2M`), `pos.basket.reaper.enabled` (default `true`, set `false` in the test profile). First use of `@EnableScheduling` in the project.
- **Sprint status** (`implementation-artifacts/sprint-status.yaml`) — new entry `4-9-…: backlog` under Epic 4 + dated comment block; `last_updated` bumped.
- **No impact**: CI/CD, IaC, monitoring, print pipeline, public API contracts (the heartbeat endpoint is a new POS route under the existing `/api/pos/**` security scope, not a contract change to an existing endpoint).

### Technical impact (summary)

| Area | Main files |
|---|---|
| Data model | `domain/pos/entity/Basket.java` (+ `lastSeenAt`) ; `db/changelog/036-basket-last-seen.xml` (**new**) ; `db.changelog-master.xml` (include) |
| Heartbeat (server) | `domain/pos/controller/PosBasketController.java` (+ `POST /api/pos/baskets/{id}/heartbeat` → 204) ; `domain/pos/service/PosBasketService.java` (`recordHeartbeat(basketId, userId)` — owned-basket + Sale-phase guard, sets `lastSeenAt = now` ; `getOrCreateCurrentBasket` / `addItem` / `removeItem` / `removeLot` also refresh `lastSeenAt`) |
| Reaper (server) | new `domain/pos/service/BasketReaperService.java` — `@Scheduled(fixedDelayString = "${pos.basket.reaper.interval}")`, guarded by `pos.basket.reaper.enabled` ; queries `BasketRepository` for active baskets with `lastSeenAt < now - deadThreshold` ; per basket → `basketCancellationService.cancelBasketSilently(basket)` (releases reservations + deletes basket/items, **no** SSE) ; tolerant of a basket cancelled concurrently (catch-and-continue). `PluribourseApplication` / a `@Configuration` gains `@EnableScheduling`. |
| `validate()` fix (Blind-Hunter-#7) | `domain/pos/service/PosBasketService.java` — on the committed-sale pre-check `LotAlreadySoldException`, release that lot's reservation (`releaseLot(lotId, basketId)`) before throwing, so a lot this basket can never validate is not left blocked. |
| Front | `features/volunteer/pos/pos-page.component.ts` — a liveness timer (RxJS `interval`, default 60 s) that `POST`s the heartbeat while the page is alive ; stopped in `ngOnDestroy` ; no ping while `basketCancelled()`. No new template, route, component or dependency. |
| Tests | new `PosBasketReaperIT` (E2E setup via endpoints, back-date `lastSeenAt`, invoke the reaper method directly, assert basket gone + `reserved_by_basket_id` NULL — documented bend of E2E-by-controller, same class as `SaleConcurrencyIT`) ; `PosBasketIT` (+ heartbeat refreshes `lastSeenAt` ; a fresh basket is not reaped ; `validate()` pre-check releases the rejected lot) ; `pos-page.component.spec.ts` (timer fires the heartbeat, stops on destroy, silent on `basketCancelled`). |

---

## 3. Recommended approach

**Direct adjustment** (checklist Option 1) via **one new Story `4-9` in Epic 4**, plus incremental mirror-note updates to Stories 4.8 / 2.8 — same pattern as SCP 2026-09-03.

- **No rollback of Story 4.8**: the reservation mechanism is correct; it only lacked a "terminal is gone" detector. 4.9 adds that detector, forward-only.
- **No MVP review**: 6 epics intact, no scope reduction. FR-110's "lifetime bounded by the session" clause becomes actually enforced.
- **One story, not several**: the endpoint, the column, the front timer and the reaper are one indivisible mechanism — shipping any subset leaves the gap open.

**Effort: Medium. Risk: Low-Medium.**
- Sensitive point #1: first `@Scheduled` job in the project. `@EnableScheduling` must be added, and the reaper must be inert in the IT suite (`pos.basket.reaper.enabled=false` in the test profile; the IT invokes the method directly).
- Sensitive point #2: threshold tuning against a flaky venue network. A cashier who loses wifi past the dead-threshold comes back to an emptied basket and re-scans — acceptable for "walked away / crashed", annoying for "wifi blip". Defaults are config, not code.
- Sensitive point #3: the reaper must tolerate a basket cancelled between its query and its `cancelBasketSilently` call (concurrent `validate()` / logout / phase change).
- Removing nothing; adding a bounded, well-isolated mechanism. The `BasketCancellationService` it reuses is already delivered and tested by 4.8.

**Out of scope / possible later:** `navigator.sendBeacon` on `pagehide` for instant cleanup on a *clean* tab close (shortens the window only for that one case; nothing on a crash). Kept out to keep the story tight — can be a small follow-up.

---

## 4. Detailed change proposals

> Recommended "frozen design points" are marked **[proposed]**; genuinely open items are in §5.

### Group A — PRD (`prds/prd-PluriBourse-2026-06-08/prd.md`)

**P-PRD1 — FR-110 (amend)** — l.233

- OLD (tail clause):
  > … L'annulation du panier sur **expiration** de session par inactivité (FR-066) n'est pas traitée par cette itération — suite possible, à réévaluer après livraison. *(2026-09-03, voir `sprint-change-proposal-2026-09-03.md`.)*
- NEW (tail clause):
  > … Au-delà de la déconnexion explicite, la durée de vie du panier est **effectivement bornée** par une détection de poste inactif : la page caisse émet un signal de vie périodique (heartbeat) tant qu'elle est ouverte, et un balayage serveur annule tout panier actif dont le signal s'est tu depuis un seuil configurable (poste fermé, planté, réseau perdu, ou session expirée par inactivité — FR-066), libérant ses réservations de lot. *(Détection de poste inactif : 2026-09-04, voir `sprint-change-proposal-2026-09-04.md`.)*

**P-PRD2 — FR-066 (amend)** — l.286

- OLD (tail):
  > … En revanche, **après 1 heure d'inactivité, la session expire et l'utilisateur est déconnecté automatiquement** (`spring.session.timeout=PT1H`) : c'est un choix de sécurité délibéré pour les postes bénévoles partagés en libre accès dans la salle. *(Amendé 2026-09-03, …)*
- NEW (tail):
  > … En revanche, **après 1 heure d'inactivité, la session expire et l'utilisateur est déconnecté automatiquement** (`spring.session.timeout=PT1H`) : c'est un choix de sécurité délibéré pour les postes bénévoles partagés en libre accès dans la salle. Un panier de caisse actif laissé sur un poste ainsi expiré (comme sur un poste fermé ou planté) est **annulé et ses réservations de lot libérées** par la détection de poste inactif (FR-110). *(Amendé 2026-09-03, puis 2026-09-04, voir `sprint-change-proposal-2026-09-04.md`.)*

### Group B — Epics: FR mirror lines & coverage map (`epics.md`)

**P-EP1 — FR-110 mirror line (l.85, "F4 bis — Lots en caisse")** — replace the tail
- OLD tail: `… L'annulation du panier sur expiration de session par inactivité (FR-066) est laissée en suite possible, à réévaluer après livraison. *(SCP 2026-09-03 ; clause SSE corrigée par la Story 4.8)*`
- NEW tail: `… Au-delà de la déconnexion explicite, un panier abandonné (poste fermé, planté, réseau perdu, session expirée) est annulé et ses réservations libérées par une détection de poste inactif : heartbeat émis par la page caisse + balayage serveur (Story 4.9). *(SCP 2026-09-03 ; clause SSE corrigée par la Story 4.8 ; détection de poste inactif SCP 2026-09-04)*`

**P-EP2 — FR-110 coverage-map line (l.307)** — replace the tail
- OLD tail: `… les autres onglets du même utilisateur sont déconnectés (session invalidée), pas de `basket-cancelled` dédié *(SCP 2026-09-03 ; clause SSE corrigée par la Story 4.8)*`
- NEW tail: `… les autres onglets du même utilisateur sont déconnectés (session invalidée), pas de `basket-cancelled` dédié ; un panier abandonné sans déconnexion explicite est récupéré par la détection de poste inactif (heartbeat + balayage, Story 4.9) *(SCP 2026-09-03 ; clause SSE Story 4.8 ; poste inactif SCP 2026-09-04)*`

**P-EP3 — FR-066 mirror lines (l.123 "F7" and l.281 coverage map)** — append one clause
- l.123 append: ` Un panier de caisse laissé sur une session ainsi expirée est annulé et ses réservations de lot libérées (détection de poste inactif, FR-110 / Story 4.9). *(Complété SCP 2026-09-04.)*`
- l.281 append: `; un panier abandonné sur une session expirée est récupéré par la détection de poste inactif (Story 4.9) *(complété SCP 2026-09-04)*`

**P-EP4 — FR-109 mirror lines (l.93, l.261) & architecture "Intégrité des lots"** — add one release trigger
- Insert into the release-trigger enumeration (after "…/ déconnexion"): `/ détection de poste inactif (heartbeat absent, Story 4.9)`.

**P-EP5 — Story 2.8 note (l.1028)** — extend
- Append to the existing SCP-2026-09-03 note: ` La routine silencieuse `cancelBasketSilently` est également invoquée par le balayage de postes inactifs (Story 4.9). *(Complété SCP 2026-09-04.)*`

**P-EP6 — Story 4.8, `### Review Findings` W5** — replace "story de suivi … à créer via correct-course" with "→ Story 4.9 (SCP 2026-09-04)". (Bookkeeping; done when 4.9 is created.)

**P-EP7 — UX-DR21 (l.206)** — append one sentence
- Append: ` Un panier récupéré par la détection de poste inactif (Story 4.9) suit le même principe que la déconnexion : disparition silencieuse, panier vide au retour du poste, aucune chaîne i18n dédiée.`

### Group C — Architecture (`architecture.md`, § Concurrence — POS + § Notification de Changement de Phase)

**P-ARCH1 — "Intégrité des lots" line** — extend the "Libération :" enumeration
- Add: `, détection de poste inactif (heartbeat absent → balayage serveur, Story 4.9 — poste fermé / planté / réseau perdu / session expirée)`.

**P-ARCH2 — § Notification de Changement de Phase, new row after "Déclencheur (bis)"**
- NEW:
  > | Déclencheur (ter) | Le **balayage de postes inactifs** annule un panier actif dont le heartbeat s'est tu au-delà du seuil et libère ses réservations de lot (FR-110 / Story 4.9) | Réutilise `BasketCancellationService.cancelBasketSilently` — **aucun** broadcast `basket-cancelled` (même raison que la déconnexion : diffusion non ciblée) ; le poste concerné a de toute façon cessé de communiquer *(SCP 2026-09-04)* |

### Group D — DB migration (`db/changelog/`)

**P-DB1 — new `036-basket-last-seen.xml`** (created by Story 4.9, described here for scoping)
- `<addColumn tableName="baskets">`: `last_seen_at` type `DATETIME`, `nullable="true"` (nullable only to make the `addColumn` on existing rows legal; the code always writes it — on basket creation, every POS action, and each heartbeat).
- `<rollback>`: `<dropColumn tableName="baskets" columnName="last_seen_at"/>`.
- No FK, no index (v1 volume: a handful of active baskets per edition).
- `<include file="db/changelog/036-basket-last-seen.xml"/>` as the last line of `db.changelog-master.xml` (after `035`).

### Group E — Sprint status (`implementation-artifacts/sprint-status.yaml`)

**P-SS1** — add under Epic 4, immediately before `epic-4-retrospective: optional`:
```
  # Correct-course SCP 2026-09-04 : décision D2 de la revue de code de la Story 4.8. La réservation de
  # lot posée au scan (4.8) n'est libérée que par une action explicite sur le panier — un poste fermé,
  # planté, sans réseau ou en session expirée (1 h) laisse la réservation orpheline → lot invendable à
  # toutes les caisses jusqu'au changement de phase. Story 4-9 : heartbeat émis par la page caisse +
  # colonne baskets.last_seen_at (migration 036) + tâche @Scheduled (BasketReaperService) qui réutilise
  # BasketCancellationService.cancelBasketSilently (livré par 4.8) pour annuler les paniers sans signe de
  # vie et libérer leurs réservations, sans broadcast SSE. Inclut un correctif ciblé : validate() libère
  # la réservation d'un lot rejeté à son pré-check "frère vendu committé" (Blind Hunter #7). Amende
  # Stories 4.8 (note W5) et 2.8 (note : cancelBasketSilently aussi appelé par le balayage). Ferme la
  # lacune FR-066 (expiration de session → panier non annulé) laissée ouverte par la SCP 2026-09-03.
  4-9-detection-poste-caisse-inactif-et-liberation-du-panier: backlog
```
- Bump `last_updated` to `2026-09-04` with a one-line pointer to this SCP.

---

## 5. Open questions — resolved (Manerial, 2026-09-04: recommendations accepted)

1. **Dead-threshold default.** ✅ `PT3M` (≈ 3 missed 60 s heartbeats). Config `pos.basket.heartbeat.dead-threshold`, tunable without redeploy; can move to `PT5M` from config if venue wifi proves too flaky.
2. **Session vs basket.** ✅ The reaper cancels the **basket only** (releases reservations, deletes basket + items); it does **not** invalidate the session. A returning terminal gets a fresh empty basket, same as after logout / phase change.
3. **`navigator.sendBeacon` on tab close.** ✅ **Out of scope** for Story 4.9. Possible small follow-up later (instant cleanup on a clean close only).
4. **New FR-111 vs amend FR-110/FR-066.** ✅ **Amend FR-110 + FR-066**, no new FR — the mechanism enforces FR-110's existing "lifetime bounded by the session" clause.
5. **Sweep condition.** ✅ Purely timestamp-based (`last_seen_at < now - threshold`), not cross-checked against `spring_session`.

---

## 6. Handoff plan

**Scope: Moderate.** New Story `4-9` (backend endpoint + entity + migration + scheduled job + front timer + config + a small `validate()` fix) + 2 `done` stories mirror-noted. No replan, MVP intact → **no PM / Architect escalation.**

| Item | Scope | Routed to |
|---|---|---|
| Artifact edits P-PRD1–2, P-EP1–7, P-ARCH1–2, P-SS1 | Minor | Applied as working-tree edits on approval — **not committed** (Manerial manages commits). |
| Story 4.9 — heartbeat + reaper + `validate()` fix | Moderate | **PO / Dev — new story via `bmad-create-story`**, real-code analysis mandatory (`@EnableScheduling` first use, reaper inert in the IT suite, `BasketCancellationService` reuse, `Basket.lastSeenAt` refresh points, reaper-vs-cancel race tolerance, Blind-Hunter-#7 `validate()` release). |
| `navigator.sendBeacon` on tab close | Minor, **out of scope** | Possible later follow-up; not blocking. |

### Success criteria

- A POS terminal that stops sending heartbeats for longer than the dead-threshold has its active basket cancelled and every `lots.reserved_by_basket_id` it held reset to `NULL` — verifiable by a second terminal that can then scan that lot.
- The heartbeat and every real POS action refresh `baskets.last_seen_at`; an actively-used basket is **never** reaped.
- An idle-timed-out session (FR-066, 1 h) results in the same basket cleanup (no separate mechanism).
- `validate()` releases the reservation of any lot it rejects at the committed-sale pre-check, before throwing.
- The reaper emits **no** SSE broadcast.
- `./mvnw clean package` green (reaper IT invokes the scheduled method directly; reaper disabled in the test profile). `npm test` + `npm run build` green, no warning.
- No new i18n key; no UX regression on the reaped-basket path (fresh empty basket on next action).
