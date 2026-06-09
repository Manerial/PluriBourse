---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-06-09'
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md'
  - '_bmad-output/planning-artifacts/briefs/brief-PluriBourse-2026-06-08/brief.md'
workflowType: 'architecture'
project_name: 'PluriBourse'
user_name: 'Manerial'
date: '2026-06-09'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

89 functional requirements across 10 feature groups (F1–F10). The system is organized around an event lifecycle state machine that drives all behaviors:

- **F1 — Internationalisation (7 FRs):** Cross-cutting foundation. Dual-layer i18n: ngx-translate for UI, Spring MessageSource for printed documents. Language per user account (UI) and per instance (documents).
- **F2 — Edition Management (12 FRs):** Admin-controlled phase lifecycle (Deposit → Sale → Post-sale → Closed). One active edition at a time. Phase rollback supported. Optional destructive "Clean Edition" action.
- **F3 — Seller & Product Management (18 FRs):** Cross-edition seller profiles. Item registration with auto table assignment. Code 128 barcode generation. Thermal label + deposit slip printing via ESC/POS queue. Lot support.
- **F4 — Point of Sale (13 FRs):** USB HID scanner with AZERTY/QWERTY transparent handling. Basket management with lot integrity enforcement. Buyer invoice printing. Phase-transition basket cancellation (FR-090).
- **F5 — Post-Sale & Payouts (5 FRs):** Seller settlement workflow. "Not collected" path transferring payout to association revenue. Sales summary per seller.
- **F6 — Reporting (6 FRs):** Daily summary, edition summary, outstanding sellers report — all PDF, admin only.
- **F7 — User Accounts & Access Control (8 FRs):** Strict Admin/Volunteer role separation. Single admin account per instance. Phase-driven volunteer interface.
- **F8 — Infrastructure & Deployment (7 FRs):** Cross-platform Docker Compose. Raspberry Pi 4 target. Non-technical installation guide.
- **F9 — Print Infrastructure (5 FRs):** Centralized server-side print endpoint. Thermal (ESC/POS) + A4 (PDF) printers via USB. Sequential print queue. Error feedback to UI.
- **F10 — Item Catalog (8 FRs):** Filterable/sortable catalog across all phases. Catalog-to-basket fallback for unreadable barcodes.

**Non-Functional Requirements:**

| ID | Category | Architectural Impact |
|---|---|---|
| NFR-001 | Performance | Must run on RPi 4 (2 GB RAM) under event load (~1,700 items, 3 workstations) |
| NFR-002 | Concurrency | Simultaneous POS operations must not generate data conflicts |
| NFR-003 | Financial Accuracy | All monetary calculations in BigDecimal — no float/double |
| NFR-004 | Browser Compatibility | Any modern browser, any OS — pure REST + SPA, no browser-specific APIs |
| NFR-005 | Scanner Compatibility | USB HID transparent layout handling in the Angular scan component |
| NFR-006 | Reliability | No data loss on browser close — server-side transaction boundaries |
| NFR-007 | GDPR | PII anonymization on seller deletion across all editions; no PII in logs |

**Scale & Complexity:**

- Primary domain: Full-stack web, backend-heavy
- Complexity level: **Medium** — modest data volume (~100 sellers, ~1,700 items/edition), significant functional complexity (state machine, print infrastructure, concurrency, GDPR, PDF generation)
- Estimated architectural modules: ~8–10 distinct bounded contexts

### Technical Constraints & Dependencies

| Constraint | Decision | Source |
|---|---|---|
| Backend | Spring Boot | Brief / CLAUDE.md |
| Frontend | Angular (standalone components, Signals) | CLAUDE.md |
| Database | MariaDB + Docker Compose | PRD Addendum |
| Thermal printing | ESC/POS protocol, `escpos-coffee` library candidate | PRD Addendum |
| i18n — UI | ngx-translate (JSON files) | PRD Addendum |
| i18n — Documents | Spring MessageSource (.properties files) | PRD Addendum |
| Financial calculations | BigDecimal — never float/double | CLAUDE.md |
| DB migrations | Liquibase | CLAUDE.md |
| Target hardware | Raspberry Pi 4 (2 GB RAM), SSD/USB storage | PRD NFR-001 |

### Cross-Cutting Concerns Identified



1. **Phase state machine** — drives UI rendering, business rule enforcement, and access control across all modules
2. **Authentication & role separation** — Admin/Volunteer strictly separated; volunteer interface adapts to active phase
3. **Concurrency management** — item "sold" state must be conflict-free across simultaneous POS workstations
4. **Server-side print queue** — sequential, centralized, two independent queues (thermal / A4)
5. **Financial accuracy** — BigDecimal propagates through item pricing, commission, payout calculation, and all reports
6. **GDPR compliance** — PII lifecycle management (anonymization, not deletion of records); no PII in logs
7. **i18n (dual layer)** — UI language per user account; document language per instance

---

## Starter Template Evaluation

### Primary Technology Domain

Full-stack web application — backend-heavy. Stack decided upfront: Spring Boot (backend) + Angular (frontend), MariaDB, Docker Compose.

### Scaffolding Tools

| Layer | Tool | Command |
|---|---|---|
| Backend | Spring Initializr | `start.spring.io` or Spring Boot CLI |
| Frontend | Angular CLI | `ng new pluribourse-frontend` |
| Infrastructure | Manual | Custom `docker-compose.yml` |

### Selected Versions

| Technology | Version | Rationale |
|---|---|---|
| Java | **21** (LTS) | Virtual threads (Project Loom) reduce memory pressure under concurrent POS load on RPi 4; LTS supported until 2031 |
| Spring Boot | **4.0.6** | Latest stable; Spring Framework 7, Spring Security 7, Hibernate 7 |
| Angular | **21** (LTS) | Angular 22 released June 3, 2026 — too fresh for `jest-preset-angular` ecosystem stability; LTS supported until May 2027 |
| Build tool | **Maven** | Zero-surprise builds; well-known to Java expert; `pom.xml` readable; JaCoCo + Failsafe coverage setup exhaustively documented |

### Backend Initialization (Spring Initializr)

```
Group:      org.pluribourse
Artifact:   pluribourse
Java:       21
Packaging:  JAR
Build:      Maven
Boot:       4.0.6
Dependencies:
  - Spring Web
  - Spring Data JPA
  - Spring Security
  - Liquibase Migration
  - Lombok
  - MariaDB Driver
  - Validation
```

**Manually added post-init:**
- MapStruct (not available in Initializr)
- OpenPDF 3.0.0 (LGPL 2.1 + MPL 1.1) — PDF generation
- escpos-coffee or equivalent — ESC/POS thermal printing

### Frontend Initialization (Angular CLI)

```bash
ng new pluribourse-frontend --standalone --routing --style=scss
```

**Manually added post-init:**
- @ngx-translate/core + @ngx-translate/http-loader — i18n
- @angular/material — UI component library (MIT)

### Architectural Decisions Provided by Starters

**Language & Runtime:** Java 21 with Maven wrapper; TypeScript strict mode (Angular default)

**Styling:** SCSS — flexibility for theming Angular Material

**Build Tooling:** Maven (backend) + Angular CLI / esbuild (frontend)

**Testing Framework:** JUnit 5 + Mockito (Spring Boot default); Jest + Angular CDK Testing Harnesses (configured post-init per CLAUDE.md)

**Code Organization:** Standard Spring Boot layered structure (Controller → Service → Repository); Angular standalone components with feature-based folder structure

**Development Experience:** Spring Boot DevTools hot reload; Angular CLI dev server with HMR

### License Compliance

All selected dependencies use permissive or weak-copyleft licenses. Project policy: no AGPL, no GPL copyleft.

| Dependency | License |
|---|---|
| Spring Boot / Spring Framework | Apache 2.0 |
| Angular + Angular Material | MIT |
| MariaDB Connector/J | LGPL 2.1 |
| Liquibase | Apache 2.0 |
| Lombok | MIT |
| MapStruct | Apache 2.0 |
| OpenPDF 3.0.0 | LGPL 2.1 + MPL 1.1 |
| ngx-translate | MIT |

> iText 7 (AGPL) was explicitly rejected — even in open-source use it requires mentioning iText in every generated PDF's metadata. OpenPDF is the drop-in alternative with no such obligation.

**Note:** Project initialization (Spring Initializr + `ng new`) should be the first implementation story.

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Session management strategy (affects all secured endpoints)
- POS concurrency model (affects item sale integrity)
- Phase change notification mechanism (affects all active-phase UIs)

**Important Decisions (Shape Architecture):**
- Print queue implementation (affects F9 reliability)
- Barcode generation library (affects F3 label generation)
- API documentation tooling (affects developer experience)

**Deferred Decisions (Post-v1):**
- Backup/restore mechanism (explicitly out of scope v1)
- Per-edition commission override (out of scope v1)
- Testcontainers CI pipeline setup (can be added incrementally)

---

### Authentication & Security

| Decision | Choice | Rationale |
|---|---|---|
| Auth mechanism | Spring Security (form-based, stateful sessions) | Single-instance deployment, no JWT complexity needed, FR-066 (no session expiry) |
| Session storage | **Spring Session JDBC** (MariaDB) | Sessions survive container restarts during events — critical in a 4–6h live event context |
| Session expiry | None (FR-066) | Configured explicitly: `server.servlet.session.timeout=-1` |
| Password encoding | BCrypt (Spring Security default) | Industry standard, no additional config |
| Admin password reset | CLI command generating a temporary password (FR-063) | Spring Boot `CommandLineRunner` or custom CLI script; forces password change on next login |
| Role model | Three roles: `ADMIN`, `VOLUNTEER`, `SELLER` — strictly separated | `SELLER` reserved in v1 (no UI, no public endpoints); link `User ↔ SellerProfile` via nullable FK ready for v2 portal |
| SELLER scope v1 | `Role.SELLER` déclaré, FK `users.seller_profile_id` nullable | Aucun endpoint public, aucune UI, aucune inscription — tout est bloqué à 403 via Spring Security |
| SELLER scope v2 | Portail d'inscription public, sélection de créneaux de dépôt | Nécessite : HTTPS, reverse proxy, ouverture réseau — hors périmètre v1 |

---

### Data Architecture

| Decision | Choice | Rationale |
|---|---|---|
| ORM | Spring Data JPA + Hibernate 7 | Provided by Spring Boot 4.0.6 |
| Migrations | Liquibase | Declared in CLAUDE.md; version-controlled schema |
| Seller PII | Stored in dedicated fields, anonymizable on request (FR-021) | Anonymization replaces values, does not delete rows — preserves referential integrity across editions |
| Financial values | `BigDecimal` throughout — never `float`/`double` | Declared in CLAUDE.md; NFR-003 (cent-level accuracy) |
| Edition isolation | `edition_id` foreign key on all transactional entities | Items, sales, baskets, reports scoped to edition |
| Caching | None for v1 | Data volume is modest (~1,700 items); MariaDB on SSD/USB is sufficient |
| User language preference | `preferredLanguage` field on `User` entity (DB-backed, `enum {EN, FR}`) | FR-067 — stored on account, applied on login via ngx-translate; not browser-local |

---

### Concurrency — POS (Point of Sale)

| Decision | Choice | Rationale |
|---|---|---|
| Locking strategy | **Optimistic locking** (`@Version` on `Item` entity) | Low contention expected across 3 workstations; no held locks, no deadlocks |
| Safety net | DB `UNIQUE` constraint on sold item state | Secondary guarantee at the database level |
| Conflict scenario | Item manually entered into two baskets simultaneously | Detected at **payment validation** (not at scan time) |
| Conflict UX | Backend returns 409 with list of conflicting items; Angular displays explicit message; volunteer removes conflicting items and re-validates | No automatic retry — manual resolution by volunteer |
| Test requirement | Integration test with two concurrent `TransactionTemplate`s + **Testcontainers (MariaDB)** in CI | H2 locking behaviour differs from MariaDB; real DB required for this test |

---

### Phase Change Notification (FR-090)

| Decision | Choice | Rationale |
|---|---|---|
| Mechanism | **Server-Sent Events (SSE)** | Server-push only (no bidirectional need); simpler than WebSocket; `EventSource` auto-reconnects per RFC 8895; plain HTTP (no proxy issues on venue LAN) |
| Spring implementation | `SseEmitter` per connected client | Emitters managed in a thread-safe registry; closed on phase change after event is sent |
| Angular implementation | `EventSource` wrapped in an Angular service | Testable with `jest.fn()`; reconnect handled natively |
| Trigger | Phase transition (any direction) triggers SSE event to all connected clients | Volunteer's active basket cancelled if phase changes mid-transaction (FR-090) |

---

### Print Infrastructure

| Decision | Choice | Rationale |
|---|---|---|
| Queue implementation | **In-memory `LinkedBlockingQueue`** (one per printer type) | Simple, no extra infrastructure; at-most-once delivery acceptable |
| Delivery guarantee | At-most-once | Acceptable: all print jobs are re-triggerable from the UI (FR-078); data source always available in DB |
| Queue injection | Constructor-injected (not static) | Enables bounded queue in tests to verify backpressure behaviour |
| Error handling | Printer errors surface to UI via SSE or polling response (FR-079) | User notified; can retry manually |
| Thermal printer | ESC/POS via `escpos-coffee` (or equivalent) — sequential jobs | One queue, one consumer thread |
| A4/document printer | PDF generated by OpenPDF 3.0.0 → sent to USB printer | One queue, one consumer thread |

---

### API & Communication

| Decision | Choice | Rationale |
|---|---|---|
| API style | REST (JSON) | Standard, well-supported by Spring MVC and Angular HttpClient |
| API documentation | **Springdoc OpenAPI** (Apache 2.0) — enabled in `dev`, disabled in `prod` | Auto-generated from annotations; OpenAPI snapshot in CI catches contract regressions |
| Error handling | `@ControllerAdvice` + RFC 7807 Problem Details | Standardised error responses; machine-readable for Angular error handling |
| Validation | Bean Validation (`jakarta.validation`) on DTOs | Fail-fast at controller boundary |
| CORS | Configured for `localhost` only (single-server deployment) | No cross-origin requests from external domains |

---

### Frontend Architecture

| Decision | Choice | Rationale |
|---|---|---|
| State management | Angular Signals (no NgRx) | Declared in CLAUDE.md; reactive state without NgRx boilerplate; composable with `computed()` |
| Component model | Standalone components | Declared in CLAUDE.md (latest Angular pattern) |
| HTTP | Angular `HttpClient` | Standard; testable with `HttpClientTestingModule` |
| i18n | ngx-translate (runtime switching, no build-per-locale) | Declared in PRD addendum; JSON files `en.json` / `fr.json` |
| UI components | Angular Material (MIT) | Idiomatic Angular patterns; CDK Testing Harnesses for robust component tests |
| Scanner input | USB HID → keyboard events captured in Angular component | AZERTY/QWERTY handled via key code mapping (FR-034); no workstation config required |

---

### Infrastructure & Deployment

| Decision | Choice | Rationale |
|---|---|---|
| Deployment | Docker Compose (`docker-compose.yml`) — single file | Declared in PRD addendum; `docker compose up -d` / `docker compose pull && up -d` |
| Logging | SLF4J + Logback (Spring Boot default) — **no PII in logs** | NFR-007 + CLAUDE.md constraint; seller names, emails, phones never logged |
| Monitoring | None for v1 | Out of scope; Raspberry Pi target, single-event usage |
| CI/CD | None for v1 | Hobby/community project; updates applied manually |

---

### Decision Impact Analysis

**Implementation Sequence (suggested order):**
1. Project scaffolding (Spring Initializr + `ng new`) + Docker Compose baseline
2. Liquibase schema + core entities (Edition, Seller, Item, User)
3. Spring Security + Spring Session JDBC
4. i18n foundation (ngx-translate + Spring MessageSource)
5. Feature development (F2 → F3 → F4 → F5 → F6 → F7 → F9 → F10)

**Cross-Component Dependencies:**
- Phase state machine (F2) must be implemented before F3, F4, F5, F10 — it governs all business rules
- Spring Session JDBC requires Liquibase migration before any auth feature
- SSE emitter registry must be in place before phase transition endpoints (F2)
- Print queue consumers must be initialised as Spring beans before F3/F4 printing features
- Testcontainers (MariaDB) CI test required before F4 POS concurrency story ships

---

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Backend — Database**

| Element | Convention | Example |
|---|---|---|
| Table names | `snake_case`, plural | `seller_profiles`, `editions`, `items`, `print_jobs` |
| Column names | `snake_case` | `last_name`, `edition_id`, `is_complete` |
| Foreign keys | `{entity}_id` | `seller_id`, `edition_id` |
| Indexes | `idx_{table}_{column}` | `idx_items_edition_id` |

**Backend — Java**

| Element | Convention | Example |
|---|---|---|
| Package structure | `org.pluribourse.{feature}.{layer}` | `org.pluribourse.seller.service` |
| Feature packages | singular noun | `edition`, `seller`, `item`, `pos`, `payout`, `report`, `user`, `print` |
| Class names | `PascalCase` | `SellerService`, `ItemController` |
| Method/field names | `camelCase` | `findByEditionId`, `isComplete` |
| DTO suffix | `Dto` | `SellerDto`, `ItemDto` |
| Mapper suffix | `Mapper` | `SellerMapper`, `ItemMapper` |

**Backend — REST API**

| Element | Convention | Example |
|---|---|---|
| URL prefix | `/api/` — no versioning | `/api/sellers`, `/api/editions` |
| Resource names | `kebab-case`, plural | `/api/seller-profiles`, `/api/print-jobs` |
| Route parameters | `{id}` | `/api/sellers/{id}` |
| Query parameters | `camelCase` | `?editionId=1&sortBy=name` |

**Frontend — Angular**

| Element | Convention | Example |
|---|---|---|
| File names | `kebab-case` | `seller-list.component.ts`, `edition.service.ts` |
| Class names | `PascalCase` | `SellerListComponent`, `EditionService` |
| Signal names | `camelCase` | `sellers = signal([])`, `isLoading = signal(false)` |
| i18n keys | dot-notation, 3 levels max | `seller.list.empty`, `pos.basket.item-already-sold` |

---

### Structure Patterns

**Backend — Package Organisation**

```
org.pluribourse.
├── edition/
│   ├── controller/   EditionController.java
│   ├── service/      EditionService.java
│   ├── repository/   EditionRepository.java
│   ├── entity/       Edition.java
│   ├── dto/          EditionDto.java, CreateEditionDto.java
│   └── mapper/       EditionMapper.java
├── seller/           (same pattern)
├── item/             (same pattern)
├── pos/              (same pattern)
├── payout/           (same pattern)
├── report/           (same pattern)
├── user/             (same pattern)
├── print/            (same pattern)
└── shared/
    ├── exception/    GlobalExceptionHandler.java, BusinessException.java
    ├── security/     SecurityConfig.java, SessionConfig.java
    ├── sse/          SseEmitterRegistry.java
    └── config/       OpenApiConfig.java, JacksonConfig.java
```

**Frontend — Folder Organisation**

```
src/
├── app/
│   ├── components/
│   │   ├── edition/
│   │   ├── seller/
│   │   ├── pos/
│   │   ├── payout/
│   │   ├── report/
│   │   ├── catalog/
│   │   ├── user/
│   │   └── shared/
│   ├── services/
│   │   ├── edition.service.ts
│   │   ├── seller.service.ts
│   │   ├── pos.service.ts
│   │   ├── phase.service.ts      (SSE)
│   │   ├── print.service.ts
│   │   └── auth.service.ts
│   └── models/
│       ├── edition.model.ts
│       ├── seller.model.ts
│       ├── item.model.ts
│       └── page.model.ts         (Spring Page<T> shape)
└── assets/
    └── i18n/
        ├── en.json
        └── fr.json
```

---

### Format Patterns

**API Responses**

- **Simple response**: direct object or array — no wrapper
- **Paginated/filtered response**: Spring `Page<T>` — `{content: [...], page: {size, number, totalElements, totalPages}}`
- **Error response**: RFC 7807 Problem Details

```json
{
  "type": "https://pluribourse/errors/item-already-sold",
  "title": "Item Already Sold",
  "status": 409,
  "detail": "Item 'Lego City 60XXX' was already sold on another workstation.",
  "instance": "/api/pos/baskets/42/validate"
}
```

**Pagination — JPageFlow**

Standard tool for all paginated/filtered list endpoints:

```java
// Service pattern
Page<ItemDto> result = FilterService.filterData(
    itemRepository.findByEditionId(editionId),
    filterDto,
    items -> items.stream().map(itemMapper::toDto).toList()
);
```

> ⚠️ **Known issue**: `BigDecimal` sort (e.g. by price) is broken in JPageFlow v1.5.0 — comparator falls back to alphabetical string comparison. Fix required in library before price-sorting is implemented. Tests will fail until patched.

**Data Formats**

| Type | Format | Example |
|---|---|---|
| JSON field names | `camelCase` | `lastName`, `editionId` |
| Dates | ISO 8601 date | `"2026-06-09"` |
| Datetimes | ISO 8601 with Z | `"2026-06-09T14:30:00Z"` |
| Monetary values | JSON number (BigDecimal serialised) | `12.50` |
| Booleans | `true` / `false` | `"isComplete": false` |

---

### Communication Patterns

**SSE Events**

| Event | Name | Payload |
|---|---|---|
| Phase transition | `phase-changed` | `{editionId, newPhase, previousPhase}` |
| Basket cancelled | `basket-cancelled` | `{reason: "phase-changed"}` |

- Event names: `kebab-case`
- Payload: JSON
- Angular: `EventSource` wrapped in `PhaseService`, exposed as `Signal<Phase>`

**Angular State Management**

```typescript
// Signal pattern — local state
sellers = signal<Seller[]>([]);
isLoading = signal(false);

// Computed — derived state
sellerCount = computed(() => this.sellers().length);

// No NgRx — no stores, no actions, no reducers
```

---

### Process Patterns

**Error Handling**

- Backend: `@ControllerAdvice` catches all exceptions, returns RFC 7807
- `BusinessException` (runtime) for domain rule violations → mapped to 4xx
- Angular: HTTP errors caught in service, exposed via Signal or re-thrown to component
- No silent swallowing of errors — always surface to user or log

**Loading States**

```typescript
// Per-component pattern
isLoading = signal(false);

async loadSellers() {
  this.isLoading.set(true);
  try { ... }
  finally { this.isLoading.set(false); }
}
```

**Validation**

- Server-side: Bean Validation (`@NotNull`, `@Size`, etc.) on all DTOs — mandatory
- Client-side: Angular reactive form validators — convenience only, not trusted

**i18n Keys**

- Max 3 levels: `feature.section.key`
- Shared business terms aligned between `en.json` and `messages_en.properties`
- Examples: `seller.label`, `edition.phase.deposit`, `pos.basket.lot-incomplete`, `report.daily.title`

---

### Enforcement Guidelines

**All implementations MUST:**
- Use `FilterService.filterData()` (JPageFlow) for any paginated/filterable list endpoint
- Return RFC 7807 Problem Details for all error responses
- Use `BigDecimal` for all monetary values — never `float` or `double`
- Use ISO 8601 for all date/datetime serialisation
- Never log PII (seller name, email, phone) — use seller ID in logs
- Follow the `org.pluribourse.{feature}.{layer}` package structure
- Place Angular files under `components/`, `services/`, or `models/` accordingly
- Use `signal()` / `computed()` for Angular state — no imperative `BehaviorSubject` patterns
- Block all `SELLER`-role requests with 403 in v1 — `SecurityConfig.java` must deny all authenticated SELLER requests; no SELLER endpoints or UI until v2 portal

---

## Project Structure & Boundaries

### Repository Layout

```
PluriBourse/                          ← monorepo root
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── CLAUDE.md
├── pluribourse-backend/              ← Spring Boot module
└── pluribourse-frontend/             ← Angular module
```

---

### Backend — Complete Directory Structure

```
pluribourse-backend/
├── pom.xml
├── mvnw / mvnw.cmd
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/org/pluribourse/
    │   │   ├── PluriboursApplication.java
    │   │   ├── edition/                          ← F2
    │   │   │   ├── controller/  EditionController.java
    │   │   │   ├── service/     EditionService.java
    │   │   │   ├── repository/  EditionRepository.java
    │   │   │   ├── entity/      Edition.java, PhaseType.java
    │   │   │   ├── dto/         EditionDto.java, CreateEditionDto.java, PhaseTransitionDto.java
    │   │   │   └── mapper/      EditionMapper.java
    │   │   ├── seller/                           ← F3
    │   │   │   ├── controller/  SellerController.java
    │   │   │   ├── service/     SellerService.java
    │   │   │   ├── repository/  SellerRepository.java
    │   │   │   ├── entity/      SellerProfile.java
    │   │   │   ├── dto/         SellerDto.java, CreateSellerDto.java
    │   │   │   └── mapper/      SellerMapper.java
    │   │   ├── item/                             ← F3, F10
    │   │   │   ├── controller/  ItemController.java, ItemCatalogController.java
    │   │   │   ├── service/     ItemService.java, BarcodeService.java, LotService.java
    │   │   │   ├── repository/  ItemRepository.java, LotRepository.java
    │   │   │   ├── entity/      Item.java, Lot.java, Category.java, TableAssignment.java
    │   │   │   ├── dto/         ItemDto.java, CreateItemDto.java, LotDto.java, CatalogFilterDto.java
    │   │   │   └── mapper/      ItemMapper.java
    │   │   ├── pos/                              ← F4
    │   │   │   ├── controller/  BasketController.java
    │   │   │   ├── service/     BasketService.java, SaleService.java
    │   │   │   ├── repository/  BasketRepository.java, SaleRepository.java
    │   │   │   ├── entity/      Basket.java, BasketItem.java, Sale.java
    │   │   │   ├── dto/         BasketDto.java, ScanResultDto.java, ValidateBasketDto.java
    │   │   │   └── mapper/      BasketMapper.java
    │   │   ├── payout/                           ← F5
    │   │   │   ├── controller/  PayoutController.java
    │   │   │   ├── service/     PayoutService.java, SettlementService.java
    │   │   │   ├── repository/  SettlementRepository.java
    │   │   │   ├── entity/      Settlement.java
    │   │   │   ├── dto/         PayoutSummaryDto.java, SettleDto.java
    │   │   │   └── mapper/      PayoutMapper.java
    │   │   ├── report/                           ← F6
    │   │   │   ├── controller/  ReportController.java
    │   │   │   ├── service/     ReportService.java, PdfReportService.java
    │   │   │   └── dto/         DailySummaryDto.java, EditionSummaryDto.java, OutstandingSellerDto.java
    │   │   ├── user/                             ← F7
    │   │   │   ├── controller/  UserController.java
    │   │   │   ├── service/     UserService.java
    │   │   │   ├── repository/  UserRepository.java
    │   │   │   ├── entity/      User.java, Role.java  (ADMIN | VOLUNTEER | SELLER)
    │   │   │   ├── dto/         UserDto.java, CreateUserDto.java, ChangePasswordDto.java
    │   │   │   ├── mapper/      UserMapper.java
    │   │   │   └── cli/         AdminPasswordResetRunner.java  ← FR-063
    │   │   ├── print/                            ← F9
    │   │   │   ├── controller/  PrintController.java
    │   │   │   ├── service/     PrintQueueService.java, ThermalPrintService.java, DocumentPrintService.java
    │   │   │   └── dto/         PrintJobDto.java
    │   │   └── shared/
    │   │       ├── exception/        GlobalExceptionHandler.java, BusinessException.java
    │   │       ├── security/         SecurityConfig.java, SessionConfig.java
    │   │       ├── sse/              SseEmitterRegistry.java
    │   │       ├── instanceconfig/   ← FR-073
    │   │       │   ├── controller/   InstanceConfigController.java
    │   │       │   ├── service/      InstanceConfigService.java
    │   │       │   ├── repository/   InstanceConfigRepository.java
    │   │       │   ├── entity/       InstanceConfig.java
    │   │       │   ├── dto/          InstanceConfigDto.java
    │   │       │   └── mapper/       InstanceConfigMapper.java
    │   │       └── config/           OpenApiConfig.java, JacksonConfig.java
    │   └── resources/
    │       ├── application.properties
    │       ├── application-dev.properties
    │       ├── application-prod.properties
    │       ├── messages_en.properties            ← F1
    │       ├── messages_fr.properties            ← F1
    │       └── db/changelog/
    │           ├── db.changelog-master.xml
    │           ├── 001-core-schema.xml             (users.seller_profile_id nullable FK)
    │           ├── 002-spring-session.xml
    │           ├── 003-category-table-mapping.xml
    │           └── 004-instance-config.xml       ← FR-073
    └── test/
        └── java/org/pluribourse/
            ├── edition/     EditionControllerTest.java, EditionServiceTest.java
            ├── seller/      SellerServiceTest.java
            ├── item/        ItemServiceTest.java, BarcodeServiceTest.java
            ├── pos/         BasketServiceTest.java, SaleConcurrencyIT.java  ← Testcontainers
            ├── payout/      PayoutServiceTest.java
            ├── report/      ReportServiceTest.java
            └── shared/      SecurityConfigTest.java
```

---

### Frontend — Complete Directory Structure

```
pluribourse-frontend/
├── package.json
├── angular.json
├── tsconfig.json
└── src/
    ├── main.ts
    ├── index.html
    ├── styles.scss
    ├── app/
    │   ├── app.component.ts
    │   ├── app.config.ts             (HttpClient, TranslateModule, Angular Material)
    │   ├── app.routes.ts
    │   ├── components/
    │   │   ├── edition/              ← F2
    │   │   │   ├── edition-list.component.ts
    │   │   │   ├── edition-form.component.ts
    │   │   │   └── phase-controls.component.ts
    │   │   ├── seller/               ← F3
    │   │   │   ├── seller-search.component.ts
    │   │   │   ├── seller-form.component.ts
    │   │   │   └── item-form.component.ts
    │   │   ├── pos/                  ← F4
    │   │   │   ├── scanner.component.ts
    │   │   │   ├── basket.component.ts
    │   │   │   └── lot-warning.component.ts
    │   │   ├── payout/               ← F5
    │   │   │   ├── settlement-list.component.ts
    │   │   │   └── settlement-form.component.ts
    │   │   ├── report/               ← F6
    │   │   │   └── report-panel.component.ts
    │   │   ├── catalog/              ← F10
    │   │   │   └── item-catalog.component.ts
    │   │   ├── user/                 ← F7
    │   │   │   ├── user-list.component.ts
    │   │   │   └── user-form.component.ts
    │   │   ├── admin/                ← F8
    │   │   │   └── admin-settings.component.ts
    │   │   └── shared/
    │   │       ├── nav.component.ts
    │   │       ├── phase-banner.component.ts
    │   │       └── confirm-dialog.component.ts
    │   ├── services/
    │   │   ├── auth.service.ts
    │   │   ├── edition.service.ts
    │   │   ├── seller.service.ts
    │   │   ├── item.service.ts
    │   │   ├── pos.service.ts
    │   │   ├── payout.service.ts
    │   │   ├── report.service.ts
    │   │   ├── instance-config.service.ts
    │   │   ├── print.service.ts
    │   │   └── phase.service.ts      (SSE EventSource → Signal<Phase>)
    │   └── models/
    │       ├── edition.model.ts
    │       ├── seller.model.ts
    │       ├── item.model.ts
    │       ├── basket.model.ts
    │       ├── user.model.ts
    │       └── page.model.ts         (Spring Page<T> TypeScript shape)
    └── assets/
        └── i18n/
            ├── en.json               ← F1
            └── fr.json               ← F1
```

---

### Feature → Structure Mapping

| Feature | Backend packages | Frontend |
|---|---|---|
| F1 — i18n | `resources/messages_*.properties` | `assets/i18n/*.json`, `app.config.ts` |
| F2 — Éditions & lifecycle | `edition/` + `shared/sse/` | `components/edition/`, `services/phase.service.ts` |
| F3 — Vendeurs & articles | `seller/`, `item/`, `print/` | `components/seller/`, `services/seller+item` |
| F4 — POS | `pos/` | `components/pos/`, `services/pos.service.ts` |
| F5 — Post-vente & reversements | `payout/` | `components/payout/` |
| F6 — Reporting | `report/` (OpenPDF) | `components/report/` |
| F7 — Comptes utilisateurs | `user/` (+ `cli/AdminPasswordResetRunner`), `shared/security/` | `components/user/`, `services/auth.service.ts` |
| F8 (admin settings) — Config instance | `shared/instanceconfig/` | `components/admin/`, `services/instance-config.service.ts` |
| F8 — Infrastructure | `docker-compose.yml`, `application.properties`, Liquibase | — |
| F9 — Impression | `print/` (BlockingQueue + ESC/POS + OpenPDF) | `services/print.service.ts` |
| F10 — Catalogue articles | `item/controller/ItemCatalogController.java` | `components/catalog/` |

---

### Integration Boundaries

**API Boundary (Backend ↔ Frontend)**
- All communication via REST JSON over HTTP
- Base URL: `/api/`
- Auth: session cookie (Spring Security, `JSESSIONID` stored in MariaDB via Spring Session JDBC)
- SSE endpoint: `GET /api/sse/events` — phase change notifications
- Print endpoint: `POST /api/print/{type}` — triggers server-side print job

**Data Boundary (Service ↔ Repository)**
- Entities never leave the `service/` layer — always mapped to DTOs via MapStruct
- `FilterService.filterData()` (JPageFlow) operates on DTO lists, not entities

**Print Boundary**
- `PrintQueueService` owns two `LinkedBlockingQueue` instances (thermal / document)
- `ThermalPrintService` and `DocumentPrintService` are queue consumers running on dedicated threads
- No direct print call from controllers — always via `PrintQueueService`

**Data Flow — POS Sale**
```
Angular scanner.component
  → POST /api/pos/baskets/{id}/items (scan)
  → BasketController → BasketService
  → ItemRepository (check sold status)
  ← ScanResultDto (item added or error)

Angular basket.component
  → POST /api/pos/baskets/{id}/validate
  → BasketController → SaleService
  → Item @Version optimistic lock → Sale persisted
  ← 200 OK or 409 (conflict list)
  → POST /api/print/invoice (optional)
  → PrintQueueService → DocumentPrintService
```

**Data Flow — Phase Transition**
```
Admin phase-controls.component
  → PUT /api/editions/{id}/phase
  → EditionController → EditionService
  → Edition.phase updated
  → SseEmitterRegistry.broadcast("phase-changed", payload)
  → All connected EventSource clients receive event
  → Angular phase.service updates Signal<Phase>
  → Components react (basket cancelled if active)
```

---

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
All technology choices are mutually compatible. Spring Boot 4.0.6 / Java 21 / Angular 21 / MariaDB / OpenPDF 3.0.0 / ZXing / Liquibase / MapStruct / Lombok operate without conflicts. One runtime note: JPageFlow declares `spring-data-commons:3.5.5` as a dependency; Spring Boot 4.0.6's BOM overrides this to Spring Data 4.x at runtime — no conflict expected, but verify on first build.

**Pattern Consistency:**
Implementation patterns are fully aligned with architectural decisions: JPageFlow for all paginated endpoints, RFC 7807 for all errors, SSE for phase notification, Signals for Angular state, BigDecimal for all monetary values.

**Structure Alignment:**
Project structure supports all architectural decisions. Layered backend (Controller → Service → Repository) enforced via package layout. Angular type-based structure (`components/`, `services/`, `models/`) consistent with standalone component model.

---

### Requirements Coverage Validation ✅

**Feature Coverage:**

| Feature group | Covered by |
|---|---|
| F1 — i18n | `messages_*.properties` + `assets/i18n/*.json` + ngx-translate |
| F2 — Edition lifecycle | `edition/` + `shared/sse/` + `phase.service.ts` |
| F3 — Seller & items | `seller/`, `item/`, `print/` |
| F4 — POS | `pos/` + optimistic locking + SSE basket cancellation |
| F5 — Post-sale | `payout/` |
| F6 — Reporting | `report/` + OpenPDF 3.0.0 |
| F7 — Users & auth | `user/` + Spring Security + Spring Session JDBC |
| F8 — Infrastructure | Docker Compose + Liquibase + `application.properties` |
| F9 — Print | `print/` + `LinkedBlockingQueue` + ESC/POS + OpenPDF |
| F10 — Catalog | `item/controller/ItemCatalogController` + JPageFlow |

**Non-Functional Requirements:**

| NFR | Addressed by |
|---|---|
| NFR-001 Performance (RPi 4) | Java 21 virtual threads; lean stack; no caching complexity |
| NFR-002 Concurrency | Optimistic locking (`@Version`) + DB unique constraint + Testcontainers CI |
| NFR-003 Financial accuracy | BigDecimal policy — enforced at pattern level |
| NFR-004 Browser compatibility | REST + SPA; no browser-specific APIs |
| NFR-005 Scanner compatibility | Angular key-code mapping in `scanner.component.ts` |
| NFR-006 Reliability | Server-side transactions; Spring Session JDBC (sessions survive restart) |
| NFR-007 GDPR | Anonymization on seller delete; no PII in logs — enforced in patterns |

---

### Gap Analysis Results

**Gaps identified and resolved during validation:**

| Gap | Priority | Resolution |
|---|---|---|
| Missing `InstanceConfig` entity (FR-073) | Critical | Added `shared/instanceconfig/` package + Liquibase `004-instance-config.xml` |
| Admin password reset CLI (FR-063) | Important | Added `user/cli/AdminPasswordResetRunner.java` — Spring Boot `CommandLineRunner` triggered via `--reset-admin-password` arg |

**Remaining known issue (deferred):**

| Item | Status | Action |
|---|---|---|
| JPageFlow `BigDecimal` sort bug | Deferred — known | Fix in library before implementing price-based sort; test will fail until patched |

---

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed (~100 sellers, ~1,700 items, 3 POS workstations)
- [x] Technical constraints identified (RPi 4, Docker Compose, license policy)
- [x] Cross-cutting concerns mapped (phase state machine, i18n, GDPR, concurrency, print queue)

**Architectural Decisions**
- [x] Critical decisions documented with versions (Java 21, Spring Boot 4.0.6, Angular 21, OpenPDF 3.0.0)
- [x] Technology stack fully specified
- [x] Integration patterns defined (SSE, REST, JPageFlow, Spring Session JDBC)
- [x] Performance considerations addressed (virtual threads, in-memory queue, no caching)

**Implementation Patterns**
- [x] Naming conventions established (snake_case DB, camelCase JSON, kebab-case Angular files)
- [x] Structure patterns defined (feature sub-packages backend, type-based Angular)
- [x] Communication patterns specified (SSE events, RFC 7807 errors, JPageFlow pagination)
- [x] Process patterns documented (loading states, error handling, validation, i18n keys)

**Project Structure**
- [x] Complete directory structure defined (backend + frontend)
- [x] Component boundaries established (shared/, feature packages, print boundary)
- [x] Integration points mapped (API boundary, SSE, print queue, data flows)
- [x] Requirements to structure mapping complete (F1–F10 + NFR-001–007)

---

### Architecture Readiness Assessment

**Overall Status: READY FOR IMPLEMENTATION**

**Confidence level: High**

**Key strengths:**
- "Boring by design" stack — well-documented, maintainable, no exotic dependencies
- All licenses permissive or weak-copyleft (MIT, Apache 2.0, LGPL) — no AGPL
- Concurrency model explicitly tested via Testcontainers
- Print queue failure mode documented and accepted
- Phase state machine is the architectural spine — all features hang off it clearly

**Areas for future enhancement (post-v1):**
- JPageFlow BigDecimal fix (patch before price-sort feature)
- Testcontainers CI pipeline (can be added incrementally)
- Backup/restore mechanism (explicitly deferred to v2)
- Per-edition commission override (out of scope v1)

---

### Implementation Handoff

**First implementation story:** Project scaffolding
```bash
# Backend
spring init --boot-version=4.0.6 --java-version=21 --build=maven \
  --group-id=org.pluribourse --artifact-id=pluribourse \
  --dependencies=web,data-jpa,security,liquibase,lombok,validation,mariadb

# Frontend
ng new pluribourse-frontend --standalone --routing --style=scss
```

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented — no local optimisations
- Use `FilterService.filterData()` (JPageFlow) for every paginated/filterable endpoint
- Never use `float` or `double` for monetary values — `BigDecimal` only
- Never log PII — use entity IDs in logs
- Return RFC 7807 Problem Details for all error responses
- Follow `org.pluribourse.{feature}.{layer}` package structure strictly
- Use `signal()` / `computed()` for Angular state — no `BehaviorSubject`
- Refer to this document for all architectural questions before making local decisions
