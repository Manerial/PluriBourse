# Story 2.6: Real-Time Phase Notification via SSE

Status: ready-for-dev

## Story

As a volunteer or administrator,
I want the phase chip in the top bar to update in real time when the phase changes,
so that I always know the current phase without reloading the page.

## Acceptance Criteria

1. **Given** any authenticated user (admin or volunteer) is logged in, **When** the app layout renders, **Then** the phase chip displays the current active edition's phase (PREPARATION/DEPOSIT/SALE/POST_SALE) — or "No active edition" if no edition is in an active phase.

2. **Given** an admin advances the phase, **When** the SSE event `phase-changed` is received by any connected client, **Then** the phase chip updates within 2 seconds, with a 150ms fade transition.

3. **Given** the active edition transitions to `CLOSED`, **When** the SSE event is received, **Then** the chip displays "No active edition" (CLOSED is not an active phase — same as no edition).

4. **Given** the SSE connection drops and reconnects (e.g., after the server closes it post-broadcast), **When** the `EventSource` auto-reconnects, **Then** no user action is needed — the chip retains its last known state and updates on the next event.

5. **Given** the user is an admin and an active edition exists, **When** the phase chip is rendered, **Then** it is a clickable link that navigates to `/admin/editions/{id}/phase` (the phase control page for the current edition).

6. **Given** the user is a volunteer, **When** the phase chip is rendered, **Then** it is a non-clickable span regardless of edition state.

7. **Given** `GET /api/editions/current` is called by any authenticated non-SELLER user, **When** an edition is in an active phase, **Then** it returns 200 + the full `EditionDto`. **When** no edition is active, **Then** it returns 204 No Content.

8. **Given** the layout initializes, **When** `ngOnInit` runs, **Then** an HTTP call is made to `GET /api/editions/current` to load the initial state before any SSE event arrives.

## Tasks / Subtasks

- [ ] **T1 — Backend: EditionRepository — new query** (AC: 7)
  - [ ] T1.1 — Add `Optional<Edition> findFirstByPhaseIn(List<PhaseType> phases)` to `EditionRepository`

- [ ] **T2 — Backend: EditionService — getCurrentEdition** (AC: 7)
  - [ ] T2.1 — Add `getCurrentEdition(): Optional<EditionDto>` method using `findFirstByPhaseIn(ACTIVE_PHASES)` and mapping with `mapper::toDto`

- [ ] **T3 — Backend: CurrentEditionController** (AC: 7, 8)
  - [ ] T3.1 — Create `org.pluribourse.edition.controller.CurrentEditionController` at `/api/editions/current` (see Dev Notes)
  - [ ] T3.2 — `GET /api/editions/current`: returns 200 + EditionDto if active edition found, 204 No Content otherwise. No `@PreAuthorize` — the `anyRequest()` rule in `SecurityConfig` already restricts to authenticated non-SELLER users

- [ ] **T4 — Backend: Integration test** (AC: 7, 8)
  - [ ] T4.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/CurrentEditionIT.java`
  - [ ] T4.2 — `@Order(1)` — `GET /api/editions/current` with admin session returns 204 when no edition exists
  - [ ] T4.3 — `@Order(2)` — create an edition (admin), then `GET /api/editions/current` returns 200 + edition in PREPARATION
  - [ ] T4.4 — `@Order(3)` — advance to CLOSED (4 advances), then `GET /api/editions/current` returns 204
  - [ ] T4.5 — `@Order(4)` — `GET /api/editions/current` with volunteer session returns 200 when active edition exists (verify endpoint is accessible to volunteers)

- [ ] **T5 — Frontend: PhaseChangedEvent interface** (AC: 2, 3)
  - [ ] T5.1 — Add to `pluribourse-frontend/src/app/models/edition.model.ts`:
    ```typescript
    export interface PhaseChangedEvent {
      editionId: number;
      newPhase: PhaseType;
      previousPhase: PhaseType;
    }
    ```

- [ ] **T6 — Frontend: SseService** (AC: 2, 3, 4)
  - [ ] T6.1 — Create `pluribourse-frontend/src/app/services/sse.service.ts` (see Dev Notes)
  - [ ] T6.2 — Create `pluribourse-frontend/src/app/services/sse.service.spec.ts` (see Dev Notes)

- [ ] **T7 — Frontend: CurrentEditionService** (AC: 1, 2, 3, 5, 8)
  - [ ] T7.1 — Create `pluribourse-frontend/src/app/services/current-edition.service.ts` (see Dev Notes)
  - [ ] T7.2 — Create `pluribourse-frontend/src/app/services/current-edition.service.spec.ts` (see Dev Notes)

- [ ] **T8 — Frontend: AppLayoutComponent — TypeScript** (AC: 1, 2, 3, 4, 5, 6, 8)
  - [ ] T8.1 — Inject `CurrentEditionService`, `SseService`, `DestroyRef` into `AppLayoutComponent`
  - [ ] T8.2 — In `ngOnInit`, call `currentEditionService.load()` and subscribe with `takeUntilDestroyed`
  - [ ] T8.3 — In `ngOnInit`, subscribe to `sseService.phaseChanges()` with `takeUntilDestroyed`, calling `currentEditionService.updateFromEvent(event)` on each event
  - [ ] T8.4 — Expose `currentEditionService.currentEdition` as a computed / direct signal reference for the template
  - [ ] T8.5 — Add `readonly isAdmin` computed already exists — add `readonly activeEditionId = computed(() => this.currentEditionService.currentEdition()?.id ?? null)` for the admin chip link

- [ ] **T9 — Frontend: AppLayoutComponent — HTML** (AC: 1, 2, 3, 5, 6)
  - [ ] T9.1 — Replace the hardcoded phase chip block (lines 9–15 of current `app-layout.component.html`) with a dynamic version (see Dev Notes)

- [ ] **T10 — Frontend: i18n keys** (AC: 1, 3)
  - [ ] T10.1 — Add to `public/i18n/en.json` under `nav.phase`:
    ```json
    "none": "No active edition"
    ```
  - [ ] T10.2 — Add to `public/i18n/fr.json` under `nav.phase`:
    ```json
    "none": "Aucune édition en cours"
    ```

- [ ] **T11 — Frontend: AppLayoutComponent spec** (AC: 1, 5, 6)
  - [ ] T11.1 — Add mock for `CurrentEditionService` to `TestBed` providers (see Dev Notes)
  - [ ] T11.2 — Add test: when `currentEdition()` is null, chip shows `nav.phase.none` key text and no link
  - [ ] T11.3 — Add test: when `currentEdition()` has phase DEPOSIT and user is admin, chip is an `<a>` with href matching `/admin/editions/{id}/phase`
  - [ ] T11.4 — Add test: when user is volunteer, chip is a `<span>` (not an `<a>`) even when edition is active
  - [ ] T11.5 — Run `npm test` — all tests must pass

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
- [Source: edition/entity/PhaseType.java + EditionService.java#ACTIVE_PHASES] Active phases definition
- [Source: edition/repository/EditionRepository.java] Existing `existsByPhaseIn` — add `findFirstByPhaseIn`
- [Source: shared/security/SecurityConfig.java] `anyRequest()` covers non-admin authenticated users ✅

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
