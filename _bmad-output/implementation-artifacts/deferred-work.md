# Deferred Work

## Résolutions constatées lors de l'audit du 2026-06-24

Les items suivants ont été résolus dans des stories ultérieures et ne nécessitent plus d'action :

- ~~**`app.html` scaffold Angular avec strings codées en dur**~~ → ✅ Résolu Story 1.7 : `app.html` = `<router-outlet />` uniquement
- ~~**`authGuard` ne vérifie pas `forcePasswordChange`**~~ → ✅ Résolu Story 1.2/1.3 : `auth.guard.ts` contient le check
- ~~**`adminGuard` redirige VOLUNTEER vers `/login`**~~ → ✅ Résolu Story 1.7 : redirige vers `/volunteer` si authentifié
- ~~**`index.html <title>` hardcodé `PluribourseFrontend`**~~ → ✅ Résolu Story 1.7 : `<title>PluriBourse</title>` (TitleStrategy dynamique = nouveau defer existant)
- ~~**`app.spec.ts` test scaffold cassera en Story 1.7**~~ → ✅ Résolu Story 1.7 : spec mis à jour avec tests router-outlet

## Correctifs appliqués hors story — 2026-06-24

- ~~**`proxy.conf.json` expose tous `/actuator/*`**~~ → ✅ Corrigé : path restreint à `/actuator/health`
- ~~**`ngx-translate` : pas de langue de fallback explicite**~~ → ✅ Corrigé : `defaultLang: 'fr'` ajouté dans `app.config.ts`
- ~~**`aria-label="Current phase"` masque la valeur de phase**~~ → ✅ Corrigé : `[attr.aria-label]` retiré du phase-chip ; texte visible = nom accessible

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

## Deferred from: code review of 1-5-configuration-de-linstance-page-de-parametres-admin (2026-06-23)

- **Pas de cache sur `findConfig()`** — `InstanceConfigService.java` — Chaque appel à `getDefaultDocumentLanguage()` et `getDefaultCommissionRate()` déclenche une requête DB séparée. Les appels depuis Story 2.1 dans la même transaction ouvriront des transactions imbriquées sans partage du cache JPA first-level. À adresser avec `@Cacheable` si les performances s'avèrent insuffisantes.
- **`Language.valueOf()` non-guardé dans `updateConfig()`** — `InstanceConfigService.java:28` — La protection repose uniquement sur `@Valid` au niveau contrôleur. Si le service est appelé directement (tâche planifiée, autre service), une valeur invalide lève une `IllegalArgumentException` → HTTP 500. À protéger avec `try/catch + ResponseStatusException` lors de l'introduction d'appels directs au service.
- **`IllegalStateException` depuis `findConfig()` non mappée** — `InstanceConfigService.java:43` — Si la ligne singleton id=1 est absente (migration non exécutée ou supprimée manuellement), l'exception remonte comme HTTP 500 avec message interne. Comportement 500 acceptable pour une panne d'infrastructure ; à revoir si un health-check expose ce cas.
- **Pas de bouton "Réessayer" après échec de chargement** — `admin-settings.component.html` — En cas d'erreur réseau transitoire au chargement, le formulaire est masqué sans possibilité de réessayer sans rechargement de page. Amélioration UX hors scope spec actuelle.
- **Soumission silencieuse sur formulaire invalide** — `admin-settings.component.ts:45` — Le guard `if (this.form.invalid || this.isSaving()) return` ne déclenche pas `markAllAsTouched()` ni n'affiche de message. L'utilisateur ne voit aucun retour visuel. Amélioration UX hors scope spec actuelle.

## Deferred from: code review of 1-5-configuration-de-linstance-page-de-parametres-admin 2ème passe (2026-06-23)

- **`InstanceConfigMapper` NPE si `defaultDocumentLanguage` null** — `InstanceConfigMapper.java:10` — `config.getDefaultDocumentLanguage().name()` dans l'expression MapStruct explose si le champ est `null`. Protégé par la contrainte DB `NOT NULL`, mais sans garantie Java de nullsafety. À adresser si la couche de persistance est contournée (migration, import direct).
- **Order(5) n'assert pas les champs non modifiés après PUT** — `InstanceConfigIT.java:Order(5)` — Le test ne vérifie que `defaultCommissionRate` après la mise à jour. Un bug de partial-write (autres champs remis à zéro) ne serait pas détecté. Les autres champs sont couverts par leurs propres tests dédiés.
- **État intermédiaire `isSaving()` non testé dans le spec** — `admin-settings.component.spec.ts` — Les tests ne vérifient que la valeur finale de `isSaving()`. Si `isSaving.set(true)` était supprimé d'`onSubmit()`, les tests passeraient quand même.

## Deferred from: code review of 1-6-preference-de-langue-utilisateur-infrastructure-i18n (2026-06-24)

- **Race condition sur premier login concurrent** — `LoginSuccessHandler.java` — Deux logins simultanés du même nouvel utilisateur lisent tous deux `languageInitialized=false` dans le principal d'authentification et appellent `initializeLanguage()` ; le second écrase le premier. Probabilité négligeable dans le cas d'usage cible (bourse).
- **NPE dans `LanguagePreferenceIT` Order(11) si Order(9) échoue** — `LanguagePreferenceIT.java` — `frUserUsername` est null si Order(9) ne s'exécute pas (filtre de test IDE). Limitation inhérente au pattern de scénario ordonné documenté dans CLAUDE.md.
- **Bannière `saveSuccess` persistante après changement de select sans resoumission** — `account.component.ts` — Amélioration UX : effacer `saveSuccess` sur `valueChanges`. Non spécifié dans les AC.
- **Admin seedé perd sa langue FR au 1er login post-migration 006** — `006-user-language-initialized.xml` — Migration ajoute `language_initialized = false` sur tous les utilisateurs existants. L'admin reçoit la langue du navigateur au 1er login. Comportement intentionnel selon la spec ; pas de backfill prévu.
- **`Language.valueOf()` non gardé dans AccountController** — `AccountController.java` — Si `@Pattern` est correctement ancré (patch P1), ce cas ne peut pas survenir. À garder en tête si le DTO évolue.
- **`restoreSession()` ne restaure pas la langue sur le chemin 403 `forcePasswordChange`** — `pluribourse-frontend/src/app/services/auth.service.ts:61` — La page `/change-password` s'affiche en 'en' quelle que soit la préférence langue de l'utilisateur. Un fix propre nécessiterait d'inclure `preferredLanguage` dans le body de la réponse 403, ce qui est hors scope de cette story.

## Deferred from: code review of 1-3-gestion-des-comptes-benevoles (2026-06-22)

- **Session active d'un bénévole désactivé reste valide jusqu'à expiration** — `UserService.java:disableVolunteer()` — `setEnabled(false)` en DB ne révoque pas la session Spring Session JDBC en cours. Le bénévole désactivé peut continuer à faire des requêtes jusqu'à l'expiration de sa session (défaut : 1 jour). Pré-existant, identique au defer Story 1.2 "Changement de mot de passe n'invalide pas les autres sessions actives". À adresser avec Spring Session registry si invalidation immédiate requise.
- **URL d'erreur `account-disabled` dupliquée en magic string Java + TypeScript** — `LoginFailureHandler.java` et `login.component.ts` — `"https://pluribourse/errors/account-disabled"` hardcodé dans les deux couches sans constante partagée. Une divergence silencieuse casse la détection côté frontend. Cross-langage, pas de solution compile-time. Risque faible à court terme.

## Deferred from: code review of 1-7-systeme-de-design-angular-material-mise-en-page-applicative — Pass 2 (2026-06-24)

- **`volunteerRoutes` array vide** — `pluribourse-frontend/src/app/features/volunteer/volunteer.routes.ts` — Un admin redirigé vers `/volunteer` par `adminGuard` voit un contenu vide. Pré-existant ; routes bénévoles définies à partir de l'Epic 3.
- **`authInterceptor` coupe la session sur tout 403** — `pluribourse-frontend/src/app/core/interceptors/auth.interceptor.ts` — Le 403 d'une ressource admin protégée (feature flag, permission granulaire) déconnecte l'utilisateur comme un 403 d'expiration de session. Pré-existant.
- **Hard reload `forcePasswordChange=true` bloque `/change-password`** — `auth.service.ts:63 / auth.guard.ts` — `restoreSession()` retourne early sans setter `currentUser`, `authGuard` refuse alors `/change-password`. Variante du defer Story 1.6 "restoreSession ne restaure pas la langue sur le chemin 403". Pré-existant.

## Deferred from: code review of 1-7-systeme-de-design-angular-material-mise-en-page-applicative (2026-06-24)

- **"Édition active" section sans liens de navigation** — `app-layout.component.html:34-37` — La section "Édition active" affiche uniquement son label sans `<a>` items. Explicitement différé à l'Epic 2 (Story 2.1+) dans les notes de dev de la story.
- **Topbar `position: sticky` vs spec "fixed"** — `app-layout.component.scss:27` — La spec dit "fixed topbar" mais l'implémentation utilise `sticky`. Comportement équivalent dans le layout CSS grid actuel (`.app-shell` sans overflow, scroll au niveau document). À surveiller si la structure du shell évolue.
- **`forcePasswordChange: undefined` contourne le guard** — `auth.guard.ts` — Si l'API retourne un 200 sans le champ `forcePasswordChange`, le guard ne redirige pas vers `/change-password`. Pré-existant, non introduit par cette story.
- **Race condition de restauration de session** — `auth.guard.ts` / `app.config.ts` — Entre le lancement de `restoreSession` et la fin de la résolution, si le guard évalue `currentUser === null`, un utilisateur valide est renvoyé au login. Pré-existant.
- **`logout()` sans gestion d'erreur / flash cosmétique** — `app-layout.component.ts:22-24` — `AuthService.logout()` utilise un `finally` qui navigue toujours vers `/login`. Un échec POST n'empêche pas la déconnexion, mais l'absence de `try/catch` dans le composant supprime tout feedback d'erreur visible. Flash d'un frame : role-badge passe à "volunteer" avant la navigation.
- **Google Fonts sans attributs SRI ni crossorigin** — `index.html:9-10` — Deux `<link>` Google Fonts sans `integrity` ni `crossorigin`. Risque de sécurité faible pour un outil auto-hébergé ; les polices pourraient être intégrées localement en hardening.
- **Double source CSS (`mat.theme()` + `:root`) fragile aux mises à jour** — `styles.scss` — Angular Material M3 génère ses variables `--mat-sys-*` via `mat.theme()`, puis `:root` les écrase immédiatement. Les variables émises par les composants Material dans leur propre portée (`:host`) peuvent re-overrider ces valeurs. À surveiller lors des upgrades Angular Material.
- **`!important` sur `.sidebar__item--active`** — `app-layout.component.scss` — `routerLinkActive` injecte la classe sur l'élément ; `!important` est nécessaire uniquement si des styles Material conflictuels interfèrent. À retirer si plus nécessaire lors d'une revue CSS.
- **`aria-label="Current phase"` ne correspond pas au texte visible** — `app-layout.component.html:13` — Le chip affiche "Préparation" / "Preparation" mais son `aria-label` dit "Phase actuelle" / "Current phase". Un lecteur d'écran annonce "Phase actuelle" sans la valeur de la phase. Amélioration accessibilité mineure.
- **Tokens CSS non utilisés définis dans `:root`** — `styles.scss` — `--pb-primary-hover`, `--pb-success-container`, `--pb-on-success-container` sont déclarés sans usage dans cette story. Tokens intentionnels pour usage futur ; à supprimer si jamais implémentés dans un composant.
- **Pas de `TitleStrategy` pour les routes enfants** — Chaque page affiche "PluriBourse" dans l'onglet navigateur. À adresser avec `TitleStrategy` + clés i18n pour améliorer la lisibilité de l'historique et les annonces de changement de page pour les lecteurs d'écran.
- **`routerLinkActive` en mode prefix sur les liens sidebar** — `app-layout.component.html:43-47` — `/admin/users/create` activera le lien "Bénévoles" (prefixe match). Comportement intentionnel pour l'UX actuelle ; à surveiller si des routes enfants profondes créent des ambiguïtés.
- **Pas de breakpoint responsive pour le sidebar** — `app-layout.component.scss` — `has-sidebar` = `200px 1fr` sans `min-width` sur la colonne content. La story est explicitement desktop-only (v1). À adresser en Epic de responsive design.
- **Logo et position du bouton logout non assertés dans les tests** — `app-layout.component.spec.ts` — Tests vérifient la présence de `.topbar` mais pas `.topbar__logo` ni que `.btn-ghost` est enfant de `.topbar__actions`. Couverture suffisante pour la story, à renforcer si des régressions surviennent.
- **`button-primary` CSS absent des global styles** — `styles.scss` — DESIGN.md spécifie un composant `button-primary` (`background: #C44626, padding: 10px 20px`). Aucune classe ni token dédié n'est défini dans `styles.scss`. À implémenter lors de la première story introduisant un bouton primaire dans le shell ou les pages enfants.

## Deferred from: code review of 1-10-ameliorations-ux-mots-de-passe (2026-06-26)

- **Multiple `Validators.pattern()` calls collide on same `pattern` error key** — `passwords-match.validator.ts`, `reset-password-dialog.component.ts`, `change-password.component.ts` — Both pattern validators (`/.*[A-Z]*/` and `/.*[0-9]*/`) write under the Angular-reserved `pattern` key; the second silently overwrites the first. Pre-existing across the codebase, not introduced by this story. To fix: replace with a custom named validator (`Validators.pattern` → dedicated `hasUppercase`, `hasDigit` validators).
- **`mat-error` in reset dialog shows only minLength regardless of which pattern failed** — `reset-password-dialog.component.html` — Consistent with existing `change-password` form behavior. No per-validator error messages specified in AC2. To fix when the full validation error strategy is revisited.
- **Shared `submitting` signal creates table-wide lock on all reset buttons** — `user-list.component.ts` — While a password reset is in flight, all other reset buttons in the user table are disabled. The old inline form only gated its own submit button. Acceptable for single-user-at-a-time admin workflows; to refactor to per-row state if concurrent admin scenarios are introduced.

## Deferred from: code review of 1-9-guide-dinstallation (2026-06-26)

- **Pas de recommandation de sauvegarde avant mise à jour** — `GUIDE_INSTALLATION.md` § Mise à jour — Bonne pratique non requise par la spec (AC6 précise seulement que les données sont préservées). À ajouter si un incident de migration survient en production.
- **Pas d'instruction pour arrêter l'application** — `GUIDE_INSTALLATION.md` — `docker compose down` / `docker compose stop` non couverts. Hors scope story 1.9 ; à inclure dans une révision ultérieure du guide.
- **Promesse "aucune connaissance technique requise" partiellement contredite** — `GUIDE_INSTALLATION.md` introduction — Tension inhérente entre la promesse d'accessibilité totale et les étapes en terminal nécessaires. Non actionnable sans réécriture du positionnement du guide.

## Deferred from: code review of 1-8-composants-ui-partages-boites-de-dialogue-notifications-accessibilite (2026-06-25)

- **confirm-dialog — fermeture backdrop émet `undefined`** — `confirm-dialog.service.ts` — `disableClose: false` permet à CDK de fermer sans valeur. Aucun appelant dans ce diff ; à gérer avec `filter(v => v !== undefined)` ou en mapant undefined → false dans le service lors de l'implémentation de l'appelant.
- **Toast `z-index: 200` — nombre magique sans token** — `toast-container.component.scss` — La valeur 200 n'est pas documentée dans le système de tokens `--pb-*`. À extraire en `--pb-z-toast: 200` lors d'un audit CSS.
- **`ToastService` — pas de nettoyage du timer sur destroy** — `toast.service.ts` — Aucun hook `DestroyRef.onDestroy`. Risque faible pour un service root ; les tests utilisent `vi.useRealTimers()` en afterEach. À adresser si des instances non-root sont introduites.
- **Sidebar — focus ring corail potentiellement invisible sur fond sombre** — `app-layout.component.scss` — La règle `a:focus-visible` globale s'applique aux liens sidebar, mais le fond sombre (`--mat-sys-on-primary`) peut masquer l'outline corail. À vérifier manuellement.
- **`user-list` — route `/admin/users/create` codée en dur** — `user-list.component.ts` — `navigateByUrl('/admin/users/create')` absolu vs `routerLink="create"` relatif dans le template. Fragile aux refactos de routing. À unifier avec l'item différé "Route Angular plate" de Story 1.3.
- **`user-list` — champ reset-password utilise `<input>` brut** — `user-list.component.html` — Compromis intentionnel pour l'espace réduit d'une cellule de tableau. À migrer vers `mat-form-field` si le design évolue vers une modale ou un drawer.
- **Auth — erreur affichée via `<app-notification-inline>` au lieu de `<p class="auth-card__error">`** — `login.component.html`, `change-password.component.html` — Écart par rapport aux templates spec T9.3/T9.6, amélioration UX intentionnelle. La classe CSS `.auth-card__error` est morte dans `styles.scss`.
- **T9.5 — `ChangePasswordComponent` : logique hors périmètre spec** — `change-password.component.ts` — Injection `ToastService`/`TranslateService` et toast de succès ajoutés hors du scope T9.5. Amélioration intentionnelle mais non spécifiée.
- **`change-password` — `showSuccess()` avant `await router.navigate()`** — `change-password.component.ts:37` — Le toast succès s'affiche avant la fin de la navigation. Risque négligeable (routes statiques, pas de lazy loading sur ce chemin).

## Deferred from: code review of 2-1-crud-dedition-configuration-du-taux-de-commission (2026-06-28)

- **Race condition création concurrente d'édition** — `EditionService.java:createEdition()` — check-then-insert sans contrainte DB unique partielle sur phases actives ; deux POST simultanés peuvent créer deux éditions actives. Fix: contrainte `UNIQUE` partielle sur la colonne `phase` limitée aux phases actives, ou isolation `SERIALIZABLE`. Low risk pour une plateforme mono-admin.
- **Assertion BigDecimal style** — `EditionManagementIT.java:@Order(7)` — `compareTo(new BigDecimal("15")).isZero()` → préférer `isEqualByComparingTo(new BigDecimal("15"))` pour des messages d'échec lisibles dans les rapports JUnit.
- **Race condition double-clic Delete** — `edition-list.component.ts:confirmDelete()` — sans guard `isDeleting`, deux confirmations rapides envoient deux DELETE ; le second reçoit 404 et affiche un toast d'erreur alors que la suppression a réussi.
- **`createdEditionId` null en cascade** — `EditionManagementIT.java` — si `@Order(4)` échoue, les tests `@Order(5)` à `@Order(16)` génèrent des NPE ou URL `/api/admin/editions/null/...` masquant la vraie cause. Pre-existing pattern des tests IT ordonnés.
- **AC7/AC8 couverture partielle** — `EditionManagementIT.java` — seul `DEPOSIT` est testé pour le verrouillage du taux de commission et le refus de suppression ; `SALE`, `POST_SALE` et `CLOSED` ne sont pas couverts. Le code `!= PhaseType.PREPARATION` est correct ; à compléter en Story 2.2 quand les transitions de phase seront implémentées.
- **POST/PATCH/DELETE non testés avec session volunteer** — `EditionManagementIT.java` — seul GET retournant 403 est testé ; les mutations (créer, modifier, supprimer) ne vérifient pas le 403 pour un volunteer. Sécurité appliquée au niveau `SecurityConfig` + `@PreAuthorize`.
- **`reloadEditions()` laisse l'UI dans un état visuellement incohérent** — `edition-list.component.ts:reloadEditions()` — après delete réussi suivi d'une erreur réseau sur GET, la table disparaît (`@if (!error())`) et affiche "Failed to load editions" bien que le delete ait réussi. Fix propre : optimistic update ou conserver les données précédentes si le reload échoue.

## Deferred from: Story 2.3 — blocage-benevoles-sans-edition-active (2026-06-29)

- **URL d'erreur `no-active-edition` dupliquée en magic string Java + TypeScript** — `LoginSuccessHandler.java` et `login.component.ts` — `"https://pluribourse/errors/no-active-edition"` hardcodé dans les deux couches sans constante partagée. Même pattern que le defer Story 1.3 sur `account-disabled`. Une divergence silencieuse casse la détection côté frontend. Cross-langage, pas de solution compile-time. Risque faible à court terme.

## Deferred from: code review of 2-3-blocage-benevoles-sans-edition-active (2026-06-30)

- **`ACTIVE_PHASES` dupliqué dans `LoginSuccessHandler` et `EditionService`** — `LoginSuccessHandler.java:28` — La constante `List.of(PREPARATION, DEPOSIT, SALE, POST_SALE)` existe en double. Si une phase est ajoutée (ex: `CHECKOUT`), les deux constantes doivent être mises à jour manuellement. Refacto possible : exposer la constante depuis `EditionService` ou créer une constante partagée dans un objet de domaine. Risque faible à court terme.
- **Timing oracle — 401 `no-active-edition` révèle des credentials valides hors-saison** — `LoginSuccessHandler.java:53` — Un attaquant peut distinguer "mauvais mot de passe" (401 générique) de "bon mot de passe mais aucune édition active" (401 + `type: no-active-edition`) pour énumérer des usernames bénévoles valides. Risque faible pour une plateforme locale non exposée sur Internet ; comportement imposé par la spec (AC1). À réévaluer si le contexte de déploiement change.
- **Gate d'autorisation dans `onAuthenticationSuccess` — session brièvement persistée avant cleanup** — `LoginSuccessHandler.java:53` — Spring écrit la session en base avant d'appeler le handler. Le `session.invalidate()` nettoie l'entrée ensuite. Dans un déploiement clustérisé, la session pourrait être répliquée avant invalidation. Acceptable pour un déploiement single-instance (Docker Compose). À adresser si un cluster est envisagé.
- **`EditionManagementIT @Order(9)` envoie `null` commissionRate pour contourner le frozen-rate check** — `EditionManagementIT.java:160` — La valeur précédente `new BigDecimal("15.00")` testait l'envoi du même taux en DEPOSIT. La correction à `null` évite le check mais ne couvre plus le cas "même valeur que le taux gelé". À investiguer si `EditionService` doit accepter ou rejeter une mise à jour idempotente du taux en DEPOSIT.
- **HTTP 422 pour auto-protection admin (disable/change-password/delete self)** — `UserManagementIT.java:158,197,293` — RFC 9110 définit 422 pour des erreurs sémantiques sur le corps de la requête, pas pour des contraintes de règle métier (l'admin ne peut pas se désactiver lui-même). 403 Forbidden ou 409 Conflict seraient plus corrects. Comportement pré-existant en production, non introduit par Story 2.3.

## Deferred from: code review of 2-4-dates-debut-fin-edition (2026-06-30)

- **`updateEdition` écrase startDate/endDate inconditionnellement (null = effacement), asymétrique avec commissionRate/documentLanguage** — `EditionService.java:updateEdition()` — Intentionnel par la spec (Dev Notes: "Setting null explicitly is correct — it clears the field"). Tout appelant futur qui fait un PUT partiel (sans inclure les dates courantes) effacera silencieusement les dates. À documenter sur le contrat API ou à aligner avec le pattern `if (!= null)` des autres champs lors d'un refacto.

## Deferred from: code review of 2-2-controle-du-cycle-de-phases-boites-de-dialogue-de-confirmation (2026-06-29)

- **Async callback écrit sur les signals après destruction du composant** — `phase-control.component.ts` — `takeUntilDestroyed` annule la souscription Observable mais ne cancelle pas le `firstValueFrom` en vol. Si le composant est détruit pendant une transition, le `finally` block écrit sur les signals d'un composant détruit. Pas de crash en mode Signal Angular ; orphaned write sans conséquence visible. À adresser si des `ExpressionChangedAfterItHasBeenCheckedError` sont observées en dev.
- **`PhaseTransitionIT` cascade de null** — `PhaseTransitionIT.java` — `editionId` reste `null` si `@Order(1)` échoue. Les tests suivants génèrent des URLs `/api/admin/editions/null/...` avec des 404 trompeurs. Pre-existing pattern des tests IT ordonnés documenté dans CLAUDE.md. Sans `Assumptions.assumeTrue(editionId != null)` sur les autres méthodes, le diagnostic est bruyant.
