# Cohérence Review — PluriBourse

Date : 2026-06-15
Relecteur : UX Reviewer — Cohérence interne
Périmètre : DESIGN.md (2026-06-09) × EXPERIENCE.md (2026-06-15)
Note : Cette revue remplace et étend la revue précédente du 2026-06-12 en couvrant les 6 axes de cohérence systématiquement.

## Verdict global

Les deux spines forment un système UX globalement solide et cohérent. La grande majorité des patterns comportementaux et visuels sont alignés, et les corrections introduites depuis la revue du 2026-06-12 (tokens elevation, sidebar-bg, infinite scroll, autofocus) ont été bien intégrées. Trois zones de friction subsistent et doivent être résolues avant implémentation : un token typographique inexistant (`display-sm`) référencé dans la définition du Metric tile, une ambiguïté de style pour le bouton de confirmation de l'action "Non réclamé" (irréversible mais traité en `primary`), et une ambiguïté entre la bannière d'édition archivée (primary-container) et le composant Banner standard (warning) qui n'est pas documentée comme variante.

---

## 2.1 Noms de composants — adequate

### Findings

- **medium** Le composant `Segmented control` est décrit dans EXPERIENCE.md Component Patterns avec le segment actif en `fond {colors.primary}, texte blanc`, alors que DESIGN.md frontmatter (`segmented-control`) définit `active-background: '{colors.primary-container}'` et `active-foreground: '{colors.on-primary-container}'`. Deux descriptions portent le même nom de composant mais spécifient des tokens différents pour l'état actif. *Fix :* Aligner EXPERIENCE.md sur le token DESIGN.md (`primary-container` / `on-primary-container`), ou mettre à jour DESIGN.md si le segment actif doit être `primary` plein — et ajouter une note d'exception explicite dans DESIGN.md Components.

- **low** Le composant `Notification inline` est nommé `notification-inline` dans le frontmatter DESIGN.md, puis référencé en prose dans EXPERIENCE.md sous les formes "Notification inline", "notification inline (variante erreur)", "notification inline avertissement" selon les sections. La substance est cohérente mais la casse et les parenthèses varient selon le contexte. *Fix :* Uniformiser en `notification-inline` (kebab-case) dans les mentions EXPERIENCE.md pour faciliter la traçabilité token → composant.

- **low** Le composant `Banner` est utilisé dans deux contextes visuellement différents : Fiche Catégories & Tables (fond `{colors.warning}`, texte `{colors.on-warning}`) et Édition archivée — vue détail (fond `{colors.primary-container}`). Les deux usages partagent le nom "bannière" ou "Banner" mais leurs tokens diffèrent, sans qu'une variante soit documentée dans DESIGN.md ni dans EXPERIENCE.md. *Fix :* Ajouter dans la définition du composant Banner (EXPERIENCE.md) une section "Variantes" listant `warning` (Catégories verrouillées) et `info` (Édition archivée) avec leurs tokens respectifs.

---

## 2.2 Tokens couleur et statut — adequate

### Findings

- **critical** EXPERIENCE.md Component Patterns ligne Metric tile : `chiffre en '{typography.display-sm}'`. Ce token **n'existe pas** dans DESIGN.md — la scale typographique définit uniquement `display` (32px/700), `headline`, `title-lg`, `title-md`, `body-lg`, `body-md`, `label-lg`, `label-sm`. Le token `display-sm` est absent du frontmatter et n'a jamais été défini. *Fix :* Remplacer `{typography.display-sm}` par `{typography.headline}` (24px/700) ou `{typography.title-lg}` (18px/600) selon l'intention, et/ou ajouter le token `display-sm` dans DESIGN.md si un niveau intermédiaire entre display (32px) et headline (24px) est nécessaire.

- **high** EXPERIENCE.md State Patterns "Conflit POS (article déjà vendu)" et "Conflit de scan concurrent" décrivent une "notification inline (variante erreur — fond `{colors.error-container}`, bordure `{colors.error}`)". DESIGN.md Components section `notification-inline` définit bien une variante erreur avec ces tokens. Cohérent sur ces deux occurrences. Mais EXPERIENCE.md Component Patterns "Lot dans le panier" mentionne "label du lot en `{colors.primary}`" (cohérent) sans préciser qu'il s'agit de texte coloré. La review précédente signalait un "rouge" non tokenisé — ce finding est résolu dans EXPERIENCE.md version 2026-06-15 qui utilise bien `{colors.primary}`. Finding précédent : clos.

- **medium** DESIGN.md frontmatter définit `warning: '#FFF4EE'` et `primary-container: '#FFF4EE'` — deux tokens avec des valeurs hex identiques mais des noms sémantiques distincts. EXPERIENCE.md Banner utilise `{colors.warning}` / `{colors.on-warning}`, tandis que `status-chip-warning` (DESIGN.md) utilise `{colors.primary-container}` / `{colors.on-primary-container}`. Ce sont les mêmes couleurs rendues sous deux noms différents, créant une dualité sémantique confuse pour les développeurs. *Fix :* Documenter explicitement dans DESIGN.md Colors que `warning` est un alias intentionnel de `primary-container` (et `on-warning` un alias de `on-primary-container`), ou fusionner les deux tokens en un seul avec usage documenté.

- **medium** Flow 3 "Reversement, vendeur absent" décrit le dialog "Non réclamé" avec le bouton "Confirmer" en style `primary` (corail plein). L'action est décrite comme "irréversible" en prose. Les autres actions irréversibles (suppression vendeur RGPD, archivage édition) utilisent `error` pour le bouton de confirmation. Le choix `primary` pour "Non réclamé" n'est pas documenté comme exception et peut sembler incohérent au regard du pattern établi. *Fix :* Soit documenter l'exception ("Non réclamé est irréversible mais pas destructif au sens RGPD — primary suffisant") dans Component Patterns ou DESIGN.md Do's and Don'ts, soit aligner sur `error` pour cohérence avec le pattern d'actions irréversibles.

- **low** Les boutons destructifs (suppression RGPD, archivage) utilisent correctement `secondary` couleur `error` dans EXPERIENCE.md. Le composant Danger zone précise "bouton destructif style `secondary` couleur `error`". DESIGN.md Components section Boutons : "Les actions destructives utilisent le style `secondary` avec la couleur `error` — jamais un bouton primaire corail pour une action destructive." Cohérence confirmée sur tous les cas destructifs identifiés.

- **low** Les `status-chip-success` (vert), `status-chip-warning` (corail doux), `status-chip-error` (rouge) sont référencés de façon cohérente dans EXPERIENCE.md Component Patterns "Page Reversements" (ligne soldée = chip success), State Patterns (lot incomplet = warning), et DESIGN.md frontmatter. Cohérence confirmée.

---

## 2.3 Patterns UX — adequate

### Findings

- **high** EXPERIENCE.md Voice and Tone précise : "États vides : toujours proposer une action. Jamais un état vide sans sortie." Or, trois états vides sans action sont documentés dans State Patterns : (a) "Phase Préparation — bénévole connecté" sur `/volunteer/waiting` : "Aucune action proposée" ; (b) "Reversements — liste vide" : "Aucune action proposée" ; (c) "Catalogue post-Archivage" : "Aucune action. Aucun filtre." Ces dérogations sont métier-justifiées (l'utilisateur n'a structurellement rien à faire), mais la règle n'est pas tempérée par une exception documentée, créant une contradiction formelle. *Fix :* Ajouter dans Voice and Tone une note d'exception : "Exception aux états vides : les états structurellement bloquants (phase non ouverte côté bénévole, édition archivée sans articles) n'ont pas d'action proposée — le message explicatif suffit."

- **medium** EXPERIENCE.md Interaction Primitives "Actions irréversibles" mentionne "Clean Edition" comme exemple. Ce terme n'apparaît nulle part dans l'IA, les Component Patterns, ou DESIGN.md — il est orphelin. La review précédente de 2026-06-12 référençait "Action Nettoyer l'édition" / "Action Clean Edition" mais ce composant n'existe pas dans EXPERIENCE.md version 2026-06-15. *Fix :* Retirer la mention "Clean Edition" de la section Interaction Primitives si la fonctionnalité est hors scope v1, ou ajouter un composant dédié dans Component Patterns.

- **low** Tous les toasts de succès dans flows et composants confirment la règle "4s" : Flow 3 ("toast 4s"), Flow 5 ("Toast succès (4s)"), Page Paramètres ("toast succès"), Page Rapports ("toast 'Export téléchargé.'"), Récapitulatif reversement imprimable ("toast succès (4s)"). Cohérence confirmée.

- **low** Tous les toasts d'erreur système (imprimante hors ligne) sont décrits comme persistants avec bouton "Fermer" : State Patterns "Imprimante hors ligne", Flow 1 ("toast persistant"), Flow 5 ("toast persistant"), Récapitulatif reversement imprimable ("toast persistant si imprimante hors ligne"), Page Rapports ("toast persistant en cas d'erreur serveur"). Cohérence confirmée.

- **low** Toutes les actions irréversibles identifiées sont suivies d'un dialog de confirmation : transition de phase (Flow 4), suppression vendeur RGPD (Component Patterns "Fiche vendeur admin"), archivage édition (Component Patterns "Action Archiver l'édition"), action "Non réclamé" (Flow 3), retour arrière de phase (Component Patterns "Contrôle de phase — retour arrière"). Cohérence confirmée sur ce pattern.

- **low** Focus initial des dialogs : EXPERIENCE.md Component Patterns "Dialog de confirmation" et Accessibility Floor spécifient "Focus initial sur le bouton d'annulation (action sûre)". Aucun flow ne contredit ce pattern. Cohérence confirmée.

---

## 2.4 Cohérence de la navigation — strong

### Findings

- **low** Toutes les routes Admin de l'IA sont référencées dans au moins un composant : `/admin/editions` (Page Éditions), `/admin/editions/:id` (Formulaire édition), `/admin/editions/:id/categories` (Fiche Catégories & Tables), `/admin/editions/:id/phase` (Contrôle de phase), `/admin/sellers` (flow dépôt, post-suppression), `/admin/sellers/:id` (Fiche vendeur), `/admin/catalog` (note flux catalogue), `/admin/reports` (Page Rapports), `/admin/print-queue` (Page file d'impression), `/admin/printers` (Gestion des imprimantes), `/admin/users` (Page Utilisateurs), `/admin/settings` (Page Paramètres), `/admin/settlement` (Page Reversements). Cohérence confirmée — aucune route orpheline.

- **low** Toutes les routes Bénévole de l'IA sont couvertes : `/volunteer/waiting` (State Patterns), `/volunteer/deposit` (Formulaire dépôt, Flow 1), `/volunteer/pos` (Panier POS, Flow 2), `/volunteer/settlement` (Page Reversements, Flows 3 et 5), `/volunteer/catalog` (Catalogue post-Archivage). Cohérence confirmée.

- **low** Routes partagées : `/login` (Flow 6), `/account` (Page compte utilisateur), `/account/force-password` (Premier lancement, State Patterns). Cohérence confirmée.

- **low** Sidebar Admin EXPERIENCE.md liste : Vendeurs, Articles, File d'impression, Reversements (section "Édition active") + Éditions, Rapports, Imprimantes, Utilisateurs, Paramètres (section "Gestion"). Ces 9 entrées correspondent aux 9 routes Admin de l'IA. Cohérence confirmée. La visibilité conditionnelle de "Reversements" (phases Post-vente et Clôturée uniquement) est documentée dans la sidebar IA et confirmée par Component Patterns "Page Reversements".

- **low** Aucune route référencée dans les composants n'est absente de l'IA. Vérification croisée complète : toutes les routes mentionnées dans Component Patterns et Key Flows existent dans l'IA. Cohérence confirmée.

---

## 2.5 Cohérence du vocabulaire métier — strong

### Findings

- **low** "Solder" (verbe) / "Soldé" (statut) / "Non soldé" : stable dans l'IA, Component Patterns "Page Reversements", Key Flows 3 et 5, State Patterns. Aucun synonyme détecté ("payer", "régler" apparaissent dans Voice and Tone comme exemples de langage naturel mais non comme labels UI — distinction correcte).

- **low** "Reversement" (substantif, masculin) : stable dans l'IA, sidebar, Component Patterns, Key Flows. Le titre de page est "Reversements" partout. Cohérence confirmée.

- **low** "Non réclamé" (statut vendeur) : stable dans l'IA (colonne actions), Flow 3, Component Patterns "Page Reversements". Cohérence confirmée.

- **low** "Lot" / "lot incomplet" / "article incomplet" : trois concepts distincts traités sans synonymes. "Lot" = groupe d'articles vendus ensemble à prix global. "Lot incomplet" = lot dont tous les articles n'ont pas été scannés à la caisse. "Article incomplet" = article déposé avec la case "incomplet" cochée. La distinction est maintenue dans tous les composants et flows. Cohérence confirmée.

- **low** "Archiver" / "Archivage" : stable dans Component Patterns "Action Archiver l'édition", State Patterns "Phase Clôturée — édition archivée", "Catalogue post-Archivage". Cohérence confirmée.

- **low** "Bordereau" (document dépôt vendeur) vs "Bilan" (document reversement) vs "Facture" (document acheteur) : trois documents distincts avec des termes stables. "Bordereau de dépôt" apparaît dans Interaction Primitives "Impression" uniquement. "Bilan" dans Key Flow 5 et Component Patterns "Récapitulatif reversement imprimable". "Facture" dans Component Patterns "Panier POS" post-validation. Aucun synonyme non intentionnel détecté.

- **low** "Clôturer" / "Clôturée" (phase terminale) : stable dans Phase chip labels ("Clôturée"), IA, Component Patterns. Cohérence confirmée.

---

## 2.6 Cohérence des phases — adequate

### Findings

- **high** EXPERIENCE.md Component Patterns "Édition archivée — vue détail" décrit une "Bannière permanente sous la topbar : 'Édition clôturée — données en lecture seule' (style `{colors.primary-container}`)". Dans le même fichier, la définition du composant Banner précise "Fond `{colors.warning}`, texte `{colors.on-warning}`". Il y a donc deux composants visuellement différents appelés "bannière" dans EXPERIENCE.md sans que la distinction soit documentée. La bannière d'édition archivée utilise `primary-container` (informatif) tandis que le composant Banner standard utilise `warning` (avertissement). *Fix :* Soit (a) nommer explicitement la bannière d'édition archivée "info-banner" dans EXPERIENCE.md et ajouter une entrée correspondante dans DESIGN.md frontmatter, soit (b) ajouter une variante `info` dans la définition du composant Banner avec tokens `primary-container` / `on-primary-container`.

- **medium** EXPERIENCE.md Component Patterns "Fiche Catégories & Tables" indique "Mode lecture (phase Dépôt et au-delà)". L'IA précise "Phase Préparation (éditable) · Dépôt et après : lecture seule". Le composant couvre correctement les phases mais ne mentionne pas explicitement que la page reste accessible (en lecture seule) en phases Vente, Post-vente, et Clôturée. *Fix :* Ajouter dans la description du composant : "Accessible en lecture seule pour toutes les phases au-delà de Préparation (Dépôt, Vente, Post-vente, Clôturée)."

- **medium** EXPERIENCE.md Component Patterns "Page Rapports" décrit un contenu conditionnel par phase. L'IA limite l'accès aux phases "Vente (journalier) · Post-vente · Clôturée". Le composant ne précise pas explicitement que la route est inaccessible en Préparation et Dépôt, laissant implicite la conséquence d'un accès direct (ex. via URL). *Fix :* Ajouter dans le composant "Page Rapports" : "(non accessible en phases Préparation et Dépôt — entrée masquée dans la sidebar ; accès direct via URL → redirection vers `/admin/editions` ou page d'erreur)."

- **low** EXPERIENCE.md Component Patterns "Page Reversements" ne précise pas la phase d'accès dans sa description, contrairement à l'IA qui liste "Post-vente · Clôturée". La sidebar mentionne la visibilité conditionnelle. La contrainte est documentée en deux endroits mais pas dans le composant lui-même. *Fix :* Ajouter dans la description du composant "Page Reversements" : "(accessible uniquement en phases Post-vente et Clôturée — entrée masquée dans la sidebar les autres phases)."

- **low** EXPERIENCE.md Component Patterns "Formulaire dépôt" et "Formulaire lot" indiquent correctement "Bénévole — phase Dépôt" dans la colonne Usage. Cohérence confirmée.

- **low** EXPERIENCE.md Component Patterns "Fiche édition — taux de commission" précise "modifiable uniquement en phase Préparation. Dès la phase Dépôt démarrée : champ désactivé". Cohérent avec l'IA "Édition — Catégories & Tables" qui suit la même logique de verrouillage post-Préparation. Cohérence confirmée.

- **low** EXPERIENCE.md State Patterns couvre la redirection SSE phase Préparation → Dépôt pour le bénévole (`/volunteer/waiting` → `/volunteer/deposit`). Key Flow 4 couvre la transition Admin Dépôt → Vente avec push SSE sur tous les postes. Cohérence confirmée sur les transitions de phase en temps réel.

---

## Résumé

| Axe | Verdict | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| 2.1 Noms de composants | adequate | 0 | 0 | 1 | 2 |
| 2.2 Tokens couleur et statut | adequate | 1 | 0 | 2 | 2 |
| 2.3 Patterns UX | adequate | 0 | 1 | 1 | 4 |
| 2.4 Navigation | strong | 0 | 0 | 0 | 5 |
| 2.5 Vocabulaire métier | strong | 0 | 0 | 0 | 6 |
| 2.6 Phases | adequate | 0 | 1 | 2 | 3 |
| **Total** | | **1** | **2** | **6** | **22** |

### Priorités d'action avant implémentation

1. **(critical — bloquant)** Corriger le token `{typography.display-sm}` inexistant dans la définition du composant Metric tile. Remplacer par `{typography.headline}` ou `{typography.title-lg}`, ou définir le token dans DESIGN.md.
2. **(high)** Documenter la variante `info` du composant Banner pour la bannière d'édition archivée (tokens `primary-container`), distincte du composant Banner standard (tokens `warning`).
3. **(high)** Clarifier le style du bouton "Confirmer" de l'action "Non réclamé" : justifier le choix `primary` (action engagée non-destructive) ou aligner sur `error` (cohérence avec le pattern "irréversible = error").
4. **(medium)** Aligner les tokens du `Segmented control` actif entre DESIGN.md frontmatter (`primary-container`) et EXPERIENCE.md prose (`primary` plein).
5. **(medium)** Résoudre la dualité sémantique `warning` / `primary-container` (valeurs identiques, tokens différents) — documenter l'alias ou fusionner.
6. **(medium)** Ajouter une note d'exception dans Voice and Tone pour les états vides structurellement bloquants (phase non ouverte, édition archivée).
7. **(medium)** Retirer ou documenter la référence "Clean Edition" dans Interaction Primitives.
8. **(medium)** Préciser les contraintes d'accès par phase directement dans les composants Page Rapports, Page Reversements, et Fiche Catégories & Tables.
