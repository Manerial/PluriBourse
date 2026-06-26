# Guide d'installation — PluriBourse

Ce guide vous accompagne pas à pas dans l'installation et la configuration de PluriBourse sur votre ordinateur ou sur un Raspberry Pi. Aucune connaissance préalable de Docker ou du terminal (une fenêtre noire dans laquelle vous tapez des commandes) n'est nécessaire.

---

## Table des matières

1. [Prérequis](#prérequis)
2. [Ouvrir et utiliser le terminal](#ouvrir-et-utiliser-le-terminal)
3. [Installation de Docker (Obligatoire)](#installation-de-docker-obligatoire)
4. [Téléchargement et lancement](#téléchargement-et-lancement)
5. [Premier lancement](#premier-lancement)
6. [Configuration initiale](#configuration-initiale)
7. [Réinitialisation du mot de passe admin](#réinitialisation-du-mot-de-passe-admin)
8. [Mise à jour](#mise-à-jour)
9. [Dépannage rapide](#dépannage-rapide)

---

## Prérequis

Avant de commencer, vérifiez que vous disposez de :

- Un ordinateur sous **Windows**, **macOS** ou **Linux** — ou un **Raspberry Pi 4** avec au moins 2 Go de RAM
- Au moins **5 Go d'espace disque libre** (pour les images Docker et la base de données)
- Une **connexion Internet** (pour télécharger Docker et les images de l'application lors du premier lancement)

> **Windows uniquement :** Docker Desktop nécessite que la **virtualisation matérielle** soit activée dans le BIOS/UEFI de votre ordinateur. Sur la plupart des PC récents, elle l'est déjà. L'installateur de Docker Desktop vous guidera si ce n'est pas le cas.

> Aucune connaissance technique n'est requise pour suivre ce guide.

---

## Ouvrir et Utiliser le terminal

Plusieurs étapes de ce guide nécessitent d'utiliser un **terminal** — une fenêtre dans laquelle vous tapez des commandes textuelles. Pas d'inquiétude : vous n'avez pas besoin de le comprendre en profondeur, il suffit de copier-coller les commandes indiquées.

### Ouvrir le terminal dans le bon dossier

Les commandes de ce guide doivent être exécutées depuis un dossier précis. La façon la plus simple est d'ouvrir le terminal directement depuis votre explorateur de fichiers, sans avoir à taper de chemin.

**Windows :** Dans l'Explorateur de fichiers, naviguez jusqu'au dossier voulu, puis cliquez dans la **barre d'adresse** (en haut), tapez `powershell` et appuyez sur **Entrée**. Un terminal s'ouvre directement dans ce dossier.

**macOS :** Dans le Finder, faites un clic droit sur le dossier voulu → **« Nouveau terminal au dossier »**.

> Si cette option n'apparaît pas, activez-la dans Réglages système → Clavier → Raccourcis clavier → Services → cochez « Nouveau terminal au dossier ».

**Linux / Raspberry Pi :** Dans votre gestionnaire de fichiers, faites un clic droit dans le dossier voulu → **« Ouvrir dans un terminal »** (le libellé exact dépend de votre environnement de bureau).

> **Astuce :** Le terminal ouvert ainsi est déjà positionné dans le bon dossier — toutes les commandes que vous tapez s'exécutent à partir de cet emplacement.


### Utiliser le terminal

Une fois le terminal ouvert, vous voyez une ligne qui se termine par un curseur clignotant. C'est là que vous tapez les commandes.

**Règles de base :**

- Tapez la commande exactement telle qu'elle est écrite dans ce guide (majuscules et tirets inclus), puis appuyez sur **Entrée** pour l'exécuter.
- Pour copier une commande depuis ce guide et la coller dans le terminal :
  - **Windows :** Ctrl+C pour copier, clic droit dans le terminal pour coller (ou Ctrl+V dans Windows Terminal)
  - **macOS :** Cmd+C pour copier, Cmd+V pour coller
  - **Linux :** Ctrl+C pour copier, Ctrl+Shift+V pour coller dans le terminal
- Certaines commandes prennent quelques secondes ou quelques minutes à s'exécuter. Attendez que le curseur réapparaisse avant de taper la suivante.
- Si une commande affiche un message d'erreur en rouge, lisez-le attentivement — il indique souvent la cause du problème.

---

## Installation de Docker (Obligatoire)

Docker est le logiciel qui permet à PluriBourse de fonctionner de manière autonome, encapsulé dans des conteneurs (des programmes isolés qui s'exécutent de façon autonome). Choisissez votre système d'exploitation ci-dessous.

### Windows

1. Rendez-vous sur [https://docs.docker.com/desktop/install/windows-install/](https://docs.docker.com/desktop/install/windows-install/) et téléchargez **Docker Desktop pour Windows (version x86_64)**.
2. Lancez le fichier d'installation téléchargé et suivez l'assistant (acceptez les paramètres par défaut, y compris l'installation de WSL2 si elle est proposée).
3. Une fois l'installation terminée, démarrez **Docker Desktop** depuis le menu Démarrer.
4. Attendez que l'icône Docker dans la barre des tâches (en bas à droite) affiche « Docker Desktop is running ».

**Vérification :** Ouvrez un terminal (voir : [Ouvrir et utiliser le terminal](#ouvrir-et-utiliser-le-terminal)) et tapez :

```
docker --version
```

Vous devriez voir s'afficher quelque chose comme `Docker version 27.x.x`. Si c'est le cas, Docker est correctement installé.

---

### macOS

1. Rendez-vous sur [https://docs.docker.com/desktop/install/mac-install/](https://docs.docker.com/desktop/install/mac-install/) et téléchargez **Docker Desktop pour Mac** (choisissez la version Intel ou Apple Silicon selon votre modèle).
2. Ouvrez le fichier `.dmg` téléchargé et faites glisser l'icône Docker dans le dossier **Applications**.
3. Lancez **Docker** depuis le dossier Applications.
4. Attendez que l'icône Docker dans la barre des menus (en haut à droite) indique que Docker est en cours d'exécution.

**Vérification :** Ouvrez le **Terminal** (voir : [Ouvrir et utiliser le terminal](#ouvrir-et-utiliser-le-terminal)) et tapez :

```
docker --version
```

Vous devriez voir la version de Docker s'afficher. Docker est prêt.

---

### Linux / Raspberry Pi

Pour un **Raspberry Pi** (Raspberry Pi OS), la page [https://docs.docker.com/engine/install/raspberry-pi-os/](https://docs.docker.com/engine/install/raspberry-pi-os/) détaille la procédure complète.

**Étape 1 —** Ouvrez un terminal (voir : [Ouvrir et utiliser le terminal](#ouvrir-et-utiliser-le-terminal)) et suivez les instructions officielles d'installation pour votre distribution sur [https://docs.docker.com/engine/install/](https://docs.docker.com/engine/install/).

**Étape 2 —** Une fois Docker installé, autorisez votre utilisateur à l'utiliser sans devoir taper `sudo` à chaque fois :

```bash
sudo usermod -aG docker $USER
```

**Déconnectez-vous puis reconnectez-vous** pour que cette modification prenne effet.

**Étape 3 —** Assurez-vous que Docker démarre automatiquement au démarrage :

```bash
sudo systemctl enable --now docker
```

**Vérification :**

```bash
docker --version
```

La version de Docker doit s'afficher. Si c'est le cas, Docker est prêt.

---

## Téléchargement et lancement

### 1. Récupérer les fichiers de configuration

Créez un dossier `PluriBourse` à l'emplacement de votre choix (par exemple sur le Bureau).

Deux fichiers sont nécessaires. Depuis la page GitHub du projet ([github.com/Manerial/PluriBourse](https://github.com/Manerial/PluriBourse)), naviguez dans le dossier `.docker` et téléchargez-les un par un dans votre dossier `PluriBourse` :

- `docker-compose.yml`
- `.env.example`

Pour télécharger un fichier depuis GitHub : cliquez sur le fichier, puis sur le bouton **Raw** en haut à droite, et enfin **Ctrl+S** (ou **Cmd+S** sur Mac) pour l'enregistrer.

### 2. Configurer l'environnement

Dans votre dossier `PluriBourse`, renommez le fichier `.env.example` en `.env` (clic droit → Renommer).

> **Windows :** une fenêtre peut vous avertir que changer l'extension risque de rendre le fichier inutilisable — confirmez en cliquant sur **Oui**.

Ouvrez ensuite le fichier `.env` dans un éditeur de texte (clic droit → Ouvrir avec → Bloc-notes sur Windows, TextEdit sur macOS) et modifiez les valeurs suivantes :

```
DB_PASSWORD=un_mot_de_passe_solide
MYSQL_ROOT_PASSWORD=un_autre_mot_de_passe_solide
```

Choisissez des mots de passe d'au moins 12 caractères, mélangeant lettres et chiffres. Notez-les dans un endroit sûr.

### 3. Télécharger les images de l'application

```bash
docker compose pull
```

Cette commande télécharge les composants de PluriBourse. Les images de l'application (`backend` et `frontend`) proviennent de GitHub Container Registry (`ghcr.io`), et l'image de la base de données (`mariadb`) provient de Docker Hub (`registry-1.docker.io`). Selon votre connexion Internet, cette étape peut prendre plusieurs minutes.

### 4. Lancer l'application

```bash
docker compose up -d
```

**Attendez environ 60 à 90 secondes** avant d'ouvrir votre navigateur (jusqu'à 3 minutes sur Raspberry Pi lors du premier lancement).

### 5. Vérifier que l'application est prête

Ouvrez votre navigateur et accédez à :

```
http://localhost
```

La page de connexion de PluriBourse doit apparaître. Si vous obtenez une erreur de connexion, attendez encore 30 secondes et rafraîchissez la page.

> **Sur un Raspberry Pi** accessible depuis d'autres appareils du réseau, remplacez `localhost` par l'adresse IP du Raspberry Pi : `http://192.168.x.x` (consultez votre routeur ou tapez `hostname -I` dans le terminal du Raspberry Pi pour connaître son adresse).

---

## Premier lancement

1. Ouvrez votre navigateur et accédez à `http://localhost`.
2. Connectez-vous avec les identifiants par défaut :
   - **Identifiant :** `Admin`
   - **Mot de passe :** `Admin`
3. L'application vous demande immédiatement de **changer votre mot de passe**. C'est une mesure de sécurité obligatoire — choisissez un mot de passe d'au moins 8 caractères comprenant une majuscule et un chiffre (ce mot de passe est distinct des mots de passe de base de données définis à l'étape précédente).
4. Après avoir changé votre mot de passe, vous accédez au tableau de bord administrateur.

> **Important :** Ne sautez pas l'étape de changement de mot de passe. Votre instance ne sera pas sécurisée tant que les identifiants par défaut (`Admin` / `Admin`) sont actifs.

---

## Configuration initiale

Une fois connecté en tant qu'administrateur :

1. Dans le menu latéral gauche, cliquez sur **Paramètres** (ou accédez directement à `http://localhost/admin/settings`).
2. Renseignez les informations de votre association :
   - **Nom de l'association** — s'affichera dans l'application
   - **Taux de commission par défaut (%)** — appliqué à chaque nouvelle édition
   - **Langue des documents par défaut** — langue utilisée pour les bordereaux et bilans imprimés
3. Cliquez sur **Enregistrer les paramètres**.

Ces paramètres peuvent être modifiés à tout moment. Votre instance est maintenant opérationnelle.

---

## Réinitialisation du mot de passe admin

Si vous avez oublié le mot de passe du compte administrateur, cette procédure vous permet d'en générer un nouveau temporaire.

> **Prérequis :** L'application doit être en cours d'exécution. Si ce n'est pas le cas, naviguez d'abord dans le dossier `.docker` et exécutez `docker compose up -d`, puis attendez que l'application soit prête avant de continuer.

### Ouvrir un terminal

- **Windows 11 :** Clic droit sur le bouton Démarrer → « Terminal »
- **Windows 10 :** Clic droit sur le bouton Démarrer → « Windows PowerShell »
- **macOS :** Applications → Utilitaires → Terminal
- **Linux / Raspberry Pi :** Ctrl+Alt+T ou cherchez « Terminal » dans les applications

### Naviguer dans le dossier Docker

```bash
cd chemin/vers/PluriBourse/.docker
```

Remplacez `chemin/vers/PluriBourse` par le chemin complet vers le dossier du projet.

### Lancer la réinitialisation

```bash
docker compose run --rm --no-deps backend --reset-admin-password --login=Admin
```

> Si votre identifiant administrateur est différent de `Admin`, remplacez `Admin` par votre identifiant.

Vous verrez s'afficher un résultat similaire à :

```
=== PluriBourse Admin Password Reset ===
Temporary password: xK3mP9qAzR2n
Log in and change your password immediately.
========================================
```

Notez le mot de passe temporaire affiché, puis connectez-vous à `http://localhost` avec cet identifiant et ce mot de passe temporaire. L'application vous demandera immédiatement d'en choisir un nouveau.

> **Si plusieurs comptes admin existent**, la commande vous demandera de saisir les identifiants d'un admin existant avant de procéder à la réinitialisation. Cette saisie s'affiche en clair dans le terminal — tapez votre mot de passe et appuyez sur Entrée.

---

## Mise à jour

Pour mettre à jour PluriBourse vers la dernière version, naviguez dans le dossier `.docker` (voir la section [Ouvrir et utiliser le terminal](#ouvrir-et-utiliser-le-terminal)) et exécutez :

```bash
docker compose pull && docker compose up -d
```

Cette commande récupère les nouvelles images depuis GitHub puis redémarre les conteneurs avec ces nouvelles versions. Aucune compilation locale n'est nécessaire.

> Si les notes de version mentionnent un changement de configuration, re-téléchargez l'archive ZIP, copiez votre fichier `.docker/.env` dans le nouveau dossier `.docker`, puis relancez la commande ci-dessus.

**Vos données sont préservées.** Toutes les données de votre association (éditions, vendeurs, articles, bilans) sont stockées dans un volume Docker (un espace de stockage persistant qui garde vos données même si l'application est arrêtée) nommé `pluribourse_db_data`. Ce volume n'est jamais supprimé lors d'une mise à jour — vos données sont conservées.

Si la mise à jour inclut des modifications de la base de données, celles-ci sont appliquées automatiquement au démarrage.

---

## Dépannage rapide

Si l'application ne démarre pas, naviguez dans le dossier `.docker` et vérifiez l'état des services :

```bash
docker compose ps
```

Cette commande affiche l'état de chaque composant. Il doit indiquer `running` ou `healthy` pour les trois services : `db`, `backend`, `frontend`. Si l'un d'eux est en erreur, consultez ses journaux :

```bash
docker compose logs db
docker compose logs backend
docker compose logs frontend
```

**Le port 80 est déjà utilisé par un autre programme ?** Si la page `http://localhost` affiche une erreur ou le contenu d'un autre service (IIS sous Windows, Apache, etc.), le port 80 est probablement occupé. Arrêtez l'autre programme ou configurez PluriBourse pour utiliser un autre port en modifiant la ligne `ports` du service `frontend` dans `.docker/docker-compose.yml` (par exemple `"8080:80"` pour accéder à l'application via `http://localhost:8080`).

---

### Docker Desktop ne démarre pas (Windows)

**« Cannot connect to the Docker daemon »** — Cette erreur signifie que Docker Desktop n'est pas en cours d'exécution. Ouvrez Docker Desktop depuis le menu Démarrer et attendez que l'icône dans la barre des tâches affiche « Docker Desktop is running » avant de retaper votre commande.

**Docker Desktop reste bloqué au démarrage ou affiche une erreur WSL2** — Ouvrez PowerShell et tapez :

```
wsl --update
```

Puis redémarrez Docker Desktop. Si le problème persiste, redémarrez l'ordinateur.

**« Hardware assisted virtualization and data execution protection must be enabled »** — La virtualisation matérielle n'est pas activée sur votre ordinateur. Redémarrez le PC, entrez dans le BIOS/UEFI (généralement en appuyant sur F2, F10, Suppr ou Échap au démarrage — le message affiché au démarrage indique la touche exacte) et activez l'option « Virtualization Technology », « VT-x » ou « AMD-V » selon votre processeur.

**Docker Desktop demande une mise à jour avant de démarrer** — Acceptez la mise à jour et attendez qu'elle se termine. Docker Desktop redémarre automatiquement ensuite.

---

### Erreur lors du téléchargement des images (`docker compose pull`)

**« TLS handshake timeout »** ou **« net/http: TLS handshake timeout »** — Docker n'arrive pas à établir une connexion sécurisée avec le registre d'images (Docker Hub). Essayez les solutions suivantes dans l'ordre :

1. **Vérifiez l'accès au registre Docker** — Ouvrez `https://registry-1.docker.io/v2/` dans votre navigateur. Vous devez voir s'afficher `{}` (deux accolades). Si la page ne s'affiche pas ou que la connexion expire, le domaine `registry-1.docker.io` (le registre Docker Hub, distinct du site `hub.docker.com`) est inaccessible depuis votre réseau.

2. **Redémarrez Docker Desktop** — Clic droit sur l'icône Docker dans la barre des tâches → « Restart Docker Desktop », puis relancez `docker compose pull`.

3. **Configurez les serveurs DNS de Docker** — Ouvrez Docker Desktop, allez dans *Settings → Docker Engine* et ajoutez les serveurs DNS de Google dans la configuration JSON :

   ```json
   {
     "dns": ["8.8.8.8", "8.8.4.4"]
   }
   ```

   Cliquez sur **Apply & restart**, puis relancez `docker compose pull`.

4. **Désactivez temporairement votre antivirus ou pare-feu** — Certains logiciels de sécurité (Kaspersky, ESET, Bitdefender, etc.) interceptent les connexions TLS et peuvent bloquer Docker Hub. Désactivez-les le temps du téléchargement, puis réactivez-les.

5. **Configurez un proxy réseau** — Si vous êtes dans un environnement d'entreprise ou d'école, votre réseau passe peut-être par un proxy. Configurez-le dans Docker Desktop : *Settings → Resources → Proxies*, en renseignant l'adresse de votre proxy. Contactez votre administrateur réseau si vous ne connaissez pas cette adresse.

6. **Testez depuis un autre réseau** — Essayez en partageant la connexion 4G/5G de votre téléphone via le point d'accès mobile. Si le téléchargement réussit depuis ce réseau, le blocage vient de votre réseau habituel (box internet, réseau d'entreprise).
