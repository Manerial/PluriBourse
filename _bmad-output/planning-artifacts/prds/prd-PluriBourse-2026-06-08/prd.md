---
title: "PRD : PluriBourse v1"
status: final
created: 2026-06-08
updated: 2026-06-15
---

# PRD : PluriBourse v1

## Énoncé du Problème

Les associations organisant des ventes d'occasion (jouets, livres, skis, vêtements, etc.) se trouvent dans l'une de ces deux situations : elles gèrent tout manuellement sur papier ou tableur, ou elles s'appuient sur un logiciel existant que personne ne peut maintenir.

Dans le premier cas, la gestion manuelle ne passe pas à l'échelle : l'inscription des vendeurs, l'étiquetage des articles, la caisse multi-poste et le calcul des reversements deviennent ingérables au-delà d'un certain volume.

Dans le second cas, le logiciel fonctionne — jusqu'à ce qu'il tombe en panne. Des chemins codés en dur échouent au moindre changement d'infrastructure. L'absence de documentation fait de l'auteur original le seul capable de diagnostiquer les pannes. Des architectures datées, installées directement sur le serveur sans historique des modifications, rendent l'investigation fastidieuse et le passage de relais impossible. Chaque édition comporte le risque qu'une mise à jour de routine ou une nouvelle machine brise silencieusement l'outil, sans possibilité de le corriger sous la pression du jour J.

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
| G4 | Déployable par toute association indépendamment | Le modèle d'instance unique est conçu pour être répliqué — chaque association déploie la sienne sans dépendance à un tiers |
| G5 | Maintenable par la communauté | Stack standard, bien documentée, sans dépendances exotiques |

---

## Utilisateurs & Rôles

| Rôle | Accès                        | Notes |
|---|------------------------------|---|
| **Administrateur** | Complet                      | Gestion des phases, commission, éditions, comptes bénévoles, rapports |
| **Bénévole** | Dépôt + Caisse + Solde | Rôle unique ; l'interface s'adapte à la phase active. Utilisateurs non techniques opérant sous la pression du jour J |
| **Vendeur** | Hors application V1          | Documents papier uniquement — bordereau de dépôt à l'arrivée, bilan de vente à la récupération |

> Un admin ne peut pas opérer en tant que bénévole depuis son compte admin. Pour assurer des fonctions de caisse ou de dépôt, l'admin crée un compte bénévole dédié.

---

## Périmètre

### Inclus — v1

**Internationalisation**

- Interface en anglais et français : langue configurée par compte utilisateur
- Documents imprimés en anglais ou français : langue configurée par édition

**Gestion des Événements**

- Nommage libre des éditions ; plusieurs éditions par an supportées
- Cycle de vie des phases contrôlé par l'admin : Préparation → Dépôt → Vente → Post-vente → Clôturé
- Dialogue de confirmation requis pour toute transition de phase (avant ou arrière)
- Retour en arrière disponible phase par phase ; données toujours préservées
- À la clôture, les vendeurs non soldés sont automatiquement marqués Non réclamé (montant intégral → recettes de l'association) ; une alerte dans la dialog de confirmation indique le nombre de vendeurs concernés et le montant total avant confirmation
- Action post-clôture « Archiver l'Édition » : copie chaque article de l'édition (nom, catégorie, statut vendu/invendu) dans une table d'archivage, puis supprime définitivement les enregistrements articles et les profils vendeurs de l'édition, et désactive le retour en arrière
- Taux de commission configurable par édition (initialisé depuis un paramètre instance, défaut 20 %)

**Gestion des Vendeurs & Articles**

- Profils vendeurs propres à chaque édition (nom, prénom, email, téléphone)
- Inscription des articles : nom, prix, catégorie, indicateur complet/incomplet
- Table assignée automatiquement selon le mapping catégorie-table configuré par édition
- Support des lots : ensembles indivisibles à prix global unique, une étiquette par article
- Génération de codes-barres Code 128 et impression d'étiquettes sur rouleau thermique adhésif 57mm
- Impression de bordereau de dépôt par vendeur (articles, prix, solde net attendu)

**Point de Vente**

- Interface caisse avec support scanner USB HID (AZERTY/QWERTY transparent)
- Panier : plusieurs articles par transaction, une facture acheteur globale
- Moyen de paiement enregistré à chaque transaction (espèces, chèque, carte) — sélection obligatoire
- En cas de paiement en espèces, calcul de la monnaie à rendre si le montant remis est saisi
- Impression de facture acheteur à la demande

**Post-Vente**

- Solde vendeur : le bénévole saisit le montant remis en espèces et clique « Solder »
- Bouton « Non réclamé » : le montant intégral dû est transféré aux recettes de l'association
- Bilan de vente par vendeur : articles vendus, invendus avec emplacement de table, reversement net

**Rapports**

- Bilan journalier (PDF, admin uniquement)
- Bilan d'édition (PDF, admin uniquement, généré à la clôture)
- Rapport des vendeurs en attente (vendeurs non soldés avec numéro de téléphone)

**Catalogue Articles**

- Catalogue filtrable et triable de tous les articles de l'édition active (accessible à l'admin et aux bénévoles)

**Infrastructure & Accès**

- Rôles Admin et Bénévole, strictement séparés
- Support multi-poste (minimum 3 simultanés)
- Déploiement Docker Compose (Spring Boot + MariaDB)
- N imprimantes thermiques Bluetooth et N imprimantes A4 réseau (WiFi), connectées au serveur — chaque bénévole sélectionne sa paire d'imprimantes préférée à la connexion
- Point d'impression centralisé — aucune imprimante requise sur les postes clients
- Guide d'installation pour utilisateurs non techniques, avec instructions spécifiques par OS (Linux, macOS, Windows)

### Hors — v1
- Traitement de paiement intégré
- Portail vendeur en libre-service ou notifications email/SMS
- Application mobile
- Hébergement SaaS multi-tenant (chaque association héberge sa propre instance)
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
| FR-005 | La langue de tous les documents imprimés (bordereaux de dépôt, factures acheteur, bilans de vente, rapports) est configurée par édition. |
| FR-006 | Chaque édition possède sa propre langue de documents, initialisée depuis le paramètre instance à sa création. Le paramètre instance sert de valeur par défaut pour les nouvelles éditions. |
| FR-007 | La langue de documents d'une édition est modifiable par l'admin à tout moment. La valeur par défaut de l'instance reste modifiable à tout moment et ne s'applique qu'aux nouvelles éditions créées après la modification. |

---

### F2 — Gestion des Éditions & Cycle de Vie

| ID | Exigence |
|---|---|
| FR-008 | L'admin peut créer une édition avec un nom libre (ex. « Bourse de printemps 2026 », « Vide-grenier novembre »). |
| FR-009 | Plusieurs éditions peuvent être créées par an. |
| FR-010 | Une seule édition peut être active à la fois. Une édition est « active » tant qu'elle est en phase Préparation, Dépôt, Vente ou Post-vente. Une édition Clôturée n'est plus active. |
| FR-011 | Toute transition de phase — avant ou arrière — nécessite une confirmation explicite de l'admin via un dialogue. |
| FR-012 | La phase active de l'édition courante est affichée clairement à tous les utilisateurs connectés. |
| FR-013 | L'admin déclenche la clôture de l'édition via un bouton « Clôturer l'Édition » en phase Post-vente. Tous les documents sont générés en PDF dans les deux langues (EN et FR). L'édition passe en lecture seule. Les enregistrements articles restent en base jusqu'à ce que l'admin déclenche explicitement l'action Archiver l'Édition. |
| FR-014 | Une édition ayant dépassé la phase Préparation ne peut pas être supprimée. |
| FR-015 | Les données de chaque édition sont strictement cloisonnées — articles, ventes et rapports ne se mélangent jamais entre éditions. |
| FR-016 | Chaque édition possède son propre taux de commission, initialisé depuis le paramètre instance au moment de la création (défaut 20 %). L'admin peut le modifier en phase Préparation. Une fois la phase Dépôt démarrée, le taux est gelé pour cette édition et s'applique à tous ses articles. |
| FR-080 | Lors de la création d'une nouvelle édition, l'admin peut soit configurer les catégories et le mapping catégorie-table depuis zéro, soit copier la structure d'une édition clôturée. La copie inclut les catégories d'articles et le mapping catégorie-table uniquement. Le taux de commission et la langue de documents sont initialisés depuis les paramètres instance (FR-016, FR-006). |
| FR-082 | L'admin peut revenir en arrière d'une phase à la fois : Clôturé → Post-vente, Post-vente → Vente, Vente → Dépôt, Dépôt → Préparation. Les données enregistrées dans la phase annulée sont intégralement préservées — rien n'est supprimé ni annulé. En particulier : les ventes enregistrées restent marquées comme vendues lors d'un retour Vente → Dépôt ; les soldes enregistrés restent valides lors d'un retour Post-vente → Vente. Les articles appartenant à un vendeur déjà soldé ne peuvent plus être mis en vente (le vendeur est supposé avoir récupéré ses invendus). Le retour depuis Clôturé n'est disponible qu'avant le déclenchement de l'action Archiver l'Édition (FR-088). |
| FR-088 | Après clôture, l'admin peut déclencher une action **« Archiver l'Édition »** qui : (1) copie chaque article de l'édition dans une table d'archivage avec son nom, sa catégorie et son statut (vendu ou invendu) — les articles de lot sont archivés individuellement, sans conserver la notion de lot ; (2) supprime définitivement les enregistrements articles et les profils vendeurs de cette édition. Après archivage, le retour en arrière vers Post-vente est définitivement désactivé pour cette édition. Cette action nécessite une confirmation explicite. |
| FR-096 | À la clôture de l'édition, tous les vendeurs non soldés sont automatiquement marqués « Non réclamé » : leur montant net dû est enregistré en recettes de l'association (même logique que FR-052), de manière atomique avec la transition de phase. Si au moins un vendeur est non soldé au moment de la clôture, la boîte de dialogue de confirmation affiche : « X vendeur(s) non soldé(s) seront automatiquement marqués Non réclamé. Montant total transféré aux recettes de l'association : Y,YY €. » Le bouton « Clôturer l'édition » n'est plus désactivé en présence de vendeurs non soldés. |
| FR-099 | *(Retiré — 2026-07-06, voir `sprint-change-proposal-2026-07-06.md`)* Les bénévoles peuvent se connecter à tout moment, y compris hors édition active. L'accès aux données d'une édition reste strictement conditionné à l'existence d'une édition active et à sa phase courante, vérifié côté serveur à chaque requête métier (voir FR-015). |
| FR-100 | Une édition possède deux champs de date optionnels — date de début et date de fin — à titre purement informatif et administratif. Ces dates n'ont aucune incidence sur la logique métier ni sur les transitions de phase. Elles sont saisies dans le formulaire de création/édition, affichées dans la liste des éditions et conservées en base de données. |

---

### F3 — Gestion des Vendeurs & Articles (Phase de Dépôt)

#### Pré-configuration Admin

| ID | Exigence |
|---|---|
| FR-017 | L'admin configure la liste des catégories d'articles par édition. |
| FR-018 | L'admin configure le mapping catégorie-table par édition (ex. jeux de société → tables 1, 2, 3 ; livres → tables 4, 5). Les tables sont identifiées par numéro. Une même table peut être assignée à plusieurs catégories (relation many-to-many) : deux petites catégories peuvent ainsi partager une même table physique. Chaque catégorie doit avoir au moins une table assignée — la sauvegarde est bloquée si une catégorie n'en a aucune. Les catégories et leur mapping sont modifiables en phase Préparation. Une fois la phase Dépôt démarrée, ils sont figés pour l'édition. Un retour arrière vers la phase Préparation (FR-082) les rend à nouveau modifiables. |

#### Inscription des Vendeurs

| ID | Exigence |
|---|---|
| FR-019 | Les profils vendeurs sont propres à chaque édition. Champs obligatoires : nom, prénom, email, numéro de téléphone. |
| FR-020 | Le bénévole recherche un vendeur existant par nom ou email. S'il n'est pas trouvé, un nouveau profil est créé. |
| FR-021 | L'admin peut supprimer un vendeur en phase de Dépôt. La suppression efface définitivement le profil vendeur et l'ensemble de ses articles dans cette édition (droit à l'effacement RGPD). Cette action nécessite une confirmation explicite. |

#### Inscription des Articles

| ID | Exigence |
|---|---|
| FR-022 | Pour chaque article, le bénévole saisit : nom/description, prix, catégorie, indicateur complet/incomplet, et un commentaire libre optionnel (disponible en tout temps, qu'il s'agisse d'un article complet ou incomplet). |
| FR-023 | La table est assignée automatiquement par le système selon le mapping catégorie-table de l'édition. Algorithme : si le vendeur a déjà des articles dans cette catégorie pour cette édition, la même table lui est réassignée. Sinon, le système choisit la table la moins chargée parmi celles configurées pour la catégorie. La charge d'une table est calculée sur l'ensemble des articles qui lui sont assignés pour l'édition, toutes catégories confondues. |
| FR-024 | Un article ne peut être corrigé ou supprimé qu'en phase de Dépôt. |
| FR-025 | L'indicateur complet/incomplet et le commentaire article sont modifiables dans toutes les phases. |
| FR-089 | La commission s'applique normalement aux articles vendus avec l'indicateur incomplet. Le prix de vente et le taux de commission ne sont pas modifiés par l'état de complétude de l'article. |

#### Lots

Un lot est un ensemble indivisible d'articles vendu à un prix global unique, chaque article recevant sa propre étiquette.

| ID | Exigence |
|---|---|
| FR-043 | Un bénévole peut créer un lot en lui assignant un nom et un prix global, puis en y ajoutant plusieurs articles. |
| FR-044 | Chaque article du lot possède son propre nom/description et reçoit sa propre étiquette. |
| FR-045 | L'étiquette d'un article de lot affiche, en plus des champs standard : « Prix du lot : X€ » en lieu et place d'un prix individuel, et « Lot indivisible : X/N » (X = position de l'article, N = nombre total d'articles dans le lot). |

#### Impression

| ID | Exigence                                                                                                                                                                                                                                                                                   |
|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-026 | Un code-barres Code 128 unique est généré côté serveur pour chaque article inscrit. Le numéro encode 8 chiffres : 4 chiffres pour le numéro du vendeur (dans l'édition) + 4 chiffres pour le numéro de l'article dans l'inventaire du vendeur. |
| FR-027 | L'étiquette article affiche de manière centrée, dans cet ordre : nom de l'édition — ligne vide — catégorie encadrée (« --- Catégorie --- ») — nom de l'article + prix sur une ligne — « /!\ INCOMPLET » sur une ligne dédiée si applicable — commentaire article sur une ligne dédiée si non vide — numéro de table (« Table n°X ») — ligne vide — graphique Code 128 (bitmap) — numéro de code-barres lisible au format XXXX-XXXX (séparation entre numéro vendeur et numéro article) — ligne vide. Le nom du vendeur n'apparaît pas (RGPD). |
| FR-028 | Le système déclenche l'impression des étiquettes automatiquement lorsqu'un bénévole valide le dépôt d'un vendeur.                                                                                                                                                                          |
| FR-029 | Les travaux d'impression sont mis en file d'attente côté serveur et exécutés séquentiellement.                                                                                                                                                                                             |
| FR-030 | Le rouleau imprimé suit ce format par vendeur : [séparateur vendeur : nom vendeur + édition] → [étiquette article] → [séparateur article] → [étiquette article] → …                                                                                                                        |
| FR-031 | Un bordereau de dépôt est imprimable par vendeur : liste des articles, prix unitaires et reversement net attendu après commission.                                                                                                                                                         |
| FR-032 | La largeur du ticket thermique (57 mm ou 80 mm) est configurable par imprimante thermique enregistrée — ce n'est plus un paramètre global d'instance. Voir FR-076. |

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
| FR-041 | La facture affiche : liste des articles, prix unitaires, total, nom de l'association (tel que configuré dans les paramètres instance — FR-073), nom de l'édition, date. Un lot apparaît sur une ligne unique (nom du lot, prix du lot). |
| FR-042 | L'application supporte un minimum de 3 postes caisse simultanés sans conflits de données. La limite effective dépend de la configuration du serveur. |
| FR-090 | Si l'admin déclenche une transition de phase alors qu'un bénévole a un panier actif, le système annule le panier et affiche un message d'erreur explicite au bénévole. |
| FR-093 | À la validation du paiement, le caissier sélectionne le moyen de paiement de l'acheteur parmi trois valeurs : espèces, chèque, carte. Cette sélection est obligatoire — la transaction ne peut pas être finalisée sans. Le moyen de paiement est enregistré avec la transaction. En cas de paiement en espèces, un champ optionnel « Somme remise » permet de saisir le montant donné par l'acheteur ; si renseigné, le système affiche la monnaie à rendre (somme remise − total du panier) ; si laissé vide, aucun calcul n'est effectué (montant exact supposé). |

#### Lots en Caisse

| ID | Exigence |
|---|---|
| FR-046 | Scanner un article appartenant à un lot affiche le nom du lot en rouge avec un compteur « X/N scanné(s) ». |
| FR-047 | Si le lot n'est pas complet lors de la validation, une notification inline avertissement est affichée dans le panier, mais la validation du paiement n'est pas bloquée — le caissier peut valider un lot incomplet. |
| FR-048 | Une fois complet, le lot est vendu à son prix global de lot. Les articles d'un lot n'ont pas de prix individuel — seul le lot en a un. La commission s'applique au prix global : `commission_lot = prix_lot × taux_commission`. |
| FR-081 | Si un caissier ne peut pas compléter un lot (article introuvable), il peut retirer le lot entier du panier. Tous les articles du lot déjà scannés sont retirés. |

---

### F5 — Post-Vente & Reversements

| ID | Exigence                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-095 | La page de solde est le point d'entrée de F5. Elle affiche la liste de tous les vendeurs de l'édition active, filtrable par statut (soldé / non soldé). Chaque ligne comporte les actions : imprimer le bilan de vente, accéder au formulaire de solde, marquer comme non réclamé. La page est accessible aux bénévoles via `/volunteer/settlement` et à l'admin via `/admin/settlement`. L'admin voit en plus le numéro de téléphone et l'adresse email de chaque vendeur. Le composant Angular sous-jacent est unique — l'affichage des colonnes de contact est conditionné par le rôle de l'utilisateur connecté. |
| FR-049 | En phase Post-vente, un **bilan de vente** est imprimable par vendeur.                                                                                                                                                                                                                                                                                                                                                                                                       |
| FR-050 | Le bilan de vente contient : articles vendus (nom, prix unitaire), invendus (nom, catégorie, numéro de table), total brut, commission déduite, montant net à reverser. Un lot apparaît sur une ligne unique (nom du lot, prix du lot).                                                                                                                                                                                                                                       |
| FR-051 | Pour solder un vendeur, le bénévole saisit le montant en espèces remis et clique « Solder ». Le système enregistre le montant saisi. Si ce montant est strictement inférieur au montant net calculé, un avertissement est affiché avant confirmation — le bénévole peut tout de même valider (cas où le vendeur souhaite récupérer un montant différent). Si le montant est supérieur, la validation est bloquée. Après l'opération, le statut du vendeur passe à **Soldé**. |
| FR-052 | Si un vendeur ne souhaite pas récupérer son reversement, un bouton **« Non réclamé »** enregistre le montant intégral dû comme recette de l'association.                                                                                                                                                                                                                                                                                                                     |
| FR-053 | Les vendeurs non soldés sont identifiables dans la liste de solde via un filtre dédié.                                                                                                                                                                                                                                                                                                                                                                                       |
| FR-097 | En phase Post-vente, l'admin peut déclencher depuis `/admin/settlement` l'impression groupée des bilans de vente de tous les vendeurs correspondant au filtre actif, toutes pages confondues. Le périmètre est résolu côté serveur depuis le filtre courant — la pagination n'est pas un facteur limitant. Le retour visuel suit le pattern UX-DR19 (spinner, toast succès avec compteur, toast d'erreur persistant si des enfilages échouent avec lien vers `/admin/print-queue`). Absent en phase Clôturée. Absent de `/volunteer/settlement`. |

---

### F6 — Rapports

| ID | Exigence                                                                                                                                                                                                                                                                          |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-054 | Un **bilan journalier** est générable par l'admin à tout moment pendant la phase de Vente. Il couvre le jour calendaire courant. Il contient : nombre d'articles vendus/invendus pour la journée, chiffre d'affaires journalier, commission journalière gagnée par l'association. |
| FR-055 | Un **bilan d'édition** est généré à la clôture de l'édition. Il contient : total des articles vendus/invendus, chiffre d'affaires brut total, commission totale gagnée par l'association.                                                                                         |
| FR-094 | Le bilan journalier et le bilan d'édition incluent une ventilation des recettes par moyen de paiement (espèces, chèque, carte).                                                                                                                                                   |
| FR-057 | Tous les rapports sont générés en PDF.                                                                                                                                                                                                                                            |
| FR-058 | Les rapports sont accessibles à l'admin uniquement.                                                                                                                                                                                                                               |
| FR-059 | Les éditions clôturées affichent les métriques agrégées en lecture seule. Les profils vendeurs et le détail des articles restent consultables jusqu'au déclenchement de l'action Archiver l'Édition ; après archivage, seules les métriques agrégées sont accessibles en base.     |
| FR-091 | En phase Post-vente et Clôturée, l'admin peut exporter le catalogue articles au format CSV (articles avec leur statut vendu/invendu). Le téléchargement est déclenché directement sans boîte de dialogue. |
| FR-092 | En phase Post-vente et Clôturée, l'admin peut exporter les reversements vendeurs au format CSV. Le téléchargement est déclenché directement sans boîte de dialogue. |

---

### F7 — Comptes Utilisateurs & Contrôle d'Accès

| ID | Exigence |
|---|---|
| FR-060 | L'admin crée, modifie et désactive les comptes bénévoles. L'admin peut réinitialiser le mot de passe d'un bénévole. |
| FR-061 | Il y a un seul compte admin par instance. |
| FR-062 | Au premier lancement, le compte admin est initialisé avec les identifiants Admin/Admin. L'admin est forcé de changer son mot de passe à la première connexion. |
| FR-063 | Si l'admin perd son mot de passe, une commande exécutée sur le serveur génère un mot de passe temporaire. L'admin est forcé de le changer à la prochaine connexion. |
| FR-064 | Les rôles Admin et Bénévole sont strictement séparés. Un admin ne peut pas accéder aux interfaces bénévole depuis son compte admin. Pour opérer en caisse ou au dépôt, l'admin crée un compte bénévole séparé. |
| FR-065 | L'interface bénévole s'adapte à la phase active : dépôt en phase Dépôt, caisse en phase Vente, reversement en phase Post-vente. En phase Post-vente, le bénévole peut imprimer le bilan de vente d'un vendeur pour regrouper ses invendus avant la remise. |
| FR-066 | Les sessions n'expirent pas automatiquement. [NON-GOAL pour v1 : la gestion des sessions n'est pas requise — la plateforme opère sur un réseau local fermé et une reconnexion forcée le jour J serait trop contraignante pour les bénévoles.] |
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
| FR-073 | Une page de paramètres admin centralise la configuration de l'instance : nom de l'association, taux de commission par défaut, langue des documents par défaut. |
| FR-074 | Le guide d'installation est un livrable à part entière, destiné à un non-technicien. Il couvre 7 sections obligatoires (prérequis, installation de Docker par OS, déploiement, premier lancement, configuration initiale, réinitialisation du mot de passe admin, mise à jour) et doit pouvoir être suivi de A à Z sans assistance. Voir détail ci-dessous. |

#### Détail FR-074 — Guide d'installation

**Public cible :** un responsable d'association capable d'utiliser un ordinateur, sans aucune connaissance de Docker, des conteneurs ou du terminal. Le guide doit pouvoir être suivi de A à Z sans assistance extérieure.

**Structure obligatoire (7 sections, dans cet ordre) :**

1. **Prérequis** — matériel minimal (Raspberry Pi 4 ou PC 64 bits), espace disque recommandé, aucun logiciel prérequis sauf Docker.
2. **Installation de Docker** — instructions séparées par OS : Docker Desktop pour Windows et macOS (lien de téléchargement officiel, étapes d'installation GUI) ; Docker Engine pour Linux et Raspberry Pi (commandes `apt` / `apt-get`, activation du service).
3. **Téléchargement et lancement** — récupération du `docker-compose.yml`, commande `docker compose up -d`, vérification que l'application répond dans le navigateur à `http://localhost:8080` (ou le port configuré).
4. **Premier lancement** — accès à l'interface, connexion avec `Admin` / `Admin`, procédure de changement de mot de passe obligatoire.
5. **Configuration initiale** — paramétrage du nom de l'association, du taux de commission par défaut et de la langue des documents via la page Paramètres ; installation et lancement de PrinterBridge (composant natif séparé, poste admin) ; enregistrement des imprimantes thermiques et A4 via la page Gestion des imprimantes, parmi celles détectées par PrinterBridge.
6. **Réinitialisation du mot de passe admin** — commande CLI exacte à exécuter sur le serveur (avec les étapes pour ouvrir un terminal selon l'OS), résultat attendu affiché dans la console, procédure de connexion avec le mot de passe temporaire.
7. **Mise à jour** — commande exacte `docker compose pull && docker compose up -d`, confirmation que les données sont préservées.

**Contraintes de rédaction :**
- Chaque commande est dans un bloc de code copier-coller.
- Chaque section majeure se termine par une étape de vérification (« Comment savoir que ça a fonctionné »).
- Aucun jargon technique non expliqué — si « terminal » est mentionné, expliquer comment l'ouvrir selon l'OS.
- Le vouvoiement est utilisé en français.

---

### F9 — Infrastructure d'Impression

| ID | Exigence |
|---|---|
| FR-075 | Toute impression est routée via le serveur central — aucune imprimante requise sur les postes clients. |
| FR-076 | **Imprimantes thermiques** (étiquettes articles) : l'admin enregistre une ou plusieurs imprimantes thermiques Bluetooth dans l'interface d'administration. Chaque imprimante est nommée et associée à une imprimante détectée par le service PrinterBridge (composant natif séparé, installé sur le poste admin, qui possède l'accès matériel Bluetooth). PrinterBridge consomme les ports déjà disponibles et les expose à PluriBourse via une API locale. La largeur du ticket (57 mm ou 80 mm) est configurable par imprimante (FR-032). Chaque imprimante dispose de sa propre file d'impression indépendante (FR-029). |
| FR-077 | **Imprimantes A4** (documents) : l'admin enregistre une ou plusieurs imprimantes A4 dans l'interface d'administration. Chaque imprimante est nommée et sélectionnée parmi les imprimantes déjà installées dans le spouleur d'impression du système d'exploitation, détectées et exposées par PrinterBridge. PDF généré côté serveur, transmis à PrinterBridge qui le soumet au spouleur OS. Chaque imprimante dispose de sa propre file d'impression indépendante (FR-029). |
| | [ASSUMPTION: les imprimantes thermiques sont appairées en Bluetooth au niveau OS, sur le poste où tourne PrinterBridge, avant le début de l'événement. Une édition sans imprimante thermique enregistrée et accessible ne peut pas opérer la phase Dépôt — aucune étiquette ne peut être générée.] |
| FR-078 | Un utilisateur déclenche l'impression depuis l'interface ; la requête est traitée par le serveur sans action requise sur le poste client. |
| FR-079 | En cas d'erreur d'impression (imprimante hors ligne, bourrage papier, manque de papier), l'utilisateur est notifié dans l'interface avec un message explicite indiquant la cause de l'erreur. La file de l'imprimante concernée est suspendue ; les autres files ne sont pas affectées. L'utilisateur peut relancer le job en erreur ou l'ignorer pour reprendre la file. L'admin dispose d'une vue de diagnostic affichant par imprimante enregistrée : profondeur de file, statut du thread consommateur, dernière erreur. Au démarrage du serveur, la connectivité de chaque imprimante enregistrée est vérifiée via un appel au statut PrinterBridge (plutôt qu'un test direct de port/adresse) ; toute imprimante inaccessible est signalée par une alerte dans le tableau de bord admin, avec une distinction entre PrinterBridge lui-même injoignable et une imprimante spécifique signalée hors ligne. |
| FR-098 | À la connexion, le bénévole sélectionne une imprimante thermique et une imprimante A4 parmi les imprimantes enregistrées et disponibles. Cette sélection est active pour toute la durée de la session et n'est pas persistée entre les sessions. Si l'imprimante sélectionnée est indisponible au moment d'un travail d'impression, le job échoue immédiatement avec un message d'erreur explicite — pas de retry automatique ni de reroutage. |
| FR-104 | L'admin peut ignorer une imprimante détectée par PrinterBridge mais non enregistrée, afin qu'elle cesse d'apparaître dans la liste de découverte (`GET /admin/printers/discovered`). L'action est réversible : une section dédiée liste les imprimantes ignorées, avec une action permettant de les réactiver (elles réapparaissent alors dans la découverte au prochain scan). Une imprimante déjà enregistrée dans le registre ne peut pas être ignorée — l'action ne s'applique qu'aux imprimantes détectées et non enregistrées. *(Renumérotée depuis FR-100 le 2026-07-29 — collision avec le FR-100 pré-existant des dates d'édition, Story 2.4. FR-103 laissée libre pour de futures statistiques comparatives inter-éditions, voir FR-102.)* |

---

### F10 — Catalogue Articles

*Disponible pendant toutes les phases de l'édition active.*

| ID | Exigence |
|---|---|
| FR-083 | Un catalogue d'articles filtrable et triable est accessible à l'admin et aux bénévoles pendant toutes les phases de l'édition active. |
| FR-084 | Le catalogue peut être filtré par : nom/description, numéro de code-barres, catégorie, table, statut vendu/invendu, indicateur complet/incomplet, nom du vendeur. |
| FR-085 | Le catalogue peut être trié par n'importe quelle colonne visible. |
| FR-086 | Le catalogue affiche les articles de l'édition active uniquement. Les données au niveau article ne sont pas disponibles sur les éditions où l'action Archiver l'Édition a été déclenchée. |
| FR-102 | L'administrateur peut consulter le catalogue archivé d'une édition passée (déjà Archivée), via un sélecteur d'édition. La liste est filtrable et triable sur les seules données conservées par l'archivage (FR-088) : nom, catégorie, statut vendu/invendu — le prix, le code-barres, la table et le vendeur ne sont plus disponibles après archivage, par construction. Cette consultation sert de brique de base pour de futures statistiques comparatives entre éditions (hors scope de cette exigence). Réservé aux administrateurs. Dépend de la Story 2.7 (mécanisme d'archivage). *(2026-07-29, voir `sprint-change-proposal-2026-07-29.md`)* |

---

## Exigences Non Fonctionnelles

| ID | Catégorie | Exigence |
|---|---|---|
| NFR-001 | Performance | L'application est utilisable sur un Raspberry Pi 4 (2 Go RAM) sans dégradation notable sous charge événementielle. Charge de référence : ~100 vendeurs, ~1 700 articles, 3 postes simultanés. Les opérations caisse (scan, validation de paiement) répondent en moins de 500ms sous charge normale. Les autres pages (catalogue, rapports) se chargent en moins de 1 seconde sous charge nominale. |
| NFR-002 | Concurrence | Les opérations simultanées depuis plusieurs postes (scan, saisie de données, impression) ne génèrent pas de conflits de données. Le système empêche la vente simultanée d'un même article depuis deux postes — le second poste reçoit un message d'erreur explicite. |
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
| SM-8 | Le serveur s'installe et fonctionne sous Linux, macOS et Windows sans modification du code | Le guide couvre les trois OS avec des instructions spécifiques — aucune étape n'est laissée à l'interprétation de l'OS |
