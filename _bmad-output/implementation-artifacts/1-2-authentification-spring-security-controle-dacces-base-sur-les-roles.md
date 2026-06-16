---
baseline_commit: 30e9db7d51fc43aa55d35ab8def96f334c0090be
---

# Story 1.2: Authentification Spring Security & Contrôle d'accès basé sur les rôles

Status: done

## Story

As an administrator,
I want to log in with my credentials and benefit from role-restricted access to admin pages,
so that the application is secured and the admin/volunteer interfaces are strictly separated from the start.

## Acceptance Criteria

1. **Given** any user accesses a protected route while unauthenticated, **When** the request is processed, **Then** a 401 response is returned (not a 302 redirect) — Angular handles the navigation to `/login`.

2. **Given** the admin submits "Admin" / "Admin" on first login, **When** authentication succeeds, **Then** the response includes `{"forcePasswordChange": true}`, access to all other endpoints returns 403 with `{"type": "...", "detail": "Password change required"}` until the password is changed, and the session is stored in the MariaDB `SPRING_SESSION` table.

3. **Given** a session is established, **When** the backend container is restarted, **Then** the session survives and the user remains authenticated on the next request (FR-066 — Spring Session JDBC).

4. **Given** a VOLUNTEER tries to access `/api/admin/**`, **When** the request is processed, **Then** a 403 RFC 7807 Problem Details response is returned.

5. **Given** any request from a user with the SELLER role, **When** processed by Spring Security, **Then** a 403 is returned regardless of the endpoint.

6. **Given** an admin calls `POST /logout`, **When** the request is processed, **Then** the session is invalidated in the database and a 200 is returned.

## Tasks / Subtasks

- [x] **T1 — User entity, Role enum, UserDetailsService** (AC: 2, 3, 4, 5)
  - [x] T1.1 — Create `Role` enum in `org.pluribourse.user.entity.Role` with values `ADMIN`, `VOLUNTEER`, `SELLER`
  - [x] T1.2 — Create `Language` enum in `org.pluribourse.user.entity.Language` with values `EN`, `FR`
  - [x] T1.3 — Create `User` JPA entity in `org.pluribourse.user.entity.User` mapping to the existing `users` table (all columns: id, username, password, role, preferred_language, seller_profile_id, force_password_change)
  - [x] T1.4 — Create `UserRepository` extending `JpaRepository<User, Long>` with `Optional<User> findByUsername(String username)` in `org.pluribourse.user.repository`
  - [x] T1.5 — Create `PluriBourseUserDetails implements UserDetails` wrapping `User`, exposing `isForcePasswordChange()` — in `org.pluribourse.shared.security`
  - [x] T1.6 — Create `PluriBourseUserDetailsService implements UserDetailsService` loading `User` from `UserRepository`, throwing `UsernameNotFoundException` if absent — in `org.pluribourse.shared.security`

- [x] **T2 — Full SecurityConfig** (AC: 1, 2, 3, 4, 5, 6)
  - [x] T2.1 — Replace the Story 1.1 minimal `SecurityConfig` with the full configuration: form login with custom success/failure handlers, Spring Session JDBC, role-based access rules (SELLER explicitly blocked on all routes), CSRF with `CookieCsrfTokenRepository` + `ignoringRequestMatchers("/login")`, custom 401/403 entry points (RFC 7807), `PasswordEncoder` bean, explicit `DaoAuthenticationProvider` bean
  - [x] T2.2 — Create `LoginSuccessHandler` returning `{username, role, forcePasswordChange}` as JSON 200 (using `ObjectMapper` + `UserSessionDto` — same shape as `/api/auth/me`) instead of a redirect. Angular needs `username` and `role` to route correctly after login.
  - [x] T2.3 — Create `LoginFailureHandler` returning RFC 7807 Problem Details with status 401
  - [x] T2.4 — Create `ForcePasswordChangeFilter extends OncePerRequestFilter`: if authenticated user has `forcePasswordChange=true` AND path is not `/api/auth/change-password` AND not `/logout` → return 403 with RFC 7807 `{"type": "…/password-change-required", "status": 403, "detail": "Password change required"}`
  - [x] T2.5 — Create `LogoutSuccessHandler` returning HTTP 200 (not a redirect) — Angular handles navigation to `/login`

- [x] **T3 — AuthController & ChangePassword** (AC: 2, 6)
  - [x] T3.1 — Create `ChangePasswordDto` with `newPassword` (not blank, min 8 chars) in `org.pluribourse.user.dto`
  - [x] T3.2 — Create `AuthController` in `org.pluribourse.user.controller` with `POST /api/auth/change-password` (authenticated only): use `@Valid @RequestBody ChangePasswordDto` — the `@Valid` is mandatory for Bean Validation to trigger; delegate to `UserService.changePassword()`
  - [x] T3.3 — Create `UserService` in `org.pluribourse.user.service` with `changePassword(Long userId, String newRawPassword)` method
  - [x] T3.4 — Create `GET /api/auth/me` in `AuthController`: returns `UserSessionDto {username, role, forcePasswordChange}` as JSON 200; returns 401 via `ProblemDetailAuthenticationEntryPoint` if not authenticated (no redirect). Used by Angular `AuthService` on startup to restore session state after page reload. **No explicit security rule needed** — covered by `anyRequest().authenticated()` in `SecurityConfig`. `/api/auth/me` **IS in EXEMPT_PATHS** of `ForcePasswordChangeFilter` (see filter code), so users with `forcePasswordChange=true` can still call it to restore session state.

- [x] **T4 — Frontend: login page, change-password page, auth guard** (AC: 1, 2)
  - [x] T4.1 — Update `app.config.ts` `provideHttpClient()` call: add `withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })` **and** `withInterceptors([authInterceptor])` in the **same** call (see T4.10 — both options go in one `provideHttpClient()` invocation)
  - [x] T4.2 — Create `AuthService` in `src/app/services/auth.service.ts`: `login(username, password)`, `logout()`, `changePassword(newPassword)`, `restoreSession()` (calls `GET /api/auth/me`, sets `currentUser`), `currentUser = signal<{username: string, role: string, forcePasswordChange: boolean} | null>(null)`, `isAuthenticated = computed(() => this.currentUser() !== null)` — `isAuthenticated` must be `computed()` (derived from `currentUser`), not a separate `signal(false)`
  - [x] T4.2b — Register `APP_INITIALIZER` in `app.config.ts` to call `AuthService.restoreSession()` before the router activates — prevents route guards from firing before session state is restored (which would cause a spurious redirect to `/login` on every page refresh)
  - [x] T4.3 — Create `AuthGuard` (functional guard using `inject(AuthService)`) in `src/app/core/guards/auth.guard.ts`: if not authenticated → navigate to `/login`
  - [x] T4.4 — Create `AdminGuard` in `src/app/core/guards/admin.guard.ts`: checks role is ADMIN
  - [x] T4.5 — Create minimal `LoginComponent` at `src/app/features/auth/login/login.component.ts`: reactive form with username/password, calls `AuthService.login()`, on `forcePasswordChange=true` navigates to `/change-password`, on success navigates to `/admin` or `/volunteer` based on role
  - [x] T4.6 — Create `ChangePasswordComponent` at `src/app/features/auth/change-password/change-password.component.ts`: reactive form with `newPassword`, calls `AuthService.changePassword()`, on success navigates to home
  - [x] T4.7 — Update `app.routes.ts` with routes: `/login` → `LoginComponent`, `/change-password` → `ChangePasswordComponent` (authenticated), all other routes protected with `AuthGuard`
  - [x] T4.8 — Add i18n keys for auth screens in `en.json` and `fr.json` (all text through ngx-translate — no hardcoded strings)
  - [x] T4.9 — Create `src/app/core/interceptors/auth.interceptor.ts` (functional `HttpInterceptorFn`): on HTTP 401 response → `AuthService.currentUser.set(null)` + `Router.navigate(['/login'])`; on HTTP 403 response where `error.error?.type?.includes('password-change-required')` is true → `Router.navigate(['/change-password'])`. **Do NOT redirect all 403s** — SELLER-blocking and admin-only 403s must not trigger this redirect.
  - [x] T4.10 — (Handled in T4.1) Confirm both `withXsrfConfiguration` and `withInterceptors([authInterceptor])` are in the **same** `provideHttpClient()` call — Angular only allows one `provideHttpClient()` per providers array

- [x] **T5 — Tests** (coverage target ≥ 80%)
  - [x] T5.0 — Add `spring-security-test` to `pom.xml` (scope `test`, version managed by Spring Boot BOM — no explicit version): enables `@WithMockUser` and `SecurityMockMvcRequestPostProcessors`
  - [x] T5.1 — `SecurityConfigIT.java` (`@SpringBootTest` + `MockMvc`): unauthenticated GET `/api/sellers` → 401; VOLUNTEER on `/api/admin/anything` → 403; SELLER on any endpoint → 403; logout → 200
  - [x] T5.2 — `ForcePasswordChangeFilterTest.java`: mock authenticated user with `forcePasswordChange=true`, request to `/api/sellers` → 403 with RFC 7807; request to `/api/auth/change-password` → passes through
  - [x] T5.3 — `AuthControllerTest.java` (`MockMvc`): `POST /api/auth/change-password` with valid body → 200, `force_password_change` set to false; with blank password → 422 RFC 7807
  - [x] T5.4 — `UserServiceTest.java` (unit + Mockito): `changePassword` BCrypt-encodes the new password and saves

## Dev Notes

### ⚠️ Critical: What Story 1.1 Left for Story 1.2

The Story 1.1 review explicitly deferred these items to Story 1.2:
- `Role` enum `(ADMIN, VOLUNTEER, SELLER)` — **does not exist yet**; to be created in `org.pluribourse.user.entity`
- `User` JPA entity — **does not exist yet**; maps to the `users` table created by changeset 001
- Full Spring Security form login (`formLogin()`) — Story 1.1's `SecurityConfig` has `CSRF disabled` and no `formLogin()`
- `force_password_change=true` enforcement — the admin account is seeded with this flag but no mechanism enforces it yet

**Do not create** user management endpoints (list/create/update/disable volunteers) — those are Story 1.3.

---

### Stack — Do Not Deviate

| Technology | Version |
|---|---|
| Java | **21** (LTS) |
| Spring Boot | **4.0.6** (Spring Framework 7, Spring Security 7) |
| Angular | **21** (LTS — do NOT upgrade to 22) |
| spring-session-jdbc | managed by Spring Boot BOM |

---

### User JPA Entity — Map to Existing Schema

The `users` table already exists (Liquibase changeset 001). `role` and `preferred_language` are `VARCHAR(20)` (not ENUM — see Story 1.1 note: "use VARCHAR(20) for H2 compatibility"). Use `@Enumerated(EnumType.STRING)`.

```java
@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 20)
    private Language preferredLanguage;

    @Column(name = "seller_profile_id")
    private Long sellerProfileId;   // nullable, no FK constraint yet (Epic 3)

    @Column(name = "force_password_change", nullable = false)
    private boolean forcePasswordChange;
}
```

---

### Spring Security 7 — Full SecurityConfig

Spring Security 7 (with Spring Boot 4.0.6) uses the same lambda DSL. Key constraints:
- **No `WebSecurityConfigurerAdapter`** — already removed in earlier versions
- **`authorizeHttpRequests()`** replaces `authorizeRequests()`
- **`formLogin()`** lambda style
- **CSRF**: use `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler` (non-XOR, simpler for Angular SPA)

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            PluriBourseUserDetailsService userDetailsService,
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            LogoutSuccessHandler logoutSuccessHandler,
            ForcePasswordChangeFilter forcePasswordChangeFilter) throws Exception {

        http
            .userDetailsService(userDetailsService)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/login")  // login cannot be CSRF-forged (needs credentials)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
                .accessDeniedHandler(new ProblemDetailAccessDeniedHandler())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/**").not().hasRole("SELLER")  // AC5: block SELLER universally
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/login")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(logoutSuccessHandler)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .addFilterAfter(forcePasswordChangeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            PluriBourseUserDetailsService uds, PasswordEncoder pe) {
        var provider = new DaoAuthenticationProvider(pe);
        provider.setUserDetailsService(uds);
        return provider;
    }
}
```

> ⚠️ **Do NOT set `SessionCreationPolicy.STATELESS`** — this disables session creation and breaks Spring Session JDBC, which invalidates AC3 (session survives restart). The default policy `IF_REQUIRED` is correct.

---

### Login Flow — SPA-Friendly (No Browser Redirects)

**Do NOT use Spring Security's default redirect behavior** — Angular `HttpClient` does not handle 302 redirects to HTML pages gracefully. Instead:

**LoginSuccessHandler** returns JSON 200 — inject `ObjectMapper` and reuse `UserSessionDto` (same shape as `/api/auth/me`; `String.format` must not be used — usernames containing `"` or `\` would produce invalid JSON):
```java
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    public LoginSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
            Authentication auth) throws IOException {
        PluriBourseUserDetails userDetails = (PluriBourseUserDetails) auth.getPrincipal();
        var dto = new UserSessionDto(
            userDetails.getUsername(),
            userDetails.getUser().getRole().name(),
            userDetails.isForcePasswordChange()
        );
        res.setContentType("application/json");
        res.setStatus(200);
        objectMapper.writeValue(res.getWriter(), dto);
    }
}
```

**LoginFailureHandler** returns RFC 7807 401:
```java
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res,
            AuthenticationException ex) throws IOException {
        res.setContentType("application/problem+json");
        res.setStatus(401);
        res.getWriter().write(
            "{\"type\":\"https://pluribourse/errors/authentication-failed\"," +
            "\"title\":\"Authentication Failed\",\"status\":401," +
            "\"detail\":\"Invalid username or password\"}"
        );
    }
}
```

**LogoutSuccessHandler** returns 200 (no redirect):
```java
@Component
public class LogoutSuccessHandler implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {
    @Override
    public void onLogoutSuccess(HttpServletRequest req, HttpServletResponse res,
            Authentication auth) throws IOException {
        res.setStatus(200);
    }
}
```

**Custom 401/403 entry points** (for unauthenticated access to API, and access denied):
- Both return `application/problem+json` with the appropriate RFC 7807 body
- This prevents Spring Security from returning HTML login-redirect responses for API calls

---

### ForcePasswordChange Filter

```java
@Component
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private static final List<String> EXEMPT_PATHS =
        List.of("/api/auth/change-password", "/api/auth/me", "/logout", "/login", "/actuator/health");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof PluriBourseUserDetails ud) {
            if (ud.isForcePasswordChange() && EXEMPT_PATHS.stream().noneMatch(req.getRequestURI()::startsWith)) {
                res.setContentType("application/problem+json");
                res.setStatus(403);
                res.getWriter().write(
                    "{\"type\":\"https://pluribourse/errors/password-change-required\"," +
                    "\"title\":\"Password Change Required\",\"status\":403," +
                    "\"detail\":\"You must change your password before accessing this resource\"}"
                );
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
```

---

### Spring Session JDBC — Already Configured

`application.properties` from Story 1.1 already has:
```properties
spring.session.store-type=jdbc
spring.session.timeout=P1D
spring.session.jdbc.initialize-schema=never   # Liquibase manages the tables
```

Liquibase changesets 002 (MariaDB) and the H2 variant already exist. **No new DB changes needed for sessions.** Just wiring Spring Security to use sessions (the `HttpSecurity` form login configuration above is sufficient — Spring Session JDBC auto-integrates with Spring Security's `HttpSession`).

---

### CSRF + Angular Integration

Backend: `CookieCsrfTokenRepository.withHttpOnlyFalse()` writes the `XSRF-TOKEN` cookie.

Frontend `app.config.ts` — update `provideHttpClient` (T4.1 + T4.10 go in the **same** call — Angular allows only one `provideHttpClient()` per providers array):
```typescript
provideHttpClient(
  withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
  withInterceptors([authInterceptor])
)
```

Angular automatically reads the `XSRF-TOKEN` cookie and sends `X-XSRF-TOKEN` on all mutating requests (POST, PUT, DELETE). This is the standard Angular-Spring Security CSRF integration.

**Login POST**: `/login` is excluded from CSRF protection via `ignoringRequestMatchers("/login")` (already wired in `SecurityConfig`). After the first GET to any page (e.g., `GET /api/auth/me` on app startup), the backend sets the `XSRF-TOKEN` cookie; Angular then includes `X-XSRF-TOKEN` on all subsequent mutating calls.

---

### AuthController — POST /api/auth/change-password

```
POST /api/auth/change-password
Authorization: Session cookie (authenticated users only)
Content-Type: application/json
Body: { "newPassword": "minimum8chars" }

Response 200: {} (empty body)
Response 422: RFC 7807 if validation fails
Response 401: if not authenticated (handled by ProblemDetailAuthenticationEntryPoint)
```

The `newPassword` field: minimum 8 characters, not blank. Use `@NotBlank` + `@Size(min = 8)` Bean Validation on the DTO.

After changing the password: BCrypt-encode with `PasswordEncoder`, save the `User`, set `forcePasswordChange = false`.

**Extracting the authenticated user ID** — use the injected `Authentication` parameter (Spring MVC resolves it automatically):
```java
@PostMapping("/api/auth/change-password")
public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordDto dto,
        Authentication authentication) {
    var userDetails = (PluriBourseUserDetails) authentication.getPrincipal();
    userService.changePassword(userDetails.getUser().getId(), dto.getNewPassword());
    return ResponseEntity.ok().build();
}
```
Spring Security guarantees `authentication` is non-null here (the filter chain blocks unauthenticated requests before reaching the controller).

**Do not log the new password** (NFR-007 no-PII in logs; passwords are credentials, not PII, but still must not be logged).

---

### Angular Auth Flow

**`AuthService` responsibilities:**
- `login(username, password)`: POST `/login` (form-urlencoded: `username=...&password=...`), returns `{username, role, forcePasswordChange}`
- `logout()`: POST `/logout`, navigates to `/login`
- `changePassword(newPassword)`: POST `/api/auth/change-password` with JSON body
- `restoreSession()`: GET `/api/auth/me` → sets `currentUser` on success, sets `null` on 401. **Called via `APP_INITIALIZER`** — see below.
- `currentUser = signal<{username: string, role: string, forcePasswordChange: boolean} | null>(null)` — set after login
- `isAuthenticated = computed(() => this.currentUser() !== null)`

**`APP_INITIALIZER` — session restoration on page reload**

Without this, route guards fire before the `GET /api/auth/me` response arrives, causing a spurious redirect to `/login` on every page refresh even when the session is valid.

```typescript
// app.config.ts
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([authInterceptor])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: (authService: AuthService) => () => authService.restoreSession(),
      deps: [AuthService],
      multi: true
    },
    provideTranslateService({ lang: 'fr' }),
    provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' })
  ]
};
```

`restoreSession()` must return a `Promise` or `Observable` — Angular waits for it before bootstrapping the router:
```typescript
restoreSession(): Promise<void> {
  return firstValueFrom(
    this.http.get<{username: string, role: string, forcePasswordChange: boolean}>('/api/auth/me').pipe(
      tap(user => this.currentUser.set(user)),
      catchError(() => { this.currentUser.set(null); return EMPTY; })
    )
  ).then(() => undefined).catch(() => undefined);
}
```

**Add backend endpoint** `GET /api/auth/me` returning `{username, role, forcePasswordChange}` — or 401 if not authenticated (no redirect). This allows Angular to restore auth state after a page reload.

**Login POST format** — Spring Security expects `application/x-www-form-urlencoded`:
```typescript
login(username: string, password: string) {
  const body = new HttpParams()
    .set('username', username)
    .set('password', password);
  return this.http.post<LoginResponse>('/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  });
}
```

**AuthGuard** (functional, Angular 21 style):
```typescript
export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  // Avoid infinite loop: /change-password itself is guarded by authGuard
  const goingToChangePassword = route.routeConfig?.path === 'change-password';
  if (!goingToChangePassword && auth.currentUser()?.forcePasswordChange) {
    return router.createUrlTree(['/change-password']);
  }
  return true;
};
```

**Route structure** (`app.routes.ts`):
```typescript
[
  { path: 'login', component: LoginComponent },
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [authGuard] },
  { path: 'admin', canActivate: [authGuard, adminGuard],
    loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES) },
  { path: 'volunteer', canActivate: [authGuard],
    loadChildren: () => import('./features/volunteer/volunteer.routes').then(m => m.VOLUNTEER_ROUTES) },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
]
```

**Route stub files** — exact content required for lazy loading to compile:
```typescript
// src/app/features/admin/admin.routes.ts
import { Routes } from '@angular/router';
export const ADMIN_ROUTES: Routes = [];

// src/app/features/volunteer/volunteer.routes.ts
import { Routes } from '@angular/router';
export const VOLUNTEER_ROUTES: Routes = [];
```

**Do not implement admin or volunteer pages in this story** — only the auth skeleton routes. Styling is intentionally minimal (plain HTML form); Story 1.7 (Angular Material design system) will style these pages.

---

### ngx-translate v18 API (from Story 1.1 learnings)

Use the v18 API in `app.config.ts`:
```typescript
provideTranslateService({ lang: 'fr' })  // NOT importProvidersFrom(TranslateModule.forRoot(...))
provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' })
```
All text in `LoginComponent` and `ChangePasswordComponent` must use `translate` pipe or `TranslateService` — no hardcoded strings.

---

### i18n Keys (add to en.json and fr.json)

```
auth.login.title, auth.login.username, auth.login.password, auth.login.submit
auth.login.error.invalid-credentials
auth.change-password.title, auth.change-password.new-password, auth.change-password.submit
auth.change-password.error.too-short
```

Follow the 3-level key format: `feature.section.key` (from architecture: `auth.login.title`).

---

### Test Approach

**Backend integration tests** use H2 (same setup as Story 1.1):
- Test `src/test/resources/application.properties` already exists (H2, `MODE=MySQL`, Liquibase enabled, `spring.session.jdbc.initialize-schema=never`)
- `SecurityConfigIT.java` with `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)` + `TestRestTemplate` OR `@AutoConfigureMockMvc` + `MockMvc`
- Use `@WithMockUser(roles = "VOLUNTEER")` from `spring-security-test` for role-based tests
- **AC3 (session survives restart) requires a real `POST /login`**, not `@WithMockUser`. `@WithMockUser` does not write to the `SPRING_SESSION` table. To test AC3: perform a real `POST /login` in the IT test, verify the session row exists in H2, then verify a subsequent authenticated request with the session cookie returns 200. Full container-restart simulation is out of scope for automated tests — the Liquibase changeset 002 creating `SPRING_SESSION` tables is the implementation guarantee for AC3.

**Maven Surefire** already configured from Story 1.1 to include `**/*IT.java`.

**`@WithMockUser` dependency**: add `spring-security-test` to `pom.xml` in `<dependencies>` scope `test`:
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```
(Managed by Spring Boot BOM — no explicit version needed.)

---

### No PII in Logs

- The `LoginFailureHandler` must not log the username from the failed authentication attempt
- `UserService.changePassword()` must not log the old or new password
- Use the user's `id` in log messages, never `username`, email, or name
- **`UsernameNotFoundException` message**: Spring Security may log the exception message internally (debug level). Use a generic message — `throw new UsernameNotFoundException("User not found")` — never `throw new UsernameNotFoundException(username)`. The Spring Security filter chain receives the exception and handles authentication failure without needing the username in the message.

---

### Files Created/Modified in This Story

**Backend — new files:**
- `org.pluribourse.user.entity.Role` (enum)
- `org.pluribourse.user.entity.Language` (enum)
- `org.pluribourse.user.entity.User` (JPA entity)
- `org.pluribourse.user.repository.UserRepository`
- `org.pluribourse.user.service.UserService`
- `org.pluribourse.user.controller.AuthController`
- `org.pluribourse.user.dto.ChangePasswordDto`
- `org.pluribourse.user.dto.UserSessionDto` (response DTO for `GET /api/auth/me`: `{username, role, forcePasswordChange}`)
- `org.pluribourse.shared.security.PluriBourseUserDetails`
- `org.pluribourse.shared.security.PluriBourseUserDetailsService`
- `org.pluribourse.shared.security.LoginSuccessHandler`
- `org.pluribourse.shared.security.LoginFailureHandler`
- `org.pluribourse.shared.security.LogoutSuccessHandler`
- `org.pluribourse.shared.security.ForcePasswordChangeFilter`
- `org.pluribourse.shared.security.ProblemDetailAuthenticationEntryPoint`
- `org.pluribourse.shared.security.ProblemDetailAccessDeniedHandler`

**Backend — modified files:**
- `org.pluribourse.shared.security.SecurityConfig` (full replacement of minimal Story 1.1 version)

**Test files — new:**
- `org.pluribourse.shared.SecurityConfigIT`
- `org.pluribourse.shared.ForcePasswordChangeFilterTest`
- `org.pluribourse.user.AuthControllerTest`
- `org.pluribourse.user.UserServiceTest`

**Frontend — new files:**
- `src/app/services/auth.service.ts`
- `src/app/core/guards/auth.guard.ts`
- `src/app/core/guards/admin.guard.ts`
- `src/app/core/interceptors/auth.interceptor.ts`
- `src/app/features/auth/login/login.component.ts`
- `src/app/features/auth/change-password/change-password.component.ts`
- `src/app/features/admin/admin.routes.ts` (stub: exports `ADMIN_ROUTES: Routes = []`)
- `src/app/features/volunteer/volunteer.routes.ts` (stub: exports `VOLUNTEER_ROUTES: Routes = []`)

**Frontend — modified files:**
- `src/app/app.config.ts` (add `withXsrfConfiguration`, `withInterceptors([authInterceptor])`, `APP_INITIALIZER` for session restoration)
- `src/app/app.routes.ts` (add auth routes + guards)
- `public/i18n/en.json` (add auth i18n keys — matches Story 1.1 path: `pluribourse-frontend/public/i18n/`)
- `public/i18n/fr.json` (add auth i18n keys)

---

## Project Structure Notes

**Package placement:**
- `User`, `Role`, `Language`, `UserRepository`, `UserService`, `AuthController`, `ChangePasswordDto` → `org.pluribourse.user.*` (F7 package per architecture)
- `PluriBourseUserDetails`, `PluriBourseUserDetailsService`, `LoginSuccessHandler`, `LoginFailureHandler`, `LogoutSuccessHandler`, `ForcePasswordChangeFilter`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler` → `org.pluribourse.shared.security` (security infrastructure, not business logic)
- `SecurityConfig` already exists at `org.pluribourse.shared.security.SecurityConfig` — update it in place

**Frontend placement:**
- Auth components → `src/app/features/auth/` (new directory, matches architecture `features/auth/` from Story 1.1 scaffold TODO)
- Guards → `src/app/core/guards/` (matches architecture `core/guards/`)
- `auth.service.ts` → `src/app/services/` (matches architecture `services/`)

**API naming:**
- `/api/auth/change-password` (POST) — `kebab-case`, under `/api/` prefix per architecture
- `/api/auth/me` (GET) — for session restoration
- `/login` and `/logout` are Spring Security endpoints — no `/api/` prefix (Spring Security convention)

## References

- [Source: epics.md#Story 1.2] — user story and acceptance criteria
- [Source: architecture.md#Authentification & Sécurité] — Spring Session JDBC, BCrypt, form login, Spring Security, role model (ADMIN/VOLUNTEER/SELLER), session timeout P1D, no session expiration (FR-066)
- [Source: architecture.md#Backend — Structure de Répertoires Complète] — `user/`, `shared/security/` package layout
- [Source: architecture.md#API & Communication] — RFC 7807, no API versioning, `/api/` prefix
- [Source: architecture.md#Directives d'Application] — BigDecimal, no PII in logs, SecurityConfig must block SELLER role
- [Source: architecture.md#Patrons de Nommage] — kebab-case API routes, camelCase JSON
- [Source: 1-1-mise-en-place-du-squelette-de-projet-baseline-docker-compose.md#Dev Notes] — Spring Boot 4 notes: Jackson2ObjectMapperBuilder static factory, H2 MODE=MySQL, ngx-translate v18 API (`provideTranslateService`/`provideTranslateHttpLoader`), Surefire `*IT.java` config, `spring.session.jdbc.initialize-schema=never` in tests
- [Source: 1-1-mise-en-place-du-squelette-de-projet-baseline-docker-compose.md#Review Findings] — Role enum deferred to Story 1.2; force_password_change enforcement deferred to Story 1.2; CSRF disabled pending Story 1.2 full config

### Review Findings

- [x] [Review][Patch] Stale `forcePasswordChange` dans le SecurityContext après `changePassword` — après que `UserService.changePassword()` met à jour la DB, la session Spring contient toujours l'ancien `PluriBourseUserDetails` avec `forcePasswordChange=true` ; chaque requête suivante reçoit un 403 du `ForcePasswordChangeFilter`, créant une boucle infinie de redirections vers `/change-password` [AuthController.java]
- [x] [Review][Patch] Test attendant HTTP 400 pour mot de passe trop court ; la spec T5.3 exige 422 [AuthControllerTest.java:124]
- [x] [Review][Patch] Clé i18n `auth.login.error` est une string directe ; la spec T4.8 exige `auth.login.error.invalid-credentials` [en.json, fr.json, login.component.ts]
- [x] [Review][Patch] `PluriBourseUserDetails` manque de `serialVersionUID` — Spring Session JDBC sérialise cette classe ; tout ajout de champ cassera les sessions existantes avec `InvalidClassException` [PluriBourseUserDetails.java]
- [x] [Review][Patch] `ForcePasswordChangeFilter.EXEMPT_PATHS` utilise `startsWith` — `/api/auth/change-passwordXXX` serait incorrectement exempté ; utiliser `equals()` pour une correspondance exacte [ForcePasswordChangeFilter.java:41]
- [x] [Review][Patch] `ChangePasswordDto` manque de `@Size(max = 128)` — BCrypt tronque silencieusement à 72 octets, rendant les mots de passe > 72 chars équivalents entre eux [ChangePasswordDto.java]
- [x] [Review][Patch] `AuthService.logout()` efface `currentUser` mais ne navigue pas vers `/login` — la spec précise que `logout()` doit naviguer vers `/login` [auth.service.ts:29-32]
- [x] [Review][Patch] `LoginComponent.loading` retourne toujours `() => false` — le bouton Submit n'est jamais désactivé pendant l'appel async ; doit être un Signal qui passe à `true` pendant le login [login.component.ts:41]
- [x] [Review][Defer] `authGuard` ne vérifie pas `forcePasswordChange` — fonctionne via le fallback intercepteur (403 serveur → intercepteur → `/change-password`) ; la spec montre ce check dans le guard pour éviter un aller-retour serveur [auth.guard.ts] — différé, amélioration UX
- [x] [Review][Defer] `adminGuard` redirige un VOLUNTEER authentifié vers `/login` au lieu de `/volunteer` — mauvaise UX mais pas de boucle infinie [admin.guard.ts] — différé, amélioration UX
- [x] [Review][Defer] Pas de rate limiting sur `/login` — protection brute-force (Spring Security `LockingUserDetailsService` ou Bucket4j) à prévoir dans un epic de hardening sécurité — différé, hors scope
- [x] [Review][Defer] `loadUserByUsername` sans `@Transactional(readOnly = true)` — chaque authentification ouvre une transaction lecture-écriture ; optimisation mineure [PluriBourseUserDetailsService.java] — différé, optimisation
- [x] [Review][Defer] `PluriBourseUserDetails` manque de `isEnabled`/`isAccountNonLocked` — pas de colonne `enabled` sur `User` ; nécessaire pour le verrouillage de compte (Story 1.3+) [PluriBourseUserDetails.java] — différé, hors scope
- [x] [Review][Defer] Colonne `preferred_language` sans contrainte CHECK au niveau DB — valeurs invalides provoqueraient `IllegalArgumentException` JPA [001-core-schema.xml] — différé, hardening schéma
- [x] [Review][Defer] Changement de mot de passe concurrent depuis deux onglets laisse le second onglet avec un contexte de sécurité périmé — résolu une fois le Patch #1 appliqué — différé, secondaire

### 2nd Review Pass Findings (2026-06-16)

- [x] [Review][Patch] `nginx.conf` ne proxifie pas `/login` ni `/logout` — le `location /` sert `index.html` pour ces URLs ; l'authentification est non fonctionnelle en production Docker [nginx.conf]
- [x] [Review][Patch] Entité `User` non `Serializable` — `PluriBourseUserDetails` (Serializable) contient `User` qui ne l'est pas ; Spring Session JDBC lèverait `NotSerializableException` en production MariaDB [User.java]
- [x] [Review][Patch] `ChangePasswordComponent.error` est une fonction ordinaire, pas un Signal — invisible à la détection de changement Angular en contexte async/zoneless ; inconsistant avec `LoginComponent` [change-password.component.ts]
- [x] [Review][Defer] SELLER obtient 200 au login puis 403 permanent — comportement ambigu ; différé (SELLER n'est pas un utilisateur web)
- [x] [Review][Defer] `getRequestURI()` inclut le context-path dans `ForcePasswordChangeFilter` — latent, Spring Boot déploie sans context-path
- [x] [Review][Defer] `UserService.changePassword` lève `IllegalArgumentException` → 500 si user supprimé pendant session — edge case extrême
- [x] [Review][Defer] Changement de mot de passe n'invalide pas les autres sessions actives — hardening futur
- [x] [Review][Defer] Test AC3 ne prouve pas la persistence cross-restart via JDBC — garantie architecturale (changeset 002)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

1. **Spring Security 7: `DaoAuthenticationProvider(PasswordEncoder)` constructor removed** — Only `DaoAuthenticationProvider(UserDetailsService)` constructor exists in Spring Security 7. Use `new DaoAuthenticationProvider(uds)` then `provider.setPasswordEncoder(pe)`.

2. **Spring Security 7: exposing `DaoAuthenticationProvider` as `@Bean` causes double-provider conflict** — When exposed as a `@Bean`, Spring Boot's `AuthenticationProviderBeanManagerConfigurer` registers it globally AND the `.authenticationProvider()` call registers it again in the HTTP-specific `ProviderManager`. The HTTP-specific `ProviderManager` chains to the global parent, resulting in two `DaoAuthenticationProvider` instances both being tried. Diagnosis: `TRACE` log shows `DaoAuthenticationProvider (1/2)` and `(1/1)`. Fix: create the `DaoAuthenticationProvider` **inline** inside `filterChain()` and pass to `http.authenticationProvider()` — do NOT expose it as a `@Bean`.

3. **Liquibase BCrypt hash was wrong** — The initial hash `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyVDyP7fi` stored in changeset 001 was NOT the hash of "Admin". Diagnosed via `BCryptPasswordEncoder.matches("Admin", hash)` returning `false` in a diagnostic test. Root cause: the hash was generated for a different password. Fixed by running `new BCryptPasswordEncoder(10).encode("Admin")` in-project and updating the changeset.

4. **Spring Security 7: `not().hasRole("SELLER")` permits unauthenticated users** — The rule `.requestMatchers("/**").not().hasRole("SELLER")` allows anonymous users because they don't have SELLER role. `anyRequest().authenticated()` never fires because `/**` already matched everything. Fix: use `anyRequest().access((authentication, context) -> new AuthorizationDecision(isAuthenticated && notSeller))` with a custom lambda.

5. **Spring Boot 4: `@AutoConfigureMockMvc` removed** — Use `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()` in `@BeforeEach` instead.

6. **Spring Session JDBC + MockMvc: use `request.getSession(false)` not `Set-Cookie` header** — Spring Session sets cookies via `addCookie()`, not `Set-Cookie` header. In MockMvc, extract the session via `loginResult.getRequest().getSession(false)` and pass it via `.session(session)` to subsequent requests.

7. **ngx-translate v18: use `TranslatePipe` not `TranslateModule`** — For standalone components, import `TranslatePipe` directly (not `TranslateModule` which was removed).

### Completion Notes List

- All 26 backend tests pass (green)
- Angular build compiles without errors
- Frontend uses `TranslatePipe` (ngx-translate v18 API), `APP_INITIALIZER` for session restoration, inline `DaoAuthenticationProvider` in `SecurityConfig`

### File List

**Backend — new:**
- `src/main/java/org/pluribourse/user/entity/Role.java`
- `src/main/java/org/pluribourse/user/entity/Language.java`
- `src/main/java/org/pluribourse/user/entity/User.java`
- `src/main/java/org/pluribourse/user/repository/UserRepository.java`
- `src/main/java/org/pluribourse/user/service/UserService.java`
- `src/main/java/org/pluribourse/user/controller/AuthController.java`
- `src/main/java/org/pluribourse/user/dto/ChangePasswordDto.java`
- `src/main/java/org/pluribourse/user/dto/UserSessionDto.java`
- `src/main/java/org/pluribourse/shared/security/PluriBourseUserDetails.java`
- `src/main/java/org/pluribourse/shared/security/PluriBourseUserDetailsService.java`
- `src/main/java/org/pluribourse/shared/security/LoginSuccessHandler.java`
- `src/main/java/org/pluribourse/shared/security/LoginFailureHandler.java`
- `src/main/java/org/pluribourse/shared/security/LogoutSuccessHandler.java`
- `src/main/java/org/pluribourse/shared/security/ForcePasswordChangeFilter.java`
- `src/main/java/org/pluribourse/shared/security/ProblemDetailAuthenticationEntryPoint.java`
- `src/main/java/org/pluribourse/shared/security/ProblemDetailAccessDeniedHandler.java`

**Backend — modified:**
- `src/main/java/org/pluribourse/shared/security/SecurityConfig.java`
- `src/main/resources/db/changelog/001-core-schema.xml` (fixed BCrypt hash)
- `src/test/java/org/pluribourse/shared/GlobalExceptionHandlerTest.java` (pre-existing: updated "Unprocessable Entity" → "Unprocessable Content" for Spring Framework 7)

**Backend — test (new):**
- `src/test/java/org/pluribourse/shared/SecurityConfigIT.java`
- `src/test/java/org/pluribourse/shared/ForcePasswordChangeFilterTest.java`
- `src/test/java/org/pluribourse/user/AuthControllerTest.java`
- `src/test/java/org/pluribourse/user/UserServiceTest.java`

**Frontend — new:**
- `src/app/services/auth.service.ts`
- `src/app/core/guards/auth.guard.ts`
- `src/app/core/guards/admin.guard.ts`
- `src/app/core/interceptors/auth.interceptor.ts`
- `src/app/features/auth/login/login.component.ts`
- `src/app/features/auth/change-password/change-password.component.ts`
- `src/app/features/admin/admin.routes.ts`
- `src/app/features/volunteer/volunteer.routes.ts`

**Frontend — modified:**
- `src/app/app.config.ts`
- `src/app/app.routes.ts`
- `public/i18n/en.json`
- `public/i18n/fr.json`
