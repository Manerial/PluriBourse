# Story 2.3: Volunteer Login Blocked Without Active Edition

Status: ready-for-dev

## Story

As an administrator,
I want volunteer login to be rejected when no edition is currently in an active phase,
so that volunteers cannot access the system between events.

## Acceptance Criteria

1. **Given** a volunteer attempts to log in and no edition exists with phase in `[PREPARATION, DEPOSIT, SALE, POST_SALE]`, **When** the login form is submitted, **Then** the backend rejects the authentication with HTTP 401 and a Problem Details body of type `https://pluribourse/errors/no-active-edition`.

2. **Given** the login page receives a 401 with type `no-active-edition`, **When** the error is displayed, **Then** a dedicated inline notification appears with the i18n key `auth.login.error.no-active-edition` (distinct from `invalid-credentials`).

3. **Given** an edition exists with phase `CLOSED` (and nothing else), **When** a volunteer tries to log in, **Then** login is rejected — `CLOSED` is not an active phase.

4. **Given** an admin attempts to log in, **When** no edition is active, **Then** login succeeds normally — the gate applies to `ROLE_VOLUNTEER` only.

5. **Given** a volunteer is already logged in and the current edition later transitions to `CLOSED`, **When** they navigate the app, **Then** their existing session remains valid until they log out (session invalidation on phase change is out of scope — handled in Story 2.8).

6. **Given** the backend returns 401 with type `no-active-edition`, **When** the frontend login component handles the error, **Then** the signal `error` is set to `'no-active-edition'` and the `NotificationInlineComponent` renders the corresponding message.

## Tasks / Subtasks

- [ ] **T1 — Backend: LoginSuccessHandler — volunteer edition gate** (AC: 1, 3, 4)
  - [ ] T1.1 — Inject `EditionRepository` into `LoginSuccessHandler` constructor
  - [ ] T1.2 — After the principal check, if role is `ROLE_VOLUNTEER`, call `editionRepository.existsByPhaseIn(ACTIVE_PHASES)`. If `false`, write a 401 Problem Details response with type `https://pluribourse/errors/no-active-edition` and return without completing the login
  - [ ] T1.3 — `ACTIVE_PHASES` constant: `List.of(PhaseType.PREPARATION, PhaseType.DEPOSIT, PhaseType.SALE, PhaseType.POST_SALE)` — defined as a private static final in `LoginSuccessHandler`

- [ ] **T2 — Backend: Integration test** (AC: 1, 3, 4, 5)
  - [ ] T2.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/VolunteerEditionGateIT.java`
  - [ ] T2.2 — Scenario: `@Order(1)` — volunteer login fails with 401 + type `no-active-edition` when no active edition
  - [ ] T2.3 — Scenario: `@Order(2)` — admin login succeeds when no active edition
  - [ ] T2.4 — Scenario: `@Order(3)` — create a PREPARATION edition (as admin), then volunteer login succeeds
  - [ ] T2.5 — Scenario: `@Order(4)` — advance edition to CLOSED (via `POST /api/admin/editions/{id}/phase/advance` × 4), then volunteer login fails again

- [ ] **T3 — Frontend: login.component.ts** (AC: 2, 6)
  - [ ] T3.1 — Add `'no-active-edition'` to the `error` signal union type: `signal<'invalid-credentials' | 'unauthorized-role' | 'account-disabled' | 'no-active-edition' | null>(null)`
  - [ ] T3.2 — In `catch`, add a branch before the fallback: `if (err?.error?.type === 'https://pluribourse/errors/no-active-edition') { this.error.set('no-active-edition'); }` (see Dev Notes — `err?.error?.type` is the existing pattern already used for `account-disabled`)

- [ ] **T4 — Frontend: login.component.html** (AC: 2)
  - [ ] T4.1 — Add a conditional `<app-notification-inline>` for the `no-active-edition` error — follow the exact same pattern as the `account-disabled` notification already present in the template (see Dev Notes)

- [ ] **T5 — Frontend: i18n keys** (AC: 2)
  - [ ] T5.1 — Add to `public/i18n/en.json` under `auth.login.error`:
    ```json
    "no-active-edition": "No edition is currently active. Please try again when an event is scheduled."
    ```
  - [ ] T5.2 — Add the French equivalent to `fr.json` (vouvoiement obligatoire):
    ```json
    "no-active-edition": "Aucune édition n'est en cours. Veuillez vous reconnecter lors du prochain événement."
    ```

- [ ] **T6 — Frontend: login.component.spec.ts** (AC: 2, 6)
  - [ ] T6.1 — Add a test: when `authService.login` rejects with `{ error: { type: 'https://pluribourse/errors/no-active-edition' } }`, `error()` signal equals `'no-active-edition'`
  - [ ] T6.2 — Run `npm test` — all tests must pass

## Dev Notes

### T1 — LoginSuccessHandler Modification

`LoginSuccessHandler` is a `@Component` — constructor injection of `EditionRepository` works directly. Current constructor is explicit (not `@RequiredArgsConstructor`) so add `EditionRepository` as a fourth parameter.

The check must happen **after** the principal cast succeeds and **before** language detection, so failed logins don't create side effects (language update). Exact insertion point:

```java
// After principal instanceof check, before language detection:
if (userDetails.getRole() == Role.VOLUNTEER &&
        !editionRepository.existsByPhaseIn(ACTIVE_PHASES)) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "No edition is currently active");
    pd.setType(URI.create("https://pluribourse/errors/no-active-edition"));
    pd.setTitle("No Active Edition");
    response.setContentType("application/problem+json");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    objectMapper.writeValue(response.getWriter(), pd);
    return;
}
```

`ACTIVE_PHASES` as private static final (mirrors the constant in `EditionService`):
```java
private static final List<PhaseType> ACTIVE_PHASES = List.of(
        PhaseType.PREPARATION, PhaseType.DEPOSIT, PhaseType.SALE, PhaseType.POST_SALE
);
```

**Session cleanup is required.** `onAuthenticationSuccess` is called AFTER `AbstractAuthenticationProcessingFilter.successfulAuthentication()` has already called `securityContextRepository.saveContext()` — the session has been created and written to the DB. Before returning the 401, clear the security context and invalidate the session to prevent an orphaned Spring Session row:

```java
SecurityContextHolder.clearContext();
HttpSession session = request.getSession(false);
if (session != null) {
    session.invalidate();
}
```

Add these three lines immediately before `objectMapper.writeValue(...)` in the gate block.

### T2 — Integration Test

Class: `org.pluribourse.edition.VolunteerEditionGateIT` — extends `IntegrationTest` (as all IT classes do). Test accounts available in `test-data.sql`: `test_admin` (ADMIN, password `admin`), `volunteer1` (VOLUNTEER, password `volunteer1`).

The `H2` test database starts empty for each class (`@DirtiesContext(classMode = AFTER_CLASS)` + `spring.liquibase.drop-first=true`). `test-data.sql` inserts users but **no editions** — so `@Order(1)` tests the "no edition" case without any setup.

For `@Order(3)` and `@Order(4)`, call the edition API to create and advance. Store the edition ID between methods as an instance field (allowed by `@TestInstance(PER_CLASS)`).

Advancing to CLOSED requires 4 POST calls to `POST /api/admin/editions/{id}/phase/advance` (PREPARATION → DEPOSIT → SALE → POST_SALE → CLOSED).

### T3 — Frontend Error Pattern

The existing pattern in `login.component.ts` for the `account-disabled` error:
```typescript
if (err?.error?.type === 'https://pluribourse/errors/account-disabled') {
    this.error.set('account-disabled');
} else {
    this.error.set('invalid-credentials');
}
```
Add the `no-active-edition` check **before** the `account-disabled` check (or in any order before the `else`):
```typescript
if (err?.error?.type === 'https://pluribourse/errors/no-active-edition') {
    this.error.set('no-active-edition');
} else if (err?.error?.type === 'https://pluribourse/errors/account-disabled') {
    this.error.set('account-disabled');
} else {
    this.error.set('invalid-credentials');
}
```

### T4 — HTML Template Pattern

Read `login.component.html` first. Replicate the same `@if (error() === '...')` guard pattern used for `account-disabled`:
```html
@if (error() === 'no-active-edition') {
  <app-notification-inline [message]="'auth.login.error.no-active-edition' | translate" />
}
```

### Project Structure Notes

- Backend security handlers: `pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/LoginSuccessHandler.java`
- Backend edition repository: `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java` — `existsByPhaseIn(List<PhaseType>)` already exists, no new query needed
- Frontend login component: `pluribourse-frontend/src/app/features/auth/login/login.component.ts` (and `.html`, `.spec.ts`)
- i18n files: `pluribourse-frontend/public/i18n/en.json` and `fr.json`
- Test infrastructure base class: `org.pluribourse.shared.IntegrationTest`
- Test data: `src/test/resources/db/changelog/test-data.sql` — `volunteer1` / `volunteer2` exist, no edition exists

### References

- [Source: epics.md#FR-010] One active edition at a time — active = PREPARATION through POST_SALE
- [Source: security/handlers/LoginSuccessHandler.java] Existing handler structure and injection pattern
- [Source: edition/repository/EditionRepository.java] `existsByPhaseIn` query already available
- [Source: edition/service/EditionService.java#ACTIVE_PHASES] Canonical list of active phases
- [Source: features/auth/login/login.component.ts] Error signal union type and catch block pattern

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
