---
title: "PRD : PluriBourse v1"
status: final
created: 2026-06-08
updated: 2026-06-09
---

# PRD : PluriBourse v1

## Énoncé du Problème

Les associations organisant des ventes d'occasion (jouets, livres, skis, vêtements, etc.) se trouvent dans l'une de ces deux situations : elles gèrent tout manuellement sur papier ou tableur, ou elles s'appuient sur un logiciel existant que personne ne peut maintenir.

Dans le premier cas, la gestion manuelle ne passe pas à l'échelle : l'inscription des vendeurs, l'étiquetage des articles, la caisse multi-poste et le calcul des reversements deviennent ingérables au-delà d'un certain volume.

Dans le second cas, le logiciel fonctionne — jusqu'à ce qu'il tombe en panne. Des chemins codés en dur échouent au moindre changement d'infrastructure. L'absence de documentation fait de l'auteur original le seul capable de diagnostiquer les pannes. Chaque édition comporte le risque qu'une mise à jour de routine ou une nouvelle machine brise silencieusement l'outil, sans possibilité de le corriger sous la pression du jour J.

Dans les deux cas, le coût est le même : un événement fragile, des bénévoles sous pression, et une association qui ne peut pas se concentrer sur l'essentiel.

---

## Vision & Objectifs

**Vision**

PluriBourse est la plateforme de référence pour les associations organisant des ventes d'occasion — accessible à toute personne capable de télécharger un fichier et de suivre un guide. Auto-hébergée, agnostique au type de produit, sans abonnement, sans dépendances externes : chaque association possède son instance et ses données en propre.

Le guide d'installation est un produit à part entière — c'est ce qui transforme un code fonctionnel en quelque chose que le trésorier d'une association peut déployer un samedi après-midi.

**Objectifs**

| ID | Objectif | Indicateur d'Orientation |
|---|---|---|
| G1 | Couvrir le cycle de vie complet de l'événement | Les quatre phases (Préparation → Dépôt → Vente → Post-vente) fonctionnent de bout en bout sans intervention technique |
| G2 | Fonctionner sur du matériel modeste | Tourne sur Raspberry Pi 4 (2 Go RAM) sans dégradation notable sous charge événementielle |
| G3 | Déployable par un utilisateur non technique | Une association peut installer et configurer la plateforme sans développeur, avec le guide seul |
| G4 | Supporter plusieurs associations indépendantes | Chaque instance est isolée ; le modèle est conçu pour la réplication |
| G5 | Maintenable par la communauté | Stack standard, bien documentée, sans dépendances exotiques |

---

## Utilisateurs & Rôles

| Rôle | Accès | Notes |
|---|---|---|
| **Administrateur** | Complet | Gestion des phases, commission, éditions, comptes bénévoles, rapports |
| **Bénévole** | Dépôt + Caisse + Reversement | Rôle unique ; l'interface s'adapte à la phase active. Utilisateurs non techniques opérant sous la pression du jour J |
| **Vendeur** | Hors application | Documents papier uniquement — bordereau de dépôt à l'arrivée, bilan de vente à la récupération |

> Un admin ne peut pas opérer en tant que bénévole depuis son compte admin. Pour assurer des fonctions de caisse ou de dépôt, l'admin crée un compte bénévole dédié.

---

## Périmètre

### Inclus — v1

**Internationalisation**
- Interface en anglais et français : langue configurée par compte utilisateur
- Documents imprimés en anglais ou français : langue configurée au niveau de l'instance

**Gestion des Événements**
- Nommage libre des éditions ; plusieurs éditions par an supportées
- Cycle de vie des phases contrôlé par l'admin : Préparation → Dépôt → Vente → Post-vente → Clôturé
- Dialogue de confirmation requis pour toute transition de phase (avant ou arrière)
- Retour en arrière disponible phase par phase ; données toujours préservées
- Action optionnelle post-clôture « Nettoyer l'Édition » : supprime définitivement les enregistrements articles et désactive le retour en arrière
- Taux de commission configurable par instance (défaut 20 %)

**Gestion des Vendeurs & Articles**
- Profils vendeurs persistants inter-éditions (nom, prénom, email, téléphone)
- Inscription des articles : nom, prix, catégorie, indicateur complet/incomplet
- Table assignée automatiquement selon le mapping catégorie-table configuré par édition
- Support des lots : ensembles indivisibles à prix global unique, une étiquette par article
- Génération de codes-barres Code 128 et impression d'étiquettes sur rouleau thermique adhésif 57mm
- Impression de bordereau de dépôt par vendeur

**Point de Vente**
- Interface caisse avec support scanner USB HID (AZERTY/QWERTY transparent)
- Panier : plusieurs articles par transaction, une facture acheteur globale
- Impression de facture acheteur à la demande

**Post-Vente**
- Reversement vendeur : le bénévole saisit le montant remis en espèces et clique « Reverser »
- Bouton « Non collecté » : le montant intégral dû est transféré aux recettes de l'association
- Bilan de vente par vendeur : articles vendus, invendus avec emplacement de table, reversement net

**Rapports**
- Bilan journalier (PDF, admin uniquement)
- Bilan d'édition (PDF, admin uniquement, généré à la clôture)
- Rapport des vendeurs en attente (vendeurs non reversés avec numéro de téléphone)

**Catalogue Articles**
- Catalogue filtrable et triable de tous les articles de l'édition active (accessible à l'admin et aux bénévoles)
- Ajout manuel au panier depuis le catalogue (solution de repli pour codes-barres illisibles)

**Infrastructure & Accès**
- Rôles Admin et Bénévole, strictement séparés
- Support multi-poste (minimum 3 simultanés)
- Déploiement Docker Compose (Spring Boot + MariaDB)
- Deux imprimantes USB connectées au serveur : thermique (étiquettes) + standard (documents)
- Point d'impression centralisé — aucune imprimante requise sur les postes clients
- Guide d'installation pour utilisateurs non techniques, avec instructions spécifiques par OS (Linux, macOS, Windows)

### Hors — v1
- Traitement de paiement intégré
- Portail vendeur en libre-service ou notifications email/SMS
- Application mobile
- Hébergement SaaS multi-tenant
- Migration de données depuis des outils existants
- Rôle de consultation en lecture seule
- Mécanisme de sauvegarde/restauration des données

---

## Fonctionnalités

### F1 — Internationalisation (EN/FR)

*Fondation transversale — à implémenter avant et en parallèle de toutes les autres fonctionnalités.*

| ID | Exigence |
|---|---|
| FR-001 | L'interface utilisateur est disponible en anglais et en français. |
| FR-002 | La langue par défaut de l'interface est détectée depuis le navigateur et stockée dans les préférences du compte à la première connexion de l'utilisateur. |
| FR-003 | Chaque utilisateur peut modifier sa préférence de langue dans les paramètres du compte. |
| FR-004 | Tout le texte de l'interface est externalisé — aucun texte d'interface n'est codé en dur dans le code source. |
| FR-005 | La langue de tous les documents imprimés (bordereaux de dépôt, factures acheteur, bilans de vente, rapports) est configurée au niveau de l'instance par l'admin. |
| FR-006 | Le paramètre de langue des documents s'applique à toute l'instance et à toutes les éditions. |
| FR-007 | Le paramètre de langue des documents est modifiable par l'admin à tout moment. |

---

### F2 — Gestion des Éditions & Cycle de Vie

| ID | Exigence |
|---|---|
| FR-008 | L'admin peut créer une édition avec un nom libre (ex. « Bourse de printemps 2026 », « Vide-grenier novembre »). |
| FR-009 | Plusieurs éditions peuvent être créées par an. |
| FR-010 | Une seule édition peut être active à la fois. Une édition est « active » tant qu'elle est en phase Préparation, Dépôt, Vente ou Post-vente. Une édition Clôturée n'est plus active. |
| FR-011 | Toute transition de phase — avant ou arrière — nécessite une confirmation explicite de l'admin via un dialogue. |
| FR-012 | La phase active de l'édition courante est affichée clairement à tous les utilisateurs connectés. |
| FR-013 | L'admin déclenche la clôture de l'édition via un bouton « Clôturer l'Édition » en phase Post-vente. Tous les documents sont générés en PDF dans les deux langues (EN et FR). L'édition passe en lecture seule. Les enregistrements articles restent en base jusqu'à ce que l'admin déclenche explicitement l'action Nettoyer. |
| FR-088 | Après clôture, l'admin peut déclencher une action **« Nettoyer l'Édition »** qui supprime définitivement les enregistrements articles de la base de données. Après nettoyage, le retour en arrière vers Post-vente est définitivement désactivé pour cette édition. Cette action nécessite une confirmation explicite. |
| FR-014 | Une édition archivée ne peut pas être supprimée. |
| FR-015 | Les données de chaque édition sont strictement cloisonnées — articles, ventes et rapports ne se mélangent jamais entre éditions. |
| FR-016 | Chaque édition possède son propre taux de commission, initialisé depuis le paramètre instance au moment de la création (défaut 20 %). L'admin peut le modifier en phase Préparation. Une fois la phase Dépôt démarrée, le taux est gelé pour cette édition et s'applique à tous ses articles. |
| FR-080 | Lors de la création d'une nouvelle édition, l'admin peut soit configurer les catégories et le mapping catégorie-table depuis zéro, soit copier la structure d'une édition existante. |
| FR-082 | L'admin peut revenir en arrière d'une phase à la fois : Clôturé → Post-vente, Post-vente → Vente, Vente → Dépôt, Dépôt → Préparation. Les données enregistrées dans la phase annulée sont préservées — rien n'est supprimé. Le retour depuis Clôturé n'est disponible qu'avant le déclenchement de l'action Nettoyer l'Édition (FR-088). |

---

### F3 — Gestion des Vendeurs & Articles (Phase de Dépôt)

#### Pré-configuration Admin

| ID | Exigence |
|---|---|
| FR-017 | L'admin configure la liste des catégories d'articles par édition. |
| FR-018 | L'admin configure le mapping catégorie-table par édition (ex. jeux de société → tables 1, 2, 3 ; livres → tables 4, 5). Les tables sont identifiées par numéro. |

#### Inscription des Vendeurs

| ID | Exigence |
|---|---|
| FR-019 | Les profils vendeurs persistent inter-éditions. Champs obligatoires : nom, prénom, email, numéro de téléphone. |
| FR-020 | Le bénévole recherche un vendeur existant par nom ou email. S'il n'est pas trouvé, un nouveau profil est créé. |
| FR-021 | L'admin peut supprimer un profil vendeur (droit à l'effacement RGPD). La suppression anonymise le nom, le prénom, l'email et le numéro de téléphone dans toutes les éditions. Les descriptions d'articles et les catégories de produits sont conservées (les articles sont supprimés en totalité à la clôture via FR-088). |

#### Inscription des Articles

| ID | Exigence |
|---|---|
| FR-022 | Pour chaque article, le bénévole saisit : nom/description, prix, catégorie, indicateur complet/incomplet, et un commentaire si incomplet. |
| FR-023 | La table est assignée automatiquement par le système selon le mapping catégorie-table de l'édition. |
| FR-024 | Un article ne peut être corrigé ou supprimé qu'en phase de Dépôt. |
| FR-025 | L'indicateur complet/incomplet et son commentaire sont modifiables dans toutes les phases. |

#### Lots

| ID | Exigence |
|---|---|
| FR-043 | Un bénévole peut créer un lot en lui assignant un nom et un prix global, puis en y ajoutant plusieurs articles. |
| FR-044 | Chaque article du lot possède son propre nom/description et reçoit sa propre étiquette. |
| FR-045 | L'étiquette d'un article de lot affiche, en plus des champs standard : « Prix du lot : X€ » en lieu et place d'un prix individuel, et « Lot indivisible : X/N » (X = position de l'article, N = nombre total d'articles dans le lot). |

#### Impression

| ID | Exigence |
|---|---|
| FR-026 | Un code-barres Code 128 unique est généré côté serveur pour chaque article inscrit. |
| FR-027 | L'étiquette article affiche centré : graphique du code-barres Code 128, numéro de code-barres lisible, nom de l'article (retour à la ligne si nécessaire), prix, catégorie, numéro de table, indicateur d'incomplétude si applicable. Le nom du vendeur n'apparaît pas (RGPD). |
| FR-028 | Le système déclenche l'impression des étiquettes automatiquement lorsqu'un bénévole valide le dépôt d'un vendeur. |
| FR-029 | Les travaux d'impression sont mis en file d'attente côté serveur et exécutés séquentiellement. |
| FR-030 | Le rouleau imprimé suit ce format par vendeur : [séparateur vendeur : nom vendeur + édition] → [étiquette article] → [séparateur article] → [étiquette article] → … |
| FR-031 | Un bordereau de dépôt est imprimable par vendeur : liste des articles, prix unitaires et reversement net attendu après commission. |
| FR-032 | La largeur du ticket thermique est configurable dans les paramètres admin (défaut : 57mm). |

---

### F4 — Point de Vente (Phase de Vente)

| ID | Exigence |
|---|---|
| FR-033 | L'interface caisse permet les ventes via scanner USB HID à code-barres. |
| FR-034 | Le composant de scan gère les différences de disposition clavier AZERTY/QWERTY de façon transparente via le mappage de codes touches — aucune configuration de poste requise. |
| FR-035 | Chaque article scanné est ajouté au panier de l'acheteur courant. Le système affiche le nom et le prix de l'article. |
| FR-036 | Scanner un article déjà vendu affiche un message d'erreur explicite. L'article n'est pas ajouté au panier. |
| FR-037 | Scanner un article incomplet affiche un avertissement informatif au caissier, incluant le détail de ce qui manque. L'article peut tout de même être vendu. |
| FR-038 | Le caissier peut retirer un ou plusieurs articles individuels du panier avant la validation du paiement. |
| FR-039 | La validation du paiement marque tous les articles du panier comme vendus et clôt la transaction. Aucune modification n'est possible après cette étape — pas de retour ni d'échange. |
| FR-040 | Après validation, une facture acheteur est imprimable à la demande via le point d'impression centralisé. |
| FR-041 | La facture affiche : liste des articles, prix unitaires, total, nom de l'association, nom de l'édition, date. Un lot apparaît sur une ligne unique (nom du lot, prix du lot). |
| FR-042 | L'application supporte un minimum de 3 postes caisse simultanés sans conflits de données. La limite effective dépend de la configuration du serveur. |

#### Lots en Caisse

| ID | Exigence |
|---|---|
| FR-046 | Scanner un article appartenant à un lot affiche le nom du lot en rouge avec un compteur « X/N scanné(s) ». |
| FR-047 | Le système bloque la validation du paiement tant que le lot n'est pas complet (tous les N articles scannés). |
| FR-048 | Une fois complet, le lot est vendu à son prix global de lot. |
| FR-081 | Si un caissier ne peut pas compléter un lot (article introuvable), il peut retirer le lot entier du panier. Tous les articles du lot déjà scannés sont retirés. |
| FR-090 | Si l'admin déclenche une transition de phase alors qu'un bénévole a un panier actif, le système annule le panier et affiche un message d'erreur explicite au bénévole. |

---

### F5 — Post-Vente & Reversements

| ID | Exigence |
|---|---|
| FR-049 | En phase Post-vente, un **bilan de vente** est imprimable par vendeur. |
| FR-050 | Le bilan de vente contient : articles vendus (nom, prix unitaire), invendus (nom, catégorie, numéro de table), total brut, commission déduite, montant net à reverser. Un lot apparaît sur une ligne unique (nom du lot, prix du lot). |
| FR-051 | Pour solder un vendeur, le bénévole saisit le montant en espèces remis et clique « Solder ». Le statut du vendeur passe à **Soldé**. |
| FR-052 | Si un vendeur ne souhaite pas récupérer son reversement, un bouton **« Non réclamé »** enregistre le montant intégral dû comme recette de l'association. |
| FR-053 | Les vendeurs non soldés sont identifiables dans l'application, avec leur numéro de téléphone visible pour les contacter. |

---

### F6 — Rapports

| ID | Exigence |
|---|---|
| FR-054 | Un **bilan journalier** est générable par l'admin à tout moment pendant la phase de Vente. Il couvre le jour calendaire courant. Il contient : nombre d'articles vendus/invendus pour la journée, chiffre d'affaires journalier, commission journalière gagnée par l'association. |
| FR-055 | Un **bilan d'édition** est généré à la clôture de l'édition. Il contient : total des articles vendus/invendus, chiffre d'affaires brut total, commission totale gagnée par l'association. |
| FR-056 | Un **rapport des vendeurs en attente** liste les vendeurs non reversés avec leur numéro de téléphone. |
| FR-057 | Tous les rapports sont générés en PDF. |
| FR-058 | Les rapports sont accessibles à l'admin uniquement. |
| FR-059 | Les éditions archivées affichent les métriques agrégées et les profils vendeurs en lecture seule. Le détail au niveau article n'est accessible que via les documents PDF générés à la clôture. |

---

### F7 — Comptes Utilisateurs & Contrôle d'Accès

| ID | Exigence |
|---|---|
| FR-060 | L'admin crée, modifie et désactive les comptes bénévoles. L'admin peut réinitialiser le mot de passe d'un bénévole. |
| FR-061 | Il y a un seul compte admin par instance. |
| FR-062 | Au premier lancement, le compte admin est initialisé avec les identifiants Admin/Admin. L'admin est forcé de changer son mot de passe à la première connexion. |
| FR-063 | Si l'admin perd son mot de passe, une commande exécutée sur le serveur génère un mot de passe temporaire. L'admin est forcé de le changer à la prochaine connexion. |
| FR-064 | Les rôles Admin et Bénévole sont strictement séparés. Un admin ne peut pas accéder aux interfaces bénévole depuis son compte admin. |
| FR-065 | L'interface bénévole s'adapte à la phase active : dépôt en phase Dépôt, caisse en phase Vente, reversement en phase Post-vente. En phase Post-vente, le bénévole peut imprimer le bilan de vente d'un vendeur pour regrouper ses invendus avant la remise. |
| FR-066 | Les sessions n'expirent pas automatiquement. |
| FR-067 | Chaque compte stocke une préférence de langue d'interface (EN/FR), détectée depuis le navigateur à la première connexion de l'utilisateur, modifiable dans les paramètres du compte. |

---

### F8 — Infrastructure & Déploiement

| ID | Exigence |
|---|---|
| FR-068 | Le serveur fonctionne sous Linux, macOS et Windows sans modification du code. |
| FR-069 | Spec minimale : Raspberry Pi 4 (2 Go RAM) ou machine 64 bits équivalente. Stockage SSD/USB fortement recommandé — la carte microSD est peu fiable pour les écritures en base sous charge événementielle. |
| FR-070 | L'application est déployée via Docker Compose (application Spring Boot + MariaDB) — un seul fichier `docker-compose.yml`. Les données sont stockées dans des volumes Docker persistants. |
| FR-071 | Les mises à jour s'appliquent avec deux commandes : `docker compose pull && docker compose up -d`. Les données persistantes sont préservées. |
| FR-072 | Les postes clients accèdent à l'application via navigateur — aucune installation locale requise sur les postes. |
| FR-073 | Une page de paramètres admin centralise la configuration de l'instance : nom de l'association, taux de commission, langue des documents, largeur du ticket thermique. |
| FR-074 | Le guide d'installation est exhaustif et cible les utilisateurs non techniques. Il couvre : installation de Docker, démarrage, configuration initiale, procédure de réinitialisation du mot de passe admin, et procédure de mise à jour. Les instructions sont fournies par OS (Linux, macOS, Windows) — commandes et procédures sont spécifiques à chaque plateforme. |

---

### F9 — Infrastructure d'Impression

| ID | Exigence |
|---|---|
| FR-075 | Toute impression est routée via le serveur central — aucune imprimante requise sur les postes clients. |
| FR-076 | **Imprimante thermique** (étiquettes articles) : connectée au serveur via USB. Largeur du ticket : voir FR-032. Voir FR-029 pour le comportement de la file d'impression. |
| FR-077 | **Imprimante standard** (documents A4) : connectée au serveur via USB. PDF généré côté serveur, envoyé directement à l'imprimante sans aperçu. |
| FR-078 | Un utilisateur déclenche l'impression depuis l'interface ; la requête est traitée par le serveur sans action requise sur le poste client. |
| FR-079 | En cas d'erreur d'impression (imprimante hors ligne, bourrage papier, manque de papier), l'utilisateur est notifié dans l'interface avec un message explicite. |

---

### F10 — Catalogue Articles

*Disponible pendant toutes les phases de l'édition active.*

| ID | Exigence |
|---|---|
| FR-083 | Un catalogue d'articles filtrable et triable est accessible à l'admin et aux bénévoles pendant toutes les phases de l'édition active. |
| FR-084 | Le catalogue peut être filtré par : nom/description, numéro de code-barres, catégorie, table, statut vendu/invendu, indicateur complet/incomplet, nom du vendeur. |
| FR-085 | Le catalogue peut être trié par n'importe quelle colonne visible. |
| FR-086 | Le catalogue affiche les articles de l'édition active uniquement. Les données au niveau article ne sont pas disponibles sur les éditions où l'action Nettoyer a été déclenchée. |
| FR-087 | En phase de Vente, un bénévole peut ajouter un article du catalogue directement au panier courant — solution de repli pour les codes-barres illisibles ou endommagés. Le système empêche l'ajout d'un article déjà vendu ou déjà présent dans le panier courant. |
| FR-089 | La commission s'applique normalement aux articles vendus avec l'indicateur incomplet. Le prix de vente et le taux de commission ne sont pas modifiés par l'état de complétude de l'article. |

---

## Exigences Non Fonctionnelles

| ID | Catégorie | Exigence |
|---|---|---|
| NFR-001 | Performance | L'application est utilisable sur un Raspberry Pi 4 (2 Go RAM) sans dégradation notable sous charge événementielle (3 postes simultanés, ~1 700 articles). |
| NFR-002 | Concurrence | Les opérations simultanées depuis plusieurs postes (scan, saisie de données, impression) ne génèrent pas de conflits de données. |
| NFR-003 | Exactitude Financière | Les calculs de reversement (prix − commission) sont exacts au centime pour chaque vendeur et pour les totaux d'édition. |
| NFR-004 | Compatibilité Navigateur | L'interface fonctionne sur tout navigateur moderne (Chrome, Firefox, Edge, Safari) sur tout OS. |
| NFR-005 | Compatibilité Scanner | Les scanners USB HID fonctionnent sans configuration, quelle que soit la disposition clavier du poste (AZERTY/QWERTY). |
| NFR-006 | Fiabilité | Aucune perte de données ne survient lors d'une fermeture inattendue du navigateur ou d'une défaillance d'un poste client. |
| NFR-007 | RGPD | Les données personnelles vendeur (nom, prénom, email, téléphone) sont supprimables sur demande. Les données anonymisées dans les éditions archivées ne permettent pas la réidentification. |

---

## Métriques de Succès

| ID | Métrique de Succès | Contre-Métrique |
|---|---|---|
| SM-1 | 3 postes caisse fonctionnent simultanément sans conflits de données | Pas de latence notable côté caisse due aux verrous ou à la synchronisation |
| SM-2 | Les calculs de reversement correspondent à la vérification manuelle au centime près | Aucune commission appliquée incorrectement sur un lot ou un article incomplet |
| SM-3 | Les étiquettes thermiques se scannent de façon fiable avec un scanner USB standard | Le temps d'impression total pour un dépôt vendeur complet ne dépasse pas 2 minutes |
| SM-4 | Tous les documents PDF s'impriment correctement depuis n'importe quel OS de poste (Linux, macOS, Windows) | Aucun document tronqué ou mal formaté selon l'OS du poste déclencheur |
| SM-5 | L'admin ouvre et ferme les phases sans incident, avec un retour d'état clair | Aucune transition de phase accidentelle due à une interface ambiguë |
| SM-6 | Le serveur fonctionne sans dégradation notable sur Raspberry Pi 4 (2 Go RAM) sous charge événementielle | L'utilisation mémoire ne dépasse pas 80 % dans les conditions normales d'événement |
| SM-7 | Un utilisateur non technique installe et configure l'instance seul, guide en main, sans assistance développeur | Le guide ne nécessite aucune connaissance préalable de Docker ou de la ligne de commande au-delà des instructions littérales |
