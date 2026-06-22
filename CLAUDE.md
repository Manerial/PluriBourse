# CLAUDE.md — PluriBourse

## Présentation du projet
PluriBourse est une plateforme auto-hébergée de gestion d'événements pour les associations organisant des bourses d'occasion (jouets, livres, skis, vêtements, etc.). Elle couvre le cycle de vie complet d'un événement : inscription des vendeurs, catalogage des articles avec génération d'étiquettes-codes-barres, caisse multi-postes par scan, et calcul automatique des reversements vendeurs.

Stack : Spring Boot (backend) + Angular (frontend), déployé via Docker Compose avec MariaDB.

## Langue
- Code (variables, méthodes, classes, packages) : anglais
- Commentaires et JavaDoc : anglais
- Documentation projet (artefacts de planification, PRD, architecture, epics, UX) : français

## Architecture

### Backend (Spring Boot)
- Package racine : `org.pluribourse`
- Architecture en couches : Contrôleur → Service → Repository
- DTOs pour la couche API ; MapStruct pour le mapping entité↔DTO
- Lombok pour le code répétitif (getters, setters, builders, constructeurs)
- Migrations de base de données : Liquibase

### Frontend (Angular)
- Composants standalone (dernière version Angular)
- Gestion d'état : Signals — pas de NgRx
- Pas de template inline. Toujours créer un nouveau fichier html

## JavaDoc
- Obligatoire sur les méthodes complexes : logique non triviale, paramètres ou valeurs de retour non évidents
- Non requise sur les getters, setters simples ou les opérations CRUD explicites

## Commentaires
- Ajouter des commentaires inline uniquement quand le **pourquoi** n'est pas évident depuis le code
- Ne jamais décrire ce que fait le code — des identifiants bien nommés s'en chargent
- Pas de blocs de commentaires multi-lignes

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
- Frameworks : Jest + Jasmine
- Couverture minimale cible : 80 %

## Git

### Messages de commit
Suivre les Conventional Commits :
- `feat:` nouvelle fonctionnalité
- `fix:` correction de bug
- `docs:` documentation uniquement
- `refactor:` modification du code sans impact fonctionnel
- `test:` ajout ou mise à jour de tests
- `chore:` outillage, dépendances, configuration

### Nommage des branches
`FEATURE-[FEATURE_ID]-[description-courte]`
- Les IDs de feature correspondent aux groupes de fonctionnalités du PRD (F1–F10)
- Exemple : `FEATURE-F3-seller-registration`

## Contraintes clés
- Tous les calculs financiers (commission, reversements) : utiliser `BigDecimal` — jamais `float` ou `double`
- Tous les textes de l'interface doivent passer par le système i18n (ngx-translate) — pas de chaînes codées en dur dans les templates ou les composants
- Pas de données personnelles (nom du vendeur, email, numéro de téléphone) dans les logs applicatifs
