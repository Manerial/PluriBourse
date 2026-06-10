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

# Document de Décisions d'Architecture

_Ce document se construit de manière collaborative à travers une découverte étape par étape. Les sections sont ajoutées au fur et à mesure que nous travaillons ensemble sur chaque décision architecturale._

## Analyse du Contexte Projet

### Vue d'Ensemble des Exigences

**Exigences Fonctionnelles :**

89 exigences fonctionnelles réparties en 10 groupes de fonctionnalités (F1–F10). Le système est organisé autour d'une machine à états du cycle de vie d'une édition qui gouverne tous les comportements :

- **F1 — Internationalisation (7 EFs) :** Fondation transversale. i18n en double couche : ngx-translate pour l'interface, Spring MessageSource pour les documents imprimés. Langue par compte utilisateur (interface) et par instance (documents).
- **F2 — Gestion des Éditions (12 EFs) :** Cycle de vie des phases contrôlé par l'administrateur (Préparation → Dépôt → Vente → Post-vente → Clôturé). Une seule édition active à la fois. Retour arrière de phase supporté. Action optionnelle destructrice « Nettoyer l'Édition ».
- **F3 — Gestion des Vendeurs & Articles (18 EFs) :** Profils vendeurs multi-éditions. Enregistrement des articles avec affectation de table automatique. Génération de codes-barres Code 128. Impression d'étiquettes thermiques + bordereau de dépôt via file d'attente ESC/POS. Prise en charge des lots.
- **F4 — Point de Vente (13 EFs) :** Scanner USB HID avec gestion transparente AZERTY/QWERTY. Gestion du panier avec respect de l'intégrité des lots. Impression de facture acheteur. Annulation du panier lors d'une transition de phase (FR-090).
- **F5 — Post-Vente & Reversements (5 EFs) :** Flux de règlement vendeur. Chemin « non récupéré » transférant le reversement en recette de l'association. Récapitulatif des ventes par vendeur.
- **F6 — Rapports (6 EFs) :** Récapitulatif journalier, récapitulatif d'édition, rapport des vendeurs en attente — tous en PDF, administrateur uniquement.
- **F7 — Comptes Utilisateurs & Contrôle d'Accès (8 EFs) :** Séparation stricte des rôles Administrateur/Bénévole. Un seul compte administrateur par instance. Interface bénévole pilotée par la phase.
- **F8 — Infrastructure & Déploiement (7 EFs) :** Docker Compose multiplateforme. Cible Raspberry Pi 4. Guide d'installation non technique.
- **F9 — Infrastructure d'Impression (5 EFs) :** Point d'impression centralisé côté serveur. Imprimantes thermiques (ESC/POS) + A4 (PDF) via USB. File d'attente d'impression séquentielle. Retour d'erreur vers l'interface.
- **F10 — Catalogue Articles (8 EFs) :** Catalogue filtrable/triable sur toutes les phases. Repli catalogue-vers-panier pour les codes-barres illisibles.

**Exigences Non Fonctionnelles :**

| ID | Catégorie | Impact Architectural |
|---|---|---|
| NFR-001 | Performance | Doit fonctionner sur RPi 4 (2 Go RAM) sous charge événementielle (~1 700 articles, 3 postes) |
| NFR-002 | Concurrence | Les opérations POS simultanées ne doivent pas générer de conflits de données |
| NFR-003 | Exactitude Financière | Tous les calculs monétaires en BigDecimal — jamais float/double |
| NFR-004 | Compatibilité Navigateur | Tout navigateur moderne, tout OS — REST pur + SPA, sans API spécifiques au navigateur |
| NFR-005 | Compatibilité Scanner | Gestion transparente du mapping de touches USB HID dans le composant Angular de scan |
| NFR-006 | Fiabilité | Aucune perte de données à la fermeture du navigateur — limites de transaction côté serveur |
| NFR-007 | RGPD | Anonymisation des DCP à la suppression du vendeur sur toutes les éditions ; aucune DCP dans les journaux |

**Échelle & Complexité :**

- Domaine principal : Application web full-stack, orientée backend
- Niveau de complexité : **Moyen** — volume de données modeste (~100 vendeurs, ~1 700 articles/édition), complexité fonctionnelle significative (machine à états, infrastructure d'impression, concurrence, RGPD, génération PDF)
- Modules architecturaux estimés : ~8–10 contextes délimités distincts

### Contraintes Techniques & Dépendances

| Contrainte | Décision | Source |
|---|---|---|
| Backend | Spring Boot | Brief / CLAUDE.md |
| Frontend | Angular (composants standalone, Signals) | CLAUDE.md |
| Base de données | MariaDB + Docker Compose | Addendum PRD |
| Impression thermique | Protocole ESC/POS, bibliothèque candidate `escpos-coffee` | Addendum PRD |
| i18n — Interface | ngx-translate (fichiers JSON) | Addendum PRD |
| i18n — Documents | Spring MessageSource (fichiers .properties) | Addendum PRD |
| Calculs financiers | BigDecimal — jamais float/double | CLAUDE.md |
| Migrations BDD | Liquibase | CLAUDE.md |
| Matériel cible | Raspberry Pi 4 (2 Go RAM), stockage SSD/USB | PRD NFR-001 |

### Préoccupations Transversales Identifiées

1. **Machine à états de phase** — pilote le rendu de l'interface, l'application des règles métier et le contrôle d'accès dans tous les modules
2. **Authentification & séparation des rôles** — Administrateur/Bénévole strictement séparés ; l'interface bénévole s'adapte à la phase active
3. **Gestion de la concurrence** — l'état « vendu » d'un article doit être sans conflit entre les postes POS simultanés
4. **File d'attente d'impression côté serveur** — séquentielle, centralisée, deux files indépendantes (thermique / A4)
5. **Exactitude financière** — BigDecimal se propage à travers la tarification des articles, la commission, le calcul du reversement et tous les rapports
6. **Conformité RGPD** — gestion du cycle de vie des DCP (anonymisation, pas suppression des enregistrements) ; aucune DCP dans les journaux
7. **i18n (double couche)** — langue de l'interface par compte utilisateur ; langue des documents par instance

---

## Évaluation du Modèle de Démarrage

### Domaine Technologique Principal

Application web full-stack — orientée backend. Stack décidée en amont : Spring Boot (backend) + Angular (frontend), MariaDB, Docker Compose.

### Outils de Génération de Squelette

| Couche | Outil | Commande |
|---|---|---|
| Backend | Spring Initializr | `start.spring.io` ou Spring Boot CLI |
| Frontend | Angular CLI | `ng new pluribourse-frontend` |
| Infrastructure | Manuel | `docker-compose.yml` personnalisé |

### Versions Sélectionnées

| Technologie | Version | Justification |
|---|---|---|
| Java | **21** (LTS) | Les threads virtuels (Project Loom) réduisent la pression mémoire sous charge POS concurrente sur RPi 4 ; LTS supporté jusqu'en 2031 |
| Spring Boot | **4.0.6** | Version stable réelle, sortie avril 2026 (confirmé sur spring.io) ; Spring Framework 7, Spring Security 7, Hibernate 7 — ne pas rétrograder vers 3.x |
| Angular | **21** (LTS) | Angular 22 sorti le 3 juin 2026 — délibérément écarté : l'écosystème `jest-preset-angular` n'est pas encore stabilisé sur cette version ; Angular 21 LTS supporté jusqu'en mai 2027 — ne pas upgrader vers 22 avant la fin du projet |
| Outil de build | **Maven** | Builds prévisibles ; bien connu des experts Java ; `pom.xml` lisible ; configuration JaCoCo + Failsafe exhaustivement documentée |

### Initialisation Backend (Spring Initializr)

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

**Ajouté manuellement après initialisation :**
- MapStruct (non disponible dans Initializr)
- OpenPDF 3.0.0 (LGPL 2.1 + MPL 1.1) — génération PDF
- escpos-coffee ou équivalent — impression thermique ESC/POS

### Initialisation Frontend (Angular CLI)

```bash
ng new pluribourse-frontend --standalone --routing --style=scss
```

**Ajouté manuellement après initialisation :**
- @ngx-translate/core + @ngx-translate/http-loader — i18n
- @angular/material — bibliothèque de composants UI (MIT)

### Décisions Architecturales Apportées par les Modèles de Démarrage

**Langage & Runtime :** Java 21 avec Maven wrapper ; mode strict TypeScript (défaut Angular)

**Styles :** SCSS — flexibilité pour le theming Angular Material

**Outillage de Build :** Maven (backend) + Angular CLI / esbuild (frontend)

**Framework de Test :** JUnit 5 + Mockito (défaut Spring Boot) ; Jest + Angular CDK Testing Harnesses (configuré après initialisation selon CLAUDE.md)

**Organisation du Code :** Structure en couches standard Spring Boot (Contrôleur → Service → Référentiel) ; composants Angular standalone avec structure de répertoires par fonctionnalité

**Expérience de Développement :** Rechargement à chaud Spring Boot DevTools ; serveur de développement Angular CLI avec HMR

### Conformité des Licences

Toutes les dépendances sélectionnées utilisent des licences permissives ou faiblement copyleft. Politique du projet : pas d'AGPL, pas de GPL copyleft.

| Dépendance | Licence |
|---|---|
| Spring Boot / Spring Framework | Apache 2.0 |
| Angular + Angular Material | MIT |
| MariaDB Connector/J | LGPL 2.1 |
| Liquibase | Apache 2.0 |
| Lombok | MIT |
| MapStruct | Apache 2.0 |
| OpenPDF 3.0.0 | LGPL 2.1 + MPL 1.1 |
| ngx-translate | MIT |

> iText 7 (AGPL) a été explicitement rejeté — même en utilisation open-source, il impose de mentionner iText dans les métadonnées de chaque PDF généré. OpenPDF est l'alternative directe sans cette obligation.

**Remarque :** L'initialisation du projet (Spring Initializr + `ng new`) devrait constituer la première story d'implémentation.

---

## Décisions Architecturales Fondamentales

### Analyse des Priorités de Décision

**Décisions Critiques (Bloquent l'Implémentation) :**
- Stratégie de gestion de session (affecte tous les points d'entrée sécurisés)
- Modèle de concurrence POS (affecte l'intégrité des ventes d'articles)
- Mécanisme de notification de changement de phase (affecte toutes les interfaces en phase active)

**Décisions Importantes (Façonnent l'Architecture) :**
- Implémentation de la file d'attente d'impression (affecte la fiabilité F9)
- Bibliothèque de génération de codes-barres (affecte la génération d'étiquettes F3)
- Outillage de documentation API (affecte l'expérience développeur)

**Décisions Reportées (Post-v1) :**
- Mécanisme de sauvegarde/restauration (explicitement hors périmètre v1)
- Override de commission par édition (hors périmètre v1)
- Configuration du pipeline CI avec Testcontainers (peut être ajouté de manière incrémentale)

---

### Authentification & Sécurité

| Décision | Choix | Justification |
|---|---|---|
| Mécanisme d'auth | Spring Security (basé sur formulaire, sessions avec état) | Déploiement mono-instance, pas besoin de complexité JWT, FR-066 (pas d'expiration de session) |
| Stockage de session | **Spring Session JDBC** (MariaDB) | Les sessions survivent aux redémarrages du conteneur pendant les événements — critique dans un contexte d'événement en direct de 4–6h |
| Expiration de session | Aucune (FR-066) | Configuré explicitement : `server.servlet.session.timeout=-1` |
| Encodage du mot de passe | BCrypt (défaut Spring Security) | Standard de l'industrie, aucune configuration supplémentaire |
| Réinitialisation du mot de passe admin | Commande CLI générant un mot de passe temporaire (FR-063) | Spring Boot `CommandLineRunner` ou script CLI personnalisé ; impose le changement de mot de passe à la prochaine connexion |
| Modèle de rôles | Trois rôles : `ADMIN`, `VOLUNTEER`, `SELLER` — strictement séparés | `SELLER` réservé en v1 (pas d'interface, pas de points d'entrée publics) ; lien `User ↔ SellerProfile` via FK nullable prête pour le portail v2 |
| Périmètre SELLER v1 | `Role.SELLER` déclaré, FK `users.seller_profile_id` nullable | Aucun point d'entrée public, aucune interface, aucune inscription — tout est bloqué à 403 via Spring Security |
| Périmètre SELLER v2 | Portail d'inscription public, sélection de créneaux de dépôt | Nécessite : HTTPS, reverse proxy, ouverture réseau — hors périmètre v1 |

---

### Architecture des Données

| Décision | Choix | Justification |
|---|---|---|
| ORM | Spring Data JPA + Hibernate 7 | Fourni par Spring Boot 4.0.6 |
| Migrations | Liquibase | Déclaré dans CLAUDE.md ; schéma versionné |
| DCP Vendeur | Stockées dans des champs dédiés, anonymisables sur demande (FR-021) | L'anonymisation remplace les valeurs, ne supprime pas les lignes — préserve l'intégrité référentielle entre les éditions |
| Valeurs financières | `BigDecimal` partout — jamais `float`/`double` | Déclaré dans CLAUDE.md ; NFR-003 (précision au centime) |
| Isolation par édition | Clé étrangère `edition_id` sur toutes les entités transactionnelles | Articles, ventes, paniers, rapports limités à l'édition |
| Cache | Aucun pour la v1 | Volume de données modeste (~1 700 articles) ; MariaDB sur SSD/USB est suffisant |
| Préférence de langue utilisateur | Champ `preferredLanguage` sur l'entité `User` (persisté en BDD, `enum {EN, FR}`) | FR-067 — stocké sur le compte, appliqué à la connexion via ngx-translate ; pas local au navigateur |

---

### Concurrence — POS (Point de Vente)

| Décision | Choix | Justification |
|---|---|---|
| Stratégie de verrouillage | **Verrouillage optimiste** (`@Version` sur l'entité `Item`) | Faible contention attendue sur 3 postes ; pas de verrous maintenus, pas d'interblocages |
| Filet de sécurité | Contrainte `UNIQUE` en BDD sur l'état vendu d'un article | Garantie secondaire au niveau de la base de données |
| Scénario de conflit | Article saisi manuellement dans deux paniers simultanément | Détecté à la **validation du paiement** (pas au moment du scan) |
| UX de conflit | Le backend retourne 409 avec la liste des articles conflictuels ; Angular affiche un message explicite ; le bénévole retire les articles conflictuels et revalide | Pas de réessai automatique — résolution manuelle par le bénévole |
| Exigence de test | Test d'intégration avec deux `TransactionTemplate`s concurrents + **Testcontainers (MariaDB)** en CI | Le comportement de verrouillage de H2 diffère de MariaDB ; une vraie BDD est requise pour ce test |

---

### Notification de Changement de Phase (FR-090)

| Décision | Choix | Justification |
|---|---|---|
| Mécanisme | **Server-Sent Events (SSE)** | Envoi serveur uniquement (pas besoin de bidirectionnel) ; plus simple que WebSocket ; `EventSource` se reconnecte automatiquement selon la RFC 8895 ; HTTP simple (pas de problèmes de proxy sur le réseau local de la salle) |
| Implémentation Spring | `SseEmitter` par client connecté | Émetteurs gérés dans un registre thread-safe ; fermés après l'envoi de l'événement lors du changement de phase |
| Implémentation Angular | `EventSource` encapsulé dans un service Angular | Testable avec `jest.fn()` ; reconnexion gérée nativement |
| Déclencheur | La transition de phase (dans n'importe quelle direction) déclenche un événement SSE vers tous les clients connectés | Le panier actif du bénévole est annulé si la phase change en cours de transaction (FR-090) |

---

### Infrastructure d'Impression

| Décision | Choix | Justification |
|---|---|---|
| Implémentation de la file | **`LinkedBlockingQueue` en mémoire** (une par type d'imprimante) | Simple, pas d'infrastructure supplémentaire ; livraison au-plus-une fois acceptable |
| Garantie de livraison | Au-plus-une fois | Acceptable : tous les travaux d'impression sont redéclenchables depuis l'interface (FR-078) ; source de données toujours disponible en BDD |
| Injection de la file | Injectée par constructeur (pas statique) | Permet une file bornée dans les tests pour vérifier le comportement sous contre-pression |
| Gestion des erreurs | Les erreurs d'imprimante remontent vers l'interface via SSE ou réponse de polling (FR-079) | Utilisateur notifié ; peut réessayer manuellement |
| Imprimante thermique | ESC/POS via `escpos-coffee` (ou équivalent) — travaux séquentiels | Une file, un thread consommateur |
| Imprimante A4/document | PDF généré par OpenPDF 3.0.0 → envoyé à l'imprimante USB | Une file, un thread consommateur |

---

### API & Communication

| Décision | Choix | Justification |
|---|---|---|
| Style API | REST (JSON) | Standard, bien supporté par Spring MVC et Angular HttpClient |
| Documentation API | **Springdoc OpenAPI** (Apache 2.0) — activé en `dev`, désactivé en `prod` | Auto-générée depuis les annotations ; snapshot OpenAPI en CI détecte les régressions de contrat |
| Gestion des erreurs | `@ControllerAdvice` + RFC 7807 Problem Details | Réponses d'erreur standardisées ; lisibles par machine pour la gestion d'erreurs Angular |
| Validation | Bean Validation (`jakarta.validation`) sur les DTOs | Échec rapide à la frontière du contrôleur |
| CORS | Configuré pour `localhost` uniquement (déploiement mono-serveur) | Pas de requêtes cross-origin depuis des domaines externes |

---

### Architecture Frontend

| Décision | Choix | Justification |
|---|---|---|
| Gestion d'état | Angular Signals (pas de NgRx) | Déclaré dans CLAUDE.md ; état réactif sans le boilerplate NgRx ; composable avec `computed()` |
| Modèle de composants | Composants standalone | Déclaré dans CLAUDE.md (dernier patron Angular) |
| HTTP | Angular `HttpClient` | Standard ; testable avec `HttpClientTestingModule` |
| i18n | ngx-translate (commutation à l'exécution, pas de build par locale) | Déclaré dans l'addendum PRD ; fichiers JSON `en.json` / `fr.json` |
| Composants UI | Angular Material (MIT) | Patrons Angular idiomatiques ; CDK Testing Harnesses pour des tests de composants robustes |
| Saisie scanner | USB HID → événements clavier capturés dans le composant Angular | AZERTY/QWERTY géré via le mapping des codes de touches (FR-034) ; aucune configuration du poste requise |

---

### Infrastructure & Déploiement

| Décision | Choix | Justification |
|---|---|---|
| Déploiement | Docker Compose (`docker-compose.yml`) — fichier unique | Déclaré dans l'addendum PRD ; `docker compose up -d` / `docker compose pull && up -d` |
| Journalisation | SLF4J + Logback (défaut Spring Boot) — **aucune DCP dans les journaux** | NFR-007 + contrainte CLAUDE.md ; noms, emails, téléphones des vendeurs jamais journalisés |
| Monitoring | Aucun pour la v1 | Hors périmètre ; cible Raspberry Pi, usage mono-événement |
| CI/CD | Aucun pour la v1 | Projet communautaire/hobby ; mises à jour appliquées manuellement |

---

### Analyse d'Impact des Décisions

**Séquence d'Implémentation (ordre suggéré) :**
1. Génération du squelette du projet (Spring Initializr + `ng new`) + socle Docker Compose
2. Schéma Liquibase + entités principales (Edition, Seller, Item, User)
3. Spring Security + Spring Session JDBC
4. Fondation i18n (ngx-translate + Spring MessageSource)
5. Développement des fonctionnalités (F2 → F3 → F4 → F5 → F6 → F7 → F9 → F10)

**Dépendances Entre Composants :**
- La machine à états de phase (F2) doit être implémentée avant F3, F4, F5, F10 — elle gouverne toutes les règles métier
- Spring Session JDBC nécessite une migration Liquibase avant toute fonctionnalité d'authentification
- Le registre des émetteurs SSE doit être en place avant les points d'entrée de transition de phase (F2)
- Les consommateurs de la file d'impression doivent être initialisés en tant que beans Spring avant les fonctionnalités d'impression F3/F4
- Le test CI Testcontainers (MariaDB) est requis avant la livraison de la story de concurrence POS F4

---

## Patrons d'Implémentation & Règles de Cohérence

### Patrons de Nommage

**Backend — Base de Données**

| Élément | Convention | Exemple |
|---|---|---|
| Noms de tables | `snake_case`, pluriel | `seller_profiles`, `editions`, `items`, `print_jobs` |
| Noms de colonnes | `snake_case` | `last_name`, `edition_id`, `is_complete` |
| Clés étrangères | `{entité}_id` | `seller_id`, `edition_id` |
| Index | `idx_{table}_{colonne}` | `idx_items_edition_id` |

**Backend — Java**

| Élément | Convention | Exemple |
|---|---|---|
| Structure des packages | `org.pluribourse.{fonctionnalité}.{couche}` | `org.pluribourse.seller.service` |
| Packages de fonctionnalité | nom au singulier | `edition`, `seller`, `item`, `pos`, `payout`, `report`, `user`, `print` |
| Noms de classes | `PascalCase` | `SellerService`, `ItemController` |
| Noms de méthodes/champs | `camelCase` | `findByEditionId`, `isComplete` |
| Suffixe DTO | `Dto` | `SellerDto`, `ItemDto` |
| Suffixe Mapper | `Mapper` | `SellerMapper`, `ItemMapper` |

**Backend — API REST**

| Élément | Convention | Exemple |
|---|---|---|
| Préfixe URL | `/api/` — pas de versionnage | `/api/sellers`, `/api/editions` |
| Noms de ressources | `kebab-case`, pluriel | `/api/seller-profiles`, `/api/print-jobs` |
| Paramètres de route | `{id}` | `/api/sellers/{id}` |
| Paramètres de requête | `camelCase` | `?editionId=1&sortBy=name` |

**Frontend — Angular**

| Élément | Convention | Exemple |
|---|---|---|
| Noms de fichiers | `kebab-case` | `seller-list.component.ts`, `edition.service.ts` |
| Noms de classes | `PascalCase` | `SellerListComponent`, `EditionService` |
| Noms de signals | `camelCase` | `sellers = signal([])`, `isLoading = signal(false)` |
| Clés i18n | notation pointée, 3 niveaux max | `seller.list.empty`, `pos.basket.item-already-sold` |

---

### Patrons de Structure

**Backend — Organisation des Packages**

```
org.pluribourse.
├── edition/
│   ├── controller/   EditionController.java
│   ├── service/      EditionService.java
│   ├── repository/   EditionRepository.java
│   ├── entity/       Edition.java
│   ├── dto/          EditionDto.java, CreateEditionDto.java
│   └── mapper/       EditionMapper.java
├── seller/           (même patron)
├── item/             (même patron)
├── pos/              (même patron)
├── payout/           (même patron)
├── report/           (même patron)
├── user/             (même patron)
├── print/            (même patron)
└── shared/
    ├── exception/    GlobalExceptionHandler.java, BusinessException.java
    ├── security/     SecurityConfig.java, SessionConfig.java
    ├── sse/          SseEmitterRegistry.java
    └── config/       OpenApiConfig.java, JacksonConfig.java
```

**Frontend — Organisation des Répertoires**

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
│       └── page.model.ts         (forme Spring Page<T>)
└── assets/
    └── i18n/
        ├── en.json
        └── fr.json
```

---

### Patrons de Format

**Réponses API**

- **Réponse simple** : objet ou tableau direct — pas d'enveloppe
- **Réponse paginée/filtrée** : Spring `Page<T>` — `{content: [...], page: {size, number, totalElements, totalPages}}`
- **Réponse d'erreur** : RFC 7807 Problem Details

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

Bibliothèque open source communautaire, dont l'auteur du projet (Manerial) est également l'auteur. Dépôt : https://github.com/Manerial/JPageFlow. Ce projet sert de terrain d'épreuve pour valider JPageFlow en conditions réelles. Ne pas la remplacer par une autre solution de pagination sans décision explicite.

Outil standard pour tous les points d'entrée de listes paginées/filtrées :

```java
// Patron de service
Page<ItemDto> result = FilterService.filterData(
    itemRepository.findByEditionId(editionId),
    filterDto,
    items -> items.stream().map(itemMapper::toDto).toList()
);
```

> ⚠️ **Problème connu** : Le tri par `BigDecimal` (ex. par prix) est défaillant dans JPageFlow v1.5.0 — le comparateur se replie sur la comparaison alphabétique de chaînes. Correction requise dans la bibliothèque avant l'implémentation du tri par prix. Les tests échoueront jusqu'au correctif.

**Formats de Données**

| Type | Format | Exemple |
|---|---|---|
| Noms de champs JSON | `camelCase` | `lastName`, `editionId` |
| Dates | ISO 8601 date | `"2026-06-09"` |
| Datetimes | ISO 8601 avec Z | `"2026-06-09T14:30:00Z"` |
| Valeurs monétaires | nombre JSON (BigDecimal sérialisé) | `12.50` |
| Booléens | `true` / `false` | `"isComplete": false` |

---

### Patrons de Communication

**Événements SSE**

| Événement | Nom | Charge utile |
|---|---|---|
| Transition de phase | `phase-changed` | `{editionId, newPhase, previousPhase}` |
| Panier annulé | `basket-cancelled` | `{reason: "phase-changed"}` |

- Noms d'événements : `kebab-case`
- Charge utile : JSON
- Angular : `EventSource` encapsulé dans `PhaseService`, exposé en tant que `Signal<Phase>`

**Gestion d'État Angular**

```typescript
// Patron Signal — état local
sellers = signal<Seller[]>([]);
isLoading = signal(false);

// Computed — état dérivé
sellerCount = computed(() => this.sellers().length);

// Pas de NgRx — pas de stores, pas d'actions, pas de reducers
```

---

### Patrons de Processus

**Gestion des Erreurs**

- Backend : `@ControllerAdvice` intercepte toutes les exceptions, retourne RFC 7807
- `BusinessException` (runtime) pour les violations de règles métier → mappée en 4xx
- Angular : erreurs HTTP interceptées dans le service, exposées via Signal ou propagées au composant
- Pas de suppression silencieuse des erreurs — toujours remonter vers l'utilisateur ou journaliser

**États de Chargement**

```typescript
// Patron par composant
isLoading = signal(false);

async loadSellers() {
  this.isLoading.set(true);
  try { ... }
  finally { this.isLoading.set(false); }
}
```

**Validation**

- Côté serveur : Bean Validation (`@NotNull`, `@Size`, etc.) sur tous les DTOs — obligatoire
- Côté client : Validateurs de formulaires réactifs Angular — commodité uniquement, non fiables

**Clés i18n**

- Maximum 3 niveaux : `fonctionnalité.section.clé`
- Termes métier partagés alignés entre `en.json` et `messages_en.properties`
- Exemples : `seller.label`, `edition.phase.deposit`, `pos.basket.lot-incomplete`, `report.daily.title`

---

### Directives d'Application

**Toutes les implémentations DOIVENT :**
- Utiliser `FilterService.filterData()` (JPageFlow) pour tout point d'entrée de liste paginée/filtrable
- Retourner RFC 7807 Problem Details pour toutes les réponses d'erreur
- Utiliser `BigDecimal` pour toutes les valeurs monétaires — jamais `float` ou `double`
- Utiliser ISO 8601 pour toute sérialisation de date/datetime
- Ne jamais journaliser les DCP (nom, email, téléphone du vendeur) — utiliser l'ID vendeur dans les journaux
- Respecter la structure de packages `org.pluribourse.{fonctionnalité}.{couche}`
- Placer les fichiers Angular sous `components/`, `services/`, ou `models/` selon leur nature
- Utiliser `signal()` / `computed()` pour l'état Angular — pas de patrons `BehaviorSubject` impératifs
- Bloquer toutes les requêtes de rôle `SELLER` avec 403 en v1 — `SecurityConfig.java` doit refuser toutes les requêtes SELLER authentifiées ; pas de points d'entrée ni d'interface SELLER avant le portail v2

---

## Structure du Projet & Frontières

### Organisation du Référentiel

```
PluriBourse/                          ← racine du monorepo
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── CLAUDE.md
├── pluribourse-backend/              ← module Spring Boot
└── pluribourse-frontend/             ← module Angular
```

---

### Backend — Structure de Répertoires Complète

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
    │           ├── 001-core-schema.xml             (users.seller_profile_id FK nullable)
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

### Frontend — Structure de Répertoires Complète

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
    │       └── page.model.ts         (forme TypeScript de Spring Page<T>)
    └── assets/
        └── i18n/
            ├── en.json               ← F1
            └── fr.json               ← F1
```

---

### Correspondance Fonctionnalité → Structure

| Fonctionnalité | Packages backend | Frontend |
|---|---|---|
| F1 — i18n | `resources/messages_*.properties` | `assets/i18n/*.json`, `app.config.ts` |
| F2 — Éditions & cycle de vie | `edition/` + `shared/sse/` | `components/edition/`, `services/phase.service.ts` |
| F3 — Vendeurs & articles | `seller/`, `item/`, `print/` | `components/seller/`, `services/seller+item` |
| F4 — POS | `pos/` | `components/pos/`, `services/pos.service.ts` |
| F5 — Post-vente & reversements | `payout/` | `components/payout/` |
| F6 — Rapports | `report/` (OpenPDF) | `components/report/` |
| F7 — Comptes utilisateurs | `user/` (+ `cli/AdminPasswordResetRunner`), `shared/security/` | `components/user/`, `services/auth.service.ts` |
| F8 (paramètres admin) — Config instance | `shared/instanceconfig/` | `components/admin/`, `services/instance-config.service.ts` |
| F8 — Infrastructure | `docker-compose.yml`, `application.properties`, Liquibase | — |
| F9 — Impression | `print/` (BlockingQueue + ESC/POS + OpenPDF) | `services/print.service.ts` |
| F10 — Catalogue articles | `item/controller/ItemCatalogController.java` | `components/catalog/` |

---

### Frontières d'Intégration

**Frontière API (Backend ↔ Frontend)**
- Toute communication via REST JSON sur HTTP
- URL de base : `/api/`
- Auth : cookie de session (Spring Security, `JSESSIONID` stocké dans MariaDB via Spring Session JDBC)
- Point d'entrée SSE : `GET /api/sse/events` — notifications de changement de phase
- Point d'entrée d'impression : `POST /api/print/{type}` — déclenche un travail d'impression côté serveur

**Frontière de Données (Service ↔ Référentiel)**
- Les entités ne quittent jamais la couche `service/` — toujours mappées en DTOs via MapStruct
- `FilterService.filterData()` (JPageFlow) opère sur des listes de DTOs, pas sur des entités

**Frontière d'Impression**
- `PrintQueueService` possède deux instances `LinkedBlockingQueue` (thermique / document)
- `ThermalPrintService` et `DocumentPrintService` sont des consommateurs de file s'exécutant sur des threads dédiés
- Pas d'appel d'impression direct depuis les contrôleurs — toujours via `PrintQueueService`

**Flux de Données — Vente POS**
```
Angular scanner.component
  → POST /api/pos/baskets/{id}/items (scan)
  → BasketController → BasketService
  → ItemRepository (vérification état vendu)
  ← ScanResultDto (article ajouté ou erreur)

Angular basket.component
  → POST /api/pos/baskets/{id}/validate
  → BasketController → SaleService
  → Item @Version verrouillage optimiste → Sale persisté
  ← 200 OK ou 409 (liste de conflits)
  → POST /api/print/invoice (optionnel)
  → PrintQueueService → DocumentPrintService
```

**Flux de Données — Transition de Phase**
```
Admin phase-controls.component
  → PUT /api/editions/{id}/phase
  → EditionController → EditionService
  → Edition.phase mis à jour
  → SseEmitterRegistry.broadcast("phase-changed", payload)
  → Tous les clients EventSource connectés reçoivent l'événement
  → Angular phase.service met à jour Signal<Phase>
  → Les composants réagissent (panier annulé si actif)
```

---

## Résultats de Validation de l'Architecture

### Validation de Cohérence ✅

**Compatibilité des Décisions :**
Tous les choix technologiques sont mutuellement compatibles. Spring Boot 4.0.6 / Java 21 / Angular 21 / MariaDB / OpenPDF 3.0.0 / ZXing / Liquibase / MapStruct / Lombok fonctionnent sans conflits. Une note d'exécution : JPageFlow déclare `spring-data-commons:3.5.5` comme dépendance ; le BOM de Spring Boot 4.0.6 écrase cela avec Spring Data 4.x à l'exécution — pas de conflit attendu, mais à vérifier lors du premier build.

**Cohérence des Patrons :**
Les patrons d'implémentation sont pleinement alignés avec les décisions architecturales : JPageFlow pour tous les points d'entrée paginés, RFC 7807 pour toutes les erreurs, SSE pour la notification de phase, Signals pour l'état Angular, BigDecimal pour toutes les valeurs monétaires.

**Alignement de la Structure :**
La structure du projet supporte toutes les décisions architecturales. Backend en couches (Contrôleur → Service → Référentiel) appliqué via l'organisation des packages. Structure Angular par type (`components/`, `services/`, `models/`) cohérente avec le modèle de composants standalone.

---

### Validation de la Couverture des Exigences ✅

**Couverture des Fonctionnalités :**

| Groupe de fonctionnalités | Couvert par |
|---|---|
| F1 — i18n | `messages_*.properties` + `assets/i18n/*.json` + ngx-translate |
| F2 — Cycle de vie des éditions | `edition/` + `shared/sse/` + `phase.service.ts` |
| F3 — Vendeurs & articles | `seller/`, `item/`, `print/` |
| F4 — POS | `pos/` + verrouillage optimiste + annulation de panier SSE |
| F5 — Post-vente | `payout/` |
| F6 — Rapports | `report/` + OpenPDF 3.0.0 |
| F7 — Utilisateurs & auth | `user/` + Spring Security + Spring Session JDBC |
| F8 — Infrastructure | Docker Compose + Liquibase + `application.properties` |
| F9 — Impression | `print/` + `LinkedBlockingQueue` + ESC/POS + OpenPDF |
| F10 — Catalogue | `item/controller/ItemCatalogController` + JPageFlow |

**Exigences Non Fonctionnelles :**

| NFR | Traité par |
|---|---|
| NFR-001 Performance (RPi 4) | Threads virtuels Java 21 ; stack légère ; pas de complexité de cache |
| NFR-002 Concurrence | Verrouillage optimiste (`@Version`) + contrainte unique BDD + CI Testcontainers |
| NFR-003 Exactitude financière | Politique BigDecimal — appliquée au niveau des patrons |
| NFR-004 Compatibilité navigateur | REST + SPA ; pas d'APIs spécifiques au navigateur |
| NFR-005 Compatibilité scanner | Mapping de codes de touches Angular dans `scanner.component.ts` |
| NFR-006 Fiabilité | Transactions côté serveur ; Spring Session JDBC (sessions survivent au redémarrage) |
| NFR-007 RGPD | Anonymisation à la suppression du vendeur ; aucune DCP dans les journaux — appliqué dans les patrons |

---

### Résultats de l'Analyse des Écarts

**Écarts identifiés et résolus lors de la validation :**

| Écart | Priorité | Résolution |
|---|---|---|
| Entité `InstanceConfig` manquante (FR-073) | Critique | Ajout du package `shared/instanceconfig/` + Liquibase `004-instance-config.xml` |
| CLI de réinitialisation du mot de passe admin (FR-063) | Important | Ajout de `user/cli/AdminPasswordResetRunner.java` — Spring Boot `CommandLineRunner` déclenché via l'argument `--reset-admin-password` |

**Problème connu restant (reporté) :**

| Élément | Statut | Action |
|---|---|---|
| Bug de tri `BigDecimal` dans JPageFlow | Reporté — connu | Corriger dans la bibliothèque avant d'implémenter le tri par prix ; le test échouera jusqu'au correctif |

---

### Liste de Contrôle de Complétude Architecturale

**Analyse des Exigences**
- [x] Contexte du projet analysé en profondeur
- [x] Échelle et complexité évaluées (~100 vendeurs, ~1 700 articles, 3 postes POS)
- [x] Contraintes techniques identifiées (RPi 4, Docker Compose, politique de licences)
- [x] Préoccupations transversales cartographiées (machine à états de phase, i18n, RGPD, concurrence, file d'impression)

**Décisions Architecturales**
- [x] Décisions critiques documentées avec versions (Java 21, Spring Boot 4.0.6, Angular 21, OpenPDF 3.0.0)
- [x] Stack technologique entièrement spécifiée
- [x] Patrons d'intégration définis (SSE, REST, JPageFlow, Spring Session JDBC)
- [x] Considérations de performance adressées (threads virtuels, file en mémoire, pas de cache)

**Patrons d'Implémentation**
- [x] Conventions de nommage établies (snake_case BDD, camelCase JSON, kebab-case fichiers Angular)
- [x] Patrons de structure définis (sous-packages par fonctionnalité backend, Angular par type)
- [x] Patrons de communication spécifiés (événements SSE, erreurs RFC 7807, pagination JPageFlow)
- [x] Patrons de processus documentés (états de chargement, gestion d'erreurs, validation, clés i18n)

**Structure du Projet**
- [x] Structure de répertoires complète définie (backend + frontend)
- [x] Frontières des composants établies (shared/, packages de fonctionnalités, frontière d'impression)
- [x] Points d'intégration cartographiés (frontière API, SSE, file d'impression, flux de données)
- [x] Correspondance exigences-structure complète (F1–F10 + NFR-001–007)

---

### Évaluation de la Maturité Architecturale

**Statut Global : PRÊT POUR L'IMPLÉMENTATION**

**Niveau de confiance : Élevé**

**Points forts clés :**
- Stack « ennuyeuse par conception » — bien documentée, maintenable, pas de dépendances exotiques
- Toutes les licences sont permissives ou faiblement copyleft (MIT, Apache 2.0, LGPL) — pas d'AGPL
- Modèle de concurrence explicitement testé via Testcontainers
- Mode de défaillance de la file d'impression documenté et accepté
- La machine à états de phase est la colonne vertébrale architecturale — toutes les fonctionnalités s'y accrochent clairement

**Axes d'amélioration futurs (post-v1) :**
- Correctif BigDecimal de JPageFlow (appliquer avant la fonctionnalité de tri par prix)
- Pipeline CI Testcontainers (peut être ajouté de manière incrémentale)
- Mécanisme de sauvegarde/restauration (explicitement reporté à la v2)

---

### Passation pour l'Implémentation

**Première story d'implémentation :** Génération du squelette du projet
```bash
# Backend
spring init --boot-version=4.0.6 --java-version=21 --build=maven \
  --group-id=org.pluribourse --artifact-id=pluribourse \
  --dependencies=web,data-jpa,security,liquibase,lombok,validation,mariadb

# Frontend
ng new pluribourse-frontend --standalone --routing --style=scss
```

**Directives pour l'Agent IA :**
- Respecter scrupuleusement toutes les décisions architecturales telles que documentées — pas d'optimisations locales
- Utiliser `FilterService.filterData()` (JPageFlow) pour chaque point d'entrée paginé/filtrable
- Ne jamais utiliser `float` ou `double` pour les valeurs monétaires — `BigDecimal` uniquement
- Ne jamais journaliser les DCP — utiliser les IDs d'entités dans les journaux
- Retourner RFC 7807 Problem Details pour toutes les réponses d'erreur
- Respecter strictement la structure de packages `org.pluribourse.{fonctionnalité}.{couche}`
- Utiliser `signal()` / `computed()` pour l'état Angular — pas de `BehaviorSubject`
- Se référer à ce document pour toute question architecturale avant de prendre des décisions locales
