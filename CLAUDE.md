# CLAUDE.md — PluriBourse

## Présentation du projet
PluriBourse est une plateforme auto-hébergée de gestion d'événements pour les associations organisant des bourses d'occasion (jouets, livres, skis, vêtements, etc.). Elle couvre le cycle de vie complet d'un événement : inscription des vendeurs, catalogage des articles avec génération d'étiquettes-codes-barres, caisse multi-postes par scan, et calcul automatique des reversements vendeurs.

Stack : Spring Boot (backend) + Angular (frontend), déployé via Docker Compose avec MariaDB.

## Langue
- Code (variables, méthodes, classes, packages) : anglais
- Commentaires et JavaDoc : anglais
- Documentation projet (artefacts de planification, PRD, architecture, epics, UX) : français

## Interaction utilisateur
- **TOUJOURS** parler en Français à l'utilisateur.
- **TOUJOURS** vérifier si un changement dans le code est valide ou challengeable (avec une argumentation détaillée) si un utilisateur fait une telle demande.
- **TOUJOURS** proposer de rédiger une nouvelle story si un changement de code est trop impactant.

## Budget IA
- **TOUJOURS** vérifier que le crédit restant est suffisant avant de développer quoi que ce soit ou d'utiliser un skill.
- **TOUJOURS** informer l'utilisateur s'il reste moins de 10% de crédit et lui demander s'il veut continuer.

## Environnement de développement local
- **TOUJOURS** partir du principe que les comptes présents dans la base de dev locale (MariaDB) sont des comptes réels de l'utilisateur, pas des fixtures de test.
- **JAMAIS** créer, réinitialiser ou modifier un mot de passe (admin ou autre) dans l'environnement de développement local, même via un outil CLI prévu à cet effet (`reset-admin-password`, `create-admin`, etc.).
- **JAMAIS** modifier les données de la base de développement locale sans confirmation explicite préalable.
- **TOUJOURS** demander à l'utilisateur de vérifier lui-même pour une vérification visuelle.

## Architecture

### Backend (Spring Boot)
- Package racine : `org.pluribourse`
- Architecture en couches : Contrôleur → Service → Repository
- DTOs pour la couche API
- MapStruct pour le mapping entité to DTO, DTO to entité, mises à jour de l'entité par le DTO
- Lombok pour le code répétitif (getters, setters, builders, constructeurs)
- Migrations de base de données : Liquibase
— **TOUJOURS** déclarer le type explicite des variables
- **JAMAIS** de mot-clé `var`

### Frontend (Angular)
- Composants standalone (dernière version Angular)
- Gestion d'état : Signals — pas de NgRx
- **TOUJOURS** créer un nouveau fichier html
- **JAMAIS** de template inline.

## JavaDoc
- Obligatoire sur les méthodes complexes : logique non triviale, paramètres ou valeurs de retour non évidents
- Non requise sur les getters, setters simples ou les opérations CRUD explicites

## Style de code (back + front)
- **TOUJOURS** utiliser des accolades pour les blocs `if`, `else`, `for`, `while` — même si le corps tient sur une ligne
- **JAMAIS** de style inline : `if (condition) return;` → toujours développer avec `{ }` sur plusieurs lignes

## Commentaires
- Ajouter des commentaires inline uniquement quand le **pourquoi** n'est pas évident depuis le code
- **TOUJOURS** utiliser des identifiants bien nommés
- **JAMAIS** décrire ce que fait le code (les identifiants bien nommés s'en chargent)
- Eviter les commentaires multi-lignes sauf nécessité pour du code complexe

## Tests

### Backend (Spring Boot)
- Frameworks : JUnit 5, pas de Mockito sauf pour les composants externes (email, API tierce)
- **Philosophie : E2E par les contrôleurs uniquement.** On ne teste pas les couches en isolation (pas de tests de service seuls, pas de tests de migration Liquibase, pas de tests de config Spring Security). Chaque test passe par le contrôleur HTTP et vérifie l'état en BDD après.
- **Une classe = un scénario métier**, lu comme un story-board. Les tests sont ordonnés avec `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` + `@Order(N)`. Les données persistent entre les méthodes (pas de `@Transactional` au niveau classe).
- **Infrastructure de base :**
  - Toutes les classes IT étendent `org.pluribourse.shared.IntegrationTest` (`@SpringBootTest` + `@DirtiesContext(classMode = AFTER_CLASS)` + `@TestInstance(Lifecycle.PER_CLASS)`)
  - `@DirtiesContext` remet la base H2 à zéro entre les classes via `spring.liquibase.drop-first=true`
  - `@TestInstance(PER_CLASS)` permet de conserver les sessions MockMvc (`MockHttpSession`) et les IDs entre les méthodes de test
  - Le changelog de test est `src/test/resources/db/changelog/db.changelog-test.xml` — il inclut le master + `test-data.sql`
  - Les données de référence sont dans `src/test/resources/db/changelog/test-data.sql` : `test_admin` (ADMIN, `forcePasswordChange=false`), `volunteer1`, `volunteer2`
- **Ce qu'on ne teste pas séparément :** migrations Liquibase, config Spring Security, handlers d'erreur, filtres — ils sont couverts implicitement par les scénarios E2E
- Couverture minimale cible : 80 %

### Frontend (Angular)
- Frameworks : Vitest (via `ng test` / `npm test` dans `pluribourse-frontend/`)
- Commande : `npm test` (dans `pluribourse-frontend/`) — ne pas utiliser `npx vitest run` directement
- Couverture minimale cible : 80 %

## Contraintes clés
- Tous les calculs financiers (commission, reversements) : utiliser `BigDecimal` — jamais `float` ou `double`
- Tous les textes de l'interface doivent passer par le système i18n (ngx-translate) — pas de chaînes codées en dur dans les templates ou les composants
- Pas de données personnelles (nom du vendeur, email, numéro de téléphone) dans les logs applicatifs