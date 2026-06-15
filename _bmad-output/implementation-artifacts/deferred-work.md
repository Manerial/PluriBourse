# Deferred Work

## Deferred from: code review of 1-1-mise-en-place-du-squelette-de-projet-baseline-docker-compose (2026-06-15)

- **`app.html` scaffold Angular avec strings codées en dur** — `pluribourse-frontend/src/app/app.html` — Accepté comme placeholder Story 1.1. Story 1.7 (Design System / Angular Material) remplacera le template complet avec composants i18n-compliant.
- **Hash BCrypt admin commis dans Liquibase** — `001-core-schema.xml:34` — Intentionnel (`force_password_change=true`), Story 1.2 implémentera le reset obligatoire au premier login. Risque résiduel : plaintext documenté dans le commentaire adjacent.
- **Spring Session H2 : slice tests sans Liquibase** — `002-spring-session.xml` — Les `@WebMvcTest` ou slice tests qui désactivent Liquibase échoueront silencieusement sur `SPRING_SESSION`. Aucun test affecté actuellement. À surveiller si des slice tests sont introduits.
- **Colonne `role` en `VARCHAR(20)` sans contrainte `CHECK`** — `001-core-schema.xml:19` — La couche JPA enforcer les valeurs valides via `@Enumerated(STRING)` quand l'entité `User` sera créée. Pas de risque tant qu'aucun accès DB direct n'existe.
- **Colonne `preferred_language` sans contrainte de valeurs** — `001-core-schema.xml:23` — Scope Story 1.6 (i18n complet). À adresser lors de la mise en place de la préférence de langue utilisateur.
- **ngx-translate : pas de langue de fallback explicite** — `app.config.ts:16` — Scope Story 1.6. Si un utilisateur switche vers une langue non supportée, ngx-translate retourne la clé brute. À corriger avec `defaultLang: 'fr'` lors de l'implémentation de la sélection de langue.
