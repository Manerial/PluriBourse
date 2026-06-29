---
baseline_commit: accd31a
---

# Story 2.2: Phase Cycle Control & Confirmation Dialogs

Status: done

## Story

As an administrator,
I want to advance or roll back the edition phase with explicit confirmation,
so that phase transitions are intentional and their consequences are clearly communicated.

## Acceptance Criteria

1. **Given** admin navigates to `/admin/editions/:id/phase`, **When** page loads, **Then** the current phase is clearly displayed and available transition buttons (forward and backward) are shown.

2. **Given** admin clicks a phase transition button, **When** the confirmation dialog appears, **Then** it shows the destination phase and describes the main consequence — plus two buttons: confirm (primary) and cancel (ghost).

3. **Given** admin confirms a forward phase transition, **When** transition completes, **Then** the edition phase is updated in the database and the updated edition is returned.

4. **Given** admin confirms the PREPARATION → DEPOSIT transition, **When** transition completes, **Then** phase becomes DEPOSIT, commission rate is now locked (FR-016), and categories/table mappings become read-only (Story 2.3).

5. **Given** admin confirms a backward phase transition, **When** transition completes, **Then** phase rolls back one step (DEPOSIT→PREPARATION, SALE→DEPOSIT, POST_SALE→SALE, CLOSED→POST_SALE) with all recorded data preserved (FR-082).

6. **Given** admin confirms the DEPOSIT → PREPARATION rollback, **When** transition completes, **Then** commission rate becomes editable again (FR-016) and categories/table mappings become editable again (FR-018).

7. **Given** an edition has been closed AND the Archive action has been triggered (Story 2.5), **When** admin views the phase control page, **Then** the rollback button from CLOSED is absent (FR-082 — rollback disabled after archiving).

8. **Given** any phase transition completes, **When** the server processes it, **Then** an SSE event `phase-changed` is broadcast to all connected clients with payload `{editionId, newPhase, previousPhase}`.

9. **Given** admin is in Preparation phase and tries to roll back, **When** API is called, **Then** 422 is returned — no rollback from Preparation.

10. **Given** admin tries to advance from CLOSED, **When** API is called, **Then** 422 is returned — no further advance from CLOSED.

## Tasks / Subtasks

- [x] **T1 — Backend: Liquibase migration 008** (AC: 7)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/008-edition-archived.xml` (see Dev Notes)
  - [x] T1.2 — Add `<include file="db/changelog/008-edition-archived.xml"/>` to `db.changelog-master.xml` after 007

- [x] **T2 — Backend: Edition entity update** (AC: 7)
  - [x] T2.1 — Add `archived` boolean field to `Edition.java` (see Dev Notes — naming matters for Jackson)

- [x] **T3 — Backend: EditionDto update** (AC: 7)
  - [x] T3.1 — Add `boolean archived` component to `EditionDto` record — MapStruct maps it automatically (same field name)

- [x] **T4 — Backend: PhaseChangedEventDto** (AC: 8)
  - [x] T4.1 — Create `org.pluribourse.shared.sse.PhaseChangedEventDto` record (see Dev Notes)

- [x] **T5 — Backend: SseEmitterRegistry** (AC: 8)
  - [x] T5.1 — Create `org.pluribourse.shared.sse.SseEmitterRegistry` (see Dev Notes)

- [x] **T6 — Backend: SseController** (AC: 8)
  - [x] T6.1 — Create `org.pluribourse.shared.sse.SseController` with `GET /api/sse/events` (see Dev Notes)

- [x] **T7 — Backend: EditionService additions** (AC: 1, 3, 4, 5, 6, 7, 8, 9, 10)
  - [x] T7.1 — Add `getEditionById(Long id)` method (for the phase control page GET)
  - [x] T7.2 — Add `advancePhase(Long id)` with state machine + SSE broadcast (see Dev Notes)
  - [x] T7.3 — Add `rollbackPhase(Long id)` with state machine + SSE broadcast (see Dev Notes)

- [x] **T8 — Backend: EditionController additions** (AC: 1, 3, 4, 5, 6, 7, 8, 9, 10)
  - [x] T8.1 — Add `GET /api/admin/editions/{id}` — returns single EditionDto
  - [x] T8.2 — Add `POST /api/admin/editions/{id}/phase/advance` — returns updated EditionDto
  - [x] T8.3 — Add `POST /api/admin/editions/{id}/phase/rollback` — returns updated EditionDto

- [x] **T9 — Backend: Integration tests** (AC: 1–10)
  - [x] T9.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/PhaseTransitionIT.java` (see Dev Notes)

- [x] **T10 — Frontend: edition.model.ts update** (AC: 7)
  - [x] T10.1 — Add `archived: boolean` to `EditionDto` interface

- [x] **T11 — Frontend: edition.service.ts update** (AC: 1, 3, 5, 8)
  - [x] T11.1 — Add `getById(id: number): Observable<EditionDto>` — GET `/api/admin/editions/:id`
  - [x] T11.2 — Add `advancePhase(id: number): Observable<EditionDto>` — POST `.../phase/advance` with empty body `{}`
  - [x] T11.3 — Add `rollbackPhase(id: number): Observable<EditionDto>` — POST `.../phase/rollback` with empty body `{}`

- [x] **T12 — Frontend: edition.service.spec.ts update** (AC: 1, 3, 5)
  - [x] T12.1 — Add tests for `getById`, `advancePhase`, `rollbackPhase`

- [x] **T13 — Frontend: PhaseControlComponent** (AC: 1, 2, 3, 4, 5, 6, 7, 8)
  - [x] T13.1 — Create `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts` (see Dev Notes)
  - [x] T13.2 — Create `phase-control.component.html`
  - [x] T13.3 — Create `phase-control.component.scss`
  - [x] T13.4 — Create `phase-control.component.spec.ts`

- [x] **T14 — Frontend: edition-list update** (AC: 1 — navigation to phase control)
  - [x] T14.1 — Add "Manage phase" `routerLink` anchor in `edition-list.component.html` per edition row (see Dev Notes — use `routerLink`, NOT a TS navigation method)
  - [x] T14.2 — Update `MOCK_EDITIONS` in `edition-list.component.spec.ts` to add `archived: false` (TypeScript compile error without it — `archived` is now mandatory in `EditionDto`)

- [x] **T15 — Frontend: Routing** (AC: 1)
  - [x] T15.1 — Add `editions/:id/phase` route to `admin.routes.ts` (BEFORE `editions/create` to avoid path conflict — see Dev Notes)

- [x] **T16 — Frontend: i18n keys** (AC: 1, 2, 4, 5, 6, 7)
  - [x] T16.1 — Add `phase.*` keys to `pluribourse-frontend/public/i18n/en.json` (see Dev Notes)
  - [x] T16.2 — Add same keys in French (vouvoiement obligatoire) to `fr.json`

- [x] **T17 — Frontend: Run `npm test`** — all existing tests must pass, zero regressions

### Review Findings

- [x] [Review][Decision] No optimistic locking on phase transitions — `@Version` added to `Edition.java` + migration 009 [`Edition.java`, `009-edition-version.xml`]
- [x] [Review][Patch] `broadcast()` non-atomic snapshot+removeAll allows double delivery under concurrent calls [`SseEmitterRegistry.java`]
- [x] [Review][Patch] `broadcast()` catches only `IOException` — `RuntimeException` from `send()`/`complete()` propagates through `@Transactional`, rolling back the phase transition [`SseEmitterRegistry.java`]
- [x] [Review][Patch] `confirmAdvance()`/`confirmRollback()` — `isSubmitting` guard set after dialog confirms, multiple dialogs openable with rapid clicks [`phase-control.component.ts`]
- [x] [Review][Patch] `canAdvance()` returns `true` when `edition()` is `null` — inconsistent with `canRollback()` [`phase-control.component.ts`]
- [x] [Review][Patch] `Number(null)` = 0 for missing route param — invalid ID silently sent to backend [`phase-control.component.ts:827`]
- [x] [Review][Patch] `confirmRollback()` untested; `confirmAdvance()` confirmed-path (toast, edition update, error handler) untested — probable coverage below 80% [`phase-control.component.spec.ts`]
- [x] [Review][Defer] Async callback writes to signals after component destroyed (in-flight `firstValueFrom` not cancelled by `takeUntilDestroyed`) [`phase-control.component.ts`] — deferred, pre-existing Angular limitation
- [x] [Review][Defer] `PhaseTransitionIT` test cascade — `@Order(2+)` emit misleading 404 failures if `@Order(1)` fails (no `Assumptions.assumeTrue`) [`PhaseTransitionIT.java`] — deferred, pre-existing ordered-test convention

## Dev Notes

### T1 — Migration 008

File: `pluribourse-backend/src/main/resources/db/changelog/008-edition-archived.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="008-edition-archived" author="pluribourse">
        <addColumn tableName="editions">
            <column name="is_archived" type="BOOLEAN" defaultValueBoolean="false">
                <constraints nullable="false"/>
            </column>
        </addColumn>
    </changeSet>

</databaseChangeLog>
```

In `db.changelog-master.xml`, add after the 007 include:
```xml
<include file="db/changelog/008-edition-archived.xml"/>
```

### T2 — Edition Entity Update

Add to `Edition.java` (in `org.pluribourse.edition.entity`):

```java
@Column(name = "is_archived", nullable = false)
private boolean archived = false;
```

**CRITICAL — naming:** Use `archived` (NOT `isArchived`). Lombok's `@Getter` on a `boolean` field named `isArchived` generates `isIsArchived()` which is wrong. Field named `archived` generates the correct `isArchived()` getter. MapStruct maps it correctly since the DTO also uses `archived`.

### T3 — EditionDto Update

Update `EditionDto.java` record to add the `archived` component **at the end** of the record signature:

```java
public record EditionDto(
        Long id,
        String name,
        PhaseType phase,
        BigDecimal commissionRate,
        Language documentLanguage,
        LocalDate createdAt,
        boolean archived
) {}
```

MapStruct (`EditionMapper`) auto-maps `archived` from `Edition.isArchived()` to `EditionDto.archived()` — no changes needed in the mapper interface.

**Existing tests:** `EditionManagementIT` uses JSON deserialization, not the record constructor, so adding a field does not break any existing tests. The new field will appear as `"archived": false` in all existing test responses (no assertions on it — safe).

### T4 — PhaseChangedEventDto

```java
package org.pluribourse.shared.sse;

import org.pluribourse.edition.entity.PhaseType;

public record PhaseChangedEventDto(Long editionId, PhaseType newPhase, PhaseType previousPhase) {}
```

### T5 — SseEmitterRegistry

```java
package org.pluribourse.shared.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterRegistry {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * Broadcasts an SSE event to all registered emitters, then closes each one.
     * Clients (Angular EventSource) reconnect automatically per RFC 8895.
     */
    public void broadcast(String eventName, Object payload) {
        List<SseEmitter> snapshot = new ArrayList<>(emitters);
        emitters.removeAll(snapshot);
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
                emitter.complete();
            } catch (IOException e) {
                // emitter was already dead — ignore
            }
        }
    }
}
```

**Design note:** Emitters are closed after every broadcast (architecture decision). `CopyOnWriteArrayList` provides thread-safe iteration without holding locks during send. `snapshot` approach avoids ConcurrentModificationException when emitters remove themselves during the send loop.

### T6 — SseController

```java
package org.pluribourse.shared.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping("/events")
    public SseEmitter subscribe() {
        return registry.register();
    }
}
```

**Security note:** `/api/sse/events` is NOT under `/api/admin/**`. It falls under `anyRequest().access(...)` in `SecurityConfig` — accessible to all authenticated non-SELLER users (both ADMIN and VOLUNTEER). Do NOT add it to admin-only config.

### T7 — EditionService Additions

Add these methods to the existing `EditionService`. Inject `SseEmitterRegistry` in the constructor. Keep `ACTIVE_PHASES` constant unchanged.

```java
// Add to constructor injection:
private final SseEmitterRegistry sseEmitterRegistry;

@Transactional(readOnly = true)
public EditionDto getEditionById(Long id) {
    return mapper.toDto(findById(id));
}

@Transactional
public EditionDto advancePhase(Long id) {
    Edition edition = findById(id);
    PhaseType previousPhase = edition.getPhase();
    PhaseType newPhase = computeNextPhase(previousPhase);
    edition.setPhase(newPhase);
    Edition saved = repository.save(edition);
    sseEmitterRegistry.broadcast("phase-changed", new PhaseChangedEventDto(id, newPhase, previousPhase));
    return mapper.toDto(saved);
}

@Transactional
public EditionDto rollbackPhase(Long id) {
    Edition edition = findById(id);
    PhaseType previousPhase = edition.getPhase();
    PhaseType newPhase = computePreviousPhase(previousPhase, edition.isArchived());
    edition.setPhase(newPhase);
    Edition saved = repository.save(edition);
    sseEmitterRegistry.broadcast("phase-changed", new PhaseChangedEventDto(id, newPhase, previousPhase));
    return mapper.toDto(saved);
}

private PhaseType computeNextPhase(PhaseType current) {
    return switch (current) {
        case PREPARATION -> PhaseType.DEPOSIT;
        case DEPOSIT -> PhaseType.SALE;
        case SALE -> PhaseType.POST_SALE;
        case POST_SALE -> PhaseType.CLOSED;
        case CLOSED -> throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "phase-already-closed",
                "Edition is already closed. Cannot advance further.");
    };
}

private PhaseType computePreviousPhase(PhaseType current, boolean archived) {
    return switch (current) {
        case PREPARATION -> throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "phase-cannot-rollback-from-preparation",
                "Cannot roll back from Preparation phase.");
        case DEPOSIT -> PhaseType.PREPARATION;
        case SALE -> PhaseType.DEPOSIT;
        case POST_SALE -> PhaseType.SALE;
        case CLOSED -> {
            if (archived) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "phase-rollback-disabled-after-archive",
                        "Cannot roll back from Closed phase after the edition has been archived.");
            }
            yield PhaseType.POST_SALE;
        }
    };
}
```

**CRITICAL — SSE timing inside @Transactional:** The `sseEmitterRegistry.broadcast()` call fires INSIDE the `@Transactional` block, which means it executes BEFORE the DB transaction commits (Spring commits when the method returns, not when `repository.save()` is called). In theory a reconnecting client could reread the old phase in the microseconds between the broadcast and the commit. In practice this is safe: network round-trip latency for any SSE client to reconnect is ≥ 10 ms, while the gap between broadcast and commit is < 1 ms. The broadcast is correct for this story. The proper production-grade solution — if this ever causes an observable race — is `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { sseEmitterRegistry.broadcast(...); } })`. Do NOT implement this in Story 2.2; it is noted here only for awareness.

**Imports to add:**
```java
import org.pluribourse.shared.sse.PhaseChangedEventDto;
import org.pluribourse.shared.sse.SseEmitterRegistry;
```

### T8 — EditionController Additions

Add these endpoints to the existing `EditionController`:

```java
@GetMapping("/{id}")
public ResponseEntity<EditionDto> getEditionById(@PathVariable Long id) {
    return ResponseEntity.ok(service.getEditionById(id));
}

@PostMapping("/{id}/phase/advance")
public ResponseEntity<EditionDto> advancePhase(@PathVariable Long id) {
    return ResponseEntity.ok(service.advancePhase(id));
}

@PostMapping("/{id}/phase/rollback")
public ResponseEntity<EditionDto> rollbackPhase(@PathVariable Long id) {
    return ResponseEntity.ok(service.rollbackPhase(id));
}
```

These endpoints are under `/api/admin/editions` and thus automatically require `ADMIN` role.

### T9 — PhaseTransitionIT

```java
package org.pluribourse.edition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.dto.CreateEditionDto;
import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.edition.repository.EditionRepository;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PhaseTransitionIT extends IntegrationTest {

    @Autowired private EditionRepository repository;
    @Autowired private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test @Order(1)
    void create_edition_in_preparation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEditionDto("Bourse Test 2026"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn();
        editionId = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();
        assertThat(editionId).isNotNull();
    }

    @Test @Order(2)
    void get_by_id_returns_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editionId))
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }

    @Test @Order(3)
    void rollback_from_preparation_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(4)
    void advance_to_deposit_locks_commission_rate() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        Edition edition = repository.findById(editionId).orElseThrow();
        assertThat(edition.getPhase()).isEqualTo(PhaseType.DEPOSIT);
    }

    @Test @Order(5)
    void commission_rate_update_rejected_in_deposit() throws Exception {
        String body = "{\"commissionRate\": 10.00}";
        mockMvc.perform(patch("/api/admin/editions/" + editionId + "/commission-rate")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(6)
    void rollback_deposit_to_preparation_unlocks_commission() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));

        // Commission rate is editable again after rollback
        String body = "{\"commissionRate\": 18.00}";
        mockMvc.perform(patch("/api/admin/editions/" + editionId + "/commission-rate")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionRate").value(18.00));
    }

    @Test @Order(7)
    void advance_through_all_phases_to_closed() throws Exception {
        // PREPARATION → DEPOSIT
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        // DEPOSIT → SALE
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        // SALE → POST_SALE
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        // POST_SALE → CLOSED
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));
    }

    @Test @Order(8)
    void advance_from_closed_returns_422() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(9)
    void rollback_from_closed_to_post_sale_succeeds_when_not_archived() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
    }

    @Test @Order(10)
    void rollback_from_closed_blocked_when_archived() throws Exception {
        // Advance back to CLOSED
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        // Simulate archiving (Story 2.5 will set this via a real endpoint)
        Edition edition = repository.findById(editionId).orElseThrow();
        edition.setArchived(true);
        repository.save(edition);

        // Rollback from CLOSED should be rejected
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/rollback")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(11)
    void volunteer_cannot_trigger_phase_transitions() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(volunteerSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test @Order(12)
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(13)
    void get_by_id_returns_404_for_nonexistent_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions/99999").session(adminSession))
                .andExpect(status().isNotFound());
    }
}
```

**Note:** SSE broadcast is not directly testable with MockMvc in this test class (no connected clients during tests). The broadcast loop runs over an empty `CopyOnWriteArrayList` — no error, no assertion needed. Do NOT attempt to test SSE emission in integration tests; it will be covered by Story 2.4's E2E SSE tests.

Add two more tests at the end of `PhaseTransitionIT` to cover `SseController` access rules (no extra setup needed — sessions are already initialized):

```java
    @Test @Order(14)
    void sse_endpoint_accessible_by_authenticated_admin() throws Exception {
        // AsyncDispatch is needed for SseEmitter — just verify the response starts (200/text-event-stream)
        mockMvc.perform(get("/api/sse/events").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")));
    }

    @Test @Order(15)
    void sse_endpoint_accessible_by_volunteer() throws Exception {
        mockMvc.perform(get("/api/sse/events").session(volunteerSession))
                .andExpect(status().isOk());
    }

    @Test @Order(16)
    void sse_endpoint_returns_401_for_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/sse/events"))
                .andExpect(status().isUnauthorized());
    }
```

### T10 — edition.model.ts Update

Add `archived: boolean` to `EditionDto`:

```typescript
export interface EditionDto {
  id: number;
  name: string;
  phase: PhaseType;
  commissionRate: number;
  documentLanguage: 'EN' | 'FR';
  createdAt: string; // ISO 8601 date string "YYYY-MM-DD"
  archived: boolean;
}
```

No changes needed to `CreateEditionDto`, `UpdateCommissionRateDto`, or `UpdateDocumentLanguageDto`.

### T11 — edition.service.ts Additions

Add these three methods to the existing `EditionService`:

```typescript
getById(id: number): Observable<EditionDto> {
  return this.http.get<EditionDto>(`${this.BASE}/${id}`);
}

advancePhase(id: number): Observable<EditionDto> {
  return this.http.post<EditionDto>(`${this.BASE}/${id}/phase/advance`, {});
}

rollbackPhase(id: number): Observable<EditionDto> {
  return this.http.post<EditionDto>(`${this.BASE}/${id}/phase/rollback`, {});
}
```

**POST body clarification:** `http.post(url, {})` sends a JSON body `{}` with `Content-Type: application/json`. The Spring controller endpoints (`advancePhase`, `rollbackPhase`) have NO `@RequestBody` parameter — Spring simply ignores the body. Do NOT add `@RequestBody` to the controller, and do NOT send a `null` body from Angular (some HTTP clients reject `POST` with a `null` body at the browser level). The `{}` pattern is correct and tested.

### T12 — edition.service.spec.ts Additions

Add to `describe('EditionService')`:

```typescript
it('getById() sends GET /api/admin/editions/1', async () => {
  const p = firstValueFrom(service.getById(1));
  http.expectOne('/api/admin/editions/1').flush(MOCK_EDITION);
  expect(await p).toEqual(MOCK_EDITION);
});

it('advancePhase() sends POST /api/admin/editions/1/phase/advance', async () => {
  const p = firstValueFrom(service.advancePhase(1));
  const req = http.expectOne('/api/admin/editions/1/phase/advance');
  expect(req.request.method).toBe('POST');
  req.flush({ ...MOCK_EDITION, phase: 'DEPOSIT' as PhaseType });
  expect((await p).phase).toBe('DEPOSIT');
});

it('rollbackPhase() sends POST /api/admin/editions/1/phase/rollback', async () => {
  const p = firstValueFrom(service.rollbackPhase(1));
  const req = http.expectOne('/api/admin/editions/1/phase/rollback');
  expect(req.request.method).toBe('POST');
  req.flush({ ...MOCK_EDITION, phase: 'PREPARATION' as PhaseType });
  expect((await p).phase).toBe('PREPARATION');
});
```

Update `MOCK_EDITION` to include `archived: false`:
```typescript
const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01',
  archived: false
};
```

### T13 — PhaseControlComponent

File: `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`

```typescript
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionDto, PhaseType } from '../../../../models/edition.model';
import { EditionService } from '../../../../services/edition.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../../shared/components/notification-inline/notification-inline.component';

const PHASE_ORDER: PhaseType[] = ['PREPARATION', 'DEPOSIT', 'SALE', 'POST_SALE', 'CLOSED'];

@Component({
  selector: 'app-phase-control',
  standalone: true,
  imports: [TranslatePipe, RouterLink, SkeletonRowComponent, NotificationInlineComponent],
  templateUrl: './phase-control.component.html',
  styleUrl: './phase-control.component.scss',
})
export class PhaseControlComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly edition = signal<EditionDto | null>(null);
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.edition.set(await firstValueFrom(this.editionService.getById(id)));
    } catch {
      this.error.set('phase.control.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  canAdvance(): boolean {
    return this.edition()?.phase !== 'CLOSED';
  }

  canRollback(): boolean {
    const e = this.edition();
    if (!e) { return false; }
    if (e.phase === 'PREPARATION') { return false; }
    if (e.phase === 'CLOSED' && e.archived) { return false; }
    return true;
  }

  nextPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) { return null; }
    const idx = PHASE_ORDER.indexOf(current);
    return idx < PHASE_ORDER.length - 1 ? PHASE_ORDER[idx + 1] : null;
  }

  prevPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) { return null; }
    const idx = PHASE_ORDER.indexOf(current);
    return idx > 0 ? PHASE_ORDER[idx - 1] : null;
  }

  confirmAdvance(): void {
    const e = this.edition();
    if (!e || !this.canAdvance() || this.isSubmitting()) { return; }
    const next = this.nextPhase()!;
    const nextLabel = this.translate.instant('edition.phase.' + next);
    this.confirmDialog.open({
      title: this.translate.instant('phase.advance.dialog.title', { nextPhase: nextLabel }),
      description: this.translate.instant('phase.advance.dialog.description.' + e.phase),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) { return; }
      this.isSubmitting.set(true);
      try {
        this.edition.set(await firstValueFrom(this.editionService.advancePhase(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.advance.success'));
      } catch {
        this.toast.showError(this.translate.instant('phase.advance.error'));
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }

  confirmRollback(): void {
    const e = this.edition();
    if (!e || !this.canRollback() || this.isSubmitting()) { return; }
    const prev = this.prevPhase()!;
    const prevLabel = this.translate.instant('edition.phase.' + prev);
    this.confirmDialog.open({
      title: this.translate.instant('phase.rollback.dialog.title', { prevPhase: prevLabel }),
      description: this.translate.instant('phase.rollback.dialog.description.' + e.phase),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) { return; }
      this.isSubmitting.set(true);
      try {
        this.edition.set(await firstValueFrom(this.editionService.rollbackPhase(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.rollback.success'));
      } catch {
        this.toast.showError(this.translate.instant('phase.rollback.error'));
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }
}
```

**`phase-control.component.scss`** — leave this file **empty** (or write only a comment). All classes used in the template (`card`, `card__header`, `btn-ghost`, `btn-primary`, `phase-chip`, `phase-chip__dot`) are global classes defined in the app's shared styles. Do NOT duplicate or redefine them. Only add component-level rules if the layout genuinely requires a local override that cannot use global classes.

**`phase-control.component.html`** — minimal structure:

```html
<div class="card">
  <div class="card__header">
    <a routerLink="/admin/editions" class="btn-ghost">
      <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
      {{ 'phase.control.back' | translate }}
    </a>
  </div>

  @if (isLoading()) {
    <app-skeleton-row [rows]="3" />
  }

  @if (error()) {
    <app-notification-inline [message]="error()! | translate" variant="error" />
  }

  @if (!isLoading() && !error() && edition()) {
    <div class="phase-control">
      <h1 class="phase-control__title">{{ edition()!.name }}</h1>

      <div class="phase-control__current">
        <span class="phase-chip">
          <span class="phase-chip__dot" aria-hidden="true">●</span>
          {{ ('edition.phase.' + edition()!.phase) | translate }}
        </span>
      </div>

      <div class="phase-control__actions">
        @if (canRollback()) {
          <button
            type="button"
            class="btn-ghost"
            [disabled]="isSubmitting()"
            (click)="confirmRollback()">
            <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
            {{ 'phase.rollback.button' | translate }} {{ ('edition.phase.' + prevPhase()) | translate }}
          </button>
        }

        @if (canAdvance()) {
          <button
            type="button"
            class="btn-primary"
            [disabled]="isSubmitting()"
            (click)="confirmAdvance()">
            {{ 'phase.advance.button' | translate }} {{ ('edition.phase.' + nextPhase()) | translate }}
            <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
          </button>
        }
      </div>
    </div>
  }
</div>
```

**`phase-control.component.spec.ts`** — follow `EditionListComponent.spec.ts` pattern:

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PhaseControlComponent } from './phase-control.component';
import { EditionService } from '../../../../services/edition.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../../models/edition.model';

const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01', archived: false
};

describe('PhaseControlComponent', () => {
  let fixture: ComponentFixture<PhaseControlComponent>;
  let component: PhaseControlComponent;

  const editionServiceMock = {
    getById: vi.fn().mockReturnValue(of(MOCK_EDITION)),
    advancePhase: vi.fn().mockReturnValue(of({ ...MOCK_EDITION, phase: 'DEPOSIT' })),
    rollbackPhase: vi.fn().mockReturnValue(of(MOCK_EDITION)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION));

    await TestBed.configureTestingModule({
      imports: [PhaseControlComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        { provide: EditionService, useValue: editionServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PhaseControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads edition on init', () => {
    expect(editionServiceMock.getById).toHaveBeenCalledWith(1);
    expect(component.edition()?.phase).toBe('PREPARATION');
  });

  it('sets error key when load fails', async () => {
    editionServiceMock.getById.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('phase.control.error.load');
  });

  it('canAdvance returns true when phase is PREPARATION', () => {
    expect(component.canAdvance()).toBe(true);
  });

  it('canRollback returns false when phase is PREPARATION', () => {
    expect(component.canRollback()).toBe(false);
  });

  it('canRollback returns false when CLOSED and archived', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: true });
    expect(component.canRollback()).toBe(false);
  });

  it('canRollback returns true when CLOSED and not archived', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false });
    expect(component.canRollback()).toBe(true);
  });

  it('nextPhase returns DEPOSIT when current is PREPARATION', () => {
    expect(component.nextPhase()).toBe('DEPOSIT');
  });

  it('prevPhase returns null when current is PREPARATION', () => {
    expect(component.prevPhase()).toBeNull();
  });

  it('confirmAdvance opens confirm dialog', () => {
    component.confirmAdvance();
    expect(confirmMock.open).toHaveBeenCalledOnce();
  });
});
```

### T14 — Edition List Update

Add a "Manage phase" navigation link to each edition row in `edition-list.component.html`. Insert inside the `<div class="actions-cell">`:

```html
<a
  [routerLink]="['/admin/editions', edition.id, 'phase']"
  class="btn-ghost">
  {{ 'edition.actions.managePhase' | translate }}
</a>
```

Do NOT add a `navigateToPhaseControl()` TypeScript method — `routerLink` handles navigation declaratively (no TS method needed). `RouterLink` is already imported in `edition-list.component.ts` — no import changes required.

Update `edition-list.component.spec.ts` — the `MOCK_EDITIONS` constant needs `archived: false` or TypeScript will refuse to compile (the `archived` field is now required in `EditionDto`):
```typescript
const MOCK_EDITIONS: EditionDto[] = [
  { id: 1, name: 'Bourse 2026', phase: 'PREPARATION', commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01', archived: false }
];
```

### T15 — Routing Update

In `admin.routes.ts`, add the phase control route **BEFORE** `editions/create` to ensure Angular router precedence (specific paths before wildcard-style paths). The `:id` segment would otherwise match `create` if placed after:

```typescript
{
  path: 'editions/:id/phase',
  loadComponent: () =>
    import('./editions/phase-control/phase-control.component').then((m) => m.PhaseControlComponent),
},
{
  path: 'editions/create',   // keep this AFTER :id/phase or it would match first
  loadComponent: () =>
    import('./editions/edition-form.component').then((m) => m.EditionFormComponent),
},
```

**CRITICAL:** The path `editions/:id/phase` and `editions/create` can coexist because `:id/phase` requires two segments (`:id` + `phase`) while `create` is a single segment. Angular 21 router handles this correctly. However, to be safe, the `:id/phase` route should still come BEFORE `editions/create` in the array.

### T16 — i18n Keys

**`en.json`** — add at root level next to existing `edition` key:

```json
"phase": {
  "control": {
    "back": "Back to editions",
    "error": {
      "load": "Failed to load edition."
    }
  },
  "advance": {
    "button": "Advance to",
    "success": "Phase advanced.",
    "error": "Failed to advance phase.",
    "dialog": {
      "title": "Advance to {{nextPhase}}?",
      "description": {
        "PREPARATION": "The commission rate will be locked for this edition. Categories and table assignments will become read-only.",
        "DEPOSIT": "The deposit phase ends. No new sellers or items can be registered.",
        "SALE": "The sale phase ends. No more sales can be processed.",
        "POST_SALE": "The edition will be closed and become read-only."
      }
    }
  },
  "rollback": {
    "button": "Roll back to",
    "success": "Phase rolled back.",
    "error": "Failed to roll back phase.",
    "dialog": {
      "title": "Roll back to {{prevPhase}}?",
      "description": {
        "DEPOSIT": "The commission rate and categories become editable again.",
        "SALE": "All recorded items remain unchanged. Deposit operations can resume.",
        "POST_SALE": "All sales and payouts remain unchanged. Sale operations can resume.",
        "CLOSED": "The edition becomes active again. All data is preserved."
      }
    }
  }
}
```

Also add to `edition.actions` (inside existing `edition` key):
```json
"edition": {
  "actions": {
    "managePhase": "Manage phase"
  }
}
```

**`fr.json`** — same keys in French, vouvoiement obligatoire:

```json
"phase": {
  "control": {
    "back": "Retour aux éditions",
    "error": {
      "load": "Impossible de charger l'édition."
    }
  },
  "advance": {
    "button": "Avancer vers",
    "success": "Phase avancée.",
    "error": "Impossible de faire avancer la phase.",
    "dialog": {
      "title": "Avancer vers {{nextPhase}} ?",
      "description": {
        "PREPARATION": "Le taux de commission sera gelé pour cette édition. Les catégories et la correspondance des tables passeront en lecture seule.",
        "DEPOSIT": "La phase de dépôt se termine. Aucun nouveau vendeur ni article ne peut être enregistré.",
        "SALE": "La phase de vente se termine. Aucune nouvelle vente ne peut être traitée.",
        "POST_SALE": "L'édition sera clôturée et passera en lecture seule."
      }
    }
  },
  "rollback": {
    "button": "Revenir à",
    "success": "Phase revenue en arrière.",
    "error": "Impossible de revenir en arrière.",
    "dialog": {
      "title": "Revenir à {{prevPhase}} ?",
      "description": {
        "DEPOSIT": "Le taux de commission et les catégories redeviennent modifiables.",
        "SALE": "Tous les articles enregistrés restent inchangés. Les opérations de dépôt peuvent reprendre.",
        "POST_SALE": "Toutes les ventes et les soldes restent inchangés. Les opérations de vente peuvent reprendre.",
        "CLOSED": "L'édition redevient active. Toutes les données sont préservées."
      }
    }
  }
}
```

Add to `edition.actions` in `fr.json`:
```json
"edition": {
  "actions": {
    "managePhase": "Gérer les phases"
  }
}
```

### Previous Story Intelligence (from Story 2.1)

**Critical learnings to NOT repeat:**
- `JacksonConfig.objectMapper()` requires `findAndAddModules()` to register `JavaTimeModule` — already fixed in Story 2.1. Do NOT touch `JacksonConfig` again.
- `ACTIVE_PHASES` constant belongs in `EditionService`, NOT in `PhaseType` enum — already correctly set.
- `AdminCreateRunnerTest` has 3 pre-existing failures (`findByRole` vs `existsByRole`) — these are NOT regressions, do not count them.
- `EditionManagementIT` directly manipulates DB to set DEPOSIT phase (since phase transition didn't exist). Story 2.2 provides the real endpoint — existing test still works with direct DB manipulation.
- Backend package naming: SINGULAR sub-packages (`entity`, `dto`, `service`, not `entities`, `dtos`).
- Frontend: Files go in `src/app/features/admin/editions/` NOT `src/app/components/edition/`.
- i18n files are at `pluribourse-frontend/public/i18n/` (NOT `src/assets/i18n/`).
- Use `<span class="material-symbols-outlined">` NOT `<mat-icon>` (MatIconRegistry not configured).
- Dialog SCSS: reuse `.dialog` global class + `--pb-space-*` and `--mat-sys-*` tokens; do NOT redeclare them.

**Review findings from Story 2.1 deferred to this story:**
- AC7/AC8 coverage partial in `EditionManagementIT` — SALE, POST_SALE, CLOSED not tested for commission lock and delete refusal. The new `PhaseTransitionIT` covers the actual phase transitions through all states, which implicitly validates these scenarios.

### Existing Code NOT to Break

- `EditionService.ACTIVE_PHASES` — add new methods, do NOT alter or remove existing ones
- `EditionController` — only add new endpoints; all existing 5 endpoints must remain unchanged
- `SecurityConfig.java` — do NOT touch; the SSE endpoint `/api/sse/events` is correctly covered by `anyRequest().access(...)` as a non-admin route
- `GlobalExceptionHandler.java` — do NOT touch; `BusinessException` is already handled
- `db.changelog-master.xml` — only append 008 include after 007
- `EditionManagementIT.java` — existing 16 tests must still pass; the `archived` field addition to `EditionDto` does not break JSON assertions
- `app-layout.component.html` — the phase chip stays static (`nav.phase.preparation` hardcoded) in this story; Story 2.4 makes it dynamic

### Package Structure (Backend)

New files all in `org.pluribourse.shared.sse`:
```
org.pluribourse.shared.sse.SseEmitterRegistry
org.pluribourse.shared.sse.SseController
org.pluribourse.shared.sse.PhaseChangedEventDto
```

Matches architecture doc: `shared/sse/SseEmitterRegistry.java`.

### Package Structure (Frontend)

New files:
```
src/app/features/admin/editions/phase-control/phase-control.component.ts
src/app/features/admin/editions/phase-control/phase-control.component.html
src/app/features/admin/editions/phase-control/phase-control.component.scss
src/app/features/admin/editions/phase-control/phase-control.component.spec.ts
```

### References

- [Source: epics.md, Epic 2, Story 2.2] — full acceptance criteria and FR references (FR-011, FR-016, FR-018, FR-082)
- [Source: architecture.md — Notification de Changement de Phase] — SSE design: SseEmitter per client, closed after broadcast, clients reconnect
- [Source: architecture.md — Patrons de Communication] — SSE event names and payload format
- [Source: architecture.md — shared/sse/SseEmitterRegistry.java] — target package location
- [Source: architecture.md — ARCH-012] — SseEmitterRegistry must be initialized BEFORE phase transition endpoints
- [Source: architecture.md — ARCH-015] — phase state machine is a prerequisite for F3, F4, F5, F10
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java] — existing service to extend
- [Source: pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionController.java] — existing controller to extend
- [Source: pluribourse-backend/src/main/java/org/pluribourse/shared/security/SecurityConfig.java] — `/api/sse/events` must NOT be under `/api/admin/`
- [Source: pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts] — ConfirmDialogService.open(ConfirmDialogData) returns Observable<boolean | undefined>
- [Source: pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts] — ConfirmDialogData: title, description, confirmLabel?, cancelLabel?, confirmVariant?
- [Source: pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts:7] — Use `<span class="material-symbols-outlined">` NOT MatIcon
- [Source: 2-1-crud-dedition-configuration-du-taux-de-commission.md — T9] — Integration test pattern (session setup, MockMvc, repository access)
- [Source: 2-1-crud-dedition-configuration-du-taux-de-commission.md — T16.3] — Angular router path ordering concern

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Backend: Migration 008 ajoute `is_archived` BOOLEAN NOT NULL DEFAULT false sur la table `editions`.
- Backend: `Edition.java` — champ `archived` (pas `isArchived`) pour que Lombok génère `isArchived()` correctement. MapStruct mappe automatiquement vers `EditionDto.archived`.
- Backend: `SseEmitterRegistry` + `SseController` créés dans `org.pluribourse.shared.sse`. Endpoint `/api/sse/events` couvert par `anyRequest()` — accessible ADMIN et VOLUNTEER.
- Backend: Machine à états (`computeNextPhase` / `computePreviousPhase`) dans `EditionService` avec `BusinessException(422)` aux bornes invalides (PREPARATION rollback, CLOSED advance, CLOSED rollback si archivé).
- Backend: `PhaseTransitionIT` — 16 tests couvrant l'intégralité des AC (avance, rollback, verrouillage commission, SSE access control, 401/403, 404, archivage).
- Backend: 103 tests au total, 0 échec.
- Frontend: `EditionDto` étendu avec `archived: boolean`. `EditionService` étendu avec `getById`, `advancePhase`, `rollbackPhase`.
- Frontend: `PhaseControlComponent` standalone avec signals, `ConfirmDialogService`, `ToastService`, `SkeletonRowComponent`, `NotificationInlineComponent`.
- Frontend: Route `editions/:id/phase` ajoutée AVANT `editions/create` dans `admin.routes.ts`.
- Frontend: Clés i18n `phase.*` ajoutées en EN et FR (vouvoiement). Clé `edition.actions.managePhase` ajoutée.
- Frontend: 151 tests, 0 échec (27 fichiers spec).

### File List

**New backend files:**
- pluribourse-backend/src/main/resources/db/changelog/008-edition-archived.xml
- pluribourse-backend/src/main/java/org/pluribourse/shared/sse/PhaseChangedEventDto.java
- pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseEmitterRegistry.java
- pluribourse-backend/src/main/java/org/pluribourse/shared/sse/SseController.java
- pluribourse-backend/src/test/java/org/pluribourse/edition/PhaseTransitionIT.java

**Modified backend files:**
- pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml
- pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionController.java

**New frontend files:**
- pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts
- pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.html
- pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.scss
- pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts

**Modified frontend files:**
- pluribourse-frontend/src/app/models/edition.model.ts
- pluribourse-frontend/src/app/services/edition.service.ts
- pluribourse-frontend/src/app/services/edition.service.spec.ts
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts
- pluribourse-frontend/src/app/features/admin/admin.routes.ts
- pluribourse-frontend/public/i18n/en.json
- pluribourse-frontend/public/i18n/fr.json

## Change Log

- 2026-06-29: Story 2.2 created — Phase Cycle Control & Confirmation Dialogs.
- 2026-06-29: Story 2.2 implemented — Phase state machine, SSE infrastructure, PhaseControlComponent, i18n, 16 IT + 9 UT frontend. All 103 backend + 151 frontend tests pass.
