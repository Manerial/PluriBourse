---
name: PluriBourse
status: final
updated: 2026-06-09
colors:
  # Light theme — coral primary
  primary: '#C44626'
  on-primary: '#FFFFFF'
  primary-container: '#FFF4EE'
  on-primary-container: '#8C2910'
  secondary: '#8C5C4E'
  on-secondary: '#FFFFFF'
  secondary-container: '#F5EEEA'
  on-secondary-container: '#3D1A10'
  surface: '#FFFBF9'
  surface-variant: '#F5EEEA'
  on-surface: '#1A0A05'
  on-surface-variant: '#6B6460'
  background: '#FFFBF9'
  outline: '#C8B0A4'
  outline-variant: '#F0E4DC'
  error: '#BA1A1A'
  on-error: '#FFFFFF'
  error-container: '#FFDAD6'
  on-error-container: '#410002'
  # Dark theme
  primary-dark: '#F07040'
  on-primary-dark: '#1A0A05'
  primary-container-dark: '#4A2010'
  on-primary-container-dark: '#FDCBB0'
  surface-dark: '#1A0C06'
  surface-variant-dark: '#2A1510'
  on-surface-dark: '#F5EAE4'
  on-surface-variant-dark: '#C8B5AE'
  background-dark: '#110804'
  outline-dark: '#8C5840'
  outline-variant-dark: '#3D2218'
  error-dark: '#FFB4AB'
  on-error-dark: '#690005'
  sidebar-bg: '#2A100A'
typography:
  display:
    fontFamily: 'DM Sans'
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: '-0.01em'
  headline:
    fontFamily: 'DM Sans'
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '1.25'
  title-lg:
    fontFamily: 'DM Sans'
    fontSize: 18px
    fontWeight: '600'
    lineHeight: '1.3'
  title-md:
    fontFamily: 'DM Sans'
    fontSize: 16px
    fontWeight: '600'
    lineHeight: '1.35'
  body-lg:
    fontFamily: 'DM Sans'
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.5'
  body-md:
    fontFamily: 'DM Sans'
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-lg:
    fontFamily: 'DM Sans'
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '1.4'
  label-sm:
    fontFamily: 'DM Sans'
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: '0.05em'
    textTransform: 'uppercase'
elevation:
  level-1: '0 1px 4px rgba(28,10,5,.08)'
  level-2: '0 4px 16px rgba(28,10,5,.14), 0 1px 4px rgba(28,10,5,.08)'
  level-3: '0 8px 24px rgba(28,10,5,.18), 0 2px 6px rgba(28,10,5,.10)'
rounded:
  sm: 4px
  md: 8px
  lg: 12px
  xl: 20px
  full: 999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
  3xl: 64px
components:
  button-primary:
    background: '{colors.primary}'
    foreground: '{colors.on-primary}'
    radius: '{rounded.md}'
    padding: '10px 20px'
    font: '{typography.label-lg}'
    hover-background: '#A83A1E'
  button-secondary:
    background: '{colors.primary-container}'
    foreground: '{colors.on-primary-container}'
    radius: '{rounded.md}'
    padding: '9px 20px'
    border: '1.5px solid {colors.primary}'
    font: '{typography.label-lg}'
  button-ghost:
    background: 'transparent'
    foreground: '{colors.on-surface-variant}'
    radius: '{rounded.md}'
    padding: '9px 20px'
    font: '{typography.label-lg}'
  phase-chip:
    background: '{colors.primary-container}'
    foreground: '{colors.on-primary-container}'
    radius: '{rounded.full}'
    padding: '4px 12px'
    font: '{typography.label-lg}'
    indicator: '● before — {colors.primary}'
  role-badge:
    background: '{colors.surface-variant}'
    foreground: '{colors.on-surface-variant}'
    radius: '{rounded.full}'
    padding: '3px 10px'
    font: '{typography.label-sm}'
    admin-background: '{colors.primary-container}'
    admin-foreground: '{colors.on-primary-container}'
  status-chip-success:
    background: '#F0FDF4'
    foreground: '#166534'
    radius: '{rounded.full}'
    padding: '3px 10px'
    font: '{typography.label-sm}'
  status-chip-warning:
    background: '{colors.primary-container}'
    foreground: '{colors.on-primary-container}'
    radius: '{rounded.full}'
    padding: '3px 10px'
    font: '{typography.label-sm}'
  status-chip-error:
    background: '{colors.error-container}'
    foreground: '{colors.on-error-container}'
    radius: '{rounded.full}'
    padding: '3px 10px'
    font: '{typography.label-sm}'
  card:
    background: '{colors.surface}'
    radius: '{rounded.lg}'
    shadow: '{elevation.level-2}'
    padding: '{spacing.md}'
  list-row:
    background: '{colors.surface-variant}'
    radius: '{rounded.md}'
    padding: '10px 14px'
    hover-background: '{colors.outline-variant}'
  sidebar-item:
    foreground: 'rgba(245, 238, 234, 0.65)'
    radius: '{rounded.md}'
    padding: '8px 12px'
    font: '{typography.body-md}'
    active-background: '{colors.primary}'
    active-foreground: '#FFFFFF'
  input:
    border: '1.5px solid {colors.outline}'
    radius: '{rounded.md}'
    padding: '10px 14px'
    font: '{typography.body-md}'
    focus-border: '{colors.primary}'
    error-border: '{colors.error}'
  dialog:
    background: '{colors.surface}'
    radius: '{rounded.xl}'
    shadow: '{elevation.level-3}'
    padding: '{spacing.lg}'
---

## Brand & Style

PluriBourse est un outil opérationnel pour associations organisant des bourses aux échanges (jouets, livres, skis, vêtements). L'identité visuelle reflète la chaleur humaine de ces événements — le partage, l'échange, l'appréciation des petites choses — sans jamais oublier que l'application est avant tout un outil de travail utilisé sous pression d'événement.

La discipline de marque est minimaliste et fonctionnelle : **pas d'images décoratives, pas d'animations d'ambiance, pas de gradients**. La couleur corail est le seul signal de marque ; elle porte à la fois l'identité et les actions primaires. Le reste de la surface est neutre — beige chaud, gris pierre — pour que les informations métier (noms de vendeurs, prix, statuts de phase) ressortent sans compétition visuelle.

L'application est multi-association : l'identité est volontairement générique. Elle peut s'adapter à n'importe quelle association sans paraître étrange, et sans porter une identité trop forte qui efface celle de l'association hôte.

## Colors

Le système de couleurs est organisé autour des rôles Material Design 3, mappés sur une palette corail chaude.

**Couleur primaire** — `#C44626` (clair) / `#F07040` (sombre)
Le corail rouge-orangé est la couleur de marque. Il apparaît sur les boutons primaires, les éléments de navigation actifs, les indicateurs de phase, et les liens. En mode sombre, il s'éclaircit en corail-orangé `#F07040` pour maintenir le contraste WCAG AA sur fonds sombres. Il ne s'utilise pas en décoration.

**Surfaces** — `#FFFBF9` (clair) / `#1A0C06` (sombre)
Les surfaces sont teintées de chaud beige-pierre pour éviter le blanc clinique. En mode sombre, elles tirent vers un brun très sombre plutôt que le noir pur — cohérent avec la chaleur de la palette corail.

**Statuts sémantiques**
- Succès / Réglé : vert `#166534` sur `#F0FDF4` — neutre, lisible, non corail
- Avertissement / Incomplet : `{colors.on-primary-container}` sur `{colors.primary-container}` — corail doux
- Erreur critique : `{colors.on-error-container}` sur `{colors.error-container}` — rouge Material standard

Règle : le corail primaire ne double pas comme couleur de statut. Si quelque chose est en erreur, c'est rouge erreur, pas corail.

## Typography

Une seule famille : **DM Sans** (Google Fonts, SIL OFL). Pas de serif, pas de monospace visible en interface (le code-barres est un graphique, pas du texte).

L'échelle couvre huit niveaux de `display` (32px/700) à `label-sm` (12px/600 uppercase). Les niveaux critiques pour PluriBourse :

- **`title-lg` 18px/600** — titres de pages, nom de l'édition active dans la topbar
- **`body-md` 14px/400** — contenu principal des listes, formulaires, descriptions d'articles
- **`label-lg` 14px/600** — labels de boutons, headers de tableaux
- **`label-sm` 12px/600 uppercase** — badges de statut, chips de rôle, labels de sections sidebar

Les prix et montants financiers utilisent **`title-md` 16px/600** avec la couleur `{colors.primary}` — ils doivent ressortir visuellement dans les listes.

Taille minimale autorisée en interface : 12px. En dessous : interdit, même pour les mentions légales ou les métadonnées.

## Layout & Spacing

Échelle d'espacement base-4 (4px, 8px, 16px, 24px, 32px, 48px, 64px). Densité confortable : padding standard des cartes et listes est `{spacing.md}` (16px).

**Topbar** : hauteur fixe 56px. Contient logo + indicateur de phase centré + badge rôle + icône profil. Ne contient pas de liens de navigation.

**Sidebar Admin** : largeur fixe 200px. Fond sombre `{colors.sidebar-bg}` (#2A100A). Sections séparées par labels uppercase — "Édition active" / "Gestion". Absente de la vue Bénévole.

**Zone de contenu** : padding `{spacing.lg}` (24px) sur les bords. Largeur maximale des formulaires : 640px (centré). Largeur maximale des tableaux : illimitée (s'adapte au viewport).

Desktop uniquement — pas de breakpoints `< 1024px` à gérer en v1.

## Elevation & Depth

Deux niveaux d'élévation actifs :

- **Niveau 1** (`0 1px 4px rgba(28,10,5,.08)`) : rows de liste, inputs, petits composants
- **Niveau 2** (`0 4px 16px rgba(28,10,5,.14), 0 1px 4px rgba(28,10,5,.08)`) : cartes, panneaux principaux
- **Niveau 3** (`0 8px 24px rgba(28,10,5,.18), 0 2px 6px rgba(28,10,5,.10)`) : dialogs de confirmation, modals

La sidebar et la topbar n'ont pas d'ombre — elles se distinguent par leur couleur de fond.

## Shapes

| Token | Valeur | Utilisé sur |
|---|---|---|
| `rounded.sm` | 4px | Tags textuels, separators |
| `rounded.md` | 8px | Boutons, inputs, items de liste |
| `rounded.lg` | 12px | Cartes, panneaux |
| `rounded.xl` | 20px | Dialogs, modals |
| `rounded.full` | 999px | Badges de statut, phase chip, role badge |

Cohérence : un même type de composant utilise toujours le même token. Ne pas mélanger `rounded.md` et `rounded.lg` sur les boutons selon la page.

## Components

**Boutons**

Trois variantes : `primary` (corail plein), `secondary` (corail outline + fond container), `ghost` (transparent, texte muted). Toujours une seule action primaire visible par section. Les actions destructives (supprimer un vendeur, déclencher Clean Edition) utilisent le style `secondary` avec la couleur `error` — jamais un bouton primaire corail pour une action destructive.

**Phase chip** — topbar, toujours visible
Pill `{rounded.full}` fond `{colors.primary-container}`. Indicateur ● corail avant le label. Labels de phase : "Dépôt" · "Vente" · "Post-vente" · "Clôturée". Cliquable uniquement pour l'Admin (ouvre le panneau de contrôle de phase).

**Role badge** — topbar, à droite du phase chip
Pill `{rounded.full}`. Admin : fond `{colors.primary-container}`, texte `{colors.on-primary-container}`. Bénévole : fond `{colors.surface-variant}`, texte `{colors.on-surface-variant}`.

**Status chips** — listes de vendeurs, articles, reversements
Trois variants : success (vert), warning (corail doux), error (rouge). Toujours avec une icône Material Symbols avant le label (taille 14px).

**Dialog de confirmation** — transitions de phase, actions destructives
Radius `{rounded.xl}`. Toujours : titre explicite + description des conséquences + deux boutons (action confirmée = primary ou error, annulation = ghost). Jamais de dialog sans description des conséquences. Overlay sombre `rgba(0,0,0,0.5)`.

**Sidebar item** — Admin uniquement
Fond transparent par défaut, texte `rgba(245,238,234,0.65)`. Actif : fond `{colors.primary}`, texte blanc. Icône Material Symbols 18px avant le label. Hover : fond `rgba(255,255,255,0.08)`.

**Notification d'erreur inline** — erreurs métier (article déjà vendu, lot incomplet)
Fond `{colors.primary-container}`, bordure gauche 3px `{colors.primary}`, icône `warning` Material Symbols. Apparaît directement dans le flux de la page, pas en toast flottant.

**Toast** — confirmations d'action réussie, erreurs système (imprimante hors ligne)
Position : bottom-right. Durée : 4s pour succès, persistant jusqu'à interaction pour les erreurs système.

## Do's and Don'ts

| Do | Don't |
|---|---|
| Un seul bouton primaire corail par section visible | Deux boutons primaires côte à côte |
| Couleur primaire corail pour les actions et l'identité | Couleur primaire corail pour les statuts ou la décoration |
| Dialog de confirmation pour toute transition de phase | Transition de phase sans confirmation |
| `label-sm` uppercase pour les headers de colonnes | Texte en dessous de 12px |
| Prix en `title-md` corail dans les listes | Prix en body-md sans distinction visuelle |
| Erreurs destructives en rouge error, pas en corail | Bouton "Supprimer" en corail primaire |
| Phase chip toujours visible dans la topbar | Masquer l'indicateur de phase en mode bénévole |
