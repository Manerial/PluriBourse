---
baseline_commit: 2917bd9097ae5c667a4ac913fd03905c35e49630
---

# Story 1.5: Instance Configuration & Admin Settings Page

Status: done

## Story

As an administrator,
I want to configure instance settings in a dedicated page,
so that the application reflects the identity and operational parameters of my association.

## Acceptance Criteria

1. **Given** the admin navigates to `/admin/settings`, **When** the page loads, **Then** the current configuration is displayed: association name, default commission rate (default 20%), default document language (EN/FR).

2. **Given** the admin updates the association name and saves, **When** the server restarts, **Then** the association name is preserved (persisted in database — verified by checking DB state after PUT).

3. **Given** the admin sets the commission rate to 15% and saves, **When** the value is stored, **Then** the stored value is a BigDecimal `15.00` (neither float nor double).

4. **Given** the admin sets the default document language to "FR", **When** the value is saved, **Then** any new edition created afterward inherits language "FR" (FR-006 — `InstanceConfigService.getDefaultDocumentLanguage()` is the hook; verification of edition inheritance is Story 2.1's responsibility), **And** existing editions retain their own value unchanged.

## Tasks / Subtasks

- [x] **T1 — Liquibase: `004-instance-config.xml`** (AC: 1, 2, 3, 4)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/004-instance-config.xml` with changeset `004-instance-config`: table `instance_config` (columns: `id BIGINT PK`, `association_name VARCHAR(255) NOT NULL DEFAULT ''`, `default_commission_rate DECIMAL(5,2) NOT NULL DEFAULT 20.00`, `default_document_language VARCHAR(2) NOT NULL DEFAULT 'EN'`)
  - [x] T1.2 — Same changeset: INSERT default row `(id=1, association_name='', default_commission_rate=20.00, default_document_language='EN')`
  - [x] T1.3 — Update `db.changelog-master.xml`: add `<include file="db/changelog/004-instance-config.xml"/>` **before** `005-user-volunteer-fields.xml` (preserve sequential order: 001 → 002 → 004 → 005)

- [x] **T2 — Backend: `InstanceConfig` entity** (AC: 2, 3)
  - [x] T2.1 — Create `org.pluribourse.shared.instanceconfig.entity.InstanceConfig` with `@Entity @Table(name = "instance_config")`, Lombok `@Getter @Setter @NoArgsConstructor`
  - [x] T2.2 — Fields: `@Id private Long id` (NO `@GeneratedValue` — singleton, always id=1), `@Column String associationName`, `@Column BigDecimal defaultCommissionRate` (precision=5, scale=2), `@Enumerated(EnumType.STRING) @Column(length=2) Language defaultDocumentLanguage` — import `org.pluribourse.user.enums.Language` (already exists, do NOT create a new enum)

- [x] **T3 — Backend: Repository, DTOs, Mapper** (AC: 1, 2, 3, 4)
  - [x] T3.1 — Create `org.pluribourse.shared.instanceconfig.repository.InstanceConfigRepository extends JpaRepository<InstanceConfig, Long>` (no extra methods needed)
  - [x] T3.2 — Create `org.pluribourse.shared.instanceconfig.dto.InstanceConfigDto` record: `String associationName`, `BigDecimal defaultCommissionRate`, `String defaultDocumentLanguage`
  - [x] T3.3 — Create `org.pluribourse.shared.instanceconfig.dto.UpdateInstanceConfigDto` record with Bean Validation: `@NotNull @Size(max=255) String associationName`, `@NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3, fraction=2) BigDecimal defaultCommissionRate`, `@NotNull @Pattern(regexp="EN|FR") String defaultDocumentLanguage`
  - [x] T3.4 — Create `org.pluribourse.shared.instanceconfig.mapper.InstanceConfigMapper` (MapStruct): `@Mapper(componentModel = "spring")` with `@Mapping(target = "defaultDocumentLanguage", expression = "java(config.getDefaultDocumentLanguage().name())") InstanceConfigDto toDto(InstanceConfig config)`

- [x] **T4 — Backend: `InstanceConfigService`** (AC: 1, 2, 3, 4)
  - [x] T4.1 — Create `org.pluribourse.shared.instanceconfig.service.InstanceConfigService` with `@Service @RequiredArgsConstructor`
  - [x] T4.2 — Private `findConfig()`: `repository.findById(1L).orElseThrow(() -> new IllegalStateException("instance_config row missing"))` — throws to fail loudly if migration didn't run
  - [x] T4.3 — `getConfig()` annotated `@Transactional(readOnly = true)`: returns `mapper.toDto(findConfig())`
  - [x] T4.4 — `updateConfig(UpdateInstanceConfigDto dto)` annotated `@Transactional`: load via `findConfig()`, set all 3 fields manually (like UserService pattern — no `toEntity` mapper), `repository.save(config)`, return `mapper.toDto(saved)`
  - [x] T4.5 — Two public helper methods for Story 2.1: `getDefaultDocumentLanguage()` returning `Language` and `getDefaultCommissionRate()` returning `BigDecimal` — both call `findConfig()` internally

- [x] **T5 — Backend: `InstanceConfigController`** (AC: 1, 2, 3, 4)
  - [x] T5.1 — Create `org.pluribourse.shared.instanceconfig.controller.InstanceConfigController` with `@RestController @RequestMapping("/api/admin/instance-config") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")`
  - [x] T5.2 — `GET /api/admin/instance-config` → `ResponseEntity<InstanceConfigDto>` via `service.getConfig()`
  - [x] T5.3 — `PUT /api/admin/instance-config` with `@Valid @RequestBody UpdateInstanceConfigDto` → `ResponseEntity<InstanceConfigDto>` via `service.updateConfig(dto)` (200 OK, not 204 — returns updated state)

- [x] **T6 — Backend: `InstanceConfigIT`** (coverage ≥ 80%)
  - [x] T6.1 — Create `org.pluribourse.shared.InstanceConfigIT extends IntegrationTest` with `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@TestInstance(Lifecycle.PER_CLASS)` (already in IntegrationTest via `@SpringBootTest`)
  - [x] T6.2 — `@BeforeAll setUpSessions()`: POST `/login` with `test_admin`/`Admin` → `adminSession`; POST `/login` with `volunteer1`/`Admin` → `volunteerSession`
  - [x] T6.3 — Ordered test methods (see Dev Notes for full scenario storyboard)

- [x] **T7 — Frontend: model, service, route** (AC: 1, 2, 3, 4)
  - [x] T7.1 — Create `pluribourse-frontend/src/app/models/instance-config.model.ts` with `InstanceConfigDto` and `UpdateInstanceConfigRequest` interfaces
  - [x] T7.2 — Create `pluribourse-frontend/src/app/services/instance-config.service.ts` with `getConfig()` and `updateConfig()` methods
  - [x] T7.3 — Add `settings` route to `pluribourse-frontend/src/app/features/admin/admin.routes.ts`

- [x] **T8 — Frontend: `AdminSettingsComponent`** (AC: 1, 2, 3, 4)
  - [x] T8.1 — Create `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts` (standalone, uses reactive form)
  - [x] T8.2 — Create `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.html` (separate file — no inline template per CLAUDE.md)

- [x] **T9 — i18n keys** (FR-004: no hardcoded strings)
  - [x] T9.1 — Add `admin.settings` section to `pluribourse-frontend/public/i18n/en.json`
  - [x] T9.2 — Add `admin.settings` section to `pluribourse-frontend/public/i18n/fr.json` (vouvoiement systematique)

## Dev Notes

### Database: 004-instance-config.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="004-instance-config" author="pluribourse">
        <createTable tableName="instance_config">
            <column name="id" type="BIGINT">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="association_name" type="VARCHAR(255)" defaultValue="">
                <constraints nullable="false"/>
            </column>
            <!-- DECIMAL(5,2): supports 0.00 to 999.99 — commission rate in percent -->
            <column name="default_commission_rate" type="DECIMAL(5,2)" defaultValueNumeric="20.00">
                <constraints nullable="false"/>
            </column>
            <!-- VARCHAR(2) instead of ENUM for H2/MariaDB portability — same rationale as users.role -->
            <column name="default_document_language" type="VARCHAR(2)" defaultValue="EN">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <!-- Singleton: exactly one row, id=1 always -->
        <insert tableName="instance_config">
            <column name="id" valueNumeric="1"/>
            <column name="association_name" value=""/>
            <column name="default_commission_rate" valueNumeric="20.00"/>
            <column name="default_document_language" value="EN"/>
        </insert>
    </changeSet>

</databaseChangeLog>
```

**db.changelog-master.xml after update:**
```xml
<include file="db/changelog/001-core-schema.xml"/>
<include file="db/changelog/002-spring-session.xml"/>
<include file="db/changelog/004-instance-config.xml"/>
<include file="db/changelog/005-user-volunteer-fields.xml"/>
```

### InstanceConfig entity — no @GeneratedValue

```java
package org.pluribourse.shared.instanceconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.user.enums.Language;
import java.math.BigDecimal;

@Entity
@Table(name = "instance_config")
@Getter
@Setter
@NoArgsConstructor
public class InstanceConfig {

    @Id  // NO @GeneratedValue — singleton, always id=1, set by migration
    private Long id;

    @Column(name = "association_name", nullable = false, length = 255)
    private String associationName;

    @Column(name = "default_commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultCommissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_document_language", nullable = false, length = 2)
    private Language defaultDocumentLanguage;
}
```

### InstanceConfigService — full implementation

```java
package org.pluribourse.shared.instanceconfig.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.shared.instanceconfig.dto.*;
import org.pluribourse.shared.instanceconfig.entity.InstanceConfig;
import org.pluribourse.shared.instanceconfig.mapper.InstanceConfigMapper;
import org.pluribourse.shared.instanceconfig.repository.InstanceConfigRepository;
import org.pluribourse.user.enums.Language;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class InstanceConfigService {

    private final InstanceConfigRepository repository;
    private final InstanceConfigMapper mapper;

    @Transactional(readOnly = true)
    public InstanceConfigDto getConfig() {
        return mapper.toDto(findConfig());
    }

    @Transactional
    public InstanceConfigDto updateConfig(UpdateInstanceConfigDto dto) {
        var config = findConfig();
        config.setAssociationName(dto.associationName());
        config.setDefaultCommissionRate(dto.defaultCommissionRate());
        config.setDefaultDocumentLanguage(Language.valueOf(dto.defaultDocumentLanguage()));
        return mapper.toDto(repository.save(config));
    }

    // Used by Story 2.1 (EditionService) to initialize new editions
    @Transactional(readOnly = true)
    public Language getDefaultDocumentLanguage() {
        return findConfig().getDefaultDocumentLanguage();
    }

    // Used by Story 2.1 (EditionService) to initialize new editions
    @Transactional(readOnly = true)
    public BigDecimal getDefaultCommissionRate() {
        return findConfig().getDefaultCommissionRate();
    }

    private InstanceConfig findConfig() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("instance_config row missing — ensure migration 004 ran"));
    }
}
```

### InstanceConfigMapper

```java
package org.pluribourse.shared.instanceconfig.mapper;

import org.mapstruct.*;
import org.pluribourse.shared.instanceconfig.dto.InstanceConfigDto;
import org.pluribourse.shared.instanceconfig.entity.InstanceConfig;

@Mapper(componentModel = "spring")
public interface InstanceConfigMapper {

    @Mapping(target = "defaultDocumentLanguage",
             expression = "java(config.getDefaultDocumentLanguage().name())")
    InstanceConfigDto toDto(InstanceConfig config);
}
```

### DTOs

```java
// InstanceConfigDto.java — response only, no validation annotations
public record InstanceConfigDto(
        String associationName,
        BigDecimal defaultCommissionRate,
        String defaultDocumentLanguage
) {}

// UpdateInstanceConfigDto.java — request with Bean Validation
public record UpdateInstanceConfigDto(
        @NotNull @Size(max = 255) String associationName,
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal defaultCommissionRate,
        @NotNull @Pattern(regexp = "EN|FR") String defaultDocumentLanguage
) {}
```

### InstanceConfigController

```java
@RestController
@RequestMapping("/api/admin/instance-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InstanceConfigController {

    private final InstanceConfigService service;

    @GetMapping
    public ResponseEntity<InstanceConfigDto> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping
    public ResponseEntity<InstanceConfigDto> updateConfig(
            @Valid @RequestBody UpdateInstanceConfigDto dto) {
        return ResponseEntity.ok(service.updateConfig(dto));
    }
}
```

### Integration Test storyboard

Test class: `org.pluribourse.shared.InstanceConfigIT` — all methods in order form a single scenario.

**CRITICAL: always `.with(csrf())` on PUT requests** (Spring Security 7 rejects without it → 403). Always use explicit `import org.springframework.http.MediaType;` (JUnit 5 exports `org.junit.jupiter.api.MediaType` — wildcard import causes a collision).

**HTTP 400 for Bean Validation failures** (not 422). `@Valid @RequestBody` violations (`MethodArgumentNotValidException`) → 400. HTTP 422 is reserved for business rule exceptions (`BusinessException`).

```java
package org.pluribourse.shared;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.*;
import org.pluribourse.shared.instanceconfig.dto.*;
import org.pluribourse.shared.instanceconfig.repository.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;   // EXPLICIT — not wildcard (JUnit 5 / MediaType collision)
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstanceConfigIT extends IntegrationTest {

    @Autowired
    private InstanceConfigRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteerLogin = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test @Order(1)
    void unauthenticated_get_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    void volunteer_get_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3)
    void admin_get_returns_defaults() throws Exception {
        mockMvc.perform(get("/api/admin/instance-config").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value(""))
                .andExpect(jsonPath("$.defaultCommissionRate").value(20.00))
                .andExpect(jsonPath("$.defaultDocumentLanguage").value("EN"));
    }

    @Test @Order(4)
    void admin_put_updates_association_name_and_persists() throws Exception {
        var dto = new UpdateInstanceConfigDto("Mon Association", new BigDecimal("20.00"), "EN");
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())                                    // REQUIRED
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.associationName").value("Mon Association"));

        // Verify persisted in DB (AC2: survives restart = persisted, not in-memory)
        var config = repository.findById(1L).orElseThrow();
        assertThat(config.getAssociationName()).isEqualTo("Mon Association");
    }

    @Test @Order(5)
    void admin_put_commission_rate_stored_as_bigdecimal() throws Exception {
        var dto = new UpdateInstanceConfigDto("Mon Association", new BigDecimal("15.00"), "EN");
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        var config = repository.findById(1L).orElseThrow();
        // Use compareTo (not equals) to avoid scale sensitivity: 15 == 15.0 == 15.00
        assertThat(config.getDefaultCommissionRate().compareTo(new BigDecimal("15"))).isZero();
    }

    @Test @Order(6)
    void admin_put_document_language_fr_persists() throws Exception {
        var dto = new UpdateInstanceConfigDto("Mon Association", new BigDecimal("15.00"), "FR");
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultDocumentLanguage").value("FR"));

        var config = repository.findById(1L).orElseThrow();
        assertThat(config.getDefaultDocumentLanguage().name()).isEqualTo("FR");
    }

    @Test @Order(7)
    void admin_put_commission_over_100_returns_400() throws Exception {
        // Bean Validation (@DecimalMax) → MethodArgumentNotValidException → HTTP 400
        String body = "{\"associationName\":\"\",\"defaultCommissionRate\":101,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(8)
    void admin_put_invalid_language_returns_400() throws Exception {
        // Bean Validation (@Pattern(regexp="EN|FR")) → HTTP 400
        String body = "{\"associationName\":\"\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"DE\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(9)
    void volunteer_put_returns_403() throws Exception {
        String body = "{\"associationName\":\"test\",\"defaultCommissionRate\":20,\"defaultDocumentLanguage\":\"EN\"}";
        mockMvc.perform(put("/api/admin/instance-config")
                        .session(volunteerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
```

No `test-data.sql` changes needed — the 004 changeset seeds the singleton row (id=1) during migration, which runs via `db.changelog-test.xml → db.changelog-master.xml → 004-instance-config.xml`.

### Frontend: Angular file locations

CRITICAL: The frontend uses **`features/`** not `components/` for page-level components. See existing structure:
- `features/admin/users/user-list.component.ts` ← admin feature pages live here
- `features/auth/login/login.component.ts`

Settings page goes in: **`features/admin/settings/admin-settings.component.ts`** (and `.html`)

Services are in: **`services/instance-config.service.ts`** (same level as `auth.service.ts`, `user.service.ts`)

### Frontend: instance-config.model.ts

```typescript
export interface InstanceConfigDto {
  associationName: string;
  defaultCommissionRate: number;
  defaultDocumentLanguage: 'EN' | 'FR';
}

export interface UpdateInstanceConfigRequest {
  associationName: string;
  defaultCommissionRate: number;
  defaultDocumentLanguage: 'EN' | 'FR';
}
```

### Frontend: instance-config.service.ts

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InstanceConfigDto, UpdateInstanceConfigRequest } from '../models/instance-config.model';

@Injectable({ providedIn: 'root' })
export class InstanceConfigService {
  private readonly http = inject(HttpClient);

  getConfig(): Observable<InstanceConfigDto> {
    return this.http.get<InstanceConfigDto>('/api/admin/instance-config');
  }

  updateConfig(data: UpdateInstanceConfigRequest): Observable<InstanceConfigDto> {
    return this.http.put<InstanceConfigDto>('/api/admin/instance-config', data);
  }
}
```

### Frontend: admin.routes.ts — add settings route

Add to existing `adminRoutes` array:
```typescript
{
  path: 'settings',
  loadComponent: () =>
    import('./settings/admin-settings.component').then(m => m.AdminSettingsComponent)
}
```

### Frontend: AdminSettingsComponent pattern

Follow the `UserListComponent` pattern exactly:
- `standalone: true`, `inject()` for DI, `signal()` for state, `firstValueFrom()` for async calls
- No NgRx, no `BehaviorSubject`, no `async` pipe — use `signal()` / `computed()`
- Reactive form via `FormBuilder.nonNullable.group()`
- `ngOnInit()` loads config and patches form
- `onSubmit()` sends PUT and shows success/error signals

**Component form fields:**
- `associationName`: `['', [Validators.maxLength(255)]]`
- `defaultCommissionRate`: `[20, [Validators.required, Validators.min(0), Validators.max(100)]]`
- `defaultDocumentLanguage`: `['EN' as 'EN' | 'FR', [Validators.required]]`

**Template must:**
- Separate `.html` file (no inline template — CLAUDE.md rule)
- All strings via `| translate` (no hardcoded strings — FR-004)
- Show loading state while fetching
- Show success/error message after save

### Frontend: i18n keys

**en.json** — add under `"admin"`:
```json
"settings": {
  "title": "Instance Settings",
  "associationName": "Association name",
  "defaultCommissionRate": "Default commission rate (%)",
  "defaultDocumentLanguage": "Default document language",
  "language": {
    "EN": "English",
    "FR": "French"
  },
  "save": "Save settings",
  "success": "Settings saved.",
  "error": {
    "load": "Failed to load settings.",
    "save": "Failed to save settings."
  }
}
```

**fr.json** — add under `"admin"` (vouvoiement):
```json
"settings": {
  "title": "Paramètres de l'instance",
  "associationName": "Nom de l'association",
  "defaultCommissionRate": "Taux de commission par défaut (%)",
  "defaultDocumentLanguage": "Langue des documents par défaut",
  "language": {
    "EN": "Anglais",
    "FR": "Français"
  },
  "save": "Enregistrer les paramètres",
  "success": "Paramètres enregistrés.",
  "error": {
    "load": "Impossible de charger les paramètres.",
    "save": "Impossible d'enregistrer les paramètres."
  }
}
```

### JacksonConfig: BigDecimal serialization already correct

`JacksonConfig.java` already has:
- `USE_BIG_DECIMAL_FOR_FLOATS`: Angular sends `15` (JSON number) → Spring deserializes as `BigDecimal(15)` automatically. No additional Jackson configuration needed.
- `WRITE_BIGDECIMAL_AS_PLAIN`: Responses serialize `BigDecimal("20.00")` as `20.00` (not scientific notation `2E+1`).

**Do NOT touch `JacksonConfig.java`** — it already handles BigDecimal correctly for this story.

### Frontend: plain HTML — no Angular Material components

**This story uses plain HTML** (same as existing `user-list.component.html`). No `mat-form-field`, `mat-input`, `mat-select`, `mat-button`, etc. Angular Material UI components are Story 1.7's scope. Use `<form>`, `<input>`, `<select>`, `<button>` exactly like the user components.

### Frontend tests: Vitest (not Jest/Jasmine)

Frontend tests use **Vitest**. Use:
- `vi.fn()`, `vi.spyOn()`, `vi.clearAllMocks()` — **never** `jasmine.createSpyObj` or `jest.fn()`
- `provideTranslateService({ lang: 'en' })` in TestBed providers (not `TranslateModule.forRoot()`)
- `TranslatePipe` in imports (not `TranslateModule`)

### Security: existing SecurityConfig covers this story

`/api/admin/**` → `hasRole('ADMIN')` is already configured in `SecurityConfig.java`. No changes to `SecurityConfig` needed. The `@PreAuthorize("hasRole('ADMIN')")` on the controller is belt-and-suspenders (defense in depth).

### Cross-story dependency note (Story 2.1)

Story 2.1 (Edition CRUD) will call `instanceConfigService.getDefaultDocumentLanguage()` and `instanceConfigService.getDefaultCommissionRate()` to initialize new editions. These methods are already defined in `InstanceConfigService`. Story 2.1 should inject `InstanceConfigService` directly.

### Language enum reuse

`Language` enum is at `org.pluribourse.user.enums.Language` — it has exactly `EN` and `FR`. **Do NOT create a new enum.** Import it in `InstanceConfig.java` and `InstanceConfigService.java`. Cross-package reference within the same module is expected (it's a shared enum, just housed in `user/enums/` by historical convention).

### Commission rate: BigDecimal precision

- DB: `DECIMAL(5,2)` stores up to `999.99` with 2 decimal places.
- DTO validation: `@DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3, fraction=2)` — commission is a percentage, max 100.
- Frontend sends: `number` (JSON). Spring/Jackson deserializes to `BigDecimal` when the target field type is `BigDecimal`.
- Hibernate reads from DB with scale=2 (e.g., `BigDecimal("15.00"`)).
- Use `compareTo()` not `equals()` in tests to avoid scale sensitivity.

## Project Structure Notes

**Backend — new files:**
```
pluribourse-backend/src/main/resources/db/changelog/004-instance-config.xml
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/entity/InstanceConfig.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/repository/InstanceConfigRepository.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/service/InstanceConfigService.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/dto/InstanceConfigDto.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/dto/UpdateInstanceConfigDto.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/mapper/InstanceConfigMapper.java
pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/controller/InstanceConfigController.java
pluribourse-backend/src/test/java/org/pluribourse/shared/InstanceConfigIT.java
```

**Backend — files to update:**
```
pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml  ← add 004 include
```

**Frontend — new files:**
```
pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts
pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.html
pluribourse-frontend/src/app/services/instance-config.service.ts
pluribourse-frontend/src/app/models/instance-config.model.ts
```

**Frontend — files to update:**
```
pluribourse-frontend/src/app/features/admin/admin.routes.ts  ← add 'settings' route
pluribourse-frontend/public/i18n/en.json  ← add admin.settings section
pluribourse-frontend/public/i18n/fr.json  ← add admin.settings section
```

**No changes to:**
- `SecurityConfig.java` — `/api/admin/**` already secured
- `User.java` — no new columns
- Existing Liquibase changesets — only adding 004, not touching 001/002/005
- `UserController.java` / `UserService.java` — separate concerns
- Test infrastructure (`IntegrationTest.java`, `test-data.sql`) — migration seeds the instance_config row

## References

- [Source: epics.md#Story 1.5] — user story and acceptance criteria
- [Source: epics.md#FR-073] — admin settings page: association name, default commission rate, default document language
- [Source: epics.md#FR-016] — default commission rate 20%, configurable per instance
- [Source: epics.md#FR-006] — default document language initializes new editions
- [Source: architecture.md#Backend — Structure de Répertoires Complète] — `shared/instanceconfig/` package layout, `004-instance-config.xml` changeset
- [Source: architecture.md#Patrons de Nommage] — API: `/api/admin/instance-config`, packages: `org.pluribourse.shared.instanceconfig.*`
- [Source: architecture.md#Architecture des Données] — BigDecimal, Liquibase, singleton config pattern
- [Source: 1-4-recuperation-du-mot-de-passe-admin-via-cli.md] — Language enum in `org.pluribourse.user.enums.Language`, no @Transactional needed on single-save ops
- [Source: 1-3-gestion-des-comptes-benevoles.md] — integration test pattern (IntegrationTest, @BeforeAll sessions, @Order)

## File List

**New files:**
- `pluribourse-backend/src/main/resources/db/changelog/004-instance-config.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/entity/InstanceConfig.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/repository/InstanceConfigRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/dto/InstanceConfigDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/dto/UpdateInstanceConfigDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/mapper/InstanceConfigMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/service/InstanceConfigService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/shared/instanceconfig/controller/InstanceConfigController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/shared/InstanceConfigIT.java`
- `pluribourse-frontend/src/app/models/instance-config.model.ts`
- `pluribourse-frontend/src/app/services/instance-config.service.ts`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.ts`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.html`
- `pluribourse-frontend/src/app/features/admin/settings/admin-settings.component.spec.ts`
- `pluribourse-frontend/src/app/services/instance-config.service.spec.ts`

**Modified files:**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` — added 004 include before 005
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — added settings route
- `pluribourse-frontend/public/i18n/en.json` — added admin.settings section
- `pluribourse-frontend/public/i18n/fr.json` — added admin.settings section

### Review Findings

- [x] [Review][Patch] AC3 non prouvé — scale BigDecimal non vérifié dans Order(5) : ajouter `assertThat(config.getDefaultCommissionRate().scale()).isEqualTo(2)` [InstanceConfigIT.java:Order(5)]
- [x] [Review][Patch] Pas de test pour PUT non-authentifié retournant 401 [InstanceConfigIT.java]
- [x] [Review][Patch] `@DecimalMin("0.00")` non testé — ajouter test taux négatif → 400 [InstanceConfigIT.java]
- [x] [Review][Patch] `@NotNull` sur `associationName` non testé — ajouter test body null → 400 [InstanceConfigIT.java]
- [x] [Review][Defer] Pas de cache sur `findConfig()` — optimisation prématurée, JPA first-level cache aide dans les transactions [InstanceConfigService.java] — deferred, pre-existing
- [x] [Review][Defer] `Language.valueOf()` sans guard catch — protégé par `@Valid` dans l'usage actuel ; defer à l'introduction d'appels service directs [InstanceConfigService.java:28] — deferred, pre-existing
- [x] [Review][Defer] `IllegalStateException` depuis `findConfig()` non mappée → HTTP 500 — comportement 500 approprié pour panne d'infrastructure [InstanceConfigService.java:43] — deferred, pre-existing
- [x] [Review][Defer] Pas de bouton "Réessayer" après échec de chargement — amélioration UX hors scope spec [admin-settings.component.html] — deferred, pre-existing
- [x] [Review][Defer] Soumission silencieuse sur formulaire invalide (pas de `markAllAsTouched`) — amélioration UX hors scope spec [admin-settings.component.ts:45] — deferred, pre-existing

### Review Findings — 2ème passe (2026-06-23)

- [x] [Review][Patch] `@NotBlank` manquant sur `associationName` — remplacer `@NotNull` par `@NotBlank` (rejette null, vide et whitespace-only) ; nom d'association obligatoire [UpdateInstanceConfigDto.java:8]
- [x] [Review][Patch] `isLoading` initialisé à `false` — le formulaire vide flashe une frame avant que `ngOnInit` ne mette `isLoading` à `true` [admin-settings.component.ts:38]
- [x] [Review][Patch] `saveSuccess` non réinitialisé dans `ngOnInit` — état "Paramètres enregistrés." peut réapparaître si le composant est réutilisé [admin-settings.component.ts]
- [x] [Review][Patch] Pas de test `@Size(max=255)` → 400 — borne 256 chars non couverte [InstanceConfigIT.java]
- [x] [Review][Patch] Borne 100 non testée comme succès — `@DecimalMax("100.00")` inclusif mais jamais validé positivement [InstanceConfigIT.java]
- [x] [Review][Patch] Borne 0 non testée comme succès — `@DecimalMin("0.00")` inclusif mais jamais validé positivement [InstanceConfigIT.java]
- [x] [Review][Patch] Taux >2 décimales (ex: 15.123) non testé → 400 — `@Digits(fraction=2)` non documenté par un test [InstanceConfigIT.java]
- [x] [Review][Patch] Casse `"en"` non testée → 400 — `@Pattern(regexp="EN|FR")` case-sensitive non documenté par un test [InstanceConfigIT.java]
- [x] [Review][Patch] `patchValue(updated)` non vérifié dans le spec test — submit success ne vérifie pas que le formulaire est patché avec la réponse serveur [admin-settings.component.spec.ts]
- [x] [Review][Defer] `InstanceConfigMapper` NPE si `defaultDocumentLanguage` est null — `config.getDefaultDocumentLanguage().name()` sans null-check ; protégé par contrainte DB `NOT NULL` [InstanceConfigMapper.java:10] — deferred, pre-existing
- [x] [Review][Defer] Order(5) n'assert pas `associationName`/`defaultDocumentLanguage` après PUT — un bug de partial-write ne serait pas détecté [InstanceConfigIT.java:Order(5)] — deferred, pre-existing
- [x] [Review][Defer] État intermédiaire `isSaving()` non testé — si `isSaving.set(true)` était retiré, le test de saveError passerait quand même [admin-settings.component.spec.ts] — deferred, pre-existing

## Dev Agent Record

### Completion Notes

- Backend: Singleton pattern via `@Id` without `@GeneratedValue`; migration 004 seeds row id=1.
- Backend: `Language` enum reused from `org.pluribourse.user.enums.Language` — no new enum created.
- Backend: `InstanceConfigService.getDefaultDocumentLanguage()` and `getDefaultCommissionRate()` hooks ready for Story 2.1.
- Backend: 48 tests pass (0 failures), including 9 new `InstanceConfigIT` tests.
- Frontend: `AdminSettingsComponent` follows `UserListComponent` pattern exactly (signals, `firstValueFrom`, `FormBuilder.nonNullable`).
- Frontend: Plain HTML only, no Angular Material (scoped to Story 1.7).
- Frontend: 35 tests pass (7 new: 5 `AdminSettingsComponent` + 2 `InstanceConfigService`). Pre-existing `app.spec.ts` failure unrelated to this story — confirmed failure exists on baseline commit.
- All i18n strings added to `en.json` and `fr.json` (vouvoiement for FR).

## Change Log

- 2026-06-23 — Story 1.5 created: ready-for-dev
- 2026-06-23 — Validation pass: added full `InstanceConfigIT` test code with `.with(csrf())`, explicit `MediaType` import, JSON request bodies, `ObjectMapper` injection; added JacksonConfig BigDecimal note, plain HTML constraint, Vitest note, HTTP 400 vs 422 clarification
- 2026-06-23 — Implementation complete: all 9 backend + 7 frontend + 2 i18n + 1 migration tasks done; 48 backend tests pass, no new frontend regressions
- 2026-06-23 — Verify pass: added frontend tests — 5 AdminSettingsComponent + 2 InstanceConfigService; 35 frontend tests pass total
- 2026-06-23 — Code review: 4 patches applied to InstanceConfigIT (BigDecimal scale assertion, unauthenticated PUT 401, negative commission 400, null associationName 400); 5 items deferred; story → done
- 2026-06-23 — Code review 2ème passe: 9 patches appliqués (@NotBlank sur associationName, isLoading=true, saveSuccess reset, 6 nouveaux tests IT boundary + blank/too-long/excess-decimal/lowercase-lang, patchValue assertion spec); 3 defers; story reste done
