---
title: "Product Brief: PluriBourse"
status: final
created: 2026-06-08
updated: 2026-06-08
---

# Product Brief: PluriBourse

## Executive Summary

PluriBourse is a self-hosted, web-based event management platform for associations organizing secondhand sale events — bourse aux jouets, livres, skis, vêtements, or any product type. It covers the full event lifecycle: seller registration, product cataloging with barcode label generation, multi-workstation point-of-sale scanning, and automated seller payout calculation.

The platform replaces fragile legacy tools with a clean, maintainable system designed to run across multiple workstations via a central server. Each association hosts its own instance, manages its own events under freely named editions, and configures its own commission rate. A detailed installation guide for non-technical users makes deployment accessible without dedicated IT support.

Built with Spring Boot and Angular, PluriBourse is designed to fit any association running any type of secondhand sale, multiple times per year if needed.

## The Problem

Associations organizing bourse events may already have working tools — multi-workstation support, volunteer traceability, automatic payout reconciliation. The problem is not what the software does: it is whether anyone can maintain it.

Hardcoded file paths mean any infrastructure change breaks the software. Poor or absent documentation means only the original author can diagnose failures. Every edition runs with the risk that a routine system update or a new machine silently breaks the tool, with no safe way to fix it under event-day pressure.

The cost is not lost features — it is fragility. A tool no one can maintain is a liability, not an asset.

## The Solution

PluriBourse structures each event into three administrator-controlled phases:

**1. Deposit phase** — Volunteers register sellers (creating new profiles or retrieving existing ones by name and email) and catalog each item with a price, category, and table assignment. The app generates Code 128 barcodes, prints adhesive labels on standard label sheets, and produces a deposit slip for each seller listing their items, prices, and expected net payout after commission.

**2. Sale phase** — Cashiers at up to three simultaneous workstations use USB barcode scanners to record sales. Items are automatically flagged as sold. Buyer invoices are printable via a centralized print endpoint — no printer required at each workstation.

**3. Post-sale phase** — Per-seller payout documents are generated. When a seller returns to collect their money and unsold items, a volunteer marks them as collected. Reports flag sellers who have not yet returned.

Seller profiles persist across editions so returning sellers are found by name or email without re-entry. Each edition carries a free-form name (e.g. "Bourse de printemps 2026", "Vide-grenier novembre") and is scoped independently — sales, inventory, and reports never bleed across editions.

## Who This Serves

**Administrator (event organizer):** Full platform access. Controls phase transitions, configures the commission rate, manages volunteer accounts, creates and names editions, and generates all reports.

**Volunteers (bénévoles):** Individual accounts for basic traceability. Operate both deposit registration and cashier functions at different phases. Interface designed for speed and simplicity — non-technical users working under event-day pressure.

**Associations:** Any association running secondhand sale events. Because the platform is self-hosted, each association owns its data and its instance outright — no subscription, no external dependency, no shared infrastructure.

**Sellers:** Do not access the application. They interact via paper documents only — a deposit slip at drop-off, a payout document and their unsold items at collection.

## Scope

**In for v1:**

*Event management*
- Free-form edition naming; multiple editions per year supported
- Admin-controlled event phase lifecycle: Deposit → Sale → Post-sale (no rollback)
- Configurable commission rate per instance (admin settings, default 20%)

*Seller & product management*
- Seller profile management with cross-edition persistence (name, first name, email)
- Product registration: price, category, table assignment, per-seller
- Code 128 barcode generation + label printing on standard adhesive sheets
- Deposit slip printing per seller (items, prices, expected net payout)

*Point of sale*
- POS interface with USB barcode scanner support
- Buyer invoice printing

*Post-sale*
- Seller collection tracking: flag seller as having retrieved payout and unsold items
- Per-seller payout document
- Per-seller unsold item return list (item descriptions + table location)

*Reporting*
- Daily summary: sold/unsold counts, total revenue, association commission earned
- Outstanding collections report (sellers who have not returned)

*Infrastructure & access*
- User accounts: Admin and Bénévole roles
- Multi-workstation support via central server (up to 3 simultaneous)
- Centralized print endpoint (all workstations print via server; one shared printer)
- Detailed installation guide for non-technical users

**Explicitly out of v1:**
- Integrated payment processing
- Seller self-service portal or email/SMS notifications
- Mobile application
- Multi-tenant SaaS hosting (each association self-hosts its own instance)
- Data migration from legacy tools (fresh installation per deployment)

## Success Criteria

- All three workstations operate simultaneously without data conflicts
- Payout calculations match manual verification to the cent
- Barcode labels scan reliably with standard USB scanners off standard label sheets
- All documents (deposit slips, invoices, reports) print correctly via the central endpoint from any workstation OS (Linux, macOS, Windows)
- Admin can open and close phases without incident, with clear state feedback
- The server runs acceptably on minimum-spec hardware: Raspberry Pi 4 (2 GB RAM) or equivalent — any 64-bit machine with 2 GB RAM and SSD/USB storage
- The server installs and runs on Linux, macOS, and Windows without code changes
- A non-technical user can install and configure the platform by following the installation guide alone, without developer assistance

## Technical Context

- **Stack:** Spring Boot (backend) + Angular (frontend)
- **Architecture:** Self-hosted, central server, browser-based clients (no local install on workstations)
- **Deployment:** One instance per association; cross-platform (Linux, macOS, Windows); installation guide targets non-technical users
- **Minimum spec:** Raspberry Pi 4 (2 GB RAM) or equivalent 64-bit machine; SSD/USB storage strongly recommended (microSD unreliable for database writes under event load)
- **Workstations:** Up to 3 simultaneous; any OS with a modern browser
- **Barcode scanner compatibility:** USB HID scanners output as keyboard input; the POS scan component handles OS keyboard layout mismatches (AZERTY/QWERTY) transparently via layout-independent key code mapping — no workstation configuration required
- **Printing:** Centralized endpoint on the server; one shared printer
- **Barcodes:** Code 128, generated server-side, printable on standard adhesive label sheets
- **Scale:** ~100 sellers, ~1,700 items per edition; multiple editions per year supported

## Vision

PluriBourse starts as a tool built for one association's specific needs. Its self-hosted model, configurable commission, free-form edition naming, and product-agnostic design are intentional foundations for a wider reach: any association running any type of secondhand sale event can download, install, and run it independently.

The installation guide is as much a product feature as the software itself — it is what turns a working codebase into something an association treasurer can actually deploy on a Saturday afternoon.
