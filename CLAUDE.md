# CLAUDE.md — PluriBourse

## Project Overview
PluriBourse is a self-hosted event management platform for associations organizing secondhand sale events (toys, books, skis, clothing, and more). It covers the full event lifecycle: seller registration, product cataloging with barcode label generation, multi-workstation point-of-sale scanning, and automated seller payout calculation.

Stack: Spring Boot (backend) + Angular (frontend), deployed via Docker Compose with MariaDB.

## Language
- Code (variables, methods, classes, packages): English
- Code comments and JavaDoc: English
- Project documentation (planning artifacts, PRD, architecture, epics, UX docs): French

## Architecture

### Backend (Spring Boot)
- Root package: `org.pluribourse`
- Layered architecture: Controller → Service → Repository
- DTOs for the API layer; MapStruct for entity↔DTO mapping
- Lombok for boilerplate (getters, setters, builders, constructors)
- Database migrations: Liquibase

### Frontend (Angular)
- Standalone components (latest Angular version)
- State management: Signals — no NgRx
- No inline template. Always create a new html file

## JavaDoc
- Required on complex methods: non-trivial logic, non-obvious parameters or return values
- Not required on simple getters, setters, or self-explanatory CRUD operations

## Comments
- Add inline comments only when the **why** is not obvious from the code
- Never describe what the code does — well-named identifiers do that
- No multi-line comment blocks

## Testing

### Backend (Spring Boot)
- Frameworks: JUnit 5 + Mockito
- Both unit tests and integration tests required
- Integration tests use H2 in-memory database with realistic fixture datasets
- Minimum coverage target: 80%

### Frontend (Angular)
- Frameworks: Jest + Jasmine
- Minimum coverage target: 80%

## Git

### Commit messages
Follow Conventional Commits:
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` code change with no functional impact
- `test:` adding or updating tests
- `chore:` tooling, dependencies, config

### Branch naming
`FEATURE-[FEATURE_ID]-[short-description]`
- Feature IDs match the PRD feature groups (F1–F10)
- Example: `FEATURE-F3-seller-registration`

## Key Constraints
- All financial calculations (commission, payouts): use `BigDecimal` — never `float` or `double`
- All UI text must go through the i18n system (ngx-translate) — no hardcoded strings in templates or components
- No PII (seller name, email, phone number) in application logs
