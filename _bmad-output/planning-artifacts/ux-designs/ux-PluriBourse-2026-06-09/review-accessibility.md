# Revue Accessibilité — UX PluriBourse
Date : 2026-06-12
Relecteur : Lens Accessibilité

---

## Résumé

Cette revue rafraîchit le rapport du 2026-06-09 à la lumière des mises à jour apportées à DESIGN.md (tokens de couleur corrigés) et de l'extension substantielle de EXPERIENCE.md (2026-06-12 — quatorze nouveaux composants, deux nouveaux Key Flows). Deux des trois échecs originaux sont résolus par les ajustements de tokens : `on-surface-variant` sur `surface-variant` passe maintenant 4,99:1 (seuil 4,5:1 SC 1.4.3 ✓) ; `outline-dark` sur `surface-dark` passe maintenant 3,37:1 (seuil 3:1 SC 1.4.11 ✓). L'échec de la bordure de champ de saisie en mode clair persiste : `outline` `#C8B0A4` sur `surface` `#FFFBF9` donne **1,99:1**, encore très en dessous du seuil 3:1. Les nouveaux composants ajoutent plusieurs lacunes de priorité élevée : les dialogs de confirmation manquent systématiquement de spécification de restauration du focus à la fermeture, les nouveaux boutons icône-seuls n'ont pas de noms accessibles définis, et le panneau de solde vendeur introduit un cas d'erreur inline sans annonce `aria-live`. Sur les dix préoccupations originales, sept subsistent, deux sont partiellement atténuées, et une seule est pleinement résolue (le texte de Flow 1 sur le focus initial du scanner est corrigé).

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

### PASS — Contraste des couleurs : `on-surface-variant` sur `surface-variant` [RÉSOLU depuis 2026-06-09] (SC 1.4.3)

Précédemment ÉCHEC. `on-surface-variant` a été assombri de `#78716C` à `#6B6460`. Ratio recalculé : `#6B6460` sur `#F5EEEA` = **4,99:1**. Passe le seuil 4,5:1 pour le texte normal (SC 1.4.3 AA). Couvre les lignes de liste (`list-row`), le badge de rôle Bénévole, et le texte des champs désactivés (ex : "Taux gelé pour cette édition."). Le bouton ghost (`on-surface-variant` sur `surface` `#FFFBF9`) atteint **5,53:1** — passe également. **Résolu.**

### PASS — Contraste des couleurs : bordure de contour mode sombre [RÉSOLU depuis 2026-06-09] (SC 1.4.11)

Précédemment ÉCHEC. `outline-dark` a été éclairci de `#5C3828` à `#8C5840`. Ratio recalculé : `#8C5840` sur `surface-dark` `#1A0C06` = **3,37:1**. Passe le seuil 3:1 pour les composants UI (SC 1.4.11 AA). **Résolu.**

### PASS — Piégeage du focus dans les dialogs (SC 2.1.2)

EXPERIENCE.md spécifie explicitement : « Focus piégé dans le dialog (accessibilité). Focus initial sur le bouton d'annulation (action sûre). » Ce patron est correctement repris dans les nouveaux composants qui définissent des dialogs de confirmation (Action Nettoyer l'édition, Formulaire de solde vendeur, Contrôle de phase — retour arrière, Fiche vendeur admin — suppression RGPD). Le piégeage est confirmé pour tous. Passe.

### PASS — Primitives d'activation clavier (SC 2.1.1)

Tab/Shift+Tab, Entrée/Espace pour les boutons et liens, Échap pour les dialogs et popovers sont tous spécifiés. L'interdiction explicite des affordances au survol uniquement est correcte et élimine toute une classe d'échecs d'inaccessibilité au clavier.

### PASS — Labellisation `aria` des éléments interactifs (SC 4.1.2)

Puce de phase : `aria-label="Phase actuelle : Dépôt"` évite le piège d'exposer uniquement le texte de la puce aux lecteurs d'écran. Champ scanner : `aria-label="Scanner ou saisir un code-barres"` est correct. Icônes décoratives : `aria-hidden="true"` est spécifié.

### PASS — Clarté du rôle des icônes (SC 1.1.1)

Les icônes décoratives portent `aria-hidden="true"` ; les icônes significatives doivent être accompagnées d'un label visible ou d'un `aria-label`. Cette règle est posée. Application lors de l'implémentation à vérifier (voir PRÉOCCUPATION sur les nouveaux boutons icône-seuls).

### PASS — Conception des états vides (esprit SC 3.3.2)

Chaque état vide propose une action — cela évite les impasses et est cohérent avec le fait de fournir une aide lorsqu'une liste attendue est absente. Les nouveaux états vides (Catalogue post-Nettoyage : "Édition nettoyée — les articles ont été supprimés. Seules les métriques agrégées sont disponibles.") ne proposent volontairement aucune action, ce qui est justifié et décrit clairement.

### PASS — Texte de focus autofocus Flow 1 corrigé [RÉSOLU depuis 2026-06-09] (SC 2.4.3)

Flow 1 indiquait précédemment "Scanner input autofocused" pour l'étape 1 ; le texte indique maintenant correctement "Le champ de recherche vendeur reçoit le focus à l'ouverture." — cohérent avec le formulaire de dépôt (pas le scanner). **Résolu.**

---

### PRÉOCCUPATION — Restauration du focus après fermeture de dialog non spécifiée pour tous les nouveaux dialogs (SC 2.4.3)

La préoccupation originale (2026-06-09) reste non résolue dans la spec de base, et est maintenant amplifiée par les nouveaux composants qui définissent des dialogs supplémentaires : Action Nettoyer l'édition, Formulaire de solde vendeur (dialog "montant inférieur"), Contrôle de phase — retour arrière, et Fiche vendeur admin — suppression RGPD. Aucun d'entre eux ne spécifie où le focus revient à la fermeture.

La SC 2.4.3 WCAG exige que le focus soit rétabli à un emplacement significatif et prévisible — presque toujours l'élément qui a ouvert le dialog.

**Ajout requis dans le patron "Dialog de confirmation" de la section Component Patterns :** « À la fermeture du dialog (quelle que soit la cause — Annuler, Échap, ou confirmation), rétablir le focus sur l'élément déclencheur du dialog. »

### PRÉOCCUPATION — La boucle de refocus du scanner peut constituer un piège clavier (SC 2.1.2)

Préoccupation originale non résolue. EXPERIENCE.md spécifie toujours que le champ scanner « remet le focus automatiquement après 500 ms d'inactivité clavier ». La SC 2.1.2 interdit les pièges clavier. Un refocus automatique agressif peut empêcher les utilisateurs naviguant au clavier seul d'atteindre les lignes du panier ou les boutons d'action.

**Référence SC WCAG :** 2.1.2 Pas de piège clavier.

### PRÉOCCUPATION — Couverture `aria-live` incomplète pour les toasts, SSE et formulaire de solde (SC 4.1.3)

Préoccupation originale non résolue, élargie par les nouveaux composants :

1. **Messages toast** — les confirmations de succès et les toasts d'erreur système persistent sans région `aria-live` spécifiée. Sans cette annotation, les lecteurs d'écran n'annoncent pas les messages toast.
2. **Mise à jour SSE de la puce de phase** — aucune région `aria-live` dédiée à l'annonce du changement de phase.
3. **Annulation SSE du panier caisse** — le changement de contenu du panier nécessite une annonce indépendante du toast.
4. **Nouveau — Formulaire de solde vendeur :** le message inline "Le montant saisi dépasse le reversement dû." qui bloque le bouton "Valider" est une erreur dynamique critique qui n'a aucune annotation `aria-live` ni `aria-describedby` spécifiée. Les utilisateurs de lecteurs d'écran ne sauront pas pourquoi le bouton est bloqué.

**Référence SC WCAG :** 4.1.3 Messages de statut (Niveau AA) ; 3.3.1 Identification des erreurs (Niveau A) pour le point 4.

### PRÉOCCUPATION — Anomalie d'ordre de tabulation : sidebar → contenu → topbar (SC 2.4.3)

Préoccupation originale non résolue. EXPERIENCE.md maintient l'ordre de tabulation « Sidebar → contenu principal → topbar actions (ordre DOM correspondant). » Placer la topbar en dernier dans l'ordre DOM reste inhabituel. Un utilisateur clavier partant de la puce de phase devra tabuler à travers potentiellement des dizaines d'éléments avant d'atteindre l'icône de profil.

**Référence SC WCAG :** 2.4.3 Ordre du focus.

### PRÉOCCUPATION — Aucun lien de saut de navigation spécifié (SC 2.4.1)

Préoccupation originale non résolue. La mise en page Admin dispose d'une sidebar persistante de 200 px avec plusieurs liens de navigation. Sans lien « Aller au contenu principal » comme premier élément focalisable, les utilisateurs clavier et lecteurs d'écran doivent tabuler à travers tous les éléments de la sidebar à chaque chargement de page.

**Ajout requis :** Un lien d'évitement visuellement masqué `<a href="#main-content">Aller au contenu principal</a>` comme premier élément dans le DOM, visible au focus.

### PRÉOCCUPATION — Mises à jour du titre de page à la navigation sous-définies (SC 2.4.2)

Préoccupation originale non résolue. La spec indique « `<title>` mis à jour » à chaque navigation, mais sans définir le modèle ni l'intégration Angular `TitleStrategy`. Les nouvelles surfaces (Rapports, Reversements, Paramètres instance, Page compte, Premier lancement, Contrôle de phase — retour arrière) ne mentionnent pas leur titre de page.

**Référence SC WCAG :** 2.4.2 Titre de la page.

### PRÉOCCUPATION — Le comptage de liste annoncé via `aria-label` est fragile (SC 1.3.1)

Préoccupation originale non résolue. La Page Reversements ajoute une liste paginée filtrée (filtre "tous / non soldés / soldés") dont les `aria-label` de comptage devront être mis à jour dynamiquement à chaque changement de filtre. Aucun mécanisme n'est spécifié.

**Référence SC WCAG :** 1.3.1 Information et relations.

### PRÉOCCUPATION — Taille de cible des éléments de sidebar avec un padding réduit (SC 2.5.8 / WCAG 2.2)

Préoccupation originale non résolue. DESIGN.md spécifie des éléments de sidebar avec `padding: '8px 12px'` et `font: body-md (14px/400)`. Hauteur effective : ≈ 37 px, en dessous de 44 px (SC 2.5.8 WCAG 2.2).

### PRÉOCCUPATION — Boutons icône-seuls dans le panier caisse : noms accessibles non définis (SC 4.1.2)

Préoccupation originale non résolue. Le panier caisse spécifie « Suppression individuelle par icône `close` sur chaque ligne. » Aucun `aria-label` n'est défini, rendant tous les boutons identiques pour un lecteur d'écran.

**Référence SC WCAG :** 4.1.2 Nom, rôle, valeur.

### PRÉOCCUPATION — Le mécanisme d'annonce des erreurs de formulaire est incomplet (SC 3.3.1)

Préoccupation originale non résolue. « Erreurs de formulaire annoncées via `aria-describedby` » sans préciser si le conteneur d'erreur est pré-rendu ou injecté. Cette lacune est amplifiée par les nouveaux formulaires (Page Paramètres, Fiche édition, Page compte utilisateur, Premier lancement, Formulaire de solde vendeur).

**Référence SC WCAG :** 3.3.1 Identification des erreurs.

---

### PRÉOCCUPATION [NOUVEAU] — Nouveaux boutons icône-seuls sans noms accessibles définis (SC 4.1.2)

Les composants ajoutés en 2026-06-12 introduisent plusieurs boutons icône-seuls dont aucun `aria-label` n'est spécifié :

- **Action Nettoyer l'édition** : le bouton "Supprimer" dans le dialog de confirmation est un bouton texte — acceptable. Mais le bouton "Nettoyer l'édition" sur la fiche édition est décrit sans `aria-label` distinct.
- **Récapitulatif reversement imprimable** : le bouton "Imprimer le récapitulatif" porte une icône `print` et un label texte — acceptable. Cependant, le comportement spinner-dans-le-bouton pendant la mise en queue doit maintenir le nom accessible (l'état de chargement ne doit pas vider le label du bouton via un `innerHTML` spinner-only).
- **Fiche vendeur admin — suppression RGPD** : le bouton déclencheur en bas de fiche est décrit sans `aria-label`.
- **Formulaire de solde vendeur** : le bouton "Solder" sur chaque ligne de la Page Reversements est accessible depuis un contexte de liste — si c'est un bouton icône ou un bouton contextuel sans label explicite incluant le nom du vendeur, il sera indiscernable pour un lecteur d'écran ("Solder" × N).

**Référence SC WCAG :** 4.1.2 Nom, rôle, valeur.

### PRÉOCCUPATION [NOUVEAU] — Premier lancement : navigation bloquée sans annonce d'accessibilité (SC 3.3.2, SC 4.1.3)

Le composant "Premier lancement — changement de mot de passe forcé" décrit un mécanisme de blocage de navigation : « toute navigation vers une autre route redirige vers `/account/force-password` tant que le changement n'est pas effectué. » Le message "Vous devez changer votre mot de passe avant de continuer." est décrit comme bannière, mais :

1. La spec ne précise pas que la bannière porte `role="alert"` ou `aria-live="assertive"` pour l'annoncer immédiatement à la connexion.
2. La redirection silencieuse vers `/account/force-password` en cas de tentative de navigation ailleurs n'est pas spécifiée comme une annonce — un utilisateur lecteur d'écran qui tente de naviguer n'entendra pas d'explication.

**Référence SC WCAG :** 3.3.2 Étiquettes ou instructions (Niveau A) ; 4.1.3 Messages de statut (Niveau AA).

### PRÉOCCUPATION [NOUVEAU] — Contrôle de phase — retour arrière : état désactivé sans annonce accessible (SC 4.1.2)

Le composant "Contrôle de phase — retour arrière" décrit deux états pour le bouton de retour arrière après Nettoyage : le bouton est absent et remplacé par un message inline avec icône `lock`. Ce message est décrit comme étant « en style `{colors.on-surface-variant}` avec icône `lock` » — aucune annotation `aria-live` ni `role` n'est spécifiée pour annoncer ce changement d'état si la page est déjà ouverte. De plus, si l'état post-Nettoyage est chargé directement (navigation directe vers `/admin/editions/:id/phase`), le message doit être announcé à l'entrée sur la page.

**Référence SC WCAG :** 4.1.2 Nom, rôle, valeur.

### PRÉOCCUPATION [NOUVEAU] — Page Rapports : boutons CSV sans retour accessible sur le téléchargement (SC 4.1.3)

Les boutons "Exporter le catalogue" et "Exporter les reversements" de la Page Rapports déclenchent un « téléchargement direct sans dialog ». Aucun feedback d'accessibilité n'est spécifié : ni spinner dans le bouton, ni toast de confirmation, ni `aria-live`. Un utilisateur lecteur d'écran ne saura pas si le téléchargement a démarré ou échoué.

**Référence SC WCAG :** 4.1.3 Messages de statut (Niveau AA).

---

### ÉCHEC — Le contraste de la bordure de champ de saisie contre la surface en mode clair est encore insuffisant (SC 1.4.11)

DESIGN.md a amélioré le token `outline` de `#EDE0D8` à `#C8B0A4`, mais le ratio reste en échec.

Ratio recalculé : `#C8B0A4` sur `surface` `#FFFBF9` = **1,99:1**. La SC 1.4.11 WCAG requiert **3:1** pour l'indicateur visuel des composants UI. La correction a presque doublé le ratio d'origine (1,26:1 → 1,99:1), mais reste insuffisante.

Pour atteindre 3:1 contre `#FFFBF9`, la valeur cible doit être ≈ `#9E8C86` minimum (3,01:1) ou `#8A7870` pour une marge confortable (≈ 3,5:1). La suggestion `#9E8F89` du rapport précédent reste valable.

Ce défaut affecte tous les champs de saisie de l'application : formulaire de dépôt, recherche vendeur, formulaire lot, formulaire de solde vendeur, Page Paramètres instance, Page compte utilisateur, Premier lancement — changement de mot de passe, Fiche catégories & tables, Fiche édition. L'étendue est maximale.

---

## Recommandations

Ordonnées par priorité. Le point 1 est un bloquant pour la conformité WCAG 2.2 AA.

**1. [BLOQUANT] Corriger le contraste de la bordure de saisie — mode clair (SC 1.4.11)**
Remplacer `outline: #C8B0A4` par une valeur atteignant ≥ 3:1 contre `#FFFBF9`. Valeur minimale : `#9E8C86` (3,01:1) ; valeur recommandée : `#8A7870` (≈ 3,5:1) pour une marge de sécurité. Mettre à jour le token `colors.outline` dans DESIGN.md. Ce token est référencé par `input.border` et `outline-variant` — vérifier tous les usages.

**2. [ÉLEVÉ] Spécifier la restauration du focus à la fermeture de tous les dialogs (SC 2.4.3)**
Ajouter au patron "Dialog de confirmation" dans EXPERIENCE.md, section Component Patterns : « À la fermeture du dialog (quelle que soit la cause — bouton Annuler, touche Échap, ou bouton de confirmation), rétablir le focus sur l'élément déclencheur du dialog. » Ce patron s'applique à tous les dialogs documentés : transitions de phase, Action Nettoyer, suppression RGPD, retour arrière de phase, solde vendeur inférieur.

**3. [ÉLEVÉ] Ajouter un lien de saut de navigation (SC 2.4.1)**
Spécifier un lien d'évitement visuellement masqué, visible au focus, comme premier élément DOM : `<a class="skip-link" href="#main-content">Aller au contenu principal</a>`. Requis pour la mise en page Admin avec sa sidebar persistante.

**4. [ÉLEVÉ] Spécifier `aria-live` pour les notifications toast (SC 4.1.3)**
Ajouter à EXPERIENCE.md, section Component Patterns, entrée Toast : « Le conteneur toast porte `role='status'` et `aria-live='polite'` pour les toasts de succès (4s) ; `role='alert'` et `aria-live='assertive'` pour les toasts d'erreur système persistants. »

**5. [ÉLEVÉ] Spécifier `aria-live` pour la mise à jour SSE de la puce de phase (SC 4.1.3)**
Ajouter à EXPERIENCE.md : « Lorsque la phase change via SSE, une région `aria-live='polite'` (peut être visuellement masquée) annonce "Phase changée : [nouvelle phase]." » La seule mise à jour de `aria-label` de la puce est insuffisante — les lecteurs d'écran ne relisent pas les éléments dont le `aria-label` change silencieusement.

**6. [ÉLEVÉ] Spécifier le feedback accessible pour les erreurs du formulaire de solde vendeur (SC 4.1.3, 3.3.1)**
Ajouter au composant "Formulaire de solde vendeur" dans EXPERIENCE.md : « Le message d'erreur inline "Le montant saisi dépasse le reversement dû." est contenu dans un élément portant `aria-live='polite'` et lié au champ de saisie via `aria-describedby`. » Cela garantit que les utilisateurs lecteurs d'écran reçoivent l'annonce sans action supplémentaire.

**7. [ÉLEVÉ] Résoudre le risque de piège clavier du refocus automatique du scanner (SC 2.1.2)**
Remplacer le refocus automatique après 500 ms d'inactivité par une interaction explicite « Retour au scanner » (bouton avec label ou touche de raccourci dédiée). Si le refocus automatique est conservé pour des raisons opérationnelles, documenter un mécanisme d'exception (ex. double-Tab) qui le désactive temporairement, permettant aux utilisateurs clavier de naviguer dans le panier.

**8. [ÉLEVÉ] Spécifier `role="alert"` pour la bannière de Premier lancement (SC 4.1.3)**
Ajouter au composant "Premier lancement — changement de mot de passe forcé" : « La bannière "Vous devez changer votre mot de passe avant de continuer." porte `role='alert'` pour être annoncée immédiatement par les lecteurs d'écran à l'arrivée sur la page. »

**9. [MOYEN] Définir les noms accessibles pour les boutons d'action contextuels dans les listes (SC 4.1.2)**
Pour tous les boutons répétés dans les listes (Page Reversements : "Solder", "Imprimer le bilan", "Non réclamé" × N lignes), spécifier que le nom accessible inclut le contexte : `aria-label="Solder [prénom nom]"`, `aria-label="Imprimer le bilan de [prénom nom]"`. Pour le bouton `close` du panier caisse : `aria-label="Retirer [nom de l'article] du panier"`.

**10. [MOYEN] Spécifier le feedback accessible pour les boutons CSV de la Page Rapports (SC 4.1.3)**
Ajouter au composant "Page Rapports" : spinner dans le bouton pendant le déclenchement du téléchargement + toast succès "Export en cours de téléchargement." (4s) ou toast persistant en cas d'erreur, avec région `aria-live` standard.

**11. [MOYEN] Vérifier la hauteur minimale des éléments de la sidebar (SC 2.5.8)**
Soit augmenter le `padding` des éléments de sidebar de `8px 12px` à `12px 12px` (donnant ≈ 44 px de hauteur effective pour du texte `body-md` 14 px), soit documenter explicitement que l'espacement entre les éléments satisfait l'exception de décalage de la SC 2.5.8.

**12. [MOYEN] Spécifier l'implémentation `TitleStrategy` pour les titres de page SPA (SC 2.4.2)**
Ajouter une note d'implémentation à EXPERIENCE.md : « Angular `TitleStrategy` doit être implémenté pour mettre à jour `<title>` à chaque route. Modèle : "[Nom de la surface] — PluriBourse". » Définir les titres pour toutes les nouvelles surfaces (Rapports, Reversements, Paramètres instance, Compte utilisateur, Premier lancement, Contrôle de phase).

**13. [FAIBLE] Clarifier la stratégie de mise à jour `aria-label` pour les listes filtrées/en chargement (SC 1.3.1)**
Spécifier que le comptage `aria-label` sur les conteneurs de liste (dont la Page Reversements avec son filtre de statut) est mis à jour de façon réactive (liaison Angular) à chaque changement de filtre ou événement de chargement.

**14. [FAIBLE] Clarifier le patron d'injection d'erreur pour `aria-describedby` (SC 3.3.1)**
Noter dans EXPERIENCE.md : « Les conteneurs d'erreur inline sont pré-rendus avec un contenu vide et `aria-live='polite'` ; ils sont remplis à la validation, pas injectés. Cela garantit que les liens `aria-describedby` restent valides. » `<mat-error>` d'Angular Material gère cela correctement s'il est utilisé de façon cohérente.

**15. [FAIBLE] Vérifier que le style `focus-visible` satisfait la SC 2.4.11 WCAG 2.2 (Apparence du focus)**
WCAG 2.2 a ajouté la SC 2.4.11 (Apparence du focus, Niveau AA) : l'indicateur de focus doit avoir une superficie minimale égale au périmètre du composant sans focus, avec au moins 3:1 de contraste entre les états avec et sans focus. La spec indique « Focus ring hérité du token `{colors.primary}` » — vérifier que l'implémentation du focus ring d'Angular Material satisfait les exigences de 2 px minimum et de superficie, pas seulement l'exigence de couleur.

**16. [FAIBLE] Annoter l'état désactivé du Contrôle de phase — retour arrière (SC 4.1.2)**
Ajouter au composant "Contrôle de phase — retour arrière" : « Le message inline "Retour en arrière indisponible — l'édition a été nettoyée." est contenu dans un élément avec `role='status'` pour être détectable par les technologies d'assistance lors d'un chargement direct de la page. »
