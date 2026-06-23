---
baseline_commit: 14ee3fcc13327e8c26a0c1822b8fb95765769a03
---

# Story 1.4: Admin Password Recovery via CLI

Status: done

## Story

As an administrator who forgot their password,
I want to reset it via a server-side command,
so that I can recover access without developer intervention or direct database manipulation.

## Acceptance Criteria

1. **Given** the admin has forgotten their password, **When** the application is launched with the argument `--reset-admin-password`, **Then** a new temporary password (12+ characters, alphanumeric) is printed to the console, **And** the admin account password is updated in the database (BCrypt-encoded), **And** the `forcePasswordChange` flag is set to `true` on the admin account.

2. **Given** the temporary password has been generated, **When** the admin logs in with this password, **Then** they are immediately redirected to the mandatory password change page, **And** they cannot access any other page until the password has been changed.

## Tasks / Subtasks

- [x] **T1 — Backend: `AdminPasswordResetRunner`** (AC: 1)
  - [x] T1.1 — Create `org.pluribourse.user.cli.AdminPasswordResetRunner` implementing `ApplicationRunner`
  - [x] T1.2 — Inject `UserRepository`, `PasswordEncoder`, `ApplicationContext`; annotate with `@Component`
  - [x] T1.3 — In `run(ApplicationArguments args)`: if args does NOT contain option `reset-admin-password`, return immediately (normal boot continues)
  - [x] T1.4 — Generate a 12-character alphanumeric password using `SecureRandom` (see Dev Notes for implementation)
  - [x] T1.5 — Extract a package-private `@Transactional void performReset()` method (see Dev Notes): load admin via `findByRole(ADMIN)`, throw `IllegalStateException` if empty, encode password, set `forcePasswordChange=true`, save, print to `System.out`
  - [x] T1.6 — In `run()`: call `performReset()` then `System.exit(SpringApplication.exit(applicationContext, () -> 0))` — do NOT wrap in try/catch

- [x] **T2 — Tests** (coverage ≥ 80%)
  - [x] T2.1 — `AdminPasswordResetRunnerTest.java` (unit, `@ExtendWith(MockitoExtension.class)`): mock `UserRepository`, `PasswordEncoder`, `ApplicationContext`
    - Test: `run_without_reset_flag_does_nothing()` — args do NOT contain `reset-admin-password`, `verifyNoInteractions` on repo and encoder
    - Test: `performReset_encodes_password_and_sets_force_change_flag()` — call `performReset()` directly, verify `forcePasswordChange=true` and encoded password saved
    - Test: `performReset_throws_when_no_admin_found()` — call `performReset()` directly, `findByRole(ADMIN)` returns empty → `IllegalStateException`
  - [x] T2.2 — AC2 is already covered by Story 1.2's `ForcePasswordChangeFilter` tests — no new integration test needed. Confirmed: `PasswordChangeFlowIT` (11 tests) covers the `forcePasswordChange=true` redirect flow end-to-end.

### Review Findings

- [x] [Review][Decision] `@Transactional` supprimé de `performReset()` — un seul `save()` par runner, Spring Data JPA est atomique à ce niveau. [AdminPasswordResetRunner.java]
- [x] [Review][Patch] Multiples comptes ADMIN : warning ajouté quand `admins.size() > 1` [AdminPasswordResetRunner.java]
- [x] [Review][Patch] `applicationContext` ajouté dans `verifyNoInteractions` du test early-return [AdminPasswordResetRunnerTest.java]
- [x] [Review][Patch] Format 12-car alphanumérique asserté via `ArgumentCaptor` + `.hasSize(12).matches("[A-Za-z0-9]+")` [AdminPasswordResetRunnerTest.java]
- [x] [Review][Patch] `System.out` capturé et mot de passe asserté dans la sortie console (AC1) [AdminPasswordResetRunnerTest.java]

## Dev Notes

### No frontend changes needed

The mandatory password change redirect (`forcePasswordChange=true` → `/change-password`) is fully implemented by `ForcePasswordChangeFilter` (Story 1.2). AC2 requires zero new frontend code.

### `AdminPasswordResetRunner` implementation

```java
package org.pluribourse.user.cli;

import lombok.RequiredArgsConstructor;
import org.pluribourse.user.enums.Role;
import org.pluribourse.user.repositories.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdminPasswordResetRunner implements ApplicationRunner {

    private static final String RESET_OPTION = "reset-admin-password";
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(RESET_OPTION)) {
            return;
        }
        performReset();
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }

    @Transactional
    void performReset() {
        var admins = userRepository.findByRole(Role.ADMIN);
        if (admins.isEmpty()) {
            throw new IllegalStateException("No ADMIN account found in the database.");
        }
        var admin = admins.getFirst();
        var temporaryPassword = generatePassword();
        admin.setPassword(passwordEncoder.encode(temporaryPassword));
        admin.setForcePasswordChange(true);
        userRepository.save(admin);

        System.out.println("=== PluriBourse Admin Password Reset ===");
        System.out.println("Temporary password: " + temporaryPassword);
        System.out.println("Log in and change your password immediately.");
        System.out.println("========================================");
    }

    private String generatePassword() {
        var random = new SecureRandom();
        return random.ints(PASSWORD_LENGTH, 0, CHARS.length())
                .mapToObj(i -> String.valueOf(CHARS.charAt(i)))
                .collect(Collectors.joining());
    }
}
```

### CLI invocation (from architecture FR-063)

```bash
# In Docker Compose deployment:
docker compose exec pluribourse java -jar pluribourse.jar --reset-admin-password

# Or via Maven in dev:
./mvnw spring-boot:run -Dspring-boot.run.arguments=--reset-admin-password
```

### Key behavioural constraints

- **`args.containsOption("reset-admin-password")`**: Spring parses `--reset-admin-password` as an option with no value. Use `containsOption()`, NOT `getNonOptionArgs()`.
- **`findByRole(Role.ADMIN)` already exists** in `UserRepository` (added in Story 1.3). Do NOT add a new query method.
- **`System.exit(SpringApplication.exit(...))`**: `SpringApplication.exit()` returns an int exit code; pass it to `System.exit()`. This cleanly shuts down the context and JVM. The full Spring context (including web server) starts briefly — acceptable for a CLI recovery tool.
- **Do NOT log the generated password** via SLF4J/Logback (NFR-007 no-PII in logs). Print ONLY to `System.out`.
- **`forcePasswordChange` is a primitive `boolean`** on `User` (not `Boolean`) — use `setForcePasswordChange(true)` directly.

### Unit test pattern

Tests call `performReset()` directly for the happy path (avoids `System.exit()` entirely). `run()` is tested only for the early-return branch.

```java
@ExtendWith(MockitoExtension.class)
class AdminPasswordResetRunnerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationContext applicationContext;
    @InjectMocks private AdminPasswordResetRunner runner;

    @Test
    void run_without_reset_flag_does_nothing() throws Exception {
        var args = mock(ApplicationArguments.class);
        when(args.containsOption("reset-admin-password")).thenReturn(false);

        runner.run(args);

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void performReset_encodes_password_and_sets_force_change_flag() {
        var admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setForcePasswordChange(false);
        admin.setEnabled(true);
        admin.setPassword("oldEncoded");

        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(passwordEncoder.encode(anyString())).thenReturn("newEncoded");
        when(userRepository.save(any())).thenReturn(admin);

        runner.performReset();

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isForcePasswordChange()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("newEncoded");
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void performReset_throws_when_no_admin_found() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());

        assertThatThrownBy(() -> runner.performReset())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No ADMIN account");
    }
}
```

### Extract `performReset()` for testability — required pattern

Split implementation so the business logic is testable without triggering `System.exit()`. Use `@Transactional` to make the read-modify-save atomic:

```java
// In AdminPasswordResetRunner:

@Override
public void run(ApplicationArguments args) {
    if (!args.containsOption(RESET_OPTION)) return;
    performReset();
    System.exit(SpringApplication.exit(applicationContext, () -> 0));
}

// package-private: called directly by tests
@Transactional
void performReset() {
    var admins = userRepository.findByRole(Role.ADMIN);
    if (admins.isEmpty()) {
        throw new IllegalStateException("No ADMIN account found in the database.");
    }
    var admin = admins.getFirst();
    var temporaryPassword = generatePassword();
    admin.setPassword(passwordEncoder.encode(temporaryPassword));
    admin.setForcePasswordChange(true);
    userRepository.save(admin);
    System.out.println("=== PluriBourse Admin Password Reset ===");
    System.out.println("Temporary password: " + temporaryPassword);
    System.out.println("Log in and change your password immediately.");
    System.out.println("========================================");
}
```

Add `import org.springframework.transaction.annotation.Transactional;` — already on classpath (Spring Data JPA).

Test `performReset()` directly — no `System.exit()` involved, no `ApplicationContext` mock needed in the happy-path test.

### Error behavior — do NOT mask exceptions

If `findByRole(ADMIN)` returns an empty list, `IllegalStateException` propagates through Spring's runner mechanism and the JVM exits with a non-zero code (stack trace printed to stderr). This is the correct behavior: the operator sees what went wrong. **Do NOT add a try/catch around `performReset()` in `run()`** — silent failures are worse than visible ones for a recovery CLI tool.

### No Liquibase changeset needed

Story 1.4 uses only existing columns: `password`, `force_password_change`. Both already exist from changeset `001-core-schema.xml`. No DB schema changes.

### `UserRepository.findByRole(Role)` — already exists

Added in Story 1.3 (used by `UserService.listVolunteers()`). The runner reuses it directly.

### Patterns from previous stories

- Lombok `@RequiredArgsConstructor` injects all `final` fields — consistent with `UserService`, `UserController`
- `@Component` annotation — the runner is a Spring bean, conditional on `--reset-admin-password` at runtime, not at Spring context load time
- `PasswordEncoder` injected — same bean used in `UserService`; no need to instantiate `BCryptPasswordEncoder` directly

## Project Structure Notes

**Backend — new file only:**
- `pluribourse-backend/src/main/java/org/pluribourse/user/cli/AdminPasswordResetRunner.java`

**Test — new file:**
- `pluribourse-backend/src/test/java/org/pluribourse/user/AdminPasswordResetRunnerTest.java`

**No changes to:**
- `UserRepository` — `findByRole(Role)` already exists
- `UserService` — no new service method needed; runner accesses repository directly (justified: CLI tool, not a business flow)
- Liquibase changesets — no new columns
- Frontend — `ForcePasswordChangeFilter` already handles AC2
- `PluribourseApplication.java` — no changes; runner is a `@Component`, auto-discovered

## References

- [Source: epics.md#Story 1.4] — user story and acceptance criteria
- [Source: epics.md#FR-063] — CLI reset: Spring Boot `CommandLineRunner` or script; forces password change on next login
- [Source: architecture.md#Authentification & Sécurité] — BCrypt, `forcePasswordChange` mechanism, role model
- [Source: architecture.md#Backend — Structure de Répertoires Complète] — `user/cli/AdminPasswordResetRunner.java` file location
- [Source: architecture.md#Directives d'Application] — no PII in logs (use `System.out` not SLF4J for the password)
- [Source: 1-2-authentification-spring-security-controle-dacces-base-sur-les-roles.md] — `ForcePasswordChangeFilter` already handles `forcePasswordChange=true` redirect (AC2 already covered)
- [Source: 1-3-gestion-des-comptes-benevoles.md#Dev Notes] — `findByRole(Role)` added to `UserRepository`; `User.forcePasswordChange` is primitive `boolean`
- [Source: 1-3-gestion-des-comptes-benevoles.md#Dev Agent Record] — package naming: plural (`dtos`, `controllers`, `mappers`); `cli` is singular per architecture diagram

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Test class initially placed in `org.pluribourse.user` package but `performReset()` is package-private in `org.pluribourse.user.cli`. Moved test to `org.pluribourse.user.cli` package to allow direct access.

### Completion Notes List

- Implemented `AdminPasswordResetRunner` as a Spring `ApplicationRunner` component. Early-return pattern when `--reset-admin-password` flag is absent ensures zero impact on normal application startup.
- `performReset()` is package-private to enable direct unit testing without triggering `System.exit()`.
- AC2 (force password change redirect) confirmed covered by existing `PasswordChangeFlowIT` (Story 1.2) — no new integration test required.
- All 33 tests pass (3 new unit tests + 30 existing tests), no regressions.

### File List

- `pluribourse-backend/src/main/java/org/pluribourse/user/cli/AdminPasswordResetRunner.java` (new)
- `pluribourse-backend/src/test/java/org/pluribourse/user/cli/AdminPasswordResetRunnerTest.java` (new)

## Change Log

- 2026-06-22 — Story 1.4 implemented: added `AdminPasswordResetRunner` (CLI password reset) and 3 unit tests. All 33 tests pass.
- 2026-06-22 — Code review patches applied: removed `@Transactional`, added multi-admin warning, fixed `verifyNoInteractions`, added password format and stdout assertions, added `performReset_warns_when_multiple_admins_found` test. All 34 tests pass.
