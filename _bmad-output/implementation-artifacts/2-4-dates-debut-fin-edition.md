---
baseline_commit: 320bb2795954fc5596c0c5bdfa4db668e8c2506f
---

# Story 2.4: Edition Start and End Dates

Status: done

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

- [x] **T1 — Backend: Liquibase migration 010** (AC: 3, 7)
  - [x] T1.1 — Create `pluribourse-backend/src/main/resources/db/changelog/010-edition-dates.xml` (see Dev Notes)
  - [x] T1.2 — Add `<include file="db/changelog/010-edition-dates.xml"/>` to `db.changelog-master.xml` after 009

- [x] **T2 — Backend: Edition entity** (AC: 3)
  - [x] T2.1 — Add `startDate` (`LocalDate`, nullable) and `endDate` (`LocalDate`, nullable) fields to `Edition.java` (see Dev Notes)

- [x] **T3 — Backend: EditionDto** (AC: 7)
  - [x] T3.1 — Add `LocalDate startDate` and `LocalDate endDate` components to `EditionDto` record (nullable — no `@NotNull`). MapStruct maps them automatically by name match.

- [x] **T4 — Backend: EditionService — updateEdition** (AC: 1, 2, 3)
  - [x] T4.1 — In `updateEdition()`, update both `startDate` and `endDate` from the DTO unconditionally (null values are valid — they clear the field). No guard based on phase.
  - [x] T4.2 — In `createEdition()`, set `startDate` and `endDate` from the DTO if provided (both nullable).

- [x] **T5 — Backend: Integration test** (AC: 1, 2, 3, 7)
  - [x] T5.1 — Add test cases to the existing `EditionManagementIT` class (or create a new class — follow the existing test philosophy: E2E by controller, ordered scenario)
  - [x] T5.2 — Scenario: create edition with `startDate` and `endDate` → verify both appear in the response
  - [x] T5.3 — Scenario: update edition to clear `startDate` (send `null`) → verify response returns `null` for `startDate`

- [x] **T6 — Frontend: edition.model.ts** (AC: 7)
  - [x] T6.1 — Add `startDate: string | null` and `endDate: string | null` to the `EditionDto` interface

- [x] **T7 — Frontend: edition-form.component.ts** (AC: 1, 2, 3, 5)
  - [x] T7.1 — Add `startDate: [null as string | null]` and `endDate: [null as string | null]` controls to the form group (no validators — both optional)
  - [x] T7.2 — In `loadEdition()`, patch `startDate` and `endDate` from the loaded edition
  - [x] T7.3 — In `onSubmit()`, include `startDate` and `endDate` (via `getRawValue()`) in the payload sent to `create()` or `update()`

- [x] **T8 — Frontend: edition-form.component.html** (AC: 1, 5)
  - [x] T8.1 — Add a `<mat-form-field>` with `<input matInput formControlName="startDate" type="date">` after the `documentLanguage` field
  - [x] T8.2 — Add a second `<mat-form-field>` for `endDate` the same way
  - [x] T8.3 — Labels use i18n keys `edition.form.startDate` and `edition.form.endDate`

- [x] **T9 — Frontend: edition.service.ts** (AC: 3)
  - [x] T9.1 — Update `EditionRequest` type to include `startDate: string | null` and `endDate: string | null`

- [x] **T10 — Frontend: edition-list.component.html** (AC: 4)
  - [x] T10.1 — Add `<th>` columns for "Start date" and "End date" in the header row (before the "Actions" column), using i18n keys `edition.list.columns.startDate` and `edition.list.columns.endDate`
  - [x] T10.2 — Add `<td>` cells: `{{ edition.startDate ?? '—' }}` and `{{ edition.endDate ?? '—' }}`

- [x] **T11 — Frontend: i18n keys** (AC: 1, 4)
  - [x] T11.1 — Add to `public/i18n/en.json`:
    - Under `edition.form`: `"startDate": "Start date"` and `"endDate": "End date"`
    - Under `edition.list.columns`: `"startDate": "Start date"` and `"endDate": "End date"`
  - [x] T11.2 — Add the French equivalents to `fr.json`:
    - Under `edition.form`: `"startDate": "Date de début"` and `"endDate": "Date de fin"`
    - Under `edition.list.columns`: `"startDate": "Date de début"` and `"endDate": "Date de fin"`

- [x] **T12 — Frontend: spec files — update mock data** (regression prevention)
  - [x] T12.1 — Add `startDate: null, endDate: null` to every `EditionDto` mock object in `edition-form.component.spec.ts`, `edition-list.component.spec.ts`, and `phase-control.component.spec.ts` (TypeScript will fail to compile if the new mandatory fields are absent from mock objects)
  - [x] T12.2 — Run `npm test` — all tests must pass

### Review Findings (AI)

- [x] [Review][Patch] Misleading test comment in Order(18): says "then delete" but only sets CLOSED [EditionManagementIT.java:Order(18)]
- [x] [Review][Patch] Editing dates on a non-PREPARATION edition triggers spurious 422 "commission-rate-frozen" — fixed by sending `commissionRate: null` when the control is disabled; `EditionRequest` type expanded to `number | null` [edition-form.component.ts:onSubmit(), edition.service.ts:EditionRequest]
- [x] [Review][Defer] `updateEdition` overwrites startDate/endDate unconditionally (null = clear), asymmetric with commissionRate/documentLanguage guards — intentional by spec Dev Notes; noted as API footgun for future callers [EditionService.java:updateEdition()] — deferred, pre-existing

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

_None._

### Completion Notes List

- Liquibase migration 010 adds `start_date` and `end_date` nullable DATE columns to the `editions` table.
- `Edition` entity, `EditionDto` record, and `EditionService` (create + update) updated; MapStruct maps both fields by name — no mapper changes needed.
- All existing `EditionManagementIT`, `PhaseTransitionIT`, and `VolunteerEditionGateIT` test constructors updated from 7 to 9 args (`null, null` appended).
- Two new ordered tests added (@Order 18, 19): create with dates → verify, then update to clear startDate → verify null.
- Frontend: `EditionDto` interface, `EditionRequest` type, form group, `loadEdition()`, `onSubmit()`, HTML template, list HTML, i18n (en + fr), and all spec mocks updated.
- Empty-string → null conversion applied in `onSubmit()` before sending to API (`startDate: startDate || null`).
- `edition.service.spec.ts` was also discovered and updated (was not listed in story Dev Notes).
- Tests: 114 backend (0 failures), 161 frontend (0 failures).

### File List

- `pluribourse-backend/src/main/resources/db/changelog/010-edition-dates.xml` _(new)_
- `pluribourse-backend/src/main/resources/db/changelog/db.changelog-master.xml`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/entity/Edition.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/dto/EditionDto.java`
- `pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`
- `pluribourse-backend/src/test/java/org/pluribourse/edition/EditionManagementIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/edition/PhaseTransitionIT.java`
- `pluribourse-backend/src/test/java/org/pluribourse/edition/VolunteerEditionGateIT.java`
- `pluribourse-frontend/src/app/models/edition.model.ts`
- `pluribourse-frontend/src/app/services/edition.service.ts`
- `pluribourse-frontend/src/app/services/edition.service.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
