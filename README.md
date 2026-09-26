# PluriBourse

PluriBourse est une application web complète conçue pour gérer des bourses aux jeux, skis, vêtements ou autres événements de vente d'articles d'occasion.

L'application est pensée pour être robuste, fiable et simple d'utilisation pour des bénévoles lors d'un événement potentiellement stressant. Elle est optimisée pour fonctionner sur du matériel peu coûteux (comme un Raspberry Pi) et être déployée facilement grâce à Docker.

L'accent est mis sur une expérience utilisateur fluide.

### Fonctionnalités Principales

*   **Gestion du Cycle de Vie des Éditions :** Le cœur de l'application est une machine à états qui guide une "édition" (un événement unique) à travers plusieurs phases : Dépôt, Vente, Post-vente et Clôturée.
*   **Gestion des Vendeurs et Articles :** Les bénévoles peuvent enregistrer les vendeurs et leurs articles, y compris la création de lots. Le système assigne automatiquement des tables aux articles en fonction de leur catégorie.
*   **Impression Automatisée d'Étiquettes :** Le système génère des codes-barres uniques pour chaque article et imprime automatiquement des étiquettes thermiques via un serveur centralisé, évitant aux bénévoles d'avoir à gérer des imprimantes.
*   **Point de Vente (PDV) :** Une interface de caisse optimisée permet aux bénévoles de scanner les articles avec un lecteur de code-barres USB. Le système gère les ventes concurrentes depuis plusieurs postes et s'assure que les lots sont vendus en une seule fois.
*   **Rapports :** L'administrateur peut générer divers rapports au format PDF, incluant des résumés de ventes journaliers, des bilans d'édition finaux, et la liste des vendeurs à payer.
*   **Internationalisation :** L'interface est disponible en Anglais et en Français.

### Stack Technique

*   **Backend :** Spring Boot avec Java 21 et une base de données MariaDB.
*   **Frontend :** Angular avec TypeScript, utilisant des fonctionnalités modernes comme les "standalone components" et les "signals".
*   **Déploiement :** L'application est packagée en conteneurs Docker et gérée avec Docker Compose pour une installation et des mises à jour simplifiées.

## Installation

Sur une machine Debian/Ubuntu neuve (poste de bureau de l'association), une seule commande installe
tout (Docker, PluriBourse, PrinterBridge) :

```bash
curl -fsSL https://raw.githubusercontent.com/Manerial/PluriBourse/main/install.sh | sudo bash
```

À lancer depuis le compte utilisateur habituel de l'admin (pas en root direct) : PrinterBridge tourne
comme service de ce compte, pas de root. Le script est idempotent — le relancer ne recommence pas ce
qui est déjà en place. Pour récupérer les dernières versions (PluriBourse et PrinterBridge) :

```bash
sudo /opt/pluribourse/install.sh --update
```

Ne touche jamais aux données existantes (mots de passe, base MariaDB), avec ou sans `--update`. Voir
`install.sh` pour le détail des étapes.

## Utilisation de BMAD

Ce projet a été généré et spécifié à l'aide de l'outil BMAD. Vous trouverez ci-dessous un résumé du projet basé sur les documents générés.

## PrinterBridge

Afin de faciliter l'utilisation des imprimantes à travers l'outil instancié dans Docker, un petit logiciel de gestion d'imprimantes a été implémenté.
Installé et configuré automatiquement par `install.sh` ci-dessus (dépôt séparé :
https://github.com/Manerial/PrinterBridge).

### Ajouter une imprimante thermique Bluetooth

PrinterBridge ne gère aujourd'hui les imprimantes thermiques **que par Bluetooth** — pas de connexion
filaire/USB pour l'instant (constaté sur le terrain : une imprimante branchée en USB s'énumère bien
comme périphérique, mais rien ne garantit qu'un port série utilisable soit créé sur la machine ; pas de
support de code pour ce cas de toute façon, cf. CLAUDE.md). Il faut donc une machine avec Bluetooth
(adaptateur intégré ou clé USB Bluetooth).

Sur Linux, contrairement à Windows, l'**appairage seul ne suffit pas** — il faut en plus un `rfcomm
bind` explicite pour qu'un port utilisable apparaisse :

```bash
bluetoothctl pair XX:XX:XX:XX:XX:XX
sudo rfcomm bind 0 XX:XX:XX:XX:XX:XX
ls /dev/rfcomm0
```

Sans cette étape, l'imprimante n'apparaîtra pas dans la liste de PrinterBridge (`GET /printers`),
silencieusement — pas d'erreur explicite pour le signaler.

## MKDocs

MKdocs est utilisé pour relire la documentation générée par BMAD.

### Installation

```bash
pip install mkdocs
```

### Usage

```bash
mkdocs serve
```