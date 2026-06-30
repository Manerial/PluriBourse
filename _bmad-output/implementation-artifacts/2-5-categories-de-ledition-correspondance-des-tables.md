---
status: done
baseline_commit: 6a5bb7b0c9e85990a2cd843464650ab03c374122
---

# Story 2.5: Edition Categories & Table Mapping

Status: done

## Story

As an administrator,
I want to configure article categories and their table assignments per edition,
so that articles are automatically directed to the right tables during the deposit phase.

## Acceptance Criteria

1. **Given** the admin opens the categories page for a new edition `/admin/editions/:id/categories`, **When** the page loads, **Then** the category list is empty and editable, **And** a "Copy from a closed edition" option is available with a dropdown listing only closed editions.

2. **Given** the admin selects "Copy from a closed edition" and confirms, **When** the copy completes, **Then** all categories and table assignments from the selected edition are applied to the new edition (FR-080).

3. **Given** the admin adds a category (e.g. "Jouets") assigned to tables 1, 2, 3, **When** saved, **Then** articles of that category will be auto-assigned to tables 1–3.

4. **Given** the admin assigns table 5 to two distinct categories (e.g. "Livres" and "BD"), **When** saved, **Then** table 5 appears in the mapping of both categories without a validation error.

5. **Given** the admin tries to save with a category that has no table assigned, **When** they click "Save", **Then** the save is blocked and an inline error appears on the relevant row: "Assign at least one table to this category" (FR-018).

6. **Given** the edition is in Preparation phase, **When** the admin modifies categories and table assignments, **Then** changes are saved immediately.

7. **Given** the edition has entered the Deposit phase, **When** the admin opens the categories page, **Then** the page is read-only with a banner indicating "Categories locked".

## Tasks / Subtasks

- [x] **T1 — Backend: Liquibase migration 011** (AC: 3, 4, 5, 6, 7)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/011-edition-categories.xml` with `edition_categories` table (id, edition_id FK, name, display_order) and `category_table_assignments` table (category_id FK, table_number, PK composite)
  - [x] T1.2 — Add `<include file="db/changelog/011-edition-categories.xml"/>` to `db.changelog-master.xml` after 010

- [x] **T2 — Backend: EditionCategory entity** (AC: 3, 4)
  - [x] T2.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/EditionCategory.java` with `@ElementCollection` for `tableNumbers` (see Dev Notes for full entity design)

- [x] **T3 — Backend: EditionCategoryRepository** (AC: 3, 6, 7)
  - [x] T3.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionCategoryRepository.java` (see Dev Notes)

- [x] **T4 — Backend: EditionCategoryDto** (AC: 3, 4, 5)
  - [x] T4.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionCategoryDto.java` record (id nullable, name, sorted list of tableNumbers)

- [x] **T5 — Backend: EditionCategoryMapper** (AC: 3)
  - [x] T5.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionCategoryMapper.java` MapStruct mapper

- [x] **T6 — Backend: EditionCategoryService** (AC: 2, 3, 4, 5, 6, 7)
  - [x] T6.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionCategoryService.java` with `getCategories`, `saveCategories`, `copyFromEdition` methods (see Dev Notes)

- [x] **T7 — Backend: EditionCategoryController** (AC: 1, 2, 3, 4, 5, 6, 7)
  - [x] T7.1 — Create `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionCategoryController.java` with 3 endpoints (see Dev Notes)

- [x] **T8 — Backend: Integration test** (AC: 1–7)
  - [x] T8.1 — Create `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionCategoryIT.java` extending `IntegrationTest` with full E2E scenario (see Dev Notes)

- [x] **T9 — Frontend: category.model.ts** (AC: 3)
  - [x] T9.1 — Create `pluribourse-frontend/src/app/models/category.model.ts` with `EditionCategoryDto` interface

- [x] **T10 — Frontend: category.service.ts** (AC: 1, 2, 3, 4, 5, 6, 7)
  - [x] T10.1 — Create `pluribourse-frontend/src/app/services/category.service.ts` with `getCategories`, `saveCategories`, `copyFromEdition` methods

- [x] **T11 — Frontend: edition-categories component** (AC: 1–7)
  - [x] T11.1 — Create `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts` (see Dev Notes for state/logic)
  - [x] T11.2 — Create `edition-categories.component.html` (see Dev Notes for UX layout)

- [x] **T12 — Frontend: routing** (AC: 1)
  - [x] T12.1 — Add route `editions/:id/categories` to `admin.routes.ts` loading `EditionCategoriesComponent`

- [x] **T13 — Frontend: edition-list navigation link** (AC: 1)
  - [x] T13.1 — Add "Manage categories" action button in `edition-list.component.html` pointing to `/admin/editions/:id/categories`
  - [x] T13.2 — Add i18n key `edition.actions.manageCategories` in `en.json` and `fr.json`

- [x] **T14 — Frontend: i18n keys** (AC: 1–7)
  - [x] T14.1 — Add all `category.*` keys to `en.json` (see Dev Notes for complete key list)
  - [x] T14.2 — Add French equivalents to `fr.json` (see Dev Notes)

- [x] **T15 — Frontend: tests** (regression + new)
  - [x] T15.1 — Create `edition-categories.component.spec.ts` testing: component loads categories on init; `isReadOnly` is `true` when phase is DEPOSIT; `addCategory` pushes a new row; `removeCategory` removes the row at the given index; copy section hidden when no closed editions
  - [x] T15.2 — Create `category.service.spec.ts` verifying the 3 HTTP calls: `getCategories` → GET, `saveCategories` → PUT, `copyFromEdition` → POST
  - [x] T15.3 — Run `npm test` — all tests must pass (172 tests, 0 failures)

## Dev Notes

### Architecture Constraints

- **Package placement**: All backend category code lives in `org.pluribourse.edition.*` for Story 2.5. When Story 3.x (item deposit) is implemented, `EditionCategoryRepository` is imported from `edition.repository`.
- **Phase guard**: Categories editable in `PREPARATION` only. Calls to `saveCategories` and `copyFromEdition` on editions not in `PREPARATION` throw `BusinessException(422, "categories-locked", ...)`.
- **Bulk save pattern**: `PUT /api/admin/editions/{id}/categories` receives the complete list and replaces atomically (delete existing rows, insert new ones in one transaction). No partial-update endpoint.
- **Table numbers**: Free integers (1..N), no upper bound, no pre-created "table" entity. A table number is just an integer stored in `category_table_assignments`.
- **At-least-one-table validation**: Done server-side. If any category in the payload has an empty `tableNumbers` list, throw `BusinessException(422, "category-missing-table", "Category '...' must have at least one table assigned.")`.
- **No `var`**: CLAUDE.md rule — always declare explicit variable types in Java.

---

### T1 — Migration 011

File: `pluribourse-backend/src/main/resources/db/changelog/011-edition-categories.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="011-edition-categories" author="pluribourse">
        <createTable tableName="edition_categories">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="edition_id" type="BIGINT">
                <constraints nullable="false"
                             foreignKeyName="fk_edition_categories_edition"
                             references="editions(id)"
                             deleteCascade="true"/>
            </column>
            <column name="name" type="VARCHAR(100)">
                <constraints nullable="false"/>
            </column>
            <column name="display_order" type="INT" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <addUniqueConstraint tableName="edition_categories"
                             columnNames="edition_id, name"
                             constraintName="uq_edition_categories_edition_name"/>

        <createTable tableName="category_table_assignments">
            <column name="category_id" type="BIGINT">
                <constraints nullable="false"
                             foreignKeyName="fk_cat_table_category"
                             references="edition_categories(id)"
                             deleteCascade="true"/>
            </column>
            <column name="table_number" type="INT">
                <constraints nullable="false"/>
            </column>
        </createTable>

        <addPrimaryKey tableName="category_table_assignments"
                       columnNames="category_id, table_number"
                       constraintName="pk_category_table_assignments"/>
    </changeSet>

</databaseChangeLog>
```

---

### T2 — EditionCategory Entity

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/EditionCategory.java`

```java
package org.pluribourse.edition.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "edition_categories",
       uniqueConstraints = @UniqueConstraint(columnNames = {"edition_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class EditionCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @ElementCollection
    @CollectionTable(
            name = "category_table_assignments",
            joinColumns = @JoinColumn(name = "category_id")
    )
    @Column(name = "table_number")
    private Set<Integer> tableNumbers = new HashSet<>();
}
```

Key points:
- `@ManyToOne(fetch = LAZY)` — edition loaded lazily; we only need `edition.id` in the service
- `@ElementCollection` on `Set<Integer>` maps to `category_table_assignments` — no separate entity needed
- Cascade delete from `editions` handled at DB level (Liquibase FK `deleteCascade="true"`)

---

### T3 — EditionCategoryRepository

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionCategoryRepository.java`

```java
package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.EditionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EditionCategoryRepository extends JpaRepository<EditionCategory, Long> {

    @Query("SELECT c FROM EditionCategory c LEFT JOIN FETCH c.tableNumbers WHERE c.edition.id = :editionId ORDER BY c.displayOrder ASC, c.name ASC")
    List<EditionCategory> findAllByEditionIdWithTables(@Param("editionId") Long editionId);

    void deleteAllByEditionId(Long editionId);
}
```

Why `LEFT JOIN FETCH c.tableNumbers`: avoids N+1 queries when loading categories with their table sets.

---

### T4 — EditionCategoryDto

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionCategoryDto.java`

```java
package org.pluribourse.edition.dto;

import java.util.List;

public record EditionCategoryDto(
        Long id,
        String name,
        List<Integer> tableNumbers
) {}
```

- `id` is nullable on input (new categories sent from frontend have `null` id)
- `tableNumbers` is a sorted `List<Integer>` in the response (server sorts ascending); frontend sends any order
- No Bean Validation on fields — service performs business validation (at-least-one-table check)

---

### T5 — EditionCategoryMapper

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionCategoryMapper.java`

```java
package org.pluribourse.edition.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.entity.EditionCategory;

import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring")
public interface EditionCategoryMapper {

    @Mapping(target = "tableNumbers", expression = "java(sortedTableNumbers(category))")
    EditionCategoryDto toDto(EditionCategory category);

    default List<Integer> sortedTableNumbers(EditionCategory category) {
        List<Integer> sorted = new ArrayList<>(category.getTableNumbers());
        sorted.sort(Integer::compareTo);
        return sorted;
    }
}
```

---

### T6 — EditionCategoryService

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionCategoryService.java`

```java
package org.pluribourse.edition.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.EditionCategory;
import org.pluribourse.edition.entity.PhaseType;
import org.pluribourse.edition.mapper.EditionCategoryMapper;
import org.pluribourse.edition.repository.EditionCategoryRepository;
import org.pluribourse.edition.repository.EditionRepository;
import org.pluribourse.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EditionCategoryService {

    private final EditionCategoryRepository categoryRepository;
    private final EditionRepository editionRepository;
    private final EditionCategoryMapper mapper;

    @Transactional(readOnly = true)
    public List<EditionCategoryDto> getCategories(Long editionId) {
        requireEditionExists(editionId);
        return categoryRepository.findAllByEditionIdWithTables(editionId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public List<EditionCategoryDto> saveCategories(Long editionId, List<EditionCategoryDto> dtos) {
        Edition edition = requirePreparationPhase(editionId);
        validateAtLeastOneTable(dtos);
        categoryRepository.deleteAllByEditionId(editionId);
        List<EditionCategory> saved = persistCategories(edition, dtos);
        return saved.stream().map(mapper::toDto).toList();
    }

    @Transactional
    public List<EditionCategoryDto> copyFromEdition(Long targetEditionId, Long sourceEditionId) {
        Edition target = requirePreparationPhase(targetEditionId);
        Edition source = editionRepository.findById(sourceEditionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Source edition not found: " + sourceEditionId));
        if (source.getPhase() != PhaseType.CLOSED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "source-edition-not-closed",
                    "Can only copy categories from a CLOSED edition.");
        }
        List<EditionCategoryDto> sourceDtos = categoryRepository.findAllByEditionIdWithTables(sourceEditionId)
                .stream()
                .map(mapper::toDto)
                .toList();
        categoryRepository.deleteAllByEditionId(targetEditionId);
        List<EditionCategory> saved = persistCategories(target, sourceDtos);
        return saved.stream().map(mapper::toDto).toList();
    }

    private List<EditionCategory> persistCategories(Edition edition, List<EditionCategoryDto> dtos) {
        List<EditionCategory> categories = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            EditionCategoryDto dto = dtos.get(i);
            EditionCategory category = new EditionCategory();
            category.setEdition(edition);
            category.setName(dto.name());
            category.setDisplayOrder(i);
            category.setTableNumbers(new HashSet<>(dto.tableNumbers()));
            categories.add(categoryRepository.save(category));
        }
        return categories;
    }

    private Edition requirePreparationPhase(Long editionId) {
        Edition edition = requireEditionExists(editionId);
        if (edition.getPhase() != PhaseType.PREPARATION) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "categories-locked",
                    "Categories and table assignments are locked once the Deposit phase has started.");
        }
        return edition;
    }

    private Edition requireEditionExists(Long editionId) {
        return editionRepository.findById(editionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "edition-not-found",
                        "Edition not found: " + editionId));
    }

    private void validateAtLeastOneTable(List<EditionCategoryDto> dtos) {
        for (EditionCategoryDto dto : dtos) {
            if (dto.name() == null || dto.name().isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "category-name-required",
                        "Category name must not be blank.");
            }
            if (dto.tableNumbers() == null || dto.tableNumbers().isEmpty()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "category-missing-table",
                        "Category '" + dto.name() + "' must have at least one table assigned.");
            }
        }
    }
}
```

---

### T7 — EditionCategoryController

File: `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionCategoryController.java`

```java
package org.pluribourse.edition.controller;

import lombok.RequiredArgsConstructor;
import org.pluribourse.edition.dto.EditionCategoryDto;
import org.pluribourse.edition.service.EditionCategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/editions/{editionId}/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EditionCategoryController {

    private final EditionCategoryService service;

    @GetMapping
    public ResponseEntity<List<EditionCategoryDto>> getCategories(@PathVariable Long editionId) {
        return ResponseEntity.ok(service.getCategories(editionId));
    }

    @PutMapping
    public ResponseEntity<List<EditionCategoryDto>> saveCategories(
            @PathVariable Long editionId,
            @RequestBody List<EditionCategoryDto> dtos) {
        return ResponseEntity.ok(service.saveCategories(editionId, dtos));
    }

    @PostMapping("/copy-from/{sourceEditionId}")
    public ResponseEntity<List<EditionCategoryDto>> copyFromEdition(
            @PathVariable Long editionId,
            @PathVariable Long sourceEditionId) {
        return ResponseEntity.ok(service.copyFromEdition(editionId, sourceEditionId));
    }
}
```

Security: `@PreAuthorize("hasRole('ADMIN')")` is already applied globally via SecurityConfig, but explicit annotation documents intent. If SecurityConfig already covers `/api/admin/**` for ADMIN role, you may omit the annotation and keep only the class-level `@RequestMapping`. Follow the same pattern as `EditionController`.

---

### T8 — EditionCategoryIT test scenario

File: `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionCategoryIT.java`

Scenario storyboard (ordered):
1. `@Order(1)` — Admin logs in, creates a PREPARATION edition → stores `editionId`
2. `@Order(2)` — GET `/api/admin/editions/{id}/categories` → 200, empty array
3. `@Order(3)` — PUT with one category "Jouets" tables [1,2] → 200, returned list has 1 category with sorted tableNumbers
4. `@Order(4)` — GET again → 1 category persisted
5. `@Order(5)` — PUT with two categories "Jouets" [1,2] and "Livres" [2,3] (table 2 shared) → 200, no error
6. `@Order(6)` — PUT with category "BD" with empty tableNumbers → 422, error type `category-missing-table`
7. `@Order(7)` — Advance edition to DEPOSIT phase (via repository directly for speed) → then PUT → 422, error type `categories-locked`
8. `@Order(8)` — GET in DEPOSIT phase → 200 (read is always allowed)
9. `@Order(9)` — Roll back to PREPARATION (via repository) → PUT succeeds again
10. `@Order(10)` — Create a second edition (source CLOSED via repository), call POST `/copy-from/{sourceId}` → 200, categories copied
11. `@Order(11)` — Volunteer GET `/api/admin/editions/{id}/categories` → 403

Base class: `IntegrationTest` (same as `PhaseTransitionIT`). Use `objectMapper` for JSON. Reuse session login pattern from existing ITs.

---

### T9 — Frontend Model

File: `pluribourse-frontend/src/app/models/category.model.ts`

```typescript
export interface EditionCategoryDto {
  id: number | null;
  name: string;
  tableNumbers: number[];
}
```

---

### T10 — CategoryService

File: `pluribourse-frontend/src/app/services/category.service.ts`

```typescript
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EditionCategoryDto } from '../models/category.model';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);

  private base(editionId: number): string {
    return `/api/admin/editions/${editionId}/categories`;
  }

  getCategories(editionId: number): Observable<EditionCategoryDto[]> {
    return this.http.get<EditionCategoryDto[]>(this.base(editionId));
  }

  saveCategories(editionId: number, categories: EditionCategoryDto[]): Observable<EditionCategoryDto[]> {
    return this.http.put<EditionCategoryDto[]>(this.base(editionId), categories);
  }

  copyFromEdition(editionId: number, sourceEditionId: number): Observable<EditionCategoryDto[]> {
    return this.http.post<EditionCategoryDto[]>(
      `${this.base(editionId)}/copy-from/${sourceEditionId}`,
      {}
    );
  }
}
```

---

### T11 — EditionCategoriesComponent

#### Component logic (`.ts`)

File: `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts`

Key signals:
```typescript
readonly edition = signal<EditionDto | null>(null);
readonly categories = signal<EditableCategoryRow[]>([]);  // local edit state
readonly closedEditions = signal<EditionDto[]>([]);
readonly isLoading = signal(false);
readonly isSaving = signal(false);
readonly isReadOnly = computed(() => this.edition()?.phase !== 'PREPARATION');
readonly error = signal<string | null>(null);
readonly selectedSourceEditionId = signal<number | null>(null);
```

Internal type for editable rows:
```typescript
interface EditableCategoryRow {
  id: number | null;
  name: string;
  tableInput: string;   // raw text input e.g. "1, 2, 3"
  tableNumbers: number[];
  tableError: string | null;
}
```

Table numbers UX: a single text input per category row, comma-separated integers (e.g. "1, 2, 3"). Parse on save. Show inline error if empty after parsing.

**`ngOnInit()`**: load edition (from `EditionService.getById(id)`), load categories (from `CategoryService.getCategories(id)`), load closed editions (filter `EditionService.getAll()` where `phase === 'CLOSED'`).

**`addCategory()`**: pushes `{ id: null, name: '', tableInput: '', tableNumbers: [], tableError: null }` to `categories` signal.

**`removeCategory(index)`**: splices the row out.

**`onSave()`**: parse each row's `tableInput` → `tableNumbers`, validate non-empty (show `tableError` inline), call `CategoryService.saveCategories()`, update `categories` signal on success, show toast.

**`onCopy()`**: call `CategoryService.copyFromEdition(editionId, selectedSourceEditionId())`, update categories on success, show toast.

#### Component HTML (`.html`)

UX structure (UX-DR16):
```
[Back to edition list link]
[Page title: "Categories — {edition.name}"]

[if isReadOnly]
  [NotificationInlineComponent: "category.locked.banner"]

[if closedEditions().length > 0 && !isReadOnly]
  [Copy section]
    [mat-select bound to selectedSourceEditionId — lists closed editions by name]
    [Button "Copy from this edition" — triggers onCopy()]

[Categories table]
  [Skeleton rows when isLoading]
  [For each category row:]
    [mat-form-field: name input — disabled if isReadOnly]
    [mat-form-field: table numbers text input (comma-separated) — disabled if isReadOnly]
    [Inline error if tableError]
    [Remove button — hidden if isReadOnly]
  [EmptyState if categories empty and isReadOnly]
  [Button "Add category" — hidden if isReadOnly]

[if !isReadOnly]
  [Save button with isSaving spinner]
  [Back/Cancel link]
```

Import: `MatFormFieldModule`, `MatInputModule`, `MatSelectModule`, `MatButtonModule`, `MatIconModule`, `ReactiveFormsModule`, `TranslatePipe`, `NotificationInlineComponent`, `SkeletonRowComponent`, `EmptyStateComponent`, `RouterLink`, `FormsModule` (for ngModel on signals or use regular properties).

**Note on ngModel vs Signals**: For the editable row array, use a local mutable array (not pure signal) or use `signal` with careful mutation. The recommended pattern for this component is a plain property `categories: EditableCategoryRow[] = []` updated via `signal` setter — or just use a plain TS array managed imperatively, as this is simpler for array mutation.

---

### T12 — Routing

In `admin.routes.ts`, add after the `editions/:id/phase` route:

```typescript
{
  path: 'editions/:id/categories',
  loadComponent: () =>
    import('./editions/edition-categories/edition-categories.component')
      .then((m) => m.EditionCategoriesComponent),
},
```

---

### T13 — Edition List Navigation Link

In `edition-list.component.html`, add a "Manage categories" button in the actions column alongside "Manage phase" and "Edit":

```html
<button mat-button [routerLink]="['/admin/editions', edition.id, 'categories']">
  {{ 'edition.actions.manageCategories' | translate }}
</button>
```

---

### T14 — i18n Keys

#### `en.json` additions:

Under `edition.actions`:
```json
"manageCategories": "Manage categories"
```

New top-level `category` section:
```json
"category": {
  "title": "Categories — {{editionName}}",
  "back": "Back to editions",
  "locked": {
    "banner": "Categories are locked — the edition has entered the Deposit phase."
  },
  "copy": {
    "label": "Copy from a closed edition",
    "placeholder": "Select a closed edition",
    "button": "Copy from this edition",
    "success": "Categories copied.",
    "error": "Failed to copy categories."
  },
  "table": {
    "addButton": "Add category",
    "save": "Save categories",
    "empty": "No categories configured yet.",
    "columns": {
      "name": "Category name",
      "tables": "Tables (comma-separated)"
    }
  },
  "row": {
    "namePlaceholder": "e.g. Toys",
    "tablesPlaceholder": "e.g. 1, 2, 3",
    "remove": "Remove",
    "error": {
      "nameRequired": "Category name is required.",
      "tableRequired": "Assign at least one table to this category."
    }
  },
  "save": {
    "success": "Categories saved.",
    "error": "Failed to save categories."
  },
  "load": {
    "error": "Failed to load categories."
  }
}
```

#### `fr.json` additions:

Under `edition.actions`:
```json
"manageCategories": "Gérer les catégories"
```

New top-level `category` section (vouvoiement):
```json
"category": {
  "title": "Catégories — {{editionName}}",
  "back": "Retour aux éditions",
  "locked": {
    "banner": "Les catégories sont verrouillées — l'édition est entrée en phase de dépôt."
  },
  "copy": {
    "label": "Copier depuis une édition clôturée",
    "placeholder": "Sélectionnez une édition clôturée",
    "button": "Copier depuis cette édition",
    "success": "Catégories copiées.",
    "error": "Impossible de copier les catégories."
  },
  "table": {
    "addButton": "Ajouter une catégorie",
    "save": "Enregistrer les catégories",
    "empty": "Aucune catégorie configurée.",
    "columns": {
      "name": "Nom de la catégorie",
      "tables": "Tables (séparées par des virgules)"
    }
  },
  "row": {
    "namePlaceholder": "ex. Jouets",
    "tablesPlaceholder": "ex. 1, 2, 3",
    "remove": "Supprimer",
    "error": {
      "nameRequired": "Le nom de la catégorie est obligatoire.",
      "tableRequired": "Assignez au moins une table à cette catégorie."
    }
  },
  "save": {
    "success": "Catégories enregistrées.",
    "error": "Impossible d'enregistrer les catégories."
  },
  "load": {
    "error": "Impossible de charger les catégories."
  }
}
```

---

### Key Paths Reference

**Backend:**
- Migration: `pluribourse-backend/src/main/resources/db/changelog/011-edition-categories.xml`
- Changelog master: `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- Entity: `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/EditionCategory.java`
- Repository: `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionCategoryRepository.java`
- DTO: `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionCategoryDto.java`
- Mapper: `pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionCategoryMapper.java`
- Service: `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionCategoryService.java`
- Controller: `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionCategoryController.java`
- IT test: `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionCategoryIT.java`

**Frontend:**
- Model: `pluribourse-frontend/src/app/models/category.model.ts`
- Service: `pluribourse-frontend/src/app/services/category.service.ts`
- Component TS: `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts`
- Component HTML: `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.html`
- Routes: `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- Edition list HTML: `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- i18n EN: `pluribourse-frontend/public/i18n/en.json`
- i18n FR: `pluribourse-frontend/public/i18n/fr.json`
- Spec — component: `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts`
- Spec — service: `pluribourse-frontend/src/app/services/category.service.spec.ts`

---

### Previous Story Learnings (from Story 2.4)

- **`@ElementCollection` test isolation**: `@DirtiesContext(classMode = AFTER_CLASS)` handles cleanup between IT classes — no extra cleanup needed for collection tables.
- **Empty string vs null**: `<input type="text">` returns `''` not `null` when cleared. Always convert `''` → `[]` or `null` before sending to API.
- **Existing `EditionDto` mock objects**: When adding new fields to `EditionDto` record (not the case here — `EditionDto` stays as-is), update all spec mocks. For this story, `EditionDto` is unchanged.
- **`ObjectMapper` in tests**: `@Autowired` from `IntegrationTest` context (it's auto-configured). Use `objectMapper.writeValueAsString(list)` to serialize `List<EditionCategoryDto>`.
- **H2 compatibility**: H2 in tests supports `@ElementCollection` fine. No Testcontainers needed for this story.
- **Security in IT tests**: All admin endpoints need `with(csrf())` on modifying requests (POST, PUT, DELETE). GET requests don't need it.

### ⚠️ JPQL Delete + @ElementCollection — Critical Implementation Detail

`deleteAllByEditionId` executes a JPQL `DELETE FROM EditionCategory WHERE edition.id = ?` which bypasses JPA lifecycle events. This means the `@ElementCollection` rows in `category_table_assignments` are NOT deleted by JPA — they rely on the **database FK cascade** configured in the Liquibase migration (`deleteCascade="true"`).

This is safe because:
1. The Liquibase migration sets `ON DELETE CASCADE` on `category_table_assignments.category_id → edition_categories.id`
2. H2 (test) and MariaDB (prod) both enforce FK cascade deletes
3. JPQL DELETE does execute the underlying SQL which triggers DB-level cascade

**Do NOT** switch `deleteAllByEditionId` to a `findAll + deleteAll` pattern — it would generate N×2 SQL statements per save, which is inefficient.

---

### Story 2.5-specific Learnings from Analysis

- `EditionRepository` already exists and has `findById()` — reuse it in `EditionCategoryService` via constructor injection.
- `EditionService.findById()` is private — do NOT reuse it. Inject `EditionRepository` directly in `EditionCategoryService`.
- The existing `PhaseTransitionIT` tests advance/rollback phase via the API. For `EditionCategoryIT`, it's simpler to manipulate phase directly via `EditionRepository` (same approach as `EditionManagementIT.@Order(8)`) to avoid dependency on PhaseTransition endpoint.
- `ConfirmDialogService` is available in the frontend context for the copy action confirmation if needed (optional — the story does not explicitly require a confirmation dialog for copy).
- Angular `FormsModule` (for `[(ngModel)]`) or `ReactiveFormsModule` — use `FormsModule` for the simpler inline row editing (ngModel on `EditableCategoryRow` fields); the component doesn't need a form group here.

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- **Bug**: Derived-delete `deleteAllByEditionId` schedules SQL DELETEs after INSERTs at Hibernate flush time, violating the `(edition_id, name)` unique constraint on re-save. Fixed by replacing with `@Modifying(clearAutomatically = true) @Query("DELETE FROM EditionCategory c WHERE c.edition.id = :editionId")`.

### Completion Notes List

- **Migration 011**: `edition_categories` + `category_table_assignments` with FK cascade delete (DB-level cascade covers `@ElementCollection` orphans during bulk JPQL delete).
- **`EditionCategory` entity**: `@ManyToOne(LAZY)` + `@ElementCollection` on `Set<Integer>` → `category_table_assignments`. Package: `edition.entity`.
- **`EditionCategoryRepository`**: `@Modifying(clearAutomatically = true) @Query` for delete (instead of derived delete) to avoid Hibernate flush-order issue with unique constraint.
- **`EditionCategoryService`**: `getCategories`, `saveCategories` (full replace), `copyFromEdition`. Phase guard: PREPARATION only for writes. Validation: name non-blank + at least one table number.
- **`EditionCategoryController`**: 3 endpoints — GET, PUT, POST `/copy-from/{sourceEditionId}`. `@PreAuthorize("hasRole('ADMIN')")` at class level, matching `EditionController` pattern.
- **`EditionCategoryIT`**: 11 ordered tests. Source edition setup in `@BeforeAll`: create, add categories, force CLOSED via repository. Main test edition created in `@Order(1)`. All 125 backend tests pass.
- **Frontend**: `category.model.ts`, `category.service.ts`, `EditionCategoriesComponent` (standalone, FormsModule + signals). `categories: EditableCategoryRow[]` as plain mutable array; signals for reactive state. Copy section visible when closed editions exist and not read-only.
- **Frontend tests**: 172 tests, 0 failures (11 new: 8 component + 3 service).
- **i18n**: `category.*` keys added in EN and FR. `edition.actions.manageCategories` added.
- **Routing**: `editions/:id/categories` route added to `admin.routes.ts`.
- **Edition list**: "Manage categories" link added alongside "Manage phase".

### File List

- `pluribourse-backend/src/main/resources/db/changelog/011-edition-categories.xml` _(new)_
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/EditionCategory.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/repository/EditionCategoryRepository.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionCategoryDto.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/mapper/EditionCategoryMapper.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionCategoryService.java` _(new)_
- `pluribourse-backend/src/main/java/org/pluribourse/edition/controller/EditionCategoryController.java` _(new)_
- `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionCategoryIT.java` _(new)_
- `pluribourse-frontend/src/app/models/category.model.ts` _(new)_
- `pluribourse-frontend/src/app/services/category.service.ts` _(new)_
- `pluribourse-frontend/src/app/services/category.service.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts` _(new)_
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.html` _(new)_
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts` _(new)_
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

| Date | Change |
|------|--------|
| 2026-06-30 | Story file created (ready-for-dev) |
| 2026-06-30 | Implementation complete — status → review |
| 2026-06-30 | Code review complete — findings written below |

### Review Findings

- [x] [Review][Patch] Always show copy section — section must remain visible even when no closed editions exist; show dropdown disabled with placeholder instead of hiding the entire block [edition-categories.component.html]

- [x] [Review][Patch] Two i18n keys missing from en.json and fr.json: `category.copy.placeholder` and `category.row.remove` specified in T14 are absent from both language files [pluribourse-frontend/public/i18n/en.json, fr.json]

- [x] [Review][Patch] `<mat-error>` placed outside `<mat-form-field>` — Angular Material error integration broken; input field does not enter error state (no red underline), `aria-describedby` accessibility link absent [edition-categories.component.html]

- [x] [Review][Patch] No tests for `onSave()` or `onCopy()` — CSV parsing, client-side validation gating, error branching and toast calls are untested [edition-categories.component.spec.ts]

- [x] [Review][Defer] `parseTableInput` silently drops table number 0 (filter `n > 0`) without user-visible warning [edition-categories.component.ts:103] — deferred, spec says "1..N" so 0 is invalid; low UX impact

- [x] [Review][Defer] `tableError` field used for both name-required and table-required errors — confusing naming in `EditableCategoryRow` interface [edition-categories.component.ts:73] — deferred, no functional bug

- [x] [Review][Defer] Empty array PUT silently deletes all categories with no frontend guard or confirmation [edition-categories.component.ts, EditionCategoryService.java] — deferred, valid per bulk-replace spec; future UX improvement

- [x] [Review][Defer] N+1 inserts in `persistCategories` — each category saved individually in a loop [EditionCategoryService.java:233] — deferred, performance concern only; acceptable for typical category counts

- [x] [Review][Defer] Concurrent saves race condition — no pessimistic lock; simultaneous PUTs could cause unique constraint violation (500 instead of graceful 422) [EditionCategoryService.java:206] — deferred, unlikely in single-admin context

- [x] [Review][Defer] `copyFromEdition` with a 0-category source silently clears all target categories with no user warning [EditionCategoryService.java:215] — deferred, correct per bulk-replace contract; UX edge case only
