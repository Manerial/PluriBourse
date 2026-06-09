# Revue de Cohérence — UX PluriBourse (DESIGN↔EXPERIENCE)
Date : 2026-06-09
Relecteur : Lens Cohérence

## Résumé

Les deux documents sont bien alignés dans leur intention et partagent une terminologie cohérente dans la plupart des domaines. Cependant, trois catégories de défauts réels nécessitent d'être corrigés avant le début de l'implémentation : deux références de tokens non résolues (`{colors.sidebar-bg}` et `{elevation.*}`) qui casseront tout pipeline de thématisation piloté par tokens, une contradiction directe sur la politique de défilement infini vs. pagination, et une déclaration d'autofocus ambiguë dans le Flux 1 qui entre en conflit avec le tableau des Patrons de Composants. Toutes les autres divergences sont des écarts mineurs de formulation ou des lacunes de couverture.

---

## Constats

### PASS — Échelle typographique

Les deux documents nomment et appliquent les mêmes niveaux d'échelle de façon cohérente : `title-lg`, `body-md`, `label-lg`, `label-sm`. Les règles pour les prix (`title-md` / `{colors.primary}`) apparaissent dans DESIGN.md et se reflètent correctement dans les descriptions comportementales des composants dans EXPERIENCE.md.

### PASS — Comportement et labels de la puce de phase

Les labels de phase correspondent exactement dans les deux documents : « Dépôt » · « Vente » · « Post-vente » · « Clôturée ». La règle de cliquabilité (Admin uniquement → panneau de contrôle de phase) est cohérente. L'événement SSE `phase-changed` et la transition fondu 150 ms ne sont mentionnés que dans EXPERIENCE.md (comme détail comportemental), ce qui est correct au regard de la finalité du document.

### PASS — Spec du dialog de confirmation

Les deux documents décrivent la même structure : titre + conséquences + bouton confirmer + bouton annuler/ghost. EXPERIENCE.md ajoute les extras comportementaux corrects (piège focus, Échap ferme, focus initial sur Annuler). Pas de contradiction.

### PASS — Comportement des toasts

Les deux s'accordent : en bas à droite, 4 s pour le succès, persistant pour les erreurs système (imprimante). EXPERIENCE.md ajoute « Max 1 toast simultané » et « bouton Fermer » sur le persistant — les deux sont des détails comportementaux additifs, pas des contradictions.

### PASS — Variantes du badge de rôle

DESIGN.md définit deux variantes (Admin : primary-container / on-primary-container ; Bénévole : surface-variant / on-surface-variant). La table des Patrons de Composants de EXPERIENCE.md référence la Topbar qui inclut « Badge rôle + icône profil » — cohérent, bien que EXPERIENCE.md ne respécifie pas les couleurs du badge (correct, DESIGN.md est propriétaire des couleurs).

### PASS — Cohérence des noms de composants

Les noms suivants correspondent exactement entre les documents : Phase chip, Role badge, Status chips (success/warning/error), Dialog de confirmation, Notification inline, Toast, Sidebar item, Boutons (primary / secondary / ghost).

### PASS — Largeur de la sidebar

DESIGN.md Mise en page : « Sidebar Admin : largeur fixe 200px. » Patrons de Composants EXPERIENCE.md : « Largeur fixe 200px. » Correspondance exacte.

### PASS — Hauteur de la topbar

DESIGN.md : « Topbar : hauteur fixe 56px. » EXPERIENCE.md ne re-spécifie pas la hauteur (correct, la spec visuelle appartient à DESIGN.md). Pas de contradiction.

### PASS — Règle de couleur des boutons destructifs

DESIGN.md : « actions destructives utilisent le style `secondary` avec la couleur `error` — jamais un bouton primaire corail. » EXPERIENCE.md Flux 3 utilise correctement « Confirmer (primary) » pour une confirmation financière non destructive, et la section Dialog des Composants de DESIGN.md mentionne « action confirmée = primary ou error » — cohérent.

### PASS — Référence du token du plancher d'accessibilité

La section Accessibilité de EXPERIENCE.md référence `{colors.primary}` pour le focus ring — ce token existe dans le YAML de DESIGN.md.

---

### PRÉOCCUPATION — Asymétrie de couverture des composants : EXPERIENCE.md n'a pas de contrepartie DESIGN pour plusieurs composants

Les composants suivants apparaissent dans `## Patrons de Composants` de EXPERIENCE.md mais n'ont pas d'entrée dédiée dans `## Composants` de DESIGN.md :

- **Topbar** — DESIGN.md décrit le contenu de la topbar de façon inline dans la section Mise en page, mais aucune clé `topbar` n'existe dans le bloc YAML `components`. Il n'y a pas de spec pour le fond de la topbar, sa bordure ou son token d'ombre.
- **Catalogue / liste filtrée** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle (style de l'en-tête, apparence du champ de filtre, style de la flèche de tri) dans DESIGN.md.
- **Champ scanner** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle dans DESIGN.md (distinct du composant `input` générique : style de ring autofocus, état actif du scanner).
- **Panier caisse** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle dans DESIGN.md (disposition du panneau, ligne de total, placement du bouton Valider).
- **Lot dans le panier** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle.
- **Formulaire dépôt** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle.
- **Fiche Catégories & Tables** — Spec comportementale dans EXPERIENCE.md ; pas de spec visuelle.

À l'inverse, DESIGN.md définit `list-row` (composant YAML) mais EXPERIENCE.md n'a pas d'entrée comportementale dédiée — acceptable car list-row est utilisé implicitement par Catalogue et la liste Vendeurs.

**Sévérité :** Moyenne. L'implémentation peut avancer, mais les développeurs devront prendre des décisions visuelles pour ces composants sans spec ancrée dans des tokens, risquant une incohérence visuelle.

### PRÉOCCUPATION — Couleur de fond de la sidebar non tokenisée

Prose Mise en page DESIGN.md : « Fond sombre `{colors.sidebar-bg}` (#2A100A). »

Le token `{colors.sidebar-bg}` **n'existe pas** dans le bloc YAML `colors` de DESIGN.md. La valeur littérale `#2A100A` n'apparaît nulle part dans le YAML non plus. Le token existant le plus proche est `surface-variant-dark: '#2A1510'` (thème sombre), qui est une valeur différente.

EXPERIENCE.md ne référence pas `{colors.sidebar-bg}` directement (l'entrée Sidebar indique seulement la largeur et le comportement), donc il n'y a pas de référence non résolue dans EXPERIENCE.md — mais la prose de DESIGN.md elle-même cite un token qu'elle ne définit pas.

**Sévérité :** Moyenne-élevée. Tout système de thématisation consommant le YAML échouera à résoudre cette référence.

### PRÉOCCUPATION — Divergence de formulation : « Notification d'erreur inline » vs « Notification inline »

Titre de section `## Composants` de DESIGN.md : **« Notification d'erreur inline »**
Entrée du tableau Patrons de Composants de EXPERIENCE.md : **« Notification inline »**

La description dans DESIGN.md restreint ce composant aux « erreurs métier (article déjà vendu, lot incomplet). » EXPERIENCE.md élargit le label à simplement « Notification inline » avec usage « Erreurs métier dans le flux (POS, dépôt) » — même périmètre mais le nom diffère d'un mot.

**Sévérité :** Faible. Pas d'impact fonctionnel, mais le nom de fichier et le sélecteur du composant Angular devraient adopter un nom canonique unique pour éviter la divergence.

### PRÉOCCUPATION — Couleur de la puce de statut dans Patterns d'État EXPERIENCE.md : « rouge » vs « orange »

`## Patterns d'État` de EXPERIENCE.md :
- « Conflit POS (article déjà vendu) » → « Notification inline **rouge** sous le scanner »
- « Lot incomplet » → « Notification inline **orange** dans le panier »

DESIGN.md définit :
- `status-chip-warning` : utilise `{colors.primary-container}` / `{colors.on-primary-container}` (corail doux) — ce qui correspond à orange-ish
- `status-chip-error` : utilise `{colors.error-container}` / `{colors.on-error-container}` (rouge)

EXPERIENCE.md Flux 2 utilise également « Notification inline **orange** » pour lot incomplet, et la table des Patrons de Composants de DESIGN.md décrit le lot incomplet comme notification inline orange. Le token de puce de statut avertissement est corail/orange — c'est cohérent.

Cependant, les Patterns d'État de EXPERIENCE.md indiquent que le conflit POS est « rouge » (notification inline) alors que le composant décrit dans DESIGN.md pour les erreurs inline utilise `{colors.primary-container}` (corail/orange). Une notification inline « rouge » nécessiterait le style `status-chip-error`, mais le composant Notification d'erreur inline n'utilise que le style primary-container. Il y a une lacune potentielle : aucune spec visuelle n'existe pour une notification inline **rouge**, seulement orange.

**Sévérité :** Moyenne. L'équipe d'implémentation a besoin d'une clarification : un conflit d'article POS déclenche-t-il une notification inline rouge (style erreur) ou orange (style avertissement) ?

---

### ÉCHEC — Référence de token non résolue : `{elevation.level-2}` et `{elevation.level-3}` dans DESIGN.md YAML

Le bloc YAML `components` de DESIGN.md référence :
- `card.shadow: '{elevation.level-2}'` (ligne 156)
- `dialog.shadow: '{elevation.level-3}'` (ligne 180)

Il n'y a pas de clé `elevation` dans le frontmatter YAML de DESIGN.md. Les valeurs d'élévation ne sont décrites que dans la prose (section `## Élévation & Profondeur` de DESIGN.md) mais jamais définies comme tokens YAML.

EXPERIENCE.md ne référence pas directement les tokens `{elevation.*}`, mais hérite de ces références cassées indirectement via les tokens de composants `card` et `dialog` sur lesquels il s'appuie.

**Référence fichier :** DESIGN.md lignes 156, 180.

**Sévérité :** Élevée / Bloquant pour la thématisation pilotée par tokens. Tout système résolvant `{elevation.level-2}` depuis le YAML échouera.

### ÉCHEC — Contradiction directe : politique de défilement infini

`## Patrons de Composants`, entrée Catalogue/liste filtrée de EXPERIENCE.md :
> « Pas de pagination en v1 — **scroll infini si nécessaire** (volume modeste ~1 700 articles). »

`## Primitives d'Interaction`, section Interdit de EXPERIENCE.md :
> « **Infinite scroll** (utiliser pagination ou chargement complet pour les volumes PluriBourse) »

Ces deux déclarations se contredisent directement au sein du même document. L'une impose le défilement infini pour les catalogues ; l'autre l'interdit explicitement.

**Référence fichier :** EXPERIENCE.md lignes 104 et 153.

**Sévérité :** Élevée / Bloquant pour l'implémentation du catalogue POS et de la liste articles. L'équipe ne peut pas implémenter les deux.

### ÉCHEC — Incohérence d'autofocus : champ scanner dans le flux de dépôt (Flux 1)

`## Patrons de Composants`, entrée Champ scanner de EXPERIENCE.md :
> « Champ auto-focused à l'ouverture de la **caisse** (POS). »

`## Flux Clés`, Flux 1 (Dépôt) de EXPERIENCE.md :
> « Sophie arrive sur `/volunteer/deposit`. **Scanner input autofocused.** »

La surface de dépôt (`/volunteer/deposit`) est un formulaire de dépôt, pas la caisse. Le tableau des Patrons de Composants scopes le champ scanner exclusivement à la caisse POS. Si `/volunteer/deposit` a également un scanner autofocused, il doit être spécifié comme second contexte d'utilisation du champ scanner. Si l'autofocus du Flux 1 fait référence au champ de recherche de vendeur plutôt qu'à un scanner à code-barres, le texte du flux est trompeur.

**Référence fichier :** EXPERIENCE.md lignes 105 et 175.

**Sévérité :** Élevée. Le dépôt et la caisse POS sont des surfaces fondamentalement différentes ; confondre le comportement du scanner entraînera des erreurs d'implémentation.

---

## Recommandations

Ordonnées par priorité. Bloquants en premier.

1. **(Bloquant) Résoudre la contradiction de défilement infini** — Choisir une politique unique pour toutes les surfaces de liste et mettre à jour EXPERIENCE.md pour être cohérent. Recommandation : charger tous les enregistrements (chargement complet) pour le volume de ~1 700 articles, sans pagination ni défilement infini. Supprimer la clause « scroll infini si nécessaire » des Patrons de Composants et l'interdiction « Infinite scroll » de la liste Interdit (remplacer par la règle positive choisie).

2. **(Bloquant) Définir les tokens `elevation` dans le YAML de DESIGN.md** — Ajouter un bloc `elevation` au frontmatter YAML avec les clés `level-1`, `level-2`, `level-3` correspondant aux valeurs d'ombre de la section prose `## Élévation & Profondeur`. Exemple :
   ```yaml
   elevation:
     level-1: '0 1px 4px rgba(28,10,5,.08)'
     level-2: '0 4px 16px rgba(28,10,5,.14), 0 1px 4px rgba(28,10,5,.08)'
     level-3: '0 8px 24px rgba(28,10,5,.18), 0 2px 6px rgba(28,10,5,.10)'
   ```

3. **(Bloquant) Définir le token `colors.sidebar-bg` dans le YAML de DESIGN.md** — Ajouter `sidebar-bg: '#2A100A'` au bloc `colors`, ou remplacer la référence dans la prose par un token existant (`surface-variant-dark` est proche mais pas identique — vérifier l'intention avant d'en faire un alias).

4. **(Bloquant) Clarifier l'autofocus dans le Flux 1 (Dépôt)** — Soit : (a) étendre la spec du composant Champ scanner pour couvrir `/volunteer/deposit` avec son propre comportement d'autofocus et son périmètre, soit (b) corriger le Flux 1 pour indiquer « Champ de recherche vendeur autofocused » (pas le champ scanner). Cette distinction est critique pour l'architecture de composants Angular.

5. **(Moyen) Clarifier la couleur de la notification inline pour le conflit d'article POS** — Décider si le conflit article-déjà-vendu utilise le style de notification inline erreur (rouge) ou avertissement (orange), et ajouter une spec visuelle dans `## Composants` de DESIGN.md si une variante rouge de la notification inline est nécessaire.

6. **(Moyen) Ajouter des specs visuelles dans DESIGN.md pour les composants spécifiques au POS** — Topbar, Panier caisse, Lot dans le panier, Champ scanner et Formulaire dépôt devraient chacun avoir au minimum une entrée YAML dans `components` (fond, padding, références de couleur clés) pour que les développeurs aient une référence ancrée dans des tokens et n'improvisent pas.

7. **(Faible) Aligner le nom du composant** — Standardiser sur « Notification d'erreur inline » (DESIGN.md) ou « Notification inline » (EXPERIENCE.md) dans les deux documents et dans le nommage du composant Angular (`notification-inline.component.ts` ou `error-notification-inline.component.ts`).
