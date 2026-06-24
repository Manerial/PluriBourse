---
baseline_commit: e7bb5907d95e8e4018cf07bc2742b9c38e2fc4dd
---

# Story 1.6: User Language Preference & i18n Infrastructure

Status: done

## Story

As a user,
I want the application to display in my preferred language (English or French),
so that I can work comfortably in my native language during the event.

## Acceptance Criteria

1. **Given** a new user logs in for the first time with browser language `fr`, **When** the login completes, **Then** the interface displays in French **And** `preferredLanguage: FR` is saved on the user account.

2. **Given** a new user's browser is configured in English or an unsupported language, **When** they log in for the first time, **Then** the interface displays in English **And** `preferredLanguage: EN` is saved.

3. **Given** a logged-in user accesses `/account` and selects the other language, **When** they save the preference, **Then** the interface switches immediately to the selected language (no page reload) **And** the preference survives logout and re-login.

4. **Given** any page is displayed, **When** visible text is inspected, **Then** all text comes from `en.json` or `fr.json` translation keys — no hardcoded strings (FR-004) **And** i18n keys follow the `feature.section.key` format (max 3 levels).

5. **Given** a PDF document is generated, **When** the active edition's document language is `FR`, **Then** all document text uses entries from `messages_fr.properties` (Spring MessageSource infrastructure is wired and resolves keys correctly for both locales).

## Tasks / Subtasks

- [x] **T1 — Liquibase: `006-user-language-initialized.xml`** (AC: 1, 2)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/006-user-language-initialized.xml` with changeset `006-user-language-initialized`: add column `language_initialized BOOLEAN NOT NULL DEFAULT FALSE` to table `users`
  - [x] T1.2 — Update `db.changelog-master.xml`: add `<include file="db/changelog/006-user-language-initialized.xml"/>` after `005-user-volunteer-fields.xml`

- [x] **T2 — Backend: Update `User` entity** (AC: 1, 2, 3)
  - [x] T2.1 — Add field `@Column(name = "language_initialized", nullable = false) private boolean languageInitialized;` to `User.java`

- [x] **T3 — Backend: Update `PluriBourseUserDetails`** (AC: 1, 2, 3)
  - [x] T3.1 — Add `getPreferredLanguage()` returning `user.getPreferredLanguage()`
  - [x] T3.2 — Add `isLanguageInitialized()` returning `user.isLanguageInitialized()`

- [x] **T4 — Backend: Update `UserSessionDto`** (AC: 1, 2, 3)
  - [x] T4.1 — Add `String preferredLanguage` as the 4th field: `record UserSessionDto(String username, String role, boolean forcePasswordChange, String preferredLanguage)`
  - [x] T4.2 — Update `AuthController.me()` to pass `userDetails.getPreferredLanguage().name()` as 4th arg
  - [x] T4.3 — Update `LoginSuccessHandler.onAuthenticationSuccess()` to pass the effective language as 4th arg (see T5)

- [x] **T5 — Backend: Update `LoginSuccessHandler` — first-login language detection** (AC: 1, 2)
  - [x] T5.1 — Inject `UserService` into `LoginSuccessHandler` constructor (add as 2nd constructor parameter; update `@Component` class signature; no Lombok `@RequiredArgsConstructor` — use explicit constructor)
  - [x] T5.2 — In `onAuthenticationSuccess()`: if `!userDetails.isLanguageInitialized()`, call `detectLanguage(request)` → assign to local `Language effectiveLanguage`, then call `userService.initializeLanguage(userDetails.getUserId(), effectiveLanguage)` (discard return value); if already initialized, `effectiveLanguage = userDetails.getPreferredLanguage()` — see Dev Notes snippet for exact pattern
  - [x] T5.3 — Build `UserSessionDto` with `effectiveLanguage.name()` as 4th field

- [x] **T6 — Backend: Update `UserService` — new language methods** (AC: 1, 2, 3)
  - [x] T6.1 — Add `@Transactional initializeLanguage(Long userId, Language lang)`: calls `getUser(userId)`, sets `user.setPreferredLanguage(lang)`, `user.setLanguageInitialized(true)`, `userRepository.save(user)`, returns `new PluriBourseUserDetails(user)`
  - [x] T6.2 — Add `@Transactional updateLanguagePreference(Long userId, Language lang)`: calls `getUser(userId)`, sets `user.setPreferredLanguage(lang)` (and `user.setLanguageInitialized(true)` if not already), `userRepository.save(user)`, returns `new PluriBourseUserDetails(user)`

- [x] **T7 — Backend: `UpdateLanguageDto`** (AC: 3)
  - [x] T7.1 — Create `org.pluribourse.user.dtos.UpdateLanguageDto` record: `@NotNull @Pattern(regexp = "EN|FR") String language`

- [x] **T8 — Backend: `AccountController`** (AC: 3)
  - [x] T8.1 — Create `org.pluribourse.user.controllers.AccountController` with `@RestController @RequestMapping("/api/account") @RequiredArgsConstructor`
  - [x] T8.2 — `PUT /api/account/language-preference` with `@Valid @RequestBody UpdateLanguageDto dto`, `Authentication authentication`: extract `userId` from `PluriBourseUserDetails`, call `userService.updateLanguagePreference(userId, Language.valueOf(dto.language()))`, update SecurityContext (same pattern as `AuthController.changePassword`), return `ResponseEntity<Void>` 200 OK

- [x] **T9 — Backend: Update `UserService.createVolunteer()` and CLIs** (AC: 1, 2)
  - [x] T9.1 — In `UserService.createVolunteer()`: change `user.setPreferredLanguage(Language.FR)` to `user.setPreferredLanguage(Language.EN)` (neutral default) AND add `user.setLanguageInitialized(false)` — language will be initialized on first login
  - [x] T9.2 — In `AdminCreateRunner.performCreate()`: add `admin.setLanguageInitialized(false)` — the seeded Admin user gets `language_initialized = false` so their language is set on first login; the `001-admin-seed` changeset seeds `preferred_language = 'FR'` which serves as a DB fallback before first login
  - [x] T9.3 — `AdminPasswordResetRunner` only updates `password` and `forcePasswordChange` on existing users — no changes needed; it does NOT touch `languageInitialized` or `preferredLanguage`

- [x] **T10 — Backend: Spring MessageSource infrastructure** (AC: 5)
  - [x] T10.1 — Verify `application.properties` has `spring.messages.basename=messages` (Spring Boot auto-configures this; add it explicitly if missing to avoid ambiguity)
  - [x] T10.2 — Add at least one entry to `messages_en.properties` and `messages_fr.properties` (e.g., `app.name=PluriBourse`) to prove the infrastructure works and enable testing
  - [x] T10.3 — Add `MessageSource` injection + test in `LanguagePreferenceIT` to verify both locales resolve correctly (see T11)

- [x] **T11 — Backend: `LanguagePreferenceIT`** (coverage ≥ 80%)
  - [x] T11.1 — Create `org.pluribourse.user.LanguagePreferenceIT extends IntegrationTest` with `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@TestInstance(Lifecycle.PER_CLASS)`
  - [x] T11.2 — `@BeforeAll setUpSessions()`: POST `/api/auth/login` as `test_admin` → `adminSession`; POST `/api/auth/login` as `volunteer1` → `volunteerSession` (same URL as Orders 9/10 in the storyboard)
  - [x] T11.3 — Ordered test scenario (see Dev Notes for full storyboard)

- [x] **T12 — Backend: Update `test-data.sql`** (test isolation)
  - [x] T12.1 — Add `language_initialized = true` to all three INSERT statements in `test-data.sql` — prevents existing IT classes from triggering first-login language detection during login setup

- [x] **T13 — Frontend: Update `AuthService`** (AC: 1, 2, 3)
  - [x] T13.1 — Add `preferredLanguage: 'EN' | 'FR'` to `CurrentUser` interface in `auth.service.ts`
  - [x] T13.2 — Inject `TranslateService` via `private readonly translateService = inject(TranslateService)` in `AuthService`; add import `import { TranslateService } from '@ngx-translate/core'`
  - [x] T13.3 — In `login()`: after `this.currentUser.set(user)`, add `await firstValueFrom(this.translateService.use(user.preferredLanguage.toLowerCase()))`
  - [x] T13.4 — In `restoreSession()`: after `this.currentUser.set(user)`, add `await firstValueFrom(this.translateService.use(user.preferredLanguage.toLowerCase()))`
  - [x] T13.5 — In `logout()`: in the `finally` block after `this.currentUser.set(null)`, add `this.translateService.use('en').subscribe()` (fire-and-forget; no await needed since user won't see translated content until next login)
  - [x] T13.6 — Update `auth.service.spec.ts`: (a) add `preferredLanguage: 'FR' as const` to `adminUser` constant — TypeScript will error without it since `CurrentUser` now requires this field; (b) add `TranslateService` mock to TestBed providers: `{ provide: TranslateService, useValue: { use: vi.fn().mockReturnValue(of({})) } }` — without this mock ALL existing tests throw a DI error; (c) import `of` from `rxjs` and `TranslateService` from `@ngx-translate/core`; (d) add test: `'applies user preferredLanguage on login'` — verify `translateServiceMock.use` was called with `'fr'` after a login that returns `preferredLanguage: 'FR'`; (e) add test: `'applies user preferredLanguage on session restore'`

- [x] **T14 — Frontend: Update `app.config.ts`** (AC: 1, 2)
  - [x] T14.1 — Change `provideTranslateService({ lang: 'fr' })` to `provideTranslateService({ lang: 'en' })` — `lang` is the correct option name in ngx-translate v18 (`defaultLanguage` does not exist); pre-auth default becomes EN, user's language is applied after session restore in `AuthService.restoreSession()`

- [x] **T15 — Frontend: `AccountComponent`** (AC: 3, 4)
  - [x] T15.1 — Create `pluribourse-frontend/src/app/features/account/account.component.ts` (standalone; inject `AuthService`, `inject(HttpClient)` or `AccountService`, `FormBuilder`, `TranslateService`; signals for `isSaving`, `saveSuccess`, `saveError`; form with a `language` field; `ngOnInit` patches form from `authService.currentUser()?.preferredLanguage`)
  - [x] T15.2 — Create `pluribourse-frontend/src/app/features/account/account.component.html` (separate HTML file — no inline template; `<select>` with EN/FR options; all strings via `| translate`)
  - [x] T15.3 — Create `pluribourse-frontend/src/app/services/account.service.ts` with `updateLanguage(language: 'EN' | 'FR'): Observable<void>` calling `PUT /api/account/language-preference`

- [x] **T16 — Frontend: Routing** (AC: 3)
  - [x] T16.1 — Add `/account` route to `app.routes.ts` with `canActivate: [authGuard]` (accessible to both ADMIN and VOLUNTEER — no `adminGuard`)

- [x] **T17 — Frontend: i18n keys** (AC: 4)
  - [x] T17.1 — Add `account` section to `en.json`
  - [x] T17.2 — Add `account` section to `fr.json` (vouvoiement systematique)

## Dev Notes

### Critical: language_initialized column — why it exists

Story 1.6 AC1–2 require detecting browser language **only on first login**. The `users.preferred_language` column already has `DEFAULT 'FR'` in the DB seed (001-admin-seed), so we cannot use NULL as the "not-yet-initialized" sentinel. The `language_initialized BOOLEAN DEFAULT FALSE` column is the clean solution.

- `false` = first login → detect from `Accept-Language`, save, flip to `true`
- `true` = user or system has explicitly set the language → never override

### Test data: must set language_initialized = true

`test-data.sql` currently inserts `test_admin`, `volunteer1`, `volunteer2` without `language_initialized`. After migration 006, these rows will have `language_initialized = false` (DB DEFAULT). This means every `@BeforeAll` login in ANY IT class would trigger the first-login language detection and a DB write, potentially causing flakiness. Fix: update all three INSERTs in `test-data.sql` to include `language_initialized = true`.

```sql
INSERT INTO users (username, password, role, preferred_language, force_password_change, first_name, last_name, enabled, language_initialized)
VALUES ('test_admin', '...', 'ADMIN', 'FR', false, 'Test', 'Admin', true, true);
-- same for volunteer1 and volunteer2
```

### LoginSuccessHandler — reading Accept-Language

`HttpServletRequest.getLocale()` parses the `Accept-Language` header and returns the preferred `Locale`. Use this instead of manually parsing the raw header:

```java
private Language detectLanguage(HttpServletRequest request) {
    String lang = request.getLocale().getLanguage(); // "fr", "en", "de", etc.
    return "fr".equals(lang) ? Language.FR : Language.EN;
}
```

### LoginSuccessHandler — constructor injection pattern

`LoginSuccessHandler` currently does NOT use Lombok. Keep the explicit constructor; add `UserService` as a second parameter:

```java
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public LoginSuccessHandler(ObjectMapper objectMapper, UserService userService) {
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof PluriBourseUserDetails userDetails)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        Language effectiveLanguage = userDetails.getPreferredLanguage();
        if (!userDetails.isLanguageInitialized()) {
            effectiveLanguage = detectLanguage(request);
            userService.initializeLanguage(userDetails.getUserId(), effectiveLanguage);
        }
        var dto = new UserSessionDto(
                userDetails.getUsername(),
                userDetails.getRole(),
                userDetails.isForcePasswordChange(),
                effectiveLanguage.name()
        );
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), dto);
    }
}
```

### AccountController — SecurityContext update pattern

After `updateLanguagePreference`, the Spring Session JDBC-serialized `PluriBourseUserDetails` in the session still has the old language. Update SecurityContext so `GET /api/auth/me` returns the new language immediately (same pattern as `AuthController.changePassword`):

```java
@PutMapping("/language-preference")
@PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
public ResponseEntity<Void> updateLanguagePreference(
        @Valid @RequestBody UpdateLanguageDto dto,
        Authentication authentication) {
    var userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
    var newDetails = userService.updateLanguagePreference(userDetails.getUserId(), Language.valueOf(dto.language()));
    var newAuth = UsernamePasswordAuthenticationToken.authenticated(newDetails, null, newDetails.getAuthorities());
    SecurityContextHolder.getContext().setAuthentication(newAuth);
    return ResponseEntity.ok().build();
}
```

**Note:** `/api/account/**` is NOT under `/api/admin/**`, so it's covered by `anyRequest().access(...)` in `SecurityConfig` — accessible to both ADMIN and VOLUNTEER automatically. The `@PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")` adds defense-in-depth but is not strictly required. No changes to `SecurityConfig.java`.

### AuthController.me() — update UserSessionDto construction

```java
@GetMapping("/me")
@PreAuthorize("hasAnyRole('ADMIN', 'VOLUNTEER')")
public ResponseEntity<UserSessionDto> me(Authentication authentication) {
    var userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
    var dto = new UserSessionDto(
            userDetails.getUsername(),
            userDetails.getRole(),
            userDetails.isForcePasswordChange(),
            userDetails.getPreferredLanguage().name()  // NEW 4th field
    );
    return ResponseEntity.ok(dto);
}
```

### Frontend: AuthService — inject TranslateService

`TranslateService.use(lang)` returns an `Observable<any>`. Use `firstValueFrom()` in async login/restore to ensure translations are applied before the caller continues. In the logout `finally` block, fire-and-forget is acceptable since there's nothing language-sensitive to render immediately.

```typescript
import { TranslateService } from '@ngx-translate/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);

  async login(username: string, password: string): Promise<CurrentUser> {
    const body = new URLSearchParams({ username, password });
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/auth/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      })
    );
    this.currentUser.set(user);
    await firstValueFrom(this.translateService.use(user.preferredLanguage.toLowerCase()));
    return user;
  }

  async restoreSession(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<CurrentUser>('/api/auth/me'));
      this.currentUser.set(user);
      await firstValueFrom(this.translateService.use(user.preferredLanguage.toLowerCase()));
    } catch (error: any) {
      if (error?.status === 403 && error?.error?.type?.includes('password-change-required')) {
        return;
      }
      this.currentUser.set(null);
    }
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } finally {
      this.currentUser.set(null);
      this.translateService.use('en').subscribe();  // fire-and-forget
      await this.router.navigate(['/login']);
    }
  }
}
```

### Frontend: app.config.ts — change default language

Change `provideTranslateService({ lang: 'fr' })` to `provideTranslateService({ lang: 'en' })`.

**CRITICAL:** In ngx-translate v18 (`@ngx-translate/core: ^18.0.0`), the functional providers API option is `lang` — NOT `defaultLanguage`. This is confirmed by `admin-settings.component.spec.ts:33` which uses `provideTranslateService({ lang: 'en' })` and already works. Do NOT use `defaultLanguage` — it is not a valid key in v18 and will be silently ignored (breaking the default language behavior).

EN is used until `restoreSession()` completes and applies the user's language.

### Frontend: auth.service.spec.ts — critical update required

The existing spec has `adminUser: CurrentUser` without `preferredLanguage`. After adding this field to `CurrentUser`, TypeScript will fail to compile. Also, `AuthService` now injects `TranslateService` which is not provided in the current spec's TestBed — ALL tests will throw a DI error without a mock.

**Required spec changes:**

```typescript
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

// 1. Update adminUser to include preferredLanguage
const adminUser: CurrentUser = {
  username: 'Admin',
  role: 'ADMIN',
  forcePasswordChange: false,
  preferredLanguage: 'FR'
};

// 2. Add translateServiceMock alongside routerMock
let translateServiceMock: { use: ReturnType<typeof vi.fn> };

beforeEach(() => {
  routerMock = { navigate: vi.fn().mockResolvedValue(true) };
  translateServiceMock = { use: vi.fn().mockReturnValue(of({})) };

  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: Router, useValue: routerMock },
      { provide: TranslateService, useValue: translateServiceMock }  // REQUIRED
    ]
  });
  ...
});

// 3. Add new tests
describe('language switching', () => {
  it('applies preferredLanguage from server on login', async () => {
    const promise = service.login('Admin', 'Admin');
    httpMock.expectOne('/api/auth/login').flush(adminUser);  // adminUser has preferredLanguage: 'FR'
    await promise;
    expect(translateServiceMock.use).toHaveBeenCalledWith('fr');
  });

  it('applies preferredLanguage from server on session restore', async () => {
    const promise = service.restoreSession();
    httpMock.expectOne('/api/auth/me').flush(adminUser);
    await promise;
    expect(translateServiceMock.use).toHaveBeenCalledWith('fr');
  });

  it('resets to en on logout', async () => {
    service.currentUser.set(adminUser);
    const promise = service.logout();
    httpMock.expectOne('/api/auth/logout').flush(null);
    await promise;
    expect(translateServiceMock.use).toHaveBeenCalledWith('en');
  });
});
```

**Note on existing tests:** Existing `restoreSession` tests that use `service.currentUser.set(adminUser)` to pre-set a user before testing error cases will now need `adminUser` to include `preferredLanguage`. Since we're updating `adminUser` globally in the spec, this is handled by change #1 above.

**CRITICAL — pre-existing URL bugs in the spec:** The existing spec uses `httpMock.expectOne('/login')` and `httpMock.expectOne('/logout')` but `AuthService` calls `/api/auth/login` and `/api/auth/logout`. These tests are already broken (or skip matching). Fix them alongside the T13.6 changes:
- `httpMock.expectOne('/login')` → `httpMock.expectOne('/api/auth/login')`
- `httpMock.expectOne('/logout')` → `httpMock.expectOne('/api/auth/logout')`

### Frontend: AccountComponent spec pattern

Follow the `admin-settings.component.spec.ts` pattern exactly (confirmed to work with Vitest + ngx-translate v18):

```typescript
// account.component.spec.ts
import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AccountComponent } from './account.component';
import { AccountService } from '../../services/account.service';
import { AuthService } from '../../services/auth.service';

describe('AccountComponent', () => {
  let fixture: ComponentFixture<AccountComponent>;
  let component: AccountComponent;

  const accountServiceMock = {
    updateLanguage: vi.fn().mockReturnValue(of(undefined))
  };
  const authServiceMock = {
    currentUser: vi.fn().mockReturnValue({ username: 'test', role: 'VOLUNTEER', forcePasswordChange: false, preferredLanguage: 'FR' }),
    currentUser: { set: vi.fn() }
    // Note: use a signal mock — or provide a real AuthService with a mock currentUser signal
  };

  // Simpler: test the component's DOM/logic, mock both services
  // Refer to AdminSettingsComponent spec structure for the full pattern
});
```

**Recommended test cases for `account.component.spec.ts`:**
1. `'patches form with current preferredLanguage on init'` — verify form's `language` control = `'FR'`
2. `'calls updateLanguage with selected value on submit'` — trigger submit, verify `accountService.updateLanguage('EN')` called
3. `'sets saveSuccess on successful submit'` — verify `saveSuccess() === true`
4. `'sets saveError key on failed submit'` — `accountService.updateLanguage` throws → `saveError() === 'account.error.save'`
5. `'does not call updateLanguage when form is pristine/untouched'` — optional guard

**For `account.service.spec.ts`:** Follow `instance-config.service.spec.ts` pattern — one test verifying `PUT /api/account/language-preference` is called with `{ language: 'EN' }`.

### Frontend: AccountComponent pattern

Follow `AdminSettingsComponent` exactly (Story 1.5):
- `standalone: true`, `inject()` for DI, `signal()` for state, `firstValueFrom()` for async calls
- No NgRx, no `BehaviorSubject`
- Reactive form via `FormBuilder.nonNullable.group()`
- Plain HTML only — no Angular Material (Story 1.7's scope)
- All strings via `| translate`

Account component form:
```typescript
readonly form = this.fb.nonNullable.group({
  language: ['EN' as 'EN' | 'FR', [Validators.required]]
});
```

On init: `this.form.patchValue({ language: this.auth.currentUser()?.preferredLanguage ?? 'EN' })`

On submit: call `accountService.updateLanguage(lang)`, then `this.translateService.use(lang.toLowerCase())`, update `auth.currentUser` signal to reflect new language.

### Frontend: update AuthService.currentUser after language change

After saving in `AccountComponent`, update the `auth.currentUser` signal:
```typescript
const current = this.auth.currentUser();
if (current) {
  this.auth.currentUser.set({ ...current, preferredLanguage: lang });
}
```

`currentUser` is a `signal<CurrentUser | null>` in `AuthService` — it's writable from the component, which is fine for this pattern.

### Spring MessageSource — infrastructure only in this story

`messages_en.properties` and `messages_fr.properties` exist at `src/main/resources/` but are currently empty (comment only). Spring Boot auto-configures `MessageSource` with `spring.messages.basename=messages` as the default. Verify this is either the default (no config needed) or explicitly set.

Add at least the following entries to enable testing:
```properties
# messages_en.properties
app.name=PluriBourse
```
```properties
# messages_fr.properties
app.name=PluriBourse
```

PDF-generation stories (2.5, 5.x) will populate `messages_*.properties` with their specific keys. This story only wires the infrastructure and proves it works.

### Integration Test storyboard: LanguagePreferenceIT

Single scenario class, ordered methods:

| Order | Method | Description |
|-------|--------|-------------|
| 1 | `get_me_returns_preferredLanguage` | GET /api/auth/me with admin session → assert `preferredLanguage = "FR"` is in response JSON |
| 2 | `volunteer_get_me_returns_preferredLanguage` | GET /api/auth/me with volunteer1 session → assert `preferredLanguage` present |
| 3 | `update_language_en_as_volunteer` | PUT /api/account/language-preference `{"language":"EN"}` with volunteer1 → 200 |
| 4 | `get_me_after_update_returns_en` | GET /api/auth/me with volunteer1 session → `preferredLanguage = "EN"` |
| 5 | `update_language_back_to_fr` | PUT /api/account/language-preference `{"language":"FR"}` → 200 (restore for isolation) |
| 6 | `unauthenticated_put_returns_401` | PUT /api/account/language-preference without session → 401 |
| 7 | `invalid_language_code_returns_400` | PUT with `{"language":"DE"}` → 400 (Bean Validation `@Pattern(regexp="EN\|FR")`) |
| 8 | `lowercase_language_returns_400` | PUT with `{"language":"en"}` → 400 (pattern is case-sensitive) |
| 9 | `first_login_sets_fr_from_accept_language` | Create a new user via `UserRepository` with `languageInitialized = false`, POST /api/auth/login with `Accept-Language: fr-FR,fr;q=0.9`, verify `preferredLanguage = "FR"` in response AND DB has `language_initialized = true` |
| 10 | `first_login_sets_en_for_unsupported_language` | Create a new user with `languageInitialized = false`, POST /api/auth/login with `Accept-Language: de-DE,de;q=0.9`, verify `preferredLanguage = "EN"` |
| 11 | `subsequent_login_preserves_preference` | Login again with the user from Order(9) (now `languageInitialized = true`, `preferredLanguage = FR`) → `preferredLanguage = "FR"` in response |
| 12 | `message_source_resolves_en` | Inject `MessageSource`, assert `getMessage("app.name", null, Locale.ENGLISH)` = "PluriBourse" |
| 13 | `message_source_resolves_fr` | assert `getMessage("app.name", null, Locale.FRENCH)` = "PluriBourse" |

**CRITICAL test infrastructure notes:**
- Always `.with(csrf())` on PUT requests — Spring Security 7 rejects without it → 403
- Use explicit `import org.springframework.http.MediaType;` (not wildcard — JUnit 5 exports `org.junit.jupiter.api.MediaType`)
- Bean Validation failures (`@Valid @RequestBody`) → HTTP 400 (not 422)
- 422 is for business exceptions (`BusinessException`)
- For Order(9) and (10): inject `UserRepository` and `PasswordEncoder` to create test users programmatically; `loginSession9` / `loginSession10` are local to those methods (not stored in IT class)

### i18n key namespace: `account`

```json
// en.json — add "account" section
"account": {
  "title": "Account Settings",
  "language": {
    "label": "Interface language",
    "EN": "English",
    "FR": "French"
  },
  "save": "Save",
  "success": "Language preference saved.",
  "error": {
    "save": "Failed to save language preference."
  }
}
```

```json
// fr.json — vouvoiement
"account": {
  "title": "Paramètres du compte",
  "language": {
    "label": "Langue de l'interface",
    "EN": "Anglais",
    "FR": "Français"
  },
  "save": "Enregistrer",
  "success": "Préférence de langue enregistrée.",
  "error": {
    "save": "Impossible d'enregistrer la préférence de langue."
  }
}
```

### Frontend test: Vitest (not Jest/Jasmine)

Same as Story 1.5 — frontend tests use **Vitest**:
- `vi.fn()`, `vi.spyOn()`, `vi.clearAllMocks()` — NEVER `jasmine.createSpyObj` or `jest.fn()`
- `provideTranslateService({ lang: 'en' })` in TestBed providers (not `TranslateModule.forRoot()`)
- `TranslatePipe` in imports (not `TranslateModule`)
- `provideHttpClient()` + `provideHttpClientTesting()` for HTTP tests

### What NOT to change

- `SecurityConfig.java` — no changes needed; `/api/account/**` falls under `anyRequest()` which allows authenticated non-SELLER users
- `001-core-schema.xml` — do NOT modify existing changesets; add new changeset 006
- `IntegrationTest.java` — no changes needed
- `UserMapper.java` / `UserDto.java` — these are for admin volunteer management; `preferredLanguage` is not needed there
- Existing Liquibase changesets 001-005 — only add 006

## Project Structure Notes

**Backend — new files:**
```
pluribourse-backend/src/main/resources/db/changelog/006-user-language-initialized.xml
pluribourse-backend/src/main/java/org/pluribourse/user/dtos/UpdateLanguageDto.java
pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AccountController.java
pluribourse-backend/src/test/java/org/pluribourse/user/LanguagePreferenceIT.java
```

**Backend — files to update:**
```
pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml  ← add 006 include
pluribourse-backend/src/main/java/org/pluribourse/user/entities/User.java  ← add languageInitialized field
pluribourse-backend/src/main/java/org/pluribourse/user/entities/PluriBourseUserDetails.java  ← add getPreferredLanguage(), isLanguageInitialized()
pluribourse-backend/src/main/java/org/pluribourse/user/dtos/UserSessionDto.java  ← add preferredLanguage field
pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AuthController.java  ← update me() to pass preferredLanguage
pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/LoginSuccessHandler.java  ← inject UserService, detect language
pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java  ← add initializeLanguage() + updateLanguagePreference()
pluribourse-backend/src/main/java/org/pluribourse/user/cli/AdminCreateRunner.java  ← add setLanguageInitialized(false)
pluribourse-backend/src/main/resources/messages_en.properties  ← add app.name entry
pluribourse-backend/src/main/resources/messages_fr.properties  ← add app.name entry
pluribourse-backend/src/test/resources/db/changelog/test-data.sql  ← add language_initialized = true
```

**Frontend — new files:**
```
pluribourse-frontend/src/app/features/account/account.component.ts
pluribourse-frontend/src/app/features/account/account.component.html
pluribourse-frontend/src/app/features/account/account.component.spec.ts
pluribourse-frontend/src/app/services/account.service.ts
pluribourse-frontend/src/app/services/account.service.spec.ts
```

**Frontend — files to update:**
```
pluribourse-frontend/src/app/services/auth.service.ts  ← add preferredLanguage to CurrentUser, inject TranslateService, apply language
pluribourse-frontend/src/app/services/auth.service.spec.ts  ← update for new CurrentUser shape + TranslateService mock
pluribourse-frontend/src/app/app.config.ts  ← change provideTranslateService({ lang: 'fr' }) to provideTranslateService({ lang: 'en' })
pluribourse-frontend/src/app/app.routes.ts  ← add /account route
pluribourse-frontend/public/i18n/en.json  ← add account section
pluribourse-frontend/public/i18n/fr.json  ← add account section
```

## References

- [Source: epics.md#Story 1.6] — user story, acceptance criteria, dev note on vouvoiement
- [Source: epics.md#FR-001–FR-004, FR-067] — i18n requirements, language per user account
- [Source: architecture.md#Architecture des Données] — `preferredLanguage` on `User` entity, `enum {EN, FR}`
- [Source: architecture.md#Frontend Architecture] — ngx-translate, runtime switching, no build-per-locale
- [Source: architecture.md#Clés i18n] — `feature.section.key` format, max 3 levels
- [Source: 1-5-configuration-de-linstance-page-de-parametres-admin.md] — AdminSettingsComponent pattern (signals, firstValueFrom, FormBuilder.nonNullable, plain HTML, Vitest), HTTP 400 vs 422, `.with(csrf())` requirement, explicit MediaType import
- [Source: 1-5-configuration-de-linstance-page-de-parametres-admin.md#Security: existing SecurityConfig] — `/api/admin/**` only; `anyRequest()` covers the rest
- [Source: User.java] — `preferredLanguage: Language` field already exists at `org.pluribourse.user.entities.User`
- [Source: PluriBourseUserDetails.java] — wraps User, has `getUserId()`, `getRole()`, `isForcePasswordChange()`; needs `getPreferredLanguage()` and `isLanguageInitialized()`
- [Source: AuthController.java:25] — `changePassword()` pattern for SecurityContext update — reuse for `AccountController`
- [Source: LoginSuccessHandler.java] — current 1-arg constructor, builds `UserSessionDto(username, role, forcePasswordChange)`
- [Source: UserService.java] — `getUser(Long userId)` private helper — reuse in new methods
- [Source: test-data.sql] — 3 test accounts with `preferred_language = 'FR'`; need to add `language_initialized = true`
- [Source: IntegrationTest.java] — base class with `@SpringBootTest`, `@DirtiesContext(AFTER_CLASS)`, `@TestInstance(PER_CLASS)`
- [Source: 001-core-schema.xml] — `preferred_language VARCHAR(2) DEFAULT 'FR'`; confirms `languageInitialized` column needed separately
- [Source: db.changelog-master.xml] — current order: 001, 002, 004, 005; add 006 after 005

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Spring Boot 4's `ResourceBundleCondition` requires `messages.properties` (base file) to exist for `MessageSourceAutoConfiguration` to activate. Having only locale-specific files (`messages_en.properties`, `messages_fr.properties`) is not enough — added `messages.properties` as the base/fallback bundle.
- Adding `TranslateService` to `AuthService` caused a cascade of DI failures in guard/interceptor specs. Fixed by adding `{ provide: TranslateService, useValue: { use: () => of({}) } }` to all affected TestBed configurations (`auth.guard.spec.ts`, `admin.guard.spec.ts`, `auth.interceptor.spec.ts`).
- Stale scaffold test in `app.spec.ts` (`should render title` expecting `Hello, pluribourse-frontend`) replaced with a `router-outlet` presence check.
- Pre-existing bug in `admin-settings.component.spec.ts`: imports had `GlobalGlobalInstanceConfigService`/`GlobalGlobalInstanceConfigDto` (double "Global" prefix). Fixed as part of making the test suite compile.
- Final test results: **backend 70/70**, **frontend 45/45**.

### File List

**New files:**
- `pluribourse-backend/src/main/resources/db/changelog/006-user-language-initialized.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/user/dtos/UpdateLanguageDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AccountController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/user/LanguagePreferenceIT.java`
- `pluribourse-frontend/src/app/features/account/account.component.ts`
- `pluribourse-frontend/src/app/features/account/account.component.html`
- `pluribourse-frontend/src/app/features/account/account.component.spec.ts`
- `pluribourse-frontend/src/app/services/account.service.ts`
- `pluribourse-frontend/src/app/services/account.service.spec.ts`

**Modified files:**
- `pluribourse-backend/src/main/resources/messages.properties` ← NEW base bundle (required for Spring Boot MessageSourceAutoConfiguration)
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/user/entities/User.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/entities/PluriBourseUserDetails.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/dtos/UserSessionDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AuthController.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/security/handlers/LoginSuccessHandler.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/services/UserService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/user/cli/AdminCreateRunner.java`
- `pluribourse-backend/src/main/resources/messages_en.properties`
- `pluribourse-backend/src/main/resources/messages_fr.properties`
- `pluribourse-backend/src/test/resources/db/changelog/test-data.sql`
- `pluribourse-frontend/src/app/services/auth.service.ts`
- `pluribourse-frontend/src/app/services/auth.service.spec.ts`
- `pluribourse-frontend/src/app/app.config.ts`
- `pluribourse-frontend/src/app/app.routes.ts`
- `pluribourse-frontend/src/app/app.spec.ts` ← removed stale scaffold test
- `pluribourse-frontend/src/app/core/guards/auth.guard.spec.ts` ← added TranslateService mock + preferredLanguage
- `pluribourse-frontend/src/app/core/guards/admin.guard.spec.ts` ← added TranslateService mock + preferredLanguage
- `pluribourse-frontend/src/app/core/interceptors/auth.interceptor.spec.ts` ← added TranslateService mock + preferredLanguage
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.spec.ts` ← fixed double-Global import typo
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`

### Review Findings

- [x] [Review][Patch] `translateService.use()` ne doit pas être awaitée dans `onSubmit()` — séparer DB save (montrer succès immédiatement) et chargement traduction (fire-and-forget, comme `logout()`) [account.component.ts]

- [x] [Review][Patch] `@Pattern(regexp = "EN|FR")` non ancré — doit être `^(EN|FR)$` [UpdateLanguageDto.java]
- [x] [Review][Patch] `LoginSuccessHandler` ne met pas à jour le SecurityContext après `initializeLanguage()` — la session conserve le `PluriBourseUserDetails` périmé (languageInitialized=false, ancien preferredLanguage) ; un rechargement de page appelle `GET /api/auth/me` et retourne la mauvaise langue [LoginSuccessHandler.java]
- [x] [Review][Patch] `messages.properties` (fichier base requis pour Spring Boot MessageSourceAutoConfiguration) non tracké par git — manquant du commit, AC5 et LanguagePreferenceIT Orders 12/13 échouent sur un checkout propre [pluribourse-backend/src/main/resources/messages.properties]
- [x] [Review][Patch] `restoreSession()` crash silencieux si `preferredLanguage` absent de la réponse — `undefined.toLowerCase()` lève TypeError → catch set `currentUser = null` → déconnexion silencieuse [auth.service.ts]
- [x] [Review][Patch] `detectLanguage()` fallback sur la locale JVM par défaut si header Accept-Language absent — un client API sans header sur un serveur avec `Locale.FRENCH` par défaut recevrait FR [LoginSuccessHandler.java]
- [x] [Review][Patch] Commentaire `@BeforeAll` — faux positif, déjà correct ("setUpMockMvc") [LanguagePreferenceIT.java]
- [x] [Review][Patch] Assertion manquante : `auth.currentUser.set()` n'est pas vérifié après submit réussi dans `account.component.spec.ts` [account.component.spec.ts]

- [x] [Review][Defer] Race condition sur premier login concurrent (même utilisateur, deux onglets simultanés) — probabilité négligeable dans le cas d'usage cible [LoginSuccessHandler.java] — deferred, pre-existing
- [x] [Review][Defer] NPE dans Order(11) si Order(9) échoue — limitation inhérente au pattern de scénario ordonné intentionnel décrit dans CLAUDE.md [LanguagePreferenceIT.java] — deferred, pre-existing
- [x] [Review][Defer] Bannière `saveSuccess` persistante si l'utilisateur change le select sans resoumission — amélioration UX, non spécifié dans les AC [account.component.ts] — deferred, pre-existing
- [x] [Review][Defer] Admin seedé obtient `language_initialized = false` après upgrade — langue écrasée au 1er login post-migration (comportement attendu par la spec) [006-user-language-initialized.xml] — deferred, pre-existing
- [x] [Review][Defer] `Language.valueOf()` non guardé dans AccountController — secondaire : fixé implicitement par le patch P1 (`^(EN|FR)$`) [AccountController.java] — deferred, pre-existing

#### Second review pass (2026-06-24)

- [x] [Review][Patch] `AccountController` — SecurityContext mis à jour via `SecurityContextHolder` seulement, sans écriture explicite dans l'attribut de session HTTP ; avec un vrai store Spring Session JDBC (production), `GET /api/auth/me` après PUT peut retourner la langue périmée [pluribourse-backend/src/main/java/org/pluribourse/user/controllers/AccountController.java:30]
- [x] [Review][Patch] `auth.service.ts login()` — `user.preferredLanguage.toLowerCase()` sans null-guard `?? 'EN'`, contrairement à `restoreSession()` ; lève TypeError si la réponse serveur omet le champ [pluribourse-frontend/src/app/services/auth.service.ts:31]
- [x] [Review][Patch] `LanguagePreferenceIT` — aucun scénario couvrant le cycle complet logout → re-login pour valider AC3 ("la préférence survit à la déconnexion et au re-login") [pluribourse-backend/src/test/java/org/pluribourse/user/LanguagePreferenceIT.java]

- [x] [Review][Defer] `restoreSession()` ne restaure pas la langue sur le chemin 403 `forcePasswordChange` — `/change-password` s'affiche en 'en' quelle que soit la préférence ; nécessiterait d'inclure `preferredLanguage` dans le body 403 (changement backend hors scope) [pluribourse-frontend/src/app/services/auth.service.ts:61] — deferred, pre-existing

## Change Log

- 2026-06-23 — Story 1.6 created: ready-for-dev
- 2026-06-23 — Story 1.6 validated: fixed 3 issues (Project Structure Notes `defaultLanguage` → `lang`; T5.2 wording clarified; auth.service.spec.ts URL bugs documented; T11.2 login URL explicit)
- 2026-06-24 — Story 1.6 implemented: all 17 tasks completed, backend 70/70, frontend 45/45 — status → review
- 2026-06-24 — Story 1.6 code review: 8 patches appliqués (dont D1 résolu), 5 différés, 5 dismissés — status → done
- 2026-06-24 — Story 1.6 second review pass: 3 patches appliqués, 1 différé, 8 dismissés — status → done
