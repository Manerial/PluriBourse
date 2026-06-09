---
title: "PRD: PluriBourse v1"
status: final
created: 2026-06-08
updated: 2026-06-09
---

# PRD: PluriBourse v1

## Problem Statement

Associations organizing secondhand sale events (toys, books, skis, clothing, and more) face one of two situations: they manage everything manually on paper or spreadsheets, or they rely on existing software that nobody can maintain.

In the first case, manual management does not scale: registering sellers, labeling items, running multi-workstation sales, and calculating payouts become unmanageable beyond a certain volume.

In the second case, the software works — until it breaks. Hardcoded file paths fail with any infrastructure change. Absent documentation leaves the original author as the sole person capable of diagnosing failures. Every edition carries the risk that a routine system update or a new machine silently breaks the tool, with no safe way to fix it under event-day pressure.

In both cases, the cost is the same: a fragile event, volunteers under pressure, and an association that cannot focus on what matters.

---

## Vision & Goals

**Vision**

PluriBourse is the reference platform for associations organizing secondhand sales — accessible to anyone who can download a file and follow a guide. Self-hosted, product-agnostic, subscription-free, with no external dependencies: each association owns its instance and its data outright.

The installation guide is a product in its own right — it is what turns a working codebase into something an association treasurer can deploy on a Saturday afternoon.

**Goals**

| ID | Goal | Direction Indicator |
|---|---|---|
| G1 | Cover the complete event lifecycle | All three phases (Deposit → Sale → Post-sale) work end-to-end without technical intervention |
| G2 | Run on modest hardware | Runs on Raspberry Pi 4 (2 GB RAM) without noticeable degradation under event load |
| G3 | Deployable by a non-technical user | An association can install and configure the platform without a developer, using the guide alone |
| G4 | Support multiple independent associations | Each instance is isolated; the model is designed for replication |
| G5 | Maintainable by the community | Standard stack, well-documented, no exotic dependencies |

---

## Users & Roles

| Role | Access | Notes |
|---|---|---|
| **Administrator** | Full | Phase management, commission, editions, volunteer accounts, reports |
| **Volunteer** | Deposit + Cashier + Settlement | Single role; interface adapts to the active phase. Non-technical users operating under event-day pressure |
| **Seller** | Out of application | Paper documents only — deposit slip at drop-off, sales summary at collection |

> An admin cannot operate as a volunteer from their admin account. To handle cashier or deposit duties, the admin creates a dedicated volunteer account.

---

## Scope

### In — v1

**Internationalisation**
- UI in English and French: language configured per user account
- Printed documents in English or French: language configured at instance level

**Event Management**
- Free-form edition naming; multiple editions per year supported
- Admin-controlled phase lifecycle: Deposit → Sale → Post-sale → Closed
- Confirmation dialog required for every phase transition (forward or backward)
- Rollback available one phase at a time; data always preserved
- Optional post-closure "Clean Edition" action: permanently deletes item records and disables rollback
- Configurable commission rate per instance (default 20%)

**Seller & Product Management**
- Seller profiles persistent across editions (last name, first name, email, phone)
- Item registration: name, price, category, complete/incomplete flag
- Table auto-assigned from category-to-table mapping configured per edition
- Lot support: indivisible bundles at a single global price, one label per item
- Code 128 barcode generation and label printing on 57mm thermal adhesive roll
- Deposit slip printing per seller

**Point of Sale**
- Cashier interface with USB HID barcode scanner support (AZERTY/QWERTY transparent)
- Shopping basket: multiple items per transaction, one global buyer invoice
- Buyer invoice printing on demand

**Post-Sale**
- Seller settlement: volunteer enters cash amount, clicks "Settle"
- "Not collected" button: full amount owed transferred to association revenue
- Sales summary per seller: sold items, unsold items with table location, net payout

**Reporting**
- Daily summary (PDF, admin only)
- Edition summary (PDF, admin only, generated at edition close)
- Outstanding sellers report (unsettled sellers with phone numbers)

**Item Catalog**
- Filterable, sortable catalog of all items in the active edition (accessible to admin and volunteers)
- Manual basket entry from catalog (fallback for unreadable barcodes)

**Infrastructure & Access**
- Admin and Volunteer roles, strictly separated
- Multi-workstation support (minimum 3 simultaneous)
- Docker Compose deployment (Spring Boot + MariaDB)
- Two USB printers connected to server: thermal (labels) + standard (documents)
- Centralized print endpoint — no printer required on client workstations
- Installation guide for non-technical users, with OS-specific instructions (Linux, macOS, Windows)

### Out — v1
- Integrated payment processing
- Seller self-service portal or email/SMS notifications
- Mobile application
- Multi-tenant SaaS hosting
- Data migration from legacy tools
- Read-only reporting role
- Data backup/restore mechanism
- Per-edition commission rate override

---

## Features

### F1 — Internationalisation (EN/FR)

*Cross-cutting foundation — to be implemented before and in parallel with all other features.*

| ID | Requirement |
|---|---|
| FR-001 | The user interface is available in English and French. |
| FR-002 | The default UI language is detected from the browser on first access and stored in the user account preferences. |
| FR-003 | Each user can change their language preference in account settings. |
| FR-004 | All UI text is externalized — no UI text is hardcoded in the source code. |
| FR-005 | The language of all printed documents (deposit slips, buyer invoices, sales summaries, reports) is configured at the instance level by the admin. |
| FR-006 | The document language setting is instance-wide and applies to all editions. |
| FR-007 | The document language setting is modifiable by the admin at any time. |

---

### F2 — Edition Management & Event Lifecycle

| ID | Requirement |
|---|---|
| FR-008 | The admin can create an edition with a free-form name (e.g. "Bourse de printemps 2026", "Vide-grenier novembre"). |
| FR-009 | Multiple editions can be created per year. |
| FR-010 | Only one edition can be active at a time. |
| FR-011 | Every phase transition — forward or backward — requires explicit admin confirmation via a dialog. |
| FR-012 | The active phase of the current edition is displayed clearly to all connected users. |
| FR-013 | The admin triggers edition closure via a "Close Edition" button in Post-sale phase. All documents are generated as PDFs in both languages (EN and FR). The edition becomes read-only. Item records remain in the database until the admin explicitly triggers the Clean action. |
| FR-088 | Post-closure, the admin can trigger a **"Clean Edition"** action that permanently deletes item records from the database. After cleaning, rollback to Post-sale is permanently disabled for this edition. This action requires explicit confirmation. |
| FR-014 | An archived edition cannot be deleted. |
| FR-015 | Each edition's data is strictly isolated — items, sales, and reports never bleed across editions. |
| FR-016 | The commission rate is configured at instance setup (default 20%) and is modifiable by the admin until the Deposit phase starts. Once the Deposit phase is active, the rate is frozen for that edition. It applies to all items in the edition. |
| FR-080 | When creating a new edition, the admin can either configure categories and the category-to-table mapping from scratch, or copy the structure from an existing edition. |
| FR-082 | The admin can roll back the active phase one step at a time: Closed → Post-sale, Post-sale → Sale, Sale → Deposit. Data recorded in the rolled-back phase is preserved — nothing is deleted. Rollback from Closed is only available before the Clean Edition action has been triggered (FR-088). |

---

### F3 — Seller & Product Management (Deposit Phase)

#### Admin Pre-configuration

| ID | Requirement |
|---|---|
| FR-017 | The admin configures the list of item categories per edition. |
| FR-018 | The admin configures the category-to-table mapping per edition (e.g. board games → tables 1, 2, 3; books → tables 4, 5). Tables are identified by number. |

#### Seller Registration

| ID | Requirement |
|---|---|
| FR-019 | Seller profiles persist across editions. Mandatory fields: last name, first name, email, phone number. |
| FR-020 | The volunteer searches for an existing seller by name or email. If not found, a new profile is created. |
| FR-021 | The admin can delete a seller profile (GDPR right to erasure). Deletion anonymizes last name, first name, email, phone number, and item descriptions across all editions. Product categories are retained. |

#### Item Registration

| ID | Requirement |
|---|---|
| FR-022 | For each item, the volunteer enters: name/description, price, category, complete/incomplete flag, and a comment if incomplete. |
| FR-023 | The table is automatically assigned by the system based on the edition's category-to-table mapping. |
| FR-024 | An item can be corrected or deleted only during the Deposit phase. |
| FR-025 | The complete/incomplete flag and its comment are modifiable in any phase. |

#### Lots

| ID | Requirement |
|---|---|
| FR-043 | A volunteer can create a lot by assigning a name and a global price, then adding multiple items to it. |
| FR-044 | Each item in a lot has its own name/description and receives its own label. |
| FR-045 | A lot item label displays, in addition to standard fields: "Lot price: X€" in place of an individual price, and "Indivisible lot: X/N" (X = item position, N = total items in lot). |

#### Printing

| ID | Requirement |
|---|---|
| FR-026 | A unique Code 128 barcode is generated server-side for each registered item. |
| FR-027 | The item label displays centered: Code 128 barcode graphic, human-readable barcode number, item name (wraps to multiple lines if needed), price, category, table number, incompleteness indicator if applicable. The seller name does not appear (GDPR). |
| FR-028 | The system triggers label printing automatically when a volunteer validates a seller's deposit. |
| FR-029 | Print jobs are queued server-side and executed sequentially. |
| FR-030 | The printed roll follows this format per seller: [seller separator: seller name + edition] → [item label] → [item separator] → [item label] → … |
| FR-031 | A deposit slip is printable per seller: item list, unit prices, and expected net payout after commission. |
| FR-032 | The thermal ticket width is configurable in admin settings (default: 57mm). |

---

### F4 — Point of Sale (Sale Phase)

| ID | Requirement |
|---|---|
| FR-033 | The cashier interface allows sales via USB HID barcode scanner. |
| FR-034 | The scan component handles AZERTY/QWERTY keyboard layout differences transparently via key code mapping — no workstation configuration required. |
| FR-035 | Each scanned item is added to the current buyer's basket. The system displays the item name and price. |
| FR-036 | Scanning an already-sold item displays an explicit error message. The item is not added to the basket. |
| FR-037 | Scanning an incomplete item displays an informative warning to the cashier, including the detail of what is missing. The item can still be sold. |
| FR-038 | The cashier can remove one or more individual items from the basket before payment validation. |
| FR-039 | Payment validation marks all basket items as sold and closes the transaction. No modification is possible after this step — no returns or exchanges. |
| FR-040 | After validation, a buyer invoice is printable on demand via the centralized print endpoint. |
| FR-041 | The invoice displays: item list, unit prices, total, association name, edition name, date. A lot appears as a single line (lot name, lot price). |
| FR-042 | The application supports a minimum of 3 simultaneous cashier workstations without data conflicts. The effective limit depends on server configuration. |

#### Lots at POS

| ID | Requirement |
|---|---|
| FR-046 | Scanning an item belonging to a lot displays the lot name in red with a "X/N scanned" counter. |
| FR-047 | The system blocks payment validation until the lot is complete (all N items scanned). |
| FR-048 | Once complete, the lot is sold at its global lot price. |
| FR-081 | If a cashier cannot complete a lot (item not found), they can remove the entire lot from the basket. All already-scanned items of the lot are removed. |

---

### F5 — Post-Sale & Payouts

| ID | Requirement |
|---|---|
| FR-049 | In Post-sale phase, a **sales summary** ("Bilan de vente") is printable per seller. |
| FR-050 | The sales summary contains: sold items (name, unit price), unsold items (name, category, table number), gross total, commission deducted, net amount to pay out. A lot appears as a single line (lot name, lot price). |
| FR-051 | To settle a seller, the volunteer enters the cash amount handed over and clicks "Settle". The seller's status changes to **Settled**. |
| FR-052 | If a seller does not wish to collect their payout, a **"Not collected"** button enters the full amount owed into the association's revenue. |
| FR-053 | Unsettled sellers are identifiable in the application, with their phone number visible for contact. |

---

### F6 — Reporting

| ID | Requirement |
|---|---|
| FR-054 | A **daily summary** is generatable by the admin at any time during the Sale phase. It covers the current calendar day. It contains: number of items sold/unsold for the day, daily revenue, daily commission earned by the association. |
| FR-055 | An **edition summary** is generated at edition closure. It contains: total items sold/unsold, total gross revenue, total commission earned by the association. |
| FR-056 | An **outstanding sellers report** lists unsettled sellers with their phone number. |
| FR-057 | All reports are generated as PDF. |
| FR-058 | Reports are accessible to the admin only. |
| FR-059 | Archived editions display aggregate metrics and seller profiles in read-only mode. Item-level detail is accessible only through the PDF documents generated at closure. |

---

### F7 — User Accounts & Access Control

| ID | Requirement |
|---|---|
| FR-060 | The admin creates, modifies, and deactivates volunteer accounts. The admin can reset a volunteer's password. |
| FR-061 | There is one admin account per instance. |
| FR-062 | At first launch, the admin account is initialized with Admin/Admin credentials. The admin is forced to change their password on first login. |
| FR-063 | If the admin loses their password, a command run on the server generates a temporary password. The admin is forced to change it on next login. |
| FR-064 | Admin and Volunteer roles are strictly separated. An admin cannot access volunteer interfaces from their admin account. |
| FR-065 | The volunteer interface adapts to the active phase: deposit in Deposit phase, cashier in Sale phase, settlement in Post-sale phase. In Post-sale phase, the volunteer can print a seller's sales summary to group their unsold items before handover. |
| FR-066 | Sessions do not expire automatically. |
| FR-067 | Each account stores a UI language preference (EN/FR), detected from the browser on account creation, modifiable in account settings. |

---

### F8 — Infrastructure & Deployment

| ID | Requirement |
|---|---|
| FR-068 | The server runs on Linux, macOS, and Windows without code changes. |
| FR-069 | Minimum specification: Raspberry Pi 4 (2 GB RAM) or equivalent 64-bit machine. SSD/USB storage strongly recommended — microSD is unreliable for database writes under event load. |
| FR-070 | The application is deployed via Docker Compose (Spring Boot app + MariaDB) — a single `docker-compose.yml` file. Data is stored in persistent Docker volumes. |
| FR-071 | Updates are applied with two commands: `docker compose pull && docker compose up -d`. Persistent data is preserved. |
| FR-072 | Client workstations access the application via browser — no local installation required on workstations. |
| FR-073 | An admin settings page centralizes instance configuration: association name, commission rate, document language, thermal ticket width. |
| FR-074 | The installation guide is exhaustive and targets non-technical users. It covers: Docker installation, startup, initial configuration, admin password reset procedure, and update procedure. Instructions are provided per OS (Linux, macOS, Windows) — commands and procedures are platform-specific. |

---

### F9 — Print Infrastructure

| ID | Requirement |
|---|---|
| FR-075 | All printing is routed through the central server — no printer is required on client workstations. |
| FR-076 | **Thermal printer** (item labels): connected to the server via USB. Ticket width: see FR-032. See FR-029 for print queue behavior. |
| FR-077 | **Standard printer** (A4 documents): connected to the server via USB. PDF generated server-side, sent directly to the printer without preview. |
| FR-078 | A user triggers printing from the interface; the request is processed by the server with no action required on the client workstation. |
| FR-079 | In case of a print error (printer offline, paper jam, out of paper), the user is notified in the interface with an explicit message. |

---

### F10 — Item Catalog

*Available during all phases of the active edition.*

| ID | Requirement |
|---|---|
| FR-083 | A filterable, sortable item catalog is accessible to both admin and volunteers during all phases of the active edition. |
| FR-084 | The catalog can be filtered by: name/description, barcode number, category, table, sold/unsold status, complete/incomplete flag, seller name. |
| FR-085 | The catalog can be sorted by any visible column. |
| FR-086 | The catalog displays items from the active edition only. Item-level data is not available on editions where the Clean action has been triggered. |
| FR-087 | During the Sale phase, a volunteer can add an item from the catalog directly to the current basket — fallback for unreadable or damaged barcodes. The system prevents adding an item that is already sold or already present in the current basket. |
| FR-089 | Commission applies normally to items sold with an incomplete flag. The sale price and commission rate are unchanged by the item's completeness status. |
| FR-090 | If the admin triggers a phase transition while a volunteer has an active basket, the system cancels the basket and displays an explicit error message to the volunteer. |

---

## Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-001 | Performance | The application is usable on a Raspberry Pi 4 (2 GB RAM) without noticeable degradation under event load (3 simultaneous workstations, ~1,700 items). |
| NFR-002 | Concurrency | Simultaneous operations from multiple workstations (scanning, data entry, printing) do not generate data conflicts. |
| NFR-003 | Financial Accuracy | Payout calculations (price − commission) are accurate to the cent for each seller and for edition totals. |
| NFR-004 | Browser Compatibility | The interface works on any modern browser (Chrome, Firefox, Edge, Safari) on any OS. |
| NFR-005 | Scanner Compatibility | USB HID scanners function without configuration, regardless of the workstation keyboard layout (AZERTY/QWERTY). |
| NFR-006 | Reliability | No data loss occurs on unexpected browser close or client workstation failure. |
| NFR-007 | GDPR | Seller personal data (last name, first name, email, phone) is deletable on request. Anonymized data in archived editions does not allow re-identification. |

---

## Success Metrics

| ID | Success Metric | Counter-Metric |
|---|---|---|
| SM-1 | 3 cashier workstations operate simultaneously without data conflicts | No noticeable latency on the cashier side due to locks or synchronization |
| SM-2 | Payout calculations match manual verification to the cent | No commission applied incorrectly on a lot or an incomplete item |
| SM-3 | Thermal labels scan reliably with a standard USB scanner | Total print time for a complete seller deposit does not exceed 2 minutes |
| SM-4 | All PDF documents print correctly from any workstation OS (Linux, macOS, Windows) | No truncated or misformatted document depending on the triggering workstation's OS |
| SM-5 | Admin opens and closes phases without incident, with clear state feedback | No accidental phase transition due to an ambiguous interface |
| SM-6 | Server runs without noticeable degradation on Raspberry Pi 4 (2 GB RAM) under event load | Memory usage does not exceed 80% under normal event conditions |
| SM-7 | A non-technical user installs and configures the instance alone, guide in hand, without developer assistance | The guide requires no prior knowledge of Docker or the command line beyond the literal instructions |
