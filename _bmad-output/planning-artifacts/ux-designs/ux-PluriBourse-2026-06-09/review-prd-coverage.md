# Revue de Couverture PRD — UX PluriBourse
Date : 2026-06-09
Relecteur : Lens Couverture PRD

---

## Résumé

EXPERIENCE.md couvre solidement les flux opérationnels principaux (dépôt, POS, reversement, transitions de phase) et son inventaire de composants s'aligne bien avec le PRD. Cependant, plusieurs exigences à interface utilisateur sont absentes ou significativement sous-spécifiées : l'anonymisation RGPD des vendeurs, les déclencheurs d'impression du bordereau de dépôt et de la facture, le repli catalogue-vers-panier, la page Paramètres admin, le support multilingue dans l'interface elle-même, et le flux de retour en arrière de phase. Ces lacunes ne sont pas cosmétiques — chacune correspond à un écran ou une interaction utilisateur qui devra être conçu.

---

## Constats

### PASS — Couverture du cycle de vie des phases

Les cinq phases (Inscription est pré-application, puis Dépôt → Vente → Post-vente → Clôturé) sont représentées. La table IA de EXPERIENCE.md mappe chaque surface Bénévole à la phase correcte. La mise à jour de la puce de phase via SSE (`phase-changed`) est spécifiée pour la topbar. Le Flux 4 démontre la transition avant avec dialog et diffusion SSE. Le retour en arrière est mentionné dans le PRD (FR-082) mais n'est que partiellement couvert — voir PRÉOCCUPATION ci-dessous.

### PASS — Flux POS principaux (FR-033 à FR-048, FR-081, FR-090)

Le comportement du champ scanner, la transparence AZERTY/QWERTY, la gestion du panier, le regroupement des lots avec compteur X/N, la validation bloquée sur les lots incomplets, la suppression de lot (FR-081) et l'annulation du panier lors d'un changement de phase (FR-090) sont tous spécifiés dans les sections Patrons de Composants et Patterns d'État, avec le Flux 2 fournissant le parcours narratif. C'est le domaine de couverture le plus solide.

### PASS — Couverture des rôles utilisateurs (Admin / Bénévole)

Le modèle à deux rôles est correctement reflété dans l'IA (routes séparées, sidebar Admin uniquement, badge de rôle topbar). FR-064 (l'admin ne peut pas agir en tant que bénévole) est reconnu dans la section Foundation. L'interface Bénévole adaptative à la phase (FR-065) est couverte par la table IA. FR-061 (compte admin unique) n'a pas de surface UX directe, donc pas de lacune ici.

### PASS — Cœur du reversement post-vente (FR-049 à FR-053)

Le Flux 3 couvre le chemin « Non collecté » avec le dialog de confirmation correct et le langage d'irréversibilité. La liste des vendeurs non reversés avec numéro de téléphone (FR-053) est représentée. La saisie du montant du reversement par le bénévole (FR-051) est sous-entendue par le Flux 3 mais pas explicitement conçue comme composant — acceptable au niveau spine.

### PASS — Plancher d'accessibilité et ton

WCAG 2.2 AA, piège focus, aria-live pour le scanner, aria-label sur la puce de phase, et cibles minimum 44×44 px sont tous spécifiés. La section Voix et Ton est approfondie et adresse directement le contexte du bénévole sous pression d'événement.

### PASS — Patrons de composants pour le retour d'impression (FR-079)

Le patron toast imprimante-hors-ligne est défini à la fois dans Patterns d'État et Patrons de Composants, avec le comportement correct (toast persistant, action rejouable). Couvre FR-079.

---

### PRÉOCCUPATION — Flux de retour en arrière de phase (FR-082)

FR-082 spécifie que le retour en arrière (Clôturé → Post-vente → Vente → Dépôt) est disponible phase par phase et nécessite une confirmation. EXPERIENCE.md définit `/admin/editions/:id/phase` comme surface et montre la transition avant dans le Flux 4, mais il n'y a aucune spécification de ce à quoi ressemble le déclencheur de retour en arrière, si le même patron de dialog s'applique, ni comment l'interface signale que le retour en arrière depuis Clôturé est indisponible après Nettoyer l'Édition (FR-088). L'action Nettoyer l'Édition est mentionnée dans les Patterns d'État (Phase Clôturée), mais l'état retour-en-arrière-désactivé-après-nettoyage n'est pas couvert.

### PRÉOCCUPATION — Anonymisation RGPD des vendeurs (FR-021)

FR-021 exige que l'admin puisse supprimer un profil vendeur, ce qui déclenche l'anonymisation dans toutes les éditions (nom, email, téléphone, descriptions d'articles). C'est une action destructive distincte avec des conséquences significatives. EXPERIENCE.md ne spécifie pas de surface UI pour cela : aucune mention sur la fiche `/admin/sellers/:id`, pas de spec de dialog de confirmation, pas d'explication de ce à quoi ressemble la fiche anonymisée post-action. La règle générale « dialog de confirmation pour les actions destructives » existe dans les Primitives d'Interaction, mais le déclencheur spécifique et le post-état pour la suppression RGPD sont absents.

### PRÉOCCUPATION — Déclencheur d'impression du bordereau de dépôt (FR-031)

FR-031 exige un bordereau de dépôt imprimable par vendeur affichant la liste des articles, les prix unitaires et le reversement net attendu. Le Flux 1 décrit l'impression automatique des étiquettes après la validation du dépôt mais ne mentionne pas l'impression du bordereau. Il n'est pas clair d'après EXPERIENCE.md si le bordereau de dépôt s'imprime automatiquement avec les étiquettes, ou s'il nécessite un déclencheur manuel sur la fiche vendeur. Cette distinction affecte la conception du formulaire de dépôt. La primitive d'interaction Impression dans EXPERIENCE.md (bouton explicite → spinner → toast) implique qu'il est manuel, mais cela n'est pas confirmé pour le bordereau de dépôt spécifiquement.

### PRÉOCCUPATION — Déclencheur d'impression de la facture acheteur (FR-040, FR-041)

FR-040 spécifie qu'après la validation du paiement, une facture acheteur est imprimable « à la demande ». FR-041 en spécifie le contenu. EXPERIENCE.md ne décrit pas où dans l'interface de la caisse le bouton d'impression de facture apparaît (dans le panier après validation ? sur un écran post-transaction ?), ni à quoi ressemble l'état post-validation de la caisse avant la réinitialisation du panier. C'est une lacune dans la spécification du flux POS.

### PRÉOCCUPATION — Ajout manuel au panier depuis le catalogue (FR-087)

FR-087 est un repli critique pour les codes-barres illisibles : un bénévole peut ajouter un article directement depuis le catalogue au panier courant. EXPERIENCE.md référence cela brièvement dans la section Périmètre (« Ajout manuel au panier depuis le catalogue (repli codes-barres illisibles) ») et liste `/volunteer/catalog` comme disponible dans toutes les phases, mais ne spécifie jamais l'interaction : y a-t-il un bouton « Ajouter au panier » sur la ligne du catalogue qui n'apparaît que pendant la phase Vente ? Cela ouvre-t-il la vue POS ? Le catalogue a-t-il besoin du contexte du panier actif ? Ce flux n'a pas de Flux Clé correspondant et pas de patron de composant.

### PRÉOCCUPATION — Contenu de la page Paramètres admin (FR-073, FR-032, FR-005 à FR-007)

FR-073 spécifie qu'une page de paramètres admin centralise : nom de l'association, taux de commission, langue des documents et largeur du ticket thermique. FR-032 rend la largeur du ticket configurable. FR-005–FR-007 définissent la langue des documents au niveau de l'instance. EXPERIENCE.md liste `/admin/settings` dans la table IA mais ne fournit aucune spécification du contenu de la page, de la disposition des champs, ni de quels paramètres sont modifiables à quelle phase (ex. taux de commission gelé après le démarrage du Dépôt — FR-016). Cette page nécessite une spec de composant ou de formulaire.

### PRÉOCCUPATION — UX du gel du taux de commission (FR-016)

FR-016 stipule que le taux de commission est modifiable jusqu'au démarrage de la phase Dépôt, puis gelé pour cette édition. Cela implique un état d'édition conditionnel sur la page Paramètres ou Détail de l'édition. EXPERIENCE.md note que `/admin/editions/:id/categories` est « éditable » avant le Dépôt et « en lecture seule » après, mais n'étend pas ce patron au champ du taux de commission. Aucun traitement visuel (champ désactivé, message inline explicatif) n'est spécifié.

### PRÉOCCUPATION — Configuration initiale admin et changement de mot de passe forcé (FR-062)

FR-062 spécifie qu'au premier lancement, l'admin se connecte avec Admin/Admin et est forcé de changer son mot de passe. Il s'agit d'un flux de premier lancement avec son propre écran ou modal. EXPERIENCE.md ne le couvre pas. Bien qu'il s'agisse d'un flux ponctuel, c'est la première chose qu'une association déployant rencontrera.

### PRÉOCCUPATION — Préférence de langue par compte (FR-003, FR-067)

FR-003 et FR-067 exigent que chaque compte stocke une préférence de langue d'interface, modifiable dans les paramètres du compte. `/account` est listé dans l'IA partagée mais EXPERIENCE.md ne fournit aucune spécification du contenu de la page de compte. Le changement de langue est fondamental à l'exigence ngx-translate — il nécessite au minimum une spec de surface.

### PRÉOCCUPATION — Exigences non fonctionnelles — perception des performances (NFR-001)

NFR-001 exige aucune dégradation notable sur Raspberry Pi 4 sous charge événementielle. EXPERIENCE.md spécifie des lignes de squelette pour les états de chargement (bien), mais n'aborde pas les considérations de performance perçue spécifiques aux contextes de matériel bas de gamme : aucune stratégie de chargement paresseux mentionnée, aucune spécification de latence maximale acceptable pour le retour du scanner, aucune orientation sur l'anti-rebond ou la limitation des requêtes pour les filtres du catalogue. Ce sont des décisions techniques adjacentes à l'UX qui devraient au moins être signalées dans la spine.

### PRÉOCCUPATION — NFR-002 — Communication des conflits entre postes concurrents

NFR-002 et FR-042 exigent que les opérations simultanées depuis 3+ postes ne produisent pas de conflits. EXPERIENCE.md gère le cas article-déjà-vendu (FR-036) et l'annulation du panier lors d'un changement de phase (FR-090) via SSE. Cependant, il n'aborde pas ce qui se passe lorsque deux caissiers scannent le même article simultanément au même moment exact — l'événement SSE `phase-changed` est documenté, mais aucun événement `item-sold` équivalent pour les conflits n'est spécifié pour la zone de résultat du scanner. Le message d'erreur inline « Article déjà vendu sur un autre poste » apparaît dans Voix et Ton comme exemple de message, mais son mécanisme de déclenchement et son timing (est-il synchrone au scan ? asynchrone via SSE ?) ne sont pas spécifiés dans les Patrons de Composants.

---

### ÉCHEC — Surface UI du rapport des vendeurs en attente (FR-056)

FR-056 définit un « rapport des vendeurs en attente » listant les vendeurs non reversés avec leur numéro de téléphone, généré en PDF (FR-057). La route `/admin/reports` est dans l'IA, accessible en phases Post-vente et Clôturé. Cependant, EXPERIENCE.md ne spécifie pas comment les rapports sont déclenchés, listés ou téléchargés. Il n'y a pas de patron de composant pour la page Rapports : aucune mention d'un bouton « Générer », aucun mécanisme de téléchargement, aucune distinction entre le bilan journalier (FR-054, phase Vente uniquement), le bilan d'édition (FR-055, généré à la clôture via FR-013), et le rapport des vendeurs en attente (FR-056). Trois rapports distincts avec des déclencheurs et des cycles de vie différents sont réduits à une seule entrée IA sans spécification comportementale.

### ÉCHEC — Vue en lecture seule de l'édition archivée et état post-Nettoyage (FR-059, FR-086, FR-088)

FR-059 spécifie que les éditions archivées affichent les métriques agrégées et les profils vendeurs en mode lecture seule ; le détail au niveau article n'est disponible qu'en PDF. FR-086 ajoute que si Nettoyer a été déclenché, les données au niveau article ne sont pas disponibles dans le catalogue. FR-088 définit Nettoyer l'Édition comme une action permanente. EXPERIENCE.md mentionne l'état « Phase Clôturée » dans les Patterns d'État (bannière lecture seule, bouton « Nettoyer l'édition »), mais ne spécifie pas : à quoi ressemble la page de détail de l'édition archivée, quelles métriques agrégées sont affichées, comment les profils vendeurs apparaissent sans détail article, ni comment le catalogue gère l'état post-Nettoyage (vide ? masqué ? message ?). C'est en pratique toute une famille d'écrans absente de la spine.

### ÉCHEC — Déclencheur d'impression du bilan de vente en post-vente (FR-049, FR-050, FR-065)

FR-049 et FR-065 spécifient qu'en phase Post-vente, un bénévole peut imprimer le bilan de vente d'un vendeur pour regrouper ses invendus avant la remise. C'est une action bénévole principale en Post-vente — sans doute plus fréquente que le reversement lui-même. Le Flux 3 (reversement) est le seul flux clé Post-vente et il ne mentionne pas l'impression. Le bouton d'impression du bilan de vente sur la liste de reversement ou la fiche vendeur est complètement absent de EXPERIENCE.md.

---

## Recommandations

Ordonnées par priorité. Bloquants (éléments ÉCHEC) en premier.

### 1. [BLOQUANT] Spécifier la page Rapports (FR-054, FR-055, FR-056, FR-057, FR-058)
Ajouter un patron de composant et au minimum une description de maquette pour `/admin/reports`. Définir les trois types de rapports, leurs déclencheurs de génération (bouton à la demande vs. automatique à la clôture), leurs phases d'accès, et le mécanisme de téléchargement (lien de téléchargement PDF / ouverture dans le navigateur / impression automatique). C'est une surface à trois rapports, pas une simple route.

### 2. [BLOQUANT] Spécifier la vue de l'édition archivée et l'état du catalogue post-Nettoyage (FR-059, FR-086, FR-088)
Ajouter une description de la page de détail de l'édition archivée : métriques agrégées affichées, vue en lecture seule du profil vendeur sans détail article, et comportement du catalogue après Nettoyage. Définir ce que déclenche le bouton « Nettoyer l'édition » et à quoi ressemble l'état UI post-Nettoyage (entrée catalogue désactivée ? catalogue vide avec message explicatif ?).

### 3. [BLOQUANT] Ajouter un Flux Clé Post-vente couvrant l'impression du bilan de vente (FR-049, FR-050, FR-065)
Le Flux 3 ne couvre que le reversement. Ajouter le Flux 5 (ou étendre le Flux 3) pour montrer le bénévole imprimant le bilan de vente d'un vendeur avant de remettre les invendus. Spécifier où apparaît le déclencheur d'impression : sur la ligne de la liste de reversement, ou uniquement sur la fiche vendeur.

### 4. Spécifier le contenu et les états modifiables de la page Paramètres admin (FR-073, FR-016, FR-032, FR-005)
Ajouter une spec de formulaire pour `/admin/settings` : champs (nom de l'association, taux de commission, langue des documents, largeur du ticket thermique), leurs conditions de modification (taux de commission désactivé après le démarrage du Dépôt avec message explicatif), et comment la langue des documents est liée à la langue de sortie des documents imprimés.

### 5. Ajouter un Flux Clé catalogue-vers-panier (FR-087)
Ajouter un flux court montrant comment un bénévole accède au catalogue pendant la phase Vente, localise un article illisible, et l'ajoute au panier actif. Spécifier si « Ajouter au panier » n'est visible de façon contextuelle que pendant la phase Vente, et comment le bénévole retourne à la caisse POS après l'action.

### 6. Spécifier la suppression RGPD du vendeur (FR-021)
Sur la fiche `/admin/sellers/:id`, documenter l'action supprimer/anonymiser : contenu du dialog de confirmation (conséquences : anonymisation dans toutes les éditions, irréversible), et l'état post-anonymisation de la fiche (données en placeholder grisées vs. entrée supprimée).

### 7. Spécifier les déclencheurs d'impression du bordereau de dépôt et de la facture acheteur (FR-031, FR-040, FR-041)
Clarifier dans le flux du formulaire de dépôt si le bordereau de dépôt s'imprime automatiquement avec les étiquettes (aux côtés de FR-028) ou est déclenché manuellement. Clarifier à quoi ressemble l'état de la caisse POS après la validation de la transaction et où apparaît le bouton d'impression de facture.

### 8. Couvrir le flux de retour en arrière et l'état retour-en-arrière-désactivé-après-Nettoyage (FR-082, FR-088)
Sur `/admin/editions/:id/phase`, spécifier à quoi ressemblent les boutons de retour en arrière, que le même patron de dialog de confirmation s'applique, et comment l'interface communique que le retour en arrière depuis Clôturé est définitivement indisponible après Nettoyer l'Édition.

### 9. Spécifier la page `/account` (FR-003, FR-067)
Ajouter une spec minimale pour la page de paramètres du compte : sélecteur de préférence de langue (EN/FR), comment il s'applique immédiatement sans rechargement de page (changement de langue à l'exécution ngx-translate), et tous les autres champs modifiables par l'utilisateur.

### 10. Ajouter le flux de changement de mot de passe forcé au premier lancement (FR-062)
Spécifier le flux de première connexion Admin/Admin : est-ce une redirection vers un écran de changement de mot de passe dédié, ou un modal en place sur `/admin` ? Qu'est-ce qui empêche de naviguer ailleurs avant que le mot de passe soit changé ?

### 11. Clarifier le mécanisme de livraison des conflits de scan concurrent (NFR-002, FR-036)
Dans le patron du composant Champ scanner, spécifier si l'erreur « déjà vendu » pour un scan concurrent est une réponse d'erreur HTTP synchrone (très probable) ou un push SSE asynchrone — et comment la caisse gère le cas où deux postes réussissent à scanner le même article dans la même fenêtre de requête.
