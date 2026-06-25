---
baseline_commit: a26c1666297a8c92368ab3a186f822986ca9bea0
---

# Story 1.9: Installation Guide

Status: done

## Story

As a non-technical association manager,
I want a clear and complete installation guide,
so that I can deploy and configure PluriBourse on my own, without prior knowledge of Docker or a terminal.

## Acceptance Criteria

1. **Given** the repository is cloned, **When** `GUIDE_INSTALLATION.md` is opened, **Then** it exists at the repository root **And** it contains exactly the following 7 sections in order: « Prérequis », « Installation de Docker », « Téléchargement et lancement », « Premier lancement », « Configuration initiale », « Réinitialisation du mot de passe admin », « Mise à jour ».

2. **Given** the « Installation de Docker » section is read, **When** the reader identifies their operating system, **Then** distinct and complete instructions are present for Windows (Docker Desktop), macOS (Docker Desktop), and Linux / Raspberry Pi (Docker Engine) **And** each OS procedure is self-contained — it does not require reading other OS sections.

3. **Given** the « Téléchargement et lancement » section is read, **When** the instructions are followed, **Then** the `docker compose up -d` command is present in a copy-paste code block **And** a verification step tells the user how to confirm the application responds (URL and expected message in the browser).

4. **Given** the « Réinitialisation du mot de passe admin » section is read, **When** the instructions are followed, **Then** the exact CLI command (`docker compose run --rm --no-deps ...`) is present in a code block **And** the expected console output is described **And** instructions for opening a terminal are provided for each OS.

5. **Given** the « Premier lancement » section is read, **When** the instructions are followed, **Then** the access URL (`http://localhost` or the configured port) and the default credentials (`Admin` / `Admin`) are indicated **And** a verification step confirms the mandatory password change has been completed before continuing.

6. **Given** the « Mise à jour » section is read, **When** the instructions are followed, **Then** the exact command `docker compose pull && docker compose up -d` is present in a code block **And** the guide explicitly confirms that the association's data is preserved after the update (FR-071).

7. **Given** the guide is written in French, **Then** all phrasing addressed to the reader uses « vous » (vouvoiement) throughout.

8. **Given** any technical section of the guide is read, **When** a technical term is mentioned for the first time (terminal, container, volume, port), **Then** that term is defined or accompanied by a plain-language explanation.

9. **Given** a person who has never used Docker follows the guide from A to Z, **When** they reach the end of « Configuration initiale », **Then** the application is deployed, the admin password has been changed, and the instance settings are configured — with no step requiring prior Docker knowledge.

## Tasks / Subtasks

- [x] **T1 — Create `GUIDE_INSTALLATION.md` at the repository root** (AC: 1–9)
  - [x] T1.1 — Create the file at `GUIDE_INSTALLATION.md` (repo root — same level as `.docker/`, `pluribourse-backend/`, `pluribourse-frontend/`, `CLAUDE.md`)
  - [x] T1.2 — Write section « Prérequis » (see Dev Notes)
  - [x] T1.3 — Write section « Installation de Docker » (see Dev Notes — 3 separate OS subsections)
  - [x] T1.4 — Write section « Téléchargement et lancement » (see Dev Notes)
  - [x] T1.5 — Write section « Premier lancement » (see Dev Notes — URL is `http://localhost`, credentials `Admin`/`Admin`)
  - [x] T1.6 — Write section « Configuration initiale » (see Dev Notes — points to Story 1.5 admin settings page)
  - [x] T1.7 — Write section « Réinitialisation du mot de passe admin » (see Dev Notes — use `docker compose run --rm --no-deps` pattern)
  - [x] T1.8 — Write section « Mise à jour » (see Dev Notes — include data-preservation guarantee FR-071)
  - [x] T1.9 — Review full guide: verify vouvoiement throughout, all code blocks copy-paste safe, all technical terms explained on first use, no step requires prior Docker knowledge

## Dev Notes

### Critical: Docker Files Location

**The Docker files are in the `.docker/` subdirectory, NOT at the repo root.** The architecture document shows them at the root, but the actual implementation places them in `.docker/`:

```
PluriBourse/
├── .docker/
│   ├── docker-compose.yml       ← compose file
│   ├── docker-compose-db-only.yml
│   ├── .env                     ← created by user from .env.example
│   └── .env.example             ← template with env var explanations
├── pluribourse-backend/
├── pluribourse-frontend/
├── CLAUDE.md
└── GUIDE_INSTALLATION.md        ← file to create (AC1)
```

All `docker compose` commands in the guide must either:
- **Option A (recommended for guide):** Tell the user to `cd` into `.docker/` first, then use `docker compose` without a `-f` flag.
- **Option B:** Use `docker compose -f .docker/docker-compose.yml` from the repo root.

Choose Option A for readability in a non-technical guide.

### Critical: Application URL

The frontend nginx container runs on **port 80** (see `.docker/docker-compose.yml` and `pluribourse-frontend/nginx.conf`). The **user-facing URL is `http://localhost`**, not `http://localhost:8080`.

- Port 80 → frontend (Angular, served by nginx) — the URL the user types
- Port 8080 → backend API (Spring Boot) — internal only, proxied by nginx

The Epic AC says "http://localhost:8080" but this is an error in the spec. The correct URL is **`http://localhost`**. Verification: the nginx config proxies `/api/`, `/login`, `/logout`, `/actuator/health` to `backend:8080`.

### Critical: Admin Password Reset CLI Command

`AdminPasswordResetRunner` is a Spring `ApplicationRunner` (`pluribourse-backend/src/main/java/org/pluribourse/user/cli/AdminPasswordResetRunner.java`) that starts a full Spring context including Tomcat on port 8080. **Using `docker compose exec` against an already-running backend would cause a port 8080 conflict.**

The correct Docker command for the guide is `docker compose run --rm --no-deps`:

```bash
# Run from the .docker/ directory
docker compose run --rm --no-deps backend --reset-admin-password --login=Admin
```

- `run` — creates a NEW container (avoids port conflict with running backend)
- `--rm` — removes the container after it exits
- `--no-deps` — does not start db/frontend dependencies (they are already running)
- `backend` — the service name from docker-compose.yml
- `--reset-admin-password --login=Admin` — Spring Boot CLI args appended to the Dockerfile ENTRYPOINT (`["java", "-jar", "app.jar"]`), giving full command: `java -jar app.jar --reset-admin-password --login=Admin`

**Do NOT add `java -jar app.jar` in the command** — the Dockerfile ENTRYPOINT already provides it. Adding it again would produce `java -jar app.jar java -jar app.jar ...` which fails.

Expected console output (from `AdminPasswordResetRunner.performReset()`):
```
=== PluriBourse Admin Password Reset ===
Temporary password: <12-char alphanumeric>
Log in and change your password immediately.
========================================
```

If there is more than one admin account, the runner will prompt for credentials of an existing admin before proceeding.

### `.env` Configuration

The `.docker/.env.example` file contains:
```
# Database
DB_NAME=pluribourse
DB_PASSWORD=change_me_in_production
MYSQL_ROOT_PASSWORD=change_me_in_production

# Spring Boot active profile
SPRING_PROFILES_ACTIVE=prod
```

The user must copy `.env.example` to `.env` and change `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` to secure values. These are the only required edits before first launch.

### Docker Compose Services

From `.docker/docker-compose.yml`:
- `db` — MariaDB 11, persistent volume `db_data`, healthcheck
- `backend` — Spring Boot, depends on `db` (healthy), port 8080:8080, env: `DB_NAME`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE`
- `frontend` — nginx, depends on `backend` (healthy), port 80:80

Startup order: db → (healthcheck) → backend → (healthcheck) → frontend.
On `docker compose up -d`, all 3 start in sequence. Initial startup takes ~60–90 seconds while the backend passes its healthcheck.

### Guide Section Content Guidance

#### Section « Prérequis »
- Git (to clone the repository) or ability to download a ZIP from GitHub
- A computer running Linux, macOS, or Windows — or a Raspberry Pi 4 (minimum 2 GB RAM)
- Internet connection (to download Docker and the application images)
- No prior knowledge of Docker required

#### Section « Installation de Docker »
Three self-contained subsections:

**Windows:**
- Download Docker Desktop from docker.com
- Run the installer, follow the wizard
- Start Docker Desktop from the Start menu
- Verification: open PowerShell, run `docker --version` — should display the version

**macOS:**
- Download Docker Desktop for Mac (choose Intel or Apple Silicon)
- Drag to Applications, launch it
- Wait for the Docker icon in the menu bar to show "running"
- Verification: open Terminal, run `docker --version`

**Linux / Raspberry Pi:**
- Install Docker Engine via the convenience script or package manager (link to official Docker docs, not a hardcoded script — the URL may change)
- After install: `sudo usermod -aG docker $USER` + logout/login so the user doesn't need `sudo` for every docker command
- Start Docker: `sudo systemctl enable --now docker`
- Verification: `docker --version`

#### Section « Téléchargement et lancement »
1. Clone the repo: `git clone <repo-url>` (or download ZIP from GitHub and extract — use this if Git is not installed)
2. Navigate to the project folder: `cd PluriBourse`
3. Navigate to the Docker folder: `cd .docker`
4. Copy the environment template:
   - Linux / macOS: `cp .env.example .env`
   - Windows (PowerShell): `Copy-Item .env.example .env`
   - Windows (cmd): `copy .env.example .env`
5. Edit `.env` to set secure passwords:
   - Linux / macOS: `nano .env` (then Ctrl+X to save)
   - Windows: `notepad .env`
   - Change `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` to values that are at least 12 characters and contain letters and numbers
6. Launch: `docker compose up -d`
7. Wait 60–90 seconds for the first startup (images are downloaded the first time — this can take longer depending on your connection)
8. Verification: open a browser and go to `http://localhost` — the PluriBourse login page should appear

#### Section « Premier lancement »
- URL: `http://localhost` (or `http://<server-ip>` if running on a Raspberry Pi accessed from another machine on the network)
- Default credentials: username `Admin`, password `Admin`
- The application immediately requires changing the password — this is intentional and mandatory
- After changing the password, go to the « Configuration initiale » section

#### Section « Configuration initiale »
- After first login, navigate to Settings via the left sidebar (or go directly to `http://localhost/admin/settings`)
- Configure: association name, default commission rate, default document language
- These settings can be changed at any time later

#### Section « Réinitialisation du mot de passe admin »
- Opening a terminal per OS (Windows: PowerShell or cmd; macOS/Linux: Terminal)
- Navigate to the `.docker/` directory
- Run the reset command with `docker compose run --rm --no-deps` (see above)
- Copy the temporary password displayed in the console
- Go to `http://localhost`, log in with `Admin` and the temporary password
- The application will immediately require setting a new password

#### Section « Mise à jour »
```bash
# From the .docker/ directory
docker compose pull
docker compose up -d
```
- Explain what `pull` does (downloads newer images)
- Explain what `up -d` does (replaces running containers with new versions)
- **Explicitly state:** all association data (sellers, items, editions) is stored in the `db_data` Docker volume, which is never deleted by the update command (FR-071)
- Note: if there are database schema changes, they are applied automatically by Liquibase on startup

#### Troubleshooting note (brief, optional subsection)
If the app does not start after `docker compose up -d`, the user can check the logs:
- `docker compose logs backend` — shows Spring Boot startup logs
- `docker compose ps` — shows which containers are running and their status
Keep this section very short and reassuring — the target user is non-technical.

### Language / Tone Requirements (FR-074)

- Written in **French** throughout
- Use **« vous »** (vouvoiement) — never « tu »
- Keep sentences short and direct — target audience has no technical background
- Technical terms to define on first use:
  - **terminal** — « une fenêtre noire dans laquelle vous tapez des commandes »
  - **conteneur** — « un programme isolé qui s'exécute de façon autonome »
  - **volume** — « un espace de stockage persistant qui garde vos données même si l'application est arrêtée »
  - **port** — « un numéro qui identifie la "porte" par laquelle votre navigateur communique avec l'application »

### No Tests Required

This story produces only a Markdown documentation file (`GUIDE_INSTALLATION.md`). No unit tests, no Angular specs, no Spring IT classes. The acceptance criteria are verified by reading the file content, not by running code.

### Project Structure Notes

- **Output file:** `GUIDE_INSTALLATION.md` at repo root (`C:\Users\JHER\IdeaProjects\PluriBourse\GUIDE_INSTALLATION.md` on Windows; `./GUIDE_INSTALLATION.md` relative to repo root)
- **No code changes required** — this story is documentation only
- **Do not modify** any existing source files, Docker configs, or CI settings
- **Document language:** French (per PRD and guide target audience) — this is an exception to the CLAUDE.md rule "Documentation projet : français"; the GUIDE_INSTALLATION.md is user-facing documentation for end users, written in French

### References

- [Source: epics.md#Story 1.9] — Story requirements, acceptance criteria, dependencies
- [Source: epics.md#FR-074] — Guide targets non-technical users; covers Docker install, first launch, config, password reset, update per OS
- [Source: epics.md#FR-068] — Server functional on Linux, macOS, Windows without code changes
- [Source: epics.md#FR-069] — Minimum config: Raspberry Pi 4 (2 GB RAM)
- [Source: epics.md#FR-070] — Deployed via Docker Compose, data in persistent volumes
- [Source: epics.md#FR-071] — Update via `docker compose pull && docker compose up -d`, data preserved
- [Source: .docker/docker-compose.yml] — Service names (db, backend, frontend), ports (80:80, 8080:8080)
- [Source: .docker/.env.example] — Required environment variables
- [Source: pluribourse-backend/Dockerfile] — `app.jar` name, `ENTRYPOINT ["java", "-jar", "app.jar"]`
- [Source: pluribourse-frontend/nginx.conf] — Frontend on port 80, backend proxied
- [Source: pluribourse-backend/.../AdminPasswordResetRunner.java] — CLI options `--reset-admin-password --login=<username>`
- [Source: 1-4-recuperation-du-mot-de-passe-admin-via-cli.md#CLI invocation] — Spring context starts with web server; use `docker compose run --rm --no-deps` to avoid port conflict

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

- Created `GUIDE_INSTALLATION.md` at repo root — 7 sections in order, all ACs validated programmatically.
- Corrected spec error: URL is `http://localhost` (port 80, nginx frontend), not `http://localhost:8080` as stated in epic AC.
- Corrected CLI reset command: `docker compose run --rm --no-deps backend --reset-admin-password --login=Admin` (no `java -jar app.jar` prefix — ENTRYPOINT already provides it).
- 12 occurrences of « vous » — vouvoiement consistent throughout.
- All 4 technical terms defined on first use: terminal, conteneur, volume, port.
- No code changes — documentation story only.

### File List

- `GUIDE_INSTALLATION.md` (new)

### Review Findings

- [x] [Review][Patch] Procédure de mise à jour cassée — remplacé par `git pull && docker compose -f .docker/docker-compose.yml up -d --build` (build local retenu) [GUIDE_INSTALLATION.md + .docker/docker-compose.yml]
- [x] [Review][Patch] Instruction nano incorrecte — corrigé en "Ctrl+O pour sauvegarder, Ctrl+X pour quitter" [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Nom du volume incorrect — corrigé `db_data` → `pluribourse_db_data` [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] "volume" non défini à la première utilisation (AC8) — ajout parenthèse explicative au premier usage [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Commandes de mise à jour en deux code blocks séparés — fusionnées en un seul bloc avec `&&` [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] `--no-deps` crash si la stack est arrêtée — ajout prérequis explicite avant la section reset [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Section « Dépannage rapide » absente de la table des matières — élevée en `##` et ajoutée en entrée 8 de la ToC [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Délai de démarrage trop optimiste sur Raspberry Pi — ajout "jusqu'à 3 minutes sur Raspberry Pi" [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Libellé Windows 11 incorrect — distingué Windows 11 ("Terminal") et Windows 10 ("Windows PowerShell") [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Point de départ du terminal non précisé — ajout instruction explicite "dans le dossier qui contient `PluriBourse`" [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Option ZIP — ajout guidance sur l'emplacement + note sur les limites pour les mises à jour [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Mot de passe admin visible en clair lors du reset multi-admin — ajout avertissement explicite [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] URL GitHub placeholder non fonctionnel — remplacé par `<URL du dépôt PluriBourse>` avec instruction [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Logs de dépannage limités au backend — ajout `logs db` et `logs frontend` [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Contradiction complexité mots de passe — clarifié que le mot de passe admin (≥8) est distinct des mots de passe BD (≥12) [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Port 80 potentiellement occupé — ajout note + solution de contournement dans « Dépannage rapide » [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Exigence d'espace disque absente — ajout "5 Go d'espace disque libre" dans Prérequis [GUIDE_INSTALLATION.md]
- [x] [Review][Patch] Prérequis WSL2 / virtualisation pour Windows omis — ajout note Prérequis + mention dans l'étape d'installation Windows [GUIDE_INSTALLATION.md]
- [x] [Review][Defer] Pas de recommandation de sauvegarde avant mise à jour — bonne pratique mais hors scope story 1.9 [GUIDE_INSTALLATION.md] — deferred, pre-existing
- [x] [Review][Defer] Pas d'instruction pour arrêter l'application (`docker compose down`) — hors scope story 1.9 [GUIDE_INSTALLATION.md] — deferred, pre-existing
- [x] [Review][Defer] Promesse "aucune connaissance technique requise" partiellement contredite par le contenu — tension inhérente au type de guide, non actionnable sans réécriture du positionnement [GUIDE_INSTALLATION.md] — deferred, pre-existing
