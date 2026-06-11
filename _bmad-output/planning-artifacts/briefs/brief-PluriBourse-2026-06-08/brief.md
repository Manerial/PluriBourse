---
title: "Brief Produit : PluriBourse"
status: final
created: 2026-06-08
updated: 2026-06-10
---

# Brief Produit : PluriBourse

## Résumé Exécutif

PluriBourse est une plateforme de gestion d'événements auto-hébergée, accessible via navigateur, destinée aux associations organisant des ventes d'occasion — bourse aux jouets, livres, skis, vêtements, ou tout autre type de produits. Elle couvre l'ensemble du cycle de vie de l'événement : inscription des vendeurs, catalogage des articles avec génération d'étiquettes à code-barres, caisse multi-poste avec douchette à code-barres, et calcul automatisé des soldes vendeurs.

La plateforme remplace des outils fragiles par un système propre et maintenable, conçu pour fonctionner sur plusieurs postes via un serveur central. Chaque association héberge sa propre instance, gère ses propres événements sous des éditions librement nommées, et configure son propre taux de commission. Un guide d'installation détaillé pour utilisateurs non techniques rend le déploiement accessible sans support informatique dédié.

Construit avec Spring Boot et Angular, déployé via Docker Compose, PluriBourse est conçu pour s'adapter à toute association organisant n'importe quel type de vente d'occasion, plusieurs fois par an si nécessaire.

## Vision

PluriBourse démarre comme un outil construit pour les besoins spécifiques d'une association. Son modèle auto-hébergé, sa commission configurable, le nommage libre des éditions et sa conception agnostique au type de produit sont des fondations intentionnelles pour une portée plus large : toute association organisant n'importe quel type de vente d'occasion peut le télécharger, l'installer et le faire fonctionner de façon autonome.

Le guide d'installation est aussi important que le produit lui-même — c'est lui qui transforme le logiciel en un outil que le trésorier d'une association peut déployer sans assistance un samedi après-midi.

## Le Problème

Les associations qui organisent des bourses disposent parfois d'outils fonctionnels — support multi-poste, traçabilité des actions des bénévoles, gestion automatique des soldes. Le problème ne porte pas sur ce que le logiciel fait : il porte sur la maintenabilité.

- Des chemins parfois codés en dur impliquant que tout changement d'infrastructure casse le logiciel.
- Une documentation absente ou insuffisante faisant de l'auteur original le seul capable de diagnostiquer facilement les pannes.
- Des architectures et technologies datées, déployées directement sur le serveur et sans versionning, rendant l'investigation fastidieuse.

Chaque édition se déroule avec le risque qu'une mise à jour de routine ou une nouvelle machine brise silencieusement l'outil, sans possibilité de le corriger sous la pression du jour J.

Le coût caché de ces solutions, c'est leur fragilité. Un outil que personne ne peut maintenir est une charge, pas un atout.

## La Solution

PluriBourse répond aux trois fragilités identifiées par des choix techniques délibérés.

**Une architecture reproductible, sans dépendance à la machine.** Le déploiement repose sur Docker Compose : l'environnement d'exécution est décrit dans un fichier, pas dans la tête de quelqu'un. Changer de serveur ou reconstruire après une panne ne nécessite aucune connaissance préalable de la configuration. Il n'y a pas de chemin codé en dur, pas de paramètre implicite hérité d'une installation précédente.

**Une stack normée, lisible par n'importe quel développeur.** Spring Boot et Angular sont des standards largement documentés. La structure en couches (contrôleur → service → dépôt), les migrations de base de données versionnées avec Liquibase, et l'absence d'abstractions maison font que tout développeur familier de ces technologies peut ouvrir le code, comprendre ce qu'il fait, et diagnostiquer un problème sans l'auteur original.

**Un guide d'installation conçu comme un livrable à part entière.** Si un trésorier non technique peut installer la plateforme seul un samedi après-midi, il peut aussi suivre un runbook de diagnostic le jour d'une panne. La documentation n'est pas une annexe : c'est ce qui transforme un logiciel fonctionnel en un outil qu'une association peut posséder et maintenir de façon autonome.

## Aperçu

PluriBourse structure chaque événement en cinq phases contrôlées par l'administrateur :

**1. Phase de préparation** — L'administrateur configure l'édition : taux de commission, langue des documents, catégories d'articles et mapping catégorie-table. Les catégories et le mapping d'une édition précédente peuvent être copiés en un clic.

**2. Phase de dépôt** — Les bénévoles inscrivent les vendeurs (création de nouveaux profils ou récupération d'existants par nom et email) et cataloguent chaque article avec un nom, un prix, une catégorie et un indicateur complet/incomplet. La table est assignée automatiquement selon la catégorie. L'application génère des codes-barres Code 128, imprime des étiquettes sur rouleau thermique adhésif 57mm et produit un bordereau de dépôt par vendeur. Les lots (ensembles indivisibles à prix global) sont supportés.

**3. Phase de vente** — Les caissiers sur plusieurs postes simultanés utilisent des scanners USB à code-barres pour enregistrer les ventes. Les factures acheteur sont imprimables via un point d'impression centralisé — aucune imprimante requise à chaque poste.

**4. Phase post-vente** — Les bilans de vente par vendeur sont imprimés. Lorsqu'un vendeur revient récupérer son argent et ses invendus, un bénévole clique « Solder ». Si le vendeur ne se présente pas, le bouton « Non réclamé » transfère le montant dû aux recettes de l'association. Les rapports signalent les vendeurs non soldés avec leur numéro de téléphone et adresse mail.

**5. Clôture** — L'administrateur clôture l'édition : les bilans PDF sont générés, l'édition passe en lecture seule. Une action optionnelle « Nettoyer l'Édition » supprime définitivement les enregistrements articles.

Les transitions de phase nécessitent une confirmation explicite. Un retour en arrière phase par phase est possible, les données étant toujours préservées. Les profils vendeurs persistent d'une édition à l'autre ; chaque édition est cloisonnée indépendamment.

## À Qui Cela S'adresse

**Associations (organisateur) :** Toute association organisant des ventes d'occasion. La plateforme étant auto-hébergée, chaque association possède ses données et son instance en propre — pas d'abonnement, pas de dépendance externe, pas d'infrastructure partagée.

**Administrateur :** Accès complet à la plateforme. Contrôle les transitions de phase, configure le taux de commission et la langue des documents par édition, gère les comptes bénévoles, crée et nomme les éditions, et génère tous les rapports.

**Bénévoles :** Comptes individuels pour la traçabilité de base. Opèrent aussi bien l'inscription au dépôt, la caisse, et le solde vendeur selon les phases. Interface conçue pour la rapidité et la simplicité — utilisateurs non techniques travaillant sous la pression du jour J.

**Vendeurs :** N'accèdent pas à l'application. Ils interagissent avec les bénévoles et via des documents papier — un bordereau de dépôt à l'arrivée, un bilan de vente et leurs invendus à la récupération.

## Périmètre

**Inclus en v1 :**

*Internationalisation*

- Interface en anglais et français : langue configurée par compte utilisateur
- Documents imprimés en anglais ou français : langue configurée par édition

*Gestion des événements*

- Nommage libre des éditions ; plusieurs éditions par an supportées
- Cycle de vie des phases contrôlé par l'admin : Préparation → Dépôt → Vente → Post-vente → Clôturée
- Retour en arrière phase par phase disponible ; données toujours préservées
- Dialogue de confirmation requis pour toute transition de phase
- Taux de commission configurable par édition (initialisé depuis un paramètre instance, défaut 20 %)
- Action optionnelle post-clôture « Nettoyer l'Édition » : suppression définitive des articles

*Gestion des vendeurs et articles*

- Profils vendeurs persistants inter-éditions (nom, prénom, email, téléphone)
- Inscription des articles : nom, prix, catégorie, indicateur complet/incomplet
- Table assignée automatiquement selon le mapping catégorie-table de l'édition
- Support des lots : ensembles indivisibles à prix global unique, une étiquette par article
- Génération de codes-barres Code 128 + impression d'étiquettes sur rouleau thermique adhésif 57mm
- Impression de bordereau de dépôt par vendeur (articles, prix, solde net attendu)
- Catalogue filtrable et triable de tous les articles de l'édition (admin et bénévoles)

*Point de vente*

- Interface caisse avec support scanner USB à code-barres (AZERTY/QWERTY transparent)
- Gestion des lots au scanner : validation bloquée tant que le lot est incomplet
- Impression de factures acheteur

*Post-vente*

- Bilan de vente imprimable par vendeur (articles vendus, invendus avec table, solde net)
- Bouton « Solder » : enregistre le solde remis en espèces / chèque / carte bleue
- Bouton « Non réclamé » : transfère le montant aux recettes de l'association
- Liste des vendeurs non soldés avec numéro de téléphone et adresse mail

*Rapports (admin uniquement)*

- Bilan journalier pendant la phase Vente (PDF)
- Bilan d'édition généré à la clôture (PDF)
- Rapport des vendeurs non soldés

*Infrastructure & accès*

- Rôles Admin et Bénévole strictement séparés
- Support multi-poste : minimum 3 postes simultanés sans conflit de données
- Déploiement Docker Compose (Spring Boot + MariaDB)
- Deux imprimantes USB connectées au serveur : thermique (étiquettes) + standard A4 (documents)
- Point d'impression centralisé — aucune imprimante requise sur les postes clients
- Guide d'installation détaillé pour utilisateurs non techniques (Linux, macOS, Windows)

**Explicitement hors v1 :**

- Traitement de paiement intégré
- Portail vendeur en libre-service ou notifications email/SMS
- Application mobile
- Hébergement SaaS multi-tenant (chaque association héberge sa propre instance)
- Migration de données depuis des outils existants (installation fraîche par déploiement)
- Rôle de consultation en lecture seule
- Mécanisme de sauvegarde/restauration des données

## Critères de Succès

- Minimum 3 postes fonctionnent simultanément sans conflits de données
- Les calculs de solde correspondent à la vérification manuelle au centime près
- Les étiquettes à code-barres se scannent de façon fiable avec des scanners USB standard
- Tous les documents (bordereaux de dépôt, factures, rapports) s'impriment correctement via le point central depuis n'importe quel OS (Linux, macOS, Windows)
- L'admin peut ouvrir et fermer les phases sans incident, avec un retour d'état clair
- Le serveur fonctionne de façon acceptable sur du matériel d'entrée de gamme : Raspberry Pi 4 (2 Go RAM) ou équivalent — toute machine 64 bits avec 2 Go RAM et stockage SSD/USB
- Le serveur s'installe et fonctionne sous Linux, macOS et Windows sans modification du code
- Un utilisateur non technique peut installer et configurer la plateforme en suivant le guide d'installation seul, sans assistance développeur

## Contexte Technique

- **Stack :** Spring Boot (backend) + Angular (frontend)
- **Architecture :** Auto-hébergée, serveur central, clients navigateur (pas d'installation locale sur les postes)
- **Déploiement :** Docker Compose (Spring Boot + MariaDB) ; une instance par association ; multi-plateforme (Linux, macOS, Windows) ; guide d'installation ciblant les utilisateurs non techniques
- **Spec minimale :** Raspberry Pi 4 (2 Go RAM) ou machine 64 bits équivalente ; stockage SSD/USB fortement recommandé (la carte microSD est peu fiable pour les écritures en base sous charge événementielle)
- **Postes :** Minimum 3 simultanés ; tout OS avec un navigateur moderne
- **Compatibilité scanner :** Les scanners USB HID émettent en entrée clavier ; le composant de scan caisse gère les incohérences de disposition clavier (AZERTY/QWERTY) de façon transparente via un mappage de codes touches indépendant de la disposition — aucune configuration de poste requise
- **Impression :** Point centralisé sur le serveur ; imprimante thermique USB (étiquettes, rouleau 57mm) + imprimante standard A4 USB (documents PDF)
- **Codes-barres :** Code 128, générés côté serveur, imprimés sur rouleau thermique adhésif 57mm
- **Échelle :** ~100 vendeurs, ~1 700 articles par édition ; plusieurs éditions par an supportées