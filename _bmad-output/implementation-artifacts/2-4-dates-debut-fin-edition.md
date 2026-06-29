# Story 2.4: Edition Start and End Dates

Status: ready-for-dev

## Story

As an administrator,
I want to record optional start and end dates for an edition,
so that I have an administrative record of when the event took place.

## Acceptance Criteria

1. **Given** the administrator creates or edits an edition, **When** the form is displayed, **Then** two optional date fields are available: "Start date" and "End date".

2. **Given** the administrator leaves both date fields empty, **When** the edition is saved, **Then** the edition is saved normally — both fields are nullable and their absence is not an error.

3. **Given** the administrator fills one or both date fields, **When** the edition is saved, **Then** the dates are persisted to the database and returned in all edition API responses.

4. **Given** the edition list is displayed, **When** editions are loaded, **Then** a "Start date" and "End date" column appear in the table, showing the date value or a dash (`—`) when not set.

5. **Given** a date is entered in the form, **When** the field is rendered, **Then** it uses `<input type="date">` inside a `mat-form-field` — no MatDatepicker, no date adapter dependency.

6. **Given** both start and end dates are provided, **When** either field changes, **Then** no cross-field validation is enforced — the backend and frontend treat them as independent optional fields (business-level consistency is the admin's responsibility).

7. **Given** the edition API returns edition data, **When** the response is serialized, **Then** both `startDate` and `endDate` appear as ISO 8601 date strings (`YYYY-MM-DD`) or `null`.

## Tasks / Subtasks

- [ ] **T1 — Backend: Liquibase migration 010** (AC: 3, 7)
  - [ ] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/010-edition-dates.xml` (see Dev Notes)
  - [ ] T1.2 — Add `<include file="db/changelog/010-edition-dates.xml"/>` to `db.changelog-master.xml` after 009

- [ ] **T2 — Backend: Edition entity** (AC: 3)
  - [ ] T2.1 — Add `startDate` (`LocalDate`, nullable) and `endDate` (`LocalDate`, nullable) fields to `Edition.java` (see Dev Notes)

- [ ] **T3 — Backend: EditionDto** (AC: 7)
  - [ ] T3.1 — Add `LocalDate startDate` and `LocalDate endDate` components to `EditionDto` record (nullable — no `@NotNull`). MapStruct maps them automatically by name match.

- [ ] **T4 — Backend: EditionService — updateEdition** (AC: 1, 2, 3)
  - [ ] T4.1 — In `updateEdition()`, update both `startDate` and `endDate` from the DTO unconditionally (null values are valid — they clear the field). No guard based on phase.
  - [ ] T4.2 — In `createEdition()`, set `startDate` and `endDate` from the DTO if provided (both nullable).

- [ ] **T5 — Backend: Integration test** (AC: 1, 2, 3, 7)
  - [ ] T5.1 — Add test cases to the existing `EditionManagementIT` class (or create a new class — follow the existing test philosophy: E2E by controller, ordered scenario)
  - [ ] T5.2 — Scenario: create edition with `startDate` and `endDate` → verify both appear in the response
  - [ ] T5.3 — Scenario: update edition to clear `startDate` (send `null`) → verify response returns `null` for `startDate`

- [ ] **T6 — Frontend: edition.model.ts** (AC: 7)
  - [ ] T6.1 — Add `startDate: string | null` and `endDate: string | null` to the `EditionDto` interface

- [ ] **T7 — Frontend: edition-form.component.ts** (AC: 1, 2, 3, 5)
  - [ ] T7.1 — Add `startDate: [null as string | null]` and `endDate: [null as string | null]` controls to the form group (no validators — both optional)
  - [ ] T7.2 — In `loadEdition()`, patch `startDate` and `endDate` from the loaded edition
  - [ ] T7.3 — In `onSubmit()`, include `startDate` and `endDate` (via `getRawValue()`) in the payload sent to `create()` or `update()`

- [ ] **T8 — Frontend: edition-form.component.html** (AC: 1, 5)
  - [ ] T8.1 — Add a `<mat-form-field>` with `<input matInput formControlName="startDate" type="date">` after the `documentLanguage` field
  - [ ] T8.2 — Add a second `<mat-form-field>` for `endDate` the same way
  - [ ] T8.3 — Labels use i18n keys `edition.form.startDate` and `edition.form.endDate`

- [ ] **T9 — Frontend: edition.service.ts** (AC: 3)
  - [ ] T9.1 — Update `EditionRequest` type to include `startDate: string | null` and `endDate: string | null`

- [ ] **T10 — Frontend: edition-list.component.html** (AC: 4)
  - [ ] T10.1 — Add `<th>` columns for "Start date" and "End date" in the header row (before the "Actions" column), using i18n keys `edition.list.columns.startDate` and `edition.list.columns.endDate`
  - [ ] T10.2 — Add `<td>` cells: `{{ edition.startDate ?? '—' }}` and `{{ edition.endDate ?? '—' }}`

- [ ] **T11 — Frontend: i18n keys** (AC: 1, 4)
  - [ ] T11.1 — Add to `public/i18n/en.json`:
    - Under `edition.form`: `"startDate": "Start date"` and `"endDate": "End date"`
    - Under `edition.list.columns`: `"startDate": "Start date"` and `"endDate": "End date"`
  - [ ] T11.2 — Add the French equivalents to `fr.json`:
    - Under `edition.form`: `"startDate": "Date de début"` and `"endDate": "Date de fin"`
    - Under `edition.list.columns`: `"startDate": "Date de début"` and `"endDate": "Date de fin"`

- [ ] **T12 — Frontend: spec files — update mock data** (regression prevention)
  - [ ] T12.1 — Add `startDate: null, endDate: null` to every `EditionDto` mock object in `edition-form.component.spec.ts`, `edition-list.component.spec.ts`, and `phase-control.component.spec.ts` (TypeScript will fail to compile if the new mandatory fields are absent from mock objects)
  - [ ] T12.2 — Run `npm test` — all tests must pass

## Dev Notes

### T1 — Migration 010

File: `pluribourse-backend/src/main/resources/db/changelog/010-edition-dates.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="010-edition-dates" author="pluribourse">
        <addColumn tableName="editions">
            <column name="start_date" type="DATE">
                <constraints nullable="true"/>
            </column>
            <column name="end_date" type="DATE">
                <constraints nullable="true"/>
            </column>
        </addColumn>
    </changeSet>

</databaseChangeLog>
```

### T2 — Edition Entity

Add to `Edition.java`, after the `createdAt` field:

```java
@Column(name = "start_date")
private LocalDate startDate;

@Column(name = "end_date")
private LocalDate endDate;
```

No `nullable = false` — both are optional. No default value needed (defaults to `null`).

### T3 — EditionDto

Add `startDate` and `endDate` at the end of the record signature, after `archived`:

```java
public record EditionDto(
        Long id,
        @NotBlank @Size(max = 255) String name,
        PhaseType phase,
        @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal commissionRate,
        Language documentLanguage,
        LocalDate createdAt,
        Boolean archived,
        LocalDate startDate,
        LocalDate endDate
) {}
```

MapStruct auto-maps `startDate` and `endDate` by name — no changes needed in `EditionMapper`.

**Existing tests:** Adding fields to the record doesn't break `EditionManagementIT` — JSON deserialization ignores unknown fields by default in Spring Boot (Jackson's `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` is `false`). The two new fields will appear as `null` in existing test responses — no assertions on them, so safe.

### T4 — EditionService Updates

In `updateEdition()`, add after the `documentLanguage` update:
```java
edition.setStartDate(dto.startDate());
edition.setEndDate(dto.endDate());
```

In `createEdition()`, add after `setDocumentLanguage`:
```java
edition.setStartDate(dto.startDate());
edition.setEndDate(dto.endDate());
```

Setting `null` explicitly is correct — it clears the field.

### T7 — Form Type

`<input type="date">` emits a string in `YYYY-MM-DD` format or `''` (empty string) when cleared. Use `null as string | null` as the initial value. In `getRawValue()`, the field will be `''` when empty — convert empty string to `null` before sending to the API:

```typescript
const { name, commissionRate, documentLanguage, startDate, endDate } = this.form.getRawValue();
// ...
startDate: startDate || null,
endDate: endDate || null,
```

This prevents the backend from receiving an empty string that fails LocalDate parsing.

### Project Structure Notes

- Liquibase master: `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml` — next file is `010-edition-dates.xml`
- Entity: `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java`
- DTO: `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java`
- Service: `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`
- Frontend model: `pluribourse-frontend/src/app/models/edition.model.ts`
- Frontend service: `pluribourse-frontend/src/app/services/edition.service.ts`
- Form component: `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts` and `.html`
- List component: `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- Spec files containing `EditionDto` mocks: `edition-form.component.spec.ts`, `edition-list.component.spec.ts`, `phase-control.component.spec.ts`

### References

- [Source: edition/entity/Edition.java] Current fields and column mapping patterns
- [Source: edition/dto/EditionDto.java] Current record structure — `archived` is `Boolean` (nullable wrapper), `createdAt` is `LocalDate`
- [Source: edition/service/EditionService.java#createEdition+updateEdition] Existing field update patterns
- [Source: db/changelog/db.changelog-master.xml] Current migration sequence (009 is last)
- [Source: edition-form.component.html] Form field pattern to replicate for date inputs
- [Source: edition-list.component.html] Table column pattern to replicate for new columns

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
