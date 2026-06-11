# Revue de Cohérence — UX PluriBourse (DESIGN↔EXPERIENCE)
Date : 2026-06-12
Relecteur : Lens Cohérence

## Résumé

Les trois ÉCHECS de la revue du 2026-06-09 ont été résolus : les tokens `{elevation.*}` sont maintenant définis dans le YAML de DESIGN.md, `{colors.sidebar-bg}` est présent dans le bloc `colors`, et la contradiction infinite scroll / pagination a été levée. L'autofocus ambigu du Flux 1 a également été corrigé. En revanche, les ajouts de composants du 2026-06-12 introduisent un nouvel ÉCHEC (`{colors.warning}` non défini) et deux nouvelles PRÉOCCUPATIONS, et plusieurs PRÉOCCUPATIONS de l'ancienne revue restent ouvertes.

---

## Constats

### PASS — Tokens `{elevation.*}` définis (résolution ÉCHEC #1 de 2026-06-09)

Le frontmatter YAML de DESIGN.md contient maintenant le bloc `elevation:` avec les clés `level-1`, `level-2`, `level-3` et leurs valeurs d'ombre correspondantes. Les références `{elevation.level-2}` (card) et `{elevation.level-3}` (dialog) dans le YAML `components` se résolvent correctement.

### PASS — Token `{colors.sidebar-bg}` défini (résolution ÉCHEC #2 de 2026-06-09)

Le bloc YAML `colors` de DESIGN.md contient maintenant `sidebar-bg: '#2A100A'`. La prose de DESIGN.md Mise en page référence ce token ; il se résout correctement.

### PASS — Contradiction infinite scroll levée (résolution ÉCHEC #3 de 2026-06-09)

L'entrée `Catalogue / liste filtrée` dans les Patrons de Composants de EXPERIENCE.md indique maintenant « Pagination via `MatPaginator` — page size par défaut 50 », sans mention de scroll infini. La section Interdit continue d'interdire l'infinite scroll. Les deux déclarations sont cohérentes.

### PASS — Autofocus Flux 1 clarifié (résolution PRÉOCCUPATION sérieuse de 2026-06-09)

Le Flux 1 de EXPERIENCE.md indique désormais : « Le champ de recherche vendeur reçoit le focus à l'ouverture. » Il n'y a plus de confusion entre le champ scanner (POS uniquement) et le champ de recherche vendeur (dépôt). La distinction est nette.

### PASS — Échelle typographique

Inchangé depuis la revue précédente. Tous les tokens `{typography.*}` référencés dans EXPERIENCE.md (`{typography.label-lg}` ligne 125) se résolvent vers des clés existantes dans le YAML de DESIGN.md.

### PASS — Comportement et labels de la puce de phase

Cohérence maintenue. Les labels de phase correspondent dans les deux documents.

### PASS — Spec du dialog de confirmation (cas standard)

La structure titre + conséquences + bouton confirmer + bouton annuler/ghost reste cohérente entre les deux documents pour les dialogs de transition de phase et de suppression destructive.

### PASS — Comportement des toasts

Inchangé. Cohérence maintenue sur position, durée et persistance.

### PASS — Tokens `{colors.*}` des nouveaux composants 2026-06-12 (cas résolus)

Les références suivantes dans les nouveaux composants se résolvent correctement vers le YAML de DESIGN.md :
- `{colors.on-surface-variant}` (Contrôle de phase — retour arrière ; Fiche édition — taux de commission ; Formulaire dépôt — table auto-assignée)
- `{colors.primary-container}` (Édition archivée — vue détail)
- `{colors.surface-variant}` (Fiche édition — taux de commission)

### PASS — Nouveaux composants : cohérence des noms

Les composants ajoutés en 2026-06-12 utilisent des noms de composants de base (`button-secondary`, `button-ghost`, `button-primary`, `dialog`, `status-chip-success`, `status-chip-error`) qui correspondent exactement aux entrées du YAML `components` de DESIGN.md. Aucune dérive de nomenclature introduite.

### PASS — Règle boutons destructifs dans les nouveaux composants

Les composants `Fiche vendeur admin — suppression RGPD` et `Action "Nettoyer l'édition"` utilisent correctement le style `secondary` couleur `error` pour les boutons destructifs, conformément à la règle DESIGN.md « jamais un bouton primaire corail pour une action destructive ».

---

### PRÉOCCUPATION — Asymétrie de couverture : 9 nouveaux composants EXPERIENCE.md sans entrée YAML dans DESIGN.md

Les composants suivants, ajoutés le 2026-06-12, n'ont aucune entrée dans le bloc `components` de DESIGN.md :

- **Page Rapports** — pas de spec pour les bannières de section conditionnelle, le style des métriques agrégées, le style du bouton "Actualiser".
- **Action "Nettoyer l'édition"** — pas de spec pour le style de bannière d'avertissement post-Clean.
- **Contrôle de phase — retour arrière** — pas de spec pour le style du message inline verrouillé (icône `lock`).
- **Édition archivée — vue détail** — pas de spec pour les tuiles de métriques agrégées.
- **Catalogue — état post-Nettoyage** — pas de spec pour la disposition de l'état vide spécialisé.
- **Page Paramètres instance** — pas de spec pour le style des notes explicatives sous les champs.
- **Fiche édition — taux de commission** — pas de spec pour l'état désactivé du champ (au-delà des tokens de couleur).
- **Page compte utilisateur** — pas de spec pour le layout à deux sections.
- **Premier lancement — changement de mot de passe forcé** — pas de spec pour la bannière d'invite proéminente.
- **Fiche vendeur admin — suppression RGPD** — pas de spec visuelle pour la zone de danger en bas de fiche.

À ceci s'ajoutent les composants déjà signalés dans la revue précédente et toujours manquants : Topbar (clé YAML absente), Catalogue/liste filtrée (style tri, champ filtre), Scanner input (ring autofocus), Panier POS, Lot dans le panier, Formulaire dépôt.

**Sévérité :** Moyenne. L'implémentation peut avancer avec les maquettes HTML de référence, mais les développeurs manquent d'un ancrage token systématique pour ces surfaces. Le risque d'incohérence visuelle augmente au fil des composants.

**Recommandation :** Ajouter des entrées YAML minimalistes dans DESIGN.md pour les composants les plus complexes (bannières, tuiles métriques, zone danger) avec au minimum fond, bordure et référence de couleur. Les pages simples peuvent rester couvertes par les maquettes.

### PRÉOCCUPATION — Couleur de la notification inline pour le conflit POS : « rouge » non spécifié (persistante depuis 2026-06-09)

`## Patterns d'État` de EXPERIENCE.md :
- « Conflit POS (article déjà vendu) » → « Notification inline **rouge** sous le scanner »
- « Conflit de scan concurrent » → « Notification inline **rouge** sous le scanner »
- « Lot incomplet » → « Notification inline **orange** dans le panier »

DESIGN.md définit la `Notification d'erreur inline` avec fond `{colors.primary-container}` et bordure `{colors.primary}` — ce qui est corail/orange, pas rouge. Aucune variante rouge de la notification inline n'est définie dans DESIGN.md. La distinction rouge/orange n'est toujours pas ancrée dans des tokens.

Par ailleurs, EXPERIENCE.md ligne 121 (`Lot dans le panier`) indique « label du lot en rouge » sans référencer un token — la valeur littérale de rouge reste non spécifiée.

**Sévérité :** Moyenne. Les développeurs doivent décider quelle valeur de rouge employer pour les notifications d'erreur POS, sans référence dans DESIGN.md.

**Recommandation :** Soit (a) ajouter une variante `notification-inline-error` dans DESIGN.md `components` avec `{colors.error-container}` / `{colors.on-error-container}`, soit (b) préciser explicitement dans EXPERIENCE.md que le « rouge » de la notification inline POS utilise les tokens `{colors.error-container}` / `{colors.on-error-container}`. Le label de lot rouge doit également être tokenisé.

### PRÉOCCUPATION — Bouton "Confirmer" en style `secondary` dans le dialog de retour arrière

Composant `Contrôle de phase — retour arrière` dans EXPERIENCE.md :
> « Boutons : "Confirmer" (secondary) + "Annuler" (ghost). »

DESIGN.md `## Composants`, section Dialog de confirmation :
> « action confirmée = primary ou error »

Un bouton de confirmation en style `secondary` (corail outline) diverge de la règle visuelle des dialogs. Le style `secondary` est prévu pour les actions contextuelles importantes mais non primaires, pas pour les confirmations de dialog. Cette incohérence peut semer la confusion : un dialog standard a un bouton corail plein ("Confirmer"), le retour arrière aurait un bouton outline — sans règle explicite justifiant l'exception.

**Sévérité :** Faible à Moyenne. L'exception est compréhensible (retour arrière = action moins engagée qu'une avancée de phase), mais elle doit être soit explicitée comme règle dans DESIGN.md, soit corrigée vers `primary`.

**Recommandation :** Si l'intention est de différencier visuellement le retour arrière de l'avancement (plus rassurant, moins urgent), documenter la règle dans DESIGN.md : « Dans les dialogs de retour arrière non destructif, le bouton de confirmation peut utiliser le style `secondary`. » Sinon, aligner sur `primary`.

---

### ECHEC — Token `{colors.warning}` non défini dans DESIGN.md

Composant `Fiche Catégories & Tables` dans EXPERIENCE.md (ligne 124) :
> « **Mode lecture (phase Dépôt et au-delà) :** bannière `{colors.warning}` … »

Le bloc YAML `colors` de DESIGN.md ne contient aucune clé `warning`. DESIGN.md définit `status-chip-warning` (composant) mais aucun token de couleur de ce nom. Les tokens de couleur existants les plus proches pour une bannière d'avertissement sont `{colors.primary-container}` / `{colors.on-primary-container}` (corail doux, utilisé par `status-chip-warning`).

**Référence fichier :** EXPERIENCE.md ligne 124.

**Sévérité :** Élevée. Toute pipeline de thématisation pilotée par tokens échouera à résoudre `{colors.warning}`. Ce token était absent avant les ajouts du 2026-06-12 et l'est toujours.

**Recommandation :** Soit (a) ajouter `warning: '{colors.primary-container}'` (alias) dans le YAML `colors` de DESIGN.md si la bannière doit se comporter comme un avertissement corail doux, soit (b) remplacer `{colors.warning}` dans EXPERIENCE.md par `{colors.primary-container}` directement, avec une note explicite que les bannières d'avertissement utilisent le token `primary-container`.

---

## Récapitulatif des constats

| Sévérité | Nb | Statut |
|---|---|---|
| ÉCHEC | 1 | `{colors.warning}` non défini (nouveau) |
| PRÉOCCUPATION | 3 | Asymétrie composants (persistante + élargie) · Couleur rouge inline non tokenisée (persistante) · Confirm `secondary` dans dialog retour arrière (nouveau) |
| PASS | 13 | — |

---

## Recommandations

Ordonnées par priorité.

1. **(Bloquant) Définir ou remplacer le token `{colors.warning}`** — Ajouter dans le YAML `colors` de DESIGN.md la clé `warning` avec la valeur `{colors.primary-container}` (alias lisible) ou remplacer la référence dans EXPERIENCE.md par le token existant. À faire avant tout travail de thématisation sur la Fiche Catégories & Tables.

2. **(Moyen) Tokeniser la couleur « rouge » de la notification inline POS et le label de lot** — Ajouter une variante `notification-inline-error` dans DESIGN.md `components` ou référencer explicitement `{colors.error-container}` / `{colors.on-error-container}` dans EXPERIENCE.md pour les cas d'erreur POS (conflit vendu, conflit concurrent). Tokeniser également le « rouge » du label de lot dans le panier.

3. **(Moyen) Clarifier le style du bouton "Confirmer" dans les dialogs de retour arrière** — Documenter l'exception dans DESIGN.md si `secondary` est intentionnel, ou corriger vers `primary` pour respecter la règle existante des dialogs.

4. **(Moyen) Ajouter des entrées YAML minimalistes dans DESIGN.md pour les composants à fort risque de dérive** — Priorité aux composants avec une structure visuelle propre et non couverte par les composants existants : bannières (verrouillage, archivage, premier lancement), tuiles métriques (Édition archivée), zone danger (suppression RGPD). Les pages standard (Paramètres, Compte) peuvent rester couvertes par les maquettes existantes.

5. **(Faible) Compléter les entrées YAML des composants signalés en 2026-06-09 toujours manquants** — Topbar, Scanner input (ring autofocus), Panier POS, Lot dans le panier, Formulaire dépôt.
