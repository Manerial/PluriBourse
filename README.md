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

## Utilisation de BMAD

Ce projet a été généré et spécifié à l'aide de l'outil BMAD. Vous trouverez ci-dessous un résumé du projet basé sur les documents générés.

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