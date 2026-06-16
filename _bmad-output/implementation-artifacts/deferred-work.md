# Deferred Work

## Deferred from: code review of 1-1-mise-en-place-du-squelette-de-projet-baseline-docker-compose (2026-06-15)

- **`app.html` scaffold Angular avec strings codées en dur** — `pluribourse-frontend/src/app/app.html` — Accepté comme placeholder Story 1.1. Story 1.7 (Design System / Angular Material) remplacera le template complet avec composants i18n-compliant.
- **Hash BCrypt admin commis dans Liquibase** — `001-core-schema.xml:34` — Intentionnel (`force_password_change=true`), Story 1.2 implémentera le reset obligatoire au premier login. Risque résiduel : plaintext documenté dans le commentaire adjacent.
- **Spring Session H2 : slice tests sans Liquibase** — `002-spring-session.xml` — Les `@WebMvcTest` ou slice tests qui désactivent Liquibase échoueront silencieusement sur `SPRING_SESSION`. Aucun test affecté actuellement. À surveiller si des slice tests sont introduits.
- **Colonne `role` en `VARCHAR(20)` sans contrainte `CHECK`** — `001-core-schema.xml:19` — La couche JPA enforcer les valeurs valides via `@Enumerated(STRING)` quand l'entité `User` sera créée. Pas de risque tant qu'aucun accès DB direct n'existe.
- **Colonne `preferred_language` sans contrainte de valeurs** — `001-core-schema.xml:23` — Scope Story 1.6 (i18n complet). À adresser lors de la mise en place de la préférence de langue utilisateur.
- **ngx-translate : pas de langue de fallback explicite** — `app.config.ts:16` — Scope Story 1.6. Si un utilisateur switche vers une langue non supportée, ngx-translate retourne la clé brute. À corriger avec `defaultLang: 'fr'` lors de l'implémentation de la sélection de langue.

## Deferred from: re-run code review of 1-1 (2026-06-16)

- **`index.html` `<title>` hardcodé** — `pluribourse-frontend/src/index.html:4` — `<title>PluribourseFrontend</title>` n'est pas i18n-compliant. Différé Story 1.7 (même scope que `app.html`). À remplacer par un appel dynamique via Angular `Title` service + clé ngx-translate.
- **`handleMethodArgumentNotValid` ignore les `getGlobalErrors()`** — `GlobalExceptionHandler.java` — Les erreurs de validateurs de classe (`@ScriptAssert`, `Validator` custom) ne sont pas incluses dans le `detail` RFC 7807. Sans impact tant qu'aucun validateur objet n'existe. À corriger quand des `@ScriptAssert` ou `Validator` custom seront introduits.
- **`proxy.conf.json` expose tous `/actuator/*`** — `pluribourse-frontend/proxy.conf.json` — Le proxy de dev (`ng serve`) proxie tous les sous-chemins actuator, contrairement à nginx (exact-match `/actuator/health`). Inconsistance dev-only. À tightener à `/actuator/health` pour aligner prod/dev.
- **`app.spec.ts` test scaffold cassera en Story 1.7** — `pluribourse-frontend/src/app/app.spec.ts` — Assertion `'Hello, pluribourse-frontend'` couplée au template scaffold. À supprimer ou remplacer (ex : `<router-outlet>` présent) lors du remplacement de `app.html`.
- **`logback-spring.xml` : enforcement PII structurel absent** — `src/main/resources/logback-spring.xml` — La politique no-PII est documentée en commentaire. Aucun `TurboFilter` ou masquage pattern n'est en place. Acceptable pour le skeleton ; à adresser si le besoin de garantie formelle émerge.
