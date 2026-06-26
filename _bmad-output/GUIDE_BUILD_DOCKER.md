# Guide de build et publication des images Docker — PluriBourse

Ce guide s'adresse aux développeurs de PluriBourse. Il couvre la construction des images Docker multi-plateforme (AMD64 + ARM64 pour Raspberry Pi), leur publication sur GitHub Container Registry (GHCR), la configuration de `docker-compose.yml` pour en bénéficier, et l'automatisation via GitHub Actions.

---

## Table des matières

1. [Architecture des images](#architecture-des-images)
2. [Prérequis](#prérequis)
3. [Adapter le Dockerfile backend pour un build autonome](#adapter-le-dockerfile-backend-pour-un-build-autonome)
4. [Build multi-plateforme en local](#build-multi-plateforme-en-local)
5. [Publication sur GHCR](#publication-sur-ghcr)
6. [Mettre à jour docker-compose.yml](#mettre-à-jour-docker-composeyml)
7. [CI/CD avec GitHub Actions](#cicd-avec-github-actions)
8. [Stratégie de versioning et de tags](#stratégie-de-versioning-et-de-tags)

---

## Architecture des images

PluriBourse produit deux images Docker :

| Image | Dockerfile | Base runtime | Rôle |
|---|---|---|---|
| `pluribourse-backend` | `pluribourse-backend/Dockerfile` | `eclipse-temurin:21-jre-alpine` | API Spring Boot |
| `pluribourse-frontend` | `pluribourse-frontend/Dockerfile` | `nginx:alpine` | Angular servi par nginx |

La base de données (`mariadb:11`) est une image officielle publiée sur DockerHub — elle n'est pas à builder.

Les deux images de base (`eclipse-temurin` et `nginx:alpine`) sont disponibles nativement en AMD64 et ARM64 : le build multi-plateforme ne requiert pas d'émulation coûteuse pour la couche runtime.

---

## Prérequis

- **Docker Desktop** (≥ 4.0) ou **Docker Engine** avec le plugin `buildx` (inclus depuis Docker 23)
- Un compte **GitHub** avec accès en écriture au dépôt PluriBourse (pour GHCR et GitHub Actions)
- **Git** configuré localement

Vérifiez que `buildx` est disponible :

```bash
docker buildx version
```

---

## Adapter le Dockerfile backend pour un build autonome

Le `Dockerfile` actuel du backend attend un JAR précompilé (`COPY target/*.jar app.jar`). Cette approche ne fonctionne pas pour un build multi-plateforme en CI puisque BuildKit ne peut pas accéder à un `target/` compilé hors de son contexte.

La solution est de passer à un **build multi-stage** qui compile le JAR à l'intérieur de Docker.

Remplacez le contenu de `pluribourse-backend/Dockerfile` par :

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> **Pourquoi `dependency:go-offline` avant de copier les sources ?** Docker met en cache chaque couche. En téléchargeant les dépendances Maven dans une couche séparée du code source, un simple changement de code ne retélécharge pas toutes les dépendances — le build CI est significativement plus rapide.

Le Dockerfile frontend est déjà multi-stage et ne nécessite pas de modification.

---

## Build multi-plateforme en local

### Créer un builder dédié

Le builder par défaut de Docker ne supporte qu'une seule plateforme. Créez un builder `buildx` capable de cibler plusieurs architectures :

```bash
docker buildx create --name pluribourse-builder --driver docker-container --use
docker buildx inspect --bootstrap
```

Vérifiez que `linux/amd64` et `linux/arm64` apparaissent dans la liste des plateformes supportées.

### Builder les images

Depuis la racine du dépôt :

```bash
# Backend
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag ghcr.io/<org>/pluribourse-backend:latest \
  pluribourse-backend/

# Frontend
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag ghcr.io/<org>/pluribourse-frontend:latest \
  pluribourse-frontend/
```

Remplacez `<org>` par le nom de votre organisation ou compte GitHub (en minuscules).

> Sans `--push`, les images multi-plateforme restent dans le cache BuildKit et ne sont pas exportées vers le daemon Docker local (contrairement aux images mono-plateforme). Pour les tester localement, utilisez `--load` avec une seule plateforme : `--platform linux/amd64 --load`.

---

## Publication sur GHCR

### S'authentifier

```bash
echo $GITHUB_TOKEN | docker login ghcr.io -u <votre-username-github> --password-stdin
```

Générez un token GitHub avec les permissions `write:packages` dans *Settings → Developer settings → Personal access tokens*.

### Builder et publier en une commande

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag ghcr.io/<org>/pluribourse-backend:latest \
  --push \
  pluribourse-backend/

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag ghcr.io/<org>/pluribourse-frontend:latest \
  --push \
  pluribourse-frontend/
```

L'option `--push` envoie directement les images vers GHCR. Les images apparaissent dans l'onglet **Packages** de votre dépôt GitHub.

### Rendre les packages publics

Par défaut, les packages GHCR sont privés. Pour que les utilisateurs finaux puissent faire `docker compose pull` sans authentification, rendez les packages publics dans *GitHub → Packages → [package] → Package settings → Change visibility → Public*.

---

## Mettre à jour docker-compose.yml

Avec les images publiées sur GHCR, ajoutez une clé `image:` à chaque service dans `.docker/docker-compose.yml`. En conservant la clé `build:`, le fichier reste utilisable pour le développement local (`docker compose build`) tout en permettant `docker compose pull` en production.

```yaml
  backend:
    image: ghcr.io/<org>/pluribourse-backend:latest
    build:
      context: ../pluribourse-backend
      dockerfile: Dockerfile
    # ... reste inchangé

  frontend:
    image: ghcr.io/<org>/pluribourse-frontend:latest
    build:
      context: ../pluribourse-frontend
      dockerfile: Dockerfile
    # ... reste inchangé
```

Comportement selon la commande utilisée :

| Commande | Comportement |
|---|---|
| `docker compose pull` | Télécharge les images depuis GHCR (production) |
| `docker compose build` | Compile depuis les sources locales (développement) |
| `docker compose up -d` | Utilise l'image GHCR si disponible localement, sinon build |
| `docker compose up -d --build` | Force le build depuis les sources |

Une fois cette modification en place, les utilisateurs finaux peuvent mettre à jour PluriBourse avec :

```bash
docker compose pull && docker compose up -d
```

> **Note :** Le `GUIDE_INSTALLATION.md` devra être mis à jour pour remplacer la commande de mise à jour `git pull && docker compose ... up -d --build` par `docker compose pull && docker compose up -d` une fois les images publiées sur GHCR.

---

## CI/CD avec GitHub Actions

Le workflow suivant se déclenche à chaque push d'un tag `v*.*.*`, build les deux images en multi-plateforme et les publie sur GHCR avec les tags appropriés.

Créez le fichier `.github/workflows/docker-publish.yml` :

```yaml
name: Build and publish Docker images

on:
  push:
    tags:
      - 'v*.*.*'

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract metadata — backend
        id: meta-backend
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/pluribourse-backend
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=raw,value=latest

      - name: Build and push — backend
        uses: docker/build-push-action@v6
        with:
          context: ./pluribourse-backend
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta-backend.outputs.tags }}
          labels: ${{ steps.meta-backend.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Extract metadata — frontend
        id: meta-frontend
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/pluribourse-frontend
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
            type=raw,value=latest

      - name: Build and push — frontend
        uses: docker/build-push-action@v6
        with:
          context: ./pluribourse-frontend
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta-frontend.outputs.tags }}
          labels: ${{ steps.meta-frontend.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Points clés du workflow

**`secrets.GITHUB_TOKEN`** est automatiquement fourni par GitHub Actions — aucun secret à configurer manuellement pour GHCR.

**`cache-from: type=gha` / `cache-to: type=gha,mode=max`** met en cache les couches BuildKit dans le cache GitHub Actions. Le `dependency:go-offline` du Dockerfile backend est particulièrement bénéfique ici : les dépendances Maven sont mises en cache entre les builds et ne sont retéléchargées que si `pom.xml` change.

**`docker/metadata-action`** génère automatiquement les tags à partir du tag Git :
- `v1.2.3` → images taguées `1.2.3`, `1.2`, et `latest`

---

## Stratégie de versioning et de tags

PluriBourse suit le **versioning sémantique** : `vMAJEUR.MINEUR.PATCH`

| Type de changement | Exemple | Tag Git |
|---|---|---|
| Correctif (bug fix) | `v1.0.1` | `git tag v1.0.1 && git push origin v1.0.1` |
| Nouvelle fonctionnalité | `v1.1.0` | `git tag v1.1.0 && git push origin v1.1.0` |
| Breaking change | `v2.0.0` | `git tag v2.0.0 && git push origin v2.0.0` |

Le tag `latest` pointe toujours vers la dernière version publiée. Les utilisateurs finaux qui gardent `latest` dans leur `docker-compose.yml` bénéficient des mises à jour automatiquement via `docker compose pull`.

### Publier une release

```bash
git tag v1.0.0
git push origin v1.0.0
```

Le workflow GitHub Actions se déclenche automatiquement, build les images et les publie. La release apparaît dans l'onglet **Packages** du dépôt GitHub.
