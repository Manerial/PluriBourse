# AR + ECH Review — Stories 1.1 & 1.2
**Date :** 2026-06-16
**Périmètre :** Backend Spring Boot + Frontend Angular (Stories 1.1 skeleton + 1.2 authentification)
**Méthode :** Adversarial Review (AR) + Edge Case Hunter (ECH)

---

## Critique — blocage ou corruption d'état

### ✅ C1 — AuthController : perte de récupération si re-fetch échoue après changePassword
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/user/controller/AuthController.java:51`
**Problème (AR) :** `userRepository.findById().orElseThrow()` après `userService.changePassword()` lève `NoSuchElementException` (pas `BusinessException`) si l'utilisateur a été supprimé entre les deux appels. Le mot de passe est déjà changé en base, mais le SecurityContext n'est pas mis à jour → l'utilisateur est bloqué à vie par `ForcePasswordChangeFilter` sans chemin de récupération.
**Fix suggéré :** Wrapper le re-fetch + refresh SecurityContext dans un try-catch ; si le re-fetch échoue, logguer et retourner 200 quand même (le mot de passe est sauvegardé). Déplacer le re-fetch dans `UserService`.

---

### ✅ C2 — AuthService.restoreSession + interceptor 403 : boucle infinie potentielle
**Fichier :** `pluribourse-frontend/src/app/services/auth.service.ts:45-52`
**Problème (ECH) :** Si `GET /api/auth/me` retourne 403 `password-change-required` pendant `restoreSession` : l'interceptor navigue vers `/change-password` ET le bloc `catch` met `currentUser` à null. L'`authGuard` sur `/change-password` voit `isAuthenticated() = false` et redirige vers `/login`. Puis `restoreSession` est rappelé → boucle infinie login ↔ change-password.
**Fix suggéré :** Dans le `catch` de `restoreSession`, distinguer le 403 `password-change-required` (setter `currentUser` avec les données partielles disponibles) du 401 (setter null). Ou désactiver l'interceptor sur `/api/auth/me`.

---

### ✅ C3 — UserService : IllegalArgumentException non capté → 500 non structuré
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/user/service/UserService.java:22`
**Problème (AR + ECH) :** `orElseThrow(() -> new IllegalArgumentException("User not found"))` n'est pas capté par le `GlobalExceptionHandler` (qui ne gère que `BusinessException` et les exceptions Spring MVC). Résultat : 500 sans Problem Detail.
**Fix suggéré :** Remplacer par `new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found")`.

---

### ✅ C4 — docker-compose.yml : mismatch DB_PASSWORD si variable non définie
**Fichier :** `.docker/docker-compose.yml`
**Problème (AR) :** Service `backend` : `DB_PASSWORD: ${DB_PASSWORD:-change_me}` (fallback "change_me"). Service `db` : `MARIADB_PASSWORD: ${DB_PASSWORD}` (pas de fallback → chaîne vide). Si `DB_PASSWORD` n'est pas défini sur l'hôte, le backend utilise "change_me" mais la BDD a un mot de passe vide → connexion impossible au démarrage.
**Fix suggéré :** Harmoniser les deux services avec le même fallback, ou supprimer le fallback sur le backend pour forcer un échec explicite si la variable est absente.

---

## Élevé — sécurité ou UX brisée

### ✅ E1 — authGuard frontend : forcePasswordChange non vérifié
**Fichier :** `pluribourse-frontend/src/app/core/guards/auth.guard.ts`
**Problème (AR + ECH) :** Le guard ne vérifie que `isAuthenticated()`. Un utilisateur avec `forcePasswordChange: true` peut naviguer directement vers `/volunteer` ou `/admin`, où toutes les requêtes API retournent 403 de `ForcePasswordChangeFilter` sans explication.
**Fix suggéré :**
```typescript
if (auth.currentUser()?.forcePasswordChange) return router.createUrlTree(['/change-password']);
```

---

### ✅ E2 — AuthService.logout : pas de finally → état client incohérent
**Fichier :** `pluribourse-frontend/src/app/services/auth.service.ts:31-35`
**Problème (AR + ECH) :** Si `POST /logout` échoue (réseau), `currentUser.set(null)` et la navigation ne se produisent pas. L'utilisateur reste en état "connecté" côté client.
**Fix suggéré :** Déplacer `currentUser.set(null)` et `router.navigate` dans un bloc `finally`.

---

### ✅ E3 — AuthController : injection directe de UserRepository dans le contrôleur (résolu avec C1)
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/user/controller/AuthController.java`
**Problème (AR) :** `UserRepository` est injecté directement dans le contrôleur pour re-charger l'utilisateur après le changement de mot de passe. Violation de l'architecture Controller → Service → Repository.
**Fix suggéré :** Ajouter une méthode `UserService.refreshUserDetails(Long userId)` qui retourne un `PluriBourseUserDetails` frais. Le contrôleur appelle cette méthode et n'a plus besoin de `UserRepository`.

---

### ✅ E4 — ChangePasswordDto : aucune règle de complexité
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/user/dto/ChangePasswordDto.java`
**Problème (AR) :** `@Size(min=8, max=128)` accepte "aaaaaaaa". Pour un compte admin par défaut accessible depuis le réseau, c'est une faiblesse réelle.
**Fix suggéré :** Ajouter une contrainte `@Pattern` (ex. au moins une majuscule, un chiffre) ou une annotation de validation personnalisée.

---

## Moyen — qualité ou cohérence

### ✅ M1 — PluriBourseUserDetails expose l'entité User mutable
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/shared/security/PluriBourseUserDetails.java`
**Problème (AR) :** `getUser()` retourne la référence directe vers l'entité JPA `User` (Lombok `@Setter`). N'importe quel composant peut modifier l'état du principal en session sans passer par la couche service.
**Fix suggéré :** Soit retirer `getUser()` et exposer uniquement les champs nécessaires via des méthodes déléguées. Soit créer un snapshot immutable de `User` à la construction du `UserDetails`.

---

### ✅ M2 — ForcePasswordChangeFilter : JSON hardcodé au lieu d'ObjectMapper
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/shared/security/ForcePasswordChangeFilter.java:29-34`
**Problème (AR) :** La réponse Problem Detail est une string concaténée à la main, incohérente avec `LoginSuccessHandler` qui utilise `ObjectMapper`.
**Fix suggéré :** Injecter `ObjectMapper` dans le filtre et sérialiser un objet `ProblemDetail` comme dans les autres handlers.

---

### ✅ M3 — adminGuard redirige un VOLUNTEER authentifié vers /login
**Fichier :** `pluribourse-frontend/src/app/core/guards/admin.guard.ts`
**Problème (ECH) :** Un VOLUNTEER qui tente d'accéder à `/admin` est redirigé vers `/login` même s'il est connecté.
**Fix suggéré :** Rediriger vers `/volunteer` si l'utilisateur est authentifié mais pas ADMIN.

---

### ✅ M4 — ChangePasswordComponent : pas de signal loading → double-submit
**Fichier :** `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts`
**Problème (AR + ECH) :** Le bouton est désactivé si `form.invalid` mais pas pendant l'appel async. Double-clic = deux requêtes `POST /api/auth/change-password`.
**Fix suggéré :** Ajouter `readonly loading = signal(false)` et `[disabled]="form.invalid || loading()"`.

---

### ✅ M5 — LoginComponent : rôle SELLER non géré → navigation vers /volunteer
**Fichier :** `pluribourse-frontend/src/app/features/auth/login/login.component.ts:53-57`
**Problème (AR + ECH) :** Après login, si `role !== 'ADMIN'`, on navigue vers `/volunteer`. Un SELLER atterrit là et cumule les 403.
**Fix suggéré :** Ajouter un cas explicite pour SELLER (afficher un message "accès non autorisé" ou rediriger vers une page dédiée).

---

### ✅ M6 — authInterceptor : 403 accès refusé (non password-change) sans récupération
**Fichier :** `pluribourse-frontend/src/app/core/interceptors/auth.interceptor.ts:16-18`
**Problème (ECH) :** Un 403 qui n'est pas `password-change-required` (ex. accès non autorisé) est simplement re-propagé sans navigation ni clear du state. L'utilisateur reste sur la page courante avec un état brisé.
**Fix suggéré :** Ajouter un `else` pour les autres 403 : naviguer vers une page d'erreur ou `/login`.

---

## Faible — technique / dette

### ✅ F1 — Zéro test frontend sur guards, interceptor, AuthService
**Fichier :** `pluribourse-frontend/src/`
**Problème (AR) :** Aucun test pour `authGuard`, `adminGuard`, `authInterceptor`, `AuthService`. L'objectif de 80% de couverture est vraisemblablement non atteint.
**Fix suggéré :** Créer des specs pour les 4 : scénarios connecté/déconnecté, rôles, et comportement de l'interceptor sur 401/403.

---

### ✅ F2 — Session 24h sans idle timeout
**Fichier :** `pluribourse-backend/src/main/resources/application.properties`
**Problème (AR) :** `spring.session.timeout=P1D` sans `max-inactive-interval`. Sur des postes partagés (bénévoles), une session reste valide 24h même après fermeture du navigateur.
**Fix suggéré :** Ajouter `server.servlet.session.timeout=2h` ou une valeur adaptée au contexte d'une journée de bourse.

---

### ✅ F3 — @Component + addFilterAfter : double-registration potentielle
**Fichier :** `pluribourse-backend/src/main/java/org/pluribourse/shared/security/ForcePasswordChangeFilter.java`
**Problème (AR) :** Un filtre `@Component` est auto-enregistré par Spring Boot ET ajouté manuellement via `addFilterAfter`. `OncePerRequestFilter` protège contre la double exécution via un attribut de requête, mais l'intent est ambigu.
**Fix suggéré :** Retirer `@Component` et déclarer le filtre uniquement via `@Bean` dans `SecurityConfig`, ou ajouter `FilterRegistrationBean` pour désactiver l'auto-registration.

---

### ✅ F4 — Insert admin non idempotent dans la migration
**Fichier :** `pluribourse-backend/src/main/resources/db/changelog/001-core-schema.xml`
**Problème (ECH) :** Pas de `<preConditions>` sur l'insert de l'admin par défaut. Si le changeSet est rejoué (reset Liquibase), la contrainte unique sur `username` lève une erreur → migration en échec → app ne démarre pas.
**Fix suggéré :** Ajouter `<preConditions onFail="MARK_RAN"><sqlCheck expectedResult="0">SELECT COUNT(*) FROM users WHERE username='Admin'</sqlCheck></preConditions>`.

---

## Récapitulatif

| Sévérité | Count | IDs |
|----------|-------|-----|
| Critique | 4 | C1, C2, C3, C4 |
| Élevé | 4 | E1, E2, E3, E4 |
| Moyen | 6 | M1, M2, M3, M4, M5, M6 |
| Faible | 4 | F1, F2, F3, F4 |
| **Total** | **18** | |
