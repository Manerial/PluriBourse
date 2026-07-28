---
name: PluriBourse
status: final
updated: 2026-07-02
sources:
  - _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/prd.md
  - _bmad-output/planning-artifacts/prds/prd-PluriBourse-2026-06-08/addendum.md
  - _bmad-output/planning-artifacts/architecture.md
---

# PluriBourse — Experience Spine

## Foundation

Application web desktop uniquement (aucun breakpoint mobile en v1). Angular 21 + Angular Material (MDC / Material Design 3). `DESIGN.md` est la référence d'identité visuelle ; cette spine est la référence comportementale.

Deux rôles utilisateurs : **Admin** et **Bénévole**. Un seul layout partagé (`AppLayoutComponent`) : topbar fixe commune, sidebar contextuelle Admin uniquement. La session ne expire pas (FR-066) — Spring Session JDBC persiste les sessions à travers les redémarrages du serveur.

L'application est mono-instance, auto-hébergée, réseau local d'événement. Pas de mode offline, pas de PWA, pas de synchronisation cross-device.

## Inspiration & Anti-patterns

**Inspirations positives**
- Stripe Dashboard — clarté des données financières, hiérarchie typographique forte
- Square POS — simplicité du flux de caisse, feedback immédiat
- Notion — navigation sidebar plate et lisible

**Anti-patterns bannis**
- Interfaces POS surchargées avec des dizaines de boutons visibles simultanément
- Couleurs de statut empruntant la couleur primaire (corail = marque, pas statut)
- Wizards multi-étapes pour des actions simples (le dépôt reste sur une page)
- Modals pour confirmer des actions non destructives (préférer l'inline ou le toast) — cet anti-pattern vise les *confirmations*, pas les formulaires CRUD courts ; ces derniers peuvent légitimement être encapsulés dans `DialogShellComponent` (ex. créer/modifier une édition) sans déclencher l'anti-pattern

## Information Architecture

> Les maquettes référencées dans cette section sont des références de composition — EXPERIENCE.md prévaut en cas de conflit entre une maquette et cette spine.

### Admin

| Surface | Chemin | Accessible en |
|---|---|---|
| Éditions — liste | `/admin/editions` | Toutes phases |
| Vendeurs — liste | `/admin/sellers` | Toutes phases |
| Vendeur — fiche / création | `/admin/sellers/:id` | Toutes phases |
| Articles — catalogue | `/admin/catalog` | Toutes phases |
| Rapports | `/admin/reports` | Vente (journalier) · Post-vente · Clôturée |
| File d'impression | `/admin/print-queue` | Toutes phases |
| Gestion des imprimantes | `/admin/printers` | Toutes phases |
| Utilisateurs | `/admin/users` | Toutes phases |
| Paramètres instance | `/admin/settings` | Toutes phases |
| Reversements | `/admin/settlement` | Post-vente · Clôturée |

**Surfaces en dialog (pas d'URL dédiée)** — déclenchées par bouton depuis leur liste parente, via `DialogShellComponent` (voir Component Patterns) : Créer une édition · Modifier l'édition · Gérer les phases · Gérer les catégories & tables (toutes depuis `/admin/editions`) ; Ajouter un bénévole · Réinitialiser le mot de passe (depuis `/admin/users`). Fermeture : croix en haut à droite, bouton Annuler/Fermer, ou Echap — le focus revient toujours au bouton déclencheur de la liste.

### Bénévole (interface adaptée à la phase active)

| Phase active | Surface | Chemin |
|---|---|---|
| Préparation | Événement non ouvert | `/volunteer/waiting` |
| Dépôt | Accueil dépôt | `/volunteer/deposit` |
| Vente | Caisse | `/volunteer/pos` |
| Post-vente | Reversements | `/volunteer/settlement` |
| Toutes | Catalogue | `/volunteer/catalog` |

### Partagées

| Surface | Chemin |
|---|---|
| Connexion | `/login` |
| Mon compte | `/account` |
| Changement de mot de passe forcé | `/account/force-password` — admin uniquement, premier lancement |

### Navigation Admin — sidebar

```
Édition active
  ├── Vendeurs          (icône: people)
  ├── Articles          (icône: inventory_2)
  ├── File d'impression (icône: print)
  └── Reversements      (icône: payments) — visible en phases Post-vente et Clôturée ; masqué les autres phases
Gestion
  ├── Éditions          (icône: event)
  ├── Rapports          (icône: assessment)
  ├── Imprimantes       (icône: print_connect)
  ├── Utilisateurs      (icône: manage_accounts)
  └── Paramètres        (icône: settings)
```

Note — flux catalogue admin : la route `/admin/catalog` permet à l'Admin de consulter les articles déposés (toutes phases), de modifier un article individuel (phase Dépôt uniquement), et de déclencher la réimpression des étiquettes depuis la fiche article. La gestion des étiquettes (impression initiale et réimpression) transite par la file d'impression `/admin/print-queue` — il n'y a pas d'impression directe depuis le catalogue.

L'entrée active dans la sidebar correspond à la route courante. Aucun sous-menu dépliable — la navigation reste plate et lisible.

→ Composition référence : `.working/navigation-layouts.html` (Option 3). Spine wins on conflict.
→ Maquette formulaire édition (création / détail) : `mockups/mock-admin-edition-create.html` — contenu affiché dans `DialogShellComponent`, plus une page pleine
→ Maquette caisse POS (lot incomplet) : `mockups/mock-pos-caisse.html`
→ Maquette caisse POS (lot complet) : `mockups/mock-pos-caisse-lot-complet.html`
→ Maquette formulaire dépôt : `mockups/mock-deposit.html`
→ Maquette catégories & tables (admin) : `mockups/mock-admin-categories.html` — contenu affiché dans `DialogShellComponent`, plus une page pleine
→ Maquette contrôle de phase : `mockups/mock-phase-control.html` — contenu affiché dans `DialogShellComponent`, plus une page pleine
→ Maquette validation paiement POS : `mockups/mock-pos-paiement.html`
→ Maquette liste vendeurs admin (`/admin/sellers`) : `mockups/mock-admin-vendors.html`
→ Maquette reversements — vue admin (`/admin/settlement`) : `mockups/mock-admin-settlement.html`
→ Maquette reversements — vue bénévole (`/volunteer/settlement`) : `mockups/mock-volunteer-settlement.html`

## Voice and Tone

Langue de l'interface : EN ou FR selon la préférence du compte utilisateur (ngx-translate). Les règles ci-dessous s'appliquent aux deux langues.

**En français : vouvoiement systématique.** L'application s'adresse à des bénévoles de tous âges ; le vouvoiement est neutre et inclusif.

| Do | Don't |
|---|---|
| "Êtes-vous sûr de vouloir passer en phase Vente ?" | "T'es sûr ?" · "Confirmer ?" sans contexte |
| "Aucun vendeur trouvé. Créez un nouveau profil." | "Pas de résultat." |
| "Article déjà vendu sur un autre poste." | "Erreur 409." · "Conflit détecté." |
| "Lot incomplet — il manque 2 articles sur 4." | "Lot invalide." |
| "L'imprimante [nom] ne répond pas. Vérifiez la connexion." | "Erreur d'impression." |
| "Vendeur réglé." (toast succès) | "Opération effectuée avec succès." |
| Actions : verbe + objet — "Valider le dépôt", "Régler le vendeur" | Jargon technique visible |
| Labels : noms — "Catégorie", "Table", "Reversement" | Abréviations non évidentes |

**Confirmations de phase** : toujours nommer la phase de destination et décrire la conséquence principale. Exemple : "Passer en phase Vente. Les dépôts ne seront plus modifiables."

**États vides** : toujours proposer une action. Jamais un état vide sans sortie. *Exception :* les états structurellement bloquants (bénévole en phase Préparation, liste de reversements sans vendeurs inscrits, catalogue post-Archivage) n'ont pas d'action proposée — le message explicatif suffit.

## Component Patterns

Comportemental. Spécifications visuelles dans `DESIGN.md.Components`.

| Composant | Usage | Règles comportementales |
|---|---|---|
| **Topbar** | Global | Logo à gauche · Phase chip au centre · Role badge + icône profil à droite. Phase chip cliquable Admin (→ contrôle de phase). Non cliquable Bénévole. |
| **Sidebar** | Admin uniquement | Largeur fixe 200px. Non collapsable en v1. Entrée active déterminée par la route courante. |
| **Phase chip** | Topbar | Mis à jour en temps réel via SSE (`phase-changed`). Transition animée : fade 150ms. Porte `aria-label="Phase actuelle : [phase]"` mis à jour à chaque changement. Une région `aria-live="polite"` visuellement masquée (`sr-only`) dans la topbar annonce "Phase changée : [nouvelle phase]" lors du push SSE — la mise à jour du `aria-label` seul est silencieuse pour les lecteurs d'écran (SC 4.1.3). |
| **DialogShellComponent** | Conteneur standard pour **tout** dialog de l'application, présents et futurs (confirmation, réinitialisation de mot de passe, formulaires CRUD courts, sous-vues d'édition, et toute nouvelle boîte de dialogue à venir — ex. modification de statut d'article, suppression d'imprimante) | Structure fixe : titre + croix de fermeture (icône `close`, 44×44px) en haut à droite + un seul emplacement de contenu projeté (`ng-content`) portant le corps et les boutons d'action. Bloque l'interaction derrière un overlay sombre `rgba(0,0,0,0.5)`. Focus piégé sur l'ensemble du contenu projeté, y compris les champs de formulaire et la croix de fermeture (Tab ne sort pas du dialog). Focus initial : premier champ interactif utile (ou bouton d'annulation pour les dialogs de confirmation — action sûre par défaut). À la fermeture — quelle que soit la cause (croix, bouton Annuler/Fermer, Échap, ou confirmation) — le focus revient systématiquement à l'élément déclencheur. Largeur : s'adapte au contenu jusqu'à `{components.dialog.max-width}` (640px) ; corps scrollable verticalement si le contenu dépasse la hauteur de viewport disponible, le titre et la croix restent fixes en tête. La croix porte `aria-label="Fermer"` (ou libellé traduit équivalent) ; c'est une fermeture équivalente à Annuler, jamais à Confirmer. |
| **Dialog de confirmation** | Transitions de phase, actions destructives, archivage d'édition | Construit sur `DialogShellComponent`. Toujours : titre + conséquence + bouton confirm + bouton annuler, en plus de la croix de fermeture du shell (la croix équivaut à Annuler). Focus initial sur le bouton d'annulation (action sûre). Fermeture : croix, bouton annuler, ou Echap — jamais de confirmation implicite par fermeture. |
| **Notification inline** | Erreurs métier dans le flux (POS, dépôt) | Apparaît directement sous l'élément déclencheur, pas en toast. Reste visible jusqu'à résolution ou nouvelle action. Icône `warning` + message en langage naturel. |
| **Toast** | Confirmations de succès, erreurs système | Bottom-right. Succès : 4s puis disparaît. Erreur système (imprimante) : persistant, bouton "Fermer". Max 1 toast simultané. Le conteneur toast porte `role="status"` et `aria-live="polite"` pour les toasts de succès ; `role="alert"` et `aria-live="assertive"` pour les toasts d'erreur système persistants (SC 4.1.3). |
| **Catalogue / liste filtrée** | Admin + Bénévole | Filtres en ligne au-dessus de la liste : catégorie · statut (vendu / non vendu) · table · nom du vendeur · incomplet. Tri par clic sur le header de colonne (↑↓). Pagination via `MatPaginator` — page size par défaut 50. **Modification partielle d'article (Admin — toutes phases) :** bouton "Modifier statut" dans la colonne Actions de chaque list-row ; ouvre un dialog avec deux champs — complet/incomplet (case à cocher) + commentaire (texte libre). Accessible en phases Dépôt, Vente, Post-vente, Clôturée. → Maquette liste vendeurs : `mockups/mock-admin-vendors.html` |
| **Scanner input** | Caisse (POS) | Champ auto-focused à l'ouverture de la caisse. Capture les événements keyboard du scanner USB HID. Traitement à la touche `Enter` (ou `\n` selon scanner). AZERTY/QWERTY géré par key code mapping côté Angular. Pas de debounce — traitement immédiat. |
| **Panier POS** | Caisse | Liste des articles scannés avec prix unitaire et total. Suppression individuelle par icône `close` sur chaque ligne. Notification inline avertissement si lot incomplet — le bouton "Valider" reste actif. Annulé automatiquement si changement de phase (SSE `basket-cancelled`). **Étape de validation du paiement** (déclenchée par "Valider") : panel inline avec trois boutons radio — "Espèces" · "Chèque" · "Carte". Sélection obligatoire — le bouton "Confirmer" reste désactivé tant qu'aucun moyen de paiement n'est sélectionné. En cas d'espèces : champ optionnel "Somme remise (€)" — si renseigné, le système affiche "Monnaie à rendre : X,XX €" (calcul = somme remise − total panier) ; si laissé vide, aucun calcul effectué (montant exact supposé). Bouton "Confirmer" (primary) clôt la transaction ; bouton "Annuler" retourne au panier sans perdre les articles. **État post-validation** : panier vidé ; bouton "Imprimer la facture" (icône `print`) visible pendant 30 secondes puis disparaît automatiquement ; scanner reprend le focus pour une nouvelle transaction. **Structure du PDF facture acheteur (FR-041) :** en-tête : nom de l'association (Paramètres) + nom de l'édition + date et heure ; corps : liste des articles (nom, prix unitaire) avec les lots sur une ligne unique au prix global du lot ; total TTC. Sans nom d'acheteur (non collecté). → Maquette caisse : `mockups/mock-pos-caisse.html` · `mockups/mock-pos-caisse-lot-complet.html` · Maquette paiement : `mockups/mock-pos-paiement.html` |
| **Lot dans le panier** | Caisse | Articles du lot affichés groupés avec label du lot en `{colors.primary}`. Compteur "X/N scannés". Sous-total du lot affiché dans l'en-tête du groupe (somme des articles scannés). **Pas de prix individuel par article** — le lot est une unité de vente. Bouton "Retirer le lot entier" visible dès le premier article du lot dans le panier. Notification inline avertissement si lot incomplet — la validation n'est pas bloquée. |
| **Formulaire dépôt** | Bénévole — phase Dépôt | Recherche vendeur par nom ou email en premier. Si non trouvé : bouton "Créer un profil" — formulaire de création avec les champs obligatoires : nom de famille · prénom · email (validation format email) · téléphone (validation : 10 chiffres ou format international). En tête du formulaire de saisie des articles : **sélecteur de type** (contrôle segmenté à deux segments mutuellement exclusifs) — "Article individuel" (défaut) et "Lot". **Mode Article individuel :** nom de l'article, prix (€), catégorie (sélecteur), case à cocher complet/incomplet + champ commentaire. **Mode Lot :** voir "Formulaire lot". Table assignée automatiquement depuis la catégorie de chaque article ou de chaque article du lot. → Maquette : `mockups/mock-deposit.html` |
| **Formulaire lot** | Bénévole — phase Dépôt, sélecteur de type sur "Lot" | Déclenché par le segment "Lot" du sélecteur de type dans le formulaire de dépôt. Champs du lot : **nom du lot** (texte) + **prix global du lot** (€, BigDecimal). Section "Articles du lot" : liste dynamique — chaque ligne contient nom de l'article + catégorie (sélecteur) + case incomplet + commentaire optionnel (FR-022) ; pas de prix individuel par article. Bouton "+ Ajouter un article au lot" (style dashed border) en bas de liste pour ajouter une nouvelle ligne. Bouton **"Valider le lot (N articles)"** : désactivé tant que N < 2 (FR-043 — 2 articles minimum) ; le label se met à jour en temps réel avec le nombre d'articles. Bouton **"Annuler"** : désélectionne le segment "Lot" et retourne au mode Article individuel sans perdre les autres données vendeur. **Étiquette thermique lot vs étiquette standard :** les articles d'un lot reçoivent une étiquette spécifique — pas de prix individuel (remplacé par "Prix du lot : X,XX €" commun à tous les articles du lot) + mention "Lot indivisible : N/M" (N = position de l'article, M = nombre total d'articles dans le lot). Tous les autres champs (nom, catégorie, table, barcode) restent identiques à l'étiquette standard. |
| **Page Éditions — liste** | Admin — `/admin/editions` · toutes phases | Liste des éditions de l'instance. Colonnes : nom de l'édition · phase courante (phase chip) · date de création · actions. Bouton "Créer une édition" (primary) en haut à droite. Chaque ligne est cliquable et ouvre `/admin/editions/:id`. Tri par date décroissant (plus récente en tête). Pas de pagination en v1 (nombre d'éditions faible). État vide : "Aucune édition — créez la première édition de votre association." avec bouton "Créer une édition". **Suppression :** l'action "Supprimer" est présente uniquement pour les éditions en phase Préparation ; absente pour toute édition en phase Dépôt, Vente, Post-vente ou Clôturée (FR-014). |
| **Formulaire édition — création / modification** | Admin — dialog `DialogShellComponent`, déclenché par le bouton "Créer une édition" (liste `/admin/editions`) ou "Modifier" sur une ligne | Deux modes : **Création** (nouvel objet, titre "Créer une édition") et **Modification** (édition existante, titre = nom de l'édition). Champs : nom de l'édition (obligatoire), taux de commission (voir composant dédié), langue des documents (sélecteur EN/FR). **À la création :** option "Copier les catégories d'une édition clôturée" (sélecteur limité aux éditions clôturées). Pas de navigation interne vers Catégories & Tables ou Contrôle de phase — ce sont des dialogs distincts, ouverts depuis les actions de la ligne dans la liste des éditions, jamais imbriqués dans ce dialog. Phase Clôturée : bannière lecture seule (voir composant Édition archivée). Fermeture (croix, Annuler, Echap) : ferme sans confirmation, même si des champs ont été modifiés — comportement identique à la navigation hors d'une page non sauvegardée aujourd'hui, pas de régression introduite. → Maquette : `mockups/mock-admin-edition-create.html` |
| **Dialog Catégories & Tables** | Admin — dialog `DialogShellComponent`, déclenché par le bouton "Gérer les catégories" sur une ligne d'édition (`/admin/editions`) | **Mode édition (phase Préparation) :** tableau à deux colonnes — nom de catégorie (champ texte) · tables assignées (chip input). Chip input : l'admin tape un entier et appuie sur Entrée ; le numéro apparaît en chip avec un bouton × de suppression. Plusieurs chips par catégorie autorisés. Un même numéro peut figurer dans plusieurs catégories (many-to-many) — aucun indicateur visuel de partage. Bouton « + Ajouter une catégorie » sous le tableau. Chaque ligne dispose d'un bouton de suppression de catégorie (icône `delete`). **À la création d'édition :** option « Copier depuis une édition clôturée » (sélecteur limité aux éditions clôturées avec aperçu) ou « Configurer manuellement ». **Mode lecture (phases Dépôt, Vente, Post-vente, Clôturée) :** composant `banner` variante `warning` — message « Catégories verrouillées — passez en phase Préparation pour modifier » · chips affichés sans × · champ de saisie absent · bouton d'ajout absent · bouton de suppression de ligne absent. Le mode lecture redevient mode édition si l'admin effectue un retour arrière Dépôt → Préparation. Le tableau, potentiellement long, défile dans le corps scrollable du dialog ; le titre et la croix de fermeture restent fixes. → Maquette : `mockups/mock-admin-categories.html` |
| **Formulaire dépôt — table auto-assignée** | Bénévole — phase Dépôt | Après sélection de la catégorie, une ligne de texte inline apparaît immédiatement sous le sélecteur : « Table n°X » en style `{typography.label-lg}` couleur `{colors.on-surface-variant}`. La valeur est calculée côté serveur (algorithme FR-023 : même table si vendeur déjà présent dans la catégorie, sinon table la moins chargée toutes catégories confondues). Si le calcul est en cours, un indicateur de chargement inline (spinner 16px) remplace le texte jusqu'à la réponse. La table n'est pas modifiable par le bénévole. |
| **Page Rapports** | Admin — phases Vente · Post-vente · Clôturée (non accessible en Préparation et Dépôt — entrée masquée dans la sidebar ; accès direct via URL → redirection vers `/admin/editions`) | Contenu conditionnel selon la phase active — seules les sections pertinentes à la phase courante sont affichées, les autres sont absentes. **Rapport de caisse journalier** (Vente uniquement) : montant total des ventes du jour ; ventilation par moyen de paiement (total espèces / total chèques / total carte) ; bouton "Actualiser". **Rapport de synthèse** (Post-vente + Clôturée) : total ventes, ventilation par moyen de paiement, total reversements nets, total recettes association — lecture seule. **Exports CSV** (Post-vente + Clôturée) : boutons "Exporter le catalogue" (articles + statut vendu/non vendu) et "Exporter les reversements". Au clic : le bouton passe en état désactivé avec spinner inline pendant la génération ; le téléchargement démarre dès que le fichier est prêt (pas de dialog) ; toast succès 4s "Export téléchargé." ou toast persistant en cas d'erreur serveur. La liste des vendeurs non soldés est accessible via `/admin/settlement` — pas de section dédiée dans les rapports. **Changement de phase en temps réel** : lors d'un push SSE `phase-changed`, la page recharge son contenu automatiquement ; si de nouvelles sections deviennent pertinentes (ex. passage Vente → Post-vente débloque le rapport de synthèse), elles apparaissent sans rechargement manuel. Un toast informatif : "La phase a changé — rapports mis à jour." |
| **Action "Archiver l'édition"** | Admin — phase Clôturée | Bouton visible sur la fiche édition uniquement si des articles non supprimés existent. Style `secondary` couleur `error`. Dialog de confirmation : "Archiver et supprimer tous les articles de cette édition. Cette action est irréversible." Boutons : "Archiver" (error) + "Annuler" (ghost). Post-archivage : le catalogue affiche un état vide "Édition archivée — aucun article." sans action proposée. Le bouton "Archiver l'édition" disparaît définitivement. |
| **Récapitulatif reversement imprimable** | Bénévole — phase Post-vente · Admin toutes phases | Bouton "Imprimer le récapitulatif" accessible depuis la ligne vendeur sur `/volunteer/settlement` (après règlement) et depuis la fiche vendeur Admin. Feedback : spinner dans le bouton pendant la mise en queue. Résultat communiqué par toast succès (4s) ou toast persistant si imprimante hors ligne. Toujours rejouable. |
| **Page file d'impression** | Admin — `/admin/print-queue` · toutes phases (FR-079) | Vue organisée par imprimante enregistrée. Chaque imprimante est présentée dans une carte : nom · type (thermique / A4) · statut de connexion (chip vert « Connectée » / rouge « Hors ligne ») · profondeur de file · job en cours · dernière erreur. Si une imprimante est inaccessible, la carte affiche un bandeau d'alerte : « Imprimante inaccessible — vérifiez la connexion Bluetooth / réseau de l'imprimante elle-même (si PrinterBridge répond) ou le service PrinterBridge (si injoignable, voir bandeau dédié). » Liste des jobs par imprimante : colonnes type de document · vendeur ou article concerné · statut (En attente / En cours / Imprimé / Erreur). Actions par job : "Relancer" · "Ignorer" (disponible si statut Erreur). Mise à jour en temps réel via SSE (`print-job-updated`). État vide par imprimante : « Aucun travail en file. » **État "Agent PrinterBridge injoignable"** (réutilise `NotificationInlineComponent` variant `warning`) : si le backend ne parvient pas à joindre PrinterBridge, bandeau en tête de page — « Le service PrinterBridge ne répond pas sur ce poste. Vérifiez qu'il est lancé. » — distinct du statut « Hors ligne » d'une imprimante précise. |
| **Gestion des imprimantes (Admin)** | Admin — `/admin/printers` · toutes phases (FR-076, FR-077, FR-100) | Liste unique des imprimantes enregistrées (thermiques et A4) : nom · type déduit · statut de connexion · largeur (thermiques uniquement). Bouton "Tester l'impression" (icône `print`) par ligne — spinner pendant l'appel, toast succès/erreur. Bouton "Ajouter une imprimante" (primary) en haut de la liste : ouvre un dialog listant les imprimantes détectées par PrinterBridge, chacune présentée comme une ligne avec deux actions — **"Enregistrer"** (formulaire : nom d'affichage, largeur si thermique — le type est dérivé automatiquement de l'imprimante détectée) et **"Ignorer"**. Un indicateur de chargement s'affiche pendant l'appel à PrinterBridge, avant que le dialog ne s'ouvre. Si PrinterBridge est injoignable, un bandeau d'avertissement (« Le service PrinterBridge ne répond pas sur ce poste. Vérifiez qu'il est lancé. ») remplace la liste dans le dialog. À la sauvegarde : toast succès "Imprimante enregistrée." et la file correspondante est instanciée. **Section "Imprimantes ignorées"** (repliée par défaut, sous la liste principale) : liste les imprimantes explicitement ignorées, avec une action "Réactiver" par ligne — l'imprimante réapparaît alors dans la découverte au prochain scan. Suppression d'une imprimante enregistrée : bouton icône `delete` par ligne, dialog de confirmation "Supprimer [nom] ? Les travaux en cours seront perdus." |
| **Sélection d'imprimante — login bénévole** | Bénévole — après authentification, avant accès à l'interface (FR-098) | Écran interstitiel affiché immédiatement après la connexion réussie, avant la redirection vers l'interface de phase. Titre : "Choisissez vos imprimantes". Deux sélecteurs : **Imprimante thermique** (liste des thermiques enregistrées et disponibles) · **Imprimante A4** (liste des A4 enregistrées et disponibles). Bouton "Confirmer" (primary) — actif dès qu'une imprimante est sélectionnée dans chaque liste. Si aucune imprimante d'un type n'est disponible : message d'avertissement inline "Aucune imprimante [thermique / A4] disponible — contactez l'administrateur." Le bénévole peut tout de même confirmer et accéder à l'interface (les impressions seront en erreur jusqu'à résolution). La sélection est active pour toute la durée de la session. |
| **Segmented control** | Formulaire dépôt (sélecteur Article / Lot) | Contrôle segmenté mutuellement exclusif. Mappe sur `MatButtonToggleGroup`. Segment actif : fond `{colors.primary-container}`, texte `{colors.on-primary-container}`. Désactivé en totalité si le contexte l'interdit. Pas d'état intermédiaire. Hauteur 40px. |
| **Banner** | Fiche Catégories & Tables (mode lecture), Édition archivée — vue détail, toute surface nécessitant une alerte persistante | Bannière informative pleine largeur, non fermable — disparaît uniquement à la résolution de la condition. Message concis (une phrase). **Variante `warning`** (fond `{colors.warning}`, texte `{colors.on-warning}`, icône `warning`) : pour les états de verrouillage temporaire (ex. "Catégories verrouillées — passez en phase Préparation pour modifier"). **Variante `info`** (fond `{colors.primary-container}`, texte `{colors.on-primary-container}`, icône `info`) : pour les états informatifs persistants non actionnables (ex. "Édition clôturée — données en lecture seule"). |
| **Metric tile** | Page Rapports, Édition archivée — vue détail | Carte de métrique agrégée : chiffre en `{typography.title-lg}`, label en `{typography.label-lg}`. Lecture seule. Pas d'action, pas d'état hover. |
| **Danger zone** | Fiche vendeur admin — suppression RGPD, Action "Archiver l'édition" | Wrapper sémantique pour les actions irréversibles : description de la conséquence + bouton destructif style `secondary` couleur `error`. Pas un composant standalone — container. Toujours suivi d'un dialog de confirmation. |
| **Skeleton row** | Toutes les listes pendant le chargement initial | Ligne de chargement animée (shimmer). Affichée pendant le chargement initial, remplacée dès la réponse. 3 à 5 lignes par défaut, même hauteur que la `list-row` réelle. |
| **List row** | Toutes les listes paginées (vendeurs, éditions, utilisateurs) | Ligne cliquable → navigation vers la fiche détail. Actions disponibles : boutons inline dans une colonne "Actions", toujours visibles (pas de hover-only affordances). Pas d'action déclenchée par double-clic ou survol seul. |
| **Page Reversements** | Admin `/admin/settlement` · Bénévole `/volunteer/settlement` (accessible uniquement en phases Post-vente et Clôturée — entrée masquée dans la sidebar les autres phases) | Liste paginée de tous les vendeurs de l'édition active. Colonnes communes : nom, prénom, montant dû, statut (soldé / non soldé), actions (imprimer bilan · solder · non réclamé). Colonnes supplémentaires Admin uniquement : téléphone, email — affichage conditionnel selon le rôle (`*ngIf="isAdmin"`), composant Angular unique. Filtre de statut en haut de liste (tous / non soldés / soldés). Tri par colonne. Ligne soldée grisée avec chip `status-chip-success`. → Maquette admin : `mockups/mock-admin-settlement.html` · Maquette bénévole : `mockups/mock-volunteer-settlement.html` |
| **Formulaire de solde vendeur** | Bénévole + Admin — `/volunteer/settlement` et `/admin/settlement` | Accessible depuis la ligne vendeur (bouton "Solder"). Champ unique : "Montant remis en espèces (€)". Validation côté client : si montant > net calculé → bouton "Valider" bloqué + message inline "Le montant saisi dépasse le reversement dû." (le conteneur du message porte `aria-live="polite"` et est lié au champ via `aria-describedby` — SC 3.3.1, 4.1.3). Si montant < net calculé → bouton "Valider" actif mais dialog de confirmation : "Le montant saisi est inférieur au reversement dû (X,XX €). Confirmer quand même ?" Boutons : "Confirmer" (primary) + "Annuler" (ghost). Après confirmation : statut vendeur passe à Soldé, ligne mise à jour en temps réel. |
| **Dialog Contrôle de phase — retour arrière** | Admin — dialog `DialogShellComponent`, déclenché par le bouton "Gérer les phases" sur une ligne d'édition (`/admin/editions`) | En-tête du dialog : nom de l'édition (titre) + phase courante (phase chip). En plus du bouton d'avancement, un bouton "Revenir à la phase précédente" (style `secondary`) affiche la phase cible : "Revenir en phase Dépôt", etc. Dialog de confirmation systématique : "Revenir en phase [X]. Les données enregistrées en phase [Y] sont préservées — aucune action n'est annulée." Boutons : "Confirmer" (secondary) + "Annuler" (ghost). **Cas Clôturé → Post-vente après Archivage** : le bouton "Revenir en Post-vente" est absent et remplacé par un message inline : "Retour en arrière indisponible — l'édition a été archivée." Le message est en style `{colors.on-surface-variant}` avec icône `lock` et porte `role="status"` (annonce non urgente au chargement de la page). **Clôture avec vendeurs non soldés (FR-096)** : le bouton "Clôturer l'édition" est toujours actif. Si des vendeurs non soldés subsistent, la boîte de dialogue de confirmation (FR-011) est enrichie d'un bloc d'alerte : « X vendeur(s) non soldé(s) seront automatiquement marqués Non réclamé. Montant total transféré aux recettes de l'association : Y,YY €. » Boutons : "Confirmer" (primary) + "Annuler" (ghost). Si tous les vendeurs sont déjà soldés ou Non réclamés, la dialog standard s'affiche sans ce bloc. **Génération du bilan d'édition (FR-055) :** après confirmation de la clôture, le bouton "Clôturer l'édition" passe en état désactivé avec spinner inline pendant la génération du bilan d'édition PDF (EN + FR). Toast succès (4s) : "Bilan d'édition généré (EN + FR)." Toast persistant en cas d'erreur de génération. → Maquette : `mockups/mock-phase-control.html` |
| **Édition archivée — vue détail** | Admin — `/admin/editions/:id` (phase Clôturée) | Bannière permanente sous la topbar : "Édition clôturée — données en lecture seule" (style `{colors.primary-container}`). Métriques agrégées affichées : total articles déposés, total articles vendus, total invendus, chiffre d'affaires brut, commission totale, montant reversé aux vendeurs. Sous-section vendeurs : liste des vendeurs avec statut soldé/non soldé — colonnes en lecture seule, pas d'action solder. Catalogue articles accessible uniquement si l'action Archivage n'a pas été déclenchée (voir composant Action Archiver). Bouton "Archiver l'édition" visible si articles non supprimés. Bouton "Revenir en Post-vente" accessible si Archivage non déclenché (voir Contrôle de phase — retour arrière). |
| **Catalogue — état post-Archivage** | Admin + Bénévole — édition après Archivage | La route `/admin/catalog` et `/volunteer/catalog` pour une édition archivée affichent : icône centré + message "Édition archivée — les articles ont été supprimés. Seules les métriques agrégées sont disponibles." Pas d'action proposée. Aucun filtre visible. |
| **Page Utilisateurs** | Admin — `/admin/users` · toutes phases | Liste paginée des comptes utilisateurs (Admin + Bénévoles). Colonnes : prénom/nom · rôle (role badge) · dernier accès · actions. Bouton "Créer un utilisateur" (primary) en haut à droite — ouvre le dialog **Ajouter un bénévole** (`DialogShellComponent`). Actions par ligne : "Modifier le rôle" (sélecteur inline) · "Réinitialiser le mot de passe" (ouvre le dialog **Réinitialiser le mot de passe**, `DialogShellComponent`, un champ nouveau mot de passe + confirmation via toast) · "Supprimer" (dialog de confirmation). L'Admin connecté ne peut pas supprimer son propre compte (bouton Supprimer absent sur sa ligne). |
| **Page Paramètres instance** | Admin — `/admin/settings` | Formulaire à trois champs : **Nom de l'association** (champ texte, obligatoire — apparaît sur les factures acheteur FR-041) · **Taux de commission par défaut** (champ numérique %, appliqué aux nouvelles éditions uniquement) · **Langue des documents par défaut** (sélecteur EN / FR, appliqué aux nouvelles éditions). Note explicative sous chaque champ "par défaut" : "Cette valeur s'applique aux nouvelles éditions. Modifiez la langue / le taux directement sur la fiche édition pour les éditions existantes." Bouton "Enregistrer" (primary) avec feedback spinner + toast succès. La largeur du ticket thermique est configurée par imprimante dans `/admin/printers` (FR-032). |
| **Fiche édition — taux de commission** | Admin — `/admin/editions/:id` (phase Préparation) | Champ "Taux de commission" modifiable uniquement en phase Préparation. Dès la phase Dépôt démarrée : champ désactivé visuellement (fond `{colors.surface-variant}`, texte `{colors.on-surface-variant}`) + message inline : "Taux gelé pour cette édition. Retournez en phase Préparation pour le modifier." |
| **Page compte utilisateur** | Admin + Bénévole — `/account` | Deux sections : **Préférence de langue** — sélecteur EN / FR avec application immédiate sans rechargement (ngx-translate runtime switch). **Changement de mot de passe** — formulaire trois champs : mot de passe actuel · nouveau mot de passe · confirmation. Validation : nouveau mot de passe ≠ actuel. Bouton "Enregistrer" avec feedback toast. |
| **Premier lancement — changement de mot de passe forcé** | Admin — première connexion | À la connexion avec les identifiants par défaut Admin/Admin, le système redirige vers `/account/force-password` avant toute autre navigation. La topbar est visible mais les liens de navigation sont désactivés (sidebar masquée). Message : "Vous devez changer votre mot de passe avant de continuer." (le conteneur de ce message porte `role="alert"` pour que les lecteurs d'écran annoncent immédiatement la contrainte à l'arrivée sur la page). Formulaire : nouveau mot de passe + confirmation. Après confirmation : redirection vers `/admin/settings` pour la configuration initiale. Aucun contournement possible — toute navigation vers une autre route redirige vers `/account/force-password` tant que le changement n'est pas effectué. |
| **Fiche vendeur admin — suppression RGPD** | Admin — `/admin/sellers/:id` (phase Dépôt uniquement) | Bouton "Supprimer ce vendeur" (style `secondary` couleur `error`) en bas de fiche. Dialog de confirmation : "Supprimer le profil de [prénom nom] et l'ensemble de ses articles pour cette édition. Cette action est irréversible et ne peut pas être annulée." Boutons : "Supprimer" (error) + "Annuler" (ghost). Post-suppression : redirection vers `/admin/sellers` avec toast "Vendeur supprimé." Le profil n'apparaît plus dans aucune liste. |

## State Patterns

| État | Surface | Traitement |
|---|---|---|
| Phase Préparation — bénévole connecté | `/volunteer/waiting` | Page neutre : icône Material Symbols centré + "L'événement n'est pas encore ouvert. Revenez quand la phase Dépôt sera démarrée." Chip de phase visible dans la topbar. Aucune action proposée. Mise à jour automatique via SSE dès que la phase passe à Dépôt (redirection vers `/volunteer/deposit`). |
| Chargement initial | Listes, catalogues | Skeleton rows Angular Material (3–5 lignes). Pas de spinner global. |
| Liste vide | Vendeurs, articles, reversements | Icône Material Symbols centré + phrase descriptive + action primaire. Ex : "Aucun vendeur enregistré. Créez le premier profil." |
| Résultats filtrés vides | Catalogue, liste vendeurs | "Aucun résultat pour ces filtres." + bouton "Effacer les filtres". Pas d'état vide générique. |
| Erreur de chargement | Toutes | Card d'erreur avec message naturel + bouton "Réessayer". |
| Sauvegarde en cours | Formulaires | Bouton de validation désactivé + spinner inline dans le bouton. Pas de loader plein écran. |
| Conflit POS (article déjà vendu) | Caisse | Notification inline (variante erreur — fond `{colors.error-container}`, bordure `{colors.error}`) sous le scanner + liste des articles en conflit. Bénévole retire les articles manuellement. Pas de résolution automatique. |
| Lot incomplet | Caisse | Notification inline (variante avertissement — fond `{colors.primary-container}`, bordure `{colors.primary}`) dans le panier. Bouton "Valider" actif — la vente d'un lot incomplet est autorisée. Compteur X/N mis à jour à chaque scan. |
| Article incomplet scanné — caisse (FR-037) | Caisse | Lors du scan d'un article marqué "incomplet" lors du dépôt, l'article est ajouté au panier normalement (la vente n'est pas bloquée) mais une notification inline avertissement apparaît sous la ligne de l'article : "Article incomplet — vérifiez l'état avant de valider." Le bénévole peut continuer ou retirer l'article manuellement. |
| Formulaire lot — articles insuffisants | Formulaire dépôt — mode Lot | Bouton "Valider le lot" désactivé tant que moins de 2 articles sont présents dans la liste. Aucun message inline proactif — le compte dans le label du bouton suffit (ex : "Valider le lot (1 article)" reste grisé). L'état désactivé lève dès que le 2ème article reçoit un nom non-vide. |
| Changement de phase pendant une transaction | Caisse | Toast persistant : "La phase a changé. Votre panier a été annulé." Panier vidé. Scanner désactivé jusqu'à rechargement de la page caisse. |
| Imprimante hors ligne | Toast | Toast persistant : "L'imprimante [nom] ne répond pas. Vérifiez la connexion Bluetooth / réseau." Bouton "Fermer". L'action d'impression reste rejouable depuis l'interface. |
| Session expirée | Global | Redirection vers `/login` avec message "Votre session a expiré. Reconnectez-vous." (théoriquement impossible vu FR-066, mais couvert par sécurité). |
| Reversements — liste vide | `/volunteer/settlement` · `/admin/settlement` | Aucun vendeur enregistré dans l'édition (cas rare mais défensivement atteignable). Icône centré + "Aucun vendeur enregistré pour cette édition." Aucune action proposée. |
| Phase Clôturée — édition archivée | Admin | Données en lecture seule. Bannière "Édition clôturée" sous la topbar. Bouton "Archiver l'édition" accessible si articles non supprimés. |
| Catalogue post-Archivage | Admin + Bénévole | Catalogue vide avec message "Édition archivée — les articles ont été supprimés." Aucune action. Aucun filtre. |
| Retour arrière désactivé après Archivage | Admin — `/admin/editions/:id/phase` | Bouton "Revenir en Post-vente" absent, remplacé par un message inline icône `lock` : "Retour en arrière indisponible — l'édition a été archivée." |
| Premier lancement — mot de passe forcé | Admin | Navigation bloquée sur `/account/force-password`. Sidebar masquée. Message d'invite prominent. Aucune route accessible avant changement effectué. |
| Post-validation POS — facture disponible | Caisse | Bouton "Imprimer la facture" visible pendant 30 secondes. Disparaît automatiquement. Scanner autofocused pour la transaction suivante dès la fermeture du panel paiement. |
| Conflit de scan concurrent (article déjà vendu depuis un autre poste) | Caisse | Réponse HTTP 409 synchrone au scan. Notification inline (variante erreur — fond `{colors.error-container}`, bordure `{colors.error}`) sous le scanner : "Article déjà vendu sur un autre poste." L'article n'est pas ajouté au panier. Scanner reste actif. |

## Interaction Primitives

**Scanner USB HID — caisse uniquement**
La caisse est conçue pour une utilisation scanner-first. Le champ de scan est autofocused et capte tous les événements clavier. Un clic involontaire sur la page peut déplacer le focus — plutôt qu'un re-focus automatique temporisé (risque de piège clavier SC 2.1.2), le composant `scanner.component.ts` expose un bouton "Retour au scanner" (raccourci `Échap` depuis n'importe quel élément interactif de la page caisse) qui rétablit explicitement le focus sur le champ de scan à la demande de l'utilisateur.

**Clavier — navigation générale**

- `Tab` / `Shift+Tab` : navigation entre éléments interactifs dans l'ordre visuel
- `Enter` / `Space` : activation des boutons et liens
- `Echap` : fermeture des dialogs et popovers
- Pas de raccourcis vim ou globaux en v1 — l'audience bénévole ne les utiliserait pas

**Formulaires**

- Validation côté client : Angular reactive forms, feedback immédiat sur `blur`
- Validation côté serveur : erreurs mappées sur les champs concernés (format d'erreur standard par champ)
- `Enter` dans un formulaire mono-champ (recherche vendeur) soumet le formulaire
- `Enter` dans un formulaire multi-champs déplace le focus au champ suivant
- `Enter` dans le champ optionnel "Somme remise (€)" du panel paiement POS déplace le focus vers le bouton "Confirmer" (champ optionnel dans un contexte multi-champ — ne soumet pas)

**Actions irréversibles**
Toute action irréversible ou à fort impact (transition de phase, suppression profil vendeur, archivage de l'édition) nécessite un dialog de confirmation avec description des conséquences. Aucune action de ce type n'est accessible depuis un double-clic ou un raccourci clavier non-intentionnel.

**Impression**
Déclenchée par un bouton explicite dans l'interface. Feedback immédiat : spinner dans le bouton pendant la mise en queue. Résultat communiqué par toast (succès) ou toast persistant (erreur). Le bouton redevient actif après traitement — l'impression est toujours rejouable.

Cas particulier — validation du dépôt (FR-028, FR-031) : à la validation du dépôt, l'impression des étiquettes **et** du bordereau de dépôt est déclenchée automatiquement, sans bouton supplémentaire. Le bordereau imprime à la suite des étiquettes sur l'imprimante standard (A4). L'impression du bordereau est également rejouable depuis la fiche vendeur via un bouton "Réimprimer le bordereau".

**Structure du rouleau thermique — dépôt (FR-030) :** à la validation du dépôt, l'imprimante thermique produit dans l'ordre : (1) séparateur vendeur (ligne en pointillés + nom du vendeur) ; (2) pour chaque article : étiquette article puis séparateur article (fine ligne de séparation). Les articles de lot reçoivent chacun leur propre étiquette avec la mention "Lot indivisible : N/M". Cette structure permet d'identifier les transitions entre vendeurs lors d'impressions consécutives.

**Conflit de scan concurrent**
L'erreur "article déjà vendu" est une réponse HTTP 409 synchrone au scan — pas un push SSE. Le composant Scanner traite le 409 comme une notification inline rouge, sans délai supplémentaire. L'article n'est pas ajouté au panier ; le scanner reste actif pour la prochaine saisie.

**Interdit**

- Drag-and-drop (v1)
- Infinite scroll (utiliser pagination ou chargement complet pour les volumes PluriBourse)
- Hover-only affordances (pas d'action visible uniquement au survol)
- Double-clic comme déclencheur d'action primaire
- Auto-submit de formulaire sans confirmation utilisateur

## Accessibility Floor

Comportemental. Contrastes visuels dans `DESIGN.md`.

- **Skip-link** : premier élément du DOM sur toutes les pages — `<a class="skip-link" href="#main-content">Aller au contenu principal</a>`. Invisible au repos, visible au focus (SC 2.4.1). La zone de contenu principal porte `id="main-content"` et `tabindex="-1"` pour recevoir le focus programmatique.
- **WCAG 2.2 AA minimum** sur toute la surface. Couleur primaire `#C44626` sur fond blanc : ratio 4.6:1 (AA ✓). Version sombre `#F07040` sur `#1A0C06` : ratio 5.2:1 (AA ✓).
- **Focus ring** visible sur tous les éléments interactifs — hérité du token `{colors.primary}` Angular Material, jamais supprimé. SC 2.4.11 (WCAG 2.2 AA) : l'anneau de focus doit avoir une aire minimale égale au périmètre de la cible × 2px et un ratio de contraste ≥ 3:1 entre l'état focus et l'état repos — à vérifier lors des tests de composants Material personnalisés.
- **Tab order** suit l'ordre de lecture visuel sur chaque page. Ordre DOM recommandé : skip-link → topbar (logo, phase chip, icône profil) → sidebar (Admin uniquement) → contenu principal. La topbar est en tête du DOM pour être atteinte rapidement au clavier, mais le skip-link permet de la sauter — les deux exigences sont ainsi satisfaites.
- **Focus trap** dans tous les dialogs (`DialogShellComponent`, y compris les formulaires CRUD encapsulés) — `Tab` ne sort pas du dialog tant qu'il est ouvert, la croix de fermeture fait partie du cycle de focus piégé. Focus initial sur le bouton d'annulation pour les dialogs de confirmation (action sûre) ; sur le premier champ utile pour les formulaires. La croix de fermeture porte `aria-label="Fermer"` et équivaut fonctionnellement à Annuler/Echap — jamais à une confirmation implicite.
- **Annonces screen reader** : titre de page annoncé à chaque navigation (`<title>` mis à jour via un `TitleStrategy` Angular personnalisé — format `"[Nom de la page] — PluriBourse"`). Listes annoncées avec leur nombre d'éléments via `aria-label` mis à jour dynamiquement après chaque filtre — ex. `aria-label="Liste des vendeurs — 12 résultats"` ou `aria-label="Liste des vendeurs filtrée — 3 résultats"`. Erreurs de formulaire annoncées via `aria-describedby`.
- **Noms accessibles des boutons contextuels** (SC 2.4.6) : les boutons d'action dans les listes incluent le contexte de la ligne dans leur `aria-label`. Exemples : `aria-label="Solder Martin Pierre"` · `aria-label="Imprimer le bilan de Martin Pierre"` · `aria-label="Supprimer l'article Jeu de construction"`. Le nom visible du bouton ("Solder", "Imprimer") reste court ; le complément contextuel est porté uniquement par l'`aria-label`. Implémentation Angular : `[attr.aria-label]="'Solder ' + seller.firstName + ' ' + seller.lastName"`.
- **Phase chip** porte un `aria-label` complet : "Phase actuelle : Dépôt" (pas seulement le texte visible).
- **Scanner input** : `aria-label="Scanner ou saisir un code-barres"` · `aria-live="polite"` sur la zone de résultat du scan pour annoncer l'article ajouté au panier.
- **Taille minimale des cibles tactiles** : 44×44px (même en desktop — bénévoles potentiellement moins précis avec la souris sous stress d'événement).
- Icônes décoratives : `aria-hidden="true"`. Icônes porteuses de sens : accompagnées d'un label texte ou `aria-label`.
- **Stratégie `aria-describedby`** : les conteneurs de messages d'erreur sont pré-rendus dans le DOM (avec `aria-live="polite"`) mais vides par défaut ; le `aria-describedby` de l'input pointe vers leur `id` dès le rendu. Le message est injecté dans le conteneur lors de la validation — Angular Material `MatError` suit ce pattern nativement.

## Key Flows

### Flow 1 — Dépôt d'un vendeur (Sophie, bénévole accueil, phase Dépôt)

**Pré-condition :** Sophie s'est connectée et a sélectionné son imprimante thermique et A4 via l'écran de sélection interstitiel (FR-098). Elle arrive sur `/volunteer/deposit` avec ses préférences d'impression en session.

1. Sophie arrive sur `/volunteer/deposit`. Le champ de recherche vendeur reçoit le focus à l'ouverture.
2. Elle tape le nom "Martin" dans la recherche vendeur — résultats filtrés en temps réel.
   2b. **Chemin alternatif — vendeur non trouvé** : la recherche ne retourne aucun résultat. L'état vide affiche "Aucun vendeur trouvé. Créez un nouveau profil." avec un bouton "Créer un profil". Sophie clique — le formulaire de création s'ouvre pré-rempli avec le terme de recherche dans le champ nom. Elle complète le profil (prénom, nom, email optionnel, téléphone optionnel) et clique "Créer et continuer" — le vendeur est créé et la saisie des articles démarre immédiatement.
3. Elle sélectionne "Martin Pierre". La fiche vendeur s'ouvre avec la liste de ses articles des éditions précédentes en lecture seule.
4. Elle saisit les articles un par un : nom, prix, catégorie. La table est assignée automatiquement. Pour les articles vendus en ensemble, elle bascule le sélecteur sur "Lot", saisit le nom du lot et son prix global, ajoute les articles du lot (nom + catégorie par article), puis valide le lot.
5. Pour un article de jeu de société incomplet, elle coche "Incomplet" et saisit le détail manquant.
6. **Climax :** Elle clique "Valider le dépôt". Un spinner apparaît dans le bouton. 2 secondes plus tard : toast "Dépôt enregistré." L'imprimante thermique démarre automatiquement — séparateur vendeur puis étiquettes. Sophie passe au suivant.

Échec imprimante : toast persistant "L'imprimante [nom] ne répond pas. Vérifiez la connexion Bluetooth / réseau." Le dépôt est enregistré ; les étiquettes sont rejouables depuis la fiche vendeur.

### Flow 2 — Vente avec lot incomplet (Marc, bénévole caisse, phase Vente)

1. Marc est sur `/volunteer/pos`. Scanner autofocused, panier vide.
2. Il scanne plusieurs articles normaux — ajoutés au panier avec nom et prix.
3. Il scanne un article appartenant à un lot "Jeu de société Catan". Notification inline avertissement : "Lot Catan — 1/3 scannés." Le bouton "Valider" reste actif.
4. Il scanne le deuxième article du lot. Compteur : "2/3". La notification persiste.
5. **Climax :** L'acheteur ne retrouve pas le 3ème article. Marc a deux options : (a) valider le paiement tel quel — le lot incomplet est vendu au prix global du lot avec l'avertissement affiché ; (b) cliquer "Retirer le lot entier" — les 2 articles du lot disparaissent du panier et la transaction continue avec les articles hors lot.

### Flow 3 — Reversement, vendeur absent (Sophie, phase Post-vente)

1. Sophie ouvre `/volunteer/settlement`. Liste des vendeurs non soldés avec nom, prénom et montant dû. (Les colonnes téléphone et email sont visibles uniquement dans la vue admin `/admin/settlement`.)
2. Elle appelle Martin Pierre — pas de réponse. Elle clique "Non réclamé" sur sa ligne.
3. Dialog de confirmation : "Le montant de 34,50 € sera versé aux recettes de l'association. Cette action est irréversible." Deux boutons : "Confirmer" (primary) et "Annuler" (ghost).
4. **Climax :** Elle confirme. La ligne disparaît de la liste des non soldés. Toast : "Montant transféré aux recettes." Sophie passe au vendeur suivant.

### Flow 4 — Transition de phase (Laurent, admin)

1. Laurent est sur `/admin/editions`. Il clique "Gérer les phases" sur la ligne de l'édition active — le dialog Contrôle de phase s'ouvre par-dessus la liste. Il voit la phase actuelle "Dépôt" et le bouton "Passer en phase Vente".
2. Il clique. Un second dialog de confirmation s'ouvre par-dessus : "Passer en phase Vente. Les dépôts ne seront plus modifiables. Les caisses bénévoles s'activeront." Boutons : "Confirmer" et "Annuler".
3. **Climax :** Il confirme. Le dialog de confirmation se ferme, toast succès : "Phase avancée." Le dialog Contrôle de phase se ferme automatiquement et Laurent retrouve la liste des éditions. La phase chip dans la topbar passe de "Dépôt" à "Vente" (fade 150ms). Sur tous les postes bénévoles connectés, la même transition s'affiche via SSE — l'interface bascule automatiquement sur la caisse. Un bénévole qui avait un formulaire de dépôt ouvert voit un toast : "La phase a changé. Votre session de dépôt a été annulée."

### Flow 5 — Impression du bilan de vente avant remise des invendus (Lucie, bénévole, phase Post-vente)

1. Lucie ouvre `/volunteer/settlement`. La liste affiche tous les vendeurs — filtre actif par défaut sur "Non soldés".
2. Elle cherche "Dubois Marie" dans la liste. Elle repère la ligne avec le montant dû et les actions disponibles.
3. Avant de solder, elle doit d'abord imprimer le bilan pour que Marie puisse regrouper ses invendus sur les bonnes tables. Elle clique "Imprimer le bilan" sur la ligne de Marie.
4. Spinner dans le bouton pendant la mise en queue. Toast succès (4s) : "Bilan envoyé à l'imprimante." L'imprimante A4 produit le bilan : articles vendus, invendus avec numéro de table, reversement net.
5. **Climax :** Marie récupère le bilan imprimé, repère ses invendus, revient au bureau de solde. Lucie clique "Solder" sur sa ligne. Le formulaire de solde apparaît. Elle saisit 18,50 € — montant exact. Elle clique "Valider". La ligne passe en statut "Soldé" (chip vert). Toast : "Vendeur réglé."

Échec impression : toast persistant "L'imprimante [nom] ne répond pas. Vérifiez la connexion Bluetooth / réseau." Le bouton "Imprimer le bilan" reste actif et rejouable après résolution de l'erreur.

### Flow 6 — Premier lancement (Laurent, admin, installation initiale)

1. Laurent ouvre `http://localhost:8080` après `docker compose up -d`. Il atterrit sur `/login`.
2. Il saisit "Admin" / "Admin" et clique "Connexion".
3. **Climax :** Le système reconnaît les identifiants par défaut et redirige immédiatement vers `/account/force-password`. La sidebar est masquée. Le message "Vous devez changer votre mot de passe avant de continuer." apparaît en bannière.
4. Laurent choisit un nouveau mot de passe, confirme. Bouton "Enregistrer" (primary). Après confirmation : redirection vers `/admin/settings`.
5. Il configure le nom de l'association, le taux de commission et la langue des documents. Toast "Paramètres enregistrés." L'instance est prête.
