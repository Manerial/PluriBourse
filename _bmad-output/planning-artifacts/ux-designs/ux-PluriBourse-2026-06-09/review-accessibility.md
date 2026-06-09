# Revue Accessibilité — UX PluriBourse
Date : 2026-06-09
Relecteur : Lens Accessibilité

---

## Résumé

Les spines témoignent d'une intention réelle en matière d'accessibilité : piégeage du focus dans les dialogs, `aria-live` sur la zone de résultat du scanner, interdiction des affordances au survol uniquement, et taille minimale de cible 44 × 44 px — tout cela reflète une pensée WCAG délibérée. Cependant, trois échecs réels existent — la bordure de champ de saisie et le changement de survol sur les lignes de liste passent bien en dessous du seuil 3:1 pour les composants UI (SC 1.4.11), `on-surface-variant` sur `surface-variant` échoue au seuil 4,5:1 pour le texte normal (SC 1.4.3), et la bordure de contour en mode sombre est d'un contraste inutilisable — et plusieurs lacunes significatives restent non traitées, notamment la restauration du focus après fermeture de dialog, la boucle de refocus du scanner comme piège clavier potentiel, la couverture `aria-live` pour les toasts et la mise à jour SSE de la puce de phase, ainsi que les exigences WCAG 2.2 spécifiques à la taille de cible et à l'apparence du focus.

---

## Constats

### PASS — Contraste des couleurs : primaire sur blanc (SC 1.4.3, 1.4.11)

`#C44626` sur blanc donne **4,94:1** (EXPERIENCE.md indique 4,6:1, légèrement sous-estimé, mais le ratio réel passe). Cela couvre le texte normal (labels de boutons, texte actif de la sidebar) et les limites des composants UI où le primaire est utilisé comme bordure/indicateur. Passe AA.

### PASS — Contraste des couleurs : primaire mode sombre sur surface sombre (SC 1.4.3)

`#F07040` sur `#1A0C06` donne **6,46:1**. EXPERIENCE.md indique 5,2:1 — encore sous-estimé, mais le ratio réel offre une marge AA confortable. Couvre le texte principal et les éléments actifs en mode sombre.

### PASS — Contraste des couleurs : pairages de tokens `primary-container` (SC 1.4.3)

`on-primary-container` (`#8C2910`) sur `primary-container` (`#FFF4EE`) = **7,96:1**. Couvre la puce de phase, la puce de statut avertissement, les labels de bouton secondaire et le badge de rôle admin. Tous passent au seuil de texte normal, ce qui importe car `label-sm` (12 px/600) ne qualifie pas comme « grand texte » WCAG (qui requiert 18 pt/24 px normal ou 14 pt/18,67 px gras — 12 px gras correspond à 9 pt).

### PASS — Contraste des couleurs : puces de statut succès et erreur (SC 1.4.3)

Puce succès : `#166534` sur `#F0FDF4` = **6,81:1**. Puce erreur : `#410002` sur `#FFDAD6` = **13,26:1**. Les deux sont bien au-dessus du seuil 4,5:1 pour le texte `label-sm` 12 px gras utilisé.

### PASS — Contraste des couleurs : texte inactif de la sidebar (SC 1.4.3)

Le `rgba(245, 238, 234, 0.65)` semi-transparent composité sur `#2A100A` produit approximativement `rgb(173, 160, 155)`, donnant **7,07:1** contre le fond de la sidebar. Passe AA.

### PASS — Contraste des couleurs : texte principal et tokens `on-surface` (SC 1.4.3)

`on-surface` (`#1A0A05`) sur `surface` (`#FFFBF9`) = **18,74:1**. `on-surface-dark` (`#F5EAE4`) sur `surface-dark` (`#1A0C06`) = **16,17:1**. Les deux sont excellents.

### PASS — Piégeage du focus dans les dialogs (SC 2.1.2)

EXPERIENCE.md spécifie explicitement : « Focus piégé dans le dialog (accessibilité). Focus initial sur le bouton d'annulation (action sûre). » Placer le focus initial sur l'action sûre/annuler est une bonne pratique et correct.

### PASS — Primitives d'activation clavier (SC 2.1.1)

Tab/Shift+Tab, Entrée/Espace pour les boutons et liens, Échap pour les dialogs et popovers sont tous spécifiés. L'interdiction explicite des affordances au survol uniquement est correcte et élimine toute une classe d'échecs d'inaccessibilité au clavier.

### PASS — Labellisation `aria` des éléments interactifs (SC 4.1.2)

Puce de phase : `aria-label="Phase actuelle : Dépôt"` évite le piège d'exposer uniquement le texte de la puce aux lecteurs d'écran. Champ scanner : `aria-label="Scanner ou saisir un code-barres"` est correct. Icônes décoratives : `aria-hidden="true"` est spécifié.

### PASS — Clarté du rôle des icônes (SC 1.1.1)

Les icônes décoratives portent `aria-hidden="true"` ; les icônes significatives doivent être accompagnées d'un label visible ou d'un `aria-label`. Cette règle est posée, bien que son application lors de l'implémentation nécessitera une revue de code.

### PASS — Conception des états vides (esprit SC 3.3.2)

Chaque état vide doit proposer une action — cela évite les impasses et est cohérent avec le fait de fournir une aide lorsqu'une liste attendue est absente, ce qui aide les utilisateurs s'appuyant sur des repères clairs.

---

### PRÉOCCUPATION — Restauration du focus après fermeture de dialog non spécifiée (SC 2.4.3)

EXPERIENCE.md spécifie le piégeage du focus à l'ouverture du dialog et le focus initial sur le bouton Annuler, mais ne dit rien sur l'endroit où le focus revient à la fermeture (que ce soit via Échap, « Annuler » ou « Confirmer »). La SC 2.4.3 WCAG (Ordre du focus) exige que le focus soit rétabli à un emplacement significatif et prévisible — presque toujours l'élément qui a ouvert le dialog. Sans cette spécification, les implémentations risquent de renvoyer le focus en haut de la page ou vers un emplacement indéfini, ce qui désoriente les utilisateurs de lecteurs d'écran et de navigation au clavier.

**Ajout requis :** « À la fermeture du dialog (quelle que soit la cause), rétablir le focus sur l'élément qui a ouvert le dialog. »

### PRÉOCCUPATION — La boucle de refocus du scanner peut constituer un piège clavier (SC 2.1.2)

EXPERIENCE.md spécifie que le champ scanner de la caisse « remet le focus automatiquement après 500 ms d'inactivité clavier ». La SC 2.1.2 interdit les pièges clavier : les utilisateurs doivent pouvoir déplacer le focus depuis n'importe quel composant avec les touches standard. Un refocus automatique agressif vers le champ scanner pourrait empêcher un utilisateur naviguant au clavier seul d'atteindre les lignes du panier, le bouton « Retirer le lot entier » ou le bouton « Valider » sans clic souris.

Le seuil de 500 ms d'inactivité peut être insuffisant pour les utilisateurs qui naviguent lentement. Une meilleure approche : refocaliser le scanner uniquement lorsque l'utilisateur appuie sur une touche dédiée (ex. F2 ou un bouton visible « Retour au scanner »), ou supprimer le refocus automatique pendant une navigation au clavier en cours (détectée par des événements `keydown` qui ne sont pas des saisies de scanner).

**Référence SC WCAG :** 2.1.2 Pas de piège clavier.

### PRÉOCCUPATION — Couverture `aria-live` incomplète pour les toasts et les mises à jour SSE (SC 4.1.3)

La zone de résultat du scanner porte `aria-live="polite"` ce qui est correct. Cependant, les mises à jour dynamiques suivantes n'ont aucune annonce de région live spécifiée :

1. **Messages toast** — les confirmations de succès (« Dépôt enregistré. », « Vendeur réglé. ») et les toasts d'erreur persistants (« L'imprimante ne répond pas. ») sont positionnés en bas à droite et apparaissent dynamiquement. Sans région `aria-live`, les utilisateurs de lecteurs d'écran ne reçoivent aucune annonce.
2. **Mise à jour SSE de la puce de phase** — lorsque la phase change via un événement Server-Sent, le texte de la puce se met à jour (avec un fondu de 150 ms). La puce a un `aria-label` mais aucune spécification n'indique qu'une région `aria-live` annonce le changement. Les utilisateurs de lecteurs d'écran en cours de workflow n'entendront pas que la phase a changé.
3. **Annulation SSE du panier caisse** — le toast persistant « La phase a changé. Votre panier a été annulé. » est persistant, mais le changement de contenu du panier (articles supprimés) nécessite également une annonce indépendante du toast.

**Référence SC WCAG :** 4.1.3 Messages de statut (Niveau AA).

### PRÉOCCUPATION — Anomalie d'ordre de tabulation : sidebar → contenu → topbar (SC 2.4.3)

EXPERIENCE.md indique l'ordre de tabulation comme « Sidebar → contenu principal → topbar actions (ordre DOM correspondant). » Placer la topbar en dernier dans l'ordre DOM (et donc en dernier dans l'ordre de tabulation) est inhabituel : la topbar est visuellement en haut, et les utilisateurs s'attendent à ce que Tab depuis le dernier élément interactif de la topbar entre dans la sidebar ou le contenu principal — pas à devoir tabuler sur toute la page d'abord. Si la topbar est vraiment en dernier dans le DOM, un utilisateur clavier partant de la puce de phase devra tabuler à travers potentiellement des dizaines d'éléments de sidebar et de contenu pour atteindre l'icône de profil.

**Requis :** Vérifier que le repère topbar est accessible tôt dans la séquence de focus — soit via un lien d'évitement, soit en le plaçant en premier dans l'ordre DOM. Envisager un lien « Aller au contenu principal » comme premier élément focalisable (voir SC 2.4.1 ci-dessous).

**Référence SC WCAG :** 2.4.3 Ordre du focus.

### PRÉOCCUPATION — Aucun lien de saut de navigation spécifié (SC 2.4.1)

La mise en page Admin dispose d'une sidebar persistante de 200 px avec six liens de navigation. Sans lien « Aller au contenu principal » comme premier élément focalisable, les utilisateurs clavier et lecteurs d'écran doivent tabuler à travers tous les éléments de la sidebar à chaque chargement de page ou événement de navigation. La SC 2.4.1 WCAG (Ignorer des blocs, Niveau A) exige un mécanisme pour sauter la navigation répétée.

**Ajout requis :** Un lien d'évitement visuellement masqué `<a href="#main-content">Aller au contenu principal</a>` comme premier élément dans le DOM, visible au focus.

### PRÉOCCUPATION — Mises à jour du titre de page à la navigation sous-définies (SC 2.4.2)

EXPERIENCE.md indique « `<title>` mis à jour » à chaque navigation, ce qui est correct en principe. Cependant, la spec ne définit pas le modèle de titre (ex. « Vendeurs — PluriBourse » vs « PluriBourse — Vendeurs »), ni si l'intégration du routeur Angular est gérée via une `TitleStrategy`. Sans implémentation Angular `TitleStrategy`, le `<title>` ne sera pas mis à jour lors de la navigation SPA. Cela doit être une exigence d'implémentation concrète, pas simplement une déclaration.

**Référence SC WCAG :** 2.4.2 Titre de la page.

### PRÉOCCUPATION — Le comptage de liste annoncé via `aria-label` est fragile (SC 1.3.1)

La spec indique « Listes annoncées avec leur nombre d'éléments via `aria-label`. » Utiliser `aria-label` sur un `<ul>` ou `<table>` pour transmettre le comptage est acceptable, mais si la liste est dynamique (le comptage change avec les filtres), le `aria-label` doit également être mis à jour dynamiquement. Avec le chargement par défilement (« scroll infini si nécessaire »), le comptage dans le `aria-label` devient obsolète au fur et à mesure que les éléments se chargent. Aucun mécanisme de mise à jour n'est spécifié.

**Référence SC WCAG :** 1.3.1 Information et relations.

### PRÉOCCUPATION — Taille de cible des éléments de sidebar avec un padding réduit (SC 2.5.8 / WCAG 2.2)

DESIGN.md spécifie des éléments de sidebar avec `padding: '8px 12px'` et `font: body-md (14px/400)`. Une ligne de texte 14 px avec 8 px de padding vertical donne une hauteur effective d'environ 14 × 1,5 (hauteur de ligne) + 16 px = 37 px. C'est en dessous de la taille minimale de cible 44 × 44 px spécifiée. Bien que la SC 2.5.8 WCAG 2.2 (Taille minimale de cible, AA) autorise des exceptions lorsque l'espacement compense, la spec ne précise pas si les éléments de la sidebar ont un espacement suffisant entre eux pour satisfaire l'exception de décalage. Cela nécessite une clarification explicite.

### PRÉOCCUPATION — Les boutons icône seuls dans le panier caisse manquent de noms accessibles (SC 4.1.2)

Le panier caisse spécifie « Suppression individuelle par icône `close` sur chaque ligne. » Un bouton icône seul sans label visible nécessite un `aria-label` pour être accessible au clavier et aux lecteurs d'écran. La spec ne précise pas quel doit être le nom accessible (ex. `aria-label="Retirer [nom de l'article] du panier"`). Sans le nom de l'article dans le label, tous les boutons de suppression seraient annoncés de façon identique (« Fermer » × N), rendant impossible leur distinction sans contexte visuel.

**Référence SC WCAG :** 4.1.2 Nom, rôle, valeur.

### PRÉOCCUPATION — Le mécanisme d'annonce des erreurs de formulaire est incomplet (SC 3.3.1)

« Erreurs de formulaire annoncées via `aria-describedby` » est la bonne technique, mais les liens `aria-describedby` sont statiques — ils connectent un champ à un élément qui existe déjà. La validation inline au `blur` signifie que les éléments d'erreur sont injectés dynamiquement. La spec n'aborde pas si le conteneur d'erreur existe préalablement dans le DOM (masqué jusqu'au besoin) ou s'il est injecté. S'il est injecté, `aria-describedby` ne fonctionnera pas sauf si la référence est ajoutée à l'input au même moment où l'élément d'erreur apparaît. Le champ de formulaire Angular Material gère cela correctement s'il est utilisé tel que prévu, mais la spec devrait noter cette contrainte explicitement.

**Référence SC WCAG :** 3.3.1 Identification des erreurs.

---

### ÉCHEC — Le contraste de la bordure de champ de saisie contre la surface est critiquement bas (SC 1.4.11)

DESIGN.md spécifie la bordure de saisie comme `1.5px solid {colors.outline}` = `#EDE0D8` sur surface `#FFFBF9`. Contraste calculé : **1,26:1**. La SC 1.4.11 WCAG (Contraste non textuel, Niveau AA) requiert **3:1** pour l'indicateur visuel des composants UI, y compris les bordures de champs de saisie.

C'est un échec réel. La bordure est pratiquement invisible sur la surface beige-blanc. À ce ratio, les utilisateurs malvoyants, avec des cataractes ou dans des environnements très lumineux auront du mal à identifier les champs de saisie.

**Correction :** Assombrir le token `outline`. `#78716C` (`on-surface-variant`) sur `#FFFBF9` donne 4,18:1, ce qui passe comme limite de composant UI. Alternativement, utiliser une approche de remplissage par fond (fond `surface-variant` légèrement décalé) pour différencier les inputs de la surface de la page sans dépendre d'une bordure fine — la variante d'input rempli d'Angular Material fait cela.

### ÉCHEC — `on-surface-variant` sur `surface-variant` échoue au seuil de texte normal (SC 1.4.3)

Plusieurs composants utilisent `on-surface-variant` (`#78716C`) comme texte sur des fonds `surface-variant` (`#F5EEEA`) :

- **Lignes de liste** (composant `list-row` : texte sur fond `surface-variant`)
- **Badge de rôle (Bénévole)** : `on-surface-variant` sur `surface-variant`
- **Bouton ghost** : `on-surface-variant` sur transparent (≈ surface `#FFFBF9`) = 4,66:1 — celui-ci passe

Ratio calculé pour `#78716C` sur `#F5EEEA` : **4,18:1**. Cela échoue au seuil 4,5:1 pour la SC 1.4.3 (Contraste minimum, Niveau AA) pour le texte de taille normale. Le badge de rôle utilise `label-sm` (12 px/600 = 9 pt gras), qui n'est pas du grand texte, donc le seuil abaissé de 3:1 pour le grand texte ne s'applique pas.

**Correction :** Assombrir `on-surface-variant` à environ `#6B6461` ou augmenter légèrement le contraste. Alternativement, utiliser `on-surface` (`#1A0A05`) pour le texte dans les lignes de liste `surface-variant` si la hiérarchie visuelle le permet.

### ÉCHEC — Le contraste de la bordure de saisie/contour en mode sombre est critiquement bas (SC 1.4.11)

DESIGN.md définit `outline-dark: '#5C3828'` sur `surface-dark: '#1A0C06'`. Contraste calculé : **1,87:1**. La SC 1.4.11 WCAG requiert 3:1 pour les limites de composants UI. Il s'agit d'un parallèle mode sombre de l'échec de bordure de saisie en mode clair.

**Correction :** Éclaircir le token `outline` en mode sombre. `on-surface-variant-dark` (`#C8B5AE`) sur `surface-dark` atteint 8,80:1 et fonctionnerait comme couleur de bordure, bien qu'il puisse être trop proéminent. Une valeur autour de `#7A5040` ciblerait 3:1+.

---

## Recommandations

Ordonnées par priorité. Les points 1–3 sont des bloquants pour la conformité WCAG 2.2 AA.

**1. [BLOQUANT] Corriger le contraste de la bordure de saisie — mode clair (SC 1.4.11)**
Remplacer `outline: #EDE0D8` par une valeur atteignant ≥ 3:1 contre `#FFFBF9`. Suggestion : `#9E8F89` (3,05:1) comme minimum, `#78716C` (4,18:1) pour une conformité confortable. Mettre à jour le token de composant `input.border` dans DESIGN.md en conséquence.

**2. [BLOQUANT] Corriger le contraste de la bordure de saisie/contour — mode sombre (SC 1.4.11)**
Remplacer `outline-dark: #5C3828` par une valeur atteignant ≥ 3:1 contre `#1A0C06`. Suggestion : `#8A5A44` (~3,2:1). Mettre à jour DESIGN.md.

**3. [BLOQUANT] Corriger le contraste du texte `on-surface-variant` sur `surface-variant` (SC 1.4.3)**
Le ratio 4,18:1 échoue pour le badge de rôle (Bénévole) et le texte principal des lignes de liste. Soit assombrir `on-surface-variant` de `#78716C` à ≈ `#6B6461` (4,5:1+ sur surface-variant), soit utiliser `on-surface` pour le texte dans les conteneurs surface-variant. Mettre à jour les tokens DESIGN.md et vérifier tous les usages de composants.

**4. [ÉLEVÉ] Spécifier la restauration du focus à la fermeture du dialog (SC 2.4.3)**
Ajouter au patron dialog de EXPERIENCE.md : « À la fermeture du dialog (quelle que soit la cause — Annuler, Échap ou confirmer), rétablir le focus sur l'élément qui a déclenché le dialog. »

**5. [ÉLEVÉ] Ajouter un lien de saut de navigation (SC 2.4.1)**
Spécifier un lien d'évitement visuellement masqué, visible au focus, comme premier élément DOM : `<a class="skip-link" href="#main-content">Aller au contenu principal</a>`. Requis pour la mise en page Admin avec sa sidebar persistante.

**6. [ÉLEVÉ] Spécifier `aria-live` pour les notifications toast (SC 4.1.3)**
Ajouter à EXPERIENCE.md : « Le conteneur de toast porte `role='status'` et `aria-live='polite'` pour les toasts de succès ; `role='alert'` et `aria-live='assertive'` pour les toasts d'erreur système persistants. » Cela garantit que les lecteurs d'écran annoncent les messages toast sans nécessiter une surveillance visuelle.

**7. [ÉLEVÉ] Spécifier `aria-live` pour la mise à jour SSE de la puce de phase (SC 4.1.3)**
Ajouter à EXPERIENCE.md : « Lorsque la phase change via SSE, une région `aria-live='polite'` (peut être visuellement masquée) annonce "Phase changée : [nouvelle phase]." » La seule mise à jour de `aria-label` de la puce est insuffisante — les lecteurs d'écran ne relisent pas les éléments dont le `aria-label` change silencieusement.

**8. [ÉLEVÉ] Résoudre le risque de piège clavier du refocus automatique du scanner (SC 2.1.2)**
Remplacer le refocus automatique après 500 ms d'inactivité par une interaction explicite « Retour au scanner » (un bouton avec label ou une touche de raccourci dédiée). Si le refocus automatique est conservé pour des raisons opérationnelles, ajouter un mécanisme d'exception documenté (ex. appuyer deux fois sur Tab, ou un bouton visible « Pause scanner ») qui le désactive temporairement, permettant aux utilisateurs clavier de naviguer dans le panier.

**9. [MOYEN] Définir les noms accessibles pour les boutons de suppression du panier caisse (SC 4.1.2)**
Ajouter à EXPERIENCE.md : « Chaque bouton de suppression d'article dans le panier caisse porte `aria-label='Retirer [nom de l'article] du panier'`. » Cela rend chaque bouton distinguable dans les listes des lecteurs d'écran.

**10. [MOYEN] Vérifier la hauteur minimale des éléments de la sidebar (SC 2.5.8)**
Soit augmenter le `padding` des éléments de sidebar de `8px 12px` à `12px 12px` (donnant ≈ 44 px de hauteur effective pour du texte `body-md` 14 px), soit documenter explicitement que l'espacement entre les éléments satisfait l'exception de décalage de la SC 2.5.8.

**11. [MOYEN] Spécifier l'implémentation `TitleStrategy` pour les titres de page SPA (SC 2.4.2)**
Ajouter une note d'implémentation à EXPERIENCE.md : « Angular `TitleStrategy` doit être implémenté pour mettre à jour `<title>` à chaque route. Modèle : "[Nom de la surface] — PluriBourse". »

**12. [FAIBLE] Clarifier la stratégie de mise à jour `aria-label` pour les listes filtrées/en chargement (SC 1.3.1)**
Spécifier que le comptage `aria-label` sur les conteneurs de liste est mis à jour de façon réactive (via liaison Angular) à chaque changement de filtre ou événement de chargement, et reconnaître que la précision du comptage pendant le chargement par défilement est approximative (« X articles affichés »).

**13. [FAIBLE] Clarifier le patron d'injection d'erreur pour `aria-describedby` (SC 3.3.1)**
Noter dans EXPERIENCE.md : « Les conteneurs d'erreur inline sont pré-rendus avec un contenu vide et `aria-live='polite'` ; ils sont remplis à la validation, pas injectés. Cela garantit que les liens `aria-describedby` restent valides. » `<mat-error>` d'Angular Material gère cela correctement s'il est utilisé de façon cohérente.

**14. [FAIBLE] Vérifier que le style `focus-visible` satisfait la SC 2.4.11 WCAG 2.2 (Apparence du focus)**
WCAG 2.2 a ajouté la SC 2.4.11 (Apparence du focus, Niveau AA) : l'indicateur de focus doit avoir une superficie minimale égale au périmètre du composant sans focus, avec au moins 3:1 de contraste entre les états avec et sans focus. La spec indique « Focus ring hérité du token `{colors.primary}` » — vérifier que l'implémentation du focus ring d'Angular Material satisfait les exigences de 2 px minimum et de superficie, pas seulement l'exigence de couleur.
