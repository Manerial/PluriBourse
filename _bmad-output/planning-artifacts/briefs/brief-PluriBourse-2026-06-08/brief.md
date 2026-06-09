---
title: "Brief Produit : PluriBourse"
status: final
created: 2026-06-08
updated: 2026-06-08
---

# Brief Produit : PluriBourse

## Résumé Exécutif

PluriBourse est une plateforme de gestion d'événements auto-hébergée, accessible via navigateur, destinée aux associations organisant des ventes d'occasion — bourse aux jouets, livres, skis, vêtements, ou tout autre type de produits. Elle couvre l'ensemble du cycle de vie de l'événement : inscription des vendeurs, catalogage des articles avec génération d'étiquettes à code-barres, caisse multi-poste avec scanner, et calcul automatisé des reversements vendeurs.

La plateforme remplace des outils fragiles par un système propre et maintenable, conçu pour fonctionner sur plusieurs postes via un serveur central. Chaque association héberge sa propre instance, gère ses propres événements sous des éditions librement nommées, et configure son propre taux de commission. Un guide d'installation détaillé pour utilisateurs non techniques rend le déploiement accessible sans support informatique dédié.

Construit avec Spring Boot et Angular, PluriBourse est conçu pour s'adapter à toute association organisant n'importe quel type de vente d'occasion, plusieurs fois par an si nécessaire.

## Le Problème

Les associations qui organisent des bourses disposent parfois d'outils qui fonctionnent — support multi-poste, traçabilité des bénévoles, réconciliation automatique des reversements. Le problème ne porte pas sur ce que le logiciel fait : il porte sur la capacité de quiconque à le maintenir.

Des chemins codés en dur signifient que tout changement d'infrastructure casse le logiciel. Une documentation absente ou insuffisante fait de l'auteur original le seul capable de diagnostiquer les pannes. Chaque édition se déroule avec le risque qu'une mise à jour de routine ou une nouvelle machine brise silencieusement l'outil, sans possibilité de le corriger sous la pression du jour J.

Le coût n'est pas la perte de fonctionnalités — c'est la fragilité. Un outil que personne ne peut maintenir est une charge, pas un atout.

## La Solution

PluriBourse structure chaque événement en trois phases contrôlées par l'administrateur :

**1. Phase de dépôt** — Les bénévoles inscrivent les vendeurs (création de nouveaux profils ou récupération d'existants par nom et email) et cataloguent chaque article avec un prix, une catégorie et une affectation de table. L'application génère des codes-barres Code 128, imprime des étiquettes adhésives sur des planches standard et produit un bordereau de dépôt pour chaque vendeur listant ses articles, prix et reversement net attendu après commission.

**2. Phase de vente** — Les caissiers sur jusqu'à trois postes simultanés utilisent des scanners USB à code-barres pour enregistrer les ventes. Les articles sont automatiquement marqués comme vendus. Les factures acheteur sont imprimables via un point d'impression centralisé — aucune imprimante requise à chaque poste.

**3. Phase post-vente** — Des documents de reversement par vendeur sont générés. Lorsqu'un vendeur revient récupérer son argent et ses invendus, un bénévole le marque comme collecté. Les rapports signalent les vendeurs qui ne sont pas encore revenus.

Les profils vendeurs persistent d'une édition à l'autre, de sorte que les vendeurs récurrents sont retrouvés par nom ou email sans ressaisie. Chaque édition porte un nom libre (ex. « Bourse de printemps 2026 », « Vide-grenier novembre ») et est cloisonnée indépendamment — ventes, inventaire et rapports ne se mélangent jamais entre éditions.

## À Qui Cela S'adresse

**Administrateur (organisateur) :** Accès complet à la plateforme. Contrôle les transitions de phase, configure le taux de commission, gère les comptes bénévoles, crée et nomme les éditions, et génère tous les rapports.

**Bénévoles :** Comptes individuels pour la traçabilité de base. Opèrent aussi bien l'inscription au dépôt que la caisse selon les phases. Interface conçue pour la rapidité et la simplicité — utilisateurs non techniques travaillant sous la pression du jour J.

**Associations :** Toute association organisant des ventes d'occasion. La plateforme étant auto-hébergée, chaque association possède ses données et son instance en propre — pas d'abonnement, pas de dépendance externe, pas d'infrastructure partagée.

**Vendeurs :** N'accèdent pas à l'application. Ils interagissent uniquement via des documents papier — un bordereau de dépôt à l'arrivée, un document de reversement et leurs invendus à la récupération.

## Périmètre

**Inclus en v1 :**

*Gestion des événements*
- Nommage libre des éditions ; plusieurs éditions par an supportées
- Cycle de vie des phases contrôlé par l'admin : Dépôt → Vente → Post-vente (sans retour en arrière)
- Taux de commission configurable par instance (paramètres admin, 20 % par défaut)

*Gestion des vendeurs et articles*
- Gestion des profils vendeurs avec persistance inter-éditions (nom, prénom, email)
- Inscription des articles : prix, catégorie, affectation de table, par vendeur
- Génération de codes-barres Code 128 + impression d'étiquettes sur planches adhésives standard
- Impression de bordereau de dépôt par vendeur (articles, prix, reversement net attendu)

*Point de vente*
- Interface caisse avec support scanner USB à code-barres
- Impression de factures acheteur

*Post-vente*
- Suivi de collecte vendeur : marquage du vendeur ayant récupéré son reversement et ses invendus
- Document de reversement par vendeur
- Liste d'invendus à restituer par vendeur (descriptions des articles + emplacement de table)

*Rapports*
- Bilan journalier : comptages vendus/invendus, chiffre d'affaires total, commission association gagnée
- Rapport des collectes en attente (vendeurs non encore revenus)

*Infrastructure & accès*
- Comptes utilisateurs : rôles Admin et Bénévole
- Support multi-poste via serveur central (jusqu'à 3 simultanés)
- Point d'impression centralisé (tous les postes impriment via le serveur ; une imprimante partagée)
- Guide d'installation détaillé pour utilisateurs non techniques

**Explicitement hors v1 :**
- Traitement de paiement intégré
- Portail vendeur en libre-service ou notifications email/SMS
- Application mobile
- Hébergement SaaS multi-tenant (chaque association héberge sa propre instance)
- Migration de données depuis des outils existants (installation fraîche par déploiement)

## Critères de Succès

- Les trois postes fonctionnent simultanément sans conflits de données
- Les calculs de reversement correspondent à la vérification manuelle au centime près
- Les étiquettes à code-barres se scannent de façon fiable avec des scanners USB standard sur des planches standard
- Tous les documents (bordereaux de dépôt, factures, rapports) s'impriment correctement via le point central depuis n'importe quel OS (Linux, macOS, Windows)
- L'admin peut ouvrir et fermer les phases sans incident, avec un retour d'état clair
- Le serveur fonctionne de façon acceptable sur du matériel d'entrée de gamme : Raspberry Pi 4 (2 Go RAM) ou équivalent — toute machine 64 bits avec 2 Go RAM et stockage SSD/USB
- Le serveur s'installe et fonctionne sous Linux, macOS et Windows sans modification du code
- Un utilisateur non technique peut installer et configurer la plateforme en suivant le guide d'installation seul, sans assistance développeur

## Contexte Technique

- **Stack :** Spring Boot (backend) + Angular (frontend)
- **Architecture :** Auto-hébergée, serveur central, clients navigateur (pas d'installation locale sur les postes)
- **Déploiement :** Une instance par association ; multi-plateforme (Linux, macOS, Windows) ; guide d'installation ciblant les utilisateurs non techniques
- **Spec minimale :** Raspberry Pi 4 (2 Go RAM) ou machine 64 bits équivalente ; stockage SSD/USB fortement recommandé (la carte microSD est peu fiable pour les écritures en base sous charge événementielle)
- **Postes :** Jusqu'à 3 simultanés ; tout OS avec un navigateur moderne
- **Compatibilité scanner :** Les scanners USB HID émettent en entrée clavier ; le composant de scan caisse gère les incohérences de disposition clavier (AZERTY/QWERTY) de façon transparente via un mappage de codes touches indépendant de la disposition — aucune configuration de poste requise
- **Impression :** Point centralisé sur le serveur ; une imprimante partagée
- **Codes-barres :** Code 128, générés côté serveur, imprimables sur planches adhésives standard
- **Échelle :** ~100 vendeurs, ~1 700 articles par édition ; plusieurs éditions par an supportées

## Vision

PluriBourse démarre comme un outil construit pour les besoins spécifiques d'une association. Son modèle auto-hébergé, sa commission configurable, le nommage libre des éditions et sa conception agnostique au type de produit sont des fondations intentionnelles pour une portée plus large : toute association organisant n'importe quel type de vente d'occasion peut le télécharger, l'installer et le faire fonctionner de façon autonome.

Le guide d'installation est autant une fonctionnalité du produit que le logiciel lui-même — c'est ce qui transforme un code fonctionnel en quelque chose que le trésorier d'une association peut vraiment déployer un samedi après-midi.
