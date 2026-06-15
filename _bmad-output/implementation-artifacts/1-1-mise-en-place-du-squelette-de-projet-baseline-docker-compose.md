# Story 1.1: Mise en place du squelette de projet & baseline Docker Compose

Status: ready-for-dev

## Story

As a developer,
I want the full technology stack initialized with Docker Compose and a functional development environment,
so that feature development can begin on a stable and reproducible foundation.

## Acceptance Criteria

1. **Given** the repository is cloned, **When** `docker compose up -d` is executed, **Then** the Spring Boot application starts and responds on `/actuator/health`, the Angular dev server starts on `http://localhost:4200`, and the MariaDB container runs with a persistent volume.

2. **Given** the Spring Boot application starts, **When** Liquibase migrations execute, **Then**:
   - The `users` table exists with all fields including `preferred_language` and nullable `seller_profile_id`
   - Spring Session JDBC tables exist (changeset 002)
   - `categories` and `table_assignments` tables exist (changeset 003)
   - The `instance_config` table exists (changeset 004)
   - A default admin account (username: `Admin`, BCrypt hash of `Admin`, `force_password_change: true`) is initialized

3. **Given** the application returns an error, **When** an endpoint produces a 4xx or 5xx response, **Then** the body follows RFC 7807 Problem Details (`type`, `title`, `status`, `detail`, `instance`).

4. **Given** the Spring `dev` profile is active, **When** `/swagger-ui.html` is accessed, **Then** the Springdoc OpenAPI UI is available.

5. **Given** the Spring `prod` profile is active, **When** `/swagger-ui.html` is accessed, **Then** a 404 is returned.

## Tasks / Subtasks

- [ ] **T1 — Initialiser le backend Spring Boot** (AC: 1, 2, 3, 4, 5)
  - [ ] T1.1 — Générer via Spring Initializr : group `org.pluribourse`, artifact `pluribourse`, Java 21, Maven, Boot 4.0.6, dépendances : Spring Web, Spring Data JPA, Spring Security, Liquibase Migration, Lombok, MariaDB Driver, Validation
  - [ ] T1.2 — Ajouter au `pom.xml` : MapStruct (`mapstruct` + `mapstruct-processor` dans `annotationProcessorPaths`), `spring-session-jdbc`, `springdoc-openapi-starter-webmvc-ui` (vérifier la version compatible SB 4.x au moment de l'implémentation)
  - [ ] T1.3 — Configurer `application.properties` (datasource MariaDB, Liquibase, Spring Session JDBC, session sans expiration, Actuator, threads virtuels)
  - [ ] T1.4 — Créer `application-dev.properties` (Springdoc activé) et `application-prod.properties` (Springdoc désactivé)
  - [ ] T1.5 — Créer `GlobalExceptionHandler` (`@ControllerAdvice`) retournant `ProblemDetail` RFC 7807 pour `BusinessException`, `MethodArgumentNotValidException`, `ConstraintViolationException`
  - [ ] T1.6 — Créer `BusinessException` (runtime, porte un `HttpStatus`)
  - [ ] T1.7 — Créer `OpenApiConfig.java` (expose la doc uniquement si profil `dev`) et `JacksonConfig.java` (BigDecimal sans notation scientifique, dates ISO 8601)
  - [ ] T1.8 — Créer `SecurityConfig.java` minimal : autoriser `/actuator/health` sans auth, bloquer tout le reste — **ne pas** configurer `formLogin()` ni `httpBasic()` (Story 1.2)

- [ ] **T2 — Liquibase : 4 changesets** (AC: 2)
  - [ ] T2.1 — `db.changelog-master.xml` incluant les 4 changesets dans l'ordre
  - [ ] T2.2 — `001-core-schema.xml` : table `users` + insert compte admin par défaut (voir schéma complet en Dev Notes)
  - [ ] T2.3 — `002-spring-session.xml` : tables Spring Session JDBC pour MariaDB (utiliser le DDL officiel `schema-mysql.sql` du jar `spring-session-jdbc`, NE PAS écrire à la main)
  - [ ] T2.4 — `003-category-table-mapping.xml` : tables `categories` et `table_assignments` (sans FK vers `editions` — table inexistante à ce stade)
  - [ ] T2.5 — `004-instance-config.xml` : table `instance_config` avec `commission_rate` en `DECIMAL(5,2)`

- [ ] **T3 — Initialiser le frontend Angular** (AC: 1)
  - [ ] T3.1 — Générer : `ng new pluribourse-frontend --standalone --routing --style=scss` (**ne pas omettre `--standalone`**)
  - [ ] T3.2 — Installer : `ng add @angular/material` + `npm install @ngx-translate/core @ngx-translate/http-loader`
  - [ ] T3.3 — Configurer `app.config.ts` : `provideHttpClient()`, `provideAnimationsAsync()`, `TranslateModule.forRoot()` avec `HttpLoaderFactory` pointant vers `assets/i18n/`
  - [ ] T3.4 — Créer `assets/i18n/en.json` et `assets/i18n/fr.json` (objets JSON vides `{}` — stubs)
  - [ ] T3.5 — Créer les répertoires vides : `src/app/components/shared/`, `src/app/services/`, `src/app/models/`
  - [ ] T3.6 — Vérifier que `ng serve` démarre sur le port 4200 sans erreur

- [ ] **T4 — Infrastructure Docker Compose** (AC: 1)
  - [ ] T4.1 — `docker-compose.yml` : services `db` (MariaDB 11, volume persistant, healthcheck) et `backend` (build depuis Dockerfile, port 8080, `depends_on: db: condition: service_healthy`)
  - [ ] T4.2 — `docker-compose.dev.yml` : override dev (DevTools, profil Spring `dev`, pas de build Angular)
  - [ ] T4.3 — `.env.example` avec `DB_NAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `SPRING_PROFILES_ACTIVE`
  - [ ] T4.4 — `pluribourse-backend/Dockerfile` : `eclipse-temurin:21-jre`, copie JAR, `EXPOSE 8080`, `ENTRYPOINT`

- [ ] **T5 — Tests** (couverture cible ≥ 80%)
  - [ ] T5.1 — `PluriboursApplicationTests.java` : `@SpringBootTest` vérifiant que le contexte Spring charge (test de fumée H2)
  - [ ] T5.2 — Test d'intégration Liquibase (H2 in-memory) : vérifier que les 4 tables clés (`users`, `SPRING_SESSION`, `categories`, `instance_config`) existent après migration
  - [ ] T5.3 — Test RFC 7807 : via `MockMvc`, vérifier qu'une erreur retourne `Content-Type: application/problem+json` et les champs `type`, `title`, `status`, `detail`, `instance`

## Dev Notes

### Stack — NE PAS dévier de ces versions

| Technologie | Version | Raison |
|---|---|---|
| Java | **21** (LTS) | Threads virtuels (Project Loom) — critique pour RPi 4 |
| Spring Boot | **4.0.6** | Spring Framework 7 / Security 7 / Hibernate 7 — **ne pas rétrograder vers 3.x** |
| Angular | **21** (LTS) | Angular 22 sorti le 03/06/2026 — écarté : `jest-preset-angular` non stabilisé sur 22. LTS jusqu'à mai 2027 — **ne pas upgrader** |
| Build backend | Maven | `pom.xml` lisible ; JaCoCo + Failsafe documentés |

### Commande Spring Initializr (alternative CLI)

```bash
spring init --boot-version=4.0.6 --java-version=21 --build=maven \
  --group-id=org.pluribourse --artifact-id=pluribourse \
  --dependencies=web,data-jpa,security,liquibase,lombok,validation,mariadb
```

### Dépendances ajoutées manuellement après Spring Initializr

```xml
<!-- pom.xml — dans <dependencies> -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
<!-- Springdoc — ATTENTION à la compatibilité Spring Boot 4.x / Spring Framework 7 -->
<!-- springdoc-openapi 2.x cible Spring Boot 3.x. Pour SB 4.0.6 (Spring Framework 7), une version 3.x peut être requise. -->
<!-- Vérifier https://springdoc.org pour la version publiée compatible SB 4. -->
<!-- Fallback si Springdoc ne supporte pas encore SB 4 au moment de l'implémentation :
     implémenter un WebMvcConfigurer qui redirige /swagger-ui.html vers 404 en prod
     et revenir à Springdoc quand disponible. -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version><!-- vérifier la version compatible SB 4 --></version>
</dependency>

<!-- pom.xml — dans maven-compiler-plugin / annotationProcessorPaths -->
<path>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.6.3</version>
</path>
<path>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-mapstruct-binding</artifactId>
    <version>0.2.0</version>
</path>
```

> ⚠️ **Ordre du processor Lombok → MapStruct** : Lombok doit tourner **avant** MapStruct dans `annotationProcessorPaths`. L'ordre dans le POM détermine l'ordre d'exécution. Ajouter `lombok` en premier, puis `mapstruct-processor`.

> ⚠️ **JPageFlow** : déclare `spring-data-commons:3.5.5` comme dépendance transitive. Le BOM Spring Boot 4.0.6 écrase cela avec Spring Data 4.x. Pas de conflit attendu, mais **vérifier `mvn dependency:tree`** lors du premier build.

### application.properties — propriétés clés

```properties
# Datasource — URL utilise le nom du service Docker en prod, localhost en dev
spring.datasource.url=jdbc:mariadb://db:3306/${DB_NAME}
spring.datasource.username=pluribourse
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver

# JPA — Liquibase gère le DDL, Hibernate ne doit PAS modifier le schéma
spring.jpa.hibernate.ddl-auto=validate

# Liquibase
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml

# Spring Session JDBC (sessions survivent aux redémarrages conteneur — FR-066)
spring.session.store-type=jdbc
# FR-066 : sessions sans expiration pratique — P1D couvre largement un événement de 4-6h.
# ⚠️ spring.session.timeout prime sur server.servlet.session.timeout quand Spring Session JDBC est actif.
# -1 n'est pas une durée valide pour spring.session.timeout ; utiliser une durée ISO 8601.
spring.session.timeout=P1D
server.servlet.session.timeout=-1

# Threads virtuels Java 21 (réduit pression mémoire sur RPi 4)
spring.threads.virtual.enabled=true

# Actuator — santé uniquement, sans détails exposés
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

### application-dev.properties

> **Contextes d'exécution et URL datasource :**
> - `mvn spring-boot:run -Dspring-boot.run.profiles=dev` : Spring Boot tourne localement, MariaDB aussi en local (ou via `docker compose up db -d`). L'URL doit pointer vers `localhost`.
> - `docker compose up` : Spring Boot tourne dans un conteneur Docker, MariaDB dans le conteneur `db`. L'URL doit pointer vers `db` (nom du service Docker). `application.properties` (défaut, profil `prod`) utilise `db:3306`.
>
> Résumé : **profil `dev` = localhost ; profil `prod` (défaut Docker) = db:3306.**

```properties
# URL locale pour dev sans Docker — MariaDB doit tourner localement
spring.datasource.url=jdbc:mariadb://localhost:3306/${DB_NAME:pluribourse_dev}
# Springdoc activé uniquement en dev
springdoc.swagger-ui.enabled=true
springdoc.api-docs.enabled=true
# DevTools rechargement à chaud
spring.devtools.restart.enabled=true
```

### application-prod.properties

```properties
# Springdoc désactivé — /swagger-ui.html → 404 en prod
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```

### Test H2 — application.properties pour tests

Créer `src/test/resources/application.properties` (ou utiliser `@TestPropertySource`) :

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=none
spring.liquibase.enabled=true
```

`MODE=MySQL` permet à Liquibase d'exécuter le DDL MySQL/MariaDB sur H2. **Ne pas utiliser H2 sans `MODE=MySQL`** — les types `ENUM` ne sont pas supportés nativement.

> ⚠️ **ENUM sur H2** : Si Liquibase échoue avec une erreur sur les colonnes `ENUM` (changeset 001), remplacer `ENUM('ADMIN','VOLUNTEER','SELLER')` par `VARCHAR(20)` dans le changeset pour les tests. JPA continue de mapper correctement les enums Java → VARCHAR via `@Enumerated(EnumType.STRING)`. Le schéma MariaDB de production n'est pas affecté. Stratégie recommandée : utiliser `VARCHAR(20)` dans les changesets Liquibase dès le départ pour la portabilité H2/MariaDB.

### Liquibase — Schéma `001-core-schema.xml` (table users + admin)

```xml
<changeSet id="001-core-schema" author="pluribourse">
    <createTable tableName="users">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="username" type="VARCHAR(50)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="password" type="VARCHAR(255)">
            <constraints nullable="false"/>
        </column>
        <column name="role" type="ENUM('ADMIN','VOLUNTEER','SELLER')">
            <constraints nullable="false"/>
        </column>
        <column name="preferred_language" type="ENUM('EN','FR')" defaultValue="FR">
            <constraints nullable="false"/>
        </column>
        <!-- FK nullable : reliée à seller_profiles (Epic 3) — sans contrainte FK à ce stade -->
        <column name="seller_profile_id" type="BIGINT"/>
        <column name="force_password_change" type="BOOLEAN" defaultValueBoolean="false">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <!-- Compte admin par défaut — force_password_change=true impose le changement à la 1ère connexion (Story 1.2) -->
    <insert tableName="users">
        <column name="username" value="Admin"/>
        <!-- Hash BCrypt de "Admin" — à générer : new BCryptPasswordEncoder().encode("Admin") -->
        <!-- Exemple de hash valide (force 10) : $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyVDyP7fi -->
        <column name="password" value="$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBpwTTyVDyP7fi"/>
        <column name="role" value="ADMIN"/>
        <column name="preferred_language" value="FR"/>
        <column name="force_password_change" valueBoolean="true"/>
    </insert>
</changeSet>
```

> ⚠️ **Hash BCrypt** : le hash ci-dessus est un exemple. Pour générer un hash valide sans écrire de code supplémentaire, ajouter ce test jetable dans `PluriboursApplicationTests` :
> ```java
> @Test void printAdminHash() {
>     System.out.println(new BCryptPasswordEncoder().encode("Admin"));
> }
> ```
> Copier la sortie dans le changeset, puis supprimer le test. Le hash BCrypt est non-déterministe (salt aléatoire) — n'importe quel hash BCrypt de "Admin" avec force 10 est valide pour Spring Security.

### Liquibase — `002-spring-session.xml`

Utiliser le DDL officiel Spring Session pour MariaDB/MySQL. Le fichier source est dans le jar `spring-session-jdbc` à l'emplacement `org/springframework/session/jdbc/schema-mysql.sql`.

**Ne PAS écrire ce DDL manuellement.** Deux approches valides :

**Option A — Laisser Spring Session auto-créer les tables (recommandé pour cette story) :**

Dans `application.properties`, ajouter :
```properties
spring.session.jdbc.initialize-schema=never
```
Et dans `application-dev.properties` pour les tests locaux :
```properties
spring.session.jdbc.initialize-schema=embedded
```
Laisser Liquibase créer les tables en prod via changeset 002, et Spring Session les créer automatiquement en test (`embedded` = H2).

**Option B — Importer le DDL officiel depuis le jar :**
```xml
<changeSet id="002-spring-session" author="pluribourse">
    <sqlFile path="classpath:org/springframework/session/jdbc/schema-mysql.sql"
             stripComments="true"/>
</changeSet>
```

> ⚠️ **Vérifier le chemin exact** dans la version de `spring-session-jdbc` incluse avec Spring Boot 4.0.6. Le chemin a historiquement varié (`schema-mysql.sql` vs `schema.sql`). Inspecter le contenu du jar : `jar tf ~/.m2/.../spring-session-jdbc-*.jar | grep schema`.

### Liquibase — `003-category-table-mapping.xml`

```xml
<changeSet id="003-category-table-mapping" author="pluribourse">
    <createTable tableName="categories">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="name" type="VARCHAR(100)">
            <constraints nullable="false" unique="true"/>
        </column>
        <column name="display_order" type="INT" defaultValueNumeric="0">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <createTable tableName="table_assignments">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="category_id" type="BIGINT">
            <constraints nullable="false" foreignKeyName="fk_table_assignments_category"
                         references="categories(id)"/>
        </column>
        <!-- edition_id sans FK pour l'instant — la table editions est créée en Epic 2 -->
        <column name="edition_id" type="BIGINT"/>
        <column name="table_number" type="INT">
            <constraints nullable="false"/>
        </column>
    </createTable>
</changeSet>
```

### Liquibase — `004-instance-config.xml`

```xml
<changeSet id="004-instance-config" author="pluribourse">
    <createTable tableName="instance_config">
        <column name="id" type="BIGINT" autoIncrement="true">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="association_name" type="VARCHAR(255)"/>
        <column name="event_name" type="VARCHAR(255)"/>
        <!-- commission_rate en DECIMAL(5,2) — correspond à BigDecimal en Java (NFR-003 / CLAUDE.md) -->
        <column name="commission_rate" type="DECIMAL(5,2)"/>
    </createTable>
</changeSet>
```

### GlobalExceptionHandler — RFC 7807 avec Spring Framework 7

Spring Framework 7 supporte `ProblemDetail` nativement (RFC 7807). Étendre `ResponseEntityExceptionHandler` pour hériter des handlers par défaut :

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setType(URI.create("https://pluribourse/errors/" + ex.getErrorCode()));
        pd.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(ex.getStatus()).body(pd);
    }
}
```

`BusinessException` :
```java
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    // constructeur, getters
}
```

### SecurityConfig.java — configuration minimale (Story 1.1 uniquement)

La configuration complète (formLogin, Spring Session JDBC, rôles, CSRF) est implémentée en **Story 1.2**. Pour cette story, démarrer Spring Security sans bloquer l'Actuator :

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
        );
        // Pas de formLogin() ici — Story 1.2
        // Pas de httpBasic() ici — non utilisé dans ce projet
        return http.build();
    }
}
```

> ⚠️ **CSRF** : Spring Security active CSRF par défaut. En attendant Story 1.2, désactiver si les tests de fumée échouent : `.csrf(csrf -> csrf.disable())`. Story 1.2 configure CSRF correctement.

### OpenApiConfig.java

```java
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {
    @Bean
    public OpenAPI pluriboursOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("PluriBourse API")
                .version("1.0"));
    }
}
```

### JacksonConfig.java — sérialisation BigDecimal et dates

```java
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToEnable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                // BigDecimal sans notation scientifique (ex: 12.50, pas 1.25E+1)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .simpleDateFormat("yyyy-MM-dd");
    }
}
```

### Angular — app.config.ts avec TranslateModule

```typescript
import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { HttpClient } from '@angular/common/http';
import { routes } from './app.routes';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    provideAnimationsAsync(),
    importProvidersFrom(
      TranslateModule.forRoot({
        loader: {
          provide: TranslateLoader,
          useFactory: HttpLoaderFactory,
          deps: [HttpClient],
        },
        defaultLanguage: 'fr',
      })
    ),
  ],
};
```

> **Note i18n** : `defaultLanguage: 'fr'` — le français est la langue par défaut. Les clés i18n dans `en.json` / `fr.json` sont des stubs `{}` dans cette story. Le système i18n complet (sélection de langue, persistance) est en Story 1.6.

### Proxy Angular CLI (CORS en développement local)

En dev sans Docker, Angular (`:4200`) appelle Spring Boot (`:8080`) — cross-origin, bloqué par le navigateur sans proxy.

Créer `pluribourse-frontend/proxy.conf.json` :
```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/actuator": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

Dans `angular.json`, sous `serve > options` :
```json
"proxyConfig": "proxy.conf.json"
```

Démarrer Angular en dev : `ng serve --proxy-config proxy.conf.json` (ou via `angular.json`).

> **Note** : Ce proxy n'est utilisé **qu'en dev local** (`ng serve`). Dans Docker, le frontend est servi par un Nginx (Story 1.9) qui proxifie vers le backend. Ne pas configurer CORS côté Spring Boot en v1 — le déploiement Docker est mono-serveur.

### Docker Compose — docker-compose.yml

```yaml
services:
  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: ${DB_NAME}
      MARIADB_USER: pluribourse
      MARIADB_PASSWORD: ${DB_PASSWORD}
      MARIADB_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
    volumes:
      - db_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "healthcheck.sh", "--connect", "--innodb_initialized"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  backend:
    build:
      context: ./pluribourse-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      DB_NAME: ${DB_NAME}
      DB_PASSWORD: ${DB_PASSWORD}
    depends_on:
      db:
        condition: service_healthy

volumes:
  db_data:
```

### Dockerfile backend

```dockerfile
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY target/pluribourse-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> **Note** : construire l'image via `mvn clean package -DskipTests` puis `docker build`. Le JAR fat est produit par `spring-boot-maven-plugin` (inclus par défaut dans le parent BOM Spring Boot).

### Structure du monorepo à créer

```
PluriBourse/
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── CLAUDE.md                           ← déjà présent
├── pluribourse-backend/                ← généré par Spring Initializr
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd
│   ├── Dockerfile                      ← à créer manuellement
│   └── src/
│       ├── main/java/org/pluribourse/
│       │   ├── PluriboursApplication.java
│       │   └── shared/
│       │       ├── exception/  GlobalExceptionHandler.java, BusinessException.java
│       │       ├── security/   SecurityConfig.java
│       │       └── config/     OpenApiConfig.java, JacksonConfig.java
│       └── main/resources/
│           ├── application.properties
│           ├── application-dev.properties
│           ├── application-prod.properties
│           ├── messages_en.properties   ← stub vide
│           ├── messages_fr.properties   ← stub vide
│           └── db/changelog/
│               ├── db.changelog-master.xml
│               ├── 001-core-schema.xml
│               ├── 002-spring-session.xml
│               ├── 003-category-table-mapping.xml
│               └── 004-instance-config.xml
└── pluribourse-frontend/               ← généré par ng new
    ├── package.json
    ├── angular.json
    ├── tsconfig.json
    └── src/
        ├── main.ts
        ├── index.html
        ├── styles.scss
        └── app/
            ├── app.component.ts
            ├── app.config.ts
            ├── app.routes.ts
            ├── components/shared/      ← répertoire vide
            ├── services/               ← répertoire vide
            ├── models/                 ← répertoire vide
            └── assets/i18n/
                ├── en.json             ← stub {}
                └── fr.json             ← stub {}
```

### Contraintes CLAUDE.md — invariants à respecter dès cette story

- **BigDecimal** : `instance_config.commission_rate` est `DECIMAL(5,2)` en BDD → `BigDecimal` en Java (entité + DTO). Jamais `float` ou `double`.
- **Pas de DCP dans les logs** : configurer Logback pour ne jamais logger de données personnelles. Dans cette story, aucune DCP n'est encore présente, mais poser la règle dans `logback-spring.xml` dès maintenant.
- **i18n** : les fichiers `en.json` / `fr.json` sont des stubs vides **mais doivent exister** — `TranslateModule` lève une erreur si les fichiers sont absents.
- **Sécurité** : le rôle `SELLER` doit être déclaré dans l'enum `Role` dès maintenant (même s'il est bloqué à 403 — aucun point d'entrée SELLER en v1).

### Project Structure Notes

**Packages backend créés dans cette story :**
- `org.pluribourse.shared.exception` : `GlobalExceptionHandler.java`, `BusinessException.java`
- `org.pluribourse.shared.security` : `SecurityConfig.java` (minimal, complété en Story 1.2)
- `org.pluribourse.shared.config` : `OpenApiConfig.java`, `JacksonConfig.java`
- `org.pluribourse.PluriboursApplication.java` (point d'entrée)

**Packages non créés dans cette story** (créés par leurs stories respectives) :
- `edition/`, `seller/`, `item/`, `pos/`, `payout/`, `report/`, `user/`, `print/`, `shared/sse/`, `shared/instanceconfig/`

**Frontend — créé dans cette story :**
- `app.config.ts`, `app.component.ts`, `app.routes.ts` (stubs)
- `assets/i18n/en.json` et `fr.json` (stubs `{}`)
- Répertoires vides : `components/shared/`, `services/`, `models/`

**Non créé dans cette story** (stories suivantes) :
- Composants Angular, services métier, modèles TypeScript

### References

- [Source: architecture.md#Outils de Génération de Squelette] — commandes `spring init` et `ng new`
- [Source: architecture.md#Versions Sélectionnées] — Java 21, Spring Boot 4.0.6, Angular 21
- [Source: architecture.md#Initialisation Backend] — dépendances Spring Initializr + manuelles
- [Source: architecture.md#Initialisation Frontend] — `ng new` + dépendances manuelles
- [Source: architecture.md#Authentification & Sécurité] — Spring Session JDBC, BCrypt, `server.servlet.session.timeout=-1`, rôles ADMIN/VOLUNTEER/SELLER
- [Source: architecture.md#Backend — Structure de Répertoires Complète] — noms exacts fichiers et packages
- [Source: architecture.md#Patrons de Nommage] — snake_case BDD, PascalCase Java, camelCase JSON
- [Source: architecture.md#API & Communication] — RFC 7807 Problem Details, Springdoc profil dev
- [Source: architecture.md#Organisation du Référentiel] — structure monorepo PluriBourse/
- [Source: architecture.md#Directives d'Application] — BigDecimal, pas de DCP dans logs, structure packages
- [Source: architecture.md#Frontières d'Intégration] — Spring Session JDBC nécessite Liquibase avant auth
- [Source: epics.md#Story 1.1] — critères d'acceptation BDD
- [Source: CLAUDE.md] — BigDecimal, i18n via ngx-translate, pas de DCP dans logs, Liquibase

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
