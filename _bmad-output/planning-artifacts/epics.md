---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md'
  - '_bmad-output/planning-artifacts/architecture.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md'
---

# PluriBourse - Découpage en Épics

## Vue d'ensemble

Ce document présente le découpage complet en épics et en stories pour PluriBourse, en décomposant les exigences issues du PRD, du Design UX et de l'Architecture en stories implémentables.

## Inventaire des exigences

### Exigences fonctionnelles

**F1 — Internationalisation (EN/FR)**

- FR-001 : L'interface utilisateur est disponible en anglais et en français.
- FR-002 : La langue par défaut de l'interface est détectée à partir du navigateur et enregistrée dans les préférences du compte à la première connexion de l'utilisateur.
- FR-003 : Chaque utilisateur peut modifier sa préférence de langue dans les paramètres de son compte.
- FR-004 : Tous les textes de l'interface sont externalisés — aucun texte n'est codé en dur dans le code source.
- FR-005 : La langue des documents imprimés est configurée par édition.
- FR-006 : Chaque édition possède sa propre langue de documents, initialisée depuis le paramètre instance à sa création. Le paramètre instance sert de valeur par défaut pour les nouvelles éditions.
- FR-007 : La langue de documents d'une édition est modifiable par l'admin à tout moment. La valeur par défaut de l'instance reste modifiable à tout moment et ne s'applique qu'aux nouvelles éditions.

**F2 — Gestion des éditions et cycle de vie des événements**

- FR-008 : L'administrateur peut créer une édition avec un nom libre.
- FR-009 : Plusieurs éditions peuvent être créées par année.
- FR-010 : Une seule édition peut être active à la fois.
- FR-011 : Toute transition de phase — vers l'avant ou vers l'arrière — nécessite une confirmation explicite de l'administrateur via une boîte de dialogue.
- FR-012 : La phase active de l'édition en cours est affichée clairement à tous les utilisateurs connectés.
- FR-013 : L'administrateur déclenche la clôture de l'édition via « Clôturer l'édition » en phase Post-vente. Tous les documents sont générés en PDF dans les deux langues. L'édition passe en lecture seule.
- FR-014 : Une édition ayant dépassé la phase Préparation ne peut pas être supprimée.
- FR-015 : Les données de chaque édition sont strictement isolées.
- FR-016 : Le taux de commission est configuré lors de la mise en place de l'instance (20 % par défaut), modifiable par l'administrateur jusqu'au démarrage de la phase Dépôt, puis figé pour cette édition.
- FR-017 : L'administrateur configure la liste des catégories d'articles par édition.
- FR-018 : L'administrateur configure la correspondance catégorie-table par édition. Une même table peut être assignée à plusieurs catégories (relation many-to-many). Chaque catégorie doit avoir au moins une table — la sauvegarde est bloquée sinon. Modifiable en phase Préparation, figé au démarrage de Dépôt, à nouveau modifiable en cas de retour arrière vers Préparation (FR-082).
- FR-080 : Lors de la création d'une nouvelle édition, l'administrateur peut copier les catégories et la correspondance tables depuis une édition clôturée.
- FR-082 : L'administrateur peut revenir en arrière d'une phase à la fois. Les ventes et les soldes sont préservés. Les articles des vendeurs déjà soldés ne peuvent plus être vendus. Le retour arrière depuis l'état Clôturé est désactivé après l'Archivage de l'édition.
- FR-088 : Après clôture, l'administrateur peut déclencher l'« Archivage de l'édition » — copie chaque article (nom, catégorie, statut vendu/invendu) dans une table d'archivage, puis supprime définitivement les enregistrements d'articles et les profils vendeurs de l'édition. Les articles de lot sont archivés individuellement sans conserver la notion de lot. Nécessite une confirmation explicite. Désactive le retour arrière vers Post-vente.
- FR-096 : À la clôture de l'édition, tous les vendeurs non soldés sont automatiquement marqués « Non réclamé » et leurs montants enregistrés en recettes de l'association (même logique que FR-052), de manière atomique avec la transition de phase. Si au moins un vendeur est non soldé, la boîte de dialogue de confirmation affiche le nombre de vendeurs concernés et le montant total à transférer. Le bouton « Clôturer l'édition » n'est plus désactivé en présence de vendeurs non soldés.
- FR-099 : *(Retiré — 2026-07-06)* Les bénévoles peuvent se connecter à tout moment ; l'accès aux données d'édition reste protégé par FR-015 (isolation stricte par édition), vérifiée côté serveur à chaque requête.
- FR-100 : Une édition possède deux dates optionnelles — date de début et date de fin — à titre purement informatif et administratif. Ces dates n'ont aucune incidence sur la logique métier. Elles sont saisies dans le formulaire de création/édition, affichées dans la liste des éditions et conservées en base de données.

**F3 — Gestion des vendeurs et des articles (Phase Dépôt)**

- FR-019 : Les profils vendeurs sont propres à chaque édition. Champs obligatoires : nom, prénom, adresse e-mail, numéro de téléphone.
- FR-020 : Le bénévole recherche un vendeur existant par nom ou e-mail. Si aucun résultat, un nouveau profil est créé.
- FR-021 : L'administrateur peut supprimer un vendeur en phase de Dépôt (RGPD), à condition qu'aucun article ne soit enregistré pour lui dans cette édition — la suppression n'efface jamais ses articles en cascade, elle est refusée tant qu'il en reste (voir Story 3.2). Confirmation explicite requise.
- FR-022 : Pour chaque article (individuel ou membre d'un lot), le bénévole saisit : nom/description, prix, catégorie, indicateur complet/incomplet, et un commentaire libre optionnel disponible en tout temps, qu'il s'agisse d'un article complet ou incomplet.
- FR-023 : La table est automatiquement assignée selon la correspondance catégorie-table de l'édition. Algorithme : si le vendeur a déjà des articles dans cette catégorie pour cette édition, la même table lui est réassignée ; sinon, le système choisit la table la moins chargée parmi celles configurées pour la catégorie. La charge est calculée sur l'ensemble des articles assignés à la table, toutes catégories confondues.
- FR-024 : Un article ne peut être corrigé ou supprimé que durant la phase Dépôt.
- FR-025 : L'indicateur complet/incomplet et le commentaire article sont modifiables dans toutes les phases.
- FR-026 : Un code-barres Code 128 unique est généré côté serveur pour chaque article enregistré. Le numéro encode 8 chiffres : 4 chiffres pour le numéro du vendeur (dans l'édition) + 4 chiffres pour le numéro de l'article dans l'inventaire du vendeur.
- FR-027 : L'étiquette article affiche de manière centrée, dans cet ordre : nom de l'édition — ligne vide — « --- Catégorie --- » — nom de l'article + prix — « /!\ INCOMPLET » (ligne dédiée, si applicable) — commentaire article (ligne dédiée, si non vide) — « Table n°X » — ligne vide — graphique Code 128 (bitmap) — numéro de code-barres lisible au format XXXX-XXXX (séparation entre numéro vendeur et numéro article) — ligne vide. Aucun nom de vendeur (RGPD).
- FR-028 : Le système déclenche l'impression des étiquettes automatiquement lorsqu'un bénévole valide le dépôt d'un vendeur.
- FR-029 : Les travaux d'impression sont mis en file d'attente côté serveur et exécutés séquentiellement.
- FR-030 : Le rouleau imprimé suit le format : [séparateur vendeur : nom du vendeur + édition] → [étiquette article] → [séparateur article] → [étiquette article] → …
- FR-031 : Un bordereau de dépôt est imprimable par vendeur **en phase Dépôt** : articles + prix (lot = 1 ligne), taux de commission, reversement net attendu, **+ tableau « détail des lots » (nom du lot, catégorie du lot, article)**. *(SCP 2026-09-02b)*
- FR-032 : La largeur du ticket thermique est configurable dans les paramètres administrateur (défaut : 57 mm).
- FR-043 : Un bénévole peut créer un lot en lui attribuant un nom et un prix global, puis en ajoutant plusieurs articles.
- FR-044 : Chaque article d'un lot possède son propre nom/description et reçoit sa propre étiquette.
- FR-045 : L'étiquette d'un article de lot affiche : « Prix du lot : X€ » à la place du prix individuel, et « Lot indivisible : X/N ».

**F4 — Point de vente (Phase Vente)**

- FR-033 : L'interface caissier permet les ventes via un scanner code-barres USB HID.
- FR-034 : Le composant de scan gère de manière transparente les différences de disposition de clavier AZERTY/QWERTY via une correspondance de codes de touches.
- FR-035 : Chaque article scanné est ajouté au panier de l'acheteur courant. Le système affiche le nom et le prix de l'article.
- FR-036 : Le scan d'un article déjà vendu affiche un message d'erreur explicite. L'article n'est pas ajouté au panier.
- FR-037 : Le scan d'un article incomplet affiche un avertissement informatif. L'article peut tout de même être vendu.
- FR-038 : Le caissier peut retirer un ou plusieurs articles du panier avant la validation du paiement.
- FR-039 : La validation du paiement marque tous les articles du panier comme vendus et clôt la transaction. Aucune modification n'est possible après.
- FR-040 : Après validation, une facture acheteur est imprimable à la demande.
- FR-041 : La facture affiche : liste des articles, prix unitaires, total, nom de l'association, nom de l'édition, date. Un lot apparaît sur une seule ligne.
- FR-042 : L'application supporte un minimum de 3 postes caissiers simultanés sans conflits de données.
- FR-090 : Si l'administrateur déclenche une transition de phase pendant qu'un bénévole a un panier actif, le système annule le panier et affiche un message d'erreur explicite.
- FR-093 : À la validation du paiement, le caissier sélectionne le moyen de paiement de l'acheteur. Valeurs possibles : espèces, chèque, carte. Le moyen de paiement est enregistré avec la transaction. En cas de paiement en espèces, un champ optionnel permet de saisir la somme remise par l'acheteur ; si renseigné, le système affiche la monnaie à rendre. Si laissé vide, aucun calcul n'est effectué (montant exact supposé).

**F4 bis — Lots en caisse**

- FR-046 : Le scan d'un article appartenant à un lot affiche le nom du lot en rouge avec un compteur « X/N scannés ».
- FR-047 : Si le lot n'est pas complet lors de la validation, une notification inline avertissement est affichée dans le panier, mais la validation du paiement n'est pas bloquée — le caissier peut valider un lot incomplet. **Dès qu'un article du lot est vendu, le lot est réputé vendu comme un tout ; les articles restants reviennent au vendeur (FR-109).** *(SCP 2026-09-02b)*
- FR-048 : Les articles d'un lot n'ont pas de prix individuel — seul le lot en a un. Une fois complet, le lot est vendu à son prix global. La commission s'applique au prix global : `commission_lot = prix_lot × taux_commission`.
- FR-081 : Si un caissier ne peut pas compléter un lot, il peut retirer l'ensemble du lot du panier.
- FR-109 : Un lot ne se vend qu'une fois — scanner un article d'un lot déjà vendu est rejeté (409, au scan et à la validation) ; les articles restants reviennent au vendeur. *(SCP 2026-09-02b)*

**F5 — Post-vente et reversements**

- FR-095 : La page de solde est le point d'entrée de F5. Elle affiche la liste de tous les vendeurs de l'édition active, filtrable par statut (soldé / non soldé). Actions par ligne : solder et marquer non réclamé (non soldés), imprimer le bilan (**soldés / non réclamés uniquement**) ; **case « Imprimer le bilan » cochée par défaut dans le formulaire de solde**. Accessible aux bénévoles via `/volunteer/settlement` et à l'admin via `/admin/settlement`. L'admin voit en plus téléphone et email. Composant Angular unique — affichage des colonnes de contact conditionné par le rôle. *(SCP 2026-09-02b)*
- FR-049 : En phase Post-vente, un bilan de vente est imprimable par vendeur.
- FR-050 : Le bilan de vente contient : tableau unifié des articles avec **statut vendu/invendu** (lot = 1 ligne), **tableau « détail des lots »** (statut réel par article), **ligne de comptage vendus/invendus/déposés**, total brut, commission, reversement net, **montant remis si soldé**. *(SCP 2026-09-02b)*
- FR-051 : Pour solder un vendeur, le bénévole saisit le montant en espèces remis et clique « Solder ». Le système enregistre le montant saisi. Si ce montant est strictement inférieur au montant net calculé, un avertissement est affiché avant confirmation — le bénévole peut tout de même valider. Si le montant est supérieur, la validation est bloquée. Après l'opération, le statut du vendeur passe à **Soldé**.
- FR-052 : Le bouton « Non réclamé » transfère l'intégralité du montant dû en recettes de l'association.
- FR-053 : Les vendeurs non soldés sont identifiables dans la liste de solde via un filtre dédié.
- FR-097 : En phase Post-vente, l'admin peut déclencher depuis `/admin/settlement` l'impression groupée des bilans de vente de tous les vendeurs correspondant au filtre actif (toutes pages confondues). Absent en phase Clôturée et de `/volunteer/settlement`.

**F6 — Rapports**

- FR-054 : Un bilan journalier est générable par l'administrateur en phase Vente. Couvre la journée calendaire en cours : articles vendus/invendus, recettes, commission.
- FR-055 : Un bilan d'édition est généré à la clôture de l'édition : total des articles vendus/invendus, recettes brutes totales, commission totale.
- FR-057 : Tous les rapports sont générés en PDF.
- FR-058 : Les rapports sont accessibles à l'administrateur uniquement.
- FR-094 : Le bilan journalier et le bilan d'édition incluent une ventilation des recettes par moyen de paiement (espèces, chèque, carte).
- FR-059 : Les éditions clôturées affichent les métriques agrégées en lecture seule. Les profils vendeurs et le détail des articles restent consultables jusqu'au déclenchement de l'action Archiver l'Édition ; après archivage, seules les métriques agrégées sont accessibles en base.

**F7 — Comptes utilisateurs et contrôle d'accès**

- FR-060 : L'administrateur crée, modifie et désactive les comptes bénévoles. L'administrateur peut réinitialiser le mot de passe d'un bénévole.
- FR-101 : Lorsque l'administrateur désactive ou supprime un compte bénévole, toute session déjà ouverte pour ce compte est invalidée côté serveur immédiatement — le bénévole perd l'accès à sa prochaine requête, sans attendre l'expiration naturelle de la session (jusqu'à 1h).
- FR-061 : Il existe un seul compte administrateur par instance.
- FR-062 : Au premier lancement, le compte administrateur est initialisé avec les identifiants Admin/Admin. L'administrateur est forcé de changer son mot de passe à la première connexion.
- FR-063 : Si l'administrateur perd son mot de passe, une commande exécutée sur le serveur génère un mot de passe temporaire. L'administrateur est forcé de le changer à la connexion suivante.
- FR-064 : Les rôles Administrateur et Bénévole sont strictement séparés. L'administrateur ne peut pas accéder aux interfaces bénévoles.
- FR-065 : L'interface bénévole s'adapte à la phase active. En Post-vente, le bénévole peut imprimer le bilan de vente.
- FR-066 : Les sessions n'expirent pas automatiquement.
- FR-067 : Chaque compte mémorise une préférence de langue d'interface (EN/FR), détectée depuis le navigateur à la création, modifiable dans les paramètres.

**F8 — Infrastructure et déploiement**

- FR-068 : Le serveur fonctionne sur Linux, macOS et Windows sans modification du code.
- FR-069 : Configuration minimale : Raspberry Pi 4 (2 Go de RAM). Stockage SSD/USB fortement recommandé.
- FR-070 : L'application est déployée via Docker Compose (Spring Boot + MariaDB). Les données sont dans des volumes Docker persistants.
- FR-071 : Les mises à jour s'appliquent avec : `docker compose pull && docker compose up -d`. Les données sont préservées.
- FR-072 : Les postes clients accèdent à l'application via un navigateur — aucune installation locale requise.
- FR-073 : Une page de paramètres administrateur centralise la configuration de l'instance : nom de l'association, taux de commission par défaut, langue des documents par défaut.
- FR-074 : Le guide d'installation cible les utilisateurs non techniques. Couvre l'installation de Docker, le démarrage, la configuration initiale, la réinitialisation du mot de passe et la procédure de mise à jour par OS (Linux, macOS, Windows).

**F9 — Infrastructure d'impression**

- FR-075 : Toute l'impression est acheminée via le serveur central — aucune imprimante n'est requise sur les postes clients.
- FR-076 : N imprimantes thermiques Bluetooth enregistrées par l'admin : nom, port série sélectionné depuis la liste OS (SerialPort.getCommPorts()), largeur (57 mm ou 80 mm). Chaque imprimante dispose d'une file indépendante.
- FR-077 : N imprimantes A4 réseau enregistrées par l'admin : nom, adresse IP/hostname, port TCP (défaut 9100). Chaque imprimante dispose d'une file indépendante.
- FR-078 : Un utilisateur déclenche l'impression depuis l'interface ; traité par le serveur, aucune action requise côté client.
- FR-079 : En cas d'erreur d'impression, l'utilisateur est notifié dans l'interface avec un message explicite. La file de l'imprimante concernée est suspendue ; les autres files ne sont pas affectées. Vue de diagnostic par imprimante (profondeur de file, statut thread, dernière erreur). Au démarrage, les ports série et adresses réseau configurés sont vérifiés ; toute imprimante inaccessible est signalée dans le tableau de bord admin.
- FR-098 : À la connexion, le bénévole sélectionne une imprimante thermique et une imprimante A4 parmi les imprimantes enregistrées et disponibles. Sélection active pour toute la session, non persistée. Si l'imprimante sélectionnée est indisponible au moment d'un job, le job échoue immédiatement avec un message d'erreur.

**F10 — Catalogue articles**

- FR-083 : Un catalogue articles filtrable et triable est accessible aux administrateurs et aux bénévoles durant toutes les phases de l'édition active.
- FR-084 : Catalogue filtré par : nom/description, numéro de code-barres, catégorie, table, statut vendu/invendu, indicateur complet/incomplet, nom du vendeur.
- FR-085 : Catalogue triable par n'importe quelle colonne visible.
- FR-086 : Le catalogue affiche uniquement les articles de l'édition active. Non disponible après l'action Archiver l'Édition.
- FR-089 : La commission s'applique normalement aux articles vendus avec l'indicateur incomplet.

### Exigences non fonctionnelles

- NFR-001 : Performance — charge de référence : ~100 vendeurs, ~1 700 articles, 3 postes simultanés. L'application est utilisable sur Raspberry Pi 4 (2 Go de RAM). Les opérations caisse (scan, validation de paiement) répondent en moins de 500ms sous charge normale. Les autres pages (catalogue, rapports) se chargent en moins de 1 seconde sous charge nominale.
- NFR-002 : Concurrence — le système empêche la vente simultanée d'un même article depuis deux postes via verrou optimiste (`@Version` sur `Item`). Le second poste reçoit un 409 avec la liste des articles en conflit.
- NFR-003 : Précision financière — les calculs de reversement sont précis au centime. Toutes les valeurs monétaires utilisent BigDecimal — jamais float ou double.
- NFR-004 : Compatibilité navigateurs — l'interface fonctionne sur tout navigateur moderne (Chrome, Firefox, Edge, Safari) sur tout système d'exploitation.
- NFR-005 : Compatibilité scanners — les scanners USB HID fonctionnent sans configuration, quelle que soit la disposition du clavier (AZERTY/QWERTY).
- NFR-006 : Fiabilité — aucune perte de données en cas de fermeture inattendue du navigateur ou de défaillance d'un poste client.
- NFR-007 : RGPD — les données personnelles des vendeurs sont supprimables sur demande. Les données anonymisées dans les éditions archivées ne doivent pas permettre la réidentification. Aucune donnée personnelle dans les logs applicatifs.

### Exigences complémentaires

Exigences issues de l'architecture ayant un impact sur l'implémentation :

- ARCH-001 : La mise en place du squelette de projet est la première story d'implémentation — Spring Initializr (Spring Boot 4.0.6, Java 21, Maven) + `ng new pluribourse-frontend --standalone --routing --style=scss`.
- ARCH-002 : Spring Session JDBC (MariaDB) pour la persistance des sessions — les sessions doivent survivre aux redémarrages du conteneur pendant les événements.
- ARCH-003 : Verrouillage optimiste (`@Version` sur l'entité `Item`) + contrainte UNIQUE en base sur l'état article vendu pour la concurrence au POS. Le conflit est détecté à la validation du paiement, retourne un 409 avec la liste des articles en conflit.
- ARCH-004 : Un test d'intégration Testcontainers (MariaDB) pour la concurrence POS est requis avant la livraison de F4 — le comportement de verrouillage H2 diffère de MariaDB.
- ARCH-005 : JPageFlow (`FilterService.filterData()`) pour tous les endpoints de listes paginées/filtrables. Bug connu : le tri BigDecimal est cassé en v1.5.0 — correctif requis avant la fonctionnalité de tri par prix.
- ARCH-006 : Migrations Liquibase : 4 changesets initiaux — 001-core-schema (users + FK nullable seller_profile_id), 002-spring-session, 003-category-table-mapping, 004-instance-config.
- ARCH-007 : MapStruct pour toute la correspondance entité↔DTO (ajouté manuellement après Spring Initializr, absent de l'interface Initializr).
- ARCH-008 : OpenPDF 3.0.0 (LGPL) pour toute la génération de PDF. iText 7 (AGPL) explicitement rejeté.
- ARCH-009 : escpos-coffee (ou équivalent) pour l'impression thermique ESC/POS via jSerialComm (port série RFCOMM Bluetooth). N files `LinkedBlockingQueue` dynamiques — une par imprimante enregistrée (thermique ou A4) — livraison au plus une fois, redéclenchable depuis l'interface. Les files sont instanciées au démarrage depuis la liste des imprimantes configurées en base.
- ARCH-010 : ZXing pour la génération de codes-barres Code 128 (Apache 2.0).
- ARCH-011 : Le rôle `SELLER` est déclaré dans le code et bloqué en 403 dans la v1 via `SecurityConfig`. Aucun endpoint ni interface SELLER jusqu'à la v2.
- ARCH-012 : SSE (`SseEmitterRegistry`) doit être initialisé avant les endpoints de transition de phase. Événements : `phase-changed` (payload : editionId, newPhase, previousPhase) et `basket-cancelled`.
- ARCH-013 : RFC 7807 Problem Details pour toutes les réponses d'erreur via `@ControllerAdvice`.
- ARCH-014 : Springdoc OpenAPI activé dans le profil `dev` uniquement, désactivé en `prod`.
- ARCH-015 : Ordre de build inter-composants — la machine à états des phases (F2) doit être implémentée avant F3, F4, F5, F10. Spring Session JDBC nécessite la migration Liquibase avant toute fonctionnalité d'authentification. Les consommateurs de la file d'impression doivent être des beans Spring avant l'impression F3/F4.
- ARCH-016 : Format d'étiquette ESC/POS : séparateur vendeur → étiquette article (nom édition, catégorie encadrée, nom+prix, /!\ INCOMPLET si applicable, commentaire si non vide, table, bitmap Code 128, numéro de code-barres) → séparateur article → …

### Exigences UX Design

- UX-DR1 : Implémenter le thème global Angular Material 3 avec tous les tokens de design du fichier DESIGN.md : primaire corail (`#C44626` clair / `#F07040` sombre), surfaces beige chaud (`#FFFBF9` clair / `#1A0C06` sombre), fond de la barre latérale (`#2A100A`), couleurs de statut sémantiques (succès vert `#166534`/`#F0FDF4`, avertissement corail-container, erreur rouge `#BA1A1A`/`#FFDAD6`), tokens d'élévation (3 niveaux), tokens de forme/arrondi (5 niveaux : 4/8/12/20/999px), échelle d'espacement (base-4 : 4/8/16/24/32/48/64px).
- UX-DR2 : Implémenter la police DM Sans (Google Fonts, SIL OFL) avec une échelle typographique à 8 niveaux — display (32px/700) à label-sm (12px/600 majuscules). Taille de police minimale de 12px imposée.
- UX-DR3 : Implémenter `AppLayoutComponent` avec une barre supérieure fixe (hauteur 56px) + barre latérale optionnelle (largeur 200px, admin uniquement, non rétractable en v1) + zone de contenu (padding 24px, max 640px pour les formulaires, illimité pour les tableaux).
- UX-DR4 : Implémenter le composant chip de phase (centre de la barre supérieure) : pill arrondie, fond primary-container, indicateur ● corail, mise à jour en temps réel via SSE avec transition de fondu 150ms. Cliquable pour l'admin (→ page de contrôle de phase), non cliquable pour le bénévole. aria-label « Phase actuelle : [phase] ».
- UX-DR5 : Implémenter le composant badge de rôle (droite de la barre supérieure) : pill arrondie, style admin (primary-container), style bénévole (surface-variant), label-sm majuscules.
- UX-DR6 : Implémenter le composant boîte de dialogue de confirmation : rounded-xl, élévation niveau 3, superposition sombre à 50%, titre + description des conséquences + bouton confirmer + bouton annuler (ghost), focus piégé, focus initial sur le bouton annuler, Échap ferme la fenêtre.
- UX-DR7 : Implémenter le composant notification inline : fond primary-container, bordure gauche de 3px corail, icône Material Symbols `warning`, apparaît sous l'élément déclencheur dans le flux (pas de toast), persiste jusqu'à résolution.
- UX-DR8 : Implémenter le composant toast : position bas-droite, succès (auto-fermeture 4s), système d'erreur (persistant jusqu'à interaction), max 1 toast simultané.
- UX-DR9 : Implémenter le composant de navigation latérale (admin uniquement) : fond sombre sidebar-bg `#2A100A`, navigation plate (sans sous-menus), sections séparées par des labels label-sm en majuscules (« Édition active » / « Gestion »), élément actif déterminé par la route courante (fond corail primaire + texte blanc), icônes Material Symbols 18px.
- UX-DR10 : Implémenter le composant de saisie scanner : auto-focus à l'ouverture de la caisse, re-focus automatique après 500ms d'inactivité clavier, correspondance de codes de touches AZERTY/QWERTY, Entrée/`\n` déclenche le traitement, pas de debounce, aria-label « Scanner ou saisir un code-barres », aria-live="polite" sur la zone de résultat du scan.
- UX-DR11 : Implémenter le pattern liste filtrable/triable avec `MatPaginator` (taille de page par défaut 50), tri par clic sur l'en-tête de colonne (indicateur ↑↓), filtres inline au-dessus de la liste. Utilisé par le catalogue articles et la liste vendeurs.
- UX-DR12 : Implémenter l'état de chargement squelette (3–5 lignes squelettes Angular Material) pour les listes lors du chargement initial des données. Pas de spinner global.
- UX-DR13 : Implémenter le composant état vide : icône Material Symbol centrée + phrase descriptive + bouton d'action primaire. Propose toujours une sortie. Utilisé par la liste vendeurs, le catalogue, les résultats filtrés vides (avec action « Effacer les filtres »).
- UX-DR14 : Implémenter le composant panier POS : liste d'articles avec nom + prix unitaire, bouton de suppression individuel (icône fermer par ligne), regroupement par lot (en-tête de lot en rouge avec compteur « X/N scannés » + sous-total du lot, sans prix individuel par article), bouton « Retirer le lot entier » depuis le premier article du lot, notification inline avertissement si lot incomplet (le bouton « Valider » reste actif), panier auto-vidé sur événement SSE basket-cancelled.
- UX-DR15 : Implémenter le flux formulaire de dépôt (bénévole) : recherche vendeur par nom/email → « Créer un profil » si introuvable → enregistrement d'article (nom, prix, sélecteur de catégorie, case à cocher complet/incomplet + champ commentaire) avec affichage de la table auto-assignée. Autofocus sur le champ de recherche vendeur au chargement de la page.
- UX-DR16 : Implémenter le composant admin catégories & tables : mode éditable avant le démarrage de la phase Dépôt, lecture seule après. Sur nouvelle édition : option « Copier depuis une édition clôturée » (liste déroulante de sélection d'édition clôturée) ou « Configurer manuellement ».
- UX-DR17 : Implémenter la page rapports admin avec des sections de contenu conditionnelles selon la phase : section bilan journalier (phase Vente uniquement, bouton actualiser), section synthèse (Post-vente + Clôturée, lecture seule), boutons d'export CSV (catalogue + reversements, Post-vente + Clôturée, téléchargement direct sans boîte de dialogue). La liste des vendeurs non soldés est accessible via la page de solde, commune aux bénévoles (`/volunteer/settlement`) et à l'admin (`/admin/settlement`) — pas de section dédiée dans les rapports. L'admin voit en plus les colonnes téléphone et email, affichées conditionnellement selon le rôle.
- UX-DR18 : Implémenter l'action « Archiver l'édition » : bouton secondaire couleur d'erreur, boîte de dialogue de confirmation irréversible (« Archiver et supprimer tous les articles de cette édition. Cette action est irréversible. »), état vide post-archivage « Édition archivée — aucun article. » sans action, bouton disparaît après l'archivage. Bouton visible uniquement si des articles existent encore.
- UX-DR19 : Implémenter le pattern de retour visuel du bouton d'impression : spinner dans le bouton pendant la soumission à la file d'attente, toast de succès (4s), toast d'erreur persistant si l'imprimante est hors ligne avec bouton « Fermer ». Toujours redéclenchable.
- UX-DR20 : Implémenter le socle d'accessibilité WCAG 2.2 AA : anneaux de focus sur tous les éléments interactifs (jamais supprimés), ordre de tabulation suivant l'ordre de lecture visuel, piège de focus dans les boîtes de dialogue de confirmation, annonces pour lecteurs d'écran via aria-live/aria-label/aria-describedby, cibles tactiles minimales de 44×44px, icônes décoratives aria-hidden="true", icônes sémantiques avec texte accompagnateur ou aria-label.
- UX-DR21 : Implémenter la gestion des transitions de phase dans l'interface POS bénévole : événement SSE `basket-cancelled` → toast persistant « La phase a changé. Votre panier a été annulé. » → panier vidé → scanner désactivé jusqu'au rechargement de la page.
- UX-DR22 : Implémenter l'impression du bilan de vente : (1) case « Imprimer le bilan » dans le formulaire de solde, **cochée par défaut**, déclenchant l'impression à la confirmation du solde (best-effort) ; (2) bouton « Imprimer le bilan » par ligne, **visible uniquement pour les vendeurs soldés ou non réclamés** (ré-impression). Retour visuel spinner + toast dans les deux cas. *(SCP 2026-09-02b)*

### Carte de couverture FR

- FR-001 : Epic 1 — Interface i18n disponible en EN/FR
- FR-002 : Epic 1 — Détection de la langue navigateur → préférence utilisateur
- FR-003 : Epic 1 — L'utilisateur peut modifier sa préférence de langue dans les paramètres
- FR-004 : Epic 1 — Tous les textes de l'interface externalisés (aucune chaîne codée en dur)
- FR-005 : Epic 1 + 2 — Langue des documents configurée par édition
- FR-006 : Epic 1 + 2 — Langue de documents par édition, initialisée depuis le paramètre instance à la création
- FR-007 : Epic 1 + 2 — Langue de documents modifiable par l'admin à tout moment
- FR-008 : Epic 2 — L'admin crée une édition avec un nom libre
- FR-009 : Epic 2 — Plusieurs éditions par année
- FR-010 : Epic 2 — Une seule édition active à la fois (active = phases Préparation → Post-vente ; Clôturée = inactive)
- FR-011 : Epic 2 — La transition de phase nécessite une boîte de dialogue de confirmation
- FR-012 : Epic 2 — Phase active affichée à tous les utilisateurs
- FR-013 : Epic 2 — La clôture de l'édition génère des PDF, l'édition passe en lecture seule
- FR-014 : Epic 2 — Une édition ayant dépassé la phase Préparation ne peut pas être supprimée
- FR-015 : Epic 2 — Données d'édition strictement isolées
- FR-016 : Epic 2 — Taux de commission figé dès le démarrage de la phase Dépôt
- FR-017 : Epic 2 — L'admin configure les catégories d'articles par édition (Story 2.5)
- FR-018 : Epic 2 — L'admin configure la correspondance catégorie-table (Story 2.5)
- FR-099 : Epic 2 — Retiré (voir Story 2.3, amendée le 2026-07-06)
- FR-100 : Epic 2 — Dates de début et de fin d'édition optionnelles (Story 2.4)
- FR-019 : Epic 3 — Les profils vendeurs sont propres à chaque édition
- FR-020 : Epic 3 — Le bénévole recherche/crée des profils vendeurs
- FR-021 : Epic 3 — L'admin peut supprimer un profil vendeur (anonymisation RGPD)
- FR-022 : Epic 3 — Le bénévole saisit les détails de l'article ; commentaire optionnel disponible en tout temps (article individuel et articles de lot)
- FR-023 : Epic 3 — Table auto-assignée : même table si vendeur déjà présent dans la catégorie, sinon table la moins chargée
- FR-024 : Epic 3 — Article corrigeable/supprimable uniquement en phase Dépôt
- FR-025 : Epic 3 — Indicateur complet/incomplet et commentaire article modifiables dans toutes les phases
- FR-026 : Epic 3 — Code-barres Code 128 généré côté serveur par article (8 chiffres : 4 vendeur + 4 article dans inventaire vendeur)
- FR-027 : Epic 3 — Format d'étiquette : édition / catégorie / nom+prix / /!\ INCOMPLET si applicable / commentaire si non vide / table / code-barres (numéro XXXX-XXXX)
- FR-028 : Epic 3 — Étiquettes imprimées automatiquement à la validation du dépôt
- FR-029 : Epic 3 — Travaux d'impression mis en file d'attente séquentiellement côté serveur
- FR-030 : Epic 3 — Format du rouleau thermique : séparateur vendeur → étiquettes articles
- FR-031 : Epic 3 — Bordereau de dépôt imprimable par vendeur
- FR-032 : Epic 3 — Largeur du ticket thermique (57 mm ou 80 mm) configurable par imprimante enregistrée
- FR-033 : Epic 4 — Interface caissier avec scanner USB HID
- FR-034 : Epic 4 — Gestion transparente AZERTY/QWERTY via correspondance de codes de touches
- FR-035 : Epic 4 — Article scanné ajouté au panier avec nom et prix
- FR-036 : Epic 4 — Scan article déjà vendu : message d'erreur, non ajouté
- FR-037 : Epic 4 — Scan article incomplet : avertissement, toujours vendable
- FR-038 : Epic 4 — Le caissier peut retirer des articles du panier avant validation
- FR-039 : Epic 4 — La validation du paiement marque les articles vendus, clôt la transaction
- FR-040 : Epic 4 — Facture acheteur imprimable à la demande après validation
- FR-041 : Epic 4 — Format de la facture : liste articles, prix, total, association, édition, date
- FR-042 : Epic 4 — Minimum 3 postes caissiers simultanés sans conflits
- FR-043 : Epic 3 — Le bénévole peut créer un lot avec un prix global + plusieurs articles
- FR-044 : Epic 3 — Chaque article d'un lot a son propre nom et étiquette
- FR-045 : Epic 3 — L'étiquette d'un article de lot affiche le prix du lot et « Lot indivisible : X/N »
- FR-046 : Epic 4 — Le scan d'un article de lot affiche le nom du lot en rouge + compteur « X/N scannés »
- FR-047 : Epic 4 — Avertissement inline si lot incomplet, validation non bloquée ; un lot avec ≥1 article vendu est réputé vendu en entier
- FR-048 : Epic 4 — Lot complet vendu au prix global du lot
- FR-109 : Epic 4 — Un lot ne se vend qu'une fois ; scan d'un article d'un lot déjà vendu rejeté (409, au scan et à la validation) ; articles restants rendus au vendeur *(SCP 2026-09-02b)*
- FR-049 : Epic 5 — Bilan de vente imprimable par vendeur en phase Post-vente
- FR-050 : Epic 5 — Bilan de vente : tableau unifié des articles + statut vendu/invendu, tableau « détail des lots », ligne de comptage vendus/invendus/déposés, total brut, commission, reversement net, montant remis si soldé *(SCP 2026-09-02b)*
- FR-051 : Epic 5 — Le bénévole solde le vendeur : saisit le montant en espèces, clique Solder
- FR-052 : Epic 5 — Le bouton « Non réclamé » transfère le reversement en recettes de l'association
- FR-053 : Epic 5 — Vendeurs non soldés identifiables dans la liste de solde via un filtre dédié
- FR-097 : Epic 5 — Impression groupée des bilans depuis `/admin/settlement` (filtre actif, toutes pages, Post-vente uniquement)
- FR-054 : Epic 5 — Bilan journalier générable par l'admin en phase Vente
- FR-055 : Epic 5 — Bilan d'édition généré à la clôture de l'édition
- FR-095 : Epic 5 — Page de solde : liste de tous les vendeurs filtrable par statut (soldé / non soldé), actions par ligne (solder + non réclamé pour les non soldés ; imprimer bilan pour les soldés / non réclamés uniquement), case « Imprimer le bilan » cochée par défaut dans le formulaire de solde ; accessible bénévoles (`/volunteer/settlement`) et admin (`/admin/settlement`) ; l'admin voit en plus téléphone et email ; composant Angular unique *(SCP 2026-09-02b)*
- FR-057 : Epic 5 — Tous les rapports générés en PDF
- FR-058 : Epic 5 — Rapports accessibles à l'admin uniquement
- FR-059 : Epic 5 — Éditions clôturées : métriques agrégées + profils vendeurs + détail articles en lecture seule jusqu'à l'Archivage ; après archivage, seules les métriques agrégées en base
- FR-060 : Epic 1 — L'admin crée/modifie/désactive les comptes bénévoles, réinitialise les mots de passe
- FR-101 : Epic 1 — Invalidation de session immédiate à la désactivation/suppression d'un compte bénévole (Story 1.12)
- FR-061 : Epic 1 — Un seul compte admin par instance
- FR-062 : Epic 1 — Premier lancement : identifiants Admin/Admin, changement de mot de passe forcé
- FR-063 : Epic 1 — Réinitialisation du mot de passe admin via commande CLI serveur
- FR-064 : Epic 1 — Rôles Admin/Bénévole strictement séparés
- FR-065 : Epic 1 — Interface bénévole adaptée à la phase active
- FR-066 : Epic 1 — Les sessions n'expirent pas automatiquement
- FR-067 : Epic 1 — Chaque compte mémorise une préférence de langue d'interface (EN/FR)
- FR-068 : Epic 1 — Serveur fonctionnel sur Linux, macOS, Windows sans modification du code
- FR-069 : Epic 1 — Configuration minimale : Raspberry Pi 4 (2 Go de RAM)
- FR-070 : Epic 1 — Déployé via Docker Compose, données dans des volumes persistants
- FR-071 : Epic 1 — Mises à jour via `docker compose pull && docker compose up -d`
- FR-072 : Epic 1 — Postes clients accèdent via navigateur, aucune installation locale
- FR-073 : Epic 1 — Page paramètres admin : nom de l'association, taux de commission par défaut, langue des documents par défaut
- FR-074 : Epic 1 — Guide d'installation pour utilisateurs non techniques, par OS (Linux/macOS/Windows)
- FR-075 : Epic 3 — Toute l'impression acheminée via le serveur central
- FR-076 : Epic 3 — N imprimantes thermiques Bluetooth enregistrées par l'admin, une file indépendante par imprimante
- FR-077 : Epic 3 — N imprimantes A4 réseau enregistrées par l'admin, une file indépendante par imprimante
- FR-078 : Epic 3 — L'utilisateur déclenche l'impression ; aucune action requise côté client
- FR-079 : Epic 3 — Erreur d'impression : notification explicite ; diagnostic par imprimante ; vérification au démarrage
- FR-098 : Epic 3 — Sélection des imprimantes thermique + A4 par le bénévole à la connexion
- FR-080 : Epic 2 — Nouvelle édition peut copier catégories/correspondance tables depuis une édition clôturée
- FR-081 : Epic 4 — Le caissier peut retirer l'ensemble d'un lot du panier
- FR-082 : Epic 2 — L'admin peut revenir en arrière d'une phase à la fois, données préservées
- FR-083 : Epic 6 — Catalogue articles filtrable/triable accessible durant toutes les phases
- FR-084 : Epic 6 — Filtres du catalogue : nom, code-barres, catégorie, table, vendu/invendu, complet/incomplet, vendeur
- FR-085 : Epic 6 — Catalogue triable par n'importe quelle colonne visible
- FR-086 : Epic 6 — Catalogue affiche l'édition active uniquement ; indisponible après Archivage de l'édition
- FR-088 : Epic 2 — « Archivage de l'édition » archive chaque article (nom, catégorie, statut, prix effectif, + référence de lot pour les membres de lot) puis supprime les enregistrements (code-barres, table, vendeur non conservés) ; désactive le retour arrière vers Post-vente
- FR-096 : Epic 2 — À la clôture, vendeurs non soldés auto-marqués Non réclamé (atomique avec la phase) ; dialog de confirmation enrichie si vendeurs non soldés
- FR-089 : Epic 3 — La commission s'applique normalement aux articles vendus avec l'indicateur incomplet
- FR-090 : Epic 4 — Transition de phase avec panier actif : panier annulé, message explicite au bénévole
- FR-091 : Epic 5 — Export CSV du catalogue articles (Post-vente + Clôturée, admin uniquement, téléchargement direct) — addendum
- FR-092 : Epic 5 — Export CSV des reversements (Post-vente + Clôturée, admin uniquement, téléchargement direct) — addendum
- FR-093 : Epic 4 — Moyen de paiement enregistré à la validation (espèces, chèque, carte)
- FR-094 : Epic 5 — Ventilation des recettes par moyen de paiement dans les bilans journalier et d'édition — addendum
- FR-102 : Epic 6 — Consultation du catalogue archivé d'une édition passée (admin uniquement, dépend de la Story 2.7) : nom, catégorie, statut, prix (marqueur « (lot) » pour les membres de lot) — addendum 2026-07-29, prix + marqueur lot 2026-09-02

## Liste des épics

### Epic 1 : Fondation applicative, Authentification & i18n
Les administrateurs et les bénévoles peuvent déployer l'application, se connecter avec les rôles appropriés, gérer les comptes utilisateurs, configurer l'instance et utiliser l'application dans leur langue préférée (EN/FR). Tous les composants partagés et le système de design Angular Material sont en place. Un guide d'installation complet permet à un utilisateur non technique de déployer l'instance sans assistance.

**FR couvertes :** FR-001–007, FR-060–067, FR-068–074
**Architecture :** ARCH-001, ARCH-002, ARCH-006, ARCH-007, ARCH-011, ARCH-013, ARCH-014
**UX :** UX-DR1, UX-DR2, UX-DR3, UX-DR5, UX-DR6, UX-DR7, UX-DR8, UX-DR9, UX-DR12, UX-DR13, UX-DR20

### Epic 2 : Gestion du cycle de vie des éditions
Les administrateurs peuvent créer des éditions, piloter l'intégralité du cycle de phases (Préparation → Dépôt → Vente → Post-vente → Clôturée), effectuer des retours arrière de phases, et clôturer/archiver les éditions. Tous les utilisateurs connectés voient la phase active en temps réel via SSE.

**FR couvertes :** FR-008–018, FR-080, FR-082, FR-088, FR-090 (côté serveur), FR-096, FR-099, FR-100
**Architecture :** ARCH-012, ARCH-015 (prérequis machine de phases)
**UX :** UX-DR4, UX-DR18

### Epic 3 : Enregistrement des vendeurs & Dépôt
Les bénévoles peuvent enregistrer les vendeurs et tous leurs articles (y compris les lots) avec assignation automatique de table, et imprimer les étiquettes et bordereaux de dépôt via l'imprimante thermique centralisée.

**FR couvertes :** FR-019–032, FR-043–045, FR-075–079, FR-089, FR-098
**Architecture :** ARCH-003, ARCH-008, ARCH-009, ARCH-010, ARCH-015 (prérequis file d'impression), ARCH-016
**UX :** UX-DR15, UX-DR16, UX-DR19, UX-DR22

### Epic 4 : Point de vente
Les bénévoles peuvent scanner des articles avec un scanner code-barres USB, gérer les paniers avec prise en charge complète des lots, finaliser les ventes et imprimer les factures acheteurs — en toute sécurité sur plusieurs postes simultanés.

**FR couvertes :** FR-033–042, FR-046–048, FR-081, FR-090 (côté client), FR-093
**Architecture :** ARCH-003 (validation concurrence), ARCH-004
**UX :** UX-DR10, UX-DR14, UX-DR21

### Epic 5 : Post-vente, Reversements & Rapports
Les bénévoles peuvent solder les vendeurs et traiter les reversements. Les administrateurs peuvent générer des rapports de bilan journaliers et d'édition en PDF, identifier les vendeurs non soldés et clôturer officiellement les éditions.

**FR couvertes :** FR-049–055, FR-057–059, FR-091, FR-092, FR-094, FR-095, FR-097
**UX :** UX-DR17, UX-DR22

### Epic 6 : Catalogue articles
Les administrateurs et les bénévoles peuvent parcourir, rechercher et filtrer tous les articles de l'édition active dans toutes les phases.

**FR couvertes :** FR-083–086
**Architecture :** ARCH-005
**UX :** UX-DR11

---

## Epic 1 : Fondation applicative, Authentification & i18n

Les administrateurs et les bénévoles peuvent déployer l'application, se connecter avec les rôles appropriés, gérer les comptes utilisateurs, configurer l'instance et utiliser l'application dans leur langue préférée (EN/FR). Tous les composants partagés et le système de design Angular Material sont en place.

### Story 1.1 : Mise en place du squelette de projet & baseline Docker Compose

En tant que développeur,
je veux que la pile technologique complète soit initialisée avec Docker Compose et un environnement de développement fonctionnel,
afin que le développement des fonctionnalités puisse démarrer sur une base stable et reproductible.

**Critères d'acceptation :**

**Étant donné** que le dépôt est cloné
**Quand** `docker compose up -d` est exécuté
**Alors** l'application Spring Boot démarre et répond sur `/actuator/health`
**Et** le serveur de développement Angular démarre sur `http://localhost:4200`
**Et** le conteneur MariaDB tourne avec un volume persistant

**Étant donné** que l'application Spring Boot démarre
**Quand** les migrations Liquibase s'exécutent
**Alors** la table `users` existe avec tous les champs, y compris `preferred_language` et la FK nullable `seller_profile_id`
**Et** les tables Spring Session JDBC existent (changeset 002)
**Et** les tables `categories` et `table_assignments` existent (changeset 003)
**Et** la table `instance_config` existe (changeset 004)
**Et** un compte administrateur par défaut (username : « Admin », hash BCrypt de « Admin ») est initialisé

**Étant donné** que l'application retourne une erreur
**Quand** un endpoint produit un code 4xx ou 5xx
**Alors** la réponse suit le format RFC 7807 Problem Details (`type`, `title`, `status`, `detail`, `instance`)

**Étant donné** que le profil Spring `dev` est actif
**Quand** `/swagger-ui.html` est accédé
**Alors** l'interface Springdoc OpenAPI est disponible

**Étant donné** que le profil Spring `prod` est actif
**Quand** `/swagger-ui.html` est accédé
**Alors** un 404 est retourné

### Story 1.2 : Authentification Spring Security & Contrôle d'accès basé sur les rôles

En tant qu'administrateur,
je veux me connecter avec mes identifiants et bénéficier d'un accès aux pages admin restreint par rôle,
afin que l'application soit sécurisée et que les interfaces admin/bénévole soient strictement séparées dès le départ.

**Critères d'acceptation :**

**Étant donné** que l'application vient d'être déployée
**Quand** un utilisateur quelconque accède à une route protégée
**Alors** il est redirigé vers `/login`

**Étant donné** que l'admin soumet « Admin » / « Admin » à la première connexion
**Quand** l'authentification réussit
**Alors** le système redirige immédiatement vers une page de changement de mot de passe obligatoire
**Et** l'accès à toutes les autres pages est bloqué jusqu'au changement de mot de passe
**Et** la session est stockée dans la table MariaDB `spring_session`

**Étant donné** qu'une session est établie
**Quand** le conteneur serveur est redémarré
**Alors** la session survit et l'utilisateur reste connecté (FR-066)

**Étant donné** qu'un BÉNÉVOLE tente d'accéder à `/admin/*`
**Quand** la requête est traitée
**Alors** un 403 est retourné

**Étant donné** toute requête d'un utilisateur avec le rôle SELLER
**Quand** traitée par Spring Security
**Alors** un 403 est retourné quel que soit l'endpoint

**Étant donné** qu'un admin se déconnecte
**Quand** `/logout` est appelé
**Alors** la session est invalidée en base de données

### Story 1.3 : Gestion des comptes bénévoles

En tant qu'administrateur,
je veux créer, modifier, désactiver les comptes bénévoles et réinitialiser leurs mots de passe,
afin de contrôler qui a accès à l'application pendant l'événement.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers `/admin/users`
**Quand** la page se charge
**Alors** tous les comptes bénévoles sont listés avec nom, statut (actif/inactif) et badge de rôle

**Étant donné** que l'admin renseigne prénom, nom, identifiant et mot de passe pour un nouveau bénévole
**Quand** le formulaire est soumis
**Alors** un compte BÉNÉVOLE est créé et le bénévole peut se connecter immédiatement

**Étant donné** que l'admin réinitialise le mot de passe d'un bénévole
**Quand** la réinitialisation est soumise
**Alors** le mot de passe du bénévole est mis à jour
**Et** le bénévole est forcé de le changer à la prochaine connexion

**Étant donné** que l'admin désactive un compte bénévole
**Quand** ce bénévole tente de se connecter
**Alors** la connexion est refusée avec un message clair « Compte désactivé »

**Étant donné** qu'un compte admin existe déjà
**Quand** l'admin tente de créer un second compte admin
**Alors** le système le refuse avec une erreur explicite (FR-061 : un seul admin par instance)

### Story 1.4 : Récupération du mot de passe admin via CLI

En tant qu'administrateur ayant oublié son mot de passe,
je veux le réinitialiser via une commande côté serveur,
afin de récupérer l'accès sans intervention de développeur ni manipulation directe de la base de données.

**Critères d'acceptation :**

**Étant donné** que l'admin a oublié son mot de passe
**Quand** il lance l'application avec l'argument `--reset-admin-password`
**Alors** un nouveau mot de passe temporaire (12+ caractères, alphanumérique) est affiché dans la console
**Et** le mot de passe du compte admin est mis à jour en base de données (BCrypt)
**Et** un indicateur de changement de mot de passe forcé est positionné sur le compte

**Étant donné** que le mot de passe temporaire a été généré
**Quand** l'admin se connecte avec ce mot de passe
**Alors** il est immédiatement redirigé vers la page de changement de mot de passe obligatoire
**Et** il ne peut accéder à aucune autre page tant que le mot de passe n'a pas été changé

### Story 1.5 : Configuration de l'instance & Page de paramètres admin

En tant qu'administrateur,
je veux configurer les paramètres de l'instance dans une page dédiée,
afin que l'application reflète l'identité et les paramètres opérationnels de mon association.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers `/admin/settings`
**Quand** la page se charge
**Alors** la configuration courante est affichée : nom de l'association, taux de commission par défaut (défaut 20 %), langue des documents par défaut (EN/FR)

**Étant donné** que l'admin met à jour le nom de l'association et sauvegarde
**Quand** le serveur redémarre
**Alors** le nom de l'association est préservé (persisté en base de données)

**Étant donné** que l'admin définit le taux de commission à 15 % et sauvegarde
**Quand** la valeur est stockée
**Alors** la valeur stockée est un BigDecimal `15.00` (ni float ni double)

**Étant donné** que l'admin définit la langue des documents par défaut sur « FR »
**Quand** la valeur est sauvegardée
**Alors** toute nouvelle édition créée ultérieurement hérite de la langue « FR » (FR-006)
**Et** les éditions existantes conservent leur propre valeur inchangée

### Story 1.6 : Préférence de langue utilisateur & Infrastructure i18n

En tant qu'utilisateur,
je veux que l'application s'affiche dans ma langue préférée (anglais ou français),
afin de travailler confortablement dans ma langue maternelle pendant l'événement.

**Critères d'acceptation :**

**Étant donné** qu'un nouvel utilisateur se connecte pour la première fois avec la langue navigateur `fr`
**Quand** la connexion se termine
**Alors** l'interface s'affiche en français
**Et** `preferredLanguage: FR` est enregistré sur son compte utilisateur

**Étant donné** que le navigateur d'un nouvel utilisateur est configuré en anglais ou dans une langue non prise en charge
**Quand** il se connecte pour la première fois
**Alors** l'interface s'affiche en anglais et `preferredLanguage: EN` est enregistré

**Étant donné** qu'un utilisateur connecté accède à `/account` et sélectionne l'autre langue
**Quand** il sauvegarde la préférence
**Alors** l'interface bascule immédiatement dans la langue sélectionnée (sans rechargement de page)
**Et** la préférence survit à la déconnexion et à la reconnexion

**Étant donné** que n'importe quelle page s'affiche
**Quand** un texte visible est inspecté
**Alors** tout le texte provient de clés de traduction `en.json` ou `fr.json` — aucune chaîne codée en dur (FR-004)
**Et** les clés i18n suivent le format `feature.section.key` (3 niveaux maximum)

**Étant donné** qu'un document PDF est généré
**Quand** la langue des documents de l'édition active est « FR »
**Alors** tous les textes du document utilisent les entrées de `messages_fr.properties`

**Note de développement :** Toutes les chaînes françaises (`fr.json` et `messages_fr.properties`) utilisent le **vouvoiement systématique**. Aucun tutoiement, même informel. Voir EXPERIENCE.md § Voice and Tone pour les exemples.

### Story 1.7 : Système de design Angular Material & Mise en page applicative

En tant qu'utilisateur naviguant dans l'application,
je veux un design visuel cohérent et adapté à mon rôle avec une navigation claire,
afin de trouver instantanément ce dont j'ai besoin sous la pression d'une journée d'événement.

**Critères d'acceptation :**

**Étant donné** qu'un utilisateur authentifié charge n'importe quelle page
**Quand** la page s'affiche
**Alors** la barre supérieure (56px) est visible avec le logo à gauche, le badge de rôle en haut à droite, et le chip de phase au centre (statique à ce stade)
**Et** la police DM Sans et le corail primaire `#C44626` sont appliqués de manière cohérente

**Étant donné** qu'un admin est connecté
**Quand** une page admin se charge
**Alors** la barre latérale (200px, fond `#2A100A`) est affichée avec les sections « Édition active » / « Gestion » et des liens de navigation plats
**Et** la route active courante est mise en évidence en corail (fond `#C44626`, texte blanc)

**Étant donné** qu'un bénévole est connecté
**Quand** une page bénévole se charge
**Alors** aucune barre latérale n'est affichée

**Étant donné** qu'un bouton est rendu en tant qu'action principale
**Quand** le bouton apparaît
**Alors** il utilise le style rempli corail
**Et** au plus un bouton primaire (rempli corail) apparaît par section visible

**Étant donné** que le thème Angular Material est appliqué
**Quand** rendu dans Chrome, Firefox, Edge ou Safari sur Linux/macOS/Windows
**Alors** les couleurs, la typographie, l'élévation et les coins arrondis correspondent aux spécifications des tokens du fichier DESIGN.md

### Story 1.8 : Composants UI partagés — Boîtes de dialogue, Notifications & Accessibilité

En tant qu'utilisateur effectuant des opérations dans l'application,
je veux des retours clairs, des confirmations accessibles et des états vides utiles,
afin d'agir en confiance sans faire d'erreurs accidentelles sous pression.

**Critères d'acceptation :**

**Étant donné** qu'une action irréversible est déclenchée
**Quand** la boîte de dialogue de confirmation apparaît
**Alors** elle affiche un titre, une description des conséquences, un bouton de confirmation et un bouton annuler (ghost)
**Et** le focus est piégé à l'intérieur de la boîte de dialogue
**Et** le focus initial est sur le bouton annuler
**Et** appuyer sur Échap ferme la boîte de dialogue sans agir

**Étant donné** qu'une opération réussie se termine
**Quand** le résultat est retourné
**Alors** un toast de succès apparaît en bas à droite pendant 4 secondes puis disparaît automatiquement
**Et** au plus un toast est visible à tout moment

**Étant donné** qu'une erreur système survient (imprimante hors ligne, panne réseau)
**Quand** elle est remontée à l'utilisateur
**Alors** un toast d'erreur persistant apparaît en bas à droite avec un bouton « Fermer » qui doit être cliqué pour le fermer

**Étant donné** qu'une erreur métier survient de manière inline dans un flux de travail
**Quand** l'erreur est déclenchée
**Alors** une notification inline apparaît directement sous l'élément déclencheur (pas un toast)
**Et** elle persiste jusqu'à ce que l'erreur soit résolue ou qu'une nouvelle action soit entreprise

**Étant donné** qu'une liste charge ses données initiales
**Quand** la requête API est en cours
**Alors** 3 à 5 lignes squelettes sont affichées et aucun spinner global ne bloque l'interface

**Étant donné** qu'une liste ne contient aucun élément
**Quand** l'état vide s'affiche
**Alors** une icône Material centrée, un message descriptif et un bouton d'action primaire (le cas échéant) sont affichés

**Étant donné** qu'un élément reçoit le focus via la touche Tab
**Quand** le focus se pose sur un bouton, un lien ou un champ de saisie
**Alors** un anneau de focus visible (corail primaire, jamais supprimé) est affiché
**Et** tous les éléments interactifs ont une cible tactile minimale de 44×44px
**Et** les icônes décoratives ont `aria-hidden="true"`
**Et** les icônes sémantiques ont un `aria-label` ou un libellé textuel visible

### Story 1.9 : Guide d'installation

En tant que responsable d'association non technique,
je veux un guide d'installation clair et complet,
afin de pouvoir déployer et configurer PluriBourse seul, sans connaissances préalables de Docker ou du terminal.

**Dépendances :** Stories 1.1 (Docker Compose), 1.4 (commande CLI reset mdp), 1.5 (paramètres instance)

**Critères d'acceptation :**

**Étant donné** que le dépôt est cloné
**Quand** le fichier `GUIDE_INSTALLATION.md` est ouvert
**Alors** il existe à la racine du dépôt
**Et** il contient exactement les 7 sections suivantes dans l'ordre : « Prérequis », « Installation de Docker », « Téléchargement et lancement », « Premier lancement », « Configuration initiale », « Réinitialisation du mot de passe admin », « Mise à jour »

**Étant donné** que la section « Installation de Docker » est lue
**Quand** le lecteur identifie son système d'exploitation
**Alors** des instructions distinctes et complètes sont présentes pour Windows (Docker Desktop), macOS (Docker Desktop) et Linux / Raspberry Pi (Docker Engine)
**Et** chaque procédure OS est autonome — elle n'oblige pas à lire les autres sections OS

**Étant donné** que la section « Téléchargement et lancement » est lue
**Quand** les instructions sont suivies
**Alors** la commande `docker compose up -d` est présente dans un bloc de code copier-coller
**Et** une étape de vérification indique comment confirmer que l'application répond (URL et message attendu dans le navigateur)

**Étant donné** que la section « Réinitialisation du mot de passe admin » est lue
**Quand** les instructions sont suivies
**Alors** la commande CLI exacte (`docker compose exec ...`) est présente dans un bloc de code
**Et** le résultat attendu dans la console est décrit
**Et** les instructions pour ouvrir un terminal sont fournies pour chaque OS

**Étant donné** que la section « Premier lancement » est lue
**Quand** les instructions sont suivies
**Alors** l'URL d'accès (`http://localhost:8080` ou le port configuré) et les identifiants par défaut (`Admin` / `Admin`) sont indiqués
**Et** une étape de vérification confirme que le changement de mot de passe obligatoire a bien été effectué avant de poursuivre

**Étant donné** que la section « Mise à jour » est lue
**Quand** les instructions sont suivies
**Alors** la commande exacte `docker compose pull && docker compose up -d` est présente dans un bloc de code
**Et** le guide confirme explicitement que les données de l'association sont préservées après la mise à jour (FR-071)

**Étant donné** que le guide est rédigé en français
**Alors** toutes les formulations adressées au lecteur utilisent le vouvoiement (vous, votre)

**Étant donné** que n'importe quelle section technique du guide est lue
**Quand** un terme technique est mentionné pour la première fois (terminal, conteneur, volume, port)
**Alors** ce terme est défini ou accompagné d'une explication en langage naturel

**Étant donné** qu'une personne n'ayant jamais utilisé Docker suit le guide de A à Z
**Quand** elle atteint la fin de la section « Configuration initiale »
**Alors** l'application est déployée, le mot de passe admin a été changé, et les paramètres de l'instance sont configurés — sans étape nécessitant une connaissance Docker préalable

### Story 1.10 : Améliorations UX des mots de passe

En tant qu'utilisateur gérant les mots de passe,
je veux une confirmation lors du changement de mot de passe et une popup lors de la réinitialisation,
afin d'éviter les erreurs de saisie et d'avoir une interface cohérente avec les autres actions destructives.

**Dépendances :** Story 1.2 (auth), Story 1.3 (gestion bénévoles), Story 1.8 (CDK Dialog)

**Critères d'acceptation :**

**Étant donné** que l'utilisateur saisit un nouveau mot de passe sur `/change-password`
**Quand** il remplit le formulaire
**Alors** un second champ « Confirmer le mot de passe » est présent
**Et** le bouton de soumission reste désactivé si les deux champs ne correspondent pas
**Et** un message d'erreur inline indique que les mots de passe ne correspondent pas

**Étant donné** que l'admin clique sur « Réinitialiser le mot de passe » dans la liste des bénévoles
**Quand** le clic est effectué
**Alors** une boîte de dialogue (CDK Dialog) s'ouvre avec le nom du bénévole, un champ mot de passe et les boutons Confirmer / Annuler
**Et** le champ mot de passe applique les mêmes règles de validation que le formulaire de changement de mot de passe

**Étant donné** que l'admin confirme la réinitialisation dans la popup
**Quand** la demande API réussit
**Then** la popup se ferme et un toast de succès s'affiche

**Étant donné** que l'admin annule ou ferme la popup
**Quand** la fermeture est déclenchée (bouton Annuler ou Échap)
**Alors** aucune action API n'est effectuée

### Story 1.11 : Dialogs mutualisés pour la gestion des éditions et des bénévoles

En tant qu'administrateur,
je veux créer/modifier une édition, gérer ses phases, gérer ses catégories et ajouter un bénévole depuis des boîtes de dialogue plutôt que des pages dédiées,
afin de rester dans le contexte de la liste en cours et de bénéficier d'une fermeture cohérente (croix, Annuler, Echap) sur toutes ces actions courtes.

**Dépendances :** Story 1.8 (CDK Dialog, `ConfirmDialogComponent`), Story 1.10 (`ResetPasswordDialogComponent`), Story 2.1 (CRUD édition), Story 2.2 (contrôle de phase), Story 2.5 (catégories & tables)

**Critères d'acceptation :**

**Étant donné** que l'admin est sur `/admin/editions`
**Quand** il clique sur « Créer une édition »
**Alors** un dialog s'ouvre (via `DialogShellComponent`) avec le formulaire de création, sans changement d'URL

**Étant donné** que l'admin clique sur « Modifier » sur une ligne d'édition
**Quand** le dialog s'ouvre
**Alors** le formulaire est pré-rempli avec les données de l'édition et le titre du dialog affiche son nom

**Étant donné** que l'admin clique sur « Gérer les phases » sur une ligne d'édition
**Quand** le dialog s'ouvre
**Alors** le contrôle de phase (phase actuelle, boutons d'avancement/retour) s'affiche dans le dialog
**Et** les dialogs de confirmation existants (avancer/reculer de phase) continuent de fonctionner par-dessus ce dialog

**Étant donné** que l'admin clique sur « Gérer les catégories » sur une ligne d'édition
**Quand** le dialog s'ouvre
**Alors** le tableau de catégories (ajout/suppression de lignes) s'affiche dans le dialog
**Et** le corps du dialog défile verticalement si le contenu dépasse la hauteur du viewport, tandis que le titre et la croix de fermeture restent fixes

**Étant donné** que l'admin est sur `/admin/users`
**Quand** il clique sur « Créer un utilisateur »
**Alors** un dialog s'ouvre avec le formulaire d'ajout de bénévole, sans changement d'URL

**Étant donné** que n'importe lequel de ces 4 dialogs (ou les 2 dialogs existants — confirmation, réinitialisation de mot de passe) est ouvert
**Quand** l'admin clique sur la croix de fermeture en haut à droite, sur Annuler, ou appuie sur Échap
**Alors** le dialog se ferme sans effectuer l'action
**Et** le focus revient sur l'élément qui a déclenché son ouverture

**Étant donné** que les routes `editions/create`, `editions/:id/edit`, `editions/:id/phase`, `editions/:id/categories` et `users/create` existaient précédemment dans `admin.routes.ts`
**Quand** cette story est complète
**Alors** ces routes n'existent plus — les fonctionnalités correspondantes ne sont accessibles que par dialog depuis la liste parente

**Étant donné** que `ConfirmDialogComponent` et `ResetPasswordDialogComponent` existent déjà (Stories 1.8, 1.10)
**Quand** cette story est complète
**Alors** les deux utilisent `DialogShellComponent` comme conteneur commun, avec une croix de fermeture fonctionnellement équivalente à Annuler/Échap

### Story 1.12 : Déconnexion automatique des bénévoles désactivés ou supprimés

En tant qu'administrateur,
je veux qu'un bénévole désactivé ou supprimé perde immédiatement l'accès à l'application s'il est déjà connecté,
afin qu'un compte révoqué ne conserve pas un accès fonctionnel pendant la durée résiduelle de sa session (jusqu'à 1h).

**Contexte :** analyse menée le 2026-07-06 (voir `sprint-change-proposal-2026-07-06.md`) — contrairement à la suppression/fermeture d'une édition (où toute action métier échoue déjà côté serveur quelle que soit la session), rien ne revérifie aujourd'hui qu'un utilisateur est toujours actif une fois sa session ouverte : `PluriBourseUserDetails` est mis en cache dans la session à la connexion et n'est jamais rafraîchi depuis la base tant que la session vit.

**Critères d'acceptation :**

**Étant donné** qu'un bénévole a une session ouverte
**Quand** l'administrateur désactive son compte (`PUT /admin/users/{id}/disable`)
**Alors** la session de ce bénévole est invalidée côté serveur immédiatement
**Et** sa prochaine requête authentifiée échoue avec 401, sans attendre l'expiration naturelle de la session

**Étant donné** qu'un bénévole a une session ouverte
**Quand** l'administrateur supprime son compte (`DELETE /admin/users/{id}`)
**Alors** la session de ce bénévole est invalidée côté serveur immédiatement, selon le même mécanisme

**Étant donné** qu'un bénévole désactivé est ensuite réactivé par l'administrateur
**Quand** il tente de se reconnecter
**Alors** il doit ressaisir ses identifiants — sa session précédente n'est pas restaurée (FR-101)

**Étant donné** qu'un administrateur a une session ouverte
**Quand** cette story est complète
**Alors** rien ne change pour les comptes administrateurs — seule la désactivation/suppression d'un compte bénévole déclenche l'invalidation (un seul compte admin existe par instance, FR-061)

---

## Epic 2 : Gestion du cycle de vie des éditions

Les administrateurs peuvent créer des éditions, piloter l'intégralité du cycle de phases (Préparation → Dépôt → Vente → Post-vente → Clôturée), effectuer des retours arrière de phases, et clôturer/archiver les éditions. Tous les utilisateurs connectés voient la phase active en temps réel via SSE.

### Story 2.1 : CRUD d'édition & Configuration du taux de commission

En tant qu'administrateur,
je veux créer et gérer des éditions avec un nom libre et un taux de commission configurable,
afin que chaque événement soit correctement identifié et configuré financièrement avant l'arrivée des vendeurs.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers `/admin/editions`
**Quand** la page se charge
**Alors** toutes les éditions sont listées avec nom, date de création et phase courante

**Étant donné** que l'admin renseigne un nom d'édition et soumet
**Quand** le formulaire est soumis
**Alors** une nouvelle édition est créée avec la phase « Préparation », un taux de commission initialisé depuis le paramètre instance (20 % par défaut) et une langue de documents initialisée depuis le paramètre instance (EN par défaut)

**Étant donné** qu'une édition existe (quelle que soit sa phase)
**Quand** l'admin modifie la langue de documents de l'édition (ex. « FR »)
**Alors** la nouvelle valeur est enregistrée sur l'édition
**Et** les documents imprimés ultérieurement pour cette édition utilisent cette langue (FR-005, FR-006, FR-007)

**Étant donné** qu'aucune édition active n'existe
**Quand** l'admin crée une nouvelle édition
**Alors** cette édition est créée en phase « Préparation » et devient l'édition active

**Étant donné** qu'une édition est déjà en phase Préparation, Dépôt, Vente ou Post-vente
**Quand** l'admin tente de créer une nouvelle édition
**Alors** le système le refuse avec une erreur explicite (FR-010 : une seule édition active à la fois)

**Étant donné** qu'une édition est en phase « Préparation »
**Quand** l'admin change le taux de commission à 15 %
**Alors** le taux est enregistré sous forme de BigDecimal `15.00`

**Étant donné** qu'une édition est entrée en phase Dépôt
**Quand** l'admin tente de modifier le taux de commission
**Alors** le système le refuse avec une erreur explicite (FR-016 : taux figé une fois le Dépôt démarré)

**Étant donné** qu'une édition est en phase Dépôt ou ultérieure
**Quand** l'admin tente de la supprimer
**Alors** le système refuse la suppression (FR-014)

**Étant donné** que l'admin soumet le formulaire de création d'édition avec un nom vide
**Quand** la requête est traitée
**Alors** une réponse 422 est retournée au format RFC 7807 avec un message d'erreur explicite sur le champ nom

### Story 2.2 : Contrôle du cycle de phases & Boîtes de dialogue de confirmation

En tant qu'administrateur,
je veux avancer ou reculer la phase de l'édition avec une confirmation explicite,
afin que les transitions de phase soient intentionnelles et que leurs conséquences soient clairement communiquées.

**Critères d'acceptation :**

**Étant donné** que l'admin est sur `/admin/editions/:id/phase`
**Quand** la page se charge
**Alors** la phase courante est clairement affichée avec les boutons de transition disponibles vers l'avant et vers l'arrière

**Étant donné** que l'admin clique sur un bouton de transition de phase
**Quand** la boîte de dialogue de confirmation apparaît
**Alors** elle indique la phase de destination et décrit la principale conséquence
**Et** deux boutons sont affichés : confirmer (primaire) et annuler (ghost)

**Étant donné** que l'admin confirme une transition de phase vers l'avant
**Quand** la transition se termine
**Alors** la phase de l'édition est mise à jour en base de données
**Et** le chip de phase dans la barre supérieure reflète la nouvelle phase pour tous les utilisateurs

**Étant donné** que l'admin confirme la transition Préparation → Dépôt
**Quand** la transition se termine
**Alors** la phase passe à « Dépôt »
**Et** le taux de commission est gelé pour cette édition (FR-016)
**Et** les catégories et correspondances de tables passent en lecture seule (Story 2.5)

**Étant donné** que l'admin confirme un retour arrière de phase
**Quand** la transition se termine
**Alors** la phase revient d'un cran en arrière (Dépôt → Préparation, Vente → Dépôt, Post-vente → Vente, Clôturé → Post-vente)
**Et** toutes les données enregistrées dans la phase annulée sont préservées (FR-082)

**Étant donné** que l'admin confirme le retour arrière Dépôt → Préparation
**Quand** la transition se termine
**Alors** les catégories et la correspondance des tables redeviennent modifiables (FR-018)
**Et** le taux de commission redevient modifiable (FR-016)

**Étant donné** qu'une édition a été clôturée et que l'Archivage de l'édition a été déclenché
**Quand** l'admin consulte la page de contrôle des phases
**Alors** le bouton de retour arrière depuis Clôturée est absent (FR-082 : retour arrière désactivé après archivage)

**Étant donné** qu'une transition de phase se termine
**Quand** le serveur la traite
**Alors** un événement SSE `phase-changed` est diffusé avec `{editionId, newPhase, previousPhase}`

### Story 2.3 : Blocage de connexion bénévole sans édition active — RETIRÉE

**Statut : amendée le 2026-07-06** (voir `sprint-change-proposal-2026-07-06.md`).

Le blocage de connexion des bénévoles hors édition active a été retiré : l'analyse a montré
qu'il n'apportait pas de protection propre, celle-ci étant déjà assurée par la vérification
systématique de l'édition active et de sa phase à chaque requête métier (FR-015). Les bénévoles
peuvent désormais se connecter à tout moment. Voir l'historique complet dans
`2-3-blocage-benevoles-sans-edition-active.md`.

### Story 2.4 : Dates de début et de fin d'édition

En tant qu'administrateur,
je veux saisir des dates de début et de fin optionnelles pour chaque édition,
afin de disposer d'un enregistrement administratif indiquant quand l'événement a eu lieu.

**Critères d'acceptation :**

**Étant donné** que l'admin crée ou modifie une édition
**Quand** le formulaire s'affiche
**Alors** deux champs de date optionnels sont disponibles : « Date de début » et « Date de fin » (FR-100)

**Étant donné** que l'admin laisse les deux champs vides
**Quand** l'édition est sauvegardée
**Alors** l'édition est enregistrée normalement — les deux champs sont nullable (FR-100)

**Étant donné** que l'admin renseigne une ou deux dates
**Quand** l'édition est sauvegardée
**Alors** les dates sont persistées en base de données et retournées dans toutes les réponses API de l'édition (FR-100)

**Étant donné** que la liste des éditions est affichée
**Quand** les éditions sont chargées
**Alors** des colonnes « Date de début » et « Date de fin » apparaissent dans le tableau, avec la valeur ou un tiret (`—`) si non renseignée (FR-100)

**Étant donné** que les deux dates sont renseignées
**Quand** l'une ou l'autre est modifiée
**Alors** aucune validation croisée n'est appliquée — les deux champs sont indépendants (FR-100)

### Story 2.5 : Catégories de l'édition & Correspondance des tables

En tant qu'administrateur,
je veux configurer les catégories d'articles et leurs attributions de tables par édition,
afin que les articles soient automatiquement dirigés vers les bonnes tables lors du dépôt.

**Critères d'acceptation :**

**Étant donné** que l'admin ouvre la page des catégories d'une nouvelle édition `/admin/editions/:id/categories`
**Quand** la page se charge
**Alors** la liste des catégories est vide et modifiable
**Et** une option « Copier depuis une édition clôturée » est disponible avec une liste déroulante listant uniquement les éditions clôturées

**Étant donné** que l'admin sélectionne « Copier depuis une édition clôturée » et confirme
**Quand** la copie se termine
**Alors** toutes les catégories et correspondances de tables de l'édition sélectionnée sont appliquées à la nouvelle édition (FR-080)

**Étant donné** que l'admin ajoute une catégorie (ex. « Jouets ») assignée aux tables 1, 2, 3
**Quand** sauvegardée
**Alors** les articles de cette catégorie seront auto-assignés aux tables 1-3

**Étant donné** que l'admin assigne la table 5 à deux catégories distinctes (ex. « Livres » et « BD »)
**Quand** sauvegardé
**Alors** la table 5 apparaît dans la correspondance des deux catégories sans erreur de validation

**Étant donné** que l'admin tente de sauvegarder avec une catégorie sans aucune table assignée
**Quand** il clique sur « Enregistrer »
**Alors** la sauvegarde est bloquée et une erreur inline apparaît sur la ligne concernée : « Assignez au moins une table à cette catégorie » (FR-018)

**Étant donné** que l'édition est en phase Préparation
**Quand** l'admin modifie les catégories et la correspondance des tables
**Alors** les modifications sont sauvegardées immédiatement

**Étant donné** que l'édition est entrée en phase Dépôt
**Quand** l'admin ouvre la page des catégories
**Alors** la page est en lecture seule avec une bannière indiquant « Catégories verrouillées »

### Story 2.6 : Notification de phase en temps réel via SSE

En tant que bénévole,
je veux voir la phase active se mettre à jour en temps réel dans la barre supérieure sans rechargement de page,
afin de toujours savoir quelle interface utiliser sans recharger manuellement la page.

**Critères d'acceptation :**

**Étant donné** qu'un bénévole est connecté et actif
**Quand** l'admin fait passer l'édition en phase Vente
**Alors** le chip de phase dans la barre supérieure du bénévole se met à jour en moins de 2 secondes
**Et** le chip utilise une transition de fondu de 150ms

**Étant donné** que l'application Angular s'initialise
**Quand** un utilisateur se connecte
**Alors** `PhaseService` ouvre une connexion `EventSource` vers `GET /api/sse/events`
**Et** la phase courante est chargée sous forme de `Signal<Phase>` depuis un appel REST initial

**Étant donné** que la connexion SSE est interrompue
**Quand** la connectivité est rétablie
**Alors** `EventSource` se reconnecte automatiquement sans action de l'utilisateur

**Étant donné** qu'un admin effectue une transition de phase
**Quand** l'événement SSE est diffusé
**Alors** tous les clients connectés reçoivent l'événement `phase-changed`
**Et** le `SseEmitterRegistry` ferme l'émetteur après la diffusion

### Story 2.7 : Clôture de l'édition & Archivage de l'édition

⚠ Dépend de la Story 5.1 (flux de solde des vendeurs — statut Soldé/Non soldé, bouton « Non réclamé » et transfert en recettes association, FR-052/FR-096) et d'une story de génération du bilan d'édition PDF EN/FR (FR-013, cf. Epic 5) — ne peut pas être implémentée avant que ces deux capacités existent. Dépendance identifiée le 2026-07-30, non documentée à la création initiale de la story.

En tant qu'administrateur,
je veux clôturer officiellement une édition et optionnellement archiver ses enregistrements d'articles,
afin que l'édition soit correctement archivée et que le stockage puisse être libéré après l'événement.

**Critères d'acceptation :**

**Étant donné** que l'édition est en phase Post-vente et qu'au moins un vendeur est non soldé
**Quand** l'admin clique sur « Clôturer l'édition »
**Alors** la boîte de dialogue de confirmation affiche : « X vendeur(s) non soldé(s) seront automatiquement marqués Non réclamé. Montant total transféré aux recettes de l'association : Y,YY €. » (FR-096)
**Et** le bouton « Clôturer l'édition » est actif (non désactivé)

**Étant donné** que tous les vendeurs sont déjà Soldés ou Non réclamés
**Quand** l'admin clique sur « Clôturer l'édition »
**Alors** la boîte de dialogue de confirmation standard s'affiche sans message d'alerte sur les vendeurs non soldés

**Étant donné** que l'admin confirme la clôture
**Quand** la transaction s'exécute
**Alors** tous les vendeurs encore non soldés sont marqués Non réclamé et leurs montants enregistrés en recettes de l'association, de manière atomique avec la transition de phase (FR-096)
**Et** la phase de l'édition passe à « Clôturée » et devient en lecture seule
**Et** les PDF de bilan d'édition sont générés en EN et FR (FR-013)

**Étant donné** que l'édition est Clôturée et que des enregistrements d'articles existent
**Quand** l'admin consulte le détail de l'édition
**Alors** un bouton « Archiver l'édition » est visible (style secondaire couleur erreur)

**Étant donné** que l'admin clique sur « Archiver l'édition » et confirme
**Quand** l'action se termine
**Alors** chaque article de l'édition est copié dans la table d'archivage avec son nom, sa catégorie et son statut (vendu ou invendu) — les articles de lot sont archivés individuellement
**Et** tous les enregistrements d'articles de cette édition sont définitivement supprimés de la table principale
**Et** tous les profils vendeurs de cette édition sont définitivement supprimés
**Et** le bouton « Archiver l'édition » disparaît
**Et** le catalogue affiche l'état vide « Édition archivée — aucun article. »
**Et** le retour arrière depuis Clôturée est définitivement désactivé pour cette édition (FR-088)

**Étant donné** qu'une édition Clôturée a été archivée
**Quand** l'admin consulte l'édition
**Alors** les métriques agrégées (total des ventes, recettes, commission) restent visibles en lecture seule (FR-059)

### Story 2.8 : Annulation du panier lors d'une transition de phase — côté serveur

⚠ Dépend de la Story 4.2 (Gestion du panier & validation du paiement) qui crée l'entité panier POS — ne peut pas être implémentée avant, puisque le serveur doit pouvoir identifier des paniers actifs qui n'existent pas encore. Aucun lien avec la Story 2.7 (mécanismes indépendants). Dépendance identifiée le 2026-07-30, non documentée à la création initiale de la story.

En tant qu'administrateur déclenchant une transition de phase,
je veux que le serveur invalide automatiquement les paniers POS actifs et notifie les clients via SSE,
afin que les bénévoles en caisse ne puissent pas finaliser des ventes dans une phase qui n'est plus valide.

**Critères d'acceptation :**

**Étant donné** qu'un bénévole a un panier actif sur la page caissier
**Quand** l'admin fait transiter la phase de l'édition
**Alors** le serveur identifie tous les paniers actifs et envoie l'événement SSE `basket-cancelled` aux clients concernés (FR-090)

**Étant donné** qu'un bénévole n'a pas de panier actif
**Quand** une transition de phase se produit
**Alors** aucun événement `basket-cancelled` ne lui est envoyé — seul `phase-changed` est diffusé

**Étant donné** qu'une transition de phase est déclenchée
**Quand** le `SseEmitterRegistry` diffuse `basket-cancelled`
**Alors** le payload contient l'`editionId` et la nouvelle phase

**Note de développement :** La gestion côté Angular du composant POS (toast persistant, vidage du panier, désactivation du scanner) est implémentée dans Story 4.6.

---

## Epic 3 : Enregistrement des vendeurs & Dépôt

Les bénévoles peuvent enregistrer les vendeurs et tous leurs articles (y compris les lots) avec assignation automatique de table, et imprimer les étiquettes et bordereaux de dépôt via l'imprimante thermique centralisée.

### Story 3.1 : Gestion des profils vendeurs

En tant que bénévole,
je veux rechercher des vendeurs existants et enregistrer de nouveaux profils vendeurs,
afin que les vendeurs puissent être associés à leurs articles sans ressaisir leurs informations à chaque édition.

**Critères d'acceptation :**

**Étant donné** que le bénévole est sur la page de dépôt `/volunteer/deposit`
**Quand** la page se charge
**Alors** le champ de recherche vendeur reçoit le focus automatiquement

**Étant donné** que le bénévole saisit un nom ou un e-mail
**Quand** des caractères sont saisis
**Alors** les profils vendeurs correspondants apparaissent en temps réel

**Étant donné** qu'aucun vendeur correspondant n'est trouvé
**Quand** le bénévole voit le résultat vide
**Alors** un bouton « Créer un nouveau profil » est affiché

**Étant donné** que le bénévole renseigne nom, prénom, e-mail et téléphone
**Quand** le formulaire est soumis
**Alors** un nouveau profil vendeur est créé et immédiatement sélectionnable pour l'enregistrement d'articles

**Étant donné** que le bénévole soumet le formulaire de création vendeur avec un e-mail au format invalide
**Quand** la requête est traitée
**Alors** une réponse 422 est retournée au format RFC 7807 avec un message d'erreur sur le champ e-mail

**Étant donné** que le bénévole soumet le formulaire avec un champ obligatoire vide (nom, prénom, e-mail ou téléphone)
**Quand** la requête est traitée
**Alors** une réponse 422 est retournée avec un message d'erreur identifiant le champ manquant (FR-019)

**Étant donné** que l'admin supprime un vendeur en phase de Dépôt, qu'aucun article n'est enregistré pour lui dans cette édition, et qu'il confirme
**Quand** la suppression se termine
**Alors** le profil vendeur est définitivement supprimé de cette édition (FR-021)
**Et** aucune donnée personnelle n'apparaît dans les logs applicatifs

> **Amendement 2026-08-19** — Cette AC décrivait initialement une suppression en cascade (« le profil vendeur et tous ses articles sont définitivement supprimés »), impossible à implémenter tant que l'entité `Item` n'existait pas (Story 3.2). La Story 3.2 (voir plus bas) a tranché explicitement dans l'autre sens : la suppression est **refusée** tant qu'il reste au moins un article enregistré pour ce vendeur, plutôt que de les supprimer en cascade — cohérent avec le principe déjà appliqué à la suppression d'édition. Le comportement livré (`SellerProfile.canBeDeleted()` / `SellerService.delete()`) suit cette version ; le texte ci-dessus a été aligné en conséquence lors d'un audit de la dette différée. Le service refuse aussi la suppression si un solde (`Settlement`, Story 5.1) existe déjà pour ce vendeur, pour ne jamais effacer un enregistrement financier.

### Story 3.2 : Enregistrement d'articles & Assignation automatique de table

En tant que bénévole,
je veux enregistrer des articles pour un vendeur avec assignation automatique de table,
afin que les articles soient correctement catalogués et localisés physiquement pendant l'événement.

**Critères d'acceptation :**

**Étant donné** qu'un vendeur est sélectionné et que le bénévole saisit un article dans une catégorie où ce vendeur a déjà des articles pour cette édition
**Quand** l'article est sauvegardé
**Alors** la même table que ses articles existants dans cette catégorie lui est assignée (FR-023)
**Et** le numéro de table assigné est affiché immédiatement

**Étant donné** qu'un vendeur est sélectionné et que le bénévole saisit un premier article dans une catégorie pour cette édition
**Quand** l'article est sauvegardé
**Alors** la table ayant le moins d'articles toutes catégories confondues parmi celles configurées pour cette catégorie lui est assignée (FR-023)
**Et** le numéro de table assigné est affiché immédiatement

**Étant donné** que le bénévole saisit un article
**Quand** le formulaire est affiché
**Alors** un champ commentaire optionnel est disponible indépendamment de l'état de la case complet/incomplet (FR-022)

**Étant donné** que le bénévole coche « Incomplet » pour un article
**Quand** l'article est sauvegardé
**Alors** l'indicateur d'incomplétude est stocké avec l'article

**Étant donné** qu'un article est enregistré en phase Dépôt
**Quand** le bénévole modifie son nom, son prix ou sa catégorie
**Alors** la modification est sauvegardée et si la catégorie a changé, la table est réassignée selon l'algorithme FR-023 (même table si vendeur déjà présent dans la nouvelle catégorie, sinon table la moins chargée toutes catégories confondues)

**Étant donné** qu'un article est enregistré en phase Dépôt
**Quand** le bénévole le supprime
**Alors** l'article est retiré de la liste du vendeur (FR-024)

**Étant donné** que l'édition a dépassé la phase Dépôt
**Quand** un bénévole tente de modifier ou supprimer un article
**Alors** l'action est bloquée avec un message explicite

**Étant donné** qu'un article existe dans n'importe quelle phase
**Quand** un bénévole modifie l'indicateur complet/incomplet ou le commentaire
**Alors** la modification est sauvegardée immédiatement (FR-025)
**Et** tous les prix sont stockés sous forme de BigDecimal (NFR-003)

**Étant donné** que l'entité `Item` (article) est introduite par cette story
**Quand** cette story est implémentée
**Alors** `SellerProfile.canBeDeleted()` est mise à jour pour remplacer le `hasNoSelledArticles = false` codé en dur (`// TODO avec Story 3.2`) par une vérification réelle : le vendeur peut être supprimé s'il est en phase Dépôt **et** qu'aucun article n'est enregistré pour lui dans cette édition — pas seulement l'absence d'article vendu (FR-021)

**Note technique :** `SellerProfile.canBeDeleted()` (`pluribourse-backend/src/main/java/org/pluribourse/seller/entity/SellerProfile.java`) combine déjà la condition de phase (`isOnDeletablePhase`) avec une condition `hasNoSelledArticles` volontairement figée à `false` depuis la Story 3.1, en attendant que cette story introduise l'entité `Item`. La contrainte réelle porte sur l'absence de tout article enregistré pour ce vendeur (et non sur son seul statut vendu) — le nom de la variable devrait être renommé en conséquence (ex. `hasNoRegisteredArticles`) lors de l'implémentation.

**Étant donné** qu'une édition est en phase Préparation (donc a priori supprimable selon FR-014) mais possède des articles enregistrés — cas possible après un retour arrière Dépôt → Préparation (FR-082) qui préserve les données déjà saisies
**Quand** l'admin tente de supprimer cette édition
**Alors** la suppression est refusée tant qu'il reste au moins un article enregistré pour cette édition, même si la phase autoriserait la suppression

**Note technique :** `EditionService.deleteEdition()` (`pluribourse-backend/src/main/java/org/pluribourse/edition/service/EditionService.java`) ne vérifie aujourd'hui que la phase (`EditionCannotBeDeletedException` si différente de Préparation, Story 2.1/FR-014). Cette story doit y ajouter la même vérification d'absence d'articles enregistrés que celle introduite sur `SellerProfile.canBeDeleted()` ci-dessus, appliquée à l'ensemble des vendeurs de l'édition.

### Story 3.3 : Création et gestion des lots

En tant que bénévole,
je veux regrouper des articles en un lot indivisible avec un prix global unique,
afin que les ensembles vendus ensemble soient traités comme une unité atomique lors de la vente.

**Critères d'acceptation :**

**Étant donné** que le bénévole enregistre des articles pour un vendeur
**Quand** il sélectionne le segment "Lot" dans le sélecteur de type en tête du formulaire de dépôt
**Alors** le formulaire bascule en mode Lot : les champs "Nom du lot" et "Prix global du lot (€)" remplacent les champs de saisie individuelle (FR-043)
**Et** une liste d'articles apparaît avec un bouton "+ Ajouter un article au lot"

**Étant donné** que le bénévole est en mode Lot
**Quand** il renseigne les articles du lot
**Alors** chaque article possède son propre nom/description, catégorie et commentaire optionnel, sans prix individuel (FR-022, FR-043, FR-044)
**Et** le bouton "Valider le lot" reste désactivé tant que moins de 2 articles sont présents dans la liste
**Et** le label du bouton reflète en temps réel le nombre d'articles saisis — ex : "Valider le lot (2 articles)"

**Étant donné** qu'un lot contient plusieurs articles
**Quand** le lot est sauvegardé
**Alors** chaque article reçoit son propre code-barres généré (une étiquette par article, FR-044)
**Et** les articles du lot héritent de l'assignation automatique de table depuis leur catégorie

**Étant donné** qu'une étiquette est générée pour un article de lot
**Quand** rendue
**Alors** elle affiche « Prix du lot : X€ » à la place du prix individuel
**Et** « Lot indivisible : X/N » où X est la position de l'article et N est le total (FR-045)

### Story 3.4 : Infrastructure d'impression — Registre d'imprimantes & Files dynamiques

> **Story technique prérequise (infrastructure enabler)** — Aucune valeur utilisateur visible en sprint review. Livrée avant les Stories 3.5, 3.6, 3.7, 3.8 et 3.9 qui l'utilisent. La Definition of Done est basée sur les ACs techniques ci-dessous.
>
> ⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir `sprint-change-proposal-2026-07-27.md`). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge.

En tant que bénévole déclenchant une impression,
je veux que les travaux d'impression soient traités côté serveur et routés vers l'imprimante que j'ai sélectionnée,
afin que l'impression fonctionne depuis n'importe quel poste connecté via navigateur pendant l'événement.

**Critères d'acceptation :**

**Étant donné** que des imprimantes sont enregistrées en base
**Quand** l'application Spring Boot démarre
**Alors** une `LinkedBlockingQueue` et un thread consommateur dédié sont instanciés par imprimante enregistrée (ARCH-009)
**Et** les queues thermiques utilisent jSerialComm pour écrire sur le port série RFCOMM Bluetooth
**Et** les queues A4 utilisent une socket TCP vers l'adresse réseau configurée

**Étant donné** que le serveur démarre
**Quand** le contexte est initialisé
**Alors** chaque port série thermique configuré est testé en accessibilité
**Et** chaque adresse réseau A4 configurée est testée en accessibilité
**Et** toute imprimante inaccessible est marquée en erreur dans son état de statut (FR-079)

**Étant donné** que plusieurs travaux d'impression sont soumis vers la même imprimante
**Quand** ils entrent dans sa file
**Alors** les travaux sont exécutés séquentiellement — un à la fois (FR-029)
**Et** les files d'imprimantes différentes s'exécutent indépendamment sans se bloquer

**Étant donné** qu'un utilisateur déclenche l'impression depuis l'interface
**Quand** la requête est reçue
**Alors** un spinner apparaît dans le bouton d'impression pendant la soumission à la file (UX-DR19)
**Et** aucune action n'est requise sur le poste client (FR-078)

**Étant donné** qu'un travail d'impression se termine avec succès
**Quand** le thread consommateur termine
**Alors** un toast de succès apparaît pendant 4 secondes

**Étant donné** que l'imprimante sélectionnée est hors ligne ou en erreur au moment du job
**Quand** le travail d'impression échoue
**Alors** un toast d'erreur persistant apparaît identifiant l'imprimante concernée (FR-079)
**Et** la file de cette imprimante est suspendue ; les autres files ne sont pas affectées
**Et** l'action d'impression reste redéclenchable depuis l'interface

### Story 3.5 : Génération & Impression des étiquettes thermiques

En tant que bénévole validant le dépôt d'un vendeur,
je veux que les étiquettes d'articles soient automatiquement imprimées sur l'imprimante thermique,
afin que les articles soient physiquement étiquetés immédiatement après le dépôt, sans étape manuelle.

**Critères d'acceptation :**

**Étant donné** qu'un article est enregistré
**Quand** sauvegardé
**Alors** un code-barres Code 128 unique est généré côté serveur via ZXing (FR-026) : 8 chiffres structurés XXXX-XXXX (4 chiffres numéro vendeur + 4 chiffres numéro article dans l'inventaire du vendeur)

**Étant donné** qu'un dépôt est validé
**Quand** la validation se termine
**Alors** toutes les étiquettes de ce vendeur sont automatiquement mises en file d'attente pour l'impression thermique (FR-028)
**Et** le format du rouleau est : séparateur vendeur (nom + édition) → étiquette article → séparateur article → étiquette article → … (FR-030)

**Étant donné** qu'une étiquette est générée pour un article standard
**Quand** rendue pour ESC/POS
**Alors** elle affiche dans cet ordre : nom de l'édition — ligne vide — « --- Catégorie --- » — nom de l'article + prix — « /!\ INCOMPLET » sur ligne dédiée si applicable — « Table n°X » — ligne vide — graphique Code 128 (bitmap) — numéro de code-barres lisible au format XXXX-XXXX — ligne vide
**Et** aucun nom de vendeur n'apparaît sur l'étiquette (RGPD, FR-027)

**Étant donné** qu'une étiquette est générée pour un article de lot
**Quand** rendue
**Alors** elle affiche « Prix du lot : X€ » et « Lot indivisible : X/N » (FR-045)

**Étant donné** que l'imprimante thermique sélectionnée a une largeur configurée
**Quand** le travail ESC/POS est préparé
**Alors** la largeur de cette imprimante est appliquée (FR-032 : 57 mm ou 80 mm selon la configuration par imprimante)

### Story 3.6 : Génération & Impression automatique du bordereau de dépôt PDF

En tant que bénévole complétant un dépôt,
je veux qu'un bordereau de dépôt soit automatiquement imprimé à la validation,
afin que le vendeur dispose d'un justificatif papier de ce qu'il a déposé et du montant qu'il percevra, sans étape manuelle supplémentaire.

**Critères d'acceptation :**

**Étant donné** qu'un dépôt est validé
**Quand** la validation se termine (en parallèle de l'impression des étiquettes — Story 3.5)
**Alors** un PDF de bordereau de dépôt est automatiquement généré côté serveur via OpenPDF 3.0.0 dans la langue des documents de l'édition (FR-031)
**Et** le PDF est mis en file d'attente dans la file des documents A4 et envoyé à l'imprimante standard USB

**Étant donné** que le PDF est généré
**Quand** le contenu est rendu
**Alors** il contient : liste des articles (nom, prix unitaire), taux de commission, reversement net attendu (BigDecimal, précis au centime, FR-031)
**Et** un lot apparaît sur une seule ligne (nom du lot, prix du lot)
**Et** un tableau « détail des lots » liste chaque article membre d'un lot : nom du lot, catégorie du lot, nom de l'article *(SCP 2026-09-02b)*

**Étant donné** que le bénévole consulte la fiche vendeur **en phase Dépôt**
**Quand** il clique sur « Réimprimer le bordereau »
**Alors** le bordereau est régénéré et remis en file d'attente
**Et** en phase Post-vente, la fiche vendeur (`/volunteer/deposit`) n'est plus accessible (ni entrée de navigation, ni route active) — le bilan de vente (Story 5.2) est le document de référence du vendeur en Post-vente *(SCP 2026-09-02b — supersède la décision de suivi 2026-08-24)*

### Story 3.7 : Vue admin de diagnostic des imprimantes

> ⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir `sprint-change-proposal-2026-07-27.md`). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge.

En tant qu'administrateur,
je veux consulter l'état en temps réel de chaque imprimante enregistrée et de ses jobs en cours,
afin de diagnostiquer les problèmes d'imprimante sans interrompre l'événement.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers `/admin/print-queue`
**Quand** la page se charge
**Alors** toutes les imprimantes enregistrées sont listées (thermiques et A4), chacune avec : nom, type, statut de connexion, profondeur de file, job en cours, dernière erreur (FR-079)

**Étant donné** que le serveur a détecté une imprimante inaccessible au démarrage
**Quand** l'admin consulte la vue de diagnostic
**Alors** cette imprimante est signalée en erreur avec la cause (port série introuvable / adresse réseau injoignable)

**Étant donné** qu'un job est en erreur dans la file d'une imprimante
**Quand** l'admin clique sur « Relancer »
**Alors** le job est remis en tête de file et la file de cette imprimante reprend (FR-079)

**Étant donné** qu'un job est en erreur
**Quand** l'admin clique sur « Ignorer »
**Alors** le job est retiré de la file et la file reprend les jobs suivants (FR-079)

**Étant donné** qu'un bénévole tente d'accéder à la page de diagnostic
**Quand** la route est chargée
**Alors** l'accès est refusé avec un 403 — vue admin uniquement

### Story 3.8 : Registre des imprimantes (Admin)

> ⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir `sprint-change-proposal-2026-07-27.md`). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge.

En tant qu'administrateur,
je veux enregistrer et gérer les imprimantes thermiques et A4 disponibles,
afin que les bénévoles puissent les sélectionner à leur connexion et que chaque imprimante dispose de sa propre file d'impression.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers `/admin/printers`
**Quand** la page se charge
**Alors** la liste des imprimantes enregistrées est affichée (nom, type, statut de connexion)

**Étant donné** que l'admin ajoute une imprimante thermique
**Quand** le formulaire s'affiche
**Alors** une liste déroulante présente les ports série disponibles sur le serveur (SerialPort.getCommPorts() — nom descriptif de l'appareil Bluetooth appairé)
**Et** l'admin saisit un nom d'affichage et sélectionne la largeur (57 mm ou 80 mm) (FR-032)
**Et** à la sauvegarde, une file `LinkedBlockingQueue` est instanciée pour cette imprimante (ARCH-009)

**Étant donné** que l'admin ajoute une imprimante A4
**Quand** le formulaire est soumis
**Alors** l'admin a saisi un nom d'affichage, une adresse IP ou hostname, et un port TCP (défaut 9100) (FR-077)
**Et** à la sauvegarde, une file `LinkedBlockingQueue` est instanciée pour cette imprimante

**Étant donné** que l'admin supprime une imprimante
**Quand** la suppression est confirmée
**Alors** l'imprimante est retirée du registre et sa file est détruite
**Et** les bénévoles ayant cette imprimante sélectionnée en session reçoivent un message d'erreur au prochain job d'impression

**Étant donné** qu'un bénévole tente d'accéder à `/admin/printers`
**Quand** la route est chargée
**Alors** l'accès est refusé avec un 403 — vue admin uniquement

### Story 3.9 : Sélection d'imprimante par le bénévole à la connexion (FR-098)

> ⚠️ Mécanisme de connexion remplacé par les Stories 3.11/3.12 (voir `sprint-change-proposal-2026-07-27.md`). Les ACs ci-dessous décrivent l'implémentation d'origine, obsolète depuis l'introduction de PrinterBridge.

En tant que bénévole,
je veux choisir mon imprimante thermique et mon imprimante A4 à ma connexion,
afin que mes travaux d'impression soient routés vers l'imprimante la plus proche de mon poste.

**Critères d'acceptation :**

**Étant donné** que le bénévole se connecte avec succès
**Quand** la connexion aboutit
**Alors** un écran de sélection d'imprimante s'affiche avant l'accès à l'interface principale
**Et** deux listes déroulantes sont présentées : une pour les imprimantes thermiques, une pour les imprimantes A4 (parmi celles enregistrées et dont le statut de connexion est disponible)

**Étant donné** que le bénévole valide sa sélection
**Quand** la confirmation est soumise
**Alors** la sélection est stockée en session (non persistée en base)
**Et** le bénévole est redirigé vers l'interface correspondant à la phase active

**Étant donné** qu'un bénévole déclenche une impression thermique pendant sa session
**Quand** le job est soumis
**Alors** il est routé vers la file de l'imprimante thermique sélectionnée en session

**Étant donné** que l'imprimante sélectionnée est indisponible au moment du job
**Quand** le job est traité
**Alors** le job échoue immédiatement avec un toast d'erreur persistant (FR-098)
**Et** aucun retry automatique ni reroutage n'est effectué

**Étant donné** qu'aucune imprimante n'est enregistrée ou disponible
**Quand** l'écran de sélection s'affiche
**Alors** un message d'avertissement indique qu'aucune imprimante n'est disponible
**Et** le bénévole peut tout de même accéder à l'interface (l'impression sera en erreur jusqu'à la résolution par l'admin)

### Story 3.10 : Modification d'un lot après saisie

> **Story ajoutée après coup (2026-07-21)** — la Story 3.3 avait explicitement exclu ce périmètre ("aucune AC de l'épic ne le demande, à traiter dans une story dédiée si le besoin est confirmé"). Le besoin est confirmé par l'utilisateur.

En tant que bénévole,
je veux modifier un lot déjà enregistré (nom, prix global, articles membres),
afin de pouvoir corriger une erreur de saisie sans devoir supprimer et recréer tout le lot.

**Critères d'acceptation :**

**Étant donné** qu'un lot est enregistré en phase Dépôt
**Quand** le bénévole modifie son nom ou son prix global
**Alors** la modification est sauvegardée et reflétée sur chaque article membre du lot (nom de lot / prix de lot affichés, cohérent avec l'AC équivalente sur les articles individuels, Story 3.2)

**Étant donné** qu'un lot est enregistré en phase Dépôt avec ses articles membres
**Quand** le bénévole ajoute un article au lot, ou modifie le nom/la catégorie/l'indicateur incomplet/le commentaire d'un article membre existant
**Alors** la modification est sauvegardée ; si la catégorie d'un article change, sa table est réassignée selon l'algorithme FR-023 (même règle que pour un article individuel)

**Étant donné** qu'un lot compte exactement 2 articles membres
**Quand** le bénévole tente de retirer un article du lot
**Alors** le retrait est refusé (un lot doit toujours compter au moins 2 articles, FR-043) — le bénévole doit soit ajouter un article avant de retirer l'ancien, soit supprimer le lot entier

**Étant donné** qu'un lot est enregistré en phase Dépôt
**Quand** le bénévole supprime le lot entier
**Alors** le lot et tous ses articles membres sont retirés de la liste du vendeur (cohérent avec la suppression d'un article individuel, FR-024)

**Étant donné** que l'édition a dépassé la phase Dépôt
**Quand** un bénévole tente de modifier, ajouter/retirer un article, ou supprimer un lot
**Alors** l'action est bloquée avec un message explicite (même règle que pour les articles individuels)

---

### Story 3.11 : Intégration de PrinterBridge — connexion et statut

> **Story ajoutée après coup (2026-07-27)** — remplace le mécanisme de connexion directe des Stories 3.4/3.7/3.8/3.9 (voir `sprint-change-proposal-2026-07-27.md`). Le Bluetooth est un périphérique matériel de la machine hôte, invisible depuis un conteneur Docker — PrinterBridge (repository séparé, composant natif installé sur le poste admin) possède seul l'accès matériel et l'expose via une API HTTP/WebSocket locale.

En tant qu'administrateur,
je veux enregistrer une imprimante en la sélectionnant dans la liste détectée par PrinterBridge plutôt qu'en saisissant un port série ou une adresse IP,
afin que le backend n'ait plus jamais à ouvrir lui-même une connexion matérielle.

**Critères d'acceptation :**

**Étant donné** que l'admin ouvre le dialog "Ajouter une imprimante"
**Quand** le dialog se charge
**Alors** la liste des imprimantes détectées par PrinterBridge est affichée (nom, type déduit, statut), à l'exclusion des imprimantes déjà enregistrées
**Et** un indicateur de chargement s'affiche pendant l'appel à PrinterBridge, avant que le dialog ne s'ouvre

**Étant donné** que PrinterBridge est injoignable
**Quand** l'appel de découverte échoue
**Alors** un message d'avertissement remplace le formulaire : "Le service PrinterBridge ne répond pas sur ce poste. Vérifiez qu'il est lancé."

**Étant donné** que l'admin sélectionne une imprimante détectée
**Quand** le formulaire est soumis
**Alors** le type (THERMAL/A4) est dérivé automatiquement de l'imprimante sélectionnée — plus de sélecteur manuel — et l'imprimante est enregistrée avec un identifiant opaque `printerBridgeId` (fini port série/IP+port)

**Étant donné** qu'une imprimante enregistrée doit être vérifiée (démarrage du serveur ou création)
**Quand** la vérification de connectivité s'exécute
**Alors** elle interroge le statut PrinterBridge de l'imprimante plutôt que d'ouvrir une socket/port série ; une erreur PrinterBridge injoignable est distinguée d'une imprimante spécifiquement signalée hors ligne

**Étant donné** qu'une imprimante est enregistrée
**Quand** l'admin clique sur "Tester l'impression"
**Alors** un test d'impression réel est déclenché via PrinterBridge, avec retour succès/erreur

### Story 3.12 : Intégration de PrinterBridge — envoi des jobs d'impression

En tant que bénévole validant un dépôt,
je veux que le contenu à imprimer (étiquettes thermiques, bordereau PDF) soit transmis à PrinterBridge plutôt qu'écrit directement sur un port série ou une socket TCP,
afin que l'impression fonctionne réellement depuis un backend conteneurisé.

**Critères d'acceptation :**

**Étant donné** qu'un job d'impression thermique est soumis (étiquettes articles)
**Quand** le job est traité
**Alors** l'intégralité du contenu ESC/POS est construite en un seul payload et transmise à PrinterBridge via WebSocket (`WS /printers/{id}/print`)

**Étant donné** qu'un job d'impression de document A4 est soumis (bordereau de dépôt)
**Quand** le job est traité
**Alors** le PDF déjà généré est transmis à PrinterBridge via le même canal WebSocket, qui le soumet au spouleur OS

**Étant donné** que PrinterBridge ne répond pas dans le délai imparti (10s) ou refuse la connexion
**Quand** le job échoue à ce niveau
**Alors** l'erreur remonte comme une indisponibilité de PrinterBridge, distincte d'un échec d'impression signalé par PrinterBridge lui-même

**Étant donné** le contrat `PrintQueueService.submit(printerId, job)` existant
**Quand** cette story est implémentée
**Alors** sa signature reste strictement inchangée — aucun impact sur les epics 4/5 qui en dépendront

### Story 3.13 : Ignorer une imprimante détectée (FR-104)

> **Story ajoutée après coup (2026-07-28)** — capacité demandée après livraison des Stories 3.11/3.12.

En tant qu'administrateur,
je veux ignorer une imprimante détectée par PrinterBridge mais que je ne veux pas enregistrer (ex. l'imprimante d'un voisin, une imprimante temporaire),
afin qu'elle cesse d'encombrer la liste de découverte à chaque scan.

**Critères d'acceptation :**

**Étant donné** que l'admin ouvre le dialog "Ajouter une imprimante"
**Quand** le dialog affiche les imprimantes détectées
**Alors** chaque imprimante détectée est présentée comme une ligne avec deux actions : "Enregistrer" et "Ignorer" (remplace le menu déroulant unique de la Story 3.11)

**Étant donné** que l'admin clique sur "Ignorer" pour une imprimante détectée
**Quand** l'action est confirmée
**Alors** l'imprimante est ajoutée au registre des imprimantes ignorées et disparaît immédiatement de la liste de découverte, y compris lors des scans suivants

**Étant donné** qu'une imprimante a été ignorée
**Quand** l'admin consulte `/admin/printers`
**Alors** une section "Imprimantes ignorées" (repliée par défaut) liste les imprimantes ignorées, avec une action "Réactiver" par ligne

**Étant donné** qu'une imprimante ignorée est réactivée
**Quand** l'action est confirmée
**Alors** elle est retirée du registre des imprimantes ignorées et réapparaît dans la liste de découverte au prochain scan

**Étant donné** qu'une imprimante détectée est déjà enregistrée dans le registre des imprimantes
**Quand** la liste de découverte est construite
**Alors** elle n'apparaît ni dans la liste "à enregistrer" ni dans la liste "ignorées" — l'action "Ignorer" ne s'applique qu'aux imprimantes non enregistrées (cohérent avec Story 3.11, filtrage déjà en place)

**Étant donné** qu'un bénévole tente d'accéder à une route liée aux imprimantes ignorées
**Quand** la route est chargée
**Alors** l'accès est refusé avec un 403 — vue admin uniquement (cohérent avec Story 3.8)

---

## Epic 4 : Point de vente

Les bénévoles peuvent scanner des articles avec un scanner code-barres USB, gérer les paniers avec prise en charge complète des lots, finaliser les ventes et imprimer les factures acheteurs — en toute sécurité sur plusieurs postes simultanés.

### Story 4.1 : Composant scanner & Scan d'articles

En tant que bénévole caissier,
je veux scanner des articles avec un scanner code-barres USB fonctionnant quelle que soit la disposition du clavier,
afin de traiter les ventes rapidement sans configurer chaque poste de travail.

**Critères d'acceptation :**

**Étant donné** que le bénévole ouvre `/volunteer/pos`
**Quand** la page se charge
**Alors** le champ de saisie scanner est auto-focalisé et capture tous les événements clavier

**Étant donné** que le bénévole clique ailleurs sur la page
**Quand** 500ms d'inactivité clavier s'écoulent
**Alors** le focus revient automatiquement sur le champ de saisie scanner

**Étant donné** qu'un scanner envoie un code-barres sur une disposition QWERTY alors que l'OS est en AZERTY
**Quand** le scan est traité
**Alors** la valeur correcte du code-barres est décodée via la correspondance de codes de touches (FR-034)

**Étant donné** qu'un code-barres valide est scanné
**Quand** l'article est trouvé et disponible
**Alors** l'article est ajouté au panier et affiche le nom et le prix (FR-035)

**Étant donné** qu'un code-barres est scanné pour un article déjà vendu
**Quand** la recherche se termine
**Alors** une erreur inline apparaît : « Article déjà vendu sur un autre poste. » (FR-036)
**Et** l'article n'est pas ajouté au panier

**Étant donné** qu'un code-barres est scanné pour un article avec l'indicateur incomplet
**Quand** l'article est trouvé
**Alors** un avertissement inline est affiché avec le détail manquant (FR-037)
**Et** l'article est ajouté au panier (toujours vendable)

### Story 4.2 : Gestion du panier & Validation du paiement

En tant que bénévole caissier,
je veux gérer le panier acheteur et valider le paiement,
afin de conclure les transactions proprement avec un historique complet.

**Critères d'acceptation :**

**Étant donné** que des articles ont été ajoutés au panier
**Quand** le panier est affiché
**Alors** chaque article affiche son nom et son prix unitaire
**Et** le total courant est affiché en bas

**Étant donné** que le bénévole souhaite retirer un article
**Quand** il clique sur l'icône fermer d'une ligne d'article
**Alors** l'article est retiré du panier

**Étant donné** que le panier contient au moins un article
**Quand** le bénévole clique sur « Valider »
**Alors** tous les articles du panier sont marqués comme vendus dans une transaction atomique unique (FR-039)
**Et** le panier est vidé et prêt pour une nouvelle transaction

**Étant donné** que le panier contient uniquement des articles valides et que le bénévole clique sur « Valider »
**Quand** la confirmation de paiement s'affiche
**Alors** le bénévole sélectionne le moyen de paiement (espèces, chèque, carte) avant que la transaction ne se finalise (FR-093)
**Et** le moyen de paiement est enregistré avec la transaction

**Étant donné** que le moyen de paiement sélectionné est « espèces »
**Quand** le bénévole saisit un montant dans le champ optionnel « Somme remise »
**Alors** le système affiche la monnaie à rendre (somme remise − total du panier) (FR-093)

**Étant donné** que le moyen de paiement sélectionné est « espèces » et que le champ « Somme remise » est laissé vide
**Quand** le bénévole valide
**Alors** la transaction se finalise sans calcul de monnaie (montant exact supposé) (FR-093)

**Étant donné** que le paiement a été validé
**Quand** la transaction se clôt
**Alors** aucun article ne peut être retourné ou modifié (FR-039 : ni retour ni échange)

### Story 4.3 : Gestion des lots au POS

En tant que bénévole caissier,
je veux être informé lorsqu'un lot est incomplet lors du scan,
afin de décider de le vendre en l'état ou de le retirer du panier.

**Critères d'acceptation :**

**Étant donné** qu'un article scanné appartient à un lot
**Quand** il est ajouté au panier
**Alors** le groupe lot apparaît avec le nom du lot en rouge et un compteur « 1/N scannés » (FR-046)
**Et** le sous-total du lot est affiché dans l'en-tête du groupe
**Et** aucun prix individuel n'est affiché dans le groupe lot

**Étant donné** que le premier article d'un lot est scanné
**Quand** il est ajouté au panier
**Alors** une notification inline avertissement apparaît dans le panier indiquant combien d'articles manquent dans le lot (FR-047)
**Et** le bouton « Valider » reste actif — la vente d'un lot incomplet est autorisée

**Étant donné** que les N articles d'un lot sont tous scannés
**Quand** le dernier article est ajouté
**Alors** le lot est marqué complet et vendu à son prix global (FR-048)

**Étant donné** qu'au moins un article d'un lot a été vendu
**Quand** un caissier scanne un autre article du même lot
**Alors** le scan est rejeté avec une erreur explicite (« cet article appartient à un lot déjà vendu ») — au scan **et** à la validation du panier (course multi-postes)
**Et** les articles non vendus du lot reviennent au vendeur et apparaissent comme invendus au bilan (FR-109) *(SCP 2026-09-02b)*

**Étant donné** qu'un lot est partiellement scanné et que l'acheteur ne trouve pas les articles restants
**Quand** le bénévole clique sur « Retirer le lot entier »
**Alors** tous les articles de ce lot sont retirés du panier (FR-081)
**Et** la transaction peut continuer avec les articles hors lot

**Étant donné** qu'un lot complet est validé
**Quand** la facture est générée
**Alors** le lot apparaît sur une seule ligne : nom du lot et prix du lot (FR-041)

### Story 4.4 : Sécurité de la concurrence multi-postes

En tant que bénévole sur n'importe quel poste caissier,
je veux que le système empêche la double vente du même article,
afin que deux caissiers ne puissent pas accidentellement vendre le même article à deux acheteurs différents.

**Critères d'acceptation :**

**Étant donné** que deux bénévoles sur des postes séparés ont le même article dans leurs paniers
**Quand** le premier bénévole valide avec succès
**Alors** la validation du second bénévole retourne un 409 avec la liste des articles en conflit

**Étant donné** qu'un conflit 409 est retourné
**Quand** le composant Angular POS le reçoit
**Alors** une notification inline liste les articles en conflit par nom
**Et** le bénévole les retire manuellement et revalide

**Étant donné** qu'une vente est en cours de validation
**Quand** le verrou optimiste (`@Version` sur `Item`) détecte une écriture concurrente
**Alors** la transaction est annulée et un 409 est retourné — aucune vente partielle n'est enregistrée

**Notes de développement :**
Valider la concurrence via un test d'intégration Testcontainers MariaDB : deux threads `TransactionTemplate` concurrents valident des paniers qui se chevauchent — exactement un doit réussir et l'autre recevoir un 409.

### Story 4.5 : Impression de la facture acheteur

En tant que bénévole caissier,
je veux imprimer une facture acheteur à la demande après une vente validée,
afin que l'acheteur dispose d'un justificatif papier de son achat.

**Critères d'acceptation :**

**Étant donné** qu'un paiement a été validé
**Quand** le bénévole clique sur « Imprimer la facture »
**Alors** un PDF est généré côté serveur via OpenPDF 3.0.0

**Étant donné** que le PDF est généré
**Quand** le contenu est rendu
**Alors** il contient : liste des articles (nom, prix unitaire), total du panier, nom de l'association, nom de l'édition, date (FR-041)
**Et** un lot apparaît sur une seule ligne (nom du lot, prix du lot)

**Étant donné** que le PDF est généré
**Quand** envoyé pour impression
**Alors** il est mis en file d'attente dans la file des documents A4 et envoyé à l'imprimante standard USB

**Étant donné** que la facture a déjà été imprimée une fois
**Quand** le bénévole redéclenche l'impression
**Alors** la facture est remise en file d'attente (toujours réimprimable)

### Story 4.6 : Gestion du changement de phase dans le composant POS — côté client

En tant que bénévole caissier avec un panier actif,
je veux être immédiatement notifié si l'administrateur change la phase pendant que je suis en cours de transaction,
afin de ne pas tenter de finaliser une vente dans une phase qui n'est plus valide.

**Dépendance :** Story 2.8 (émission SSE `basket-cancelled` côté serveur)

**Critères d'acceptation :**

**Étant donné** que le composant Angular POS reçoit l'événement SSE `basket-cancelled`
**Quand** l'événement arrive
**Alors** un toast persistant apparaît : « La phase a changé. Votre panier a été annulé. »
**Et** le panier est entièrement vidé
**Et** le champ de saisie scanner est désactivé (FR-090, UX-DR21)

**Étant donné** que le scanner est désactivé après l'annulation du panier
**Quand** le bénévole veut reprendre
**Alors** il doit recharger la page caissier pour réactiver le scanner

**Étant donné** qu'un bénévole n'a pas de panier actif
**Quand** un événement `basket-cancelled` arrive (cas théorique)
**Alors** aucun toast n'est affiché — le composant l'ignore silencieusement

---

## Epic 5 : Post-vente, Reversements & Rapports

Les bénévoles peuvent solder les vendeurs et traiter les reversements. Les administrateurs peuvent générer des rapports de bilan journaliers et d'édition en PDF, identifier les vendeurs non soldés et clôturer officiellement les éditions.

### Story 5.1 : Flux de solde des vendeurs

En tant que bénévole,
je veux voir la liste des vendeurs non soldés et les solder ou marquer leur reversement comme non réclamé,
afin que tous les reversements soient comptabilisés avant la fin de l'événement.

**Critères d'acceptation :**

**Étant donné** que le bénévole navigue vers `/volunteer/settlement`
**Quand** la page se charge
**Alors** tous les vendeurs non soldés sont listés avec nom, prénom, montant dû et statut (FR-053)
**Et** les colonnes téléphone et email ne sont pas affichées — elles sont réservées à la vue admin `/admin/settlement` (FR-095)

**Étant donné** que le montant saisi est strictement inférieur au montant net calculé
**Quand** le bénévole clique sur « Solder »
**Alors** un avertissement s'affiche : « Le montant saisi (X,XX €) est inférieur au montant dû (Y,YY €). »
**Et** le bénévole peut tout de même confirmer le solde (FR-051)

**Étant donné** que le montant saisi est strictement supérieur au montant net calculé
**Quand** le bénévole clique sur « Solder »
**Alors** la validation est bloquée avec un message d'erreur (FR-051)
**Et** le bénévole doit corriger le montant avant de pouvoir valider

**Étant donné** que le bénévole clique sur « Solder » pour un vendeur
**Quand** l'action de solde se termine
**Alors** le statut du vendeur passe à Soldé
**Et** le vendeur disparaît de la liste des non soldés

**Étant donné** qu'un vendeur ne souhaite pas récupérer son reversement
**Quand** le bénévole clique sur « Non réclamé » (FR-052)
**Alors** une boîte de dialogue de confirmation apparaît : « Le montant de X,XX EUR sera transféré aux recettes de l'association. Cette action est irréversible. »
**Et** à la confirmation, le montant total dû est enregistré comme recette de l'association
**Et** le vendeur est retiré de la liste des non soldés

**Étant donné** que le bénévole ouvre le formulaire de solde d'un vendeur
**Alors** une case « Imprimer le bilan de vente » y est présente, cochée par défaut
**Et** à la confirmation du solde, si la case est cochée, le bilan est mis en file d'impression A4 (best-effort — un échec d'impression n'annule pas le solde)

**Étant donné** qu'un vendeur est soldé ou marqué non réclamé
**Quand** le bénévole consulte la liste de solde
**Alors** un bouton « Imprimer le bilan de vente » est disponible pour ce vendeur (ré-impression, UX-DR22), avec retour visuel spinner et toast
**Et** ce bouton est masqué pour les vendeurs non soldés
*(Les deux blocs ci-dessus : SCP 2026-09-02b — remplacent « un vendeur a été soldé → bouton disponible ».)*

### Story 5.2 : Génération du bilan de vente PDF

En tant que bénévole ou administrateur,
je veux générer un bilan de vente par vendeur affichant les articles vendus, les invendus et le reversement net,
afin que les vendeurs puissent récupérer leur paiement avec un détail complet.

**Critères d'acceptation :**

**Étant donné** qu'un bilan de vente est demandé pour un vendeur
**Quand** le PDF est généré via OpenPDF 3.0.0
**Alors** il contient : (1) un tableau unifié des articles — nom, catégorie, table, prix, **statut (vendu/invendu)** — un lot sur une ligne unique (statut « vendu » si ≥1 article du lot vendu, prix global compté une fois) ; (2) **un tableau « détail des lots »** — nom du lot, article, catégorie, table, statut réel par article — pour indiquer les articles à récupérer ; (3) une **ligne de comptage** : articles vendus / invendus / déposés (1 article = 1 unité, `vendus + invendus = déposés`) ; (4) total brut, commission déduite, montant net à reverser (FR-050) ; (5) le montant remis, **uniquement si le vendeur est soldé** *(SCP 2026-09-02b)*

**Étant donné** qu'un vendeur a vendu des articles avec l'indicateur incomplet
**Quand** le reversement net est calculé
**Alors** la commission s'applique au taux plein — l'incomplétude n'affecte ni la commission ni le prix de vente (FR-089)
**Et** toutes les valeurs monétaires utilisent BigDecimal (précis au centime, NFR-003)

**Étant donné** que la langue du PDF est « FR »
**Quand** le document est généré
**Alors** tous les libellés et en-têtes utilisent les entrées de `messages_fr.properties`

**Étant donné** que l'admin consulte la page de détail d'un vendeur
**Quand** il clique sur « Imprimer le bilan de vente »
**Alors** le même PDF est généré et mis en file d'attente pour impression (UX-DR22)

### Story 5.3 : Rapport de ventes journalier (Admin)

En tant qu'administrateur,
je veux générer un bilan des ventes journalier en phase Vente,
afin de suivre les recettes et la performance des ventes au cours de l'événement.

**Critères d'acceptation :**

**Étant donné** que l'édition est en phase Vente
**Quand** l'admin génère un bilan journalier
**Alors** le rapport couvre la journée calendaire en cours
**Et** contient : articles vendus et invendus pour la journée, recettes brutes journalières, commission journalière perçue, ventilation des recettes par moyen de paiement (FR-054, FR-094)

**Étant donné** que le rapport est généré
**Quand** le PDF est produit via OpenPDF 3.0.0
**Alors** il utilise la langue des documents de l'édition (FR-057)

**Étant donné** qu'un bénévole tente d'accéder à la page des rapports
**Quand** la route est chargée
**Alors** l'accès est refusé avec un 403 (FR-058 : admin uniquement)

**Étant donné** que l'admin actualise le rapport journalier
**Quand** l'actualisation est déclenchée
**Alors** le rapport reflète les dernières données de ventes pour cette journée calendaire

### Story 5.4 : Bilan d'édition & Rapports des vendeurs non soldés

En tant qu'administrateur,
je veux des rapports de bilan au niveau de l'édition et une liste des vendeurs non soldés,
afin d'avoir une vision financière complète à la clôture de l'événement.

**Critères d'acceptation :**

**Étant donné** que l'édition est clôturée
**Quand** l'admin consulte la page des rapports
**Alors** un PDF de bilan d'édition est disponible : total des articles vendus/invendus, recettes brutes totales, commission totale perçue, ventilation des recettes par moyen de paiement (FR-055, FR-094)

**Étant donné** que l'admin veut identifier les vendeurs non soldés
**Quand** il consulte la page de solde (`/admin/settlement`)
**Alors** les vendeurs non soldés sont visibles avec leur numéro de téléphone et leur adresse email, via le filtre « non soldés » de la liste (FR-095)

**Étant donné** qu'une édition est Clôturée et que l'Archivage n'a pas été déclenché
**Quand** un admin consulte l'édition
**Alors** les métriques agrégées, les profils vendeurs et le détail des articles sont accessibles en lecture seule (FR-059)

**Étant donné** que l'Archivage de l'édition a été déclenché
**Quand** un admin consulte l'édition
**Alors** seules les métriques agrégées restent accessibles — les articles et profils vendeurs ne sont plus disponibles en base (FR-059, FR-088)

### Story 5.5 : Page des rapports admin

En tant qu'administrateur,
je veux une page de rapports qui n'affiche que les sections pertinentes pour la phase courante,
afin d'agir rapidement sans naviguer parmi des options non pertinentes.

**Critères d'acceptation :**

**Étant donné** que l'édition est en phase Vente
**Quand** l'admin navigue vers `/admin/reports`
**Alors** seule la section bilan journalier est affichée avec un bouton « Actualiser »
**Et** les sections synthèse et export sont absentes

**Étant donné** que l'édition est en phase Post-vente ou Clôturée
**Quand** l'admin navigue vers `/admin/reports`
**Alors** la section synthèse est visible (total des ventes, reversements, recettes de l'association) en lecture seule
**Et** deux boutons d'export CSV apparaissent : « Exporter le catalogue » et « Exporter les reversements »
**Et** cliquer sur un export CSV déclenche un téléchargement de fichier direct sans boîte de dialogue

**Étant donné** qu'une phase ne correspond pas à la condition de disponibilité d'une section de rapport
**Quand** l'admin consulte la page des rapports
**Alors** cette section est complètement absente (pas grisée — absente)

### Story 5.6 : Impression groupée des bilans de vente (Admin)

En tant qu'administrateur,
je veux imprimer en un seul clic les bilans de vente de tous les vendeurs correspondant au filtre actif,
afin d'éviter de déclencher les impressions une par une avant de commencer les règlements.

**Critères d'acceptation :**

**Étant donné** que l'édition est en phase Post-vente et que l'admin consulte `/admin/settlement`
**Quand** la page se charge
**Alors** un bouton « Imprimer tous les bilans » est visible en haut de la liste (FR-097)

**Étant donné** que le filtre actif est « tous les vendeurs » (filtre par défaut)
**Quand** l'admin clique sur « Imprimer tous les bilans »
**Alors** un travail d'impression A4 est enfilé pour chaque vendeur de l'édition active, toutes pages confondues (FR-097)
**Et** le contenu de chaque bilan respecte le format FR-050

**Étant donné** que le filtre actif est « non soldés »
**Quand** l'admin clique sur « Imprimer tous les bilans »
**Alors** seuls les bilans des vendeurs non soldés sont enfilés — tous, pas uniquement ceux de la page courante (FR-097)

**Étant donné** que l'admin clique sur le bouton
**Quand** la soumission est en cours
**Alors** le bouton passe en état désactivé avec un spinner inline (UX-DR19)
**Et** à la fin, un toast succès (4 s) indique le nombre de bilans mis en file (ex. : « 20 bilans mis en file d'impression. »)

**Étant donné** qu'un ou plusieurs enfilages échouent
**Quand** la soumission se termine
**Alors** un toast d'erreur persistant indique le nombre d'échecs et contient un lien vers `/admin/print-queue` (UX-DR19)
**Et** les travaux déjà enfilés avec succès ne sont pas annulés

**Étant donné** que l'édition est en phase Clôturée
**Quand** l'admin consulte `/admin/settlement`
**Alors** le bouton « Imprimer tous les bilans » est absent (FR-097)

**Étant donné** qu'un bénévole consulte `/volunteer/settlement`
**Quand** la page se charge
**Alors** le bouton « Imprimer tous les bilans » est absent (FR-097)

---

## Epic 6 : Catalogue articles

Les administrateurs et les bénévoles peuvent parcourir, rechercher et filtrer tous les articles de l'édition active dans toutes les phases. Les administrateurs peuvent également consulter le catalogue archivé d'une édition passée.

### Story 6.1 : Catalogue articles — Liste filtrable & triable

En tant qu'administrateur ou bénévole,
je veux parcourir tous les articles de l'édition active avec des filtres et un tri,
afin de localiser rapidement n'importe quel article quelle que soit la phase de l'événement.

**Critères d'acceptation :**

**Étant donné** que l'admin ou le bénévole navigue vers `/admin/catalog` ou `/volunteer/catalog`
**Quand** la page se charge
**Alors** tous les articles de l'édition active sont affichés avec pagination (50 par page par défaut, MatPaginator)
**Et** des filtres inline apparaissent au-dessus de la liste

**Étant donné** que l'utilisateur applique un ou plusieurs filtres
**Quand** les filtres sont soumis
**Alors** la liste se met à jour pour n'afficher que les articles correspondants filtrés par : nom/description, numéro de code-barres, catégorie, table, statut vendu/invendu, indicateur complet/incomplet, nom du vendeur (FR-084)

**Étant donné** que l'utilisateur clique sur un en-tête de colonne triable
**Quand** cliqué une fois
**Alors** la liste est triée par ordre croissant avec un indicateur visible
**Et** cliquer à nouveau trie par ordre décroissant

**Étant donné** que la colonne prix est triée
**Quand** JPageFlow traite le tri BigDecimal
**Alors** le tri est tenté ; si le bug connu (JPageFlow v1.5.0) est présent, le test documente ceci comme un échec connu en attente du correctif de la bibliothèque (ARCH-005)

**Étant donné** que l'action Archiver l'édition a été déclenchée
**Quand** un utilisateur navigue vers le catalogue
**Alors** un état vide apparaît : « Édition archivée — aucun article. » sans action (FR-086)

**Étant donné** que plusieurs utilisateurs filtrent le catalogue simultanément
**Quand** chacun soumet des combinaisons de filtres différentes
**Alors** chacun reçoit son propre résultat correct de manière indépendante

### Story 6.2 : Consultation du catalogue d'une édition archivée

⚠ Dépend de la Story 2.7 (mécanisme d'archivage + table d'archivage) — ne peut pas être implémentée avant. Ajoutée le 2026-07-29, voir `sprint-change-proposal-2026-07-29.md`.

En tant qu'administrateur,
je veux consulter le catalogue archivé d'une édition passée,
afin de retrouver l'historique d'une édition après sa clôture et son archivage.

**Critères d'acceptation :**

**Étant donné** que l'admin navigue vers la consultation des éditions archivées
**Quand** la page se charge
**Alors** un sélecteur liste toutes les éditions archivées (nom, dates)
**Et** aucun article n'est affiché tant qu'aucune édition n'est sélectionnée

**Étant donné** que l'admin sélectionne une édition archivée
**Quand** la sélection est confirmée
**Alors** la liste des articles archivés de cette édition s'affiche avec pagination (50 par page, MatPaginator), limitée aux données conservées par l'archivage : nom, catégorie, statut vendu/invendu (FR-102)

**Étant donné** que l'utilisateur applique un ou plusieurs filtres
**Quand** les filtres sont soumis
**Alors** la liste se met à jour, filtrée par nom, catégorie, statut vendu/invendu — pas de filtre code-barres/table/vendeur, ces données n'existant plus après archivage (FR-088)

**Étant donné** que l'utilisateur clique sur un en-tête de colonne triable
**Quand** cliqué une fois
**Alors** la liste est triée par ordre croissant avec un indicateur visible
**Et** cliquer à nouveau trie par ordre décroissant

**Étant donné** qu'un bénévole (non admin) tente d'accéder à cette consultation
**Quand** la requête est envoyée
**Alors** l'accès est refusé (403) — réservé aux administrateurs

### Story 6.3 : Prix et marqueur « (lot) » dans les catalogues

Ajoutée le 2026-09-02, voir `sprint-change-proposal-2026-09-02.md`. Amende FR-088 et FR-102.

En tant qu'administrateur ou bénévole,
je veux que les articles membres d'un lot affichent le prix du lot avec un marqueur « (lot) » dans le catalogue actif comme dans le catalogue archivé,
afin de disposer d'un prix lisible pour les membres de lot (vide aujourd'hui dans le catalogue actif) et de distinguer d'un coup d'œil une ligne de lot d'un article individuel.

**Critères d'acceptation :**

**Étant donné** un article appartenant à un lot dans le catalogue actif
**Quand** la liste s'affiche
**Alors** sa cellule prix affiche le prix global du lot suivi d'un marqueur « (lot) » (ex. « 10 € (lot) »)
**Et** un article individuel affiche son propre prix, inchangé

**Étant donné** l'API `GET /api/catalog`
**Quand** la page est renvoyée
**Alors** chaque entrée ayant un `lotId` porte aussi `lotPrice` égal au prix global du lot
**Et** les entrées individuelles portent `lotPrice = null` ; `price` reste `null` pour les membres de lot (inchangé)

**Étant donné** un article archivé ayant appartenu à un lot
**Quand** le catalogue archivé s'affiche
**Alors** sa cellule prix affiche le prix (= prix global du lot, déjà archivé) suivi d'un marqueur « (lot) »
**Et** un article archivé individuel est inchangé

**Étant donné** qu'une édition est archivée
**Quand** les lignes d'archive sont écrites
**Alors** chaque ligne membre d'un lot stocke l'identifiant du lot d'origine (`lot_ref`) et le nom du lot (`lot_name`)
**Et** les lignes d'articles individuels stockent `null` pour ces deux champs

**Étant donné** deux lots différents de la même édition portant le même nom
**Quand** l'édition est archivée
**Alors** leurs membres archivés portent des valeurs `lot_ref` différentes (les deux lots restent distinguables) malgré un `lot_name` identique

**Étant donné** l'API `GET /api/admin/archive/editions/{id}/items`
**Quand** la page est renvoyée
**Alors** chaque entrée porte `lotRef` et `lotName` (`null` pour les articles individuels)

**Étant donné** qu'un bénévole (non admin) appelle l'endpoint du catalogue archivé
**Quand** la requête est envoyée
**Alors** l'accès est toujours refusé (403) — inchangé

**Étant donné** une édition archivée avant cette migration
**Quand** son catalogue archivé est consulté
**Alors** ses lignes existantes conservent `lot_ref = null` / `lot_name = null` et leurs membres s'affichent sans marqueur — accepté tel quel (données de dev, seront réinitialisées)

**Hors périmètre :** regroupement repliable/dépliable des membres de lot en une seule ligne (dans les deux catalogues) ; tri du catalogue actif sur le prix effectif du lot (reste sur `Item.price`) ; filtre `nom` sur le nom de lot ; rétro-remplissage de `lot_ref`/`lot_name` sur les éditions déjà archivées.
