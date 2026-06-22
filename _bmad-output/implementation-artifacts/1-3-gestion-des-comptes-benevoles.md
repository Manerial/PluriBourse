---
baseline_commit: 2ecba4e
---

# Story 1.3: Volunteer Account Management

Status: done

## Story

As an administrator,
I want to create, update, disable volunteer accounts and reset their passwords,
so that I control who has access to the application during the event.

## Acceptance Criteria

1. **Given** the admin navigates to `/admin/users`, **When** the page loads, **Then** all volunteer accounts are listed with name, status (active/inactive), and role.

2. **Given** the admin fills in first name, last name, username and password for a new volunteer, **When** the form is submitted, **Then** a VOLUNTEER account is created and the volunteer can log in immediately.

3. **Given** the admin resets a volunteer's password, **When** the reset is submitted, **Then** the volunteer's password is updated and the volunteer is forced to change it on next login.

4. **Given** the admin disables a volunteer account, **When** that volunteer attempts to log in, **Then** login is refused with a clear "Account disabled" message.

5. **Given** an admin account already exists, **When** the admin attempts to create a second admin via `POST /api/admin/users`, **Then** the system always creates a VOLUNTEER — the endpoint accepts no `role` field, structurally preventing a second ADMIN (FR-061: one admin per instance).

## Tasks / Subtasks

- [x] **T1 — Database: add `enabled`, `first_name`, `last_name` columns** (AC: 2, 4)
  - [x] T1.1 — Create `005-user-volunteer-fields.xml` Liquibase changeset: add `first_name VARCHAR(50) NOT NULL DEFAULT ''`, `last_name VARCHAR(50) NOT NULL DEFAULT ''`, `enabled BOOLEAN NOT NULL DEFAULT TRUE` to `users` table
  - [x] T1.2 — Register the new changeset in `db.changelog-master.xml` after `002-spring-session.xml`
  - [x] T1.3 — Update H2 test variant if one exists for session tests (none expected for this changeset)

- [x] **T2 — Update `User` entity and `PluriBourseUserDetails`** (AC: 2, 4)
  - [x] T2.1 — Add `firstName` (`VARCHAR(50)`), `lastName` (`VARCHAR(50)`), `enabled` (`Boolean` — nullable wrapper, **not** primitive `boolean`) fields to `User.java`; use `@Column(name = "first_name")`, `@Column(name = "last_name")`, `@Column(nullable = false)`. The nullable wrapper is required: primitive `boolean` defaults to `false` during Java deserialization of existing Spring Session JDBC sessions, which would lock out the admin on upgrade.
  - [x] T2.2 — Override `isEnabled()` in `PluriBourseUserDetails` with null-safe check: `return user.getEnabled() == null || user.getEnabled()` — `null` maps to `true` (safe default for old deserialized sessions); resolves the deferred Story 1.2 item

- [x] **T3 — Update `LoginFailureHandler` for disabled account error** (AC: 4)
  - [x] T3.1 — Import `DisabledException`; check `if (exception instanceof DisabledException)` → return RFC 7807 `{"type": "https://pluribourse/errors/account-disabled", "title": "Account Disabled", "status": 401, "detail": "This account has been disabled"}` — otherwise return the existing "Invalid username or password" response

- [x] **T4 — Backend: DTOs, Mapper, UserController, UserService methods** (AC: 1, 2, 3, 4, 5)
  - [x] T4.1 — Create `UserDto` record in `org.pluribourse.user.dtos`: `Long id, String firstName, String lastName, String username, String role, boolean enabled`
  - [x] T4.2 — Create `CreateUserDto` record in `org.pluribourse.user.dtos`: `@NotBlank @Size(max=50) String firstName`, `@NotBlank @Size(max=50) String lastName`, `@NotBlank @Size(max=50) String username`, `@NotBlank @Size(min=8, max=128) String password` — no `role` field; role is always VOLUNTEER
  - [x] T4.3 — Create `ResetPasswordDto` record in `org.pluribourse.user.dtos`: `@NotBlank @Size(min=8, max=128) String newPassword`
  - [x] T4.4 — Create `UserMapper` interface in `org.pluribourse.user.mappers` with `@Mapper(componentModel = "spring")`: `UserDto toDto(User user)` — MapStruct derives all fields by name
  - [x] T4.5 — Add `private final UserMapper userMapper;` as a new field to `UserService` (`@RequiredArgsConstructor` will inject it automatically). Add methods: `listVolunteers()`, `createVolunteer(CreateUserDto)`, `resetVolunteerPassword(Long id, String newPassword)`, `disableVolunteer(Long id)`, `enableVolunteer(Long id)`
    - All find-by-id calls: throw `BusinessException(NOT_FOUND, "user-not-found", ...)` when user not found
    - `createVolunteer()`: call `userRepository.existsByUsername(dto.username())` before save — throw `BusinessException(CONFLICT, "username-already-taken", "Username already taken")` if true
    - `disableVolunteer()`: throw `BusinessException(FORBIDDEN, "cannot-disable-admin", "Admin account cannot be disabled")` if `user.getRole() != Role.VOLUNTEER`
  - [x] T4.6 — Create `UserController` in `org.pluribourse.user.controllers` at `/api/admin/users` with:
    - `GET /api/admin/users` → `listVolunteers()` → `List<UserDto>` 200
    - `POST /api/admin/users` → `createVolunteer()` → `UserDto` 201 (Location header: `/api/admin/users/{id}`)
    - `PUT /api/admin/users/{id}/reset-password` → `resetVolunteerPassword()` → 200 empty
    - `PUT /api/admin/users/{id}/disable` → `disableVolunteer()` → 200 empty
    - `PUT /api/admin/users/{id}/enable` → `enableVolunteer()` → 200 empty

- [x] **T5 — Frontend: UserService, UserListComponent, UserFormComponent** (AC: 1, 2, 3, 4)
  - [x] T5.1 — Create `src/app/services/user.service.ts`: `getVolunteers()`, `createVolunteer(data)`, `resetPassword(id, newPassword)`, `disableVolunteer(id)`, `enableVolunteer(id)` — inject `HttpClient`; return `Observable` or use `firstValueFrom`
  - [x] T5.2 — Create `src/app/features/admin/users/user-list.component.ts` (standalone): signal `users = signal<UserDto[]>([])`, `isLoading = signal(false)`, `error = signal<string|null>(null)`; on init calls `userService.getVolunteers()`; inline actions: disable/enable (toggle), reset password (shows inline password field), create new (navigates to form)
  - [x] T5.3 — Create `src/app/features/admin/users/user-form.component.ts` (standalone): reactive form with `firstName`, `lastName`, `username`, `password` fields; calls `userService.createVolunteer()`; on success navigates back to `/admin/users`
  - [x] T5.4 — Update `src/app/features/admin/admin.routes.ts` to add routes for `users` (list) and `users/create` (form)
  - [x] T5.5 — Add i18n keys to `en.json` and `fr.json` (see i18n section below)

- [x] **T6 — Tests** (coverage ≥ 80%)
  - [x] T6.1 — `UserServiceTest.java`: test `listVolunteers()` returns only VOLUNTEER accounts; test `createVolunteer()` sets role=VOLUNTEER, forcePasswordChange=false, enabled=true; test `createVolunteer()` with duplicate username → throws `BusinessException` with status CONFLICT; test `resetVolunteerPassword()` BCrypt-encodes and sets forcePasswordChange=true; test `disableVolunteer()`/`enableVolunteer()` toggle `enabled`; test `disableVolunteer()` with an ADMIN user → throws `BusinessException` with status FORBIDDEN; test all find-by-id methods throw `BusinessException(NOT_FOUND)` when user not found
  - [x] T6.2 — `UserControllerTest.java` (`@SpringBootTest` + MockMvc): `GET /api/admin/users` → 200 list; `POST /api/admin/users` with valid body → 201; `POST /api/admin/users` with blank firstName → 400; `POST /api/admin/users` with duplicate username → 409; `PUT .../disable` → 200; `PUT .../disable` on admin account → 403; anonymous `GET /api/admin/users` → 401; VOLUNTEER `GET /api/admin/users` → 403
  - [x] T6.3 — `LoginFailureHandlerTest.java`: `DisabledException` → response body contains `account-disabled`; `BadCredentialsException` → response body contains `authentication-failed`
  - [x] T6.4 — `UserListComponent` spec: renders volunteer list; disable button calls service; enable button calls service; showResetPassword sets signal; error on load failure

---

## Dev Notes

### ⚠️ Schema: Three new columns on `users`

The `users` table already exists (001-core-schema.xml, Story 1.1). **Do NOT modify existing changesets** — add a new changeset `005-user-volunteer-fields.xml`:

```xml
<changeSet id="005-user-volunteer-fields" author="pluribourse">
    <addColumn tableName="users">
        <column name="first_name" type="VARCHAR(50)" defaultValue="">
            <constraints nullable="false"/>
        </column>
        <column name="last_name" type="VARCHAR(50)" defaultValue="">
            <constraints nullable="false"/>
        </column>
        <column name="enabled" type="BOOLEAN" defaultValueBoolean="true">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

Register it in `db.changelog-master.xml` after `002-spring-session.xml`. The `defaultValue=""` on name columns is required because the existing `Admin` row has no names — Liquibase cannot add a NOT NULL column with no default to a table with existing rows.

Changesets 003 and 004 (`003-category-table-mapping.xml`, `004-instance-config.xml`) are planned for Stories 2.3 and 1.5 respectively — do not create them here.

---

### PluriBourseUserDetails.isEnabled() — fix the deferred item

Story 1.2 deferred: `"PluriBourseUserDetails manque de isEnabled/isAccountNonLocked — pas de colonne enabled sur User"`.

Add `enabled` to `User` and override `isEnabled()` in `PluriBourseUserDetails`:

```java
// User.java — Boolean (nullable wrapper), NOT primitive boolean
// Reason: primitive boolean defaults to false during Java deserialization;
// old Spring Session JDBC sessions would deserialize with enabled=false, locking out existing users.
@Column(nullable = false)
private Boolean enabled = true;

// PluriBourseUserDetails.java — null-safe override
// null = old deserialized session with no enabled field → treat as enabled
@Override
public boolean isEnabled() {
    return user.getEnabled() == null || user.getEnabled();
}
```

When `isEnabled()` returns `false`, Spring Security's `DaoAuthenticationProvider` throws `DisabledException` **before** checking the password — so even with wrong credentials, a disabled account returns the `account-disabled` error. This is intentional (AC4).

---

### LoginFailureHandler — distinguish DisabledException

Update `LoginFailureHandler.java`:

```java
import org.springframework.security.authentication.DisabledException;

@Override
public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException exception) throws IOException {
    response.setContentType("application/problem+json");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    if (exception instanceof DisabledException) {
        response.getWriter().write(
            "{\"type\":\"https://pluribourse/errors/account-disabled\"," +
            "\"title\":\"Account Disabled\",\"status\":401," +
            "\"detail\":\"This account has been disabled\"}"
        );
    } else {
        response.getWriter().write(
            "{\"type\":\"https://pluribourse/errors/authentication-failed\"," +
            "\"title\":\"Authentication Failed\",\"status\":401," +
            "\"detail\":\"Invalid username or password\"}"
        );
    }
}
```

---

### FR-061: One admin per instance — implementation note

**The `createVolunteer()` method always hardcodes `role = Role.VOLUNTEER`**. `CreateUserDto` has no `role` field. The controller never accepts a role from the request. This inherently satisfies FR-061 — a second ADMIN can never be created through this endpoint.

AC5 ("refuses a second admin account") is satisfied implicitly: the endpoint only ever creates VOLUNTEER accounts, so attempting to "create a second admin" simply results in another volunteer being created (which the caller might not intend, but the system correctly enforces role = VOLUNTEER).

If the business requires an explicit error when trying to create an admin via this endpoint, do NOT add role to CreateUserDto — it would open a security surface. AC5 is satisfied by the endpoint design.

---

### UserService — method implementations

```java
@Transactional(readOnly = true)
public List<UserDto> listVolunteers() {
    return userRepository.findByRole(Role.VOLUNTEER)
            .stream().map(userMapper::toDto).toList();
}

@Transactional
public UserDto createVolunteer(CreateUserDto dto) {
    if (userRepository.existsByUsername(dto.username())) {
        throw new BusinessException(HttpStatus.CONFLICT, "username-already-taken", "Username already taken");
    }
    var user = new User();
    user.setFirstName(dto.firstName());
    user.setLastName(dto.lastName());
    user.setUsername(dto.username());
    user.setPassword(passwordEncoder.encode(dto.password()));
    user.setRole(Role.VOLUNTEER);
    user.setPreferredLanguage(Language.FR);  // default; Story 1.6 will detect from browser
    user.setForcePasswordChange(false);
    user.setEnabled(true);
    return userMapper.toDto(userRepository.save(user));
}

@Transactional
public void resetVolunteerPassword(Long id, String newPassword) {
    var user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
    user.setPassword(passwordEncoder.encode(newPassword));
    user.setForcePasswordChange(true);
    userRepository.save(user);
}

@Transactional
public void disableVolunteer(Long id) {
    var user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
    if (user.getRole() != Role.VOLUNTEER) {
        throw new BusinessException(HttpStatus.FORBIDDEN, "cannot-disable-admin", "Admin account cannot be disabled");
    }
    user.setEnabled(false);
    userRepository.save(user);
}

@Transactional
public void enableVolunteer(Long id) {
    var user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
    user.setEnabled(true);
    userRepository.save(user);
}
```

Add to `UserRepository`:
```java
List<User> findByRole(Role role);
boolean existsByUsername(String username);
```

---

### UserController

```java
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserDto>> listVolunteers() {
        return ResponseEntity.ok(userService.listVolunteers());
    }

    @PostMapping
    public ResponseEntity<UserDto> createVolunteer(
            @Valid @RequestBody CreateUserDto dto,
            HttpServletRequest request) {
        UserDto created = userService.createVolunteer(dto);
        URI location = URI.create(request.getRequestURI() + "/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordDto dto) {
        userService.resetVolunteerPassword(id, dto.newPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        userService.disableVolunteer(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        userService.enableVolunteer(id);
        return ResponseEntity.ok().build();
    }
}
```

`/api/admin/**` is protected by `.hasRole("ADMIN")` in `SecurityConfig` — no additional `@PreAuthorize` needed.

---

### MapStruct UserMapper

```java
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDto toDto(User user);
}
```

MapStruct maps fields by name. `User.firstName` → `UserDto.firstName()`, etc. The `role` field: `User.role` is `Role` enum, `UserDto.role` is `String`. Add explicit mapping:

```java
@Mapping(target = "role", expression = "java(user.getRole().name())")
UserDto toDto(User user);
```

---

### Angular: follow the existing patterns

**Pattern from Story 1.2 — always reuse:**
- Standalone components: `standalone: true`
- Signals for state: `signal()`, `computed()`
- ngx-translate: import `TranslatePipe`, use `{{ 'key' | translate }}` in template — **never hardcode text**
- HTTP calls: inject `HttpClient` via `inject(HttpClient)` in service; use `firstValueFrom()` in the component
- Reactive forms: `inject(FormBuilder)`
- Routing: `inject(Router)`, `inject(ActivatedRoute)`

**Frontend file locations** — follow `features/` pattern established in Story 1.2:
- `src/app/features/admin/users/user-list.component.ts`
- `src/app/features/admin/users/user-form.component.ts`
- `src/app/services/user.service.ts`

**UserService** (frontend):
```typescript
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);

  getVolunteers(): Observable<UserDto[]> {
    return this.http.get<UserDto[]>('/api/admin/users');
  }

  createVolunteer(data: CreateUserRequest): Observable<UserDto> {
    return this.http.post<UserDto>('/api/admin/users', data);
  }

  resetPassword(id: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/reset-password`, { newPassword });
  }

  disableVolunteer(id: number): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/disable`, {});
  }

  enableVolunteer(id: number): Observable<void> {
    return this.http.put<void>(`/api/admin/users/${id}/enable`, {});
  }
}
```

Define TypeScript interfaces in `src/app/models/user.model.ts`:
```typescript
export interface UserDto {
  id: number;
  firstName: string;
  lastName: string;
  username: string;
  role: string;
  enabled: boolean;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  username: string;
  password: string;
}
```

**UserListComponent** (minimal, functional):
- Loads volunteer list on init via `UserService.getVolunteers()`
- Shows name (firstName + lastName), username, status (enabled/disabled)
- Buttons: "Disable" / "Enable" (toggle based on `user.enabled`), "Reset Password"
- "Reset Password" reveals an inline input + confirm button (signal `resetPasswordFor = signal<number|null>(null)`)
- Link or button to create new volunteer
- All text via i18n keys

**Update `admin.routes.ts`**:
```typescript
import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  { path: 'users', loadComponent: () => import('./users/user-list.component').then(m => m.UserListComponent) },
];
```

---

### i18n Keys

Add to `en.json` and `fr.json`:

```json
// en.json additions
{
  "admin": {
    "users": {
      "title": "Volunteer Accounts",
      "columns": {
        "name": "Name",
        "username": "Username",
        "status": "Status",
        "actions": "Actions"
      },
      "status": {
        "active": "Active",
        "inactive": "Inactive"
      },
      "actions": {
        "create": "Add volunteer",
        "disable": "Disable",
        "enable": "Enable",
        "resetPassword": "Reset password",
        "confirmReset": "Confirm"
      },
      "create": {
        "title": "Create volunteer",
        "firstName": "First name",
        "lastName": "Last name",
        "username": "Username",
        "password": "Temporary password",
        "submit": "Create",
        "cancel": "Cancel"
      },
      "error": {
        "load": "Failed to load volunteers.",
        "create": "Failed to create volunteer.",
        "disable": "Failed to disable account.",
        "enable": "Failed to enable account.",
        "resetPassword": "Failed to reset password."
      }
    }
  },
  "auth": {
    "login": {
      "error": {
        "account-disabled": "This account has been disabled."
      }
    }
  }
}
```

**FR translation notes — vouvoiement obligatoire** (from EXPERIENCE.md):
- "Ajouter un bénévole", "Désactiver", "Activer", "Réinitialiser le mot de passe"
- "Ce compte a été désactivé."
- "Impossible de charger les bénévoles."

The frontend login error display currently only shows `auth.login.error.invalid-credentials` for any error. Update `LoginComponent` to distinguish the error type: on 401 with `error.type.includes('account-disabled')` → show `auth.login.error.account-disabled`.

---

### Login Component: distinguish disabled account error

The existing `LoginComponent` (Story 1.2) always shows `auth.login.error.invalid-credentials` on any catch. Apply **all five changes** below to `login.component.ts`:

**1 — Signal rename** (currently line 42):
```typescript
// Before:
readonly error = signal(false);
// After:
readonly errorKey = signal<string|null>(null);
```

**2 — Clear on submit** (currently line 46, first line inside `onSubmit()`):
```typescript
// Before:
this.error.set(false);
// After:
this.errorKey.set(null);
```

**3 — Unexpected role branch** (currently line 59, inside `else` block):
```typescript
// Before:
this.error.set(true);
// After:
this.errorKey.set('auth.login.error.invalid-credentials');
```

**4 — Catch block** (currently line 62–63):
```typescript
// Before:
} catch {
  this.error.set(true);
// After:
} catch (err: any) {
  if (err?.error?.type?.includes('account-disabled')) {
    this.errorKey.set('auth.login.error.account-disabled');
  } else {
    this.errorKey.set('auth.login.error.invalid-credentials');
  }
```

**5 — Template** (currently lines 22–24):
```html
<!-- Before: -->
@if (error()) {
  <p role="alert">{{ 'auth.login.error.invalid-credentials' | translate }}</p>
}
<!-- After: -->
@if (errorKey()) {
  <p role="alert">{{ errorKey()! | translate }}</p>
}
```

---

### Testing Patterns — reuse from Story 1.2

**Backend integration tests** — H2 in-memory, same setup as Stories 1.1/1.2:
- `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` or `@AutoConfigureMockMvc` replacement pattern from Story 1.2 debug log #5: use `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()`
- Use `@WithMockUser(roles = "ADMIN")` for admin-only endpoints
- Use `@WithMockUser(roles = "VOLUNTEER")` to test 403 on admin endpoints
- Unauthenticated request → 401

**Note on `@AutoConfigureMockMvc` removal (Spring Boot 4)** — from Story 1.2 debug log #5:
```java
// In @BeforeEach:
mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
```

**Backend unit tests** — JUnit 5 + Mockito, same as `UserServiceTest.java`:
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @InjectMocks private UserService userService;
    ...
}
```

---

### No PII in Logs

- Never log `username`, `firstName`, `lastName` in any log message
- Log only user `id` for traceability
- Exception: Spring Security may log `UsernameNotFoundException` internally at debug — use generic message, never include the username value

---

### Validation returns HTTP 400

`@Valid @RequestBody` validation failures (blank firstName, short password) go through `GlobalExceptionHandler.handleMethodArgumentNotValid()` which returns **HTTP 400** (not 422). The `ConstraintViolationException` handler returns 422 (used for `@Validated` on method parameters). Tests should expect 400 for `@Valid @RequestBody` failures.

---

## Project Structure Notes

**Backend — new files:**
- `src/main/resources/db/changelog/005-user-volunteer-fields.xml`
- `org.pluribourse.user.dto.UserDto`
- `org.pluribourse.user.dto.CreateUserDto`
- `org.pluribourse.user.dto.ResetPasswordDto`
- `org.pluribourse.user.mapper.UserMapper`
- `org.pluribourse.user.controller.UserController`

**Backend — modified files:**
- `src/main/resources/db/changelog/db.changelog-master.xml` (add include for 005)
- `org.pluribourse.user.entity.User` (add `firstName`, `lastName`, `enabled`)
- `org.pluribourse.user.repository.UserRepository` (add `findByRole`, `existsByUsername`)
- `org.pluribourse.user.service.UserService` (add volunteer management methods)
- `org.pluribourse.shared.security.PluriBourseUserDetails` (override `isEnabled()`)
- `org.pluribourse.shared.security.LoginFailureHandler` (distinguish DisabledException)

**Test files — new:**
- `src/test/java/org/pluribourse/user/UserControllerTest.java`
- `src/test/java/org/pluribourse/shared/LoginFailureHandlerTest.java`

**Test files — modified:**
- `src/test/java/org/pluribourse/user/UserServiceTest.java` (extend with new method tests)

**Frontend — new files:**
- `src/app/models/user.model.ts`
- `src/app/services/user.service.ts`
- `src/app/features/admin/users/user-list.component.ts`
- `src/app/features/admin/users/user-form.component.ts`

**Frontend — modified files:**
- `src/app/features/admin/admin.routes.ts` (add users route)
- `src/app/features/auth/login/login.component.ts` (distinguish disabled account error)
- `public/i18n/en.json` (add admin.users.* and auth.login.error.account-disabled keys)
- `public/i18n/fr.json` (add same keys in French, vouvoiement)

---

## References

- [Source: epics.md#Story 1.3] — user story and acceptance criteria (AC1–AC5)
- [Source: epics.md#FR-060] — admin creates/modifies/disables volunteer accounts
- [Source: epics.md#FR-061] — one admin per instance
- [Source: architecture.md#Authentification & Sécurité] — BCrypt, role model, Spring Security
- [Source: architecture.md#Backend — Structure de Répertoires Complète] — `user/` package layout: controller, service, repository, entity, dto, mapper
- [Source: architecture.md#API & Communication] — RFC 7807, `/api/` prefix, no versioning
- [Source: architecture.md#Patrons de Nommage] — kebab-case API routes, camelCase JSON, `UserDto` suffix
- [Source: architecture.md#Directives d'Application] — no PII in logs, use user ID in logs
- [Source: 1-2-authentification-spring-security-controle-dacces-base-sur-les-roles.md#Dev Notes] — Spring Boot 4: inline DaoAuthenticationProvider, `@AutoConfigureMockMvc` removed, `TranslatePipe` import, Surefire `*IT.java` config
- [Source: 1-2-authentification-spring-security-controle-dacces-base-sur-les-roles.md#Review Findings] — `PluriBourseUserDetails isEnabled/isAccountNonLocked` deferred to Story 1.3; `loadUserByUsername` without `@Transactional(readOnly=true)` deferred
- [Source: deferred-work.md] — `PluriBourseUserDetails manque de isEnabled/isAccountNonLocked` — resolved in this story

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- **MediaType ambiguity** (Spring Boot 4 + JUnit 5): `org.junit.jupiter.api.MediaType` clashes with `org.springframework.http.MediaType` when using wildcard imports. Fixed by using explicit `import org.springframework.http.MediaType;` in `UserControllerTest.java`.
- **LiquibaseMigrationIT update**: `usersTableHasAllRequiredColumns` expected exact column list; updated to include `FIRST_NAME`, `LAST_NAME`, `ENABLED`.
- **LoginComponent signal was already typed**: story notes described 5 changes from `signal(false)` — actual code used `signal<'invalid-credentials' | 'unauthorized-role' | null>(null)`. Only added `'account-disabled'` to the union type and updated the catch block.
- **ngx-translate v18 / Vitest in spec**: no `TranslateModule.forRoot()` — used `provideTranslateService({ lang: 'en' })`. No `jasmine` — used `vi.fn()` from Vitest.
- **Package naming**: project uses plural (`dtos`, `controllers`, `mappers`) — followed existing convention.

### Completion Notes List

- Liquibase changeset 005 adds `first_name`, `last_name`, `enabled` columns to `users` table with safe defaults for existing rows.
- `User.enabled` is `Boolean` (nullable wrapper) to prevent `false` default during Java deserialization of old Spring Session JDBC sessions.
- `PluriBourseUserDetails.isEnabled()` overridden with null-safe logic: `null` → treated as enabled (legacy session compatibility).
- `LoginFailureHandler` now distinguishes `DisabledException` from other auth failures (AC4).
- `UserController` at `/api/admin/users` (protected by `hasRole("ADMIN")` in SecurityConfig) — no `@PreAuthorize` needed.
- `CreateUserDto` has no `role` field — role always hardcoded to VOLUNTEER (FR-061).
- Backend: 49/49 tests pass.
- Frontend: 28/29 pass — 1 pre-existing failure in `app.spec.ts` (checks `<h1>Hello…` but template only has `<router-outlet />` since Story 1.1).

### File List

**Backend — new files:**
- `pluribourse-backend/src/main/resources/db/changelog/005-user-volunteer-fields.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/user/dtos/UserDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/dtos/CreateUserDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/dtos/ResetPasswordDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/mappers/UserMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/controllers/UserController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/user/UserControllerTest.java`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/LoginFailureHandlerTest.java`

**Backend — modified files:**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/user/entities/User.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/entities/PluriBourseUserDetails.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/repositories/UserRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/LoginFailureHandler.java`
- `pluribourse-backend/src/test/java/org/pluribourse/user/UserServiceTest.java`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/LiquibaseMigrationIT.java`

**Frontend — new files:**
- `pluribourse-frontend/src/app/models/user.model.ts`
- `pluribourse-frontend/src/app/services/user.service.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.spec.ts`

**Frontend — modified files:**
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/features/auth/login/login.component.ts`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`

---

### Review Findings

- [x] [Review][Decision→Patch] `resetVolunteerPassword` accepte tout user ID, admin inclus — décision : ajouter le guard VOLUNTEER (`BusinessException(FORBIDDEN)` si rôle != VOLUNTEER), cohérent avec `disableVolunteer`. Appliqué dans `UserService.java:resetVolunteerPassword()`.

- [x] [Review][Patch] AC1 — Colonne "Role" absente de `user-list.component.html` — colonne ajoutée dans le template et clés `admin.users.columns.role` ajoutées dans `en.json` / `fr.json`. [`user-list.component.html`]
- [x] [Review][Patch] Race TOCTOU dans `createVolunteer` — `DataIntegrityViolationException` catchée autour du `save()` et relancée en `BusinessException(CONFLICT)`. [`UserService.java:createVolunteer()`]
- [x] [Review][Patch] Tests d'intégration manquants pour `PUT /{id}/reset-password` et `PUT /{id}/enable` — 4 tests ajoutés (reset 200, reset-admin 403, enable 200, enable-admin 403). [`UserControllerTest.java`]
- [x] [Review][Patch] `enableVolunteer` n'a pas de role guard — guard ajouté, symétrique avec `disableVolunteer`. [`UserService.java:enableVolunteer()`]
- [x] [Review][Patch] MapStruct unboxing `Boolean → boolean` sans null-check — `@Mapping` explicite avec expression null-safe ajouté. [`UserMapper.java`]
- [x] [Review][Patch] `Location` header URI relative — remplacé par `ServletUriComponentsBuilder.fromCurrentRequestUri()`. Paramètre `HttpServletRequest` supprimé. [`UserController.java:createVolunteer()`]
- [x] [Review][Patch] Substring match fragile dans `login.component.ts` — remplacé par égalité exacte `=== 'https://pluribourse/errors/account-disabled'`. [`login.component.ts`]

- [x] [Review][Defer] Route Angular `'users/create'` plate (sibling dans adminRoutes) — non-idiomatique, devrait être un enfant de `users`. Pas de bug actuel, restructurer quand le nesting sera nécessaire. [`admin.routes.ts`] — deferred, design choice

### Review Findings (2ème passe — 2026-06-22)

- [x] [Review][Patch] `UserServiceTest` manque `resetVolunteerPassword_on_admin_throws_forbidden` — test ajouté dans `UserServiceTest.java`. [`UserServiceTest.java`]
- [x] [Review][Patch] `UserServiceTest` manque `enableVolunteer_on_admin_throws_forbidden` — test ajouté dans `UserServiceTest.java`. [`UserServiceTest.java`]
- [x] [Review][Patch] `user-list.component.spec.ts` : `whenStable()` avant `detectChanges()` — ordre corrigé : `detectChanges()` puis `await whenStable()`. [`user-list.component.spec.ts`]
- [x] [Review][Patch] `user-form.component.html` : `<label>` sans `for`/`id` — attributs `for`/`id` ajoutés sur les 4 paires label/input. [`user-form.component.html`]
- [x] [Review][Patch] `submitResetPassword` sans guard d'envoi en cours — signal `submitting` ajouté, bouton désactivé pendant la soumission. [`user-list.component.ts` / `user-list.component.html`]
- [x] [Review][Patch] `UserServiceTest.createVolunteer` ne vérifie pas `forcePasswordChange=false` — `ArgumentCaptor<User>` ajouté, assertion sur `forcePasswordChange=false`. [`UserServiceTest.java`]
- [x] [Review][Patch] `UserControllerTest.createVolunteer_with_valid_body_returns_201` ne vérifie pas le header `Location` — assertion `header().string("Location", containsString("/api/admin/users/"))` ajoutée. [`UserControllerTest.java`]

- [x] [Review][Defer] Session active d'un bénévole désactivé reste valide jusqu'à expiration — `disableVolunteer` ne révoque pas la session Spring Session. Pré-existant (identique au defer Story 1.2 "sessions parallèles non invalidées"). [`UserService.java:disableVolunteer()`] — deferred, pre-existing
- [x] [Review][Defer] URL d'erreur `https://pluribourse/errors/account-disabled` dupliquée en magic string Java et TypeScript — divergence silencieuse si l'une change. Cross-langage, risque faible à court terme. [`LoginFailureHandler.java` / `login.component.ts`] — deferred, pre-existing
