# Deferred Work

## Deferred from: code review of 1-1-mise-en-place-du-squelette-de-projet-baseline-docker-compose (2026-06-15)

- **`app.html` scaffold Angular avec strings codées en dur** — `pluribourse-frontend/src/app/app.html` — Accepté comme placeholder Story 1.1. Story 1.7 (Design System / Angular Material) remplacera le template complet avec composants i18n-compliant.
- **Hash BCrypt admin commis dans Liquibase** — `001-core-schema.xml:34` — Intentionnel (`force_password_change=true`), Story 1.2 implémentera le reset obligatoire au premier login. Risque résiduel : plaintext documenté dans le commentaire adjacent.
- **Spring Session H2 : slice tests sans Liquibase** — `002-spring-session.xml` — Les `@WebMvcTest` ou slice tests qui désactivent Liquibase échoueront silencieusement sur `SPRING_SESSION`. Aucun test affecté actuellement. À surveiller si des slice tests sont introduits.
- **Colonne `role` en `VARCHAR(20)` sans contrainte `CHECK`** — `001-core-schema.xml:19` — La couche JPA enforcer les valeurs valides via `@Enumerated(STRING)` quand l'entité `User` sera créée. Pas de risque tant qu'aucun accès DB direct n'existe.
- **Colonne `preferred_language` sans contrainte de valeurs** — `001-core-schema.xml:23` — Scope Story 1.6 (i18n complet). À adresser lors de la mise en place de la préférence de langue utilisateur.
- **ngx-translate : pas de langue de fallback explicite** — `app.config.ts:16` — Scope Story 1.6. Si un utilisateur switche vers une langue non supportée, ngx-translate retourne la clé brute. À corriger avec `defaultLang: 'fr'` lors de l'implémentation de la sélection de langue.

## Deferred from: code review of 1-2-authentification-spring-security-controle-dacces-base-sur-les-roles (2026-06-16)

- **`authGuard` ne vérifie pas `forcePasswordChange`** — fonctionne via fallback intercepteur (403 → interceptor → `/change-password`) mais cause un aller-retour serveur évitable. La spec montre le check dans le guard. À ajouter lors d'un refacto UX.
- **`adminGuard` redirige VOLUNTEER vers `/login`** — un VOLUNTEER authentifié accédant à `/admin` est redirigé vers `/login` au lieu de `/volunteer`. Mauvaise UX, pas de boucle infinie. À corriger Story 1.7 ou plus.
- **Pas de rate limiting sur `/login`** — protection brute-force à prévoir (Spring Security `LockingUserDetailsService` ou Bucket4j). Epic de hardening sécurité.
- **`loadUserByUsername` sans `@Transactional(readOnly = true)`** — optimisation mineure ; chaque auth ouvre une transaction RW. `PluriBourseUserDetailsService.java`.
- **`PluriBourseUserDetails` manque de `isEnabled`/`isAccountNonLocked`** — pas de colonne `enabled` sur `User` ; nécessaire pour verrouillage de compte Story 1.3+.
- **Colonne `preferred_language` sans contrainte CHECK DB** — valeurs invalides → `IllegalArgumentException` JPA. À ajouter en hardening schéma.
- **Changement de mot de passe concurrent depuis deux onglets** — second onglet garde contexte de sécurité périmé. Secondaire au Patch stale-forcePasswordChange.

## Deferred from: 2nd code review pass of 1-2-authentification-spring-security-controle-dacces-base-sur-les-roles (2026-06-16)

- **SELLER reçoit 200 au login puis 403 partout** — un compte SELLER peut s'authentifier (`/login` est `permitAll()`), obtient une session et un body JSON 200, puis est bloqué 403 sur tout autre endpoint. UX confuse mais comportement intentionnel : les SELLER ne sont pas des utilisateurs de l'interface web. À clarifier si des comptes SELLER avec accès web devaient exister.
- **`getRequestURI()` incluant le context-path dans `ForcePasswordChangeFilter`** — si `server.servlet.context-path` est configuré, les EXEMPT_PATHS ne matcheront plus. Latent ; Spring Boot déploie sans context-path par défaut. Passer à `getServletPath()` si un context-path est introduit.
- **`UserService.changePassword` lève `IllegalArgumentException` → 500** — si l'utilisateur est supprimé pendant sa session active, le changement de mot de passe retourne 500 au lieu de 404. Edge case extrême. À mapper en `EntityNotFoundException` dans un epic de hardening.
- **Changement de mot de passe n'invalide pas les autres sessions actives** — les sessions parallèles (autre navigateur/onglet) restent valides jusqu'à leur expiration (P1D). À adresser avec Spring Session registry si le besoin d'invalidation immédiate émerge.
- **Test AC3 ne prouve pas la persistence cross-restart** — `SecurityConfigIT.ac3_real_login_creates_spring_session_and_subsequent_request_succeeds` réutilise une `MockHttpSession` en mémoire, ne valide pas que la session est réellement lue depuis JDBC après redémarrage. La garantie de persistance repose sur le changeset 002 Liquibase et la config Spring Session.

## Deferred from: re-run code review of 1-1 (2026-06-16)

- **`index.html` `<title>` hardcodé** — `pluribourse-frontend/src/index.html:4` — `<title>PluribourseFrontend</title>` n'est pas i18n-compliant. Différé Story 1.7 (même scope que `app.html`). À remplacer par un appel dynamique via Angular `Title` service + clé ngx-translate.
- **`handleMethodArgumentNotValid` ignore les `getGlobalErrors()`** — `GlobalExceptionHandler.java` — Les erreurs de validateurs de classe (`@ScriptAssert`, `Validator` custom) ne sont pas incluses dans le `detail` RFC 7807. Sans impact tant qu'aucun validateur objet n'existe. À corriger quand des `@ScriptAssert` ou `Validator` custom seront introduits.
- **`proxy.conf.json` expose tous `/actuator/*`** — `pluribourse-frontend/proxy.conf.json` — Le proxy de dev (`ng serve`) proxie tous les sous-chemins actuator, contrairement à nginx (exact-match `/actuator/health`). Inconsistance dev-only. À tightener à `/actuator/health` pour aligner prod/dev.
- **`app.spec.ts` test scaffold cassera en Story 1.7** — `pluribourse-frontend/src/app/app.spec.ts` — Assertion `'Hello, pluribourse-frontend'` couplée au template scaffold. À supprimer ou remplacer (ex : `<router-outlet>` présent) lors du remplacement de `app.html`.
- **`logback-spring.xml` : enforcement PII structurel absent** — `src/main/resources/logback-spring.xml` — La politique no-PII est documentée en commentaire. Aucun `TurboFilter` ou masquage pattern n'est en place. Acceptable pour le skeleton ; à adresser si le besoin de garantie formelle émerge.

## Deferred from: code review of 1-3-gestion-des-comptes-benevoles (2026-06-22)

- **Route Angular `'users/create'` plate dans `adminRoutes`** — `pluribourse-frontend/src/app/features/admin/admin.routes.ts` — La route `{ path: 'users/create', ... }` est un sibling plat au lieu d'un enfant de `users`. Fonctionnel, mais non-idiomatique Angular et bloque le nesting futur (ex: `users/:id`). À restructurer en `{ path: 'users', children: [...] }` lors d'un refacto de routing.

## Deferred from: code review of 1-3-gestion-des-comptes-benevoles 2ème passe (2026-06-22)

- **Session active d'un bénévole désactivé reste valide jusqu'à expiration** — `UserService.java:disableVolunteer()` — `setEnabled(false)` en DB ne révoque pas la session Spring Session JDBC en cours. Le bénévole désactivé peut continuer à faire des requêtes jusqu'à l'expiration de sa session (défaut : 1 jour). Pré-existant, identique au defer Story 1.2 "Changement de mot de passe n'invalide pas les autres sessions actives". À adresser avec Spring Session registry si invalidation immédiate requise.
- **URL d'erreur `account-disabled` dupliquée en magic string Java + TypeScript** — `LoginFailureHandler.java` et `login.component.ts` — `"https://pluribourse/errors/account-disabled"` hardcodé dans les deux couches sans constante partagée. Une divergence silencieuse casse la détection côté frontend. Cross-langage, pas de solution compile-time. Risque faible à court terme.
