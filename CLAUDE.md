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
- **TOUJOURS** vérifier que les anciens devs ont été commité avant de commencer le travail sur une nouvelle story.

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
  - **Exception : le client d'un système externe peut avoir son propre test de service isolé** (ex. `PrinterBridgeClient`, Story 3.11/3.12) — un client HTTP/WebSocket sortant est une frontière avec l'extérieur du même ordre que les composants externes déjà exemptés de la règle no-Mockito ci-dessus, pas une couche interne de l'application. Ce test isolé s'ajoute à la couverture E2E (qui reste obligatoire pour le comportement métier autour de ce client), il ne la remplace pas.
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

## Installation (`install.sh`)

Script unique à la racine, pensé pour un admin non technique : `curl -fsSL .../install.sh | sudo bash`
sur une machine Debian/Ubuntu neuve installe Docker, clone PluriBourse dans `/opt/pluribourse`, lance
`docker compose`, puis installe/configure PrinterBridge (dépôt séparé). Idempotent ; `--update` récupère
les dernières versions sans jamais toucher au `.env` (mots de passe) ni aux volumes Docker (données
MariaDB) — voir les commentaires du script pour le détail de chaque étape.

Décisions et bugs trouvés en le testant de bout en bout (24 septembre 2026, sur une VM Ubuntu neuve) :
- **Dépôt Docker Debian vs Ubuntu** — le dépôt APT officiel de Docker est différent pour Debian et
  Ubuntu (noms de code différents : `bookworm`/`trixie` côté Debian, `jammy`/`noble`/... côté Ubuntu).
  Pointer sur le mauvais donne `does not have a Release file`, quelle que soit la distro réellement
  utilisée. Le script détecte `$ID` dans `/etc/os-release` et choisit le bon dépôt.
- **Nettoyage défensif du dépôt Docker** — si une exécution précédente a laissé un `docker.list` cassé
  (mauvais dépôt, coupure réseau en plein milieu), **tout** `apt-get update` suivant échoue à cause de
  lui, y compris pour des paquets sans rapport (`curl`/`git`) — bien après que le script lui-même ait
  été corrigé. Le script le supprime au démarrage si Docker n'est pas déjà fonctionnel.
- **`systemctl --user` exige une vraie session active** pour l'utilisateur admin (cf. CLAUDE.md de
  PrinterBridge, incident "Failed to connect to bus") — le script vérifie `/run/user/<uid>` et échoue
  clairement si absent, plutôt que de laisser une erreur D-Bus cryptique plus loin. Doit être lancé
  avec `sudo` depuis le compte de l'admin (pas en root direct, pas depuis un SSH brut sans session).
- **Un seul script, pas deux** — un script séparé pour l'installation de Docker existait avant
  (`.docker/install-docker-debian.sh`) et était appelé par `install.sh` ; supprimé et fusionné dans
  `install.sh` (choix explicite : éviter la duplication/l'ordre de dépendance entre deux fichiers pour
  un gain nul, `install.sh` étant de toute façon le seul point d'entrée réel).
- **Adresse réseau de PrinterBridge** — PrinterBridge ne connaît rien à Docker (choix côté
  PrinterBridge, voir son CLAUDE.md) ; c'est `install.sh` qui détecte la passerelle Docker et l'écrit
  dans une surcharge systemd (`PRINTERBRIDGE_EXTRA_BIND_ADDRESSES`).
  **Correctif (24 septembre 2026)** : la première version inspectait `pluribourse_default` (le réseau
  propre au projet Compose, nom fixé par `name: pluribourse`) — logique en apparence, mais faux en
  pratique. Constaté par un `wget` réel depuis le conteneur `backend` vers `host.docker.internal` :
  cette adresse résout **toujours** vers la passerelle du bridge Docker **par défaut** (`docker network
  inspect bridge`), quel que soit le réseau auquel le conteneur est réellement connecté — un
  comportement global à la machine, pas propre à chaque réseau Compose. `extra_hosts:
  host.docker.internal:host-gateway` (déjà dans `docker-compose.yml`) ne fait donc pas ce qu'on
  supposait initialement ; corrigé en inspectant `bridge` au lieu de `pluribourse_default`.
- **Validé de bout en bout (24 septembre 2026, Ubuntu neuve via WSL2)** : `install.sh` exécuté du
  début à la fin (Docker, clone, `.env`, `docker compose up`, détection de la passerelle, installation
  de PrinterBridge, surcharge systemd, démarrage), puis confirmé depuis l'intérieur du conteneur
  `backend` : `wget http://host.docker.internal:7420/printers` répond `200 OK`. Résout définitivement
  le rapport terrain initial sur `e6320` ("le service PrinterBridge ne répond pas").
- **`install.sh` corrigé sur `e6320` lui-même (25-26 septembre 2026)** : bit d'exécution manquant sur
  `install.sh` dans le repo (créé depuis un environnement Windows, cf. `.gitattributes` — commit
  `1c32892`) faisait échouer `./install.sh` avec "Permission non accordée" ; contournable sans attendre
  le correctif via `sudo bash install.sh`.
- **Imprimante thermique sur `e6320` : pas de Bluetooth sur cette machine, tentative filaire/USB
  infructueuse.** L'imprimante s'énumère bien en USB (`lsusb` : "Winbond Electronics Corp. Virtual Com
  Port", donc un port série virtuel — même famille que le RFCOMM Bluetooth), mais **aucun nœud
  `/dev/ttyACM*`/`/dev/ttyUSB*` n'a été créé** par le noyau (pilote `cdc_acm` non lié à ce périphérique
  précis — cause exacte non investiguée, diagnostic `dmesg` interrompu). De toute façon, même si le
  port était apparu, **PrinterBridge ne le verrait pas** : son filtre Linux n'accepte que les ports
  nommés `rfcommN`, un `ttyACM`/`ttyUSB` est explicitement exclu (choix voulu côté PrinterBridge, pour
  ne pas faire remonter n'importe quel port série — Arduino, modem — comme fausse imprimante ; aucun
  support de code pour une imprimante thermique filaire à ce stade). **Décision** : achat d'une clé
  USB Bluetooth plutôt que de creuser le filaire — le chemin Bluetooth est celui réellement supporté et
  déjà validé. Voir la section "Ajouter une imprimante thermique Bluetooth" du `README.md` (le `rfcomm
  bind` explicite requis sur Linux, contrairement à Windows). **Reste à faire** : recevoir/appairer la
  clé Bluetooth, puis tester une vraie impression via PrinterBridge — pas encore fait à ce stade.