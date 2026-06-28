---
baseline_commit: 2cbdafdc0413f530353ce216a597bd2f39c72b3d
---

# Story 2.1: Edition CRUD & Commission Rate Configuration

Status: done

## Story

As an administrator,
I want to create and manage editions with a free name and a configurable commission rate,
so that each event is correctly identified and configured financially before sellers arrive.

## Acceptance Criteria

1. **Given** admin navigates to `/admin/editions`, **When** page loads, **Then** all editions are listed with name, creation date, and current phase.

2. **Given** admin fills in an edition name and submits, **When** form is submitted, **Then** a new edition is created in phase PREPARATION, commission rate initialized from instance config (20% default), document language initialized from instance config (EN default).

3. **Given** any edition exists (any phase), **When** admin updates the edition's document language (e.g. "FR"), **Then** the new value is saved on the edition (FR-005, FR-006, FR-007).

4. **Given** no active edition exists, **When** admin creates a new edition, **Then** this edition is created in phase PREPARATION and becomes the active edition.

5. **Given** an edition already exists in phase PREPARATION, DEPOSIT, SALE, or POST_SALE, **When** admin attempts to create a new edition, **Then** the system rejects it with an explicit error (FR-010: only one active edition at a time).

6. **Given** an edition is in phase PREPARATION, **When** admin changes commission rate to 15%, **Then** the rate is saved as BigDecimal `15.00`.

7. **Given** an edition has entered DEPOSIT phase, **When** admin attempts to modify the commission rate, **Then** the system rejects it with an explicit error (FR-016: rate locked once Deposit started).

8. **Given** an edition is in DEPOSIT phase or later, **When** admin attempts to delete it, **Then** the system refuses deletion (FR-014).

9. **Given** admin submits the edition creation form with an empty name, **When** request is processed, **Then** a 400 response is returned in RFC 7807 format with an explicit error on the name field.

> **Note on AC9:** The epic specifies 422, but the existing `GlobalExceptionHandler.handleMethodArgumentNotValid` returns 400 for `@Valid` DTO violations. Do NOT change the global handler (it would break all existing tests). Return 400 as-is and test for 400.

## Tasks / Subtasks

- [x] **T1 — Backend: Liquibase migration** (AC: 2, 3, 6)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/007-editions.xml` (see Dev Notes)
  - [x] T1.2 — Add `<include file="db/changelog/007-editions.xml"/>` to `db.changelog-master.xml` after the existing 006 include

- [x] **T2 — Backend: PhaseType enum** (AC: 1, 4, 5, 6, 7, 8)
  - [x] T2.1 — Create `org.pluribourse.edition.entity.PhaseType` enum (see Dev Notes)

- [x] **T3 — Backend: Edition entity** (AC: 1, 2, 3)
  - [x] T3.1 — Create `org.pluribourse.edition.entity.Edition` JPA entity (see Dev Notes)

- [x] **T4 — Backend: DTOs** (AC: 2, 3, 6, 7)
  - [x] T4.1 — Create `org.pluribourse.edition.dto.EditionDto` record (response, no validation)
  - [x] T4.2 — Create `org.pluribourse.edition.dto.CreateEditionDto` record (`@NotBlank @Size(max=255) String name`)
  - [x] T4.3 — Create `org.pluribourse.edition.dto.UpdateCommissionRateDto` record (see Dev Notes)
  - [x] T4.4 — Create `org.pluribourse.edition.dto.UpdateDocumentLanguageDto` record (`@NotNull Language documentLanguage`)

- [x] **T5 — Backend: EditionMapper** (AC: 1, 2)
  - [x] T5.1 — Create `org.pluribourse.edition.mapper.EditionMapper` MapStruct interface

- [x] **T6 — Backend: EditionRepository** (AC: 4, 5, 7, 8)
  - [x] T6.1 — Create `org.pluribourse.edition.repository.EditionRepository` (see Dev Notes)

- [x] **T7 — Backend: EditionService** (AC: 2, 3, 4, 5, 6, 7, 8)
  - [x] T7.1 — Create `org.pluribourse.edition.service.EditionService` (see Dev Notes)
  - [x] T7.2 — Inject `GlobalInstanceConfigService` — use existing `getDefaultCommissionRate()` and `getDefaultDocumentLanguage()` methods

- [x] **T8 — Backend: EditionController** (AC: 1, 2, 3, 6, 7, 8, 9)
  - [x] T8.1 — Create `org.pluribourse.edition.controller.EditionController` (see Dev Notes)

- [x] **T9 — Backend: Integration tests** (AC: 1–9)
  - [x] T9.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionManagementIT.java` (see Dev Notes)

- [x] **T10 — Frontend: Edition model** (AC: 1, 2)
  - [x] T10.1 — Create `pluribourse-frontend/src/app/models/edition.model.ts` (see Dev Notes)

- [x] **T11 — Frontend: EditionService** (AC: 1, 2, 3, 6, 7, 8)
  - [x] T11.1 — Create `pluribourse-frontend/src/app/services/edition.service.ts` (see Dev Notes)
  - [x] T11.2 — Create `pluribourse-frontend/src/app/services/edition.service.spec.ts`

- [x] **T12 — Frontend: EditionListComponent** (AC: 1, 4, 5, 6, 7, 8)
  - [x] T12.1 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts` (see Dev Notes)
  - [x] T12.2 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
  - [x] T12.3 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
  - [x] T12.4 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.scss`

- [x] **T13 — Frontend: EditionFormComponent** (AC: 2, 5, 9)
  - [x] T13.1 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts` (see Dev Notes)
  - [x] T13.2 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
  - [x] T13.3 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`

- [x] **T14 — Frontend: UpdateCommissionRateDialogComponent** (AC: 6, 7)
  - [x] T14.1 — Create `pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.ts` (see Dev Notes)
  - [x] T14.2 — Create `.html` for the dialog
  - [x] T14.3 — Create `.spec.ts`

- [x] **T15 — Frontend: UpdateDocumentLanguageDialogComponent** (AC: 3)
  - [x] T15.1 — Create `pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.ts` (see Dev Notes)
  - [x] T15.2 — Create `.html` for the dialog
  - [x] T15.3 — Create `.spec.ts`

- [x] **T16 — Frontend: Routing & Navigation** (AC: 1)
  - [x] T16.1 — Update `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — add edition routes
  - [x] T16.2 — Update `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` — add Editions nav link in admin sidebar
  - [x] T16.3 — Update `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` for new nav link (see Dev Notes)

- [x] **T17 — Frontend: i18n keys** (AC: 1, 2, 3)
  - [x] T17.1 — Add `edition.*` and `nav.editions` keys to `pluribourse-frontend/public/i18n/en.json` (see Dev Notes)
  - [x] T17.2 — Add same keys (in French, vouvoiement) to `pluribourse-frontend/public/i18n/fr.json` (see Dev Notes)

- [x] **T18 — Frontend: Run `npm test`** — all existing tests must pass, zero regressions

### Review Findings

- [x] [Review][Patch] Signal `submitting` mort — jamais assigné ni lu dans la classe ni le template [edition-list.component.ts:~L1265]
- [x] [Review][Patch] Export `ACTIVE_PHASES` inutilisé — exporté depuis edition.model.ts mais jamais importé ; `isEditable()` hardcode `=== 'PREPARATION'` indépendamment [edition.model.ts:L2]
- [x] [Review][Patch] Aucun `<mat-error>` dans le dialog commission — `[disabled]="form.invalid"` sans message d'erreur ; l'utilisateur ne comprend pas pourquoi le bouton est grisé [update-commission-rate-dialog.component.html]
- [x] [Review][Patch] Précision décimale non contrainte côté Angular — `step="0.01"` cosmétique seulement ; `15.123` passe `Validators.min/max` mais échoue `@Digits(fraction=2)` côté back avec un toast générique sans explication [update-commission-rate-dialog.component.ts]
- [x] [Review][Patch] AC9 — test vérifie uniquement le code HTTP 400, pas la structure RFC 7807 (pas de `jsonPath("$.type")` ni vérification du champ en erreur) [EditionManagementIT.java:@Order(14)]
- [x] [Review][Patch] Aria-label "Cancel" sémantiquement incorrect — le lien `arrow_back` utilise `'edition.create.cancel'` → lecteur d'écran annonce "Cancel" pour un lien de navigation [edition-form.component.html:L1]
- [x] [Review][Defer] Race condition sur création concurrente — check-then-insert sans contrainte DB unique partielle sur phases actives ; deux POST simultanés peuvent créer deux éditions actives [EditionService.java:createEdition()] — deferred, fix nécessite une contrainte DB ou isolation SERIALIZABLE
- [x] [Review][Defer] Assertion BigDecimal style — `compareTo(new BigDecimal("15")).isZero()` à remplacer par `isEqualByComparingTo(new BigDecimal("15"))` pour des messages d'échec lisibles [EditionManagementIT.java:@Order(7)] — deferred, amélioration de style mineure
- [x] [Review][Defer] Race condition double-clic Delete — `confirmDelete()` sans guard `isDeleting` ; deux confirmations successives envoient deux DELETE, le second reçoit 404 et affiche un toast d'erreur alors que la suppression a réussi [edition-list.component.ts:confirmDelete()] — deferred, low risk, UX edge case
- [x] [Review][Defer] `createdEditionId` null en cascade — si @Order(4) échoue, les tests @Order(5) à @Order(16) génèrent des NPE masquant la vraie cause [EditionManagementIT.java] — deferred, pre-existing pattern IT dans le projet
- [x] [Review][Defer] AC7/AC8 couverture partielle — seul DEPOSIT testé pour le verrouillage de commission et le refus de suppression ; SALE, POST_SALE et CLOSED non couverts [EditionManagementIT.java] — deferred, le code est correct (`!= PREPARATION`), à compléter en Story 2.2
- [x] [Review][Defer] POST/PATCH/DELETE non testés avec session volunteer — seul GET retournant 403 est testé [EditionManagementIT.java] — deferred, sécurité appliquée au niveau SecurityConfig + @PreAuthorize
- [x] [Review][Defer] `reloadEditions()` incohérence visuelle — après delete réussi + GET échoué, la table disparaît malgré le toast de succès (les données restent dans le signal mais masquées par `@if (!error())`) [edition-list.component.ts:reloadEditions()] — deferred, async UX pattern, fix nécessite un optimistic update

## Dev Notes

### T1 — 007-editions.xml Liquibase Migration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="007-editions" author="pluribourse">
        <createTable tableName="editions">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="name" type="VARCHAR(255)">
                <constraints nullable="false"/>
            </column>
            <!-- VARCHAR(20) for H2/MariaDB portability — same rationale as users.role -->
            <column name="phase" type="VARCHAR(20)" defaultValue="PREPARATION">
                <constraints nullable="false"/>
            </column>
            <!-- DECIMAL(5,2): supports 0.00–999.99 (commission rate in percent) -->
            <column name="commission_rate" type="DECIMAL(5,2)">
                <constraints nullable="false"/>
            </column>
            <!-- VARCHAR(2) for H2/MariaDB portability — same rationale as users.preferred_language -->
            <column name="document_language" type="VARCHAR(2)">
                <constraints nullable="false"/>
            </column>
            <column name="created_at" type="DATE">
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>

</databaseChangeLog>
```

**WARNING:** `003-category-table-mapping.xml` does NOT exist yet (deferred to Story 2.3). Do NOT create it here. The next migration number is 007 (existing: 001, 002, 004, 005, 006 — 003 is a gap reserved for Story 2.3).

In `db.changelog-master.xml`, add: `<include file="db/changelog/007-editions.xml"/>` after the `006-user-language-initialized.xml` include.

### T2 — PhaseType Enum

```java
package org.pluribourse.edition.entity;

public enum PhaseType {
    PREPARATION,
    DEPOSIT,
    SALE,
    POST_SALE,
    CLOSED
}
```

**Active phases (FR-010):** PREPARATION, DEPOSIT, SALE, POST_SALE. CLOSED is **not** active — a CLOSED edition does not block creating a new edition. Define `ACTIVE_PHASES` as a constant in `EditionService`, not in the enum itself.

### T3 — Edition Entity

```java
package org.pluribourse.edition.entity;

import jakarta.persistence.*;
import lombok.*;
import org.pluribourse.user.enums.Language;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "editions")
@Getter
@Setter
@NoArgsConstructor
public class Edition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhaseType phase;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_language", nullable = false, length = 2)
    private Language documentLanguage;

    @Column(name = "created_at", nullable = false)
    private LocalDate createdAt;
}
```

**Reuse existing `Language` enum** from `org.pluribourse.user.enums.Language` (EN, FR). Do NOT create a new Language enum.

### T4 — DTOs

All DTOs are Java records (same pattern as `GlobalInstanceConfigDto`).

```java
// EditionDto.java — response DTO, no validation annotations needed
package org.pluribourse.edition.dto;

import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.user.enums.Language;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EditionDto(
        Long id,
        String name,
        PhaseType phase,
        BigDecimal commissionRate,
        Language documentLanguage,
        LocalDate createdAt
) {}
```

```java
// CreateEditionDto.java
package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;

public record CreateEditionDto(
        @NotBlank @Size(max = 255) String name
) {}
```

```java
// UpdateCommissionRateDto.java — same validation as GlobalInstanceConfigDto.defaultCommissionRate
package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateCommissionRateDto(
        @NotNull
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate
) {}
```

```java
// UpdateDocumentLanguageDto.java
package org.pluribourse.edition.dto;

import jakarta.validation.constraints.*;
import org.pluribourse.user.enums.Language;

public record UpdateDocumentLanguageDto(
        @NotNull Language documentLanguage
) {}
```

### T5 — EditionMapper

```java
package org.pluribourse.edition.mapper;

import org.mapstruct.*;
import org.pluribourse.edition.dto.EditionDto;
import org.pluribourse.edition.entity.Edition;

@Mapper(componentModel = "spring")
public interface EditionMapper {
    EditionDto toDto(Edition edition);
}
```

MapStruct maps fields by name. `PhaseType` and `Language` enums serialize as-is. No custom mapping needed.

### T6 — EditionRepository

```java
package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.PhaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EditionRepository extends JpaRepository<Edition, Long> {
    boolean existsByPhaseIn(List<PhaseType> phases);
    List<Edition> findAllByOrderByCreatedAtDesc();
}
```

### T7 — EditionService

```java
package org.pluribourse.edition.service;

import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.mapper.EditionMapper;
import org.pluribourse.edition.repository.EditionRepository;
import org.pluribourse.shared.exception.BusinessException;
import org.pluribourse.shared.instanceconfig.service.GlobalInstanceConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EditionService {

    private static final List<PhaseType> ACTIVE_PHASES = List.of(
            PhaseType.PREPARATION, PhaseType.DEPOSIT, PhaseType.SALE, PhaseType.POST_SALE
    );

    private final EditionRepository repository;
    private final EditionMapper mapper;
    private final GlobalInstanceConfigService instanceConfigService;

    @Transactional(readOnly = true)
    public List<EditionDto> getAllEditions() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public EditionDto createEdition(CreateEditionDto dto) {
        if (repository.existsByPhaseIn(ACTIVE_PHASES)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "edition-already-active",
                    "An edition is already active. Close the current edition before creating a new one.");
        }
        Edition edition = new Edition();
        edition.setName(dto.name());
        edition.setPhase(PhaseType.PREPARATION);
        edition.setCommissionRate(instanceConfigService.getDefaultCommissionRate());
        edition.setDocumentLanguage(instanceConfigService.getDefaultDocumentLanguage());
        edition.setCreatedAt(LocalDate.now());
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public EditionDto updateCommissionRate(Long id, UpdateCommissionRateDto dto) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "commission-rate-frozen",
                    "Commission rate is locked once the Deposit phase has started.");
        }
        edition.setCommissionRate(dto.commissionRate());
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public EditionDto updateDocumentLanguage(Long id, UpdateDocumentLanguageDto dto) {
        Edition edition = findById(id);
        edition.setDocumentLanguage(dto.documentLanguage());
        return mapper.toDto(repository.save(edition));
    }

    @Transactional
    public void deleteEdition(Long id) {
        Edition edition = findById(id);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "edition-cannot-be-deleted",
                    "Editions that have progressed past Preparation phase cannot be deleted.");
        }
        repository.delete(edition);
    }

    private Edition findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Edition not found: " + id));
    }
}
```

**CRITICAL:** `GlobalInstanceConfigService.getDefaultCommissionRate()` and `getDefaultDocumentLanguage()` are **already implemented** in Story 1.5 with the comment `// Used by Story 2.1 (EditionService)`. Call them directly — do not re-implement.

`BusinessException(HttpStatus, errorCode, message)` is defined at `org.pluribourse.shared.exception.BusinessException`. `GlobalExceptionHandler` maps it to RFC 7807 `ProblemDetail` automatically.

### T8 — EditionController

```java
package org.pluribourse.edition.controller;

import jakarta.validation.*;
import lombok.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.service.EditionService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/editions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EditionController {

    private final EditionService service;

    @GetMapping
    public ResponseEntity<List<EditionDto>> getAllEditions() {
        return ResponseEntity.ok(service.getAllEditions());
    }

    @PostMapping
    public ResponseEntity<EditionDto> createEdition(@Valid @RequestBody CreateEditionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEdition(dto));
    }

    @PatchMapping("/{id}/commission-rate")
    public ResponseEntity<EditionDto> updateCommissionRate(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommissionRateDto dto) {
        return ResponseEntity.ok(service.updateCommissionRate(id, dto));
    }

    @PatchMapping("/{id}/document-language")
    public ResponseEntity<EditionDto> updateDocumentLanguage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDocumentLanguageDto dto) {
        return ResponseEntity.ok(service.updateDocumentLanguage(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdition(@PathVariable Long id) {
        service.deleteEdition(id);
        return ResponseEntity.noContent().build();
    }
}
```

`/api/admin/editions` is under `/api/admin/**` which is already locked to `ADMIN` role in `SecurityConfig`. `@PreAuthorize("hasRole('ADMIN')")` adds method-level defense-in-depth, consistent with `GlobalInstanceConfigController`.

### T9 — EditionManagementIT

Follow `GlobalInstanceConfigIT.java` as the model (same session setup, same patterns).

```java
package org.pluribourse.edition;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.edition.dto.*;
import org.pluribourse.edition.entity.*;
import org.pluribourse.edition.repository.EditionRepository;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EditionManagementIT extends IntegrationTest {

    @Autowired private EditionRepository repository;
    @Autowired private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long createdEditionId;

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
    
    // Tests ordered as a story-board (state persists between methods via PER_CLASS + no @Transactional):

    @Test @Order(1)
    void unauthenticated_get_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/editions"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(2)
    void volunteer_get_returns_403() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test @Order(3)
    void admin_get_returns_empty_list() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test @Order(4)
    void admin_create_edition_returns_201_with_defaults() throws Exception {
        CreateEditionDto dto = new CreateEditionDto("Bourse 2026");
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Bourse 2026"))
                .andExpect(jsonPath("$.phase").value("PREPARATION"))
                .andExpect(jsonPath("$.commissionRate").value(20.00))
                .andExpect(jsonPath("$.documentLanguage").value("EN"))
                .andReturn();
        EditionDto created = objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class);
        createdEditionId = created.id();
        assertThat(createdEditionId).isNotNull();
    }

    @Test @Order(5)
    void admin_create_second_edition_while_active_returns_422() throws Exception {
        CreateEditionDto dto = new CreateEditionDto("Bourse 2027");
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(6)
    void admin_get_returns_one_edition() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bourse 2026"));
    }

    @Test @Order(7)
    void admin_update_commission_rate_in_preparation_succeeds() throws Exception {
        UpdateCommissionRateDto dto = new UpdateCommissionRateDto(new BigDecimal("15.00"));
        mockMvc.perform(patch("/api/admin/editions/" + createdEditionId + "/commission-rate")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionRate").value(15.00));

        Edition edition = repository.findById(createdEditionId).orElseThrow();
        assertThat(edition.getCommissionRate().compareTo(new BigDecimal("15"))).isZero();
        assertThat(edition.getCommissionRate().scale()).isEqualTo(2);
    }

    @Test @Order(8)
    void admin_update_document_language_to_fr_succeeds() throws Exception {
        UpdateDocumentLanguageDto dto = new UpdateDocumentLanguageDto(org.pluribourse.user.enums.Language.FR);
        mockMvc.perform(patch("/api/admin/editions/" + createdEditionId + "/document-language")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentLanguage").value("FR"));
    }

    @Test @Order(9)
    void simulate_deposit_phase_then_commission_rate_update_returns_422() throws Exception {
        // Direct DB manipulation — Story 2.2 (phase transitions) not yet implemented
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.DEPOSIT);
        repository.save(edition);

        UpdateCommissionRateDto dto = new UpdateCommissionRateDto(new BigDecimal("10.00"));
        mockMvc.perform(patch("/api/admin/editions/" + createdEditionId + "/commission-rate")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(10)
    void admin_update_document_language_in_deposit_phase_succeeds() throws Exception {
        // Edition is still in DEPOSIT from step 9
        UpdateDocumentLanguageDto dto = new UpdateDocumentLanguageDto(org.pluribourse.user.enums.Language.EN);
        mockMvc.perform(patch("/api/admin/editions/" + createdEditionId + "/document-language")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentLanguage").value("EN"));
    }

    @Test @Order(11)
    void admin_delete_in_deposit_phase_returns_422() throws Exception {
        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test @Order(12)
    void admin_delete_in_preparation_phase_succeeds() throws Exception {
        // Reset to PREPARATION so delete is allowed
        Edition edition = repository.findById(createdEditionId).orElseThrow();
        edition.setPhase(PhaseType.PREPARATION);
        repository.save(edition);

        mockMvc.perform(delete("/api/admin/editions/" + createdEditionId)
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test @Order(13)
    void list_is_empty_after_delete() throws Exception {
        mockMvc.perform(get("/api/admin/editions").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test @Order(14)
    void create_edition_with_blank_name_returns_400() throws Exception {
        // AC9: epic says 422, but existing GlobalExceptionHandler.handleMethodArgumentNotValid returns 400
        // for @Valid DTO violations — do NOT change the handler; test for 400
        String body = "{\"name\":\"\"}";
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(15)
    void create_edition_with_null_name_returns_400() throws Exception {
        String body = "{\"name\":null}";
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(16)
    void create_edition_after_closed_edition_succeeds() throws Exception {
        // CLOSED phase is not active — creating after a CLOSED edition is allowed (FR-010)
        // First create an edition
        CreateEditionDto dto1 = new CreateEditionDto("Bourse Clôturée");
        MvcResult r1 = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto1)))
                .andExpect(status().isCreated()).andReturn();
        Long id1 = objectMapper.readValue(r1.getResponse().getContentAsString(), EditionDto.class).id();

        // Set it to CLOSED
        Edition edition = repository.findById(id1).orElseThrow();
        edition.setPhase(PhaseType.CLOSED);
        repository.save(edition);

        // Now create a new edition — should succeed
        CreateEditionDto dto2 = new CreateEditionDto("Bourse Suivante");
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phase").value("PREPARATION"));
    }
}
```

### T10 — Edition Model (TypeScript)

```typescript
// src/app/models/edition.model.ts
export type PhaseType = 'PREPARATION' | 'DEPOSIT' | 'SALE' | 'POST_SALE' | 'CLOSED';

export const ACTIVE_PHASES: readonly PhaseType[] = ['PREPARATION', 'DEPOSIT', 'SALE', 'POST_SALE'] as const;

export interface EditionDto {
  id: number;
  name: string;
  phase: PhaseType;
  commissionRate: number;
  documentLanguage: 'EN' | 'FR';
  createdAt: string; // ISO 8601 date string "YYYY-MM-DD"
}

export interface CreateEditionDto {
  name: string;
}

export interface UpdateCommissionRateDto {
  commissionRate: number;
}

export interface UpdateDocumentLanguageDto {
  documentLanguage: 'EN' | 'FR';
}
```

### T11 — EditionService (Angular)

```typescript
// src/app/services/edition.service.ts
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateEditionDto, EditionDto,
  UpdateCommissionRateDto, UpdateDocumentLanguageDto
} from '../models/edition.model';

@Injectable({ providedIn: 'root' })
export class EditionService {
  private readonly http = inject(HttpClient);
  private readonly BASE = '/api/admin/editions';

  getAll(): Observable<EditionDto[]> {
    return this.http.get<EditionDto[]>(this.BASE);
  }

  create(dto: CreateEditionDto): Observable<EditionDto> {
    return this.http.post<EditionDto>(this.BASE, dto);
  }

  updateCommissionRate(id: number, dto: UpdateCommissionRateDto): Observable<EditionDto> {
    return this.http.patch<EditionDto>(`${this.BASE}/${id}/commission-rate`, dto);
  }

  updateDocumentLanguage(id: number, dto: UpdateDocumentLanguageDto): Observable<EditionDto> {
    return this.http.patch<EditionDto>(`${this.BASE}/${id}/document-language`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/${id}`);
  }
}
```

### T12 — EditionListComponent

Key structure (follows `UserListComponent` pattern):

```typescript
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-edition-list',
  standalone: true,
  imports: [TranslatePipe, RouterLink, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent],
  templateUrl: './edition-list.component.html',
  styleUrl: './edition-list.component.scss'
})
export class EditionListComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly editions = signal<EditionDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.editions.set(await firstValueFrom(this.editionService.getAll()));
    } catch {
      this.error.set('edition.actions.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  isEditable(edition: EditionDto): boolean {
    return edition.phase === 'PREPARATION';
  }

  openEditCommissionRate(edition: EditionDto): void {
    const ref = this.dialog.open<number, UpdateCommissionRateDialogData, UpdateCommissionRateDialogComponent>(
      UpdateCommissionRateDialogComponent,
      {
        data: { editionId: edition.id, currentRate: edition.commissionRate },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabelledBy: 'update-rate-dialog-title',
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (newRate) => {
      if (newRate === undefined) { return; }
      try {
        await firstValueFrom(this.editionService.updateCommissionRate(edition.id, { commissionRate: newRate }));
        this.toast.showSuccess(this.translate.instant('edition.actions.success.updateCommissionRate'));
        await this.reloadEditions();
      } catch {
        this.toast.showError(this.translate.instant('edition.actions.error.updateCommissionRate'));
      }
    });
  }

  openEditDocumentLanguage(edition: EditionDto): void {
    const ref = this.dialog.open<'EN' | 'FR', UpdateDocumentLanguageDialogData, UpdateDocumentLanguageDialogComponent>(
      UpdateDocumentLanguageDialogComponent,
      {
        data: { editionId: edition.id, currentLanguage: edition.documentLanguage },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabelledBy: 'update-lang-dialog-title',
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (newLang) => {
      if (newLang === undefined) { return; }
      try {
        await firstValueFrom(this.editionService.updateDocumentLanguage(edition.id, { documentLanguage: newLang }));
        this.toast.showSuccess(this.translate.instant('edition.actions.success.updateDocumentLanguage'));
        await this.reloadEditions();
      } catch {
        this.toast.showError(this.translate.instant('edition.actions.error.updateDocumentLanguage'));
      }
    });
  }

  confirmDelete(edition: EditionDto): void {
    this.confirmDialog.open({
      title: this.translate.instant('edition.deleteDialog.title'),
      description: this.translate.instant('edition.deleteDialog.description'),
      confirmVariant: 'error',
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) { return; }
      try {
        await firstValueFrom(this.editionService.delete(edition.id));
        this.toast.showSuccess(this.translate.instant('edition.actions.success.delete'));
        await this.reloadEditions();
      } catch {
        this.toast.showError(this.translate.instant('edition.actions.error.delete'));
      }
    });
  }

  navigateToCreate(): void { this.router.navigateByUrl('/admin/editions/create'); }

  private async reloadEditions(): Promise<void> {
    try {
      this.editions.set(await firstValueFrom(this.editionService.getAll()));
    } catch {
      this.error.set('edition.actions.error.load');
    }
  }
}
```

**ConfirmDialogService** is at `src/app/shared/components/confirm-dialog/confirm-dialog.service.ts` — use it for delete confirmation (same pattern as Story 1.8 shared components).

**Commission rate display:** Show as a plain number (e.g. `20`). The column header "Commission (%)" already conveys the unit — no need to append "%" or force 2 decimals in the template. `{{ edition.commissionRate }}` is sufficient.

**After mutation:** reload the edition list with `reloadEditions()` (or `ngOnInit()`).

**Use `takeUntilDestroyed(this.destroyRef)`** on dialog `.closed.subscribe()` calls (pattern from Story 1.10, `user-list.component.ts:82`).

### T13 — EditionFormComponent (Create Edition)

Route: `/admin/editions/create`

```typescript
@Component({
  selector: 'app-edition-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, MatFormFieldModule, MatInputModule, RouterLink, NotificationInlineComponent],
  templateUrl: './edition-form.component.html',
})
export class EditionFormComponent {
  private readonly editionService = inject(EditionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]]
  });
  readonly isSaving = signal(false);
  readonly createError = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isSaving()) { return; }
    this.isSaving.set(true);
    this.createError.set(null);
    try {
      await firstValueFrom(this.editionService.create({ name: this.form.getRawValue().name }));
      this.toast.showSuccess(this.translate.instant('edition.create.success'));
      this.router.navigateByUrl('/admin/editions');
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422) {
        // Active edition already exists (FR-010)
        this.createError.set('edition.create.error.alreadyActive');
      } else {
        this.toast.showError(this.translate.instant('edition.create.error.save'));
      }
    } finally {
      this.isSaving.set(false);
    }
  }
}
```

For `HttpErrorResponse` check: import from `@angular/common/http`.

Show `createError` using `NotificationInlineComponent` (at `src/app/shared/components/notification-inline/notification-inline.component.ts`).

### T14 — UpdateCommissionRateDialogComponent

Follow the exact `ResetPasswordDialogComponent` pattern from Story 1.10.

```typescript
// update-commission-rate-dialog.component.ts
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

export interface UpdateCommissionRateDialogData {
  editionId: number;
  currentRate: number;
}

@Component({
  selector: 'app-update-commission-rate-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, A11yModule, MatFormFieldModule, MatInputModule],
  templateUrl: './update-commission-rate-dialog.component.html',
  styleUrl: './update-commission-rate-dialog.component.scss',
})
export class UpdateCommissionRateDialogComponent {
  readonly dialogRef = inject<DialogRef<number>>(DialogRef);
  readonly data = inject<UpdateCommissionRateDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    commissionRate: [this.data.currentRate, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  confirm(): void {
    if (this.form.invalid) { return; }
    this.dialogRef.close(this.form.getRawValue().commissionRate);
  }

  cancel(): void { this.dialogRef.close(undefined); }
}
```

HTML: `<input type="number" step="0.01" formControlName="commissionRate" [min]="0" [max]="100">` inside `mat-form-field`. Confirm button `[disabled]="form.invalid"`.

**SCSS:** Reuse global dialog styles via `.dialog` class (same as `reset-password-dialog.component.scss`). Use `--pb-space-*` and `--mat-sys-*` tokens — do NOT redeclare them.

CDK Dialog open config (in EditionListComponent):
```typescript
{
  data: { editionId: edition.id, currentRate: edition.commissionRate },
  hasBackdrop: true,
  backdropClass: 'dialog-backdrop',
  panelClass: 'dialog-panel',
  disableClose: false,
  ariaLabelledBy: 'update-rate-dialog-title',
}
```

After dialog closes with a number: call `editionService.updateCommissionRate(id, { commissionRate })`.

**SCSS:** Reuse global dialog styles via `.dialog` class (same as `reset-password-dialog.component.scss`). Use `--pb-space-*` and `--mat-sys-*` tokens — do NOT redeclare them.

### T15 — UpdateDocumentLanguageDialogComponent

```typescript
// update-document-language-dialog.component.ts
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

export interface UpdateDocumentLanguageDialogData {
  editionId: number;
  currentLanguage: 'EN' | 'FR';
}

@Component({
  selector: 'app-update-document-language-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, A11yModule, MatFormFieldModule, MatSelectModule],
  templateUrl: './update-document-language-dialog.component.html',
  styleUrl: './update-document-language-dialog.component.scss',
})
export class UpdateDocumentLanguageDialogComponent {
  readonly dialogRef = inject<DialogRef<'EN' | 'FR'>>(DialogRef);
  readonly data = inject<UpdateDocumentLanguageDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    documentLanguage: [this.data.currentLanguage as 'EN' | 'FR', [Validators.required]]
  });

  confirm(): void {
    if (this.form.invalid) { return; }
    this.dialogRef.close(this.form.getRawValue().documentLanguage);
  }

  cancel(): void { this.dialogRef.close(undefined); }
}
```

HTML: `<mat-select formControlName="documentLanguage">` with `<mat-option value="EN">English</mat-option>` and `<mat-option value="FR">Français</mat-option>` inside `mat-form-field`. Confirm button `[disabled]="form.invalid"`.

### T16 — Routing, Navigation & Spec

**T16.3 — `app-layout.component.spec.ts`:** Two changes:
1. Add `{ path: 'admin/editions', component: StubComponent }` to the `provideRouter([...])` array.
2. Add this test inside the `'when admin is logged in'` describe block (same pattern as the existing users/settings link tests):
```typescript
it('contains nav link to /admin/editions', () => {
  const links: HTMLAnchorElement[] = Array.from(
    fixture.nativeElement.querySelectorAll('a.sidebar__item')
  );
  expect(links.some(l => l.getAttribute('href') === '/admin/editions')).toBe(true);
});
```

### T16 — Routing & Navigation

**`admin.routes.ts` — append after `settings` route:**
```typescript
{
  path: 'editions',
  loadComponent: () =>
    import('./editions/edition-list.component').then((m) => m.EditionListComponent),
},
{
  path: 'editions/create',
  loadComponent: () =>
    import('./editions/edition-form.component').then((m) => m.EditionFormComponent),
},
```

**`app-layout.component.html`** — add Editions nav link **inside the first `<div class="sidebar__section">` block**, replacing the placeholder comment `<!-- Edition nav items added in Epic 2 (Story 2.1+) -->`. Use `<span class="material-symbols-outlined">` NOT MatIcon (see `app-layout.component.ts:7`). Use CSS classes `sidebar__item` / `sidebar__item--active` (same as the Users and Settings links — NOT `sidebar-nav__link`):

```html
<a
  routerLink="/admin/editions"
  routerLinkActive="sidebar__item--active"
  ariaCurrentWhenActive="page"
  class="sidebar__item">
  <span class="material-symbols-outlined" aria-hidden="true">event</span>
  <span>{{ 'nav.admin.editions' | translate }}</span>
</a>
```

### T17 — i18n Keys

**File locations:** `pluribourse-frontend/public/i18n/en.json` and `fr.json` (NOT `src/assets/i18n/` — confirmed by Story 1.10 file list).

**`en.json`** — the `nav` key already exists with a nested `admin` object (`nav.admin.users`, `nav.admin.settings`). Add `editions` inside `nav.admin` — do NOT create a new top-level `nav` key:
```json
"nav": {
  "admin": {
    "editions": "Editions"
  }
},
"edition": {
  "list": {
    "title": "Editions",
    "createButton": "Create Edition",
    "empty": "No editions created yet.",
    "emptyAction": "Create the first edition",
    "columns": {
      "name": "Name",
      "phase": "Phase",
      "commissionRate": "Commission (%)",
      "documentLanguage": "Document language",
      "createdAt": "Created on",
      "actions": "Actions"
    }
  },
  "create": {
    "title": "Create Edition",
    "name": {
      "label": "Edition name",
      "required": "Edition name is required.",
      "maxLength": "Edition name must not exceed 255 characters."
    },
    "submit": "Create",
    "cancel": "Cancel",
    "error": {
      "alreadyActive": "An edition is already active. Close it before creating a new one.",
      "save": "Failed to create edition."
    },
    "success": "Edition created."
  },
  "phase": {
    "PREPARATION": "Preparation",
    "DEPOSIT": "Deposit",
    "SALE": "Sale",
    "POST_SALE": "Post-sale",
    "CLOSED": "Closed"
  },
  "actions": {
    "editCommissionRate": "Edit commission",
    "editDocumentLanguage": "Edit language",
    "delete": "Delete",
    "error": {
      "delete": "Failed to delete edition.",
      "load": "Failed to load editions.",
      "updateCommissionRate": "Failed to update commission rate.",
      "updateDocumentLanguage": "Failed to update document language."
    },
    "success": {
      "delete": "Edition deleted.",
      "updateCommissionRate": "Commission rate updated.",
      "updateDocumentLanguage": "Document language updated."
    }
  },
  "updateCommissionRateDialog": {
    "title": "Update commission rate",
    "label": "Commission rate (%)",
    "confirm": "Save"
  },
  "updateDocumentLanguageDialog": {
    "title": "Update document language",
    "label": "Document language",
    "confirm": "Save"
  },
  "deleteDialog": {
    "title": "Delete edition?",
    "description": "This edition will be permanently deleted. This action cannot be undone."
  }
}
```

**`fr.json`** — same keys in French, **vouvoiement obligatoire** (see CLAUDE.md and Story 1.6 note). Add `editions` inside the existing `nav.admin` object — do NOT create a new top-level `nav` key:
```json
"nav": {
  "admin": {
    "editions": "Éditions"
  }
},
"edition": {
  "list": {
    "title": "Éditions",
    "createButton": "Créer une édition",
    "empty": "Aucune édition créée.",
    "emptyAction": "Créer la première édition",
    "columns": {
      "name": "Nom",
      "phase": "Phase",
      "commissionRate": "Commission (%)",
      "documentLanguage": "Langue des documents",
      "createdAt": "Créée le",
      "actions": "Actions"
    }
  },
  "create": {
    "title": "Créer une édition",
    "name": {
      "label": "Nom de l'édition",
      "required": "Le nom de l'édition est obligatoire.",
      "maxLength": "Le nom de l'édition ne doit pas dépasser 255 caractères."
    },
    "submit": "Créer",
    "cancel": "Annuler",
    "error": {
      "alreadyActive": "Une édition est déjà active. Clôturez-la avant d'en créer une nouvelle.",
      "save": "Impossible de créer l'édition."
    },
    "success": "Édition créée."
  },
  "phase": {
    "PREPARATION": "Préparation",
    "DEPOSIT": "Dépôt",
    "SALE": "Vente",
    "POST_SALE": "Post-vente",
    "CLOSED": "Clôturée"
  },
  "actions": {
    "editCommissionRate": "Modifier la commission",
    "editDocumentLanguage": "Modifier la langue",
    "delete": "Supprimer",
    "error": {
      "delete": "Impossible de supprimer l'édition.",
      "load": "Impossible de charger les éditions.",
      "updateCommissionRate": "Impossible de modifier le taux de commission.",
      "updateDocumentLanguage": "Impossible de modifier la langue des documents."
    },
    "success": {
      "delete": "Édition supprimée.",
      "updateCommissionRate": "Taux de commission mis à jour.",
      "updateDocumentLanguage": "Langue des documents mise à jour."
    }
  },
  "updateCommissionRateDialog": {
    "title": "Modifier le taux de commission",
    "label": "Taux de commission (%)",
    "confirm": "Enregistrer"
  },
  "updateDocumentLanguageDialog": {
    "title": "Modifier la langue des documents",
    "label": "Langue des documents",
    "confirm": "Enregistrer"
  },
  "deleteDialog": {
    "title": "Supprimer l'édition ?",
    "description": "Cette édition sera définitivement supprimée. Cette action est irréversible."
  }
}
```

### Package Structure (CRITICAL — Read Before Starting)

**Backend:** Use **SINGULAR** sub-packages under `org.pluribourse.edition`:
```
org.pluribourse.edition.entity.PhaseType       ← NOT entities
org.pluribourse.edition.entity.Edition         ← NOT entities
org.pluribourse.edition.dto.EditionDto         ← NOT dtos
org.pluribourse.edition.dto.CreateEditionDto
org.pluribourse.edition.dto.UpdateCommissionRateDto
org.pluribourse.edition.dto.UpdateDocumentLanguageDto
org.pluribourse.edition.mapper.EditionMapper   ← NOT mappers
org.pluribourse.edition.repository.EditionRepository ← NOT repositories
org.pluribourse.edition.service.EditionService ← NOT services
org.pluribourse.edition.controller.EditionController ← NOT controllers
```

This matches the `org.pluribourse.shared.instanceconfig.*` pattern (singular sub-packages) and the architecture doc. The `org.pluribourse.user` package uses plural (`controllers`, `services`, etc.) — that is a pre-existing inconsistency predating the architecture decision; new features use singular.

**Frontend:** Files go in `src/app/features/admin/editions/` (NOT `src/app/components/edition/` — the architecture doc lists `components/edition/` but the actual codebase uses `features/` for all feature components).

### Existing Code NOT to Break

- `GlobalInstanceConfigService.getDefaultCommissionRate()` and `getDefaultDocumentLanguage()` — call these, do NOT modify them
- `AppLayoutComponent` — only add nav link, do not change existing links or admin check
- `admin.routes.ts` — only add edition routes, do not touch existing routes
- `SecurityConfig.java` — do NOT touch; `/api/admin/**` is already locked to ADMIN
- `GlobalExceptionHandler.java` — do NOT touch; keep 400 for `@Valid` violations
- `db.changelog-master.xml` — only append the 007 include, preserve existing includes

### Project Structure Notes

**New backend files:**
- `pluribourse-backend/src/main/resources/db/changelog/007-editions.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/PhaseType.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/CreateEditionDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/UpdateCommissionRateDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/UpdateDocumentLanguageDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionMapper.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionController.java`
- `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionManagementIT.java`

**Modified backend files:**
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`

**New frontend files:**
- `pluribourse-frontend/src/app/models/edition.model.ts`
- `pluribourse-frontend/src/app/services/edition.service.ts`
- `pluribourse-frontend/src/app/services/edition.service.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.scss`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.spec.ts`

**Modified frontend files:**
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`

### References

- [Source: architecture.md — Patrons de Structure] — package structure, DTO/mapper patterns
- [Source: architecture.md — Patrons de Communication] — REST API design (`/api/admin/editions`)
- [Source: architecture.md — Backend Structure] — `edition/` package layout (singular sub-packages)
- [Source: shared/instanceconfig/*] — authoritative model for a new backend feature (entity record DTO mapper controller service repository)
- [Source: GlobalInstanceConfigService.java:35-42] — `getDefaultCommissionRate()` and `getDefaultDocumentLanguage()` ready for Story 2.1
- [Source: shared/exception/BusinessException.java] — `BusinessException(HttpStatus, errorCode, message)` for business rule violations
- [Source: shared/exception/GlobalExceptionHandler.java] — handles BusinessException → RFC 7807; `@Valid` violations → 400
- [Source: 004-instance-config.xml] — Liquibase migration format reference
- [Source: 1-10-ameliorations-ux-mots-de-passe.md — T2] — CDK Dialog pattern: `DIALOG_DATA`, `DialogRef<T>`, `A11yModule`, `cdkFocusInitial`, no `cdkTrapFocus` on container
- [Source: user-list.component.ts:82] — `takeUntilDestroyed(this.destroyRef)` on dialog `.closed.subscribe()`
- [Source: admin-settings.component.ts] — Angular component pattern (signal, firstValueFrom, inject)
- [Source: admin.routes.ts] — route lazy-loading pattern
- [Source: app-layout.component.ts:7] — Use `<span class="material-symbols-outlined">` NOT MatIcon (MatIconRegistry not configured)
- [Source: GlobalInstanceConfigIT.java] — IT test pattern (sessions, @TestMethodOrder, direct repository access)
- [Source: epics.md, Epic 2, Story 2.1] — full acceptance criteria and FR references

### T11.2 — edition.service.spec.ts Pattern

Follow `GlobalInstanceConfigService.spec.ts` exactly (`provideHttpClient()` + `HttpTestingController`).

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { EditionService } from './edition.service';
import { EditionDto, CreateEditionDto, UpdateCommissionRateDto } from '../models/edition.model';

const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01'
};

describe('EditionService', () => {
  let service: EditionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EditionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll() sends GET /api/admin/editions', async () => {
    const p = firstValueFrom(service.getAll());
    http.expectOne('/api/admin/editions').flush([MOCK_EDITION]);
    expect(await p).toEqual([MOCK_EDITION]);
  });

  it('create() sends POST /api/admin/editions with name', async () => {
    const dto: CreateEditionDto = { name: 'Bourse 2026' };
    const p = firstValueFrom(service.create(dto));
    const req = http.expectOne('/api/admin/editions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_EDITION);
    expect(await p).toEqual(MOCK_EDITION);
  });

  it('updateCommissionRate() sends PATCH /api/admin/editions/1/commission-rate', async () => {
    const dto: UpdateCommissionRateDto = { commissionRate: 15 };
    const p = firstValueFrom(service.updateCommissionRate(1, dto));
    const req = http.expectOne('/api/admin/editions/1/commission-rate');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...MOCK_EDITION, commissionRate: 15 });
    expect((await p).commissionRate).toBe(15);
  });

  it('delete() sends DELETE /api/admin/editions/1', async () => {
    const p = firstValueFrom(service.delete(1));
    const req = http.expectOne('/api/admin/editions/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await p;
  });
});
```

### T12.3 — edition-list.component.spec.ts Pattern

Follow `UserListComponent.spec.ts` exactly — mock `EditionService`, `ToastService`, `Dialog`, `ConfirmDialogService` via `useValue`.

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { EditionListComponent } from './edition-list.component';
import { EditionService } from '../../../services/edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../models/edition.model';

const MOCK_EDITIONS: EditionDto[] = [
  { id: 1, name: 'Bourse 2026', phase: 'PREPARATION', commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01' }
];

describe('EditionListComponent', () => {
  let fixture: ComponentFixture<EditionListComponent>;
  let component: EditionListComponent;

  const editionServiceMock = {
    getAll: vi.fn().mockReturnValue(of(MOCK_EDITIONS)),
    updateCommissionRate: vi.fn().mockReturnValue(of(MOCK_EDITIONS[0])),
    updateDocumentLanguage: vi.fn().mockReturnValue(of(MOCK_EDITIONS[0])),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const dialogMock = { open: vi.fn().mockReturnValue({ closed: of(undefined) }) };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getAll.mockReturnValue(of(MOCK_EDITIONS));

    await TestBed.configureTestingModule({
      imports: [EditionListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: Dialog, useValue: dialogMock },
        { provide: ConfirmDialogService, useValue: confirmMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads editions on init', () => {
    expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
    expect(component.editions().length).toBe(1);
    expect(component.error()).toBeNull();
  });

  it('sets error key when load fails', async () => {
    editionServiceMock.getAll.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('edition.actions.error.load');
  });

  it('isEditable returns true only for PREPARATION phase', () => {
    expect(component.isEditable(MOCK_EDITIONS[0])).toBe(true);
    expect(component.isEditable({ ...MOCK_EDITIONS[0], phase: 'DEPOSIT' })).toBe(false);
  });

  it('openEditCommissionRate opens dialog', () => {
    component.openEditCommissionRate(MOCK_EDITIONS[0]);
    expect(dialogMock.open).toHaveBeenCalledOnce();
  });

  it('openEditDocumentLanguage opens dialog', () => {
    component.openEditDocumentLanguage(MOCK_EDITIONS[0]);
    expect(dialogMock.open).toHaveBeenCalledOnce();
  });
});
```

### T13.3 — edition-form.component.spec.ts Pattern

```typescript
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { EditionFormComponent } from './edition-form.component';
import { EditionService } from '../../../services/edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

describe('EditionFormComponent', () => {
  let fixture: ComponentFixture<EditionFormComponent>;
  let component: EditionFormComponent;

  const editionServiceMock = { create: vi.fn().mockReturnValue(of({})) };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.create.mockReturnValue(of({}));
    await TestBed.configureTestingModule({
      imports: [EditionFormComponent],
      providers: [
        provideRouter([{ path: 'admin/editions', component: EditionFormComponent }]),
        provideTranslateService({ lang: 'en' }),
        provideAnimationsAsync(),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(EditionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('form is invalid when name is empty', () => {
    expect(component.form.invalid).toBe(true);
  });

  it('calls editionService.create with form value on valid submit', async () => {
    component.form.controls.name.setValue('Bourse 2026');
    await component.onSubmit();
    expect(editionServiceMock.create).toHaveBeenCalledWith({ name: 'Bourse 2026' });
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('sets createError key on 422 response (active edition already exists)', async () => {
    editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422 })));
    component.form.controls.name.setValue('Bourse 2027');
    await component.onSubmit();
    expect(component.createError()).toBe('edition.create.error.alreadyActive');
    expect(toastMock.showError).not.toHaveBeenCalled();
  });

  it('shows error toast on non-422 API error', async () => {
    editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.form.controls.name.setValue('Bourse 2027');
    await component.onSubmit();
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(component.createError()).toBeNull();
  });

  it('isSaving is false after submit completes', async () => {
    component.form.controls.name.setValue('Bourse 2026');
    await component.onSubmit();
    expect(component.isSaving()).toBe(false);
  });
});
```

### T14.3 — update-commission-rate-dialog.component.spec.ts Pattern

Follow `ResetPasswordDialogComponent.spec.ts` exactly — provide `DialogRef` and `DIALOG_DATA` via `useValue`.

```typescript
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import { UpdateCommissionRateDialogComponent, UpdateCommissionRateDialogData } from './update-commission-rate-dialog.component';

const testData: UpdateCommissionRateDialogData = { editionId: 1, currentRate: 20 };

describe('UpdateCommissionRateDialogComponent', () => {
  const mockClose = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [UpdateCommissionRateDialogComponent],
      providers: [
        provideAnimationsAsync(),
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: { close: mockClose } },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('initializes form with currentRate', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue().commissionRate).toBe(20);
  });

  it('confirm() with valid rate closes dialog with the new rate', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.commissionRate.setValue(15);
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith(15);
  });

  it('confirm() with null rate does NOT close dialog', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.commissionRate.setValue(null as unknown as number);
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });

  it('cancel() closes dialog with undefined', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });
});
```

### T15.3 — update-document-language-dialog.component.spec.ts Pattern

```typescript
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import { UpdateDocumentLanguageDialogComponent, UpdateDocumentLanguageDialogData } from './update-document-language-dialog.component';

const testData: UpdateDocumentLanguageDialogData = { editionId: 1, currentLanguage: 'EN' };

describe('UpdateDocumentLanguageDialogComponent', () => {
  const mockClose = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [UpdateDocumentLanguageDialogComponent],
      providers: [
        provideAnimationsAsync(),
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: { close: mockClose } },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('initializes form with currentLanguage', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue().documentLanguage).toBe('EN');
  });

  it('confirm() closes dialog with the selected language', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.documentLanguage.setValue('FR');
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith('FR');
  });

  it('cancel() closes dialog with undefined', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });
});
```

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Jackson LocalDate issue: `JacksonConfig.objectMapper()` was missing `findAndAddModules()` — added it so `JavaTimeModule` is auto-registered. `EditionDto.createdAt` (LocalDate) now serializes/deserializes correctly.
- Pre-existing failures (not regressions): `AdminCreateRunnerTest` stubs `findByRole` but the code calls `existsByRole`. These 3 failures exist before Story 2.1 and are out of scope.

### Completion Notes List

- Backend: Liquibase migration 007 creates `editions` table. Master changelog updated.
- Backend: `PhaseType` enum, `Edition` entity, 4 DTOs, `EditionMapper`, `EditionRepository`, `EditionService`, `EditionController` created in `org.pluribourse.edition.*` (singular sub-packages).
- Backend: `EditionManagementIT` — 16 integration tests, all pass. Covers auth, CRUD, phase-based commission lock, CLOSED-edition bypass.
- Backend fix: `JacksonConfig` — added `findAndAddModules()` to register `JavaTimeModule` for `LocalDate` serialization.
- Frontend: TypeScript model, Angular `EditionService` with 4 HTTP methods, `EditionListComponent`, `EditionFormComponent`, `UpdateCommissionRateDialogComponent`, `UpdateDocumentLanguageDialogComponent`.
- Frontend: Routes added to `admin.routes.ts`. Nav link added to `app-layout.component.html`. i18n keys added to both `en.json` and `fr.json`.
- Frontend: 139 tests pass, zero regressions (26 test files).

### File List

**New backend files:**
- pluribourse-backend/src/main/resources/db/changelog/007-editions.xml
- pluribourse-backend/src/main/java/org/pluribourse/edition/entity/PhaseType.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/dto/CreateEditionDto.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/dto/UpdateCommissionRateDto.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/dto/UpdateDocumentLanguageDto.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionMapper.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionRepository.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java
- pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionController.java
- pluribourse-backend/src/test/java/org/pluribourse/edition/EditionManagementIT.java

**Modified backend files:**
- pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml
- pluribourse-backend/src/main/java/org/pluribourse/shared/config/JacksonConfig.java

**New frontend files:**
- pluribourse-frontend/src/app/models/edition.model.ts
- pluribourse-frontend/src/app/services/edition.service.ts
- pluribourse-frontend/src/app/services/edition.service.spec.ts
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.scss
- pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts
- pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts
- pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html
- pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts
- pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.ts
- pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.html
- pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.scss
- pluribourse-frontend/src/app/features/admin/editions/update-commission-rate-dialog/update-commission-rate-dialog.component.spec.ts
- pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.ts
- pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.html
- pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.scss
- pluribourse-frontend/src/app/features/admin/editions/update-document-language-dialog/update-document-language-dialog.component.spec.ts

**Modified frontend files:**
- pluribourse-frontend/src/app/features/admin/admin.routes.ts
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts
- pluribourse-frontend/public/i18n/en.json
- pluribourse-frontend/public/i18n/fr.json

## Change Log

- 2026-06-28: Story 2.1 implemented — Edition CRUD & commission rate configuration (backend + frontend). Fixed JacksonConfig to register JavaTimeModule via findAndAddModules().
