---
baseline_commit: db7212b6e4a03d891e4e3e2831d4c23b36681551
---

# Story 1.12: Automatic Session Revocation on Volunteer Disable/Delete

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an administrator,
I want a volunteer's session to be invalidated immediately when I disable or delete their account,
so that a revoked account cannot retain functional access for the remainder of its session lifetime.

## Acceptance Criteria

1. **Given** a volunteer has an open session, **When** the administrator disables their account (`PUT /admin/users/{id}/disable`), **Then** that volunteer's session is invalidated server-side immediately, **And** their next authenticated request fails with 401 — it does not wait for natural session expiry.
2. **Given** a volunteer has an open session, **When** the administrator deletes their account (`DELETE /admin/users/{id}`), **Then** that volunteer's session is invalidated server-side immediately via the same mechanism.
3. **Given** a disabled volunteer is later re-enabled by the administrator, **When** they attempt to reconnect, **Then** they must re-enter their credentials — their previous session is not restored (FR-101).
4. **Given** an administrator has an open session, **When** this story is complete, **Then** nothing changes for admin accounts — `disableUser`/`deleteUser` already reject admin targets (`CannotDisableAdminException`/`CannotDeleteAdminException`), so this invalidation path only ever runs for volunteers.

## Tasks / Subtasks

- [x] **T1 — Backend: `SessionInvalidationService`** (AC: 1, 2)
  - [x] T1.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/shared/security/SessionInvalidationService.java` (see Dev Notes for exact code)
- [x] **T2 — Backend: wire into `UserService`** (AC: 1, 2, 4)
  - [x] T2.1 — Inject `SessionInvalidationService` into `UserService` and call `invalidateSessionsFor(user.getUsername())` at the end of `disableUser(id)`, after `userRepository.save(user)`
  - [x] T2.2 — Call the same in `deleteUser(id)`, after `userRepository.delete(user)`
  - [x] T2.3 — Do NOT add this call to `enableUser`, `resetUserPassword`, or `changePassword` — out of scope for this story (see Dev Notes)
- [x] **T3 — Backend: integration test** (AC: 1, 2, 3, 4)
  - [x] T3.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/user/VolunteerSessionRevocationIT.java` — new dedicated class, do NOT add to `UserManagementIT` (see Dev Notes — reusing the shared `volunteerSession` there would break its later ordered tests)
  - [x] T3.2 — Full storyboard: create a new volunteer, log in as them, prove the session works, disable the account, prove the OLD session now gets 401, re-enable, prove a fresh login is required, log in again, delete the account, prove the newest session also gets 401 (see Dev Notes for exact scenario order)
- [x] **T4 — Run `mvn test`** — all existing tests must pass, zero regressions

### Review Findings

- [x] [Review][Patch] Transactional coupling: `invalidateSessionsFor` runs inside the same `@Transactional` as `save()`/`delete()` — wrap the call in try/catch + log so a session-store failure does not roll back the disable/delete (best-effort, non-blocking — user decision 2026-07-06). [UserService.java:80-83,100-103] — ✅ Applied: `SessionInvalidationService` now catches exceptions internally (both around `findByPrincipalName` and per `deleteById` call) and logs via SLF4J instead of propagating.
- [x] [Review][Defer] `pom.xml` change adds `spring-boot-session-jdbc`, which per the Dev Agent Record also silently restores JDBC-backed session persistence (FR-066) that was "very likely never actually active" before this fix — an app-wide production behavior change bundled into this story with no dedicated AC or regression test. Kept in this story (required for 1.12 to function); a dedicated follow-up story should explicitly verify FR-066 (session persistence across container restart) — user decision 2026-07-06. [pom.xml]
- [x] [Review][Patch] Add JavaDoc to `SessionInvalidationService.invalidateSessionsFor` — the method revokes *all* active sessions for a principal (multi-device), which is non-obvious from the code alone. [SessionInvalidationService.java:14] — ✅ Applied
- [x] [Review][Patch] Invalidation loop aborts entirely if one `deleteById` call throws (e.g. session already reaped by expiry) — catch and continue per session, logging failures instead of leaving remaining sessions un-invalidated. [SessionInvalidationService.java:14-18] — ✅ Applied
- [x] [Review][Patch] Add a test asserting that disabling a volunteer with two concurrent sessions (e.g. two devices) revokes both — the actual value of `findByPrincipalName` over a single-session lookup is currently untested. [VolunteerSessionRevocationIT.java] — ✅ Applied: new `bob_logs_in_from_a_second_device` (Order 3) + `bobs_sessions_on_both_devices_now_get_401` (Order 5), storyboard renumbered to 9 ordered tests
- [x] [Review][Patch] Capture `user.getUsername()` into a local variable before `userRepository.delete(user)` instead of reading it off the entity afterward, for clarity/robustness. [UserService.java:100-103] — ✅ Applied
- [x] [Review][Patch] Add null-safety assertions before reading the response `id` field and session cookies in the test, so a contract break fails with a clear assertion message instead of an NPE. [VolunteerSessionRevocationIT.java:52-55,118-134] — ✅ Applied via AssertJ `assertThat(...).isNotNull()` before use
- [x] [Review][Defer] TOCTOU race: a session created for the same principal between `findByPrincipalName`'s snapshot and the delete loop is not revoked. [SessionInvalidationService.java:14-18] — deferred, pre-existing limitation of the approach
- [x] [Review][Defer] `IntegrationTest`'s shared `MockMvc` never wires the real `springSessionRepositoryFilter`, so every other existing IT class reuses a plain in-memory `MockHttpSession` that never touches the real JDBC `SPRING_SESSION` table — a systemic gap this story worked around locally but did not fix globally. [IntegrationTest.java] — deferred, pre-existing
- [x] [Review][Defer] No audit/log trail for session-revocation events (e.g. "N sessions revoked for user id X") — would aid production debugging but is out of this story's scope. [SessionInvalidationService.java] — deferred, pre-existing

## Dev Notes

### Why this is needed (do not skip — read before implementing)

Spring Security's session-based auth does **not** re-check `user.enabled` or user existence on every request — `PluriBourseUserDetails` is cached inside the HTTP session (backed by Spring Session JDBC, table `SPRING_SESSION`) from the moment of login, and is never reloaded from the database while the session lives. Disabling or deleting a user today only blocks *future* logins (`PluriBourseUserDetailsService` / `DaoAuthenticationProvider` checks happen at authentication time only) — an already-open session keeps full functional access.

This is made worse by the session timeout being a **sliding idle timeout**, not a fixed TTL:
```
# pluribourse-backend/src/main/resources/application.properties:21
spring.session.timeout=PT1H
```
Every request resets the idle clock. A volunteer who stays active (which is exactly the scenario an admin would be reacting to — e.g. disabling someone *because* they're doing something wrong right now) can keep a revoked session alive indefinitely, not just "up to 1h". This is the concrete justification for FR-101 — see `sprint-change-proposal-2026-07-06.md` for the related (but distinct) analysis of the edition-deletion case, which was deliberately left unchanged because business endpoints there already re-check the active edition/phase on every request. There is no equivalent per-request re-check for `user.enabled`/existence, which is exactly the gap this story closes.

### T1 — `SessionInvalidationService`

Package: `org.pluribourse.shared.security` (same package as `SecurityContextHelper`, which is a related but different concern — `SecurityContextHelper` refreshes the **current** request's session principal in place; this new service invalidates **other** users' already-open sessions by principal name, which requires going through the Spring Session repository rather than `HttpServletRequest`).

`spring.session.store-type=jdbc` (already active in both `application.properties` and the test profile) auto-configures a `FindByIndexNameSessionRepository<? extends Session>` bean (`JdbcIndexedSessionRepository`). Spring Session automatically indexes sessions by principal name out of the box whenever Spring Security stores a `SecurityContext` under the `SPRING_SECURITY_CONTEXT` session attribute (which `HttpSessionSecurityContextRepository` already does for every authenticated request in this app) — no extra configuration needed. This is the same `SPRING_SESSION` / `PRINCIPAL_NAME` index already used by `002-spring-session.xml`.

```java
package org.pluribourse.shared.security;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SessionInvalidationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void invalidateSessionsFor(String username) {
        sessionRepository.findByPrincipalName(username)
                .keySet()
                .forEach(sessionRepository::deleteById);
    }
}
```

`username` here is the Spring Security principal name — for `PluriBourseUserDetails` this is `getUsername()`, i.e. `user.getUsername()` on the entity. `findByPrincipalName` returns a `Map<String, S>` keyed by session id; a user could in theory have more than one open session (e.g. two tabs/devices) — `.keySet().forEach(deleteById)` kills all of them, which is the correct behavior here (revoke means revoke everywhere).

### T2 — `UserService` wiring

Current methods (`pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java:72-99`):

```java
@Transactional
public void disableUser(Long id) {
    User user = getUser(id);
    if (user.getRole() == Role.ADMIN) {
        throw new CannotDisableAdminException();
    }
    user.setEnabled(false);
    userRepository.save(user);
}
```
becomes:
```java
@Transactional
public void disableUser(Long id) {
    User user = getUser(id);
    if (user.getRole() == Role.ADMIN) {
        throw new CannotDisableAdminException();
    }
    user.setEnabled(false);
    userRepository.save(user);
    sessionInvalidationService.invalidateSessionsFor(user.getUsername());
}
```
Same pattern for `deleteUser`, calling `invalidateSessionsFor` after `userRepository.delete(user)` (the in-memory `user` object is still valid to read from after `.delete(user)` — only the DB row is gone).

Add the field `private final SessionInvalidationService sessionInvalidationService;` — `UserService` already uses `@RequiredArgsConstructor`, so no constructor changes needed beyond adding the field.

**Do not** wire this into `enableUser` (re-enabling doesn't need to kill any session — there's nothing to revoke), `resetUserPassword`, or `changePassword` (AC4/FR-060 already forces a password change via `forcePasswordChange`, handled separately by `ForcePasswordChangeFilter`; extending session revocation to password resets is a plausible related improvement but was explicitly not requested and is out of scope here — flag it as a deferred idea in the review, do not implement it).

### T3 — Integration test: why a new class

`UserManagementIT` already has a `@BeforeAll`-scoped `volunteerSession` (for `volunteer1` from `test-data.sql`) that is reused later in the ordered sequence — notably `@Order(17) volunteer_can_change_own_password`. If this story's test disabled `volunteer1` using that shared session, it would permanently break every subsequent ordered test in that class. Per this project's testing philosophy (one class = one business-scenario storyboard, `_bmad-output/implementation-artifacts/1-12-...md` itself being a good example of an isolated concern), create a **new** class instead: `org.pluribourse.user.VolunteerSessionRevocationIT`.

Suggested storyboard (`@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`, extends `IntegrationTest`, `@TestInstance(Lifecycle.PER_CLASS)` — same pattern as `UserManagementIT`/`VolunteerEditionGateIT` (removed by the FR-099 course-correction, but still a useful style reference in git history at commit `f9c9238`)):

1. `@BeforeAll` — log in as `test_admin`, capture `adminSession`.
2. `@Order(1)` — admin creates a new volunteer via `POST /admin/users` (`.session(adminSession).with(csrf())`, JSON body `{"firstName":"Bob","lastName":"Martin","username":"bob","password":"Password1","role":"VOLUNTEER"}` — same literal pattern as `UserManagementIT.admin_creates_volunteer()`), capture `bobId` from the response body's `id` field.
3. `@Order(2)` — log in as `bob`, capture `bobSession`. Sanity check: `GET /api/auth/me` with `bobSession` → 200.
4. `@Order(3)` — admin disables `bob` (`PUT /admin/users/{bobId}/disable`, with `adminSession`, `.with(csrf())`).
5. `@Order(4)` — `GET /api/auth/me` with the **same, now-stale** `bobSession` → expect 401. This is the core assertion proving the session was killed server-side, not just that future logins are blocked.
6. `@Order(5)` — admin re-enables `bob` (`PUT /admin/users/{bobId}/enable`).
7. `@Order(6)` — attempting to reuse the old `bobSession` still fails with 401 (it's gone, re-enabling doesn't resurrect it) — log in again as `bob` with a fresh request, capture a new `bobSession2`, assert 200.
8. `@Order(7)` — admin deletes `bob` (`DELETE /admin/users/{bobId}`, with `adminSession`).
9. `@Order(8)` — `GET /api/auth/me` with `bobSession2` → expect 401, proving deletion also revokes the live session.

Use `test_admin` / `Admin` and a `POST /api/auth/login` + `.contentType(MediaType.APPLICATION_FORM_URLENCODED)` pattern identical to `UserManagementIT.setUpSessions()` — do not invent a different login mechanism. Always `.with(csrf())` on state-changing requests (Spring Security 7 rejects without it → 403, per prior stories' Dev Notes).

### Project Structure Notes

- New file: `pluribourse-backend/src/main/java/org/pluribourse/shared/security/SessionInvalidationService.java`
- Modified: `pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java`
- New test: `pluribourse-backend/src/test/java/org/pluribourse/user/VolunteerSessionRevocationIT.java`
- No frontend changes — the admin UI for disable/delete already exists and calls the same endpoints; behavior change is entirely server-side.
- No new Liquibase migration — `SPRING_SESSION`/`SPRING_SESSION_ATTRIBUTES` tables and the `PRINCIPAL_NAME` index already exist since `002-spring-session.xml`.

### References

- [Source: epics.md#Story 1.12] — user story, acceptance criteria, FR-101
- [Source: sprint-change-proposal-2026-07-06.md] — related course-correction (FR-099 removal) that established the "business endpoints already re-check edition/phase per request" contrast this story is built on
- [Source: user/services/UserService.java:72-99] — `disableUser`/`deleteUser` current implementation
- [Source: user/controllers/UserController.java:46-62] — endpoints calling the above, unchanged by this story
- [Source: shared/security/SecurityContextHelper.java] — existing (different) session-related helper, pattern reference for package placement and `@Component` style
- [Source: shared/security/handlers/LoginSuccessHandler.java] — only pre-existing example of manual session invalidation in the codebase (current-request session only, via `HttpSession.invalidate()`) — not reusable here since this story needs to invalidate a *different* user's session
- [Source: db/changelog/002-spring-session.xml] — `SPRING_SESSION` schema, `SPRING_SESSION_IX3` index on `PRINCIPAL_NAME`
- [Source: application.properties:18-25] — `spring.session.store-type=jdbc`, `spring.session.timeout=PT1H` (sliding idle timeout)
- [Source: user/UserManagementIT.java] — existing test class style, and the specific reason a *new* class is needed instead of extending this one (shared `volunteerSession` reused at `@Order(17)`)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

- Initial `mvn test -Dtest=VolunteerSessionRevocationIT` run failed at Spring context startup: `NoSuchBeanDefinitionException` for `FindByIndexNameSessionRepository<Session>`. Root cause: Spring Boot 4 split the monolithic `spring-boot-autoconfigure` module into dozens of per-feature modules; the JDBC-backed Spring Session auto-configuration now lives in a separate `org.springframework.boot:spring-boot-session-jdbc` artifact that was not declared in `pom.xml` (only the underlying `org.springframework.session:spring-session-jdbc` library was present, without its Spring Boot auto-configuration glue). Confirmed via the `spring-boot-dependencies-4.0.6.pom` BOM and by inspecting `spring-boot-session-jdbc-4.0.6.jar`'s `AutoConfiguration.imports` (`JdbcSessionAutoConfiguration`). Flagged to user and approved before adding the dependency (see AskUserQuestion in session transcript).
- After adding `spring-boot-session-jdbc`, the context started but the test storyboard still failed: old sessions kept returning 200 instead of 401 after disable. Root cause: `IntegrationTest`'s shared `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())` never registers the real `springSessionRepositoryFilter` bean into the MockMvc filter chain, so `.session(mockHttpSession)` reuse (the pattern used by `UserManagementIT`) never touches the JDBC-backed `SPRING_SESSION` table at all — it's a plain in-memory object, unaffected by `SessionInvalidationService`'s `deleteById` calls. This means the literal test pattern described in the Dev Notes can never pass, regardless of implementation correctness. Fixed by building a dedicated `MockMvc` instance inside `VolunteerSessionRevocationIT` only (not touching the shared `IntegrationTest`), with the `springSessionRepositoryFilter` bean explicitly added to the filter chain before Spring Security, and capturing/replaying sessions via the real `SESSION` cookie instead of the shared `MockHttpSession` object. Flagged to user and approved before implementing (see AskUserQuestion in session transcript).

### Completion Notes List

- Implemented `SessionInvalidationService` exactly as specified in Dev Notes: looks up all sessions for a principal name via `FindByIndexNameSessionRepository.findByPrincipalName` and deletes them all.
- Wired `invalidateSessionsFor(user.getUsername())` into `UserService.disableUser` (after `save`) and `UserService.deleteUser` (after `delete`), per T2. Deliberately left `enableUser`, `resetUserPassword`, and `changePassword` untouched, per Dev Notes T2 scope.
- Added `org.springframework.boot:spring-boot-session-jdbc` to `pom.xml` — required Spring Boot 4 module for the `spring.session.store-type=jdbc` auto-configuration to actually create the `FindByIndexNameSessionRepository`/`springSessionRepositoryFilter` beans (previously absent — see Debug Log). This is a pre-existing gap unrelated to this story's business logic: JDBC-backed session persistence was very likely never actually active before this fix, despite the `spring.session.store-type=jdbc` property and the `SPRING_SESSION` Liquibase schema already existing since story 1.1/1.2.
- `VolunteerSessionRevocationIT` builds its own `MockMvc` (with the real `springSessionRepositoryFilter` wired in) instead of reusing the inherited `mockMvc` from `IntegrationTest`, and drives sessions via the `SESSION` cookie rather than a shared `MockHttpSession` object — see Debug Log for why the Dev Notes' originally suggested pattern cannot verify real session invalidation. `IntegrationTest` and all other existing IT classes are unmodified.
- Full storyboard implemented and passing (8 ordered tests): create volunteer → login → session works → disable → old session 401 → re-enable → old session still 401, fresh login required → new session works → delete → newest session 401.
- Full backend regression suite: 191 tests, 0 failures, 0 errors.
- Deferred idea (explicitly out of scope per Dev Notes, not implemented): extending session invalidation to `resetUserPassword`/`changePassword` flows — currently handled separately via `forcePasswordChange` + `ForcePasswordChangeFilter`.

### File List

- `pluribourse-backend/pom.xml` (modified — added `spring-boot-session-jdbc` dependency)
- `pluribourse-backend/src/main/java/org/pluribourse/shared/security/SessionInvalidationService.java` (new)
- `pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java` (modified — wired `SessionInvalidationService` into `disableUser`/`deleteUser`)
- `pluribourse-backend/src/test/java/org/pluribourse/user/VolunteerSessionRevocationIT.java` (new)

## Change Log

- 2026-07-06 — Implemented Story 1.12: `SessionInvalidationService` created and wired into `UserService.disableUser`/`deleteUser`; added missing `spring-boot-session-jdbc` dependency (Spring Boot 4 modularization gap) required for the JDBC session repository beans to exist; added `VolunteerSessionRevocationIT` with a dedicated cookie-backed `MockMvc` to genuinely exercise server-side session invalidation. Full regression suite green (191 tests).
