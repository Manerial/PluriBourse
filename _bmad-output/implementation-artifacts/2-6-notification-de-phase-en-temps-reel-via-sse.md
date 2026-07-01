---
baseline_commit: 4ef5883df41ddd36b24192d8af54066f62e9c5d0
---

# Story 2.6: Real-Time Phase Notification via SSE

Status: done

## Story

As a volunteer or administrator,
I want the phase chip in the top bar to update in real time when the phase changes,
so that I always know the current phase without reloading the page.

## Acceptance Criteria

1. **Given** any authenticated user (admin or volunteer) is logged in, **When** the app layout renders, **Then** the phase chip displays the current active edition's phase (PREPARATION/DEPOSIT/SALE/POST_SALE) — or "No active edition" if no edition is in an active phase.

2. **Given** an admin advances the phase, **When** the SSE event `phase-changed` is received by any connected client, **Then** the phase chip updates within 2 seconds. *(Amended 2026-07-01: the 150ms fade transition originally specified here was dropped by product decision during the second review pass — a subtle fade adds no value; phase transitions will instead get a mandatory blocking popup + forced navigation in a future story, see "Spun off into new stories" below.)*

3. **Given** the active edition transitions to `CLOSED`, **When** the SSE event is received, **Then** the chip displays "No active edition" (CLOSED is not an active phase — same as no edition).

4. **Given** the SSE connection drops and reconnects (e.g., after the server closes it post-broadcast), **When** the `EventSource` auto-reconnects, **Then** no user action is needed — the chip retains its last known state and updates on the next event.

5. **Given** the user is an admin and an active edition exists, **When** the phase chip is rendered, **Then** it is a clickable link that navigates to `/admin/editions/{id}/phase` (the phase control page for the current edition).

6. **Given** the user is a volunteer, **When** the phase chip is rendered, **Then** it is a non-clickable span regardless of edition state.

7. **Given** `GET /api/editions/current` is called by any authenticated non-SELLER user, **When** an edition is in an active phase, **Then** it returns 200 + the full `EditionDto`. **When** no edition is active, **Then** it returns 204 No Content.

8. **Given** the layout initializes, **When** `ngOnInit` runs, **Then** an HTTP call is made to `GET /api/editions/current` to load the initial state before any SSE event arrives.

## Tasks / Subtasks

- [x] **T1 — Backend: EditionRepository — new query** (AC: 7)
  - [x] T1.1 — Add `Optional<Edition> findFirstByPhaseIn(List<PhaseType> phases)` to `EditionRepository`

- [x] **T2 — Backend: EditionService — getCurrentEdition** (AC: 7)
  - [x] T2.1 — Add `getCurrentEdition(): Optional<EditionDto>` method using `findFirstByPhaseIn(PhaseType.ACTIVE)` and mapping with `mapper::toDto`

- [x] **T3 — Backend: CurrentEditionController** (AC: 7, 8)
  - [x] T3.1 — Create `org.pluribourse.edition.controller.CurrentEditionController` at `/api/editions/current` (see Dev Notes)
  - [x] T3.2 — `GET /api/editions/current`: returns 200 + EditionDto if active edition found, 204 No Content otherwise. No `@PreAuthorize` — the `anyRequest()` rule in `SecurityConfig` already restricts to authenticated non-SELLER users

- [x] **T4 — Backend: Integration test** (AC: 7, 8)
  - [x] T4.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/CurrentEditionIT.java`
  - [x] T4.2 — `@Order(1)` — `GET /api/editions/current` with admin session returns 204 when no edition exists
  - [x] T4.3 — `@Order(2)` — create an edition (admin), then `GET /api/editions/current` returns 200 + edition in PREPARATION
  - [x] T4.4 — `@Order(3)` — volunteer session returns 200 (edition active in PREPARATION)
  - [x] T4.5 — `@Order(4)` — advance to CLOSED (4 advances), then `GET /api/editions/current` returns 204

- [x] **T5 — Frontend: PhaseChangedEvent interface** (AC: 2, 3)
  - [x] T5.1 — Add to `pluribourse-frontend/src/app/models/edition.model.ts`:
    ```typescript
    export interface PhaseChangedEvent {
      editionId: number;
      newPhase: PhaseType;
      previousPhase: PhaseType;
    }
    ```

- [x] **T6 — Frontend: SseService** (AC: 2, 3, 4)
  - [x] T6.1 — Create `pluribourse-frontend/src/app/services/sse.service.ts` (see Dev Notes)
  - [x] T6.2 — Create `pluribourse-frontend/src/app/services/sse.service.spec.ts` (see Dev Notes)

- [x] **T7 — Frontend: CurrentEditionService** (AC: 1, 2, 3, 5, 8)
  - [x] T7.1 — Create `pluribourse-frontend/src/app/services/current-edition.service.ts` (see Dev Notes)
  - [x] T7.2 — Create `pluribourse-frontend/src/app/services/current-edition.service.spec.ts` (see Dev Notes)

- [x] **T8 — Frontend: AppLayoutComponent — TypeScript** (AC: 1, 2, 3, 4, 5, 6, 8)
  - [x] T8.1 — Inject `CurrentEditionService`, `SseService`, `DestroyRef` into `AppLayoutComponent`
  - [x] T8.2 — In `ngOnInit`, call `currentEditionService.load()` and subscribe with `takeUntilDestroyed`
  - [x] T8.3 — In `ngOnInit`, subscribe to `sseService.phaseChanges()` with `takeUntilDestroyed`, calling `currentEditionService.updateFromEvent(event)` on each event
  - [x] T8.4 — Expose `currentEditionService.currentEdition` as a computed / direct signal reference for the template
  - [x] T8.5 — Add `readonly isAdmin` computed already exists — add `readonly activeEditionId = computed(() => this.currentEditionService.currentEdition()?.id ?? null)` for the admin chip link

- [x] **T9 — Frontend: AppLayoutComponent — HTML** (AC: 1, 2, 3, 5, 6)
  - [x] T9.1 — Replace the hardcoded phase chip block (lines 9–15 of current `app-layout.component.html`) with a dynamic version (see Dev Notes)

- [x] **T10 — Frontend: i18n keys** (AC: 1, 3)
  - [x] T10.1 — Add to `public/i18n/en.json` under `nav.phase`:
    ```json
    "none": "No active edition"
    ```
  - [x] T10.2 — Add to `public/i18n/fr.json` under `nav.phase`:
    ```json
    "none": "Aucune édition en cours"
    ```

- [x] **T11 — Frontend: AppLayoutComponent spec** (AC: 1, 5, 6)
  - [x] T11.1 — Add mock for `CurrentEditionService` to `TestBed` providers (see Dev Notes)
  - [x] T11.2 — Add test: when `currentEdition()` is null, chip shows `nav.phase.none` key text and no link
  - [x] T11.3 — Add test: when `currentEdition()` has phase DEPOSIT and user is admin, chip is an `<a>` with href matching `/admin/editions/{id}/phase`
  - [x] T11.4 — Add test: when user is volunteer, chip is a `<span>` (not an `<a>`) even when edition is active
  - [x] T11.5 — Run `npm test` — all tests must pass

## Dev Notes

### T3 — CurrentEditionController

```java
package org.pluribourse.edition.controller;

import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.service.EditionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/editions")
@RequiredArgsConstructor
public class CurrentEditionController {

    private final EditionService editionService;

    @GetMapping("/current")
    public ResponseEntity<EditionDto> getCurrentEdition() {
        return editionService.getCurrentEdition()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
```

No `@PreAuthorize` — `SecurityConfig.anyRequest()` already covers this: authenticated non-SELLER only. Both ADMIN and VOLUNTEER can call this endpoint.

### T4 — Integration Test Setup

For `@Order(4)` (volunteer access), use `volunteer1`'s session. The test must first log in as volunteer, then call `GET /api/editions/current`. Note: at this point in the scenario the edition is CLOSED — so `@Order(4)` should call after rolling back to an active phase. Adjust the scenario order if needed, or create a separate rollback step.

Alternative simpler scenario for `@Order(4)`: after creating the edition in `@Order(2)`, immediately test volunteer access with a fresh session (still in PREPARATION).

### T6 — SseService

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PhaseChangedEvent } from '../models/edition.model';

@Injectable({ providedIn: 'root' })
export class SseService {

  phaseChanges(): Observable<PhaseChangedEvent> {
    return new Observable(observer => {
      const source = new EventSource('/api/sse/events', { withCredentials: true });
      source.addEventListener('phase-changed', (event: MessageEvent) => {
        try {
          observer.next(JSON.parse(event.data) as PhaseChangedEvent);
        } catch {
          // malformed event — ignore
        }
      });
      source.onerror = () => {
        // EventSource reconnects automatically per the SSE spec — do not error the Observable
        // The chip retains its last known state and will update on the next event
      };
      return () => source.close();
    });
  }
}
```

**`withCredentials: true` is required** — the browser must send the JSESSIONID session cookie for the SSE request to be authenticated. Without it, Spring Security returns 401 and the EventSource fails silently.

**EventSource reconnect behavior:** The `SseEmitterRegistry` closes each emitter after broadcast (`emitter.complete()`). The browser's `EventSource` detects the connection closing and reconnects automatically after ~3 seconds (the default SSE retry delay). The `onerror` handler fires during reconnect — this is expected and must NOT propagate as an Observable error (which would terminate the Observable). The chip retains its last known state between reconnects.

**Spec for SseService:** Use `vi.stubGlobal('EventSource', ...)` to mock `EventSource`. Verify that:
- `phaseChanges()` creates an `EventSource` with `withCredentials: true`
- A `phase-changed` event with valid JSON emits the parsed event to subscribers
- Unsubscribing calls `source.close()`

### T7 — CurrentEditionService

```typescript
import { inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { EditionDto, PhaseChangedEvent } from '../models/edition.model';

const ACTIVE_PHASES = new Set(['PREPARATION', 'DEPOSIT', 'SALE', 'POST_SALE']);

@Injectable({ providedIn: 'root' })
export class CurrentEditionService {
  private readonly http = inject(HttpClient);

  readonly currentEdition = signal<EditionDto | null>(null);

  load(): Observable<EditionDto | null> {
    return this.http.get<EditionDto>('/api/editions/current', { observe: 'response' }).pipe(
      tap(response => {
        this.currentEdition.set(response.status === 200 ? response.body : null);
      }),
      // Map to the body for callers who don't need the response envelope
      // tap is enough — no need to expose the HttpResponse externally
    ) as Observable<any>;
  }

  updateFromEvent(event: PhaseChangedEvent): void {
    const current = this.currentEdition();
    if (!ACTIVE_PHASES.has(event.newPhase)) {
      this.currentEdition.set(null);
      return;
    }
    if (current && current.id === event.editionId) {
      this.currentEdition.set({ ...current, phase: event.newPhase });
    }
  }
}
```

**`load()` return type note:** The `HttpClient.get` with `observe: 'response'` returns `Observable<HttpResponse<EditionDto>>`. Use `tap` to update the signal, then the caller just subscribes without needing the value. Alternatively simplify: use two separate HTTP strategies (one for 204 detection). See Dev Notes alternative below.

**Simpler alternative for `load()`:**
```typescript
load(): void {
  this.http.get<EditionDto>('/api/editions/current', { observe: 'response' })
    .subscribe(response => {
      this.currentEdition.set(response.status === 200 ? response.body : null);
    });
}
```
The component calls `this.currentEditionService.load()` in `ngOnInit` without subscribing — the service manages its own subscription. This is simpler but less testable. Prefer the Observable version for testability.

**`updateFromEvent` logic:**
- If `newPhase` not in ACTIVE_PHASES (e.g., CLOSED) → set to null
- If `newPhase` is active and `editionId` matches current → update phase in-place (spread)
- If `editionId` doesn't match current (edge case: rollback opened a different edition) → call `load()` to resync

### T8/T9 — AppLayoutComponent Changes

**TypeScript additions:**
```typescript
// New injections
private readonly currentEditionService = inject(CurrentEditionService);
private readonly sseService = inject(SseService);
private readonly destroyRef = inject(DestroyRef);

// New signal ref
readonly currentEdition = this.currentEditionService.currentEdition;
readonly activeEditionId = computed(() => this.currentEditionService.currentEdition()?.id ?? null);

ngOnInit(): void {
  this.currentEditionService.load().pipe(
    takeUntilDestroyed(this.destroyRef)
  ).subscribe();

  this.sseService.phaseChanges().pipe(
    takeUntilDestroyed(this.destroyRef)
  ).subscribe(event => this.currentEditionService.updateFromEvent(event));
}
```

Implement `OnInit` interface.

**HTML — replace the static chip block:**
```html
<div class="topbar__center">
  @if (currentEdition()) {
    @if (isAdmin()) {
      <a [routerLink]="['/admin/editions', activeEditionId(), 'phase']"
         class="phase-chip"
         [attr.aria-label]="'nav.phase.current' | translate">
        <span class="phase-chip__dot" aria-hidden="true">●</span>
        {{ ('edition.phase.' + currentEdition()!.phase) | translate }}
      </a>
    } @else {
      <span class="phase-chip" [attr.aria-label]="'nav.phase.current' | translate">
        <span class="phase-chip__dot" aria-hidden="true">●</span>
        {{ ('edition.phase.' + currentEdition()!.phase) | translate }}
      </span>
    }
  } @else {
    <span class="phase-chip phase-chip--inactive">
      {{ 'nav.phase.none' | translate }}
    </span>
  }
</div>
```

Note: `RouterLink` must be added to `AppLayoutComponent` imports (it may already be there — check the current imports list).

**CSS — add the inactive variant and fade transition** (in `app-layout.component.scss`):
```scss
.phase-chip {
  transition: opacity 150ms ease; // UX-DR4: fade 150ms on update
}

.phase-chip--inactive {
  opacity: 0.5; // visual distinction: muted, no coral dot
}
```

### T11 — Spec Mock

In `app-layout.component.spec.ts`, add a mock for `CurrentEditionService`:

```typescript
import { signal } from '@angular/core';
import { CurrentEditionService } from '../../services/current-edition.service';

// In beforeEach providers:
const mockEdition = signal<EditionDto | null>(null);
const mockCurrentEditionService = {
  currentEdition: mockEdition,
  load: vi.fn().mockReturnValue(of(null)),
  updateFromEvent: vi.fn(),
};

// Add to providers:
{ provide: CurrentEditionService, useValue: mockCurrentEditionService }
```

Also add `SseService` mock that returns `EMPTY` for `phaseChanges()`:
```typescript
import { EMPTY } from 'rxjs';
{ provide: SseService, useValue: { phaseChanges: () => EMPTY } }
```

**Existing test `'renders the phase chip'`** — currently asserts `.phase-chip` exists. This will still pass since the `@else` branch always renders `.phase-chip`. No change needed for this test.

### Project Structure Notes

- New backend controller: `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/CurrentEditionController.java`
- New backend IT: `pluribourse-backend/src/test/java/org/pluribourse/edition/CurrentEditionIT.java`
- New frontend service: `pluribourse-frontend/src/app/services/sse.service.ts` + `.spec.ts`
- New frontend service: `pluribourse-frontend/src/app/services/current-edition.service.ts` + `.spec.ts`
- Modified: `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` + `.html` + `.spec.ts`
- Modified: `pluribourse-frontend/src/app/models/edition.model.ts` (add `PhaseChangedEvent`)
- Modified: `pluribourse-frontend/public/i18n/en.json` + `fr.json` (add `nav.phase.none`)
- SSE endpoint `/api/sse/events` is already accessible to volunteers (not under `/api/admin/**`) ✅
- New endpoint `/api/editions/current` must NOT be under `/api/admin/**` ✅

### References

- [Source: app-layout.component.html:10-14] Static chip to replace
- [Source: shared/sse/SseEmitterRegistry.java] Closes emitter after each broadcast — EventSource auto-reconnects
- [Source: shared/sse/PhaseChangedEventDto.java] `{editionId, newPhase, previousPhase}` — maps to `PhaseChangedEvent` TS interface
- [Source: edition/entity/PhaseType.java#ACTIVE] Active phases constant — `List.of(PREPARATION, DEPOSIT, SALE, POST_SALE)`
- [Source: edition/repository/EditionRepository.java] Existing `existsByPhaseIn` — add `findFirstByPhaseIn`
- [Source: shared/security/SecurityConfig.java] `anyRequest()` covers non-admin authenticated users ✅

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- SseService spec: `vi.fn(() => mockSource)` uses arrow function → not a valid constructor for `new EventSource(...)`. Fixed with `vi.fn(function() { return instance; })`.
- CurrentEditionIT ordering: volunteer access test placed at @Order(3) while edition is still in PREPARATION (active), before the advance-to-CLOSED sequence in @Order(4).

### Completion Notes List

- Backend: Added `findFirstByPhaseIn` to `EditionRepository`, `getCurrentEdition()` to `EditionService`, and new `CurrentEditionController` at `GET /api/editions/current` (200 or 204). No `@PreAuthorize` needed — `SecurityConfig.anyRequest()` already blocks SELLER and anonymous. 130/130 backend tests pass.
- Frontend: `PhaseChangedEvent` interface in edition.model.ts; `SseService` (EventSource with `withCredentials: true`, auto-reconnect via `onerror` no-op); `CurrentEditionService` (signal + `load()` + `updateFromEvent()`); `AppLayoutComponent` wired with `ngOnInit`, `takeUntilDestroyed`; dynamic chip template (admin link vs volunteer span vs inactive span); i18n `nav.phase.none` added in en/fr. 186/186 frontend tests pass.

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java` — modified
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java` — modified
- `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/CurrentEditionController.java` — created
- `pluribourse-backend/src/test/java/org/pluribourse/edition/CurrentEditionIT.java` — created
- `pluribourse-frontend/src/app/models/edition.model.ts` — modified
- `pluribourse-frontend/src/app/services/sse.service.ts` — created
- `pluribourse-frontend/src/app/services/sse.service.spec.ts` — created
- `pluribourse-frontend/src/app/services/current-edition.service.ts` — created
- `pluribourse-frontend/src/app/services/current-edition.service.spec.ts` — created
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` — modified
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — modified
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss` — modified
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` — modified
- `pluribourse-frontend/public/i18n/en.json` — modified
- `pluribourse-frontend/public/i18n/fr.json` — modified

### Review Findings

- [x] [Review][Patch] **(resolved from Decision)** SSE permanent failure (EventSource `readyState === CLOSED`, e.g. after session expiry) is never recovered from — `source.onerror` is a total no-op. Fixed: `SseService.onerror` now detects `readyState === EventSource.CLOSED` and triggers `clearSession()` + `router.navigate(['/login'])`. Also applied the related config change: `spring.session.timeout` `PT8H` → `PT1H` in `application.properties`. [pluribourse-frontend/src/app/services/sse.service.ts]
- [x] [Review][Dismiss] **(resolved from Decision)** `updateFromEvent`'s CLOSED branch nulls `currentEdition` unconditionally without checking `event.editionId` — dismissed: this scenario requires two simultaneously active editions, which depends entirely on the already-deferred Story 2.1 race condition ("Race condition création concurrente d'édition"), not a new issue introduced here. The SSE delivery model (one event per connection, connection closed immediately after send) makes reordering impossible for a single client otherwise. [pluribourse-frontend/src/app/services/current-edition.service.ts:717-728]
- [x] [Review][Patch] **(resolved from Decision)** AC2's "150ms fade transition" only fires on the active/inactive opacity toggle, not on phase-to-phase text changes. Fixed: removed the fade entirely — text updates immediately with no transition. Product decision: a subtle fade is not useful for this app; phase transitions will instead get a mandatory blocking popup + forced navigation in a future story (see below), making the fade moot. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss]
- [x] [Review][Patch] No test asserts the actual rendered/translated phase chip text (only tag name, CSS class, href are checked) — AC8's translated-text rendering is implemented but unverified by tests. Fixed: added a test with an inline `setTranslation` and an assertion on the rendered chip text. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts]
- [x] [Review][Patch] Concurrent/in-flight `load()` HTTP requests are never cancelled — `updateFromEvent`'s resync path and `ngOnInit`'s initial call can race, letting a stale response overwrite fresher state. Fixed: `loadEdition()` tracks a monotonic request id and discards any response that isn't from the latest call. [pluribourse-frontend/src/app/services/current-edition.service.ts]
- [x] [Review][Patch] `load()` has no error handling — a failed HTTP call (401/5xx/network) is an uncaught observable error, leaving `currentEdition` stuck on stale data with nothing surfaced. Fixed: `catchError(() => EMPTY)` swallows the error gracefully; `authInterceptor` already handles the 401 redirect upstream. [pluribourse-frontend/src/app/services/current-edition.service.ts]
- [x] [Review][Patch] **(reverted 2026-07-01, see second pass)** `findFirstByPhaseIn` has no explicit ordering — if the single-active-edition invariant is ever violated, which row is "first" is undefined/non-deterministic behavior. Originally fixed by renaming to `findFirstByPhaseInOrderByCreatedAtDesc`, then reverted: the invariant is meant to always hold, and defending against its violation here treats a symptom of the already-deferred Story 2.1 race condition rather than the cause. [pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java]
- [x] [Review][Patch] `SseService.phaseChanges()` only guards against JSON parse *syntax* errors — a validly-parsed but non-object payload (e.g. `"42"`) passes through and produces a malformed `PhaseChangedEvent` silently misinterpreted downstream. Fixed: added an `isPhaseChangedEvent` type guard before emitting. [pluribourse-frontend/src/app/services/sse.service.ts]
- [x] [Review][Patch] T8.2 deviation — `load()` is fire-and-forget (subscribes internally) instead of returning an `Observable` piped through `takeUntilDestroyed` in the component, as both the task text and Dev Notes' stated preference call for. Fixed: `loadEdition()` returns `Observable<void>`, subscribed in `ngOnInit` via `takeUntilDestroyed`. [pluribourse-frontend/src/app/services/current-edition.service.ts, pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts]
- [x] [Review][Patch] Missing test coverage for AC8 — nothing asserts that `currentEditionService.load()` is actually invoked during `ngOnInit`. Fixed: added an assertion. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts]
- [x] [Review][Patch] Inactive phase chip (`<span class="phase-chip phase-chip--inactive">`) has no `aria-label`, unlike the active-edition variants — inconsistent accessibility treatment of the same UI element. Fixed. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html]
- [x] [Review][Patch] Template repeats `currentEdition()!` with non-null assertions three times instead of binding once via `@if (currentEdition(); as edition)`. Fixed. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html]
- [x] [Review][Patch] Unused `of` import in test file (only `EMPTY` is used). Resolved naturally — `of` is now used to mock `loadEdition()`'s Observable return value. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts]
- [x] [Review][Patch] Code comment cites "RFC 8895" for SSE auto-reconnect behavior — SSE is a WHATWG HTML living-standard feature, not governed by an IETF RFC; citation appears fabricated and should be corrected or removed. Fixed. [pluribourse-frontend/src/app/services/sse.service.ts]
- [x] [Review][Defer] `SseService.phaseChanges()` creates a new `EventSource` per subscriber with no multicast/sharing — harmless with today's single consumer, but a footgun if a second consumer is added later — deferred, pre-existing pattern risk, not a current bug
- [x] [Review][Defer] No fallback UI for an unmapped/future `PhaseType` value — if backend and frontend ever drift, the chip would show a raw untranslated i18n key — deferred, low-severity robustness gap

**Dismissed as noise (5):** i18n key mismatch `nav.phase` vs `edition.phase` (false positive — Blind Hunter had no project context; `edition.phase.*` keys already exist, confirmed by Acceptance Auditor and Edge Case Hunter with project read access) · missing negative-path security test for SELLER/anonymous rejection (contradicts CLAUDE.md testing philosophy — Spring Security config is explicitly not tested in isolation) · no resync of missed events after SSE reconnect (matches AC4 exactly by design, not a bug) · duplicated diff hunks (artifact of the reviewer's diff-capture process, not a real repo issue) · `updateFromEvent` CLOSED-branch editionId race (depends on already-deferred Story 2.1 race condition, not new)

**Spun off into new stories (out of scope for this diff, discussed during review):**
- Force-invalidate volunteer sessions server-side when the edition transitions to `CLOSED` (symmetric to Story 2.3's login-blocking behavior; requires `FindByIndexNameSessionRepository`-based session lookup, not yet implemented anywhere)
- Replace the phase-chip ambient notification with a mandatory blocking popup ("l'édition est désormais en phase [PHASE]") + forced navigation on every phase change, across all routes

### Review Findings — Second Pass (2026-07-01)

- [x] [Review][Decision] **(resolved: amend AC)** AC2's "150ms fade transition" was dropped in code without amending the AC text. Resolved 2026-07-01: AC2 text amended in place to drop the fade requirement and document the product decision (see Acceptance Criteria above). [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss]
- [x] [Review][Defer] Stale in-flight `loadEdition()` resync can overwrite a newer optimistic SSE update — the `latestRequestId` staleness guard only orders `loadEdition()` HTTP calls against each other; it does not account for `updateFromEvent`'s in-place optimistic branch. If a resync `loadEdition()` (triggered by an editionId mismatch) is in flight when a later SSE event lands and applies its optimistic update, the resync's stale response can subsequently overwrite the newer optimistic phase. Deferred: low risk, narrow race window for a non-critical ambient-notification feature; AC4 already accepts "chip retains last known state... updates on next event." [pluribourse-frontend/src/app/services/current-edition.service.ts]
- [x] [Review][Patch] `aria-label="nav.phase.current"` (a static "Current phase" string) overrides the dynamic, visible phase text for screen readers on both the admin `<a>` and volunteer `<span>` chip variants — ARIA accessible-name computation prefers `aria-label` over text content, so assistive tech always announces "Current phase," never the actual phase name. Fixed: removed the static `aria-label` from both variants — visible text content now serves as the accessible name. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html]
- [x] [Review][Patch] `isPhaseChangedEvent` type guard only checks that `editionId`/`newPhase` keys exist — it doesn't validate `previousPhase` is present nor that `newPhase` is a real `PhaseType` value. A payload like `{editionId:1, newPhase:"BOGUS"}` passes the guard, then silently nulls `currentEdition` downstream via the `ACTIVE_PHASES.has()` miss. Fixed: added an `isPhaseType` helper validating both `newPhase` and `previousPhase` against the full `PhaseType` value set. Refined 2026-07-01 after challenge from Manerial: the phase-values list was initially hardcoded inline in `sse.service.ts`, duplicating the same kind of data as `ActivePhase`; moved to a new `ALL_PHASES` export in `pluribourse-frontend/src/app/models/active-phase.enum.ts` (derived from `ActivePhase` + `CLOSED`), grouping all phase-set constants in one file. [pluribourse-frontend/src/app/services/sse.service.ts, pluribourse-frontend/src/app/models/active-phase.enum.ts]
- [x] [Review][Patch] No fallback branch in the template when `currentUser` role is neither ADMIN nor VOLUNTEER (`@if (isAdmin()) {...} @else if (isVolunteer()) {...}` has no final `@else`) — the chip silently disappears instead of degrading to a safe read-only view. Fixed: replaced `@else if (isVolunteer())` with an unconditional `@else`, so any non-admin role (including an unexpected one) gets the read-only span. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html]
- [x] [Review][Patch] **(reverted 2026-07-01)** `findFirstByPhaseInOrderByCreatedAtDesc` has no tie-breaker for editions sharing an identical `createdAt` (millisecond-resolution collision) — ordering becomes non-deterministic in that case. Initially fixed by renaming to `findFirstByPhaseInOrderByCreatedAtDescIdDesc` with `id DESC` as secondary sort key, then reverted after challenge from Manerial: the tie-breaker only matters if two active editions coexist, which the invariant forbids and which is already tracked as the Story 2.1 race condition defer — defending against it here treats the symptom in the wrong place. Reverted all the way to plain `findFirstByPhaseIn` (no ordering at all), since a real single-active-edition invariant makes any ordering moot; the actual fix belongs to Story 2.1's partial unique DB constraint. [pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java]
- [x] [Review][Patch] `SseService`'s permanent-failure handler (`readyState === CLOSED`) calls `auth.clearSession()` but never resets `CurrentEditionService.currentEdition` — stale edition/phase data from the previous session can leak into the next login screen before `loadEdition()` resolves. Fixed: `SseService` now injects `CurrentEditionService` and sets `currentEdition.set(null)` alongside `clearSession()`. [pluribourse-frontend/src/app/services/sse.service.ts]
- [x] [Review][Patch] The "no active edition" chip test only asserts tag name and CSS class, never `chip.textContent` — AC1's "No active edition" text output remains unverified by any test, unlike the active-edition case which does assert rendered text. Fixed: added `nav.phase.none` to the test's inline translation dict and asserted `chip.textContent`. [pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts]
- [x] [Review][Defer] `loadEdition()`'s `catchError(() => EMPTY)` silently swallows every non-401 HTTP error (5xx, network failures) with no logging and no user-facing indication — the chip freezes on stale data indefinitely unless a later SSE event happens to trigger a resync — deferred, pre-existing risk-accepted trade-off from the prior review round; would need product input on whether to surface a degraded-state indicator via the existing `ToastContainerComponent`. [pluribourse-frontend/src/app/services/current-edition.service.ts]

**Dismissed as noise (13):** missing `edition.phase.*` i18n keys (false positive — keys exist at the top level of en/fr.json, Blind Hunter had no project context) · `SseService` opens one `EventSource` per subscriber, no multicast (duplicate of already-deferred pattern risk from pass 1) · "inconsistent" session-expiry handling between `SseService` (redirects on CLOSED) and `CurrentEditionService` (no-ops on 401) — false positive, `authInterceptor` already handles 401 globally for all `HttpClient` calls including `loadEdition()`; `EventSource` bypasses Angular interceptors entirely, which is exactly why `SseService` needs its own handler · missing negative-path security test for SELLER/anonymous rejection (contradicts CLAUDE.md: Spring Security config is explicitly not unit-tested, already dismissed identically in pass 1) · `EditionDto` exposes `commissionRate`/`documentLanguage` to volunteers (explicitly required by AC7: "full EditionDto" to any non-SELLER) · `updateFromEvent`'s CLOSED branch nulls unconditionally without checking `editionId` (duplicate of pass-1 dismissal — depends on the already-deferred Story 2.1 race condition) · "silent masking" of two-simultaneously-active-editions via most-recent-`createdAt` selection (same already-deferred Story 2.1 race condition) · `previousPhase` field defined/tested but unconsumed (intentional mirror of the backend `PhaseChangedEventDto` contract per Dev Notes, not dead code) · `204` vs `404` REST semantics debate (explicit, unambiguous AC7 requirement) · `CurrentEditionIT`'s `@Order(1)` assumes zero seeded editions (matches established `test-data.sql` conventions, speculative not actual) · thin phase coverage in `CurrentEditionIT` (only PREPARATION/CLOSED tested, not DEPOSIT/SALE/POST_SALE) — same code path handles all active phases identically, exceeds the project's storyboard-style E2E testing philosophy · wildcard imports in `CurrentEditionIT.java` (verified as the established convention across every other `*IT.java` in this package) · T8.5 checked off but the specified `activeEditionId` computed signal was never created (doc/traceability nit only — the `@if (...; as edition)` implementation is functionally equivalent, arguably cleaner)

**Out of scope (FYI, not reviewed):** `application.properties`' `spring.session.timeout` change (`PT8H` → `PT1H`) is present in the working tree alongside this diff but was excluded from this review's scope per user's explicit choice — it should be reviewed separately if it's meant to ship with this story.
