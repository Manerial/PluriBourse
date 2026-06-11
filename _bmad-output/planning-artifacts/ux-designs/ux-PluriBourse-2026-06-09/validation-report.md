# Validation Report — PluriBourse

- **DESIGN.md:** `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md`
- **EXPERIENCE.md:** `_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md`
- **Run at:** 2026-06-12T00:00:00Z
- **Lentilles:** Rubric Walker · Accessibilité · Cohérence · Couverture PRD

---

## Overall verdict

Le spine pair est opérationnellement complet et bien structuré pour une première vague d'implémentation. La rubrique mécaniques est solide — 6 catégories sur 8 en *strong*, les 2 autres en *adequate* — et la couverture PRD est quasi-totale après la mise à jour du 12 juin : les 2 ÉCHECS et 9 PRÉOCCUPATIONS de la précédente revue sont tous résolus. Deux bloquants subsistent : le token `{colors.warning}` est référencé dans EXPERIENCE.md mais absent du YAML de DESIGN.md (bloquant immédiat pour toute pipeline de thématisation), et le contraste de bordure des champs de saisie en mode clair reste à 1,99:1 contre un seuil WCAG 3:1 — l'unique ÉCHEC d'accessibilité survivant après les corrections de tokens du 12 juin.

Le périmètre restant à traiter avant implémentation est délimité et concret : corriger 2 tokens dans DESIGN.md (ou remplacer les références dans EXPERIENCE.md), ajouter une spec comportementale pour la surface de création d'édition (mockup orphelin existant mais non lié), et compléter les annotations d'accessibilité des composants ajoutés en juin. Les 4 lentilles convergent sur les mêmes 2-3 zones de risque, ce qui confirme l'absence de problèmes systémiques cachés.

---

## Category verdicts

| Catégorie | Verdict |
|---|---|
| Flow coverage | **strong** |
| Token completeness | **adequate** |
| Component coverage | **adequate** |
| State coverage | **strong** |
| Visual reference coverage | **adequate** |
| Bloat & overspecification | **strong** |
| Inheritance discipline | **strong** |
| Shape fit | **strong** |

---

## Findings by severity

### Critical (2)

**[Rubric / Coherence]** — Token `{colors.warning}` non défini (§ EXPERIENCE.md ligne 124 ; DESIGN.md frontmatter colors)
Le composant "Fiche Catégories & Tables" référence `{colors.warning}` pour la bannière de verrouillage. Ce token n'existe pas dans le YAML `colors` de DESIGN.md. Toute pipeline de thématisation échouera à résoudre ce token.
Fix: Ajouter `warning: '{colors.primary-container}'` dans le YAML `colors` de DESIGN.md (alias du corail doux déjà utilisé par `status-chip-warning`), ou remplacer la référence dans EXPERIENCE.md par `{colors.primary-container}` directement.

**[Accessibilité]** — Contraste de la bordure de saisie mode clair insuffisant — SC 1.4.11 (§ DESIGN.md `colors.outline`, `components.input.border`)
`outline` `#C8B0A4` sur `surface` `#FFFBF9` = **1,99:1**. Seuil requis : 3:1 (composants UI, SC 1.4.11 WCAG 2.2 AA). Amélioration depuis 1,26:1 mais encore insuffisant. Affecte tous les champs de saisie de l'application.
Fix: Remplacer `outline: '#C8B0A4'` par `#9E8C86` (3,01:1 minimum) ou `#8A7870` (≈ 3,5:1 pour marge de sécurité) dans DESIGN.md.

---

### High (9)

**[Rubric]** — Mockup orphelin : `mock-admin-edition-create.html` sans spec comportementale (§ EXPERIENCE.md IA `/admin/editions/:id` ; `mockups/mock-admin-edition-create.html`)
Le fichier mockup couvre FR-008, FR-017, FR-018, FR-080 (création/détail d'édition) mais aucun composant ni lien vers ce mockup n'existe dans EXPERIENCE.md. Un développeur implémentant la création d'édition n'a ni spec comportementale ni pointer vers la maquette.
Fix: Ajouter un composant "Formulaire édition — création / détail" dans Component Patterns + lien `→ Maquette : mockups/mock-admin-edition-create.html` dans le bloc IA.

**[Rubric]** — Topbar sans entrée YAML dans DESIGN.md components (§ DESIGN.md components YAML)
La topbar est décrite en prose (Layout & Spacing) et en Component Patterns (EXPERIENCE.md) mais n'a aucune clé dans le YAML `components`. Background, hauteur (56px) et absence d'ombre ne sont accessibles que par lecture de la prose.
Fix: Ajouter `topbar: { background: '{colors.surface}', height: '56px', border-bottom: 'none', shadow: 'none' }` dans DESIGN.md.

**[Rubric]** — Scanner input sans spec visuelle dans DESIGN.md (§ DESIGN.md components)
Spec comportementale complète dans EXPERIENCE.md, aucune entrée dans DESIGN.md pour le ring autofocus, la zone de résultat et les états d'erreur visuels distincts du composant `input` générique.
Fix: Ajouter une note dans le composant `input` de DESIGN.md : "Variante scanner — zone résultat : `{colors.primary-container}` pour succès scan, `{colors.error-container}` pour rejet."

**[Accessibilité]** — Restauration du focus à la fermeture des dialogs non spécifiée — SC 2.4.3 (§ EXPERIENCE.md Component Patterns, Dialog de confirmation)
Le piégeage du focus est correctement spécifié, mais aucun composant (ni dans les patterns originaux, ni dans les 4 nouveaux dialogs ajoutés en juin) ne spécifie où revient le focus après fermeture.
Fix: Ajouter au patron "Dialog de confirmation" : "À la fermeture (Annuler, Échap ou confirmation), rétablir le focus sur l'élément déclencheur du dialog."

**[Accessibilité]** — Aucun lien de saut de navigation — SC 2.4.1 (§ EXPERIENCE.md Foundation / Layout)
Sidebar persistante 200px sans skip-link. Les utilisateurs clavier/lecteur d'écran doivent tabuler à travers tous les liens de navigation à chaque changement de page.
Fix: Spécifier `<a class="skip-link" href="#main-content">Aller au contenu principal</a>` comme premier élément DOM, visible au focus.

**[Accessibilité]** — Toast sans `aria-live` — SC 4.1.3 (§ EXPERIENCE.md Component Patterns, Toast)
Les toasts de succès (4s) et les toasts d'erreur persistants (imprimante) n'ont aucune région `aria-live` spécifiée.
Fix: Ajouter au composant Toast : "`role='status'` et `aria-live='polite'` pour les toasts succès ; `role='alert'` et `aria-live='assertive'` pour les toasts d'erreur système persistants."

**[Accessibilité]** — Puce de phase SSE sans `aria-live` — SC 4.1.3 (§ EXPERIENCE.md Component Patterns, Phase chip)
La mise à jour SSE de la puce de phase (`phase-changed`) met à jour le `aria-label` de la puce mais les lecteurs d'écran ne relisent pas les éléments dont le `aria-label` change silencieusement.
Fix: Spécifier une région `aria-live='polite'` (peut être visuellement masquée) annonçant "Phase changée : [nouvelle phase]" lors de l'événement SSE.

**[Accessibilité]** — Formulaire de solde vendeur : erreur inline sans `aria-live` — SC 3.3.1, 4.1.3 (§ EXPERIENCE.md, Formulaire de solde vendeur)
Le message "Le montant saisi dépasse le reversement dû." bloque le bouton "Valider" sans annonce accessible.
Fix: Spécifier `aria-live='polite'` sur le conteneur de l'erreur inline + lier au champ via `aria-describedby`.

**[Accessibilité]** — Refocus automatique du scanner : risque de piège clavier — SC 2.1.2 (§ EXPERIENCE.md Interaction Primitives, Scanner USB HID)
Le refocus après 500ms d'inactivité peut empêcher les utilisateurs clavier d'accéder aux éléments du panier.
Fix: Remplacer par un mécanisme explicite (bouton "Retour au scanner") ou documenter une exception de désactivation (ex. double-Tab suspend le refocus).

---

### Medium (14)

**[Rubric]** — `/admin/users` sans spec comportementale ni état (§ EXPERIENCE.md IA + Component Patterns)
Surface dans l'IA, zéro contenu spécifié. Développeur sans spec pour liste des comptes, invitation, changement de rôle.
Fix: Ajouter un composant minimal "Page Utilisateurs" ou taguer explicitement comme hors-scope v1.

**[Rubric]** — `/admin/editions` (liste) sans spec comportementale ni état (§ EXPERIENCE.md)
État vide premier lancement, colonnes, bouton de création — aucun n'est spécifié.
Fix: Ajouter un composant minimal ou état vide "Aucune édition. Créez votre première édition."

**[Rubric]** — `/admin/print-queue` sans spec (§ EXPERIENCE.md IA)
Route présente dans l'IA, aucun contenu. Voir aussi PRD finding FR-079.
Fix: Ajouter spec ou taguer explicitement "Couvert par les patterns Toast + Notification inline existants."

**[Rubric]** — Reports page : pas d'état pour changement de phase pendant consultation (§ EXPERIENCE.md, Page Rapports)
Si la phase change pendant la consultation des rapports, le contenu conditionnel change mais aucun état de transition n'est spécifié.
Fix: Ajouter un state "Phase change while Reports open — re-render conditionnel ou bannière d'actualisation."

**[Rubric]** — Squelette de chargement sans spec visuelle (§ EXPERIENCE.md State Patterns ; DESIGN.md)
Skeleton rows référencées dans State Patterns, sans token de couleur ni durée d'animation dans DESIGN.md.
Fix: Ajouter `skeleton-row: { background: '{colors.surface-variant}', animation: 'pulse 1.5s ease-in-out infinite' }` dans DESIGN.md.

**[Rubric]** — Segmented control sans spec visuelle (§ EXPERIENCE.md, Formulaire dépôt ; DESIGN.md)
Contrôle segmenté "Article individuel / Lot" spécifié comportementalement, pas de tokens visuels pour segment actif vs inactif.
Fix: Ajouter `segmented-control:` dans DESIGN.md components avec tokens pour état actif/inactif.

**[Rubric]** — `status-chip-success` avec couleurs hardcodées (§ DESIGN.md lines 141–142)
Les tokens vert `#F0FDF4`/`#166534` ne sont pas définis dans le bloc `colors` — incohérence avec la discipline token des autres chips.
Fix: Ajouter `success-container: '#F0FDF4'` et `on-success-container: '#166534'` dans `colors`, ou documenter l'exception.

**[Rubric]** — Références mockups non répétées dans les Component Patterns (§ EXPERIENCE.md Component Patterns)
Les liens vers les mockups sont regroupés dans le bloc IA mais non répétés dans les entrées des composants concernés (POS caisse, admin-vendors, phase-control).
Fix: Répliquer le lien `→ Maquette :` dans chaque entrée Component Patterns ayant un mockup correspondant.

**[Rubric]** — Sources frontmatter : `architecture.md` en chemin plat (§ EXPERIENCE.md frontmatter)
Les deux autres sources sont des chemins profonds ; `_bmad-output/planning-artifacts/architecture.md` pourrait ne pas résoudre si le toolchain cherche relatif au fichier spine.
Fix: Vérifier la règle de résolution et harmoniser avec les autres chemins sources.

**[Cohérence]** — Asymétrie composants : 9 nouveaux composants 2026-06-12 sans entrée YAML DESIGN.md (§ DESIGN.md components)
Les composants ajoutés en juin (Page Rapports, Action Nettoyer, Contrôle phase retour arrière, Édition archivée, Catalogue post-Nettoyage, Page Paramètres, Fiche édition taux, Page compte, Premier lancement, Suppression RGPD) n'ont aucune entrée dans le YAML `components` de DESIGN.md.
Fix: Priorité aux composants à structure visuelle propre : bannières, tuiles métriques, zone danger. Entrées YAML minimalistes suffisent.

**[Cohérence]** — Couleur "rouge" de la notification inline POS non tokenisée (§ EXPERIENCE.md State Patterns + Component Patterns)
State Patterns et Flow 2 utilisent le terme "rouge" pour la notification de conflit POS, mais DESIGN.md définit la notification inline avec fond `{colors.primary-container}` (corail, pas rouge). Aucune variante rouge n'est définie.
Fix: Définir `notification-inline-error` dans DESIGN.md components avec `{colors.error-container}` / `{colors.on-error-container}`, ou préciser explicitement dans EXPERIENCE.md que "rouge" = tokens `error-container`.

**[Cohérence]** — Bouton "Confirmer" style `secondary` dans dialog retour arrière (§ EXPERIENCE.md, Contrôle de phase — retour arrière)
Tous les dialogs de confirmation utilisent `primary` ou `error` pour l'action confirmée (règle DESIGN.md). Le retour arrière utilise `secondary` — exception non documentée.
Fix: Soit documenter l'exception dans DESIGN.md ("retour arrière non destructif peut utiliser `secondary`"), soit aligner sur `primary`.

**[PRD]** — Page file d'impression admin sans spec (FR-079) (§ EXPERIENCE.md IA `/admin/print-queue`)
Route présente, aucun composant. Colonnes, actions par ligne (Relancer, Ignorer), états (en attente, en erreur, traité) non définis.
Fix: Ajouter composant "Page file d'impression" avec colonnes, actions par ligne, état vide "Aucun job en attente."

**[PRD]** — Bouton de clôture conditionnel et notification vendeurs non soldés (FR-096) (§ EXPERIENCE.md, Contrôle de phase)
Le bouton "Clôturer l'Édition" doit être désactivé tant que des vendeurs ne sont pas soldés ou non-réclamés (FR-096), avec notification inline et lien vers `/admin/settlement`. Ce comportement n'est pas spécifié.
Fix: Étendre le composant "Contrôle de phase" avec état désactivé et notification inline "X vendeur(s) non soldé(s) — Accéder à la page de solde."

---

### Low (17)

**[Accessibilité]** — Bannière Premier lancement sans `role="alert"` — SC 4.1.3 (§ EXPERIENCE.md, Premier lancement)
La bannière critique "Vous devez changer votre mot de passe avant de continuer." n'a pas de `role='alert'`.

**[Accessibilité]** — Boutons contextuels dans les listes sans noms accessibles distincts — SC 4.1.2 (§ EXPERIENCE.md, Page Reversements)
"Solder" × N, "Imprimer le bilan" × N — indiscernables pour un lecteur d'écran sans nom incluant le contexte vendeur.
Fix: `aria-label="Solder [prénom nom]"`, `aria-label="Imprimer le bilan de [prénom nom]"`.

**[Accessibilité]** — Exports CSV sans feedback accessible — SC 4.1.3 (§ EXPERIENCE.md, Page Rapports)
Téléchargement direct sans toast ni `aria-live` de confirmation.
Fix: Ajouter spinner dans bouton + toast succès + région `aria-live` standard.

**[Accessibilité]** — État désactivé "Retour arrière indisponible" sans annotation accessible (§ EXPERIENCE.md, Contrôle de phase — retour arrière)
Message inline icône `lock` sans `role='status'` pour détection par technologies d'assistance.

**[Accessibilité]** — Ordre de tabulation topbar en dernier dans le DOM — SC 2.4.3 (§ EXPERIENCE.md Accessibility Floor)
Topbar visuellement en haut, DOM en dernier — non conventionnel. Le skip-link résout partiellement.

**[Accessibilité]** — Taille de cible sidebar < 44px — SC 2.5.8 WCAG 2.2 (§ DESIGN.md sidebar-item)
`padding: '8px 12px'` + texte 14px = hauteur ≈ 37px. En dessous du minimum 44px.
Fix: Passer à `padding: '12px 12px'` ou documenter l'exception de décalage.

**[Accessibilité]** — `TitleStrategy` Angular non spécifiée — SC 2.4.2 (§ EXPERIENCE.md Accessibility Floor)
Mise à jour du `<title>` requise mais implémentation Angular non précisée.
Fix: Spécifier `TitleStrategy` avec modèle "[Surface] — PluriBourse".

**[Accessibilité]** — `aria-label` des listes non mis à jour dynamiquement — SC 1.3.1 (§ EXPERIENCE.md, Page Reversements)
Comptage dans `aria-label` doit être réactif aux changements de filtre.

**[Accessibilité]** — Patron d'injection d'erreur `aria-describedby` non spécifié — SC 3.3.1 (§ EXPERIENCE.md Interaction Primitives)
Conteneurs d'erreur inline : pré-rendus ou injectés ? Sans clarification, `aria-describedby` peut ne pas fonctionner.

**[Accessibilité]** — SC 2.4.11 WCAG 2.2 — apparence du focus non vérifiée (§ DESIGN.md, EXPERIENCE.md Accessibility Floor)
Focus ring hérité d'Angular Material — superficie minimale et contraste 3:1 entre états focus/non-focus à vérifier.

**[Rubric]** — Flow 1 : chemin "vendeur non trouvé → créer" non narraté (§ EXPERIENCE.md Flow 1)
Fork documenté dans Component Patterns mais absent du flow narratif.

**[Rubric]** — `button-primary.hover-background` hardcodé `#A83A1E` (§ DESIGN.md components)
Couleur de survol sans token ni règle de calcul documentée.

**[Rubric]** — `sidebar-item.foreground` opacité hardcodée (§ DESIGN.md components)
`rgba(245,238,234,0.65)` devrait être un token `on-surface-muted` ou une note de calcul.

**[Rubric]** — Nom component divergent : "Notification d'erreur inline" vs "Notification inline" (§ DESIGN.md ligne 285 ; EXPERIENCE.md ligne 116)
Même composant, deux noms.

**[Rubric]** — Casing "Post-Vente" dans `.decision-log.md` vs "Post-vente" dans les spines (§ decision-log.md ligne 7)
Causera des mismatches dans la recherche full-text des story tools.

**[Rubric]** — "Reversements" absent du sidebar dans `.decision-log.md` (§ decision-log.md ligne 33)
Le log est antérieur à l'ajout de "Reversements" dans la sidebar IA.

**[PRD]** — Article incomplet scanné en caisse sans state pattern (FR-037) (§ EXPERIENCE.md State Patterns)
Notification type et contenu non définis pour article individuel incomplet scanné au POS.
Fix: Ajouter State Pattern "Article incomplet scanné — caisse" : notification inline orange, message incluant commentaire d'incomplétude, sans blocage.

---

## Reviewer files

- [`review-rubric.md`](_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/review-rubric.md) — critical 1, high 3, medium 9, low 10
- [`review-accessibility.md`](_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/review-accessibility.md) — critical 1 (ÉCHEC), high 8, medium 3, low 5
- [`review-coherence.md`](_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/review-coherence.md) — ÉCHEC 1, PRÉOCCUPATION 3
- [`review-prd-coverage.md`](_bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/review-prd-coverage.md) — 2/2 failures resolved, 9/9 concerns resolved, 2 medium + 1 low new
